#!/usr/bin/env bash
# Generuje STL (hardware/stl) i podglądy PNG (hardware/renders) z plików OpenSCAD.
# Użycie:  OPENSCAD=/ścieżka/do/openscad ./hardware/tools/build.sh [stl|png|all]
set -euo pipefail
cd "$(dirname "$0")/.."
OSC=${OPENSCAD:-openscad}
MODE=${1:-all}
PROFILES="s23 fold8_open fold8_closed flip6_open"
mkdir -p stl renders

stl() { # plik_wyj plik_scad [parametry -D...]
  local out=$1 src=$2; shift 2
  echo ">> stl/$out"
  "$OSC" -o "stl/$out" "$@" "cad/$src" 2>&1 | grep -E "WARNING|ERROR" || true
}
png() { # plik_wyj plik_scad kamera [parametry]
  local out=$1 src=$2 cam=$3; shift 3
  echo ">> renders/$out"
  xvfb-run -a "$OSC" -o "renders/$out" --imgsize=1200,900 --viewall --autocenter \
    --colorscheme=Tomorrow --camera="$cam" "$@" "cad/$src" > /dev/null 2>&1 || true
}

if [[ $MODE == stl || $MODE == all ]]; then
  stl base.stl            base.scad    -D 'part="base"'
  stl cover.stl           base.scad    -D 'part="cover"'
  stl yoke.stl            yoke.scad
  stl pan_hub.stl         pan_hub.scad
  stl bracket_servo.stl   bracket.scad -D 'side="servo"'
  stl bracket_bearing.stl bracket.scad -D 'side="bearing"'
  stl spacer.stl          spacer.scad
  for p in $PROFILES; do
    stl "head_plate_$p.stl" head_plate.scad -D "profile=\"$p\""
    stl "clamp_$p.stl"      clamp.scad      -D "profile=\"$p\""
  done
fi

if [[ $MODE == png || $MODE == all ]]; then
  png asm_fold8_open_front.png assembly.scad 0,0,0,70,0,205,1000  -D 'profile="fold8_open"'
  png asm_fold8_open_back.png  assembly.scad 0,0,0,70,0,330,1000 -D 'profile="fold8_open"'
  png asm_fold8_open_side_down.png assembly.scad 0,0,0,90,0,90,1000 -D 'profile="fold8_open"' -D 'tilt=-25'
  png asm_fold8_open_side_up.png   assembly.scad 0,0,0,90,0,90,1000 -D 'profile="fold8_open"' -D 'tilt=30'
  png asm_fold8_open_pan80_down.png assembly.scad 0,0,0,60,0,230,1000 -D 'profile="fold8_open"' -D 'pan=80' -D 'tilt=-25'
  png asm_s23_front.png        assembly.scad 0,0,0,70,0,205,1000  -D 'profile="s23"'
  png asm_flip6_open_front.png assembly.scad 0,0,0,70,0,205,1000  -D 'profile="flip6_open"'
  png part_base.png  base.scad 0,0,0,55,0,25,600 -D 'part="base"'
  png part_cover.png base.scad 0,0,0,55,0,25,600 -D 'part="cover"'
  png part_yoke.png  yoke.scad 0,0,0,60,0,35,600
  png part_bracket_servo.png bracket.scad 0,0,0,50,0,30,300 -D 'side="servo"'
  for p in $PROFILES; do
    png "part_head_plate_$p.png" head_plate.scad 0,0,0,50,0,20,500 -D "profile=\"$p\""
  done
fi
