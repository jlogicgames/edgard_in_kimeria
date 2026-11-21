package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;

import java.util.Set;

/**
 * The mastered route of {@code forest-walls} (#545, D116) with the ordinary controls, one frame at a time and no wall jump: climb the
 * three branches of the tree in front of the first wall (A1 x 880-912 y 272, A2 x 832-864 y 224, A3 x 880-912 y 176), then leap from
 * A3's top branch across the first wall (x 912-928) on to the second wall's top (x 992-1008, y 224), where the Bat lies; walk off its
 * end on to the YellowMob (a stomp) and on to the RedMob and the exit. A second route climbs out of the gap between the walls by tree B
 * (branch B1 x 944-976 y 272). Shared by {@code ForestWallsTest} and {@code RoomReplayTest}. World Y grows downward; positions are
 * the body's centre x and its feet y (the body is 11 x 22 px).
 */
final class ForestWallsRoute {
    /** Where the hero leaves the floor for A1, A1 for A2, A2 for A3 (body centre x), and where the leap leaves A3. */
    static final float FLOOR_JUMP = 845f, A1_JUMP = 884f, A2_JUMP = 858f, LEAP = 896f;
    /** Where he steers to in the air for A1, A2, A3 (body centre x). */
    static final float A1_LAND = 896f, A2_LAND = 848f, A3_LAND = 896f;
    /** The platform tops: A1, A2, A3, wall 2, and B1. */
    static final float A1_TOP = 272f, A2_TOP = 224f, A3_TOP = 176f, WALL2_TOP = 224f, B1_TOP = 272f;
    private static final float SLACK = 0.5f;

    private final boolean fromGap;
    private final float leap;
    private int step;
    private boolean airborne;

    /** {@code fromGap}: the hero stands in the gap between the walls and climbs out by tree B instead. */
    ForestWallsRoute(boolean fromGap) { this(fromGap, LEAP); }

    /** {@code leap}: where the leap leaves A3 (body centre x): a late one (above x 908) overshoots the second wall. */
    ForestWallsRoute(boolean fromGap, float leap) { this.fromGap = fromGap; this.leap = leap; step = fromGap ? 10 : 0; }

    int step() { return step; }

    /** True once the hero has stood on the second wall's top. */
    boolean onWall2() { return step == 4; }

    /** Adds this frame's keys to {@code held} (and {@code pressed} for a jump); the caller clears both every frame. */
    void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        Rectangle b = level.player.hitboxBounds();
        float cx = b.x + b.width / 2, feet = b.y + b.height;
        boolean ground = level.player.isOnGround();
        switch (step) {
            case 0 -> go(held, pressed, ground, cx, feet, FLOOR_JUMP, A1_LAND, A1_TOP, false);
            case 1 -> go(held, pressed, ground, cx, feet, A1_JUMP, A2_LAND, A2_TOP, false);
            case 2 -> go(held, pressed, ground, cx, feet, A2_JUMP, A3_LAND, A3_TOP, false);
            case 3 -> go(held, pressed, ground, cx, feet, leap, Float.NaN, WALL2_TOP, true);
            case 10 -> go(held, pressed, ground, cx, feet, 936f, 960f, B1_TOP, false);
            case 11 -> go(held, pressed, ground, cx, feet, 972f, 1000f, WALL2_TOP, false);
            default -> held.add(Input.Keys.D);
        }
        if (step == 3 && airborne && feet <= WALL2_TOP + SLACK && ground && cx >= 992 && cx <= 1008) step = 4;
        if (step == 11 && airborne && ground && Math.abs(feet - WALL2_TOP) <= SLACK) step = 4;
    }

    /**
     * One hop: walk to {@code jumpX}, jump there, steer to {@code landX} in the air (NaN: keep going right) and finish when he stands
     * on {@code top}. {@code leap}: the last hop, which ends on the second wall's top.
     */
    private void go(Set<Integer> held, Set<Integer> pressed, boolean ground, float cx, float feet, float jumpX, float landX, float top, boolean leap) {
        boolean right = !(step == 1);
        if (!airborne) {
            if (ground && (right ? cx >= jumpX : cx <= jumpX)) {
                held.add(Input.Keys.J); pressed.add(Input.Keys.J); airborne = true;
                if (step != 11) steer(held, cx, landX, right);
            } else held.add(right ? Input.Keys.D : Input.Keys.A);
            return;
        }
        if (ground && Math.abs(feet - top) <= SLACK && !(step == 3 || step == 11)) { airborne = false; step++; return; }
        // Never touch a wall face in the air (it would be a wall grab): hold toward the second wall only once the feet are above its top.
        if (step == 11 && feet > WALL2_TOP) return;
        steer(held, cx, landX, right);
    }

    private static void steer(Set<Integer> held, float cx, float landX, boolean right) {
        if (Float.isNaN(landX)) { held.add(Input.Keys.D); return; }
        if (right ? cx < landX : cx > landX) held.add(right ? Input.Keys.D : Input.Keys.A);
    }
}
