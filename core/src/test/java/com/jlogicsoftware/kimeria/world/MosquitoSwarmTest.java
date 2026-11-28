package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.jlogicsoftware.kimeria.testsupport.HeroBody;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.MosquitoSwarm;
import com.jlogicsoftware.kimeria.entity.enemy.Remains;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest mosquito swarm (#645, D151), driven headlessly through real Level updates with ordinary keyboard controls.
 *
 * <p>Fixtures: a room 640 wide with its floor at y 320, the hero entering at x 32, a stone at x 96 and one swarm whose 22x18
 * box is authored at (309, 298), 4 to 22 px over the floor. mosquito-test flies x 245..373, mosquito-run x 117..501,
 * mosquito-still holds its place, mosquito-high holds it 30 px up (y 272), mosquito-vertical flies y 250..298 (its bottom
 * 4 to 52 px up), mosquito-entrance and mosquito-stone (mosquito-test under a falling stone) each break a load rule.
 */
class MosquitoSwarmTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, SPAWN_X = 309, SPAWN_Y = 298, LEFT_END = 245, RIGHT_END = 373;
    /** The hero's body middle from its entity position, the entity's feet below it, half the body, and the sword's reach. */
    private static final float HERO_MIDDLE = HeroBody.CENTRE, HERO_FEET = 48, HERO_HALF = HeroBody.HALF, SWORD_REACH = HeroBody.HALF + 22;
    private static final float STONE_X = 96, TORCH_X = 150, CARRY_SIZE = 12;
    private static final int TORCH_ID = 9001;
    /** The sheet's frames of a dead and a burning mosquito. */
    private static final int DEAD_FRAME = 4, EMBER_FRAME = 5;
    /** The lethal core's box, gathered. */
    private static final float CORE_WIDTH = 18, CORE_HEIGHT = 14;
    /** The core's rim sampled at this many points, every {@code RIM_EVERY} frames of a minute; the bounds on the gaps. */
    private static final int RIM_POINTS = 60, RIM_FRAMES = 60 * 60, RIM_EVERY = 3;
    private static final float RIM_MEDIAN = 2f, RIM_MOST = 4.5f;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level level(String map) { return harness.roomOf(map, 0); }

    private static MosquitoSwarm swarm(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof MosquitoSwarm s) return s;
        return null;
    }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }
    private void press(int key) { harness.held.add(key); harness.pressed.add(key); }
    private void tap(Level level, int key) { press(key); frame(level); harness.held.remove(key); }

    /** Stands the hero (body middle at {@code middle}) on the floor, still, facing right. */
    private void place(Level level, float middle) {
        level.player.position.set(middle - HERO_MIDDLE, FLOOR - HERO_FEET);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        harness.held.clear();
    }

    /** Holds the hero in the air with its body's middle and feet there, for one frame. */
    private void hover(Level level, float middle, float feet) {
        level.player.position.set(middle - HERO_MIDDLE, feet - HERO_FEET);
        level.player.velocity().setZero();
        frame(level);
    }

    private int until(Level level, BooleanSupplier condition, int limit) {
        for (int f = 0; f < limit; f++) {
            if (condition.getAsBoolean()) return f;
            frame(level);
        }
        fail("condition never held in " + limit + " frames");
        return -1;
    }

    @Test void fliesToAndFroAsTheBatDoesAndNeverReactsToTheHero() {
        Level level = level("mosquito-test");
        MosquitoSwarm s = swarm(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE, before = s.position.x;
        for (int f = 0; f < 60 * 12; f++) {
            if (f > 60 * 6) place(level, 200);   // a hero near its line changes nothing
            frame(level);
            float moved = Math.abs(s.position.x - before);
            before = s.position.x;
            assertTrue(moved <= MosquitoSwarm.SPEED * STEP + 1e-3f, "50 px/s: " + moved);
            min = Math.min(min, s.position.x); max = Math.max(max, s.position.x);
            assertEquals(SPAWN_Y, s.position.y, 0, "level flight");
            assertEquals(MosquitoSwarm.State.FLY, s.state());
        }
        assertEquals(LEFT_END, min, .01f, "reaches its left end");
        assertEquals(RIGHT_END, max, .01f, "reaches its right end");
        assertTrue(alive(level), "a hero off its line is safe");
        assertTrue(harness.sounds.isEmpty(), "it has no notice and no call");
    }

    @Test void fliesUpAndDownOnAVerticalLine() {
        Level level = level("mosquito-vertical");
        MosquitoSwarm s = swarm(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int f = 0; f < 60 * 5; f++) {
            frame(level);
            min = Math.min(min, s.position.y); max = Math.max(max, s.position.y);
            assertEquals(SPAWN_X, s.position.x, 0);
        }
        assertEquals(250, min, .01f);
        assertEquals(SPAWN_Y, max, .01f);
    }

    @Test void everyRetryDrawsTheSameFlight() {
        Level first = level("mosquito-test"), second = level("mosquito-test");
        for (int f = 0; f < 240; f++) {
            frame(first); frame(second);
            for (int i = 0; i < MosquitoSwarm.COUNT; i++) {
                assertEquals(swarm(first).mosquitoX(i), swarm(second).mosquitoX(i), 0);
                assertEquals(swarm(first).mosquitoY(i), swarm(second).mosquitoY(i), 0);
            }
        }
    }

    /**
     * D10: every drawn pixel lies in the cloud, the oval {@code CORE_INSET} px round the lethal core, gathered over two minutes of
     * flight and while swings from either side scatter it; the mosquitoes wander past the core, so it has a harmless fringe.
     */
    @Test void whatIsDrawnStaysInTheCloudRoundTheCore() {
        Pixmap sheet = new Pixmap(Gdx.files.internal("images/objects/monster-mosquito.png"));
        try {
            for (int hero : new int[]{291, 349}) {   // a swing from the left blows it right, from the right blows it left
                Level level = level("mosquito-still");
                MosquitoSwarm s = swarm(level);
                Rectangle core = new Rectangle();
                int fringe = 0;
                for (int f = 0; f < 60 * 120; f++) {
                    if (f == 3590) place(level, hero);
                    if (f >= 3600 && (f - 3600) % 97 == 0) { level.player.facingRight = hero < SPAWN_X; press(Input.Keys.K); }
                    frame(level);
                    harness.held.clear();
                    s.shapeInto(core);
                    float rx = core.width / 2 + MosquitoSwarm.CORE_INSET, ry = core.height / 2 + MosquitoSwarm.CORE_INSET;
                    float cx = core.x + core.width / 2, cy = core.y + core.height / 2;
                    for (int i = 0; i < MosquitoSwarm.COUNT; i++) {
                        for (int px = 0; px < MosquitoSwarm.MOSQUITO_WIDTH; px++) {
                            int column = (i & 1) == 0 ? px : MosquitoSwarm.MOSQUITO_WIDTH - 1 - px;
                            if ((sheet.getPixel(s.frameIndex(i) * MosquitoSwarm.MOSQUITO_WIDTH + column, 0) & 0xff) == 0) continue;
                            float x = s.mosquitoX(i) + px + .5f, y = s.mosquitoY(i) + .5f;
                            float dx = (x - cx) / rx, dy = (y - cy) / ry;
                            assertTrue(dx * dx + dy * dy <= 1, "frame " + f + " mosquito " + i + ": " + (dx * dx + dy * dy));
                            float inX = (x - cx) / (core.width / 2), inY = (y - cy) / (core.height / 2);
                            if (inX * inX + inY * inY > 1) fringe++;
                        }
                    }
                }
                assertTrue(fringe > 0, "strays fly outside the lethal core");
            }
        } finally {
            sheet.dispose();
        }
    }

    /**
     * D5, D10 the other way round, as the author accepted it with the free flight (2026-11-27): the rim of the lethal core is
     * usually near a drawn pixel. Over a minute of the gathered cloud, from points all round the rim, the median distance to the
     * nearest pixel and the distance 95 in 100 stay within bounds.
     */
    @Test void theRimOfTheCoreIsRarelyFarFromAMosquito() {
        Pixmap sheet = new Pixmap(Gdx.files.internal("images/objects/monster-mosquito.png"));
        try {
            Level level = level("mosquito-still");
            MosquitoSwarm s = swarm(level);
            Rectangle core = new Rectangle();
            float[] gaps = new float[RIM_FRAMES / RIM_EVERY * RIM_POINTS];
            int n = 0;
            for (int f = 0; f < RIM_FRAMES; f++) {
                frame(level);
                if (f % RIM_EVERY != 0) continue;
                s.shapeInto(core);
                for (int k = 0; k < RIM_POINTS; k++) {
                    double angle = 2 * Math.PI * k / RIM_POINTS;
                    float ex = core.x + core.width * (.5f + .5f * (float) Math.cos(angle));
                    float ey = core.y + core.height * (.5f + .5f * (float) Math.sin(angle));
                    gaps[n++] = nearestDrawn(sheet, s, ex, ey);
                }
            }
            java.util.Arrays.sort(gaps);
            float median = gaps[n / 2], most = gaps[n * 95 / 100];
            System.out.printf(java.util.Locale.ROOT, "SWARM core rim to the nearest mosquito: median %.2f, 95%% %.2f, widest %.2f px%n",
                median, most, gaps[n - 1]);
            assertTrue(median <= RIM_MEDIAN, "typical gap " + median + " px");
            assertTrue(most <= RIM_MOST, "95 in 100 gaps within " + most + " px");
        } finally {
            sheet.dispose();
        }
    }

    /** From a point to the nearest opaque pixel (its square) of any mosquito, as drawn: odd ones mirrored. */
    private static float nearestDrawn(Pixmap sheet, MosquitoSwarm s, float x, float y) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < MosquitoSwarm.COUNT; i++) {
            for (int px = 0; px < MosquitoSwarm.MOSQUITO_WIDTH; px++) {
                int column = (i & 1) == 0 ? px : MosquitoSwarm.MOSQUITO_WIDTH - 1 - px;
                if ((sheet.getPixel(s.frameIndex(i) * MosquitoSwarm.MOSQUITO_WIDTH + column, 0) & 0xff) == 0) continue;
                float left = s.mosquitoX(i) + px, top = s.mosquitoY(i);
                float dx = Math.max(Math.max(left - x, 0), x - left - 1), dy = Math.max(Math.max(top - y, 0), y - top - 1);
                best = Math.min(best, (float) Math.hypot(dx, dy));
            }
        }
        return best;
    }

    @Test void walkingIntoItKillsOnTheSwarmsNextFrame() {
        Level level = level("mosquito-still");
        place(level, 280);
        harness.held.add(Input.Keys.D);
        int death = until(level, () -> !alive(level), 60);
        assertTrue(death > 0);
        // The frame before the death the hero already overlapped it: the touch waits one frame for a hit to beat it.
        assertTrue(swarm(level).hitboxOverlaps(level.player.hitboxBounds()));
        assertFalse(swarm(level).isDefeated(), "contact never harms the swarm");
    }

    @Test void theFringeOfTheCloudIsSafeAndTheCoreIsNot() {
        Level level = level("mosquito-still");
        for (int f = 0; f < 10; f++) hover(level, 311 - HERO_HALF, 312);
        assertTrue(alive(level), "a body in the 2 px fringe, up to the core's tip");
        for (int f = 0; f < 3; f++) hover(level, 313 - HERO_HALF, 312);
        assertFalse(alive(level), "inside the core");
    }

    @Test void landingOnItKillsTheHeroAndNotTheSwarm() {
        Level level = level("mosquito-still");
        MosquitoSwarm s = swarm(level);
        level.player.position.set(SPAWN_X + 11 - HERO_MIDDLE, SPAWN_Y - HERO_FEET - 6);
        level.player.velocity().setZero();
        until(level, () -> !alive(level), 40);
        assertTrue(level.player.velocity.y >= 0, "no stomp bounce");
        assertFalse(s.isDefeated());
        assertFalse(harness.sounds.contains("bounce"));
    }

    /** The hero stands with the blade just reaching the still swarm's near edge. */
    private MosquitoSwarm swingAt(Level level, boolean fromLeft) {
        float middle = fromLeft ? SPAWN_X - SWORD_REACH + 5 : SPAWN_X + MosquitoSwarm.WIDTH + SWORD_REACH - 5;
        place(level, middle);
        level.player.facingRight = fromLeft;
        settle(level, 3);
        level.player.facingRight = fromLeft;
        tap(level, Input.Keys.K);
        return swarm(level);
    }

    @Test void aSwingScattersItAwayFromTheHeroAndItGathersAgain() {
        for (boolean fromLeft : new boolean[]{true, false}) {
            harness.sounds.clear();
            Level level = level("mosquito-still");
            MosquitoSwarm s = swingAt(level, fromLeft);
            Rectangle shape = new Rectangle();
            float widest = 0;
            int apart = 0;
            for (int f = 0; f < 60; f++) {
                s.shapeInto(shape);
                widest = Math.max(widest, shape.width);
                if (fromLeft) assertEquals(SPAWN_X + MosquitoSwarm.CORE_INSET, shape.x, .001f, "the near edge stays");
                else assertEquals(SPAWN_X + MosquitoSwarm.WIDTH - MosquitoSwarm.CORE_INSET, shape.x + shape.width, .001f, "the near edge stays");
                assertEquals(CORE_HEIGHT, shape.height, 0, "no taller");
                if (s.scatterAmount() > 0) apart++;
                frame(level);
            }
            assertEquals(CORE_WIDTH + MosquitoSwarm.SCATTER_REACH, widest, .001f, "12 px further on the far side");
            assertEquals(MosquitoSwarm.SCATTER_SECONDS, apart * STEP, STEP * 1.5f, "gathered after 0.7 s");
            assertEquals(CORE_WIDTH, s.shapeInto(shape).width, 0);
            assertFalse(s.isDefeated(), "the sword never kills it");
            assertTrue(alive(level), "the swing itself does not push it into the hero");
            assertEquals(List.of("mosquito_scatter"), harness.sounds, "once per swing");
            assertEquals(0, level.bloodParticles().count(), "no blood");
        }
    }

    @Test void everySwingScattersItOnce() {
        Level level = level("mosquito-still");
        swingAt(level, true);
        settle(level, 60);
        tap(level, Input.Keys.K);
        settle(level, 60);
        assertEquals(List.of("mosquito_scatter", "mosquito_scatter"), harness.sounds);
    }

    @Test void theBladeGoesOnThroughItToWhatIsBehind() {
        Level level = level("mosquito-still");
        // A bat just behind the swarm, listed after it, inside the same swing's box.
        Bat bat = new Bat(harness.assets(), harness.game(), SPAWN_X, SPAWN_Y, 16, 16, false, 0, 0);
        bat.setPlayer(level.player);
        level.objects.add(bat);
        swingAt(level, true);
        assertTrue(bat.isDefeated(), "the swarm does not shield it");
        assertFalse(swarm(level).isDefeated());
    }

    /** The list order is the map's: a monster listed first, which stops the blade, leaves the swarm its scatter on that frame. */
    @Test void aMonsterListedFirstDoesNotKeepTheBladeOffTheSwarm() {
        Level level = level("mosquito-still");
        Bat bat = new Bat(harness.assets(), harness.game(), SPAWN_X, SPAWN_Y, 16, 16, false, 0, 0);
        bat.setPlayer(level.player);
        level.objects.add(0, bat);
        swingAt(level, true);
        assertTrue(bat.isDefeated(), "the blade still reaches the bat");
        assertTrue(harness.sounds.contains("mosquito_scatter"), "scattered on the frame of the blow: " + harness.sounds);
    }

    /** A dead swarm still falling in the blade's reach must not undo D101: the swing that kills a monster leaves its body. */
    @Test void aFallingDeadSwarmDoesNotMakeTheSwingCrumbleTheBodyItMade() {
        Level level = level("mosquito-still");
        Snail snail = new Snail(harness.assets(), harness.game(), SPAWN_X, FLOOR - 16, 32, 16, 0, 0);
        snail.setPlayer(level.player);
        snail.setLevel(level);
        level.objects.add(0, snail);
        swarm(level).collidedWithActor(true);
        swingAt(level, true);
        assertTrue(snail.isDefeated(), "the blade killed it");
        settle(level, 20);
        assertTrue(snail.isRemains(), "its body is left by the swing that made it");
    }

    /** The card: swinging at an oncoming swarm and standing, the hero dies about 0.68 s after the blade first reaches it. */
    @Test void swingingAtAnOncomingSwarmAndStandingKills() {
        Level level = level("mosquito-test");
        MosquitoSwarm s = swarm(level);
        until(level, () -> s.position.x >= RIGHT_END - .01f, 60 * 5);
        float middle = 245;
        place(level, middle);
        settle(level, 3);
        until(level, () -> s.position.x + MosquitoSwarm.CORE_INSET <= middle + SWORD_REACH + .5f, 60 * 6);
        tap(level, Input.Keys.K);
        until(level, () -> harness.sounds.contains("mosquito_scatter"), 3);   // the blade reaches it
        int frames = until(level, () -> !alive(level), 60);
        assertEquals(.88f, frames * STEP, .06f);   // D164: the blade reaches 22 px ahead (0.68 s with 17)
        assertFalse(s.isDefeated());
    }

    /** Dead at once and harmless; its mosquitoes drop, burning first when fire took them, fade, and are gone within 0.8 s. */
    private void assertKilled(Level level, MosquitoSwarm s, boolean fire) {
        assertTrue(s.isDefeated());
        assertFalse(s instanceof Remains, "nothing to crumble or burn (D101)");
        assertTrue(harness.sounds.contains("mosquito_die"));
        assertFalse(harness.sounds.contains("bounce"), "its own sound");
        assertEquals(fire ? EMBER_FRAME : DEAD_FRAME, s.frameIndex(0));
        float[] start = new float[MosquitoSwarm.COUNT];
        for (int i = 0; i < start.length; i++) start[i] = s.mosquitoY(i);
        until(level, () -> s.deadSeconds() >= MosquitoSwarm.EMBER_SECONDS, 60);
        assertEquals(DEAD_FRAME, s.frameIndex(0));
        settle(level, 20);
        assertTrue(s.opacity() < 1, "fading");
        for (int i = 0; i < start.length; i++) {
            assertTrue(s.mosquitoY(i) >= start[i], "mosquito " + i + " drops");
            assertTrue(s.mosquitoY(i) + MosquitoSwarm.MOSQUITO_HEIGHT <= FLOOR, "never under the floor");
        }
        until(level, s::isRemoved, 60);
        assertNull(swarm(level), "no body, nothing stays");
    }

    @Test void aStandingFireballKillsTheWholeSwarmAndIsSpent() {
        Level level = level("mosquito-still");
        MosquitoSwarm s = swarm(level);
        place(level, 200);
        settle(level, 3);
        int charges = level.rangedStock.count();
        tap(level, Input.Keys.L);
        until(level, s::isDefeated, 60);
        assertEquals(charges - 1, level.rangedStock.count());
        assertKilled(level, s, true);
        frame(level);
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.Fireball), "spent");
        assertTrue(alive(level));
    }

    private void pickUp(Level level, float objectX) {
        place(level, objectX + CARRY_SIZE - HeroBody.RIGHT + HERO_MIDDLE);
        frame(level);
        press(Input.Keys.W); frame(level); // Up alone interacts (D126)
        harness.held.clear(); frame(level);
        assertNotNull(level.player.carriedObject(), "picked it up");
    }

    @Test void aStandingThrownStoneKillsItStopsAndCanBePickedUpAgain() {
        Level level = level("mosquito-still");
        MosquitoSwarm s = swarm(level);
        pickUp(level, STONE_X);
        Carryable stone = level.player.carriedObject();
        place(level, 250);
        settle(level, 3);
        tap(level, Input.Keys.L);
        until(level, s::isDefeated, 60);
        assertKilled(level, s, false);
        settle(level, 60);
        assertFalse(stone.isRemoved());
        assertTrue(stone.available(), "it lies there");
        assertTrue(stone.getX() < SPAWN_X + MosquitoSwarm.WIDTH, "it stopped there and did not fly on: " + stone.getX());
    }

    @Test void aThrownTorchKillsItAsFireDoes() {
        Level level = level("mosquito-still");
        MosquitoSwarm s = swarm(level);
        level.objects.add(new Carryable(level, TORCH_ID, Carryable.Kind.TORCH, TORCH_X, FLOOR - CARRY_SIZE));
        pickUp(level, TORCH_X);
        place(level, 250);
        settle(level, 3);
        tap(level, Input.Keys.L);
        until(level, s::isDefeated, 60);
        assertKilled(level, s, true);
    }

    /** Frames of walking from {@code start} until the death, so the touch happens on the frame before. */
    private int framesToDeath(float start, boolean carrying) {
        Level level = level("mosquito-still");
        if (carrying) pickUp(level, STONE_X);
        place(level, start);
        settle(level, 3);
        harness.held.add(Input.Keys.D);
        int frames = until(level, () -> !alive(level), 90);
        harness.held.clear();
        return frames;
    }

    /** #503, #505: a ball cast, or a stone thrown, on the frame of the touch beats it; a frame later is too late. */
    @Test void aHitOnTheFrameOfTheTouchWins() {
        for (boolean stone : new boolean[]{false, true}) {
            int death = framesToDeath(280, stone);
            for (int late : new int[]{0, 1}) {
                Level level = level("mosquito-still");
                MosquitoSwarm s = swarm(level);
                if (stone) pickUp(level, STONE_X);
                place(level, 280);
                settle(level, 3);
                harness.held.add(Input.Keys.D);
                settle(level, death - 2 + late);
                assertTrue(alive(level));
                tap(level, Input.Keys.L);
                settle(level, 5);
                harness.held.clear();
                assertEquals(late == 0, alive(level), (stone ? "stone" : "fireball") + ", " + late + " frame late");
                if (late == 0) assertTrue(s.isDefeated());
            }
        }
    }

    /**
     * Take-off distances (hero middle to swarm middle, whole px) from which a jump clears the low swarm, with the swarm
     * coming ({@code oncoming}) or flying away, the hero {@code running} at it or jumping on the spot.
     */
    private List<Integer> clearingTakeOffs(boolean oncoming, boolean running) {
        List<Integer> clear = new ArrayList<>();
        for (int distance = 17; distance <= 130; distance++) {
            Level level = level("mosquito-run");
            MosquitoSwarm s = swarm(level);
            if (oncoming) {
                until(level, () -> s.position.x >= 501 - .01f, 60 * 16);
                settle(level, 180);              // flying left from x 426
            } else {
                settle(level, 60);               // flying right from x 334
            }
            place(level, s.position.x + 11 - distance);
            if (s.hitboxOverlaps(level.player.hitboxBounds())) continue;
            if (running) harness.held.add(Input.Keys.D);
            press(Input.Keys.J);
            boolean passed = false;
            // One jump (0.88 s in the air): it must end with the swarm wholly behind him.
            for (int f = 0; f < 54 && alive(level); f++) {
                frame(level);
                passed = level.player.hitboxBounds().x > s.position.x + MosquitoSwarm.WIDTH;
            }
            harness.held.clear();
            if (alive(level) && passed) clear.add(distance);
        }
        return clear;
    }

    @Test void aRunningJumpClearsAnOncomingSwarmOverAWideWindow() {
        List<Integer> clear = clearingTakeOffs(true, true);
        assertEquals(.60f, clear.size() / (Player.MOVE_SPEED + MosquitoSwarm.SPEED), .02f, "take-offs " + clear);
    }

    @Test void jumpingAfterASwarmIsNarrowerAndFromStandingItIsNotCleared() {
        List<Integer> after = clearingTakeOffs(false, true), standing = clearingTakeOffs(true, false);
        assertEquals(.51f, after.size() / (Player.MOVE_SPEED - MosquitoSwarm.SPEED), .03f, "after it: " + after);
        assertTrue(standing.isEmpty(), "at half the speed a jump on the spot never lets it pass under: " + standing);
    }

    @Test void aSwarm30PxUpPassesOverAStandingHero() {
        Level level = level("mosquito-high");
        place(level, 270);
        harness.held.add(Input.Keys.D);
        settle(level, 60);
        assertTrue(level.player.hitboxBounds().x > SPAWN_X + MosquitoSwarm.WIDTH, "walked under");
        assertTrue(alive(level));
    }

    @Test void aHeroWalksUnderAVerticalSwarmWhileItIsHigh() {
        Level level = level("mosquito-vertical");
        MosquitoSwarm s = swarm(level);
        place(level, 285);
        until(level, () -> s.position.y <= 270, 60 * 4);   // rising, its bottom 32 px up
        harness.held.add(Input.Keys.D);
        settle(level, 45);
        harness.held.clear();
        assertTrue(level.player.hitboxBounds().x > SPAWN_X + MosquitoSwarm.WIDTH);
        assertTrue(alive(level));
    }

    @Test void aRevisitBringsASurvivorBackWholeAtItsSpawn() {
        Level level = level("mosquito-test");
        MosquitoSwarm s = swingAt(level, true);
        settle(level, 80);
        s.revisit(level.player);
        assertEquals(SPAWN_X, s.position.x, 0);
        assertEquals(SPAWN_Y, s.position.y, 0);
        assertEquals(0, s.scatterAmount(), 0);
        assertFalse(s.isDefeated());
    }

    @Test void aRetryBringsAKilledSwarmBack() {
        Level level = level("mosquito-still");
        swarm(level).collidedWithActor(true);
        settle(level, 60);
        assertNull(swarm(level));
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        assertNotNull(swarm(level));
        assertEquals(MosquitoSwarm.State.FLY, swarm(level).state());
    }

    /** D151: no sound of its own while it flies, however long it is watched; only the evasion and the death are heard. */
    @Test void itFliesWithoutAnyLoopOrSound() {
        Rectangle view = new Rectangle(0, 0, 640, 360);
        Level level = harness.roomOf("mosquito-test", 0, () -> view);
        settle(level, 60 * 6);
        assertTrue(harness.loops.isEmpty(), harness.loops.toString());
        assertTrue(harness.sounds.isEmpty(), harness.sounds.toString());
    }

    @Test void withTheSoundSwitchOffTheEvasionAndTheDeathAreSilent() {
        harness.playSounds = false;
        Level level = level("mosquito-still");
        swingAt(level, true);
        settle(level, 60);
        swarm(level).collidedWithActor(true);
        settle(level, 5);
        assertTrue(harness.sounds.isEmpty() && harness.loops.isEmpty(), harness.sounds + " " + harness.loops);
    }

    // -- Load checks (D151, D26) -------------------------------------------------------------------------------------------

    private static final Rectangle ROOM = new Rectangle(0, 0, 640, 368), GROUND = new Rectangle(0, 320, 640, 48);

    private static String problem(float x, float y, boolean vertical, float neg, float pos, Rectangle... extra) {
        List<Rectangle> floors = new ArrayList<>(List.of(GROUND));
        floors.addAll(List.of(extra));
        return MosquitoSwarm.problem(x, y, 22, 18, vertical, neg, pos, floors, floors, ROOM, 8,
            Map.of("entrance", new Rectangle(50, 292, HeroBody.W, HeroBody.H)));
    }

    @Test void theLoadChecksAcceptALowALevelAndAHighAndAVerticalLine() {
        assertNull(problem(309, 298, false, 4, 4), "low: over it");
        assertNull(problem(309, 302, false, 4, 4), "on the floor line");
        assertNull(problem(309, 272, false, 4, 4), "30 px up: under it");
        assertNull(problem(309, 270, false, 4, 4));
        assertNull(problem(309, 298, true, 3, 0), "bottom 4 to 52 px up");
        assertNull(problem(309, 272, true, 1, 0), "always high enough");
    }

    @Test void aLineWithNoFreeWayPastFails() {
        String between = problem(309, 284, false, 4, 4);          // bottom 18, top 36
        assertNotNull(between);
        assertTrue(between.contains("no way past"), between);
        Rectangle ceiling = new Rectangle(200, 220, 240, 24);      // 76 px over the floor: no room to jump, no way under
        assertTrue(problem(309, 298, false, 4, 4, ceiling).contains("no way past"));
        assertNull(problem(309, 270, false, 4, 4, ceiling), "the way under needs no headroom");
        // A vertical swarm that is high for too short a time each cycle: bottom 24 to 34 px up, 4 px of it 30 or more.
        String brief = problem(309, 268, true, 0, .625f);
        assertNotNull(brief);
        assertTrue(brief.contains("no way past open"), brief);
    }

    @Test void theOtherLoadRules() {
        assertTrue(problem(309, 298, false, -1, 0).contains("offNeg"));
        assertTrue(problem(20, 298, false, 4, 0).contains("leaving the room"));
        assertTrue(problem(309, 298, false, 0, 24).contains("leaving the room"), "the scatter's reach counts");
        assertTrue(problem(309, 4, false, 0, 0).contains("camera"));
        assertTrue(problem(309, 298, false, 4, 4, new Rectangle(390, 290, 16, 30)).contains("solid"), "a post in the reach");
        assertTrue(problem(72, 298, false, 0, 4).contains("entrance"), "12 px of scatter from the entrance");
        List<Rectangle> broken = List.of(new Rectangle(0, 320, 300, 48), new Rectangle(340, 320, 300, 48));
        String pit = MosquitoSwarm.problem(309, 298, 22, 18, false, 4, 4, broken, broken, ROOM, 8, Map.of());
        assertTrue(pit.contains("floor"), pit);
    }

    @Test void theLevelRunsTheLoadChecks() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> level("mosquito-entrance"));
        assertTrue(error.getMessage().contains("MosquitoSwarm"), error.getMessage());
        assertTrue(error.getMessage().contains("entrance"), error.getMessage());
    }

    /** The stone of mosquito-stone hangs at the room top, clear of the line, and falls through it: the whole column counts. */
    @Test void theLevelRejectsAFallingStoneOverTheLine() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> level("mosquito-stone"));
        assertTrue(error.getMessage().contains("MosquitoSwarm"), error.getMessage());
        assertTrue(error.getMessage().contains("moving on its line"), error.getMessage());
    }
}
