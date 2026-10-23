---
name: kimeria-art-direction
description: Creates and reviews Edgard in Kimeria portraits, gameplay sprites, items, projectiles and illustrations with separate native pixel-art world sprites and detailed HUD/portrait artwork. Use for new or revised game artwork and any visual placeholder for a world object.
---

# Kimeria art direction

Read [art direction](../../../docs/ART_DIRECTION.md) and the relevant
[original DOS references](../../../docs/ORIGINAL_DOS.md) before drawing.
The author clarified the scope on 2026-10-22 after seeing detailed icons used in
world gameplay. Choose the asset's destination BEFORE choosing a style or reference.
Portrait approval does not authorize the same treatment for gameplay sprites.

## Three distinct asset contracts

- **World gameplay:** inspect the actual shipped sprite sheets (`assets/images/Items.png`,
  `assets/images/hero/Player.png` and the relevant map tiles). Preserve native tiny pixel
  art, low resolution, a very limited palette, solid outlines and hard pixel boundaries.
  New small items normally use 8×8, 8×16 or 16×16 canvases (up to 32×32 when the object
  warrants it), at most 16 palette entries INCLUDING transparency, binary alpha,
  no antialiasing, no gradients, no dithering and nearest filtering. Draw them at native
  logical dimensions or integer multiples; never shrink detailed portrait/HUD artwork
  into a gameplay object. Existing sheets contain multiple frames/palettes; inspect a
  frame rather than imposing a global 16-color limit on an entire legacy sheet.
- **HUD inventory icons:** separate 128×128 exports, at most 256 palette entries including
  transparency, clean detailed illustration, no dithering, linear filtering in the small
  HUD slot. Use the world sprite as the identity reference: the cooked chicken on its
  plate stays cooked chicken, the silver key stays the same key. The tiny world chicken
  remains unchanged; its detailed HUD counterpart is a different asset/path.
- **Dialogue portraits / illustrations:** preserve the accepted richer remake/remaster
  style and character identity described below. Portrait runtime exports stay 256×256,
  up to 256 palette entries, with linear filtering. Coarse NES blocks are superseded
  for these illustrations only, not for world gameplay.

Use original GAMEPLAY sheets as world-style references. Detailed portrait masters
are references for illustrations, not a world sprite style baseline. For HUD icons,
combine the original item identity with a clean remastered finish. Record each asset's
category, native dimensions, palette, alpha and filter. Inspect the actual exported
pixels and the in-game result, not just the high-resolution source.

## Viewpoint of interactive objects

The author specified on 2026-10-23 that actionable world objects are seen on the
hero's left-hand side, with their working face readable to the viewer. The latest
lever revision explicitly selects **three-quarter (3/4) view**, superseding its
previous en-face/front-only requirement. Show the lever's broad working face plus
clear side/top thickness so its handle and up/down states read at native size;
do not return to a thin profile or force bilateral frontal symmetry. Preserve the
same mounting plate, pivot, angle and canvas registration across both poses.
Other interactive objects retain the existing front-facing default unless their
own design is revised; do not silently redraw every item because the lever changed.
Check the selected viewpoint in prompts, tiny runtime exports and actual gameplay.
Keep native pixel-art constraints, and keep character/HUD/portrait style separate.

## Gameplay artwork must be a sprite

Physical game artwork (characters, enemies, items, weapons, torches, rocks,
projectiles and scenery) must be an authored raster sprite/animation loaded from
`assets/images/`. Reuse suitable existing artwork or use the imagegen skill.
Do not replace it with `renderHolder`, coloured white quads, tinted particle dots,
ShapeRenderer/Pixmap drawings, SVG/Canvas/PIL/Java2D shape painting or any other
code-generated silhouette. This rule includes prototypes and small objects;
"temporary", "simple" and "only a few pixels" are not exemptions.

Inspect existing assets and style references before generation. Keep transparent
masters and the exact generation prompts under `art/`; export the selected drawing
repeatably. Code may crop alpha, resize or quantize an authored image without
inventing its drawing. Validate the actual in-game silhouette, contrast, filtering
and alpha in both relevant renderers. A missing sprite is missing work; do not
silently substitute procedural artwork.

UI frames/focus rectangles, debug collision measurements, blank GPU storage and
truly time-varying VFX are separate categories. They carry interface geometry,
measurements or simulated/transient state, rather than an object's authored identity.
They may use primitives for that purpose. A torch's flame particles may be procedural;
its physical handle/head must be a sprite. A static dot is not a stone/projectile
sprite, and calling an object an "effect" does not change the category.

Run `python3 tools/check_sprite_art.py` (or `./gradlew validateSpriteArt`). The
project's PostToolUse/Stop hook and release-build gate reject primitive object-art
sources, including newly named helpers, and validates the new world/HUD PNG contracts. Do not weaken the gate to make an item pass;
replace the item drawing with artwork. New legitimate UI/VFX permissions must name
the exact method and primitive kind and explain what non-art state they carry.

## References and portrait continuity

Inspect the actual original identity reference and the current detailed masters
in `art/portraits/`, accepted by the author as the future style baseline on 2026-10-22.
Use both as image inputs: DOS art establishes identity;
remaster masters establish consistent finish, contours, shading and palette family.
Keep the warm ochre/brown tones, black hair/shadows and red accents, recognizable
face proportions and character silhouettes. Preserve Edgard's long black hair,
narrow red headband and angular clean-shaven face; preserve the healer's age,
bald crown, very long ochre beard and red hooked staff. Avoid generic fantasy
redesigns, photorealism, 3D/anime styling, noisy texture and dithering.

For raster creation or substantive edits, use the available image-generation skill.
Keep selected high-resolution transparent masters in the repository and record exact
prompts/reference roles. Current portrait exports are 256×256 PNGs, at most 256 palette
entries including transparency, with no dithering. `tools/PortraitExport.java` performs
the repeatable export; use [portrait instructions](../../../docs/PORTRAITS.md).
Use the established master as a style reference for the next related image rather
than reconstructing the style from a text prompt alone. Keep new characters' unknown
design choices distinct from documented original-artist facts.

Compare the export with its DOS identity source and the other remastered characters,
then inspect it in its actual dark dialogue frame at game size. Use the rendering
skill for desktop/web orientation, transparency, scaling and paused presentation;
use the dialogue skill for speaker/text behavior. Record author artwork review for
each revision separately from automated/layout checks. Approval of an older image
does not automatically approve a new revision.
