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

    @Test void flameStaysOpaqueAndShrinksOutAtT0T05T1() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_FLAME, 0, 10, 0, 0, 1f, 3f, 1f, .5f, 0f);
        assertEquals(3f, s.drawSize(0), 1e-6f);
        assertEquals(1f, s.alpha(0), 0f, "opaque: no alpha fade");
        s.update(0.5f);
        assertEquals(1.5f, s.drawSize(0), 1e-5f, "half size at t = 0.5");
        assertEquals(1f, s.alpha(0), 0f);
        assertEquals(5f, s.y(0), 1e-4f, "rises: smaller Y is higher");
        s.update(0.499f);
        assertEquals(0.003f, s.drawSize(0), 1e-4f, "shrinks to nothing as life ends");
        assertEquals(1f, s.alpha(0), 0f);
    }

    @Test void flameIsDrawnInTheNormalBlendPass() {
        Recording only = new Recording();
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_FLAME, 0, 0, 0, 1, 1f, 1f, 1f, 1f, 1f);
        s.render(only.batch, null);
        assertEquals(1, only.draws);
        assertEquals(java.util.List.of(com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA),
            only.blends, "one normal switch for the pass, then the restore; no additive switch");
    }

    @Test void aLeafSwaysFlipsAndFadesInAndOutOverItsLife() {
        KimeriaParticleSystem p = new KimeriaParticleSystem(4, 1);
        p.spawnLeaf(100, 10, 100, 110, 10f, 6f, 8f, 0f, .4f, .5f, .4f);
        p.update(.01f);
        assertEquals(KimeriaParticleSystem.KIND_LEAF, p.kind(0));
        assertTrue(p.alpha(0) < .05f, "fades in");
        for (int i = 0; i < 500; i++) p.update(.01f);
        assertEquals(.85f, p.alpha(0), 1e-4f, "opaque in the middle of its life");
        assertTrue(p.drawWidth(0) <= 6f && p.drawWidth(0) >= 1f && p.drawHeight(0) <= 6f);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minW = Float.MAX_VALUE, maxW = 0;
        for (int i = 0; i < 300; i++) {
            p.update(.01f);
            minX = Math.min(minX, p.x(0)); maxX = Math.max(maxX, p.x(0));
            minW = Math.min(minW, p.drawWidth(0)); maxW = Math.max(maxW, p.drawWidth(0));
        }
        assertTrue(maxX - minX > 8f, "sways sideways: " + (maxX - minX));
        assertTrue(maxW - minW > 3f, "flips from edge-on to broad: " + minW + " to " + maxW);
        while (p.count() > 0 && p.life(0) > .2f) p.update(.01f);
        assertTrue(p.alpha(0) < .85f, "fades out");
    }

    /** D147: {@code KIND_DOME} is the upper half of a glow: its curves, additive, half as tall, drawn above its y. */
    @Test void aDomeIsTheUpperHalfOfAGlowStandingOnItsY() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_DOME, 50, 100, 50, 100, 1f, 20f, 1f, 1f, 1f);
        assertEquals(20f, s.drawWidth(0), 1e-6f);
        assertEquals(10f, s.drawHeight(0), 1e-6f);
        assertEquals(0.7f, s.alpha(0), 1e-6f);
        s.update(0.5f);
        assertEquals(20f * 0.8f, s.drawSize(0), 1e-5f, "shrinks as a glow");
        assertEquals(0.7f * 0.25f, s.alpha(0), 1e-6f);
        java.util.ArrayList<float[]> rects = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> blends = new java.util.ArrayList<>();
        com.badlogic.gdx.graphics.g2d.Batch batch = (com.badlogic.gdx.graphics.g2d.Batch) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{com.badlogic.gdx.graphics.g2d.Batch.class}, (proxy, method, args) -> {
                if (method.getName().equals("setBlendFunction")) blends.add((Integer) args[1]);
                if (method.getName().equals("draw")) rects.add(new float[]{(Float) args[1], (Float) args[2], (Float) args[3], (Float) args[4],
                    (Float) args[6], (Float) args[8]});
                return null;
            });
        com.badlogic.gdx.graphics.g2d.TextureRegion region = new com.badlogic.gdx.graphics.g2d.TextureRegion() { // a whole texture, no GL
            @Override public float getU2() { return 1f; }
            @Override public float getV2() { return 1f; }
        };
        s.render(batch, region);
        assertArrayEquals(new float[]{50 - 8, 100 - 8, 16, 8, 0f, .5f}, rects.get(0), 1e-4f,
            "x, y, width, height above its y, and the region's half from its top edge to its middle");
        assertEquals(com.badlogic.gdx.graphics.GL20.GL_ONE, blends.get(0), "additive");
    }

    /** #552: {@code KIND_ORBIT} rides an ellipse: position at t = 0, 0.5 and 1, constant size, alpha ramped in and out, additive. */
    @Test void anOrbitRidesItsEllipseAndRampsItsAlpha() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        // centre (100, 50), radii 20 by 10, start at 0 turns (pointing right), half a turn over its life, rho 1 to 0.5
        s.spawnOrbit(100, 50, 20, 10, 0f, 0.5f, 1f, 0.5f, 1f, 3f, 1f, 1f, 1f);
        assertEquals(120f, s.x(0), 1e-3f, "t = 0: on the right of the oval");
        assertEquals(50f, s.y(0), 1e-3f);
        assertEquals(0f, s.alpha(0), 0f, "alpha ramps in from nothing");
        for (int i = 0; i < 30; i++) s.update(STEP); // t = 0.5: a quarter turn (towards larger Y), rho 0.75
        assertEquals(100f, s.x(0), 1e-2f);
        assertEquals(50f + 10f * 0.75f, s.y(0), 1e-2f, "a quarter turn lands below the centre: world Y grows downward");
        assertEquals(1f, s.alpha(0), 1e-3f, "full in the middle of its life");
        assertEquals(3f, s.drawSize(0), 0f, "constant size");
        for (int i = 0; i < 29; i++) s.update(STEP);
        assertTrue(s.count() == 1 && s.alpha(0) < 0.25f, "ramping out at the end");
        s.update(STEP * 2);
        assertEquals(0, s.count());
    }

    /** D152: {@code KIND_SPARK} brakes along its line, drags a shrinking streak and is still seen as its life ends: t = 0, 0.5 and near 1. */
    @Test void aSparkBrakesDragsAStreakAndDimsLate() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_SPARK, 100, 50, 116, 50, 1f, 2f, 1f, 1f, 1f);
        assertEquals(100f, s.x(0), 0f);
        assertEquals(2f, s.drawSize(0), 1e-6f);
        assertEquals(1f, s.alpha(0), 0f);
        assertEquals(0f, s.tailLength(0), 0f, "no streak longer than the way already flown");
        s.update(0.1f); // 1 - 0.9^4 = 0.3439 of the way
        assertEquals(100f + 16f * 0.3439f, s.x(0), 1e-3f);
        assertEquals(5f * 0.729f, s.tailLength(0), 1e-3f, "5 px times (1 - t)^3");
        s.update(0.4f); // t = 0.5: 15/16 of the way
        assertEquals(115f, s.x(0), 1e-3f, "braking: almost there at half its life");
        assertEquals(50f, s.y(0), 0f);
        assertEquals(1.6f, s.drawSize(0), 1e-5f);
        assertEquals(1.6f * 0.7f, s.tailWidth(0), 1e-5f);
        assertEquals(5f / 8f, s.tailLength(0), 1e-4f);
        assertEquals(0f, s.tilt(0), 1e-3f, "flying right");
        assertEquals(1f, s.alpha(0), 0f);
        s.update(0.35f); // t = 0.85: half way down the fade
        assertEquals(1f - 0.55f * 0.5f, s.alpha(0), 1e-4f);
        s.update(0.149f);
        assertEquals(0.45f, s.alpha(0), 1e-2f, "dim, not gone");
    }

    @Test void aSparkIsAStreakAndAHeadInTheNormalPassPointingBackAlongItsFlight() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(CAPACITY, 1);
        s.spawn(KimeriaParticleSystem.KIND_SPARK, 0, 0, 0, 10, 1f, 2f, 1f, 1f, 1f); // downward: world Y grows down
        s.update(0.1f);
        assertEquals(90f, s.tilt(0), 1e-3f);
        NoopBatch batch = new NoopBatch();
        s.render(batch, null);
        assertEquals(1, batch.rotatedCount(), "the streak");
        assertEquals(s.tailLength(0), batch.lastRotatedWidth(), 0f);
        assertEquals(90f, batch.lastRotatedRotation(), 1e-3f);
        QuadRecorder quads = new QuadRecorder();
        s.render(quads.batch, null);
        assertEquals(2, quads.quads.size(), "streak and head");
        float[] streak = quads.quads.get(0);
        float head = s.y(0), tail = s.tailLength(0), half = s.tailWidth(0) / 2f;
        // Flying down: the streak lies above its head, from the head's centre back towards the start.
        for (int corner = 0; corner < 4; corner++) {
            assertEquals(half, Math.abs(QuadRecorder.cornerX(streak, corner)), 1e-3f, "half the width either side of the line");
            float y = QuadRecorder.cornerY(streak, corner);
            assertTrue(Math.abs(y - head) < 1e-3f || Math.abs(y - (head - tail)) < 1e-3f, "ends at the head and behind it: " + y);
        }
        assertTrue(QuadRecorder.cornerY(streak, 0) < head, "behind, not ahead");
        float size = s.drawSize(0);
        assertArrayEquals(new float[]{-size / 2f, head - size / 2f, size, size}, quads.quads.get(1), 1e-4f, "the head on the spark");
        Recording only = new Recording();
        s.render(only.batch, null);
        assertEquals(java.util.List.of(com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA),
            only.blends, "one normal switch for the pass, then the restore; no additive switch");
    }

    @Test void anOrbitSurvivesTheOldestBeingDroppedAndCompaction() {
        KimeriaParticleSystem s = new KimeriaParticleSystem(2, 1);
        s.spawnOrbit(0, 0, 10, 10, 0f, 0f, 1f, 1f, 0.05f, 1f, 1f, 1f, 1f);
        s.spawnOrbit(0, 0, 10, 10, 0.25f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f);
        s.spawnOrbit(0, 0, 10, 10, 0.5f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f); // drops the first
        assertEquals(2, s.count());
        assertEquals(10f, s.y(0), 1e-3f, "the 0.25 turn orbit moved to index 0 with its own radii");
        s.update(STEP);
        assertEquals(10f, s.y(0), 1e-2f);
        assertEquals(-10f, s.x(1), 1e-2f, "the half turn orbit sits on the left");
    }
}
