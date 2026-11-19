package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.Gdx;

/**
 * Builds the shared particle textures once, when a level loads (#483): the soft dot and its glow ({@link SoftDot}) and the white
 * texel ({@link Shaders#whiteQuad()}). They are built lazily on first use, which would otherwise land on the first frame that
 * draws a particle of that kind; building them at load keeps every frame free of it, whatever particle shows first.
 */
public final class ParticleTextures {
    private ParticleTextures() {
    }

    /** Without a GL context (a logic-only test) nothing can draw, so nothing is built; the lazy path still covers that case. */
    public static void prepare() {
        if (Gdx.gl == null) return;
        SoftDot.region();
        SoftDot.glowRegion();
        Shaders.whiteQuad();
    }
}
