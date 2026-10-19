---
name: kimeria-progress-settings
description: Implements and reviews Edgard in Kimeria persisted options, level-only saves, Continue/New Game and preference compatibility. Use when adding a setting, changing save validation or menu behavior, or fixing desktop/browser persistence without conflating it with in-memory room rollback.
---

# Kimeria progress and settings

Work from the repository root. Documentation links are relative to this file;
source paths and commands are relative to the repository root.

## Read and trace the persistence boundary

Read D6, D8, D9 and D11 as relevant in
[design decisions](../../../docs/DECISIONS.md), and Game state, progress and saves
in [README](../../../README.md). For elixir lifetime consult
[healer](../../../docs/HEALER.md). Explicitly requested persistence changes may
revise these defaults; update the corresponding rule and compatibility behavior.

Under `core/src/main/java/com/jlogicsoftware/kimeria/`, inspect:
- `Settings.java`: typed Option keys, defaults, decoders, loaded snapshot and
  per-key writes, shared Preferences name and next-level key.
- `SavedProgress.java`: index validation, reload, save, clear and save existence.
- `KimeriaGame.java`: StartData, Continue/New Game confirmation, menu refresh,
  settings application and level completion.
- `ui/Menu.java` and `ui/MenuItem.java`: visibility, selection and navigation.
- `ArtifactInventory.java`: run-only state; it is not a persistence backend.

For startup display options also inspect
`lwjgl3/src/main/java/com/jlogicsoftware/kimeria/lwjgl3/Lwjgl3Launcher.java`:
the desktop launcher must read settings before Gdx.app exists.

## Change only the intended state

- Settings and saved progress share one Preferences file with separate keys.
  Use the typed Settings.Option path for new persisted options. Update only the
  changed key and flush; clearing a run removes only KEY_NEXT_LEVEL. Never clear
  the whole file or rewrite the Settings snapshot over a newer save.
- Boolean decoders accept backend boolean values and valid boolean strings;
  invalid values use the defined fallback. Language stores enum names and
  defaults to English independently of system locale. Settings read failure
  falls back; failed writes keep the in-session choice and report the error.
  SavedProgress has a different error policy: do not silently convert a failed
  save/clear into success just to match settings handling.
- Apply settings to the running feature and restore them on startup. Fullscreen
  persists the actual resulting mode, is desktop-only, and changes outside the
  render pass; window size/position are not currently saved. Assist toggles are
  independent and carry no reward penalties. Localize option labels EN/UK.
- Saved progress is a zero-based next-level index only. Validate against the
  current level count; missing, malformed or out-of-range data hides Continue
  rather than clamping to an unrelated level. The MVP order is `forest` then
  `forest-1`: save index 1 after the boss exit and final guide advance. The final
  level opens the ending; clear only the run save after that scroll finishes/skips,
  then return to the menu without Continue. Never wrap/save index zero. See
  [MVP](../../../docs/MVP.md) for interruption and legacy-index behavior.
- Death, room transitions, collection return and Restart Level do not save.
  Coins, inventory, collection identities, boss/elixir and encounter state remain
  in memory; Continue starts the saved level with zero coins and no elixir.
- Continue is hidden until a valid save exists and preselected when one exists.
  Visibility must affect layout, rendering, mouse hit areas and keyboard/gamepad
  navigation. Reload save state when showing the main menu.
- New Game over a save defaults its confirmation to Cancel. Cancel preserves the
  old run; Confirm clears it before the intro. No replacement save exists until
  first level completion. Resume unpauses the live run and is not Continue.

## Verify persistence and menu integration

Use existing suites under `core/src/test/java/com/jlogicsoftware/kimeria/`:
```sh
./gradlew :core:test --tests '*SettingsTest' --tests '*SavedProgressTest' --tests '*MenuTest' --tests '*CampaignFlowTest'
```
Add focused assertions for changed defaults/decoders, restart round trips,
malformed data, storage failures, saving while retaining options and changing
options while retaining the save. Test New Game cancel/confirm and menu selection
when their orchestration changes, not just the underlying preference methods.
Do not assume preference mocks prove that the real UI is wired correctly.

For startup/storage changes, relaunch the affected desktop/browser build and
verify the selected setting or Continue behavior through the actual backend.
Use isolated test preferences or a separate browser profile for destructive
save/reset cases; preserve the user's normal game progress. Browser storage is
local to that browser/origin; no cloud sync or mobile module is implemented.
Report actual backend checks separately from unit-test results.
