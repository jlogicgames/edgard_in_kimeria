package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.HashMap;
import java.util.Map;

/** Loads/caches the GLSL effect shaders (ported from the original .frag files, see assets/shaders). */
public final class Shaders {
    private static final Map<String, ShaderProgram> cache = new HashMap<>();
    private static Texture whitePixel;

    private Shaders() {
    }

    public static ShaderProgram load(String fragmentFile) {
        return cache.computeIfAbsent(fragmentFile, f -> {
            ShaderProgram.pedantic = false;
            ShaderProgram program = new ShaderProgram(
                Gdx.files.internal("shaders/quad.vert"),
                Gdx.files.internal("shaders/" + f));
            if (!program.isCompiled()) {
                throw new GdxRuntimeException("Shader compile failed (" + f + "): " + program.getLog());
            }
            return program;
        });
    }

    /**
     * A 1x1 white texture region used to draw a shader-only quad through SpriteBatch.
     * Flipped vertically like {@code Assets.region}: the game renders through a Y-down
     * camera, so without the flip {@code v_texCoords.y} would run bottom-to-top and the
     * Flutter shaders (which assume Y-down) would come out upside down.
     */
    public static TextureRegion whiteQuad() {
        if (whitePixel == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(Color.WHITE);
            pm.fill();
            whitePixel = new Texture(pm);
            pm.dispose();
        }
        TextureRegion region = new TextureRegion(whitePixel);
        // TextureRegion insets a 1x1 region to UV 0.25..0.75 to prevent atlas
        // bleeding. Procedural shaders need the whole 0..1 domain: with the
        // inset, a ground origin at 0.85 lies outside the rendered quad.
        // Set each bound directly, since setRegion applies that inset again.
        region.setU(0f);
        region.setU2(1f);
        region.setV(1f);
        region.setV2(0f);
        return region;
    }

    public static void dispose() {
        cache.values().forEach(ShaderProgram::dispose);
        cache.clear();
        if (whitePixel != null) {
            whitePixel.dispose();
            whitePixel = null;
        }
    }
}
