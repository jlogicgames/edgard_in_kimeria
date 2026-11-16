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
ladder/bridge, wall, moving platform, thrown stone, existing traps). `forest-1` and `forest-2`
show no tutorial hints. Status: the lever room is built (a dark torch the lever lights and
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
| Forest | Reconfigure the route through visible linked mechanisms: levers change traversal supports such as ladders, bridges, platforms and obstacles. | Accepted direction. The lever introduction is built in `forest-0` (#186); the optional-route variation is built as a second room of `forest-1` (#187, [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15): one lever lowers a ladder and stops the lift that is the mandatory way, and a one-shot falling step guards a fireball refill; automated tests only, not yet played by the author); the combination with an enemy is built as a third room (#104, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15): a RedMob in a tunnel under a spore cloud, and a roof route with a refill; automated tests only, not yet played by the author). |
| Swamp | Quicksand, unreliable footing, bridges, moving and falling platforms. | Ideas from the notes; distinguish its problems from forest switches. |
| Steppe | Wind as a spatial property: gusts, shelter, effects on movement and objects. | Wind accepted; directions, strength, cycles and interactions need design. |
| Fortress | Carrying, placing and throwing a torch; light affects visibility and creatures. | Preferred prototype direction; complete rules not approved. |

The torch was previously considered for Steppe. Wind is now selected there;
light remains the proposed Fortress mechanic rather than a mechanic for every stage.

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

The Mechanics MVP should test an introduction to one visible link, a route choice with
a legible optional consequence, and a combination with a familiar enemy or hazard.
Under D26, every mandatory exit, boss and required fragment must remain reachable from
each secured state produced by supported actions; an optional reward may become
unavailable until Restart Level. Remote or throw activation, one-shot versus reversible
supports, and the recovery rules for each new linked object remain open until a prototype
defines them. (One-shot versus reversible is resolved: see the route variation below.)

**Route variation, built (#187, [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15), 2026-11-15; automated tests only, the author has not played it):**
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

**Combination, built (#104, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15), 2026-11-15; automated tests only, the author has not played it):**
`forest-1` gained a third room after the route room. A RedMob that never moves stands in a 32 px-high tunnel on the floor, and a spore puffball's cloud covers every place from which the sword reaches it, so **melee is a timed risk** (the author's request: a second threat beside the RedMob so the sword does not dominate). The floor through the tunnel is the support-free route and needs no charge; one lever lowers a ladder to the roof, which is the safe route and carries a fireball refill; both routes reach the checkpoint with zero charges. Choices (scripted hero): **timed melee** (6.83 s mastered; a patient hero waits 1.06 s on average and always succeeds; a player who simply walks in from the entry dies every time, at frame 101, because the cloud is always at the same phase there, and succeeds with a pause of 0.15 s or more; a sweep over artificial start phases kills 30 of 60); **fireball from afar** (6.15 s, one charge, then the patient crossing); **roof** (7.67 s, nothing threatens it, the refill pays one charge). D26 holds from every swept state (216 runs). **Assessment, stated plainly:** the sword no longer dominates as an instant answer, and the roof beats an unaware patient melee (7.67 s against 7.89 s) and pays a charge; but for a hero who knows the cycle at a full stock (the usual arrival) the mastered melee is still best by 0.84 s and the refill pays nothing, so the risk is a learning risk. Whether that is a meaningful decision is the author's call after playing it. The reasoning, the two earlier layouts and the numbers are in D68 and ROOMS.md.

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
| Ranged attack | A consumable advantage against distant danger. | Consumable fireball, capacity three and two rare single-charge pickups implemented for the test campaign (#98); campaign economy remains future work. |
| Interaction | Operate a lever or another mechanism. | Up + Action chooses the nearest eligible carryable or mechanism; the smaller Tiled object ID breaks equal-distance ties. While carrying, Up + Action operates a mechanism. See [D30](DECISIONS.md#d30-one-direction-for-pickup-and-mechanism-interaction) and [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175). |
| Pick up and place | Move a stone or torch to change the room situation. | Up + Action picks up a nearby item; Down + Action only puts down a held item. Ranged throws it; one visible slot is shared (#95/#97/#175). Authored Forest puzzles remain #103–#104, Fortress light puzzles #112–#115. |
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
| Beetle | About half the hero's height. Crawls to and fro along its path. | Hears the hero in its awareness area, reacts and runs at the hero; explodes when it gets close. |
| Spider | Same patrol and chase as the beetle. | Bites and kills instead of exploding. |
| Snail | Slow. Crawls along its path like the others. | Sees the hero and spits at them; a long reload between shots. |
| Flying beetle | Looks like a bat. Flies around on its own. | Explodes near the hero. |
| Wolf (or a similar animal) | Fast runner. | Bites and kills, like the dog in Broforce. |

## Rooms, dangers and skill development

A room should provide a short meaningful section for observation and execution.
Within a level, familiar mechanics recur under different conditions to develop
skill; the next level increases difficulty or adds an element. A stage develops
its leading mechanic over several levels.

**Every room gives something new ([D72](DECISIONS.md#d72-every-room-gives-the-player-something-new), playtest F20).**
Each room introduces, develops or combines at least one element the player has not met in that form; a reskin or
more of the same geometry does not count, and outside the tutorial a room introduces at most two elements, each
first met alone. Familiar mechanics still recur for mastery, but never as the whole room. Every shipped room has a
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
what killed them and can apply that knowledge on retry. New traps keep Assist
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
| Movement and combat | Standing look up/down is implemented in #95; crouching remains open. Visible enemy windups; enemies reacting to the hero's arrival. Vertical ladders are already accepted above. |
| Atmosphere and feedback | Parallax, fireflies, fog, landing/mechanism dust, moving bushes/vines and restrained camera shake. |

This list does not establish implementation status: old checklist marks refer to
different project versions. Check current behavior against [GAMEPLAY.md](GAMEPLAY.md)
and [feature parity](DEVELOPMENT.md#feature-parity). Candidates such as fog or hit
protection may require explicitly revising previous decisions.

## Open decisions and the existing build

Further discussion must specify:

1. Forest, Swamp and Fortress mechanics, plus concrete Steppe wind rules.
2. Validation of five levels × five rooms per stage, pacing, bosses and fragment acquisition.
3. Double-jump acquisition (including on ladders) and the existing wall jump's campaign role; #95 dispatch and #99 ladders are implemented.
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
