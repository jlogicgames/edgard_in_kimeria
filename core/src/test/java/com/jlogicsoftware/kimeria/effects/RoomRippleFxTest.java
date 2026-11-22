package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #553: the ripple of a room change: three rings one after another, motes pulled in or sent out, and the screen ripple. */
class RoomRippleFxTest {
    private static final float STEP = 1f / 60f;

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
        leave.leave(0, 0); enter.enter(0, 0);
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
        r.enter(5, 5);
        assertNotNull(r.distortion());
        for (float t = 0; t < RoomRippleFx.DISTORT_SECONDS + 0.1f; t += STEP) r.update(STEP);
        assertNull(r.distortion(), "it ends by itself");
        r.leave(5, 5);
        r.clear();
        assertNull(r.distortion());
        assertEquals(0, r.ringCount() + r.motes().count());
    }

    @Test void thePauseIsTheApprovedTwentyHundredthsOfASecond() {
        assertEquals(0.20f, RoomRippleFx.LEAVE_HOLD_SECONDS, 0f, "the author approved 0.20 s on 2026-11-22; a change needs D123 amended and RoomReplayTest re-read");
    }
}
