# near-fern: four fern fronds hanging from a leafy clump above the frame. Each rachis is one typed glyph (f1 .. f4 in rachis.txt):
# a continuous stem that droops 1 px sideways every 8 to 14 rows, one way per frond, thinning to a tip. Each pinna is placed by hand
# on its own row of the stem, big near the top, smaller toward the tip, with irregular spacing and some left out. The fronds differ:
# 1 mid-length, drooping left, dense; 2 the longest, drooping left, big pinnae; 3 short and sparse, drooping right; 4 the shortest,
# drooping right, dense and small.
canvas 288 152 -32 -24
glyph B leaf-broad.txt
glyph E leaf-edge.txt
glyph S leaf-small.txt
recolour KB B akl klm
recolour KE E akl klm
recolour KS S akl klm
glyph f1 rachis.txt f1
glyph f2 rachis.txt f2
glyph f3 rachis.txt f3
glyph f4 rachis.txt f4
glyph Rbig pinna.txt Rbig
glyph Lbig pinna.txt Lbig
glyph Rmid pinna.txt Rmid
glyph Lmid pinna.txt Lmid
glyph Rsmall pinna.txt Rsmall
glyph Lsmall pinna.txt Lsmall
# the clump above the frame
put KB -4 -34
put KS 34 -28
put KB 60 -36
put KE 100 -30
put KB 112 -34
put KS 150 -26
put KB 172 -34
put B 20 -26
put S 88 -22
put B 140 -28
# frond 1: stem x 34 at the top, 1 px left every 14 rows, to y 95
put f1 26 -10
put Rbig 36 -3
put Lbig 15 1
put Rbig 35 4
put Lbig 14 8
put Rbig 35 10
put Lbig 14 14
put Rbig 35 17
put Lbig 13 21
put Rbig 34 23
put Lbig 13 27
put Rmid 34 30
put Lmid 18 33
put Rbig 33 36
put Lbig 12 40
put Rmid 33 43
put Lmid 17 46
put Rmid 32 49
put Lmid 17 52
put Rmid 32 56
put Lmid 17 59
put Rsmall 31 62
put Lsmall 22 64
put Rmid 31 68
put Lmid 16 71
put Rsmall 30 75
put Lsmall 21 77
put Rsmall 30 81
put Lsmall 21 83
put Rsmall 30 87
# frond 2: stem x 92 at the top, 1 px left every 10 rows, to y 111
put f2 79 -10
put Rbig 94 -4
put Lbig 72 0
put Rbig 93 4
put Lbig 72 8
put Rbig 92 11
put Lbig 71 15
put Rbig 92 19
put Lbig 70 23
put Rbig 91 27
put Lbig 69 31
put Rbig 90 35
put Lbig 69 39
put Rbig 89 42
put Lbig 68 46
put Rmid 88 50
put Lmid 73 53
put Rmid 88 58
put Lmid 72 61
put Rmid 87 66
put Lmid 72 69
put Rmid 86 74
put Lmid 71 77
put Rsmall 85 82
put Lsmall 76 84
put Rsmall 85 89
put Lsmall 75 91
put Rsmall 84 96
put Lsmall 75 98
put Rsmall 83 103
# frond 3: stem x 140 at the top, 1 px right every 12 rows, to y 75
put f3 139 -10
put Rbig 142 -1
put Lbig 122 3
put Rbig 143 9
put Lbig 122 13
put Rmid 144 19
put Lmid 129 22
put Rmid 145 29
put Lmid 130 32
put Rmid 146 40
put Lmid 131 43
put Rsmall 147 50
put Lsmall 138 52
put Rsmall 147 59
put Lsmall 138 61
put Rsmall 148 68
# frond 4: stem x 192 at the top, 1 px right every 8 rows, to y 65
put f4 191 -10
put Rmid 194 -4
put Lmid 179 -1
put Rmid 195 2
put Lmid 180 5
put Rmid 196 8
put Lmid 181 11
put Rmid 196 13
put Lmid 182 16
put Rmid 197 19
put Lmid 183 22
put Rsmall 198 25
put Lsmall 189 27
put Rmid 199 30
put Lmid 184 33
put Rsmall 199 36
put Lsmall 191 38
put Rsmall 200 41
put Lsmall 191 43
put Rsmall 201 47
put Lsmall 192 49
put Rsmall 201 52
put Lsmall 193 54
put Rsmall 202 58
put Lsmall 194 64
piece near-fern 14 8 hang
