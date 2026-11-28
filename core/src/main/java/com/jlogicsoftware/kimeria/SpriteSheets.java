package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Native artwork formats. These frame dimensions are independent of TMX tile dimensions. */
public final class SpriteSheets {
    public static final Sheet STONE = new Sheet("images/objects/stone.png", 8, 8);
    public static final Sheet TORCH = new Sheet("images/objects/torch.png", 8, 16);
    public static final Sheet FIREBALL = new Sheet("images/objects/fireball.png", 16, 16);
    public static final Sheet LEVER = new Sheet("images/objects/lever.png", 16, 16);
    public static final Sheet LADDER = new Sheet("images/objects/ladder.png", 16, 16);
    public static final Sheet PLATFORM = new Sheet("images/objects/platform.png", 32, 16);
    public static final Sheet WALL = new Sheet("images/objects/wall-gate.png", 48, 96);
    // Forest trap sprites (#230): authored and validated, not drawn by any code yet. Frame order: tools/sprite_layouts.py FOREST_TRAP_SHEETS.
    public static final Sheet TRAP_STUMP = new Sheet("images/objects/trap-stump.png", 16, 16);
    public static final Sheet TRAP_ROOT = new Sheet("images/objects/trap-root.png", 16, 24);
    public static final Sheet TRAP_BOMB = new Sheet("images/objects/trap-bomb.png", 16, 16);
    public static final Sheet TRAP_BLAST = new Sheet("images/objects/trap-blast.png", 32, 32);
    public static final Sheet TRAP_THORN_ROOT = new Sheet("images/objects/trap-thorn-root.png", 16, 24);
    public static final Sheet TRAP_THORN_TUFT = new Sheet("images/objects/trap-thorn-tuft.png", 16, 16);
    public static final Sheet TRAP_BRANCH = new Sheet("images/objects/trap-branch.png", 32, 16);
    public static final Sheet TRAP_STONE = new Sheet("images/objects/trap-stone.png", 16, 16);
    public static final Sheet TRAP_SHADOW = new Sheet("images/objects/trap-shadow.png", 24, 8);
    // The falling stone's at-rest cue (#360): loose, broken.
    public static final Sheet TRAP_STONE_LIP = new Sheet("images/objects/trap-stone-lip.png", 24, 8);
    public static final Sheet TRAP_BRAMBLE = new Sheet("images/objects/trap-bramble.png", 16, 24);
    public static final Sheet TRAP_PUFFBALL = new Sheet("images/objects/trap-puffball.png", 16, 16);
    // The hanging hive (#365, D90): rest, shake1, shake2, release, bare, falling, fallen; its stinger curtain tile (fall1, fall2).
    public static final Sheet TRAP_HIVE = new Sheet("images/objects/trap-hive.png", 24, 32);
    public static final Sheet TRAP_STINGERS = new Sheet("images/objects/trap-stingers.png", 8, 16);
    // The ember fissure (#366, D91): rest1, rest2, warn1, warn2, vent, spent; its jet column tile (jet1, jet2) and tip (top1, top2).
    public static final Sheet TRAP_FISSURE = new Sheet("images/objects/trap-fissure.png", 16, 12);
    public static final Sheet TRAP_EMBER_JET = new Sheet("images/objects/trap-ember-jet.png", 8, 16);
    // The leg-hold trap (#561, D128): open, rattle1, rattle2, snap, shut, rearm. The lance root (#562, D129): the fissure's frames, its shaft tile and tip.
    public static final Sheet TRAP_LEGHOLD = new Sheet("images/objects/trap-leghold.png", 32, 16);
    public static final Sheet TRAP_LANCE_ROOT = new Sheet("images/objects/trap-lance-root.png", 16, 12);
    public static final Sheet TRAP_LANCE_SHAFT = new Sheet("images/objects/trap-lance-shaft.png", 8, 16);
    public static final Sheet TRAP_VINE = new Sheet("images/objects/trap-vine.png", 24, 40);
    public static final Sheet TRAP_LOG = new Sheet("images/objects/trap-log.png", 32, 16);
    // Tree branches (#349, D80): perch, perch-left (the sound branch); intact, crumble1, crumble2 and their -left copies (the rotten branch).
    public static final Sheet TRAP_PERCH = new Sheet("images/objects/trap-perch.png", 32, 16);
    public static final Sheet TRAP_ROTTEN_PERCH = new Sheet("images/objects/trap-rotten-perch.png", 32, 16);
    // The trip-line deadfall (#363, D88): the log (tied, strain, slip, swing, caught), the bough, the trip vine (taut, tugged, cut), the rope strip.
    public static final Sheet TRAP_DEADFALL = new Sheet("images/objects/trap-deadfall.png", 32, 16);
    public static final Sheet TRAP_DEADFALL_BOUGH = new Sheet("images/objects/trap-deadfall-bough.png", 112, 24);
    public static final Sheet TRAP_DEADFALL_VINE = new Sheet("images/objects/trap-deadfall-vine.png", 24, 8);
    public static final Sheet TRAP_DEADFALL_ROPE = new Sheet("images/objects/trap-deadfall-rope.png", 1, 8);
    // #295 phase 1 (art only, no code draws them yet): idle1..idle3, swell1, swell2, spent; tools/sprite_layouts.py FOREST_TRAP_SHEETS.
    public static final Sheet TRAP_HONEY_FUNGUS = new Sheet("images/objects/trap-honey-fungus.png", 24, 16);
    public static final Sheet TRAP_FALSE_HONEY_FUNGUS = new Sheet("images/objects/trap-false-honey-fungus.png", 24, 16);
    // Decor (#239 step 4): frame order idle, brush1, brush2, settle; tools/sprite_layouts.py DECOR_SHEETS.
    public static final Sheet DECOR_BUSH = new Sheet("images/objects/decor-bush.png", 32, 24);
    // Hanging scenery vine (#239 step 5): one frame; tools/sprite_layouts.py DECOR_SHEETS.
    public static final Sheet DECOR_VINE = new Sheet("images/objects/decor-vine.png", 8, 40);
    // Static look-alike mushroom scenery (#299): one frame, never animated; tools/sprite_layouts.py DECOR_SHEETS.
    public static final Sheet DECOR_LOOKALIKE = new Sheet("images/objects/decor-lookalike.png", 24, 16);
    // Tree trunk behind the branches (#349, D80): one frame; tools/sprite_layouts.py DECOR_SHEETS. 28 wide since D122 (the
    // branches' roots lie over its bark), centred on its 16 px object.
    public static final Sheet DECOR_TRUNK = new Sheet("images/objects/decor-trunk.png", 28, 288);
    // A dead tree's snapped trunk (D122): one frame, 200 px tall, for a branch whose tree must stop below the moon.
    public static final Sheet DECOR_SNAG = new Sheet("images/objects/decor-snag.png", 28, 200);
    // The second trunk (#635): the trunk's bark with a hollow and a burl, so a row of trees does not repeat one shape.
    public static final Sheet DECOR_TRUNK_KNOT = new Sheet("images/objects/decor-trunk-knot.png", 28, 288);
    // Leaf crowns hung from the room top over a trunk (#635, D162; they replace the 96x40 crown of #421): one frame each,
    // centred on their object, which is centred on the trunk; tools/sprite_layouts.py DECOR_SHEETS.
    public static final Sheet DECOR_CROWN_DROOP = new Sheet("images/objects/decor-crown-droop.png", 208, 160);
    public static final Sheet DECOR_CROWN_BROAD = new Sheet("images/objects/decor-crown-broad.png", 224, 128);
    public static final Sheet DECOR_CROWN_ROUND = new Sheet("images/objects/decor-crown-round.png", 160, 144);
    // The same crowns in the fine-clump style (D162): the author keeps both styles.
    public static final Sheet DECOR_CROWN_DROOP_FINE = new Sheet("images/objects/decor-crown-droop-fine.png", 208, 160);
    public static final Sheet DECOR_CROWN_BROAD_FINE = new Sheet("images/objects/decor-crown-broad-fine.png", 224, 128);
    public static final Sheet DECOR_CROWN_ROUND_FINE = new Sheet("images/objects/decor-crown-round-fine.png", 160, 144);
    // The hero (#343, D79): 64x64 cells, one clip per row; tools/sprite_layouts.py HERO_SHEETS, drawn from art/hero/hero.grid.
    public static final Sheet HERO_RIGHT = new Sheet("images/hero/hero-right.png", 64, 64);
    public static final Sheet HERO_LEFT = new Sheet("images/hero/hero-left.png", 64, 64);
    public static final Sheet HERO_LADDER = new Sheet("images/hero/hero-ladder.png", 64, 64);
    // The silver thread on death (#341, D81): the 1x2 strand at (0, 0) and the 5x5 glint at (1, 0); tools/silver_thread_sprites.py.
    public static final Sheet SILVER_THREAD = new Sheet("images/objects/silver-thread.png", 6, 5);

