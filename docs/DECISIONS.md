# Design decisions

A log of gameplay and product decisions. Each entry says what was decided, why, and where it
is tracked. Technical structure lives in the Architecture section of the [README](../README.md).

**Tie-breaker rule (D1): when a design question is disputed, we do what Katana Zero does.**

Status: *Accepted* means decided but not necessarily built yet; the ticket shows build status.

## D1. Katana Zero is the design reference

- **Status:** Accepted
- **Decision:** The game is a puzzle-platformer with arcade elements. Puzzles and precision
  come first. Any disputed design question is settled by asking what Katana Zero does.
- **Why:** One reference avoids re-arguing every detail and keeps the feel consistent.
- **Tracked in:** #47

## D2. One hit kills, attempts are unlimited

- **Status:** Accepted
- **Decision:** Any fatal hit kills the player instantly. There are no lives, no health, no
  game over. The player retries until it works.
- **Why:** Strict hits only feel fair when failing is cheap. Lives and game over fight that.
- **Tracked in:** #45

## D3. Death resets the whole level, and respawn is instant

- **Status:** Accepted
- **Decision:** On death the entire level returns to its initial state (player, enemies, items,
  traps, coin counter), so every attempt starts identically. Respawn takes well under half a
  second. The respawn point is always the level start. There are no mid-level checkpoints.
  The `Checkpoint` object is the level exit that loads the next level.
- **Why:** Matches Katana Zero. Deterministic attempts are what make precision puzzles
  learnable. Because there are no mid-level saves, levels must stay short.
- **Also applies to** the pause menu's *Restart Level*: it is a fresh attempt at the current
  level, with the coin counter back to its value at level start. It does not touch the save.
- **Tracked in:** #45, #47, #51

## D4. Stomp is allowed, from above only

- **Status:** Accepted
- **Decision:** The player kills a bat or mob by landing on it from above, and bounces off.
  Side or bottom contact kills the player and leaves the enemy alone. The sword is the other way
  to kill, and it cannot be used while airborne, so the stomp is the player's only airborne
  answer. It is described in About and introduced early in the levels.
- **Why:** Gives a second, intuitive way to deal with enemies without special rules.
- **Tracked in:** #44

## D5. Hits must be fair

- **Status:** Accepted
- **Decision:** Hit and stomp checks use entity hitboxes, which are smaller than the sprite frames
  (sizing in D10). No enemy kills from a distance the player cannot see (an enemy hitbox
  overhangs its drawn body by about a pixel, no more), and enemies never teleport.
- **Why:** Instant death is only acceptable when every death is understandable.
- **Tracked in:** #44

## D6. Collectables

- **Status:** Accepted
- **Decision:** Hearts are removed, since there is nothing for them to restore. Coins are a
  proof-of-concept counter only. The real design, to be built later, is N artifacts that must all
  be collected to finish the game. Do not balance around coins.
- **Tracked in:** #46, #47

## D7. Traps are deterministic puzzle elements

- **Status:** Accepted
- **Decision:** Bombs are the first of many planned traps. Every trap behaves the same on each
  attempt and warns the player before it can kill. This is guaranteed by the level reset (D3).
- **Tracked in:** #47

## D8. Main menu has Continue and New Game

- **Status:** Accepted
- **Decision:**
  - The player has persistent progress, so the main menu has two entries: **Continue** and
    **New Game**.
  - **Continue** is hidden (not just disabled) until a save exists, because it is impossible
    to continue without having started.
  - **New Game** is always shown. If a save exists, it asks for confirmation before replacing
    it.
  - Progress is saved when a level is completed (reaching the `Checkpoint` exit) and holds the
    next level to play, and nothing else. It is not saved mid-level, consistent with D3.
    Continue starts that level from its beginning.
  - Saving is **automatic**. The game writes the save the moment a level is completed, with no
    save button, no prompt and no manual save slots. The player never has to save.
  - The save holds **only the level**. Coins are a temporary mechanic (D6) that will be
    removed, so they are not saved and a continued run starts with 0 coins. Do not add coins
    to the save format.
  - Completing the **last level** resets the save to level 1, so Continue then starts the
    game over from the first level.
  - **Continue** (a saved run, from the main menu) is different from **Resume** (unpausing the
    current run, from the pause menu). The names stay distinct.
- **Why:** Standard for games with progress, and the same shape as Katana Zero's menu. The
  confirmation guards a destructive action.
