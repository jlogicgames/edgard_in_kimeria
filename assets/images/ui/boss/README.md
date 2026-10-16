# Boss HUD art

These are independent, replaceable RGBA PNG files; health segments are not procedural shapes
in the game. Keep transparent backgrounds and use nearest-neighbour pixel art.

| File | Native size | Purpose |
| --- | --- | --- |
| frame.png | 224 × 26 | Outer frame, with transparent interior |
| segment-filled.png | 14 × 16 | One filled health point |
| segment-empty.png | 14 × 16 | One empty health point |

Replacing these files at the same sizes needs no code change. To change dimensions or
spacing, update the measurements grouped at the top of `ui/BossHud.java`. The current bar has twelve slots. Code repeats the segment
images and animates their opacity; recent damage and restoration add a brief glow using
the same PNGs. The existing green potion at (80,0) in `images/Items.png` is the elixir icon.

`frame.png` and `segment-empty.png` were authored locally as simple native-resolution pixel
geometry. The filled tile uses built-in ImageGen, trimmed and reduced with nearest
neighbour sampling. Original prompt set:

- Filled: “Single isolated FILLED HEALTH SEGMENT sprite for a retro pixel-art fantasy
  platformer UI, transparent background. ONLY one upright rectangular tile approximately
  3:4 aspect ratio, centered. Crisp pixel edges, ruby crimson red fill, warm red highlight
  on top/left, dark burgundy bottom/right bevel and charcoal outline. Flat restrained SNES
  style. No text, additional icons, full health bar, scenery or cast shadow.”

Sword hits use blood particles in `effects/BloodBurst.java`; there is no impact-star image.

The short original synthesized sounds are `audio/boss_hit.wav` (75 ms) and
`audio/boss_restore.wav` (250 ms), mono PCM 16-bit / 22050 Hz. They can also be replaced
independently. They respect the game's sound setting and volume. The restoration sound
is deliberately quiet and has no speech.
