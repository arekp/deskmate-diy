#!/usr/bin/env python3
"""Sprawdzenie kolizji, momentu na serwie TILT i stabilności DeskMate DIY.

Eksportuje z assembly.scad grupy brył (podstawa, jarzmo, głowa), woksel-
izuje je (0.5 mm) i sprawdza próbkowane powierzchnie w całym zakresie
pan/tilt z params.scad. Telefon w etui = prostopadłościan.

Użycie:
  OPENSCAD=/ścieżka/openscad python3 hardware/tools/collision_check.py [profil ...]
Wymaga: numpy, scipy.
"""
import os
import json
import re
import subprocess
import sys
import tempfile

import numpy as np
from scipy import ndimage

sys.path.insert(0, os.path.dirname(__file__))
from stl_check import load_stl  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
CAD = os.path.join(HERE, "..", "cad")
OSC = os.environ.get("OPENSCAD", "openscad")
PITCH = 0.5
FILL = 0.55          # efektywna gęstość wydruku (4 ścianki, 25–30 % wypełnienia)
RHO = 1.27e-3        # g/mm3 PETG
PROFILES = ["s23", "fold8_open", "fold8_closed", "flip6_open"]


def scad(out, defs):
    args = [OSC, "-o", out]
    for k, v in defs.items():
        args += ["-D", f'{k}={v}']
    args.append(os.path.join(CAD, "assembly.scad"))
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def info(profile, tmp):
    f = os.path.join(tmp, "info.echo")
    scad(f, {"show": '"info"', "profile": f'"{profile}"'})
    line = open(f).read()
    d = {}
    for kv in re.search(r"INFO\|(.*)\"", line).group(1).split("|"):
        k, v = kv.split("=", 1)
        try:
            d[k] = float(v)
        except ValueError:
            try:
                d[k] = json.loads(v)
            except ValueError:
                d[k] = v
    return d


class Vox:
    """Wokselizacja metodą parzystości promieni wzdłuż Z."""

    def __init__(self, tri, pitch=PITCH, pad=24):
        self.h = pitch
        lo = tri.reshape(-1, 3).min(0) - pad * pitch
        hi = tri.reshape(-1, 3).max(0) + pad * pitch
        self.o = lo
        n = np.ceil((hi - lo) / pitch).astype(int) + 1
        cnt = np.zeros(n, dtype=np.int16)
        off = np.array([1.13e-4, 2.71e-4])        # unikamy trafień w krawędzie
        for t in tri:
            xy = t[:, :2]
            a, b, c = xy
            det = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
            if abs(det) < 1e-12:
                continue
            i0, j0 = np.floor((xy.min(0) - lo[:2]) / pitch).astype(int)
            i1, j1 = np.ceil((xy.max(0) - lo[:2]) / pitch).astype(int)
            ii, jj = np.meshgrid(np.arange(i0, i1 + 1), np.arange(j0, j1 + 1), indexing="ij")
            px = lo[0] + ii * pitch + off[0]
            py = lo[1] + jj * pitch + off[1]
            l1 = ((b[0] - px) * (c[1] - py) - (c[0] - px) * (b[1] - py)) / det
            l2 = ((c[0] - px) * (a[1] - py) - (a[0] - px) * (c[1] - py)) / det
            l3 = 1 - l1 - l2
            m = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
            if not m.any():
                continue
            z = l1[m] * t[0, 2] + l2[m] * t[1, 2] + l3[m] * t[2, 2]
            k = np.ceil((z - lo[2]) / pitch).astype(int)
            np.add.at(cnt, (ii[m], jj[m], np.clip(k, 0, n[2] - 1)), 1)
        self.inside = (np.cumsum(cnt, axis=2) % 2).astype(bool)
        self.dist = ndimage.distance_transform_edt(~self.inside) * pitch

    def query(self, p):
        """Zwraca odległość od bryły (0 = wewnątrz); poza siatką = 99."""
        idx = np.round((p - self.o) / self.h).astype(int)
        ok = np.all((idx >= 0) & (idx < np.array(self.inside.shape)), axis=1)
        d = np.full(len(p), 99.0)
        d[ok] = self.dist[idx[ok, 0], idx[ok, 1], idx[ok, 2]]
        return d


