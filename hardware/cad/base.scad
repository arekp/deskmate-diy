// =====================================================================
//  DeskMate DIY – TUŁÓW ("jajo"), POKRYWA DOLNA i KOŁNIERZ SZYI
//  Tułów: obła skorupa (elipsoida ścięta płasko u dołu i u góry,
//  boki parabola – nawis ≤ 45° przy druku do góry dnem), płyta górna
//  z gniazdem lazy susan, ramka serwa PAN, łuk przelotu przewodów,
//  kratki wentylacyjne, "guziki", gniazda USB-C z tyłu.
//  Pokrywa: ramki modułów, komora obciążnika, wgłębienia na nóżki.
//  Kołnierz: pierścień zasłaniający lazy susan (część "szyi").
//  -D 'part="base"' | "cover" | "collar". Układ globalny.
// =====================================================================
include <params.scad>
part = "base"; // [base, cover, collar]

lug_ang = [45, 135, 225, 315];
function lug_pos(a) = [(body_ax(cover_t) - wall - 7) * cos(a), (body_ax(cover_t) - wall - 7) * body_ky() * sin(a)];
collar_tab_ang = [40, 90, 140];
collar_screw_r = (collar_rin() + 46) / 2;

// profil boku (r, z) – zewnętrzny i wewnętrzny
function body_pts(ofs, ztop) = concat(
  [[0, cover_t - (ofs > 0 ? 1 : 0)]],
  [for (z = [cover_t - (ofs > 0 ? 1 : 0) : 2 : ztop - 4]) [body_ax(z) - ofs*sqrt(1 + pow(2*body_da*z/(base_h*base_h), 2)), z]],
  [for (a = [0 : 15 : 90]) let(z = ztop - 4 + 4*sin(a))
     [body_ax(ztop - 4) - ofs*1.4 - 4*(1 - cos(a)), z]],
  [[0, ztop]]);

module body_solid() {
  scale([1, body_ky(), 1]) rotate_extrude($fn = 128) polygon(body_pts(0, base_h));
}
module body_cavity() {
  scale([1, body_ky(), 1]) rotate_extrude($fn = 128) polygon(body_pts(wall, base_h - top_t));
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
    translate([0, 0, zt - 3]) cylinder(d = hub_d + 2, h = 10);
  }
}

module base_shell() {
  zt = base_h - top_t;
  difference() {
    union() {
      difference() { body_solid(); body_cavity(); }
      intersection() {
        body_solid();
        union() {
          // ucha pokrywy z podparciem 45°
          for (a = lug_ang) let(p = lug_pos(a)) hull() {
            translate([p[0], p[1], cover_t]) cylinder(d = 10, h = 10);
            translate([p[0]*1.12, p[1]*1.12, cover_t]) cylinder(d = 10, h = 22);
          }
          // podkładki pod wkładki (lazy susan, kołnierz)
          for (p = ls_bot_holes()) translate([p[0], p[1], zt - 4]) cylinder(d = 9, h = 4.01);
          for (a = collar_tab_ang) translate(collar_screw_r*[cos(a), sin(a), 0] + [0, 0, zt - 4]) cylinder(d = 9, h = 4.01);
          pan_servo_frame();
        }
      }
    }
    // otwór środkowy: piasta + orczyk
    translate([0, 0, zt - 5]) cylinder(d = hub_d + 2, h = 20);
    // wkładki M3: lazy susan (od góry), kołnierz szyi (od góry)
    for (p = ls_bot_holes()) translate([p[0], p[1], base_h - ins_l]) cylinder(d = ins_d, h = ins_l + 1);
    for (a = collar_tab_ang) translate(collar_screw_r*[cos(a), sin(a), 0] + [0, 0, base_h - ins_l]) cylinder(d = ins_d, h = ins_l + 1);
    // otwory serwisowe do śrub pierścienia górnego lazy susan
    for (p = ls_access_holes()) translate([p[0], p[1], zt - 5]) cylinder(d = 8, h = 20);
    // łuk przelotu przewodów do głowy (pan ±80° + zapas)
    translate([0, 0, zt - 1]) linear_extrude(top_t + 2)
      arc_slot2d(wire_r(), wire_w, -90 + pan_rng[0] - 10, -90 + pan_rng[1] + 10);
    // wkładki pokrywy
    for (a = lug_ang) let(p = lug_pos(a)) translate([p[0], p[1], cover_t - 0.01]) cylinder(d = ins_d, h = ins_l);
    // gniazdo USB-C zasilania (PD trigger) i dostęp do USB ESP32 – z tyłu
    translate([pd_pos[0] - 6.5, -body_b - 5, cover_t - 1]) cube([13, 40, 11]);
    translate([esp_pos[0] - 8, -body_b - 5, cover_t - 1]) cube([16, 40, 14]);
    // ozdobne kratki wentylacyjne po bokach (przy przetwornicach)
    scale([1, body_ky(), 1]) for (side = [0, 180]) for (da = [-24 : 8 : 24])
      rotate([0, 0, side + da]) hull() for (z = [16, 40]) translate([body_a - 12, 0, z]) rotate([0, 90, 0]) cylinder(d = 3.6, h = 20, $fn = 16);
    // "guziki" na brzuchu – wgłębione pierścienie
    for (dx = [-18, 0, 18]) let(z = 26, r = body_ax(z) * body_ky(), y = r * sqrt(1 - pow(dx / body_ax(z), 2)))
      translate([dx, y, z]) rotate([-90 + atan(2*body_da*z/(base_h*base_h)), 0, 0]) translate([0, 0, -1.4])
        difference() { cylinder(d = 11, h = 5); translate([0, 0, -1]) cylinder(d = 7.5, h = 7); }
  }
}

