package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import java.util.List;
import java.util.Set;

/**
 * Ember fissure (#366, D91): a rhythmic trap. A crust of ash on the floor line with a glowing slit; on the level clock
 * {@code (elapsed + phase) mod period} it is calm (the slit pulses), then shows the shared warning
 * ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the assist, taken from the calm: the slit turns orange, then
 * yellow, sparks rise), then jets fire for the last {@code jet} seconds of the period. The jet is a column
 * {@link #JET_WIDTH} px wide centred on the fissure, from the floor line up to its top: the top rises to the full
 * {@code height} in {@link #RISE_SECONDS}, holds, and sinks back to the floor in {@link #SINK_SECONDS}. The drawn column is
 * the lethal part. Embers pass through one-way platforms; a solid block above cuts the column (see {@link #ceilingAbove}).
 * World Y grows downward: the column's top has the smaller Y.
 * <p>
 * Every jet is preceded by its full warning: a cycle whose warning would have begun before the room started stays calm,
 * so the hero never arrives in front of an unannounced jet. {@code elapsed} only advances in {@link #update}, so pause
 * freezes it; a room retry rebuilds the object at the authored phase.
 */
public class EmberFissure extends Entity implements LethalHazard {
    public static final float DEFAULT_PERIOD_SECONDS = 3.5f, DEFAULT_JET_SECONDS = 1f, DEFAULT_HEIGHT = 96f;
    public static final float MIN_JET_SECONDS = 0.6f, MAX_JET_SECONDS = 1.5f, MIN_HEIGHT = 48f, MAX_HEIGHT = 128f, HEIGHT_STEP = 16f;
    /** With the assisted warning, every cycle keeps at least this long of calm: the pass window. */
    public static final float MIN_CALM_SECONDS = 0.8f;
    public static final float RISE_SECONDS = 0.1f, SINK_SECONDS = 0.2f;
    /** The sprite's frame; the object's rectangle is the frame, its top {@link #FLOOR_OFFSET} px above the floor line. */
    public static final float WIDTH = 16f, HEIGHT = 12f, FLOOR_OFFSET = 4f;
    /** Lethal and drawn width of the jet, centred on the fissure. */
    public static final float JET_WIDTH = 8f;
    /** Authored sizes and floor heights are compared with this slack (Tiled may write 11.9999). */
    public static final float SIZE_TOLERANCE = 0.5f;
    /** The column must still be this tall under a ceiling that cuts it. */
    public static final float MIN_COLUMN = 32f;
    public static final float PULSE_SECONDS = 0.5f, SPENT_SECONDS = 0.4f, JET_FRAME_SECONDS = 0.05f;
    // Sparks (particles, KIND_GLOW): rising from the slit in the warning, falling from the column as it sinks (D93).
    public static final float RISE_PERIOD = 0.08f, RISE_LIFE = 0.4f, RISE_MIN_PX = 8f, RISE_MAX_PX = 24f, RISE_SPREAD_PX = 3f;
    public static final int FALL_SPARKS = 7, SPARK_CAPACITY = 24;
    public static final float FALL_LIFE = 0.5f, FALL_DROP_PX = 24f, FALL_SPREAD_PX = 8f, SPARK_PX = 1f;
    /** Falling sparks start in the upper part of the column: from this fraction of its height up to its top. */
    public static final float FALL_FROM_FRACTION = 0.5f;
    /** Spark colours from the grid's y ffd23a, o f07a1a and h fff4b0, in turn (red, green, blue channels). */
    private static final float[] SPARK_R = {1f, 0.941f, 1f}, SPARK_G = {0.824f, 0.478f, 0.957f}, SPARK_B = {0.227f, 0.102f, 0.690f};
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final float TWO = 2f;
    private static final int REST1 = 0, REST2 = 1, WARN1 = 2, WARN2 = 3, VENT = 4, SPENT = 5, FRAMES = 6;
    private static final int JET_FRAMES = 2, JET_ROWS = 16, TIP_ROWS = 8, TIP_FIRST_FRAME = 2;
    /** The map properties a fissure reads; any other key fails the load (a misspelling must not fall back to a default). */
    public static final Set<String> PROPERTIES = Set.of("period", "phase", "jet", "height");

