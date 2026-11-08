#!/usr/bin/env bash
# Generuje wszystkie STL wariantu z ramieniem do katalogu podanego jako argument (domyślnie ../stl).
# Wymaga: python3, numpy, scipy, scikit-image. Czas: ok. 2 min.
set -e
cd "$(dirname "$0")"
OUT="${1:-../stl}"
python3 parts_arm.py "$OUT" ramie
python3 parts_arm.py "$OUT" przedramie
python3 parts_rest.py "$OUT"
python3 parts_body_head.py "$OUT" body plinth holder accents
python3 testpiece.py "$OUT"
python3 make_3mf.py "$OUT" "${2:-../3mf}"
python3 dfam_lite.py "$OUT"
