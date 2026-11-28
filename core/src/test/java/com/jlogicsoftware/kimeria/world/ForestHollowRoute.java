package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.MosquitoSwarm;

import java.util.Set;

/**
 * The mastered route of {@code forest-hollow} (#645, D151) with the ordinary controls, one frame at a time, with no ammunition and
 * the stone left where it lies: wait short of the low swarm's line until it comes towards the hero, jump it head on, walk on, jump
 * the thorn root, wait short of the rising swarm until it is high enough, and walk under it to the exit. On a pass where the swarms
 * are gone it walks straight through. Shared by {@code ForestHollowTest} and {@code RoomReplayTest} (D19 time). World Y grows
 * downward.
 */
final class ForestHollowRoute {
    /** Hero frame x of the take-off over the thorn root (the body's left edge is 18 px right of it). */
    static final float ROOT_JUMP = 5574f;
    /**
     * Where the hero waits for the low swarm (his body's right edge, short of its line from x 5408), and where the swarm's left
     * edge is, coming towards him, when he jumps.
     */
    static final float LOW_WAIT = 5386f, TAKE_OFF_FROM = 5420f, TAKE_OFF_TO = 5450f;
    /** Where he waits for the high swarm, and the swarm's top at or above which he walks under it while it still rises. */
    static final float HIGH_WAIT = 5852f, HIGH_ENOUGH = 262f;

    private final MosquitoSwarm low, high;
    private boolean lowJumped, rootJumped, passingHigh;
    private float lowBefore = Float.NaN, highBefore = Float.NaN;

    ForestHollowRoute(Level level) {
        MosquitoSwarm first = null, second = null;
        for (var o : level.objects) if (o instanceof MosquitoSwarm s) {
            if (s.isVertical) second = s;
            else first = s;
        }
        low = first;
        high = second;
    }

    private static boolean alive(MosquitoSwarm s) { return s != null && !s.isRemoved() && !s.isDefeated(); }

    /** Adds this frame's keys to {@code held} (and {@code pressed} for a jump); the caller clears both every frame. */
    void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        Rectangle b = level.player.hitboxBounds();
        float front = b.x + b.width, x = level.player.position.x;
        boolean ground = level.player.isOnGround();
        boolean coming = alive(low) && low.position.x < lowBefore;
        boolean rising = alive(high) && high.position.y < highBefore;
        if (alive(low)) lowBefore = low.position.x;
        if (alive(high)) highBefore = high.position.y;
        if (alive(low) && !lowJumped && front >= LOW_WAIT) {
            if (!(ground && coming && low.position.x >= TAKE_OFF_FROM && low.position.x <= TAKE_OFF_TO)) return; // wait for it to come
            held.add(Input.Keys.J); pressed.add(Input.Keys.J); lowJumped = true;
        }
        if (alive(high) && !passingHigh && front >= HIGH_WAIT) {
            if (!(rising && high.position.y <= HIGH_ENOUGH)) return; // wait until it is high and still going up
            passingHigh = true;
        }
        held.add(Input.Keys.D);
        if (!rootJumped && x >= ROOT_JUMP && ground) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); rootJumped = true; }
    }
}
