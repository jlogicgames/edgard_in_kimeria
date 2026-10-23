# Directional actions (#95)

Final PR verification (2026-10-23): a separate clean checkout of the complete
change passes all 181 core tests, all 8 art-guard tests, localization (five catalogs,
83 keys), sprite-art validation, desktop/web release builds and dev-source
compilation. The earlier per-revision counts below are historical checks. Browser
and native-dev visual evidence remains linked with each relevant revision;
physical controller/phone and novice playtests remain separate acceptance work.

Camera-lifetime clarification (2026-10-23, confirmed by the author): each new
pickup in the first room shows Put down/Throw at its fixed world anchor. The first
room-clamped camera-follow target change after leaving the dead zone consumes
that pickup's reminder; it fades/slides out and cannot remain at the screen edge
or revive merely because the hero stops/returns. A fresh pickup arms it again.
Room-edge clamping, turning, screen shake and scripted respawn pans do not count
as camera following. Mechanism teaching remains repeatable after a reminder has
expired, so a carried object never blocks the lever instruction.

All 39 DirectionalActionsTest/HintTransitionTest cases and desktop/web release
builds pass, as does `validateSpriteArt`. Regressions cover movement into/out of
the dead zone, consumed reminders staying hidden, re-pickup rearming in both
layouts and mechanism teaching after consumption. The release browser at 1280×720
shows the reminder [before camera scrolling](references/controls-carry-before-camera-scroll.png)
and its absence [after scrolling](references/controls-carry-after-camera-scroll.png)
while the stone remains carried. F2 invulnerability was used for presentation
inspection; this is not a required-route/novice playtest. Release FPS is absent.

Tutorial correction (2026-10-23, D24): the entire first Forest room now teaches,
and carrying shows the selected mechanism's lesson alongside placement/throwing.
All 37 cases in DirectionalActionsTest and HintTransitionTest pass, as do
`validateSpriteArt`, `buildDesktop` and `buildWeb`. Regressions cover stone and
torch in both keyboard layouts, switching the lever without losing the held item,
its local hint anchor, positions across the first room including beyond the former
rectangle, and later-room suppression/collection return. A separate carrying-only
fixture omits mechanisms so its unchanged pickup anchor is checked independently.
The release browser at 1280×720 shows all three lines and the correct
[Turn on](references/controls-carry-lever-lesson-off.png) /
[Turn off](references/controls-carry-lever-lesson-on.png) state while still holding
the stone, with no page errors. F2 invulnerability was used only to inspect the
presentation without enemy interruption; this is not novice or route evidence.
Desktop was compiled; visual inspection used the browser renderer.

HUD correction (2026-10-23): coin and fireball icons both occupy 32×32 logical
slots, with the same 20 px count font and vertical alignment. Local desktop
`run`/`runDev` and the unnamed web development target reserve a stable FPS column
at the far right; stock is laid out to its left. Release launchers disable FPS
and the stock takes the rightmost HUD position.

Carrying decision (2026-10-23): [D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket)
accepts exactly one shared carryable slot, always visible in the animated front
hand. A pocket stone plus hand torch was rejected to preserve tool-choice puzzles
and unambiguous placement/throw commands. Recipe inventory and fireball stock
remain separate; controls and room lifecycle are unchanged. One-slot state is
implemented, including visible stone and torch rendering in the animated front
hand. Historical hidden-stone screenshots below document the earlier presentation.

Stone rendering correction (2026-10-23): the existing native 8×8 sprite now renders
after the hero through the same held-object foreground pass as the torch. Both
objects stay omitted from the world-object pass while held, so the stone cannot
appear behind the body or be drawn twice. Its attachment already follows the
current animated palm and facing; pickup, placement and throwing rules are unchanged.
All 28 DirectionalActionsTest cases, `validateSpriteArt`, `buildDesktop` and
`buildWeb` pass. The updated stone regression checks foreground draw order, native
size and its return to the world pass after throwing. Release-browser checks at
1280×720 show the stone in the hand while facing
[left](references/controls-stone-front-left.png) and
[right](references/controls-stone-front-right.png),
[running](references/controls-stone-front-run.png) and
[jumping](references/controls-stone-front-jump.png), with no page errors.
Desktop was compiled; visual inspection used the browser renderer.

