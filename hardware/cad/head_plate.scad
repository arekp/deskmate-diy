// =====================================================================
//  DeskMate DIY – PŁYTA GŁOWY (kołyska telefonu + kieszeń ładowarki)
//  Jedna płyta na profil telefonu:  -D 'profile="fold8_open"'
//  Układ: oś TILT = oś X, telefon z przodu (+Y). Druk: tyłem do stołu.
// =====================================================================
include <params.scad>

// ---------- geometria zależna od profilu ----------
function hp_zmin() = min(pad_cz() - pad_rp() - 4, ph_bot() - hook_h);
function hp_zmax() = max(pad_cz() + pad_rp() + 6, ph_top() - 4);
function clamp_z0() = ph_top() + tol;                       // spód haka zaczepu
function clamp_screw_z() = clamp_z0() + clamp_h / 2;        // środek regulacji
function chan_z0() = ph_top() - clamp_travel - 2;
function chan_z1() = clamp_z0() + clamp_h + clamp_travel + 1;
function ear_top() = chan_z1() + 4;
function shelf_y1() = lip_y0() + lip_t;
function hp_ttp_z() = hp_zmax();                            // górna krawędź na X = 0

// ---------- obrys płyty (2D, x -> X, y -> Z) ----------
module hp_outline() {
  union() {
    rrect([-head_half_w, hp_zmin()], [head_half_w, hp_zmax()], 5);
    // uszy na zaczepy
    for (s = [-1, 1])
      translate([s*clamp_x, 0])
        rrect([-clamp_w/2 - 5, hp_zmax() - 10], [clamp_w/2 + 5, ear_top()], 3);
    // język z kanałem kabla ładowarki
    translate([0, pad_cz()]) rotate(pad_port_angle)
      translate([0, -(port_w()/2 + 7)])
        square([pad_rp() + chan_len, port_w() + 14]);
  }
}

// ---------- otwory wentylacyjne poza ładowarką ----------
module hp_vent_keepout() {
  translate([0, pad_cz()]) circle(r = pad_rp() + 5);
  translate([0, pad_cz()]) rotate(pad_port_angle)
    translate([0, -(port_w()/2 + 10)]) square([pad_rp() + chan_len + 5, port_w() + 20]);
  for (s = [-1, 1]) translate([s*clamp_x, 0])
    translate([-clamp_w/2 - 6, chan_z0() - 4]) square([clamp_w + 12, 1000]);
  for (s = [-1, 1]) translate([s > 0 ? br_fl_x0 - 3 : -1000, -br_fl_hz - 4])
    square([1000 - br_fl_x0 + 3, 2*br_fl_hz + 8]);
  translate([-1000, -1000]) square([2000, ph_bot() + 4 + 1000]);   // półka
  translate([-1000, hp_zmax() - 10]) square([2000, 1000]);          // TTP / góra
  difference() { square(2000, center = true); offset(delta = -5) hp_outline(); }
}
module hp_vents2d() {
  difference() {
    for (x = [-46 : 9 : 46]) translate([x - 2, -500]) square([4, 1000]);
    hp_vent_keepout();
  }
}

// ---------- półka pod dolną krawędzią telefonu ----------
module hp_shelf() {
  difference() {
    union() {
      translate([-head_half_w, slab_front - 0.01, ph_bot() - hook_h])
        cube([2*head_half_w, shelf_y1() - slab_front, hook_h]);
      translate([-head_half_w, lip_y0(), ph_bot() - 0.01])
        cube([2*head_half_w, lip_t, lip_h]);
    }
    // wycięcie środkowe (przyciski / palec)
    translate([-shelf_cut_w/2, slab_front - 1, ph_bot() - hook_h - 1])
      cube([shelf_cut_w, 50, hook_h + lip_h + 2]);
  }
}

