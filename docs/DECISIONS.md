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
- **Exceptions:** a deliberate departure from Katana Zero is recorded as its own decision with
  its reasoning (D11).
- **Tracked in:** #47

## D2. One hit kills, attempts are unlimited

- **Status:** Accepted
- **Decision:** Any fatal hit kills the player instantly. There are no lives, no health, no
  game over. The player retries until it works.
- **Why:** Strict hits only feel fair when failing is cheap. Lives and game over fight that.
- **Tracked in:** #45

## D3. Death resets the current room, and respawn is instant

- **Status:** Accepted
- **Decision:**
  - A level is a chain of **rooms**, as in Katana Zero. A room may span several screens; what
    is capped is how much a retry costs to replay, not the screen count. A level without room
    markers is one room.
  - A mastered required route should replay in **at most 20 seconds**; collecting
    all optional room pickups has a separate **30 second** target. These guide
    room authoring, never impose a timer on the player. Measure active real time,
    including combat, waiting, and slow motion, from usable entrance input to the
    exit contact; exclude loading, pauses, fades, and death/exit presentation.
    Reference routes and results are recorded in [ROOMS.md](ROOMS.md) (#70).
  - On death the **current room** resets the player, surviving enemies and traversal objects;
    new provisional pickups roll back. On its first visit, traps also return to their initial
    state. On a collection revisit, secured pickups and killed enemies stay absent, and traps
    return to the state secured on the previous exit. The player respawns at the room entrance.
    The complete first-visit/revisit policy is in [HEALER.md](HEALER.md).
  - **Rooms already passed keep progression** for the rest of the level: killed enemies stay
    dead and collected pickups stay collected. The optional healer collection return (D6)
    preserves enemy/trap state but restores traversal platforms, switches and linked doors.
    It is distinct from a same-room death reset. See [HEALER.md](HEALER.md).
  - Death plays in slow motion, as in Katana Zero (about 0.7 s of real time at a fifth of
    normal speed), so the player sees what killed them and does not walk into it twice. Only
    the hit animation is cut short, not played to its end. A fall into a pit has nothing to
    look at and skips the slow motion.
  - Then the respawn is instant: well under half a second. The camera returns to the entrance
    with a fast, eased pan of fixed duration (about 0.3 to 0.4 s, whatever the distance), never
    a hard cut, and the player appears as it arrives.
  - Entering the next room uses a whole-scene **fade out → fade in**: about 0.25 s
    each way, in real time. The world and input freeze during it. The room, player
    position, and camera switch only at full black; at least one black frame is
    shown. The hero has no disappearance/appearance animation for this transition.
    Death retains the separate respawn animation and eased camera return above.
  - Rooms are traversed forward through explicit exit zones. Passed rooms are
    frozen with their state retained. The camera is bounded by the current room;
    a room smaller than the viewport is centred on that axis.
  - Tiled authoring and transition behaviour are documented in [ROOMS.md](ROOMS.md).
  - The `Checkpoint` object completes a level without a boss. A boss level completes after
    defeat and its automatic exit walk (D13). There are no mid-level save checkpoints.
- **Why:** Deterministic attempts make precision puzzles learnable, as in Katana Zero. A reset
  that throws the player far back (TMNT: Shredder's Revenge) makes dying tedious instead of
  cheap; keeping passed rooms, as Expendabros keeps what was destroyed, means a death costs
  only the room being attempted. A hard camera cut disorients, so the camera always pans.
- **Tracked in:** #45, #47, #60

Bat proximity does not change time scale; the old proximity slow motion has been removed.
Death retains its own slow-motion presentation. Gameplay levels have no fog; fog remains
only in the menu backdrop.

## D4. Stomp is allowed, from above only

- **Status:** Accepted
- **Decision:** The player kills a bat or mob by landing on it from above, and bounces off.
  The forest boss is the exception: top contact bounces without hurting either actor.
  Its solid sides and underside block passage without damage; only axe strikes kill.
  Ordinary enemies kill the player on side or bottom contact and remain unharmed. The sword is the other way
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
- **Decision:**
  - Hearts are removed, since there is nothing for them to restore.
  - Coins are optional healer payment in `forest` (#82/#84); collection is never required.
    Dialogue, payment and collection returns are described in [HEALER.md](HEALER.md);
    boss combat is described in [BOSS.md](BOSS.md).
  - **Artifacts** are optional: the game can be finished without them. The current test set
    for `forest` is **Chicken + Key**; `forest-1` has no artifacts or full-set reward yet.
    Their current map positions are test placements, not the final challenge layout.
  - The boss always starts at full normal energy. Merely holding Chicken and Key gives
    no bonus. The optional healer bargain consumes Chicken, Key and every forest coin;
    the hero immediately drinks the elixir, granting **10x sword damage to this boss**.
    Starting/max energy is unchanged; only the player's sword damage is multiplied.
    The effect belongs only to this boss encounter and its retries until this boss is defeated;
    clear it immediately on boss defeat. Empowered hits emit 48 thicker droplets with longer trails and shake the
    screen. After committing to the fight, no backward return or onward passage is possible
    until victory. The bargain/dialogue does not repeat on retry. It is run-only and also
    clears on Restart Level, leaving the level or leaving the run.
  - Payment consumes Chicken, Key and every unique authored forest coin, never prior-level
    currency. Collection identities and completion remain recorded after spending. Brewing is
    once per encounter; an empty/undefined authored coin set cannot satisfy the recipe.
  - At healer entry and collection return, secure the current room's pickups and enemy/trap
    state. Collection return teleports to the first room; it is not Restart Level or free
    backward traversal. Restore platforms, switches and dependent traversal chains each time
    a visited room is re-entered; retain enemy/trap state and collected pickup identities.
  - An artifact **counts once secured at room/level exit or healer entry/return**.
    Dying before that returns the new provisional pickup to its place (D3).
  - Pickups appear immediately in a single HUD row after the coin counter, in collection
    order. Their world sprites use two 16x16 frames at 0.3 seconds per frame, like coins.
    Artifacts do not increase the coin counter.
  - Secured artifacts remain available across levels in the live run. Restart Level restores
    the inventory at level entry. New Game, Continue, and leaving the live run clear it.
    Artifacts are not written to disk; the level-only save contract (D8)
    is unchanged. Persistent artifacts require a separate explicit decision.
- **Why:** Optional artifacts put the hardest challenges on side paths, as Celeste does with
  its strawberries, so players who are not after mastery can still finish while those who are
  have a reason to explore.
- **Tracked in:** #46, #47, #60, #72

## D7. Traps are deterministic puzzle elements

- **Status:** Accepted
- **Decision:** Bombs are the first of many planned traps. Each first-visit retry starts from the same
  trap state and warns before killing. On a healer revisit, retain the secured trap state,
  including a partially elapsed warning or prior explosion; death restores that secured
  baseline (D3/D6). Falling platforms are restored traversal objects on collection return.
  Every trap takes its warning time from one shared place, so the assist option for longer
  warnings (D11) applies to every trap, including new ones.
  Implementation rule: obtain `game.trapWarnings()` and call `duration(baseSeconds)` once
  when a timed warning starts. Store that result for the countdown; repeated contact must
  not restart it. Scale only the warning, never movement or cooldowns. Falling platforms use
  `TrapWarnings.FALLING_PLATFORM_SECONDS` (1 s normally, 1.5 s with assist).
- **Contact bombs (#71):** The art-sized 10x13 activation hitbox starts a one-shot countdown
  of `TrapWarnings.DEFAULT_WARNING_SECONDS` (0.8 s normally, 1.2 s with assist), a shared
  default available to future traps. The sprite alternates
  warm tint and normal art every 0.15 s without disappearing. Leaving does not cancel the
  countdown; repeated contact and setting changes do not restart or rescale it. At detonation,
  check the player's current hitbox against a 64x64 square centered on the bomb exactly once,
  then remove the bomb. The cosmetic shader uses a separate 64x64 quad, with its origin
  at the bomb's visible bottom and 85% of its height above that point, so the original plume rises
  from the ground rather than the quad's center. Fade just above the baseline, with no
  visible pixels below it, and fade before removal. The lethal
  square, including its corners, is deliberate: no shader sampling,
  circular hitbox or expanding lethal wave. Assist changes only the warning duration.
- **Future trap contract:** Keep activation, warning and lethal event distinct. Each trap owns
  its state and samples shared timing once at activation. Spawn transient effects through
  `Level.queueSpawn`; never leave damage in a cosmetic effect. Room retries rebuild traps from the applicable initial/secured baseline
  and discard both active and queued effects. No trap base class is needed yet: bombs and
  falling platforms share warning timing but have distinct collection-return retention rules
  and activation/damage logic (see [HEALER.md](HEALER.md)).
- **Tracked in:** #47, #71

## D8. Main menu has Continue and New Game

- **Status:** Accepted
- **Decision:**
  - The player has persistent progress, so the main menu has two entries: **Continue** and
    **New Game**.
  - **Continue** is hidden (not just disabled) until a save exists, because it is impossible
    to continue without having started.
  - **New Game** is always shown. If a save exists, it asks for confirmation before replacing
    it. Confirmation clears the old save; the new run creates a save only on its first
    level completion. Cancelling preserves the old save.
  - Progress is saved when a level is completed (`Checkpoint` or the boss victory walk) and holds the
    next level to play, and nothing else. The first-level boss route completes after its post-boss healer dialogue. It is not saved mid-level or per room.
    Continue starts that level from its beginning. Missing, malformed or out-of-range indices
    hide Continue (for example, after removing levels).
  - Saving is **automatic**. The game writes the save the moment a level is completed, with no
    save button, no prompt and no manual save slots. The player never has to save.
  - Settings and progress share `Settings.PREFS_NAME`, with separate keys. Settings writes
    update only the changed setting; clearing a run removes only `KEY_NEXT_LEVEL`.
  - The save holds **only the level**. Coins and the consumed elixir are run-only (D6), so they are not saved and a
    continued run starts with 0 coins and no elixir. Do not add coins
    to the save format.
  - The MVP order is `forest` (three rooms and boss), then `forest-1` (one room).
    Completing the **last level** opens the ending scroll with the intro music; after
    it finishes or is skipped, delete only the run save and return to the main menu.
    Continue is then hidden and New Game starts a fresh run. There is no level wrap.
    If the application closes before the ending finishes, the previous level-only save
    remains available, so Continue restarts the second level.
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
    before it visibly touches. Without a separate hurtbox the body is both for ordinary enemies;
    the forest boss instead uses a solid harmless body and lethal axe boxes.
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
    enemy alone, except the solid forest boss, whose body blocks without contact damage.
    A sword hit kills ordinary enemies; boss energy and counter rules are in D13.
    A dying enemy is harmless.
  - **One-way platforms** (`Platform`, `FallingPlatform`, `Escalator`) support only from
    above. They are ignored by horizontal collision; their landing test (feet-based, with a
    catch-up margin at rest) is only valid vertically.
- **Why:** Instant death is only acceptable when every death is understandable (D5), and the
  deaths players reported all came from geometry that did not match what was drawn.
- **Known gap:** collision resolution only pushes an actor out of a wall while it is moving
  (`velocity.x != 0`). Nothing in the current levels embeds a stationary actor in a wall, but
  a block that activates around an actor (a toggled `Actionable` wall) could. Not fixed yet.
- **Tracked in:** #44

## D11. Assist mode, a deliberate exception to D1

- **Status:** Accepted
- **Decision:**
  - Settings have an **Assist** section of independent toggles, as in Dead Cells and Celeste,
    not a single "easy mode".
  - Assist never shames or penalises: no "you are playing on easy" labels, no locked endings,
    no reduced rewards.
  - The first option is **longer trap warnings** (D7), under Options → Assist: an independent
    On/Off toggle, off by default, multiplying timed warnings by 1.5. It is saved in the same
    player preferences as the other settings and restored at startup. Other options (game speed,
    wider jump windows, skipping a room, invulnerability) are candidates only, each to be decided on its
    own.
- **Why:** Katana Zero has no assist mode, but the audience is wider than hardcore players.
  One hit kills (D2) stays the default; assist lets more players finish without changing it.
- **Tracked in:** #59

## D12. Jumping needs a fresh press, with a short buffer

- **Status:** Accepted
- **Decision:**
  - A jump needs a new press. Holding the button through a landing never jumps again (no
    auto-bunnyhop).
  - A press made shortly before the player can jump (about 0.1 s, tuned by feel) is buffered
    and fires on the first frame a jump is possible.
  - Coyote time (jumping just after walking off a ledge) already exists and stays.
- **Why:** Holding jump a little too long before a ledge or a trap fired an unintended jump,
  and an early press was silently lost. Both cause deaths the player does not understand (D5).
- **Tracked in:** #58

## D13. Forest boss (#82/#83)

The boss wakes once per attempt, pursues on the ground and attacks at visible axe range.
Its solid body blocks passage without contact damage; top contact bounces. Only active
axe strikes kill. Sword hits emit a conspicuous crimson particle spray, including lethal hits.
Normal melee lasts approximately 0.6222 s (two successive +50% speed increases, author revision
on 2026-10-16); the hero remains at 0.7 s. A landed sword hit during ordinary
pursuit, normal melee or ordinary recovery interrupts that action,
removing its damage and completion shake, then starts a distinct, visibly warned counter.
Further sword hits still damage the boss but cannot interrupt or restart that counter,
including its recovery. After counter recovery, ordinary actions become interruptible again.
Lethal damage always cancels combat. The player can jump away after sword recovery.
Each completed attack produces one mild, room-bounded visual shake, even on a miss.
Death resets the boss to full energy and throne at the boss-room entrance, retaining the elixir.
Boss defeat freezes hero control, shows death, then walks the hero safely through terrain
off the right edge. The world keeps updating throughout: fireflies, fire, platforms and hazards
continue moving, while the scripted hero is protected from damage. Pause still freezes it. Only then does the existing level completion/save path run, exactly once.
Authored checkpoints cannot bypass boss defeat or its victory walk. See [BOSS.md](BOSS.md).


## D14. The healer is Edgard's recurring guide

- **Status:** Accepted, 2026-10-16; opening, pre-boss and post-boss (#86) scenes implemented.
- **Decision:** The healer (знахарь) from the optional pre-boss elixir encounter is a
  recurring character and Edgard's guide through the journey. He narrates the very
  beginning: darkness has descended upon the kingdom, brought about by the evil sorceress
  L'Kardina (Л’Кардина). He provides practical explanations and advice on how to progress
  through the game, as well as story context. He returns after the forest
  boss, following the death animation and automatic hero exit, on a separate dialogue
  screen before the next level. Use the same character and dialogue presentation.
- **Why:** A familiar guide gives continuity between encounters and helps connect the
  hero's victory to the next stage of the story. His significance extends beyond the
  optional elixir bargain; declining it does not remove his role in the story.
- **Constraints:** Do not replay progression dialogue on death/retry or change the fast
  respawn and level-only save contracts. The post-boss dialogue meaning was approved
  on 2026-10-19; no new destination or quest was added. New lore still needs agreement. See [STORY.md](STORY.md) for the character's role, agreed appearances and open
  questions; consult it when extending the narrative.
- **Tracked in:** [#84](https://github.com/jlogicgames/edgard_in_kimeria/issues/84),
  [#86](https://github.com/jlogicgames/edgard_in_kimeria/issues/86).


## D15. Two-level MVP with a finite ending

- **Status:** Accepted by the author, 2026-10-19.
- **Decision:** First play `forest` with three rooms, the pre-boss healer and forest boss.
  The existing death animation, automatic exit and guide dialogue lead to `forest-1`,
  the single-room second level. Its checkpoint leads to a black scrolling ending using
  `IntroScreen` and `audio/intro.wav`, with the same fade and fresh-input skip behavior
  as the opening. EN/UK text says Edgard defeated every enemy, freed the kingdom of
  monsters and brought light back to the world. After the ending, clear the saved run
  and return to the menu; retain settings. Future mechanics expand these two levels.
- **Why:** This is the complete playable MVP loop, with a clear beginning and ending.
- **Persistence:** D8 remains level-only. Save index 1 after the first level's guide
  dialogue; never write index 0 on final completion. See [MVP.md](MVP.md) for scope
  and validation. This supersedes the former final-level wrap behavior.
