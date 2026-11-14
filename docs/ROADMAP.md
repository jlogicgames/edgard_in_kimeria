# Edgard in Kimeria — development roadmap

**Working version — 2026-10-21.** Discussion and drafts are in Russian; committed
documentation is in English.

**Accepted sequence:** mechanics MVP for all four stages, then the full campaign
and first external playtests. The breakdown and exit criteria below are a working
plan, refined by prototypes. Calendar dates are not assigned yet.

[GAME_DESIGN.md](GAME_DESIGN.md) owns game intent and rules;
[DECISIONS.md](DECISIONS.md) records their history. This roadmap defines work order
and verifiable outputs; it does not automatically change the design.

## Starting point

The existing test build has two forest levels, melee combat, rooms/death recovery,
the healer, an elixir, the forest boss and an ending. Its accepted scope is in
[MVP.md](MVP.md), available behavior in [GAMEPLAY.md](GAMEPLAY.md). Previous
validation establishes only the versions checked, not future mechanics.

English, Ukrainian, Polish, German and Belarusian are implemented in the current
build by [#85](https://github.com/jlogicgames/edgard_in_kimeria/issues/85), merged in
[PR #150](https://github.com/jlogicgames/edgard_in_kimeria/pull/150). Catalog/font
validation and editing instructions are in [LOCALIZATION.md](LOCALIZATION.md).
This is the language baseline for future text; native-speaker review and final
campaign/device acceptance remain release work.

**Two different meanings of MVP:**

| Name | Meaning |
| --- | --- |
| Existing two-level MVP | The assembled test loop and starting implementation. |
| New mechanics MVP | The next phase: verify each stage mechanic's function and usefulness in short prototypes. |

The old scope limits do not forbid new prototypes. Its completed status does not
mean the expanded campaign is ready.

## Route at a glance

| Phase | Output | Status |
| --- | --- | --- |
| 1. Mechanics MVP | Shared tools, one mechanic prototype per stage, resource/recovery checks. | Planned |
| 2. Full game and first playtests | Coherent levels, mechanic progression, four stages, story objective, economy and bosses; first checks with new players. | After phase 1 |
| 3. Completion and release validation | Full-run balance, presentation, sound, localization, controls, saves and release-build checks. | After campaign assembly |

Initial order: shared tools → Forest → Swamp → Steppe → Fortress → cross-prototype
MVP checks → coherent playable section and first playtests → campaign expansion →
full-playthrough and release checks.

<a id="mechanics-mvp"></a>

## Phase 1. Mechanics MVP

Verify that each leading mechanic can be taught clearly, developed into interesting
situations and combined with Edgard's shared actions before producing all levels.

Existing assets, simple presentation and short test maps are enough. The working
scope is **one small prototype per stage, with 2–3 rooms**: introduction, variation,
and combination with a familiar danger. This is not a mandatory future-level format.

During this phase the author plays prototypes, checks functionality and measures
mastered traversal. These are necessary development checks. First external
playtests and one-minute learning checks with unfamiliar players belong to phase 2.

### 1.1. Shared player tools

- [x] [#95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95) Implement directional action, pickup/place and ranged attacks. The original directional split shipped 2026-10-22 and was revised by [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175): Up + Action selects a pickup or mechanism, Down + Action only puts down, and ranged throws a held object or fires a fireball with an empty hand. See [current behavior](DIRECTIONAL_CONTROLS.md) and [D30](DECISIONS.md#d30-one-direction-for-pickup-and-mechanism-interaction).
- [x] [#97](https://github.com/jlogicgames/edgard_in_kimeria/issues/97) Implement carrying one object in one hand, with the sword still available,
  plus placing and throwing. [D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket)
  fixes one shared carryable slot with its object always visible in the front hand;
  no separate pocket or simultaneous stone and torch. Implemented in PR #154;
  secured object rollback completed in PR #165. All four ticket criteria verified
  2026-10-25; [evidence and practice route](DIRECTIONAL_CONTROLS.md#carrying-acceptance-97).
  Authored Forest puzzles remain #103–#104; torch/light puzzles remain #112–#115.
- [x] [#98](https://github.com/jlogicgames/edgard_in_kimeria/issues/98) Implement consumable ranged attacks and rare refills. Separate throwing
  from fireball expenditure; a single press must not perform both. Capacity three,
  two +1 contact pickups, full-stock hints and three-icon HUD merged in PR #168
  on 2026-10-25. [Rules and audit](DIRECTIONAL_CONTROLS.md#fireball-refills-98).
- [x] [#99](https://github.com/jlogicgames/edgard_in_kimeria/issues/99) Implement vertical ladders. Define entry, ascent/descent, exit,
  permitted actions and interaction priority near a ladder. Implemented 2026-10-25;
  [rules](DECISIONS.md#d28-vertical-ladders), [evidence and limits](DIRECTIONAL_CONTROLS.md#ladders-99).
- [x] [#100](https://github.com/jlogicgames/edgard_in_kimeria/issues/100) Check death/recovery with held objects and ammunition: no failed-attempt
  stock loss, duplicated pickups or resource accumulation through retries. Verified 2026-10-25
  with 95 focused tests and Chrome checks; [results](ROLLBACK_VALIDATION.md).
  The separate development F4 transition crash is tracked in #170.
- [ ] [#101](https://github.com/jlogicgames/edgard_in_kimeria/issues/101) Check keyboard, gamepad and touch in both handedness layouts. Hints show
  the selected action/target. Check held buttons after pause, dialogue and transitions.
  Author-reported status (2026-10-27): keyboard checked in both layouts; gamepad
  checked in the default layout only; touch not checked; there is no native phone build and none is planned (D61).
  Remaining device coverage: mirrored gamepad; both touch layouts on a phone are **Cancelled (D61): will not be implemented**.
  Author-reported update (2026-11-06): the desktop version was checked with keyboard
  and gamepad in both the right-handed (default) and left-handed layouts. The author did
  not say which build or rooms. Still open: the other
  unverified criteria (held buttons after pause, dialogue and transitions).

**Output:** in a test section, move an object, operate a mechanism, fight while
carrying and deliberately choose a throw or fireball. Ladders and contextual action
must not interfere. Preserve existing jumping, combat and recovery behavior.

### 1.2. Forest

Level order: `forest-0` (tutorial) → `forest-1` → `forest-2` (boss level, formerly `forest`, then
`forest-3` until [#262](https://github.com/jlogicgames/edgard_in_kimeria/issues/262); the former open question
"`forest-2`?" is closed, no extra level is planned); see
[D32](DECISIONS.md#d32-forest-level-order-and-identity), [D51](DECISIONS.md#d51-forest-3-renamed-forest-2-the-bomb-stays-a-separate-trap). The rename and decoupling from the
level name are [#194](https://github.com/jlogicgames/edgard_in_kimeria/issues/194).

**Tutorial level (`forest-0`, [#195](https://github.com/jlogicgames/edgard_in_kimeria/issues/195), skeleton done):**
six short lesson rooms without monsters, hints always on, one new element per room, then `forest-1`
with no hints. Hints are authored in the map ([D33](DECISIONS.md#d33-the-forest-0-tutorial-level-and-map-authored-hints)).
Rooms in order, each a separate ticket: lever (#186), ladder/bridge (#196), wall (#197), moving
platform (#198), thrown stone (#199), existing traps (#200).
**Lever room (#186) is built** (`forest-0-lever`, room shortened to 576 px by #206/#209, mastered route about 4.3 s, headless author check, then the author's desktop and web play-through of 2026-11-05; novice
playtests are #129–#131) and is the sample the other lessons copy: see the
[tutorial lesson room recipe](ROOMS.md#tutorial-lesson-room-recipe-186) and [D34](DECISIONS.md#d34-the-tutorial-lever-lesson-rule).
**Ladder room (#196) is built** (`forest-0-ladder`, 704 px, a lever lowers a retracted ladder to the ledge that holds the exit; mastered route about 6.9 s,
headless author check, then the author's desktop and web play-through of 2026-11-05; not a novice playtest): see [D35](DECISIONS.md#d35-the-tutorial-ladder-lesson-rule).
**Wall room (#197) is built** (`forest-0-wall`, 640 px, a floor lever raises and lowers a wall that blocks the corridor; mastered route about 5.1 s,
headless author check, then the author's desktop and web play-through of 2026-11-05; not a novice playtest): see [D36](DECISIONS.md#d36-the-tutorial-wall-lesson-rule).
**Platform room (#198) is built** (`forest-0-platform`, 640 px, a floor lever starts and stops a vertical moving platform that is the only way up to the ledge holding the exit; mastered route about 9.6 s,
headless author check, then the author's desktop and web play-through of 2026-11-05; not a novice playtest): see [D37](DECISIONS.md#d37-the-tutorial-moving-platform-lesson-rule).
**Stone room (#199) is built** (`forest-0-stone`, 528 px, the hero picks up a stone and throws it across a chasm to flip a lever on the far side; the lever starts the moving platform that carries the hero over; mastered route about 4.8 s,
headless author check, then the author's desktop and web play-through of 2026-11-05; not a novice playtest): see [D39](DECISIONS.md#d39-the-tutorial-thrown-stone-lesson-rule).
**Traps room (#200) is built** (`forest-0-traps`, 720 px, the last room: a pit bridged by one falling platform, then a contact bomb (since #233 an exploding stump of the same geometry, see [D47](DECISIONS.md#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root)), each with its D7 warning; mistakes cost one cheap room retry; mastered route about 6.1 s,
headless author check, then the author's desktop and web play-through of 2026-11-05; not a novice playtest): see [D40](DECISIONS.md#d40-the-tutorial-traps-lesson-rule). **The forest-0 lesson sequence (lever, ladder, wall, platform, stone, traps) is complete.**
**Explosive traps (#233) are implemented** (exploding stump and root replace the glass-bottle bomb: touch or a fireball / thrown stone from afar lights them, shared D7 warning, one 64x64 blast, a mandatory floor bypass checked at load; the only shipped placement is the stump in the traps room; the separate `Bomb` in `forest-2` has its own redrawn sprite and keeps the shader blast, [D60](DECISIONS.md#d60-the-bomb-gets-its-own-redrawn-sprite); automated tests only): see [D47](DECISIONS.md#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root).
**Healer tales (#220)** open and close the tutorial in fairy-tale language (start of a new game, end before forest-1): see [D41](DECISIONS.md#d41-the-healers-tales-around-the-forest-0-tutorial). Both tales (start and farewell) were played by the author on 2026-11-05 with no remarks; the de, pl, uk and be translations are not native-reviewed.

**Author acceptance of the forest-0 tutorial (2026-11-05):** Adam Konter (project author) played the whole tutorial on desktop (keyboard only) and also checked the web build. All six rooms, both healer dialogues (start tale and farewell) and the transition into forest-1 were checked, with no remarks (OK). Not checked in that session: gamepad, touch controls, native-speaker quality of the de/pl/uk/be translations (the author reported a desktop keyboard and gamepad check in both layouts on 2026-11-06, without naming a build or rooms; see [#101](https://github.com/jlogicgames/edgard_in_kimeria/issues/101) above). Still not done: touch in both layouts, native-speaker translation check, novice playtests (#129–#131). Mastered route times above are headless author checks and are unchanged. Record: [ROOMS.md](ROOMS.md#author-acceptance-of-forest-0-2026-11-05).

**Author mobile-browser play-through (2026-11-05):** the author also played the web build on an iPhone (browser; no mobile build exists yet, and none will be made: D61) and completed all the tutorial screens by touch. The game is playable. Four problems were found and are tracked in [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226): in portrait the touch controls are drawn over the game although there is plenty of free space above and below; no fullscreen on mobile; in landscape the browser chrome stays and only about half of the game is visible; the screen turns off after some minutes. The fixes (D53 to D57) were checked by the author on a real iPhone in Safari on 2026-11-07 and #226 is closed; fullscreen / Add to Home Screen was dropped as not needed. Android, tablets and the frame rate on a mid-range phone are tracked in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277). Not a novice playtest (#129–#131), and not the release-device pass (#144). Record: [ROOMS.md](ROOMS.md#author-acceptance-of-forest-0-2026-11-05).

- [x] [#102](https://github.com/jlogicgames/edgard_in_kimeria/issues/102) Select Forest's leading mechanic from levers/linked objects, lowered
  ladders and carrying/throwing to affect obstacles.
  Completed 2026-11-14: direction accepted in [D31](DECISIONS.md#d31-forest-uses-linked-route-reconfiguration) and GAME_DESIGN.md; prototype validation continues in #103, #104 and #187.
- [ ] [#103](https://github.com/jlogicgames/edgard_in_kimeria/issues/103) Build short rooms demonstrating the rule and several applications.
- [ ] [#104](https://github.com/jlogicgames/edgard_in_kimeria/issues/104) Combine it with an enemy or trap. Test choices between waiting,
  melee, spending a ranged attack and environmental interaction.

Concrete follow-up tasks: [#186](https://github.com/jlogicgames/edgard_in_kimeria/issues/186)
introduces a lever and linked support; [#187](https://github.com/jlogicgames/edgard_in_kimeria/issues/187)
develops an optional route. [#188](https://github.com/jlogicgames/edgard_in_kimeria/issues/188)
researches original traps, tests adaptations, then brainstorms new traps. Its research
phase is documented in [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md) (15 screenshots, the author's
notes, open questions, unselected candidates); the author answered the original behaviour on 2026-11-05
(only the successful finds are adapted for modern fairness, D7 and D26). **The brainstorm
phase is done by [D42](DECISIONS.md#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan)**
(forest trap set, explosive-trap choice rule, tutorial plan). Implementation is tracked by
#230 art, #231 proximity traps, #232 falling traps (implemented, D45), #233 explosive stump/root, #234 rhythm
traps and #235 tutorial integration (implemented 2026-11-06, [D48](DECISIONS.md#d48-traps-in-the-tutorial-rooms-one-new-trap-per-lesson-room): thorn root in the ladder room, falling branch in the wall room, falling stone in the platform room, exploding stump in the stone room; automated tests only, not yet played by the author), and #260 forest-1 traps (implemented 2026-11-09, [D58](DECISIONS.md#d58-forest-1-traps-and-a-second-redmob-three-traps-and-one-enemy-in-the-open-stretch): thorn tuft, second RedMob, spore puffball, rotten log over a pit; automated tests only, the author has not played it and the new art has not been seen on this map; #260 stays open until the author checks it in the running game). **Backlog added 2026-11-05** (author requests, not
accepted rules): [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236) parallax
layers, [#237](https://github.com/jlogicgames/edgard_in_kimeria/issues/237) castle floor
pressure plate, [#238](https://github.com/jlogicgames/edgard_in_kimeria/issues/238) arrow trap
for world 2 and later, [#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239)
environment effects, [#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240) enemy
wind-up; see ART_DIRECTION.md, GAME_DESIGN.md and D42. Also #224 (impaled-death sprite), #225 (decompose the
screens) and #229 (Bat and Mushroom). Playtests remain open. #104 may
also use an existing enemy. [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189)
tracks third-party artwork inventory, schematic replacement and original redraws.

**Output:** a concrete forest puzzle principle creates distinct situations rather
than repeated activation of the same lever. Existing forest maps/boss provide
material for testing and development without requiring immediate total rebuilding.

### 1.3. Swamp

- [ ] [#105](https://github.com/jlogicgames/edgard_in_kimeria/issues/105) Select a leading mechanic distinct from Forest: quicksand, unreliable
  footing, changing a traversable route or a combination.
- [ ] [#106](https://github.com/jlogicgames/edgard_in_kimeria/issues/106) Test a short route-preparation problem and a route-execution problem.
- [ ] [#107](https://github.com/jlogicgames/edgard_in_kimeria/issues/107) Check retries after falls, disappearing supports and linked-object
  changes: the puzzle must always become solvable again.

**Output:** Swamp has its own kind of solution. Changing forest art and switch
appearance alone does not count as a new mechanic.

### 1.4. Steppe

- [ ] [#108](https://github.com/jlogicgames/edgard_in_kimeria/issues/108) Implement readable wind: direction, strength, cycle and shelter are
  understandable before committing to a dangerous action.
- [ ] [#109](https://github.com/jlogicgames/edgard_in_kimeria/issues/109) Select a minimal set of wind effects on the hero and objects. Test
  helpful and dangerous gusts rather than only constant slowdown.
- [ ] [#110](https://github.com/jlogicgames/edgard_in_kimeria/issues/110) Implement double jump available from Steppe. Define acquisition,
  second-jump use/reset and compatibility with carrying and ladders.
- [ ] [#111](https://github.com/jlogicgames/edgard_in_kimeria/issues/111) Teach the second jump alone before combining it with wind. Check
  it is unavailable before the intended point and unnecessary in Forest/Swamp.

**Output:** wind changes decisions; double jump becomes part of Steppe and later
Fortress authoring. Wall jump already exists and is not scheduled for redundant
implementation here. Discuss its campaign role/changes separately; preserve current
routes until an explicit change is accepted.

### 1.5. Fortress

- [ ] [#112](https://github.com/jlogicgames/edgard_in_kimeria/issues/112) Confirm light as the leading mechanic with a portable-torch prototype.
- [ ] [#113](https://github.com/jlogicgames/edgard_in_kimeria/issues/113) Check taking a torch from the wall, carrying, placing and throwing it ahead.
- [ ] [#114](https://github.com/jlogicgames/edgard_in_kimeria/issues/114) Check path/danger visibility under different light placement.
- [ ] [#115](https://github.com/jlogicgames/edgard_in_kimeria/issues/115) Add one meaningful creature response, such as repelling spiders or
  attracting insects. Choose one for the prototype; both are not required immediately.
- [ ] [#116](https://github.com/jlogicgames/edgard_in_kimeria/issues/116) Combine light with mastered movement, combat or double jump.

**Output:** source placement changes the room situation. Darkness must not turn
lethal hazards into unpredictable invisible hits.

### 1.6. Cross-prototype MVP validation

- [ ] [#117](https://github.com/jlogicgames/edgard_in_kimeria/issues/117) Traverse all four prototypes with normal controls and invulnerability off.
- [ ] [#118](https://github.com/jlogicgames/edgard_in_kimeria/issues/118) Confirm required routes remain possible with zero fireballs.
- [ ] [#119](https://github.com/jlogicgames/edgard_in_kimeria/issues/119) Check recovery of carried objects, enemies, mechanisms, platforms,
  pickups and ammunition after death.
- [ ] [#120](https://github.com/jlogicgames/edgard_in_kimeria/issues/120) Test a minimal economy on the existing boss: limited coins, ingredients,
  elixir and an extra fireball. Purchases must create a choice; MVP prices/counts are trial values.
- [ ] [#121](https://github.com/jlogicgames/edgard_in_kimeria/issues/121) Check expenditure/pickups across test-level boundaries. Before campaign
  production, decide how Continue restores stage stock, double-jump access and
  mandatory story progress. Record the choice; integrate it in phase 2.
- [ ] [#122](https://github.com/jlogicgames/edgard_in_kimeria/issues/122) Record mastered traversal and replay cost. Target roughly 20 seconds
  for ordinary rooms; 60 seconds of first learning remains an untested hypothesis.
- [ ] [#123](https://github.com/jlogicgames/edgard_in_kimeria/issues/123) Update the design with Forest/Swamp/Fortress choices, verified rules,
  rejected options and questions for the next iteration.

**Phase 1 exit:** four functioning prototypes and shared actions exist. The author
can explain their distinct problems and traverse them. No blocking control/recovery
bugs remain. Questions affecting campaign structure are resolved or have a concrete
next experiment. Four stage names/icons alone do not complete the mechanics MVP.

<a id="campaign-and-playtests"></a>

## Phase 2. Full game and first playtests

### 2.1. Campaign framework

- [ ] [#124](https://github.com/jlogicgames/edgard_in_kimeria/issues/124) Integrate stage → levels → rooms and Forest → Swamp → Steppe → Fortress.
  The existing overarching campaign task is [#94](https://github.com/jlogicgames/edgard_in_kimeria/issues/94).
- [ ] [#125](https://github.com/jlogicgames/edgard_in_kimeria/issues/125) Reconcile its details with current design: the two existing forest levels
  form Forest's starting base; five levels per stage remains an expansion hypothesis.
- [ ] [#126](https://github.com/jlogicgames/edgard_in_kimeria/issues/126) Integrate transitions within/between stages, the world-map presentation
  from #94 and an ending only after the full campaign. Final map art can follow later.
- [ ] [#127](https://github.com/jlogicgames/edgard_in_kimeria/issues/127) Implement phase 1's persistence decisions: Continue at level entry,
  required tablet fragments, Steppe skill and stage resource budget. Check quitting
  during transitions and prevent level skipping.

### 2.2. First coherent level and external validation

- [ ] [#128](https://github.com/jlogicgames/edgard_in_kimeria/issues/128) Build a complete level of roughly five rooms: introduce a rule,
  develop it, combine it with dangers and offer an optional route. Author and replay-test
  changed-world consequences under the [D26 contract](ROOMS.md#puzzle-platformer-authoring-contract-d26).
- [ ] [#129](https://github.com/jlogicgames/edgard_in_kimeria/issues/129) Run first external playtests after the mechanics MVP, without waiting
  for all 100 hypothetical rooms. Let new players play without explaining solutions.
- [ ] [#130](https://github.com/jlogicgames/edgard_in_kimeria/issues/130) Measure roughly 20-second mastered traversal and 60-second first learning
  separately. Record deaths, waiting, unclear actions and unexplained failures.
  Do not turn the hypotheses into a timer.
- [ ] [#131](https://github.com/jlogicgames/edgard_in_kimeria/issues/131) Check teaching without author hints: controls, danger warnings,
  room objective and the optional route's purpose are understood.
  [D24](DECISIONS.md#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying)
  makes the entire first Forest room the contextual teaching scope. Include a
  participant holding a stone/torch at a lever: activation, placement and throw
  must all be discoverable. Each pickup's placement/throw reminder ends when camera
  following starts after leaving the dead zone; every new pickup in this room
  teaches again. Mechanism teaching stays repeatable independently. Verify both layouts and positions beyond the former
  320×144 area, plus retries/returns and suppression in later rooms.
- [ ] [#132](https://github.com/jlogicgames/edgard_in_kimeria/issues/132) Revise the level and content scope from results. Related existing tasks:
  [#73](https://github.com/jlogicgames/edgard_in_kimeria/issues/73)
  and [#75](https://github.com/jlogicgames/edgard_in_kimeria/issues/75).

**Output:** the cost of creating a coherent level is known, with first observations
of new players. Only then refine room counts, expected duration and production scope
for the remaining stages.

### 2.3. Developing all four stages

- [ ] [#133](https://github.com/jlogicgames/edgard_in_kimeria/issues/133) Plan difficulty progression by level and each room's purpose. New
  content includes situations, enemies and mechanic combinations, not just geometry.
- [ ] [#134](https://github.com/jlogicgames/edgard_in_kimeria/issues/134) Develop Forest and Swamp, then Steppe with double-jump acquisition,
  then Fortress accounting for all learned tools.
- [ ] [#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135) Add levels in small complete batches and repeat external checks,
  especially for new mechanics. Five levels × five rooms is a scope hypothesis,
  not a requirement to fill a grid with repetitive tasks.
- [ ] [#136](https://github.com/jlogicgames/edgard_in_kimeria/issues/136) Place four mandatory tablet fragments and optional ingredients/coins.
  Define where/how each fragment is obtained and how the next objective is understood.
- [ ] [#137](https://github.com/jlogicgames/edgard_in_kimeria/issues/137) Balance income/prices: insufficient coins for every advantage,
  a noticeable elixir/fireball choice and a required path possible without purchases.
- [ ] [#138](https://github.com/jlogicgames/edgard_in_kimeria/issues/138) Design/add bosses, their stage placement and the final encounter.
  Check elixir and unaided paths; do not extend play through endless retries or
  simply adding identical required hits.
- [ ] [#139](https://github.com/jlogicgames/edgard_in_kimeria/issues/139) Write healer meetings and the tablet story's conclusion. Preserve
  established character roles and obtain agreement on new narrative decisions.

**Phase 2 exit:** the campaign is traversable with normal controls from start to
finish, with four fragments and an ending. Both collection-for-help and unaided
paths work. Continue and death restore their distinct intended state boundaries.
First external playtests have happened and their problems are recorded/triaged.
Temporary assets and balance may still require refinement.

<a id="release"></a>

## Phase 3. Completion and release

- [ ] [#140](https://github.com/jlogicgames/edgard_in_kimeria/issues/140) Run full playthroughs with different habits: required path,
  exploration, elixir/fireball purchases and no help. Measure actual duration
  separately from the 100-room calculation.
- [ ] [#141](https://github.com/jlogicgames/edgard_in_kimeria/issues/141) Fix blocking/recurring playtest problems; recheck affected situations
  and the complete route to the extent needed.
- [ ] [#142](https://github.com/jlogicgames/edgard_in_kimeria/issues/142) Finish environment, readability, animation/feedback, music and sound.
  Shared PNG portraits from [#90](https://github.com/jlogicgames/edgard_in_kimeria/issues/90)
  are implemented with the revised 256-color remake/remaster direction; see
  [art direction](ART_DIRECTION.md) and [current artwork review](PORTRAITS.md).
  Recheck their readability in the final campaign/device presentation.
  Select decorative reserve ideas by their benefit to finished situations.
  Includes the polish of the false honey fungus's spore cloud (done, approved by the author after playing the desktop build and checking the shader in a browser:
  `SporeCloud` particles plus `spore_cloud.frag`, the only path; the sprite path is gone, [#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296);
  the trap itself is [#295](https://github.com/jlogicgames/edgard_in_kimeria/issues/295), its static scenery
  look-alikes [#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299), see
  [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md)). Trap warning times are tuned by playtest in
  [#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300) (D7 note; the plan is
  [PLAYTEST_WARNING_TIMES.md](PLAYTEST_WARNING_TIMES.md)).
- [ ] [#143](https://github.com/jlogicgames/edgard_in_kimeria/issues/143) Complete final campaign text and review all five supported languages
  (EN/UK/PL/DE/BE), including native-speaker review of the new translations and final
  device/layout checks. The catalog/font infrastructure from
  [#85](https://github.com/jlogicgames/edgard_in_kimeria/issues/85) is already merged;
  its completion does not complete this release pass.
- [ ] [#144](https://github.com/jlogicgames/edgard_in_kimeria/issues/144) Complete the updated [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md):
  controls/both layouts, desktop/web, real devices, saves, rendering, audio and
  the full loop. Mark checks complete only with evidence for the relevant build.
- [ ] [#145](https://github.com/jlogicgames/edgard_in_kimeria/issues/145) Update current gameplay, design, index and release-readiness information.

**Phase 3 exit:** the selected release scope is finished and checked, significant
problems are resolved and remaining limitations are explicit. Publishing and Git
operations require separate authorization; the roadmap itself does not trigger them.

## Maintaining the roadmap

<a id="github-tracking"></a>

All 50 checkboxes have a separate English GitHub issue: the first reuses #95;
#97–#145 cover the other 49. A checkbox's link identifies its primary issue;
additional links identify related larger tasks or dependencies. Issue creation
does not mean completion.
The more specific Forest, trap research and asset-provenance tasks #186–#189
supplement these existing items rather than replacing their acceptance criteria.

- Mark completion from concrete output, not a partial scaffold.
- Clarify scope/dependencies before starting a block. Give new checkboxes separate
  issues without duplicating existing ones; update both checkbox and primary issue
  when scope changes. Larger existing tasks #94, #73, #75 and #90 have not
  automatically been rewritten to match this plan.
- After prototypes/playtests, update this roadmap and the
  [living design](GAME_DESIGN.md#design-maintenance), keeping decision,
  implementation and validation distinct.
- Add calendar/effort estimates after measuring the first prototype/level.
  The 20/60-second play hypotheses and 100-minute campaign model are not development estimates.

**Next output:** shared actions and ladders are implemented. Finish the remaining
device checks in #101 and build the short Forest-mechanic prototype.
