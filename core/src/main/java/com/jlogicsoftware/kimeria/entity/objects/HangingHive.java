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
import java.util.Set;

/**
 * Hanging hive (#365, D90): a rhythmic trap. A paper hive hangs from a bough (drawn into the sprite) with specks drifting
 * at its mouth; on the level clock {@code (elapsed + phase) mod period} it is calm, then shakes for the shared warning
 * ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the assist, taken from the calm), then releases stingers for
 * {@code curtain} seconds at the end of the cycle. The stingers fall at {@link #FALL_SPEED} px/s from the mouth to the first
 * solid under it, so the lethal part is a band {@link #BAND_WIDTH} px wide between the stream's tail and head: every height is
 * lethal for exactly {@code curtain} seconds, later the lower it is. World Y grows downward: the head's Y grows as it falls.
 * <p>
 * A thrown stone or a fireball hitting the hive's body knocks it down ({@link #knockDown()}): no stinger leaves after that,
 * the ones already falling finish their fall (they are visible), the hive drops harmlessly and lies split on the solid.
 * {@code elapsed} only advances in {@link #update}, so pause freezes it, and a room retry (which rebuilds the object)
 * restarts at the authored phase with the hive hanging. A release that would have begun before the room started is
 * counted from the room's start, so no stinger is ever already in the air when the hero arrives.
 */
public class HangingHive extends Entity implements LethalHazard {
    public static final float DEFAULT_PERIOD_SECONDS = 4f, DEFAULT_CURTAIN_SECONDS = 1f;
    /** At the lowest point of the band the hero always gets this long of calm per cycle, with the assist on. */
    public static final float MIN_CALM_SECONDS = 0.8f;
    /** Stinger fall speed (px/s, downward). */
    public static final float FALL_SPEED = 240f;
    /** The sprite's frame; the object's rectangle is the frame (its top edge is the bough's top). */
    public static final float WIDTH = 24f, HEIGHT = 32f;
    /** The band starts this far below the object's top (the first row under the mouth's rim). */
    public static final float MOUTH_OFFSET = 26f;
    /** Lethal and drawn width of the stinger band, centred on the hive. */
    public static final float BAND_WIDTH = 8f;
    /** Authored sizes and floor heights are compared with this slack (Tiled may write 23.9999). */
    public static final float SIZE_TOLERANCE = 0.5f;
    /** The band must run at least this far before it meets a solid. */
    public static final float MIN_DEPTH = 32f;
    /** The knock-down target: the paper body inside the frame (x, y offsets and size). */
    public static final float BODY_X = 2f, BODY_Y = 7f, BODY_WIDTH = 20f, BODY_HEIGHT = 19f; // rows 7-25, above the mouth row 26
    /**
     * Bottom edges (Y down, px below the frame's top) of what is drawn: the paper body in `falling` (rows 7-26, so 27) and the
     * split hive in `fallen` (its lowest row is 29, so 30). `fallen` is drawn with that edge on the solid's top line.
     */
    public static final float FALLING_BOTTOM = 27f, FALLEN_BOTTOM = 30f;
    public static final float SHAKE_FRAME_SECONDS = 0.1f, STINGER_FRAME_SECONDS = 0.1f;
    // Specks (particles, KIND_GRIT): drifting at rest, gathering into the mouth in the warning, scattering at a knock-down.
    public static final float DRIFT_PERIOD = 0.4f, DRIFT_LIFE = 0.8f, DRIFT_MIN_PX = 4f, DRIFT_MAX_PX = 8f;
    public static final float GATHER_PERIOD = 0.05f, GATHER_LIFE = 0.3f, GATHER_RING_PX = 10f;
    public static final int SCATTER_SPECKS = 10, SPECK_CAPACITY = 24;
    public static final float SCATTER_LIFE = 0.6f, SCATTER_PX = 24f, SCATTER_MIN_FRACTION = 0.5f, SPECK_PX = 1f;
    // hive.grid's b efe6c4 (bone) and k 0b0814 (ink): the ink specks read on the sky, the bone ones on dirt.
    private static final float BONE_R = 0.937f, BONE_G = 0.902f, BONE_B = 0.769f, INK_R = 0.043f, INK_G = 0.031f, INK_B = 0.078f;
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final float TWO = 2f, FULL_TURN = (float) (Math.PI * 2);
    private static final int REST = 0, SHAKE1 = 1, SHAKE2 = 2, RELEASE = 3, BARE = 4, FALLING = 5, FALLEN = 6, FRAMES = 7;
    private static final int STINGER_FRAMES = 2, STINGER_ROWS = 16;
    /** The map properties a hive reads; any other key fails the load (a misspelling must not fall back to a default). */
    public static final Set<String> PROPERTIES = Set.of("period", "phase", "curtain");

