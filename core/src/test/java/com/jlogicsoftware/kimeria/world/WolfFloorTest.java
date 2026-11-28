package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wolf never comes to rest off its floor (#525, D165), through the real maps of both wolf rooms: a jump bite whose arc ends
 * above a one-way perch passes down through it, and so does a body killed in the air. World Y grows downward: the floor top is
 * y 304, the ridge's mounds y 288, both perches y 272.
 */
class WolfFloorTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 304, MOUND = 288, PERCH = 272, SLACK = .5f;
    private static final int SECOND = 60;
    /** The hero's frame x is his body's left edge less this; his frame's height. */
    private static final float BODY_LEFT = 18, HERO_FRAME = 48;
    /** Each room's sweep starts him standing at these body x, left edge: from its perch to past the near end of it. */
    private static final float GLADE_FROM = 3960, RIDGE_FROM = 6150, SPAN = 128, X_STEP = 4;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    /** The floor line of the wolf's leg: the glade has one floor; the ridge's two upper legs are its mounds. */
    private static float line(String room, Wolf w) { return room.equals("forest-glade") || w.leg() == 0 ? FLOOR : MOUND; }

    /** The scripted hero and what the wolf did before: the frames of its first wind-up and recovery, of his second jump. */
    private final class Script {
        final String room;
        final Level level;
        final Wolf wolf;
        final int jumpAt, back;
        int frame, windup = -1, recovery = -1, jumped = -1;
        private boolean released;
        private float lastY;
        private Wolf.State last;

        Script(String room, float x, int jumpAt, int back) {
            this.room = room;
            this.jumpAt = jumpAt;
            this.back = back;
            level = h.room("forest-2", room);
            wolf = WolfRoomRuns.wolf(level);
            level.player.position.set(x - BODY_LEFT, FLOOR - HERO_FRAME);
            level.player.velocity().setZero();
            level.player.setOnGround(true);
            lastY = wolf.position.y;
            last = wolf.state();
        }

        /**
         * He stands, jumps {@code jumpAt} frames into its first wind-up, jumps again toward the perch (left) {@code back} frames
         * into its recovery, lets go as it gathers or once he lands, and stands. Then the floor rule is checked.
         */
        void step() {
            h.held.clear(); h.pressed.clear();
            if (windup >= 0 && frame == windup + jumpAt) jump();
            if (recovery >= 0 && frame >= recovery + back && !released) {
                h.held.add(Input.Keys.A);
                if (jumped < 0) { jumped = frame; jump(); }
            }
            level.update(STEP);
            Wolf.State state = wolf.state();
            if (windup < 0 && state == Wolf.State.WINDUP) windup = frame;
            if (windup >= 0 && recovery < 0 && state == Wolf.State.RECOVER) recovery = frame;
            released |= jumped >= 0 && (state == Wolf.State.POUNCE_COIL || frame > jumped + 5 && level.player.isOnGround());
            assertOnItsFloor(state);
            lastY = wolf.position.y;
            last = state;
            frame++;
        }

        private void jump() { h.held.add(Input.Keys.J); h.pressed.add(Input.Keys.J); }

        /**
         * On its feet it stands on its leg's floor line; biting, recovering or dead it may be in the air, but where it holds still
         * from one frame to the next it is on that line too.
         */
        private void assertOnItsFloor(Wolf.State state) {
            if (wolf.isRemoved() || inFlight(state) || inFlight(last)) return;
            boolean mayFall = state == Wolf.State.BITE || state == Wolf.State.RECOVER || state == Wolf.State.DEAD || state == Wolf.State.BURN;
            if (mayFall && Math.abs(wolf.position.y - lastY) > .001f) return;
            assertEquals(line(room, wolf), wolf.floorY(), SLACK,
                () -> room + ": off its floor in " + state + " at x " + wolf.position.x + ", frame " + frame + ", jumps " + jumpAt + " and " + back);
        }

        private boolean inFlight(Wolf.State state) { return state == Wolf.State.POUNCE || state == Wolf.State.LEAP; }

        boolean heroOnThePerch() {
            Rectangle hero = level.player.hitboxBounds();
            return level.player.isOnGround() && Math.abs(hero.y + hero.height - PERCH) < SLACK;
        }
    }

    /**
     * The case seen in the running game: on the ridge floor at body x 6220 he jumps the first lunge, 0.37 s into its recovery jumps
     * back left on to the ledge, lets go as it coils and stands there. Its bite passes under him: it lands on the floor, never winds
     * up at him from the ledge, and gives him up.
     */
    @Test void aJumpBiteOverTheLedgeComesDownToTheFloorAndItDoesNotAttackFromTheLedge() {
        Script s = new Script("forest-ridge", 6220, 18, 22);
        boolean pounced = false, gaveUp = false;
        int onLedge = 0;
        while (s.frame < SECOND * 20 && !gaveUp) {
            s.step();
            assertFalse(s.level.player.isGotHit(), "never bitten: frame " + s.frame);
            pounced |= s.wolf.state() == Wolf.State.POUNCE;
            if (s.heroOnThePerch()) onLedge++;
            gaveUp = pounced && s.wolf.state() == Wolf.State.TROT;
        }
        assertTrue(pounced && onLedge > SECOND && gaveUp, "it leapt, he stood on the ledge and it gave him up: " + pounced + " " + onLedge + " " + gaveUp);
    }

    @Test void inBothRoomsItNeverRestsAnywhereButItsFloorAfterJumpBitesOffAndOntoThePerch() {
        for (String room : new String[]{"forest-glade", "forest-ridge"}) {
            float from = room.equals("forest-glade") ? GLADE_FROM : RIDGE_FROM;
            int pounces = 0;
            for (float x = from; x <= from + SPAN; x += X_STEP)
                for (int jumpAt = 6; jumpAt <= 18; jumpAt += 12)
                    for (int back = 18; back <= 30; back += 6) {
                        Script s = new Script(room, x, jumpAt, back);
                        boolean pounced = false;
                        while (s.frame < SECOND * 12 && !s.level.player.isGotHit() && !s.level.atRoomExit()) {
                            s.step();
                            pounced |= s.wolf.state() == Wolf.State.POUNCE;
                        }
                        if (pounced) pounces++;
                    }
            assertTrue(pounces > 20, room + ": the sweep does set off jump bites: " + pounces);
        }
    }

    /** Killed at any quarter of its jump bite or after it, or anywhere along a route leap, the body comes down to a floor of its route. */
    @Test void aBodyKilledInTheAirComesDownToItsFloorInBothRooms() {
        for (String room : new String[]{"forest-glade", "forest-ridge"}) {
            int killed = 0;
            float from = room.equals("forest-glade") ? GLADE_FROM : RIDGE_FROM;
            for (float x = from; x <= from + SPAN; x += X_STEP)
                for (int after = 0; after <= 30; after += 6) {
                    Script s = new Script(room, x, 18, 24);
                    int pounce = -1;
                    while (s.frame < SECOND * 12 && !s.level.player.isGotHit() && !s.wolf.isDefeated()) {
                        s.step();
                        if (pounce < 0 && s.wolf.state() == Wolf.State.POUNCE) pounce = s.frame;
                        if (pounce >= 0 && s.frame == pounce + after) s.wolf.collidedWithActor(true);
                    }
                    if (!s.wolf.isDefeated()) continue;
                    killed++;
                    settle(s);
                }
            assertTrue(killed > 20, room + ": bodies were looked at: " + killed);
        }
        for (int leap = 0; leap < 2; leap++)
            for (int after = 0; after < 18; after++) {
                Script s = new Script("forest-ridge", 6184, -1, SECOND * 60);
                s.level.player.position.y = PERCH - HERO_FRAME; // watching from the ledge while it comes down its two links
                int seen = 0, into = -1;
                while (s.frame < SECOND * 12 && !s.wolf.isDefeated()) {
                    boolean leaping = s.wolf.state() == Wolf.State.LEAP;
                    s.step();
                    if (!leaping && s.wolf.state() == Wolf.State.LEAP && seen++ == leap) into = 0;
                    else if (into >= 0) into++;
                    if (into == after) s.wolf.collidedWithActor(true);
                }
                assertTrue(s.wolf.isDefeated(), "killed in leap " + leap + ", " + after + " frames in");
                settle(s);
            }
    }

    /** Two seconds on, the body lies still on its leg's floor line. */
    private void settle(Script s) {
        for (int f = 0; f < SECOND * 2; f++) s.step();
        assertEquals(Wolf.State.DEAD, s.wolf.state());
        assertEquals(line(s.room, s.wolf), s.wolf.floorY(), SLACK, s.room + ": the body lies at x " + s.wolf.position.x);
    }
}
