# Directional actions (#95)

Implemented 2026-10-22, with the author's final tutorial/carrying clarifications
on 2026-10-23. This replaces the proximity-based Attack/Interact priority from #22.
Desktop and web share the same dispatcher. Accepted choices and their rationale
are retained in [D23](DECISIONS.md#d23-one-visible-carried-object-with-no-separate-pocket)
and [D24](DECISIONS.md#d24-the-whole-first-room-teaches-including-mechanisms-while-carrying).
This document describes the current behavior; superseded artwork and intermediate
UI captures have been removed.

The author requested a control revision in [#175](https://github.com/jlogicgames/edgard_in_kimeria/issues/175).
Its target behavior is recorded below as pending implementation. Until #175 ships,
the runtime follows the existing #95 contract in the tables and sections that follow.

## Requested interaction revision (#175; pending implementation)

| Command | Target result |
| --- | --- |
| Action without Up/Down | Keep the grounded sword attack, including while holding an object. |
| Up + Action | Pick up one selected nearby stone/portable torch when empty-handed, or activate one selected lever/mechanism. |
| Down + Action | Place/drop the held object on the ground. With an empty hand, do nothing. |
| Up + Down + Action | Consume the press without an action. Release Action before trying again. |
| Ranged, holding an object | Keep throwing that object without spending ammunition or firing a fireball. |
| Ranged, empty-handed | Keep spending one available charge on one fireball. With zero stock, show Empty. |

When a pickup and a mechanism are both eligible, choose one deterministically and
make the visible outline and contextual hint identify the same target that Up + Action
will affect. Keep the existing nearest-target and Tiled-ID tie-break policy across
both target types. Preserve pickup reach, placement validation, one-slot carrying,
trigger behavior, and Action-chord input suppression. Apply the rule to keyboard,
gamepad, and touch in normal and left-handed layouts. Update About text, contextual
teaching, touch labels and every localization catalog together with the implementation.

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
When an eligible mechanism or carryable is selected and its interaction outline is
visible, that selection suppresses standing look and vertical camera inspection. If
inspection was already underway, it returns to the ordinary camera target over 0.2 s
([D29](DECISIONS.md#d29-interaction-outline-takes-priority-over-standing-look)).

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
including retries and collection returns. Other rooms/levels omit teaching labels. The full-ammunition pickup message is
status feedback, available at its pickup in every room and level.
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
ammunition icons remain available throughout gameplay; there is no opaque banner.
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
The Forest boss absorbs thrown objects without damage. Fireballs damage it for exactly
2 energy each, regardless of the elixir; only the sword receives the elixir's 10x bonus.
Objects never injure the hero. Carrying does not change movement or jump behavior.

Carryables belong to their authored room. Their secured position and motion resume on
collection return; room death rolls them back to the latest secured snapshot, and a fresh
Level starts from authored positions. A held tool is released onto clear, stable collision
terrain within one hero-width of the authored entrance before the room is secured. It stays
with that room at a forward transition and is not persistent inventory. No old hero holder
reference survives.
Stock starts at three for a new/continued test run, is shared by both Forest levels,
and is not refilled at each level. Death restores the active room's entry stock;
forward entry secures remaining stock. Healer preparation/collection return secures
the current stock without replenishing it. Restart Level restores level-entry stock.
The persistent save still contains only a level index.

## Carrying acceptance (#97)

All four criteria in [#97](https://github.com/jlogicgames/edgard_in_kimeria/issues/97)
are implemented: one visible hand slot with sword use retained; Down + Action
pickup/place; Ranged throw without a fireball; and documented room ownership/rollback.
The mechanics and entrance practice objects shipped in
[PR #154](https://github.com/jlogicgames/edgard_in_kimeria/pull/154), closing #95;
secured mutable-object restoration followed in
[PR #165](https://github.com/jlogicgames/edgard_in_kimeria/pull/165), addressing #158.

Practice in the first `forest` room: touch stone 116, press S+K, release,
walk with D/A, release movement, press K to use the sword, wait for the swing to end,
press S+K to place, release, approach and pick up again, then press L to throw.
The mirrored layout uses Down+X, arrows, X and C. Stock remains unchanged
through this sequence; a separate fresh Ranged press with an empty hand fires.

State owners are `Player.carried`, `Carryable.holder` and the authored room's
object ID in `Level`. A secured `Carryable.Snapshot` stores x/y, velocity x/y,
thrown and removed flags. Before securing, release a held object onto safe support;
restoration clears the old holder. First-visit death restores authored state,
revisit death restores the latest secured state, and Restart Level rebuilds authored
objects. Tools never enter the persistent save. See [room lifecycle](ROOMS.md).

Verification on 2026-10-25: `DirectionalActionsTest`, `RoomTest` and `RoomReplayTest`
pass. Coverage includes sword use while carrying, one-slot selection, pickup/place,
throw/fireball separation, shipped entrance practice in both keyboard layouts,
holder cleanup, secured tool restoration and collection-return routes.
This audit adds no new physical-device or human playtest evidence. Authored
environmental puzzles and wider recovery/device acceptance remain separate below.

## Fireball refills (#98)

Author decisions, 2026-10-25: maximum **three held charges**, auto-pickup on body
contact, +1 per pickup. A full stock leaves the world sprite untouched and shows
`FIREBALL_STOCK_FULL` above it through the existing `HintTransition` fade/slide,
including outside the first teaching room. All five catalogs translate the message.
Leaving contact, spending a charge or losing gameplay control dismisses the hint.
Holding a stone/torch does not block ammunition collection or use its carryable slot.
The HUD has three permanent 32×32 icons, 4 px apart; available charges are bright,
spent charges use 25% RGB brightness. There is no ammunition number. The existing
separate illustrated HUD texture and native 16×16 world sprite are reused.

| Map / owner | Authored refill | Position | Purpose |
| --- | --- | --- | --- |
| `forest` / `forest-canopy` | 121 | TMX (1824, 320), world (1824, 304) after layer offset | Before the exit to the healer/boss chamber |
| `forest-1` / sole room | 25 | (312, 176) | Optional jump above horizontal moving platform 17 |

`Level.pendingPickups` / `keptPickups` track the authored map IDs, separately from
recipe artifacts. Full-stock contact records nothing. Death restores room-entry stock
and unsecure refills together; securing a room/healer visit retains both the charge
and consumed identity. Spending a secured charge never respawns its pickup on a
collection return. Restart Level restores level-entry stock and authored pickup
opportunities. Stock stays run-only; no new save field or automatic level refill.

Audit against main `ae7318a`: [PR #154](https://github.com/jlogicgames/edgard_in_kimeria/pull/154)
(closed #95) already implemented single-press fireball spending, empty-stock labels,
throw/fire separation and stock snapshots. [PR #159](https://github.com/jlogicgames/edgard_in_kimeria/pull/159)
(closed #155) fixed magical boss damage at two independently of the sword elixir;
[PR #166](https://github.com/jlogicgames/edgard_in_kimeria/pull/166) protects orange
retaliation. [PR #165](https://github.com/jlogicgames/edgard_in_kimeria/pull/165)
provides retained room owners. #98 adds the missing authored refill opportunity,
full-stock feedback and icon presentation, merged in
[PR #168](https://github.com/jlogicgames/edgard_in_kimeria/pull/168) on 2026-10-25.
The merge does not by itself establish deployment or manual acceptance.

## Explicit dependencies

- [#99](https://github.com/jlogicgames/edgard_in_kimeria/issues/99) is implemented:
  see [Ladders](#ladders-99). `Player.verticalMovement()` still returns zero
  throughout a held directional Action, and the ladder consumes it so one chord cannot
  climb and activate. Only simulated-device tests are claimed; no physical ladder playtest.
- [#97](https://github.com/jlogicgames/edgard_in_kimeria/issues/97) meets its carrying
  criteria; [evidence above](#carrying-acceptance-97). Authored environmental puzzles
  remain #102–#104 (Forest) and #112–#115 (Fortress).
- [#98](https://github.com/jlogicgames/edgard_in_kimeria/issues/98) now has two rare
  single-charge refills and identity/stock rollback; [rules and audit](#fireball-refills-98).
  Campaign shop supply/prices remain future economy work; no shop is advertised.
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
- Falling-platform warning is a one-shot route cue: once its spent state is secured,
  it remains spent until Restart Level. An optional pickup may be missed, while the
  mandatory exit route remains open. Manual acceptance should verify that this
  consequence is telegraphed fairly; this state change adds no new control or UI.
- Three-quarter lever [down/off](references/controls-lever-three-quarter-off.png)
  and [up/on](references/controls-lever-three-quarter-on.png), with local instructions
  and selection brackets. F2 invulnerability was used for presentation inspection.
- Current [three-slot fireball HUD](references/hud-fireball-capacity-debug.png)
  (#98, 2026-10-25): two bright charges and one dark slot, no ammunition number,
  separate debug FPS column. It supersedes the former icon-plus-number captures.
- Real desktop DevHost FPS at [startup](references/run-dev-fps-start.png)
  and [hot restart](references/run-dev-fps-hot-restart.png).

Browser checks included both handedness layouts, simulated multitouch, held items,
resize/letterboxing and release FPS absence without runtime errors. Physical
controller/phone and novice playtests remain separate acceptance work (#101, #131).
The original #95 artwork review omitted Snyk scanning at the author's instruction.
The later #98 scan found no new issues; only two existing LOW findings in unchanged
`tools/PortraitExport.java`.

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
ladder-zone physical-device acceptance remains with #101. Novice teaching
acceptance remains #131. These are not claimed complete by simulated tests.

<a id="ladders-99"></a>

## Ladders (#99)

Rules and rationale: [D28](DECISIONS.md#d28-vertical-ladders). Implemented 2026-10-25.

| Situation | Up / Down | Action | Ranged | Jump |
| --- | --- | --- | --- | --- |
| Beside rungs, on the ground or airborne | Up takes hold (climb) | Sword as usual | As usual | As usual |
| Grounded on the upper span or at the bottom | Up takes hold; Down does nothing | Sword as usual | As usual | As usual |
| Grounded on an intermediate platform at least one third down the ladder | Up or Down takes hold (climb); hint shows separate `Up: Climb` and `Down: Climb down` lines | Sword as usual | As usual | As usual |
| On the top surface | Down takes hold (descend); Up does nothing | Sword as usual | As usual | As usual |
| Attached | Up/Down climb at 70 px/s; Left/Right releases the ladder and moves normally; both ends step off | Does not swing; no pick up/put down | Throws a held object or spends a fireball; keeps the grip | Lets go with a 0.7× hop |
| Up/Down + Action, anywhere | Not climbing: Action owns the direction | Operates a mechanism in reach (Up) | | |

The direction held with an Action press stays spent until it is released, on the ground
and on the ladder, so a chord can climb or operate, never both, and releasing Action while
still holding Up does not climb. Up and Down together climb nothing. Keyboard (both
handedness layouts), gamepad movement stick/D-pad and the touch stick feed the same
input; the lesson text names the device's own label (`W`, `Left stick up`, `Stick up`).

Tests (`DirectionalActionsTest`, production Player/Level, simulated devices): climb to the
top and step onto the ledge, descend and step off, no regrab at the top, chord on the ground
and on the ladder with no climb after release, sword/pick up unavailable and ranged working
while attached, jump-off with direction, gamepad stick, touch stick with an Action touch,
and the first-room hint present only in the first Forest room. Not checked: a physical
controller, a physical multitouch phone, and novice comprehension.

**Accepted follow-up (2026-10-26):** The ladder top now has a one-way support that follows
the moving sprite, so reaching its top leaves Edgard standing there. A grounded midpoint
platform at least one third down the ladder accepts both vertical directions and shows separate `Up: Climb` and `Down: Climb down`
lines; the upper span and bottom accept Up, while the top surface accepts Down. Left/Right
releases the ladder, and the climbing animation advances only during vertical movement.
Eligible target outlines suppress
standing-look camera movement (D29). These follow-up changes have not yet been exercised by
the automated or physical-device checks recorded above.
