// =====================================================================
//  DeskMate DIY – wspólne parametry mechaniki (OpenSCAD 2021.01+)
//  Wszystkie wymiary w mm, masy w g.
//
//  Układy współrzędnych:
//   * GLOBALNY (podstawa): początek na osi PAN, na stole (Z=0).
//     X – w prawo (patrząc z tyłu robota), Y – do przodu (tam patrzy ekran),
//     Z – w górę.
//   * GŁOWY / JARZMA: początek na osi TILT. Ten sam kierunek osi co
//     globalny przy pan=0, tilt=0. Oś TILT = oś X.
//
//  Parametry oznaczone "ZMIERZ" to wartości katalogowe/szacunkowe –
//  sprawdź suwmiarką przed drukiem (lista w README.md).
// =====================================================================

/* [Wybór wariantu] */
// Profil telefonu (każdy daje własną płytę głowy i zaczepy)
profile = "s23"; // [s23, fold8_open, fold8_closed, flip6_open]
// Ładowarka bezprzewodowa w płycie głowy
pad = "anker313"; // [anker313, samsung_ep_p2900, esr_halolock_mini, anker_zolo_a25m2]
// Serwo PAN (w podstawie)
servo_pan = "MG996R"; // [MG996R, DS3218, MG90S]
// Serwo TILT (w jarzmie)
servo_tilt = "MG996R"; // [MG996R, DS3218]
// Łożysko obrotowe "lazy susan"
ls_shape = "square"; // [square, round]
// Gniazda na wentylator 30x30 za ładowarką
fan_on = true;

/* [Druk] */
// Luz pasowań na stronę (0.2–0.3 dla PETG)
tol = 0.25;
$fn = 48;

/* [Telefon w etui] */
// Grubość etui na boki (dodawana do długości i szerokości)
case_side = 2.0;
// Dodatek etui do grubości (tył + ranty z przodu)
case_thick = 3.0;
case_mass = 40;
// Dodatkowe przesunięcie środka telefonu względem osi TILT w Z (+ = telefon wyżej)
axis_dz = 0;

// --- tabela telefonów (bez etui; orientacja pozioma: L wzdłuż X, W wzdłuż Z)
//  0 nazwa, 1 L, 2 W, 3 T, 4 masa,
//  5 coil_dx, 6 coil_dz – położenie cewki względem środka telefonu w etui
//    (widok od ekranu, X w prawo, Z w górę) – ZMIERZ / sprawdź aplikacją
//  7 cg_dz – środek telefonu względem osi TILT (0 = oś na środku telefonu)
PHONES = [
  ["s23",          146.3,  70.9, 7.6, 168,  0.0,  0.0, 0],
  // Fold8 Ultra rozłożony poziomo: zawias poziomo, połówka z ekranem
  // zewnętrznym (i cewką) NA GÓRZE -> cewka ~35.8 mm nad środkiem
  ["fold8_open",   158.4, 143.2, 4.1, 215,  0.0, 35.8, 0],
  ["fold8_closed", 158.4,  72.8, 8.9, 215,  0.0,  0.0, 0],
  // Flip6 rozłożony poziomo, dolna połowa (z cewką) po PRAWEJ
  ["flip6_open",   165.1,  71.9, 6.9, 187, 41.3,  0.0, 0],
];

