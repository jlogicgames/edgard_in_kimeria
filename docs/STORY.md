# Story and recurring characters

**Status — 2026-10-20:** The current implementation/review stage is complete for the
test version by author decision. Remaining release acceptance checks are deferred
until the planned gameplay mechanics have been added and are tracked only in
[PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md). Deferral does not mean those checks passed.

This document records agreed narrative roles for future story, dialogue and level-design
work. Accepted direction is not a claim that the corresponding scenes are implemented.
See [DECISIONS.md, D14](DECISIONS.md#d14-the-healer-is-edgards-recurring-guide).
How story text is written (voices, glossary of names, limits) is in [TEXT_STYLE.md](TEXT_STYLE.md) (D74);
the story's beats against Propp's functions, and its open gaps, are in [TEXT_AUDIT.md](TEXT_AUDIT.md#2-the-story-against-propps-functions-and-roles).

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
healer on his way. These facts now form the opening in all five supported languages.

The opening introduces the world and Edgard's journey. **Do not include Bat, Mushroom,
coins, an elixir recipe, collection instructions or a boss-shopping checklist.** Explain
those mechanics in the healer encounter. The historical seven-component remedy is
reference material for a later healer story, not a new mechanic to implement now.
The opening retains the healer's narrative voice without turning into a tutorial.

**Presentation (#336, playtest F2, [D77](DECISIONS.md#d77-story-screens-are-static-pages-playtest-f2-336-2026-11-16)):** the opening is shown as static pages, not a scroll. The paragraphs appear one
after another from the top and stay; the player turns the page with any fresh key, button or tap, and Escape (or the
pad's Back/Start) leaves it. With the Instant text option (D11) a page appears whole. The text is cut at paragraph
breaks only, so wording is never changed to fit. **Since [D133](DECISIONS.md#d133-the-opening-is-a-tale-on-an-open-book-535-536-amends-d77-2026-11-24) ([#535](https://github.com/jlogicgames/edgard_in_kimeria/issues/535), [#536](https://github.com/jlogicgames/edgard_in_kimeria/issues/536))** the opening is a tale on an open book: six leaves (three spreads) of two paragraphs, one key each (`INTRO_LEAF_1` to `INTRO_LEAF_6`), in the healer's tale formulae, naming the fairy as the third gift after the thread and the spark. The ending stays on black with the arms.

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
   elixir bargain. Bat, Mushroom and all coins authored in the current forest level are
    ingredients/payment; the elixir is immediately drunk and gives 10x physical sword damage
    against this boss. Magical fireballs always remove two energy; the elixir does not
    strengthen them. Edgard may proceed without it. Collection-return and payment details remain
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
  imply a new checkpoint, resurrection, healing or persistence mechanic. The silver-thread
  explanation of Edgard's returns (below) is narrative only and does not change this.

### Kingdom heraldry — accepted 2026-11-16 ([#350](https://github.com/jlogicgames/edgard_in_kimeria/issues/350), playtest F12)

The arms of Kimeria, the arms of King Rufardus the Wise: **a gold sun in splendour, eight rays, on a night-blue
shield with a gold border, under a royal crown.** Gold on night blue is the kingdom's story in one picture: the sun
that L'Kardina's curse put out, held against the eternal night.

- **The sun alone** is the kingdom's mark on small things. Every coin of Kimeria carries it (the world coin and the
  HUD coin), so the coins the healer asks for are the kingdom's own money.
- **The full arms** (crown, shield and sun) belong to the king and his places: banners and shields in the fortress
  ([#116](https://github.com/jlogicgames/edgard_in_kimeria/issues/116)) and Kimeria on the world map
  ([#94](https://github.com/jlogicgames/edgard_in_kimeria/issues/94)).
- **The lit arms close the story** ([#416](https://github.com/jlogicgames/edgard_in_kimeria/issues/416),
  [D95](DECISIONS.md#d95-the-sun-on-the-kimeria-arms-lights-up-on-the-ending-page-416-2026-11-17)): the ending page shows
  the full arms with the sun put out; the sun lights again first, and only then do the words tell how the light came back
  ([#573](https://github.com/jlogicgames/edgard_in_kimeria/issues/573)). The opening shows no arms.
- **The menus** carry the full arms as the mark of the chosen line: the player meets the kingdom's sign on the first
  screen, beside the button they are about to press ([#415](https://github.com/jlogicgames/edgard_in_kimeria/issues/415)).

### Not yet decided

The healer's personal name, origin, motives, relationship to the boss, exact knowledge of
Kimeria, later destinations and quests are not established. Neither are
specific later appearances beyond the opening and agreed forest encounters. Propose these explicitly
when extending the story; do not treat an implementation placeholder as accepted lore.

## Implementation status

The opening tells the original world history in EN/UK/PL/DE/BE, narrated by the healer, without
recipe or collection instructions. About also leaves recipe details to the encounter.
The revision changes localized text only; opening timing/input and save rules are unchanged.

The original EN/UK wording was preserved when #85 added Polish, German and Belarusian
in PR #150. Translations now live in [UTF-8 catalogs](LOCALIZATION.md), with separate
[technical validation and remaining wording/device review](LOCALIZATION_VALIDATION.md).
This language expansion does not imply author approval of every new translation.

The pre-boss dialogue, checklist, paid elixir and collection return are implemented by
#84. The author accepted the dialogue wording in play for the test version
(2026-10-20). The shared PNG portraits from #90 follow the author's revised 2026-10-22 remake/remaster
direction: richer color and more natural drawing, preserving the original artist's style.
See [art direction](ART_DIRECTION.md) and [current portrait review](PORTRAITS.md). Release presentation/device review
remains in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md#dialogue-and-localization).
The post-boss guide scene is implemented by #86 after the automatic exit and before
normal next-level loading. It reuses the pre-boss portrait/dialogue presentation and
introduces no new quest, destination or persistent checkpoint. See [HEALER.md](HEALER.md).


## The fairy helper — accepted 2026-11-16 ([#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338), playtest F4, F14)

**Accepted by the author, 2026-11-16 ([D82](DECISIONS.md#d82-the-fairy-helper-shows-the-interaction-target-instead-of-the-corner-brackets-playtest-f4-f14-338-2026-11-16)).** The healer's third gift, after the silver
thread and the spark of his fire, is a fairy of the wood: one of the little lights the night could not put out, a tiny
maid with dragonfly wings whose skirt glows like a firefly's lantern. In Propp's terms she is the magical helper the
donor gives. She has no name (names stay undecided, above).

- **Shown, not only told:** she arrives at Edgard's shoulder with a twinkle when the forest-0 start tale ends, and
  stays with him in every level. When he reaches for a thing his hand can wake or take, her light falls on it at
  once, and she flies to it and points; when what a lever wakes lies beyond the edge of his sight, she flies to that edge and twinkles toward it ([D87](DECISIONS.md#d87-the-fairy-points-at-a-linked-mechanism-that-is-off-screen-and-the-pulled-levers-mechanisms-stay-marked-419-2026-11-17)). She fights nothing and carries nothing.
- **Approved texts (EN source, 2026-11-16, variant V1 of the Phase A proposal):**
  - New healer line `HEALER_TALE_FAIRY`, after `HEALER_TALE_FIREGIFT` (start tale, 9 lines): "And my last gift,
    Edgard: a fairy, one of the little lights the night could not put out. When you reach for a thing, she flies to it
    and lights it."
  - `HEALER_TALE_FAIRIES`: "a sleeping fairy wakes the torch with a touch" became "a sleeping torch wakes at a touch"
    (the fairy is not given yet at that line).
  - `HEALER_TALE_LIFT`: "A far-off fairy can be woken by a stone sent like a wish" became "What your hand cannot
    reach, a stone sent like a wish can wake."
  - `HEALER_TALE_BLESSING`: "May the fairies light your road" became "May your fairy light your road."
  - The other images in those lines (ladder, wall, golden boat, "Do not be afraid to ask") were rewritten by
    [#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370) ("The tutorial tales in tale voice", below).


## The tutorial tales in tale voice — accepted 2026-11-17 ([#370](https://github.com/jlogicgames/edgard_in_kimeria/issues/370))

**Accepted by the author, 2026-11-17 (variant V1 of the Phase A proposal).** The forest-0 tales follow the tale voice
of [TEXT_STYLE.md](TEXT_STYLE.md) (D74) and say only what the rooms do (text audit G5, G6, G7, G16). Every mechanism
in forest-0 is a lever, and "lever" is a control word in tale voice, so the tale calls them old handles. The healer
opens with "Listen, Edgard" and closes the end tale with "Go on"; the witch's three kinds of gifts (what rises, what
falls, what bursts) stand before the healer's three gifts (thread, spark, fairy). Approved texts (EN source):

- `HEALER_TALE_ROAD`: "Listen, Edgard. The road ahead is short, yet it is a tale all the same, so hear it before you walk it."
- `HEALER_TALE_FAIRIES`: "This wood is full of old handles. One wakes a sleeping torch, one lets down a ladder, and one moves a
  stubborn wall out of your way." (lever, ladder and wall rooms)
- `HEALER_TALE_LIFT`: "Another sets a boat of the air sailing up to the high ledge. What your hand cannot reach, a stone sent
  like a wish can wake." (platform and stone rooms; "Do not be afraid to ask" is gone, nobody can be asked)
- `HEALER_TALE_WITCH`: "Beware the wicked witch's gifts: thorns that rise, branches, stones and bridges that fall, and stumps
  that burst. Answer her riddles with quick feet." (all five forest-0 traps; the berry written for the bomb is now the stump)
- `EDGARD_TALE_READY`: "Forgive my haste, healer. Am I ready for the road, then?" (Edgard takes back his haste, so the
  donor's test is passed in words too)
- `HEALER_TALE_BLESSING`: "Take my blessing: walk lightly, look keenly, and trust no path that seems too kind, for the witch
  smiles sweetest. May your fairy light your road. Go on."
- Unchanged: `EDGARD_TALE_NO_TIME`, `EDGARD_TALE_UNDERSTOOD`, `HEALER_TALE_PRAISE`, `HEALER_TALE_HEROES`, and the lines
  settled earlier (`HEALER_TALE_THREAD`, `HEALER_TALE_FIREGIFT`, `HEALER_TALE_FAIRY`).


## MVP ending — accepted 2026-10-19

The playable MVP is finite: the existing three-room `forest-1` (trap course, route room and guard room) is level one; the forest's three rooms and boss
(`forest-2`) form the last level. The forest guide scene follows the boss. After
it, an ending screen in the opening's page style plays the same music.
Its localized text establishes that Edgard defeated every enemy, cleared the kingdom of
monsters and brought light back to the world. This outcome is now agreed; no new account
of L'Kardina's fate or the mechanism of dispelling darkness is added.
Above the text the Kimeria arms show the same outcome without words: the sun on the shield is dark while the page
appears and lights up with the paragraph that brings the light back (#416, D95, see "Kingdom heraldry"). With the
Instant text option the arms are lit at once.

**Told by the healer (2026-11-17, [#372](https://github.com/jlogicgames/edgard_in_kimeria/issues/372)).** The healer who
opens the game also closes it, so the frame closes: "Hear how my tale ends." He tells that from the forest Edgard walked a
long road with the healer as his guide (which keeps the guide scene's "we will meet again"), defeated every enemy and left
no monster in Kimeria; then the sun shone again and the sleeping kingdom woke to the light (the sleep of the opening is
the curse's image, ended by the light). The accepted outcome above is unchanged. The light has its own paragraph in every
language, before the closing line, for the arms on the ending page (#416).

After the ending, the game returns to the main menu and removes the run save. A new run
starts from the opening and the `forest-0` tutorial. Later mechanics will expand these levels;
additional stages and quests are outside the agreed MVP. See [MVP.md](MVP.md).


## Why Edgard returns after every fall — accepted 2026-11-10 ([#228](https://github.com/jlogicgames/edgard_in_kimeria/issues/228))

**Accepted by the author, 2026-11-10.** Edgard can rise again after every fall because the
healer helps him. In the tale it is a silver thread: the healer tied it about Edgard's wrist
and holds the other end. When the night swallows Edgard, the healer draws the thread and Edgard
rises again at the edge of the path. The returning is certain ("you will rise"), and the healer
may name the fall ("when the night swallows you"). This is narrative only: it adds no
checkpoint, resurrection, healing or persistence mechanic and changes no rule in
[HEALER.md](HEALER.md#state-policy). The healer's name, origin and powers beyond this image stay
undecided.

Approved texts (EN source; the five catalogs hold the translations):

- **Forest-0 start tale, one new healer line** after the witch line, key `HEALER_TALE_THREAD`:
  "Edgard, I tied a silver thread about your wrist, and I hold the other end. When the
  night swallows you, I draw the thread, and you will rise again at the path's edge."
- **Opening narration**, one sentence in the paragraph where he meets the healer (`INTRO_STORY`, since [D133](DECISIONS.md#d133-the-opening-is-a-tale-on-an-open-book-535-536-amends-d77-2026-11-24) `INTRO_LEAF_5`):
  "I gave him my counsel. I tied a silver thread about his wrist and kept the other end in my
  hand, and he ventured onward."
- **#340 (2026-11-16), the healer's second gift: the fireball.** Author-approved (variant A).
  Start tale, 8 lines, new healer line after the thread, key `HEALER_TALE_FIREGIFT`: "And one
  more gift, Edgard. Here is a spark of my own fire, and it will leap from your hand at what you
  fear. Sparks found on the road are this same fire, but it is not endless, so spend it as you
  would your last bread." Opening narration, the same paragraph, one added sentence after the
  thread: "I tied a silver thread about his wrist and kept the other end in my hand. I gave him a
  spark of my own fire, and he ventured onward." In Propp's terms the healer is the donor and the
  spark is the magical agent; refills on the road are the same fire, and the text says it is
  not endless (the scarcity rule of GAME_DESIGN.md). The line says "spark" and "fire", never
  "fireball", so it names no mechanic.

Not used by the author's decision: the farewell (no line there) and any answer from Edgard.
**Revised 2026-11-15 after the first external playtest ([PLAYTEST_1.md](PLAYTEST_1.md), D66):** a healer
line on the first death and a visible thread pull on every death are now wanted, and built
([#341](https://github.com/jlogicgames/edgard_in_kimeria/issues/341), [D81](DECISIONS.md#d81-the-silver-thread-pulls-edgard-back-on-every-death-the-healer-speaks-on-the-first-playtest-f7-f9-341-2026-11-16)):
- **Every death with a visible cause** shows the thread drawing out from Edgard's wrist to the room start,
  tightening and pulling him back under a fade; he lands at the start with the thread slack. A fall into a
  pit shows only the landing with the slack thread.
- **First death of a New Game run, one healer line**, key `HEALER_FIRST_DEATH`, author-approved:
  "Edgard, the night swallowed you, so I drew the silver thread, and you rise again at the path's edge. Go on."
  It keeps the start tale's promise in the healer's own words; it is shown once per run, saved when it opens,
  so Continue never repeats it, and never on a later death, Restart Level or a collection return.
- The thread stays narrative: the one saved "told" flag is not a checkpoint, healing or persistence mechanic.

Also revised after the playtest:
the fireball becomes the healer's second gift, given with the thread
([#340](https://github.com/jlogicgames/edgard_in_kimeria/issues/340)); the fairies of the start
tale become a visible fairy helper ([#338](https://github.com/jlogicgames/edgard_in_kimeria/issues/338), done: "The fairy helper" above);
all story texts are audited against the magic-tale structure first
([#339](https://github.com/jlogicgames/edgard_in_kimeria/issues/339)). Until those land, the
texts above are the shipped ones. The start tale runs once per New Game run and is never repeated by death,
room retry or Restart Level; the opening plays once per New Game. Wording rules, tests and
measured sizes: [D41 note](DECISIONS.md#d41-the-healers-tales-around-the-forest-0-tutorial) and
[localization validation](LOCALIZATION_VALIDATION.md).
