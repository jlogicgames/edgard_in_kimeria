# Forest boss (#83)

Current MVP order: the forest boss ends **level one**. After death, automatic exit and
the healer guide dialogue, start the single-room `forest-1` as **level two**. Its ending
returns to the menu and clears progress; the campaign no longer wraps. See [MVP.md](MVP.md).
Historical wrap references in the validation notes below describe earlier builds.

`forest-boss` is the authored third forest room (448 × 368, entrance 2032,272).
The author-owned Boss object (2304,304) and Boss.png are preserved. Physics settles the
boss's feet onto y=320; its render frame is 48 × 32 regardless of the map marker's 32 × 32.
No authored map geometry or sprite pixels were replaced for this implementation.

## Sheet inspection

Boss.png is 192 × 320: four 48 × 32 frames per row, indexed from zero below.
The author's **7th row** is row 6 here (normal); **3rd row** is row 2 (counter).

| Row | Art | Use |
| --- | --- | --- |
| 0 | Walking without raised axe | Unused alternative walk |
| 1 | Walking with axe | Ground pursuit, 0.1 s/frame |
| 2 | Raised axe, wind-up, sweep, extended axe | Counter: columns 0–1 warning, 2–3 strike, 3 recovery |
| 3 | Recoil alternating with white silhouette | Interruption reaction, 0.05 s/frame |
| 4 | Red collapse into particles | Death, 0.2 s/frame |
| 5 | Standing with raised axe | Idle and ordinary recovery |
| 6 | A second axe sweep with a different arc | Normal melee, approximately 0.1556 s/frame |
| 7 | Seated on a throne | Initial state, 0.2 s/frame |
| 8–9 | Empty | Never sampled |

There is no separate getting-up animation, dedicated counter charge, or aftermath pose.
Wake switches throne → walk. After the recoil/white reaction and the existing bounce cue,
the counter shows both preparation poses from row 2 (0.575 s each), then both strike poses
(approximately 0.0667 s each), then holds its extended-axe final pose during recovery (0.3 s). Orange colour
remains throughout this uninterruptible sequence; the warning pulses. Normal attacks keep
row 6 and ordinary recovery returns to row 5. The two attack sequences never share frames.

## Balance values

| Parameter | Value |
| --- | --- |
| Initial/max energy | 12, with or without elixir |
| Normal sword damage / hits required | 1 / 12 distinct swings |
| Consumed elixir sword damage / hits required | 10 / 2 distinct swings |
| Pursuit speed | 65 px/s; player remains 100 px/s |
| Wake threshold | 160 px horizontal body-centre distance, with vertical body overlap |
| Normal duration | approximately 0.6222 s; unchanged hero is 0.7 s |
| Normal wind-up / damaging / recovery art | 0–0.3111 / 0.3111–0.4667 / 0.4667–0.6222 s |
| Recovery before pursuit | 0.3 s |
| After interruption: recoil → warning → counter → recovery | 0.2 → 1.15 → 0.1333 → 0.3 s |
| Counter escape allowance | 1.35 s from interruption before damage starts |
| Death presentation | 0.8 s, then automatic exit at 100 px/s |
| Completed attack shake | 0.18 s; at most 1.25 world pixels, decaying |

Twelve normal swings leave room for combat movement within the 20 s mastered-room target.
A full successful human route still needs timing; this does not establish compliance with
that target or exempt the boss from it. The 10x rule multiplies damage, not fight duration.

Boxes below are in the right-facing 48 × 32 sprite, mirrored around the body centre:

| Box | x, y, width, height |
| --- | --- |
| Generous sword/stomp/terrain body | 7, 4, 18, 28 |
| Normal lower axe sweep | 24, 22, 16, 8 |
| Counter axe sweep | 24, 12, 16, 15 |

The boss starts a normal attack when its future axe box overlaps the hero. Facing and
position stay locked through normal attack, recoil, warning and counter; there is no
tracking lunge. Retreating and jumping are both available after sword recovery. At the
latest possible beginning-of-swing interruption the remaining 0.7 s hero lock still leaves
about 0.65 s before the counter can damage. Further hits during recoil, warning, strike
or counter recovery damage the boss without restarting or interrupting that sequence.
After counter recovery, every new ordinary attack becomes interruptible again.

