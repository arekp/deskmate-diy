// =====================================================================
//  DeskMate DIY – DASZEK (górny zaczep telefonu), 1 szt. na profil
//  Wsuwany w gniazdo nad telefonem, 2 x M3x20 z łbem grzybkowym przez
//  pionowe szczeliny (regulacja 6 mm) do wkładek w płycie kasku.
//  Druk: przednią powierzchnią do stołu, bez podpór.
// =====================================================================
ASM = true;
include <head_plate.scad>
translate([0, 0, y_front()]) rotate([-90, 0, 0]) visor();   // przodem do stołu
