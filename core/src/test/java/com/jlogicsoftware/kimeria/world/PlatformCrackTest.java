package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.jlogicsoftware.kimeria.Settings;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.EnvironmentFx;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The rotten log and the rotten branch are heard and seen when they give way (#444, D93): one {@code platform_crack} and
 * {@value EnvironmentFx#SPLINTERS} splinters in the frame the warning ends, once per fall, in the platform's own wood.
 * Real shipped maps: the log over the pit of forest-0-traps and the top branch of forest-1-tree. Y grows downward.
 */
class PlatformCrackTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int SECONDS = 60;
    private static final String CRACK = "platform_crack";
    private static final float WARNING = TrapWarnings.FALLING_PLATFORM_SECONDS, EPSILON = .001f;
    private static final float HERO_FRAME = 48f, HERO_SHIFT_X = 8f;
    /** Frames before the warning ends in which nothing may happen yet, and the few after in which the drop must come. */
    private static final int MARGIN_FRAMES = 3;
    /** .3 s of splinter flight: long enough to have a direction, shorter than any splinter's life (.35 s at least). */
    private static final int FLIGHT_FRAMES = 18;
    private static final float BESIDE_THE_COLUMN = 96f;
    private static final float[][] LOG_WOOD = {{.541f, .353f, .188f}, {.753f, .541f, .298f}, {.937f, .902f, .769f}};
    private static final float[][] DEAD_WOOD = {{.478f, .431f, .424f}, {.722f, .678f, .612f}, {.937f, .902f, .769f}};

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    @TempDir Path directory;

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void tearDown() { h.restoreGlobals(); }

    private void run(Level level, int frames) { for (int i = 0; i < frames; i++) { level.update(STEP); h.pressed.clear(); } }
    private long cracks() { return h.sounds.stream().filter(CRACK::equals).count(); }
    private Level traps() { return h.room("forest-0", "forest-0-traps"); }
    private static FallingPlatform topBranch(Level level) {
        return level.fallingPlatforms.stream().filter(p -> p.skin() == FallingPlatform.Skin.BRANCH)
            .min((a, b) -> Float.compare(a.getY(), b.getY())).orElseThrow();
    }

    /** Drops the hero onto the platform and runs until it is landed on. */
    private void land(Level level, FallingPlatform platform) {
        level.player.position.set(platform.getX() - HERO_SHIFT_X, platform.getY() - HERO_FRAME);
        level.player.velocity().setZero();
        for (int i = 0; i < SECONDS && !platform.isFalling(); i++) run(level, 1);
        assertTrue(platform.isFalling(), "the hero landed on it");
    }

    /**
     * Checked in the crack frame, then {@value #FLIGHT_FRAMES} frames of flight later (the particles only, the level stands
     * still). The side is read from the flight: {@code Level.update} spawns the splinters after the particles' step, so in
     * the crack frame each still sits at its random start beside the break and all six can be on one side of it (1 in 32).
     */
    private void assertSplinters(Level level, FallingPlatform platform, float restY, float[][] wood) {
        KimeriaParticleSystem grit = level.fx().grit();
        assertEquals(EnvironmentFx.SPLINTERS, grit.count(), "the splinters of one break");
        for (int i = 0; i < grit.count(); i++) {
            int n = i;
            assertEquals(KimeriaParticleSystem.KIND_GRIT, grit.kind(i));
            assertTrue(List.of(wood).stream().anyMatch(c -> Math.abs(c[0] - grit.red(n)) < EPSILON
                && Math.abs(c[1] - grit.green(n)) < EPSILON && Math.abs(c[2] - grit.blue(n)) < EPSILON), "the platform's own wood");
            assertTrue(grit.y(i) > restY && grit.y(i) < restY + platform.getHeight(), "they start inside the platform, at the break");
        }
        for (int frame = 0; frame < FLIGHT_FRAMES; frame++) grit.update(STEP);
        assertEquals(EnvironmentFx.SPLINTERS, grit.count(), "all still in the air");
        int left = 0, right = 0;
        for (int i = 0; i < grit.count(); i++) {
            if (grit.x(i) < grit.fromX(i)) left++;
            if (grit.x(i) > grit.fromX(i)) right++;
        }
        assertEquals(EnvironmentFx.SPLINTERS / 2, left, "half fly to the left");
        assertEquals(EnvironmentFx.SPLINTERS / 2, right, "half fly to the right");
    }

    @Test void theLogCracksOnceAtTheDropAndNotAtTheLanding() {
        Level level = traps();
        FallingPlatform log = level.fallingPlatforms.get(0);
        float restY = log.getY();
        land(level, log);
        assertEquals(0, cracks(), "silent at the landing: the warning is the shake and the split");
        run(level, Math.round(WARNING * SECONDS) - MARGIN_FRAMES);
        assertEquals(0, cracks(), "and through the warning");
        assertEquals(restY, log.getY(), .01f);
        assertEquals(0, level.fx().grit().count());
        for (int i = 0; i < 2 * MARGIN_FRAMES && cracks() == 0; i++) run(level, 1);
        assertEquals(1, cracks(), "the crack as it gives way");
        assertSplinters(level, log, restY, LOG_WOOD);
        run(level, 3 * SECONDS);
        assertEquals(1, cracks(), "once per fall");
        assertTrue(level.fallingPlatforms.isEmpty(), "it fell and is gone");
        assertEquals(0, level.fx().grit().count(), "the splinters have faded");
    }

    @Test void theRottenBranchCracksWithTheSameSoundAndGreySplinters() {
        Level level = h.room("forest-1", "forest-1-tree");
        FallingPlatform branch = topBranch(level);
        float restY = branch.getY();
        land(level, branch);
        for (int i = 0; i < 2 * SECONDS && cracks() == 0; i++) run(level, 1);
        assertEquals(1, cracks());
        assertSplinters(level, branch, restY, DEAD_WOOD);
        level.player.position.x -= BESIDE_THE_COLUMN; // clear of the lower branches, which would each break under him too
        level.player.velocity().setZero();
        run(level, 2 * SECONDS);
        assertEquals(1, cracks(), "once per fall");
    }

    @Test void theSplintersStayCloseUnderThePlatformSoNoneIsDrawnIntoAFloor() {
        Level level = traps();
        FallingPlatform log = level.fallingPlatforms.get(0);
        float bottom = log.getY() + log.getHeight();
        float lowest = 0;
        for (int burst = 0; burst < 40; burst++) {
            level.fx().clear();
            level.platformGaveWay(log);
            KimeriaParticleSystem grit = level.fx().grit();
            while (grit.count() > 0) {
                for (int i = 0; i < grit.count(); i++) lowest = Math.max(lowest, grit.y(i) + grit.drawSize(i) / 2f);
                grit.update(STEP);
            }
        }
        assertTrue(lowest > bottom, "they fall past it");
        assertTrue(lowest <= bottom + EnvironmentFx.SPLINTER_REACH_PX + EPSILON, "lowest " + lowest + ", bottom " + bottom);
    }

    @Test void theLongerWarningsAssistDelaysTheCrackWithTheDrop() {
        h.game().trapWarnings().toggle(new Settings(new HeadlessPreferences(new FileHandle(directory.resolve("prefs.xml").toFile()))));
        assertTrue(h.game().trapWarnings().longer());
        Level level = traps();
        land(level, level.fallingPlatforms.get(0));
        run(level, Math.round(h.game().trapWarnings().duration(WARNING) * SECONDS) - MARGIN_FRAMES);
        assertEquals(0, cracks(), "still warning after the base time");
        run(level, 2 * MARGIN_FRAMES);
        assertEquals(1, cracks());
    }

    @Test void aRestoredWarningStillCracksOnceAndARestoredFallDoesNotCrackAgain() {
        Level level = traps();
        FallingPlatform log = level.fallingPlatforms.get(0);
        float x = log.getX(), y = log.getY();
        log.restore(new FallingPlatform.Snapshot(x, y, true, -STEP, .2f, true, 0f, false)); // already dropping
        run(level, SECONDS / 2);
        assertEquals(0, cracks(), "a fall under way is not a new break");
        log.restore(new FallingPlatform.Snapshot(x, y, true, .2f, 0f, true, 0f, false)); // mid-warning
        run(level, SECONDS);
        assertEquals(1, cracks(), "the saved countdown ends in one crack");
    }

    @Test void withSoundOffTheSplintersStillFly() {
        h.playSounds = false;
        Level level = traps();
        level.platformGaveWay(level.fallingPlatforms.get(0));
        assertTrue(h.sounds.isEmpty());
        assertEquals(EnvironmentFx.SPLINTERS, level.fx().grit().count());
    }
}
