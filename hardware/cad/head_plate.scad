// =====================================================================
//  DeskMate DIY – KASK, część przednia (ramka telefonu, krótka półka,
//  kieszeń ładowarki) i TARCZA Z MAGNESAMI (przed ładowarką).
//  Telefon trzymają magnesy (pierścień wokół cewki) + półka z wargą u dołu.
//  Jeden komplet na profil telefonu:  -D 'profile="fold8_open"'
//  Układ: oś TILT = oś X, telefon z przodu (+Y).
//  Druk przodu kasku: tyłem (płaską płytą) do stołu, bez podpór.
// =====================================================================
include <params.scad>

// ---------- obrys kasku (widok z przodu, x -> X, y -> Z) ----------
function hd_zb() = min(ph_bot() - hook_h - bottom_bar, pad_cz() - pad_rp() - 6);
function hd_zt() = max(ph_top() + forehead, pad_cz() + pad_rp() + 6);
function hd_r()  = min(head_r, (hd_zt() - hd_zb())/2 - 1);
module hd_outline(d = 0) {
  offset(r = d) rrect([-head_w, hd_zb()], [head_w, hd_zt()], hd_r());
}
function cav_x() = ph_L()/2 + fit_x;                     // wnęka telefonu ±X
function cav_top() = ph_top() + lift;                    // zapas na wkładanie

// bryła z zaokrągloną przednią krawędzią (wycinki połączone hull)
module rounded_front(y0, y1, r, d0 = 0) {
  hull() {
    xz_extrude(y0, y1 - r) hd_outline(d0);
    for (a = [15 : 15 : 90])
      xz_extrude(y1 - r + r*sin(a) - 0.01, y1 - r + r*sin(a)) hd_outline(d0 - r*(1 - cos(a)));
  }
}

