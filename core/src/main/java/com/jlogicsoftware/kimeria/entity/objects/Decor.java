package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.Locale;

/**
 * Cosmetic scenery (#239 step 4): a hand-placed sprite that plays its brush-past frames once when the hero walks
 * through it. No collision, not saved, never read by gameplay; {@code Level} draws it behind everything but the
 * background. Tiled type {@code Decor}, properties {@code kind} (see {@link #KINDS}) and optional boolean {@code mirror}.
 * <p>
 * Frame 0 is the rest pose; frames 1.. play at {@link #FPS} and the object returns to frame 0. It restarts only when
 * finished, so it cannot flicker. Timing depends only on hero position and {@code dt}.
 * <p>
 * Y grows downward: {@code position.y + size.y} is the floor line. The sprite is drawn at its native size, centred on the
 * rectangle and standing on its bottom edge.
 */
public final class Decor extends Entity {
    /** Frames per strip: idle, brush1, brush2, settle (tools/sprite_layouts.py DECOR_SHEETS). */
    public static final int FRAMES = 4;
    public static final float FPS = 10f;
    /** The brush is noticed within this many pixels of the rectangle on each side. */
    public static final float REACH = 6f;
    /** Horizontal hero speed (px/s) below which the hero counts as standing. */
    public static final float MIN_SPEED = 1f;
    /** One entry per kind: a new kind is one sheet and one table entry. The names are read by tools/check_tiled_links.py. */
    private static final java.util.Map<String, SpriteSheets.Sheet> KINDS = java.util.Map.of(
        "bush", SpriteSheets.DECOR_BUSH);

    public static java.util.Set<String> kindNames() { return KINDS.keySet(); }

    private final SpriteSheets.Sheet sheet;
    private final TextureRegion[] frames;
    private final boolean mirror;
    private boolean playing, leanLeft;
    private float elapsed;

    public Decor(Assets assets, String kind, float x, float y, float w, float h, boolean mirror) {
        super(x, y, w, h);
        sheet = KINDS.get(kind.toLowerCase(Locale.ROOT));
        if (sheet == null) throw new IllegalArgumentException("Unknown decor kind '" + kind + "', known: " + KINDS.keySet());
        frames = new TextureRegion[FRAMES];
        for (int i = 0; i < frames.length; i++) frames[i] = sheet.frame(assets, i, 0);
        this.mirror = mirror;
    }

    public static Decor fromMap(Assets assets, TiledObject o, String label) {
        if (!(o.width > 0 && o.height > 0)) throw new IllegalArgumentException("Decor " + label + ": needs a positive size");
        try {
            return new Decor(assets, o.prop("kind", ""), o.x, o.y, o.width, o.height, o.propBool("mirror", false));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Decor " + label + ": " + e.getMessage());
        }
    }

    /**
     * Starts the brush when the hero's body box overlaps the rectangle widened by {@link #REACH} and the hero moves
     * horizontally ({@code heroVelocityX}, px/s); the sprite leans the way the hero is heading. Does nothing while playing.
     */
    public void notice(Rectangle heroBody, float heroVelocityX) {
        if (playing || Math.abs(heroVelocityX) < MIN_SPEED) return;
        if (heroBody.x >= position.x + size.x + REACH || heroBody.x + heroBody.width <= position.x - REACH
            || heroBody.y >= position.y + size.y + REACH || heroBody.y + heroBody.height <= position.y - REACH) return;
        playing = true;
        elapsed = 0f;
        leanLeft = heroVelocityX < 0;
    }

    public boolean playing() { return playing; }
    /** The frame drawn now: 0 at rest, then 1..frames-1 while playing. */
    public int frameIndex() { return playing ? Math.min(1 + (int) (elapsed * FPS), frames.length - 1) : 0; }
    public int frameCount() { return frames.length; }

    @Override
    public void update(float dt) {
        if (!playing) return;
        elapsed += dt;
        if (elapsed * FPS >= frames.length - 1) { playing = false; elapsed = 0f; }
    }

    @Override
    public void render(Batch batch) {
        float w = sheet.width(), h = sheet.height();
        float x = position.x + (size.x - w) / 2f, y = position.y + size.y - h;
        // The strip is drawn leaning right; mirroring turns it to lean left (heading) and `mirror` flips the whole bush.
        if ((playing && leanLeft) != mirror) batch.draw(frames[frameIndex()], x + w, y, -w, h);
        else batch.draw(frames[frameIndex()], x, y, w, h);
    }
}
