package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the CURRENT look of the torch and blood particles (step 0 of the particle plan, #285): with a fixed seed the
 * live particle count and every particle's position, size and alpha at chosen frames are constants. Steps 2 and 3
 * move these effects onto a shared allocation-free system and must reproduce the numbers to the float; if one has to
 * change, the report says so and docs/DECISIONS.md records the measured value and why (AGENTS.md, tolerance rule).
 *
 * <p>Sizes and alphas are the values {@code render} computes from the particle state; they are recomputed here with
 * the same formulas, so a change to either the simulation or the render formulas shows up. Y grows downward.
 */
class ParticleGoldenTest {
    static final long SEED = 7264;
    static final float STEP = 1f / 60f;
    /** Frames (1-based count of updates) at which a snapshot is compared. */
    static final int[] TORCH_FRAMES = {20, 90, 300};
    static final int[] BLOOD_FRAMES = {6, 24, 48};
    static final float TORCH_X = 100, TORCH_Y = 100, TORCH_SIZE = 16;
    static final int TORCH_INTENSITY = 200;
    static final float BLOOD_X = 100, BLOOD_Y = 90, BLOOD_FLOOR = 110;
    static final int BLOOD_CAPACITY = 96;

    /**
     * Each snapshot is {liveCount, x0, y0, size0, alpha0, x1, ...} in spawn order. The torch's generator is seeded with
     * {@link #SEED} directly: {@code new RandomXS128(SEED)} is the sequence {@code MathUtils.random} gave the old code.
     */
    static float[][] torchSnapshots() {
        Torch torch = new Torch(null, TORCH_X, TORCH_Y, TORCH_SIZE, TORCH_SIZE, TORCH_INTENSITY, "", SEED);
        float[][] result = new float[TORCH_FRAMES.length][];
        int next = 0;
        for (int frame = 1; frame <= TORCH_FRAMES[TORCH_FRAMES.length - 1]; frame++) {
            torch.update(STEP);
            if (frame == TORCH_FRAMES[next]) result[next++] = torchSnapshot(torch.particles());
        }
        return result;
    }

    private static float[] torchSnapshot(ParticleSystem particles) {
        float[] values = new float[1 + particles.count() * 4];
        values[0] = particles.count();
        int at = 1;
        for (int i = 0; i < particles.count(); i++) {
            values[at++] = particles.x(i);
            values[at++] = particles.y(i);
            values[at++] = particles.drawSize(i);
            values[at++] = particles.alpha(i);
        }
        return values;
    }

    /**
     * Same layout as {@link #torchSnapshots()}, for the live drops of one burst of the {@link BloodSystem}; the size is the
     * drop's base size and the alpha its fade, as {@code render} uses them. {@code new RandomXS128(SEED)} is the sequence
     * {@code MathUtils.random} gave the old {@code BloodBurst}, which this system replaced in step 3.
     */
    static float[][] bloodSnapshots(boolean empowered) {
        BloodSystem blood = new BloodSystem(BLOOD_CAPACITY, SEED);
        blood.spray(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR, empowered);
        float[][] result = new float[BLOOD_FRAMES.length][];
        int next = 0;
        for (int frame = 1; frame <= BLOOD_FRAMES[BLOOD_FRAMES.length - 1]; frame++) {
            blood.update(STEP);
            if (frame == BLOOD_FRAMES[next]) result[next++] = bloodSnapshot(blood);
        }
        return result;
    }

    private static float[] bloodSnapshot(BloodSystem blood) {
        float[] values = new float[1 + blood.count() * 4];
        values[0] = blood.count();
        int at = 1;
        for (int i = 0; i < blood.count(); i++) {
            values[at++] = blood.x(i);
            values[at++] = blood.y(i);
            values[at++] = blood.size(i);
            values[at++] = blood.fade(i);
        }
        return values;
    }

    private static void assertSnapshots(float[][] expected, float[][] actual, String what) {
        assertEquals(expected.length, actual.length, what);
        for (int i = 0; i < expected.length; i++) {
            assertEquals((int) expected[i][0], (int) actual[i][0], what + ": live particle count at snapshot " + i);
            assertArrayEquals(expected[i], actual[i], 0f, what + ": snapshot " + i + " (to the float)");
        }
    }

    @Test void torchLooksExactlyAsItDid() {
        assertSnapshots(ParticleGoldenData.TORCH, torchSnapshots(), "torch");
    }
    @Test void bloodBurstOf32DropsLooksExactlyAsItDid() {
        assertSnapshots(ParticleGoldenData.BLOOD_32, bloodSnapshots(false), "blood 32");
    }
    @Test void empoweredBloodBurstOf48DropsLooksExactlyAsItDid() {
        assertSnapshots(ParticleGoldenData.BLOOD_48, bloodSnapshots(true), "blood 48");
    }
}
