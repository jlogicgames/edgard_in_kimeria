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
The hero (D79) uses 64×64 frames with a figure at most 31 px tall (15 colors plus
transparency over the whole grid). Frame canvas, occupied pixels and a complete
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
  binary transparency. The coin is a native 32×16 strip too (`art/objects/coin-pixel.png` → `assets/images/objects/coin.png`, #350): the
  original coin's four colours and white rim, with the sun of the Kimeria arms on its face (see "Kingdom arms"). The Bat is drawn as an item, not a
  creature ([#345](https://github.com/jlogicgames/edgard_in_kimeria/issues/345), playtest F19: the flapping
  bat read as an enemy): a corked glass jar tied with twine, one dried bat wing inside; frame 2 moves the glint.
  A pickup must never share a silhouette with an enemy.
- **Pickup cue (#345):** every `Collectable`, and nothing else, bobs 2 px up and back every 1.2 s in whole pixels
  (only the drawing moves, the pickup box stays), glows and sparkles, so the cue says "pick me up". The glow is
  `art/objects/pickup/glow.grid`, a 24×24 warm-white halo drawn additively behind the item, alpha 0.5 at rest
  to 0.9 at the top of the bob, never gold or ember (those are reserved). **The coin has no halo:** its white rim
  washes out against added light (outline 0.123 against the D76 floor of 0.28 at forest-2 x 336), and the rim
  already does a halo's job; it keeps the bob and the sparkle (`Collectable.hasHalo`, `check_contrast.NO_HALO`). The sparkle is `pickup/sparkle.grid`, a 5×5 glint of three 0.1 s
  frames every 2.4 s, 3 px right of the item's centre and 2 px above it, started later per tile of x.
- **Pickup burst (#402, approved by the author 2026-11-18):** at the moment of pickup, the same for every `Collectable`, a burst of
  12 sparks (`effects.PickupSparksFx`, `KIND_FLAME`: opaque 1.5 to 3 px squares that shrink out, in the sparkle's white and
  `fff4aa`, 0.4 to 0.6 s, flying 10 to 26 px from the item's centre) and a faint quick ring behind them (the existing
  `shockwave.frag` through `RingPool`, 16 px radius, 2 px wide, 0.2 s, colour intensity 1.7, 1.3, 0.4: dim on purpose, the sparks are
  the main read). Drawn after the hero, because the item is at his hand when he takes it. It goes with the `collect` sound and the cue
  above; there is no screen ripple on a pickup (the lens hid the burst); `RippleEffect` is used only by the F3 debug hotkey and is reserved for picking up a tablet fragment, tracked by [#136](https://github.com/jlogicgames/edgard_in_kimeria/issues/136): that pickup plays only the ripple wave, without sparks or a ring. No sprite,
  shader edit, shake or flash.
- **HUD:** separate 128×128 indexed PNGs, up to 256 entries including transparency,
  detailed clean illustration and linear filtering. Preserve the same identity:
  Bat wing jar, mushroom (D43), the gold coin with the Kimeria sun (#350) and fireball. These appear
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

The look poses are frames of the hero sheets (D79): the idle body with the head turned up or down, drawn in
`art/hero/hero.grid` like every other hero frame.

## Group artwork by meaning

Store related states or animation frames of the same object in one registered
sprite sheet. The lever uses `assets/images/objects/lever.png`, 32×16, with two
16×16 frames: on (handle right) at x=0 and off (handle left) at x=16. Its drawing is the grid
`art/objects/lever.grid` (frames `on`, `off`, the same order). Keep a shared
palette, base and pivot; use texture regions instead of separate textures.
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

The lever is a **floor lever in three-quarter (3/4) view** (#413, 2026-11-17; the
3/4 view is the author's choice of 2026-10-23, the floor stance replaced the earlier
wall-mounted plate). A wooden block stands on the ground with its top face, front face
and right side visible; an iron handle comes out of a slot in its top and ends in the
red knob. The handle leans right for on and left for off. The base's bottom row rests
on the floor line, and the base, pivot and viewing angle are identical across poses;
do not return to a thin profile or to a wall mount. Other interactive items retain the frontal default unless revised
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

## The hero (D79, playtest F11 and F21, [#343](https://github.com/jlogicgames/edgard_in_kimeria/issues/343))

Edgard in the world is an original pixel drawing, `art/hero/hero.grid`, exported exactly by
`tools/hero_sprites.py` to three sheets of 64×64 cells, one clip per row (`tools/sprite_layouts.py HERO_SHEETS`):
`hero-right.png` and `hero-left.png` (idle 4, run 6, jump 2, fall 2, attack 7, look 2, clamber 1, hit 2, appear 4,
disappear 4; then carry idle 4, carry run 6, throw 3, land 1, pull 3) and `hero-ladder.png` (up 4, down 4, hold 1; a
back view used by both facings).

- **Identity:** long black hair, a narrow red headband, an angular clean-shaven face, warm ochre skin, bare
  shoulders; blue breeches, brown boots, a steel blade with a leather grip. No reserved accent hue (gold, ember,
  violet, lime, magenta). Ink contour `0b0814`, binary alpha, 15 colors plus transparency for all three sheets.
- **Registration:** feet on row 63, body axis on x 31.5 (the hitbox spans x 26..36, y 42..63). The game draws the
  frame at (x − 8, y − 16) from the unchanged 48×48 box: world Y grows down, so the frame's bottom row is the
  floor line. Standing and running poses stay within rows 33..63 (31 px: the lowest gap he walks under is 32 px);
  nothing in a pose the label shows over rises above row 24.
- **The sword stays in the right hand.** Facing right it is in front of the body; facing left it is behind him,
  held in the far hand, and the near left fist is empty. A left frame starts as the mirror of the right one
  (`frame l.x flip r.x`) and is then redrawn where the sword is; the game never flips the hero at runtime.
- **Ladder:** climbing up, climbing down (the head turned to look down over the shoulder) and holding still are
  different clips. Every ladder frame stands on its last row.
- **Attack:** the blade's reach ends at the sword box (17 px ahead of the body); the drawn swing never promises more
  reach than the hit has.
- **Contrast (D76):** active; idle in both facings measures 0.349 worst at the spawn points.
- **Gates:** `tools/test_hero_sprites.py` (the export is the grid, the palette, every layout frame drawn),
  `HeroFrameTest` (feet on the last row and on the floor line in play, the 31 px limit, the ladder clips),
  `check_sprite_art.py` (16-entry indexed PNGs, binary alpha), `check_contrast.py`.
- **Carry and throw:** the sword is sheathed on his back (the hilt over the far shoulder, the scabbard tip below it)
  and the object rides in the near fist (D23), so their left-facing frames are true mirrors. **Land** is a squat with
  the sword lowered in the right hand.
- **Thread pull ([#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341)):** tug, lift and land. The thread
  is tied to the **left** wrist in both facings: the far hand facing right, the near one facing left (drawn over the
  hair). The wrist pixel of each frame is recorded in `Player` and checked by `HeroFrameTest` to be the drawn forearm.

## Silver thread on death (D81, #341)

Every death with a visible cause shows the silver thread of #228 pulling Edgard back to the room start
([D81](DECISIONS.md#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16)).

- **Art:** `art/objects/silver-thread.grid` -> `assets/images/objects/silver-thread.png` (6 x 5, exported exactly by
  `tools/silver_thread_sprites.py`): the 1 x 2 strand, silver `d8dee9` over ink `0b0d18`, and the D78 glint star. The
  game stamps the strand one pixel step at a time along a chord with a parabolic sag between the wrist and the start
  (`effects.SilverThread`); only that curve is computed, the colours are authored (D81's argued exception to principle 6:
  a line between two moving points, not an object sprite).
- **Tier (D76):** active. Worst outline 0.335 over every RoomStart of the three maps, taut and slack, level and sloped,
  over each map's background (`tools/test_silver_thread_sprites.py`; 0.340 over the old sky, see D92); the D78 dialogue silvers
  measured 0.104 on the old sky.
- **Hero poses:** the three pull frames of #343 (row 14 of both facing sheets): tug, lift, land. The thread is tied to
  the **left** wrist in both facings, and the frames are drawn yanked backward by it, so the pull faces **away** from the
  start (a start on the left, a smaller X, means facing right). Wrists, frame pixels: tug and lift (17, 44) facing right
  and (46, 44) facing left, land (23, 52) and (24, 52); `Player.pullWrist` gives the pixel centre in world coordinates
  and the thread starts there (`SilverThreadTest` checks it lands on an opaque pixel of the drawn frame, both facings).
  Tug from 0.10 s, lift from 0.45 s, land under the fade-in (0.20 s) or after a pit (0.25 s).
- **Not verified:** the beat in the running game, by the author's eye (desktop and web):
  [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368).

## The fairy helper (D82, playtest F4, F14, [#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338))

The healer's fairy is an original pixel drawing in three sheets, from grids in `art/objects/fairy/`
(`tools/forest_trap_sprites.py fairy:body fairy:glow fairy:glint`; layouts in `tools/sprite_layouts.py FAIRY_SHEETS`).

- **Identity:** a tiny maid, not a winged ball of light. She has copper hair, a teal dress with a pale edge, a
  lantern skirt glowing pale aqua-white (the firefly), and pale blue dragonfly wings with blue veins. Ink contour
  `0b0814` (the hero's), 10 colours, binary alpha.
- **`fairy-body.png`, 16×16 frames:**
  - idle ×4, 0.12 s each, the wings beating;
  - fly ×2, 0.06 s each;
  - point ×3, 0.1 s each, a thin wand held out, its star lighting, the last frame held;
  - the three point frames flipped (grid `flip`, never at runtime);
  - appear ×2, 0.2 s each: the one-time twinkle after the start tale, looped for the twinkle at the screen edge toward an off-screen linked mechanism (D87).
  - Idle and fly are symmetric (grid `mirror`), so they need no facing.
- **`fairy-glow.png`, 24×24:** three stepped aqua rings (`123432`, `286a66`, `3c9c92`), drawn additively as a
  nine-slice with 8 px corners. It is the target glow (selected 1.0, passive 0.3) and, at 20×20 with alpha 0.6 to 0.9,
  her own light. Aqua is used by nothing else: the pickup halo is warm white (#345).
- **`fairy-glint.png`, 5×5 ×2:** an aqua-white star with an ink edge, big and small, at the selected target's corners.
  It is not the pickup sparkle (warm yellow, one per item, every 2.4 s).
- **Contrast (D76):** active. `check_contrast.py` sweeps every map, since she has no placement: her frames over her
  light at its dimmest, 0.314 to 0.440; the glints over the selected glow, 0.336 and 0.380. Without her light she
  would fail (0.187); `test_check_contrast.py` pins that.

## Production and continuity

Current portraits have a **256 × 256 px** runtime canvas, at most **256 palette entries
including transparency**, and are displayed at 158 × 158 logical pixels in the dialogue
screen's portrait windows ([D78](DECISIONS.md#d78-the-dialogue-is-a-codec-style-storybook-screen-playtest-f3f10-337-2026-11-16); 64 × 64 before #337). Keep higher-resolution transparent masters under `art/portraits/` so future work
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
- a dust puff under the player on a jump, and a smaller one when it lands;
- the screen shakes, and dust may fall from the ceiling, when a lever lowers a wall.

**Accessibility note:** screen shake must be **optional or reducible** (an Assist-style
setting, D11), and no effect may hide a trap warning.

**Fireflies (#472, approved by the author).** A Forest room's gameplay fireflies are small lime lights, not black dots:
`Firefly.smallGlow` in `b8f23a` (the menu backdrop's lime), a glow of 1.75 to 2.25 px radius (about 4 px across), alpha
0.65 at the peak with flights of 3 to 7 s, drawn additively (`Level.render` sets the blend around them). forest-2 has 24.
The menu backdrop keeps its larger `Firefly.glowing` (4 to 6 px). Lime is not a reserved hue: aqua is the healer's own
light, warm white is the pickup halo and fire orange the fireball and torches. A firefly is one small dot that drifts;
a lime spore cloud (`SporeCloud`, #296) is a lethal area, so the two must stay apart in size and motion. A firefly is
never a pickup, a trap cue or a warning. Allocation is pinned on the whole room, fireflies included:
`AllocationBudgetTest.forest2RoomWithFireflies`, 568 B/frame in isolation.

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
- **Landing puff (feel fix, D64).** `EnvironmentFx.landingPuff(feetX, feetY)` is the same dust (same colours, life, drift)
  with 4 puffs instead of 6. `Player.update` fires it when the hero goes from airborne to grounded in the vertical
  collision pass with a downward speed of at least `Player.LANDING_MIN_FALL_SPEED` = 140 px/s (gravity adds 9.8 per step,
  so about 14 frames of falling, a 16 px drop); not in quicksand. Walking off a ledge keeps the coyote flag until the
  speed passes 147, a wall slide divides the speed by 10, a ladder step-off bypasses the pass, so none of them puffs.
  The `dust_puff` sound plays with it at 0.6 of the sound volume (`GameContext.playSound(name, volumeScale)`).
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
  so the fall is `min(speed * life, 64)`, which keeps the 64 px bound. Measured peaks: 24 live grit (capacity 32 at the time), 12 live
  dust for a jump every quarter second (capacity 24 at the time; both pools grew in D73, #347: 48 and 32). Dust and grit ignore the Screen shake option, as section 5 says; the
  grit burst and both sounds start with the lever shake in `Level.perform`, so a pause cannot replay them.
- **Falling stone impact (#347, accepted by the author 2026-11-16).** Every `FallingStone` landing (all skins) calls
  `EnvironmentFx.stoneImpact(centerX, floorY, width)` from the `Level` impact callback: 10 dust puffs (`KIND_SMOKE`, 4 px,
  0.35 to 0.6 s) along the stone's width, 8 stone chips (`KIND_GRIT`, 1.5 to 2.5 px, 0.25 to 0.45 s) that fly out and drop to the
  floor, and 6 specks of debris that lie on the floor 2 to 3 s and fade (`KIND_GRIT`, linear alpha). The cracked stone frame
  stays. A 1.5 px, 0.15 s shake through `ScreenShake` (off with the Screen shake option, D62; dust and debris stay). Everything
  rests on the floor line (`y + h` of the stone; Y grows downward), pinned by `FallingTrapsTest`. Pool sizes grew (D73): dust 24 to
  32, grit 32 to 48 slots. The warning (the growing ground shadow) was checked in the capture and reads before the fall. Not
  verified: the look on the real forest tiles (#368; update this note when it is closed).
- **Which shared texture is needed.** Only the two that exist: `SoftDot` (dust) and the 1×1 white quad
  (grit). No new image, no `fx-dust-puff`/`fx-grit` sheet, nothing in `tools/sprite_layouts.py`.
- **Life cycle.** Both systems advance only in `Level.update(dt)` (pause freezes them), render
  allocation-free through `KimeriaParticleSystem.render`, and `clear()` runs from `clearCameraEffects()`/the room
  rebuild (death, retry, Restart Level, room change).
- **Web cost.** Worst case 24 + 32 = 56 quad draws for about 1 s after a lever, at most 24 for 0.4 s after a
  jump, nothing while idle (`render` returns at once when the count is 0). Torches already draw more every
  frame. Both textures are already bound by torches and fireflies; a frame that mixes these draws
  with sprites may flush the batch a few extra times. Measuring that belongs to the atlas work,
  [#284](https://github.com/jlogicgames/edgard_in_kimeria/issues/284); #239 adds no atlas.

#### 4. Active decor objects (`decor-bush` only)

- **Object.** Tiled type `Decor` in `SpawnPoints`, a rectangle placed by hand, with a string property
  `kind` (`bush`) and an optional boolean `mirror`. One `Decor` class draws one animated
  sprite: frame 0 at rest; when the hero's body box overlaps the bush's **visible body** (the rectangle
  inset by 3 px left, 2 px right and 6 px top, the opaque pixels of `decor-bush.png`; no widened zone, so
  it never moves before the hero touches it; the feel fix of 2026-11-13, D64) **and** the
  hero moves horizontally, it plays the brush-past frames once at 10 fps and returns to frame 0. It
  restarts only when finished, so it cannot flicker. The brush direction comes from the hero's heading by
  drawing the sheet mirrored (negative width), so no second set of frames is needed. Timing depends only
  on hero position and `dt`, never on a random value.
- **One kind in #239: `decor-bush`.** One horizontal strip `assets/images/objects/decor-bush.png`, source
  `art/objects/decor/bush.grid`, at most 16 palette entries with alpha:

| Sheet | Frame size | Frames | Palette notes |
| --- | --- | --- | --- |
| `decor-bush` | 32×24 | idle, brush1, brush2, settle | Decor tier (D76, 2026-11-16): muted grey-green leaves `597059 / 677d62 / 758b6a`, dull olive highlights `858358 / 676547`, contour `4d6151` (its own darker green, no ink). Was violet `3b2a5c / 7a5ab0` with ink `0b0814`, which read like the pickups (F15). |

  `kind` accepts `bush` here; the class is written so that a new kind is one sheet and one table entry (`lookalike` was added by #299, below).
- **Later kinds.** The other kinds (shrub 16×16, flowers 16×16, fern 24×24 and a mushroom) are **not built
  in #239**. They belong to the redo of all default sprites and tiles, issue
  [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189) ("Replace third-party artwork with
  original assets"). Their palette notes carry over: shrub in the same leaf and olive family with a smaller
  silhouette; flowers with bone `efe6c4` petals and ember `ffd23a` centres on leaf-green stems, away from
  magenta (tuft) and lime (spores); fern in the leaf family with drooping fronds. A decor mushroom needs a
  look that cannot be read as the healer's Mushroom pickup (D43) or the puffball trap (built as the static pale look-alike tuft, #299, below).
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
  a solid block. The grid is an original drawing in the palette above (three leaf tones); the 2026-11-16 recolour
  changed only its palette lines (D76). In a map the rectangle is 32×24,
  so `y = floor - 24` (forest-2's SpawnPoints layer is offset by -16, so its file `y` is `floor - 24 + 16`).
- **Crown (#421).** The decor kind `crown`, `decor-crown` 96×40, one frame, from the hand-authored `art/objects/decor/crown.grid`:
  a leaf crown hung from the room top over a `Decor:trunk` (trunk x = crown x + 40; the middle lobe hides the trunk's cap).
  The bush's grey-green family one step lighter (contour `607660`, leaves `6e8466 / 7b9170 / 889e79`, olive tips `948f62`);
  no ink, no violet. Measured over forest-0's `background` `4a92b8` (D92): outline 0.087, body 0.119. The bush's own palette
  on the same grid measures outline 0.157, inside the 0.20 decor ceiling by 0.043; the lighter greens keep the crown, the
  largest decor sprite and hung in open sky, about halfway under it. A hanging kind (`hangs` in `Decor.KINDS`): its top edge is on the
  room's top or a solid's underside, not its bottom edge on a solid. First placed in forest-0-wall over the falling branch.
- **Placement (all levels, by hand, standing on the ground floor away from traps, pickups, checkpoints and exits):**

| Map | Bushes | World x (left edge) |
| --- | --- | --- |
| `forest-0` | 12 (two per room) | 200, 300, 700, 1140, 1560, 1860, 2340, 2620, 3140, 3420, 3860, 4230 |
| `forest-1` | 3 | 240, 600, 780 |
| `forest-2` | 7 | 400, 700, 1040, 1380, 1580, 1800, 2700 |

  `DecorTest` pins these counts and measures every bush against the floor line. The brush trigger is the visible
  body (`Decor.BODY_INSET_LEFT/RIGHT/TOP`), pinned by `DecorTest`.

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
- **Colour (decor tier, D76, recoloured 2026-11-16).** Pale dull olive stem `9d9f7b / 8a8c6d`, grey-green leaves `88a083 / 738b73`,
  contour `757a67` (no ink), light enough to stay quiet in front of the clouds of the old screen-fixed sky (outline at most 0.16; it was
  0.57 in front of a cloud with the ink contour and violet leaves); 0.059 over the flat background of D92.

##### Static look-alike mushroom (#299), as built

Author-approved design, revised 2026-11-13. The whole point is doubt: the player has learned that a mushroom bursts when approached
(the honey fungus pair, D63), so a mushroom that does nothing must look **enough like one of them to make the player stop and think**
("is this a trap?"), yet differ in a way that can be learned. Rules:

- **Why the first design was dropped.** The first version was a bracket (shelf) fungus on a dead trunk, grey-brown. The author judged
  it looked nothing like the dangerous mushrooms, so it would scare nobody and teach nothing. A look-alike must share the active
  pair's silhouette family; it is the colour and the ring that differ. The "never a ground tuft on the route" rule and the
  trunk idea were dropped with it.
- **Shape.** The same family as the active pair: a tuft of three small caps on stems, 24×16 px, standing on the floor, possibly near
  the route. The stems have a **visible ring**, like the harmless honey fungus and **unlike** the ringless lethal false honey fungus,
  so the learnable shape cue stays consistent (a ring means harmless).
- **Colour (decor tier, D76, recoloured 2026-11-16 after playtest F15).** Muted grey-beige: caps `aa9c8a`, `998b7a`, `897b6a`
  with paler speckles `b4ada0`, stems `a7a194`, `958e82`, gills `796c60`, contour `6d6154` (its own hue, no ink). It reads as scenery
  (outline 0.16; the active pair 0.36 and up); the tuft and rings stay, so the shape still recalls the family. Before: pale grey-lilac
  `cfc8de / 9d93b6 / 6b6188` with bone `f2eef8`, ink contour `0b0814` (outline 0.45, as loud as a trap). **No golden glow, no honey
  brown, no lime** (those mark the active mushrooms).
- **No animation at all.** One frame; no idle glow, no reaction to the hero. Motion is the cue that an active mushroom is alive.
- **Implementation.** A static `still` kind of `Decor` (`kind` = `lookalike`): a picture, no object logic. `Decor.notice` and `update`
  do nothing for it. Sheet `assets/images/objects/decor-lookalike.png`, 24×16, one frame `static`, source
  `art/objects/decor/lookalike.grid`, preview `docs/references/forest-traps/decor-lookalike-preview.png`, layout
  `DECOR_SHEETS['lookalike']`, eight palette entries plus transparency (original drawing, nearest filter, at most 16). The rectangle is
  24×16 with `y = floor - 16` (add 16 in forest-2's offset layer), the same footprint as the active pair.
- **Placement.** One or two per level (the check allows at most two), only after the player has met an active mushroom, **never** on an
  active mushroom's trigger zone (which holds its swell), on a hint zone, or over a trap, pickup or mechanism. Built: `forest-2`, world
  x 1980 (file `y` 320), after the lethal mushroom at x 1672 (zone to x 1760); none in `forest-0` or `forest-1` yet.
- **Checks.** `tools/check_tiled_links.py` `check_still_decor` rejects a static look-alike on an active mushroom's zone, a hint zone or a
  trap/pickup rectangle, and more than two per map (`StillDecorLinksTest` in `tools/test_check_tiled_links.py`). `DecorTest` pins the
  stillness, the floor line, the placement after the active mushrooms and the count per map; `tools/test_forest_trap_sprites.py` and
  `SpriteSheetContractsTest` pin the sheet; `validateSpriteArt` guards the art.
- Not verified: how the look-alike reads at game scale in the running game; the author reviews the sprite before merge.

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
  ("Assets": libGDX's WAV decoder needs plain PCM; every effect and `intro.wav` are original synthesized sounds, see
  `docs/SOUND_DIRECTION.md`) and the web release check lists the audio files it expects.
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
- **The rumble loops (feel fix, D64).** `wall_rumble.wav` is a seamless one-second loop (no fade at the edges, every component
  periodic). `Level.activateTrigger` starts it with `GameContext.loopSound` and `Level.update` stops it with `stopSound`
  after `wall.moveDurationSeconds()` of game time; `clearCameraEffects` (pause, death, retry, room change, level change)
  stops it at once, so a paused or dead game is silent and a second lever restarts the loop. `Sound.loop(volume)` and
  `Sound.stop(id)` exist in the desktop backend and in the web (Howler) backend `HowlSound`; the loop id is kept in
  `KimeriaGame` per sound name.
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

## Effects from pixelfx (plan, 2026-11-16, [#396](https://github.com/jlogicgames/edgard_in_kimeria/issues/396))

A plan agreed with the author; only the fireball is built (below). [patch-gg/pixelfx](https://github.com/patch-gg/pixelfx) is a library of
244 procedural pixel art effects (MIT; credit Patch and Hugo Duprez in a notice file when the first effect lands).

- **Category.** Only time-varying VFX come from it (blasts, smoke, dust, sparks, splashes, slashes, flashes, shock
  waves, transitions), the same category as the torch particles in the section below. Anything that depicts a thing
  (a creature, a torch body, a boulder, a coin, an eyeball) stays an authored sprite; pixelfx is only a motion reference.
- **Route.** Default: a sprite sheet exported with its `tools/sheets.mjs`, recoloured to our palette and scaled down
  (its default frames are large, e.g. 300x240 for `small-explosion`), played as an `Animation`. Port a recipe to
  `KimeriaParticleSystem` only when the effect must react to the game. No allocation per frame, D7 warnings stay
  visible, shakes follow the reduced-motion option.

| Area | Issue | Main candidates |
| --- | --- | --- |
| Forest, existing objects | [#397](https://github.com/jlogicgames/edgard_in_kimeria/issues/397) | fireball done; bomb, stump and root blasts, stone and branch landing, spores kept as they are; falling leaves → [#473](https://github.com/jlogicgames/edgard_in_kimeria/issues/473); boss strike, counter and death → [#474](https://github.com/jlogicgames/edgard_in_kimeria/issues/474) |
| Forest monsters | [#398](https://github.com/jlogicgames/edgard_in_kimeria/issues/398) | beetle and flying beetle blasts, snail spit, bites, the notice pop of #240, death poof (check against the D65 blood) |
| Swamp | [#399](https://github.com/jlogicgames/edgard_in_kimeria/issues/399) | quicksand surface and bubbles, splashes and ripples, swamp gas |
| Steppe | [#400](https://github.com/jlogicgames/edgard_in_kimeria/issues/400) | wind and gust streaks, double-jump puff, arrow impact and deflection, lightning |
| Fortress | [#401](https://github.com/jlogicgames/edgard_in_kimeria/issues/401) | torch flame and light shafts, plate dust, cutters, incendiary fire |
| All worlds | [#402](https://github.com/jlogicgames/edgard_in_kimeria/issues/402) | pickup sparks and ring: **done** (see Pickup cue). The other items of #402 are not applicable or belong elsewhere: lost heart, no hearts exist (D2/D6); respawn beam, the silver thread already covers respawn (D81); level and room transitions belong to [#126](https://github.com/jlogicgames/edgard_in_kimeria/issues/126) (D3) |

**Forest audit (#397, author's decision 2026-11-18).** The Forest effects that already exist are kept, not replaced by pixelfx
versions: the bomb's explosion shader (`BombExplosionEffect`), the stump and root blast sheet (`ExplosiveTrap`), the falling stone's
impact (`EnvironmentFx.stoneImpact`), the boss strike's `ShockwaveEffect`, the honey fungus spores (`SporeCloud`) and the D65 blood.
The one gap was the hero's fireball, which had no trail and vanished where it stopped.

**Fireball, done (#397, approved by the author 2026-11-18).** Route: particles on `KimeriaParticleSystem` (`effects.FireballFx`, one
per `Level`), not an exported sheet, because the effect must follow the ball and react to what it hits; the ball keeps its authored
sprite. pixelfx's `fire/fireball`, `impacts/hit-spark` and `impacts/blunt-hit` were a motion, colour and timing reference only: no
code, no exported asset and no constant was copied, so no notice file is needed for it. The look, after the author's first review:

- **Warm palette only**, from the fireball sprite: pale `fdfaa0`, yellow `fde902`, orange `fc5601`, red `c50e10`. No smoke, no grey,
  no debris.
- **Hard pixels that shrink out**: trail, splash and sparks are 1 to 3 px opaque squares (`KIND_FLAME`, added for this), so nothing
  fades into olive or magenta over the sky. A trail flame's colour sets its life (pale .04 to .06 s, red .12 to .17 s), so the
  trail is short (about 50 px of flight at most) and cools from the ball outward; one flame per 2 px flown, so it looks the same at
  any frame rate.
- **Warm halo**: a steady additive `SoftDot` light under the flying ball (30 px) and one fading halo per hit (30 px, 0.2 s; 46 px,
  0.3 s on the boss; 16 px on a burn-out).
- **Where it stops**: a flame splash that rises and dies out (24 flames and 14 sparks, living 0.2 to 0.32 s) thrown back off a wall
  or on along the blow into a creature, stump, root or hive; on the boss a squashed ring of 30 flames and 22 sparks (0.3 to 0.44 s)
  over the boss's own hurt flash (a fireball the boss's orange counter sequence rejects bursts lightly, like a wall hit); at the end of its life a burn-out of 4 small rising flames. No blood (D99), no shake, the same with
  and without the elixir.
- **Impact ring** (author's addition, approved 2026-11-18): an expanding fire-coloured ring at every hit, drawn through the existing
  `shockwave.frag` with the uniforms `ShockwaveEffect` sets (the F3 debug ring), from a fixed pool of 4 ring slots (`RingPool`, owned by `FireballFx`), so
  no object is created per hit; no existing shader was edited, and there is no screen ripple (`RippleEffect`). Light hit: 0.26 s,
  20 px radius, 3 px wide; boss: 0.36 s, 36 px, 5 px; none on a burn-out. The ring ends a little before the burst's particles, so
  the two read as one blast. The shader grows the ring with a slow start, so it is fed an eased progress, `0.3 + 0.58 * (1 - (1 -
  t)^2)`: visible from the first frame of contact, fast, and stopped at 0.88 while still orange (the shader's last stretch reads
  grey over the blue sky). It is drawn additively with a warm colour intensity (4, 2.2, 0.3), because the shader's premultiplied
  output darkens the sky under normal blending. Where the shader cannot be built (`Shaders.tryLoad`) the ring is skipped.
- **Sound**: `fireball_fizzle` on a terrain hit and, at 0.6 volume, on a burn-out (SOUND_DIRECTION.md); a creature, stump or hive hit
  keeps its own sound.

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

## Contrast tiers (D76, playtest F15, #344)

What the player can interact with must be the loudest thing on screen, and scenery must not compete with it. Every
sprite belongs to one of three tiers. It is measured against what the game draws behind it at every placement:
`tools/check_contrast.py`, part of `:core:check`. Metrics and reasons are in
[D76](DECISIONS.md#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16).

| Tier | Belongs | Check (OKLab, worst placement) | Drawing language |
|---|---|---|---|
| Active | hero, traps and their at-rest cues (D71), enemies, collectables, mechanisms, terrain | outline at least 0.28 | ink contour `0b0814` (or the sheet's own near-black); the accent hues are reserved here |
| Decor | `Decor` kinds, `HangingVine`, later shrubs, flowers, ferns (#189) | outline at most 0.20, body 0.08 to 0.20, p90 chroma at most 0.10, no ink pixel | contour in its own darker hue, no ink, no accent hue, no idle animation (it may react to the hero or the world) |
| Background | the level's flat `background` colour (D92), tile scenery, later clouds and moon (#451) and parallax layers (#236) | no louder than decor | desaturated, static |

- **Outline** is the mean lightness difference between each silhouette pixel and the background pixel just outside it.
- **Body** is the mean colour distance between each opaque pixel and the background under it.
- **Chroma** is the 90th percentile of the sprite's chroma.

**What is behind the world ([D92](DECISIONS.md#d92-the-world-is-drawn-over-a-flat-per-level-background-the-forest-rain-and-sky-are-gone-430-2026-11-17)).** Each map sets a flat colour in its map property `background` (a Tiled colour); the
game clears to it, and the gate composites every sprite over that colour plus the map's tile layer, one sample per
placement. Every shipped map must set it. Open levels are at night: the forest maps use `2c3f66`; the night sky with its
moon and clouds is still to be drawn ([#451](https://github.com/jlogicgames/edgard_in_kimeria/issues/451)), and the gate is re-run over it then. Glowing sprites are
measured at their dimmest glow, and the bush in every frame. Collectables are measured over their pickup halo (#345), at rest
with the dimmest halo and at the top of the bob with the brightest (the coin, which has none, at both positions).

**At night (D92): the moon rim.** The ink contour reads against a light sky, not against `2c3f66`. An active sprite
whose outline does not reach 0.28 on its level's background carries the moon rim: its outermost contour pixels darker than
OKLab L 0.50 that face up, left or right become `c4cfe8`; those facing down keep their ink. Nothing is added or moved. The
exporters apply it (`tools/moon_rim.py`; `RIM_SHEETS`, `RIM_OBJECTS`, the CLI for the inherited sheets), never a hand
painting pixel by pixel; it is idempotent, so a rim softened by hand in the grid where it reads like a sticker survives a
re-export, and the gate keeps the softening honest. A trap's ink cue on a lit edge is kept (`RIM_KEEP`, the falling
platform's crack). It costs one palette entry (the hero's hair highlight was merged into the hair for it). Glowing sprites
keep the rim over the floor at their dimmest glow (the honey fungi breathe from 0.80). Which sheets have it, and every
measured value: D92.

**Decor at night (D92).** The decor keeps its own-hue contour and quiet tier; every decor colour is mixed 35 % toward
`2c3f66` in its grid, so the body stays at most 0.20 over the night (it was 0.215 to 0.273). The day values in the sheet
notes below are the drawings' history:

| Sheet | Day → night (contour first) | Outline / body over `2c3f66` |
|---|---|---|
| `decor-bush` | `4d6151`→`415558`, `597059`→`495f5e`, `677d62`→`526763`, `758b6a`→`5b7069`, `858358`→`666b5d`, `676547`→`525852` | 0.106 / 0.151 |
| `decor-lookalike` | `6d6154`→`56555a`, `aa9c8a`→`7e7b7d`, `998b7a`→`737073`, `897b6a`→`686669`, `796c60`→`5e5c62`, `a7a194`→`7c7f84`, `958e82`→`707278`, `b4ada0`→`84868c` | 0.106 / 0.157 |
| `decor-trunk` | `6d665e`→`565861`, `8b8379`→`6a6b72`, `9a9288`→`74757c`, `7a736a`→`5f6169` | 0.095 / 0.160 |
| `decor-vine` | `757a67`→`5b6567`, `9d9f7b`→`757d74`, `8a8c6d`→`69716b`, `88a083`→`687e79`, `738b73`→`5a706e` | 0.130 / 0.173 |
| `decor-crown` | `607660`→`4e6362`, `6e8466`→`576c66`, `7b9170`→`5f746c`, `889e79`→`687d72`, `948f62`→`707363` | 0.111 / 0.180 |

Reserved accent hues (active only):
- magenta (thorn tuft);
- lime (spores);
- ember and gold (`ffd23a`, fire, coin);
- the honey (`e2b25a`) and sulphur (`dcdc50`) of the fungi;
- the violet of the Bat and Mushroom pickups.

In short: **ink and accent colour mean "interact with me"; scenery gets neither.** Contrast is necessary, not
sufficient. The stump measured 0.39 and still read as scenery by shape and colour (F18), so D71's silhouette and
cue rules still apply; its fix was an idle tell in the ember accent, not more contrast (#346).

Measured 2026-11-16 (outline, worst placement; `python3 tools/check_contrast.py` prints the current table):

| Tier | Sprites |
|---|---|
| Active: traps | branch 0.48, log platform 0.48, honey fungus 0.45, bomb 0.42, root 0.35 (idle tell; was 0.41), thorn vine 0.41, puffball 0.40, stump 0.33 (idle tell; was 0.39), falling platform 0.38, false honey fungus 0.36, thorn tuft 0.36, thorn root 0.38 (tip and barbs at rest, #359; was 0.35), falling stone lip 0.32 (loose-overhang cue, #360; 0.39 in forest-0), hanging hive 0.45 at worst over its frames (falling 0.44, fallen 0.34 on the floor line) and its stingers 0.35, in `forest-1-hive` (#365) |
| Active: collectables | with the pickup halo (#345): Bat wing jar 0.54 (the flapping bat was 0.41), Mushroom 0.53, coin 0.37 (no halo); fireball refill 0.43 |
| Active: mechanisms | lever 0.47 (both frames; was 0.25 to 0.32, below the floor in front of the forest-2 rubble), ladder 0.43, vertical wall 0.42, carried torch 0.39, platform 0.37, lift 0.37, stone 0.30 |
| Active: enemies and hero | hero 0.35 (D79, both facings; the old 48 px hero was 0.46), RedMob 0.32, Boss 0.30, Bat 0.29, YellowMob 0.28 |
| Decor (after the recolour) | bush 0.12 to 0.18 (was 0.31 to 0.39), look-alike 0.16 (was 0.45), hanging vine 0.06 to 0.16 (was 0.47 to 0.57) |
| Background (tile layer) | rocks 0.29, signs 0.37, dead trees 0.37, tile bushes 0.36: louder than the ceiling. This is third-party art, reported and not gated until it is replaced ([#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189)). |

The decor recolour changed only the palette lines of `art/objects/decor/{bush,lookalike,vine}.grid`; the drawings, frames
and the D64 brush trigger are unchanged. The lever (then the author's wall-mounted 3/4 drawing, `art/objects/lever-pixel.png`) gained a 1 px
outline in its own near-black `010000` around both frames, painted into the master's cells so the export is exact; colours,
frame, pivot and bottom line were unchanged. The floor lever that replaced it (#413, `art/objects/lever.grid`) keeps the
near-black contour and measures 0.51 in both frames. It was the one active sprite under the floor: 0.25 in front of the rubble tiles
at forest-2 x 128 (up frame), 0.27 for the pulled frame. How it looks in the running game: [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368).

## Dialogue screen (D78, playtest F3/F10, #337)

Every healer conversation is one storybook screen: two portrait windows face each other in a carved walnut frame and the
silver thread (#228) hangs between them. Rule and measurements:
[D78](DECISIONS.md#d78-the-dialogue-is-a-codec-style-storybook-screen-playtest-f3f10-337-2026-11-16). Accepted design:
style A "walnut and gilt" of the Phase A mock-up (2026-11-16).

| Element | Art (`art/ui/dialogue/*.grid` → `assets/images/ui/dialogue-*.png`) | Speaker / listener |
|---|---|---|
| Portrait window | `window`, 32 px nine-patch (insets 12): walnut band 8 px, gilt bead, corner rosettes; opening dusk blue `3d4a68` | `lit` / `dim` (every colour × 0.5, the two brightest gilts capped) |
| Crest | `crest`, 17 × 7, a carved gable with a rosette on each window | `lit` / `dim` |
| Name plate | `plate`, 16 × 22 three-patch: gilt rule, dark walnut, two nail heads | `lit` / `dim` |
| Text box | `box`, 24 px nine-patch (insets 10): walnut band 6 px, ink inside | one look |
| Speech notch | `notch`, 13 × 8, on the box's top edge under the speaker's plate | moves to the speaker |
| Page | `page`, 24 px nine-patch: the whole screen, dim walnut border | one look |
| Choice | `choice`, 16 × 26 three-patch: ink with a dark gilt rule | `idle` / `selected` (double bright rule) |
| Silver thread | `thread`, 284 × 32, drawn at (178, 154): knot on Edgard's wrist, 16 px sag, end in the healer's hand | draws in over the opening |
| Glint | `glint`, 5 × 5 star, runs from the speaker to the listener as a line starts | |

- **Palette:** walnut `1d120c` `3a2416` `5a3a22` `7d5534`; gilt `6e5226` `a8844a` `d8c08a`, dim gilt caps `44361e` `3e3728` (muted, never the reserved gold
  `ffd23a` or honey `e2b25a`); ink `0b0d18`; silver `5d6577` `9aa3b6` `d8dee9`; at most 16 colours per sheet.
- **Portraits:** 158 px in the 160 px opening, linear filtering; the speaker lifts 1 px for half of every 1.2 s, the
  listener is multiplied by (0.55, 0.58, 0.70). The opening is dusk blue, not navy: on the first navy the portraits' dark
  contours vanished (dimmed healer outline 0.05).
- **Tiers (D76 by analogy, `tools/test_dialogue_frame_sprites.py`, layout read from `HealerDialogueView`):** an element's
  loudness is its loudest colour against the ground it sits on. Active (at least 0.28): everything that names the speaker
  (bead 0.40 on the opening; window band, crest, plate and notch 0.64 on the page) and the choices (idle 0.29, selected
  0.65), and the thread's outline (0.43 to 0.44). Decor (at most 0.20): the listener's window band, crest and plate, the page
  border and the text box border (0.17 to 0.18). The dim sheets are the lit colours × 0.5, except the two brightest gilts,
  capped at `44361e` and `3e3728`: at plain half brightness their rosette highlights measured 0.32. The dimmed portraits
  keep an outline of 0.21 and 0.20 against the opening (at least 0.15).
- **Production:** edit a grid, run `python3 tools/dialogue_frame_sprites.py` (exports and the contact sheet
  [dialogue-frame-art.png](references/dialogue-frame-art.png)); the tool test fails when an export is stale.
- **Portrait facing:** both portraits face the viewer for now (author, 2026-11-16); the healer is never mirrored, since
  that would move his staff to the other hand.

## Kingdom arms (#350, playtest F12)

The arms of Kimeria, the arms of King Rufardus ([STORY.md, "Kingdom heraldry"](STORY.md#kingdom-heraldry--accepted-2026-11-16-350-playtest-f12)):
a gold sun in splendour with eight straight rays, on a night-blue shield with a gold border, under a royal crown with
red jewels. The author chose it on 2026-11-16 from three options. The reference drawing is `art/heraldry/kimeria-arms.png`:
24×36 native pixel art, 8 colours, binary alpha, ink `0b0814` contour, the gold `ffd23a` of the sun.

One mark, scaled to purpose. The sun always has a round disc and eight rays, so the coin and the arms read as the same
mark. Small things carry the sun alone, because the shield and crown do not read at 7×7. Banner size and above shows the
full arms.

| Use | Form | Tier and colour |
|---|---|---|
| World coin | the sun alone: a white disc, eight navy rays, on the coin's gold face | active (collectable); outline 0.371 from the white rim; no halo |
| HUD coin | the sun engraved in the painted 3/4 coin, in place of the original "C" | HUD illustration |
| Fortress banners and wall shields ([#116](https://github.com/jlogicgames/edgard_in_kimeria/issues/116)) | the full arms, or the sun on blue cloth | **decor** (D76): no ink contour, none of the reserved gold, p90 chroma at most 0.10. A dull ochre sun on grey-blue cloth with a darker blue contour. Only a banner the player interacts with may use full colour, and then it is active and measured as such. |
| World map ([#94](https://github.com/jlogicgames/edgard_in_kimeria/issues/94)) | the full arms mark Kimeria | map art |
| Ending ([#416](https://github.com/jlogicgames/edgard_in_kimeria/issues/416), [D95](DECISIONS.md#d95-the-sun-on-the-kimeria-arms-lights-up-on-the-ending-page-416-2026-11-17)) | the full arms at 3x above the ending's text; the sun is dark while the page appears and lights with the paragraph that brings the light back | UI art, full colour, Nearest; no dark arms on the opening |
| Menu selection marker ([#415](https://github.com/jlogicgames/edgard_in_kimeria/issues/415)) | the full arms at native 24×36 (scale 1), one on each side of the selected button of every menu, 6 px outside its 3 px gold frame and centred on its row; they replace the old `>` `<` brackets and move with the selection (keyboard, gamepad, mouse hover, touch). Button width is capped at 558 px (`Overlay.MAX_BUTTONS_W`) so both stay 8 px inside the screen in every language (`CampaignFlowTest`). Frame `lit` of the strip below through `ui/KimeriaArms` (`Overlay.drawSelectionArms`). Allocation: the selection arms 0.0 B/frame (`AllocationBudgetTest.menuSelectionArms`); the rest of the menu frame: [#464](https://github.com/jlogicgames/edgard_in_kimeria/issues/464). Drawn only beside the selected button (`MenuSelectionArmsRenderTest`) | UI, full colour, nearest filter |

The ending's frames are one strip, `art/ui/arms/arms.grid` → `tools/arms_sprites.py` → `assets/images/ui/kimeria-arms.png`
(six 24×36 frames: dark, ember, glow, kindle, lit, flare; 10 colours, binary alpha). Frame `lit` is the reference drawing
pixel for pixel, and only the sun changes between frames (`tools/test_arms_sprites.py`). The put-out sun is a black disc
`12142c` with a cold ash rim `464e70` and no rays; the crown and the gold border stay gold in every frame, because they
are metal, not light. Code draws the strip through `ui/KimeriaArms` at a whole-number scale.
[Frames at 8x](references/kimeria-arms-frames.png).

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
| `trap-stump` | 16×16 | idle1..idle4, windup1, windup2, windup3, spent | exploding stump: idle tell (#346: a crack with a dull-red `7a1a14`/`b3201a`/`d8461c` ember pulsing and a 1 px `5b5870`/`9a97ad` wisp rising, 0.25 s a frame), then cracks, smoke, glow; charred remains |
| `trap-bomb` | 16×16 | idle1, idle2 | round iron bomb with a bone fuse and a smouldering tip (dull red / orange, no flame: the lit state is the D7 warm tint flash drawn over it); the blast is the `BombExplosionEffect` shader, not a sprite |
| `trap-root` | 16×24 | idle1..idle4, windup1, windup2, windup3, spent | exploding root, same idle tell (ember crack down the root, wisp from its tip) and wind-up language |
| `trap-blast` | 32×32 | blast1..blast12 | 12 steps (#250): pale ball, orange ball with sparks, burst of puffs with a ground ring, fire mushroom under a dark-brown smoke cap, brown smoke saucer, ring, fade; the gameplay blast is 64×64 px, so draw at 2× (nearest) or keep the sprite as the visual core |
| `trap-thorn-root` | 16×24 | hidden, peek1, peek2, rising, up | bone-coloured stake; peek1/peek2 is the wobble |
| `trap-thorn-tuft` | 16×16 | calm, bristle, spiked | crimson-magenta stems with bone tips, deliberately not green like the bushes |
| `trap-bramble` | 16×24 | rest, rustle1, rustle2, cane1, cane2, tip | creeping bramble (#364, D89): a clump of the tuft's magenta canes rooted in a face on the left, bone tips always showing (rest), shifting and poking a first then a second tip (the warning); the arm is `cane1`/`cane2` alternating behind `tip` at the current extent; drawn mirrored for `root=right`; active tier, outline in `forest-1-bramble` (`check_contrast.py`, all six frames): rest 0.419, worst 0.414 (`cane1`, `cane2`) |
| `trap-branch` | 32×16 | resting, rustle, sag, falling | resting and rustle carry the at-rest cue (#361): a pale raw-wood band round the joint, cut by an ink zig-zag, and a chip under it; rustle = leaves shake, sag = drooping with a split at the trunk |
| `trap-stone` | 16×16 | stone, cracked | boulder and impact frame |
| `trap-stone-lip` | 24×8 | loose, broken | the falling stone's at-rest cue (#360): a cracked rock lip with the stone's crown wedged in it, then the empty socket; centred on the stone's column at its `lipTop` (on screen with the hero on the floor; forest-0 y 8, forest-2 y 48 under the dirt block); stone palette; with a grit trickle (`KIND_GRIT`, dark `34314a` and light `d8d6e8` specks) while armed |
| `trap-deadfall` | 32×16 | tied, strain, slip, swing, caught | the trip-line deadfall's log (#363, D88): a 24×10 bark log seen broadside with its end grain (`e8c890`/`9c6a34`) on the left; tied/strain/slip carry the bone lashing (`efe6c4`/`b8ad9c`) up to the bough, strain adds frayed fibres, slip draws the log one row lower; swing has three bone speed streaks on its trailing side; caught has a frayed cord stub. Rope eyes at x 8 and 23, row 4. Active tier, worst outline 0.410 tied (forest-1-deadfall; the same over the old sky and the D92 background) |
| `trap-deadfall-bough` | 112×24 | bough | the bough the deadfall hangs from: the sound branch's bark (`4a2e1c`/`8a5a30`/`c08a4c`), two bone rope knots at x 40 and 55, a bare snag from its underside to (8, 17) where the log ends, a leaf tuft at the thin end, end grain at the thick end that meets a `Decor:trunk`; mirrored for `swing=right`. Outline 0.464 |
| `trap-deadfall-vine` | 24×8 | taut, tugged, cut | the trip vine: bone (`efe6c4`/`b8ad9c`) between two dark pegs, 4 px over the floor; tugged into a V during the warning, cut in two after the release. Outline 0.323 taut |
| `trap-deadfall-rope` | 1×8 | rope | a twisted bone cord strip the game stretches and rotates from a knot to a log eye (as the silver thread's strand) |
| `trap-shadow` | 24×8 | small, medium, large | near-black ground shadow (growing warning); the renderer may tint or scale |
| `trap-puffball` | 16×16 | idle, swell1, swell2, burst | spore puffball mushroom |
| `trap-spores` | 24×24 | cloud1, cloud2, cloud3 | lime spore cloud |
| `trap-vine` | 24×40 | hang, swing-left, swing-right | anchored top centre; pendulum by swing frames or rotation |
| `trap-log` | 32×16 | intact, crumble1, crumble2 | rotten log bridge, same footprint as `platform.png` |
| `trap-honey-fungus` | 24×16 | idle1, idle2, idle3, swell1, swell2, spent | harmless honey fungus: three honey-brown caps, bone stems **with rings** (#295, D63; drawn by `HoneyFungus`, the sprites still await the author's review) |
| `trap-hive` | 24×32 | rest, shake1, shake2, release, bare, falling, fallen | hanging hive (#365, D90): a paper wasp hive on a bough drawn into the top 5 rows, the bough the frame's top edge; `shake2` and `release` show stinger tips; `bare`, `falling` and `fallen` are the knock-down; specks are `KIND_GRIT` particles |
| `trap-stingers` | 8×16 | fall1, fall2 | the hive's lethal band, drawn a row at a time from the mouth to the solid, the pattern moving down with the stream: bone `efe6c4` shafts with ink `0b0814` tips (the tips are what read on the mid-blue sky: 0.346, against 0.208 with a darker bone tip) |
| `trap-fissure` | 16×12 | rest1, rest2, warn1, warn2, vent, spent | ember fissure (#366, D91): an ash crust on the floor line (top = floor − 4) with a pale `d8d6e8` sky-facing rim and a glowing slit; the frames step the slit through the ember palette (dull red at rest, pulsing every 0.5 s; orange, then yellow with the lips lifted in the warning; white-hot `vent` during the jet; sooted `spent` for 0.4 s after); the rising and falling sparks are `KIND_GLOW` particles. Drawn for the night of D92: the light rim and the ink crack against the floor carry the outline, not ink against the sky |
| `trap-ember-jet` | 8×16 | jet1, jet2, top1, top2 | the fissure's lethal column, drawn a row at a time up from the floor line (`jet1`/`jet2` swapped every 0.05 s so the flame climbs) under the 8 px tip (`top1`/`top2`, the upper half of the frame): orange `f07a1a` and yellow `ffd23a` edges around a white-hot `fff4b0` core, a red-orange `d8461c` fleck inside |
| `trap-false-honey-fungus` | 24×16 | idle1, idle2, idle3, swell1, swell2, spent | poisonous look-alike: same tuft and size, sulphur-yellow to orange caps with a green tinge, olive gills, greenish stems, **no ring**, a bump on the big cap (#295, D63; drawn by `FalseHoneyFungus`; its lethal cloud is `effects.SporeCloud`, particles plus a shader, no sprite) |

**Bomb:** the stump and root sets replace the glass-bottle bomb in the tutorial (#233, D47: `ExplosiveStump`/`ExplosiveRoot` use these sheets, the blast frames are drawn at 2x). The `Bomb` trap type stays separate (D51) and has its own redrawn sprite, `trap-bomb` (#263, D60); the glass bottles in `images/Items.png` are no longer drawn (the file stays for the boss elixir icon; the coin moved to `objects/coin.png`, #350).

**Honey fungus pair (#295, D63), built (phase 2, 2026-11-13); the sprites await the author's review.** Sources `art/objects/forest-traps/honey-fungus.grid` and `false-honey-fungus.grid`, previews `docs/references/forest-traps/honey-fungus-preview.png` and `false-honey-fungus-preview.png` (the strip at 6x on the night, canopy and trunk backgrounds, and at 1x). Both are 24×16 with six frames: three idle frames (same silhouette; **currently only `idle1` is drawn**: the runtime now modulates its brightness with a smooth sine glow on the level clock instead of swapping frames, because the author found the frame swap blinking; `idle2` and `idle3` are unused, kept in the sheet and not redrawn), two swell frames for the D7 telegraph, and a deflated `spent` frame. They are near twins on purpose: the same tuft and footprint; the honey fungus has rings on its stems and honey-brown caps, the false one has no rings, sulphur-yellow to orange caps with an olive tinge, olive gills and a small bump on the big cap. Not a fly agaric, not the purple healer Mushroom (D43), not the pale round `trap-puffball`. The lethal cloud is drawn by `effects.SporeCloud` (below); the harmless burst is golden particles, so it needs no sprite. **As built:** the `HoneyFungus` and `FalseHoneyFungus` objects draw the strips (`idle1` tinted by a smooth brightness glow on the object's own clock, 2.5 s period, 0.80 to 1.0 since D92 (#430: the moon rim must stay over the active floor at the dimmest glow on the night; it was 0.55, and before that 0.8, found too subtle by day), the same for both; the two swell frames over the shared warning; `spent` after the burst); the harmless burst is 16 golden `KIND_GLOW` particles, the lethal cloud is `SporeCloud` (it first used `trap-spores` at 24x24; replaced, #296). Remaining: static scenery look-alikes are [#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299); the swell duration comes from `TrapWarnings` and is tuned by [#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300).

**False honey fungus spore cloud, particles and shader (#296, final, approved by the author, 2026-11-13).** Live simulated particles and a shader, not sprite art (they depict no object, so `check_sprite_art.py` has nothing to allow). `effects.SporeCloud` is the only cloud path (the sprite path is removed; the `trap-spores` sheet stays for the spore puffball, D46). Palette is that of `trap-spores`: `5fa02a` dark, `a6e04a` mid, `e6f26a` bright, so the cloud stays lime next to the golden `KIND_GLOW` burst of the harmless fungus. **The shader layer is always on; if a context cannot compile it, `Shaders.tryLoad` returns null and the particles alone are drawn, a complete look of their own.** Layers, back to front: (1) `assets/shaders/spore_cloud.frag`, normal blending, a swirling lime glow (angle grows with distance from the cap tuft and with time, sine-warped density, a bright core) on a quad that is exactly the lethal rectangle; (2) 34 `KIND_SMOKE` puffs (normal blending, grow 1x to 2x, 6 to 8 px, dark-to-mid lime); (3) 12 `KIND_GLOW` specks (additive, shrinking, 2.5 to 3.5 px, `e6f26a`). Timeline over the 1.0 s life: the puffs start around the tuft and grow and drift to points across the rectangle, fading linearly (`KIND_SMOKE`); its haze swells in the first 0.25 s, thins to about 45 % by 0.9 s and is zero as the hitbox ends (`SporeCloud.strength`). Particles are cleared the frame the hitbox ends. **Look:** a lime puff swelling out of the tuft, speckled with bright specks, over one continuous swirling body of light that fills the rectangle edge to edge and keeps the late, thin phase visible. **Fallback look (shader not compiled):** the particles alone, covering roughly the middle 60 % of the rectangle and fading out by about 0.9 s. **Why it stays inside the hitbox (D5):** the particles are kept inside by construction; the shader quad is the lethal rectangle and its alpha is multiplied by a border fade to zero (12 % at the sides and top, 4 % at the floor line), and every particle's start and end are kept half its largest drawn size inside the rectangle, so a straight lerp never leaves it (tested every frame). Shader notes (checked on macOS desktop and in a browser by the author, 2026-11-13): if it shows nothing in a build, run with `-Dkimeria.debug=true`: the first burst logs whether the layer loaded or fell back; GLSL ES 1.0, `precision mediump float`, no loops, no texture reads, no hash noise (mediump), only small-argument `sin`/`cos`/`smoothstep`/`mix`; loaded with `Shaders.tryLoad`, so a build that cannot compile it draws the particles alone (the fallback above). Preview without playing: `docs/references/forest-traps/false-honey-fungus-cloud-preview.png` (seven times from 0.05 to 0.98 s, 10x, red frame = lethal rectangle), made by the test-side `SporeCloudPreview` (CPU port of the shader, so an approximation of the GPU image): `SPORE_PREVIEW_OUT=docs/references/forest-traps/false-honey-fungus-cloud-preview.png ./gradlew :core:test --tests '*SporeCloudPreview'`.

**Palette (shared families, each sheet uses at most 15 colours + transparency):** near-black
contour `0b0814`; wood `4a2e1c / 8a5a30 / c08a4c`; ember `7a1a14 / b3201a / d8461c / f07a1a / ffd23a / fff4b0` (the two dim reds are the stump and root idle tell, #346: red at rest, orange and yellow only in the wind-up); the ember fissure uses the same steps, red at rest, orange and yellow in its warning and jet, #366);
smoke and stone `2a2638 / 34314a / 5b5870 / 9a97ad / d8d6e8`; bone `efe6c4 / a89c78`; thorn tuft
`c2307a / 6e1650`; spores `a6e04a / 5fa02a / e6f26a`; honey fungus caps `e2b25a / c08a4c / 8a5a30`, false honey fungus caps `dcdc50 / d2a62c / 8f8a26`, its greenish stems `d4dc9c / 98a468`; vine `8a8a2a / 4a4a18`; leaves `3b2a5c / 7a5ab0`.
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
