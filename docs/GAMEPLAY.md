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

The MVP has two playable levels: **forest** (three rooms, healer and boss), followed
by **forest-1** (one room). After the second level, an ending scroll uses the opening
music, celebrates the kingdom's liberation and returns to the menu with the run save
removed. Future mechanics will expand these same two levels. See [MVP](MVP.md).
The current implementation/review stages are complete for the test version; remaining
release acceptance checks are deferred until those mechanics are added and collected in
[Pre-release TODO](PRE_RELEASE_TODO.md).

## Controls

Three action buttons are **Jump**, **Action** and **Ranged**. Plain Action always
selects the grounded sword, including while carrying. **Down + Action** picks up
one nearby object or puts it down; **Up + Action** operates one selected mechanism.
Conflicting Up+Down consumes Action without doing anything. Holding Action never
repeats an interaction. Ranged throws the held object without spending ammunition;
with an empty hand it spends one fireball, if available.

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
This changes neither hitboxes nor movement and is not crouching or ladder climbing.
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
Coin and fireball icons use equal 32×32 logical slots and matching count text.
Fireball stock is at the top right in release launches. Development launches
reserve the far-right column for FPS and shift stock left to avoid overlap.
Pickup requires contact within a 3 px tolerance;
touch labels also distinguish sword, pickup/place, mechanism, throw and fireball.
Stock starts at three, persists between the two Forest levels and restores to room
entry stock on death. Sword and fireballs can damage the boss. Full behavior, target
selection, recovery, test evidence and the explicit **#99 ladder dependency** are in
[Directional actions](DIRECTIONAL_CONTROLS.md). Ladders, rare refills and Steppe double
jump remain separate work; no available ladder controls are claimed.

The web build opens on a single-button start screen (click, Enter or Space): browsers block audio
until the page has had a user gesture, so the menu music waits for it. Other platforms skip it.

## Rooms

`forest` contains three rooms: the original course, a forest course with an ascending
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
`forest-1` has no markers and continues to behave as one room. See
[Rooms in Tiled](ROOMS.md) for authoring, retry rules, and validation history; deferred checks live in
[Pre-release TODO](PRE_RELEASE_TODO.md).

## Optional artifacts

`forest` has two optional artifacts: Chicken and Key, using the existing `Items.png` frames.
Tiled accepts object types `Chicken` and `Key`, or `Collectable` with the
item name. The author accepted their current positions as final for the MVP on 2026-10-19;
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
The healer requires Chicken, Key and **all six unique coins authored in this forest**;
prior-level coins do not count and are not spent. Payment consumes the two artifacts and
forest coins; collection completion remains recorded separately. The elixir is drunk at once.
It applies only to this boss, including retries until victory, and ends on boss defeat.
Empowered sword hits produce 48 thicker blood droplets with longer trails and a mild screen shake.
Restart Level, transition, New Game, Continue and leaving the run clear the effect.

Before committing to the fight, return to the forest entrance as often as needed. Secured
pickups, enemy state and trap state are retained; platforms and switches/linked traversal
objects are restored. Previously collected items and killed enemies never respawn on return.
After choosing the fight, neither backward return nor progression past the boss is possible
until victory. See [Healer dialogue and returns](HEALER.md) for input, payment,
room-death rollback, traversal reset policy and validation.