    // The forest beetle (#327, D100): ten 24x16 frames, one strip; tools/sprite_layouts.py MONSTER_SHEETS, art/objects/monster/beetle.grid.
    public static final Sheet MONSTER_BEETLE = new Sheet("images/objects/monster-beetle.png", 24, 16);
    /** D101: the beetle's body, its dead frame in its live palette and rim (D117; art/objects/monster/beetle-body.grid). */
    public static final Sheet MONSTER_BEETLE_BODY = new Sheet("images/objects/monster-beetle-body.png", 24, 16);
    // The forest flying beetle (#330, D145): ten 40x32 frames, one strip; art/objects/monster/flying-beetle.grid.
    public static final Sheet MONSTER_FLYING_BEETLE = new Sheet("images/objects/monster-flying-beetle.png", 40, 32);
    /** D101: the flying beetle's body, its dead frame in its live palette and rim (D117). */
    public static final Sheet MONSTER_FLYING_BEETLE_BODY = new Sheet("images/objects/monster-flying-beetle-body.png", 40, 32);
    // The forest spider (#328, D102): nine 32x16 frames, one strip; tools/sprite_layouts.py MONSTER_SHEETS, art/objects/monster/spider.grid.
    public static final Sheet MONSTER_SPIDER = new Sheet("images/objects/monster-spider.png", 32, 16);
    /** D101: the spider's body, its dead frame in its live palette and rim (D117; art/objects/monster/spider-body.grid). */
    // The forest snail (#329, D106): eight 24x16 frames and the gob's cell (two 8x8 frames at its top-left), one strip;
    // tools/sprite_layouts.py MONSTER_SHEETS, art/objects/monster/snail.grid.
    public static final Sheet MONSTER_SNAIL = new Sheet("images/objects/monster-snail.png", 24, 16);
    /** D101: the snail's body, the empty shell (its dead frame) in its live palette and rim (D117; art/objects/monster/snail-body.grid). */
    public static final Sheet MONSTER_SNAIL_BODY = new Sheet("images/objects/monster-snail-body.png", 24, 16);
    // The forest wolf (#331, D115): seventeen 48x24 frames, one strip; tools/sprite_layouts.py MONSTER_SHEETS, art/objects/monster/wolf.grid.
    public static final Sheet MONSTER_WOLF = new Sheet("images/objects/monster-wolf.png", 48, 24);
    /** D101: the wolf's body, its dead frame (lying on its side) in its live palette and rim (D117; art/objects/monster/wolf-body.grid). */
    public static final Sheet MONSTER_WOLF_BODY = new Sheet("images/objects/monster-wolf-body.png", 48, 24);
    // The forest mosquito swarm (#645, D151): one mosquito, a 1-2 px dot in 2x1, two wing frames dark and two fed, a dead and a burning one; art/objects/monster/mosquito.grid.
    public static final Sheet MONSTER_MOSQUITO = new Sheet("images/objects/monster-mosquito.png", 2, 1);
    public static final Sheet MONSTER_SPIDER_BODY = new Sheet("images/objects/monster-spider-body.png", 32, 16);
    /** D103 (#483): the spider on a wall, three 16x32 frames (wall1, wall2, walltap), head up, the wall on the right (spider-wall.grid). */
    public static final Sheet MONSTER_SPIDER_WALL = new Sheet("images/objects/monster-spider-wall.png", 16, 32);

    private SpriteSheets() { }

    public record Sheet(String path, int width, int height) {
        public TextureRegion frame(Assets assets) { return frame(assets, 0, 0); }
        public TextureRegion frame(Assets assets, int column, int row) {
            return assets.region(path, column * width, row * height, width, height);
        }
    }
}
