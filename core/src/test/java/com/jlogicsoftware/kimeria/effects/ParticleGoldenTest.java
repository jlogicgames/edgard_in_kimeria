package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

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

    /** Each snapshot is {liveCount, x0, y0, size0, alpha0, x1, ...} in spawn order. */
    static float[][] torchSnapshots() throws Exception {
        var previous = MathUtils.random;
        var random = new RandomXS128(SEED);
        MathUtils.random = random;
        try {
            Torch torch = new Torch(TORCH_X, TORCH_Y, TORCH_SIZE, TORCH_SIZE, TORCH_INTENSITY, "");
            Field field = Torch.class.getDeclaredField("particles"); field.setAccessible(true);
            float[][] result = new float[TORCH_FRAMES.length][];
            int next = 0;
            for (int frame = 1; frame <= TORCH_FRAMES[TORCH_FRAMES.length - 1]; frame++) {
                torch.update(STEP);
                if (frame == TORCH_FRAMES[next]) result[next++] = torchSnapshot((List<?>) field.get(torch));
            }
            return result;
        } finally { MathUtils.random = previous; }
    }

    private static float[] torchSnapshot(List<?> particles) throws Exception {
        float[] values = new float[1 + particles.size() * 4];
        values[0] = particles.size();
        int at = 1;
        for (Object p : particles) {
            Vector2 pos = (Vector2) field(p, "pos");
            float life = (float) field(p, "life"), maxLife = (float) field(p, "maxLife"), base = (float) field(p, "size");
            boolean smoke = String.valueOf(field(p, "kind")).equals("SMOKE");
            float t = 1f - life / maxLife;
            values[at++] = pos.x;
            values[at++] = pos.y;
            values[at++] = base * (smoke ? (1f + t) : (1f - 0.4f * t));
            values[at++] = (smoke ? (1f - t) : (1f - t) * (1f - t)) * 0.7f;
        }
        return values;
    }

    /** Same layout as {@link #torchSnapshots()}, for the live drops of a {@code BloodBurst}. */
    static float[][] bloodSnapshots(boolean empowered) throws Exception {
        var previous = MathUtils.random;
        var random = new RandomXS128(SEED);
        MathUtils.random = random;
        try {
            BloodBurst burst = new BloodBurst(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR, empowered);
            Field drops = BloodBurst.class.getDeclaredField("drops"); drops.setAccessible(true);
            float[][] result = new float[BLOOD_FRAMES.length][];
            int next = 0;
            for (int frame = 1; frame <= BLOOD_FRAMES[BLOOD_FRAMES.length - 1]; frame++) {
                burst.update(STEP);
                if (frame == BLOOD_FRAMES[next]) result[next++] = bloodSnapshot(burst, (Object[]) drops.get(burst));
            }
            return result;
        } finally { MathUtils.random = previous; }
    }

    private static float[] bloodSnapshot(BloodBurst burst, Object[] drops) throws Exception {
        float age = (float) field(burst, "age");
        int live = 0;
        for (Object d : drops) if (age < (float) field(d, "life")) live++;
        float[] values = new float[1 + live * 4];
        values[0] = live;
        int at = 1;
        for (Object d : drops) {
            float life = (float) field(d, "life");
            if (age >= life) continue;
            values[at++] = (float) field(d, "x");
            values[at++] = (float) field(d, "y");
            values[at++] = (float) field(d, "size");
            values[at++] = Math.min(1, (life - age) / .2f);
        }
        return values;
    }

    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true);
        return f.get(owner);
    }

    private static void assertSnapshots(float[][] expected, float[][] actual, String what) {
        assertEquals(expected.length, actual.length, what);
        for (int i = 0; i < expected.length; i++) {
            assertEquals((int) expected[i][0], (int) actual[i][0], what + ": live particle count at snapshot " + i);
            assertArrayEquals(expected[i], actual[i], 0f, what + ": snapshot " + i + " (to the float)");
        }
    }

    @Test void torchLooksExactlyAsItDid() throws Exception {
        assertSnapshots(ParticleGoldenData.TORCH, torchSnapshots(), "torch");
    }
    @Test void bloodBurstOf32DropsLooksExactlyAsItDid() throws Exception {
        assertSnapshots(ParticleGoldenData.BLOOD_32, bloodSnapshots(false), "blood 32");
    }
    @Test void empoweredBloodBurstOf48DropsLooksExactlyAsItDid() throws Exception {
        assertSnapshots(ParticleGoldenData.BLOOD_48, bloodSnapshots(true), "blood 48");
    }
}
