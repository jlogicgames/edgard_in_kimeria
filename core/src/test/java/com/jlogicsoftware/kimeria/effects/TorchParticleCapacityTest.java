package com.jlogicsoftware.kimeria.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Sizes {@link Torch#PARTICLE_CAPACITY}: the peak live count over a long run of many seeds, at several frame rates. */
class TorchParticleCapacityTest {
    private static final int SEEDS = 40, FRAMES = 20000;
    private static final float[] STEPS = {1f / 30f, 1f / 60f, 1f / 144f};

    @Test void peakLiveCountStaysBelowCapacityWithHeadroom() {
        int peak = 0;
        for (float step : STEPS) {
            for (int seed = 1; seed <= SEEDS; seed++) {
                Torch torch = new Torch(null, 100, 100, 16, 16, 200, "", seed);
                for (int f = 0; f < FRAMES; f++) { torch.update(step); peak = Math.max(peak, torch.particles().count()); }
            }
        }
        System.out.println("TORCH PEAK LIVE PARTICLES: " + peak + " (capacity " + Torch.PARTICLE_CAPACITY + ")");
        assertTrue(peak * 3 / 2 <= Torch.PARTICLE_CAPACITY, "peak " + peak + " leaves less than 50% headroom in " + Torch.PARTICLE_CAPACITY);
    }
}
