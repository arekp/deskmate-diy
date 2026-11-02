// =====================================================================
//  DeskMate DIY – ZŁOŻENIE (podgląd kolizji i zakresu ruchu)
//  Ustaw pan / tilt / profile i obejrzyj (F5). Telefon = prosta bryła
//  w etui. Moduły "show" służą też do eksportu grup do skryptu kolizji.
// =====================================================================
ASM = true;
include <head_plate.scad>
include <bracket.scad>
include <yoke.scad>
include <base.scad>

pan = 0;     // [-80:5:80]
tilt = 0;    // [-25:5:30]
show = "all"; // [all, g_base, g_yoke, g_head, g_head_plastic, info]
ghost_phone = true;

// --- atrapy elementów kupnych ---
module servo_dummy(s, horn = true) {          // wałek w (0,0,0), oś w +Z, korpus w +Y
  c = sv_c(s);
  translate([-sv_w(s)/2, -sv_sh(s), -sv_ht(s) - sv_ft(s) - sv_hb(s)])
    cube([sv_w(s), sv_l(s), sv_hb(s)]);                          // pod uszami
  translate([-sv_w(s)/2, c - sv_el(s)/2, -sv_ht(s) - sv_ft(s)])
    cube([sv_w(s), sv_el(s), sv_ft(s)]);                         // uszy
  translate([-sv_w(s)/2, -sv_sh(s), -sv_ht(s)])
    cube([sv_w(s), sv_l(s), sv_ha(s)]);                          // nad uszami
  if (horn) translate([0, 0, -sv_hornt(s)]) cylinder(d = sv_hornd(s), h = sv_hornt(s)); // tarcza orczyka
}
module tilt_servo_dummy(horn = true) {      // w układzie osi TILT
  // wałek na osi TILT skierowany do środka (-X), korpus do tyłu (-Y)
  multmatrix([[0, 0, -1, br_x], [0, -1, 0, 0], [-1, 0, 0, 0], [0, 0, 0, 1]]) servo_dummy(servo_tilt, horn);
}
module pan_servo_dummy() { translate([0, 0, z_horn_face()]) servo_dummy(servo_pan); }
module lazy_susan_dummy() {
  translate([0, 0, base_h]) linear_extrude(ls_t) difference() {
    if (ls_shape == "square") square(ls_sq, center = true); else circle(d = ls_round_od);
    circle(d = ls_id);
  }
}
module pad_dummy() {
  translate([0, 0, pad_cz()]) cyl_y(pad_d(), pad_top_y() - pad_h(), pad_top_y());
}
module phone_box() {
  translate([ph_cx() - ph_L()/2, D_back, ph_bot()]) cube([ph_L(), ph_T(), ph_H()]);
}
module m8_axle() { cyl_x(8, -arm_l_out - 8, -(br_x - br_t) + m8_head_t); }

// --- grupy ---
module g_head_plastic() {
  head_plate();
  bracket("servo"); bracket("bearing");
  for (sx = [-1, 1]) translate([sx*clamp_x, 0, 0]) clamp_part();
  translate([-br_x, 0, 0]) rotate([0, -90, 0]) spacer();
}
module tilt_horn() { cyl_x(sv_hornd(servo_tilt), br_x, br_x + sv_hornt(servo_tilt)); }
module g_head() { g_head_plastic(); pad_dummy(); m8_axle(); tilt_horn(); }
module g_yoke() { yoke(); tilt_servo_dummy(false); }
module g_base() { base_shell(); cover(); pan_servo_dummy(); }

module head_frame() { translate([0, tilt_y, z_axis]) rotate([tilt, 0, 0]) children(); }
module yoke_frame() { translate([0, tilt_y, z_axis]) children(); }

if (show == "info") {
  echo(str("INFO|", "profile=", profile, "|L=", ph_L(), "|H=", ph_H(), "|T=", ph_T(),
           "|cx=", ph_cx(), "|cz=", ph_cz(), "|mphone=", ph_m(), "|D_back=", D_back,
           "|pad_cz=", pad_cz(), "|pad_h=", pad_h(), "|pad_m=", pad_m(), "|pad_top_y=", pad_top_y(),
           "|tilt_y=", tilt_y, "|z_axis=", z_axis, "|base_h=", base_h,
           "|z_deck_top=", z_deck_top, "|tilt_min=", tilt_rng[0], "|tilt_max=", tilt_rng[1],
           "|pan_min=", pan_rng[0], "|pan_max=", pan_rng[1], "|sv_tilt_m=", sv_m(servo_tilt),
           "|sv_pan_m=", sv_m(servo_pan), "|br_x=", br_x, "|arm_r_in=", arm_r_in(),
           "|slab_back=", slab_back(), "|fan_on=", fan_on ? 1 : 0, "|sv_tilt_len=", sv_l(servo_tilt), "|sv_tilt_c=", sv_c(servo_tilt),
           "|arm_l_out=", arm_l_out, "|esp=", esp_pos, "|ballast=", ballast, "|feet_x=", 70, "|feet_y0=", -62, "|feet_y1=", 58,
           "|base_x0=", base_x[0], "|base_x1=", base_x[1], "|base_y0=", base_y[0], "|base_y1=", base_y[1]));
  cube(1);
}
else if (show == "g_base") g_base();
else if (show == "g_yoke") g_yoke();
else if (show == "g_head") g_head();
else if (show == "g_head_plastic") g_head_plastic();
else {
  color("SteelBlue") base_shell();
  color("SlateGray") cover();
  color("Silver", 0.8) lazy_susan_dummy();
  rotate([0, 0, pan]) {
    color("DimGray") pan_servo_dummy();
    color("Orange") pan_hub();
    yoke_frame() { color("Orange") yoke(); color("DimGray") tilt_servo_dummy(); }
    head_frame() {
      color("Gold") head_plate();
      color("Orange") { bracket("servo"); bracket("bearing"); }
      color("Tomato") { for (sx = [-1, 1]) translate([sx*clamp_x, 0, 0]) clamp_part(); }
      color("White") translate([-br_x, 0, 0]) rotate([0, -90, 0]) spacer();
      color("Black") pad_dummy();
      color("Silver") m8_axle();
      if (ghost_phone) color("LightSkyBlue", 0.45) phone_box();
    }
  }
}
