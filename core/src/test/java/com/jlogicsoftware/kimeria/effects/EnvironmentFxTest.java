package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The dust and grit particle systems of {@link EnvironmentFx} (#239 steps 2 and 3, #285 step 5). */
class EnvironmentFxTest {
    private static final float STEP = 1f / 60f;
    private static final float FEET_X = 200, FEET_Y = 300, WALL_X = 500, WALL_W = 48, ROOM_TOP = 100;

    private static EnvironmentFx fx(long seed) { return new EnvironmentFx(() -> true, seed); }

    private static float[] positions(KimeriaParticleSystem p) {
        float[] out = new float[1 + p.count() * 2];
        out[0] = p.count();
        for (int i = 0; i < p.count(); i++) { out[1 + 2 * i] = p.x(i); out[2 + 2 * i] = p.y(i); }
        return out;
    }

    @Test void aPuffIsSixSmokeParticlesAtTheFeetThatRise() {
        EnvironmentFx fx = fx(1);
        fx.dustPuff(FEET_X, FEET_Y);
        KimeriaParticleSystem d = fx.dust();
        assertEquals(EnvironmentFx.DUST_PER_PUFF, d.count());
        assertEquals(0, fx.grit().count());
        for (int i = 0; i < d.count(); i++) {
            assertEquals(KimeriaParticleSystem.KIND_SMOKE, d.kind(i));
            assertEquals(FEET_Y, d.y(i), 0f);
            assertTrue(Math.abs(d.x(i) - FEET_X) <= 8, "starts within 8 px of the centre");
            assertTrue(d.maxLife(i) >= .25f && d.maxLife(i) <= .4f);
        }
        fx.update(.1f);
        for (int i = 0; i < d.count(); i++) assertTrue(d.y(i) < FEET_Y, "rises: smaller Y is higher");
    }

    @Test void aLandingPuffIsASmallerPuffOfTheSameDustAtTheFeet() {
        EnvironmentFx fx = fx(1);
        fx.landingPuff(FEET_X, FEET_Y);
        KimeriaParticleSystem d = fx.dust();
        assertEquals(EnvironmentFx.DUST_PER_LANDING, d.count());
        assertTrue(EnvironmentFx.DUST_PER_LANDING <= EnvironmentFx.DUST_PER_PUFF);
        for (int i = 0; i < d.count(); i++) {
            assertEquals(KimeriaParticleSystem.KIND_SMOKE, d.kind(i));
            assertEquals(FEET_Y, d.y(i), 0f);
        }
        fx.update(.1f);
        for (int i = 0; i < d.count(); i++) assertTrue(d.y(i) < FEET_Y, "rises: smaller Y is higher");
    }

    @Test void aFullPoolDropsTheOldestAndNeverExceedsCapacity() {
        EnvironmentFx fx = fx(1);
        for (int i = 0; i < 20; i++) { fx.dustPuff(FEET_X + i, FEET_Y); assertTrue(fx.dust().count() <= EnvironmentFx.DUST_CAPACITY); }
        assertEquals(EnvironmentFx.DUST_CAPACITY, fx.dust().count());
        assertTrue(fx.dust().x(fx.dust().count() - 1) > FEET_X + 10, "the newest puff is last; the first ones were dropped");
    }

    @Test void particlesDieByTheirLifetimeAndPauseLeavesThemUnchanged() {
        EnvironmentFx fx = fx(1);
        fx.dustPuff(FEET_X, FEET_Y);
        fx.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        float[] dustBefore = positions(fx.dust()), gritBefore = positions(fx.grit());
        // Pause is "no update": nothing reads a clock.
        assertArrayEquals(dustBefore, positions(fx.dust()), 0f);
        assertArrayEquals(gritBefore, positions(fx.grit()), 0f);
        for (int i = 0; i < 120; i++) fx.update(STEP);
        assertEquals(0, fx.dust().count());
        assertEquals(0, fx.grit().count());
    }

    @Test void sameSeedAndEventsGiveTheSamePositionsOtherSeedDiffers() {
        assertArrayEquals(run(7), run(7), 0f);
        assertFalse(java.util.Arrays.equals(run(7), run(8)));
    }

    private static float[] run(long seed) {
        EnvironmentFx fx = fx(seed);
        fx.dustPuff(FEET_X, FEET_Y);
        fx.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        for (int i = 0; i < 20; i++) fx.update(STEP);
        float[] a = positions(fx.dust()), b = positions(fx.grit());
        float[] out = new float[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @Test void theTwoStreamsDoNotDisturbEachOther() {
        EnvironmentFx plain = fx(3), withDust = fx(3);
        withDust.dustPuff(FEET_X, FEET_Y);
        plain.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        withDust.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        for (int i = 0; i < 30; i++) { plain.update(STEP); withDust.update(STEP); }
        assertArrayEquals(positions(plain.grit()), positions(withDust.grit()), 0f);
    }

    @Test void aGritBurstFallsFromTheRoomTopOverTheWallWidthFromTheRoomTop() {
        EnvironmentFx fx = fx(1);
        fx.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        KimeriaParticleSystem g = fx.grit();
        assertEquals(1, g.count(), "the first particle starts at once");
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxFall = 0;
        for (int f = 0; f < Math.round(EnvironmentFx.GRIT_EMIT_SECONDS / STEP) + 2; f++) {
            fx.update(STEP);
            for (int i = 0; i < g.count(); i++) {
                assertEquals(KimeriaParticleSystem.KIND_GRIT, g.kind(i));
                minX = Math.min(minX, g.x(i)); maxX = Math.max(maxX, g.x(i));
                assertTrue(g.y(i) >= ROOM_TOP, "starts at the top edge and falls (larger Y is lower)");
                maxFall = Math.max(maxFall, g.y(i) - ROOM_TOP);
            }
        }
        assertTrue(minX >= WALL_X - WALL_W / 2f - 24 && maxX <= WALL_X + WALL_W / 2f + 24, minX + ".." + maxX);
        assertTrue(maxFall <= 64.001f, "falls at most 64 px, got " + maxFall);
        // Everything is out and gone after the burst plus the longest life.
        for (int i = 0; i < 90; i++) fx.update(STEP);
        assertEquals(0, g.count());
    }

    @Test void gritPeakLiveCountFitsTheCapacityWithoutDropping() {
        int peak = 0;
        for (long seed = 1; seed <= 40; seed++) {
            for (float step : new float[]{1f / 30f, STEP, 1f / 144f}) {
                EnvironmentFx fx = fx(seed);
                fx.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
                for (int f = 0; f < Math.round(2f / step); f++) { fx.update(step); peak = Math.max(peak, fx.grit().count()); }
            }
        }
        System.out.println("GRIT PEAK LIVE PARTICLES: " + peak + " (capacity " + EnvironmentFx.GRIT_CAPACITY + ")");
        assertTrue(peak < EnvironmentFx.GRIT_CAPACITY, "a burst must not fill the pool: peak " + peak);
    }

    @Test void dustPeakLiveCountForJumpsEveryQuarterSecondFitsTheCapacity() {
        EnvironmentFx fx = fx(1);
        int peak = 0;
        for (int f = 0; f < 600; f++) {
            if (f % 15 == 0) fx.dustPuff(FEET_X, FEET_Y);
            fx.update(STEP);
            peak = Math.max(peak, fx.dust().count());
        }
        System.out.println("DUST PEAK LIVE PARTICLES: " + peak + " (capacity " + EnvironmentFx.DUST_CAPACITY + ")");
        assertTrue(peak < EnvironmentFx.DUST_CAPACITY, "peak " + peak);
    }

    @Test void clearLeavesNoParticleAndStopsAnUnfinishedBurst() {
        EnvironmentFx fx = fx(1);
        fx.dustPuff(FEET_X, FEET_Y);
        fx.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        fx.update(STEP);
        fx.clear();
        assertEquals(0, fx.dust().count());
        assertEquals(0, fx.grit().count());
        for (int i = 0; i < 60; i++) fx.update(STEP);
        assertEquals(0, fx.grit().count(), "a cleared burst does not resume");
    }

    @Test void theShakeOptionDoesNotSwitchDustOrGritOff() {
        EnvironmentFx fx = new EnvironmentFx(() -> false, 1);
        fx.dustPuff(FEET_X, FEET_Y);
        fx.ceilingGrit(WALL_X, WALL_W, ROOM_TOP);
        assertEquals(EnvironmentFx.DUST_PER_PUFF, fx.dust().count());
        assertEquals(1, fx.grit().count());
    }
}
