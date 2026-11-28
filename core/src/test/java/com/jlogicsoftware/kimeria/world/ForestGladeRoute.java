package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;

/**
 * The mastered route of {@code forest-glade} (#331, D115, D165): jump the thorn root, jump on to the trunk and walk out on the
 * one-way branch, where the trunk's face no longer cuts the wolf's sight; wait there while it notices him, sprints up, stands back
 * and bays; step off the branch's end, stop in the air as it leaps, and finish it with the sword ({@link WolfPerchRoute}); then walk
 * on, jump the second thorn root and walk to the exit. Shared by {@code ForestGladeTest} (the answers, the assist) and
 * {@code RoomReplayTest} (D19 time). World Y grows downward.
 */
final class ForestGladeRoute extends WolfPerchRoute {
    /** Hero frame x (the body's left edge is 18 px right of it) of the two ground take-offs: the thorn root, the trunk. */
    static final float ROOT_JUMP = 3750f, TRUNK_JUMP = 3880f;
    /** Where the second thorn root (x 4360-4376) is jumped (hero frame x: the body's right edge 29 px on is 7 to 63 px short of the column). */
    static final float ROOT2_JUMP = 4290f;
    /** The tops of the trunk and the branch. */
    static final float BRANCH_TOP = 272f;
    private static final float BRANCH_FROM = 3952f;

    private final boolean jumpsSecondRoot;
    private boolean rootJumped, trunkJumped, root2Jumped;

    ForestGladeRoute(Level level) { this(level, true, 0, true); }

    /** {@code jumpsSecondRoot} false: the hero walks straight into the second thorn root (a death, never the route). */
    ForestGladeRoute(Level level, boolean jumpsSecondRoot) { this(level, true, 0, jumpsSecondRoot); }

    private ForestGladeRoute(Level level, boolean patient, int pause, boolean jumpsSecondRoot) {
        super(level, patient, pause);
        this.jumpsSecondRoot = jumpsSecondRoot;
    }

    /** A hero who only walks forward and stops on the branch for {@code pause} frames (never the route). */
    static ForestGladeRoute careless(Level level, int pause) { return new ForestGladeRoute(level, false, pause, true); }

    @Override float perchTop() { return BRANCH_TOP; }

    @Override boolean onPerch(Level level) {
        Rectangle b = level.player.hitboxBounds();
        return onFeet(level, BRANCH_TOP) && b.x + b.width > BRANCH_FROM + 2;
    }

    @Override boolean jumps(Level level) {
        float x = level.player.position.x;
        if (!level.player.isOnGround()) return false;
        if (!rootJumped && x >= ROOT_JUMP) return rootJumped = true;
        if (rootJumped && !trunkJumped && x >= TRUNK_JUMP) return trunkJumped = true;
        if (jumpsSecondRoot && trunkJumped && !root2Jumped && x >= ROOT2_JUMP) return root2Jumped = true;
        return false;
    }
}
