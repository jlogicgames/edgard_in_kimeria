"""Named native-art/export contracts; map tile dimensions are a separate source of data."""
WORLD_PALETTE_SIZE = 16
HUD_PALETTE_SIZE = 256
WORLD_PALETTE_BITS = 4
HUD_PALETTE_BITS = 8
ALPHA_THRESHOLD = 128
OPAQUE_ALPHA = 255
RGB_CHANNELS = 3
ALPHA_CHANNEL = RGB_CHANNELS
PALETTE_ENTRIES = 256
HUD_ICON_SIZE = (128, 128)
LEVER_FRAME_SIZE = 16
LEVER_FRAME_COUNT = 2
LADDER_FRAME_SIZE = 16
LADDER_FRAME_COUNT = 2
WALL_FRAME_SIZE = (48, 96)
PLATFORM_SIZE = (32, 16)
# The escalator bar (#236): art/objects/escalator.grid, frame off -> GreyOff.png, on1..on8 -> GreyOn.png (one row).
ESCALATOR_FRAME_SIZE = (32, 8)
ESCALATOR_ON_FRAMES = 8
ITEM_FRAME_SIZE = 16
ITEM_FRAME_COUNT = 2
ITEM_STRIP_SIZE = (ITEM_FRAME_SIZE * ITEM_FRAME_COUNT, ITEM_FRAME_SIZE)
ITEM_STRIPS = ('coin', 'bat', 'mushroom')  # the coin (#350) and the healer ingredients: two registered 16x16 frames each
HUD_ICONS = ('coin', 'bat', 'mushroom', 'fireball')
# Light high-contrast touch controls (#226, D55): hand-drawn grids in art/hud/touch -> assets/images/HUD/touch.
TOUCH_CONTROL_SIZES = {'disc': (64, 64), 'disc-down': (64, 64), 'base': (64, 64), 'knob': (32, 32), 'knob-down': (32, 32),
                       'jump': (64, 64), 'jump-down': (64, 64), 'pause': (64, 64), 'pause-down': (64, 64),
                       'skip': (64, 64), 'skip-down': (64, 64), 'cue': (8, 8)}
WORLD_FRAMES = {'torch': (8, 16), 'stone': (8, 8), 'fireball': (16, 16)}
# The silver thread on death (#341, D81): art/objects/silver-thread.grid -> assets/images/objects/silver-thread.png,
# the 1x2 strand at (0, 0) and the 5x5 glint at (1, 0); exported exactly by tools/silver_thread_sprites.py.
SILVER_THREAD_SIZE = (6, 5)
WORLD_IMAGES = {**WORLD_FRAMES, 'lever': (LEVER_FRAME_SIZE * LEVER_FRAME_COUNT, LEVER_FRAME_SIZE),
                'ladder': (LADDER_FRAME_SIZE * LADDER_FRAME_COUNT, LADDER_FRAME_SIZE),
                'wall-gate': WALL_FRAME_SIZE, 'platform': PLATFORM_SIZE,
                'GreyOff': ESCALATOR_FRAME_SIZE, 'GreyOn': (ESCALATOR_FRAME_SIZE[0] * ESCALATOR_ON_FRAMES, ESCALATOR_FRAME_SIZE[1]),
                **{name: ITEM_STRIP_SIZE for name in ITEM_STRIPS}}
