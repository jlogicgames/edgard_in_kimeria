# Detailed HUD inventory icons (2026-10-22)

Mode: built-in image generation tool, transparent output. These are UI illustrations,
never world sprites. Runtime files: `assets/images/HUD/{coin,chicken,key,fireball}.png`,
128×128 indexed, at most 256 entries including transparency, no dithering, linear
texture filtering in matching 32 px item and ammunition slots.

Identity references are nearest-enlarged crops from original `assets/images/Items.png`:
coin (0,0,16,16), cooked chicken on plate (32,16,16,16), key (64,16,16,16).
The original world sheet remains unchanged. Export via `python3 tools/export_object_sprites.py`.
The detailed fireball source is `fireball-master.png` in this directory; its
selected-source prompt is recorded below. It is used only for the HUD.

## coin-master

Reference role: the corresponding original item crop establishes identity.
Selected source: `exec-7becbdf6-9b76-4d06-b132-7fcc70969d47.png`; stored as `coin-master.png`.

Exact prompt:

> Create a detailed HUD inventory icon for Edgard in Kimeria, a beautiful hand-painted remaster of the gold coin in reference image. Reference establishes identity: a thick gold coin seen nearly front on with slightly angled right rim, inner dark hooked C-shaped engraving, white-yellow highlights. Preserve its simple silhouette. Rich controlled golden shades, strong dark contour, smooth clean illustration, readable at 32 pixels; source intended for 128x128/256-color HUD export. Centered single isolated coin filling most of square canvas, true transparent background, no shadow outside, no text or labels, no extra items. This is UI art, NOT low resolution gameplay pixel art.

## chicken-master

Reference role: the corresponding original item crop establishes identity.
Selected source: `exec-7a8375e3-9c0b-49d9-a5ef-c39d5dd17432.png`; stored as `chicken-master.png`.

Exact prompt:

> Create a detailed HUD inventory icon for Edgard in Kimeria. Faithful beautiful hand-painted remaster of reference: a roasted CHICKEN DRUMSTICK lying diagonally on a small oval pewter plate, warm reddish golden brown meat at lower left, short clean white bone sticking toward upper right. It is cooked food, NOT a live chicken or bird character. Preserve silhouette and identity, add appetizing controlled shading, strong dark contour, clean polished drawing. Readable at 32 pixels, source intended for 128x128/256-color HUD export. Centered single isolated drumstick and plate filling square, true transparent background, no cast shadow outside, no labels, no text. This is UI illustration, NOT low resolution gameplay pixel art.

## key-master

Reference role: the corresponding original item crop establishes identity.
Selected source: `exec-ef68cd90-3155-4f9b-bb1c-0242b63a7fcf.png`; stored as `key-master.png`.

Exact prompt:

> Create a detailed HUD inventory icon for Edgard in Kimeria. Faithful beautiful hand-painted remaster of reference: small silver key lying horizontally, square open bow at LEFT, straight shaft to RIGHT, two teeth pointing downward near right tip. Preserve recognizable plain silver key silhouette, dark contour and square hole, controlled steel gray shades and bright edge highlights. Readable at 32 pixels, source intended for 128x128/256-color HUD export. Single isolated key centered filling square canvas, true transparent background, no cast shadow, no labels, no text, no ornament. This is polished UI illustration, NOT low resolution gameplay pixel art.


## fireball-master

Selected source: imagegen `exec-6e69a005-3ffa-4e89-8c44-0df8ffc0419b.png`.

Exact prompt:

> One isolated magical fireball projectile sprite for a 2D side-view fantasy game. Compact round golden-white hot core surrounded by orange-red tongues of flame, a short tapered trailing flame pointing LEFT so the projectile travels RIGHT. Strong single clear silhouette, broad hand-painted colour shapes with restrained highlights, dark red edge for sky readability. Genuine transparent background, no black rectangle, no cast shadow, no floor, no environment, text or other objects. Fill 85 percent of square canvas. Finished warm ochre/red fantasy DOS-remaster artwork, clean natural contours, not photorealistic, no gritty noise or dithering. Must remain legible in a 16 by 16 logical-pixel in-game slot. This raster replaces a code-coloured glowing dot.

