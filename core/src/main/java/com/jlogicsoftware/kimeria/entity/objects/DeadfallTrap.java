package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The trip-line deadfall (#363, D88), a VISIBLE touch trap (D71): a log hauled up on the far side and lashed to a bough,
 * and a pale trip vine stretched across the floor in front of it. The vine is the only trigger.
 *
 * <p>Sequence, one way: {@code TIED} -> {@code WARNING} -> {@code SWING} -> {@code CAUGHT}.
 * <ul>
 *   <li><b>Trip</b>: the hero's body box overlapping the vine's {@value #VINE_WIDTH}x{@value #VINE_HEIGHT} box, from either
 *       side. A jump clears it and nothing fires; nothing else (stone, fireball) trips it.
 *   <li><b>Warning</b>: the shared D7 warning ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the assist, sampled
 *       once at the trip): the vine tugged and the log shivering for its first half ({@code strain}), the lashing slipping
 *       and the log dropping a pixel for its second ({@code slip}). It always runs to the end.
 *   <li><b>Swing</b>: one eased pass of {@value #SWING_SECONDS} s on two parallel ropes ({@value #ROPE_LENGTH} px from the
 *       pivot to the log's centre) from {@value #TIED_DEGREES} degrees on the far side to {@value #SNAG_DEGREES} on the
 *       near side, where the ropes meet the bough's snag. The log's {@value #LOG_WIDTH}x{@value #LOG_HEIGHT} box kills
 *       while it swings; it reaches body height only near the bottom of the arc.
 *   <li><b>Caught</b>: at {@value #SNAG_DEGREES} degrees the ropes lie across the snag's tip, so from there they run
 *       from the tip; the log drops in under it over {@value #SETTLE_SECONDS} s (eased, so it never jumps) and hangs there
 *       above head height, the vine lying cut: harmless until the room is rebuilt (retry, Restart Level, re-entry).
 * </ul>
 *
 * <p>Geometry (world Y grows downward). The Tiled object is the bough's own {@value #BOUGH_WIDTH}x{@value #BOUGH_HEIGHT}
 * rectangle; {@code swing=left} (default) swings the log towards -x, at a hero coming from the left, {@code swing=right}
 * mirrors everything. The pivot P is between the two rope knots, {@value #PIVOT_Y} px below the object's top; the floor
 * line is {@value #FLOOR_BELOW_PIVOT} px below P, so the log's lowest centre is 12 px over the floor. The vine lies
 * {@value #VINE_NEAR} to {@value #VINE_FAR} px from P on the hero's side.
 *
 * <p>Timers tick in {@link #update(float)} only, so pause stops them. The hero is seen only while
 * {@code Player.canLeaveRoom()} (the supplier returns null otherwise); a running warning or swing keeps going.
 * {@link #problem} is the load check; tools/check_tiled_links.py mirrors it.
 */
public class DeadfallTrap extends Entity {
    public enum Phase { TIED, WARNING, SWING, CAUGHT }

    /** What the level does at the trip (the creak) and when the ropes hit the snag (the knock and the bark chips). */
    public interface Listener {
        void tripped(DeadfallTrap trap);
        void caught(DeadfallTrap trap);
    }

    public static final float BOUGH_WIDTH = 112, BOUGH_HEIGHT = 24;
    /** The pivot in the bough sprite: between the knots at x 40 and 55 (centres 40.5, 55.5), at the bough's underside. */
    public static final float PIVOT_X = 48, PIVOT_Y = 9;
    public static final float ROPE_LENGTH = 56, FLOOR_BELOW_PIVOT = 68;
    public static final float TIED_DEGREES = 80, SNAG_DEGREES = -78, SWING_SECONDS = 0.5f;
    public static final float LOG_WIDTH = 24, LOG_HEIGHT = 10;
    /** The trip vine's box, measured from P towards the hero's side, and its height over the floor. */
    public static final float VINE_NEAR = 104, VINE_FAR = 120, VINE_WIDTH = VINE_FAR - VINE_NEAR, VINE_HEIGHT = 5;
    /** The snag's tip: this far from P on the hero's side and this far below it; the caught log's centre hangs below it. */
    public static final float SNAG_REACH = 40, SNAG_BELOW = 8, CAUGHT_BELOW_SNAG = 19;
    /** After the catch the log eases from its swing's end pose to its hanging pose under the snag (looks only: it is harmless). */
    public static final float SETTLE_SECONDS = 0.25f;
    /** Strain: the log shifts this many px every {@link #SHIVER_STEP_SECONDS} (looks only). */
    public static final float SHIVER_STEP_SECONDS = 0.1f, SHIVER_PX = 1;
    /** Load-check reach: floor and headroom from this far before the vine's far end to this far past P. */
    public static final float APPROACH_FLOOR = 64, FAR_FLOOR = 56, HEADROOM = 96;
    /** The swept area: the log's centre reaches {@value #ROPE_LENGTH} px either side of P, plus half the log. */
    private static final float SWEEP_HALF_WIDTH = ROPE_LENGTH + LOG_WIDTH / 2f;
    /** Where the log can be at body height: its box below this far over the floor (the hero is 22 px tall, plus half a log). */
    private static final float BAND_ABOVE_FLOOR = 27, BAND_HALF_WIDTH = 62;
    private static final float ROPE_HALF_SPAN = 7.5f, EYE_ABOVE_CENTRE = 5;
    private static final float FRAME_CENTRE_X = 16, FRAME_CENTRE_Y = 10, VINE_SPRITE_CENTRE = 110, VINE_SPRITE_DROP = 8;
    private static final float SIZE_TOLERANCE = 0.5f, FLOOR_TOLERANCE = 0.5f, HALF = 0.5f;
    public static final int FRAME_TIED = 0, FRAME_STRAIN = 1, FRAME_SLIP = 2, FRAME_SWING = 3, FRAME_CAUGHT = 4;
    private static final int LOG_FRAMES = 5, VINE_TAUT = 0, VINE_TUGGED = 1, VINE_CUT = 2, VINE_FRAMES = 3, SHIVER_FRAMES = 2;

    private final TrapWarnings warnings;
    private final Supplier<Rectangle> heroBody;
    private final Runnable kill;
    private final Listener listener;
    /** -1 when the log swings towards -x (swing=left), +1 for swing=right. */
    private final float toward;
    private final float pivotX, pivotY, floor;
    private final TextureRegion bough, rope;
    private final TextureRegion[] logFrames = new TextureRegion[LOG_FRAMES], vineFrames = new TextureRegion[VINE_FRAMES];
    private final Rectangle vineScratch = new Rectangle(), logScratch = new Rectangle();
    private Phase phase = Phase.TIED;
    private float phaseTime, warningTotal;

    public DeadfallTrap(Assets assets, TrapWarnings warnings, float x, float y, boolean swingRight,
                        Supplier<Rectangle> heroBody, Runnable kill, Listener listener) {
        super(x, y, BOUGH_WIDTH, BOUGH_HEIGHT);
        this.warnings = warnings;
        this.heroBody = heroBody;
        this.kill = kill;
        this.listener = listener;
        toward = swingRight ? 1f : -1f;
        pivotX = pivotXOf(x, swingRight);
        pivotY = y + PIVOT_Y;
        floor = pivotY + FLOOR_BELOW_PIVOT;
        bough = SpriteSheets.TRAP_DEADFALL_BOUGH.frame(assets);
        rope = SpriteSheets.TRAP_DEADFALL_ROPE.frame(assets);
        for (int i = 0; i < LOG_FRAMES; i++) logFrames[i] = SpriteSheets.TRAP_DEADFALL.frame(assets, i, 0);
        for (int i = 0; i < VINE_FRAMES; i++) vineFrames[i] = SpriteSheets.TRAP_DEADFALL_VINE.frame(assets, i, 0);
    }

    public static DeadfallTrap fromMap(Assets assets, TrapWarnings warnings, TiledObject o, Supplier<Rectangle> heroBody,
                                       Runnable kill, Listener listener) {
        return new DeadfallTrap(assets, warnings, o.x, o.y, "right".equals(o.prop("swing", "")), heroBody, kill, listener);
    }

    private static float pivotXOf(float x, boolean swingRight) { return x + (swingRight ? BOUGH_WIDTH - PIVOT_X : PIVOT_X); }

    public Phase phase() { return phase; }
    public float pivotX() { return pivotX; }
    public float pivotY() { return pivotY; }
    public float floor() { return floor; }
    public float toward() { return toward; }
    public float snagX() { return pivotX + toward * SNAG_REACH; }
    public float snagY() { return pivotY + SNAG_BELOW; }
    public float warningTotal() { return warningTotal; }
    public float warningRemaining() { return phase == Phase.WARNING ? Math.max(0f, warningTotal - phaseTime) : 0f; }
    /** Seconds since the release (0 outside the swing). */
    public float swingTime() { return phase == Phase.SWING ? phaseTime : 0f; }

    /** The trip vine's box: {@value #VINE_WIDTH} px wide, {@value #VINE_HEIGHT} px tall, standing on the floor line. */
    public Rectangle vineBox() { return vineInto(new Rectangle()); }

    private Rectangle vineInto(Rectangle out) {
        float near = pivotX + toward * VINE_NEAR, far = pivotX + toward * VINE_FAR;
        return out.set(Math.min(near, far), floor - VINE_HEIGHT, VINE_WIDTH, VINE_HEIGHT);
    }

    /** The rope angle, in degrees towards the far side, {@code t} seconds into the swing (eased: fastest at the bottom). */
    public static float angleAt(float t) {
        float u = MathUtils.clamp(t / SWING_SECONDS, 0f, 1f);
        return TIED_DEGREES - (TIED_DEGREES - SNAG_DEGREES) * (1f - MathUtils.cos(MathUtils.PI * u)) * HALF;
    }

    /** The log's box at rope angle {@code degrees} (towards the far side), written into {@code out}. */
    public Rectangle logBoxAt(float degrees, Rectangle out) {
        float cx = pivotX - toward * ROPE_LENGTH * MathUtils.sinDeg(degrees), cy = pivotY + ROPE_LENGTH * MathUtils.cosDeg(degrees);
        return out.set(cx - LOG_WIDTH / 2f, cy - LOG_HEIGHT / 2f, LOG_WIDTH, LOG_HEIGHT);
    }

    /** What kills the hero right now: the log's box during the swing, an empty box otherwise. */
    public Rectangle lethalBounds() { return lethalInto(new Rectangle()); }

    private Rectangle lethalInto(Rectangle out) {
        if (phase != Phase.SWING) return out.set(pivotX, floor, 0f, 0f);
        return logBoxAt(angleAt(phaseTime), out);
    }

    public boolean isLethal() { return phase == Phase.SWING; }

    @Override
    public void update(float dt) {
        if (isRemoved()) return;
        Rectangle hero = heroBody.get();
        if (phase == Phase.TIED) {
            if (hero == null || !hero.overlaps(vineInto(vineScratch))) return;
            phase = Phase.WARNING;
            phaseTime = 0f;
            warningTotal = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
            listener.tripped(this);
            return;
        }
        if (phase == Phase.CAUGHT) {
            phaseTime = Math.min(phaseTime + dt, SETTLE_SECONDS);
            return;
        }
        phaseTime += dt;
        if (phase == Phase.WARNING && phaseTime >= warningTotal) {
            phaseTime -= warningTotal;
            phase = Phase.SWING;
        }
        if (phase != Phase.SWING) return;
        if (hero != null && hero.overlaps(lethalInto(logScratch))) kill.run();
        if (phaseTime >= SWING_SECONDS) {
            phase = Phase.CAUGHT;
            phaseTime = 0f;
            listener.caught(this);
        }
    }

    /** Frame index into the log sheet for the current phase and time. */
    public int logFrame() {
        return switch (phase) {
            case TIED -> FRAME_TIED;
            case WARNING -> phaseTime < warningTotal * HALF ? FRAME_STRAIN : FRAME_SLIP;
            case SWING -> FRAME_SWING;
            case CAUGHT -> FRAME_CAUGHT;
        };
    }

    public int vineFrame() {
        return switch (phase) {
            case TIED -> VINE_TAUT;
            case WARNING -> VINE_TUGGED;
            default -> VINE_CUT;
        };
    }

    /** The log's centre as drawn, before the strain shiver: on its rope arc, or easing in under the snag once caught. */
    public float logCentreX() {
        if (phase != Phase.CAUGHT) return pivotX - toward * ROPE_LENGTH * MathUtils.sinDeg(drawnDegrees());
        return MathUtils.lerp(pivotX - toward * ROPE_LENGTH * MathUtils.sinDeg(SNAG_DEGREES), snagX(), settled());
    }

    public float logCentreY() {
        if (phase != Phase.CAUGHT) return pivotY + ROPE_LENGTH * MathUtils.cosDeg(drawnDegrees());
        return MathUtils.lerp(pivotY + ROPE_LENGTH * MathUtils.cosDeg(SNAG_DEGREES), snagY() + CAUGHT_BELOW_SNAG, settled());
    }

    private float drawnDegrees() { return phase == Phase.SWING ? angleAt(phaseTime) : TIED_DEGREES; }

    /** 0 at the catch, 1 once the log hangs under the snag; smoothstep, so it starts and ends at rest. */
    private float settled() {
        return Interpolation.smooth.apply(MathUtils.clamp(phaseTime / SETTLE_SECONDS, 0f, 1f));
    }

    @Override
    public void render(Batch batch) {
        boolean flip = toward > 0;
        float cx = logCentreX(), cy = logCentreY();
        int frame = logFrame();
        if (frame == FRAME_STRAIN && (int) (phaseTime / SHIVER_STEP_SECONDS) % SHIVER_FRAMES == 1) cx += SHIVER_PX;
        float eyeY = cy - EYE_ABOVE_CENTRE + (frame == FRAME_SLIP ? 1f : 0f);
        if (phase == Phase.CAUGHT) {
            drawRope(batch, snagX(), snagY(), cx - ROPE_HALF_SPAN, eyeY);
            drawRope(batch, snagX(), snagY(), cx + ROPE_HALF_SPAN, eyeY);
        } else {
            drawRope(batch, pivotX - ROPE_HALF_SPAN, pivotY, cx - ROPE_HALF_SPAN, eyeY);
            drawRope(batch, pivotX + ROPE_HALF_SPAN, pivotY, cx + ROPE_HALF_SPAN, eyeY);
        }
        drawFlipped(batch, logFrames[frame], cx - FRAME_CENTRE_X, cy - FRAME_CENTRE_Y, SpriteSheets.TRAP_DEADFALL.width(),
            SpriteSheets.TRAP_DEADFALL.height(), flip);
        drawFlipped(batch, bough, position.x, position.y, BOUGH_WIDTH, BOUGH_HEIGHT, flip);
        float vineW = SpriteSheets.TRAP_DEADFALL_VINE.width();
        drawFlipped(batch, vineFrames[vineFrame()], pivotX + toward * VINE_SPRITE_CENTRE - vineW / 2f, floor - VINE_SPRITE_DROP,
            vineW, SpriteSheets.TRAP_DEADFALL_VINE.height(), flip);
    }

    private static void drawFlipped(Batch batch, TextureRegion region, float x, float y, float w, float h, boolean flip) {
        if (flip) batch.draw(region, x + w, y, -w, h);
        else batch.draw(region, x, y, w, h);
    }

    /** The 1 px rope strip stretched from (x0, y0) to (x1, y1); rotation as ThornVine's (clockwise for +x in this Y-down world). */
    private void drawRope(Batch batch, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        batch.draw(rope, x0 - HALF, y0, HALF, 0f, 1f, length, 1f, 1f, -MathUtils.atan2(dx, dy) * MathUtils.radiansToDegrees);
    }

    /**
     * Placement rules for a {@code Deadfall} object; returns a message or null. The object is the bough's own
     * {@value #BOUGH_WIDTH}x{@value #BOUGH_HEIGHT} rectangle, {@code swing} is empty, {@code left} or {@code right}. Solid floor
     * (not quicksand) at the floor line must run from {@value #APPROACH_FLOOR} px before the vine's far end to
     * {@value #FAR_FLOOR} px past P, so the hero can stop before the swing or jump the vine and land; nothing solid
     * (collision blocks, falling platforms, walls with their travel, ladders, escalators with their travel) may stand in the
     * swept area (bough to floor, {@value #SWEEP_HALF_WIDTH} px either side of P) or within {@value #HEADROOM} px over the
     * floor from that run's start to the swept area; all of it lies in the room. The room entrance must not touch the vine,
     * and no standing place (entrance, exit, checkpoint) may lie where the log passes at body height.
     */
    public static String problem(TiledObject o, List<TiledObject> collisions, List<TiledObject> spawn, Rectangle room,
                                 Map<String, Rectangle> standing) {
        if (Math.abs(o.width - BOUGH_WIDTH) > SIZE_TOLERANCE || Math.abs(o.height - BOUGH_HEIGHT) > SIZE_TOLERANCE)
            return "must be the bough's own " + (int) BOUGH_WIDTH + "x" + (int) BOUGH_HEIGHT + " rectangle";
        String swing = o.prop("swing", "");
        if (!swing.isEmpty() && !"left".equals(swing) && !"right".equals(swing)) return "has swing '" + swing + "' (left or right)";
        boolean right = "right".equals(swing);
        float toward = right ? 1f : -1f, px = pivotXOf(o.x, right), floorY = o.y + PIVOT_Y + FLOOR_BELOW_PIVOT;
        float runStart = px + toward * (VINE_FAR + APPROACH_FLOOR), runEnd = px - toward * FAR_FLOOR;
        float runLeft = Math.min(runStart, runEnd), runRight = Math.max(runStart, runEnd);
        Rectangle swept = new Rectangle(px - SWEEP_HALF_WIDTH, o.y, 2 * SWEEP_HALF_WIDTH, floorY - o.y - 1f);
        float approachNear = px + toward * SWEEP_HALF_WIDTH;
        Rectangle approach = new Rectangle(Math.min(runStart, approachNear), floorY - HEADROOM,
            Math.abs(approachNear - runStart), HEADROOM - 1f);
        if (runLeft < room.x || runRight > room.x + room.width || o.y < room.y || approach.y < room.y || floorY > room.y + room.height)
            return "needs its bough, its swing and its floor run (" + (int) runLeft + " to " + (int) runRight + ") inside its room";
        List<float[]> spans = new java.util.ArrayList<>();
        for (TiledObject c : collisions)
            if (!"QuickSand".equals(c.type) && Math.abs(c.y - floorY) < FLOOR_TOLERANCE) spans.add(new float[]{c.x, c.x + c.width});
        spans.sort((a, b) -> Float.compare(a[0], b[0]));
        float covered = runLeft;
        for (float[] span : spans) if (span[0] <= covered + FLOOR_TOLERANCE) covered = Math.max(covered, span[1]);
        if (covered < runRight)
            return "needs solid floor at y " + (int) floorY + " (" + (int) FLOOR_BELOW_PIVOT + " px under the knots) from x " + (int) runLeft
                + " to " + (int) runRight + ": room to stop before the swing and to land past the vine";
        for (TiledObject c : collisions) {
            if ("QuickSand".equals(c.type)) continue;
            String clash = clash(swept, approach, new Rectangle(c.x, c.y, c.width, c.height));
            if (clash != null) return clash;
        }
        for (TiledObject c : spawn) {
            Rectangle thing = ThornTrap.sweptBounds(c);
            String clash = thing == null ? null : clash(swept, approach, thing);
            if (clash != null) return clash;
        }
        Rectangle vine = new Rectangle(Math.min(px + toward * VINE_NEAR, px + toward * VINE_FAR), floorY - VINE_HEIGHT, VINE_WIDTH, VINE_HEIGHT);
        Rectangle band = new Rectangle(px - BAND_HALF_WIDTH, floorY - BAND_ABOVE_FLOOR, 2 * BAND_HALF_WIDTH, BAND_ABOVE_FLOOR);
        for (Map.Entry<String, Rectangle> e : standing.entrySet()) {
            if ("entrance".equals(e.getKey()) && e.getValue().overlaps(vine)) return "has its trip vine under the room entrance (the hero would start tripping it)";
            if (e.getValue().overlaps(band)) return "swings its log through the room " + e.getKey();
        }
        return null;
    }

    private static String clash(Rectangle swept, Rectangle approach, Rectangle thing) {
        if (thing.overlaps(swept)) return "has something solid in the log's swing (from the bough to the floor, "
            + (int) SWEEP_HALF_WIDTH + " px either side of the knots)";
        if (thing.overlaps(approach)) return "needs " + (int) HEADROOM + " px of free height over the floor before the swing (jump the vine, stop and wait)";
        return null;
    }
}
