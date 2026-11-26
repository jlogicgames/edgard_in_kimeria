# edge-giant-right (#597): the tall leaf mass on the right screen edge at the last camera, below the HUD
canvas 240 288 0 0
glyph A leaf-giant-a.txt
glyph B leaf-giant-b.txt
recolour a A akldec baaefb
recolour b B akldec baaefb
glyph stl stem.txt stl
glyph str stem.txt str
# stems first, the leaves over them
put str 213 100
put str 229 132
put str 126 178
put str 142 210
put str 158 242
put str 174 274
put str 82 194
put str 98 226
put str 114 258
put str 135 220
put str 151 252
put str 167 284
put str 183 158
put str 199 190
put str 215 222
put str 231 254
put str 92 238
put str 108 270
put str 139 262
# leaves, the later in front; a and b are the back leaves, a tone darker
put b 134 14
put a 120 70
put a 76 86
put b 56 134
put B 104 72
put A 86 130
put B 60 176
piece edge-giant-right 15 18 plain blur=2.6
