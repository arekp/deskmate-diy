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
  "$OSC" -o "stl/$out" "$@" "cad/$src" 2>&1 | grep -E "ERROR" || true
}
png() { # plik_wyj plik_scad kamera [parametry]
  local out=$1 src=$2 cam=$3; shift 3
  echo ">> renders/$out"
  xvfb-run -a "$OSC" -o "renders/$out" --imgsize=1400,1100 --viewall --autocenter \
    --colorscheme=Tomorrow --camera="$cam" "$@" "cad/$src" > /dev/null 2>&1 || true
}

if [[ $MODE == stl || $MODE == all ]]; then
  stl body.stl            base.scad    -D 'part="base"'
  stl body_cover.stl      base.scad    -D 'part="cover"'
  stl neck_collar.stl     base.scad    -D 'part="collar"'
  stl neck_shoulders.stl  yoke.scad    -D 'part="neck"'
  stl arm_servo.stl       yoke.scad    -D 'part="arm_servo"'
  stl arm_bearing.stl     yoke.scad    -D 'part="arm_bearing"'
  stl ear_cap_servo.stl   yoke.scad    -D 'part="cap_servo"'
  stl ear_cap_bearing.stl yoke.scad    -D 'part="cap_bearing"'
  stl pan_hub.stl         yoke.scad    -D 'part="hub"'
  stl head_mount_servo.stl   bracket.scad -D 'side="servo"'
  stl head_mount_bearing.stl bracket.scad -D 'side="bearing"'
  stl spacers.stl         spacer.scad
  stl antenna.stl         antenna.scad -D 'part="antenna"'
  stl antenna_cap.stl     antenna.scad -D 'part="cap"'
  stl magnet_disc.stl     magnet_disc.scad
  for p in $PROFILES; do
    stl "head_front_$p.stl" head_plate.scad -D "profile=\"$p\""
    stl "head_back_$p.stl"  head_back.scad  -D "profile=\"$p\""
  done
fi

if [[ $MODE == png || $MODE == all ]]; then
  F=0,0,0,78,0,200,1100      # przód (lekko z prawej robota)
  S=0,0,0,85,0,270,1100      # bok
  Q=0,0,0,70,0,225,1100      # 3/4
  B=0,0,0,70,0,35,1100       # 3/4 z tyłu
  for p in $PROFILES; do
    png "asm_front_$p.png" assembly.scad $F -D "profile=\"$p\""
    png "asm_34_$p.png"    assembly.scad $Q -D "profile=\"$p\""
    png "asm_side_$p.png"  assembly.scad $S -D "profile=\"$p\""
    png "part_head_front_$p.png" head_plate.scad 0,0,0,45,0,20,650 -D "profile=\"$p\""
  done
  png asm_back_fold8_open.png  assembly.scad $B -D 'profile="fold8_open"'
  png asm_side_fold8_open_tilt-25.png assembly.scad $S -D 'profile="fold8_open"' -D 'tilt=-25'
  png asm_side_fold8_open_tilt+30.png assembly.scad $S -D 'profile="fold8_open"' -D 'tilt=30'
  png asm_34_fold8_open_pan80_tilt-25.png assembly.scad $Q -D 'profile="fold8_open"' -D 'pan=80' -D 'tilt=-25'
  png part_magnet_disc_back.png magnet_disc.scad 0,0,0,145,0,20,300
  png part_body.png  base.scad 0,0,0,55,0,25,700 -D 'part="base"'
  png part_cover.png base.scad 0,0,0,50,0,25,700 -D 'part="cover"'
  png part_neck_shoulders.png yoke.scad 0,0,0,50,0,25,600 -D 'part="neck"'
  png part_arm_bearing.png yoke.scad 0,0,0,40,0,25,400 -D 'part="arm_bearing"'
  png part_head_mount_servo.png bracket.scad 0,0,0,50,0,30,300 -D 'side="servo"'
  png part_head_back_s23.png  head_back.scad  0,0,0,45,0,20,600 -D 'profile="s23"'
  png part_antenna.png antenna.scad 0,0,0,70,0,30,150
fi
