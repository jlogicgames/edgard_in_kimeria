# Boss HUD art

These are independent, replaceable RGBA PNG files; health segments are not procedural shapes
in the game. Keep the segments' transparent corners and use nearest-neighbour pixel art.

| File | Native size | Purpose |
| --- | --- | --- |
| frame.png | 224 × 26 | Iron plate with an opaque well behind the segments |
| segment-filled.png | 14 × 16 | One filled health point |
| segment-empty.png | 14 × 16 | One empty health point |

Replacing these files at the same sizes needs no code change. To change dimensions or
spacing, update the measurements grouped at the top of `ui/BossHud.java`. The current bar has twelve slots. Code repeats the segment
images and animates their opacity; recent damage and restoration add a brief glow using
the same PNGs. `elixir.png` (16 × 16) is the elixir icon, the green potion cropped unchanged from the old `images/Items.png` sheet (since removed).

All three are hand-drawn pixel grids in `art/ui/boss/*.grid` (the boss's palette: blackened iron, mulberry, bone, ember), exported
exactly, one palette entry per grid colour, by `python3 tools/boss_hud_sprites.py` (sizes in `sprite_layouts.BOSS_HUD_SHEETS`, test
`tools/test_boss_hud_sprites.py`); edit the grid, not the PNG. At most 15 opaque colours each, binary alpha, no dithering. The frame is
an opaque plate in a bone rim with a dark well (x 10..213, y 4..21) that the segments sit in; the segments keep the same silhouette so the filled
one hides the empty socket. Design, contrast and the rejected alternative: [ART_DIRECTION.md](../../../../docs/ART_DIRECTION.md#boss-health-bar-playtest-2-f31-543).

Sword hits use blood particles in `effects/KimeriaBloodSystem.java`; there is no impact-star image.

The short original synthesized sounds are `audio/boss_hit.wav` (0.24 s) and
`audio/boss_restore.wav` (0.40 s), mono PCM 16-bit / 44100 Hz (recipes: `docs/SOUND_DIRECTION.md`). They can also be replaced
independently. They respect the game's sound setting and volume. The restoration sound
is deliberately quiet and has no speech.
