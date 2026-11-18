# Gameplay reference

Detailed controls, current game scope, room flow and optional collectibles.
See the [documentation index](README.md) for development and design records.

## Project background and current scope

**[Play in your browser](https://jlogicgames.github.io/edgard_in_kimeria/)** — the
web build, deployed automatically from `main` (see
[`.github/workflows/deploy-web.yml`](../.github/workflows/deploy-web.yml)).

A 2D platformer, ported to Java and [libGDX](https://libgdx.com/) from the project's
Flutter/Flame version (with reference to its separate Rust/Bevy version for feature parity —
full menu system, localization, gamepad support, dev hotkeys).

The MVP has three playable levels: the tutorial **forest-0** (six empty rooms), **forest-1**
(seven rooms: the first RedMob alone, a trip-line deadfall alone, the trap course, a lever lesson room where one lever works a lift and a ladder, a route room with a lever, a ladder and a lift, a guard room with a RedMob in a tunnel under a spore cloud and a lever-lowered roof route with a refill, and a tree to climb), then **forest-2** (four rooms, healer and boss). The Forest order is
`forest-0` → `forest-1` → `forest-2` (boss) ([D32](DECISIONS.md#d32-forest-level-order-and-identity)). After the last level, an ending screen in the opening's page style ([D77](DECISIONS.md#d77-story-screens-are-static-pages-playtest-f2-336-2026-11-16)) uses the opening
music, celebrates the kingdom's liberation and returns to the menu with the run save
removed. Future mechanics will expand these same levels. See [MVP](MVP.md).
The current implementation/review stages are complete for the test version; remaining
release acceptance checks are deferred until those mechanics are added and collected in
[Pre-release TODO](PRE_RELEASE_TODO.md).

## Controls

Three action buttons are **Jump**, **Action** and **Ranged**. Plain Action always
selects the grounded sword, including while carrying. **Up + Action** picks up the
nearest eligible stone/portable torch or activates the nearest eligible mechanism;
the smaller Tiled object ID breaks equal-distance ties across both types. While
carrying, Up + Action remains available for mechanisms. **Down + Action** puts down
the held object and does nothing with an empty hand. Conflicting Up+Down consumes
Action without doing anything. Holding Action never repeats an interaction. Ranged
throws the held object without spending ammunition; with an empty hand it spends one
fireball, if available.

The selected interaction uses the same target for its outline and contextual hint.
On keyboard, gamepad and touch in either layout, the vertical direction joined to
Action is consumed until released, so it cannot also start looking or climbing.
See [Directional actions](DIRECTIONAL_CONTROLS.md#interaction-controls-175) for the
full contract and device bindings.
**Ladders:** Up beside a ladder (Down from its top surface) takes hold. At a grounded platform
at least one third down the ladder, Up or Down takes hold; on its upper span and at the bottom,
only Up does. The ladder's top is a one-way support. Up/Down climb, Left/Right steps off into
normal movement, and Jump lets go with a hop. Ranged and mechanisms still work while attached;
the sword and pick up/put down do not. The climbing animation advances only during vertical
movement. Up/Down held
with Action never climbs ([D28](DECISIONS.md#d28-vertical-ladders)).

| Device | Normal | Left-handed (Options) |
| --- | --- | --- |
| Keyboard | A/D horizontal, W/S vertical; J jump, K Action, L Ranged | Arrows horizontal/vertical; Z jump, X Action, C Ranged |
| Gamepad | Left stick/D-pad horizontal/vertical; A jump, X/B Action, Y Ranged | Right stick horizontal/vertical; D-pad Down jump, Left/Right Action, Up Ranged |
| Touch | Two-axis stick left, three buttons right | Stick right, buttons left |

Standing still on solid ground, hold Up or Down without Action/Ranged to look.
The corresponding static head pose replaces idle immediately. After a continuous
0.3-second hold, the camera eases vertically over one second: 100 logical pixels
up or 150 down. Release, reverse, walk, jump, attack or interact to cancel/restart the hold;
the camera eases back over 0.2 seconds, matching the immediate return to idle. Conflicting Up+Down selects no look.
The full inspection distance is allowed even outside the current room or with
the hero temporarily outside the view; horizontal room bounds remain intact. Deliberate inspection is an explicit exception to the ordinary
vertical room clamp: current Forest rooms are only 8 px taller than the viewport.
This changes neither hitboxes nor movement and is not crouching; ladder climbing is separate (Up takes hold beside a ladder).
UI/layout boundaries discard the old hold and require vertical release before
inspection can restart. A carried object stays in the unchanged front palm.
When a reachable pickup or usable mechanism is available, allow a one-second
hold before moving the camera, giving its pickup/activation chord priority over
inspection. Choose this delay at the beginning of each hold; other holds keep
0.3 seconds. A carry-only Put down/Throw reminder does not extend the delay.
Target eligibility governs this rule even if the held look direction hides the
other direction's teaching label, or a later room omits teaching text.
All devices use their active movement directions; mirrored gamepad action D-pad
buttons never double as look directions.
When an eligible mechanism or carryable is selected and outlined, standing look is
suppressed until the outline disappears; an existing inspection returns to the ordinary
camera target over 0.2 s ([D29](DECISIONS.md#d29-interaction-outline-takes-priority-over-standing-look)).

**Esc/Start** pauses. Menus: arrows/Tab navigate, Enter/Space/A confirm, Esc/B back;
Esc/B/Start resumes the pause menu. Debug keys F1 colliders, F2 invulnerability,
F3 effects, F4 next level, F5 checkpoint, F6 next room remain unchanged.

The Forest entrance has a stone, portable torch and a linked light to try the new
controls. The entire first Forest room is the teaching room: contextual pickup,
placement, throw and mechanism labels are available at every position/height,
including retries and collection returns. Later rooms/levels show no such teaching
labels. This replaces the earlier 320×144 rectangle, one-time and 50 px rules.
While holding a stone or torch near a usable lever, show its Up + Action instruction
together with any active Put down/Throw reminder; carrying must never hide the mechanism lesson.
This combined label stays near the lever, with Turn on/Turn off reflecting its state.
Each new pickup arms a fixed-position Put down/Throw reminder. It fades out when
the camera begins scrolling after the hero leaves its dead zone, and stays hidden
for the rest of that pickup. Stopping/returning does not revive it; picking up an
object again in the first room does. A room-edge camera clamp does not dismiss it
merely because the hero walked, and shake/respawn pans do not count. Mechanism
lessons remain repeatable even after the carrying reminder has expired.
Labels slide 12 logical pixels upward: below → normal position while fading
in over 0.18 s, then normal position → above while fading out over 0.25 s.
There is no bottom control panel. [D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket)
accepts exactly one carryable slot, shared by stone and torch, with its object
always visible in the animated front hand. There is no separate pocket. Both stone
and torch follow the front palm through turning, movement and jumps and render
after the hero. World items retain tiny hard-edged pixel art; HUD icons use
separate detailed images.
Coin and fireball icons use equal 32×32 logical slots. Coins retain their count;
fireballs use three fixed icons without a number, with spent charges darkened.
Fireball stock is at the top right in release launches. Development launches
reserve the far-right column for FPS and shift stock left to avoid overlap.
Pickup requires contact within a 3 px tolerance;
touch labels also distinguish sword, pickup, put-down, no action, mechanism, throw
and fireball.
A new/continued run starts with three charges, the maximum held at once. Stock
persists between the two Forest levels and restores to room
entry stock on death. Sword and fireballs can damage the boss. Full behavior, target
selection, recovery, test evidence and the explicit **#99 ladder dependency** are in
[Directional actions](DIRECTIONAL_CONTROLS.md). Two rare pickups each restore one
charge automatically on contact: before the Forest boss entrance and above a moving
platform in level two. Full stock leaves the pickup in place and shows a local
“Fireball inventory full” hint using the mechanism hint fade/slide system in every
room. Steppe double jump remains separate work. Ladders are implemented ([D28](DECISIONS.md#d28-vertical-ladders)):
Up takes hold beside rungs, Down from a top surface, Jump lets go; see [Ladders](DIRECTIONAL_CONTROLS.md#ladders-99).

The web build opens on a single-button start screen (click, Enter or Space): browsers block audio
until the page has had a user gesture, so the menu music waits for it. Other platforms skip it.

## Rooms

`forest-2` contains four rooms: the entry course (YellowMobs, quicksand, the Bomb), a wall room
with two wall jumps (split from the entry by #376), a forest course with an ascending
optional coin route, and the authored boss chamber. The boss starts on a throne; sword
and fireball hits can defeat him. Top contact bounces safely; the solid body blocks passage
without contact damage.
Interrupting a normal strike triggers a flashing, uninterruptible counterattack: jump away.
Victory plays the boss death and an automatic hero exit, then the healer dialogue,
before completing/saving the level.
The boss HUD uses twelve replaceable PNG segments, blood particles/impact sounds, and a short
refill cue during the existing retry animation. A potion icon marks the active 10x effect.
See [Boss combat](BOSS.md) for art, balance and validation details.
The healer is Edgard's recurring guide and opening narrator: he tells of the kingdom's
fall into darkness caused by the evil witch L'Kardina, and guides progression through
the game. The opening identifies him as the narrator. Before the boss, a separate NES-style dialogue
offers an optional paid elixir or collection return (#84). The post-victory dialogue (#86)
acknowledges the victory and establishes his ongoing guidance before the next level;
[Story and characters](STORY.md) records his role and the basis for future narrative decisions.
At the first room's exit, the scene fades out and in (0.25 seconds each way).
The hero and camera relocate under full black, without a hero transition animation.
Death resets only the active room; previous rooms' collections remain kept.
`forest-1` has five rooms (`forest-1-mob`, where the first RedMob is met alone, [D85](DECISIONS.md#d85-forest-1-opens-with-the-first-redmob-alone-in-a-room-of-its-own-375-2026-11-17), the trap course, the route room, [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15), the guard room, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15), and the tree room, [D80](DECISIONS.md#d80-tree-branches-one-way-perches-and-rotten-branches-349-playtest-f13-2026-11-16)): the first four end at an exit like the others and the level checkpoint is on the tree room's crown. See
[Rooms in Tiled](ROOMS.md) for authoring, retry rules, and validation history; deferred checks live in
[Pre-release TODO](PRE_RELEASE_TODO.md).

## Honey fungus and false honey fungus

Two look-alike mushrooms (D63, #295): a tuft of three small caps on the floor, 24x16 px. Both glow the same
gentle way while idle: one fixed frame whose brightness (not hue) breathes smoothly every 2.5 s. When Edgard's body enters a mushroom's zone (its width plus 64 px each side, from 32 px above it to the floor),
it swells for the shared trap warning (0.8 s, 1.2 s with the longer-warnings assist; the value comes from `TrapWarnings`)
and bursts once; afterwards it stays deflated for the rest of the room. Leaving the zone does not stop the swell.
The **honey fungus** (honey-brown caps, **rings** on the stems) is harmless: its burst is a puff of golden spores.
The **false honey fungus** (yellow to orange caps with a green tinge, no rings) is poisonous: its burst is a lime
spore cloud, 24x24 px standing on the floor, that kills on touch for one second (a death resets the room as usual).
Jump over the live cloud, or step back and wait for it to pass. It fires once and never re-arms.
`forest-0` shows the harmless one twice (the lever room and the wall room); the false one waits in the `forest-2`
canopy at x 1672. Authoring: [Rooms in Tiled](ROOMS.md) and the level-authoring skill.

## Optional artifacts

`forest-2` has two optional artifacts: Bat and Mushroom (D43, replacing Chicken and Key), each a
two-frame 16×16 strip (`assets/images/objects/bat.png`, `mushroom.png`).
Tiled uses object type `Collectable` with the name `Bat` or `Mushroom` (the object type `Bat` is the enemy). The author accepted their current positions as final for the MVP on 2026-10-19;
no relocation or additional artifact challenge paths are planned for this MVP (#72).
World items animate like
coins; collected icons appear in one row beside the coin counter, without adding coins.

Items are provisional until the room/level exit or healer entry/return. Death restores
only new provisional items;
secured artifacts survive later deaths and level transitions. Restart Level restores its
entry inventory. A new/continued run or exit to the main menu clears it.
No artifact data is written to preferences or saves.

Artifacts alone give no boss bonus. The optional healer elixir (#84) grants **10x physical
sword damage against the forest boss**; a magical fireball always removes 2 energy, with
or without the elixir. The boss starts with 12 energy and six landed fireballs defeat it.
The healer requires Bat, Mushroom and **all six unique coins authored in this forest**;
prior-level coins do not count and are not spent. Payment consumes the two artifacts and
forest coins; collection completion remains recorded separately. The elixir is drunk at once.
It applies only to this boss, including retries until victory, and ends on boss defeat.
Empowered sword hits produce 48 thicker blood droplets with longer trails and a mild screen shake.
Restart Level, transition, New Game, Continue and leaving the run clear the effect.

Every monster dies from one hit. A monster killed by the sword also sprays a light burst of 12 crimson blood
droplets away from the hero (#319); one killed by a fireball, a thrown stone or a stomp dies without any spray.
The forest beetle is the exception (below): the sword and a stomp set it off instead of killing it, and it never bleeds.

## Forest beetle

A green-bronze beetle about half Edgard's height (D100, #327) crawls to and fro along its own stretch of floor and
never leaves it. Touching it is harmless. When Edgard comes within about six tiles on its level it hears him: it stops,
raises its head, wiggles its antennae and hops (half a second, with a dry double click), then scuttles at him, slower than
he runs. When it reaches his feet it commits where it stands: it shakes, blinks brighter and brighter, turns white and
bursts after the shared trap warning (0.8 s, 1.2 s with the longer-warnings assist), whatever he does. Pale specks drawn in
from the edges of the burst show how far it reaches: a low, wide box, five tiles across and two high. Outside it, or above
it in a jump, Edgard is safe. A fireball or a thrown stone kills it at any moment, even while it winds up: it flips onto
its back under a pale puff instead of bursting, and its body stays. The sword, or landing on it before it commits, makes
it burst at once, under him; landing on one that is already winding up bounces him off. There is no hint about it: it is
met in play. No shipped
room places one yet (#327); authoring: `Beetle` in `SpawnPoints` with `offNeg`/`offPos` like the mobs, checked at load.
There is no violence or blood option in Settings.

**Monster bodies (D101).** A monster killed without bursting leaves its body, in a pose no living one takes (the beetle
on its back), so it never looks like a monster at rest. Bodies are harmless and in nobody's way. They rock when Edgard
walks past. The sword, a thrown stone or landing on one crumbles it (landing bounces him as on a monster), and a
fireball or a thrown torch burns it up. No weapon is used up or stopped by a body. A burst leaves no body. Bodies last
until Edgard leaves the room. A death resets the room with the monster alive again, and a passed room shows neither the
monster nor its body. The beetle is the first; the other monsters follow in [#478](https://github.com/jlogicgames/edgard_in_kimeria/issues/478).

Options -> Assist has an **Instant text** toggle (off by default): healer dialogue lines then appear whole instead of
letter by letter, and one press moves to the next line.

Before committing to the fight, return to the forest entrance as often as needed. Secured
pickups, enemy state, traps and mutable traversal-object state are retained on collection
return. A spent falling platform or changed mechanism may leave an optional pickup route
closed until Restart Level; the mandatory canopy exit and boss path remain open. Previously
collected items and killed enemies never respawn on return. Runtime retention is tracked in
#158; acceptance for each campaign map is verified separately. See the [state policy](HEALER.md#state-policy)
and [authoring contract](ROOMS.md#puzzle-platformer-authoring-contract-d26).
After choosing the fight, neither backward return nor progression past the boss is possible
until victory. See [Healer dialogue and returns](HEALER.md) for input, payment,
room-death rollback, traversal-state policy and validation.