module head_front() {
  pc = pad_cz();
  yf = y_front();
  difference() {
    union() {
      difference() {
        union() {
          rounded_front(slab_back() + skirt, yf, front_r);
          // stopień pod zakładkę tylnej skorupy
          xz_extrude(slab_back(), slab_back() + skirt + 0.01) hd_outline(-shell_t - 0.15);
        }
        // ---- wnęka telefonu (z zapasem na wkładanie nad półką) i okno ekranu
        translate([-cav_x(), slab_front, ph_bot()]) cube([2*cav_x(), lip_y0() - slab_front, cav_top() - ph_bot()]);
        translate([-cav_x(), lip_y0() - 0.01, ph_bot() + lip_h]) cube([2*cav_x(), 30, cav_top() - ph_bot() - lip_h]);
        // zaokrąglenie wargi półki (przód i strona ekranu)
        lip_rounding();
      }
      // pierścień wokół tarczy magnesów – podparcie tyłu telefonu
      translate([0, 0, pc]) cyl_y(2*disc_r() + 7, slab_front - 0.01, D_back);
    }
    // ---- odchudzenie: komory od tyłu (zamknięte od przodu skórą 2.5–3 mm)
    hd_lighten();
    // wnęka w półce (przyciski boczne) i wycięcie wargi (palec / przewód)
    translate([-shelf_cut_w/2, slab_front, ph_bot() - 2.5]) cube([shelf_cut_w, lip_y0() - slab_front, 3]);
    translate([-20, lip_y0() - 0.01, ph_bot() - 0.01]) cube([40, 30, lip_h + 0.02]);
    // okna na gniazdo USB-C / głośnik po bokach (otwarte do przodu)
    for (s = [-1, 1]) translate([s > 0 ? cav_x() - 1 : -head_w - 1, D_back - 2, ph_cz() - 7])
      cube([head_w - cav_x() + 2, 30, 14]);
    // ---- kieszeń ładowarki + kołnierz tarczy; otwór na tarczę z magnesami
    translate([0, 0, pc]) cyl_y(2*pad_rp(), pocket_floor(), pad_top_y() + disc_fl + 0.05);
    translate([0, 0, pc]) cyl_y(2*disc_boss_r() + 0.4, pad_top_y(), D_back + 1);
    // kanał wtyku/kabla + przelot do tyłu + szczeliny opaski (odciążenie)
    translate([0, 0, pc]) rotate([0, -pad_port_angle, 0]) {
      translate([pad_rp() - 3, pocket_floor(), -port_w()/2]) cube([chan_len + 3, pad_top_y() + 0.3 - pocket_floor(), port_w()]);
      translate([pad_rp() + chan_len - 12, slab_back() - 1, -port_w()/2]) cube([12, 30, port_w()]);
      for (s = [-1, 1]) translate([pad_rp() + chan_len - 22, slab_back() - 1, s*(port_w()/2 + 3) - 1.5]) cube([4, 30, 3]);
    }
    // dno: szczeliny powietrza między żebrami
    translate([0, 0, pc]) xz_extrude(slab_back() - 1, pocket_floor() + 0.01)
      for (r = [pad_rp()*0.45, pad_rp()*0.72])
        if (r > (fan_on ? fan_size/2 + 3 : 6))
          for (a = [0 : 45 : 359]) arc_slot2d(r, 5, a + 9, a + 36);
    // wentylator 30x30 (z tyłu płyty)
    if (fan_on) translate([0, 0, pc]) {
      cyl_y(fan_size - 2, slab_back() - 1, pocket_floor() + 0.01);
      for (sx = [-1, 1], sz = [-1, 1])
        translate([sx*fan_pitch/2, 0, sz*fan_pitch/2]) cyl_y(m3_clear, slab_back() - 1, pocket_floor() + 1);
    }
    // wkładki: łapy uchwytów serwa / łożyska (od tyłu)
    for (s = [-1, 1], tz = tab_z) translate([s*tab_x, 0, (tz[0] + tz[1])/2])
      cyl_y(ins_d, slab_back() - 1, slab_back() + ins_l);
    // wkładki: śruby tylnej skorupy (góra i dół, w stopniu)
    for (s = [-1, 1]) {
      translate([s*dome_screw_x, slab_back() + skirt/2, hd_zt() - shell_t - ins_l]) cylinder(d = ins_d, h = ins_l + 1);
      translate([s*dome_screw_x, slab_back() + skirt/2, hd_zb() + shell_t - 1]) cylinder(d = ins_d, h = ins_l + 1);
    }
    // czujnik dotyku TTP223 na czubku + przewód do tyłu
    translate([ttp_x - ttp[0]/2, slab_back() + skirt + 2, hd_zt() - ttp[2]]) cube([ttp[0], ttp[1], 10]);
    translate([ttp_x - 2, slab_back() - 1, hd_zt() - ttp[2] - 6]) cube([4, skirt + 4 + ttp[1]/2, 6.01]);
    // antenka: 2 wkładki + przelot przewodów
    for (s = [-1, 1]) translate([s*8, ant_y(), hd_zt() - ins_l]) cylinder(d = ins_d, h = ins_l + 1);
    translate([0, ant_y(), hd_zt() - 12]) cylinder(d = 4, h = 13);
    translate([-2, slab_back() - 1, hd_zt() - 12]) cube([4, ant_y() - slab_back() + 1, 4]);
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
// strefy, które muszą zostać pełne (widok z przodu)
module hd_keep2d() {
  translate([0, pad_cz()]) circle(r = disc_r() + 7);
  translate([0, pad_cz()]) rotate(pad_port_angle) translate([0, -port_w()/2 - 9]) square([pad_rp() + chan_len + 4, port_w() + 18]);
  for (s = [-1, 1], tz = tab_z) translate([s*tab_x, (tz[0] + tz[1])/2]) circle(r = 7);
  for (s = [-1, 1]) translate([s*dome_screw_x - 6, hd_zt() - 14]) square([12, 20]);
  for (s = [-1, 1]) translate([s*dome_screw_x - 6, hd_zb() - 6]) square([12, 20]);
  translate([-14, hd_zt() - 18]) square([28, 30]);                 // antenka
  translate([ttp_x - 12, hd_zt() - 14]) square([24, 20]);          // TTP223
  for (s = [-1, 1]) translate([s > 0 ? cav_x() - 4 : -head_w, ph_cz() - 11]) square([head_w - cav_x() + 4, 22]);
}
module hd_lighten() {
  // za wnęką telefonu
  xz_extrude(slab_back() - 1, slab_front - 2.5) difference() { offset(r = -5) hd_outline(); hd_keep2d(); }
  // w ramce (boki, czoło, dół) – poza telefonem
  xz_extrude(slab_front - 3, y_front() - 3.5) difference() {
    offset(r = -5) hd_outline();
    hd_keep2d();
    translate([-cav_x() - 3, ph_bot() - 3]) square([2*cav_x() + 6, cav_top() - ph_bot() + 6]);
  }
}
function ant_y() = (slab_back() + skirt + slab_front) / 2;

// zaokrąglenie krawędzi wargi półki (usuwa naroże wokół osi X)
module round_edge_x(yc, zc, r, sy, sz, len) {   // sy, sz = kierunek naroża (+1/-1)
  translate([-len/2, yc, zc]) difference() {
    translate([0, sy > 0 ? 0 : -r - 1, sz > 0 ? 0 : -r - 1]) cube([len, r + 1, r + 1]);
    rotate([0, 90, 0]) cylinder(r = r, h = len, $fn = 32);
  }
}
module lip_rounding() {
  zt = ph_bot() + lip_h;
  round_edge_x(y_front() - lip_round, zt - lip_round, lip_round, 1, 1, 2*cav_x());
  round_edge_x(lip_y0() + 1, zt - 1, 1, -1, 1, 2*cav_x());
}

// ---------- tarcza z magnesami (przed ładowarką, Ø = kieszeń) ----------
// Kołnierz za wargą kieszeni trzyma tarczę; przód tarczy = płaszczyzna tyłu
// telefonu. Magnesy wciskane/klejone od strony ładowarki, skórka 0.6 mm.
function mag_pos() = [for (i = [0 : mag_n - 1]) let(a = 360/mag_n*i + 90) mag_ring_d/2*[cos(a), sin(a)]];
module magnet_disc() {   // w układzie głowy
  pc = pad_cz();
  difference() {
    translate([0, 0, pc]) union() {
      cyl_y(2*disc_r(), pad_top_y(), pad_top_y() + disc_fl);
      cyl_y(2*disc_boss_r(), pad_top_y() + disc_fl - 0.01, D_back);
    }
    for (p = mag_pos()) translate([p[0], 0, pc + p[1]]) cyl_y(mag_d + mag_fit, pad_top_y() - 1, pad_top_y() + mag_t + mag_depth_clear);
    // znacznik orientacji (kreska na kołnierzu, u góry)
    translate([-0.6, pad_top_y() - 0.01, pc + disc_r() - 3]) cube([1.2, 0.5, 3]);
  }
}

if (is_undef(ASM)) {
  translate([0, 0, -slab_back()]) rotate([90, 0, 0]) head_front();
}