// --- ładowarki: 0 nazwa, 1 średnica, 2 grubość, 3 masa, 4 luz na średnicy,
//     5 magnetyczna?, 6 szerokość wyjścia przewodu/wtyku
PADS = [
  // Anker 313 Wireless Charger (Pad) A2503: Ø100 x 11.2 mm, 67 g (dane producenta),
  // gniazdo micro-USB z boku – kąt położenia ZMIERZ (pad_port_angle)
  ["anker313",          100.0, 11.2, 67, 0.6, false, 12],
  // Samsung EP-P2900 (Qi2 25 W): grubość 4.4 wg Samsunga, średnica ZMIERZ
  ["samsung_ep_p2900",   60.0,  4.4, 60, 1.0, true,  10],
  // ESR HaloLock Qi2 Mini: wymiary ZMIERZ
  ["esr_halolock_mini",  58.0,  8.0, 50, 1.0, true,  10],
  // Anker Zolo A25M2: Ø60 x 10.5, 68 g, kabel stały z boku
  ["anker_zolo_a25m2",   60.0, 10.5, 68, 1.0, true,  10],
];
// Kierunek wyjścia wtyku/kabla z boku ładowarki (stopnie w płaszczyźnie XZ,
// 0 = w prawo, 90 = w górę, 270 = w dół w stronę osi TILT) – ZMIERZ
pad_port_angle = 270;

// --- serwa: 0 nazwa, 1 dł. korpusu, 2 szer., 3 wys. od dna do spodu uszu,
//  4 grubość uszu, 5 wys. korpusu nad uszami, 6 wypust wałka nad korpusem,
//  7 dł. z uszami, 8 rozstaw otworów w uszach (wzdłuż), 9 Ø otworów,
//  10 rozstaw otworów w poprzek (0 = jeden otwór), 11 odl. osi wałka od
//  bliższego końca korpusu, 12 odl. od górnej pow. uszu do zewn. pow.
//  tarczy orczyka, 13 Ø tarczy orczyka, 14 grubość tarczy, 15 masa
//  WSZYSTKO ZMIERZ na swoim egzemplarzu (różnice między klonami do ~1 mm)
SERVOS = [
  ["MG996R", 40.7, 19.7, 27.5, 2.5, 9.5, 3.4, 54.0, 49.5, 4.2, 10.0, 10.2, 13.9, 24.5, 2.5, 55],
  ["DS3218", 40.0, 20.0, 26.2, 2.5, 8.5, 3.3, 54.5, 49.5, 4.2, 10.0, 10.0, 12.3, 24.0, 2.5, 60],
  ["MG90S",  22.8, 12.2, 16.5, 2.5, 6.0, 3.5, 32.5, 27.8, 2.2,  0.0,  6.0, 10.5, 20.0, 2.0, 13.4],
];
// Promienie otworów na wkręty orczyka (pilotowe Ø1.8) – ZMIERZ
horn_hole_r = [7.0, 9.5];

/* [Elementy złączne] */
ins_d = 4.2;      // otwór pod wkładkę gwintowaną M3 (M3x5.7, OD ~4.6–5)
ins_l = 6.0;      // głębokość otworu pod wkładkę
m3_clear = 3.4;
m3_head_d = 6.2;
m3_csk_d = 6.4;   // łeb stożkowy M3
m8_clear = 8.4;
m8_nut_af = 13.0; // klucz nakrętki M8
m8_nut_t = 6.5;
// łożysko 608: 22 x 8 x 7
b608_od = 22.0; b608_id = 8.0; b608_w = 7.0;
b608_fit = 0.1;   // wcisk/luz na średnicy gniazda łożyska

/* [Głowa] */
D_back = 27;      // odległość osi TILT od tylnej ścianki telefonu (Y)
gap = 2.5;        // szczelina wentylacyjna między telefonem a płytą
pad_proud = 0.0;  // wierzch ładowarki względem tyłu telefonu (0 = styk)
rib_h = 2.5;      // wysokość żeber pod ładowarką (kanał powietrza)
floor_t = 3.0;    // dno kieszeni
head_half_w = 56; // połowa szerokości płyty (X)
hook_h = 6;       // grubość półki pod telefonem
lip_h = 3.5;      // wysokość wargi zachodzącej na ekran
lip_t = 2.5;      // grubość wargi
clamp_x = 46;     // położenie zaczepów górnych (±X)
clamp_w = 14;
chan_len = 18;    // długość kanału na wtyk/kabel od krawędzi ładowarki
br_x = 59.5;      // zewn. powierzchnia wspornika głowy (styk z orczykiem)
br_t = 12.5;      // grubość wspornika (X)
br_screw_x = 52;  // X śrub wsporników
br_screw_z = [-32, -16, 16, 32]; // kandydaci Z śrub wsporników
hub_r = 15;       // promień piasty wspornika wokół osi
ttp = [15.6, 11.6, 3.0]; // kieszeń TTP223 (X, Y, Z)
fan_size = 30; fan_pitch = 24; fan_hole = 27;

