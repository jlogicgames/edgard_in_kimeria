# Pre-release TODO

**Status — deferred by the author, 2026-10-20.** Run this acceptance pass after the
planned gameplay mechanics have been added, before preparing a release. It is too early
to perform the full release pass now. The current implementation/review stages in the
source documents are complete for the test version; these future checks do not keep them open.
Unchecked items below mean deferred work, not a failure or a claimed pass.

This is the single list for deferred release checks formerly scattered across
[BOSS.md](BOSS.md), [HEALER.md](HEALER.md), [ROOMS.md](ROOMS.md), [MVP.md](MVP.md),
[POST_BOSS_VALIDATION.md](POST_BOSS_VALIDATION.md), [SKILL_VALIDATION.md](SKILL_VALIDATION.md)
and [STORY.md](STORY.md). Their completed test results and limitations remain historical
records. This defers the broad release acceptance pass; focused regression checks for
new changes and the existing design contracts still apply during development.

The [roadmap](ROADMAP.md) separates prototype checks, first external playtests
during campaign production, and this final release acceptance pass. Early playtests
do not wait for every campaign room or every item in this checklist to be finished.

## Starting the pass

- [ ] Confirm the planned mechanics are implemented and identify the release revision.
  Update affected expectations below against the agreed design before testing.
- [ ] Run the core tests and build desktop and release web output:
  `./gradlew :core:test buildDesktop buildWeb`. Record actual results for that revision;
  historical test counts are not release evidence.
- [ ] Use isolated test saves/preferences. Record build, OS/browser, device/controller,
  language, handedness and assist settings for each run. Use normal play with
  invulnerability off for acceptance; record any developer-shortcut diagnostic separately.

## Devices and controls