    public enum Phase {CALM, WARNING, RELEASE}

    private final TrapWarnings warnings;
    private final float period, phase, curtain, floor;
    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    /** One region per row of each stinger frame: the band is drawn a row at a time, so both of its ends clip exactly. */
    private final TextureRegion[][] stingerRows = new TextureRegion[STINGER_FRAMES][STINGER_ROWS];
    private final KimeriaParticleSystem specks;
    private final Rectangle bandScratch = new Rectangle(), bodyScratch = new Rectangle();
    private float elapsed, knockedAt = Float.NaN, dropSpeed, drop, driftClock, gatherClock;
    private boolean knocked, wasWarning, wasRelease, bandOn;
    private float bandTop, bandBottom, bandHead;
    private int drifts;
    private Runnable onWarning = () -> { }, onRelease = () -> { }, onKnockDown = () -> { };

    /** @param floor the world Y of the first solid under the mouth, where the stingers stop (see {@link #floorProblem}) */
    public HangingHive(Assets assets, float x, float y, TrapWarnings warnings, float period, float phase, float curtain, float floor) {
        super(x, y, WIDTH, HEIGHT);
        this.warnings = warnings;
        this.period = period;
        this.phase = phase;
        this.curtain = curtain;
        this.floor = floor;
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.TRAP_HIVE.frame(assets, i, 0);
        SpriteSheets.Sheet sheet = SpriteSheets.TRAP_STINGERS;
        for (int f = 0; f < STINGER_FRAMES; f++)
            for (int r = 0; r < STINGER_ROWS; r++) stingerRows[f][r] = assets.region(sheet.path(), f * sheet.width(), r, sheet.width(), 1);
        specks = new KimeriaParticleSystem(SPECK_CAPACITY, (Float.floatToIntBits(x) * SEED_MIX) ^ Float.floatToIntBits(y));
        updateBand();
    }

    /** Reads and checks the map properties strictly; {@code floor} comes from {@link #floorBelow}. */
    public static HangingHive fromMap(Assets assets, TiledObject o, TrapWarnings warnings, float floor, String label) {
        float period, curtain, phase;
        try {
            for (String key : o.properties.keySet())
                if (!PROPERTIES.contains(key)) throw new IllegalArgumentException("unknown property '" + key + "' (a hive reads " + new java.util.TreeSet<>(PROPERTIES) + ")");
            period = RhythmProps.number(o, "period", DEFAULT_PERIOD_SECONDS);
            curtain = RhythmProps.number(o, "curtain", DEFAULT_CURTAIN_SECONDS);
            phase = RhythmProps.number(o, "phase", 0f);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("HangingHive " + label + ": " + e.getMessage());
        }
        if (Math.abs(o.width - WIDTH) > SIZE_TOLERANCE || Math.abs(o.height - HEIGHT) > SIZE_TOLERANCE)
            throw new IllegalArgumentException("HangingHive " + label + ": must be " + (int) WIDTH + "x" + (int) HEIGHT + " (the sprite's frame)");
        String problem = validate(period, curtain, phase, floor - (o.y + MOUTH_OFFSET));
        if (problem != null) throw new IllegalArgumentException("HangingHive " + label + ": " + problem);
        return new HangingHive(assets, o.x, o.y, warnings, period, phase, curtain, floor);
    }

