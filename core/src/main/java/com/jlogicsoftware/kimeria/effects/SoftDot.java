package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * A single shared soft radial-gradient dot texture used by every particle
 * effect (torch sparks/embers/smoke, fireflies, rain). Stands in for the
 * {@code MaskFilter.blur} glow the original Flutter/Skia particles used,
 * which SpriteBatch has no equivalent for.
 */
public final class SoftDot {
    private static Texture texture;
    private static Texture glowTexture;

    private SoftDot() {
    }

    public static TextureRegion region() {
        if (texture == null) {
            texture = build(1f);
        }
        return new TextureRegion(texture);
    }

    /** Same dot with a boosted core (it saturates to full alpha), so small radii still read as a bright light. */
    public static TextureRegion glowRegion() {
        if (glowTexture == null) {
            glowTexture = build(2f);
        }
        return new TextureRegion(glowTexture);
    }

    private static Texture build(float coreBoost) {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float cx = size / 2f, cy = size / 2f, r = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - cx, dy = y - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
                float a = Math.max(0f, 1f - d);
                a = Math.min(1f, a * a * coreBoost); // soften falloff
                pixmap.setColor(1f, 1f, 1f, a);
                pixmap.drawPixel(x, y);
            }
        }
        Texture t = new Texture(pixmap);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return t;
    }

    public static void dispose() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        if (glowTexture != null) {
            glowTexture.dispose();
            glowTexture = null;
        }
    }
}