    public enum Phase {CALM, WARNING, JET}

    private final TrapWarnings warnings;
    private final float period, phase, jet, height, floor;
    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    /** One region per row of each column tile and tip: the column is drawn a row at a time, so its top clips exactly. */
    private final TextureRegion[][] jetRows = new TextureRegion[JET_FRAMES][JET_ROWS], tipRows = new TextureRegion[JET_FRAMES][TIP_ROWS];
    private final KimeriaParticleSystem sparks;
    private final Rectangle jetScratch = new Rectangle();
    protected float riseClock;
    private float elapsed, top;
    private boolean wasWarning, wasJet, wasSinking, jetOn;
    private int emitted;
    private Runnable onWarning = () -> { }, onJet = () -> { };

    /** @param height the column's full height, already cut by a solid above (see {@link #columnHeight}) */
    public EmberFissure(Assets assets, float x, float y, TrapWarnings warnings, float period, float phase, float jet, float height) {
        this(assets, x, y, warnings, period, phase, jet, height, SpriteSheets.TRAP_FISSURE, SpriteSheets.TRAP_EMBER_JET);
    }

    /**
     * The same clock, column and load rules with another look (D129): {@link LanceRoot} passes its own base and column sheets,
     * which keep this class's frame order (rest1, rest2, warn1, warn2, vent, spent; jet1, jet2, top1, top2), and overrides
     * the two spark hooks.
     */
    protected EmberFissure(Assets assets, float x, float y, TrapWarnings warnings, float period, float phase, float jet, float height,
                           SpriteSheets.Sheet base, SpriteSheets.Sheet column) {
        super(x, y, WIDTH, HEIGHT);
        this.warnings = warnings;
        this.period = period;
        this.phase = phase;
        this.jet = jet;
        this.height = height;
        this.floor = y + FLOOR_OFFSET;
        for (int i = 0; i < FRAMES; i++) frames[i] = base.frame(assets, i, 0);
        SpriteSheets.Sheet sheet = column;
        for (int f = 0; f < JET_FRAMES; f++) {
            for (int r = 0; r < JET_ROWS; r++) jetRows[f][r] = assets.region(sheet.path(), f * sheet.width(), r, sheet.width(), 1);
            for (int r = 0; r < TIP_ROWS; r++) tipRows[f][r] = assets.region(sheet.path(), (TIP_FIRST_FRAME + f) * sheet.width(), r, sheet.width(), 1);
        }
        sparks = new KimeriaParticleSystem(SPARK_CAPACITY, (Float.floatToIntBits(x) * SEED_MIX) ^ Float.floatToIntBits(y));
        top = floor;
    }

    /** Builds the object once the map's numbers are read and checked. */
    @FunctionalInterface
    public interface Builder<T extends EmberFissure> {
        T build(float period, float phase, float jet, float columnHeight);
    }

    /** Reads and checks the map properties strictly; {@code collisions} (the room's blocks) may cut the column ({@link #ceilingAbove}). */
    public static EmberFissure fromMap(Assets assets, TiledObject o, TrapWarnings warnings, Iterable<TiledObject> collisions, String label) {
        return fromMap("EmberFissure", o, collisions, label,
            (period, phase, jet, height) -> new EmberFissure(assets, o.x, o.y, warnings, period, phase, jet, height));
    }

