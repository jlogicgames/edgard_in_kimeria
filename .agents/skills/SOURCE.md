# Provenance of the vendored libGDX skills

| | |
| --- | --- |
| Upstream | https://github.com/kyu-n/gdx-claude-skills |
| Pinned commit | `0887af2` (`0887af25df8f7e19ac044f37f2900d1c5237d370`) |
| License | MIT, copy in [LICENSE-gdx-claude-skills](LICENSE-gdx-claude-skills) |
| Copied on | 2026-11-11 |
| Status | Adapted for this project; no upstream tracking (nothing is synced, nothing is expected to be) |

Vendored: `SKILL.md` of `libgdx-particles`, `libgdx-collections-json`, `libgdx-2d-rendering`,
`libgdx-application-lifecycle`, `libgdx-camera-viewport` and `libgdx-tiled`. Nothing else of the upstream
repository is kept (no tests, scripts, summaries or other skills). The project-owned skill
`kimeria-libgdx-rules` is not from upstream.

Each copied skill was read in full on 2026-11-11 for instructions aimed at the agent beyond libGDX
reference, commands, URLs and hidden text. Found: none. The only URLs are `libgdx.com` and `github.com/libgdx`
documentation links. Frontmatter is `name` and `description` only.

## Local changes

Every skill gained a `> **Project note (local edit).**` block under its title (libGDX 1.14.2, desktop plus
TeaVM, Y-down world, what the project does and does not use). Beyond that:

- `libgdx-particles`: `free()` over the pool `max` discards without resetting (checked in 1.14.2 `Pool.free`);
  editor links marked as not run here; the 3D section marked as unused and not exercised on TeaVM.
- `libgdx-collections-json`: `DefaultPool` sentence extended (other constructor, no reflection so no TeaVM
  setup, `fill`, discard over `max`, no double-free check); the "GWT Compatibility" section replaced by a
  "Web build" section stating TeaVM, not GWT, with thread-based classes (`AsyncExecutor`, `Timer`) unverified
  there; "GWT-compatible" removed from common mistake 1; `AsyncExecutor` and `Timer` marked not verified on
  TeaVM and not used.
- `libgdx-2d-rendering`: note that regions are pre-flipped by `Assets` (comment at `region.flip`); the y-sort
  example marked Y-up only; the note records that `KimeriaGame` uses `glClearColor`/`glClear` on purpose.
- `libgdx-application-lifecycle`: only the project note (Android/iOS columns not applicable, D61; TeaVM
  lifecycle not covered; `togglePause()` naming).
- `libgdx-camera-viewport`: project note (Y-down, `PlayfieldViewport`); the `setToOrtho(yDown)` text states
  this project uses `yDown = true`.
- `libgdx-tiled`: frontmatter `name` changed from `libgdx-tiled-maps` to `libgdx-tiled` to match its directory;
  project note (the libGDX loader is not used, levels are read by `tiled.TiledMapData`); the 1.12.x
  `TmxMapLoader.Parameters` line removed because that class does not exist in 1.14.2, with the missing
  `projectFilePath` and `forceTextureFilters` rows added; the GWT gzip/zlib remark relabelled as GWT, not TeaVM.

## Checked against the libGDX 1.14.2 sources

Confirmed in `gdx-1.14.2-sources.jar`: `DefaultPool` (supplier constructors), `PoolManager`
(`addPool`, `obtain`, `free`), `@Deprecated` on `Pools` and `ReflectionPool`, `Pool.free` discard over `max`,
`ParticleEffectPool` and `PooledEffect.free`, `ParticleEffect.reset(boolean)`, `scaleEffect`, `flipY`,
`setEmittersCleanUpBlendFunction`, `ParticleEmitter` setters and `SpriteMode`, `ParticleEffectLoader`
parameters, `Array.removeValue/contains/indexOf` identity parameter, no `Array.remove(Object)`,
`ObjectMap` nested-iterator exception and `Collections.allocateIterators`, `ObjectIntMap.put/getAndIncrement`,
`JsonReader.parse`, `Json.setIgnoreUnknownFields/setUsePrototypes/setOutputType`, `I18NBundle.createBundle`,
`Timer.scheduleTask`, `ScreenUtils.clear` overloads, `Texture` default `Nearest`, `SpriteBatch` default 1000,
`OrthographicCamera.setToOrtho(yDown)`, `Viewport.update(..., centerCamera)`, `MapObjects/MapLayers.getByType`,
`BaseTiledMapLoader.Parameters` fields.

## Not verified

- Release tags in text such as "since 1.13.5", "since 1.14.0", "since 1.10.0": the sources do not record
  them (the deprecation and the classes themselves are verified for 1.14.2).
- LWJGL3, Android and iOS `pause()`/`resume()` behaviour (backend sources are not in the Gradle cache for
  this build), and everything about the TeaVM backend's lifecycle.
