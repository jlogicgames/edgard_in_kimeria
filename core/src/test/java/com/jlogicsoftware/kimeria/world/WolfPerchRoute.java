package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;

import java.util.Set;

/**
 * The way past a wolf from its room's perch (D165) with the ordinary controls and no ammunition, one frame at a time: reach the
 * perch, wait there until the wolf bays, step off, stop in the air when it leaps at where he was going, then finish it with the sword while
 * it recovers (with the longer-warnings assist it does not leap: he swings as its lunge comes), and walk on to the exit. The room's
 * subclass gives the way: its jumps and where its perch is. A {@code careless} hero ({@code patient} false) only walks forward,
 * making the room's jumps and stopping on the perch for {@code pause} frames: never the route.
 */
abstract class WolfPerchRoute {
    /** He strikes a recovering wolf from this near (px, body edge to body edge: the blade reaches 17 px past his). */
    private static final float STRIKE_GAP = 10;
    /** A wind-up with this much of it left is struck now: the 0.7 s swing is then live when the jaws arrive. */
    private static final float STRIKE_AT_SECONDS = .35f;
    /** Both rooms' floor under the perch (a larger Y than the perch's top). */
    static final float FLOOR = 304, GROUND_SLACK = .5f;

    private enum Phase { APPROACH, WAIT, DROP, FIGHT }

    private final Wolf wolf;
    private final boolean patient;
    private int pause;
    private Phase phase = Phase.APPROACH;

    WolfPerchRoute(Level level, boolean patient, int pause) {
        this.patient = patient;
        this.pause = pause;
        wolf = level.objects.stream().filter(Wolf.class::isInstance).map(Wolf.class::cast).findFirst().orElse(null);
    }

    /** True while the wolf can still bite: not killed. */
    boolean wolfAlive() { return wolf != null && !wolf.isRemoved() && !wolf.isDefeated(); }

    /** True when the hero stands on the room's perch, far enough on to it to stop there. */
    abstract boolean onPerch(Level level);

    /** The room's jumps for a hero walking right; true on the frame he presses jump. */
    abstract boolean jumps(Level level);

    /** Adds this frame's keys to {@code held} (and {@code pressed} for a jump or a swing); the caller clears both every frame. */
    void drive(Level level, Set<Integer> held, Set<Integer> pressed) {
        if (!patient) {
            if (pause > 0 && onPerch(level)) { pause--; return; }
            walk(level, held, pressed);
            return;
        }
        if (!wolfAlive()) { walk(level, held, pressed); return; }
        if (phase == Phase.APPROACH && onPerch(level)) phase = Phase.WAIT;
        if (phase == Phase.WAIT) {
            if (wolf.state() != Wolf.State.BAY) return;
            phase = Phase.DROP;
        }
        if (phase == Phase.DROP && (pouncing() || onFeet(level, FLOOR))) phase = Phase.FIGHT;
        if (phase == Phase.FIGHT) fight(level, held, pressed);
        else walk(level, held, pressed);
    }

    abstract float perchTop();

    static boolean onFeet(Level level, float top) {
        Rectangle b = level.player.hitboxBounds();
        return level.player.isOnGround() && Math.abs(b.y + b.height - top) <= GROUND_SLACK;
    }

    private boolean pouncing() { return wolf.state() == Wolf.State.POUNCE_COIL || wolf.state() == Wolf.State.POUNCE; }

    private void walk(Level level, Set<Integer> held, Set<Integer> pressed) {
        held.add(Input.Keys.D);
        if (jumps(level)) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
    }

    private void fight(Level level, Set<Integer> held, Set<Integer> pressed) {
        Rectangle hero = level.player.hitboxBounds(), body = wolf.hitboxBounds();
        boolean right = body.x + body.width / 2 > hero.x + hero.width / 2;
        int toward = right ? Input.Keys.D : Input.Keys.A;
        if (pouncing()) return; // stop in the air: the arc was locked ahead of him
        if (level.player.isSwinging() || !level.player.isOnGround()) return;
        Wolf.State state = wolf.state();
        boolean recovering = state == Wolf.State.RECOVER;
        boolean coming = state == Wolf.State.WINDUP && wolf.warningRemaining() <= STRIKE_AT_SECONDS;
        if (state == Wolf.State.LUNGE || state == Wolf.State.BITE) return;
        if (level.player.facingRight != right) { held.add(toward); return; }
        float gap = right ? body.x - (hero.x + hero.width) : hero.x - (body.x + body.width);
        if (coming || recovering && gap <= STRIKE_GAP) { held.add(Input.Keys.K); pressed.add(Input.Keys.K); }
        else if (recovering) held.add(toward);
    }
}
