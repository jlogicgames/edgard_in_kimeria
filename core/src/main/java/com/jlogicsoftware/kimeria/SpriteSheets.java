package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Native artwork formats. These frame dimensions are independent of TMX tile dimensions. */
public final class SpriteSheets {
    public static final Sheet STONE = new Sheet("images/objects/stone.png", 8, 8);
    public static final Sheet TORCH = new Sheet("images/objects/torch.png", 8, 16);
    public static final Sheet FIREBALL = new Sheet("images/objects/fireball.png", 16, 16);
    public static final Sheet LEVER = new Sheet("images/objects/lever.png", 16, 16);
    public static final Sheet LADDER = new Sheet("images/objects/ladder.png", 16, 16);
    public static final Sheet WALL = new Sheet("images/objects/wall-brick.png", 48, 96);
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
