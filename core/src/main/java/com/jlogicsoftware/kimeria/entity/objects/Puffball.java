package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.PuffballFx;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * Spore puffball (D46, redrawn in #558): a rhythmic trap. Every {@code period} seconds the sac swells for the
 * shared warning ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the assist; spores leak from its chimney in the second half),
 * then releases a spore cloud that is lethal on contact for {@code cloudSeconds}, then the sac is calm again. The cloud is particles
 * ({@link PuffballFx}) that fill exactly {@link #cloudBounds()}.
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
    /** The cloud's nominal square; it is centred on the sac and sits on its base line. */
    public static final float CLOUD_SIZE = 24f;
    /** The lethal volume (and the area the spores fill) is the square shrunk by this much per side, so grazing the fringe is safe. */
    public static final float CLOUD_INSET = 2f;
    /** Seconds a calm breath frame lasts, how long the empty sac shows after a release, and how long the torn one shows at the start of the cloud. */
    public static final float BREATH_SECONDS = 0.6f, REFILL_SECONDS = 0.4f, BURST_SECONDS = 0.25f;
    /** The chimney mouth's distance under the object's top edge (world Y grows downward). */
    private static final float MOUTH_FROM_TOP = 3f;
    private static final int BREATH_FRAMES = 2;
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final int SAC_FRAMES = 6, SAC_BREATH_IN = 0, SAC_BREATH_OUT = 1, SAC_SWELL_FIRST = 2, SAC_SWELL_SECOND = 3, SAC_BURST = 4, SAC_SPENT = 5;

    public enum Phase {CALM, SWELL, CLOUD}

    private final TrapWarnings warnings;
    private final float period, phase, cloudSeconds;
    private float elapsed;
    private boolean wasCloud;
    private Runnable onRelease = () -> { };
    private final TextureRegion[] sac = new TextureRegion[SAC_FRAMES];
    private final PuffballFx fx;
    /** The lethal volume, built once so the per-frame hero test allocates nothing. */
    private final Rectangle lethalBox;

    public Puffball(Assets assets, float x, float y, float w, float h, TrapWarnings warnings,
                    float period, float phase, float cloudSeconds) {
        super(x, y, w, h);
        this.warnings = warnings;
        this.period = period;
        this.phase = phase;
        this.cloudSeconds = cloudSeconds;
        for (int i = 0; i < sac.length; i++) sac[i] = SpriteSheets.TRAP_PUFFBALL.frame(assets, i, 0);
        lethalBox = new Rectangle(centerX() - CLOUD_SIZE / 2f + CLOUD_INSET, position.y + size.y - CLOUD_SIZE + CLOUD_INSET,
            CLOUD_SIZE - 2 * CLOUD_INSET, CLOUD_SIZE - 2 * CLOUD_INSET);
        fx = new PuffballFx(lethalBox.x, lethalBox.y, lethalBox.width, lethalBox.height, centerX(), y + MOUTH_FROM_TOP,
            (Float.floatToIntBits(x) * SEED_MIX) ^ Float.floatToIntBits(y));
        wasCloud = currentPhase() == Phase.CLOUD; // a room that opens inside a cloud is not a release (no sound), but its cloud is already out
        if (wasCloud) fx.release();
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

    /** Seconds the sac has been swelling in the current swell, or 0 outside it. */
    public float swellElapsed() {
        return currentPhase() == Phase.SWELL ? cycleTime() - (period - cloudSeconds - swellSeconds()) : 0f;
    }

    /** The lethal volume while a cloud is out (the spores fill exactly it): the nominal square minus the inset; a copy. */
    public Rectangle cloudBounds() { return new Rectangle(lethalBox); }

    @Override
    public boolean lethalTo(Rectangle heroBody) {
        return currentPhase() == Phase.CLOUD && lethalBox.overlaps(heroBody);
    }

    @Override
    public boolean everLethalIn(Rectangle area) {
        return lethalBox.overlaps(area);
    }

    /** The spores, for tests. */
    public PuffballFx fx() { return fx; }

    /** Called once at the start of each cloud (the release); the level plays the puffball burst from it (#445, #558). */
    public void onRelease(Runnable onRelease) { this.onRelease = onRelease; }

    /** True when the sac or the cloud overlaps {@code view}; allocation free (the release is heard only while it is on screen). */
    public boolean inView(Rectangle view) {
        float cloudX = centerX() - CLOUD_SIZE / 2f, cloudY = position.y + size.y - CLOUD_SIZE;
        boolean sacInView = position.x < view.x + view.width && view.x < position.x + size.x
            && position.y < view.y + view.height && view.y < position.y + size.y;
        boolean puff = cloudX < view.x + view.width && view.x < cloudX + CLOUD_SIZE
            && cloudY < view.y + view.height && view.y < cloudY + CLOUD_SIZE;
        return sacInView || puff;
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
        Phase now = currentPhase();
        boolean cloudNow = now == Phase.CLOUD;
        if (cloudNow && !wasCloud) {
            onRelease.run();
            fx.release();
        }
        wasCloud = cloudNow;
        fx.update(dt, now == Phase.SWELL && swellElapsed() >= swellSeconds() / 2f, cloudNow);
    }

    /** The sac's frame now: spent for the first {@link #REFILL_SECONDS} of the calm and after the burst, then breathing, then the swell. */
    private int frameNow() {
        float cycle = cycleTime();
        switch (phaseAt(cycle)) {
            case SWELL: return swellElapsed() < swellSeconds() / 2f ? SAC_SWELL_FIRST : SAC_SWELL_SECOND;
            case CLOUD: return cycle - (period - cloudSeconds) < BURST_SECONDS ? SAC_BURST : SAC_SPENT;
            default:
                if (cycle < REFILL_SECONDS) return SAC_SPENT;
                return (int) ((cycle - REFILL_SECONDS) / BREATH_SECONDS) % BREATH_FRAMES == 0 ? SAC_BREATH_IN : SAC_BREATH_OUT;
        }
    }

    @Override
    public void render(Batch batch) {
        batch.draw(sac[frameNow()], position.x, position.y, size.x, size.y);
        fx.render(batch);
    }
}