def sample_surface(tri, spacing=1.0):
    pts = [tri.reshape(-1, 3)]
    area = 0.5 * np.linalg.norm(np.cross(tri[:, 1] - tri[:, 0], tri[:, 2] - tri[:, 0]), axis=1)
    n = np.floor(area / spacing ** 2).astype(int)
    rng = np.random.default_rng(1)
    rep = np.repeat(np.arange(len(tri)), n)
    if len(rep):
        r1, r2 = rng.random(len(rep)), rng.random(len(rep))
        s = np.sqrt(r1)
        t = tri[rep]
        pts.append((1 - s)[:, None] * t[:, 0] + (s * (1 - r2))[:, None] * t[:, 1] + (s * r2)[:, None] * t[:, 2])
    return np.vstack(pts)


def box_surface(p0, size, spacing=1.0):
    g = [np.linspace(p0[i], p0[i] + size[i], max(2, int(size[i] / spacing) + 1)) for i in range(3)]
    pts = []
    for ax in range(3):
        o = [a for a in range(3) if a != ax]
        A, B = np.meshgrid(g[o[0]], g[o[1]], indexing="ij")
        for v in (p0[ax], p0[ax] + size[ax]):
            P = np.zeros((A.size, 3))
            P[:, ax] = v
            P[:, o[0]] = A.ravel()
            P[:, o[1]] = B.ravel()
            pts.append(P)
    return np.vstack(pts)


def in_box(p, p0, size):
    return np.all((p >= p0) & (p <= p0 + size), axis=1)


def rx(deg):
    a = np.radians(deg)
    return np.array([[1, 0, 0], [0, np.cos(a), -np.sin(a)], [0, np.sin(a), np.cos(a)]])


def rz(deg):
    a = np.radians(deg)
    return np.array([[np.cos(a), -np.sin(a), 0], [np.sin(a), np.cos(a), 0], [0, 0, 1]])


def mass_props(tri):
    t = tri
    v = np.einsum("ij,ij->i", t[:, 0], np.cross(t[:, 1], t[:, 2])) / 6.0
    c = (t[:, 0] + t[:, 1] + t[:, 2]) / 4.0
    V = v.sum()
    return V, (v[:, None] * c).sum(0) / V


def support_margin(p, feet):
    """Odległość punktu od krawędzi wielokąta podparcia (wypukła otoczka nóżek)."""
    pts = np.array(feet, dtype=float)
    c = pts.mean(0)
    pts = pts[np.argsort(np.arctan2(pts[:, 1] - c[1], pts[:, 0] - c[0]))]
    m = 1e9
    for i in range(len(pts)):
        a, b = pts[i], pts[(i + 1) % len(pts)]
        n = np.array([b[1] - a[1], a[0] - b[0]])
        n /= np.linalg.norm(n)
        m = min(m, np.dot(a - p, n))   # >0 = wewnątrz (krawędzie CCW)
    return m


def fmt(v):
    return "> 10 mm" if v >= 10 else f"{v:.1f} mm"


