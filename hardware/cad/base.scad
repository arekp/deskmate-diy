// =====================================================================
//  DeskMate DIY – PODSTAWA (obudowa) + POKRYWA DOLNA
//  Obudowa: płyta górna z gniazdem lazy susan, ramka serwa PAN pod płytą,
//  łuk przelotu przewodów, wkładki M3 w narożnikach, wentylacja w bokach.
//  Pokrywa: gniazda modułów, komora obciążnika, wgłębienia na nóżki.
//  -D 'part="base"' lub 'part="cover"'. Układ globalny.
//  Druk obudowy: płytą górną do stołu (do góry dnem), bez podpór.
// =====================================================================
include <params.scad>
part = "base"; // [base, cover]

corner_boss = [for (sx = [-1, 1], sy = [-1, 1])
  [sx*(base_x[1] - 11), sy > 0 ? base_y[1] - 11 : base_y[0] + 11]];
feet = [for (sx = [-1, 1], sy = [-1, 1]) [sx*70, sy > 0 ? 58 : -62]];

module base_outline(d = 0) {
  offset(delta = d) rrect([base_x[0], base_y[0]], [base_x[1], base_y[1]], base_r);
}

// ramka serwa PAN (wisi pod płytą górną); serwo korpusem w +Y
module pan_servo_frame() {
  s = servo_pan;
  zt = base_h - top_t;
  z0 = z_ear_top(s);
  y_body0 = -sv_sh(s) - 0.4;
  y_body1 = sv_l(s) - sv_sh(s) + 0.4;
  hy = [for (h = sv_holes(s)) h[0]];
  y_near = min(hy); y_far = max(hy);
  xw = sv_w(s)/2 + 0.5;
  difference() {
    union() {
      translate([-xw - 2.5, y_near - 4.5, z0]) cube([2*xw + 5, y_body0 - y_near + 4.5, zt - z0 + 0.01]);
      translate([-xw - 2.5, y_body1, z0]) cube([2*xw + 5, y_far + 4.5 - y_body1, zt - z0 + 0.01]);
      for (sx = [-1, 1]) translate([sx > 0 ? xw : -xw - 2.5, y_near - 4.5, z0])
        cube([2.5, y_far - y_near + 9, zt - z0 + 0.01]);
    }
    for (h = sv_holes(s)) translate([h[1], h[0], z0 - 0.01]) cylinder(d = ins_d, h = ins_l);
    // przelot orczyka/piasty
    translate([0, 0, zt - 3]) cylinder(d = hub_d + 2, h = 10);
  }
}

module base_shell() {
  zt = base_h - top_t;
  difference() {
    union() {
      difference() {
        translate([0, 0, cover_t]) linear_extrude(base_h - cover_t) base_outline();
        translate([0, 0, cover_t - 1]) linear_extrude(zt - cover_t + 1) base_outline(-wall);
      }
      // słupki narożne z wkładkami
      for (p = corner_boss) translate([p[0], p[1], cover_t]) cylinder(d = 10, h = zt - cover_t + 0.01);
      // podkładki pod wkładki lazy susan
      for (p = ls_bot_holes()) translate([p[0], p[1], zt - 4]) cylinder(d = 9, h = 4.01);
      pan_servo_frame();
    }
    // otwór środkowy: piasta + orczyk
    translate([0, 0, zt - 5]) cylinder(d = hub_d + 2, h = 20);
    // wkładki M3 na pierścień dolny lazy susan (od góry)
    for (p = ls_bot_holes()) translate([p[0], p[1], base_h - ins_l]) cylinder(d = ins_d, h = ins_l + 1);
    // otwory serwisowe do śrub pierścienia górnego (jarzmo obrócone o 45°)
    for (p = ls_access_holes()) translate([p[0], p[1], zt - 5]) cylinder(d = 8, h = 20);
    // łuk przelotu przewodów do głowy (pan ±80° + zapas)
    translate([0, 0, zt - 1]) linear_extrude(top_t + 2)
      arc_slot2d(wire_r, wire_w, -90 + pan_rng[0] - 10, -90 + pan_rng[1] + 10);
    // wkładki pokrywy
    for (p = corner_boss) translate([p[0], p[1], cover_t - 0.01]) cylinder(d = ins_d, h = ins_l);
    // gniazdo USB-C zasilania (moduł PD) – tylna ścianka
    translate([pd_pos[0] - 6.5, base_y[0] - 1, cover_t - 1]) cube([13, wall + 2, 11]);
    // dostęp do USB ESP32 (programowanie) – lewa ścianka
    translate([base_x[0] - 1, esp_pos[1] - 10, cover_t - 1]) cube([wall + 2, 20, 12]);
    // szczeliny wentylacyjne przy przetwornicach (pionowe – bez mostków)
    for (y = [xl_pos[1] - 21 : 7 : xl_pos[1] + 21])
      translate([base_x[0] - 1, y - 1.5, 14]) cube([wall + 2, 3, 30]);
    for (y = [lm_pos[1] - 18 : 7 : lm_pos[1] + 18])
      translate([base_x[1] - wall - 1, y - 1.5, 14]) cube([wall + 2, 3, 30]);
  }
}

module cradle(pos, dim, h = 4) {   // ramka 1.6 mm wokół modułu (+0.6 luzu)
  translate([pos[0], pos[1], cover_t - 0.01]) linear_extrude(h)
    difference() { square([dim[0] + 4.4, dim[1] + 4.4], center = true); square([dim[0] + 1.2, dim[1] + 1.2], center = true); }
}

module cover() {
  difference() {
    union() {
      linear_extrude(cover_t) base_outline(-0.3);
      cradle(esp_pos, esp_dim);
      cradle(xl_pos, xl_dim);
      cradle(lm_pos, lm_dim);
      // PD trigger: ramka otwarta od strony gniazda USB-C (tył)
      difference() {
        cradle(pd_pos, pd_dim);
        translate([pd_pos[0] - 8, pd_pos[1] - pd_dim[1]/2 - 5, 0]) cube([16, 6, 20]);
      }
      // komora obciążnika
      translate([0, 0, cover_t - 0.01]) linear_extrude(30) difference() {
        translate(ballast[0]) square(ballast[1] - ballast[0]);
        translate(ballast[0] + [2, 2]) square(ballast[1] - ballast[0] - [4, 4]);
      }
    }
    for (p = corner_boss) translate([p[0], p[1], -1]) {
      cylinder(d = m3_clear, h = 10);
      translate([0, 0, 1]) cylinder(d1 = m3_csk_d, d2 = 0, h = m3_csk_d/2);
      cylinder(d = m3_csk_d, h = 1.01);
    }
    // wgłębienia na gumowe nóżki Ø12
    for (p = feet) translate([p[0], p[1], -1]) cylinder(d = 12.6, h = 1 + 1.2);
    // wentylacja pod przetwornicami i ESP32
    for (p = [[xl_pos, xl_dim], [lm_pos, lm_dim]])
      for (dy = [-p[1][1]/2 + 6 : 6 : p[1][1]/2 - 6])
        translate([p[0][0] - p[1][0]/2 + 4, p[0][1] + dy - 1.25, -1]) cube([p[1][0] - 8, 2.5, 10]);
    for (dx = [-esp_dim[0]/2 + 8 : 6 : esp_dim[0]/2 - 8])
      translate([esp_pos[0] + dx - 1.25, esp_pos[1] - 7, -1]) cube([2.5, 14, 10]);
  }
}

if (is_undef(ASM)) {
  if (part == "base") translate([0, 0, base_h]) rotate([180, 0, 0]) base_shell();
  else cover();
}
