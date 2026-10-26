package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Entity;

/** Repeats the authored 32x16 platform sprite across a static platform collider. */
public final class StaticPlatform extends Entity {
    private final TextureRegion sprite;

    public StaticPlatform(Assets assets, float x, float y, float width, float height) {
        super(x, y, width, height);
        if (width <= 0 || width % SpriteSheets.PLATFORM.width() != 0
            || height != SpriteSheets.PLATFORM.height()) {
            throw new IllegalArgumentException("Static platform must use 32x16 segments");
        }
        sprite = SpriteSheets.PLATFORM.frame(assets);
    }

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
