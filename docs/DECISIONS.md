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
- **Tracked in:** #45, #47

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
- **Decision:** Hit and stomp checks use entity hitboxes, which are smaller than the sprites. No
  enemy kills from a distance the player cannot see, and enemies never teleport.
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
