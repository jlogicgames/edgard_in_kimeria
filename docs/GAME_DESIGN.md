# Edgard in Kimeria — full game design

**Working version — 2026-10-20.** Based on the author's remake decisions,
mechanics notes and repository documents. Discussion and drafts are in Russian;
committed documentation is in English.

This document describes the full game. The existing two-level forest test build
is documented separately in [MVP](MVP.md) and [Gameplay reference](GAMEPLAY.md).
A feature appearing in the design does not mean it is implemented.

The [roadmap](ROADMAP.md) defines delivery order and exit criteria: mechanics MVP
for four stages, campaign production and first external playtests, then release
completion. The design describes the game; the roadmap describes the next output.

**Accepted direction** records author decisions. **Proposals** and **open questions**
are labelled separately and require discussion or testing.

## Working pitch

Morning no longer comes to Kimeria. To bring back the sun, wandering warrior
Edgard must cross an enchanted forest, swamps and steppes, enter the fortress
and recover four pieces of a shattered spell tablet.

**Edgard in Kimeria** is a fairy-tale adventure and arcade puzzle-platformer:
spot an opportunity, then seize the moment. Slip between traps? Lure an enemy
within striking distance? Spend your last fireball to clear the way? Carry a
torch to discover what the darkness hides?

Each stage brings new tricks and tests of wit and reflexes. Bring ingredients
and coins to the healer for a boss-fighting elixir. Seek them out, or head straight
for the boss and trust your skill? Four corners of a sleeping kingdom lie ahead.
Beyond them, the sun must rise.

## Intent and player experience

**Accepted direction:** a remake and remaster of the 1994 game, developing the
original puzzle adventure into an arcade puzzle-platformer. Duration will follow
campaign structure and measured play. “Several hours” is no longer a standalone
promise; the working calculation appears below.

The player studies a dangerous space, understands its obstacles and chooses a
route or solution. They execute it using movement, jumps, melee and ranged attacks,
and environmental interaction. Observation and planning matter alongside timing
and precision.

A puzzle may concern a route, a jump's timing, a way to remove an enemy, or an
object interaction. Item puzzles belong here, but each room need not reduce to
finding one correct item or sequence.

**The author's reference situation:** a bat flies between platforms. Study its
movement and jump at the right moment, wait until it comes within melee range,
or spend a scarce ranged attack to clear the path. Planning and arcade execution
work within the same encounter.

The full game's length should come from developing mechanics, new enemies,
combined obstacles, exploration and optional challenges. Repeating a long,
already-mastered section after every mistake must not substitute for content.

## Visual identity

