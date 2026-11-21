# Selected object sprite sources

Created with built-in image generation and real transparent output. Only the
selected source artwork is retained; rejected detailed world art is omitted. Exact selected-source prompts follow below.
World sources use original `assets/images/Items.png` for native pixel style,
not portrait masters. Export with `python3 tools/export_object_sprites.py`.
Torch is 8×16, stone 8×8 and fireball 16×16, with at most 16 palette entries,
binary alpha and nearest filtering. Separate detailed HUD sources live under
[`art/hud/`](../hud/generation-prompts.md).

## Floor lever (#413, 2026-11-17)

The lever is hand-drawn, not generated: `lever.grid` (the grid format of the forest traps, one character per pixel)
holds two 16×16 frames, `on` (frame 0, the handle leaning right) and `off` (frame 1, leaning left), in the
`SpriteSheets.LEVER` order. A 3/4 wooden block stands on the ground (light top face with the slot, brown front with two
rivets, dark right side); an iron handle comes out of the slot and ends in the red knob. The base and the pivot are
identical in both frames, and the base's bottom row is the frame's last row, so it rests on the floor line.
`export_levers()` writes `assets/images/objects/lever.png` (32×16) exactly: one palette entry per grid colour, binary
alpha, nothing quantized. It replaced the generated wall-mounted 3/4 lever of 2026-10-23.

## Native gameplay pixel revision

Mode: built-in image generation tool, transparent output. Reference role: actual
`assets/images/Items.png`, pixel style only. Cropping, nearest resizing, binary
alpha and palette quantization export the authored image; no geometry painting.

### torch-pixel

Selected source: `exec-b0efcdd2-c436-4230-8bdd-305b1ce84a75.png`; stored as `torch-pixel.png`.

Exact prompt:

> Create a single gameplay torch sprite for Edgard in Kimeria. Reference image is the ACTUAL original low resolution game sprite sheet; match its crude crisp pixel density and black outlines, NOT modern illustrated item icons. Author exactly an 8 by 16 pixel design, shown magnified with equal square pixel blocks, centered on transparent background. Upright UNLIT wooden torch: dark outline, brown shaft, ochre cloth head, dark collar. Shaft at bottom, grip at horizontal center around row 12; head top. At most 8 solid opaque colors plus transparent. Flat tiny blocks, no gradients, no antialiasing, no semitransparent pixels, no bevel realism, no glow, no flame, no shadow, no text. Entire object visible.

### stone-pixel

Selected source: `exec-a2510983-0471-4679-b777-8d108ff3a6ed.png`; stored as `stone-pixel.png`.

Exact prompt:

> Create a single gameplay throwable stone sprite for Edgard in Kimeria. Reference is ACTUAL original game sprites; match very tiny crude crisp pixel art and dark outlines. Exactly an 8 by 8 pixel design shown magnified as equal square pixel blocks, transparent background. One squat irregular gray-brown rock with dark near black outline and 3 gray-brown shade regions, no details smaller than one grid cell. At most 5 opaque colors plus transparent, no antialiasing, no gradients, no semi transparency, no shadows, no realism, no text. Entire rock visible.

### fireball-pixel

Selected source: `exec-2f5bf39e-5446-4d25-93a7-4dae4ca9f744.png`; stored as `fireball-pixel.png`.

Exact prompt:

> Create a single gameplay fireball projectile sprite for Edgard in Kimeria. Reference is ACTUAL original game sprites, match their very tiny crisp old pixel art with black outlines. Exactly a 16 by 16 pixel design, magnified as equal square blocks, transparent background. Fireball travels RIGHT: round bright yellow core at RIGHT, two or three orange flame tips trailing LEFT. Use only near black outline, dark red, red-orange, orange, golden yellow, pale yellow and transparent. No gradients, no antialiasing, no glow, no semitransparency, no realism, no text. Simple legible silhouette.

## Ladder (2026-10-25, #99)

Mode: **hand-authored native pixels, not image generation.** No image-generation tool was
available when the ladder was made, so the 32×16 master `ladder-pixel.png` was written
directly from the pixel grids below (one letter per pixel; `.` transparent, `O` near-black
outline, `M`/`H` rail shade/highlight, `R`/`S` rung lit/shaded). Front-facing, per the default
for interactive objects. Replace it with a generated master if the author prefers: keep the
registered frame order and the 16×16 frame size, then run `python3 tools/export_object_sprites.py`.

`export_ladders()` only validates the size, makes alpha binary and quantizes both frames to one
shared palette (6 entries including transparency). Frame 0 is the top segment (capped rails), frame 1
the repeating middle; the rung phase matches so a stack reads as one ladder, and both rail ends
run to the tile edge. Runtime `assets/images/objects/ladder.png` is 32×16, nearest-filtered.

```
frame 0 (top)       frame 1 (middle)
..OOOO....OOOO..    ..OMHO....OHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
..OMHOOOOOOHMO..    ..OMHOOOOOOHMO..
..OMHORRRROHMO..    ..OMHORRRROHMO..
..OMHOSSSSOHMO..    ..OMHOSSSSOHMO..
..OMHOOOOOOHMO..    ..OMHOOOOOOHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
..OMHOOOOOOHMO..    ..OMHOOOOOOHMO..
..OMHORRRROHMO..    ..OMHORRRROHMO..
..OMHOSSSSOHMO..    ..OMHOSSSSOHMO..
..OMHOOOOOOHMO..    ..OMHOOOOOOHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
..OMHO....OHMO..    ..OMHO....OHMO..
```

