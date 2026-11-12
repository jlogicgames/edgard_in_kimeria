package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * Scenery vine (#239 step 5): hangs from its rectangle's top edge (the ceiling) and stays still until a falling stone
 * lands within {@link #REACH} px; then it sways about its top centre with a decaying swing. Tiled type
 * {@code HangingVine}, no properties. Not the lethal {@link ThornVine}: it has no hitbox, no collision, is not saved and
 * nothing in gameplay reads its angle. There is no idle sway.
 * <p>
 * Y grows downward: {@code position.y} is the top (anchor) edge and the vine hangs to {@code position.y + size.y}.
 * The swing is {@code A * exp(-t / TAU) * sin(2 pi FREQUENCY t)}, positive to the right (clockwise in this Y-down world),
 * started away from the stone, and advances only in {@link #update}, so pause freezes it.
 */
public final class HangingVine extends Entity {
    /** A stone landing farther than this from the vine's anchor (px, horizontally) does not move it. */
    public static final float REACH = 160f;
    /** Strength of a falling stone's impact (1 = the peak swing right under it). */
    public static final float STONE_STRENGTH = 1f;
    public static final float PEAK_DEGREES = 14f;
    public static final float FREQUENCY_HZ = 1.6f;
    /** Time constant of the decay (s); the sway stops after {@link #DURATION} s. */
    public static final float TAU = 0.5f;
    public static final float DURATION = 1.6f;

    private final TextureRegion hang;
    private float amplitude, direction = 1f, elapsed;
    private boolean swaying;

    public HangingVine(Assets assets, float x, float y, float w, float h) {
        super(x, y, w, h);
        hang = SpriteSheets.DECOR_VINE.frame(assets, 0, 0);
    }

    public static HangingVine fromMap(Assets assets, TiledObject o, String label) {
        if (!(o.width > 0 && o.height > 0)) throw new IllegalArgumentException("HangingVine " + label + ": needs a positive size");
        return new HangingVine(assets, o.x, o.y, o.width, o.height);
    }

    public float anchorX() { return position.x + size.x / 2f; }
    public float anchorY() { return position.y; }
    public boolean swaying() { return swaying; }

    /** The swing now, in radians from the vertical; 0 at rest. */
    public float angle() {
        if (!swaying) return 0f;
        return direction * amplitude * (float) (Math.exp(-elapsed / TAU) * Math.sin(2.0 * Math.PI * FREQUENCY_HZ * elapsed));
    }

    /** A ground shake at world {@code x}: sways when within {@link #REACH}, the stronger of a running and a new swing wins. */
    public void shake(float x, float strength) {
        float distance = Math.abs(anchorX() - x);
        if (distance >= REACH || strength <= 0f) return;
        float peak = (float) Math.toRadians(PEAK_DEGREES) * strength * (1f - distance / REACH);
        float running = swaying ? amplitude * (float) Math.exp(-elapsed / TAU) : 0f;
        if (peak <= running) return;
        amplitude = peak;
        direction = anchorX() >= x ? 1f : -1f;
        elapsed = 0f;
        swaying = true;
    }

    @Override
    public void update(float dt) {
        if (!swaying) return;
        elapsed += dt;
        if (elapsed >= DURATION) { swaying = false; elapsed = 0f; }
    }

    @Override
    public void render(Batch batch) {
        // Pivot at the top centre; the same rotation convention as ThornVine.render (clockwise for a rightward swing).
        batch.draw(hang, position.x, position.y, size.x / 2f, 0f, size.x, size.y, 1f, 1f, -(float) Math.toDegrees(angle()));
    }
}
