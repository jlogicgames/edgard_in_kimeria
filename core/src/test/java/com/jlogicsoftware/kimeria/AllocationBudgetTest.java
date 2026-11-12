package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.effects.BloodBurst;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-frame allocation budget (#285, docs/DEVELOPMENT.md "Particle system plan", section 13). Each scenario runs
 * {@value AllocationProbe#WARMUP_FRAMES} warm-up and {@value AllocationProbe#MEASURED_FRAMES} measured frames of
 * {@code update(1/60)} plus {@code render} through a {@link NoopBatch} and compares the bytes the thread allocated
 * per frame with a ceiling.
 *
 * <p>Step 0 sets every ceiling to today's measured value plus headroom for run-to-run variation (a few percent), so the
 * old code passes and a regression fails. Steps 1 to 4 of the plan lower the ceilings they earn; a ceiling that is
 * raised is reported and recorded in docs/DECISIONS.md with the measured value and why (AGENTS.md, tolerance rule).
 *
 * <p>The room scenes run through {@link HeadlessLevelHarness}, whose proxied {@code Gdx} and {@code GameContext} objects
 * allocate on every call (argument arrays, boxing, a {@code Vector2} per {@code logicalResolution()}), so their numbers
 * include that harness cost; the ceilings guard the trend and a regression, they are not the cost in the real game.
 * HotSpot only: nothing here speaks for TeaVM, GC pauses or frame time, and escape analysis hides some allocations.
 */
class AllocationBudgetTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int BURST_CYCLE_FRAMES = 60;
    private static final float TORCH_X = 100, TORCH_Y = 100, TORCH_SIZE = 16;
    private static final int TORCH_INTENSITY = 200;
    private static final float BLOOD_X = 100, BLOOD_Y = 90, BLOOD_FLOOR = 110;

    // Measured 2026-11-11 on HotSpot (GraalVM CE 25), update + render, 3 runs each. Each ceiling is the highest run plus about 8 %.
    // Step 1 (cached regions) and step 2 (Torch on ParticleSystem) lowered the ceilings; earlier values are in the comments. The remaining room cost is the harness and level/entity code (plan, section 12).
    /** Measured 0.1 B/frame in 3 runs (step 1: 149.4 to 151.3; step 0: 190.9 to 195.1): step 2 put the torch on the allocation-free ParticleSystem. */
    static final double TORCH_CEILING = 8;
    /** Measured 32.8 B/frame averaged over a 60-frame cycle (step 0: 97.6): exactly the 1,968 B constructor per cycle; render allocates 0. */
    static final double BLOOD_BURST_CEILING = 36;
    /** Measured 2,441.8 to 2,441.9 B/frame (step 1: 2,530.2 to 2,564.4; step 0: 4,491.8 to 4,525.5); one torch and 48 {@code RainDrop}s. */
    static final double FOREST_1_CEILING = 2640;
    /** Measured 8,920.6 to 9,000.0 B/frame (step 1: 9,370.7 to 9,430.5; step 0: 10,243.0 to 10,317.4); two torches; the cause of the large {@code update} share is not attributed (plan, section 8). */
    static final double FOREST_2_ROOM_0_CEILING = 9720;
    /** Measured 2,184.0 B/frame in 3 runs (step 1: 2,549.6 to 2,605.0; step 0: 3,374.5 to 3,451.0); three torches. */
    static final double FOREST_2_ROOM_1_CEILING = 2360;
    /** Measured 1,992.0 to 1,992.1 B/frame (step 1: 2,651.3 to 2,656.8; step 0: 3,583.2 to 3,587.8); five torches, the boss room. */
    static final double FOREST_2_ROOM_2_CEILING = 2150;

    /** Escapes the deliberate allocation of {@link #theProbeCanReadAllocatedBytes()} so escape analysis keeps it. */
    private static volatile byte[] sink;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() {
        // The cached GPU textures were made under the stubbed GL; drop them so no other test inherits them.
        SoftDot.dispose();
        Shaders.dispose();
        harness.restoreGlobals();
    }

    private static void assertWithinBudget(String scene, double ceiling, double measured) {
        System.out.printf("ALLOCATION %s: %.1f B/frame (ceiling %.1f)%n", scene, measured, ceiling);
        assertTrue(measured <= ceiling, scene + " allocates " + measured + " B/frame, above its ceiling of " + ceiling
            + " B/frame. Lower it only by removing allocation; raising it needs a report and a docs/DECISIONS.md entry.");
    }

    @Test void theProbeCanReadAllocatedBytes() {
        // Fails, never skips, when the JVM cannot report per-thread allocation: the whole guard would be decoration.
        double bytes = AllocationProbe.bytesPerFrame(i -> sink = new byte[1024]);
        assertTrue(bytes >= 1024, "the probe must see a deliberate 1 KB allocation per frame, saw " + bytes);
    }

    @Test void oneBurningTorch() {
        Torch torch = new Torch(TORCH_X, TORCH_Y, TORCH_SIZE, TORCH_SIZE, TORCH_INTENSITY, "");
        double bytes = AllocationProbe.bytesPerFrame(i -> { torch.update(STEP); torch.render(batch); });
        assertWithinBudget("one Torch", TORCH_CEILING, bytes);
    }

    @Test void oneBloodBurstOverItsWholeLife() {
        BloodBurst[] burst = new BloodBurst[1];
        // A burst lives .9 s (54 frames): a new one every 60 frames, so the constructor is part of the average.
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) burst[0] = new BloodBurst(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR);
            burst[0].update(STEP);
            burst[0].render(batch);
        });
        assertWithinBudget("one BloodBurst", BLOOD_BURST_CEILING, bytes);
    }

    private double room(String map, int index) {
        Level level = harness.roomOf(map, index);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        return AllocationProbe.bytesPerFrame(i -> { level.update(STEP); level.render(batch, view); });
    }

    @Test void forest1() { assertWithinBudget("forest-1", FOREST_1_CEILING, room("forest-1", 0)); }
    @Test void forest2Room0() { assertWithinBudget("forest-2 room 0", FOREST_2_ROOM_0_CEILING, room("forest-2", 0)); }
    @Test void forest2Room1() { assertWithinBudget("forest-2 room 1", FOREST_2_ROOM_1_CEILING, room("forest-2", 1)); }
    @Test void forest2Room2() { assertWithinBudget("forest-2 room 2", FOREST_2_ROOM_2_CEILING, room("forest-2", 2)); }
}
