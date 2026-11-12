package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticleSystemTest {
    private static final float STEP = 1f / 60f;
    private static final int CAPACITY = 8;
    /** Measured 0.0 B/frame (3 runs) for a full system with spawn, update and render; a few bytes of slack. */
    private static final double SYSTEM_CEILING = 8;

    private static void spawn(ParticleSystem s, float marker, float life) {
        s.spawn(ParticleSystem.KIND_GLOW, marker, 0, marker, -10, life, 2f, 1f, 1f, 1f);
    }

    @Test void neverExceedsCapacity() {
        ParticleSystem s = new ParticleSystem(CAPACITY, 1);
        for (int i = 0; i < CAPACITY * 5; i++) { spawn(s, i, 1f); assertTrue(s.count() <= CAPACITY); }
        assertEquals(CAPACITY, s.count());
    }

    @Test void fullPoolRecyclesTheOldest() {
        ParticleSystem s = new ParticleSystem(CAPACITY, 1);
        for (int i = 0; i < CAPACITY + 3; i++) spawn(s, i, 1f);
        assertEquals(3f, s.x(0), 0f, "the three oldest were dropped");
        assertEquals(CAPACITY + 2f, s.x(CAPACITY - 1), 0f, "the newest is last");
    }

    @Test void clearEmpties() {
        ParticleSystem s = new ParticleSystem(CAPACITY, 1);
        spawn(s, 1, 1f);
        s.clear();
        assertEquals(0, s.count());
    }

    @Test void deadParticlesLeaveAndOrderIsKept() {
        ParticleSystem s = new ParticleSystem(CAPACITY, 1);
        spawn(s, 0, 1f); spawn(s, 1, 0.01f); spawn(s, 2, 1f);
        s.update(STEP);
        assertEquals(2, s.count());
        assertEquals(0f, s.x(0), 0f);
        assertEquals(2f, s.x(1), 0f);
    }

    @Test void everyParticleIsDeadByItsMaxLife() {
        ParticleSystem s = new ParticleSystem(CAPACITY, 1);
        float maxLife = 0.5f;
        for (int i = 0; i < CAPACITY; i++) spawn(s, i, maxLife * (i + 1) / CAPACITY);
        int frames = (int) Math.ceil(maxLife / STEP) + 1;
        for (int i = 0; i < frames; i++) s.update(STEP);
        assertEquals(0, s.count());
    }

    private static float[] run(long seed) {
        Torch torch = new Torch(null, 100, 100, 16, 16, 200, "", seed);
        for (int i = 0; i < 120; i++) torch.update(STEP);
        ParticleSystem p = torch.particles();
        float[] out = new float[1 + p.count() * 2];
        out[0] = p.count();
        for (int i = 0; i < p.count(); i++) { out[1 + 2 * i] = p.x(i); out[2 + 2 * i] = p.y(i); }
        return out;
    }

    @Test void sameSeedSameStateOtherSeedDiffers() {
        assertArrayEquals(run(5), run(5), 0f);
        assertFalse(java.util.Arrays.equals(run(5), run(6)));
    }

    @Test void aFullSystemAllocatesNothingPerFrame() {
        ParticleSystem s = new ParticleSystem(CAPACITY, 1);
        NoopBatch batch = new NoopBatch();
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            for (int k = 0; k < 3; k++) s.spawn(k == 0 ? ParticleSystem.KIND_SMOKE : ParticleSystem.KIND_GLOW, 1, 1, 2, 2, 0.1f, 1f, 1f, 1f, 1f);
            s.update(STEP);
            s.render(batch, null);
        });
        System.out.printf("ALLOCATION ParticleSystem at capacity: %.1f B/frame (ceiling %.1f)%n", bytes, SYSTEM_CEILING);
        assertTrue(bytes <= SYSTEM_CEILING, "ParticleSystem allocates " + bytes + " B/frame");
    }
}
