# Selected object sprite sources

Created with built-in image generation and real transparent output. Only the
selected source artwork is retained; rejected detailed world art and earlier
lever viewpoints are omitted. Exact selected-source prompts follow below.
World sources use original `assets/images/Items.png` for native pixel style,
not portrait masters. Export with `python3 tools/export_object_sprites.py`.
Torch is 8×16, stone 8×8 and fireball 16×16, with at most 16 palette entries,
binary alpha and nearest filtering. Separate detailed HUD sources live under
[`art/hud/`](../hud/generation-prompts.md).

## Practice-light three-quarter revision (2026-10-23)

The author changed the lever from en face to **3/4** for clearer depth and handle
motion. Other interactive objects keep their prior default until separately revised.
Mode: built-in image generation, real transparent output. The selected sources are stored together in `lever-pixel.png` (2508×1254):
left = up/on, right = down/off. Each registered source frame is 1254×1254.
Runtime `assets/images/objects/lever.png` is one 32×16 sheet with the same order,
16×16 frames, a shared palette of at most 16 entries including binary alpha,
nearest filtering and no dithering. The exporter resizes the full registered
frames and quantizes them together; no geometry is painted by code. The base
and pivot stay fixed across both states.

### Three-quarter up

Selected source: `exec-f6dcbadc-a575-4f11-aa81-69d55775887b.png`.
References: former frontal up master for object identity, original `Items.png` for
native pixel style. Exact prompt:

> Use case: precise-object-edit. Redraw this medieval lever as ONE native gameplay pixel-art sprite in a CLEAR THREE-QUARTER VIEW (3/4), UP / ON state. Reference 1 is the existing front-view lever, identity only: retain ochre wooden mounting plate, gray iron mechanism, brown shaft, red grip and dark outline. Reference 2 Items.png is the original DOS game's tiny flat-color pixel style. Change the viewpoint: show a broad oblique front face plus a narrow darker RIGHT SIDE face and a tiny TOP face of the mounting plate, clearly giving depth. The front panel remains most of the visible width: do not show the thin profile, and do not use a symmetrical frontal elevation. Make a simple compact 16x16 pixel design displayed enlarged as equal crisp square cells. Mounting plate occupies roughly grid columns3..12 and rows6..13, oblique with visible right thickness. Fixed iron pivot near grid(8,8). Short brown shaft rises from the pivot toward grid(6,2), ending in a chunky RED grip. This is unmistakably UP / ON. Fill about 80% of the canvas height and 65% of the width, leaving only a one-cell transparent border. Exactly one complete lever, isolated on genuine transparent background, square canvas. At most 12 opaque flat colors plus transparency: near-black contour, dark/medium gray iron, ochre/brown wood and red grip. Hard edges, no antialiasing, no gradients, no dithering, no glow, no cast shadow, no floor or wall/background, no text, no UI, no hand, no second pose. The matching DOWN pose will preserve exactly the same plate/pivot/canvas and move only the handle.

### Three-quarter down

Selected source: `exec-0921c9c5-273b-4b25-9640-f69e0fd44e52.png`.
Edit target: the three-quarter up source; stationary panel, pivot, angle and canvas
remain invariant. Exact prompt:

> Use case: precise-object-edit. Create the matching DOWN / OFF frame of this THREE-QUARTER (3/4) medieval pixel-art lever. Keep the exact square transparent canvas and the same oblique wooden mounting plate, its darker right side, metal front panel, rivets and pivot at exactly their original coordinates. Do NOT rotate the entire object, shift the base, change scale or redraw the stationary panel. Change ONLY the movable brown shaft and red grip: remove the upright handle and grip above the pivot, restoring transparency or stationary wood/metal behind them, then rotate the handle DOWN toward the LOWER LEFT / foreground. The fixed pivot is near 49% of canvas width, 65% of canvas height. The lowered red grip should be near 32% width, 84% height, clearly BELOW the pivot, fully inside the canvas; the shaft is visibly foreshortened by the existing three-quarter viewpoint. Keep the same chunky red grip identity and block size. Preserve the dark outlined iron pivot cap in front of the shaft junction. The OFF state must look distinctly lowered, never a diagonal-up pose. Keep all stationary plate edges, perspective, palette and lighting exactly aligned with the ON frame. Native tiny pixel-art gameplay asset, not an illustrated HUD icon: at most 12 opaque flat colors plus real transparency, crisp square blocks, no smoothing, no gradients, no dithering, no glow, no shadows, no background, no text, no hand, no extra objects.


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

## Moving brick wall (2026-10-26; rounded-base revision)

Selected source: `wall-brick-master.png`; this restores the rounded lower silhouette
from the first generated wall and removes its gray backing. The source has pixel-stepped
rounded lower corners and a broad flat center base. `export_brick_wall()` thresholds
alpha, trims transparent source margins, nearest-resizes to the 48×96 runtime frame, and
quantizes without dithering. The centered base reaches the frame's lower edge.

Exact revision prompt:

> Use this FIRST wall sprite as the source artwork. Preserve its exact warm brown brick design, brick layout, dark outlines, pixel-art style, overall proportions, and especially its rounded, stepped lower-left and lower-right corners and centered flat bottom. Make one cleanup only: remove the solid gray background so every pixel outside the existing brick wall silhouette is fully transparent. Do not square, crop, extend, redesign, or straighten the rounded lower corners. Do not change the wall or add any background, floor, shadow, glow, or scenery. Return the complete wall centered on a transparent canvas, keeping the broad flat center of the lowest course touching the bottom canvas edge. Crisp hard pixel boundaries, no antialiasing.

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
