// =====================================================================
//  DeskMate DIY – wspólne parametry mechaniki (OpenSCAD 2021.01+)
//  Wszystkie wymiary w mm, masy w g, kąty w stopniach.
//
//  Układy współrzędnych:
//   * GLOBALNY (złożenie): początek na osi PAN, na blacie (Z = 0).
//     X – w prawo robota (patrząc od tyłu), Y – do przodu (tam patrzy
//     ekran), Z – w górę.
//   * GŁOWY: początek na osi TILT; przy pan = 0, tilt = 0 osie jak globalne.
//     Oś TILT = oś X. Tył telefonu leży w płaszczyźnie y = D_back.
//
//  Parametry z komentarzem "ZMIERZ" to wartości katalogowe/szacunkowe –
//  sprawdź suwmiarką przed drukiem (lista w README.md).
// =====================================================================

/* [Wybór wariantu] */
// Profil telefonu (każdy ma własną płytę głowy i zaczepy)
profile = "s23"; // [s23, fold8_open, fold8_closed, flip6_open]
// Ładowarka w płycie głowy (domyślnie Anker 313 Pad – bez magnesów)
pad = "anker313"; // [anker313, samsung_ep_p2900, esr_halolock_mini, anker_zolo_a25m2]
// Serwo PAN (w podstawie)
servo_pan = "MG996R"; // [MG996R, DS3218, MG90S]
// Serwo TILT (w jarzmie)
servo_tilt = "MG996R"; // [MG996R, DS3218]
// Łożysko obrotowe "lazy susan"
ls_shape = "square"; // [square, round]
// Otwory na wentylator 30x30 pod ładowarką
fan_on = true;

/* [Zakres ruchu – zgodny z firmware/include/config.h] */
pan_rng  = [-80, 80];
tilt_rng = [-25, 30];   // - w dół, + w górę

/* [Druk] */
// Luz pasowań na stronę (0.2–0.3 dla PETG)
tol = 0.25;
$fn = 48;

/* [Telefon w etui] */
case_side = 2.0;   // etui – dodatek na każdy bok (długość i wysokość)
case_thick = 3.0;  // etui – dodatek do grubości
case_mass = 40;
axis_dz = 0;       // dodatkowe przesunięcie telefonu względem osi TILT w Z (+ = wyżej)

// --- telefony (bez etui), orientacja pozioma: L wzdłuż X, W wzdłuż Z
//  0 nazwa, 1 L, 2 W, 3 T, 4 masa,
//  5 coil_dx, 6 coil_dz – położenie cewki względem środka telefonu
//    (widok od ekranu: X w prawo, Z w górę) – ZMIERZ (aplikacja/latarka/
//    "szukanie" ładowarką),
//  7 cg_dz – środek telefonu względem osi TILT (0 = oś w środku wysokości)
PHONES = [
  ["s23",          146.3,  70.9, 7.6, 168,  0.0,  0.0, 0],
  // Fold8 Ultra rozłożony poziomo: zawias poziomo; połowa z cewką NA GÓRZE
  ["fold8_open",   158.4, 143.2, 4.1, 215,  0.0, 35.8, 0],
  ["fold8_closed", 158.4,  72.8, 8.9, 215,  0.0,  0.0, 0],
  // Flip6 rozłożony poziomo; dolna połowa (z cewką) po PRAWEJ stronie ekranu
  ["flip6_open",   165.1,  71.9, 6.9, 187, 41.3,  0.0, 0],
];

// --- ładowarki: 0 nazwa, 1 średnica, 2 grubość, 3 masa, 4 luz na średnicy,
//     5 magnetyczna?, 6 szerokość wtyku/kabla wychodzącego z boku
PADS = [
  // Anker 313 Wireless Charger (Pad) A2503 – Qi 10 W, bez magnesów,
  // Ø100 x 11.2 mm, 67 g (dokumentacja), gniazdo micro-USB z boku
  ["anker313",          100.0, 11.2, 67, 0.6, false, 12],
  // Alternatywy magnetyczne (Qi2):
  // Samsung EP-P2900 (25 W): grubość 4.4 wg Samsunga, średnica ZMIERZ
  ["samsung_ep_p2900",   60.0,  4.4, 60, 1.0, true,  10],
  // ESR HaloLock Qi2 Mini: wymiary ZMIERZ
  ["esr_halolock_mini",  58.0,  8.0, 50, 1.0, true,  10],
  // Anker Zolo A25M2: Ø60 x 10.5, 68 g, kabel stały z boku
  ["anker_zolo_a25m2",   60.0, 10.5, 68, 1.0, true,  10],
];
// Kierunek gniazda/kabla ładowarki (w płaszczyźnie XZ: 0 = w prawo,
// 90 = w górę, 270 = w dół, w stronę osi TILT) – ZMIERZ / ustaw przy montażu
pad_port_angle = 270;

