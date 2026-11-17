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
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Fireball;
import com.jlogicsoftware.kimeria.entity.objects.HangingHive;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
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
 * The hanging hive (#365, D90) on the test map {@code hive-test.tmx}: room 0 a hive over open floor with a stone, room 1 a
 * hive over a ladder up to a ledge, room 2 a hive level with a ledge (for the fireball). World Y grows downward: the band's
 * head falls to larger Y, and the floor is the largest Y it reaches. Scripted heroes use the ordinary controls.
 */
class HangingHiveTest {
    private static final float STEP = 1f / 60f, EPS = 1e-4f;
    private static final int FLOOR_ROOM = 0, LADDER_ROOM = 1, LEDGE_ROOM = 2;
    private static final float FLOOR = 320f, LEDGE_TOP = 208f;

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final List<String> sounds = new ArrayList<>();
    /** The camera's visible world rect the level reads: the whole fixture unless a test moves it (hive sounds are view-gated). */
    private final Rectangle view = new Rectangle(0, 0, 1920, 368);
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
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), "hive-test", () -> view);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }

    private static HangingHive hive(Level level) {
        return level.objects.stream().filter(HangingHive.class::isInstance).map(HangingHive.class::cast).findFirst().orElseThrow();
    }

    private static Carryable stone(Level level) {
        return level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
    }

    /** A hive on its own (no level): band 94 px deep under a 4 s period, unless given. */
    private HangingHive lone(float period, float phase, float y, float floor) {
        return new HangingHive(assets, 100, y, warnings, period, phase, HangingHive.DEFAULT_CURTAIN_SECONDS, floor);
    }

    private static void advance(HangingHive h, float seconds) {
        for (int i = 0, n = Math.round(seconds / STEP); i < n; i++) h.update(STEP);
    }

    // -- the objects --

    @Test void theFixtureHoldsThreeHivesWhoseBandsStopOnTheFirstSolidUnderTheirMouths() {
        HangingHive floor = hive(room(FLOOR_ROOM)), ladder = hive(room(LADDER_ROOM)), ledge = hive(room(LEDGE_ROOM));
        assertEquals(258f, floor.mouthY(), EPS, "the mouth is 26 px under the object's top");
        assertEquals(FLOOR, floor.floorY(), EPS);
        assertEquals(FLOOR, ladder.floorY(), EPS, "a ladder is not solid: the band runs down it to the floor");
        assertEquals(FLOOR, ledge.floorY(), EPS, "the ledge is beside the band, not under it");
        assertEquals(4.5f, ladder.period(), EPS);
        assertFalse(floor.bandActive(), "nothing is in the air when the room starts");
    }

    @Test void theWarningIsTheSharedWarningWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            HangingHive h = lone(4f, 0f, 232f, FLOOR);
            float warning = warnings.duration(TrapWarnings.DEFAULT_WARNING_SECONDS);
            assertEquals(longer ? 1.2f : 0.8f, warning, EPS);
            assertEquals(warning, h.warningSeconds(), EPS);
            assertEquals(HangingHive.Phase.CALM, h.phaseAt(3f - warning - 0.01f));
            assertEquals(HangingHive.Phase.WARNING, h.phaseAt(3f - warning + 0.01f));
            assertEquals(HangingHive.Phase.WARNING, h.phaseAt(2.99f));
            assertEquals(HangingHive.Phase.RELEASE, h.phaseAt(3f), "the release takes the last curtain second, assist or not");
            assertEquals(HangingHive.Phase.RELEASE, h.phaseAt(3.99f));
        }
    }

    @Test void theBandFallsAtTheStingerSpeedAndEveryHeightIsLethalForExactlyTheCurtain() {
        HangingHive h = lone(4f, 0f, 232f, FLOOR); // mouth 258, band 62 px
        advance(h, 3f);                              // the release starts now
        Rectangle band = new Rectangle();
        h.update(STEP); h.bandInto(band);
        assertTrue(h.bandActive());
        assertEquals(258f, band.y, EPS, "Y down: the band starts at the mouth");
        assertEquals(258f + HangingHive.FALL_SPEED * STEP * 1, band.y + band.height, 0.5f, "and its head is one frame of fall below it");
        assertEquals(HangingHive.BAND_WIDTH, band.width, EPS);
        assertEquals(h.centerX() - HangingHive.BAND_WIDTH / 2f, band.x, EPS);
        float head = band.y + band.height;
        h.update(STEP); h.bandInto(band);
        assertTrue(band.y + band.height > head, "the head moves to larger Y: it falls");
        // Probe two heights (one row each): lethal from depth / speed after the release for exactly the curtain.
        for (float depth : new float[]{4f, 58f}) {
            HangingHive p = lone(4f, 0f, 232f, FLOOR);
            Rectangle probe = new Rectangle(p.centerX() - 1f, p.mouthY() + depth, 2f, 1f);
            float first = -1, last = -1;
            for (int i = 0; i < 60 * 8; i++) {
                p.update(STEP);
                float t = (i + 1) * STEP;
                if (p.lethalTo(probe)) { if (first < 0) first = t; last = t; }
                if (t > 4f + 1f) break;
            }
            assertEquals(3f + depth / HangingHive.FALL_SPEED, first, 2 * STEP, "first lethal frame at depth " + depth);
            assertEquals(1f, last - first, 2 * STEP, "lethal for the curtain at depth " + depth);
        }
    }

    @Test void theBandNeverPassesTheSolidAndTheHiveItselfNeverHurts() {
        HangingHive h = lone(4f, 0f, 232f, FLOOR);
        Rectangle band = new Rectangle(), hiveBody = h.bodyInto(new Rectangle());
        Rectangle beside = new Rectangle(h.centerX() + HangingHive.BAND_WIDTH / 2f, FLOOR - 22f, 11f, 22f);
        for (int i = 0; i < 60 * 9; i++) {
            h.update(STEP);
            if (h.bandActive()) {
                h.bandInto(band);
                assertTrue(band.y >= h.mouthY() - EPS && band.y + band.height <= FLOOR + EPS, "inside mouth..floor: " + band);
            }
            assertFalse(h.lethalTo(hiveBody), "the paper body is harmless");
            assertFalse(h.lethalTo(beside), "a hero right beside the band is safe");
        }
    }

    @Test void theWarningAndReleaseSoundsPlayOncePerCycle() {
        Level level = room(FLOOR_ROOM);
        sounds.clear();
        run(level, 8f);
        assertEquals(2, sounds.stream().filter("hive_buzz"::equals).count());
        assertEquals(2, sounds.stream().filter("stinger_rain"::equals).count());
    }

    @Test void aHiveOutsideTheCameraViewIsSilent() {
        view.set(1280, 0, 640, 368); // the ledge room, far from the floor hive
        Level level = room(FLOOR_ROOM);
        sounds.clear();
        run(level, 8f);
        assertTrue(sounds.isEmpty(), "an off-screen hive must not buzz every period: " + sounds);
    }

    // -- the counter: time the pass --

    /** Waits {@code delay} s at the entrance (a phase offset), then holds Right past the band. */
    private boolean walkAcross(Level level, float delay, float pastX) {
        held.clear();
        run(level, delay);
        for (int i = 0; i < 60 * 12; i++) {
            held.clear(); held.add(Input.Keys.D);
            frame(level);
            if (!alive(level)) { held.clear(); return false; }
            if (body(level).x > pastX) { held.clear(); return true; }
        }
        held.clear();
        return fail("never crossed, hero at " + level.player.position);
    }

    /** Ordinary play: walk up to the band, wait outside it while it is in the air or about to be, then walk on. */
    private boolean timedPass(Level level, float delay) {
        HangingHive h = hive(level);
        float bandLeft = h.centerX() - HangingHive.BAND_WIDTH / 2f;
        held.clear();
        run(level, delay);
        for (int i = 0; i < 60 * 20; i++) {
            held.clear();
            Rectangle b = body(level);
            boolean beforeBand = b.x + b.width <= bandLeft - 2f;
            boolean wait = beforeBand && b.x + b.width >= bandLeft - 12f && (h.bandActive() || h.currentPhase() != HangingHive.Phase.CALM);
            if (!wait) held.add(Input.Keys.D);
            frame(level);
            if (!alive(level)) return false;
            if (body(level).x > h.centerX() + 40f) return true;
        }
        return fail("never crossed, hero at " + level.player.position);
    }

    @Test void aWalkerWhoReadsTheHivePassesAtEveryPhaseWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < 4f; delay += 0.05f)
                assertTrue(timedPass(room(FLOOR_ROOM), delay), "longer=" + longer + " delay " + delay);
        }
    }

    @Test void aWalkerWhoIgnoresTheHiveDiesInSomePhasesAndPassesInOthers() {
        int deaths = 0, passes = 0;
        for (float delay = 0f; delay < 4f; delay += 0.05f) {
            Level level = room(FLOOR_ROOM);
            if (walkAcross(level, delay, hive(level).centerX() + 40f)) passes++; else deaths++;
        }
        assertTrue(deaths > 0, "the band is lethal to a walker");
        assertTrue(passes > deaths, "most of the cycle is calm at the floor: " + passes + " passes, " + deaths + " deaths");
    }

    // -- the counter on a ladder: the band runs down it --

    /** The climber starts only this early in the cycle: the last stinger left the foot at (198 px / 240 px/s) 0.83 s. */
    private static final float CLIMB_CUE_SECONDS = 1.2f;

    /** Walks to the ladder's foot (outside the band), waits until the band is gone and the hive calm, climbs, steps onto the ledge. */
    private float[] climbPass(Level level, float delay) {
        HangingHive h = hive(level);
        float bandLeft = h.centerX() - HangingHive.BAND_WIDTH / 2f;
        held.clear();
        run(level, delay);
        int frames = 0;
        while (body(level).x + body(level).width < bandLeft - 6f) {
            held.clear(); held.add(Input.Keys.D); frame(level);
            assertTrue(alive(level) && ++frames < 60 * 20, "walk to the ladder");
        }
        held.clear();
        // The cue: start right after a curtain has passed the foot (or at once when the room has just begun its calm).
        while (h.bandActive() || h.currentPhase() != HangingHive.Phase.CALM || h.cycleTime() > CLIMB_CUE_SECONDS) {
            frame(level);
            assertTrue(alive(level) && ++frames < 60 * 30, "wait at the foot");
        }
        float start = frames * STEP, calmLeft = h.period() - h.curtainSeconds() - h.warningSeconds() - h.cycleTime();
        int climb = 0;
        while (!level.atRoomExit()) {
            held.clear(); held.add(Input.Keys.W);
            if (level.player.position.y + 48 <= LEDGE_TOP + 1 && !level.player.onLadder()) { held.clear(); held.add(Input.Keys.D); }
            else if (!level.player.onLadder()) held.add(Input.Keys.D);
            frame(level);
            climb++;
            if (!alive(level)) return null;
            assertTrue(climb < 60 * 15, "climb stuck at " + level.player.position);
        }
        return new float[]{start, climb * STEP, calmLeft};
    }

    @Test void aClimberWhoStartsInTheCalmPassesTheBandOnTheLadderAtEveryPhase() throws Exception {
        float longest = 0;
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float delay = 0f; delay < 4.5f; delay += 0.05f) {
                float[] r = climbPass(room(LADDER_ROOM), delay);
                assertNotNull(r, "longer=" + longer + " delay " + delay + ": killed on the ladder");
                longest = Math.max(longest, r[1]);
            }
        }
        System.out.println("hive ladder: longest climb from the band's end to the exit " + longest + " s");
    }

    @Test void aClimberWhoStartsAsTheReleaseBeginsIsKilledOnTheLadder() {
        Level level = room(LADDER_ROOM);
        HangingHive h = hive(level);
        level.player.position.set(h.centerX() - 24f, 272);  // body centred on the ladder
        held.clear();
        while (h.currentPhase() != HangingHive.Phase.RELEASE) frame(level);
        boolean died = false;
        for (int i = 0; i < 60 * 3 && !died; i++) { held.clear(); held.add(Input.Keys.W); frame(level); died = !alive(level); }
        assertTrue(died, "the band covers the whole ladder");
    }

    /** The ladder's window: from the moment the band has left the foot to the moment the next head reaches the ledge. */
    @Test void theLadderWindowIsMeasuredAgainstTheClimb() {
        HangingHive h = hive(room(LADDER_ROOM));
        float depth = FLOOR - h.mouthY(), drain = depth / HangingHive.FALL_SPEED;
        float headAtLedge = (LEDGE_TOP - 22f - h.mouthY()) / HangingHive.FALL_SPEED;
        float window = h.period() - h.curtainSeconds() - drain + headAtLedge;
        assertEquals(198f, depth, EPS);
        System.out.println("hive ladder: window " + window + " s (period " + h.period() + ", drain " + drain + " s)");
        assertTrue(window > 2.5f, "window " + window);
    }

    // -- the optional answer: knock it down --

    /** Picks up the stone, walks to {@code from} (body right edge) and throws it. */
    private void throwStoneFrom(Level level, float from) {
        level.player.position.set(108 - 29, 272);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        assertSame(stone(level), level.player.carriedObject(), "picked up the stone");
        int f = 0;
        while (body(level).x + body(level).width < from && f++ < 60 * 10) {
            HangingHive h = hive(level);
            if (h.bandActive() && body(level).x + body(level).width > h.centerX() - 20f) { held.clear(); frame(level); continue; }
            held.clear(); held.add(Input.Keys.D); frame(level);
        }
        held.clear();
        settle(level, 20);
        // A standing throw rises only about 10 px (THROW_UPWARD_SPEED 110, GRAVITY 600): the hive is reached by a jump-throw.
        held.add(Input.Keys.J); pressed.add(Input.Keys.J); frame(level); held.clear(); settle(level, JUMP_THROW_LEAD);
        held.add(Input.Keys.L); pressed.add(Input.Keys.L); frame(level);
        held.clear();
    }

    /** Frames after the jump key before the throw: the stone leaves the hand at about y 240 and tops out near y 230. */
    private static final int JUMP_THROW_LEAD = 10;

    @Test void aThrownStoneKnocksTheHiveDownIsNotConsumedAndNoStingerFallsAfterwards() {
        int hits = 0;
        float nearest = Float.MAX_VALUE, farthest = 0;
        for (float from = 120; from <= 296; from += 4) {
            Level level = room(FLOOR_ROOM);
            throwStoneFrom(level, from);
            int ammo = level.rangedStock.count();
            settle(level, 60);
            HangingHive h = hive(level);
            if (!h.isKnocked()) continue;
            hits++;
            nearest = Math.min(nearest, from); farthest = Math.max(farthest, from);
            assertEquals(ammo, level.rangedStock.count(), "a stone costs no charge");
            run(level, 1f); assertTrue(stone(level).available(), "the stone dropped and lies at rest (D39)");
            assertFalse(stone(level).isRemoved(), "not consumed");
            run(level, 2f);
            assertTrue(h.hasLanded(), "the hive lies on the floor");
            for (int i = 0; i < 60 * 10; i++) { frame(level); assertFalse(h.bandActive(), "no stinger leaves a knocked hive"); }
        }
        System.out.println("hive stone: " + hits + " hits, thrown from body right edge " + nearest + " to " + farthest);
        assertTrue(hits >= 3, "a stone thrown from the right range reaches the hive (found " + hits + ")");
    }

    @Test void aFireballFromTheLedgeKnocksTheHiveDownAndIsSpent() {
        Level level = room(LEDGE_ROOM);
        HangingHive h = hive(level);
        level.player.position.set(1500 - 29, 160);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        settle(level, 20);
        int ammo = level.rangedStock.count();
        assertTrue(ammo > 0, "the fixture hero has a charge");
        held.clear(); held.add(Input.Keys.L); pressed.add(Input.Keys.L); frame(level); held.clear();
        assertEquals(ammo - 1, level.rangedStock.count(), "one charge is paid");
        int frames = 0;
        while (!h.isKnocked() && frames++ < 60) frame(level);
        assertTrue(h.isKnocked(), "the fireball reached the hive");
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance), "the fireball is spent");
        assertTrue(sounds.contains("hive_fall"));
    }

    @Test void stingersAlreadyFallingFinishTheirFallAfterAKnockDown() {
        HangingHive h = lone(4f, 0f, 232f, FLOOR);
        advance(h, 3.3f);
        assertTrue(h.bandActive());
        assertTrue(h.knockDown());
        assertFalse(h.knockDown(), "a second hit flies on");
        Rectangle band = new Rectangle();
        float lastTop = h.bandInto(band).y;
        int frames = 0;
        while (h.bandActive()) {
            h.update(STEP);
            if (h.bandActive()) { h.bandInto(band); assertTrue(band.y > lastTop, "the tail falls"); lastTop = band.y; }
            assertTrue(++frames < 60, "drains in (floor - mouth) / speed");
        }
        assertEquals((FLOOR - 258f) / HangingHive.FALL_SPEED, frames * STEP, 2 * STEP);
        advance(h, 10f);
        assertFalse(h.bandActive());
        assertTrue(h.hasLanded());
    }

    // -- reset, pause, determinism --

    @Test void aRetryRebuildsTheHiveHangingAtItsAuthoredPhase() {
        Level level = room(FLOOR_ROOM);
        hive(level).knockDown();
        run(level, 1.3f);
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        HangingHive h = hive(level);
        assertFalse(h.isKnocked());
        assertEquals(0f, h.cycleTime(), EPS);
        Level fresh = room(LEDGE_ROOM);
        assertEquals(1f, hive(fresh).cycleTime(), EPS, "a new level (Restart Level) starts at the authored phase");
    }

    @Test void pauseFreezesItBecauseOnlyUpdateAdvancesTheClock() {
        HangingHive h = lone(4f, 0f, 232f, FLOOR);
        advance(h, 3.4f);
        float cycle = h.cycleTime();
        Rectangle band = new Rectangle(h.bandInto(new Rectangle()));
        for (int i = 0; i < 10; i++) h.render(new com.jlogicsoftware.kimeria.testsupport.NoopBatch());
        assertEquals(cycle, h.cycleTime(), 0f);
        assertEquals(band, h.bandInto(new Rectangle()));
    }

    @Test void twoRunsAreTheSame() {
        HangingHive a = lone(4f, 0.7f, 232f, FLOOR), b = lone(4f, 0.7f, 232f, FLOOR);
        Rectangle ra = new Rectangle(), rb = new Rectangle();
        for (int i = 0; i < 60 * 9; i++) {
            a.update(STEP); b.update(STEP);
            assertEquals(a.bandInto(ra), b.bandInto(rb));
            assertEquals(a.specks().count(), b.specks().count());
        }
        assertTrue(a.specks().count() > 0, "the specks drift");
    }

    // -- load checks --

    @Test void theAuthoringRulesNameTheProblem() {
        assertNull(HangingHive.validate(4f, 1f, 0f, 62f));
        assertTrue(HangingHive.validate(3.3f, 1f, 0f, 94f).contains("shorter than curtain"));
        assertTrue(HangingHive.validate(4f, 1f, 0f, 20f).contains("minimum is 32"));
        assertTrue(HangingHive.validate(4f, 0f, 0f, 62f).contains("curtain must be positive"));
        assertTrue(HangingHive.validate(Float.NaN, 1f, 0f, 62f).contains("finite"));
        assertTrue(HangingHive.validate(4f, 1f, 0f, Float.NaN).contains("no solid"));
    }

    @Test void theSpeckPoolNeverFillsUpAtAnyKnockDownMoment() {
        int peak = 0;
        for (float knockAt = 0f; knockAt < 4f; knockAt += 0.05f) {
            HangingHive h = lone(4f, 0f, 232f, FLOOR);
            for (int i = 0; i < 60 * 6; i++) {
                if (Math.abs(i * STEP - (2f + knockAt)) < STEP / 2f) h.knockDown();
                h.update(STEP);
                peak = Math.max(peak, h.specks().count());
            }
        }
        System.out.println("hive specks: peak " + peak + " of " + HangingHive.SPECK_CAPACITY);
        assertTrue(peak < HangingHive.SPECK_CAPACITY, "a full pool drops the oldest speck silently: peak " + peak);
    }

    /** The lowest drawn row of a frame in the authored grid (the source of trap-hive.png). */
    private static int lowestRow(String frameName) throws java.io.IOException {
        java.nio.file.Path root = java.nio.file.Path.of("").toAbsolutePath();
        while (!java.nio.file.Files.exists(root.resolve("art/objects/forest-traps/hive.grid"))) root = root.getParent();
        List<String> lines = java.nio.file.Files.readAllLines(root.resolve("art/objects/forest-traps/hive.grid"));
        int start = lines.indexOf("frame " + frameName) + 1, lowest = -1;
        for (int row = 0; row < HangingHive.HEIGHT; row++) if (lines.get(start + row).chars().anyMatch(c -> c != '.')) lowest = row;
        return lowest;
    }

    /** The fall stops with the falling frame's drawn bottom on the floor (HangingHive.fall), so both frames end on one line: no jump. */
    @Test void theKnockedHiveLiesOnTheFloorLineAndFallsOntoItWithoutAJump() throws Exception {
        // Y down: a frame drawn at top y covers y .. y + 32; its lowest drawn row r ends at y + r + 1.
        assertEquals(lowestRow("fallen") + 1, HangingHive.FALLEN_BOTTOM, 0f, "FALLEN_BOTTOM is the fallen frame's drawn bottom edge");
        assertEquals(lowestRow("falling") + 1, HangingHive.FALLING_BOTTOM, 0f, "FALLING_BOTTOM is the falling frame's drawn bottom edge");
        HangingHive h = lone(4f, 0f, 232f, FLOOR);
        h.knockDown();
        advance(h, 2f);
        assertTrue(h.hasLanded());
        assertEquals(FLOOR, h.fallenTop() + HangingHive.FALLEN_BOTTOM, 0f, "the fallen hive's drawn bottom is the floor line, not 1 px above it");
    }
}
