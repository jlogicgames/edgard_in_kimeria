---
name: kimeria-input-flows
description: Implements and reviews Edgard in Kimeria keyboard, gamepad and touch controls, handedness, and fresh-press handling across menus, gameplay, intro and healer dialogue. Use when adding actions, changing bindings or fixing held-input leakage across game states.
---

# Kimeria input flows

Work from the repository root. Documentation links are relative to this file;
source paths and commands are relative to the repository root.

## Find the input owner

Read controls and platform limits in [README](../../../README.md), D9 and D12 in
[design decisions](../../../docs/DECISIONS.md), and, for dialogue input,
[healer dialogue](../../../docs/HEALER.md). These are current rules; update the
applicable document when an explicit request changes them.

Under `core/src/main/java/com/jlogicsoftware/kimeria/`, inspect:
- `input/KeyBindings.java` for keyboard gameplay actions; `input/GamepadInput.java`
  for controller mappings, polling, edges and suppression after menu confirmation.
- `ui/TouchControls.java` for touch ownership, layout, hit areas and action labels.
- `entity/player/Player.java` and `entity/player/JumpBuffer.java` for held-to-edge
  jump handling, buffer, coyote time and resets.
- `KimeriaGame.java`, `ui/Menu.java` and `input/IntroInput.java` for state routing,
  menu input, opening/ending/dialogue fresh presses and suppression on entry/resume.

Trace the action from device polling through UI state to consumption. Gamepad
edges are updated once per frame before reading getters; do not poll again inside
a consumer. A menu mapping and a gameplay mapping may intentionally differ.

## Preserve action and transition semantics

- Movement is held; jump requires a fresh press with a short buffer and coyote
  time. Holding through landing never auto-jumps. Preserve reset/suppression when
  starting or resuming gameplay rather than clearing input on every frame.
- Action alone is sword; Down + Action picks up/places, Up + Action operates a
  mechanism. Ranged throws the held object or spends one fireball when empty-handed.
  Follow [directional controls](../../../docs/DIRECTIONAL_CONTROLS.md); proximity must
  never turn a sword press into interaction. Standing Up/Down alone selects a static
  look pose and delayed vertical inspection. Action, Ranged, horizontal movement,
  jump and climbing take priority. Preserve release suppression at UI boundaries.
- Default and left-handed gameplay mappings are disjoint. Use KeyBindings and
  generic controller mappings rather than scattering raw keys/button indices.
  Left-handed gamepad movement uses the right stick and D-pad actions; menu
  navigation remains unchanged. Changing handedness applies live.
- Mirror touch hit areas together with visuals and keep labels aligned. Check
  movement and action with simultaneous pointers, drag/release and state changes.
  The README records the missing gameplay touch pause button; do not claim all
  touch flows are complete or add that feature without it being in scope.
- A confirmation that opens a screen must not also jump, skip its opening/ending scroll, advance
  its first line or activate its initial choice. Snapshot held input at entry,
  ignore OS repeats where a fresh press is required, and require release/repress.
- Dialogue presses first reveal, then advance. Opening choices does not confirm
  them with the same event. Navigation wins over confirmation, including mirrored
  D-pad actions. Only Esc currently opens dialogue pause; Resume preserves the
  line/choice and suppresses held inputs. Follow HEALER.md rather than applying
  ordinary gameplay pause bindings to dialogue automatically.

## Verify the affected paths

Tests are under `core/src/test/java/com/jlogicsoftware/kimeria/`. Select relevant
existing suites, for example:
```sh
./gradlew :core:test --tests '*IntroInputTest' --tests '*JumpBufferTest' --tests '*MenuTest' --tests '*HealerDialogueTest'
```
For actual KimeriaGame dialogue pause/resume orchestration, also run
`*BossCombatTest`: HealerDialogueTest exercises the dialogue model, not the game
render loop or pause routing. Use the dedicated new-game confirmation flow when
checking an existing save, rather than assuming every menu Enter starts a run.

Add focused cases for changed behavior, such as held confirmation across entry,
release/repress, mode switching while held, pause/resume, or a navigation/confirm
conflict. For a new controller/touch path without existing coverage, test the
observable action and transition rather than a copy of its mapping constants.

Check affected devices in the running game in both handedness modes. Browser
controllers may appear only after their first button press. Verify mouse/touch
coordinates after resize and letterboxing when those paths change. Update About
text and action labels in all `assets/localization/*.json` catalogs when bindings
change; keep typed keys in `localization/Msg.java`. Follow
[localization](../../../docs/LOCALIZATION.md), run `validateLocalization` and
regenerate fonts when new glyphs are needed.
Report device checks actually performed; keyboard simulation does not prove
physical controller or multitouch behavior.
