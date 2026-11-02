// =====================================================================
//  DeskMate DIY – WSPORNIKI GŁOWY (2 części: od strony serwa i łożyska)
//  Przykręcane do tyłu płyty głowy 2 x M3x10 (łeb stożkowy od przodu
//  płyty, wkładki w kołnierzu). Strona +X: orczyk serwa TILT.
//  Strona -X: śruba M8 jako oś w łożysku 608.
//  -D 'side="servo"' lub 'side="bearing"'. Druk: zewnętrzną ścianą do stołu.
// =====================================================================
include <params.scad>
side = "servo"; // [servo, bearing]

module bracket_profile2d() {   // x -> Y, y -> Z
  hull() {
    circle(r = hub_r);
    translate([slab_back() - br_fl_t, -br_fl_hz]) square([br_fl_t, 2*br_fl_hz]);
  }
}

// wspornik dla strony +X (strona łożyska = lustro)
module bracket_body(kind) {
  difference() {
    union() {
      yz_extrude(br_x - br_t, br_x) bracket_profile2d();
      translate([br_fl_x0, slab_back() - br_fl_t, -br_fl_hz])
        cube([br_x - br_fl_x0, br_fl_t, 2*br_fl_hz]);
    }
    // wkładki M3 pod śruby od płyty głowy
    for (p = br_screw) translate([p[0], 0, p[1]]) cyl_y(ins_d, slab_back() - ins_l, slab_back() + 1);
    if (kind == "servo") {
      // gniazdo tarczy orczyka + otwór dostępu do śruby wałka
      cyl_x(sv_hornd(servo_tilt) + 0.6, br_x - 1.5, br_x + 1);
      cyl_x(9, br_x - br_t - 1, br_x + 1);
      for (a = [0 : 90 : 270]) rotate([a, 0, 0]) translate([0, horn_hole_r[1], 0])
        cyl_x(1.8, br_x - 9, br_x + 1);
      for (a = [45 : 90 : 315]) rotate([a, 0, 0]) translate([0, horn_hole_r[0], 0])
        cyl_x(1.6, br_x - 9, br_x + 1);
    } else {
      // przelot M8 + gniazdo łba sześciokątnego od środka
      cyl_x(m8_clear, br_x - br_t - 1, br_x + 1);
      translate([br_x - br_t - 1, 0, 0]) rotate([0, 90, 0])
        cylinder(d = m8_head_af / cos(30), h = m8_head_t + 1, $fn = 6);
    }
  }
}

module bracket(kind) {          // w układzie głowy
  if (kind == "servo") bracket_body("servo");
  else mirror([1, 0, 0]) bracket_body("bearing");
}

// tuleja dystansowa (wspornik -> pierścień wewn. 608)
module spacer() {
  difference() { cylinder(d = 11.5, h = spacer_len); translate([0, 0, -1]) cylinder(d = m8_clear, h = spacer_len + 2); }
}

if (is_undef(ASM)) {
  translate([0, 0, br_x]) rotate([0, 90, 0]) bracket_body(side);
}
