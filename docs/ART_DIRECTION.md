# Art direction: remake and remaster

## Accepted direction — 2026-10-22

Edgard in Kimeria is a **remake and remaster** of the 1994 DOS game. The author wants
a more beautiful version: richer color, finer detail, less visible grain and more
natural drawing, while preserving the original artist's style throughout the game.
This applies to future illustrations as well as the current dialogue portraits.
See [D21](DECISIONS.md#d21-remake-and-remaster-preserving-the-original-artists-style).

The supplied [DOS screenshots](ORIGINAL_DOS.md) establish the artist's character
identities and drawing language. Their hardware limits are historical context.
Coarse enlarged pixels, a tiny palette and the green screenshot background are not
requirements for new artwork. The current portrait production baseline is a 256-color
palette with fine, smoothly shaded forms and clean transparent contours.

The author reviewed the new portraits beside the DOS originals and actual game frames,
then accepted the remastered version as the style reference for future illustrations
on 2026-10-22 ("Принимаю как образец стиля"). The selected masters in `art/portraits/`
are that approved baseline; see [artwork review](PORTRAITS.md#author-review).

## Preserve the artist's identity

Use the original screenshots together with the current remaster masters in
`art/portraits/` as visual references for every related new image. Inspect the actual
images before composing a prompt. Text descriptions alone cannot keep the style stable.

- **Drawing:** retain the original faces' proportions, strong brows, angular features,
  recognizable hair/beard silhouettes and direct, characterful expressions. Refine
  anatomy and form gently while preserving the artist's design choices.
- **Color:** preserve the warm ochre/brown family, deep black hair and shadows, and clear
  red accents. Add intermediate skin, cloth and hair tones for volume and readable
  separation on a dark background. Use coherent shading and restrained highlights.
- **Finish:** natural, hand-drawn fantasy illustration with smooth contours and broad
  shading. Avoid coarse pixel blocks, stippling, noisy texture and error-diffusion
  dithering. Photographic skin, 3D materials, anime proportions or a generic fantasy
  character redesign would change this direction.
- **Edgard:** long black hair; narrow red forehead band; stern, elongated, angular,
  clean-shaven face with strong dark brows; warm tan/ochre skin; visible shoulders.
- **Healer:** visibly old; bald crown and sparse side hair; very long ochre beard and
  moustache; warm brown/ochre clothing; upright staff with a distinctive red hooked top.
  Preserve the age, beard and staff silhouette together.

These are reference-derived constraints. Unseen characters, environments and new poses
still need design work; do not invent the original artist's undocumented intent.
Maintain the same handling of faces, contours, light and color across the new work.

## Production and continuity

Current portraits have a **256 × 256 px** runtime canvas, at most **256 palette entries
including transparency**, and are displayed within the existing 64 × 64 logical-pixel
slot. Keep higher-resolution transparent masters under `art/portraits/` so future work
has a detailed style reference rather than an enlarged thumbnail. Runtime exports use
smooth resizing, palette quantization without dithering, and linear texture filtering.
Other game textures keep their existing rendering policy. See
[portrait replacement and validation](PORTRAITS.md) for paths and export instructions.

When making another image, reference both its original identity source (when available)
and an existing remastered master for style. Keep related characters at comparable
levels of detail, contrast, lighting and naturalness. Reuse the existing palette family;
a 256-entry budget does not mean introducing arbitrary unrelated hues.

Record source references, exact prompts, selected masters, production dimensions and
palette/filter settings. Compare the final export at actual dialogue size on its dark
frame, in addition to viewing the master. Record author review for each artwork revision
separately from renderer checks. Earlier approval of the coarse portrait version does
not automatically approve its remaster or every future image.
