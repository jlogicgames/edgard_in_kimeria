---
name: kimeria-parallax
description: Adds, changes or reviews an Edgard in Kimeria parallax layer or foreground piece - the scenery tile layers behind the play layer, the per-room foreground groups, the giant blurred leaves, their depth colours, the installer tools/parallax_game.py and the gate tools/parallax_mock.py --check --game. Use for any change to forest scenery behind or in front of the play, to a room's foreground, or when a new object in a room ends up under a foreground piece.
---

# Kimeria parallax

Project-owned, a thin order of work. The rules and every measured number live in
[ART_DIRECTION.md](../../../docs/ART_DIRECTION.md), "Forest look and parallax layers" (the card),
[DECISIONS.md](../../../docs/DECISIONS.md) (D108, D111 to D114, D120, D143) and
[DEVELOPMENT.md](../../../docs/DEVELOPMENT.md#scenery-layers-and-the-foreground-parallax-236) (the parser and the renderer).
Draw art with [kimeria-art-direction](../kimeria-art-direction/SKILL.md); move gameplay objects with
[kimeria-level-authoring](../kimeria-level-authoring/SKILL.md). Work from the repository root.

## The layers

| Draw order | Layer | Factor | In the map |
| --- | --- | --- | --- |
| 1 | sky, stars, moon, clouds (clouds also drift) | 0 | scenery tile layers |
| 2 | far, grove, mid | 0.15, 0.25, 0.4 | scenery tile layers |
| 3 | `play-above`, `play-below` | 1 | scenery tile layers, no collision |
| 4 | play layer, objects, hero | 1 | the one tile layer that is not scenery, and the object groups |
| 5 | `foreground-<room>`: sharp pieces | 1.6 | object group per room |
| 6 | `foreground-near-<room>`: giant blurred leaves | 2.2 | object group per room, after 5 |
| 7 | HUD | screen space | not in the map |

A tile layer is scenery when it has `parallaxx` or `parallaxy` or the property `scenery=true`. Each layer's vertical factor
equals its horizontal one. World Y grows downward.

## Foreground rules

- **Rule B:** a foreground piece never covers a trap or its at-rest cue, an enemy with its patrol, leap or silk range, a
  pickup, a D7 warning or the used part of a mechanism, at any camera x of the room, with 16 px around each. It may cover the
  walk edge and the hero elsewhere; at RoomStart and RoomExit the hero stays visible above the waist.
- **Cover time:** the hero is never (near) fully hidden where he stands, nor for more than 0.5 s of walking, on the floor or at
  a jump's apex. Both groups count together.
- **Colours (D120):** the foreground and the decor tier take a neutral dark palette with real hues (leaf green, bark brown,
  moss), never black or grey ghosts; mid, grove and far turn bluer, lighter and greyer with depth; the sky, the moon and the
  gameplay objects keep theirs.
- **Giant leaves (D143):** `edge-giant-*` are the only blurred art (`blur=` on the grid's piece line, baked at export, nothing
  at run time). They lie only in `foreground-near-<room>`, every forest room has at least one, they stay clear of open pits,
  and they hide at most 35 % of a sharp piece over the room's cameras (50 % at the first or last camera).
- **HUD:** drawn after the world, over every layer. A piece under it is fine; move nothing for it.
- **Bleed:** no cut layer edge at any camera top 0 to 8 (D111): keep each piece's `-top` or `-foot` partner with it.

## Order of work

1. **Art:** edit the grid (`art/parallax/forest/*.grid`, `art/parallax/shared/*.grid`; the near pieces through
   `art/parallax/forest/near/` and its copy-only `stamp.py`, D113), then export: `python3 tools/parallax_sprites.py`.
   A new or resized sheet updates its entry in `tools/sprite_layouts.py` (`PARALLAX_LAYER_SHEETS`, `PARALLAX_SOFT_SHEETS`).
2. **Placement:** the room lists in `tools/parallax_game.py` (`GIANT`, `MORE_BOTTOM` and the per-forest lists). Never edit the
   installed layers or groups in a `.tmx` by hand: `tools/test_parallax_game.py` fails a map that differs from the installer.
3. **Install:** `python3 tools/parallax_game.py`.
4. **Check:** `python3 tools/parallax_mock.py --check --game` (`validateParallax`: rule B, cover time, the giant-leaf rules,
   bleed, in every room with a foreground). A failing room is fixed by moving the piece or the object, never by widening a limit.
5. **Colours changed:** `python3 tools/check_contrast.py` (`validateContrast`). It composites the scenery and play layers
   behind placed sprites, not the foreground; its coverage is listed in the card, section 4.
6. **Tests:** `./gradlew :core:check validateSpriteArt`, `python3 -m unittest discover -s tools -p 'test_*.py'`; draw code stays
   allocation free (`AllocationBudgetTest.forest1ParallaxLayersAndForeground`). A change to the loader or
   `world.ParallaxRenderer` also needs `ParallaxRendererTest`, `TiledMapParallaxTest` and `./gradlew buildWeb`.
7. **Record:** measured values on the card; look at the room in the running game before calling it done.
