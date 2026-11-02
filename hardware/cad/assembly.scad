// =====================================================================
//  DeskMate DIY – ZŁOŻENIE ("ludzik na nóżce")
//  Ustaw pan / tilt / profile i obejrzyj (F5). Telefon = bryła w etui.
//  Moduły "show" służą też do eksportu grup do tools/collision_check.py.
// =====================================================================
ASM = true;
ASM_ALL = true;
include <head_plate.scad>
include <bracket.scad>
include <yoke.scad>
include <base.scad>
include <antenna.scad>
include <head_back.scad>

pan = 0;     // [-80:5:80]
tilt = 0;    // [-25:5:30]
show = "all"; // [all, g_base, g_yoke, g_head, g_head_plastic, g_yoke_plastic, g_base_plastic, info]
ghost_phone = true;

// --- atrapy elementów kupnych ---
module servo_dummy(s, horn = true) {          // wałek w (0,0,0), oś +Z, korpus w +Y
  c = sv_c(s);
  translate([-sv_w(s)/2, -sv_sh(s), -sv_ht(s) - sv_ft(s) - sv_hb(s)]) cube([sv_w(s), sv_l(s), sv_hb(s)]);
  translate([-sv_w(s)/2, c - sv_el(s)/2, -sv_ht(s) - sv_ft(s)]) cube([sv_w(s), sv_el(s), sv_ft(s)]);
  translate([-sv_w(s)/2, -sv_sh(s), -sv_ht(s)]) cube([sv_w(s), sv_l(s), sv_ha(s)]);
  translate([0, 0, -sv_ht(s)]) cylinder(d = 6, h = sv_ht(s) - sv_hornt(s) - 0.5);   // wałek
  if (horn) translate([0, 0, -sv_hornt(s)]) cylinder(d = sv_hornd(s), h = sv_hornt(s));
}
// serwo TILT w głowie: wałek w +X (do ucha), korpus w dół (-Z)
module tilt_servo_dummy() {
  multmatrix([[0, 0, 1, arm_in], [-1, 0, 0, 0], [0, -1, 0, 0], [0, 0, 0, 1]]) servo_dummy(servo_tilt, false);
}
module tilt_horn() { cyl_x(sv_hornd(servo_tilt), arm_in - sv_hornt(servo_tilt), arm_in); }
module pan_servo_dummy() { translate([0, 0, z_horn_face()]) servo_dummy(servo_pan); }
module lazy_susan_dummy() {
  translate([0, 0, base_h]) linear_extrude(ls_t) difference() {
    if (ls_shape == "square") square(ls_sq, center = true); else circle(d = ls_round_od);
    circle(d = ls_id);
  }
}
module pad_dummy() { translate([0, 0, pad_cz()]) cyl_y(pad_d(), pad_top_y() - pad_h(), pad_top_y()); }
module phone_box() { translate([ph_cx() - ph_L()/2, D_back, ph_bot()]) cube([ph_L(), ph_T(), ph_H()]); }
module m8_axle() {
  cyl_x(8, -arm_l_out - 2.5, -brg_x0);
  cyl_x(15, -arm_l_out - 2.5 - 5.3, -arm_l_out - 2.5);                 // łeb śruby
}
module head_spacers() {
  translate([-brg_x1, 0, 0]) rotate([0, -90, 0]) spacer();
  translate([-arm_l_out, 0, 0]) rotate([0, -90, 0]) spacer(2.5);
}
module antenna_asm() {
  translate([0, ant_y(), hd_zt()]) { antenna(); translate([0, 0, ball_z]) antenna_cap(); }
}

// --- grupy ---
module g_head_plastic() {
  head_front(); head_back(); visor(); servo_mount(); bearing_mount(); head_spacers(); antenna_asm();
}
module g_head() { g_head_plastic(); pad_dummy(); m8_axle(); tilt_servo_dummy(); }
module g_yoke_plastic() { yoke_asm(); }
module g_yoke() { yoke_asm(); tilt_horn(); }
module g_base_plastic() { base_shell(); cover(); collar(); }
module g_base() { g_base_plastic(); pan_servo_dummy(); }

