# Edgard in Kimeria — full game design

**Working version — 2026-10-24.** Based on the author's remake decisions,
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

**Principle (D84, 2026-11-17):** the player is never tormented by guesswork, and a puzzle never makes them look stupid.
The working reading, to be confirmed by the author: a new rule or combination is shown safely before it gates
progress, the effect of an action is visible when it happens (or the game points at it), and difficulty comes from
execution and planning, not from hidden rules. Details: [D84](DECISIONS.md#d84-a-puzzle-never-makes-the-player-guess-or-look-stupid-playtest-418-419-2026-11-17).

A puzzle may concern a route, a jump's timing, a way to remove an enemy, or an
object interaction. Item puzzles belong here, but each room need not reduce to
finding one correct item or sequence.

**The author's reference situation:** a bat flies between platforms. Study its
movement and jump at the right moment, wait until it comes within melee range,
or spend a scarce ranged attack to clear the path. Planning and arcade execution
work within the same encounter.

<a id="observe-plan-and-commit-d26"></a>

### Observe, plan and commit (D26)

The player should be able to pause mentally, read the room, and understand its
obstacles before committing to a jump or interaction. This is the campaign's
puzzle-platforming rhythm: observation creates a plan, and precise action tests it.
One-shot supports, switched mechanisms and relocated tools can remain changed after
secured progress. A later search for a secret or ingredient may therefore differ
from the first pass. The player may knowingly spend an optional route opportunity;
that consequence adds choice and makes repeat exploration more interesting.

This is a level-design rule, not a reset shortcut. From every reachable secured
state produced by supported player actions, every mandatory exit, boss and required
tablet fragment must stay reachable without a forced Restart Level.
An optional secret or ingredient may be missed until Restart Level. The behavior
and consequence must be readable before an irreversible action; a hidden trap does
not become fair just because the player can reset. Test the changed route after the
world has been secured, and test that a fresh Restart Level restores the authored
opportunity. See the canonical [D26](DECISIONS.md#d26-observe-plan-before-committing)
and [room-authoring contract](ROOMS.md#puzzle-platformer-authoring-contract-d26).

Paying the full-level reset cost for a missed optional reward is an intentional
consequence, not the ordinary death loop. Keep death retries short under the existing
20-second mastered replay target; do not make long repeated sections the price of
every mistake or use that target to erase a deliberate optional choice.

Room changes belong to the live Level: forward exits and healer entry/collection
returns secure them, revisit death rolls back to the latest secured state, and a
first visit starts authored. Restart Level restores level-entry state under its
existing inventory contract. Continue after closing the game still begins at the
saved level's start; D26 adds no disk save for mutable objects.

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

The Forest opens with a **tutorial level**, `forest-0`: six rooms (short: 576, 896, 784, 800, 736 and 720 px; since D48 the four middle rooms each also hold one forest trap) with no monsters, hints
shown by the map wherever the author places them, and one new element per room (lever,
ladder/bridge, wall, moving platform, thrown stone, existing traps). `forest-2` shows no tutorial hints and `forest-1` one, in its lever room (D131). Status: the lever room is built (a dark torch the lever lights and
darkens, see [D34](DECISIONS.md#d34-the-tutorial-lever-lesson-rule)); the ladder room is built too (a lever lowers a retracted ladder to a ledge holding the exit, see [D35](DECISIONS.md#d35-the-tutorial-ladder-lesson-rule)); the wall room is built (a lever raises and lowers a wall that blocks the corridor, see [D36](DECISIONS.md#d36-the-tutorial-wall-lesson-rule)); the platform room is built (a lever starts and stops a moving platform that carries the hero up to the ledge with the exit, see [D37](DECISIONS.md#d37-the-tutorial-moving-platform-lesson-rule)); the stone room is built (the hero picks up a stone and throws it across a chasm to operate a lever on the far side, which starts the moving platform over the chasm, see [D39](DECISIONS.md#d39-the-tutorial-thrown-stone-lesson-rule)); the traps room is built (a falling platform bridges a pit, then an exploding stump that replaced the contact bomb in #233, each warning before it hurts; a mistake is one room retry, see [D40](DECISIONS.md#d40-the-tutorial-traps-lesson-rule) and [D47](DECISIONS.md#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root): the exploding stump and root also light from afar by a fireball or thrown stone, and always have a floor bypass), so the tutorial sequence is complete. [D48](DECISIONS.md#d48-traps-in-the-tutorial-rooms-one-new-trap-per-lesson-room) then added one forest trap to each of the middle four rooms (a thorn root before the ladder lever, a falling branch before the wall lever, a falling stone before the platform lever, and an exploding stump in the stone room that the thrown stone can light from afar or the hero can jump over), each with its own hint; the lever room stays pure. The healer frames it with two short fairy-tale dialogues, at the start of a new game and before leaving for forest-1, see [D41](DECISIONS.md#d41-the-healers-tales-around-the-forest-0-tutorial). See [D33](DECISIONS.md#d33-the-forest-0-tutorial-level-and-map-authored-hints).

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
| Forest | Reconfigure the route through visible linked mechanisms: levers change traversal supports such as ladders, bridges, platforms and obstacles. | Accepted direction. The lever introduction is built in `forest-0` (#186); the optional-route variation is built as a second room of `forest-1` (#187, [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15): one lever lowers a ladder and stops the lift that is the mandatory way, and a one-shot falling step guards a fireball refill; automated tests, played by the author on 2026-11-17); the combination with an enemy is built as a third room (#104, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15): a RedMob in a tunnel under a spore cloud, and a roof route with a refill; automated tests, played by the author on 2026-11-17). |
| Swamp | Quicksand, unreliable footing, bridges, moving and falling platforms. | Ideas from the notes; distinguish its problems from forest switches. |
| Steppe | Wind as a spatial property: gusts, shelter, effects on movement and objects. | Wind accepted; directions, strength, cycles and interactions need design. |
| Fortress | Carrying, placing and throwing a torch; light affects visibility and creatures. | Preferred prototype direction; complete rules not approved. |

The torch was previously considered for Steppe. Wind is now selected there;
light remains the proposed Fortress mechanic rather than a mechanic for every stage.

Candidate visual effects per stage (plan, not built, [#396](https://github.com/jlogicgames/edgard_in_kimeria/issues/396)): Swamp [#399](https://github.com/jlogicgames/edgard_in_kimeria/issues/399), Steppe [#400](https://github.com/jlogicgames/edgard_in_kimeria/issues/400), Fortress [#401](https://github.com/jlogicgames/edgard_in_kimeria/issues/401);
see [ART_DIRECTION.md](ART_DIRECTION.md#effects-from-pixelfx-plan-2026-11-16-396).

### Forest: linked route reconfiguration

**Accepted direction (2026-10-26):** Forest's leading mechanic is changing the
configuration of a route through visible links between a mechanism and its traversal
supports. A lever is not the goal by itself: the player reads what it changes, then
chooses when to lower a ladder, move a bridge or platform, or alter an obstacle.
The result should create route and timing decisions rather than a sequence of identical
nearby lever presses.

Ladders, carrying and throwing remain shared tools. They may vary a Forest situation
-- for example, an object may make a distant or obstructed mechanism usable -- but they
are not separate required Forest mechanics. In particular, portable light remains the
distinct proposed leading mechanic for Fortress. The existing toggle ladder is a useful
technical atom for this direction, not yet evidence that the mechanic has enough variety.

A lever that drives more than one mechanism must be taught first, in a safe room, before it gates a route.
`forest-1-route` broke this (playtest 2026-11-17); the teaching room `forest-1-lever` now stands before it: one lever, a lift and a ladder in opposite
states, both in view at the pull, a wrong pull free ([D86](DECISIONS.md#d86-forest-1-gets-a-lever-lesson-room-one-lever-a-ladder-and-a-lift-in-opposite-states-418-2026-11-17),
[D84](DECISIONS.md#d84-a-puzzle-never-makes-the-player-guess-or-look-stupid-playtest-418-419-2026-11-17)). Built with automated tests, and the author played it on 2026-11-17 (desktop; see D86).
When such a lever is selected or pulled, the fairy guide marks every mechanism it drives: those in view keep a clear glow for 1.5 s after the pull,
and one outside the camera view sends her to the screen edge in its direction, where she twinkles, then she returns to Edgard; with several outside the view she shows each in turn, 0.8 s at each edge point, for as long as Up is held at the lever and, after a pull, long enough to show every one
([D87](DECISIONS.md#d87-the-fairy-points-at-a-linked-mechanism-that-is-off-screen-and-the-pulled-levers-mechanisms-stay-marked-419-2026-11-17), [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419), [D87 update](DECISIONS.md#d87-the-fairy-points-at-a-linked-mechanism-that-is-off-screen-and-the-pulled-levers-mechanisms-stay-marked-419-2026-11-17), [#434](https://github.com/jlogicgames/edgard_in_kimeria/issues/434)).

The Mechanics MVP should test an introduction to one visible link, a route choice with
a legible optional consequence, and a combination with a familiar enemy or hazard.
Under D26, every mandatory exit, boss and required fragment must remain reachable from
each secured state produced by supported actions; an optional reward may become
unavailable until Restart Level. Remote or throw activation, one-shot versus reversible
supports, and the recovery rules for each new linked object remain open until a prototype
defines them. (One-shot versus reversible is resolved: see the route variation below.)

**Route variation, built (#187, [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15), 2026-11-15; automated tests, and the author played it on 2026-11-17):**
`forest-1` gained a second room after its trap course. One lever drives two supports in
opposite states: a ladder (raised at the start) and a vertical lift (running at the start). The
lift is the mandatory way to the checkpoint and needs no lever; pulling the lever lowers the
ladder to a slab and stops the lift where it is, so the optional detour has a visible cost until
the lever is pulled again. The single-charge fireball refill (D27) lies on a pad above the slab that
only a **one-shot step** (an existing falling platform, seen collapsing in room 1) can reach: spend
the step without taking the refill and the reward is lost until Restart Level, while the lift route
to the checkpoint stays open from every state (D26, swept in ROOMS.md). **This answers "one-shot
versus reversible":** levers stay reversible, the one-shot is a support that guards an optional
reward. Remote or throw activation is not used here. The mastered route is about 11 s, the refill
detour brings the whole room to about 18 s. A **Bat** (author's request) patrols the floor stretch before the lift: the
sword, a hop or a fireball from afar deals with it, so zero fireballs are enough, but a hero who
shoots it can take the refill back; that gives the refill a use even at a full stock. The #104 room that
follows offers a fireball as another optional choice (built, next paragraph). The room has
no hint and no hazard.

**Combination, built (#104, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15), 2026-11-15; automated tests, and the author played it on 2026-11-17):**
`forest-1` gained a third room after the route room. A RedMob that never moves stands in a 32 px-high tunnel on the floor, and a spore puffball's cloud covers every place from which the sword reaches it, so **melee is a timed risk** (the author's request: a second threat beside the RedMob so the sword does not dominate). The floor through the tunnel is the support-free route and needs no charge; one lever lowers a ladder to the roof, which is the safe route and carries a fireball refill; both routes reach the checkpoint with zero charges. Choices (scripted hero): **timed melee** (6.50 s mastered since #500 batch 3, D110, 6.83 s before; a patient hero always succeeds, 6.36 s on average from a teleport 30 px before the cloud; a player who simply walks in from the entry dies every time, at frame 82 (1.37 s; 101 before #500 batch 3), because the cloud is always at the same phase there, and succeeds with a pause of 0.15 s or more; a sweep over artificial start phases kills 30 of 60); **fireball from afar** (5.82 s, one charge, then the patient crossing); **roof** (7.40 s, nothing threatens it, the refill pays one charge). D26 holds from every swept state (216 runs). **Assessment, stated plainly:** the sword no longer dominates as an instant answer, and the roof pays a charge; but for a hero who knows the cycle at a full stock (the usual arrival) the mastered melee is still best by 0.90 s and the refill pays nothing, so the risk is a learning risk. Whether that is a meaningful decision is the author's call after playing it. The reasoning, the two earlier layouts and the numbers are in D68 and ROOMS.md.

**Tree branches, built (#349, playtest F13, [D80](DECISIONS.md#d80-tree-branches-one-way-perches-and-rotten-branches-349-playtest-f13-2026-11-16), 2026-11-16; automated tests only, the author has not played it):**
`forest-1` gained a fourth and last room, `forest-1-tree`. A tree is climbed by jumping up through its one-way branches;
a **rotten branch** (dead grey wood, a split, a dry leaf: visible at rest) is the crumble support again, now on a climb. It
gives way after the shared warning and falls behind a climber who keeps going, but it never kills: a hero who stops on one
rides it down to the floor and climbs again. So the cost of hesitating is height, and the escape is upward. Two trees
stand side by side: the near one is a column of rotten branches (4.95 s to the top), the far one a zig-zag of sound
branches (6.23 s); both meet at the crown, which holds the level's checkpoint. A rotten branch is never the only way up
(D26) and never hangs above a sound one (D69); the load refuses either. The room has no lethal trap and its landmark is
the pair of trees. A beetle crawled at the sound tree's foot between #327 and #500 (D100); since #500 the room is forest-1's one
`reward` room (D105): nothing in it can kill.

**Forest traps (accepted 2026-11-05, [D42](DECISIONS.md#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan)):**
simple, lethal, organic hazards on the ground and overhead, grouped by trigger family
(proximity, position, touch, rhythm, crumble), taken in spirit from the original
([evidence](ORIGINAL_TRAPS.md)). Every explosive trap on a required path offers a choice
between detonating it from afar and a riskier bypass; the required route never needs
ammunition (D26). The tablet gate is not decided there; see #136 and #139.

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
attacking and carrying rules are fixed in [D28](DECISIONS.md#d28-vertical-ladders):
Up/Down to take hold and climb, Jump to let go, ranged and mechanisms still work,
the sword and pick up/put down do not. A first ladder stands in the first Forest room.

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

**Implementation correction (superseded: the wall jump was removed, D121):** wall-clamber/jump existed in `Player` and
was used by forest replay tests. Earlier discussion incorrectly assumed it still
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
| Ranged attack | A consumable advantage against distant danger. | Consumable fireball, capacity three and two rare single-charge pickups implemented for the test campaign (#98); campaign economy remains future work. |
| Interaction | Operate a lever or another mechanism. | Up (a fresh press, no Action chord since [D126](DECISIONS.md#d126-up-and-down-interact-no-two-button-chords-548-2026-11-22)) chooses the nearest eligible carryable or mechanism; the smaller Tiled object ID breaks equal-distance ties. While carrying, Up operates a mechanism. Earlier: [D30](DECISIONS.md#d30-one-direction-for-pickup-and-mechanism-interaction) and [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175). |
| Pick up and place | Move a stone or torch to change the room situation. | Up picks up a nearby item or uses a mechanism; Down puts down a held item (D126; Action chords before). Ranged throws it; one visible slot is shared (#95/#97/#175). Authored Forest puzzles remain #103–#104, Fortress light puzzles #112–#115. |
| Throw an object | Affect something distant or place an object ahead. | Implemented on Ranged; does not spend fireballs (#95). |
| Vertical ladder | Change elevation, choose a route and movement timing. | Implemented in the test build (#99, [D28](DECISIONS.md#d28-vertical-ladders)); first Forest room. |
| Double jump | An extra airborne jump used in authored routes and wind tasks. | Accepted from Steppe; unlock and exact rules open. |

The current scheme has three action buttons: jump, directional action and ranged.
Keyboard, gamepad, touch and left-handed bindings are in
[GAMEPLAY.md](GAMEPLAY.md#controls).

Currently the sword cannot be used in the air; ordinary enemies can be killed
by stomping from above. Preserve [D4](DECISIONS.md#d4-stomp-is-allowed-from-above-only)
until a separate change is accepted. Future throwing/fireball descriptions do not
implicitly change these existing rules.

**Accepted carrying and throwing rules:**

- Carry **one object in one shared slot**, always visible in the front hand;
  there is no additional pocket slot ([D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket)).
  Sword melee remains available.
- Pick up/place uses interaction; throwing uses the ranged button.
- With an object held, ranged throws it. Empty-handed, it uses a fireball if ammunition
  is available. Throwing a stone or torch does not spend fireball ammunition.
- A throw must not also fire a fireball on the same press after the hand becomes
  empty. This separates resource expenditure; it does not forbid multiple linked
  effects from a mechanism interaction.

**Fireball stock (D27, #98):** Capacity is three held charges, not three total
shots per stage. A new/continued test run starts full; the next level carries the
remaining stock. Two optional authored pickups restore one charge on contact. At
capacity the pickup stays available and a local hint says the inventory is full.
Three fixed HUD icons show available/spent charges without a number. Room death
rolls back provisional pickups and stock together; secured pickup identities prevent
farming on healer returns. Restart Level restores entry stock and authored pickups.
Required routes remain playable without firing; campaign shop prices and supply
balance remain separate design work. See [current rules](DIRECTIONAL_CONTROLS.md#fireball-refills-98).

The one-slot input/state, throwing rules and deterministic target selection are implemented in
[the #95 baseline](DIRECTIONAL_CONTROLS.md); visible front-hand stone rendering
was completed on 2026-10-23.
Actions allowed on ladders are fixed in [D28](DECISIONS.md#d28-vertical-ladders): ranged
and mechanisms work, sword and pick up/put down do not. Carrying alone does not
prohibit normal movement or jumping; no such extra restrictions were accepted.

### One visible carryable slot (D23)

**Accepted by the author, 2026-10-23:** one carried object, always visible in front
of the hero. The pocket-plus-hand alternative below was considered and rejected.
[D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket) is the
decision record, including the rationale and conditions for a future revision.

| Area | One visible carried item | Pocket plus hand |
| --- | --- | --- |
| Readability | Show the existing sprite in the front hand, optionally repeat its identity in the HUD. | Show both occupied slots in the HUD and distinguish their roles. |
| Commands | Existing pickup/place/throw priority can remain. | Define which slot each command uses, whether items can swap, and what happens when the destination is full. A new button is one option, not a requirement. |
| Puzzles | Choosing what to carry and where to place it remains a constraint. | Simultaneous light and stone transport becomes possible; review planned obstacle combinations. |
| State | Current room-owned carryable lifecycle can remain. | Define both slots at death, forward exit, collection return and level restart. Two slots do not inherently require persistent saves. |

The chosen model keeps commands unambiguous and preserves choosing, placing and
retrieving tools as puzzle decisions. Carrying both a light and a stone could
simplify planned obstacles while making command selection and teaching more
complex. The old invisible stone was a rendering issue, not a second slot.
Recipe ingredients, coins and fireball ammunition remain separate resources.
Current room lifecycle remains in force, with no new transport/save rules.
The held stone now appears in the animated front hand, drawn after the hero just
like the torch. One-slot state and the existing input rules remain in place.

Reference examples checked against original manuals: [Super Mario World](https://www.nintendo.co.jp/clvs/manuals/common/pdf/CLV-P-SAAAE.pdf)
uses a held item with directional throwing; [Captain Toad: Treasure Tracker](https://www.nintendo.com/eu/media/downloads/games_8/emanuals/wii_u_6/captain_toad__treasure_tracker/ElectronicManual_WiiU_CaptainToadTreasureTracker_EN.pdf)
lets the player carry and throw turnips while a separate headlamp supplies light;
[Oddworld: New 'n' Tasty](https://www.oddworld.com/nntinstructions/) gives throwable
stones, meat and grenades an explicit aiming/throwing interaction. These examples
illustrate different choices; none defines a mandatory two-slot convention.

### Making the shared button understandable

[D24](DECISIONS.md#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying)
sets the whole first Forest room as the contextual teaching room, with repeatable
instructions on retries and returns. The former 320×144 region is removed. A held
stone/torch does not suppress a nearby usable mechanism's lesson: show its activation
alongside any active placement/throwing reminder, near the mechanism. Each new pickup
in the teaching room arms its own fixed-position reminder; the first camera scroll
after leaving the dead zone dismisses it until the next pickup. Stopping or returning
does not revive it. Mechanism lessons remain repeatable independently of that
reminder, so the text cannot linger at a screen edge after travelling. Later rooms/levels omit these
control lessons. This is presentation scope, not a change to one-slot/input rules.

A shared button risks picking something up when the player wants to attack, or
spending scarce ammunition when they meant to throw a stone. Distinguish the
action-selection rule from how it is displayed: the rule is accepted, hint visuals
remain open.

**Implemented scheme, [#95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95), revised by [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175):**

| Input | Action |
| --- | --- |
| Action without an interaction direction | Sword attack, including while carrying. |
| Up + action, empty hand | Pick up the nearest eligible item or operate the nearest eligible mechanism; the smaller Tiled ID breaks equal-distance ties. |
| Up + action, carrying | Operate an eligible mechanism. |
| Down + action | Put down the held item; with an empty hand it does nothing. |
| Ranged while holding an object | Throw it. |
| Ranged with an empty hand | Fireball, if ammunition is available. |

Test directional conflicts with ladders and the touch stick, both handedness
layouts, and unambiguous target selection. The original behavior shipped on
2026-10-22; the interaction revision shipped on 2026-10-26. See [behavior and
evidence](DIRECTIONAL_CONTROLS.md); simulated-device checks remain distinct from
outstanding physical-device checks. Ladder arbitration uses the same input path (#99).

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

The accepted limit is **three held charges**, with rare +1 contact refills (D27,
2026-10-25). This supersedes the earlier “three uses per entire stage” hypothesis.
A new/continued test run starts with three; moving between levels carries the
remainder rather than refilling. The test campaign has one authored refill before
the first boss entrance and another above a moving platform after that boss.
Full stock retains the pickup and shows a local hint; the HUD uses three icons
with missing charges darkened. Validate campaign supply and prices on real levels.

**Story origin (#340):** the fireball is a spark of the healer's own fire, given with the
silver thread; every refill found on the road is the same fire. The start tale says it is not
endless, which matches the scarcity above ([STORY.md](STORY.md)).

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

The existing forest recipe is Bat + Mushroom + all six unique level coins. Its
elixir multiplies physical sword damage by ten, reducing required swings from twelve to
two. Magical fireballs always remove two boss energy, so six landed fireballs defeat a
full-energy boss with or without the elixir. This does not increase starting ammunition.
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
| Death in a room | Restore its retry state, including ranged stock. Roll back this attempt's pickups; retain earlier secured progress. The silver thread pulls Edgard back to the room entrance under a death fade ([D81](DECISIONS.md#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16)). |
| First death of a New Game run | The healer's one line on the dialogue screen (`HEALER_FIRST_DEATH`, D81). A flag is saved when it opens, so Continue never repeats it; New Game clears it; never on a later death, Restart Level or a collection return. |
| Successful room exit | Secure results; remaining stock becomes the next room's starting stock. A room transition is not a persistent save. |
| Level completion | Automatically save the next level. |
| Continue after closing the game | Start the saved level from its beginning. Currently only the level index is saved. |
| Restart Level | Restore level-entry state under the existing contract, distinct from a room retry. |

A failed attempt rolls back ammunition expenditure and collection together with
refill-source state, preventing duplicated resources. A healer collection return
uses its corresponding secured boundary in [HEALER.md](HEALER.md#state-policy),
rather than unconditionally resetting the entire previously traversed level.

Death to control (D3, measured and pinned by `KimeriaGameDeathTimingTest`, [D81](DECISIONS.md#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16)):
a death with a visible cause gives control back after **0.967 s** (58 frames at 60 Hz; it was 1.117 s
before the thread), a fall into a pit after **0.417 s** (unchanged). The thread replaced the camera pan
and the appear clip; it adds no time.

Updated [D3](DECISIONS.md#d3-death-resets-the-current-room-and-respawn-is-instant)
and [ROOMS.md](ROOMS.md#replay-budget-70) target roughly 20 seconds of mastered
traversal between recovery points. This authoring replay budget is not a player
timer. It supersedes the intermediate 40 seconds; preserve historical measurements
with their original conditions. First learning has a separate roughly 60-second
hypothesis, including observation and attempts.

Current persistence is documented in
[D8](DECISIONS.md#d8-main-menu-has-continue-and-new-game) and
[DEVELOPMENT.md](DEVELOPMENT.md#game-state-progress-and-saves).
It does not save inventory or elixir. Besides the level, it saves one narrative flag: whether this
run's healer has said the first-death line (D81). Stage → level → room hierarchy alone does
not require mid-level saves or saving only at stage boundaries.

**Open for the campaign:** persistent tablet progress and how Continue restores
resources budgeted across several levels. Whether progress can be reconstructed
from the reached level or needs a format extension is a separate decision.
This design does not claim inventory persistence is implemented.

## Later mechanism and enemy ideas (author requests, 2026-11-05)

Requests, not accepted rules.

- **Floor pressure plate** ([#237](https://github.com/jlogicgames/edgard_in_kimeria/issues/237)):
  a plate that activates a door like a lever (Prince of Persia style), for the castle-fortress
  stage. Open design points: when the plate is released, does the door stay open or close
  again; the D26 recovery rules (a required door must not become unreachable); and linking by
  Tiled `target` like the lever.
- **Enemy readability** ([#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240)):
  an enemy winds up before an attack, with a pause that lets the player run away, and
  notices the player as in Broforce. This follows the warning rule of D7 and relates to
  [#138](https://github.com/jlogicgames/edgard_in_kimeria/issues/138) (bosses) and
  [#104](https://github.com/jlogicgames/edgard_in_kimeria/issues/104) (forest mechanic with an
  enemy or hazard).

## Forest monster ideas (author requests, 2026-11-13)

Requests, not accepted rules. Numbers (speeds, ranges, cooldowns) are chosen when a monster is
built and then recorded in `DECISIONS.md`. Every monster follows the warning rule of
[D7](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements) and the readability request
[#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240): a visible reaction before
the attack, so the player can run away or act first.

All ground monsters patrol back and forth along their own path, and switch to the reaction
below when the hero enters their awareness area (the hearing range).

| Monster | Size and movement | Behavior |
| --- | --- | --- |
| Beetle | About half the hero's height. Crawls to and fro along its path. | Hears the hero in its awareness area, reacts and runs at the hero; explodes when it gets close. Built: the card below (D100). |
| Spider | Same patrol and chase as the beetle. | Bites and kills instead of exploding. Built: the card below (D102). |
| Snail | Slow. Crawls along its path like the others. | Sees the hero and spits at them; a long reload between shots. Built: the card below (D106). |
| Flying beetle | Looks like a bat. Flies around on its own. | Explodes near the hero. |
| Wolf | Fast runner, faster than the hero. Trots to and fro along its path. | Sees and hears the hero, sprints after them and bites in a long, low lunge; the most dangerous Forest monster. Built: the card below (D115). |

The inherited RedMob and Bat keep their behaviour, frames, timing and boxes and were redrawn on 2026-11-21 in the danger
family with a red of their own, apart from the bugs' crimson and rose (colours: [ART_DIRECTION.md](ART_DIRECTION.md), section 1,
Danger). The red mob is a hooded whip brigand in wine and scarlet with glowing eyes; its wind-up raises the whip in front, then
cocks it behind the hood with the eyes flaring white, and the strike throws the lash out to the end of its attack box. Its
idle frames now stand on the floor line (they hung 5 px above it). The bat is a sooty, red-eyed bat with scarlet-boned wine
wings; its sheet grew from 80x72 to 80x80 so the four death frames, read 16 px tall from y 64, are whole (they were cut at
row 8). Grids: `art/objects/enemy/red-mob.grid`, `art/objects/enemy/bat.grid`; export and checks: `tools/enemy_sprites.py`,
`tools/test_enemy_sprites.py`.

The forest boss and the YellowMob were redrawn the same way on 2026-11-21 (colours: [ART_DIRECTION.md](ART_DIRECTION.md),
section 1, Danger), with the same frames, timing and boxes. The boss is a horned warlord in blackened iron and a mulberry tabard
with a long-hafted bearded axe (crescent edge with a lit bevel, a beard hooking down, the eye round the haft, a back spike).
His two attacks read apart. The normal attack, which starts when the hero touches `NORMAL_BOX`, is low and level: he draws
the axe back to his hip, crouches with it behind his heel (0.31 s, the eyes stay red), then sweeps it at knee height through
`NORMAL_BOX`; the hero steps out of reach or hits him first, which interrupts it. The counter, which follows every landed
sword or fireball hit, is high and overhead: during its invulnerable, orange 1.15 s warning he raises the axe straight up over
his horns, then lets it hang down his back with the eyes white, then chops down in front through `COUNTER_BOX`, chest to
knee height; the hero jumps and retreats out of reach before it lands (1.35 s after the hit) and strikes back during the recovery. At rest
he sits on the ground, one knee up, the axe standing beside him, and he dies to ash. The YellowMob is a hunched,
skull-masked charger in oxblood hide with a yellowed bone mask; its idle frames now stand on the floor line (they hung 4 px
above it), and it falls face down with the whole mask, horns along the ground. Both bodies lie a pixel inside their body hitboxes on every live frame, the YellowMob's drawing covers every row and
column of its hurtbox, and the boss's axe reaches each edge of its attack box on the active frames and leaves the body box on no
other frame that hurts (D10, `tools/test_enemy_sprites.py`). Grids: `art/objects/enemy/boss.grid`, `art/objects/enemy/yellow-mob.grid`.

Their warning, attack and death effects (the beetle's built, D100, the spider's, D102, and the snail's, D106; the wolf's, D115, built and placed in `forest-glade`, #331; the others planned) are listed in [#398](https://github.com/jlogicgames/edgard_in_kimeria/issues/398).
A monster killed without bursting leaves its body, in a pose that differs from the living monster; the body rocks when
anyone passes (D119), the sword or a stomp crumbles it, a fireball or a thrown torch burns it and flies on, and a stone flies through it
([D101](DECISIONS.md#d101-monster-bodies-a-monster-killed-quietly-leaves-its-body-posed-apart-from-the-living-which-reacts-to-the-hero-and-is-finished-by-a-weapon-327-240-398);
which monsters and how: the list in D101). The beetle's, the spider's and the snail's bodies are built; the other monsters follow in [#478](https://github.com/jlogicgames/edgard_in_kimeria/issues/478).

### Beetle card (built, [D100](DECISIONS.md#d100-the-forest-beetle-a-kamikaze-that-hears-commits-and-bursts-monsters-notice-in-body-language-and-are-met-in-play-327-240-398), [#327](https://github.com/jlogicgames/edgard_in_kimeria/issues/327))

A kamikaze, after the Broforce suicide mook: it never bites, it hears the hero, runs up, commits and bursts where it
stands. It is the template for the flying beetle ([#330](https://github.com/jlogicgames/edgard_in_kimeria/issues/330))
and for the notice beat of every monster that reacts to the hero ([#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240)). Like
every new monster it is met in play, with no teaching room and no hint text. Numbers are the built values (D100); the hero's are
measured from the code (run 100 px/s, hitbox 11×22, drawn at most 31 px tall, jump feet rise about 60 px at 588 px/s²,
sword box 17 px beyond the hitbox front, active for the whole 0.7 s swing).

| Item | Proposal | Why |
| --- | --- | --- |
| Size and look | 24×16 frames, ten in one strip (`crawl1`, `crawl2`, `look`, `wiggle`, `crouch`, `air`, `brace`, `bright`, `white`, `dead`), 14 colours with transparency, 15 with the moon rim (limit 16); drawn about 14 px tall with legs; a dark crimson shell with a rose glint, a near-black head, a glowing `ff6b57` eye and dusty-rose legs (the danger family, #236; green-bronze before); body hitbox `rect(1, 2, 22, 14)`, feet on the floor line | about half the hero's 31 px; slightly larger than the drawn body (D10); no reserved hue at rest |
| Contact | **harmless**: no hurtbox; touching or walking into it never kills | only the burst kills, so every death has the one cause the player saw winding up (D5) |
| Patrol | crawls 24 px/s to and fro inside its authored range (Tiled `offNeg`/`offPos`, as the mobs), turns at the ends, never leaves the range, not even in a chase | the author controls where it can burst, so safe spots and the exit are authored (D7, D26) |
| Awareness ("hearing") | the hero's hitbox centre within **96 px** of the body centre horizontally, either side, and the hitbox overlapping the band from the beetle's floor line to 48 px above it; lost beyond 144 px or after 1.0 s out of the band | six tiles: the player sees it well before it hears (read distance about 320 px, D71); the band keeps a hero on a ledge above from pulling it |
| Notice beat (#240) | **body language, no mark over its head**: it stops, raises its head and wiggles its antennae (`look`, `wiggle`, `look`, `wiggle`, 0.1 s each), crouches (`crouch`, 0.06 s), hops on the spot (`air`, drawn 2 px up, 0.1 s; the hitbox stays), lands (`crouch`, 0.04 s), then runs: **0.5 s** in all, not scaled by the assist. Heard: its own call, `beetle_notice`, a dry double click | the reaction is the creature's own, as in a fairy tale (the author rejected a "!" mark); the same pattern holds for every monster that notices the hero: an in-body reaction and a call in its own voice, no shared marker or alert sound; information, not the lethal warning (D7 scales only the warning) |
| Chase | scuttles at **70 px/s** (crawl frames twice as fast) until it is at its range edge (it waits there, facing the hero) or close enough to commit | slower than the hero, so running away always works |
| Trigger | the hero's hitbox touches the body hitbox grown by **8 px** left and right, at body height. A hero above it (jumping over) does not trigger it | close, as a kamikaze is; the blast reaches 40 px either side, so from the trigger the hero needs 21 px (0.21 s) to get out, 29 px (0.29 s) from contact |
| Wind-up (D7 warning) | the beetle commits **on the spot**: it stops, braces and bursts after `TrapWarnings.duration(DEFAULT_WARNING_SECONDS)`, sampled once: **0.8 s, 1.2 s with the assist**, whatever the hero does. Seen: the body shakes (1 px) and blinks between its own colours and a bright frame, faster and faster, then goes **white** for the last 0.12 s; pale specks are born on the edge of the blast box and drawn in to the body, so the reach is read on the particles. Heard: `beetle_windup` | the shared warning, so the assist applies (#240's item); 0.8 s leaves 0.5 s after the escape run, more than a reaction time |
| Burst | one **80×32** box, centred on the body, bottom on its floor line; the hero's hitbox is tested **once**, at the instant, through `player.collidedWithActor(false)` (as D47); the beetle is gone. Seen (D136): a white flash with a hot core, a halo of 32 dots that runs to the rim of the half-ellipse inscribed in the box (touching it at the floor edges and the top, never leaving it), the flat spray, 14 embers in the eyes' colour, 8 flung shell chips and grey steam, about 0.6 s. Heard: `beetle_pop` (a pok, a crack, a thump and a hiss, -16.5 dBFS). Felt: a 1.5 px, 0.15 s shake through `ScreenShake` (off with the Screen shake option), only when the hero is within 160 px and survives; no hit-stop | low and wide, unlike the 64×64 stump and bomb blasts: outside the box is safe, including above it (a jump keeps the feet above 32 px for 0.61 s) |
| Fireball, thrown stone | the **only kill**: in any state, wind-up included (the one save after it commits), it dies without bursting (below). The stone stops and drops (D39), the fireball is spent | the safe answer costs ammunition or a stone; the fireball's and stone's own hit effects play as on any enemy |
| Sword, stomp | the sword, in any state (calm, noticing, chasing or winding up), and a stomp on a beetle that has not triggered make it **burst at once, with no wind-up**; the hero is inside the box (the sword reaches 17 px, the stomp stands on it), so melee kills the hero. A stomp on a triggered beetle bounces the hero off, no kill. No blood (D65 does not apply) | the author's rule: melee is punished; the bounce off a winding-up beetle throws the feet above the box from 0.08 s to 0.81 s after the stomp |
| Death (fireball, stone) | flips onto its back (`dead`, a 4 px hop over 0.2 s) and **stays as a body** (D101); a pale poof of 12 puffs starts 0.08 s after the hit, once the fireball's flash is spent, and five shell chips are knocked out to the floor; the kill sound `bounce` | a kill reads as a kill without a burst, and the body keeps reading after the fireball's ring |
| Body (D101) | the `dead` frame holds 0.3 s, then cross-fades over 0.6 s into the same frame in the live palette, ink and rim (`monster-beetle-body.png`, D117); harmless, blocks nothing, counts as defeated; rocks (10°, about 2.5 swings a second, still in about 1.4 s) when any actor (the hero, a monster, the boss) moves within 6 px; only the sword and a stomp (with the normal bounce) crumble it and a fireball or a thrown torch burns it (0.3 s) and flies on, unspent; a stone and every other thrown object fly through it, leaving it whole (D119); gone after a reset or when the hero leaves the room | its pose, on its back, tells it from a live beetle; it keeps the live palette, measured as active (D117) |
| Traps and other enemies | the burst hurts only the hero; traps do not hurt the beetle; it does not light stumps or roots | one new element at a time (D72); chaining blasts is a later room idea |
| Rollback (D3, D6) | a death resets it to its authored spawn, direction and calm state; a killed or burst beetle (and its body) stays gone in a passed room and on a healer revisit; one left winding up when the hero exits comes back calm at its spawn | the enemy rules as they are; no room-state snapshot is needed |
| Authoring checks | the range plus 40 px either side lies inside the room on a floor without gaps; the blast box at any point of the range never covers the room entrance body, the `RoomExit` or a `Checkpoint`; no beetle range in front of the forest-2 rubble at x 96 to 128, where its outline falls to 0.21 (the active floor is 0.28) | every retry is survivable and the exit stays reachable (as D46, D47) |

Effects are particles on `KimeriaParticleSystem` (`effects.BeetleFx`, one per level, like `FireballFx`); the only sprite is
the beetle's own sheet (`assets/images/objects/monster-beetle.png`). Contrast of the ten frames (`tools/check_contrast.py`
metrics at the seven RedMob and YellowMob spawns of the three maps): outline 0.329 (`look`, `wiggle`) to 0.515 (`white`),
above the active 0.28; the gate measures every placed `Beetle`. Code: `entity/enemy/Beetle.java`; tests: `BeetleTest`,
`BeetleFxTest`, `RemainsTest` (D101), `AllocationBudgetTest.beetleThroughTheRealLevel`. The body keeps the live palette since D117 and is measured
as active where a beetle can die (outline at least 0.501). Placed (#327, cards approved 2026-11-18): introduced in `forest-1-guard`
past the tunnel (it crawled at the sound tree's foot in `forest-1-tree` until that room became a reward room, #500) ([ROOM_CARDS.md](ROOM_CARDS.md#forest-1-guard)); repeated in forest-2 on
the exit ledge of `forest-fissure` ([ROOM_CARDS.md](ROOM_CARDS.md#forest-fissure), approved by the author 2026-11-18).

### Spider card (built, [D102](DECISIONS.md#d102-the-forest-spider-it-hears-notices-and-pounces-at-where-the-hero-stood-make-it-miss-then-strike-328-240-398), [#328](https://github.com/jlogicgames/edgard_in_kimeria/issues/328); climbing and the silk drop, [D103](DECISIONS.md#d103-the-forest-spider-climbs-and-drops-a-route-of-floors-walls-and-a-silk-thread-two-strikes-one-tell-483-328), [#483](https://github.com/jlogicgames/edgard_in_kimeria/issues/483))

The beetle teaches "never melee it, get clear"; the spider teaches the opposite. It pounces at the spot where the hero stood,
from further than the sword reaches, then lies flat and harmless while it recovers: make it miss, then strike, or meet its
leap with the blade at the right moment. It is the first monster whose melee answer is a matter of timing, and it is not the
RedMob (a whip at contact range, lethal contact, no notice). Like every new monster it is met in play, with no teaching room
and no hint text. Numbers are the built values (D102); the hero's are measured from the code (run 100 px/s, hitbox 11×22,
sword box 17 px past the hitbox front and live for the whole 0.7 s swing, the hero stopped while swinging).

| Item | Built | Why |
| --- | --- | --- |
| Size and look | 32×16 frames, nine in one strip (`crawl1`, `crawl2`, `rise`, `tap`, `crouch`, `leap`, `bite`, `recover`, `dead`), 12 colours, 13 with the moon rim; a dark crimson abdomen with a rose chevron of stitches, a smaller head with two glowing `ff6b57` eyes and pale fangs, eight dusty-rose legs with high knees (the danger family, #236; plum and tan before), all rooted on the cephalothorax (four near, four far); drawn about 13 px tall; body hitbox `rect(2, 3, 20, 13)`, feet on the floor line | half the hero's height, like the beetle; the high-kneed legs make a silhouette no other sprite has; no reserved hue |
| Contact | **harmless**: only the fangs kill, during the leap and the bite | every death comes from the wind-up the player watched (D5, D7) |
| Patrol | it **skitters**: 48 px/s for 0.3 s, still for 0.3 s, inside `offNeg`/`offPos`; it never leaves the range on foot, but its leap may go up to 48 px past an end over floor (#497) | the stop-and-go reads as a spider; the range is the authored safe line |
| Awareness ("feels the ground", D103) | the hero's centre within a **144 px circle** around its body's centre, in its room, through walls and floors; lost after **1.0 s beyond 192 px** | a hero above or below is noticed; hearing never kills, and the whole route is on screen |
| Notice beat (#240) | **body language, no mark**: it rises tall on its legs (`rise`, 0.1 s), drums the ground with a raised foreleg twice (`tap`, `rise`, `tap`, `rise`, 0.08 s each) and settles (`crawl1`, 0.08 s): **0.5 s**, not scaled by the assist. Heard: its own call, `spider_notice` | D100's shared pattern: the creature's own reaction and its own voice |
| Chase | **85 px/s**, crawl frames twice as fast; it waits at its range's end, facing the hero | faster than the beetle (70), slower than the hero (100): running away always works |
| Commit | the hero's hitbox touches the **pounce box** (from the body's middle to **48 px** past its front, from the floor line to 8 px above its top); the target is **locked**: the hero's centre at that instant, clamped into its range and never behind it | a melee collider authored once (D10); the locked target makes the dodge the same every time (D7) |
| Wind-up (D7 warning) | `TrapWarnings.duration(BITE_WARNING_SECONDS)`, sampled once: **0.5 s, 0.75 s with the assist**. Seen: the `crouch` frame (forelegs raised, fangs open), drawn 1 px back from the hero; pale **silk glints** (60 a second) run along the floor from its fangs and gather on the landing spot. Heard: `spider_windup` (`_long`) | the hero needs 11 px (0.11 s) to step out of a locked bite, leaving 0.39 s (0.64 s with the assist); at the shared 0.8 s the dodge would be trivial |
| Leap and bite | a **0.25 s** low leap (a 6 px arc; the body moves) to the locked spot, a silk line left in the air, `spider_leap`; then the `bite` frame for 0.1 s with two fang glints and floor dust, `spider_bite`, hit or miss (D93). The fang box (inside the drawn head and fangs) is lethal during the leap and the bite, swept every frame. A solid in the way (a wall, a crate, a block) stops the leap and, when the fangs reach him, it bites against it (otherwise it holds); a bite due on the bite's last frame lands before the recovery | the act is seen and heard, hit or miss; nothing leaps through a wall |
| Recovery | **0.6 s** in the `recover` frame, flattened and harmless, not scaled by the assist; then the chase again, and a full new wind-up for each pounce | the punish window: the reward for making it miss |
| Fireball, stone, torch | kill it in any state, the leap included; it leaves its body (D101); no blood (D99) | the safe answer costs a charge or a stone |
| Sword | kills it in any state, with the light spray of D65, and leaves the body. A trade only by timing: rushing during the wind-up works for a hero who goes at once (31 px of 48 in 0.5 s); a swing that is live when it arrives meets it; a swing that ends before it lands, or none, is a bite; after a miss the recovery is a free kill | timed melee, the spider's new decision |
| Stomp | kills it in any state with the normal bounce, and leaves the body; jumping over a leap and landing on the recovering spider is its natural use | a low, flat creature is stompable |
| Same frame | a bite that reaches the hero lands at the start of the spider's next frame, so a killing hit on the same frame wins | as the boss rule: an interruption wins a tie |
| Body (D101) | `dead`: on its back, round belly down, eight legs curled up. The active frame holds 0.3 s, then cross-fades over 0.6 s into `monster-spider-body.png` (the live palette, ink and rim, D117); rocks like the beetle's (`RemainsSwing`), for any actor passing; the sword or a stomp crumble it into crimson and rose chips, a fireball or a thrown torch burns it and flies on; a stone flies through it (D119); the swing that killed it does not crumble it | the pose tells it from a live spider (48.3 % of the silhouette differs from its closest live frame) |
| Traps, other enemies | the bite hurts only the hero; traps do not hurt it | one new element at a time (D72) |
| Rollback (D3, D6) | a death resets it calm at its spawn; killed, it stays gone with its body in a passed room; one left mid-pounce comes back calm | the enemy rules as they are |
| Authoring checks | on the 32×16 spider whatever the Tiled object's size: the range inside the room on gapless floor; no standing place (the entrance body, the `RoomExit`, a `Checkpoint`) inside the pounce box at any point of the range, facing either way (`Spider.problem`) | the hero never enters or stands in a pounce |
| Route (D103) | authored as a Tiled **polyline** of type `Spider`: level pieces are floors, upright pieces along one face of a static solid are walls, an upright piece over open air at either end, hanging from a floor's end, is a silk thread to the floor below (at most 112 px). A box with `offNeg`/`offPos` is the floor-only spider above | the route decides its abilities, so a room meets one new ability at a time (D72) |
| On a wall (D103) | it skitters and chases along the route, climbing at 64 px/s and coming down at 85 px/s, head along its motion, belly against the face, legs spread up and down it (`wall1`, `wall2`; the notice in `walltap`); corners are a one-frame quarter-turn snap. **It never strikes from a wall**: a hero below draws it down to the floor, where the pounce applies | travel never kills; the climb is slower than the hero runs |
| Silk drop (D103) | at the lip of its floor, when the hero touches the **drop box** (the 8 px column its fangs sweep, lip to thread's end): it hangs head down over the edge (`hangwind`, drawn 1 px up) for the same bite warning, **0.5 s, 0.75 s with the assist**, with glints running down the column and `spider_windup`; then it **drops at 384 px/s** (`spider_drop`), fangs lethal and swept, to the thread's end 4 px above the floor, and **bites there** hit or miss (`spider_bite`); **0.6 s hanging, harmless**, then it **reels up at 64 px/s, harmless** (`spider_reel`) and steps back onto its floor; a new drop needs a full wind-up from the lip | one grammar for both strikes: crouch, glints to the spot, strike along that line; the hero needs 10 px (0.10 s) to step out, leaving 0.40 s |
| Answers off the floor (D103) | every weapon kills it wherever it reaches it: on a wall, at the lip, dropping, hanging, reeling; the sword reaches it hanging low, a stomp from its ledge reaches it on the thread; weapons pass through the thread | about 2 s to punish a missed drop |
| Body off the floor (D103) | killed on a wall or a thread, the body is set on the open side and falls to the floor below (gone over a pit); a thread snaps with it: specks drift down, its stub fades in 0.4 s, `spider_snap`, nothing left | the body is the record of the kill (D101) |
| Route checks (D103) | `Spider.routeProblem`: pieces level or upright, walls 32 px or more, corners meeting, every floor as above, the route inside the camera's view of its room; a thread's column clear of solids, one-way platforms and moving or hanging objects, ending on floor that runs 16 px past it on each side, over no climbable or quicksand, and no standing place in its drop box; the contrast gate measures every pose along the route, so a dark wall face rejects the placement | every drop can be stepped out of, and the spider is in view when it acts |

Effects are particles on `KimeriaParticleSystem` (`effects.SpiderFx`, one per level; the body's crumble and burn in the shared
`effects.RemainsFx`); the only sprites are the spider's sheet (`assets/images/objects/monster-spider.png`, with the hanging
frames and the thread's 1×4 tile since D103), its wall sheet (`monster-spider-wall.png`) and its body (live palette, D117).
Contrast of the nine frames (`tools/check_contrast.py` metrics at the seven RedMob and YellowMob spawns and the three beetle
spawns): outline 0.300 (`recover`) to 0.331 (`crouch`), above the active 0.28; the gate measures every placed `Spider`, and
`BODY_SWEEP` the body where a spider can die (active since D117: outline 0.285 to 0.572). Code: `entity/enemy/Spider.java`; tests:
`SpiderTest`, `SpiderFxTest`, `RemainsTest`, `AllocationBudgetTest.spiderThroughTheRealLevel` (and, for D103,
`spiderDropThroughTheRealLevel` and `spiderClimbThroughTheRealLevel`). Placed on room cards ([#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484)), as the beetle's were, in
the order floor, wall, thread: `forest-1-spider` (on open ground, before the snail; reworked under D105 by #500), `forest-1-climb`
(up the far face of a block where the hero waits for a running lift, beside a snail) and `forest-1-route` (its thread from the ladder slab's lip over the mandatory
floor). The climb and the drop are develops of the one element `spider` (D103).

### Snail card (built, [D106](DECISIONS.md#d106-the-forest-snail-it-looks-ahead-holds-a-line-and-spits-a-slow-gob-cross-its-ground-in-the-reload-329-240-398), [#329](https://github.com/jlogicgames/edgard_in_kimeria/issues/329))

The beetle says "get clear", the spider says "make it miss, then strike". The snail is the first monster that hurts from a
distance and the first that only looks ahead. It holds a stretch of open floor with a slow spit, and its question is new: **when
do you cross?** Wait behind cover for its shot, jump or cut the gob, and walk in during its long reload; or come from behind
while it crawls the other way. Once you reach it, it is harmless. Like every new monster it is met in play, with no teaching
room and no hint text. Numbers are the built values (D106); the hero's are measured from the code (run 100 px/s, hitbox 11×22,
feet rise about 60 px in a jump, about 0.9 s in the air; sword box 17 px past the hitbox front, live for the whole 0.7 s swing,
the hero stopped while swinging).

| Item | Built | Why |
| --- | --- | --- |
| Size and look | 24×16 frames, eight in one strip (`crawl1`, `crawl2`, `look`, `blink`, `draw`, `spit`, `rest`, `dead`) and a ninth cell with the gob's two 8×8 wobble frames; 13 colours, 14 with the moon rim; a crimson shell coiled in two turns (a dark groove, a rose whorl), a dusty-rose foot and neck, two eye stalks with glowing `ff6b57` tips, a crimson gob (the danger family, #236; chestnut, clay and an olive gob before); drawn about 14 px tall; body hitbox `rect(1, 3, 20, 13)`, the stalks outside it | about half the hero's height, like the beetle and the spider; the shell's ink contour and moon rim cover only its top two rows, so it reads as quiet as the spider (outline 0.302 to 0.339) |
| Contact | **harmless**: only the gob kills | every death comes from an act the player watched wind up (D5, D7) |
| Patrol | crawls at **12 px/s** inside `offNeg`/`offPos`, stops **0.8 s** at each end (`blink`), then turns; never leaves its range | slow is the snail; the pause and the turn are the windows to come up behind it |
| Sight (it looks, it does not hear) | **only ahead**: a box from its front edge to **176 px** ahead, from its floor line to **48 px** above it, **cut at the first solid** at the gob's height; aware, it follows a hero who goes behind it with a turn after **0.4 s** and loses him after **1.5 s** out of sight | a safe back and cover that breaks its line are its new readings; the sight and the firing line are cut by the same solids, so if it can see you, it can hit you |
| Notice beat (#240) | **body language, no mark**: it stops and its stalks shoot up and pull in twice (`look` 0.15 s, `blink` 0.1 s, twice): **0.5 s**, not scaled by the assist. Heard: its own call, `snail_notice`, a wet "mm?" | D100's shared pattern |
| When aware | **no chase**: it holds still and faces the hero, stalks up | it guards a line, not a route |
| Firing line | a band **4 px** tall centred **7 px** above its floor line, from its mouth to 176 px ahead, cut at the first solid; it fires only when the hero's hitbox touches the line and its reload is done; a hero it sees above the line is watched, not shot at | the trigger is the lethal reach itself (D10) |
| Wind-up (D7 warning) | `TrapWarnings.duration(DEFAULT_WARNING_SECONDS)`, sampled once: **0.8 s, 1.2 s with the assist**, facing locked. Seen: `draw` (reared, throat swollen), a glow swelling at the mouth, pale specks drawn in along the line from its far end. Heard: `snail_windup` (`_long`), a wet gurgling inhale | the warning shows where the shot goes and how far; the gob's flight adds up to 1.47 s on top |
| Spit and gob (D71, D93) | `spit` 0.15 s, `snail_spit` ("ptoo"); the gob flies straight and level at **120 px/s**, an authored 8×8 sprite that sheds drips; its **4×4 core** kills, swept every frame, the hit landing on the snail's next frame. It splats on the hero, on the first solid face, or after 176 px drops to the floor and splats there; droplets and a fading smear, `snail_splat`, hit or miss; no puddle. It passes through one-way platforms, monsters (the spider too), bodies and pickups; one gob per snail | seen in flight, heard when fired and when it ends; faster than the hero runs, so it is jumped, cut or put behind cover, never outrun |
| Reload | **2.4 s** from the spit, harmless, not scaled by the assist (`rest`/`blink`, a slow swallow); then a full new wind-up | from 176 px the hero crosses in 1.76 s; one shot every 3.2 s, the D105 cadence |
| Sword | kills it in any state, with the light spray of D65, and leaves the body; a live sword box that touches the gob cuts it (the blade wins a same-frame tie) | melee is safe once you are there; a cut costs the hero the 0.7 s swing |
| Fireball, stone, torch | kill it in any state and leave the body (no blood, D99); against the gob each destroys it (fire with a puff of steam) and **flies on, unspent**; a weapon breaks the gob's drawn 8×6, so the chest-high fireball meets it | the right tool never costs a charge on a gob |
| Stomp | the normal D10 stomp on the shell, with the bounce; it leaves the body | a low creature is stompable |
| Killed mid-act | during the wind-up it fires nothing (the gob pops on its lips, seen and heard); a gob already in the air flies on | what the player has read still holds |
| Shell | blocks nothing | the safe approach from behind stays safe |
| Body (D101) | `dead`: the empty shell rolled onto its back, its mouth open upward, no foot, no stalks; the active frame holds 0.3 s, then cross-fades over 0.6 s into `monster-snail-body.png` (the live palette, ink and rim, D117); rocks (`RemainsSwing`) for any actor passing, crumbles (sword, stomp) into crimson and rose chips, burns (a fireball or a thrown torch, which fly on); stones pass through (D119) | 49.3 % of the silhouette differs from its closest live frame |
| Rollback (D3, D6) | a death or a revisit brings it back calm at its spawn with no gob in the air; killed, it stays gone with its body in a passed room | the enemy rules as they are |
| Authoring checks | `Snail.problem`, on the 24×16 snail: the range inside the room on gapless floor; every firing line, from every 4 px of the range facing either way, ends on a solid face or floor inside the room, below the camera's top, and crosses no standing place (the entrance body, the `RoomExit`, a `Checkpoint`), the drop past its range included | every retry can be survived and every shot is seen |

With the spider in one room: the snail controls the far, open floor and forces a jump or a cut on its beat; the spider controls
the near floor and punishes standing still. Both notice in 0.5 s of their own body language and strike along a line drawn in pale
particles (the spider's glints run out to the landing spot, the snail's specks are drawn in to its mouth), and neither changes
the other (the gob passes through the spider). Cover (crates, blocks, steps) breaks the snail's line with no new object.
Effects are particles on `KimeriaParticleSystem` (`effects.SnailFx`, one per level; the body's crumble and burn in
`effects.RemainsFx`); the only sprites are the snail's sheet with the gob's cell (`assets/images/objects/monster-snail.png`) and
its body (live palette, D117). The gate measures a placed `Snail` along its range and its gob along every firing line (`check_contrast.py`).
Code: `entity/enemy/Snail.java`, `entity/enemy/Breakable.java`; tests: `SnailTest`, `SnailFxTest`, `RemainsTest`,
`AllocationBudgetTest.snailThroughTheRealLevel`. Placement: with the spider rooms' rework under D105 ([#499](https://github.com/jlogicgames/edgard_in_kimeria/issues/499)).

### Wolf card (built, [D115](DECISIONS.md#d115-the-forest-wolf-it-sees-far-outruns-the-hero-and-lunges-along-a-line-jump-it-then-strike-331-240-398), [#331](https://github.com/jlogicgames/edgard_in_kimeria/issues/331))

The beetle says "get clear", the spider "make it miss, then strike", the snail "cross in the reload". Every one of them loses a
foot race, so walking away always answers them. **The wolf is the first monster you cannot outrun**, and the author asked for it
to be the fastest and most dangerous in the Forest (2026-11-20). Its question is: **where do you meet it, and do you act at
once?** Its bite is a long, low, straight lunge along a locked line: jump over it (the first monster beaten by going *over* its
attack, where the spider's dodge is *out of* a spot), turn and strike its back in its short recovery, go up out of its reach, or
meet it with a live sword, a fireball or a stone. Like every new monster it is met in play, with no teaching room and no hint
text. Numbers are the built values (D115; the author approved the card on 2026-11-21 with every recommendation, and the
lunge at 160 px in 0.25 s on 2026-11-21, see the running row). The hero's are measured from the code (run 100 px/s, hitbox 11×22, drawn at most 31 px tall; a jump lifts the feet
about 60 px and keeps them above 14 px for about 0.79 s, from 0.06 s after take-off; sword box 17 px past the hitbox front, live
for the whole 0.7 s swing, the hero stopped while swinging).

| Item | Built | Why |
| --- | --- | --- |
| Size and look | **48×24 frames**, seventeen in one strip (`stand`, `trot1`, `trot2`, `alert`, `hackle`, `scrape`, `run1` to `run4`, `skid`, `crouch`, `lunge`, `bite`, `shake`, `bay`, `dead`); 11 colours, 12 with the moon rim lit from above only (a full rim made it outline 0.52 to 0.74, far louder than the other monsters); drawn about 20 px tall from paws to ear tips and about 38 px long, two thirds of the hero's 31 px, the largest Forest monster. Lean and low-slung, long muzzle, pricked ears, brush tail. Body hitbox `rect(6, 6, 34, 18)` (a pixel past the drawn body on each open side, D10), feet on the floor line (row 23) | big enough to read as the dangerous animal at a glance, low enough that a jump clears it; four long legs and a muzzle are unlike every other sprite |
| Contact | **Harmless**: no hurtbox. Only the jaws kill, during the lunge and the bite | every death comes from the wind-up the player watched (D5, D7) |
| Patrol | **trots at 40 px/s** inside its authored range (Tiled `offNeg`/`offPos`), with a **0.6 s** sniff at each end (`trot1`, head down) before it turns; it never leaves the range on foot | its territory is authored, so every refuge and the exit are placed by the author (D7, D26); the sniff is the window to slip past behind it |
| Sight | **ahead**: a box from its muzzle to **256 px** ahead, from its floor line to **48 px** above it, **cut at the first solid** at eye height, 16 px above its floor (`Level.blocksObject`) | the furthest sense of any monster, yet only 40 % of the view, so the player always sees it first; cover counts, as for the snail |
| Hearing | a **64 px circle** around its body's centre, in its room, through anything | a hero cannot linger near its back; sneaking past further away still works |
| Notice beat (#240, D100) | **body language, no mark**: it stops dead, its head snaps up and its ears prick (`alert`, 0.15 s); it drops its front with its hackles raised (`hackle`, 0.15 s); it scrapes a forepaw once (`scrape` 0.1 s, `hackle` 0.1 s), then bursts into the run. **0.5 s**, **not scaled by the assist**. Heard: `wolf_notice`, a short chesty "wuff" | D100's shared pattern; the scrape points the way it will run |
| Chase | sprints at **160 px/s**, reaching it in **0.25 s** (640 px/s²); a four-frame gallop. It chases to a target (under the hero, or its range's end) and stops on it. **Turning costs time**: when the hero gets behind it, it skids to a stop (1280 px/s², 0.125 s, about 10 px, `skid`, floor dust), then starts the other way from standing | it closes 60 px every second on a running hero; the skid rewards going over it |
| Commit | the hero's hitbox touches the **lunge box**: from its muzzle to **72 px** ahead, from its floor line to **24 px** above it. The **direction is locked** (its facing); the jaws travel the full lunge, they do not aim at a spot | the longest commit of any monster (spider 48 px); the box starts at the jaws' back edge, 7 px behind the muzzle (the jaw box's front edge, the reference of every reach), so a hero in front of the jaws is bitten and one beside its flank is not; one collider authored once (D10); a locked direction makes the jump the same every retry (D7) |
| Wind-up (D7 warning) | `TrapWarnings.duration(BITE_WARNING_SECONDS)`, sampled once: **0.5 s, 0.75 s with the assist**; nothing the hero does changes it. Seen: `crouch` (front low, ears flat, jaws open, drawn 1 px back from the hero); pale **breath streaks** run along its floor line from the jaws to the lunge's end, 60 a second, so the whole 160 px is read (the line is traced at the commit over the floor under it and the lunge flies exactly that line). Heard: `wolf_windup` (`_long`), a rising snarl | the shortest warning in the game, but the jump needs no distance, only timing; at the shared 0.8 s a runner would escape (177 px against 160) |
| Lunge and bite | a **0.25 s** lunge of **160 px** along its floor (640 px/s; the body moves, `lunge` frame, drawn low), `wolf_lunge`; then `bite` for 0.1 s, jaw glints and floor dust, `wolf_bite`, **hit or miss (D93)**. The **jaw box** (about 7×7, 8 to 15 px above the floor line, inside the drawn muzzle) is lethal during the lunge and the bite and **swept every frame**. The lunge stops against a solid face (it bites there) and at the end of its floor (2 px floor probes, the spider's #497 rule); it may leave its range by up to its reach, over floor only. A bite that reaches the hero lands at the start of the wolf's next frame | outreaches any run: a hero who runs from the commit is 147 px off when the jaws end at 160. Low, so a jump clears it. The next-frame landing gives a killing hit the same-frame tie (#503, #505). Every lunge frame re-checks the floor and solids: a floor that goes during the wind-up (a lever-driven wall set inactive) stops the lunge at the last good place, its body never over air, and the bite is still seen and heard |
| Recovery | **0.5 s** in `shake` (it shakes its head, flat-footed), harmless, **not scaled by the assist**; then a standing turn if the hero is behind it, and the chase again. Each new lunge needs a full wind-up | a shorter punish window than the spider's 0.6 s, and it is open only to a hero who turns and swings at once; hesitate and a new wind-up begins |
| Refuge and give-up | when the hero is **out of its reach** (more than its 72 px commit box beyond the range end, so a hero 73 px or more past the muzzle at the range end is safe even though a lunge would cover 160, or on anything whose top is more than 24 px above its floor) it runs to the nearest point it can reach, stops facing him and **bays** (`bay`, head up, `wolf_bay`; harmless). After **1.5 s** of the hero out of reach, or **1.5 s** out of both sight and hearing, it **gives up** and trots back to patrol; it will not notice that hero again until he has been out of its sight for **1.0 s** | the chase always ends; the cool-down stops a loop of notice and bay under a ledge (D26) |
| Running away | it commits when the gap closes to 72 px and stops where it is. A hero who runs from the commit gains 100 px/s through the wind-up and the lunge, so his hitbox's near edge is 72 + 75 = 147 px from the muzzle when the jaws end at 160: **bitten** by 13 px. With the assist (0.75 s) he is at 172: **missed by 12 px**. Measured (`WolfTest`): bitten at every commit gap from 40 to 72 px without the assist; with it, missed from about a 60 px gap up. A hero who survives a lunge by running (assist only) is struck again about 2 s after it began: the 0.5 s recovery leaves him 72 px ahead, it closes the last pixel and winds up for 0.75 s (measured 1.97 s). The reach is 160 px (approved by the author 2026-11-21) because a hero who keeps running gains 25 px during the 0.25 s lunge: at 160 he is bitten by 13 px without the assist and missed by 12 px with it | outrunning is not an answer without the assist |
| Standing, stepping back | a hero who stands is bitten whatever the assist; one who steps back is the runner above | hesitation is punished: only a jump, a swing or a shot works without the assist |
| Jumping | the jaws reach a hero G px from the muzzle G/640 s into the lunge and pass him 18 px later; the feet stay above the jaws (15 px) from 0.06 s to 0.84 s after take-off, so a take-off up to **0.55 s** after the commit clears them at the edge of its box (71 px) and up to **0.45 s** at its muzzle; with the assist up to **0.82 s** and **0.72 s**, and a jump made at the very commit has then landed before the jaws arrive (the first clearing take-off is 0.07 s after it): the jump is timed, not spammed (measured, `WolfTest`). A hero more than 24 px up is never committed to: a jump during its sprint makes it run underneath, and it stops under him (it skids only if he lands behind it) | the one answer that always works, and the wolf's lesson |
| Sword | kills it in any state, with the light spray of D65, and leaves its body. Its body reaches the sword box (17 px past the hero's hitbox) about 0.6 s after the commit, as the jaws arrive: a swing started anywhere from just before the commit to about 0.6 s after it is live then (`WolfTest` pins one started 0.2 s after). A swing started early, while it is still noticing or sprinting in, ends before the lunge: a bite. After a jumped lunge, the 0.5 s recovery is a kill from behind for a hero who turns and swings at once (its haunches end about 15 px away) | timed melee, harder than the spider's because the punish window is shorter and needs a turn first |
| Stomp | kills it in any state with the normal bounce and leaves its body; its use is dropping on it from a refuge while it bays | the reward for going up |
| Fireball, stone, torch | kill it in any state, the lunge included, and leave its body; no blood (D99). One cast or thrown on the frame the jaws arrive wins the tie | the safe answer costs a charge or a carried stone, and the hero must stand and face it to aim |
| Killed mid-act | killed during the wind-up it never lunges; killed in the lunge the body slides on to the lunge's end over the floor | what the player read still holds |
| Body (D101) | `dead`: **lying flat on its side along the floor, forelegs stretched forward under its flat head, hind legs back, tail limp** (the author's edit: a wolf's paws never stick up). The active frame holds 0.3 s, then cross-fades over 0.6 s into `monster-wolf-body.png` (the live palette, ink and rim from above, D117). **It does not rock; it twitches**, a 1 px shudder of the same frame for about 0.3 s, when any actor comes within 6 px (as D101 lists for the wolf, D119). The sword or a stomp crumble it into fur tufts and dark chips; a fireball or a thrown torch burns it (`RemainsFx`) and flies on; a stone flies through it (D119); the swing that killed it does not crumble it | every live frame stands on four paws, so lying on its side is far from all of them (well over the 25 % floor of `RemainsTest`) |
| Traps, other monsters | the bite hurts only the hero; traps do not hurt it; it passes through other monsters and bodies; a snail's gob passes through it | one new element at a time (D72) |
| Rollback (D3, D6) | a death or a revisit brings it back calm at its spawn, facing its authored way; killed, it stays gone with its body in a passed room; one left mid-chase or mid-lunge comes back calm | the enemy rules as they are |
| Authoring checks (`Wolf.problem`) | on the 48×24 wolf whatever the Tiled object's size: its range inside the room on gapless floor; no standing place (the entrance body, the `RoomExit`, a `Checkpoint`) inside its **sight box or its reach** (its range plus the 256 px sight, which includes the 160 px lunge and the jaw box, facing either way) at any point of the range; **at least 64 px of headroom** above its floor over its whole reach; the range at least **160 px** long | the hero never arrives caught; the jump is always possible; a short range would waste the sprint |
| Assist | only the lethal wind-up is longer, **0.75 s**, which makes running and stepping back work again for a hero who runs from a commit gap of about 60 px up (measured; 12 px of margin at the usual 72). Notice, speeds, skid, recovery and give-up are not scaled (`TrapWarnings`: movement is not scaled) | the same rule as every monster |

Against the other monsters (built values; D100, D102, D103, D106):

| | YellowMob, RedMob | Beetle | Spider | Snail | **Wolf** |
| --- | --- | --- | --- | --- | --- |
| Chase speed (hero 100 px/s) | 80 px/s | 70 px/s | 85 px/s | none | **160 px/s**, full speed in 0.25 s |
| Senses | its range, at body height | hears 96 px either side | feels a 144 px circle | sees 176 px ahead | **sees 256 px ahead and hears a 64 px circle** |
| Commits at | contact or whip reach | body + 8 px | 48 px pounce box | its 176 px firing line | **72 px lunge box** |
| Lethal reach | contact, whip | 40 px either side | 48 px leap to a spot | a gob at 120 px/s | **a 160 px lunge in 0.25 s** along a locked line |
| Wind-up (`TrapWarnings`) | none | 0.8 s (1.2) | 0.5 s (0.75) | 0.8 s (1.2) | **0.5 s (0.75)** |
| Running hero escapes the strike | yes | yes | yes | no: jump, cut or cover | **no**; with the assist, by 12 px |
| Punish window after a miss | none | none | 0.6 s | 2.4 s reload | **0.5 s**, after a turn |

What keeps it fair: two warnings (the 0.5 s notice in its own body, then the wind-up with the whole 160 px line drawn); a jump
always works (low jaws and 64 px of headroom checked at load); contact is harmless; it never strikes over a gap, off a lip or
through a wall; the chase ends at its range and at any refuge more than 24 px up, with a 1.0 s cool-down; locked direction,
fixed reach and timings, so the same dodge works on every retry (D7); the assist makes running work again.

It stays on its floor; leaping up steps and over gaps on an authored route is a later develop of the element `wolf`, as
the climb and the silk drop developed `spider` ([#525](https://github.com/jlogicgames/edgard_in_kimeria/issues/525)). Its first
meeting is a refuge pocket in `forest-glade`, forest-2's fourth room ([ROOM_CARDS.md](ROOM_CARDS.md#forest-glade); the author approved
the card on 2026-11-21): a one-way branch 32 px above its floor, at the near end of its range, out of its reach but in its sight, from
which the hero watches it trot, notice him, sprint under him, bay, give up and trot back (D105). The ledge of that pocket is a one-way
platform: a solid block's face cuts its sight at eye height, so a hero standing on a block beyond its face is not seen (the room's
fallen trunk hides a hero on its top for exactly that reason). The mastered answer there is a step off the branch's end on to its back
while it bays (a stomp); a jump, a swing and the assist's run all work on its floor (`ForestGladeTest`). Sounds (seeds 7301 to 7311, [SOUND_DIRECTION.md](SOUND_DIRECTION.md)): its own call `wolf_notice` (a sharp dog-like bark),
`wolf_run` (a soft paw patter once a gallop cycle while it sprints, in view only), `wolf_windup` (`_long`), `wolf_lunge`, `wolf_bite`,
`wolf_bay` (a double bark); its death plays the shared `bounce`. Art: a crimson pelt in the danger family (#236), the largest Forest
monster ([ART_DIRECTION.md](ART_DIRECTION.md)). Effects are particles on `KimeriaParticleSystem` (`effects.WolfFx`, one per level; the
body's crumble and burn in `effects.RemainsFx`); the only sprites are its sheet (`assets/images/objects/monster-wolf.png`) and its body
(live palette, D117). Code: `entity/enemy/Wolf.java`; tests: `WolfTest` (the fairness pins: a runner bitten without the assist and missed with it, the
window of take-offs that clear the jaws, a hero 24 px up never committed to, the refuge, the bay, the give-up and the cool-down, the
lunge stopping at a gap and at a wall, every weapon in every state, both same-frame ties, the load checks), `WolfFxTest`, `RemainsTest`,
`AllocationBudgetTest.wolfThroughTheRealLevel` (0.2 to 0.6 B/frame before the floor re-check, 0.0 in one run after it), `check_contrast.py` (the wolf and its body, once placed),
`FirstMeetingPocketTest.forestGladeTheWolf`, `ForestGladeTest` and `RoomCardsTest` (the placement, #331).

## Rooms, dangers and skill development

A room should provide a short meaningful section for observation and execution.
Within a level, familiar mechanics recur under different conditions to develop
skill; the next level increases difficulty or adds an element. A stage develops
its leading mechanic over several levels.

**Every room gives something new ([D72](DECISIONS.md#d72-every-room-gives-the-player-something-new), playtest F20).**
Each room introduces, develops or combines at least one element the player has not met in that form; a reskin or
more of the same geometry does not count, and outside the tutorial a room introduces at most two elements, one unknown
at a time. Since [D105](DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) a room is also dense, about one beat every 3 s of the mastered route (3 beats or more),
and a new element is first met in a safe pocket among known beats, not in a room of its own. Familiar mechanics recur
for mastery and fill the room, but never as the whole of it. Every shipped room has a
card in [ROOM_CARDS.md](ROOM_CARDS.md) with its role (lesson, variation, combination, challenge, approach, reward,
rest, boss), purpose, novelty and D19 times, checked against the maps by `RoomCardsTest`; rule and workflow in
[ROOMS.md](ROOMS.md#every-room-gives-something-new-d72). A level plan for campaign planning (#133) is a sequence of
cards whose novelty ledger passes before any map is built. The Primal Planet study (#352, "Reference research")
supports counting a known element on a new surface as a develop and suggests one memorable landmark per room and a
calm, wordless beat after a dangerous one. The 2026-11-16 audit found the Forest's later levels overloaded rather
than repetitive (`forest-1-traps` [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375), `forest-entry` [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376)).

Solutions can involve avoidance, timing, melee/ranged attacks, mechanisms,
carrying or throwing. Every room need not support every option.

Preserve readable danger under [D5](DECISIONS.md#d5-hits-must-be-fair) and
[D7](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements): the player understands
what killed them and can apply that knowledge on retry. New traps keep Accessibility
support for longer warnings.

**Authoring proposals to test:** a safe observation position; light, sound and
movement as clues to hidden dangers; an introduction → variation → combination →
final challenge progression. These are techniques, not a mandatory room template
(the room card of D72 is the mandatory record; the progression is one way to fill its roles).

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
| Movement and combat | Standing look up/down was implemented in #95 and removed in D111; crouching remains open. Visible enemy windups; enemies reacting to the hero's arrival. Vertical ladders are already accepted above. |
| Atmosphere and feedback | Parallax, fireflies, fog, landing/mechanism dust, moving bushes/vines and restrained camera shake. Effect candidates from pixelfx: [#396](https://github.com/jlogicgames/edgard_in_kimeria/issues/396). |

This list does not establish implementation status: old checklist marks refer to
different project versions. Check current behavior against [GAMEPLAY.md](GAMEPLAY.md)
and [feature parity](DEVELOPMENT.md#feature-parity). Candidates such as fog or hit
protection may require explicitly revising previous decisions.

## Open decisions and the existing build

Further discussion must specify:

1. Forest, Swamp and Fortress mechanics, plus concrete Steppe wind rules.
2. Validation of five levels × five rooms per stage, pacing, bosses and fragment acquisition.
3. Double-jump acquisition (including on ladders) (the wall jump was removed, D121); #95 dispatch and #99 ladders are implemented.
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

**Placed (#500, D105, cards approved by the author 2026-11-19):** met in `forest-1-spider` from a one-way step inside its sight and above its line, where it notices and watches without spitting, after the spider and out of view while the spider is met; then on the block top of `forest-1-climb`, where the hero waits for the lift and the spider climbs, and under the ladder slab of `forest-1-route`, before the spider's drop. Room cards: [ROOM_CARDS.md](ROOM_CARDS.md).

### Primal Planet level study (playtest F16, [#352](https://github.com/jlogicgames/edgard_in_kimeria/issues/352))

Checked 2026-11-15. Ideas only: no level, art, name or text of the game is copied. **Method
limit:** nobody on this project played Primal Planet or watched footage for this study. It
rests on three reviews and the store page, each read through a page-summary tool, not
frame by frame. The game is a 2025 solo-developed pixel-art **metroidvania** with survival
and crafting (one open, interconnected map), not a screen-by-screen platformer like Edgard,
so room-structure findings transfer only as principles. The sources say little about traps
and nothing about measured contrast; those rows say so instead of guessing. Review scores
are deliberately not cited (no primary page was read).

Sources, used as the tags below: **Gameluster**
([review](https://gameluster.com/primal-planet-review-a-snappy-dinovania/)), **Into Indie**
([review](https://intoindiegames.com/reviews/primal-planet-review/)), **Tech-Gaming**
([review](https://www.tech-gaming.com/primal-planet/)), **Steam**
([store page](https://store.steampowered.com/app/2350270), developer description).

| Aspect | What the sources report | Takeaway |
| --- | --- | --- |
| Structure | One map of biomes: forest, plains, mountains, ocean depths (Steam). A mobile village hub for healing, upgrades and skills; an ocean side with secrets and breath-hold challenges (Gameluster). Hidden alcoves, alternate routes, and revisits with new abilities; this review says players rarely get completely lost (Tech-Gaming). **The reviews disagree on orientation:** with no objectives or quest log, players loop back through the same caves, thorns and cliffs, and the local maps are cluttered and lack useful markers (Into Indie). | Variety and wayfinding are the contested points; sameness and lost players are the named failure ([#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351)). |
| New elements | Opens with a T. rex chase and gives most of the moveset quickly; double jump and air dash come from skill points; aliens appear fairly early; the water section has its own enemies and items, and there is a boss (Gameluster). Weapons and upgrades arrive slowly (Tech-Gaming). The spear can be set on fire to burn overgrown fields and open new areas, and puzzles build on that (Tech-Gaming). Survival items are introduced with little explanation (Into Indie). Too many skills, some redundant (Gameluster). | One tool that changes how the world can be crossed, introduced slowly, gives rooms something new without new assets; unexplained items are a fault ([#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351)). |
| Creature readability | Enemies move fast and sometimes turn without warning, but encounters are short and the speed is not overwhelming (Gameluster). Early enemy attacks are telegraphed; later enemies hit much harder, often without warning, the difficulty spikes instead of rising gradually, and the reviewer is sometimes destroyed in a room without understanding why (Into Indie). Boss fights need pattern recognition (Tech-Gaming). Enemy sprites read as menacing (Gameluster). | Telegraph first, then raise damage; an unexplained death is the complaint ([#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348)). |
| Traps | Not described by any of the three reviews. Steam lists bait, traps and stealth as player tools. Tech-Gaming says exploration feels risky, with enough unexpected dangers to keep players cautious. | Hazard readability: not established. Surprise danger is praised only in general terms. |
| Colour and contrast | Backgrounds change palette across four times of day, and the ocean is singled out as pretty (Gameluster). Praised colour and detail: jungle treetops, bioluminescent caves, drifting fireflies, shimmering deep water; the review does not say whether hazards separate from the background (Into Indie). Sunsets and shadowy caverns stand out (Tech-Gaming). Complaints are about maps and items: cluttered local maps and hard-to-tell items on a small screen (Into Indie). | Atmosphere is praised; clutter is the complaint, in maps and item lists. Hazard-versus-background contrast: not established ([#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344)). |
| Environmental storytelling | No dialogue; the pixel art carries the story, and a village mural shows a map (Gameluster). Story through movement, music and small moments such as resting by a fire (Into Indie). A hostile, alive jungle and a soundtrack mixing prehistoric instruments with synth (Tech-Gaming). | A single wordless visual beat can carry story and direction at once. |
| Rest vs danger | An intense opening, then a slower loop that the reviewer finds mismatched; checkpoints do not heal; full healing and skills are at the village; enemies and resources respawn quickly so grinding is easy (Gameluster). Rough pacing and a jagged difficulty curve (Into Indie). The customary rhythm of exploration, upgrades and intermittent backtracking, a familiar pattern rather than praise (Tech-Gaming). | Our own principle, not a source finding: a calm beat works as contrast only if the danger after it matches what the calm promised. |

#### Adopt (as principles, in Edgard's own terms)

1. **Give each room one identity and one landmark a player could name afterwards**, so
   rooms do not blur together. This answers the same-caves complaint (Into Indie); it is our
   principle, not something the game is shown to do. [#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351).
2. **A tool or element that changes how the terrain can be crossed** (the way fire opens
   overgrown paths, Tech-Gaming) counts as "something new" for a room, introduced once and
   then reused in later rooms. [#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351).
3. **Telegraph before damage:** a hazard or creature shows its tell on the first encounter,
   and severity rises only once the tell is learned. A death the player cannot explain is a
   defect (Into Indie). [#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348).
4. **Keep encounters short and the rhythm legible** (Gameluster); this matches Edgard's
   instant room retry. [#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348).
5. **An atmospheric background is acceptable only with a clear active layer on top.** The
   reviews praise atmosphere and criticise clutter in maps and item lists; the scene-level
   hazard contrast is not reported, so this is our rule supported only indirectly. Fits the
   three-tier background / decor / active rule. [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344).
6. **Our own principle, not a source finding: a calm beat after a danger beat, told with a
   wordless visual** (the sources show a fire and a mural as wordless visuals, Gameluster and
   Into Indie) rather than text. [#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351).

#### Do not adopt, and why

1. **Open interconnected map, backtracking, survival and crafting loops, grinding:** a
   different genre. Edgard is a room-by-room campaign with instant retry; backtracking and
   resource loops would break the room-novelty rule, which assumes each room is new
   (Gameluster, Tech-Gaming). [#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351).
2. **Intense opening then slower loop, and hard hits without warning:** reviewers name the
   pacing mismatch (Gameluster) and the unannounced hits (Into Indie) as faults. Edgard
   keeps a steady curve and readable hazards. [#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348).
3. **Cluttered maps, no markers and unexplained items** (Into Indie): the recorded weakness;
   we must not reproduce it. [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344)
   and [#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351).
4. **Time-of-day palette shifts as a contrast tool:** they vary the background, which the
   fixed three-tier rule must keep stable, and the sources give no evidence they help
   readability (Gameluster). [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344).
5. **Any concrete hazard, enemy, skill or story element** (flaming spear, T. rex chase,
   alien plot): the value here is the principle, not the content.
