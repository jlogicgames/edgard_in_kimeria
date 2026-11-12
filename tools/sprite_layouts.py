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
ITEM_STRIPS = ('bat', 'mushroom')  # healer ingredients: two registered 16x16 frames each
HUD_ICONS = ('coin', 'bat', 'mushroom', 'fireball')
# Light high-contrast touch controls (#226, D55): hand-drawn grids in art/hud/touch -> assets/images/HUD/touch.
TOUCH_CONTROL_SIZES = {'disc': (64, 64), 'disc-down': (64, 64), 'base': (64, 64), 'knob': (32, 32), 'knob-down': (32, 32),
                       'jump': (64, 64), 'jump-down': (64, 64), 'pause': (64, 64), 'pause-down': (64, 64)}
PLAYER_FRAME_SIZE = 48
PLAYER_IDLE_ROW = 9
PLAYER_LOOK_FRAME_COUNT = 2
PLAYER_HEAD_BOUNDS = (24, 23, 31, 31)
WORLD_FRAMES = {'torch': (8, 16), 'stone': (8, 8), 'fireball': (16, 16)}
WORLD_IMAGES = {**WORLD_FRAMES, 'lever': (LEVER_FRAME_SIZE * LEVER_FRAME_COUNT, LEVER_FRAME_SIZE),
                'ladder': (LADDER_FRAME_SIZE * LADDER_FRAME_COUNT, LADDER_FRAME_SIZE),
                'wall-brick': WALL_FRAME_SIZE, 'platform': PLATFORM_SIZE,
                **{name: ITEM_STRIP_SIZE for name in ITEM_STRIPS}}
# World objects retain transparent surroundings; alpha remains binary after export.
OPAQUE_WORLD_IMAGES = set()
PLAYER_LOOK_SIZE = (PLAYER_FRAME_SIZE * PLAYER_LOOK_FRAME_COUNT, PLAYER_FRAME_SIZE)
# Forest trap sheets (#230): name -> (frame size, ordered frame names); horizontal strips, binary alpha.
FOREST_TRAP_SHEETS = {
    'stump': ((16, 16), ('idle', 'windup1', 'windup2', 'windup3', 'spent')),
    'root': ((16, 24), ('idle', 'windup1', 'windup2', 'windup3', 'spent')),
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
}
FOREST_TRAP_FRAME_COUNTS = {name: len(frames) for name, (_, frames) in FOREST_TRAP_SHEETS.items()}
# Runtime exports are `assets/images/objects/trap-<name>.png`, one horizontal strip each.
WORLD_IMAGES.update({f'trap-{name}': (size[0] * len(frames), size[1])
                     for name, (size, frames) in FOREST_TRAP_SHEETS.items()})
# Decor sheets (#239 step 4): name -> (frame size, ordered frame names); runtime export `decor-<name>.png`, one horizontal strip.
DECOR_SHEETS = {
    'bush': ((32, 24), ('idle', 'brush1', 'brush2', 'settle')),
    'vine': ((8, 40), ('hang',)),  # #239 step 5: the stone-linked HangingVine
}
WORLD_IMAGES.update({f'decor-{name}': (size[0] * len(frames), size[1]) for name, (size, frames) in DECOR_SHEETS.items()})
