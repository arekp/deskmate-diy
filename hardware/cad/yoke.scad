// =====================================================================
//  DeskMate DIY – JARZMO (U) + PIASTA PAN
//  Pokład obraca się na lazy susan; piasta (osobny wydruk, 3 x M3 do
//  pokładu) przenosi moment z orczyka serwa PAN.
//  Ramię +X: serwo TILT (wałek na osi, korpus do tyłu, uszy na wewn. ścianie).
//  Ramię -X: łożysko 608 wciskane od zewnątrz.
//  Układ: oś TILT (jak głowa). Druk: pokładem do stołu, bez podpór.
// =====================================================================
include <params.scad>

pan_y = -tilt_y;                       // oś PAN w układzie osi TILT
function g2a(zg) = zg - z_axis;       // wysokość globalna -> układ osi

// profil ramienia (x -> Y, y -> Z) – poza zasięgiem tyłu telefonu
module arm_profile2d() {
  intersection() {
    hull() {
      polygon([[-40, -17], [12, -17], [19, -13], [19, 13], [12, 17], [-40, 17]]);
      translate([post_y[0], z_deck - 1]) square([post_y[1] - post_y[0], 6]);
    }
    for (t = tilt_rng) rotate(t) translate([clear_r - 2000, -1000]) square([2000, 2000]);
  }
}

module deck2d() {
  hull() {
    translate([0, pan_y]) circle(r = deck_r, $fn = 96);
    translate([-arm_l_out - 12, post_y[0]]) square([arm_l_out + arm_r_out() + 24, post_y[1] - post_y[0]]);
  }
}

module gusset(x_in, dir) {     // dir = +1 rośnie w +X, -1 w -X
  translate([x_in, post_y[0], z_deck - 0.01]) rotate([90, 0, 0]) mirror([0, 0, 1])
    linear_extrude(post_y[1] - post_y[0])
      polygon([[0, 0], [dir*12, 0], [0, 22]]);
}

module yoke() {
  difference() {
    union() {
      // pokład
      translate([0, 0, z_deck - deck_t]) linear_extrude(deck_t) deck2d();
      // ramiona
      yz_extrude(arm_r_in(), arm_r_out()) arm_profile2d();
      yz_extrude(-arm_l_out, -arm_l_in) arm_profile2d();
      // żebra
      gusset(arm_r_in() + 0.01, -1);  gusset(arm_r_out() - 0.01, 1);
      gusset(-arm_l_in - 0.01, 1);    gusset(-arm_l_out + 0.01, -1);
    }
    // --- serwo TILT: okno korpusu, otwory uszu, gniazda nakrętek
    translate([arm_r_in() - 1, -(sv_l(servo_tilt) - sv_sh(servo_tilt)) - 0.4, -sv_w(servo_tilt)/2 - 0.4])
      cube([arm_r_t + 2, sv_l(servo_tilt) + 0.8, sv_w(servo_tilt) + 0.8]);
    for (h = sv_holes(servo_tilt)) translate([0, -h[0], h[1]]) {
      cyl_x(m3_clear, arm_r_in() - 1, arm_r_out() + 1);
      translate([arm_r_out() - 2.5, 0, 0]) rotate([0, 90, 0]) cylinder(d = m3_nut_af / cos(30), h = 3, $fn = 6);
    }
    // --- łożysko 608 od zewnątrz + przelot kołnierza
    tear_x(b608_od + b608_fit, -arm_l_out - 1, -arm_l_out + b608_w);
    tear_x(16, -arm_l_out - 1, -arm_l_in + 1);
    // --- przelot przewodów w pokładzie (nad łukiem w podstawie)
    translate([0, pan_y - wire_r, z_deck - deck_t - 1]) cylinder(d = wire_w, h = deck_t + 2);
    // --- otwór dostępu do śruby wałka PAN
    translate([0, pan_y, z_deck - deck_t - 1]) cylinder(d = 8, h = deck_t + 2);
    // --- wkładki M3 lazy susan (od spodu) i śruby piasty (stożkowe od góry)
    for (p = ls_top_holes()) translate([p[0], pan_y + p[1], z_deck - deck_t - 0.01]) cylinder(d = ins_d, h = ins_l);
    for (a = [45, 165, 285]) translate([14*cos(a), pan_y + 14*sin(a), z_deck - deck_t - 1]) {
      cylinder(d = m3_clear, h = deck_t + 2);
      translate([0, 0, deck_t + 1 - m3_csk_d/2]) cylinder(d1 = 0, d2 = m3_csk_d, h = m3_csk_d/2 + 0.01);
      translate([0, 0, deck_t + 1]) cylinder(d = m3_csk_d, h = 5);
    }
    // --- otwory na opaski (przewody wzdłuż ramion)
    for (x = [arm_r_in() - 17, -arm_l_in + 17]) for (y = [post_y[0] + 10, post_y[1] - 6])
      translate([x - 2, y - 1.5, z_deck - deck_t - 1]) cube([4, 3, deck_t + 2]);
  }
}

// piasta PAN (osobny wydruk), w układzie globalnym
module pan_hub() {
  zb = z_horn_face() - 1.5;      // spód piasty
  difference() {
    union() {
      translate([0, 0, zb]) cylinder(d = hub_d, h = base_h - zb + 0.3);
      translate([0, 0, base_h + 0.3]) cylinder(d = hub_d2, h = z_ls_top - base_h - 0.3);
    }
    translate([0, 0, zb - 1]) cylinder(d = sv_hornd(servo_pan) + 0.6, h = 2.5);
    translate([0, 0, zb - 1]) cylinder(d = 8, h = 50);
    for (a = [0 : 90 : 270]) rotate(a) translate([horn_hole_r[1], 0, zb]) cylinder(d = 1.8, h = 8);
    for (a = [45 : 90 : 315]) rotate(a) translate([horn_hole_r[0], 0, zb]) cylinder(d = 1.6, h = 8);
    for (a = [45, 165, 285]) translate([14*cos(a), 14*sin(a), z_ls_top - ins_l]) cylinder(d = ins_d, h = ins_l + 1);
  }
}

if (is_undef(ASM)) {
  translate([0, 0, axis_above_deck + deck_t]) yoke();
}
