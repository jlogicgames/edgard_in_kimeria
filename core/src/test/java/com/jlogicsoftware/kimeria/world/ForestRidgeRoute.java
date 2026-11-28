package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;

import java.util.Set;

/**
 * The mastered route of {@code forest-ridge} (#525, D158, D165): jump the first thorn root, jump on to the one-way ledge and wait
 * there while the wolf comes down its mounds, leaping trench and step, notices him, stands back and bays; step off the ledge's far
 * end, stop in the air as it leaps, and finish it with the sword ({@link WolfPerchRoute}); then cross the two mounds and the
 * trench, jump the thorn tuft and the second root, and walk to the exit. World Y grows downward; the take-offs and windows below
 * are measured from the room's left edge.
 */
final class ForestRidgeRoute extends WolfPerchRoute {
    static final float ROOM_X = 5952, LEDGE_TOP = 272, MOUND_TOP = 288;
    /** The ledge, and the first mound's left edge. */
    static final float LEDGE_LEFT = 6144, LEDGE_RIGHT = 6208, MOUND_LEFT = 6384;
    /** Take-offs: the first root, the ledge, the thorn tuft, the second root. */
    private static final float ROOT_JUMP = 54, LEDGE_JUMP = 135, TUFT_JUMP = 590, ROOT2_JUMP = 708;
    /**
     * The body's front edge, room-relative, from which he jumps up the first mound and out of the trench; each window ends past
     * the face, so a hero who has walked up against it jumps too.
     */
    private static final float MOUND_FROM = 416, MOUND_TO = 432.5f, TRENCH_FROM = 516, TRENCH_TO = 528.5f;

    private boolean rootJumped, ledgeJumped, tuftJumped, root2Jumped, backing, down;
    /** The back way: frames still to stand on the ledge and then on the floor; {@code floorWait} negative: he goes the front way. */
    private int ledgeWait, floorWait = -1;

    ForestRidgeRoute(Level level) { super(level, true, 0); }

    private ForestRidgeRoute(Level level, int pause) { super(level, false, pause); }

    /** A hero who only walks forward and stops on the ledge for {@code pause} frames (never the route). */
    static ForestRidgeRoute careless(Level level, int pause) { return new ForestRidgeRoute(level, pause); }

    /**
     * A careless hero who leaves the ledge by its back (left) end: after {@code ledgeWait} frames on it he walks off to the left,
     * stands on the floor where he lands for {@code floorWait} frames, then walks forward under the ledge and on (never the route).
     */
    static ForestRidgeRoute backWay(Level level, int ledgeWait, int floorWait) {
        ForestRidgeRoute route = careless(level, 0);
        route.ledgeWait = ledgeWait;
        route.floorWait = floorWait;
        return route;
    }

    @Override void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        if (floorWait >= 0 && !down) {
            backing |= onPerch(level);
            if (backing) {
                if (ledgeWait > 0) { ledgeWait--; return; }
                Rectangle b = level.player.hitboxBounds();
                if (b.x + b.width > LEDGE_LEFT) { held.add(Input.Keys.A); return; }
                if (!onFeet(level, FLOOR)) return;
                down = true;
            }
        }
        if (down && floorWait > 0) { floorWait--; return; }
        super.drive(level, held, pressed);
    }

    @Override float perchTop() { return LEDGE_TOP; }

    @Override boolean onPerch(Level level) { return onFeet(level, LEDGE_TOP); }

    @Override boolean jumps(Level level) {
        if (!level.player.isOnGround()) return false;
        Rectangle b = level.player.hitboxBounds();
        float x = level.player.position.x - ROOM_X, front = b.x + b.width - ROOM_X;
        if (!rootJumped && x >= ROOT_JUMP) return rootJumped = true;
        if (rootJumped && !ledgeJumped && x >= LEDGE_JUMP) return ledgeJumped = true;
        if (!ledgeJumped || !onFeet(level, FLOOR)) return false;
        if (front > MOUND_FROM && front <= MOUND_TO || front > TRENCH_FROM && front <= TRENCH_TO) return true;
        if (!tuftJumped && x >= TUFT_JUMP) return tuftJumped = true;
        if (tuftJumped && !root2Jumped && x >= ROOT2_JUMP) return root2Jumped = true;
        return false;
    }
}