    /**
     * The world Y where the stingers of a hive at {@code o} stop: the top of the first solid (any Collisions block but
     * quicksand) under its mouth that overlaps the band, inside {@code room}; NaN when there is none.
     */
    public static float floorBelow(TiledObject o, Iterable<TiledObject> collisions, Rectangle room) {
        float mouth = o.y + MOUTH_OFFSET, left = o.x + (WIDTH - BAND_WIDTH) / TWO, best = Float.NaN;
        for (TiledObject c : collisions) {
            if ("QuickSand".equals(c.type) || c.y < mouth - SIZE_TOLERANCE || c.x >= left + BAND_WIDTH || c.x + c.width <= left) continue;
            if (c.x + c.width <= room.x || c.x >= room.x + room.width || c.y >= room.y + room.height) continue;
            if (Float.isNaN(best) || c.y < best) best = c.y;
        }
        return best;
    }

    /** A moving or switchable solid in the band would change where the stingers stop: the load fails with it named. */
    public static String movingSolidProblem(TiledObject o, Iterable<TiledObject> spawn, float floor) {
        float mouth = o.y + MOUTH_OFFSET, left = o.x + (WIDTH - BAND_WIDTH) / TWO;
        for (TiledObject m : spawn) {
            if (!("Actionable".equals(m.type) || "Escalator".equals(m.type) || "FallingPlatform".equals(m.type))) continue;
            if (m.x < left + BAND_WIDTH && m.x + m.width > left && m.y < floor && m.y + m.height > mouth)
                return "a moving or switchable " + m.type + " [" + m.id + "] stands in its stinger band";
        }
        return null;
    }

