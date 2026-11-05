# Forest healer (#84)

**Status — 2026-10-20:** The current implementation/review stage is complete for the
test version by author decision. Remaining release acceptance checks are deferred
until the planned gameplay mechanics have been added and are tracked only in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Deferral does not mean those checks passed.

The same healer who narrates the opening meets Edgard before `forest-boss`. The meeting
opens at the black midpoint of the canopy exit fade, before entering the boss room.
The world, player input and gameplay effects stop. The dedicated black dialogue screen
shows shared transparent PNG portraits: Edgard left, healer right. See
[portrait assets and replacement instructions](PORTRAITS.md). The speaker's
frame is highlighted; all five supported languages use the shared baked pixel font.
In every pre/post-boss conversation Edgard's name and dialogue are white, with his
lines aligned left. The healer's name and dialogue are green, with his lines aligned
right. Inside the dialogue frame, Edgard's spoken text wraps within the left two thirds
of the padded text area and the healer's within the right two thirds. These regions
intentionally overlap in the middle third because only the current speaker's line is
shown. This applies to first and repeat meetings, brewing/payment lines and the
post-victory guide scene; the recipe checklist, choices and `Next >` prompt keep their
own full-width layout. Complete-line layout stays fixed while letters reveal. A right-aligned,
translated `Next >` prompt appears below the dialogue only after the line is fully
revealed; it hides during typing and when choices are displayed.

Localization expanded to EN/UK/PL/DE/BE in #85 / PR #150 on 2026-10-21. Text lives
in the [UTF-8 catalogs](LOCALIZATION.md); dated EN/UK checks below retain their
original scope. See [localization validation](LOCALIZATION_VALIDATION.md) for the
five-language rendering/storage checks and remaining native-speaker/device review.

## Narrative boundary

The opening tells Kimeria's history and Edgard's departure without recipe or collection
instructions. The existing pre-boss conversation remains the place for the implemented
elixir requirements. The original DOS healer's seven-component remedy and trap warning are preserved in
[ORIGINAL_DOS.md](ORIGINAL_DOS.md) for a later narrative revision; this change does not
rewrite the current bargain or add ingredients. Portrait replacement in #90 must take
its character inspiration from the supplied DOS screenshots recorded there.

