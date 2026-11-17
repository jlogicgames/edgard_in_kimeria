package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;

/**
 * The Kimeria arms (#350): one strip of native 24x36 frames, {@code art/ui/arms/arms.grid} exported by
 * {@code tools/arms_sprites.py}. {@link #LIT} is the reference drawing; the other frames are the sun lighting up on the
 * ending page (#416, D95). Regions are cut once and flipped for the Y-down screen camera; drawing allocates nothing.
 */
public final class KimeriaArms {
    public static final String ART = "images/ui/kimeria-arms.png";
    public static final int WIDTH = 24, HEIGHT = 36;
    /** Frame indices in the strip; the ending plays DARK, EMBER, GLOW, KINDLE, FLARE and then holds LIT. */
    public static final int DARK = 0, EMBER = 1, GLOW = 2, KINDLE = 3, LIT = 4, FLARE = 5, FRAME_COUNT = 6;

    private final TextureRegion[] frames = new TextureRegion[FRAME_COUNT];

    public KimeriaArms(Assets assets) {
        for (int i = 0; i < FRAME_COUNT; i++) frames[i] = assets.region(ART, i * WIDTH, 0, WIDTH, HEIGHT);
    }

    /**
     * Draws {@code frame} untinted at a whole-number {@code scale}, centred on {@code centerX}, its top edge at {@code top}
     * (Y-down screen projection: the arms cover {@code top .. top + HEIGHT * scale} downward). Whole-pixel positions keep
     * the art crisp. The batch colour is saved and restored, so a caller's tint is neither used nor lost.
     */
    public void draw(Batch batch, int frame, float centerX, float top, int scale) {
        float width = WIDTH * scale;
        float saved = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        batch.draw(frames[frame], (float) Math.floor(centerX - width / 2f), (float) Math.floor(top), width, HEIGHT * scale);
        batch.setPackedColor(saved);
    }
}
