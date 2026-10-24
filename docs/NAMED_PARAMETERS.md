# Named gameplay and artwork parameters

Accepted after PR #154 review (2026-10-23). New or revised gameplay, rendering and
art-export code must give tuning values and geometry a meaningful owner and name.
The purpose is to keep related values consistent and make changes deliberate,
not to replace every numeric character with a constant.

## Ownership

- **Map grid:** `TiledMapData.tileWidth/tileHeight` come from TMX/TSX. They describe
  map cells, not character frames, item artwork, collision bodies or HUD slots.
- **Runtime artwork:** `SpriteSheets` describes native frame paths and dimensions.
  Torch, carryables, fireballs and levers use the same definitions when loading
  and drawing. The player uses its frame format plus a named `CLIPS` data table.
- **Offline artwork:** `tools/sprite_layouts.py` owns export dimensions, selected
  head bounds, palette/alpha settings and the image-validator contracts. Export
  tools and PNG validation share it. The runtime and offline formats must continue
  to describe the same shipped pixels; validate exports after either is revised.
- **Collision and motion:** `Carryable.BODY_SIZE` and `Fireball.BODY_SIZE` belong to
  those entities. Placement/spawning refer to these definitions. `ProjectileStep`
  owns the shared frame/substep limits for thrown objects and fireballs. Gravity,
  launch speed, lifespan and attachment offsets remain with their relevant entity.
- **UI layout:** HUD margins, label constraints, fonts, gaps and shadow offsets
  belong to HUD. The FPS renderer uses HUD's shared FPS margins. Selection-frame
  colors and dimensions belong to `InteractionHighlight`, independently of tiles.

Do not introduce an unrelated global `TILE_SIZE` or a bag of `EIGHT`/`SIXTEEN`
constants. Equal values are not necessarily the same parameter: a frame width,
collision width and flame-emission region may each need independent adjustment.
Share a value only when the concepts must change together.

## Expressions and authored data

Derive half-widths, double-sided padding, mirrored offsets and sheet extents from
their definitions. For example, placement on the left uses body size plus the
placement gap; it does not repeat their sum as a literal. Preserve grouping and
floating-point precision when rewriting positions.

An immutable, named palette, frame layout, animation clip table or measured palm
coordinate table is already explicit data. Its entries need not each become an
independent scalar constant. Standard zero/one states and indices, or multiplication/
division by two for centering and symmetric bounds, need no `ZERO`/`TWO` wrappers.
Use the existing interpolation function for smoothstep; name a derived slope bound
when it participates in camera-speed calculations.

A parameter name must communicate its role and, for timing/physics, its units.
Names such as `MAX_SUBSTEP_SECONDS` and `THROW_UPWARD_SPEED` convey a contract;
renaming an anonymous value to `VALUE_280` does not.

## Automated check and its exact scope

`tools/check_named_parameters.py` checks added/modified Java production lines in
core, desktop (including dev sources), web and Java developer tools. It also checks Python artwork
exporters identified by their PIL imports. New/untracked source files are included.
Named Java final constants and Python uppercase assignments are explicit data.
Unchanged legacy lines, independent expected values in tests, and technical parser/
checker byte formats are different categories from new gameplay/export parameters.
There is no per-object filename allowlist or stored list of tolerated violations.

```sh
python3 tools/check_named_parameters.py
python3 tools/check_named_parameters.py --base origin/main
./gradlew validateNamedParameters -PnamedParameterBase=origin/main
python3 tools/test_named_parameters.py
```

The default compares with HEAD, covering staged, unstaged and untracked work.
`--base origin/main` compares with its merge base with HEAD, also covering committed
PR changes. Use this whole-PR mode for review; a clean tree checked only against HEAD
is not a historical audit of its committed code. An unborn repository treats all
source as new. Missing Git metadata or an invalid requested base fails loudly.

Core `check`, desktop JAR/native packaging and the TeaVM release build run the
changed-code gate. `-PnamedParameterBase=origin/main` selects the whole-PR mode for
these build gates too. This is a lexical/AST check, not a proof of design quality:
it cannot decide whether a named constant has the right owner or whether every
zero/one denotes a tuning value. Review ownership, formulas and cross-language
asset contracts alongside it; do not claim the checker infers those semantics.

The project `.codex/hooks.json` runs both this check and the authored-sprite check
on PostToolUse and Stop. Review/trust the configuration through Codex `/hooks` as
explained in [development](DEVELOPMENT.md#sprite-artwork-guard). Hook tests exercise
real failing files and nonzero exit status, then demonstrate recovery after fixing
the source; they do not merely check the hook's wording.

## Validation when refactoring

Run the focused production regressions and the guard's negative tests. For shared
geometry/physics changes, run the core suite and desktop/web builds. Re-export
selected art and compare native RGBA pixels when export code changes; naming
parameters alone must not redraw assets or move an attachment. Keep expected
values in tests independent so a wrong shared constant cannot make both production
code and its assertion change together unnoticed.
