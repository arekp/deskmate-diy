// =====================================================================
//  DeskMate DIY – JARZMO: szyja z "barkami", dwa ramiona z uszami,
//  nakładki uszu i piasta serwa PAN.
//  Szyja/barki obracają się na lazy susan (piasta na orczyku serwa PAN).
//  Ramię +X: do wewn. strony ucha przykręcona tarcza orczyka serwa TILT
//            (serwo siedzi w głowie).  Ramię -X: łożysko 608 osi TILT.
//  Układ: oś TILT (jak głowa); oś PAN w y = -tilt_y.
//  Druk: szyja płaskim spodem do stołu (po przekątnej stołu 220),
//        ramiona płaską wewnętrzną stroną do stołu, nakładki płaskim spodem.
// =====================================================================
include <params.scad>
part = "neck"; // [neck, arm_servo, arm_bearing, cap_servo, cap_bearing, hub]

pan_y = -tilt_y;
arm_xc = arm_in + arm_t/2;            // środek stopy ramienia
foot_z0 = z_deck - 1.5;               // spód stopy (w wybraniu barku)

// "krążek" z zaokrągloną górną krawędzią (płaski spód)
module puck(r, h, rr) {
  rotate_extrude($fn = 96) hull() {
    square([r - rr, h]);
    square([r, h - rr]);
    translate([r - rr, h - rr]) circle(r = rr);
  }
}

// ---------- szyja + barki ----------
module yoke_neck() {
  zb = z_deck - deck_t;
  difference() {
    union() {
      translate([0, pan_y, zb]) puck(neck_r(), deck_t, 5);
      hull() for (s = [-1, 1]) translate([s*arm_xc, 0, zb]) puck(bar_w/2, deck_t, 5);
      hull() for (s = [-1, 1]) translate([s*arm_xc*0.55, 0, zb]) puck(bar_w/2 + 6, deck_t, 5);
    }
    // wybrania na stopy ramion + śruby od spodu (stożkowe)
    for (s = [-1, 1]) mirror([s < 0 ? 1 : 0, 0, 0]) {
      x1 = s > 0 ? arm_r_out : arm_l_out;
      translate([arm_in - 0.3, -arm_foot_w/2 - 0.3, foot_z0]) cube([x1 - arm_in + 0.6, arm_foot_w + 0.6, 5]);
      for (dy = [-8, 8]) translate([(arm_in + x1)/2, dy, zb - 1]) {
        cylinder(d = m3_clear, h = deck_t + 2);
        cylinder(d1 = m3_csk_d + 2, d2 = 0, h = (m3_csk_d + 2)/2);
      }
    }
    // dostęp do śruby wałka PAN, przelot przewodów
    translate([0, pan_y, zb - 1]) cylinder(d = 8, h = deck_t + 2);
    translate([0, pan_y - wire_r(), zb - 1]) cylinder(d = wire_w, h = deck_t + 2);
    // wkładki lazy susan (od spodu), śruby piasty (stożkowe od góry)
    for (p = ls_top_holes()) translate([p[0], pan_y + p[1], zb - 0.01]) cylinder(d = ins_d, h = ins_l);
    for (a = [45, 165, 285]) translate([14*cos(a), pan_y + 14*sin(a), zb - 1]) {
      cylinder(d = m3_clear, h = deck_t + 2);
      translate([0, 0, deck_t + 1 - m3_csk_d/2]) cylinder(d1 = 0, d2 = m3_csk_d, h = m3_csk_d/2 + 0.01);
      translate([0, 0, deck_t + 1]) cylinder(d = m3_csk_d, h = 5);
    }
    // otwory na opaski (przewody z ramienia do szyi)
    for (x = [-arm_xc + 14, arm_xc - 14]) translate([x - 1.5, -bar_w/2 + 6, zb - 1]) cube([3, 5, deck_t + 2]);
  }
}

