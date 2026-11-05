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
import java.util.function.Supplier;

/**
 * Proximity traps (D44, #231): the thorn root/stake ({@code ThornRoot}) and the bristling thorn tuft
 * ({@code ThornTuft}). Both sit on the floor, harmless and quiet until the hero's body enters the
 * trap's trigger zone. That starts a warning of the shared D7 length
 * ({@link TrapWarnings#DEFAULT_WARNING_SECONDS}, longer with the assist), after which the trap turns
 * lethal on overlap and stays lethal until the room is rebuilt (retry, Restart Level, re-entry).
 *
 * <p>Once triggered the sequence always runs to the end: leaving the zone does not cancel it (the same
 * rule as the bomb's lit fuse), so the outcome never depends on a hidden "re-calm" state. Every timer
 * ticks inside {@link #update(float)} only, which {@code Level.update} calls, so pause stops it.
 *
 * <p>Like every other hazard (the {@code Deadline}, enemy contact, the bomb's trigger, all in
 * {@code Player.checkGameplayOverlaps}), the hero is only seen while {@code Player.canLeaveRoom()}: not while dying,
 * resetting or arriving, nor after reaching the checkpoint. {@code Level} passes a null body then, so the zone does
 * not trigger and the column does not kill; timers already running keep ticking (as a lit bomb fuse does).
 */
public class ThornTrap extends Entity {
    /** Lethal column of the root (art: a thin stake) and of the tuft (art: the spikes inside the 16x16 frame). */
    private static final float ROOT_KILL_WIDTH = 6f, ROOT_KILL_HEIGHT = 22f, TUFT_KILL_SIZE = 12f;
    /** Frames per sheet, in tools/sprite_layouts.py order. */
    private static final int ROOT_FRAMES = 5, TUFT_FRAMES = 3;
    private static final int FRAME_PEEK_1 = 1, FRAME_RISING = 3, FRAME_UP = 4, FRAME_BRISTLE = 1, FRAME_SPIKED = 2;
    private static final int WOBBLE_FRAMES = 2;
    /** The zone starts this far above the footprint, so a hero on a ledge well above the trap does not trigger it. */
    private static final float ZONE_ABOVE = 32f;
    /** Floor tolerance for "bottom edge on a block's top" when checking a map. */
    private static final float FLOOR_TOLERANCE = 0.5f;

    public enum Kind {
        /** 16x24 stake: hidden, wobbling tip, rising, up. */
        ROOT(SpriteSheets.TRAP_THORN_ROOT, ROOT_KILL_WIDTH, ROOT_KILL_HEIGHT, ROOT_FRAMES),
        /** 16x16 tuft: calm, bristling, spiked. */
        TUFT(SpriteSheets.TRAP_THORN_TUFT, TUFT_KILL_SIZE, TUFT_KILL_SIZE, TUFT_FRAMES);

        final SpriteSheets.Sheet sheet;
        /** Width and full height of the lethal column, centred on the footprint, standing on the floor. */
        final float killWidth, killHeight;
        final int frameCount;

        Kind(SpriteSheets.Sheet sheet, float killWidth, float killHeight, int frameCount) {
            this.sheet = sheet;
            this.killWidth = killWidth;
            this.killHeight = killHeight;
            this.frameCount = frameCount;
        }
    }

    /** Where the trap is in its one-way sequence. {@code RISING} is only used by the root. */
    public enum Phase { DORMANT, WARNING, RISING, ARMED }