- **Tracked in:** #48

## D9. Left-handed mode swaps the key bindings and mirrors touch

- **Status:** Accepted
- **Decision:**
  - One **Left-handed** option (Options menu, saved across launches) switches both input
    methods at once, immediately, with no restart.
  - **Touch:** the layout is mirrored horizontally (stick right, buttons left), keeping the
    relative arrangement of Jump / Shoot / Attack-Interact. Hit areas mirror with the drawing.
  - **Keyboard:** movement is the **arrow keys**; **Z** jump, **X** attack/interact, **C**
    shoot. The two sets are **disjoint**: the arrow keys work only in this mode, and
    A/D and J/K/L work only in the default mode.
  - **Gamepad:** southpaw layout. The **right stick** moves and the D-pad takes the face
    buttons' place: **Down** jump, **Left**/**Right** attack/interact, **Up** shoot. As with the
    keyboard, the sets are disjoint (left stick and D-pad left/right do not move the player in
    this mode). Start still pauses and menu navigation is identical in both modes.
  - The About screen lists the keyboard bindings for the current mode.
- **Why:** Keeping the default keys live in parallel would leave a second control scheme under
  the hand that is meant to be doing the other job, and A/D sit under the same hand as Z/X/C. A clean
  swap keeps the About text honest and the behaviour predictable. Z/X/C is the usual
  left-hand cluster beside the arrows.
- **Tracked in:** #42

## D10. Hitbox and collision conventions

- **Status:** Accepted
- **Decision:** Every actor follows the same rules, so a new enemy or item cannot reintroduce
  the "dies from nowhere / teleports at a wall" class of bug (#44).
  - **Hitboxes are the single source of truth.** Contact, stomp, sword hits and aggro all use
    the body `Hitbox`, never the sprite box or raw `position`/`size`. Every hitbox is smaller
    than its sprite frame, and the sizing is deliberately in the player's favour: the
    **player's** hitbox is slightly *smaller* than the drawn body (forgiving near misses), an
    **enemy's** body hitbox is slightly *larger* than its drawn body (easy to hit and stomp).
    Anything lethal that is not an actor (a bomb) gets a hitbox that matches the art.
  - **An enemy has two boxes when it needs them.** The body hitbox (generous) is what the sword
    and stomps hit and what terrain collides with. The **hurtbox** (`Enemy.hurtbox`) is what
    kills the player on touch, and never reaches past the drawn art, so an enemy cannot kill
    before it visibly touches. Without a separate hurtbox the body is both.
  - **Hitboxes are rectangles**, for every actor including the bat, so what is drawn in F1 is
    exactly what is tested. A circle `Hitbox` is still supported and tested as a real circle
    (`CollisionUtils.overlaps`), but nothing uses one now.
  - **Turning pivots about the body hitbox centre.** Boxes are authored once, for the
    right-facing sprite, in sprite space. Facing left mirrors them (and the art) about the
    centre of the body hitbox, in `Actor.boundsOf` / `Actor.render`. The body therefore never
    moves when an actor turns. Never mirror about the sprite centre: with an off-centre
    hitbox that shifts the body sideways on every turn, which pushes it into walls.
  - **A melee attack is its own collider**, authored the same way (RedMob's whip, the player's
    sword). It is dangerous only on its active frames, after a wind-up that is long enough to
    react to. Enemies start the wind-up when that collider, as it will be at the strike,
    overlaps the player, so there is no separate trigger distance that can drift out of sync.
    F1 draws attack colliders: yellow while winding up, magenta while dangerous.
  - **A stomp** needs the player moving down and with their feet above the enemy's top edge on
    the previous frame (`Enemy.isStomp`). Any other contact hurts the player and leaves the
    enemy alone. A sword hit kills; a dying enemy is harmless.
  - **One-way platforms** (`Platform`, `FallingPlatform`, `Escalator`) support only from
    above. They are ignored by horizontal collision; their landing test (feet-based, with a
    catch-up margin at rest) is only valid vertically.
- **Why:** Instant death is only acceptable when every death is understandable (D5), and the
  deaths players reported all came from geometry that did not match what was drawn.
- **Known gap:** collision resolution only pushes an actor out of a wall while it is moving
  (`velocity.x != 0`). Nothing in the current levels embeds a stationary actor in a wall, but
  a block that activates around an actor (a toggled `Actionable` wall) could. Not fixed yet.
- **Tracked in:** #44