Sources: [MVP](MVP.md#validation), [post-boss validation](POST_BOSS_VALIDATION.md),
[skill audit limits](SKILL_VALIDATION.md#limits-and-cleanup), [healer](HEALER.md).

- [ ] Play on a real touchscreen with simultaneous movement/jump/attack touches, in both
  handedness layouts. Check mirrored drawing and hit areas, separate dialogue choices,
  reveal/advance and pause/Resume. Mouse clicks do not establish multitouch behavior.
- [ ] Play with a physical gamepad in both layouts, including the unchecked left-handed
  layout: right-stick movement and D-pad actions, disjoint bindings, unchanged menus/Start.
- [ ] Recheck both keyboard layouts after the new mechanics, including Shoot when its
  mechanic is implemented. Confirm About matches the selected layout in all five supported languages.
- [ ] Hold movement/action/confirm through web start, menu, opening, dialogue entry,
  choices, Resume, level loading and ending. Only the intended fresh press may act;
  navigation must not confirm a choice. Check live layout changes and persistence.
- [ ] Complete normal end-to-end runs in the release web build, including mobile browsers
  on real devices. Earlier development-browser and seeded runs retain their limited scope.

## Rooms, pickups and traps

Sources: [rooms](ROOMS.md), [healer state policy](HEALER.md).

- [ ] Artifact pickup/rollback/retention and optional completion:

  In `forest`, collect Chicken in room one: it disappears and its icon appears after the
  coin count without increasing that count. Die before leaving: the icon disappears and
  Chicken returns. Collect it again and leave the room: a later death must keep Chicken.
  Repeat for Key in room two, then enter the boss chamber to secure both. Restart Level
  must restore the inventory from level entry, and exiting to the main menu must clear it.
  Finishing without either artifact must remain possible.

- [ ] Bomb timing, readability, lethal area and cleanup:

  With Assist off, touch a bomb in `forest`: contact must be safe, the sprite must
  alternate warm tint and normal art for 0.8 s, then explode once. Repeat with
  Assist on (1.2 s). Check readability against the forest background at normal
  scale; the bomb must stay visible throughout. Repeated contact must not restart
  the countdown, and leaving must not cancel it. Stay inside the centered 64x64
  blast square to die; leave it before detonation to survive. Passing through the
  remaining shader effect after detonation must be safe. Die during the warning
  and after the explosion on the first visit: each retry must restore an idle bomb with no
  old effect. A collection revisit instead restores its secured trap snapshot, as specified
  in [HEALER.md](HEALER.md).
  Pause during warning and resume: the countdown must resume with the game.

- [ ] Room transitions and retries:

  Start New Game and reach `forest` (the first level) through the opening, with
  invulnerability off. Walk through the original room to its far-right
  exit and verify fade out, a black midpoint, and fade in at the second entrance,
  with an idle hero and no camera travel. Walk the ascending platforms in room two
  and check its optional coins. Collect a coin and die: the hero must
  return to room two, its coin and hazards must reset, and room one's coin count
  must remain. Repeat a death, meet the healer, choose to enter the third room, defeat the boss, and watch the
  automatic victory walk. Also pause
  mid-fade, Resume, Restart Level, and run `forest-1` without room markers.

- [ ] Perform a fresh all-artifact/all-coin route and repeated collection returns with
  missed pickups, surviving/defeated enemies, warning/exploded traps and restored linked
  traversal objects. Include destroyed-platform approaches once authored. Compare HUD,
  recipe checklist and actual world state before/after death and room exits. Existing
  seeded return routes do not prove first-time discovery or every surviving-enemy state.
- [ ] Remeasure the finished rooms at the final movement/combat timings. Record mastered
  required and all-optional-pickup routes separately against the roughly 20 s replay hypothesis (D19),
  including combat/waiting and active real time; exclude loading, pause and presentation.
  Keep slow required segments in the measurement. Test relevant frame rates beyond 60 fps.
- [ ] Assess first-time discovery, threat readability, input comfort and fair deaths with
  the final mechanics. Observe unfamiliar players and record first-learning time
  to first successful exit against the roughly 60 s hypothesis for ordinary rooms,
  including observation and retries; record deaths and presentation separately.
  Treat this as playtest evidence, not a player deadline. Preserve the accepted MVP Chicken/Key placements unless a new
  author decision changes them.

- [ ] Check the revised teaching layout (`9d5e630`, #73/#75) with Assist off and on:
  the 64 px runway after the bomb must let the player escape without meeting the mob;
  the shortened bat patrol over the second quicksand must remain readable; assess
  the optional canopy collapse on the way to the Key. Ask the player to explain
  failures involving stomp, sword, bomb and falling platform without prompting.
  Earlier acceptance of the previous layout and automated route success do not
  establish readability of these changed placements.

## Boss combat, teaching and replay time

Source: [boss validation](BOSS.md#validation-record-7375). Repeat on the final mechanics,
with invulnerability off; the earlier keyboard/gamepad author acceptance remains valid
for the test version.

- [ ] Observe the throne before approaching. Approach just inside/outside 160 px, retreat,
   and confirm the boss never re-seats until retry.
- [ ] Hit during the wind-up, axe strike and completion boundary. Confirm orange warning,
   no old strike damage/shake, and one energy loss per swing. Jump away once sword recovery
   ends; repeat near both walls and under the four authored platforms. Test a deliberately
   late reaction to distinguish a fair death from an unavoidable one.
- [ ] Strike repeatedly during warning/counter. Confirm damage continues without restarting or
   cancelling it. Land a lethal sword hit during counter. Complete/miss attacks, pause during
   shake, resume, die, retry and transition: no residual shake or delayed damage.
- [ ] Land on the head while falling, including near an axe strike. Confirm bounce with no
   energy loss or player death. Test side contact both idle and swinging.
- [ ] Complete without artifacts, then with #84's consumed elixir.
   Check 12 versus 2 sword hits, unchanged starting energy, retained effect after death,
   no repeated bargain, and a fresh effect-free run after Restart Level/Continue.
- [ ] Watch death and automatic exit. Hold left/jump/attack; verify the hero crosses the right
   wall, cannot die, leaves the viewport, then opens the post-boss guide; its final advance completes/saves once. Try Checkpoint/F5 before
   defeat and during exit. Check the next level on Continue.
- [ ] With a new player, ask them to explain head contact versus sword damage and the orange
   warning without prompting. Record misunderstood rules for #73; do not mark teaching
   complete merely because the scripted scenarios pass.
- [ ] Time at least three mastered normal fights and three elixir fights from usable entrance
   input to boss defeat, recording failed attempts separately. Also record the fixed death
   presentation and exit walk separately. Record fight replay cost alongside the
   roughly 20 s room hypothesis (D19); evaluate bosses separately from ordinary-room
   first learning. Keep old measurements labeled with their original criteria and tune on evidence.

## Dialogue and localization

The opening was revised on 2026-10-20 using the author's DOS world history. It has no
collection/recipe instructions. Keep those in the healer encounter; expansion of the
historical healer story is later work. Use [original DOS references](ORIGINAL_DOS.md)
for portrait work; the shared PNGs from #90 and their author approval are recorded in
[portrait validation](PORTRAITS.md).

Sources: [healer](HEALER.md), [post-boss guide](POST_BOSS_VALIDATION.md), [story](STORY.md),
[localization validation](LOCALIZATION_VALIDATION.md).

- [ ] Read the introduction, every refusal/offer/payment line and choices in EN/UK/PL/DE/BE.
  Review portrait readability on the target devices and the healer's voice as the recurring guide.
- [ ] Have native speakers review Polish, German and Belarusian wording, control terminology,
  names and the healer's voice. Technical catalog/font checks do not approve prose quality.
- [ ] Use a physical controller and touch device, in both handedness modes. Hold inputs into
  entry, reveal, choices and Resume; navigate without confirming; pause during typing.
- [ ] Miss pickups, return repeatedly, collect more, die before/after a room exit and compare
  the checklist, HUD, enemies and traps. Include burned-platform approaches when authored.
- [ ] Fight normally and with the elixir; die repeatedly, then win. Check the impact shake,
  thicker, longer-trailed blood, effect expiry, automatic exit and absence of another healer return.
- [ ] Verify Restart Level/New Game/Continue and a clean subsequent run. Save remains level-only.
- [ ] Recheck every opening, pre-boss, post-boss, ending and About line in EN/UK/PL/DE/BE with
  the actual baked fonts and final assets: glyphs, clipping, wrapping, speaker colors,
  stable alignment during reveal and Next prompt visibility. Include all five languages in
  the release browser run; desktop measurements do not replace that observation.
- [x] Review the remastered 256-color dialogue portraits/character identity with the author.
  On 2026-10-22 the author accepted this version as the style reference for future
  illustrations; see [art direction](ART_DIRECTION.md) and [artwork review](PORTRAITS.md#author-review).
  Physical-device readability remains a separate check above.
- [ ] Confirm the recurring guide's voice and guidance match the added mechanics and
  agreed lore. Final campaign assets/writing are follow-up work where replacements are
  needed, not a reason to reopen the accepted test-version implementation.

## Rendering and audio

Sources: [skill audit limits](SKILL_VALIDATION.md#limits-and-cleanup), [MVP](MVP.md#validation),
[room effect evidence](ROOMS.md), [boss feedback](BOSS.md).

- [ ] Check native desktop fullscreen/windowed modes, high-DPI displays, resize and
  letterboxing. Check world/text orientation, HUD and dialogue readability, paused-scene
  post-processing and a crisp pause menu through the real renderer.
- [ ] Observe the explosion in actual gameplay on desktop and release web, at normal scale:
  warning remains visible, original plume rises above ground, fades cleanly, no lingering
  damage/effects. Shader-only captures do not establish in-world appearance.
- [ ] Listen to menu/opening/ending music, dialogue transitions, boss hit/restoration and
  elixir feedback. Review subjective quality, volume, sound-off behavior and fades.
- [ ] Check release-browser audio after the required user gesture, including mobile:
  startup/unlock, music transitions, mute/volume and restart/reload. Desktop `isPlaying`
  and headless spies do not establish browser autoplay behavior.

## Campaign, saves and completion

Sources: [MVP](MVP.md), [post-boss validation](POST_BOSS_VALIDATION.md).

- [ ] Play New Game → opening → forest entry/canopy → healer → normal boss defeat →
  protected exit → guide → controllable `forest-1` → checkpoint delay → ending → menu.
  Repeat without optional artifacts/elixir and with the paid recipe.
- [ ] Confirm exactly one save of index 1 after the guide's final advance; no early save,
  duplicate transition or campaign wrap. The second-level hero can move, jump and attack.
- [ ] Let the ending scroll naturally; separately skip with a fresh key/controller press
  and touch. Held entry input cannot skip it. Check picture/music fades and menu return.
- [ ] Finish/skip the ending, reload/relaunch, and confirm Continue is absent. Preserve
  language, handedness, display and assist settings. New Game starts the opening/forest.
- [ ] Close during the ending: Continue restarts the second level from its beginning.
  Recheck Restart Level, Exit, New Game cancel/confirm and clean subsequent runs on desktop
  and browser; the save stays level-only, without persisted coins/artifacts/elixir.

## Recording completion

- [ ] Record observed results against each item, with evidence and any defects. Distinguish
  actual device/human play from automated, seeded or scripted checks. Recheck fixed defects.
- [ ] Obtain final author acceptance of the release experience, readability and audio after
  mechanics are in place. Record any new deferral explicitly rather than marking it passed.
- [ ] Remove temporary previews, hooks, test saves/preferences, processes, contexts and other
  artifacts created for the pass. Preserve the user's normal data and intended evidence.