**Accepted by the author, 2026-10-22:** remake and remaster means a more beautiful,
more natural presentation with richer colors and less grain, preserving the original
artist's style across all future illustrations. The portrait baseline uses 256 colors
and finer source images. Preserve the original character designs, proportions,
silhouettes and warm color family while improving finish and shading. Follow the
[art direction](ART_DIRECTION.md) and [D21](DECISIONS.md#d21-remake-and-remaster-preserving-the-original-artists-style).

## Original game and remake references

- The original supplies the world, characters, eternal-night curse and tablet.
  Keep [historical material](ORIGINAL_DOS.md) as source evidence.
- The author accepts correcting the original design's mistakes and changing the
  journey's structure. DOS limitations and every historical decision need not return.
- **Katana Zero** is the main reference for arcade puzzle-platforming, pace,
  controls, readable dangers and retries; see
  [D1](DECISIONS.md#d1-katana-zero-is-the-design-reference).
  New decisions must also fit Edgard's own tools and intent.
- **The Expendabros** informs short sections between recovery points and retention
  of progress within a run. This does not require a flag graphic at every room boundary.

## World and story objective

Rufardus the Wise rules Kimeria. Banished from a neighboring kingdom, L'Kardina
curses Kimeria with eternal night when its people also try to expel her. Wandering
warrior Edgard hears of the disaster and sets out to save the kingdom.

**Accepted direction:** recover four tablet fragments needed to lift the spell,
one per stage. These are mandatory campaign objectives. Ingredients and coins
serve a separate, optional purpose: the healer's help against bosses.

The healer remains Edgard's narrator and recurring guide. He connects events,
offers advice and prepares elixirs. His role continues if the player skips
collection. Established facts, appearances and visual references are in
[STORY.md](STORY.md) and [ORIGINAL_DOS.md](ORIGINAL_DOS.md).

**Open:** fragment locations, their relationship to boss victories, how the
assembled tablet is used and L'Kardina's final fate. A proposed ritual or new
plot must not be presented as an accepted decision.

<a id="campaign-structure"></a>

## Stages, levels and rooms

**Accepted hierarchy: campaign → stages → levels → rooms.**

| Scale | Purpose |
| --- | --- |
| Stage | A large region with its own atmosphere and distinctive leading mechanic. Contains several levels; its objective is a tablet fragment. |
| Level | A step in progression: greater difficulty, new enemies, obstacles or applications of a mechanic. Contains rooms. |
| Room | A short section for learning and developing a skill through repetition and variation. The death-recovery unit; may span several screens. |

**Accepted remake route: Forest → Swamp → Steppe → Fortress.** This replaces the
historical Forest → Castle → Steppe → Fortress route. Castle and Fortress were
poorly differentiated; Swamp makes the journey more varied. “Castle” in the earlier
campaign plan is not an additional fifth stage.

Each stage needs a leading mechanic. Edgard's shared tools remain available;
the new mechanic creates new situations for using them.

| Stage | Material for further design | Status |
| --- | --- | --- |
| Forest | Levers, lowered ladders, carrying objects and acting on obstacles. | Ideas from the author's notes; leading mechanic not selected. |
| Swamp | Quicksand, unreliable footing, bridges, moving and falling platforms. | Ideas from the notes; distinguish its problems from forest switches. |
| Steppe | Wind as a spatial property: gusts, shelter, effects on movement and objects. | Wind accepted; directions, strength, cycles and interactions need design. |
| Fortress | Carrying, placing and throwing a torch; light affects visibility and creatures. | Preferred prototype direction; complete rules not approved. |

The torch was previously considered for Steppe. Wind is now selected there;
light remains the proposed Fortress mechanic rather than a mechanic for every stage.

**Author's working structure:** five levels per stage, five rooms per level.
Validate this planning model with authored levels. Distribution of new elements,
boss roster and boss placement remain open.

<a id="pacing-and-duration"></a>

### Pace, duration and cost of failure

Measure these separately:

- **Mastered successful traversal:** one attempt after learning a room. The accepted
  working hypothesis is **about 20 seconds between recovery points**, superseding
  the intermediate 40-second target. Do not stretch shorter rooms to fill 20 seconds.
  Measure required and collection routes separately; redesign or split a long route
  rather than excluding it from evaluation.
- **First learning:** observation, finding a solution and attempts until the first
  successful exit. The accepted hypothesis is **about 60 seconds per ordinary room**.
  This includes the successful traversal; do not add another 20 seconds. Difficult
  and optional challenges may take longer and need separate assessment.
- **Full playthrough:** room learning, exploration, dialogue, transitions and bosses.
  Do not count a boss twice if its fight is already included in a room measurement.

| Working model | Rooms | Mastered traversals at 20 s | First learning at 60 s |
| --- | --- | --- | --- |
| One level | 5 | 1 min 40 s | 5 min |
| One stage: five levels | 25 | 8 min 20 s | 25 min |
| Campaign: four stages | 100 | 33 min 20 s | 100 min / 1 h 40 min |

This is arithmetic, not a duration promise. Add separate dialogue, exploration,
optional routes and fights only when room timings have not already included them.
The earlier 5–8 h 20 min estimate assumed the rejected 3–5 minutes of learning per
room and no longer defines the planned campaign length.

**20/60** is Edgard's starting hypothesis, not an established average for Celeste,
TetherGeist or the genre. A personal Celeste account estimates ordinary death costs
at roughly up to 30 seconds; TetherGeist's developers describe frequent checkpoints
and instant retries. Neither establishes a 60-second average for first learning.

The checked online material does not establish “3–5 minutes per room” for Katana
Zero or The Expendabros. Chapter timestamps in a player's deathless Katana Zero
recording suggest roughly 50 seconds for all of Factory and 78 for all of Hotel.
These are mastered chapter runs, not average first room attempts. Only the indexed
description was inspected; the video was not timed. See [sources](#research-references).

**Proposed validation:** build a coherent five-room level in the
[campaign phase](ROADMAP.md#campaign-and-playtests), after short mechanics-MVP
prototypes. The MVP need not contain a five-room level for every stage.
Record mastered traversal, first learning, deaths, waiting and optional routes
separately, then revise campaign scope. Never fill a timing target through forced
waiting or required deaths. A minute is a learning guide, not a deadline or failure
timer. Discuss deviations using evidence rather than mechanically speeding up or
slowing down every room. If a single attempt actually lasts several minutes,
reconsider recovery boundaries explicitly rather than silently abandoning cheap death.

The author recalls the nearly unbeatable witch in the original fortress basement,
on its fifth level, extending play through frustrating retries. The remake must
correct this. The new campaign's final-fight placement is not approved. Test readable
attacks, victory without an elixir and retry cost separately; a prolonged deadlock
is not game content.

<a id="wind-and-ladders"></a>

### Wind, ladders and movement progression

**Accepted:** vertical ladders, adding choices of height, route and timing of
ascent/descent. Ladders do not replace every jumping challenge. Entry, exit,
attacking and carrying rules on a ladder remain to be defined.

**Accepted:** wind is Steppe's leading mechanic. **Prototype proposals:**

- Gust cycles visible in grass, dust or ribbons, with sheltered waiting places.
- A following gust helps cross a gap; headwind changes the chosen timing.
- Wind carries a thrown light object differently from a heavy stone.
- A shutter or mechanism position changes the available airflow.

This is not an approved complete interaction set. Wind should offer readable
solutions rather than only slow the hero. Effects on fireballs and sword attacks
are not decided. Celeste is the author's example; LostWinds uses wind for jumps,
enemies and puzzles, while The Wind Waker uses gusts to move objects and wind for
gliding. These references do not approve flight or direct wind control for Edgard.

**Accepted direction:** double jump becomes available starting in Steppe. Rooms
from there onward account for it, teaching second-jump timing and combinations
with wind. Forest and Swamp do not require it. Unlock method, returning to earlier
stages and its use while carrying/on ladders remain open. A route designed around
this skill must not treat it as an arbitrary optional purchase. Decide acquisition
before authoring dependent routes.

**Implementation correction:** wall-clamber/jump already exists in `Player` and
is used by forest replay tests. Earlier discussion incorrectly assumed it still
needed adding. Its role in the full campaign remains open: retain its behavior,
and how should it combine with ladders, wind and double jump? Do not describe it
as absent or remove it silently; changes need a separate decision and route checks.
It can fit puzzles when rooms account for it. A purchasable higher jump requires
separate geometry validation; double jump is already selected as Steppe progression.

<a id="player-tools"></a>

## Edgard's tools and controls

Use capability names in the design: **movement, jump, melee attack, ranged attack,
environmental interaction and carried-object interaction**. Sword and fireball
are concrete forms of the attacks.

| Capability | Role | Status |
| --- | --- | --- |
| Movement and jump | Positioning, avoiding danger, precision and timing between supports. | Implemented in the test build. |
| Melee | Remove an enemy from the right position at the right moment. | Sword implemented; current limits in D4/D10. |
| Ranged attack | A consumable advantage against distant danger. | Accepted direction; button exists, attack not implemented. |
| Interaction | Operate a lever or another mechanism. | Context-sensitive attack/interact button exists. |
| Pick up and place | Move a stone or torch to change the room situation. | Same interaction button intended; carrying needs implementation/design. |
| Throw an object | Affect something distant or place an object ahead. | Planned. |
| Vertical ladder | Change elevation, choose a route and movement timing. | Accepted addition; detailed controls open. |
| Double jump | An extra airborne jump used in authored routes and wind tasks. | Accepted from Steppe; unlock and exact rules open. |

The current scheme has three action buttons: jump, attack/interact and ranged.
Keyboard, gamepad, touch and left-handed bindings are in
[GAMEPLAY.md](GAMEPLAY.md#controls).

Currently the sword cannot be used in the air; ordinary enemies can be killed
by stomping from above. Preserve [D4](DECISIONS.md#d4-stomp-is-allowed-from-above-only)
until a separate change is accepted. Future throwing/fireball descriptions do not
implicitly change these existing rules.

**Accepted carrying and throwing rules:**

- Carry **one object in one hand**; sword melee remains available.
- Pick up/place uses interaction; throwing uses the ranged button.
- With an object held, ranged throws it. Empty-handed, it uses a fireball if ammunition
  is available. Throwing a stone or torch does not spend fireball ammunition.
- A throw must not also fire a fireball on the same press after the hand becomes
  empty. This separates resource expenditure; it does not forbid multiple linked
  effects from a mechanism interaction.

These rules are not implemented. Action selection is accepted below; exact target
selection and actions allowed on ladders remain open. Carrying alone does not
prohibit normal movement or jumping; no such extra restrictions were accepted.

### Making the shared button understandable

A shared button risks picking something up when the player wants to attack, or
spending scarce ammunition when they meant to throw a stone. Distinguish the
action-selection rule from how it is displayed: the rule is accepted, hint visuals
remain open.

**Accepted scheme, [#95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95):**

| Input | Action |
| --- | --- |
| Action without an interaction direction | Sword attack, including while carrying. |
| Down + action | Pick up or place an object. |
| Up + action | Operate the selected lever/mechanism. |
| Ranged while holding an object | Throw it. |
| Ranged with an empty hand | Fireball, if ammunition is available. |

Test directional conflicts with ladders and the touch stick, both handedness
layouts, and unambiguous target selection. This is approved for implementation;
the test build's current controls have not changed yet.

An earlier alternative used a highlighted nearby object and a labelled contextual
action, as in Zelda. It still conflicts with Edgard's sword during combat; a label
alone does not resolve intent. Do not infer intent from a hidden “enemy nearby” rule.

Comparisons: Super Mario World combines running/attacks and carrying on one button,
holding to carry and releasing to throw. Ocarina of Time labels its contextual
action and keeps the sword separate. Spelunky 2 uses down + attack to pick up/place.
Super Mario 3D World also uses hold/release for carrying/throwing. Edgard need not
copy release-to-throw: the author selected a separate throw command. Sources below.

Possible feedback includes a short device-appropriate prompt at one selected object,
target highlighting and an updated touch-button label/icon. Separately show when
ranged will throw rather than spend ammunition. Exact presentation is unapproved.

Preventing repeated activation means a held button after pickup must not immediately
place the item again. It is not a universal “one button can have only one consequence”
rule: a lever may operate several linked objects. Define fresh-press handling for
new actions during implementation.

## Ranged attack as a scarce resource

**Accepted direction:** ranged attacks are consumable rather than unlimited free
shooting. The author's reference is an item picked up and used once, like throwable
objects in Katana Zero.

The working stock is **three uses per entire stage, with very rare refills**.
A stage containing several levels does not imply resetting to three every level.
Validate final quantities and refill placement on real levels.

Required routes must remain possible without consumable ranged attacks. The player
chooses where to spend an advantage; empty ammunition must not block progress.
A thrown stone or torch need not share this economy; define each object's rules.

**Accepted:** death restores ammunition with room state. Uses spent in a failed
attempt return, so a retry does not become harder because of the previous mistake.
See the recovery boundary below.

<a id="collection-and-elixirs"></a>

## Collection and elixirs

| Collectible | Required? | Purpose |
| --- | --- | --- |
| Four tablet fragments | Required to complete the campaign. | Lift eternal night; one per stage. |
| Ingredients | Optional. | Healer elixirs that shorten boss fights. |
| Coins | Optional. | Payment for elixirs and, in the planned campaign economy, extra fireballs. |

**Accepted choice:** exploration and collection lengthen the route to a boss but
shorten the fight. Skip collection to reach it sooner and fight longer. An elixir
is not a condition for victory or story progress.

Balance compares the effort of both paths: optional challenges for help versus
fighting without it. A strong collection reward fits this intent.

The original's seven remedy components helped lift the curse. In the remake's
accepted direction, the tablet takes that mandatory role and ingredients offer
help along the way. Seven does not become a mandatory future recipe size.

The existing forest recipe is Chicken + Key + all six unique level coins. Its
elixir multiplies sword damage by ten, reducing required hits from twelve to two.
These are current test-encounter values in [HEALER.md](HEALER.md) and
[BOSS.md](BOSS.md), not universal campaign values.

**Open:** stage/boss recipes, ingredient counts, fees, strength, returns for missing
items and each elixir's duration/scope. This document does not change the current
effect-retention rules on boss retries.

<a id="economy-proposals"></a>

### Economy: accepted direction and balancing

**Accepted direction:** coins are limited; the player chooses between an elixir
and extra fireballs. Full collection should not create enough surplus to buy
everything without a choice. Ingredients enable elixirs; coins pay for help and
ammunition. Exact prices, income and limits remain open.

Use coins and ingredient items. A separate crystal currency is not selected.
The earlier surplus-coins proposal was not accepted. Higher jump and two rapid
strikes remain candidates; double jump is already selected as progression from
Steppe, rather than a freely purchased upgrade.

One currency creates a choice between simplifying a section with a fireball and
saving for the boss. Test the full-collection budget too: if it buys the entire
offering, revise income, prices or stock.

Define required/optional-route income, elixir price, ammunition price/purchase
limit and carrying cap per stage. Freely buying ammunition would undo “three per
stage”; explicitly balance total stock including purchases. The current MVP's
“all unique level coins” recipe does not automatically become the full-game shop rule.

Mobility changes the solution space: higher jumps can bypass supports and levers.
Rapid strikes change safe windows and fight duration. **Proposal:** test consumable
purchases against individual dangers first; consider those upgrades only with
separate authoring and checks of affected routes. They are not added capabilities;
Steppe/Fortress geometry must already account for the accepted double jump.

Purchases and refills must agree with room rollback. Death must not refund payment
while keeping purchased ammunition or allow farming repeatable coins. Specify
transaction boundaries and Continue behavior before implementation. The current
elixir encounter is unchanged and has no extra sales yet. A one-ammunition-purchase
limit per stage remains an example to test, not an accepted number.

<a id="death-and-saves"></a>

## Death, recovery and saves

Keep one-hit death, unlimited attempts and quick recovery at the current room's
entrance. Earlier rooms' progress remains secured within the run. Death and
continuing after closing the application have different recovery boundaries.

| Event | Rule |
| --- | --- |
| Death in a room | Restore its retry state, including ranged stock. Roll back this attempt's pickups; retain earlier secured progress. |
| Successful room exit | Secure results; remaining stock becomes the next room's starting stock. A room transition is not a persistent save. |
| Level completion | Automatically save the next level. |
| Continue after closing the game | Start the saved level from its beginning. Currently only the level index is saved. |
| Restart Level | Restore level-entry state under the existing contract, distinct from a room retry. |

A failed attempt rolls back ammunition expenditure and collection together with
refill-source state, preventing duplicated resources. A healer collection return
uses its corresponding secured boundary in [HEALER.md](HEALER.md#state-policy),
rather than unconditionally resetting the entire previously traversed level.

Updated [D3](DECISIONS.md#d3-death-resets-the-current-room-and-respawn-is-instant)
and [ROOMS.md](ROOMS.md#replay-budget-70) target roughly 20 seconds of mastered
traversal between recovery points. This authoring replay budget is not a player
timer. It supersedes the intermediate 40 seconds; preserve historical measurements
with their original conditions. First learning has a separate roughly 60-second
hypothesis, including observation and attempts.

Current persistence is documented in
[D8](DECISIONS.md#d8-main-menu-has-continue-and-new-game) and
[DEVELOPMENT.md](DEVELOPMENT.md#game-state-progress-and-saves).
It does not save inventory or elixir. Stage → level → room hierarchy alone does
not require mid-level saves or saving only at stage boundaries.

**Open for the campaign:** persistent tablet progress and how Continue restores
resources budgeted across several levels. Whether progress can be reconstructed
from the reached level or needs a format extension is a separate decision.
This design does not claim inventory persistence is implemented.

## Rooms, dangers and skill development

A room should provide a short meaningful section for observation and execution.
Within a level, familiar mechanics recur under different conditions to develop
skill; the next level increases difficulty or adds an element. A stage develops
its leading mechanic over several levels.

Solutions can involve avoidance, timing, melee/ranged attacks, mechanisms,
carrying or throwing. Every room need not support every option.

Preserve readable danger under [D5](DECISIONS.md#d5-hits-must-be-fair) and
[D7](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements): the player understands
what killed them and can apply that knowledge on retry. New traps keep Assist
support for longer warnings.

**Authoring proposals to test:** a safe observation position; light, sound and
movement as clues to hidden dangers; an introduction → variation → combination →
final challenge progression. These are techniques, not a mandatory room template.

## Idea reserve

These ideas from the author's notes and discussion remain candidates, not a promise
to implement everything.

| Group | Possible elements |
| --- | --- |
| Mechanisms | Levers/plates opening doors, lowering ladders/bridges or starting platforms; throwing at a distant lever. |
| Supports and weights | A support shaking before it falls; a dropped platform/stone affecting an obstacle or enemy. Activation and damage remain undefined. |
| Traps | Arrows and deflecting them; falling rocks; cutters; electrical discharges; quicksand. |
| Light and creatures | A removable wall torch; throwing it to light the path; insects attracted to light; spiders repelled by it; eyes in darkness. |
| Other items | Protection from one danger type; bottles with different effects; incendiary mixture. Inclusion and readable properties undecided. |
| Movement and combat | Looking up/crouching; visible enemy windups; enemies reacting to the hero's arrival. Vertical ladders are already accepted above. |
| Atmosphere and feedback | Parallax, fireflies, fog, landing/mechanism dust, moving bushes/vines and restrained camera shake. |

This list does not establish implementation status: old checklist marks refer to
different project versions. Check current behavior against [GAMEPLAY.md](GAMEPLAY.md)
and [feature parity](DEVELOPMENT.md#feature-parity). Candidates such as fog or hit
protection may require explicitly revising previous decisions.

## Open decisions and the existing build

Further discussion must specify:

1. Forest, Swamp and Fortress mechanics, plus concrete Steppe wind rules.
2. Validation of five levels × five rooms per stage, pacing, bosses and fragment acquisition.
3. Implementing #95, ladder actions, double-jump acquisition and the existing wall jump's campaign role.
4. Ranged stock, rare refills and restoration after Continue.
5. Elixir/fireball prices, coin income, recipes, purchase limits and collection/return boundaries.
6. Persistent tablet progress and the story's conclusion.

The test build retains its explicit contracts in [MVP.md](MVP.md),
[DECISIONS.md](DECISIONS.md), [ROOMS.md](ROOMS.md), [HEALER.md](HEALER.md) and
[BOSS.md](BOSS.md). Its two-level limits do not constrain the full campaign's intent.
Accepting a design does not mean current maps and mechanics have changed.

When implementing a campaign feature, explicitly reconcile the relevant contracts
and update status. Earlier checks do not validate new mechanics. The active release
checklist is [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md); use the [index](README.md)
for topic navigation.

<a id="design-maintenance"></a>

## Maintaining the design during development

**This is a living document**, updated through discussion and development.

1. Record accepted author decisions in the relevant section. Clearly label proposals,
   research findings and unselected alternatives.
2. If implementation or testing changes a rule, update this design and the topic
   document alongside that work. An accidental code behavior is not an accepted decision.
3. Record consequential decisions in [DECISIONS.md](DECISIONS.md), including date,
   rationale, scope and a link to the full description here.
4. [GAMEPLAY.md](GAMEPLAY.md) describes available behavior. Do not mark wind, carrying,
   ladders or a shop implemented before they exist.
5. Add documents/topics to the index and check relative links/headings. Keep earlier
   validation reports as history. Update [ROADMAP.md](ROADMAP.md) when order, scope
   or exit criteria change; prototype/playtest evidence refines the plan.
6. Revise duration and content estimates after measurement. Separate targets,
   measured results and author impressions.
7. Discussion and drafts are Russian; prepare documentation in English before
   committing. A commit requires the author's explicit authorization.

**2026-10-20 clarification sequence:** accepted Steppe wind, ladders, one carried
object, sword availability while carrying and a separate throw on ranged. The 5 × 5
model needs measurement. Then accepted #95 controls, double jump from Steppe,
limited coins with an elixir/fireball choice and a roughly 40-second retry target.
The latest clarification replaces both 40 seconds and the earlier 3–5-minute learning
assumption with **about 20 seconds mastered / about 60 seconds first learning**.
This is a testable hypothesis (D19), not verified genre statistics. Shop numbers and
exact skill rules remain open.

<a id="research-references"></a>

## Reference research

Checked 2026-10-20. Examples inform decisions; they do not decide for the author or
establish average room durations.

- [Justin Stander interview](https://www.gamerevolution.com/originals/520685-katana-zero-interview):
  Katana Zero's creator connects sustained interest with new enemies, mechanics,
  situations and story; it does not establish a 3–5-minute room norm.
- [Deathless Katana Zero recording description](https://steamcommunity.com/sharedfiles/filedetails/?id=1782557503):
  Factory 00:37 → Hotel 01:27 → Club 02:45 implies 50 and 78 seconds for the first
  two chapters in this run. One fast run cannot establish average first-play time.
  Indexed description inspected; video playback/frame timing not performed.
- [The Expendabros publisher description](https://store.steampowered.com/app/312990/The_Expendabros/):
  ten missions; no average room or flag-to-flag duration stated.
- [LostWinds developer description](https://store.steampowered.com/app/447780/LostWinds/):
  wind supports jumps/gliding, affects enemies and helps solve puzzles.
- [The Wind Waker HD Nintendo manual](https://www.nintendo.com/eu/media/downloads/games_8/emanuals/wii_u_6/thelegendofzelda_windwaker_hd/ElectronicManual_WiiU_TLOZWWHD_EN.pdf):
  the leaf creates gusts that move enemies/objects and uses wind while airborne.
- [Super Mario World Nintendo manual](https://www.nintendo.co.jp/clvs/manuals/common/pdf/CLV-P-SAAAE.pdf):
  shared Y actions, holding to carry and release-to-throw.
- [Ocarina of Time Nintendo manual](https://m1.nintendo.net/docvc/NUS/USA/CZLE/CZLE_E.pdf):
  contextual action labels and a separate sword button.
- [Super Mario 3D World Nintendo manual](https://csassets.nintendo.com/noaext/image/private/t_KA_PDF/manual-WiiU-Super_Mario_3D-World):
  hold/release carrying/throwing, with the fire attack documented separately.
- [Spelunky 2 item-control guide](https://attackofthefanboy.com/guides/spelunky-2-how-to-pick-up-drop-use-and-throw-items/):
  down + attack picks up/places; a third-party guide, not developer documentation.
- [Lies of P Xbox demo impressions](https://news.xbox.com/en-us/2023/06/08/lies-of-p-demo-hands-on-impressions-souls/):
  the author clarified this was the intended Korean puppet game. Boss-lost currency
  appears outside the arena, easing retrieval but unlike Edgard's instant room retry.
  Currency loss and retrieval punishment are not added to Edgard's design.
- [Personal Celeste playthrough analysis](https://kaikuehner.com/blog/difficulty.html):
  estimates ordinary-screen death costs at roughly up to 30 seconds, distinguishing
  later challenges. One player's experience, not a measured average/developer standard.
- [TetherGeist developer interview](https://somanygames.co.uk/articles/tethergeist-developer-interview/):
  frequent checkpoints and instant retries avoid long replays; harder rooms/secrets
  are optional. No average room duration or one-minute learning figure is given.
