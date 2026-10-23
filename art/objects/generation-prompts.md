# Object sprite sources (2026-10-22)

Current lever artwork is the three-quarter revision below. Earlier frontal and
profile poses are superseded and retained only as source history.

Created with the built-in image generation tool, genuine transparent output.
Current world sources are `*-pixel.png`, generated from the original Items.png style,
not portrait masters. Runtime exports are torch 8×16, stone 8×8 and fireball 16×16,
indexed with at most 16 entries, binary alpha, nearest resizing/filtering and no
dithering. `python3 tools/export_object_sprites.py` exports both the native world
art and separate detailed HUD icons. New artwork still needs author visual review.

The older detailed `*-master.png` sources below are historical: the author rejected
that finish in the world. The fireball detailed master is now used ONLY for the
128×128 HUD icon. The other older detailed masters are retained as source history,
not loaded by the game. See the later pixel prompts at the end and
[HUD sources](../hud/generation-prompts.md).

## Practice-light three-quarter revision (2026-10-23)

The author changed the lever from en face to **3/4** for clearer depth and handle
motion. This supersedes the historical frontal/profile lever poses below; other
interactive objects keep their prior default until separately revised.
Mode: built-in image generation, real transparent output. Current aligned sources
are `lever-up-pixel.png` and `lever-down-pixel.png`, both 1254×1254. Runtime exports
stay separate native 16×16 indexed images with a shared palette of at most 16
entries including binary alpha, nearest filtering and no dithering. The exporter
resizes the full registered canvases and quantizes the authored images together;
no object geometry is painted by code. The base and pivot stay fixed; up means on,
down means off. Native comparison: `docs/references/lever-three-quarter-native-preview.png`.

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

## Historical practice-light frontal revision (2026-10-23)

Superseded by the author's later three-quarter lever choice. These masters are
archived as `lever-up-frontal-pixel.png` and `lever-down-frontal-pixel.png`.

Author correction: all actionable world objects face the viewer en face, as seen
on the hero's left-hand side. Both lever poses now show a broad frontal mounting
panel with a centered vertical handle. The upper grip means on; lower grip means
off. The old diagonal/profile poses recorded later in this file are superseded.

Mode: built-in image generation. Historical transparent masters are
`lever-up-frontal-pixel.png` and `lever-down-frontal-pixel.png`; the existing exporter kept their
full registered canvas, exports native 16×16 indexed PNGs with a shared palette of
at most 16 entries including binary transparency, no dithering and nearest filtering.
No geometry was drawn with code.

### Frontal up

Selected source: `exec-79b5b55c-f3ca-417c-9e63-180f62b4a8f9.png`.
References: original `Items.png` for native gameplay style, `torch.png` for palette.
Exact prompt:

> Use case: stylized-concept. Create exactly ONE tiny native 16x16 pixel-art interactive wall lever sprite for Edgard in Kimeria, UP/ON pose, FRONT VIEW / EN FACE. Reference 1 Items.png supplies the original DOS game's tiny pixel-art style, limited flat colors and black outline. Reference 2 torch.png supplies warm ochre/brown palette only. The player sees the FRONT FACE of interactive objects on the wall at his left hand: show the broad rectangular mounting panel facing directly toward the viewer. Orthographic front elevation, bilateral symmetry, no side face, no perspective, no isometric depth. Centered ochre wooden backing plate roughly columns4..11 rows4..13 with dark gray iron front panel and two tiny rivets; central visible pivot at column8 row8. The brown handle goes STRAIGHT VERTICALLY UP from central pivot to a red rectangular grip at rows1..3, centered horizontally. Clear separate silhouette and broad frontal plate, unmistakably frontal rather than a thin side-view mechanism. Square transparent canvas representing an exact 16 by 16 grid displayed enlarged with crisp equal square pixel cells. Use at most 10 opaque flat colors plus real transparency, black outlines, gray metal, ochre/brown wood and red grip. Full object fits inside with one pixel transparent border, base near row14. No diagonal handle, no side-on view, no gradients, no antialiasing, no dithering, no shadows, no glow, no scene, no hero, no text, no UI. A matching DOWN pose will later keep panel and pivot fixed and move the same grip straight below the pivot.

### Frontal down

Selected source: `exec-d554e162-571b-4b86-914e-082a6ba89f83.png`.
Edit target: the frontal up master, with panel/pivot/canvas preserved.
Exact prompt:

> Use case: precise-object-edit. Edit this FRONT VIEW / EN FACE pixel-art lever into its matching DOWN / OFF pose. Preserve the exact square transparent canvas and all stationary geometry at exactly the same coordinates: the broad ochre wooden backing panel, front-facing gray metal plate, left/right rivets, and centered circular pivot around (627,725) in this 1254-square image. Change only the movable handle and red grip. Remove the entire upright handle/grip above the pivot, restoring transparency or the original panel where appropriate. Extend the same brown handle STRAIGHT VERTICALLY DOWN from the pivot along the central column, passing in front of the lower mounting plate, and put the same red rectangular grip centered around (627,1060), staying inside the canvas. Draw the small pivot face in front of the shaft junction as in the reference. The handle must unmistakably point DOWN, no diagonal, no side view, no perspective, no isometric view. Keep the frontal symmetric broad mounting panel exactly aligned with the UP pose. Preserve crisp square pixel blocks, black outline, warm ochre/brown wood, dark gray metal and red grip. No antialiasing, no gradients, no dithering, no shadows, no glow, no scene, no text, no UI. One sprite on real transparent background.

## Superseded detailed world sources

## Torch

