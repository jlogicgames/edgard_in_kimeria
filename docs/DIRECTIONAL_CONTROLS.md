# Directional actions (#95)

Implemented 2026-10-22, with the author's final tutorial/carrying clarifications
on 2026-10-23. This replaces the proximity-based Attack/Interact priority from #22.
Desktop and web share the same dispatcher. Accepted choices and their rationale
are retained in [D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket)
and [D24](DECISIONS.md#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying).
This document describes the current behavior; superseded artwork and intermediate
UI captures have been removed.

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

## Standing look up/down (2026-10-23)

Standing still on solid ground, hold Up or Down without Action/Ranged to look.
The corresponding static head pose replaces idle immediately. After a continuous
0.3-second hold, the camera eases vertically over one second: 100 logical pixels
up or 150 down. Release, reverse, walk, jump, attack or interact to cancel/restart the hold;
the camera eases back over 0.2 seconds, matching the immediate return to idle. Conflicting Up+Down selects no look.
The full inspection distance is allowed even outside the current room or with
the hero temporarily outside the view; horizontal room bounds remain intact. Deliberate inspection is an explicit exception to the ordinary
vertical room clamp: current Forest rooms are only 8 px taller than the viewport.
This changes neither hitboxes nor movement and is not crouching or ladder climbing.
UI/layout boundaries discard the old hold and require vertical release before
inspection can restart. A carried object stays in the unchanged front palm.
When a reachable pickup or usable mechanism is available, allow a one-second
hold before moving the camera, giving its pickup/activation chord priority over
inspection. Choose this delay at the beginning of each hold; other holds keep
0.3 seconds. A carry-only Put down/Throw reminder does not extend the delay.
Target eligibility governs this rule even if the held look direction hides the
other direction's teaching label, or a later room omits teaching text.
All devices use their active movement directions; mirrored gamepad action D-pad
buttons never double as look directions.

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

Both 16×16 poses are stored in one registered 32×16 sheet,
`assets/images/objects/lever.png`: left = up/on, right = down/off.
The runtime regions share the texture, palette, base and pivot; nearest filtering
and binary alpha preserve the selected native pixels. The matching source sheet
and exact prompts are retained under `art/objects/`; rejected viewpoints are removed.

## Verification

The complete PR was checked in a separate clean checkout: all 198 core tests,
8 art-guard tests, localization (five catalogs, 83 keys), sprite-art validation,
desktop/web release builds and dev-source compilation passed.
`DirectionalActionsTest` exercises keyboard, simulated controller and touch in
both layouts, target selection, fresh presses, pickup/place/throw, carrying plus
sword/mechanisms, camera-dismissed reminders, ammunition and room rollback.
`RoomReplayTest` retains live hazards; required routes use no fireball charges.
These are scripted checks, not physical-device or novice playtests.

Keep only representative captures of the selected presentation:

- Carrying reminder [before camera following](references/controls-carry-before-camera-scroll.png)
  and [after following starts](references/controls-carry-after-camera-scroll.png).
  A new pickup rearms it; mechanism teaching remains available after consumption.
- Three-quarter lever [down/off](references/controls-lever-three-quarter-off.png)
  and [up/on](references/controls-lever-three-quarter-on.png), with local instructions
  and selection brackets. F2 invulnerability was used for presentation inspection.
- Matching 32 px HUD slots in [release](references/hud-equal-icons-release.png)
  and [debug](references/hud-equal-icons-debug.png); debug FPS reserves the far-right
  column, while release stock occupies it. Both use a 20 px count font.
- Real desktop DevHost FPS at [startup](references/run-dev-fps-start.png)
  and [hot restart](references/run-dev-fps-hot-restart.png).

Browser checks included both handedness layouts, simulated multitouch, held items,
resize/letterboxing and release FPS absence without runtime errors. Physical
controller/phone and novice playtests remain separate acceptance work (#101, #131).
Snyk scanning is omitted at the author's explicit instruction; no scan result is claimed.

After packing/cleanup (2026-10-23), the directional-action suite, all eight
art-guard tests, sprite validation and both release builds pass again. Every
runtime image's RGBA pixels were compared before/after export: both lever frames
are identical to the selected standalone images, and all other runtime art is
unchanged. The release browser loads the shared sheet and renders its lowered
frame, local label and target brackets without console errors.

Standing-look follow-up (2026-10-23): 100 logical pixels up / 150 down after a 0.3 s hold,
1 s eased travel, 0.2 s return, per the author's tuning. Both poses use the first idle
frame with only the head changed. The camera-shake clamp now bounds only shake,
so it cannot cancel deliberate inspection beyond room bounds. Keyboard/browser
inspection verifies both poses, full excursions and return; original body/palm/
sword/feet pixels are unchanged. Physical controller/phone checks remain #101;
real ladder traversal and ladder-zone acceptance remain #99. Novice teaching
acceptance remains #131. These are not claimed complete by simulated tests.
