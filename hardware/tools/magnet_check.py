#!/usr/bin/env python3
"""Szacunek siły trzymania telefonu przez pierścień magnesów w kasku.

Model (orientacyjny, celowo ostrożny):
  * F0 – udźwig pojedynczego magnesu N52 na grubej stali przy styku
    (typowe wartości katalogowe, tabela niżej),
  * spadek z odległością: (B(g)/B(0))^2, B(z) – pole na osi walca
    B(z) = Br/2 * [(L+z)/sqrt(R^2+(L+z)^2) - z/sqrt(R^2+z^2)],
  * k_ring – cienki (0.3–0.5 mm) i wąski (~5 mm) pierścień stalowy pod
    magnesem Ø8–10 wykorzystuje tylko część strumienia: przyjęte 0.3,
  * szczelina = skórka 0.6 mm + luz 0.1 mm + etui/naklejka pierścienia
    (nominalnie 1.0 mm, pesymistycznie 2.0 mm).

Wymagana siła – telefon stoi na półce z wargą i przy tilt −25° (ekran w dół)
odchyla się od płyty, obracając się wokół dolnej krawędzi:
  F_req = k_dyn * m * g * sin(25°) * h_cg / h_mag
(h_cg, h_mag – wysokość środka telefonu i środka pierścienia nad półką),
k_dyn = 2 (szarpnięcia głowy). Ciężar wzdłuż płyty trzyma półka.

Użycie: OPENSCAD=... python3 hardware/tools/magnet_check.py
"""
import math
import os
import re
import sys
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
from collision_check import info, PROFILES, CAD  # noqa: E402

G = 9.81
K_DYN = 2.0
K_RING = 0.3
TILT = 25.0
# udźwig katalogowy N52 na grubej stali [N] (≈ kg × 9.81)
F0_TABLE = {(8, 2): 15.0, (8, 3): 20.0, (10, 2): 20.0, (10, 3): 28.0, (12, 3): 38.0}


def field(z, d, t):
    R, L = d / 2, t
    return 0.5 * ((L + z) / math.hypot(R, L + z) - z / math.hypot(R, z))


def holding(n, d, t, gap):
    f0 = F0_TABLE.get((d, t), 15.0 * (d * d * t) / (8 * 8 * 2))
    decay = (field(gap, d, t) / field(0, d, t)) ** 2
    return n * f0 * decay * K_RING, f0, decay


def params():
    s = open(os.path.join(CAD, "params.scad")).read()
    get = lambda k: float(re.search(rf"^{k}\s*=\s*([0-9.]+)", s, re.M).group(1))
    return {k: get(k) for k in ("mag_n", "mag_d", "mag_t", "mag_ring_d", "mag_skin", "mag_depth_clear")}


def main():
    P = params()
    n, d, t = int(P["mag_n"]), int(P["mag_d"]), int(P["mag_t"])
    tmp = tempfile.mkdtemp(prefix="deskmate_mag_")
    print(f"Magnesy: {n} × Ø{d}×{t} mm N52 na okręgu Ø{P['mag_ring_d']:.0f} mm, skórka {P['mag_skin']} mm")
    print(f"Założenia: k_ring = {K_RING}, k_dyn = {K_DYN}, tilt −{TILT:.0f}°\n")
    variants = [(n, d, t), (12, 10, 3)] if (d, t) != (10, 3) else [(n, d, t)]
    print(f"{'profil':14s} {'m [g]':>6s} {'F_wym [N]':>9s} " +
          " ".join(f"{f'{vn}×Ø{vd}×{vt} g={g:.1f}':>18s}" for vn, vd, vt in variants for g in (1.7, 2.7)))
    for prof in PROFILES:
        I = info(prof, tmp)
        m = I["mphone"] / 1000
        h_cg = I["H"] / 2
        h_mag = I["pad_cz"] - (I["cz"] - I["H"] / 2)
        f_req = K_DYN * m * G * math.sin(math.radians(TILT)) * h_cg / h_mag
        row = f"{prof:14s} {I['mphone']:6.0f} {f_req:9.2f} "
        for vn, vd, vt in variants:
            for g in (P["mag_skin"] + P["mag_depth_clear"] + 1.0, P["mag_skin"] + P["mag_depth_clear"] + 2.0):
                F, f0, dec = holding(vn, vd, vt, g)
                row += f"{F:7.1f} N (×{F / f_req:4.1f})  "
        print(row)
    F, f0, dec = holding(n, d, t, P["mag_skin"] + P["mag_depth_clear"] + 1.0)
    print(f"\nPojedynczy magnes Ø{d}×{t}: F0 ≈ {f0:.0f} N na grubej stali, przy szczelinie "
          f"{P['mag_skin'] + P['mag_depth_clear'] + 1.0:.1f} mm ×{dec:.2f}, na cienkim pierścieniu ×{K_RING}")
    print("Bez półki (sam magnes, tarcie μ≈0.3) potrzeba ≈ m·g·cos(25°)/0.3 – dlatego półka jest konieczna.")


if __name__ == "__main__":
    main()
