# Project skill validation — 2026-10-19

Game revision: `2fbcafa`. Applied all six project skills to concrete review and
verification scenarios against the existing game. This was a same-agent audit,
test execution and browser check, not an independent harness evaluation or a
new feature implementation. No game source, maps or balance values were changed.

## Results by skill

| Skill | Scenario and evidence | Outcome / refinement |
| --- | --- | --- |
| `kimeria-room-state` | Review a repeated healer collection return followed by death: trace `secureVisit`, `activateRoom`, `Enemy.revisit`, inventory and caller coin snapshots. Run RoomTest, RoomReplayTest and ArtifactInventoryTest. | Passed relevant tests. Clarified that retained enemies must discard old platform support references as well as reference the new hero. |
| `kimeria-level-authoring` | Verify authored room markers/ownership and reproduce required and collection routes on the shipped TMX maps. Inspect the route driver's assertions and setup. | Routes passed. Clarified that existing collection measurements assert all coins, not all artifacts; the return test seeds prior kills/pickups/traps directly before its movement section. Neither proves a fresh all-artifact route. |
| `kimeria-combat` | Review normal-strike interruption, protected counter, lethal hits and retry using BossCombatTest, then contact/stomp/turning/geometry suites. | Passed. Clarified that the existing sword attack is grounded-only; airborne sword use cannot be assumed when evaluating escape options. |
| `kimeria-input-flows` | Trace held-input suppression and dialogue pause routing. Run IntroInputTest, JumpBufferTest, MenuTest, HealerDialogueTest and BossCombatTest; exercise browser menu/intro and dialogue pause/resume. | Passed checked paths. Added BossCombatTest as the game-level pause/resume check; dialogue model tests alone do not exercise orchestration/rendering. |
| `kimeria-rendering` | Inspect actual UV/projection/framebuffer paths; run ShadersTest and BombTest. Visually inspect browser gameplay, text, paused dialogue post-processing and resize. | Checked scenes rendered correctly. Replaced the overly broad ban on buffer recreation during render with recreation before the framebuffer/batch pass, matching `ensureSceneBuffer`. |
| `kimeria-progress-settings` | Run SettingsTest, SavedProgressTest and MenuTest. In isolated browser storage, toggle an option, reload, Continue, cancel New Game, then confirm New Game. | Checked behavior matched instructions; no skill change needed. Settings and level progress remained separate. |

## Automated execution

```sh
./gradlew :core:test --rerun-tasks
```

Result: **125 tests, zero failures, zero errors, zero skipped** across 22 suites.
Counts overlap the skills above; there were not six independent full test runs.
Gradle emitted a native-access warning from libGDX's loader; the task succeeded.

RoomReplayTest measured each normal route on three matching attempts:

| Route | Required | All room coins |
| --- | ---: | ---: |
| forest-1 | 5.32 s | No coin route |
| forest-entry | 16.27 s | 20.00 s |
| forest-canopy | 5.00 s | 5.00 s |

Collection returns measured entry/canopy at 14.78/5.13 s, 14.25/5.00 s and
14.25/5.00 s. These are headless route measurements, not human playtest results.
BossCombatTest includes escape checks at 30/60/120 steps per second; these do
not prove teaching, visual readability or human fight completion time.

All six skills passed the bundled `quick_validate.py` using temporary PyYAML
6.0.3. Also checked documentation links, YAML UI metadata, naming, instruction
length and trailing whitespace. Structural validation does not prove implicit
skill selection or correct execution by a different harness.

## Actual browser checks

Started the local TeaVM **development** build with `./gradlew runWeb`. Used a
new isolated browser context, leaving the user's normal preferences untouched.
No injected gameplay hooks, map edits or temporary game-source changes were used.

- Holding Enter on the web start screen reached the main menu without also
  starting New Game. Holding the New Game confirmation key did not skip the
  story; a fresh Space press skipped it. Ukrainian intro text rendered upright.
- Left-handed mode was switched through Options, written to browser storage,
  and visibly restored after reload. Language was switched to Ukrainian through
  Options and remained Ukrainian after a later reload.
- Used the existing F4/F6 developer shortcuts to reach the forest healer.
  This bypassed traversal and combat and is not evidence of a completed human
  route. F4 produced the test level save through the game's developer path.
- Esc paused the healer dialogue. The post-process background showed the
  dialogue rather than gameplay, and the pause menu remained crisp. Resizing
  from 1280x720 to 900x900 retained letterboxed content. Resume returned to the
  same dialogue line rather than the world; typing continued.
- Continue was shown and selected after returning to the menu and after reload.
  Activating it loaded the forest from its beginning with a zero coin counter.
- New Game confirmation initially selected Cancel. Cancel retained the level
  key and both options. Confirm removed the level key while retaining language
  and handedness; the intro began.
- A WebGL status sample after Continue reported context not lost, error code
  zero and a 1280x720 drawing buffer. This is one sample, not a complete GL audit.

## Limits and cleanup

Physical gamepad and multitouch behavior, native desktop/fullscreen/high-DPI
rendering, the release web build, explosion appearance in-world, and a complete
human boss fight were not exercised in this pass. The skills retain those
checks for tasks that change the relevant behavior. No fairness or teaching
acceptance is claimed.

Temporary browser context, local preview process, validator environment and
captures were removed after the audit. Existing build caches/output were retained.
