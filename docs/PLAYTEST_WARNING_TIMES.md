# Playtest plan: trap warning times (#300)

**Status: a plan, not a result.** No session described here has been run. Every number marked
*candidate* or *proposed* is for the author to accept or change before the first session; none is
a design decision. When the playtest has run, the findings and the decision go into
[DECISIONS.md](DECISIONS.md) (a new D-number that updates D7 and the D46, D47, D58 and D59
mentions), as the last items of the #300 work list require.

Sources: `TrapWarnings` (`core/src/main/java/com/jlogicsoftware/kimeria/TrapWarnings.java`),
[D3](DECISIONS.md#d3-death-resets-the-current-room-and-respawn-is-instant),
[D5](DECISIONS.md#d5-hits-must-be-fair), [D7](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements)
(including its 2026-11-12 note), [D11](DECISIONS.md#d11-assist-mode-a-deliberate-exception-to-d1),
D26, D46, D47, D58, D59, [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md), [ROOMS.md](ROOMS.md), and the
playtest issues #129 (unfamiliar players, no solutions explained), #130 (20 s mastered and 60 s
first-learning hypotheses) and #140 (full-campaign playthroughs).

## 1. Goal and questions

**Goal.** Find, by watching real players, the shortest timed warning per trap family that is still
fair (D5: every death is understandable), and the absolute minimum window the assist (D11) must
give. The author's direction is Celeste/Katana Zero style: short warnings, learnable trigger
zones, instant retries (D3).

**Why this is a real question and not a number to set.** A person's visual reaction time is
usually 0.2 to 0.3 s (stated in #300, not measured here). Below that, the warning cannot be
reacted to; the player survives by having learned where the trigger is. So the playtest must tell
the two apart: a death the player "saw coming too late" and a death they "did not know was
coming".

Questions the sessions must answer (each maps to a decision rule in section 5):

1. **Q1. Per family, is a warning of 0.125 / 0.25 / 0.5 / 0.8 s fair?** Fair means: the player can
   say afterwards what killed them (D5), does not call it unfair, and gets through the trap in a
   few attempts.
2. **Q2. Does a learnable zone replace the warning?** After the first death at a trap, does the
   player pass the *next instance of the same family* (a different object, other room) at a
   short warning without dying? If yes, a short warning plus learning works; if no, the warning
   itself is doing the teaching.
3. **Q3. What absolute floor must the assist give?** The assist multiplies by 1.5, which on
   0.125 s is 0.19 s and helps nobody. Which fixed window lets a player who struggles at the
   default get through?
4. **Q4. Are difficulty tiers wanted?** The author's candidate tiers are nightmare 0.125 s,
   default 0.25 s, easy 0.5 s. Today there is no difficulty setting, only the boolean assist
   "Longer trap warnings" (D11, multiplier 1.5). Do players at 0.125 s have a good time or a bad
   one; would a player choose it deliberately?
5. **Q5. Does one value fit all families, or are per-family values needed?** (#300 says per-family
   values may be better than one number.)

Non-goals: the 20 s / 60 s room hypotheses (#130) and the full-campaign length (#140). This
plan reuses their rules (unfamiliar players, nothing explained, build and settings recorded) and
records room times only because the retry loop (D3) is the safety net for short warnings.

## 2. Trap families, rooms and values

### 2.1 What is in the shipped maps

Read from `assets/tiles/forest-0.tmx`, `forest-1.tmx`, `forest-2.tmx`; the warning source is the
constant each class passes to `TrapWarnings.duration`.

| Family (class) | Warning source | Where it is | Notes |
| --- | --- | --- | --- |
| Proximity (`ThornTrap`): thorn root | `DEFAULT_WARNING_SECONDS` | `forest-0` room `forest-0-ladder`, x 896 | the room also teaches the ladder |
| Proximity: thorn tuft | `DEFAULT_WARNING_SECONDS` | `forest-1` (single room), x 696 | first trap of the level, D58 |
| Falling, position-triggered (`FallingTrap`): branch | `DEFAULT_WARNING_SECONDS` | `forest-0` room `forest-0-wall`, x 1744 | |
| Falling, position-triggered: stone | `DEFAULT_WARNING_SECONDS` | `forest-0` room `forest-0-platform`, x 2544; `forest-2` room `forest-canopy`, x 2224 | |
| Explosive (`ExplosiveTrap`): stump | `DEFAULT_WARNING_SECONDS` | `forest-0` rooms `forest-0-stone` (x 3488) and `forest-0-traps` (x 4352) | touch or fireball or thrown stone (D47) |
| Explosive: root | `DEFAULT_WARNING_SECONDS` | `forest-2` room `forest-canopy`, x 2064 | |
| Bomb (`Bomb`, D51/D60) | `DEFAULT_WARNING_SECONDS` | `forest-2` room `forest-entry`, x 656 | separate type, same warning |
| Falling platform (`FallingPlatform`, also the rotten log) | `FALLING_PLATFORM_SECONDS` (1 s) | `forest-0` room `forest-0-traps`, x 4096; `forest-1` rotten log x 1088 (and four platforms at x 96 to 192); `forest-2` `forest-canopy`, x 1568 | the only family with its own constant |
| Rhythmic: spore puffball (`Puffball`) | the swell *is* `DEFAULT_WARNING_SECONDS`; the cycle (`period`) is authored | `forest-1`, x 944 | `Puffball.validate` sizes the calm against the *longest assisted* swell |
| Proximity, burst (`FungusTrap`): honey fungus (harmless) and false honey fungus (lethal cloud) | `DEFAULT_WARNING_SECONDS` (the swell) | `forest-0` rooms `forest-0-lever` (x 150) and `forest-0-wall` (x 1804), harmless; `forest-2` `forest-canopy`, x 1672, lethal | D63; the lethal one's cloud is 1 s, not scaled |
| Rhythmic: swinging thorn vine | none; a pendulum, never scaled | `forest-2` `forest-canopy`, x 1896 | a control: no timed warning to change |

(`Bomb` at x 656 in `forest-2.tmx` is in `forest-entry`; `forest-2` also holds the boss room.)

Enemy wind-ups (`RedMob` 0.4 s) are not scaled by the assist and are out of scope.

### 2.2 Smallest set that covers every timed family

Every timed family (proximity, falling position-triggered, explosive, falling platform,
rhythmic) appears in three rooms:

| Role | Room | Families it covers |
| --- | --- | --- |
| Primary 1 | `forest-0` / `forest-0-wall` | falling branch |
| Primary 2 | `forest-0` / `forest-0-traps` | exploding stump (touch) and falling platform |
| Primary 3 | `forest-1` | thorn tuft (proximity), spore puffball (rhythmic), rotten log (falling platform) |

Pairs for Q2: the family's **first instance** (met earlier in the fixed game order) and a
**transfer instance** (a different object, met later), so a short warning plus learning can be
seen working or failing.

| Family | First instance | Transfer instance |
| --- | --- | --- |
| Explosive stump | `forest-0` / `forest-0-stone`, x 3488 | `forest-0` / `forest-0-traps`, x 4352 |
| Proximity | `forest-0` / `forest-0-ladder`, thorn root | `forest-1`, thorn tuft |
| Falling platform | `forest-0` / `forest-0-traps`, x 4096 | `forest-1`, rotten log |
| Falling (`FallingTrap`) | `forest-0` / `forest-0-wall`, branch | `forest-0` / `forest-0-platform`, stone (then `forest-2` / `forest-canopy`) |
| Mixed, later round | `forest-2` / `forest-canopy`: stone, root, platform, vine (9.5 s mastered, D59) | |

The Primary rooms above are where per-trap numbers are analysed for Q1; the pairs feed Q2.
`forest-0-stone` and `forest-0-ladder`/`forest-0-platform` are therefore played and recorded
too, but analysed as first instances for Q2 only.

**Confounds to say plainly.** Each `forest-0` room also teaches another mechanic (ladder, wall,
platform, stone, traps), and `forest-0` is a tutorial with message-zone hints (D33). Deaths in
those rooms can come from the lesson, not the trap; the observer marks the cause. `forest-2`
`forest-canopy` is the only room where families mix; run it in a later round, not the first.

### 2.3 Values to compare

All values are **candidates** (the author's, 2026-11-12, except the control, which is today's
placeholder). The falling-platform constant is currently 1.0 s against 0.8 s for the rest; the
test uses one value `W` for every family so the arms are comparable, and keeps the platform's
extra 0.2 s out of the experiment (stated in the report, section 5.3).

| Arm | Warning `W` (all timed traps) | Role |
| --- | --- | --- |
| A | 0.125 s | candidate "nightmare" tier |
| B | 0.25 s | candidate "default" tier |
| C | 0.5 s | candidate "easy" tier |
| D | 0.8 s (and 1.0 s for the platform, as shipped) | control, today's placeholder |

Assist arms (Q3), played by "struggler" participants (section 3.4), with the assist floor
candidates **0.5 s** and **0.8 s** (absolute, replacing the 1.5 multiplier). 1.0 s is a third
floor if 0.8 s still fails. These are proposals; the repo contains no floor value.

**Expected effect of short values on existing geometry (arithmetic from documented numbers, not
measured).** The walker moves at 100 px/s (ROOMS.md, proximity section). At 0.125 s that is
12.5 px of walking between the first tell and the hit; at 0.25 s, 25 px. The explosive blast
reaches 32 px either side of the centre (D47), so a hero who touches a stump and keeps walking
needs on the order of 0.3 s to be clear; with 0.25 s or less a walker is no longer safe, only a
jump or a pre-emptive stop is. The branch rule "keep `warning - time to clear the column` at
0.15 s or more" (ROOMS.md, falling traps) no longer holds below about 0.5 s. This is exactly
why the tests `FallingTrapsTest` (walk margin), `ProximityTrapsTest` (straight walk) and two
`TrapsLessonTest` stump tests fail at 0.5 s (#300): the shipped rooms were authored for a
warning long enough to walk away from. So in arms A and B the playtest measures *rooms whose
geometry was not authored for the value*. A death caused by that is data (it says which traps
need new geometry), not a flaw of the test; the observer records it.

## 3. How to run it

### 3.1 Build variants: one place to set the value

**Does it exist now? No.** `DEFAULT_WARNING_SECONDS` and `FALLING_PLATFORM_SECONDS` are
`public static final float`, so the Java compiler inlines them into every caller and test. There
is no setting, system property or build flag to change them for one build. The only runtime
dials today are the assist toggle (`Settings.LONGER_TRAP_WARNINGS`, multiplier 1.5, private
`ASSIST_MULTIPLIER`) and the F1-F6 debug keys (below).

**What the smallest change would be (not implemented here).** Everything timed already goes
through `TrapWarnings`, so the override belongs there and nowhere else:

1. Replace the two constants by static values read once at class load from system properties,
   e.g. `kimeria.warning.default`, `kimeria.warning.falling` and (for Q3)
   `kimeria.assist.floor`, falling back to 0.8, 1.0 and "multiplier only". They must stop being
   compile-time constants, otherwise already compiled callers keep the old literal.
2. Make `duration(base)` honour the floor: `max(scaled, floor)` when the assist is on.
3. Check the three static users that run at load time: `Puffball.validate` (uses
   `longestDuration(DEFAULT)` to size the calm; shorter values only relax it), the falling-trap
   and explosive load checks, and `check_tiled_links.py` (mirrors the loader and should not
   depend on the value).
4. Desktop launch: `-Dkimeria.warning.default=0.25` next to the existing `-Dkimeria.debug=true`
   (`lwjgl3/build.gradle.kts` sets that for the run tasks). The **web** build cannot read JVM
   system properties; a playtest on the web needs a query parameter or a separate artifact per
   arm. Prefer desktop for the playtest (the author's own play-through was on desktop, D33).
5. Show the active value on the F1 debug overlay so the facilitator can confirm the build.

Until this exists no arm other than D can be built. **Gap: needs a debug override for warning
values and the assist floor (#306).** It also blocks the later work item "update
`TrapWarnings`"; do the permanent per-family values and floor there, not in the test override.

An override that changes `W` does not change the shipped tests: `TrapWarningsTest` pins 0.8 and
1.0 and must keep passing in the normal build, so the override defaults to the shipped values.

### 3.2 Entering the rooms

There is no room or level select. In debug mode (`kimeria.debug=true`, set by the lwjgl3 run
tasks) the keys are F1 gizmos, F2 invulnerability, F3 effects, F4 next level, F5 checkpoint, F6
next room (`KimeriaGame.handleDevHotkeys`). They only go *forward*. Consequences:

- A session starts at the beginning of `forest-0` and the participant plays on through the
  tutorial. That is acceptable and even preferable (it is what a real first player does, #129),
  but it costs about 6 to 8 minutes before `forest-1` (estimate from the 60 s first-learning
  hypothesis per room; not measured).
- To re-run one room for a re-test the facilitator needs a way back. **Gap: no start-at-room
  option for playtest builds (#307).** Without it, a repeat means replaying from
  the start.
- The debug keys must be off for the participant's build (they are: `KimeriaGame` ignores F1-F6
  unless built in debug mode, DEVELOPMENT.md). Run the playtest build with debug off, and use a
  separate debug build only for the facilitator's warm-up and for checking the value.

### 3.3 Number and type of players

- **Primary round (Q1, Q2, Q4, Q5):** 16 players, 4 per arm A to D, **between subjects**. A
  player sees each trap fresh only once, so one player cannot compare values on the same first
  encounter. With four per arm this is a qualitative screening, not a statistical test; nothing
  below claims significance.
- **Mix per arm:** at least one player who rarely plays platformers, one who plays them
  regularly, and (where available) one with Celeste/Katana Zero experience. Unfamiliar with the
  game in every case (#129). Not the author, not anyone who has seen the rooms.
- **Assist round (Q3):** 6 more "struggler" players (self-described as weak at action games),
  three on each floor candidate, played on arm A or B values with the assist on (needs the
  override of 3.1).
- **Second round, mastered feel (optional):** the same participants replay one room at two
  values, blind (below).

Counts are proposals. Stop earlier if four players in an arm already hit a gate in section 5
clearly (for example, 3 of 4 give a fairness rating of 1 or 2), and say so in the report.

### 3.4 Session script (about 35 minutes)

1. **Setup (3 min).** Record build id, device and input (keyboard, which keys; gamepad), screen,
   settings including the assist and the arm. Consent to screen recording of the game window.
2. **Warm-up (none).** No tutorial from the facilitator: the game's own tutorial is the test
   (#129, #131).
3. **Play (20 min cap).** From the start of `forest-0` through `forest-1`; if the participant is
   ahead of time, on into `forest-2`. The facilitator watches and fills in the observation
   sheet (section 4). If a participant has been stuck at one trap for 5 minutes, say "you can
   skip ahead if you like" and mark it as a stall, not a failure of the participant.
4. **After each primary and transfer room (30 s each).** Ask only the fairness question: "How
   fair was that last part, 1 (not at all) to 5 (completely)?" and "What killed you there?" if
   they died.
5. **Interview (5 min).** The questions in 3.6.
6. **Blind A/B (optional, 5 min).** Same room, two values, order alternated between
   participants (AB then BA), "which felt fairer, and which felt better?" Needs a restart with
   another value (3.1). Use the room the participant knows best, e.g. `forest-0-traps`.

### 3.5 Order balancing

- **Arm assignment:** rotate A, B, C, D, A, B, C, D, ... in the order players arrive, so no arm
  collects the early (fresher) or late (more tired, more experienced-looking) participants. Keep
  the experience mix balanced by hand on top (3.3). Assign by a written list made before the
  first session, not by the facilitator's impression of the player.
- **Room order inside a session is fixed** by the game's progress (forest-0 rooms, then
  forest-1). That is a limit: a later room's results mix in practice. The transfer design in
  section 2.2 measures it deliberately (first vs second instance), instead of hiding it.
- **Blind A/B order** alternates AB/BA across participants.
- **Facilitator is not blind** to the arm (they must check the build). They say the same words
  to everyone and do not react to a death.

### 3.6 What the facilitator says (and does not say)

Say, word for word, at the start:

> "This is a game in development. I am testing the game, not you. Please play as you naturally
> would and think out loud if you like. I cannot help you or explain anything while you play. If
> you get stuck, say so, and you may stop at any time."

Never: name a trap, say "jump now", hint where danger is, or say a number of seconds. After the
play: "What happened when you died at X?", "How did you know when to move?", "Would you want
that trap quicker, slower or the same?", "Did you ever feel the game cheated you? Where?",
"Did you notice the warning before the hit, or learn where to stand?" Do not ask leading
questions about the assist.

## 4. What to record

### 4.1 Per player and per trap

| Measure | Why |
| --- | --- |
| Deaths at each trap (observer attributes the cause) | Q1: cost of the warning |
| Retries before first success at the trap | Q1, Q2 |
| First-attempt success (yes/no) | Q1, Q2: does the player pass at first sight |
| Time in the room, from entry to exit (stopwatch or video) | D3 retry-loop check; #130 compatible |
| Death understood? (player says what killed them: yes/partly/no) | D5, the key fairness test |
| Fairness rating 1 to 5, asked right after the room | Q1, Q4 |
| Perceived: "saw it coming too late" / "did not know" / "unlucky" | separates reaction from learning |
| Free comments, quoted verbatim | everything else |

### 4.2 Data sheet template

One row per player per trap instance. Copy into a spreadsheet; one sheet per participant plus a
summary. `P` is the participant id, never a name.

| P | Arm (W, assist floor) | Build id / device / input | Room | Trap (family, id) | 1st instance of family? | Deaths here | Retries to pass | 1st-attempt pass (Y/N) | Time in room (s) | Understood the death (Y/Partly/N) | Late / Didn't know / Unlucky | Fairness 1-5 | Comment (verbatim) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P01 | A (0.125) | | forest-0-wall | FallingBranch id 78 | Y | | | | | | | | |
| P01 | A (0.125) | | forest-0-stone | ExplosiveStump id 80 | Y | | | | | | | | |
| P01 | A (0.125) | | forest-0-traps | ExplosiveStump id 73 | N | | | | | | | | |
| P01 | A (0.125) | | forest-0-traps | FallingPlatform id 72 | Y | | | | | | | | |
| P01 | A (0.125) | | forest-1 | ThornTuft id 35 | Y | | | | | | | | |
| P01 | A (0.125) | | forest-1 | Puffball id 36 | Y | | | | | | | | |
| P01 | A (0.125) | | forest-1 | Rotten log id 37 | N | | | | | | | | |

Per participant, once: age band, platformer experience (never / sometimes / often), Celeste or
Katana Zero played (Y/N), dominant hand, assist on (Y/N), session length, stalls (trap and
minutes), anything unusual (crash, stutter, controls).

Rules for the observer: count a death at a trap only if the player's body is lethal to it (the
death view slows time to show the cause, `Player.die(showCause)`); write the trap id from the
map, not a description. Do not add the lesson's own non-trap deaths (pits, mobs) to a trap's
count; list them in comments.

### 4.3 Does the game already record anything?

I looked in `core/src/main` for death or retry counters, attempt counts, per-room timers, a
session log, or `Gdx.app.log`, and found **none**. What exists:

- `Player.die(boolean showCause)` (a single hook point: every death passes it) and the D3 room
  retry in `KimeriaGame`; neither counts or logs anything.
- `Level.elapsed` (the rhythmic traps' clock, D46) resets on retry and is not a per-room timer
  for the session.
- The F1 debug overlay draws hitboxes only (`level.renderDebug`); it shows no values.
- Headless tests (`RoomReplayTest`, `HeadlessLevelHarness`) measure a *scripted* hero's route
  time, which the D3, D58 and D59 numbers come from. They say nothing about human deaths.

So today **all of 4.1 has to be recorded by the observer from the screen and a recording.** That
is workable for 16 to 22 sessions and is the plan. A built-in recorder would remove the
transcription errors and give exact times:

- A small session log (CSV or JSON lines, desktop only): timestamp, arm/value, room, death
  (cause = the trap id that killed the hero), room entry and exit, retry. The hook points are
  `Player.die` (cause), the room fade (entry/exit) and the retry in `KimeriaGame`.
- It must not allocate in per-frame code (AGENTS.md); logging on death and on room change is
  not per-frame and may allocate.

**Gap: no playtest telemetry or session log (#308).** The plan does not depend on it.
If it is not built, a recording of every session (screen plus voice) is mandatory, so deaths can
be recounted afterwards.

## 5. Decision rules

All thresholds here are **proposed** and are meant to be argued before the first session, not
after the data. With 4 players per arm they are screens that pick *candidates*, not tests.

### 5.1 Per family, per value

A value `W` is **acceptable for a family** when, over the players in that arm, **all** hold at
the family's first-encounter trap:

1. **Understood:** at least 80% of deaths at the trap are rated "understood" (Y or Partly).
2. **Fair:** the median fairness rating is 4 or more, and no more than one player in the arm
   rates it 1 or 2.
3. **Passable:** every player passes the trap in at most 5 deaths (above that, mark it a stall),
   and the mean room time stays within the 60 s first-learning hypothesis (D3, #130; a
   hypothesis, not a player deadline).
4. **Learnable (only for `W` below about 0.3 s, where reaction is unlikely):** at the family's
   *transfer instance* (section 2.2) at least 75% of the players who died at the first instance
   pass the second one **on their first attempt**, and none gives "did not know" as the cause
   twice.

Pick, for each family, the **shortest `W` that is acceptable**. If none of A to C is acceptable,
the family stays at D (0.8 s) or longer and the cause goes into the report.

### 5.2 The assist floor (Q3)

From the struggler round, the floor candidates 0.5 s and 0.8 s are compared on the same gates
(5.1, 1 to 3). Pick the **smallest absolute floor** at which at least 5 of the 6 strugglers get
through each primary trap in at most 5 deaths. If neither passes, test 1.0 s once more or accept
that the assist cannot solve it and say so. The floor must be an absolute window: the final
assist is `max(default * 1.5, floor)`, so a shorter default is never made worse by the assist.
The floor should not exceed the shipped default (0.8 s) unless the data demand it; that would
invert the meaning of "assist".

### 5.3 Conflicting results

- **A family passes at a short value, another does not:** keep per-family values. This needs
  separate constants in `TrapWarnings` (the platform already has one) and a new D-number; the
  single "shared place" of D7 stays the *access path*, not necessarily one number.
- **The fairness rating and the death counts disagree** (players die often but call it fair, or
  rarely die but call it unfair): trust the "understood the death" column and the verbatim
  comments, and re-run the arm with two more players before changing a value.
- **Large spread inside an arm** (one of four players cannot pass, the rest pass in one try):
  do not average; record the player's profile. The assist (5.2), not the default, serves that
  player; the default is set by the arm's majority.
- **A and B fail only because the existing geometry assumed 0.8 s (section 2.3):** do not
  conclude "0.25 s is unfair". Record which trap needs re-authoring (zone, distance, route) and
  decide whether to re-author it or keep that trap longer. The geometry, not the value, is the
  finding.
- **Difficulty tiers (Q4):** propose tiers only if at least two arms are acceptable for most
  families *and* the extremes differ in the "better" preference of the blind A/B. If only one arm
  is acceptable, ship one value and the assist; tiers would add a setting nobody needs.
- **The platform's 1 s versus 0.8 s:** keep the control as shipped; if arm D passes and the
  platform at the shared `W` also passes, merge the constants.

### 5.4 Follow-up changes (the rest of the #300 work)

After the decision, in this order (each is already a line of #300's Work list):

1. Record the decision as a new D-number in `docs/DECISIONS.md`, updating D7 (rewrite "warns
   before killing" and D26 where traps are learn-the-zone rather than react), and the D46, D47,
   D58 and D59 mentions of 0.8 s, 1.0 s, 1.2 s and 1.5 s.
2. Update `TrapWarnings` (values, per-family constants if chosen, the assist floor) and the pin in
   `TrapWarningsTest`.
3. Update the tests that depend on the real value: `FallingTrapsTest` (walk margin),
   `ProximityTrapsTest` (straight walk), two `TrapsLessonTest` stump tests, and the pinned
   literals in `RhythmTrapsTest`, `Forest1TrapsTest`, `Forest2TrapsTest` and the
   `TutorialLevelTest` routes. State every changed tolerance in the report and in DECISIONS.md
   (AGENTS.md: no silent weakening).
4. Re-measure the mastered routes (D58 12.27 s and 14.98 s, D59 9.48 s) and `RoomReplayTest`.
5. Re-author the traps whose geometry assumed the old value (section 2.3), under the ROOMS.md
   recipes and the D26 contract.
6. Update `docs/ROOMS.md`, `docs/GAME_DESIGN.md`, `docs/ORIGINAL_TRAPS.md` and any in-game hint
   text that names a time (the hints seen in `en.json` say "soon" and "smokes, then explodes",
   not seconds, but check all five languages).
7. If tiers are wanted, a difficulty setting (new D-number; it changes D11's "assist is a set of
   independent toggles" scope) and the settings UI.

## 6. Open gaps

Each gap has an open issue; when it lands, update this document (AGENTS.md: gaps in docs must be tracked).

| Gap | Issue | Update this document when it lands |
| --- | --- | --- |
| Debug/build override for warning values and the assist floor | #306 | section 3.1 |
| Playtest session log (deaths per trap, retries, room time) | #308 | section 4.3 |
| Start-at-room (or room select) for playtest builds | #307 | section 3.2 |

The playtest itself, its results and the decision are tracked by
[#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300); the unfamiliar-player
conditions follow [#129](https://github.com/jlogicgames/edgard_in_kimeria/issues/129) and the
time hypotheses [#130](https://github.com/jlogicgames/edgard_in_kimeria/issues/130).
