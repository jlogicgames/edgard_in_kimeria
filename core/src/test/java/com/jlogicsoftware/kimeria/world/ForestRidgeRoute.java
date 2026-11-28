package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;

import java.util.Set;

/**
 * The mastered route of {@code forest-ridge} (#525, D158) with the ordinary controls, one frame at a time: jump the first thorn
 * root, jump on to the high one-way ledge and wait there while the wolf trots its mounds, leaping step and trench, notices him,
 * leaps down to its first leg and bays; walk off the ledge's far end on to its back (a stomp on a bayed wolf), cross the two
 * mounds and the trench, jump the thorn tuft and the second root, and walk to the exit. World Y grows downward; every x below is
 * the hero frame's, measured from the room's left edge.
 */
final class ForestRidgeRoute {
    static final float ROOM_X = 5952, LEDGE_TOP = 256, FLOOR = 304, MOUND_TOP = 288;
    /** Take-offs: the first root, the ledge, the thorn tuft, the second root. */
    private static final float ROOT_JUMP = 86, LEDGE_JUMP = 215, TUFT_JUMP = 566, ROOT2_JUMP = 684;
    /** The body's front edge, room-relative, from which he jumps up the first mound and out of the trench. */
    private static final float MOUND_FROM = 384, MOUND_TO = 396, TRENCH_FROM = 484, TRENCH_TO = 496.5f;
    private static final float WAIT_BEFORE = 300, GROUND_SLACK = .5f;

    private final Wolf wolf;
    private final boolean patient;
    private boolean rootJumped, ledgeJumped, tuftJumped, root2Jumped;

    ForestRidgeRoute(Level level) { this(level, true); }

    /** {@code patient} false: the hero does not wait on the ledge for the wolf to bay (never the route). */
    ForestRidgeRoute(Level level, boolean patient) {
        this.patient = patient;
        wolf = level.objects.stream().filter(Wolf.class::isInstance).map(Wolf.class::cast).findFirst().orElse(null);
    }

    private boolean wolfAlive() { return wolf != null && !wolf.isRemoved() && !wolf.isDefeated(); }

    /** Adds this frame's keys to {@code held} (and {@code pressed} for a jump); the caller clears both every frame. */
    void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        Rectangle b = level.player.hitboxBounds();
        float x = level.player.position.x - ROOM_X, front = b.x + b.width - ROOM_X, feet = b.y + b.height;
        boolean ground = level.player.isOnGround();
        boolean onLedge = ground && Math.abs(feet - LEDGE_TOP) <= GROUND_SLACK;
        if (patient && onLedge && wolfAlive() && wolf.state() != Wolf.State.BAY && x < WAIT_BEFORE) return;
        held.add(Input.Keys.D);
        if (!ground) return;
        boolean jump = false;
        if (!rootJumped && x >= ROOT_JUMP) jump = rootJumped = true;
        else if (rootJumped && !ledgeJumped && x >= LEDGE_JUMP) jump = ledgeJumped = true;
        else if (ledgeJumped && Math.abs(feet - FLOOR) <= GROUND_SLACK
            && (front > MOUND_FROM && front < MOUND_TO || front > TRENCH_FROM && front <= TRENCH_TO)) jump = true;
        else if (ledgeJumped && !tuftJumped && x >= TUFT_JUMP && Math.abs(feet - FLOOR) <= GROUND_SLACK) jump = tuftJumped = true;
        else if (tuftJumped && !root2Jumped && x >= ROOT2_JUMP) jump = root2Jumped = true;
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
    }
}
