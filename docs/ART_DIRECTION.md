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
has 6 colors for the first coin frame, 9 for the cooked chicken and 4 for the key (the retired Chicken and Key items, replaced by Bat and Mushroom in D43),
including transparency; its frames are 16×16 with small occupied silhouettes.
Player.png uses 48×48 frames with a much smaller occupied figure (12–17 colors in
the inspected running/idle frames). Frame canvas, occupied pixels and a complete
multi-frame sheet are different measurements.

- **Gameplay:** preserve tiny native pixel art, low color counts, black contours,
  binary transparency, no antialiasing/dithering and nearest filtering. New torch:
  8×16; stone: 8×8; fireball and lever poses: 16×16; static platform: 32×16; each at most 16 palette entries including alpha.
  Their render sizes match those native dimensions. Original collectable sprites
  remain original; do not replace them with detailed UI assets. Static platforms use
  `assets/images/objects/platform.png`, repeated across wider collision spans; the
  selected source is `art/objects/platform-pixel.png`.
  The healer ingredients (D43) are original redraws, never copies of the DOS art: Bat and
  Mushroom, each a native 32×16 strip of two 16×16 frames (`art/objects/{bat,mushroom}-pixel.png`
  → `assets/images/objects/`), at most 16 palette entries with alpha, black-navy contour,
  binary transparency. The coin alone stays in `Items.png`.
- **HUD:** separate 128×128 indexed PNGs, up to 256 entries including transparency,
  detailed clean illustration and linear filtering. Preserve the same identity:
  bat, mushroom (D43), gold coin and fireball. These appear
  in the small existing HUD slots, not as 128-pixel gameplay objects.
