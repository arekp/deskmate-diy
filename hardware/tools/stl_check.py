#!/usr/bin/env python3
"""Sprawdza pliki STL: szczelność (każda krawędź w dokładnie 2 trójkątach,
spójna orientacja), objętość, wymiary, liczba brył, szacowana masa (PETG).

Użycie: python3 hardware/tools/stl_check.py hardware/stl/*.stl
Wymaga tylko numpy.
"""
import struct
import sys
from collections import Counter

import numpy as np

PETG_DENSITY = 1.27  # g/cm3


def load_stl(path):
    data = open(path, "rb").read()
    if data[:5] == b"solid" and b"facet" in data[:400]:
        verts = []
        for line in data.decode("ascii", "replace").splitlines():
            line = line.strip()
            if line.startswith("vertex"):
                verts.append([float(v) for v in line.split()[1:4]])
        tri = np.array(verts, dtype=np.float64).reshape(-1, 3, 3)
    else:
        n = struct.unpack("<I", data[80:84])[0]
        rec = np.frombuffer(data[84:84 + n * 50], dtype=np.dtype([
            ("n", "<f4", 3), ("v", "<f4", (3, 3)), ("a", "<u2")]))
        tri = rec["v"].astype(np.float64)
    return tri


def index_mesh(tri, decimals=5):
    flat = np.round(tri.reshape(-1, 3), decimals)
    uniq, inv = np.unique(flat, axis=0, return_inverse=True)
    return uniq, inv.reshape(-1, 3)


def components(faces, nverts):
    parent = np.arange(nverts)

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for f in faces:
        ra, rb, rc = find(f[0]), find(f[1]), find(f[2])
        parent[rb] = ra
        parent[find(rc)] = ra
    used = np.unique(faces)
    return len({find(v) for v in used})


def check(path):
    tri = load_stl(path)
    verts, faces = index_mesh(tri)
    # usuń zdegenerowane
    good = (faces[:, 0] != faces[:, 1]) & (faces[:, 1] != faces[:, 2]) & (faces[:, 0] != faces[:, 2])
    faces = faces[good]
    directed = Counter()
    undirected = Counter()
    for a, b, c in faces:
        for u, v in ((a, b), (b, c), (c, a)):
            directed[(u, v)] += 1
            undirected[(min(u, v), max(u, v))] += 1
    bad_count = sum(1 for k, n in undirected.items() if n != 2)
    bad_orient = sum(1 for k, n in directed.items() if n != 1 or directed.get((k[1], k[0]), 0) != 1)
    t = verts[faces]
    vol = np.einsum("ij,ij->i", t[:, 0], np.cross(t[:, 1], t[:, 2])).sum() / 6.0
    lo, hi = verts[faces].reshape(-1, 3).min(0), verts[faces].reshape(-1, 3).max(0)
    ncomp = components(faces, len(verts))
    euler = len(np.unique(faces)) - len(undirected) + len(faces)
    ok = bad_count == 0 and bad_orient == 0 and vol > 0
    return dict(file=path.split("/")[-1], tris=len(faces), ok=ok, open_edges=bad_count,
                bad_orient=bad_orient, vol_cm3=vol / 1000, size=hi - lo, comps=ncomp,
                euler=euler, mass_solid=vol / 1000 * PETG_DENSITY)


if __name__ == "__main__":
    allok = True
    print(f"{'plik':32s} {'trójk.':>7s} {'szczelny':>8s} {'brył':>4s} {'X×Y×Z [mm]':>22s} {'obj.[cm3]':>9s} {'masa 100%':>9s}")
    for p in sys.argv[1:]:
        r = check(p)
        allok &= r["ok"]
        s = r["size"]
        print(f"{r['file']:32s} {r['tris']:7d} {('TAK' if r['ok'] else 'NIE'):>8s} {r['comps']:4d} "
              f"{s[0]:6.1f}×{s[1]:6.1f}×{s[2]:6.1f} {r['vol_cm3']:9.1f} {r['mass_solid']:7.0f} g"
              + ("" if r["ok"] else f"  (krawędzie otwarte {r['open_edges']}, orientacja {r['bad_orient']})"))
    sys.exit(0 if allok else 1)
