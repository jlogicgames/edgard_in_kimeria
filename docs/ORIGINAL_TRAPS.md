# Original DOS game: traps and hazards (evidence for #188)

This is the **evidence-documentation phase** of
[#188](https://github.com/jlogicgames/edgard_in_kimeria/issues/188). It records what the
1994 DOS game (Company MOTO, see [ORIGINAL_DOS.md](ORIGINAL_DOS.md)) shows and what the
author reports about it. The remake's trap set that followed from it is decided in
[D42](DECISIONS.md#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan) and
summarised below; nothing is implemented or tested yet, and implementation is tracked by
#230-#235.

**Framing (author, 2026-11-05).** These traps come from a 1990s game (1994). The remake is
a retro-styled **new work for the 2020s**. Only the **successful finds** are carried over,
adapted to modern fairness: a visible telegraph before a kill (D7) and recoverable
retries (D3, D26). Not everything in the original is to be reproduced, and the original's
rules (for example lives) are history, not requirements.

## Sources and how to read this document

- **Screenshots (S1-S15):** 15 DOSBox stills supplied by the author, stored in
  [`references/original-dos/`](references/original-dos/). Stills show one frame, so they
  cannot prove timing, triggers or motion.
- **Author notes (N1-N11) and answers:** the author's written descriptions of the traps,
  translated from Russian, plus the author's answers to the first version's open questions
  (all dated 2026-11-05). Each note is cited as `N#`. The notes were given as a numbered list without
  explicit screenshot numbers, so the note-to-screen mapping below is **inferred** and
  carries a confidence level.
- **Labels used throughout:**
  - **AUTHOR-CONFIRMED (2026-11-05):** the author's answer to a specific question.
  - **AUTHOR-REPORTED (2026-11-05):** the author's original note, not visible in a still.
  - **VISIBLE:** seen in the screenshot.
  - **DECODED:** read from the screenshot text by the method in
    [Decoded on-screen text](#decoded-on-screen-text), with a stated confidence.
  - **ASSUMED:** a proposed default for a design choice, change if wrong.
  - **TO CONFIRM:** still needs the
    [author-supplied video](https://www.youtube.com/watch?v=8hD09QE4U3Q) (not reviewed for
    this document) or the author.
- The on-screen text is CP866 Cyrillic shown with western DOS glyphs. It is decoded below.
- The screenshots are historical references, as the three in ORIGINAL_DOS.md. The original
  pixel art must not be copied as remake assets; #225 covers redrawing.

## Screen sequence

Screen numbers follow the order the files were supplied, not a verified in-game order.

| # | File | Window title | What is visible |
| --- | --- | --- | --- |
| S1 | [forest-01-bush-start.png](references/original-dos/forest-01-bush-start.png) | `EDGARD` | Forest. Edgard stands to the right of a cyan bush with purple veins; a thin short stake stands further right; HUD level counter reads "1"; three helmet icons. |
| S2 | [castle-02-spear.png](references/original-dos/castle-02-spear.png) | `LEVEL2` | Castle interior (purple outlines, window, torches, columns). A thin spear-shaped stake stands on the floor at left; Edgard is bent over near the right; counter "2"; three icons. |
| S3 | [forest-03-bush-start-left.png](references/original-dos/forest-03-bush-start-left.png) | `EDGARD` | Same screen as S1 with Edgard to the **left** of the bush and **no stake** at the right, where S1 shows one. Counter "1". |
| S4 | [forest-04-grass-and-stump.png](references/original-dos/forest-04-grass-and-stump.png) | `EDGARD` | Spiky cyan grass-like clump, a tiny dark diagonal bit beside it, a flat-topped stump with a small pink sprout on top. Edgard at left. No counter text in the frame; three icons. |
| S5 | [forest-05-stake-and-root.png](references/original-dos/forest-05-stake-and-root.png) | `EDGARD` | A thin stake at left, Edgard in a thick trunk, a small purple diagonal object lying on the ground just right of him. |
| S6 | [forest-06-root-in-grass.png](references/original-dos/forest-06-root-in-grass.png) | `EDGARD` | A spiky clump with a small purple shape inside it, then a stump with something small on top. Edgard at left. |
| S7 | [forest-07-falling-branch.png](references/original-dos/forest-07-falling-branch.png) | `EDGARD` | A branch with a purple foliage cluster stretching from the left trunk above Edgard; to the right a spiky clump with a purple mark and a stump. |
| S8 | [forest-08-bat.png](references/original-dos/forest-08-bat.png) | `EDGARD` | A white-outlined bat sitting on a branch above the ground; a cyan veined bush further right. |
| S9 | [forest-09-mushroom.png](references/original-dos/forest-09-mushroom.png) | `EDGARD` | A hollow tree, a spiky clump, a small purple mushroom on the ground at the right. |
| S10 | [forest-10-tall-root-stump.png](references/original-dos/forest-10-tall-root-stump.png) | `EDGARD` | A thin tall spike in front of Edgard, a small root left of it, and a stump to the right. |
| S11 | [forest-11-stump-blast.png](references/original-dos/forest-11-stump-blast.png) | `EDGARD` | Same screen as S10, Edgard now on the stump's spot inside a large purple-rimmed, grey-filled oval. |
| S12 | [forest-12-tall-root.png](references/original-dos/forest-12-tall-root.png) | `EDGARD` | A thin tall spike beside Edgard on a darker forest screen. **One** life icon remains. |
| S13 | [forest-13-owl-grass-stump.png](references/original-dos/forest-13-owl-grass-stump.png) | `EDGARD` | An owl on a high branch (right), a spiky clump and a stump. One life icon. |
| S14 | [fortress-14-tablet-entrance.png](references/original-dos/fortress-14-tablet-entrance.png) | `EDGARD` | A small tablet-shaped object on the ground, a cyan bush, and the crenellated fortress tower at the right. One icon. |
| S15 | [items-level2-screen.png](references/original-dos/items-level2-screen.png) | `EDGARD` | Full-screen text page: a bat and a mushroom drawn with captions, a tablet icon below with text lines (decoded below). |

## HUD

- **Life icons (VISIBLE, AUTHOR-CONFIRMED 2026-11-05):** three small helmet icons at
  bottom-left are **lives**. In the original every death cost one life, a trait of 1990s
  games. S1-S11 show three; S12-S14 show **one**, so two deaths had occurred by then.
  TO CONFIRM: what happens at zero lives (game over or restart; not stated).
- **Level label (VISIBLE, DECODED):** `Уровень 1` ("Level 1") on the forest screens and
  `Уровень 2` on the castle screen S2 (whose DOSBox title is also `LEVEL2`). Confidence high.
  It is absent in S4-S14 as captured.

## Decoded on-screen text

**Method.** The screen shows CP866 bytes drawn with the CP437 font, so each visible glyph
is the CP437 glyph of the byte. I read each glyph from the zoomed screenshot, mapped it to
its byte value, and decoded the byte as CP866. Glyphs in the CP437 range 0x80-0x9F
(accented letters such as `ä`, `ô`, `æ`) are CP866 **capital** letters (`ä` = Д, `ô` = У,
`æ` = С); `«` is о, `»` is п, `Γ` is т, `β` is с, `∞` is ь, `∩` is я, `½` is л, `¼` is м,
`ó` is в, `á` is а, `Ñ` is е, `¿` is и, `¬` is к, `º` is з, `δ` is ы. Every word was then
checked against Russian: all lines read as sensible Russian, which is the strongest check.
This is my reading of a small, blurry still; it was not verified by the author.

**HUD label (S1, S3, S2).** `ôp«óÑi∞ 1` → **Уровень 1** (Level 1). Confidence high.

**Item page (S15):**

| Line | Approximate glyphs as read from the still | Decoded | Confidence |
| --- | --- | --- | --- |
| 1 | `ô ΓÑi∩ 2 β«φΓáóióδÑ ¬«¼»«iÑiΓδ ºÑ½∞∩:` | **У тебя 2 составные компоненты зелья:** ("You have 2 component ingredients of the potion:") | High (every word checked; "составные" has one glyph I could not separate cleanly) |
| caption under the bat | `ÏÑΓπΥá∩ ¼δΦ∞` | **Летучая мышь** ("Bat", literally flying mouse) | High |
| caption under the mushroom | `âp¿i` | **Гриб** ("Mushroom") | High |
| 2 | `ô ΓÑi∩ »Ñρóá∩ Υáβ Γ∞ Γái½¿Υ¬¿:` | **У тебя первая часть таблички:** ("You have the first part of the tablet:") | High |
| 3 | `ä«iρ« »«ªá½«óáΓ∞ ó ºá¼«¬ ¿` | **Добро пожаловать в замок и** ("Welcome to the castle and") | High for the words; the final `и` sits at the line end and the sentence continues on line 4 |
| 4 | (capital-letter glyphs, ending in `!`) | **СЧАСТЛИВОГО ПУТИ!** ("...have a good journey!") | Medium: capitals are harder to separate; the length and letters fit the phrase, and it completes line 3 |

**Meaning.** The page states that the player holds **2 of the potion's component
ingredients (a bat and a mushroom)** and **the first part of the tablet**, then welcomes the
player to the **castle**. This ties the bat and mushroom to the healer's seven-component
potion in [ORIGINAL_DOS.md](ORIGINAL_DOS.md) and [STORY.md](STORY.md), and the tablet to the
four-part tablet. It is shown on entering Level 2 (the castle). Note: the author's N11
calls the building at the end of the forest "the fortress", while this page says "castle";
the story's order is forest, castle, steppe, fortress. The building in S14 is therefore
most likely the castle entrance. TO CONFIRM.

## Trap and hazard catalogue

Every entry separates what the screenshots show (VISIBLE) from what the author said
(AUTHOR-CONFIRMED or AUTHOR-REPORTED, all 2026-11-05). The names are neutral working names
for this document, not the original game's names.

### 1. Rising stake (spear from the ground)

- **Where:** castle (S2, N1); a thin stake also appears in the forest (S1, S5, S10, S12).
- **VISIBLE:** a thin, pointed cyan spear on the floor (S2). A second, similar spear stands at
  the right where Edgard is bent over it, probably the impaled-death frame N1 describes
  (TO CONFIRM). In the forest pair S1/S3 the same screen shows a thin stake in S1 and none in
  S3, consistent with a stake that appears as Edgard approaches (not proof).
- **AUTHOR-CONFIRMED (a):** the stake rises when Edgard **approaches** and kills when its X
  position **coincides** with his. It is a **proximity-triggered spike**, not a timer.
- **AUTHOR-REPORTED (N1):** first it is not there, then it rises; running into it leaves
  Edgard hanging dead on the spear ("an example of death"). The impaled sprite is tracked in
  [#224](https://github.com/jlogicgames/edgard_in_kimeria/issues/224).
- **Telegraph:** none reported (the stake simply appears). TO CONFIRM: how far ahead it
  rises, and how long the rise takes. The author says it stays up until the retry in the
  remake; whether the original retracts is not stated.
- **Correct response:** not stated; evidently jump over it as it rises, or avoid its X.
- **Death presentation:** impaled, hanging on the spear; one life lost.
- **Combines with:** a small root (S5, N3).

### 2. Spike root (tall root, kills from under the ground)

- **Where:** forest: S10 (in front of Edgard), S12 (beside Edgard); N8, N9.
- **VISIBLE:** a narrow tall spike in cyan, like the stake, taller than the small roots.
- **AUTHOR-CONFIRMED (b):** there are roots that kill **like a spike from under the ground,
  on touch**. N8 calls it "a tall protruding root that kills like a spear from the ground".
- **Telegraph:** none reported. TO CONFIRM whether it also rises on approach like the stake
  (the stills show it already up).
- **Correct response:** not stated; jump over it.
- **Death presentation:** presumably spear-like; not stated. TO CONFIRM.
- **Combines with:** a small root and a stump (S10).

### 3. Exploding root

- **Where:** forest. Candidates: S4, S5, S6, S7, S10 (small root shapes on the ground).
- **VISIBLE:** a small, low shape sticking out of the ground, often purple or dark; in S5 it
  lies diagonally at ankle height.
- **AUTHOR-CONFIRMED (b):** the root **explodes on touch**. N2: "a root sticking out of the
  ground: explodes".
- **Telegraph:** none reported. TO CONFIRM: whether there is any delay between touch and
  blast, and the blast's size.
- **Correct response:** not stated; jump over it.
- **Death presentation:** an explosion, presumably the one in S11 (below). One life lost.
- **Combines with:** a stake (S5, N3), hidden in grass (N4), a stump (N8), a bat (N6), a
  mushroom (N7).

### 4. Root hidden in grass

- **Where:** forest, S6 and S7 (N4, N5); possibly S4, S13.
- **VISIBLE:** a spiky cyan grass clump that looks like a decorative tuft. A purple mark is
  visible inside it in S6 and S7.
- **AUTHOR-REPORTED (N4):** the root is hidden in the grass: camouflaged as scenery.
- **Telegraph:** none; this is the clearest contradiction with the remake's D7 rule.
- **Correct response and death:** as for the exploding root, if it behaves as one.

### 5. Exploding stump

- **Where:** forest, S10/S11 (N8); similar stumps in S4, S6, S7, S13.
- **VISIBLE:** a flat-topped cyan stump, sometimes with a tiny pink detail on top. In **S11**
  Edgard stands where the stump was, inside a large grey-filled, purple-rimmed oval.
- **AUTHOR-CONFIRMED (c):** the stump **explodes on touch**, so **S11 is the stump
  explosion**. (N8: "a stump that explodes".)
- **Telegraph:** none reported. TO CONFIRM: any delay before the blast.
- **Correct response:** not stated; jump over it.
- **Death presentation:** the oval blast of S11, with Edgard inside it; one life lost.
- **Relation to the remake:** the original source of the bomb idea.

### 6. Falling branch

- **Where:** forest, S7 (N5).
- **VISIBLE:** a branch with a purple foliage cluster extends from the left trunk over
  Edgard's head; no switch or tripwire is drawn.
- **AUTHOR-CONFIRMED (d):** the branch kills when the hero **passes under the foliage**. He
  takes a step from the start of the screen and, **if he does not jump, the branch falls on
  his head**. It is a **position trigger at a fixed spot**; **jumping avoids it**.
- **AUTHOR-REPORTED (N5):** no visible button or trigger; then two more roots follow.
- **Telegraph:** none (the overhanging foliage is the only cue).
- **Correct response (original):** jump at that spot, found by learning the screen. In the
  remake the author wants a visible tell, a short delay and a real flight time, with **jump
  or run away** as the answers (2026-11-05).
- **Death presentation:** not stated. One life lost.
- **Combines with:** the two roots after it (S7: grass clump and stump).

### 7. Bat (collectible ingredient)

- **Where:** forest, S8 (N6), and the item page S15.
- **VISIBLE:** a small bat with spread wings, white outline, on a branch above the ground.
- **AUTHOR-REPORTED (N6):** collectible; the game could not be finished without it.
- **DECODED (S15):** one of the **2 component ingredients of the potion** the player holds,
  captioned "Летучая мышь".
- **Combines with:** a root-type trap on the same screen (S8).

### 8. Mushroom (collectible ingredient)

- **Where:** forest, S9 (N7), and the item page S15.
- **VISIBLE:** a small purple mushroom at ground level, right of a spiky clump.
- **AUTHOR-REPORTED (N7):** collectible; the game could not be finished without it.
- **DECODED (S15):** the second of the 2 potion ingredients, captioned "Гриб".
- **Combines with:** a root-type trap on the same screen (S9).

### 9. Tablet part and castle/fortress entrance

- **Where:** S14 (N11).
- **VISIBLE:** a small tablet-shaped object on the ground and a crenellated tower at the
  right edge.
- **AUTHOR-REPORTED (N11):** no traps; only the first part of the tablet and the entrance to
  the fortress.
- **DECODED (S15):** "You have the first part of the tablet" and "Welcome to the castle".
  See the note in the decoded text above about castle versus fortress.

### 10. Owl and veined bush (no trap reported)

- **Owl (S13):** on a high branch at the right; not mentioned by the author. Treated as
  scenery (ASSUMED, see defaults).
- **Cyan veined bush (S1, S3, S8, S14):** appears also on S14, which the author says has **no
  traps** (N11), so the bush is **evidently harmless scenery** (inferred, medium confidence).

## Mapping of the author's notes to the screenshots

The author gave a numbered list but no screenshot numbers, so the mapping is inferred by
matching each description to what is drawn. **Confidence:** High = the screen shows what the
note says; Medium = consistent but another screen could also fit; Low = a guess.

| Note | Author's description | Mapped to | Confidence | Reason / doubt |
| --- | --- | --- | --- | --- |
| N1 | Stake from the ground; hangs dead on the spear; the castle | S2 | High | Only castle screen; S2's bent pose is likely the impaled frame. |
| N2 | A root sticking out of the ground; explodes | S4 (or S1/S3) | Low | S4 has a root-like bit and a stump; the bush screens S1/S3 show a bush and a stake. |
| N3 | A stake and a root from the ground | S5 | Medium | S5 has a thin stake and a small purple root on the ground. |
| N4 | A root hidden in the grass | S6 | Medium | S6 has a clump with a purple mark; S4 could also fit. |
| N5 | A branch falls if you do not jump; then 2 roots | S7 | Medium | S7 shows the overhanging branch, a clump and a stump. |
| N6 | A root and a bat | S8 | High | The bat is on this screen; the "root" is presumably the bush or another low object. |
| N7 | A root and a mushroom | S9 | High | The mushroom is on this screen; the "root" is presumably the clump. |
| N8 | A root, a tall root and an exploding stump | S10 and S11 | High | S10 shows all three; **S11 is the stump explosion (AUTHOR-CONFIRMED (c))**. |
| N9 | Another tall root | S12 | Medium | S12 shows one thin spike; the player is on one life. |
| N10 | A single root | S13 (or S4) | Low | S13 has a clump and a stump, and an owl the author did not mention. |
| N11 | No traps, tablet part 1 and the fortress entrance | S14 | High | Only fortress/castle-tower screen. |

S1 and S3 are two frames of the same screen without a clear note. 11 notes against 15
screenshots means some screens are repeats or carry no note. S15 is the item page, decoded
above.

## Combinations seen in the original

| Combination | Screens | Source |
| --- | --- | --- |
| Stake + root | S5 | N3 |
| Root + bat | S8 | N6 |
| Root + mushroom | S9 | N7 |
| Root + tall root + exploding stump | S10, S11 | N8 |
| Root hidden in grass (camouflage) | S6, S7 | N4 |
| Falling branch, then two roots | S7 | N5 |
| Single hazards on a screen | S12, S13 | N9, N10 |

## Observations that matter for the remake

- Most original hazards are **ground-level objects next to a safe-looking prop**: stumps,
  grass and bushes appear both as scenery and as hazard. The original has no warning; the
  remake's D7 requires one.
- Original hazards are **instant**: spikes (stake, spike root), **explosions on touch** (root,
  stump) and a **position-triggered falling branch**. Each death cost a life.
- Collectible ingredients (bat, mushroom) sit next to a trap on the same screen.
- Proximity or position triggers (stake, branch) are deterministic: the same approach always
  gives the same result. That fits D7's determinism even though the original has no warning.

## Still-open facts about the original

Settled by the author on 2026-11-05 and no longer open: stake trigger (proximity), root and
stump trigger (touch), S11 (stump blast), the falling branch's trigger, lives. Still open
(answerable from the video, not needed to start):

1. Does the original stake retract, and how far ahead does it rise? (The remake's stake stays up until the retry: author, 2026-11-05.)
2. Is there any delay between touching a root or stump and its blast, and how large is the blast?
3. Do spike roots also rise on approach?
4. What happens at zero lives (game over or restart)?
5. Is the building in S14 the castle (S15 says "castle", N11 says "fortress")?
6. Which of S1-S14 share a level, and in what order?

## Proposed defaults for design choices (ASSUMED, change if wrong)

These are design choices rather than facts about the original. Each follows the remake's
accepted decisions (D2: one hit kills, unlimited attempts, no lives; D3: room retry; D6:
hearts removed; D7: deterministic, shared warnings; D26: recoverable required routes; D40:
falling platform and bomb with warnings). The genre conventions named below are from my own
knowledge of modern retro-styled platformers; I did not look anything up.

| Choice | Proposed default | Rationale |
| --- | --- | --- |
| Lives | **No lives.** Death is an instant room retry (D2/D3). The three icons are not carried over. | D2 already decides this. Modern retro platformers (for example Celeste, Super Meat Boy, Shovel Knight's checkpoints) dropped lives for fast retries. |
| Hidden trap vs D7 | **Every killing trap shows a telegraph** before it can kill, with a warning time from the shared source. Hidden-trigger traps stay deterministic but get a visible cue. | D7 and D5 (instant death only when understandable). Modern "trap" rooms in this genre are readable on second look; unmarked traps are accepted only as learn-by-dying, which D26 rules out. |
| Falling branch | **Author-confirmed 2026-11-05:** position trigger + **visible tell + short delay**, then the branch falls with a real flight time. While it falls the player can **jump or run away**; both answers work. | The author's wording; it meets D7 and keeps the original's jump answer. |
| Rising stake | **Author-confirmed 2026-11-05:** it is **animated**: when the player gets close the stake's tip **pokes out and wobbles a little** (the readable warning), then it rises and **stays up until the retry**. Placement (assumed): on open floor so it can be jumped, never in a required one-tile corridor. | Matches the falling platform's "spent until the retry" (D7/D40). |
| Root and stump explosions | **Implemented in D47 (#233).** **Author-confirmed 2026-11-05: the existing bomb behaviour is OK**: touch lights a fuse (shared 0.8 s warning, 1.2 s with assist) then one 64 x 64 blast. The root and stump sprites (cracks, smoke, glow, blast, charred remains) exist and are used by the new traps. | The existing bomb is the tested, fair version of the original's touch-explosion. |
| Camouflaged root | **Author rejected keeping it as is (2026-11-05).** The root's colour clearly differs from the grass and bush on the screenshots, so it was not truly hidden. D42 replaces it with the bristling thorn tuft. | Evidence from the stills (S6, S7) and the author's decision. |
| Owl (S13) and veined bush | **Decor, no hazard. Author-confirmed 2026-11-05.** | The bush also appears on S14, which has no traps. Much other decor will need drawing later (follow-up). |
| Bat and mushroom | **Author decision 2026-11-05:** they stay **optional ingredients** and **replace the current Chicken and Key** in the remake, as in the original. Each may be guarded by one trap. | Ticket [#229](https://github.com/jlogicgames/edgard_in_kimeria/issues/229). D16 already makes ingredients optional. |
| Item page | Not carried over as a mandatory screen. A short summary of collected ingredients and tablet parts at a level change is an option, not a requirement. | A recap before the next level is a genre convention; no need to copy it. |

## Adaptation for the remake: accepted design (D42)

On 2026-11-05 the author accepted a forest trap set, an explosive-trap rule and a tutorial
plan, recorded in
[D42](DECISIONS.md#d42-forest-trap-set-explosive-trap-choice-and-tutorial-plan). **The #188
brainstorm phase is done by that decision; implementation is tracked by the tickets below.**
Nothing is implemented or playtested yet. Summary (D42 is the owner of the rules):

- **Spirit:** simple, lethal, organic hazards on the ground and overhead; variety by trigger
  family; more traps are welcome. All traps are lethal (or a moving block), each with a
  readable telegraph (D7) and room-retry recovery (D26).
- **Proximity:** thorn root / stake (tip peeks out and wobbles, then rises, stays until the
  retry) and the bristling thorn tuft (replaces the camouflaged root; clearly a different
  colour from bushes).
- **Position:** falling branch (tell, short delay, jump or run) and falling stone (growing
  ground shadow).
- **Touch:** exploding stump and exploding root, the existing bomb behaviour; they replace the
  glass-bottle bomb.
- **Rhythm:** spore puffball mushroom and swinging thorn vine.
- **Crumble:** rotten log bridge, the existing falling platform in a forest skin.
- **Castle (later):** blade cutters and blue sparks / lightning; the falling stone may also
  appear there.
- **Explosive-trap rule:** on a required path, detonate it from afar (fireball or thrown
  stone) or take a riskier bypass; the bypass is mandatory because the route never needs
  ammunition (D26).
- **Tutorial plan:** one new trap per `forest-0` room (ladder: thorn root; wall: falling
  branch; platform: falling stone; stone: exploding stump; traps: stump replaces the bomb;
  lever room stays pure). This deliberately relaxes the one-new-element-per-room rule; D35-D40
  are updated by #235 (done, [D48](DECISIONS.md#d48-traps-in-the-tutorial-rooms-one-new-trap-per-lesson-room)).
- **Art:** the explosion is currently a shader, so sprites are needed (stump/root wind-up with
  cracks, smoke and glow, then the blast); the glass-bottle bomb is redrawn or replaced.

**Rejected by the author:** carnivorous flower (does not grow in this forest), rolling log (no
slopes), non-lethal burnable bramble (all traps are lethal), and the camouflaged root as such
(the original root's colour differs from the scenery, so it was not truly hidden).

## Follow-ups

- [#224](https://github.com/jlogicgames/edgard_in_kimeria/issues/224): impaled death sprite.
- [#225](https://github.com/jlogicgames/edgard_in_kimeria/issues/225): decompose the original
  screens into visual elements (decor such as the owl and veined bush ties in here).
- [#229](https://github.com/jlogicgames/edgard_in_kimeria/issues/229): Bat and Mushroom
  replace Chicken and Key. **Done, see D43.**
- [#230](https://github.com/jlogicgames/edgard_in_kimeria/issues/230): art (forest trap
  sprites, bomb replacement), in the remake's style per ART_DIRECTION.md without the 1990s
  palette, resolution or flat-colour limits; only the original's silhouettes and readability
  ideas are kept (author, 2026-11-05). **State (2026-11-05):** first-pass sprite sets for every
  forest trap are drawn and exported (see "Forest trap sprites (#230)" in ART_DIRECTION.md); no
  code uses them yet and several are rough placeholders for a human artist to redo.
- [#231](https://github.com/jlogicgames/edgard_in_kimeria/issues/231): code A, proximity
  traps (thorn root/stake, thorn tuft). **Implemented, see D44** (object types `ThornRoot`,
  `ThornTuft`; the root is placed in the ladder room by #235/D48, the tuft nowhere).
- [#232](https://github.com/jlogicgames/edgard_in_kimeria/issues/232): code B, falling traps
  (branch, stone). **Implemented, see D45** (tested headlessly in test-resource maps, not
  placed in any shipped level, not yet seen in the running game).
- [#233](https://github.com/jlogicgames/edgard_in_kimeria/issues/233): code C, explosive
  stump/root replacing the bomb. **Implemented, see D47** (types `ExplosiveStump`,
  `ExplosiveRoot`; touch or a fireball / thrown stone from afar lights them; every one needs a
  floor bypass at load; the `forest-0-traps` bomb is replaced by a stump; the old `Bomb` type
  stays and `forest-2` still has one).
- [#234](https://github.com/jlogicgames/edgard_in_kimeria/issues/234): code D, rhythm traps
  (puffball, vine) and the rotten-log skin. **Implemented, see D46** (types `Puffball`,
  `ThornVine`, property `skin=log`; not placed in any shipped level, #235 does that).
- [#235](https://github.com/jlogicgames/edgard_in_kimeria/issues/235): tutorial integration. **Implemented, see D48**: thorn root (ladder room), falling branch (wall), falling stone (platform), exploding stump (stone room, next to the stump of the traps room); the rhythm traps stay unplaced.
- Backlog added 2026-11-05 (author requests and ideas, not implemented rules):
  - [#236](https://github.com/jlogicgames/edgard_in_kimeria/issues/236): parallax layers
    (see ART_DIRECTION.md).
  - [#237](https://github.com/jlogicgames/edgard_in_kimeria/issues/237): castle floor
    pressure plate that opens a door like a lever (see the later ideas below).
  - [#238](https://github.com/jlogicgames/edgard_in_kimeria/issues/238): arrow shot from the
    side (see the later ideas below).
  - [#239](https://github.com/jlogicgames/edgard_in_kimeria/issues/239): environment effects
    (see ART_DIRECTION.md).
  - [#240](https://github.com/jlogicgames/edgard_in_kimeria/issues/240): enemy wind-up and
    noticing (see GAME_DESIGN.md).

## Later trap and mechanism ideas (author, 2026-11-05)

Not part of the forest set and not implemented; also recorded in D42.

- **Falling stone in other worlds:** reusable, with a stone skin and shadow per world (#232).
- **Arrow shot from the side (#238):** for world 2 and later (castle and other worlds, not
  the forest). The projectile can change per world and the player dodges it. It needs the D7
  telegraph before it fires.
- **Honey fungus and false honey fungus (#295):** two look-alike spore mushrooms, one harmless (particle burst) and one that kills by its spore cloud (sprite first, particles and shaders later, #296); decided 2026-11-12, see the subsection below.
- **Floor pressure plate (#237):** activates a door like a lever (Prince of Persia style), for
  the castle-fortress stage. Open design points: whether the door stays open or closes when the
  plate is released, the D26 recovery rules, and linking by `target`.

### Honey fungus and false honey fungus (built, 2026-11-13)

Decided by the author on 2026-11-12; **status: built** (phase 1: decision record [D63](DECISIONS.md#d63-honey-fungus-and-false-honey-fungus-a-pair-of-look-alike-proximity-mushrooms-295-phase-1-decision-and-art)
and the sprites `trap-honey-fungus` and `trap-false-honey-fungus`, awaiting the author's review of the art; phase 2: the objects `HoneyFungus` and
`FalseHoneyFungus` on the base class `FungusTrap`, the tests and the placement, all built and described in D63 "As built"); tracked by
[#295](https://github.com/jlogicgames/edgard_in_kimeria/issues/295), with the later polish in
[#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296).

**Basis.** The honey fungus (Russian *opyonok*) is harmless to people; the false honey fungus is poisonous, and
they look alike. The game gets two map objects with nearly identical sprites that differ slightly, so the
player has to read the difference:

| | Honey fungus (harmless) | False honey fungus (poisonous) |
| --- | --- | --- |
| Bursts into spores | yes | yes |
| What the burst is | only a pretty effect: **particles**, like the torch's flame and sparks (`KimeriaParticleSystem`, the `kimeria-particles` skill) | a spore cloud drawn as a **sprite animation**, which **kills** |
| Harm | none | instant death, by hitbox |

**Rules (author answers, 2026-11-12).**

- **Death.** The poisonous cloud is instant death, as for every lethal trap ([D3](DECISIONS.md#d3-death-resets-the-current-room-and-respawn-is-instant)):
  one overlap test of the cloud's rectangle with the hero's body box, as the bomb blast and the spore puffball
  (D46) do. The harmless burst has no hitbox.
- **Trigger.** The hero's approach, as for the other proximity traps: the hero entering the mushroom's zone
  starts it (the family of the thorn tuft, D42).
- **Telegraph (D7).** Applies to both: after the trigger the cap swells for the shared warning
  (`TrapWarnings`; today 0.8 s, 1.2 s with the longer-warnings assist, **a placeholder that was never playtested**:
  the author wants shorter warnings in the spirit of Celeste and Katana Zero, to be tuned by playtest, see the note
  in D7 and [#300](https://github.com/jlogicgames/edgard_in_kimeria/issues/300)) and then bursts. Both mushrooms swell
  the same way, so the puzzle is whether the burst kills. Only the warning scales with the assist, never movement
  or cooldowns.
- **Idle animation (author, 2026-11-12).** The active mushrooms play a subtle idle animation (a small sway or
  pulse), so the player can tell from a distance that a mushroom is alive and may react. It is deterministic (the
  level clock, no randomness) and independent of the hero. **Revised 2026-11-13:** the author played the frame-swap
  idle and found the constant blinking strange and unclear, so the idle is now a smooth brightness glow (a sine on
  one fixed frame, period 2.5 s, 0.55 to 1.0 brightness (was 0.8; the author could not see a 20 % dip), identical for both, brightness only so it is colour-blind
  safe); it stops when the swell starts, and the swell remains the only telegraph (D63).
- **Look.** Real fungi, **not** a fly agaric (no red cap with white spots) and not like the healer's purple spotted
  Mushroom pickup (D43) or the existing `trap-puffball`. Both are a tuft of several small caps on thin stems.
  The **honey fungus**: honey-brown to tan caps, pale stems with a visible ring. The **false honey fungus**: the
  same tuft shape, but sulphur-yellow to orange caps with a greenish tinge, darker olive gills and no ring.
  Spore colours differ too: warm golden for the harmless burst, the lime of `trap-spores` for the lethal cloud. They
  must be distinguishable only on a close look.
- **Objects.** Two new Tiled object types, `HoneyFungus` and `FalseHoneyFungus`, sharing one base class
  (proximity, swell, burst), not an extension of `Puffball` (D46), which is a rhythmic timer trap with no
  proximity and no harmless variant. The lethal cloud is drawn by `SporeCloud` (below) and shares the puffball's
  lethal-hitbox entry, not its sprite.
- **Static look-alikes (built, [#299](https://github.com/jlogicgames/edgard_in_kimeria/issues/299); author design revised 2026-11-13).**
  Scenery mushrooms that never react and can never kill, so that the player stops and reads the room (D26) before running and
  asks "is this a trap?". They are a **cap-and-stem tuft of the same family** as the active pair, 24×16 on the floor, but **cool pale
  grey-lilac with bone speckles, no golden glow**, and with a **ring on the stem** (like the harmless honey fungus, the opposite of the
  ringless lethal one); they **do not animate at all**, while the active ones glow and move. They are a static kind of the `Decor` object
  of #239 (`kind` = `lookalike`, no object logic, no hitbox, never saved). The first design, a grey-brown bracket fungus on a trunk, was
  dropped: it looked nothing like the dangerous mushrooms, so it created no doubt. Never placed on an active mushroom's zone or swell,
  over a hint zone, or over a trap, pickup or mechanism; at most two per level, only after the player has seen an active mushroom. Built:
  one in `forest-2` at world x 1980, after the lethal mushroom (x 1672, zone to 1760) and clear of it; `forest-0` and `forest-1` have none
  yet. `tools/check_tiled_links.py` (`check_still_decor`) enforces the placement rules for every map and `DecorTest` pins the placement
  and the stillness. Sprite, palette and size: `docs/ART_DIRECTION.md`, "Static look-alike mushroom".
- **Placement (a game-design choice, the author left it open).** Teach by contrast, safe first: the harmless honey
  fungus appears first, in plain view and off the route's critical path, so the player sees the pretty burst and
  learns the shape; the false honey fungus comes after that, in a room where the route passes its zone, with
  the swell telegraph as the only warning. Which world and rooms is decided when the objects are built (#295),
  with a scripted route and the hint-zone advice of [ROOMS.md](ROOMS.md); the forest-1 and forest-2 trap
  placements already fixed in D58 and D59 stay untouched.
  **As built:** harmless ones in `forest-0-lever` (x 150) and `forest-0-wall` (x 1804), the false one in `forest-2` `forest-canopy` (x 1672; `forest-entry` has no usable floor). Coordinates and reasons are in D63.

**First version and later polish (author, 2026-11-12).** The first version used the `trap-spores` sprite for the poisonous
cloud, as the existing spore puffball does (D46). During polishing the sprite was replaced by particles and shaders so that it looks "super beautiful"; this mandatory task was tracked by
[#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296) (part of the polish of
[#142](https://github.com/jlogicgames/edgard_in_kimeria/issues/142)).

**#296 spore cloud (built, approved by the author):** `FalseHoneyFungus` draws the cloud as `SporeCloud` only (34 lime haze puffs and 12 glow specks, all inside the 24x24 lethal rectangle, over the swirling shader haze `spore_cloud.frag`; look in [ART_DIRECTION.md](ART_DIRECTION.md)). If a context cannot compile the shader, `Shaders.tryLoad` returns null and the particles alone are drawn. The sprite path is removed; the `trap-spores` sheet stays because the spore puffball (D46) still draws it. Hitbox (`FalseHoneyFungus.CLOUD_SIZE`, 24x24, independent of any sprite), 1.0 s life, telegraph and timing are unchanged. Tracked by [#296](https://github.com/jlogicgames/edgard_in_kimeria/issues/296), closed with this change.

## Tablet in the original vs the remake

**Original (AUTHOR-REPORTED, 2026-11-05; mechanism not verified from the stills).** The
tablet is needed for the **final boss, the witch**. Without all of it the castle does not let
the player in, and the player starts over until the tablet is collected. The S15 page does
show "the first part of the tablet" (decoded), but the gate itself is the author's statement
only.

**Already decided for the remake** (quoted from the design docs):

- [GAME_DESIGN.md](GAME_DESIGN.md#collection-and-elixirs): "Accepted direction: recover four
  tablet fragments needed to lift the spell, one per stage. These are mandatory campaign
  objectives."
- Collectibles table: "Four tablet fragments | Required to complete the campaign. | Lift
  eternal night; one per stage." Ingredients and coins are optional.
- [D16 / campaign decision](DECISIONS.md): "One required tablet fragment per stage serves the
  goal of lifting eternal night."
- [D26](DECISIONS.md#d26-observe-plan-before-committing): mandatory exits, bosses and required
  tablet fragments must stay reachable without a forced Restart Level.

**Still open** (GAME_DESIGN.md: "fragment locations, their relationship to boss victories,
how the assembled tablet is used and L'Kardina's final fate"; "persistent tablet progress and
how Continue restores" it):

1. How the assembled tablet is used.
2. Whether the final area is gated by it.
3. Its relation to the boss.
4. Persistence on Continue.

**Not decided by the trap design (D42).** The tablet question lives in
[#136](https://github.com/jlogicgames/edgard_in_kimeria/issues/136) and
[#139](https://github.com/jlogicgames/edgard_in_kimeria/issues/139); the original's gate stays
an option there.

**Gate options for the author to choose (not decided):**

| Option | Pros | Cons |
| --- | --- | --- |
| The original's hard gate: the castle will not let you in and you start over until all of it is collected | Faithful; strong goal | "Back to the start" conflicts with D26 (no forced Restart Level) and with fast retries |
| A softer gate: the door will not open until you carry all four, and a missing fragment's level stays replayable | Fits D26; the missing piece is findable without losing progress | Needs a replayable-level rule and a clear "missing fragment" message |
| No gate; the tablet is used in a final scene only | Simplest | Loses the original's pressure to explore every stage |

## Author trap ideas (now part of D42)

The author's own ideas of 2026-11-05 were a falling stone, blade cutters as in Prince of
Persia (Broderbund) and blue sparks or lightning. They are now accepted in D42: the falling
stone for the forest (and possibly the castle), blades and lightning for the castle (later).

## Decided by the author (2026-11-05)

The trap set, explosive-trap rule and tutorial plan are decided in D42. The remaining
decisions were answered by the author on 2026-11-05:

1. **Art style:** the remake's own style ([ART_DIRECTION.md](ART_DIRECTION.md)), **without the
   1990s limitations** (palette, resolution, flat colours). Only the original's silhouettes
   and readability ideas are kept (#230).
2. **Blade cutters and lightning/sparks:** in the **castle/fortress** only (the author's
   wording). The stage names in STORY.md may differ; this document does not resolve the
   castle-versus-fortress naming and calls it the castle-fortress stage.
3. **Tablet gate and use:** stays in
   [#136](https://github.com/jlogicgames/edgard_in_kimeria/issues/136) and
   [#139](https://github.com/jlogicgames/edgard_in_kimeria/issues/139), where the original's
   gate remains an option.
4. **Video-only facts about the original** (blast delay and size, what happens at zero lives,
   whether the stake retracts, whether tall roots rise on approach, castle versus fortress at
   S14): **not needed for the remake.** Answer them only if an original behaviour is wanted
   one-to-one. The "still-open facts" list above is therefore informational.

**There are no open decisions left for the author in this document, except the future ones in
#136 and #139.**