Ordinary approach, normal melee and ordinary recovery all react to a landed sword hit by
cancelling their current action and entering the recoil/counter sequence. This closes the
reach-boundary gap: the hero's sword can hit during WALK just before the boss starts a
normal attack. Previously that hit was counted only as damage; the following frame began
an uncancellable normal strike inside the already-counted player swing. Counter recovery
is a separate state and remains protected until it finishes, avoiding recursive counters.

Only the player's weapon reduces energy. Each player swing records whether it already
hit this boss. Top contact snaps the player's feet onto the boss top and bounces at
260 px/s without damaging either actor. The solid body blocks side and underside passage without contact damage, including while swinging.
Only the active axe strike kills the hero.
Ordinary mobs and the hero's animation, input and movement timing retain their rules.

Boss damage resolves after the player's sword and movement. A same-frame interruption
wins over the normal strike or its pending completion shake. Lethal sword damage wins over
that boss's remaining attack/body damage. A hero already killed by another hazard cannot
land a posthumous sword hit. Further hits during counter still deal damage, including 10x;
a lethal one cancels the attack and its completion shake. An empowered sword hit still
creates its own brief impact shake, including on the lethal hit. There is no recursive counter queue.

Shake is only a render-camera offset; it never changes the chase target or collisions.
An axis narrower than the viewport keeps its existing centred framing. In this particular
room horizontal shake is therefore zero; vertical shake uses the eight pixels of available
room framing. Pause, death, room transition and retry clear the effect. Boss death clears old attack
shake; its final empowered sword hit may start its own impact shake. A
completed counter has its own completion shake; the interrupted normal has none.

## Retry, elixir and completion

Death rebuilds the boss at full energy and throne, respawning the hero at 2032,272 with
the existing arrival animation. Consumed elixir is run-only inventory state, outside room
rollback. The healer consumes Chicken, Key and all unique forest coins after verification,
then calls `drinkBossElixir("forest")`; no automatic artifact activation occurs.
[HEALER.md](HEALER.md) describes the implemented dialogue and collection-return policy.
The encounter locks on choosing the fight: no return or onward passage until boss defeat,
and no repeated bargain on retry. The effect applies only to this boss and ends immediately
on its defeat; Restart Level, next level, New Game, Continue and exit also clear it.
The removed `bossEnergyMultiplier` must not be restored or combined with this multiplier.

Boss death immediately stops control and damaging interactions. At the end of the 0.8 s
death animation, remove the boss from the room objects and stop drawing it. Keep only its
completion state so the hero begins and finishes the protected victory walk. The camera stays fixed; the hero walks beyond the
right viewport edge (also when the room is narrower than the viewport). Terrain, traps,
clambering, input, contact and falling cannot block or kill this scripted walk. Completion
is latched before calling the existing `loadNextLevel`, which retains the existing level-only
save format. Checkpoint contact and F5 are blocked throughout a map containing a boss,
including before entering its chamber. The separate F4 developer level-skip remains a
deliberate debug tool.

## Validation and remaining playtest (#73/#75)

`BossCombatTest` exercises the real forest map, player input and room update order with
rendering disabled. It covers throne/wake/pursuit, one damage per swing, normal and elixir
energy, interrupting wind-up/active/completion frames, counter damage without interruption,
jump-and-retreat after full sword recovery at 30/60/120 steps per second, top/body contact,
missed-attack shake, reset/arrival with retained elixir, lethal counter damage and exactly-once
completion after the protected walk. `ArtifactInventoryTest` covers clearing the effect at
run/level boundaries. Room tests now match the authored three-room map and reject the old
automatic artifact bonus.

2026-10-15 local checks:

- Final desktop and TeaVM release builds succeeded.
- All 89 core tests passed after the completion-boundary sample was corrected. The repeated
  failure was handed to the author under the working agreement; the author explicitly
  authorized the corrected rerun.
- Actual browser rendering/input were checked at approximately 120 FPS: throne at entrance,
  waking/pursuing on approach, orange counter warning after a wind-up interruption, a
  sword/recovery/jump-and-retreat sequence, and death returning to the chamber entrance.
  A temporary local preview booted directly into the room; those preview hooks were removed
  from source and are not part of the shipped game.
- Snyk transmission was rejected by automatic review; the author selected local checks only.

Human validation is still open. Automated checks and the limited browser check above do not
prove fairness or teachability. In a normal run with invulnerability off:

1. Observe the throne before approaching. Approach just inside/outside 160 px, retreat,
   and confirm the boss never re-seats until retry.
