package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.List;

/**
 * Creeping bramble (D89, #364): a rhythm trap rooted in a wall or ledge face. A clump of thorny canes sits at the
 * root and is lethal at every moment; on a clock it rustles for the shared warning, then an arm grows across the lane
 * to the object's full width, holds, and retracts.
 * <p>
 * One cycle, measured as {@code (elapsed + phase) mod period}: rest, warning, grow, hold, retract (the cycle ends with
 * the retract). {@code elapsed} only advances in {@link #update}, so pause freezes it and a room rebuild restarts it at
 * the authored phase. World Y grows downward: the object's bottom edge is the floor line, and the lethal band runs
 * from {@code floor - 18} to {@code floor - 2}.
 */
public class Bramble extends Entity implements LethalHazard {
    public static final float DEFAULT_PERIOD_SECONDS = 4f, DEFAULT_HOLD_SECONDS = 1f;
    public static final float MIN_HOLD_SECONDS = 0.6f, MAX_HOLD_SECONDS = 1.5f;
    public static final float GROW_SECONDS = 0.25f, RETRACT_SECONDS = 0.5f;
    /** Rest left per cycle with the longest (assisted) warning. */
    public static final float MIN_REST_SECONDS = 0.8f;
    /** The clump: this much of the lane from the root face is lethal in every phase. */
    public static final float CLUMP_REACH = 12f;
    /** The lethal band: its top is {@code BAND_TOP} above the floor line, its bottom {@code BAND_BOTTOM} above it. */
    public static final float BAND_TOP = 18f, BAND_BOTTOM = 2f;
    public static final float HEIGHT = 24f, MIN_REACH = 48f, MAX_REACH = 96f;
    /** Floor the loader wants beyond the reach, so the hero can stand clear of the grown arm. */
    public static final float CLEAR_STANDING = 16f;
    /** Authoring slack for faces and floors that meet the box, as DeadfallTrap's (a 0.5 px Tiled offset still counts). */
    public static final float FLOOR_TOLERANCE = 0.5f;
    public static final List<String> PROPERTIES = List.of("root", "period", "phase", "hold");
    private static final int REST = 0, RUSTLE_FIRST = 1, RUSTLE_SECOND = 2, CANE_A = 3, CANE_B = 4, TIP = 5, FRAMES = 6;
    /** The arm's first cane segment starts this far from the root face, under the clump. */
    private static final float CANE_START = 8f;
    /** A right-root frame is mirrored about its own centre (a fraction of its size). */
    private static final float MIRROR_ORIGIN = 0.5f;
    /** The arm alternates its two cane segments. */
    private static final int CANE_KINDS = 2;

    public enum Phase {REST, WARNING, GROW, HOLD, RETRACT}

    private final TrapWarnings warnings;
    private final boolean rootRight;
    private final float period, phase, hold;
    private final TextureRegion[] frames = new TextureRegion[FRAMES];
    private final Rectangle band = new Rectangle(), fullBand = new Rectangle();
    private Runnable onWarning = () -> { }, onGrow = () -> { };
    private float elapsed;
    private Phase last;

    public Bramble(Assets assets, float x, float y, float w, float h, TrapWarnings warnings,
                   boolean rootRight, float period, float phase, float hold) {
        super(x, y, w, h);
        this.warnings = warnings;
        this.rootRight = rootRight;
        this.period = period;
        this.phase = phase;
        this.hold = hold;
        for (int i = 0; i < FRAMES; i++) frames[i] = SpriteSheets.TRAP_BRAMBLE.frame(assets, i, 0);
        bandInto(fullBand, w);
        last = currentPhase();
    }

    /** Reads and checks the map properties strictly; the message is prefixed with {@code label}. */
    public static Bramble fromMap(Assets assets, TiledObject o, TrapWarnings warnings, String label) {
        for (String key : o.properties.keySet())
            if (!PROPERTIES.contains(key)) throw new IllegalArgumentException("Bramble " + label + ": unknown property " + key);
        String root = o.properties.get("root");
        if (!"left".equals(root) && !"right".equals(root))
            throw new IllegalArgumentException("Bramble " + label + ": property root must be left or right, not '" + root + "'");
        float period, phase, hold;
        try {
            period = RhythmProps.number(o, "period", DEFAULT_PERIOD_SECONDS);
            phase = RhythmProps.number(o, "phase", 0f);
            hold = RhythmProps.number(o, "hold", DEFAULT_HOLD_SECONDS);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Bramble " + label + ": " + e.getMessage());
        }
        String problem = validate(o.width, o.height, period, hold);
        if (problem != null) throw new IllegalArgumentException("Bramble " + label + ": " + problem);
        return new Bramble(assets, o.x, o.y, o.width, o.height, warnings, "right".equals(root), period, phase, hold);
    }

