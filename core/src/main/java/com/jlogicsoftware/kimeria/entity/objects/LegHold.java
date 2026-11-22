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
import java.util.function.Supplier;

/**
 * The leg-hold trap (#561, D128), a DISGUISED touch trap (D71): a hunter's trap of hardwood jaws and bone teeth lying flat on the
 * ground under leaf litter, its always-drawn cue two tooth tips, a sinew-wrapped hinge at each end and a peg with its cord. It replaces the forest's bomb (D51): the bomb goes to the Fortress.
 *
 * <p>Sequence: {@code OPEN} -> {@code WARNING} -> {@code SNAP} -> {@code SHUT} -> {@code REARM} -> {@code OPEN}.
 * <ul>
 *   <li><b>Contact</b>: while the jaws are open (also during the warning) a hero whose body box touches the open box
 *       ({@value #OPEN_WIDTH} x {@value #OPEN_HEIGHT} px, the litter as drawn) is caught at once, as a risen thorn kills.
 *   <li><b>Zone</b>: the hero's body box inside the jaws' box widened by {@code zone} px (and 32 px up) starts the
 *       <b>warning</b>, the shared D7 time ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, sampled once; leaving the zone does
 *       not cancel it): the leaves shiver and the jaws rattle. A thrown stone landing on the litter snaps them at once, empty; a fireball flies at floor-19..floor-9 and passes over.
 *   <li><b>Snap</b>: for {@value #SNAP_SECONDS} s the arch is lethal up to {@value #SNAP_HEIGHT} px (the jaws as drawn, sprung up), then the
 *       jaws are <b>shut</b> and harmless for {@code window} s, and <b>re-arm</b> for {@value #REARM_SECONDS} s; they stay shut while
 *       the hero's body is in the open box, so they never re-arm under a foot.
 * </ul>
 * Timers tick in {@link #update(float)} only, so pause stops them; the hero is seen only while {@code Player.canLeaveRoom()}
 * (the supplier returns null otherwise). A retry rebuilds the object open. World Y grows downward: the box's bottom is the floor.
 */
public class LegHold extends Entity implements LethalHazard {
    public enum Phase { OPEN, WARNING, SNAP, SHUT, REARM }

    public static final float WIDTH = 32f, HEIGHT = 16f;
    /** The open jaws under their leaf litter: this wide, centred, and this tall on the floor line (the litter and the two tooth tips, as drawn); the snapped arch is {@link #SNAP_HEIGHT} tall. */
    public static final float OPEN_INSET = 4f, OPEN_WIDTH = 24f, OPEN_HEIGHT = 6f, SNAP_HEIGHT = 14f;
    public static final float DEFAULT_ZONE = 24f, MIN_ZONE = 16f, MAX_ZONE = 64f, ZONE_HEIGHT = 32f;
    public static final float DEFAULT_WINDOW_SECONDS = 2f, MIN_WINDOW_SECONDS = 1f, MAX_WINDOW_SECONDS = 3f;
    public static final float SNAP_SECONDS = 0.1f, REARM_SECONDS = 0.3f, RATTLE_STEP_SECONDS = 0.1f;
    /** Free height over the jaws (a run jump), checked at load. */
    public static final float HEADROOM = 96f;
    public static final float SIZE_TOLERANCE = 0.5f;
    // Specks (particles, KIND_GRIT): hopping from the hinges in the warning, bone and bark chips thrown up at the snap (D93).
    public static final float HOP_PERIOD = 0.1f, HOP_LIFE = 0.3f, HOP_PX = 6f, CHIP_LIFE = 0.4f, CHIP_RISE_PX = 14f, CHIP_SPREAD_PX = 12f, SPECK_PX = 1f;
    public static final int CHIPS = 5, PARTICLE_CAPACITY = 16;
    /** The hinge specks start this far over the floor line; a chip flies up between this fraction of {@link #CHIP_RISE_PX} and all of it. */
    public static final float HINGE_RISE_PX = 2f, CHIP_RISE_MIN = 0.5f;
    private static final float[] SPECK_R = {0.541f, 0.937f, 0.290f}, SPECK_G = {0.353f, 0.902f, 0.180f}, SPECK_B = {0.188f, 0.769f, 0.110f};
    private static final long SEED_MIX = 0x9E3779B97F4A7C15L;
    private static final float TWO = 2f;
    private static final int OPEN = 0, RATTLE1 = 1, RATTLE2 = 2, SNAPPED = 3, SHUT = 4, REARMING = 5, FRAMES = 6;
    /** The map properties a leg-hold reads; any other key fails the load. */
    public static final Set<String> PROPERTIES = Set.of("zone", "window");

    private final TrapWarnings warnings;
    private final Supplier<Rectangle> heroBody;
    private final float zone, window, floor;
    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final KimeriaParticleSystem specks;
    private final Rectangle scratch = new Rectangle();
    private Phase phase = Phase.OPEN;
    private float phaseTime, warningTotal, hopClock;
    private int emitted;
    private Runnable onWarning = () -> { }, onSnap = () -> { };