2. Hit during the wind-up, axe strike and completion boundary. Confirm orange warning,
   no old strike damage/shake, and one energy loss per swing. Jump away once sword recovery
   ends; repeat near both walls and under the four authored platforms. Test a deliberately
   late reaction to distinguish a fair death from an unavoidable one.
3. Strike repeatedly during warning/counter. Confirm damage continues without restarting or
   cancelling it. Land a lethal sword hit during counter. Complete/miss attacks, pause during
   shake, resume, die, retry and transition: no residual shake or delayed damage.
4. Land on the head while falling, including near an axe strike. Confirm bounce with no
   energy loss or player death. Test side contact both idle and swinging.
5. Complete without artifacts, then with #84's consumed elixir when that feature is available.
   Check 12 versus 2 sword hits, unchanged starting energy, retained effect after death,
   no repeated bargain, and a fresh effect-free run after Restart Level/Continue.
6. Watch death and automatic exit. Hold left/jump/attack; verify the hero crosses the right
   wall, cannot die, leaves the viewport, then completes/saves once. Try Checkpoint/F5 before
   defeat and during exit. Check the next level on Continue.
7. With a new player, ask them to explain head contact versus sword damage and the orange
   warning without prompting. Record misunderstood rules for #73; do not mark teaching
   complete merely because the scripted scenarios pass.
8. Time at least three mastered normal fights and three elixir fights from usable entrance
   input to boss defeat, recording failed attempts separately. Also record the fixed death
   presentation and exit walk separately. Retain the room's 20 s target; tune on evidence.

## Visible health and hit feedback

The active boss room shows a fixed HUD bar with twelve image segments, including while the
boss sits on the throne and during the protected victory walk. It disappears with the room
or level. The label is localized (English/Ukrainian); a green potion and `x10` indicate the
consumed elixir and remain on boss retry. No automatic artifact bonus is implied.

Each landed sword swing triggers one 75 ms impact sound, a 32-droplet crimson blood spray
at the contact side (48 droplets with 1.8x thickness, 4x trails and a brief impact shake while the elixir is active),
and a 250 ms glow/fade over the lost slots. Counterattack hits have the same feedback without
changing the counter's state or timer. Lethal hits still show their impact. Missing a swing
or bouncing off the head produces no sword-damage feedback.

On death, remember the boss's actual remaining energy. The rebuilt boss immediately has
full real energy, but its displayed bar fills from the remembered value to full over **250 ms**
with one quiet restoration cue. This happens inside the existing camera pan/appearance;
there is no extra delay, text message, confirmation or input lock. Even an undamaged boss's
frame briefly glows on retry. Pause freezes the animation and does not replay its sound.
A fresh level/room entry does not play the retry cue. Feedback uses real gameplay time,
so death slow motion does not stretch it, and visual state never affects combat health.

Blood uses code particles with gravity, streaks and brief floor splashes (up to 0.9 s),
including on the lethal hit; contact and stomping emit none. The star texture has been removed.
The frame and filled/empty slots are independent PNGs in
[`assets/images/ui/boss`](../assets/images/ui/boss/README.md). Dimensions and gaps are grouped
in `BossHud`; equivalent-sized art replacements require no code change. The short impact
and restore WAVs are independently replaceable too.

`BossFeedbackTest` covers refill duration, ten-slot elixir feedback and removal of effects
from the previous attempt. The gameplay integration tests check one sound per swing,
feedback during an uninterruptible counter, full actual energy during the display refill,
no repeated cue and the unchanged arrival duration.

Follow-up verification on 2026-10-15: all **94 core tests** pass; desktop and TeaVM release
builds succeed. Real libGDX framebuffer checks verified normal/empty slots, Ukrainian label,
elixir icon, partial restoration during arrival, full restoration, and sword impact during
the counterattack. The capture harness used isolated preferences and was removed afterwards.


Follow-up on 2026-10-16: the reported hit-and-retreat sequence was reproduced in a failing
regression test before the fix. All **98 core tests** now pass, including repeated normal
interruptions over three counter cycles, approach-range hits after the first counter,
ordinary recovery interruption and nonrecursive damage during counter recovery. The full
second attack row is used for preparation, strike and recovery; normal attack timing and
the existing counter escape allowance are unchanged.


## Author adjustments, 2026-10-16

Normal attack now uses the **7th sheet row**, counterattack the **3rd**, counting from one.
Both the full normal animation and the counter strike play at **1.5x their previous speed**:
1.4 → 0.9333 s and 0.3 → 0.2 s respectively. This supersedes the earlier half-player-speed
normal requirement. The 1.15 s counter warning remains unchanged for the existing sword
recovery and jump-away allowance. Pursuit, health and recovery durations are unchanged.
The lethal axe boxes follow the newly assigned rows.

