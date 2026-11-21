package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;

import java.util.Set;

/**
 * The mastered route of {@code forest-glade} (#331, D115) with the ordinary controls, one frame at a time: jump the thorn root,
 * jump on to the trunk and walk out on the one-way branch, where the trunk's face no longer cuts the wolf's sight; wait there while it
 * notices him, sprints under the branch and bays; then walk on along the branch and step off its far end on to the wolf's back
 * (a stomp kills it with the bounce, the wolf card's "reward for going up"); then walk on, jump the second thorn root and walk to the exit.
 * Shared by {@code ForestGladeTest} (the answers, the assist) and {@code RoomReplayTest} (D19 time). World Y grows downward.
 */
final class ForestGladeRoute {
    /** Hero frame x (the body's left edge is 18 px right of it) of the two ground take-offs: the thorn root, the trunk. */
    static final float ROOT_JUMP = 3750f, TRUNK_JUMP = 3880f;
    /** Where the second thorn root (x 4360-4376) is jumped (hero frame x: the body's right edge 29 px on is 7 to 63 px short of the column). */
    static final float ROOT2_JUMP = 4290f;
    /** The tops of the trunk and the branch, and the slack a body on them is measured with. */
    static final float BRANCH_TOP = 288f;
    private static final float GROUND_SLACK = 0.5f, BRANCH_FROM = 3952f;

    private final Wolf wolf;
    private final boolean patient, jumpsSecondRoot;
    private boolean rootJumped, trunkJumped, root2Jumped;

    ForestGladeRoute(Level level) { this(level, true, true); }

    /** {@code patient} false: the hero does not wait for the wolf to bay and walks straight on over the branch (a death, never the route). */
    ForestGladeRoute(Level level, boolean patient) { this(level, patient, true); }

    /** {@code jumpsSecondRoot} false: the hero walks straight into the second thorn root (a death, never the route). */
    ForestGladeRoute(Level level, boolean patient, boolean jumpsSecondRoot) {
        this.patient = patient;
        this.jumpsSecondRoot = jumpsSecondRoot;
        wolf = level.objects.stream().filter(Wolf.class::isInstance).map(Wolf.class::cast).findFirst().orElse(null);
    }

    /** True while the wolf can still bite: not killed. */
    boolean wolfAlive() { return wolf != null && !wolf.isRemoved() && !wolf.isDefeated(); }

    /** Adds this frame's keys to {@code held} (and {@code pressed} for a jump); the caller clears both every frame. */
    void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        Rectangle b = level.player.hitboxBounds();
        float front = b.x + b.width, feet = b.y + b.height, x = level.player.position.x;
        boolean ground = level.player.isOnGround();
        boolean onBranch = ground && Math.abs(feet - BRANCH_TOP) <= GROUND_SLACK && front > BRANCH_FROM + 2;
        if (patient && onBranch && wolfAlive() && wolf.state() != Wolf.State.BAY && x < 4010) return; // wait: it notices, sprints under him and bays
        held.add(Input.Keys.D);
        if (!rootJumped && x >= ROOT_JUMP && ground) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); rootJumped = true; }
        else if (rootJumped && !trunkJumped && x >= TRUNK_JUMP && ground) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); trunkJumped = true; }
        else if (jumpsSecondRoot && trunkJumped && !wolfAlive() && !root2Jumped && x >= ROOT2_JUMP && ground) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); root2Jumped = true; }
    }
}
