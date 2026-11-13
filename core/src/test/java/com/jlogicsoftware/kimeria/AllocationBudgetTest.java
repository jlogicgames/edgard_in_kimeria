package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.effects.KimeriaBloodSystem;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.effects.SoftDot;
import com.jlogicsoftware.kimeria.effects.Torch;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.entity.objects.HoneyFungus;
import com.jlogicsoftware.kimeria.testsupport.AllocationProbe;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.ui.BossHud;
import com.jlogicsoftware.kimeria.ui.FpsLabel;
import com.jlogicsoftware.kimeria.ui.HealerDialogueView;
import com.jlogicsoftware.kimeria.ui.Hud;
import com.jlogicsoftware.kimeria.ui.MenuBackdrop;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-frame allocation budget (#285, docs/DEVELOPMENT.md "Particles and the allocation guard"). Each scenario runs
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
    /** Measured 1,322.0 to 1,322.2 B/frame in 3 runs (#303: the index loops, the scratch rectangles and the HashSet removal; before: 2,441.8 to 2,441.9; step 1: 2,530.2 to 2,564.4; step 0: 4,491.8 to 4,525.5); one torch and 48 {@code RainDrop}s. Ceiling = highest run + about 10 %. */
    static final double FOREST_1_CEILING = 1450;
    /** Measured 992.1 to 1,068.3 B/frame in 3 isolated runs and 1,416.0 in the full suite (#303; the ceiling follows the full-suite run, whose JIT state differs; before: 8,920.6 to 9,000.0; step 1: 9,370.7 to 9,430.5; step 0: 10,243.0 to 10,317.4); two torches. The large drop is the for-each iterators over {@code Level.objects} and the per-object {@code Rectangle}s, not attributed object by object. Ceiling = highest run + about 10 %. */
    static final double FOREST_2_ROOM_0_CEILING = 1560;
    /** Measured 816.0 B/frame in 3 isolated runs and 1,128.0 in the full suite (#303; the ceiling follows the full-suite run; before: 2,184.0; step 1: 2,549.6 to 2,605.0; step 0: 3,374.5 to 3,451.0); three torches. Ceiling = highest run + about 10 %. */
    static final double FOREST_2_ROOM_1_CEILING = 1250;
    /** Measured 816.0 B/frame in 3 isolated runs and 1,128.0 in the full suite (#303; the ceiling follows the full-suite run; before: 1,960.0 to 1,960.6; step 3: 1,992.0; step 1: 2,651.3 to 2,656.8; step 0: 3,583.2 to 3,587.8); five torches, the boss room. An earlier run of this scene moved by more than 200 B with JIT state (1,512 to 1,712 against 1,960), so the ceiling keeps about 10 % over the full-suite measurement. */
    static final double FOREST_2_ROOM_2_CEILING = 1250;
    /** Measured 704.0 to 912.0 B/frame in 3 isolated runs and 824.0 to 902.1 in the full suite (#303; before: 1,991.9 to 1,992.6): the boss room plus an empowered spray through {@code Level.blood} every 60 frames, so blood adds nothing measurable to FOREST_2_ROOM_2_CEILING's scene. */
    static final double FOREST_2_ROOM_2_BLOOD_CEILING = 1250;

    /** Measured 0.0 B/frame in 3 runs (before step 4: 24.0, the returned {@code Vector2}; the 32 B {@code Rectangle} of {@code KimeriaGame.render} is not in this scenario, which already reuses its view). */
    static final double CAMERA_SHAKE_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#303: {@code Level.activateTrigger} reuses an {@code Array}; before: 4.5, the {@code HashSet} it made once per lever pull, about 270 B every 60 frames). The shake, its offset and {@code fx.update} are 0.0 (like CAMERA_SHAKE_CEILING). */
    static final double LEVER_SHAKE_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs: a 6-particle jump puff every 60 frames through {@code Level.fx()} (spawn, update, render). */
    static final double JUMP_DUST_CEILING = 8;
    /** Measured 0.1 B/frame in 3 runs (#303; before: 4.6, the {@code HashSet} of {@code Level.activateTrigger}, as in LEVER_SHAKE_CEILING): the grit burst (24 particles over 0.6 s), its update and render. */
    static final double LEVER_GRIT_CEILING = 8;

    /** Measured 0.0 B/frame in 3 runs (#303: the projection copy is a held {@code Matrix4}; before: 96.0 from {@code getProjectionMatrix().cpy()}, step 4: 160.0 with two {@code Color} copies). */
    static final double BOSS_HUD_CEILING = 8;

    /** Escapes the deliberate allocation of {@link #theProbeCanReadAllocatedBytes()} so escape analysis keeps it. */
    private static volatile byte[] sink;

    /** Measured 0.0 B/frame in 3 runs (#239 step 4): one {@code batch.draw} per bush, frame regions held in the object. */
    static final double DECOR_BRUSH_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#239 step 5): one rotated {@code batch.draw} per vine, the frame region held in the object. */
    static final double HANGING_VINE_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#295 phase 2): ten fungi idling, swelling and bursting 16 golden spores on a {@code KimeriaParticleSystem}, update and render through the batch. */
    static final double HONEY_FUNGUS_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#295 phase 2): ten fungi swelling, the live cloud tested against the hero every frame and drawn. #296: the same scenario now draws the {@code SporeCloud} (30 particles each); the measurement is in the report. Headless there is no GL context, so the shader layer is skipped ({@code Shaders.tryLoad} returns null) and the number is the particles alone. */
    static final double FALSE_HONEY_FUNGUS_CEILING = 8;
    private static final int FUNGUS_CYCLES = 10, FUNGUS_CYCLE_FRAMES = 150, FUNGUS_ENTER_FRAME = 40, FUNGUS_SPACING = 200, FUNGUS_FAR_X = 5000;
    private static final float FUNGUS_FLOOR = 320, FUNGUS_WIDTH = 24, FUNGUS_HEIGHT = 16, FUNGUS_ZONE = 64;
    private static final float HERO_WIDTH = 11, HERO_HEIGHT = 22;
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

    /** A sword kill sprays a light burst through {@code Level.mobBlood} every {@link #BURST_CYCLE_FRAMES} frames in the boss room (the level path of #319). */
    @Test void forest2Room2WithMobKillBlood() {
        Level level = harness.roomOf("forest-2", 2);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.mobBlood(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR);
            level.update(STEP); level.render(batch, view);
        });
        assertWithinBudget("forest-2 room 2 with mob kill blood", FOREST_2_ROOM_2_BLOOD_CEILING, bytes);
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

    /** A lever lowering the forest-0 wall through {@code Level.activateTrigger} every 60 frames (the shake lasts 1 s, so it is running nearly all the time), with {@code fx.update} and the offset read each frame. The room's own update and render are not in this scenario (they are the harness cost measured above). */
    @Test void leverShakeThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 2);
        Rectangle view = new Rectangle();
        Rectangle room = level.currentRoomBounds();
        float[] sum = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % 60 == 0) level.activateTrigger(level.triggers.get(0));
            level.fx().update(STEP);
            view.set(room.x, room.y, 640, 360);
            Vector2 shake = level.cameraShakeOffset(view);
            sum[0] += shake.x + shake.y;
        });
        assertTrue(sum[0] != 0, "the shake must actually run in this scenario");
        assertWithinBudget("lever shake", LEVER_SHAKE_CEILING, bytes);
    }

    /** A jump puff every {@link #BURST_CYCLE_FRAMES} frames through the real {@code Level.fx()} (a puff lives at most 0.4 s), with {@code fx.update} and {@code fx.render} each frame. */
    @Test void jumpDustThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 2);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.fx().dustPuff(100, 200);
            level.fx().update(STEP);
            level.fx().render(batch);
            peak[0] = Math.max(peak[0], level.fx().dust().count());
        });
        assertTrue(peak[0] > 0, "dust must actually rise in this scenario");
        assertWithinBudget("jump dust", JUMP_DUST_CEILING, bytes);
    }

    /** A landing puff every {@link #BURST_CYCLE_FRAMES} frames through the real {@code Level.fx()}, updated and drawn each frame. */
    @Test void landingDustThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 2);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.fx().landingPuff(100, 200);
            level.fx().update(STEP);
            level.fx().render(batch);
            peak[0] = Math.max(peak[0], level.fx().dust().count());
        });
        assertTrue(peak[0] > 0, "dust must actually rise in this scenario");
        assertWithinBudget("landing dust", JUMP_DUST_CEILING, bytes);
    }

    /** A lever pull every {@link #BURST_CYCLE_FRAMES} frames through {@code Level.activateTrigger}: the shake plus the grit burst over its whole life, drawn each frame. */
    @Test void leverGritThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 2);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.activateTrigger(level.triggers.get(0));
            level.fx().update(STEP);
            level.fx().render(batch);
            peak[0] = Math.max(peak[0], level.fx().grit().count());
        });
        assertTrue(peak[0] > 0, "grit must actually fall in this scenario");
        assertWithinBudget("lever grit", LEVER_GRIT_CEILING, bytes);
    }

    /** A bush brushed by a walking hero every {@link #BURST_CYCLE_FRAMES} frames (its animation lasts 0.3 s): {@code notice}, {@code update} and {@code render} through the batch. */
    @Test void decorBrushThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 0);
        com.jlogicsoftware.kimeria.entity.objects.Decor bush = level.decor.get(0);
        Rectangle hero = new Rectangle(bush.getX(), bush.getY(), 11, 22);
        boolean[] played = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) bush.notice(hero, 100);
            bush.update(STEP);
            bush.render(batch);
            played[0] |= bush.playing();
        });
        assertTrue(played[0], "the brush must actually play in this scenario");
        assertWithinBudget("decor brush", DECOR_BRUSH_CEILING, bytes);
    }

    /** The three vines of forest-0's stone room shaken by a stone every {@link #BURST_CYCLE_FRAMES} frames (a swing lasts 1.6 s, so one is always running): {@code shake}, {@code update} and {@code render} through the batch. */
    @Test void hangingVinesSwayingThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 3);
        var vines = level.vines;
        assertEquals(3, vines.size());
        boolean[] swayed = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) for (int v = 0; v < vines.size(); v++) vines.get(v).shake(2552, 1f);
            for (int v = 0; v < vines.size(); v++) { vines.get(v).update(STEP); vines.get(v).render(batch); }
            swayed[0] |= vines.get(0).swaying();
        });
        assertTrue(swayed[0], "the vines must actually sway in this scenario");
        assertWithinBudget("hanging vines", HANGING_VINE_CEILING, bytes);
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

    /**
     * Ten honey fungi, each entered by the hero in turn every {@value #FUNGUS_CYCLE_FRAMES} frames (one swell of 48 frames, a burst of
     * 16 golden spores that live up to 1 s, then spent): idle, trigger, swell, burst, particle update and the particle render through the
     * batch. Every object is built before the measurement: a fungus fires once, so the scene needs one per cycle.
     */
    @Test void honeyFungusBurstsInTurn() {
        HoneyFungus[] fungi = new HoneyFungus[FUNGUS_CYCLES];
        Rectangle hero = new Rectangle(0, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        for (int k = 0; k < fungi.length; k++)
            fungi[k] = new HoneyFungus(harness.assets(), harness.game().trapWarnings(), k * FUNGUS_SPACING, FUNGUS_FLOOR - FUNGUS_HEIGHT,
                FUNGUS_WIDTH, FUNGUS_HEIGHT, FUNGUS_ZONE, FUNGUS_ZONE, () -> hero);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = Math.min(i / FUNGUS_CYCLE_FRAMES, fungi.length - 1);
            hero.x = i % FUNGUS_CYCLE_FRAMES < FUNGUS_ENTER_FRAME ? -FUNGUS_SPACING / 2f : k * FUNGUS_SPACING;
            for (HoneyFungus f : fungi) { f.update(STEP); f.render(batch); }
            peak[0] = Math.max(peak[0], fungi[k].spores().count());
        });
        assertTrue(peak[0] >= HoneyFungus.SPORES_PER_BURST, "the spores must actually burst in this scenario: " + peak[0]);
        assertTrue(fungi[0].isSpent() && fungi[FUNGUS_CYCLES - 1].state() != FungusTrap.State.IDLE, "the cycle must run to the end");
        assertWithinBudget("honey fungus bursts", HONEY_FUNGUS_CEILING, bytes);
    }

    /** Two fungi that never get triggered: only the idle glow (tint set, draw, tint restored) runs every frame. */
    @Test void idleFungusGlow() {
        Rectangle far = new Rectangle(FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        HoneyFungus honey = new HoneyFungus(harness.assets(), harness.game().trapWarnings(), 0, FUNGUS_FLOOR - FUNGUS_HEIGHT,
            FUNGUS_WIDTH, FUNGUS_HEIGHT, FUNGUS_ZONE, FUNGUS_ZONE, () -> far);
        FalseHoneyFungus fake = new FalseHoneyFungus(harness.assets(), harness.game().trapWarnings(), FUNGUS_SPACING, FUNGUS_FLOOR - FUNGUS_HEIGHT,
            FUNGUS_WIDTH, FUNGUS_HEIGHT, FUNGUS_ZONE, FUNGUS_ZONE, () -> far);
        double bytes = AllocationProbe.bytesPerFrame(i -> { honey.update(STEP); fake.update(STEP); honey.render(batch); fake.render(batch); });
        assertEquals(FungusTrap.State.IDLE, honey.state());
        assertWithinBudget("idle fungus glow", HONEY_FUNGUS_CEILING, bytes);
    }

    /** The same cycle for the false honey fungus: swell, the live cloud's {@code lethalTo} against the hero every frame, the cloud draw, spent. */
    @Test void falseHoneyFungusCloudsInTurn() {
        FalseHoneyFungus[] fungi = new FalseHoneyFungus[FUNGUS_CYCLES];
        Rectangle hero = new Rectangle(0, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        for (int k = 0; k < fungi.length; k++)
            fungi[k] = new FalseHoneyFungus(harness.assets(), harness.game().trapWarnings(), k * FUNGUS_SPACING, FUNGUS_FLOOR - FUNGUS_HEIGHT,
                FUNGUS_WIDTH, FUNGUS_HEIGHT, FUNGUS_ZONE, FUNGUS_ZONE, () -> hero);
        boolean[] lethal = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = Math.min(i / FUNGUS_CYCLE_FRAMES, fungi.length - 1);
            hero.x = i % FUNGUS_CYCLE_FRAMES < FUNGUS_ENTER_FRAME ? -FUNGUS_SPACING / 2f : k * FUNGUS_SPACING;
            for (FalseHoneyFungus f : fungi) { f.update(STEP); f.render(batch); lethal[0] |= f.lethalTo(hero); }
        });
        assertTrue(lethal[0], "the cloud must actually be lethal to the standing hero in this scenario");
        assertWithinBudget("false honey fungus clouds", FALSE_HONEY_FUNGUS_CEILING, bytes);
    }

    // #303: the per-frame sites outside the particle code. Measured in 3 runs each: every scenario is 0.0 to 1.6 B/frame (the FPS label 1.6, the hint 0.9, the outlines 0.1 to 0.7), so the ceiling is the same 8 B as the particle scenarios.
    static final double STONE_FIREBALL_CEILING = 8, PASSIVE_OUTLINE_CEILING = 8, HUD_CEILING = 8, HUD_HINT_CEILING = 8,
        CHECKLIST_CEILING = 8, FPS_LABEL_CEILING = 8, GAMEPAD_CEILING = 8, MENU_BACKDROP_CEILING = 8;
    private static final float THROW_X = 3328, THROW_Y = 300, THROW_VX = 220, THROW_VY = -110;
    private static final int STONE_ROOM = 4, THROW_CYCLES = 30;
    private static final float REACH_SCAN = 40, REACH_SCAN_STEP = 2;
    private static final String HINT_TEXT = "Up + Action: turn the lever on", ZONE_TEXT = "Light the torch with the lever to open the way forward";
    private static final float HINT_X = 200, HINT_Y = 220, ZONE_X = 320, ZONE_Y = 120;
    private static final int FPS_SHOWN = 60, FPS_CHANGE_FRAMES = 30;
    private static final float LEVER_ROOM_FIRST_X = 24, LEVER_ROOM_LAST_X = 544, LEVER_ROOM_STEP_X = 8, LEVER_ROOM_FLOOR_Y = 272;
    private static final int GAMEPAD_BUTTONS = 20, GAMEPAD_SWAP_FRAMES = 60;

    /** A stone thrown every {@link #BURST_CYCLE_FRAMES} frames and a fireball shot at the same moment, in the stone room with its stump: {@code Carryable.update}, {@code Fireball.update}, {@code Level.blocksObject}, {@code Carryable.bounds}, {@code triggerBoxInto}. */
    @Test void thrownStoneAndFireballInTheStoneRoom() {
        Level level = harness.roomOf("forest-0", STONE_ROOM);
        Carryable stone = level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
        assertTrue(level.objects.stream().anyMatch(ExplosiveTrap.class::isInstance), "the stump must be in this scenario");
        Fireball[] shots = new Fireball[THROW_CYCLES];
        for (int k = 0; k < shots.length; k++) shots[k] = new Fireball(level, THROW_X, THROW_Y, true);
        Carryable.Snapshot thrown = new Carryable.Snapshot(THROW_X, THROW_Y, THROW_VX, THROW_VY, true, false);
        int[] flights = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            Fireball shot = shots[Math.min(i / BURST_CYCLE_FRAMES, shots.length - 1)];
            if (i % BURST_CYCLE_FRAMES == 0) { stone.restore(thrown); flights[0]++; }
            stone.update(STEP);
            shot.update(STEP);
        });
        assertTrue(flights[0] > 1, "stones must actually fly in this scenario");
        assertWithinBudget("thrown stone and fireball", STONE_FIREBALL_CEILING, bytes);
    }

    /** The hero beside the stone and a lever: the passive corner outlines of {@code Player.renderInteractionTarget}, each frame. */
    @Test void passiveInteractionOutlines() {
        Level level = harness.roomOf("forest-0", STONE_ROOM);
        Carryable stone = level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
        for (float dy = -REACH_SCAN; dy <= REACH_SCAN && level.player.selectedCarryable() == null; dy += REACH_SCAN_STEP)
            level.player.position.set(stone.getX() - HERO_WIDTH, stone.getY() + dy);
        assertNotNull(level.player.selectedCarryable(), "the stone must be in reach in this scenario");
        double bytes = AllocationProbe.bytesPerFrame(i -> level.player.renderInteractionTarget(batch));
        assertWithinBudget("passive interaction outlines", PASSIVE_OUTLINE_CEILING, bytes);
    }

    private Hud hudWithMetricsFont() {
        harness.font = new MetricsFont(Hud.CONTEXT_FONT_SIZE);
        return new Hud(harness.assets(), harness.game(), 640, 360);
    }

    /** The coin count, the FPS counter and the fireball stock of {@code Hud.render} with the boss bar, each frame. */
    @Test void hudCoinCountAndFps() {
        Hud hud = hudWithMetricsFont();
        Level level = harness.roomOf("forest-2", 2);
        double bytes = AllocationProbe.bytesPerFrame(i -> hud.render(batch, level.boss(), true, level.player, "60 FPS"));
        assertWithinBudget("Hud.render", HUD_CEILING, bytes);
    }

    /** A hero whose two hint channels are fixed texts, so the scenario measures the HUD's own layout and drawing, not the hero's hint derivation. */
    private static final class FixedHintPlayer extends Player {
        FixedHintPlayer(HeadlessLevelHarness harness) { super(harness.assets(), harness.game(), 0, 0); }
        @Override public String contextHint() { return HINT_TEXT; }
        @Override public String zoneHint() { return ZONE_TEXT; }
        @Override public void contextHintPosition(Vector2 out) { out.set(HINT_X, HINT_Y); }
        @Override public void zoneHintPosition(Vector2 out) { out.set(ZONE_X, ZONE_Y); }
    }

    /** The zone message and the contextual label together, each frame: layout, placement, shadow and text draw (the hint texts are fixed, see {@link FixedHintPlayer}). */
    @Test void hudHintAndZoneMessage() {
        Hud hud = hudWithMetricsFont();
        Player player = new FixedHintPlayer(harness);
        OrthographicCamera camera = new OrthographicCamera();
        camera.setToOrtho(true, 640, 360);
        camera.update();
        double bytes = AllocationProbe.bytesPerFrame(i -> hud.renderInteractionHint(batch, player, camera, STEP));
        assertWithinBudget("Hud.renderInteractionHint", HUD_HINT_CEILING, bytes);
    }

    /**
     * The healer's checklist text asked for every frame with unchanged inputs (the frame cost). The rebuild after a change is an event,
     * measured at about 4.3 KB (35.7 B/frame when the input flips every 120 frames), once per pickup, coin or language change.
     */
    @Test void healerChecklistText() {
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        int[] length = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> length[0] += view.checklistText(Language.ENGLISH, false,
            true, true, false, 1, 3).length());
        assertTrue(length[0] > 0, "the checklist must have text");
        assertWithinBudget("HealerDialogueView checklist", CHECKLIST_CEILING, bytes);
    }

    /** The debug FPS text asked for every frame while the rate changes every {@value #FPS_CHANGE_FRAMES} frames. */
    @Test void fpsLabelText() {
        FpsLabel label = new FpsLabel();
        int[] length = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> length[0] += label.text(FPS_SHOWN + i / FPS_CHANGE_FRAMES % 3).length());
        assertTrue(length[0] > 0);
        assertWithinBudget("FpsLabel", FPS_LABEL_CEILING, bytes);
    }

    /** A pad that is connected and disconnected every {@value #GAMEPAD_SWAP_FRAMES} frames, polled each frame (the button array is sized on each swap). */
    @Test void gamepadPollingAcrossReconnects() {
        ControllerMapping mapping = new ControllerMapping(0, 1, 2, 3, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15) { };
        Controller pad = new FakePad(mapping);
        boolean[] connected = new boolean[1];
        GamepadInput input = new GamepadInput() {
            @Override protected Controller controller() { return connected[0] ? pad : null; }
        };
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            connected[0] = i / GAMEPAD_SWAP_FRAMES % 2 == 0;
            input.update(false);
        });
        assertWithinBudget("GamepadInput.update", GAMEPAD_CEILING, bytes);
    }

    /** A pad as a plain class (a {@link Proxy} would allocate argument arrays on every call and drown the measurement). */
    private static final class FakePad implements Controller {
        private final ControllerMapping mapping;
        FakePad(ControllerMapping mapping) { this.mapping = mapping; }
        @Override public boolean getButton(int buttonCode) { return false; }
        @Override public float getAxis(int axisCode) { return 0; }
        @Override public String getName() { return "fake"; }
        @Override public String getUniqueId() { return "fake"; }
        @Override public int getMinButtonIndex() { return 0; }
        @Override public int getMaxButtonIndex() { return GAMEPAD_BUTTONS - 1; }
        @Override public int getAxisCount() { return 4; }
        @Override public boolean isConnected() { return true; }
        @Override public boolean canVibrate() { return false; }
        @Override public boolean isVibrating() { return false; }
        @Override public void startVibration(int duration, float strength) { }
        @Override public void cancelVibration() { }
        @Override public boolean supportsPlayerIndex() { return false; }
        @Override public int getPlayerIndex() { return 0; }
        @Override public void setPlayerIndex(int index) { }
        @Override public ControllerMapping getMapping() { return mapping; }
        @Override public com.badlogic.gdx.controllers.ControllerPowerLevel getPowerLevel() { return null; }
        @Override public void addListener(com.badlogic.gdx.controllers.ControllerListener listener) { }
        @Override public void removeListener(com.badlogic.gdx.controllers.ControllerListener listener) { }
    }

    /** The menu fireflies: the loops of {@code MenuBackdrop.update} (its fog needs a GPU shader, so only the update is measured). */
    @Test void menuBackdropUpdate() {
        MenuBackdrop backdrop = new MenuBackdrop();
        double bytes = AllocationProbe.bytesPerFrame(i -> backdrop.update(STEP));
        assertWithinBudget("MenuBackdrop.update", MENU_BACKDROP_CEILING, bytes);
    }
}