    /** The authoring rule: the sprite height, a reach in range, and {@link #MIN_REST_SECONDS} of rest with the assisted warning. */
    public static String validate(float width, float height, float period, float hold) {
        if (height != HEIGHT) return "height must be " + HEIGHT + " (the sprite), not " + height;
        if (width < MIN_REACH || width > MAX_REACH) return "width (the reach) must be " + MIN_REACH + " to " + MAX_REACH + ", not " + width;
        if (hold < MIN_HOLD_SECONDS || hold > MAX_HOLD_SECONDS) return "hold must be " + MIN_HOLD_SECONDS + " to " + MAX_HOLD_SECONDS + ", not " + hold;
        float needed = minimumPeriod(hold);
        if (period < needed) return "period " + period + " is shorter than assisted warning + grow + hold + retract + minimum rest = " + needed;
        return null;
    }

    public static float minimumPeriod(float hold) {
        return TrapWarnings.longestDuration(TrapWarnings.DEFAULT_WARNING_SECONDS) + GROW_SECONDS + hold + RETRACT_SECONDS + MIN_REST_SECONDS;
    }

    /**
     * Where the bramble may stand, or null: the root edge against a solid face over the whole band, and a floor under
     * the tip end and {@link #CLEAR_STANDING} beyond it. {@code solids} are the room's solid collision rectangles,
     * {@code floors} every rectangle the hero can stand on.
     */
    public static String placementProblem(Rectangle box, boolean rootRight, List<Rectangle> solids, List<Rectangle> floors) {
        float floor = box.y + box.height, rootX = rootRight ? box.x + box.width : box.x;
        // The root face may be split into several solids (a wall and a block): their spans along the band must cover it.
        List<float[]> face = new java.util.ArrayList<>();
        for (int i = 0; i < solids.size(); i++) {
            Rectangle s = solids.get(i);
            float edge = rootRight ? s.x : s.x + s.width;
            if (Math.abs(edge - rootX) < FLOOR_TOLERANCE) face.add(new float[]{s.y, s.y + s.height});
        }
        if (!covers(face, floor - BAND_TOP, floor - BAND_BOTTOM))
            return "its root edge (x " + rootX + ") is not against a solid face over the band y " + (floor - BAND_TOP) + ".." + (floor - BAND_BOTTOM);
        float tipEnd = rootRight ? box.x : box.x + box.width;
        float from = tipEnd - CLEAR_STANDING, to = tipEnd + CLEAR_STANDING;
        List<float[]> run = new java.util.ArrayList<>();
        for (int i = 0; i < floors.size(); i++) {
            Rectangle f = floors.get(i);
            if (Math.abs(f.y - floor) < FLOOR_TOLERANCE) run.add(new float[]{f.x, f.x + f.width});
        }
        if (covers(run, from, to)) return null;
        return "needs floor at y " + floor + " under x " + from + ".." + to + " (the tip end and " + CLEAR_STANDING + " px beyond it)";
    }

    /** True when the merged spans cover {@code from}..{@code to}, gaps up to the tolerance allowed (DeadfallTrap's floor rule). */
    private static boolean covers(List<float[]> spans, float from, float to) {
        spans.sort((a, b) -> Float.compare(a[0], b[0]));
        float covered = from;
        for (float[] span : spans) if (span[0] <= covered + FLOOR_TOLERANCE) covered = Math.max(covered, span[1]);
        return covered >= to - FLOOR_TOLERANCE;
    }

    /** True when the bramble's own box (its full reach) overlaps {@code area}; no allocation. */
    public boolean boxOverlaps(Rectangle area) {
        return position.x < area.x + area.width && area.x < position.x + size.x
            && position.y < area.y + area.height && area.y < position.y + size.y;
    }

    /** Called once at the start of every warning and every grow (sounds); the caller decides whether it is heard. */
    public void onCues(Runnable warning, Runnable grow) {
        onWarning = warning;
        onGrow = grow;
    }

