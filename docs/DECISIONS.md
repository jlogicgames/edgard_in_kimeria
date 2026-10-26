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
  - **Artifacts** are optional: the game can be finished without them. The MVP set
    for `forest` is **Chicken + Key**; `forest-1` has no artifacts or full-set reward yet.
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
- **Decision:** First play `forest` with three rooms, the pre-boss healer and forest boss.
  The existing death animation, automatic exit and guide dialogue lead to `forest-1`,
  the single-room second level. Its checkpoint leads to a black scrolling ending using
  `IntroScreen` and `audio/intro.wav`, with the same fade and fresh-input skip behavior
  as the opening. Localized text says Edgard defeated every enemy, freed the kingdom of
  monsters and brought light back to the world. After the ending, clear the saved run
  and return to the menu; retain settings. Future mechanics expand these two levels.
- **Why:** This is the complete playable MVP loop, with a clear beginning and ending.
- **Persistence:** D8 remains level-only. Save index 1 after the first level's guide
  dialogue; never write index 0 on final completion. See [MVP.md](MVP.md) for scope
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

- **Status:** Author-directed, 2026-10-26; implementation pending in [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175).
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
- **Details:** [Requested interaction contract](DIRECTIONAL_CONTROLS.md#requested-interaction-revision-175-pending-implementation),
  [gameplay controls](GAMEPLAY.md#controls), and [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175).
