# Design decisions

A log of gameplay and product decisions. Each entry says what was decided, why, and where it
is tracked. Technical structure lives in the [development guide](DEVELOPMENT.md#architecture).

The [full game design](GAME_DESIGN.md) records the expanded campaign. Later entries
explicitly marked as future design do not claim that the current test build implements them.

**Tie-breaker rule (D1): when a design question is disputed, we do what Katana Zero does.**

Status: *Accepted* means decided but not necessarily built yet; the ticket shows build status.
Current test-version implementation/review stages are complete by author decision
(2026-10-20). Remaining release acceptance checks are deferred until the planned gameplay
mechanics are added; see [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Design contracts and
verification of subsequent implementation changes still apply.

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
  - A mastered route between recovery points should replay in **about 20 seconds**
    (latest author hypothesis, 2026-10-20; D19), replacing D18's 40-second target.
    Measure required and all-pickup routes separately. Do not pad shorter rooms to
    reach 20 seconds or exclude a long route from review. First-time learning until
    the first successful exit, including observation and attempts, has a separate
    **about 60 seconds** hypothesis for ordinary rooms. These are playtest targets,
    not verified reference-game averages or hard player deadlines. This guides room
    authoring, never imposes a timer on the player. Measure active real time,
    including combat, waiting, and slow motion, from usable entrance input to the
    exit contact; exclude loading, pauses, fades, and death/exit presentation.
    Reference routes and results are recorded in [ROOMS.md](ROOMS.md) (#70).
  - On death the **current room** resets the player and surviving enemies to authored
    spawn/AI state, and mutable room owners to their latest secured snapshot; new
    provisional pickups roll back. On its first visit, the authored map is the baseline.
    On a collection revisit, secured pickups and
    killed enemies stay absent, and traps return to the state secured on the previous exit.
    Falling platforms restore the latest secured snapshot like other mutable room owners.
    A spent support may make optional Key 95 unreachable until Restart Level; mandatory
    exits and boss routes must remain passable. The player respawns at the room entrance.
    The complete first-visit/revisit policy is in [HEALER.md](HEALER.md).
  - **Rooms already passed keep progression** for the rest of the level: killed enemies stay
    dead and collected pickups stay collected. The optional healer collection return (D6)
    preserves enemy/trap state and mutable room-owner snapshots, including one-shot
    falling platforms. An optional item may be missed until Restart Level.
    It is distinct from a same-room death reset. See [HEALER.md](HEALER.md).
  - Death plays in slow motion, as in Katana Zero (about 0.7 s of real time at a fifth of
    normal speed), so the player sees what killed them and does not walk into it twice. Only
    the hit animation is cut short, not played to its end. A fall into a pit has nothing to
    look at and skips the slow motion. Each level authors a `Deadline` trigger volume to
    define where off-play falls become this instant pit death; death is not tied to a global
    player-Y threshold. See [ROOMS.md](ROOMS.md#authoring).
  - Then the respawn is instant: well under half a second. The camera returns to the entrance
    with a fast, eased pan of fixed duration (about 0.3 to 0.4 s, whatever the distance), never
    a hard cut, and the player appears as it arrives. **Amended by
    [D81](#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16)
    (#341):** after a death with a visible cause the silver thread pulls Edgard back under a death fade and the camera
    snaps at black; death to control is 0.967 s (was 1.117 s); a pit keeps its pan and 0.417 s.
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
  - Coins are optional healer payment in `forest-3` (now `forest-2`) (#82/#84); collection is never required.
    Dialogue, payment and collection returns are described in [HEALER.md](HEALER.md);
    boss combat is described in [BOSS.md](BOSS.md).
  - **Artifacts** are optional: the game can be finished without them. The MVP set
    for `forest-3` (now `forest-2`) is **Chicken + Key**; `forest-1` has no artifacts or full-set reward yet.
    The author accepted their existing map positions as final for the MVP on 2026-10-19
    (#72). Keep Chicken and Key in those positions; relocating them or adding harder
    artifact side paths is not required or planned for this MVP. Under [D26](#d26-observe-plan-before-committing),
    a secured one-shot consequence may make an optional artifact route missable until
    Restart Level, but it cannot block a mandatory exit or future tablet fragment.
  - The boss always starts at full normal energy. Merely holding Chicken and Key gives
    no bonus. The optional healer bargain consumes Chicken, Key and every forest coin;
    the hero immediately drinks the elixir, granting **10x physical sword damage to this boss**.
    Starting/max energy is unchanged. Magical fireballs always remove two boss energy;
    the elixir never increases their damage.
    The effect belongs only to this boss encounter and its retries until this boss is defeated;
    clear it immediately on boss defeat. Empowered sword hits emit 48 thicker droplets with longer trails and shake the
    screen. After committing to the fight, no backward return or onward passage is possible
    until victory. The bargain/dialogue does not repeat on retry. It is run-only and also
    clears on Restart Level, leaving the level or leaving the run.
  - Payment consumes Chicken, Key and every unique authored forest coin, never prior-level
    currency. Collection identities and completion remain recorded after spending. Brewing is
    once per encounter; an empty/undefined authored coin set cannot satisfy the recipe.
  - At healer entry and collection return, secure the current room's pickups, enemy/trap
    state and mutable room-owner snapshots. Collection return teleports to the first room;
    it is not Restart Level or free backward traversal. Restore each visited room's secured
    objects and dependent chains when re-entered, including falling-platform progress.
    An optional item may remain inaccessible until Restart Level; retain enemy/trap state
    and collected pickup identities.
  - An artifact **counts once secured at room/level exit or healer entry/return**.
    Dying before that returns the new provisional pickup to its place (D3).
  - Pickups appear immediately in a single HUD row after the coin counter, in collection
    order. Their world sprites use two 16x16 frames at 0.3 seconds per frame, like coins.
    Artifacts do not increase the coin counter.
  - Secured artifacts remain available across levels in the live run. Restart Level restores
    the inventory at level entry. New Game, Continue, and leaving the live run clear it.
    Artifacts are not written to disk; the level-only save contract (D8)
    is unchanged. Persistent artifacts require a separate explicit decision.
- **Why:** Optional artifacts give players a reason to explore without requiring collection
  to finish. Harder side paths inspired the original design, but the accepted MVP uses the
  existing placements and optional healer recipe without further challenge-layout work.
- **Tracked in:** #46, #47, #60, #72, #155

## D7. Traps are deterministic puzzle elements

- **Status:** Accepted
- **Decision:** Bombs are the first of many planned traps. Each first-visit retry starts from the same
  trap state and warns before killing. On a healer revisit, retain the secured trap state,
  including a partially elapsed warning or prior explosion; death restores that secured
  baseline (D3/D6). Falling platforms retain the same secured snapshots as other room
  owners, including warning/fall progress and removal; transient warning lights are rebuilt.
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
- **Note (author, 2026-11-12): the warning durations are placeholders.** The numbers above (0.8 s default, 1.2 s with
  the assist, 1 s and 1.5 s for the falling platform) are values from the first implementation that were recorded by
  accident; they have **not been playtested** and are not a design decision. The author's direction is shorter
  warnings in the spirit of Celeste and Katana Zero (candidates from 0 to about 0.125 s). Before a number changes:
  a person's visual reaction time is usually 0.2 to 0.3 s, so such a warning cannot be reacted to and fairness
  must come from a learnable trigger zone, an understandable death (D5) and instant retries (D3), which means
  rewording "warns before killing" and D26 for those traps; the assist (D11) multiplies the base by 1.5, so it
  needs an absolute minimum window; and because every trap takes its time from one shared place and tests pin the
  literals, a global change moves all existing traps and routes. Tuned by playtest, tracked by
  [#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300) (playtest plan:
  [PLAYTEST_WARNING_TIMES.md](PLAYTEST_WARNING_TIMES.md)); until then the current values stand.
- **Update (author, 2026-11-15, [D66](#d66-first-external-playtest-closed-findings-and-direction-2026-11-15)):**
  after the first external playtest the author closed #300. No tester reported a warning-time problem, so the
  current values (0.8 s, 1.2 s with the assist; 1 s and 1.5 s for the falling platform) are the accepted values,
  not placeholders. No comparison of values was run; a later change needs a new decision.

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
  - The MVP order is `forest-1` (one room), then `forest-3` (now `forest-2`) (three rooms and boss, the last level);
    see [D32](#d32-forest-level-order-and-identity). Since #195 the order is `forest-0` (tutorial),
    `forest-1`, `forest-3` (now `forest-2`); the save indices are 0, 1, 2 and older saves are migrated (D33).
    Completing the **last level** opens the ending scroll with the intro music; after
    it finishes or is skipped, delete only the run save and return to the main menu.
    Continue is then hidden and New Game starts a fresh run. There is no level wrap.
    If the application closes before the ending finishes, the previous level-only save
    remains available, so Continue restarts the last level.
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
  - ~~The About screen lists the keyboard bindings for the current mode.~~ Amended by [D135](#d135-about-describes-the-game-and-options-gets-a-controls-screen-531-532-2026-11-24): Options > Controls lists both hands.
- **Why:** Keeping the default keys live in parallel would leave a second control scheme under
  the hand that is meant to be doing the other job, and A/D sit under the same hand as Z/X/C. A clean
  swap keeps the About text honest and the behaviour predictable. Z/X/C is the usual
  left-hand cluster beside the arrows.
- **Tracked in:** #42
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** proposed, not yet approved: the Controls screen moves from About into Options and focusing Left-handed shows both hand layouts and swaps them on toggle; if approved, this decision is revised by issue [#532](https://github.com/jlogicgames/edgard_in_kimeria/issues/532). Approved and built as [D135](#d135-about-describes-the-game-and-options-gets-a-controls-screen-531-532-2026-11-24).

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
    One exception, by the author (2026-11-21): the redrawn RedMob keeps its body hitbox
    `rect(6, 4, 20, 28)` although its back edge is drawn from x 5 and the run's trailing foot reaches x 2, so a
    hit on that trailing sliver misses. The author kept both the drawing and the box rather than make the mob
    easier to kill; its hurtbox `rect(10, 6, 14, 26)` stays inside the art.
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
    own. **Screen shake** (On/Off, D62) is the second item; it defaults to on, and on the web to
    the browser's reduced-motion preference. **Instant text** (On/Off, #342, playtest F8) is the third: off by
    default (letters still appear one by one), it shows each healer dialogue line whole from its first frame, so
    the first press advances the line. Key `instant-text`, read live; no platform default.
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
The orange HURT, WARNING, COUNTER and COUNTER_RECOVERY states are invulnerable to all
damage sources. Rejected hits do not emit landed-hit feedback or change the counter timer;
their projectile is still consumed, and each sword swing remains consumed once. After
counter recovery, ordinary actions become vulnerable and interruptible again.
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
  beginning: darkness has descended upon the kingdom, brought about by the evil witch
  L'Kardina (Л’Кардина). He provides practical explanations and advice on how to progress
  through the game, as well as story context. He returns after the forest
  boss, following the death animation and automatic hero exit, on a separate dialogue
  screen before the next level. Use the same character and dialogue presentation.
- **Opening revision, 2026-10-20:** Use the author's original DOS story: Rufardus the
  Wise, L'Kardina's expulsion and curse of eternal night, and Edgard's departure. The
  opening is world history, with no Chicken/Key/coin checklist or elixir instructions.
  Collection and remedy explanations belong to the healer encounter; expanding that
  story is later work. Historical tablet pieces and seven ingredients are references,
  not new implemented mechanics. See [ORIGINAL_DOS.md](ORIGINAL_DOS.md).
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
- **Decision:** Play the single-room `forest-1`, then `forest-3` (now `forest-2`) with three rooms, the
  pre-boss healer and forest boss (order reconciled in [D32](#d32-forest-level-order-and-identity);
  this entry originally played the forest first). The existing death animation, automatic exit and
  guide dialogue after the boss lead to a black scrolling ending using
  `IntroScreen` and `audio/intro.wav`, with the same fade and fresh-input skip behavior
  as the opening. Localized text says Edgard defeated every enemy, freed the kingdom of
  monsters and brought light back to the world. After the ending, clear the saved run
  and return to the menu; retain settings. Future mechanics expand these two levels.
- **Why:** This is the complete playable MVP loop, with a clear beginning and ending.
- **Persistence:** D8 remains level-only. Save index 1 after the first level
  (`forest-1`); never write index 0 on final completion. See [MVP.md](MVP.md) for scope
  and validation. This supersedes the former final-level wrap behavior.

## D16. Expanded campaign, wind, ladders and carried objects

- **Status:** Accepted direction, 2026-10-20; future design, not implemented by this entry.
- **Decision:** The campaign has four stages, Forest → Swamp → Steppe → Fortress.
  Each stage contains levels; levels contain short retry rooms. One required tablet
  fragment per stage serves the goal of lifting eternal night. Ingredients and coins
  remain optional help against bosses. Wind is the Steppe's leading mechanic.
  Add vertical ladders. Wall jumping already exists in the current player;
  its role or changes in the expanded campaign remain undecided.
- **Carrying:** One object occupies one hand; sword attacks remain available.
  The same interaction command picks up and puts down. The ranged command throws
  a held object; with no held object it uses a fireball if ammunition is available.
  Throwing an object does not spend fireball ammunition or also fire after the hand
  becomes empty. Directional attack/interact arbitration is now accepted in D18
  and implemented in #95 with device feedback; ladder actions are decided in
  [D28](#d28-vertical-ladders) and implemented in #99.
- **Retry:** Ranged ammunition follows room rollback, including pickups and their
  sources, without duplication. Successful-room expenditure carries forward.
  Persistent ammunition/tablet handling on Continue still needs a separate decision;
  this entry does not extend D8's save format.
- **Planning assumptions:** Five levels per stage and five rooms per level are a
  working content model. The earlier three-to-five-minute first-learning hypothesis
  has been replaced by D19's 20-second replay / 60-second first-learning hypothesis.
  D18 subsequently accepts the coin-spending choice and double jump from the Steppe;
  crystals, higher jumps and faster sword combos remain unselected.
- **Why:** Preserve cheap experimentation while expanding the original's adventure,
  with environmental mechanics and alternative combat/traversal solutions.
- **Details:** [Campaign](GAME_DESIGN.md#campaign-structure),
  [pacing](GAME_DESIGN.md#pacing-and-duration), [player tools](GAME_DESIGN.md#player-tools),
  [economy proposals](GAME_DESIGN.md#economy-proposals).

## D17. Maintain the game design throughout development

- **Status:** Accepted documentation workflow, 2026-10-20.
- **Decision:** Keep [GAME_DESIGN.md](GAME_DESIGN.md) current as design is discussed,
  implemented and evaluated. Record accepted changes in the relevant section and
  update affected topic documents with the implementation work. Keep proposals,
  accepted direction, implemented behavior and validation evidence distinguishable.
  Date consequential decisions here and maintain topic links in the documentation index.
- **Why:** A one-time design snapshot drifts away from the game and obscures which
  rules apply. The full-campaign design and test-build contracts must remain explicit.
- **Language:** Discussion and working drafts are Russian; prepare repository
  documents in English before a commit. A commit still requires explicit author approval.
- **Details:** [Design maintenance](GAME_DESIGN.md#design-maintenance).

## D18. Directional controls, Steppe double jump and replay target

- **Status:** Accepted future direction, 2026-10-20; not implemented by this entry.
- **Controls:** Action without an interaction direction attacks with the sword;
  Down + Action picks up/puts down; Up + Action activates a mechanism. Ranged throws
  the held object, or uses a fireball when empty-handed. One carried object and
  sword availability remain as in D16. Implementation:
  [#95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95), implemented 2026-10-22;
  [original contract and dependencies](DIRECTIONAL_CONTROLS.md). The author-requested
  directional revision is recorded in D30 and tracked by #175.
  **D126 (2026-11-22) reverses the Action chords above:** Up picks up and uses a mechanism, Down puts down, Action is the sword only.
- **Movement progression:** Double jump becomes available starting with the Steppe.
  Design subsequent rooms around that ability rather than an arbitrary optional
  upgrade purchase. The unlock event and exact movement rules still need design;
  the already implemented wall jump's future role remains undecided (settled by [D121](#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21): removed).
- **Economy:** Limited coins create a real choice between the healer elixir and
  extra fireballs. Do not author a surplus that buys every offered advantage.
  Prices, income, purchase limits and the resulting ranged-ammunition budget still
  need balancing. Ingredients remain recipe objects; a second currency is unselected.
- **Replay (superseded by D19):** About 40 seconds of mastered traversal between recovery points replaced
  the former 20-second required / 30-second collection targets in D3. Measure both
  routes separately; short rooms need no padding. First-time learning with failed
  attempts remains a different metric. Historical measurements retain their original
  conditions; current replay tests still check the stricter old limits on existing maps.
- **Why:** Keep attacks intentional, make double jump part of the authored puzzle
  progression, preserve meaningful spending choices and bound the cost of a late mistake.
- **Details:** [Player tools](GAME_DESIGN.md#player-tools),
  [movement](GAME_DESIGN.md#wind-and-ladders), [economy](GAME_DESIGN.md#economy-proposals),
  [pacing](GAME_DESIGN.md#pacing-and-duration).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** Up alone interacts and Down puts down, so the Up + Action and Down + Action chords go; Up and Down also climb ladders. This decision will be revised by issue [#548](https://github.com/jlogicgames/edgard_in_kimeria/issues/548).

## D19. Test a 20-second replay and 60-second first learning

- **Status:** Accepted working hypothesis, 2026-10-20; subject to playtesting.
- **Decision:** Target about 20 seconds for a mastered successful traversal of an
  ordinary room. Target about 60 seconds from first usable entrance input to the
  first successful exit, including observation, planning, failed attempts and the
  successful traversal. The successful 20 seconds are included in the 60, not added.
- **Revision:** This supersedes D18's 40-second replay target and the earlier
  3–5-minute first-learning model. Measure required and collection routes separately;
  retain historical measurements under their original criteria. Short rooms need
  no padding; hard/optional rooms and bosses require separate evaluation.
- **Evidence:** Sources support short retries and frequent recovery points, but do
  not establish 20/60 as a measured average for Celeste, TetherGeist or the genre.
  Sixty seconds is our initial hypothesis, not an inferred published statistic.
- **Validation:** Observe new players, record both metrics and deaths separately,
  and revise the targets from evidence. These are authoring targets, not timers
  or penalties. The provisional 100-room model now implies about 100 minutes of
  first learning before any separately counted activities, not the previous 5–8h20.
- **Why:** Keep retries cheap while giving the player time to read and learn a room.
- **Details:** [Pacing](GAME_DESIGN.md#pacing-and-duration),
  [measurement protocol](ROOMS.md#replay-budget-70),
  [research references](GAME_DESIGN.md#research-references).

## D20. Mechanics MVP before campaign production and playtests

- **Status:** Accepted development sequence, 2026-10-20; detailed roadmap is a working plan.
- **Decision:** First build a mechanics MVP covering the four stages on small
  prototypes. Then build the full campaign and conduct the first external playtests;
  release completion and acceptance follow. The existing two-level test MVP is the
  starting baseline, not evidence that the new mechanics MVP is complete.
- **Scope:** Prototype checks and author trials still happen during mechanics work.
  First external learning/pacing playtests begin in the campaign phase on a coherent
  playable section; the plan does not require producing all provisional 100 rooms first.
  Dates and final content counts remain open until prototypes and measurements inform them.
- **Why:** Validate each stage's gameplay before scaling content production, then
  use player observations to refine learning, difficulty and campaign scope.
- **Details:** [Roadmap](ROADMAP.md), [mechanics MVP](ROADMAP.md#mechanics-mvp),
  [campaign and playtests](ROADMAP.md#campaign-and-playtests).


## D21. Remake and remaster preserving the original artist's style

- **Status:** Accepted by the author, 2026-10-22.
- **Decision:** Make a more beautiful version of the original, with more colors,
  finer detail, less grain and more natural illustration. Preserve the original
  artist's style, recognizable faces, silhouettes and characteristic palette family
  consistently across the many images still to be produced.
- **Current baseline:** Rework Edgard and the healer with a palette of up to 256 colors,
  detailed transparent source masters and smoothly rendered 256 × 256 portrait exports.
  This supersedes the earlier coarse 64 × 64 / tiny-palette NES treatment for these
  illustrations. Existing gameplay sprites are not replaced by this portrait revision.
- **Continuity:** Use the supplied DOS originals for identity and the remaster masters
  as style references; retain exact prompts and export settings. Review each final
  artwork revision at game size separately from automated checks.
- **Details:** [Art direction](ART_DIRECTION.md), [portraits](PORTRAITS.md),
  [original references](ORIGINAL_DOS.md).


## D22. Native world pixels and separate detailed HUD icons

- **Status:** Explicit author correction, 2026-10-22.
- **Decision:** World art keeps the existing tiny, limited-color pixel style with
  crisp boundaries and no smoothing. New small objects use 8×8/8×16/16×16 frames
  and at most 16 entries including binary transparency. D21's detailed portrait
  finish is not the world sprite baseline.
- **HUD:** Separate 128×128, up to 256-entry illustrated icons may show the same
  objects with more detail. The world chicken and the HUD chicken keep the same
  cooked-food identity, while their resolutions and finish differ.
- **Presentation (clarified 2026-10-23, D24):** All item teaching is contextual and
  repeatable throughout the entire first Forest room, including while carrying.
  The earlier 320×144 area, one-time and 50 px limits are superseded.
  Text fades and slides upward
  through its normal local position. The earlier hidden carried-stone presentation
  is superseded by D23: every carried object must be visible in the front hand.
  The torch's animated free-front-palm attachment remains the positioning baseline.
- **Details:** [Art direction](ART_DIRECTION.md), [directional actions](DIRECTIONAL_CONTROLS.md).

## D23. One visible carried object, with no separate pocket

- **Status:** Explicitly accepted by the author, 2026-10-23: “Выбираю один слот.”
- **Decision:** The hero has exactly **one carryable slot** shared by stones,
  portable torches and other movable puzzle objects. Its object is always visible
  in the animated front hand, in front of the hero. A stone does not occupy a
  separate hidden pocket; stone plus torch cannot be carried simultaneously.
- **Controls:** Keep the existing pickup/place and ranged-throw rules. With an
  object held, Ranged throws that object; empty-handed it can spend a fireball.
  Sword melee, normal movement and jumping remain available while carrying.
  No slot selection, swapping command or second-slot priority is introduced.
- **Why:** Visible carrying communicates what the hero has without remembering
  a hidden state. One slot preserves a meaningful puzzle choice: which tool to
  bring, where to leave it and when to retrieve it. Two slots would remove some
  of these choices, potentially simplifying light-plus-stone obstacles, while
  adding ambiguity to throwing, placement, full-slot pickup and teaching across
  keyboard, gamepad and touch. Additional inventory management is not the aim
  of the current arcade puzzle-platforming design.
- **Rejected alternative:** A pocket stone plus a hand torch, with two HUD slots.
  This is not a presentation-only change: it permits simultaneous tools and
  requires different command/state rules and puzzle balancing. Hiding a stone
  in the renderer is not a reason to create this mechanic. A future proposal
  should demonstrate a concrete authored puzzle need and explicitly revise D23;
  do not reopen the decision merely because the item is hard to see.
- **Scope:** Recipe ingredients, coins and fireball stock keep their separate
  existing inventory/resource rules; they do not consume this carryable slot.
  A HUD icon may mirror the held object without creating another slot. Current
  room ownership remains: secured tools resume on collection return, death rolls
  back to the latest secured state, and a held tool is released onto safe support
  before forward exit. This decision adds no cross-room transport or save fields.
- **Implementation status:** One-slot input/state and visible front-hand carrying
  for both stone and torch are implemented (2026-10-23). Both use the player's
  foreground pass and animated palm attachment. A carried-item HUD icon is not
  implemented by this change; it remains optional presentation.
  The four carrying criteria in #97 were verified on 2026-10-25 against PR #154
  and secured-state retention in PR #165; see the
  [acceptance evidence](DIRECTIONAL_CONTROLS.md#carrying-acceptance-97).
- **Details:** [Carrying model](GAME_DESIGN.md#one-visible-carryable-slot-d23),
  [current behavior](DIRECTIONAL_CONTROLS.md), [room lifecycle](ROOMS.md),
  [mechanics MVP](ROADMAP.md#mechanics-mvp).

## D24. The whole first room teaches, including mechanisms while carrying

- **Status:** Explicit author correction, 2026-10-23.
- **Decision:** The entire first Forest room is the tutorial scope for contextual
  item/mechanism instructions, at all positions and heights. Use active room/level
  identity, with no separate 320×144 rectangle or fixed travel-distance budget.
  Instructions remain repeatable after death and on collection
  return to this room; subsequent rooms and levels omit these teaching labels.
- **Revised (#195, 2026-11-04, see [D33](#d33-the-forest-0-tutorial-level-and-map-authored-hints)):**
  the "first room" rule is now a map-authored **hint zone** (`Hint` object with `controls`),
  not a room index or level name. `tutorialRoom` and `Level.tutorialHintsActive()` are gone.
  Every behavior below is unchanged inside such a zone and absent outside it. Today the only
  `controls` zone is `forest-3` (now `forest-2`), room 0 (whole room); the control lessons are meant to move to
  `forest-0`'s lesson rooms as those tickets (#186, #196–#200) land, after which that zone is
  removed from `forest-3` (now `forest-2`) and this entry is closed.
- **Closed (2026-11-17, [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376), D72):** the control lessons now live in `forest-0`'s lesson rooms, so the practice
  stone, carryable torch, practice lever and torch, and the ladder (objects 116-119, 123, 124) are removed from
  `forest-2` and its `controls` zone (Hint 126) is removed with them; `forest-entry` is split at x 736. The behaviour
  below is unchanged inside any `controls` zone; its tests run on the fixture
  `core/src/test/resources/tiles/practice-test.tmx`.
- **Carrying:** Holding the one stone/torch slot must not hide a usable mechanism's
  instruction. Near a lever, show Up + Action and its current Turn on/Turn off
  action together with any still-active Put down/Throw reminder. Place this combined
  label by the visible lever rather than at the earlier pickup point. Elsewhere,
  retain the carrying label's pickup anchor. Mechanism lessons remain available
  even after that pickup's carrying reminder has ended.
- **Pickup reminder lifetime (clarified 2026-10-23):** Show Put down/Throw at each
  new pickup in the first teaching room. Keep the world anchor fixed while the
  player moves within the camera's dead zone. As soon as camera following begins
  to scroll the room-clamped view, fade/slide this reminder out once for that
  pickup; stopping or returning must not revive it or pin it to the viewport edge.
  A new pickup arms it again, including retries. Use camera movement, not distance
  walked, facing, screen shake or scripted respawn pans: movement against a clamped
  room edge may leave the camera still. This is per pickup, not a global learned flag.
  Existing fade/slide timings remain unchanged.
- **Why:** The first room exists for learning. A hidden rectangle suppresses
  instruction without communicating why, and carrying does not make a switch
  unusable. Teaching only placement/throwing in that situation hides a valid action
  and prevents the player from learning how their tools work together.
- **Scope:** Reach, trigger eligibility, the one-slot model and action bindings are
  unchanged. Context determines which instructions appear; whole-room scope does
  not mean showing every instruction continuously. Device-specific button labels
  and target brackets remain available outside the teaching room.
- **Validation:** Check both handedness layouts, stone and torch, state changes,
  first-room positions beyond the former rectangle, retries/collection returns and
  suppression in later rooms. Scripted checks do not replace novice playtests.
- **Tracked in:** [#131](https://github.com/jlogicgames/edgard_in_kimeria/issues/131),
  [author clarification and playtest checks](https://github.com/jlogicgames/edgard_in_kimeria/issues/131#issuecomment-6360805528),
  [directional actions](DIRECTIONAL_CONTROLS.md), [room authoring](ROOMS.md).

## D25. Standing look with delayed vertical inspection

- **Status:** Superseded by [D111](#d111-look-up-and-down-are-removed-the-camera-no-longer-pans-for-a-look-d25-d29-superseded-513), 2026-11-20 (was: accepted by the author, 2026-10-23; distance/timing are initial tuning values).
- **Decision:** Up/Down alone while stationary and grounded immediately replaces idle
  with a registered static head-up/head-down sprite. After 0.3 seconds, ease the camera
  over 1 second: 100 logical pixels up or 150 down. Permit the full offset beyond current vertical room bounds
  and even with the hero outside the view, as explicitly requested during tuning.
  Release or another action restores the ordinary camera smoothly over 0.2 seconds, matching the restored idle pose.
  A reversed direction starts a fresh hold: normally 0.3 s, or 1 s when a
  reachable pickup/usable mechanism is available. Select this delay at hold start;
  carry-only reminders do not extend it. Movement, jump, sword,
  directional interaction, ranged, climbing and UI/room ownership take priority.
  Held input across UI/layout boundaries cannot begin a new inspection until released.
- **Why:** Short directional taps and interaction chords must not drag the camera.
  A visible pose acknowledges the hold immediately, while the delay distinguishes
  deliberate inspection. The current 368 px rooms barely exceed the 360 px viewport;
  an explicit vertical-only exception to the normal room camera clamp is necessary
  for a useful peek. Horizontal room limits, transitions and collision remain unchanged.
- **Tracked in:** #95 author follow-up; ladder behavior remains #99. Details and
  validation: [directional controls](DIRECTIONAL_CONTROLS.md).

  Current author tuning: 100 px up, 150 px down; 0.3 s delay, 1 s travel,
  0.2 s return after release (2026-10-23).

  Author correction: releasing Up/Down restores idle immediately; return the camera
  in 0.2 s so the view agrees with the hero's restored gaze (2026-10-23).

  Author clarification: while standing at a pickup/activation target, wait 1 s
  before inspection so its directional interaction can be entered first.

  **2026-10-26 interaction-focus update:** When Up/Down selects an eligible mechanism
  or carryable and its interaction outline is visible, that target owns the vertical
  input. Standing inspection is suppressed for as long as the outline remains visible;
  if an inspection was already underway, cancel it and return the camera to its ordinary
  target using the existing 0.2-second return.

<a id="d26-observe-plan-before-committing"></a>

## D26. Observe and plan before committing

- **Status:** Accepted for the full campaign, 2026-10-24.
- **Decision:** Edgard in Kimeria is a puzzle-platformer where the player should
  first observe the room, infer how its obstacles and mechanisms work, then choose
  when and how to act. One-shot supports, switched mechanisms, moved tools and
  other secured world changes remain as played during the live Level. Their state
  can change later searches for secrets and optional ingredients; this is an
  intentional source of planning, consequence and replay variety.
- **Authoring contract:** Mandatory exits, bosses and required future tablet
  fragments must remain reachable without a forced Restart Level from every
  reachable secured state produced by supported player actions, including after
  one-way or mutable-object consequences. Optional secrets and ingredients may
  become unavailable until Restart Level. Before an irreversible action, the
  one-shot behavior and its meaningful consequence must be legible to an attentive
  player. A hidden trap is not made fair by the presence of a reset button. Do not
  clear or rearm objects merely to pass a route gate; author and verify routes under
  the retained state.
- **State boundary:** Forward room exits and healer entry/collection returns
  secure the current room. Death restores the latest secured mutable-object state
  on a revisit and authored state on the first visit. Restart Level rebuilds the
  level from its authored state and restores level-entry inventory under existing
  rules. Continue after closing the game still starts the saved level from its
  beginning; this decision adds no object persistence to disk. Ordinary death and
  room retries remain distinct from the optional full-level reset cost.
- **Why:** A player who studies a space and chooses a risk should feel that choice
  shape later exploration. Retaining a changed world supports richer puzzles and
  more interesting searches, while clear signals and an unblocked mandatory route
  keep consequence fair. The desired feel is “look around and think, then jump,”
  not trial-and-error through hidden traps. A full Restart Level to recover a missed
  optional reward is a deliberate cost of that choice, not the routine death-retry
  loop or a substitute for the short replay targets.
- **Scope:** This does not change death's secured rollback, Restart Level,
  Continue/save boundaries, the 20-second mastered replay target or 60-second
  first-learning hypothesis. It introduces no UI, controls, mechanics or geometry.
- **Implementation status:** The design rule applies to the whole campaign.
  Runtime retention is tracked in #158; acceptance for each campaign map is
  verified separately.
- **Details:** [Campaign design](GAME_DESIGN.md#observe-plan-and-commit-d26),
  [room-authoring contract](ROOMS.md#puzzle-platformer-authoring-contract-d26),
  [state boundaries](HEALER.md#state-policy), [#158](https://github.com/jlogicgames/edgard_in_kimeria/issues/158).


## D27. Three held fireballs and rare single-charge refills

- **Status:** Accepted by the author, 2026-10-25 (#98); implemented in PR #168,
  merged on 2026-10-25.
- **Decision:** Keep the test run's initial stock at three and cap held stock at
  three. Carry the remainder between levels. This replaces the tentative “three
  uses per stage” assumption with a capacity rule: authored refills can permit
  more than three shots over a stage, while keeping no more than three at once.
- **Pickups:** Place one before the first level's boss-room entrance and one above
  a moving platform in the second level. Body contact grants exactly one charge.
  At full stock, retain the pickup and show a local full-inventory message using
  the existing torch/lever hint animation, in every room. Ammunition remains
  separate from recipe artifacts and the one visible carryable slot.
- **HUD:** Three fixed icons at the top right, no number; missing charges are
  darkened. Preserve development FPS spacing and separate world/HUD artwork.
- **Recovery:** Record authored refill IDs with room pickups; roll provisional
  stock and identity back together on death, retain secured identities on healer
  returns, and restore entry stock/authored objects on Restart Level. No save fields.
- **Why:** Make scarce ranged capacity readable at a glance, offer a deliberate
  optional jump reward and pre-boss replenishment, and avoid wasting full-stock
  pickups or farming charges through room returns. Required routes need no shots.
- **Details:** [Rules and audit](DIRECTIONAL_CONTROLS.md#fireball-refills-98),
  [placements and route evidence](ROOMS.md), [gameplay](GAMEPLAY.md).

## D28. Vertical ladders

- **Status:** Accepted and implemented for the Mechanics MVP, 2026-10-25 ([#99](https://github.com/jlogicgames/edgard_in_kimeria/issues/99)).
- **Decision:** A ladder is an authored `Ladder` rectangle, one 16 px frame wide and a whole
  number of 16 px frames tall. Its top edge is a one-way support where the hero can stand;
  its bottom edge is where the hero steps off. The ladder passes through any one-way platform
  at its top; while attached the hero ignores terrain, so authors must put support at both ends.
- **Entering:** Held Up takes hold of a ladder whose rungs the hero's body centre is over, from
  the ground or in the air, once the feet are more than 2 px below the top surface. Held Down
  takes hold from the top surface. From a grounded intermediate platform at least one third
  down the ladder, either direction takes hold; on the upper span and in the air, only Up does.
  At the bottom, only Up does. The hero is centred on the rungs and gravity and horizontal movement stop.
  Up/Down held with Action, or released out of a chord, never enters.
- **Climbing:** Up/Down climb at 70 logical px/s, and the pose holds still while the hero does.
  The climbing animation advances only while Up/Down moves the hero. Left/Right releases the
  ladder and resumes normal horizontal movement; leaving the ladder returns to the normal movement
  animation. Reaching the top or bottom steps off onto that surface.
- **Jumping off:** A fresh Jump press lets go with a 0.7× hop; the held horizontal direction
  carries the hero away as in any airborne jump. A released ladder is ignored for 0.25 s so
  a held Up cannot snatch it straight back.
- **Actions while attached:** Ranged works (throw a held object or spend a fireball) and keeps
  the grip. Up + Action still operates a mechanism in reach. The sword and pick up/put down
  need footing and do nothing, matching D4's rule that the sword is not used in the air.
  Carrying changes nothing else: a held object stays in the front hand.
- **Interaction priority:** An Action chord owns its Up/Down. While Action is held the vertical
  input is zero, and it stays spent until released, so one press can climb or operate, never both.
  Releasing Action while still holding Up does not climb; press Up again.
- **Teaching:** Only the first Forest room teaches ([D24](#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying)):
  beside a ladder's rungs the hint reads `Up: Climb`; at a grounded intermediate platform it shows
  separate `Up: Climb` and `Down: Climb down` lines; from its top surface it reads
  `Down: Climb down`. Hints use the active
  device's label. A pickup or mechanism hint takes precedence. Nothing is shown once attached,
  and later rooms do not repeat it.
- **Why:** Clear, rule-of-thumb arbitration; Action and vertical input already share one chord
  language (D18), so the ladder needed a rule for who owns Up/Down. Sideways exit is deliberate
  through Left/Right; Jump remains the deliberate hop away from the ladder.
- **Not decided:** Double jump on ladders (#110), wind on ladders (#108/#109), and a dedicated
  climbing animation: the existing raised-arms
  pose is reused until the author supplies art.
- **Details:** [Directional controls](DIRECTIONAL_CONTROLS.md#ladders-99), [room authoring](ROOMS.md#ladder-practice-revision-99) and the [toggle ladder update](ROOMS.md#toggle-ladder-update-2026-10-26).

## D29. Interaction outline takes priority over standing look

- **Status:** Superseded by [D111](#d111-look-up-and-down-are-removed-the-camera-no-longer-pans-for-a-look-d25-d29-superseded-513), 2026-11-20, the look it yielded to is gone (was: accepted by the author, 2026-10-26). The interaction target and its eligibility are unchanged.
- **Decision:** When Up/Down selects an eligible mechanism or carryable and the game draws
  its interaction outline, suppress standing look and vertical camera inspection until that
  outline disappears. If inspection was already active, cancel it and return to the ordinary
  camera target with D25's existing 0.2-second easing. The outline and its usable target retain
  the same interaction eligibility.
- **Why:** The highlight tells the player which object will receive Action. Vertical camera
  motion during that selection makes the target and hint drift while the player is deciding.
- **Details:** [Standing look](#d25-standing-look-with-delayed-vertical-inspection),
  [directional controls](DIRECTIONAL_CONTROLS.md#standing-look-updown-removed), and
  [gameplay controls](GAMEPLAY.md#controls).
- **Note, D82 (#338):** the outline is now the fairy guide's glow, glints and fairy. Look suppression still keys on
  the same selection (`Player.interactionFeedbackTarget()`), so this rule is unchanged.

## D30. One direction for pickup and mechanism interaction

- **Status:** Implemented 2026-10-26 ([#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175)).
- **Decision:** Up + Action is the single affirmative interaction chord. With an empty hand,
  it picks up a selected nearby carryable or activates a selected mechanism. With an object
  held, it remains available to activate a mechanism. Down + Action only places/drops the
  held object; with an empty hand it does nothing. Plain Action remains the sword, and
  Ranged keeps its throw/fireball behavior.
- **Selection and feedback:** When both a pickup and mechanism are eligible, select the
  nearest target across both types, with the smaller Tiled object ID breaking equal-distance
  ties. The outline and contextual hint must identify the target that Up + Action will affect.
- **Compatibility:** Preserve the existing pickup reach, placement checks, single carry slot,
  trigger behavior, and directional-input suppression across keyboard, gamepad, touch and
  both handedness layouts. Update About text, contextual labels, touch labels and all
  localization catalogs with implementation.
- **Why:** Pickup and mechanism use are both interactions; assigning both to Up + Action
  gives the player one consistent direction for acting and leaves Down + Action as the
  explicit put-down command.
- **Details:** [Interaction contract](DIRECTIONAL_CONTROLS.md#interaction-controls-175),
  [gameplay controls](GAMEPLAY.md#controls), and [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175).
- **Note, D82 (#338):** "the outline" in this decision is now the fairy guide (glow, glints, fairy); it identifies
  the same nearest target with the same tie-break.
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the one direction for pickup and mechanism interaction becomes the Up key alone (no Action chord); a placement rule keeps interactive objects away from ladders. Reversed by [D126](#d126-up-and-down-interact-no-two-button-chords-548-2026-11-22) (#548, implemented 2026-11-22).

## D31. Forest uses linked route reconfiguration

- **Status:** Accepted direction, 2026-10-26 ([#102](https://github.com/jlogicgames/edgard_in_kimeria/issues/102)).
- **Decision:** Forest's leading mechanic is route reconfiguration through visible
  linked mechanisms. Levers change traversal supports--including ladders, bridges,
  platforms and obstacles--so the player can observe a connection and its consequence,
  then choose when to change the route. A lever is not an end in itself; Forest rooms
  must create route or timing decisions rather than repeat identical nearby activations.
- **Scope:** Ladders, carrying and throwing remain shared tools that may vary an
  interaction, not separate mandatory Forest mechanics. Portable light remains the
  proposed distinct mechanic for Fortress. The current toggle ladder is a technical
  atom, not proof of a complete Forest prototype. Remote/throw activation and
  object-specific recovery rules remain open until prototyping resolves them. **One-shot
  versus reversible (resolved by the author's decision of 2026-11-15, [D67](#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15)):**
  linked levers stay reversible toggles; a one-shot support (an existing falling platform)
  guards an optional reward, so spending it loses the reward until Restart Level while the
  mandatory exit stays reachable.
- **State contract:** D26 applies to every linked route change. From every secured
  state created by supported actions, mandatory exits, bosses and required fragments
  remain reachable without Restart Level. An optional reward may become unavailable
  until Restart Level only when the consequence is legible before the commitment.
- **Why:** This gives Forest a coherent puzzle-platforming identity: inspect the
  space, understand a visible relationship, then act precisely. It develops the shared
  tools without making the stage a series of lever presses or overlapping Fortress light.
- **Next check:** #103 must test introduction, variation and a readable optional
  consequence; #104 must establish that combining the route change with a familiar
  enemy or hazard creates a new decision while keeping a zero-fireball required route.
  **#104 built ([D68](#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15), 2026-11-15; automated tests, then the author's play-through of 2026-11-17):** the first two layouts did not add enough (a guard alone on the floor added no risk; a guard at the top of the ladder was a 0.1 s reflex that paid nothing at a full stock); the third puts a spore puffball's cloud over the places where the sword reaches the guard, so melee is a timed risk with a visible telegraph, and the roof route is the safe alternative. The author accepted the room as it is after the play-through of 2026-11-17 (see D68).
- **Details:** [Forest design](GAME_DESIGN.md#forest-linked-route-reconfiguration),
  [D26](#d26-observe-plan-before-committing), and [roadmap](ROADMAP.md#12-forest).

## D32. Forest level order and identity

- **Status:** Accepted, 2026-11-04 ([#194](https://github.com/jlogicgames/edgard_in_kimeria/issues/194)); `forest-2` was open then and is settled by [D51](#d51-forest-3-renamed-forest-2-the-bomb-stays-a-separate-trap) (2026-11-06): the boss level `forest-3` below is now named `forest-2`.
- **Decision:** The intended Forest order is `forest-0` (tutorial, no monsters, hints everywhere)
  → `forest-1` → (`forest-2`?) → `forest-3` (the boss level, formerly `forest`; now `forest-2`, D51). The boss level
  is renamed `forest-3` (`assets/tiles/forest-3.tmx`, now `forest-2.tmx`). **Open question:** whether `forest-2`
  exists is not decided here. `forest-0` was added by #195 (D33): `KimeriaGame.LEVEL_NAMES` is
  `forest-0`, `forest-1`, `forest-3` (now `forest-2`); saves are a zero-based index into that list (0 = `forest-0`,
  1 = `forest-1`, 2 = `forest-3`, now `forest-2`). Before #195 the list was `forest-1`, `forest-3`. This reconciles earlier docs (D15 and others), which said the forest
  was played first: the code order had been `forest-1` then `forest` since #193.
- **Level configuration, not names:** game logic never tests a level's name. Map-level
  properties in the TMX configure it: `fireflies` (count), `healerBeforeBoss` (true when a healer meets
  the player before the boss room) and `ingredients` (healer recipe artifacts, e.g.
  `CHICKEN,KEY`). The post-boss flow keys on the level having a boss encounter.
- **Why:** A tutorial level and further Forest levels need the same mechanisms without
  inheriting behavior from the name `forest`.

## D33. The forest-0 tutorial level and map-authored hints

- **Status:** Accepted, 2026-11-04 ([#195](https://github.com/jlogicgames/edgard_in_kimeria/issues/195)); revises D24.
- **Decision:** `forest-0` is the first level: six long rooms (1280 px; since shortened, see the lesson rules D34-D40), no monsters (hazards only in the last room, D40),
  each a plain corridor with a safe entrance and a clear exit; the last room ends with the usual
  `Checkpoint` level exit. *Note (#200): the rooms were later shortened and the last room is 720 px with a falling platform and a bomb, see [D40](#d40-the-tutorial-traps-lesson-rule).* The rooms are reserved, in order, for one lesson each (room property
  `lesson`): `lever` (#186), `ladder-bridge` (#196), `wall` (#197), `moving-platform` (#198),
  `thrown-stone` (#199), `falling-platform` (#200, was `existing-traps`, renamed in #377). Each lesson ticket fills only its room.
- **Hints are authored in the map.** An object layer `Hints` holds `Hint` rectangles inside one
  room. Property `message` names a `Msg` key (shown at the zone: centred on it, 30% down it; see Hint presentation)
  and/or `controls=true` turns on the contextual pickup/lever/ladder instructions of D24 while the
  hero's body is inside. A bad key, an empty zone or a zone crossing a room boundary fails the level
  load. Zones are stateless, so hints repeat after death, Restart Level and returns, and a level
  with no `Hints` layer (`forest-1`) shows none. The fireball-stock-full message is status
  feedback, not tutorial, and is unchanged. Texts live in the five `assets/localization` catalogs.
- **Hint presentation (#206):** a message hint is drawn about 45% down its zone (mid-screen, above the
  standing hero's head; moved to 30%, higher up, by #211), not at the room's top edge. It is held back until the hero has stayed in the zone for
  0.35 s (#209; was 0.8 s, which made it appear after the player had walked past the object), then fades in over 0.45 s while sliding up from 22 px below with a small overshoot, so it draws the eye
  instead of sitting on screen from the first frame. Controls-zone labels (pickup, lever, ladder) and the
  fireball-stock message stay immediate and show alongside it. Leaving before the delay shows nothing and restarts the wait (a gap under 0.25 s is forgiven, so boundary jitter does not restart it). **Linger and hysteresis (#209):** a shown message stays 2.5 s after the hero leaves its zone, is cancelled back to full opacity if the hero returns, and is not replaced by another tutorial message before it has been up 0.7 s; a message replacing a shown label crossfades in without a new appear delay (only a first message waits it out). **Two channels (#211, supersedes the #209 rule that an immediate label replaces a lingering message):** the zone message and the immediate contextual label (pickup, lever, mechanism, ladder, controls, fireball-full) are separate channels, each with its own `HintTransition`, and are drawn at the same time. A contextual label never hides, replaces, resets or delays the message, so the lever's "Up + Action: Turn on - Lever" shows below the zone text instead of in its place; the contextual label keeps its immediate behaviour. `Player.zoneHint()` (position only: it does not depend on attacking or direction conflicts) feeds the first channel, `Player.contextHint()` the second. **Layout (`HintLayout`):** the message never moves: it sits 45% down its zone (near the screen's middle, #206; #211 had raised it to 30% so a label above a hero on the exit ledge fits under it, which was reverted: the label is lowered instead), growing upward, pinned by `DirectionalActionsTest`. The contextual label keeps its anchor (by the object or above the hero's head, growing upward) unless that would leave less than a 24 px gap below the message's bottom (larger than the message's 22 px slide-in, so even its faint start cannot touch a shown label); then the label is lowered to 24 px under it. The reservation depends on the message being present (requested, pending or lingering), not on its opacity, so nothing jumps when a label crossfades ("Turn on" to "Turn off") or the message fades. Checked at floor, jump apex, ledge, climbing height, ladder raised and lowered, camera 0 and 8 px, and the widest label of every language for keyboard, gamepad (both handedness) and touch wording (`LadderLessonTest`, `LeverLessonTest`, `LocalizationLayoutTest`). The one case not avoided is a hero airborne or climbing right under the message (within about 60 px, e.g. the ladder climb), where the 24 px gap leaves no room and the lowered label may touch the top of the hero's hitbox by up to about 6 px; a hero on the floor is never crossed. Death, checkpoint and victory clear a lingering label (the HUD clears it when the hero cannot fight). **Zones start well before the object** they explain, and the approach is long enough that a continuous walk sees the message fully visible for about 1 s before the object is in reach (`HintLeadTest`). The HUD feeds the timer frame times clamped to 0.05 s, so a load hitch cannot skip the delay.
  Timing lives in `HintTransition` and is unit-tested headlessly. Alternatives such as torches above the hint were rejected.
- **forest-3 (now forest-2) room 0:** keeps its control lessons for now through one `controls` zone covering the
  room, so playtested behavior is unchanged. Moving them to `forest-0` is the lesson tickets' job. (Done: the zone and
  the practice objects were removed on 2026-11-17 by [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376), see D24.)
- **Saves:** the save stays a bare level index, so inserting `forest-0` at the front would shift
  it. A new `level-order` preference stamps saves written since #195; a save without it is read as
  the old order (0 = `forest-1`, 1 = `forest-3` (now `forest-2`)) and shifted by one, so Continue lands on the
  same level. New Game and the ending remove both keys.
- **Why:** The tutorial needs hints that belong to rooms and objects, are authored by level
  designers in Tiled, and can be removed in later levels without code changes.
- **Author acceptance (2026-11-05):** the author played the whole tutorial on desktop (keyboard only) and checked the web build; all six rooms, both healer dialogues and the transition into forest-1 were checked with no remarks. Not checked in that session: gamepad, touch controls, native-speaker quality of de/pl/uk/be. No novice playtest (#129–#131). On 2026-11-06 the author reported a desktop keyboard and gamepad check in both layouts (build and rooms not stated); touch, translations and novice playtests remain open. Details: [ROOMS.md](ROOMS.md#author-acceptance-of-forest-0-2026-11-05).
- **Earlier note (superseded by the acceptance above for the desktop and web play):** at #195 the rooms were empty placeholders without a human playtest; the #206 animation and hint height and the #211 two-channel layout were checked headlessly only (anchors and font metrics).

## D34. The tutorial lever lesson rule

- **Status:** Accepted, 2026-11-04 ([#186](https://github.com/jlogicgames/edgard_in_kimeria/issues/186)); first lesson room of D33.
- **Decision:** The `forest-0-lever` room teaches exactly one new element: a lever that **toggles** a
  linked torch (dark at start, `Intensity` 0) on and off, any number of times, with Up + Action (D30).
  The effect is a light and flame beside the lever, visible from it and from the approach. It is
  **decorative and never gates the route**: the exit is reachable with the lever off, on, after death and after
  Restart Level (D26 holds trivially). Operating it is encouraged by hints, not forced.
- **Recovery:** the room is not secured until its exit is taken, so death/room retry returns the torch to its
  authored dark state; Restart Level rebuilds it dark. (A later visit to a secured room would keep the lit state,
  per D26.) No lever is one-shot in the tutorial.
- **Length (#206, #209):** after playtests the room is 576 px (was 768 after #206, 1280 before): lever at x=376, torch at x=456, exit at x=512; the lever message zone starts 216 px before the lever. Later rooms moved 192 px left (#209).
- **Hints:** one `controls` zone over the whole room (live lever instruction near the lever) plus three
  position-only message zones that tile it: walk (before the lever), "this lever lights the torch and puts it
  out" (around lever and torch), "the exit is ahead; the lever works any time" (after). Message zones cannot see
  object state, so each text is true with the torch lit, dark, or the lever never touched.
- **Why:** a reversible, route-independent first lesson lets a novice experiment safely and read cause and effect
  before ladders, walls and platforms (which reconfigure the route) arrive in later rooms.
- **Not verified:** no novice playtest (#129–#131); the mastered route (walk, operate, exit) was measured headlessly.
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the forest-0 lever stops driving the decorative torch and drives another visible mechanism, still with no gating; amended by [D127](#d127-the-tutorial-lever-drives-a-lift-not-the-torch-amends-d34-549-2026-11-22) (#549, implemented 2026-11-22).

## D35. The tutorial ladder lesson rule

- **Status:** Accepted, 2026-11-05 ([#196](https://github.com/jlogicgames/edgard_in_kimeria/issues/196)); second lesson room of D33, after D34.
- **Decision:** `forest-0-ladder` (704 px) teaches exactly one new element: a lever that **toggles a retracted ladder**
  down and up (D28 toggle ladder, Up + Action per D30). The ladder starts raised, hanging y 96–208 with its foot 112 px
  above the floor (a jump reaches about 58 px, and a ladder is grabbed in the air only when the body overlaps its rungs,
  so no jump reaches it). The exit stands on a 112 px ledge (top y=208, plain collision, not a wall-jumpable `Wall`) whose
  edge meets the lowered ladder's top; there is no other way up. The lever is on the floor 200 px left of the ladder
  and linked by Tiled `target` (`check_tiled_links.py`); the lowered ladder reaches the floor (about 0.7 s at 160 px/s).
- **Ladder, not bridge:** the engine already animates a lever-linked `Ladder` (D28), snapshots it for room retry, and mirrors
  its state on the lever sprite. A bridge or drawbridge would need a new mechanism or a re-skinned Escalator. A wall/ledge
  obstacle rather than a pit keeps the room free of falling hazards: no death trap, so no new death rule to teach.
- **Interaction:** Up + Action at the lever, then Up at the ladder's rungs; the controls hint zone supplies the live labels.
  The lever prompt reads "Turn on/off - Lever" (the lever's on state, handle right since #413, means ladder lowered).
- **Recovery / D26:** the lever is reachable only from the floor and is never in reach from the ledge or the ladder (the
  hero cannot pull it while climbing), so the ladder cannot be raised under the hero. Dropping off the ledge or ladder lands
  on the floor with the lever in reach, so every reachable state can lower (or re-lower) the ladder. Death / room retry restores
  the authored retracted ladder (the room is unsecured until the exit is taken); Restart Level rebuilds it. The lever toggles;
  it is never one-shot.
- **Hints:** one `controls` zone over the room plus three position-only message zones: lever (lowers *and raises* the ladder), ladder
  (climb it when it is down), exit (ahead, up on the ledge). Each is true in either ladder state.
- **Length (#196, #209):** 704 px (was 768): the exit ledge shrank from 384 to 224 px and the approach before the lever grew by 96 px so the lever message is readable before the lever is in reach; ladder and ledge-top geometry (jump margins) are unchanged, RoomStart moved to x=624, lever x=824. Later rooms moved a further 64 px left (256 px with the lever room's 192 px, #209); the climb message zone starts 128 px before the ladder.
- **Not verified:** no novice playtest (#129–#131); not seen in the running game when written, later covered by the author's desktop and web play-through of 2026-11-05 (see D33); the route, jump-reach and hint
  coverage are measured headlessly (`LadderLessonTest`, mastered route about 6.9 s).

## D36. The tutorial wall lesson rule

- **Status:** Accepted, 2026-11-05 ([#197](https://github.com/jlogicgames/edgard_in_kimeria/issues/197)); third lesson room of D33, after D34 and D35.
- **Decision:** `forest-0-wall` (640 px) teaches exactly one new element: a lever that **raises and lowers a wall** blocking the corridor. The
  wall is the existing vertical `Wall` Actionable (48×96 px, `movement`=vertical, 1.5 s, as in `forest-1`), starting lowered on the floor, taller
  than a jump (about 58 px) and not wall-climbable, so nothing but the lever gets the hero past it. Raised, it leaves a 96 px gap under it. The lever
  is on the floor 232 px before the wall, linked by a Tiled `target` reference (`check_tiled_links.py`), Up + Action per D30.
- **Interaction / reuse:** all existing: `Wall`, `Lever`, the lever sprite mirrors the raised state, room snapshot restores it. No new asset or code.
- **Recovery / D26:** the lever only comes into reach well clear of the wall (more than 120 px on the entrance side), never beside or under the wall,
  so the wall cannot be lowered onto the hero and the hero cannot be shut on the far side without the lever (the exit is on that side; the entrance
  side with the lever stays reachable by walking left). Toggling is free: the lever works any number of times, lowering the wall from the entrance
  side never blocks the exit because it can be raised again. Death / room retry restores the authored lowered wall (the room is unsecured until the exit
  is taken, so retry from under the wall restarts on the entrance side); Restart Level rebuilds it. No monsters, no hazards, no one-shot.
- **Hints:** one `controls` zone over the room plus two position-only message zones that butt together: lever (raises *and lowers* the wall,
  starts 232 px before the lever) and pass ("Walk under the wall once it is raised", starts 176 px before the wall, covers the exit side). Each is
  true in either wall state. A continuous walk from RoomStart reads the lever message about 1 s before the lever is in reach and the pass message
  about 1 s before the body meets the wall (`HintLeadTest`).
- **Length:** 640 px (was 1280): approach 200 px to the lever, 232 px between lever and wall, 48 px wall, exit 48 px beyond. Later rooms and the `Checkpoint` moved 640 px left.
- **Not verified:** no novice playtest (#129–#131); not seen in the running game when written, later covered by the author's desktop and web play-through of 2026-11-05 (see D33); route, jump-block, retry and hint coverage are measured
  headlessly (`WallLessonTest`, mastered route about 5.1 s).

## D37. The tutorial moving-platform lesson rule

- **Status:** Accepted, 2026-11-05 ([#198](https://github.com/jlogicgames/edgard_in_kimeria/issues/198)); fourth lesson room of D33, after D34-D36.
- **Decision:** `forest-0-platform` (640 px) teaches exactly one new element: a lever that **starts and stops a moving platform**. The platform is the
  existing vertical `Escalator` (32×16 px, 50 px/s, as in `forest-1`), the only way up to a 112 px ledge that holds the exit (not jumpable, clamberable
  or wall-climbable; `PlatformLessonTest` checks every approach x). It shuttles between its two ends for as long as it runs: bottom y=304 (16 px above the
  floor, a hop from the floor) and top y=176 (32 px above the ledge top, so the hero can step off for about 1.3 s each pass instead of two frames). The
  lever is on the floor 216 px before the platform, linked by a Tiled `target` reference (`check_tiled_links.py`), Up + Action per D30.
- **Rule (what stopping and starting do):** the lever is a plain toggle. The platform starts **stopped at the bottom**. Pulling the lever starts it
  and it moves up; pulling it again **stops it exactly where it is** (it does not return to an end and does not snap); a further pull continues in the
  direction it was going and it keeps turning at the ends. The lever sprite mirrors running/stopped. A rider is carried with the platform while it
  moves and stays standing on it when it stops. The platform is one-way (supports from above only), so it never crushes or blocks a hero on the floor,
  and it ignores horizontal collision.
- **Recovery / D26:** there is no pit and no death trap: under the platform and the ledge is plain floor, so every fall (off the platform, off the
  ledge, 112 px, no damage) lands on the floor on the entrance side of the lever or on the platform. The lever stays on the floor, 216 px from the platform, so it can
  never be pulled from the platform or the ledge; a hero on the platform when it stops (only possible by a toggle from elsewhere, tested through the
  mechanism) hops off to the floor, walks to the lever and starts it again. Therefore the exit is reachable from every state: stopped at any height, running, repeated
  toggling at any rhythm, after riding down again, after walking off the ledge. The room has no secured state before the exit; death or room retry
  restores the authored stopped platform at the bottom (the room snapshot owns it) and puts the hero at the entrance; Restart Level rebuilds it.
  Passed rooms freeze, so the platform is never needed again after the exit. No monsters, no hazards, no one-shot.
- **Hints:** one `controls` zone over the room plus two position-only message zones that butt together: lever ("starts the platform and stops it again",
  starts 232 px before the lever) and ride ("Jump on the platform and ride it up to the exit", starts 184 px before the platform and covers the ledge).
  Each is true whether the platform runs or stands. A continuous walk reads the lever message about 1.0 s fully visible before the lever is in reach and
  the ride message about 1.1 s before the body reaches the platform (`HintLeadTest`).
- **Length:** 640 px (was 1280): 232 px to the lever, 216 px lever to platform, 32 px platform, 128 px ledge with the exit 80 px from its edge. Later
  rooms and the `Checkpoint` moved 640 px left.
- **Not verified:** no novice playtest (#129–#131); not seen in the running game when written, later covered by the author's desktop and web play-through of 2026-11-05 (see D33); route, ledge-block, stop-at-any-height, retry and hint
  coverage are measured headlessly (`PlatformLessonTest`, mastered route about 9.6 s including the wait for the shuttling platform to come back down to the bottom; the platform cycles in about 5 s).

## D38. Every room has an invisible ceiling

- **Status:** Accepted, 2026-11-05 ([#215](https://github.com/jlogicgames/edgard_in_kimeria/issues/215)); found in a forest-0 playtest after #198.
- **Problem:** the side walls of a room are `Wall`-typed collision blocks, which can be clambered and wall-jumped (hold toward the wall, Jump). They end at the
  map top (y=0) and nothing closed the room above, so repeated wall jumps took the hero over the top (hitbox y below 0, about 3 s after starting) and out of
  the room and level. Reproduced headlessly in `forest-0` (all four finished rooms, either wall), `forest-1` and the `forest-3` (now `forest-2`) entry/canopy rooms
  (`RoomCeilingTest`; every forest-0 room, skeletons included, is tested); the same happened from the ladder and platform exit ledges, which give a 112 px head start.
- **Decision:** `Level.addCollisions` adds, for every room it builds, one invisible plain collision slab directly above the room (x = room x, width = room
  width, 64 px thick, underside at the room's top edge), the ceiling. Chosen over making the walls slippery (no grab) because wall climbing and wall jumping
  is a shipped mechanic the later levels (forest-3 (now forest-2) canopy) are built on and the issue is the room being open, not the mechanic; a ceiling fixes the
  exploit at its cause for every present and future lesson room and level (the stone and traps rooms copy the recipe and get it for free) without
  editing any map, so no map SHA, `check_tiled_links` or web-release-gate hash changes. The slab lies wholly outside the room, so it cuts no jump, ladder,
  platform, ledge or hint zone, and a vertical moving platform stays below it. 64 px is far more than one frame of jump travel, so nothing tunnels through.
- **Consequences:** a head bump on the room top stops upward motion like any block. A carried stone thrown straight up used to leave the room (and was then removed above it, `Carryable`); it now stops
  at the slab and falls back (`blocksObject` already refused placement outside the room). Nothing may be authored above a room's top edge.
- **Update ([D121](#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21), 2026-11-21):** the wall jump is gone, so the walls are no longer climbable. The ceiling slab stays and its assertions are unchanged.
- **Not verified:** the ceiling is invisible; behavior is measured headlessly (`RoomCeilingTest`); the rooms it affects were played by the author on 2026-11-05 (see D33), with no remarks.

## D39. The tutorial thrown-stone lesson rule

- **Status:** Accepted, 2026-11-05 ([#199](https://github.com/jlogicgames/edgard_in_kimeria/issues/199)); fifth lesson room of D33, after D34-D38. Design set by the author: a chasm in the middle of the room.
- **Decision:** `forest-0-stone` (528 px) teaches exactly one new element: a **stone that is picked up (Up + Action) and thrown (Ranged) at a lever the hero cannot reach by walking**. A 96 px chasm
  with no floor cuts the room in two. The lever stands at the far lip, linked by a Tiled `target` reference (`check_tiled_links.py`) to the **horizontal moving platform** of the platform room
  (the existing `Escalator`, 32×16, 50 px/s, started and stopped by the lever, D37), parked stopped at the near lip, flush with both lips. The platform is not a new element; only the throw trigger and the stone are.
  No monster, no wall, no second element.
- **Engine changes (all opt-in, `forest-1` and `forest-3` (now `forest-2`) unchanged because none of their objects carries the properties):** (1) a thrown carryable that touches a `Trigger` with the Tiled bool `throwActivated` operates it once per throw
  (`Level.throwHitsLever`, from `Carryable.update`); the engine had no remote activation. A stone only put down (Down + Action) never fires it. (2) a `Stone` with the bool `respawn` returns to its authored spot when it falls out of the room, when it is lifted
  out of the room above (a throw that starts inside a wall the hero presses against is lifted onto that wall's top) and after it hit a lever (`Carryable.returnHome`); without the flag nothing changes. Stones do not rest on a moving platform (it is not in the stone's collision list), they fall through it and come home.
- **Reach and jump rules (measured, `StoneLessonTest`):** a thrown object leaves the hand at 220 px/s sideways and 110 px/s up, so it flies about 100 px at hand height and rises only about 10 px. The lever's trigger is tall (y 272–320) and an
  **overhang** (plain collision, y 0–272, x 2864–3024) makes sure a stone launched under it cannot pass above it, and **caps jumps** to about 26 px of rise over the approach and the chasm. With the platform parked, 64 px remain between its end and the far lip: no running
  jump crosses them (swept from every takeoff x, platform parked at six stop positions) and the lever cannot be reached from the near side.
- **Throw window (real, swept up to the lever):** the stone reaches the lever when the body's right edge is from 8 px **before** the near lip (so a throw from the floor edge works) to the far lip (100 px beyond the near lip, on the platform); the window ends at the lever because that is the end of what the hero can stand on.
  A hero 8 or more px behind the edge throws short and the stone returns home. The room does not make a throw from further back work: the hint says to take the stone to the edge of the chasm. The route throws from 28 px onto the platform, which starts the platform under the hero's feet.
- **Fall rules:** falling into the chasm is the level's `Deadline`: death and the usual room retry. The room is unsecured until the exit is taken, so a retry restores the authored room (stone at its spot, lever down, platform parked stopped, empty hand, hero at the entrance); Restart Level rebuilds the whole level. The chasm is therefore a
  mistake that costs one retry, never a trap, because no state exists in which the room cannot be left. Stepping off the platform at the far lip needs the hero near the platform's far end (a late boarder walks to it on the way, tested).
- **Miss rules:** a stone that falls short, flies away from the lever, drops into the chasm, is put down on the platform (it falls through) or is thrown while pressed into a side wall **returns home** (the spot where it lay, on the entrance side). A stone that lands on the near floor stays there and can be picked up. A stone that hits the lever also returns home,
  so a stone is never stranded across the chasm and the platform can always be toggled again from the entrance side. `StoneLessonTest` sweeps over 200 throws (both facings, platform parked and running), jump throws at every phase, wall-pressed throws on both sides and put-downs on a platform at five stop positions, parked and running.
- **Repeated throws:** every hit toggles the lever (a real double and triple throw from the floor edge is tested): a second hit stops the platform where it is, a third starts it, as in D37. From the far side the lever is within hand reach (Up + Action), which also toggles it.
- **Recovery / D26:** the required route is reachable from every state. Platform stopped anywhere: the hero on the near side fetches the stone and throws again; the hero standing on a platform stopped mid-chasm (only possible by their own second hit) is never more than 32 px from the nearer lip, within the capped jump (tested at every stop position); the hero on the far side restarts the platform by hand.
  Platform running: it shuttles between the lips; a mistimed step costs a retry or at most one 2.6 s cycle.
- **No leaking:** carrying the stone into the exit puts it down inside its room when the room is secured (`releaseCarriedForRoomSecure`); the next room has no stone and an empty hand.
- **Hints:** one `controls` zone over the room plus three position-only message zones that butt together, each true wherever it shows: take the stone to the edge and throw it at the lever (starts 272 px before the stone, ends just past it), hit the lever with the stone, then ride the platform across (from there to the far lip: stone, edge, platform), and the exit is just ahead (far lip to the end, so a hero who crossed is no longer told to throw).
  The live label supplies "Up + Action: Pick up - Stone" beside the stone and the put-down/throw reminder while it is in hand (D30), drawn together with the message (#211). A continuous walk reads the first message about 1.2 s fully visible before the stone is in reach (`HintLeadTest`); the second starts 40 px before the edge of the chasm, which a walker never needs because the first message stays up while the hero carries the stone there.
- **Length:** 528 px (was 1280): entrance, the stone 224 px from it (lead for its hint), the near lip 64 px beyond the stone, the 96 px chasm, 80 px of far floor with the exit. The last room and the `Checkpoint` moved 752 px left. Mastered route about 4.8 s.
- **Not verified:** no novice playtest (#129-#131); the visuals (overhang and chasm tile art, the lever sprite at the far lip, the stone's flight and its return) were seen only in the author's desktop and web play-through of 2026-11-05, with no remarks; route, throw window, jump limits, miss, retry and hint coverage are measured headlessly (`StoneLessonTest`).

## D40. The tutorial traps lesson rule

- **Status:** Accepted, 2026-11-05 ([#200](https://github.com/jlogicgames/edgard_in_kimeria/issues/200)); sixth and last lesson room of D33, after D34-D39.
- **Decision:** `forest-0-traps` (720 px, the level's last room) teaches exactly one new element, the two **existing** traps, in sequence and with their D7 warnings unchanged: a **falling platform** bridging a pit, then a **contact bomb** on the floor before the level's `Checkpoint`.
  No lever, no mechanism, no monster; no new trap and no new asset or engine change (the traps of #188 are taught later). A 144 px pit (x 3328-3472, no floor, the level-wide `Deadline` below it) is wider than any jump (a full jump from the lip flies about 88 px, tested from every takeoff x with the platform removed), so the
  platform (32x16, x 3392-3424, flush with the floor at y=320) is the only way across. It sits 64 px past the lip and 48 px before the far floor, so the natural full jump from the lip lands on it and a full jump from its far end lands on the far floor.
- **Platform rule (D7, unchanged):** landing on it lights it; after the shared warning `TrapWarnings.FALLING_PLATFORM_SECONDS` (1.0 s, 1.5 s with assist) it falls and is removed. The hero may jump off while it sinks: a dawdle of up to about 1.45 s after landing still crosses (swept, `TrapsLessonTest`), so a mistimed step costs nothing and a hero who stands there until it is gone falls into the pit.
- **Bomb rule (D7, unchanged; *replaced by an exploding stump of the same geometry and timing in [D47](#d47-explosive-traps-replace-the-bomb-exploding-stump-and-exploding-root)*):** a bomb lies on the far floor 176 px past the pit (x 3648). Touching it lights the fuse (0.8 s, 1.2 s with assist; it flashes warm, leaving does not cancel it) and then a 64x64 blast around its centre (x 3624-3688) is checked once. Three ways pass without damage and are all tested: **jump over it** (it is never lit),
  **walk on through it** (the blast is behind the hero: a continuous walker's body is at least 25 px clear when it goes off) and **back away** at least 27 px from the touch point; the hero who stands still on it is hit.
- **Damage rule for the tutorial:** the game has one-hit death and no hit points, so a mistake is the existing cheap retry: death with the usual 0.7 s slow motion, then the room restarts from its entrance (about 6 s to walk back). Nothing is added: the pit, the fallen platform and the blast all end the attempt, never the game. A room that is shorter than its walking time keeps a retry cheap.
- **Recovery / D26:** the room is secured only when the level ends, so every retry rebuilds the authored room (platform unlit and in place, bomb unlit, hero at the entrance) and Restart Level rebuilds the whole level; both are tested. The required route is reachable from every state: a hero on the entrance floor after the platform has fallen (they stepped back during the warning) cannot cross, but the pit is the `Deadline`, so dropping in is a retry
  (tested). That recovery is told to the player by the platform hint, which is true in every state, rather than by resetting or re-arming the trap (D7: a spent platform stays spent until the retry, D26: no re-arming to make a gate pass). The pit's walls cannot be climbed back (swept wall-jump inputs from inside the pit, `TrapsLessonTest`), so the pit is neither a bypass nor a trap with no exit: it is a retry. Nothing can make the retry itself impossible. After the bomb has gone off the way to the `Checkpoint` is free. The bomb cannot be unavoidable: it lies on open floor, can be jumped, and the `Checkpoint` stands 72 px beyond its blast.
- **Assist and pause:** warnings are pinned to `TrapWarnings` (1.0 s platform, 0.8 s bomb; 1.5 s and 1.2 s with the longer-warnings assist), tested in this room within 3 frames in both settings, and the route still passes with the assist. Pause stops `KimeriaGame` from calling `Level.update` (it only runs while `gameStarted`), and both traps tick only inside it, so a paused warning does not advance; this is read from the code, not covered by a headless test.
- **Level end:** the room has no `RoomExit`; the `Checkpoint` (x 3760, inside the right wall, 72 px clear of the blast) finishes the level, loading `forest-1` as before (`DirectionalActionsTest` walks the whole level, now including the jumps).
- **Hints:** two position-only message zones that butt together, each true wherever it shows: the platform falls soon after landing and a fall into the pit just retries (x 3104-3440, starts at the room edge, about 1.05 s fully visible before a continuous walk reaches the pit lip), and a touched bomb flashes then explodes, so jump over it or run clear (*since #233 `HINT_TRAP_STUMP`, "A touched stump smokes, then explodes. ...", D47*) (x 3440-3792, starts 208 px before the bomb, about 1.4 s fully visible before the touch and
  kept up while the hero runs clear). No `controls` zone (no lever). `HINT_TRAP_PLATFORM` ("The platform falls soon after you land. Falling into the pit just retries"), `HINT_TRAP_BOMB` in all five catalogs, no key names, at most two lines (`LocalizationLayoutTest`, `HintLeadTest`).
- **Length:** 720 px (was 1280): entrance 32 px past the left wall, 208 px of run-up (its hint needs the lead), the pit, 176 px of floor to the bomb, 160 px beyond it to the `Checkpoint` and the wall. Mastered route about 6.1 s (author check, `TrapsLessonTest`).
- **Not verified:** no novice playtest (#129-#131); the visuals (pit tile edges, the platform's light, the bomb's flash and blast art) were seen only in the author's desktop and web play-through of 2026-11-05, with no remarks; route, jump span, warning timing, damage, retry and hint coverage are measured headlessly.

## D41. The healer's tales around the forest-0 tutorial

- **Status:** Accepted for implementation, 2026-11-05 ([#220](https://github.com/jlogicgames/edgard_in_kimeria/issues/220)); the wording is a proposal for author review, not approved story. Adds no plot fact: it uses only the STORY.md healer, the wicked-witch image (L'Kardina is never named or claimed here) and generic fairy-tale imagery.
- **Decision:** two short, fixed healer/Edgard conversations reuse the existing `HealerDialogue` / `HealerDialogueView` (portraits, typewriter, speaker regions, `Next >` prompt, Esc pause). No new UI. New `HealerDialogue.Kind` values `TUTORIAL_START` and `TUTORIAL_END` join `VICTORY`: scripted scenes with no checklist and no choices, finishing with a single `CONTINUE`.
- **Start tale (6 lines, the last one Edgard's):** a new game's first entry into forest-0, after the loading screen and before the first room is played. Pending only for a New Game run (`StartData.newRun`); consumed the moment it opens.
- **End tale (4 lines):** when `Level` calls `loadNextLevel()` for forest-0 (the last room's `Checkpoint`, or the F4 debug skip) and it has not yet opened in this run. Finishing it calls the ordinary `completeLevel()` (D32 order, save of the next level index, load of forest-1); until then nothing is saved.
- **Never repeats:** both are run state of `KimeriaGame` (`tutorialIntroPending`, `tutorialOutroShown`), consumed on opening, so death (room rebuild), retry, Restart Level and the repeated Checkpoint callbacks never open the start tale again. The farewell is the exception for one case: Restart Level pressed while it is open (Esc, Restart) replays forest-0, so `restartLevel()` clears `tutorialOutroShown` there and the farewell opens again when the tutorial is finished, instead of being lost for good (review of #220). Death and retries never reopen it. Exit to menu and New Game start a new run and re-arm them. Continue never arms the start tale.
- **Saves: no new field.** `SavedProgress` writes only after a level is completed (`nextLevelIndex` >= 1 once forest-0 is done), so a save can never place Continue inside forest-0 and a one-time flag would be dead data. Consequences, all tested: Continue (even a hypothetical save pointing at forest-0) never plays the start tale; a legacy save with no order key (D32 shift) resolves to forest-1 or later and never meets the tutorial or its tales; the end tale cannot replay after the save because forest-0 is not entered again. Exiting during the end tale leaves no save, so Continue is hidden or points at the previous save, as before. If saves ever gain mid-level points, the flags must then be persisted with them.
- **Pause, skip, input:** while a tale is open `KimeriaGame` is in `UiState.HEALER`: `Level.update` / `updatePresentation` and `roomFade` do not run, so enemy, trap, platform and warning timers do not advance and the hero cannot move. Input follows HEALER.md: the first fresh press reveals a line, the next advances (keyboard, gamepad and tap all feed the same path; held entry input is ignored). Skipping is pressing through (at most two presses per line, 12 for the longest tale; tested); Esc opens the dialogue pause menu (Resume / Restart Level / Exit to Menu). The start tale hands control back with jump input and gamepad jump suppressed until release; a skipped or finished tale never leaves the game blocked.
- **Texts** (EN, source; `HEALER_TALE_*` / `EDGARD_TALE_*` keys, five catalogs, no em dashes, no new glyphs, so no font rebake):
  Start: (Healer) "Edgard, the road ahead is short, yet it is a tale all the same. Hear it before you walk it." (Edgard) "A tale? The kingdom waits, and I have no time for stories." (Healer) "In this wood a sleeping fairy wakes the torch with a touch, a ladder is let down for kind hands, and a stubborn wall rises when the right spell is spoken." (Healer) "A golden boat of the air carries the bold over the dark gap. A far-off fairy can be woken by a stone sent like a wish. Do not be afraid to ask." (Healer) "Beware the gifts of the wicked witch: a bridge that crumbles and a berry that bursts. Answer her riddles with quick feet, and the wood will let you pass." (Edgard) "I understand, old man. Now let me go, the kingdom cannot wait."
  End: (Healer) "Well done, Edgard. The enchanted wood has told you all its tales, and you have solved every one of its riddles." (Edgard) "Then I am ready for the road?" (Healer) "Ready as the heroes of old tales, who were afraid and went on all the same. A true warrior needs no more than that." (Healer) "Take my blessing: walk lightly, look keenly, and look twice at any path that seems too kind, for witches smile the sweetest. May the fairies light your road."
  The healer never names a control or mechanic (lever, jump, button); `HealerDialogueTest` scans all five languages. Every line is measured by the existing baked-font speaker-region test (all `HEALER_`/`EDGARD_` keys).
- **Unchanged:** forest-1 and forest-3 (now forest-2) (the boss pre/post scenes, `afterVictory`, level order, saves, ending); the lesson rooms and `RoomCeilingTest`.
- **Note, #228 (2026-11-10): the silver thread.** The author accepted that Edgard returns after every fall because the healer helps him (narrative only, no mechanic; see [STORY.md](STORY.md#why-edgard-returns-after-every-fall--accepted-2026-11-10-228)). The start tale therefore has **7 lines** (was 6): `HEALER_TALE_THREAD` sits after `HEALER_TALE_WITCH` and before Edgard's last line, text: (Healer) "Edgard, I tied a silver thread about your wrist, and I hold the other end. When the night swallows you, I draw the thread, and you will rise again at the path's edge." The same image is one sentence in `INTRO_STORY`. The farewell stays at 4 lines and Edgard has no answer. Same one-shot rules as above. Skipping is still at most two presses per line, so the longest tale is now 14 presses (was 12); `CampaignFlowTest` asserts `2 x lines` instead of a fixed 12, and no width or height threshold changed. Measured with `QuestSquare-15` in the 370.67 px speaker region (limit 72 px, five wrapped lines): the new line is 3 lines / 37 px in EN (171 chars before trimming, 166 shipped), UK, PL, DE and BE. `HealerDialogueTest` also scans all five languages for game terms.
- **Note, #340 (2026-11-16): the fire-gift line.** The start tale has **8 lines** (was 7): `HEALER_TALE_FIREGIFT` sits after `HEALER_TALE_THREAD` and before Edgard's last line; text and the matching `INTRO_STORY` sentence are in [STORY.md](STORY.md). The line names no mechanic ("spark", "fire") and says the fire is not endless, so it keeps the fireball scarce. Skipping is still at most two presses per line, so the longest tale is now 16 presses; `CampaignFlowTest` expects 8 lines instead of 7 (no width or height threshold changed). Measured with `QuestSquare-18` (D70) in the 370.67 px region (limit 72 px): 4 lines in all five languages, widest line EN 363, UK 365, PL 367, DE 365, BE 356 px, about 66 px high. UK, PL, DE and BE are machine-quality drafts awaiting native review ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369), [localization validation](LOCALIZATION_VALIDATION.md)).
- **Author play (2026-11-05):** the start tale and farewell were played on desktop (keyboard) and the web build with no remarks (see D33). **Not verified in that session:** gamepad and touch (the author later reported a desktop gamepad check in both layouts on 2026-11-06, without naming what was played; touch stays unverified), and the native-speaker quality of the de, pl, uk and be translations (machine-quality drafts in the same register as the existing healer text).

## D42. Forest trap set, explosive-trap choice and tutorial plan

- **Status:** Accepted by the author, 2026-11-05 ([#188](https://github.com/jlogicgames/edgard_in_kimeria/issues/188)); design only, nothing is implemented. Evidence: [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md). It adds traps on top of D7 and keeps D7's rules.
- **Framing:** the 1994 original's traps are history; the remake is a retro-styled new work for the 2020s. The spirit is simple organic hazards on the ground and overhead, with variety by **trigger family**; more traps are welcome. **All traps are lethal** (or a moving block); each kill is preceded by a readable telegraph (D7) and recovers by room retry (D3, D26).
- **Forest trap set:**

  | Family | Trap | Behaviour |
  | --- | --- | --- |
  | Proximity | Thorn root / stake | When the hero approaches, the tip peeks out and wobbles (the warning), then it rises and stays up until the retry. |
  | Proximity | Bristling thorn tuft | Replaces the original's "camouflaged root". A clearly different colour from bushes; bristles on approach. |
  | Position | Falling branch | Visible tell plus a short delay, then a fall with real flight time; the hero may jump or run away during the fall. |
  | Position | Falling stone | A growing shadow on the ground is the warning. |
  | Touch | Exploding stump, exploding root | The existing bomb behaviour (shared fuse, one blast, D7). Replaces the glass-bottle bomb. |
  | Rhythm | Spore puffball mushroom | The cap swells, then a spore cloud. |
  | Rhythm | Swinging thorn vine | Periodic swing. |
  | Crumble | Rotten log bridge | The existing falling platform in a forest skin. |

  **Castle/fortress (later):** blade cutters (Prince of Persia style) and blue sparks / lightning appear **in the castle/fortress only** (the author's wording, 2026-11-05; the stage naming of STORY.md is not resolved here); the falling stone may also appear there.
- **Explosive-trap rule:** every explosive trap on a **required path** offers a choice: pay by **detonating it from afar** with a fireball or a thrown stone, or take a **riskier bypass** (jumps, a higher edge). The required route never needs ammunition (D26), so the **bypass is mandatory**.
- **Art:** the explosion is currently a shader, so **sprites are needed**: a stump/root wind-up (cracks, smoke, glow) and then the blast. The glass-bottle bomb is redrawn or replaced by the stump/root. Tracked by #230. **Style (author, 2026-11-05):** the remake's own style ([ART_DIRECTION.md](ART_DIRECTION.md)) without the 1990s limitations (palette, resolution, flat colours); only the original's silhouettes and readability ideas are kept.
- **Tutorial plan:** not all traps go to the tutorial. One new trap per existing `forest-0` room: ladder room, thorn root; wall room, falling branch; platform room, falling stone; stone room, an exploding stump that the thrown stone can detonate from afar or that can be bypassed; traps room, the glass-bottle bomb replaced by the exploding stump; the lever room stays pure. This **deliberately relaxes** the one-new-element-per-room rule (D34-D40). **D35-D40 will be updated when the integration (#235) lands**; until then they describe the current build.
- **Later ideas (author requests, 2026-11-05; not part of the forest set, not implemented):** the **falling stone is reusable across worlds** (stone skin and shadow per world, #232); **arrow shot from the side** ([#238](https://github.com/jlogicgames/edgard_in_kimeria/issues/238)) for world 2 and later, the projectile may change per world and the player dodges, with the **D7 telegraph required** before it fires; **floor pressure plate** activating a door like a lever, for the castle-fortress stage ([#237](https://github.com/jlogicgames/edgard_in_kimeria/issues/237); open: stays open or closes when released, D26, link by `target`). Related requests: parallax [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236) and environment effects [#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239) (ART_DIRECTION.md), enemy wind-up [#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240) (GAME_DESIGN.md).
- **Rejected by the author:** a carnivorous flower (does not grow in this forest), a rolling log (no slopes), a non-lethal burnable bramble (all traps are lethal), and the camouflaged root as such (the original root's colour already differed from the scenery, so it was never truly hidden).
- **Not decided here:** the tablet (its gate and use) lives in [#136](https://github.com/jlogicgames/edgard_in_kimeria/issues/136) and [#139](https://github.com/jlogicgames/edgard_in_kimeria/issues/139), where the original's gate stays an option. The original's video-only facts (blast delay and size, zero lives, stake retract, tall roots on approach, S14 castle versus fortress) are not needed for the remake; answer only if an original behaviour is wanted one-to-one (author, 2026-11-05). **No other decision is open in D42.**
- **Tickets:** #230 art (forest trap sprites, bomb replacement), #231 code A proximity (thorn root/stake, thorn tuft), #232 code B falling (branch, stone), #233 code C explosive stump/root replacing the bomb, #234 code D rhythm (puffball, vine), #235 tutorial integration; also #224 impaled-death sprite, #225 decompose screens, #229 Bat and Mushroom replace Chicken and Key.
- **Not verified:** none of this is built or playtested.

## D43. Bat and Mushroom replace Chicken and Key as the healer ingredients

- **Status:** Accepted by the author, 2026-11-05 ([#229](https://github.com/jlogicgames/edgard_in_kimeria/issues/229)); implemented. Evidence: [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md) (the original's item page: "You have 2 component ingredients of the potion: Bat, Mushroom"). It supersedes the Chicken/Key names in D16 and the earlier healer entries above; every rule in them (optional, secured only at room exits, consumed with the forest's coins, rollback, no combat bonus without payment) is unchanged.
- **Decision:** the level property `ingredients` of `forest-3` (now `forest-2`) is `BAT,MUSHROOM`. `ItemType` is `COIN`, `BAT`, `MUSHROOM`; the map objects are `Collectable` named `Bat` / `Mushroom` (ids 94 and 95, positions unchanged; the object type `Bat` stays the enemy). They are still optional (D16), carry no door or other special rule (the Key was never a door key: it was only a collectable), and behave exactly like the Chicken and Key did. Healer texts, checklist labels (`BAT`, `MUSHROOM`) and tests use the new names in all five languages.
- **Art:** original redraws, not DOS copies: a small purple bat (wings up/down) and a purple spotted mushroom, each a native 32×16 two-frame strip in `assets/images/objects/` (16 palette entries with alpha, binary alpha) plus 128×128 HUD icons. The coin kept `Items.png` (since removed: the coin is `objects/coin.png`, #350). Masters are hand-placed pixel maps in `art/objects/*-pixel.png` and `art/hud/*-master.png`; the HUD icons are the world frames smoothed up, not separately painted illustrations (no image generator was available). A later hand-painted HUD pass is welcome.
- **Art revised ([#345](https://github.com/jlogicgames/edgard_in_kimeria/issues/345), playtest F19, 2026-11-16):** the flapping purple bat read as an enemy (a Bat enemy also exists), so players avoided it. The ingredient stays the Bat (`ItemType.BAT`, map objects and checklist logic unchanged) but is drawn as an item: a corked glass jar tied with twine, one dried bat wing inside (`art/objects/bat-pixel.png`, two frames, the glass glint moves; outline 0.425 at its one placement, D76). Its label is "Bat wing" in all five languages (`BAT` text and the five healer lines that name it; glossary row in TEXT_STYLE.md). Every collectable wears one pickup cue, enemies none: a 2 px bob up and back every 1.2 s in whole pixels (only the drawing moves, the pickup box stays), a 24×24 warm-white halo drawn additively behind it with alpha 0.5 at rest to 0.9 at the top of the bob (every collectable but the coin, below), and a 5×5 sparkle (three 0.1 s frames) every 2.4 s, started 0.4 s later per 16 px of x (`Collectable`, `art/objects/pickup/{glow,sparkle}.grid`; `CollectableTest`; `AllocationBudgetTest.collectablesWithThePickupCue` 0.0 B/frame in 3 runs). **The coin has no halo** (`Collectable.hasHalo`): its white rim washes out against added light, outline 0.123 at forest-2 x 336 against the D76 floor of 0.28, and the rim already does a halo's job; it keeps the bob and the sparkle. `check_contrast.py` measures every collectable as drawn, at rest with the dimmest halo and at the bob top with the brightest (`NO_HALO` for the coin): Bat wing jar 0.541, Mushroom 0.533, coin 0.371. The HUD icon is a hand-drawn 128×128 pixel grid of the same jar (`art/hud/bat-master.grid`, accepted 2026-11-16). The detailed 128×128 HUD icon of the jar replaces the old bat icon.
- **Saves:** unchanged. Ingredients were never persisted (only the next level index is saved; the inventory is run-only), so a legacy save needs no mapping and a test pins it.
- **Placement note:** the Mushroom hovers at the Key's old spot like every other pickup; a stepping platform under it was tried and rejected because it made the pickup a walk-through of the main route (the optional jump is the point).
- **Verified:** automated tests and guards only; the sprites are not yet seen in the running game.

## D44. Proximity traps: thorn root and bristling thorn tuft

- **Status:** Implemented, 2026-11-05 ([#231](https://github.com/jlogicgames/edgard_in_kimeria/issues/231)); the proximity family of [D42](#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan). **Not placed in any shipped level**: the tutorial integration is #235. Tested in test-resource maps only (`thorn-*.tmx`, `ProximityTrapsTest`).
- **Decision:** two Tiled object types in `SpawnPoints`, both one class (`ThornTrap`): `ThornRoot` (the stake, sprite `TRAP_THORN_ROOT`, 16×24) and `ThornTuft` (`TRAP_THORN_TUFT`, 16×16). The object rectangle is the footprint; its bottom edge is the floor. Both sit quiet and **harmless** until the hero's body hitbox overlaps the **trigger zone**, then run one fixed sequence and stay lethal until the room is rebuilt.
- **Zone:** the footprint widened by `zone` px on each side (default **80**; optional `zoneLeft` / `zoneRight` override a side, 0 makes it one-sided; each **used** side must be at least **64 px**), from 32 px above the footprint down to the floor. A hero standing beside the trap outside the zone is safe forever. The zone is checked against the same body box that dies (11×22), so what triggers is what kills.
- **Warning (D7):** on trigger the warning lasts `TrapWarnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS)`: **0.8 s, 1.2 s with the longer-warnings assist**, fixed at the trigger like the bomb's fuse. Root: the tip peeks out and wobbles between `peek1` and `peek2` every 0.1 s. Tuft: it bristles (`bristle` frame). Nothing hurts during the warning, not even a hero standing in the column.
- **Lethal phase:** root: `rising` frame for 0.12 s (movement, not scaled) during which the lower half of the column (11 px) kills, then `up` for good (22 px tall, 6 px wide, centred); tuft: `spiked` frame, 12×12 px centred, lethal at once. Hero hitbox overlapping the column is instant death through the ordinary hero death (slow motion, then room retry), as the other lethal traps. Beside the column or above it (a jump) is safe.
- **Determinism:** once triggered the sequence **always completes**: leaving the zone does not cancel it, and a bristling tuft does **not** re-calm (same as the bomb's lit fuse). A lethal trap **stays lethal** until the room is rebuilt: Retry, Restart Level and re-entering the room all reset it to hidden/calm (it has no saved state, so a returned-to room starts hidden; a deliberate simplification: forgiving, and no extra snapshot type). Timers tick only inside `Level.update`, so pause freezes them.
- **Fairness and D26:** a trap is always passable on a floor with headroom. A full jump clears an armed root from take-offs 7 to 63 px before it (body right edge to the column; 57 px wide window) and an armed tuft from 2 to 61 px (measured every pixel). Walking straight through the default zone takes about 0.97 s, longer than the 0.8 s warning, so it is hit; with the assist (1.2 s) it passes, which is the intent of the assist. Jumping, or waiting for the rise and then jumping, always passes, and a hero in front of an armed trap can always walk back: the route is never closed. No ammunition, no lever.
- **Who can be hurt (same guard as the other hazards):** the `Deadline`, enemy contact and the bomb's trigger all run in `Player.checkGameplayOverlaps`, which only runs while `Player.canLeaveRoom()` (not dying, resetting or arriving, not after the checkpoint). The thorn traps use exactly that guard: `Level` hands them no hero body when `canLeaveRoom()` is false, so a hero who is already dying neither triggers the next trap nor is killed again, and a trap already running keeps its timer (as a lit bomb fuse does). Rhythm and falling traps must use the same rule.
- **Authoring rules (enforced when the map loads, `Level` fails with a message naming the object; `check_tiled_links.py` mirrors them for what a map alone can say, except the hero entrance):** the **jump envelope**, the footprint widened by `max(zone side, 64)` px on each side and **96 px** above the floor, must lie inside the room; have **floor under all of it** (floor blocks whose top is the floor line, joined end to end; no pit, ledge edge or step beside the trap); and be free of anything solid: every collision block including one-way `Platform`, and the swept space of `FallingPlatform`, `Escalator` (with its travel), `Ladder` (with its drop), wall `Actionable` (with its travel). Nothing may stand in the trap's own column. Together this means never a one-tile corridor or low tunnel, where it could not be jumped, and always a take-off and a landing run. The room entrance body must be outside the zone (it never starts triggered). **Bad numbers fail**: `zone`, `zoneLeft`, `zoneRight` must be plain decimals (no `NaN`, `inf`, exponent, suffix), non-negative, at least one side used; they are validated inside the trap class on the raw property strings, never defaulted. Recipe: [ROOMS.md](ROOMS.md#proximity-traps-231).
- **Art:** existing frames only; no frame was missing. The root's `rising` frame is drawn for the 0.12 s rise at full height (no clipping), and its lethal half-height during it is an approximation of the art.
- **Not verified:** how the traps look and feel in the running game (sprites, wobble readability, draw order against the hero), no playtest; the F1 debug view does not outline the zones; behaviour is measured headlessly.

## D45. Falling traps: the position-triggered branch and stone

- **Status:** Implemented, 2026-11-05 ([#232](https://github.com/jlogicgames/edgard_in_kimeria/issues/232)); realises the "position" family of [D42](#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan). Not placed in any shipped level yet (the tutorial integration is #235).
- **Common rule (`FallingTrap`):** a trap is **armed** until the hero's body box overlaps its authored **trigger zone**; then a **warning** of `TrapWarnings.DEFAULT_WARNING_SECONDS` (0.8 s, 1.2 s with the longer-warnings assist; the zone stays "used" even if the hero leaves) shows the telegraph; then the body **falls straight down** with constant acceleration 900 px/s² from rest (`FallingTrap.GRAVITY`; an 80 px drop takes 0.42 s, a 304 px drop 0.82 s) to the top of the first solid below it; **it kills on contact** (the usual instant death and room retry, with the death slow motion showing the cause; exact rule per trap below). Death goes through the hero's `collidedWithActor(false)` and `die()`, **the same entry and guards as the bomb blast** (already hit, victory walk, debug invulnerability); the falling platform never kills, it only drops, so there is no other kill path to align with. The bomb's blast also kills after a checkpoint was reached, as these do; after impact it is **spent: harmless, non-solid scenery** until the room is rebuilt. **Single-shot:** a spent trap never re-arms by itself. Retry, Restart Level and re-entering the room rebuild the room from the map, so they re-arm it (traps are not part of the room-visit snapshot, so a spent trap is armed again whenever its room is entered again, e.g. the healer-return). Timers advance only inside `Level.update`, so pause stops them (read from the code; as D40, not covered by a headless test).
- **Landing and fairness limits (`FallingTrapLayout.validate`, run for **every** trap of **every** room when the `Level` is constructed, so a bad trap in room 5 fails the load, never mid-play; the object is named; `tools/check_tiled_links.py` mirrors all of it):** it must start inside its room (never in the D38 ceiling slab: above the room is "outside rooms"); have a solid below it in the same room (`Collisions` layer, not `QuickSand`; one-way `Platform` blocks count); a flight time of at least `FallingTrap.MIN_FLIGHT_SECONDS` = 0.3 s (a drop of at least about 41 px); the zone properties are parsed from the raw strings and must be finite positive-size numbers (the shared `propFloat` would silently read garbage as 0), inside the room; the zone must overlap the trap's x-range (the lethal column) and the hero's body standing on the floor (22 px above the landing top), otherwise a walking hero can never trigger it; the room entrance must not put the hero inside both the zone and the column; a **stone must start at the room top or directly under a solid block** (never pop into view mid-air); and no `Actionable` (e.g. a lever-raised `Wall`), `Escalator` or `FallingPlatform` may stand in the fall column, because the landing could not be fixed. `FallingTrap.HERO_BODY_*` mirrors the `Player` hitbox and a test pins it.
- **Tiled objects (layer `SpawnPoints`):** `FallingBranch` and `FallingStone`. The object's own rectangle is where it hangs / starts and **its lethal footprint** (author it as the sprite size, 32x16 and 16x16). Required properties `zoneX`, `zoneY`, `zoneWidth`, `zoneHeight` (the zone, absolute world pixels). `FallingStone` only: `skin` (default `forest`).
- **Falling branch:** hangs as harmless scenery (it neither blocks nor hurts at rest; the hero may jump through it). The **first half of the warning rustles** (frames resting/rustle alternate every 0.1 s), **the second half sags**, then it falls. The `TRAP_BRANCH` sheet has no landed frame, so a landed branch shows the "falling" frame lying on the floor. **Kill rule (revised after review, matches the author's "it falls on your head if you do not jump"):** during the fall the swept box hurts only a hero who is **on the ground** (`Player.isOnGround()`); at the **impact update** the landed box (the branch's own 32x16 at the floor) hurts anyone overlapping it, grounded or not. So standing still dies, leaving the column before impact survives (run on, run back, or a running jump), and a **jump in place survives** if the hero is airborne above the landed branch's top (16 px) at impact. The swept box is still used for the grounded check, so a long frame cannot tunnel through a standing hero. **Measured jump window** (real jump arc, 60 fps, hero standing in the column, Jump pressed N updates before the impact update): N = 3 to 48 (0.05 s to 0.8 s before impact, 46 frames wide), identical with and without the assist because it is relative to the impact; too late is still below the branch top, too early lands back under the falling branch and dies (`FallingTrapsTest`). A continuous run from the first pixel of the zone leaves the column 0.2 s before the branch starts to fall; every takeoff frame of a running jump survives, with and without the assist. A hero airborne in the column passes the branch visually without harm until impact; that is the price of the jump rule. A landed branch is non-solid scenery. **The falling stone keeps hurting on any contact** (an airborne hero is hit too): a rock does not spare a jumper.
- **Falling stone:** the stone is **hidden while the shadow warns** (it starts at the top of the room, flush with it or lower, "from above"); a ground **shadow** (`TRAP_SHADOW`, drawn on the landing surface under the stone) **grows through the warning**: frame small, medium, large by thirds and width from 35% to 100% of the frame; then the stone appears and falls (`TRAP_STONE` frame 0) and on impact shows the cracked frame (frame 1) and stays. It is **not** the hero's throwable `Carryable` stone: it cannot be picked up, thrown, pushed or stood on, no fireball or sword interacts with it, and it never enters the carry code.
- **Reusable across worlds (author, 2026-11-05):** the `skin` property selects the stone and shadow sheets from `FallingStone.SKINS` (only `forest` exists: `TRAP_STONE`, `TRAP_SHADOW`; a name outside the table fails the level load, and `tools/check_tiled_links.py` knows the same list). A new world adds one entry and its two sheets, no other code.
- **Skins, single source of truth:** the names are the string keys of `FallingStone.SKINS`; `tools/check_tiled_links.py` reads them from that Java file (`falling_stone_skins()`), so the two cannot differ, and a Java and a Python test pin the set to `forest`.
- **Warning length, exact:** the trap adds one float 1/60 s step per update, so the base 0.8 s warning is exactly **49 updates** (48 float steps sum to just under 0.8) and the assisted 1.2 s is **73** at 60 fps; the test computes the same sum and pins both literals. A pause test shows that without `Level.update` calls nothing advances and that resuming continues the remaining warning (the pause itself is the existing `KimeriaGame` behaviour of not updating the level).
- **Recovery (D26):** a required route may pass a trap by walking on from the edge of its zone with the telegraph in view, or by a running jump, and survives the assist; a hero who stands still in the column dies and retries (a jump in place is also a valid answer at the branch, within the window above) from the room entrance. The trap never blocks the route afterwards.
- **Not verified:** how it looks in the running game (tell frames, shadow growth, the death view), no novice playtest (#129-#131), no human check of the art frames used; everything else is measured headlessly in `FallingTrapsTest` with fixtures `falling-traps-test.tmx` and `falling-bad-*-test.tmx`.

## D46. Rhythmic traps: spore puffball, swinging thorn vine and the rotten-log skin

- **Status:** Implemented by [#234](https://github.com/jlogicgames/edgard_in_kimeria/issues/234) from [D42](#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan); not placed in any shipped level (the tutorial integration is #235). Automated tests only; nobody has seen the sprites move in the running game.
- **Shared rules.** Both traps are deterministic functions of time since the room began: the clock is `elapsed`, which advances only in `Level.update` (so pause freezes it and resuming continues exactly where it stopped) and which a room retry, Restart Level, a room re-entry or a death reset to the authored phase, because those rebuild the objects. There is no randomness and no dependence on the hero. Lethal contact uses the hero's body box and kills exactly like the bomb blast: one overlap test, then `player.collidedWithActor(false)` with **no extra state guard** (the bomb blast has none; `Player.die` alone ignores it while the hero is already hit, in the victory walk or invulnerable in debug). The falling platform is not lethal and has no kill rule. Death, then the room retry (D26). Placement makes the guard unnecessary: **a lethal area may never cover the room entrance (hero body at `RoomStart`), the `RoomExit` or a `Checkpoint` of its room, at any phase**, so every retry is survivable and the exit is always reachable (the vine is tested over its whole swing, the puffball over its whole cloud box); the load fails with the object named. Authoring errors fail the load with the object named: the danger area must lie inside the owning room, the properties must be finite numbers (the traps read the raw strings themselves, so `1,5`, `NaN` or `Infinity` fail instead of silently falling back to a default), and the rhythm must pass the minimum rules below. `tools/check_tiled_links.py` repeats the property rules.
- **Spore puffball** (Tiled object type `Puffball`, a 16×16 rectangle on the floor; sheets `TRAP_PUFFBALL` frames idle, swell1, swell2, burst and `TRAP_SPORES` cloud1-3). One cycle of `period` seconds is **calm, then swell, then cloud**, measured as `(elapsed + phase) mod period`. The swell is the **shared warning** `TrapWarnings.DEFAULT_WARNING_SECONDS` through `TrapWarnings.duration`: **0.8 s, 1.2 s with the longer-warnings assist** (the cap swells over the first and second half, then bursts); the cloud is lethal for `cloud` seconds (default 1.0) while the collapsed cap and the animated cloud are drawn; then calm. Properties: `period` (default 4.0 s), `phase` (default 0 s, shifts the clock), `cloud` (default 1.0 s). **Lethal volume:** the 24×24 cloud sprite, centred on the mushroom and sitting on its base line, shrunk by 2 px per side, so 20×20; the mushroom body itself is never lethal, so a hero standing next to it is safe in every phase. Because the assist lengthens the swell at the expense of calm, the **minimum calm** is checked with the longest swell: `period >= cloud + 1.2 + 0.8`, so the defaults leave 2.2 s of calm (1.8 s with the assist) and at least 0.8 s of calm plus the swell (a pass from the first calm frame needs about 0.38 s: the 38 px crossing at 100 px/s). A hero inside the cloud's column when the swell ends dies; the swell is the warning to leave.
- **Swinging thorn vine** (type `ThornVine`, 24×40, sheet `TRAP_VINE` frame hang, rotated about its top centre; the swing frames are unused because they lean without being anchored at the top centre). A pendulum: angle from the vertical `amplitude * sin(2 pi (elapsed + phase) / period)`, continuous and deterministic, so **the motion is the warning and D7 needs no extra tell**: the hero reads the rhythm by watching it, and the pass is by timing, never by reaction. Properties: `period` (default **4.5 s**, minimum 3.5), `amplitude` (default **50°**, allowed 35-60), `phase` (default 0). **Lethal part:** the lower half only, the segment from half the vine length (20 px) to the tip (40 px), 4 px half-width (effectively a thorn part of 8 px, tested as the segment against the hero's box grown by 4 px, so the capsule's round ends are slightly generous); the upper stem is harmless. The whole swept area (length plus the half-width around the anchor) must lie inside the room. **Recipe:** hang the vine so the **floor is 64 px below the object's top edge** (anchor), then the hanging tip reaches 6 px into the hero's head height and the tip only clears the hero's head (26 px) when it is raised by the swing: the vine is lethal while it is within about 32° of vertical and harmless beyond that, and the hero runs under it while it is lifted to the side. That gives a safe window of about 1.2 s per swing at the defaults (two per period, about 1.5 s of start offsets measured by the blind walker), against a 0.7-0.8 s crossing of the danger column. Do not hang a vine lower than that recipe without re-measuring.
- **Rotten log** (map property `skin=log` on a `FallingPlatform`, default unchanged): identical behaviour (the same falling platform, the shared 1 s warning, the same fall), only the picture changes, `TRAP_LOG` intact until landed on, crumble1 for the first half of the warning, crumble2 for the rest and the fall; it is drawn over the whole object rectangle (32×16 like the platform sprite). The tutorial traps room is untouched. An unknown `skin` fails the load.
- **Frames not used / nearest used:** none is missing; the vine uses `hang` for every angle (rotation instead of the two lean frames, see ART_DIRECTION.md "pendulum by swing frames or rotation").
- **Rooms:** authoring recipe and hint-zone advice for #235 in [ROOMS.md](ROOMS.md#rhythmic-traps-234-d46).
- **Tests:** `RhythmTrapsTest` (headless, the real map `core/src/test/resources/tiles/rhythm-test.tmx` and two broken fixtures): swell tell equals the shared warning (literal 0.8 and 1.2), cloud lethal only inside the cloud while it lasts, a hero beside it is safe, safe windows exist and a scripted ordinary-controls walker passes at every phase offset (0.05 s steps) for both traps, vine hitbox and period, determinism, reset on retry, pause, the log falls like the platform, nothing in forest-0/1/3 changes.
- **Not decided or not verified:** the sprites' look in motion, the sound (none yet), whether 4.0 s and 4.5 s feel right to a first-time player, and any shipped placement (#235).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the plain yellow falling-platform skin is replaced everywhere by the rotten-log look (`skin=log`); this decision will be revised by issue [#551](https://github.com/jlogicgames/edgard_in_kimeria/issues/551).
- **Update (2026-11-21, author: "replace all flying logs with the rotten log", "delete the code of the flying yellow platforms and the flying logs"):** every plain `FallingPlatform` (forest-0 x 4096, forest-1 x 96 to 192 and x 1648, forest-2 x 1568) now carries `skin=log`, like the log over the pit; `Skin.PLATFORM`, the plank sheet `trap-falling-platform`, its grid, the rope sway constants, its contrast, rim and crack-pin entries are deleted. An object with no `skin` is the log. The shared 1 s warning, the fall and the boxes are unchanged (a look-only swap). D7, D69 and D71 stand: the falling platform is now VISIBLE (the rotten look is its cue, D71), no longer DISGUISED.

## D47. Explosive traps replace the bomb: exploding stump and exploding root

- **Status:** Implemented by [#233](https://github.com/jlogicgames/edgard_in_kimeria/issues/233) from [D42](#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan). The only shipped placement is the replacement of the bomb in the `forest-0-traps` room ([D40](#d40-the-tutorial-traps-lesson-rule)); the other tutorial rooms are #235. Automated tests only; nobody has seen the new sprites animate in the running game.
- **Author decisions (2026-11-05):** the stump and the root **replace** the glass-bottle bomb; they explode on **touch** (the original's behaviour); **every explosive on a required path offers a choice**: pay by detonating it from a distance with a fireball or a thrown stone, or take a riskier bypass; the required route never needs ammunition ([D26](#d26-observe-plan-before-committing)), so the bypass is mandatory; all traps are lethal.
- **Tiled objects (layer `SpawnPoints`):** `ExplosiveStump` (16x16, sheet `TRAP_STUMP`) and `ExplosiveRoot` (16x24, `TRAP_ROOT`). No properties. The object rectangle is the sprite footprint; the **trigger box** is the same inner box as the old bomb's activation hitbox, 10x13 inside the 16x16 frame (3 px in per side, 2 px down from the top, scaled with the object: the root's is 10x19.5), so the empty corners of the art do not trigger it and touching and jumping behave exactly as with the bomb; author it standing on the floor (stump y = floor - 16, root y = floor - 24). Class `ExplosiveTrap`.
- **Sequence, one way:** `IDLE` -> `WINDUP` -> `BLAST` -> `SPENT`. **Touch:** the hero's body box overlapping the trigger box starts the wind-up (same guard as the other hazards: the hero is only seen while `Player.canLeaveRoom()`, but a running wind-up keeps ticking). **Wind-up:** the shared D7 warning `TrapWarnings.DEFAULT_WARNING_SECONDS` through `TrapWarnings.duration`, **0.8 s, 1.2 s with the longer-warnings assist, sampled once when it starts**, through the three wind-up frames (cracks, smoke, glow, each a third of the warning). It always runs to the end: leaving the box does not cancel it, a second touch neither restarts nor postpones it. **Blast:** one **64x64 square centred on the object** (the existing bomb blast); the hero's body box is tested **once at the instant of detonation** and is killed through `player.collidedWithActor(false)` exactly as the bomb blast did (no extra guard; `Player.die` holds them). The four `TRAP_BLAST` frames (32x32, drawn at 2x) then play for 0.4 s without hurting: a hero walking into the square after detonation is safe. The old shader effect is not used for these traps. **Spent:** the object stays in the room as **non-solid, harmless scenery** in its `spent` frame (nothing blocks the way afterwards and it cannot be lit again). A retry after a death on the first visit and Restart Level rebuild the authored room, so it is whole again. **Room-state retention (like the bomb and the falling platform):** the trap is a registered room-state owner (`registerRoomStateOwner`, `ExplosiveTrap.Snapshot` of phase, phase time and warning length), so when the hero leaves the room alive and comes back, or retries a room already secured by a visit, a spent trap stays spent and a winding-up one resumes its saved countdown. Every timer ticks in `update` only, so pause freezes it.
- **Detonation from afar:** a **fireball** (`Fireball`) or a **thrown stone** (`Carryable` of kind stone in flight) that reaches the trigger box of an **intact** trap starts the very same wind-up with the **full warning**: a distant hit shortens nothing, so the hero can still step away from the square, and the assist lengthens it as for a touch. Only an intact trap is hit: a fireball or stone **flies on through** a trap that is already winding up, in its blast or spent, so no ammunition is wasted on it. **Fireball:** one charge is paid when fired (D26 ammunition), the lit fireball is spent. **Stone (D39 rules):** a stone costs no charge and is **not consumed**: like after hitting an enemy it stops at the trap (`thrown = false`, horizontal speed 0), falls and lies at rest, and the hero can pick it up and throw it again. A thrown **torch does not** light it (only the stone and the fireball are this trap's ammunition). Walls and blocks stop both projectiles first, as everywhere (a trap behind terrain cannot be lit). Nothing else interacts with it: the sword and `throwActivated` levers are unrelated.
- **The bypass rule (`ExplosiveTrap.problem`, run for **every** explosive of **every** room when the `Level` is constructed, so a bad trap fails the load with the object named; `tools/check_tiled_links.py` mirrors it, and both are tested against the same fixtures):** a hero with **no ammunition** must be able to get past without touching the trap. Precisely, the trap must stand on **open floor with the jump envelope of the thorn traps (D44, `ThornTrap.openFloorProblem`, shared code) clear**: floor under the footprint and 64 px either side (take-off and landing; no pit, pillar or ledge edge), **96 px of free height over all of it** counting every solid thing (collision blocks including one-way platforms, falling platforms, walls with their travel, ladders, escalators with their travel; so **not a one-tile corridor, not under a low roof, not between walls**), nothing solid in the trap's own column, the envelope inside the room. The hero jumps over the trigger box and lands beyond it; the blast is not a factor because nothing was lit. Two more rules for the **blast square**: it must lie inside the room, and it must not cover a place the hero has to stand (the entrance body, the `RoomExit`, a `Checkpoint` of the room), because a hit from afar may light the trap while the hero waits there. **What the rule cannot know:** which objects are on a required route; so it is applied to **all** explosives. **Explicit routes:** an author may add a higher ledge or platform line as an extra, riskier path; it is never counted in place of the floor bypass (the validator does not trace routes), and it must itself pass the D26 checks.
- **The two answers, both tested:** (1) with the fireball stock set to 0, a scripted ordinary-controls walker (D, J) jumps over a stump and a root on the fixture and over the forest-0 stump without lighting them, with and without the assist, from a range of take-off points; (2) paying a fireball, or a stone, from outside the square, waiting out the warning and walking through the spent remains.
- **Replacing the bomb in `forest-0-traps` (D40):** `Bomb` (id 73) at x=3648, y=304 became `ExplosiveStump` (id 73, "Exploding Stump") at the same place, so the lesson geometry is unchanged: 176 px of floor from the pit, the blast square x 3624-3688, the `Checkpoint` at x=3760 (72 px beyond it) and the unchanged 720 px room. The validator accepts it (open floor from x 3584 to 3728, nothing above). The hint `HINT_TRAP_BOMB` is renamed `HINT_TRAP_STUMP` in all five catalogs and rewords "A touched bomb flashes, then explodes" to a stump that smokes and then explodes ("A touched stump smokes, then explodes. Jump over it or run clear"); still position-only and true in every state (it states the rule of the trap, not the state of the one in the room, so it stays true after the stump is spent), no key names, at most two lines; zone renamed `forest-0-traps-stump`. The room still teaches only the touch and the two free answers (jump, run clear); detonating from afar is **not** taught here (the tutorial plan puts that stump in the stone room, #235). **Mastered route re-measured** (`TrapsLessonTest`, author check, not a novice playtest): walking through the lit stump **6.12 s**, jumping over it **6.12 s** (about 6.1 s before, geometry and timing are the same), warning 0.78 s (0.8 s) and 1.18 s (1.2 s) with the assist, assisted route still passes. The healer tales (D41) mention no bomb or bottle ("a berry that bursts" left alone, it does not contradict a stump).
- **Other uses of the old `Bomb`:** the class, the `Bomb` object type, `BombExplosionEffect`, the shader and the bottle art stay and work. `forest-3.tmx` (now `forest-2.tmx`) still has one `Bomb` (id 54, x=656, y=320): it is **not replaced here** (D51: the author later kept the Bomb as its own trap type) because that level is outside this ticket's scope and the object sits at y=320, which is the top of the floor strip (its footprint is inside the ground), so it would also fail the new open-floor rule as it stands; replacing it needs a layout decision. The test rooms (`room-test.tmx`) use `Bomb` too, and `BombTest`, `RoomTest` and `RoomReplayTest` keep covering it.
- **Not decided or not verified:** how the sprites and the 2x blast look in motion (the 32x32 blast art scaled to 64x64 is the open visual question), the sound (the old "bounce" sound plays at detonation, no new one), any placement beyond forest-0 (#235), whether the stone's arc and the fireball height find a 16 px stump comfortably in play (measured headlessly: a standing hero's fireball hits the 16 px stump and the 24 px root, and stones thrown from a range of distances reach the stump; no novice playtest, #129-#131).
- **Tests:** `ExplosiveTrapsTest` (fixtures `explosive-*.tmx`, including `explosive-rooms.tmx` for the room re-entry tests), `TrapsLessonTest` (the real forest-0 room), `HintLeadTest` and `LocalizationLayoutTest` (hint), `TutorialLevelTest` (object census), `tools/test_check_tiled_links.py` (`ExplosiveTrapLinksTest`, agrees with every Java fixture).

## D48. Traps in the tutorial rooms: one new trap per lesson room

- **Status:** Implemented by [#235](https://github.com/jlogicgames/edgard_in_kimeria/issues/235), 2026-11-06 (revised the same day after review), from [D42](#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan). Automated tests only; nobody has played the new rooms, and nobody has seen the new telegraphs in motion in the running game. The author's acceptance of `forest-0` (2026-11-05) is of the revision **before** this change.
- **Rule relaxed on purpose (author, D42):** the recipe's "one new element per room" now reads "one new lesson element, plus at most one trap in the four middle rooms". The lever room stays pure; the traps room is unchanged apart from moving (D47 stump).
- **The trap comes first in each of the three rooms with a lever (changed in review):** entrance, trap, lever, lesson object. Three rules force this order, and no lever-first layout satisfies them together: (1) the lesson object must stay within one screen of the lever's reach (the lesson tests assert < 320 px, measured 316 px at most); (2) a hint needs about 160 px between its zone start and the thing it warns about for 0.9 s of fully visible text (`HintLeadTest`, measured: 162 px gives 0.93-1.0 s); (3) the trap hint must last past the trap's column and the next text must start beyond it (review item), so a walking hero never watches a text swap mid-crossing. Lever-first needs lever reach -> trap trigger 161 px + column 16-48 px + object lead 161 px = 338-370 px, over the 319 px cap in the wall (by 45 px) and platform rooms; in the ladder room it also put the armed root only 85 px from where a hero who walks off the ledge to the left lands (he was killed in the air). Trap-first needs entrance -> trigger 165 px (first message) + column + lever lead 161 px + object lead 161 px, with no cap problem. The stone room has no lever before the stone, so its stump stays after the stone. A retry after the trap now costs only the first few seconds (the root hit comes 2.6 s in).
- **Placements** (final `forest-0.tmx` x; every room grew only by what the trap, its 64 px jump envelope and its hint leads need, in tiles of 16; later rooms, the tile layer, `Deadline` and `Checkpoint` moved, ids unchanged, new ids 77-84; the levers of the ladder, wall and platform rooms moved right of their trap):

| Room | Trap | Where | Room length | Mastered route (headless) |
| --- | --- | --- | --- | --- |
| ladder (x 576) | `ThornRoot` (id 77), `zone` 64 | x 896, trigger from 832; lever 1080, ladder 1216 | 704 -> 896 px | 6.9 -> 8.82 s (jumps the root) |
| wall (x 1472) | `FallingBranch` (id 78) | column x 1744-1776, 64 px above the floor, zone x 1728-1776; lever 1944, wall 2096 | 640 -> 784 px | 5.1 -> 6.48 s (walks on) |
| platform (x 2256) | `FallingStone` (id 79), skin `forest` | column x 2544-2560 from the room top, zone x 2512-2560; lever 2728, platform 2880 | 640 -> 800 px | 9.6 -> 11.85 s (walks on) |
| stone (x 3056) | `ExplosiveStump` (id 80) | x 3488 on the near floor, between the stone (3328) and the chasm lip (3616); blast square x 3464-3528 | 528 -> 736 px | 4.8 -> 6.88 s walking through or jumping it, 8.47 s setting it off from afar and waiting |
| traps (x 3792) | unchanged (platform, stump) | | 720 px | 6.1 s |

  What forces each length (the minimum reached under all rules): *ladder* 896: first message 165 px (T = 832) + root column and 64 px landing + lever lead 164 px + the climb text lead (128 px) and the lever-to-ladder clearance (the lever's reach band ends 20 px past its x, and the existing test wants the ladder centre more than 120 px beyond that, so 216 px) + the 224 px ledge; *wall* 784: 165 + 48 (branch) + 164 (lever lead) + 162 (wall lead) + 96 px tail to the exit; *platform* 800: the same chain with a 16 px stone column and the 176 px ledge tail; *stone* 736: the stone sits 208 px from the entrance (its lead was 1.18 s and now is 1.02 s; 16 px less would break 0.9), the stump hint zone starts at the stone (the throw text must still show at the pickup spot), the stump is 164 px later (0.9 s lead), and the overhang may not start within 80 px of it (the 64 px floor rule and the 96 px headroom rule of D44/D47), then the unchanged 48 px overhang lead, chasm and far side. Nothing was relaxed: not the 0.9 s lead, not the lever-to-object cap, not the 64 px floor rule. The two `TutorialLevelTest` thresholds "wall-lever and platform-lever message lead the lever by 200 px" became 160 px, the same 0.9 s as `HintLeadTest`, because those messages now start after the trap text instead of at the room start.
- **Hint zones and texts** (new keys in all five catalogs, `Msg`, `LocalizationLayoutTest`; plain wording, no key names, two lines (D49 made it 39 px / 608 px, D50 now 20 px / 400 px; was 13 px / 200 px), true in every state because zones are position-only). A trap text covers the whole crossing: its zone ends at or beyond the trap's column, and the next text starts there (`HintLeadTest.eachTrapHintStaysTheZoneTextAcrossTheTrapsWholeColumn`, `TutorialLevelTest`):
  - `HINT_LADDER_ROOT` (zone x 592-912, from the room start): "Thorns rise when you come near. Jump over it".
  - `HINT_WALL_BRANCH` (x 1488-1776): "A branch falls soon after you walk under it. Run on or jump".
  - `HINT_PLATFORM_STONE` (x 2272-2560): "A shadow on the ground shows where a stone will fall. Do not stand under it".
  - `HINT_STONE_STUMP` (x 3322-3576, from the stone's pickup spot): "A thrown stone lights the stump from afar. It smokes, then blasts. Or jump it". The throw message keeps the stone's pickup spot, so the lever lesson stays the first and last word of the room; the stump text never mentions the lever.
  The lever hints keep their 0.9 s lead (zones start about 164 px before the lever's reach); zones of a room butt against each other, and the controls zone still spans the room.
- **Rules per trap:**
  - *Thorn root:* triggered at x 832 (zone 64 either side), rises 0.8 s later (1.2 s with the assist); walking straight in is a hit 2.6 s after the entrance and one very cheap retry; a running jump from 2 to 66 px before the root clears it (measured); the root stays up for the rest of the attempt but is 200+ px from the ladder, so a hero who drops off the ledge to the left, holding Left the whole way and then jumping, never meets it (tested). The hero never needs to go back past it.
  - *Falling branch:* walking on is safe from every start inside the zone, and a running jump from anywhere in the zone is too; standing still in the column is a hit; stepping back out of the column is safe. The landed branch is harmless scenery.
  - *Falling stone:* the shadow shows during the 0.8 s warning; walking on is safe from every start, and so is a running jump; waiting under it is a hit. The stone starts at the room top (under the D38 slab).
  - *Stump:* touch lights it (walking on through it is safe, as in the traps room); a thrown stone lights it from 2 to 100 px before it (hits from beyond the 36 px blast reach exist), costs no ammunition and is **not consumed** (D47): it drops where it hit, the hero waits out the 0.8 s fuse and picks it up again, still the stone that works the lever (so no second stone is needed; a stone that falls into the chasm still comes home, D39). The bypass is the D47 floor bypass: a jump from 4 to 70 px before the stump clears it without lighting it, with no ammunition at all (30 of 34 sampled take-offs). The blast does not reach the entrance, the stone's home, the lip, the throwing spot or the exit.
- **Retry:** every trap is rebuilt by a retry, Restart Level and room entry. A retry on the first visit makes the stump whole again.
- **Tests:** `LadderLessonTest`, `WallLessonTest`, `PlatformLessonTest`, `StoneLessonTest` (new per-trap tests: placement, telegraph, scripted walkers and jumpers at every offset, one cheap retry and what it restores, soft-lock, zero ammunition, the held-Left drop from the ladder top, no leak into other rooms), `HintLeadTest` (trap leads, lever leads, trap text across the column), `TutorialLevelTest` (census, room widths, zone butting, leads, text past the column), `RoomCeilingTest` (the stone starts under the slab), `LocalizationLayoutTest`, `FallingTrapsTest`/`ExplosiveTrapsTest` (shipped-level guards updated: `forest-0` has exactly one branch, one stone and two stumps, `forest-1` and `forest-3` (now `forest-2`) none). The old geometry sweeps (hint coverage, label overlap, lesson geometry) run with the room's trap removed so teleported standing heroes are not killed; the trap cases are separate tests, and the route tests (taught route, retry, restart, soft locks) run with the trap in place and jump or walk past it.
- **Foliage over the branch (#421, 2026-11-17):** the branch no longer hangs in open sky. A `Decor:trunk` (id 102, x 1736) stands on the floor behind the branch's knob, and a new decor kind `crown` (id 103, x 1696, 96×40, `art/objects/decor/crown.grid`) hangs from the room top over the trunk's cap, as in the original screen (S7: trunk, canopy, branch at mid height). The branch, its zone, its timing and its tests are unchanged; the cracked joint (#361) lies right of the trunk, against the sky. The crown is a hanging kind (`hangs` in `Decor.KINDS`): `check_tiled_links.py` holds its top edge to the room's top or a solid's underside, the HangingVine rule, instead of standing it on a solid. Decor tier over forest-0's map `background` `4a92b8` (D92) and its tile layer, measured after #455: crown outline 0.087, body 0.119 (ceiling 0.20, body 0.08 to 0.20); the new trunk placement outline 0.121, body 0.119 (by day; over the night `2c3f66` with the D92 night palettes: crown 0.111 / 0.180, trunk 0.095 / 0.160); the branch stays at 0.463 (resting) and 0.429 (rustle), and the joint's own edges at 0.416 with or without the tree behind it.
- **Not decided or not verified:** how the rooms look and feel in the running game (the stone starts flush with the room top), the novice reading of the hints, the translations (de, pl, uk, be are drafts like the other hints), the gamepad and touch feel of the jumps over the root and the stump, and whether the four longer rooms (+144 to +208 px) still feel short to the author, who asked for short rooms: the trap, its envelope and two hint leads are the cost.

## D49. Hint font sizes: zone message 3x, label above the hero 1.5x

- **Status:** Superseded by D50 (sizes, widths, `HINT_TRAP_PLATFORM` wording and the 8 px bound are back or changed there). Implemented by [#249](https://github.com/jlogicgames/edgard_in_kimeria/issues/249), 2026-11-06, from the author's feedback after playing `forest-0` ("the general hint is too small: 3 times larger; the contextual hint above the player 1.5 times larger"). Automated tests only; not seen in the running game.
- **Sizes (logical px, the 640x360 view):** the zone message (D33, the map-authored `Hint` text) was 13 px / 200 px wide and is now **39 px (3x) / 608 px wide** (the menu width: the 640 px screen minus the 2 x 8 px `HintLayout.SIDE_MARGIN`... laid out in 608 like every other screen-wide text), a new baked font size. The contextual label over the hero (pickup, lever, ladder, controls, fireball-full) was 13 px / 200 px and is now **20 px / 300 px wide** (13 x 1.5 = 19.5, rounded to the existing 20 px size, so no extra font; the width scales with it). All other 13 px text (FPS, menu hint, touch-button labels, 62 px labels) is unchanged. `Hud.ZONE_FONT_SIZE`, `CONTEXT_FONT_SIZE`, `ZONE_WIDTH`, `CONTEXT_WIDTH` are the single source; `FontCharacters.SIZES` gained 39 and `assets/fonts/generated/QuestSquare-39.{fnt,png}` was baked with `./gradlew lwjgl3:bakeFonts` (the other four sizes came out byte-identical).
- **Unchanged on purpose:** the zone anchor (45% down the zone, `Player.HINT_ZONE_HEIGHT_FRACTION`), the 0.35 s dwell, the 2.5 s linger and the fade/slide (`HintTransition`), the 24 px `HintLayout.GAP`, the top/bottom/side margins. `HintLayout.contextTop` has an overload taking each label's own cap height, because the two channels now use different fonts.
- **Texts:** at 39 px / 608 px every hint of all five languages wraps to at most two lines except `HINT_TRAP_PLATFORM` in Ukrainian and Belarusian (3 lines). Those two were shortened ("...Падіння в яму лише повторює спробу" became "...Яма лише повторює спробу", the Belarusian likewise); the other languages and all other keys needed no change. The English/German/Polish wording of that hint is untouched.
- **Tests:** `LocalizationLayoutTest` now measures the zone messages in the real 39 px font at 608 px and the fireball-full label in the 20 px font at 300 px, asserting a **line count of at most two** (`GlyphLayout.runs`) instead of the old pixel threshold (40 px, which at 13 px also let a third line through); the widest-contextual-stack test uses the two real fonts and the 24 px gap rule unchanged. `HintGeometry` (used by the lesson tests) measures with the same two fonts.
- **One bound relaxed, with the measurement:** `LadderLessonTest.messageAndLiveLabelsNeverOverlap...` allowed the lowered label to touch the top of a hero's hitbox by 8 px when the hero is not standing on the floor (D33's one unavoided case). With the taller message the measured worst case is **10 px**, on the ledge (hero y 160, x 1220-1224); the bound is now 12 px. Standing on the floor is still 0 (no overlap), and the 24 px message-to-label gap is unchanged in every case.
- **Not verified:** how the 39 px text reads in the running game (the 3x pixel font on a 640x360 view, the two-line message next to the HUD and the pit/ceiling in each room), the touch layout, and the web build at runtime.

## D50. Hint fonts halved after D49, label raised above the hero

- **Status:** Implemented by [#254](https://github.com/jlogicgames/edgard_in_kimeria/issues/254), 2026-11-06, from the author's feedback after playing D49 (the uk zone message ran across nearly the whole screen; the label was drawn on top of the hero). Automated tests only; not seen in the running game.
- **Sizes (logical px, 640x360 view):** zone message 39 -> **20 px** ("50% smaller", the existing baked 20), width 608 -> **400 px** (inside the view with 120 px either side; two lines in all five languages, tested). Label above the hero 20 -> **13 px**, width 300 -> **200 px**. The author asked for 50% of 20 = 10 px, but no 10 px size is baked and a 10 px QuestSquare is below the pixel font's readable grid, so the smallest existing size (13, the pre-D49 size) was taken instead: **this is 65%, not 50%, of the D49 size**. Say so if a real 10 px is wanted (it needs a new baked size). `FontCharacters.SIZES` loses 39 (unused); `QuestSquare-39.{fnt,png}` removed. `Hud.ZONE_FONT_SIZE`, `CONTEXT_FONT_SIZE`, `ZONE_WIDTH`, `CONTEXT_WIDTH` remain the single source.
- **Label raised:** the hero-anchored label (controls, ladder, carried item, lever beside the hero) was anchored 12 px below the sprite's top edge and grew downward over the hero. `Player.HINT_PLAYER_OFFSET_Y` was **-24** (24 px above the sprite top, the label 36 px up) and the author later tuned it to **-6** (18 px higher than the original +12, 18 px lower than -24, so the label sits closer to the hero and is not detached from it; author-edited 2026-11-06); its bottom edge (anchor minus one cap height, about 10 px) is then about 14 px above the sprite top, clearing the head, the raised sword and a climbing hero. Targets lower than the hero (`HINT_TARGET_OFFSET_Y` 24) still anchor to the nearer of the two, so a lever's label follows the hero's new height. The 24 px gap to the zone message may still push the label lower when a message is present near the screen top (D33's unavoided case).
- **Unchanged:** zone anchor 45%, 0.35 s dwell, 2.5 s linger, `HintLayout.GAP` 24, all margins, `HintLayout`.
- **Texts:** uk and be `HINT_TRAP_PLATFORM` back to the full pre-D49 wording (two lines at 20 px / 400 px); no other text changed.
- **Tolerance:** `LadderLessonTest` label-over-hero bound for an airborne/climbing hero is back to **8 px** (D49 had relaxed it to 12); it passes. A hero on the floor is still 0.
- **Not verified:** how it looks and reads in the running game (13 px label and 20 px zone text on 640x360, label clearance on ladder and with sword raised, the touch layout), web build at runtime.

## D51. forest-3 renamed forest-2; the bomb stays a separate trap

- **Status:** Implemented by [#262](https://github.com/jlogicgames/edgard_in_kimeria/issues/262), author decisions of 2026-11-06. Automated tests only; the renamed level was not played by hand.
- **Rename:** the boss level `forest-3` is now `forest-2`, so the Forest order is `forest-0` (tutorial) → `forest-1` → `forest-2` (boss). This closes the open question "`forest-2`?" of D32: there is no extra level between them. `assets/tiles/forest-3.tmx` became `assets/tiles/forest-2.tmx` (`git mv`; the map content has no level name inside), and `KimeriaGame.LEVEL_NAMES` is `forest-0`, `forest-1`, `forest-2`. Tests, the web release check (`tools/check_web_release_artifact.py` and its test) and the docs follow. Dated records above keep the old name and say "(now `forest-2`)" where they quote it; `forest-3` in this entry and in such records is intentional history.
- **Saves are unaffected:** a save is only the zero-based next-level index plus the `level-order` key (D33); it stores no level name, and the list keeps its length and order (index 2 was and is the boss level). So existing saves and Continue work unchanged, the pre-`forest-0` migration (index 1 = the boss level) is unchanged, and `SavedProgressTest` and `CampaignFlowTest` pass. Artifact and coin keys are in-memory only (nothing persists them), so no renamed key can be lost.
- **Bomb:** author decision of 2026-11-06: the bomb is **not replaced** by the exploding stump (D42 said "redraw or replace"; D47 swapped the tutorial one and left the old `Bomb` in the boss level). `Bomb` stays its own trap type, to be redrawn and to explode with the shader (the redrawn art is [#263](https://github.com/jlogicgames/edgard_in_kimeria/issues/263)). D47's statement that the class, the object type, `BombExplosionEffect` and the shader stay is therefore the final plan, not a stopgap, and the one `Bomb` in `forest-2.tmx` (id 54) stays.
- **Not verified:** the renamed level in the running game (desktop and web), and the bomb art (redrawn later, see D60).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the forest `Bomb` becomes a leg-hold trap and the bomb moves to the Fortress later. **Done by [D128](#d128-the-leg-hold-trap-replaces-the-forest-bomb-561-2026-11-22) ([#561](https://github.com/jlogicgames/edgard_in_kimeria/issues/561)):** the one forest `Bomb` (id 54) became a leg-hold trap; the `Bomb` class, art, shader and tests stay for the Fortress.

## D52. Touch pause button at the top centre

- **Frozen (D61):** Implemented and frozen; the mobile version is cancelled (D61), maintenance only. Any "not verified" or "still open" line below is cancelled (D61), including its tracking in #277.
- **Status:** Implemented by [#259](https://github.com/jlogicgames/edgard_in_kimeria/issues/259), 2026-11-06, after the author found no way to open the menu on a phone. Automated tests (`TouchPauseButtonTest`). Author checked on a real touch device on 2026-11-07: the button works (the author's one-line report; device, browser and which screens were not recorded).
- **What:** `TouchControls` draws a round pause button (`assets/images/HUD/PauseButton.png`, the joystick disc with two white bars; drawn like the other buttons, at 55% opacity, 90% pressed) at logical (320, 19), drawn radius 13, **hit radius 26 (52 px across)**. A finger that *starts* on it is the touch twin of Esc / gamepad Start: `Player` (play, death, victory), the room-fade branch and `updateHealer` all read `TouchControls.pausePressed()`, an edge-triggered flag. Healer dialogue also shows the button (`update(active, pausable)`; the stick and buttons stay play-only).
- **Why there:** the top centre is the one free spot in play. Left: coin and artifact HUD (ends near x 200). Right: fireball stock (starts at x about 526, further left only while the debug FPS counter shows). Below: boss label from y 32. Stick (left bottom) and action buttons (right bottom) are far away, hints stay inside y 52..320, and in healer dialogue the portraits (y 32..120) and choices (y 252+) are clear.
- **Left-handed (D9):** the centre is its own mirror image, so the button does not move; it is placed through the same mirror helper as the other controls, and a test checks both modes and that no other control's centre pauses.
- **Pause menu by touch:** unchanged; its buttons already respond to `justTouched` and the touch position.
- **Portrait:** the game is a fixed 640x360 `FitViewport`, so portrait is a letterboxed small view and the button shrinks with it (about 52 px logical, smaller than a thumb on a narrow phone). Portrait got its own layout in D53 (#226): the picture stays at the top and the button keeps its place in it, with a larger hit circle.
- **Not verified in detail:** beyond the author's "works" report, the healer-dialogue case, both handedness layouts, portrait, thumb reach and accidental taps while moving were not recorded as checked. Still open as itemised cases in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277) (release acceptance: [#144](https://github.com/jlogicgames/edgard_in_kimeria/issues/144)).

## D53. Mobile browser: portrait strip, rotate hint, fullscreen, wake lock, home-screen app

- **Frozen (D61):** Implemented and frozen; the mobile version is cancelled (D61), maintenance only. Any "not verified" or "still open" line below is cancelled (D61), including its tracking in #277.
- **Status:** Implemented for [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226), 2026-11-07. Automated tests plus a headless-browser look at the release build in a 390x844 phone emulation (Android user agent): picture on top, controls in the strip, pause button and hint visible. **Not verified on a real iPhone or Android phone** when written (see "Not verified"). *Update 2026-11-07:* the author then checked a real iPhone in Safari (see "Update" below); #226 is closed.
- **Portrait layout (`ui.TouchLayout`, `ui.PlayfieldViewport`, `TouchControls.setLayout`):** with a touch screen and a window whose strip below the width-fitted 640x360 picture is at least 190 units tall (a 390 px phone: 625 px of strip), the picture is pinned to the **top** of the window and the stick and the Jump / Ranged / Action buttons move into the **strip below**, in their own 360-unit-wide space (about 1.08 px per unit, so the 44-unit stick and 32-unit buttons are thumb-sized; at the picture's 0.61 px per unit they would not be). Nothing is drawn over the playfield. Left-handed mode (D9) mirrors the strip layout. Otherwise (landscape phone, 4:3 tablet window, no touch screen) the layout is unchanged: full fit, controls over the picture. The strip is cleared to a dark colour, the scene colour only behind the picture (scissor).
- **Why top-aligned, not centred:** the free space is then one block under the picture where both thumbs rest, instead of two bars. Cost: the picture sits against the top edge, and the pause button (top centre of the picture) is a reach for a thumb.
- **Pause button (D52):** stays in the picture at logical (320, 19) in every layout. In portrait its hit circle grows from radius 26 to **40 units** (about 48 px across on a 390 px phone; at 26 it was about 32 px). Drawn size is unchanged.
- **Rotate hint:** a one-line `ROTATE_HINT` (en, de, pl, uk, be) is drawn at the top of the strip in portrait, in menus too. It is text only; the game does not lock or force orientation (browsers cannot lock it outside fullscreen / installed mode anyway).
- **Fullscreen:** `DisplayControl` (core) is implemented by the web launcher with the browser Fullscreen API on the whole page, and adds the same "Display: Fullscreen / Windowed" Options item the desktop build has, **only where `document.fullscreenEnabled` is true** (Android Chrome, desktop browsers). **iPhone Safari has no page Fullscreen API** (it exists on iPad), so the item is not offered there: iOS cannot be made fullscreen from a page. The only iOS route is Add to Home Screen (next point).
- **Home-screen app:** the release `index.html` gets `apple-mobile-web-app-capable`, `mobile-web-app-capable`, status bar style `black` (not `black-translucent`, which would draw the picture under the notch), title, theme colour, `manifest.webmanifest` (`display: fullscreen`, falling back to `standalone`), a 180 px `apple-touch-icon.png` and a 512 px icon. The icons are the 32 px favicon enlarged with nearest-neighbour on a dark square (an author decision whether to commission real icon art). Launched from the home screen the page has no Safari toolbars, which is the closest thing to fullscreen on an iPhone. `tools/check_web_release_artifact.py` now requires these files and tags.
- **Landscape fit:** the generated page used `height: 100vh`, which on iOS Safari is the *large* viewport (under the toolbars), so the bottom of the canvas was cut off. The release build now adds `height: 100dvh` (and the launcher injects the same at runtime for the dev server), blocks page scroll / rubber-band / pinch-zoom (`maximum-scale=1, user-scalable=no`, `touch-action: none`, `overscroll-behavior: none`), and re-sends a `resize` after `orientationchange` and visual-viewport changes. **In a normal Safari tab the browser toolbars still take space and cannot be hidden by a page**: landscape in Safari shows the whole picture, but smaller. Only the home-screen app removes the toolbars.
- **Screen wake lock:** the launcher requests `navigator.wakeLock.request('screen')` on the first touch / click / key, re-requests it on `visibilitychange` (the browser drops it whenever the page is hidden), and ignores failure. It is held while the page is visible, **including menus and pause**, not only during play: simpler, and a paused phone is usually about to be picked up again; the cost is that a forgotten open tab keeps the screen on until it is closed or hidden. Unsupported browsers (iOS before 16.4 in Safari, older Chrome) keep auto-locking as before. The auto-lock timeout the author saw was not measured.
- **Touch detection:** the web launcher now also treats `'ontouchstart' in window` as touch, not only `navigator.maxTouchPoints > 0` (the latter read 0 in one emulation; real iPhones report > 0).
- **Tests:** `PortraitTouchLayoutTest` (layout decision for phone, landscape, no touch, tablets; strip maps pixels to units; every control inside the strip with its grab radius; stick, Jump, Ranged and Action driven from the strip in both handedness modes; touches on the picture never drive them; pause works in the picture and has the wider portrait target; landscape unchanged), `TouchPauseButtonTest` unchanged and green, `test_web_release_artifact.py` for the mobile metadata. Localization validation covers the new key and its glyphs (the uk text avoids the em dash, which the baked fonts lack).
- **Update, 2026-11-07 (author, real iPhone, Safari tab):** portrait and landscape layouts are OK and the screen stays on during play, so the **wake lock is confirmed**. **Fullscreen and the Add to Home Screen app were dropped as not needed (author decision):** the code stays (`DisplayControl`, the manifest, the home-screen metadata, the Options "Display" item where the Fullscreen API exists), but nothing is required of it and it is not on the acceptance list. Remaining checks (Android, tablets) are in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277).
- **Not verified when written (history):** a real iPhone (Safari tab and home-screen app), a real Android phone (fullscreen item, wake lock), thumb comfort of the strip layout and the 48 px pause target, whether the home-screen status bar and safe-area insets look right, the actual auto-lock timeout, orientation changes on a device (only a resized desktop window was tried). Then tracked by #226, now by [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277) and [#144](https://github.com/jlogicgames/edgard_in_kimeria/issues/144).

## D54. Landscape: stick and buttons in the side bars beside the picture

- **Frozen (D61):** Implemented and frozen; the mobile version is cancelled (D61), maintenance only. Any "not verified" or "still open" line below is cancelled (D61), including its tracking in #277.
- **Superseded in part by [D56](#d56-touch-landscape-the-controls-never-cover-the-picture):** the 24-unit minimum bar and the "16:9 / 4:3 keep the controls over the picture" rule below no longer apply; every touch landscape window now gets bars of at least 104 units.

- **Status:** Implemented as a follow-up to D53 / [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226), 2026-11-07, after the author's iPhone test (landscape in a Safari tab: the stick and buttons covered the picture although empty bars were left and right of it). Automated tests only when written; **not verified on a real phone** then (the bars are 30 to 140 units wide on real phones, see below; thumb comfort of the column is a guess). Superseded by D56. *Update 2026-11-07:* the author found the iPhone landscape layout OK after D56; Android and tablets remain in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277).
- **Decision (`ui.TouchLayout.sideBars()`, `TouchControls`):** a touch window that is not portrait and is wider than 16:9, with each bar at least **24 picture units** wide (bar = (window width / (height / 360) - 640) / 2), uses the side layout. Controls space = the whole window at the picture's scale (X from the window's left edge, 360 units high, picture at X offset `sideBar()`). The **stick sits in the left bar, the three buttons in a single column in the right bar** (Action y 108, Ranged y 190, Jump y 272; stick y 270), mirrored in left-handed mode (D9). Each is centred in its bar when the bar is wide enough, otherwise as far out as fits (stick 52 units from the window edge, buttons 46.4 units), so the overlap with the picture is the smallest possible. The pause button (D52) is untouched: top centre of the picture, landscape hit radius 26, drawn in the picture.
- **Measured bar widths (picture units):** iPhone landscape 844x390 without toolbars 69.5; 19.5:9 with Safari toolbars (844x330) 140; 844x300 about 186. A stick needs 104 (radius 44 plus margin on both sides), so on the full-height window it still overlaps the picture by about 27 units (translucent), the button column by about 9; with toolbars both fit completely. A window of exactly 16:9, a 4:3 tablet and a desktop (no touch) have no bars and keep the old layout (controls over the picture, unchanged); portrait keeps the D53 strip.
- **Why a column:** the old triangle is 134 units wide, which would overlap the picture by about 90 units in a 69-unit bar; a column is 64 wide. Cost: the buttons are no longer in the triangle the desktop touch layout uses, so muscle memory from the over-picture layout does not carry over.
- **Hit areas:** stick grab radius is 60 units (was 76) in this layout so it reaches less into the picture; button hit radius unchanged (38.4). A finger that starts on the picture never drives a control (tested over the whole picture for bars wide enough; in the narrow case only the overlapping edge is shared by design).
- **Rendering:** the bars are cleared to the same dark colour as the portrait strip with the scene colour scissored to the picture (before, the bars showed sky colour and looked like part of the game); the controls are drawn in their own viewport covering the whole window, then the game view is restored.
- **Test change, stated plainly:** `PortraitTouchLayoutTest.theLandscapeLayoutIsUnchangedWithoutAPortraitLayout` used an 844x390 window as its "controls over the picture" case; that window now has side bars, so the test uses a 1024x768 4:3 window instead (same assertion: stick at 80 units over the picture). No tolerance was changed.
- **Tests:** `SideBarTouchLayoutTest` (layout decision for iPhone 14/15/Pro Max landscape, with and without toolbars, 16:9, 4:3, no touch, portrait; space mapping; controls fully inside wide bars and as far out as fits in narrow ones; stick and all buttons driven in both handedness modes on four windows; picture touches never drive them; pause still works and a bar touch does not pause).
- **Not verified:** a real iPhone or Android phone; whether a 70-unit bar is wide enough for the stick to feel right (alternative: shrink the controls a little in narrow bars); the iPad / Android tablets with other aspect ratios; Safari toolbar show/hide changing the window size mid-play (the layout follows `resize`, not tested on a device).


## D55. Touch controls: new light, high-contrast art (refines D53 / D54)

- **Frozen (D61):** Implemented and frozen; the mobile version is cancelled (D61), maintenance only. Any "not verified" or "still open" line below is cancelled (D61), including its tracking in #277.
- **Status:** Implemented as a follow-up to [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226), 2026-11-07, after the author's iPhone test: with the dark strip and side bars (D53, D54) the old controls (dark grey discs at 55% alpha) were hard to see. Automated tests plus a headless-browser look at the release build (390x760, 844x390, 640x360); not verified on a real phone when written. *Update 2026-11-07:* the author accepted the light controls on a real iPhone; Android is in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277).
- **Decision:** the stick base, knob, Action/Ranged discs, Jump arrow and pause button use new art under `assets/images/HUD/touch/` (`disc`, `base`, `knob`, `jump`, `pause`, each with a `-down` pressed variant for the buttons and knob), same 64x64 / 32x32 sizes and the same drawn radii. Light cream fill, 2 px white ring inside a dark navy outline (the navy reads on sky, the white on the dark bars and ground), dark navy icons; pressed state is amber and drawn 8% smaller. Idle alpha 0.94 (was 0.55 / 0.85), pressed 1.0. The Action and Ranged buttons **keep their text labels** (they change with the situation: Sword / Use / Pick up / Turn on / Fireball / Throw / Empty and so on, in five languages; the author ruled out icons). The labels are drawn in dark navy `TouchControls.LABEL_COLOR` (13 px) instead of white because the buttons are light; the portrait rotate hint stays white on the dark strip. The old `Joystick.png`, `JumpButton.png`, `Knob.png`, `PauseButton.png` are untouched and no longer loaded by `TouchControls` (no other user in the code). They were deleted on 2026-11-20 with the unused `Tileset/Village_tileset.png` (art review of #236).
- **Source and tooling:** hand-authored pixel grids in `art/hud/touch/*.grid` (left half, mirrored; same format as the forest traps), exported by `tools/touch_control_sprites.py` into the PNGs and the contact sheet `docs/references/touch-controls-art.png`. The grid text was first laid out by a one-off script, then treated as the art; edit the grids, not the PNGs. `validateSpriteArt` now also checks these nine PNGs (native size, indexed, at most 16 colours, binary alpha) via `sprite_layouts.TOUCH_CONTROL_SIZES`.
- **Label fit (test change, stated plainly):** the new art has about 5 px of dark outline and white ring on every side, so the cream area is about 51 px wide on the Action button and about 43 px on the smaller Ranged button. Measured at 13 px (all five languages): the widest one-line labels are be `NO_ACTION` "Няма дзеяння" 59 px, de `NO_ACTION` "Keine Aktion" 53 px, de "Einschalten" / "Ausschalten" 48 px; Ranged labels are at most 40 px. Labels now wrap at `ACTION_LABEL_WIDTH` 50 px / `RANGED_LABEL_WIDTH` 40.4 px (diameter minus 14, was minus 2) and may take two lines. `LocalizationLayoutTest` previously required every touch label on a single line within 62 px (Action) / 52.4 px (Ranged); it now requires every word to fit 50 / 40.4 px, at most two lines and height at most 32 (the old height limit is kept), and also covers `NO_ACTION`, which was missing. That is a stricter width and a looser line count, both measured above; no label is wider than the cream area.
- **Layouts:** unchanged (positions, hit areas, D52 to D54). Over-picture layouts (16:9, 4:3, narrow bars) use the same art; the dark outline and white ring keep it readable on sky and ground, at the cost of covering the picture at 94% instead of seeing through it at 55%.
- **Tests:** `TouchControlArtTest` measures the shipped PNGs and the label colour with WCAG luminance (fill vs dark bar at least 7:1, label colour vs idle and pressed fill at least 7:1, knob vs base at least 3:1, outline or ring at least 3:1 against bar, sky and ground, opaque centres, pressed differs from idle, alpha at least 0.9). Existing touch layout tests pass unchanged; no threshold was changed.
- **Previews:** `docs/references/touch-controls-art.png` (all sprites on bar, sky and ground), `touch-controls-portrait.png` (390x760), `touch-controls-landscape-bars.png` (844x390), `touch-controls-landscape-640x360.png` (16:9 touch window; before D56 this showed the controls over the picture).
- **Not verified when written:** a real iPhone / Android phone (sunlight glare, finger occlusion, whether the cream is too bright at night); the author's taste on the palette (cream / amber / navy) and the dark label text. The author accepted the look on an iPhone on 2026-11-07; Android, daylight and dark-room checks remain in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277).

## D56. Touch landscape: the controls never cover the picture

- **Frozen (D61):** Implemented and frozen; the mobile version is cancelled (D61), maintenance only. Any "not verified" or "still open" line below is cancelled (D61), including its tracking in #277.
- **Status:** Implemented as a follow-up to D54 / D55 / [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226), 2026-11-07, after the author's test in a ~16:9 touch window (634x360 picture, no bars by D54, so the stick and buttons covered the game). Automated tests plus a headless-browser look at the release build (640x360, 844x390, 1280x720, 390x760); not verified on a real phone when written. *Update 2026-11-07:* the author found the landscape layout OK on a real iPhone (Safari); tablets and Android remain in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277).
- **Decision (`TouchLayout`, `PlayfieldViewport`, `TouchControls`, `KimeriaGame`):** on a touch window that is not portrait the stick and buttons are *always* in side bars, never over the picture. Each bar is at least `REQUIRED_BAR` = **104 picture units** (the stick needs 2 x (radius 44 + margin 8) = 104, the button column 2 x (hit radius 38.4 + 8) = 93, so the larger one rules and both bars are equal). The picture is drawn at `min(height / 360, width / (640 + 2 x 104))` pixels per unit (`PlayfieldViewport.setMaxScale`) and centred both ways; a wider window simply gets wider bars (D54 behaviour, e.g. 844x330 is unchanged at 140.4 units); a taller window gets dark bands above and below. The controls space is the whole window at the picture's scale with the picture at offset (`sideBar`, `sideTop`); the stick and the buttons are centred in their bars (52 units from the window edge when the bar is 104). Desktop without touch and the portrait strip (D53) are unchanged.
- **Measured picture scale (pixels per unit; share of the full fit):** iPhone landscape 844x390 without toolbars 0.995 (91.9%, picture 637x358 px instead of 693x390, bars 104); 844x330 with toolbars 0.917 (100%, bars 140); 844x300 0.833 (100%); 932x430 1.099 (100%); 640x360 0.755 (75.5%, picture 483x272); 1280x720 1.509 (75.5%); 1024x768 tablet 1.208 (75.5%); 1366x1024 1.611 (75.5%); 500x480 0.590 (75.5%).
- **Floor:** 75.5% (= 640 / 848) is the worst case for any window narrower than or equal to 848:360, and wider windows are better, so the picture never drops below 75.5% of what it was; the author's suggested 60% floor is never reached and no smaller-bar fallback exists. If a smaller bar is wanted on tiny screens, `REQUIRED_BAR` is the one number to change (the stick would then overlap the picture).
- **Hit areas:** the stick grab circle is `min(60, bar - stick centre)` = 52 units at the minimum bar, so it ends exactly at the picture's edge; a finger that starts on the picture never drives a control (tested on a grid of picture points, both handedness modes, 5 windows). The pause button (D52) stays at the top centre of the picture; because the picture can be drawn at 0.755 px per unit its hit radius is `max(26, 24 px / scale)` units, i.e. never under 48 px across (31.8 units at 640x360).
- **Test changes, stated plainly:** `SideBarTouchLayoutTest`: the test that required no bars on 16:9 / 4:3 / 680x360 windows and a 24-unit minimum is replaced by one requiring bars of at least 104 units, a picture of at least 75.5% of the full fit and the measured scales above for 13 window sizes; the "narrow bar, controls as far out as fits" test (the stick overlapped the picture by about 27 units) is replaced by an "every control completely inside its bar on 7 windows" test and a minimum-bar geometry test; the old stick-grab allowance of one margin into the picture (`nearEdge`) is gone (a touch on any picture point must now drive nothing); controls-space Y now carries `sideTop`; the pause test adds 640x360 and 1280x720 and a new test pins the 48 px pause target. `PortraitTouchLayoutTest.theLandscapeLayoutIsUnchangedWithoutAPortraitLayout` used a 4:3 window for "controls over the picture"; that window now has bars, so the test (renamed `theOverPictureLayoutIsUnchangedWithoutATouchLayout`) checks the same classic layout by setting no `TouchLayout` on `TouchControls`; that layout is now reachable only that way. `PlayfieldViewport.setMaxScale` itself is not unit-tested (it needs a GL context); it was checked in the browser previews.
- **Previews (`docs/references/`):** `touch-controls-landscape-640x360.png`, `touch-controls-landscape-bars.png` (844x390), `touch-controls-landscape-1280x720.png`, `touch-controls-portrait.png`.
- **Not verified when written (iPhone landscape later found OK by the author, 2026-11-07; the rest is in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277)):** a real phone (is a picture 8% smaller on a full-height iPhone acceptable? is a thumb comfortable 52 units from the edge?); iPad / Android tablet; touch laptops (a Windows touch screen also gets the side layout because the peripheral reports touch).

## D57. Web canvas backed with physical pixels (refines D53 / D56)

- **Frozen (D61):** Implemented and frozen; the mobile version is cancelled (D61), maintenance only. Any "not verified" or "still open" line below is cancelled (D61), including its tracking in #277.
- **Status:** Implemented as a follow-up to [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226), 2026-11-07, after the author's iPhone test of D55/D56 ("text completely unreadable, graphics squeezed, pixel artifacts"). Automated tests plus a phone-emulated headless browser (devicePixelRatio 3, touch) on the release build before and after; not verified on a real phone when written. *Update 2026-11-07:* the author confirmed on a real iPhone (Safari) that text and pixel art are sharp; the frame rate is still not measured on a device.
- **Root cause (measured, not guessed):** `WebLauncher` left `WebApplicationConfiguration.usePhysicalPixels` at its default `false`, so the gdx-teavm backend sized the canvas backing store in *CSS* pixels (`canvas.width = window.innerWidth`). On an iPhone (`devicePixelRatio` 3) a 390x844 CSS window had a 390x844 backing store that the browser then stretched 3x with its own smoothing. Probe at DPR 3 on the pre-fix release build: canvas 390x844 / 844x390 / 844x330 for CSS boxes of the same size. On top of that D53/D56 draw the 640x360 picture at 0.61 px per unit in portrait and 0.92 to 0.99 in landscape, so the baked bitmap fonts (13 to 30 px) and the sprites were first *minified* to well under 1 backbuffer pixel per source pixel (dropped rows and columns, which is what made letters unreadable) and then magnified 3x by the browser. Texture filters were not the cause: sprites and the baked font pages are already `Nearest` (`Assets`); only the portraits and HUD icons use `Linear`, and that is right for them.
- **Decision:** `config.usePhysicalPixels = true` in `WebLauncher`. The backend now sets `canvas.width = innerWidth x devicePixelRatio` (CSS size unchanged), `Gdx.graphics.getWidth()/getHeight()` and the back buffer are the same physical size, input is scaled by the backend to canvas pixels, so `resize`, the viewports, the D53/D56 layout maths (all in picture units), `HdpiUtils.glScissor` and the scene buffer (`ensureSceneBuffer`, ratio 1) stay correct unchanged. The picture is now drawn at **1.83 px per unit in portrait** (was 0.61) and **2.99 at 844x390, 2.75 at 844x330** (was 0.99 / 0.92), always with `Nearest` filtering. Desktop (LWJGL) is untouched; a desktop browser with `devicePixelRatio` 1 behaves as before, with ratio 2 it is now sharp instead of blurry.
- **Only pixel constant that needed a fix:** the pause button's minimum hit radius (D56, 24 px) is now 24 *CSS* px: `TouchLayout.pixelRatio()` (set from the new `DisplayControl.pixelRatio()`, default 1, `devicePixelRatio` on the web) multiplies it (`TouchControls.pauseHitRadius`). Everything else is in picture units.
- **Cost:** no cap on the ratio. Backbuffer pixels, before then after: iPhone portrait 390x844 0.33 M to 1170x2532 **2.96 M**; landscape 844x390 0.33 M to **2.96 M**; 844x330 0.28 M to 2.51 M; a 2x desktop at 1440x900 5.2 M. The game is a sprite batch with one picture-sized pass at most (the paused and ripple scene buffer is picture-sized), so fill rate, not geometry, is what grows (about 9x on a phone). If a low-end Android phone drops frames, the lever is to cap the ratio (a custom canvas size in `WebLauncher`), not the layout.
- **Non-integer scale:** 1.83 and 2.99 are not integers, so on-screen pixels of the art are 1 or 2 (resp. 2 or 3) device pixels wide. At 3 device pixels per CSS pixel this is not visible in the previews; an integer-scale offscreen buffer was considered and rejected because it adds a smoothing pass (blur) to gain evenness that is not needed at this density.
- **Previews (`docs/references/`):** `mobile-sharpness-portrait-before-after.png` (390x844 at DPR 3: menu, healer dialogue, play; left before, right after) and `mobile-sharpness-landscape-before-after.png` (844x390 at DPR 3, same screens).
- **Tests:** `SideBarTouchLayoutTest.thePauseTargetIsMeasuredInCssPixelsOnAPhysicalPixelCanvas` (a 1920x1080 window at ratio 3 hits the same CSS area as 640x360 at ratio 1) and `thePixelRatioNeverChangesTheLayoutInPictureUnitsAndIsAtLeastOne`. No test was weakened. The canvas size itself is backend behaviour and is checked in the browser, not in a unit test.
- **Not verified:** Android Chrome on a real phone; frame rate at 2.96 M pixels on a mid-range Android (the author reported no problem on an iPhone but did not measure it); tracked in [#277](https://github.com/jlogicgames/edgard_in_kimeria/issues/277). Sharpness on a real iPhone: confirmed 2026-11-07 (see Status).

## D58. forest-1 traps and a second RedMob: three traps and one enemy in the open stretch

- **Status:** Implemented by [#260](https://github.com/jlogicgames/edgard_in_kimeria/issues/260), 2026-11-09, from the placement plan the author approved in ROOMS.md ("forest-1 trap placement plan"). Automated tests only: nobody has played the stretch, and the tuft, puffball and log art has not been seen on this map in the running game (the issue stays open for that check).
- **Author's choices (2026-11-09):** three traps (not four); the full-route retry cost is accepted and `forest-1` is **not** split into rooms (still true for the trap course; D67 later appended a separate route room after it); the first trap is the **thorn tuft** (not the learned root); the **rotten log goes last**; **no exploding root** and no fireball choice in this level; and, changing the plan's recommendation, **an enemy is added in the trap stretch** so the level alternates things instead of keeping the RedMob alone.
- **Decision:** in the 620 px of empty floor between the wall (x 480-528) and the checkpoint (x 1216), one thing at a time, left to right (Y grows downward, floor top y 320): `ThornTuft` at x 696, y 304 (zone 80, trigger x 616-792; id 35); the second `RedMob` at x 864, y 288, `offNeg`/`offPos` 2 tiles (hero centre x 832-896, worst reach of body and whip x 816-912; id 38); `Puffball` at x 944, y 304 (`period` 4, `cloud` 1, `phase` 0; cloud x 940-964 with margin; id 36); a 144 px pit at x 1024-1168 with a `FallingPlatform` `skin=log` at x 1088, y 320 (id 37). The floor collision rectangle (id 3) is split (x 0-1024, new id 39 x 1168-1280), the three floor tile rows lose columns 64-72, with edge tiles at columns 63 and 73. `nextobjectid` 40. No hints (forest-1 has none, D33).
- **Enemy choice:** a second **RedMob**, the only walking enemy the player already met in forest-1 (YellowMob and Bat are not in forest-0 or forest-1). Its telegraph is the whip's raised arm, 0.4 s of wind-up before the 0.2 s strike (`RedMob`); enemy wind-ups are **not** scaled by the longer-warnings assist (D11 applies to traps, `TrapWarnings`), so the assist changes nothing here. A sword hit or a stomp defeats it, so the required route needs **no ammunition** (D26). It is a ground blocker that chases only while the hero's centre is in its small range, so it never enters the tuft zone (24+ px clear), the cloud (28 px clear) or the log, and is beyond the pit (tested: the mob's worst reach is checked against every trap area).
- **Telegraphs (D7, no hints):** the tuft bristles for 0.8 s (1.2 s with the assist) when the hero enters its zone, then stays lethal; the puffball cap swells 0.8 s (1.2 s) before a 1.0 s cloud; the log shows the shared falling-platform warning (1.0 s, 1.5 s with the assist), the same behaviour as the four platforms at x 96-224. A tuft or puffball needs no bypass rule (not explosive; the jump envelope and the cloud's calm window are the loader's checks).
- **Required route (D26):** after the lever-272 wall: jump the tuft, sword or stomp the mob, cross the cloud in the calm, jump the pit onto the log and on to the far floor, step to the checkpoint. A hero who steps back off the log while it falls finds it gone; the pit is the level `Deadline`, so it is a retry (the pit is wider than any jump and not climbable), never a soft-lock; nothing is re-armed to make the gate pass. The checkpoint stays 48 px past the pit and the spawn is far from every trap.
- **Measured (scripted hero, real input, zero ammunition, `Forest1TrapsTest` and `RoomReplayTest`):** mastered route **12.27 s** (was 11.58 s), 0.00 s waiting for the puffball, 14.98 s with the longer-warnings assist (the swell lasts 1.2 s then); budget 20 s. A death at the log replays about 12.7 s. Author check, not a novice playtest (#129-#131).
- **Test changes, stated plainly:** (1) `RoomReplayTest.singleRoomSecondLevelRequiredRouteFitsTwentySeconds`: its scripted takeoff list changed from `{125, 260, 540, 1120}` to `{125, 260, 540, 637, 993, 1089}`, because the old walker no longer passes the tuft and the pit; the 20 s limit, the zero-ammunition assertions and the three-identical-attempts check are unchanged. (2) `RhythmTrapsTest.SHIPPED_ALLOWANCE`: `forest-1` goes from nothing to `Puffball` and `skin` (the rest stay empty). No threshold or tolerance was relaxed. New: `Forest1TrapsTest`.
- **Not verified (tracked by #260):** the new sprites and the pit edge tiles on this map by eye; novice playtests; a human feel of a 12-13 s retry loop. No new text, so no translation review.
- **Revised by [D85](#d85-forest-1-opens-with-the-first-redmob-alone-in-a-room-of-its-own-375-2026-11-17) (#375, 2026-11-17):** "not split" no longer holds for the level's first stretch. The first RedMob (id 11) and the refill (id 25) left `forest-1-traps` for a new first room, `forest-1-mob`, and the lever-and-torch pair (ids 12, 13) was removed, because the room met four new elements at once (D72). The trap course described above (tuft, second RedMob, puffball, log; x 616-1280) is unchanged and still one room with the 12.73 s retry at the log.


## D59. forest-2 traps in the approach to the boss: a thorn vine, an exploding root and a falling stone

- **Status:** Implemented by [#261](https://github.com/jlogicgames/edgard_in_kimeria/issues/261), approved by the author on 2026-11-10 (plan and answers in [ROOMS.md](ROOMS.md#forest-2-trap-placement-plan-261-d59)). Automated tests only; nobody has seen the new sprites, the earth blocks that hold the vine and the stone, or the lengthened room in the running game (tracked by #261).
- **Decision:** `forest-entry` is not touched: it has no free floor (gaps of 64, 16 and 48 px between its hazards), already takes 16.27 s and holds the level's Bomb. `forest-canopy` is lengthened by **448 px (28 tiles) at x = 1808**: floor, tile layer (28 columns), the `Deadline` and everything right of the cut move or grow; the old exit 1872 became 2320 and the **boss room is only translated by +448** (Room 2560-3008, entrance 2640,304, Boss object 2912,304; nothing inside it changed). Map width 160 -> 188 tiles. Three traps stand in the new stretch, one at a time, each alone, no enemy added: T1 `ThornVine` x 1896 (anchor x 1908, y 256, floor 64 below, `period` 4.5, `amplitude` 50, `phase` 0); T2 `ExplosiveRoot` x 2064, y 296 (stands on the floor, blast square x 2040-2104); T3 `FallingStone` x 2224 from the room top (320 px drop, zone x 2208-2240, y 288-320). Both the vine and the root are placed for the first time in any level. No hints, no Bomb companion, no extra stone pickup. The Bomb (x 656, entry room) is unchanged.
- **Rules kept:** D7 (the vine's motion, the root's wind-up and the stone's growing shadow are the telegraphs), D26 (no ammunition on the route; the root's bypass is the floor jump the loader validates), D42/D47 explosive rule, D3 room retry, D44-D47 placement rules (every gap between a lethal area and the entrance, the exit and the other traps is at least 48 px: Bat reach to vine 80, vine to root envelope 48, root envelope to stone zone 64, stone zone to exit 80).
- **Measured (scripted hero, real input, zero ammunition; `Forest2TrapsTest`, `RoomReplayTest`):** `forest-canopy` mastered route **9.48 s** (was 5.00 s), the same jumping the root or walking through it and with the longer-warnings assist; collection route 9.48 s (was 5.00 s); collection returns through the canopy 9.62 s then 9.48 s (was 5.13 / 5.00 s); `forest-entry` 16.27 s and 20.00 s unchanged. The vine is reached at 4.87 s and passed with 0.8 s of slack; a blind walker started at every 0.1 s offset is killed in 15 of 45 cases. The root is passed unlit by a takeoff with the body's right edge 5 to 57 px before it (measured every pixel); walking through the lit root survives. The stone warns for 49 / 73 updates (0.8 / 1.2 s).
- **Test changes, stated plainly (no tolerance relaxed):** new `Forest2TrapsTest`; `BossCombatTest` coordinates 2112 -> 2560 and 2192,304 -> 2640,304; `RhythmTrapsTest` allowance (`ThornVine` in forest-2); `ExplosiveTrapsTest` census (one root in forest-2); `FallingTrapsTest` census (one stone, no branch in forest-2); `RoomReplayTest` unchanged. `tools/check_tiled_links.py` now applies an object group's `offsetx` / `offsety` like the game's loader (it ignored forest-2's -16 `SpawnPoints` offset); test added.
- **Deviation from the approved plan:** the vine object is at x 1896, not 1908 (1908 is its anchor), so the plan's swept-area gaps hold.
- **Agent check in the web build (Chrome, 2026-11-17, by the orchestrator, this branch before the merge of D86):** in `forest-1-route`, standing at
  the lever with Up held, the lever and the ladder glowed and the fairy flew to the right screen edge and twinkled there, toward the off-screen
  lift; at the pull the ladder dropped with the strong glow and the corner glints while she stayed at the edge; about 2 s later she was back with
  Edgard; no console errors. This is an agent's look, not the author's play-through.
- **Not verified:** the look in the running game, novice playtests, the feel of the 9.5 s retry loop, the vine's `phase` as a teaching choice (0 was kept because the mastered route passes with slack).

## D60. The Bomb gets its own redrawn sprite; the explosion shader is untouched

- **Status:** Implemented by [#263](https://github.com/jlogicgames/edgard_in_kimeria/issues/263), approved by the author on 2026-11-10. Automated checks only; nobody has seen the sprite in the running game.
- **Decision:** the glass-bottle art of `Bomb` (`images/Items.png` frames at x 32 and 48, a green and a blue potion that alternated at 10 Hz) is replaced by `trap-bomb`, a native 16x16, two-frame strip (`idle1`, `idle2`): a round iron bomb (steel greys `34314a / 5b5870 / 9a97ad`, near-black contour `0b0814`, a red collar `b3201a`, a bone fuse `efe6c4`), with a smouldering tip that pulses dull red to orange/yellow between the frames. Source: `art/objects/forest-traps/bomb.grid`; exports `art/objects/trap-bomb-pixel.png`, `assets/images/objects/trap-bomb.png` and the preview `docs/references/forest-traps/bomb-preview.png` through the existing `tools/export_object_sprites.py` pipeline (`FOREST_TRAP_SHEETS['bomb']`, `SpriteSheets.TRAP_BOMB`).
- **Kept unchanged (author rules):** `Bomb` stays a separate trap type (D51); behaviour, the 10x13 hit box inside the 16x16 frame (body rows 2..14, columns 3..12), the D7 telegraph (shared 0.8 s / 1.2 s warning with the warm tint flash drawn over the sprite), D42 (detonate from afar or bypass) and the 64x64 blast. **No new explosion:** `BombExplosionEffect` and `bomb_explosion.frag` are untouched. The code has no separate wind-up or spent frame for `Bomb` (the warning is the tint, the bomb is removed on detonation), so none was drawn.
- **Readability:** the body is mid grey with a lighter rim and a dark contour so it separates from both the dark night/trunk backgrounds and the lit HUD (preview on the three forest backgrounds in `bomb-preview.png`); the red collar and the ember tip are the warm accents the forest traps use (D42 palette).
- **Used by:** `forest-2` `forest-entry` (id 54, x 656), `room-test.tmx` and the tests that load them; all draw the same class, so one change covers every use.
- **Not verified:** the sprite at game scale in the running game (desktop and web) and on a real touch device.

## D61. No native mobile version: the release is desktop and web

- **Status:** Decided by the author on 2026-11-10 ([#266](https://github.com/jlogicgames/edgard_in_kimeria/issues/266)). Documentation only; no code changed.
- **Decision:** there will be no native mobile version: no libGDX Android or iOS module and no App Store or Google Play release. The release targets are the desktop build (LWJGL) and the web build.
- **Why:** the focus group said they are not interested in a mobile version (author, 2026-11-10).
- **What stays as it is:** the mobile-browser support already merged under [#226](https://github.com/jlogicgames/edgard_in_kimeria/issues/226) (touch controls and pause button, portrait strip, side bars in landscape, physical-pixel canvas, screen wake lock; D52 to D57) stays in the code with its tests; the author played it on an iPhone and accepted it. Desktop-only fullscreen (Options, LWJGL) and the browser Display item where the Fullscreen API exists stay as they are. Persistence uses the existing backends only: desktop preferences on disk and browser local storage.
- **What gets no further investment:** no new mobile features, no Android phone, tablet or frame-rate check campaign, no native mobile preferences backend, no touch pause or fullscreen work for a native app. Mobile browsers keep working as a side effect of the web build, but a mobile-only problem is not a release blocker unless the author says so.
- **Scope of the cancellation:** every planned, unfinished or "not verified" mobile item in the docs (real-device touch, Android, tablet and frame-rate checks, touch on native, phone builds) is marked **Cancelled (D61): will not be implemented**, with the original text kept. D52 to D57 are historical records, implemented and frozen (maintenance only).
- **What would reopen it:** only a new author decision; a request from players or testers alone does not.

## D62. Screen shake: a manager, an Assist option and the lever-wall shake (#239 step 1)

- **Status:** Implemented by [#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239) step 1; plan approved by the author on 2026-11-12 (`docs/ART_DIRECTION.md`, "Environment effects plan (#239)"). Automated checks; the look in the running game was not seen (see Not verified).
- **Decision:** `effects.EnvironmentFx` (owned by `Level`, `level.fx()`) holds `effects.ScreenShake`. `ScreenShake.start(amplitudePx, seconds)` keeps the stronger of the running and the new shake (an equal one restarts, as the boss attack always did); the offset is `amplitude * remaining/total * sin(age * 110 | 137)`, clamped to the room bounds as before. The boss shake moved out of `Level` into it with the same numbers (1.25 px, 0.18 s, frequencies 110 and 137) and the same public methods (`shakeAfterBossAttack`, `shakeAfterEmpoweredHit`, `clearCameraEffects`, `cameraShakeOffset`, still one reused `Vector2`). A lever that really moves a vertical `Wall` (`Level.activateTrigger`) starts a 1.5 px shake for that wall's move time (`Wall.moveDurationSeconds()`; forest-0's wall is authored at 1.5 s); a wall that cannot move starts none.
- **New user-visible option (D11):** Options -> Assist -> "Screen shake: On/Off" (`Settings.SCREEN_SHAKE`, key `screen-shake`, `Msg.SCREEN_SHAKE` in en, uk, be, de, pl). Off gives a zero offset for **every** shake, the boss shake included, read live. **Default:** on on desktop; on the web it is `!prefers-reduced-motion: reduce` (`WebLauncher.prefersReducedMotion()`, a `@JSBody` `matchMedia` call, passed to `KimeriaGame` next to `hasTouchScreen`). `Settings.get(option, platformDefault)` applies the platform value only while the key is absent or unreadable; a saved choice always wins, and merely reading the default saves nothing.
- **Why:** reduced-motion players and players who dislike camera movement need a way off (the author's checklist, #239); the lever shake is the first new shake, so the option and the manager land with it.
- **Hint geometry (D33, D49, D50):** `KimeriaGame.render` now puts the camera back to the unshaken position after the world is drawn and before the HUD, so `Hud.renderInteractionHint` (which places the label through `camera.project`) no longer moves with a shake. Before this change the hint anchor was projected through the shaken camera (boss shake only).
- **`GameContext.screenShakeDisabled()`:** inverted on purpose. The test proxies of `GameContext` answer `false` to every boolean, so an "enabled" question would have turned the boss shake off in `BossCombatTest` and `RoomTest`, which must pass unchanged.
- **Test changes, stated plainly (no tolerance relaxed, no existing test edited except by additions):** new `ScreenShakeTest` (includes a bit-identical comparison with a verbatim copy of the old `Level.cameraShakeOffset` over several room shapes and 14 frames), `WallLessonTest` (a moving wall starts one shake for its move time, a non-moving one none), `SettingsTest` (default, reduced-motion default, saved value wins, round trip), the new label in `LocalizationLayoutTest`, and `AllocationBudgetTest.leverShakeThroughTheRealLevel`.
- **Measured:** `camera shake query` still 0.0 B/frame; lever shake through the real `Level` 4.5 B/frame in 3 runs (ceiling 16; the 4.5 is the `HashSet` that `activateTrigger` makes once per lever pull, the shake, its update and its offset are 0.0). `Forest1TrapsTest`, `Forest2TrapsTest`, `RhythmTrapsTest`, `TutorialLevelTest`, `BossCombatTest`, `RoomTest` pass unchanged (physics untouched).
- **Not verified:** how the 1.5 px lever shake looks and feels in the running game (desktop and web); the label wording in de, pl, uk and be (machine-quality drafts, native review pending, [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369), [LOCALIZATION_VALIDATION.md](LOCALIZATION_VALIDATION.md)); `prefers-reduced-motion` in iOS Safari and Firefox (see the browser check in the pull request).


## D63. Honey fungus and false honey fungus: a pair of look-alike proximity mushrooms (#295, phase 1: decision and art)

- **Status:** Phase 1 (this record and the art) and **phase 2 built** for [#295](https://github.com/jlogicgames/edgard_in_kimeria/issues/295) (2026-11-13): the objects `HoneyFungus` and `FalseHoneyFungus` on the shared base class `FungusTrap`, the tests, the Tiled checker and the placement below ("As built"). The author's decisions of 2026-11-12 are recorded here; the **sprites and the look in the running game still await the author's eye**. The placement proposal was not objected to and is treated as accepted, with the one change forced by the measurement: the false fungus stands in `forest-canopy`, not `forest-entry` (see "As built").
- **Decisions (author, 2026-11-12):**
  - **Death:** the false honey fungus's spore cloud kills by an overlap of its rectangle with the hero's body box, as the bomb blast and the spore puffball (D46, D3). The honey fungus's burst has **no hitbox**.
  - **Trigger:** the hero entering the mushroom's zone, as for the other proximity traps (D44).
  - **Telegraph (D7):** both swell for the shared warning before they burst; the warning is the only thing the longer-warnings assist scales. It is read from `TrapWarnings` (`DEFAULT_WARNING_SECONDS`, today 0.8 s, 1.2 s with the assist), **never a literal**. The value was accepted after the first external playtest (D7 update, D66); any later change must flow through without touching these objects.
  - **Idle animation:** both play a subtle deterministic idle (level clock, no randomness, independent of the hero), so a live mushroom can be told from a static one at a distance. Static look-alike scenery is [#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299), built as the `lookalike` kind of `Decor`: a pale, ringed, motionless tuft of the same family, after the first design (a bracket fungus on a trunk) was dropped as too unlike the trap to create doubt (docs/ART_DIRECTION.md, "Static look-alike mushroom").
  - **Look:** real fungi (tufts of small caps on thin stems), not a fly agaric, not the healer's Mushroom pickup (D43), not `trap-puffball` (D46). Harmless: honey-brown caps, pale stems **with a ring**. Poisonous: sulphur-yellow to orange caps with a greenish tinge, olive gills, **no ring**. Spores: warm golden particles (harmless) against the lime cloud (lethal; the `trap-spores` palette).
  - **Objects:** two new Tiled types, `HoneyFungus` and `FalseHoneyFungus`, with one shared base class (proximity, swell, burst); not an extension of `Puffball` (a rhythmic timer trap, no proximity, no harmless variant).
  - **Burst:** the harmless one uses particles (`KimeriaParticleSystem`, an `AllocationBudgetTest` scenario, `tools/check_allocations.py`); the lethal one first used a sprite cloud and now draws `SporeCloud`, particles plus the `spore_cloud.frag` shader ([#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296), see "#296 final" below).
- **Art built in phase 1 (original drawings, grids in `art/objects/forest-traps/`, exporter `tools/forest_trap_sprites.py`, layouts in `FOREST_TRAP_SHEETS`, `SpriteSheets.TRAP_HONEY_FUNGUS` and `TRAP_FALSE_HONEY_FUNGUS`, previews in `docs/references/forest-traps/`):**
  - `trap-honey-fungus` and `trap-false-honey-fungus`: **24x16 px per frame**, six frames each in one strip of 144x16 px: `idle1`, `idle2`, `idle3` (a brightness pulse only, the silhouette is identical, so the runtime may play them as a ping-pong loop on the level clock), `swell1`, `swell2` (the caps grow and brighten: the D7 telegraph) and `spent` (deflated, darker: the state after the burst, shown for the harmless one and for the lethal one once its cloud has gone). Each uses 7 colours plus transparency (limit 16), binary alpha.
  - The two are drawn to be **near twins**: the same three-cap tuft, the same stem positions and the same bounding box on every frame. They differ in three small things: the colour (honey `e2b25a / c08a4c / 8a5a30` caps and bone `efe6c4 / a89c78` stems; against sulphur `dcdc50 / d2a62c / 8f8a26` caps and greenish stems `d4dc9c / 98a468`), the **rings** on all three stems of the honey fungus (none on the false one) and a small **bump** on the false one's big cap. The gills are `4a2e1c` (brown) and `4a4a18` (olive) respectively.
  - The footprint is 24x16: an object `y = floor - 16`, with the tuft's bottom row on the floor line (world Y grows down). The trigger zone and the cloud rectangle are phase 2 data, not art.
  - **The lethal cloud first reused `trap-spores` unchanged (superseded by #296, see below)** (24x24, `cloud1` puff, `cloud2` bloom, `cloud3` thin drift, lime `a6e04a / 5fa02a / e6f26a`): it already is the lethal-lime cloud the author asked for and the puffball uses it, so no new cloud art was drawn. If the author finds 24x24 too small for a tuft's cloud, phase 2 may scale it by an integer factor (nearest) with its hitbox equal to the drawn size; that choice is for phase 2 and the author. The golden burst needs no sprite (particles).
- **Proposed placement (as proposed in phase 1; the as-built coordinates follow it; D58 and D59 placements stay untouched):** teach by contrast, safe first.
  1. **Harmless, first sight:** `forest-0-lever` (the first room, x 0-576), on open floor at about x 150, between the spawn (64) and the first bush (200): in plain view, nothing to avoid, so the player watches a pretty burst of golden spores. No hint needed; if the author wants words, they follow the hint rules of ROOMS.md.
  2. **Harmless, repeat:** `forest-0-wall` (x 1472-2256), after the lever (1944) at about x 2060, off the route's critical path, so the shape is seen twice before it matters.
  3. **False, the first lesson:** `forest-2` room `forest-entry`, on the floor between the second YellowMob (832) and the RedMob (1136), at about x 980, where the route has to pass its zone and the swell is the only warning. Candidate if that stretch has no free floor after the mobs' reach is measured: `forest-canopy` before the D59 stretch (x 1808-2320). Nothing is proposed for `forest-1`: its trap stretch (D58) is full and stays as it is.
  - The false mushroom needs a bypass in the D26 sense (no ammunition; passable by outrunning the swell or by a jump over its zone), a calm window or a clear line, and a distance of at least 48 px from entrances, exits, checkpoints and other lethal areas (the rule of D44 to D47); the phase 2 tests prove it.
- **As built (phase 2, 2026-11-13).** Shared rules in `FungusTrap`: idle (a smooth, deterministic brightness glow of the fixed `idle1` frame on the object's own clock: sine between `GLOW_MIN` 0.55 (2026-11-13: was 0.8, a 20 % dip the author could not see on the desktop build; now about 45 %) and 1.0, `GLOW_PERIOD_SECONDS` 2.5, the batch tint is set around the draw and restored; the same for both so only the swell tells them apart; the swell, burst and spent draw at full brightness) until the hero's body box enters the zone, then swell for `TrapWarnings.duration(DEFAULT_WARNING_SECONDS)` (two swell frames; leaving the zone does not cancel it), then one burst, then spent for good (no re-arming, D26). Everything ticks in `update` only (pause freezes it); a retry rebuilds the room (D3) and re-arms both. The zone is the footprint widened by `zone` (default 64 px, or `zoneLeft` / `zoneRight`) from 32 px above it to the floor; the hero is seen only while `Player.canLeaveRoom()`.
  - **Idle revised (2026-11-13).** The first build swapped idle1, idle2, idle3, idle2 at 0.45 s a step; the author played it and found the constant blinking strange and unclear, so the idle is now the continuous glow above (brightness only, no hue shift, so colour-blind safe and the pair is still told apart by shape and colour). `idle2` and `idle3` are currently unused (the sheet is unchanged; the sheet contract tests still expect six frames). `AllocationBudgetTest` idle scenario: see the report numbers below the ceiling of 8 B/frame.
  - `HoneyFungus`: 16 golden `KIND_GLOW` spores on its own `KimeriaParticleSystem` (24 slots, seeded from its position), rising 14 to 34 px over 0.6 to 1.0 s; no hitbox, not a `LethalHazard`. `AllocationBudgetTest`: **0.0 B/frame** (3 runs, ceiling 8).
  - `FalseHoneyFungus`: a 24x24 lethal square (`CLOUD_SIZE`; first drawn by the `trap-spores` sprite, now by `SporeCloud`, #296 final below), centred on the mushroom with its bottom edge on the floor line, live for 1.0 s; lethal exactly where drawn (the hitbox is the lethal square, no inset), through `LethalHazard` and the same `Level.killOnLethalHazards` entry as the puffball and the bomb blast. `AllocationBudgetTest`: **0.0 B/frame**.
  - Authoring rules (load-time errors in `Level.validateFungus`, mirrored by `tools/check_tiled_links.py`): the thorn jump envelope (64 px of floor either side, 96 px of free height; a bypass: jump the cloud, or wait it out outside it), a zone that does not cover the entrance, zone sides of at least 64 px for the lethal one (24 px for the harmless one), and for the lethal one a cloud at least 48 px (`FungusTrap.CLEARANCE`) from the entrance, the exit and checkpoints. The distance of 48 px to other lethal areas (mobs' patrol, bat, vine) is asserted by `HoneyFungusTest` on the map.
  - Placement as built (world coordinates; footprint 24x16, `y = floor - 16 = 304`; `forest-2`'s SpawnPoints layer is offset by -16, so its Tiled `y` is 320):
    1. `forest-0-lever`: `HoneyFungus` at **x 150** (zone 40 px each side, so the zone starts at x 110 and stays off the entrance body), between the spawn (64) and the first bush (200).
    2. `forest-0-wall`: `HoneyFungus` at **x 1804** (zoneLeft 24, zoneRight 48: zone x 1780 to 1876, off the falling branch's zone, which ends at 1776). The proposed x 2060 was not usable: the wall's travel box (x 2048 to 2192) is inside the jump envelope, so the fungus stands before the lever (1944) instead of after it.
    3. `forest-2` **`forest-canopy`**, `FalseHoneyFungus` at **x 1672** (cloud x 1672 to 1696, y 296 to 320; zone x 1608 to 1760). `forest-entry` has no usable floor (`ROOMS.md`: the free gaps are 16 to 64 px between hazards, and the Bomb's blast, the YellowMob's patrol from x 752 and the bat leave less than the 120 px a cloud with its 48 px clearances needs), so the D63 fall-back applies. The stretch x 1664 to 1680 is the only one in the canopy before the D59 stretch that satisfies all of: envelope clear of the falling platform (column 1568 to 1600), 48 px from the RedMob's patrol (ends 1536), 48 px from the bat (hurts from 1752), 48 px from the vine at 1896. D58 (`forest-1`) and the D59 stretch (x 1808 and on) are untouched.
  - The mastered routes of the canopy: the first pass flies over it on the falling platform and the ledge (no change); a floor walker (the second pass, the later collection trips, the required route) takes off at hero x 1600 (body right edge 43 px before the cloud). `RoomReplayTest` and `Forest2TrapsTest` carry that jump; their budgets (20 s and 30 s) and identical-attempt checks were not changed. Measured by `HoneyFungusTest` with a scripted hero from the zone's edge: waiting beside the cloud and then walking past is safe with and without the assist, a jump over it from three take-off distances survives at every start delay tried, and a walker without a jump is killed in 14 of 21 start delays and passes in 7 (the others wait it out), so the lesson has teeth and a bypass.
  - **#296 final (2026-11-13).** The lethal cloud is `effects.SporeCloud` only: 34 lime `KIND_SMOKE` puffs and 12 `KIND_GLOW` specks over the swirling shader haze `spore_cloud.frag`, all inside the lethal square (D5: nothing drawn outside what kills). The author played it on macOS desktop and checked the shader in a browser, and approved the look. The switches `FalseHoneyFungus.PARTICLE_CLOUD` and `SporeCloud.SHADER_LAYER` and the sprite drawing path are removed. Fallback: `Shaders.tryLoad` returns null when a context cannot compile the shader and the particles alone are drawn (headless tests take this path); debug runs (`-Dkimeria.debug=true`) log once on the first burst whether the shader loaded. **Gameplay unchanged:** hitbox `FalseHoneyFungus.CLOUD_SIZE` = 24x24 (now defined by that constant, not by a sprite), centred on the mushroom with its bottom on the floor line, 1.0 s life, same telegraph and D5 fairness; no test tolerance changed. **The `trap-spores` sheet stays**: the spore puffball (D46) still draws it; only `FalseHoneyFungus` stopped using it. `AllocationBudgetTest` cloud scenario: ceiling 8 B/frame, unchanged.
- **Rules kept:** D3 (retry), D7 (telegraph, assist), D26 (no ammunition, no re-arming), D42 and D44 to D47 (placement and zone distances), D43 (the pickup's look stays distinct), D58 and D59 (placements untouched).
- **Not verified:** the sprites and bursts at game scale in the running game and by the author's eye (the headless tests prove geometry, timing and allocation, not the look); that the pair is told apart "only on a close look" at the real scale; the gap for static look-alikes ([#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299)) and the polish ([#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296)) are separate issues.
- **Static look-alike recoloured (D76, #344, 2026-11-16).** After playtest F15, readability wins over the deception: the static look-alike (#299) is in the decor tier (muted grey-beige, no ink contour, outline 0.16 against 0.36 and up for the active pair), so it no longer imitates the trap at full contrast. It keeps the tuft shape and the rings. The real-or-false game stays between the two active fungi, whose art and glow are unchanged.

## D64. Feel fixes for the #239 environment effects: landing dust, bush trigger, looping wall rumble (2026-11-13)

- **Status:** Implemented after the author played the game; automated checks only, the look and sound were not judged by a human yet.
- **Landing dust:** a smaller puff (4 particles, same dust) and the `dust_puff` sound at 0.6 volume on an airborne-to-grounded transition with a downward speed of at least `Player.LANDING_MIN_FALL_SPEED` = 140 px/s (about a 16 px fall). Ledge walk-offs, wall slides, ladder step-offs and quicksand do not puff. New `GameContext.playSound(name, volumeScale)` (default ignores the scale).
- **Bush trigger:** the brush starts when the hero's body box overlaps the bush's visible body (rectangle inset 3 / 2 / 6 px left / right / top, measured on the art), not the rectangle widened by 6 px. `Decor.REACH` is removed.
- **Wall rumble:** `wall_rumble.wav` is regenerated as a seamless 1 s loop and played with `GameContext.loopSound` for `Wall.moveDurationSeconds()`, then `stopSound`; `Level.clearCameraEffects` (pause, death, retry, room change) stops it. Chosen over tiling or a longer file because the loop stops exactly with the wall for any `time` property (the shipped maps use 1.5 s, the default is 1 s) and on pause, death and room change, and both backends support `loop`/`stop(id)`.
- **Tests changed, stated plainly (no tolerance relaxed):** `DecorTest.aStandingHeroOrOneOutsideTheWidenedBoxStartsNothing` became `aStandingHeroOrOneBesideTheVisibleBodyStartsNothing` because the widened zone is exactly what was wrong; `WallLessonTest` rumble assertions read the loop request instead of a one-shot `playSound`; `tools/test_generate_sounds.py` no longer requires the loop file to start and end at exactly 0 (a seamless loop has no fade) and instead requires the wrap step to be no larger than the largest step inside the file. The one-shots still start and end at 0.
- **Measured:** landing dust through the real `Level.fx()` 0.0 B/frame (ceiling 8, same as the jump dust).
- **Not verified:** how the landing puff looks, the bush timing and whether the abrupt start and stop of the loop sound acceptable in the running game (desktop and web); web loop/stop behaviour in a release browser.

## D65. Light blood spray when the sword kills a monster (#319, 2026-11-13)

- **Status:** Implemented; automated checks only, the look was not judged by a human yet.
- **Decision (author):** every monster dies from one hit, so the spray happens when the sword hits it; a monster killed by a fireball dies without any spray. Thrown stones and stomps (also kills) spray nothing either, and no other killer calls it.
- **Mechanism:** `Player` calls `Enemy.hitBySword(level)` for non-boss enemies; it does nothing when the enemy is already defeated, otherwise defeats it exactly as `collidedWithActor(true)` and then calls `Level.mobBlood` once, so repeated frames of one swing, defeated mobs and room resets produce one spray at most. `KimeriaBloodSystem.sprayLight` emits `LIGHT_DROPLETS` = 12 (3 thick), speed x0.75, size x0.8, streaks x0.6, same colour, from the body's near edge at 45% of its height, direction away from the hero (world Y downward). Floor line: the body's bottom edge; a `Bat` (airborne) lets the drops fall 256 px so they fade before landing. Covered: `RedMob`, `YellowMob`, `Bat`.
- **Capacity measured, unchanged at 96:** `KimeriaBloodSystemCapacityTest.bossSprayPlusSwordKillsStayBelowCapacityWithoutEvictingDrops` (a boss spray whenever it can be hit, a kill on every 0.7 s swing, 40 seeds, 30/60/144 fps) peaks at 72 live drops (48 + 2 x 12); a swing kills at most one monster (the hit loop stops at the first enemy), and three simultaneous kills plus an empowered boss spray would still be only 84. No existing test was weakened (the old boss-only peak of 48 is unchanged).
- **Allocation:** `AllocationBudgetTest.forest2Room2WithMobKillBlood` 1896.0 B/frame inside the boss-room scene against its 2120 ceiling (the scene itself costs about 1960); the blood system adds no allocation (`oneBloodSpray` 0.0 B/frame).
- **Settings:** there is no violence or blood option; none was added.
- **Not verified:** the look in the running game.

## D66. First external playtest closed: findings and direction (2026-11-15)

- **Status:** Accepted by the author, 2026-11-15 (questionnaire after the first external playtest). Record and
  analysis: [PLAYTEST_1.md](PLAYTEST_1.md).
- **Playtest:** external players, web build, written feedback (22 remarks, F1-F22). Qualitative only: no room
  times, death counts or per-trap data.
- **Closed by the author:** #129 (first external observation, done), #130 and #131 (not measured; closed by
  decision, the D19 hypotheses stay hypotheses), #300 (warning times accepted as they are, see the D7 update)
  with its tooling issues #306, #307, #308 (not needed).
- **Direction (each a tracked issue; the detailed rule gets its own D-number when it lands):**
  - Readability first: minimum text size raised for everyone (#335); static intro pages (#336); instant-text
    accessibility toggle, off by default (#342).
  - A contrast rule with three tiers (background, decor, active); decor is muted. Readability wins over
    deception: static look-alike mushrooms (#299) are muted, the real/false game stays between the active
    mushrooms of D63 (#344). The exploding stump gets an idle tell (#346); a trap visibility audit and
    visible-by-default rule (#348); falling-stone impact feedback (#347); the Bat ingredient is drawn as an item,
    D43's rule unchanged (#345).
  - The story is shown, not only told: codec-style dialogue screen with two facing portraits in a fairy-tale frame
    (#337); a visible fairy helper replaces the interaction outline, D29/D30 eligibility unchanged (#338); the
    fireball is the healer's gift (#340); a silver-thread pull on every death within today's death-to-control time
    (D3) and a healer line on the first death only, reversing the #228 exclusion in STORY.md (#341); a text audit
    against the magic-tale structure (Propp) before any rewrite (#339).
  - A bigger hero with separate left/right frames and distinct ladder up/down; hitbox and physics unchanged (#343).
  - Level design: every room gives something new (#351); tree branches as a forest mechanic candidate (#349);
    a Primal Planet reference study (#352); a Kimeria coat of arms on coins, reused later (#350).
- **Not verified:** none of the direction is implemented.

## D67. forest-1 grows a route room: one lever, two supports in opposite states, and a one-shot step to the refill (#187, 2026-11-15)

- **Status:** Implemented by [#187](https://github.com/jlogicgames/edgard_in_kimeria/issues/187) (part of [#103](https://github.com/jlogicgames/edgard_in_kimeria/issues/103)); automated tests, then the author's play-through of 2026-11-17: the author played the whole game on desktop and confirmed that everything is clearly visible and done. Refines D58 (see "Placement") and answers D31's open "one-shot versus reversible" question (see "One-shot").
- **Placement (author decision, 2026-11-15):** `forest-1` becomes a multi-room level. Room 1, `forest-1-traps` (x 0-1280), is the #260 trap course with every object, id and measured behaviour unchanged: its `RoomStart` is the old `Player` spawn (32, 272) and a `RoomExit` (1216, 272, 32 x 48) stands where the `Checkpoint` was, so a retry still restarts at x 32 (the course stays one room; D58's "not split" holds). Room 2, `forest-1-route` (x 1280-2112), is new; the `Checkpoint` (id 7) moved to its end. **Update (#104, D68):** the combination room was appended after room 2 as planned: this room's checkpoint became a `RoomExit` (2048, 112, 48 x 32, the old checkpoint's x, so the times below are unchanged) and the level `Checkpoint` moved to the end of room 3. `forest-1` still has no hints (D33, `TutorialLevelTest`). **Update (#375, D85):** a new first room, `forest-1-mob`, now comes before the trap course, which became room 2; its `RoomStart` is no longer the level spawn.
- **Update (#418, D86, 2026-11-17):** a teaching room, `forest-1-lever`, now precedes this one in play order (the level's room 4): the two-mechanism lever is taught there first. No coordinate of this room changed.
- **Update (D84, playtest 2026-11-17):** the two-mechanism legibility claimed below failed in play: the author got stuck, because only the ladder visibly reacted to the pull. [#418](https://github.com/jlogicgames/edgard_in_kimeria/issues/418) and [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419) track the fix; see [D84](#d84-a-puzzle-never-makes-the-player-guess-or-look-stupid-playtest-418-419-2026-11-17).
- **Rule (D31, the "optional route" check):** one lever drives two supports **in opposite states**. A `Ladder` starts raised (its foot 112 px above the floor, out of every jump) and a vertical lift (`Escalator`) starts running, from its top (y 176) on its way down. One pull lowers the ladder **and stops the lift where it is**; the next pull raises the ladder and starts the lift again. The lift is the **mandatory** way to the checkpoint ledge and needs no lever; the ladder is the **optional** way up to a slab. So the lever is a real choice, readable before it is made: the entrance shows the lever, a raised ladder over a slab with a refill pad above it, and a lift in motion beside the exit; pulling it shows at once that the ladder comes down and the lift freezes, so the way on is closed until the lever is pulled again.
- **One-shot (author decision, 2026-11-15: "a one-shot element is needed, after which the reward is lost until Restart Level"):** the single-charge fireball refill (D27) lies on a pad 80 px above the slab, out of reach of a jump (the first hop can be made only from a platform in between). That platform is an existing `FallingPlatform` (the same look and the same shared warning as the four platforms of room 1, D46), the **step**, 40 px above the slab and 40 px below the pad. Standing on it starts a 1 s warning (1.5 s with the longer-warnings assist), then it falls and is gone for good. Whoever hops onto it and on to the pad in time takes the refill; whoever steps on it and back, hesitates or falls off has **spent the one-shot** and the pad is out of reach: the refill is lost until Restart Level (or, before the room is secured, until a death retry). The exit stays reachable through the lift in every such state. **Legibility without a hint:** the step is in plain sight from the slab (and from the entrance), it looks and behaves like the platforms the player has already seen collapse in room 1, and the reward it guards is in sight above it; the consequence (the platform lights, then drops) is the one the level has already shown twice. **Why a falling platform and not a jamming lever:** it is an existing, snapshot-aware one-shot object (the room snapshot keeps its position, timers and removed state, so D26's state boundary needs no new code), whereas a one-shot lever would need a new owner class, a save snapshot and a visual. Answer to D31: the lever toggles stay **reversible**; the one-shot belongs to a **support the optional reward depends on**; no new code.
- **The bat (author's request, 2026-11-15):** one existing `Bat` (no new art) patrols horizontally at the hero's own height over the floor stretch before the lift, x 1856-1904 (id 59, (1856, 296), 16 x 16, `offNeg` 0, `offPos` 3, 50 px/s). It never chases; it blocks the walk to the lift, so the mandatory route has to deal with it: the **sword** (one hit), a **stomp or a hop over it** (a scripted hop clears it from 39 of 42 takeoff gaps) or a **fireball from afar**. **Zero fireballs are enough**, and a fireball is an optional shortcut (D26, #104's rule). Its beat stays clear of everything the one-shot decision involves: 300+ px from the lever's reach, 100+ px from the ladder, 80+ px from the step and 14 px before the lift's boarding point; and a hero who drops off the pad lands clear of it: the second review found that holding Right off the pad's right edge drifts about 90 px (the first beat, starting at x 1760, killed 20 of 58 swept bat phases), so the beat now starts at x 1856 and `droppingOffEitherPadEdgeWhateverTheBatsPhaseNeverLandsInItsBeat` sweeps 54 bat phases for a walk off the right edge and a walk or jump off the left (the farthest landing is centre x 1838.5, the bat's reach starts at 1859). A deliberate running jump to the right off the pad can land in the beat; that is an act, not a drift. The decision at the slab is never made under attack (`theBatPatrolsTheFloorLevelStretchBeforeTheLiftAndNothingElse`, `theBatNeverReachesTheLeverOrTheSlabHeroAndNeverChases`). **Retry rules:** a death or room retry on a first visit brings the bat back alive at x 1856 (the existing enemy rule); a kill is secured with the room (a room appended after it, #104, or a healer return), so a secured kill stays a kill (`deathAndRetryBringTheBatBackAndASecuredKillStaysAKill`).
- **State boundary (D26):** while the room is unsecured a death or room retry restores the authored state (step intact, ladder raised, lift running from its top, lever down, refill back, the unsecured charge rolled back, hero at x 1344). Room 2 is no longer the last room (D68): its forward exit secures it, as does a healer return. The room snapshot then keeps the step spent and the ladder as left, the refill stays out of reach and the lift route still reaches the exit (`aSecuredSpentStepStaysSpentOnARevisitAndTheExitStillWorks` secures the room through the package-private `Level.secureVisit()` the room transition uses, then takes the reset path (`resetCurrentRoom`, which applies the snapshot as a revisit does; only the stock bookkeeping of `activateRoom(revisit = true)` is not exercised, since that needs a next room)). **Restart Level** rebuilds the level from the map: the step is intact and the refill is back. Room 1 is secured when the hero leaves it, so a death in room 2 never replays the trap course.
- **Link:** the lever, the ladder and the lift share the name `Route` (the legacy name link of `Level.spawnLevers` and `tools/check_tiled_links.py`: the ladder is the lever's face, an `Escalator` never counts as the face). No code was needed for the room. A wall and a ladder cannot be paired this way (a lever needs exactly one `Actionable`, and neither has an authored start state); the lift is the one support with both properties, and its stop-in-place rule (D37) is what makes "closed until undone" without a second lever.
- **Recovery and D26:** the lever is a reversible toggle and the lever is in reach only on the floor, 96 px from the ladder and about 430 px from the lift, never from the slab, the step, the pad, the ladder, the lift or the checkpoint ledge, so no support is taken from under the hero. Every drop (slab, step, pad, ladder) lands on the floor with the lever in walking reach (a 190 px fall from the pad is harmless). From every state reached by supported actions (lever pulled any number of times at any rhythm, lift stopped at any of 53 moments 0.1 s apart over its 5.12 s cycle, step intact or spent, hero on the floor or on the slab, refill taken or lost) the hero flips the lever back if needed and reaches the checkpoint; none needs Restart Level (`Forest1RouteTest`).
- **Geometry (Y grows downward, floor top y 320; room 2 is 832 px wide):** lever (1440, 304); ladder (1536, 96, 16 x 112, `dropDistance` 112; lowered y 208-320); slab (1552, 208, 80 x 16), a floating slab so the floor passes under it and the lever stays reachable; step `FallingPlatform` (1648, 168, 32 x 16); refill pad (1696, 128, 48 x 16) with the `FireballRefill` (1712, 112); lift (1936, 176, 32 x 16, vertical, `offPos` 4, 50 px/s, between y 176 and y 304, starting at the top); bat (1856, 296, patrol x 1856-1904); exit ledge (1968, 144, 128 x 176); room exit (2048, 112, 48 x 32; the checkpoint stood here until #104). Why the numbers: from the slab (top y 208) the pad (top y 128) is 80 px up, more than a hero can reach (a platform is boarded up to 71.8 px above the feet, measured, thanks to the platform catch-up margin), and the step splits it into two hops of 40; the pad is 128 px from the lift and the exit ledge, so neither reaches it (swept). The exit ledge is 16 px **above** the lift's top (a hop, not a step off) because a lift stopped at y 248 or lower can be boarded from the floor: the first version had the ledge top at y 208 and a sweep found the exit reachable from a stopped lift without the lever.
- **Reward and its worth:** one existing `FireballRefill` (D27); the room starts a retry with the stock rolled back to its entry value. The required route needs zero charges. At a full stock of three (a run starts with three and room 1 needs none) the refill alone would be worth nothing. **The bat changes that (author's request):** it gives a reason to spend a fireball in this room, so a hero who shoots it arrives at the pad with a free slot and the refill returns the charge (`theRefillPaysForTheFireballTheBatCostsAndAFullStockCanBeSpentHere`: stock 2, shoot, detour, stock 2 again; at a full stock the touch leaves the pickup in place, D27). The fireball buys **safety, not time**: the shot route measured the same as the sword route (the lift's schedule decides both). The #104 room that follows (a RedMob, [D68](#d68-forest-1-grows-a-guard-room-a-redmob-in-a-low-tunnel-under-a-spore-cloud-and-a-roof-route-with-a-refill-104-2026-11-15)) offers a fireball as another optional choice: built, a held charge shoots the guard from afar, and that room has a refill of its own on its roof, which returns the charge (D68).
- **Measured (scripted hero, real input, zero charges, `Forest1RouteTest`):** required route (walk, beat the bat with the sword, ride the lift, hop onto the exit ledge, walk to the checkpoint) **11.02 s**, three identical attempts after retries; with one fireball on the bat 11.02 s (same: the lift's schedule, which is boardable from the floor in the windows 1.5-3.7 s and 6.6-8.8 s after room start, decides it); the optional detour (lever, ladder, slab, step, pad, refill, drop, back to the lever, lever again) **8.03 s**, the whole room with the refill **18.32 s**, inside the 20 s budget. The lift starts at its top so that the sword fight does not make a hero miss the first window (starting at the bottom, the bat pushed the route to 13.35 s). Author check, not a novice playtest (#129-#131). Allocation: the route room (bat alive) measures about 1,026 B/frame in the budget harness (ceiling 1,130; the riding scene measured 930 here and 993.8 for the reviewer: JIT-dependent whole-scene numbers that do not isolate the rider path); the rider path itself is guarded by `ridingARunningEscalatorCostsNoMoreThanStandingStill` (a hero riding a running horizontal escalator minus the same hero standing: 0.0 B/frame, ceiling 8; with the old `Vector2` code it measured 24.0 and fails); room 1 is unchanged (1,322 against 1,450).
- **Code change, small (review finding):** `Player` allocated a `Vector2` every frame while riding an escalator (`Escalator.currentMoveDirection()` then `scl`). It now reads the new allocation-free `Escalator.riderVelocityX()`; `currentMoveDirection()` stays for its test. The lift is the mandatory way in this room, so the rider path now matters. `Level.secureVisit()` lost `private` (package-private) so that a test can secure a room without reflection.
- **Tests changed, stated plainly (no tolerance relaxed):** `Forest1TrapsTest` now ends a run at the room exit instead of at the checkpoint touch (same x, same measured 12.27 s); `RoomTest.unmarkedMapKeepsItsOriginalPlayerEntrance` became `aMapWithoutRoomMarkersIsOneRoomAtItsPlayerSpawn` (no shipped map is unmarked any more, so the fallback is exercised by stripping the Rooms layer in memory) plus `forestOneIsTheTrapCourseThenTheRouteRoomWithTheCheckpoint`; `RoomCeilingTest` covers both forest-1 rooms and the 176 px exit-ledge head start; `AllocationBudgetTest` gains the route room's ceiling (measured), the riding scene and the isolated rider-path guard. New: `Forest1RouteTest` (census and links, the bat's patrol and clearances, the sword, hop and fireball answers, the refill's worth at stock 2 and 3, the bat's retry and secured kill, the optional route closed without the lever, the one-shot step and the closed pad, the required route and its time, the optional detour, the D26 sweeps with the step intact and spent, the refill-lost state reaching the exit, Restart Level, retry and a secured revisit, entrance).
- **Author's answers (2026-11-15):** (1) append rooms to forest-1, the checkpoint moves to the new room; (2) the optional reward is a single-charge fireball refill; (3) no hints, existing art only; (4) "a one-shot element is needed, after which the reward is lost until Restart Level"; (5) "add a Bat to the route room: it harasses the hero and a fireball can shoot it down, so a second fireball becomes useful in this room too" (the bat patrols and blocks rather than chases, see above).
- **Not verified:** the room and its pad and slab tiles in the running game, whether the step reads as a one-shot without a hint (it relies on the player having seen the room-1 platforms collapse), the feel of the lift wait (the lift is boardable from the floor while it is within 56 px of its bottom, windows of 2.2 s every 5.12 s, the first 1.5-3.7 s after room start; a hero who just misses one waits up to about 2.9 s) and of the bat fight. The author's play-through of 2026-11-17 gave only the general confirmation above, so these questions were not answered separately; the novice playtests (#129-#131) remain open.
- **Update (#104, D68):** the room is no longer last. `Forest1RouteTest` now ends its runs at the room exit instead of at the checkpoint touch (same x, the same measured 11.02 s, 8.03 s and 18.32 s, nothing relaxed); its census asserts that the checkpoint is gone from this room and the exit stands on the ledge; `theRouteRoomIsEnteredFromTheTrapCourseExitAndRetriesStayInTheRouteRoom` now expects a next room.
- **Details:** [ROOMS.md](ROOMS.md#forest-1-route-room-187-d67), [Forest design](GAME_DESIGN.md#forest-linked-route-reconfiguration), [D31](#d31-forest-uses-linked-route-reconfiguration), [D26](#d26-observe-plan-before-committing).


## D68. forest-1 grows a guard room: a RedMob in a low tunnel under a spore cloud, and a roof route with a refill (#104, 2026-11-15)

- **Status:** Implemented by [#104](https://github.com/jlogicgames/edgard_in_kimeria/issues/104) (part of [#103](https://github.com/jlogicgames/edgard_in_kimeria/issues/103)); stacks on D67 (#187, PR #334). Revised twice after review (see "Finding"). Automated tests, then the author's play-through of 2026-11-17: the author played the whole game on desktop, confirmed that everything is clearly visible and accepted the room as it is, without a separate answer to the questions below. There is still no novice playtest (#129-#131).
- **Placement (author decision, 2026-11-15):** `forest-1` gets a third room, `forest-1-guard` (x 2112-2880, 768 px), appended after `forest-1-route`. Room 2's checkpoint became a `RoomExit` (2048, 112, 48 x 32, the old checkpoint's x, so room 2's measured times did not move) and the level `Checkpoint` (id 7) moved to the end of room 3, on the floor at (2800, 288). `forest-1.tmx` is 180 x 23 tiles (2880 x 368 px); the level-wide `Deadline` was widened from 3872 to 4640 px so that it still spans the map. The threat is a RedMob, the enemy already met in room 1 (D58). No hints, no hazards beyond the one trap, no new art, no production code change.
- **Layout (Y grows downward, floor top y 320):** a **tunnel** on the floor: a solid block (2304, 224, 256 x 64) under the slab (2304, 208, 400 x 16) leaves a 32 px opening (y 288-320) between x 2304 and 2560. In it, left to right: a **spore puffball** (`Puffball`, 2375, 304, 16 x 16, `period` 4, `cloud` 1, `phase` 2.52 since #500, D110 (2.2 before); its cloud is x 2373-2393, y 298-318) and the **guard** (`RedMob`, art (2394, 288), body x 2400-2420, `offNeg` 0, `offPos` 0: it never moves; it faces left and strikes only a hero within 6 px of its body). **The floor through the tunnel is the support-free route** (start (2208, 272) since #500 (2176 before) to the checkpoint; it needs no lever, ladder or other support): there is no way over the guard (the opening is too low to jump or stomp) and no way past it but to kill it. The **optional route is the roof**: the lever (a `Trigger`, 2256, 304 since #500; 2224 before) and a `Ladder` (2288, 96, `dropDistance` 112, starts raised) share the name `Bridge`; the lowered ladder reaches the slab, which runs over the tunnel to x 2704, with a **`FireballRefill`** (2640, 192, id 76; not a coin, coins feed the healer recipe) on its far part. **Both routes reach the checkpoint with zero charges**, so the zero-fireball requirement (D26, D27) holds on each; the roof is not a bypass of a mandatory fight, it is the second way through. The roof is unreachable without the lever (`theRoofIsUnreachableWithoutTheLever...` fuzzes 40 six-second runs of jumps, wall-jumps and clambers against the tunnel block's left face and the raised ladder; the feet never got above y 245, the roof is at y 208). Full table: [ROOMS.md](ROOMS.md#forest-1-guard-room-104-d68).
- **Why a second threat, and why this one (the author's decision of 2026-11-15: "add a second threat next to the RedMob so the sword does not dominate"):** the sword out-reaches the whip (17 px against 6 px) and its swing keeps its hitbox active while the guard walks into it, so a RedMob alone is no danger to a hero who can swing (measured in the first layout: one swing started at any gap of 0-64 px killed it, 33 of 33). The candidates: a **thorn tuft** at the guard's flank bristles when the hero enters its zone and then **stays** lethal, so it is a one-way trap, not a timing; a **falling branch or stone** is a one-shot and would need its own recovery rules (D26); a **bat** is killed by the same swing. A **spore puffball** is the one existing trap that repeats on a rhythm (D46): the hero can see where it is in its cycle (the cap swells for 0.8 s, the shared warning of D7, then a 1 s cloud), wait, and act in the calm. It is already known from room 1 (D58), so the telegraph needs no hint. Placed so that its cloud (x 2373-2393) covers **every place from which the sword reaches the guard without the whip reaching the hero** (a gap of 7-17 px; asserted for every half pixel), a swing is possible only in the calm, and a swing that is still going when the cloud arrives is fatal (the hero is frozen while it plays). The tunnel's 32 px ceiling closes the other doors: no hop over the guard, no stomp. A fireball from afar needs none of this (it flies into the tunnel, and the guard never moves); the roof route avoids the tunnel entirely.
- **The choices (scripted hero, real keyboard input, no invulnerability; author check, not a novice playtest):**

| Choice | What the hero does | Time (entrance to checkpoint) | Succeeds | Charges | Guard | Roof, refill |
| --- | --- | ---: | ---: | ---: | --- | --- |
| Timed melee, mastered (the support-free route) | arrives in the calm (the phase is deterministic), swings from within 16 px, walks on | **6.50 s** | always | 0 | dead | untouched |
| Timed melee, patient, phase unknown | waits outside the cloud until 1.0 s of calm are left, then as above | 6.36 s on average over 60 start phases (measured from a teleport 30 px before the cloud, wait included, `Forest1CombatTest`) | 60 of 60 | 0 | dead | untouched |
| Melee, impatient, at artificial start phases | teleported 30 px before the cloud, walks straight in and swings | 5.33 s when it works | 30 of 60 phases (50 %); otherwise the cloud kills | 0 | dead | untouched |
| **Straight run from the room's entry (what a player does first)** | holds Right from the start and swings from 16 px | dies at frame 82 | **0 of 1: deterministic first death**; works with a pause of 9 frames (0.15 s) or more | 0 | (dead) | untouched |
| Fireball from afar, then a patient crossing | one shot from x 2290 (the guard dies in its tunnel), then waits outside the cloud for 0.5 s of calm | **5.82 s** | 30 of 30 phases | one spent | dead | untouched |
| Roof route (environment) | lever, ladder, roof, refill, drop | **7.40 s** | 10 of 10 phases | **+1** (the refill) | alive, asleep | taken |

Times are pinned within 0.1 s by the tests; every route is well inside the 20 s mastered budget. The impatient window is exact: the melee works when the hero reaches the cloud's edge in a 2.0 s stretch of the 4 s cycle (calm with at least about 1 s left), 30 of the 60 start phases swept 4 frames apart.
- **Update (#500 batch 3, D110, author-approved 2026-11-20, revised in place):** the beetle was met as beat 3 with a 6.08 s retry, over D105's 6.0 s. The mastered melee is gated by the puffball's clock, not by the walk, so moving only the start would not have shortened it. `RoomStart` 61 (x 2176 to 2208), the lever `Trigger` 63 (x 2224 to 2256) and the puffball 77 `phase` (2.2 to 2.52) moved **together**, by 32 px and 0.32 s: the hero reaches the cloud 0.32 s sooner and meets it at the same point of its cycle, so the first death and its cure are unchanged (a straight run still dies, with a pause of 0 to 6 frames; a pause of 9 frames succeeds, sweep of 0-90 frames in steps of 3: 3 deaths, then 28 successes) and the price list moves by the same shift. Measured (`Forest1CombatTest`, pinned within 0.1 s): melee 6.83 to **6.50 s**, shot 6.15 to **5.82 s**, roof 7.67 to **7.40 s** (so the roof costs **0.90 s** over the melee, was 0.84 s; a charge still buys 0.68 s), the first walk-in death at frame 82 (1.37 s, was frame 101), the beetle's burst after the mastered melee 6.08 to **5.82 s**. Lever reach x 2246.5-2281.5, 91.5 px from the danger and 69.3 px from a climber (the rule: 48 px). **The one assertion changed:** `theEntranceIsSafeAndFarFromTheTrapsAndTheExitIsClearOfTheRightWallAndTheDrop` asserted the cloud at least **140 px** from the entrance's 48 px square (`CLOUD_LEFT - (START_X + 48)`); it is **117 px** now (2373 - 2256) and the assertion is `>= 117`. What the 140 carried: the distance D68 had measured, not a rule. The rule it stands for is that the cloud never covers a place where the hero stands at the entrance (`Puffball.validate`, `everLethalIn` the entrance body), which still holds, and the lever and climber clearances keep their 48 px rule. The beetle's pocket (the tunnel's mouth, x 2550) is in `FirstMeetingPocketTest.forest1GuardTheBeetle`.
- **Finding (does the sword still dominate? Honest assessment, with the history):**
  1. **First layout (guard alone on the floor, hole in a slab above it):** no. One swing killed it at any gap of 0-64 px (33 of 33) and a hop over it passed from 22 of 42 takeoff gaps. The sword dominated.
  2. **Second layout (guard at the ladder's top):** a real risk but a 0.1 s one (a swing within 5 frames of stepping off, 6 of 15 delays), a refill that paid nothing at a full stock, and no timing answer: superseded by the author's request for a second threat.
  3. **This layout (second threat, cloud over the sword's standing places):** the sword no longer dominates *as an instant answer*. An unaware or impatient hero dies in half of the cycle (30 of 60 start phases), a patient one never does (60 of 60) and pays for the wait (6.36 s on average from the teleport against 5.33 s for the impatient who live); a hero who knows the phase loses nothing (6.50 s). The alternatives now have a price list a player can read: a **charge** buys 0.68 s and removes the risk; the **roof** buys safety and **+1 charge** for **0.90 s** over the mastered melee (7.40 s against 6.50 s); the sweeps start at a teleport 30 px before the cloud and the roof at the entrance, so the two are not compared. **What still holds:** at a **full stock of three** the refill pays nothing (D27: the shot route is 3 -> 2, and the roof route 3 -> 3), so for a hero who knows the cycle the mastered melee is still the best route (0.90 s faster than the roof, free, risk-free once learned); the decision then is only whether to spend 0.90 s as insurance. Below a full stock, the roof is the better route unless the hero is sure of the phase. The risk is a *learning* risk, not an execution risk: once the cycle is known the melee is deterministic. Whether that is a meaningful decision is the author's play-through to judge.
  **A real player does not arrive at an arbitrary phase.** The 50 % comes from a sweep with the hero teleported to 30 px before the cloud at 60 artificial start phases; a player enters at the room's start, where the cloud is always at the same point of its cycle (2.52 s into its cycle since #500, D110: the clock starts 0.32 s further on and the walk is 0.32 s shorter, so the hero meets it at the same point), and the retry replays the identical phase. Measured with the hero holding Right from the entry and swinging from 16 px (no teleport): **a straight run with no pause dies, every time, at frame 82** (the hero reaches the cloud's edge as the swell ends); a pause before setting off of 0, 3 or 6 frames (up to 0.1 s) dies the same way, and **a pause of 9 frames (0.15 s) or more succeeds** (sweep of 0-90 frames in steps of 3: 3 deaths, then 28 successes). So the first walk-in is a deterministic first death and the retry teaches the stop-and-wait; whether the swell and the cloud read as that cue is the author's play-through to judge.
  The hole of the first layout, the plank bridge and the guard on the slab were all removed; they do not appear in the shipped room.
- **D7 telegraphs (no hints):** the puffball's cap swells for 0.8 s (1.2 s with the longer-warnings assist; the assist changes only the warning, and the melee window does not depend on it because the cloud starts at the same cycle time) before the cloud; the guard's whip is raised for 0.4 s before it strikes (unchanged, not scaled). `check_tiled_links.py` (and `Puffball.validate`) accept the trap: the calm is 2.2 s, above the 0.8 s minimum with the assist, and the cloud never covers a place where the hero stands at the entrance.
- **D26 (swept by `Forest1CombatTest`):** the exit stays reachable with **zero charges and no Restart Level** from every state the player can produce: the lever pulled 0, 1 or 2 times (ladder raised, lowered, raised), the guard alive or dead, the refill taken or not, the cloud at four points of its cycle (0, 1.2, 2.3 and 3.5 s into the room), and the hero at the entrance, at the ladder's foot, at the tunnel's mouth, on the ladder's top, on the roof, by the refill or (guard dead) on the far side of the guard (the roof positions only with the ladder down: the lever is in reach only on the floor, so the ladder is never taken from under a hero): **216 runs, 0 failures**. The recovery is the patient floor route, which needs no support. Death (an impatient melee, killed by the cloud) and room retry on a first visit restore the authored room: the guard alive in its place, **the puffball's cycle back at its authored phase (2.52 s)**, ladder raised, lever down, refill back, the unsecured charge rolled back, hero at x 2208; the same route takes the same time twice. A **secured** room (a forward exit, a healer return: `keepPickups()` and the package-private `Level.secureVisit()` in the test, as in D67) keeps a dead guard dead, a taken refill gone and the ladder as left, and the floor still finishes. Restart Level rebuilds the level from the map.
- **Clearances (asserted, measured):** the lever's reach (the hero standing and at every height of a jump over the lever) is x 2246.5-2281.5, y 282.5-320; a climber on the ladder occupies x 2290.5-2303.7; the danger (the guard's body and whip, the cloud) is x 2373-2420, y 292-320. They are **91.5 px** from the lever's reach and **69.3 px** from a climber (the rule: 48 px or more); the cloud lies wholly under the roof. The cloud is 117 px or more from the entrance's 48 px square (140 px before #500, D110: see the Update below). A hero standing still outside the cloud (40 px short of the guard) for 10 s is never touched and the guard never moves.
- **Allocation:** the guard room measures 1,162.1 to 1,162.2 B/frame with the guard asleep and 849.7 to 1,017.8 with its attack running (the puffball cycles in both; the second is JIT-dependent and always the lower) (`AllocationBudgetTest.forest1GuardRoom`, `forest1GuardRoomWithTheGuardAwake`, ceiling 1,280 = highest run plus about 10 %). No production code changed (a stale comment in `Level.renderBackground`), so `check_allocations.py` has nothing to flag.
- **Tests (no tolerance relaxed):** new `Forest1CombatTest` (22 tests: census and coordinates, the entrance, the post, the tunnel (no hop, no stomp), the cloud over the sword's places and its phase lengths, the start-phase sweeps of melee and shot, the straight run from the room's entry (death with no pause, success from 0.15 s), the roof unreachable without the lever, the mastered melee, the mistimed melee and retry, the roof route at ten phases, a full stock, the outcome table, the clearances for a standing, a jumping and a climbing hero, the empty-stock routes, the D26 sweep, the secured and death states, Restart Level, the room chain, the lever's reach); route times are asserted within 0.1 s of the measured values and the sweeps against their exact counts. Changed existing tests: `RoomTest.forestOneIsTheTrapCourseThenTheRouteRoomWithTheCheckpoint` became `forestOneIsTheTrapCourseThenTheRouteRoomThenTheGuardRoomWithTheCheckpoint`; `RoomCeilingTest` covers all three forest-1 rooms; `AllocationBudgetTest` gains the two scenes; `Forest1RouteTest` ends its runs at the route room's exit, same x and same times, and **its assertions were changed, with the reasons**: `theMapKeepsNoHintsAndTheCheckpointStandsOnTheExitLedge` lost its direct checkpoint-x and "stands on the ledge top" assertions (the checkpoint is no longer in room 2) and became `theMapKeepsNoHintsAndTheRoomExitStandsOnTheExitLedgeAtTheOldCheckpointsX`, which asserts the exit rectangle instead (not reached 12 px short of its left edge, reached inside it, hero standing on the ledge top, the exit well in from the ledge's edge); and **one more assertion was reworded and weakened**: the entrance test's `ROOM_END - 16 - CHECKPOINT_X >= 48` ("the checkpoint is clear of the right wall") became `>= 16` ("the exit rectangle ends at the right wall"), because the 16 x 32 checkpoint was replaced by a 48 x 32 exit rectangle that is meant to reach the wall (2048-2096); the player triggers it at its left edge, the old checkpoint's x, and nothing hurts there, so no safety margin is lost.
- **Not verified:** whether the puffball's swell reads as the signal for the guard's spot without a hint, and whether the first walk-in reads as a stop-and-wait cue (it relies on the player having met the puffball in room 1 and on the guard and the cap being visible together); how a person, not a script, judges the 1 s swing-and-wait; the feel of the tunnel. The tiles were seen, and a fireball route was played through to the checkpoint, in the running web build by an agent check on 2026-11-15 ([ROOMS.md](ROOMS.md#agent-check-in-the-running-web-build-chrome-2026-11-15)). The author's play-through of 2026-11-17 did not answer these questions separately; the novice playtests (#129-#131) remain open. Measured times are a scripted hero's, not a player's.
- **Author's answers (2026-11-15):** (1) append a third room to forest-1 after the route room, its checkpoint becomes a room exit and the level checkpoint moves to the new room; (2) the threat is a RedMob; (3) no hints, existing art and objects only; (4) the fireball is offered as one optional choice here; (5) after the first review: **"add a second threat next to the RedMob so the sword does not dominate"**, keeping the RedMob where the player meets it with the sword (this entry), with the refill on the slab kept.
- **Details:** [ROOMS.md](ROOMS.md#forest-1-guard-room-104-d68), [Forest design](GAME_DESIGN.md#forest-linked-route-reconfiguration), [D67](#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15), [D58](#d58-forest-1-traps-and-a-second-redmob-three-traps-and-one-enemy-in-the-open-stretch), [D46](#d46-rhythmic-traps-spore-puffball-swinging-thorn-vine-and-the-rotten-log-skin), [D31](#d31-forest-uses-linked-route-reconfiguration), [D26](#d26-observe-plan-before-committing), [D27](#d27-three-held-fireballs-and-rare-single-charge-refills), [D7](#d7-traps-are-deterministic-puzzle-elements).


## D69. A collapsing FallingPlatform never carries a body into the floor under it (#355, 2026-11-16)

- **Status:** Implemented. Fixes [#355](https://github.com/jlogicgames/edgard_in_kimeria/issues/355); the platform rule is [D45](#d45-falling-traps-the-position-triggered-branch-and-stone) and the room rule [D40](#d40-the-tutorial-traps-lesson-rule).
- **Bug:** a `FallingPlatform` falls its full 200 px and `CollideBody.checkVerticalCollisions` snapped the feet to its top after the solid-block pass, even once the platform had sunk below a solid floor. The next frame `checkHorizontalCollisions` (it runs first) found the body inside the floor block and, with a direction held, moved it to the block's far edge: x -11 holding Right, x 1024 (the pit edge) holding Left on forest-1 room 1, platform (128,272), floor block x 0-1024. Standing still was fine.
- **Choice (the issue's option a):** the platform snap is skipped when the snapped body would overlap a solid block (`CollideBody.overlapsSolidAt`: active blocks that are not one-way platforms or quicksand, the same `CollisionUtils.checkCollision` test). The platform keeps falling and removes itself as scenery; the body is left where the solid pass put it, so it stands on the floor line (feet y = floor top) and gravity does the rest. Why not (b) or (c): (b) needs a second "lose support" state in the hero and in every `CollideBody` user (mobs too) for the same result; (c) changes the platform's authored fall and its snapshot, and would not cover a platform that sinks beside a step block (forest-1 (128,272) borders the block at (160,304)). (a) is one guard at the one place that moves the body into the floor.
- **Unchanged:** over a pit nothing solid is beneath, the guard never fires and the hero is carried down exactly as before (forest-1 rotten log, forest-0 traps room; tested, feet within 0.5 px of the platform top, measured max difference 0.0). A platform that has not sunk into a solid snaps as before.
- **At the crossing:** the platform passes the floor line, the snap is skipped and the hero free-falls for 3 frames (feet 318.83, 319.16, 319.65, then 320.0, the floor line) with `isOnGround()` staying true throughout (coyote time), so no input or animation notices (traced in the PR #367 review).
- **What counts as solid:** every active block that is not a one-way platform or quicksand, so walls and step blocks count as well as the floor. One-way platforms are excluded on purpose: they never block from the side or below, and a body resting on one must still be carried by a falling platform. A body overlapping a wall by a float epsilon would skip the snap; shipped coordinates are integers, so it does not occur.
- **Affected platforms fixed:** forest-1 (96,272), (128,272), (160,272), (192,256); forest-2 (1568,256). Coordinates: the tmx says y=256 for forest-2, the loaded platform sits at y=240 because the SpawnPoints layer has `offsety=-16` (forest-1 has none, its y is as authored). The step (1648,168) of the open PR #334 gets the fix once both are merged (the code is generic).
- **Tests:** `FallingPlatformFloorTest` rides forest-1 (96,272), (128,272), (160,272), (192,256) and forest-2 (1568,256), each mounted 8 px left of, at and 8 px right of the platform's left edge (so the hero stands over the plain floor, 320, or over the step block (160,304), 304, as asserted per case), holding Right, holding Left and (for (128,272)) with no input, sweeping the key-press frame from 22 frames before the crossing to 6 after. Each ends on the floor line under the body, grounded, within 60 px of where it was (a shove is 900+ px), never sunk into a solid, inside the level. Pit tests: the rotten log and the forest-0 traps platform still carry the hero. Without the fix the floor tests fail, with it they pass; the pit tests pass both ways. No existing expectation or tolerance changed.
- **Allocation:** `overlapsSolidAt` and `checkVerticalCollisions` are in `HOT_METHODS` of `tools/check_allocations.py`; the scan passes. It does not see `for (x : level.list)` (it only follows plain field names), so the older for-each loops in `checkVerticalCollisions` are not flagged; no `AllocationBudgetTest` scene covers a hero on a sinking platform.
- **Not verified:** the look in the running game.

## D70. Minimum text size raised for everyone (playtest F1, #335)

- **Decision (author, 2026-11-15):** raise the minimum text size for all players; no separate text-size option.
- **Sizes (logical px, 640x360 view; approved by the author, 2026-11-16):** `ui.FontSizes` holds all of them. Labels and hints (hero label, FPS counter, menu hint, boss label) 13 -> **16**; paragraph body (About, rules, menu body, story text) 15 -> **16**; dialogue (healer/Edgard lines, choice buttons, checklist, speaker names) 15 -> **18**; zone message, buttons, coin count (20) and titles (30) unchanged. Touch button labels stay **13** (author decision): 15 and 16 overflow the 50 px / 40.4 px button face (D55) in uk, de and be by 2 to 3 px, and the fix would need new words or bigger art.
- **Fonts:** only QuestSquare is baked (NanoPlus is not used for text, it lacks Belarusian glyphs). `FontCharacters.SIZES` goes from {13,15,20,30} to {13,16,18,20,30}; `QuestSquare-15.{fnt,png}` removed, `FontSizesTest` keeps used and baked sizes equal. 16 and 18 baked with the existing charset (192 glyphs incl. Cyrillic, Polish, German); the other sizes came out byte-identical.
- **Measured:** cap height 6 -> 7 px (13 -> 16), 7 -> 8 (15 -> 18, 16 gives 7). Hero label stack (3 lines, 200 px wide) height 32 -> 39 px, still 3 lines in all five languages. Dialogue: worst line 3 -> 4 lines (37 -> 62 px of the 72 px box). About controller help panel: 319 -> 327 px at 16 (limit 332, screen 360); 18 would need 380 px and overflows.
- **Tests:** D33/D49/D50 pinned geometry (24 px gap, two-line zone messages, label on screen) pass unchanged with the label at 16; no tolerance changes. Only the font-size literals in `LocalizationLayoutTest` and `HealerDialogueTest` change; the touch-label checks now measure the 13 px font explicitly. `HintLayoutTest` cap-height inputs moved from 29/15 to 10/7 (measured from the "H" glyph in the .fnt: the 20 px font gives 10, the 16 px font gives 7); its assertions are unchanged. `ingredientChecklistAndChoiceButtonsFitAtTheDialogueSize` is new in `LocalizationLayoutTest`: the 18 px checklist (width at most 361 px, top 48 plus height at most 130) and the three choice labels in the 560 px button, in all five languages. `FontSizesTest` is new.
- **Status:** Accepted by the author, 2026-11-16 (sizes; touch labels stay 13).
- **Verified (web release build, 2026-11-16, English, 4:3 pane):** main menu and menu hint (one line), About (panel fits), intro pages (2 to 3 lines), healer dialogue at 18 px (2 and 3 line replies inside the frame, speaker names under the portraits), and the forest-0 zone message with the 16 px hero label (no overlap). Playtest 2 (2026-11-21, web build, Ukrainian; [PLAYTEST_2.md](PLAYTEST_2.md)) covered the Ukrainian text at 16 and 18 px, the boss HUD label size and the three-choice dialogue: the testers raised nothing, which the author accepted as the check (weak evidence, not a measurement). De/be, the touch layout and the desktop build were checked by font metrics only (`LocalizationLayoutTest`, `HealerDialogueTest`), not rendered in the game; those in-game checks stay open in [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368).

## D71. Traps are visible before they fire (2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 ([#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348),
  playtest F22 in [PLAYTEST_1.md](PLAYTEST_1.md)). The rule, vocabulary, card template, catalogue and cards live in
  [TRAPS.md](TRAPS.md).
- **Rule:** every trap is **VISIBLE** at rest by default: drawn before it can fire and reading as active, not as
  scenery (a silhouette or colour no scenery shares, the "active" tier of
  [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344), or motion). A trap may be **DISGUISED**
  (drawn, but looks like scenery) or **HIDDEN** (nothing drawn at rest) only with all three: an always-drawn
  **at-rest cue**, the same on every instance of the type (a hint text is not a cue); the
  [D7](#d7-traps-are-deterministic-puzzle-elements) **telegraph** after the trigger, unchanged; and a first meeting in
  a **teaching room**. A cue not drawn yet is allowed only as `pending #NNN` with an open issue.
- **Audit (2026-11-16, from sprites and code, not frames of the running game):** VISIBLE: thorn tuft, bomb, spore
  puffball, thorn vine, both honey fungi, the rotten-log skin. DISGUISED: falling branch, exploding stump and root
  (cue: #346), plain falling platform (the author counts it as a trap, 2026-11-16; cue: #362, a hairline crack). HIDDEN: thorn root (a brown
  mound), falling stone (nothing until it falls; in `forest-2` its zone starts 16 px before the column and no hint
  teaches it there). Each pending cue has an issue: root #359, stone #360 (a "loose overhang":
  cracked lip and grit trickle; the author chose the cue alone, no wider zone, then measure), branch
  #361; the platform's crack is done (#362).
- **Consistent with earlier decisions:** D42's default ("hidden-trigger traps stay deterministic but get a visible
  cue") becomes the rule; D63's look-alike mushrooms stay allowed, because both glow as active and the puzzle is which
  one kills, not whether something is there. Supporting input: the #352 reference study ([GAME_DESIGN.md, "Primal Planet level study"](GAME_DESIGN.md#primal-planet-level-study-playtest-f16-352)).
- **New visible traps chosen for later work (cards first, nothing built):** trip-line deadfall (#363), creeping
  bramble (#364), hanging hive (#365). Candidate: an ember fissure (#366). Rejected: a snap burr (too close to the
  carnivorous plant D42 rejected).
- **Enforcement:** `TrapCatalogTest` (`:core:test`) reads the spawn switch of `world/Level.java` and the catalogue of
  TRAPS.md: a spawned type with no row, a stale row, an empty column, an unknown visibility or family word, a missing `###` card heading, a
  DISGUISED or HIDDEN row without a cue or `pending #NNN`, or one with no teaching room fails. The procedure for agents
  is the `kimeria-trap-design` skill; AGENTS.md has a "Traps" section.
- **Not verified:** the at-rest judgements and the ~320 px read distance (camera centred on the hero, from the code)
  were not checked on frames of the running game; the gate checks what the catalogue declares, not the art. In-game check: #368.

## D72. Every room gives the player something new

- **Status:** Accepted by the author, 2026-11-16 ([#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351)), from
  playtest remark F20 ([PLAYTEST_1.md](PLAYTEST_1.md), D66). Gate built (`RoomCardsTest`); the two room changes it found are
  follow-up issues ([#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375), [#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376)).
  **Revised by [D105](#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499)
  (#499, 2026-11-19):** "first met where it is the only unknown thing on screen" stays, but it never meant the only
  object in the room; a room also keeps the player busy (at least 3 beats, 1.5 to 4.0 s per beat) and a first meeting
  gets a safe pocket among known beats instead of a room of its own.
- **Decision:** every room (the death-recovery unit) contains at least one novelty in play order: it **introduces** an element
  the player has never met (mechanic, enemy, trap, pickup, interaction, view or reward), **develops** a known element under a
  condition that changes the answer, or **combines** known elements whose solution needs both. A room that only repeats is
  changed, merged into a neighbour or cut. A **reskin** (new look, same trigger, same answer) and more of the same geometry are
  not novelty. Outside the tutorial a room introduces **at most two** elements, each first met where it is the only unknown
  thing on screen before it is combined; more needs an `Overload reason` that cites an issue. Repetition for mastery stays
  allowed beside a novelty.
- **Relation to earlier rules:** generalises the tutorial's one new element per lesson room (D33-D40) and its D48 form (one
  lesson element plus at most one trap) to every room: D34-D40 and D48 stay the strict form for lesson rooms, D72 is the
  floor for all rooms and the ceiling for ordinary ones. It restates the #73/#75 "teach alone, then combine" rule in the
  current play order (D32). D19 (about 20 s mastered, about 60 s first learning) and D26 are unchanged and recorded per room.
- **System:** one card per shipped room in [ROOM_CARDS.md](ROOM_CARDS.md) (role, purpose, novelty by element id, D19 times,
  retry cost, D26), with an element registry that maps Tiled types to elements so reskins share one id; the rule and workflow
  in [ROOMS.md](ROOMS.md#every-room-gives-something-new-d72); the skill `kimeria-room-design` (`.agents/skills/`, with a
  Claude pointer in `.claude/skills/design-room/`, renamed `kimeria-room-design` by D75); a line in `AGENTS.md`; and `RoomCardsTest` in `:core:test`, which
  derives each element's first sighting from the maps and checks the cards against it (the Gradle test task declares the
  card file as an input).
- **Audit (2026-11-16, verdicts on the cards; the forest-1 part redone after #187/D67 and #104/D68 added two rooms):**
  the six `forest-0` rooms, `forest-1-route`, `forest-1-guard`, `forest-canopy` and `forest-boss` keep
  (`forest-0-wall` watched: its wall repeats the ladder room's "press, the way opens"); `forest-1-traps` and
  `forest-entry` change. The Forest's problem is overload and misplaced repeats, not too little novelty:
  `forest-1-traps`, the unchanged #260 course that D67 kept as one room, introduces four elements and meets its first
  enemy beside two other first sightings, the running escalators and the fireball refill (x 256-450; not on the falling
  platforms, which the hero has crossed 128 px earlier, measured on the map), so it is not met alone (splitting it
  reopens D58's choice); the new route room
  (one introduction, the bat) and guard room (no introduction, two combinations) pass. `forest-entry` introduces six
  (the bat is now first met in `forest-1-route`) and keeps the D24/#95 practice stone, light, ladder and carryable
  torch that `forest-0` now teaches (the author chose to remove the carryable torch too). Both overloaded cards carry
  an `Overload reason` until the follow-ups land.
- **Reference input:** the Primal Planet study (#352, GAME_DESIGN.md "Reference research") supports "develop" (a known
  element on a new surface) and suggests a landmark per room and calm beats after danger; the card has an optional
  `Landmark` field, not gated.
- **Why:** players notice a screen that offers nothing new (F20). The tutorial already followed the rule; the later levels
  had none and showed both failure modes.
- **Not verified:** whether a "develop" really changes the answer and whether each element is first met alone (the gate
  cannot judge it; review does); first-learning times (none measured, #135); how the follow-up splits will feel.

## D73. Falling stone impact feedback (#347, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (frames and sound reviewed). Closes playtest finding F17.
- **Decision:** every `FallingStone` landing, in every skin, throws dust, flying chips and debris that lies 2 to 3 s and fades
  (`EnvironmentFx.stoneImpact`, pooled `KimeriaParticleSystem`), plays the new `stone_impact.wav` (voice-synth, seeds 3601/3602,
  RMS -16.5), and shakes the screen 1.5 px for 0.15 s through `ScreenShake` (off with the Assist Screen shake option, D62).
  Particles rest on the floor line. Details: ART_DIRECTION.md "Environment effects", SOUND_DIRECTION.md "Stone impact".
- **Changed numbers:** pool capacities `DUST_CAPACITY` 24 to 32 and `GRIT_CAPACITY` 32 to 48 (a stone adds 10 dust and 14 grit
  particles to a pool a jump or lever also uses). Peak live grit: a lever burst (24) plus a stone (14) is 38 of 48. No test threshold or tolerance was changed.
- **Allocation:** new scenario `stoneImpactThroughTheRealLevel` 0.0 B/frame (ceiling 8); existing scenarios unchanged.
- **Verified (playtest 2, 2026-11-21, web build; [PLAYTEST_2.md](PLAYTEST_2.md)):** the dust and grit on the real forest-0 tiles; the testers raised nothing, which the author accepted as the check.
- **Not verified:** the sound on a real output (audio was not recorded in playtest 2): tracked in #368 (in-game verification of the playtest changes; also update this document when it is closed).

## D74. Text style system: two voices, glossary and a gate (#339, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (all drafts of [TEXT_AUDIT.md](TEXT_AUDIT.md) section 4). Rules:
  [TEXT_STYLE.md](TEXT_STYLE.md). No shipped string is changed by this decision; the rewrite follows in its own issues.
- **Decision:** narration and the healer speak in the tale voice (no control names, no game jargon such as boss,
  retry, checkpoint, lives, damage, list); hints are plain imperative; labels are one to three words; one form per
  concept per language from the glossary. English source first, author approval, then all five catalogs.
- **Limits:** the pixel limits of D50 are unchanged. New: EN zone messages at most 60 characters and two sentences
  (the five shipped hints over it measure 64 to 77 characters and fit two lines today, so the budget is a margin for
  translations, not a fix of a measured overflow).
- **Gate:** `tools/check_text_style.py` (T1 glossary, T2 tale register, T3 hint budget, T4 key names in hints, T5 unused
  keys, T6 glossary documented), test `tools/test_check_text_style.py` (Gradle `testTextStyleGate`, run before the gate),
  Gradle `validateTextStyle` in `:core:check`, the desktop jar and the web release build. Data: `tools/text_style.json`.
  Register word lists are per language; stems match at a word start, listed words only whole. 43 allowlist entries, one
  per finding (key, language, term), cover today's 43 findings, each naming the open issue (#NNN) that removes it
  (#371: 32, #373: 5, #369: 5, #374: 1); a stale entry or a non-issue reference fails the gate.
- **Test change, stated plainly:** `HealerDialogueTest.tutorialTalesStayInFairyTaleRegisterWithoutNamingControlsOrMechanics`
  now reads its word lists from `tools/text_style.json` (one list for Java and Python, found by walking up to the
  repository root). Its matching changed on purpose: before, every word was a substring checked in every language
  ("alive", "monkey", "sticky" would have failed); now each word is checked in its own language at a word start (or as
  a whole word for short English terms such as key, stick, press, live). The new
  `sharedRegisterListKeepsEveryWordThisTestUsedToPin` asserts every word the test pinned before is still held by its
  group and language; the shared list adds inflections, damage, boss and list, and the tutorial tales pass.
- **Not verified:** native-speaker review of the glossary forms for uk, pl, de and be; whether the 60-character hint
  budget is the right margin (it is a proposal from the audit, not measured against a rewritten hint).

## D75. One style system: the design language, card first, rules land complete (#386, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 ([#386](https://github.com/jlogicgames/edgard_in_kimeria/issues/386), the
  synthesis of the first-playtest audits #335, #339, #340, #347, #348, #351 and #352). Entry point:
  [STYLE_SYSTEM.md](STYLE_SYSTEM.md).
- **Decision:** the game is built in one design language of six principles, each owned by its existing decision:
  (1) readable before lethal (D70, D71, the contrast tiers of D76, [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344));
  (2) fair and deterministic with cheap retries (D2, D3, D7, D26); (3) every room gives something new, taught alone then
  combined (D72, D33-D40, D48); (4) **a hazard warns before it acts and shows a consequence after**: the D7 telegraph
  before, a visible and audible result after (screen shake only through `ScreenShake` and its Assist option, D62), and
  no effect hides a warning (the D73 pattern, generalised; measured and refined by [D93](#d93-principle-4-measured-a-hazards-own-act-is-seen-and-heard-hit-or-miss-388-2026-11-17));
  (5) the tale is shown, not only told, in two voices (D74); (6) crude original craft: native pixel art from authored
  sprites, voice-synth sounds generated, never sampled (D21, ART_DIRECTION.md, SOUND_DIRECTION.md).
- **Card first:** a trap, a room or a string is designed on its card (TRAPS.md, ROOM_CARDS.md, the TEXT_STYLE workflow)
  and approved by the author before it is built; a change that spans systems follows the order in STYLE_SYSTEM.md.
- **A rule lands complete:** a new design rule arrives in one change with its D-number, a reference document, a gate
  (or a stated reason that review checks it instead), a shared skill with its Claude pointer when it has a procedure,
  an AGENTS.md paragraph and a row in STYLE_SYSTEM.md.
- **Enforcement (new in this change):**
  - `TrapTeachingRoomTest` (`:core:test`): a trap's `Taught in` (TRAPS.md) starts with its teaching room, a shipped room
    with a card that shows the trap; no earlier room shows the type; a DISGUISED or HIDDEN trap's room introduces its
    element on its card (D71, D72). Five catalogue rows named a map or put the room second and were rewritten; no
    rule or tolerance of `TrapCatalogTest` or `RoomCardsTest` changed (two helpers became visible to the new test).
  - `docs/TRAPS.md` and `tools/text_style.json` are declared `:core:test` inputs, like ROOM_CARDS.md: before, an edit to
    either alone left the tests up to date and `TrapCatalogTest` or `HealerDialogueTest` did not run.
  - `tools/check_doc_gaps.py` (`validateDocGaps`, `:core:check`): on lines of `docs/**/*.md` changed on the branch
    (since the merge-base with `origin/main`, plus the working tree), a block with a gap marker (TODO, not yet, known
    gap, to be measured, needs upstream fix, pending) names an issue (`#362` or `.../issues/362`; an anchor such as
    `#2-foo` or the `#NNN` placeholder is not one). Not judged: headings, fenced code and inline code (quoted syntax),
    and "not yet" followed by a verb of the player's progress ("not yet met": play order, not project state). Run over the last 40 commits it found two real untracked gaps
    (TRAPS.md "the plain skin is the one pending", now #362; D62 "native review pending", now #369), both linked here.
  - `tools/check_skills.py` (`validateSkills`, `:core:check`): shared skills, the index `.agents/skills/README.md` and the
    Claude pointers agree; English only. Every `kimeria-*` skill has a Claude pointer of the same name (`design-room`
    and `game-text` were renamed); seven Russian `openai.yaml` files were translated.
  - `.github/workflows/check.yml` (author's choice, 2026-11-16): every pull request to `main` runs `:core:check` on a
    full-history checkout, then the Python tool tests; before it, no workflow ran `:core:check` or the new gates.
  - The changed-line gates `validateAllocations`, `validateNamedParameters` and `validateDocGaps` default to the base
    `auto` (`tools/git_base.py`): the merge-base with `origin/main` when it exists, else HEAD with a note. Before, the
    first two compared with HEAD, so on a clean checkout (CI) they checked nothing; shown in a scratch clone, where a
    committed `new` in `KimeriaParticleSystem.render` and four literal draw coordinates passed with the old base and fail
    with the new one. The hooks (`--hook`) keep HEAD; `validateSpriteArt` reads the whole tree and needed no base.
  - `tools/check_style_hook.py`, run by the PostToolUse and Stop hooks of Codex and Claude Code: the fast gates that
    apply to the files an edit touched (after the edit) or to every changed file (at Stop), including D76's
    `check_contrast.py` for sprites and maps (about 2.5 s), and the Gradle command of the trap and room tests when
    their inputs change.
- **Stray skill folders:** the Codex desktop app's Claude import ("Migrate skills from ... to ...", on when
  `external-agent-import-sync-enabled` is set in `~/.codex/config.toml`) copied `.claude/skills/design-room` and
  `game-text` to `.agents/skills/` as Codex pointers in every worktree. With the pointers named like their shared skills
  there is no missing folder to create; a folder it does create is unlisted and fails `check_skills.py`.
- **New skill:** `kimeria-sound`, the order of work of SOUND_DIRECTION.md.
- **Why:** the audits each left a working system written in parallel; without one entry point and one workflow the
  next feature picks one system and misses the others.
- **Not verified:** principle 4's gaps, found by the audit of [D93](#d93-principle-4-measured-a-hazards-own-act-is-seen-and-heard-hit-or-miss-388-2026-11-17), are [#443](https://github.com/jlogicgames/edgard_in_kimeria/issues/443) to [#448](https://github.com/jlogicgames/edgard_in_kimeria/issues/448) (#447 is delivered, see D93) and, for enemies, [#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240); whether the Codex import overwrites
  an existing `.agents/skills` folder of the same name (its code was read only as strings; `check_skills.py` fails if a
  shared skill turns into a pointer); Codex reading the `hookSpecificOutput` reminder (Claude Code's format). All three
  are checked in [#389](https://github.com/jlogicgames/edgard_in_kimeria/issues/389).

## D76. Three contrast tiers: background, decor, active (#344, playtest F15, 2026-11-16)

- **Status:** Rule accepted by the author, 2026-11-16, with the 0.28 active floor; the recolour of the bush, the static
  look-alike mushroom and the hanging vine, and the lever outline, approved and built the same day. Playtest F15 in [PLAYTEST_1.md](PLAYTEST_1.md).
  Section and measured inventory: [ART_DIRECTION.md, "Contrast tiers"](ART_DIRECTION.md#contrast-tiers-d76-playtest-f15-344).
  *Update 2026-11-17 (D92):* the background is now each map's flat `background` colour, one sample per placement; the
  sky and its offsets below are history.
- **Rule:** every sprite belongs to one tier. It is measured against what the game draws behind it (the screen-fixed
  sky at several horizontal offsets plus the tile layer) at every placement, in its rest frame (glowing sprites at full
  and dimmest glow, the bush in every frame); the worst placement counts.
  - **Active** (the hero, traps and their at-rest cues, enemies, collectables, mechanisms, terrain): outline
    **at least 0.28**. The near-black ink contour and the accent hues (magenta, lime, ember and gold, honey, sulphur,
    the pickup violet) belong to this tier only.
  - **Decor** (scenery sprites in the play plane: the `Decor` kinds and `HangingVine`): outline **at most 0.20**, body
    **0.08 to 0.20**, p90 chroma **at most 0.10**, no accent hue, no idle animation. *(The absolute ink floor, no pixel under OKLab L 0.30, is superseded by D108, 2026-11-20.)*
  - **Background** (sky, tile scenery, later parallax layers): no louder than decor, and static.
- **Metrics (OKLab):** outline is the mean lightness difference between each silhouette pixel and the background pixel
  just outside it. Body is the mean colour distance between each opaque pixel and the background under it. Chroma is
  the 90th percentile of the sprite's chroma.
- **Why these numbers (measured 2026-11-16 on forest-0, forest-1 and forest-2):**
  - Active sprites measured 0.25 to 0.48. Only the lever fell under 0.28 (below); no playtester reported any active
    sprite as hard to see except the stump (F18, reads as scenery by shape and colour, not by contrast: it measures 0.39).
  - The decor measured 0.31 to 0.57 with the same ink contour as the traps; the bush used the pickups' violet. That was F15.
  - 0.20 leaves 0.08, about four just-noticeable lightness steps, between the tiers; the body ceiling sits at the
    quietest tile scenery nobody complained about (rocks, 0.21); the body floor keeps the decor and the bush's
    brush-past (D64) visible.
- **Recolour (palette lines of `art/objects/decor/{bush,lookalike,vine}.grid` only; every pixel, frame and size
  unchanged):**
  - The bush is muted grey-green instead of violet.
  - The look-alike is muted grey-beige, keeping its tuft and rings.
  - The vine is pale dull olive.
  - All three lost the ink contour for a darker shade of their own hue.
  - Measured after: bush outline 0.12 to 0.18 in all four frames, look-alike 0.16, vine 0.06 to 0.16 (it was 0.57 in
    front of a cloud).
  - The brush-past changes the same pixels as before, with a smaller colour step per pixel (0.10 to 0.12, was 0.26 to 0.32).
- **Lever raised (author-approved art, 2026-11-16):** every `Trigger` draws the lever, and the one in front of the tile
  rubble at forest-2 x 128 measured 0.25 (the pulled frame 0.27 to 0.30 everywhere). A 1 px outline in the lever's own
  near-black `010000` around both frames, painted into `art/objects/lever-pixel.png` (removed in #413), lifts it to 0.47 to 0.58; the 3/4
  drawing, colours, frame, pivot and bottom line are unchanged. Active sprites now measure 0.28 (YellowMob) to 0.58.
- **Consistent with:**
  - [D71](#d71-traps-are-visible-before-they-fire-2026-11-16): "the active tier" is this tier, and at-rest cues are
    drawn in it.
  - [D63](#d63-honey-fungus-and-false-honey-fungus-a-pair-of-look-alike-proximity-mushrooms-295-phase-1-decision-and-art):
    both honey fungi are active (0.36 and up). The static look-alike is decor and no longer imitates them at full
    contrast, so the real-or-false game stays between the active pair.
  - [D64](#d64-feel-fixes-for-the-239-environment-effects-landing-dust-bush-trigger-looping-wall-rumble-2026-11-13):
    the bush trigger and frames are unchanged.
- **Not sufficient alone:** contrast does not make a trap read as a trap; D71's silhouette and cue rules still apply.
- **Update 2026-11-21 ([D120](#d120-depth-palette-the-near-layers-take-dark-real-colours-the-scenery-turns-bluer-lighter-and-greyer-with-depth-236-2026-11-21)):** the decor tier is drawn in a neutral dark palette with real hues (leaf green,
  bark brown, moss), not slate, teal or grey; the limits above are unchanged and every decor sprite passes them over the new layers.
- **Enforcement:** `tools/check_contrast.py` (test `tools/test_check_contrast.py`, Gradle `testContrastGate`, run
  before the gate), Gradle `validateContrast` in `:core:check`, the desktop jar and the web release build. It fails an
  active sprite under the floor (the lever in both frames), a decor sprite over a ceiling or under the body floor, and any object type in
  SpawnPoints or Collisions that is classified neither as a sprite with its tier nor as drawing nothing of its own.
  There are no exemptions.
- **Known gap:** the third-party tile scenery is louder than the background ceiling (rocks 0.29, signs, dead trees
  and tile bushes 0.36 to 0.37). The gate reports it without gating; replacing the tileset is
  [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189) (its checklist carries the D76 background ceiling).
- **Not verified:** the recoloured decor in the running game, by the author's eye (desktop and web): tracked in
  [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** trees and bushes are drawn opaque and clear in another palette, a little paler or darker than the active elements, and the glows are replaced by softer pixelfx effects, so the tier numbers are re-measured; this decision will be revised by issues [#555](https://github.com/jlogicgames/edgard_in_kimeria/issues/555), [#544](https://github.com/jlogicgames/edgard_in_kimeria/issues/544).

- **Update ([#571](https://github.com/jlogicgames/edgard_in_kimeria/issues/571), 2026-11-21, [D117](#d117-a-monster-body-keeps-its-live-palette-ink-and-moon-rim-the-pose-is-the-cue-571-2026-11-21)):** monster bodies (D101) leave the decor tier: a body keeps its live
  palette, ink and moon rim and is measured as active (outline at least 0.28 at every spot where it can lie).

## D77. Story screens are static pages (playtest F2, #336, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (the plan of 2026-11-15 and this draft).
- **Decision:** the opening and the ending are cut at paragraph breaks into pages, measured with the body font (D70) at
  load; a paragraph taller than a page fails the load and is never allowed to overflow, so no wording changes to fit.
  Paragraphs fade in (0.9 s each, 1.6 s apart) from the top and stay; nothing scrolls and nothing turns by itself.
- **Controls:** any fresh key, gamepad button or tap shows the rest of the page, then turns it; the last turn fades out.
  Escape or the pad's Back/Start leaves at once. Input is ignored for 0.6 s after a page starts and after it is whole,
  so a held button cannot skip pages. With Instant text (D11) a page appears whole and turns at once.
- **Measured:** the opening and the ending are one page in all five languages (the longest opening, German, ends 258 px
  from the screen top; the page area ends at 304 px). `AllocationBudgetTest.pagedIntro`: 0.0 B/frame, instant text off
  and on (ceiling 8), with the touch skip button's `updateSkip` and `renderSkipButton` in the measured loop. The harness's
  proxied `Gdx.input` allocates 120 B/frame by itself (24 B for each of the 5 polled pointers; measured alone, and the
  JIT sometimes removes it), so the scenario subtracts that baseline: 0.0 B/frame left in 3 runs.
- **No new string:** a pulsing triangle marks a page that waits for the button. (Amended by [D118](#d118-the-intro-names-its-skip-key-534-2026-11-21): the skip hint is a string.)
- **Touch skip button (author-approved, 2026-11-16; "good enough for now, polish later"):** on touch devices only, an
  icon-only button (skip to end, a play triangle and a bar, no string) sits top right of the picture at (606, 22),
  radius 13, hit radius 26 like the pause button. Paging is single-finger: a tap while another finger is already down turns no page (libGDX reports `justTouched` without a pointer index; `IntroInputTest` pins it); the button polls every pointer on its own, so any finger skips. A tap calls the same `IntroScreen.skip()` as Escape and Back, also
  during the input lock and on the ending, and that tap never also turns a page (the skip wins in the same frame).
  Art: `art/hud/touch/skip.grid` and `skip-down.grid` (exported by `tools/touch_control_sprites.py`). The outermost
  outline is white: the navy outline of the other discs measures 0.20 (OKLab) against the black story screen, under the
  0.28 active floor of [D76](#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16); the white one
  measures 0.99. `TouchControlArtTest` pins it; `validateContrast` covers world sprites, not touch art.
- **Verified (playtest 2, 2026-11-21, web build, Ukrainian; [PLAYTEST_2.md](PLAYTEST_2.md)):** the opening pages in Ukrainian on the web build; the testers raised nothing about reading them, which the author accepted as the check.
- **Not verified:** the ending page, the de and be pages, the look on desktop and the button on a real touch device
  ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the intro shows a small skip hint, reversing "no new string" (author decision); [D118](#d118-the-intro-names-its-skip-key-534-2026-11-21) (issue [#534](https://github.com/jlogicgames/edgard_in_kimeria/issues/534)) revises it. The intro retold in tale formulae over six leaves on an open book is [D133](#d133-the-opening-is-a-tale-on-an-open-book-535-536-amends-d77-2026-11-24): the opening is no longer one page cut by paragraph, but six leaves (three spreads) on a book; the ending is unchanged.

## D78. The dialogue is a codec-style storybook screen (playtest F3/F10, #337, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (Phase A mock-up, style A "walnut and gilt"; frontal portraits; a 1 px
  speaker bob; the frame as authored art).
- **Decision:** every healer conversation (the forest-0 start tale, the farewell, the pre-boss stop, the post-victory
  guide and later the first-death line, #341) uses one screen: two 176 px portrait windows face each other (Edgard
  left, the healer right; portraits 158 px, was 64), in a carved walnut frame with a gilt bead, crests and name plates,
  joined by the silver thread of [#228](https://github.com/jlogicgames/edgard_in_kimeria/issues/228). The speaker's
  window, crest and plate are lit and the portrait lifts 1 px for half of every 1.2 s; the listener's window, crest and
  plate are at half brightness (the two brightest gilts capped lower, see Contrast) and the portrait is multiplied by (0.55, 0.58, 0.70). A gilt notch on the text box points at
  the speaker; Edgard's text stays white and left-aligned, the healer's green and right-aligned. A glint runs along the
  thread from the speaker to the listener as each line starts (0.8 s).
- **Text box:** one box, 556 px of text, four lines of the 18 px dialogue font (D70). Every spoken line in all five
  languages fits in three (the longest, de `HEALER_TALE_FIREGIFT`, 1459 px), so one line stays spare for the
  first-death line. `HealerDialogueTest` holds both; the old two-thirds speaker regions (D41) are gone.
- **Pre-boss stop:** the checklist and the choices move to the column between the windows (232 px); the longest choice
  (uk, 173 px) fits with its markers in every language (`LocalizationLayoutTest`).
- **Opening:** 0.3 s (`HealerDialogue.OPEN_SECONDS`): the windows unfold (0 to 0.15 s), the box opens (to 0.2 s), the
  thread draws in from Edgard's wrist (0.1 to 0.3 s); the first letter comes after it. Instant text (D11) shows the line
  whole once the frame is whole. Paused time does not advance it; gameplay timing is unchanged (the world is stopped
  during a dialogue, as before). **Input waits for it** (review, 2026-11-16): `HealerDialogue.interact` takes no press,
  tap or choice before the screen is open, since no text is drawn yet; a press would otherwise skip the typewriter
  unseen, or with instant text advance lines nobody read. `HealerDialogueTest.inputDuringTheOpeningIsIgnored` covers both
  settings.
- **Art:** native pixel art, hand-drawn grids in `art/ui/dialogue/` exported by `tools/dialogue_frame_sprites.py` to
  `assets/images/ui/dialogue-*.png` (nine-patches, at most 16 colours, `check_sprite_art.py` contracts). The gilt is a muted
  `d8c08a`/`a8844a`, not the reserved gold `ffd23a` or honey `e2b25a`. The window opening is a dusk blue `3d4a68`: on the
  first dark navy the portraits' dark contours vanished (healer outline 0.05 dimmed).
- **Contrast (D76 by analogy):** `check_contrast.py` measures world sprites only, so `tools/test_dialogue_frame_sprites.py`
  holds every element of the frame to the tiers, on the exported art, with the layout read from `HealerDialogueView`.
  An element's loudness is its loudest colour against the ground it sits on (OKLab L; a window without its opening,
  which is measured with the portraits):
  - active, at least 0.28, what names the speaker or can be chosen: the speaker's bead 0.40 on the opening; the speaker's
    window band, crest and plate 0.64 on the page (the plate's rule 0.58 on its fill); the notch 0.64; the choices, idle
    0.29 on the page and 0.30 on the ink, selected 0.65; the thread's outline 0.43 to 0.44 on the screen it crosses;
  - decor, at most 0.20, the ornament and the listener: the listener's bead 0.07 on the opening; the listener's window
    band 0.17, crest 0.17 and plate 0.17 on the page (its rule 0.17 on its fill); the page border 0.17; the text box border
    0.17 on the page and 0.18 on the ink. The first build had the dim sheets at plain half brightness, whose rosette
    highlights measured 0.32: the two brightest dim gilts are capped at `44361e` and `3e3728` (0.18 at most from the page
    and the ink; review, 2026-11-16);
  - the dimmed portraits' outline 0.21 (Edgard) and 0.20 (healer) on the opening (at least 0.15), and visibly darker.
  Text against its ground (WCAG): lines 19.4 (Edgard) and 11.6 (healer), listener names 7.9 and 5.0, the prompt 8.8.
- **Allocation:** `AllocationBudgetTest.healerDialogueScreen`, two full runs: the tale line revealing 0.0 to 0.3 B/frame
  with instant text off or on, the pre-boss checklist and choices 0.0 (ceiling 8). Text is drawn from blocks built when a line, the language
  or a label changes (a new line every frame measured 16 B/frame, an event); the checklist's inventory and coin facts are
  read once per dialogue, since the world is paused while it is open. The screen queues at most 8 text blocks a frame
  (two names, the line, the checklist, two choices, two markers); `Overlay`'s block queue holds 16 before it grows, room
  for the first-death line (#341) and the fairy (#338). Headroom was chosen over a failing check: libGDX `Array.clear`
  keeps its storage, so an overflow would cost one growth, not one per frame.
- **No new string.**
- **Checked (2026-11-16, agent):** the web release build in headless Chromium at 1280 × 720, English, the forest-0 start
  tale: the healer's first line, Edgard's answer (the lit side, notch and name colours swap) and the fire gift (three
  lines, one spare). Those captures predate the dim-gilt cap above (only the listener's rosette highlights changed). Playtest 2 (2026-11-21, web build, Ukrainian; [PLAYTEST_2.md](PLAYTEST_2.md)) played the dialogue and raised no fit problem; the author accepted that as the check. **Not verified:** the desktop build, the other languages and the pre-boss stop in a running game
  (font metrics only), the opening's timing on screen, and a touch device
  ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)).

## D79. The hero is redrawn bigger, with two facings and ladder up and down (playtest F11, F21, #343, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (the Phase A art spec, as recommended); the core set is built, the
  second set follows (below).
- **Frame and box:** the hero is drawn in **64×64** frames; the 48×48 Entity box (`position`, `size`, spawn and room
  geometry) and the 11×22 hitbox are unchanged, so physics, every route test and the label anchor are untouched.
  The frame is registered with the feet on its last row (63) and the body axis on x 31.5, and drawn at
  `(position.x + pivotX() - 31.5, position.y + 48 - 64)` = **(x − 8, y − 16)**. World Y grows down: the frame's top is
  16 px above the box and its bottom is the hitbox bottom, the floor line (`Player.frameX/frameY`).
- **Size:** standing and running poses are at most **31 px** tall (the old figure was 24 to 25 px, about ×1.25), because
  the lowest gap the hero walks under is 32 px (forest-1 x 2296 to 2500; forest-2 x 240 and 1488 under a `Platform`).
  The head is drawn 9 px above the hitbox top; something passing over the drawn head does not kill (accepted).
- **Two facings:** `hero-right.png` and `hero-left.png` share one layout and nothing is mirrored at runtime. The sword is
  always in Edgard's **right hand**: in front of the body facing right, behind it facing left (the near left fist is
  empty), so it no longer jumps hands when he turns (F11). Left frames start as the mirror of the right ones in the
  grid (`frame ... flip`) and are then redrawn where the sword is.
- **Ladder:** `hero-ladder.png`, a back view used by both facings, with separate clips for climbing **up**, climbing
  **down** (the head turned to look down over the shoulder) and **holding still**, chosen by the ladder direction (the
  input, since a ladder zeroes the velocity). The old per-frame drop table (`LADDER_FRAME_DROP`) is gone: every ladder
  frame stands on its last row.
- **Clips (0.1 s a frame):** idle 4, run 6 (was 4), jump 2 (rise, apex; holds the apex), fall 2, attack **7**, look up
  and down 1 each, wall clamber 1, hit **2**, appear **4**, disappear **4**; ladder up 4, down 4, hold 1. The counts in
  bold are gameplay timing and did not change. The attack's blade ends at the unchanged sword box (x 9..46 of the box,
  17 px ahead of the body).
- **Carried object:** the grip is the near hand's palm, measured per facing on the new frames (`Player.heldObjectGrip`).
  Standing and running, the carry clips' near fist holds it (second set below); jumping, falling and attacking keep the
  core frames' palm. `DirectionalActionsTest` pins the palms: carrying, x 32 facing right and 15 facing left (the carry
  fist; the core idle palm was 31 and 16); the facing-left jump palm moved from (26, 17), measured on the old 48 px art,
  to (16, 22), the raised empty left fist of the new frame.
- **Art:** original, hand-drawn as a pixel grid (`art/hero/hero.grid`, 15 colours plus transparency, ink contour
  `0b0814`), exported exactly (no quantizing) by `tools/hero_sprites.py`; `tools/test_hero_sprites.py` checks the
  export, the palette and the binary alpha. `Player.png`, `Look.png` and `tools/export_player_look.py` are retired.
- **Measured:**
  - Contrast (D76): idle in both facings at every spawn point, worst outline **0.349** (floor 0.28); `check_contrast.py`
    measures both sheets under the `Player` key. The Phase A sweep over every walkable surface gave 0.320 worst for
    the sample frames.
  - Label above the hero (D33, D49, D50, unchanged and green): against the **drawn** hero
    (`LadderLessonTest.theLabelClearsTheDrawnHeroAsWellAsItsHitbox`), the label clears it by at least **16 px** on the
    floor in every sampled position (asserted: never crossing). Airborne or climbing under the zone message (D33's one
    accepted case) the label overlaps the drawn head by up to **7 px**, which is reported, not asserted.
  - `AllocationBudgetTest.heroDrawPathInBothFacingsAndOnTheLadder`: **0.0 B/frame** (ceiling 8), every state in both
    facings and the three ladder clips, with the grip.
  - `HeroFrameTest`: every grounded and ladder frame stands on its last row; the drawn feet meet the floor line in
    every grounded frame of a walk, a turn and an attack each way; idle, run and look are at most 31 px tall; the
    ladder plays up, hold and down as separate clips with the feet on the rung line.
- **Second set (#343 part 2, approved by the author 2026-11-16):** 34 more frames, rows 10 to 14 of both facing sheets
  (now 448×960):
  - **Carry idle 4 and carry run 6:** the sword sheathed on his back, the object in the near fist.
  - **Throw 3:** wind-up, release, follow-through.
  - **Land 1:** a squat, the sword lowered.
  - **The thread pull of [#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341), 3:** tug, lift and land.

  `Player` draws them over its states without changing the state machine or any timing:
  - Carrying draws the carry clips in place of idle, look and run.
  - A throw plays its clip for 0.3 s.
  - A landing that kicks up dust (and is not carrying) plays the squat for 0.1 s, counted from the first frame it
    shows.
  - The pull is drawn in place of everything else while `showPull(PULL_TUG | PULL_LIFT | PULL_LAND)` holds a frame
    (`NO_PULL` stops). `pullWrist(frame, out)` gives the world position (Y down) of the centre of the left wrist's
    pixel, where the thread is tied. The thread is on the left wrist in both facings: the far hand facing right, the
    near one facing left.

  Measured:
  - **Wrist pixels (frame):** tug and lift (17, 44) facing right and (46, 44) facing left; land (23, 52) and (24, 52).
    `HeroFrameTest` checks each one is the drawn forearm's skin.
  - **Contrast (D76, sweep over every walkable surface):** worst 0.324 (`l.throw1`) against 0.340 for the core idle;
    the gate measures carry idle in both facings.
  - **`HeroFrameTest`:** the carry clips while holding, with the feet on the floor line; the throw for 17 or 18 frames
    at 60 Hz; the squat for 5 or 6; the pull frames and the wrists in both facings.
  - **`AllocationBudgetTest.heroDrawPathInBothFacingsAndOnTheLadder`:** with the pull frames, 0.0 and 0.6 B/frame in
    two runs (ceiling 8).
  - **Known limit:** jumping while carrying still draws the core jump frame, so facing right the object rides in the
    raised sword hand for the jump.
- **Not verified:** the hero in the running game, by the author's eye (desktop and web):
  [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368).

## D80. Tree branches: one-way perches and rotten branches (#349, playtest F13, 2026-11-16)

- **Superseded in part:** the tree room's "no enemy, no death" is superseded for the beetle by D100 (#327, 2026-11-18): a
  beetle at the sound tree's foot makes standing in its burst the room's one death; the branch rules below stand. **The beetle was
  removed again by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (2026-11-19, D105, D107): the room is forest-1's one `reward` room and nothing in it kills.**
- **Status:** Accepted by the author, 2026-11-16 (the Phase A card and placement: a non-lethal break, a decor trunk, no
  coin, the room at the end of `forest-1`). Built; playtest F13 in [PLAYTEST_1.md](PLAYTEST_1.md).
- **Decision:** a tree is climbed by jumping up through one-way branches: a sound branch is a `Collisions` `Platform`
  with `skin=branch` (one 32x16 segment; `mirror` draws it on the left of its trunk). A rotten branch is the `branch` skin
  of `FallingPlatform`: VISIBLE at rest (D71: dead grey wood, a split with a pale splinter mid-top, one dry leaf), with the
  log's frames (crumble1, crumble2) and the shared `TrapWarnings.FALLING_PLATFORM_SECONDS` warning. Behaviour is the
  existing platform's: nothing new in the physics. The trunk is a `Decor` kind (`trunk`, one frame, decor tier); each kind now says explicitly whether it `brushes` (the bush) beside `still` (the look-alike), and the trunk does neither.
- **Elements (D72):** the rotten branch is a reskin of `crumble-support`; the new element is the climb, `branch-climb`
  (a skill). Its room develops crumble-support: on a climb a break costs height, not life, and the escape is upward.
- **Placement, checked at load** (`FallingPlatform.branchProblem`, mirrored in `tools/check_tiled_links.py`):
  - **D26:** a fallen branch stays fallen until death or a healer revisit, and a fall on a climb does not kill, so a rotten
    branch is never the only way up: the room's exit or checkpoint must be reachable from its entrance over plain blocks
    and one-way platforms alone (each step at most 48 px up and 32 px across; ladders, lifts and other falling platforms
    are not counted).
  - **D69:** no one-way platform lies under a rotten branch within its 200 px fall (its span widened by 16 px each side),
    so a sinking branch never carries the hero onto a sound one. D69's floor guard then stands him on the floor line. The
    boundary is pinned on both sides: 16 px beside passes, 15 px fails, 200 px down fails, 201 px passes
    (`TreeClimbTest`, `test_check_tiled_links.py`). The shipped room sits exactly on it (the crown and the zig-zag start
    16 px right of the rotten column).
  - **What the D26 check does not model:** it is a coarse reachability over top edges, not a movement simulation. Every
    plain block counts as a step (a wall's top or a ceiling slab too), and nothing blocks a jump: no ceiling, wall or block
    in the way, no head room, no run-up. It can therefore pass a room the hero cannot climb; it only catches a rotten
    branch that is the sole support in reach. A room's D26 claim rests on its route test (`TreeClimbTest` climbs the sure
    line with real input with every combination of rotten branches gone), not on this check.
  - **Two copies of the rules:** the check exists in Java (`FallingPlatform.branchProblem`, at load) and in Python
    (`branch_problems` in `tools/check_tiled_links.py`, for maps), as every placement rule here does. The Python side
    reads the numbers from the Java class, but the logic is written twice, so the two must change together; both have
    the same boundary tests.
- **Reads apart from `FallingBranch`:** underfoot versus overhead, dead grey versus tan with violet buds, a mid-top split
  versus the pale cracked band at the joint (#361). From the plain falling platform (a striped slab) and the log (brown, intact
  until landed on): by material; the crumble frames are shared on purpose, because it is the same element.
- **Room:** `forest-1-tree` (forest-1's fourth and last room; the guard room's checkpoint became its `RoomExit` at the
  same x, so D68's times are unchanged). Two trees: a column of five rotten branches next to the entrance (the fast line)
  and a zig-zag of five sound branches that starts on the far side (the sure line), meeting at a crown branch (y 32) that
  holds the level `Checkpoint`. No enemy, no lethal trap, no coin, no hint.
- **Measured** (`TreeClimbTest`, scripted hero, real keyboard input; an author check, not a novice playtest): fast line
  4.95 s, sure line 6.23 s, so the rotten column saves 1.28 s. A first layout, with the zig-zag starting on the near side,
  saved only 0.27 s, so the zig-zag was moved to start on the far side, as the Phase A card said. Stopping on a rotten
  branch and riding it down: 7.72 s (lowest branch) to 11.05 s (top branch) in total, so the worst fall costs 6.10 s.
  The hero never dies; the sure line reaches the crown with any of the 32 subsets of rotten branches gone. A rotten
  branch falls one frame after the shared 1.0 s warning; rising through it from below starts nothing.
- **Allocation (5 full-suite runs of `:core:test` and `:core:check`, with the `CollideBody` index loops below):**
  `AllocationBudgetTest.forest1TreeRoom` 537.8 to 897.8 B/frame (ceiling 990) and
  `forest1TreeRoomRidingARottenBranchDown` 611.3 to 923.3 (ceiling 1,020); each ceiling is the highest full-suite run
  plus about 10 %, the whole-room convention of the route and guard rooms; the cost is the harness and level code, as the
  other room scenarios. The value moves with JIT state together with the other room scenes (the 897.8 runs had the
  route room at 897.9 and the guard room at 906.2); a first ceiling of 880, set from the first 3 runs, failed the next 2. Before the index loops they measured 713.9 (881.7 in the full suite) and 1,050.9. A control run
  then, with the hero stood on a sound branch and nothing falling, measured 1,025.8, so the warning, the fall and the
  floor guard add about 25 B/frame: the shared warning light spawned at each trigger, as the log's is. No new per-frame
  code: the skin is chosen in the constructor.
- **`CollideBody` index loops:** the hero's and the mobs' collision passes iterated `level.collisionBlocks`, `escalators`
  and `fallingPlatforms` with for-each, which AGENTS.md forbids in per-frame code (the iterators D69 noted that
  `check_allocations.py` does not see). They are index loops now (behaviour unchanged, same order and `break`s).
  `checkHorizontalCollisions` takes a `List<CollisionBlock>` instead of an `Iterable`, so it can index; every caller
  already passed a list (`level.collisionBlocks`, and `List.of(...)` in `OneWayPlatformTest`). The change stays on its
  own merit: it removes those iterators, and the whole-room scenes allocate less (forest-1 room 1 1,322 to 938 B/frame,
  the tree room 881.7 to at most 801.8 in the full suite). No ceiling was lowered or raised for it.
- **Known intermittent failure:** `ridingARunningEscalatorCostsNoMoreThanStandingStill`, fixed by D83 (#391).
- **Changed existing tests (no threshold relaxed):** `RoomTest` (four forest-1 rooms, the guard room's exit, the
  checkpoint in room 4), `RoomCeilingTest` (four forest-1 rooms), `RoomCardsTest` (13 cards), `Forest1CombatTest` (the
  guard room ends at its `RoomExit`, at the checkpoint's x: the same three route times), `DecorTest` (the `trunk` kind;
  the bush count skips kinds that do not brush), `SpriteSheetContractsTest` (three new sheets), `test_check_tiled_links.py`
  (the decor kind set includes `trunk`; it pins that set and was missed in the first run, since `:core:check` does not run
  the tools' tests).
- **Not verified:** the look and the read distance in the running game, desktop and web
  ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)); first learning
  ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)). The jump height the layout relies on (about 58 px,
  48 px steps) is measured only at the 60 Hz step of the tests: gravity is added per physics step.

## D81. The silver thread pulls Edgard back on every death; the healer speaks on the first (playtest F7, F9, #341, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (the Phase A proposal as recommended: timeline, look, line A, legacy
  saves told, the pluck). Built; playtest F7 and F9 in [PLAYTEST_1.md](PLAYTEST_1.md). Amends
  [D3](#d3-death-resets-the-current-room-and-respawn-is-instant)'s respawn presentation; D3's room reset is unchanged.
- **Decision:**
  - **Every death with a visible cause shows the silver thread** of [#228](STORY.md): it draws out from Edgard's wrist
    toward the room start (0.10 to 0.30 s after the killing frame, hanging 16 px), tightens while a glint runs from
    the start to the wrist (0.30 to 0.45 s), and pulls (from 0.45 s: the hero is drawn up to 10 px toward the start and
    2 px up; his box stays). The world keeps its ×0.2 slow motion until black, so the cause stays live. A death fade
    (`RoomFade`, its 0.25 s halves) starts as the thread goes taut and reaches black at 0.70 s, where the slow motion
    used to end; the room is rebuilt there and the camera snaps to the start (no pan). Edgard lands at the start with
    the thread slack under the 0.25 s fade-in (the landing pose for 0.20 s, then idle; the slack thread dissolves
    from its far end while the landing pose is drawn) and takes input on the first frame after it.
  - **A fall into a pit** (nothing to see, D3) keeps its instant rebuild and 0.35 s camera pan; the 0.40 s appear clip is
    replaced by the landing pose (0.25 s) and idle (0.15 s), with the slack thread dissolving over the landing pose.
  - **Death to control** (first frame the hero moves with Right held, real `KimeriaGame` frame code, 60 Hz,
    `KimeriaGameDeathTimingTest`): before, a hit took **67 frames, 1.117 s** (0.70 s slow motion, then the 0.40 s
    appear clip under a 0.35 s pan; the same on forest-0, -1 and -2, 1.111 s at 144 Hz, 1.133 s at 30 Hz) and a pit
    **25 frames, 0.417 s**; there was no fade. Now a hit takes **58 frames, 0.967 s** (also at 144 Hz, within a frame) and
    a pit **25 frames, 0.417 s**. Both are pinned; neither may grow without a new decision (D3 fast retry).
  - **Trade-off, accepted:** the cause of death is fully visible for 0.45 s instead of 0.70 s; the fade dims it to black
    by 0.70 s (readable to about 0.55 s).
  - **The look (D76 active tier):** 2 px, silver `d8dee9` over ink `0b0d18`, and the D78 glint star, authored in
    `art/objects/silver-thread.grid` and exported exactly by `tools/silver_thread_sprites.py`. The D78 dialogue silvers
    (`9aa3b6` over `5d6577`) measured 0.104 on the forest sky, and `d8dee9` over `5d6577` 0.196; with the ink, the worst
    outline over every RoomStart of forest-0, -1 and -2, four thread shapes (taut and slack, level and sloped) and eight
    sky offsets is **0.340** (forest-0's lever room, with the landing wrist as the anchor; floor 0.28), pinned by `tools/test_silver_thread_sprites.py`. The dialogue thread keeps
    its own palette: it sits on the walnut page, not the sky.
  - **A deliberate, argued exception to principle 6** ("nothing procedural stands in for an object",
    [STYLE_SYSTEM.md](STYLE_SYSTEM.md)): the thread is not an object sprite but a line between two moving points (the
    wrist of a falling hero and the room start), whose length and slope change with every death; a fixed sprite cannot
    carry that. What is computed is only the curve: a chord with a parabolic sag, stamped one pixel step at a time with
    the authored 1x2 strand (`effects.SilverThread`), so it stays native pixel art in authored colours with no rotation,
    scaling or blending. Nothing else is procedural; the hero's poses stay authored frames. `check_sprite_art.py` guards
    the strand image like any world sprite; no gate exemption is used.
  - **The sag stops at the floor** (review of #341): while the thread draws out, its 16 px sag never takes it below the
    higher of the two floor lines (the hero's and the start's); where the chord itself is lower (a death below the start),
    the thread follows the chord. Without it, a death 48 px below the start hung the thread up to 27 px under the
    start's floor line (`SilverThreadTest.aDeathBelowTheStartNeverSagsUnderTheStartsFloor`, which fails with the clamp
    off). The slack thread after the pull is a drape that meets the floor at its lowest point and a coil lying on it,
    on the tied wrist's side of the body (the side is taken from the wrist's position against the body's centre; it is
    behind him, since he always lands facing right), and it is drawn only with the landing frame (0.20 s after a pull,
    0.25 s after a pit), dissolving over it. The
    web build showed both earlier versions wrong (2026-11-17): a drape always to the right crossed his legs, and a
    slack thread that outlived the landing frame hung in the air where the hand no longer was. `SilverThreadTest` checks
    the side, the dissolve and the lifetime on both paths (`arriveUnderThread`, `arriveAfterReset`); either old
    behaviour fails it.
  - **Facing and poses:** the pull frames of [#343](https://github.com/jlogicgames/edgard_in_kimeria/issues/343)'s
    second set (D79), drawn through `Player.showPull`: the tug from 0.10 s, the lift from 0.45 s, the land after a
    respawn (0.20 s under the fade-in, 0.25 s after a pit), `NO_PULL` after. The thread is tied to the **left** wrist in
    both facings and the frames are drawn yanked backward by it, so the pull faces **away** from the start: a start on
    the left (a smaller X) turns him to face right, one on the right to face left; the turn happens when the thread
    appears (0.10 s), so the cause of death is seen in his own facing first. The thread starts at
    `Player.pullWrist` (the wrist pixel's centre in world coordinates: tug and lift (17, 44) facing right, (46, 44)
    facing left; land (23, 52) and (24, 52), frame pixels) and ends at the landing frame's left wrist at the room start;
    `SilverThread` maps points to pixels by floor, since they are pixel centres. `SilverThreadTest` checks, in both
    facings, that the first stamp lies on an opaque pixel of the frame actually drawn. A death within 32 px of the start
    sends the thread straight up out of the view, to the healer's end, without a turn.
  - **Sound:** a plucked "tinn" as the thread goes taut (`thread.wav`, `THREAD_VOICE` in `tools/audio/generate_sounds.py`,
    seed 2701, RMS -19.0 dBFS, 0.22 s), a consequence sound after the hit's voice (principle 4).
  - **The first death of a New Game run** opens one healer line on the dialogue screen (D78), `HEALER_FIRST_DEATH`, at the
    black of the death fade (a pit: on the rebuild frame); `Next >` closes it into the fade-in. EN, approved: "Edgard, the
    night swallowed you, so I drew the silver thread, and you rise again at the path's edge. Go on." It takes 2 of the
    text box's 4 lines in all five languages. No reply from Edgard.
  - **Once per run, saved at once:** `Settings.KEY_FIRST_DEATH_TOLD` is written false when a New Game starts and true
    when the line opens (consumed on opening, like the tales), not with the level save, because a death can come after
    Continue and before the next level is completed. Continue reads it; a save without the key (written before this
    change) counts as told; New Game and the end of a run (`SavedProgress.clear`) remove it. Never on a later death,
    Restart Level or a collection return (neither is a death). This is the one persisted narrative flag; it adds no
    checkpoint, healing or persistence mechanic ([STORY.md](STORY.md#why-edgard-returns-after-every-fall--accepted-2026-11-10-228)).
  - The respawn-beam candidate of the effects plan ([#402](https://github.com/jlogicgames/edgard_in_kimeria/issues/402))
    is to be checked against this decision before it lands, as #341 asked; the thread is the respawn effect.
- **Why:** playtest F7 and F9: the thread was told twice and never shown (principle 5); the animation fits inside the
  old retry cost (D3, principle 2) by replacing the camera pan and the appear clip, which said nothing.
- **Allocation (`AllocationBudgetTest.silverThreadBeat`, 3 runs):** the thread beat's draw path through the real
  `Player`, every stage (draw-out, tighten with the glint, pull, landing with the slack thread, pit arrival), with the
  #343 pull frames drawn through `showPull`, **0.6 to 0.7 B/frame** (2026-11-17, 3 runs; 0.0 before #343, when the
  beat drew fixed regions; the hero's own draw path, `heroDrawPathInBothFacingsAndOnTheLadder`, measures the same 0.6, and
  the thread adds no per-frame `new`); a dying hero's update, **0.0 to 0.1 B/frame** once the 40.0 B/frame of the harness's `Proxy` stubs are taken
  off (a boxed frame time and the pause key's argument array; the real backends allocate neither); ceiling 8. The
  game's per-frame additions are field reads and one `Runnable` held in a field (`rebuildUnderBlack`).
- **Changed existing tests (no threshold relaxed):** `BossCombatTest` asserted `Player.State.APPEARING` after a pit
  respawn. That clip no longer plays on a respawn: D81 replaces it with the landing (respawn phase `ARRIVING`, the
  landing pose drawn over the `IDLE` state, `Player.drawsLandingPose`), which keeps the clip's 0.40 s. The test now asserts
  exactly that state, the same 26 frames, then phase `NONE`, no landing pose and `canFight`; it is no looser than the old
  state check (a first rewrite to a bare `!canFight()` was, and the review sent it back). It also called the
  no-argument `rebuildRoom`, now `rebuildRoom(false)` (the pit path).
- **Tests added:** `KimeriaGameDeathTimingTest` (death to control at 60 and 144 Hz, the rebuild at black, the
  once-per-run rule, including that a collection return from the pre-boss healer opens no line and leaves it untold);
  `HealerDialogueTest.theFirstDeathLineTakesTwoOfTheFourLinesInEveryLanguage`; `SilverThreadTest` (where the thread is
  drawn, from the real batch calls); `AllocationBudgetTest.silverThreadBeat`; `tools/test_silver_thread_sprites.py`.
- **Verified (playtest 2, 2026-11-21, web build; [PLAYTEST_2.md](PLAYTEST_2.md)):** the thread pull's look on the web; the testers raised nothing, which the author accepted as the check.
- **Not verified:** the pluck on a real output and the beat on desktop, by the author's eye and ear:
  [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368). Native-speaker review of the uk, pl, de
  and be line: [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369).

## D82. The fairy helper shows the interaction target instead of the corner brackets (playtest F4, F14, #338, 2026-11-16)

- **Status:** Accepted by the author, 2026-11-16 (Phase A proposal: glow and glints, the text as the third gift, the
  one-time appear twinkle, her look, this number; D81 is left to #341). Built.
- **Story:** the healer gives Edgard a fairy of the wood, one of the little lights the night could not put out, as his
  third gift after the silver thread and the spark of fire ([STORY.md](STORY.md#the-fairy-helper--accepted-2026-11-16-338)).
  She is narrative and feedback only: no attack, no pickup, no collision, no save field.
- **First appearance:** when the forest-0 start tale closes, she arrives at Edgard's shoulder in a one-time 0.4 s
  twinkle (`FairyGuide.arrive`, called by `KimeriaGame` on the tale's CONTINUE). After that, and in every other level,
  on Continue, after death and on Restart Level, she is simply there. The first-death line (D81) closes through the
  same CONTINUE case; the call is guarded to the start tale, so closing that line does not replay the twinkle
  (`KimeriaGameDeathTimingTest.closingTheFirstDeathLineDoesNotReplayTheFairysArrival`, which fails without the guard,
  and `closingTheStartTaleStartsTheFairysArrival`).
- **Idle:** frame centre beside his face, 14 px behind him (on the side away from where he faces), 16 px under his box
  top, with a 1.5 px bob over 1.6 s. World Y grows down, so that is level with his face, never above his head where
  the label is anchored. She follows with exponential smoothing (rate 10 /s).
- **Selected target** (`Player.interactionFeedbackTarget()` is non-null: Up held, Down not, a target eligible):
  - **On the frame of selection,** before she moves, an aqua glow is drawn additively behind the target at full
    strength (the target box grown by 6 px, a nine-slice of `fairy-glow.png`). Four fairy-dust glints are drawn on top
    at its corners (grown by 2 px), the diagonal pairs alternating every 0.25 s. They show even when the hero stands
    over the target. Both disappear on the frame the selection ends.
  - **She flies** (rate 14 /s, about 0.3 s over 60 px) to a point beside the target on the side away from the hero,
    with the wand tip 2 px off its upper far corner, and points (three frames, the last held). She does not cover the
    target, and she stays under the context label anchored above it.
- **Passive targets** (in reach and eligible but not selected, and the mechanisms a selected lever drives; were the
  grey brackets): the same glow at 0.3, with no glints and no fairy (`Player.collectPassiveTargets`).
- **D29 and D30 kept:**
  - The eligibility, the nearest-target rule with its ID tie-break, the single target, the label anchor and the look
    suppression are untouched, since she only reads the hero's selection.
  - Down + Action still marks nothing.
  - `DirectionalActionsTest` asserts, at every interaction-target check, that the fairy shows the same target.
- **Hidden** while the hero cannot act (death, respawn, checkpoint, victory walk): she fades out in 0.1 s, and the
  glows go. She comes back at his shoulder without a flight. During a dialogue she is frozen with the level.
- **Draw order:** the glows after the vines and before the platforms and objects (light behind, the objects stay
  crisp); the glints, her light and her frame after the hero.
- **Art** (original, authored grids `art/objects/fairy/{body,glow,glint}.grid`, exported by
  `tools/forest_trap_sprites.py fairy:<name>`):
  - `fairy-body.png`: 14 frames of 16×16 (idle 4, fly 2, point 3, the same three flipped, appear 2), 10 colours.
  - `fairy-glow.png`: 24×24, three aqua rings, used as a nine-slice.
  - `fairy-glint.png`: 2 frames of 5×5.
  - Details: [ART_DIRECTION.md](ART_DIRECTION.md#the-fairy-helper-d82-playtest-f4-f14-338).
- **Contrast (D76), all active tier:**
  - Without light, her ink contour fell to 0.187 on dark tiles. So she is drawn over a 20×20 copy of the glow at
    alpha 0.6 to 0.9 (her own light, the firefly glow); a 16×16 light at 0.6 still failed (0.227).
  - Measured by `check_contrast.py` over a sweep of every map (x every 64 px, y 40 to 328 every 48 px, two sky
    offsets), since she has no placement: frames 0.314 to 0.440, glints over the selected glow 0.336 (big) and
    0.380 (small).
  - Over forest-1's scenery layers ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236), 2026-11-20) her ink
    fell to 0.136 to 0.269 (glints 0.179, 0.215): the play band is near black and her own light lifts it toward the ink.
    Her ink went to `020106` and her top edges are lit in her lantern white `f2fff4` (up only, `RIM_STYLE` in
    `tools/forest_trap_sprites.py`; a full up-left-right rim fell to 0.035 over the moon): frames 0.314 to 0.376, glints
    0.323 and 0.377. Her ink changed (`0b0814` to `020106`) and her top edges are lit; the rest of her palette, her identity, frames and timings are unchanged; no threshold changed.
  - The glow only adds light behind a target and raised every measured target's outline (stone 0.293 to 0.686,
    lever 0.579 to 0.881).
  - The gate now takes about 5.6 s (was about 2.5 s).
- **Allocation:** `AllocationBudgetTest.fairyGuideWithPassiveGlows` (was `passiveInteractionOutlines`, the
  passive-outline scene; its ceiling constant is now `FAIRY_GUIDE_CEILING`, still 8 B/frame) measures the fairy guide
  (update and both render passes): 0.1. `Level.linkedMechanisms` returned a new list per call, every frame a lever was
  selected. It is now `linkedMechanismsInto(trigger, Array)`: `linkedMechanismsOfASelectedLever` measures 0.0 under
  the same ceiling.
- **Removed:** `InteractionHighlight` and its `check_sprite_art.py` permission (`corners`, white quad).
  `test_sprite_art_guard.py` now checks the same "UI geometry versus object art" distinction on the menu panel's
  permission, and that the old permission is gone. No threshold changed.
- **Text:** the fairy words of `HEALER_TALE_FAIRIES`, `HEALER_TALE_LIFT` and `HEALER_TALE_BLESSING` changed, and
  `HEALER_TALE_FAIRY` was added after the fire gift (the start tale has 9 lines; see STORY.md). The rest of those
  lines is [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370)'s.
- **Changed existing tests (no threshold relaxed):**
  - `DirectionalActionsTest` gains the fairy assertions and five fairy tests (the instant glow, hiding and the
    twinkle, the call order, following the hero, and the point clip restarting when the target changes where she
    already hovers).
  - `LinkedMechanismTest` uses `linkedMechanismsInto` and checks each mechanism appears once.
  - `HealerDialogueTest` and `CampaignFlowTest` expect 9 start-tale lines: still at most two presses a line, now 18
    presses; the speakers are `HEHHHHHHE`.
  - `test_sprite_art_guard.py`: see "Removed" above.
- **Update ([D87](#d87-the-fairy-points-at-a-linked-mechanism-that-is-off-screen-and-the-pulled-levers-mechanisms-stay-marked-419-2026-11-17), #419):** a linked mechanism outside the camera view sends her to the screen edge in its direction to twinkle, and the pulled lever's mechanisms keep a clear glow for 1.5 s after the pull.
- **Not verified:** the look, the read distance and the timing in the running game, desktop, web and touch
  ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)); the native-speaker quality of the uk, pl, de and be lines ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369)).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the fairy's aqua glow is replaced by a softer pixelfx effect; this decision was revised by issue [#544](https://github.com/jlogicgames/edgard_in_kimeria/issues/544) (D124). Approved 2026-11-24 and built (D132): on the dialogue screen she flies from the healer's portrait to Edgard's ([#539](https://github.com/jlogicgames/edgard_in_kimeria/issues/539)).

## D83. The rider-path allocation test is measured as a median of paired windows; the per-frame for-each and stream are gone (#391, 2026-11-17)

- **Problem:** `AllocationBudgetTest.ridingARunningEscalatorCostsNoMoreThanStandingStill` compared two single windows of
  about 870 B/frame each against an 8 B ceiling on the difference, and failed at random: on main (4ac6ed1) 2 of 40
  isolated runs, 18 of 40 with the machine loaded (differences up to 311 B/frame).
- **Cause (two parts):** (1) `Player.update` really allocated: for-each iterators in `selectedMechanism`, `ladderEntry`,
  `checkGameplayOverlaps` and the carry and refill searches, and a stream in `Level.touchesDeadline`. HotSpot's C2 removes
  about 310 B of that at an arbitrary moment (also with escape analysis off), the interpreter and C1 do not, and TeaVM has
  no C2; so the riding window and the standing window could sit in different JIT states. (2) The rest, 536 B/frame, is the
  headless harness's `Gdx.input` and `Gdx.graphics` proxy stubs.
- **Code change:** index loops in `Player` (`selectedMechanism`, `ladderEntry`, `checkGameplayOverlaps`,
  `selectedCarryable`, `fullFireballRefill`) and `Level.touchesDeadline`. Measured at C1 (stable): `Player.update` standing
  872 to 536 B/frame, forest-1 route room 897.8 to 561.9, tree room 897.7 to 561.8. No ceiling was changed.
- **Measurement change:** `AllocationProbe.medianPairedDifference` warms both states up twice, then takes 15 pairs of
  300-frame windows alternately (riding then standing, standing then riding) and the test asserts the **median** of the
  differences. A JIT change lands in one pair; a real difference shows in every pair. The ceiling stays **8 B**
  (`RIDER_PATH_CEILING`).
- **Verified:** 0 of 40 failures isolated and 0 of 40 with 20 busy processes (before 2 and 18); a deliberate 24 B
  allocation in the rider branch gives a median of 24.0 and fails 10 of 10, isolated and loaded. The ordinary median
  difference (no injection) was 0.0 B/frame in all 80 runs (min 0.0, max 0.0, printed to 0.1 B), so the margin under the
  8 B ceiling is the whole 8 B.

## D84. A puzzle never makes the player guess or look stupid (playtest, #418, #419, 2026-11-17)

- **Status:** the one-sentence principle and the two applications (#418, #419) are the author's, 2026-11-17. The four
  rules and the checks below are the agent's reading of that sentence, which the author kept after the play-through of 2026-11-17 (a general confirmation, "everything is clearly visible, everything is done", not an answer point by point). The
  applications are the issues
  [#418](https://github.com/jlogicgames/edgard_in_kimeria/issues/418) (built, [D86](#d86-forest-1-gets-a-lever-lesson-room-one-lever-a-ladder-and-a-lift-in-opposite-states-418-2026-11-17):
  automated tests, then the author's play-through of 2026-11-17) and
  [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419) (built, see D87).
- **Decision:** the author's words (translated from Russian): "Don't torment the player with guesswork; a puzzle must not
  make the player look stupid." Agent's reading, for room and mechanism design, as four rules:
  1. **Teach before gating.** Every new mechanic, and every new combination of known ones (for example one control that
     drives several mechanisms), is shown first in a safe place, where failing costs nothing, before it stands between the
     player and progress.
  2. **Cause and effect are visible.** The effect of every player action is visible at the moment it happens. If the
     effect lands off screen, the game points at it (the fairy of D82 is the tool, extended by [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419) to off-screen targets).
  3. **A stuck player can see what to try next.** From any reachable state the player can see something they have not
     tried yet: a lever not pulled, a mechanism that reacted, a path that changed. A room that looks impassable while a
     solution exists is a defect of the room, not of the player.
  4. **Difficulty is execution and planning.** Challenge comes from timing, precision, route choice and resource use
     (consistent with D26), never from a hidden rule or an unreadable state.
- **Why:** a puzzle that is solved by trying everything is not a puzzle, and a player who finds the answer by luck learns
  nothing. Worse, a failure the player cannot explain reads as the player's own stupidity (agent's gloss).
- **Relation to earlier rules:** it extends, and does not replace:
  - [D7](#d7-traps-are-deterministic-puzzle-elements): a trap is deterministic and warns before it kills, so its
    telegraph is the trap form of rule 2.
  - [D26](#d26-observe-and-plan-before-committing): observe, plan, then commit; D84 asks that what the player observes
    is enough to plan with.
  - [D31](#d31-forest-uses-linked-route-reconfiguration): a lever's effect is a visible link; D84 requires the link to be
    seen when it fires, whatever the number of links.
  - [D72](#d72-every-room-gives-the-player-something-new): each new element is first met alone, then combined; D84 makes
    the same demand of a new combination of known elements, which D72 counts as a combination but does not require to be
    shown on its own.
  - [D82](#d82-the-fairy-helper-shows-the-interaction-target-instead-of-the-corner-brackets-playtest-f4-f14-338-2026-11-16):
    the fairy points at the selected target; D84 extends that to a linked mechanism that is off screen.
- **How to apply (checks for a room, a mechanism or a review):**
  - List every control that drives more than one mechanism, and every combination of mechanics the room asks for. For each,
    name the earlier safe place where the player met it, or add one.
  - For each player action, name the frame where its effect is seen and whether it is on screen. If not, say how the game
    points at it.
  - From each stuck state the room can reach, name what the player can try next and where they see it.
  - Agent proposal: if the only way to find the answer is to try everything, the room fails this decision, whatever its measured time.
  - A room card records the check; see the kimeria-room-design skill.
- **Case that broke the principle:** the `forest-1-route` room ([D67](#d67-forest-1-grows-a-route-room-one-lever-two-supports-in-opposite-states-and-a-one-shot-step-to-the-refill-187-2026-11-15)),
  playtest 2026-11-17. The `Route` lever is the only lever in the game that drives two mechanisms (it lowers the ladder and
  stops the lift). Nothing taught that earlier, and after the pull only the ladder visibly reacted, so the room looked
  impassable and the author got stuck, although the lift was the way on.
- **Applications (first two):**
  - [#418](https://github.com/jlogicgames/edgard_in_kimeria/issues/418): a teaching room for a lever that drives two
    mechanisms, placed in forest-1 between `forest-1-traps` and `forest-1-route` (rule 1). Built as `forest-1-lever`
    ([D86](#d86-forest-1-gets-a-lever-lesson-room-one-lever-a-ladder-and-a-lift-in-opposite-states-418-2026-11-17)).
  - [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419): the fairy guide (D82) flies to the screen edge and
    twinkles toward a linked mechanism that is off screen (rule 2). Built by [D87](#d87-the-fairy-points-at-a-linked-mechanism-that-is-off-screen-and-the-pulled-levers-mechanisms-stay-marked-419-2026-11-17).
  - #418 and #419 are built; the author's play-through of 2026-11-17 is recorded in D86 and D87. When one lands, its PR updates this list and the document it changes.
- **Details:** playtest of 2026-11-17 (the forest-1 route room); [GAME_DESIGN.md](GAME_DESIGN.md#intent-and-player-experience),
  [GAME_DESIGN.md](GAME_DESIGN.md#forest-linked-route-reconfiguration).

## D85. forest-1 opens with the first RedMob alone, in a room of its own (#375, 2026-11-17)

- **Status:** Implemented by [#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375), cards approved by the author
  on 2026-11-17. Automated tests only: nobody has played the new room. Revises D58 ("not split") and D67's room list.
- **Revised by [D105](#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) (#499), done by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (2026-11-19):** a room of its own for the first RedMob is the "met alone" reading D105 drops; `forest-1-mob` now holds a log that stops the mob (the pocket), a falling branch and an exploding stump (ROOMS.md, "Reworked under D105, batch 2").
- **Problem (D72 audit, #351):** `forest-1-traps` introduced four elements (redmob, running-platform, fireball-refill,
  puffball), and the first RedMob was met in the stretch x 256-450 together with the running escalators and the refill. The
  lever-and-torch pair (ids 12, 13) repeated `forest-0-lever` with no role.
- **Decision:** a new room, `forest-1-mob`, is played first. It lies after the tree room on the map (x 3520-4320, 50 tile
  columns appended; map width 220 to 270 tiles) and is first in the `Rooms` list, which sets the play order (`RoomLayout`),
  so no existing object, collision or tile moved. It holds the RedMob id 11 (moved from x 352 to x 3776, y 288, range
  `offNeg`/`offPos` 3 tiles instead of 10) and the FireballRefill id 25 (moved from x 312, y 176 to x 4208, y 288; identity
  kept), with RoomStart (3552, 272) and RoomExit (4256, 272, 32 x 48). The `Player` object moved to the new RoomStart;
  the `Deadline` grew to cover the new room. The lever-and-torch pair is removed. `forest-1` has five rooms.
- **Met alone:** the camera centres on the hero and clamps to the room, so the refill stays off screen while the mob is alive
  and the hero's centre is in its range; `Forest1MobTest` checks that frame by frame on a real walk. In `forest-1-traps` the
  escalators (x 256-464) are now met with no other new element in view; the puffball (x 944) enters the screen after them.
- **Not done, and why:** the trap course was not cut further. Cutting it at the wall would put the new entrance inside the
  tuft's 48 px clearance and would need the tuft moved; the author kept the 12.73 s retry D58 accepted.
- **Measured (scripted hero, real input, `RoomReplayTest`):** `forest-1-mob` 7.43 s required with zero charges (the sword
  takes the mob), 6.97 s to the refill with two charges, three identical attempts each; `forest-1-traps` 12.27 s, the same
  as before (the removed lever and mob cost no time on the scripted route), 12.73 s retry at the log, 14.98 s with the
  longer-warnings assist. Budget 20 s. Map: `forest-1.tmx` SHA-256 `c5acdc5a30bac873735246ccda12cdd5edbcf5ea1dc96a238d9cd0f7058ec9b8`.
- **Test changes, stated plainly:** the tests that build `forest-1` and step into a later room take one more
  `enterNextRoom` (`Forest1TrapsTest`, `Forest1RouteTest`, `Forest1CombatTest`, `TreeClimbTest`,
  `FallingPlatformFloorTest`, `RoomReplayTest`), and `AllocationBudgetTest` room indices shift by one; the room count in
  `RoomTest` and `RoomCardsTest` goes from 4 to 5 for forest-1 (13 to 14 rooms in all). `Forest1TrapsTest` now expects one
  RedMob in the trap course (the first one moved). `RoomReplayTest.secondLevelRefillCanBeCollectedByJumpingWithLiveHazards`
  now walks the new room to the refill and is renamed `secondLevelRefillIsCollectedAfterTheFirstRedMob`. New: `Forest1MobTest`, `RoomReplayTest.forestOneMobRoomRequiredRouteFitsTwentySeconds`,
  `AllocationBudgetTest.forest1MobRoom`. Allocation ceilings, each the highest of 3 isolated runs plus about 10 %:
  `FOREST_1_MOB_CEILING` **620** (561.8, 561.8, 345.8 B/frame); `FOREST_1_CEILING` (the trap course) lowered from 1450 to
  **665** (601.8, 602.3, 290.3 B/frame; the old ceiling was set when the room held the torch, 1,322 B/frame). Both tighten
  the gate. No threshold or tolerance was relaxed.
- **Checked in the running game** (camera, wall tiles, the refill's place by eye) by playtest 2 and the author, 2026-11-24
  ([#422](https://github.com/jlogicgames/edgard_in_kimeria/issues/422)). Not verified: novice playtests (#135).

## D86. forest-1 gets a lever lesson room: one lever, a ladder and a lift in opposite states (#418, 2026-11-17)

- **Status:** Built by [#418](https://github.com/jlogicgames/edgard_in_kimeria/issues/418) as the first application of [D84](#d84-a-puzzle-never-makes-the-player-guess-or-look-stupid-playtest-418-419-2026-11-17).
  Automated tests, then the author's play-through of 2026-11-17 (see the note below); the Chrome agent check and the clean-session review were done before the merge. The split below says which details
  are the author's decision and which are the agent's; the agent's part is which the author kept after the play-through of 2026-11-17 (a general confirmation, "everything is clearly visible, everything is done", not an answer point by point).
- **Author's decision (2026-11-17):** add a short teaching room in `forest-1` between `forest-1-traps` and `forest-1-route`:
  one lever, two mechanisms next to it, both in view at the same time as the lever, a mistake that costs nothing; it
  follows D84 (no guesswork) and D72 (room card first); no hint text (forest-1 shows none, D33); existing objects and art only.
- **Agent's design (details to confirm), as built (docs/ROOMS.md, "forest-1 lever room"; card in ROOM_CARDS.md):**
  - **The room:** `forest-1-lever`, 640 px wide (one camera view: the whole room is on screen wherever the hero stands), the level's room 3 (after the first-RedMob room and the trap course).
    A running vertical lift beside a floating slab that holds the lever; a ladder hanging in the air to the right over a
    112 px ledge that holds the exit. One `Trigger` linked by name (`Lesson`) to the `Ladder` and the `Escalator`, as the route
    room's `Route`: the same combination, the same authored state (ladder raised, lift running), the same pull (ladder down,
    lift stopped; pull again, ladder up, lift running).
  - **Why this teaches the link:** the way on needs both mechanisms in turn (the lift is the only way to the lever, the ladder
    the only way to the exit) and the lever changes both, so the player pulls it with the lift 80 px away (it just carried the
    hero there and now stops) and the ladder 192 px away (it comes down), both inside one view. The other designs weighed:
    a lever on the floor with the lift required running and the ladder required lowered (the pull stops the lift anywhere, so the
    way back depends on when the hero pulled, a timing puzzle in a teaching room); a lift whose stop matters (the same);
    a lever on a ledge with a thick block under it (a soft-lock, found by the sweep, see below).
  - **D84 check (the four rules):** (1) teach before gating: the combination is met here, with nothing at stake, before the
    route room gates progress with it; (2) the effect of the pull is on screen at once (one-screen room, both mechanisms at most 192 px away);
    (3) a stuck player always sees something untried: the lever, the running lift or the hanging ladder (the room has no state without one);
    (4) the difficulty is execution only: boarding a moving lift (the route room's own skill).
  - **A wrong pull costs nothing:** no enemy, hazard, pit, pickup or one-shot object; the lever is a reversible toggle; the floor is unbroken.
  - **D26:** from every reachable state the exit stays reachable with zero charges and no Restart Level. The first geometry
    (a slab solid down to the floor) had a soft-lock that the sweep found: a hero who pulled and dropped left of the slab, with the lift stopped out of
    reach, had no way back to the lever. The floating slab (the floor runs under it) removes it: with the ladder down it reaches the floor, with the ladder raised
    the lift runs and is boarded from the floor. 312 lever-timing, pull-count and drop runs and every floor position in both states reach the exit.
  - **D72 (card first):** the room introduces nothing; it develops `lever` (one lever now drives two mechanisms) and combines
    `ladder + running-platform`, all met before it; role `lesson`. The route room's card now says the combination was first taught here.
  - **Measured:** the required route 8.43 s (scripted hero, real input), inside the 20 s target.
- **Placement (no shift, follows #425's precedent):** the room is authored after the end of the map, x 4320-4960 (the map grows from 270 to 310 tiles, the `Deadline` from 6176 to 6816 px, `nextobjectid` 115), and listed in the `Rooms` list between `forest-1-traps` and `forest-1-route`; the list, not x, sets the play order (mob, traps, lever, route, guard, tree), as D85 (#375) did for `forest-1-mob`. So no existing object, collision, tile or test coordinate moves, and no float-resolution side effect on the measured times. Transitions rely only on what the mob-to-traps transition already relies on: the target room's `RoomStart`, the camera clamped to the room's bounds and the room's end walls (`Forest1LeverLessonTest` pins the hero's start and the camera for traps to lever and lever to route). An earlier version of this branch inserted the room at x 1280 and shifted everything after it 640 px; it was dropped for this. Checked by script against origin/main: the first 270 tile columns and every old object are identical (only the `Deadline` width changed); the new objects use ids 104-114 (D85 took 98-103).
- **Test changes (no tolerance or threshold relaxed):**
  - `Forest1RouteTest`, `Forest1CombatTest`, `TreeClimbTest`: one more room entered and the room-chain assertions name the lever room; no coordinate changed.
  - **No existing room's measured time moved** (route 11.02 s and 18.32 s, mob 7.43 s, guard 6.83 s, 6.15 s and 7.67 s, tree 4.95 s and 6.23 s, trap course 12.27 s); the route test's swing policy is unchanged.
  - `RoomCeilingTest`: the generic wall sweep skips the lever room's right wall (a hero put on the floor there starts inside the 112 px exit ledge); the dedicated test sweeps it from the ledge's top. Forest-1 has six rooms.
  - `RoomTest`, `RoomCardsTest` (16 rooms): the new room in the chain and the cards. `AllocationBudgetTest`: room indices and three lever room scenes (at rest, riding the lift, pulling the lever). Measured in 10 isolated runs (after main's #375, with the mob room first): 513.8 to 564.5 B/frame over the three scenes (pulling the lever the highest, 564.3 to 564.5; an earlier run of the rest scene measured 345.8: JIT state, D83). The ceiling `FOREST_1_LEVER_CEILING` follows D85's rule (highest measured + about 10 %): **620** (564.5 + 10 %), not the route room's 1,130 as in the first version of this branch.
- **Agent check in the running web build (Chrome, 2026-11-17, observed by the orchestrator):** the room renders as one view; the hero rode the lift up, hopped onto the slab, pulled the lever (Up + Action), dropped, climbed the lowered ladder and walked to the exit, and the route room loaded (observed on the first version of the branch, which inserted the room at x 1280 instead of appending it; the room's layout and mechanics are identical); no console errors. **Chrome check of the appended version (2026-11-17, orchestrator):** the room chain `forest-1-mob` to `forest-1-traps` to `forest-1-lever` to `forest-1-route` ran in the web build; each transition put the hero at the next room's RoomStart with the camera inside the room; no console errors. The full lever-room route was played on the first version only; on the appended version the lift boarding was not repeated (tool latency). Observation that matters: while the hero holds Up at the lever, the fairy glows the lever, the ladder **and** the lift (the in-view glow works). At the pull the glow disappears at once; the ladder visibly drops, while the lift just stops with no cue, so the lift's change at the moment of the pull is shown only by its stopping. [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419) covers this: its checklist item "Keep a cue on in-view linked mechanisms briefly after the pull".
- **Author play-through (2026-11-17):** the answer to (0) is no: the author did not notice that the lift stopped, and asked to keep the room as it is for now, so nothing in the room was changed. The questions as they were asked before the play-through: (0) **"did the player notice that the lift stopped?"**; if not, the stopped lift needs a use that does not create a timing puzzle (open for the author, no design proposed here). (a) the lift's stop is visible and reversible but not needed: nothing in the room makes the player wait for it to stop. A design where the stop
  is required was rejected as a timing puzzle in a teaching room; whether the lift's stop should matter is parked by the author ("leave it like this for now", 2026-11-17), not resolved. (b) The route room is not changed: after the pull a player there still has to notice the lift
  stopped; this room only teaches that the lever does both. Whether that is enough was left to the play-through; the author kept the room as it is. (c) No sound or visual cue was added beyond the existing lever picture (D82's fairy marks the selected target only; [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419) extends it).
- **Details:** [ROOMS.md](ROOMS.md#forest-1-lever-room-418-d86), [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-lever), `Forest1LeverLessonTest`.

## D87. The fairy points at a linked mechanism that is off screen, and the pulled lever's mechanisms stay marked (#419, 2026-11-17)

- **Status:** built by [#419](https://github.com/jlogicgames/edgard_in_kimeria/issues/419); the three author decisions below are
  from 2026-11-17, the numbers and the selection rule are the agent's design, which the author kept after the play-through of 2026-11-17 (a general confirmation, "everything is clearly visible, everything is done", not an answer point by point). Automated
  tests, then the author's play-through of 2026-11-17: the author played the whole game on desktop and confirmed that everything is clearly visible.
  Applies [D84](#d84-a-puzzle-never-makes-the-player-guess-or-look-stupid-playtest-418-419-2026-11-17) rule 2 and extends
  [D82](#d82-the-fairy-helper-shows-the-interaction-target-instead-of-the-corner-brackets-playtest-f4-f14-338-2026-11-16).
  No new HUD element ([D7](#d7-traps-are-deterministic-puzzle-elements): still no text hints).
- **Author's decisions (2026-11-17):**
  1. When a lever is selected or has just been pulled and a linked mechanism is outside the camera view, the fairy flies to
     the screen edge in that mechanism's direction and twinkles there (while the lever is selected, and for a short time
     after the pull), then returns to Edgard. No arrows, no text.
  2. The in-view linked mechanisms keep a cue for a short time after the pull, so the change on each of them is marked when it
     happens (the Chrome check showed the glow vanishing at the pull: the ladder dropped, the lift just stopped).
  3. Investigate why the lift's glow was not noticed; record the finding.
- **Design (agent's):**
  - **The marked set.** `Level.activateTrigger` ends by filling `FairyGuide.beginChanged()` with
    `linkedMechanismsInto(trigger)`: the mechanisms the pull drives, for **1.5 s** (`CHANGED_SECONDS`). Any trigger counts,
    so a lever thrown at with a stone marks them too. The set is cleared when the hero cannot act (death, checkpoint).
    1.5 s is the time to see two things: she needs about 0.4 s to fly to an edge (rate 14 /s) and about 0.4 s back, which
    leaves about 1 s of twinkle on the edge, and a lift stopping or a ladder dropping takes under 1 s to read. The
    longer-warnings assist (`TrapWarnings`, x1.5) is a trap-warning aid, not a cue timer, so it is not wired in: the cue is not a
    hazard warning and the player cannot lose by missing it.
  - **In-view marks.** A marked mechanism gets the glow at **0.8** (`CHANGED_ALPHA`; passive stays 0.3, the selected target 1.0)
    for 1.1 s, fading to 0 over the last 0.4 s (`CHANGED_FADE_SECONDS`), with the four glints on its corners. A mechanism that is
    both passive and marked is drawn once, at the stronger value. `FairyGuide.glowAlpha` reports the same number.
  - **Off-screen test.** A mechanism is off screen when its box does not overlap the camera's visible world rect (the supplier
    `KimeriaGame.visibleWorldRect` that `Level` already received for the rain, now also given to the fairy). With no view or an
    empty one (a headless test that passes none) nothing is off screen, so every existing scene behaves as before D87.
  - **Edge point.** `FairyGuide.edgePoint`: the point where the ray from the view centre to the mechanism's centre leaves the view
    inset by **16 px** (`EDGE_MARGIN`; her 16 px frame then sits 8 px inside the edge, clear of the picture border), so the direction on
    screen is the mechanism's true direction, diagonals included. World Y grows downward: a mechanism below the view has the
    larger y and gets a point on the bottom edge. She flies there at the existing target rate (14 /s) and, within 8 px of the point
    (`TWINKLE_RANGE`: the point moves while a lift runs, and her smoothing lags it by a few px), plays the existing twinkle (the two
    "appear" frames looping) with the four glints around her frame. No new art.
  - **Which one (D87 as built, one target).** The nearest to the view centre (ties: smaller x, then y) of the selected lever's mechanisms and the marked ones that
    are off screen; the one she already serves keeps the job while it stays off screen (no flip-flop between near-equal distances).
    **Superseded by the update (#434) below:** with several off screen she now cycles through them; with exactly one the rule is the
    one above, unchanged.
  - **While and after.** She goes to the edge while a lever is selected (Up held at it) and for 1.5 s (see the update below) after a pull, whether or not it
    is still selected. When no mechanism is off screen she behaves exactly as in D82 (points beside the selected target, idles at
    his shoulder). When the cue ends she flies back at the follow rate. The selection itself (`selected()`, the label, eligibility, D29,
    D30) is untouched: only her position and the glows change.
- **Finding 1, the in-view lift glow (decision 3).** The glow code is correct: while the lever is selected, the passive glow at 0.3 is
  drawn behind the in-view mechanisms (`FairyOffscreenTest`, and the Chrome check). Measured with `check_contrast.py`'s functions over
  the six lifts in the shipped maps against the sky and background tiles (the sprite's outline contrast, OKLab, worst of the sky
  offsets): the bare lift 0.365; with the 0.3 glow 0.493; with 0.8, 0.655; with 1.0, 0.666 (the active gate is 0.28). The halo itself
  over the plain background: mean lightness change 0.076 at 0.3, 0.192 at 0.8 and 0.228 at 1.0. So 0.3 is legible by the metric
  and `PASSIVE_ALPHA` is **not changed**; what the author missed was not contrast but attention: a stopping lift gives nothing to
  look at (it looks like a lift that was never moving), and the post-pull mark at 0.8 with glints is the answer.
  Not measured by eye.
- **Finding 2, the lift is off screen from the lever.** In the real route room, with the real camera rule (`Player.roomCameraTarget`),
  a hero at the lever (x 1440) has the view x 1280 to 1920 (the room's left clamp), and the lift (x 1936 to 1968) lies **16 px
  beyond the right edge**: off screen (`FairyOffscreenTest.fromTheLeverTheLiftIsOffScreenAndTheLadderIsInView...`). The camera's
  dead zone moves the focus by under 50 px, which cannot bring it in. The Chrome check of 2026-11-17 that saw the lever, the ladder and
  the lift glow was made in the lever lesson room `forest-1-lever` ([D86](#d86-forest-1-gets-a-lever-lesson-room-one-lever-a-ladder-and-a-lift-in-opposite-states-418-2026-11-17); #418: the lift 80 px from the lever, the whole room one view), not in the
  route room, so it does not contradict this: the in-view glow works, and in the route room, standing at the lever, the lift is off
  screen, which is the case this decision fixes. (In the author's original playtest screenshot of the route room the hero stood
  mid-room, where the lift was in view.) The design covers both cases (in view: the mark; out of view: the edge twinkle).
- **Found while measuring:** `Player.selectedInteraction` made a record every frame any target was selected (24 B/frame; the
  D82 fairy scene never selected one, it only had passive targets). It is now one reused instance
  (`Player.InteractionTarget`, read at once by each caller). The new scene showed 24.6 B/frame before the fix.
- **Allocation:** `AllocationBudgetTest.fairyGuidePointingOffScreenWithAPostPullGlow` (the hero at the route lever with Up held,
  the lift off screen, marks renewed every 60 frames; the fairy's update and both render passes): 0.6 to 2.0 B/frame in 3 isolated
  runs before the merge of D86 and 0.6, 0.6, 0.6 in 3 isolated runs after it (the route room is room 3 now), ceiling `FAIRY_GUIDE_CEILING` 8 B (the shared isolated-scene ceiling of the D82 and #303 fairy scenes, not D85's room rule of highest run plus 10 %, which
  applies to whole rooms; for this scene that rule would give 2.2, and the shared 8 B absorbs the JIT noise of one object per few hundred frames). The existing
  `fairyGuideWithPassiveGlows` measures 0.1 to 0.7. `HeadlessLevelHarness.roomOf` now passes one shared empty view instead of
  `Rectangle::new` (a supplier that makes a rect per call would itself allocate under the fairy). The new helpers are in
  `tools/check_allocations.py` `HOT_METHODS`.
- **Room changes.** `Level.activateRoom` (next room, retry, collection return and the dev `reloadAt`) calls the new `FairyGuide.reset()`: the marks,
  the selection and the off-screen cue go and she comes back to his shoulder without a flight. Found in review: without it she flew toward the old
  room's lift for 1.5 s after a pull and a room change.
- **Tests (new, `FairyOffscreenTest`, 21):** the edge point for right, left, above, below, both corners, direction kept and inside the
  inset, and an inside point unchanged (Y down); on the real route room: the lift off and the ladder in view from the lever, the
  selected cue (twinkle at the right edge), the post-pull cue and its 1.5 s end with the return to his shoulder, the 0.8 glow and
  its fade, Up held through the pull, every mechanism in view (unchanged D82 behaviour plus the mark), no view, a real pull of a trigger that drives nothing,
  nearest and sticky choice among two, above and below, the selection and position identical with and without the cue, a room change and a room
  reset dropping the marks, and a thrown stone hitting a lever (`throwHitsLever`) marking its mechanisms. No existing test changed and no threshold
  moved; `./gradlew test` 1,013 tests, 1 skipped (as before), 0 failures.
- **Not verified:** the look and timing in the running game on desktop, web and touch (the glints around her, the 1.5 s, the 16 px
  top edge point, where she can sit under the HUD row (the coin and artifact icons top left, the fireball stock top right, y 10 to 42), which is
  drawn over her: cosmetic; the touch controls sit in the portrait strip or side bars and never overlap the picture). The author's play-through of 2026-11-17 (desktop) confirmed that the cues are clearly visible; for
  the shared visual checks, [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368).

- **Update (#434, 2026-11-17): several off-screen mechanisms are shown in turn.**
  - **Author's decision (2026-11-17, #434):** when several linked mechanisms are off screen, the fairy shows each of them, cycling,
    not only the nearest. She stays at the edge for as long as Up is held at the lever (as built) and for the post-pull window.
    Everything below is the agent's design, which the author kept after the play-through of 2026-11-17 (a general confirmation, "everything is clearly visible, everything is done", not an answer point by point); that keeps the cycle order, the 0.8 s dwell and the post-pull window as built.
  - **Order.** The cycle starts at the nearest off-screen mechanism to the view centre (the D87 rule, so one target and the first of
    several are chosen as before), then goes **clockwise round the view centre**: the next target is the one with the smallest
    `atan2(dy, dx)` (Y down, so an increasing angle is clockwise on screen) greater than the last one's, ties by smaller x then y; after
    the last it wraps to the smallest. Why not by distance: a distance order jumps between the near and far edge points of the
    screen and back (zig-zag); every edge point lies on the view's border on the ray toward its mechanism, so angle order is the order
    of the points along the border and each flight is to the neighbouring point, except the one wrap. The key is the target's place when it was
    chosen (frozen, so a lift swinging past another target's angle during its dwell does not make the next hop skip that target), its angle
    taken round the view centre at the hop (so a moving camera does not make it stale); it is kept after a target leaves the view, so the
    cycle goes on after its place. When the current target is no longer in the selected lever's or the cue's set (another lever, Up released,
    the cue ended) the key is dropped and a new cycle starts at the nearest.
  - **Dwell.** 0.8 s of twinkle (`DWELL_SECONDS` = two loops of the 0.4 s appear/twinkle clip), counted from her arrival within 8 px
    of the point (flight time is not part of it), then she flies to the next at the usual rate 14 (about 0.2 to 0.4 s). A hop ends
    after 2 s at the latest (`HOP_MAX_SECONDS`) so a point she cannot reach (a fast-moving lift) never holds the cycle. 0.8 s is
    long enough to read as "here" and short enough that three targets fit in a few seconds; it is not tested on a player.
  - **No flicker.** The set is read again only at a hop. A target that comes into view (or is removed) ends its dwell at once and gets
    the in-view mark instead; one that leaves the view joins at the next hop, not in the middle of a dwell. With one off-screen
    target nothing hops: it keeps the job while it stays off screen and its dwell timer just restarts, so her position, mode and
    twinkle clip are those of D87 (pinned step by step, `aSingleOffScreenTargetFliesTheD87PathAndNeverLeavesItsTwinkle`).
    With none, nothing changed.
  - **The post-pull window with several (agent's proposal, for the author).** 1.5 s cannot show three targets (about 1 s each).
    The in-view marks keep 1.5 s (the glow is about the change itself), but the fairy's cue now has its own window,
    `max(1.5 s, n x (0.8 s dwell + 0.4 s flight))` with n the number of marked mechanisms off screen at the first frame after the
    pull (`FairyGuide.sizeCue`; two: 2.4 s, three: 3.6 s). For one it is 1.5 s as before. While Up is held at the lever she cycles
    without a limit. The author kept these windows after the play-through of 2026-11-17 (general confirmation, not a separate answer).
  - **Room change** (`reset()`) drops the cycle, its key and the cue copy, so the next cue starts at the nearest.
  - **Tests.** `FairyOffscreenTest` +13 (now 34): three targets (nearest first, clockwise, wrap, 0.8 s of twinkle at each, the last one
    inside its window), the flight between two edge points (goal = `edgePoint`, FLY then TWINKLE), the held-lever cycle (ladder, lift,
    ladder, ... over 12 s), a target coming into view mid-dwell, a target leaving the view joining only at a hop, the window for three
    and for one, the single target step for step (twinkling on every frame once arrived), a room change, a set across the +-pi seam, targets on one ray (ties by x), Up released mid-cycle (the next hold starts at the nearest), a new pull mid-cycle (current target kept and dropped), and a moving target passing a static one's angle. The scenarios are built in the tests (the shipped maps have no room
    with two off-screen mechanisms for one lever; the route room's lift and ladder, plus its fireball refill as a third marked
    entity). The old "nearest and sticky" test still passes: it holds within the first dwell. No threshold moved.
  - **Allocation.** `AllocationBudgetTest.fairyGuideCyclingBetweenTwoOffScreenTargets` (the route lever held, view far left, ladder and
    lift both off screen, at least 10 hops in the 1,200 measured frames; update and both render passes): 0.0, 0.0, 0.0 B/frame in
    3 isolated runs; `fairyGuidePostPullCueCyclingBetweenTwoOffScreenTargets` (nothing selected, the pull renewed every 150 frames, so `sizeCue` and the `cue` scan run) 0.6, 0.0, 0.0; ceiling `FAIRY_GUIDE_CEILING` 8 B (the shared isolated fairy-scene ceiling, as D87 argues). The ordering is a
    scan per hop over the two fixed arrays, with no sort and no new object. New helpers are in `tools/check_allocations.py` `HOT_METHODS`.
  - **Not verified:** the look on web and touch and the 0.4 s feel (the author's desktop play-through of 2026-11-17 found everything clearly visible and kept the numbers as built), and that a player reads the
    sequence as "each of these". No shipped room has two off-screen mechanisms for one lever, so only a test map shows it. Tracked by
    [#434](https://github.com/jlogicgames/edgard_in_kimeria/issues/434) (Chrome check; the author's play-through of 2026-11-17 is recorded above).

## D88. The trip-line deadfall: a touch trap whose trigger is drawn on the floor (#363, 2026-11-17)

- **Status:** built by [#363](https://github.com/jlogicgames/edgard_in_kimeria/issues/363). The card, the art and the
  placement were approved by the author on 2026-11-17; the room was played and the two sounds auditioned by the author on 2026-11-17, approved.
- **Revised by [D105](#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) (#499), done by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (2026-11-19):** the deadfall as its room's only object is the "met alone" reading D105 drops; `forest-1-deadfall` moved the trap 64 px left and added a falling stone and a RedMob; its pocket is on the vine ([D107](#d107-the-first-meeting-clearance-counts-what-can-kill-a-crumble-support-over-a-floor-is-not-a-lethal-neighbour-500-499)).
- **Decision:** a new VISIBLE trap of the touch family (D47), `Deadfall` (card: TRAPS.md, "Trip-line deadfall"). A log is
  hauled up on the far side of a bough and lashed there; a pale trip vine lies across the floor in front of it. The vine is
  the only trigger (the hero's body box, from either side; no stone or fireball), so the answer is in the hero's own feet:
  jump the vine and nothing fires, or trip it, stop and let the log go by. The trip starts the shared D7 warning
  (`TrapWarnings.DEFAULT_WARNING_SECONDS`, 0.8 / 1.2 s), then one eased 0.5 s swing on two parallel 56 px ropes from +80
  to -78 degrees, where the ropes meet a snag on the near side; the log kills only while it swings and is at body height
  only 0.16-0.34 s into it. Afterwards the log eases in under the snag over 0.25 s (no jump at the catch) and hangs there above head height, and the vine lies cut: harmless until the
  room is rebuilt (retry, Restart Level, re-entry), so no state is carried across a visit.
- **Why this geometry:** the log swings at the hero, not with him, so a walker who trips the vine and keeps going is hit
  with either warning length (the assist does not turn the trap into a free pass), while the vine lies 104-120 px before the
  knots (a hero who trips it has his centre 125 px before them, about 70 px short of the swing band) so that stopping is a generous answer (measured: safe up to 0.68 s after the trip with either warning).
- **Placement (D72):** a new one-screen room, `forest-1-deadfall`, second in forest-1's play order, introduces the
  `deadfall` element alone (registry row in ROOM_CARDS.md). No existing room could take it: the mob room, the trap course
  and forest-canopy are at the two-introduction cap, the forest-0 lesson rooms hold their one trap (D48), the lever and
  tree rooms are built to have no death, and in the route, guard and walls rooms it would not be met alone.
- **Consequence (D75):** the creak at the trip is the warning's sound, the knock and six bark chips at the snag are the
  consequence (SOUND_DIRECTION.md, "Trip-line deadfall").
- **Checks:** `DeadfallTrap.problem` at load for every room, mirrored in `tools/check_tiled_links.py`; the contrast gate
  measures the bough, the tied log and the vine where they are drawn (worst 0.464, 0.410, 0.323); `DeadfallTrapTest`;
  `AllocationBudgetTest.deadfallTripAndSwing` (0.0 B/frame) and `forest1DeadfallRoom`.

## D89. The creeping bramble: a clocked thorn arm across a lane, taught at a ladder exit (#364, 2026-11-17)

- **Status:** card, art, sound plan and the new room `forest-1-bramble` approved by the author on 2026-11-17
  ([#364](https://github.com/jlogicgames/edgard_in_kimeria/issues/364), trap N3 of the #348 proposals). The trap, its art,
  its sounds, its load checks, its tests and its room `forest-1-bramble` (fifth in forest-1's play order, after the lever
  lesson) are built. Played and auditioned by the author on 2026-11-17, approved.
- **The trap** (Tiled type `Bramble`, class `entity/objects/Bramble`, card in [TRAPS.md](TRAPS.md)): VISIBLE, rhythm family
  (the D46 rules: a deterministic clock `(elapsed + phase) mod period`, advanced only in `Level.update`, reset by every room
  rebuild, lethal by one overlap test with the hero's body box). One cycle: rest, the shared warning
  (`TrapWarnings.DEFAULT_WARNING_SECONDS`, 0.8 s / 1.2 s), grow 0.25 s, hold `hold` s (default 1.0, 0.6-1.5), retract 0.5 s.
  Default `period` 4.0 s: rest 1.45 s (1.05 s with the assist), and rest plus warning, when the lane is open, 2.25 s either
  way. Load rule: at least 0.8 s of rest with the assisted warning, `period >= 2.75 + hold`.
- **Geometry (world Y grows downward).** The object box is the full grown reach (width 48-96 px) by 24 px (the sprite); its
  bottom edge is the floor line. The lethal band runs from `floor - 18` to `floor - 2`: every hero standing on that floor
  (body `floor - 22` to `floor`) overlaps it, a body above its top does not. Along the lane it covers the first 12 px from the
  root face in every phase (the clump, drawn at rest: the danger is always shown), and from the root face to the tip while the
  arm grows, holds and retracts; the tip moves linearly, about 208 px/s out and 104 px/s back, so the hero (100 px/s) cannot
  outrun the grow: the warning means leave now or wait.
- **Placement rule** (`Bramble.placementProblem` with `Level.requireSafeSetting`, run by `Level.validateBramble` for every bramble of
  every room when the level loads, as the deadfall, the branch, the fungi and the explosives are; mirrored in
  `tools/check_tiled_links.py`, which reads its numbers from `Bramble.java` and `TrapWarnings.java`):
  `root` is `left` or `right` and names the side whose edge must lie against a solid face (a collision that is not a one-way
  platform) over the whole band; the floor (or a shelf top) at the box's bottom must run under the tip end and 16 px beyond it
  (faces and floors may be split into several collisions and may be off by up to 0.5 px: their spans are merged with
  `FLOOR_TOLERANCE`, DeadfallTrap's rule),
  so the hero can stand clear of the grown arm; the D46 rule that the lethal part never covers the entrance, the `RoomExit`
  or a `Checkpoint` (`Level.requireSafeSetting`, at full reach); the properties are `root`, `period`, `phase`, `hold` only.
- **The ladder exit recipe (found while building).** The clump is lethal at every moment and reaches 12 px from the root
  face, so a ladder whose top the bramble guards must stand at least one tile off that face: the ladder is at x 592-608 of
  the room, the 16 px beside it open down to the floor, and the root face is the wall at x 624. With the ladder against the
  wall the hero on its top is inside the clump and no exit exists (the first fixture showed it; a pillar tried in the gap
  was not needed and made the wall sweep of `RoomCeilingTest` stand the hero inside it, so it was removed). A static ladder
  (no lever, `dropDistance` 0) stands where it is authored and carries the hero up.
- **Measured** (`BrambleTest` on `core/src/test/resources/tiles/bramble-test.tmx`): a hero who waits on the ladder with the
  head under the band and climbs out when the retract ends reaches the exit from every start offset (0.05 s steps over the
  period); a blind climb-out after a fixed wait survives 37 of 80 offsets, the longest safe run 1.85 s; waiting on the ladder
  is safe for two whole cycles; the warning lasts the shared warning in play with and without the assist; the cues fire once
  per cycle. `AllocationBudgetTest.brambleCycles`: 0.0 to 0.7 B/frame (ceiling 8).
- **Art:** `trap-bramble` 16x24 x 6 (`rest`, `rustle1`, `rustle2`, `cane1`, `cane2`, `tip`; grid
  `art/objects/forest-traps/bramble.grid`): the tuft's magenta cane and bone tips, a shape no scenery has. A `root=right`
  bramble is drawn mirrored (`Batch.draw` with scale x -1; `check_contrast.py` measures the mirrored clump at the root end).
  The arm is drawn as alternating cane segments behind a tip at the current extent; the mock-up's straight cane with paired
  thorns read as a centipede and was redrawn wavy with irregular hooked thorns before approval.
- **Sound:** `bramble_rustle` (dry snaps over a whispered bed, 0.36 s, RMS -27 dBFS) at the warning start and
  `bramble_lash` (a rising "shk", 0.16 s, RMS -21 dBFS) at the grow start, generated by `tools/audio/generate_sounds.py`
  (seeds 6401, 6402); heard only while the bramble's box overlaps the camera's visible world rect (`Level.brambleInView`), so an off-screen
  clock does not tick, and the warning is heard from the entrance of its one-screen room (`Forest1BrambleTest`).
  Auditioned by the author on 2026-11-17, approved.
- **The room** (`forest-1-bramble`, x 5600-6240 of `forest-1.tmx`, card in ROOM_CARDS.md): the floor under a shelf, a static
  ladder at the shelf's right end one tile off the right wall, the bramble rooted in that wall over the ladder top, the exit on
  the shelf's left end. No existing room could take the first meeting (the route
  room's only climb-top lane ends at its exit, the lever and tree rooms have no death by design, the guard room would combine
  it at once, the rest are at the introduction cap). Measured (`Forest1BrambleTest`): mastered 10.73 s (the same with the
  assist), 9.42 to 12.80 s over every start offset, a blind climb from a fresh start caught at 6.70 s; the room scene in
  `AllocationBudgetTest` 536.1 to 536.3 B/frame (the deadfall room 536.0 in the same runs). Contrast (`check_contrast.py`, all six frames at the root end): rest 0.419, worst frame 0.414 (`cane1`, `cane2`).
- **Not decided or not verified:** nothing open: the look in motion, the sounds and the room were played and auditioned by the author on 2026-11-17, approved.

## D90. The hanging hive: a rhythm trap whose stingers fall as a band (#365, 2026-11-17)

- **Status:** card, art, sound plan and placement approved by the author on 2026-11-17 (through the orchestrating session);
  built by [#365](https://github.com/jlogicgames/edgard_in_kimeria/issues/365) with its teaching room `forest-1-hive`
  (appended to `forest-1.tmx` after #363 and #364, played after `forest-1-guard`). The author played the room and auditioned
  its sounds on 2026-11-17, approved.
- **What it is.** A VISIBLE ([D71](#d71-traps-are-visible-before-they-fire-2026-11-16)) rhythm trap (family of
  [D46](#d46-rhythmic-traps-spore-puffball-swinging-thorn-vine-and-the-rotten-log-skin)): a paper wasp hive hanging from a
  bough (Tiled type `HangingHive`, a 24×32 object whose top edge is the bough's top), specks drifting at its mouth. Card:
  [TRAPS.md, "Hanging hive"](TRAPS.md#hanging-hive-hanginghive).
- **Rhythm.** The puffball's clock: `(elapsed + phase) mod period`, calm, then the shared warning
  (`TrapWarnings.DEFAULT_WARNING_SECONDS`, 0.8 s, 1.2 s with the assist, taken from the calm), then the release for the last
  `curtain` seconds of the period (default period 4.0 s, curtain 1.0 s). The release begins at the same point of every
  cycle, so the assist never shortens the pass window, only the calm before the shake.
- **The lethal part is a falling band, not a column switched on.** Stingers leave the mouth (object top + 26) for `curtain`
  seconds and fall at 240 px/s to the first solid under it (any Collisions block but quicksand; a ladder is not solid), so the
  band runs from `mouth + 240 max(0, s - curtain)` to `mouth + 240 s`, cut at the solid, 8 px wide (the drawn stinger tile).
  Every height is lethal for exactly `curtain` seconds, later the lower it is. Chosen over an instant column because the
  stingers are seen to fall (the consequence of the shake, D75), and because a climber or a walker can read where the danger
  is now. A release that would have begun before the room started counts from the room's start, so nothing is in the air when
  the hero arrives. The hive's body never hurts.
- **Counter.** Free: pass in the calm. Optional, never required ([D26](#d26-observe-plan-before-committing)): knock the hive
  down by hitting its 20×19 body box (x + 2, y + 7, above the mouth) with a thrown stone (free, D39: the stone stops and drops)
  or a fireball (one charge, spent). A standing throw rises only about 10 px, so from the floor it is a **jump-throw**
  (measured: the stone leaves the hand at y 226 to 270 depending on the moment of the jump, and a hive whose body is at y 239
  to 258 is hit from 27 of 45 tested distances, body right edge 192 to 296). The knocked hive falls (gravity 900 px/s²,
  harmless), lies split on the solid, the bough is left bare and the specks scatter; no stinger leaves after the hit, those
  already falling finish their fall (they are visible).
- **Load checks** (`HangingHive.fromMap` and `Level.hiveFor`, at load, for every room; mirrored in `tools/check_tiled_links.py`): only `period`, `phase`,
  `curtain` (finite numbers; any other key fails); the 24×32 size; a solid under the mouth in its room at least 32 px down;
  `period >= curtain + depth / 240 + 1.2 + 0.8` (0.8 s of calm at the lowest point with the longest warning); no
  `Actionable`, `Escalator` or `FallingPlatform` in the band; the band never over the entrance body, the exit or a checkpoint
  (D46's rule).
- **Measured** (`HangingHiveTest`, `hive-test.tmx`): the band's first lethal frame at depth d is `3 + d / 240` s into a 4 s cycle
  and lasts 1.0 s (two depths, within two frames); a walker who waits outside the band while it is in the air or about to be
  passes at every 0.05 s offset, with and without the assist; one who ignores it dies at some offsets. On a 112 px ladder under a
  hive with a 198 px band and a 4.5 s period, the window from the last stinger leaving the foot to the next head reaching a
  hero standing on the ledge is 2.94 s; a climber who starts right after a curtain (cycle time under 1.2 s) passes at every
  offset with and without the assist, and one who starts as the release begins is killed. Speck pool peak below its 24.
- **Art and sound.** `trap-hive` 24×32 × 7 and `trap-stingers` 8×16 × 2, authored grids; the stingers are a sprite because they
  are the lethal object, the specks are particles (`KIND_GRIT`). Outline where they are drawn in `forest-1-hive`: hive frames
  0.450 at worst, falling 0.438, fallen 0.343 (drawn on the floor line, over the floor tiles), stingers 0.346 (active tier, D76). A first stinger draft with a darker bone tip measured 0.208
  on the mid-blue sky; ink tips are what make them read. Sounds `hive_buzz` (warning), `stinger_rain` (release), `hive_fall`
  (knock-down), generated ([SOUND_DIRECTION.md](SOUND_DIRECTION.md#hanging-hive-365-seeds-6501-6504-d90); seeds 6501-6504, since 3701 is the deadfall creak).
- **The room** (`forest-1-hive`, card in [ROOM_CARDS.md](ROOM_CARDS.md#forest-1-hive), `Forest1HiveTest`): the floor hive
  first, alone over open floor with the stone by the entrance, then a hive over the static ladder up to the exit ledge
  (period 4.5 s, phase 2). Mastered route 7.12 s from a fresh start, 6.85 to 12.22 s over start offsets of 0 to 9 s with and
  without the assist; a hero who never waits dies in all 45 tested offsets, 3.1 to 7.5 s in (12 at the floor hive, 33 at the
  ladder). In the room the stone's jump-throw knocks the floor hive down from 28 of 28 throw points, a fireball near the top of
  a jump from 10 of 13 timings; the ladder hive is out of reach of both, so its answer is timing alone.
- **Review patterns of #363 and #364, applied:** every hive of every room is built and checked when the level loads
  (`Level.hiveFor`), not only when its room spawns; the hive's sounds play only while its frame or band is in the camera
  view (`Level.hiveInView`), so an off-screen hive never buzzes; sizes and the floor search use a 0.5 px tolerance;
  `check_tiled_links.py` reads the hive's numbers from `HangingHive.java` (`java_constant`); `check_contrast.py` gates every
  frame where the game draws it (`hive_parts`: the hive's frames, the falling and fallen hive, the stinger tile down the band).
- **Allocation:** `AllocationBudgetTest.hangingHivesCycleAndOneIsKnockedDown` 0.1 to 0.9 B/frame in 3 isolated runs and 0.0 to 0.1 in 2 full-suite runs, ceiling 8 B
  (the shared isolated-scene ceiling); the room scene `forest1HiveRoom` 536.7 B/frame in 3 isolated runs and 536.0 in the full
  check, ceiling 590 (highest run + about 10 %), the same as the deadfall and bramble rooms. The band is drawn a row at a time from 32 cached one-row regions, so both of its ends clip
  exactly without a region per frame.

## D91. The ember fissure: a clocked fire jet from a floor crack, taught in forest-2 (#366, 2026-11-17)

- **Status:** card, art, sound plan and placement approved by the author on 2026-11-17 (through the orchestrating session);
  built by [#366](https://github.com/jlogicgames/edgard_in_kimeria/issues/366) with its teaching room `forest-fissure` in
  `forest-2.tmx`, added after the night colour of D92 reached every forest map (#461).
- **What it is.** A VISIBLE ([D71](#d71-traps-are-visible-before-they-fire-2026-11-16)) rhythm trap (family of
  [D46](#d46-rhythmic-traps-spore-puffball-swinging-thorn-vine-and-the-rotten-log-skin)): a crust of ash on the floor line
  with a glowing slit (Tiled type `EmberFissure`, a 16×12 object whose top is 4 px above the floor line). Card:
  [TRAPS.md, "Ember fissure"](TRAPS.md#ember-fissure-emberfissure). A vertical danger rising from the floor, the one
  direction no earlier trap covers; its ember palette is the stump's, and it echoes the healer's gift of fire (#340).
- **Rhythm.** The hive's clock: `(elapsed + phase) mod period`, calm, then the shared warning
  (`TrapWarnings.DEFAULT_WARNING_SECONDS`, 0.8 s, 1.2 s with the assist, taken from the calm), then the jet for the last
  `jet` seconds (default period 3.5 s, jet 1.0 s: rise 0.1 s, hold, sink 0.2 s). The jet starts at the same point of every
  cycle, so the assist shortens the calm and never the open window. **Timing rule:** `period >= jet + 1.2 + 0.8`, the
  assisted warning plus at least 0.8 s of calm (the bramble's minimum, D89). A cycle whose warning would have begun before
  the room started stays calm: every jet is announced in full.
- **Lethal part.** A column 8 px wide (the drawn width of the jet) from the floor line up to the jet's top, default 96 px,
  so it cannot be jumped (the jump reaches about 58 px). Embers pass through one-way platforms; a solid block above cuts
  the column. Lethal exactly where drawn. No ammunition answer, by design: the counter is reading the rhythm (D26 holds:
  nothing is spent).
- **Consequence (D93).** Every jet, hit or miss: the column itself, `ember_jet`, seven sparks falling from the upper column
  as it sinks, and the sooted `spent` frame for 0.4 s. Warning sound `fissure_rumble`. Both heard only while the fissure or
  its column is in the camera view (D89, D90).
- **Art.** Original native pixel art, `trap-fissure` 16×12 × 6 and `trap-ember-jet` 8×16 × 4. Drawn for the night of D92:
  the outline is led by the pale ash rim against the sky and the ink crack against the floor, not by ink against the sky,
  which no longer separates over `2c3f66`. The contrast gate at the two placed fissures over `2c3f66`: fissure 0.377, jet
  tile 0.349, tip 0.398 at worst (floor 0.28), so neither sheet takes the moon rim (`RIM_SHEETS`). Over the day blues the
  jet measured 0.091 (the dry run before #461), which is why the room waited for the night.
- **Placement (D72).** A new room `forest-fissure` in forest-2 (x 3008-3648, appended at the map's end, which grows from
  188 to 228 tiles; listed after `forest-walls`, so it is played before `forest-canopy`; forest-1 already holds nine rooms,
  forest-2 had three before the boss): one screen, a fissure on open floor met alone, then a second, half a period out of
  step, under a one-way branch on the step to the exit ledge. Card: [ROOM_CARDS.md, `forest-fissure`](ROOM_CARDS.md#forest-fissure).
  Every forest-2 room after it moved one index on (canopy 3, boss 4); the tests that walk the level were updated to match,
  none of their thresholds changed.
- **Measured.** `EmberFissureTest`: a walker who goes when a jet ends passes at every 0.05 s offset, with and without the
  assist; a blind walker dies at 23 of 71 offsets; the branch climb passes at every offset (3.75 to 6.73 s after the
  start delay). In the shipped room (`ForestFissureTest`, `RoomReplayTest`): the mastered route 8.08 s from the entrance
  (three identical attempts and every collection return), 5.18 to 8.63 s over start offsets of 0 to 3.5 s (0.05 s steps)
  with and without the assist; a careless walker dies at the floor fissure at 23 of 71 offsets, at most 3.5 s after
  entering. Allocation: `AllocationBudgetTest.emberFissuresCycle` 1.1 B/frame when it runs alone in a fresh JVM (about
  1.3 KB once over the 1200 measured frames: first use and JIT), 0.3 after the same code is warm, 0.0 in the full check; the
  trap's own cost is `emberFissuresCostNothingOverAnEmptyFrame`, the median of 15 paired windows of the four fissures minus an
  empty frame, 0.0 B/frame in 3 isolated runs (the empty frame alone 0.0). Ceiling 8 B for both, unchanged; the room
  scene `forest2FissureRoom` 536.8 B/frame in 3 isolated runs and 328.0 to 472.0 in 3 full-suite runs, ceiling 590 (the hive room's).
- **Verified in play:** the author played every room, `forest-fissure` included, and heard `fissure_rumble` and `ember_jet`
  in play on 2026-11-18 ([#463](https://github.com/jlogicgames/edgard_in_kimeria/issues/463)). No novice playtest (#135).
- **Later (#327, 2026-11-18):** a beetle guards the exit ledge and the `RoomExit` moved 16 px right (x 3600); the route
  times above are from before it. Current numbers: [ROOMS.md, forest-2 fissure room](ROOMS.md#forest-2-fissure-room-366-d91).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the ember fissure leaves the forest for a new forest-natural clocked trap with the same beat timing, and returns later as a Swamp geyser. **Done by [D129](#d129-the-lance-root-replaces-the-forests-ember-fissure-562-2026-11-22) ([#562](https://github.com/jlogicgames/edgard_in_kimeria/issues/562)):** `forest-fissure` places the lance root; the clock, the column and the rules above stand; the fissure's code, art and sounds stay for the Swamp.

## D92. The world is drawn over a flat per-level background; the forest rain and sky are gone (#430, 2026-11-17)

- **Status.** Built in three changes of [#430](https://github.com/jlogicgames/edgard_in_kimeria/issues/430), approved by the
  author on 2026-11-17: (1) the removal, the map property and the gate switch (PR 455); (2) the moon rim tool, every flag
  off (PR 456); (3) **the night**: every forest map's `background` is `2c3f66`, with the moon rim, the decor night
  palettes and the honey fungus breath (`FungusTrap.GLOW_MIN` 0.55 to 0.80, a change to D63) in the same change, because
  the gate cannot pass on night with day sprites, nor on day with rimmed ones. Clouds and a moon:
  [#451](https://github.com/jlogicgames/edgard_in_kimeria/issues/451). Swamp weather: part 2 of #430, parked.
- **Backgrounds still to be drawn.** Open levels show a night sky (the castle shows night through its windows; author,
  2026-11-17). The night sky with its moon and clouds is still to be drawn in
  [#451](https://github.com/jlogicgames/edgard_in_kimeria/issues/451); the contrast gate is re-run over that real background
  then, and any sprite it fails is fixed then.
- **Moon rim (rule).** On the night background the near-black ink contour
  of D71/D76 no longer separates a sprite from the sky (below about OKLab L 0.40; over `2c3f66` 31 active sprites fall
  under 0.28). An active sprite whose outline does not reach the active floor on its level's background carries the moon
  rim:
  - its outermost contour pixels darker than OKLab L 0.50 that face **up, left or right** (a transparent 4-neighbour on
    that side, or the frame's edge) are `c4cfe8`; contour pixels facing **down** keep their ink, so the underside still
    reads on the ground. No pixel is added or moved: the silhouette and the hitbox are unchanged;
  - it is applied by the exporter, never painted pixel by pixel: `tools/moon_rim.py` (`rim(image, cell)`), switched on
    per sheet by `RIM_SHEETS` in `tools/forest_trap_sprites.py` and `tools/hero_sprites.py`, and by its CLI for sheets
    with no grid source (`python3 tools/moon_rim.py SHEET.png --cell W H`). It is idempotent, so a rim softened by hand
    in the grid (where it reads like a sticker) survives a re-export; the gate keeps the softening honest;
  - it is used only where the gate needs it: on every sprite it lowers the Bat pickup to 0.266 against its own halo;
  - it costs one palette entry. The hero grid had all 15 opaque colours in use; the author chose to merge the hair
    highlight `h` `4a3a4e` (100 px over every frame) into the hair `H` `241a26` (2026-11-17, option B of two; the other
    merged the belt brown into the boots, 867 px). `hero_sprites.indexed` refuses a 16th colour. The brick wall's
    generated master also had 15; `export_object_sprites.rim_objects` quantizes it to 14 first (its closest pair merges);
  - a trap's at-rest cue drawn in ink on a lit edge is never repainted: grid chars in `forest_trap_sprites.RIM_KEEP`
    (the falling platform's crack, #362, its own char `c` in the same ink; `SpriteSheetContractsTest` checks it);
  - companions, same change: decor keeps its own-hue contour and is tinted toward the level's background until its body
    is at most 0.20; a glowing sprite's dimmest brightness keeps its rim over the floor (the honey fungi:
    `GLOW_MIN` 0.80);
  - the inherited sheets get the rim through the CLI as an interim, every frame (cell in brackets): `enemy/Bat.png`
    (16×16, `--pad`: its last row is 8 px), `enemy/Mobs.png`, `enemy/yellow_mob.png` (48×32), `objects/GreyOff.png`
    and `objects/GreyOn.png` (32×8; since 2026-11-21 drawn in `art/objects/escalator.grid` and rimmed by
    `export_object_sprites.export_escalators`, #236), and the author's `enemy/Boss.png` (48×32). They have no source to re-export from, so
    the rim is in the shipped PNG; the redraws of [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189)
    follow this rule instead;
  - where it is on: the hero (all three sheets); the traps `bomb`, `bramble`, `branch`, `deadfall`, `deadfall-bough`,
    `falling-platform`, `false-honey-fungus` (so the honey pair stays alike), `hive`, `honey-fungus`, `log`, `perch`,
    `puffball`, `root`, `rotten-perch`, `stone-lip`, `stump`, `thorn-root`, `thorn-tuft`
    (`forest_trap_sprites.RIM_SHEETS`); the objects `ladder`, `stone`, `fireball`, `wall-gate` (`wall-brick` until 2026-11-21), `platform`
    (`export_object_sprites.RIM_OBJECTS`; since #236 also the lever and the bat and mushroom pickups, D112); the inherited sheets
    above. Off where the gate passes without it (the coin, the torch, the stingers, the deadfall vine, the fairy);
  - Tests: `tools/test_moon_rim.py` (lit sides rimmed, underside kept, light edges and the silhouette untouched, frames
    in a strip independent, idempotent, mirror-symmetric, the CLI, the thorn tuft on a plain `2c3f66` patch 0.226 to
    0.411 against the 0.28 floor, a flagged sheet rimmed with one more colour, the hero's palette with the rim slot and a
    16th colour refused, kept pixels, the falling platform's crack kept in ink, the CLI's `--pad`).
- **Measured over `2c3f66` (`check_contrast.py`, 2026-11-17): 0 errors** (37 before the change, the crown of #421
  included). Active worst: the Mushroom pickup 0.290, the Bat pickup and the false honey fungus 0.299, the hero 0.311
  (forest-2 x 64, against the light rubble wall), the floor lever of #413 0.306 (no rim needed); the Bat enemy 0.057 to 0.473, the RedMob 0.123 to
  0.467, the thorn tuft 0.189 to 0.383. Decor (outline / body max): bush 0.106 / 0.151, look-alike 0.106 / 0.157,
  trunk 0.095 / 0.160, hanging vine 0.130 / 0.173, crown 0.111 / 0.180. The tile scenery report falls to 0.116 to 0.130,
  under the decor ceiling. `TouchControlArtTest` passes against `2c3f66`.
- **Why.** The forest-1 rain (`RainDrop`, one `WorldObject` per drop, a mutable static wind) and sky (`SkyBackground`,
  `sky.png`) were Dart-port leftovers chosen by level name in `Level`. The gate measured every sprite of all three maps
  over `sky.png`, but forest-0 and forest-2 never drew it: they showed the clear colour `87ceeb`. Over `87ceeb` the gate
  fails 10 checks (bush, look-alike, trunk and vine too loud; the coin 0.185 and the hive's stingers 0.262 below the active
  floor). For two of the three maps it had measured a background the player never saw.
- **Decision.** Each map sets the colour behind the world in its map property `background`, a Tiled colour (`#AARRGGBB` with
  alpha `ff`, or `#RRGGBB`). `Level` reads it once at load (`Level.parseBackground`; a malformed or translucent value fails
  the load) and `KimeriaGame` clears to it at both clear sites (the direct pass and the post-processed scene buffer, which
  had its own copy of the old literal). Before a level loads, and on a map without the property (test maps), the default is
  `Level.DEFAULT_BACKGROUND` `4a92b8`, `sky.png`'s own blue without its clouds. In PR 455 the three forest maps set `4a92b8`
  explicitly. forest-1 loses its clouds and rain; forest-0 and forest-2 move from the pale `87ceeb` to the same blue.
- **Gate.** `tools/check_contrast.py` composites every sprite over its map's `background` colour plus the tile layer, one
  sample per placement (no sky offsets), and fails a shipped map without the property (`map_background`;
  `test_check_contrast.BackgroundTest`). The run takes 2.6 s instead of about 8 s. Measured on 2026-11-17: 0 errors; every
  active worst value equals the old sky's (worst YellowMob 0.284), because clouds were never an active sprite's worst case;
  two decor sprites got quieter (hanging vine 0.156 to 0.059, trunk 0.174 to 0.121). The silver thread's worst moved from
  0.340 to **0.335** (forest-2, `forest-walls`): the old composite pasted the 360 px sky strip into 368 px maps, so the bottom
  8 rows were transparent and measured as black. `TouchControlArtTest` and `tools/touch_control_sprites.py` use
  `4a92b8` in place of `87ceeb`.
- **Allocation (measured, unchanged ceilings).** Three isolated runs of `AllocationBudgetTest` on `origin/main` and three
  after the removal, same machine, B/frame: forest-1 trap course 328.1 to 544.3 before, 328.0 to 544.3 after; mob room
  320.0 to 488.0 / 346.1 to 536.0; deadfall 320.0 to 536.0 / 320.0 to 536.0; lever 440.0 to 488.0 / 352.0 to 536.0;
  bramble 320.0 to 536.0 / 320.0 to 536.0; route 320.0 to 536.0 / 320.0 to 536.0; guard 232.7 to 544.7 / 232.7 to 544.4;
  hive 224.0 to 536.0 / 224.0 to 536.7; tree 320.0 to 488.0 / 352.0 to 536.0; tree ride 250.8 to 563.4 / 346.8 to 562.8.
  The rain and sky cost nothing measurable: the 536 B/frame runs are the harness and level code (D83), so the earlier
  comments that called the sky and rain dominant were wrong and are corrected. No ceiling changed.
- **Tests changed.** The `replay-forest-1` file aliases in `BossCombatTest`, `DirectionalActionsTest`,
  `FallingPlatformFloorTest`, `Forest1TrapsTest` and `RoomReplayTest` are gone (forest-1 loads as shipped);
  `RhythmTrapsTest` now loads forest-1 like the other maps; `CampaignFlowTest` loads forest-1 instead of completing it
  unloaded; `FallingTrapsTest` no longer stubs GL. `HeadlessLevelHarness` keeps its GL stub with its real reason: rendering
  builds the shared `SoftDot` and `Shaders.whiteQuad` textures. New: `LevelBackgroundTest` (parsing; a sign such as
  `#-4a92b` is rejected digit by digit) and `ShippedBackgroundTest` (every shipped map sets `background`, even if the Python
  gate is skipped). No threshold changed.

## D93. Principle 4 measured: a hazard's own act is seen and heard, hit or miss (#388, 2026-11-17)

- **Status:** Accepted by the author, 2026-11-17 (through the orchestrating session), from the audit of [#388](https://github.com/jlogicgames/edgard_in_kimeria/issues/388). Refines
  principle 4 of [D75](#d75-one-style-system-the-design-language-card-first-rules-land-complete-386-2026-11-16); the
  falling stone ([D73](#d73-falling-stone-impact-feedback-347-2026-11-16)) stays the model.
- **Decision:**
  - **Before:** a visible [D7](#d7-traps-are-deterministic-puzzle-elements) telegraph whose length comes from
    `TrapWarnings` (the assist ×1.5); a continuous motion is its own warning
    ([D46](#d46-rhythmic-traps-spore-puffball-swinging-thorn-vine-and-the-rotten-log-skin), the thorn vine). A warning
    sound is welcome but not required.
  - **After:** when a hazard acts (fires, falls, bursts, strikes) it shows a visible result (frames or particles) and
    plays a generated sound **whether or not it hits**. The shared death beat (`hit`, ×0.2 slow motion, the thread,
    [D81](#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16))
    is the hero's consequence and does not count as the hazard's.
  - Screen shake stays optional, through `ScreenShake` only (and its Assist option,
    [D62](#d62-screen-shake-a-manager-an-assist-option-and-the-lever-wall-shake-239-step-1)), for heavy impacts.
  - A clocked hazard is heard only while it is in the camera view (D89, D90).
  - Look-alikes share their sound: a sound never tells which of a D63 pair kills.
- **Audit (measured from the code, not in the running game):** complete for the falling stone, the deadfall, the bramble,
  the hive, the thorn root and tuft ([#446](https://github.com/jlogicgames/edgard_in_kimeria/issues/446): `thorn_spring`, awaiting its audition, [#520](https://github.com/jlogicgames/edgard_in_kimeria/issues/520)), the exploding stump and root and the bomb; none needed for the thorn vine (D46) and for a pit's warning (static
  geometry, D3). Gaps, one issue each: the falling branch's landing [#443](https://github.com/jlogicgames/edgard_in_kimeria/issues/443); the falling platform and its log and
  branch skins [#444](https://github.com/jlogicgames/edgard_in_kimeria/issues/444); the puffball and the honey fungi [#445](https://github.com/jlogicgames/edgard_in_kimeria/issues/445) (delivered: `spore_puff`); the thorn root and tuft [#446](https://github.com/jlogicgames/edgard_in_kimeria/issues/446) (done); the RedMob's and
  the boss's strikes [#447](https://github.com/jlogicgames/edgard_in_kimeria/issues/447) (delivered: `whip_crack`, `boss_sweep` and `boss_sweep_counter` play once per strike, hit or miss; awaiting the author's audition, [#520](https://github.com/jlogicgames/edgard_in_kimeria/issues/520)); whether blasts shake [#448](https://github.com/jlogicgames/edgard_in_kimeria/issues/448). Enemy warnings (notice beats, wind-ups under the assist) are
  [#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240). The table: STYLE_SYSTEM.md, "Known gaps"; the trap cards' "Art and sound" rows in TRAPS.md.
- **Gate:** `TrapCatalogTest.everyTrapCardStatesTheConsequenceOfItsAct` (`:core:test`): the card of every catalogue trap
  holds exactly one `Consequence (D93):` statement, and it is one of three forms: sound names in backticks, each a file in
  `assets/audio` listed in `Assets.SOUND_EFFECTS`; `none yet (#NNN)`; or `intentionally none (Dxx)`. Its rules are shown to
  fire by `aConsequenceIsASoundAnIssueOrADecision` and `aCardWithoutAValidConsequenceFails`. Whether the code plays that
  sound at the act is each trap's own test (`DeadfallTrapTest`, `BrambleTest`, `HangingHiveTest`, `FallingTrapsTest`);
  enemies and the boss have no cards, so their strikes ([#447](https://github.com/jlogicgames/edgard_in_kimeria/issues/447))
  are checked in review and in their combat tests.
- **Why:** the death beat already tells the player that they died; what a survivor needs is to see and hear what the
  hazard just did, and a miss teaches as much as a hit. Only the stone and the three traps of wave 5 did that.
- **Not verified:** the new and missing sounds on a real output and the look in the running game
  ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)).

## D95. The sun on the Kimeria arms lights up on the ending page (#416, 2026-11-17)

- **Status:** Accepted by the author, 2026-11-17 (through the orchestrating session), from the phase A frames and
  mock-ups of [#416](https://github.com/jlogicgames/edgard_in_kimeria/issues/416) (part of #350, playtest F12). The
  opening keeps no arms: the author chose not to show the dark arms there.
- **Before:** the ending was text only, although it says Edgard brought light back to the world.
- **Decision:**
  - The ending page draws the Kimeria arms ([ART_DIRECTION.md, "Kingdom arms"](ART_DIRECTION.md#kingdom-arms-350-playtest-f12))
    at 3x (72x108), centred, top at 40 (`IntroPages.TEXT_TOP`). The ending's text starts 20 px under them, at 168
    (`IntroScreen.ENDING_TEXT_TOP`); the page bottom stays 304 (`IntroPages.PAGE_BOTTOM`), so the ending has 136 px of text.
  - The arms are dark (the sun put out) while the page appears. The sun lights as the paragraph that brings the light
    back starts to fade in: the last paragraph before the closing line (THE END), in every language. The steps are
    ember, glow, kindle (0.12 s each) and a flare (0.24 s), then the lit arms hold: 0.6 s in all.
  - D77 holds unchanged: with instant text, and after a press while the page appears, the arms are lit at once (the
    final state). The animation never delays a press; Escape fades the arms out with the frame they had.
  - **Amended 2026-11-21 ([#573](https://github.com/jlogicgames/edgard_in_kimeria/issues/573), author's playtest): the sun
    first, the text after.** The text no longer fades in while the sun lights ("otherwise you just skip past the
    animation"). Before: the sun lit in 0.6 s as the light paragraph started to fade in, 1.6 s after the page began. Now,
    from the start of the ending page: the shield stays dark 0.5 s, the sun lights in 1.0 s (ember 0.2, glow 0.2, kindle
    0.2, flare 0.4; slowed from 0.6 s so it can be seen), a beat of 0.25 s, and only then does the first paragraph start to
    fade in (1.75 s in all; `IntroScreen.TEXT_START_AT`); the paragraph stagger and fade are unchanged. The input rule:
    **the first press during the sun completes it at once (the lit end state, no text yet) and the text then reveals as
    usual; the next press shows the whole page (D77) and a third turns it.** A press never skips the sun unseen, and none
    is delayed beyond the existing 0.6 s lock (re-armed after the completing press, so a held button cannot run on).
    Instant text still shows the lit arms and the whole page at once. The opening (no arms) is unchanged. Escape and the
    skip hint (D118) are untouched. The timing numbers still need the author's sign-off (#573 stays open until then).
  - The art is one strip, `art/ui/arms/arms.grid` exported by `tools/arms_sprites.py` to `assets/images/ui/kimeria-arms.png`
    (six 24x36 frames, 10 colours, binary alpha). Frame `lit` is `art/heraldry/kimeria-arms.png` pixel for pixel, and
    only the sun changes between frames (`tools/test_arms_sprites.py`). `ui/KimeriaArms` cuts the regions once and is
    shared with the main menu (#415).
- **Measured:** with the real body font, the ending ends at 244 of 304 in all five languages today; the healer's ending
  of [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) ends at 237 in EN and at 253 with a German
  written long on purpose (337 characters). `IntroPagesTest` keeps the ending one page under the arms. The ending's
  frame loop allocates 0.0 to 0.1 B/frame (`AllocationBudgetTest.endingArms`, 3 runs, instant text off and on).
- **Gate:** `IntroPagesTest` (one page and the text top; the ending's structure in all five catalogs and in #372's
  text: one closing line in capitals, the same paragraphs as English, and the light paragraph is the first to speak of
  light or the sun), `IntroScreenTest` (the frame sequence, instant text, a press, Escape), `KimeriaArmsTest` (the
  caller's batch colour survives), `AllocationBudgetTest.endingArms`, and `check_sprite_art.py` for the strip.

## D96. The first coin is met in the tree room and the Bat ingredient in the wall room (#417, 2026-11-17)

- **Why.** After the D72 split of `forest-entry` (#376) it still introduced four elements (yellowmob, quicksand, coin,
  ingredient). No `forest-2` room could take the coin (the canopy is at the cap, the wall room had one free slot), and
  `forest-0` and `forest-1` held no pickup at all.
- **Decision (author, 2026-11-17).** The Bat ingredient (`forest-2` id 94) moves from the entry ledge (448, 240) to the top
  of the second wall in `forest-walls` (map y 224; the SpawnPoints layer is drawn 16 px up, so the item's bottom edge is wall id 45's top, y 224, pinned by `RoomReplayTest.theBatRestsOnTopOfTheSecondWall`). A first draft used map y 208, which drew the Bat 16 px above the wall; the review caught it. One new
  coin (`forest-1` id 145) stands on the floor of `forest-1-tree` at (3040, 304), off the climb. Entry keeps its three
  coins, now a repeat.
- **Result.** Introductions: `forest-entry` yellowmob, quicksand (2); `forest-walls` wall-jump, ingredient (2);
  `forest-1-tree` branch-climb, coin (2). The entry `Overload reason` is removed; `RoomCardsTest` is green.
- **Healer and saves are unchanged.** Coins are counted per level (`Level.authoredCoins`) and the healer pays from the coins
  collected since the level began (`entryCoins`), so the forest-1 coin is a sighting that feeds no recipe; the healer text
  already says coins from earlier lands do not count. The Bat keeps its id and level name, so the ingredient bookkeeping
  is the same; only the room that owns it changed (a room retry in the entry room no longer restores it).
- **Measured** (`RoomReplayTest`, `TreeClimbTest`): `forest-entry` 6.80 s required, 10.40 s all coins (unchanged: the
  route never detoured for the Bat); `forest-walls` 8.80 s required; collection return entry 6.12 s (was 6.12-6.72 s with
  the Bat), walls 7.32 s (7.3167 s, re-measured after the Bat moved, all three trips; the Bat is taken on the way); the tree room's two climbs are unchanged. No
  threshold was changed.
- **Tests changed.** `TreeClimbTest` no longer forbids a coin: it asserts exactly one, on the floor line and left of the
  first trunk (still no enemy and no lethal trap); `RoomReplayTest` expects the Bat after the walls replay and not after the
  entry replay, and the room-retry test enters the wall room first.

## D97. Zone messages follow one formula: the cue, then what to do (#373, 2026-11-17)

- **Status:** Accepted by the author, 2026-11-17 (through the orchestrating session), from the phase A draft of
  [#373](https://github.com/jlogicgames/edgard_in_kimeria/issues/373) (text audit G17). Refines the plain voice of
  [D74](#d74-text-style-system-two-voices-glossary-and-a-gate-339-2026-11-16) for `HINT_*` zone messages.
- **Before:** the 19 zone messages of `forest-0` used several shapes ("This lever ... and ... again", three wordings of
  "the exit is ahead", trap lines of up to three sentences and 77 EN characters, one that said a fall "just retries").
- **Decision:** every zone message ends with one imperative that says what to do. It may start with one short statement
  of what the player sees and what follows. At most two sentences and 60 EN characters (T3). Fixed forms:
  - Lever: "Use the lever to `<do X>` or `<undo it>`" ("Use" is the action label `INTERACT`).
  - Trap: "`<the cue>` `<what follows>`. `<how to get past>`", naming the cue as TRAPS.md draws it (cracked branch,
    shadow, cracked platform, smoking stump).
  - Exit: "Head for the exit ahead", one wording everywhere (`HINT_LEVER_EXIT` keeps its own first sentence, D34).
  - Present tense; no retry, lives or key words (T4); no tale images; one verb per trap (the stump "explodes").
- **Applied:** 14 of 19 messages rewritten in all five languages; 5 already fit and stay (`HINT_TUTORIAL_WALK`,
  `HINT_LADDER_CLIMB`, `HINT_WALL_PASS`, `HINT_PLATFORM_RIDE`, `HINT_STONE_EXIT`). Keys and maps are unchanged.
- **Measured:** longest EN 59 characters. With the 20 px zone font in 400 px (`LocalizationLayoutTest`), every message is
  one line in en and pl; `HINT_TRAP_PLATFORM` and `HINT_TRAP_STUMP` take two lines in uk, de and be, and `HINT_STONE_STUMP` two in de;
  all others one.
  The five T3 allowlist entries of #373 are removed.
- **Gate:** T3 of `tools/check_text_style.py` checks the length and the sentence count. Whether a message ends with an
  imperative and uses the fixed forms is a review rule (TEXT_STYLE §1; a script cannot tell an imperative reliably).
- **Not verified:** native-speaker review of the translations ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369))
  and the look in the running game ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)).

## D98. Gravity and the jump launch are integrated per second, so the jump is as high at every frame rate (2026-11-17)

- **Status:** Bug fix from the author's web playtest, 2026-11-17: on GitHub Pages in Chrome the hero jumped lower than
  on the macOS desktop build, and the lever ledge of `forest-1-lever` (32 px above the running lift's highest point) could
  not be reached from the lift.
- **Cause (measured):** `GravityBody.applyGravity` added `gravityAcceleration()` (9.8 px/s) to the velocity once per
  *frame*, not per second, and `Player.playerJump` added a launch-frame lift of `velocity.y * dt`. The desktop build is
  capped at 60 fps (`Lwjgl3Launcher`), but a browser runs `requestAnimationFrame` at the display's refresh rate (120 Hz
  on a ProMotion Mac, 144 Hz on many monitors) with uneven frame times, so gravity per second scaled with the frame rate.
  Feet rise of a standing jump in `JumpFrameRateTest`, before the fix: 30 Hz 119.34 px, 60 Hz 59.67 px, 120 Hz 29.83 px,
  144 Hz 24.86 px, an uneven 120 Hz browser pattern 37.51 px, an uneven 60 Hz pattern 65.99 px. At 120 Hz the 32 px
  ledge is out of reach. Every body with `GravityBody` (the hero, `RedMob`, `YellowMob`, `Boss`) fell faster at high frame
  rates for the same reason; one standing on a floor is snapped to it every frame, so it does not drift (all forest RedMobs,
  YellowMobs and the Boss measured 0.00 px/s at 30 to 144 Hz).
- **Decision:** keep one variable physics step per rendered frame (the `Player.update` comment explains why a fixed
  accumulator was dropped) and make that step independent of its length:
  - `GravityBody.REFERENCE_STEP` = 1/60 s, the frame time the movement was tuned at. Gravity is
    `gravityAcceleration() / REFERENCE_STEP` = 588 px/s².
  - The position step is exact for constant acceleration and reproduces the old 60 Hz semi-implicit Euler arc: that arc
    is the continuous one with the velocity raised by `g * REFERENCE_STEP / 2`, so each step moves
    `v1 * dt + g * dt * (REFERENCE_STEP - dt) / 2`, where the second term is zero at 60 Hz. A step that reaches the velocity
    clamp (terminal fall speed) moves at the clamped velocity, as before.
  - The launch frame's own lift is one reference step at launch speed (4.33 px for a ground jump), whatever the frame time.
    A jump taken in the second `tryJump(0f)` call of a frame (a buffered press on landing) keeps no launch lift, as before.
  - Quicksand (`CollideBody.QUICKSAND_SINK_SPEED`, 9.8 px/s): the quicksand branch stops a body without snapping it, so a
    body at rest sinks by one resting gravity step per frame. That was 9.8 px/s at every rate when gravity was per frame;
    the per-second step alone gives `g * (dt + 1/60) / 2` per second: 14.70 px/s at 30 Hz, 7.35 at 120, 6.94 at 144, 8.84
    and 10.75 on the uneven patterns. A resting step in quicksand now moves exactly `QUICKSAND_SINK_SPEED * dt` (the
    correction is zero at 60 Hz). It is the same branch for the hero and every enemy.
  - Coyote time (`Player.COYOTE_SECONDS`, 0.25 s): after walking off a ledge the hero may jump until his fall speed passes
    147 px/s. That was 15 frames, so 0.25 s on desktop and 0.125 s on a 120 Hz browser; it is now 0.25 s at every rate,
    still the 15th frame at 60 Hz.
- **Measured after:** the standing jump rises 59.67 px at all six patterns (spread 0.00 px), the same 59.67 px as before
  the fix at 60 Hz. The jump lasts 0.900 s at 30 and 60 Hz, 0.892 s at 120 Hz, 0.889 s at 144 Hz and 0.896 and 0.895 s on the
  uneven patterns: the same within one frame, because landing is seen on a frame boundary. Quicksand sinks 9.80 px/s at all six
  patterns, for the hero and for a YellowMob. The lift-to-ledge hop of `forest-1-lever`, taken at the top of the lift's run, lands on the ledge at
  every pattern. All room tests, `RoomReplayTest` times and `AllocationBudgetTest` pass unchanged (60 Hz steps).
- **Left as it is, with the reason:** two per-frame rules remain and do not change what the player can reach. The wall
  clamber damps the fall with `velocity.y *= 0.1` per frame, which holds the hero within about 1 to 2 px/s at any rate.
  The one-way platform catch-up margin (`CollisionUtils`, "at rest" below 20 px/s) sees 9.8 px/s after one 60 Hz step and
  19.6 px/s after a 30 Hz step; only frames longer than 34 ms (below 29 fps) exceed it, and then a rider on a descending
  platform may re-snap once.
- **Gate:** `JumpFrameRateTest` pins the apex to 59.67 px at 60 Hz and its spread across 30, 60, 120, 144 Hz and two
  uneven browser patterns to 0.1 px, the air time to 0.9 s at 60 Hz and to it within one frame of the coarser rate at
  every pattern, the quicksand sink speed to 9.8 ± 0.2 px/s for the hero and an enemy, and rides the reported lift to the
  ledge at each pattern.

## D99. Blood comes only from the sword; a fireball hit sprays none (#397, 2026-11-18)

- **Status:** Accepted by the author, 2026-11-18.
- **Decision (author):** a fireball hit on any enemy, the forest boss included, sprays no blood. Blood comes only from a sword hit (D65 for monsters, the boss landed-hit spray for the boss). This replaces the earlier BOSS.md rule that a fireball hit on the boss sprays 32 droplets.
- **The elixir never touches the fireball:** damage stays 2 per landed fireball (BOSS.md), and its look and sound are the same with and without the elixir: no empowered variant, no impact shake.
- **What a boss fireball hit shows:** the 75 ms impact sound, the 250 ms glow over the lost HP slots and the heavy fireball burst (#397), drawn over the boss's hurt flash.
- **Why:** the fire is the healer's gift (D66) and reads as burning, not cutting; blood stays with the blade, so the two weapons feel different.
- **Tests:** a boss fireball hit with and without the elixir removes 2 energy, leaves zero live blood drops and spawns the same burst; a sword hit sprays as before.

## D100. The forest beetle: a kamikaze that hears, commits and bursts; monsters notice in body language and are met in play (#327, #240, #398)

- **Status:** Accepted (author, 2026-11-18).
- **Decision:**
  - **Body.** One strip of ten 24×16 frames (`crawl1`, `crawl2`, `look`, `wiggle`, `crouch`, `air`, `brace`, `bright`,
    `white`, `dead`), 15 colours with the moon rim; drawn about 14 px tall, half the hero; a dark green-bronze shell, no
    reserved hue. *Recoloured by #236 (2026-11-20): the danger family, a dark crimson `86101f` shell with `ff6b57` eyes; its body
    (D101) a dusky indigo, L 0.176 to 0.242 (`docs/ART_DIRECTION.md`, Forest beetle).* Body hitbox `rect(1, 2, 22, 14)`. **Contact is harmless** (no hurtbox): only the burst kills.
  - **Patrol** 24 px/s inside the authored range (`offNeg`/`offPos`); it never leaves the range, chasing included.
  - **Hearing** 96 px either side, centre to centre, within a 48 px band above its floor line; lost beyond 144 px or after
    1.0 s out of the band.
  - **Notice, in body language** (#240): it stops, raises its head and wiggles its antennae (`look`, `wiggle`, `look`,
    `wiggle`, 0.1 s each), crouches (0.06 s), hops on the spot (`air` drawn 2 px up, 0.1 s; the hitbox stays) and lands
    (0.04 s): 0.5 s, then the chase. Its own call `beetle_notice` (a dry double click) plays at the start. Not scaled by
    the assist: it is information, not the lethal warning (D7).
  - **Chase** 70 px/s (the hero runs 100).
  - **Trigger:** the hero's hitbox touches the body grown 8 px left and right, at body height; a hero above it does not
    trigger it. The trigger is a fixed inner part of the blast box, so it cannot drift from the reach (the purpose of
    D10's melee rule, which does not fit a centred burst).
  - **Commit (the Broforce rule):** once triggered it stops where it is and bursts after
    `TrapWarnings.duration(DEFAULT_WARNING_SECONDS)`, sampled once: 0.8 s, 1.2 s with the assist. Nothing the hero does
    moves, stops or restarts it; the hero lives by being outside the blast box at the instant. This closes #240's item
    "enemy wind-ups follow the trap assist" for this enemy.
  - **The warning is the body:** a 1 px shake, a blink between its own colours and the `bright` frame that quickens from
    0.2 s to 0.05 s, then `white` for the last 0.12 s; pale specks born on the blast box's edges are drawn in to the body,
    so the reach is shown. Heard: `beetle_windup` (0.8 s) or `beetle_windup_long` (1.2 s).
  - **Burst:** an 80×32 box centred on the body, bottom on its floor line, tested once through
    `player.collidedWithActor(false)` (D47); a white flash, a flat white spray inside the box and grey steam; `beetle_pop`.
    No shake (a small burst; D62, #448). *Amended by [D136](#d136-the-beetles-burst-is-seen-felt-and-heard-559-2026-11-24): the burst now has a halo, embers, a louder sound and a small shake for a near survivor.*
  - **Answers:** a fireball or a thrown stone kills it in any state, the wind-up included (the one save after it
    commits), without a burst: the `dead` frame under a small grey poof, removed at 0.3 s. *Update (D101, 2026-11-18): the
    body stays, on its back; the poof is larger, paler and starts 0.08 s after the hit, so it reads over the fireball's
    ring.* The sword in any state, and a stomp on a beetle that has not triggered, burst it at once with no wind-up; the
    hero is inside the box, so melee kills the hero. A stomp on a triggered beetle bounces the hero off without a kill. No blood (D65 does not apply to it).
  - **Shared pattern for every monster that notices the hero (#240):** the notice is the creature's own body language
    (a stop and a reaction drawn in its frames) and a short call in its own voice; no mark over its head and no shared
    alert sound, which do not belong in a fairy tale. Its length is set per monster, about 0.5 s, not scaled by the assist;
    the lethal wind-up after it takes its length from `TrapWarnings`.
  - **New monsters are met in play:** no monster (the beetle, the spider, the snail, the flying beetle, the wolf) gets a
    teaching room or a hint text; the player learns it by meeting it. Its first room lists it as an introduction on its
    room card (D72), with no zone message about it.
  - **Room rules:** D3/D6 as for every enemy (reset on death; killed or burst stays gone in passed rooms and on revisits;
    one left winding up returns calm at its spawn). Load-time checks: range ±40 px inside the room on gapless floor; no
    blast position covers the entrance body, `RoomExit` or a `Checkpoint`. Placement: no beetle range in front of the
    forest-2 rubble at x 96 to 128.
  - **Effects** on `KimeriaParticleSystem` only (one owner per `Level`); the beetle's strip is the only sprite.
- **Measured from the code (hero) and the concept sheet:** from the trigger the hero needs 21 px (0.21 s) to leave the
  box, 29 px (0.29 s) from contact, leaving at least 0.51 s of the 0.8 s warning (0.91 s with the assist); a jump keeps
  the feet above 32 px for 0.61 s; the bounce off a triggered beetle keeps them above it from 0.08 s to 0.81 s after the
  stomp. Contrast (`tools/check_contrast.py` metrics, frames at the seven RedMob and YellowMob spawns of the three maps):
  outline 0.329 (`look`, `wiggle`) to 0.515 (`white`), active floor 0.28; p90 chroma 0.055; along the floors it falls to
  0.21 only in front of the forest-2 rubble, hence the placement rule.
- **Why:** a bomb on legs puts the explosive family's lesson in motion: read the commit, then get clear. Harmless contact
  and a fixed spot keep every death attributable to the burst the player watched wind up (D5). Melee bursts it because the
  player should learn to keep their distance; the ranged answer costs ammunition or a stone. A creature that shows it has
  noticed you with its own body stays inside the tale; a floating mark does not.
- **Exception, stated:** the instant burst from the sword or a stomp has no D7 wind-up. It is the consequence of the hero's
  own act on a creature known to burst; the first meeting may cost one retry (D2, D3).
- **Verified in play:** the author played `forest-1-guard` and `forest-1-tree` with the placed beetles on 2026-11-18 and
  approved the look, the timing and the sounds ("all good", #327). No novice playtest (#135).
- **Built (#327):** `entity/enemy/Beetle.java` (Tiled type `Beetle` in `SpawnPoints`, `offNeg`/`offPos` as the mobs; the
  load-time checks are `Beetle.problem`, run by `Level` for every room), `effects/BeetleFx.java` (one per `Level`; pools
  108 soft and 296 hard since D101, from measured peaks of 85 and 236; 84 and 240 since #328, from peaks of 67 and 192, once the
  body's crumble and burn moved to the shared `RemainsFx`; the look check of 2026-11-18 made the wind-up specks
  denser, 50 to 150 a second instead of 30 to 90, and brighter), the sheet `assets/images/objects/monster-beetle.png` from
  `art/objects/monster/beetle.grid` (`tools/forest_trap_sprites.py monster:beetle`), the four sounds from
  `tools/audio/generate_sounds.py` (seeds 6801-6806). The wind-up sound is a one-shot like the other warnings: it plays to
  its end even when the beetle is killed during it. Placed by #327 (cards approved by the author 2026-11-18): it is
  introduced in `forest-1-guard` (x 2632, `offNeg` 2, `offPos` 2, past the tunnel) and developed in `forest-1-tree` until #500 made that a reward room and removed it (x 3264,
  `offNeg` 0, `offPos` 6, at the sound tree's foot); see `docs/ROOM_CARDS.md`; the placements are tested in
  `Forest1CombatTest` and `TreeClimbTest`. Tests: `BeetleTest` (the real `Level` on `beetle-test.tmx` and two failing
  fixtures), `BeetleFxTest` (capacity), `AllocationBudgetTest.beetleThroughTheRealLevel` (0.2 B/frame in 3 runs, ceiling
  8; 0.1 with the body of D101).
- **Placed in forest-2 (#327, card approved by the author 2026-11-18):** on the exit ledge of `forest-fissure` (map x 3508,
  y 224, `offNeg` 0, `offPos` 2; blast at most x 3480-3592; the `RoomExit` moved 16 px right to x 3600), tested in
  `ForestFissureTest`: the lure 9.30 s from the entrance, a fireball from the lip 8.60 s, a death in its burst after the
  climb 8.35 s at start offset 0 and 8.75 s at worst after the wait (11.70 s from entering, the wait included). A hero who
  keeps walking clears the blast at every crawl phase, so the room repeats the beetle rather than developing it. Contrast
  at this placement (the `check_contrast.py` rows for forest-2 x 3508, the ten frames over the ledge): outline 0.334 to
  0.519, body at most 0.591, the same as at both forest-1 spawns.

## D101. Monster bodies: a monster killed quietly leaves its body, posed apart from the living, which reacts to the hero and is finished by a weapon (#327, #240, #398)

- **Status:** Accepted (author, 2026-11-18). *Amended by [D119](#d119-a-stone-passes-through-bodies-everyone-sways-bodies-and-bushes-amends-d101): a stone flies through a body, and any moving actor rocks it.*
- **Decision:**
  - **What stays.** A monster that is killed and does not burst or explode leaves its body where it fell. A monster
    that bursts or explodes leaves nothing: the beetle's burst (whether it bursts on its own, under the sword or under a
    stomp, D100) and the flying beetle's blast. Its end is the effect.
  - **The pose tells the body apart (the readability rule).** A body's pose differs from every live frame of its monster,
    so it never reads as a monster at rest: the beetle lies on its back, legs up. Every future body follows this rule. A
    recoloured live frame is not a body. The pose is the guarantee, not the palette.
  - **Contrast tier (D76): decor.** A body is harmless scenery in the play plane that reacts to the hero, like the bush
    (D64), so it belongs to the decor tier. It shows the kill in the monster's own active `dead` frame, then cross-fades
    into the same frame in an ink-free, muted palette (the beetle: hold 0.3 s, fade 0.6 s). The active frame cannot stay,
    because it fits neither tier as a lasting sprite: the beetle's `dead` frame measures outline 0.217 to 0.379 along the
    floors of the three maps (over the decor ceiling of 0.20, under the active floor of 0.28 at its worst) and has ink
    pixels down to OKLab L 0.146. The decor variant measures outline 0.083 to 0.156, body 0.104 to 0.178 and p90 chroma
    0.018, with no ink. It is the same pixels, recoloured by the grid pipeline, and is not a new drawing.
  - **Harmless, in nobody's way.** A body has no hurtbox. It blocks neither the hero nor monsters nor projectiles, and it
    counts as defeated (D6), so the combat loops never treat it as an enemy.
  - **It reacts to the hero passing.** When the hero's body enters the body's box grown 6 px either side, the body takes
    a push the way the hero moves and rocks on its back. This is a damped swing of the same frame about the bottom of the
    shell: the beetle's first peak is 10°, at about 2.5 swings a second, and it comes to rest about 1.4 s after a push.
    Each new arrival pushes it again. The rocking is reactive, not an idle animation (D76).
  - **Weapons finish it.**
    - **The sword, a thrown stone or a stomp** crumble it: it is gone at once, brown and shell chips fall to the floor
      under a little dust, and `remains_crumble` plays. A stomp gives the hero the normal stomp bounce.
    - **A fireball or a thrown torch** burns it: it darkens for 0.3 s inside flames in the fireball's own ramp under
      one warm glow, ends in a dark wisp, and `remains_burn` plays.
    - **No weapon is spent or stopped by a body.** The fireball and the stone fly on, and the sword's swing still
      reaches a live monster behind it. A body never shields a monster and never costs ammunition. This holds for
      every body.
  - **Room rules (D3, D6).** A body is not saved room state. A death or a room reset rebuilds the room as authored, so
    the monster is back alive and its body is gone. A body lasts until the hero leaves the room. A monster killed in a
    passed room stays gone on revisits, and so does its body.
  - **No cap per room.** A room has at most one body per killed monster. The bodies are the monsters' own objects, and
    the particle pools are sized from measured worst cases.
  - **Sound.** The rocking is silent: it plays on every pass and would nag. `remains_crumble` (a dry crunch) and
    `remains_burn` (a flaring hiss with crackles) are shared by every monster's body.
- **Applies to:**
  - **Beetle (#327): yes, built.** A fireball or a stone kills it quietly and leaves the body. The sword and a stomp
    burst it (D100) and leave nothing.
  - **Spider (#328): yes, built (D102).** Its body lies on its back with its eight legs curled up, and rocks like the beetle's.
  - **Snail (#329): yes, built ([D106](#d106-the-forest-snail-it-looks-ahead-holds-a-line-and-spits-a-slow-gob-cross-its-ground-in-the-reload-329-240-398)).** Its empty shell stays, rolled onto its back with its mouth open upward, and rocks like the others.
  - **Flying beetle (#330): partly.** Its own blast leaves nothing. Killed in the air by a fireball or a stone, it falls
    and its body lies on the floor below. Over a pit it falls out and leaves nothing.
  - **Wolf (#331): yes, with its own reaction ([D115](#d115-the-forest-wolf-it-sees-far-outruns-the-hero-and-lunges-along-a-line-jump-it-then-strike-331-240-398)).** A large body does not rock. It twitches (a 1 px shudder of the same
    frame) when the hero passes. It crumbles and burns like the others.
  - **Bat (existing): yes.** It falls when killed. Over a pit it leaves nothing.
  - **RedMob and YellowMob (existing): the author decides per mob in [#478](https://github.com/jlogicgames/edgard_in_kimeria/issues/478).** Both are in rooms the author has played,
    and their death animation and the sword's blood (D65) would change.
  - **Boss: no.** Its defeat is a scripted scene with the healer.
  - **Traps** (stump, root, bomb, hive): **no.** They are not monsters, and their spent states are already authored.
- **Why:** the body is the clearest record of what the hero did. It keeps a quiet kill readable after the hit's flash,
  where the fireball's ring hid the old 0.3 s poof (look check, 2026-11-18). It also turns the room into a small toy that
  answers every weapon, at no cost in danger or puzzle state. A burst leaves nothing because the burst is the death: a
  body there would contradict what the player saw. The pose rather than the colour carries the difference, because a
  colour-only difference is the look-alike problem of F15 in reverse.
- **Built (#327):**
  - **Code.** The interface `entity/enemy/Remains.java` (`isRemains`, `remainsInto`, `crumble`, `burnUp`). The beetle's
    `DEAD` state is the body, plus a `BURN` state. The sword (`Player`), the thrown stone or torch (`Carryable`) and the
    fireball (`Fireball`) test bodies after live enemies and fly on.
  - **Effects.** `BeetleFx.crumble`, `burnGlow`, `burnFlame` and `burnOut`, with the fire colours shared from
    `FireballFx.RAMP`.
  - **Art.** The decor body sheet `assets/images/objects/monster-beetle-body.png`, from
    `art/objects/monster/beetle-body.grid`.
  - **Contrast gate.** `check_contrast.py` measures every body on every floor top of every shipped map (`BODY_SWEEP`),
    since a body has no placement of its own.
  - **Sounds.** Seeds 6807 and 6808.
  - **Gate:** `RemainsTest`. It lists every `Remains` class and fails on an unlisted one. Through the real level it
    checks that the pose differs (the closest live frame differs in 30.5 % of the silhouette, floor 25 %), that the
    body counts as defeated, that it rocks, crumbles and burns, that a reset or a passed room leaves no body, and that a
    burst leaves none.
  - **Tests and budgets.** Behaviour in `BeetleTest`. `BeetleFxTest` sizes the pools at 108 and 296 from peaks of 85 and
    236, including three bodies crumbling and three burning at once. `AllocationBudgetTest.beetleThroughTheRealLevel`
    measured 0.1 B/frame in 3 runs.
  - **The other monsters follow in [#478](https://github.com/jlogicgames/edgard_in_kimeria/issues/478).**
- **Update (#503, 2026-11-19): a fireball wins the same-frame tie too.** The hero updates after the monsters, and a cast ball used
  to join the object list a frame late, so on the frame a bite (or the snail's gob, D106) became due the ball had not moved, and on
  the next one the monster landed its hit first and the ball met a hero who could no longer fight. Now a ball cast this frame flies
  its first step on that frame (`Level.stepNewFireballs`, straight after the hero's update and before the boss's combat), so it
  kills, burns a gob or lights a trap before the pending hit lands: the sword and the fireball win the tie (and, since #505, a thrown stone: see the next update). Its second
  step is the next frame's, as before. A ball in flight when the hero cannot fight any more goes out like a burn-out, seen and
  heard (`fireballFx().fizzle`, the quiet fizzle sound, D93), no longer silently. The beetle needs no tie: its burst is
  immediate in its own update, before the hero can cast; a ball cast on the burst frame is never spawned (the hero is dead, nothing is
  spent), pinned by `BeetleTest.aCastOnTheBurstFrameSpawnsNoBallAndSpendsNoCharge`. Tests: `SpiderTest.aFireballCastOnTheFrameTheFangsArriveWinsTheSameFrameTie`,
  `SnailTest.aFireballCastOnTheFrameTheGobReachesTheHeroWinsTheTie`, `FireballEffectTest.aBallInFlightWhenTheHeroDiesFizzlesSeenAndHeard`.
- **Update (#505, 2026-11-20): a thrown stone wins the same-frame tie too.** `Carryable.release` only set the stone's velocity, so a
  stone thrown on the frame a bite was due first moved in the next frame's `updateWorld`, after the monster had updated and landed
  the hit. The rule is the fireball's: a throw flies its first step on its release frame. Frame order before: `updateWorld` (the
  monsters; the due bite lands on their next update) then `player.update` (release, velocity only) then the next frame's
  `updateWorld`, where the monster hits first. After: `updateWorld`, `player.update` (release), `Level.stepNewThrows` (the stone's
  first step, straight after `stepNewFireballs`), and the next frame's `updateWorld` is its second step; the stone was already
  updated this frame as held, and `Carryable.firstStepPending` (set by a throw, cleared by any update or restore) lets only a throw
  just released take the extra step, so nothing moves twice in a frame. The first step runs the whole `Carryable.update`: it breaks
  a gob (`Breakable`) before the enemy test, crumbles bodies (D101), kills an enemy, lights a stump and knocks down a hive, as on any
  step. **The tie is real for the spider, and for the snail when it stands above the hero's floor:** the gob flies 7 px above the
  snail's floor (`Snail.GOB_HEIGHT`) and a stone leaves the hero's hands above that band, so on a shared floor the two never meet
  on the release frame whatever the frame order (a stone breaks a gob at range, D106); but with the snail 6 to 16 px up (a one-tile
  ledge is 16) the gob is at the stone's height and, before this change, the hero died with the stone a frame late. Tests:
  `SpiderTest.aStoneThrownOnTheFrameTheFangsArriveWinsTheSameFrameTie`,
  `SnailTest.aStoneThrownOnTheFrameTheGobArrivesFromALedgeWinsTheTie` (lifts 6, 8, 12, 16; fails without `stepNewThrows`),
  `SnailTest.onASharedFloorAStoneThrownWhenTheGobArrivesIsAboveItsBand` (pins the shared-floor geometry),
  `SnailTest.aThrownStoneNeverMovesTwiceInAFrame` (one step of 220 px/s on the release frame, one on the next), and
  `AllocationBudgetTest.stoneThrowWithTheRealKeyPress` (a real pick-up and throw, 3.5 B/frame over the same room with none;
  ceiling 16). A thrown torch takes the same first step, and its flame is updated once that frame by construction (`Carryable.fly` is the flight alone and does not touch the flame; no test observes the flame's clock).
- **Update (#328, 2026-11-19): the second body.** The spider leaves one (D102). The crumble and the burn moved out of `BeetleFx`
  into `effects/RemainsFx.java`, one per `Level`, shared by every body: each monster passes its own chip colours (the beetle's
  `BeetleFx.SHELL_CHIPS`, the spider's `SpiderFx.SPIDER_CHIPS`); pools 57 and 218 from peaks of 45 and 174 (`RemainsFxTest`:
  three bodies crumbled and three burnt, then as many again 0.2 s later). The rocking is `entity/enemy/RemainsSwing.java`, the
  same swing for both. A sword kill leaves the body: the swing that killed a monster skips its body for the rest of that swing
  (`Player`), so the blade does not crumble what it has just killed. `RemainsTest` lists the beetle and the spider; each case
  names how its body reacts to the hero passing. `BODY_SWEEP` in `check_contrast.py` measures both bodies.

- **Update ([#571](https://github.com/jlogicgames/edgard_in_kimeria/issues/571), 2026-11-21, [D117](#d117-a-monster-body-keeps-its-live-palette-ink-and-moon-rim-the-pose-is-the-cue-571-2026-11-21)): the body keeps the live palette.** The "Contrast tier (D76): decor"
  bullet above is superseded: a body is the `dead` frame in the monster's live palette, ink and moon rim, with no recolour,
  tint or halo, and is measured as active. The pose stays the readability rule; the behaviour (harmless, rocks, crumbles, burns) is unchanged.

## D102. The forest spider: it hears, notices, and pounces at where the hero stood; make it miss, then strike (#328, #240, #398)

- **Status:** Accepted (author, 2026-11-19: the card, the eight recommendations, the art with eight legs and the five sounds).
- **Placement revised by [D105](#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) (#499), done by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (2026-11-19):** the three spider rooms were reworked with the snail (D106): `forest-1-spider` grew to 1120 px and meets the spider, then the snail, then a falling stone and a thorn tuft; `forest-1-climb` adds a stone and a snail on the block top; `forest-1-route` a snail under the slab (D105, "Reworked under #500"). The spider's rules are unchanged.
- **Decision:**
  - **Body.** One strip of nine 32×16 frames (`crawl1`, `crawl2`, `rise`, `tap`, `crouch`, `leap`, `bite`, `recover`,
    `dead`), 12 colours, 13 with the moon rim; drawn about 13 px tall; a plum-black abdomen with an ochre chevron, eight tan
    legs with high knees, all rooted on the cephalothorax (four near, four far); no reserved hue. *Recoloured by #236 (2026-11-20):
    the danger family, a dark crimson `86101f` body, `ff6b57` eyes, dusty-rose legs; its body (D101) a dark dried umber, L 0.143 to 0.253.* Body hitbox
    `rect(2, 3, 20, 13)`. **Contact is harmless** (no hurtbox): only the fangs kill, during the leap and the bite.
  - **Patrol:** it skitters (48 px/s for 0.3 s, still for 0.3 s) inside its authored range (`offNeg`/`offPos`) and never
    leaves the range on foot. *Amended by #497 (see "Leap past the range end" below): its leap may leave the range by up to its reach.*
  - **Hearing:** 112 px either side, centre to centre, within a 40 px band above its floor line; lost beyond 160 px or
    after 1.0 s out of the band. *Amended by [D103](#d103-the-forest-spider-climbs-and-drops-a-route-of-floors-walls-and-a-silk-thread-two-strikes-one-tell-483-328):
    a 144 px circle around the body's centre, in its room, blocked by nothing; lost after 1.0 s beyond 192 px.*
  - **Notice, in body language (D100's shared pattern):** it freezes and rises on its legs, drums the ground twice with a
    raised foreleg and settles (`rise` 0.1 s, then `tap`, `rise`, `tap`, `rise`, `crawl1` 0.08 s each): 0.5 s, not scaled by
    the assist; its own call `spider_notice`. No mark, no shared sound.
  - **Chase** 85 px/s (the hero runs 100). It chases to a target (under the hero, or its range's end) and the last step is
    clamped to land on it; at the target it holds still, facing the hero, with no overshoot and no turning back (#488; a wall
    climb, D103, clamps the same way). Held under a hero it cannot reach, it keeps its x and its facing for as long as he does.
  - **Commit:** when the hero's hitbox touches the pounce box (from the body's middle to 48 px ahead of its front, from the
    floor line to 8 px above its top) it locks its target, the hero's centre at that instant, clamped into its range and
    never behind it, and winds up on the spot for `TrapWarnings.duration(BITE_WARNING_SECONDS)`, sampled once: 0.5 s,
    0.75 s with the assist. Nothing the hero does changes it.
  - **The warning is the body and the reach:** the `crouch` frame (forelegs raised, fangs open, drawn 1 px back from the
    hero) and pale silk glints, 60 a second, that run along the floor from its fangs and gather on the landing spot;
    `spider_windup` (`_long`).
  - **Leap past the range end (#497, 2026-11-19, author's option B).** A spider at its range end used to commit on a hero standing
    just past it, inside its 48 px pounce box, and clamp the leap to the range: it landed where it stood, bit nothing, recovered,
    committed again and leapt in place for ever while the wind-up glints ran to the hero. Now the leap may leave the range by
    up to `POUNCE_REACH` (48 px) towards the hero, never behind the spider, and is cut short by what the spider cannot cross:
    it goes in 2 px steps over **authored floor under the whole body at the spider's floor line** (a collision box of the map, as
    `monsterFloors` counts for the load check: not quicksand, not a ladder's top step, not an object that moves) and stops at the
    last step that has it, so it never leaves the floor over a gap or a lip; a solid in the body's way still stops it at runtime
    as before and it bites against it. **It commits only when the landing, or the flight to it, puts the fangs on the hero**
    (swept from where they are now to where they would land, a solid cutting the flight at its face, on the hero's x span and in
    the height of the bite box): otherwise it holds, facing him, never leaping in place. This replaces D102's leaping at a hero
    behind a wall and biting against it; a leap still stops against a solid at runtime and bites there when the fangs reach him
    (`aCrateInTheWayStopsTheLeapAgainstItsFaceAndBitesThere`). The gate predicts the stop on the 2 px grid at the floor line and the leap is locked to that same stop (`leapToX`), so prediction and flight agree (`aLongFlightAndAMarginalOverlapStillBiteAgainstTheFace`). A bump of 7 px or less (the 6 px arc plus the 1 px raise) would be flown over at runtime but the gate treats it as a wall, so the spider holds rather than leaping over it. The recovery does not snap it back: it crawls back into its range at the chase speed and the #488 hold
    rule applies (the last step lands on the target, no overshoot, no turning); `clampToRange` only stops it moving further out.
    A route spider (D103) leaps only along a floor leg: its leg ends are the polyline's vertices, so past a vertex where the floor
    goes on it overshoots like a box spider, and where the leg ends at a wall (a solid) or a lip (no floor) the clamp cuts the
    leap there. **Load check:** the standing-place reach grew from the pounce box (+70, -46 px from `position.x` at a range end) to the fangs' real
    maximum extent (`FANG_EXTENT`: 48 + the fangs' middle 20 + half their width 3 = +71, -47), so no standing place is inside it;
    `floorProblem` still requires gapless floor under the body's sweep over the range; floor under the
    reach is not required at load: the runtime clamp to the real floor is enough, because a gap beyond the range end is a map the
    author may want (the spider simply cannot cross it) and a hero across it is out of the fangs' reach. Measured pins that moved:
    `forest-1-spider` walk-in death 3.58 to 2.32 s, `forest-1-route` mid-slab death 5.22 to 5.55 s; the detour's spider no longer
    winds up while the hero climbs and is stomped by the drop in both assist modes (`Forest1SpiderTest`, `Forest1RouteTest`,
    `SpiderTest`: `spider-edge.tmx` has a gap beyond the range end, `spider-crate.tmx` a crate in the flight). `Forest1RouteTest.aSecuredSpentStep…` removes the slab's spider before its teleporting `assertPadClosed`, as the geometry-only test beside it does: a teleported hero is a legitimate bite.
  - **Pounce:** a 0.25 s low leap (a 6 px arc; the body really moves) to the locked spot (`spider_leap`), leaving a silk line
    in the air, then the bite (`bite` frame 0.1 s, two fang glints and floor dust, `spider_bite`, hit or miss, D93). The fang
    box (`rect(17, 3, 6, 8)` in the leap frame, `rect(17, 7, 6, 9)` on the floor, inside the drawn head and fangs) kills
    through `player.collidedWithActor(false)` during the leap and the bite, swept every frame from where the fangs were.
    **A solid stops the leap:** a wall, a crate, a block or a platform's side in the way (`Level.blocksObject`) ends the
    flight against it, short of the spot, and it bites there; it never passes through. This is a runtime rule rather than a
    load-time one, so a solid that moves into the range (a lowered wall) is covered too. A bite that reaches the hero on the
    bite's last frame keeps the spider biting until it lands, so a bite never lands during the harmless recovery.
    It bites once per leap: a hero the bite cannot kill (the developer's invulnerability, the victory walk) is not bitten again
    every frame, and the spider always goes on to recover.
    Then **recovery**: 0.6 s flattened and harmless, not scaled by the assist; then the chase again, with a full wind-up for
    each new pounce.
  - **Answers:** a fireball, a thrown stone or torch, the sword and a stomp (with the normal bounce) kill it in any state,
    the leap included, and it leaves its body (D101). A bite that reaches the hero lands at the start of the spider's next
    frame, so a killing hit on the same frame wins the tie. The sword is a trade only by timing: rushing it during the wind-up
    works for a hero who goes at once (31 px of 48 to cover in 0.5 s); a swing that is live when it arrives meets it; a swing
    that ends before it lands, or no swing, is a bite. After a miss its recovery is a free kill. The sword's kill sprays the
    light blood of D65, as on every monster it kills; fire and stones spray none (D99).
  - **Body (D101):** on its back, legs curled up; it rocks, crumbles and burns as the beetle's.
  - **Room rules:** D3/D6 as for every enemy: a death resets it calm at its spawn; killed, it stays gone with its body in a
    passed room; one left mid-pounce comes back calm. Load-time checks (`Spider.problem`, run by `Level` for every room), on the
    spider as the game builds it whatever the Tiled object's size (the 32×16 frame): the frame's sweep inside the room, the
    body's sweep on gapless floor, and no standing place (the entrance body, the `RoomExit`, a `Checkpoint`) inside the pounce
    box at any point of the range facing either way (`position.x` - 46 to `position.x` + 70). Met in play: no teaching room, no hint text (D100). Placed by [#484](https://github.com/jlogicgames/edgard_in_kimeria/issues/484)
    on room cards: first met alone in `forest-1-spider` (see D103, "Placed").
  - **Effects** on `KimeriaParticleSystem` only (`SpiderFx`, one per `Level`; the body's crumble and burn in the shared
    `RemainsFx`, D101 update); the spider's strip and its decor body are the only sprites.
- **Why:** the beetle teaches the player to stay out of melee; the spider teaches them to bait and punish. Its lunge
  outreaches the sword, so standing still in its reach is the one mistake; the locked target makes the dodge one step and
  the same on every retry (D7), and the long recovery rewards the player who made it miss. Harmless contact keeps every death
  on the fangs the player watched wind up (D5). The wind-up is shorter than the shared 0.8 s because the target is locked:
  at 0.8 s the dodge would be trivial and the rush would always win.
- **Measured:** from the code, the hero needs 11 px (0.11 s) to step out of a locked bite, which leaves 0.39 s of the 0.5 s
  warning (0.64 s with the assist). Contrast (the `check_contrast.py` metrics at the seven RedMob and YellowMob spawns and
  the three beetle spawns of the three maps): outline 0.300 (`recover`) to 0.331 (`crouch`), `leap` 0.321, over the active
  0.28; the decor body over every floor top of the three maps (`BODY_SWEEP`) outline 0.075 to 0.184 (decor ceiling 0.20). The
  pose: the body differs from its closest live frame (`recover`) in 48.3 % of the silhouette (`RemainsTest`, floor 25 %).
- **Built (#328):** `entity/enemy/Spider.java` (Tiled type `Spider` in `SpawnPoints`, `offNeg`/`offPos` as the mobs, 32×16),
  `effects/SpiderFx.java` (pools 64 soft and 104 hard from measured peaks of 50 and 83, `SpiderFxTest`),
  `TrapWarnings.BITE_WARNING_SECONDS`, the sheets `assets/images/objects/monster-spider.png` and `monster-spider-body.png`
  from `art/objects/monster/spider.grid` and `spider-body.grid` (`tools/forest_trap_sprites.py monster:spider`), and the five
  sounds from `tools/audio/generate_sounds.py` (seeds 6901-6907). The wind-up sound is a one-shot like the other warnings.
  Tests: `SpiderTest` (the real `Level` on `spider-test.tmx`, a low wall in `spider-wall.tmx`, and two failing fixtures,
  `spider-entrance.tmx` and `spider-gap.tmx`; the same-frame tie is driven through `Level.update`: the objects update before the hero, and the tie
  holds because the bite lands on the spider's next frame and a sword, fireball or stone takes effect on its own frame: #503, #505), `SpiderFxTest`, `RemainsTest`, `AllocationBudgetTest.spiderThroughTheRealLevel` (0.0 B/frame in 3 runs,
  ceiling 8). Placed in three forest-1 rooms by #484 (D103, "Placed"); the author judges the look, the timing and the sounds there in play.

## D103. The forest spider climbs and drops: a route of floors, walls and a silk thread; two strikes, one tell (#483, #328)

- **Status:** Accepted (author, 2026-11-19: the Phase 1 card and its ten recommendations, with one change: the wall poses are
  drawn, not turned, so the legs grip above and below the body). Built (#483); placed in three forest-1 rooms (#484, below).
- **Placement revised by [D105](#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) (#499), done by [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500) (2026-11-19):** the three spider rooms were reworked with the snail (D106): `forest-1-spider` grew to 1120 px and meets the spider, then the snail, then a falling stone and a thorn tuft; `forest-1-climb` adds a stone and a snail on the block top; `forest-1-route` a snail under the slab (D105, "Reworked under #500"). The spider's rules are unchanged.
- **Amends D102:** hearing is a **144 px circle**, from the body's centre to the hero's centre, in the spider's own room, and
  **nothing blocks it** (walls and floors do not: it feels the hero through rock and roots). It loses the hero after **1.0 s
  beyond 192 px**. The 40 px band is gone for every spider. Everything else in D102 holds on a floor.
- **Decision:**
  - **Route.** A spider may be authored as a Tiled **polyline** of type `Spider` (`Spider.Route.trace`); a box with
    `offNeg`/`offPos` stays the D102 floor-only spider. A horizontal piece is a **floor** (its points on the floor line; the
    body stays on it). A vertical piece flush with one face of a static solid (an untyped or `Wall` collision box) over its
    whole length is a **wall**, climbed on the open side. A vertical piece over open air, at either end of the polyline, hanging
    from the end of a floor, is a **thread** down to the floor below. No ceilings, no undersides, nothing that moves. Floors and
    walls alternate; corners meet at a wall's foot (the floor on the open side) or over its top (the floor along the solid). A
    floor-only route is the D102 spider. The climb and the drop are **develops** of the element `spider` (ROOM_CARDS.md), one
    new ability per room (D72), listed under Develops by the room that first shows it; the route decides which (#484).
  - **On a route** it skitters (48 px/s for 0.3 s, still for 0.3 s) from end to end, and chases to the route point nearest the
    hero (the leg it is on wins a tie). On a wall it climbs at 64 px/s and comes down at 85 px/s, head along its motion, its
    belly against the face; a corner is a one-frame quarter-turn snap. **It never strikes from a wall**: a hero below draws it
    down to the floor, where D102's pounce applies. At the lip of its floor it waits facing over the edge, harmless.
  - **The silk drop.** On the floor that ends at a thread, when the hero's hitbox touches the **drop box** (the column the
    fangs sweep: 8 px wide, from the lip to the fang tips at the thread's end, the trigger being the reach as D10 asks) and the
    spider is at the lip after its notice: it hangs head down over the edge (`hangwind`, legs drawn in, fangs spread, drawn
    1 px up) for `TrapWarnings.duration(BITE_WARNING_SECONDS)` (**0.5 s, 0.75 s with the assist**, sampled once) while silk
    glints run down the column to the thread's end and `spider_windup` (`_long`) plays. Then it **drops at 384 px/s**
    (`spider_drop`), the thread paying out and shedding specks, the fangs lethal and swept every frame, to the thread's end,
    which leaves the hanging frame's bottom **4 px above the floor**; it never lands there. It **bites** there, hit or miss
    (`hang` frame, two fang glints and floor dust, `spider_bite`, 0.1 s, D93), once per drop; a bite resolves on its next
    frame, so a same-frame kill wins. Then **0.6 s hanging, harmless**, then it **reels up at 64 px/s, head down, harmless**
    (`spider_reel`), and steps back onto its floor at the lip. A new drop needs a full wind-up from the lip; it never drops or
    pounces from the thread. The thread hangs out from the lip so that the hanging body clears the ledge's face by 1 px on either
    side (`Spider.threadOut`: 6 px from a lip on the right of its floor, 8 px from one on its left, since the thread leaves the
    frame just left of its mirror axis); a thread is at
    most 112 px from lip to floor.
  - **Answers.** Every weapon kills it wherever it reaches it: on a wall, at the lip, in the drop, hanging or reeling. The sword
    reaches it hanging low (with D65's light spray), a stomp from its ledge reaches it on the thread, and weapons pass through
    the thread, which is not a target.
  - **Body (D101).** Killed on a wall or a thread, its body is set on the open side where it was (off the face, out past the
    lip) and falls to the floor below, where it lies, rocks, crumbles and burns as on a floor; over a pit it is gone. **A thread
    snaps** with its spider: five specks drift down from it, its stub fades over 0.4 s, `spider_snap` plays, and nothing is left.
  - **Load checks** (`Spider.routeProblem`, run by `Level` for every room): every piece is level or upright; walls are at least
    32 px and inside the room; corners meet; every floor passes D102's checks (in the room, gapless, no standing place in a
    pounce box facing either way); the route stays inside the camera's view of its room (`FallingTrapLayout.floorCameraTop`);
    a thread is at most 112 px, its column misses every solid, one-way platform and object that moves or hangs (`Actionable`,
    `Escalator`, `FallingPlatform`, `FallingBranch`, `HangingVine`, `ThornVine`, `HangingHive`, `Deadfall`), it ends on
    floor that runs 16 px past the drop box on each side (somewhere to step), it lands on no climbable (`Ladder`,
    `HangingVine`) and no quicksand, and no standing place (the entrance body, the `RoomExit`, a `Checkpoint`) is in its drop box.
  - **Contrast placement rule.** `tools/check_contrast.py` measures a placed polyline spider in every pose along its route
    (`spider_parts`): floor frames every 16 px, wall frames every 16 px up each wall head up and head down, the hanging frames
    every 8 px down the thread and the silk tile every 4 px. A wall face too dark for the climbing spider fails the placement,
    not the art (two faces of the shipped maps today, below).
  - **Art.** `assets/images/objects/monster-spider-wall.png`: three drawn 16x32 frames (`wall1`, `wall2`, `walltap`) in
    `art/objects/monster/spider-wall.grid`, head up with the wall on the right, the legs spread up and down the face with the
    knees out and the feet on it, the body close to the wall, the ochre chevron down its back; mirrored for a wall on the left,
    flipped for a head going down; the notice on a wall rises 1 px off the face and drums a raised foreleg (`walltap`). Three
    cells added to the floor strip: `hang` and `hangwind` (upright, head down, mirrored about the thread; their far legs a lighter
    tan `b08c68`), and `silk`, the thread's 1x4 tile (the fang pale with one pale-yellow texel), tiled down the thread every
    frame without allocation. The drop is drawn in `hangwind`: the turned `leap` frame of Phase 1 hung its legs through the floor
    at the thread's end and put its head 3 px off the thread.
  - **Sounds** (seeds 6908-6910): `spider_drop` (0.25 s, a falling "zzzip": noise swept 4.2 to 0.9 kHz, chopped by a flutter
    slowing from 90 to 45 Hz, after a tick), `spider_reel` (0.30 s, ten dry ticks rising in band and level, quiet),
    `spider_snap` (0.12 s, a small 2.3 kHz pluck over a noise transient, with the kill sound). The rest are D102's.
- **Why:** the D102 spider asks "make it miss, then strike" on one floor. Following the hero up walls and dropping from above
  asks the same question from new directions, and the player brings the reading they already have: a crouch, glints along the
  strike line to the spot, a strike along that line hit or miss, then a harmless recovery. Every lethal act runs along a straight
  line the glints drew first, after a notice and a wind-up (D5, D7, D71, D100); travel never kills. The route is checked at
  load, so the spider is always on screen when it acts and every drop can be stepped out of. Hearing through walls costs no
  fairness, since hearing never kills and the whole route is in view; a line-of-sight rule would have failed exactly at the lip,
  where a ledge's own corner hides the hero below.
- **Measured:** the hero needs 10 px (0.10 s at 100 px/s) to leave the 8 px drop box, which leaves 0.40 s of the 0.5 s warning
  (0.65 s with the assist); a drop of the longest thread takes 0.24 s; the punish window after a miss is 0.6 s hanging plus
  1.4 s reeling up a 92 px thread. Contrast (`check_contrast.py` metrics over the night composites of the three maps): the wall
  frames on every face of a static solid at least 32 px tall, every 16 px, both ways up and both sides: outline 0.198 to 0.415,
  at least 0.28 on 63 of 65 faces; forest-2 x 16 (the map's left wall, as low as 0.198 at 44 % of heights) and forest-2 x 1296
  (0.257 at 4 %) fail a placement there. The hanging frames at every lip of the three maps with a floor 48 to 112 px below:
  0.311 to 0.391 (Phase 1's `hang` was 0.274 under one lip; the lighter far legs lifted it). The thread: 0.537 to 0.549. The pose
  rule: the body differs from the hanging frames in 64.6 % (`hangwind`) and 68.5 % (`hang`) of the silhouette (floor 25 %).
  `SpiderFx` pools unchanged (64 and 104): the drop's worst case (three spiders at the assist's warning down the longest thread,
  biting, snapping and dying together) peaks at 50 soft and 69 hard (`SpiderFxTest`).
- **Built (#483):** `entity/enemy/Spider.java` (`Spider.Route`, the states `SET`, `DROP`, `DANGLE`, `HANG` and `REEL`, the wall
  pose through `CRAWL`, `NOTICE` and `CHASE`, `routeProblem`), the polyline in `tiled/TiledObject` and `TiledMapData`, `Level`
  (spawn and load checks), `effects/SpiderFx.java` (`silkDown`, `shed`, `snap`), `SpriteSheets.MONSTER_SPIDER_WALL`, the
  sheets from `spider.grid` and `spider-wall.grid` (`tools/forest_trap_sprites.py monster:spider monster:spider-wall`),
  `tools/check_contrast.py` (`spider_parts`), `tools/check_allocations.py` (the new per-frame helpers) and three sounds in
  `tools/audio/generate_sounds.py`. `Level` builds the shared particle textures once at load (`effects/ParticleTextures.java`:
  `SoftDot` and `Shaders.whiteQuad`, an argued `check_sprite_art.py` entry: it draws nothing), never on the first frame that
  draws one. **Tests:** `SpiderTest` (20 new cases on `spider-climb.tmx`, `spider-drop.tmx`, the
  mirrored fixtures `spider-climb-left.tmx` (the solid on the wall's left, floors run right to left), `spider-left-drop.tmx` (a
  thread left of its lip) and `spider-start-drop.tmx` (a thread at the route's start), and the failing fixture
  `spider-drop-checkpoint.tmx`: tracing, patrol up and back, hearing and climbing, coming down head first, a
  kill on the wall, the drop's wind-up with and without the assist, the miss, hang and reel, a full new wind-up, the sword and
  the snapped thread, a kill in the drop, the same-frame tie, the reset, the route load checks, and that a floor-only route is
  D102's spider; the mirrored paths; no snap for a kill in the drop's wind-up, where no thread is drawn; and the Java route
  against the shared fixture `spider-routes.json`, which pins `check_contrast.spider_route` too); `SpiderFxTest` (the drop's pools and the glints' column); `RemainsTest` (the hanging frames join the pose rule;
  bodies killed on a wall and on a thread lie on the floor below); `AllocationBudgetTest.spiderDropThroughTheRealLevel` (0.0, 0.0
  and 0.0 B/frame in 3 isolated runs) and `spiderClimbThroughTheRealLevel` (0.1, 1.1 and 1.1 B/frame; with the measured frames doubled to
  2400, 0.5, 0.6 and 0.6: the same 1.3 KB spread over twice the frames, so it is a one-time cost of the run, not a per-frame
  one), ceiling 8, measured after
  the textures moved to load, so no scenario depends on which kill comes first; all three spider scenarios 0.0 in the full
  `:core:check`; `test_check_contrast.py` (the route sweep, the
  dark-face rule and the shared route fixture); `test_generate_sounds.py` (the three sounds' bounds).
- **Tests changed by the hearing, and why:** `SpiderTest.itLosesAHeroWhoGetsFar` became `itLosesAHeroWhoStaysFarForASecond`: the
  spider no longer drops the hero at once beyond 160 px, it keeps chasing for 1.0 s beyond 192 px. `SpiderTest` gained
  `itHearsAHeroAboveItInACircle` for the circle. `AllocationBudgetTest.spiderThroughTheRealLevel` now requires the floor states
  only (`FLOOR_STATES`), because the enum grew by the drop's states, which its floor-only scenario cannot reach; its measured
  0.1 B/frame is unchanged in kind. No threshold or tolerance changed. The other D102 tests pass unchanged.
- **Placed (#484, cards approved by the author 2026-11-19).** No shipped room could show the spider alone (D72): every room's own
  new element is in view from its entrance, or the room is at the two-introduction cap, and the shipped wall faces were poor (the
  `forest-walls` posts are drawn as thin poles 14 px from their collision face, the route room's exit block has the lift sliding over
  it, the fissure ledge is crowded). So forest-1 grew two one-screen rooms (510 tiles, 11 rooms), as the traps of #363-#366 did:
  `forest-1-spider` (room 5, a D102 box spider alone on a flat floor, **introduces** `spider`) and `forest-1-climb` (room 7, a
  polyline up the far face of a block the hero must wait on for a running lift, **develops** the climb and combines it with the
  lift), and `forest-1-route` (room 8) got a spider on its ladder slab whose
  thread hangs from the slab's lip over the mandatory floor (**develops** the drop). Order: floor, wall, thread, each the only new
  reading on its screen. Measured with a scripted hero (`Forest1SpiderTest`, `Forest1ClimbTest`, `Forest1ShelfSpiderTest`):
  clearing 6.90 s baiting and striking, 5.85 s rushing with the sword, 5.17 s with a fireball, death 2.32 s in (3.58 s before #497; walking on without
  a strike is bitten); climb 10.77 s with a fireball as it tops the block's far edge, 13.33 s baiting and
  striking it on the block top, death 5.33 to 6.38 s in waiting under the lift and 5.57 to 6.92 s walking to it once it holds the block
  top, each pinned at every pause on the second step (the first layout, a ledge climbed beside its face, let the jump onto the ledge stomp it in 1,700 of 1,736 runs, so the
  author chose this one after review); route unchanged at 11.02 s and
  18.32 s (a walker sets off the drop and is past when it falls, at every skitter phase, with and without the assist), death
  under the lip 3.43 s in. The optional ladder detour meets the spider on its slab: it holds while the hero climbs (#497; it used
  to wind up) and misses a hero who walks on, at every skitter phase with and without the assist; the ladder's top is out of its
  bite, and a hero who stops mid-slab is bitten 5.55 s in (5.22 s before #497). Contrast along the three routes passes (`check_contrast.py`). The placement
  previews were review images only and are not kept in the repo.

## D104. Quicksand leaves the Forest for the Swamp; the engine support stays (#486)

- **Status:** Accepted (author, 2026-11-19: the `forest-entry` card without sand, with a plain pit where each pool stood).
- **Decision:** the Forest has no quicksand pit, no quicksand tiles in use and no quicksand teaching. Quicksand is a Swamp
  mechanic by theme (`docs/GAME_DESIGN.md` stage table); its rules, tiles, sprites, sound and teaching room are designed with
  the Swamp (#105, #399, #486 Part 2).
- **Forest change:** in `forest-entry` the two pools (x 240-320 and x 560-608) became plain pits, as in `forest-0` (the floor block stops and restarts; the level's `Deadline` kills a fall). The room introduces only
  `yellowmob`; the Bomb now develops against the second pit (a 24 px pocket, x 608-632, at its edge, between the pit and the
  blast) instead of against sand.
- **Kept:** `CollisionBlock.quickSand`, the `CollideBody` sink branch and D98's sink speed, and the load checks that skip
  `QuickSand`, because the Swamp reuses them. The physics stays covered on the test map `practice-test.tmx`
  (`JumpFrameRateTest`, `RoomTest`).

## D105. Rooms are dense: one beat about every 3 s, and a first meeting gets a safe pocket, not a room of its own (#499)

- **Status:** Accepted (author, 2026-11-19: the Phase 1 draft and its ten recommendations,
  [#499](https://github.com/jlogicgames/edgard_in_kimeria/issues/499)). Gate built (`RoomCardsTest`). The rooms shipped below it
  were all reworked under [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500), done: the three spider rooms with the snail ([#504](https://github.com/jlogicgames/edgard_in_kimeria/pull/504)),
  five sparse forest-1 rooms ([#507](https://github.com/jlogicgames/edgard_in_kimeria/pull/507), D107), `forest-1-traps` and `forest-1-guard` ([#509](https://github.com/jlogicgames/edgard_in_kimeria/pull/509), D110) and the three forest-2 rooms (batch 4, D109). Revises D72's "met alone" reading and the placements built on it (D85, D88, D102 and D103 "Placed", #484).
- **Problem:** the author found the rooms built so one mechanic sits alone "dull and empty" and asked for one mechanic per
  3 s of play, so that a room holds 3 to 4 at once. D72 never asked for empty rooms: it says a new element is first met
  where it is the only *unknown* thing on screen, and known things were always allowed beside it. The emptiness came from
  applying it as "the only object" or "a room of its own" (D85's first RedMob, D88's deadfall, #484's spider), and from
  nothing on the card measuring how much a room asks of the player. The spider's own room was a teaching room in all but
  name, which D100 rules out for monsters.
- **Decision, density:** a **beat** is one placed instance of a registry element that the **required route** makes the player
  read and answer. The registry's **Beat kind** says what counts: a `hazard` (a trap) and a `monster` once per instance; a
  `mechanism` once per lever the route pulls or hits, with everything it drives (pull, ladder and climb are one beat; a
  ladder with no lever is not a beat of its own); a `platform` once per running platform the route boards or must pass while
  it moves; a `skill` once per obstacle that needs it (one wall, one tree). A `pickup` and a `harmless` element never count;
  plain geometry (a pit, a step) is not a beat; identical instances crossed in one movement (a row of falling platforms) are
  one beat; mechanically, **two instances of one element whose spans lie within 96 px of each other are one beat** (the
  span is the object's box, a polyline's points, widened by a horizontal patrol; 96 px is about 1 s of walking at the
  hero's 100 px/s, the same yardstick as the pocket's clearance). A `skill` beat is posed by map geometry, never free: a
  wall jump by a free-standing `Collisions:Wall` at least 96 px tall, a branch climb by an object with `skin=branch`.
  Beats are listed in route order, which the gate reads as rising map x (every shipped required route runs left to
  right), so "the first two beats" are the route's first two. The **beat time** `T` is the card's mastered time for the
  required route with zero charges spent (D26, D27), the first time its `Mastered route` field states;
  with `N` beats the room's pace is `T / N`. Outside the exempt roles a room has **at least 3 beats** of **at least 2
  distinct elements** and **at most 4.0 s per beat**; the aim is about **3 s**. Under **1.5 s per beat** it needs an
  `Overload reason` that cites an issue. At the 20 s target of D19 a room holds up to six or seven beats; the author's 3 to
  4 is a room of about 9 to 12 s. 4.0 s is the 3 s aim with slack for walking and a lift's wait; 1.5 s is just under the
  densest room the author has played and accepted (`forest-canopy`, 1.58 s).
- **Decision, newness (what stays of D72):** every room introduces, develops or combines; a reskin or more geometry is not
  novelty; outside lessons at most **two** introductions; **one unknown at a time**; repetition for mastery is welcome and is
  now what fills a room to its density. **Dropped:** "met alone" read as the only object or a room of its own, and a new
  trap or monster getting its own one-screen room as the default when no room has space: the default is now to put it into
  a room of known beats. An element may be introduced and then developed or combined in the same room, its first
  instance met under the pocket below and the later one after it on the route.
- **Decision, first meetings:** a new element's first instance gets a **pocket**: (1) a safe spot on the required route,
  outside every danger zone, from which it is in view and acting, where the player may stand as long as they like (D26,
  D84); (2) no other lethal beat's danger zone within **96 px** of its own along the route, and nothing else lethal active
  in view from the pocket (a known lever, lift or ladder may share the stretch); (3) it is among the room's **first two
  beats**, or a death at it replays at most **6 s**; (4) it is not combined on first sight. A **monster** stays met in play
  (D100: no teaching room, no hint); its danger zone for the pocket is its whole reach, its range plus its strike plus its
  hearing (D103's 144 px circle for the spider). A DISGUISED or HIDDEN trap keeps its teaching room (D71), which is dense
  too.
- **Decision, times and retry:** D19 is unchanged (about 20 s mastered, about 60 s first learning, both hypotheses). Density
  fills a room, it does not lengthen it. The most expensive retry stays within the 20 s; a room whose retry passes about 15 s
  because of density is split, each half passing the band, never padded. A room under about 6 s rarely holds 3 beats without
  falling under 1.5 s per beat: merge it or give it more route. First learning will rise with density; it is not measured
  yet (#135).
- **Decision, exempt roles, each earned, not declared:** `lesson` (D33-D40, D48: one lesson element plus at most one trap) is
  outside the band in the tutorial level and in a fixed, shrink-only list of lessons elsewhere (`LESSON_ROOMS`), each
  argued here and holding no lethal element. Today it holds only `forest-1-lever`, a different category from a play
  room: it is a combination lesson (one lever driving a ladder and a lift in opposite states, taught before the route room
  gates progress with it, D84, D86) with nothing lethal in it, so its retry cost is the pull that undoes a wrong pull,
  about 0.1 s. Density measures how much a room asks of a player who can die; a room built so nobody can is a teaching
  aid, not a sparse play room. A new lesson outside the tutorial needs its own decision, not an entry in the list. `boss` (D19: measured apart) is outside the band only in a room
  that holds the boss. `rest` and `reward`, the calm beat after danger (#352), are exempt from the minimum only in a room
  with no lethal element, at most one per level; `forest-1-tree` is the first (#500, D107).
- **Gate:** `RoomCardsTest` reads the registry's `Beat kind` (a row without one fails with its name) and, per card:
  `Beats` (`id@x` in rising x, each backed by its own map object of that element, told apart by map id, whose span holds
  `x` within 16 px; a skill by the geometry above; two listed beats of one element within 96 px fail as one beat);
  `Beat time` (`seconds (TestClass)`: the first time `Mastered route` states, matched as a whole token, and pinned in that
  test as a float literal that an `assertEquals` uses as its expected value, directly or through the constant it
  initialises, within 0.1 s); `First meeting` (per introduced element that counts as a beat: `id: beat k` as it really is
  in the Beats, `, retry R s` when it is not among the first two; `pocket at x N`, the hero's centre at the safe spot on
  the required route just before that beat; no other lethal object within 96 px of its span on the map; and a named test
  method, `FirstMeetingPocketTest.method`, whose body names the room, the element id and N: it stands the hero there for
  12 s in the real camera view (the target the player hands the camera, at the logical resolution), asserts every frame
  that he is never hit and the element is in view, and that the element is seen acting during the wait: the spider
  notices him, the bramble grows, the bat flies, the hive releases, the fissure jets); `Below density`. An issue citation is `#` and digits. The exempt roles are checked against the room (the
  level, the fixed lesson list, a boss object, nothing lethal). Synthetic cases pin each rule. Every route test the cards
  name now pins its time within 0.1 s (before, `RoomReplayTest`, the deadfall, bramble, route, hive and tree tests only
  checked "under 20 s"): no threshold was loosened.
- **Rooms shipped below it (history, done: the lists were emptied by #500 and then deleted from the test, so no shipped room is exempt and none can be added back):** a fixed, shrink-only list in the test (`GRANDFATHERED`), each card with `Below density` citing
  [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500); the test fails when a listed room passes and keeps the field, and a room not on the list cannot use it. Argued, not
  taken for convenience: the field carries a tracked rework that removes it, the same mechanism as D72's `Overload reason`
  while #375 and #376 were open. Beats verified against each room's route test (the scripted routes of `RoomReplayTest`
  were traced object by object; the others from their tests), under the mechanical rules above:

| Room | Beats on the required route | T (s) | s per beat | Result |
| --- | --- | ---: | ---: | --- |
| `forest-1-mob` | redmob | 7.43 | 7.43 | below, #500 |
| `forest-1-deadfall` | deadfall | 5.17 | 5.17 | below, #500 |
| `forest-1-traps` | crumble-support, running-platform (the two escalators, 48 px apart: one beat), lever-wall, lever-platform, proximity-thorn, redmob, puffball, crumble-support | 12.27 | 1.53 | passes |
| `forest-1-spider` | spider, snail, falling-stone, proximity-thorn (since #500; before, spider alone in 6.90 s) | 11.53 | 2.88 | passes (#500) |
| `forest-1-bramble` | bramble | 10.73 | 10.73 | below, #500 |
| `forest-1-climb` | falling-stone, snail, running-platform, spider (since #500; before, the lift and the spider) | 13.33 | 3.33 | passes (#500) |
| `forest-1-route` | snail, spider, bat-enemy, running-platform (the snail since #500) | 11.02 | 2.76 | passes (#500) |
| `forest-1-guard` | puffball, redmob, beetle | 6.83 (6.50 since #500 batch 3, D110) | 2.28 (2.17) | passes |
| `forest-1-hive` | hanging-hive x2 (140 px apart) | 7.12 | 3.56 | below (one element), #500 |
| `forest-1-tree` | branch-climb (the beetle never commits on the fast line) | 4.95 | 4.95 | below, #500 |
| `forest-entry` | yellowmob, bat-enemy x2, touch-explosive | 6.80 | 1.70 | passes |
| `forest-walls` | yellowmob, wall-jump (the two walls, 64 px apart: one beat), redmob | 8.80 | 2.93 | passes |
| `forest-fissure` | ember-fissure x2 (112 px apart: two beats, each waited out), beetle | 9.30 | 3.10 | passes |
| `forest-canopy` | redmob, false-honey-fungus, bat-enemy, thorn-vine, touch-explosive, falling-stone | 9.48 | 1.58 | passes |

  Seven of the fourteen passed and seven were below; at that point, after the spider rooms' rework (#500, below), nine passed and five were below (history: all rooms pass since #500 finished). `forest-1-traps` passes by a hair: 12.27 s over 8 beats is 1.534 s per
  beat, 0.03 s above the 1.5 s floor, so a faster mastered route would push it over; it was a candidate for the #500
  rework, done by batch 3 (D110: split, 2.00 s per beat). Against the first count: `forest-1-tree` has one beat, not two
  (`TreeClimbTest`: on the fast line the beetle never commits); with the 96 px rule `forest-1-traps` has eight, not nine,
  and is no longer too dense, so it carries no `Overload reason`; `forest-walls` has three, not four, and still passes.
  `forest-fissure` keeps two fissure beats: they are 112 px apart and the route waits out each jet.
- **First meetings found late, crowded or idle by the gate:** eight rooms built before D105 break the pocket:
  `forest-1-mob` (the RedMob stands idle until the hero is in its reach: measured, idle for a hero centre up to x 3728, a
  hit from x 3732, so no safe spot shows it acting), `forest-1-deadfall` (a touch trap acts only when its vine is tripped,
  D88), `forest-1-traps` (the escalators' patrol 32 px from the falling platforms; the puffball beat 7 of 8, beside the
  RedMob's patrol), `forest-1-guard` (the beetle beat 3 with a 6.08 s retry), `forest-1-tree` (the climb is the rotten
  column itself and the beetle's patrol is 48 px from it), `forest-entry` (the YellowMob's patrol 16 px from the first
  Bat's column), `forest-walls` (the first wall stands inside the YellowMob's patrol: a combination on first sight) and
  `forest-canopy` (the false honey fungus 64 px from the Bat's column; the thorn vine beat 4, its retry not measured).
  They were on a second shrink-only list (`FIRST_MEETING_GRANDFATHERED`), each entry citing [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500), which re-measured,
  re-ordered or recorded the author's decision for each (all done by batches 2 to 4; the list was deleted). The five first meetings that pass (the spider, the bramble, the route
  room's bat, the floor hive, the first fissure) are pinned by `FirstMeetingPocketTest`.
- **Reworked under #500 (the spider rooms with the snail, #329; cards approved by the author 2026-11-19, with the five
  recommendations of the Phase 1 proposal).** `forest-1-spider` grew from 640 to 1120 px (x 6880-8000, forest-1 from 510 to 540
  tiles), so `forest-1-climb` moved 480 px right (x 8000-8640); the rooms are found by name, and every later room and test held.
  `forest-1-spider` introduces the spider and the snail, one at a time: the spider (unchanged, pocket x 7030) is met with the
  snail off screen (the camera ends at x 7520 while it is fought, `Forest1SpiderTest`); the snail (x 7600, range 7584-7616) is
  met from a one-way step (x 7440-7472, top y 288), inside its sight and above its firing line, where it notices the hero and
  watches without spitting (`FirstMeetingPocketTest.forest1SpiderTheSnail`, which now stands the hero on a raised support);
  then a falling stone (x 7744) and a thorn tuft (x 7872). Measured: 11.53 s baiting the spider and jumping the gob (the hero walks
  on past the snail in its reload, with no swing: it is pinned alive behind him at the exit, and its next gob falls short), 9.80 s
  with a fireball, 10.48 s rushing the spider; deaths at the spider
  2.32 s, the gob 8.18 s, the tuft 11.08 s, the stone 11.23 s (the retry cost). `forest-1-climb` adds a stone over the approach
  (x 8112) and a snail on the block top (x 8336, range 8336-8352) left of the lift: the mastered route finishes it (the gob
  jumped, then the sword) before the spider hears the hero, so the climb is still first seen alone; 13.33 s, unchanged, the snail
  fitting into the wait for the lift; deaths at the stone 1.98 s, the gob 4.73 s, waiting for the lift 5.33 to 6.65 s (the retry
  cost, 6.92 s before). `forest-1-route` adds a snail under the slab (x 1576, no patrol: it holds still facing the entrance)
  and a 16 x 16 solid stump (x 1600-1616) that ends its line to the right, so it never fires at the drop column, the bat's pocket
  or a hero who passed it; 11.02 s required, unchanged; 18.45 s with the refill (18.32 s); deaths at the gob 1.85 s, the drop
  3.40 s (3.43 s), the bat 5.43 s (its first meeting is now beat 3 and a cheap retry, at most 6 s), mid-slab 5.77 s (5.55 s,
  +0.22 s: the detour now finishes the snail between the lever and the ladder, walking on to it while the ladder comes down and
  back to the ladder's foot, so the climb to the slab starts later; the spider's bite itself is unchanged).
  **Tests changed, and why (no tolerance loosened):** `FallingTrapsTest.onlyForestZeroAndTheForestTwoApproachUseTheFallingTraps`
  became `theFallingTrapsAreTheTutorialsTheForestTwoApproachsAndTheTwoSpiderRoomStones` (a census: forest-1 now has two stones,
  still no branch); `Forest1RouteTest`'s teleporting geometry sweeps, which took the bat out, take the snail out too, and
  `FairyOffscreenTest` takes the snail out of the route room (both are about geometry or the fairy's cue; the snail fires
  along the lever's floor); the scripted routes of `Forest1RouteTest`, `Forest1ShelfSpiderTest` and `Forest1ClimbTest` now
  answer the snail as the mastered route does (`ScriptedAnswers`: jump a gob 24 px away, swing at a snail in reach) and hop
  the stump; `Forest1ClimbTest`'s fireball route is pinned per pause (10.77 s, and 13.32 s for a pause of 1.87 s or more on the
  second step, which meets the snail later and misses a lift), and its late-shot hero stops when the spider commits
  instead of walking into the locked pounce (walking in, a point-blank shot at 15 px missed it at one pause); one test leaves a
  key held from the previous case no longer (`droppingOffEitherPadEdge...`: a held Right walked the waiting hero into the
  snail's line). `RoomCardsTest`'s `GRANDFATHERED` lost `forest-1-spider` and `forest-1-climb`.
- **Why:** a room that asks one thing in 7 s reads as empty; a full room stays fair when each new thing is met in a pocket
  of its own and deaths at it are cheap.
- **Not verified:** how dense rooms feel to a first-time player and what they do to first learning (#135); the 96 px and
  the 6 s are authoring numbers, not measured on players.
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the wall jump is no longer a skill example of this rule; it was removed ([D121](#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21)), and the branch climb is the skill the rule still names.

## D106. The forest snail: it looks ahead, holds a line and spits a slow gob; cross its ground in the reload (#329, #240, #398)

- **Status:** Accepted (author, 2026-11-19: the card, all thirteen recommendations of the Phase 1 proposal, and the art with the
  shell toned down to the spider's loudness). D105 is the room density rule of #499.
- **Decision:**
  - **Body.** One strip of eight 24×16 frames (`crawl1`, `crawl2`, `look`, `blink`, `draw`, `spit`, `rest`, `dead`) and a ninth
    cell holding the gob's two 8×8 wobble frames, 13 colours, 14 with the moon rim; a chestnut shell coiled in two turns with a
    dark groove and a cream whorl, a pale clay foot and neck, two eye stalks with pale tips; drawn about 14 px tall; no reserved
    hue. *Recoloured by #236 (2026-11-20): the danger family, a dark crimson `86101f` shell, `ff6b57` eye tips, a crimson gob;
    its body (D101) a dark dried umber, L 0.143 to 0.253.* Body hitbox `rect(1, 3, 20, 13)`. **Contact is harmless**: only the gob kills.
  - **Patrol:** 12 px/s inside its authored range (`offNeg`/`offPos`), a 0.8 s pause at each end (the `blink` frame), then it
    turns; it never leaves its range.
  - **Sight, ahead only:** a box from its front edge to 176 px ahead, from its floor line to 48 px above it, cut at the first
    solid at the gob's height (`Level.blocksObject`). It does not hear. Aware, it follows a hero who goes behind it with a turn
    after 0.4 s (behind it, the same box mirrored) and loses him after 1.5 s out of both. These clocks start with the notice and
    run on through the spit and the reload. It never chases: aware, it holds still and faces the hero.
  - **Notice, in body language (D100's pattern):** its stalks shoot up, pull in, shoot up, pull in (`look` 0.15 s, `blink`
    0.1 s, twice): 0.5 s, not scaled by the assist; its own call `snail_notice`. No mark, no shared sound.
  - **Firing line:** a 4 px band centred 7 px above its floor line, from its mouth to 176 px ahead, cut at the first solid. It
    winds up only when the hero's hitbox touches the line and its reload is done; a hero it sees above the line is watched
    (stalks up) and not shot at.
  - **Wind-up:** `TrapWarnings.duration(DEFAULT_WARNING_SECONDS)`, sampled once: 0.8 s, 1.2 s with the assist; its facing locked,
    nothing the hero does stops it. Seen: the `draw` frame (reared, neck tall, throat swollen), a glow swelling at the mouth
    from 1 to 3 px, and pale specks born along the firing line and drawn into the mouth (40 a second), so the reach and the
    height read before the shot. Heard: `snail_windup` (`_long`).
  - **Spit and gob:** the `spit` frame for 0.15 s and `snail_spit`; the gob leaves the mouth at 120 px/s, straight and level, an
    authored 8×8 sprite of two wobble frames that sheds a drip every 12 px. Its 4×4 core kills through
    `player.collidedWithActor(false)`, swept every frame; the hit lands at the start of the snail's next frame, so a weapon that
    breaks the gob on the same frame wins. It ends at the first of: the hero, a solid face (it splats on the face), or 176 px out,
    where it falls at 588 px/s² and splats on the floor about 20 px further (or on a wall face it meets as it falls; one that
    left the room would splat at its edge, which the load check makes unreachable). Every end is seen (droplets thrown back and a smear
    that fades in 0.4 s) and heard (`snail_splat`), hit or miss (D93); no puddle, nothing lasting. It passes through one-way
    platforms, monsters (the spider too), bodies, decor, pickups and traps. One gob per snail; it is owned by the snail, with no
    allocation.
  - **Reload:** 2.4 s from the spit, harmless, not scaled by the assist (`rest` and `blink` alternate every 0.4 s, a slow
    swallow); then a full new wind-up for each shot. One shot every 3.2 s, the cadence of D105.
  - **Answers:** the sword (with the light spray of D65), a fireball, a thrown stone or torch and a stomp (the normal bounce)
    kill it in any state and leave its body (D101). Killed during the wind-up it fires nothing: the swelling gob pops on its
    lips, seen and heard. A gob already in the air flies on when its snail dies, and the snail's object stays until the gob ends
    even when its body is crumbled. Every weapon breaks the gob and flies on (`Breakable`): the sword's live box and a thrown stone
    break it apart, a fireball and a thrown torch burn it away with a puff of steam; the fireball is not spent. A weapon breaks
    what it visibly touches: the gob's drawn 8×6, not only its lethal core, since a fireball flies at the hero's chest and meets
    only the gob's top edge. The weapon loops test gobs before enemies (the sword in a pass of its own; a fireball or a throw
    in the same step before the enemy that spends or stops it), so a swing or a shot that also hits an enemy still breaks the
    gob it reached. The shell blocks nothing.
  - **Body (D101):** the empty shell rolled onto its back, its mouth open upward; the `dead` frame holds 0.3 s, then cross-fades
    over 0.6 s into `monster-snail-body.png`; it rocks (`RemainsSwing`), crumbles into shell and foot chips, burns, as the others.
  - **Room rules:** D3/D6 as for every enemy: a death or a revisit brings it back calm at its spawn with no gob in the air.
    Load-time checks (`Snail.problem`, on the 24×16 snail whatever the Tiled object's size): its range inside the room on floor
    without gaps; every firing line, from every 4 px of its range facing either way, ends on a solid face or on floor inside the
    room, stays below the camera's top, and crosses no standing place (the entrance body, the `RoomExit`, a `Checkpoint`),
    including the drop past its range. Met in play: no teaching room, no hint text (D100).
  - **Effects** on `KimeriaParticleSystem` only (`effects.SnailFx`, one per `Level`: pools of 64 and 112 from measured peaks of
    51 and 84; the body's crumble and burn in `RemainsFx`); the snail's strip with the gob's frames, and its decor body, are the
    only sprites.
- **Why:** the beetle and the spider are fights at close range; the snail is the first threat at a distance, and its decision is
  the approach: read its gaze (ahead only, cut by cover) and its rhythm (a 0.8 s warning, then 2.4 s of reload) and cross its
  ground then, or come from behind. Its gob is level at shin height, so a jump, a cut or cover always answers it, and a hero who
  reaches it is safe. Its warning shares the spider's grammar (a line drawn in pale particles before the act), so the two
  combine in one room without a new reading, and the gob never touches the spider, so each threat stays the one the player read.
- **Measured:** live frames outline 0.302 (`look`) to 0.339 (`draw`) at the 23 monster spawns of the three maps, both facings
  (active floor 0.28; the spider measures 0.300 to 0.331). The first concept measured 0.41 to 0.44 and read big and icon-like
  beside the spider, so the shell was toned down: softer browns, and its ink contour, with the moon rim, kept only on the top two
  rows of the shell, below which the shell's own light lip is its edge (without any rim the snail measured 0.222). The gob 0.298
  to 0.504 every 8 px along 176 px of line either side of each spawn. The decor body 0.091 to 0.157 on every floor top
  (`BODY_SWEEP`; ceiling 0.20). The pose differs from its closest live frame (`draw`) in 49.3 % (floor 25 %). Allocation
  (`AllocationBudgetTest.snailThroughTheRealLevel`): 0.2, 0.2 and 0.1 B/frame in 3 runs (ceiling 8).
- **Gates:** `SnailTest` (25: with a swing and a fireball that meet an enemy and a gob together, a gob through a spider, a
  falling gob on a wall, a gob out of the room), `SnailFxTest`, `RemainsTest` (the snail's case), `AllocationBudgetTest`, `check_contrast.py` (`Snail`,
  `Snail:gob` along every line, `Snail:body`).

## D107. The first-meeting clearance counts what can kill: a crumble support over a floor is not a lethal neighbour (#500, #499)

- **Status:** Accepted (author, 2026-11-19: the batch-2 design's decision 2 and, with it, the reward tree room).
  Gate built (`RoomCardsTest`). Revises D105's clearance and calm-room checks only; D69, D80 and the density band are unchanged.
- **Problem:** `RoomCardsTest` judged "lethal" by the registry's Beat kind, and `crumble-support` is a `hazard`. In
  `forest-1-tree` the rotten branches are the climb itself and never kill (D69, D80: a stop rides the hero down alive onto the
  floor), yet they stood 0 px from the first-met `branch-climb` and failed its 96 px clearance whatever else moved. The same
  rule failed `forest-1-traps`' escalator pocket against the falling platforms at x 96-224, which drop the hero 48 px onto the
  floor, and would have failed a `reward` room's "nothing lethal" for the same rotten branches.
- **Decision:** (1) a `FallingPlatform` (element `crumble-support`, any skin) counts as lethal only when what lies under it is
  lethal: no solid collision beneath its span (a pit). Over a floor it carries the hero down alive and so is no neighbour that
  can kill; the rotten log over the pit in `forest-1-traps` (id 37) still counts. (2) An object that is part of the first-met
  skill's own geometry (a `skin=branch` object for `branch-climb`: the rotten branches are branches like the sound ones) is not
  its own neighbour. The relief of (1) also decides whether a `rest` or `reward` room "holds nothing lethal". What the
  excluded content carries: no lethal danger in that placement; a support over a pit, a monster, a trap or any other hazard
  stays counted, and the beats and the band are untouched.
- **Gate:** `RoomCardsTest`: `Placed.carriesNoDeath`, set from the map by `floorUnder`; synthetic cases for support over a floor
  (not counted), over a pit (counted), the skill's own geometry (not its own neighbour, another object still is) and a reward
  room (a support over a floor passes, over a pit or mixed fails); `onTheShippedMapsOnlyTheSupportsOverAFloorCarryNoDeath` pins
  the shipped supports.
- **Also in this batch (the density rework of [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500), cards approved 2026-11-19):** `forest-1-mob`,
  `forest-1-deadfall`, `forest-1-bramble` and `forest-1-hive` gained beats (ROOMS.md, "Reworked under D105, batch 2");
  `forest-1-tree` became forest-1's one `reward` room (the beetle left, nothing lethal added, the level's end stays on the
  crown); `GRANDFATHERED` is empty (ceiling 0) and `FIRST_MEETING_GRANDFATHERED` lost `forest-1-mob`, `forest-1-deadfall` and
  `forest-1-tree`. **A touch trap whose trigger lies outside its lethal band** (only the deadfall, D88; superseded by [D109](#d109-a-skills-first-meeting-has-a-pocket-in-view-and-calm-and-the-false-honey-fungus-has-a-trip-and-stand-pocket-500-499), which adds the false honey fungus and a measured condition) has a trip-and-stand
  pocket on its trigger (`DeadfallTrap.phase()`, acting `SWING`); a trap whose trigger is the lethal spot has none.
  `forest-1-traps`' escalator pocket now passes (`forest1TrapsTheEscalator`); the room stays on the first-meeting list for its
  puffball.
- **Not verified:** how the reworked rooms feel to a first-time player (#135); the thresholds are authoring numbers.

## D108. The Forest parallax layers are read and drawn by the game: scenery apart from the play layer, a foreground per room ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236))

- **Status:** Accepted (author, 2026-11-24). Built in forest-1 (2026-11-19) from the card "Forest look and parallax layers" in
  [ART_DIRECTION.md](ART_DIRECTION.md); the card and this number waited for the author's approval ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)), given on 2026-11-24.
- **Problem:** `TiledMapData` merged every tile layer into one play grid and had no notion of parallax, offsets, opacity or a second tileset,
  so a layered night forest could not exist in the game.
- **Decision:** (1) a tile layer is *scenery* when it has a `parallaxx` or `parallaxy` attribute or the layer property `scenery=true`; scenery
  is read apart (`SceneryLayer`: factors, offsets, opacity, `driftx`, `wrapx`, regions resolved at load) and never feeds collision or the
  play layer; (2) the foreground is an object group per room (`foreground-<room>`, `roomx`, tile objects relative to the room, factor 1.6),
  drawn only in that room; (3) the maths is the card's, in whole pixels as Java's `Math.round` (half up): `x - round(f * (camX - originX))` and
  `(y - originY) - round(f * (camTop - originY))`, `originX` 0 for a map-wide layer and the room's x for the foreground, `originY` the room's
  floor camera top; clouds drift by accumulated time, not by the camera; the moon is a factor-0 layer; (4) draw order: scenery, play layer,
  objects and hero, fairy, foreground, HUD; (5) a layer is only as wide as its factor needs; (6) forest-1 uses the terrain sketch as its own
  play tileset (`ForestTerrain.tsx`); forest-0, forest-2 and other worlds keep theirs (forest-0 and forest-2: superseded by D112); (7) the parser is strict: a foreground group needs
  `roomx` and must be named `foreground-<room name>` for a `Room` object at that x, a flipped or rotated scenery cell is refused, the
  factors are doubles (the mock's 0.15, not a float's), and a foreground `y` is in map coordinates (the floor camera top is too; every forest
  room lies at y 0); (8) cost: the installer leaves the cell of a fully transparent tile empty (no draw), `forest-fg` is a Tiled collection
  whose tiles are sub-rectangles of one packed sheet (896 x 1696), the cull rectangle is the view plus one tile (the shake moves the camera
  up to 1.5 px after `visibleWorldRect`), the batch holds 8191 sprites (the libGDX maximum); a frame of `forest-1-mob` with the 640 px view
  went from 4 198 region draws and 15 texture switches to 3 187 and 9 (800 px view: 4 866 and 21 to 3 698 and 9), measured through `NoopBatch`
  with `ParallaxRenderer` alone (play layer, objects and the HUD not counted); (9) the clouds drift with game time (`Level.update`) by a fractional, unsnapped offset (the world is drawn at screen resolution, so the drift glides; the camera parallax stays whole pixels), the two copies `wrapx` apart so the seam stays closed: they
  stop in the pause, a room fade and after black in a death fade, and slow with the death slow motion; "map-wide layers never jump at a
  room change" holds while the rooms share a floor camera top (`everyForestRoomSharesOneFloorCameraTop`).
- **Gate:** `TiledMapParallaxTest` (the split, factors, foreground groups), `ParallaxRendererTest` (the maths, half-up rounding, per-room origin,
  cloud drift, the moon, draw order in a real frame, the cull slack, the batch colour, the shared floor camera top), `AllocationBudgetTest.forest1ParallaxLayersAndForeground` (0.0 B/frame, ceiling 8),
  `python3 tools/parallax_mock.py --check --game` (`validateParallax`: rule B and cover time of every room with a foreground,
  against the game map), `tools/test_parallax_game.py` (the installed map equals what the installer writes).
- **What changed on the way:** `forest-1-mob` (reworked by #507) put a red mob, a falling branch and an explosive stump over the sketch's tall
  pieces, so its foreground lost `rise-trunk` and moved the reeds and the fern to the room's ends (card, "Calm room"). The buried right-edge
  ground tile (`ground-buried-r`, id 72) had a transparent bottom-right pixel that cut the deep soil's top: filled. `check_contrast.py` reads
  the play layer by name (and fails without it) and the sheet and column count of each map's first tileset, and now composites what the
  game draws behind each sprite: the flat colour, the scenery layers at every steady camera x where the placement is on screen (4 px apart,
  clouds at 8 drift phases where they can show) and the play layer; every sprite now passes the tiers over that real background (D108 update).
- **Not done (all [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)):** a shared skill
  (`.agents/skills`) and the STYLE_SYSTEM rows beyond the one added here; the draw-call budget measured on a phone; approval by the author.

- **Update 2026-11-20, author decision: the decor tier is relative to the composited background (D76 amended).** The absolute ink floor of D76
  (no decor pixel under OKLab L 0.30, `INK_L_MAX`) is dropped from `check_contrast.py`. It carried one thing: keeping decor off black ink
  contours over the flat navy `2c3f66` (L 0.37), where a near-black outline is loud. It no longer applies because the new Forest ground is
  itself near black (L about 0.035): over it any colour at or above L 0.30 is at least 0.27 away, past the 0.20 ceiling, so the floor made decor
  impossible there. Kept unchanged: outline at most 0.20, body 0.08 to 0.20 against the real composited background at every camera (the 0.08 floor keeps
  decor visible), p90 chroma at most 0.10; the active and background tiers are untouched. `test_check_contrast.py`: a dark decor on a dark ground
  passes, a too bright one fails, one under the 0.08 body floor fails. The remains (beetle, spider, snail) and the bush were re-darkened (palette x0.45 and x0.22 of the old plum and grey-green,
  chips of `BeetleFx`, `SpiderFx`, `SnailFx` follow); results and what still fails are in ART_DIRECTION, open items.
- **Update 2026-11-20 (D108): a monster body is measured only where it can lie.** `BODY_SWEEP` used to stand every body on every floor
  top of every map: cream terrain tops, the lilac sky behind ledges, forest-2's light tiles, rooms where that monster never appears. A body
  (D101) exists only where its monster is killed, and `fall` drops it straight down (`velocity.x = 0`) to the first floor under it or removes
  it below the room. So `check_contrast.py` (`body_spots`) now derives the spots from the placements: a beetle or a snail along its authored
  range on the range's floor (the load checks keep that range on floor without gaps; the notice hop and the flip land there); a spider along
  each floor leg widened by `Spider.POUNCE_REACH` while authored floor stays under its whole body (#497), and from every wall pose and every
  thread pose, set on the open side as `Spider.die` does and dropped to the first collision box below. The positions removed carry nothing
  for the gate: no body can lie there. A new placement is swept with no edit (`test_check_contrast.py`, a newly placed monster's route).
  The remains were then repainted by tone role (not a uniform multiply): the beetle a dusky indigo (L 0.176 to 0.242), the spider and
  the snail a dark dried umber (L 0.143 to 0.253, hue 50; a cool hue cannot clear the blue night forest behind the forest-1 ledges by 0.08).
  All three pass at every spot (outline at most 0.181, body 0.097 to 0.177). The bush was repainted to a slate with a lighter foot row
  and passes at every placement. It first failed at forest-2 x 1040, in front of the third-party cliff tiles (#189): the background there
  averaged L 0.453, while forest-1's bushes stand on L 0.037, and no palette meets both. The placement was fixed, not the palette: bush id 132
  moved x 1040 to 1060, off those tiles (still on the ground floor, clear of traps, pickups and exits); measured outline 0.156, body 0.147
  there. The threshold is unchanged; `validateContrast` passes. The gate was also hardened: `body_spots` fails when a falling platform,
  escalator, deadfall or quicksand lies in a monster's range or drop column (it reads static collisions only); a placed monster kind
  with no measured body fails; a map with more than one non-scenery tile layer fails (the game overwrites layer by layer, last wins); the 128 px body camera step
  is enough because a 4 px sweep gave identical results for every kind and map (measured).

- **Update ([#571](https://github.com/jlogicgames/edgard_in_kimeria/issues/571), 2026-11-21, [D117](#d117-a-monster-body-keeps-its-live-palette-ink-and-moon-rim-the-pose-is-the-cue-571-2026-11-21)):** the monster bodies are no longer held in the decor band
  (outline 0.08 to 0.20): they keep their live palette and are measured as active at every spot of `BODY_SWEEP`.

- **Update 2026-11-21 ([D120](#d120-depth-palette-the-near-layers-take-dark-real-colours-the-scenery-turns-bluer-lighter-and-greyer-with-depth-236-2026-11-21)): the depth palette.** The foreground's one neutral near-black palette (`030303` with a blue moon rim) gives way to dark real colours (leaf green, a bark brown on
  the wood pieces, a grey-green moon rim), and mid, grove and far are no longer one near-black blue: each is lighter, bluer and greyer than the
  layer in front of it. Targets and measurements in D120; the maths, the draw order and the gates are unchanged.

## D109. A skill's first meeting has a pocket in view and calm, and the false honey fungus has a trip-and-stand pocket (#500, #499)

- **Status:** Accepted (author, 2026-11-20: decisions 1 and 2 of the batch-4 design). Gate built (`FirstMeetingPocketTest.skillPocket`, new
  signals in `signals`). Extends D105's first-meeting pocket; the band and the clearance are unchanged.
- **Problem:** D105 asks a first meeting's pocket to show the new element "in view and acting". A wall jump is geometry and never acts, so
  `forest-walls`, whose first wall stood inside a YellowMob's patrol, had no pocket to pin; and the false honey fungus, a touch trap
  whose trigger zone reaches 64 px beyond its 24 px cloud, was excluded with the stump and the Bomb by a line that said "only the
  deadfall" (D105, ROOMS.md).
- **Decision:** (1) *A skill's pocket* is a spot where the whole first obstacle lies inside the camera view and no enemy, hazard or trap of the room stirs during
  the wait (`skillPocket`: the hero stands 12 s, is never hit, the climbable `Wall` block at the card's geometry x is inside the view every
  frame, and no enemy, hazard or trap changes position, state or phase). It replaces "seen acting" for a skill only. (2) *A touch trap whose trigger lies outside its lethal
  band* has a trip-and-stand pocket. The condition is measured, so no trap is excluded by name (author, 2026-11-20): the deadfall (D88), the false honey fungus (D63) and the thorn root and tuft meet it, while the exploding stump and root and the Bomb, whose trigger lies inside their blast, do not and still have no pocket. At the fungus the hero stands in its zone short of the cloud, sees it swell and burst (`FungusTrap.state()`, acting `BURST`) and is never touched. What the exemption carries: nothing is excused; each pocket is a measured, pinned test.
- **Applied in batch 4 (cards approved 2026-11-20):** `forest-entry` (the YellowMob behind a 32 px cover; Bat 41 moved to x 352; the two known Bats
  stay in view from the pocket, 112 px away, as in the fissure room), `forest-walls` (the wall jump met alone, YellowMob moved to x 1040) and
  `forest-canopy` (Bat 65 removed). `forest-entry`, `forest-walls` and `forest-canopy` left `FIRST_MEETING_GRANDFATHERED` (the list is now empty, ceiling 0, with batch 3, D110). Details and measured times: ROOMS.md, "Reworked under D105, batch 4". The trip-and-stand condition is measured, not a list (`FirstMeetingPocketTest.tripAndStand`, asserted from the map by both pocket tests; the stump and the root fail it): the trigger reaches at least a hero's body width (16 px) past the lethal band and the pocket stands at least that clear of it; the deadfall and the fungus are the members today. The skill pocket also fails when any enemy, hazard or trap of the room (enemies, lethal hazards, deadfalls, fungus, falling, exploding and thorn traps, puffballs) changes position, state or phase (`aSkillPocketTooFarFromTheWallFails`).
- **Found in the build:** the thorn vine, now beat 3 of `forest-canopy`, had a cheapest death at **6.45 s**, not the estimated 5 s (that was the
  mastered hero's arrival), over D105's 6 s. The gate was not loosened: the vine's `phase` went from 0 to 2.77 s (scanned 0 to 4.4 s), which makes
  the cheapest death **5.93 s** with the mastered route unchanged (9.48 s, no wait). The vine is in view for 4.90 s (more than its 4.5 s period) before the
  mastered hero can reach it, so a first-timer sees it swing (D84). The cost: the slack before the vine hits a late walker fell from 0.85 s to **0.6 s**
  (held 0 to 35 frames passes; at phase 2.9 it would be 0.45 s with a 5.80 s retry, at 2.7 0.65 s with a 6.00 s retry). It is no gotcha: the mastered hero
  needs no wait at all, so the 0.6 s is margin on a route that never stops, a first-timer is not killed by arriving on time, he is killed only by arriving
  0.6 s or more late and then sees the swing, and the coin route (the only one with waits, near Bat 41 in `forest-entry`) is another room. `forest-canopy` left the list too.
- **Not verified:** how the reworked rooms feel to a first-time player (#135).
- **Update (playtest 2, [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563), 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)):** the wall-jump skill pocket went with the wall jump, removed by [D121](#d121-the-wall-jump-is-removed-walls-are-solid-and-cannot-be-grabbed-546-2026-11-21); a skill that remains (the branch climb) keeps this pocket rule, and `skillPocket` now finds a wall or a one-way branch at the card's geometry x.

## D110. forest-1's trap course is split in two: the escalators and the puffball each get a room, and the course loses a tuft and a lever pair (#500, #499)

- **Status:** Accepted (author, 2026-11-20: batch 3 of [#500](https://github.com/jlogicgames/edgard_in_kimeria/issues/500), option A, the room named `forest-1-escalators`). Built; revises D58's "the course is not split" (which the D72 audit of 2026-11-16 had already asked to revisit) and D68's guard-room start (see its Update).
- **Problem:** `forest-1-traps` was the densest room in the game (8 beats in 12.27 s, 1.53 s per beat) with a 12.73 s retry at the log, and the puffball was beat 7 at x 944, 0 px from the second RedMob's patrol. In place, the puffball could not be met within a 6 s retry without sharing the screen with the escalators (D72: the only unknown on screen), and the 384 px of floor between the wall and the pit fit at most three of the tuft, RedMob, puffball and log with a 96 px clearance. A room of 4 beats in about 5.6 s with both escalators would have been 1.4 s per beat, under D105's 1.5 s floor.
- **Decision:** the room splits at x 640 into two rooms of exactly one screen (640 px = the camera's view, so the puffball is never in view from the escalators and the whole course is in view on entering): `forest-1-escalators` (x 0-640: the falling platforms, the running escalators, the lever and its wall) and `forest-1-traps` (x 640-1280: the puffball, the RedMob 38, the rotten log over the pit). Removed: lever 33 and its overhead escalator 34 (a repeat with no role: it ran above the hero's body and nothing needed it, as the lever-and-torch pair #375 removed) and thorn tuft 35 (a reskin of a known element; forest-1 keeps one in `forest-1-spider`). Moved: the puffball 36 from x 944 to 768 with `phase` 0 to 3.4, the RedMob 38 from x 864 to 928, two bushes. No other room moved; forest-1 has 12 rooms and the game 23. The seam is drawn and solid: walls at x 624-640 and 640-656, the floor cut at x 640, the seam tiles (Background columns 39 and 40) copied value for value from the art-era seam columns 219 and 220 (column 80's top cap tile 105 shows a layer edge above the room, which D108's look check refuses). **The foreground (D108):** the old room's one `foreground-forest-1-traps` group was split with the room: the pieces left of x 600 became `foreground-forest-1-escalators` (`roomx` 0) and the others `foreground-forest-1-traps` (`roomx` 640, x moved 640 to the left); the art's `edge-leaves` and `edge-fern` (with their feet) stood at x 1568 and 1600, where the old room's camera carried them to the room's end; they are placed at x 560 and 592 in the traps group so they frame the 640 px room's right edge the same way. `tools/parallax_game.py` writes both (`ESCALATORS_FOREGROUND`, `TRAPS_FOREGROUND`); `parallax_mock.py --check --game` passes rule B and the look check for both rooms.
- **The puffball's first sight (D84: no gotcha):** its clock starts at the room's entry, so with `phase` 3.4 (period 4: calm 0-2.2, swell 2.2-3.0, cloud 3.0-4.0) the cloud is out at the entry, sinks at 0.6 s, the straight walker is in its column at 0.8 to 1.13 s in the calm with no wait, a hero who waits sees the swell at 2.8 s and the next cloud at 3.6 s (measured, `Forest1TrapsTest.thePuffballIsOutAtTheEntrySinksBeforeTheWalkerArrivesAndSwellsBeforeItsNextCloud`). The rejected alternative, a cloud that meets the straight walker, is a deterministic first death as in the guard room (D68), which D84 forbids at a first meeting.
- **Measured (scripted hero, pinned within 0.1 s):** `forest-1-escalators` 5.48 s (3 beats, 1.83 s per beat); `forest-1-traps` 6.00 s (3 beats, 2.00 s per beat), the death at the log 6.47 s after the entrance (`Forest1TrapsTest`). Both pockets are in `FirstMeetingPocketTest` (`forest1EscalatorsTheEscalator` x 240, `forest1TrapsThePuffball` x 744, acting `CLOUD`), which needed a `Puffball` line in `signals()`: the puffball neither moves nor acts, so nothing would show `CLOUD`.
- **Allocation (`AllocationBudgetTest`, 3 isolated runs, no ceiling raised):** the trap course 544.3, 328.3, 328.3 B/frame under its unchanged ceiling 665; the new escalator room 536.0, 320.0, 320.0 B/frame (its scene `forest1EscalatorsRoom`, ceiling 620 = highest run 536.0 + about 16 %, the same as the other forest-1 room ceilings); the guard room 544.0, 232.0, 232.0 (guard awake 544.0, 328.0, 328.0) under its unchanged 1,280.
- **Gate:** `FIRST_MEETING_GRANDFATHERED` lost `forest-1-traps` and `forest-1-guard` (5 to 3 entries: `forest-entry`, `forest-walls`, `forest-canopy` remain); `RoomCardsTest` counts 23 rooms. `Forest1TrapsTest` lost its tuft tests (the tuft's mechanics stay covered by `ProximityTrapsTest` and `Forest1SpiderTest`) and gained the census of the split course, the seam, the first-sight facts and the pinned route and retry. The tests that walk `forest-1` by room index (`enterNextRoom`, `roomOf`) were re-indexed, none relaxed; the one changed assertion is D68's 140 to 117 px (its Update).
- **Not verified:** how the split course feels to a first-time player (#135); the thresholds are authoring numbers.

## D111. Look up and down are removed: the camera no longer pans for a look (D25, D29 superseded) ([#513](https://github.com/jlogicgames/edgard_in_kimeria/issues/513))

- **Status:** Decided by the author, 2026-11-20. Supersedes D25 (standing look) and D29 (the outline yields to the look); their text stays as the record.
- **Decision:** Holding Up or Down while standing still no longer shows a head pose or pans the camera (100 px up, 150 px down, after 0.3 s or 1 s by a target). `LookAround`, the `LOOK_UP` and `LOOK_DOWN` states and clips, `GameplayInput.lookDirection` and the look sentence of `ABOUT_BODY` and `ABOUT_BODY_LEFT_HANDED` (all five catalogs, a pure deletion) are gone. Up and Down keep every other use: ladders, Up/Down + Action to pick up and put down, the touch stick, menus. The camera is the ordinary follow camera clamped to the room (`Player.cameraTarget`).
- **Why:** No puzzle, hint or room needs the look. The Forest parallax (D108) is built for the floor camera, and a 100 to 150 px pan moved every layer far past the art and the `parallax_mock.py` check; the author chose to drop the feature rather than keep fixing its interaction with the parallax.
- **Hero frames:** the clips `LOOK_UP` and `LOOK_DOWN` (D79, 1 frame each) are removed from `Player`. The look frames (`r.look1`, `r.look2` and the left pair, sheet row 5) were first left in the grid and the sheets; a follow-up change removed them: the `look` clip left `HERO_CLIPS` (`tools/sprite_layouts.py`), the hero sheets went from 15 to 14 rows (960 to 896 px), and every later row moved up by one (clamber 6 to 5, hit 7 to 6, appear 8 to 7, disappear 9 to 8, carry idle 10 to 9, carry run 11 to 10, throw 12 to 11, land 13 to 12, pull 14 to 13) in `Player`, `HeroFrameTest`, `SpriteSheetContractsTest` and the contrast gate's carry-idle crop.
- **Vertical parallax:** the camera top still changes. A forest room is 368 px tall and the view 360 px, so `Player.cameraTarget` keeps the top between the room top (hero standing high, above y 256) and `FallingTrapLayout.floorCameraTop` (room bottom minus the view, 8 px lower, hero on the floor), and a room change pans it. So the vertical parallax code (`ParallaxRenderer`, factor 1 on `parallaxy`) stays; only the look-sized ranges went: `parallax_mock.py` lost `LOOK_PLAN`, the camera-top sweep (-100 to 158), the look frames and GIF and `check_look` (visible layer edges and open ground at the look tops), keeping rule B, the cover time and the exclusions at the steady camera. The bleed art drawn for the look range was trimmed by the follow-up change, so no gap is left untracked: the bleed a layer of factor f needs over the camera top's 0 to 8 px is at most 8 f plus the shake's 1.5 (1 + f) px (12.8 + 2.4 px at the foreground's 1.6, none for the play layer; the shake, at most `ScreenShake.LEVER_AMPLITUDE_PX` and `STONE_AMPLITUDE_PX` of 1.5 px, is clamped to the room, so counting it is conservative), and every layer keeps one tile row (16 px) of margin. `play-above` 7 to 1 row (`offsety` -112 to -16), the deep soil 10 to 1 row (`deep-2` pieces removed from the terrain grid, tile ids unchanged), `mid` from 30 to 25 rows (`offsety` -48 to -16), `far` 18 to 17, `grove` 21 to 19, the sky and the other factor-0 layers 33 to 24 rows, foreground `-top` pieces 12 to 2 rows, `-foot` pieces 16 to 1 row, the `edge-*` pieces 15 and 13 to 4 rows (their feet removed). `parallax_mock.py --check` gained `check_bleed` (a cut edge or a missing `-top`, `-foot`, `play-above`, `play-below` or strip layer at any camera top 0 to 8, with the shake; `BleedCheckTest` removes each in turn and the check fails). Measured: the frames of the three game rooms at camera tops 0 to 8 (63 frames) are pixel-identical before and after; the shipped assets shrank from 310 to 233 KB (`forest-fg.png` 92 to 55, `forest-1.tmx` 154 to 115, the hero sheets 13 to 12, the mid strip 26 to 25) and the art sources (grids, exports, mocks) by about 1 MB. The stale look previews (`preview-*-up.png`, `preview-*-down.png`) are deleted.
- **Gate:** `AllocationBudgetTest.forest1ParallaxLayersAndForeground` now sweeps the camera top 0 to 8; `ParallaxRendererTest` pins the shift at 0 and 8; `DirectionalActionsTest` keeps the Up + Action lever and the touch pickup tests, and drops the look tests; `LookAroundTest` is deleted. No other assertion was changed in that change. The follow-up that trimmed the bleed changed three pins of `TiledMapParallaxTest` to the new art (shipped geometry, not tolerances): `mid` `offsety` -48 to -16, `play-above` `offsety` -112 to -16, `play-below` 10 to 1 rows; and `HeroFrameTest` and `SpriteSheetContractsTest` follow the hero sheet's 14 rows.

## D112. forest-0 and forest-2 take the Forest look, and the third-party terrain sheet is retired ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236))

- **Status:** Accepted (author, 2026-11-24); proposed 2026-11-20 with D108 ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)); the canopy clumps and the recolours below
  waited for the author's look like the rest of the card.
- **Problem:** forest-0 (6 rooms) and forest-2 (5 rooms) still drew the flat `2c3f66` and the third-party `Tileset.png`: two games side by
  side, a seam through the ground at every room edge (the blocks' outlined ends met there) and a straw rope, the grab cue's colour, over
  every room-edge wall.
- **Decision:** (1) `tools/parallax_game.py` installs the layers of D108 into all three forest maps (sky, stars, moon, clouds, far, grove,
  mid, `play-above`, `play-below`, the buried bottom row) and `ForestTerrain.tsx` as their play tileset; (2) `retile` changes only which
  tile a drawn cell shows, never whether it is drawn (collision and `CollisionVisibilityTest` read that): a ground tile takes its left,
  middle or right column from its neighbours, keeping its authored row, so floors meeting at a room edge read as one; the rope column over
  a `Wall` becomes soil like the other walls (lit top where open air is above it: a short wall in `forest-walls` can be stood on), and a
  slab tile with a floor top on both sides (forest-2, x 1520) becomes the floor's top (a slab lip over a block keeps its caps). The lit
  lip means "stand here", so a drawn cell with no solid collision never carries it: forest-2's 91 rock cells that are scenery (the ruin
  at the entry, cols 1 to 9, and the mounds) draw new back-rock tiles (`back-*` in `terrain.grid`: the boulder and slope drawings with the
  straw lip and the moss in the rock's own tones), and its 20 ground tops with no collision (the floating blocks, the block over the
  stone) show the soil body. forest-0 and forest-1 have no such cell. This also removes forest-1's ropes; its walls stay, so the hero
  can still clamber on them; (3) every room of forest-0 and forest-2 (and, since 2026-11-20, forest-1) has a `foreground-<room>` group, each piece kept only where the room
  passes rule B and the cover time with everything placed before it (lists in `parallax_game.py`); the boss roams its arena, so
  `parallax_mock.py` protects the whole arena above the floor and the boss room keeps low clumps and reeds outside it; (4) the checks know
  rooms narrower than the view (`forest-0-lever` 576 px, `forest-walls` 544 px, `forest-boss` 448 px): one camera, centred as
  `Player.cameraTarget` does (`parallax_mock.room_cameras`, `check_contrast.Scene.cameras`), and the last room's route ends at its right
  wall; (5) the hanging vines (#239) hang in front of a dark canopy clump of the play layer (`canopy-*` in `terrain.grid`, no collision,
  over the room top in `forest-0-platform` and `forest-canopy`): in front of the moving sky no vine colour stays decor (outline 0.315 at best,
  over the moon and the clouds, 0.43 over a mid trunk), in front of the clump one does; `check_tiled_links.py` does not let a stone's lip
  hang from a canopy cell; (6) `Tileset.png` and `Forest.tsx` are deleted. What the maps drew from them: the terrain tiles (ground, slab,
  ledge, boulder, slope) were already redrawn in `terrain.grid` (the sheet the game loaded since D108 for forest-1); the 24 non-empty
  prop tiles (village sign, bare trees, ochre tufts) are copied pixel for pixel into `terrain.grid` (`prop-<id>`, #189), so the exported
  sheet was identical before the canopy and back-rock tiles were added.
- **Measured over the new layers (`check_contrast.py`, every placement and camera):** recoloured in OKLab, hue and tone roles kept: the
  crown (L x0.55) outline 0.036 to 0.184, body 0.144 to 0.190 (was 0.302 and 0.385); the look-alike (L x0.55 - 0.10) 0.157 and 0.145 (was
  0.413 and 0.475); the trunk (L - 0.07, chroma x0.3) 0.072 to 0.170 and 0.090 to 0.189 (was 0.222 and 0.237), forest-1's trunks included;
  the vine (L x0.6) 0.120 and 0.139 over the clump (was 0.428 and 0.457); the beetle body to the spider's and snail's dried umber (hue 1.0
  rad, chroma 0.045, L kept), body 0.094 to 0.175 (was 0.034 over forest-2's grove spruces; `BeetleFx.SHELL_CHIPS` follow). The bat and
  mushroom pickups get the moon rim (D92: an active outline under 0.28 carries it), outline 0.375 and 0.390 at worst (was 0.151 and
  0.057 over their halo on near black). Three forest-2 bushes in front of the third-party ochre tufts moved off them as in D108 (x 400 to
  432, 768 to 816, 1800 to 1808), body at most 0.198. No threshold changed. Rule B: 0 covered objects in 14 rooms, the hero hidden 0.00 s.
- **Gate:** `validateContrast`, `validateParallax` (`--check --game`, now 14 rooms), `tools/test_parallax_game.py` (every map is what the
  installer writes, every forest-0 and forest-2 room has a foreground, `retile` and the narrow-room camera), `TiledMapParallaxTest`.
- **Since D114:** `retile` also gives the blocks faces, an underside, inner corners at a wall's foot and soil variants; a top under a wall
  is no longer drawn with the straw (it shows the wall foot), the rest of this decision is unchanged.

## D113. The foreground takes big near pieces, drawn by hand ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236))

- **Status:** Accepted (author, 2026-11-24); proposed 2026-11-21 with D108 and D112 ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)); the look waited for
  the author like the rest of the card; the author accepted the copy-only `stamp.py` on 2026-11-21 (below).
- **Problem:** the author asked for a foreground that reads as right in front of the camera: big, dark shapes (Hollow Knight, Dead Cells),
  not the thin strands and small clumps of `fg.grid`. The earlier big pieces (`trunk-near`, `rise-trunk`) read as flat black slabs. Two
  first sets were rejected in review: one made by a script (polygons and seeded strokes, then a Gaussian blur: code-generated scenery and a
  Gaussian blur; its root bank lay under the floor line, its mound read as spiky balls, its fern as a smeared block, its trunk as a tube),
  then one built from copies of a single hard-edged lens leaf (the canopy's hanging ovals read as stones under forest-0-platform's
  falling-stone hint, the bush as black eggs or boulders by the floor, the fern's pinnae were placed by a loop and a size rule).
- **Decision:** (1) three pieces in `art/parallax/forest/fg.grid`, after the old ones (their tile ids and the mocks unchanged): `near-canopy`
  224x112, `near-fern` 224x128 and `near-sprig` 128x112, all hanging from the top edge with a `-top`. How they are made, in
  `art/parallax/forest/near/`: three leaves typed row by row (`leaf-broad.txt` 40x29, tilted down to the left; `leaf-small.txt` 32x19,
  turned right; `leaf-edge.txt` 16x37, seen edge-on), each with a body gradient `a`, `k`, `l` toward the moon and soft edges of two or
  three typed outer steps with dither (`h`, `g`, a dithered `i` away from the moon; `m`, `e`, a dithered `j` toward it), no dark outline;
  fern pinnae in three sizes (`pinna.txt`) and a rachis (`rachis.txt`), typed: each fern frond has its own continuous stem glyph
  (`f1` to `f4`, author 2026-11-21: "the stem is broken in several places, that is not natural"), drooping 1 px sideways every 8 to 14
  rows, one way per frond, thinning to a tip; the sprig's stalk uses the short pieces `r20`, `r10`, `r5`. The placement lists
  (`canopy.spec`, `fern.spec`, `sprig.spec`) are typed too: every leaf, pinna and stem has its own line, with irregular spacing, sizes and
  gaps; each fern pinna sits on its own row of its stem, and the fronds differ in length (y 65 to 111), droop (two left, two right) and
  pinna density (5 to 11 px apart, big near the top and smaller toward the tip). `stamp.py` copies the typed
  characters onto a canvas at those positions (the later in front; a back leaf is the same leaf through the palette swap `a k l` to
  `k l m`; a soft edge `g` or `h` landing on an opaque leaf becomes that leaf's dark edge `a`, an `i` or `j` there leaves it as it is,
  so overlaps leave no see-through holes) and slices the canvas into the piece and its `-top`; it draws no shape, adds no noise, applies
  no filter. Its output replaces everything after the near-pieces comment at the end of `fg.grid`. The author accepted this copy-only stamp
  on 2026-11-21 (it is not a shape generator); without it the same rows would be pasted by hand. A trunk and a bush were drawn and dropped (a striped tube; a
  pile of eggs); (2) the rooms of all three forest maps were recomposed (`tools/parallax_game.py`): a big top mass in the start corner
  (canopy and fern in turn) and another in the end corner right of the moon at the last camera (the sprig where only it fits); the 7
  rooms with a falling or thrown stone (forest-0-platform and -stone, forest-1-spider, -climb, -hive and -deadfall, forest-canopy) take no
  canopy. Each piece was kept only where the room still passes rule B and the cover time with everything placed before it, with at least
  a fifth (canopy, fern) or 0.3 (sprig) of it on screen over the room at the first or last camera; one earlier curtain or the reeds stay
  beside them, forest-0-lever keeps its broad-leaf clump (the first room needs a standing piece with a `-foot` for `BleedCheckTest`) and
  the boss room its reeds; (3) the tests follow the pieces (`TiledMapParallaxTest`: the mob room's and the escalators' canopies at x -88,
  the traps room's at 540 and its fern; `test_parallax_game`: the mob room's canopy lowered over the red mob must fail). No threshold or
  rule changed.
- **Measured:** `parallax_mock.py --check --game`: 23 rooms, 0 covered objects, the hero standing hidden at 0 box x, hidden at most 0.00 s
  on the floor and at the jump apex, no visible layer edge. `forest-fg.png` grows from 896x832 to 896x1072 px and from 54 710 to
  68 158 bytes (+13 448).
- **Gate:** `validateParallax`, `tools/test_parallax_game.py`, `TiledMapParallaxTest`, `validateContrast`.

## D114. Terrain blocks get faces, an underside and soil variants ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236))

- **Status:** Accepted (author, 2026-11-24); proposed 2026-11-21 with D108 and D112 ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)); the look waited for
  the author like the rest of the card.
- **Problem:** the art review of the forest maps found the raised blocks flat: no side light, no underside, no bottom edge under an overhang
  (`forest-1-guard`, the block at x 192 to 448; `forest-1-lever`), and the soil body repeating one motif every 16 px.
- **Decision:** (1) new tiles drawn by hand in `art/terrain/forest/terrain.grid` (one character per pixel, exported by
  `tools/terrain_sprites.py`): the ground piece's left and right columns become the faces (the moon of D92 is up and to the right, so the
  right face is lit, `5c412c` with a `765639` rim, and the left face falls to `150e0a` at the edge), its bottom row the underside (the soil
  darkens, a lit lip, a near-black underside with root tips), the buried corners carry the faces into the deep soil; under `Block faces`: a
  second left and right face, three soil bodies (strata and flecks in other places, no round clod: a first clod read as a dot grid), the
  inner corners (moss against the face, no straw) and a one-tile pillar (its top and body are halves of the caps and faces, copied);
  (2) `tools/parallax_game.py` `retile` picks them from the drawn ground around each cell, after its D112 steps (ropes, slabs set into a
  floor, no straw off solid ground): a top with ground above it (a wall's foot) is soil; a body cell with air below is the underside; air on
  the left shows the left face, on the right the right face, on both the pillar; a body cell under a wall whose top-left or top-right cell is
  air carries the inner corner; a face takes one of its two drawings and a soil body one of four by a hash of the cell's row and column
  (`variant`), the body never the same as its left or upper neighbour. Past the map's sides and bottom is ground, above its top air. It only
  changes which tile a drawn cell shows, never whether it is drawn, and touches no collision or object; a second install changes nothing.
  The neighbourhood is the drawn ground, not the collision: the faces show the shape the eye sees (forest-2's scenery blocks have no
  collision and still get faces); the straw keeps following the collision (D112). A slab stays a slab (its open underside is already an
  overhang); a slab lip over a block keeps its caps.
- **Measured:** forest-0, forest-1, forest-2: 950 face cells, 46 underside cells, 59 inner corners, 54 pillar cells, 1 774 soil bodies
  (451, 457, 433, 433 per drawing); no cell's emptiness changed (`test_retile_never_changes_which_cells_are_drawn`). `check_contrast.py`
  passes (no threshold changed); `parallax_mock.py --check --game`: 0 covered objects, the hero hidden 0.00 s.
- **Gate:** `tools/test_parallax_game.py` (the underside under a block with air below, the left and right faces, the wall foot's inner
  corner and no straw on it, no straw on any face, body or corner tile, the variants fixed by place and never alike side by side, the
  maps' drawn cells unchanged, every map what the installer writes), `validateParallax`, `validateContrast`, `CollisionVisibilityTest`.

## D115. The forest wolf: it sees far, outruns the hero and lunges along a line; jump it, then strike (#331, #240, #398)

- **Status:** Accepted and built (author, 2026-11-20: "the wolf must be faster and more dangerous than the other enemies";
  2026-11-21: the card and every recommendation of the proposal, the danger-family crimson, the first meeting in forest-2, the art
  with the author's edits to `dead` and `bay`, the sounds with the barks lowered). Placed in `forest-glade`, forest-2's fourth room (the author approved
  its card on 2026-11-21: [ROOM_CARDS.md](ROOM_CARDS.md#forest-glade), [#331](https://github.com/jlogicgames/edgard_in_kimeria/issues/331)). The lunge is **160 px in 0.25 s**
  (author, 2026-11-21: "Yes, 160"; see Why). Card:
  [GAME_DESIGN.md, "Wolf card"](GAME_DESIGN.md#wolf-card-built-d115-331).
- **Decision:**
  - **The fastest and most dangerous Forest monster.** It is the first monster faster than the hero, it senses the furthest,
    commits from the furthest and strikes the furthest of the monsters that close in, and it gives the shortest window after a
    miss. **Without the assist, only a jump escapes its lunge**: running and stepping out are bitten. Besides escaping, the
    hero can kill it as it comes (a live sword, a fireball, a stone) or go up out of its reach.
  - **Body:** 48×24 frames, seventeen in one strip (`stand`, `trot1`, `trot2`, `alert`, `hackle`, `scrape`, `run1` to `run4`,
    `skid`, `crouch`, `lunge`, `bite`, `shake`, `bay`, `dead`), drawn about 20 px tall and 38 px long, the largest Forest monster;
    a crimson pelt in the danger family (#236), 11 colours, 12 with the moon rim lit from above only. Body hitbox `rect(6, 6, 34, 18)`;
    jaw box `rect(40, 9, 7, 7)` (8 to 15 px above the floor line), whose front edge, the muzzle, is the reference of every reach.
    **Contact is harmless**: only the jaws kill.
  - **Patrol:** it trots at 40 px/s inside its authored range (`offNeg`/`offPos`), with a 0.6 s sniff at each end; it never
    leaves the range on foot and never leaves its floor (no hops up steps, no leaps over gaps; leaping is a later develop,
    [#525](https://github.com/jlogicgames/edgard_in_kimeria/issues/525)).
  - **Senses:** sight **256 px** ahead, in a 48 px band above its floor line, cut at the first solid at its eye height (16 px);
    hearing, a **64 px circle** around its body's centre, through anything.
  - **Notice, in body language (D100):** alert, hackles, one paw scrape: **0.5 s**, not scaled by the assist; its own call
    `wolf_notice`. No mark, no shared sound.
  - **Chase:** **160 px/s** (1.6× the hero's 100), full speed in 0.25 s; turning costs a 0.125 s skid (about 10 px).
  - **Commit and wind-up:** when the hero's hitbox touches the **72 px lunge box** (from the jaws' back edge to 72 px past the
    muzzle, 24 px up from its floor line), its direction locks and it stops where it is and winds up for `TrapWarnings.duration(BITE_WARNING_SECONDS)`, sampled once:
    **0.5 s, 0.75 s with the assist**, with pale breath streaks along the whole line its lunge will cover (traced at the commit over the floor under it) and `wolf_windup`
    (`_long`).
  - **Lunge and bite:** **160 px in 0.25 s** (640 px/s) along its floor; a jaw box of about 7×7, 8 to 15 px above the floor line,
    lethal during the lunge and the 0.1 s bite and swept every frame; the bite is seen and heard, hit or miss (D93). The lunge
    stops at a solid face and at the end of its floor (#497's rule), and may pass its range end over floor by up to its reach.
    A bite that reaches the hero lands at the start of the wolf's next frame, so a killing hit on the same frame wins.
  - **Floor re-check:** the line is traced once at the commit, and every lunge frame checks the floor and solids again: a floor
    that goes during the wind-up (a lever-driven wall set inactive) or a solid that comes stops the lunge at the last good place,
    its body never over air, and the bite still lands seen and heard.
  - **Recovery:** **0.5 s**, harmless, not scaled by the assist; then a new full wind-up for each lunge.
  - **Refuge and give-up:** a hero on anything more than **24 px** above its floor, or more than its 72 px commit box beyond its
    range end, is out of reach (the code commits only when the hero touches the commit box; a hero 73 px or more past the muzzle at
    the range end is safe although the 160 px lunge would cover him, the card's "range end plus the lunge" was loose wording): it runs to the nearest reachable point (under him, kept inside its range) and bays, and **gives up after 1.5 s**
    (also after 1.5 s out of both senses); it does not notice that hero again until he has been out of its sight, which looks only
    ahead, for 1.0 s, so it trots away and notices him again at its next turn, a patrol and not a loop under the ledge. A solid's
    face cuts its sight at eye height (16 px), so a hero on a solid block beyond its face is not seen: a refuge for a first meeting
    is a one-way platform, or within its 64 px hearing.
  - **Answers:** every weapon (the sword with the light spray of D65, a fireball, a stone or torch, a stomp with the normal
    bounce) kills it in any state and leaves its body (D101): on its side, legs straight out; it twitches, it does not rock.
    Its death plays the shared `bounce`.
  - **Room rules:** D3/D6 as for every enemy. Load checks (`Wolf.problem`): its range inside the room on gapless floor and at
    least 160 px long; no standing place (the entrance body, the `RoomExit`, a `Checkpoint`) inside its sight box (256 px, which
    includes its reach);
    at least 64 px of headroom over its whole reach. Met in play (D100): its first meeting is a refuge pocket in `forest-glade`,
    a one-way branch 32 px above its floor, from which the hero sees it notice, sprint and bay (D105).
  - **Assist:** only the wind-up is longer (0.75 s); notice, speeds, skid, recovery and give-up are not scaled.
- **Why:** every other Forest monster loses a foot race, so walking away answers all of them; the author asked for one that
  does not. The wolf keeps fair by what it cannot do: it warns twice (the notice, then the wind-up with its whole line drawn),
  its contact is harmless, its jaws run low so a jump always clears them, it never strikes over a gap or through a wall, and its
  chase ends at its range and at any refuge. **Why a 160 px lunge:** a hero who keeps running gains 100 px/s through the wind-up
  *and* the 0.25 s lunge, so his near edge is 72 + 75 = 147 px from the muzzle when the jaws end. At 160 (0.25 s, 640 px/s) he is
  bitten by 13 px without the assist, and with the assist's 0.75 s he is at 172 and missed by 12 px. The shared 0.8 s warning would let a runner escape (177 px against 160), so the
  wolf shares the spider's bite warning. A real hero reacts later than the commit, which only makes the bite more certain; the
  the constant is `Wolf.LUNGE_REACH`.
- **Measured (`WolfTest`, perfect-reaction heroes):** a hero who runs from the commit is bitten at every commit gap from 40 to
  72 px without the assist and missed from about 60 px up with it. The latest take-off after the commit that clears the jaws is
  0.55 s at the edge of the lunge box and 0.45 s at the muzzle (0.82 s and 0.72 s with the assist, where a jump made at the commit
  itself has landed before the jaws arrive: the first clearing take-off is 0.07 s after it). A hero who runs through the assist's
  warning is struck again about 2.0 s after the first lunge began (1.97 s). Allocation (`AllocationBudgetTest.wolfThroughTheRealLevel`):
  0.2, 0.6 and 0.6 B/frame in 3 runs before the floor re-check, 0.0 in the one run after it (ceiling 8). Contrast at the 15 monster spawns of the three maps (outline, active floor 0.28; the
  spider measures 0.300 to 0.331, the snail 0.302 to 0.339): live frames 0.315 (`alert`) to 0.367 (`shake`) at worst; the body (decor,
  ceiling 0.20) 0.068 to 0.184. The body's silhouette differs from its closest live frame (`bay`), either way round, in 52.5 %.
- **Gates:** `WolfTest` (37 tests: the fairness pins above, each its own test; every weapon in every state; the refuge; the
  gap and the wall; a floor that vanishes during the wind-up, `wolf-split.tmx`; the failing load fixtures), `WolfFxTest`, `RemainsTest` (the wolf's case), `AllocationBudgetTest.wolfThroughTheRealLevel`,
  `check_contrast.py` (the wolf and its body, measured from the first placement, with its own `test_check_contrast.py` case),
  `tools/test_generate_sounds.py`, `SoundPreloadTest`; `FirstMeetingPocketTest.forestGladeTheWolf`, `ForestGladeTest` and `RoomCardsTest` with the placement (#331).

## D116. Second external playtest: findings and direction (#563, 2026-11-21)

- **Status:** Accepted by the author where marked, 2026-11-21; the other rows of the record are proposals until the author
  approves them in their issues. Record and analysis: [PLAYTEST_2.md](PLAYTEST_2.md). Umbrella: [#563](https://github.com/jlogicgames/edgard_in_kimeria/issues/563).
- **Playtest:** players unfamiliar with the game, Ukrainian speakers, web build (GitHub Pages) in a desktop browser, written
  feedback (31 remarks, F1-F31, plus the author's note on F21). Qualitative only: no room times, death counts or per-trap data; the
  number of players, whether the ending was reached and the audio were not recorded.
- **Accepted on silence:** the author ticked the open check boxes of #368 (Ukrainian text fit at 16 and 18 px, the boss HUD
  label size, the three-choice dialogue at 18 px, the stone dust on forest-0 tiles, the Ukrainian opening pages and the thread-pull
  visual on the web), #428 (forest-2 seam), #422 (forest-1 seams and the first RedMob), #138 (readable boss attacks, escape and retry cost) and #128
  (a playable build for newcomers) because the testers played those parts and raised nothing: weak evidence, not a measurement. The
  notes under D70, D73, D77, D78, D81 and the art and localization docs say what is covered and what #368 still holds open.
- **Direction (author, 2026-11-21; each change gets its own D-number when its issue lands):**
  - **Up and Down interact, no chords** ([#548](https://github.com/jlogicgames/edgard_in_kimeria/issues/548)): Up picks up and uses a mechanism, Down puts down; Up and Down also climb ladders;
    no interactive object near a ladder, enforced at load and in `tools/check_tiled_links.py`. Interaction on a fresh press, climbing on hold. Reverses D30 and parts of D18.
  - **The wall jump is removed** ([#545](https://github.com/jlogicgames/edgard_in_kimeria/issues/545), [#546](https://github.com/jlogicgames/edgard_in_kimeria/issues/546)): `forest-walls` keeps its two walls and gets a new way over, designed on its
    room card first. Touches D18, D38, D105 and D109.
  - **The decor tier rule changes** ([#555](https://github.com/jlogicgames/edgard_in_kimeria/issues/555)): trees and bushes are drawn opaque and clear in another palette, a little paler
    or darker than the active elements. Amends D76. The pickup and fairy glows become softer pixelfx effects ([#544](https://github.com/jlogicgames/edgard_in_kimeria/issues/544)).
  - **The yellow falling platform is replaced by the log skin** everywhere ([#551](https://github.com/jlogicgames/edgard_in_kimeria/issues/551)), the plain skin dropped from code, sprites, Tiled, docs and text.
  - **The bomb leaves the forest** for a leg-hold trap, and goes to the Fortress later ([#561](https://github.com/jlogicgames/edgard_in_kimeria/issues/561)).
  - **The ember fissure leaves the forest** for a forest clocked trap, and returns later as a Swamp geyser ([#562](https://github.com/jlogicgames/edgard_in_kimeria/issues/562)). Amends D91.
  - **Exits are marked by a portal with a ripple transition** ([#552](https://github.com/jlogicgames/edgard_in_kimeria/issues/552), [#553](https://github.com/jlogicgames/edgard_in_kimeria/issues/553)): a pixelfx portal (MIT, the Sprite Fusion notice kept)
    ported to `KimeriaParticleSystem`, allocation free, and a ripple when the hero leaves and enters a room (D3 timing to be stated).
  - **Smaller author decisions:** the Assist menu is renamed Accessibility ([#533](https://github.com/jlogicgames/edgard_in_kimeria/issues/533)); the boss HUD label is "Forest Guardian"
    ([#542](https://github.com/jlogicgames/edgard_in_kimeria/issues/542)); the forest-0 lever drives another visible mechanism, amending D34 ([#549](https://github.com/jlogicgames/edgard_in_kimeria/issues/549)); the Puffball is rethought and
    redrawn ([#558](https://github.com/jlogicgames/edgard_in_kimeria/issues/558)); the coin's edge-on frame is fixed ([#560](https://github.com/jlogicgames/edgard_in_kimeria/issues/560)); the intro gets a skip hint, amending D77 ([#534](https://github.com/jlogicgames/edgard_in_kimeria/issues/534)).
- **Proposals (not approved), each in its issue:** an About description and Controls under Options ([#531](https://github.com/jlogicgames/edgard_in_kimeria/issues/531), [#532](https://github.com/jlogicgames/edgard_in_kimeria/issues/532)); the intro retold
  in tale formulae on a book ([#535](https://github.com/jlogicgames/edgard_in_kimeria/issues/535), [#536](https://github.com/jlogicgames/edgard_in_kimeria/issues/536)); the dialogue frame band and tale-voice lines ([#537](https://github.com/jlogicgames/edgard_in_kimeria/issues/537), [#538](https://github.com/jlogicgames/edgard_in_kimeria/issues/538)); the fairy on the dialogue
  screen and the healer-screen icons and hand-over ([#539](https://github.com/jlogicgames/edgard_in_kimeria/issues/539), [#540](https://github.com/jlogicgames/edgard_in_kimeria/issues/540), [#541](https://github.com/jlogicgames/edgard_in_kimeria/issues/541)); the boss HUD redraw ([#543](https://github.com/jlogicgames/edgard_in_kimeria/issues/543)); edge rocks
  ([#547](https://github.com/jlogicgames/edgard_in_kimeria/issues/547)); the forest-0 stone and vines lowered ([#550](https://github.com/jlogicgames/edgard_in_kimeria/issues/550)); a hint in `forest-1-lever` ([#554](https://github.com/jlogicgames/edgard_in_kimeria/issues/554)); branch redraws
  ([#556](https://github.com/jlogicgames/edgard_in_kimeria/issues/556)); the pickup flight ([#557](https://github.com/jlogicgames/edgard_in_kimeria/issues/557)); the beetle blast ([#559](https://github.com/jlogicgames/edgard_in_kimeria/issues/559)).
- **Not changed:** no threshold, tolerance or test was changed by this record.

## D117. A monster body keeps its live palette, ink and moon rim; the pose is the cue (#571, 2026-11-21)

- **Status:** Accepted (author, 2026-11-21, [#571](https://github.com/jlogicgames/edgard_in_kimeria/issues/571)). Amends D101 (contrast tier), D108 (the decor band for
  bodies) and D76 (bodies leave the decor tier).
- **Context:** in the author's playtest of 2026-11-21 the bodies were "almost invisible": since PR 517 all four sat on one dried umber
  (OKLab L 0.09 to 0.25, outline 0.04 to 0.20), a dark shape on forest-1's near-black ground. Three recolours were tried on the real
  ground (ash with a light contour, a muted crimson with a light contour, a lifted umber over a dust halo; outline 0.15 to 0.31 at the
  death spots): within the decor-to-active gap none read much brighter than the umber, and the halo could read as a pickup glow.
- **Decision (author):** "Let's keep the sprite with no filters at all. That is how other games do it, and nobody will confuse fallen bodies
  with real dangerous enemies." Each body (`art/objects/monster/{beetle,spider,snail,wolf}-body.grid`) is its monster's `dead` frame,
  pixel for pixel, in the live palette with the ink contour and the moon rim (the wolf's from above only, as the live wolf), exported by
  `tools/forest_trap_sprites.py` (the body sheets are in `RIM_SHEETS`): no darkening, no tint, no decor recolour, no halo. The crumble chips
  (`BeetleFx.SHELL_CHIPS`, `SpiderFx.SPIDER_CHIPS`, `SnailFx.SNAIL_CHIPS`, `WolfFx.WOLF_CHIPS`, passed to `RemainsFx`) take the same live
  colours. The kill's cross-fade into the body sheet stays in the code and now fades between two identical frames.
- **What still tells a body from a monster:** the pose (D101's readability rule, `RemainsTest`: the closest live frame differs in at least
  25 % of the silhouette), the stillness and the behaviour (harmless, blocks nothing, rocks or twitches when the hero passes, crumbles or burns).
- **Gate:** `check_contrast.py` measures the four bodies as **active** at every spot of `BODY_SWEEP`: outline at least 0.28 (stricter
  than before; nothing loosened). Measured 2026-11-21: beetle 0.501 at worst (forest-2 x 3508), spider 0.285 (forest-1-climb x 8406, its
  worst spot, 0.285 to 0.294 over x 8382 to 8406, where the body lies over the soil), snail 0.513, wolf 0.433.
- **Test changed:** `WolfFxTest.theChipsOfTheBodyAreFourPaletteColours` held every wolf body chip channel at most 0.25 (the dried umber);
  it is replaced by `theChipsOfTheBodyAreFourLivePaletteColours`, which requires each chip to be a colour of the live wolf's palette, a
  stricter pin of the same intent.
- **Not changed:** no threshold or tolerance; the pose, the rocking, the crumble and the burn.

## D118. The intro names its skip key (#534, 2026-11-21)

- **Status:** wording approved by the author, 2026-11-21 (one form in every language: "Esc: skip", "Start: skip" and their translations); amends [D77](#d77-story-screens-are-static-pages-playtest-f2-336-2026-11-16) "No new string". Playtest 2 F10 ([PLAYTEST_2.md](PLAYTEST_2.md), [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)): a tester wanted to skip and did not know how.
- **Decision:** the opening and the ending show a small grey hint in the bottom right corner (right edge 12 px from the screen edge, top 338 px, label font, 55 % brightness times the story's fade), under the page area (ends at 304 px) and clear of the page-turn cue. It names the key of the input mode in use: keyboard `INTRO_SKIP_KEYBOARD` ("Esc: skip"), a connected pad `INTRO_SKIP_GAMEPAD` ("Start: skip"); a touch device shows no text, its skip button is the hint. On a pad, B (back) and Start both call `IntroScreen.skip()` (`KimeriaGame.updateStory`); the hint names Start, as the issue says, the word the pad hints (`ABOUT_GAMEPAD`) already use. Esc, the system Back key, B and Start all skip.
- **Mode:** touch first (`TouchControls.isAvailable`), then a connected pad, else the keyboard; chosen each frame by `IntroScreen.skipHint`. Both layouts are cached once at load (`Overlay.hintBlock`), so the frame only queues a prebuilt block.
- **Input unchanged:** the hint draws only; skipping is still `IntroScreen.skip()` (D77), and the 0.6 s input lock and fresh-press rules are untouched.
- **Strings:** `INTRO_SKIP_KEYBOARD`, `INTRO_SKIP_GAMEPAD` in all five catalogs, plain voice, one form "key: verb" in every language. No native-speaker review yet.
- **Ending:** the ending shares the intro's skip rule ([D77](#d77-story-screens-are-static-pages-playtest-f2-336-2026-11-16), [#336](https://github.com/jlogicgames/edgard_in_kimeria/issues/336)): the same `IntroScreen.skip()` and the same hint.
- **Connected pad:** on the web a pad counts as connected only after its first button press, so a web player sees "Esc: skip" until then; a desktop with a pad plugged in shows "Start: skip" at once, as the Controls screen's pad table does (`ControlsView.modeFor`, D135).
- **Measured / not changed:** the page area and the page split are unchanged (the hint is outside it; `IntroPagesTest` passes in all five languages). The hint's rect is measured with the real QuestSquare-16 font in all five languages and both strings (`LocalizationLayoutTest.skipHintSitsInTheCornerClearOfThePageAndTheCue`): left of it right of the cue (x 324), bottom within 360 px, top (338) below the page bottom (304). Allocation (`AllocationBudgetTest`, the hint queued every frame): `pagedIntro` 0.0 B/frame, instant text off and on (ceiling 8; the harness's own input proxy, 120 B/frame, is subtracted as in D77); the ending scenario 0.0 B/frame (it does not set the hint, which is the same queued block). No threshold or tolerance changed.
- **Checked in the running game** by the author, 2026-11-24 ([#534](https://github.com/jlogicgames/edgard_in_kimeria/issues/534)): the look on desktop, web and a real touch device, and that a pad's B skips on real hardware.

## D119. A stone passes through bodies; everyone sways bodies and bushes (amends D101)

- **Status:** Accepted by the author, 2026-11-21. Amends D101 (what finishes a body, who rocks it) and D64 (who brushes a bush).
- **Author's rule (translated):** "Important! A body must not block a fireball, otherwise it will be annoying. That is: a sword hit
  or a jump from above destroys it; anyone running past (not only the player) makes it sway (bushes too: they sway from everyone);
  but a fireball flies through them, just like a stone or anything else thrown flies through bodies."
- **Author's answers on the open points (2026-11-21, translated):** "The thrown torch burns the body and flies on: acceptable, let it
  burn and fly on." and "Fireball: yes, let it set the body on fire and fly on." So "flies through" means never stopped or spent:
  the fireball and the torch keep burning a body on the way, as in D101.
- **Rule:**
  - Sword or stomp (with the normal bounce): crumbles the body.
  - Fireball and thrown torch: burn the body (flames, burn sound, gone after 0.3 s) and fly on, never stopped, slowed or spent (D101 kept).
  - A thrown stone and every other thrown object pass straight through: the body stays whole, the object is not stopped or deflected.
    Changed from D101, where a stone crumbled the body.
  - Any moving actor (the hero, a monster, the boss) rocks a body it touches (the wolf's body twitches), and brushes a decor bush
    (`Decor.notice`). Changed from D101 and D64, where only the hero did. A mover is the hero or a live `Enemy` (the boss
    included) moving sideways (`velocity.x` not 0); projectiles, thrown or falling objects and a snail's gob are not movers.
- **Why the author met a "block":** no code path stopped the ball (`Fireball` and `Carryable` skipped defeated enemies and let bodies
  be); the ball burnt each body it crossed, which reads as the body taking the ball. The author accepted that burn, so the fireball is unchanged.
- **Implementation:** the stone no longer calls `crumble()` in `Carryable`; `Remains.moverNear` finds a moving live actor in a body's near
  box, and `Level.updateDecor` offers every live enemy to every bush; index loops over `level.objects`, no allocation.
- **Tests:** `BeetleTest.aThrownStonePassesThroughTheBodyAndLeavesItIntact` (replaces `aThrownStoneCrumblesTheBodyAndFliesOn`; the stone is not
  stopped and the body whole), `aFireballBurnsTheBodyAndFliesOnUnslowed` (the ball's step is unchanged on every frame across the body),
  `aMonsterWalkingThroughTheBodyRocksIt`, `DecorTest.aMonsterWalkingThroughABushMakesItSway`. `SpiderTest` and the torch test keep the
  burn. No tolerance changed.

## D120. Depth palette: the near layers take dark real colours, the scenery turns bluer, lighter and greyer with depth ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236), 2026-11-21)

- **Status:** Accepted (author, 2026-11-21: "I approve the direction"). Amends [D108](#d108-the-forest-parallax-layers-are-read-and-drawn-by-the-game-scenery-apart-from-the-play-layer-a-foreground-per-room-236)
  (the foreground's one neutral near-black palette and the layers' near-black blue) and
  [D76](#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16) (what colour the decor tier is drawn in); every
  threshold is unchanged.
- **Context:** the author, 2026-11-21, after approving the monster bodies in their live colours (D117): "Do the same for the bushes, trees
  and other things in the foreground: don't filter them and darken them to zero. Draw them in a neutral dark palette with dark colours, so
  they don't distract but don't look like ghosts either. The second and third layers and beyond are dark blue." Before, the foreground was
  near black (`030303`, L 0.097) with a blue moon rim (L 0.64), the decor a grey slate or teal, and mid, grove and far all one near-black
  blue (L 0.03 to 0.27, hue 272), so the layers did not separate by depth.
- **Rule:**
  - **Near layers** (the foreground pieces of `fg.grid` and the near pieces, D113; the decor tier in the play layer: the `Decor` kinds,
    `HangingVine`, the canopy clump and the floor tufts and props of the terrain sheet) take a **neutral dark palette with real hues**:
    leaves a dark leaf green (hue 135 to 145), wood a bark brown (hue 55 to 65), moss a moss green, low in value (L 0.13 to 0.38) and in
    chroma (C 0.015 to 0.036). They are colours, not near-black silhouettes and not grey ghosts; they read as plants and wood and stay quiet.
    The foreground's moon rim is a moonlit grey-green (L 0.52, C 0.03), not the blue `7d8db0`.
  - **Scenery layers** turn **bluer, lighter and less saturated with depth** (atmospheric perspective toward the night sky): every key of
    a layer's ramp keeps its order, so the shapes, mist bands and darkening toward the foot stay as drawn.
  - **Unchanged:** the sky, stars, moon and clouds; the play layer's gameplay objects (terrain, traps, monsters, hero, pickups, mechanisms).
- **Targets (OKLab L / C / hue):**

  | Layer | Before | After |
  |---|---|---|
  | Foreground leaves (`a b k l m`) | L 0.07 to 0.32, C 0 to 0.04, neutral or blue | L 0.13 to 0.29, C 0.024 to 0.036, hue 130 to 150 |
  | Foreground wood (`trunk-near`, `roots-curtain`, `rise-trunk`: keys `A B K L M`, `G H I` fringe) | the leaf palette | L 0.13 to 0.29, C 0.020 to 0.030, hue 62 to 72 |
  | Foreground rim (`d e f`, `j`) | L 0.64 / 0.40 / 0.26, hue 267 | L 0.52 / 0.37 / 0.27, C 0.03, hue 155 to 167 |
  | Decor bush, crown, canopy clump | L 0.15 to 0.31, slate (hue 248) or teal (hue 169 to 196) | the same L, C 0.03, hue 145 |
  | Decor vine | L 0.30 to 0.35, teal-olive | the same L, C 0.03, hue 136; contour bark |
  | Decor trunk | L 0.16 to 0.38, slate (hue 275) | the same L, C 0.026, hue 62; moss hue 135; lit edge a lichen grey (C 0.016, hue 95) |
  | Look-alike mushroom | L 0.15 to 0.24, violet-grey | one step lighter, L 0.20 to 0.29: caps a mushroom grey-brown (hue 60), stems grey-beige (hue 85) |
  | Terrain-sheet props (#189) | contour `222034` (violet), sign ochre L 0.43 to 0.70, tufts teal-moss L 0.36 to 0.51 | contour bark L 0.275, sign a dark wood ramp L 0.25 to 0.41 (hue 64), tufts moss L 0.28 to 0.36 |
  | Mid (0.4) | L 0.03 to 0.20, C 0.04, hue 274 | L = 1.75 L + 0.045: 0.10 to 0.39, C 0.034, hue 250 |
  | Grove (0.25) | L 0.04 to 0.22, C 0.05, hue 275 | L = 1.80 L + 0.055: 0.12 to 0.45, C 0.030, hue 257 |
  | Far (0.15) | L 0.04 to 0.27, C 0.05, hue 272 | L = 1.30 L + 0.040: 0.10 to 0.39, C 0.026, hue 263 |

  At their tops the trunks and spruces now step mid 0.19, grove 0.24, far 0.26 (were 0.085, 0.10, 0.165): lighter with depth.
- **How it was made:** palette lines only, typed into the grids (`art/parallax/forest/{fg,mid,grove,far}.grid`,
  `art/objects/decor/*.grid`, the keys `u v w A B D E F H I J K` of `art/terrain/forest/terrain.grid`); the wood pieces of `fg.grid` swap
  their keys for the bark keys. No pixel, frame or size changed; exported with `tools/parallax_sprites.py`, `tools/terrain_sprites.py`,
  `tools/forest_trap_sprites.py` and installed with `tools/parallax_game.py`.
- **Measured (2026-11-21, `check_contrast.py`, every placement over the new layers):** passes. The lighter layers lower every active
  outline; the thinnest margins over the 0.28 floor: `Spider:body` 0.284 (0.285 before), `FallingPlatform:branch` 0.290 (unchanged),
  `ThornVine` 0.300 (was 0.346), `FallingStone` 0.302 (was 0.315). Decor: bush outline 0.120 to 0.135, trunk 0.077 to 0.133, crown 0.025
  to 0.168, hanging vine 0.099 to 0.100, look-alike 0.129 to 0.130 (body 0.130; it measured 0.076, under the 0.08 floor, over the new
  layers until it was lifted). Tile props, report only: sign outline 0.150, tile bush 0.197, dead tree 0.164 (body 0.183), all under the
  decor ceilings now (the sign was 0.223 body before). `parallax_mock.py --check --game`: passes, the same output as before (the cover
  rules read alpha, not colour).
- **Not changed:** no threshold or tolerance; the drawings and the foreground placements; the sky, the moon and the clouds.
## D121. The wall jump is removed: walls are solid and cannot be grabbed (#546, 2026-11-21)

- **Status:** Accepted (author, 2026-11-21, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21) direction, playtest 2 F12). Settles [D18](#d18-directional-controls-steppe-double-jump-and-replay-target)'s open question
  ("the already implemented wall jump's future role remains undecided"): it has none. Revises [D38](#d38-every-room-has-an-invisible-ceiling)'s reason and [D109](#d109-a-skills-first-meeting-has-a-pocket-in-view-and-calm-and-the-false-honey-fungus-has-a-trip-and-stand-pocket-500-499)'s skill example. Landed after [#545](https://github.com/jlogicgames/edgard_in_kimeria/issues/545) (PR 579), which gave `forest-walls` its way over by trees.
- **Decision:** a hero who presses into a wall in the air just falls; jumping beside a wall is an ordinary jump. A `Wall` collision block stays solid from the side and from above, as any block.
- **Code:** `Player` loses `clambering`, `wallJumping`, `wallJumpTimer`, the slide (vertical speed times 0.1), the wall-jump launch branch of `playerJump`, the clamber term of `tryJump` and the `CLIMBING` state with its clip. `CollideBody` loses `isClambering`/`setClambering`; its `Wall` branch was the same as the plain-block branch once the grab was gone, so it is merged into it. `CollisionBlock` loses `isWallClimbable` and `setWallClimbable` (and `Wall` its call for a lever wall), every enemy loses the unread `isClambering`/`setClambering` pair, and `YellowMob` its unused `wallJumping`. No per-frame work was added; the paths removed only shrink the frame.
- **Hero art:** the clamber frame is dropped from `art/hero/hero.grid` and `HERO_CLIPS` in `tools/sprite_layouts.py`; `tools/hero_sprites.py` regenerates `hero-right.png`, `hero-left.png` (13 rows, not 14, so every row after the attack moves up one: hit 5, appear 6, disappear 7, carry idle 8, carry run 9, throw 10, land 11, pull 12; `Player` `CLIPS` and the second-set row constants, `HeroFrameTest` and `SpriteSheetContractsTest` follow; `tools/check_contrast.py` crops carry idle at y 512 now, not 576).
- **Maps:** no map relies on climbing. `forest-walls` is crossed by its trees (#545); `RoomReplayTest` and `ForestWallsTest` no longer press jump on a wall, and the routes of every other room never did.
- **Registry (D105, D109):** the `wall-jump` row and `RoomCardsTest`'s `Geometry:wall` derivation (and its 96 px constant) are removed; the one skill left in the registry is `branch-climb` (`Geometry:branch`). The test asserts that `wall-jump` is gone and that `branch-climb` is registered (the old assertion that the wall-jump row exists existed only for it). `FirstMeetingPocketTest.skillPocket` is kept as the gate a skill's pocket needs (D109's rule is about skills, not about the wall jump): it now finds the room's wall or one-way branch at `geometryX` instead of a climbable wall. No shipped card uses it today, because the branch-climb was met in `forest-1-tree` and `forest-walls` records no first meeting.
- **D38 (the ceiling):** the slab was added because climbed walls led out of the room. It stays, unchanged: it still closes every room from above for a jump, a thrown stone and a ledge's head start, and `RoomCeilingTest` keeps every assertion, now naming the reason as a guard.
- **Tests:** assertions that existed only to prove "the hero never grabs a wall" are removed (`ForestWallsTest` its grab checks, `RoomReplayTest` the grab assertion and its jump-on-grab driver step, the `grabbed` flag of the leap scan); the routes and their measured times are unchanged. `JumpBufferTest`, `OneWayPlatformTest`, `Forest1CombatTest`, `TrapsLessonTest` and `RoomCeilingTest` lose the climbing words only; their sweeps (jumping against walls, the pit's lip, the tunnel block) are kept as they are. No threshold or tolerance changed. `WallContactTest` pins the new behaviour: a hero in the air pressing into forest-walls' first wall, with two jump presses, falls exactly as a hero with no input (same speed and height every frame, never pushed up).
- **Checked in the running game** by the author, 2026-11-24 ([#546](https://github.com/jlogicgames/edgard_in_kimeria/issues/546)): the feel of jumping beside a wall, desktop and web.

## D122. A branch always grows from a trunk ([#555](https://github.com/jlogicgames/edgard_in_kimeria/issues/555), 2026-11-21)

- **Status:** Accepted direction (author feedback, 2026-11-21: "All branches on all trees are not attached to the trunk. All branches,
  not only the hive."); the redrawn trunk, snag and collars await the author's review in the running game.
- **Context:** the play layer's trunk (`decor-trunk`, D80) was a 16 px frame with about 10 px of bark, while every branch box was
  placed at the trunk's x - 32 or x + 16, so each sound branch, rotten branch and hive bough hung 1 to 3 px clear of the bark, and the
  moon rim (D92) drew a pale line across the root at the frame's edge, which read as a cut end. Two falling branches (forest-1 x 3984
  and x 6000) had no tree at all (#555).
- **Rule:** every object drawn as a branch or hanging from a bough (`Platform` and `FallingPlatform` with `skin=branch`, `FallingBranch`,
  `HangingHive`, `Deadfall`) lays its root at least 3 px over the bark of a trunk kind on the root's side, below the trunk's cap. A
  trunk kind is a `Decor` kind drawn behind the play tiles (`behindTiles`): `trunk` and `snag`. A branch that stays shows a joint (a
  dark bark collar, its root column kept out of the moon rim); a branch that breaks off keeps its break at the trunk (the falling
  branch's cracked joint, its D71 cue; the rotten branch's end). *Revised by D130 (2026-11-24): a branch's root blends into the trunk
  bark, with no cut at the joint.* Trunks are decor in the D120 bark palette, never solid, and are drawn
  before the play tiles, so a ledge in front of a tree hides it. Gameplay boxes, triggers and timing are not changed by this rule.
- **As built:** `decor-trunk` 28×288 (bark 23 px, x - 3 to x + 19 of its 16 px object); a new kind `snag` (28×200, a dead tree snapped
  off 200 px up) for forest-1-mob, where a full trunk crossed the moon disc at the room's first camera (decor body 0.211, over the 0.20
  ceiling); new trunks: a snag at forest-1 x 3968 and a trunk at x 5984 under a crown; forest-0's trunk and crown moved 8 px left (x 1728,
  1688). Collars on `trap-perch`, `trap-hive` (rest to bare) and `trap-deadfall-bough` (its end grain removed); `RIM_KEEP` `J` for them.
- **Gate:** `tools/check_tiled_links.py` `check_branch_roots` (`BranchRootLinksTest`, shipped maps included; the root columns and the
  trunk's bark columns are typed in the tool from the grids).
- **Measured (2026-11-21, `check_contrast.py`):** passes, no threshold changed. Root over bark: 3 to 9 px for all 21 branch objects (was
  -9 to +3 px gap, two with no trunk). Active outlines at worst, before / after: hive 0.432 / 0.395, deadfall bough 0.452 / 0.430,
  sound branch 0.410 / 0.388 (left copy 0.454 / 0.413), rotten branch 0.290 (unchanged sprite). Decor: trunk outline 0.076 to 0.144,
  body at most 0.140 (was 0.074 to 0.133 / 0.142); snag outline 0.087 to 0.119, body at most 0.103; crown 0.026 to 0.165.
- **References:** [ART_DIRECTION.md](ART_DIRECTION.md) "A branch always grows from a trunk (D122)"; [TRAPS.md](TRAPS.md) falling branch,
  rotten branch, deadfall and hive cards.

## D123. A portal marks every room exit and a ripple marks the room change ([#552](https://github.com/jlogicgames/edgard_in_kimeria/issues/552), [#553](https://github.com/jlogicgames/edgard_in_kimeria/issues/553), 2026-11-22)

- **Status:** Accepted (author, 2026-11-21 and 2026-11-22: portal light only and drawn behind the hero; ripple pause 0.20 s). Playtest 2 (D116) finding F21:
  in a room whose exit is not at the screen edge it was unclear how to leave, because the `RoomExit` trigger drew nothing.
- **Portal:** every `RoomExit` carries one (`PortalFx`, one per `Level`, placed in `activateRoom` and in the constructor, so it is there on the room's first frame, with its streams filling in over about a second (rim and arms are full after one life of a mote, 0.6 to 1.3 s); the last
  room, which has no exit, has none). A violet upright oval (half height the exit's half height, kept between 20 and 28 px; half width 0.6 of it) whose rim sparkles, with
  three spiral arms of motes winding in, motes pulled in from round about, and a soft light on and in the rim. Behaviour ported from pixelfx's `arcane/portal` onto
  `KimeriaParticleSystem` (new `KIND_ORBIT`: a point riding an ellipse, fixed storage); **light only**, so the per-pixel dark well of the original is not drawn and
  the portal never darkens anything. It is drawn after the decor and before every object and the hero, so it never covers the hero, an enemy, a trap or a pickup; it is
  additive and carries no collision or timing (the exit stays the invisible `RoomExit` trigger, `Level.atRoomExit` unchanged). It never touches the parallax foreground, so
  `validateParallax` rule B is unaffected. D76: the portal draws no sprite and is measured by nothing in `check_contrast.py`; it is light on the active tier's side.
- **Ripple and pause:** when the hero reaches the exit the world holds for **0.20 s** (`RoomRippleFx.LEAVE_HOLD_SECONDS`, `RoomFade.start(hold)`), clear, with three violet rings
  one after another (the existing `shockwave.frag` through `RingPool`), 16 motes pulled into the hero and the existing `RippleEffect` screen distortion (0.6 s). The hero
  goes into the portal at the middle of the pause (`Level.heroLeft`, drawing only). Then the fade as before (0.25 s to black, the room switch, 0.25 s back), and in the new
  room the same ripple plays at the entrance with motes flying out. The pause ignores input as the fade always did; the hero's keys are polled state, so a direction held
  through the pause is still held when the new room starts (the game still calls `suppressJumpUntilRelease` on entry). Pause freezes it; a cancelled change (the healer meeting) clears it.
- **Relation to D3:** D3 makes *death* and retry instant and that is untouched: a retry, a restart and a reset play no ripple and no pause (`PortalPerExitTest`). The pause is
  on the *forward* room change only, a transition D3 never timed. It adds **0.20 s per forward room change** to the existing 0.50 s fade: 0.70 s in all, about 2.2 s over the
  11 exits of forest-1, 1.0 s over the 5 of forest-0 or forest-2.
- **Measured against D19 (`RoomReplayTest`, 2026-11-22, route to the exit, then plus the pause):** required routes 5.43 to 9.30 s, 5.63 to 9.50 s with the pause
  (forest-1 escalators 5.48 / 5.68, mob 6.75 / 6.95, traps 6.00 / 6.20; entry 6.83 / 7.03, walls 5.55 / 5.75, fissure 9.30 / 9.50, glade 8.85 / 9.05, canopy 9.48 / 9.68) against the 20 s budget;
  collection routes up to 12.05 s, 12.25 s with the pause (entry), against 30 s. Every route is now asserted with the pause added (`RoomReplayTest.within`, which prints the
  numbers); no limit was changed. The pause is not part of the 20 s of a room's replay on a retry (retry stays at the room start), it is the cost of leaving.
- **Measured:** `AllocationBudgetTest`: the portal 0.0, 0.0, 0.0 B/frame (3 runs), the room-change ripple 0.0, 0.0, 0.0 (one `RippleEffect` reused by `RoomRippleFx`, restarted at each leave and enter; 3.2 to 3.5 while each change created its own), ceilings 8; the
  forest rooms with their portal still pass their own ceilings unchanged. Pool peaks (`PortalFxTest`, 30 to 144 fps): 184 of 256 motes and 27 of 48 lights.
- **Level end (#605, 2026-11-24, extends D123):** playtest 3 found no marker for the end of a level. The same `PortalFx` takes a second, deep amber ramp (`7a3a10 b8611c e8892e ffc277 fff0d6`, redder than the honey `e2b25a` and the gold `ffd23a`; slow swirl, no flicker) and is placed on the final `Checkpoint` of a level without a boss (forest-0, forest-1) and, in the boss level, at the right end of the boss room on the hero's floor line once the boss is dead (`Level.placeVictoryPortal`). Cosmetic: `Checkpoint` and the victory walk are unchanged; still light only and behind the hero. Tests: `PortalFxTest`, `PortalPerExitTest`. The colour is the issue's recommendation; the author confirms it in the running game (warm white is the fallback if it reads as fire).
- **State and input (`PortalPerExitTest`):** a real room change (leave, fade, switch, fade-in), a retry and a restart end in the same room, kept pickups, inventory and hero with the effects as without; the hero is
  hidden only by a leave (`Level.leaving`, set by `beginLeaving` alone, reset by every room start), so the fade-in, a boss fight's fade and the debug fade leave him drawn; a direction held through the pause carries him on when
  the room starts, and a jump key held through it needs a fresh press (the exit reset). Coins are counted by `KimeriaGame` (`GameContext.coinsCollected`, a game-level counter the harness does not keep), not reachable from `Level`, so they are not in that comparison; the room change code does not touch them.
- **Licence:** `THIRD_PARTY_NOTICES.md` holds the pixelfx MIT licence and the Sprite Fusion credit; no pixelfx source is copied.
- **Not verified:** how the pause and the ripple feel and read in the running game, desktop and web; a new player finding the exits of `forest-1-bramble` and `forest-1-climb` without help;
  the frame rate with a portal on the web build ([#552](https://github.com/jlogicgames/edgard_in_kimeria/issues/552) and [#553](https://github.com/jlogicgames/edgard_in_kimeria/issues/553) keep those boxes open).
- **References:** [ART_DIRECTION.md](ART_DIRECTION.md) "Effects from pixelfx", D3, D19, D76, D116; review frames `docs/references/pixelfx/`.

## D124. The pickup halo and the fairy's glow become soft animated lights ([#544](https://github.com/jlogicgames/edgard_in_kimeria/issues/544), 2026-11-22)

- **Status:** Accepted (author, 2026-11-22: soft glows for pickups; for the fairy, brightened until every case clears 0.28). Amends **D76** (what the contrast gate measures) and **D82**
  (the fairy's glow and her own light). Playtest 2 finding F13: object highlights were too bright.
- **Decision:** `pickup-glow.png` and `fairy-glow.png` (and their grids and layout rows) are removed. A `Collectable` (not the coin) carries a `SoftGlowFx.PICKUP`; the fairy
  has `SoftGlowFx.FAIRY_SELF` for her own light and `FAIRY_TARGET` for the selected, passive and just-changed marks (up to 12 slots, one per target, each an oval hugging the
  target grown by 6 px). `SoftGlowFx` takes the look of pixelfx's orb and halo lights: a pool of soft light that breathes between 1 and 2 times its strength (two lights half a life apart),
  dots of light hugging the rim, specks drifting round it; additive, on `KIND_ORBIT`, fixed pools, a strength that dims a whole effect at once (selected 1, passive 0.3, changed 0.8
  fading, as before). The glow no longer bobs a 24 px disc; the strengths of D82 are unchanged.
- **Contrast (D76), measured by `check_contrast.py` against the two bounds of the effect (`SoftGlowFx.POOL_DIM` and `POOL_BRIGHT`, pinned against the real particle alphas by
  `SoftGlowFxTest`), old / new, no limit changed (0.28):**

  | Case | Old | New |
  | --- | --- | --- |
  | Bat wing jar, min / max outline | 0.353 / 0.420 | 0.432 / 0.504 |
  | Mushroom, min / max | 0.358 / 0.482 | 0.449 / 0.575 |
  | Coin (no light) | 0.702 / 0.891 | 0.702 / 0.891 |
  | Fairy body, worst frame | 0.308 (frame 0) | 0.292 (frame 0) |
  | Fairy glint big / small | 0.323 / 0.397 | 0.322 / 0.420 |

  The first soft version (her light 0.40 at 20 px, target light 0.22) fell below the floor on four fairy frames (0.262) and the small glint (0.232); her light was
  brightened to 0.6 at 28 px and the target's to 0.30 pool and 0.50 rim until every one of the 14 frames and both glints passed. Pickups improve because the soft light washes
  out less of their outline than the flat disc did.
- **A light that appears is running at once:** `SoftGlowFx.place` primes the pool (two lights at full alpha half a life apart) and every rim dot at its own age, so the first frame is inside the bounds the gate
  measures. Without it her own light was missing for about 1.2 s each time she appeared (on every room entry and retry), her contour fell to 0.269 to 0.278, and a new target or pickup started unlit, against D82's "full
  strength from its first frame" (`SoftGlowFxTest.aLightThatAppearsShowsAtFullStrengthOnItsFirstFrame` pins it; the gate's dim bound now holds from frame one). A `Collectable` places its light in its constructor, so pickups are lit from their first draw, through a new room's fade-in and the dark phase after a death.
- **Measured:** `AllocationBudgetTest`: pickups with the soft light 0.0, 0.1, 0.1 B/frame and the fairy's light and marks with a selected lever 0.1, 0.1, 0.1 (3 runs each, ceiling 8). Pool peaks (`SoftGlowFxTest`, three looks, 30 to 240 fps): 11 of 16 lights, 6 of 12 specks.
- **Not verified:** that pickups and the fairy are easy to find but not glaring in forest-0, forest-1 and forest-2 in the running game, desktop and web.
- **References:** [ART_DIRECTION.md](ART_DIRECTION.md) "Pickup cue", "The fairy helper", "Effects from pixelfx"; D76, D82, D123.

## D125. The spore puffball is redrawn as a crimson sac with particle spores and its own sound ([#558](https://github.com/jlogicgames/edgard_in_kimeria/issues/558), 2026-11-22)

- **Status:** Accepted by the author, 2026-11-22 (every recommendation of the proposal), from playtest F19 ([PLAYTEST_2.md](PLAYTEST_2.md)).
  Built; the look and the sound await the author's eye and ear in the running game.
- **Context:** in the second external playtest the "killer mushroom" did not read as dangerous. The author described a white cap with a yellow
  ooze that keeps pouring out and drawing back: the shipped puffball (a pale cap on a stem, drawn like the harmless fungi) and its three-frame
  sprite cloud that grew and shrank every 0.25 s. D71 asks a trap to be visible before it acts, D93 that its act is seen and heard.
- **Decision (gameplay unchanged):** the clock, the shared warning (`TrapWarnings`: 0.8 s, 1.2 s with the assist), the 20x20 lethal rectangle, the
  4 s period and every placement and phase (D58, D110) stay as they are. Only the look and the sound change:
  - **Look:** a crimson, pear-shaped sac of the danger family with a lime mouth and veins (the lethal hue shows at rest). 6 frames: two breaths
    (0.6 s each), two swell steps, the torn `burst` (first 0.25 s of the cloud), the empty `spent` (rest of the cloud and the first 0.4 s of the calm).
  - **Spores as particles:** `effects.PuffballFx`, two `KimeriaParticleSystem`s (96 and 24 slots, from the measured peaks of 71 spores and 14 puffs in 40 seeds at 30, 60 and 144 fps over 20 s each; no new kind): crisp `KIND_GRIT` squares and a
    `KIND_SMOKE` haze plus a `KIND_GLOW` flash. A leak of specks in the second half of the swell; at the release a flash, a jet from the chimney,
    40 spores over the whole rectangle and 8 puffs; spores and puffs kept up while lethal; all cleared the frame the hitbox ends. Every quad stays
    inside the lethal rectangle (D5). A room that opens inside a cloud shows it at once (no sound). The sprite sheet `trap-spores` is removed.
  - **Sound:** a new `puffball_burst` (0.45 s, RMS -21.5 dBFS): a tear, a rattling spray and a wheeze. `spore_puff` stays the honey fungi's shared
    sound (D63). The puffball is no look-alike, so it no longer shares it.
  - **Apart from the false honey fungus:** shape (a sac against a tuft), colour (crimson against sulphur), rest (breathes on a clock against waits),
    cloud (crisp pixel spores against a swirling shader haze) and sound.
- **Measured (2026-11-22):** `check_contrast.py` passes, no threshold changed: outline 0.655 to 0.686 (was 0.625 to 0.653). `AllocationBudgetTest`
  `puffballsLeakReleaseAndCloud`: 0.0, 0.3 and 0.3 B/frame in 3 isolated runs (ceiling 8); `Puffball.lethalTo` no longer builds a `Rectangle` per frame
  of a cloud (the box is built once). the 56 other generated sounds byte-identical.
- **Tests:** `PuffballFxTest` (containment on every frame at 30, 60 and 144 fps, the whole rectangle speckled on the first lethal frame, nothing
  outliving the hitbox, the leak only in the swell's second half, pool headroom, determinism, pause), `SporePuffSoundTest` (the puffball plays
  `puffball_burst`, never `spore_puff`), `SpriteSheetContractsTest` (6 frames).
- **Not verified:** the sprites, the spores and the sound in the running game, desktop and web; the author's audition ([#520](https://github.com/jlogicgames/edgard_in_kimeria/issues/520)).
- **References:** [TRAPS.md](TRAPS.md) (the card), [ART_DIRECTION.md](ART_DIRECTION.md), [SOUND_DIRECTION.md](SOUND_DIRECTION.md).

## D126. Up and Down interact, no two-button chords ([#548](https://github.com/jlogicgames/edgard_in_kimeria/issues/548), 2026-11-22)

- **Status:** Accepted (author, 2026-11-21, the author's own rule, playtest 2 finding F15, [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)); implemented. Reverses [D30](#d30-one-direction-for-pickup-and-mechanism-interaction) and the Action chords of [D18](#d18-directional-controls-steppe-double-jump-and-replay-target).
- **Decision:** Action is the sword and nothing else. **Up** picks up the nearest eligible carryable or uses the nearest eligible mechanism (D30's nearest-first selection and smaller-id tie-break are unchanged; while carrying, Up still reaches mechanisms). **Down** puts the carried object down (no effect empty-handed). Up and Down still climb ladders. No control needs two buttons.
- **Fresh press and hold:** interaction needs a fresh press, per device, latched like Action (`GameplayInput.upPressed()/downPressed()`; entry, resume and layout changes suppress a held key). Climbing stays on hold. A press that did something is spent: that held key does not also grab or climb a ladder until it is released and pressed again. Up and Down held together interact with nothing.
- **Touch stick:** the stick climbs as before, but counts as an interaction only past about 60 degrees from horizontal (`TouchControls.interactUpHeld()/interactDownHeld()`, tan 60 = 1.732), so a diagonal climb never picks anything up. The touch Action button always reads Sword.
- **Ladder overlap:** a stone or torch lying on a ladder: a fresh Up picks it up first and the next Up climbs. Down while carrying puts down on the floor, and at a ladder top it descends with the object (the ladder wins). Interaction does nothing while on a ladder.
- **Placement rule (every room, at load):** no hand-operated object (a `Trigger` not marked `throwActivated`, a `Stone`, a `CarryableTorch`) stands within 32 px (`Level.LADDER_CLEARANCE`) of a ladder zone, the union of its raised and lowered spans, so one Up never means two things. `Level.validateLadderClearance` throws at load; `tools/check_tiled_links.py` (`check_ladder_clearance`) fails the map; the test fixture `practice-test.tmx` moved its lever from x 448 to x 480.
- **Audit (2026-11-21, a script over the shipped maps):** `forest-0-ladder` (lever 120 px from the ladder zone), `forest-1-route` (80 px) and `forest-1-lever` (192 px) comply. `forest-1-guard`'s lever (id 63, x 2256) stood 16 px from the "Bridge" ladder (x 2288): moved to x 2240 (32 px, the floor lever's reach now x 2230.5-2265.5).
- **Text:** `ABOUT_BODY`, `ABOUT_BODY_LEFT_HANDED`, `ABOUT_GAMEPAD` and `ABOUT_GAMEPAD_LEFT_HANDED` (en approved by the author, translated for uk, pl, de, be; native review under #369) no longer name Action chords; the labels above the hero read "W: Pick up - Stone", "W: Turn on - Lever", "S: Put down - Stone". `NO_ACTION` and `DIRECTION_CONFLICT` are removed from the five catalogs and `Msg`; `ATTACK` stays (the touch button).
- **Measured (2026-11-22, headless, scripted hero):** the replays no longer swing the sword to pull a lever, and the D19 times are unchanged: `forest-1-escalators` 5.48 s (`RoomReplayTest`), `forest-1-traps` 6.00 s; the `forest-1-guard` roof route is 7.33 s with the lever 16 px farther left (7.4 s pinned, within the 0.1 s tolerance), melee 6.50 s, fireball 5.82 s, lure 4.53 s. Tests that hold Up now press it fresh after a released frame, and one fuzz test (`Forest1CombatTest` roof) no longer presses Up beside the lever, where a fresh Up pulls it.
- **Allocation (2026-11-22, `AllocationBudgetTest`, whole class, two runs each, B/frame; no ceiling changed):** the scenes are noisy and bimodal between runs (JIT), so the table gives both runs. A first version of the input code read `Gdx.input` more often per frame and the test's proxy input allocates per call (about 24 B), which showed as +48 B in every scene; the keys are now read once and the per-frame arrays are fields, so no scene is above origin/main.

| Scene | origin/main | this change | ceiling |
| --- | --- | --- | --- |
| forest-1 lever room riding the lift | 512 / 512 | 367 / 377 | 620 |
| forest-1 guard room | 520 / 520 | 232 / 232 | 1280 |
| forest-1 hive room | 512 / 512 | 251 / 247 | 590 |
| forest-1 escalators room | 512 / 512 | 320 / 320 | 620 |
| forest-1 route room | 512 / 512 | 320 / 320 | 1130 |
| forest-1 bramble room | 512 / 512 | 440 / 440 | 620 |
| forest-1 deadfall room | 512 / 512 | 320 / 440 | 620 |
| forest-1 mob room | 470 / 464 | 440 / 328 | 620 |
| forest-1 tree room | 448 / 448 | 448 / 328 | 990 |
| forest-1 lever room | 448 / 448 | 448 / 328 | 620 |
| forest-1 route room riding the lift | 448 / 448 | 448 / 448 | 1130 |
| forest-2 room 0 with fireflies | 480 / 496 | 472 / 360 | 625 |
| forest-2 fissure room | 448 / 448 | 448 / 448 | 590 |
| forest-2 room 2 with mob kill blood | 576 / 576 | 288 / 288 | 1250 |

- **Stone-throw scene** (`AllocationBudgetTest`, median of throw minus no-throw pairs, ceiling 16 B/frame, unchanged): 3.5 / 3.5 on origin/main, 4.7 / 4.7 here (an earlier origin/main run read 2.4). The difference is about 1 B/frame, from the two extra `isKeyJustPressed` reads per frame that the test's proxy input boxes and allocates (D126 reads them for the sub-frame tap rule); the game code allocates nothing new, and the scene stays far under its ceiling.
- **Checked in the running game** by the author, 2026-11-24 ([#548](https://github.com/jlogicgames/edgard_in_kimeria/issues/548)): pick up, use, put down and climb with Up and Down only, on keyboard in both layouts, gamepad and touch; a stone on a ladder; the feel of the 60 degree line and the pad and touch hysteresis (press at 60 degrees or a 0.4 pad axis, release below 55 degrees or 0.3) on real devices.
- **Hysteresis and taps:** an interaction edge needs a press at the line and a release below a lower one (touch 60 to 55 degrees, pad stick 0.4 to 0.3), so a resting finger cannot chatter a lever; a key tap shorter than a frame (the web) counts through `isKeyJustPressed`, like Action. Down at a ladder top descends even inside the short regrab pause after a step-off.
- **Gates:** `DirectionalActionsTest` (rewritten: fresh press, spent press, the ladder-overlap order, the touch angle, the placement rule), `LadderLessonTest`, `LeverLessonTest`, `StoneLessonTest`, `TrapsLessonTest`, `Forest1LeverLessonTest`, `Forest1RouteTest`, `FairyOffscreenTest`, `SideBarTouchLayoutTest`, `RoomReplayTest`, `AllocationBudgetTest`, `test_check_tiled_links.py`, `validateTextStyle`.
- **References:** [DIRECTIONAL_CONTROLS.md](DIRECTIONAL_CONTROLS.md), [GAMEPLAY.md](GAMEPLAY.md#controls), `.agents/skills/kimeria-input-flows/SKILL.md`, [PLAYTEST_2.md](PLAYTEST_2.md) F15.

## D127. The tutorial lever drives a lift, not the torch (amends D34; [#549](https://github.com/jlogicgames/edgard_in_kimeria/issues/549), 2026-11-22)

- **Status:** Accepted (author, 2026-11-21, playtest 2 finding F14); implemented. Amends [D34](#d34-the-tutorial-lever-lesson-rule).
- **Decision:** in `forest-0-lever` the lever (id 41, x 376) starts and stops a lift (`Escalator` id 40, x 432, y 256, 32 x 16, vertical, `offNeg` 3, `running` false) instead of lighting a torch. The lift hangs above the walking line (its deck bottom is y 272, the hero's top), so it rests, runs or stops part-way without ever touching the floor route: the exit (x 512) is never gated, after death, Restart Level and in every lift state (D26). A one-way step (id 142, x 400, y 304) and a two-piece ledge (ids 143, 144, x 472-536, y 160) give the curious player a ride: jump on the step, step onto the lift at its rest, ride to the ledge. The ledge carries nothing.
- **Cards:** `forest-0-lever` introduces lever, lever-platform and honey-fungus (tutorial rooms are exempt from the two-element cap, D72); `forest-0-platform` develops lever-platform and keeps falling-stone as its new element.
- **Text:** `HINT_LEVER_TORCH` "Use the lever to start the lift or stop it" and `HINT_LEVER_EXIT` "Lift moving or still, the way is open. Head for the exit" (D97 formula); the key names are kept. `HEALER_TALE_FAIRIES` still names the torch handle; the fairy's own line is `HEALER_TALE_FAIRY` ([#539](https://github.com/jlogicgames/edgard_in_kimeria/issues/539), D132).
- **Measured (2026-11-22, `LeverLessonTest`, scripted hero):** walk, pull, walk to the exit 4.25 s (about 4.3 s before; the lift sits above the floor route, so it adds no time). Not a novice playtest.
- **Not verified:** whether a new player sees the lift move and understands the ledge (the stopped-lift cue is the weakest, #418/#419). **Checked in the running game** by the author, 2026-11-24 ([#549](https://github.com/jlogicgames/edgard_in_kimeria/issues/549)): a pull, the lift moving, the hint, desktop and web.

## D128. The leg-hold trap replaces the forest Bomb ([#561](https://github.com/jlogicgames/edgard_in_kimeria/issues/561), 2026-11-22)

- **Status:** Accepted by the author, 2026-11-22 (through the orchestrating session): the card and its eight recommendations; then, after three redraws, **the hidden look** ("the trap approved": a real leg-hold lies on the ground under leaves), **the floor placement
  at x 496, Bat 43 with a shortened patrol, and the 6 px open / 14 px snap boxes** ("Yes to both"). From playtest 2 F27 ([D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)). Amends
  [D51](#d51-forest-3-renamed-forest-2-the-bomb-stays-a-separate-trap), whose "the forest Bomb stays" no longer holds. Built and tested with a scripted hero; the sounds heard and the trap checked in the running game by the author, 2026-11-24
  ([#561](https://github.com/jlogicgames/edgard_in_kimeria/issues/561)).
- **Decision:** the only forest `Bomb` (`forest-entry`, id 54, x 656) is replaced by a **leg-hold trap** (Tiled type `LegHold`, [TRAPS.md](TRAPS.md#leg-hold-trap-leghold)), a hunter's trap of hardwood jaws, bone teeth and crimson sinew lying flat
  under leaf litter. The exploding stumps and root (D47) stay. The `Bomb` is for the Fortress ([#116](https://github.com/jlogicgames/edgard_in_kimeria/issues/116)): its class, art, shader and tests are kept, and its tests run on the fixtures
  `bomb-test.tmx` and `bomb-rooms-test.tmx` since no forest map places one.
- **The trap: DISGUISED (D71)**, family touch. The rest frame is a 5 px mound of leaf litter; the **always-drawn cue**, the same on every instance, is two bone tooth tips through the litter, a sinew wrap at each hinge and a peg with its cord. The telegraph is the
  litter shivering and the jaws' rattle; the teaching room is `forest-entry`, whose card introduces `leg-hold`, and its first meeting is the trip-and-stand pocket at x 478 (D109): there the cue, the tell and the act (leaves burst, jaws spring up to 14 px, snap empty, shut, leaves settle) play on one object
  before any lethal choice. The litter kills on contact at once (a 24×6 px box, floor−6..floor, as drawn); entering the 24 px zone starts the shared D7 warning (0.8 s, 1.2 s with the assist, sampled once, not cancelled by leaving);
  then the jaws snap empty (0.1 s of a 24×14 px arch), stay shut and harmless for 2.0 s (and shut while a foot is on them) and re-arm. Answers, all free (D26): stand short of it, let it snap and walk over it shut; jump the 6 px litter; or snap it from afar with a thrown stone
  landing on it (`LegHoldProjectilesTest`: 22 throw positions hit, the stone is not consumed). **A fireball cannot snap it**: it flies at floor−19..floor−9 and passes over the flat litter (pinned by the same test); no answer needs ammunition, so that costs nothing.
  Monsters are not caught (the author's Q3: a later develop). It is a new element (`leg-hold`): its answer is not any earlier trap's. Consequence (D93): `leghold_snap`, the leaf burst and five chips, hit or miss.
- **Decisions of the build, each approved (2026-11-22) and with its reason:**
  1. *Placement: the floor at x 496-528, not the proposal's ledge (x 448-480).* The ledge (Platform id 122) is not on the required route (the route walks the floor under it, found when the scripted route died on the jaws as it passed under the ledge). The trap
     stands where the stair's jump lands (hero centre about x 480); the pocket is at **x 478**, inside the zone (from x 476) and 16.5 px clear of the litter (the body's edge to x 500), beat 3 of 4, retry 4.63 s (`RoomReplayTest.theLegHoldRetryIsAboutFourSeconds`).
  2. *Bat 43* moved from x 512 (patrol x 496-608, 16 px from the approved jaws: the 96 px first-meeting clearance fails) to **x 672 with `offNeg` 1 (patrol x 656-688)**. The first try (patrol x 624-688) died at the pit-2 landing when the mastered route started 0.8 s late
     (measured by `RoomReplayTest.theExitIsReachedSafelyPastBat43AtEveryBatPhase`); with x 656-688 the route, started 0 to 4 s late in 0.2 s steps, is never hit and the Bat's patrol stays 16 px short of the exit. (Every offset runs; at 2.6 to 4.0 s the scripted hero lands on the ledge above the trap, which cannot see him there, so the script waits for the snap only on the floor and walks off the ledge's end.)
  3. *Boxes 6 px open, 14 px snap.* The open box is the litter as drawn (24×6 px); the snap arch is the approved floor−14..floor. The sprite is 32×16 to draw the 14 px jaws.
- **Rooms:** `forest-entry` introduces `yellowmob` and `leg-hold` (two, D72), `Develops` is empty (the Bomb's develop of `touch-explosive` is gone); beats `yellowmob@192, bat-enemy@352, leg-hold@496, bat-enemy@672`.
  **Mastered route 6.83 s to 8.37 s** (the hero waits about 1.0 s for the snap and fights the Bat at the exit): the `RoomReplayTest` pin `ENTRY_S` and the card's `Beat time` changed with it, measured, not loosened (three identical attempts; all coins 12.02 s,
  collection return 7.00 s); 2.09 s per beat, inside the 1.5 to 4.0 s band. `ENTRY_JUMPS` is unchanged. `FirstMeetingPocketTest.forestEntryTheLegHold` pins the pocket and the trip-and-stand condition (the zone reaches 24 px past the litter);
  `aBombFailsTheTripAndStandCondition` now measures the Bomb on `bomb-test.tmx`.
- **Allocation (`AllocationBudgetTest.legHoldsRattleAndSnap`, four leg-holds cycling, 3 isolated runs):** 0.0, 0.0 and 0.0 B/frame against the ceiling 8, unchanged.
- **Contrast (`validateContrast`):** `LegHold` 0.412 at worst at the placed trap over the night, with the moon rim (the litter's lit edge), and the cue's two bone tooth tips on their own 0.608 and 0.601 (`LegHold:tip`, `LegHold:tip2`). It was 0.537 for the earlier open-jaw art and 0.077 without the rim.
- **Checked in the running game** by the author, 2026-11-24 ([#561](https://github.com/jlogicgames/edgard_in_kimeria/issues/561)): the look, the feel and the sounds on a real output. **Not done:** the Fortress placement of the Bomb ([#116](https://github.com/jlogicgames/edgard_in_kimeria/issues/116)).

## D129. The lance root replaces the forest's ember fissure ([#562](https://github.com/jlogicgames/edgard_in_kimeria/issues/562), 2026-11-22)

- **Status:** Accepted by the author, 2026-11-22 (the lance root, candidate A, with the proposal's recommendations), from playtest 2 F28 ([D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)).
  Amends [D91](#d91-the-ember-fissure-a-clocked-fire-jet-from-a-floor-crack-taught-in-forest-2-366-2026-11-17). Built and tested with a scripted hero; the sounds heard and the look checked in the running game by the author, 2026-11-24 ([#562](https://github.com/jlogicgames/edgard_in_kimeria/issues/562)).
- **Decision:** both `EmberFissure` objects of `forest-fissure` (ids 151, 152) became a **lance root** (`LanceRoot`, [TRAPS.md](TRAPS.md#lance-root-lanceroot)): a bark burl with crimson heartwood on the floor line that,
  on the clock, lifts its lips, then lances a sharpened root 96 px straight up and sinks it. The fire jet does not fit the forest (F28); it returns later as a Swamp geyser
  ([#105](https://github.com/jlogicgames/edgard_in_kimeria/issues/105), [#399](https://github.com/jlogicgames/edgard_in_kimeria/issues/399)).
- **Same machine:** D91's rhythm, warning, lethal column, counters, load rules and beats are unchanged (period 3.5 s, jet 1.0 s, 8 px × 96 px, phases 0 and 1.75, `period >= jet + 1.2 + 0.8`). In code `LanceRoot extends EmberFissure`
  (a protected constructor takes the sheets, two spark hooks differ): the clock and the column are one implementation, and `LanceRootTest` pins step for step that the two have the same phases, frames and column
  at both assist values. The map's types, the sounds, the particles and the art differ. `EmberFissure` keeps its code, art (`trap-fissure`, `trap-ember-jet`), sounds (`fissure_rumble`, `ember_jet`) and tests, and `fissure-test.tmx`.
- **One element (the author's Q5):** `clocked-column` in the registry for both types (`LanceRoot`, `EmberFissure`; was `ember-fissure`), a reskin by the rule: the answer is the same. `forest-fissure` introduces it;
  its card keeps the same beats (`clocked-column@3232, clocked-column@3360, beetle@3508`), beat time 9.30 s and pocket at x 3200; the map and the tests changed by the object type only. The `EmberFissure` catalogue row
  keeps `forest-fissure` as its teaching room (the room shows its element) and says no Forest room places it since this decision.
- **Look:** `trap-lance-root` 16×12 × 6 and `trap-lance-shaft` 8×16 × 4 (the column's tile and a bone tip). The column is **pale bleached wood round crimson heartwood with dark bark rings, a bone tip**: an ink outline vanished on the night
  (0.069), and the pale edge carries it. Contrast at the placed roots: burl 0.449, shaft 0.529, tip 0.482 at worst (the burl has the moon rim; the repeating tile has none).
  Particles: soil specks hop from the lips in the warning, six bark chips fall as the root sinks (`KIND_GRIT`), every thrust, hit or miss (D93). Sounds: `root_creak` (warning), `root_thrust` (the thrust), voice-synth, seeds 7403 and 7404.
- **Allocation (`AllocationBudgetTest.lanceRootsCycle`, four roots, 3 isolated runs):** 1.1, 1.1 and 1.2 B/frame against the ceiling 8, unchanged (the ember fissure's own figure is 1.1).

## D130. Decor trees and bushes are solid and calm, and a trunk is apart from the ground (amends D76 and D120, [#555](https://github.com/jlogicgames/edgard_in_kimeria/issues/555), 2026-11-24)

- **Related:** [#596](https://github.com/jlogicgames/edgard_in_kimeria/issues/596) covers the same trunk and soil gap.

- **Status:** Accepted direction (author, 2026-11-21, playtest 2 F17: "trees and bushes are drawn properly, opaque and clear, a bit
  darker than the active elements; everything important is bright"); the red-brown bark and the measured bounds await the author's
  review in the running game (#555 keeps that box open). Amends [D76](#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16)
  (one new decor limit, every old limit unchanged) and [D120](#d120-depth-palette-the-near-layers-take-dark-real-colours-the-scenery-turns-bluer-lighter-and-greyer-with-depth-236-2026-11-21)
  (the play layer's trunk hue).
- **What was already done by D120 and D122:** the decor sheets are opaque (binary alpha, no enclosed hole in `decor-trunk`,
  `decor-snag`, `decor-crown`, `decor-bush`) and drawn in dark real colours, not grey ghosts (D120); every branch, falling branch,
  hive and deadfall bough grows from a brown trunk or snag (D122, `check_branch_roots`, 3 to 9 px of root over the bark). Those trunks
  are the "brown copies of the background trees" the issue asked for: a tree in the play plane, brown, under each branch. Literal
  copies of the parallax trees were not made: D120 keeps the scenery bluer with depth, and a second brown tree kind would only repeat
  the trunk. The falling platforms are all the rotten log since PR 590 (the author: "replace all flying logs with the rotten log over
  the pit, with its behaviour"); the log has no tree by that choice, and no support is added for it.
- **Decision:** the D76 decor limits stay (outline at most 0.20, body 0.08 to 0.20, p90 chroma at most 0.10): measured on
  `forest-1-tree` and every other placement they keep the decor a step under every active sprite (active at least 0.28). One limit is
  added: a **trunk kind** (a `Decor` kind drawn behind the play tiles, D122: trunk, snag) keeps a mean OKLab distance of at least
  **0.02** (one just-noticeable step, the unit of D76's reasons) from the nearest terrain colour (every colour of
  `art/terrain/forest/terrain.grid` but its decor-tier keys `A B D E F H I J K u v w`).
- **Why:** the D120 bark used the soil's own browns (bark `302419` against soil `2f2117`, `251a10` against `22180f`): mean 0.012 from the
  nearest terrain colour, so a trunk standing on the floor read as a pillar of earth, and the terrain sheet does have one-tile pillars
  and rock walls (#547). A lighter or darker bark alone cannot leave the soil, whose ramp covers L 0.12 to 0.48; a hue step can.
- **Recolour (palette lines of `art/objects/decor/trunk.grid` and `snag.grid` only; every pixel, frame and size unchanged):** the
  bark keys `k f d m r l` become a red-brown pine bark, hue 35 (was 62), C 0.036 (was 0.026), each key 0.01 lighter: `1c0805`,
  `220e09`, `28130e`, `2e1813`, `39231d`, `47302a` (were `140a03`, `1a0f06`, `20150b`, `251a10`, `302419`, `3d3126`). The moss `g` and the
  lichen lit edge `h` are unchanged. It stays brown and in D120's chroma band, and it sits nearer the active branches' collar
  (`4a2e1c`, hue 53) than the soil does. The other decor (bush, crown, vine, look-alike) is not a column and is not changed.
- **Measured (2026-11-24, `check_contrast.py`, worst placement; before / after):** from the terrain colours, trunk 0.012 / 0.026,
  snag 0.012 / 0.026. Trunk outline 0.076-0.144 / 0.074-0.142, body at most 0.140 / 0.142; snag outline 0.079-0.119 / 0.080-0.120,
  body at most 0.103 / 0.114. Bush 0.120 to 0.135 (body 0.134), crown 0.026 to 0.165 (body 0.159), vine 0.099 to 0.100, look-alike
  0.129 to 0.130, unchanged. Report only (not trunk kinds): vine 0.015 and look-alike 0.014 from the terrain colours, bush 0.033,
  crown 0.033.
- **Gate:** `tools/check_contrast.py` `check_trunk_soil` (in `validateContrast`; the trunk kinds are read from `Decor.java` through
  `check_tiled_links.decor_trunk_kinds`), `tools/test_check_contrast.py` `TrunkSoilTest` (the old bark fails, the new bark passes,
  the shipped trunks pass); `DecorTest.theTreesAndBushesAreSolidWithNoTranslucentPixelAndNoHole` pins the opaque trunk, snag, crown
  and bush (alpha 0 or 255, no enclosed hole, every bush frame). No threshold or tolerance was loosened.
- **A branch's root blends into the trunk bark (revises D122's joint; the author, 2026-11-24, on the rotten branch: "a completely
  different colour and an obvious cut. Make the colour near the edge change gradually into the trunk's colour"):** on every branch
  that hangs on a trunk, the last 5 to 8 px at the root ramp row by row, with staggered steps, from the branch's own colours through
  a half step (an OKLab midpoint typed per colour) into the trunk's bark (`47302a`, `2e1813`, `28130e`, `220e09`), and flare a row up
  and down into a bark collar; the outermost column is the trunk's furrow `220e09`, the pixel the trunk draws under it. Hand-typed in
  `rotten-perch.grid` (intact, crumble1, crumble2; columns 0-6), `branch.grid` (resting, rustle, sag; columns 0-5, the D71 crack
  band at 6-11 untouched; `falling` keeps its broken end), `perch.grid`, `hive.grid` (rest to bare) and `deadfall-bough.grid`
  (columns 104-111); the `-left` frames are flips and the deadfall is mirrored in code, so both sides match. The old collar key `J`
  (`4a2e1c`) is gone; `RIM_KEEP` keeps the ramp keys. The rotten branch keeps its rot and split along its length, none at the joint.
  Boxes, triggers and timing are unchanged; `check_branch_roots` passes as before (3 to 9 px over the bark). Active outline at worst,
  before / after: rotten branch 0.290 / 0.281 (its rimmed end at the frame's edge was its brightest edge; the furrow-coloured edge
  column is what keeps it over the 0.28 floor, a lighter bark edge measured 0.265), falling branch 0.492 / 0.375, hive 0.395 / 0.324,
  sound branch 0.388 / 0.391 (left 0.413 / 0.380), deadfall bough 0.430 / 0.411. No threshold changed.
- **Checked in the running game** by the author, 2026-11-24, desktop and web: trees and bushes solid and calm, important objects brighter, the
  branch and the log not hung in the air (#555).
## D131. Two room touch-ups from playtest 2: the stone lip hangs a third lower in forest-0-platform, and forest-1-lever says what its lever moves ([#550](https://github.com/jlogicgames/edgard_in_kimeria/issues/550), [#554](https://github.com/jlogicgames/edgard_in_kimeria/issues/554), 2026-11-24)

- **Status:** Accepted by the author, 2026-11-24, from playtest 2 F18 and F22 ([D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)); the before and after frames are `docs/references/rooms/550-forest-0-platform-*.png` and `554-forest-1-lever-hint.png` (composed renders, not screenshots). Built and checked with a scripted hero and the gates; the look checked in the running game by the author, 2026-11-24.
- **#550 (F18):** `FallingStone` 79 gets `lipTop` 128 (screen y 120, one third of the 360 px view; was the default line y 8, the camera's top edge). The lip hangs under a soil block (tiles 42, 9, 44 over 62-64, x 2528-2576, y 96-128, drawn only: the stone falls through its column) under a canopy lobe (rows 0-5); the two side canopy clumps grow to y 80. Vines 97, 98, 99 hang from y 80 at x 2476, 2614, 2658 (were y 0 at x 2476, 2616, 2680); two collision slabs (ids 183, 184: x 2448 and x 2592, y 0, h 80) are their anchors. The x moves keep the D76 decor body band 0.08 to 0.20 (the old x at y 80 measured 0.073 and 0.076); the band is not changed. **Thin margins:** at y 80 the vines at x 2476 and x 2614 measure 0.083 and 0.082 against the 0.08 floor (x 2658: 0.108); the gate is deterministic, but a later change to the forest-0 backdrop can tip them (re-measured after the merge of #603: unchanged). The stone lip at y 128 measures outline 0.544, body 0.348 to 0.389. `tools/parallax_game.py`, the installer the map gate demands, renumbers the layer ids of `forest-0.tmx` and `forest-1.tmx` (every scenery and foreground layer, `nextlayerid`, `nextobjectid`), so those two diffs are larger than the change; a second run changes nothing. The stone body, zone, landing (y 320), shadow, fall timing and the D19 time (11.85 s) are unchanged.
- **Pins changed with the map (AGENTS.md: never silently):** `RoomCeilingTest` forest-0's lip y 8 becomes 128; `HangingVineTest` no longer demands a vine's top edge on the room's ceiling line, but on the ceiling line or the underside of a solid block (the rule `check_tiled_links.py` already had, so the vine decision of #239 step 5 is widened to match).
- **#554 (F22):** `forest-1` gets a `Hints` layer with one zone over `forest-1-lever` (x 4320, w 640, whole height), message `HINT_LEVER_BOTH` "Use the lever to move the ladder and the platform" (49 characters, D97 lever form, "platform" per the glossary; the same verb "move" in uk, pl, de, be). The author first approved "Use the lever to lower the ladder and stop the platform" and then replaced it: that text is true only before the first pull, while a zone message must be true in every state of the room (D33, D48), and "move" holds with the ladder up or down and the platform running or stopped, no `controls`. This is an exception to D33's "a level with no `Hints` layer shows none", for this room only; `forest-1-route` stays silent, because it tests the lesson (D67, D86).



## D132. Things fly to where they go: one flight helper for the pickup, the hand-over, the elixir and the fairy ([#539](https://github.com/jlogicgames/edgard_in_kimeria/issues/539), [#540](https://github.com/jlogicgames/edgard_in_kimeria/issues/540), [#541](https://github.com/jlogicgames/edgard_in_kimeria/issues/541), [#557](https://github.com/jlogicgames/edgard_in_kimeria/issues/557), 2026-11-24)

- **Status:** Accepted by the author, 2026-11-24 (the flights proposal, with every recommendation; the proposal file was deleted when this landed, its content is this entry), from playtest 2 F11, F25 and F29 ([D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)). Built and tested headless; the look, the timing and the two new sounds checked in the running game and heard by the author, 2026-11-24.
- **Decision:** one cosmetic helper, `effects.FlightFx`, flies a cached sprite from A to B in screen space (a quadratic Bezier bowed up, an ease, whole pixels, a fade near the end, a `KIND_FLAME` trail and an optional landing twinkle). Fixed 12 slots and 48 trail sparks, no allocation. It reads no state, takes no input and nothing waits for it. It advances only in `update` (a pause freezes it), with real dt clamped to 1/20 s (never the assist's time scale), and is cleared on death, retry, level load, a room change and when the screen closes.
- **#557 pickup:** after the unchanged state change, `Collectable` calls `GameContext.startPickupFlight`; `KimeriaGame` projects the item's world point to the screen and starts a 0.12 s pop (rise 6 px, 16 to 20 px) then a 0.40 s ease-in flight (arc 24 px, 20 to 10 px, fading over the last 35%) to `Hud.slotCenter(type)`: the coin icon or the newest inventory slot. It lands with a four-spark twinkle; no new sound.
- **#540 icons:** each checklist row is the item's HUD icon (20 px, no new art) and the old `[+]`/`[-]` text; a missing item's icon is drawn at 0.25 brightness. `CHECKLIST_Y` is 16 and rows are 21 px apart, so the rows end at y 79, 5 px above the choices. Widest row in five languages: 128.0 of 206.0 px beside the icon.
- **#541 hand-over:** when the paid dialogue opens (after `brewElixir()` has settled everything) the three icons wait in a column at the left of the middle column (the message wraps to their right, so they never overlap it in any language) and take off at 0.5, 0.7 and 0.9 s (bat, mushroom, one coin for the coins), 0.6 s each to the healer's portrait; the elixir (the boss-HUD flask at 2x) leaves the healer at 1.7 s for 0.8 s to Edgard, and a soft green light breathes on his portrait for 0.6 s. New sounds `handover` (three arrivals) and `elixir`. With instant text there is no flight, only one `elixir` sound.
- **#539 fairy:** the start tale shows the fairy on **`HEALER_TALE_FAIRY`**, not the issue's `HEALER_TALE_FAIRIES` (that line speaks of the old handles; she is introduced on the fairy line): she twinkles in beside the healer's portrait (0.4 s), flies 0.9 s to Edgard's window and hovers beside his head with a bob, at 2x with her own light, until the dialogue closes. Her path and hover stay at least 8 px clear of the thread's rectangle (`HealerFlightsTest`). With instant text she is at the hover spot from the first frame.
- **Sounds:** `handover.wav` (0.12 s, seed 4801, -19.4 RMS / -13.9 peak) and `elixir.wav` (0.58 s, seeds 4901 and 4902, -19.0 / -11.7); every other sound is byte-identical.
- **Allocation:** `AllocationBudgetTest.pickupFlights` 0.0, 0.0 and 0.0 B/frame; `dialogueFlightsAndTheFairy` (the fairy) 0.4, 0.4 and 0.4 B/frame (0.0 in a reviewer's run) and `paidScreenHandOver` (a real paid dialogue, a new one every 300 frames, the event included) 2.7, 2.5 and 2.5; the dialogue screen scenarios still 0.4, 0.1 and 0.0 against the ceiling 8 (3 runs each, 2026-11-24).
- **References:** [HEALER.md](HEALER.md), [ART_DIRECTION.md](ART_DIRECTION.md) ("Dialogue screen (D78)", "Pickup burst"), [DEVELOPMENT.md](DEVELOPMENT.md) (particles), [SOUND_DIRECTION.md](SOUND_DIRECTION.md), D78, D82, D116, D124.



## D133. The opening is a tale on an open book ([#535](https://github.com/jlogicgames/edgard_in_kimeria/issues/535), [#536](https://github.com/jlogicgames/edgard_in_kimeria/issues/536), amends D77, 2026-11-24)

- **Status:** Accepted by the author, 2026-11-24, on the proposal of the same day with every recommendation: the English text as written, the fairy as the third gift, palette A (warm parchment), the ending stays black, 20 px text on the book. Playtest 2 F5 and F6 ([PLAYTEST_2.md](PLAYTEST_2.md), [D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)). The uk, pl, de and be leaves are machine-quality drafts with each language's own tale formulae, for native review in [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369).
- **Text:** `INTRO_STORY` becomes six keys, `INTRO_LEAF_1` to `INTRO_LEAF_6`, two paragraphs each, in all five catalogs. The healer tells it as a tale with a frame that matches the ending's ("Hear my tale of Kimeria ... the tale is yet to come"): "Once upon a time", "fair beyond telling in a tale or writing with a pen", word that flew far and wide, "whether his road was long or short", "a tale is soon told, but a deed is not soon done"; threes: the three things the curse takes, "no king and no prince, only a man with a sword", the three gifts. Every fact of [STORY.md](STORY.md) is kept; the one addition is the fairy as the third gift (D82). The breaks between leaves are the author's, never the font's; the limit per leaf is three paragraphs and 300 EN characters ([TEXT_STYLE.md](TEXT_STYLE.md) section 3). EN is 1334 characters, about 1.5 minutes of reading (the shipped opening was 790). The optional cuts of the proposal were not made.
- **Book:** an open book fills the screen (cover (16, 10)-(624, 326), two leaves of 290 x 298 px, spine at x 320), drawn from `art/ui/intro-book/*.grid` and composed by `tools/intro_book_sprites.py` into `assets/images/ui/intro-book.png` (640 x 360, opaque) and `intro-leaves.png` (the two leaves, for the leaf in flight). Palette A: walnut and gilt as the dialogue screen (D78), parchment `b8a47c`, a silver-cord bookmark (the healer's thread, #228). Look and rules: [ART_DIRECTION.md](ART_DIRECTION.md), "Story book". Contract: opaque, at most 256 palette entries (`validateSpriteArt`).
- **Text on the book:** the story font is 20 px (`FontSizes.STORY`, a size already baked, so no rebake: the 16 px body filled half a leaf), dark ink `2a1a10`, left-aligned in a 236 px column at x 56 (left leaf) and 348 (right leaf), paragraphs 20 px apart, first line 40 px from the screen top and nothing below 304 (`IntroPages.TEXT_TOP` and `PAGE_BOTTOM` unchanged). Ink on the parchment colours a column sits on is at least 4.5:1 (`test_intro_book_sprites.py`; 6.9:1 on the main leaf colour).
- **Reading:** a page is a leaf, two leaves are a spread (`IntroPages.ofLeaves`, `IntroScreen.openBook`). The left leaf's paragraphs fade in as before (0.9 s each, 1.6 s apart); a press shows the rest, the next press moves to the right leaf of the spread (no fade, no flight; the left text stays); a press on the right leaf, once whole, turns the spread: both leaves' text fades over 0.4 s, then the right leaf flies over the spine for 0.4 s (`FLIP_SECONDS`, `IntroBook.draw`) and the next spread's left leaf starts. The last leaf fades to black. A press is ignored during the turn.
- **Unchanged:** the skip hint and every skip key and button (D118, D77: Esc, Back, B, Start, the touch button at (606, 22), which lies over the cover's top-right corner and keeps its white outline); fresh-press paging and the 0.6 s input lock after a leaf starts and after it is whole (`IntroInput`); Instant text (a leaf appears whole, turns at once, no fade and no flight); the page-turn cue at (320, 330) under the book; the ending (black, the arms over its text, D95). A language that ever had an odd number of leaves would leave the last right leaf blank; leaves are fixed per key, so there are six everywhere.
- **Measured:** every leaf fits in all five languages (`IntroPagesTest.everyLeafFitsItsPageInEveryLanguageAndKeepsTheTaleLimits`); the longest, German leaf 5, ends at 240 px of the 304 px bottom (the proposal's long drafts measured the same). Allocation (`AllocationBudgetTest.introBook`, 240 leaves, the skip hint queued every frame, the touch skip button polled and drawn, the harness's input proxy of 120 B/frame subtracted as in D77): 0.0 B/frame with instant text off (175 frames with a leaf in flight) and on, against the ceiling 8; `pagedIntro` (the ending's greedy path) 0.0 B/frame. No threshold or tolerance changed.
- **Not verified:** native-speaker review of the four translations ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369)); **Checked in the running game** by the author, 2026-11-24 ([#535](https://github.com/jlogicgames/edgard_in_kimeria/issues/535) and [#536](https://github.com/jlogicgames/edgard_in_kimeria/issues/536)): the look and the paging on desktop, web and a real phone; that the touch skip button reads over the book's corner on a real device.


## D134. The portrait band is even on all four sides; the pre-boss and victory lines get the tale voice (amends D78, D74; [#537](https://github.com/jlogicgames/edgard_in_kimeria/issues/537), [#538](https://github.com/jlogicgames/edgard_in_kimeria/issues/538), 2026-11-24)

- **Status:** Accepted by the author, 2026-11-24 (both proposals as drafted, every line), from playtest 2 F7 and F8 ([D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)). Tested with scripted checks; the look and the text checked in the running game by the author, 2026-11-24 ([#537](https://github.com/jlogicgames/edgard_in_kimeria/issues/537), [#538](https://github.com/jlogicgames/edgard_in_kimeria/issues/538)); a native-speaker review of the uk, pl, de and be lines ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369)) stays open.
- **Band:** the window's right and bottom sides carried 3 px of near-black that merged with the page, so the visible band measured 7, 7, 5, 5 px (left, top, right, bottom). The outer 2 px inside the outline of those sides are now shadow walnut `523420` (dim `291a10`); the band is 7 px on every side, the bevel and the contrast tiers are unchanged. Same outer size, opening and insets. The test counts the visible band on all four sides (equal within 1 px).
  A plain mirror (a light rim on all sides) was rejected: it also measures 7 px but flattens the bevel.
- **Text:** the fourteen lines of the pre-boss meetings (greeting, return, what is missing, the offer, ready, paid, lock) and the victory scene are rewritten in tale voice in all five languages ("my price" for the pay, "the wood still hides", "where this forest begins", "the old wood breathes again", "your tale is not over"). Each keeps its job and its glossary forms; each fits two lines of the 18 px box.
  No allowlist entry. The choice buttons and `HEALER_FIGHT` ("Enter the boss fight") are unchanged; whether it should name the forest guardian like the HUD label (#542) is open.


## D135. About describes the game and Options gets a Controls screen ([#531](https://github.com/jlogicgames/edgard_in_kimeria/issues/531), [#532](https://github.com/jlogicgames/edgard_in_kimeria/issues/532), 2026-11-24)

- **Status:** Accepted by the author, 2026-11-24 (the proposal's recommendations), from playtest 2 F1, F2 and F4 ([D116](#d116-second-external-playtest-findings-and-direction-563-2026-11-21)).
  Amends [D9](#d9-left-handed-mode-swaps-the-key-bindings-and-mirrors-touch) and the placement of #35 (the controls were About's first page). Built and tested headless; checked in the running game by the author, 2026-11-24, on a pad and on a touch screen.
- **About:** two pages switched by one button. **The game** opens first: three short paragraphs (`ABOUT_GAME_STORY`, `ABOUT_GAME_PLAY`, `ABOUT_GAME_MORE`), the store-style description the tester expected (what the game is, who Edgard is, what the player does). **Game rules** shows `ABOUT_RULES` and `ABOUT_RULES_CARRY` (the carry and throw rule, moved from the retired control text). The button is `Game rules` / `The game` (`ABOUT_GAME`); About opens on the game page each time. `ABOUT_BODY`, `ABOUT_BODY_LEFT_HANDED`, `ABOUT_GAMEPAD` and `ABOUT_GAMEPAD_LEFT_HANDED` are removed from the five catalogs.
- **Names in the blurb:** the Plain voice forbids character names ([TEXT_STYLE.md](TEXT_STYLE.md) section 1); `ABOUT_GAME_*` alone may name Edgard, Kimeria and L'Kardina, because a store description cannot say who the hero is without them. The gate does not check Plain strings for names, so no gate changed. The blurb says "throw fire": the glossary lists "fireballs" under Avoid for the ranged magic, whose label is Fireball.
- **Controls:** Options > Controls (between Display and Accessibility). The screen has the title, a table and two buttons, `Left-handed: Off|On` (selected on open) and `Back`; **the Left-handed switch moved here from Options**, so Options has no second switch for one setting. The table has an action column and two hand columns, **Right hand** and **Left hand**, always both visible: the active hand is gold on a lit panel and the other grey, so toggling swaps the highlight (F4). The device follows touch first, then a connected pad, else the keyboard (`ControlsView.modeFor`, as the intro's skip hint, D118). Keyboard and pad show eight rows (Move, Climb, Pick up / use, Put down, Jump, Sword, Throw / Fireball, Pause), the D126 bindings, with the pad's mirrored layout of D9 (right hand: Pick up / use and Put down on the left stick or D-pad up and down, as the code reads them); touch shows two mini screens (stick left and three buttons right, and the mirror) and one line about the stick. The touch preview is **schematic**: it shows the layout order (Sword, Fireball, Jump from top to bottom; the stick on the other side for the left hand) with the jump disc and the live ranged label, not the real button art or positions, and no Pause button (the pause button is not a layout choice). Letters, `Esc` and `Start` are not translated; the words around them are.
- **Code:** `ui.ControlsView` (rows, layout, drawing) and `Overlay.textBlock` / `bodyLines`; `KimeriaGame` state `CONTROLS`. The text is laid out once per mode, language and hand into cached blocks, so a frame allocates next to nothing in the view. Measured with `AllocationProbe` (ThreadMXBean, 1200 frames after 300 warm-up, `NoopBatch`, view draw plus text flush): 0.00 to 0.27 B/frame across keyboard, pad and touch in both hands; `ControlsViewTest` fails above 0.5 B/frame (about twice the worst reading). The menu frame around it still allocates (#464).
- **Tests:** `ControlsViewTest` (device choice, rows against `KeyBindings`, every cell and the table's bottom in five languages and three modes, allocation); `LocalizationLayoutTest` (both About pages and the new labels). Navigation, toggle and Back reuse the generic `Menu` and `toggleLeftHanded` (persisted by `Settings.LEFT_HANDED`, `SettingsTest`).
- **New strings** (uk, pl, de and be are drafts for native review, [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369)): `ABOUT_GAME_STORY`, `ABOUT_GAME_PLAY`, `ABOUT_GAME_MORE`, `ABOUT_GAME`, `ABOUT_RULES_CARRY`, `RIGHT_HAND`, `LEFT_HAND`, `MOVE`, `JUMP`, `PAUSE`, `PICK_UP_USE`, `THROW_FIREBALL`, `KEY_ARROWS`, `KEY_UP`, `KEY_DOWN`, `PAD_DPAD`, `STICK_HINT`.



## D136. The beetle's burst is seen, felt and heard ([#559](https://github.com/jlogicgames/edgard_in_kimeria/issues/559), 2026-11-24)

- **Status:** Accepted (author, 2026-11-24, every recommendation of the proposal; sound A). The author accepted sound A at **-16.5 dBFS** (confirmed 2026-11-24): lowered 0.5 dB from the auditioned -16.0 because the voice-set balance test caps the spread at 3 dB (see below). Amends D100's "No shake". Built; the look, the feel and the sound checked in the running game by the author, 2026-11-24, desktop and web.
- **Why:** playtest 2, F23 (D116): the burst "is not visible at all". D93 asks for an act to be seen and heard; D5 that what kills is what is seen.
- **Decision (the lethal timing, the 80x32 box and the order of `Beetle.burst` are unchanged; everything is cosmetic):**
  - **Halo, not a ring.** 32 `KIND_FLAME` dots (4 px, white and pale) fly from the body to the rim of the half-ellipse inscribed in the box (rx 40, ry 32, centred on the floor line) in 0.16 s. A circle (`RingPool`, the shockwave shader) would overstate the reach upward or understate it sideways by 8 px. All dots spawn at the instant: no emitter, no new kind, no shader.
  - **Flash and core** 44 px for 0.12 s plus a 20 px core for 0.07 s; the old spray kept; **14 embers** in the eyes' colour `ff6b57` (two in three, one white), 2 px, inside the box; **8 shell chips** (`86101f`, `9c2050`, `KIND_GRIT`) flung to the floor, the burst's debris with no body left (D101); steam 0.40 to 0.60 s. No new hue. `theSprayStaysInsideTheBlastBox` still covers every hard particle.
  - **Sound:** `beetle_pop` regenerated (see SOUND_DIRECTION.md): 0.40 s, **-16.5 dBFS**, was -18.0. The author heard -16.0; the gate `test_voice_set_levels_are_balanced` allows a 3 dB spread across the voice set (the wind-ups are -19.4), so it is -16.5, and the tolerance is not touched. All other 60 sounds are byte-identical (`git status`: only `beetle_pop.wav` changed).
  - **Felt: a shake and no hit-stop.** `ScreenShake.BURST_AMPLITUDE_PX` 1.5 and `BURST_SECONDS` 0.15 (the falling stone's numbers), started after the kill test, **only when the hero survives and is within `Beetle.SHAKE_RANGE` 160 px of the burst (centre to centre)**. It goes through `ScreenShake` only, so it is zero with the Screen shake option off (D62; the web default follows `prefers-reduced-motion`). A lethal burst starts none: `Level.playerDied` clears camera effects and the death beat (D81) is that burst's feedback; this is the answer to #448's note about a shake cut by a death, and the survivor-only rule is what #448 is asked to adopt for the other blasts. No hit-stop: it would change every other timing window, has no accessibility option and the game already has one time effect (D81).
- **Measured:** `BeetleFxTest` peaks 69 soft and 318 hard over 40 seeds at 30/60/144 fps (was 67 and 192); pools **92 and 400** (25 % headroom; were 84 and 240). Hard peak 318 is three beetles bursting at once plus three poofs.
- **Tests:** `BeetleTest` (shake on a near miss, none on a lethal burst, none for a far hero, zero with the option off, the halo reaches the box edges and top inside it), `BeetleFxTest`, `AllocationBudgetTest.beetleThroughTheRealLevel`, `tools/test_generate_sounds.py` (bounds row -16.5), `./gradlew :core:check`.
- **Not verified:** the halo on the real forest backgrounds (the strip in `docs/references/beetle-burst/` is a mock over a flat sky); the shake's feel at 30/60/144 fps. **Checked in the running game** by the author, 2026-11-24: the pop.
