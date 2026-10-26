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
| `ENDING_STORY` | The implemented two-level test campaign's ending, not a new story branch. |
| `CHICKEN`, `KEY`, `COINS` | Ingredient/item labels and coins from the current forest. Earlier-level coins do not satisfy this payment. |
| `FOREST_COINS_COUNT` | Checklist label with secured/authored forest coin counts. |
| `HEALER_READY`, `HEALER_PAID`, `PAID` | Ready to brew / payment completed and elixir already drunk. Physical sword damage is tenfold against this boss only; magical fireballs always remove 2 energy. |
| `HEALER_RETURN`, `HEALER_FIGHT` | Return for missing pickups / commit to the boss fight, with no return once it starts. |
| `ABOUT_BODY*`, `ABOUT_GAMEPAD*` | Preserve exact key/button bindings for normal and left-handed modes. Key names such as J, K, L, Z, X, C identify physical controls. |
| `FIREBALL_STOCK_FULL` | Local full-ammunition status at a world pickup, in every room; existing hint fade/slide. |
| `ATTACK`, `INTERACT`, `SHOOT`, `PICK_UP`, `PUT_DOWN`, `THROW`, `FIREBALL`, `NO_AMMO`, `PLACE_BLOCKED`, `DIRECTION_CONFLICT` | Compact directional-action touch labels. Current runtime: Plain Action means sword; Up/Down selects mechanism or pickup/place. For requested #175 behavior, Up selects a pickup or mechanism and Down only places a held item; update labels, About instructions and all five catalogs when implementing it. Ranged means object throw or consumable fireball. |
| `CLIMB`, `CLIMB_DOWN` | First-room ladder hint verbs, shown after the Up/Down input: take hold and climb up, or step onto a ladder from its top edge. |
| `ABOUT_RULES` | One-hit death, unlimited room retries, provisional pickup rollback and retained secured pickups. |
| `LONGER_TRAP_WARNINGS` | The assist option extends trap warnings; it does not grant invulnerability. |

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