/* [Jarzmo i podstawa] */
tilt_y = -10;          // położenie osi TILT względem osi PAN (Y)
axis_above_deck = 96;  // oś TILT nad górną powierzchnią pokładu jarzma
post_y = [-64, -28];   // zakres Y słupków jarzma (układ osi TILT)
bearing_lip = 7;       // kołnierz za łożyskiem 608
base_h = 46;           // wysokość podstawy (góra płyty górnej)
top_t = 4;             // grubość płyty górnej podstawy
wall = 2.4;            // ścianki podstawy
cover_t = 3;           // pokrywa dolna
base_x = [-92, 92];
base_y = [-82, 78];
base_r = 16;
deck_t = 6;
hub_gap = 1.5;         // szczelina piasta PAN – płyta podstawy
wire_r = 62;           // promień przelotu przewodów (łuk w podstawie)

/* [Łożysko lazy susan] – ZMIERZ */
ls_t = 8;              // wysokość całkowita
ls_id = 40;            // otwór środkowy (musi przepuścić piastę Ø28)
ls_sq = 75;            // bok wersji kwadratowej
ls_sq_pitch = 63;      // rozstaw otworów (kwadrat)
ls_round_od = 80;      // średnica wersji okrągłej
ls_round_bot_d = 72;   // okrąg otworów pierścienia dolnego
ls_round_top_d = 56;   // okrąg otworów pierścienia górnego
ls_hole_d = 4.5;

// =====================================================================
//  Funkcje pomocnicze
// =====================================================================
eps = 0.01;
function _row(tab, key) = tab[search([key], tab)[0]];

// telefon (w etui)
function ph(n=profile)    = _row(PHONES, n);
function ph_L(n=profile)  = ph(n)[1] + 2*case_side;
function ph_H(n=profile)  = ph(n)[2] + 2*case_side;
function ph_T(n=profile)  = ph(n)[3] + case_thick;
function ph_m(n=profile)  = ph(n)[4] + case_mass;
function ph_cx(n=profile) = -ph(n)[5];               // środek telefonu X (ładowarka na X=0)
function ph_cz(n=profile) = ph(n)[7] + axis_dz;      // środek telefonu Z
function ph_bot(n=profile) = ph_cz(n) - ph_H(n)/2;
function ph_top(n=profile) = ph_cz(n) + ph_H(n)/2;

// ładowarka
function pd()      = _row(PADS, pad);
function pad_d()   = pd()[1];
function pad_h()   = pd()[2];
function pad_m()   = pd()[3];
function pad_rp()  = (pd()[1] + pd()[4]) / 2;        // promień kieszeni
function pad_cz(n=profile) = ph_cz(n) + ph(n)[6];    // środek ładowarki Z
function pad_top_y()   = D_back + pad_proud;
function pocket_floor()= pad_top_y() - pad_h() - rib_h;
slab_front = D_back - gap;
function slab_back()   = min(slab_front - 8, pocket_floor() - floor_t);

// serwa
function sv(n) = _row(SERVOS, n);
function sv_l(n) = sv(n)[1];  function sv_w(n) = sv(n)[2];
function sv_hb(n) = sv(n)[3]; function sv_ft(n) = sv(n)[4];
function sv_ha(n) = sv(n)[5]; function sv_sp(n) = sv(n)[6];
function sv_el(n) = sv(n)[7]; function sv_hp(n) = sv(n)[8];
function sv_hd(n) = sv(n)[9]; function sv_hw(n) = sv(n)[10];
function sv_sh(n) = sv(n)[11]; function sv_ht(n) = sv(n)[12];
function sv_hornd(n) = sv(n)[13]; function sv_hornt(n) = sv(n)[14];
function sv_m(n) = sv(n)[15];

