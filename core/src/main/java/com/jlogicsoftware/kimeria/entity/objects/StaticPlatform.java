package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Entity;

/**
 * Repeats the authored 32x16 platform sprite across a static one-way platform collider. The {@code branch} skin (#349,
 * D80) draws one sound tree branch instead (a single 32x16 segment), on the right of its trunk or, with
 * {@code mirror}, on the left; the collider is the same one-way block.
 */
public final class StaticPlatform extends Entity {
    /** Look only; the collider is the same. BRANCH is the sound tree branch, chosen by the map property {@code skin=branch}. */
    public enum Skin {
        PLATFORM, BRANCH;

        public static Skin fromMap(String value) {
            return switch (value) {
                case "" -> PLATFORM;
                case "branch" -> BRANCH;
                default -> throw new IllegalArgumentException("Unknown Platform skin '" + value + "' (expected branch)");
            };
        }
    }

    private static final int PERCH_LEFT_FRAME = 1;
    private final TextureRegion sprite;
    private final Skin skin;

    public StaticPlatform(Assets assets, float x, float y, float width, float height) {
        this(assets, x, y, width, height, Skin.PLATFORM, false);
    }

    public StaticPlatform(Assets assets, float x, float y, float width, float height, Skin skin, boolean mirror) {
        super(x, y, width, height);
        this.skin = skin;
        if (width <= 0 || width % SpriteSheets.PLATFORM.width() != 0
            || height != SpriteSheets.PLATFORM.height()) {
            throw new IllegalArgumentException("Static platform must use 32x16 segments");
        }
        if (skin == Skin.BRANCH && width != SpriteSheets.TRAP_PERCH.width())
            throw new IllegalArgumentException("A branch Platform is one 32x16 segment");
        if (mirror && skin != Skin.BRANCH) throw new IllegalArgumentException("mirror applies to the branch skin only");
        sprite = skin == Skin.BRANCH ? SpriteSheets.TRAP_PERCH.frame(assets, mirror ? PERCH_LEFT_FRAME : 0, 0)
            : SpriteSheets.PLATFORM.frame(assets);
    }

    public Skin skin() { return skin; }

    @Override
    public void update(float dt) {
    }

    @Override
    public void render(Batch batch) {
        int segmentCount = (int) (getWidth() / SpriteSheets.PLATFORM.width());
        for (int segment = 0; segment < segmentCount; segment++) {
            batch.draw(sprite, getX() + segment * SpriteSheets.PLATFORM.width(), getY(),
                SpriteSheets.PLATFORM.width(), SpriteSheets.PLATFORM.height());
        }
    }
}
