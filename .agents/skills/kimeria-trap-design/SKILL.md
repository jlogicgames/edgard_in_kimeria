---
name: kimeria-trap-design
description: Designs, builds and places an Edgard in Kimeria trap, from idea to a tested placement, under the visibility rule D71. Use when proposing a new trap, changing a trap's look, trigger, telegraph or placement, or auditing how readable the traps are.
---

# Kimeria trap design

Project-owned, a thin procedure: the rules live in [docs/TRAPS.md](../../../docs/TRAPS.md) and
[docs/DECISIONS.md](../../../docs/DECISIONS.md). Work from the repository root; links resolve relative to this file.

## 1. Read before proposing

- [TRAPS.md](../../../docs/TRAPS.md): the D71 rule (VISIBLE by default), the card template, the catalogue, the cards
  of the existing traps and the proposed ones.
- D7 (deterministic, shared warnings from `TrapWarnings`), D26 (no ammunition on a required route), D42 (forest trap
  set and the **rejected** ideas), the family's decision (D44 proximity, D45 position, D46 rhythm, D47 touch, D63
  look-alikes) and D71.
- [ORIGINAL_TRAPS.md](../../../docs/ORIGINAL_TRAPS.md) for what the 1994 game had: adapt ideas, never copy another game.

## 2. Write the card first

Fill the template of TRAPS.md for the new trap. It is **VISIBLE** unless the author approves otherwise; a DISGUISED or
HIDDEN trap needs an always-drawn at-rest cue and a teaching room. Name every counter that works without ammunition.
Stop and get the author's approval of the card and of its D-number before any code.

## 3. Art

[kimeria-art-direction](../kimeria-art-direction/SKILL.md): the at-rest frame in the "active" contrast tier (#344),
a silhouette no scenery shares; `tools/check_sprite_art.py`. Effects: [kimeria-particles](../kimeria-particles/SKILL.md).

## 4. Code

[kimeria-libgdx-rules](../kimeria-libgdx-rules/SKILL.md): Y grows downward, no allocation per frame. A class in
`entity/objects/`; the warning only through `game.trapWarnings().duration(...)`, sampled once at the trigger; the
hero is seen only while `Player.canLeaveRoom()`; a placement validator run for every room at load in `Level`,
mirrored in `tools/check_tiled_links.py`; reset by the room rebuild.

## 5. Register

Add the `case` to the spawn switch of `world/Level.java`, the row to the catalogue in TRAPS.md and the card.
`TrapCatalogTest` fails until all three agree.

## 6. Place

[kimeria-level-authoring](../kimeria-level-authoring/SKILL.md): the first meeting with the type in a teaching room;
measure the route (`RoomReplayTest`) and keep the D26 contract of [ROOMS.md](../../../docs/ROOMS.md).

## 7. Test and record

Tests: the telegraph equals the shared warning (both assist values), lethal only in the lethal part and phase, every
counter passes at every phase or offset, reset on retry and Restart Level, pause freezes it. Run `./gradlew :core:test`
and `python3 -m unittest discover -s tools -p 'test_*.py'`. Record the decision in DECISIONS.md and the card's final
values in TRAPS.md; any "not yet" gets an open issue linked from the doc (AGENTS.md).
