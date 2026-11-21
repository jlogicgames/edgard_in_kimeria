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
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.objects.DeadfallTrap;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
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
import com.jlogicsoftware.kimeria.ui.KimeriaArms;
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
     * now room 2, after #375 moved its first RedMob and its refill to forest-1-mob and removed its lever and torch, then with the
     * level-wide rain and sky that #430 later removed. Ceiling = highest run + about 10 %. #430 (D92, 2026-11-17) removed the rain and the sky: 328.0 to 544.3 in 3 isolated runs of the class
     * after, 328.1 to 544.3 before on the same machine, so they cost nothing measurable and the ceiling stays; every forest-1 room scene below
     * measured the same before and after (D92 has the table). History: 1,322.0 to 1,322.2 B/frame with that torch (#303, ceiling 1450); before #303 2,441.8 to 2,441.9;
     * step 1: 2,530.2 to 2,564.4; step 0: 4,491.8 to 4,525.5.
     */
    static final double FOREST_1_CEILING = 665;
    /**
     * The escalator room, split off the trap course by #500 (D110, 2026-11-20): measured 320.0 to 536.0 B/frame in 3 isolated runs of the class (JIT-dependent, the
     * same band as every other forest-1 room scene). Ceiling 620 = the highest run (536.0) + about 16 %, the same as the other forest-1 room ceilings (mob, lever, deadfall, bramble: 620 over the same 536.0 band). The trap course (FOREST_1_CEILING
     * above, now only the puffball, the second RedMob and the log) measured 544.3, 328.3, 328.3 in the same runs, the same as before the split, so its ceiling stays;
     * the guard room measured 544.0, 232.0, 232.0 (guard awake 544.0, 328.0, 328.0) after its start moved 32 px, under its unchanged ceiling.
     */
    static final double FOREST_1_ESCALATORS_CEILING = 620;
    /** Measured 1,025.6 to 1,026.3 B/frame in isolated and full-suite runs, bat alive (#187, D67, 2026-11-15), the same with the hero riding the lift (forest1RouteRoomRidingTheLift): the forest-1 route room (a running lift, a lever, a raised ladder, a refill), update + render. Ceiling = highest run + about 10 %. */
    static final double FOREST_1_ROUTE_CEILING = 1130;
    /**
     * Measured 345.8 to 561.8 B/frame in 3 isolated runs (#375, D85, 2026-11-17: 561.8, 561.8, 345.8; 561.8 in the full suite): forest-1's first
     * room at rest (the RedMob asleep, the refill), update + render. Ceiling = highest run + about 10 %.
     */
    static final double FOREST_1_MOB_CEILING = 620;
    /** Measured 1,162.1 to 1,162.2 B/frame (guard asleep, 3 isolated runs and the full class) and 849.7 to 1,017.8 B/frame (guard awake: JIT-dependent, always below the asleep scene) (#104, D68, 2026-11-15; the guard's attack adds nothing measurable): the forest-1 guard room (a lever, a raised ladder, the tunnel with its guard and spore puffball, the roof, the refill), update + render. Ceiling = highest run + about 10 %. Since #327 the room holds a beetle past the tunnel: 544.5 B/frame in 3 isolated runs (2026-11-18), the beetle crawling (the hero at the entrance is out of its hearing); its other states are {@link #beetleThroughTheRealLevel}. */
    static final double FOREST_1_GUARD_CEILING = 1280;
    /**
     * Measured 513.8 to 564.5 B/frame in 10 isolated runs of the class (#418, D86, 2026-11-17): at rest 513.8 to 561.9, riding the lift 561.6 to 562.3, pulling the lever 564.3 to 564.5
     * (one earlier run of the rest scene: 345.8; JIT state, D83; the mob, route and tree scenes moved together with it): the forest-1 lever lesson room (a running lift, a lever, a raised ladder; no enemy or hazard),
     * update + render. Ceiling = the highest run (564.5, pulling the lever) + about 10 %, the rule D85 (#375) set for the other forest-1 rooms; the riding scene
     * measures the same as the route room's here (561.8), as it should: the cost is the harness and level code (D83).
     */
    static final double FOREST_1_LEVER_CEILING = 620;
    /** The forest-1 deadfall room at rest (#363): measured 357.7 to 536.7 B/frame in 4 isolated runs and 536.0 in the full :core:check (2026-11-17), the mob room 224.0 to 536.0 in the same runs: the harness and level code dominate, as in the other forest-1 rooms (D83; #430 showed the sky and rain cost nothing measurable), so it takes the mob and lever rooms' ceiling (highest run + about 15 %). */
    static final double FOREST_1_DEADFALL_CEILING = 620;
    /** The forest-1 bramble room (#364, D89): measured 536.1 to 536.3 B/frame in 3 isolated runs (2026-11-17), the deadfall room 536.0 in the same runs, so the bramble adds nothing measurable; it takes the other forest-1 one-screen rooms' ceiling. */
    static final double FOREST_1_BRAMBLE_CEILING = 620;
    /** The forest-1 hive room (#365, D90): measured 536.7 B/frame in 3 isolated runs (2026-11-17), the same as the deadfall and bramble rooms: the two hives add nothing measurable over the rest of the room. Ceiling = the highest run + about 10 %. */
    static final double FOREST_1_HIVE_CEILING = 590;
    /** The forest-2 fissure room (#366, D91): measured 536.8 B/frame in 3 isolated runs and 328.0 to 472.0 in 3 full-suite runs (2026-11-17), the hive room 536.6 to 536.7 in the same isolated runs: the two fissures add nothing measurable over the rest of the room, so it takes the hive room's ceiling (highest run + about 10 %). */
    static final double FOREST_2_FISSURE_CEILING = 590;
    /**
     * Measured 537.8 to 897.8 B/frame in 5 full-suite runs of {@code :core:test} and {@code :core:check} (#349, D80, 2026-11-16, with the CollideBody
     * index loops: 801.8, 801.8, 537.8, 897.8, 897.8; before them 713.9 isolated and 881.7 in the full suite): the forest-1 tree room at rest (two
     * trunks, five rotten and six sound branches), update + render. The value moves with JIT state
     * together with the other room scenes (the 897.8 runs had the route room at 897.9 and the guard room at 906.2). The cost is the harness and
     * level code, as the other room scenarios; the ceiling is the highest full-suite run plus about 10 %, the whole-room convention of
     * FOREST_1_ROUTE_CEILING (1130) and FOREST_1_GUARD_CEILING (1280). A first ceiling of 880, set from the first 3 runs, failed the next 2.
     * Between #327 and #500 the room held a beetle at the sound tree's foot (224.0 B/frame in 3 isolated runs, 2026-11-18); it left the room
     * under #500 (D105), so the scene is the trees alone again and the ceiling is kept as it was, not re-derived; the beetle's states are
     * {@link #beetleThroughTheRealLevel}.
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
    /** Measured 568.0 to 568.5 B/frame in isolated runs and 481.8 in the full class run (2026-11-18, #472): the whole room, its objects and its 24 fireflies together. Ceiling = highest isolated run + about 10 %. */
    static final double FOREST_2_FIREFLIES_CEILING = 625;

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
    /** Measured 2.8, 2.8, 2.8, 2.8, 2.8 and 0.0 B/frame in 6 runs (2026-11-18, #397 rework; the same 8 B ceiling as the other isolated paths); the 2.8 is a one-time ~3.4 KB in the first measurement of a fresh JVM, not a per-frame cost: a probe (2026-11-18) read 2.8 or 0.3 on its first run and 0.0 on every repeat in the same JVM, and 0.0 to 0.1 with either the ball's or the effect's render left out, so it is a one-time cost of the first run of that combined path whose source was not identified (JIT warm-up or a one-time lazy initialisation); no ball stops within the measured frames, so {@code playSounds()} is never reached here: a fireball flying through the real level, its halo drawn, its trail spawned, updated and drawn every frame, a new ball every 60 frames. */
    static final double FIREBALL_TRAIL_CEILING = 8;
    /** The cast's extra over the same room with no cast, B/frame (#503): one {@code new Fireball} per 60-frame cycle. Measured 5.6 B/frame (about 340 B per cast; median of 9 paired windows, full suite {@code --rerun-tasks}, 2026-11-19, 99 casts); 16 is about three times that, headroom for JIT variation. */
    static final double FIREBALL_CAST_EXTRA_CEILING = 16;
    private static final int FIREBALL_CAST_PAIRS = 9, FIREBALL_CAST_WINDOW_FRAMES = 600;
    /** Measured 0.0 B/frame in 6 runs (2026-11-18, #397), and 0.0 in 3 more runs with the impact ring, 0.2 and 0.3 in two full-suite runs; headless there is no GL context, so the ring's shader draw is skipped ({@code Shaders.tryLoad} returns null) and the number is the particles and the ring slots: a fireball burst every 60 frames through the real {@code Level.fireballFx()} (light, heavy, burn-out in turn), updated and drawn each frame. */
    static final double FIREBALL_IMPACT_CEILING = 8;
    private static final int FIREBALL_ROOM = 3, FIREBALL_SHOTS = 30, FIREBALL_BURST_KINDS = 3;
    /** Measured 0.0 B/frame in 3 runs (2026-11-18, #402); headless there is no GL context, so the ring's shader draw is skipped ({@code Shaders.tryLoad} returns null) and the number is the sparks and the ring slots: a pickup burst (12 sparks and a ring) every 60 frames through the real {@code Level.pickupSparks()}, spawn, update and render. */
    static final double PICKUP_SPARKS_CEILING = 8;
    /** Measured 0.1, 0.1 and 0.1 B/frame in 3 runs (2026-11-18, #327 and D101; 0.2 before the body; the same 8 B ceiling as the other isolated paths; the harness does not play sounds, so {@code playSound} is not reached): beetles crawling, noticing, committing, winding up with specks, bursting or dying under a kill, their bodies rocking, crumbling and burning, through the real {@code Level} of beetle-test.tmx, update and render every frame. */
    static final double BEETLE_CEILING = 8;
    /** Measured 0.0, 0.0 and 0.0 B/frame in 3 runs (2026-11-19, #328, D102; the same 8 B ceiling as the beetle's; the harness does not play sounds): spiders skittering, noticing, chasing, winding up with their silk, leaping, biting and recovering, or killed and leaving a body that rocks, crumbles and burns, through the real {@code Level} of spider-test.tmx, update and render every frame. */
    static final double SPIDER_CEILING = 8;
    /** Measured in 3 runs (2026-11-19, #483, D103; the same 8 B ceiling as the floor spider's): see {@code spiderDropThroughTheRealLevel} and {@code spiderClimbThroughTheRealLevel}; the numbers are in D103. */
    static final double SPIDER_ROUTE_CEILING = 8;
    /** Measured in 3 runs (2026-11-19, #329, D106; the same 8 B ceiling as the other monsters; the harness does not play sounds): snails crawling, noticing, winding up with their specks and swelling gob, spitting, their gobs flying with drips and ending (dropping and splatting past their range, cut by the blade or burnt), reloading, or killed with their gob still in the air and leaving a body that rocks, crumbles and burns, through the real {@code Level} of snail-test.tmx, update and render every frame; the numbers are in D106. */
    static final double SNAIL_CEILING = 8;
    /** Measured 0.2, 0.6 and 0.6 B/frame in 3 runs before the floor re-check, and 0.0 in the one run after it (2026-11-21, #331, D115; the same 8 B ceiling as the other monsters; the harness does not play sounds): wolves trotting, sniffing, noticing, sprinting, skidding on a turn, winding up with their breath streaks, lunging, biting, recovering and baying under a hero out of reach, or killed in the lunge and leaving a body that twitches, crumbles and burns, through the real {@code Level} of wolf-test.tmx, update and render every frame; the numbers are in D115. */
    static final double WOLF_CEILING = 8;
    /** The leaves' own cost, {@code leafFx().update} and {@code render} of forest-1's deadfall room (its `leaves` property and trap columns are the game's own): measured 0.0 B/frame in 3 runs (2026-11-18, #473); the whole level in that room, leaves running, is {@link #FOREST_1_DEADFALL_CEILING}'s scenario: 320.0 and 536.0 B/frame in two runs (the harness and level code dominate, as the existing deadfall room row says), so only the isolated number can show the leaves are free. */
    static final double FALLING_LEAVES_CEILING = 8;
    private static final int LEAF_ROOM = 1;

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
    /** #364 (D89): two brambles (a left and a right root) through whole cycles, the lethal test against the hero and the draw every frame. Measured 0.0 to 0.7 B/frame in 3 isolated runs (2026-11-17; the 0.7 is the probe noise the other trap scenes show). */
    static final double BRAMBLE_CEILING = 8;
    private static final float BRAMBLE_REACH = 64, BRAMBLE_HEIGHT = 24;
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

    /** The forest-2 room with its 24 gameplay fireflies (#472): their update and the additive blend switch around their render, through the real {@code Level.update} and {@code Level.render}. */
    @Test void forest2RoomWithFireflies() {
        assertWithinBudget("forest-2 room 0 with fireflies", FOREST_2_FIREFLIES_CEILING, room("forest-2", 0));
    }

    /** The boss room with an empowered spray every {@link #BURST_CYCLE_FRAMES} frames through {@code Level.blood}: the level path, not just the system. */
    @Test void forest2Room3WithBlood() {
        Level level = harness.roomOf("forest-2", 4);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.blood(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR, true);
            level.update(STEP); level.render(batch, view);
        });
        assertWithinBudget("forest-2 room 2 with blood", FOREST_2_ROOM_2_BLOOD_CEILING, bytes);
    }

    /** A sword kill sprays a light burst through {@code Level.mobBlood} every {@link #BURST_CYCLE_FRAMES} frames in the boss room (the level path of #319). */
    @Test void forest2Room3WithMobKillBlood() {
        Level level = harness.roomOf("forest-2", 4);
        Rectangle view = new Rectangle(level.currentRoomBounds());
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.mobBlood(BLOOD_X, BLOOD_Y, 1, BLOOD_FLOOR);
            level.update(STEP); level.render(batch, view);
        });
        assertWithinBudget("forest-2 room 2 with mob kill blood", FOREST_2_ROOM_2_BLOOD_CEILING, bytes);
    }

    /**
     * The parallax layers and the room's foreground of forest-1's mob room (#236), update, {@code renderBack} and {@code renderFront}
     * through the {@link NoopBatch} with a real 640x360 view: the camera sweeps the room's 160 px and back every
     * {@value #PARALLAX_SWEEP_FRAMES} frames while its top swings between the room top and the floor camera top (8 px apart), so every layer, the
     * cloud drift, the culling and the per-room foreground origin all run. Measured 0.0 B/frame in 3 runs (2026-11-19): index loops,
     * cached regions, no object per frame. Every forest-1 room scene below also draws these layers (its view is the room's rect).
     */
    static final double PARALLAX_CEILING = 8;
    private static final int PARALLAX_SWEEP_FRAMES = 320;
    private static final float PARALLAX_CAMERA_SPAN = 160, PARALLAX_TOP_HIGH = 0, PARALLAX_TOP_FLOOR = 8;
    @Test void forest1ParallaxLayersAndForeground() {
        Rectangle view = new Rectangle(0, 0, 640, 360);
        Level level = harness.room("forest-1", "forest-1-mob", () -> view);
        Rectangle room = new Rectangle(level.currentRoomBounds());
        com.jlogicsoftware.kimeria.world.ParallaxRenderer parallax = new com.jlogicsoftware.kimeria.world.ParallaxRenderer(level.map);
        float originY = com.jlogicsoftware.kimeria.world.ParallaxRenderer.originY(room, view.height);
        batch.record(1 << 16); // the log is allocated once here, then only written: the probe sees no allocation
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            float t = (i % PARALLAX_SWEEP_FRAMES) / (float) PARALLAX_SWEEP_FRAMES;
            float sweep = t < 0.5f ? t * 2 : 2 - t * 2;
            view.x = room.x + PARALLAX_CAMERA_SPAN * sweep;
            view.y = PARALLAX_TOP_HIGH + (PARALLAX_TOP_FLOOR - PARALLAX_TOP_HIGH) * sweep;
            parallax.update(STEP);
            parallax.renderBack(batch, view, originY);
            parallax.renderFront(batch, view, room, originY);
        });
        assertTrue(batch.drawnCount() > 0, "the scenario must actually draw layers");
        assertWithinBudget("forest-1 parallax layers and foreground", PARALLAX_CEILING, bytes);
    }

    /** The first-RedMob room (#375): the mob asleep, the refill. */
    @Test void forest1MobRoom() { assertWithinBudget("forest-1 mob room", FOREST_1_MOB_CEILING, room("forest-1", 0)); }
    /** The trap course (the puffball, the second RedMob, the rotten log over the pit), room 4 since #500 (D110) split the escalators off it. */
    @Test void forest1() { assertWithinBudget("forest-1", FOREST_1_CEILING, room("forest-1", 3)); }
    /** The escalator room (the falling platforms, two running escalators, the lever and its wall), room 3 since #500 (D110). */
    @Test void forest1EscalatorsRoom() { assertWithinBudget("forest-1 escalators room", FOREST_1_ESCALATORS_CEILING, room("forest-1", 2)); }
    @Test void forest1RouteRoom() { assertWithinBudget("forest-1 route room", FOREST_1_ROUTE_CEILING, room("forest-1", 8)); }
    /** The guard room (#104, D68): the RedMob asleep in its tunnel, the puffball cycling (swell and cloud included), a lever, a raised ladder, the roof and its refill. */
    @Test void forest1GuardRoom() { assertWithinBudget("forest-1 guard room", FOREST_1_GUARD_CEILING, room("forest-1", 9)); }

    /** The tree room (#349, D80): two trunks, five rotten branches at rest, six sound branches. */
    @Test void forest1TreeRoom() { assertWithinBudget("forest-1 tree room", FOREST_1_TREE_CEILING, room("forest-1", 11)); } // the tree room is room 12 (index 11) since #500 (D110), index 10 since the spider rooms (#484)
    /** The lever lesson room (#418, D86): a running lift, a lever, a raised ladder. */
    @Test void forest1LeverRoom() { assertWithinBudget("forest-1 lever room", FOREST_1_LEVER_CEILING, room("forest-1", 4)); }
    /** The deadfall room at rest (#363): the tied log, the bough, the vine, the trunk. */
    @Test void forest1DeadfallRoom() { assertWithinBudget("forest-1 deadfall room", FOREST_1_DEADFALL_CEILING, room("forest-1", 1)); }
    /** The bramble room (#364): the bramble cycling (its arm grows and retracts during the measurement), the static ladder. */
    @Test void forest1BrambleRoom() { assertWithinBudget("forest-1 bramble room", FOREST_1_BRAMBLE_CEILING, room("forest-1", 6)); }
    @Test void forest1HiveRoom() { assertWithinBudget("forest-1 hive room", FOREST_1_HIVE_CEILING, room("forest-1", 10)); }
    /** The fissure room (#366, D91): two ember fissures out of step, cycling through warnings, jets and sparks, one under a branch. */
    @Test void forest2FissureRoom() { assertWithinBudget("forest-2 fissure room", FOREST_2_FISSURE_CEILING, room("forest-2", 2)); }

    /**
     * Deadfalls tripped in turn (#363): one stays tied (the hero far), and the hero's box steps onto the vine of the next one
     * every {@value #DEADFALL_CYCLE_FRAMES} frames, so the measured window holds the warning (strain, slip), the swing with its
     * kill test, the catch with the listener and the caught state; {@code update} and {@code render} (ropes, log, bough, vine)
     * every frame. The traps are built before the probe and the hero box is moved, never rebuilt.
     */
    @Test void deadfallTripAndSwing() {
        Rectangle far = new Rectangle(FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        Rectangle hero = new Rectangle(-FUNGUS_FAR_X, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        int[] caught = new int[1];
        DeadfallTrap.Listener listener = new DeadfallTrap.Listener() {
            @Override public void tripped(DeadfallTrap trap) { }
            @Override public void caught(DeadfallTrap trap) { caught[0]++; }
        };
        Runnable none = () -> { };
        float y = FUNGUS_FLOOR - DeadfallTrap.FLOOR_BELOW_PIVOT - DeadfallTrap.PIVOT_Y;
        DeadfallTrap tied = new DeadfallTrap(harness.assets(), harness.game().trapWarnings(), -DEADFALL_SPACING, y, false, () -> far, none, listener);
        DeadfallTrap[] traps = new DeadfallTrap[DEADFALL_CYCLES];
        for (int k = 0; k < traps.length; k++)
            traps[k] = new DeadfallTrap(harness.assets(), harness.game().trapWarnings(), k * DEADFALL_SPACING, y, k % 2 == 1, () -> hero, none, listener);
        float[] vineX = new float[traps.length];
        for (int k = 0; k < traps.length; k++) vineX[k] = traps[k].vineBox().x;
        boolean[] seen = new boolean[DeadfallTrap.Phase.values().length];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = Math.min(i / DEADFALL_CYCLE_FRAMES, traps.length - 1);
            hero.x = vineX[k];
            tied.update(STEP); tied.render(batch);
            for (DeadfallTrap trap : traps) { trap.update(STEP); trap.render(batch); }
            if (i >= AllocationProbe.WARMUP_FRAMES) seen[traps[k].phase().ordinal()] = true;
        });
        for (DeadfallTrap.Phase phase : DeadfallTrap.Phase.values())
            assertTrue(seen[phase.ordinal()] || phase == DeadfallTrap.Phase.TIED, "the measured window must hold a deadfall in " + phase);
        assertEquals(DeadfallTrap.Phase.TIED, tied.phase(), "the far deadfall stays tied");
        assertTrue(caught[0] >= DEADFALL_CYCLES - 2, "the swings ran to the catch: " + caught[0]);
        assertWithinBudget("deadfalls tripped in turn", DEADFALL_CEILING, bytes);
    }

    /**
     * The tree room with the hero riding a rotten branch down (D69 path: the snap to a sinking platform, the solid-floor guard, then the floor):
     * every {@value #RIDE_CYCLE_FRAMES} frames the top branch is restored intact and the hero is stood on it, so its warning, its crumble frames,
     * its fall with the hero and its removal all run inside the measured window. Each trigger spawns the shared warning light (a Torch), as the log's does.
     */
    @Test void forest1TreeRoomRidingARottenBranchDown() {
        Level level = harness.roomOf("forest-1", 11); // the tree room (#365 put the hive room before it, #484 the two spider rooms)
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
        Level level = harness.roomOf("forest-1", 9);
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
    /** The puffball cycles every 4 s (240 frames) from phase 2.52 s (since #500, D110): the swell has 29 frames left, cloud 29-89, calm 89-221, the next swell from 221; the attack starts at frame 120 and takes 48 frames, so it ends at 168, before that swell. */
    private static final int GUARD_CYCLE_FRAMES = 240, CALM_FRAME = 120;
    /** forest-1-guard: the hero's hitbox centre sits 23.5 px right of {@code position.x}; the guard's whip reaches x 2394-2400 on the floor, so a hero centred at x 2390 is in it; the entrance is (2208, 272) since #500 (D110). */
    private static final float HERO_CENTRE_OFFSET_X = 23.5f, HERO_AT_WHIP_X = 2390, ENTRANCE_X = 2208, ENTRANCE_Y = 272;

    /** The route room with the hero standing on the lift the whole time, so the rider path of {@code Player} runs every frame (it allocated a {@code Vector2} each frame before the #187 review). */
    @Test void forest1RouteRoomRidingTheLift() {
        Level level = harness.roomOf("forest-1", 8);
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
        Level level = harness.roomOf("forest-1", 4);
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
        Level level = harness.roomOf("forest-1", 4);
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
        Level level = harness.roomOf("forest-1", 2);
        var escalator = level.escalators.stream().filter(e -> !e.isVertical && e.isRunning()).findFirst().orElseThrow();
        Runnable ride = () -> {
            level.player.position.set(escalator.getX() + 3, escalator.getY() - 48);
            for (int i = 0; i < 5; i++) { escalator.update(STEP); level.player.update(STEP); }
            org.junit.jupiter.api.Assertions.assertSame(escalator, level.player.currentEscalator(), "the hero rides");
        };
        Runnable stand = () -> {
            level.player.position.set(570, 272); // plain floor, no escalator
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
        Level level = harness.roomOf("forest-1", 3);
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
        Level level = harness.roomOf("forest-2", 4);
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

    /**
     * A fireball shot every {@link #BURST_CYCLE_FRAMES} frames along a free line of forest-1 room 2 (#397): {@code Fireball.update}
     * sheds its trail into {@code Level.fireballFx()}, which is updated and drawn each frame. The balls are built before the probe.
     */
    @Test void fireballTrailThroughTheRealLevel() {
        Level level = harness.roomOf("forest-1", FIREBALL_ROOM);
        Rectangle line = new Rectangle(), room = level.currentRoomBounds();
        float reach = STEP * BURST_CYCLE_FRAMES * 280 + Fireball.BODY_SIZE;
        float startX = Float.NaN, startY = 0;
        for (float y = room.y + Fireball.BODY_SIZE; Float.isNaN(startX) && y < room.y + room.height - Fireball.BODY_SIZE; y += Fireball.BODY_SIZE)
            for (float x = room.x; x < room.x + room.width - reach; x += Fireball.BODY_SIZE)
                if (!level.blocksObject(line.set(x, y, reach, Fireball.BODY_SIZE), false)) { startX = x; startY = y; break; }
        assertFalse(Float.isNaN(startX), "the room must have a free line for this scenario");
        Fireball[] shots = new Fireball[FIREBALL_SHOTS];
        for (int k = 0; k < shots.length; k++) shots[k] = new Fireball(level, startX, startY, true);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            Fireball shot = shots[Math.min(i / BURST_CYCLE_FRAMES, shots.length - 1)];
            if (!shot.isRemoved()) { shot.update(STEP); shot.render(batch); }
            level.fireballFx().update(STEP);
            level.fireballFx().render(batch);
            peak[0] = Math.max(peak[0], level.fireballFx().hard().count());
        });
        assertTrue(peak[0] > 0, "the trail must actually burn in this scenario");
        assertWithinBudget("fireball trail", FIREBALL_TRAIL_CEILING, bytes);
    }

    /**
     * The cast of #503 through the real {@code Level.update}, key to ball: a real press every {@link #BURST_CYCLE_FRAMES} frames
     * ({@code Player.shoot} builds the ball and queues it, {@code stepNewFireballs} flies its first step, {@code finishUpdate} joins it)
     * against the same room with no press. A whole forest-1 room allocates 220 to 540 B/frame by itself in this probe, and which
     * figure a window reads depends on the JIT state, so two single windows cannot be subtracted: the paired probe alternates
     * the two scenes and takes the median of the differences. The scene proves it cast: every press spends a charge, and a ball is
     * seen in the object list after a press. The extra is one {@code new Fireball} per cast.
     */
    @Test void fireballCastWithTheRealKeyPress() {
        Level cast = harness.roomOf("forest-1", FIREBALL_ROOM), quiet = harness.roomOf("forest-1", FIREBALL_ROOM);
        Rectangle view = new Rectangle(cast.currentRoomBounds());
        int[] presses = new int[1], spent = new int[1], seen = new int[1];
        double extra = AllocationProbe.medianPairedDifference(FIREBALL_CAST_PAIRS, FIREBALL_CAST_WINDOW_FRAMES, () -> { },
            i -> {
                harness.pressed.clear(); harness.held.clear();
                boolean press = i > 0 && i % BURST_CYCLE_FRAMES == 0;   // not on the level's very first frame
                if (press) { cast.rangedStock.restore(3); harness.held.add(com.badlogic.gdx.Input.Keys.L); harness.pressed.add(com.badlogic.gdx.Input.Keys.L); presses[0]++; }
                cast.update(STEP); cast.render(batch, view);
                if (press) {
                    if (cast.rangedStock.count() == 2) spent[0]++;
                    boolean ball = false;
                    for (int o = 0; o < cast.objects.size(); o++) ball |= cast.objects.get(o) instanceof Fireball f && !f.isRemoved();
                    if (ball) seen[0]++;
                }
            },
            () -> { }, i -> { harness.pressed.clear(); harness.held.clear(); quiet.update(STEP); quiet.render(batch, view); });
        System.out.printf("ALLOCATION fireball cast: median cast minus no cast %.1f B/frame over %d pairs (%d presses, %d spent a charge, %d saw a ball)%n",
            extra, FIREBALL_CAST_PAIRS, presses[0], spent[0], seen[0]);
        assertTrue(presses[0] > 0, "the scene must press");
        assertEquals(presses[0], spent[0], "every press cast a ball: a charge was spent");
        assertEquals(presses[0], seen[0], "and the ball was in the level after its cast frame");
        assertWithinBudget("fireball cast over the same room with no cast", FIREBALL_CAST_EXTRA_CEILING, extra);
    }

    /** A fireball burst every {@link #BURST_CYCLE_FRAMES} frames through the real {@code Level.fireballFx()} (#397): a light hit, the boss's heavy hit and a burn-out in turn, each over its whole life. */
    @Test void fireballImpactThroughTheRealLevel() {
        Level level = harness.roomOf("forest-1", FIREBALL_ROOM);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) {
                int kind = i / BURST_CYCLE_FRAMES % FIREBALL_BURST_KINDS;
                if (kind == 0) level.fireballFx().hit(100, 200, -1);
                else if (kind == 1) level.fireballFx().heavyHit(100, 200, 1);
                else level.fireballFx().fizzle(100, 200);
            }
            level.fireballFx().update(STEP);
            level.fireballFx().render(batch);
            peak[0] = Math.max(peak[0], level.fireballFx().soft().count() + level.fireballFx().hard().count());
        });
        assertTrue(peak[0] > 0, "the bursts must actually throw particles in this scenario");
        assertWithinBudget("fireball impact", FIREBALL_IMPACT_CEILING, bytes);
    }

    private static final int BEETLE_CYCLE_FRAMES = 120, BEETLE_HEARD_FRAME = 5, BEETLE_ESCAPE_FRAME = 45, BEETLE_KILL_FRAME = 55, BEETLE_SETTLE_FRAMES = 60;
    /** D101: the hero walks up to an odd beetle's body (it rocks), then the body is crumbled or burnt. */
    private static final int BEETLE_PUSH_FRAME = 70, BEETLE_FINISH_FRAME = 95, BEETLE_KINDS = 4, BEETLE_BURNT = 3;
    private static final float HERO_AT_BODY_X = 254 - 29;
    private static final float BEETLE_LEFT_END = 256, BEETLE_Y = 304, BEETLE_W = 24, BEETLE_H = 16, BEETLE_RANGE_TILES = 8;
    private static final float HERO_TOUCHING_X = 250 - 29, HERO_CLEAR_X = 200 - 29, HERO_FAR_X = 600 - 29, HERO_Y = 272;

    /**
     * Beetles in turn through the real {@code Level} of beetle-test.tmx (#327, D100): each crawls a few frames, then hears the hero, notices (0.5 s), finds
     * him touching and commits, winds up with its specks; the hero steps out of the box at frame {@value #BEETLE_ESCAPE_FRAME}
     * of the cycle; even beetles burst, odd ones are killed at frame {@value #BEETLE_KILL_FRAME} as a fireball would (the poof)
     * and leave their body (D101): the hero walks up to it at frame {@value #BEETLE_PUSH_FRAME} (it rocks, fading into the decor
     * body), and at frame {@value #BEETLE_FINISH_FRAME} it crumbles or, one beetle in four, burns.
     * {@code update} and {@code render} of the beetle and {@code Level.beetleFx()} every frame. Beetles are built before the probe.
     */
    @Test void beetleThroughTheRealLevel() {
        Level level = harness.roomOf("beetle-test", 0);
        for (int i = 0; i < BEETLE_SETTLE_FRAMES; i++) level.update(STEP);   // the hero finishes appearing
        int cycles = (AllocationProbe.WARMUP_FRAMES + AllocationProbe.MEASURED_FRAMES) / BEETLE_CYCLE_FRAMES + 1;
        Beetle[] beetles = new Beetle[cycles];
        for (int k = 0; k < cycles; k++) {
            beetles[k] = new Beetle(harness.assets(), harness.game(), BEETLE_LEFT_END, BEETLE_Y, BEETLE_W, BEETLE_H, 0, BEETLE_RANGE_TILES);
            beetles[k].setPlayer(level.player);
            beetles[k].setLevel(level);
        }
        boolean[] seen = new boolean[Beetle.State.values().length];
        int[] peak = new int[1];
        float[] maxRock = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = i / BEETLE_CYCLE_FRAMES, f = i % BEETLE_CYCLE_FRAMES;
            Beetle b = beetles[k];
            if (f == 0) level.player.position.set(HERO_FAR_X, HERO_Y);
            if (f == BEETLE_HEARD_FRAME) level.player.position.set(HERO_TOUCHING_X, HERO_Y);
            if (f == BEETLE_ESCAPE_FRAME) level.player.position.set(HERO_CLEAR_X, HERO_Y);
            if (f == BEETLE_KILL_FRAME && k % 2 == 1) b.collidedWithActor(true);
            if (f == BEETLE_PUSH_FRAME && k % 2 == 1) level.player.position.set(HERO_AT_BODY_X, HERO_Y);
            if (f == BEETLE_FINISH_FRAME && k % 2 == 1) { if (k % BEETLE_KINDS == BEETLE_BURNT) b.burnUp(); else b.crumble(); }
            if (!b.isRemoved()) { b.update(STEP); b.render(batch); seen[b.getState().ordinal()] = true; maxRock[0] = Math.max(maxRock[0], Math.abs(b.rockAngle())); }
            level.beetleFx().update(STEP);
            level.beetleFx().render(batch);
            level.remainsFx().update(STEP);
            level.remainsFx().render(batch);
            peak[0] = Math.max(peak[0], level.beetleFx().soft().count() + level.beetleFx().hard().count());
        });
        for (Beetle.State state : Beetle.State.values()) if (state != Beetle.State.CHASE) assertTrue(seen[state.ordinal()], "the scenario must reach " + state); // CHASE commits within its first frame here
        assertTrue(peak[0] > 0, "the beetles must actually throw particles");
        assertFalse(level.player.isGotHit(), "the hero stepped clear of every burst");
        assertTrue(maxRock[0] > 0, "an odd beetle's body must rock when the hero walks up to it");
        assertWithinBudget("beetle notice, wind-up, burst, death and body", BEETLE_CEILING, bytes);
    }

    private static final int SPIDER_CYCLE_FRAMES = 150, SPIDER_HEARD_FRAME = 5, SPIDER_ESCAPE_FRAME = 50, SPIDER_KILL_FRAME = 110;
    private static final int SPIDER_PUSH_FRAME = 125, SPIDER_FINISH_FRAME = 140, SPIDER_KINDS = 4, SPIDER_BURNT = 3;
    private static final float SPIDER_LEFT_END = 256, SPIDER_Y = 304, SPIDER_W = 32, SPIDER_H = 16, SPIDER_RANGE_TILES = 8;
    /** Hero positions (x of the 64 px frame): in its hearing and pounce box; clear of the locked bite; beside the body. */
    private static final float HERO_POUNCED_X = 330 - 29, HERO_STEPPED_X = 360 - 29, HERO_BESIDE_SPIDER_X = 310 - 29;

    /**
     * Spiders in turn through the real {@code Level} of spider-test.tmx (#328, D102): each skitters a few frames, hears the hero,
     * notices (0.5 s), chases and commits at once with the hero in its pounce box, winds up with its silk; the hero steps clear
     * of the locked bite at frame {@value #SPIDER_ESCAPE_FRAME}, so the leap and the bite miss and it recovers, then chases and
     * winds up again; odd spiders are killed at frame {@value #SPIDER_KILL_FRAME} as a fireball would and leave their body
     * (D101): the hero walks up to it at frame {@value #SPIDER_PUSH_FRAME} (it rocks), and at frame {@value #SPIDER_FINISH_FRAME}
     * it crumbles or, one spider in four, burns. {@code update} and {@code render} of the spider, {@code Level.spiderFx()} and
     * {@code Level.remainsFx()} every frame. Spiders are built before the probe.
     */
    @Test void spiderThroughTheRealLevel() {
        Level level = harness.roomOf("spider-test", 0);
        for (int i = 0; i < BEETLE_SETTLE_FRAMES; i++) level.update(STEP);   // the hero finishes appearing
        int cycles = (AllocationProbe.WARMUP_FRAMES + AllocationProbe.MEASURED_FRAMES) / SPIDER_CYCLE_FRAMES + 1;
        Spider[] spiders = new Spider[cycles];
        for (int k = 0; k < cycles; k++) {
            spiders[k] = new Spider(harness.assets(), harness.game(), SPIDER_LEFT_END, SPIDER_Y, SPIDER_W, SPIDER_H, 0, SPIDER_RANGE_TILES);
            spiders[k].setPlayer(level.player);
            spiders[k].setLevel(level);
        }
        boolean[] seen = new boolean[Spider.State.values().length];
        int[] peak = new int[1];
        float[] maxRock = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = i / SPIDER_CYCLE_FRAMES, f = i % SPIDER_CYCLE_FRAMES;
            Spider s = spiders[k];
            if (f == 0) level.player.position.set(HERO_FAR_X, HERO_Y);
            if (f == SPIDER_HEARD_FRAME) level.player.position.set(HERO_POUNCED_X, HERO_Y);
            if (f == SPIDER_ESCAPE_FRAME) level.player.position.set(HERO_STEPPED_X, HERO_Y);
            if (f == SPIDER_KILL_FRAME && k % 2 == 1) s.collidedWithActor(true);
            if (f == SPIDER_PUSH_FRAME && k % 2 == 1) level.player.position.set(HERO_BESIDE_SPIDER_X, HERO_Y);
            if (f == SPIDER_FINISH_FRAME && k % 2 == 1) { if (k % SPIDER_KINDS == SPIDER_BURNT) s.burnUp(); else s.crumble(); }
            if (!s.isRemoved()) { s.update(STEP); s.render(batch); seen[s.getState().ordinal()] = true; maxRock[0] = Math.max(maxRock[0], Math.abs(s.rockAngle())); }
            level.spiderFx().update(STEP);
            level.spiderFx().render(batch);
            level.remainsFx().update(STEP);
            level.remainsFx().render(batch);
            peak[0] = Math.max(peak[0], level.spiderFx().soft().count() + level.spiderFx().hard().count());
        });
        for (Spider.State state : FLOOR_STATES) assertTrue(seen[state.ordinal()], "the scenario must reach " + state);
        assertTrue(peak[0] > 0, "the spiders must actually throw particles");
        assertFalse(level.player.isGotHit(), "the hero stepped clear of every bite");
        assertTrue(maxRock[0] > 0, "an odd spider's body must rock when the hero walks up to it");
        assertWithinBudget("spider notice, wind-up, leap, bite, recovery, death and body", SPIDER_CEILING, bytes);
    }

    private static final int SNAIL_CYCLE_FRAMES = 300, SNAIL_SEEN_FRAME = 5, SNAIL_LIFT_FROM = 86, SNAIL_LIFT_TO = 200, SNAIL_BREAK_FRAME = 110;
    private static final int SNAIL_KILL_FRAME = 100, SNAIL_PUSH_FRAME = 220, SNAIL_FINISH_FRAME = 260, SNAIL_KINDS = 4, SNAIL_ODD = 3, SNAIL_AT_END = 3;
    private static final float SNAIL_X = 320, SNAIL_Y = 304, SNAIL_W = 24, SNAIL_H = 16, SNAIL_OFF_NEG = 3, SNAIL_OFF_POS = 4;
    /** Hero positions (x of the 64 px frame): ahead in the snail's line; the feet 12 px up, above the line; beside the body. */
    private static final float HERO_IN_LINE_X = 250 - 29, HERO_LIFT = 12, HERO_BESIDE_SNAIL_X = 318 - 29;

    /**
     * Snails in turn through the real {@code Level} of snail-test.tmx (#329, D106): each crawls a few frames, sees the hero ahead
     * at frame {@value #SNAIL_SEEN_FRAME} (one in three has paused at its range's end first), notices (0.5 s), winds up with its specks and swelling gob and spits; the hero is held
     * above the line from frame {@value #SNAIL_LIFT_FROM} to {@value #SNAIL_LIFT_TO}, so the gob flies its range shedding drips,
     * drops and splats on the floor, while the snail reloads, then loses the hero and crawls on. One even snail in two has its
     * gob cut by the blade, the other burnt, at frame {@value #SNAIL_BREAK_FRAME}. Odd snails are killed at frame
     * {@value #SNAIL_KILL_FRAME} with their gob in the air (it flies on), leave their body, are walked up to at frame
     * {@value #SNAIL_PUSH_FRAME} (it rocks) and crumble or, one in four, burn at frame {@value #SNAIL_FINISH_FRAME}.
     * {@code update} and {@code render} of the snail, {@code Level.snailFx()} and {@code Level.remainsFx()} every frame. Snails are
     * built before the probe.
     */
    @Test void snailThroughTheRealLevel() {
        Level level = harness.roomOf("snail-test", 0);
        for (int i = 0; i < BEETLE_SETTLE_FRAMES; i++) level.update(STEP);   // the hero finishes appearing
        int cycles = (AllocationProbe.WARMUP_FRAMES + AllocationProbe.MEASURED_FRAMES) / SNAIL_CYCLE_FRAMES + 1;
        Snail[] snails = new Snail[cycles];
        for (int k = 0; k < cycles; k++) {
            // One snail in three starts at its range's left end, so it pauses there before the hero comes into view.
            snails[k] = new Snail(harness.assets(), harness.game(), SNAIL_X, SNAIL_Y, SNAIL_W, SNAIL_H, k % SNAIL_AT_END == SNAIL_AT_END - 1 ? 0 : SNAIL_OFF_NEG,
                SNAIL_OFF_POS);
            snails[k].setPlayer(level.player);
            snails[k].setLevel(level);
        }
        boolean[] seen = new boolean[Snail.State.values().length];
        int[] peak = new int[1], gobs = new int[1];
        float[] maxRock = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = i / SNAIL_CYCLE_FRAMES, f = i % SNAIL_CYCLE_FRAMES;
            Snail s = snails[k];
            if (f == 0) level.player.position.set(HERO_FAR_X, HERO_Y);
            if (f == SNAIL_SEEN_FRAME) level.player.position.set(HERO_IN_LINE_X, HERO_Y);
            if (f >= SNAIL_LIFT_FROM && f < SNAIL_LIFT_TO) { level.player.position.set(HERO_IN_LINE_X, HERO_Y - HERO_LIFT); level.player.velocity().setZero(); }
            if (f == SNAIL_LIFT_TO) level.player.position.set(HERO_FAR_X, HERO_Y);
            if (f == SNAIL_BREAK_FRAME && k % 2 == 0) s.breakApart(k % SNAIL_KINDS == 0);
            if (f == SNAIL_KILL_FRAME && k % 2 == 1) s.collidedWithActor(true);
            if (f == SNAIL_PUSH_FRAME && k % 2 == 1) level.player.position.set(HERO_BESIDE_SNAIL_X, HERO_Y);
            if (f == SNAIL_FINISH_FRAME && k % 2 == 1) { if (k % SNAIL_KINDS == SNAIL_ODD) s.burnUp(); else s.crumble(); }
            if (!s.isRemoved()) {
                s.update(STEP); s.render(batch); seen[s.state().ordinal()] = true;
                if (s.gobActive()) gobs[0]++;
                maxRock[0] = Math.max(maxRock[0], Math.abs(s.rockAngle()));
            }
            level.snailFx().update(STEP);
            level.snailFx().render(batch);
            level.remainsFx().update(STEP);
            level.remainsFx().render(batch);
            peak[0] = Math.max(peak[0], level.snailFx().soft().count() + level.snailFx().hard().count());
        });
        for (Snail.State state : Snail.State.values()) assertTrue(seen[state.ordinal()], "the scenario must reach " + state);
        assertTrue(gobs[0] > 0, "the gobs must actually fly");
        assertTrue(peak[0] > 0, "the snails must actually throw particles");
        assertFalse(level.player.isGotHit(), "every gob passed under the hero or was broken");
        assertTrue(maxRock[0] > 0, "an odd snail's body must rock when the hero walks up to it");
        assertWithinBudget("snail notice, wind-up, spit, gob, reload, death and body", SNAIL_CEILING, bytes);
    }

    private static final int WOLF_CYCLE_FRAMES = 420, WOLF_SEEN_FRAME = 10, WOLF_PUSH_AFTER_KILL = 30, WOLF_FINISH_AFTER_KILL = 90;
    private static final int WOLF_KINDS = 4, WOLF_BURNT = 3;
    private static final float WOLF_X = 640, WOLF_Y = 296, WOLF_W = 48, WOLF_H = 24, WOLF_OFF = 6, WOLF_LIFT = 40;
    /** Hero positions (x of the 64 px frame): 150 px ahead of the muzzle, in its sight; far, out of sight and hearing. */
    private static final float WOLF_HERO_AHEAD_X = 489 - 29, WOLF_HERO_FAR_X = 3;

    /**
     * Wolves in turn through the real {@code Level} of wolf-test.tmx (#331, D115). Each cycle the hero appears 150 px ahead (frame
     * {@value #WOLF_SEEN_FRAME}; every even wolf spawns at its range's left end, so it sniffs there first); the wolf notices
     * (0.5 s) and sprints; the hero jumps behind it as it runs, so it skids and turns; it commits, winds up with its breath streaks
     * and lunges, and the hero is gone when the jaws leave (a miss). Even wolves bite, recover, chase a hero lifted 40 px (out of
     * reach: it runs to its range end and bays), give up and trot on. Odd wolves are killed in the lunge (the body slides on),
     * lie, are walked up to ({@value #WOLF_PUSH_AFTER_KILL} frames later: they twitch) and crumble or, one in {@value #WOLF_KINDS}, burn.
     * {@code update} and {@code render} of the wolf, {@code Level.wolfFx()} and {@code Level.remainsFx()} every frame. Wolves are
     * built before the probe.
     */
    @Test void wolfThroughTheRealLevel() {
        Level level = harness.roomOf("wolf-test", 0);
        for (int i = 0; i < BEETLE_SETTLE_FRAMES; i++) level.update(STEP);   // the hero finishes appearing
        int cycles = (AllocationProbe.WARMUP_FRAMES + AllocationProbe.MEASURED_FRAMES) / WOLF_CYCLE_FRAMES + 1;
        Wolf[] wolves = new Wolf[cycles];
        for (int k = 0; k < cycles; k++) {
            // Even wolves spawn at the left end of the same range (x 544..736), so they sniff there before the hero comes into view.
            wolves[k] = k % 2 == 0 ? new Wolf(harness.assets(), harness.game(), WOLF_X - WOLF_OFF * 16, WOLF_Y, WOLF_W, WOLF_H, 0, 2 * WOLF_OFF)
                : new Wolf(harness.assets(), harness.game(), WOLF_X, WOLF_Y, WOLF_W, WOLF_H, WOLF_OFF, WOLF_OFF);
            wolves[k].setPlayer(level.player);
            wolves[k].setLevel(level);
        }
        boolean[] seen = new boolean[Wolf.State.values().length];
        boolean[] turned = new boolean[cycles], missed = new boolean[cycles], recovered = new boolean[cycles], lifted = new boolean[cycles], done = new boolean[cycles];
        int[] killedAt = new int[cycles], peak = new int[1];
        float[] maxTwitch = new float[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = i / WOLF_CYCLE_FRAMES, f = i % WOLF_CYCLE_FRAMES;
            Wolf wolf = wolves[k];
            if (f == 0) level.player.position.set(WOLF_HERO_FAR_X, HERO_Y);
            if (f == WOLF_SEEN_FRAME) level.player.position.set(WOLF_HERO_AHEAD_X, HERO_Y);
            Wolf.State st = wolf.state();
            if (st == Wolf.State.CHASE && !turned[k] && wolf.speed() > 100) {   // he jumps behind it as it runs: it must skid and turn
                level.player.position.set(wolf.position.x + 148 - 29, HERO_Y);
                turned[k] = true;
            }
            if (st == Wolf.State.LUNGE && !missed[k]) {
                level.player.position.set(WOLF_HERO_FAR_X, HERO_Y);   // gone as the jaws leave: a miss
                missed[k] = true;
                if (k % 2 == 1) { wolf.collidedWithActor(true); killedAt[k] = f; }   // odd wolves are killed in the lunge
            }
            if (st == Wolf.State.RECOVER) recovered[k] = true;
            if (recovered[k] && st == Wolf.State.CHASE && !lifted[k]) lifted[k] = true;   // then he stands 40 px up, out of its reach
            if (lifted[k] && !done[k]) {
                if (wolf.state() == Wolf.State.TROT) { done[k] = true; level.player.position.set(WOLF_HERO_FAR_X, HERO_Y); }
                else { level.player.position.set(WOLF_HERO_AHEAD_X, HERO_Y - WOLF_LIFT); level.player.velocity().setZero(); }
            }
            if (killedAt[k] > 0 && f == killedAt[k] + WOLF_PUSH_AFTER_KILL) level.player.position.set(wolf.position.x + 8 - 29, HERO_Y);
            if (killedAt[k] > 0 && f == killedAt[k] + WOLF_FINISH_AFTER_KILL) { if (k % WOLF_KINDS == WOLF_BURNT) wolf.burnUp(); else wolf.crumble(); }
            if (!wolf.isRemoved()) {
                wolf.update(STEP); wolf.render(batch); seen[wolf.state().ordinal()] = true;
                maxTwitch[0] = Math.max(maxTwitch[0], Math.abs(wolf.twitch()));
            }
            level.wolfFx().update(STEP);
            level.wolfFx().render(batch);
            level.remainsFx().update(STEP);
            level.remainsFx().render(batch);
            peak[0] = Math.max(peak[0], level.wolfFx().soft().count() + level.wolfFx().hard().count());
        });
        for (Wolf.State state : Wolf.State.values()) assertTrue(seen[state.ordinal()], "the scenario must reach " + state);
        assertTrue(peak[0] > 0, "the wolves must actually throw particles");
        assertFalse(level.player.isGotHit(), "the hero was gone before every bite");
        assertTrue(maxTwitch[0] > 0, "an odd wolf's body must twitch when the hero walks up to it");
        assertWithinBudget("wolf notice, sprint, skid, wind-up, lunge, bite, recovery, bay, death and body", WOLF_CEILING, bytes);
    }

    /** The floor spider's states (D102); the climb and the drop (D103) have their own scenarios below. */
    private static final Spider.State[] FLOOR_STATES = {Spider.State.CRAWL, Spider.State.NOTICE, Spider.State.CHASE, Spider.State.WINDUP,
        Spider.State.LEAP, Spider.State.BITE, Spider.State.RECOVER, Spider.State.DEAD, Spider.State.BURN};
    private static final int ROUTE_CYCLE_FRAMES = 300, ROUTE_HEARD_FRAME = 5, ROUTE_KILL_FRAME = 140, ROUTE_FINISH_FRAME = 220;
    private static final int DROP_ASIDE_FRAME = 80, DROP_AGAIN_FRAME = 250, CLIMB_DOWN_FRAME = 110, CLIMB_KILL_FRAME = 120;
    private static final float ROUTE_HERO_FAR_X = 3, DROP_UNDER_X = 460 - 29, DROP_ASIDE_X = 480 - 29;
    private static final float CLIMB_LEDGE_X = 430 - 29, CLIMB_LEDGE_Y = 224 - 48, CLIMB_BELOW_X = 250 - 29;

    /** The route of a fixture's spider, traced over its solids as {@code Level} does (built before the probe). */
    private static Spider.Route route(float[] points, Rectangle... solids) { return Spider.Route.trace(points, java.util.List.of(solids)); }

    /**
     * Spiders in turn on their silk thread through the real {@code Level} of spider-drop.tmx (#483, D103): each hears the hero
     * under its shelf, notices, runs to the lip and winds up hanging over it with glints down the column; the hero steps aside at
     * frame {@value #DROP_ASIDE_FRAME}, so the drop and the bite at the thread's end miss, it hangs and reels back up, the thread
     * drawn every frame; odd spiders are killed hanging at frame {@value #ROUTE_KILL_FRAME} (the thread snaps, the body falls) and
     * crumble or, one in four, burn at frame {@value #ROUTE_FINISH_FRAME}; even ones see the hero back under them at frame
     * {@value #DROP_AGAIN_FRAME} and wind up again. Update and render of the spider, {@code spiderFx} and {@code remainsFx}.
     */
    @Test void spiderDropThroughTheRealLevel() {
        Level level = harness.roomOf("spider-drop", 0);
        for (int i = 0; i < BEETLE_SETTLE_FRAMES; i++) level.update(STEP);
        int cycles = (AllocationProbe.WARMUP_FRAMES + AllocationProbe.MEASURED_FRAMES) / ROUTE_CYCLE_FRAMES + 1;
        Spider[] spiders = new Spider[cycles];
        for (int k = 0; k < cycles; k++) {
            spiders[k] = new Spider(harness.assets(), harness.game(), route(new float[]{384, 208, 448, 208, 448, 320},
                new Rectangle(0, 320, 640, 48), new Rectangle(320, 208, 128, 16)));
            spiders[k].setPlayer(level.player);
            spiders[k].setLevel(level);
        }
        boolean[] seen = new boolean[Spider.State.values().length];
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = i / ROUTE_CYCLE_FRAMES, f = i % ROUTE_CYCLE_FRAMES;
            Spider s = spiders[k];
            if (f == 0) level.player.position.set(ROUTE_HERO_FAR_X, HERO_Y);
            if (f == ROUTE_HEARD_FRAME) level.player.position.set(DROP_UNDER_X, HERO_Y);
            if (f == DROP_ASIDE_FRAME) level.player.position.set(DROP_ASIDE_X, HERO_Y);
            if (f == ROUTE_KILL_FRAME && k % 2 == 1) s.collidedWithActor(true);
            if (f == ROUTE_FINISH_FRAME && k % 2 == 1) { if (k % SPIDER_KINDS == SPIDER_BURNT) s.burnUp(); else s.crumble(); }
            if (f == DROP_AGAIN_FRAME && k % 2 == 0) level.player.position.set(DROP_ASIDE_X, HERO_Y);
            if (!s.isRemoved()) { s.update(STEP); s.render(batch); seen[s.getState().ordinal()] = true; }
            level.spiderFx().update(STEP);
            level.spiderFx().render(batch);
            level.remainsFx().update(STEP);
            level.remainsFx().render(batch);
            peak[0] = Math.max(peak[0], level.spiderFx().soft().count() + level.spiderFx().hard().count());
        });
        for (Spider.State state : new Spider.State[]{Spider.State.NOTICE, Spider.State.CHASE, Spider.State.SET, Spider.State.DROP,
            Spider.State.DANGLE, Spider.State.HANG, Spider.State.REEL, Spider.State.DEAD, Spider.State.BURN})
            assertTrue(seen[state.ordinal()], "the scenario must reach " + state);
        assertTrue(peak[0] > 0, "the spiders must actually throw particles");
        assertFalse(level.player.isGotHit(), "the hero stepped out of every drop");
        assertWithinBudget("spider drop wind-up, drop, bite, hang, reel, thread, snap and body", SPIDER_ROUTE_CEILING, bytes);
    }

    /**
     * Spiders in turn on a wall through the real {@code Level} of spider-climb.tmx (#483, D103): each hears the hero on the ledge
     * above, notices, runs to the wall's foot and climbs it head up; at frame {@value #CLIMB_DOWN_FRAME} the hero is back on the
     * floor below, out of the pounce's reach, and it comes down head first; odd spiders (the first after the warm-up: the shared
     * soft-dot texture is built at level load, so kill order does not matter) are killed at frame
     * {@value #CLIMB_KILL_FRAME}, on the wall, and their bodies fall to its foot and crumble or burn at frame
     * {@value #ROUTE_FINISH_FRAME}. Update and render of the spider, {@code spiderFx} and {@code remainsFx}.
     */
    @Test void spiderClimbThroughTheRealLevel() {
        Level level = harness.roomOf("spider-climb", 0);
        for (int i = 0; i < BEETLE_SETTLE_FRAMES; i++) level.update(STEP);
        int cycles = (AllocationProbe.WARMUP_FRAMES + AllocationProbe.MEASURED_FRAMES) / ROUTE_CYCLE_FRAMES + 1;
        Spider[] spiders = new Spider[cycles];
        for (int k = 0; k < cycles; k++) {
            spiders[k] = new Spider(harness.assets(), harness.game(), route(new float[]{320, 320, 400, 320, 400, 224, 528, 224},
                new Rectangle(0, 320, 640, 48), new Rectangle(400, 224, 224, 96)));
            spiders[k].setPlayer(level.player);
            spiders[k].setLevel(level);
        }
        boolean[] seen = new boolean[Spider.State.values().length], wall = new boolean[2];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            int k = i / ROUTE_CYCLE_FRAMES, f = i % ROUTE_CYCLE_FRAMES;
            Spider s = spiders[k];
            if (f == 0) level.player.position.set(ROUTE_HERO_FAR_X, HERO_Y);
            if (f == ROUTE_HEARD_FRAME) level.player.position.set(CLIMB_LEDGE_X, CLIMB_LEDGE_Y);
            if (f == CLIMB_DOWN_FRAME) level.player.position.set(CLIMB_BELOW_X, HERO_Y);
            if (f == CLIMB_KILL_FRAME && k % 2 == 1) s.collidedWithActor(true);
            if (f == ROUTE_FINISH_FRAME && k % 2 == 1) { if (k % SPIDER_KINDS == SPIDER_BURNT) s.burnUp(); else s.crumble(); }
            if (!s.isRemoved()) {
                s.update(STEP);
                s.render(batch);
                seen[s.getState().ordinal()] = true;
                if (s.onWall()) wall[s.headUp() ? 0 : 1] = true;
            }
            level.spiderFx().update(STEP);
            level.spiderFx().render(batch);
            level.remainsFx().update(STEP);
            level.remainsFx().render(batch);
        });
        assertTrue(wall[0] && wall[1], "it climbs the wall head up and comes down head first");
        for (Spider.State state : new Spider.State[]{Spider.State.NOTICE, Spider.State.CHASE, Spider.State.DEAD, Spider.State.BURN})
            assertTrue(seen[state.ordinal()], "the scenario must reach " + state);
        assertFalse(level.player.isGotHit(), "it never struck from the wall");
        assertWithinBudget("spider wall climb, its body falling from the wall", SPIDER_ROUTE_CEILING, bytes);
    }

    /** A pickup spark burst every {@link #BURST_CYCLE_FRAMES} frames through the real {@code Level.pickupSparks()} (#402), updated and drawn each frame. */
    @Test void pickupSparksThroughTheRealLevel() {
        Level level = harness.roomOf("forest-1", FIREBALL_ROOM);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.pickupSparks().burst(100, 200);
            level.pickupSparks().update(STEP);
            level.pickupSparks().render(batch);
            peak[0] = Math.max(peak[0], level.pickupSparks().sparks().count());
        });
        assertTrue(peak[0] > 0, "the bursts must actually throw sparks in this scenario");
        assertWithinBudget("pickup sparks", PICKUP_SPARKS_CEILING, bytes);
    }

    /** The leaves of a Forest room (#473) through the real {@code Level}: the room's ten leaves come from its map, so the scenario is the game's own. */
    @Test void fallingLeavesThroughTheRealLevel() {
        Level whole = harness.roomOf("forest-1", LEAF_ROOM);
        Rectangle view = new Rectangle(whole.currentRoomBounds());
        int[] alive = new int[1];
        double room = AllocationProbe.bytesPerFrame(i -> {
            whole.update(STEP); whole.render(batch, view);
            alive[0] = Math.max(alive[0], whole.leafFx().particles().count());
        });
        assertTrue(alive[0] > 0, "the whole-level scenario must have leaves");
        assertWithinBudget("forest-1 deadfall room with leaves, whole level", FOREST_1_DEADFALL_CEILING, room);
        Level level = harness.roomOf("forest-1", LEAF_ROOM);
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            level.leafFx().update(STEP); level.leafFx().render(batch);
            peak[0] = Math.max(peak[0], level.leafFx().particles().count());
        });
        assertTrue(peak[0] > 0, "the room must have leaves in this scenario");
        assertWithinBudget("falling leaves", FALLING_LEAVES_CEILING, bytes);
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
            items[n] = new Collectable(harness.assets(), harness.game(), types[n].name(), n * TRAP_TILE * 2, FUNGUS_FLOOR, TRAP_TILE, TRAP_TILE);
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
        Level level = harness.roomOf("forest-2", 4);
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

    /** #364: two brambles grow, hold and retract (rustle frames, cane segments, tip, the mirrored draw) while the hero is tested against them. */
    @Test void brambleCycles() {
        com.jlogicsoftware.kimeria.entity.objects.Bramble left = new com.jlogicsoftware.kimeria.entity.objects.Bramble(harness.assets(),
            0, FUNGUS_FLOOR - BRAMBLE_HEIGHT, BRAMBLE_REACH, BRAMBLE_HEIGHT, harness.game().trapWarnings(), false, 4f, 0f, 1f);
        com.jlogicsoftware.kimeria.entity.objects.Bramble right = new com.jlogicsoftware.kimeria.entity.objects.Bramble(harness.assets(),
            FUNGUS_SPACING, FUNGUS_FLOOR - BRAMBLE_HEIGHT, BRAMBLE_REACH, BRAMBLE_HEIGHT, harness.game().trapWarnings(), true, 4f, 1.3f, 1f);
        Rectangle hero = new Rectangle(BRAMBLE_REACH / 2f, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        int[] hits = new int[1];
        int[] cues = new int[1];
        left.onCues(() -> cues[0]++, () -> cues[0]++);
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            left.update(STEP); right.update(STEP);
            if (left.lethalTo(hero) || right.lethalTo(hero)) hits[0]++;
            left.render(batch); right.render(batch);
        });
        assertTrue(hits[0] > 0 && cues[0] > 0, "the arm must actually grow over the hero in this scenario");
        assertWithinBudget("bramble cycles", BRAMBLE_CEILING, bytes);
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

    /**
     * The thorn root's spring through the real {@code Level} of thorn-test.tmx (#446): {@code Level.thornSprung} (the cue, its
     * {@code playSounds()} test; the harness plays no sound) and {@code EnvironmentFx.thornSpecks} every {@value #BURST_CYCLE_FRAMES} frames,
     * with {@code fx.update} and {@code fx.render} each frame, the specks living at most 0.4 s.
     */
    @Test void thornSpringThroughTheRealLevel() {
        Level level = harness.roomOf("thorn-test", 0);
        ThornTrap root = level.objects.stream().filter(ThornTrap.class::isInstance).map(ThornTrap.class::cast)
            .filter(t -> t.kind() == ThornTrap.Kind.ROOT).findFirst().orElseThrow();
        int[] peak = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % BURST_CYCLE_FRAMES == 0) level.thornSprung(root);
            level.fx().update(STEP);
            level.fx().render(batch);
            peak[0] = Math.max(peak[0], level.fx().grit().count());
        });
        assertTrue(peak[0] > 0, "the specks must actually fly in this scenario");
        assertWithinBudget("thorn spring", THORN_SPRING_CEILING, bytes);
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

    /** Ten plain falling platforms at rest (#362): the rope sway of the split plank (the D71 cue, #236), {@code update} and {@code render}, each frame. */
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

    /**
     * Hanging hives (#365, D90) through calm (drifting specks), warning (shake, gathering specks), release (the stinger band drawn
     * a row at a time, its {@code lethalTo} against a hero under it every frame) and one knock-down inside the measured window
     * (the scatter burst, the fall, the in-flight band finishing); update and render through the batch.
     */
    @Test void hangingHivesCycleAndOneIsKnockedDown() {
        HangingHive[] hives = new HangingHive[HIVE_COUNT];
        for (int k = 0; k < hives.length; k++)
            hives[k] = new HangingHive(harness.assets(), k * FUNGUS_SPACING, HIVE_TOP, harness.game().trapWarnings(), HangingHive.DEFAULT_PERIOD_SECONDS,
                k * HIVE_PHASE_STEP, HangingHive.DEFAULT_CURTAIN_SECONDS, FUNGUS_FLOOR);
        Rectangle hero = new Rectangle(hives[0].centerX() - HERO_WIDTH / 2f, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        boolean[] lethal = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i == HIVE_KNOCK_FRAME) hives[1].knockDown();
            for (int k = 0; k < hives.length; k++) { hives[k].update(STEP); hives[k].render(batch); lethal[0] |= hives[k].lethalTo(hero); }
        });
        assertTrue(lethal[0], "the band must actually reach the hero under it in this scenario");
        assertTrue(hives[1].isKnocked(), "the knock-down must happen inside the scenario");
        assertWithinBudget("hanging hives", HANGING_HIVE_CEILING, bytes);
    }

    /**
     * Ember fissures (#366, D91): four fissures half a second apart cycle through the rest pulse, the warning with rising
     * sparks, the jet drawn a row at a time with its {@code lethalTo} against a hero standing over one crack every frame, the
     * falling sparks and the spent frame; update and render through the batch.
     */
    @Test void emberFissuresCycle() {
        EmberFissure[] fissures = new EmberFissure[FISSURE_COUNT];
        for (int k = 0; k < fissures.length; k++)
            fissures[k] = new EmberFissure(harness.assets(), k * FUNGUS_SPACING, FUNGUS_FLOOR - EmberFissure.FLOOR_OFFSET, harness.game().trapWarnings(),
                EmberFissure.DEFAULT_PERIOD_SECONDS, k * FISSURE_PHASE_STEP, EmberFissure.DEFAULT_JET_SECONDS, EmberFissure.DEFAULT_HEIGHT);
        Rectangle hero = new Rectangle(fissures[0].centerX() - HERO_WIDTH / 2f, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        boolean[] lethal = new boolean[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            for (EmberFissure f : fissures) { f.update(STEP); f.render(batch); lethal[0] |= f.lethalTo(hero); }
        });
        assertTrue(lethal[0], "the jet must actually reach the hero over the crack in this scenario");
        assertWithinBudget("ember fissures", EMBER_FISSURE_CEILING, bytes);
    }

    /**
     * Where {@link #emberFissuresCycle}'s bytes come from: the same four fissures against an empty frame (the probe, the lambda
     * and the harness, nothing of the trap), measured in alternating windows so a JIT change lands in one pair only. The median
     * difference is the trap's own cost per frame; with the empty frame alone also printed, the cycle's figure splits into the
     * probe's share and the trap's.
     */
    @Test void emberFissuresCostNothingOverAnEmptyFrame() {
        EmberFissure[] fissures = new EmberFissure[FISSURE_COUNT];
        for (int k = 0; k < fissures.length; k++)
            fissures[k] = new EmberFissure(harness.assets(), k * FUNGUS_SPACING, FUNGUS_FLOOR - EmberFissure.FLOOR_OFFSET, harness.game().trapWarnings(),
                EmberFissure.DEFAULT_PERIOD_SECONDS, k * FISSURE_PHASE_STEP, EmberFissure.DEFAULT_JET_SECONDS, EmberFissure.DEFAULT_HEIGHT);
        Rectangle hero = new Rectangle(fissures[0].centerX() - HERO_WIDTH / 2f, FUNGUS_FLOOR - HERO_HEIGHT, HERO_WIDTH, HERO_HEIGHT);
        boolean[] lethal = new boolean[1];
        double empty = AllocationProbe.bytesPerFrame(i -> lethal[0] |= hero.width < 0);
        double difference = AllocationProbe.medianPairedDifference(FISSURE_PAIRS, FISSURE_WINDOW_FRAMES, () -> { },
            i -> { for (EmberFissure f : fissures) { f.update(STEP); f.render(batch); lethal[0] |= f.lethalTo(hero); } },
            () -> { }, i -> lethal[0] |= hero.width < 0);
        System.out.printf("ALLOCATION ember fissures: empty frame %.1f B/frame; median fissures minus empty %.1f B/frame over %d pairs%n",
            empty, difference, FISSURE_PAIRS);
        assertTrue(lethal[0], "the jet must actually reach the hero over the crack in this scenario");
        assertWithinBudget("ember fissures (minus an empty frame)", EMBER_FISSURE_CEILING, difference);
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
    /** Enough endings for the warm-up and the measured window (25 s): one lasts about 7 s (today's ending lights with its third paragraph), or 3 s with instant text (#416). */
    static final int ENDING_COPIES = 12;
    /** Thorn roots at rest, warning, rising and up inside the measured window (#359, thornRootsRiseInTurn): measured 0.0 B/frame in 2 full-suite runs and 1.5 in 2 isolated runs (2026-11-17). Before #359: 64.0 B/frame (a new {@code Rectangle} per zone or column test). */
    static final double THORN_ROOT_CEILING = 8;
    /** Thorn spring cue and specks (#446, thornSpringThroughTheRealLevel): measured 0.0 B/frame in 3 isolated runs (2026-11-20): the cue and 3 specks every 60 frames, update and render. The same 8 B ceiling as the other isolated paths. */
    static final double THORN_SPRING_CEILING = 8;
    /** Deadfalls tripped in turn (#363, deadfallTripAndSwing): measured 0.0 B/frame in 3 isolated runs and 0.6 in the full :core:check (2026-11-17). The vine and log boxes are scratch rectangles and the frames are cached; the same 8 B ceiling as the other isolated trap paths. */
    static final double DEADFALL_CEILING = 8;
    /** A trip every 100 frames: 0.8 s of warning and 0.5 s of swing are 78 frames, then the catch. 16 cycles cover warm-up and measure. */
    private static final int DEADFALL_CYCLES = 16, DEADFALL_CYCLE_FRAMES = 100;
    private static final float DEADFALL_SPACING = 400;
    /** Hanging hives (#365, hangingHivesCycleAndOneIsKnockedDown): measured 0.1 to 0.9 B/frame in 3 isolated runs and 0.0 to 0.1 in 2 full-suite runs (2026-11-17); the same 8 B ceiling as the other isolated paths. */
    static final double HANGING_HIVE_CEILING = 8;
    /**
     * Ember fissures (#366, D91), 2026-11-17: emberFissuresCycle measured 1.1 B/frame in 2 isolated runs of it alone (a fresh JVM: about
     * 1.3 KB once over the 1200 measured frames, the first-use and JIT cost of the scene), 0.3 in 3 runs after the paired test had
     * warmed the same code, and 0.0 in the full check. The trap's own share is emberFissuresCostNothingOverAnEmptyFrame: the median of
     * 15 paired windows, fissures minus an empty frame, 0.0 B/frame in 3 isolated runs, the empty frame alone 0.0. The same 8 B
     * ceiling as the other isolated trap paths, for both tests.
     */
    static final double EMBER_FISSURE_CEILING = 8;
    private static final int FISSURE_COUNT = 4;
    /** Paired windows for emberFissuresCostNothingOverAnEmptyFrame: 15 pairs of 350 frames (one full 3.5 s cycle at 60 fps each, so every window holds a jet). */
    private static final int FISSURE_PAIRS = 15, FISSURE_WINDOW_FRAMES = 350;
    private static final float FISSURE_PHASE_STEP = 0.5f;
    /** Four hives a quarter period apart, 64 px up from the floor; the knock-down lands in the measured window (after the 300 warm-up frames). */
    private static final int HIVE_COUNT = 4, HIVE_KNOCK_FRAME = 900;
    private static final float HIVE_TOP = 232, HIVE_PHASE_STEP = 1f;
    /** The hero's left edge this far right of a thorn root's left edge: inside its zone, clear of the 6 px column (5 to 11). */
    private static final float THORN_BESIDE = 12;
    /** Measured 0.0 to 0.5 B/frame in 3 runs (#362, 2026-11-17): ten plain falling platforms at rest, the animation of the split plank (then the fan and the cracked slab), update and render; the same 8 B ceiling as the other isolated paths. */
    static final double FALLING_PLATFORM_REST_CEILING = 8;
    static final double EXPLOSIVE_IDLE_TELL_CEILING = 8, FALLING_STONE_CUE_CEILING = 8, STONE_FIREBALL_CEILING = 8, FAIRY_GUIDE_CEILING = 8, HUD_CEILING = 8, HUD_HINT_CEILING = 8,
        CHECKLIST_CEILING = 8, FPS_LABEL_CEILING = 8, GAMEPAD_CEILING = 8, MENU_BACKDROP_CEILING = 8;
    /**
     * #415: the arms beside the selected menu button, the selection stepping down the main menu's five rows; measured 0.0 B/frame
     * in 3 runs (2026-11-17). The same 8 B ceiling as the other isolated paths. This is the selection arms only: the rest of the menu
     * frame ({@code Overlay.buttons} labels, layouts, mouse copy) still allocates, tracked in #464.
     */
    static final double MENU_SELECTION_ARMS_CEILING = 8;
    private static final float THROW_X = 3328, THROW_Y = 300, THROW_VX = 220, THROW_VY = -110;
    private static final int STONE_ROOM = 4, THROW_CYCLES = 30;
    private static final float REACH_SCAN = 40, REACH_SCAN_STEP = 2;
    /** The throw's extra over the same room with no throw, B/frame (#505): pick up, throw, fly, first step on the release frame. Measured 3.5 B/frame (median of 9 paired windows, 100 cycles, all threw; 2026-11-20); 16 leaves headroom for JIT variation, as for the fireball cast. The 3.5 B includes the harness's own {@code snapshot()} record (the scene's proof that it threw); the cost inside {@code stepNewThrows} (an index loop and a flag test) is the same with or without a throw, so it cancels in the pair and is guarded by {@code HOT_METHODS}. */
    static final double STONE_THROW_EXTRA_CEILING = 16;
    private static final int STONE_THROW_PAIRS = 9, STONE_THROW_WINDOW_FRAMES = 600;
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
     * A real throw through {@code Level.update} (#505): the hero beside the stone picks it up (Up + Action) and throws it (the cast key) every
     * {@link #BURST_CYCLE_FRAMES} frames, so {@code Carryable.release}, {@code stepNewThrows} (the first step on the release frame) and the flight
     * run, against the same room with no input (a paired probe, as {@link #fireballCastWithTheRealKeyPress}). The scene proves it threw: every
     * cycle's throw is seen as a stone in flight after its release frame, with the hero's hands empty.
     */
    @Test void stoneThrowWithTheRealKeyPress() {
        Level throwing = harness.roomOf("forest-0", STONE_ROOM), quiet = harness.roomOf("forest-0", STONE_ROOM);
        Rectangle view = new Rectangle(throwing.currentRoomBounds());
        Carryable stone = throwing.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
        Carryable.Snapshot rest = stone.snapshot();
        for (float dy = -REACH_SCAN; dy <= REACH_SCAN && throwing.player.selectedCarryable() == null; dy += REACH_SCAN_STEP)
            throwing.player.position.set(stone.getX() - HERO_WIDTH, stone.getY() + dy);
        assertNotNull(throwing.player.selectedCarryable(), "the stone must be in reach in this scenario");
        float heroX = throwing.player.getX(), heroY = throwing.player.getY();
        int[] cycles = new int[1], thrownSeen = new int[1];
        int pickUp = 1, throwAt = 20;
        double extra = AllocationProbe.medianPairedDifference(STONE_THROW_PAIRS, STONE_THROW_WINDOW_FRAMES, () -> { },
            i -> {
                int phase = i % BURST_CYCLE_FRAMES;
                harness.pressed.clear(); harness.held.clear();
                if (phase == 0) {
                    throwing.player.position.set(heroX, heroY); throwing.player.velocity().setZero(); throwing.player.facingRight = true;
                    stone.restore(rest); cycles[0]++;
                }
                if (phase == pickUp) { harness.held.add(com.badlogic.gdx.Input.Keys.W); harness.held.add(com.badlogic.gdx.Input.Keys.K); harness.pressed.add(com.badlogic.gdx.Input.Keys.K); }
                if (phase == throwAt) { harness.held.add(com.badlogic.gdx.Input.Keys.L); harness.pressed.add(com.badlogic.gdx.Input.Keys.L); }
                throwing.update(STEP); throwing.render(batch, view);
                if (phase == throwAt && throwing.player.carriedObject() == null && stone.snapshot().thrown()) thrownSeen[0]++;
            },
            () -> { }, i -> { harness.pressed.clear(); harness.held.clear(); quiet.update(STEP); quiet.render(batch, view); });
        harness.pressed.clear(); harness.held.clear();
        System.out.printf("ALLOCATION stone throw: median throw minus no throw %.1f B/frame over %d pairs (%d cycles, %d threw)%n",
            extra, STONE_THROW_PAIRS, cycles[0], thrownSeen[0]);
        assertTrue(cycles[0] > 0, "the scene must run cycles");
        assertEquals(cycles[0], thrownSeen[0], "every cycle picked the stone up and threw it, in flight on its release frame");
        assertWithinBudget("stone throw over the same room with no throw", STONE_THROW_EXTRA_CEILING, extra);
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
        Level level = harness.roomOf("forest-1", 8, () -> view);
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

    /**
     * D87 update (#434): the route lever selected (Up held) with a view that puts both its mechanisms, the ladder and the lift, off screen, so the
     * fairy cycles between two edge points (dwell 0.8 s, flight, wrap) for the whole window; update and both render passes. The test counts
     * the hops inside the measured window. Ceiling: the shared isolated fairy-scene ceiling ({@link #FAIRY_GUIDE_CEILING}), as D87.
     */
    @Test void fairyGuideCyclingBetweenTwoOffScreenTargets() {
        Rectangle view = new Rectangle();
        Level level = harness.roomOf("forest-1", 8, () -> view);
        level.player.position.set(ROUTE_LEVER_X - HERO_CENTRE_OFFSET_X, ROUTE_FLOOR_Y);
        harness.held.add(com.badlogic.gdx.Input.Keys.W);
        view.set(0, 0, 640, 360); // far left of the room: the ladder and the lift are both off screen, to the right
        for (int i = 0; i < SELECT_FRAMES; i++) level.update(STEP);
        var fairy = level.fairy();
        com.jlogicsoftware.kimeria.entity.Entity[] last = {null};
        int[] hops = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            fairy.update(1f / 60f, level.player); fairy.renderBehind(batch); fairy.renderFront(batch);
            if (fairy.offscreenTarget() != last[0]) { last[0] = fairy.offscreenTarget(); hops[0]++; }
        });
        harness.held.clear();
        assertTrue(hops[0] >= 10, "she must hop between the two targets inside the measured window: " + hops[0]);
        assertWithinBudget("fairy guide cycling between two off-screen targets", FAIRY_GUIDE_CEILING, bytes);
    }

    /**
     * D87 update (#434): the post-pull cue with the ladder and the lift both off screen and nothing selected (Up not held), the pull renewed
     * every 150 frames: {@code sizeCue} (the copy into {@code cue}, the count) and the scans of the {@code cue} array run inside the window,
     * and she hops between the two points. Same ceiling as {@link #fairyGuideCyclingBetweenTwoOffScreenTargets}.
     */
    @Test void fairyGuidePostPullCueCyclingBetweenTwoOffScreenTargets() {
        Rectangle view = new Rectangle();
        Level level = harness.roomOf("forest-1", 8, () -> view);
        level.player.position.set(ROUTE_LEVER_X - HERO_CENTRE_OFFSET_X, ROUTE_FLOOR_Y);
        view.set(0, 0, 640, 360);
        for (int i = 0; i < SELECT_FRAMES; i++) level.update(STEP);
        var fairy = level.fairy();
        var trigger = level.triggers.get(0);
        com.jlogicsoftware.kimeria.entity.Entity[] last = {null};
        int[] hops = new int[1];
        double bytes = AllocationProbe.bytesPerFrame(i -> {
            if (i % 150 == 0) level.linkedMechanismsInto(trigger, fairy.beginChanged());
            fairy.update(1f / 60f, level.player); fairy.renderBehind(batch); fairy.renderFront(batch);
            if (fairy.offscreenTarget() != last[0]) { last[0] = fairy.offscreenTarget(); hops[0]++; }
        });
        assertTrue(hops[0] >= 20, "hops between the targets and the ends of the cue inside the window: " + hops[0]);
        assertWithinBudget("fairy guide post-pull cue cycling between two off-screen targets", FAIRY_GUIDE_CEILING, bytes);
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
        Level level = harness.roomOf("forest-2", 4);
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
        Level level = harness.roomOf("forest-1", 3);
        ArtifactInventory inventory = new ArtifactInventory();
        for (boolean instant : new boolean[]{false, true}) {
            Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
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
        Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
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
    /**
     * The ending's arms (#416, D95) frame after frame: the real English ending under the arms, dark, the sun lighting up,
     * lit, then the fade out, with instant text off and on. Endings are opened at load (outside the measurement) and played
     * one after another, each left with Escape once its sun is lit, so the lighting steps run many times in the window.
     * Measured 0.0 to 0.1 B/frame in 3 runs, instant text off and on (#416, 2026-11-17).
     */
    @Test void endingArms() {
        harness.font = new MetricsFont(FontSizes.BODY);
        String ending = com.jlogicsoftware.kimeria.localization.Catalog.parse("en",
            new com.badlogic.gdx.backends.headless.HeadlessFiles().internal("localization/en.json").readString("UTF-8")).get("ENDING_STORY");
        KimeriaArms arms = new KimeriaArms(harness.assets());
        for (boolean instant : new boolean[]{false, true}) {
            Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
            IntroScreen[] endings = new IntroScreen[ENDING_COPIES];
            for (int i = 0; i < endings.length; i++) endings[i] = IntroScreen.open(ending, overlay, harness.assets(), 640, instant, arms);
            // One whole ending first, outside the measurement, so every state change (lit, Escape, fade, done) has run once.
            IntroScreen first = IntroScreen.open(ending, overlay, harness.assets(), 640, instant, arms);
            while (!first.done()) {
                first.update(STEP);
                if (first.state() == IntroScreen.State.READING && first.armsFrame() == KimeriaArms.LIT) first.skip();
                overlay.beginFrame();
                first.render(batch, overlay);
                overlay.flushText(batch);
            }
            int[] at = new int[1], lit = new int[1];
            double bytes = AllocationProbe.bytesPerFrame(i -> {
                IntroScreen intro = endings[at[0]];
                if (intro.done() && at[0] < endings.length - 1) intro = endings[++at[0]];
                intro.update(STEP);
                if (intro.state() == IntroScreen.State.READING && intro.armsFrame() == KimeriaArms.LIT) { lit[0]++; intro.skip(); }
                overlay.beginFrame();
                intro.render(batch, overlay);
                overlay.flushText(batch);
            });
            assertTrue(lit[0] >= 3, "the sun must light several times during the measurement: " + lit[0]);
            assertTrue(!endings[at[0]].done(), "the last ending must still be running: raise ENDING_COPIES");
            System.out.printf("ALLOCATION ending arms, instant text %s: %.1f B/frame, %d endings lit%n", instant ? "on" : "off", bytes, lit[0]);
            assertWithinBudget("ending arms, instant text " + (instant ? "on" : "off"), PAGED_INTRO_CEILING, bytes);
        }
    }

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
            Overlay overlay = new Overlay(harness.assets(), 640, 360, null);
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

    /**
     * The selection arms (#415) as {@code Overlay.buttons} draws them every menu frame: both arms beside the selected button
     * of the main menu (220 px buttons from y 79, 48 px apart), the selection moving to the next row every frame. The selection
     * arms: 0.0 B/frame; the rest of the menu frame: #464.
     */
    @Test void menuSelectionArms() {
        harness.font = new MetricsFont(FontSizes.BUTTON);
        Overlay overlay = new Overlay(harness.assets(), 640, 360, new KimeriaArms(harness.assets()));
        double bytes = AllocationProbe.bytesPerFrame(i ->
            overlay.drawSelectionArms(batch, 210f, 79f + (i % 5) * (Overlay.BUTTON_H + Overlay.BUTTON_GAP), Overlay.BUTTON_W));
        assertWithinBudget("menu selection arms", MENU_SELECTION_ARMS_CEILING, bytes);
    }
}
