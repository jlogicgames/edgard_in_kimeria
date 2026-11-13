package com.jlogicsoftware.kimeria.effects;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.entity.player.Player;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The particle spore cloud of the false honey fungus (#296, D63, D5): it lives exactly as long as the hitbox, every particle quad stays
 * inside the lethal rectangle (world Y grows downward: the rectangle is y 296..320 for a mushroom standing on y 320), the same seed gives
 * the same cloud, a paused game freezes it and a retry starts with none.
 */
class SporeCloudTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, WIDTH = 24, HEIGHT = 16, ZONE = 64, HERO_W = 11, HERO_H = 22, EDGE_EPS = 1e-3f;
    private static final float FALSE_X = 480;
    private static final int FRAMES_LIMIT = 600;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();
    private final Rectangle hero = new Rectangle(FALSE_X, FLOOR - HERO_H, HERO_W, HERO_H);

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { Shaders.dispose(); SoftDot.dispose(); harness.restoreGlobals(); }

    private FalseHoneyFungus fungus(float x) {
        return new FalseHoneyFungus(harness.assets(), harness.game().trapWarnings(), x, FLOOR - HEIGHT, WIDTH, HEIGHT, ZONE, ZONE, () -> hero);
    }

    private static void step(FalseHoneyFungus f) { f.update(STEP); f.render(new NoopBatch()); }

    /** Steps until the cloud is live (the swell ends). */
    private static void toBurst(FalseHoneyFungus f) {
        int frames = 0;
        while (!f.cloudLive() && frames++ < FRAMES_LIMIT) step(f);
        assertTrue(f.cloudLive(), "the swell must end in a burst");
    }

    @Test void theShaderLayerStartsAtZeroEndsAtZeroAndNeverExceedsItsPeak() {
        assertEquals(0f, SporeCloud.strength(0f), 0f);
        assertEquals(0f, SporeCloud.strength(1f), 0f, "gone exactly when the hitbox ends");
        float peak = 0;
        for (int i = 0; i <= 100; i++) peak = Math.max(peak, SporeCloud.strength(i / 100f));
        assertTrue(peak > 0.3f && peak < 1f, "visible but translucent: " + peak);
        assertTrue(SporeCloud.strength(0.5f) > 0.1f, "still clearly there mid-life, so the danger stays readable");
        assertEquals(0f, SporeCloud.swell(0f), 0f);
        assertEquals(1f, SporeCloud.swell(1f), 0f);
    }

    @Test void particlesLiveExactlyAsLongAsTheHitbox() {
        FalseHoneyFungus f = fungus(FALSE_X);
        KimeriaParticleSystem p = f.sporeCloud().particles();
        assertEquals(0, p.count(), "none before the burst");
        toBurst(f);
        assertEquals(SporeCloud.HAZE_COUNT + SporeCloud.SPECK_COUNT, p.count(), "the whole cloud is released at once");
        int liveFrames = 0;
        while (f.cloudLive()) {
            assertTrue(p.count() > 0, "particles while the hitbox is live, frame " + liveFrames);
            step(f);
            liveFrames++;
        }
        assertEquals(FungusTrap.State.SPENT, f.state());
        assertEquals(0, p.count(), "no particle outlives the hitbox");
        assertEquals(FalseHoneyFungus.CLOUD_SECONDS / STEP, liveFrames, 2);
    }

    @Test void everyParticleQuadStaysInsideTheLethalRectangleEveryFrame() {
        FalseHoneyFungus f = fungus(FALSE_X);
        Rectangle lethal = f.cloudBounds();
        KimeriaParticleSystem p = f.sporeCloud().particles();
        toBurst(f);
        int checked = 0;
        while (f.cloudLive()) {
            for (int i = 0; i < p.count(); i++) {
                float half = p.drawSize(i) / 2f;
                assertTrue(p.x(i) - half >= lethal.x - EDGE_EPS, "left edge, particle " + i);
                assertTrue(p.x(i) + half <= lethal.x + lethal.width + EDGE_EPS, "right edge, particle " + i);
                assertTrue(p.y(i) - half >= lethal.y - EDGE_EPS, "top edge (smaller Y is higher), particle " + i);
                assertTrue(p.y(i) + half <= lethal.y + lethal.height + EDGE_EPS, "bottom edge is the floor line, particle " + i);
                checked++;
            }
            step(f);
        }
        assertTrue(checked > SporeCloud.HAZE_COUNT, "the check really looked at particles over many frames: " + checked);
    }

    @Test void theCloudIsLimeHazeAndSpecksNotTheGoldenKind() {
        FalseHoneyFungus f = fungus(FALSE_X);
        toBurst(f);
        KimeriaParticleSystem p = f.sporeCloud().particles();
        int haze = 0, specks = 0;
        for (int i = 0; i < p.count(); i++) {
            if (p.kind(i) == KimeriaParticleSystem.KIND_SMOKE) haze++;
            if (p.kind(i) == KimeriaParticleSystem.KIND_GLOW) specks++;
        }
        assertEquals(SporeCloud.HAZE_COUNT, haze);
        assertEquals(SporeCloud.SPECK_COUNT, specks);
    }

    @Test void theSameSeedGivesTheSameCloudAndAnotherSeedAnother() {
        FalseHoneyFungus a = fungus(FALSE_X), b = fungus(FALSE_X), c = fungus(FALSE_X + WIDTH * 2);
        toBurst(a); toBurst(b); toBurst(c);
        KimeriaParticleSystem pa = a.sporeCloud().particles(), pb = b.sporeCloud().particles(), pc = c.sporeCloud().particles();
        boolean differs = false;
        for (int i = 0; i < pa.count(); i++) {
            assertEquals(pa.x(i), pb.x(i), 0f);
            assertEquals(pa.y(i), pb.y(i), 0f);
            assertEquals(pa.life(i), pb.life(i), 0f);
            if (pa.life(i) != pc.life(i)) differs = true;
        }
        assertTrue(differs, "a different mushroom gets a different cloud");
    }

    @Test void aPausedGameFreezesTheCloud() {
        FalseHoneyFungus f = fungus(FALSE_X);
        toBurst(f);
        for (int i = 0; i < 10; i++) step(f);
        KimeriaParticleSystem p = f.sporeCloud().particles();
        float x = p.x(0), y = p.y(0), life = p.life(0);
        float stateTime = f.stateTime();
        for (int i = 0; i < 120; i++) f.render(batch); // paused: Level.update is not called, only drawing continues
        assertEquals(x, p.x(0), 0f);
        assertEquals(y, p.y(0), 0f);
        assertEquals(life, p.life(0), 0f);
        assertEquals(stateTime, f.stateTime(), 0f);
        assertTrue(f.cloudLive());
    }

    @Test void aRetryStartsWithoutAnyCloud() {
        Level level = new Level(harness.assets(), harness.game(), new Player(harness.assets(), harness.game(), 0, 0), "fungus-test", Rectangle::new);
        FalseHoneyFungus before = level.objects.stream().filter(FalseHoneyFungus.class::isInstance).map(FalseHoneyFungus.class::cast).findFirst().orElseThrow();
        before.sporeCloud().burst();
        assertTrue(before.sporeCloud().particles().count() > 0);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        FalseHoneyFungus after = level.objects.stream().filter(FalseHoneyFungus.class::isInstance).map(FalseHoneyFungus.class::cast).findFirst().orElseThrow();
        assertNotSame(before, after);
        assertEquals(FungusTrap.State.IDLE, after.state());
        assertEquals(0, after.sporeCloud().particles().count());
    }

    @Test void withoutAShaderTheParticlesStillDrawAndNothingThrows() {
        FalseHoneyFungus f = fungus(FALSE_X);
        toBurst(f);
        for (int i = 0; i < 5; i++) f.render(batch);
        assertNull(Shaders.tryLoad(SporeCloud.SHADER), "the headless GL stub cannot build it; the fallback is particles only");
    }

    private static String shader(String name) throws IOException {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("assets/shaders"))) p = p.getParent();
        assertNotNull(p, "assets/shaders not found");
        return Files.readString(p.resolve("assets/shaders/" + name));
    }

    /**
     * Source conventions shared with the shaders that work on macOS desktop (GLSL 1.20 through libGDX's own header, no {@code #version})
     * and on the web (GLSL ES 1.0): same precision guard, the varyings {@code quad.vert} writes, every uniform {@link SporeCloud} sets.
     * Compiled for real against macOS GL 2.1 / GLSL 1.20 once by a scratch probe (2026-11-13); a headless test cannot compile GLSL.
     */
    @Test void theShaderFollowsTheConventionsOfTheShadersThatWork() throws IOException {
        String frag = shader(SporeCloud.SHADER), shockwave = shader("shockwave.frag"), vert = shader("quad.vert");
        String guard = "#ifdef GL_ES\nprecision mediump float;\n#endif";
        assertTrue(shockwave.contains(guard) && frag.contains(guard), "the same precision guard as shockwave.frag");
        assertFalse(frag.contains("#version"), "libGDX adds the header; no shader here declares a version");
        for (String varying : new String[]{"v_texCoords", "v_color"}) {
            assertTrue(vert.contains("varying") && vert.contains(varying), "quad.vert writes " + varying);
            assertTrue(frag.contains("varying") && frag.contains(varying), "the fragment reads " + varying);
        }
        for (String uniform : new String[]{"uTime", "uSwell", "uAlpha", "uOrigin"})
            assertTrue(frag.contains("uniform") && frag.contains(uniform), "declares " + uniform);
        for (String banned : new String[]{"texture2D", "for (", "while (", "discard", "dFdx"})
            assertFalse(frag.contains(banned), "not in the ES 1.0 subset used here: " + banned);
    }
}
