#!/usr/bin/env python3
"""Raport drukowalności (DfAM, FDM) dla wszystkich STL DeskMate DIY.

Mierzy każdy plik narzędziem skilla dfam-check (.claude/skills/dfam-check,
z earthtojake/text-to-cad, MIT) i porównuje z limitami FDM z
references/process-limits.md: ścianka nośna ≥ 1,2 mm, kąt samonośny 45°.
STL leżą już w orientacji do druku (patrz hardware/cad/README.md).

Użycie:
  pip install -r .claude/skills/dfam-check/requirements.txt
  python3 hardware/tools/dfam_report.py [--out raport.md] [plik.stl ...]

Kod wyjścia 1 tylko dla nieszczelnej siatki (blokuje krojenie);
cienkie ścianki i nawisy to ostrzeżenia, bo pomiar jest próbkowany.
"""
import argparse
import glob
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
TOOL = os.path.join(ROOT, ".claude", "skills", "dfam-check", "scripts", "dfam_tool.py")
WALL_MIN = 1.2      # mm, FDM – min. ścianka podparta
ANGLE = 45.0        # stopnie od poziomu – FDM
SUPPORT_WARN = 30.0 # % objętości części – próg sensowności zmiany orientacji


def run(cmd, path):
    p = subprocess.run([sys.executable, TOOL, cmd, path, "--angle-limit", str(ANGLE)],
                       capture_output=True, text=True)
    try:
        return json.loads(p.stdout)
    except json.JSONDecodeError:
        return {"error": (p.stderr or p.stdout).strip()[-300:]}


def fmt(v, unit=""):
    return "–" if v is None else f"{v:g}{unit}"


def check(path):
    m = run("measure", path)
    name = os.path.basename(path)
    if "error" in m and "mesh" not in m:
        return {"name": name, "fatal": True, "row": f"| `{name}` | ❌ {m['error']} | | | | |", "notes": []}
    mesh, ov, wall, sup = m["mesh"], m["overhangs"], m["wall_thickness"], m["support_volume"]
    notes = []
    fatal = not mesh.get("watertight", False)
    tight = "✅" if not fatal else "❌ nieszczelna"
    if m["scale"].get("units_suspect"):
        notes.append(f"`{name}`: podejrzane jednostki (przekątna {m['scale'].get('bbox_diagonal_mm')} mm)")

    if "error" in wall:
        wall_cell = f"❓ {wall['error']}"
    else:
        p05, mn = wall.get("p05_mm"), wall.get("min_mm")
        ok = p05 is not None and p05 >= WALL_MIN
        wall_cell = f"{'✅' if ok else '⚠️'} p05 {fmt(p05)} / min {fmt(mn)}"
        if not ok:
            where = (wall.get("thinnest_samples") or [{}])[0].get("location_xyz")
            notes.append(f"`{name}`: ścianka p05 {fmt(p05)} mm < {WALL_MIN} mm (najcieńsze miejsce ~{where})")

    if "error" in ov:
        ov_cell = f"❓ {ov['error']}"
    else:
        ov_cell = f"{fmt(ov['down_facing_area_below_limit_mm2'])} mm² ({fmt(ov['down_facing_area_below_limit_pct'])} %)"

    if "error" in sup:
        sup_cell = "❓"
    else:
        ratio = sup.get("support_to_part_ratio_pct")
        sup_cell = f"{'⚠️ ' if ratio and ratio > SUPPORT_WARN else ''}{fmt(ratio, ' %')}"
        if ratio and ratio > SUPPORT_WARN:
            o = run("orientations", path).get("orientations", {})
            cands = o.get("candidates") or []
            if cands:
                cur = next((c for c in cands if c["orientation"] == "current_plus_z"), cands[0])
                best = min(cands, key=lambda c: c["support_area_mm2"])
                if best["support_area_mm2"] < 0.5 * cur["support_area_mm2"]:
                    notes.append(f"`{name}`: orientacja `{best['orientation']}` ma {best['support_area_mm2']:g} mm² "
                                 f"nawisów zamiast {cur['support_area_mm2']:g} (wysokość {best['build_height_mm']:g} mm)")

    row = (f"| `{name}` | {tight} | {mesh.get('body_count')} | {wall_cell} | {ov_cell} | {sup_cell} |")
    return {"name": name, "fatal": fatal, "row": row, "notes": notes}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="*")
    ap.add_argument("--out")
    a = ap.parse_args()
    files = a.files or sorted(glob.glob(os.path.join(ROOT, "hardware", "stl", "*.stl")))
    results = [check(f) for f in files]
    lines = [
        "# Raport drukowalności (DfAM, FDM)",
        "",
        f"Limity FDM ([process-limits.md](../../.claude/skills/dfam-check/references/process-limits.md)): "
        f"ścianka ≥ {WALL_MIN} mm, nawisy poniżej {ANGLE:g}° od poziomu (bez powierzchni na stole). "
        "Ścianki mierzone próbkowo (p05 = 5. percentyl); podpory to zgrubna górna granica.",
        "",
        "| Plik | Szczelna | Bryły | Ścianka [mm] | Nawisy < 45° | Podpory / obj. |",
        "|---|---|---|---|---|---|",
        *[r["row"] for r in results],
    ]
    notes = [n for r in results for n in r["notes"]]
    if notes:
        lines += ["", "## Uwagi", "", *[f"- {n}" for n in notes]]
    text = "\n".join(lines) + "\n"
    print(text)
    if a.out:
        with open(a.out, "w", encoding="utf-8") as f:
            f.write(text)
    return 1 if any(r["fatal"] for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