// ---------- ramię z uchem (profil w płaszczyźnie YZ) ----------
module arm_profile2d() {
  hull() {
    circle(r = ear_r, $fn = 96);
    translate([-arm_foot_w/2, foot_z0]) square([arm_foot_w, 10]);
  }
}
// ramię między |x| = x0 (płasko, wewnątrz) a x1 (zaokrąglone, na zewnątrz)
module arm_body(x0, x1, r = 3.5) {
  hull() {
    yz_extrude(x0, x1 - r) arm_profile2d();
    for (a = [30 : 30 : 90])
      yz_extrude(x1 - r + r*sin(a) - 0.01, x1 - r + r*sin(a)) offset(delta = -r*(1 - cos(a))) arm_profile2d();
  }
}
module arm(kind) {   // zawsze budowane po stronie +X; strona łożyska jest lustrem
  x1 = kind == "servo" ? arm_r_out : arm_l_out;
  difference() {
    arm_body(arm_in, x1);
    // stopa: wkładki od spodu
    for (dy = [-8, 8]) translate([(arm_in + x1)/2, dy, foot_z0 - 0.01])
      cylinder(d = ins_d, h = ins_l);
    // nakładka ucha: 2 wkładki
    for (dz = [-20, 20]) translate([x1 - ins_l, 0, dz]) rotate([0, 90, 0]) cylinder(d = ins_d, h = ins_l + 1);
    if (kind == "servo") {
      cyl_x(9, arm_in - 1, x1 + 1);                              // śruba wałka
      for (a = [0 : 90 : 270]) rotate([a, 0, 0]) translate([0, horn_hole_r[1], 0]) cyl_x(1.8, arm_in - 1, arm_in + 6);
      for (a = [45 : 90 : 315]) rotate([a, 0, 0]) translate([0, horn_hole_r[0], 0]) cyl_x(1.6, arm_in - 1, arm_in + 6);
    } else {
      cyl_x(16, arm_in - 1, arm_in + bearing_lip + 0.5);         // przelot tulei
      cyl_x(b608_od + b608_fit, arm_in + bearing_lip, x1 + 1);   // gniazdo 608 od zewnątrz
    }
    // otwory na opaski wzdłuż ramienia
    for (z = [-40, -70]) translate([arm_in - 1, -arm_foot_w/2 - 1, z - 1.5]) cube([x1 - arm_in + 2, 6, 3]);
  }
}

// ---------- nakładka ucha (czasza) ----------
module ear_cap(kind) {
  h = kind == "servo" ? 8 : 13;
  cav = kind == "servo" ? [14, 4] : [24, 8.5];
  R = (ear_cap_r*ear_cap_r + h*h) / (2*h);
  difference() {
    intersection() {
      translate([0, 0, h - R]) sphere(r = R, $fn = 96);
      cylinder(r = ear_cap_r, h = h + 1, $fn = 96);
    }
    translate([0, 0, -1]) cylinder(d = cav[0], h = cav[1] + 1);
    for (dz = [-20, 20]) translate([dz, 0, -1]) {
      cylinder(d = m3_clear, h = 20);
      translate([0, 0, 2.5]) cylinder(d = 6.4, h = 20);
    }
  }
}

// ---------- piasta PAN (układ globalny) ----------
module pan_hub() {
  zb = z_horn_face() - 1.5;
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

// złożone jarzmo w układzie osi TILT (do podglądu)
module yoke_asm() {
  yoke_neck();
  arm("servo");
  mirror([1, 0, 0]) arm("bearing");
  translate([arm_r_out, 0, 0]) rotate([0, 90, 0]) rotate([0, 0, 90]) ear_cap("servo");
  translate([-arm_l_out, 0, 0]) rotate([0, -90, 0]) rotate([0, 0, 90]) ear_cap("bearing");
}

if (is_undef(ASM)) {
  if (part == "neck") rotate([0, 0, 45]) translate([0, 0, -(z_deck - deck_t)]) yoke_neck();
  else if (part == "arm_servo") arm_print("servo");
  else if (part == "arm_bearing") arm_print("bearing");
  else if (part == "cap_servo") ear_cap("servo");
  else if (part == "cap_bearing") ear_cap("bearing");
  else translate([0, 0, z_ls_top]) rotate([180, 0, 0]) pan_hub();
}
// ramię płaską stroną wewnętrzną (x = arm_in) na stole
module arm_print(kind) { translate([0, 0, -arm_in]) rotate([0, -90, 0]) arm(kind); }
