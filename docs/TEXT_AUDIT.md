# Text audit: every player-facing string, its trigger and its place in the tale

**Status: audit written 2026-11-15; style system accepted by the author 2026-11-16 (D74, [TEXT_STYLE.md](TEXT_STYLE.md))
([#339](https://github.com/jlogicgames/edgard_in_kimeria/issues/339), playtest F5,
[PLAYTEST_1.md](PLAYTEST_1.md), [D66](DECISIONS.md#d66-first-external-playtest-closed-findings-and-direction-2026-11-15)).**
Nothing here changes a shipped string; the tutorial tales were rewritten by [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370) (2026-11-17, section 1.3 shows the new wording); the rest of the rewrite follows in the issues of [section 6](#6-follow-up-issues). Sections 1 to 3 are findings;
section 4 is the draft as the author accepted it, kept as the record. The binding rules are in
[TEXT_STYLE.md](TEXT_STYLE.md); where the two differ, TEXT_STYLE.md wins.

Source of truth for the strings: `assets/localization/en.json` (123 keys; the other four catalogs
have the same keys). Triggers were read from `KimeriaGame`, `HealerDialogue`, `HealerDialogueView`,
`Player`, `Hud`, `BossHud`, `TouchControls` and the `Hints` layers of `assets/tiles/forest-*.tmx`
on 2026-11-15. Frequencies are per **New Game run** of the current three-level build
(`forest-0` tutorial, `forest-1`, `forest-2` with the boss), not measured play data.

Contents: [1. Inventory](#1-inventory-by-place-and-time) ·
[2. Propp mapping](#2-the-story-against-propps-functions-and-roles) ·
[3. Gaps and contradictions](#3-gaps-and-contradictions) ·
[4. Draft style system](#4-draft-style-system-not-approved)

## 1. Inventory by place and time

Character counts are EN; the five-language maxima are given where size matters. "Voice" is the
register the string is written in today: **tale** (narration or a character speaking in story
terms), **plain** (instruction, imperative or description), **label** (a word or two on a control).

### 1.1 Opening (once per New Game)

| Key | Trigger | Seen | Voice | Notes |
| --- | --- | --- | --- | --- |
| `INTRO_STORY` | New Game, after the confirmation; `openStory(INTRO_STORY, INTRO)` | Once per New Game; never on Continue | Tale, first person (the healer) | 790 chars (de 877), six paragraphs, one static page in every language since [#336](https://github.com/jlogicgames/edgard_in_kimeria/issues/336). |

Story beats in order: the healer introduces himself; Rufardus the Wise; L'Kardina arrives, banished
from a neighbouring land; Kimeria tries to drive her out; her fury becomes a curse; the sun stops
shining, eternal night, "sleeping lands"; word reaches the wandering warrior Edgard; he sets out; he
meets the healer, who gives counsel and ties the silver thread; "Here begins Edgard's journey
through the sleeping kingdom."

### 1.2 Menus and system screens (every visit)

| Keys | Where | Seen | Voice |
| --- | --- | --- | --- |
| `TITLE` | Main menu title, web start | Every launch | Label (the game's name) |
| `PLAY`, `CONTINUE`, `NEW_GAME`, `ABOUT`, `OPTIONS`, `EXIT` | Main menu | Every launch | Label |
| `NEW_GAME_CONFIRMATION`, `CONFIRM`, `CANCEL` | New Game with a saved run | Rare | Plain |
| `RESUME`, `RESTART_LEVEL`, `EXIT_TO_MENU`, `PAUSE_MENU`, `BACK` | Pause menu (Esc, Start) | Each pause | Label |
| `LANGUAGE_LABEL`, `DISPLAY_LABEL`, `FULLSCREEN`, `WINDOWED`, `LEFT_HANDED_LABEL`, `ASSIST`, `LONGER_TRAP_WARNINGS`, `SCREEN_SHAKE`, `ON`, `OFF` | Options and Accessibility | Occasional | Label |
| `MENU_HINT` | Bottom line of every menu (`overlay.hint`) | Every menu screen | Plain, key list |
| `RULES`, `CONTROLS` | About tabs | Occasional | Label |
| `ABOUT_RULES` | About, Rules tab | Occasional | Plain, terse |
| `ABOUT_BODY`, `ABOUT_BODY_LEFT_HANDED`, `ABOUT_GAMEPAD`, `ABOUT_GAMEPAD_LEFT_HANDED` | About, Controls tab (layout picked by the left-handed setting) | Occasional | Plain, key list (396 chars, de 418) |
| `LOADING` | Between levels | Each level load | Label |
| `ROTATE_HINT` | Touch build held in portrait | Phones only | Plain imperative |
| `INTRO_SKIP_KEYBOARD`, `INTRO_SKIP_GAMEPAD` | Intro and ending, bottom right corner (keyboard: Esc; pad: Start; touch has its icon, no string) | Every story screen | Plain, "key: verb" (D118) |

### 1.3 The healer's tales around the tutorial (once per run)

Scripted `HealerDialogue` scenes, typewriter at 38 chars/s, `HEALER_HINT` ("Next >") prompt, speaker
names `HEALER` / `EDGARD`. Never repeated by death, retry or Restart Level (D41).

| Order | Key | Speaker | Trigger | Content |
| --- | --- | --- | --- | --- |
| Start 1 | `HEALER_TALE_ROAD` | Healer | First entry into `forest-0` of a New Game run (`tutorialStart()`) | Opening formula "Listen, Edgard" (#370); the road is a tale, hear it first. |
| Start 2 | `EDGARD_TALE_NO_TIME` | Edgard | | Impatient: no time for stories. |
| Start 3 | `HEALER_TALE_FAIRIES` | Healer | | Since #370: the wood is full of old handles (the levers); one wakes a sleeping torch (lever room), one lets down a ladder (ladder room), one moves a stubborn wall (wall room). |
| Start 4 | `HEALER_TALE_LIFT` | Healer | | Since #370: another handle sets a boat of the air sailing up to the high ledge (platform room); what the hand cannot reach, a stone "sent like a wish" can wake (stone room). "Do not be afraid to ask" is gone. |
| Start 5 | `HEALER_TALE_WITCH` | Healer | | Since #370: the witch's gifts in three groups covering all five traps: thorns that rise (thorn root), branches, stones and bridges that fall (falling branch, falling stone, falling platform), stumps that burst (stump). |
| Start 6 | `HEALER_TALE_THREAD` | Healer | | The silver thread (#228): when the night swallows you, I draw the thread. |
| Start 6a | `HEALER_TALE_FIREGIFT` | Healer | | Added by #340 after this audit: the spark of the healer's own fire, refills are the same fire (F function for the fireball). |
| Start 6b | `HEALER_TALE_FAIRY` | Healer | | Added by #338 after this audit: the fairy, the healer's last gift (D82). |
| Start 7 | `EDGARD_TALE_UNDERSTOOD` | Edgard | | "I understand, old man." |
| End 1 | `HEALER_TALE_PRAISE` | Healer | `forest-0` exit checkpoint, first time in the run (`tutorialEnd()`) | The wood told its tales; you solved its riddles. |
| End 2 | `EDGARD_TALE_READY` | Edgard | | Since #370: "Forgive my haste, healer", then am I ready? (closes G16). |
| End 3 | `HEALER_TALE_HEROES` | Healer | | Heroes of old were afraid and went on. |
| End 4 | `HEALER_TALE_BLESSING` | Healer | | Blessing; trust no path too kind, "the witch smiles sweetest"; "May your fairy light your road" (#338); closing formula "Go on" (#370). |

### 1.4 The healer before the boss (`forest-2`, each meeting)

`openHealer(...)` when the hero reaches the healer before the boss room (`beginPreBossHealerMeeting`);
again after each collection return. Lines are chosen by state (`HealerDialogue` constructor).

| Key | Speaker | When | Voice |
| --- | --- | --- | --- |
| `HEALER_GREETING` | Healer | First meeting | Tale, but "won't affect magical items" is a rule statement |
| `EDGARD_REPLY` | Edgard | First meeting | Tale |
| `HEALER_RETURN_GREETING` | Healer | Meeting after a collection return | Tale |
| `HEALER_RETURN_CHECK` | Healer | Meeting after a collection return | Mixed |
| `HEALER_INGREDIENTS` | Healer | Missing Bat or Mushroom, coins complete | Plain inside tale ("Check the list") |
| `HEALER_PAYMENT` | Healer | Ingredients complete, coins missing | Plain inside tale |
| `HEALER_BOTH` | Healer | Both missing | Plain inside tale ("The list shows...") |
| `HEALER_OFFER` | Healer | Something missing, before the choice | Mixed |
| `HEALER_READY` | Healer | Everything collected | Mixed ("ten times stronger", "this boss") |
| `HEALER_PAID` | Healer (but narrates the player: "You drink the elixir") | After Pay | System narration in the healer's frame |
| `HEALER_LOCK` | Healer | After Pay | Plain ("its retries") |
| `HEALER_RETURN`, `HEALER_BREW`, `HEALER_FIGHT` | Choice buttons | Choice step | Label, imperative |
| `BAT`, `MUSHROOM`, `FOREST_COINS_COUNT`, `PAID` | Checklist beside the portraits | Every pre-boss meeting | Label |
| `COINS` | **Unused**: no code read it (the checklist uses `FOREST_COINS_COUNT`). **Deleted (#374)** from `Msg` and all five catalogs | Never | Label |

### 1.5 The healer after the boss (once per boss victory)

| Key | Speaker | Trigger |
| --- | --- | --- |
| `HEALER_VICTORY` | Healer | Boss dead and the victory exit walked (`afterVictory()` from `loadNextLevel`) |
| `EDGARD_DARKNESS` | Edgard | |
| `HEALER_GUIDE` | Healer | "I will be your guide. Go on: we will meet again." In the current build the ending follows at once. |

### 1.6 Zone messages (map-authored, every attempt)

Drawn by `Hud` in the 20 px zone font, 400 px wide, at most two lines in every language
(D33, D49, D50, `LocalizationLayoutTest`). Shown while the hero stands in the `Hint` zone and can
act; cleared on death, so every retry shows them again. All in `forest-0`; `forest-1` has no hints
and `forest-2` has only a controls zone.

| Key | Room / zone (x range) | EN chars | Longest translation (lang) | Lesson |
| --- | --- | --- | --- | --- |
| `HINT_TUTORIAL_WALK` | lever room, 16-160 | 30 | 41 (de) | Walk right |
| `HINT_LEVER_TORCH` | lever room, 160-440 | 43 | 48 (de) | Lever and torch |
| `HINT_LEVER_EXIT` | lever room, 440-560 | 43 | 46 (de) | Exit ahead |
| `HINT_LADDER_ROOT` | ladder room, 592-912 | 44 | 56 (de) | Thorn root trap |
| `HINT_LADDER_LEVER` | ladder room, 912-1072 | 48 | 49 (de) | Lever and ladder |
| `HINT_LADDER_CLIMB` | ladder room, 1072-1232 | 32 | 44 (de) | Climb |
| `HINT_LADDER_EXIT` | ladder room, 1232-1456 | 22 | 24 (de) | Exit ahead |
| `HINT_WALL_BRANCH` | wall room, 1488-1776 | 59 | 78 (be) | Falling branch trap |
| `HINT_WALL_LEVER` | wall room, 1776-1934 | 46 | 48 (de) | Lever and wall |
| `HINT_WALL_PASS` | wall room, 1934-2240 | 37 | 44 (de) | Pass under |
| `HINT_PLATFORM_STONE` | platform room, 2272-2560 | 75 | 67 (de) | Falling stone trap |
| `HINT_PLATFORM_LEVER` | platform room, 2560-2718 | 49 | 57 (de) | Lever and platform |
| `HINT_PLATFORM_RIDE` | platform room, 2718-3040 | 47 | 50 (de) | Ride |
| `HINT_STONE_THROW` | stone room, 3072-3322 | 65 | 67 (de) | Carry and throw |
| `HINT_STONE_STUMP` | stone room, 3322-3576 | 77 | 89 (de) | Stump lit from afar |
| `HINT_STONE_EXIT` | stone room, 3576-3712 | 59 | 64 (pl, de) | Hit lever, ride |
| `HINT_STONE_ACROSS` | stone room, 3712-3776 | 22 | 31 (de) | Exit ahead |
| `HINT_TRAP_PLATFORM` | trap room, 3808-4144 | 73 | 91 (de) | Falling platform |
| `HINT_TRAP_STUMP` | trap room, 4144-4496 | 64 | 80 (de) | Exploding stump |

Observations: three "exit ahead" lines (`HINT_LEVER_EXIT`, `HINT_LADDER_EXIT`, `HINT_STONE_ACROSS`)
say the same thing in three ways; trap lines are the longest and run to two sentences; all are plain
voice with no key names, as LOCALIZATION.md requires. The table records the texts of 2026-11-15; #373 rewrote 14 of
them in one formula ([D97](DECISIONS.md#d97-zone-messages-follow-one-formula-the-cue-then-what-to-do-373-2026-11-17)), and the catalogs hold the current wording.

### 1.7 The label above the hero (continuous, inside `controls` zones)

Composed at runtime by `Player.contextHint()` as `<binding>: <action> - <target>`,
13 px font, 200 px wide, up to three lines. Active in every `forest-0` room (`controls = true` zones); `forest-2`'s
`forest-entry` zone was removed with its practice objects (#376, 2026-11-17).

| Keys | Role |
| --- | --- |
| `PICK_UP`, `PUT_DOWN`, `THROW`, `CLIMB`, `CLIMB_DOWN`, `TURN_ON`, `TURN_OFF`, `INTERACT` ("Use") | Action verbs |
| `STONE`, `TORCH`, `LEVER`, `MECHANISM` | Targets |
| `PLACE_BLOCKED` ("Blocked") | States |
| `UP`, `DOWN`, `STICK`, `LEFT_STICK`, `RIGHT_STICK`, `DPAD_UP`, `DPAD_ACTION` | Binding names for pad and touch (keyboard uses key letters) |
| `FIREBALL_STOCK_FULL` | At a fireball refill while the stock is full (shown everywhere, not only in controls zones) |

### 1.8 HUD and touch buttons (always on screen)

| Keys | Where |
| --- | --- |
| `ATTACK` ("Sword"), `ACTION`, `SHOOT` ("Ranged"), `FIREBALL`, `NO_AMMO` ("Empty"), `THROW` | Touch buttons and the ranged label; `rangedLabel()` switches Throw / Fireball / Empty |
| `BOSS` | Boss health bar label in `forest-2` |
| `BAT`, `MUSHROOM` | Not drawn in the HUD (it shows icons only); the words appear in the pre-boss checklist (1.4) |

### 1.9 Death (every death)

**No text** on a death, except the first of a New Game run. Every death with a visible cause shows the silver thread
pulling Edgard back (D81, [#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341)).

| Key | Trigger | Voice |
| --- | --- | --- |
| `HEALER_FIRST_DEATH` | The first death of a New Game run, once, saved when it opens (D81); dialogue screen, one line | Healer, tale |

### 1.10 Ending (once per finished run)

| Key | Trigger | Voice |
| --- | --- | --- |
| `ENDING_STORY` | `completeLevel()` after the last level; same scrolling screen and music as the opening | Neutral third person, four one-line paragraphs and "THE END". **Since #372:** the healer's tale voice, two paragraphs and "THE END" |

### 1.11 Exposure summary

| Exposure | Strings |
| --- | --- |
| Every few seconds of play | Label above the hero (in tutorial rooms), touch buttons, ranged label |
| Every attempt in `forest-0` | 19 zone messages |
| Once per run | Opening, 11 tutorial-tale lines, 3 victory lines, ending |
| Per pre-boss meeting (1 to n per run) | 2 to 4 healer lines and the checklist |
| Menu visits | Menu labels, `MENU_HINT`, About |
| Never | `COINS` (deleted by #374) |

The text a player reads most is the plain-voice layer (labels and zone messages); the tale layer is
read once. The style rules in section 4 keep the two voices apart on purpose.

## 2. The story against Propp's functions and roles

Propp's *Morphology of the Folktale* (1928) describes the magic tale as a sequence of up to 31
functions acted out by seven roles. The table maps what the game says today. "Where" names the key;
**absent** means the function is not told anywhere.

### 2.1 Functions

| # | Function (Propp's sign) | In the game today | Where |
| --- | --- | --- | --- |
| α | Initial situation | Kimeria under Rufardus the Wise | `INTRO_STORY` ¶2 |
| 1 β | Absentation | Absent as such; the sun "ceases", which is told as the lack (8a) | `INTRO_STORY` ¶3 |
| 2-3 γ δ | Interdiction, violation | Absent. A candidate: Kimeria's attempt to drive out the witch is the violation that brings the curse | `INTRO_STORY` ¶2 |
| 4-7 ε ζ η θ | Reconnaissance, delivery, trickery, complicity | Absent; "the witch smiles sweetest" hints at trickery, never acted | `HEALER_TALE_BLESSING` |
| 8 A / a | Villainy / lack | L'Kardina's curse: the sun is gone, eternal night | `INTRO_STORY` ¶2-3 |
| 9 B | Mediation, the call | "Word of Kimeria's misfortune reached... Edgard": no dispatcher, a rumour | `INTRO_STORY` ¶4 |
| 10 C | Beginning counteraction | "He set out... to overthrow the witch" | `INTRO_STORY` ¶4 |
| 11 ↑ | Departure | Same sentence | `INTRO_STORY` ¶4 |
| 12 D | First function of the donor (test) | Healer's tale "hear it before you walk it"; the tutorial rooms are the "riddles". Pre-boss: the recipe is a task | `HEALER_TALE_ROAD`, `HEALER_GREETING` |
| 13 E | Hero's reaction | Negative first ("no time for stories"), then passes the riddles; pre-boss: collects or refuses | `EDGARD_TALE_NO_TIME`, `HEALER_TALE_PRAISE` |
| 14 F | Receipt of a magical agent | The silver thread (told twice); the blessing; the elixir (optional, paid). **The fire (fireball) has no receipt** | `INTRO_STORY` ¶5, `HEALER_TALE_THREAD`, `HEALER_TALE_BLESSING`, `HEALER_PAID` |
| 15 G | Guidance, spatial transference | "I will be your guide"; the levels themselves | `HEALER_GUIDE` |
| 16 H | Struggle | With the forest guardian (a guardian of the way, not the villain) | `HEALER_GREETING`, `BOSS` |
| 17 J | Branding | Absent. The thread on the wrist could serve as the mark | — |
| 18 I | Victory | Over the guardian | `HEALER_VICTORY` |
| 19 K | Liquidation of the lack | "Light has returned to the world", **with no struggle against the villain and no agent that brings the light** | `ENDING_STORY` |
| 20 ↓ | Return | Absent | — |
| 21-22 Pr Rs | Pursuit, rescue | Absent | — |
| 23-31 o L M N Q Ex T U W | Unrecognized arrival, false hero, difficult task, solution, recognition, exposure, transfiguration, punishment, wedding | Absent. The pre-boss recipe is closest to M/N (difficult task, solved) | — |

Reading: the build tells a full **preparatory section and first move** (α, A, B, C, ↑, D, E, F, G)
and a **struggle with a substitute** (H, I against the guardian), then jumps to K. The planned
campaign (four tablet fragments, Forest → Swamp → Steppe → Fortress, GAME_DESIGN.md) is the natural
place for the missing H/I against L'Kardina, the return and the recognition; for the MVP the gap is
in the ending's wording, not in the plot.

### 2.2 Roles (dramatis personae)

| Role | In the game | Status |
| --- | --- | --- |
| Villain | L'Kardina | Named once (`INTRO_STORY`), then only "the wicked witch" (`HEALER_TALE_WITCH`) and "witches" (`HEALER_TALE_BLESSING`). Never seen, never faced, absent from the ending. |
| Donor | The healer | Strong: narrates, tests, gives the thread, blessing, elixir. Unnamed by design (STORY.md). |
| Magical helper | The fairy (told and shown, D82), the silver thread (told and shown, D81) | The fairy is seen since [#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338); the thread is shown on every death since [#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341). |
| Princess / sought-for person | The sun, the light | An object, not a person; acceptable in the tradition (the stolen sun is a known tale type). |
| Dispatcher | None; a rumour sends the hero. Rufardus is a candidate | Vacant. |
| Hero | Edgard, a seeker hero ("wandering warrior") | Clear. |
| False hero | None | Vacant. |

Secondary figures: the forest guardian (`HEALER_GREETING`, `HEALER_VICTORY`) acts as a guardian of
the threshold.

## 3. Gaps and contradictions

Each line names the keys and the issue that owns the fix ([section 6](#6-follow-up-issues) lists the rewrite issues). Per AGENTS.md, no
item may stay in this document as an untracked gap once committed: each needs its issue linked.

| # | Finding | Keys | Owner |
| --- | --- | --- | --- |
| G1 | Fairies are named three times (torch, far-off fairy, blessing) and never seen. **Resolved (#338, D82):** the fairy is the healer's third gift (`HEALER_TALE_FAIRY`), visible in play; the torch and stone images no longer name a fairy, and the blessing names "your fairy". | `HEALER_TALE_FAIRIES`, `HEALER_TALE_LIFT`, `HEALER_TALE_BLESSING` | [#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338) |
| G2 | The fireball has no origin (no F function). The healer's "won't affect magical items" points at an unnamed magic. | `FIREBALL`, `HEALER_GREETING`, `HEALER_PAID` | [#340](https://github.com/jlogicgames/edgard_in_kimeria/issues/340), done: `HEALER_TALE_FIREGIFT` and one `INTRO_STORY` sentence (merged after this audit) |
| G3 | One action, four names: EN "Fireball" and "Ranged" (`SHOOT`), pad "ranged" in About, "throw/fireball" in About; uk/pl/de/be call it "Fire" (Вогонь, Ogień, Feuer, Агонь) but be `FIREBALL_STOCK_FULL` says "fireballs" (вогненных шароў). | `FIREBALL`, `SHOOT`, `ABOUT_*`, `FIREBALL_STOCK_FULL` | [#340](https://github.com/jlogicgames/edgard_in_kimeria/issues/340) kept the labels; reconciled in [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369) |
| G4 | The thread is only words; death shows nothing. | `HEALER_TALE_THREAD`, `INTRO_STORY` | [#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341) |
| G5 | `HEALER_TALE_WITCH`'s "berry that bursts" was written for the bomb; D40 replaced the bomb with an exploding stump. The image no longer matches the room. **Resolved (#370):** "stumps that burst". | `HEALER_TALE_WITCH` | [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370) (tutorial tales rewrite) |
| G6 | `forest-0` teaches five traps (thorn root, falling branch, falling stone, falling platform, stump); the witch line names two. **Resolved (#370):** the line names all five in three groups (rise, fall, burst). | `HEALER_TALE_WITCH` | [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370) |
| G7 | "Do not be afraid to ask": ask whom? No asking exists in the game. **Resolved (#370):** the sentence is removed. | `HEALER_TALE_LIFT` | [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370); may resolve with the fairy helper ([#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338)) |
| G8 | The villain is named once and never again; the ending does not mention her; the light returns with no cause (K without H against the villain). STORY.md keeps L'Kardina's fate out of the MVP by decision, so this is a wording problem: the ending overclaims ("every enemy", "free of monsters") after one guardian. **Resolved (#372):** the ending keeps STORY.md's outcome but places it at the end of "a long road" from the forest, so it no longer follows one guardian. | `ENDING_STORY`, `HEALER_TALE_WITCH` | [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) (ending in the healer's voice, within STORY.md's MVP decision) |
| G9 | The opening is the healer's voice; the ending is an anonymous narrator. The frame opens and does not close. **Resolved (#372):** the healer tells the ending; the frame closes. | `INTRO_STORY`, `ENDING_STORY` | [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) |
| G10 | `HEALER_GUIDE` promises "we will meet again"; in the three-level build the ending follows at once. **Resolved (#372):** the ending says the healer guided Edgard "as I had promised". | `HEALER_GUIDE`, `ENDING_STORY` | [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) (or accept as a campaign hook) |
| G11 | Game jargon in the healer's mouth: "boss" (`HEALER_READY`, `HEALER_PAID`, `HEALER_LOCK` in uk/pl/de/be), "retries" (`HEALER_LOCK`), "damage" (`HEALER_PAID`), "the list" (`HEALER_INGREDIENTS`, `HEALER_BOTH`). The tutorial tales are already guarded against this by `HealerDialogueTest`; the pre-boss lines are not. **Resolved (#371):** the pre-boss and victory lines are rewritten in tale voice in all five languages; their 32 allowlist entries are removed. | listed | [#371](https://github.com/jlogicgames/edgard_in_kimeria/issues/371) (rewrite); the gate tracks it |
| G12 | `HEALER_PAID` is spoken by the healer but narrates the player's action in second person ("You drink the elixir"): a system message in the healer's frame. **Resolved (#371):** `HEALER_PAID` is spoken by the healer as himself ("my pay is taken and my elixir is in you"). | `HEALER_PAID` | [#371](https://github.com/jlogicgames/edgard_in_kimeria/issues/371) |
| G13 | Two names for the boss: "forest guardian" (tale) and "boss" (`BOSS` HUD, `HEALER_FIGHT` button, healer lines). The HUD label is fine as a label; the healer should say "guardian". **Resolved (#371):** the healer says "the guardian" in every line; `BOSS` is now the tale name as a label ([#542](https://github.com/jlogicgames/edgard_in_kimeria/issues/542)); `HEALER_FIGHT` stays a label. | `BOSS`, `HEALER_*` | [#371](https://github.com/jlogicgames/edgard_in_kimeria/issues/371); glossary row "Boss in the tale" |
| G14 | Polish names the witch "wiedźma" in the opening and "czarownica" in the tales. **Resolved (#370):** the rewritten Polish tales say "wiedźma"; their allowlist entries are removed. | pl `INTRO_STORY`, `HEALER_TALE_WITCH`, `HEALER_TALE_BLESSING` | [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369) (translation pass) |
| G15 | "Sleeping lands", "sleeping kingdom": sleep is never explained (the curse is night, not sleep). Either a deliberate image (the land sleeps under the night) or drift. **Resolved (#372):** a deliberate image; the ending wakes the sleeping kingdom with the light. | `INTRO_STORY` | Author's call, settled with the frame in [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) |
| G16 | Edgard's rude reaction to the donor (`EDGARD_TALE_NO_TIME`) is never paid off. In the tradition a rude hero fails the test; here the praise (`HEALER_TALE_PRAISE`) quietly turns it into a passed test. Can stay (it is charming) but could be closed by one word of Edgard's at the end. **Resolved (#370, the author's choice):** `EDGARD_TALE_READY` opens with "Forgive my haste, healer." | `EDGARD_TALE_NO_TIME`, `EDGARD_TALE_READY` | Author's call, settled with the tales in [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370) |
| G17 | Three "exit ahead" zone messages worded differently; two trap messages run to two sentences and over 70 EN chars. **Resolved (#373, D97):** one formula (the cue, then what to do), one exit wording, every message at most 59 EN chars. | `HINT_*_EXIT`, `HINT_STONE_ACROSS`, `HINT_TRAP_PLATFORM`, `HINT_PLATFORM_STONE`, `HINT_STONE_STUMP` | [#373](https://github.com/jlogicgames/edgard_in_kimeria/issues/373) (hint rewrite within D49/D50) |
| G18 | `COINS` is in all five catalogs and read by no code. **Resolved (#374):** the key is deleted from `Msg` and all five catalogs, with its allowlist entry. | `COINS` | [#374](https://github.com/jlogicgames/edgard_in_kimeria/issues/374) |
| G19 | The opening is long (790 to 877 chars) for one screen. #336 made it static (one page, the player turns it), so it can be read at the player's pace; a rule of three paragraphs per page or a word budget would keep it shorter. **Ending (#372):** two paragraphs and THE END, 265 EN characters of text, within the per-page budget; the opening keeps its D77-measured single page. | `INTRO_STORY` | [#336](https://github.com/jlogicgames/edgard_in_kimeria/issues/336) for layout; text in [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) |
| G20 | The apostrophe in L'Kardina: STORY.md writes the Ukrainian form with a typographic ’ (Л’Кардина); all catalogs use ASCII '. Fix one form per language in the glossary. | `INTRO_STORY` | [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369); glossary row "Villain" |

## 4. Draft style system (not approved)

**Accepted in full by the author on 2026-11-16 (D74) and implemented as [TEXT_STYLE.md](TEXT_STYLE.md) and
`tools/check_text_style.py`.** The heading keeps its original anchor. Kept below as written for the review.

### 4.1 Two voices, never mixed in one string

| Voice | Who and where | Rules |
| --- | --- | --- |
| **Tale** | `INTRO_STORY`, `ENDING_STORY`, every `HEALER_*` / `EDGARD_*` spoken line | Story words only: no control, key, button or mechanic name, no game jargon (boss, retry, checkpoint, damage, list, level, HUD). Past tense for narration, present for speech. The healer addresses Edgard by name at the start of a scene. Numbers only when the tale itself counts ("ten times", "three gifts"). |
| **Plain** | `HINT_*` zone messages, `MENU_HINT`, `ABOUT_*`, `ROTATE_HINT`, confirmation texts | Imperative or a short statement of what happens. One idea per string; at most two short sentences. No tale imagery, no names of characters. No key names in zone messages (the label above the hero supplies the binding). |
| **Label** | Buttons, menu items, the label above the hero, HUD | One to three words, verb first for actions ("Pick up", "Put down"), noun for targets. The same thing has one label everywhere (see the glossary). |

### 4.2 Tale formulae and the rule of three

- **Fixed openings and closings.** The healer opens a tale with a formula and closes with one
  (draft EN: opening "Listen, Edgard"; closing a blessing or "Go on"). Each language fixes its own
  traditional equivalent in the glossary rather than translating word for word.
- **Three where it fits.** Three gifts, three warnings, three images in a list; the third breaks the
  pattern or is the strongest. Today `HEALER_TALE_FAIRIES` already follows it; `HEALER_TALE_WITCH`
  (two gifts) and `HEALER_TALE_BLESSING` (three verbs, then a fourth clause) could.
- **Show what the tale names.** A magical object or helper the tale names must be visible in play
  or explicitly marked as not yet met (G1, G2, G4).
- **Every mechanic the healer explains must be true in play** (STORY.md already asks this).

### 4.3 Measurable limits (all languages)

| Channel | Limit today (pinned by tests) | Draft addition |
| --- | --- | --- |
| Zone message | 20 px font, 400 px, two lines (D50, `LocalizationLayoutTest`) | EN source at most 60 characters and at most two sentences, so translations (de runs about 25 % longer) keep their margin |
| Label above the hero | 13 px font, 200 px (D50) | Each label word list stays at most three words |
| Dialogue line | Text box: 556 px, at most three of four lines, one spare (D78, `HealerDialogueTest`) | EN at most 160 characters per line, one image or one instruction per line |
| Opening and ending | None | Per page of #336: at most three paragraphs, EN at most 300 characters per page |

### 4.4 Glossary (draft)

One form per concept per language. The column "Avoid" lists forms found in the catalogs today or
likely drift. Forms in the uk/pl/de/be columns are the ones the catalogs use most today, offered for
the author's confirmation, not new translations.

| Concept | EN | UK | PL | DE | BE | Avoid |
| --- | --- | --- | --- | --- | --- | --- |
| Hero | Edgard | Едгард | Edgard | Edgard | Эдгард | |
| Kingdom | Kimeria | Кімерія | Kimeria | Kimeria | Кімерыя | |
| Villain | L'Kardina (ASCII ') | Л'Кардіна | L'Kardina | L'Kardina | Л'Кардына | typographic ’ in one place only (G20) |
| King | Rufardus the Wise | Руфардус Мудрий | Rufardus Mądry | Rufardus der Weise | Руфардус Мудры | |
| Donor | the healer | знахар | znachor | der Heiler | знахар | "old man" only in Edgard's speech |
| Witch (common noun) | witch | відьма | wiedźma | Hexe | ведзьма | pl "czarownica" (G14) |
| Boss (tale) | the forest guardian | лісовий вартовий | strażnik lasu | Waldwächter | лясны вартавы | "boss" in spoken lines (G11, G13) |
| Boss (HUD label) | Forest Guardian | Лісовий вартовий | Strażnik lasu | Waldwächter | Лясны вартавы | the tale name as a label (#542) |
| Silver thread | silver thread | срібна нитка | srebrna nić | silberner Faden | срэбная нітка | |
| Ranged magic | to be chosen with #340 ("Fire" vs "Fireball") | Вогонь | Ogień | Feuer | Агонь | three names in EN (G3) |
| Elixir | elixir | еліксир | eliksir | Elixier | эліксір | |
| Fairy | fairy (form fixed with #338, D82) | фея | wróżka | Fee | фея | |
| Ingredients | Bat, Mushroom | per catalog | per catalog | per catalog | per catalog | |
| Coins | forest coins | per catalog | per catalog | per catalog | per catalog | |

### 4.5 AGENTS.md rule line (draft)

> **Player-facing text.** New or changed strings follow `docs/TEXT_STYLE.md`: tale voice for
> narration and the healer, plain imperative for hints, one label per concept from its glossary.
> Write EN first and get the author's approval before translating; then all five catalogs in the
> same change. `./gradlew :core:test` (layout and text-style tests) and `python3
> tools/check_text_style.py` must pass.

### 4.6 Claude skill `game-text` (draft outline)

A thin wrapper in `.claude/skills/game-text/SKILL.md` that points at `docs/TEXT_STYLE.md` and
`docs/LOCALIZATION.md`; the substance stays in the docs.

1. Find where the string appears (map `Hint` zone, `HealerDialogue`, `Player` label, menu) and its
   voice from `docs/TEXT_STYLE.md`.
2. Key naming: `HINT_<ROOM>_<LESSON>` for zone messages, `HEALER_<SCENE>_<BEAT>` /
   `EDGARD_<SCENE>_<BEAT>` for dialogue, bare nouns/verbs for labels. Add the enum value to `Msg`.
3. Write the EN text only; check it against the voice rules, limits and glossary; show it to the
   author and stop until approved.
4. Translate into uk, pl, de, be using glossary forms; add the key's context to the
   LOCALIZATION.md table.
5. Run `./gradlew validateLocalization`, `python3 tools/check_text_style.py`, `./gradlew :core:test`;
   rebake fonts only if new glyphs are reported.
6. Report the measured line counts for zone messages and state what was not checked (native-speaker
   review, look in the running game).

### 4.7 Automated gate (draft)

Split by what each check needs: pixel checks stay in Java (they need the baked fonts); text checks
go in a Python tool with its own test, data in one JSON file.

**A. `tools/check_text_style.py` + `tools/test_check_text_style.py`, data in
`tools/text_style.json`** (glossary stems per language, voice of each key prefix, word lists):

| Check | Rule | Would flag today |
| --- | --- | --- |
| T1 glossary consistency | If the EN string contains a concept's EN stem, each translation contains that concept's stem in its language | pl `HEALER_TALE_WITCH`, pl `HEALER_TALE_BLESSING` (czarownica); be `FIREBALL_STOCK_FULL` (вогненных шароў vs Агонь) |
| T2 tale register | Tale-voice keys (`INTRO_STORY`, `ENDING_STORY`, spoken `HEALER_*`, `EDGARD_*`) contain no control or jargon word (word-boundary stems in five languages, extending the list in `HealerDialogueTest`) | `HEALER_READY` (boss), `HEALER_PAID` (boss, damage), `HEALER_LOCK` (retries, boss), `HEALER_INGREDIENTS` and `HEALER_BOTH` (list), in all five languages where the word appears: 24 hits in the dry run (all rewritten by #371) (a naive substring check also hit "Listen" in `INTRO_STORY`, so T2 must match word stems at word boundaries) |
| T3 zone message budget | EN `HINT_*` at most 60 characters and at most two sentences | `HINT_STONE_THROW` 65, `HINT_TRAP_STUMP` 64, `HINT_TRAP_PLATFORM` 73, `HINT_PLATFORM_STONE` 75, `HINT_STONE_STUMP` 77 (all rewritten within 60 by #373) |
| T4 no key names in zone messages | No binding token (J, K, L, W, A, S, D, Z, X, C, Esc, Tab, Enter, Space) followed by `+` or `:` or standing as a key list | none (the naive single-letter check falsely flags the English article "A" four times; the tool must match the binding pattern, not any capital letter) |
| T5 unused keys | Every catalog key is referenced by `Msg` and by code or a map | `COINS` (deleted by #374) |

**B. `:core:test`**: keep `LocalizationLayoutTest` (pixel limits) and `HealerDialogueTest`
(the text box, D78; speaker regions before #337); move the tutorial-only register check into the shared T2 list or have the Java
test read `tools/text_style.json` so one word list binds both.

The gate is designed to fail today on G11, G14, G17, G18 and part of G3: it lands together with the
rewrite (or with an explicit, argued allowlist entry per remaining hit, removed by the rewrite).

### 4.8 Roadmap change (draft)

`docs/ROADMAP.md` has no entry for #339 today. Add one under 2.2, after the first-playtest note, with three ordered steps:

1. Style system accepted: `docs/TEXT_STYLE.md` (voices, limits, glossary), the AGENTS.md line, the
   `game-text` skill, the gate (T1 to T5) with its test, landing with the current hits allowlisted
   and each allowlist entry linked to the rewrite issue.
2. EN rewrite, in story order (opening, tutorial tales, zone messages, pre-boss, victory, ending),
   after #338/#340/#341 settle the fairy, fire and thread facts; author approval per scene.
3. Translations uk/pl/de/be with the glossary; allowlist emptied; LOCALIZATION_VALIDATION.md
   updated; native-speaker review stays a separate, explicit "not verified" item.

## 5. Where accepted pieces would live

| Piece | File |
| --- | --- |
| Voices, formulae, limits, glossary | `docs/TEXT_STYLE.md` (new); STORY.md links it for world facts |
| Machine-readable glossary and word lists | `tools/text_style.json` (new) |
| Gate | `tools/check_text_style.py`, `tools/test_check_text_style.py` (new); `HealerDialogueTest` reads the shared list |
| Rule line | `AGENTS.md` |
| Skill | `.agents/skills/kimeria-game-text/SKILL.md` (shared, repo convention) and the Claude entry point `.claude/skills/game-text/SKILL.md` |
| Roadmap | `docs/ROADMAP.md` |
| This audit | Stays as the record of 2026-11-15; findings marked done as the rewrite lands |

## 6. Follow-up issues

| Issue | Title | Covers |
| --- | --- | --- |
| [#339](https://github.com/jlogicgames/edgard_in_kimeria/issues/339) | Text style system and gate (D74): TEXT_STYLE.md, glossary, AGENTS.md rule, skill, `tools/check_text_style.py` | 4.1 to 4.8 (done in this change) |
| [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370) | EN rewrite of the tutorial tales (witch's gifts, "ask", formulae) after #338/#340 | G5, G6, G7, G16, G14 (done) |
| [#371](https://github.com/jlogicgames/edgard_in_kimeria/issues/371) | EN rewrite of the pre-boss and victory lines in tale voice | G11, G12, G13 (done) |
| [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372) | Ending in the healer's voice within the MVP decision of STORY.md | G8, G9, G10, G15, G19 (done) |
| [#373](https://github.com/jlogicgames/edgard_in_kimeria/issues/373) | Zone message rewrite: one "exit ahead" form, trap lines within 60 EN chars | G17 (done, D97) |
| [#374](https://github.com/jlogicgames/edgard_in_kimeria/issues/374) | Remove or use the unused `COINS` key | G18 (done: deleted) |
| [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369) | Native-speaker review of uk/pl/de/be, glossary forms (including the fire's name from #340; Polish "czarownica" was fixed by #370) | G3, G14, G20 |
| [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368) | In-game visual checks | Look of rewritten texts in the running game |
