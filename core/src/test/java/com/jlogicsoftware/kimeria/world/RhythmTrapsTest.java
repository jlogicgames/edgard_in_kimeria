package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.backends.headless.HeadlessPreferences;
import com.badlogic.gdx.files.FileHandle;
import com.jlogicsoftware.kimeria.Settings;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.objects.Puffball;
import com.jlogicsoftware.kimeria.entity.objects.ThornVine;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rhythmic forest traps (D46, #234): spore puffball, swinging thorn vine and the rotten-log skin, on the
 * test-resource map {@code rhythm-test.tmx} (room 0 puffball, room 1 vine, room 2 log and plank). A scripted hero
 * with ordinary controls (hold right, standing still while the trap runs) measures the safe windows; waiting
 * before the walk is exactly a phase offset. Recipe: docs/ROOMS.md.
 */
class RhythmTrapsTest {
    private static final float STEP = 1f / 60f;

    private float wallClockDelta = STEP;
    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "language" -> Language.ENGLISH;
            case "isGameStarted" -> true;
            case "artifacts" -> artifacts;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? wallClockDelta : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
    }


    @TempDir Path directory;

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private static final int PUFF = 0, VINE = 1, LOG = 2;

    private Level room(int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "rhythm-test", Rectangle::new);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private void retry(Level level) { level.resetCurrentRoom(new Player(assets, game, 0, 0)); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private void run(Level level, float seconds) { settle(level, Math.round(seconds * 60)); }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    private float bodyLeft(Level level) { return level.player.hitboxBounds().x; }

    private Puffball puffball(Level level) {
        return level.objects.stream().filter(Puffball.class::isInstance).map(Puffball.class::cast).findFirst().orElseThrow();
    }

    private ThornVine vine(Level level) {
        return level.objects.stream().filter(ThornVine.class::isInstance).map(ThornVine.class::cast).findFirst().orElseThrow();
    }

    /** Ordinary play: wait {@code delay} seconds at the entrance (a phase offset), then hold Right until past {@code pastX}. */
    private boolean walkAcross(Level level, float delay, float pastX) {
        held.clear();
        run(level, delay);
        for (int i = 0; i < 60 * 12; i++) {
            held.clear();
            held.add(Input.Keys.D);
            frame(level);
            if (!alive(level)) { held.clear(); return false; }
            if (bodyLeft(level) > pastX) { held.clear(); return true; }
        }
        held.clear();
        fail("never crossed, hero at " + level.player.position);
        return false;
    }

    private static final float PUFF_PAST = 340, VINE_PAST = 800;

    /** Survival of the walk at every start offset (0.05 s steps over {@code period}); true = survived. */
    private boolean[] sweep(Level level, float period, float pastX) {
        boolean[] ok = new boolean[Math.round(period / 0.05f)];
        for (int i = 0; i < ok.length; i++) {
            retry(level);
            ok[i] = walkAcross(level, i * 0.05f, pastX);
        }
        return ok;
    }

    /** Longest run of survivors in seconds, treating the sweep as circular (the rhythm repeats). */
    private static float longestSafeRun(boolean[] ok) {
        int best = 0;
        for (int start = 0; start < ok.length; start++) {
            int n = 0;
            while (n < ok.length && ok[(start + n) % ok.length]) n++;
            best = Math.max(best, n);
        }
        return best * 0.05f;
    }

    // -- puffball --

    @Test void theFixtureHoldsOneOfEachTrapAndTheDefaultsAreAuthored() {
        Level level = room(PUFF);
        Puffball p = puffball(level);
        assertEquals(4f, p.period(), 0f);
        assertEquals(1f, p.cloudSeconds(), 0f);
        assertEquals(0f, p.cycleTime(), 0f, "a room starts at the authored phase");
        assertEquals(Puffball.Phase.CALM, p.currentPhase());
        assertEquals(4.5f, vine(room(VINE)).period(), 0f);
        assertEquals(1, room(LOG).fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).count());
        assertEquals(1, room(LOG).fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.PLATFORM).count());
    }

    @Test void theSwellTellIsTheSharedWarningAndTheAssistLengthensIt() {
        assertEquals(0.8f, TrapWarnings.DEFAULT_WARNING_SECONDS, 0f, "pinned shared warning");
        for (boolean assist : new boolean[]{false, true}) {
            warnings.load(new Settings(preferences(assist)));
            float expected = assist ? 1.2f : 0.8f;
            Level level = room(PUFF);
            Puffball p = puffball(level);
            assertEquals(expected, p.swellSeconds(), 1e-6f);
            int swellFrames = 0, previous = -1;
            for (int i = 0; i < 60 * 4; i++) {
                frame(level);
                if (p.currentPhase() == Puffball.Phase.SWELL) swellFrames++;
                else if (previous == 1 && swellFrames > 0) break;
                previous = p.currentPhase() == Puffball.Phase.SWELL ? 1 : 0;
            }
            assertEquals(expected, swellFrames * STEP, 2 * STEP, "assist=" + assist + " swell lasted " + swellFrames * STEP + " s");
        }
    }

    private Preferences preferences(boolean assist) {
        Preferences prefs = new HeadlessPreferences(new FileHandle(directory.resolve("settings" + assist + ".xml").toFile()));
        prefs.putBoolean(Settings.KEY_LONGER_TRAP_WARNINGS, assist);
        return prefs;
    }

    @Test void theCycleIsCalmThenSwellThenCloudWithTheCloudLastAndNoAssist() {
        Puffball p = puffball(room(PUFF));
        assertEquals(Puffball.Phase.CALM, p.phaseAt(0f));
        assertEquals(Puffball.Phase.CALM, p.phaseAt(2.19f));
        assertEquals(Puffball.Phase.SWELL, p.phaseAt(2.21f));
        assertEquals(Puffball.Phase.SWELL, p.phaseAt(2.99f));
        assertEquals(Puffball.Phase.CLOUD, p.phaseAt(3.01f));
        assertEquals(Puffball.Phase.CLOUD, p.phaseAt(3.99f));
    }

    @Test void theCloudIsLethalOnlyInsideItAndOnlyWhileItLasts() {
        Level level = room(PUFF);
        Puffball p = puffball(level);
        Rectangle cloud = p.cloudBounds();
        assertEquals(20f, cloud.width, 0f, "the 24 px cloud sprite minus a 2 px fringe each side");
        assertEquals(20f, cloud.height, 0f);
        assertEquals(p.getX() + p.getWidth() / 2f, cloud.x + cloud.width / 2f, 1e-4f, "centred on the mushroom");
        assertEquals(p.getY() + p.getHeight(), cloud.y + cloud.height + 2f, 1e-4f, "the cloud sits on the base line");
        Rectangle inside = new Rectangle(cloud.x + 1, cloud.y + 1, 18, 26);
        Rectangle beside = new Rectangle(cloud.x - 18 - 0.5f, cloud.y, 18, 26);
        // Calm and swell: harmless even inside the cloud's volume.
        for (float t : new float[]{0f, 1.5f, 2.19f, 2.5f, 2.99f}) {
            retry(level);
            run(level, t);
            assertNotEquals(Puffball.Phase.CLOUD, p(level).currentPhase());
            assertFalse(p(level).lethalTo(inside), "not lethal at t=" + t);
        }
        // The cloud: lethal inside, harmless right beside it, and over once it ends.
        retry(level);
        run(level, 3.05f);
        assertEquals(Puffball.Phase.CLOUD, p(level).currentPhase());
        assertTrue(p(level).lethalTo(inside));
        assertFalse(p(level).lethalTo(beside), "standing next to the mushroom is safe, even mid-cloud");
        run(level, 1.0f);
        assertEquals(Puffball.Phase.CALM, p(level).currentPhase(), "the cloud lasted 1 s and the mushroom is calm again (0.95 s later wraps to the cycle start)");
        assertFalse(p(level).lethalTo(inside));
    }

    private Puffball p(Level level) { return puffball(level); }

    @Test void aHeroInsideTheCloudDiesAndTheRoomRetryRestoresTheAuthoredPhase() {
        Level level = room(PUFF);
        Puffball p = puffball(level);
        Rectangle cloud = p.cloudBounds();
        level.player.position.set(cloud.x - 11 + 1, 272);
        run(level, 3.05f); // hero stands on the floor inside the cloud's column
        assertTrue(level.player.isGotHit(), "the burst kills a hero standing in the cloud");
        retry(level);
        assertFalse(level.player.isGotHit());
        assertEquals(0f, puffball(level).cycleTime(), 0f, "the retry starts at the authored phase");
        assertEquals(Puffball.Phase.CALM, puffball(level).currentPhase());
    }

    @Test void aHeroBesideTheMushroomSurvivesEveryPhase() {
        Level level = room(PUFF);
        Puffball p = puffball(level);
        level.player.position.set(p.getX() - 20 - 11 - 18, 272); // body ends 20 px left of the mushroom, outside the cloud
        run(level, 12f);
        assertTrue(alive(level), "next to it through three whole cycles");
    }

    @Test void thereIsALongSafeWindowAndAWalkerPassesAtEveryPhaseItWaitsOut() {
        Level level = room(PUFF);
        boolean[] ok = sweep(level, 4f, PUFF_PAST);
        float run = longestSafeRun(ok);
        long died = 0; for (boolean b : ok) if (!b) died++;
        System.out.printf(java.util.Locale.ROOT, "puffball: blind walk survives %.2f s of every %.2f s of start offsets, %d of %d offsets die%n", run, 4f, died, ok.length);
        assertTrue(died > 0, "an unlucky blind walk does die: the trap is real");
        assertTrue(run >= 2.0f, "a blind walker has a safe start window of at least 2 s: " + run);
        // A walker who watches the mushroom: halts before the cloud's column and goes when the cloud has ended.
        for (int i = 0; i < ok.length; i++) {
            retry(level);
            run(level, i * 0.05f);
            Puffball p = puffball(level);
            boolean crossed = false, committed = false;
            for (int f = 0; f < 60 * 20 && alive(level); f++) {
                held.clear();
                boolean atEdge = level.player.hitboxBounds().x + 18 >= p.cloudBounds().x - 24;
                if (atEdge && p.currentPhase() == Puffball.Phase.CALM && p.cycleTime() < 0.4f) committed = true;
                if (committed || !atEdge) held.add(Input.Keys.D);
                frame(level);
                if (bodyLeft(level) > PUFF_PAST) { crossed = true; break; }
            }
            held.clear();
            assertTrue(crossed && alive(level), "the careful walker passes at offset " + i * 0.05f);
        }
    }

    @Test void theAssistLengthensTheSwellAndTheWalkerStillHasTheMinimumCalm() {
        assertNull(Puffball.validate(4f, 1f, 0f));
        assertNull(Puffball.validate(3f, 1f, 0f), "1 + 1.2 assisted swell + 0.8 minimum calm is exactly 3.0");
        assertNotNull(Puffball.validate(2.9f, 1f, 0f));
        assertNotNull(Puffball.validate(2f, 1f, 0f));
        assertNotNull(Puffball.validate(4f, 0f, 0f));
        warnings.load(new Settings(preferences(true)));
        Level level = room(PUFF);
        float calm = 0;
        Puffball p = puffball(level);
        for (float t = 0; t < 4f; t += 0.01f) if (p.phaseAt(t) == Puffball.Phase.CALM) calm += 0.01f;
        assertEquals(4f - 1f - 1.2f, calm, 0.03f, "assist: calm shrinks by the extra swell");
        assertTrue(calm >= Puffball.MIN_CALM_SECONDS);
        boolean[] ok = sweep(level, 4f, PUFF_PAST);
        assertTrue(longestSafeRun(ok) >= 1.5f, "the blind-walk safe window with the assist: " + longestSafeRun(ok));
    }

    @Test void thePuffballIsDeterministicAcrossRunsAndRetries() {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (StringBuilder out : new StringBuilder[]{a, b}) {
            Level level = room(PUFF);
            for (int i = 0; i < 60 * 9; i++) { frame(level); out.append(puffball(level).currentPhase().ordinal()); }
        }
        assertEquals(a.toString(), b.toString());
        Level level = room(PUFF);
        run(level, 1.7f);
        retry(level);
        StringBuilder c = new StringBuilder();
        for (int i = 0; i < 60 * 9; i++) { frame(level); c.append(puffball(level).currentPhase().ordinal()); }
        assertEquals(a.toString(), c.toString(), "after a retry it replays the same rhythm from the start");
    }

    @Test void pausingFreezesBothTrapsAndResumingContinuesFromWhereTheyStopped() {
        // The game stops calling Level.update while paused (KimeriaGame) and the traps read no clock but the dt they are given.
        for (int index : new int[]{PUFF, VINE}) {
            Level paused = room(index), straight = room(index);
            run(paused, 1.7f);
            float cycle = index == PUFF ? puffball(paused).cycleTime() : vine(paused).elapsed();
            wallClockDelta = 5f; // a long pause: the frame clock jumps, but no update reaches the level
            for (int i = 0; i < 300; i++) pressed.clear();
            wallClockDelta = STEP;
            assertEquals(cycle, index == PUFF ? puffball(paused).cycleTime() : vine(paused).elapsed(), 0f, "frozen while paused");
            assertNotEquals(0f, cycle, "and not restarted");
            run(paused, 1.3f);
            run(straight, 3.0f);
            if (index == PUFF) assertEquals(puffball(straight).cycleTime(), puffball(paused).cycleTime(), 1e-4f);
            else assertEquals(vine(straight).angle(), vine(paused).angle(), 1e-6f, "the swing continues, it does not restart");
        }
    }

    @Test void aPuffballCloudThatLeavesTheRoomOrAMistunedPeriodFailsTheLoadWithTheObjectNamed() {
        IllegalArgumentException tight = assertThrows(IllegalArgumentException.class,
            () -> new Level(assets, game, new Player(assets, game, 0, 0), "rhythm-bad-period-test", Rectangle::new));
        assertTrue(tight.getMessage().contains("Puffball") && tight.getMessage().contains("[2]"), tight.getMessage());
        IllegalArgumentException edge = assertThrows(IllegalArgumentException.class,
            () -> new Level(assets, game, new Player(assets, game, 0, 0), "rhythm-bad-edge-test", Rectangle::new));
        assertTrue(edge.getMessage().contains("ThornVine") && edge.getMessage().contains("leaves the room"), edge.getMessage());
    }

    // -- vine --

    @Test void theVineSwingsAsAPendulumWithAFixedPeriodAndPhase() {
        ThornVine v = vine(room(VINE));
        float amplitude = (float) Math.toRadians(50);
        assertEquals(0f, v.angleAt(0f), 1e-6f);
        assertEquals(amplitude, v.angleAt(1.125f), 1e-5f, "quarter period: furthest right");
        assertEquals(0f, v.angleAt(2.25f), 1e-5f);
        assertEquals(-amplitude, v.angleAt(3.375f), 1e-5f);
        assertEquals(v.angleAt(0.7f), v.angleAt(5.2f), 1e-5f, "period 4.5 s");
        assertEquals(742f, v.anchorX(), 0f, "hung from the top centre");
        assertEquals(256f, v.anchorY(), 0f);
        assertEquals(40f, v.length(), 0f);
    }

    @Test void onlyTheThornyLowerHalfOfTheVineIsLethalAndTheStemIsHarmless() {
        ThornVine v = vine(room(VINE));
        Rectangle tipBox = new Rectangle(v.anchorX() - 9, v.anchorY() + 34, 18, 26);
        Rectangle stemBox = new Rectangle(v.anchorX() - 9, v.anchorY() - 20, 18, 26); // reaches 6 px down the stem
        Rectangle upperStem = new Rectangle(v.anchorX() - 9, v.anchorY() + 6, 18, 8); // 6..14 px down: the harmless half
        assertTrue(v.lethalAt(0f, tipBox), "the tip hanging straight down kills");
        assertFalse(v.lethalAt(0f, stemBox));
        assertFalse(v.lethalAt(0f, upperStem), "the upper stem is harmless");
        assertTrue(v.lethalAt(0f, new Rectangle(v.anchorX() - 9, v.anchorY() + 22, 18, 4)), "just below the half-way point kills");
        assertFalse(v.lethalAt(0f, new Rectangle(v.anchorX() + 4 + 0.5f, v.anchorY() + 30, 18, 4)), "beside the 8 px wide thorn part is safe");
        float swing = (float) Math.toRadians(50);
        Rectangle swungTip = new Rectangle(v.anchorX() + 40 * (float) Math.sin(swing) - 4, v.anchorY() + 40 * (float) Math.cos(swing) - 4, 8, 8);
        assertTrue(v.lethalAt(swing, swungTip), "the tip follows the swing");
        assertFalse(v.lethalAt(0f, swungTip), "and is gone from the old place");
    }

    @Test void aBlindWalkerHasAWideSafeWindowUnderTheSwingAndTheDangerIsReal() {
        Level level = room(VINE);
        boolean[] ok = sweep(level, 4.5f, VINE_PAST);
        long died = 0; for (boolean b : ok) if (!b) died++;
        float run = longestSafeRun(ok);
        System.out.printf(java.util.Locale.ROOT, "vine: blind walk survives %.2f s of every 4.5 s of start offsets, %d of %d offsets die%n", run, died, ok.length);
        assertTrue(died > 0, "the swing really kills a hero who walks into it");
        assertTrue(run >= 1.0f, "a safe start window of at least 1 s per swing: " + run);
    }

    @Test void aWalkerWatchingTheVinePassesAtEveryPhase() {
        Level level = room(VINE);
        for (int i = 0; i < 90; i++) {
            retry(level);
            run(level, i * 0.05f);
            ThornVine v = vine(level);
            boolean crossed = false, committed = false;
            for (int f = 0; f < 60 * 25 && alive(level); f++) {
                held.clear();
                boolean atEdge = level.player.hitboxBounds().x + 18 >= v.anchorX() - 30;
                boolean beyond = level.player.hitboxBounds().x > v.anchorX() - 20;
                // Wait at the edge until the swing is lifting the tip (past 35 degrees and still rising), then run under it.
                boolean rising = Math.abs(v.angleAt(v.elapsed() + 0.05f)) > Math.abs(v.angle());
                if (atEdge && (beyond || (Math.abs(v.angle()) > Math.toRadians(35) && rising))) committed = true;
                if (committed || !atEdge) held.add(Input.Keys.D);
                frame(level);
                if (bodyLeft(level) > VINE_PAST) { crossed = true; break; }
            }
            held.clear();
            assertTrue(crossed && alive(level), "the careful walker passes the vine at offset " + i * 0.05f + " s, hero at " + level.player.position);
        }
    }

    @Test void theVineIsDeterministicAndRetryRestartsAtThePhase() {
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (StringBuilder out : new StringBuilder[]{a, b}) {
            Level level = room(VINE);
            for (int i = 0; i < 60 * 9; i++) { frame(level); out.append(Math.round(vine(level).angle() * 1000)).append(','); }
        }
        assertEquals(a.toString(), b.toString());
        Level level = room(VINE);
        run(level, 2.3f);
        retry(level);
        assertEquals(0f, vine(level).elapsed(), 0f);
        assertEquals(0f, vine(level).angle(), 1e-6f);
    }

    @Test void theVineKillsAHeroInItsPathThroughTheLevelLoop() {
        Level level = room(VINE);
        ThornVine v = vine(level);
        level.player.position.set(v.anchorX() - 9 - 11, 272); // the vine still hangs about straight down
        run(level, 0.1f);
        assertTrue(level.player.isGotHit(), "standing under the hanging tip at the floor dies");
    }

    @Test void vineAuthoringRulesRejectATooFastOrTooWideSwing() {
        assertNull(ThornVine.validate(4.5f, 50f, 0f));
        assertNull(ThornVine.validate(3.5f, 35f, 0f));
        assertNotNull(ThornVine.validate(3.4f, 50f, 0f));
        assertNotNull(ThornVine.validate(4f, 34f, 0f));
        assertNotNull(ThornVine.validate(4f, 61f, 0f));
    }

    // -- rotten log --

    private FallingPlatform log(Level level) {
        return level.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.LOG).findFirst().orElseThrow();
    }

    private FallingPlatform plank(Level level) {
        return level.fallingPlatforms.stream().filter(f -> f.skin() == FallingPlatform.Skin.PLATFORM).findFirst().orElseThrow();
    }

    @Test void theRottenLogFallsExactlyLikeThePlatformAndDefaultsStayThePlatformSkin() {
        assertEquals(FallingPlatform.Skin.PLATFORM, FallingPlatform.Skin.fromMap(""));
        assertEquals(FallingPlatform.Skin.LOG, FallingPlatform.Skin.fromMap("log"));
        assertThrows(IllegalArgumentException.class, () -> FallingPlatform.Skin.fromMap("oak"));
        Level level = room(LOG);
        FallingPlatform log = log(level), plank = plank(level);
        assertFalse(log.isFalling());
        log.collideWithActor(); plank.collideWithActor();
        float logY = log.getY(), plankY = plank.getY();
        int firstMove = -1;
        for (int i = 0; i < 60 * 4; i++) {
            frame(level);
            assertEquals(log.isFalling(), plank.isFalling());
            if (firstMove < 0 && log.getY() > logY) {
                firstMove = i;
                assertTrue(plank.getY() > plankY, "the plank moves on the very same frame");
            }
            if (log.isRemoved()) { assertTrue(plank.isRemoved(), "gone together"); break; }
            assertEquals(log.getY() - logY, plank.getY() - plankY, 1e-4f, "same fall");
        }
        assertTrue(log.isRemoved());
        assertEquals(TrapWarnings.FALLING_PLATFORM_SECONDS, firstMove * STEP, 3 * STEP, "falls after the shared 1 s warning");
    }

    /** Rhythmic trap types and the log skin each shipped level may use; #260 (D58) allows the one puffball and the rotten log in forest-1. */
    private static final java.util.Map<String, Set<String>> SHIPPED_ALLOWANCE = java.util.Map.of(
        "forest-0", Set.of(), "forest-1", Set.of("Puffball", "skin"), "forest-2", Set.of());

    @Test void shippedLevelsUseOnlyAllowedRhythmTrapsAndEveryTrapTheyUseLoadsValidated() {
        for (String name : new String[]{"forest-0", "forest-1", "forest-2"}) {
            String text = Gdx.files.internal("tiles/" + name + ".tmx").readString();
            Set<String> used = new HashSet<>();
            if (text.contains("\"Puffball\"")) used.add("Puffball");
            if (text.contains("\"ThornVine\"")) used.add("ThornVine");
            if (text.contains("\"skin\"")) used.add("skin");
            assertTrue(SHIPPED_ALLOWANCE.get(name).containsAll(used), name + " uses " + used + ", allowed " + SHIPPED_ALLOWANCE.get(name));
            if (name.equals("forest-1")) continue; // its decorative sky needs a GL texture; the map text check above covers it
            // Loading runs the strict validation of every trap in the map (rhythm, room, entrance, exit, checkpoints).
            Level level = new Level(assets, game, new Player(assets, game, 0, 0), name, Rectangle::new);
            if (!used.contains("skin")) assertTrue(level.fallingPlatforms.stream().allMatch(f -> f.skin() == FallingPlatform.Skin.PLATFORM), name);
        }
    }

    // -- strict loading --

    private static final String BASE = "<?xml version=\"1.0\"?><map width=\"40\" height=\"23\" tilewidth=\"16\" tileheight=\"16\">"
        + "<objectgroup name=\"Rooms\"><object name=\"a\" type=\"Room\" x=\"0\" y=\"0\" width=\"320\" height=\"368\"/>"
        + "<object name=\"a\" type=\"RoomStart\" x=\"32\" y=\"272\"><point/></object>"
        + "<object name=\"b\" type=\"Room\" x=\"320\" y=\"0\" width=\"320\" height=\"368\"/>"
        + "<object name=\"a\" type=\"RoomExit\" x=\"272\" y=\"272\" width=\"32\" height=\"48\"/>"
        + "<object name=\"b\" type=\"RoomStart\" x=\"352\" y=\"272\"><point/></object></objectgroup>"
        + "<objectgroup name=\"SpawnPoints\"><object id=\"1\" type=\"Player\" x=\"32\" y=\"272\" width=\"48\" height=\"48\"/>"
        + "<object id=\"8\" type=\"Checkpoint\" x=\"150\" y=\"288\" width=\"16\" height=\"32\"/>"
        + "<object id=\"9\" name=\"Deadline\" type=\"Deadline\" x=\"-1000\" y=\"368\" width=\"3000\" height=\"16\"/>%s</objectgroup>"
        + "<objectgroup name=\"Collisions\"><object id=\"7\" x=\"0\" y=\"320\" width=\"320\" height=\"48\"/>"
        + "<object id=\"6\" x=\"320\" y=\"320\" width=\"320\" height=\"48\"/></objectgroup></map>";

    private static String trap(String type, String x, String y, String w, String h, String... props) {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < props.length; i += 2) p.append("<property name=\"").append(props[i]).append("\" value=\"").append(props[i + 1]).append("\"/>");
        return "<object id=\"2\" type=\"" + type + "\" x=\"" + x + "\" y=\"" + y + "\" width=\"" + w + "\" height=\"" + h + "\"><properties>" + p + "</properties></object>";
    }

    /** Loads a generated one-off map (the file goes through the same Gdx.files path as the shipped ones). */
    private Level loadGenerated(String objects) throws java.io.IOException {
        java.io.File file = directory.resolve("generated.tmx").toFile();
        java.nio.file.Files.writeString(file.toPath(), String.format(BASE, objects));
        Files real = Gdx.files;
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class},
            (proxy, method, args) -> method.getName().equals("internal") && "tiles/generated.tmx".equals(args[0])
                ? new FileHandle(file) : method.invoke(real, args));
        return new Level(assets, game, new Player(assets, game, 0, 0), "generated", Rectangle::new);
    }

    private String loadFailure(String objects) {
        return assertThrows(IllegalArgumentException.class, () -> loadGenerated(objects)).getMessage();
    }

    @Test void aWellPlacedTrapLoadsFromAGeneratedMap() throws Exception {
        assertNotNull(puffball(loadGenerated(trap("Puffball", "200", "304", "16", "16"))));
        assertNotNull(vine(loadGenerated(trap("ThornVine", "200", "256", "24", "40"))));
    }

    @Test void aTrapThatCouldCoverTheEntranceTheExitOrACheckpointIsRejected() {
        // Entrance body is x 43..61, y 294..320 (RoomStart 32,272 plus the hero's box offset).
        String puff = loadFailure(trap("Puffball", "40", "304", "16", "16"));
        assertTrue(puff.contains("Puffball") && puff.contains("entrance"), puff);
        String puffLate = loadFailure(trap("Puffball", "40", "304", "16", "16", "phase", "3.5"));
        assertTrue(puffLate.contains("entrance"), "a phase that puts the cloud over the start at t=0: " + puffLate);
        String vineStart = loadFailure(trap("ThornVine", "40", "256", "24", "40"));
        assertTrue(vineStart.contains("ThornVine") && vineStart.contains("entrance"), vineStart);
        String exit = loadFailure(trap("Puffball", "280", "304", "16", "16"));
        assertTrue(exit.contains("exit"), exit);
        String vineExit = loadFailure(trap("ThornVine", "258", "256", "24", "40"));
        assertTrue(vineExit.contains("exit"), vineExit);
        String checkpoint = loadFailure(trap("Puffball", "150", "304", "16", "16"));
        assertTrue(checkpoint.contains("checkpoint"), checkpoint);
        String vineCheckpoint = loadFailure(trap("ThornVine", "140", "256", "24", "40"));
        assertTrue(vineCheckpoint.contains("checkpoint"), vineCheckpoint);
    }

    @Test void nonFiniteMalformedOrNonPositiveRhythmPropertiesFailTheLoadInsteadOfFallingBack() {
        for (String bad : new String[]{"NaN", "Infinity", "-Infinity", "1,5", "soon"}) {
            for (String key : new String[]{"period", "phase"}) {
                assertTrue(loadFailure(trap("ThornVine", "200", "256", "24", "40", key, bad)).contains("ThornVine"), "vine " + key + "=" + bad);
                assertTrue(loadFailure(trap("Puffball", "200", "304", "16", "16", key, bad)).contains("Puffball"), "puffball " + key + "=" + bad);
            }
            assertTrue(loadFailure(trap("ThornVine", "200", "256", "24", "40", "amplitude", bad)).contains("amplitude"));
            assertTrue(loadFailure(trap("Puffball", "200", "304", "16", "16", "cloud", bad)).contains("cloud"));
        }
        assertNotNull(ThornVine.validate(-4f, 50f, 0f));
        assertNotNull(ThornVine.validate(Float.NaN, 50f, 0f));
        assertNotNull(ThornVine.validate(4.5f, 50f, Float.POSITIVE_INFINITY));
        assertNotNull(Puffball.validate(Float.NaN, 1f, 0f));
        assertNotNull(Puffball.validate(4f, 1f, Float.NaN));
        assertNotNull(Puffball.validate(-4f, 1f, 0f));
    }

    @Test void aRhythmTrapKillsExactlyLikeTheBombBlastWithNoExtraStateGuard() {
        Level level = room(PUFF);
        Rectangle cloud = puffball(level).cloudBounds();
        level.player.position.set(cloud.x - 11 + 1, 272);
        run(level, 3.05f);
        assertTrue(level.player.isGotHit(), "the same collidedWithActor(false) path the bomb blast uses");
    }
}
