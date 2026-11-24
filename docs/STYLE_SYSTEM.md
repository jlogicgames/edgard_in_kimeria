# Style system

The one entry point to how Edgard in Kimeria is kept in one style: the design language, where each part of it lives,
the order of work, and what checks it. Decision: [D75](DECISIONS.md#d75-one-style-system-the-design-language-card-first-rules-land-complete-386-2026-11-16)
([#386](https://github.com/jlogicgames/edgard_in_kimeria/issues/386)). This page connects the other documents; the rules
themselves stay in [DECISIONS.md](DECISIONS.md) and the substance in the documents linked below. When they disagree,
the decision wins and this page is the one to fix.

## 1. The design language

Six principles. Every one is owned by decisions that already exist; D75 binds them together.

| # | Principle | Owned by | In practice |
| --- | --- | --- | --- |
| 1 | **Readable before lethal.** The player can read what matters: text at a size people read, dangers that look dangerous, decor that stays quiet. | [D70](DECISIONS.md#d70-minimum-text-size-raised-for-everyone-playtest-f1-335), [D71](DECISIONS.md#d71-traps-are-visible-before-they-fire-2026-11-16), [D76](DECISIONS.md#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16) contrast tiers | `ui.FontSizes`; traps VISIBLE by default; active versus decor contrast |
| 2 | **Fair and deterministic, cheap to retry.** One hit kills, attempts are unlimited, every death replays one short room, no route needs ammunition or a soft-lock escape. | [D2](DECISIONS.md#d2-one-hit-kills-attempts-are-unlimited), [D3](DECISIONS.md#d3-death-resets-the-current-room-and-respawn-is-instant), [D7](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements), [D19](DECISIONS.md#d19-test-a-20-second-replay-and-60-second-first-learning), [D26](DECISIONS.md#d26-observe-plan-before-committing) | shared warning times (`TrapWarnings`), D26 sweeps in the room tests |
| 3 | **Every room gives something new and keeps the player busy**: one unknown at a time, met in a safe pocket among known beats, then combined. | [D72](DECISIONS.md#d72-every-room-gives-the-player-something-new), [D105](DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499), D33-D40, D48 | room cards, at most two introductions outside lessons, about one beat every 3 s (3 beats or more, 1.5 to 4.0 s per beat) |
| 4 | **A hazard warns before it acts and shows a consequence after**: the D7 telegraph before, a visible and audible result after (screen shake only through `ScreenShake` and its Accessibility option, D62), and no effect hides a warning. | D7, [D73](DECISIONS.md#d73-falling-stone-impact-feedback-347-2026-11-16) (the model), D75, [D93](DECISIONS.md#d93-principle-4-measured-a-hazards-own-act-is-seen-and-heard-hit-or-miss-388-2026-11-17) (hit or miss) | the falling stone's dust, chips, sound and shake (the model); complete for the stone, deadfall, bramble, hive, stump, root, thorn root and tuft, and bomb; the thorn vine's motion is its own warning (D46); the rest are in "Known gaps" (D93: [#443](https://github.com/jlogicgames/edgard_in_kimeria/issues/443) to [#448](https://github.com/jlogicgames/edgard_in_kimeria/issues/448), enemies [#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240)) |
| 5 | **The tale is shown, not only told**, in two voices: the tale voice for narration and the healer, plain words for hints and labels. | [D74](DECISIONS.md#d74-text-style-system-two-voices-glossary-and-a-gate-339-2026-11-16), D66 (#337, #338, #340, #341) | glossary, register lists, "show what the tale names" |
| 6 | **Crude, original craft.** Native pixel art from authored sprites, detailed art only for portraits and HUD icons; crude lo-fi voice-synth sounds, generated, never sampled. Nothing procedural stands in for an object. | [D21](DECISIONS.md#d21-remake-and-remaster-preserving-the-original-artists-style), [ART_DIRECTION.md](ART_DIRECTION.md), [SOUND_DIRECTION.md](SOUND_DIRECTION.md) | `check_sprite_art.py`, `tools/audio/generate_sounds.py` |

Reference input behind 1, 3 and 4: the Primal Planet study
([GAME_DESIGN.md](GAME_DESIGN.md#primal-planet-level-study-playtest-f16-352), #352): a tell before damage, an
unexplained death is a defect, a landmark per room and a calm beat after danger (the card's optional `Landmark`).

## 2. The systems

| System | Rule | Reference | Gate | Skill |
| --- | --- | --- | --- | --- |
| Text size | D70 (D33, D49, D50 for hint geometry) | `ui.FontSizes`, [TEXT_STYLE.md](TEXT_STYLE.md) §3 | `FontSizesTest`, `LocalizationLayoutTest`, `HealerDialogueTest`, `HintLayoutTest` | [kimeria-rendering](../.agents/skills/kimeria-rendering/SKILL.md) |
| Player-facing text | D74, D97 (zone-message formula) | [TEXT_STYLE.md](TEXT_STYLE.md), [TEXT_AUDIT.md](TEXT_AUDIT.md), [LOCALIZATION.md](LOCALIZATION.md), [STORY.md](STORY.md) | `check_text_style.py` (`validateTextStyle`); `HealerDialogueTest` | [kimeria-game-text](../.agents/skills/kimeria-game-text/SKILL.md), [kimeria-dialogue](../.agents/skills/kimeria-dialogue/SKILL.md) |
| Monsters | D100 (D7, D10, #240), D102 (the spider), D103 (its route: walls and the silk drop), D106 (the snail, the first ranged monster), D115 (the wolf, the fastest and most dangerous) | [GAME_DESIGN.md](GAME_DESIGN.md) "Forest monster ideas", one card per monster | each monster's own test (`BeetleTest`, `SpiderTest`, `SnailTest`, `WolfTest`), `AllocationBudgetTest`, `check_contrast.py`; the notice rule is checked in review | [kimeria-combat](../.agents/skills/kimeria-combat/SKILL.md) |
| Monster bodies | [D101](DECISIONS.md#d101-monster-bodies-a-monster-killed-quietly-leaves-its-body-posed-apart-from-the-living-which-reacts-to-the-hero-and-is-finished-by-a-weapon-327-240-398) (D3, D6, D76, D117) | the rule and its applies-to list in D101; the body row of each monster's card in [GAME_DESIGN.md](GAME_DESIGN.md); look in [ART_DIRECTION.md](ART_DIRECTION.md), sounds in [SOUND_DIRECTION.md](SOUND_DIRECTION.md) | `RemainsTest` (every `Remains` class listed; pose differs from every live frame; rocks, crumbles, burns; no body after a reset, a passed room or a burst), `check_contrast.py` (`BODY_SWEEP`, active since D117), `AllocationBudgetTest` | [kimeria-combat](../.agents/skills/kimeria-combat/SKILL.md) |
| Traps | D71 (D7, D42, D44-D47, D63) | [TRAPS.md](TRAPS.md); history in [ORIGINAL_TRAPS.md](ORIGINAL_TRAPS.md) | `TrapCatalogTest` (with each card's D93 consequence), `TrapTeachingRoomTest`; `Level` validators and `check_tiled_links.py` | [kimeria-trap-design](../.agents/skills/kimeria-trap-design/SKILL.md) |
| Rooms | D72, [D105](DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499) density and first meetings (D19, D26, D33-D40, D48) | [ROOMS.md](ROOMS.md), [ROOM_CARDS.md](ROOM_CARDS.md) (registry with `Beat kind`; cards with `Beats`, `Beat time`, `First meeting`) | `RoomCardsTest` (novelty, density, first meetings, the shrink-only lists of rooms shipped below D105), `TrapTeachingRoomTest`, the route and replay tests | [kimeria-room-design](../.agents/skills/kimeria-room-design/SKILL.md), [kimeria-level-authoring](../.agents/skills/kimeria-level-authoring/SKILL.md) |
| Parallax layers | D108 (accepted 2026-11-24; colours by depth D120; the card in ART_DIRECTION.md "Forest look and parallax layers", [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)) | [ART_DIRECTION.md](ART_DIRECTION.md), [DEVELOPMENT.md](DEVELOPMENT.md#scenery-layers-and-the-foreground-parallax-236) | `tools/parallax_mock.py --check --game` (`validateParallax`), `ParallaxRendererTest`, `TiledMapParallaxTest`, `AllocationBudgetTest` | none yet ([#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236)) |
| Effects | D73, D93 (D62, D64, D65, D99) | [ART_DIRECTION.md](ART_DIRECTION.md) "Environment effects", [DEVELOPMENT.md](DEVELOPMENT.md#particles-and-the-allocation-guard) | `AllocationBudgetTest`, `check_allocations.py` | [kimeria-particles](../.agents/skills/kimeria-particles/SKILL.md) |
| Sound | author's aesthetic decision (2026-11-12), #313, D73, D93 | [SOUND_DIRECTION.md](SOUND_DIRECTION.md) | `tools/test_generate_sounds.py`, `SoundPreloadTest`; `TrapCatalogTest` (a trap card's D93 consequence names a sound that exists) | [kimeria-sound](../.agents/skills/kimeria-sound/SKILL.md) |
| Art | D21, D43, D55, D60; contrast tiers D76; the night and the moon rim D92; the depth palette of the decor, foreground and layers D120; a branch always grows from a trunk D122 (gate: `check_tiled_links.py` `check_branch_roots`); decor solid, quiet and apart from the ground D130 (a trunk kind at least 0.02 from the terrain colours, `check_contrast.py`); dialogue screen D78; fairy D82 (swept by `check_contrast.py`) | [ART_DIRECTION.md](ART_DIRECTION.md) (contrast: ["Contrast tiers"](ART_DIRECTION.md#contrast-tiers-d76-playtest-f15-344); ["Dialogue screen"](ART_DIRECTION.md#dialogue-screen-d78-playtest-f3f10-337)), [PORTRAITS.md](PORTRAITS.md) | `check_sprite_art.py` (`validateSpriteArt`), `check_contrast.py` (`validateContrast`), `test_dialogue_frame_sprites.py` (CI tool tests), `check_named_parameters.py` | [kimeria-art-direction](../.agents/skills/kimeria-art-direction/SKILL.md) |
| Story shown | D66, #340 (the fire is the healer's gift), D78 (the dialogue screen: the thread between the two portraits), [D82](DECISIONS.md#d82-the-fairy-helper-shows-the-interaction-target-instead-of-the-corner-brackets-playtest-f4-f14-338-2026-11-16) (the fairy helper shows the interaction target) | [STORY.md](STORY.md), [HEALER.md](HEALER.md), TEXT_STYLE §2 | `HealerDialogueTest` | [kimeria-dialogue](../.agents/skills/kimeria-dialogue/SKILL.md) |
| Agent tooling | D75 | [.agents/skills/README.md](../.agents/skills/README.md), AGENTS.md | `check_skills.py` (`validateSkills`), `check_doc_gaps.py` (`validateDocGaps`) | this page |

Three of them share one shape, and a new system copies it: **a decision, a reference document with a card or table,
the card approved by the author before the build, a gate that reads the document against the code or the maps, a thin
skill that is the order of work, and one paragraph in AGENTS.md.**

## 3. Where systems meet

- **Trap and room.** A trap's `Taught in` (TRAPS.md) and the room card's `Introduces` (ROOM_CARDS.md) name the same
  first meeting. `TrapTeachingRoomTest` holds them together: the teaching room is a shipped room with a card that shows
  the trap, no earlier room shows the type, and a DISGUISED or HIDDEN trap's room introduces its element. The registry
  in ROOM_CARDS.md groups reskins under one element; a reskin is not novelty (D72) and shares the trap's card family.
- **Trap and art.** VISIBLE means the "active" contrast tier (D76: outline at least 0.28 against the level's background colour (D92) and the
  map's tiles, checked by `check_contrast.py` for every placed sprite) or motion; the at-rest frame is designed
  with the trap card. Open levels are at night (`2c3f66`): where the ink contour falls under the floor there, the sprite
  carries the moon rim (D92), applied by the exporter, and a trap's ink cue on a lit edge is kept out of it.
- **Pickup and art.** Every collectable wears the one pickup cue (a 2 px bob, an additive halo except on the coin, a sparkle;
  [#345](https://github.com/jlogicgames/edgard_in_kimeria/issues/345)) and nothing that can hurt wears it, so a
  pickup never reads as an enemy (playtest F19); `check_contrast.py` measures collectables over their halo.
- **Hazard, effects and sound (principle 4).** The warning is the trap's or enemy's telegraph (D7, kimeria-combat for
  wind-ups), its length from `TrapWarnings`; a warning sound is welcome, not required. The consequence is what the
  hazard's own act shows and sounds when it fires, falls, bursts or strikes, hit or miss: a visible result (frames or
  particles) and a generated sound, with shake through `ScreenShake` only and only for heavy impacts. The shared death
  beat (`hit`, slow motion, the thread, D81) is the hero's consequence, not the hazard's. A clocked hazard is heard only
  while it is in the camera view (D89, D90).
- **Dialogue screen, art and text (D78).** The frame keeps the contrast tiers by analogy: what names the speaker or can be
  chosen is loud, the ornament and the listener quiet, every element measured (`test_dialogue_frame_sprites.py`, layout
  read from the view, since `check_contrast.py` measures world sprites only); no input is taken while the screen opens; every line fits the text box in three of its four lines in five languages, one spare
  (the first-death line, D81, takes two; `HealerDialogueTest`).
- **Text in rooms.** Zone messages are authored in the map (`Hint` objects, D33) and written under TEXT_STYLE's plain
  voice in one formula, the cue then what to do (D97, TEXT_STYLE §1.1); a hint is never a trap's at-rest cue (D71).
- **Text size and text style.** Pixel limits live in the layout tests (D50, D70); character budgets and voice in
  `check_text_style.py` (D74).

## 4. Card first: the common workflow

1. **Card.** Write the card in its reference document: a trap card (TRAPS.md), a room card (ROOM_CARDS.md), the English
   text (TEXT_STYLE §6), a sound's purpose (SOUND_DIRECTION).
2. **Approval.** The author approves the card, and its D-number when it is a new rule, before anything is built.
3. **Build** with the skill of each system.
4. **Gates** (section 5) pass; nothing is loosened to pass (AGENTS.md: exemptions must be argued).
5. **Record** the measured values on the card, and every unverified or unfinished part with an open issue.

**Order when one change spans systems** (for example a new trap in a new room):
trap card → art and sound (kimeria-art-direction, kimeria-sound, kimeria-particles) → room card → map
(kimeria-level-authoring) → text (kimeria-game-text) → gates and measured values.

## 5. Gates and hooks

| When | What runs |
| --- | --- |
| After every agent edit and at the end of a turn (hooks in `.codex/hooks.json` and `.claude/settings.json`) | `check_sprite_art.py`, `check_named_parameters.py` and `check_allocations.py` (with `--hook` the last two judge uncommitted edits only, base HEAD), and `check_style_hook.py`, which runs only the gates that apply: after an edit, to the changed files that call touched (the tree is shared, so another file's failure does not block it); at the end of a turn, to every changed file: `check_text_style.py` (catalogs, glossary data, TEXT_STYLE.md, `Msg.java`, maps), `check_tiled_links.py` (maps), `check_doc_gaps.py` (docs), `check_skills.py` (skills), `check_contrast.py` (sprites and maps, about 30 s since it composites the scenery layers behind every placement). For TRAPS.md, ROOM_CARDS.md, `Level.java` and the maps it prints the Gradle command of the JUnit gates instead of running them. |
| `./gradlew :core:test` | `TrapCatalogTest`, `TrapTeachingRoomTest`, `RoomCardsTest`, `FontSizesTest`, the layout and dialogue tests, `AllocationBudgetTest`. TRAPS.md, ROOM_CARDS.md and `tools/text_style.json` are declared test inputs, so an edit to them alone reruns the tests. |
| `./gradlew :core:check` | the tests plus `validateSpriteArt`, `validateContrast`, `validateParallax`, `validateNamedParameters`, `validateAllocations`, `validateTextStyle`, `validateDocGaps`, `validateSkills`. The three changed-line gates (`validateAllocations`, `validateNamedParameters`, `validateDocGaps`) judge the whole branch: every line changed since the merge-base with `origin/main`, plus uncommitted edits (`tools/git_base.py`, base `auto`); without `origin/main` they check uncommitted edits only and say so. `validateSpriteArt`, `validateContrast`, `validateTextStyle` and `validateSkills` read the whole tree. |
| Every pull request to `main` (CI, [`.github/workflows/check.yml`](../.github/workflows/check.yml)) | `./gradlew :core:check` on a full-history checkout, so `origin/main` exists and the changed-line gates see every line of the pull request; then every Python tool test (`python -m unittest discover -s tools -p 'test_*.py'`); Pillow is installed first, for `validateContrast` and the sprite tool tests |
| Release builds (desktop jar, web) | sprite art, contrast, named parameters, allocations, text style (docs and skills ship nothing, so their gates stay in `:core:check`) |

By hand: `python3 tools/check_style_hook.py` (what the hook would run now);
`./gradlew :core:test --tests '*TrapCatalogTest' --tests '*RoomCardsTest' --tests '*TrapTeachingRoomTest'`;
`python3 tools/check_allocations.py`, `python3 tools/check_named_parameters.py`, `python3 tools/check_doc_gaps.py` (every
line the branch changed since the merge-base with `origin/main`, plus uncommitted edits; `--base HEAD` for uncommitted
edits only);
`python3 -m unittest discover -s tools -p 'test_*.py'` (the tools' own tests).

Codex asks the user to trust a new or changed hook (`/hooks`) before it runs; Claude Code reads the project's
`.claude/settings.json` when a session starts. The Gradle tasks work without either. That Codex runs the dispatcher
and shows its reminder, and that its Claude import leaves the shared skills alone, is checked in [#389](https://github.com/jlogicgames/edgard_in_kimeria/issues/389). Qwen Code reads AGENTS.md and the
skills; the gates reach it through Gradle.

## 6. Which skill when

| Task | Skill (Claude pointer of the same name in `.claude/skills/`) |
| --- | --- |
| A room: plan, change, audit | kimeria-room-design, then kimeria-level-authoring |
| A trap: new, changed look, trigger or placement | kimeria-trap-design |
| Any player-facing string | kimeria-game-text (kimeria-dialogue for the dialogue mechanics) |
| A sprite, icon, portrait | kimeria-art-direction |
| A sound | kimeria-sound |
| Dust, sparks, blood, any particle | kimeria-particles |
| An enemy, the boss, hitboxes | kimeria-combat |
| Controls, saves and options, room state, rendering | kimeria-input-flows, kimeria-progress-settings, kimeria-room-state, kimeria-rendering |
| Any libGDX code | kimeria-libgdx-rules first, then the vendored `libgdx-*` reference |

The full list with links: [.agents/skills/README.md](../.agents/skills/README.md).

## 7. Known gaps

Each has an open issue whose checklist includes updating this page.

- Principle 4 (D93): these hazards act without a sound or particles of their own:
  - the falling branch lands without the stone's impact feedback ([#443](https://github.com/jlogicgames/edgard_in_kimeria/issues/443));
  - the falling platform, rotten log and rotten branch give way in silence ([#444](https://github.com/jlogicgames/edgard_in_kimeria/issues/444));
  - whether the bomb and the exploding stump and root shake the screen is not decided yet ([#448](https://github.com/jlogicgames/edgard_in_kimeria/issues/448)).
- Enemy warnings: the YellowMob and the bat have no notice or wind-up beat, and the RedMob's and the boss's wind-ups
  ignore the assist ([#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240)).
- The playtest changes are not yet checked in the running game: sizes, trap read distance, impact feedback, and that no
  effect hides a neighbouring warning ([#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368)).
- The uk, pl, de and be glossary forms are not yet reviewed by native speakers
  ([#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369)).
- The Codex side of the hooks and of the Claude skill import is not yet checked in Codex itself ([#389](https://github.com/jlogicgames/edgard_in_kimeria/issues/389)).
- First-learning times (D19's 60 s) are not measured on any room
  ([#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)).

## 8. Adding a system or a rule

A new design rule lands complete, in one change (D75):

- [ ] a D-number in DECISIONS.md, approved by the author;
- [ ] a reference document (or a section of one) with the card or table the rule is applied through;
- [ ] a gate that reads that document against the code or the maps, or a sentence saying why review checks it instead;
- [ ] a shared skill in `.agents/skills/` when the rule has a procedure, its Claude pointer in `.claude/skills/` and its
      row in `.agents/skills/README.md` (`check_skills.py` fails otherwise);
- [ ] a short paragraph in AGENTS.md;
- [ ] a row in section 2 of this page, and its gate in section 5;
- [ ] every gap it leaves linked to an open issue (`check_doc_gaps.py`).