    public LegHold(Assets assets, TrapWarnings warnings, float x, float y, float zone, float window, Supplier<Rectangle> heroBody) {
        super(x, y, WIDTH, HEIGHT);
        this.warnings = warnings;
        this.heroBody = heroBody;
        this.zone = zone;
        this.window = window;
        this.floor = y + HEIGHT;
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.TRAP_LEGHOLD.frame(assets, i, 0);
        specks = new KimeriaParticleSystem(PARTICLE_CAPACITY, (Float.floatToIntBits(x) * SEED_MIX) ^ Float.floatToIntBits(y));
    }

    /** Reads and checks the map object strictly (properties, size); the floor and the headroom are {@link #problem}. */
    public static LegHold fromMap(Assets assets, TrapWarnings warnings, TiledObject o, Supplier<Rectangle> heroBody, String label) {
        float zone, window;
        try {
            for (String key : o.properties.keySet())
                if (!PROPERTIES.contains(key)) throw new IllegalArgumentException("unknown property '" + key + "' (a leg-hold reads " + new java.util.TreeSet<>(PROPERTIES) + ")");
            zone = RhythmProps.number(o, "zone", DEFAULT_ZONE);
            window = RhythmProps.number(o, "window", DEFAULT_WINDOW_SECONDS);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("LegHold " + label + ": " + e.getMessage());
        }
        if (Math.abs(o.width - WIDTH) > SIZE_TOLERANCE || Math.abs(o.height - HEIGHT) > SIZE_TOLERANCE)
            throw new IllegalArgumentException("LegHold " + label + ": must be " + (int) WIDTH + "x" + (int) HEIGHT + " (the sprite's frame)");
        String problem = validate(zone, window);
        if (problem != null) throw new IllegalArgumentException("LegHold " + label + ": " + problem);
        return new LegHold(assets, warnings, o.x, o.y, zone, window, heroBody);
    }

    /** The authoring rule: finite numbers, the zone and the shut window in their ranges. */
    public static String validate(float zone, float window) {
        if (!Float.isFinite(zone) || !Float.isFinite(window)) return "zone and window must be finite numbers";
        if (zone < MIN_ZONE || zone > MAX_ZONE) return "zone " + zone + " is outside " + (int) MIN_ZONE + "-" + (int) MAX_ZONE + " px";
        if (window < MIN_WINDOW_SECONDS || window > MAX_WINDOW_SECONDS)
            return "window " + window + " is outside " + MIN_WINDOW_SECONDS + "-" + MAX_WINDOW_SECONDS + " s";
        return null;
    }

    /**
     * The jaws stand on a floor: solids (a block or a one-way platform, never quicksand) whose tops lie on the object's bottom
     * edge within {@link #SIZE_TOLERANCE} cover all of its width (split blocks merged); and no solid block (a one-way platform
     * is none) stands within {@link #HEADROOM} px over the jaws. Null when both hold.
     */
    public static String problem(TiledObject o, List<TiledObject> collisions) {
        float floorY = o.y + HEIGHT, covered = o.x;
        boolean progress = true;
        while (covered < o.x + WIDTH - SIZE_TOLERANCE && progress) {
            progress = false;
            for (TiledObject c : collisions) {
                if ("QuickSand".equals(c.type)) continue;
                if (Math.abs(c.y - floorY) <= SIZE_TOLERANCE && c.x <= covered + SIZE_TOLERANCE && c.x + c.width > covered + SIZE_TOLERANCE) {
                    covered = c.x + c.width;
                    progress = true;
                }
            }
        }
        if (covered < o.x + WIDTH - SIZE_TOLERANCE) return "needs a floor whose top is its bottom edge under all of its " + (int) WIDTH + " px";
        for (TiledObject c : collisions) {
            if ("Platform".equals(c.type) || "QuickSand".equals(c.type)) continue;
            if (c.x < o.x + WIDTH && c.x + c.width > o.x && c.y < floorY - SIZE_TOLERANCE && c.y + c.height > floorY - HEADROOM)
                return "needs " + (int) HEADROOM + " px of free height over its jaws (a solid block stands in it)";
        }
        return null;
    }

    public void onWarning(Runnable listener) { onWarning = listener; }
    public void onSnap(Runnable listener) { onSnap = listener; }

    public Phase phase() { return phase; }
    public float zoneSize() { return zone; }
    public float windowSeconds() { return window; }
    public float floorY() { return floor; }
    public float warningTotal() { return warningTotal; }
    public KimeriaParticleSystem specks() { return specks; }
    /** Seconds the current phase has run. */
    public float phaseTime() { return phaseTime; }

    /** The litter's box while the jaws are open: {@value #OPEN_WIDTH} px wide, {@value #OPEN_HEIGHT} px tall on the floor line. */
    public Rectangle openBoxInto(Rectangle out) { return out.set(position.x + OPEN_INSET, floor - OPEN_HEIGHT, OPEN_WIDTH, OPEN_HEIGHT); }

    /** The snapped arch: {@value #OPEN_WIDTH} px wide, {@value #SNAP_HEIGHT} px tall. */
    public Rectangle snapBoxInto(Rectangle out) { return out.set(position.x + OPEN_INSET, floor - SNAP_HEIGHT, OPEN_WIDTH, SNAP_HEIGHT); }

