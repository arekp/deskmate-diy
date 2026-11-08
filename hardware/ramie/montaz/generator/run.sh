#!/bin/sh
# Rysunki instrukcji montażu: eksport części w układzie montażowym, potem kroki i mapa.
# Wymaga numpy, scipy, scikit-image, Pillow. Wynik: PNG tutaj, JPG w ../img.
set -e
cd "$(dirname "$0")"
python3 export.py
python3 steps.py
python3 overview.py
W=900; python3 -c "from PIL import Image; i=Image.open('mapa.png').convert('RGB'); i.resize((900, i.size[1]*900//i.size[0])).save('../img/mapa.jpg', quality=85)"
for f in krok1 krok2 krok3 krok4 krok5 krok6 krok7 krok8; do
  python3 -c "from PIL import Image; Image.open('$f.png').convert('RGB').resize((850, int(Image.open('$f.png').size[1]*850/Image.open('$f.png').size[0]))).save('../img/$f.jpg', quality=85)" 
done
