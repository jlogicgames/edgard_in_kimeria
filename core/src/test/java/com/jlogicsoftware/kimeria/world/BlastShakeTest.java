package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.testsupport.HeroBody;
import com.jlogicsoftware.kimeria.effects.ScreenShake;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The blast's shake (#448, D156), through real {@code Level} updates: the stump, the root and the bomb jolt the camera
 * when they go off and the hero survives, at any distance; a blast that kills him starts none. The beetles' bursts follow
 * the same rule ({@code BeetleTest}, {@code FlyingBeetleTest}).
 *
 * <p>explosive-test.tmx: hero enters at x 32, stump at x 288 (blast square x 264..328), root at x 480 (456..520).
 * bomb-test.tmx: one bomb at x 656 on the floor, hero enters at x 32.
 */
class BlastShakeTest {
    private static final float STEP = HeadlessLevelHarness.STEP, FLOOR = 320;
    private static final float STUMP_X = 288, ROOT_X = 480, BOMB_X = 656;
    /** The hero's body right edge: at the entrance, far from every blast, or this far left of the object, just clear of its square. */
    private static final float FAR_RIGHT_EDGE = 61, NEAR_LEFT_OF_OBJECT = 38;
    private static final int WARNING_LIMIT = 60 * 3;
    private static final Rectangle ROOM = new Rectangle(0, 0, 4000, 4000), VIEW = new Rectangle(100, 100, 640, 360);

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }

    /** Stands the hero (body right edge at {@code right}) on the floor, still. */
    private void place(Level level, float right) {
        level.player.position.set(right - HeroBody.RIGHT, FLOOR - 48);
        level.player.velocity().setZero();
        harness.held.clear();
    }

    private static ExplosiveTrap trap(Level level, ExplosiveTrap.Kind kind) {
        return level.objects.stream().filter(ExplosiveTrap.class::isInstance).map(ExplosiveTrap.class::cast)
            .filter(t -> t.kind() == kind).findFirst().orElseThrow();
    }

    private static Bomb bomb(Level level) {
        return level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
    }

    /** Runs the wind-up, with no shake while it lasts, and returns after the frame it went off. */
    private void detonate(Level level, ExplosiveTrap trap) {
        for (int f = 0; f < WARNING_LIMIT && trap.phase() == ExplosiveTrap.Phase.WINDUP; f++) {
            assertFalse(level.fx().shake().running(), "the warning does not shake");
            frame(level);
        }
        assertEquals(ExplosiveTrap.Phase.BLAST, trap.phase());
    }

    private void detonate(Level level, Bomb bomb) {
        for (int f = 0; f < WARNING_LIMIT && !bomb.isRemoved(); f++) {
            assertFalse(level.fx().shake().running(), "the warning does not shake");
            frame(level);
        }
        assertTrue(bomb.isRemoved(), "it went off");
    }

    private static void assertBlastShake(Level level) {
        assertFalse(level.player.isGotHit(), "the hero survived");
        assertTrue(level.fx().shake().running(), "a survived blast shakes");
        assertEquals(ScreenShake.BLAST_AMPLITUDE_PX, level.fx().shake().currentAmplitude(), 1e-4f, "full on the detonation frame");
    }

    @Test void theBlastNumbersAreTheAuthorsAndTheHeaviestJoltInTheGame() {
        assertEquals(5f, ScreenShake.BLAST_AMPLITUDE_PX, 0);
        assertEquals(.35f, ScreenShake.BLAST_SECONDS, 0);
        assertEquals(3.5f, ScreenShake.BURST_AMPLITUDE_PX, 0);
        assertEquals(.25f, ScreenShake.BURST_SECONDS, 0);
        for (float other : new float[]{ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.LEVER_AMPLITUDE_PX,
            ScreenShake.STONE_AMPLITUDE_PX, ScreenShake.BURST_AMPLITUDE_PX})
            assertTrue(other < ScreenShake.BLAST_AMPLITUDE_PX);
        for (float lighter : new float[]{ScreenShake.BOSS_AMPLITUDE_PX, ScreenShake.LEVER_AMPLITUDE_PX, ScreenShake.STONE_AMPLITUDE_PX})
            assertTrue(lighter < ScreenShake.BURST_AMPLITUDE_PX, "a beetle's burst is the second heaviest");
    }

    @Test void theStumpAndTheRootShakeForASurvivorNearAndFar() {
        for (ExplosiveTrap.Kind kind : ExplosiveTrap.Kind.values()) {
            float x = kind == ExplosiveTrap.Kind.STUMP ? STUMP_X : ROOT_X;
            for (float right : new float[]{FAR_RIGHT_EDGE, x - NEAR_LEFT_OF_OBJECT}) {
                Level level = harness.roomOf("explosive-test", 0);
                ExplosiveTrap t = trap(level, kind);
                place(level, right);
                assertFalse(level.player.hitboxBounds().overlaps(t.blastBox()), "clear of the square");
                assertTrue(t.igniteFromAfar());
                detonate(level, t);
                assertBlastShake(level);
            }
        }
    }

    @Test void theShakeLastsABlastsTimeMovesTheCameraAndIsNotRestartedByTheBlastFrames() {
        Level level = harness.roomOf("explosive-test", 0);
        ExplosiveTrap t = trap(level, ExplosiveTrap.Kind.STUMP);
        place(level, FAR_RIGHT_EDGE);
        t.igniteFromAfar();
        detonate(level, t);
        boolean moved = false;
        int frames = 0;
        while (level.fx().shake().running() && frames < 60) {
            frame(level);
            frames++;
            moved |= level.fx().shake().offset(ROOM, VIEW).len() > 0;
        }
        assertTrue(moved, "the camera is displaced while it runs");
        assertEquals(ScreenShake.BLAST_SECONDS, frames * STEP, STEP * 1.5f);
        assertEquals(ExplosiveTrap.Phase.BLAST, t.phase(), "the blast frames outlast the shake");
    }

    @Test void aLethalStumpOrRootBlastStartsNoShake() {
        for (ExplosiveTrap.Kind kind : ExplosiveTrap.Kind.values()) {
            Level level = harness.roomOf("explosive-test", 0);
            ExplosiveTrap t = trap(level, kind);
            place(level, (kind == ExplosiveTrap.Kind.STUMP ? STUMP_X : ROOT_X) + 8);
            frame(level);
            assertEquals(ExplosiveTrap.Phase.WINDUP, t.phase());
            detonate(level, t);
            assertTrue(level.player.isGotHit(), "inside the square: killed");
            assertFalse(level.fx().shake().running(), "the death beat is the feedback");
        }
    }

    @Test void theBombShakesForAFarSurvivor() {
        Level level = harness.roomOf("bomb-test", 0);
        place(level, FAR_RIGHT_EDGE);
        Bomb bomb = bomb(level);
        bomb.collideWithPlayer();
        detonate(level, bomb);
        assertBlastShake(level);
    }

    @Test void aLethalBombBlastStartsNoShake() {
        Level level = harness.roomOf("bomb-test", 0);
        place(level, BOMB_X + 8);
        Bomb bomb = bomb(level);
        bomb.collideWithPlayer();
        detonate(level, bomb);
        assertTrue(level.player.isGotHit(), "on the bomb: killed");
        assertFalse(level.fx().shake().running());
    }

    @Test void withTheScreenShakeOptionOffABlastDoesNotMoveTheCamera() {
        harness.screenShakeDisabled = true;
        Level level = harness.roomOf("explosive-test", 0);
        ExplosiveTrap t = trap(level, ExplosiveTrap.Kind.STUMP);
        place(level, FAR_RIGHT_EDGE);
        t.igniteFromAfar();
        detonate(level, t);
        for (int f = 0; f < 4; f++) {
            frame(level);
            assertEquals(0, level.fx().shake().offset(ROOM, VIEW).len(), 0);
        }
        harness.screenShakeDisabled = false;
    }
}
