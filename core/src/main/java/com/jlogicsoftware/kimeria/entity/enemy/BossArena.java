package com.jlogicsoftware.kimeria.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.FloatArray;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * Where the forest boss's special attacks may reach (#625, D157), worked out from the room once, at load: the floor line, the
 * walls, the two protected spans no special's lethal area may overlap (D59: the hero's spawn frame at the entrance and the
 * place of the level's exit portal) and the ceilings the rocks start under. World Y grows downward.
 */
public final class BossArena {
    /** Authored floor and wall edges are compared with this slack (Tiled may write 303.9999). */
    private static final float TOLERANCE = .5f;
    private static final float HALF = .5f;
    /** A solid this close over the floor would stand in a root or the blow; one this high leaves a rock room to be seen falling. */
    private static final float CLEAR_HEIGHT = BossAttacks.ROOT_HEIGHT;
    /** A ceiling is stored as its left, right and bottom edges. */
    private static final int CEILING_STRIDE = 3, CEILING_RIGHT = 1, CEILING_BOTTOM = 2;

    private final float floor, left, right, entranceLeft, entranceRight, exitLeft, exitRight, roomTop;
    /** Every solid above the clear band, as (left, right, bottom) triples. */
    private final FloatArray ceilings = new FloatArray();

    private BossArena(float floor, float left, float right, float entranceLeft, float entranceRight, float exitLeft, float exitRight,
                      float roomTop) {
        this.floor = floor;
        this.left = left;
        this.right = right;
        this.entranceLeft = entranceLeft;
        this.entranceRight = entranceRight;
        this.exitLeft = exitLeft;
        this.exitRight = exitRight;
        this.roomTop = roomTop;
    }

    /**
     * Builds the arena of a boss whose feet stand on {@code floor}, or fails with the reason. A solid in the band
     * {@value BossAttacks#ROOT_HEIGHT} px over the floor is a wall only when it touches a side of the room; the floor must be
     * flat solid ground from wall to wall; and the space between the two protected spans must hold the blow.
     *
     * @param spawnX     the left edge of the hero's spawn frame at the entrance
     * @param spawnWidth that frame's width
     * @param exitX      the left edge of the exit portal's place
     * @param exitWidth  its width
     * @param collisions every object of the map's Collisions layer; those outside the room are ignored
     */
    public static BossArena of(Rectangle room, float floor, float spawnX, float spawnWidth, float exitX, float exitWidth,
                               Iterable<TiledObject> collisions) {
        float roomRight = room.x + room.width, left = room.x, right = roomRight;
        for (TiledObject c : collisions) {
            if (!solid(c) || !overlaps(c, room.x, roomRight) || c.y >= floor - TOLERANCE || c.y + c.height <= floor - CLEAR_HEIGHT) continue;
            if (c.x <= room.x + TOLERANCE) left = Math.max(left, c.x + c.width);
            else if (c.x + c.width >= roomRight - TOLERANCE) right = Math.min(right, c.x);
            else throw new IllegalArgumentException("Boss arena: a solid block [" + c.id + "] stands in the arena, x " + c.x);
        }
        BossArena arena = new BossArena(floor, left, right, spawnX, spawnX + spawnWidth, exitX, exitX + exitWidth, room.y);
        float covered = left;
        boolean progress = true;
        while (covered < right - TOLERANCE && progress) {
            progress = false;
            for (TiledObject c : collisions)
                if (solid(c) && Math.abs(c.y - floor) <= TOLERANCE && c.x <= covered + TOLERANCE && c.x + c.width > covered + TOLERANCE) {
                    covered = c.x + c.width;
                    progress = true;
                }
        }
        if (covered < right - TOLERANCE)
            throw new IllegalArgumentException("Boss arena: no flat solid floor at y " + floor + " from x " + covered + " to " + right);
        if (arena.exitLeft - arena.entranceRight < BeetleFx.BLAST_WIDTH)
            throw new IllegalArgumentException("Boss arena: less than " + (int) BeetleFx.BLAST_WIDTH + " px between the entrance and the exit");
        for (TiledObject c : collisions)
            if (solid(c) && overlaps(c, room.x, roomRight) && c.y + c.height <= floor - CLEAR_HEIGHT && c.y + c.height > room.y) {
                arena.ceilings.add(c.x);
                arena.ceilings.add(c.x + c.width);
                arena.ceilings.add(c.y + c.height);
            }
        return arena;
    }

    /** A block that stops a body from every side: not a one-way platform and not quicksand. */
    private static boolean solid(TiledObject c) { return !"Platform".equals(c.type) && !"QuickSand".equals(c.type); }

    private static boolean overlaps(TiledObject c, float from, float to) { return c.x < to && c.x + c.width > from; }

    public float floor() { return floor; }
    public float left() { return left; }
    public float right() { return right; }
    public float entranceLeft() { return entranceLeft; }
    public float entranceRight() { return entranceRight; }
    public float exitLeft() { return exitLeft; }
    public float exitRight() { return exitRight; }

    /** The bottom of the lowest ceiling over the columns from {@code from} to {@code to}, or the room's top. */
    public float ceilingOver(float from, float to) {
        float lowest = roomTop;
        for (int i = 0; i < ceilings.size; i += CEILING_STRIDE)
            if (ceilings.get(i) < to && ceilings.get(i + CEILING_RIGHT) > from)
                lowest = Math.max(lowest, ceilings.get(i + CEILING_BOTTOM));
        return lowest;
    }

    /** True when the span {@code halfWidth} px to either side of {@code centre} stays between the walls and off both spans. */
    public boolean clear(float centre, float halfWidth) {
        float from = centre - halfWidth, to = centre + halfWidth;
        return from >= left && to <= right && (to <= entranceLeft || from >= entranceRight) && (to <= exitLeft || from >= exitRight);
    }

    /**
     * The clear centre nearest to {@code wanted} for something {@code halfWidth} px to either side of it: inside the walls
     * and beside a protected span it would overlap. {@link Float#NaN} when there is none.
     */
    public float nearestClear(float wanted, float halfWidth) {
        float inside = Math.max(left + halfWidth, Math.min(right - halfWidth, wanted));
        if (clear(inside, halfWidth)) return inside;
        float best = nearer(wanted, Float.NaN, entranceLeft - halfWidth, halfWidth);
        best = nearer(wanted, best, entranceRight + halfWidth, halfWidth);
        best = nearer(wanted, best, exitLeft - halfWidth, halfWidth);
        return nearer(wanted, best, exitRight + halfWidth, halfWidth);
    }

    private float nearer(float wanted, float best, float candidate, float halfWidth) {
        if (!clear(candidate, halfWidth)) return best;
        return Float.isNaN(best) || Math.abs(candidate - wanted) < Math.abs(best - wanted) ? candidate : best;
    }

    /** The middle of the clear ground, for tests. */
    public float middle() { return (entranceRight + exitLeft) * HALF; }
}