// jarzmo (układ osi TILT)
function arm_r_in()  = br_x - sv_hornt(servo_tilt) + sv_ht(servo_tilt) + sv_ft(servo_tilt);
function arm_r_out() = arm_r_in() + sv_hb(servo_tilt) + 0.5 + 3;
arm_l_in  = br_x + 13.5;                     // wewn. ścianka ramienia łożyskowego
arm_l_out = arm_l_in + bearing_lip + b608_w;
spacer_len = arm_l_in + bearing_lip - br_x;  // tuleja wspornik -> pierścień wewn. 608
z_deck = -axis_above_deck;                   // góra pokładu (układ TILT)

// wysokości globalne
z_bt = base_h;
z_deck_top = base_h + ls_t + deck_t;
z_axis = z_deck_top + axis_above_deck;
function z_hub_bot() = z_bt + hub_gap;
function z_horn_face(n=servo_pan) = z_hub_bot() + sv_hornt(n) + 0.2;
function z_flange_top(n=servo_pan) = z_horn_face(n) - sv_ht(n);

// lazy susan – otwory (układ PAN)
function ls_bot_holes() = ls_shape == "square"
  ? [for (sx=[-1,1], sy=[-1,1]) [sx*ls_sq_pitch/2, sy*ls_sq_pitch/2]]
  : [for (a=[45:90:315]) ls_round_bot_d/2*[cos(a), sin(a)]];
function ls_top_holes() = ls_shape == "square"
  ? [for (sx=[-1,1], sy=[-1,1]) [sx*ls_sq_pitch/2, sy*ls_sq_pitch/2]]
  : [for (a=[0:90:270]) ls_round_top_d/2*[cos(a), sin(a)]];
ls_access_rot = ls_shape == "square" ? 45 : 0;
function ls_outer_r() = ls_shape == "square" ? ls_sq/sqrt(2) : ls_round_od/2;
function deck_r() = ls_outer_r() + 3;

// =====================================================================
//  Moduły pomocnicze
// =====================================================================
// profil 2D w płaszczyźnie XZ (x->X, y->Z) wyciągnięty wzdłuż Y od y0 do y1
module xz_extrude(y0, y1) {
  translate([0, y1, 0]) rotate([90, 0, 0]) linear_extrude(y1 - y0) children();
}
// profil 2D w płaszczyźnie YZ (x->Y, y->Z) wyciągnięty wzdłuż X od x0 do x1
module yz_extrude(x0, x1) {
  translate([x0, 0, 0]) rotate([90, 0, 90]) linear_extrude(x1 - x0) children();
}
module rrect(p0, p1, r) {   // zaokrąglony prostokąt 2D
  translate([p0[0] + r, p0[1] + r]) offset(r = r)
    square([p1[0] - p0[0] - 2*r, p1[1] - p0[1] - 2*r]);
}
module teardrop2d(r) {      // otwór drukowalny w poziomie (szpic w +y)
  union() { circle(r = r); rotate(45) square(r); }
}
module arc_slot2d(r, w, a0, a1, step=6) {
  for (a = [a0 : step : a1 - step])
    hull() {
      translate(r*[cos(a), sin(a)]) circle(d = w);
      translate(r*[cos(min(a+step, a1)), sin(min(a+step, a1))]) circle(d = w);
    }
}
module cyl_y(d, y0, y1, fn=0) {   // walec wzdłuż Y
  translate([0, y0, 0]) rotate([-90, 0, 0])
    cylinder(d = d, h = y1 - y0, $fn = fn > 0 ? fn : $fn);
}
module cyl_x(d, x0, x1, fn=0) {   // walec wzdłuż X
  translate([x0, 0, 0]) rotate([0, 90, 0])
    cylinder(d = d, h = x1 - x0, $fn = fn > 0 ? fn : $fn);
}