    public float period() { return period; }
    public float hold() { return hold; }
    public boolean rootRight() { return rootRight; }

    /** The warning length now: the shared warning, so the assist lengthens it and shortens the rest. */
    public float warningSeconds() {
        return warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
    }

    private float restEnd() { return period - RETRACT_SECONDS - hold - GROW_SECONDS - warningSeconds(); }
    private float growStart() { return period - RETRACT_SECONDS - hold - GROW_SECONDS; }
    private float holdStart() { return period - RETRACT_SECONDS - hold; }
    private float retractStart() { return period - RETRACT_SECONDS; }

    /** Position in the cycle, in [0, period). */
    public float cycleTime() {
        float c = (elapsed + phase) % period;
        return c < 0f ? c + period : c;
    }

    public Phase currentPhase() { return phaseAt(cycleTime()); }

    public Phase phaseAt(float t) {
        if (t >= retractStart()) return Phase.RETRACT;
        if (t >= holdStart()) return Phase.HOLD;
        if (t >= growStart()) return Phase.GROW;
        if (t >= restEnd()) return Phase.WARNING;
        return Phase.REST;
    }

    /** How far the lethal part reaches from the root face at cycle time {@code t}. */
    public float extentAt(float t) {
        float reach = size.x;
        return switch (phaseAt(t)) {
            case REST, WARNING -> CLUMP_REACH;
            case GROW -> CLUMP_REACH + (reach - CLUMP_REACH) * Math.min(1f, (t - growStart()) / GROW_SECONDS);
            case HOLD -> reach;
            case RETRACT -> reach - (reach - CLUMP_REACH) * Math.min(1f, (t - retractStart()) / RETRACT_SECONDS);
        };
    }

    public float extent() { return extentAt(cycleTime()); }

    /** The lethal rectangle now, written into {@code out}. */
    public Rectangle lethalBoundsInto(Rectangle out) {
        return bandInto(out, extent());
    }

    /** The lethal rectangle at its full reach (for authoring checks). */
    public Rectangle fullBounds() { return new Rectangle(fullBand); }

    private Rectangle bandInto(Rectangle out, float extent) {
        float floor = position.y + size.y;
        float x = rootRight ? position.x + size.x - extent : position.x;
        return out.set(x, floor - BAND_TOP, extent, BAND_TOP - BAND_BOTTOM);
    }

    @Override
    public boolean lethalTo(Rectangle heroBody) {
        return lethalBoundsInto(band).overlaps(heroBody);
    }

    @Override
    public boolean everLethalIn(Rectangle area) {
        return fullBand.overlaps(area);
    }

    @Override
    public void update(float dt) {
        elapsed += dt;
        Phase now = currentPhase();
        if (now != last) {
            if (now == Phase.WARNING) onWarning.run();
            else if (now == Phase.GROW) onGrow.run();
            last = now;
        }
    }

    @Override
    public void render(Batch batch) {
        float t = cycleTime();
        Phase now = phaseAt(t);
        int drawn = (int) extentAt(t);
        float w = SpriteSheets.TRAP_BRAMBLE.width(), h = size.y;
        if (drawn > CLUMP_REACH) {
            int tip = Math.max(drawn - (int) w, (int) CANE_START);
            for (int x = (int) CANE_START, n = 0; x < tip; x += (int) w, n++)
                draw(batch, frames[n % CANE_KINDS == 0 ? CANE_A : CANE_B], x, w, h);
            draw(batch, frames[TIP], tip, w, h);
        }
        int clump = now != Phase.WARNING ? REST : t - restEnd() < warningSeconds() / 2f ? RUSTLE_FIRST : RUSTLE_SECOND;
        draw(batch, frames[clump], 0, w, h);
    }

    /** Draws {@code region} with its root-side edge {@code fromRoot} px out from the root face. */
    private void draw(Batch batch, TextureRegion region, float fromRoot, float w, float h) {
        if (rootRight) // mirrored about the frame's centre, so the art grows leftward from a right-hand face
            batch.draw(region, position.x + size.x - fromRoot - w, position.y, w * MIRROR_ORIGIN, h * MIRROR_ORIGIN, w, h, -1f, 1f, 0f);
        else batch.draw(region, position.x + fromRoot, position.y, w, h);
    }
}