    /**
     * The authoring rule: the band is at least {@link #MIN_DEPTH} deep and, with the longest assisted warning, the lowest
     * point of the band still gets {@link #MIN_CALM_SECONDS} of calm after the last stinger has passed it.
     */
    public static String validate(float period, float curtain, float phase, float depth) {
        if (!Float.isFinite(period) || !Float.isFinite(curtain) || !Float.isFinite(phase)) return "period, curtain and phase must be finite numbers";
        if (Float.isNaN(depth)) return "has no solid under its mouth in its room";
        if (curtain <= 0f) return "curtain must be positive";
        if (depth < MIN_DEPTH) return "its band is " + depth + " px deep, the minimum is " + MIN_DEPTH;
        float warning = TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS), drain = depth / FALL_SPEED;
        if (period < curtain + drain + warning + MIN_CALM_SECONDS)
            return "period " + period + " is shorter than curtain " + curtain + " + fall " + drain + " + assisted warning " + warning
                + " + minimum calm " + MIN_CALM_SECONDS;
        return null;
    }

    public void onWarning(Runnable listener) { onWarning = listener; }
    public void onRelease(Runnable listener) { onRelease = listener; }
    public void onKnockDown(Runnable listener) { onKnockDown = listener; }

    public float period() { return period; }
    public float curtainSeconds() { return curtain; }
    public float mouthY() { return position.y + MOUTH_OFFSET; }
    public float floorY() { return floor; }
    public boolean isKnocked() { return knocked; }
    public KimeriaParticleSystem specks() { return specks; }

    /** The warning length now: the shared warning, so the assist lengthens the shake (and shortens the calm). */
    public float warningSeconds() { return warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS); }

    /** Position in the cycle, in [0, period). */
    public float cycleTime() {
        float c = (elapsed + phase) % period;
        return c < 0f ? c + period : c;
    }

    /** The phase now; a knocked hive is calm for good. */
    public Phase currentPhase() { return knocked ? Phase.CALM : phaseAt(cycleTime()); }

    public Phase phaseAt(float cycleTime) {
        if (cycleTime >= period - curtain) return Phase.RELEASE;
        if (cycleTime >= period - curtain - warningSeconds()) return Phase.WARNING;
        return Phase.CALM;
    }

    /** Seconds into the current warning, or 0 outside it. */
    public float warningElapsed() {
        return currentPhase() == Phase.WARNING ? cycleTime() - (period - curtain - warningSeconds()) : 0f;
    }

    /** The knock-down target (the paper body), written into {@code out}. */
    public Rectangle bodyInto(Rectangle out) { return out.set(position.x + BODY_X, position.y + BODY_Y, BODY_WIDTH, BODY_HEIGHT); }

    /**
     * A thrown stone or a fireball reached the body: the hive falls, no stinger leaves any more. True when it was hanging
     * (the projectile is stopped or spent); false when it is already down (the projectile flies on).
     */
    public boolean knockDown() {
        if (knocked) return false;
        knocked = true;
        knockedAt = elapsed + phase;
        RandomXS128 random = specks.random();
        float cx = centerX(), cy = position.y + BODY_Y + BODY_HEIGHT / TWO;
        for (int i = 0; i < SCATTER_SPECKS; i++) {
            float angle = random.nextFloat() * FULL_TURN, reach = SCATTER_PX * (SCATTER_MIN_FRACTION + random.nextFloat() * (1f - SCATTER_MIN_FRACTION));
            emit(cx, cy, cx + (float) Math.cos(angle) * reach, cy + (float) Math.sin(angle) * reach, SCATTER_LIFE, (i & 1) == 0);
        }
        onKnockDown.run();
        return true;
    }

    /** The lethal band now (or an empty rectangle when there is none), written into {@code out}. */
    public Rectangle bandInto(Rectangle out) {
        return bandOn ? out.set(centerX() - BAND_WIDTH / TWO, bandTop, BAND_WIDTH, bandBottom - bandTop) : out.set(0, 0, 0, 0);
    }

    public boolean bandActive() { return bandOn; }

    @Override
    public boolean lethalTo(Rectangle heroBody) { return bandOn && bandInto(bandScratch).overlaps(heroBody); }

    /** Every place the band can reach: the column from the mouth to the solid. */
    @Override
    public boolean everLethalIn(Rectangle area) {
        return bandScratch.set(centerX() - BAND_WIDTH / TWO, mouthY(), BAND_WIDTH, floor - mouthY()).overlaps(area);
    }

    /** Everything the hive can draw while it hangs: its frame and the column under it down to the solid. */
    public Rectangle extentInto(Rectangle out) { return out.set(position.x, position.y, size.x, Math.max(size.y, floor - position.y)); }

    /** The column the band can ever cover (for the load checks). */
    public Rectangle columnInto(Rectangle out) { return out.set(centerX() - BAND_WIDTH / TWO, mouthY(), BAND_WIDTH, floor - mouthY()); }

    @Override
    public void update(float dt) {
        elapsed += dt;
        Phase now = currentPhase();
        if (now == Phase.WARNING && !wasWarning) onWarning.run();
        if (now == Phase.RELEASE && !wasRelease) onRelease.run();
        wasWarning = now == Phase.WARNING;
        wasRelease = now == Phase.RELEASE;
        if (knocked) fall(dt);
        else emitSpecks(dt, now);
        specks.update(dt);
        updateBand();
    }

    private void fall(float dt) {
        float maxDrop = floor - (position.y + FALLING_BOTTOM);
        if (drop >= maxDrop) return;
        dropSpeed += FallingTrap.GRAVITY * dt;
        drop = Math.min(maxDrop, drop + dropSpeed * dt);
    }

    /** The top of the `fallen` frame (Y down): its drawn bottom edge, FALLEN_BOTTOM below, is the solid's top line. */
    public float fallenTop() { return floor - FALLEN_BOTTOM; }

    /** True once the knocked hive lies on the solid. */
    public boolean hasLanded() { return knocked && drop >= floor - (position.y + FALLING_BOTTOM); }

    private void emitSpecks(float dt, Phase now) {
        RandomXS128 random = specks.random();
        float cx = centerX(), mouth = mouthY();
        if (now == Phase.WARNING) {
            gatherClock += dt;
            while (gatherClock >= GATHER_PERIOD) {
                gatherClock -= GATHER_PERIOD;
                float angle = random.nextFloat() * FULL_TURN;
                emit(cx + (float) Math.cos(angle) * GATHER_RING_PX, mouth + (float) Math.sin(angle) * GATHER_RING_PX, cx, mouth, GATHER_LIFE, (drifts++ & 1) == 0);
            }
        } else gatherClock = 0f;
        if (now == Phase.CALM) {
            driftClock += dt;
            while (driftClock >= DRIFT_PERIOD) {
                driftClock -= DRIFT_PERIOD;
                float reach = DRIFT_MIN_PX + random.nextFloat() * (DRIFT_MAX_PX - DRIFT_MIN_PX);
                float side = random.nextFloat() * TWO - 1f; // -1 left .. 1 right; always below the mouth (Y down: larger Y)
                emit(cx, mouth, cx + side * reach, mouth + (1f - Math.abs(side) / TWO) * reach, DRIFT_LIFE, (drifts++ & 1) == 0);
            }
        }
    }

    private void emit(float x0, float y0, float x1, float y1, float life, boolean ink) {
        specks.spawn(KimeriaParticleSystem.KIND_GRIT, x0, y0, x1, y1, life, SPECK_PX,
            ink ? INK_R : BONE_R, ink ? INK_G : BONE_G, ink ? INK_B : BONE_B);
    }

    /** Recomputes the band from the clock (no state of its own, so it is the same after any frame rate). */
    private void updateBand() {
        float total = elapsed + phase, open = period - curtain;
        float start = (float) Math.floor((total - open) / period) * period + open; // the latest release start at or before now
        bandOn = false;
        if (knocked && knockedAt < start) return;          // that release never happened
        float emitStart = Math.max(start, phase);          // counted from the room's start at the earliest
        float emitEnd = Math.min(start + curtain, knocked ? knockedAt : Float.MAX_VALUE);
        if (emitEnd <= emitStart || total < emitStart) return;
        float mouth = mouthY();
        bandHead = mouth + FALL_SPEED * (total - emitStart);
        float tail = total <= emitEnd ? mouth : mouth + FALL_SPEED * (total - emitEnd);
        float bottom = Math.min(bandHead, floor);
        if (tail >= bottom) return;
        bandTop = tail;
        bandBottom = bottom;
        bandOn = true;
    }

    @Override
    public void render(Batch batch) {
        if (knocked) {
            batch.draw(frames[BARE], position.x, position.y, size.x, size.y);
            if (hasLanded()) batch.draw(frames[FALLEN], position.x, fallenTop(), size.x, size.y);
            else batch.draw(frames[FALLING], position.x, position.y + drop, size.x, size.y);
        } else {
            int frame = switch (currentPhase()) {
                case CALM -> REST;
                case WARNING -> ((int) (warningElapsed() / SHAKE_FRAME_SECONDS) & 1) == 0 ? SHAKE1 : SHAKE2;
                case RELEASE -> RELEASE;
            };
            batch.draw(frames[frame], position.x, position.y, size.x, size.y);
        }
        if (bandOn) {
            // A row at a time; the pattern moves down with the head, so the darts are seen falling.
            TextureRegion[] rows = stingerRows[((int) ((elapsed + phase) / STINGER_FRAME_SECONDS)) & 1];
            float x = centerX() - BAND_WIDTH / TWO;
            int top = (int) bandTop, bottom = (int) Math.ceil(bandBottom), head = (int) bandHead;
            for (int y = top; y < bottom; y++) batch.draw(rows[Math.floorMod(y - head, STINGER_ROWS)], x, y, BAND_WIDTH, 1f);
        }
        if (specks.count() > 0) specks.render(batch, Shaders.whiteQuad());
    }
}