// --- serwa: 0 nazwa, 1 dł. korpusu, 2 szer., 3 wys. od dna do spodu uszu,
//  4 grubość uszu, 5 wys. korpusu nad uszami, 6 wypust wałka nad korpusem,
//  7 dł. z uszami, 8 rozstaw otworów w uszach (wzdłuż), 9 Ø otworów,
//  10 rozstaw otworów w poprzek (0 = jeden otwór), 11 odl. osi wałka od
//  bliższego końca korpusu, 12 odl. od górnej pow. uszu do zewn. pow.
//  tarczy orczyka, 13 Ø tarczy orczyka, 14 grubość tarczy, 15 masa
//  WSZYSTKO ZMIERZ na swoim egzemplarzu (klony różnią się do ~1 mm)
SERVOS = [
  ["MG996R", 40.7, 19.7, 27.5, 2.5, 9.5, 3.4, 54.0, 49.5, 4.2, 10.0, 10.2, 13.9, 24.5, 2.5, 55],
  ["DS3218", 40.0, 20.0, 26.2, 2.5, 8.5, 3.3, 54.5, 49.5, 4.2, 10.0, 10.0, 12.3, 24.0, 2.5, 60],
  ["MG90S",  22.8, 12.2, 16.5, 2.5, 6.0, 3.5, 32.5, 27.8, 2.2,  0.0,  6.0, 10.5, 20.0, 2.0, 13.4],
];
// Promienie otworów w tarczy orczyka (pilotowe Ø1.8 pod wkręty z kompletu) – ZMIERZ
horn_hole_r = [7.0, 9.5];

/* [Elementy złączne] */
ins_d = 4.2;       // otwór pod wkładkę gwintowaną M3 (M3 x 5.7, Ø zewn. 4.6–5)
ins_l = 6.0;       // głębokość otworu pod wkładkę
m3_clear = 3.4;
m3_csk_d = 6.6;    // łeb stożkowy M3 (DIN 7991)
m3_nut_af = 5.7;   // nakrętka M3 – rozmiar pod klucz + luz
m8_clear = 8.4;
m8_head_af = 13.4; // łeb śruby M8 (klucz 13)
m8_head_t = 5.6;
// łożysko 608: 22 x 8 x 7
b608_od = 22.0; b608_id = 8.0; b608_w = 7.0;
b608_fit = 0.1;    // naddatek średnicy gniazda (0.0–0.2 dopasuj próbką)

/* [Głowa] */
D_back = 25;       // odległość osi TILT od tyłu telefonu (Y)
gap = 2.5;         // szczelina wentylacyjna telefon–płyta
pad_proud = 0.0;   // wierzch ładowarki względem tyłu telefonu (0 = styk, bez plastiku)
rib_h = 2.5;       // żebra pod ładowarką (kanał powietrza)
floor_t = 3.0;     // dno kieszeni
head_half_w = 56;  // połowa szerokości płyty (X)
hook_h = 6;        // grubość półki pod telefonem
lip_h = 4.0;       // warga półki/zaczepu zachodząca na ekran
lip_t = 2.5;       // grubość wargi
fit_t = 0.6;       // luz na grubość telefonu (pod wkładkę TPU 0.5–1 mm)
shelf_cut_w = 40;  // wycięcie w środku półki (przyciski boczne, dostęp palcem)
clamp_x = 45;      // położenie zaczepów górnych (±X)
clamp_w = 12;      // szerokość stopy zaczepu
clamp_t = 7;       // grubość stopy zaczepu (Y)
clamp_h = 26;      // wysokość stopy zaczepu (Z)
clamp_travel = 6;  // regulacja zaczepu ± (mm)
chan_len = 22;     // długość kanału na wtyk/kabel poza krawędzią ładowarki
br_x = 59.5;       // zewn. powierzchnia wspornika głowy (styk z orczykiem)
br_t = 12.5;       // grubość płyty wspornika (X)
br_fl_x0 = 28;     // wewnętrzny koniec kołnierza wspornika
br_fl_t = 7;       // grubość kołnierza (Y)
br_fl_hz = 28;     // pół-wysokość kołnierza (Z)
br_screw = [[40, 20], [40, -20]]; // śruby kołnierza (|x|, z) względem osi
hub_r = 15;        // promień piasty wspornika wokół osi
ttp = [15.6, 11.6, 3.0]; // kieszeń TTP223 (X, Y, głębokość)
fan_size = 30; fan_pitch = 24;

