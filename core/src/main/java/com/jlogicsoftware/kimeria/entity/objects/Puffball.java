package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * Spore puffball mushroom (D46): a rhythmic trap. Every {@code period} seconds the cap swells for the
 * shared warning ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the assist), then releases a
 * spore cloud that is lethal on contact for {@code cloudSeconds}, then the mushroom is calm again.
 * <p>
 * One cycle, measured from its start: calm, then swell, then cloud (the cycle ends with the cloud).
 * The clock is {@code (elapsed + phase) mod period}; {@code elapsed} only advances in {@link #update},
 * so pausing freezes it and a room retry (which rebuilds the object) restarts at the authored phase.
 */
public class Puffball extends Entity implements LethalHazard {
    public static final float DEFAULT_PERIOD_SECONDS = 4f;
    public static final float DEFAULT_CLOUD_SECONDS = 1f;
    /** The hero must always get at least this long of calm (no swell, no cloud) per cycle, with the assist on. */
    public static final float MIN_CALM_SECONDS = 0.8f;
    /** Spore cloud sprite size; the cloud is centred on the mushroom and sits on its base line. */
    public static final float CLOUD_SIZE = 24f;
    /** The lethal volume is the cloud sprite shrunk by this much per side, so grazing the fringe is safe. */
    public static final float CLOUD_INSET = 2f;
    private static final float CLOUD_FRAME_SECONDS = 0.25f;
    private static final int CAP_FRAMES = 4, CLOUD_FRAMES = 3, CAP_CALM = 0, CAP_SWELL_FIRST = 1, CAP_SWELL_SECOND = 2, CAP_BURST = 3;

    public enum Phase {CALM, SWELL, CLOUD}

    private final TrapWarnings warnings;
    private final float period, phase, cloudSeconds;
    private float elapsed;
    private final TextureRegion[] cap = new TextureRegion[CAP_FRAMES], cloud = new TextureRegion[CLOUD_FRAMES];

    public Puffball(Assets assets, float x, float y, float w, float h, TrapWarnings warnings,
                    float period, float phase, float cloudSeconds) {
        super(x, y, w, h);
        this.warnings = warnings;
        this.period = period;
        this.phase = phase;
        this.cloudSeconds = cloudSeconds;
        for (int i = 0; i < cap.length; i++) cap[i] = SpriteSheets.TRAP_PUFFBALL.frame(assets, i, 0);
        for (int i = 0; i < cloud.length; i++) cloud[i] = SpriteSheets.TRAP_SPORES.frame(assets, i, 0);
    }

    /** Reads and checks the map properties strictly; the message is prefixed with {@code label}. */
    public static Puffball fromMap(Assets assets, TiledObject o, TrapWarnings warnings, String label) {
        float period, cloud, phase;
        try {
            period = RhythmProps.number(o, "period", DEFAULT_PERIOD_SECONDS);
            cloud = RhythmProps.number(o, "cloud", DEFAULT_CLOUD_SECONDS);
            phase = RhythmProps.number(o, "phase", 0f);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Puffball " + label + ": " + e.getMessage());
        }
        String problem = validate(period, cloud, phase);
        if (problem != null) throw new IllegalArgumentException("Puffball " + label + ": " + problem);
        return new Puffball(assets, o.x, o.y, o.width, o.height, warnings, period, phase, cloud);
    }

    /** The authoring rule: with the longest assisted swell, the cycle still leaves {@link #MIN_CALM_SECONDS} of calm. */
    public static String validate(float period, float cloudSeconds, float phase) {
        if (!Float.isFinite(period) || !Float.isFinite(cloudSeconds) || !Float.isFinite(phase))
            return "period, cloud and phase must be finite numbers";
        float swell = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        if (cloudSeconds <= 0f) return "cloud must be positive";
        if (period < cloudSeconds + swell + MIN_CALM_SECONDS)
            return "period " + period + " is shorter than cloud " + cloudSeconds + " + assisted swell " + swell
                + " + minimum calm " + MIN_CALM_SECONDS;
        return null;
    }

    /** The swell length now: the shared warning, so the assist lengthens the tell (and shortens the calm). */
    public float swellSeconds() {
        return warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
    }

    public float period() { return period; }
    public float cloudSeconds() { return cloudSeconds; }

    /** Position in the cycle, in [0, period). */
    public float cycleTime() {
        float c = (elapsed + phase) % period;
        return c < 0f ? c + period : c;
    }

    public Phase currentPhase() { return phaseAt(cycleTime()); }

    public Phase phaseAt(float cycleTime) {
        if (cycleTime >= period - cloudSeconds) return Phase.CLOUD;
        if (cycleTime >= period - cloudSeconds - swellSeconds()) return Phase.SWELL;
        return Phase.CALM;
    }

    /** Seconds the cap has been swelling in the current swell, or 0 outside it. */
    public float swellElapsed() {
        return currentPhase() == Phase.SWELL ? cycleTime() - (period - cloudSeconds - swellSeconds()) : 0f;
    }

    /** The lethal volume while a cloud is out; the full cloud sprite rectangle minus the inset. */
    public Rectangle cloudBounds() {
        return new Rectangle(centerX() - CLOUD_SIZE / 2f + CLOUD_INSET, position.y + size.y - CLOUD_SIZE + CLOUD_INSET,
            CLOUD_SIZE - 2 * CLOUD_INSET, CLOUD_SIZE - 2 * CLOUD_INSET);
    }

    @Override
    public boolean lethalTo(Rectangle heroBody) {
        return currentPhase() == Phase.CLOUD && cloudBounds().overlaps(heroBody);
    }

    @Override
    public boolean everLethalIn(Rectangle area) {
        return cloudBounds().overlaps(area);
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
    }

    @Override
    public void render(Batch batch) {
        Phase phase = currentPhase();
        int frame = switch (phase) {
            case CALM -> CAP_CALM;
            case SWELL -> swellElapsed() < swellSeconds() / 2f ? CAP_SWELL_FIRST : CAP_SWELL_SECOND;
            case CLOUD -> CAP_BURST; // the collapsed cap after the burst
        };
        batch.draw(cap[frame], position.x, position.y, size.x, size.y);
        if (phase == Phase.CLOUD) {
            float into = cycleTime() - (period - cloudSeconds);
            int cloudFrame = (int) (into / CLOUD_FRAME_SECONDS) % cloud.length;
            batch.draw(cloud[cloudFrame], centerX() - CLOUD_SIZE / 2f, position.y + size.y - CLOUD_SIZE, CLOUD_SIZE, CLOUD_SIZE);
        }
    }
}
