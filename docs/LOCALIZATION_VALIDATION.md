# Localization #85 validation — 2026-10-21

## Automated checks

`./gradlew :core:test validateLocalization buildDesktop buildWeb` passes:
**141 tests**, no failures/errors/skips, desktop JAR and TeaVM release build.

- All five catalogs contain all 62 typed keys. All 122 original EN/UK values
  (61 per language) were compared against the pre-migration extraction and match
  exactly, including paragraph breaks. The added forest-coin count template
  preserves the previously composed output while exercising named placeholders.
- Tests cover strict JSON escapes and malformed syntax, duplicate/unknown/missing
  keys, blank values, placeholder mismatches/counts, malformed placeholders,
  invalid characters, runtime English fallback and diagnostic deduplication.
- Five-language settings round trips preserve existing enum names, invalid-value
  English defaults and run saves. The actual Options language action updates
  labels immediately and persists each choice. Existing dialogue/campaign tests
  also run their language loops over all five languages.
- Every shipped 13/16/18/20/30 font contains the 192 required glyphs, including
  uppercase forms and the reserved Polish/German/Belarusian alphabets. The TTF
  character map, bitmap IDs, referenced PNG pages, glyph bounds and visible ink
  are checked. German uppercase SS conversion has a regression test.
- Real baked-font metrics check titles, buttons/selection-marker space,
  confirmation/rules/help bounds, both controller help layouts, all healer lines
  and choices, native names and wrapped touch labels. These are metric checks,
  separate from the rendering observations below.
- Negative integration checks on disposable copies rejected a removed bitmap
  `ł`, NanoPlus substituted for QuestSquare (missing Belarusian glyphs), and
  an unsupported snowman added to the German TITLE. None of these fixtures or
  exceptions is part of the release gate or shipped assets.

## Desktop renderer observations

A temporary hidden-window LWJGL harness rendered 70 frames through the actual
game at 640x360 logical resolution, using isolated temporary preferences and
disabled audio. For each of EN/UK/PL/DE/BE it captured the main menu, Options,
Assist, new-game confirmation, both keyboard/controller help layouts, rules,
missing-recipe offer, ready/paid healer lines, post-boss acknowledgement,
opening, ending and on-screen controls. Contact sheets were visually inspected:
text was upright and fit the tested regions without replacement glyphs or
clipping. Full paragraphs and checklist counts appeared in their intended places.
The simulated controller made controller help visible; this was not a physical
controller interaction test.

A second temporary render pass checked the Interact touch label in normal and
mirrored layouts for all five languages (10 more frames). The Belarusian word
fits on one line inside the action button; the smaller Shoot button uses its own
text width. No physical touch device input is claimed.

The actual language-toggle action selected each language. A separate process
restart restored BELARUSIAN from the test desktop preferences. All-five persistence
is additionally covered by settings tests and the browser check below. Normal
player preferences/progress were not modified.

## Browser renderer/storage observations

The minified TeaVM release build was served on a temporary localhost origin and
exercised in Chromium. All five languages switched immediately and stored their
existing enum-name format in the real browser preference backend. Reloading the
page restored each selection. An explicitly invalid saved language displayed
English. These checks used a separate origin from the player's hosted game.

Actual browser captures for all five languages covered Options, normal and
left-handed keyboard help, rules, the scrolling opening, the pre-boss healer
greeting and the return/fight choices. Developer F6 room skips reached the healer;
these checks establish rendering, not a human playthrough of the forest. The
German dialogue pause overlay was also captured. The five-language contact
sheets were inspected: line wrapping, diacritics, Belarusian letters, checklist
counts and choice labels were readable and stayed inside their regions. No
page execution errors occurred in the successful verification runs.

Temporary harness code, modified fixtures, server, test preferences and browser
storage were removed after verification. Review contact sheets were inspected as
ignored local build artifacts under `build/localization-checks/`, then removed
during the author's requested post-merge cleanup. They are not committed or
packaged in releases; no retained screenshot artifact is claimed.

## Remaining human/device review

New Polish, German and Belarusian wording should receive native-speaker review.
The #228 silver-thread strings (`HEALER_TALE_THREAD` and the added sentence in `INTRO_STORY`) are
machine-quality drafts in all four non-English catalogs and belong to that review (UK and BE
included). Metrics: the new line wraps to 3 lines / 37 px in all five languages (limit 72 px).
The #340 fire-gift strings (`HEALER_TALE_FIREGIFT` and the added sentence in `INTRO_STORY`) are
machine-quality drafts in uk, be, de and pl and join that review, tracked in
[#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369). Metrics (QuestSquare-18, 370.67 px
region, limit 72 px): 4 lines in all five languages, widest line EN 363, UK 365, PL 367, DE 365, BE 356 px
(about 66 px high by font metrics; `HealerDialogueTest` asserts the 72 px limit). The added `INTRO_STORY`
sentence only lengthens the scrolling opening text.
The #338 fairy strings (`HEALER_TALE_FAIRY`, and the fairy words changed in `HEALER_TALE_FAIRIES`,
`HEALER_TALE_LIFT` and `HEALER_TALE_BLESSING`) are machine-quality drafts in uk, pl, de and be and join the same
review ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369)). `HealerDialogueTest` fits every line in the D78 text box in all five languages.
The #370 tutorial-tale rewrite (`HEALER_TALE_ROAD`, `HEALER_TALE_FAIRIES`, `HEALER_TALE_LIFT`, `HEALER_TALE_WITCH`,
`EDGARD_TALE_READY`, `HEALER_TALE_BLESSING`) is likewise a machine-quality draft in uk, pl, de and be and joins that review.
The #239 `SCREEN_SHAKE` label (Options -> Assist, "Screen shake") is likewise a machine-quality draft in
uk, be, de and pl and belongs to that review; "<label>: Off" fits the 440 px Assist menu in all five
languages (`LocalizationLayoutTest`).
The checks above do not claim literary approval or testing on physical touch
devices/gamepads, Safari/Firefox or Windows/Linux graphics backends. Existing
release/device checks remain in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).
The browser post-boss and ending flows were not replayed in this localization
check; their text was rendered on desktop, measured with the shared fonts, and
covered by the existing campaign/dialogue tests.

The local source/dependency review found no added dependencies, external network
paths or user-input file paths. A Snyk scan was not run: its scanning tool was
not available in this session. This is not a claim of a passed external scan.

## D70 font-size check (#335, 2026-11-16)

Sizes are now named in `ui/FontSizes` (label and body 16, dialogue 18, touch label 13, zone/button/coin 20, title 30);
`FontCharacters.SIZES` is {13, 16, 18, 20, 30} and `FontSizesTest` keeps the two in step.
`./gradlew :core:test :lwjgl3:validateLocalization` passes: 5 languages, 121 keys, 192 glyphs at every shipped size.
Measured with the real baked metrics (all five languages): the longest healer/Edgard line takes 4 lines, 62 px of the
72 px dialogue box; the controller-help About panel is 327 px of 360 (the existing 332 limit is unchanged); the hero label
stack stays 3 lines (32 to 39 px); the menu hint stays one line; touch labels are unchanged at 13 px because 15/16 px
overflow the 50 px (40.4 px) button face in uk, de and be by 2 to 3 px. Rendered check of the web release build (English): menu, About, intro, healer dialogue, forest-0 hint and label; other languages, boss HUD, touch buttons, desktop build and the three-choice dialogue are measured, not rendered (#368).