Validation for this HUD correction: all 5 tests in LocalizationLayoutTest and
CampaignFlowTest pass; `validateSpriteArt`, `buildDesktop`, `buildWeb` and
`:web:gdx_teavm_web_js_build` pass. Browser checks at 1276×720 and a letterboxed
1200×500 show equal icon slots, debug FPS pushing stock left, and no FPS in
release; neither browser target reported page errors. Captures:
[release](references/hud-equal-icons-release.png),
[debug](references/hud-equal-icons-debug.png),
[release letterbox](references/hud-equal-icons-release-letterbox.png),
[debug letterbox](references/hud-equal-icons-debug-letterbox.png).
Desktop was compiled; these visual checks used both browser targets.

Implemented 2026-10-22. This replaces the proximity-based Attack/Interact priority
from #22. Current controls apply on desktop and web, with the same action dispatcher.

| Command | Result |
| --- | --- |
| Action without Up/Down | Grounded sword attack, including while holding an object. Existing recovery, wall-clamber and jump-buffer restrictions remain. |
| Down + Action | Pick up one nearby stone/portable torch, or place the held object. |
| Up + Action | Activate exactly one selected trigger; that trigger may operate several linked objects. |
| Up + Down + Action | Consume the press without an action. Release Action before trying again. |
| Ranged, holding an object | Throw that object; consume the press, spend no ammunition and fire no fireball. |
| Ranged, empty-handed | Spend one available charge on one fireball. With zero stock, show Empty. |

Directions must be held when Action is pressed. Changing direction while holding
Action never produces another action. Each device needs release/repress independently;
keyboard repeats do not repeat interactions. Menu/loading/room entry, resume,
death arrival and layout changes suppress held action/ranged input. Jump keeps its
existing separate buffer and coyote-time implementation.

## Bindings and feedback

| Device | Normal layout | Left-handed layout |
| --- | --- | --- |
| Keyboard | A/D horizontal, W/S vertical; J jump, K Action, L Ranged | Left/Right horizontal, Up/Down vertical; Z jump, X Action, C Ranged |
| Gamepad | Left stick or D-pad horizontal/vertical; A jump, X/B Action, Y Ranged | Right stick horizontal/vertical; D-pad Down jump, Left/Right Action, Up Ranged |
| Touch | Two-axis left stick, right action buttons | Two-axis right stick, left action buttons |

