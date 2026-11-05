# Detailed HUD inventory icons (2026-10-22)

Mode: built-in image generation tool, transparent output. These are UI illustrations,
never world sprites. Runtime files: `assets/images/HUD/{coin,bat,mushroom,fireball}.png`,
128×128 indexed, at most 256 entries including transparency, no dithering, linear
texture filtering in matching 32 px item and ammunition slots.

Identity references: the coin is a nearest-enlarged crop from original `assets/images/Items.png`
(0,0,16,16). The Bat and Mushroom icons (D43, replacing the chicken and key) are not generated:
`bat-master.png` and `mushroom-master.png` are the hand-authored 16×16 world frames
(`art/objects/*-pixel.png`, frame 1) scaled 2× with Scale2x edge smoothing and then
bicubic to 128×128; no image-generation prompt exists for them. The retired chicken and key
icons were removed with their masters.
The original world sheet remains unchanged. Export via `python3 tools/export_object_sprites.py`.
The detailed fireball source is `fireball-master.png` in this directory; its
selected-source prompt is recorded below. It is used only for the HUD.

## coin-master

Reference role: the corresponding original item crop establishes identity.
Selected source: `exec-7becbdf6-9b76-4d06-b132-7fcc70969d47.png`; stored as `coin-master.png`.

Exact prompt:

> Create a detailed HUD inventory icon for Edgard in Kimeria, a beautiful hand-painted remaster of the gold coin in reference image. Reference establishes identity: a thick gold coin seen nearly front on with slightly angled right rim, inner dark hooked C-shaped engraving, white-yellow highlights. Preserve its simple silhouette. Rich controlled golden shades, strong dark contour, smooth clean illustration, readable at 32 pixels; source intended for 128x128/256-color HUD export. Centered single isolated coin filling most of square canvas, true transparent background, no shadow outside, no text or labels, no extra items. This is UI art, NOT low resolution gameplay pixel art.

## fireball-master

Selected source: imagegen `exec-6e69a005-3ffa-4e89-8c44-0df8ffc0419b.png`.

Exact prompt:

> One isolated magical fireball projectile sprite for a 2D side-view fantasy game. Compact round golden-white hot core surrounded by orange-red tongues of flame, a short tapered trailing flame pointing LEFT so the projectile travels RIGHT. Strong single clear silhouette, broad hand-painted colour shapes with restrained highlights, dark red edge for sky readability. Genuine transparent background, no black rectangle, no cast shadow, no floor, no environment, text or other objects. Fill 85 percent of square canvas. Finished warm ochre/red fantasy DOS-remaster artwork, clean natural contours, not photorealistic, no gritty noise or dithering. Must remain legible in a 16 by 16 logical-pixel in-game slot. This raster replaces a code-coloured glowing dot.

