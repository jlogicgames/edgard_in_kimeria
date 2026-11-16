# First external playtest: feedback, analysis and decisions

**Status: done.** The first external playtest has run and its feedback reached the author by
2026-11-15. This document records that feedback, its analysis against the current build, the
author's decisions (questionnaire of 2026-11-15) and the GitHub issue that tracks each change.
It closes roadmap items 2.2.2 to 2.2.4
([#129](https://github.com/jlogicgames/edgard_in_kimeria/issues/129),
[#130](https://github.com/jlogicgames/edgard_in_kimeria/issues/130),
[#131](https://github.com/jlogicgames/edgard_in_kimeria/issues/131)) and the trap warning-time
playtest [#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300) by the author's
decision; see [what this playtest did not measure](#what-this-playtest-did-not-measure).

## Session

| Field | Value |
| --- | --- |
| Build | Web build (GitHub Pages), desktop browser |
| Participants | External players unfamiliar with the game (number and devices not recorded) |
| Content | The build of the time: `forest-0` tutorial and the following forest levels |
| Form | Written feedback collected by the author and passed on as a list |
| Received | By 2026-11-15 |

These are observations of other people, kept separate from the author's own play-throughs
(ROADMAP, D33) and from headless replay checks (`RoomReplayTest`).

## What this playtest did not measure

The feedback is qualitative. No room times, death counts, retries or per-trap data were
recorded, and participants were not asked to describe the controls or room goals. So:

- **#130 (20 s mastered, 60 s first learning):** closed by the author; the hypotheses stay
  hypotheses ([D19](DECISIONS.md#d19-test-a-20-second-replay-and-60-second-first-learning)), not measured values.
- **#131 (teaching without explanations):** closed by the author; no tester reported a controls
  or room-goal misunderstanding, which is weak evidence, not a teaching check.
- **#300 (trap warning times):** closed by the author; no tester reported a warning-time
  problem, so the current values (0.8 s, 1.2 s with the assist; 1 s and 1.5 s for the falling
  platform) stand as the accepted values. No comparison of values was run. The plan in
  [PLAYTEST_WARNING_TIMES.md](PLAYTEST_WARNING_TIMES.md) is kept as a reference and its tooling
  issues (#306, #307, #308) are closed as not needed.

Measured data can still come from later rounds: [#135](https://github.com/jlogicgames/edgard_in_kimeria/issues/135)
(external checks per level batch) and [#140](https://github.com/jlogicgames/edgard_in_kimeria/issues/140)
(full playthroughs). Fixing what this round found is [#141](https://github.com/jlogicgames/edgard_in_kimeria/issues/141).

## Feedback, analysis and decisions

The feedback arrived in Ukrainian; it is translated here (AGENTS.md: docs are English).
"Analysis" says what the current build does and why the remark is valid. "Decision" is the
author's answer of 2026-11-15.

### Text and presentation

| # | Feedback | Analysis | Decision | Issue |
| --- | --- | --- | --- | --- |
| F1 | The small font is hard to read. | Smallest sizes are 13 px (`Hud` hint and label, `Overlay` hint, `BossHud`, touch labels) and 15 px (body and dialogue) on a 640×360 logical screen. | Raise the minimum size for everyone; no text-size option for now. | [#335](https://github.com/jlogicgames/edgard_in_kimeria/issues/335), done in D70: labels and body 16, dialogue 18, touch labels stay 13; in-game checks in [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368) |
| F2 | The intro passes very fast and cannot be read; it could appear top to bottom and stay still. | `IntroScreen` scrolls the whole story at 24 px/s with no player control. | Static pages: paragraphs appear top to bottom and stay; a button turns the page. **Done (2026-11-16, awaiting review; [D77](DECISIONS.md#d77-story-screens-are-static-pages-playtest-f2-336-2026-11-16)):** `IntroScreen` shows static pages cut at paragraph breaks with the body font (`IntroPages`); paragraphs fade in one after another, any fresh press shows the rest then turns the page, Escape leaves, input is locked briefly after each page appears; the opening and the ending are one page in all five languages. The desktop and web check is part of [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368). | [#336](https://github.com/jlogicgames/edgard_in_kimeria/issues/336) |
| F3 | Dialogues do not look like fantasy (example: the Metal Gear Solid radio screen). | `HealerDialogueView` is a plain frame with small corner portraits. | Two portraits facing each other, codec style, in a fairy-tale frame, joined by the silver thread. | [#337](https://github.com/jlogicgames/edgard_in_kimeria/issues/337) |
| F10 | The healer's explanations could be "a big head plus text", in the style of a game the testers named (the title in their note could not be identified). | Same view as F3. | Not used as a separate layout; the codec-style screen covers it, including the first-death line (F9). | [#337](https://github.com/jlogicgames/edgard_in_kimeria/issues/337) |
| F8 | Add an accessibility option to turn off letter-by-letter text. | The dialogue reveals text letter by letter (`Overlay.dialogue`); no option exists. | An "Instant text" toggle, off by default. Also applies to the paged intro. **Done for the dialogue (2026-11-16):** Options -> Assist -> "Instant text" (D11); the paged intro ([#336](https://github.com/jlogicgames/edgard_in_kimeria/issues/336)) reads the same setting: with it on a page appears whole. | [#342](https://github.com/jlogicgames/edgard_in_kimeria/issues/342) |

### Story and fairy-tale structure

| # | Feedback | Analysis | Decision | Issue |
| --- | --- | --- | --- | --- |
| F4 | "A sleeping fairy wakes the torch with a touch": what fairy? Rework it. | The healer's tale mentions fairies (`HEALER_TALE_FAIRIES`, `_LIFT`, `_BLESSING`); none is ever seen. | Introduce a visible fairy helper (see F14) and rewrite the fairy lines to match. | [#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338) |
| F14 | Replace the frame around elements with a fairy pointing at the element. | `InteractionHighlight` draws gold corner brackets (D29, D30). | The fairy flies to the selected target and points; an instant glow keeps the selection readable; D29/D30 eligibility unchanged. | [#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338) |
| F5 | Analyse the game's texts and when they appear; rework them after the books on the magic tale (Propp and others). | Texts were written piece by piece per feature; no overall structure document exists. | Audit first (`docs/TEXT_AUDIT.md`: every string, trigger, Propp function), rewrite after the author approves. **Status (2026-11-16):** audit done ([TEXT_AUDIT.md](TEXT_AUDIT.md)); style system accepted as [D74](DECISIONS.md#d74-text-style-system-two-voices-glossary-and-a-gate-339-2026-11-16) ([TEXT_STYLE.md](TEXT_STYLE.md), gate `tools/check_text_style.py`); no string rewritten yet. Rewrite and translations: [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370), [#371](https://github.com/jlogicgames/edgard_in_kimeria/issues/371), [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372), [#373](https://github.com/jlogicgames/edgard_in_kimeria/issues/373), [#374](https://github.com/jlogicgames/edgard_in_kimeria/issues/374), native-speaker review [#369](https://github.com/jlogicgames/edgard_in_kimeria/issues/369). | [#339](https://github.com/jlogicgames/edgard_in_kimeria/issues/339) |
| F6 | Next to the thread, explain the fireball: where does this magic come from? | The ranged attack has no story origin. | The healer's gift: with the thread he gives Edgard a spark of fire; refills are the same fire. **Done (#340):** one healer line `HEALER_TALE_FIREGIFT` after the thread and one sentence in the opening (D41 note, STORY.md). | [#340](https://github.com/jlogicgames/edgard_in_kimeria/issues/340) |
| F7 | When the player dies, show the silver thread pulling him back to the start. | Death is 0.7 s of slow motion then a room reset; the thread exists only in text (STORY.md, #228). | A thread-pull animation on every death, within today's death-to-control time (D3). | [#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341) |
| F9 | On the first death, the healer says he brings Edgard back with the thread. | STORY.md (#228) excluded a first-death line. | Reversed: a healer line on the first death only, once per New Game run. | [#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341) |

### Art and readability

| # | Feedback | Analysis | Decision | Issue |
| --- | --- | --- | --- | --- |
| F11 | When the sprite turns it is only mirrored, so the sword changes hands; ladder up and down could differ. | The 48×48 hero sheet is flipped for left; one climb animation. | Separate left/right frames, distinct ladder up and down. | [#343](https://github.com/jlogicgames/edgard_in_kimeria/issues/343) |
| F21 | The hero sprites should be bigger. | 48×48 frames on 640×360. | A new, bigger hero drawing; hitbox and physics unchanged. | [#343](https://github.com/jlogicgames/edgard_in_kimeria/issues/343) |
| F15 | Colours: everything active must contrast with the background. The bush and the mushroom contrast now but should be muted. | No written contrast rule; decor bushes and static look-alike mushrooms (#299) are as bright as active objects. | A three-tier contrast rule (background, decor, active) and a recolour. Readability wins over the look-alike deception: static decor mushrooms are muted; the real/false honey fungus game stays between the two active mushrooms (D63). **Done (2026-11-16):** rule [D76](DECISIONS.md#d76-three-contrast-tiers-background-decor-active-344-playtest-f15-2026-11-16) and [ART_DIRECTION.md, "Contrast tiers"](ART_DIRECTION.md#contrast-tiers-d76-playtest-f15-344) (active outline at least 0.28, decor at most 0.20); bush, static look-alike and hanging vine recoloured to the decor tier; the lever, the one active sprite under the floor, outlined; gate `tools/check_contrast.py`; the louder tile scenery is [#189](https://github.com/jlogicgames/edgard_in_kimeria/issues/189), the in-game check [#368](https://github.com/jlogicgames/edgard_in_kimeria/issues/368). | [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344) |
| F19 | The bat looks like an enemy but is a collectable; draw it differently or replace it. | The Bat ingredient (D43) is a small flapping bat; a Bat enemy also exists. | Keep the ingredient, draw it as an item (dried wing in a jar or pouch) with a shared collectable cue. | [#345](https://github.com/jlogicgames/edgard_in_kimeria/issues/345) |
| F12 | Put a coat of arms on the coins, then repeat it on banners, the castle and elsewhere. | Coins have no heraldry; the kingdom has no emblem. | Design the Kimeria crest, redraw the coin, list the reuse places. | [#350](https://github.com/jlogicgames/edgard_in_kimeria/issues/350) |

### Traps, effects and level design

| # | Feedback | Analysis | Decision | Issue |
| --- | --- | --- | --- | --- |
| F18 | The stump trap does not stand out, so it does not read. | The exploding stump (D47) looks like a stump until touched. | An idle tell (cracks, ember glow, smoke wisp); timing unchanged. **Done (2026-11-16):** a four-frame idle loop on the stump and the root (ember crack, smoke wisp, 1 s), both now VISIBLE in [TRAPS.md](TRAPS.md); wind-up and blast timing unchanged; idle outline 0.33 (stump) and 0.35 (root), above the D76 floor; no idle sound. | [#346](https://github.com/jlogicgames/edgard_in_kimeria/issues/346) |
| F17 | The falling stone in the tutorial has no effects when it falls: particles, sound, crack. | `FallingStone` lands silently. | Done 2026-11-16 (author-approved): dust, chips, fading debris, `stone_impact.wav`, small shake (Assist option, D62). | [#347](https://github.com/jlogicgames/edgard_in_kimeria/issues/347) |
| F22 | Make more visible traps, like the vine. | Several traps are hidden until triggered. | Visibility audit, a visible-by-default rule, new visible trap proposals. **Done (2026-11-16):** rule [D71](DECISIONS.md#d71-traps-are-visible-before-they-fire-2026-11-16), audit and cards in [TRAPS.md](TRAPS.md), gate `TrapCatalogTest`; at-rest cues (#359, #360, #361, #362, #346) and three new visible traps (#363, #364, #365) follow. | [#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348) |
| F13 | Add jumping along tree branches; some branches can fall. | No branch platforms; `FallingPlatform` and `FallingBranch` exist. | A forest mechanic candidate: prototype one room, then decide placement. | [#349](https://github.com/jlogicgames/edgard_in_kimeria/issues/349) |
| F20 | Every new screen (room) must give something new. | The lesson rooms follow one new element each (D34-D40, D48); later rooms have no such rule. | A level-design rule plus an audit of forest-0 to forest-2. **Done (2026-11-16):** rule [D72](DECISIONS.md#d72-every-room-gives-the-player-something-new), room cards ([ROOM_CARDS.md](ROOM_CARDS.md)) and the `RoomCardsTest` gate; the audit keeps ten rooms and changes `forest-1-traps` ([#375](https://github.com/jlogicgames/edgard_in_kimeria/issues/375)) and `forest-entry` ([#376](https://github.com/jlogicgames/edgard_in_kimeria/issues/376)) for overload and misplaced repeats. | [#351](https://github.com/jlogicgames/edgard_in_kimeria/issues/351) |
| F16 | Look at the levels of Primal Planet. | Studied from three public reviews and the store page only (nobody played it): `docs/GAME_DESIGN.md`, "Primal Planet level study". Adopt/not-adopt lists link #351, #348, #344. | Study written; findings feed the room-novelty, trap-visibility and contrast audits. The sources do not cover hazard visibility or contrast ([#348](https://github.com/jlogicgames/edgard_in_kimeria/issues/348), [#344](https://github.com/jlogicgames/edgard_in_kimeria/issues/344)). | [#352](https://github.com/jlogicgames/edgard_in_kimeria/issues/352) |

## Patterns

- **Readability is the biggest theme** (F1, F2, F8, F15, F17, F18, F19, F22): small text, text
  that moves away, and active objects that do not stand out from decor. Most fixes are art and
  presentation, not rule changes.
- **The story is told but not shown** (F4, F6, F7, F9, F14): the thread, the fairies and the
  fire are words in the healer's tale. The decisions turn each into something visible (the
  thread pull, the fairy helper) or explained (the fire as a gift), and the text audit (F5)
  checks the whole story against the magic-tale structure.
- **Variety per room** (F13, F20, F22, F16) feeds campaign planning
  ([#133](https://github.com/jlogicgames/edgard_in_kimeria/issues/133)).

## Maintenance

When an issue above lands, update its row (status) in the same change. When all rows are done,
mark this document as historical. Decisions recorded here are summarized in
[D66](DECISIONS.md#d66-first-external-playtest-closed-findings-and-direction-2026-11-15).