- **Fireball capacity HUD (#98, 2026-10-25):** reuse the illustrated icon in three
  fixed 32×32 slots, 4 px apart, no number; spent slots use 25% RGB brightness.
  The stationary world refill reuses the nearest-filtered 16×16 projectile art.
  [Current browser capture](references/hud-fireball-capacity-debug.png).
- **Portraits:** the accepted 256×256 illustrated remaster remains in force.

The new pixel sources use the actual original Items.png as style reference.
Portrait masters are not a gameplay style reference. Source assets and exact prompts
live under `art/objects/` and `art/hud/`; the repeatable exporter only crops, resizes
and quantizes authored art. The source guard/hook also checks dimensions, indexed
palette and binary world alpha in the new world/HUD PNG exports, without a Pillow
build dependency. Renderer checks still have to verify filtering and visual fit.

The standing look poses use the first 48×48 idle frame from `Player.png`, changing
only its head. `images/hero/Look.png` packs up/down on one 96×48 sheet. The body,
sword, palm and boots remain pixel-identical, with the original character's 16
opaque colors plus transparency. This is a registered legacy-character frame,
not a new 48-pixel-wide item. See [selected sources and exact prompts](../art/hero/generation-prompts.md).

## Group artwork by meaning

Store related states or animation frames of the same object in one registered
sprite sheet. The lever uses `assets/images/objects/lever.png`, 32×16, with two
16×16 frames: up/on at x=0 and down/off at x=16. Its selected source sheet is
`art/objects/lever-pixel.png`, with the same left-to-right order. Keep a shared
palette, mounting plate and pivot; use texture regions instead of separate textures.
Unrelated single-frame world objects keep their own assets. HUD illustrations use
separate textures and filtering from world sprites; their selected detailed masters,
including the fireball, live under `art/hud/`.

The moving brick wall uses `assets/images/objects/wall-brick.png` as one 48×96
world-sprite frame. Its selected generated source is `art/objects/wall-brick-master.png`;
the repeatable exporter trims transparent source margins, preserves the silhouette with
nearest resizing, and anchors the flat center of the base to the frame's lower edge. The
lower corners are rounded with crisp pixel steps, avoiding a cut-off rectangular base.
The sprite has a distinct top course and visible masonry edges on both sides, matching the
forest's modular block style. The runtime PNG is indexed with at most 16 palette entries,
uses binary transparency outside the masonry, and is sampled with nearest filtering. This
art follows the vertical collision object during motion.

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

## Parallax (author request, 2026-11-05, [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236))

A request and idea, not an implemented rule.

- **Layers:** a near, blurred **foreground** layer, the **main** layer where the hero acts, and
  one or more **background** layers behind it.
- **Readability rule (proposed):** the foreground layer must **never hide** traps, hints or the
  hero; it frames the scene at the edges or is faint enough to see through.
- **Performance:** desktop and web (including mobile browsers) are targets, so the number of layers, blur cost and overdraw
  need a measured budget before the layers are fixed.
- **Per-stage art:** each stage has its own set of layers.

## Environment effects (author checklist, 2026-11-05, [#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239))

A checklist of ideas, not implemented rules:

- vines sway and shake when a stone falls and the ground shakes;
- a bush shakes when the player walks past;
- a dust puff under the player on a jump;
- the screen shakes, and dust may fall from the ceiling, when a lever lowers a wall.

**Accessibility note:** screen shake must be **optional or reducible** (an Assist-style
setting, D11), and no effect may hide a trap warning.

### Environment effects plan (#239)

Plan approved by the author (revised after the review). **Status, 2026-11-12:** steps 1 to 5 of section 7 are built (the shake manager and
its option, jump dust, ceiling grit, the `Decor` bush, the stone-linked `HangingVine`); the sections that describe them (2 to 5, 6) are
plan text unless marked "built" or "as built". Author
constraints: anything that is a *thing* (a vine, a bush, a flower) is a pre-drawn sprite or
sprite-sheet animation through the art pipeline (nearest filter, at most 16 palette entries, binary
alpha). Dust and grit are **particles**, the same category as the torch's flame, sparks, embers and
smoke (the "time-varying particle VFX" category in the procedural-art section below): they carry no
artwork of an object. Movement-only effects (an offset or rotation of an existing region, a camera
offset) draw nothing new. Every effect is cosmetic: no collision, hitbox, damage, timing or route
depends on it.

#### 1. The particle code on `main` (read from the code after #285 steps 0 to 5)

- `effects.KimeriaParticleSystem` is the shared system: fixed struct-of-arrays storage allocated in the constructor
  (`spawn` writes the next free slot; a full pool drops the oldest), one seeded `RandomXS128` per system
  (`random()`), `update(dt)` (straight-line lerp from start to end over the particle's life, dead ones
  removed, spawn order kept), `clear()`, and `render(batch, region)` (normal-blend particles first, then
  additive ones, white and normal blending restored). Three kinds: `KIND_GLOW` (additive, shrinks to 0.6,
  alpha `(1-t)^2`), `KIND_SMOKE` (normal blend, grows to 2×, alpha `1-t`) and `KIND_GRIT` (normal blend, constant size,
  alpha `1-t`), all scaled by a fixed `PEAK_ALPHA` of 0.7.
- `Torch` owns one `KimeriaParticleSystem` of **96 slots** (`PARTICLE_CAPACITY`) for its flame core, embers,
  sparkles and smoke, drawn with `SoftDot.region()`. Its sprite body is `SpriteSheets.TORCH`; its glow is
  one more additive `SoftDot` quad, not a translucent sprite.
- `effects.KimeriaBloodSystem` is owned by `Level` (`Level.blood(...)` sprays, `bloodParticles()` for tests), with its own
  arrays because a drop is ballistic (velocity, gravity, a floor), not a lerp. It is updated and rendered by
  `Level` and cleared with `blood.clear()` on the room rebuild.
- `SoftDot.region()`, `SoftDot.glowRegion()` and `Shaders.whiteQuad()` return cached regions. `Level.cameraShakeOffset`
  returns one reused `Vector2` field (and the doc comment says it must not be kept or mutated).
- Guards: `AllocationBudgetTest` (runtime ceilings in bytes per frame, scenarios include one torch, one blood
  spray, a camera-shake query and the forest rooms) and `tools/check_allocations.py` (a changed-line scan of
  `update*`, `render*`, `draw*` and listed helpers). Both are described in `docs/DEVELOPMENT.md`, "Particles and the allocation guard".
- Life cycle, verified in the code and still true: pause works because `Level.update` is not called; a room
  rebuild clears `objects` and `blood` and calls `clearCameraEffects()`; `Torch.toggleFire(false)` and
  `restoreState` clear the torch's particles.
- The dust and grit of #239 are two more `KimeriaParticleSystem` instances on this base (**built in step 5 of
  [#285](https://github.com/jlogicgames/edgard_in_kimeria/issues/285)**): `effects.EnvironmentFx` owns them (dust 24 slots,
  grit 32 slots) with the `ScreenShake` manager; `Level.fx()` exposes it. Section 3 records the design and how it was built.

#### 2. The four items

| Item | Today in the code | Hook | Effect kind | New art | Web cost |
| --- | --- | --- | --- | --- | --- |
| **Vines sway; shake with a falling stone** | `ThornVine` (D46) already swings as a lethal pendulum (`angle()` feeds `lethalAt`, forest-2 only). No scenery vine exists. `FallingStone` has `landed()` (a protected no-op in `FallingTrap`). | New Tiled type `HangingVine` (non-lethal scenery, `Level.spawn` switch next to `ThornVine`). `FallingStone` gets `onImpact(Runnable)` set by `Level.spawnFallingTrap`, which calls `fx.groundShake(x, strength)`; each `HangingVine` within reach adds a decaying sway. The lethal `ThornVine` is **not** shaken (its drawn pose must equal its hitbox). | Movement only: rotation about the top centre, as `ThornVine.render` does. | `decor-vine` 8×40, 1 frame, vine `8a8a2a / 4a4a18`, leaves `3b2a5c / 7a5ab0`, contour `0b0814`. Not `trap-vine` (its thorn ball is trap art). | 1 draw per vine, no allocation. |
| **Bush (decor) reacts when the player walks past** | No decor object exists. Bushes are part of the `Background` tile layer (`Level.renderBackground`), which stays as it is. | New Tiled type `Decor` (section 4): an animated sprite placed by hand, plays a brush-past animation when the hero walks past. | Sprite animation. | The decor sheets in section 4. | 1 draw per decor object, no pool needed. |
| **Dust under the player on a jump** (**built**, step 2) | `Player.playerJump(dt)` is the single jump entry (ground jump, ladder hop and wall jump branches). | After the ordinary ground branch only: `fx.dustPuff(feetX, feetY)` with `feetY = position.y + hitbox.offsetY + hitbox.height`. Y grows downward, so particles start at `feetY` and rise (negative vy). | **Particles**, `Torch` model. | **None.** | Section 3. |
| **Lever lowers a wall: screen shake, optional ceiling dust** (**built**, steps 1 and 3) | `Level.activateTrigger` calls `performAction()` on each linked `Actionable`; `Wall` moves over `moveDurationSeconds` (private, no getter). `Level.shakeRemaining` and `cameraShakeOffset` already exist (boss feedback). | In `activateTrigger`, if an activated `Actionable` is a `Wall` with `movesVertically()`, call `fx.shake(...)` for the wall's move duration (add a `Wall` getter) and `fx.ceilingGrit(wallCenterX, roomTop)`. | Shake is a camera offset. Grit is **particles**. | **None.** | Section 3. |

#### 3. Dust puff and ceiling grit as particles

- **Pools.** `EnvironmentFx` owns two `KimeriaParticleSystem` instances created once with the `Level`:
  **dust 24** slots (a jump emits 6, so 4 overlapping jumps fit; a full pool drops the oldest) and
  **grit 32** (a lever emits 24 over about 0.6 s). Each has its **own `RandomXS128`** (the system's
  `random()`), seeded from the level's seed pattern, so a seed reproduces a run and the two streams do not
  disturb each other or the torches.
- **Dust puff fits `KIND_SMOKE`.** 6 `spawn` calls from the feet: start X ±8 px around the hero centre,
  start Y `feetY`, end Y `feetY - (10 to 22 px/s * life)`, end X start X `± 25 px/s * life` away from the
  centre, life 0.25 to 0.4 s, base size 3 px, colours from `5b5870`, `9a97ad`, `d8d6e8`
  (0.357/0.345/0.439, 0.604/0.592/0.678, 0.847/0.839/0.910). Checked against the real curves in
  `KimeriaParticleSystem.drawSize`/`alpha`: **matches** the size (base 3 px grows linearly to 6 px at the end of life,
  `1 + SMOKE_GROWTH * t` with `SMOKE_GROWTH = 1`), the straight-line motion (a constant-speed rise, no
  gravity) and the fade (linear `1 - t`); normal blending. **Differs** from my earlier text in one way: alpha
  starts at the fixed `PEAK_ALPHA` of 0.7, not at 1, and a particle cannot override it, so the puff is a little
  translucent from the first frame. Drawn with the cached `SoftDot.region()`. No new kind is needed.
- **Ceiling grit did not fit either kind (built as `KIND_GRIT`).** `KIND_GLOW` is additive and shrinks; `KIND_SMOKE` grows and is drawn
  soft. Grit needs hard white-quad squares, normal blending, constant size, falling, linear alpha. This needed
  a **third kind `KIND_GRIT`**, added to `KimeriaParticleSystem`: normal blend (it joins the `KIND_SMOKE` pass in
  `render`), `drawSize` = base size (no scaling), `alpha` = `(1 - t) * PEAK_ALPHA`. It was a small change to the shared
  class (three branches in `drawSize`, `alpha` and `draw`), made in the grit step with its own unit
  test (constant size, linear alpha, drawn in the normal pass) and the golden/allocation tests kept
  green. The grit system is drawn with `Shaders.whiteQuad()` as the region: 24 particles from the wall's
  width ±24 px at the room's top edge, falling 70 to 110 px/s for up to 64 px (end Y = start Y + speed * life),
  life 0.5 to 0.9 s, size 1 to 2 px, colours `5b5870` and `9a97ad`. The shared 0.7 peak alpha also applies to grit; the
  plan accepts it.
- **As built (#285 step 5).** The grit burst is emitted evenly over 0.6 s by `EnvironmentFx.update` (first particle at
  once; a new lever pull replaces a running burst); its X spread is the wall's width plus 24 px on each side. The doc's
  "falling up to 64 px" and "end Y = start Y + speed * life" disagree at the top of the ranges (110 px/s * 0.9 s = 99 px),
  so the fall is `min(speed * life, 64)`, which keeps the 64 px bound. Measured peaks: 24 live grit (capacity 32), 12 live
  dust for a jump every quarter second (capacity 24). Dust and grit ignore the Screen shake option, as section 5 says; the
  grit burst and both sounds start with the lever shake in `Level.perform`, so a pause cannot replay them.
- **Which shared texture is needed.** Only the two that exist: `SoftDot` (dust) and the 1×1 white quad
  (grit). No new image, no `fx-dust-puff`/`fx-grit` sheet, nothing in `tools/sprite_layouts.py`.
- **Life cycle.** Both systems advance only in `Level.update(dt)` (pause freezes them), render
  allocation-free through `KimeriaParticleSystem.render`, and `clear()` runs from `clearCameraEffects()`/the room
  rebuild (death, retry, Restart Level, room change).
- **Web cost.** Worst case 24 + 32 = 56 quad draws for about 1 s after a lever, at most 24 for 0.4 s after a
  jump, nothing while idle (`render` returns at once when the count is 0). Torches already draw more every
  frame. Both textures are already bound by torches, fireflies and rain; a frame that mixes these draws
  with sprites may flush the batch a few extra times. Measuring that belongs to the atlas work,
  [#284](https://github.com/jlogicgames/edgard_in_kimeria/issues/284); #239 adds no atlas.

#### 4. Active decor objects (`decor-bush` only)

- **Object.** Tiled type `Decor` in `SpawnPoints`, a rectangle placed by hand, with a string property
  `kind` (`bush`) and an optional boolean `mirror`. One `Decor` class draws one animated
  sprite: frame 0 at rest; when the hero's body box overlaps the rectangle widened by 6 px **and** the
  hero moves horizontally, it plays the brush-past frames once at 10 fps and returns to frame 0. It
  restarts only when finished, so it cannot flicker. The brush direction comes from the hero's heading by
  drawing the sheet mirrored (negative width), so no second set of frames is needed. Timing depends only
  on hero position and `dt`, never on a random value.
- **One kind in #239: `decor-bush`.** One horizontal strip `assets/images/objects/decor-bush.png`, source
  `art/objects/decor/bush.grid`, at most 16 palette entries with alpha:

| Sheet | Frame size | Frames | Palette notes |
| --- | --- | --- | --- |
| `decor-bush` | 32×24 | idle, brush1, brush2, settle | leaves `3b2a5c / 7a5ab0`, vine olive `8a8a2a / 4a4a18` highlights, contour `0b0814`. Distinct from the magenta thorn tuft. |

  `kind` accepts `bush` only; the class is written so that a new kind is one sheet and one table entry.
- **Later kinds.** The other kinds (shrub 16×16, flowers 16×16, fern 24×24 and a mushroom) are **not built
  in #239**. They belong to the redo of all default sprites and tiles, issue
  [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189) ("Replace third-party artwork with
  original assets"). Their palette notes carry over: shrub in the same leaf and olive family with a smaller
  silhouette; flowers with bone `efe6c4` petals and ember `ffd23a` centres on leaf-green stems, away from
  magenta (tuft) and lime (spores); fern in the leaf family with drooping fronds. A decor mushroom needs a
  look that cannot be read as the healer's Mushroom pickup (D43) or the puffball trap.
- **Draw order and independence.** `Level.render` draws decor right after `renderBackground` and
  before platforms, objects and the hero, so decor is always behind the hero and behind every trap, hint
  and pickup, and can never hide one. The class reads only its own rectangle and the hero box; it never
  reads the tile layer. The unchanged `Background` layer, and a later split of the backgrounds into
  parallax layers (#236), do not affect it: decor sits in the main layer between the background layers
  and the hero. A foreground parallax layer is owned by that task.
- **Collision and gameplay.** None: no collision block, no hitbox, not an `Actionable`, not saved or
  restored (the room rebuild recreates it at frame 0).
- **Cost.** One `batch.draw` per decor object, the frame `TextureRegion`s held in the object, nothing
  allocated in `render`, no pool needed. The sheet adds to the web download only by its size (one strip
  of 128×24 px).
- **Status: built (step 4).** `entity/objects/Decor.java`; `Level.decor` (a list apart from `objects`, cleared
  with the room, updated in `updateWorld`, drawn right after `renderBackground`); the sheet is exported from
  the hand-authored `art/objects/decor/bush.grid` by `tools/forest_trap_sprites.py decor:bush` (layout
  `DECOR_SHEETS` in `tools/sprite_layouts.py`, contract in `validateSpriteArt`). `kind` is checked at load and
  by `tools/check_tiled_links.py`, which also requires the bush's bottom edge (`y + height`) to equal the top of
  a solid block. The grid is an original drawing in the leaf/olive palette above (one extra mid purple
  `5a4488` between the two leaf tones); the author reviews it before merge. In a map the rectangle is 32×24,
  so `y = floor - 24` (forest-2's SpawnPoints layer is offset by -16, so its file `y` is `floor - 24 + 16`).
- **Placement (all levels, by hand, standing on the ground floor away from traps, pickups, checkpoints and exits):**

| Map | Bushes | World x (left edge) |
| --- | --- | --- |
| `forest-0` | 12 (two per room) | 200, 300, 700, 1140, 1560, 1860, 2340, 2620, 3140, 3420, 3860, 4230 |
| `forest-1` | 3 | 240, 600, 780 |
| `forest-2` | 7 | 400, 700, 1040, 1380, 1580, 1800, 2700 |

  `DecorTest` pins these counts and measures every bush against the floor line.

##### Hanging vine (#239 step 5), as built

- **Object.** Tiled type `HangingVine` in `SpawnPoints`, an 8×40 rectangle, no properties, drawn with the `decor-vine` frame
  (`assets/images/objects/decor-vine.png`, source `art/objects/decor/vine.grid`, preview
  `docs/references/forest-traps/decor-vine-preview.png`, layout `DECOR_SHEETS['vine']`). `entity/objects/HangingVine.java`, kept in
  `Level.vines` (cleared with the room, updated in `updateWorld`, drawn right after the decor, so behind platforms, objects and the hero).
  No collision, not an `Actionable`, not saved; the room rebuild recreates it at rest. World Y grows downward: `y` is the **top**
  (anchor) edge and must be the room's ceiling line (or the bottom of a solid block); the vine hangs to `y + 40`.
- **The stone link.** `FallingStone.onImpact(Runnable)` is called once from `landed()`; `Level.spawnFallingTrap` sets it to
  `Level.groundShake(x, strength)`, which calls `HangingVine.shake(x, strength)` on every vine of the room. The vine sways only if
  its anchor is within `HangingVine.REACH` = 160 px of the stone's centre; there is no explicit id property, the link is
  geometric and `tools/check_tiled_links.py` requires a `FallingStone` of the same room within that reach (read from the Java
  constant) so a vine that nothing can move fails the check. The branch (`FallingBranch`) does not shake vines. The doc's `fx.groundShake` is
  `Level.groundShake`: with one listener a broadcast manager would be an empty indirection.
- **Motion.** `angle = direction * A * exp(-t / 0.5 s) * sin(2 pi * 1.6 Hz * t)`, `A` = 14 degrees times the strength times
  `(1 - distance / REACH)`, started away from the stone, ending after 1.6 s; a stronger running swing is never cut by a weaker
  one. Deterministic, advances only in `update` (pause freezes it), rotation about the top centre as `ThornVine.render` does. No idle
  sway. `ThornVine` is not shaken.
- **Screen shake option.** With `SCREEN_SHAKE` off (`ScreenShake.enabled()`) the vines ignore the impact and stay still.
- **Placement (by hand, in rooms that have a falling stone: a vine nothing can move would be dead art):**

| Map | Vines | World x (left edge), y = room top (file y 16 in forest-2's SpawnPoints layer, offset -16) |
| --- | --- | --- |
| `forest-0` | 3 (stone room, stone at x 2544) | 2476, 2616, 2680 |
| `forest-1` | 0 (no falling stone in this map) | none |
| `forest-2` | 3 (canopy room, stone at x 2224) | 2146, 2286, 2346 |

  Later levels place a vine when they place a falling stone. `HangingVineTest` pins the counts and measures every vine against the ceiling line.
- **Cost.** One rotated `batch.draw` per vine; the sheet is 8×40 px. `AllocationBudgetTest.hangingVinesSwayingThroughTheRealLevel` measures the allocation.
- The grit leaves are small (2 px) on purpose: the vine is a thin background accent; the author reviews the sprite before merge.

#### 5. Shared design

- **One manager for events, per-object code for motion.** An `effects.EnvironmentFx` owned by `Level`
  (`level.fx()`): the screen-shake state, the two particle pools, and a broadcast of ground-shake
  events. `HangingVine` and `Decor` keep their own animation state, because it belongs to the object.
- **Camera shake model.** `ScreenShake.start(amplitudePx, seconds)` keeps the stronger of the running
  and the new shake. The offset is `amplitude * remaining/total * sin(...)`. The **existing boss shake
  migrates into `ScreenShake`**: `Level.shakeRemaining`, `shakeAfterBossAttack`, `shakeAfterEmpoweredHit`,
  `clearCameraEffects` and `cameraShakeOffset` keep their **public method names and signatures** and delegate to the manager,
  with **identical numbers** (1.25 px peak, 0.18 s, frequencies 110 and 137, the same room clamp), so
  `BossCombatTest` and `RoomTest` pass unchanged. `cameraShakeOffset` keeps returning **one reused
  instance** (the field added by #285 step 4). With the `SCREEN_SHAKE` option off the boss shake is
  also zero. The lever shake is the new case: about 1.5 px for the wall's move time (1 s default). The
  offset is deterministic and is added only to `camera.position` in `KimeriaGame`, as today; the clamp to the room
  bounds stays. Physics, hitboxes, trap timers and the Y-up text projection never read it. `KimeriaGame`
  will restore the unshaken camera position before drawing the HUD (the hint anchor is placed through
  `camera.project`), so the hint label does not move with the shake (D33/D49/D50 geometry stays pinned).
- **Reduced-motion option.** `Settings.SCREEN_SHAKE` (key `screen-shake`, boolean) under Options → Assist
  (D11, `assistMenu`), item "Screen shake: On/Off". **Default:** on for desktop; on the web build it follows
  the browser's reduced-motion preference (`prefers-reduced-motion: reduce` gives off, anything else on).
  **A saved choice always wins:** the default is read only while the key is absent, so a player who ever
  toggles the item keeps that choice whatever the browser says. When off, the manager returns a zero offset
  for **every** shake, including the existing boss feedback, and vines ignore shake events. New
  `Msg.SCREEN_SHAKE` in `Msg.java` and the five bundles `en`, `uk`, `be`, `de`, `pl`. The menu is the existing
  `Menu`, so keyboard, gamepad and touch reach it exactly as they reach "Longer trap warnings". Dust, grit and
  decor stay on: they are small and local and move no screen area.
- **Reading the browser preference (researched, section 5a).** See section 5a.
- **Purely cosmetic.** Effect state advances only in `Level.update(dt)` and is cleared with
  `clearCameraEffects()` on death, retry and room change. Nothing in gameplay reads effect state back.
- **Allocation rules.** New code must pass `tools/check_allocations.py` and the `AllocationBudgetTest`
  scenarios in section 6. (The old per-frame `Rectangle`/`Vector2` of `KimeriaGame` and
  `Level.cameraShakeOffset` were removed by #285 step 4; nothing here depends on that work.)

#### 5a. Reduced-motion default on the web (researched)

- **Can the web build read it? Yes, by the mechanism the project already uses.** `web/.../WebLauncher.java`
  already declares `@JSBody(script = "...") private static native` methods for browser facts it needs
  (`navigator.maxTouchPoints`, `devicePixelRatio`, the Fullscreen API, wake lock), compiled by TeaVM 0.15.0 with
  gdx-teavm 1.6.2 into the shipped release. The same form reads the preference:
  `@JSBody(script = "return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);") private static native boolean prefersReducedMotion();`.
- **Evidence.** (1) The existing `@JSBody` methods above. (2) I searched the `teavm-jso-apis-0.15.0` sources in
  the Gradle cache: `org.teavm.jso.browser` has `Window`, `Navigator`, `Screen`, `Location`, `History`,
  `Storage`, `Performance` and timers but **no** `matchMedia`/`MediaQueryList` binding, so there is no
  typed API to call and `@JSBody` is the route. (3) I did not open the gdx-teavm `backend-web` sources for a
  media-query helper; the plan does not rely on one.
- **How it reaches the game.** `WebLauncher` passes the boolean into `KimeriaGame` as a constructor argument next to
  `hasTouchScreen` (desktop passes `false`). `Settings` gains a way to read an option with a platform default
  while the key is absent, so `SCREEN_SHAKE` resolves as: saved value, else `!prefersReducedMotion`.
- **Not verified.** No browser was run: that `matchMedia` returns the OS setting in the supported browsers
  (including iOS Safari and Firefox) and that the compiled release keeps the call are unchecked; the step 1 pull request
  tests the default with a stubbed platform value and checks the real value once in a browser with the OS
  setting on and off.

#### 5b. Sound

- **Events that get a sound** (the author said yes): the **dust puff** on a ground jump, the **lever rumble**
  when a lever lowers a wall (for the wall's move time), and the **falling grit** after the lever.
- **How sounds work here (read from the code).** `KimeriaGame.playSound(name)` does nothing when sounds are
  off, else plays `assets/audio/<name>.wav` through `Assets.sound(path)`, which creates each `Sound` on first use and
  caches it, at the global `soundVolume`. The suffix `.wav` is fixed in `playSound`. Callers guard with
  `if (game.playSounds())` and use snake_case names (`jump`, `bounce`, `hit`, `collect`, `disappear`,
  `boss_hit`, `boss_restore`, `button_click`). Music (`main_menu.mp3`, `intro.wav`) is separate. There is
  no `docs/AUDIO*.md`, and `AGENTS.md` has no sound rule; the audio facts live in `docs/DEVELOPMENT.md`
  ("Assets": libGDX's WAV decoder needs plain PCM, so `button_click.wav` was re-encoded; `intro.wav` is an original
  synthesized placeholder) and the web release check lists the audio files it expects.
- **Existing sounds do not cover these events.** `jump` already plays on every jump, so a dust sound is a
  separate, quieter sound layered on it. The lever has no sound today: `Trigger`/`Lever` call no `playSound`.
  `bounce` is used for impacts (enemy, boss, bomb, falling platform) and would be the wrong rumble.
- **The audio files exist already (2026-11-12, PR 293).** `dust_puff.wav` (0.1 s, a quiet scuff), `wall_rumble.wav`
  (1.0 s, low, matching the wall's move time) and `grit_fall.wav` (0.5 s, dry patter) are synthesized by
  `tools/audio/generate_sounds.py`, accepted by the author on listening, and shipped in `assets/audio/` but played
  by nothing yet: steps 2 and 3 wire them in. The same change replaced the legacy `jump.wav` by an original synthesized
  jump, so the dust sound layers on the new jump. The approach, recipes and the way to make more sounds are in
  [SOUND_DIRECTION.md](SOUND_DIRECTION.md); the steps add no audio files. What each step still owes: the
  `game.playSounds()` guard and the sound-off behaviour (nothing plays when `playSounds()` is false). The origin of
  the other, older sounds (Flame and Rust versions) is #189's concern, not this plan's.
- **Hooks.** Dust: in `EnvironmentFx.dustPuff`'s caller in `Player.playerJump`, beside the existing `jump` call. Rumble
  and grit: in the `Level.activateTrigger` wall branch. All three sit behind `game.playSounds()` and play
  only when the effect starts, so pause (no `update`) cannot replay them.
- **Not verified.** Web playback latency of a sound that is created on its first use (`Assets.sound` is lazy), and
  that browsers play it after the start gesture; the existing `docs/PRE_RELEASE_TODO.md` already asks for a
  release-browser audio check.

#### 6. Tests (steps 1 to 3 are built: `ScreenShakeTest`, `EnvironmentFxTest`, the `AllocationBudgetTest` scenarios; the rest belong to steps 4 and 5)

- `ScreenShakeTest`: offset is zero with the option off; amplitude decays to zero at `seconds`; a
  stronger shake replaces a weaker one; deterministic across two runs; room clamp honoured.
- `EnvironmentFxTest`: a ground jump emits exactly one puff (6 particles); a ladder hop and a wall
  jump emit none; a full pool drops the oldest slot and `count()` never exceeds the capacity; the same
  seed and event sequence give identical particle positions; no `update` (pause) leaves positions
  unchanged; `clear()` (death, room rebuild) leaves no live particle; particles die by their lifetime.
- `KimeriaParticleSystemTest` addition (grit step): `KIND_GRIT` keeps its base size for the whole life, fades
  linearly to 0 and is drawn in the normal-blend pass; `KIND_GLOW` and `KIND_SMOKE` results are unchanged.
- **Allocation proof, not only array lengths.** New `AllocationBudgetTest` scenarios through the real
  `Level`: (a) a jump puff over its whole life, (b) a lever that lowers a wall: shake plus the grit burst over its
  whole life. Ceilings are a few tens of bytes per frame or less, like the torch and blood ceilings after #285,
  stated in the PR with the measured value. The new code also passes `tools/check_allocations.py`.
- `WallLessonTest` additions: a lever that lowers the wall starts one shake, one grit burst and one rumble request; a
  non-moving wall (`setActive(false)`) starts none. A jump requests one dust sound; with `playSounds()` false none is requested.
- `FallingTrapsTest` addition: the stone's impact notifies exactly once; a branch does not.
- `Decor` and vine tests: a hero walking past starts the brush animation once and it restarts only when
  finished; a standing hero or one outside the widened box starts nothing; a decor object has no
  collision block; vine sway never changes `ThornVine.lethalAt`/`everLethalIn`; an unknown `kind` fails
  the load (like the falling stone `skin`), and `tools/check_tiled_links.py` mirrors that check.
- `SettingsTest`: default on for desktop; with the platform value `reduced` the default is off; a saved value always wins in both cases; round trip; `LocalizationLayoutTest` and the catalog validation
  (new key present in all five bundles, label fits the 440 px Assist menu).
- Sprite contracts: `SpriteSheetContractsTest`, `tools/sprite_layouts.py` and `validateSpriteArt` gain
  the `decor-*` and `decor-vine` sheets; the exporter and its tests gain a table for them.
- Regression guard, no new test needed: `BossCombatTest` and `RoomTest` call
  `shakeAfterBossAttack`/`clearCameraEffects`/`cameraShakeOffset`, so keeping those method names
  (delegating to the manager) leaves them untouched. The mastered-route tests (`Forest1TrapsTest`,
  `Forest2TrapsTest`, `RhythmTrapsTest`, `TutorialLevelTest`) must pass unchanged, which is the proof
  that no effect touches physics.

#### 7. Order, cost and decisions

| Step | Content | Rough cost | Lands in |
| --- | --- | --- | --- |
| 1 | `EnvironmentFx` shell, `ScreenShake` (the boss shake migrates, same numbers and method names), the `SCREEN_SHAKE` option with its web default and strings, lever shake | medium | #239, independent of #285 |
| 2 | Jump dust: the dust `KimeriaParticleSystem`, the `Player.playerJump` hook and the dust sound | small (no art; the sound already exists). **Done (#285 step 5).** | #285 step 5 |
| 3 | Ceiling grit: `KIND_GRIT` in `KimeriaParticleSystem`, the grit system, the lever hook, the rumble and grit sounds, the `kimeria-particles` skill update | small (no art; the sounds already exist). **Done (#285 step 5).** | #285 step 5 |
| 4 | The one-kind `Decor` object and `decor-bush` only, with placement in all levels | medium (art plus map editing). **Done (#239 step 4).** | #239 |
| 5 | Stone-linked `HangingVine` and `decor-vine`, placement in all levels with a falling stone | medium. **Done (#239 step 5).** | #239 |

**Who lands what.** #285 step 5 is the dust and grit particle work (plan steps 2 and 3), built on the
`KimeriaParticleSystem` that #285 steps 2 to 4 already put on `main`; it adds `KIND_GRIT`. #239 owns the rest.
#239 step 1 (`ScreenShake`, the option and the strings) does not use particles, so it is independent of #285
and can land first or in parallel; steps 2 and 3 need only `EnvironmentFx` to exist (a minimal holder created in
whichever of step 1 or #285 step 5 lands first).

Each step is its own pull request; its sprites are reviewed by the author before the code that draws them.

#### Decisions (2026-11-12)

The author approved the plan with these answers:

- **Order.** Step 1 first (`EnvironmentFx` shell, `ScreenShake`, the `SCREEN_SHAKE` option and strings, the
  boss-shake migration, the lever shake); then the rest in the table's order.
- **Placement.** Decor objects and vines go in **all levels** (forest-0, forest-1, forest-2 and later levels),
  placed by hand in Tiled.
- **Motion.** Reaction only: the hero walking past, a shake, a falling stone and similar events. There is **no idle
  sway** in #239. A slow idle sway for forest trees is deferred to the parallax work,
  [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236), which splits the backgrounds into layers
  (trees are background art).
- **Decor kinds.** One kind for now, `decor-bush`. The other kinds (shrub, flowers, fern, mushroom) go to
  [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189) (section 4, "Later kinds").
- **`SCREEN_SHAKE` default.** Desktop on. Web: follows `prefers-reduced-motion: reduce` (off), else on. A saved
  choice always wins (sections 5 and 5a).
- **Sound.** Yes for the dust, the lever rumble and the falling grit (section 5b). The files were generated and
  accepted (PR 293); steps 2 and 3 only wire them in.
- **Particles.** Dust and grit are particles on `KimeriaParticleSystem` (not sprite strips); the atlas is out
  of scope ([#284](https://github.com/jlogicgames/edgard_in_kimeria/issues/284)).

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

## Forest trap sprites (#230)

First-pass native sprite sets for the D42 forest traps, hand-drawn for this remake (the DOS
screenshots were consulted only for silhouettes and readability, nothing was copied).
Nothing draws them yet: the code tickets #231-#235 load them through the inert
`SpriteSheets.TRAP_*` constants. Each set is one horizontal strip,
`assets/images/objects/trap-<name>.png`, native size, indexed with at most 16 palette entries
including binary alpha (guard contract in `tools/sprite_layouts.py` `FOREST_TRAP_SHEETS`, checked
by `validateSpriteArt`). Frame order is the order in the table.

| Sheet | Frame size | Frames (in order) | Notes |
| --- | --- | --- | --- |
| `trap-stump` | 16×16 | idle, windup1, windup2, windup3, spent | exploding stump: cracks, smoke, glow; charred remains |
| `trap-bomb` | 16×16 | idle1, idle2 | round iron bomb with a bone fuse and a smouldering tip (dull red / orange, no flame: the lit state is the D7 warm tint flash drawn over it); the blast is the `BombExplosionEffect` shader, not a sprite |
| `trap-root` | 16×24 | idle, windup1, windup2, windup3, spent | exploding root, same wind-up language |
| `trap-blast` | 32×32 | blast1..blast12 | 12 steps (#250): pale ball, orange ball with sparks, burst of puffs with a ground ring, fire mushroom under a dark-brown smoke cap, brown smoke saucer, ring, fade; the gameplay blast is 64×64 px, so draw at 2× (nearest) or keep the sprite as the visual core |
| `trap-thorn-root` | 16×24 | hidden, peek1, peek2, rising, up | bone-coloured stake; peek1/peek2 is the wobble |
| `trap-thorn-tuft` | 16×16 | calm, bristle, spiked | crimson-magenta stems with bone tips, deliberately not green like the bushes |
| `trap-branch` | 32×16 | resting, rustle, sag, falling | rustle = leaves shake, sag = drooping with a split at the trunk |
| `trap-stone` | 16×16 | stone, cracked | boulder and impact frame |
| `trap-shadow` | 24×8 | small, medium, large | near-black ground shadow (growing warning); the renderer may tint or scale |
| `trap-puffball` | 16×16 | idle, swell1, swell2, burst | spore puffball mushroom |
| `trap-spores` | 24×24 | cloud1, cloud2, cloud3 | lime spore cloud |
| `trap-vine` | 24×40 | hang, swing-left, swing-right | anchored top centre; pendulum by swing frames or rotation |
| `trap-log` | 32×16 | intact, crumble1, crumble2 | rotten log bridge, same footprint as `platform.png` |

**Bomb:** the stump and root sets replace the glass-bottle bomb in the tutorial (#233, D47: `ExplosiveStump`/`ExplosiveRoot` use these sheets, the blast frames are drawn at 2x). The `Bomb` trap type stays separate (D51) and has its own redrawn sprite, `trap-bomb` (#263, D60); the glass bottles in `images/Items.png` are no longer drawn (the file stays, the coin is in it).

**Palette (shared families, each sheet uses at most 15 colours + transparency):** near-black
contour `0b0814`; wood `4a2e1c / 8a5a30 / c08a4c`; ember `b3201a / f07a1a / ffd23a / fff4b0`;
smoke and stone `2a2638 / 34314a / 5b5870 / 9a97ad / d8d6e8`; bone `efe6c4 / a89c78`; thorn tuft
`c2307a / 6e1650`; spores `a6e04a / 5fa02a / e6f26a`; vine `8a8a2a / 4a4a18`; leaves `3b2a5c / 7a5ab0`.
Warm ember, bone and magenta were chosen to separate the traps from the dark night, purple
canopy and cyan trunks of the forest backdrop.

**Sources and tools:** the drawings are `art/objects/forest-traps/<name>.grid`, one character
per pixel, hand-authored (a text-format pixel file; frames, palette and an optional left-half
`mirror` / `flip` symmetry aid are described in `tools/forest_trap_sprites.py`).
`python3 tools/export_object_sprites.py` writes the 1:1 masters `art/objects/trap-<name>-pixel.png`,
the runtime sheets and contact sheets `docs/references/forest-traps/<name>-preview.png`
(x6 on night, canopy and trunk backgrounds plus a native-size row). `tools/test_forest_trap_sprites.py`
checks grid, layout and shipped PNG agree.

**Conflicts with the sprite guard / process flagged:**

- The guard's spirit says new world art comes from imagegen/source artwork and rejects PIL
  painting in `tools/`. No image generator was available, so the art was drawn as text pixel
  grids. `tools/forest_trap_sprites.py` only parses and converts them (no ImageDraw, no
  `putpixel`), so the scanner passes, but a reviewer may argue this is still hand-painted art
  by code-adjacent means. The scanner was not loosened.
- The blast is drawn at 32×32 native, not the 64×64 gameplay blast area. Every blast frame's lowest opaque row is the frame's last row, and `ExplosiveTrap.render` stands the 2× frame on the trap sprite's bottom (#255), so the blast sits on the stump's base and floor line instead of starting underground (the hit square stays centred on the object; `docs/references/forest-traps/blast-on-floor-preview.png` shows stump and floor).
- Vine swing frames: `swing-left` is hand-laid, `swing-right` is a mirror copy; the left/right
  stem offsets were laid out systematically rather than drawn freehand.

**Quality (after the review fixes):** the sprites are text pixel grids under
`art/objects/forest-traps/` drawn by hand and converted, not imagegen artwork. Final enough to
integrate and playtest: stump and thorn root (the stump's third wind-up frame is now bright
cracks and heavy smoke on an intact stump, so the blast frames carry the explosion; the root's
wind-up follows the same rule), thorn tuft, stone, puffball. Acceptable but rough, to be
redrawn by a human artist: root (reads as a spike), branch (contrast was raised with a lighter
wood and leaf palette, still thin on dark ground), blast (redrawn in #250 after the author disliked the first one: 12 frames, fire mushroom and brown smoke saucer in the spirit of an author-supplied reference, nothing copied; **the blast is 32×32
and needs 2× nearest scaling to cover the 64×64 gameplay blast area**), spore clouds (cloud3 is
now an irregular puff), shadow (a k/d checker standing in for translucency, readable on night,
canopy and trunk backgrounds but unproven on the real floor), vine (thin stem, small thorns,
mechanical swing) and log.

Run the sprite tests the way the project runs its tools tests:
`python3 tools/test_forest_trap_sprites.py` (like `python3 tools/test_sprite_art_guard.py`;
`python -m unittest tools.<name>` does not work for any tools test because they import sibling
modules).
