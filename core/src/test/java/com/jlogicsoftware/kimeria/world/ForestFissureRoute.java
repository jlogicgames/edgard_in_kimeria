package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The mastered route of {@code forest-fissure} (#366, D91) with the ordinary controls, one frame at a time: walk to just
 * short of the floor fissure's column and go when a jet has ended; walk on to the foot of the branch and, when the branch
 * fissure's jet has ended, jump on to the branch, through its column and on to the ledge. There the beetle guards the exit
 * (#327, D100): the free answer is the lure (walk at it until it commits, step back on to the ledge's lip out of its blast,
 * let it burst), or with a charge, one fireball from the lip; then walk to the exit. A beetle already gone (a collection
 * return) is walked past. Shared by {@code ForestFissureTest} (offsets, assist) and {@code RoomReplayTest} (D19 times).
 * World Y grows downward.
 */
final class ForestFissureRoute {
    /** The waiting places, in map x of the hero's body front (right edge), and the jump points (the fixture's recipe + 3008 - 640). */
    static final float FLOOR_WAIT_GAP = 12f, BRANCH_WAIT = 3300f, BRANCH_JUMP = 3310f, LEDGE_JUMP = 3380f;
    static final float BRANCH_TOP = 256f, LEDGE_TOP = 208f;
    /** Go when the cycle is this young: a jet has just ended. */
    static final float FLOOR_CUE = 1f, BRANCH_CUE = 0.5f;
    private static final float GROUND_SLACK = 0.5f;
    private final Rectangle blast = new Rectangle();

    private final EmberFissure floor, branch;
    private final Beetle beetle;
    private final boolean fire;
    private boolean floorGo, branchGo, firstJump, secondJump, fired;

    /** The lure: the answer that needs no charge. */
    ForestFissureRoute(Level level) { this(level, false); }

    /** {@code fire}: one fireball from the ledge's lip instead of the lure (the hero must hold a charge). */
    ForestFissureRoute(Level level, boolean fire) {
        List<EmberFissure> fissures = fissures(level);
        floor = fissures.get(0); branch = fissures.get(1);
        beetle = beetle(level);
        this.fire = fire;
    }

    static Beetle beetle(Level level) {
        return level.objects.stream().filter(Beetle.class::isInstance).map(Beetle.class::cast).findFirst().orElse(null);
    }

    /** True while the beetle can still burst: not burst, not killed. */
    boolean beetleAlive() { return beetle != null && !beetle.isRemoved() && !beetle.isDefeated(); }

    static List<EmberFissure> fissures(Level level) {
        return level.objects.stream().filter(EmberFissure.class::isInstance).map(EmberFissure.class::cast)
            .sorted(Comparator.comparingDouble(EmberFissure::getX)).toList();
    }

    private static boolean justEnded(EmberFissure f, float cue) {
        return f.currentPhase() == EmberFissure.Phase.CALM && !f.jetActive() && f.cycleTime() < cue;
    }

    /** Adds this frame's keys to {@code held} (and {@code pressed} for a jump); the caller clears both every frame. */
    void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        Rectangle b = level.player.hitboxBounds();
        float front = b.x + b.width, feet = b.y + b.height;
        boolean ground = level.player.isOnGround();
        if (secondJump && ground && feet <= LEDGE_TOP + GROUND_SLACK && beetleAlive()) {
            if (fire) {
                if (!fired) { pressed.add(Input.Keys.L); fired = true; } // facing right from the jump: the ball flies at it
            } else if (beetle.isCommitted()) {
                if (front >= beetle.blastBoundsInto(blast).x) held.add(Input.Keys.A); // back out of its blast, on to the lip
            } else {
                held.add(Input.Keys.D); // walk at it: it hears, comes to its range end and commits when it reaches him
            }
            return;
        }
        float floorColumn = floor.centerX() - EmberFissure.JET_WIDTH / 2f;
        if (!floorGo && front > floorColumn - FLOOR_WAIT_GAP) floorGo = justEnded(floor, FLOOR_CUE);
        boolean pastFloor = floorGo || front <= floorColumn - FLOOR_WAIT_GAP;
        if (!branchGo && front >= BRANCH_WAIT) branchGo = justEnded(branch, BRANCH_CUE);
        if (pastFloor && (branchGo || front < BRANCH_WAIT)) held.add(Input.Keys.D);
        if (branchGo && !firstJump && ground && front >= BRANCH_JUMP) {
            held.add(Input.Keys.J); pressed.add(Input.Keys.J); firstJump = true;
        } else if (firstJump && !secondJump && ground && feet <= BRANCH_TOP + GROUND_SLACK && front >= LEDGE_JUMP) {
            held.add(Input.Keys.J); pressed.add(Input.Keys.J); secondJump = true;
        } else if (firstJump && !ground) {
            held.add(Input.Keys.J); // a full jump: held while airborne, released on landing
        }
    }
}
