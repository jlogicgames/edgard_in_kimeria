# edge-giant-left (#597): the tall leaf mass on the left screen edge at the first camera, below the HUD
canvas 208 352 0 0
glyph A leaf-giant-a.txt
glyph B leaf-giant-b.txt
recolour a A akldec baaefb
recolour b B akldec baaefb
glyph stl stem.txt stl
glyph str stem.txt str
# stems first, the leaves over them
put stl 77 120
put stl 61 152
put stl 45 184
put stl 29 216
put stl 13 248
put stl -3 280
put stl -19 312
put stl 132 134
put stl 116 166
put stl 100 198
put stl 84 230
put stl 68 262
put stl 52 294
put stl 36 326
put stl 55 186
put stl 39 218
put stl 23 250
put stl 7 282
put stl -9 314
put stl 95 200
put stl 79 232
put stl 63 264
put stl 47 296
put stl 31 328
put stl 118 206
put stl 102 238
put stl 86 270
put stl 70 302
put stl 54 334
put stl 100 284
put stl 84 316
put stl 68 348
put stl 21 312
put stl 5 344
put stl 110 330
# leaves, the later in front; a and b are the back leaves, a tone darker
put a 86 12
put b 68 48
put A 64 78
put a 104 92
put B 54 120
put b 36 198
put A 30 204
put B 46 244
piece edge-giant-left 13 22 plain blur=2.6
