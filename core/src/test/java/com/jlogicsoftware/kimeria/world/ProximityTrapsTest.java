package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
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
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
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
 * Proximity traps (D44, #231): the thorn root and the bristling thorn tuft, driven headlessly through real Level
 * updates in test-resource maps (thorn-*.tmx), with ordinary keyboard controls for the hero. Times are the shared D7
 * warning (0.8 s, 1.2 s with the longer-warnings assist). Recipe: docs/ROOMS.md.
 */
class ProximityTrapsTest {
    private static final float STEP = 1f / 60f;

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
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? STEP : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private void frame(Level level) {
        level.update(STEP);
        pressed.clear();
    }

    private static final float ROOT_X = 288, ROOT_RIGHT = 304, TUFT_X = 480, TUFT_RIGHT = 496;
    private static final float WARNING = TrapWarnings.DEFAULT_WARNING_SECONDS;

    private Level level(String map) { return new Level(assets, game, new Player(assets, game, 0, 0), map, Rectangle::new); }
    private Level fresh() { return level("thorn-test"); }

    private ThornTrap trap(Level level, ThornTrap.Kind kind) {
        return level.objects.stream().filter(ThornTrap.class::isInstance).map(ThornTrap.class::cast)
            .filter(t -> t.kind() == kind).findFirst().orElseThrow();
    }
    private ThornTrap root(Level level) { return trap(level, ThornTrap.Kind.ROOT); }
    private ThornTrap tuft(Level level) { return trap(level, ThornTrap.Kind.TUFT); }

    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private float bodyLeft(Level level) { return level.player.hitboxBounds().x; }
    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    /** Stands the hero (body right edge at {@code right}) on the floor, still. */
    private void place(Level level, float right) { level.player.position.set(right - 29, 272); level.player.velocity().setZero(); held.clear(); }
    private void retry(Level level) { level.resetCurrentRoom(new Player(assets, game, 0, 0)); settle(level, 2); }

    private void step(Level level, boolean right, boolean jump) {
        held.clear();
        if (right) held.add(Input.Keys.D);
        if (jump) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }
        frame(level);
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(warnings, longer);
    }

    /** Frames from the trigger until the trap leaves its warning, with the hero standing outside the lethal column. */
    private int warningFrames(Level level, ThornTrap trap, float heroRight) {
        place(level, heroRight);
        int frames = 0;
        while (trap.phase() != ThornTrap.Phase.WARNING && frames++ < 10) frame(level);
        assertEquals(ThornTrap.Phase.WARNING, trap.phase(), "the zone started the warning");
        frames = 0;
        while (trap.phase() == ThornTrap.Phase.WARNING && frames++ < 60 * 5) frame(level);
        return frames;
    }

    /** Arms {@code trap} with the hero standing beside it, then returns the hero to the entrance. */
    private void arm(Level level, ThornTrap trap, float heroRight) {
        place(level, heroRight);
        settle(level, 60 * 2);
        assertEquals(ThornTrap.Phase.ARMED, trap.phase());
        assertTrue(alive(level), "standing clear of the column is safe");
    }

    // -- the objects, their parameters and the shared timing --

    @Test void theFixtureHoldsOneDormantRootAndOneDormantTuftWithTheDocumentedZones() {
        Level level = fresh();
        assertEquals(2, level.objects.stream().filter(ThornTrap.class::isInstance).count());
        for (ThornTrap t : new ThornTrap[]{root(level), tuft(level)}) {
            assertEquals(ThornTrap.Phase.DORMANT, t.phase());
            assertFalse(t.isLethal());
            assertEquals(0, t.frameIndex(), "hidden root / calm tuft");
        }
        assertEquals(80f, ThornTrap.DEFAULT_ZONE, 0f, "default zone: 80 px beyond each side");
        // Footprint 16 wide widened by 80 each side; from 32 px above the footprint to the floor.
        assertEquals(new Rectangle(208, 264, 176, 56), root(level).triggerZone());
        assertEquals(new Rectangle(400, 272, 176, 48), tuft(level).triggerZone());
        // Lethal column: root 6 px wide, 22 px tall once up; tuft 12 px wide, 12 px tall.
        arm(level, root(level), ROOT_X - 10);
        assertEquals(new Rectangle(293, 298, 6, 22), root(level).lethalBounds());
        arm(level, tuft(level), TUFT_X - 10);
        assertEquals(new Rectangle(482, 308, 12, 12), tuft(level).lethalBounds());
        assertEquals(.8f, TrapWarnings.DEFAULT_WARNING_SECONDS, 0f);
        assertEquals(.12f, ThornTrap.RISE_SECONDS, 0f);
    }

    @Test void theZoneStartsTheWarningAtItsBoundaryAndNotOnePixelBefore() {
        for (float edge : new float[]{208f, 400f}) { // root zone's left edge, tuft zone's left edge
            Level level = fresh();
            ThornTrap t = edge == 208f ? root(level) : tuft(level);
            place(level, edge - .5f);
            settle(level, 60 * 3);
            assertEquals(ThornTrap.Phase.DORMANT, t.phase(), "a hero standing just outside the zone never triggers it, edge " + edge);
            assertTrue(alive(level));
            place(level, edge + .5f);
            frame(level);
            assertEquals(ThornTrap.Phase.WARNING, t.phase(), "overlapping the zone by half a pixel triggers it, edge " + edge);
        }
    }

    @Test void theZoneReachesPastTheTrapToTheOtherSideToo() {
        // The root's zone ends at x=384. The body's left edge is its x + 18.
        Level inside = fresh();
        inside.player.position.x = 383.5f - 18;
        frame(inside);
        assertEquals(ThornTrap.Phase.WARNING, root(inside).phase(), "body left edge 0.5 px inside the right end");
        Level beyond = fresh();
        beyond.player.position.x = 384.5f - 18;
        settle(beyond, 5);
        assertEquals(ThornTrap.Phase.DORMANT, root(beyond).phase(), "0.5 px outside it");
    }

    @Test void aHeroStandingStillBesideTheTrapOutsideItsZoneIsSafeForever() {
        Level level = fresh();
        place(level, 207f);
        settle(level, 60 * 20);
        assertEquals(ThornTrap.Phase.DORMANT, root(level).phase());
        assertTrue(alive(level));
    }

    @Test void theWarningIsExactlyTheSharedD7LengthNormallyAndWithTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = fresh();
            int rootFrames = warningFrames(level, root(level), ROOT_X - 70);
            assertEquals(longer ? 1.2f : .8f, root(level).warningTotal(), 1e-6f, "literal shared value, longer=" + longer);
            assertEquals((longer ? 1.2f : .8f) * 60, rootFrames, 2, "root warning frames, longer=" + longer);
            Level other = fresh();
            int tuftFrames = warningFrames(other, tuft(other), TUFT_X - 70);
            assertEquals((longer ? 1.2f : .8f) * 60, tuftFrames, 2, "tuft warning frames, longer=" + longer);
            System.out.printf(java.util.Locale.ROOT, "longer=%s: root warns %.2f s, tuft warns %.2f s%n", longer, rootFrames * STEP, tuftFrames * STEP);
        }
        setLonger(false);
    }

    @Test void pausingStopsTheTimerAndResumingContinuesFromTheSameStateNotARestart() {
        // KimeriaGame stops calling Level.update while paused, so a pause is a run of frames with no update.
        Level paused = fresh(), straight = fresh();
        place(paused, ROOT_X - 70);
        place(straight, ROOT_X - 70);
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        float before = paused.objects.stream().filter(ThornTrap.class::isInstance).map(ThornTrap.class::cast)
            .filter(t -> t.kind() == ThornTrap.Kind.ROOT).findFirst().orElseThrow().warningRemaining();
        assertEquals(.8f - 20 * STEP, before, 1e-4f);
        // Ten seconds of pause: Level.update is simply not called, so there is nothing to run here.
        assertEquals(before, root(paused).warningRemaining(), 0f, "nothing advanced while paused");
        assertEquals(ThornTrap.Phase.WARNING, root(paused).phase());
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        assertEquals(root(straight).warningRemaining(), root(paused).warningRemaining(), 1e-6f, "resumed exactly where it stopped");
        for (int i = 0; i < 60 * 2; i++) { frame(paused); frame(straight); }
        assertEquals(root(straight).phase(), root(paused).phase());
        assertEquals(ThornTrap.Phase.ARMED, root(paused).phase());
    }

    @Test void aHeroWhoCannotBeHurtYetDoesNotTriggerOrKillLikeEveryOtherHazard() {
        // canLeaveRoom() is false while dying/resetting/arriving or after the checkpoint: the same guard as the
        // Deadline, enemy contact and the bomb's trigger in Player.checkGameplayOverlaps.
        Level level = fresh();
        place(level, ROOT_X + 11);
        settle(level, 60 * 2);
        assertFalse(alive(level));
        assertFalse(level.player.canLeaveRoom(), "dying");
        place(level, TUFT_X + 8);
        settle(level, 30);
        assertEquals(ThornTrap.Phase.DORMANT, tuft(level).phase(), "a dying hero does not trigger the next trap");
        assertEquals(ThornTrap.Phase.ARMED, root(level).phase(), "timers and states already running are untouched");
    }

    // -- root behaviour --

    @Test void theRootPeeksAndWobblesDuringTheWarningThenRisesThenStaysUpFlatOutFor10Seconds() {
        Level level = fresh();
        ThornTrap t = root(level);
        place(level, ROOT_X - 70);
        frame(level);
        java.util.Set<Integer> seen = new HashSet<>();
        while (t.phase() == ThornTrap.Phase.WARNING) { seen.add(t.frameIndex()); frame(level); }
        assertEquals(Set.of(1, 2), seen, "the tip wobbles between peek1 and peek2");
        assertEquals(ThornTrap.Phase.RISING, t.phase());
        assertEquals(3, t.frameIndex());
        assertEquals(new Rectangle(293, 309, 6, 11), t.lethalBounds(), "rising: only the lower half kills");
        settle(level, 10);
        assertEquals(ThornTrap.Phase.ARMED, t.phase());
        assertEquals(4, t.frameIndex());
        settle(level, 60 * 10);
        assertEquals(ThornTrap.Phase.ARMED, t.phase(), "stays up");
        assertTrue(t.isLethal());
    }

    @Test void nothingKillsBeforeTheWarningEndsEvenWithTheHeroStandingInTheColumn() {
        Level level = fresh();
        place(level, ROOT_X + 11); // body spans 293..304: straight over the column
        int frames = 0;
        while (alive(level) && frames < 60 * 3) { frame(level); frames++; }
        assertFalse(alive(level), "the hero in the column is killed once it rises");
        float seconds = frames * STEP;
        assertTrue(seconds >= .8f && seconds <= .8f + 2 * STEP + .001f, "dies when the warning ends, not before: " + seconds);
        assertEquals(ThornTrap.Phase.RISING, root(level).phase());
    }

    @Test void theRiseKillsOnlyOnOverlapWithTheColumnNotBesideIt() {
        // The lethal column is x 293..299. Body right edge 0.1 px short of it, and body left edge 0.1 px past it: safe.
        Level beside = fresh();
        arm(beside, root(beside), 292.9f);
        settle(beside, 60 * 5);
        assertTrue(alive(beside));
        Level past = fresh();
        place(past, 299.1f + 11);
        settle(past, 60 * 3);
        assertEquals(ThornTrap.Phase.ARMED, root(past).phase());
        assertTrue(alive(past), "just right of the column");
        for (float overlap : new float[]{.2f, 3f, 6f}) {
            Level touching = fresh();
            place(touching, 293f + overlap);
            settle(touching, 60 * 3);
            assertFalse(alive(touching), "overlapping by " + overlap + " px is a kill");
        }
    }

    @Test void leavingTheZoneBeforeTheRiseDoesNotCancelItAndItStaysUpAfterwards() {
        Level level = fresh();
        place(level, ROOT_X - 70);
        frame(level);
        assertEquals(ThornTrap.Phase.WARNING, root(level).phase());
        place(level, 80f);
        settle(level, 60 * 2);
        assertEquals(ThornTrap.Phase.ARMED, root(level).phase(), "once triggered it always completes the rise");
        place(level, 80f);
        settle(level, 60 * 5);
        assertEquals(ThornTrap.Phase.ARMED, root(level).phase());
        assertTrue(alive(level));
    }

    @Test void retryRestartAndRoomReentryResetTheTrapToHidden() {
        Level level = fresh();
        place(level, ROOT_X + 11);
        settle(level, 60 * 2);
        assertFalse(alive(level));
        assertEquals(ThornTrap.Phase.ARMED, root(level).phase());
        retry(level);
        assertEquals(ThornTrap.Phase.DORMANT, root(level).phase(), "retry rebuilds the room: hidden again");
        assertTrue(alive(level));
        assertEquals(0, root(level).frameIndex());
        assertEquals(ThornTrap.Phase.DORMANT, root(fresh()).phase(), "Restart Level builds the room again");
    }

    // -- tuft behaviour --

    @Test void theTuftBristlesHarmlesslyThenTurnsIntoLethalSpikesAndNeverRecalms() {
        Level level = fresh();
        ThornTrap t = tuft(level);
        place(level, TUFT_X - 70);
        frame(level);
        assertEquals(ThornTrap.Phase.WARNING, t.phase());
        assertEquals(1, t.frameIndex(), "bristle frame");
        assertFalse(t.isLethal(), "bristling does not hurt");
        place(level, 80f); // leave the zone mid-warning
        settle(level, 30);
        assertEquals(ThornTrap.Phase.WARNING, t.phase());
        assertEquals(1, t.frameIndex());
        settle(level, 60);
        assertEquals(ThornTrap.Phase.ARMED, t.phase(), "leaving does not re-calm it: deterministic");
        assertEquals(2, t.frameIndex(), "spiked frame");
        settle(level, 60 * 5);
        assertEquals(ThornTrap.Phase.ARMED, t.phase());
        place(level, TUFT_X + 8);
        settle(level, 3);
        assertFalse(alive(level), "the spiked tuft kills on overlap");
    }

    @Test void aCalmTuftHurtsNothingAWalkerCanStandOnItWhileItIsStillDormant() {
        Level level = fresh();
        ThornTrap t = tuft(level);
        assertFalse(t.isLethal());
        place(level, TUFT_X + 10);
        frame(level);
        assertTrue(alive(level));
        assertEquals(ThornTrap.Phase.WARNING, t.phase(), "standing in the zone starts it");
    }

    @Test void zonesCanBeAuthoredPerSideAndPerObject() {
        Level level = level("thorn-zones");
        assertEquals(new Rectangle(224, 264, 144, 56), root(level).triggerZone(), "zone=64 each side");
        assertEquals(new Rectangle(480, 272, 80, 48), tuft(level).triggerZone(), "one-sided: only the right, 64 px");
        place(level, 479f);
        settle(level, 60);
        assertEquals(ThornTrap.Phase.DORMANT, tuft(level).phase(), "the left side of a right-only tuft is not a zone");
        place(level, 223f);
        settle(level, 60);
        assertEquals(ThornTrap.Phase.DORMANT, root(level).phase());
        place(level, 225f);
        frame(level);
        assertEquals(ThornTrap.Phase.WARNING, root(level).phase());
    }

    // -- the hero can always pass: jumping, or running through, or waiting --

    /** A take-off from {@code gap} px before the trap (body right edge to the trap's left edge) across to its far side. */
    private boolean jumpPasses(Level level, float trapLeft, float trapRight, float gap) {
        place(level, trapLeft - gap);
        level.player.position.y = 272;
        settle(level, 2);
        for (int i = 0; i < 60 * 3 && alive(level); i++) {
            step(level, true, i == 0);
            if (bodyLeft(level) > trapRight + 4 && level.player.isOnGround()) return true;
        }
        return false;
    }

    @Test void aJumpClearsAnArmedRootFromEveryTakeoffInAWideWindowAndAnArmedTuftFromAnEvenWiderOne() {
        int rootLo = Integer.MAX_VALUE, rootHi = -1, tuftLo = Integer.MAX_VALUE, tuftHi = -1;
        for (int gap = 0; gap <= 90; gap++) {
            Level a = fresh(); arm(a, root(a), ROOT_X - 10);
            if (jumpPasses(a, 293, 299, gap)) { rootLo = Math.min(rootLo, gap); rootHi = Math.max(rootHi, gap); }
            Level b = fresh(); arm(b, tuft(b), TUFT_X - 10);
            if (jumpPasses(b, 482, 494, gap)) { tuftLo = Math.min(tuftLo, gap); tuftHi = Math.max(tuftHi, gap); }
        }
        System.out.printf("armed root cleared from gaps %d..%d px, armed tuft from %d..%d px%n", rootLo, rootHi, tuftLo, tuftHi);
        assertTrue(rootHi >= 0 && rootLo <= 14 && rootHi >= 50, "root window must be wide: " + rootLo + ".." + rootHi);
        assertTrue(tuftLo <= 6 && tuftHi >= 60, "tuft window must be wider: " + tuftLo + ".." + tuftHi);
        for (int gap = rootLo; gap <= rootHi; gap++) {
            Level a = fresh(); arm(a, root(a), ROOT_X - 10);
            assertTrue(jumpPasses(a, 293, 299, gap), "root window has a hole at gap " + gap);
        }
        for (int gap = tuftLo; gap <= tuftHi; gap++) {
            Level b = fresh(); arm(b, tuft(b), TUFT_X - 10);
            assertTrue(jumpPasses(b, 482, 494, gap), "tuft window has a hole at gap " + gap);
        }
    }

    @Test void aJumpAlsoClearsTheRootWhileItWobblesAndWhileItRises() {
        // Take off while the warning is still running (the zone starts it as the hero walks in).
        for (int gap = 20; gap <= 40; gap += 4) {
            Level level = fresh();
            place(level, 293 - gap);
            assertTrue(jumpPasses(level, 293, 299, gap), "gap " + gap);
            assertTrue(root(level).phase() != ThornTrap.Phase.DORMANT, "the jump itself started the warning");
        }
    }

    /** The mastered route: run right, jump when the next trap is {@code gap} px ahead. */
    private int route(Level level, int gap) {
        int frames = 0;
        while (bodyLeft(level) < 560 && frames < 60 * 20) {
            float r = bodyRight(level);
            boolean jump = level.player.isOnGround()
                && ((r >= 293 - gap && r < 293 - gap + 6) || (r >= 482 - gap && r < 482 - gap + 6));
            step(level, true, jump);
            assertTrue(alive(level), "died at " + level.player.position + " after " + frames);
            frames++;
        }
        assertTrue(bodyLeft(level) >= 560, "never got through");
        held.clear();
        return frames;
    }

    @Test void theRequiredRouteJumpsBothTrapsFromTheEntranceWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            int frames = route(fresh(), 30);
            System.out.printf(java.util.Locale.ROOT, "jump route, longer=%s: %.2f s%n", longer, frames * STEP);
            assertTrue(frames * STEP < 8f);
        }
        setLonger(false);
    }

    @Test void theRequiredRouteAlsoPassesByWaitingForTheRiseThenJumping() {
        Level level = fresh();
        place(level, ROOT_X - 40);
        settle(level, 60 * 2);
        assertEquals(ThornTrap.Phase.ARMED, root(level).phase());
        assertTrue(jumpPasses(level, 293, 299, 30), "wait for the stake to be fully up, then jump it");
    }

    @Test void walkingStraightThroughWithoutJumpingIsHitNormallyButSurvivedWithTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            Level level = fresh();
            int frames = 0;
            while (alive(level) && bodyLeft(level) < 330 && frames++ < 60 * 10) step(level, true, false);
            assertEquals(longer, alive(level),
                "default zone 80: crossing takes ~0.97 s, longer than 0.8 s but shorter than 1.2 s; longer=" + longer);
        }
        setLonger(false);
    }

    @Test void afterTheTrapIsUpAHeroBehindItCanAlwaysRetreatAndIsNeverStuckBetweenTrapAndWall() {
        Level level = fresh();
        arm(level, root(level), ROOT_X - 10);
        place(level, ROOT_X - 10);
        for (int i = 0; i < 60 * 3; i++) step(level, false, false);
        assertTrue(alive(level));
        for (int i = 0; i < 60 * 2; i++) { held.clear(); held.add(Input.Keys.A); frame(level); }
        assertTrue(bodyRight(level) < ROOT_X - 60, "can walk away from an armed root");
        held.clear();
        assertTrue(alive(level));
        assertTrue(jumpPasses(level, 293, 299, 30), "and the route is still open");
    }

    // -- authoring rules, enforced at load --

    @Test void aFloatingTrapFailsAtLoadWithAnErrorNamingTheObject() {
        var e = assertThrows(IllegalArgumentException.class, () -> level("thorn-floating"));
        assertTrue(e.getMessage().contains("ThornRoot") && e.getMessage().contains("[10]") && e.getMessage().contains("floor"), e.getMessage());
    }

    @Test void aTrapUnderALowCeilingOrInAOneTileCorridorFailsAtLoad() {
        var e = assertThrows(IllegalArgumentException.class, () -> level("thorn-corridor"));
        assertTrue(e.getMessage().contains("free height"), e.getMessage());
    }

    @Test void aZoneOverTheRoomEntranceFailsAtLoadBecauseTheHeroWouldStartTriggered() {
        var e = assertThrows(IllegalArgumentException.class, () -> level("thorn-entrance"));
        assertTrue(e.getMessage().contains("entrance"), e.getMessage());
    }

    private String loadError(String map) {
        return assertThrows(IllegalArgumentException.class, () -> level(map)).getMessage();
    }

    @Test void aTinyZoneFailsAtLoadBecauseTheHeroCouldNotReactInTime() {
        assertTrue(loadError("thorn-tiny-zone").contains("at least 64 px"));
    }

    @Test void unparsableNanInfiniteExponentAndNegativeZonesFailAtLoadInsteadOfBecomingTheDefault() {
        for (String map : new String[]{"far", "nan", "infinity", "1e2"})
            assertTrue(loadError("thorn-number-" + map).contains("is not a plain number"), map);
        assertTrue(loadError("thorn-number-_5").contains("not negative"));
    }

    @Test void thePitAPillarAnOverheadPlatformALiftAndAnObjectInTheColumnAllFailAtLoad() {
        assertTrue(loadError("thorn-pit").contains("open floor"), "floor ends 44 px past the root; the landing run needs 64");
        assertTrue(loadError("thorn-pillar").contains("free height"), "pillar beside it inside the jump envelope");
        assertTrue(loadError("thorn-overhead-platform").contains("free height"), "one-way platforms count");
        assertTrue(loadError("thorn-overhead-lift").contains("free height"), "an escalator counts with its travel");
        assertTrue(loadError("thorn-in-column").contains("own column"), "a falling platform in the lethal column");
    }

    @Test void theHeadroomTheAuthoringRuleAsksForFitsAFullJumpOverTheRoot() {
        assertEquals(96f, ThornTrap.HEADROOM, 0f);
        // The measured clearing jump tops out well under the headroom: hero body is 22 px tall and rises about 57 px.
        Level level = fresh();
        arm(level, root(level), ROOT_X - 10);
        place(level, 293 - 30);
        settle(level, 2);
        float top = level.player.hitboxBounds().y;
        for (int i = 0; i < 60; i++) { step(level, true, i == 0); top = Math.min(top, level.player.hitboxBounds().y); }
        assertTrue(320 - 22 - top <= 96 - 8, "jump apex " + (320 - 22 - top) + " px of rise fits the headroom");
    }
}
