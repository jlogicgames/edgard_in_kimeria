---
name: kimeria-room-design
description: Designs or audits an Edgard in Kimeria room so it gives the player something new (D72) - writes the room card before the map, checks novelty against the element registry and keeps RoomCardsTest green. Use when planning a level or room, adding or moving gameplay objects in a room, or reviewing whether a room is repetitive or overloaded.
---

# Kimeria room design

Work from the repository root. The substance is in the docs; this skill is the order of work.

## Read first

- [ROOMS.md, "Every room gives something new (D72)"](../../../docs/ROOMS.md#every-room-gives-something-new-d72): the rule,
  workflow and gate.
- [ROOM_CARDS.md](../../../docs/ROOM_CARDS.md): the card format, the element registry, and the cards of the rooms before
  and after the target room (novelty is counted in play order: `KimeriaGame.LEVEL_NAMES`, then each map's room order).
- [D72](../../../docs/DECISIONS.md#d72-every-room-gives-the-player-something-new), D19 (20 s mastered, 60 s first
  learning), D26 (observe, plan, commit; no soft-lock); for a lesson room also D33-D40 and D48.
- If the room places a trap: [TRAPS.md](../../../docs/TRAPS.md) (D71, the trap's own card and visibility; a disguised
  or hidden trap needs a teaching room, which is the room whose card introduces it). If the room adds hint text:
  [TEXT_STYLE.md](../../../docs/TEXT_STYLE.md).

## Design the card before the map

1. **Role and purpose:** one role from the card format; one sentence on what the player learns or proves.
2. **Novelty:** list what the room introduces, develops or combines, by registry id. Check each claim against the
   cards before it: an introduction must be a first sighting; a develop must change the answer, not only the look
   (a reskin shares its element id and does not count); a combine needs both elements already met.
3. **Load:** outside lessons, at most two introductions, each first met where it is the only unknown thing on screen,
   before any combination with it. More needs an `Overload reason` that cites an issue, and only as a tracked,
   temporary state.
4. **Cost:** mastered-route target about 20 s, first learning about 60 s (hypotheses, D19), and the retry cost of the
   most expensive death. A long retry is a reason to split, not to pad or trim the timing targets.
5. **Optional:** a landmark the player will remember the room by, and a calm beat after a dangerous one (#352).

## Then build and record

- Build the map with `kimeria-level-authoring` and the recipes in ROOMS.md. A new Tiled type needs a registry row.
- Measure the mastered route with the existing route tests and write the measured time into the card. Do not claim a
  first-learning time without an observed player (#135).
- Run `./gradlew :core:test --tests '*RoomCardsTest'`, then the full `:core:test`. Fix the card or the room; never
  loosen the gate (AGENTS.md: gate exemptions must be argued).
- For an audit, fill the card's `Status` with the verdict (keep, change, merge, cut) and the reason; a change becomes a
  proposed issue, one per level.