def main(profiles):
    tmp = tempfile.mkdtemp(prefix="deskmate_")
    I = info(profiles[0], tmp)
    tilts = np.arange(I["tilt_min"], I["tilt_max"] + 0.01, 1.0)
    pans = np.arange(I["pan_min"], I["pan_max"] + 0.01, 5.0)
    axis_g = np.array([0, I["tilt_y"], I["z_axis"]])

    for name in ("g_base", "g_yoke"):
        scad(os.path.join(tmp, name + ".stl"), {"show": f'"{name}"', "profile": f'"{profiles[0]}"'})
    base_tri = load_stl(os.path.join(tmp, "g_base.stl"))
    yoke_tri = load_stl(os.path.join(tmp, "g_yoke.stl"))
    vb, vy = Vox(base_tri), Vox(yoke_tri)
    base_pts, yoke_pts = sample_surface(base_tri, 1.5), sample_surface(yoke_tri, 1.5)
    scad(os.path.join(tmp, "gyp.stl"), {"show": '"g_yoke_plastic"'})
    scad(os.path.join(tmp, "gbp.stl"), {"show": '"g_base_plastic"'})
    Vy, Cy = mass_props(load_stl(os.path.join(tmp, "gyp.stl")))
    Vb, Cb = mass_props(load_stl(os.path.join(tmp, "gbp.stl")))

    # jarzmo vs podstawa (pan)
    worst = 99
    for pan in pans:
        R = rz(pan)
        pg = (yoke_pts + axis_g) @ R.T
        worst = min(worst, vb.query(pg).min())
        pb = (base_pts @ R) - axis_g        # podstawa w układzie jarzma
        worst = min(worst, vy.query(pb).min())
    print(f"\n[jarzmo ↔ podstawa] pan {pans[0]:.0f}…{pans[-1]:.0f}°: min. luz {fmt(worst)}"
          + ("  KOLIZJA!" if worst < 0.3 else "  OK"))

    results = {}
    for prof in profiles:
        I = info(prof, tmp)
        fh = os.path.join(tmp, f"g_head_{prof}.stl")
        scad(fh, {"show": '"g_head"', "profile": f'"{prof}"'})
        fhp = os.path.join(tmp, f"g_head_plastic_{prof}.stl")
        scad(fhp, {"show": '"g_head_plastic"', "profile": f'"{prof}"'})
        head_tri = load_stl(fh)
        vh = Vox(head_tri)
        head_pts = sample_surface(head_tri, 1.5)
        b0 = np.array([I["cx"] - I["L"] / 2, I["D_back"], I["cz"] - I["H"] / 2])
        bs = np.array([I["L"], I["T"], I["H"]])
        ph_pts = box_surface(b0, bs, 1.5)

        # głowa+telefon vs jarzmo (tilt)
        min_h, min_p, min_p_where = 99, 99, None
        for t in tilts:
            R = rx(t)
            min_h = min(min_h, vy.query(head_pts @ R.T).min())
            d = vy.query(ph_pts @ R.T)
            if d.min() < min_p:
                min_p, min_p_where = d.min(), t
            yl = yoke_pts @ R              # jarzmo w układzie głowy
            if in_box(yl, b0, bs).any():
                min_p = 0
            min_h = min(min_h, vh.query(yl).min())
        # głowa+telefon vs podstawa i blat (pan × tilt)
        min_b, min_z = 99, 1e9
        for pan in np.arange(I["pan_min"], I["pan_max"] + 0.01, 10.0):
            for t in np.arange(I["tilt_min"], I["tilt_max"] + 0.01, 5.0):
                M = rz(pan) @ rx(t)
                for P in (head_pts, ph_pts):
                    pg = P @ M.T + axis_g @ rz(pan).T
                    min_b = min(min_b, vb.query(pg).min())
                    min_z = min(min_z, pg[:, 2].min())

        # masa i moment
        Vh, Ch = mass_props(load_stl(fhp))
        parts = [  # (masa g, położenie w układzie głowy)
            (Vh * RHO * FILL, Ch, "plastik głowy"),
            (I["mphone"], np.array([I["cx"], I["D_back"] + I["T"] / 2, I["cz"]]), "telefon w etui"),
            (I["pad_m"], np.array([0, I["pad_top_y"] - I["pad_h"] / 2, I["pad_cz"]]), "ładowarka"),
            (10 * I["fan_on"], np.array([0, I["slab_back"] - 5, I["pad_cz"]]), "wentylator"),
            (I["sv_tilt_m"], np.array([I["ear_top_x"] - 16, 0, -I["sv_tilt_c"]]), "serwo TILT (w głowie)"),
            (30, np.array([-95, 0, 0]), "śruba M8 + nakrętka + 608"),
            (18, np.array([0, 10, 0]), "śruby M3, wkładki, TPU, przewody"),
            (4, np.array([0, 15, I["hd_zt"] + 30]), "dioda"),
        ]
        m_head = sum(p[0] for p in parts)
        cg_head = sum(p[0] * p[1] for p in parts) / m_head
        tau = [abs(sum(p[0] * (rx(t) @ p[1])[1] for p in parts)) / 1000 / 10 for t in tilts]  # kg*cm
        # stabilność
        static = [(Vy * RHO * FILL, Cy + axis_g, "jarzmo")]
        base_parts = [(Vb * RHO * FILL, Cb, "tułów+pokrywa+kołnierz"),
                      (I["sv_pan_m"], np.array([0, 10, 30]), "serwo PAN"),
                      (70, np.array([0, 0, 62]), "lazy susan"),
                      (90, np.array([0, -40, 12]), "elektronika"),
                      (300, np.array([0, 56, 15]), "obciążnik (zalecany)")]
        worst_margin, worst_pose = 1e9, None
        for pan in pans:
            for t in (I["tilt_min"], 0, I["tilt_max"]):
                items = list(base_parts)
                for m, p, _ in static:
                    items.append((m, rz(pan) @ p))
                for m, p, _ in parts:
                    items.append((m, rz(pan) @ (rx(t) @ p + axis_g)))
                M = sum(i[0] for i in items)
                cg = sum(i[0] * i[1] for i in items) / M
                m = support_margin(cg[:2], I["feet"])
                if m < worst_margin:
                    worst_margin, worst_pose, worst_cg, totm = m, (pan, t), cg, M
        results[prof] = dict(min_h=min_h, min_p=min_p, min_p_t=min_p_where, min_b=min_b, min_z=min_z,
                             m_head=m_head, cg=cg_head, tau=max(tau), margin=worst_margin,
                             pose=worst_pose, cgw=worst_cg, M=totm, m_plast=Vh * RHO * FILL)
        r = results[prof]
        ok = lambda v: "OK" if v >= 0.3 else "KOLIZJA!"
        print(f"\n=== profil {prof}: telefon w etui {I['L']:.1f}×{I['H']:.1f}×{I['T']:.1f} mm, {I['mphone']:.0f} g")
        print(f"  telefon ↔ jarzmo  (tilt {tilts[0]:.0f}…{tilts[-1]:.0f}°): min. luz {fmt(r['min_p'])} "
              f"(przy tilt {r['min_p_t']:.0f}°)  {ok(r['min_p'])}")
        print(f"  głowa   ↔ jarzmo  : min. luz {fmt(r['min_h'])}  {ok(r['min_h'])}")
        print(f"  głowa+telefon ↔ podstawa (pan × tilt): min. luz {fmt(r['min_b'])}  {ok(r['min_b'])}; "
              f"najniższy punkt {r['min_z']:.1f} mm nad blatem (góra podstawy {I['base_h']:.0f} mm)")
        print(f"  masa głowy z telefonem ≈ {r['m_head']:.0f} g (plastik ≈ {r['m_plast']:.0f} g), "
              f"środek ciężkości względem osi TILT: Y {r['cg'][1]:+.1f} mm, Z {r['cg'][2]:+.1f} mm")
        print(f"  maks. moment statyczny na serwie TILT ≈ {r['tau']:.2f} kg·cm "
              f"(MG996R ≈ 9–11 kg·cm → {100 * r['tau'] / 9.4:.0f} % przy 4.8 V)")
        print(f"  stabilność: masa całkowita ≈ {r['M']:.0f} g, najgorszy zapas środka ciężkości do krawędzi "
              f"nóżek {r['margin']:.0f} mm (pan {r['pose'][0]:.0f}°, tilt {r['pose'][1]:.0f}°), "
              f"kąt przewrócenia ≈ {np.degrees(np.arctan2(r['margin'], r['cgw'][2])):.0f}°")
    bad = any(min(r["min_h"], r["min_p"], r["min_b"]) < 0.3 for r in results.values()) or worst < 0.3
    print("\nWYNIK:", "brak kolizji w pełnym zakresie ruchu" if not bad else "WYKRYTO KOLIZJE")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or PROFILES))
