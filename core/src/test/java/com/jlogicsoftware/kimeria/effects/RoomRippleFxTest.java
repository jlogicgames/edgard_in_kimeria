package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.world.RoomFade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #553: the ripple of a room change: three rings one after another, motes pulled in or sent out, and the screen ripple. */
class RoomRippleFxTest {
    private static final float STEP = 1f / 60f;

    /** Asks for the entering ripple and runs to the update that starts it. */
    private static void enterAndStart(RoomRippleFx r, float x, float y) {
        r.enter(x, y);
        for (int i = 0; r.motes().count() == 0 && i < 60; i++) r.update(STEP);
    }

    @Test void threeRingsStartOneAfterAnotherAndEndByTheirLife() {
        RoomRippleFx r = new RoomRippleFx();
        r.leave(10, 20);
        r.update(STEP);
        assertEquals(1, r.ringCount(), "the first ring starts at once");
        for (float t = STEP; t < RoomRippleFx.RING_STAGGER * 2 + STEP; t += STEP) r.update(STEP);
        assertEquals(3, r.ringCount(), "all three are running after two staggers");
        for (float t = 0; t < RoomRippleFx.RING_LIFE + RoomRippleFx.RING_STAGGER * 2 + 0.1f; t += STEP) r.update(STEP);
        assertEquals(0, r.ringCount());
        assertEquals(0, r.motes().count());
    }

    @Test void leavingPullsMotesInAndEnteringSendsThemOut() {
        RoomRippleFx leave = new RoomRippleFx(), enter = new RoomRippleFx();
        leave.leave(0, 0); enterAndStart(enter, 0, 0);
        float leaveFirst = Float.MAX_VALUE, enterFirst = 0;
        float leaveStart = (float) Math.hypot(leave.motes().x(0), leave.motes().y(0)), enterStart = (float) Math.hypot(enter.motes().x(0), enter.motes().y(0));
        for (int i = 0; i < 6; i++) { leave.update(STEP); enter.update(STEP); }
        float leaveNow = (float) Math.hypot(leave.motes().x(0), leave.motes().y(0)), enterNow = (float) Math.hypot(enter.motes().x(0), enter.motes().y(0));
        assertTrue(leaveNow < leaveStart, "a leaving mote moves towards the hero");
        assertTrue(enterNow > enterStart, "an entering mote moves away from him");
    }

    @Test void theScreenRippleLivesItsSecondsAndClearEndsIt() {
        RoomRippleFx r = new RoomRippleFx();
        assertNull(r.distortion());
        enterAndStart(r, 5, 5);
        assertNotNull(r.distortion());
        for (float t = 0; t < RoomRippleFx.DISTORT_SECONDS + 0.1f; t += STEP) r.update(STEP);
        assertNull(r.distortion(), "it ends by itself");
        r.leave(5, 5);
        r.clear();
        assertNull(r.distortion());
        assertEquals(0, r.ringCount() + r.motes().count());
    }

    /**
     * #606: the ripple's own update, then the fade's, as {@code KimeriaGame.updatePlay} orders them: nothing of the entering ripple exists
     * while any of the black panel is left, at any frame rate.
     */
    @Test void theEnteringRippleStartsOnlyWhenTheFadeInIsClear() {
        for (int hz : new int[] {30, 60, 144}) {
            float step = 1f / hz;
            RoomRippleFx r = new RoomRippleFx();
            RoomFade fade = new RoomFade();
            fade.start();
            while (!fade.dark()) fade.update(step, () -> r.enter(5, 5));
            assertTrue(r.heroUnseen(), "the hero is not drawn from the switch on");
            int frames = 0;
            while (r.motes().count() == 0 && frames++ < hz) {
                assertNull(r.distortion(), "no screen ripple under the black panel");
                assertEquals(0, r.ringCount());
                r.update(step);
                fade.update(step, () -> { });
            }
            assertTrue(fade.alpha() <= .001f, hz + " Hz: the screen is clear when the ripple starts, alpha " + fade.alpha());
            assertEquals(RoomRippleFx.ENTER_DELAY_SECONDS, frames * step, step + .001f, hz + " Hz: and it starts as soon as it is");
        }
    }

    @Test void theHeroIsSeenAShortWayIntoTheEnteringRippleAndALeaveOrAClearEndsTheWait() {
        RoomRippleFx r = new RoomRippleFx();
        enterAndStart(r, 5, 5);
        assertTrue(r.heroUnseen(), "the ripple starts before he is drawn");
        float since = 0;
        while (r.heroUnseen() && since < 1) { r.update(STEP); since += STEP; }
        assertEquals(RoomRippleFx.REVEAL_SECONDS, since, STEP + .001f);
        r.enter(5, 5);
        r.clear();
        assertFalse(r.heroUnseen(), "a retry or a room load shows him at once");
        for (int i = 0; i < 60; i++) r.update(STEP);
        assertEquals(0, r.ringCount() + r.motes().count(), "and the cleared ripple never starts");
        r.enter(5, 5);
        r.leave(9, 9);
        assertFalse(r.heroUnseen(), "a leave drops a pending arrival");
    }

    @Test void theArrivalTimesAreTheApprovedOnes() {
        assertEquals(RoomFade.HALF_SECONDS, RoomRippleFx.ENTER_DELAY_SECONDS, 0f, "#606: the delay is the whole fade-in");
        assertEquals(0.35f, RoomRippleFx.ARRIVE_HOLD_SECONDS, 0f, "the author approved delay, hold and boost on 2026-11-25; a change needs D123 amended and RoomReplayTest re-read");
        assertEquals(0.15f, RoomRippleFx.REVEAL_SECONDS, 0f);
    }

    @Test void thePauseIsTheApprovedTwentyHundredthsOfASecond() {
        assertEquals(0.20f, RoomRippleFx.LEAVE_HOLD_SECONDS, 0f, "the author approved 0.20 s on 2026-11-22; a change needs D123 amended and RoomReplayTest re-read");
    }
}