// ---------- płyta ----------
module head_plate() {
  pc = pad_cz();
  difference() {
    union() {
      xz_extrude(slab_back(), slab_front) hp_outline();
      hp_shelf();
    }
    // kieszeń ładowarki (wierzch ładowarki = tył telefonu)
    translate([0, 0, pc]) cyl_y(2*pad_rp(), pocket_floor(), 60);
    // kanał na wtyk / kabel ładowarki
    translate([0, 0, pc]) rotate([0, -pad_port_angle, 0])
      translate([pad_rp() - 3, pocket_floor(), -port_w()/2])
        cube([chan_len + 3 + 1, 60, port_w()]);
    // otwory na opaskę zaciskową (odciążenie kabla)
    translate([0, 0, pc]) rotate([0, -pad_port_angle, 0])
      for (s = [-1, 1])
        translate([pad_rp() + chan_len - 8, slab_back() - 1, s*(port_w()/2 + 3.2) - 1.5])
          cube([4, 50, 3]);
    // dno: szczeliny powietrza między żebrami
    translate([0, 0, pc]) xz_extrude(slab_back() - 1, pocket_floor() + 0.01)
      for (r = [pad_rp()*0.45, pad_rp()*0.72])
        if (r > (fan_on ? fan_size/2 + 3 : 6))
          for (a = [0 : 45 : 359]) arc_slot2d(r, 5, a + 9, a + 36);
    // otwór i mocowanie wentylatora 30x30 (z tyłu płyty)
    if (fan_on) translate([0, 0, pc]) {
      cyl_y(fan_size - 2, slab_back() - 1, pocket_floor() + 0.01);
      for (sx = [-1, 1], sz = [-1, 1])
        translate([sx*fan_pitch/2, 0, sz*fan_pitch/2]) cyl_y(m3_clear, slab_back() - 1, pocket_floor() + 1);
    }
    // wentylacja / odchudzenie poza ładowarką
    xz_extrude(slab_back() - 1, slab_front + 1) hp_vents2d();
    // śruby wsporników (łeb stożkowy od przodu – pod ładowarką lub telefonem)
    for (s = [-1, 1], p = br_screw) translate([s*p[0], 0, p[1]]) {
      cyl_y(m3_clear, slab_back() - 1, slab_front + 1);
      translate([0, slab_back() + floor_t - m3_csk_d/2, 0])
        rotate([-90, 0, 0]) cylinder(d1 = 0, d2 = m3_csk_d, h = m3_csk_d/2 + 0.01);
      cyl_y(m3_csk_d + 0.2, slab_back() + floor_t, slab_front + 1);
    }
    // kanały zaczepów górnych + szczelina regulacji
    for (s = [-1, 1]) translate([s*clamp_x, 0, 0]) {
      translate([-clamp_w/2 - tol, slab_front - clamp_t - 0.2, chan_z0()])
        cube([clamp_w + 2*tol, 20, chan_z1() - chan_z0()]);
      hull() for (dz = [-clamp_travel, clamp_travel])
        translate([0, 0, clamp_screw_z() + dz]) cyl_y(m3_clear, slab_back() - 1, slab_front);
    }
    // kieszeń czujnika dotyku TTP223 w górnej krawędzi + wyjście przewodu w tył
    translate([-ttp[0]/2, (slab_back() + slab_front)/2 - ttp[1]/2, hp_ttp_z() - ttp[2]])
      cube([ttp[0], ttp[1], 10]);
    translate([-3, slab_back() - 1, hp_ttp_z() - ttp[2]])
      cube([6, (slab_front - slab_back())/2 + 1, 10]);
  }
  // żebra pod ładowarką
  translate([0, 0, pc]) intersection() {
    xz_extrude(pocket_floor() - 0.01, pocket_floor() + rib_h) union() {
      for (a = [0 : 45 : 359]) rotate(a)
        translate([fan_on ? fan_size/2 - 1 : 6, -1.2]) square([pad_rp(), 2.4]);
      difference() { circle(r = pad_rp() + 0.1); circle(r = pad_rp() - 2.5); }
    }
    cyl_y(2*pad_rp() + 0.1, pocket_floor() - 1, pocket_floor() + rib_h + 1);
  }
}

// ---------- zaczep górny (2 szt. na profil) ----------
module clamp_part() {   // w układzie głowy, dla zaczepu przy +X (symetryczny)
  y_back = slab_front - clamp_t;
  difference() {
    union() {
      translate([-clamp_w/2, y_back, clamp_z0()]) cube([clamp_w, clamp_t, clamp_h]);
      translate([-clamp_w/2, y_back, clamp_z0()]) cube([clamp_w, shelf_y1() - y_back, 4]);
      translate([-clamp_w/2, lip_y0(), clamp_z0() - lip_h]) cube([clamp_w, lip_t, lip_h + 4]);
    }
    translate([0, y_back - 1, clamp_screw_z()]) cyl_y(ins_d, 0, ins_l - 1 + 1);
  }
}

if (is_undef(ASM)) {
  // ułożenie do druku: tył płyty na stole
  translate([0, 0, -slab_back()]) rotate([90, 0, 0]) head_plate();
}