**F6** starts the normal fade into the next room, securing pickups just like the room exit.
It ignores paused gameplay, an active fade, death/arrival, victory and the last room. It
never completes a level or writes a save. For a fresh run: **F4** reaches forest, then
**F6 twice** opens the healer; choose the fight to enter forest-boss. F5 remains blocked on the boss map.

Regression checks cover actual F6 input dispatch and fade through both rooms, no-op in the
last room/while paused, exact one-based art rows for every attack pose, accelerated durations,
and removal from the room after death without stopping the exactly-once victory sequence.


## Additional author adjustments, 2026-10-16

Attack speed increased by a further **50%**: the total multiplier is now **2.25x** the
original speed. Normal attack is approximately **0.6222 s** (previously 0.9333 s); the
counter strike is approximately **0.1333 s** (previously 0.2 s). Rows, damage windows within
the animation, movement and energy remain assigned as above. Counter warning and recovery
remain unchanged, preserving time to finish the hero's sword attack and jump away.

During boss death and the protected hero exit, the entire active room now continues its
normal update/cleanup pass: fireflies, torch fire, sky/weather, moving platforms,
enemies and hazards. Queued visual effects still spawn and finish. The hero uses the same
scripted movement and remains protected from damage and terrain. Pause still stops updates;
completion/save is still latched exactly once after the hero leaves the viewport.

The victory regression exercises real fireflies, a burning torch and a detonating bomb, verifies
movement during the exit and pause behavior, and checks that the living world cannot kill
the scripted hero. The completion-frame interruption check follows the new attack duration.


## Solid body and blood, 2026-10-16

Body contact no longer kills. Horizontal overlap is resolved on the hero's arrival side;
terrain is respected when the boss crowds the hero against a wall. Falling onto the head
still bounces, and jumping into the underside is blocked. Active axe strikes remain lethal.

The impact-star image and its render/timer code were deleted. Each landed sword hit now
queues one independent 32-droplet blood spray, directed away from the attacker, with
gravity, red streaks and brief floor splashes. It lasts up to 0.9 s, survives lethal hits
and is cleared by the normal world cleanup or room retry. It uses the existing white
quad like other procedural effects, so no new image or particle texture is required.

All **104 core tests** pass, and desktop/web builds succeed. Regression checks cover
blocking held movement from both sides without damage, safe stomp and close sword contact,
feedback during counters, and finite particle motion/cleanup. A real libGDX framebuffer
check confirmed the red spray is visible against the boss-room fog and background.

Author tuning: blood droplet count, sizes and streak lengths were halved; floor splashes
are thinner and colours use a darker, less saturated red to match the room palette.


Victory transition fix: advancing to the next level now creates a fresh player before
loading, matching room transitions and Restart Level. The protected exit flag and outgoing
animation can no longer freeze the hero when the final boss wraps back to forest-1.
A regression completes the boss exit, invokes the real level-advance/save path, and checks
movement, running animation, sword input and jumping on the shipped first-level map.
All **105 core tests** pass; desktop and web builds succeed.


The planned next story beat is a separate healer dialogue after the automatic exit and
before the next level (#86), reusing #84's character and dialogue system. The healer's
continuing role as Edgard's guide is recorded in [STORY.md](STORY.md) and D14. This scene
is accepted direction, not part of the currently implemented victory flow.


## Healer integration, 2026-10-16 (#84)

The pre-boss healer, one-time recipe payment and repeated collection returns are implemented;
see [HEALER.md](HEALER.md). F6 into the final room now opens that dialogue first. The effect
applies only to this boss encounter and ends immediately on boss defeat. While active, each
sword hit emits 48 droplets and its own brief impact shake, including the lethal hit.
120 core tests and desktop/TeaVM release builds pass locally. Dialogue/About rendering was
checked in both languages. Final author review in play, including physical controller and
touch comfort, remains open; no post-boss #86 scene is included.


## Removed legacy ambience/mechanic

Bat proximity no longer slows gameplay. Death retains its own deliberate slow-motion
presentation. Fog is now exclusive to the menu backdrop; no traversal or boss room creates
it. The victory regression retains firefly/torch/hazard update and pause coverage, and
explicitly asserts that gameplay ambience contains no fog.