module cradle(pos, dim, h = 4) {
  translate([pos[0], pos[1], cover_t - 0.01]) linear_extrude(h)
    difference() { square([dim[0] + 4.4, dim[1] + 4.4], center = true); square([dim[0] + 1.2, dim[1] + 1.2], center = true); }
}

module cover() {
  r0 = body_ax(cover_t) - 0.3;
  difference() {
    union() {
      hull() {
        translate([0, 0, 1]) scale([1, body_ky(), 1]) cylinder(r = r0, h = cover_t - 1, $fn = 128);
        scale([1, body_ky(), 1]) cylinder(r = r0 - 1, h = 1, $fn = 128);
      }
      // wieniec centrujący wewnątrz skorupy
      translate([0, 0, cover_t - 0.01]) scale([1, body_ky(), 1]) difference() {
        cylinder(r = body_ax(cover_t) - wall - 0.4, h = 3, $fn = 128);
        cylinder(r = body_ax(cover_t) - wall - 2.4, h = 7, center = true, $fn = 128);
      }
      cradle(esp_pos, esp_dim);
      cradle(xl_pos, xl_dim);
      cradle(lm_pos, lm_dim);
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
    // wieniec: przerwy na ucha pokrywy i wycięcia USB
    for (a = lug_ang) let(p = lug_pos(a)) translate([p[0], p[1], cover_t - 1]) cylinder(d = 14, h = 10);
    translate([pd_pos[0] - 8, -body_b - 5, cover_t]) cube([16, 30, 10]);
    translate([esp_pos[0] - 9, -body_b - 5, cover_t]) cube([18, 30, 10]);
    for (a = lug_ang) let(p = lug_pos(a)) translate([p[0], p[1], -1]) {
      cylinder(d = m3_clear, h = 10);
      translate([0, 0, 1]) cylinder(d1 = m3_csk_d, d2 = 0, h = m3_csk_d/2);
      cylinder(d = m3_csk_d, h = 1.01);
    }
    for (p = feet) translate([p[0], p[1], -1]) cylinder(d = 12.6, h = 1 + 1.2);
    for (p = [[xl_pos, xl_dim], [lm_pos, lm_dim]])
      for (dy = [-p[1][1]/2 + 6 : 6 : p[1][1]/2 - 6])
        translate([p[0][0] - p[1][0]/2 + 4, p[0][1] + dy - 1.25, -1]) cube([p[1][0] - 8, 2.5, 10]);
  }
}

// kołnierz szyi (nieruchomy, zasłania lazy susan)
module collar() {
  h = ls_t - 1;
  difference() {
    union() {
      translate([0, 0, base_h]) rotate_extrude($fn = 128) hull() {
        translate([collar_rin(), 0]) square([neck_r() - collar_rin(), h - 2.5]);
        translate([collar_rin(), 0]) square([0.01, h]);
        translate([neck_r() - 2.5, h - 2.5]) circle(r = 2.5);
      }
      for (a = collar_tab_ang) rotate([0, 0, a]) translate([44, -7, base_h]) cube([collar_rin() - 43, 14, 3]);
    }
    for (a = collar_tab_ang) translate(collar_screw_r*[cos(a), sin(a), 0] + [0, 0, base_h - 1]) {
      cylinder(d = m3_clear, h = 10);
      translate([0, 0, 1 + 3 - m3_csk_d/2]) cylinder(d1 = 0, d2 = m3_csk_d, h = m3_csk_d/2 + 0.01);
    }
  }
}

if (is_undef(ASM)) {
  if (part == "base") translate([0, 0, base_h]) rotate([180, 0, 0]) base_shell();
  else if (part == "cover") cover();
  else translate([0, 0, -base_h]) collar();
}
