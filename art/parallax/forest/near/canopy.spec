# near-canopy: a leaf mass hanging from the top edge. Back leaves a tone lighter (palette swap a k l -> k l m), front leaves
# near black; broad leaves tilted down to the left, small ones turned right, some seen edge-on; gaps show the sky through.
canvas 288 136 -32 -24
glyph B leaf-broad.txt
glyph E leaf-edge.txt
glyph S leaf-small.txt
recolour KB B akl klm
recolour KE E akl klm
recolour KS S akl klm
# back leaves along the top edge
put KB -6 -28
put KS 24 -24
put KB 40 -30
put KE 78 -28
put KB 92 -26
put KS 124 -22
put KB 146 -30
put KS 176 -24
put KE 204 -22
put KB 180 -14
put KS 60 -10
# front leaves along the top edge
put B 4 -18
put S 34 -8
put B 58 -20
put E 96 -12
put B 112 -16
put S 140 -6
put B 160 -22
put S 186 -4
# the hanging tier: big leaves with gaps between them
put E 20 8
put B 26 14
put S 76 12
put E 100 18
put B 112 22
put S 150 20
put E 176 14
put B 180 26
# the lowest leaves
put S 52 38
put E 136 44
put B 84 50
piece near-canopy 14 7 hang
