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
right. Complete-line layout stays fixed while letters reveal. A right-aligned,
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

The checklist names Chicken, Key and current-forest unique coins, with `[+]`/`[-]` markers
and a collected/total count. Refusal text distinguishes missing ingredients, insufficient
payment and both. If the recipe is incomplete, offer return or an unempowered fight;
return is the initial selection. Both branches start with the healer's account of the guardian
blocking the path and Edgard's question. If the full recipe is secured, the healer then
acknowledges that everything is available and announces brewing, without a refusal/return offer. Advancing that announcement
consumes the recipe and drinks the elixir automatically; there is no redundant payment choice,
return or unempowered fight option on this branch. Selected buttons use the same two edge
markers as the other menus. After payment, two lines acknowledge consumption and explain the encounter lock, followed
by a single fight choice. The author reviewed the wording in play and accepted it for the
test version (2026-10-20); replacement artwork review is recorded separately in
[portrait validation](PORTRAITS.md#author-review).

## Recipe, payment and encounter boundary

- Require secured Chicken and Key from `forest` and every authored coin identity from that
  level. The current map has six coins. Pending pickups become secured at healer entry;
  prior-level currency cannot replace a missing forest coin. Empty authored coin sets never
  qualify. Each pickup identity is counted once, including after repeated visits/deaths.
- Consume Chicken, Key and exactly this forest's coin total once; keep prior-level currency.
  Remove the two inventory icons, immediately drink the elixir and acknowledge payment.
  Spent artifact identities and the unique coin completion record remain separate from
  remaining inventory/currency. No duplicate brewing or new persistent save fields exist.
- The elixir gives 10x sword damage to this boss only; starting/max energy stays 12.
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
| New provisional pickups | Secured before leaving/returning | Roll back, including coin counter and artifact icons |
| Secured defeated enemies | Remain absent, even if their death animation had not finished | Remain absent |
| Surviving enemies | Resume retained position, direction, animation/AI and velocity with the new hero reference | Rebuilt at authored spawn/AI state; defeats from this attempt roll back |
| Bomb traps | Resume secured idle/warning/exploded state; warnings do not advance off-room | Restore the last secured trap snapshot; first-visit traps start idle |
| Moving platforms | Recreated at authored position, direction and active state | Recreated |
| Falling / vanished platforms | Recreated idle at authored position, with warning rearmed | Recreated |
| Switches/triggers | Recreated; links remain operable | Recreated |
| Linked doors, actionable walls, switched torches | Restore authored state together with switches | Recreated |
| Static collision terrain and one-way platforms | Rebuilt from the authored descriptions | Rebuilt |
| Pickup ripples, old explosions, platform warning particles | Discarded; never replayed from a previous visit | Discarded |

Platforms take the explicit restoration rule even when their fall/warning is a hazard.
Bombs take the trap-retention rule. Enemy references to old moving supports are cleared
before resumed physics binds them to the rebuilt traversal graph. One retained entry per
room replaces the prior entry on each secured exit; trips do not append room snapshots.

The shipped forest has static stair platforms, ordinary terrain/quicksand, bombs and
unlinked decorative torches; it currently has no authored moving/falling platform or
switch chain. Those objects are still covered by the restoration path and regression
fixtures. The `forest-1` wall trigger remains operable; escalator trigger targets use
the authored object name, so restored chains can toggle moving platforms too. No awkward
traversal object is excluded from rebuilding. No map geometry or existing author art was
replaced for this feature.

## Local validation, 2026-10-16

120 core tests pass. New regressions cover missing Chicken/Key/coins, combined refusal,
prior-level currency, empty authored coin sets, payment exactly once, separate completion
and inventory, encounter lock, effect expiry on defeat, retained effect on boss retry,
48/32 droplets and hit shake, fresh/held input, reveal/advance, safe choice navigation,
touch target separation and both languages.

Room-state tests cover repeated trips, live enemy retention, secured defeats during the
death animation, provisional pickups after a revisit death, warning/exploded bombs,
restored vanished platforms and linked wall/moving-platform switches. The burned-platform
fixture misses Chicken, destroys its approach platform, returns and checks that the
platform, Chicken and operable switch chain are restored on three collection trips while
secured coins stay absent. It is a state/geometry regression, not a human traversal test.

A separate real-physics replay on the shipped forest starts after enemies were defeated,
a bomb detonated and coins secured but Chicken/Key missed. Ordinary A/D/J/K input then
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

### Author review completed for the test version (#73/#75)

The release acceptance checklist has moved to
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#dialogue-and-localization). Automated movement
and framebuffer checks retain their recorded scope.

Status (reported 2026-10-20): the author completed this review on keyboard and on a
physical gamepad, including the pre-boss healer and the post-boss guide scene, and
accepted it for the test version. The elixir works as documented and is accepted as is for
the test version. The left-handed layout was also checked on keyboard. Touch devices, and
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
first-level completion: secures pickups, advances to `forest-1`, saves index 1,
creates a fresh hero and starts normal loading. No save is written at dialogue entry,
and no dialogue/checkpoint flag is persisted. The second level ends the MVP campaign
through the ending scroll instead of wrapping to the forest; see [MVP.md](MVP.md).
Paused Restart Level still restarts the
outgoing level; Exit leaves its existing save untouched. Continue after exit follows
the previous completed-level save. Restart/Exit discard the conversation normally.

Input and Esc pause/Resume follow the pre-boss rules above, including frozen text,
dialogue-only pause background and held-input suppression. The new hero has no
victory-walk lock. See [post-boss validation](POST_BOSS_VALIDATION.md) for automated,
desktop and browser evidence. Deferred device and release checks are tracked in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).