The mirrored action D-pad is never also a movement direction. Start/Esc and menu
bindings are unchanged. Touch labels distinguish sword, pickup, placement, blocked
placement, mechanism, throw, fireball and empty stock. All contextual teaching uses
the entire first Forest room (`forest`, room 0), at every position and height,
including retries and collection returns. Other rooms/levels omit teaching labels.
The earlier 320×144 rectangle, one-time and 50 px limits are superseded by
[D24](DECISIONS.md#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying).
Carrying shows Put down and Throw at the pickup anchor until the camera begins
scrolling, once per pickup. If a mechanism is
usable while carrying, show its Up + Action instruction first, followed by Put down
and Throw while that reminder is active, anchored by the visible mechanism.
After it expires, continue showing the mechanism instruction by itself.
Turn on/Turn off tracks the lever state;
the occupied hand must never mask this lesson. Input/trigger eligibility is unchanged.

All labels ease in over 0.18 s while rising 12 logical UI pixels from below to their
normal local position; they ease out over 0.25 s while continuing 12 pixels upward.
The outgoing text keeps its world anchor until invisible, then replacement content
starts below its own anchor. Interrupted entry/re-entry preserves the current slide
position and opacity, avoiding jumps. Touch button labels, target brackets and the
ammunition counter remain available throughout gameplay; there is no opaque banner.
Gold corner brackets frame the selected object itself. For Up + Action, a visible
lever is framed at its trigger zone and named Lever, with Turn on/Turn off matching
the linked torch. Other mechanisms frame the nearest visible linked object rather
than the invisible trigger volume; a linked torch is named Torch. Usable torches have a solid
handle and cup, including when unlit. Down + Action frames the pickup at its actual
position. There is no floating selection dot above the hero or a trigger zone. Strings are typed `Msg`
keys translated in EN/UK/PL/DE/BE; the shipped fonts already contain every glyph.

Mechanisms are eligible only inside their trigger volume. Carryables are eligible
only in contact with the player's body or within a 3 px tolerance on either axis.
A held Down + Action outside that reach does nothing and shows no pickup marker
or hint; it never turns into a sword attack.
Choose the nearest eligible target centre to the body centre; equal distances use
the smaller Tiled object ID. Carryables and mechanisms are selected separately by
Down/Up. Enemies never change the selected action. Placement inside solid terrain
or beyond the current room is blocked and retains the object.

## Objects, projectiles and recovery

The optional entrance practice objects in `forest.tmx` are stone 116, portable
torch 117, trigger 118 and its linked light 119. They require no geometry changes
and do not obstruct the required route. Try S+K, release, K, L, then L again;
W+K toggles the light. The mirrored keyboard uses Down+X, X, C and Up+X.
The torch, stone and fireball load authored transparent PNG sprites; source
masters and exact built-in ImageGen prompts are in
[the artwork record](../art/objects/generation-prompts.md). No holder/object body is
painted with primitives. Existing flame/smoke VFX remain simulated particles.
Fortress light puzzles remain separate work.

A thrown object follows a gravity arc, hits terrain/ordinary enemies and comes to
rest for reuse. A fireball flies horizontally, stops at terrain or its first enemy,
and expires after two seconds. Both use movement substeps to catch thin obstacles.
The Forest boss absorbs these projectiles without damage: its sword-only rule stays.
Objects never injure the hero. Carrying does not change movement or jump behavior.

Carryables belong to their authored room. They are rebuilt on retry and collection
return, and are released with the old room at a forward transition; they are not
persistent inventory. No old holder or projectile survives reconstruction.
Stock starts at three for a new/continued test run, is shared by both Forest levels,
and is not refilled at each level. Death restores the active room's entry stock;
forward entry secures remaining stock. Healer preparation/collection return secures
the current stock without replenishing it. Restart Level restores level-entry stock.
The persistent save still contains only a level index.

## Explicit dependencies

- [#99](https://github.com/jlogicgames/edgard_in_kimeria/issues/99) still owns real
  ladder traversal, ladder geometry and permitted actions while attached. No ladder
  handler or ladder playtest is claimed here. `Player.verticalMovement()` exposes
  Up/Down normally, but returns zero throughout a held directional Action; the
  future ladder consumer must use it so one chord cannot climb and activate.
- [#97](https://github.com/jlogicgames/edgard_in_kimeria/issues/97) has a working
  stone/portable-torch baseline here; authored environmental puzzles and author
  acceptance remain in its prototype work.
- [#98](https://github.com/jlogicgames/edgard_in_kimeria/issues/98) still owns rare
  refills, refill identities/rollback and campaign balancing. It now has a real
  consumable fireball handler to build on. No refill/shop is advertised in this build.
- [#100](https://github.com/jlogicgames/edgard_in_kimeria/issues/100) and
  [#101](https://github.com/jlogicgames/edgard_in_kimeria/issues/101) retain broader
  authored recovery and physical-device acceptance. These dependencies are not
  marked complete merely because their baseline dispatch is tested here.

## Verification (2026-10-22)

`./gradlew :core:test validateLocalization` passes: 164 tests, five catalogs,
80 keys, 192 required glyphs at every shipped size. `buildDesktop buildWeb` passes.
`DirectionalActionsTest` drives production input polling, Player and Level with
keyboard events, a simulated controller mapping/axes and two simulated touch
pointers in both layouts. It covers sword plus carrying, nearest/ID target choice,
mechanisms, pickup/place/blocked place, held buttons, menu-like suppression,
layout changes, vertical-action ownership, both object throws versus ammunition,
zero stock, room stock rollback, level-entry stock, ordinary-enemy projectile hits,
boss projectile protection and existing sword/jump restrictions.

`RoomReplayTest` uses the new Up+Action chord and retains live hazards. Required
routes pass with no fireball spending. Three identical attempts each: Forest entry
16.27 s required / 20.00 s all coins; canopy 5.00 s / 5.00 s; forest-1 5.32 s.
Collection returns: entry 14.78 / 14.25 / 14.25 s; canopy 5.13 / 5.00 / 5.00 s.
These are scripted evidence, not human or novice playtest results.

Browser canvas rendering and keyboard input were checked on the local release
build in both layouts, including the About text, carrying/sword and the throw
prompt. A fresh touch-enabled browser context with two simulated touch points
also picked up the stone in both layouts; labels changed to Put down and Throw.
[Normal screenshot](references/controls-touch-right.png) and
[mirrored screenshot](references/controls-touch-left.png) record that simulation.
This exposed TeaVM's unimplemented MultitouchScreen capability query; the web
launcher now supplies `navigator.maxTouchPoints > 0` through a platform capability
constructor, while desktop keeps native detection. Physical controller, physical multitouch and human readability checks
remain outstanding; simulation is not evidence that those devices were tested.
Snyk scanning is omitted at the author's explicit instruction; no Snyk result is claimed.

### Earlier target feedback correction (2026-10-22, superseded presentation)

Author screenshots showed that the green dot above a trigger zone or near the
hero's head did not communicate its meaning. Replaced it with gold corner brackets
around the actual selected object. Up + Action frames the nearest visible linked
mechanism (with stable coordinate tie-breaking), and a linked torch is named Torch
in the prompt. Down + Action frames the actual pickup. An unlit actionable torch
now retains a visible handle/cup; the portable torch uses the same solid silhouette.
Decorative torches and controls/action dispatch are unchanged.

The local release renderer was checked with both directions held: see the
[mechanism target](references/controls-target-mechanism.png) and
[pickup target](references/controls-target-pickup.png). The earlier touch screenshots
above document the prior feedback revision's layout and dispatch, not this visual
correction. A regression in both input layouts checks that feedback points at the
linked Torch/pickup and never at the invisible trigger or hero; ambiguous/no direction
shows no target. The full 162-test suite, localization gate and desktop/web builds pass.

### Reach, teaching and sprite correction (2026-10-22)

The earlier 24 px pickup expansion allowed interaction from a visibly excessive
distance. Reduced it to 3 px contact tolerance; tests in both layouts demonstrate
that a 12 px gap cannot select/pick up an object, walking into reach enables it, and
only the reachable target receives feedback. The entrance light is now at hand
height (map object 119: y=306 before the layer offset) and its trigger is local
(object 118: x=108, width=16). Its old remote elevated placement was unnecessary.

Removed the permanent bottom panel completely. This intermediate revision placed a compact context reminder
above the hero when a pickup/mechanism is reachable, with the full directional
chord; carrying reminds the deliberate throw or current placement chord. It goes
away outside context and while attacking. It avoids touch-button clusters. The
fireball count uses a small sprite/count at the top right, so Throw never appears
to spend the displayed fireball stock. About remains the complete control reference.

References inform this choice, not a claim that all games teach identically:
[Silence](https://gameaccessibilityguidelines.com/silence-hints/) combines names,
visual indications and configurable hints; [Moving Out](https://learn.microsoft.com/en-us/xbox/accessibility/xbox-accessibility-guidelines/109)
provides a requested hint showing relevant items/destinations.
[Game Accessibility Guidelines](https://gameaccessibilityguidelines.com/include-contextual-in-game-helpguidancetips/)
recommends gradual contextual instruction and repeatable practice. This prototype
uses its safe entrance objects and short local reminders; it does not claim a
complete campaign tutorial or externally validated teaching result.

Historical evidence of that intermediate revision: [outside reach](references/controls-reach-far.png),
[close enough to pick up](references/controls-reach-close.png),
[carried sprite](references/controls-sprite-held.png),
[mirrored simulated multitouch](references/controls-local-hint-touch.png), and
[resized/letterboxed view](references/controls-local-hint-letterbox.png).
These captures superseded the large-panel version but the following author correction supersedes their moving hints and detailed world art. The full
164-test Java suite, seven art-guard tests, localization, sprite-art gate and
desktop/web builds pass. No physical-device/human teaching claim is made.


## Historical fixed-HUD revision and native world art (2026-10-22)

This intermediate response to moving reminders placed the text at logical top y=54 in the fixed
640×360 HUD, centred in a 280 px text slot. It never projects the hero's coordinates,
never follows the camera, has no opaque panel and disappears outside context/while
attacking. All bindings and translations still come from the same action resolver.

World torch/stone/fireball are new native 8×16 / 8×8 / 16×16 indexed sprites with
at most 16 entries, binary alpha, nearest filtering and no dithering/antialiasing.
Detailed 128×128 / 256-entry HUD icons have their own textures and linear filtering.
Original coin/chicken/key world sheets are unchanged. See [art contracts](ART_DIRECTION.md).

A held stone has no world or foreground drawing; it reappears on release. The torch
uses measured palm anchors per pose/frame, mirrored about the same body pivot as
the hero. Its sprite renders in Player's foreground pass; the flame emitter is at
the head rather than the grip. Carrying retains existing sword/jump/throw behavior.


Historical fixed-HUD release-renderer captures: [pixel world / detailed HUD](references/controls-pixel-hud-start.png),
[torch in right-facing palm](references/controls-torch-front-right.png),
[left-facing palm](references/controls-torch-front-left.png),
[raised jumping palm](references/controls-torch-front-jump.png),
[hidden carried stone](references/controls-stone-hidden.png),
[same fixed reminder after movement](references/controls-fixed-hint-moved.png),
[stone visible after release](references/controls-stone-released.png),
[mirrored simulated multitouch](references/controls-fixed-hint-touch-left.png),
[1000×700 letterboxed viewport](references/controls-fixed-hint-letterbox.png).
The final browser sessions reported no runtime errors. The first check caught a
case mismatch against the existing `images/HUD` directory; runtime/export paths
now use its exact case. Full core suite: 166 tests; art guard: eight tests plus an
actual Stop envelope; five-language localization and desktop/web release builds pass.
Desktop starts and loads the new textures without errors; the visual captures above
are from the browser renderer. No physical phone/controller test is claimed.


## Historical 20 px entrance lesson — author clarification, 2026-10-23

The author clarified that nearby-item hints looked right; the problem was the
permanent Throw text following the hero. Restored local labels for reachable objects,
removed the remote top slot, and anchored the first Throw lesson at its pickup
location. After 20 horizontal world pixels travelled, that lesson disappears and
cannot restart on later pickups/retries. Standing still does not consume it.
Tutorial eligibility belongs to Level, so replacing Player after death cannot reset
lesson progress; leaving the entrance permanently ends hints for that Level instance.
Later rooms/levels and collection returns have no teaching labels. Gameplay target
selection, interaction brackets, touch button labels and inventory display are unchanged.


Current browser evidence: [nearby pickup label](references/controls-entrance-local-label.png),
[first Throw lesson](references/controls-throw-lesson-start.png),
[same anchor after a short walk](references/controls-throw-lesson-anchor.png),
[dismissed after 20 px](references/controls-throw-lesson-finished.png),
[next room without lessons](references/controls-later-room-no-lessons.png),
[simulated touch](references/controls-entrance-local-touch.png),
[letterboxed viewport](references/controls-entrance-local-letterbox.png).
Core: 169 tests passed. Localization (five languages), sprite-art gate and both release
builds pass. These browser sessions reported no runtime errors. The 20 px threshold,
cumulative backtracking, stationary time, retry/re-pickup suppression and collection
return boundary also have production-path regression tests in both keyboard layouts.


## Historical 50 px carrying lesson and smooth text — 2026-10-23

The previous Throw-only lesson did not teach putting the object down. The first
pickup now shows both explicit bindings at once, e.g. `Down + X: Put down` and
`C: Throw`, without requiring Down to reveal the placement instruction. All bindings
and action text remain localized and device-aware. Blocked placement retains the
Put down instruction and adds the localized Blocked status.

The lesson lasts for 50 horizontal pixels travelled, including backtracking.
Standing still does not expire it; the existing first-visit/one-time scope remains.
All local teaching labels ease in over 0.18 s and out over 0.25 s using unscaled UI
time. Changed wording fades the outgoing label fully before swapping content and
anchor; leaving range retains the outgoing anchor during fade-out. Re-entering the
same interaction reverses the fade without flashing. New Player ownership clears the
old presentation. Multiline labels grow upward so the second line does not cover
the hero's head. Pure presentation state has timeline regression tests; production
input tests verify both instructions in both keyboard layouts and the 50 px budget.


Current browser captures: [both commands together](references/controls-carry-both-actions.png),
[still visible before 50 px](references/controls-carry-before-50.png),
[fading out](references/controls-carry-fading-out.png),
[fully dismissed](references/controls-carry-after-50.png).
The browser session reported no runtime errors. Four HintTransition timeline tests,
localization, sprite-art validation and desktop/web release builds pass. The full
173-case run had three entrance tests using stale hardcoded practice-object positions
from an earlier map revision; their setup now derives position from the actual stone
in the shared map, which was left unchanged. The updated test sources compile.
Per the working agreement's two-identical-failures rule, the full suite was handed
back for `!./gradlew :core:test`; it has not been rerun after that fixture correction.


## Historical entrance practice area and upward slide — 2026-10-23

The rectangle below was superseded by D24's whole-first-room teaching scope.
The slide/fade behavior and historical captures remain valid presentation evidence.

The author replaced separate always-on/one-time hint rules with one spatial rule:
inside the initial 320×144 training area, show the currently relevant item action(s);
outside it, show none. The area covers the starter stone, portable torch and linked
mechanism without modifying their authored positions. Body-centre membership avoids
sprite padding affecting the boundary. Death/re-pickup/revisit does not need hidden
lesson history; scope is simply the active room, level and area.

HintTransition now keeps eased opacity and an independent vertical slide. It enters
from 12 px below, settles at its usual local position and exits 12 px above. Fade/slide
reversal starts at the current presentation, with no sign-flip teleport. Hud converts
the Y-down slide to the Y-up text projection after multiline layout, so two-line
instructions stay above the hero. Tests cover every area edge with an otherwise
reachable object, repeated pickup/place/retry, walking beyond the old 50 px budget,
room return, direction of motion and interrupted animation continuity.


Current browser evidence: [pickup in the practice area](references/controls-training-area-pickup.png),
[repeated pickup after placement](references/controls-training-area-repeat-pickup.png),
[carrying after walking beyond the former 50 px limit](references/controls-training-area-carry.png),
[outside the area without teaching](references/controls-training-area-outside.png),
[slide/fade recording](references/controls-training-area-slide.webm).
The final session used held jump input to cross the starting step and verify exit
from the area. No browser runtime errors occurred. Area/motion tests pass; the full
175-case run had two pre-existing parameterized feedback cases with a hardcoded hero
position that no longer overlapped the edited practice trigger. Their setup now uses
the actual trigger and portable torch positions and compiles. Per the two-identical-
failures working agreement, another full run is handed back as `!./gradlew :core:test`.
Both release builds, localization and sprite-art validation pass.


## Practice-light lever (2026-10-23)

The author's revised trigger (object 118, x=128/y=320/w=16/h=16 before the -16 layer
offset) now has `appearance=Lever`. The coordinates and object IDs are preserved.
After its linked Torch is spawned, Level creates a native 16×16 Lever sprite at the
trigger's bottom centre. This is a visible control, not a second actionable target:
Up + Action still dispatches once through the selected Trigger to the linked torch.
The lever's up/down state reads Torch.isBurning directly, so it cannot drift from
the actual light state. Up = on; down = off. The prompt/button explicitly says
Turn on/Turn off and names the Lever in all five languages.

Selection brackets frame the lever sprite at the revised zone, using the same
InteractionHighlight as stones. Other generic triggers keep their linked-object
feedback. Lever declarations must link exactly one torch; malformed map links fail
clearly. New pixel masters/prompts and registered exports are in
[object sources](../art/objects/generation-prompts.md#practice-light-three-quarter-revision-2026-10-23).
The latest author revision selects a 3/4 lever, superseding its earlier en-face
version. Both poses show the broad oblique working face and side/top thickness;
the same base and pivot stay registered while the handle raises/lowers. The art
skill records this lever-specific revision; other items keep their prior default.

The 3/4 revision keeps native 16×16 dimensions, binary transparency and nearest
filtering; the exported up/down images use 14/15 RGBA values respectively, within
the shared 16-entry palette contract. Native enlarged
[comparison](references/lever-three-quarter-native-preview.png) shows both poses.
`validateSpriteArt`, `buildDesktop` and `buildWeb` pass. The release browser at
1280×720 shows the [lowered/off](references/controls-lever-three-quarter-off.png)
and [raised/on](references/controls-lever-three-quarter-on.png) sprites, unchanged
selection brackets and the correct Turn on/Turn off instruction, with no page errors.
F2 invulnerability was used for this artwork inspection. Desktop was compiled;
visual inspection used the browser renderer. This revision changes artwork and
guidance only; gameplay tests were not rerun for the asset replacement.

Validation: all 175 core tests pass, with no failures, errors or skipped tests;
`validateLocalization` (83 keys), `validateSpriteArt`, `buildDesktop` and `buildWeb`
also pass. This resolves the earlier full-suite handoff after the practice objects
were moved. The directional-action test exercises both keyboard layouts, checks
the lever's own frame target, both sprite regions, the real torch state, and held
versus fresh Action presses. Localization layout checks include the new action names.

The release web game was checked at 960×540: the revised zone selects the lever,
Up+Action changes Turn on to Turn off, and release/repress returns it to Turn on.
The linked torch starts/stops its flame and the lever follows its state. Captures:
[down/off](references/controls-lever-down-off.png),
[up/on](references/controls-lever-up-on.png),
[down again](references/controls-lever-down-again.png).
Desktop was compiled; the interactive rendering check used the browser build.

Historical frontal artwork revision (superseded by 3/4): `validateSpriteArt`, `buildDesktop` and `buildWeb` pass.
The release browser game at 960×540 shows the new front panel and red grip in both
states, retains the lever's selection brackets and switches the Turn on/Turn off
prompt with the real torch. No page errors occurred. Revised captures:
[off](references/controls-lever-frontal-off.png),
[on](references/controls-lever-frontal-on.png),
[after switching back](references/controls-lever-frontal-clear.png).
This revision changes artwork and guidance only; the earlier 175-test gameplay
result above was not rerun. The skill's frontmatter is unchanged; its standalone
validator could not launch because the available Python lacks PyYAML.
