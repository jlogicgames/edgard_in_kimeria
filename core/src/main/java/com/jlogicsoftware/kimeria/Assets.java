package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.util.HashMap;
import java.util.Map;

/**
 * Central texture/sound cache. Every texture is loaded once and every
 * {@link TextureRegion} handed out is pre-flipped vertically, because the
 * whole game renders through a Y-down camera (see {@link KimeriaGame}) to
 * keep world-space math identical to the original Flame/Bevy sources, where
 * (0,0) is the top-left corner and Y grows downward.
 */
public class Assets implements Disposable {
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<String, BitmapFont> fonts = new HashMap<>();

    public Texture texture(String path) {
        return textures.computeIfAbsent(path, p -> {
            Texture t = new Texture(Gdx.files.internal(p));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return t;
        });
    }

    public Sound sound(String path) {
        return sounds.computeIfAbsent(path, p -> Gdx.audio.newSound(Gdx.files.internal(p)));
    }

    /** Entire image, using its current dimensions and the shared Y-down orientation. */
    public TextureRegion region(String path) {
        Texture texture = texture(path);
        return region(path, 0, 0, texture.getWidth(), texture.getHeight());
    }

    /** Full image with explicit filtering; filtering is shared by every region of this texture. */
    public TextureRegion region(String path, Texture.TextureFilter filter) {
        TextureRegion region = region(path);
        region.getTexture().setFilter(filter, filter);
        return region;
    }

    /** A single frame region, flipped for the Y-down camera. */
    public TextureRegion region(String path, float x, float y, float w, float h) {
        TextureRegion region = new TextureRegion(texture(path), (int) x, (int) y, (int) w, (int) h);
        region.flip(false, true);
        return region;
    }

    /**
     * Builds a frame-sequenced animation exactly like Flame's
     * {@code SpriteAnimationData.sequenced}: frames run left-to-right from
     * {@code (originX, originY)} and wrap to a new row (advancing by
     * frameHeight) after {@code amountPerRow} frames.
     */
    public Animation<TextureRegion> animation(String path, int amount, float stepTime,
                                               float frameW, float frameH,
                                               float originX, float originY, int amountPerRow) {
        TextureRegion[] frames = new TextureRegion[amount];
        for (int i = 0; i < amount; i++) {
            int col = i % amountPerRow;
            int row = i / amountPerRow;
            frames[i] = region(path, originX + col * frameW, originY + row * frameH, frameW, frameH);
        }
        return new Animation<>(stepTime, frames);
    }

    public Animation<TextureRegion> animation(String path, int amount, float stepTime,
                                               float frameW, float frameH,
                                               float originX, float originY) {
        int amountPerRow = amount > 4 ? 4 : amount;
        return animation(path, amount, stepTime, frameW, frameH, originX, originY, amountPerRow);
    }

    /**
     * Loads a font pre-baked to bitmap files by {@code FontBaker} (see that
     * class for why: the TeaVM/web build can't run FreeTypeFontGenerator at
     * all, so every platform, including desktop, uses the same baked
     * files). {@code sizePx} must be one of the sizes actually baked --
     * see the call sites in {@code Overlay}/{@code Hud} and keep
     * {@code FontBaker.SIZES} in sync with them.
     */
    public BitmapFont font(int sizePx) {
        return fonts.computeIfAbsent("QuestSquare@" + sizePx, key -> {
            String path = "fonts/generated/QuestSquare-" + sizePx + ".fnt";
            if (!Gdx.files.internal(path).exists()) {
                throw new GdxRuntimeException("No baked font at " + path
                    + " -- add " + sizePx + " to FontBaker.SIZES and run `./gradlew lwjgl3:bakeFonts`.");
            }
            BitmapFont font = new BitmapFont(Gdx.files.internal(path));
            for (TextureRegion page : font.getRegions()) {
                page.getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            }
            // Deliberately NOT flipped like region(): a BitmapFont's glyph
            // quads are built from Glyph.u/v/u2/v2 baked in the .fnt file,
            // not from the page TextureRegion object, so flipping that
            // region has no effect on what's actually drawn -- flipping a
            // font for a Y-down camera needs a real UV rewrite, not this
            // trick. Text is instead drawn through a separate Y-up camera
            // pass (see Overlay/Hud), where an unmodified font is correct
            // by construction.
            return font;
        });
    }

    @Override
    public void dispose() {
        textures.values().forEach(Texture::dispose);
        sounds.values().forEach(Sound::dispose);
        fonts.values().forEach(BitmapFont::dispose);
        textures.clear();
        sounds.clear();
        fonts.clear();
    }
}
