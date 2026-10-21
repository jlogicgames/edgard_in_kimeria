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

Controls are three action buttons — **Jump**, **Shoot** and a context-sensitive
**Attack/Interact** — plus movement. **A / D** move, **J** jump, **K** attack (or
interact while the player is inside a trigger zone — walls, torches, escalators), **L** shoot,
**Esc** pause. Gamepad: **left stick / D-pad** move, **South** jump, **West**/**East**
attack/interact, **North** shoot, **Start** pause. Touch devices get an on-screen stick and the
same three buttons (stick left, buttons right); the Attack/Interact button relabels itself inside
a trigger zone. **Left-handed mode** (Options) mirrors all three. Touch: stick right, buttons
left. Keyboard: **arrows** move, **Z** jump, **X** attack/interact, **C** shoot (the two key sets
are disjoint, so the arrows work only in this mode). Gamepad: **right stick** moves and the
D-pad takes the face buttons' place (**Down** jump, **Left**/**Right** attack/interact, **Up**
shoot); menus and **Start** are the same in both modes. Shoot is bound everywhere but inert until the ranged attack (fireball or throwing knife) is built. Menus: **arrows/Tab** navigate, **Enter/Space/A** confirm,
**Esc/B** back (Esc/B/Start resume from the pause menu, which also offers **Restart Level**; on the desktop main menu they select
Exit). Debug keys: **F1** hitbox gizmos, **F2**
invulnerability, **F3** spawn shockwave + ripple, **F4** advance level, **F5** reach a
checkpoint, **F6** advance to the next room (no action in the last room).
To test the forest boss from a new run: F4 to forest, then F6 twice. The second transition
opens the healer dialogue; choose the fight to enter the boss chamber.

**Future design:** [D16](DECISIONS.md#d16-expanded-campaign-wind-ladders-and-carried-objects)
adds vertical ladders and one carried object, with sword attacks still available,
pick-up/put-down on interaction and throwing on the ranged command. These are
planned rules, not available controls in this build. The author has accepted
Action = sword, Down + Action = pick up/put down, Up + Action = mechanism,
and Ranged = held-object throw or empty-handed fireball. Implementation is tracked
in [#95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95); target selection,
ladder integration and device feedback still need work. Double jump from the Steppe
is also future design; see [D18](DECISIONS.md#d18-directional-controls-steppe-double-jump-and-replay-target)
and [player tools](GAME_DESIGN.md#player-tools).

The web build opens on a single-button start screen (click, Enter or Space): browsers block audio
until the page has had a user gesture, so the menu music waits for it. Other platforms skip it.

## Rooms

`forest` contains three rooms: the original course, a forest course with an ascending
optional coin route, and the authored boss chamber. The boss starts on a throne; sword
hits are required to defeat him. Top contact bounces safely; the solid body blocks passage
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

Artifacts alone give no boss bonus. The optional healer elixir (#84) grants **10x sword
damage against the forest boss**, with unchanged starting/max energy (12). The healer requires Chicken, Key and **all six unique coins authored in this forest**;
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