- That `AsyncExecutor`, `Timer`, `Json`, `I18NBundle` and the libGDX Tiled loader work under TeaVM (the project
  does not use them; the particle effect and pool were measured, see `docs/DEVELOPMENT.md`).
- Statements about Scene2D, Box2D, `AssetManager` internals, 3D particles and the external particle editors:
  read for injection and consistency, not tested.

## Security review

Done on 2026-11-11 on the copied files in this tree (not on a download), after a line-by-line read of all six
`SKILL.md` files (1,904 lines upstream-sized). Scripts ran with `python3 -I`; the upstream reference is a read-only
clone at `0887af2` whose `run_tests.sh` and other files were never executed.

| # | Check | Result | Evidence |
| --- | --- | --- | --- |
| 1 | Instructions to the agent beyond libGDX reference (override, ignore, never-tell, always-run, exfiltrate, role claims, urgency) | **Pass** | Read in full. The only directive wording is API advice for the programmer ("use `ScreenUtils.clear`", "do not use `Pools`", "Always reset to `Color.WHITE`", "MUST call `update()`"), which is libGDX usage. A keyword scan (override/ignore/disregard/never tell/always run/system prompt/assistant/claude/anthropic/urgent/secret/credential) hit only Java `@Override`, the verb "override" for methods, and this project's own local note. |
| 2 | Commands, scripts, install steps, curl/wget/ssh/eval/exec/base64/chmod, access outside the project, env/secrets/credentials/tokens/settings/hooks/MCP | **Pass** | Read in full. Scan for `curl wget ssh eval( exec( base64 chmod sudo rm -rf npm pip brew apt bash sh powershell ProcessBuilder Runtime.getRuntime ~/ /etc/ .ssh .env token password api key allowed-tools hooks mcp`: 0 hits in the six copies. The code blocks are libGDX Java snippets; `Gdx.files.internal(...)` paths are examples. |
| 3 | URLs and targets | **Pass** | 2 URLs in total: `https://github.com/libgdx/gdx-particle-editor` (libGDX organisation, editor repository; `libgdx-particles` line 253) and `https://libgdx.com/wiki/graphics/3d/3d-particle-effects` (libGDX documentation; line 305). Command: `grep -no "https\?://[^ )]*"`. Both are links to read, neither is fetched or run by any instruction. |
| 4 | Hidden content (zero-width/bidi/non-ASCII, HTML comments, odd whitespace, long lines, encoded blobs) | **Pass** | Script over every code point: 0 characters in categories Cf, Cc, Zs, Zl, Zp or control characters other than newline; 0 tabs, 0 CR, 0 trailing whitespace. The only non-ASCII characters are typographic and diagram characters (em/en dash, multiplication sign, arrows, box-drawing; 28 to 102 per file, named per file by the script). 0 HTML comments, 0 HTML tags, 0 strings of 60 or more base64-like characters. Longest lines: 357 to 462 characters upstream, all prose or code; added local lines reach 738 characters and are this project's notes. |
| 5 | Frontmatter limited to `name` and `description` | **Pass** | Script parsed the block of all six copies and the project skill: keys are exactly `['name', 'description']`, two lines each. No `allowed-tools`, `hooks`, `model` or other key. |
| 6 | Diff of each copy against upstream `0887af2` shows only the listed local edits | **Pass** | `diff` against the clone at `0887af25df8f7e19ac044f37f2900d1c5237d370`: particles +6/-2 lines, collections-json +10/-6, 2d-rendering +10/-2, application-lifecycle +2/-0, camera-viewport +3/-1, tiled +8/-6. Each hunk was read and matches an entry in "Local changes" above. `LICENSE-gdx-claude-skills` is byte-identical to the upstream `LICENSE` (`cmp`). |
| 7 | File inventory | **Pass** | Vendored additions are exactly: six `libgdx-*/SKILL.md`, `LICENSE-gdx-claude-skills`, `SOURCE.md` (this file) and `kimeria-libgdx-rules/SKILL.md`. `find .agents/skills/libgdx-*` lists one file per folder; 0 files with the executable bit and 0 symlinks under `.agents/skills`. (The directory also holds the project's earlier `kimeria-*` skills and their `agents/openai.yaml`, which this change did not touch.) |

Not checked: the upstream repository outside the six copied files (its other 22 skills, `run_tests.sh`, `SUMMARY.md`, `test_prompts.json`,
README, git history, other commits) was not reviewed for this decision; the correctness of the libGDX claims beyond the list under "Checked
against the libGDX 1.14.2 sources"; that the two external links still point where they pointed on the review date; semantic traps a reader could
miss in 1,900 lines of prose (the review was one human-style read plus the scripts above, not an independent second reviewer). A failed check
would have meant removing or fixing the text and listing it under "Local changes"; none failed.