    /** Zone reach, in pixels beyond each side of the footprint, when the map does not say otherwise. */
    public static final float DEFAULT_ZONE = 80f;
    /** Time the root spends on its rising frame (movement, so not scaled by the assist). */
    public static final float RISE_SECONDS = 0.12f;
    /** Seconds per peek frame while the tip wobbles. */
    public static final float WOBBLE_STEP_SECONDS = 0.1f;
    /** Smallest used zone side, and the floor run kept either side of the footprint for take-off and landing. */
    public static final float MIN_ZONE = 64f;
    /** Escalator travel is authored in tiles of this size (see Escalator). */
    private static final float ESCALATOR_TILE = 32f;
    /** A wall may sit raised or lowered one size away either way: the swept box is this many sizes wide and tall. */
    private static final float WALL_SWEEP = 3f;
    /** Free height above the floor the author must leave over the trap and its zone so a jump clears it. */
    public static final float HEADROOM = 96f;
    /** A thorn root rises to this height; while rising it kills only the lower half of it. */
    private static final float ROOT_RISING_FRACTION = 0.5f;

    private final Kind kind;
    private final TrapWarnings warnings;
    private final Supplier<Rectangle> heroBody;
    private final Runnable kill;
    private final float zoneLeft, zoneRight;
    private final TextureRegion[] frames;
    private Phase phase = Phase.DORMANT;
    private float phaseTime;
    private float warningTotal;

    public ThornTrap(Assets assets, TrapWarnings warnings, Kind kind, float x, float y, float w, float h,
                     float zoneLeft, float zoneRight, Supplier<Rectangle> heroBody, Runnable kill) {
        super(x, y, w, h);
        this.kind = kind;
        this.warnings = warnings;
        this.heroBody = heroBody;
        this.kill = kill;
        this.zoneLeft = zoneLeft;
        this.zoneRight = zoneRight;
        frames = new TextureRegion[kind.frameCount];
        for (int i = 0; i < kind.frameCount; i++) frames[i] = kind.sheet.frame(assets, i, 0);
    }

    public static ThornTrap fromMap(Assets assets, TrapWarnings warnings, TiledObject o,
                                    Supplier<Rectangle> heroBody, Runnable kill) {
        float zone = number(o, "zone", DEFAULT_ZONE);
        return new ThornTrap(assets, warnings, "ThornRoot".equals(o.type) ? Kind.ROOT : Kind.TUFT,
            o.x, o.y, o.width, o.height, number(o, "zoneLeft", zone), number(o, "zoneRight", zone), heroBody, kill);
    }

    /** Plain decimal numbers only (also what tools/check_tiled_links.py accepts): no NaN, infinity, exponent or suffix. */
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    /** The raw property parsed strictly; absent or empty gives {@code fallback}, anything unparsable throws. */
    private static float number(TiledObject o, String key, float fallback) {
        String raw = o.prop(key, "").trim();
        if (raw.isEmpty()) return fallback;
        if (!NUMBER.matcher(raw).matches()) throw new IllegalArgumentException(key + " '" + raw + "' is not a plain number");
        return Float.parseFloat(raw);
    }

    public Kind kind() { return kind; }
    public Phase phase() { return phase; }
    public boolean isDormant() { return phase == Phase.DORMANT; }
    /** Seconds of the warning left (0 outside it). */
    public float warningRemaining() { return phase == Phase.WARNING ? Math.max(0, warningTotal - phaseTime) : 0f; }
    public float warningTotal() { return warningTotal; }

    /** The area that starts the warning: the footprint widened by the zone, from {@value #ZONE_ABOVE} px above the footprint to its floor. */
    public Rectangle triggerZone() { return zoneOf(position.x, position.y, size.x, size.y, zoneLeft, zoneRight); }

    static Rectangle zoneOf(float x, float y, float w, float h, float left, float right) {
        return new Rectangle(x - left, y - ZONE_ABOVE, w + left + right, h + ZONE_ABOVE);
    }

    /** What kills the hero right now (empty-sized outside the lethal phases). */
    public Rectangle lethalBounds() {
        float height = switch (phase) {
            case ARMED -> kind.killHeight;
            case RISING -> kind.killHeight * ROOT_RISING_FRACTION;
            default -> 0f;
        };
        return new Rectangle(position.x + (size.x - kind.killWidth) / 2f, position.y + size.y - height, kind.killWidth, height);
    }

