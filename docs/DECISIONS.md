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
    own. **Screen shake** (On/Off, D62) is the second item; it defaults to on, and on the web to
    the browser's reduced-motion preference.
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
  `controls` zone is `forest-3` (now `forest-2`), room 0 (whole room); the control lessons are meant to move to
  `forest-0`'s lesson rooms as those tickets (#186, #196–#200) land, after which that zone is
  removed from `forest-3` (now `forest-2`) and this entry is closed.
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
- **forest-3 (now forest-2) room 0:** keeps its control lessons for now through one `controls` zone covering the
  room, so playtested behavior is unchanged. Moving them to `forest-0` is the lesson tickets' job.
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
- **Art:** original redraws, not DOS copies: a small purple bat (wings up/down) and a purple spotted mushroom, each a native 32×16 two-frame strip in `assets/images/objects/` (16 palette entries with alpha, binary alpha) plus 128×128 HUD icons. The coin keeps `Items.png`. Masters are hand-placed pixel maps in `art/objects/*-pixel.png` and `art/hud/*-master.png`; the HUD icons are the world frames smoothed up, not separately painted illustrations (no image generator was available). A later hand-painted HUD pass is welcome.
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
- **Not decided or not verified:** how the rooms look and feel in the running game (the branch hangs without foliage tiles above it: no tile art was added; the stone starts flush with the room top), the novice reading of the hints, the translations (de, pl, uk, be are drafts like the other hints), the gamepad and touch feel of the jumps over the root and the stump, and whether the four longer rooms (+144 to +208 px) still feel short to the author, who asked for short rooms: the trap, its envelope and two hint leads are the cost.

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
- **Decision:** the stick base, knob, Action/Ranged discs, Jump arrow and pause button use new art under `assets/images/HUD/touch/` (`disc`, `base`, `knob`, `jump`, `pause`, each with a `-down` pressed variant for the buttons and knob), same 64x64 / 32x32 sizes and the same drawn radii. Light cream fill, 2 px white ring inside a dark navy outline (the navy reads on sky, the white on the dark bars and ground), dark navy icons; pressed state is amber and drawn 8% smaller. Idle alpha 0.94 (was 0.55 / 0.85), pressed 1.0. The Action and Ranged buttons **keep their text labels** (they change with the situation: Sword / Use / Pick up / Turn on / Fireball / Throw / Empty and so on, in five languages; the author ruled out icons). The labels are drawn in dark navy `TouchControls.LABEL_COLOR` (13 px) instead of white because the buttons are light; the portrait rotate hint stays white on the dark strip. The old `Joystick.png`, `JumpButton.png`, `Knob.png`, `PauseButton.png` are untouched and no longer loaded by `TouchControls` (no other user in the code).
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
- **Author's choices (2026-11-09):** three traps (not four); the full-route retry cost is accepted and `forest-1` is **not** split into rooms; the first trap is the **thorn tuft** (not the learned root); the **rotten log goes last**; **no exploding root** and no fireball choice in this level; and, changing the plan's recommendation, **an enemy is added in the trap stretch** so the level alternates things instead of keeping the RedMob alone.
- **Decision:** in the 620 px of empty floor between the wall (x 480-528) and the checkpoint (x 1216), one thing at a time, left to right (Y grows downward, floor top y 320): `ThornTuft` at x 696, y 304 (zone 80, trigger x 616-792; id 35); the second `RedMob` at x 864, y 288, `offNeg`/`offPos` 2 tiles (hero centre x 832-896, worst reach of body and whip x 816-912; id 38); `Puffball` at x 944, y 304 (`period` 4, `cloud` 1, `phase` 0; cloud x 940-964 with margin; id 36); a 144 px pit at x 1024-1168 with a `FallingPlatform` `skin=log` at x 1088, y 320 (id 37). The floor collision rectangle (id 3) is split (x 0-1024, new id 39 x 1168-1280), the three floor tile rows lose columns 64-72, with edge tiles at columns 63 and 73. `nextobjectid` 40. No hints (forest-1 has none, D33).
- **Enemy choice:** a second **RedMob**, the only walking enemy the player already met in forest-1 (YellowMob and Bat are not in forest-0 or forest-1). Its telegraph is the whip's raised arm, 0.4 s of wind-up before the 0.2 s strike (`RedMob`); enemy wind-ups are **not** scaled by the longer-warnings assist (D11 applies to traps, `TrapWarnings`), so the assist changes nothing here. A sword hit or a stomp defeats it, so the required route needs **no ammunition** (D26). It is a ground blocker that chases only while the hero's centre is in its small range, so it never enters the tuft zone (24+ px clear), the cloud (28 px clear) or the log, and is beyond the pit (tested: the mob's worst reach is checked against every trap area).
- **Telegraphs (D7, no hints):** the tuft bristles for 0.8 s (1.2 s with the assist) when the hero enters its zone, then stays lethal; the puffball cap swells 0.8 s (1.2 s) before a 1.0 s cloud; the log shows the shared falling-platform warning (1.0 s, 1.5 s with the assist), the same behaviour as the four platforms at x 96-224. A tuft or puffball needs no bypass rule (not explosive; the jump envelope and the cloud's calm window are the loader's checks).
- **Required route (D26):** after the lever-272 wall: jump the tuft, sword or stomp the mob, cross the cloud in the calm, jump the pit onto the log and on to the far floor, step to the checkpoint. A hero who steps back off the log while it falls finds it gone; the pit is the level `Deadline`, so it is a retry (the pit is wider than any jump and not climbable), never a soft-lock; nothing is re-armed to make the gate pass. The checkpoint stays 48 px past the pit and the spawn is far from every trap.
- **Measured (scripted hero, real input, zero ammunition, `Forest1TrapsTest` and `RoomReplayTest`):** mastered route **12.27 s** (was 11.58 s), 0.00 s waiting for the puffball, 14.98 s with the longer-warnings assist (the swell lasts 1.2 s then); budget 20 s. A death at the log replays about 12.7 s. Author check, not a novice playtest (#129-#131).
- **Test changes, stated plainly:** (1) `RoomReplayTest.singleRoomSecondLevelRequiredRouteFitsTwentySeconds`: its scripted takeoff list changed from `{125, 260, 540, 1120}` to `{125, 260, 540, 637, 993, 1089}`, because the old walker no longer passes the tuft and the pit; the 20 s limit, the zero-ammunition assertions and the three-identical-attempts check are unchanged. (2) `RhythmTrapsTest.SHIPPED_ALLOWANCE`: `forest-1` goes from nothing to `Puffball` and `skin` (the rest stay empty). No threshold or tolerance was relaxed. New: `Forest1TrapsTest`.
- **Not verified (tracked by #260):** the new sprites and the pit edge tiles on this map by eye; novice playtests; a human feel of a 12-13 s retry loop. No new text, so no translation review.


## D59. forest-2 traps in the approach to the boss: a thorn vine, an exploding root and a falling stone

- **Status:** Implemented by [#261](https://github.com/jlogicgames/edgard_in_kimeria/issues/261), approved by the author on 2026-11-10 (plan and answers in [ROOMS.md](ROOMS.md#forest-2-trap-placement-plan-261-d59)). Automated tests only; nobody has seen the new sprites, the earth blocks that hold the vine and the stone, or the lengthened room in the running game (tracked by #261).
- **Decision:** `forest-entry` is not touched: it has no free floor (gaps of 64, 16 and 48 px between its hazards), already takes 16.27 s and holds the level's Bomb. `forest-canopy` is lengthened by **448 px (28 tiles) at x = 1808**: floor, tile layer (28 columns), the `Deadline` and everything right of the cut move or grow; the old exit 1872 became 2320 and the **boss room is only translated by +448** (Room 2560-3008, entrance 2640,304, Boss object 2912,304; nothing inside it changed). Map width 160 -> 188 tiles. Three traps stand in the new stretch, one at a time, each alone, no enemy added: T1 `ThornVine` x 1896 (anchor x 1908, y 256, floor 64 below, `period` 4.5, `amplitude` 50, `phase` 0); T2 `ExplosiveRoot` x 2064, y 296 (stands on the floor, blast square x 2040-2104); T3 `FallingStone` x 2224 from the room top (320 px drop, zone x 2208-2240, y 288-320). Both the vine and the root are placed for the first time in any level. No hints, no Bomb companion, no extra stone pickup. The Bomb (x 656, entry room) is unchanged.
- **Rules kept:** D7 (the vine's motion, the root's wind-up and the stone's growing shadow are the telegraphs), D26 (no ammunition on the route; the root's bypass is the floor jump the loader validates), D42/D47 explosive rule, D3 room retry, D44-D47 placement rules (every gap between a lethal area and the entrance, the exit and the other traps is at least 48 px: Bat reach to vine 80, vine to root envelope 48, root envelope to stone zone 64, stone zone to exit 80).
- **Measured (scripted hero, real input, zero ammunition; `Forest2TrapsTest`, `RoomReplayTest`):** `forest-canopy` mastered route **9.48 s** (was 5.00 s), the same jumping the root or walking through it and with the longer-warnings assist; collection route 9.48 s (was 5.00 s); collection returns through the canopy 9.62 s then 9.48 s (was 5.13 / 5.00 s); `forest-entry` 16.27 s and 20.00 s unchanged. The vine is reached at 4.87 s and passed with 0.8 s of slack; a blind walker started at every 0.1 s offset is killed in 15 of 45 cases. The root is passed unlit by a takeoff with the body's right edge 5 to 57 px before it (measured every pixel); walking through the lit root survives. The stone warns for 49 / 73 updates (0.8 / 1.2 s).
- **Test changes, stated plainly (no tolerance relaxed):** new `Forest2TrapsTest`; `BossCombatTest` coordinates 2112 -> 2560 and 2192,304 -> 2640,304; `RhythmTrapsTest` allowance (`ThornVine` in forest-2); `ExplosiveTrapsTest` census (one root in forest-2); `FallingTrapsTest` census (one stone, no branch in forest-2); `RoomReplayTest` unchanged. `tools/check_tiled_links.py` now applies an object group's `offsetx` / `offsety` like the game's loader (it ignored forest-2's -16 `SpawnPoints` offset); test added.
- **Deviation from the approved plan:** the vine object is at x 1896, not 1908 (1908 is its anchor), so the plan's swept-area gaps hold.
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
- **Not verified:** how the 1.5 px lever shake looks and feels in the running game (desktop and web); the label wording in de, pl, uk and be (machine-quality drafts, native review pending, [LOCALIZATION_VALIDATION.md](LOCALIZATION_VALIDATION.md)); `prefers-reduced-motion` in iOS Safari and Firefox (see the browser check in the pull request).


## D63. Honey fungus and false honey fungus: a pair of look-alike proximity mushrooms (#295, phase 1: decision and art)

- **Status:** Phase 1 (this record and the art) and **phase 2 built** for [#295](https://github.com/jlogicgames/edgard_in_kimeria/issues/295) (2026-11-13): the objects `HoneyFungus` and `FalseHoneyFungus` on the shared base class `FungusTrap`, the tests, the Tiled checker and the placement below ("As built"). The author's decisions of 2026-11-12 are recorded here; the **sprites and the look in the running game still await the author's eye**. The placement proposal was not objected to and is treated as accepted, with the one change forced by the measurement: the false fungus stands in `forest-canopy`, not `forest-entry` (see "As built").
- **Decisions (author, 2026-11-12):**
  - **Death:** the false honey fungus's spore cloud kills by an overlap of its rectangle with the hero's body box, as the bomb blast and the spore puffball (D46, D3). The honey fungus's burst has **no hitbox**.
  - **Trigger:** the hero entering the mushroom's zone, as for the other proximity traps (D44).
  - **Telegraph (D7):** both swell for the shared warning before they burst; the warning is the only thing the longer-warnings assist scales. It is read from `TrapWarnings` (`DEFAULT_WARNING_SECONDS`, today 0.8 s, 1.2 s with the assist), **never a literal**. The current value is a placeholder that was never playtested; the author wants shorter warnings, tuned by playtest ([#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300)), so a changed value must flow through without touching these objects.
  - **Idle animation:** both play a subtle deterministic idle (level clock, no randomness, independent of the hero), so a live mushroom can be told from a static one at a distance. Static look-alike scenery is [#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299).
  - **Look:** real fungi (tufts of small caps on thin stems), not a fly agaric, not the healer's Mushroom pickup (D43), not `trap-puffball` (D46). Harmless: honey-brown caps, pale stems **with a ring**. Poisonous: sulphur-yellow to orange caps with a greenish tinge, olive gills, **no ring**. Spores: warm golden particles (harmless) against the lime of `trap-spores` (lethal).
  - **Objects:** two new Tiled types, `HoneyFungus` and `FalseHoneyFungus`, with one shared base class (proximity, swell, burst); not an extension of `Puffball` (a rhythmic timer trap, no proximity, no harmless variant).
  - **Burst:** the harmless one uses particles (`KimeriaParticleSystem`, an `AllocationBudgetTest` scenario, `tools/check_allocations.py`); the lethal one uses a sprite cloud first, replaced by particles and shaders after the MVP ([#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296)).
- **Art built in phase 1 (original drawings, grids in `art/objects/forest-traps/`, exporter `tools/forest_trap_sprites.py`, layouts in `FOREST_TRAP_SHEETS`, `SpriteSheets.TRAP_HONEY_FUNGUS` and `TRAP_FALSE_HONEY_FUNGUS`, previews in `docs/references/forest-traps/`):**
  - `trap-honey-fungus` and `trap-false-honey-fungus`: **24x16 px per frame**, six frames each in one strip of 144x16 px: `idle1`, `idle2`, `idle3` (a brightness pulse only, the silhouette is identical, so the runtime may play them as a ping-pong loop on the level clock), `swell1`, `swell2` (the caps grow and brighten: the D7 telegraph) and `spent` (deflated, darker: the state after the burst, shown for the harmless one and for the lethal one once its cloud has gone). Each uses 7 colours plus transparency (limit 16), binary alpha.
  - The two are drawn to be **near twins**: the same three-cap tuft, the same stem positions and the same bounding box on every frame. They differ in three small things: the colour (honey `e2b25a / c08a4c / 8a5a30` caps and bone `efe6c4 / a89c78` stems; against sulphur `dcdc50 / d2a62c / 8f8a26` caps and greenish stems `d4dc9c / 98a468`), the **rings** on all three stems of the honey fungus (none on the false one) and a small **bump** on the false one's big cap. The gills are `4a2e1c` (brown) and `4a4a18` (olive) respectively.
  - The footprint is 24x16: an object `y = floor - 16`, with the tuft's bottom row on the floor line (world Y grows down). The trigger zone and the cloud rectangle are phase 2 data, not art.
  - **The lethal cloud reuses `trap-spores` unchanged** (24x24, `cloud1` puff, `cloud2` bloom, `cloud3` thin drift, lime `a6e04a / 5fa02a / e6f26a`): it already is the lethal-lime cloud the author asked for and the puffball uses it, so no new cloud art was drawn. If the author finds 24x24 too small for a tuft's cloud, phase 2 may scale it by an integer factor (nearest) with its hitbox equal to the drawn size; that choice is for phase 2 and the author. The golden burst needs no sprite (particles).
- **Proposed placement (as proposed in phase 1; the as-built coordinates follow it; D58 and D59 placements stay untouched):** teach by contrast, safe first.
  1. **Harmless, first sight:** `forest-0-lever` (the first room, x 0-576), on open floor at about x 150, between the spawn (64) and the first bush (200): in plain view, nothing to avoid, so the player watches a pretty burst of golden spores. No hint needed; if the author wants words, they follow the hint rules of ROOMS.md.
  2. **Harmless, repeat:** `forest-0-wall` (x 1472-2256), after the lever (1944) at about x 2060, off the route's critical path, so the shape is seen twice before it matters.
  3. **False, the first lesson:** `forest-2` room `forest-entry`, on the floor between the second YellowMob (832) and the RedMob (1136), at about x 980, where the route has to pass its zone and the swell is the only warning. Candidate if that stretch has no free floor after the mobs' reach is measured: `forest-canopy` before the D59 stretch (x 1808-2320). Nothing is proposed for `forest-1`: its trap stretch (D58) is full and stays as it is.
  - The false mushroom needs a bypass in the D26 sense (no ammunition; passable by outrunning the swell or by a jump over its zone), a calm window or a clear line, and a distance of at least 48 px from entrances, exits, checkpoints and other lethal areas (the rule of D44 to D47); the phase 2 tests prove it.
- **As built (phase 2, 2026-11-13).** Shared rules in `FungusTrap`: idle (deterministic ping-pong idle1, idle2, idle3, idle2 at 0.45 s a step on the object's own clock, the same for both so only the swell tells them apart) until the hero's body box enters the zone, then swell for `TrapWarnings.duration(DEFAULT_WARNING_SECONDS)` (two swell frames; leaving the zone does not cancel it), then one burst, then spent for good (no re-arming, D26). Everything ticks in `update` only (pause freezes it); a retry rebuilds the room (D3) and re-arms both. The zone is the footprint widened by `zone` (default 64 px, or `zoneLeft` / `zoneRight`) from 32 px above it to the floor; the hero is seen only while `Player.canLeaveRoom()`.
  - `HoneyFungus`: 16 golden `KIND_GLOW` spores on its own `KimeriaParticleSystem` (24 slots, seeded from its position), rising 14 to 34 px over 0.6 to 1.0 s; no hitbox, not a `LethalHazard`. `AllocationBudgetTest`: **0.0 B/frame** (3 runs, ceiling 8).
  - `FalseHoneyFungus`: the `trap-spores` sprite, **not scaled** (24x24, integer scale was not needed), centred on the mushroom with its bottom edge on the floor line, live for 1.0 s; lethal exactly where drawn (the hitbox is the drawn size, no inset), through `LethalHazard` and the same `Level.killOnLethalHazards` entry as the puffball and the bomb blast. `AllocationBudgetTest`: **0.0 B/frame**.
  - Authoring rules (load-time errors in `Level.validateFungus`, mirrored by `tools/check_tiled_links.py`): the thorn jump envelope (64 px of floor either side, 96 px of free height; a bypass: jump the cloud, or wait it out outside it), a zone that does not cover the entrance, zone sides of at least 64 px for the lethal one (24 px for the harmless one), and for the lethal one a cloud at least 48 px (`FungusTrap.CLEARANCE`) from the entrance, the exit and checkpoints. The distance of 48 px to other lethal areas (mobs' patrol, bat, vine) is asserted by `HoneyFungusTest` on the map.
  - Placement as built (world coordinates; footprint 24x16, `y = floor - 16 = 304`; `forest-2`'s SpawnPoints layer is offset by -16, so its Tiled `y` is 320):
    1. `forest-0-lever`: `HoneyFungus` at **x 150** (zone 40 px each side, so the zone starts at x 110 and stays off the entrance body), between the spawn (64) and the first bush (200).
    2. `forest-0-wall`: `HoneyFungus` at **x 1804** (zoneLeft 24, zoneRight 48: zone x 1780 to 1876, off the falling branch's zone, which ends at 1776). The proposed x 2060 was not usable: the wall's travel box (x 2048 to 2192) is inside the jump envelope, so the fungus stands before the lever (1944) instead of after it.
    3. `forest-2` **`forest-canopy`**, `FalseHoneyFungus` at **x 1672** (cloud x 1672 to 1696, y 296 to 320; zone x 1608 to 1760). `forest-entry` has no usable floor (`ROOMS.md`: the free gaps are 16 to 64 px between hazards, and the Bomb's blast, the YellowMob's patrol from x 752 and the bat leave less than the 120 px a cloud with its 48 px clearances needs), so the D63 fall-back applies. The stretch x 1664 to 1680 is the only one in the canopy before the D59 stretch that satisfies all of: envelope clear of the falling platform (column 1568 to 1600), 48 px from the RedMob's patrol (ends 1536), 48 px from the bat (hurts from 1752), 48 px from the vine at 1896. D58 (`forest-1`) and the D59 stretch (x 1808 and on) are untouched.
  - The mastered routes of the canopy: the first pass flies over it on the falling platform and the ledge (no change); a floor walker (the second pass, the later collection trips, the required route) takes off at hero x 1600 (body right edge 43 px before the cloud). `RoomReplayTest` and `Forest2TrapsTest` carry that jump; their budgets (20 s and 30 s) and identical-attempt checks were not changed. Measured by `HoneyFungusTest` with a scripted hero from the zone's edge: waiting beside the cloud and then walking past is safe with and without the assist, a jump over it from three take-off distances survives at every start delay tried, and a walker without a jump is killed in 14 of 21 start delays and passes in 7 (the others wait it out), so the lesson has teeth and a bypass.
- **Rules kept:** D3 (retry), D7 (telegraph, assist), D26 (no ammunition, no re-arming), D42 and D44 to D47 (placement and zone distances), D43 (the pickup's look stays distinct), D58 and D59 (placements untouched).
- **Not verified:** the sprites and bursts at game scale in the running game and by the author's eye (the headless tests prove geometry, timing and allocation, not the look); that the pair is told apart "only on a close look" at the real scale; the gap for static look-alikes ([#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299)) and the polish ([#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296)) are separate issues.

## D64. Feel fixes for the #239 environment effects: landing dust, bush trigger, looping wall rumble (2026-11-13)

- **Status:** Implemented after the author played the game; automated checks only, the look and sound were not judged by a human yet.
- **Landing dust:** a smaller puff (4 particles, same dust) and the `dust_puff` sound at 0.6 volume on an airborne-to-grounded transition with a downward speed of at least `Player.LANDING_MIN_FALL_SPEED` = 140 px/s (about a 16 px fall). Ledge walk-offs, wall slides, ladder step-offs and quicksand do not puff. New `GameContext.playSound(name, volumeScale)` (default ignores the scale).
- **Bush trigger:** the brush starts when the hero's body box overlaps the bush's visible body (rectangle inset 3 / 2 / 6 px left / right / top, measured on the art), not the rectangle widened by 6 px. `Decor.REACH` is removed.
- **Wall rumble:** `wall_rumble.wav` is regenerated as a seamless 1 s loop and played with `GameContext.loopSound` for `Wall.moveDurationSeconds()`, then `stopSound`; `Level.clearCameraEffects` (pause, death, retry, room change) stops it. Chosen over tiling or a longer file because the loop stops exactly with the wall for any `time` property (the shipped maps use 1.5 s, the default is 1 s) and on pause, death and room change, and both backends support `loop`/`stop(id)`.
- **Tests changed, stated plainly (no tolerance relaxed):** `DecorTest.aStandingHeroOrOneOutsideTheWidenedBoxStartsNothing` became `aStandingHeroOrOneBesideTheVisibleBodyStartsNothing` because the widened zone is exactly what was wrong; `WallLessonTest` rumble assertions read the loop request instead of a one-shot `playSound`; `tools/test_generate_sounds.py` no longer requires the loop file to start and end at exactly 0 (a seamless loop has no fade) and instead requires the wrap step to be no larger than the largest step inside the file. The one-shots still start and end at 0.
- **Measured:** landing dust through the real `Level.fx()` 0.0 B/frame (ceiling 8, same as the jump dust).
- **Not verified:** how the landing puff looks, the bush timing and whether the abrupt start and stop of the loop sound acceptable in the running game (desktop and web); web loop/stop behaviour in a release browser.