    /** {@link #fromMap(Assets, TiledObject, TrapWarnings, Iterable, String)} for any look of the clocked column: {@code type} names it in errors. */
    public static <T extends EmberFissure> T fromMap(String type, TiledObject o, Iterable<TiledObject> collisions, String label, Builder<T> builder) {
        float period, phase, jet, height;
        try {
            for (String key : o.properties.keySet())
                if (!PROPERTIES.contains(key)) throw new IllegalArgumentException("unknown property '" + key + "' (a fissure reads " + new java.util.TreeSet<>(PROPERTIES) + ")");
            period = RhythmProps.number(o, "period", DEFAULT_PERIOD_SECONDS);
            phase = RhythmProps.number(o, "phase", 0f);
            jet = RhythmProps.number(o, "jet", DEFAULT_JET_SECONDS);
            height = RhythmProps.number(o, "height", DEFAULT_HEIGHT);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(type + " " + label + ": " + e.getMessage());
        }
        if (Math.abs(o.width - WIDTH) > SIZE_TOLERANCE || Math.abs(o.height - HEIGHT) > SIZE_TOLERANCE)
            throw new IllegalArgumentException(type + " " + label + ": must be " + (int) WIDTH + "x" + (int) HEIGHT + " (the sprite's frame)");
        String problem = validate(period, phase, jet, height);
        float ceiling = problem == null ? ceilingAbove(o, height, collisions) : Float.NaN;
        if (problem == null) problem = columnProblem(o, height, ceiling);
        if (problem != null) throw new IllegalArgumentException(type + " " + label + ": " + problem);
        return builder.build(period, phase, jet, columnHeight(o, height, ceiling));
    }

    /** The authoring rule: {@code period >= jet + assisted warning + MIN_CALM_SECONDS}; jet and height in their ranges. */
    public static String validate(float period, float phase, float jet, float height) {
        if (!Float.isFinite(period) || !Float.isFinite(phase) || !Float.isFinite(jet) || !Float.isFinite(height))
            return "period, phase, jet and height must be finite numbers";
        if (jet < MIN_JET_SECONDS || jet > MAX_JET_SECONDS) return "jet " + jet + " is outside " + MIN_JET_SECONDS + "-" + MAX_JET_SECONDS + " s";
        if (height < MIN_HEIGHT || height > MAX_HEIGHT || height % HEIGHT_STEP != 0f)
            return "height " + height + " must be a multiple of " + (int) HEIGHT_STEP + " from " + (int) MIN_HEIGHT + " to " + (int) MAX_HEIGHT;
        float warning = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        if (period < jet + warning + MIN_CALM_SECONDS)
            return "period " + period + " is shorter than jet " + jet + " + assisted warning " + warning + " + minimum calm " + MIN_CALM_SECONDS;
        return null;
    }

    /**
     * The bottom edge (world Y) of the lowest solid block above the fissure that the column would reach: any Collisions block
     * but a one-way {@code Platform} (embers pass through branches) and quicksand, overlapping the column horizontally,
     * entirely above the floor line and less than {@code height} above it; NaN when nothing cuts the column.
     */
    public static float ceilingAbove(TiledObject o, float height, Iterable<TiledObject> collisions) {
        float floorY = o.y + FLOOR_OFFSET, left = o.x + (WIDTH - JET_WIDTH) / TWO, best = Float.NaN;
        for (TiledObject c : collisions) {
            if ("Platform".equals(c.type) || "QuickSand".equals(c.type)) continue;
            if (c.x >= left + JET_WIDTH || c.x + c.width <= left) continue;
            float bottom = c.y + c.height;
            if (bottom > floorY - SIZE_TOLERANCE || bottom <= floorY - height) continue;
            if (Float.isNaN(best) || bottom > best) best = bottom;
        }
        return best;
    }

    /** The column's height under {@code ceiling} (NaN: uncut). */
    public static float columnHeight(TiledObject o, float height, float ceiling) {
        return Float.isNaN(ceiling) ? height : Math.min(height, o.y + FLOOR_OFFSET - ceiling);
    }

    private static String columnProblem(TiledObject o, float height, float ceiling) {
        float h = columnHeight(o, height, ceiling);
        return h < MIN_COLUMN ? "a solid block cuts its jet to " + h + " px, the minimum is " + MIN_COLUMN : null;
    }

    /**
     * The fissure stands on a floor: solid blocks (any Collisions block but quicksand and a one-way platform) whose tops lie
     * on its floor line within {@link #SIZE_TOLERANCE} cover all of its 16 px (split blocks merged); null when they do.
     */
    public static String floorProblem(TiledObject o, List<Rectangle> solids) {
        float floorY = o.y + FLOOR_OFFSET, covered = o.x;
        boolean progress = true;
        while (covered < o.x + WIDTH - SIZE_TOLERANCE && progress) {
            progress = false;
            for (Rectangle r : solids)
                if (Math.abs(r.y - floorY) <= SIZE_TOLERANCE && r.x <= covered + SIZE_TOLERANCE && r.x + r.width > covered + SIZE_TOLERANCE) {
                    covered = r.x + r.width;
                    progress = true;
                }
        }
        return covered >= o.x + WIDTH - SIZE_TOLERANCE ? null
            : "needs a solid floor whose top is its floor line (object top + " + (int) FLOOR_OFFSET + ") under all of its " + (int) WIDTH + " px";
    }

