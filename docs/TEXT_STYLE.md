# Text style: voices, limits and glossary for player-facing strings

**Status: accepted by the author, 2026-11-16, D74 ([#339](https://github.com/jlogicgames/edgard_in_kimeria/issues/339),
from the draft in [TEXT_AUDIT.md](TEXT_AUDIT.md#4-draft-style-system-not-approved)).** These rules bind every
new or changed string in `assets/localization/*.json`. Shipped strings that break them are listed in the
gate's allowlist (`tools/text_style.json`), each entry tied to the rewrite issue that removes it; see
[the audit](TEXT_AUDIT.md) for the findings.

Related: [STORY.md](STORY.md) (world facts and the healer's role), [LOCALIZATION.md](LOCALIZATION.md)
(catalog format, per-key context, fonts), [LOCALIZATION_VALIDATION.md](LOCALIZATION_VALIDATION.md)
(technical checks), D33/D49/D50 in [DECISIONS.md](DECISIONS.md) (hint sizes).

## 1. Three voices, never mixed in one string

| Voice | Keys | Rules |
| --- | --- | --- |
| **Tale** | `INTRO_STORY`, `ENDING_STORY`, every spoken `HEALER_*` / `EDGARD_*` line (not the buttons `HEALER_RETURN`, `HEALER_BREW`, `HEALER_FIGHT`, `HEALER_HINT`) | Story words only: no control, key, button or mechanic name and no game jargon (boss, retry, checkpoint, lives, damage, list, level, HUD). Past tense for narration, present for speech. The healer addresses Edgard by name at the start of a scene. Numbers only where the tale itself counts ("ten times", "three gifts"). The healer speaks as himself; he never narrates the player's action as a system message ("You drink the elixir"). |
| **Plain** | `HINT_*` zone messages, `MENU_HINT`, `ABOUT_*`, `ROTATE_HINT`, `NEW_GAME_CONFIRMATION` | Imperative or a short statement of what happens. One idea per string, at most two short sentences. No tale imagery and no character names. Zone messages name no key or button: the label above the hero supplies the live binding. Zone messages follow one formula (section 1.1, [D97](DECISIONS.md#d97-zone-messages-follow-one-formula-the-cue-then-what-to-do-373-2026-11-17)). |
| **Label** | Menu items, buttons, the label above the hero, HUD words | One to three words. Verb first for actions ("Pick up", "Put down"), a noun for targets ("Lever", "Torch"). One concept has one label everywhere (glossary below). |

### 1.1 The zone-message formula ([D97](DECISIONS.md#d97-zone-messages-follow-one-formula-the-cue-then-what-to-do-373-2026-11-17))

Every zone message ends with one imperative that says what to do. It may start with one short statement of what
the player sees and what follows. At most two sentences and 60 EN characters. Fixed forms:

| Kind | Form | Example |
| --- | --- | --- |
| Lever | "Use the lever to `<do X>` or `<undo it>`" ("Use" is the action label) | Use the lever to raise the wall or lower it |
| Trap | "`<the cue>` `<what follows>`. `<how to get past>`", the cue named as [TRAPS.md](TRAPS.md) draws it | A shadow shows where a stone will fall. Step out of it |
| Exit | "Head for the exit ahead", one wording everywhere | Head for the exit ahead |

Present tense; no retry, lives or key words; no tale images; one verb per trap (the stump "explodes"). The length is
checked by T3; the shape is a review rule.

## 2. Tale craft

- **Formulae.** The healer opens a tale with a fixed opening and closes it with a blessing or a sending-off
  (EN: opening "Listen, Edgard", closing "Go on"). Each language uses its own traditional equivalent,
  fixed here when the rewrite settles it, not a word-for-word translation. Settled by the tutorial tales
  ([#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370)) and the first-death line (#341): opening UK
  "Слухай, Едгарде", PL "Słuchaj, Edgardzie", DE "Höre, Edgard", BE "Слухай, Эдгардзе"; closing UK "Рушай",
  PL "Ruszaj", DE "Geh weiter", BE "Рушай". The ending ([#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372))
  is the healer's tale too: it opens "Hear how my tale ends" and closes with the tale's own ending formula, EN "So ends my
  tale of Kimeria", UK "Тут і казці про Кімерію кінець", PL "I tak kończy się moja opowieść o Kimerii", DE "So endet meine
  Geschichte von Kimeria", BE "Тут і казцы пра Кімерыю канец", followed by the closing line (THE END) in capitals.
- **Three where it fits.** Three gifts, three warnings, three images in a list; the third is the
  strongest or breaks the pattern.
- **Show what the tale names.** A helper or magical object the tale names is visible in play, or the
  tale marks it as not yet met. Fairies ([#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338)),
  the fire ([#340](https://github.com/jlogicgames/edgard_in_kimeria/issues/340)) and the thread
  ([#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341)) follow this rule.
- **True in play.** Every mechanic the healer explains matches the game (STORY.md). An image that
  stands for a room's object names that object's kind (a stump is not a berry).
- **Propp as a checklist, not a template.** The function map in [TEXT_AUDIT.md](TEXT_AUDIT.md#2-the-story-against-propps-functions-and-roles)
  shows which functions a scene fills; a new scene names the function it adds.

## 3. Measurable limits

| Channel | Limit | Enforced by |
| --- | --- | --- |
| Zone message (`HINT_*`) | 20 px font, 400 px wide, at most two lines in every language (D50, sizes in `ui.FontSizes`, D70) | `LocalizationLayoutTest` |
| Zone message source | EN at most 60 characters and at most two sentences, leaving room for translations (German runs about 25 % longer); the formula of section 1.1 (D97) | `tools/check_text_style.py` (T3); the formula in review |
| Label above the hero | 16 px font (D70), 200 px wide (D50); each label at most three words | `LocalizationLayoutTest`; review |
| Dialogue line | 18 px font (D70); fits the speaker region and frame height; EN at most 160 characters, one image or one instruction per line | `HealerDialogueTest`; review |
| Opening and ending | Per page (paged screens, [#336](https://github.com/jlogicgames/edgard_in_kimeria/issues/336)): at most three paragraphs, EN at most 300 characters | Review |

## 4. Glossary

One form per concept per language. The machine-readable copy (stems, avoided forms) is
`tools/text_style.json`; the gate checks that every form below is listed here (T6) and that a concept named
in the English source is named in each translation (T1).

| Concept | EN | UK | PL | DE | BE | Avoid |
| --- | --- | --- | --- | --- | --- | --- |
| Hero | Edgard | Едгард | Edgard | Edgard | Эдгард | |
| Kingdom | Kimeria | Кімерія | Kimeria | Kimeria | Кімерыя | |
| Villain | L'Kardina | Л'Кардіна | L'Kardina | L'Kardina | Л'Кардына | the typographic ’ (catalogs use the ASCII apostrophe) |
| King | Rufardus the Wise | Руфардус Мудрий | Rufardus Mądry | Rufardus der Weise | Руфардус Мудры | |
| Donor | healer | знахар | znachor | Heiler | знахар | "old man" outside Edgard's speech |
| Witch (common noun) | witch | відьма | wiedźma | Hexe | ведзьма | pl "czarownica" |
| Boss in the tale and as a HUD label | forest guardian | лісовий вартовий | strażnik lasu | Waldwächter | лясны вартавы | "boss" in spoken lines. As the HUD label the same name is capitalised in the catalogs ("Forest Guardian", "Лісовий вартовий", "Strażnik lasu", "Waldwächter", "Лясны вартавы"): playtest F30, [#542](https://github.com/jlogicgames/edgard_in_kimeria/issues/542) |
| Silver thread | silver thread | срібна нитка | srebrna nić | silberner Faden | срэбная нітка | |
| Ranged magic | Fireball | Вогонь | Ogień | Feuer | Агонь | a second EN name ("Ranged", "fireballs"). #340 kept these labels and named the fire the healer's gift (`HEALER_TALE_FIREGIFT`); the remaining mismatches (EN "Fireball" against "Fire", be "вогненных шароў") are reconciled in [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369) |
| Elixir | elixir | еліксир | eliksir | Elixier | эліксір | |
| Healer ingredient (the bat) | Bat wing | крило кажана | skrzydło nietoperza | Fledermausflügel | крыло кажана | bare "Bat" for the ingredient: it is the name of the enemy (playtest F19, [#345](https://github.com/jlogicgames/edgard_in_kimeria/issues/345)) |
| Fairy | fairy | фея | wróżka | Fee | фея | confirmed by [#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338) (D82): one fairy, the healer's gift |
| Lever in the tale | old handle | руків'я | rękojeść | Griff | ручка | 'lever' in spoken lines |
| Accessibility menu (Options entry and title, #533, D116) | Accessibility | Доступність | Dostępność | Barrierefreiheit | Даступнасць | "Assist", "Допомога", "Ułatwienia", "Spielhilfen", "Дапамога" (the old label) |

The other ingredient and coin labels (`MUSHROOM`, `FOREST_COINS_COUNT`) keep their catalog forms; a later
change to them updates this table. The key `BAT` keeps its name; only its text became "Bat wing" (#345).

## 5. Keys

- Zone messages: `HINT_<ROOM>_<LESSON>` (`HINT_LADDER_CLIMB`), authored as the `message` property of a
  `Hint` object in the map.
- Dialogue: `HEALER_<SCENE>_<BEAT>` and `EDGARD_<SCENE>_<BEAT>`; the `EDGARD_` prefix selects Edgard's
  portrait (`HealerDialogue.edgardSpeaking`).
- Labels: the bare noun or verb (`LEVER`, `PICK_UP`).
- Every key is read by code (`Msg.KEY`) or authored in a map; an unread key fails T5.

## 6. Workflow for a new or changed string

1. Find where it appears and pick its voice (section 1).
2. Write the English text only. Check it against the voice, the limits and the glossary. Show it to the
   author and wait for approval.
3. Translate into uk, pl, de and be with the glossary forms; add or update the key's row in
   [LOCALIZATION.md](LOCALIZATION.md#message-context).
4. Run the checks:

   ```sh
   python3 tools/check_text_style.py
   ./gradlew validateLocalization
   ./gradlew :core:test
   ```

   Rebake fonts (`./gradlew :lwjgl3:bakeFonts`) only when new glyphs are reported.
5. Report what was measured (zone message lines per language) and what was not checked: native-speaker
   review and the look in the running game.

## 7. The gate

`tools/check_text_style.py` (test: `tools/test_check_text_style.py`; Gradle: `validateTextStyle`, which first runs that
test as `testTextStyleGate`, a dependency of `:core:check`, the desktop jar and the web release build):

| Check | Rule |
| --- | --- |
| T1 | A glossary concept named in the English source is named in every translation; avoided forms never appear |
| T2 | Tale-voice keys contain no term of the register groups (controls, lives, respawn, retry, checkpoint, damage, boss, list); the word lists are per language; stems match at the start of a word (inflections match, "alive" does not trip "live"), listed words only as whole words |
| T3 | English `HINT_*` within 60 characters and two sentences (the length part of D97's formula) |
| T4 | No zone message names a key (a binding token followed by `+`, `:` or `/`) |
| T5 | Every catalog key is read by code or authored in a map |
| T6 | Every glossary form appears in this document |

Procedure for agents: `.agents/skills/kimeria-game-text/SKILL.md` (Claude pointer `.claude/skills/kimeria-game-text/SKILL.md`).
`HealerDialogueTest` reads the same register groups from `tools/text_style.json` for the tutorial tales,
which have no allowlist. Pixel limits stay in the Java layout tests.

**Allowlist policy.** An entry in `tools/text_style.json` suppresses one finding (one key, one language, one
term) and names the open issue (`#NNN`) that removes it and what the shipped text carries (usually an author-approved wording awaiting the rewrite).
An entry that matches nothing fails the gate, so the list shrinks as the rewrite lands. Adding an entry
for new text is not allowed: fix the text (AGENTS.md, gate exemptions).
