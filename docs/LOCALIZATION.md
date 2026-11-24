# Translation catalogs and bitmap fonts

The same `assets/localization/*.json` files ship in desktop packages and the TeaVM
browser build. Existing messages can be translated without changing Java source.
`Msg` is the typed, stable key list; its enum names are the JSON keys.

| Catalog | Language | Persisted enum name |
| --- | --- | --- |
| `en.json` | English | `ENGLISH` |
| `uk.json` | Українська | `UKRAINIAN` |
| `pl.json` | Polski | `POLISH` |
| `de.json` | Deutsch | `GERMAN` |
| `be.json` | Беларуская | `BELARUSIAN` |

Language changes take effect immediately through existing menu label suppliers.
The saved preference continues to contain the enum name; old English/Ukrainian
preferences remain valid. First launch and an invalid preference use English,
independently of the system locale. Changing language does not rewrite run saves.

## Text style

Wording rules (tale voice for narration and the healer, plain imperative for hints, labels), length
budgets and the glossary of fixed names per language are in [TEXT_STYLE.md](TEXT_STYLE.md) (D74).
`python3 tools/check_text_style.py` (`./gradlew validateTextStyle`, part of `:core:check`) enforces them;
[TEXT_AUDIT.md](TEXT_AUDIT.md) lists where and when every string appears.

## Edit a translation

1. Edit the appropriate JSON file as UTF-8, with a flat object of string values.
   Keep keys unchanged and unique; use standard JSON, without comments or trailing
   commas. Use `\n` for a line break and `\n\n` between paragraphs. Quotes and
   backslashes in text need JSON escaping. Preserve intentional paragraphs.
2. Preserve named placeholders, such as `{count}` and `{total}` in
   `FOREST_COINS_COUNT`. You may reorder them. Their names and occurrence counts
   must match English. Braces are reserved for placeholders. The call site passes
   named values through `Msg.FOREST_COINS_COUNT.format(language, "count", "3", "total", "6")`.
3. Run the checks below. Regenerate the fonts if the new text requires new glyphs.
4. Inspect the actual game in the changed language, including both control layouts
   and help with a controller connected. In `runDev`, F7 forces catalogs to reload.

```sh
python3 tools/check_text_style.py
./gradlew validateLocalization
./gradlew :lwjgl3:bakeFonts
./gradlew :core:test
./gradlew buildDesktop buildWeb
```

`validateLocalization` rejects missing/unknown/duplicate keys, blank values,
malformed JSON, placeholder mismatches, invalid text characters and missing font
glyphs. Errors identify language/key (and JSON line/column for syntax errors).
Resource-level errors identify the resource rather than inventing a key.
The same strict reader is used by the game, validator and baker. Duplicate keys
are detected before map insertion, so they cannot silently overwrite a value.

Desktop JAR/native packaging and the web release-build task depend on this gate,
including the existing GitHub Actions workflows. It never regenerates tracked
fonts silently during a release build: run `bakeFonts` explicitly and review the
changed `.fnt` and `.png` files. Core tests additionally check failure cases,
runtime fallback, settings/menu integration and actual baked font layout metrics.

At runtime a missing/blank translation falls back to the English value. An
unreadable or malformed language catalog falls back as a whole. Diagnostics are
reported once per missing language/key. If English itself is unavailable, the
last-resort visible marker is `[KEY]`; release validation prevents shipping this
state. A language change selects an already loaded catalog, without disk IO in
the render loop.

## Message context

