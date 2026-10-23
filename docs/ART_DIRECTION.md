# Art direction: remake and remaster

## Accepted direction — 2026-10-22

Edgard in Kimeria is a **remake and remaster** of the 1994 DOS game. The author wants
a more beautiful version: richer color, finer detail, less visible grain and more
natural drawing, while preserving the original artist's style throughout the game.
This applies to future illustrations as well as the current dialogue portraits.
It does **not** prescribe the world sprite style: see the author's later clarification below.
See [D21](DECISIONS.md#d21-remake-and-remaster-preserving-the-original-artists-style).

The supplied [DOS screenshots](ORIGINAL_DOS.md) establish the artist's character
identities and drawing language. Their hardware limits are historical context.
Coarse enlarged pixels, a tiny palette and the green screenshot background are not
requirements for new dialogue/illustration artwork. The current portrait production baseline is a 256-color
palette with fine, smoothly shaded forms and clean transparent contours.

The author reviewed the new portraits beside the DOS originals and actual game frames,
then accepted the remastered version as the style reference for future illustrations
on 2026-10-22 ("Принимаю как образец стиля"). The selected masters in `art/portraits/`
are that approved baseline; see [artwork review](PORTRAITS.md#author-review).

## World sprites and HUD icons — author clarification, 2026-10-22

The author rejected detailed 256-color item art in gameplay. The original Items.png
has 6 colors for the first coin frame, 9 for the cooked chicken and 4 for the key,
including transparency; its frames are 16×16 with small occupied silhouettes.
Player.png uses 48×48 frames with a much smaller occupied figure (12–17 colors in
the inspected running/idle frames). Frame canvas, occupied pixels and a complete
multi-frame sheet are different measurements.

- **Gameplay:** preserve tiny native pixel art, low color counts, black contours,
  binary transparency, no antialiasing/dithering and nearest filtering. New torch:
  8×16; stone: 8×8; fireball and lever poses: 16×16; each at most 16 palette entries including alpha.
  Their render sizes match those native dimensions. Original collectable sprites
  remain original; do not replace them with detailed UI assets.
- **HUD:** separate 128×128 indexed PNGs, up to 256 entries including transparency,
  detailed clean illustration and linear filtering. Preserve the same identity:
  roasted drumstick on its plate, silver key, gold coin and fireball. These appear
  in the small existing HUD slots, not as 128-pixel gameplay objects.
- **Portraits:** the accepted 256×256 illustrated remaster remains in force.

The new pixel sources use the actual original Items.png as style reference.
Portrait masters are not a gameplay style reference. Source assets and exact prompts
live under `art/objects/` and `art/hud/`; the repeatable exporter only crops, resizes
and quantizes authored art. The source guard/hook also checks dimensions, indexed
palette and binary world alpha in the new world/HUD PNG exports, without a Pillow
build dependency. Renderer checks still have to verify filtering and visual fit.

## Group artwork by meaning

Store related states or animation frames of the same object in one registered
sprite sheet. The lever uses `assets/images/objects/lever.png`, 32×16, with two
16×16 frames: up/on at x=0 and down/off at x=16. Its selected source sheet is
`art/objects/lever-pixel.png`, with the same left-to-right order. Keep a shared
palette, mounting plate and pivot; use texture regions instead of separate textures.
Unrelated single-frame world objects keep their own assets. HUD illustrations use
separate textures and filtering from world sprites; their selected detailed masters,
including the fireball, live under `art/hud/`.

Retain selected sources and exact prompts needed to reproduce current runtime art.
Remove rejected iterations, unused masters, redundant previews and screenshots of
superseded behavior from the change. Keep only representative current validation
captures, with valid documentation links. Git history already records past revisions.

## Preserve the artist's identity

[D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket) fixes one
carryable slot, with every held object visible in the animated front hand and drawn
in front of the hero. This includes stones; the earlier hidden-stone treatment was
replaced on 2026-10-23 with foreground rendering of the existing 8×8 sprite. Keep native sprite size and
attach to the measured palm rather than placing the object behind the body. A HUD
representation, if added, follows the separate detailed-icon contract above.

The latest author revision (2026-10-23) selects a **three-quarter (3/4) lever**,
superseding the lever's earlier en-face requirement. Show its broad working face
and visible side/top thickness so the raised/lowered handle is easy to read.
Keep the base, pivot and viewing angle aligned across poses; do not return to a
thin profile. Other interactive items retain the frontal default unless revised
separately. The hero's side-view animation does not dictate every object's angle.
This keeps the native world pixel-art contract and separate character/HUD styles.

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

## Gameplay sprites and procedural-art prohibition (2026-10-22)

The author explicitly requires sprites for world artwork, including prototypes.
A torch's handle/head, a stone or a projectile must load an authored image; a
`renderHolder` made from coloured quads or a tinted particle dot is not acceptable
art. Do not paint substitutes in Java, ShapeRenderer, Pixmap, PIL, Canvas or SVG.
Reuse existing suitable artwork or generate real raster assets, preserve masters
and prompts, and export them repeatably. Cropping/resampling/quantizing an authored
image is asset preparation; it does not invent the drawing. Current examples:
[object sprite sources and exact prompts](../art/objects/generation-prompts.md).

UI focus frames, collider/debug measurements, blank GPU storage and time-varying
particle/shader VFX carry different information from a physical object's artwork.
For example, a torch uses a sprite for its body plus simulated flame/smoke particles.
These are principled categories, not exceptions for an inconvenient small object.
A static glowing dot is not made into a stone by calling it an effect.

`tools/check_sprite_art.py` checks source after edits and before completion through
[the project hook](../.codex/hooks.json), and is also a mandatory release/check
dependency (`validateSpriteArt`). Permissions name an exact method AND primitive
kind, with a reason. New/renamed object helpers, procedural substitutes in different
Java packages, ImageDraw/pixel painting in Python export tools and Java2D shape
painting in Java export tools are rejected. This is a source guardrail against
known primitive paths, not a semantic proof about arbitrary programs. Skills and
art review remain required; do not weaken the scanner to pass an object placeholder.

The hooks are synchronous PostToolUse and Stop checks of real files; shell-written
source is covered as well as patches. They do not undo a completed edit.
[Codex requires review/trust of a new hook definition](https://learn.chatgpt.com/docs/hooks);
use `/hooks` to review the project hook before expecting automatic agent enforcement.
The Gradle release gate works independently of that trust step. Hook payloads were
executed locally and guard regression tests cover both primitive-art rejection and world/HUD PNG contracts.

The standard skill-creator validator could not start because PyYAML is absent from
both installed Python runtimes. Both skills' YAML/frontmatter were instead parsed
with Ruby's YAML.safe_load; required fields/names/description limits and relative
references were checked. No successful standard-validator run is claimed. To run
that specific tool in an isolated environment with its dependency available:

```sh
uv run --with pyyaml python /Users/amerezhanyi/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/kimeria-art-direction
uv run --with pyyaml python /Users/amerezhanyi/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/kimeria-rendering
```
