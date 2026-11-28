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
(seven rooms: the first RedMob behind a log with a falling branch and an exploding stump, a trip-line deadfall with a falling stone and a RedMob, the trap course, a lever lesson room where one lever works a lift and a ladder, a route room with a lever, a ladder and a lift, a guard room with a RedMob in a tunnel under a spore cloud and a lever-lowered roof route with a refill, and a tree to climb), then **forest-2** (six rooms, healer and boss). The Forest order is
`forest-0` → `forest-1` → `forest-2` (boss) ([D32](DECISIONS.md#d32-forest-level-order-and-identity)). After the last level, an ending screen in the opening's page style ([D77](DECISIONS.md#d77-story-screens-are-static-pages-playtest-f2-336-2026-11-16)) uses the opening
music, celebrates the kingdom's liberation and returns to the menu with the run save
removed. Future mechanics will expand these same levels. See [MVP](MVP.md).
The current implementation/review stages are complete for the test version; remaining
release acceptance checks are deferred until those mechanics are added and collected in
[Pre-release TODO](PRE_RELEASE_TODO.md).

## Controls

Three action buttons are **Jump**, **Action** and **Ranged**. Plain Action always
selects the grounded sword, including while carrying. **Up** (a fresh press, no
Action needed, [D126](DECISIONS.md#d126-up-and-down-interact-no-two-button-chords-548-2026-11-22)) picks up the
nearest eligible stone/portable torch or activates the nearest eligible mechanism;
the smaller Tiled object ID breaks equal-distance ties across both types. While
carrying, Up remains available for mechanisms. **Down** (a fresh press) puts down
the held object and does nothing with an empty hand. Up and Down still climb ladders
on hold, and a press spent on an interaction does not also climb. On the touch stick
a vertical counts as an interaction only past about 60 degrees. Ranged
throws the held object without spending ammunition; with an empty hand it spends one
fireball, if available.

The selected interaction uses the same target for its outline and contextual hint.
On keyboard, gamepad and touch in either layout, the vertical direction joined to
Action is consumed until released, so it cannot also climb.
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

Removed (2026-11-20, [D111](DECISIONS.md#d111-look-up-and-down-are-removed-the-camera-no-longer-pans-for-a-look-d25-d29-superseded-513)): there is no standing look. Holding Up or Down while standing still does
nothing by itself; Up and Down keep every other use (ladders, Up picks up and uses, Down puts down, the touch stick, menus). The
camera follows the hero and the room clamp only: sideways with a dead zone, up and down with a slack above the hero and an
ease, so a jump from the floor does not scroll ([D154](DECISIONS.md#d154-the-camera-eases-up-and-down-with-slack-above-the-hero-678-2026-11-27-amends-d24-d111)).

**Esc/Start** pauses. Menus: arrows/Tab navigate, Enter/Space/A confirm, Esc/B back;
Esc/B/Start resumes the pause menu. Debug keys F1 colliders, F2 invulnerability,
F3 effects, F4 next level, F5 checkpoint, F6 next room remain unchanged.

The Forest entrance has a stone, portable torch and a linked light to try the new
controls. The entire first Forest room is the teaching room: contextual pickup,
placement, throw and mechanism labels are available at every position/height,
including retries and collection returns. Later rooms/levels show no such teaching
labels. This replaces the earlier 320×144 rectangle, one-time and 50 px rules.
While holding a stone or torch near a usable lever, show its Up instruction
together with any active Put down/Throw reminder; carrying must never hide the mechanism lesson.
This combined label stays near the lever, with Turn on/Turn off reflecting its state.
Each new pickup arms a fixed-position Put down/Throw reminder. It fades out when
the camera begins scrolling (either way) after the hero leaves its dead zone, and stays hidden
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
the touch buttons show a pictogram for the sword and, on the Ranged button, for throw,
fireball and empty (D138); the labels above the hero distinguish pickup, put-down, mechanism and climb.
On a touch screen the first rooms of the forest tutorial also show a hand dragging the stick, a hand pushing it up at the lever and a ring round the Ranged button while a stone is carried (D139); a keyboard or gamepad player never sees them.
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

`forest-2` contains eight rooms: the entry course (YellowMobs, Bats, two pits, the Bomb), a wall room
with a tree to climb and a leap over two walls (split from the entry by #376, redesigned by #545), the fissure room (two ember fissures and a ledge beetle), the glade (the forest wolf,
met from a one-way branch, #331), the roost (a thorn root, the flying beetle met from under a low ledge and a falling stone, #330), the hollow (two mosquito swarms around a thorn root, #645), a forest course with an ascending
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
`forest-1` has five rooms (`forest-1-mob`, where the first RedMob is met behind a log among a falling branch and a stump (D105), [D85](DECISIONS.md#d85-forest-1-opens-with-the-first-redmob-alone-in-a-room-of-its-own-375-2026-11-17), the trap course, the route room, [D67](DECISIONS.md#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15), the guard room, [D68](DECISIONS.md#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15), and the tree room, [D80](DECISIONS.md#d80-tree-branches-one-way-perches-and-rotten-branches-349-playtest-f13-2026-11-16)): the first four end at an exit like the others and the level checkpoint is on the tree room's crown. See
[Rooms in Tiled](ROOMS.md) for authoring, retry rules, and validation history; deferred checks live in
[Pre-release TODO](PRE_RELEASE_TODO.md).

## Honey fungus and false honey fungus

Two look-alike mushrooms (D63, #295): a tuft of three small caps on the floor, 24x16 px. Both glow the same
gentle way while idle: one fixed frame whose brightness (not hue) breathes smoothly every 2.5 s. When Edgard's body enters a mushroom's zone (its width plus 64 px each side, from 32 px above it to the floor),
it swells for the shared trap warning (0.8 s, 1.2 s with the longer-warnings assist; the value comes from `TrapWarnings`)
and bursts once; afterwards it stays deflated for the rest of the room. Leaving the zone does not stop the swell.
The **honey fungus** (honey-brown caps, **rings** on the stems) is harmless: its burst is a puff of golden spores.
The **false honey fungus** (yellow to orange caps with a green tinge, no rings) is poisonous: its burst is a spray of
lime spore sparks and a ring, filling a 24x24 px square standing on the floor that kills on touch for one second (a death
resets the room as usual).
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
The mosquito swarm is another (below): the sword never kills it, landing on it kills Edgard, and it never bleeds.

## Forest beetle

A green-bronze beetle about half Edgard's height (D100, #327) crawls to and fro along its own stretch of floor and
never leaves it. Touching it is harmless. When Edgard comes within about six tiles on its level it hears him: it stops,
raises its head, wiggles its antennae and hops (half a second, with a dry double click), then scuttles at him, slower than
he runs. When it reaches his feet it commits where it stands: it shakes, blinks brighter and brighter, turns white and
bursts after the shared trap warning (0.8 s, 1.2 s with the longer-warnings assist), whatever he does. Pale specks drawn in
from the edge of the burst show how far it reaches: a low half dome off the ground, five tiles across and two high in the
middle, and only what the dome covers is caught. Outside it, or above it in a jump, Edgard is safe. A fireball or a thrown stone kills it at any moment, even while it winds up: it flips onto
its back under a pale puff instead of bursting, and its body stays. The sword, or landing on it before it commits, makes
it burst at once, under him; landing on one that is already winding up bounces him off. There is no hint about it: it is
met in play, first on the open floor past the guard room's tunnel (`forest-1-guard`; it also crawled at the sound tree's foot in `forest-1-tree` until #500 made that a reward room), then on the exit ledge of the fissure room in forest-2 (`forest-fissure`) (#327); authoring: `Beetle` in `SpawnPoints` with `offNeg`/`offPos` like the mobs, checked at load.
There is no violence or blood option in Settings.

## Forest flying beetle

A crimson beetle in flight, its two wing covers lifted over long clear wings (D145, #330), flies to and fro along a straight level line a little over Edgard's
head, rising over a ledge or a low rock in its way and sinking behind it. Touching it is harmless. It hears him below, up to six tiles to either side: it claps its wing covers and drops, opens
them and tips its head down (half a second, with a high double chirr), then follows along its line, slower than he runs. Once it
is within three tiles over him with a clear way down, it hangs where it is, shaking and blinking, while pale specks gather into it
from all round and others run down a line to the spot where he stands (0.8 s, 1.2 s with the longer-warnings assist). Then it
dives along that line in a quarter of a second and bursts in a half dome off the ground, and only what the dome
covers is caught: five tiles across and two high in the middle. Run out of it, jump
as it bursts, or stand under a ledge, which it cannot dive through. A fireball or a thrown stone or torch kills it at any
moment without a burst, and its body falls to the floor; while it flies level, Edgard must jump to hit it. His sword cannot
reach it up there; if it meets the blade as it comes down, or he lands on it before it commits, it bursts on him; landing on it
once it has committed bounces him off. It never dives over a pit. There is no hint about it: it is met in play, in
`forest-roost`, forest-2's fifth room, first from under a low ledge it flies over
([#330](https://github.com/jlogicgames/edgard_in_kimeria/issues/330)). Authoring: `FlyingBeetle` (40×32) in `SpawnPoints` with `offNeg`/`offPos`, checked at load
(its route clear, its line 40 to 64 px over the surfaces under it, and no burst or dive over the room's entrance, exit or checkpoints).

## Forest spider

A plum-black spider with an ochre chevron and eight high-kneed legs (D102, #328), about half Edgard's height, skitters to
and fro along its own stretch of floor, stop and go, and never leaves it. Touching it is harmless. When Edgard comes within
about nine tiles of it, above, below or level, through rock or not, it feels him (D103): it rises on its legs and drums the ground twice with a raised foreleg (half a
second, with a dry chittering hiss), then runs at him, slower than he runs. Once he is within three tiles of its front it
locks onto the spot where he stands and crouches, forelegs up and fangs open, while pale glints of silk run along the floor
to that spot (0.5 s, 0.75 s with the longer-warnings assist). Then it leaps there in a quarter of a second and bites, with a
snap that is heard whether it hits or not, and lies flat for 0.6 s to recover, harmless. A step back out of the spot makes
it miss; running away always works. Every weapon kills it at any moment, the leap included: a fireball, a thrown stone or
torch, landing on it, or the sword, which bleeds it lightly like any monster. The sword must be swinging when it arrives or
reach it before it leaps; after a miss, its recovery is a free strike. A kill that lands on the same frame as its bite wins.
There is no hint about it: it is met in play. A wall or a crate in its way stops its leap. Authoring: `Spider` (32×16) in `SpawnPoints` with `offNeg`/`offPos` like
the mobs, checked at load (its pounce box at every point of its range must keep clear of the room's entrance, exit and
checkpoints).

**Climbing and the silk drop (D103, #483).** A spider may own a longer route: floors, the walls between them and a silk
thread hanging from the lip of a ledge. It climbs walls slower than it runs, belly to the face, and never strikes from one; a
spider chasing Edgard comes down to the floor to pounce. At the lip of its ledge it waits over the edge. When Edgard walks
under it, it hangs head down over the lip while glints run down the column below it (the same 0.5 s, 0.75 s with the assist),
then drops on its thread in a fraction of a second, bites at the thread's end just above the floor (heard hit or miss), hangs
there harmless for 0.6 s and climbs back up, still harmless. One step aside makes it miss. Every weapon kills it on a wall or
on its thread; its body falls to the floor below, and its thread snaps and fades. Authoring: a `Spider` polyline in
`SpawnPoints` along its floors and walls, ending in a thread down from a lip, checked at load (every drop can be stepped out
of and keeps clear of the entrance, exit and checkpoints, and the whole route is on screen).

**Where it is met (#484, #500).** In forest-1, in that order: on open ground in `forest-1-spider` (the pounce), before the snail and with the snail off screen; climbing
the far face of the block where Edgard must wait for the lift in `forest-1-climb`, beside a snail on the block top (it comes up the face and along the block's
top to him, so he baits its pounce there, shoots it as it tops the edge, or is bitten waiting); and on the ladder slab of `forest-1-route`, whose thread hangs over the floor Edgard
must walk (keep walking and it drops behind him; stop under the lip and it bites). Room cards: [ROOM_CARDS.md](ROOM_CARDS.md).

## Forest snail

A chestnut snail with a coiled shell and two eye stalks (D106, #329), about half Edgard's height, crawls very slowly along
its own stretch of floor, stops at each end and turns. Touching it is harmless. It only looks ahead: Edgard behind it, or
behind a block or a crate, is not seen. When he comes into its view, up to eleven tiles in front of it, its stalks shoot up and
pull in twice (half a second, with a wet "mm?"), and it holds still, facing him; it never chases. If he is on its line, at
shin height, it rears back, its throat swells and pale specks are drawn in along that line to its mouth (0.8 s, 1.2 s with
the longer-warnings assist); then it spits one gob that flies straight and level, faster than Edgard runs. A jump clears it,
the sword cuts it, and a fireball, a thrown stone or a torch breaks it and flies on. Otherwise it splats on him, on the first
wall, or on the floor just past its eleven tiles, with a wet slap heard hit or miss. Then it reloads for 2.4 s, harmless:
that is the time to cross its ground. Aware of him, it turns round if he goes behind it, and forgets him after a second and
a half out of its sight. Every weapon kills it: the sword (which bleeds it lightly, like any monster), a fireball, a thrown
stone or torch, or landing on it. Killed while it winds up, it spits nothing; a gob already in the air flies on. There is no
hint about it: it is met in play. Authoring: `Snail` (24×16) in `SpawnPoints` with `offNeg`/`offPos` like the mobs, checked at
load (from every point of its range, both ways, its shot must end on a wall or a floor inside the room and never cross the
room's entrance, exit or checkpoints). Where it is met: with the spider rooms' rework (D105, [#499](https://github.com/jlogicgames/edgard_in_kimeria/issues/499)).

## Forest wolf

A lean crimson wolf (D115, #331), the largest Forest monster, trots slowly along its own stretch of floor, stops to sniff at each
end and turns. Touching it is harmless. It sees far ahead (sixteen tiles, a band a little taller than Edgard, cut by any
wall or crate at its eye height) and hears close by (four tiles, through anything). When it notices him it stops, pricks its ears,
bristles and scrapes a paw (half a second, with a sharp bark), then sprints at him, faster than he runs, keeping to its own stretch
of floor. Within four and a half tiles of its muzzle it stops where it is and crouches, its breath streaks drawn along the whole
line its lunge will cover (half a second, three quarters with the longer-warnings assist), then lunges ten tiles along that line
in a quarter of a second, low over the floor, and bites, with a heavy clack heard hit or miss. Only the jaws kill. It cannot be
outrun without the assist: a jump, timed within about half a second of its crouch, carries Edgard over the jaws (its jaws are low and the
rooms it lives in have headroom to jump); the sword, a fireball or a stone kills it as it comes; and anything more than a block
and a half up is out of its reach: it runs to the nearest point it can reach, bays (a double bark), gives up after a second and a
half and trots on, and will not notice him again until he has been out of its sight for a second. After a bite it shakes its head
for half a second, harmless, and chases again with a new crouch. It never goes off its floor or through a wall. Every weapon kills
it: the sword, a fireball, a stone or a torch, or landing on it. It leaves its body lying flat on its side, which twitches when he
comes near. There is no hint about it: it is met in play. Authoring: `Wolf` (48×24) in `SpawnPoints` with `offNeg`/`offPos` like the
mobs, checked at load (a range of at least ten tiles on floor without gaps, nothing of the entrance, exit or checkpoints in its sight,
and four tiles of headroom over its reach). Where it is met: `forest-glade`, forest-2's fourth room, from a one-way branch ([#331](https://github.com/jlogicgames/edgard_in_kimeria/issues/331)).

## Mosquito swarm

A loose cloud of mosquitoes, pale and rose dots (D151, #645), a little narrower than Edgard is tall, drifts to and fro
along a straight line, level or up and down, like a bat but half as fast. It never notices him. Touching its thick middle
kills, from any side, even while he swings, and landing on it kills him too; the strays at its edge are harmless. His sword
cannot kill it: a swing blows the mosquitoes a little further from him, and in under a
second they gather again; the blade passes on to whatever is behind. A thrown stone or torch or a fireball kills the whole
swarm at once; its mosquitoes fall and fade, the stone drops there and can be picked up again. Without a shot, pass it by
jumping over a low one as it comes towards him (jumping after it is harder, and a jump on the spot never clears it) or walking under a high one. It flies
in silence; a thin, quiet whine is heard when it darts out of the sword's way, and a few when it dies. There is no hint about it: it is met in play, in `forest-hollow`, forest-2's sixth
room, first with a stone beside him ([#645](https://github.com/jlogicgames/edgard_in_kimeria/issues/645)). Authoring:
`MosquitoSwarm` (22×18) in `SpawnPoints` with `offNeg`/`offPos` and `isVertical`, checked at load (a free way past, floor
under it, and nothing solid, no falling branch or stone dropping through, nor the room's entrance, exit or checkpoints,
within its line).

**Monster bodies (D101).** A monster killed without bursting leaves its body, in a pose no living one takes (the beetle
on its back, the flying beetle on its back with one wing flat, the spider on its back with its legs curled up, the snail's empty shell rolled over, its mouth open upward, the wolf lying flat on its side, the bat on its side with one wing spread flat, the red mob in a low heap, the yellow mob
face down on its skull mask), so it never looks like a monster at rest. Bodies are harmless and in nobody's way. They rock when Edgard
or a monster walks past (the big ones, the wolf and the two mobs, twitch instead). The sword or landing on one crumbles it (landing bounces him as on a monster), and a
fireball or a thrown torch burns it up and flies on; a thrown stone flies straight through it (D119). No weapon is used up or stopped by a body. A burst leaves no body. Bodies last
until Edgard leaves the room. A death resets the room with the monster alive again, and a passed room shows neither the
monster nor its body. A sword kill leaves the body too: the swing that killed the monster does not crumble it. A bat or a
flying beetle killed in the air falls to the floor below; over a pit nothing stays. The mosquito swarm and the boss never leave
a body.

Options -> Accessibility has an **Instant text** toggle (off by default): healer dialogue lines then appear whole instead of
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
