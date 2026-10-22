---
name: kimeria-art-direction
description: Creates and reviews Edgard in Kimeria portraits and illustrations while preserving the original artist's style in the remake and remaster. Use for new artwork, visual variants or revisions to the game's art direction.
---

# Kimeria art direction

Read [art direction](../../../docs/ART_DIRECTION.md) and the relevant
[original DOS references](../../../docs/ORIGINAL_DOS.md) before drawing.
The author chose a more beautiful remake/remaster on 2026-10-22: richer color,
finer detail, less grain and more natural illustration, preserving the original
artist's drawing style across future images. Coarse NES pixel blocks and a tiny
palette are superseded for these illustrations.

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
