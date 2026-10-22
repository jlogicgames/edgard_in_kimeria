# Story and recurring characters

**Status — 2026-10-20:** The current implementation/review stage is complete for the
test version by author decision. Remaining release acceptance checks are deferred
until the planned gameplay mechanics have been added and are tracked only in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Deferral does not mean those checks passed.

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
Darkness has descended upon the kingdom. It was brought about by the evil witch
**L'Kardina (Л’Кардина)**. These are accepted story facts and the healer's role as narrator
is explicit; the opening must not read as an account by an unrelated, anonymous narrator.

**Original-game source supplied by the author, 2026-10-20:** Kimeria is ruled by
Rufardus the Wise. L'Kardina was banished from a neighboring kingdom; Kimeria's attempt
to expel her provokes the curse of eternal night. The sun stops shining. A wandering
warrior, Edgard, hears of the disaster and sets out to save the kingdom, meeting the old
healer on his way. These facts now form the EN/UK opening.

The opening introduces the world and Edgard's journey. **Do not include Chicken, Key,
coins, an elixir recipe, collection instructions or a boss-shopping checklist.** Explain
those mechanics in the healer encounter. The historical seven-component remedy is
reference material for a later healer story, not a new mechanic to implement now.
The opening retains the healer's narrative voice without turning into a tutorial.

The original also describes a spell tablet broken into four pieces, four regions, and a
seven-component remedy. Preserve these in [Original DOS references](ORIGINAL_DOS.md).
For the expanded campaign, the author has now selected Forest → Swamp → Steppe →
Fortress, one required tablet fragment per stage, and optional ingredients/coins
for the healer's boss elixirs (2026-10-20). This explicitly revises the DOS route
and the remedy's mandatory story role. It does not add those regions or pickups
to the current two-level build. See [full game design](GAME_DESIGN.md#campaign-structure)
and [D16](DECISIONS.md#d16-expanded-campaign-wind-ladders-and-carried-objects);
future implementation is tracked separately from this narrative decision.

### Agreed appearances

1. **At the beginning of the game.** The healer narrates the introduction: the kingdom,
   the darkness that has fallen over it, and the evil witch L'Kardina who caused it.
   The account includes Rufardus, the attempted expulsion, eternal night and Edgard's
   departure. Collection and elixir instructions belong to the healer's later encounter.
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
   visual identity and dialogue presentation from #84. The author approved the three-line meaning on 2026-10-19: victory opens the path,
   darkness remains, and the healer will guide Edgard onward. No destination is named.

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
Kimeria, later destinations and quests are not established. Neither are
specific later appearances beyond the opening and agreed forest encounters. Propose these explicitly
when extending the story; do not treat an implementation placeholder as accepted lore.

## Implementation status

The opening tells the original world history in EN/UK, narrated by the healer, without
recipe or collection instructions. About also leaves recipe details to the encounter.
The revision changes localized text only; opening timing/input and save rules are unchanged.

The pre-boss dialogue, checklist, paid elixir and collection return are implemented by
#84. The author accepted the dialogue wording in play for the test version
(2026-10-20). The shared PNG portraits from #90 follow the author's revised 2026-10-22 remake/remaster
direction: richer color and more natural drawing, preserving the original artist's style.
See [art direction](ART_DIRECTION.md) and [current portrait review](PORTRAITS.md). Release presentation/device review
remains in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#dialogue-and-localization).
The post-boss guide scene is implemented by #86 after the automatic exit and before
normal next-level loading. It reuses the pre-boss portrait/dialogue presentation and
introduces no new quest, destination or persistent checkpoint. See [HEALER.md](HEALER.md).


## MVP ending — accepted 2026-10-19

The playable MVP is finite: the forest's three rooms and boss form level one; the existing
single-room `forest-1` is level two. The forest guide scene bridges these levels. After
the second level, an ending screen in the opening's scrolling style plays the same music.
Its EN/UK text establishes that Edgard defeated every enemy, cleared the kingdom of
monsters and brought light back to the world. This outcome is now agreed; no new account
of L'Kardina's fate or the mechanism of dispelling darkness is added.

After the ending, the game returns to the main menu and removes the run save. A new run
starts from the opening and the forest. Later mechanics will expand these two levels;
additional stages and quests are outside the agreed MVP. See [MVP.md](MVP.md).