    /** A moving or switchable solid in the column would change the jet: the load fails with it named. */
    public static String movingSolidProblem(TiledObject o, Iterable<TiledObject> spawn, float height) {
        float floorY = o.y + FLOOR_OFFSET, left = o.x + (WIDTH - JET_WIDTH) / TWO;
        for (TiledObject m : spawn) {
            if (!("Actionable".equals(m.type) || "Escalator".equals(m.type) || "FallingPlatform".equals(m.type))) continue;
            if (m.x < left + JET_WIDTH && m.x + m.width > left && m.y < floorY && m.y + m.height > floorY - height)
                return "a moving or switchable " + m.type + " [" + m.id + "] stands in its jet";
        }
        return null;
    }

    public void onWarning(Runnable listener) { onWarning = listener; }
    public void onJet(Runnable listener) { onJet = listener; }

    public float period() { return period; }
    public float jetSeconds() { return jet; }
    public float phase() { return phase; }
    public float columnHeight() { return height; }
    public float floorY() { return floor; }
    public KimeriaParticleSystem sparks() { return sparks; }

    /** The warning length now: the shared warning, so the assist lengthens it (and shortens the calm). */
    public float warningSeconds() { return warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS); }

    /** Position in the cycle, in [0, period). */
    public float cycleTime() {
        float c = (elapsed + phase) % period;
        return c < 0f ? c + period : c;
    }

    /** The phase now; a cycle whose warning began before the room started stays calm (no unannounced jet). */
    public Phase currentPhase() {
        float c = cycleTime(), warningStart = period - jet - warningSeconds();
        if (c >= warningStart && elapsed - (c - warningStart) < 0f) return Phase.CALM;
        return phaseAt(c);
    }

    public Phase phaseAt(float cycleTime) {
        if (cycleTime >= period - jet) return Phase.JET;
        if (cycleTime >= period - jet - warningSeconds()) return Phase.WARNING;
        return Phase.CALM;
    }

    /** Seconds into the current warning, or 0 outside it. */
    public float warningElapsed() {
        return currentPhase() == Phase.WARNING ? cycleTime() - (period - jet - warningSeconds()) : 0f;
    }

    /** Seconds into the current jet, or 0 outside it. */
    public float jetElapsed() { return currentPhase() == Phase.JET ? cycleTime() - (period - jet) : 0f; }

    /** The column's top (world Y) {@code s} seconds into a jet: rises, holds, sinks back to the floor. */
    public float topAt(float s) {
        float f;
        if (s < 0f || s >= jet) f = 0f;
        else if (s < RISE_SECONDS) f = s / RISE_SECONDS;
        else if (s < jet - SINK_SECONDS) f = 1f;
        else f = 1f - (s - (jet - SINK_SECONDS)) / SINK_SECONDS;
        return floor - height * f;
    }

    /** The lethal column now (an empty rectangle when there is none), written into {@code out}. */
    public Rectangle jetInto(Rectangle out) {
        return jetOn ? out.set(centerX() - JET_WIDTH / TWO, top, JET_WIDTH, floor - top) : out.set(0, 0, 0, 0);
    }

    public boolean jetActive() { return jetOn; }

    @Override
    public boolean lethalTo(Rectangle heroBody) { return jetOn && jetInto(jetScratch).overlaps(heroBody); }

    /** Every place the jet can reach: the full column. */
    @Override
    public boolean everLethalIn(Rectangle area) { return columnInto(jetScratch).overlaps(area); }

    /** The column the jet can ever cover (for the load checks). */
    public Rectangle columnInto(Rectangle out) { return out.set(centerX() - JET_WIDTH / TWO, floor - height, JET_WIDTH, height); }

    /** Everything the fissure can draw: its frame and the full column above it. */
    public Rectangle extentInto(Rectangle out) { return out.set(position.x, floor - height, size.x, height + size.y - FLOOR_OFFSET); }

    @Override
    public void update(float dt) {
        elapsed += dt;
        Phase now = currentPhase();
        if (now == Phase.WARNING && !wasWarning) onWarning.run();
        if (now == Phase.JET && !wasJet) onJet.run();
        wasWarning = now == Phase.WARNING;
        wasJet = now == Phase.JET;
        float s = jetElapsed();
        jetOn = now == Phase.JET && topAt(s) < floor;
        top = jetOn ? topAt(s) : floor;
        boolean sinking = now == Phase.JET && s >= jet - SINK_SECONDS;
        if (sinking && !wasSinking) fallSparks();
        wasSinking = sinking;
        if (now == Phase.WARNING) riseSparks(dt);
        else riseClock = 0f;
        sparks.update(dt);
    }

    /** The warning's rising sparks; {@link LanceRoot} overrides it with soil specks. */
    protected void riseSparks(float dt) {
        RandomXS128 random = sparks.random();
        float cx = centerX(), slit = floor - 1f;
        riseClock += dt;
        while (riseClock >= RISE_PERIOD) {
            riseClock -= RISE_PERIOD;
            float reach = RISE_MIN_PX + random.nextFloat() * (RISE_MAX_PX - RISE_MIN_PX);
            emit(cx, slit, cx + (random.nextFloat() * TWO - 1f) * RISE_SPREAD_PX, slit - reach, RISE_LIFE);
        }
    }

    /** D93's visible consequence of every jet, hit or miss: sparks fall from the upper column and drift aside. */
    protected void fallSparks() {
        RandomXS128 random = sparks.random();
        float cx = centerX();
        for (int i = 0; i < FALL_SPARKS; i++) {
            float y0 = floor - height * (FALL_FROM_FRACTION + random.nextFloat() * (1f - FALL_FROM_FRACTION)), side = random.nextFloat() * TWO - 1f;
            emit(cx + side * JET_WIDTH / TWO, y0, cx + side * FALL_SPREAD_PX, y0 + FALL_DROP_PX, FALL_LIFE);
        }
    }

    /** One spark (the ember palette, {@code KIND_GLOW}). */
    protected void emit(float x0, float y0, float x1, float y1, float life) {
        int c = emitted++ % SPARK_R.length;
        sparks.spawn(KimeriaParticleSystem.KIND_GLOW, x0, y0, x1, y1, life, SPARK_PX, SPARK_R[c], SPARK_G[c], SPARK_B[c]);
    }

    /** The frame index now (rest pulse, warning halves, vent, spent for {@link #SPENT_SECONDS} after a jet). */
    public int frameIndex() {
        Phase now = currentPhase();
        if (now == Phase.JET) return VENT;
        if (now == Phase.WARNING) return warningElapsed() < warningSeconds() / TWO ? WARN1 : WARN2;
        float c = cycleTime();
        if (c < SPENT_SECONDS && elapsed - c - jet - warningSeconds() >= 0f) return SPENT; // a jet that ran ended c s ago
        return ((int) ((elapsed + phase) / PULSE_SECONDS) & 1) == 0 ? REST1 : REST2;
    }

    @Override
    public void render(Batch batch) {
        if (jetOn) {
            int f = ((int) ((elapsed + phase) / JET_FRAME_SECONDS)) & 1;
            float x = centerX() - JET_WIDTH / TWO;
            int t = (int) top, bottom = (int) floor;
            for (int y = t; y < bottom; y++) {
                int k = y - t;
                TextureRegion row = k < TIP_ROWS && bottom - t >= TIP_ROWS ? tipRows[f][k] : jetRows[f][Math.floorMod(y - bottom, JET_ROWS)];
                batch.draw(row, x, y, JET_WIDTH, 1f);
            }
        }
        batch.draw(frames[frameIndex()], position.x, position.y, size.x, size.y);
        if (sparks.count() > 0) sparks.render(batch, Shaders.whiteQuad());
    }
}
