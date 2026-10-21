# Documentation

Start with the [project overview](../README.md), then choose the question you need
to answer below. The full campaign design and the current two-level test build have
different scopes; a design decision is not evidence that a feature is implemented.

## Reading order and document roles

| Document | Purpose and scope |
| --- | --- |
| [Full game design](GAME_DESIGN.md) | The remake's intended experience, stage → level → room structure, four-stage campaign, player tools, collection and progression. Distinguishes author decisions from proposals and open questions. Start here when designing the expanded game. |
| [Development roadmap](ROADMAP.md) | Working sequence: mechanics MVP for the four stages → campaign production and first external playtests → release completion. Defines outputs and exit criteria, with links to existing tasks. |
| [Gameplay reference](GAMEPLAY.md) | How the current test build plays: controls, room flow, artifacts and the forest elixir. Start here when playing or checking existing behavior. |
| [Two-level MVP](MVP.md) | The accepted existing test-version scope, campaign order, ending and save compatibility. Distinct from the upcoming mechanics MVP in the roadmap and from the full campaign. |
| [Design decisions](DECISIONS.md) | Existing decisions, rationale and issue references, including death, retries, input, hitboxes and saves. Accepted entries may describe planned behavior; consult the implementation documents for status. |
| [Story and recurring characters](STORY.md) | Established world facts, the healer's role, implemented appearances and unresolved narrative details. |
| [Original DOS references](ORIGINAL_DOS.md) | Historical story, screenshot transcriptions and character references. Source material for the remake; historical mechanics and region order are not automatically remake requirements. |
| [Rooms in Tiled](ROOMS.md) | Map authoring, room transitions, retry/rollback rules, replay budgets and measured routes. |
| [Forest boss](BOSS.md) | The implemented forest encounter: attacks, balance, hit feedback, retry and completion rules. Its values do not automatically apply to future bosses. |
| [Forest healer](HEALER.md) | The implemented encounter: dialogue, recipe/payment, collection returns and the detailed state retention/reset table. |
| [Development and implementation](DEVELOPMENT.md) | Build/run instructions, architecture, persistence backends, assets and troubleshooting. |
| [Translation catalogs and fonts](LOCALIZATION.md) | Editing/adding translations, message context, strict validation, glyph coverage and font regeneration. |
| [Localization validation](LOCALIZATION_VALIDATION.md) | Dated #85 test, desktop/browser rendering and persistence evidence, with human/device review limits. |
| [Pre-release TODO](PRE_RELEASE_TODO.md) | Outstanding release acceptance checks and how to record completion. This is the active release checklist. |
| [Project skill validation](SKILL_VALIDATION.md) | Dated evidence from the project-skill validation pass, with its limits. |
| [Post-boss guide validation](POST_BOSS_VALIDATION.md) | Dated evidence for the boss exit → healer → next-level boundary and dialogue styling. |

## Find a topic