# World objects retain transparent surroundings; alpha remains binary after export.
OPAQUE_WORLD_IMAGES = set()
# Forest trap sheets (#230): name -> (frame size, ordered frame names); horizontal strips, binary alpha.
FOREST_TRAP_SHEETS = {
    'stump': ((16, 16), ('idle1', 'idle2', 'idle3', 'idle4', 'windup1', 'windup2', 'windup3', 'spent')),
    'root': ((16, 24), ('idle1', 'idle2', 'idle3', 'idle4', 'windup1', 'windup2', 'windup3', 'spent')),
    'bomb': ((16, 16), ('idle1', 'idle2')),
    'blast': ((32, 32), tuple(f'blast{n}' for n in range(1, 13))),
    'thorn-root': ((16, 24), ('hidden', 'peek1', 'peek2', 'rising', 'up')),
    'thorn-tuft': ((16, 16), ('calm', 'bristle', 'spiked')),
    # #364 (D89): the creeping bramble; the clump at rest, its two rustle frames, the arm's two cane segments and its tip.
    'bramble': ((16, 24), ('rest', 'rustle1', 'rustle2', 'cane1', 'cane2', 'tip')),
    'branch': ((32, 16), ('resting', 'rustle', 'sag', 'falling')),
    'stone': ((16, 16), ('stone', 'cracked')),
    'shadow': ((24, 8), ('small', 'medium', 'large')),
    'stone-lip': ((24, 8), ('loose', 'broken')),  # #360: the stone's at-rest cue, the loose overhang over its start point
    'puffball': ((16, 16), ('idle1', 'idle2', 'swell1', 'swell2', 'burst', 'spent')),
    'vine': ((24, 40), ('hang', 'swing-left', 'swing-right')),
    'log': ((32, 16), ('intact', 'crumble1', 'crumble2')),
    # #362: the plain falling platform; since #236 a lashed plank whose slack rope sways through the frames (named fan1..fan4
    # from the old fan) and whose split is the D71 cue.
    'falling-platform': ((32, 10), ('fan1', 'fan2', 'fan3', 'fan4')),
    # #349 (D80): the branch skins. perch = the sound branch (a one-way Platform), rotten-perch = the rotten branch
    # (a FallingPlatform, frames as the log's); each with its copy for the left side of a trunk.
    'perch': ((32, 16), ('perch', 'perch-left')),
    'rotten-perch': ((32, 16), ('intact', 'crumble1', 'crumble2', 'intact-left', 'crumble1-left', 'crumble2-left')),
    # #295 phase 1: the two look-alike mushrooms; idle1..idle3 is the brightness-pulse idle, swell1/swell2 the D7 telegraph.
    'honey-fungus': ((24, 16), ('idle1', 'idle2', 'idle3', 'swell1', 'swell2', 'spent')),
    'false-honey-fungus': ((24, 16), ('idle1', 'idle2', 'idle3', 'swell1', 'swell2', 'spent')),
    # #363 (D88): the trip-line deadfall. The log (tied, strain and slip carry the lashing; swing; caught), the bough with
    # its knots and snag, the trip vine on the floor, and the rope strip the game stretches from knot to log.
    'deadfall': ((32, 16), ('tied', 'strain', 'slip', 'swing', 'caught')),
    'deadfall-bough': ((112, 24), ('bough',)),
    'deadfall-vine': ((24, 8), ('taut', 'tugged', 'cut')),
    'deadfall-rope': ((1, 8), ('rope',)),
    # #365 (D90): the hanging hive (bough drawn in) and its stinger curtain, a tile repeated down the lethal band.
    'hive': ((24, 32), ('rest', 'shake1', 'shake2', 'release', 'bare', 'falling', 'fallen')),
    'stingers': ((8, 16), ('fall1', 'fall2')),
    # #366 (D91): the ember fissure (a crust on the floor line with a glowing slit) and its jet, a tile repeated up the column
    # under a tip (top1/top2 use the upper 8 rows).
    'fissure': ((16, 12), ('rest1', 'rest2', 'warn1', 'warn2', 'vent', 'spent')),
    'ember-jet': ((8, 16), ('jet1', 'jet2', 'top1', 'top2')),
}
FOREST_TRAP_FRAME_COUNTS = {name: len(frames) for name, (_, frames) in FOREST_TRAP_SHEETS.items()}
# Runtime exports are `assets/images/objects/trap-<name>.png`, one horizontal strip each.
WORLD_IMAGES.update({f'trap-{name}': (size[0] * len(frames), size[1])
                     for name, (size, frames) in FOREST_TRAP_SHEETS.items()})