module head_frame() { translate([0, tilt_y, z_axis]) rotate([tilt, 0, 0]) children(); }
module yoke_frame() { translate([0, tilt_y, z_axis]) children(); }

C_SHELL = "WhiteSmoke"; C_ACCENT = "DarkOrange"; C_DARK = "DimGray";

if (show == "info") {
  echo(str("INFO|", "profile=", profile, "|L=", ph_L(), "|H=", ph_H(), "|T=", ph_T(),
           "|cx=", ph_cx(), "|cz=", ph_cz(), "|mphone=", ph_m(), "|D_back=", D_back,
           "|pad_cz=", pad_cz(), "|pad_h=", pad_h(), "|pad_m=", pad_m(), "|pad_top_y=", pad_top_y(),
           "|tilt_y=", tilt_y, "|z_axis=", z_axis, "|base_h=", base_h,
           "|z_deck_top=", z_deck_top, "|tilt_min=", tilt_rng[0], "|tilt_max=", tilt_rng[1],
           "|pan_min=", pan_rng[0], "|pan_max=", pan_rng[1], "|sv_tilt_m=", sv_m(servo_tilt),
           "|sv_pan_m=", sv_m(servo_pan), "|arm_in=", arm_in, "|ear_top_x=", ear_top_x(),
           "|slab_back=", slab_back(), "|fan_on=", fan_on ? 1 : 0, "|sv_tilt_c=", sv_c(servo_tilt),
           "|feet=", feet, "|hd_zt=", hd_zt(), "|hd_zb=", hd_zb()));
  cube(1);
}
else if (show == "g_base") g_base();
else if (show == "g_yoke") g_yoke();
else if (show == "g_head") g_head();
else if (show == "g_head_plastic") g_head_plastic();
else if (show == "g_yoke_plastic") g_yoke_plastic();
else if (show == "g_base_plastic") g_base_plastic();
else {
  color(C_SHELL) base_shell();
  color(C_DARK) cover();
  color(C_ACCENT) collar();
  color("Silver") lazy_susan_dummy();
  rotate([0, 0, pan]) {
    color(C_DARK) pan_servo_dummy();
    color(C_DARK) pan_hub();
    yoke_frame() {
      color(C_ACCENT) yoke_neck();
      color(C_SHELL) { arm("servo"); mirror([1, 0, 0]) arm("bearing"); }
      color(C_ACCENT) {
        translate([arm_r_out, 0, 0]) rotate([0, 90, 0]) rotate([0, 0, 90]) ear_cap("servo");
        translate([-arm_l_out, 0, 0]) rotate([0, -90, 0]) rotate([0, 0, 90]) ear_cap("bearing");
      }
      color(C_DARK) tilt_horn();
    }
    head_frame() {
      color(C_SHELL) { head_front(); head_back(); }
      color(C_ACCENT) visor();
      color(C_DARK) { servo_mount(); bearing_mount(); head_spacers(); tilt_servo_dummy(); m8_axle(); }
      color("Black") pad_dummy();
      translate([0, ant_y(), hd_zt()]) { color(C_SHELL) antenna(); color("Gold") translate([0, 0, ball_z]) antenna_cap(); }
      if (ghost_phone) {
        color("#1a1a2e") phone_box();
        // "oczy" na ekranie – tylko do podglądu
        color("PaleTurquoise") for (s = [-1, 1])
          translate([ph_cx() + s*ph_L()*0.2, D_back + ph_T() + 0.05, ph_cz() + ph_H()*0.06])
            rotate([-90, 0, 0]) scale([1, 1.25, 1]) cylinder(r = min(ph_H(), ph_L())*0.13, h = 0.3, $fn = 48);
      }
    }
  }
}