| Keys | Meaning to preserve |
| --- | --- |
| `CONTINUE` / `RESUME` | Load the saved level / unpause the current game. Keep the distinction. |
| `NEW_GAME_CONFIRMATION` | Starting over replaces the saved run; Cancel preserves it. |
| `HEALER`, `HEALER_*` | The healer is an old man, the recurring narrator/guide. Preserve the agreed story; do not introduce quests or places. |
| `INTRO_STORY` | The healer narrates Rufardus, L'Kardina's expulsion/curse and Edgard's departure. No ingredient tutorial here. |
| `INTRO_SKIP_KEYBOARD`, `INTRO_SKIP_GAMEPAD` | The small skip hint on the intro and the ending: the key (Esc) or the pad button (Start) that leaves the story, then the verb. Touch shows its icon only. |
| `ENDING_STORY` | The implemented test campaign's ending, not a new story branch. Told by the healer (#372): three paragraphs in every language, the second one (and only it) brings back the sun and the light, the third is the closing line in capitals (THE END, КІНЕЦЬ, KONIEC, ENDE, КАНЕЦ). Keep this structure: the ending page lights the arms with the light paragraph (#416). |
| `BAT`, `MUSHROOM` | Ingredient/item labels on the healer's checklist. `BAT` reads "Bat wing" (the jar item, not the enemy; #345). The coins are named by `FOREST_COINS_COUNT`; earlier-level coins do not satisfy this payment. |
| `FOREST_COINS_COUNT` | Checklist label with secured/authored forest coin counts. |
| `HEALER_READY`, `HEALER_PAID`, `PAID` | Ready to brew / payment completed and elixir already drunk. Physical sword damage is tenfold against this boss only; magical fireballs always remove 2 energy. The spoken lines say this in the tale voice (#371): the sword strikes the guardian "ten times harder", the healer's fire "burns no hotter"; translate with the glossary forms and no register word. |
| `HEALER_RETURN`, `HEALER_FIGHT` | Return for missing pickups / commit to the boss fight, with no return once it starts. |
| `ABOUT_BODY*`, `ABOUT_GAMEPAD*` | Preserve exact key/button bindings for normal and left-handed modes. Key names such as J, K, L, Z, X, C identify physical controls. |
| `HINT_*` (all zone messages) | **Size (D49, #249):** zone messages are drawn in the 20 px font, 400 px wide, at most two lines in every language (`LocalizationLayoutTest` counts wrapped lines); the label above the hero is 16 px (D70), 200 px wide (D50 reverted D49's 39/20 px; the uk and be `HINT_TRAP_PLATFORM` D49 had shortened is back to its full wording, which fits two lines). |
| `HINT_TUTORIAL_WALK` | Map-authored `Hint` zone message in `forest-0` room 0, shown at the zone with the existing hint fade/slide; avoid device-specific key names. |
| `HINT_LEVER_TORCH`, `HINT_LEVER_EXIT` | Lever lesson hints in `forest-0-lever` (#186): lever and platform (state-independent; the keys keep their torch-era names, D127) / exit ahead, true whether or not the lever was used. No key names (the controls zone supplies the live Up label); at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_LEVER_BOTH` | Lever hint in `forest-1-lever` (#554, D131): one lever moves both the ladder and the platform, true in every state of the room (D33, D48; D97 lever form, the same verb "move" in every language; "platform" is the glossary word for the lever-driven moving platform). A message-only zone over the whole room, so no key names; at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_LADDER_LEVER`, `HINT_LADDER_CLIMB`, `HINT_LADDER_EXIT` | Ladder lesson hints in `forest-0-ladder` (#196): use the lever to lower *or raise* the ladder / climb it when it is down / head for the exit ahead (D97 formula). Position-only zones, so each text is true with the ladder up or down. No key names (the controls zone supplies the live `W: Turn on - Lever` and `W: Climb` labels); at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_WALL_LEVER`, `HINT_WALL_PASS` | Wall lesson hints in `forest-0-wall` (#197): use the lever to raise *or lower* the wall / walk under the wall once it is raised. Position-only zones, so each text is true with the wall up or down. No key names (the controls zone supplies the live Up label); at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_PLATFORM_LEVER`, `HINT_PLATFORM_RIDE` | Platform lesson hints in `forest-0-platform` (#198): use the lever to start *or stop* the moving platform / jump on it and ride up to the exit. Position-only zones, so each text is true with the platform running or stopped. No key names (the controls zone supplies the live Up label); at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_STONE_THROW`, `HINT_STONE_EXIT`, `HINT_STONE_ACROSS` | Stone lesson hints in `forest-0-stone` (#199): carry the stone to the chasm and throw it at the lever / hit the lever with the stone, then ride the platform across (shown from the stone to the far lip) / head for the exit ahead (far side). Position-only zones, so each text is true with the platform running or stopped, the stone in hand or not. No key names (the controls zone supplies the live `W: Pick up - Stone` and the put-down/throw reminder); at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_TRAP_PLATFORM`, `HINT_TRAP_STUMP` | Traps lesson hints in `forest-0-traps` (#200): a cracked platform falls soon after you land, cross quickly (D97: the cue, then what to do) / a touched stump smokes then explodes, so jump it or run clear (D47: `HINT_TRAP_BOMB` was renamed when the exploding stump replaced the glass-bottle bomb). Position-only zones that butt together (D40), each true wherever it shows. No key names (no controls zone in this room); at most two lines at the 400 px zone-message width in the 20 px font (D50), checked in `LocalizationLayoutTest`. |
| `HINT_LADDER_ROOT`, `HINT_WALL_BRANCH`, `HINT_PLATFORM_STONE`, `HINT_STONE_STUMP` | One trap hint per middle tutorial room (#235, D48): thorns rise when you come near, jump over them / a cracked branch falls just after you pass, keep moving / a shadow shows where a stone will fall, step out of it / this stump explodes when touched, throw a stone or jump it (the stone room introduces lighting it from afar; D97 formula). Position-only zones that butt against the room's lever hint (true in every state; the trap hint comes first in the ladder, wall and platform rooms and lasts past the trap's column); no key names; at most two lines at 200 px, checked in `LocalizationLayoutTest`. The de, pl, uk and be texts are drafts, like the other hints. |
| `HEALER_TALE_ROAD`, `EDGARD_TALE_NO_TIME`, `HEALER_TALE_FAIRIES`, `HEALER_TALE_LIFT`, `HEALER_TALE_WITCH`, `HEALER_TALE_FAIRY`, `EDGARD_TALE_UNDERSTOOD` | The healer's start tale before forest-0 (#220, D41): the road as a fairy tale, rewritten in tale voice by #370: opening formula "Listen, Edgard", old handles (the levers, which tale voice may not name) that wake a torch, let down a ladder, move a wall and send a boat of the air up to the ledge, a stone sent like a wish, and the witch's gifts in three groups covering the five traps, and `HEALER_TALE_FAIRY` (#338, D82): the healer's third gift, the fairy who lights what Edgard reaches for; one fairy, so "fairy" is singular everywhere with no control or mechanic named, closed by Edgard's "I understand, old man, now let me go" (he is in a hurry). Speaker by key prefix; measured against the baked font and the D78 text box by `HealerDialogueTest`. |
| `HEALER_FIRST_DEATH` | The healer after Edgard's first death in a run (#341, D81): night swallowed him, the healer drew the silver thread, he rises at the path's edge, "Go on" (the formula of `HEALER_GUIDE`). Glossary form of "silver thread"; no em dashes, no new glyphs. Native-speaker review of de/pl/uk/be: [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369). |
| `HEALER_TALE_PRAISE`, `EDGARD_TALE_READY`, `HEALER_TALE_HEROES`, `HEALER_TALE_BLESSING` | The farewell at the end of forest-0 (#220, D41): praise, Edgard's "Forgive my haste, healer. Am I ready for the road, then?" (#370), and a parting blessing closed by the formula "Go on" (per-language forms in TEXT_STYLE.md section 2). Same rules; no em dashes, no new glyphs. Native-speaker review of de/pl/uk/be: [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369). |
| `FIREBALL_STOCK_FULL` | Local full-ammunition status at a world pickup, in every room; existing hint fade/slide. |
| `ATTACK`, `INTERACT`, `SHOOT`, `PICK_UP`, `PUT_DOWN`, `THROW`, `FIREBALL`, `NO_AMMO`, `PLACE_BLOCKED` | Compact action labels (the touch Action button always shows Sword since D126; the others are the hover labels above the hero). Up selects the nearest pickup or mechanism; Down only places a held item. Ranged means object throw or consumable fireball. About text and all five catalogs describe the same bindings. |
| `CLIMB`, `CLIMB_DOWN` | Ladder hint verbs inside a map `controls` hint zone (D33), shown after the Up/Down input: take hold and climb up, or step onto a ladder from its top edge. |
| `ABOUT_RULES` | One-hit death, unlimited room retries, provisional pickup rollback and retained secured pickups. |
| `ASSIST` | The Options entry and the title of the accessibility menu (renamed from Assist, #533, D116); it holds the longer trap warnings, screen shake and instant text. Glossary row: TEXT_STYLE.md, section 4. |
| `LONGER_TRAP_WARNINGS` | The assist option extends trap warnings; it does not grant invulnerability. |
| `INSTANT_TEXT` | The assist option that shows dialogue lines whole instead of letter by letter (#342); shown as `<label>: On/Off` in the 440 px Accessibility menu, checked in `LocalizationLayoutTest`. Label register (TEXT_STYLE.md): noun phrase, no hint text. The de, pl, uk and be texts are drafts. |
| `SCREEN_SHAKE` | The assist option for camera shake (boss feedback and the lever-driven wall); shown as `<label>: On/Off` in the 440 px Accessibility menu (D62), checked in `LocalizationLayoutTest`. The de, pl, uk and be texts are drafts. |

The #95 binding/target prompt keys include `ACTION`, `STONE`, `TORCH`, `MECHANISM`,
`UP`, `DOWN`, `STICK`, `LEFT_STICK`, `RIGHT_STICK`, `DPAD_UP` and `DPAD_ACTION`.
See [directional controls](DIRECTIONAL_CONTROLS.md) for exact bindings and test scope.

## Glyph coverage and uppercase

All localized rendering currently goes through `Assets.font(size)` and the baked
**QuestSquare** fonts at **13, 15, 20 and 30 px**, including menu/body/hint fonts,
HUD, boss HUD and touch controls. FreeType is used only by offline tooling, never
by the shipped desktop or browser game.

`FontCharacters.required` derives a deterministic, deduplicated charset from all
catalog values, native language names and their uppercase forms. It also retains
ASCII for counters/controls/selection markers, the full Belarusian alphabet
(especially `ІіЎўЁё`) and all Polish/German special letters even if a current
translation happens not to use one. Intentional new static UI symbols must be
included here or expressed through a message catalog.

Both bundled TTFs lack uppercase **ẞ**. German uppercase text intentionally uses
**SS**: `FontCharacters.uppercase("Straße")` gives `STRASSE`. Normal text retains
**ß**. Use that helper if introducing uppercase UI transformations; never call
locale-dependent uppercase directly on localized text. Literal `ẞ` in a catalog
is rejected by source-font coverage until a suitable glyph is supplied.

`QuestSquare.ttf` covers the required Polish/German/Belarusian characters.
`NanoPlus.ttf` is bundled but is not used to render localized text; it lacks
Belarusian `Ё`, `І`, `ў` and must not replace QuestSquare without supplying those
glyphs or a complete fallback. The existing Y-up text rendering path is retained.

The gate checks the TTF's actual character map and every required glyph in each
shipped `.fnt`, not merely a replacement character returned by a renderer. It
loads the referenced PNG pages and checks page bounds and nontransparent ink for
each nonspace glyph. Missing, blank or unsupported glyphs fail the build with
language/key context. Glyph tests include uppercase forms.

## Add a key or language

For a new message, add its stable key to `Msg`, add every translation to all five
catalogs and use the typed API at the call site. A new key without translations
fails validation. Put new help/dialogue text through this path too.

For a new language, append an enum entry with its catalog code/native name to
`Language`, add a complete catalog, update the supported-language documentation
and regenerate fonts. Never rename existing enum values: they are persisted.
The selector cycles through `Language.values()`. The validator and baker discover
the new language through the same list. Check actual layouts as well as glyphs:
longer text may need UI changes even when every character exists.

See [validation evidence](LOCALIZATION_VALIDATION.md) for the scope of #85 checks.
