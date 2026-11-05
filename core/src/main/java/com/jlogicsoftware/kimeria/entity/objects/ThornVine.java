package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * Swinging thorn vine (D46): a pendulum hung from the top centre of its object rectangle. The angle from
 * the vertical is {@code amplitude * sin(2 pi (elapsed + phase) / period)}: continuous and fully
 * deterministic, so the motion itself is the warning (D7 needs no extra tell). Only the thorny lower half
 * of the vine is lethal; the upper stem is harmless.
 * <p>
 * {@code elapsed} advances only in {@link #update}: pause freezes it, a room retry restarts at the authored phase.
 */
public class ThornVine extends Entity implements LethalHazard {
    public static final float DEFAULT_PERIOD_SECONDS = 4.5f;
    public static final float DEFAULT_AMPLITUDE_DEGREES = 50f;
    public static final float MIN_PERIOD_SECONDS = 3.5f;
    public static final float MIN_AMPLITUDE_DEGREES = 35f, MAX_AMPLITUDE_DEGREES = 60f;
    /** Lethal part starts this fraction of the way down the vine (the thorny tip, lower half). */
    public static final float LETHAL_FROM = 0.5f;
    /** Half the thickness of the lethal part, in pixels (the thorn ball is about 10 px wide). */
    public static final float LETHAL_HALF_WIDTH = 4f;

    private static final int SLAB_SIDES = 4;
    private static final float SWEEP_STEP = (float) Math.toRadians(0.5);
    private final float period, phase, amplitudeRadians;
    private float elapsed;
    private final TextureRegion hang;

    public ThornVine(Assets assets, float x, float y, float w, float h, float period, float phase, float amplitudeDegrees) {
        super(x, y, w, h);
        this.period = period;
        this.phase = phase;
        this.amplitudeRadians = (float) Math.toRadians(amplitudeDegrees);
        hang = SpriteSheets.TRAP_VINE.frame(assets, 0, 0);
    }

    /** Reads and checks the map properties strictly; the message is prefixed with {@code label}. */
    public static ThornVine fromMap(Assets assets, TiledObject o, String label) {
        float period, amplitude, phase;
        try {
            period = RhythmProps.number(o, "period", DEFAULT_PERIOD_SECONDS);
            amplitude = RhythmProps.number(o, "amplitude", DEFAULT_AMPLITUDE_DEGREES);
            phase = RhythmProps.number(o, "phase", 0f);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ThornVine " + label + ": " + e.getMessage());
        }
        String problem = validate(period, amplitude, phase);
        if (problem != null) throw new IllegalArgumentException("ThornVine " + label + ": " + problem);
        return new ThornVine(assets, o.x, o.y, o.width, o.height, period, phase, amplitude);
    }

    public static String validate(float period, float amplitudeDegrees, float phase) {
        if (!Float.isFinite(period) || !Float.isFinite(amplitudeDegrees) || !Float.isFinite(phase))
            return "period, amplitude and phase must be finite numbers";
        if (period < MIN_PERIOD_SECONDS) return "period " + period + " is below the minimum " + MIN_PERIOD_SECONDS;
        if (amplitudeDegrees < MIN_AMPLITUDE_DEGREES || amplitudeDegrees > MAX_AMPLITUDE_DEGREES)
            return "amplitude " + amplitudeDegrees + " is outside " + MIN_AMPLITUDE_DEGREES + ".." + MAX_AMPLITUDE_DEGREES;
        return null;
    }

    public float period() { return period; }
    public float anchorX() { return position.x + size.x / 2f; }
    public float anchorY() { return position.y; }
    public float length() { return size.y; }
    public float elapsed() { return elapsed; }

    /** Angle from the vertical in radians at this time since the room began; positive swings right. */
    public float angleAt(float seconds) {
        return amplitudeRadians * (float) Math.sin(2.0 * Math.PI * (seconds + phase) / period);
    }

    public float angle() { return angleAt(elapsed); }

    /** True when the lethal part touches {@code body} with the vine at {@code angle}. */
    public boolean lethalAt(float angle, Rectangle body) {
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        float x0 = anchorX() + length() * LETHAL_FROM * sin, y0 = anchorY() + length() * LETHAL_FROM * cos;
        float x1 = anchorX() + length() * sin, y1 = anchorY() + length() * cos;
        float r = LETHAL_HALF_WIDTH;
        return segmentHitsBox(x0, y0, x1, y1, body.x - r, body.y - r, body.x + body.width + r, body.y + body.height + r);
    }

    /** Slab test of a segment against an axis-aligned box. */
    private static boolean segmentHitsBox(float x0, float y0, float x1, float y1,
                                          float minX, float minY, float maxX, float maxY) {
        float t0 = 0f, t1 = 1f, dx = x1 - x0, dy = y1 - y0;
        float[] p = {-dx, dx, -dy, dy}, q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
        for (int i = 0; i < SLAB_SIDES; i++) {
            if (p[i] == 0f) {
                if (q[i] < 0f) return false;
            } else {
                float t = q[i] / p[i];
                if (p[i] < 0f) { if (t > t1) return false; if (t > t0) t0 = t; }
                else { if (t < t0) return false; if (t < t1) t1 = t; }
            }
        }
        return true;
    }

    @Override
    public boolean lethalTo(Rectangle heroBody) {
        return lethalAt(angle(), heroBody);
    }

    /** Sweeps the whole swing (0.5 degree steps, both extremes) and asks whether the lethal part ever touches {@code area}. */
    @Override
    public boolean everLethalIn(Rectangle area) {
        for (float a = -amplitudeRadians; a <= amplitudeRadians + SWEEP_STEP; a += SWEEP_STEP)
            if (lethalAt(Math.min(a, amplitudeRadians), area)) return true;
        return false;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
    }

    @Override
    public void render(Batch batch) {
        // Pivot at the top centre of the object; rotation is clockwise for a rightward swing in this Y-down world.
        float degrees = (float) Math.toDegrees(angle());
        batch.draw(hang, position.x, position.y, size.x / 2f, 0f, size.x, size.y, 1f, 1f, -degrees);
    }
}