    public boolean isLethal() { return phase == Phase.ARMED || phase == Phase.RISING; }

    @Override
    public void update(float dt) {
        if (isRemoved()) return;
        Rectangle hero = heroBody.get();
        if (phase == Phase.DORMANT && hero != null && hero.overlaps(triggerZone())) {
            phase = Phase.WARNING;
            phaseTime = 0f;
            warningTotal = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
        }
        if (phase != Phase.DORMANT && phase != Phase.ARMED) {
            phaseTime += dt;
            if (phase == Phase.WARNING && phaseTime >= warningTotal) {
                phaseTime -= warningTotal;
                phase = kind == Kind.ROOT ? Phase.RISING : Phase.ARMED;
            }
            if (phase == Phase.RISING && phaseTime >= RISE_SECONDS) {
                phase = Phase.ARMED;
            }
        }
        if (isLethal() && hero != null && hero.overlaps(lethalBounds())) kill.run();
    }

    /** Frame index into the sheet for the current phase and time. */
    public int frameIndex() {
        return switch (kind) {
            case ROOT -> switch (phase) {
                case DORMANT -> 0;
                case WARNING -> FRAME_PEEK_1 + (int) (phaseTime / WOBBLE_STEP_SECONDS) % WOBBLE_FRAMES;
                case RISING -> FRAME_RISING;
                case ARMED -> FRAME_UP;
            };
            case TUFT -> switch (phase) {
                case DORMANT -> 0;
                case WARNING -> FRAME_BRISTLE;
                default -> FRAME_SPIKED;
            };
        };
    }

    @Override
    public void render(Batch batch) {
        float w = kind.sheet.width(), h = kind.sheet.height();
        batch.draw(frames[frameIndex()], position.x + (size.x - w) / 2f, position.y + size.y - h, w, h);
    }