## Moving brick wall (retired 2026-11-21)

The generated brick master and its prompt were removed with the #236 art review: the lever-driven wall is now the
hand-typed wooden gate `art/objects/wall-gate.grid` (no prompt; see `docs/ART_DIRECTION.md`, "Group artwork by meaning").

## Static platform (2026-10-26)

Selected source: `platform-pixel.png`, generated and refined with built-in image generation.
The uploaded screenshot is the design reference; the gray editor background and center
guides are not part of the artwork. The exporter thresholds alpha, trims transparent
margins, resizes the authored silhouette with nearest-neighbor sampling to 32×16, and
quantizes without dithering. Runtime `assets/images/objects/platform.png` is an indexed
PNG with at most 16 palette entries including transparency and binary alpha; it uses
nearest filtering. Static collision spans repeat this 32×16 frame horizontally.

Initial generation prompt:

> Use case: stylized-concept. Asset type: native low-resolution world gameplay sprite for Edgard in Kimeria. Input image 1 is the author's visual reference for the platform's silhouette, earthy colors and pixel-art direction; ignore the gray editor backdrop and the thin center crosshair/guide lines. Primary request: create one single horizontal earth-and-grass platform sprite, exactly 32 pixels wide by 16 pixels tall in its intended native grid, isolated on true transparency. Match the reference closely: a flat compact platform slab with a warm tan/ochre soil top, irregular stepped underside with a dark near-black rocky shadow band, and a deep brown earth base along the bottom. Keep a strong dark outline and crisp chunky square pixel clusters; the top is level and readable as a standing surface. Compose one complete sprite occupying nearly the full 32x16 canvas, centered, with pixel boundaries aligned to a 32-column by 16-row grid. If shown enlarged, every source pixel must be an equal square. Use at most 12 flat opaque colors plus transparency. Constraints: tiny authored gameplay pixel art, hard pixel edges, binary transparency, nearest-neighbor look, no antialiasing, no gradients, no dithering, no glow, no semitransparent pixels. No background, no editor guides, no seams, no text, no characters or extra objects.

Refinement prompt:

> Use case: precise-object-edit. Asset type: native pixel-art world platform sprite. Image 1 is the selected platform artwork to revise. Image 2 is the author's reference for the intended sprite proportions and design. Preserve the warm tan soil top, jagged dark rocky underside, deep brown earth base, dark outline and crisp pixel-art identity. Revise the silhouette so the complete platform fills a 32:16 canvas: it must be a thick slab, about twice as tall as the thin ledge in Image 1, occupying nearly the full 32-pixel width and 16-pixel height. Match Image 2's proportions: roughly 7 rows of tan top/soil, a 2–3 row jagged near-black underside band, and 5–6 rows of deep brown earth, with only a minimal dark outline. Keep the top level and readable as a standing surface. Treat the grid as exactly 32 columns by 16 rows, with every source pixel a crisp equal square. Use at most 12 flat opaque colors plus real transparent pixels outside the silhouette. Remove the excessive empty margin above and below the object. Constraints: hand-authored-looking tiny game pixel art, no smooth lighting, no gradients, no texture noise, no dithering, no antialiasing, no semitransparent edges, no glow, no editor crosshair, no seams, no text, no other objects.

## Bat wing jar (#345, 2026-11-16)

`bat-pixel.png` (32×16, two registered 16×16 frames) replaces the flapping purple bat of D43, which players took
for an enemy (playtest F19). No image generator: it was drawn per pixel as a text grid (the format of
`tools/forest_trap_sprites.py`) and converted 1:1, then exported with `python3 tools/export_object_sprites.py`.
A corked glass jar tied with twine, one dried bat wing inside (finger bones, scalloped edge); frame 2 moves the
glass glint. 9 colours plus transparency; ink `1b1226` and the violets of the old Bat item kept. Contrast at
its one placement (forest-2 x448): outline 0.425 (D76 active floor 0.28).

## Coin with the Kimeria sun (#350, 2026-11-16)

`coin-pixel.png` (32×16, two registered 16×16 frames) replaces the original coin of `images/Items.png`
(playtest F12: a coat of arms on the coins). It carries the sun of the Kimeria arms (STORY.md, "Kingdom heraldry").
No image generator: it was drawn per pixel as a text grid and converted 1:1, then exported with
`python3 tools/export_object_sprites.py` to `assets/images/objects/coin.png`. Frame 1 is the face: an 11×11 disc,
white rim, a dark-gold and navy ring, and the sun on the gold field (a white sun disc with eight navy rays). Frame 2
is the edge-on turn, the sun foreshortened to a column. The original's four colours are kept (`222034`, `c2b902`,
`fff300`, `ffffff`) and so is its white rim, which carries the outline: 0.371 at forest-2 x336 in both frames, the same
as the original (D76 active floor 0.28). The coin still draws no halo (`Collectable.hasHalo`). The old `Items.png` sheet is removed; its elixir cell is `assets/images/ui/boss/elixir.png`.
