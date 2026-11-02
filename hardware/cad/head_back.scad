// =====================================================================
//  DeskMate DIY – KASK, tylna skorupa (chowa ładowarkę, serwo TILT,
//  przewody i wentylator). Zakładka 6 mm na stopień przodu kasku,
//  4 x M3 (góra i dół). Druk: płaskim tyłem do stołu, bez podpór
//  (boki pochylone maks. 45°).
// =====================================================================
ASM = true;
include <head_plate.scad>

function hb_inset(y) = y >= dome_flat ? 0 : pow(dome_flat - y, 2) / (2*(dome_flat - dome_back));
module hb_body(d = 0, y0 = dome_back, y1 = slab_back() + skirt) {
  hull() for (y = concat([y0], [for (t = [y0 + 3 : 3 : dome_flat]) t], [dome_flat, y1]))
    xz_extrude(y - 0.01, y) hd_outline(d - hb_inset(y));
}

module head_back() {
  ytop = slab_back() + skirt;
  difference() {
    hb_body(0);
    hb_body(-shell_t, dome_back + shell_t, ytop + 1);
    // okno na górną część serwa TILT (zasłania je ucho jarzma)
    translate([head_w - shell_t - 1, -sv_w(servo_tilt)/2 - 0.6, -(sv_l(servo_tilt) - sv_sh(servo_tilt)) - 0.6])
      cube([shell_t + 2, sv_w(servo_tilt) + 1.2, sv_l(servo_tilt) + 1.2]);
    // przelot tulei osi po stronie łożyska + wyjście przewodów
    cyl_x(16, -head_w - 1, -head_w + shell_t + 1);
    translate([0, cable_exit[0], cable_exit[1]]) cyl_x(10, -head_w - 1, -head_w + shell_t + 1);
    // śruby do przodu kasku
    for (s = [-1, 1]) translate([s*dome_screw_x, ytop - skirt/2, 0]) {
      translate([0, 0, hd_zt() - shell_t - 1]) cylinder(d = m3_clear, h = shell_t + 2);
      translate([0, 0, hd_zb() - 1]) cylinder(d = m3_clear, h = shell_t + 2);
    }
    // kratka wentylacyjna z tyłu
    intersection() {
      for (x = [-60 : 8 : 60]) translate([x - 1.75, dome_back - 1, hd_zb()]) cube([3.5, shell_t + 2, hd_zt() - hd_zb()]);
      xz_extrude(dome_back - 2, dome_back + shell_t + 2) offset(r = -22) hd_outline(-hb_inset(dome_back));
    }
  }
}

if (is_undef(ASM_ALL)) translate([0, 0, -dome_back]) rotate([-90, 0, 0]) head_back();