    /**
     * Placement rules for {@code ThornRoot}/{@code ThornTuft} objects; returns a message or null. The trap
     * must lie in its room on open floor with its whole jump envelope clear: the footprint widened by
     * {@link #MIN_ZONE} (or the zone, if larger) on each side must have floor under all of it, {@link #HEADROOM}
     * of free height above it counting every solid thing (collision blocks including one-way platforms, falling
     * platforms, walls with their travel, ladders, escalators with their travel), and lie inside the room. Each
     * used zone side is at least {@link #MIN_ZONE}, so the warning starts early enough to react, and the hero's
     * entrance is outside the zone (it never starts triggered). Bad numbers are rejected, not defaulted.
     */
    public static String problem(TiledObject o, List<TiledObject> collisions, List<TiledObject> spawn,
                                 Rectangle room, Rectangle entranceBody) {
        if (o.width <= 0 || o.height <= 0) return "needs positive bounds";
        float left, right;
        try {
            float zone = number(o, "zone", DEFAULT_ZONE);
            left = number(o, "zoneLeft", zone);
            right = number(o, "zoneRight", zone);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (left < 0 || right < 0 || left + right <= 0) return "needs a zone (zone, zoneLeft, zoneRight): not negative, at least one side used";
        if ((left > 0 && left < MIN_ZONE) || (right > 0 && right < MIN_ZONE))
            return "needs each used zone side to be at least " + (int) MIN_ZONE + " px (zone, zoneLeft, zoneRight; 0 leaves a side unused)";
        String open = openFloorProblem(o, left, right, collisions, spawn, room);
        if (open != null) return open;
        if (entranceBody != null && entranceBody.overlaps(zoneOf(o.x, o.y, o.width, o.height, left, right)))
            return "has its trigger zone over the room entrance (the hero would start triggered)";
        return null;
    }

    /**
     * The shared jump-envelope rule (D44, reused by the explosive traps of D47): the footprint widened by
     * {@code max(side, MIN_ZONE)} on each side must lie in the room, stand on floor over its whole width and keep
     * {@link #HEADROOM} of free air (collision blocks, falling platforms, walls with their travel, ladders,
     * escalators with their travel); nothing solid may stand in the footprint's own column.
     */
    static String openFloorProblem(TiledObject o, float left, float right, List<TiledObject> collisions,
                                   List<TiledObject> spawn, Rectangle room) {
        float floor = o.y + o.height;
        Rectangle footprint = new Rectangle(o.x, o.y, o.width, o.height);
        Rectangle envelope = new Rectangle(o.x - Math.max(left, MIN_ZONE), floor - HEADROOM,
            o.width + Math.max(left, MIN_ZONE) + Math.max(right, MIN_ZONE), HEADROOM);
        if (envelope.x < room.x || envelope.x + envelope.width > room.x + room.width
            || footprint.y < room.y || envelope.y < room.y || floor > room.y + room.height)
            return "needs its whole jump envelope (" + (int) MIN_ZONE + " px of floor either side, " + (int) HEADROOM
                + " px above) inside its room";
        // Floor: the union of non-sand blocks whose top is the floor line must cover the envelope's whole width.
        List<float[]> spans = new java.util.ArrayList<>();
        for (TiledObject c : collisions)
            if (!"QuickSand".equals(c.type) && Math.abs(c.y - floor) < FLOOR_TOLERANCE) spans.add(new float[]{c.x, c.x + c.width});
        spans.sort((a, b) -> Float.compare(a[0], b[0]));
        float covered = envelope.x;
        for (float[] span : spans) if (span[0] <= covered + FLOOR_TOLERANCE) covered = Math.max(covered, span[1]);
        if (covered < envelope.x + envelope.width)
            return "must stand on open floor: floor under its whole footprint and " + (int) MIN_ZONE
                + " px either side (no pit, pillar or ledge edge beside it)";
        Rectangle air = new Rectangle(envelope.x, envelope.y, envelope.width, HEADROOM - 1f);
        for (TiledObject c : collisions) {
            if ("QuickSand".equals(c.type)) continue;
            String clash = clash(footprint, air, new Rectangle(c.x, c.y, c.width, c.height));
            if (clash != null) return clash;
        }
        for (TiledObject c : spawn) {
            Rectangle swept = sweptBounds(c);
            String clash = swept == null ? null : clash(footprint, air, swept);
            if (clash != null) return clash;
        }
        return null;
    }

    private static String clash(Rectangle footprint, Rectangle air, Rectangle thing) {
        if (thing.overlaps(footprint)) return "has another solid object standing in its own column";
        if (thing.overlaps(air)) return "needs " + (int) HEADROOM + " px of free height over it and " + (int) MIN_ZONE
            + " px either side (jumpable, not a one-tile corridor)";
        return null;
    }

    /** Everything a mechanism can occupy while it moves, or null for objects that are not solid. */
    private static Rectangle sweptBounds(TiledObject c) {
        float x = c.x, y = c.y, w = c.width, h = c.height;
        switch (c.type) {
            case "FallingPlatform":
                return new Rectangle(x, y, w, h);
            case "Ladder":
                return new Rectangle(x, y, w, h + Math.max(0f, lenient(c, "dropDistance")));
            case "Escalator": {
                float neg = Math.max(0f, lenient(c, "offNeg")) * ESCALATOR_TILE, pos = Math.max(0f, lenient(c, "offPos")) * ESCALATOR_TILE;
                return "true".equalsIgnoreCase(c.prop("isVertical", ""))
                    ? new Rectangle(x, y - neg, w, h + neg + pos) : new Rectangle(x - neg, y, w + neg + pos, h);
            }
            case "Actionable":
                return "Wall".equals(c.prop("type", "")) ? new Rectangle(x - w, y - h, WALL_SWEEP * w, WALL_SWEEP * h) : null;
            default:
                return null;
        }
    }

    private static float lenient(TiledObject c, String key) {
        String raw = c.prop(key, "").trim();
        return NUMBER.matcher(raw).matches() ? Float.parseFloat(raw) : 0f;
    }
}
