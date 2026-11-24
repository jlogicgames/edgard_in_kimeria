package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #552: the room-exit portal: its oval from the exit rectangle, its streams and its fixed pools. */
class PortalFxTest {
    private static final float STEP = 1f / 60f;
    /** A pool may be at most this full at its measured peak: a full pool silently drops its oldest particle. */
    private static final float HEADROOM = .75f;

    @Test void theOvalFollowsTheExitRectangleWithinLimits() {
        PortalFx p = new PortalFx();
        p.place(100, 200, 32, 48);
        assertEquals(116f, p.centreX()); assertEquals(224f, p.centreY());
        assertEquals(24f, p.radiusY()); assertEquals(24f * PortalFx.WIDTH_RATIO, p.radiusX(), 1e-4f);
        p.place(100, 200, 32, 32);
        assertEquals(PortalFx.MIN_HALF_HEIGHT, p.radiusY(), "a 32 px exit gets the minimum half height, which reaches a few pixels past it");
        p.place(0, 0, 16, 200);
        assertEquals(PortalFx.MAX_HALF_HEIGHT, p.radiusY());
    }

    @Test void nothingSpawnsUnplacedAndClearRemovesEverything() {
        PortalFx p = new PortalFx();
        p.update(1f);
        assertEquals(0, p.motes().count() + p.light().count());
        p.place(0, 0, 32, 48);
        for (int i = 0; i < 120; i++) p.update(STEP);
        assertTrue(p.motes().count() > 0 && p.light().count() > 0);
        p.clear();
        assertEquals(0, p.motes().count() + p.light().count());
        assertTrue(!p.placed());
    }

    @Test void theParticlesStayRoundTheOvalAndPoolsNeverFill() {
        for (float dt : new float[]{1f / 30, 1f / 60, 1f / 144}) {
            PortalFx p = new PortalFx();
            p.place(500, 300, 32, 48);
            int motes = 0, light = 0;
            for (float t = 0; t < 12; t += dt) {
                p.update(dt);
                motes = Math.max(motes, p.motes().count()); light = Math.max(light, p.light().count());
                for (int i = 0; i < p.motes().count(); i++) {
                    float u = (p.motes().x(i) - 516) / (p.radiusX() * 2.2f), v = (p.motes().y(i) - 324) / (p.radiusY() * 2.2f);
                    assertTrue(u * u + v * v <= 1.001f, "a mote is within 2.2 ovals of the portal");
                }
            }
            System.out.println("PEAK portal motes " + motes + " light " + light + " dt " + dt);
            assertTrue(motes <= PortalFx.MOTE_CAPACITY * HEADROOM, "mote peak " + motes + " at dt " + dt);
            assertTrue(light <= PortalFx.LIGHT_CAPACITY * HEADROOM, "light peak " + light + " at dt " + dt);
        }
    }

    @Test void theSameSeedsGiveTheSameRun() {
        PortalFx a = new PortalFx(), b = new PortalFx();
        a.place(0, 0, 32, 48); b.place(0, 0, 32, 48);
        for (int i = 0; i < 200; i++) { a.update(STEP); b.update(STEP); }
        assertEquals(a.motes().count(), b.motes().count());
        for (int i = 0; i < a.motes().count(); i++) assertEquals(a.motes().x(i), b.motes().x(i), 0f);
    }

    /** #605: the level-end ramp is amber (red above green above blue), the room ramp stays violet (blue above red), and clear goes back to violet. */
    @Test void theLevelEndRampIsAmberAndTheRoomRampStaysViolet() {
        PortalFx p = new PortalFx();
        p.placeLevelEnd(0, 0, 16, 32);
        assertTrue(p.levelEnd());
        for (int i = 0; i < 180; i++) p.update(STEP);
        assertTrue(p.motes().count() > 0 && p.light().count() > 0);
        for (int i = 0; i < p.motes().count(); i++)
            assertTrue(p.motes().red(i) >= p.motes().green(i) && p.motes().green(i) > p.motes().blue(i), "an amber mote: red, then green, then blue");
        for (int i = 0; i < p.light().count(); i++) assertTrue(p.light().red(i) > p.light().blue(i), "amber light");
        p.place(0, 0, 32, 48);
        assertTrue(!p.levelEnd());
        for (int i = 0; i < 180; i++) p.update(STEP);
        for (int i = 0; i < p.motes().count(); i++) assertTrue(p.motes().blue(i) > p.motes().green(i), "a violet mote: blue above green");
        p.placeLevelEnd(0, 0, 16, 32);
        p.clear();
        assertTrue(!p.levelEnd());
    }
}
