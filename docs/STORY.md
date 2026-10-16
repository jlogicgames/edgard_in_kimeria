# Story and recurring characters

This document records agreed narrative roles for future story, dialogue and level-design
work. Accepted direction is not a claim that the corresponding scenes are implemented.
See [DECISIONS.md, D14](DECISIONS.md#d14-the-healer-is-edgards-recurring-guide).

## The healer: Edgard's recurring guide

**Accepted by the author, 2026-10-16.** The healer (знахарь) is the narrator at the very
beginning of the game, the character met before the forest boss, and the same person who
returns after the victory. He is Edgard's guide through the journey, not only the provider
of an optional combat elixir.

His importance to the game is continuity: a familiar character connects the hero's
encounters and the stages of the journey. His dialogue gives a story context to what
Edgard has achieved and what comes next. Use this role as the basis for future appearances
and dialogue decisions; the elixir bargain is one encounter with this character, not his
entire purpose.

He also provides practical guidance on how to progress through the game: what to do next,
how to overcome obstacles and how game mechanics work. Use his voice for story-linked
explanations and progression advice, keeping them consistent with actual gameplay. The
specific hints, their timing and final wording remain implementation and writing work.

### Opening account and established world facts

At the very start, **the healer tells the story** and introduces the world and its kingdom.
Darkness has descended upon the kingdom. It was brought about by the evil sorceress
**L'Kardina (Л’Кардина)**. These are accepted story facts and the healer's role as narrator
is explicit; the opening must not read as an account by an unrelated, anonymous narrator.

The precise way L'Kardina summoned the darkness, her motives and history, and the means
of overcoming her have not been decided. Do not invent these as settled facts when
expanding the introduction or the healer's advice.

### Agreed appearances

1. **At the beginning of the game.** The healer narrates the introduction: the kingdom,
   the darkness that has fallen over it, and the evil sorceress L'Kardina who caused it.
   This establishes the same guide who will explain the journey and its challenges.
2. **Before the forest boss — [#84](https://github.com/jlogicgames/edgard_in_kimeria/issues/84).**
   The healer meets Edgard in a dedicated NES-style dialogue scene and offers the optional
   elixir bargain. Chicken, Key and all coins authored in the current forest level are
   ingredients/payment; the elixir is immediately drunk and gives 10x sword damage against
   this boss. Edgard may proceed without it. Collection-return and payment details remain
   specified in #84 and [D6](DECISIONS.md#d6-collectables).
3. **After the forest boss — [#86](https://github.com/jlogicgames/edgard_in_kimeria/issues/86).**
   After boss death and the hero's automatic exit, the same healer returns on a separate
   dialogue screen before the next playable level. The conversation acknowledges the
   victory and establishes his continuing role as guide. Reuse the character's portrait,
   visual identity and dialogue presentation from #84. Final dialogue wording is still to
   be agreed.

### Consequences for future decisions

- Treat subsequent healer scenes as meetings with the same recurring character. Keep his
  portrayal consistent with earlier appearances and the role of guide.
- His role continues whether or not the player bought the optional elixir. Collectibles
  and payment are not prerequisites for continuing the story or defeating the boss.
- Place the agreed post-boss scene at a successful progression boundary. Do not repeat it
  on boss-room death/retry or add dialogue delays to the existing fast retry loop.
- New scenes must respect the existing level-only save contract. Being a guide does not
  imply a new checkpoint, resurrection, healing or persistence mechanic.

### Not yet decided

The healer's personal name, origin, motives, relationship to the boss, exact knowledge of
Kimeria, later destinations and quests, and final lines are not established. Neither are
specific later appearances beyond the opening and agreed forest encounters. Propose these explicitly
when extending the story; do not treat an implementation placeholder as accepted lore.

## Implementation status

The pre-boss encounter and dialogue system are tracked in #84; the post-boss guide scene
is tracked separately in #86. The current boss implementation proceeds directly from the
victory exit to the next level. The existing opening story is placeholder text: it does
not yet identify the healer as narrator or L'Kardina as the cause of the darkness. Updating
the introduction must follow the established account above; this documentation does not
claim that the new opening text or guidance scenes have already shipped.
