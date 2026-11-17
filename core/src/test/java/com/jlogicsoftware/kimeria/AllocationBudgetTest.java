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
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.objects.FallingBranch;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.ui.BossHud;
import com.jlogicsoftware.kimeria.ui.FontSizes;
import com.jlogicsoftware.kimeria.ui.FpsLabel;
import com.jlogicsoftware.kimeria.ui.HealerDialogue;
import com.jlogicsoftware.kimeria.ui.HealerDialogueView;
import com.jlogicsoftware.kimeria.ui.IntroScreen;
import com.jlogicsoftware.kimeria.ui.Overlay;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import com.badlogic.gdx.utils.viewport.Viewport;
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
    /**
     * Measured 290.3 to 602.3 B/frame in 3 isolated runs (#375, D85, 2026-11-17: 601.8, 602.3, 290.3; 602.2 in the full suite): the trap course,
     * now room 2, after #375 moved its first RedMob and its refill to forest-1-mob and removed its lever and torch, with 48 {@code RainDrop}s.
     * Ceiling = highest run + about 10 %. History: 1,322.0 to 1,322.2 B/frame with that torch (#303, ceiling 1450); before #303 2,441.8 to 2,441.9;
     * step 1: 2,530.2 to 2,564.4; step 0: 4,491.8 to 4,525.5.
     */
    static final double FOREST_1_CEILING = 665;
    /** Measured 1,025.6 to 1,026.3 B/frame in isolated and full-suite runs, bat alive (#187, D67, 2026-11-15), the same with the hero riding the lift (forest1RouteRoomRidingTheLift): the forest-1 route room (a running lift, a lever, a raised ladder, a refill; the level-wide sky and rain are drawn here too), update + render. Ceiling = highest run + about 10 %. */
    static final double FOREST_1_ROUTE_CEILING = 1130;
    /**
     * Measured 345.8 to 561.8 B/frame in 3 isolated runs (#375, D85, 2026-11-17: 561.8, 561.8, 345.8; 561.8 in the full suite): forest-1's first
     * room at rest (the RedMob asleep, the refill; the level-wide sky and rain are drawn here too), update + render. Ceiling = highest run + about 10 %.
     */
    static final double FOREST_1_MOB_CEILING = 620;
    /** Measured 1,162.1 to 1,162.2 B/frame (guard asleep, 3 isolated runs and the full class) and 849.7 to 1,017.8 B/frame (guard awake: JIT-dependent, always below the asleep scene) (#104, D68, 2026-11-15; the guard's attack adds nothing measurable): the forest-1 guard room (a lever, a raised ladder, the tunnel with its guard and spore puffball, the roof, the refill; the level-wide sky and rain are drawn here too), update + render. Ceiling = highest run + about 10 %. */
    static final double FOREST_1_GUARD_CEILING = 1280;
    /**
     * Measured 513.8 to 564.5 B/frame in 10 isolated runs of the class (#418, D86, 2026-11-17): at rest 513.8 to 561.9, riding the lift 561.6 to 562.3, pulling the lever 564.3 to 564.5
     * (one earlier run of the rest scene: 345.8; JIT state, D83; the mob, route and tree scenes moved together with it): the forest-1 lever lesson room (a running lift, a lever, a raised ladder; no enemy or hazard; the level-wide sky and rain
     * are drawn here too), update + render. Ceiling = the highest run (564.5, pulling the lever) + about 10 %, the rule D85 (#375) set for the other forest-1 rooms; the riding scene
     * measures the same as the route room's here (561.8), as it should: the cost is the harness and level code (D83).
     */
    static final double FOREST_1_LEVER_CEILING = 620;
    /**
     * Measured 537.8 to 897.8 B/frame in 5 full-suite runs of {@code :core:test} and {@code :core:check} (#349, D80, 2026-11-16, with the CollideBody
     * index loops: 801.8, 801.8, 537.8, 897.8, 897.8; before them 713.9 isolated and 881.7 in the full suite): the forest-1 tree room at rest (two
     * trunks, five rotten and six sound branches; the level-wide sky and rain are drawn here too), update + render. The value moves with JIT state
     * together with the other room scenes (the 897.8 runs had the route room at 897.9 and the guard room at 906.2). The cost is the harness and
     * level code, as the other room scenarios; the ceiling is the highest full-suite run plus about 10 %, the whole-room convention of
     * FOREST_1_ROUTE_CEILING (1130) and FOREST_1_GUARD_CEILING (1280). A first ceiling of 880, set from the first 3 runs, failed the next 2.
     */
    static final double FOREST_1_TREE_CEILING = 990;
    /**
     * Measured 611.3 to 923.3 B/frame in the same 5 full-suite runs (#349, D80, 2026-11-16; before the CollideBody index loops 1,050.8 to 1,050.9
     * isolated): the tree room with the hero riding a rotten branch down every 4 s. A control run before the loops change, with the same teleport
     * onto a sound branch and nothing falling, measured 1,025.8 against 1,050.9, so the warning, the crumble frames, the fall with the hero and
     * the D69 floor guard add about 25 B/frame: the shared warning light ({@code Torch}) spawned at each trigger, as the log's is. The rest is
     * the harness and level code, as the other room scenarios. Ceiling = highest full-suite run + about 10 %.
     */
    static final double FOREST_1_TREE_RIDE_CEILING = 1020;
    /** The rider path alone (see {@link #ridingARunningEscalatorCostsNoMoreThanStandingStill}); the same 8 B ceiling as the other isolated paths. */
    static final double RIDER_PATH_CEILING = 8;
    private static final int RIDER_PATH_PAIRS = 15, RIDER_PATH_WINDOW_FRAMES = 300;
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

    /** Measured in 3 runs (#347): a 24-particle stone landing every 60 frames, spawn, update and render, like JUMP_DUST_CEILING. */
    static final double STONE_IMPACT_CEILING = 8;

    /** Measured 0.0 B/frame in 3 runs (#303: the projection copy is a held {@code Matrix4}; before: 96.0 from {@code getProjectionMatrix().cpy()}, step 4: 160.0 with two {@code Color} copies). */
    static final double BOSS_HUD_CEILING = 8;

    /** Escapes the deliberate allocation of {@link #theProbeCanReadAllocatedBytes()} so escape analysis keeps it. */
    private static volatile byte[] sink;

    /** Measured 0.0 B/frame in 3 runs (#239 step 4): one {@code batch.draw} per bush, frame regions held in the object. */
    static final double DECOR_BRUSH_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#345): a coin, a Bat wing and a Mushroom with the whole pickup cue (frame animation, the 2 px bob, the additive halo with its blend switches, the sparkle), update and render through the batch. */
    static final double COLLECTABLE_CUE_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#239 step 5): one rotated {@code batch.draw} per vine, the frame region held in the object. */
    static final double HANGING_VINE_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#295 phase 2): ten fungi idling, swelling and bursting 16 golden spores on a {@code KimeriaParticleSystem}, update and render through the batch. */
    static final double HONEY_FUNGUS_CEILING = 8;
    /** Measured 0.0 B/frame in 3 runs (#295 phase 2): ten fungi swelling, the live cloud tested against the hero every frame and drawn. #296: the same scenario now draws the {@code SporeCloud} (30 particles each); the measurement is in the report. Headless there is no GL context, so the shader layer is skipped ({@code Shaders.tryLoad} returns null) and the number is the particles alone. */
    static final double FALSE_HONEY_FUNGUS_CEILING = 8;
    /** Measured 0.0 B/frame (#343, D79): the hero's draw path, every state in both facings, the three ladder clips and #341's three pull frames with the wrist, with the held-object grip, through the batch. */
    static final double HERO_DRAW_CEILING = 8;
    /**
     * Measured in 3 runs (#341, D81): the silver thread beat through the real Player, every stage drawn each cycle
     * (drawing out, tightening with the glint, the pull, the landing with the slack thread under the fade-in, the pit
     * arrival dissolving it) with #343's pull frames drawn through showPull, 0.6 to 0.7 B/frame (2026-11-17; the hero's
     * own draw path measures 0.6 in heroDrawPathInBothFacingsAndOnTheLadder; 0.0 before #343's frame selection); one
     * dying hero updated every frame, 0.0 to 0.1 B/frame once the 40.0 B/frame of the harness's Proxy stubs (a boxed
     * frame time, the pause key's argument array) are taken off.
     */
    static final double SILVER_THREAD_CEILING = 8;
    private static final float THREAD_DEATH_X = 300, THREAD_START_X = 32, THREAD_FLOOR_TOP = 272;
    private static final int THREAD_STAGES = 12, THREAD_STAGE_FRAMES = 4;
    private static final Player.State[] HERO_STATES = Player.State.values();
    private static final int HERO_STATE_FRAMES = 12, HERO_FACING_FRAMES = 5, HERO_PULL_FRAMES = 7;
    private static final int FUNGUS_CYCLES = 10, FUNGUS_CYCLE_FRAMES = 150, FUNGUS_ENTER_FRAME = 40, FUNGUS_SPACING = 200, FUNGUS_FAR_X = 5000;
    private static final float FUNGUS_FLOOR = 320, FUNGUS_WIDTH = 24, FUNGUS_HEIGHT = 16, FUNGUS_ZONE = 64, TRAP_TILE = 16, ROOT_HEIGHT = 24;
    private static final float HERO_WIDTH = 11, HERO_HEIGHT = 22;
    /** #361: the hero walks under a new branch every 90 frames (a 0.8 s warning, then about 0.3 s of fall); 17 cover warm-up and measure. */
    private static final int BRANCH_CYCLES = 17, BRANCH_CYCLE_FRAMES = 90;
    private static final float BRANCH_Y = 240, BRANCH_WIDTH = 32, BRANCH_HEIGHT = 16;
    /** Measured 0.0 to 0.1 B/frame in 7 of 8 runs and 6.0 in one (#361; 736.6 before the branch cached its four frames and the fall reused its two contact boxes). */
    static final double FALLING_BRANCH_CEILING = 8;
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
    @Test void forest2Room3WithBlood() {
        Level level = harness.roomOf("forest-2", 3);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.blood(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR, true);
            level.update(STEP); level.render(batch, view);
        });
        assertWithinBudget("forest-2 room 2 with blood", FOREST_2_ROOM_2_BLOOD_CEILING, bytes);
    }

    /** A sword kill sprays a light burst through {@code Level.mobBlood} every {@link #BURST_CYCLE_FRAMES} frames in the boss room (the level path of #319). */
    @Test void forest2Room3WithMobKillBlood() {
        Level level = harness.roomOf("forest-2", 3);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.mobBlood(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR);
            level.update(STEP); level.render(batch, view);
        });
        assertWithinBudget("forest-2 room 2 with mob kill blood", FOREST_2_ROOM_2_BLOOD_CEILING, bytes);
    }

    /** The first-RedMob room (#375): the mob asleep, the refill, the level-wide sky and rain. */
    @Test void forest1MobRoom() { assertWithinBudget("forest-1 mob room", FOREST_1_MOB_CEILING, room("forest-1", 0)); }
    @Test void forest1() { assertWithinBudget("forest-1", FOREST_1_CEILING, room("forest-1", 1)); }
    @Test void forest1RouteRoom() { assertWithinBudget("forest-1 route room", FOREST_1_ROUTE_CEILING, room("forest-1", 3)); }
    /** The guard room (#104, D68): the RedMob asleep in its tunnel, the puffball cycling (swell and cloud included), a lever, a raised ladder, the roof and its refill. */
    @Test void forest1GuardRoom() { assertWithinBudget("forest-1 guard room", FOREST_1_GUARD_CEILING, room("forest-1", 4)); }

    /** The tree room (#349, D80): two trunks, five rotten branches at rest, six sound branches; the level-wide sky and rain are drawn here too. */
    @Test void forest1TreeRoom() { assertWithinBudget("forest-1 tree room", FOREST_1_TREE_CEILING, room("forest-1", 5)); }
    /** The lever lesson room (#418, D86): a running lift, a lever, a raised ladder; the level-wide sky and rain are drawn here too. */
    @Test void forest1LeverRoom() { assertWithinBudget("forest-1 lever room", FOREST_1_LEVER_CEILING, room("forest-1", 2)); }

    /**
     * The tree room with the hero riding a rotten branch down (D69 path: the snap to a sinking platform, the solid-floor guard, then the floor):
     * every {@value #RIDE_CYCLE_FRAMES} frames the top branch is restored intact and the hero is stood on it, so its warning, its crumble frames,
     * its fall with the hero and its removal all run inside the measured window. Each trigger spawns the shared warning light (a Torch), as the log's does.
     */
    @Test void forest1TreeRoomRidingARottenBranchDown() {
        Level level = harness.roomOf("forest-1", 5);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        FallingPlatform top = level.fallingPlatforms.get(level.fallingPlatforms.size() - 1);
        FallingPlatform.Snapshot intact = top.snapshot();
        boolean[] rode = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % RIDE_CYCLE_FRAMES == 0) {
                if (!level.fallingPlatforms.contains(top)) level.fallingPlatforms.add(top);
                top.restore(intact);
                level.player.position.set(top.getX() + top.getWidth() / 2 - HERO_CENTRE_OFFSET_X, top.getY() - HERO_FRAME_SIZE);
                level.player.velocity.setZero();
            }
            level.update(STEP); level.render(batch, view);
            if (level.player.isGotHit()) throw new AssertionError("the hero was hit at frame " + i);
            rode[0] |= top.isFalling() && top.getY() > intact.y() + RIDE_CHECK_DROP;
        });
        assertTrue(rode[0], "the scene must have the branch fall");
        assertWithinBudget("forest-1 tree room, riding a rotten branch down", FOREST_1_TREE_RIDE_CEILING, bytes);
    }
    /** A rotten branch warns 1 s, then falls 200 px in 1.5 s; 4 s also lets the hero land on the floor and stand. */
    private static final int RIDE_CYCLE_FRAMES = 240;
    private static final float HERO_FRAME_SIZE = 48, RIDE_CHECK_DROP = 32;

    /**
     * The guard room with the RedMob awake: every {@value #GUARD_CYCLE_FRAMES} frames, in the calm half of the puffball's cycle, the hero is put for one
     * frame within the guard's whip and then back at the entrance, so the guard's attack (wind-up, strike, recovery: 48 frames) runs over and over
     * against nobody: the hero is never hurt and nothing dies. The puffball's swell and cloud run in the other half of every cycle.
     */
    @Test void forest1GuardRoomWithTheGuardAwake() {
        Level level = harness.roomOf("forest-1", 4);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        var guard = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.enemy.RedMob.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.enemy.RedMob.class::cast).findFirst().orElseThrow();
        boolean[] attacked = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int phase = i % GUARD_CYCLE_FRAMES;
            if (phase == CALM_FRAME) {          // the whip's reach: the guard begins its attack
                level.player.position.set(HERO_AT_WHIP_X - HERO_CENTRE_OFFSET_X, ENTRANCE_Y);
                level.player.velocity.setZero();
            } else if (phase == CALM_FRAME + 1) {   // gone: the attack finishes on its own, hitting air
                level.player.position.set(ENTRANCE_X, ENTRANCE_Y);
                level.player.velocity.setZero();
            }
            level.update(STEP); level.render(batch, view);
            if (level.player.isGotHit()) throw new AssertionError("the hero was hit at frame " + i);
            attacked[0] |= guard.getState() == com.jlogicsoftware.kimeria.entity.enemy.RedMob.State.ATTACK;
        });
        assertTrue(attacked[0], "the scene must have the guard attack");
        assertWithinBudget("forest-1 guard room, guard awake", FOREST_1_GUARD_CEILING, bytes);
    }
    /** The puffball cycles every 4 s (240 frames) from phase 2.2 s: swell 0-48, cloud 48-108, calm 108-240; the attack takes 48 frames, so it ends before the next swell. */
    private static final int GUARD_CYCLE_FRAMES = 240, CALM_FRAME = 120;
    /** forest-1-guard: the hero's hitbox centre sits 23.5 px right of {@code position.x}; the guard's whip reaches x 2394-2400 on the floor, so a hero centred at x 2390 is in it; the entrance is (2176, 272). */
    private static final float HERO_CENTRE_OFFSET_X = 23.5f, HERO_AT_WHIP_X = 2390, ENTRANCE_X = 2176, ENTRANCE_Y = 272;

    /** The route room with the hero standing on the lift the whole time, so the rider path of {@code Player} runs every frame (it allocated a {@code Vector2} each frame before the #187 review). */
    @Test void forest1RouteRoomRidingTheLift() {
        Level level = harness.roomOf("forest-1", 3);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        var lift = level.escalators.get(0);
        level.player.position.set(lift.getX() + 3, lift.getY() - 48);
        for (int i = 0; i < 5; i++) level.update(STEP);
        org.junit.jupiter.api.Assertions.assertSame(lift, level.player.currentEscalator(), "the scene must have the hero riding");
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            level.update(STEP); level.render(batch, view);
            if (level.player.currentEscalator() != lift) throw new AssertionError("fell off the lift at frame " + i);
        });
        assertWithinBudget("forest-1 route room riding the lift", FOREST_1_ROUTE_CEILING, bytes);
    }

    /** The lever room with the hero standing on the lift the whole time (the rider path of {@code Player} runs every frame), as the route room's riding scene. */
    @Test void forest1LeverRoomRidingTheLift() {
        Level level = harness.roomOf("forest-1", 2);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        var lift = level.escalators.get(0);
        level.player.position.set(lift.getX() + 3, lift.getY() - 48);
        for (int i = 0; i < 5; i++) level.update(STEP);
        org.junit.jupiter.api.Assertions.assertSame(lift, level.player.currentEscalator(), "the scene must have the hero riding");
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            level.update(STEP); level.render(batch, view);
            if (level.player.currentEscalator() != lift) throw new AssertionError("fell off the lift at frame " + i);
        });
        assertWithinBudget("forest-1 lever room riding the lift", FOREST_1_LEVER_CEILING, bytes);
    }

    /** The lever room with the hero on the slab pulling the lever (Up + Action) every {@value #PULL_CYCLE_FRAMES} frames: the ladder moves, the lift starts and stops, the lever animates. */
    @Test void forest1LeverRoomPullingTheLever() {
        Level level = harness.roomOf("forest-1", 2);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        level.player.position.set(4528 + 8 - HERO_CENTRE_OFFSET_X, 208 - HERO_FRAME_SIZE); // the lever's centre (x 4528 + 8), standing on the slab
        for (int i = 0; i < 5; i++) level.update(STEP);
        org.junit.jupiter.api.Assertions.assertTrue(level.player.canInteract(), "the scene must have the lever in reach");
        boolean[] changed = new boolean[1];
        boolean lowered = level.ladders.get(0).isLowered();
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int phase = i % PULL_CYCLE_FRAMES;
            harness.held.clear(); harness.pressed.clear();
            if (phase == 0 || phase == 1) harness.held.add(com.badlogic.gdx.Input.Keys.W);
            if (phase == 1) { harness.held.add(com.badlogic.gdx.Input.Keys.K); harness.pressed.add(com.badlogic.gdx.Input.Keys.K); }
            level.update(STEP); level.render(batch, view);
            if (level.ladders.get(0).isLowered() != lowered) changed[0] = true;
        });
        harness.held.clear(); harness.pressed.clear();
        assertTrue(changed[0], "the scene must have the lever pulled");
        assertWithinBudget("forest-1 lever room, pulling the lever", FOREST_1_LEVER_CEILING, bytes);
    }
    private static final int PULL_CYCLE_FRAMES = 60;

    /**
     * The rider path alone: {@code Player.update} costs about 560 B/frame in this harness whatever it stands on (D83: the stubbed input and graphics
     * proxies), which hides a 24-byte {@code Vector2}, so the test measures the <b>difference</b> between a hero riding a running horizontal
     * escalator and the same hero standing on the floor, with the same calls. The difference must stay within the 8 B of the other isolated
     * paths. The test takes the median of 15 paired 300-frame windows (riding then standing, then the reverse) after 2 warm-up rounds, because a
     * single window pair can straddle a JIT change that removes some of that 560 B (#391, D83). (Before the #187 review fix {@code Player} built a {@code Vector2} per frame while riding; the JIT may remove such a short-lived
     * object, so this guard is as strong as the JIT allows, and the fix itself is the allocation-free {@code Escalator.riderVelocityX()}.)
     */
    @Test void ridingARunningEscalatorCostsNoMoreThanStandingStill() {
        Level level = harness.roomOf("forest-1", 1);
        var escalator = level.escalators.stream().filter(e -> !e.isVertical && e.isRunning()).findFirst().orElseThrow();
        Runnable ride = () -> {
            level.player.position.set(escalator.getX() + 3, escalator.getY() - 48);
            for (int i = 0; i < 5; i++) { escalator.update(STEP); level.player.update(STEP); }
            org.junit.jupiter.api.Assertions.assertSame(escalator, level.player.currentEscalator(), "the hero rides");
        };
        Runnable stand = () -> {
            level.player.position.set(700, 272); // plain floor, no escalator
            for (int i = 0; i < 5; i++) { escalator.update(STEP); level.player.update(STEP); }
            org.junit.jupiter.api.Assertions.assertNull(level.player.currentEscalator(), "the baseline stands on the floor");
        };
        double difference = AllocationProbe.medianPairedDifference(RIDER_PATH_PAIRS, RIDER_PATH_WINDOW_FRAMES, ride,
            i -> {
                escalator.update(STEP); level.player.update(STEP);
                if (level.player.currentEscalator() != escalator) throw new AssertionError("left the escalator at frame " + i);
            }, stand, i -> { escalator.update(STEP); level.player.update(STEP); });
        System.out.printf("ALLOCATION rider path: median riding minus standing %.1f B/frame over %d pairs%n", difference, RIDER_PATH_PAIRS);
        assertWithinBudget("rider path on a horizontal escalator (riding minus standing)", RIDER_PATH_CEILING, difference);
    }

    /** #343: the hero picks a facing sheet (no mirrored draw) and, on a ladder, the up, down or hold clip; nothing of it may allocate. */
    @Test void heroDrawPathInBothFacingsAndOnTheLadder() {
        Level level = harness.roomOf("forest-0", 1);
        Player hero = level.player;
        Vector2 grip = new Vector2();
        float[] sum = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            hero.setState(HERO_STATES[(i / HERO_STATE_FRAMES) % HERO_STATES.length]);
            hero.facingRight = (i / HERO_FACING_FRAMES) % 2 == 0;
            hero.restoreAnimationTime(i * STEP);
            hero.showPull((i / HERO_PULL_FRAMES) % (Player.PULL_FRAMES + 1) - 1); // #341's pull: none, tug, lift, land
            hero.render(batch);
            hero.pullWrist(Player.PULL_TUG, grip);
            hero.heldObjectGrip(grip);
            sum[0] += grip.x + hero.frameX() + hero.frameY();
        });
        assertWithinBudget("hero draw path", HERO_DRAW_CEILING, bytes);
    }

    /** A hero at the room start of forest-1's first room, then moved to where he dies; {@code spawned} fixes the anchor there. */
    private Player threadHero(Level level, float x) {
        Player hero = new Player(harness.assets(), harness.game(), THREAD_START_X, THREAD_FLOOR_TOP);
        hero.setLevel(level);
        hero.spawned();
        hero.position.x = x;
        return hero;
    }

    @Test void silverThreadBeat() {
        Level level = harness.roomOf("forest-1", 1);
        // Built before the probe (allocation allowed): dying heroes at every stage of the beat, landing and arriving ones.
        Player[] stages = new Player[THREAD_STAGES];
        for (int k = 0; k < THREAD_STAGES; k++) {
            Player hero = threadHero(level, THREAD_DEATH_X);
            int age = k * THREAD_STAGE_FRAMES;
            if (k < THREAD_STAGES / 2) {
                hero.collidedWithActor(true);
                for (int f = 0; f < age * 2; f++) hero.update(STEP); // 0 .. 0.73 s: draw-out, tighten, pull
            } else if (k < THREAD_STAGES * 3 / 4) {
                hero.arriveUnderThread();
                hero.advanceLanding(age * STEP);
            } else {
                hero.arriveAfterReset();
                for (int f = 0; f < age; f++) hero.update(STEP);
            }
            stages[k] = hero;
        }
        Player dying = threadHero(level, THREAD_DEATH_X);
        dying.collidedWithActor(true);
        float[] sum = new float[1];
        double draw = AllocationProbe.bytesPerFrame(i -> {
            dying.render(batch);
            Player stage = stages[i % THREAD_STAGES];
            stage.render(batch);
            sum[0] += stage.frameX() + dying.frameY();
        });
        assertWithinBudget("silver thread beat, draw", SILVER_THREAD_CEILING, draw);
        // The dying update reads the real frame time and the pause key through the harness's java.lang.reflect.Proxy
        // stubs, which box the returned float and allocate the call's argument array; the real backends do neither.
        // Those two calls alone are measured and taken off, so the number is the update's own code.
        double proxies = AllocationProbe.bytesPerFrame(i -> {
            sum[0] += com.badlogic.gdx.Gdx.graphics.getDeltaTime();
            if (com.badlogic.gdx.Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) sum[0]++;
        });
        double update = AllocationProbe.bytesPerFrame(i -> dying.update(STEP));
        System.out.printf("ALLOCATION dying hero update: %.1f B/frame, of which the test proxies %.1f%n", update, proxies);
        assertWithinBudget("silver thread beat, dying update less the test proxies", SILVER_THREAD_CEILING, update - proxies);
    }

    /** The camera shake query as {@code KimeriaGame.render} makes it every frame: a reused view rectangle, the offset read at once. A shake is re-armed every 10 frames so the sine path runs. */
    @Test void cameraShakeQuery() {
        Level level = harness.roomOf("forest-2", 3);
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

    /** A falling stone landing every {@link #BURST_CYCLE_FRAMES} frames through the real {@code Level.fx()} (#347: dust, chips and debris, plus the shake), updated and drawn each frame. */
    @Test void stoneImpactThroughTheRealLevel() {
        Level level = harness.roomOf("forest-0", 2);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) { level.fx().stoneImpact(100, 200, 16); level.fx().shake().start(1.5f, .15f); }
            level.fx().update(STEP);
            level.fx().render(batch);
            peak[0] = Math.max(peak[0], level.fx().grit().count() + level.fx().dust().count());
        });
        assertTrue(peak[0] > 0, "the impact must actually throw particles in this scenario");
        assertWithinBudget("stone impact", STONE_IMPACT_CEILING, bytes);
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

    /** The shared pickup cue (#345): every collectable type, update and render, over many whole bobs and sparkles. */
    @Test void collectablesWithThePickupCue() {
        ItemType[] types = ItemType.values();
        Collectable[] items = new Collectable[types.length];
        for (int n = 0; n < types.length; n++)
            items[n] = new Collectable(harness.assets(), harness.game(), ignored -> {}, types[n].name(), n * TRAP_TILE * 2, FUNGUS_FLOOR, TRAP_TILE, TRAP_TILE);
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            for (int n = 0; n < items.length; n++) { items[n].update(STEP); items[n].render(batch); }
        });
        assertWithinBudget("collectables with the pickup cue", COLLECTABLE_CUE_CEILING, bytes);
    }

    /** {@code BossHud.render} with an empowered boss; the font's {@code draw} is a no-op so only the HUD's own allocation is counted (a real {@code font.draw} allocates a {@code GlyphLayout}, out of scope here). */
    @Test void bossHud() {
        BitmapFont font = new BitmapFont() {
            @Override public com.badlogic.gdx.graphics.g2d.GlyphLayout draw(com.badlogic.gdx.graphics.g2d.Batch b, CharSequence s, float x, float y) { return null; }
        };
        harness.font = font;
        BossHud hud = new BossHud(harness.assets(), harness.game(), new Matrix4(), 640, 360);
        Level level = harness.roomOf("forest-2", 3);
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

    /** An untouched stump and root (#346): the idle tell loop's {@code update} and frame choice, and {@code render}, each frame. */
    @Test void explosiveIdleTell() {
        Rectangle far = new Rectangle(FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        Runnable none = () -> { };
        ExplosiveTrap stump = new ExplosiveTrap(harness.assets(), harness.game().trapWarnings(), ExplosiveTrap.Kind.STUMP,
            0, FUNGUS_FLOOR - TRAP_TILE, TRAP_TILE, TRAP_TILE, () -> far, none, none);
        ExplosiveTrap root = new ExplosiveTrap(harness.assets(), harness.game().trapWarnings(), ExplosiveTrap.Kind.ROOT,
            FUNGUS_SPACING, FUNGUS_FLOOR - ROOT_HEIGHT, TRAP_TILE, ROOT_HEIGHT, () -> far, none, none);
        double bytes = AllocationProbe.bytesPerFrame(i -> { stump.update(STEP); root.update(STEP); stump.render(batch); root.render(batch); });
        assertEquals(ExplosiveTrap.Phase.IDLE, stump.phase(), "the hero is far: the tell only");
        assertWithinBudget("explosive idle tell", EXPLOSIVE_IDLE_TELL_CEILING, bytes);
    }

    /**
     * Thorn roots (#359) in turn, as {@link #honeyFungusBurstsInTurn}: every {@code FUNGUS_CYCLE_FRAMES} the hero steps beside
     * the next root's column (inside its zone, clear of the 6 px column), so the measured window holds roots at rest (the cue
     * frame), warning with the wobble, rising and up; {@code update} (zone and column tests) and {@code render} of all of them each frame.
     */
    @Test void thornRootsRiseInTurn() {
        ThornTrap[] roots = new ThornTrap[FUNGUS_CYCLES];
        Rectangle hero = new Rectangle(0, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        Runnable none = () -> { };
        for (int k = 0; k < roots.length; k++)
            roots[k] = new ThornTrap(harness.assets(), harness.game().trapWarnings(), ThornTrap.Kind.ROOT, k * FUNGUS_SPACING,
                FUNGUS_FLOOR - ROOT_HEIGHT, TRAP_TILE, ROOT_HEIGHT, FUNGUS_ZONE, FUNGUS_ZONE, () -> hero, none);
        boolean[] seen = new boolean[ThornTrap.Phase.values().length];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = Math.min(i / FUNGUS_CYCLE_FRAMES, roots.length - 1);
            hero.x = i % FUNGUS_CYCLE_FRAMES < FUNGUS_ENTER_FRAME ? k * FUNGUS_SPACING - FUNGUS_SPACING / 2f : k * FUNGUS_SPACING + THORN_BESIDE;
            for (ThornTrap root : roots) { root.update(STEP); root.render(batch); }
            if (i >= AllocationProbe.WARMUP_FRAMES) seen[roots[k].phase().ordinal()] = true;
        });
        for (ThornTrap.Phase phase : ThornTrap.Phase.values())
            assertTrue(seen[phase.ordinal()], "the measured window must hold a root in " + phase);
        assertEquals(ThornTrap.Phase.ARMED, roots[roots.length - 1].phase(), "the last root must rise in the window");
        assertWithinBudget("thorn roots rise in turn", THORN_ROOT_CEILING, bytes);
    }

    /** The falling branch (#361): one at rest with its cracked-joint cue (the hero far), and a row of branches that the hero
     * walks under in turn, one every {@value #BRANCH_CYCLE_FRAMES} frames: rustle, sag, fall and land, {@code update} and
     * {@code render} each frame. The branches are built before the probe; the hero box is moved, never rebuilt. */
    @Test void fallingBranchAtRestAndThroughItsWarning() {
        Rectangle far = new Rectangle(FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        Rectangle hero = new Rectangle(-FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        Runnable none = () -> { };
        FallingBranch resting = branch(-FUNGUS_SPACING, far, none);
        FallingBranch[] branches = new FallingBranch[BRANCH_CYCLES];
        for (int k = 0; k < branches.length; k++) branches[k] = branch(k * FUNGUS_SPACING, hero, none);
        boolean[] warned = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = Math.min(i / BRANCH_CYCLE_FRAMES, branches.length - 1);
            hero.x = k * FUNGUS_SPACING + BRANCH_WIDTH / 2;
            resting.update(STEP); resting.render(batch);
            for (int b = 0; b < branches.length; b++) {
                branches[b].update(STEP); branches[b].render(batch);
                warned[0] |= branches[b].frame() == FallingBranch.SAG;
            }
        });
        assertEquals(FallingBranch.State.ARMED, resting.state(), "the far branch must stay at rest");
        assertTrue(warned[0] && branches[branches.length - 1].state() != FallingBranch.State.ARMED, "the warning must actually run");
        assertWithinBudget("falling branch", FALLING_BRANCH_CEILING, bytes);
    }

    private FallingBranch branch(float x, Rectangle hero, Runnable kill) {
        return new FallingBranch(harness.assets(), new Rectangle(x, BRANCH_Y, BRANCH_WIDTH, BRANCH_HEIGHT),
            new Rectangle(x, BRANCH_Y, BRANCH_WIDTH, FUNGUS_FLOOR - BRANCH_Y), FUNGUS_FLOOR, harness.game().trapWarnings(),
            () -> hero, () -> true, kill);
    }

    /** Ten plain falling platforms at rest (#362): the fan animation with the cracked slab (the D71 cue), {@code update} and {@code render}, each frame. */
    @Test void fallingPlatformCrackAtRest() {
        FallingPlatform[] platforms = new FallingPlatform[FUNGUS_CYCLES];
        for (int i = 0; i < platforms.length; i++)
            platforms[i] = new FallingPlatform(harness.assets(), i * FUNGUS_SPACING, FUNGUS_FLOOR, 2 * TRAP_TILE, TRAP_TILE, o -> { },
                harness.game().trapWarnings());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            for (int n = 0; n < platforms.length; n++) { platforms[n].update(STEP); platforms[n].render(batch); }
        });
        assertFalse(platforms[0].isFalling(), "nobody landed: the cue only");
        assertWithinBudget("falling platform crack at rest", FALLING_PLATFORM_REST_CEILING, bytes);
    }

    /**
     * An armed falling stone (#360): the loose-overhang lip drawn every frame and the grit trickle (four specks every 1.5 s on the
     * stone's own {@code KimeriaParticleSystem}), spawn, update and render through the batch; measured 0.0 to 0.1 B/frame in 4 runs, over the probe's 300 warm-up and 1,200 measured frames (16 trickles).
     */
    @Test void fallingStoneLooseOverhang() {
        Rectangle far = new Rectangle(FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        FallingStone stone = new FallingStone(harness.assets(), FallingStone.skin(FallingStone.DEFAULT_SKIN),
            new Rectangle(0, 0, TRAP_TILE, TRAP_TILE), new Rectangle(0, FUNGUS_FLOOR - FUNGUS_ZONE, FUNGUS_ZONE, FUNGUS_ZONE), FUNGUS_FLOOR, 0,
            harness.game().trapWarnings(), () -> far, () -> true, () -> { });
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> { stone.update(STEP); stone.render(batch); peak[0] = Math.max(peak[0], stone.grit().count()); });
        assertEquals(FallingTrap.State.ARMED, stone.state(), "the hero is far: the cue only");
        assertEquals(FallingStone.TRICKLE_SPECKS, peak[0], "the trickle must actually run in this scenario");
        assertWithinBudget("falling stone loose overhang", FALLING_STONE_CUE_CEILING, bytes);
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
    /** Measured 0.0 B/frame in 3 runs, instant text off and on (#336): the held turn button, reveal, turn fade, cue and flush of a 50-page-plus story, instant text off and on. */
    static final double PAGED_INTRO_CEILING = 8;
    /** The dialogue screen (D78, #337): measured in the tale (instant text off and on) and the pre-boss cases; see healerDialogueScreen. */
    static final double DIALOGUE_SCREEN_CEILING = 8;
    static final int DIALOGUE_SELECT_FRAMES = 60;
    static final int PAGED_INTRO_COPIES = 100, TOUCH_POINTERS = 5; // TouchControls.MAX_POINTERS
    /** Thorn roots at rest, warning, rising and up inside the measured window (#359, thornRootsRiseInTurn): measured 0.0 B/frame in 2 full-suite runs and 1.5 in 2 isolated runs (2026-11-17). Before #359: 64.0 B/frame (a new {@code Rectangle} per zone or column test). */
    static final double THORN_ROOT_CEILING = 8;
    /** The hero's left edge this far right of a thorn root's left edge: inside its zone, clear of the 6 px column (5 to 11). */
    private static final float THORN_BESIDE = 12;
    /** Measured 0.0 to 0.5 B/frame in 3 runs (#362, 2026-11-17): ten plain falling platforms at rest, the fan animation with the cracked slab, update and render; the same 8 B ceiling as the other isolated paths. */
    static final double FALLING_PLATFORM_REST_CEILING = 8;
    static final double EXPLOSIVE_IDLE_TELL_CEILING = 8, FALLING_STONE_CUE_CEILING = 8, STONE_FIREBALL_CEILING = 8, FAIRY_GUIDE_CEILING = 8, HUD_CEILING = 8, HUD_HINT_CEILING = 8,
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

    /**
     * The hero beside the stone and a lever: the fairy guide (D82, was the passive corner outlines of
     * {@code Player.renderInteractionTarget}) updated and drawn each frame: passive glows, her light and her frame.
     */
    @Test void fairyGuideWithPassiveGlows() {
        Level level = harness.roomOf("forest-0", STONE_ROOM);
        Carryable stone = level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
        for (float dy = -REACH_SCAN; dy <= REACH_SCAN && level.player.selectedCarryable() == null; dy += REACH_SCAN_STEP)
            level.player.position.set(stone.getX() - HERO_WIDTH, stone.getY() + dy);
        assertNotNull(level.player.selectedCarryable(), "the stone must be in reach in this scenario");
        var fairy = level.fairy();
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            fairy.update(1f / 60f, level.player); fairy.renderBehind(batch); fairy.renderFront(batch);
        });
        assertTrue(fairy.passiveTargets().size > 0, "the stone must be marked passive in this scenario");
        assertWithinBudget("fairy guide with passive glows", FAIRY_GUIDE_CEILING, bytes);
    }

    private static final float ROUTE_LEVER_X = 1440, ROUTE_FLOOR_Y = 272;
    private static final int PULL_CUE_FRAMES = 60, SELECT_FRAMES = 30;

    /**
     * D87 (#419): the hero at the forest-1 route room's lever with Up held, the lift outside the 640x360 view, so the fairy flies to the
     * screen edge and twinkles there; the pulled lever's marks are renewed every {@link #PULL_CUE_FRAMES} frames, so the clear glow, its
     * fade and the glints on the in-view ladder run inside the window. Update and both render passes of the fairy only.
     * Measured 0.6 B/frame in 3 isolated runs after the merge of main (0.6 to 2.0 in 3 runs before it), under the same 8 B ceiling
     * as the other isolated fairy scenes ({@link #FAIRY_GUIDE_CEILING}). The scene found 24.6 B/frame before the fix: {@code Player.selectedInteraction}
     * made a record each frame any target was selected.
     */
    @Test void fairyGuidePointingOffScreenWithAPostPullGlow() {
        Rectangle view = new Rectangle();
        Level level = harness.roomOf("forest-1", 3, () -> view);
        var t = level.player.roomCameraTarget();
        level.player.position.set(ROUTE_LEVER_X - HERO_CENTRE_OFFSET_X, ROUTE_FLOOR_Y);
        harness.held.add(com.badlogic.gdx.Input.Keys.W);
        for (int i = 0; i < SELECT_FRAMES; i++) { t = level.player.roomCameraTarget(); view.set(t.x, t.y, 640, 360); level.update(STEP); }
        var fairy = level.fairy();
        var trigger = level.triggers.get(0);
        int[] markedFrames = new int[1], twinkleFrames = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % PULL_CUE_FRAMES == 0) level.linkedMechanismsInto(trigger, fairy.beginChanged());
            fairy.update(1f / 60f, level.player); fairy.renderBehind(batch); fairy.renderFront(batch);
            if (fairy.changedSecondsLeft() > 0 && fairy.changedTargets().size > 0) markedFrames[0]++;
            if (fairy.mode() == com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.TWINKLE) twinkleFrames[0]++;
        });
        harness.held.clear();
        assertNotNull(fairy.offscreenTarget(), "the lift must be off screen in this scenario");
        assertEquals(com.jlogicsoftware.kimeria.ui.FairyGuide.Mode.TWINKLE, fairy.mode(), "she must be twinkling at the edge");
        assertTrue(markedFrames[0] > PULL_CUE_FRAMES, "the post-pull marks must be active inside the measured window: " + markedFrames[0]);
        assertTrue(twinkleFrames[0] > PULL_CUE_FRAMES, "she must twinkle at the edge inside the measured window: " + twinkleFrames[0]);
        assertWithinBudget("fairy guide pointing off screen with a post-pull glow", FAIRY_GUIDE_CEILING, bytes);
    }

    /** A selected lever marks the mechanisms it drives every frame (D82): filled into a reused array, no list per call. */
    @Test void linkedMechanismsOfASelectedLever() {
        Level level = harness.roomOf("forest-0", 0);
        var trigger = level.triggers.get(0);
        var linked = new com.badlogic.gdx.utils.Array<com.jlogicsoftware.kimeria.entity.Entity>(false, 8);
        double bytes = AllocationProbe.bytesPerFrame(i -> { linked.clear(); level.linkedMechanismsInto(trigger, linked); });
        assertTrue(linked.size > 0, "forest-0's first lever drives its torch");
        assertWithinBudget("linked mechanisms of a selected lever", FAIRY_GUIDE_CEILING, bytes);
    }

    private Hud hudWithMetricsFont() {
        harness.font = new MetricsFont(FontSizes.LABEL);
        return new Hud(harness.assets(), harness.game(), 640, 360);
    }

    /** The coin count, the FPS counter and the fireball stock of {@code Hud.render} with the boss bar, each frame. */
    @Test void hudCoinCountAndFps() {
        Hud hud = hudWithMetricsFont();
        Level level = harness.roomOf("forest-2", 3);
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

    /**
     * One healer dialogue line revealing frame after frame (update, the visible-letter count the renderer asks for, the
     * "fully revealed" check), with instant text off and on. {@code text()} builds a substring and is for tests and
     * events, not for the frame; the view uses {@code visibleCharacters}.
     */
    @Test void healerDialogueReveal() {
        for (boolean instant : new boolean[]{false, true}) {
            HealerDialogue dialogue = HealerDialogue.tutorialStart();
            dialogue.setInstantText(instant);
            int[] sum = new int[1];
            double bytes = AllocationProbe.bytesPerFrame(i -> {
                dialogue.update(STEP);
                sum[0] += dialogue.visibleCharacters(Language.ENGLISH) + (dialogue.fullyRevealed(Language.ENGLISH) ? 1 : 0);
            });
            assertTrue(sum[0] > 0);
            assertWithinBudget("HealerDialogue reveal, instant text " + (instant ? "on" : "off"), CHECKLIST_CEILING, bytes);
        }
    }

    /**
     * The dialogue screen (D78, #337) frame after frame through a no-op batch with the real 18 px font metrics:
     * {@code HealerDialogue.update}, {@code HealerDialogueView.render} and {@code Overlay.flushText}. The tale case runs
     * the longest line (the fire gift) from its first letter to whole, with the glint, the speaker's bob and the
     * continue prompt, instant text off and on; the pre-boss case shows the checklist and both choices with the selection
     * moving every {@value #DIALOGUE_SELECT_FRAMES} frames. A new line rebuilds its text block, an event measured apart.
     */
    @Test void healerDialogueScreen() {
        harness.font = new MetricsFont(FontSizes.DIALOGUE);
        Level level = harness.roomOf("forest-1", 1);
        ArtifactInventory inventory = new ArtifactInventory();
        for (boolean instant : new boolean[]{false, true}) {
            Overlay overlay = new Overlay(harness.assets(), 640, 360);
            HealerDialogueView view = new HealerDialogueView(harness.assets());
            HealerDialogue tale = HealerDialogue.tutorialStart();
            tale.setInstantText(true);
            while (tale.currentLine() != Msg.HEALER_TALE_FIREGIFT) tale.advance(Language.ENGLISH);
            tale.setInstantText(instant);
            double bytes = AllocationProbe.bytesPerFrame(i -> {
                tale.update(STEP);
                overlay.beginFrame();
                view.render(batch, overlay, tale, Language.ENGLISH, level, inventory);
                overlay.flushText(batch);
            });
            assertTrue(tale.fullyRevealed(Language.ENGLISH) && tale.openProgress() >= 1f, "the line must be whole by the end");
            System.out.printf("ALLOCATION dialogue screen, tale line, instant text %s: %.1f B/frame%n", instant ? "on" : "off", bytes);
            assertWithinBudget("dialogue screen, tale line, instant text " + (instant ? "on" : "off"), DIALOGUE_SCREEN_CEILING, bytes);
        }
        Overlay overlay = new Overlay(harness.assets(), 640, 360);
        HealerDialogueView view = new HealerDialogueView(harness.assets());
        HealerDialogue stop = new HealerDialogue(false, true, false, false, false);
        stop.setInstantText(true);
        for (int i = 0; i < 10 && !stop.choosing(); i++) stop.advance(Language.ENGLISH);
        assertTrue(stop.choosing() && stop.choices().size() == 2, "the stop must offer its two choices");
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % DIALOGUE_SELECT_FRAMES == 0) stop.move(1);
            stop.update(STEP);
            overlay.beginFrame();
            view.render(batch, overlay, stop, Language.ENGLISH, level, inventory);
            overlay.flushText(batch);
        });
        System.out.printf("ALLOCATION dialogue screen, pre-boss checklist and choices: %.1f B/frame%n", bytes);
        assertWithinBudget("dialogue screen, pre-boss checklist and choices", DIALOGUE_SCREEN_CEILING, bytes);
        // The event: one line change (a new text block). Printed for the record, not a frame cost.
        HealerDialogue[] lines = {HealerDialogue.tutorialStart()};
        lines[0].setInstantText(true);
        double perChange = AllocationProbe.bytesPerFrame(i -> {
            if (lines[0].advance(Language.ENGLISH) == HealerDialogue.Choice.CONTINUE) {
                lines[0] = HealerDialogue.tutorialStart();
                lines[0].setInstantText(true);
            }
            overlay.beginFrame();
            view.render(batch, overlay, lines[0], Language.ENGLISH, level, inventory);
            overlay.flushText(batch);
        });
        System.out.printf("ALLOCATION dialogue screen, a new line every frame (the event, not gated): %.1f B/frame%n", perChange);
    }

    /**
     * The paged intro (#336) frame after frame: {@code IntroScreen.update}, the held page-turn button (every frame, so the
     * lock, the reveal, the turn fade and the cue all run), {@code render} and {@code Overlay.flushText} through a no-op
     * batch with the real body font's metrics. The story is the English opening repeated 100 times (50 or more pages), which
     * keeps pages turning through the whole measured window. Layout, measuring and the text blocks are built at load,
     * outside the measurement.
     */
    @Test void pagedIntro() {
        harness.font = new MetricsFont(FontSizes.BODY);
        String opening = com.jlogicsoftware.kimeria.localization.Catalog.parse("en",
            new com.badlogic.gdx.backends.headless.HeadlessFiles().internal("localization/en.json").readString("UTF-8")).get("INTRO_STORY");
        String story = String.join("\n\n", java.util.Collections.nCopies(PAGED_INTRO_COPIES, opening));
        // The harness's proxied Gdx.input allocates an argument array on every call (24 B), and the skip button polls
        // TOUCH_POINTERS pointers a frame; that is the harness, not the button, so it is measured alone and subtracted.
        double harnessInput = AllocationProbe.bytesPerFrame(i -> {
            for (int pointer = 0; pointer < TOUCH_POINTERS; pointer++) com.badlogic.gdx.Gdx.input.isTouched(pointer);
        });
        System.out.printf("ALLOCATION harness input polling alone: %.1f B/frame%n", harnessInput);
        for (boolean instant : new boolean[]{false, true}) {
            Overlay overlay = new Overlay(harness.assets(), 640, 360);
            IntroScreen intro = IntroScreen.open(story, overlay, harness.assets(), 640, instant);
            assertTrue(intro.pages().pageCount() >= 50, "the scenario needs many pages");
            // The touch skip button runs every story frame on a touch device: its pointer poll and its draw.
            TouchControls touch = new TouchControls(harness.assets(), harness.game(), new Viewport() { }, 640, 360, true);
            int[] turned = new int[1];
            double bytes = AllocationProbe.bytesPerFrame(i -> {
                touch.updateSkip();
                touch.renderSkipButton(batch);
                intro.update(STEP);
                if (intro.turn()) turned[0]++;
                overlay.beginFrame();
                intro.render(batch, overlay);
                overlay.flushText(batch);
            });
            assertTrue(turned[0] > 10, "pages must keep turning during the measurement: " + turned[0]);
            assertTrue(intro.state() != IntroScreen.State.DONE, "the story must still be running at the end");
            System.out.printf("ALLOCATION paged intro with the skip button, instant text %s: %.1f B/frame in all, %.1f of it the harness's input proxy%n",
                instant ? "on" : "off", bytes, harnessInput);
            assertWithinBudget("paged intro, instant text " + (instant ? "on" : "off") + ", without the harness input proxy", PAGED_INTRO_CEILING, Math.max(0, bytes - harnessInput));
        }
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
