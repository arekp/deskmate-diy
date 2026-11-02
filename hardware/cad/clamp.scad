// =====================================================================
//  DeskMate DIY – ZACZEP GÓRNY telefonu (2 szt. na profil)
//  Stopa wchodzi w kanał płyty głowy; śruba M3x12 od tyłu płyty przez
//  szczelinę regulacji do wkładki w stopie. Druk: bokiem na stole.
// =====================================================================
ASM = true;
include <head_plate.scad>

translate([0, 0, clamp_w/2]) rotate([0, 90, 0]) clamp_part();
