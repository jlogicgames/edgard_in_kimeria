package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.effects.KimeriaBloodSystem;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.ui.BossHud;
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
    private static final long BLOOD_SEED = 7264;

    // Measured 2026-11-11 on HotSpot (GraalVM CE 25), update + render, 3 runs each. Each ceiling is the highest run plus about 8 %.
    // Step 1 (cached regions) and step 2 (Torch on KimeriaParticleSystem) lowered the ceilings; earlier values are in the comments. The remaining room cost is the harness and level/entity code (plan, section 12).
    /** Measured 0.1 B/frame in 3 runs (step 1: 149.4 to 151.3; step 0: 190.9 to 195.1): step 2 put the torch on the allocation-free KimeriaParticleSystem. */
    static final double TORCH_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs, a 32-drop spray every 60 frames with spawn, update and render (step 1: 32.8, step 0: 97.6 for the old {@code BloodBurst}); step 3 put blood on the allocation-free KimeriaBloodSystem. */
    static final double BLOOD_SPRAY_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs, a 48-drop empowered spray every 60 frames (old {@code BloodBurst}: 2,928 B constructor, about 49 B/frame over the cycle, read from the plan's section 1). */
    static final double BLOOD_EMPOWERED_SPRAY_CEILING = 8;
    /** Measured 2,441.8 to 2,441.9 B/frame (step 1: 2,530.2 to 2,564.4; step 0: 4,491.8 to 4,525.5); one torch and 48 {@code RainDrop}s. */
    static final double FOREST_1_CEILING = 2640;
    /** Measured 8,920.6 to 9,000.0 B/frame (step 1: 9,370.7 to 9,430.5; step 0: 10,243.0 to 10,317.4); two torches; the cause of the large {@code update} share is not attributed (plan, section 8). */
    static final double FOREST_2_ROOM_0_CEILING = 9720;
    /** Measured 2,184.0 B/frame in 3 runs (step 1: 2,549.6 to 2,605.0; step 0: 3,374.5 to 3,451.0); three torches. */
    static final double FOREST_2_ROOM_1_CEILING = 2360;
    /** Measured 1,960.0 to 1,960.6 B/frame in 3 runs (step 4: the {@code Boss.render} {@code Color} copy, -32, from a paired old/new run of the same test class; a later run of the final code, after scenarios were added, read 1,512 to 1,712 on the same scene, so JIT state moves this scene by more than the 32 B and the ceiling stays at the first measurement plus 8 %; step 3: 1,992.0 to 1,992.1; step 1: 2,651.3 to 2,656.8; step 0: 3,583.2 to 3,587.8); five torches, the boss room. */
    static final double FOREST_2_ROOM_2_CEILING = 2120;
    /** Measured 1,991.9 to 1,992.6 B/frame in 3 runs: the boss room plus an empowered spray through {@code Level.blood} every 60 frames, so blood adds nothing measurable to FOREST_2_ROOM_2_CEILING's scene (step 4: 1,960.0 in 3 runs). */
    static final double FOREST_2_ROOM_2_BLOOD_CEILING = 2120;

    /** Measured 0.0 B/frame in 3 runs (before step 4: 24.0, the returned {@code Vector2}; the 32 B {@code Rectangle} of {@code KimeriaGame.render} is not in this scenario, which already reuses its view). */
    static final double CAMERA_SHAKE_CEILING = 8;
    /** Measured 96.0 B/frame in 3 runs (before step 4: 160.0, two {@code Color} copies). The 96 B left is {@code batch.getProjectionMatrix().cpy()} in {@code BossHud.render}, outside step 4 (plan, section 12). */
    static final double BOSS_HUD_CEILING = 104;

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

    private double blood(boolean empowered) {
        KimeriaBloodSystem blood = new KimeriaBloodSystem(Level.BLOOD_CAPACITY, BLOOD_SEED);
        // A burst lives .9 s (54 frames): a new spray every 60 frames, so the spawn is part of the average.
        return AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) blood.spray(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR, empowered);
            blood.update(STEP);
            blood.render(batch);
        });
    }

    @Test void oneBloodSprayOverItsWholeLife() { assertWithinBudget("one blood spray", BLOOD_SPRAY_CEILING, blood(false)); }
    @Test void oneEmpoweredBloodSprayOverItsWholeLife() {
        assertWithinBudget("one empowered blood spray", BLOOD_EMPOWERED_SPRAY_CEILING, blood(true));
    }

    private double room(String map, int index) {
        Level level = harness.roomOf(map, index);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        return AllocationProbe.bytesPerFrame(i -> { level.update(STEP); level.render(batch, view); });
    }

    /** The boss room with an empowered spray every {@link #BURST_CYCLE_FRAMES} frames through {@code Level.blood}: the level path, not just the system. */
    @Test void forest2Room2WithBlood() {
        Level level = harness.roomOf("forest-2", 2);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.blood(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR, true);
            level.update(STEP); level.render(batch, view);
        });
        assertWithinBudget("forest-2 room 2 with blood", FOREST_2_ROOM_2_BLOOD_CEILING, bytes);
    }

    @Test void forest1() { assertWithinBudget("forest-1", FOREST_1_CEILING, room("forest-1", 0)); }
    @Test void forest2Room0() { assertWithinBudget("forest-2 room 0", FOREST_2_ROOM_0_CEILING, room("forest-2", 0)); }
    @Test void forest2Room1() { assertWithinBudget("forest-2 room 1", FOREST_2_ROOM_1_CEILING, room("forest-2", 1)); }
    @Test void forest2Room2() { assertWithinBudget("forest-2 room 2", FOREST_2_ROOM_2_CEILING, room("forest-2", 2)); }

    /** The camera shake query as {@code KimeriaGame.render} makes it every frame: a reused view rectangle, the offset read at once. A shake is re-armed every 10 frames so the sine path runs. */
    @Test void cameraShakeQuery() {
        Level level = harness.roomOf("forest-2", 2);
        Rectangle view = new Rectangle();
        Rectangle room = level.currentRoomBounds();
        float[] sum = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % 10 == 0) level.shakeAfterBossAttack();
            view.set(room.x, room.y, 640, 360);
            Vector2 shake = level.cameraShakeOffset(view);
            sum[0] += shake.x + shake.y;
        });
        assertWithinBudget("camera shake query", CAMERA_SHAKE_CEILING, bytes);
    }

    /** {@code BossHud.render} with an empowered boss; the font's {@code draw} is a no-op so only the HUD's own allocation is counted (a real {@code font.draw} allocates a {@code GlyphLayout}, out of scope here). */
    @Test void bossHud() {
        BitmapFont font = new BitmapFont() {
            @Override public com.badlogic.gdx.graphics.g2d.GlyphLayout draw(com.badlogic.gdx.graphics.g2d.Batch b, CharSequence s, float x, float y) { return null; }
        };
        harness.font = font;
        BossHud hud = new BossHud(harness.assets(), harness.game(), new Matrix4(), 640, 360);
        Level level = harness.roomOf("forest-2", 2);
        double bytes = AllocationProbe.bytesPerFrame(i -> hud.render(batch, level.boss(), true));
        font.dispose();
        assertWithinBudget("BossHud.render", BOSS_HUD_CEILING, bytes);
    }
}