For the future campaign, required tablet fragments and optional ingredients/coins
have distinct roles; see [collection design](GAME_DESIGN.md#collection-and-elixirs).
The author has selected limited coins and a spending choice between boss elixirs
and additional fireballs. Surplus coins are not part of that direction; prices,
income and purchase limits are still open. A second currency is unselected.
See [campaign economy](GAME_DESIGN.md#economy-proposals) and
[D18](DECISIONS.md#d18-directional-controls-steppe-double-jump-and-replay-target).
These sales are not implemented in the current encounter.
The current recipe and payment rules below remain specific to this test encounter.

## Dialogue and choices

Text appears at 38 characters/second. A fresh keyboard/gamepad button press or tap first
reveals the whole current line; the next fresh press advances. Held entry inputs and OS
key repeats are ignored. Opening choices never confirms a choice with the same event.

At choices, arrows/WASD or controller stick/D-pad move the selection. Enter/Space or the
active gameplay action keys confirm; controller A and gameplay action buttons confirm.
Navigation takes priority over confirmation, including the mirrored D-pad mappings.
Touch/mouse selects a choice directly. Only Esc opens pause during the dialogue;
Resume returns to the same line/choice with held inputs suppressed. Pausing freezes typing.
The pause glitch processes the frozen dialogue (portraits, current text and choices); the
pause menu is drawn crisply above it. The underlying gameplay room is never shown.

The checklist names Bat, Mushroom and current-forest unique coins, with `[+]`/`[-]` markers
and a collected/total count. Refusal text distinguishes missing ingredients, insufficient
payment and both. If the recipe is incomplete, offer return or an unempowered fight;
return is the initial selection. On the first meeting, both recipe branches start with the
healer's account of the guardian blocking the path and Edgard's question. If the full recipe is secured, the healer then
acknowledges that everything is available and announces brewing, without a refusal/return offer. Advancing that announcement
consumes the recipe and drinks the elixir automatically; there is no redundant payment choice,
return or unempowered fight option on this branch. Selected buttons use the same two edge
markers as the other menus. After payment, two lines acknowledge consumption and explain the encounter lock, followed
by a single fight choice. The author reviewed the wording in play and accepted it for the
test version (2026-10-20); replacement artwork review is recorded separately in
[portrait validation](PORTRAITS.md#author-review).

The first unpaid pre-boss meeting uses the introduction above. After Edgard returns from a
collection trip, the healer greets him as a returning visitor and restates the existing Bat,
Mushroom and all-current-forest-coins recipe before checking what he brought. The following refusal,
ready line and choices use the currently secured inventory. Later returns repeat this recognition
and check. Recognition belongs to the live `Level`: it survives collection return and room death,
but a full level restart or a newly loaded level begins with the first-meeting introduction again.
Preparation, paid acknowledgement and the post-victory guide do not count as meetings. No visit
state is written to the level-only save.

## Recipe, payment and encounter boundary

- Require secured Bat and Mushroom from `forest-3` and every authored coin identity from that
  level. The current map has six coins. Pending pickups become secured at healer entry;
  prior-level currency cannot replace a missing forest coin. Empty authored coin sets never
  qualify. Each pickup identity is counted once, including after repeated visits/deaths.
- Consume Bat, Mushroom and exactly this forest's coin total once; keep prior-level currency.
  Remove the two inventory icons, immediately drink the elixir and acknowledge payment.
  Spent artifact identities and the unique coin completion record remain separate from
  remaining inventory/currency. No duplicate brewing or new persistent save fields exist.
- The elixir strengthens physical sword strikes tenfold against this boss only; starting/max
  energy stays 12. Magical fireballs always remove exactly 2 energy, with or without elixir.
  Six landed fireballs defeat the boss from full energy if ammunition is available.
  Each empowered hit emits 48 blood droplets instead of 32, with 1.8x thickness and 4x trails,
  and starts a 0.18-second room-bounded impact shake. The lethal hit retains its own blood and impact shake while
  cancelling the old attack shake. Normal hits, misses, body contact and stomps do not
  receive this empowered feedback. Existing completed boss-attack shakes remain unchanged.
- Choosing the fight commits the encounter. Until this boss is defeated, neither return
  to the healer/collection route nor progression to the next level is available. Death
  retries this boss at full energy with the elixir still active; dialogue does not repeat.
  Boss defeat ends the elixir immediately. It never transfers to another level/boss.
- Restart Level restores the level-entry inventory/currency and rebuilds the entire level.
  New Game, Continue, leaving the live run and level transition clear the effect. Continue
  still restores only the saved level, with no mid-level inventory or encounter persistence.

## State policy

Leaving a room secures its pickups, enemy defeats and trap state. Healer entry and return
also secure the current room idempotently. Return teleports to the first entrance with a
fresh idle hero and camera relocation; it does not invoke Restart Level or write a save.
Rooms are then traversed forward again. No free backward-room navigation is added.

| Content | Collection return / re-entry | Death in the active room |
| --- | --- | --- |
| Secured coins/artifacts | Remain absent, including paid ingredients | Remain absent |
| Fireball stock and refill identities | Keep stock and consumed authored IDs; never refill or respawn a secured charge | Restore room-entry stock and provisional refills together |
| New provisional pickups | Secured before leaving/returning | Roll back, including coin counter and artifact icons |
| Secured defeated enemies | Remain absent, even if their death animation had not finished | Remain absent |
| Surviving enemies | Resume retained position, direction, animation/AI and velocity with the new hero reference | Rebuilt at authored spawn/AI state; defeats from this attempt roll back |
| Bomb traps | Resume secured idle/warning/exploded state; warnings do not advance off-room | Restore the last secured trap snapshot; first-visit traps start idle |
| Moving platforms | Resume secured position, direction, animation and active state | Restore the latest secured state; first visit starts authored |
| One-shot falling / vanished platforms | Resume the latest secured position, phase, timers and removed state; warning light is reconstructed | Restore the latest secured snapshot; first visits begin authored |
| Triggers and levers | Recreated; each lever derives its display and action from its linked torch | Recreated |
| Portable tools, fixed torches, linked doors and actionable walls | Resume their latest secured logical/physics state | Restore the latest secured state; first visit starts authored |
| Static collision terrain and one-way platforms | Rebuilt from the authored descriptions | Rebuilt |
| Pickup ripples, old explosions, platform warning particles | Discarded; never replayed from a previous visit | Discarded |

Falling platforms use the same one-snapshot-per-room rule as other mutable owners. A
collapsed platform 115 can make optional Mushroom 95 unreachable until Restart Level; this is
an accepted consequence of preserving the level as played. The mandatory canopy exit and
boss route must stay passable even when that optional route support is spent. Death restores
the latest secured platform state; first visits begin from authored state. A warning-phase
restore creates a fresh transient torch and never replays old particles. Other mutable room
owners also use one secured snapshot per room; each new secure replaces that entry. Removed
owners remain in the identity registry until capture, then are pruned from every live list
after restore. Inactive rooms do not update. Rebuilt levers derive their state from the
restored torch, and enemy support references are rebound to the new traversal graph.

A held carryable is released onto clear static collision support near its owning room's
authored entrance before securing. This removes the old hero reference while keeping the
tool available in that room after a transition.

The shipped forest has falling platform 115 at x=1568, y=256 and Mushroom 95 at x=1728, y=208.
The real-input replay intentionally misses Mushroom 95, collapses 115, returns from the healer,
and confirms the mandatory canopy exit remains reachable while the Mushroom stays uncollected.
A fresh Level restores the authored platform and the existing full-artifact route can then
collect the Mushroom. Synthetic fixtures separately cover linked walls, escalators, falling
platform phases and shared target names; the `forest-1` wall trigger remains operable. No
map geometry or author art is changed for this feature.

Because a one-shot support can permanently close an optional pickup route until reset, its
warning and intended consequence must be clearly telegraphed during manual acceptance.
This ticket does not add a new UI or dialogue system; any additional teaching is separate
work.

## Local validation, 2026-10-16

The platform-rearm assertions in this dated room-state test record predate the accepted
D26 contract and are superseded for current design by D26/#158. Other results below remain
historical evidence only for the behavior and test version described at that date.

120 core tests pass. New regressions cover missing Bat/Mushroom/coins, combined refusal,
prior-level currency, empty authored coin sets, payment exactly once, separate completion
and inventory, encounter lock, effect expiry on defeat, retained effect on boss retry,
48/32 droplets and hit shake, fresh/held input, reveal/advance, safe choice navigation,
touch target separation and both languages.

Room-state tests cover repeated trips, live enemy retention, secured defeats during the
death animation, provisional pickups after a revisit death, warning/exploded bombs,
restored vanished platforms and linked wall/moving-platform switches. The burned-platform
fixture misses Bat, destroys its approach platform, returns and checks that the
platform, Bat and operable switch chain are restored on three collection trips while
secured coins stay absent. It is a state/geometry regression, not a human traversal test.

A separate real-physics replay on the shipped forest starts after enemies were defeated,
a bomb detonated and coins secured but Bat/Mushroom missed. Ordinary A/D/J/K input then
traverses both rooms and collects the artifacts, using the retained enemy/trap state and
rebuilt geometry. It repeats three trips without invulnerability, additional coin pickups
or duplicated artifacts, then pays the complete recipe. Initial revisit times are 14.78 s
(entry) and 5.13 s (canopy); later passes are 14.25 s and 5.00 s. These cover that specific
second-pass state, not first-time discovery or every possible surviving-enemy arrangement.

Desktop and TeaVM release builds pass. A temporary hidden-window libGDX harness, using
isolated preferences, rendered missing/ready/paid dialogue and both About pages in English
and Ukrainian. The full text of every dialogue line fits its actual baked font and box;
framebuffer images were visually inspected. About with controller instructions also fits
both handedness layouts in both languages (312-pixel content panel at 640x360). The harness is not part of the shipped game.
Only local checks were used, per the author's choice; no source was sent to Snyk.

## Room-state retention validation, 2026-10-24 (#158)

### Earlier validation, superseded platform policy

The first set of #158 checks used a platform-rearm rule that was later replaced by the
accepted “optional Mushroom may be missed until Reset Level” policy. Its 212-core-test, named
parameter, sprite-art, localization, desktop/web build results and browser handoff smoke
are historical evidence for that earlier revision, not validation of the final platform
snapshot behavior. The browser smoke did verify held-tool handoff with debug room skips and
F2 invulnerability; it did not exercise a full unassisted route. The separate 2026-10-16
results above predate #158 and remain historical as well.

### Final platform-preservation policy

The focused `RoomTest`, `RoomReplayTest` and `ArtifactInventoryTest` run passed **42 tests**.
It covers latest secured falling-platform warning and mid-fall snapshots, inactive-room
freeze, fresh transient warning-light reconstruction, removed-owner pruning and death
rollback. The real-input forest replay intentionally misses Mushroom 95, collapses platform 115,
returns from the healer, and reaches the mandatory canopy exit while confirming the Mushroom
remains uncollected. It does not try to collect the inaccessible optional Mushroom. A fresh
Level restores the authored platform and the existing artifact route collects the Mushroom.
The full suite passed **213 core tests**, with zero failures or skips. The named-parameter,
sprite-art, five-language localization, `:core:check`, desktop JAR and TeaVM release-build
checks also passed. These are automated route/build checks, not a desktop/web human
playthrough. Manual fair-warning acceptance remains open.

### Speaker-region layout audit, 2026-10-24 (#156)

The baked-font regression now derives its spoken-line set from every typed `HEALER_` and
`EDGARD_` message key, excluding the three choice labels and the separate continuation hint.
Each catalog is measured against the view's computed two-thirds speaker region and the height
available inside the frame. The general localization layout test no longer repeats the older
full-width measurement.

A temporary LWJGL preview used the shared `KimeriaGame` renderer, `HealerDialogueView` and
shipped baked font to capture 55 desktop frames at 1280×720 for the five catalogs. Each set
covers partial and complete text for both speakers, choices, paused partial text and choices,
the paid acknowledgement/lock and all three post-victory lines. The states were seeded by the
preview harness; this verifies presentation, not the input or gameplay transitions. Contact
sheets were visually inspected for wrapping, portrait highlights, alignment and pause layering.
The release TeaVM build passed. A Chromium smoke run against the TeaVM development build
covered English full healer text, partial then complete Edgard text, fresh-Return advancement
through refusal into choices, and Esc pause followed by Resume. The dialogue remained legible
without clipping, and Resume preserved the fully revealed line. The browser run did not cover
the other four catalogs, paid acknowledgement or post-victory guide; those presentation states
are covered by the five-language desktop captures and baked-font checks above. This is not a
native-speaker wording review; existing browser observations remain in
[localization validation](LOCALIZATION_VALIDATION.md).

For #157, the updated desktop renderer displayed the returning greeting, recipe reminder and
current-inventory line in all five catalogs at 1280×720. The text stayed within each speaker's
two-thirds region, with portraits, speaker highlights and continuation prompt in their expected
positions. This presentation check does not replace native-speaker review.

The #157 TeaVM browser smoke covered English in IAB Chromium. A fresh game showed the original
first-meeting introduction; returning with no items showed the new greeting, missing-both
checklist and choices. The recipe reminder fit its two-thirds region. Pausing during partial
reveal and resuming retained the partial text, and a second collection return showed returning
recognition again on the third meeting. Debug room skips and invulnerability were used to reach
these scenes; this was not a full human route playthrough. The browser check did not cover the
other four catalogs, which were checked with the desktop renderer above.

### Author review completed for the test version (#73/#75)

The release acceptance checklist has moved to
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#dialogue-and-localization). Automated movement
and framebuffer checks retain their recorded scope.

Status (reported 2026-10-20): the author completed this review on keyboard and on a
physical gamepad, including the pre-boss healer and the post-boss guide scene, and
accepted that version for the test build. At that time the elixir was described as a sword
bonus; the later correction in #155 clarifies that it does not strengthen magical fireballs.
The left-handed layout was also checked on keyboard. Touch devices, and
the left-handed layout on touch and gamepad, are deferred to the pre-release list.
The post-victory scene is implemented by #86 as described below.

## Author feedback, 2026-10-18

After the author's follow-up, the complete-recipe branch retains the opening guardian
introduction and Edgard's question, then acknowledges the full recipe and announces brewing.
A fresh advance after that fully revealed line pays and drinks automatically. Return and
unempowered fight choices appear only when the recipe is incomplete. The gold selected
label and both edge markers use the same renderer as ordinary menu buttons.

Further author feedback: remove the top Pause button; only Esc opens the dialogue pause
menu (Resume / Restart Level / Exit to Menu). Empowered hits now emit 48 droplets versus
32 normally, preserving a mix of large and fine drops. Latest author
tuning uses 1.8x droplet thickness, 4x trail length (up to 18 pixels), stronger crimson and
wider floor splashes for an unmistakable strength-elixir hit. Lifetime and cleanup remain
unchanged.

Pause background correction: Esc is processed before rendering the pause frame. The current
dialogue, including its exact revealed text and selected choice, is drawn into the existing
post-process buffer; the pause menu is rendered above the glitch. A real libGDX render/input
check asserts that the world is never rendered or updated throughout dialogue/pause/Resume,
that text stays frozen on pause, and that Resume preserves the same dialogue state.
123 core tests and desktop/web builds pass.

## Post-victory guide scene (#86)

After the forest guardian's death animation and Edgard's complete automatic exit,
`Level.victoryExitComplete()` gates a second meeting with the same healer. Defeat,
damage, boss retry and ordinary room exits do not open this scene. The same dialogue
model, portraits, speaker frames, font and reveal/advance input path are reused;
recipe/payment UI and fight/return choices are absent.

The three lines acknowledge the guardian's defeat and the open path, Edgard notes
that darkness still hangs over the kingdom, and the healer promises to guide him
as the journey continues. The author approved this meaning on 2026-10-19. Text lives
in the five `assets/localization/*.json` catalogs, addressed by typed `Msg` keys;
no new destination, quest or explanation of the darkness is added.

A fresh final advance emits CONTINUE once. KimeriaGame then performs its existing
level completion: secures pickups, and, as `forest-3` is the last level, opens the ending
scroll instead of wrapping. No save is written at dialogue entry,
and no dialogue/checkpoint flag is persisted; see [MVP.md](MVP.md).
Paused Restart Level still restarts the
outgoing level; Exit leaves its existing save untouched. Continue after exit follows
the previous completed-level save. Restart/Exit discard the conversation normally.

Input and Esc pause/Resume follow the pre-boss rules above, including frozen text,
dialogue-only pause background and held-input suppression. The new hero has no
victory-walk lock. See [post-boss validation](POST_BOSS_VALIDATION.md) for automated,
desktop and browser evidence. Deferred device and release checks are tracked in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).

## Tutorial tales (#220, D41)

The same dialogue system opens twice around `forest-0`, in fairy-tale language and with no
named controls: a six-line **start tale** (the last line is Edgard's: he understood and is in a hurry) on a New Game run's first entry into the level,
and a four-line **farewell** when the last room's `Checkpoint` is reached (praise, "you are
ready" and a parting blessing), after which forest-1 loads as before. They are scripted
(`HealerDialogue.Kind.TUTORIAL_START` / `TUTORIAL_END`): no checklist, no choices, one
`CONTINUE`. Reveal/advance, portraits, speaker regions, `Next >` and Esc pause are the
ones described above; the game is in the HEALER state, so no level timer advances.

Each opens at most once per run (consumed on opening) and is never repeated by death,
retry, Restart Level or repeated Checkpoint callbacks. Nothing is saved: a save only exists
after a level is completed, so Continue never lands in forest-0 and legacy saves skip it.
Wording is a proposal for author review; see [D41](DECISIONS.md#d41-the-healers-tales-around-the-forest-0-tutorial)
for texts, rules and what is not verified.

## Ingredient rename (D43, #229)

The optional ingredients are Bat and Mushroom (formerly Chicken and Key); the rules above are
unchanged. Level property: `ingredients=BAT,MUSHROOM`. Legacy saves load as before because the
inventory is run-only.
