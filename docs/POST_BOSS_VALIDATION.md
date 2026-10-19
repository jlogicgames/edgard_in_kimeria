# Post-boss guide validation (#86) — 2026-10-19

The initial checks below record the former level order/wrap. The current two-level MVP
puts the forest boss first and `forest-1` second, followed by an ending and save deletion.
BossCombatTest now asserts index 1 after the guide scene. See [MVP.md](MVP.md) for the
current campaign flow and its separate completion checks.

## Implementation and author review

The author approved the proposed three-line meaning during implementation: the forest
guardian is defeated and the path is open; darkness remains over the kingdom; the healer
will guide Edgard onward and meet him again. EN/UK wording adds no destination or quest.
Final in-play typography/voice review by a human remains useful; approval of meaning is
not a claim that the author reviewed every translated glyph or the temporary portraits.

## Automated regressions

`./gradlew :core:test buildDesktop buildWeb`: 127 tests passed; desktop fat jar and
TeaVM release build passed. Existing pre-boss choices, payment, boss combat/retry,
room lifecycle and save tests remain enabled.

New/updated assertions cover both language timelines and speaker identity, no post-boss
choices, reveal before advance, a single terminal CONTINUE, real authored boss death and
automatic exit before dialogue, damage/retry not completing victory, held attack/movement
at entry, repeated completion callbacks and final actions, no save before final advance,
final-level wrap and a fresh hero responding to movement, attack and jump. The existing
pause/Resume integration case now includes the post-victory timeline.

## Actual desktop and browser execution

A temporary verification build used the real KimeriaGame render/update loop and shipped
maps/assets, initialized directly at a lethal boss encounter. It replaced SavedProgress
with an in-memory instance; it did not overwrite the player's saved progress. Its setup
and capture hooks were outside the tracked source tree and were removed afterward.
These are checks of a seeded successful encounter, not a human playthrough from New Game.

Desktop (LWJGL/macOS): real death presentation and automatic exit led to HEALER, PAUSED,
HEALER, LOADING at level 0 and PLAYING at level 0. Scripted held K/D could not reveal or
advance the entry line. Esc froze text; Resume preserved the scene. Repeated final presses
caused one wrap/save. Both language versions of every line were measured with the actual
baked font against the 556-by-80-pixel text area; captured dialogue frames were inspected.
The fresh hero's movement, grounded sword attack and jump were exercised in the running
renderer as well as in the headless integration regression.

Browser (TeaVM development output, Codex in-app browser): observed the boss exit, healer
scene, dialogue-only pause background, Resume, held K advancing only one line, subsequent
fresh advance, and mouse/tap final advance. Repeated K while loading did not cause another
level transition. Logs recorded exactly one LOADING level=0 saved=true, then PLAYING
level=0 saved=true. Actual D movement, J jump and grounded K sword animation worked in
the next level. English text and speaker highlights were visually inspected. Desktop
captures and font measurements cover Ukrainian; this was not a separate Ukrainian browser
playthrough. Release compilation was also checked, but the browser run used development JS.

Physical gamepad, touchscreen/multitouch, both handedness layouts on real devices,
mobile browsers and human end-to-end play/readability review remain unverified here.
Mouse taps and scripted keyboard input do not establish those device results.

## Project dialogue skill

Created and applied `.agents/skills/kimeria-dialogue/SKILL.md` throughout the change.
It points to current healer/story/room policies and the actual model/host owners rather
than duplicating input, rendering or rollback instructions. Applying it led to the
completed-exit gate, terminal-event guard, dialogue-only pause case and actual renderer
checks. A scripted desktop movement check initially used the wrong saved handedness; explicitly
selecting the intended layout in memory corrected the harness without changing player
preferences or game bindings. Refined the guidance to isolate verification language and
handedness, distinguish author-approved meaning from wording/layout
review, check every line using the baked font, and distinguish headless tests from
physical-device and browser observations. Documentation link validation caught and fixed
an initial relative-link depth error.

The bundled `quick_validate.py` passed in an isolated temporary Python environment with
PyYAML; YAML fields and every relative documentation link also passed a separate local
check. The default Python environments lacked PyYAML; no gate exemption was used.

Snyk transmission was rejected by automatic approval review and the author explicitly
chose local checks only. No project source was sent to Snyk. Local review found no added
network, file, credential or dependency handling in the gameplay change.

## Shared dialogue styling follow-up — 2026-10-19

Applied the author's presentation update through the shared renderer: Edgard's name
and lines are white and his lines align left; the healer's name and lines are green
and his lines align right. The complete line determines wrapping/alignment throughout
the reveal, so its visible prefix does not shift as letters appear. `Next >` / `Далі >`
appears below a fully revealed line and is absent during typing or choices.

All 127 core tests and desktop/web release builds passed. A temporary desktop renderer
preview checked pre-boss healer speech, post-boss Edgard speech, partially revealed
healer speech, and the complete-line prompt in EN/UK using the real baked fonts.
Captured frames were inspected for color, alignment and prompt visibility. No new
browser/device playthrough was performed for this styling-only follow-up. Temporary
preview source/classes were removed; retained screenshots are outside the repository.
