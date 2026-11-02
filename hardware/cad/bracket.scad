// =====================================================================
//  DeskMate DIY – UCHWYTY W GŁOWIE: serwo TILT (+X) i oś z łożyskiem (-X)
//  Przykręcane od tyłu do płyty kasku (2 x M3x10 każdy, wkładki w płycie).
//  Serwo TILT siedzi w głowie korpusem w dół; jego orczyk jest przykręcony
//  do ucha jarzma. Po drugiej stronie śruba M8 przez łożysko 608 w uchu
//  jarzma wkręca się w nakrętkę uwięzioną w uchwycie.
//  -D 'side="servo"' lub 'side="bearing"'. Druk: płytą do stołu.
// =====================================================================
include <params.scad>
side = "servo"; // [servo, bearing]

module mount_tabs(x0, x1) {
  for (tz = tab_z) difference() {
    translate([x0, slab_back() - 7, tz[0]]) cube([x1 - x0, 7, tz[1] - tz[0]]);
    translate([tab_x, 0, (tz[0] + tz[1])/2]) {
      cyl_y(m3_clear, slab_back() - 8, slab_back() + 1);
      cyl_y(6.2, slab_back() - 8, slab_back() - 4);
    }
  }
}

module servo_mount() {      // układ głowy, strona +X
  s = servo_tilt;
  zb0 = -(sv_l(s) - sv_sh(s));
  difference() {
    union() {
      yz_extrude(mount_x0(), mount_x1()) rrect([-14, tab_z[1][0]], [slab_back(), tab_z[0][1]], 4);
      mount_tabs(tab_x - 10, mount_x1());
    }
    // okno korpusu
    translate([mount_x0() - 1, -sv_w(s)/2 - 0.4, zb0 - 0.4]) cube([mount_t + 2, sv_w(s) + 0.8, sv_l(s) + 0.8]);
    // otwory uszu + gniazda nakrętek od środka głowy
    for (h = sv_holes(s)) translate([0, h[1], -h[0]]) {
      cyl_x(m3_clear, mount_x0() - 1, mount_x1() + 1);
      translate([mount_x0() - 0.5, 0, 0]) rotate([0, 90, 0]) cylinder(d = m3_nut_af / cos(30), h = 3, $fn = 6);
    }
  }
}

module bearing_mount() {    // układ głowy, strona -X
  difference() {
    union() {
      yz_extrude(-brg_x1, -brg_x0) hull() {
        circle(r = hub_r);
        rrect([-14, tab_z[1][0]], [slab_back(), tab_z[0][1]], 4);
      }
      mirror([1, 0, 0]) mount_tabs(tab_x - 10, brg_x0 + 2);
    }
    cyl_x(m8_clear, -brg_x1 - 1, -brg_x0 + 1);
    // nakrętka M8 uwięziona od środka
    translate([-brg_x0 - 6.6, 0, 0]) rotate([0, 90, 0]) cylinder(d = 13.2 / cos(30), h = 7, $fn = 6);
  }
}

// tuleje dystansowe: wewn. (uchwyt -> pierścień 608) i zewn. (608 -> łeb M8)
module spacer(l = spacer_len) {
  difference() { cylinder(d = 11.5, h = l); translate([0, 0, -1]) cylinder(d = m8_clear, h = l + 2); }
}

if (is_undef(ASM)) {
  if (side == "servo") translate([0, 0, mount_x1()]) rotate([0, 90, 0]) servo_mount();
  else translate([0, 0, brg_x1]) rotate([0, -90, 0]) bearing_mount();
}
