# Skills index

Shared skills for every coding agent (Claude Code, Codex, Qwen). Each skill is a thin order of work; the rules live
in `docs/`, and [docs/STYLE_SYSTEM.md](../../docs/STYLE_SYSTEM.md) says how the design skills fit together. Run
`python3 tools/check_skills.py` after any change here (also `./gradlew validateSkills`, part of `:core:check`).

## Layout

- `.agents/skills/<name>/SKILL.md` is the skill. Its frontmatter `name` equals the folder name.
- `.claude/skills/<name>/SKILL.md` is a pointer for Claude Code, which reads only `.claude/skills`: the same `name`
  and `description`, a link to the shared skill, nothing else. Every `kimeria-*` skill has one.
- `agents/openai.yaml` (optional) is Codex's display name, in English like everything else in the repo.
- The Codex desktop app's Claude import (`external-agent-import-sync-enabled` in `~/.codex/config.toml`, "Migrate
  skills from ... to ...") copies `.claude/skills/<name>` to `.agents/skills/<name>` when that folder is missing. With
  pointer names equal to the shared names there is nothing for it to create. A folder it does create shows up as an
  unlisted skill and fails `check_skills.py`; delete it.

## Project skills

| Skill | Use for | Rule and reference |
| --- | --- | --- |
| [kimeria-room-design](kimeria-room-design/SKILL.md) | Planning or auditing a room: the card before the map, its novelty and density | D72, D105, D84, [ROOM_CARDS.md](../../docs/ROOM_CARDS.md) |
| [kimeria-trap-design](kimeria-trap-design/SKILL.md) | A trap from idea to placement: the card before the code | D71, [TRAPS.md](../../docs/TRAPS.md) |
| [kimeria-game-text](kimeria-game-text/SKILL.md) | Any player-facing string, in all five languages | D74, [TEXT_STYLE.md](../../docs/TEXT_STYLE.md) |
| [kimeria-art-direction](kimeria-art-direction/SKILL.md) | Sprites, HUD icons, portraits, illustrations | D21, D76, [ART_DIRECTION.md](../../docs/ART_DIRECTION.md) |
| [kimeria-parallax](kimeria-parallax/SKILL.md) | Parallax layers and the foreground: installer, rules and gate | D108, D120, D143, [ART_DIRECTION.md](../../docs/ART_DIRECTION.md#forest-look-and-parallax-layers-accepted-d108) |
| [kimeria-sound](kimeria-sound/SKILL.md) | Generating, wiring and auditioning a sound effect | D73, [SOUND_DIRECTION.md](../../docs/SOUND_DIRECTION.md) |
| [kimeria-particles](kimeria-particles/SKILL.md) | Dust, sparks, smoke, blood, grit: the project's particle classes | D73, [DEVELOPMENT.md](../../docs/DEVELOPMENT.md#particles-and-the-allocation-guard) |
| [kimeria-level-authoring](kimeria-level-authoring/SKILL.md) | Tiled maps: geometry, spawn points, links, measured routes | D26, [ROOMS.md](../../docs/ROOMS.md) |
| [kimeria-room-state](kimeria-room-state/SKILL.md) | Room transitions, death rollback, collection returns, restart | D3, [ROOMS.md](../../docs/ROOMS.md#transition-and-retry) |
| [kimeria-combat](kimeria-combat/SKILL.md) | Enemies, the boss, hitboxes, stomps, enemy wind-ups | D10, [BOSS.md](../../docs/BOSS.md) |
| [kimeria-dialogue](kimeria-dialogue/SKILL.md) | Healer conversations: timeline, portraits, gameplay boundary | [HEALER.md](../../docs/HEALER.md) |
| [kimeria-input-flows](kimeria-input-flows/SKILL.md) | Keyboard, gamepad and touch bindings, fresh-press handling | D9, [DIRECTIONAL_CONTROLS.md](../../docs/DIRECTIONAL_CONTROLS.md) |
| [kimeria-progress-settings](kimeria-progress-settings/SKILL.md) | Options, level saves, Continue and New Game | D8, [DEVELOPMENT.md](../../docs/DEVELOPMENT.md#game-state-progress-and-saves) |
| [kimeria-rendering](kimeria-rendering/SKILL.md) | Y-down world and Y-up text, shaders, viewport, HUD drawing | [DEVELOPMENT.md](../../docs/DEVELOPMENT.md#architecture) |
| [kimeria-libgdx-rules](kimeria-libgdx-rules/SKILL.md) | Any libGDX code: the project rules the generic skills cannot know | [AGENTS.md](../../AGENTS.md) |

## Process skills

| Skill | Use for | Rule and reference |
| --- | --- | --- |
| [kimeria-issue](kimeria-issue/SKILL.md) | Writing, filing, updating, ticking and closing a GitHub issue in the project's format | [AGENTS.md](../../AGENTS.md) ("Gaps written into docs must be tracked") |
| [kimeria-feedback-triage](kimeria-feedback-triage/SKILL.md) | A round of playtest or reviewer feedback: analysis, the author's decisions, issues, the playtest record, ticking boxes that waited for players | D66, [PLAYTEST_1.md](../../docs/PLAYTEST_1.md) |

## Vendored libGDX skills

Reference for the libGDX API, each with a project note at the top; provenance and local edits in [SOURCE.md](SOURCE.md).
Read [kimeria-libgdx-rules](kimeria-libgdx-rules/SKILL.md) first: where they disagree, the project rules win.

| Skill | Use for |
| --- | --- |
| [libgdx-2d-rendering](libgdx-2d-rendering/SKILL.md) | SpriteBatch, textures, regions, blending |
| [libgdx-application-lifecycle](libgdx-application-lifecycle/SKILL.md) | ApplicationListener, dispose, pause and resume |
| [libgdx-camera-viewport](libgdx-camera-viewport/SKILL.md) | Cameras, viewports, unprojection |
| [libgdx-collections-json](libgdx-collections-json/SKILL.md) | Array, ObjectMap, pools, JSON, I18NBundle |
| [libgdx-particles](libgdx-particles/SKILL.md) | libGDX's own ParticleEffect (not used for the project's particles) |
| [libgdx-tiled](libgdx-tiled/SKILL.md) | Tiled maps in libGDX (the project uses its own loader) |