/* [Jarzmo i podstawa] */
tilt_y = -15;          // położenie osi TILT względem osi PAN (Y)
axis_above_deck = 96;  // oś TILT nad górą pokładu jarzma
post_y = [-64, -28];   // zakres Y słupków ramion (układ osi TILT)
clear_r = 23.5;        // ramiona: maks. odległość od osi w strefie przed osią
arm_l_in = 66;         // wewn. ścianka ramienia łożyskowego (|x|)
bearing_lip = 3;
arm_r_t = 6;           // grubość ramienia serwa
base_h = 58;           // wysokość podstawy (góra płyty górnej)
top_t = 4;             // grubość płyty górnej podstawy
wall = 2.4;            // ścianki podstawy
cover_t = 3;           // pokrywa dolna
base_x = [-92, 92];
base_y = [-82, 78];
base_r = 16;
deck_t = 8;
deck_r = 68;
wire_r = 62;           // promień przelotu przewodów (łuk w płycie podstawy)
wire_w = 14;           // szerokość łuku/otworu na przewody
hub_d = 30;            // piasta PAN – dolna część (w płycie podstawy)
hub_d2 = 37;           // piasta PAN – górna część (w otworze lazy susan)

/* [Łożysko lazy susan] – ZMIERZ */
ls_t = 8;              // wysokość całkowita
ls_id = 40;            // otwór środkowy
ls_sq = 75;            // bok wersji kwadratowej
ls_sq_pitch = 63;      // rozstaw otworów (kwadrat)
ls_round_od = 80;
ls_round_bot_d = 72;   // okrąg otworów pierścienia dolnego
ls_round_top_d = 56;   // okrąg otworów pierścienia górnego

/* [Elektronika w podstawie] – ZMIERZ: [dł, szer] i położenie środka */
esp_dim = [69, 26];   esp_pos = [-37, -63];  // ESP32-S3-DevKitC-1, USB w lewo
xl_dim  = [26, 54];   xl_pos  = [-74, -8];   // XL4015 (serwa)
lm_dim  = [21, 43];   lm_pos  = [72, -4];    // LM2596 (ESP32 + ładowarka)
pd_dim  = [11, 28];   pd_pos  = [44, -65.5]; // PD trigger, USB-C w tył
ballast = [[-56, 40], [56, 73]];             // komora obciążnika (XY)

// =====================================================================
//  Funkcje pomocnicze
// =====================================================================
function _row(tab, key) = tab[search([key], tab)[0]];

// telefon (w etui)
function ph(n=profile)     = _row(PHONES, n);
function ph_L(n=profile)   = ph(n)[1] + 2*case_side;
function ph_H(n=profile)   = ph(n)[2] + 2*case_side;
function ph_T(n=profile)   = ph(n)[3] + case_thick;
function ph_m(n=profile)   = ph(n)[4] + case_mass;
function ph_cx(n=profile)  = -ph(n)[5];             // ładowarka zawsze na X = 0
function ph_cz(n=profile)  = ph(n)[7] + axis_dz;
function ph_bot(n=profile) = ph_cz(n) - ph_H(n)/2;
function ph_top(n=profile) = ph_cz(n) + ph_H(n)/2;
function lip_y0(n=profile) = D_back + ph_T(n) + fit_t;   // wewn. pow. wargi

// ładowarka
function pd()     = _row(PADS, pad);
function pad_d()  = pd()[1];
function pad_h()  = pd()[2];
function pad_m()  = pd()[3];
function pad_rp() = (pd()[1] + pd()[4]) / 2;          // promień kieszeni
function port_w() = pd()[6] + 0.6;
function pad_cz(n=profile) = ph_cz(n) + ph(n)[6];
function pad_top_y()    = D_back + pad_proud;
function pocket_floor() = pad_top_y() - pad_h() - rib_h;
slab_front = D_back - gap;
function slab_back() = min(slab_front - 8, pocket_floor() - floor_t);