    /** The warning zone: the open box widened by {@code zone} each side and {@value #ZONE_HEIGHT} px tall. */
    public Rectangle zoneInto(Rectangle out) { return out.set(position.x + OPEN_INSET - zone, floor - ZONE_HEIGHT, OPEN_WIDTH + TWO * zone, ZONE_HEIGHT); }

    /** Everything the trap can draw (the frame and its specks' reach), for the camera-view test of its sounds. */
    public Rectangle extentInto(Rectangle out) { return out.set(position.x, floor - SNAP_HEIGHT - CHIP_RISE_PX, WIDTH, SNAP_HEIGHT + CHIP_RISE_PX); }

    @Override
    public boolean lethalTo(Rectangle body) {
        return switch (phase) {
            case OPEN, WARNING -> openBoxInto(scratch).overlaps(body);
            case SNAP -> snapBoxInto(scratch).overlaps(body);
            default -> false;
        };
    }

    /** The most it can ever cover: the snapped arch. */
    @Override
    public boolean everLethalIn(Rectangle area) { return snapBoxInto(scratch).overlaps(area); }

    /**
     * A thrown stone landed on the open jaws' litter (D128): they snap now, empty, with no warning (nothing is under them).
     * Returns true when this made them snap, false when they were not open (the projectile flies on).
     */
    public boolean snapFromAfar() {
        if (phase != Phase.OPEN && phase != Phase.WARNING) return false;
        snap();
        return true;
    }

    private void snap() {
        phase = Phase.SNAP;
        phaseTime = 0f;
        chips();
        onSnap.run();
    }

    @Override
    public void update(float dt) {
        Rectangle hero = heroBody.get();
        phaseTime += dt;
        switch (phase) {
            case OPEN -> {
                if (hero != null && zoneInto(scratch).overlaps(hero)) {
                    phase = Phase.WARNING;
                    phaseTime = 0f;
                    hopClock = 0f;
                    warningTotal = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
                    onWarning.run();
                }
            }
            case WARNING -> {
                hop(dt);
                if (phaseTime >= warningTotal) snap();
            }
            case SNAP -> { if (phaseTime >= SNAP_SECONDS) { phase = Phase.SHUT; phaseTime -= SNAP_SECONDS; } }
            case SHUT -> {
                // Never lift the jaws under a foot: stay shut until the hero's body has left the teeth's box.
                if (phaseTime >= window && (hero == null || !openBoxInto(scratch).overlaps(hero))) { phase = Phase.REARM; phaseTime = 0f; }
            }
            case REARM -> {
                // A foot that steps in while the jaws lift holds the last frame until it leaves.
                if (phaseTime >= REARM_SECONDS && (hero == null || !openBoxInto(scratch).overlaps(hero))) { phase = Phase.OPEN; phaseTime = 0f; }
            }
        }
        specks.update(dt);
    }

    private void hop(float dt) {
        RandomXS128 random = specks.random();
        hopClock += dt;
        while (hopClock >= HOP_PERIOD) {
            hopClock -= HOP_PERIOD;
            float side = random.nextBoolean() ? OPEN_INSET : WIDTH - OPEN_INSET, y0 = floor - HINGE_RISE_PX;
            speck(position.x + side, y0, position.x + side + (random.nextFloat() * TWO - 1f) * HOP_PX, y0 - HOP_PX, HOP_LIFE);
        }
    }

    /** D93's visible consequence of every snap, hit or miss: bone and bark chips thrown up from the jaws. */
    private void chips() {
        RandomXS128 random = specks.random();
        float cx = position.x + WIDTH / TWO;
        for (int i = 0; i < CHIPS; i++) {
            float side = random.nextFloat() * TWO - 1f;
            speck(cx + side * OPEN_WIDTH / TWO, floor - SNAP_HEIGHT, cx + side * CHIP_SPREAD_PX, floor - SNAP_HEIGHT - CHIP_RISE_PX * (CHIP_RISE_MIN + random.nextFloat() * (1f - CHIP_RISE_MIN)), CHIP_LIFE);
        }
    }

    private void speck(float x0, float y0, float x1, float y1, float life) {
        int c = emitted++ % SPECK_R.length;
        specks.spawn(KimeriaParticleSystem.KIND_GRIT, x0, y0, x1, y1, life, SPECK_PX, SPECK_R[c], SPECK_G[c], SPECK_B[c]);
    }

    /** The frame index now: open, the two rattle frames in turn through the warning, snap, shut, rearm. */
    public int frameIndex() {
        return switch (phase) {
            case OPEN -> OPEN;
            case WARNING -> ((int) (phaseTime / RATTLE_STEP_SECONDS) & 1) == 0 ? RATTLE1 : RATTLE2;
            case SNAP -> SNAPPED;
            case SHUT -> SHUT;
            case REARM -> REARMING;
        };
    }

    @Override
    public void render(Batch batch) {
        batch.draw(frames[frameIndex()], position.x, position.y, size.x, size.y);
        if (specks.count() > 0) specks.render(batch, Shaders.whiteQuad());
    }
}
