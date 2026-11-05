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
  - Coins are optional healer payment in `forest-3` (#82/#84); collection is never required.
    Dialogue, payment and collection returns are described in [HEALER.md](HEALER.md);
    boss combat is described in [BOSS.md](BOSS.md).
  - **Artifacts** are optional: the game can be finished without them. The MVP set
    for `forest-3` is **Chicken + Key**; `forest-1` has no artifacts or full-set reward yet.
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
  - The MVP order is `forest-1` (one room), then `forest-3` (three rooms and boss, the last level);
    see [D32](#d32-forest-level-order-and-identity). Since #195 the order is `forest-0` (tutorial),
    `forest-1`, `forest-3`; the save indices are 0, 1, 2 and older saves are migrated (D33).
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
- **Decision:** Play the single-room `forest-1`, then `forest-3` with three rooms, the
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
- **Movement progression:** Double jump becomes available starting with the Steppe.
  Design subsequent rooms around that ability rather than an arbitrary optional
  upgrade purchase. The unlock event and exact movement rules still need design;
  the already implemented wall jump's future role remains undecided.
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
  `controls` zone is `forest-3`, room 0 (whole room); the control lessons are meant to move to
  `forest-0`'s lesson rooms as those tickets (#186, #196–#200) land, after which that zone is
  removed from `forest-3` and this entry is closed.
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

- **Status:** Accepted by the author, 2026-10-23; distance/timing are initial tuning values.
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

- **Status:** Accepted by the author, 2026-10-26.
- **Decision:** When Up/Down selects an eligible mechanism or carryable and the game draws
  its interaction outline, suppress standing look and vertical camera inspection until that
  outline disappears. If inspection was already active, cancel it and return to the ordinary
  camera target with D25's existing 0.2-second easing. The outline and its usable target retain
  the same interaction eligibility.
- **Why:** The highlight tells the player which object will receive Action. Vertical camera
  motion during that selection makes the target and hint drift while the player is deciding.
- **Details:** [Standing look](#d25-standing-look-with-delayed-vertical-inspection),
  [directional controls](DIRECTIONAL_CONTROLS.md#standing-look-updown-2026-10-23), and
  [gameplay controls](GAMEPLAY.md#controls).

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
  atom, not proof of a complete Forest prototype. Remote/throw activation, one-shot
  versus reversible supports and object-specific recovery rules remain open until
  prototyping resolves them.
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
- **Details:** [Forest design](GAME_DESIGN.md#forest-linked-route-reconfiguration),
  [D26](#d26-observe-plan-before-committing), and [roadmap](ROADMAP.md#12-forest).

## D32. Forest level order and identity

- **Status:** Accepted, 2026-11-04 ([#194](https://github.com/jlogicgames/edgard_in_kimeria/issues/194)); `forest-2` is open.
- **Decision:** The intended Forest order is `forest-0` (tutorial, no monsters, hints everywhere)
  → `forest-1` → (`forest-2`?) → `forest-3` (the boss level, formerly `forest`). The boss level
  is renamed `forest-3` (`assets/tiles/forest-3.tmx`). **Open question:** whether `forest-2`
  exists is not decided here. `forest-0` was added by #195 (D33): `KimeriaGame.LEVEL_NAMES` is
  `forest-0`, `forest-1`, `forest-3`; saves are a zero-based index into that list (0 = `forest-0`,
  1 = `forest-1`, 2 = `forest-3`). Before #195 the list was `forest-1`, `forest-3`. This reconciles earlier docs (D15 and others), which said the forest
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
  `thrown-stone` (#199), `existing-traps` (#200). Each lesson ticket fills only its room.
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
- **forest-3 room 0:** keeps its control lessons for now through one `controls` zone covering the
  room, so playtested behavior is unchanged. Moving them to `forest-0` is the lesson tickets' job.
- **Saves:** the save stays a bare level index, so inserting `forest-0` at the front would shift
  it. A new `level-order` preference stamps saves written since #195; a save without it is read as
  the old order (0 = `forest-1`, 1 = `forest-3`) and shifted by one, so Continue lands on the
  same level. New Game and the ending remove both keys.
- **Why:** The tutorial needs hints that belong to rooms and objects, are authored by level
  designers in Tiled, and can be removed in later levels without code changes.
- **Author acceptance (2026-11-05):** the author played the whole tutorial on desktop (keyboard only) and checked the web build; all six rooms, both healer dialogues and the transition into forest-1 were checked with no remarks. Not checked: gamepad, touch controls, native-speaker quality of de/pl/uk/be. No novice playtest (#129–#131). Details: [ROOMS.md](ROOMS.md#author-acceptance-of-forest-0-2026-11-05).
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
  The lever prompt reads "Turn on/off - Lever" (the lever's up state means ladder lowered).
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
  the room and level. Reproduced headlessly in `forest-0` (all four finished rooms, either wall), `forest-1` and the `forest-3` entry/canopy rooms
  (`RoomCeilingTest`; every forest-0 room, skeletons included, is tested); the same happened from the ladder and platform exit ledges, which give a 112 px head start.
- **Decision:** `Level.addCollisions` adds, for every room it builds, one invisible plain collision slab directly above the room (x = room x, width = room
  width, 64 px thick, underside at the room's top edge), the ceiling. Chosen over making the walls slippery (no grab) because wall climbing and wall jumping
  is a shipped mechanic the later levels (forest-3 canopy) are built on and the issue is the room being open, not the mechanic; a ceiling fixes the
  exploit at its cause for every present and future lesson room and level (the stone and traps rooms copy the recipe and get it for free) without
  editing any map, so no map SHA, `check_tiled_links` or web-release-gate hash changes. The slab lies wholly outside the room, so it cuts no jump, ladder,
  platform, ledge or hint zone, and a vertical moving platform stays below it. 64 px is far more than one frame of jump travel, so nothing tunnels through.
- **Consequences:** a head bump on the room top stops upward motion like any block. A carried stone thrown straight up used to leave the room (and was then removed above it, `Carryable`); it now stops
  at the slab and falls back (`blocksObject` already refused placement outside the room). Nothing may be authored above a room's top edge.
- **Not verified:** the ceiling is invisible; behavior is measured headlessly (`RoomCeilingTest`); the rooms it affects were played by the author on 2026-11-05 (see D33), with no remarks.

## D39. The tutorial thrown-stone lesson rule

- **Status:** Accepted, 2026-11-05 ([#199](https://github.com/jlogicgames/edgard_in_kimeria/issues/199)); fifth lesson room of D33, after D34-D38. Design set by the author: a chasm in the middle of the room.
- **Decision:** `forest-0-stone` (528 px) teaches exactly one new element: a **stone that is picked up (Up + Action) and thrown (Ranged) at a lever the hero cannot reach by walking**. A 96 px chasm
  with no floor cuts the room in two. The lever stands at the far lip, linked by a Tiled `target` reference (`check_tiled_links.py`) to the **horizontal moving platform** of the platform room
  (the existing `Escalator`, 32×16, 50 px/s, started and stopped by the lever, D37), parked stopped at the near lip, flush with both lips. The platform is not a new element; only the throw trigger and the stone are.
  No monster, no wall, no second element.
- **Engine changes (all opt-in, `forest-1` and `forest-3` unchanged because none of their objects carries the properties):** (1) a thrown carryable that touches a `Trigger` with the Tiled bool `throwActivated` operates it once per throw
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
- **Bomb rule (D7, unchanged):** a bomb lies on the far floor 176 px past the pit (x 3648). Touching it lights the fuse (0.8 s, 1.2 s with assist; it flashes warm, leaving does not cancel it) and then a 64x64 blast around its centre (x 3624-3688) is checked once. Three ways pass without damage and are all tested: **jump over it** (it is never lit),
  **walk on through it** (the blast is behind the hero: a continuous walker's body is at least 25 px clear when it goes off) and **back away** at least 27 px from the touch point; the hero who stands still on it is hit.
- **Damage rule for the tutorial:** the game has one-hit death and no hit points, so a mistake is the existing cheap retry: death with the usual 0.7 s slow motion, then the room restarts from its entrance (about 6 s to walk back). Nothing is added: the pit, the fallen platform and the blast all end the attempt, never the game. A room that is shorter than its walking time keeps a retry cheap.
- **Recovery / D26:** the room is secured only when the level ends, so every retry rebuilds the authored room (platform unlit and in place, bomb unlit, hero at the entrance) and Restart Level rebuilds the whole level; both are tested. The required route is reachable from every state: a hero on the entrance floor after the platform has fallen (they stepped back during the warning) cannot cross, but the pit is the `Deadline`, so dropping in is a retry
  (tested). That recovery is told to the player by the platform hint, which is true in every state, rather than by resetting or re-arming the trap (D7: a spent platform stays spent until the retry, D26: no re-arming to make a gate pass). The pit's walls cannot be climbed back (swept wall-jump inputs from inside the pit, `TrapsLessonTest`), so the pit is neither a bypass nor a trap with no exit: it is a retry. Nothing can make the retry itself impossible. After the bomb has gone off the way to the `Checkpoint` is free. The bomb cannot be unavoidable: it lies on open floor, can be jumped, and the `Checkpoint` stands 72 px beyond its blast.
- **Assist and pause:** warnings are pinned to `TrapWarnings` (1.0 s platform, 0.8 s bomb; 1.5 s and 1.2 s with the longer-warnings assist), tested in this room within 3 frames in both settings, and the route still passes with the assist. Pause stops `KimeriaGame` from calling `Level.update` (it only runs while `gameStarted`), and both traps tick only inside it, so a paused warning does not advance; this is read from the code, not covered by a headless test.
- **Level end:** the room has no `RoomExit`; the `Checkpoint` (x 3760, inside the right wall, 72 px clear of the blast) finishes the level, loading `forest-1` as before (`DirectionalActionsTest` walks the whole level, now including the jumps).
- **Hints:** two position-only message zones that butt together, each true wherever it shows: the platform falls soon after landing and a fall into the pit just retries (x 3104-3440, starts at the room edge, about 1.05 s fully visible before a continuous walk reaches the pit lip), and a touched bomb flashes then explodes, so jump over it or run clear (x 3440-3792, starts 208 px before the bomb, about 1.4 s fully visible before the touch and
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
- **Unchanged:** forest-1 and forest-3 (the boss pre/post scenes, `afterVictory`, level order, saves, ending); the lesson rooms and `RoomCeilingTest`.
- **Author play (2026-11-05):** the start tale and farewell were played on desktop (keyboard) and the web build with no remarks (see D33). **Not verified:** gamepad and touch, and the native-speaker quality of the de, pl, uk and be translations (machine-quality drafts in the same register as the existing healer text).
