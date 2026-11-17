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
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ember fissure (#366, D91) on the test map {@code fissure-test.tmx}: room 0 a fissure on open floor, room 1 a fissure
 * under a one-way branch on the step up to the exit ledge (the teaching recipe of {@code forest-fissure}). World Y grows
 * downward: the jet's top is the smaller Y, the floor line the larger. Scripted heroes use the ordinary controls.
 */
class EmberFissureTest {
    private static final float STEP = 1f / 60f, EPS = 1e-4f;
    private static final int FLOOR_ROOM = 0, STEP_ROOM = 1;
    private static final float FLOOR = 320f, LEDGE_TOP = 224f, BRANCH_TOP = 272f;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final List<String> sounds = new ArrayList<>();
    /** The camera's visible world rect the level reads: the whole fixture unless a test moves it (fissure sounds are view-gated). */
    private final Rectangle view = new Rectangle(0, 0, 1280, 368);
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
            case "isGameStarted", "playSounds" -> true;
            case "artifacts" -> artifacts;
            case "playSound" -> { sounds.add((String) args[0]); yield null; }
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    @BeforeEach void setUp() throws Exception {
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
        setLonger(false);
    }

    @AfterEach void restoreGlobals() throws Exception {
        setLonger(false);
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(warnings, longer);
    }

    // -- harness --

    private void frame(Level level) { level.update(STEP); pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private void run(Level level, float seconds) { settle(level, Math.round(seconds * 60)); }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    private Rectangle body(Level level) { return level.player.hitboxBounds(); }

    private Level room(int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "fissure-test", () -> view);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private static EmberFissure fissure(Level level) {
        return level.objects.stream().filter(EmberFissure.class::isInstance).map(EmberFissure.class::cast).findFirst().orElseThrow();
    }

    /** A fissure on its own (no level), top at y 316 so its floor line is 320. */
    private EmberFissure lone(float period, float phase) {
        return new EmberFissure(assets, 100, FLOOR - EmberFissure.FLOOR_OFFSET, warnings, period, phase,
            EmberFissure.DEFAULT_JET_SECONDS, EmberFissure.DEFAULT_HEIGHT);
    }

    private static void advance(EmberFissure f, float seconds) {
        for (int i = 0, n = Math.round(seconds / STEP); i < n; i++) f.update(STEP);
    }

    private static TiledObject object(float x, float y, String... props) {
        TiledObject o = new TiledObject();
        o.type = "EmberFissure"; o.x = x; o.y = y; o.width = EmberFissure.WIDTH; o.height = EmberFissure.HEIGHT;
        for (int i = 0; i + 1 < props.length; i += 2) o.properties.put(props[i], props[i + 1]);
        return o;
    }

    private static TiledObject block(String type, float x, float y, float w, float h) {
        TiledObject o = new TiledObject();
        o.type = type; o.x = x; o.y = y; o.width = w; o.height = h;
        return o;
    }

    // -- the objects --

    @Test void theFixtureHoldsTwoFissuresStandingOnTheFloorLineWithTheirFullColumns() {
        EmberFissure a = fissure(room(FLOOR_ROOM)), b = fissure(room(STEP_ROOM));
        Rectangle r = new Rectangle();
        for (EmberFissure f : new EmberFissure[]{a, b}) {
            assertEquals(FLOOR, f.floorY(), EPS, "the floor line is the object's top + 4 (4 rows of crust stand on it)");
            assertEquals(96f, f.columnHeight(), EPS, "a one-way branch does not cut the column");
            f.columnInto(r);
            assertEquals(f.centerX() - 4f, r.x, EPS);
            assertEquals(EmberFissure.JET_WIDTH, r.width, EPS);
            assertEquals(FLOOR - 96f, r.y, EPS, "the column's top is 96 px above the floor line (smaller Y)");
            assertEquals(FLOOR, r.y + r.height, EPS, "the column's bottom is the floor line");
        }
        assertEquals(LEDGE_TOP, FLOOR - b.columnHeight(), EPS, "the branch fissure's jet reaches the ledge top, 48 px over the branch");
        assertTrue(FLOOR - b.columnHeight() < BRANCH_TOP, "the jet passes through the branch");
    }

    @Test void theWarningIsTheSharedWarningWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            EmberFissure f = lone(3.5f, 0f);
            float expected = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
            assertEquals(longer ? 1.2f : 0.8f, f.warningSeconds(), EPS);
            float warningStart = 3.5f - 1f - expected;
            assertEquals(EmberFissure.Phase.CALM, f.phaseAt(warningStart - 0.01f));
            assertEquals(EmberFissure.Phase.WARNING, f.phaseAt(warningStart + 0.01f));
            assertEquals(EmberFissure.Phase.WARNING, f.phaseAt(2.5f - 0.01f));
            assertEquals(EmberFissure.Phase.JET, f.phaseAt(2.5f), "the jet starts at the same point of every cycle, assist or not");
            assertEquals(EmberFissure.Phase.JET, f.phaseAt(3.49f));
            // In play: the first warning is seen for its full length.
            int warningFrames = 0;
            for (int i = 0; i < 60 * 3.5f; i++) { f.update(STEP); if (f.currentPhase() == EmberFissure.Phase.WARNING) warningFrames++; }
            assertEquals(expected, warningFrames * STEP, 1.5f * STEP);
        }
    }

    @Test void theColumnRisesHoldsAndSinksAndIsLethalExactlyWhereItIsDrawn() {
        EmberFissure f = lone(3.5f, 0f);
        assertEquals(FLOOR, f.topAt(0f), EPS);
        assertEquals(FLOOR - 48f, f.topAt(0.05f), 0.01f, "halfway up after half the rise");
        assertEquals(FLOOR - 96f, f.topAt(0.1f), EPS);
        assertEquals(FLOOR - 96f, f.topAt(0.79f), EPS, "it holds until the sink");
        assertEquals(FLOOR - 48f, f.topAt(0.9f), 0.01f, "halfway down after half the sink");
        assertEquals(FLOOR, f.topAt(1f), EPS);
        Rectangle jet = new Rectangle();
        advance(f, 2.5f + 0.4f);  // in the hold
        assertTrue(f.jetActive());
        f.jetInto(jet);
        assertEquals(FLOOR - 96f, jet.y, EPS);
        assertEquals(FLOOR, jet.y + jet.height, EPS, "the column stands on the floor line");
        Rectangle hero = new Rectangle(f.centerX() - 5.5f, FLOOR - 22f, 11f, 22f);
        assertTrue(f.lethalTo(hero), "a hero standing over the crack is in the column");
        hero.x = f.centerX() + 4f;
        assertFalse(f.lethalTo(hero), "a hero touching the column's side edge but not inside it lives (overlaps is strict)");
        hero.x = f.centerX() + 4.5f;
        assertFalse(f.lethalTo(hero), "beside the 8 px column is safe while it burns");
        advance(f, 1f); // calm again
        assertFalse(f.jetActive());
        hero.x = f.centerX() - 5.5f;
        assertFalse(f.lethalTo(hero), "the crust itself never hurts");
    }

    @Test void aCycleWhoseWarningBeganBeforeTheRoomStartedStaysCalm() {
        EmberFissure f = lone(3.5f, 2.9f); // the room starts 0.4 s into a jet
        assertEquals(EmberFissure.Phase.CALM, f.currentPhase());
        boolean burned = false;
        for (int i = 0; i < 36; i++) { f.update(STEP); burned |= f.jetActive(); }
        assertFalse(burned, "no unannounced jet");
        EmberFissure g = lone(3.5f, 2.0f); // the room starts 0.3 s into a warning
        assertEquals(EmberFissure.Phase.CALM, g.currentPhase());
        advance(g, 3.5f - 2.0f + 1.7f + 0.05f);
        assertEquals(EmberFissure.Phase.WARNING, g.currentPhase(), "the next cycle warns in full");
    }

    @Test void theWarningAndJetSoundsPlayOncePerCycle() {
        Level level = room(FLOOR_ROOM);
        run(level, 3.5f * 3);
        assertEquals(3, sounds.stream().filter(Level.FISSURE_RUMBLE_SOUND::equals).count());
        assertEquals(3, sounds.stream().filter(Level.EMBER_JET_SOUND::equals).count());
    }

    @Test void aFissureOutsideTheCameraViewIsSilent() {
        view.set(2000, 0, 640, 368);
        Level level = room(FLOOR_ROOM);
        run(level, 3.5f * 2);
        assertFalse(sounds.contains(Level.FISSURE_RUMBLE_SOUND));
        assertFalse(sounds.contains(Level.EMBER_JET_SOUND));
        view.set(0, 0, 1280, 368);
        run(level, 3.5f);
        assertTrue(sounds.contains(Level.FISSURE_RUMBLE_SOUND) && sounds.contains(Level.EMBER_JET_SOUND));
    }

    @Test void everyJetLeavesFallingSparksHitOrMissAndThenTheSpentFrame() {
        EmberFissure f = lone(3.5f, 0f);
        advance(f, 2.5f + 0.81f);  // just into the sink
        assertTrue(f.sparks().count() >= EmberFissure.FALL_SPARKS, "the jet's own consequence (D93): sparks fall as it sinks");
        advance(f, 0.25f);         // the jet is over: 0.06 s into the next cycle
        assertFalse(f.jetActive());
        assertEquals(5, f.frameIndex(), "the sooted spent frame follows every jet");
        advance(f, 0.5f);
        assertTrue(f.frameIndex() <= 1, "then the rest pulse");
    }

    // -- the counters --

    /** Walks right from the room start; after {@code delay} s of standing, waits short of the column until a jet ends. */
    private boolean timedPass(Level level, float delay) {
        EmberFissure f = fissure(level);
        run(level, delay);
        boolean go = false;
        for (int i = 0; i < 60 * 12 && alive(level) && level.player.position.x < f.centerX() + 60; i++) {
            held.clear();
            float front = body(level).x + body(level).width;
            boolean close = front > f.centerX() - 4f - 12f;
            if (!go && close) go = f.currentPhase() == EmberFissure.Phase.CALM && !f.jetActive() && f.cycleTime() < 1f;
            if (go || !close) held.add(Input.Keys.D);
            frame(level);
        }
        held.clear();
        return alive(level);
    }

    @Test void aWalkerWhoGoesWhenAJetEndsPassesAtEveryOffsetWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < 3.5f; delay += 0.05f) assertTrue(timedPass(room(FLOOR_ROOM), delay), "delay " + delay + " longer " + longer);
        }
    }

    @Test void aWalkerWhoIgnoresTheFissureDiesInSomePhasesAndPassesInOthers() {
        int died = 0, passed = 0;
        for (float delay = 0f; delay < 3.5f; delay += 0.05f) {
            Level level = room(FLOOR_ROOM);
            EmberFissure f = fissure(level);
            run(level, delay);
            for (int i = 0; i < 60 * 4 && alive(level) && level.player.position.x < f.centerX() + 60; i++) { held.clear(); held.add(Input.Keys.D); frame(level); }
            held.clear();
            if (alive(level)) passed++; else died++;
        }
        System.out.println("ember fissure, blind walk: " + passed + " passed, " + died + " died");
        assertTrue(died > 0, "the jet kills a careless walker");
        assertTrue(passed > died, "the open window (2.5 s of 3.5) is most of the cycle");
    }

    /**
     * The step room's route: wait on the floor short of the branch until a jet ends, then jump on to the branch, through
     * the column, and on to the ledge. Returns the seconds from the start to the ledge, or NaN on a death or no arrival.
     */
    private float branchClimb(Level level, float delay) {
        EmberFissure f = fissure(level);
        run(level, delay);
        float t = delay;
        boolean go = false, first = false, second = false;
        for (int i = 0; i < 60 * 14 && alive(level); i++) {
            held.clear();
            Rectangle b = body(level);
            boolean onLedge = level.player.isOnGround() && b.y + b.height <= LEDGE_TOP + 0.5f && b.x > 1056f;
            if (onLedge) return t;
            float waitX = 932f;
            if (!go && b.x + b.width >= waitX) go = f.currentPhase() == EmberFissure.Phase.CALM && !f.jetActive() && f.cycleTime() < 0.5f;
            if (go || b.x + b.width < waitX) held.add(Input.Keys.D);
            if (go && !first && level.player.isOnGround() && b.x + b.width >= 942f) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); first = true; }
            else if (first && !second && level.player.isOnGround() && b.y + b.height <= BRANCH_TOP + 0.5f && b.x + b.width >= 1012f) {
                held.add(Input.Keys.J); pressed.add(Input.Keys.J); second = true;
            } else if (first && !level.player.isOnGround()) held.add(Input.Keys.J); // a full jump: held while airborne, released on landing
            frame(level);
            t += STEP;
        }
        return Float.NaN;
    }

    @Test void aClimberWhoGoesWhenAJetEndsCrossesTheBranchAtEveryOffsetWithAndWithoutTheAssist() throws Exception {
        float worst = 0f, best = Float.MAX_VALUE;
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < 3.5f; delay += 0.05f) {
                float t = branchClimb(room(STEP_ROOM), delay);
                assertFalse(Float.isNaN(t), "delay " + delay + " longer " + longer);
                worst = Math.max(worst, t - delay); best = Math.min(best, t - delay);
            }
        }
        System.out.println("ember fissure, branch climb after the delay: " + best + " to " + worst + " s");
    }

    @Test void aHeroWaitingOnTheBranchBesideTheColumnLives() {
        EmberFissure f = lone(3.5f, 0f);
        Rectangle hero = new Rectangle(976f, BRANCH_TOP - 22f, 11f, 22f); // the branch's left end, x 976-987
        float column = 100 + EmberFissure.WIDTH / 2f - EmberFissure.JET_WIDTH / 2f;
        hero.x = column - 11f - 1f;
        for (int i = 0; i < 60 * 7; i++) { f.update(STEP); assertFalse(f.lethalTo(hero)); }
    }

    // -- reset, pause, determinism --

    @Test void aRespawnBuildsFromTheLoadCheckWithoutParsingTheObjectAgain() {
        Level level = room(STEP_ROOM);
        TiledObject o = level.map.layer("SpawnPoints").stream().filter(t -> "EmberFissure".equals(t.type) && t.x > 640).findFirst().orElseThrow();
        o.properties.put("period", "1"); // would fail the timing rule if the spawn parsed and checked it again
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        EmberFissure f = fissure(level);
        assertEquals(EmberFissure.DEFAULT_PERIOD_SECONDS, f.period(), EPS, "the respawn uses the values checked at load");
        assertEquals(1.75f, f.phase(), EPS);
        assertEquals(96f, f.columnHeight(), EPS);
    }

    @Test void aRetryRebuildsTheFissureAtItsAuthoredPhase() {
        Level level = room(FLOOR_ROOM);
        run(level, 1.3f);
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        EmberFissure f = fissure(level);
        assertEquals(0f, f.cycleTime(), EPS, "the clock restarts at phase 0");
        assertFalse(f.jetActive());
        assertEquals(1.75f, fissure(room(STEP_ROOM)).cycleTime(), EPS, "a new level (Restart Level) starts at the authored phase");
    }

    @Test void pauseFreezesItBecauseOnlyUpdateAdvancesTheClock() {
        EmberFissure f = lone(3.5f, 0f);
        advance(f, 2.6f);
        float c = f.cycleTime();
        Rectangle a = f.jetInto(new Rectangle());
        // no update: nothing moves
        assertEquals(c, f.cycleTime(), 0f);
        assertEquals(a, f.jetInto(new Rectangle()));
    }

    @Test void twoRunsAreTheSame() {
        EmberFissure a = lone(3.5f, 0.7f), b = lone(3.5f, 0.7f);
        Rectangle ra = new Rectangle(), rb = new Rectangle();
        int peak = 0;
        for (int i = 0; i < 60 * 9; i++) {
            a.update(STEP); b.update(STEP);
            assertEquals(a.jetInto(ra), b.jetInto(rb));
            assertEquals(a.sparks().count(), b.sparks().count());
            assertEquals(a.frameIndex(), b.frameIndex());
            peak = Math.max(peak, a.sparks().count());
        }
        System.out.println("ember fissure sparks: peak " + peak + " of " + EmberFissure.SPARK_CAPACITY);
        assertTrue(peak < EmberFissure.SPARK_CAPACITY, "a full pool drops the oldest spark silently: peak " + peak);
    }

    // -- load checks --

    @Test void theAuthoringRulesNameTheProblem() {
        assertNull(EmberFissure.validate(3.5f, 0f, 1f, 96f));
        assertNull(EmberFissure.validate(3.0f, 0f, 1f, 96f), "period = jet + 1.2 + 0.8 exactly");
        assertTrue(EmberFissure.validate(2.9f, 0f, 1f, 96f).contains("shorter than jet"));
        assertTrue(EmberFissure.validate(3.5f, 0f, 0.5f, 96f).contains("jet 0.5"));
        assertTrue(EmberFissure.validate(3.5f, 0f, 1f, 100f).contains("multiple of 16"));
        assertTrue(EmberFissure.validate(3.5f, 0f, 1f, 144f).contains("multiple of 16"));
        assertTrue(EmberFissure.validate(Float.NaN, 0f, 1f, 96f).contains("finite"));
    }

    @Test void aSolidBlockCutsTheColumnAndAOneWayPlatformDoesNot() {
        TiledObject o = object(100, 316);
        List<TiledObject> blocks = new ArrayList<>(List.of(block("Platform", 96, 272, 32, 16)));
        assertTrue(Float.isNaN(EmberFissure.ceilingAbove(o, 96f, blocks)));
        blocks.add(block("", 90, 200, 40, 40)); // bottom edge 240: 80 px above the floor line
        assertEquals(240f, EmberFissure.ceilingAbove(o, 96f, blocks), EPS);
        assertEquals(80f, EmberFissure.columnHeight(o, 96f, 240f), EPS);
        blocks.add(block("", 90, 260, 40, 40)); // bottom 300: the column would be 20 px
        assertThrows(IllegalArgumentException.class, () -> EmberFissure.fromMap(assets, o, warnings, blocks, "test"));
        List<TiledObject> beside = List.of(block("", 120, 200, 40, 40)); // right of the column (x 104-112)
        assertTrue(Float.isNaN(EmberFissure.ceilingAbove(o, 96f, beside)));
    }

    @Test void theFloorMustBeSolidUnderAllSixteenPixelsAtTheFloorLine() {
        TiledObject o = object(100, 316);
        assertNull(EmberFissure.floorProblem(o, List.of(new Rectangle(0, 320, 640, 48))));
        assertNull(EmberFissure.floorProblem(o, List.of(new Rectangle(0, 320.4f, 108, 48), new Rectangle(108, 320, 200, 48))), "split floors merge");
        assertNotNull(EmberFissure.floorProblem(o, List.of(new Rectangle(0, 320, 108, 48))), "half over a gap");
        assertNotNull(EmberFissure.floorProblem(o, List.of(new Rectangle(0, 336, 640, 48))), "floating 16 px over the floor");
    }

    @Test void unknownPropertiesAndWrongSizesFailTheLoad() {
        TiledObject typo = object(100, 316, "perod", "4");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> EmberFissure.fromMap(assets, typo, warnings, List.of(), "t"))
            .getMessage().contains("unknown property 'perod'"));
        TiledObject big = object(100, 316);
        big.width = 24;
        assertThrows(IllegalArgumentException.class, () -> EmberFissure.fromMap(assets, big, warnings, List.of(), "t"));
        TiledObject moving = object(100, 316);
        assertTrue(EmberFissure.movingSolidProblem(moving, List.of(block("Escalator", 96, 250, 32, 8)), 96f).contains("Escalator"));
        assertNull(EmberFissure.movingSolidProblem(moving, List.of(block("Escalator", 96, 200, 32, 8)), 96f), "above the column's top");
    }
}
