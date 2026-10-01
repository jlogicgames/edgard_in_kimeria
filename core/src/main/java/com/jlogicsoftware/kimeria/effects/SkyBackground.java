package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;

/**
 * Port of Dart's {@code SkyTile}: a screen-fixed sky whose clouds drift
 * left-to-right forever. Dart attaches it as {@code camera.backdrop}; here it
 * is re-anchored to the top-left of the visible world rect every frame, so
 * camera movement never shifts it.
 *
 * <p>The image is scaled to the view height and tiled horizontally by hand
 * instead of using {@code TextureWrap.Repeat}, which needs a power-of-two
 * texture on WebGL 1 (sky.png is 960x750).
 */
public class SkyBackground {
    private static final String PATH = "images/background/sky.png";
    /** Cloud drift in logical px/s, matching Dart's {@code baseVelocity: Vector2(5, 0)}. */
    private static final float SPEED = 5f;
    /** Tiles overlap by this much so fractional positions never leave a seam between them. */
    private static final float OVERLAP = 1f;

    private final TextureRegion region;
    private float scroll;

    public SkyBackground(Assets assets) {
        Texture texture = assets.texture(PATH);
        region = assets.region(PATH, 0, 0, texture.getWidth(), texture.getHeight());
    }

    public void update(float dt) {
        scroll += SPEED * dt;
    }

    public void render(Batch batch, Rectangle view) {
        float tileW = region.getRegionWidth() * view.height / region.getRegionHeight();
        scroll %= tileW;
        float right = view.x + view.width;
        // One tile to the left of the view, so the wrapped-in sliver is always covered.
        for (float x = view.x - tileW + scroll; x < right; x += tileW) {
            batch.draw(region, x, view.y, tileW + OVERLAP, view.height);
        }
    }
}
