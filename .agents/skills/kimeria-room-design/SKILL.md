---
name: kimeria-room-design
description: Designs or audits an Edgard in Kimeria room so it gives the player something new (D72) and keeps them busy (D105, about one beat every 3 s) - writes the room card before the map, checks novelty and density against the element registry, places each first meeting in a safe pocket and keeps RoomCardsTest green. Use when planning a level or room, adding or moving gameplay objects in a room, or reviewing whether a room is repetitive or overloaded.
---

# Kimeria room design

Work from the repository root. The substance is in the docs; this skill is the order of work.

## Read first

- [ROOMS.md, "Every room gives something new (D72)"](../../../docs/ROOMS.md#every-room-gives-something-new-d72): the rule,
  workflow and gate.
- [ROOM_CARDS.md](../../../docs/ROOM_CARDS.md): the card format, the element registry, and the cards of the rooms before
  and after the target room (novelty is counted in play order: `KimeriaGame.LEVEL_NAMES`, then each map's room order).
- [ROOMS.md, "Density and first meetings (D105)"](../../../docs/ROOMS.md#density-and-first-meetings-d105): what a beat is, the band and the pocket.
- [D72](../../../docs/DECISIONS.md#d72-every-room-gives-the-player-something-new), [D105](../../../docs/DECISIONS.md#d105-rooms-are-dense-one-beat-about-every-3-s-and-a-first-meeting-gets-a-safe-pocket-not-a-room-of-its-own-499), [D84](../../../docs/DECISIONS.md#d84-a-puzzle-never-makes-the-player-guess-or-look-stupid-playtest-418-419-2026-11-17) (no guesswork), D19 (20 s mastered, 60 s first
  learning), D26 (observe, plan, commit; no soft-lock); for a lesson room also D33-D40 and D48.
- [STYLE_SYSTEM.md](../../../docs/STYLE_SYSTEM.md): the order of work when a room also brings a trap, art, sound or text.
- If the room places a trap: [TRAPS.md](../../../docs/TRAPS.md) (D71, the trap's own card and visibility; a disguised
  or hidden trap needs a teaching room, which is the room whose card introduces it). If the room adds hint text:
  [TEXT_STYLE.md](../../../docs/TEXT_STYLE.md).

## Design the card before the map

1. **Role and purpose:** one role from the card format; one sentence on what the player learns or proves.
2. **Novelty:** list what the room introduces, develops or combines, by registry id. Check each claim against the
   cards before it: an introduction must be a first sighting; a develop must change the answer, not only the look
   (a reskin shares its element id and does not count); a combine needs both elements already met.
3. **Load:** outside lessons, at most two introductions, one unknown at a time, before any combination with it. More
   needs an `Overload reason` that cites an issue, and only as a tracked, temporary state.
4. **Density (D105):** list the required route's beats in rising map x as `id@x` (registry `Beat kind`: hazard,
   monster, a pulled lever with what it drives, a running platform, a skill obstacle posed by its geometry; never a
   pickup or plain geometry; two of one element within 96 px are one beat).
   Divide the planned zero-charge mastered time by their number: aim at 3 s, keep 1.5 to 4.0 s per beat, with at
   least 3 beats of 2 distinct elements (`lesson`, `boss` exempt; `rest`, `reward` one per level). A new trap or
   monster goes into a room of known beats, not into a room of its own.
5. **First meetings (D105):** for each introduced element that counts as a beat, a safe pocket in view of it, no other
   lethal danger zone within 96 px of it, among the first two beats (or a retry of at most 6 s), not combined on first
   sight; for a monster the danger zone is its whole reach, hearing included. Record `pocket at x N` and pin it with a
   `FirstMeetingPocketTest` method (hero there, never hit, the element seen acting). An enemy that waits idle until the
   hero is in reach shows nothing from a pocket: stop it behind a 32 px solid log (it cannot climb a block; the sword
   would reach across a 16 px one) so it charges and is stopped inside its range, as in `forest-1-mob`. A touch trap
   shows nothing from a pocket unless its trigger lies **outside** its lethal band (D109): then the trigger reaches at least 16 px,
   a body width, past the band and the pocket stands at least that clear of it (`FirstMeetingPocketTest.tripAndStand`). The deadfall's vine
   (D88) and the false honey fungus's zone (D63) use it today, and the thorn root and tuft meet it too; the exploding stump and root and the
   Bomb, whose trigger lies inside their blast, do not and are met among known beats. Such a trap has a trip-and-stand pocket on the trigger, the hero tripping it and standing short of the band,
   acting signal `SWING` (deadfall) or `BURST` (fungus, D109). A skill (a branch climb) never acts: its pocket shows the whole
   first obstacle in the camera view while no enemy, lethal hazard or trap of the room changes state, pinned by `skillPocket` (D109).
   Clearance counts what can kill (D107): a crumble support over a floor is not a lethal neighbour, only one over a pit
   is, and an object of the first-met skill's own geometry is not its own neighbour.
6. **Cost:** mastered-route target about 20 s, first learning about 60 s (hypotheses, D19), and the retry cost of the
   most expensive death. A long retry is a reason to split, not to pad or trim the timing targets.
7. **No guesswork (D84):** is every new rule or combination shown safely before it gates progress, and is every
   effect visible, or pointed at when it is off screen? Could a stuck player see what to try next?
8. **Optional:** a landmark the player will remember the room by, and a calm beat after a dangerous one (#352).

## Then build and record

- Build the map with `kimeria-level-authoring` and the recipes in ROOMS.md. A new Tiled type needs a registry row.
- Measure the mastered route with the existing route tests, pin it there within 0.1 s, and write it first in
  `Mastered route` and as `Beat time` (`seconds (TestClass)`); fill `Beats` and `First meeting` from the built room and
  add the room to `FirstMeetingPocketTest` for each first meeting. Do not claim a
  first-learning time without an observed player (#135).
- Run `./gradlew :core:test --tests '*RoomCardsTest'`, then the full `:core:test`. Fix the card or the room; never
  loosen the gate (AGENTS.md: gate exemptions must be argued).
- For an audit, fill the card's `Status` with the verdict (keep, change, merge, cut) and the reason; a change becomes a
  proposed issue, one per level.
