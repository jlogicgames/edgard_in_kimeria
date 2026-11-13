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
    public static final Sheet WALL = new Sheet("images/objects/wall-brick.png", 48, 96);
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
    public static final Sheet TRAP_PUFFBALL = new Sheet("images/objects/trap-puffball.png", 16, 16);
    public static final Sheet TRAP_SPORES = new Sheet("images/objects/trap-spores.png", 24, 24);
    public static final Sheet TRAP_VINE = new Sheet("images/objects/trap-vine.png", 24, 40);
    public static final Sheet TRAP_LOG = new Sheet("images/objects/trap-log.png", 32, 16);
    // #295 phase 1 (art only, no code draws them yet): idle1..idle3, swell1, swell2, spent; tools/sprite_layouts.py FOREST_TRAP_SHEETS.
    public static final Sheet TRAP_HONEY_FUNGUS = new Sheet("images/objects/trap-honey-fungus.png", 24, 16);
    public static final Sheet TRAP_FALSE_HONEY_FUNGUS = new Sheet("images/objects/trap-false-honey-fungus.png", 24, 16);
    // Decor (#239 step 4): frame order idle, brush1, brush2, settle; tools/sprite_layouts.py DECOR_SHEETS.
    public static final Sheet DECOR_BUSH = new Sheet("images/objects/decor-bush.png", 32, 24);
    // Hanging scenery vine (#239 step 5): one frame; tools/sprite_layouts.py DECOR_SHEETS.
    public static final Sheet DECOR_VINE = new Sheet("images/objects/decor-vine.png", 8, 40);
    public static final Sheet PLAYER = new Sheet("images/hero/Player.png", 48, 48);
    public static final Sheet PLAYER_LOOK = new Sheet("images/hero/Look.png", PLAYER.width(), PLAYER.height());

    private SpriteSheets() { }

    public record Sheet(String path, int width, int height) {
        public TextureRegion frame(Assets assets) { return frame(assets, 0, 0); }
        public TextureRegion frame(Assets assets, int column, int row) {
            return assets.region(path, column * width, row * height, width, height);
        }
    }
}
