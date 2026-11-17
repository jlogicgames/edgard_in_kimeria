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
                'wall-brick': WALL_FRAME_SIZE, 'platform': PLATFORM_SIZE,
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
    'branch': ((32, 16), ('resting', 'rustle', 'sag', 'falling')),
    'stone': ((16, 16), ('stone', 'cracked')),
    'shadow': ((24, 8), ('small', 'medium', 'large')),
    'puffball': ((16, 16), ('idle', 'swell1', 'swell2', 'burst')),
    'spores': ((24, 24), ('cloud1', 'cloud2', 'cloud3')),
    'vine': ((24, 40), ('hang', 'swing-left', 'swing-right')),
    'log': ((32, 16), ('intact', 'crumble1', 'crumble2')),
    # #349 (D80): the branch skins. perch = the sound branch (a one-way Platform), rotten-perch = the rotten branch
    # (a FallingPlatform, frames as the log's); each with its copy for the left side of a trunk.
    'perch': ((32, 16), ('perch', 'perch-left')),
    'rotten-perch': ((32, 16), ('intact', 'crumble1', 'crumble2', 'intact-left', 'crumble1-left', 'crumble2-left')),
    # #295 phase 1: the two look-alike mushrooms; idle1..idle3 is the brightness-pulse idle, swell1/swell2 the D7 telegraph.
    'honey-fungus': ((24, 16), ('idle1', 'idle2', 'idle3', 'swell1', 'swell2', 'spent')),
    'false-honey-fungus': ((24, 16), ('idle1', 'idle2', 'idle3', 'swell1', 'swell2', 'spent')),
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
    'trunk': ((16, 288), ('trunk',)),  # #349 (D80): the tree trunk behind the branches, one frame
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
# The hero (#343, D79): hand-drawn grids in art/hero/hero.grid -> three sheets of 64x64 cells, one clip per row.
# Facing sheets share one layout; the sword stays in Edgard's right hand, so the left sheet is drawn, not flipped at runtime.
HERO_FRAME_SIZE = 64
HERO_CLIPS = (('idle', 4), ('run', 6), ('jump', 2), ('fall', 2), ('attack', 7), ('look', 2), ('clamber', 1),
              ('hit', 2), ('appear', 4), ('disappear', 4),
              # the second set (#343 part 2): drawn over the states by Player; pull is #341's tug, lift, land
              ('carry_idle', 4), ('carry_run', 6), ('throw', 3), ('land', 1), ('pull', 3))
HERO_LADDER_CLIPS = (('up', 4), ('down', 4), ('hold', 1))  # back view, used by both facings
HERO_SHEETS = {'right': HERO_CLIPS, 'left': HERO_CLIPS, 'ladder': HERO_LADDER_CLIPS}
HERO_FRAME_PREFIX = {'right': 'r', 'left': 'l', 'ladder': 'ladder'}  # frame names in the grid: r.idle1, l.run3, ladder.up2
HERO_IMAGES = {name: (HERO_FRAME_SIZE * max(count for _, count in clips), HERO_FRAME_SIZE * len(clips))
               for name, clips in HERO_SHEETS.items()}
