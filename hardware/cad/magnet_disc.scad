// =====================================================================
//  DeskMate DIY – TARCZA Z MAGNESAMI (1 szt.; ta sama dla wszystkich profili)
//  Wkładana od tyłu w kieszeń ładowarki w przodzie kasku, przed ładowarką.
//  Druk: kołnierzem (strona ładowarki, z gniazdami magnesów) do stołu;
//  skórka 0.6 mm nad gniazdami drukuje się jako krótki mostek – bez podpór.
// =====================================================================
ASM = true;
include <head_plate.scad>
translate([0, 0, -pad_top_y()]) rotate([90, 0, 0]) translate([0, 0, -pad_cz()]) magnet_disc();
