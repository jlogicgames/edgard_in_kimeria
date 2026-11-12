package com.jlogicsoftware.kimeria.effects;

import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KimeriaParticleSystemTest {
    private static final float STEP = 1f / 60f;
    private static final int CAPACITY = 8;
    /** Measured 0.0 B/frame (3 runs) for a full system with spawn, update and render; a few bytes of slack. */
    private static final double SYSTEM_CEILING = 8;

    private static void spawn(KimeriaParticleSystem s, float marker, float life) {
        s.spawn(KimeriaParticleSystem.KIND_GLOW, marker, 0, marker, -10, life, 2f, 1f, 1f, 1f);
    }

    @Test void neverExceedsCapacity() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        for (int i = 0; i < CAPACITY * 5; i++) { spawn(s, i, 1f); assertTrue(s.count() <= CAPACITY); }
        assertEquals(CAPACITY, s.count());
    }

    @Test void fullPoolRecyclesTheOldest() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        for (int i = 0; i < CAPACITY + 3; i++) spawn(s, i, 1f);
        assertEquals(3f, s.x(0), 0f, "the three oldest were dropped");
        assertEquals(CAPACITY + 2f, s.x(CAPACITY - 1), 0f, "the newest is last");
    }

    @Test void clearEmpties() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        spawn(s, 1, 1f);
        s.clear();
        assertEquals(0, s.count());
    }

    @Test void deadParticlesLeaveAndOrderIsKept() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        spawn(s, 0, 1f); spawn(s, 1, 0.01f); spawn(s, 2, 1f);
        s.update(STEP);
        assertEquals(2, s.count());
        assertEquals(0f, s.x(0), 0f);
        assertEquals(2f, s.x(1), 0f);
    }

    @Test void everyParticleIsDeadByItsMaxLife() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        float maxLife = 0.5f;
        for (int i = 0; i < CAPACITY; i++) spawn(s, i, maxLife * (i + 1) / CAPACITY);
        int frames = (int) Math.ceil(maxLife / STEP) + 1;
        for (int i = 0; i < frames; i++) s.update(STEP);
        assertEquals(0, s.count());
    }

    private static float[] run(long seed) {
        Torch torch = new Torch(null, 100, 100, 16, 16, 200, "", seed);
        for (int i = 0; i < 120; i++) torch.update(STEP);
        KimeriaParticleSystem p = torch.particles();
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
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        NoopBatch batch = new NoopBatch();
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            for (int k = 0; k < 3; k++) s.spawn(k == 0 ? KimeriaParticleSystem.KIND_SMOKE : KimeriaParticleSystem.KIND_GLOW, 1, 1, 2, 2, 0.1f, 1f, 1f, 1f, 1f);
            s.update(STEP);
            s.render(batch, null);
        });
        System.out.printf("ALLOCATION KimeriaParticleSystem at capacity: %.1f B/frame (ceiling %.1f)%n", bytes, SYSTEM_CEILING);
        assertTrue(bytes <= SYSTEM_CEILING, "KimeriaParticleSystem allocates " + bytes + " B/frame");
    }

    /** Records the blend destination factor each time a pass switches it, and how many quads were drawn. */
    private static final class Recording {
        final java.util.ArrayList<Integer> blends = new java.util.ArrayList<>();
        int draws;
        final com.badlogic.gdx.graphics.g2d.Batch batch = (com.badlogic.gdx.graphics.g2d.Batch) java.lang.reflect.Proxy.newProxyInstance(
            Recording.class.getClassLoader(), new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("setBlendFunction")) blends.add((Integer) args[1]);
                if (method.getName().equals("draw")) draws++;
                return null;
            });
    }

    @Test void gritKeepsItsSizeAndFadesLinearlyAtT0T05T1() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_GRIT, 0, 0, 0, 10, 1f, 2f, 1f, 1f, 1f);
        assertEquals(2f, s.drawSize(0), 1e-6f);
        assertEquals(0.7f, s.alpha(0), 1e-6f);
        s.update(0.5f);
        assertEquals(2f, s.drawSize(0), 1e-6f, "no scaling at t = 0.5");
        assertEquals(0.35f, s.alpha(0), 1e-5f);
        assertEquals(5f, s.y(0), 1e-4f, "falls: larger Y is lower");
        s.update(0.499f);
        assertEquals(2f, s.drawSize(0), 1e-6f);
        assertEquals(0.0007f, s.alpha(0), 1e-4f, "alpha reaches 0 as life ends");
    }

    @Test void glowAndSmokeCurvesAreUnchangedByTheThirdKind() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_GLOW, 0, 0, 0, 0, 1f, 2f, 1f, 1f, 1f);
        s.spawn(KimeriaParticleSystem.KIND_SMOKE, 0, 0, 0, 0, 1f, 2f, 1f, 1f, 1f);
        s.update(0.5f);
        assertEquals(2f * 0.8f, s.drawSize(0), 1e-6f);
        assertEquals(0.7f * 0.25f, s.alpha(0), 1e-6f);
        assertEquals(2f * 1.5f, s.drawSize(1), 1e-6f);
        assertEquals(0.7f * 0.5f, s.alpha(1), 1e-6f);
    }

    @Test void gritIsDrawnInTheNormalBlendPassWithSmokeAndBeforeGlow() {
        Recording only = new Recording();
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_GRIT, 0, 0, 0, 1, 1f, 1f, 1f, 1f, 1f);
        s.render(only.batch, null);
        assertEquals(1, only.draws);
        assertEquals(java.util.List.of(com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA),
            only.blends, "one normal switch for the pass, then the restore; no additive switch");
        Recording mixed = new Recording();
        s.spawn(KimeriaParticleSystem.KIND_GLOW, 0, 0, 0, 1, 1f, 1f, 1f, 1f, 1f);
        s.render(mixed.batch, null);
        assertEquals(2, mixed.draws);
        assertEquals(java.util.List.of(com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE,
            com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA), mixed.blends);
    }
}