// serwa
function sv(n) = _row(SERVOS, n);
function sv_l(n) = sv(n)[1];   function sv_w(n) = sv(n)[2];
function sv_hb(n) = sv(n)[3];  function sv_ft(n) = sv(n)[4];
function sv_ha(n) = sv(n)[5];  function sv_sp(n) = sv(n)[6];
function sv_el(n) = sv(n)[7];  function sv_hp(n) = sv(n)[8];
function sv_hd(n) = sv(n)[9];  function sv_hw(n) = sv(n)[10];
function sv_sh(n) = sv(n)[11]; function sv_ht(n) = sv(n)[12];
function sv_hornd(n) = sv(n)[13]; function sv_hornt(n) = sv(n)[14];
function sv_m(n) = sv(n)[15];
// środek korpusu wzdłuż długości, mierzony od osi wałka
function sv_c(n) = sv_l(n)/2 - sv_sh(n);
// otwory w uszach: [wzdłuż od osi wałka, w poprzek]
function sv_holes(n) = [for (a = [-1, 1], b = (sv_hw(n) > 0 ? [-1, 1] : [0]))
                         [sv_c(n) + a*sv_hp(n)/2, b*sv_hw(n)/2]];

// jarzmo (|x| w układzie osi TILT)
function arm_r_in()  = br_x + sv_ht(servo_tilt) + sv_ft(servo_tilt);
function arm_r_out() = arm_r_in() + arm_r_t;
arm_l_out = arm_l_in + bearing_lip + b608_w;
spacer_len = arm_l_in + bearing_lip - br_x;
z_deck = -axis_above_deck;

// wysokości globalne
z_ls_top   = base_h + ls_t;
z_deck_top = base_h + ls_t + deck_t;
z_axis     = z_deck_top + axis_above_deck;
function z_horn_face(n=servo_pan) = base_h - 1;           // styk orczyk–piasta
function z_ear_top(n=servo_pan)   = z_horn_face(n) - sv_ht(n);

// lazy susan – otwory (układ PAN)
function ls_bot_holes() = ls_shape == "square"
  ? [for (sx=[-1,1], sy=[-1,1]) [sx*ls_sq_pitch/2, sy*ls_sq_pitch/2]]
  : [for (a=[45:90:315]) ls_round_bot_d/2*[cos(a), sin(a)]];
function ls_top_holes() = ls_shape == "square"
  ? [for (sx=[-1,1], sy=[-1,1]) [sx*ls_sq_pitch/2, sy*ls_sq_pitch/2]]
  : [for (a=[0:90:270]) ls_round_top_d/2*[cos(a), sin(a)]];
// otwory serwisowe w podstawie: pod otworami pierścienia górnego po obrocie o 45°
function ls_access_holes() = [for (p = ls_top_holes())
  [p[0]*cos(45) - p[1]*sin(45), p[0]*sin(45) + p[1]*cos(45)]];

// =====================================================================
//  Moduły pomocnicze
// =====================================================================
// profil 2D (x -> X, y -> Z) wyciągnięty wzdłuż Y od y0 do y1
module xz_extrude(y0, y1) {
  translate([0, y1, 0]) rotate([90, 0, 0]) linear_extrude(y1 - y0) children();
}
// profil 2D (x -> Y, y -> Z) wyciągnięty wzdłuż X od x0 do x1
module yz_extrude(x0, x1) {
  translate([x0, 0, 0]) rotate([90, 0, 90]) linear_extrude(x1 - x0) children();
}
module rrect(p0, p1, r) {
  translate([p0[0] + r, p0[1] + r]) offset(r = r)
    square([p1[0] - p0[0] - 2*r, p1[1] - p0[1] - 2*r]);
}
module arc_slot2d(r, w, a0, a1, step=5) {
  for (a = [a0 : step : a1 - step / 2])
    hull() {
      translate(r*[cos(a), sin(a)]) circle(d = w);
      translate(r*[cos(min(a + step, a1)), sin(min(a + step, a1))]) circle(d = w);
    }
}
module cyl_y(d, y0, y1) { translate([0, y0, 0]) rotate([-90, 0, 0]) cylinder(d = d, h = y1 - y0); }
module cyl_x(d, x0, x1) { translate([x0, 0, 0]) rotate([0, 90, 0]) cylinder(d = d, h = x1 - x0); }
// otwór poziomy drukowalny bez podpór (szpic w +Z), oś wzdłuż X
module tear_x(d, x0, x1) {
  yz_extrude(x0, x1) union() { circle(d = d); rotate(45) square(d/2); }
}
