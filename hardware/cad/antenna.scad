// =====================================================================
//  DeskMate DIY – ANTENKA z diodą na czubku kasku
//  Kołnierz z 2 x M3 (wkładki w kasku), pręt z kanałem Ø3.5 na 3 przewody,
//  kulka z gniazdem Ø12 na WS2812 (5 mm albo mały moduł) i przykrywka
//  z przezroczystego/białego PETG. Spód kulki stożkowy (45°) – druk
//  pionowo bez podpór, pogrubiona podstawa pręta.
//  -D 'part="antenna"' lub 'part="cap"'.
// =====================================================================
include <params.scad>
part = "antenna"; // [antenna, cap]

ball_z = 3 + ant_h + ant_ball/2;
module antenna() {
  difference() {
    union() {
      cylinder(d1 = ant_flange, d2 = ant_flange - 2, h = 3, $fn = 64);
      cylinder(d1 = 14, d2 = ant_d, h = 10, $fn = 48);                // pogrubiona nasada
      cylinder(d = ant_d, h = ball_z, $fn = 48);
      // dolna połowa kulki: stożek 45° + czasza
      intersection() {
        translate([0, 0, ball_z]) sphere(d = ant_ball, $fn = 64);
        translate([0, 0, ball_z - ant_ball/2]) cylinder(d = ant_ball + 1, h = ant_ball/2, $fn = 64);
      }
      translate([0, 0, ball_z - ant_ball/2*0.7 - (ant_ball/2*0.71 - ant_d/2)])
        cylinder(d1 = ant_d, d2 = ant_ball*0.71, h = ant_ball/2*0.71 - ant_d/2 + 0.01, $fn = 64);
    }
    translate([0, 0, ball_z - ant_ball/2 + 2.5]) cylinder(d = ant_cav, h = ant_ball, $fn = 48);   // gniazdo diody
    translate([0, 0, -1]) cylinder(d = 3.5, h = ball_z, $fn = 24);                                  // przewody
    for (s = [-1, 1]) translate([s*8, 0, -1]) {
      cylinder(d = m3_clear, h = 10, $fn = 24);
      translate([0, 0, 1 + 3 - m3_csk_d/2 + 0.6]) cylinder(d1 = 0, d2 = m3_csk_d, h = m3_csk_d/2, $fn = 24);
    }
  }
}
// przykrywka: górna połowa kulki z wciskanym pierścieniem
module antenna_cap() {
  difference() {
    union() {
      intersection() { sphere(d = ant_ball, $fn = 64); translate([0, 0, 0]) cylinder(d = ant_ball + 1, h = ant_ball); }
      translate([0, 0, -2]) cylinder(d = ant_cav - 0.4, h = 2.01, $fn = 48);
    }
    translate([0, 0, -3]) cylinder(d = ant_cav - 2.4, h = 7, $fn = 48);
  }
}
if (is_undef(ASM)) {
  if (part == "antenna") antenna();
  else translate([0, 0, 2]) antenna_cap();
}
