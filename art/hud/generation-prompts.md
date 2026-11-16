# Detailed HUD inventory icons (2026-10-22)

Mode: built-in image generation tool, transparent output. These are UI illustrations,
never world sprites. Runtime files: `assets/images/HUD/{coin,bat,mushroom,fireball}.png`,
128×128 indexed, at most 256 entries including transparency, no dithering, linear
texture filtering in matching 32 px item and ammunition slots.

Identity references: the coin is a nearest-enlarged crop from original `assets/images/Items.png`
(0,0,16,16). The Bat and Mushroom icons (D43, replacing the chicken and key) are not generated.
The Bat icon is the Bat wing jar (#345, 2026-11-16, accepted): `bat-master.grid` is a hand-drawn 128×128 pixel
grid (19 colours; the grid format of `tools/forest_trap_sprites.py`). Its jar, wing vertices and shading bands were
placed by hand and laid into the grid by a one-off scratch script; pores, glints and twine shine were set per pixel.
`bat-master.png` is the grid converted 1:1; the exporter crops it to its opaque bounds and fits it to 128×128 like
every icon here. `mushroom-master.png` is the hand-authored 16×16 world frame
(`art/objects/mushroom-pixel.png`, frame 1) scaled 2× with Scale2x edge smoothing and then
bicubic to 128×128; no image-generation prompt exists for either. The retired chicken and key
icons were removed with their masters.
The original world sheet remains unchanged. Export via `python3 tools/export_object_sprites.py`.
The detailed fireball source is `fireball-master.png` in this directory; its
selected-source prompt is recorded below. It is used only for the HUD.

## coin-master

Reference role: the corresponding original item crop establishes identity.
Selected source: `exec-7becbdf6-9b76-4d06-b132-7fcc70969d47.png`; stored as `coin-master.png`.

**Repainted with the Kimeria sun (#350, 2026-11-16, approved by the author).** No image generator was available, so the
painted master below was edited, not regenerated. The hooked "C" engraving was filled with the face's own gold
(diffused in from the surrounding face, then the face's own brush texture from a clean patch to the right of the C).
The sun of the Kimeria arms (STORY.md, "Kingdom heraldry") was then engraved in the centre: a raised pale-gold disc in
an engraved ring, and eight tapering rays. The rays are foreshortened to the face's 3/4 ellipse (x scale 0.84). The
engraving reuses the C's own dark recess texture, inner shadow and bright bevel. The same disc and eight rays are on the
world coin (`art/objects/coin-pixel.png`). The exact prompt below produced the original painting.

Exact prompt of the original painting:

> Create a detailed HUD inventory icon for Edgard in Kimeria, a beautiful hand-painted remaster of the gold coin in reference image. Reference establishes identity: a thick gold coin seen nearly front on with slightly angled right rim, inner dark hooked C-shaped engraving, white-yellow highlights. Preserve its simple silhouette. Rich controlled golden shades, strong dark contour, smooth clean illustration, readable at 32 pixels; source intended for 128x128/256-color HUD export. Centered single isolated coin filling most of square canvas, true transparent background, no shadow outside, no text or labels, no extra items. This is UI art, NOT low resolution gameplay pixel art.

## fireball-master

Selected source: imagegen `exec-6e69a005-3ffa-4e89-8c44-0df8ffc0419b.png`.

Exact prompt:

> One isolated magical fireball projectile sprite for a 2D side-view fantasy game. Compact round golden-white hot core surrounded by orange-red tongues of flame, a short tapered trailing flame pointing LEFT so the projectile travels RIGHT. Strong single clear silhouette, broad hand-painted colour shapes with restrained highlights, dark red edge for sky readability. Genuine transparent background, no black rectangle, no cast shadow, no floor, no environment, text or other objects. Fill 85 percent of square canvas. Finished warm ochre/red fantasy DOS-remaster artwork, clean natural contours, not photorealistic, no gritty noise or dithering. Must remain legible in a 16 by 16 logical-pixel in-game slot. This raster replaces a code-coloured glowing dot.

