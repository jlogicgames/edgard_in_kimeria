# Edgard in Kimeria — development roadmap

**Working version — 2026-10-20.** Discussion and drafts are in Russian; committed
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

- [ ] [#95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95) Implement the accepted control scheme: plain action attacks; down + action
  picks up/places; up + action operates a mechanism; ranged throws a held object
  or fires a fireball with an empty hand.
- [ ] [#97](https://github.com/jlogicgames/edgard_in_kimeria/issues/97) Implement carrying one object in one hand, with the sword still available,
  plus placing and throwing. A stone is sufficient here; add the torch in Fortress.
- [ ] [#98](https://github.com/jlogicgames/edgard_in_kimeria/issues/98) Implement consumable ranged attacks and rare refills. Separate throwing
  from fireball expenditure; a single press must not perform both.
- [ ] [#99](https://github.com/jlogicgames/edgard_in_kimeria/issues/99) Implement vertical ladders. Define entry, ascent/descent, exit,
  permitted actions and interaction priority near a ladder.
- [ ] [#100](https://github.com/jlogicgames/edgard_in_kimeria/issues/100) Check death/recovery with held objects and ammunition: no failed-attempt
  stock loss, duplicated pickups or resource accumulation through retries.
- [ ] [#101](https://github.com/jlogicgames/edgard_in_kimeria/issues/101) Check keyboard, gamepad and touch in both handedness layouts. Hints show
  the selected action/target. Check held buttons after pause, dialogue and transitions.

**Output:** in a test section, move an object, operate a mechanism, fight while
carrying and deliberately choose a throw or fireball. Ladders and contextual action
must not interfere. Preserve existing jumping, combat and recovery behavior.

### 1.2. Forest

- [ ] [#102](https://github.com/jlogicgames/edgard_in_kimeria/issues/102) Select Forest's leading mechanic from levers/linked objects, lowered
  ladders and carrying/throwing to affect obstacles.
- [ ] [#103](https://github.com/jlogicgames/edgard_in_kimeria/issues/103) Build short rooms demonstrating the rule and several applications.
- [ ] [#104](https://github.com/jlogicgames/edgard_in_kimeria/issues/104) Combine it with an enemy or trap. Test choices between waiting,
  melee, spending a ranged attack and environmental interaction.

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
  develop it, combine it with dangers and offer an optional route.
- [ ] [#129](https://github.com/jlogicgames/edgard_in_kimeria/issues/129) Run first external playtests after the mechanics MVP, without waiting
  for all 100 hypothetical rooms. Let new players play without explaining solutions.
- [ ] [#130](https://github.com/jlogicgames/edgard_in_kimeria/issues/130) Measure roughly 20-second mastered traversal and 60-second first learning
  separately. Record deaths, waiting, unclear actions and unexplained failures.
  Do not turn the hypotheses into a timer.
- [ ] [#131](https://github.com/jlogicgames/edgard_in_kimeria/issues/131) Check teaching without author hints: controls, danger warnings,
  room objective and the optional route's purpose are understood.
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
- [ ] [#143](https://github.com/jlogicgames/edgard_in_kimeria/issues/143) Complete text and verify EN/UK. Extra languages in
  [#85](https://github.com/jlogicgames/edgard_in_kimeria/issues/85) remain a separate
  existing task; decide their inclusion in a specific release.
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

- Mark completion from concrete output, not a partial scaffold.
- Clarify scope/dependencies before starting a block. Give new checkboxes separate
  issues without duplicating existing ones; update both checkbox and primary issue
  when scope changes. Larger existing tasks #94, #73, #75, #90 and #85 have not
  automatically been rewritten to match this plan.
- After prototypes/playtests, update this roadmap and the
  [living design](GAME_DESIGN.md#design-maintenance), keeping decision,
  implementation and validation distinct.
- Add calendar/effort estimates after measuring the first prototype/level.
  The 20/60-second play hypotheses and 100-minute campaign model are not development estimates.

**Next output:** complete #95 and a test section with a stone, mechanism, throwing,
consumable ranged attack and correct recovery. Then verify ladders and begin the
short Forest-mechanic prototype.
