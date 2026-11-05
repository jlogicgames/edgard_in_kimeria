package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Falling stone trap (D45), reusable across worlds through a {@link Skin}. The stone is NOT the hero's
 * throwable {@code Carryable} stone: it cannot be picked up, thrown, pushed or stood on and no tool of the
 * hero touches it. While the hero is in its zone the stone is hidden above and a growing ground shadow shows
 * where it will land; then it appears and falls; on impact it shows the cracked frame and stays as harmless
 * scenery until the room is rebuilt.
 */
public class FallingStone extends FallingTrap {
    /** Stone and shadow art for one world; add an entry to {@link #SKINS} for a new world. */
    public record Skin(SpriteSheets.Sheet stone, SpriteSheets.Sheet shadow) { }

    public static final String DEFAULT_SKIN = "forest";
    // tools/check_tiled_links.py reads the skin names from the string keys of this map: keep them literals.
    private static final java.util.Map<String, Skin> SKINS = java.util.Map.of(
        "forest", new Skin(SpriteSheets.TRAP_STONE, SpriteSheets.TRAP_SHADOW));

    /** Names accepted by the {@code skin} property. */
    public static java.util.Set<String> skinNames() { return SKINS.keySet(); }
    /** The shadow starts at this fraction of its full width and grows linearly to full by the end of the warning. */
    public static final float SHADOW_START_SCALE = 0.35f;
    private static final int SHADOW_FRAMES = 3;
    private static final int LAST_SHADOW_FRAME = SHADOW_FRAMES - 1;

    /** Resolves a map {@code skin} property; an unknown name fails loudly. */
    public static Skin skin(String name) {
        Skin skin = SKINS.get(name.toLowerCase(Locale.ROOT));
        if (skin == null) throw new IllegalArgumentException("Unknown falling stone skin '" + name + "', known: " + SKINS.keySet());
        return skin;
    }

    private final Assets assets;
    private final Skin skin;

    public FallingStone(Assets assets, Skin skin, Rectangle body, Rectangle zone, float landingBottom,
                        TrapWarnings warnings, Supplier<Rectangle> hero, BooleanSupplier heroGrounded, Runnable kill) {
        super(body, zone, landingBottom, warnings, hero, heroGrounded, kill);
        this.assets = assets;
        this.skin = skin;
    }

    /** True while the ground shadow is drawn: through the warning and the fall, until the stone lands. */
    public boolean shadowVisible() { return state() == State.WARNING || state() == State.FALLING; }

    /** Shadow width as a fraction of the sheet's frame width: grows through the warning, full while falling. */
    public float shadowScale() {
        return SHADOW_START_SCALE + (1f - SHADOW_START_SCALE) * warningProgress();
    }

    /** Shadow frame: 0 small, 1 medium, 2 large by thirds of the warning. */
    public int shadowFrame() { return Math.min(LAST_SHADOW_FRAME, (int) (warningProgress() * SHADOW_FRAMES)); }

    /** The stone stays hidden above the screen of the room while only the shadow warns. */
    public boolean stoneVisible() { return state() == State.FALLING || state() == State.SPENT; }

    /** 0 stone, 1 cracked (impact). */
    public int stoneFrame() { return state() == State.SPENT ? 1 : 0; }

    @Override
    public void render(Batch batch) {
        if (shadowVisible()) {
            SpriteSheets.Sheet sheet = skin.shadow();
            TextureRegion shadow = sheet.frame(assets, shadowFrame(), 0);
            float w = sheet.width() * shadowScale(), h = sheet.height();
            batch.draw(shadow, centerX() - w / 2f, landingBottom() - h, w, h);
        }
        if (stoneVisible()) {
            batch.draw(skin.stone().frame(assets, stoneFrame(), 0), position.x, position.y, size.x, size.y);
        }
    }
}
