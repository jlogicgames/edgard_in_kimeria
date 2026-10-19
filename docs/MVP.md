# Two-level MVP

Accepted by the author on 2026-10-19. Future mechanics will expand these two existing
levels. This MVP does not add another map, another boss, a new quest or destination.

## Complete game loop

1. New Game plays the existing healer-narrated opening.
2. Level 1, `forest`: forest entry → canopy → pre-boss healer → forest boss.
   Existing collection returns, optional elixir, room retries and boss rules remain.
3. Boss death animation → protected automatic exit → post-boss healer guide scene.
   Its final advance saves index 1 and loads level 2 with a fresh controllable hero.
4. Level 2, `forest-1`: the existing single-room course. Its checkpoint/disappearance
   delay opens the ending instead of wrapping back to level 1.
5. Ending: black background, the opening's scrolling text/fade and `audio/intro.wav`.
   EN/UK text says Edgard defeated every enemy, freed the kingdom of monsters and
   restored light to the world. A fresh key/controller press or tap can skip with the
   same fade as the opening; held gameplay input cannot dismiss it on entry.
6. Once the ending fades out, remove only `Settings.KEY_NEXT_LEVEL` and return to the
   menu. Continue disappears; New Game is selected. Language, handedness, display and
   assist preferences remain. New Game runs the opening again and starts `forest`.

## State and compatibility

The save remains a zero-based level index only: 0 = `forest`, 1 = `forest-1`.
Previously saved numeric indices are read against this MVP order; no legacy map-identity
migration is possible from an unversioned index alone. No new save/version/checkpoint
fields are introduced. First-level completion saves 1 once. Final-level completion
writes no index 0: while the ending runs, the previous save remains; finishing/skipping
it clears that save. Closing the app during the ending therefore lets Continue restart
the second level. New Game still clears an existing run before its opening.

The ending releases the live level, hero, encounter effects and room fade; it cannot
resume gameplay or respond to level-skip callbacks by loading another map. Its input,
scroll and music follow the opening timeline. The ordinary menu track returns afterward.
Music still respects the existing sound option and volume.

## Validation

`CampaignFlowTest` covers the new level order and forest-first opening, first-level save,
ending text in both languages, held-input suppression and fresh skip, intro-music
fade/play/stop routing, exactly one ending entry, save deletion on completion, retained
settings, hidden Continue, and New Game returning to the forest. `BossCombatTest` covers
boss death/exit/guide → controllable second-level hero and one save write. Existing
IntroScreen/Input tests cover scroll/fade timing and keyboard/touch edges.

All **130 core tests passed**, together with desktop and TeaVM release builds.

Actual LWJGL/macOS checks in a temporary harness exercised the opening, first-level
boss death/exit, post-boss guide, loading the real single-room second level, its real
checkpoint timer, ending and main menu. Both EN/UK runs used isolated disk preferences;
assertions checked that `intro.wav` was playing in the ending, stopped on menu return,
that held K could not skip the ending, that the run save was removed and a handedness
setting survived. Both localized ending captures were visually inspected. The harness
seeded the boss fight with lethal hits and invoked the second checkpoint directly;
this was not a human traversal from the first entrance. It also advanced the scroll
clock for the capture/completion; automatic scrolling/fades are covered by timeline tests.

The release web build was checked in the Codex in-app browser on an isolated local
origin. New Game entered the forest first. Developer F4 reached the single-room second
level; F5 exercised its real checkpoint completion delay. The English ending scrolled
automatically to the menu with Continue absent. Reloading the browser retained this
cleared-save state. A fresh New Game still offers the opening. These debug shortcuts
verify orchestration, not human traversal or normal boss defeat in that browser run.
Desktop checks and BossCombatTest cover the normal boss exit/guide boundary.

Temporary harness classes/preferences and the local server were removed; retained
captures are outside the repository. Physical controllers/touchscreens, a full human
playthrough, subjective audio quality and final author readability review remain open.
Headless music spies establish routing/envelopes; desktop `isPlaying` confirms the real
music backend, not browser autoplay behavior.