| Question | Primary reference | Related detail |
| --- | --- | --- |
| What game are we building? | [Full game design](GAME_DESIGN.md) | [Original story](ORIGINAL_DOS.md#supplied-original-story-spelling-and-punctuation-normalized) |
| What should we build next, and when do playtests begin? | [Development roadmap](ROADMAP.md) | [Mechanics MVP](ROADMAP.md#mechanics-mvp), then [campaign and first playtests](ROADMAP.md#campaign-and-playtests) |
| Where are the roadmap implementation tasks? | [GitHub tracking](ROADMAP.md#github-tracking) | Every roadmap checkbox has its own English issue; #95 is reused and #97–#145 cover the other 49 items |
| How do stages, levels and rooms differ? | [Campaign structure](GAME_DESIGN.md#campaign-structure) | [Current map/room authoring](ROOMS.md#authoring) |
| What happens after death? | [D3: room retries](DECISIONS.md#d3-death-resets-the-current-room-and-respawn-is-instant) | [Transition and retry](ROOMS.md#transition-and-retry), [state policy](HEALER.md#state-policy) |
| How short should a retry be? | [Replay hypothesis: about 20 seconds](ROOMS.md#replay-budget-70) | [Recorded routes under original criteria](ROOMS.md#current-replay-measurements-after-removing-bat-slow-motion) |
| How long is first-time learning or the whole campaign? | [Pacing and duration](GAME_DESIGN.md#pacing-and-duration) | [D19: about 60 seconds of first learning](DECISIONS.md#d19-test-a-20-second-replay-and-60-second-first-learning); a hypothesis, not a measured genre average |
| What is decided about wind, ladders and jumps? | [Movement progression](GAME_DESIGN.md#wind-and-ladders) | Double jump from the Steppe is accepted in [D18](DECISIONS.md#d18-directional-controls-steppe-double-jump-and-replay-target); wall jump already exists, its future role remains open |
| What survives closing the game? | [D8: automatic level saves](DECISIONS.md#d8-main-menu-has-continue-and-new-game) | [Persistence implementation](DEVELOPMENT.md#game-state-progress-and-saves), [MVP compatibility](MVP.md#state-and-compatibility) |
| How will ranged ammunition reset? | [Full-game death and saves](GAME_DESIGN.md#death-and-saves) | [Existing pickup rollback](ROOMS.md#transition-and-retry); ranged combat is not implemented yet |
| What must the player collect? | [Campaign collection design](GAME_DESIGN.md#collection-and-elixirs) | [Current optional artifacts](GAMEPLAY.md#optional-artifacts), [existing D6](DECISIONS.md#d6-collectables) |
| What is the coin-spending choice? | [Campaign economy](GAME_DESIGN.md#economy-proposals) | Limited coins: elixir versus extra fireballs is accepted; prices/limits are open, crystals and other purchasable upgrades remain unselected |
| How do the healer's payment and collection returns work now? | [Recipe and encounter boundary](HEALER.md#recipe-payment-and-encounter-boundary) | [State policy](HEALER.md#state-policy) |
| What are the controls? | [Current controls](GAMEPLAY.md#controls) | [Full-game player tools](GAME_DESIGN.md#player-tools), [left-handed layouts](DECISIONS.md#d9-left-handed-mode-swaps-the-key-bindings-and-mirrors-touch) |
| Where is the approved directional control scheme tracked? | [GitHub #95](https://github.com/jlogicgames/edgard_in_kimeria/issues/95) | [D18](DECISIONS.md#d18-directional-controls-steppe-double-jump-and-replay-target); approved design, implementation pending |
| How do hits, stomps and traps stay fair? | [Hitbox conventions](DECISIONS.md#d10-hitbox-and-collision-conventions) | [Trap rules](DECISIONS.md#d7-traps-are-deterministic-puzzle-elements), [assist](DECISIONS.md#d11-assist-mode-a-deliberate-exception-to-d1) |
| How does the current boss work? | [Forest boss](BOSS.md) | [Balance values](BOSS.md#balance-values), [retry and completion](BOSS.md#retry-elixir-and-completion) |
| What is established about the story and portraits? | [Story](STORY.md) | [DOS story and portrait references](ORIGINAL_DOS.md) |
| How do I build, run or edit maps? | [Build and run](DEVELOPMENT.md#building-and-running) | [Live editing](DEVELOPMENT.md#live-editing-gradlew-rundev), [map authoring](ROOMS.md#authoring) |
| What still needs release validation? | [Pre-release TODO](PRE_RELEASE_TODO.md) | [Skill evidence](SKILL_VALIDATION.md), [post-boss evidence](POST_BOSS_VALIDATION.md) |
| How is design maintained during development? | [Design maintenance](GAME_DESIGN.md#design-maintenance) | [D17](DECISIONS.md#d17-maintain-the-game-design-throughout-development) |
| What sources support the reference-game comparisons? | [Research references](GAME_DESIGN.md#research-references) | Distinguishes manuals, developer descriptions and player/guide evidence |

## Using and updating these documents

The design is a living document. Follow the
[maintenance workflow](GAME_DESIGN.md#design-maintenance) when a design decision,
implementation change or playtest changes an agreed rule; see also
[D17](DECISIONS.md#d17-maintain-the-game-design-throughout-development).

Use the [roadmap](ROADMAP.md) for the current work sequence and exit criteria.
Update it alongside the design when prototype or playtest results change the plan;
do not confuse the existing two-level test MVP with the upcoming mechanics MVP.

- Use the full game design for the expanded campaign's agreed direction. Use the
  gameplay reference, MVP and encounter documents to establish what the test build
  currently does. In particular, required tablet fragments and optional ingredients
  belong to the future campaign; the current Chicken/Key recipe remains documented
  as current behavior.
- Read the decision log and the relevant topic document together before changing an
  existing rule. A new campaign direction does not silently change an implementation
  contract. Resolve any conflict explicitly when specifying the implementation.
- Keep proposals and open questions visibly separate from author decisions. Keep
  implementation status separate from acceptance and validation status.
- Update a rule in its owning document and link to it elsewhere. Avoid creating
  another full copy of the same rule in a validation report or a technical guide.
- Validation reports record what was checked at a particular time. A deferred check
  is still outstanding; dated evidence does not replace the active release checklist.
- Add new documents to this index, with their purpose, scope and relevant topic links.