# Decor sheets (#239 step 4): name -> (frame size, ordered frame names); runtime export `decor-<name>.png`, one horizontal strip.
DECOR_SHEETS = {
    'bush': ((32, 24), ('idle', 'brush1', 'brush2', 'settle')),
    'vine': ((8, 40), ('hang',)),  # #239 step 5: the stone-linked HangingVine
    'lookalike': ((24, 16), ('static',)),  # #299: the static look-alike, one frame, never animated
    'trunk': ((28, 288), ('trunk',)),  # #349 (D80): the tree trunk behind the branches, one frame; 28 wide since D122
    'snag': ((28, 200), ('snag',)),  # D122: a dead tree's snapped trunk, for a branch whose tree must stop below the moon
    'crown': ((96, 40), ('crown',)),  # #421: the leaf crown hung over a trunk, one frame
}
WORLD_IMAGES.update({f'decor-{name}': (size[0] * len(frames), size[1]) for name, (size, frames) in DECOR_SHEETS.items()})
# The shared pickup cue (#345): drawn by every Collectable; runtime export `pickup-<name>.png`, one horizontal strip.
PICKUP_SHEETS = {
    'glow': ((24, 24), ('halo',)),  # additive halo behind the item, alpha pulsing with the bob
    'sparkle': ((5, 5), ('grow', 'star', 'fade')),
}
WORLD_IMAGES.update({f'pickup-{name}': (size[0] * len(frames), size[1]) for name, (size, frames) in PICKUP_SHEETS.items()})
# The fairy helper (#338, D82): runtime export `fairy-<name>.png`, one horizontal strip; the order is FairyGuide's.
FAIRY_SHEETS = {
    'body': ((16, 16), ('idle1', 'idle2', 'idle3', 'idle4', 'fly1', 'fly2', 'point1', 'point2', 'point3',
                        'point1-left', 'point2-left', 'point3-left', 'appear1', 'appear2')),
    'glow': ((24, 24), ('glow',)),  # additive light, drawn as a nine-slice: the target glow and the fairy's own light
    'glint': ((5, 5), ('big', 'small')),  # fairy dust at the selected target's corners
}
WORLD_IMAGES.update({f'fairy-{name}': (size[0] * len(frames), size[1]) for name, (size, frames) in FAIRY_SHEETS.items()})
# Forest monsters (#327, D100): runtime export `monster-<name>.png`, one horizontal strip; the order is the entity's.
MONSTER_SHEETS = {
    'beetle': ((24, 16), ('crawl1', 'crawl2', 'look', 'wiggle', 'crouch', 'air', 'brace', 'bright', 'white', 'dead')),
    'beetle-body': ((24, 16), ('body',)),  # D101: the dead frame, the body that stays; live palette and rim (D117)
    'spider': ((32, 16), ('crawl1', 'crawl2', 'rise', 'tap', 'crouch', 'leap', 'bite', 'recover', 'dead', 'hang', 'hangwind',
                       'silk')),  # #328 (D102); hang, hangwind and the thread tile silk #483 (D103)
    'spider-wall': ((16, 32), ('wall1', 'wall2', 'walltap')),  # #483 (D103): on a wall, head up, the wall on the right
    'spider-body': ((32, 16), ('body',)),  # D101: the spider's dead frame; live palette and rim (D117)
    'snail': ((24, 16), ('crawl1', 'crawl2', 'look', 'blink', 'draw', 'spit', 'rest', 'dead', 'gob')),  # #329 (D106); gob: two 8x8 frames
    'snail-body': ((24, 16), ('body',)),  # D101: the snail's dead frame, the empty shell; live palette and rim (D117)
    'wolf': ((48, 24), ('stand', 'trot1', 'trot2', 'alert', 'hackle', 'scrape', 'run1', 'run2', 'run3', 'run4', 'skid', 'crouch',
                     'lunge', 'bite', 'shake', 'bay', 'dead')),  # #331 (D115)
    'wolf-body': ((48, 24), ('body',)),  # D101: the wolf's dead frame, on its side; live palette and rim (D117)
}
WORLD_IMAGES.update({f'monster-{name}': (size[0] * len(frames), size[1]) for name, (size, frames) in MONSTER_SHEETS.items()})
# The inherited enemy sheets, redrawn as grids (2026-11-21): art/objects/enemy/<name>.grid -> assets/images/enemy/<sheet>.png,
# each frame in the cells the entity reads them from (Enemy.spriteAnimation -> Assets.animation: frame w x h cells from an
# origin, at most four a row, a fifth on the next row). name -> (sheet, frame size, sheet size, {frame: cells (column, row)}).
# A frame may fill several cells (the bat's RUN row repeats its flap); cells no animation reads stay empty.
ENEMY_SHEETS = {
    # RedMob: IDLE row 5, RUN row 1, ATTACK row 2 (two wind-up frames, the strike, the recovery), HIT row 4.
    'red-mob': ('Mobs', (48, 32), (192, 448), {
        'idle1': ((0, 5),), 'idle2': ((1, 5),), 'idle3': ((2, 5),), 'idle4': ((3, 5),),
        'run1': ((0, 1),), 'run2': ((1, 1),), 'run3': ((2, 1),), 'run4': ((3, 1),),
        'windup1': ((0, 2),), 'windup2': ((1, 2),), 'strike': ((2, 2),), 'recover': ((3, 2),),
        'hit': ((0, 4),), 'flash': ((1, 4),), 'fall': ((2, 4),), 'down': ((3, 4),)}),
    # Bat: IDLE five frames from (0, 0), RUN five from (0, 32) (the same flap), HIT four from (0, 64). The fifth frame of a
    # five-frame clip wraps to column 0 of the next row; column 4 is never read.
    'bat': ('Bat', (16, 16), (80, 80), {
        'up': ((0, 0), (0, 2)), 'mid': ((1, 0), (1, 2)), 'level': ((2, 0), (0, 1), (2, 2), (0, 3)), 'down': ((3, 0), (3, 2)),
        'flash': ((0, 4),), 'fold': ((1, 4),), 'tumble': ((2, 4),), 'drop': ((3, 4),)}),
    # Boss (docs/BOSS.md, "Sheet inspection"): WALK row 1, WARNING (2, 0)-(2, 1), COUNTER (2, 2)-(2, 3) with (2, 3) held through
    # COUNTER_RECOVERY, HURT row 3, DEAD row 4, IDLE and RECOVERY row 5, NORMAL row 6, THRONE row 7. Rows 0, 8 and 9 are never read.
    'boss': ('Boss', (48, 32), (192, 320), {
        'idle1': ((0, 5),), 'idle2': ((1, 5),), 'idle3': ((2, 5),), 'idle4': ((3, 5),),
        'walk1': ((0, 1),), 'walk2': ((1, 1),), 'walk3': ((2, 1),), 'walk4': ((3, 1),),
        'raise': ((0, 6),), 'cock': ((1, 6),), 'sweep': ((2, 6),), 'follow': ((3, 6),),
        'warn1': ((0, 2),), 'warn2': ((1, 2),), 'counter1': ((2, 2),), 'counter2': ((3, 2),),
        'recoil': ((0, 3), (2, 3)), 'flash': ((1, 3), (3, 3)),
        'throne1': ((0, 7),), 'throne2': ((1, 7),), 'throne3': ((2, 7),), 'throne4': ((3, 7),),
        'stagger': ((0, 4),), 'kneel': ((1, 4),), 'fall': ((2, 4),), 'ash': ((3, 4),)}),
    # YellowMob: IDLE row 5, RUN row 1, HIT row 4; the other rows are never read.
    'yellow-mob': ('yellow_mob', (48, 32), (192, 320), {
        'idle1': ((0, 5),), 'idle2': ((1, 5),), 'idle3': ((2, 5),), 'idle4': ((3, 5),),
        'run1': ((0, 1),), 'run2': ((1, 1),), 'run3': ((2, 1),), 'run4': ((3, 1),),
        'hit': ((0, 4),), 'flash': ((1, 4),), 'knees': ((2, 4),), 'down': ((3, 4),)}),
}
ENEMY_IMAGES = {sheet: size for sheet, _, size, _ in ENEMY_SHEETS.values()}
# The dialogue screen (#337, D78): hand-drawn grids in art/ui/dialogue -> assets/images/ui/dialogue-<name>.png, one horizontal
# strip each; name -> (frame size, ordered frame names). The nine-patch insets are in HealerDialogueView.
DIALOGUE_SHEETS = {
    'window': ((32, 32), ('lit', 'dim')),
    'crest': ((17, 7), ('lit', 'dim')),
    'plate': ((16, 22), ('lit', 'dim')),
    'box': ((24, 24), ('box',)),
    'page': ((24, 24), ('page',)),
    'choice': ((16, 26), ('idle', 'selected')),
    'notch': ((13, 8), ('notch',)),
    'thread': ((284, 32), ('thread',)),
    'glint': ((5, 5), ('glint',)),
}
DIALOGUE_IMAGES = {name: (size[0] * len(frames), size[1]) for name, (size, frames) in DIALOGUE_SHEETS.items()}
# Frames with no transparent pixel (bands with an opaque fill); the rest keep binary alpha.
OPAQUE_DIALOGUE_IMAGES = {'window', 'plate', 'box', 'page', 'choice'}
# The Kimeria arms (#350, #416, D95): hand-drawn grid art/ui/arms/arms.grid -> assets/images/ui/kimeria-arms.png, one strip.
# Frame `lit` is the reference drawing art/heraldry/kimeria-arms.png; the others are the sun lighting up on the ending page.
ARMS_FRAME_SIZE = (24, 36)
ARMS_FRAMES = ('dark', 'ember', 'glow', 'kindle', 'lit', 'flare')
ARMS_IMAGE_SIZE = (ARMS_FRAME_SIZE[0] * len(ARMS_FRAMES), ARMS_FRAME_SIZE[1])
# The hero (#343, D79): hand-drawn grids in art/hero/hero.grid -> three sheets of 64x64 cells, one clip per row.
# Facing sheets share one layout; the sword stays in Edgard's right hand, so the left sheet is drawn, not flipped at runtime.
HERO_FRAME_SIZE = 64
HERO_CLIPS = (('idle', 4), ('run', 6), ('jump', 2), ('fall', 2), ('attack', 7),
              ('hit', 2), ('appear', 4), ('disappear', 4),
              # the second set (#343 part 2): drawn over the states by Player; pull is #341's tug, lift, land
              ('carry_idle', 4), ('carry_run', 6), ('throw', 3), ('land', 1), ('pull', 3))
HERO_LADDER_CLIPS = (('up', 4), ('down', 4), ('hold', 1))  # back view, used by both facings
HERO_SHEETS = {'right': HERO_CLIPS, 'left': HERO_CLIPS, 'ladder': HERO_LADDER_CLIPS}
HERO_FRAME_PREFIX = {'right': 'r', 'left': 'l', 'ladder': 'ladder'}  # frame names in the grid: r.idle1, l.run3, ladder.up2
HERO_IMAGES = {name: (HERO_FRAME_SIZE * max(count for _, count in clips), HERO_FRAME_SIZE * len(clips))
               for name, clips in HERO_SHEETS.items()}
