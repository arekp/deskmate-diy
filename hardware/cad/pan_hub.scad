// DeskMate DIY – PIASTA PAN (orczyk serwa PAN -> pokład jarzma, 3 x M3x12 stożkowe)
// Druk: szerszą częścią do stołu.
ASM = true;
include <yoke.scad>
translate([0, 0, z_ls_top]) rotate([180, 0, 0]) pan_hub();