Reference inputs, style only: `art/portraits/edgard-master.png` and
`assets/images/hero/Player.png`. Selected source: imagegen
`exec-15713e24-356d-41f9-881d-41512c77f832.png`.

Exact prompt:

> Create a new game sprite asset, not an edit of the references. References are style references only: image 1 establishes Edgard in Kimeria's approved warm ochre/brown, hand-painted DOS fantasy remaster style; image 2 establishes small side-view gameplay readability. Subject: exactly ONE unlit portable medieval wooden torch. Straight vertical short dark-brown wooden handle, dark iron collar and a recognizable ochre cloth-wrapped head, no flame, no glow, no particles. Full object isolated on genuinely transparent background, centred, filling about 85 percent of height, 2D side view, no perspective floor, no cast shadow, no environment, no text, no frame, no UI. Thick enough handle and strong simple silhouette to remain legible at a 12 by 16 logical-pixel gameplay size; smooth clean hand-painted shading with restrained detail rather than grain, stippling or dithering. Preserve the natural warm ochre/black palette and artist's coherent contour language. Do not include the reference characters. This artwork will replace a code-drawn placeholder; provide finished drawable raster art.

## Stone

Selected source: imagegen `exec-56d080b6-ef2a-4cb5-bbd4-cb5a0e9cf720.png`.

Exact prompt:

> One isolated heavy oval stone for a 2D side-view game. Dark grey/brown rock with a few broad hand-painted facets, warm ochre edge light, readable chunky irregular silhouette. No moss, runes, floor, cast shadow, glow, particles, text or additional objects. Genuine transparent background. Full object centred filling 80 percent of square canvas. Finished hand-painted fantasy DOS-remaster game sprite, natural clean contours and broad coherent shading, no noisy texture or dithering; must read at a 12 by 12 logical-pixel size. This is a raster sprite to replace a grey procedural dot, not a UI icon.

## Fireball

Selected source: imagegen `exec-6e69a005-3ffa-4e89-8c44-0df8ffc0419b.png`.

Exact prompt:

> One isolated magical fireball projectile sprite for a 2D side-view fantasy game. Compact round golden-white hot core surrounded by orange-red tongues of flame, a short tapered trailing flame pointing LEFT so the projectile travels RIGHT. Strong single clear silhouette, broad hand-painted colour shapes with restrained highlights, dark red edge for sky readability. Genuine transparent background, no black rectangle, no cast shadow, no floor, no environment, text or other objects. Fill 85 percent of square canvas. Finished warm ochre/red fantasy DOS-remaster artwork, clean natural contours, not photorealistic, no gritty noise or dithering. Must remain legible in a 16 by 16 logical-pixel in-game slot. This raster replaces a code-coloured glowing dot.

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

## Practice-light lever poses (2026-10-23, superseded profile)

Mode: built-in image generation. Both selected transparent source canvases are
1254×1254 and are kept aligned, including their stationary mounting plate and pivot.
Runtime `assets/images/objects/lever-up.png` and `lever-down.png` are separate
16×16 indexed sprites, a shared palette of at most 16 entries including binary alpha,
nearest filtering/resizing and no dithering. Up means the linked torch is burning;
down means off. The exporter resizes the full registered canvas, not independent
alpha crops, then quantizes both frames together. No object geometry is painted in code.

### Lever up

Selected source: `exec-f278b035-774a-459c-b270-6417a4d64d80.png`; now archived as `lever-up-profile-pixel.png`.

Reference role: original Items.png and torch.png establish native gameplay style.

Exact prompt:

> Create ONE native pixel-art wall/floor lever sprite in the UP / ON position for Edgard in Kimeria. Reference images: original Items.png sheet and existing torch.png establish the actual tiny gameplay pixel-art style, dark outlines, brown/ochre metal/wood palette; not illustrated HUD artwork. Square transparent canvas representing an exact 16 by 16 pixel grid, displayed magnified with crisp square cells. The fixed dark gray metal mounting plate with ochre wooden base occupies the lower-right part, pivot centered around grid (10,11), base ends at row14. A short brown metal lever rises diagonally from pivot to upper-left, ending in a small red-brown square grip near (4,3). Simple readable old-game sprite, at most 12 opaque flat colors plus transparency. Keep all artwork inside the square canvas with one transparent grid cell border. No antialiasing, no gradients, no soft shadows, no glow, no dithering, no text, no UI. Exactly one lever, no hands, no environment. This is the ON pose; a matching OFF pose will later lower only the handle while keeping the base and pivot fixed.

### Lever down

Selected source: `exec-b604e4eb-b8f1-42f2-bea9-8d7a9dca64fe.png`; now archived as `lever-down-profile-pixel.png`.

Reference role: the up master is the edit target; mounting plate/pivot remain invariant.

Exact prompt:

> Edit this pixel-art lever sprite to create the matching DOWN / OFF pose. Preserve the exact square canvas, transparency, the mounting plate, wooden base, metal ring and pivot at exactly the same coordinates and same size. Change ONLY the movable brown handle and red-brown grip: rotate the handle around the existing pivot toward the LOWER LEFT. In this 1280-square reference the pivot is near (830,790); move the grip centre from near (470,375) to near (470,1070), keeping it inside the canvas. The lever must unmistakably point DOWN in the final image. Remove the old upper-left handle completely and preserve real transparency there. Keep the same crisp square pixel block size, dark contour, brown/ochre/gray/red flat colors and tiny-game style. No new objects, no labels, no gradients, no antialiasing, no dithering, no glow, no background, no shadows. This will be the second aligned frame of one 16x16 gameplay object; absolutely do not shift or redraw the stationary mounting plate/base/pivot.
 
