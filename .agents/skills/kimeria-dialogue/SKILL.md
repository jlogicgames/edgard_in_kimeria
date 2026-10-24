---
name: kimeria-dialogue
description: Implements and reviews Edgard in Kimeria healer conversations, localized dialogue text, reveal/advance timelines and dialogue-to-gameplay boundaries. Use for pre-boss choices and post-boss guide scenes.
---

# Kimeria dialogue

Read [healer behavior](../../../docs/HEALER.md),
[agreed story](../../../docs/STORY.md) and D14 in
[decisions](../../../docs/DECISIONS.md) before changing a conversation.
Paths below are relative to the repository root.

Reuse `ui/HealerDialogue.java` and `ui/HealerDialogueView.java`: the same healer
portrait, speaker highlight, typewriter and black NES-style screen serve both meetings.
For artwork, follow [art direction](../../../docs/ART_DIRECTION.md) and the
kimeria-art-direction skill: the remake/remaster preserves the original artist's
style with richer color, less grain and more natural drawing. Dialogue portraits
are `assets/images/portraits/edgard.png` and `healer.png` (256×256 transparent PNGs,
up to 256 palette entries); see [replacement and review](../../../docs/PORTRAITS.md).
Use full-image `Assets.region(path, TextureFilter.Linear)` through the shared cache,
preserve aspect ratio inside the 64×64 logical slot and retain its single Y-down flip.
Linear filtering applies to these detailed portraits; other textures retain their policy. Never
embed portrait pixel matrices or dispose shared textures in the view. Verify pre-boss,
paid and post-boss images in normal/paused scenes on desktop/web in all five languages;
record author artwork comparison against DOS references separately from automated checks.
Keep stable typed keys in `localization/Msg.java` and text in every
`assets/localization/*.json` catalog (currently EN/UK/PL/DE/BE); follow
[localization](../../../docs/LOCALIZATION.md) for validation and font generation.
Do not invent destinations, quests or character history; distinguish proposed wording
from author-approved story.
Post-victory dialogue has no recipe checklist, payment or fight/return choices.

The pre-boss first/repeat meeting flag belongs to the live `Level`. Read and mark it only
when the game opens a genuine unpaid healer meeting; `prepareHealer()` also runs for
collection returns and encounter commitment, so it must not consume the visit. Keep the
flag through room death and collection return. A new `Level` on Restart Level or a new run
starts with the first-meeting dialogue. Paid acknowledgement and `afterVictory()` remain
separate scenes and do not count as pre-boss meetings.

Trace `KimeriaGame.updateHealer`, `chooseHealer`, pause/Resume and the originating
Level callback together. First fresh input reveals a line; a later fresh input advances.
Opening choices consumes its event; navigation wins over confirmation. Snapshot held
keyboard/touch input at entry and Resume, retain controller edge tracking, and prevent
the entry frame from processing dialogue input. Repeated final presses must not repeat
completion. Reuse input/rendering skills for their device and coordinate details instead
of maintaining another mapping or renderer here.

Only Esc currently pauses dialogue. Freeze typing and simulation, render the exact
conversation under the pause effect, and resume the same line/choice with holds suppressed.
Restart/Exit must discard the conversation through the existing lifecycle cleanup.
For post-boss scenes require the finished death animation AND automatic exit, never just
a defeated boss. Route final advance through the normal completion/save operation once;
retain level-only persistence and replace the scripted hero before gameplay resumes.
See [rooms](../../../docs/ROOMS.md) and the room-state skill for lifecycle policy.

Run `:core:test` (especially HealerDialogueTest, IntroInputTest, BossCombatTest and
SavedProgressTest), `validateLocalization`, `buildDesktop` and `buildWeb` as appropriate.
Regenerate baked fonts with `:lwjgl3:bakeFonts` when catalog edits require new glyphs. In real desktop/web
render loops verify death → exit → dialogue → single-room second level, then ending → menu with the run save removed,
held/released input, repeated final advances, pause/Resume and renewed movement/jump/attack.
For scripted device checks, explicitly select language/handedness in memory so saved
player preferences cannot invalidate the intended bindings.
Measure every new line with the actual baked font in all supported languages; model tests cannot
prove layout. Report automated checks, actual desktop/browser/device observations and
remaining human readability/wording review separately. Validate this skill's frontmatter
and relative documentation links when editing it.
