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
import com.jlogicsoftware.kimeria.entity.objects.ExplosiveTrap;
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
 * Explosive traps (D47, #233): the exploding stump and the exploding root that replace the glass-bottle bomb. Driven
 * headlessly through real Level updates in the test-resource maps (explosive-*.tmx) with ordinary keyboard controls.
 * Times are the shared D7 warning (0.8 s, 1.2 s with the longer-warnings assist). Recipe: docs/ROOMS.md.
 *
 * <p>Fixture explosive-test.tmx: room 640 wide, floor y 320, hero enters at x 32 (body x 50..61), stump at x 288
 * (blast square x 264..328), root at x 480 (blast square x 456..520), a resting stone at x 96.
 */
class ExplosiveTrapsTest {
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

    private static final float STUMP_X = 288, STUMP_RIGHT = 304, ROOT_X = 480, ROOT_RIGHT = 496;
    private static final float TRIGGER_LEFT = 291, TRIGGER_RIGHT = 301;
    private static final float BLAST_LEFT = 264, BLAST_RIGHT = 328;
    private static final float WARNING = TrapWarnings.DEFAULT_WARNING_SECONDS;

    private Level level(String map) { return new Level(assets, game, new Player(assets, game, 0, 0), map, Rectangle::new); }
    private Level fresh() { return level("explosive-test"); }

    private ExplosiveTrap trap(Level level, ExplosiveTrap.Kind kind) {
        return level.objects.stream().filter(ExplosiveTrap.class::isInstance).map(ExplosiveTrap.class::cast)
            .filter(t -> t.kind() == kind).findFirst().orElseThrow();
    }
    private ExplosiveTrap stump(Level level) { return trap(level, ExplosiveTrap.Kind.STUMP); }
    private ExplosiveTrap root(Level level) { return trap(level, ExplosiveTrap.Kind.ROOT); }
    private Carryable stone(Level level) {
        return level.objects.stream().filter(Carryable.class::isInstance).map(Carryable.class::cast).findFirst().orElseThrow();
    }

    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private float bodyLeft(Level level) { return level.player.hitboxBounds().x; }
    private float bodyRight(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width; }
    private boolean alive(Level level) { return !level.touchesDeadline(level.player.hitboxBounds()) && !level.player.isGotHit(); }
    /** Stands the hero (body right edge at {@code right}) on the floor, still, facing right. */
    private void place(Level level, float right) {
        level.player.position.set(right - 29, 272);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        held.clear();
    }
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

    /** Ranged key: a fireball when nothing is held, a throw when something is. */
    private void shoot(Level level) {
        settle(level, 20); // standing and able to fight (not arriving) before the key goes down
        held.clear(); held.add(Input.Keys.L); pressed.add(Input.Keys.L); frame(level);
        held.clear();
    }

    private int framesWhile(Level level, java.util.function.BooleanSupplier condition, int limit) {
        int frames = 0;
        while (condition.getAsBoolean() && frames < limit) { frame(level); frames++; }
        return frames;
    }

    // -- the objects --

    @Test void theFixtureHoldsOneIntactStumpAndOneIntactRootWithTheDocumentedBoxes() {
        Level level = fresh();
        assertEquals(2, level.objects.stream().filter(ExplosiveTrap.class::isInstance).count());
        for (ExplosiveTrap t : new ExplosiveTrap[]{stump(level), root(level)}) {
            assertEquals(ExplosiveTrap.Phase.IDLE, t.phase());
            assertTrue(t.isIntact());
            assertEquals(0, t.frameIndex(), "idle frame");
            assertEquals(64f, t.blastBox().width, 0f);
            assertEquals(64f, t.blastBox().height, 0f);
        }
        assertEquals(64f, ExplosiveTrap.BLAST_SIZE, 0f, "the existing bomb blast: 64x64");
        assertEquals(new Rectangle(291, 306, 10, 13), stump(level).triggerBox(), "the trigger box is the old bomb's inner 10x13 box");
        assertEquals(new Rectangle(264, 280, 64, 64), stump(level).blastBox(), "blast square centred on the object");
        assertEquals(new Rectangle(483, 299, 10, 19.5f), root(level).triggerBox(), "scaled with the object");
        assertEquals(new Rectangle(456, 276, 64, 64), root(level).blastBox());
        assertEquals(.8f, TrapWarnings.DEFAULT_WARNING_SECONDS, 0f);
    }

    // -- touch --

    @Test void touchingTheTriggerBoxStartsTheWindUpAndNotOnePixelBefore() {
        Level level = fresh();
        place(level, TRIGGER_LEFT - .5f);
        settle(level, 60 * 3);
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(level).phase(), "standing just short of the box never ignites it");
        assertTrue(alive(level));
        place(level, TRIGGER_LEFT + .5f);
        frame(level);
        assertEquals(ExplosiveTrap.Phase.WINDUP, stump(level).phase(), "overlapping it by half a pixel does");
        Level beyond = fresh();
        place(beyond, TRIGGER_RIGHT + 11 + .5f); // body left edge half a pixel past the box
        settle(beyond, 60 * 3);
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(beyond).phase());
        Level above = fresh();
        place(above, STUMP_X + 8);
        above.player.position.y -= 40; // dropping from well above: the body arrives from the top and still touches
        settle(above, 60);
        assertNotEquals(ExplosiveTrap.Phase.IDLE, stump(above).phase());
    }

    @Test void theWindUpIsExactlyTheSharedD7LengthNormallyAndWithTheAssistForBothObjects() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (ExplosiveTrap.Kind kind : ExplosiveTrap.Kind.values()) {
                Level level = fresh();
                ExplosiveTrap t = trap(level, kind);
                place(level, (kind == ExplosiveTrap.Kind.STUMP ? STUMP_X : ROOT_X) + 8);
                frame(level);
                assertEquals(ExplosiveTrap.Phase.WINDUP, t.phase());
                assertEquals(longer ? 1.2f : .8f, t.warningTotal(), 1e-6f, "literal shared value, longer=" + longer);
                int frames = 1 + framesWhile(level, () -> t.phase() == ExplosiveTrap.Phase.WINDUP, 60 * 5);
                assertEquals((longer ? 1.2f : .8f) * 60, frames, 2, kind + " wind-up frames, longer=" + longer);
                assertEquals(ExplosiveTrap.Phase.BLAST, t.phase());
                System.out.printf(java.util.Locale.ROOT, "longer=%s: %s winds up %.2f s%n", longer, kind, frames * STEP);
            }
        }
        setLonger(false);
    }

    @Test void theWindUpShowsCracksSmokeAndGlowInOrderThenTheBlastFramesThenTheSpentFrame() {
        Level level = fresh();
        ExplosiveTrap t = stump(level);
        place(level, 250);
        assertTrue(t.igniteFromAfar());
        java.util.List<Integer> seen = new java.util.ArrayList<>();
        while (t.phase() == ExplosiveTrap.Phase.WINDUP) { if (seen.isEmpty() || seen.get(seen.size() - 1) != t.frameIndex()) seen.add(t.frameIndex()); frame(level); }
        assertEquals(java.util.List.of(1, 2, 3), seen, "windup1 (cracks), windup2 (smoke), windup3 (glow)");
        java.util.List<Integer> blast = new java.util.ArrayList<>();
        while (t.phase() == ExplosiveTrap.Phase.BLAST) { if (blast.isEmpty() || blast.get(blast.size() - 1) != t.blastFrameIndex()) blast.add(t.blastFrameIndex()); frame(level); }
        assertEquals(java.util.List.of(0, 1, 2, 3), blast, "the four blast frames");
        assertEquals(ExplosiveTrap.Phase.SPENT, t.phase());
        assertEquals(4, t.frameIndex(), "spent frame");
        assertEquals(-1, t.blastFrameIndex());
    }

    @Test void leavingTheBoxDoesNotCancelTheWindUpAndATouchWhileWindingUpChangesNothing() {
        Level level = fresh();
        ExplosiveTrap t = stump(level);
        place(level, STUMP_X + 8);
        frame(level);
        float total = t.warningTotal();
        place(level, 100);
        settle(level, 20);
        float remaining = t.warningRemaining();
        assertEquals(total - 21 * STEP, remaining, 2 * STEP);
        place(level, STUMP_X + 8); // touch again mid wind-up (and survive: the warning is still running)
        frame(level);
        assertEquals(remaining - STEP, t.warningRemaining(), 1e-4f, "repeated contact cannot postpone or restart it");
        place(level, 100);
        settle(level, 60 * 3);
        assertEquals(ExplosiveTrap.Phase.SPENT, t.phase());
        assertTrue(alive(level), "the hero stood clear of the square");
    }

    // -- the blast --

    @Test void theBlastKillsExactlyInsideTheBlastSquareAndOnlyAtTheInstantOfDetonation() {
        Level inside = fresh(), left = fresh(), right = fresh();
        for (Level l : new Level[]{inside, left, right}) assertTrue(stump(l).igniteFromAfar());
        place(inside, BLAST_LEFT + .5f); // body right edge half a pixel into the square
        place(left, BLAST_LEFT - .1f);
        place(right, BLAST_RIGHT + 11 + .1f); // body left edge just past the square
        int warningFrames = (int) (WARNING * 60) - 4;
        for (int i = 0; i < warningFrames; i++) { frame(inside); frame(left); frame(right); }
        assertTrue(alive(inside), "inside the square but the warning has not ended: nothing hurts yet");
        settle(inside, 8); settle(left, 8); settle(right, 8);
        assertFalse(alive(inside), "inside the square at detonation: killed");
        assertTrue(alive(left), "just left of the square: safe");
        assertTrue(alive(right), "just right of the square: safe");
        assertTrue(stump(left).phase() == ExplosiveTrap.Phase.BLAST || stump(left).isSpent(), "it did go off");
    }

    @Test void theBlastReachesUpAndDownByHalfTheSquareAndNoFurther() {
        // The square spans y 280..344 around the stump's centre (312). A hero standing on the floor (body y 298..320) is inside.
        Level level = fresh();
        place(level, STUMP_X - 20);
        assertTrue(level.player.hitboxBounds().overlaps(stump(level).blastBox()));
        Level high = fresh(); // a body hanging 70 px above the floor is above the square
        place(high, STUMP_X - 20);
        Rectangle body = high.player.hitboxBounds();
        assertFalse(new Rectangle(body.x, 200, body.width, body.height).overlaps(stump(high).blastBox()));
    }

    @Test void afterTheBlastTheStumpIsSpentHarmlessAndNonSolidAndNothingBlastsTwice() {
        Level level = fresh();
        ExplosiveTrap t = stump(level);
        place(level, 250);
        assertTrue(t.igniteFromAfar());
        settle(level, 60 * 2);
        assertTrue(t.isSpent());
        assertTrue(alive(level));
        // Walk straight through the charred remains without jumping: nothing blocks, nothing hurts, nothing re-ignites.
        place(level, STUMP_X - 20);
        int frames = 0;
        while (bodyLeft(level) < STUMP_RIGHT + 20 && frames++ < 60 * 4) step(level, true, false);
        assertTrue(bodyLeft(level) >= STUMP_RIGHT + 20, "walked through the spent stump: " + level.player.position);
        assertTrue(alive(level));
        assertTrue(t.isSpent(), "stays spent");
        assertFalse(t.igniteFromAfar(), "a spent trap cannot be lit again");
        assertTrue(level.objects.contains(t), "the charred remains stay in the room as scenery");
    }

    @Test void aHeroWalkingIntoTheSquareWhileTheBlastAnimationPlaysIsNotHurt() {
        Level level = fresh();
        ExplosiveTrap t = stump(level);
        place(level, 200);
        t.igniteFromAfar();
        framesWhile(level, () -> t.phase() == ExplosiveTrap.Phase.WINDUP, 60 * 3);
        assertEquals(ExplosiveTrap.Phase.BLAST, t.phase());
        place(level, STUMP_X - 10); // one blast, checked once at detonation
        settle(level, 10);
        assertTrue(alive(level));
    }

    // -- distant detonation --

    @Test void aFireballThatReachesTheStumpStartsTheSameWindUpAndIsSpent() {
        Level level = fresh();
        place(level, 200);
        int ammo = level.rangedStock.count();
        shoot(level);
        assertEquals(ammo - 1, level.rangedStock.count(), "one charge is paid");
        ExplosiveTrap t = stump(level);
        int frames = framesWhile(level, () -> t.isIntact(), 60);
        assertEquals(ExplosiveTrap.Phase.WINDUP, t.phase(), "the fireball lit it after " + frames + " frames");
        assertEquals(WARNING, t.warningTotal(), 1e-6f, "the very same warning: a distant hit shortens nothing");
        assertTrue(level.objects.stream().noneMatch(Fireball.class::isInstance), "the fireball is spent");
        assertEquals(ammo - 1, level.rangedStock.count());
        // The hero, 88 px away, steps back... and is never in the square.
        settle(level, 60 * 3);
        assertTrue(t.isSpent());
        assertTrue(alive(level));
        assertEquals(ExplosiveTrap.Phase.IDLE, root(level).phase(), "the root further on was not touched");
    }

    @Test void aFireballLightsTheRootToo() {
        Level level = fresh();
        place(level, 400);
        shoot(level);
        ExplosiveTrap t = root(level);
        framesWhile(level, () -> t.isIntact(), 60);
        assertEquals(ExplosiveTrap.Phase.WINDUP, t.phase());
        assertEquals(WARNING, t.warningTotal(), 1e-6f);
    }

    @Test void theDistantWindUpIsTheFullWarningWithTheAssistAndTheHeroCanStillStepAway() throws Exception {
        setLonger(true);
        Level level = fresh();
        place(level, 200);
        shoot(level);
        ExplosiveTrap t = stump(level);
        framesWhile(level, () -> t.isIntact(), 60);
        assertEquals(1.2f, t.warningTotal(), 1e-6f);
        assertTrue(t.warningRemaining() > 1.1f, "nearly the whole assisted warning is left at the moment of the hit: " + t.warningRemaining());
        setLonger(false);
    }

    @Test void aFireballFliesOnThroughAWindingUpOrSpentTrapAndLightsTheNextOne() {
        Level level = fresh();
        place(level, 250);
        assertTrue(stump(level).igniteFromAfar());
        shoot(level);
        ExplosiveTrap far = root(level);
        framesWhile(level, () -> far.isIntact(), 60 * 2);
        assertEquals(ExplosiveTrap.Phase.WINDUP, far.phase(), "the fireball passed the winding-up stump and lit the root");
        Level spent = fresh();
        place(spent, 250);
        spent.player.position.y = 272;
        stump(spent).igniteFromAfar();
        settle(spent, 60 * 2);
        assertTrue(stump(spent).isSpent());
        shoot(spent);
        framesWhile(spent, () -> root(spent).isIntact(), 60 * 2);
        assertEquals(ExplosiveTrap.Phase.WINDUP, root(spent).phase(), "and through the spent one");
    }

    @Test void aSolidWallStillStopsAFireballBeforeTheTrap() {
        // The fireball dies on terrain, so a trap behind terrain cannot be lit (same as enemies).
        Level level = level("explosive-shield");
        place(level, 100); // facing right at the pillar x 190..206, which stands in front of the stump
        shoot(level);
        settle(level, 60);
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(level).phase());
    }

    /** Picks up the stone, walks to {@code from} (body right edge) and throws it; returns once everything has come to rest. */
    private void throwStoneFrom(Level level, float from) {
        place(level, 108);
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear(); frame(level);
        assertSame(stone(level), level.player.carriedObject(), "picked up the stone");
        int f = 0;
        while (bodyRight(level) < from && f++ < 60 * 10) step(level, true, false);
        held.clear();
        settle(level, 12);
        shoot(level);
    }

    @Test void aThrownStoneLightsTheStumpFromAfarIsNotConsumedAndCanBeFetchedAgain() {
        int hits = 0;
        for (float from = 140; from <= 255; from += 5) {
            Level level = fresh();
            throwStoneFrom(level, from);
            ExplosiveTrap t = stump(level);
            int ammo = level.rangedStock.count();
            settle(level, 45);
            if (t.isIntact()) continue;
            hits++;
            assertEquals(ammo, level.rangedStock.count(), "a stone costs no fireball charge");
            settle(level, 60 * 3);
            assertTrue(t.isSpent());
            assertTrue(alive(level), "thrown from " + from + " the hero stood clear of the blast");
            Carryable stone = stone(level);
            assertFalse(stone.isRemoved(), "the stone was not consumed by the hit");
            assertTrue(stone.available(), "it lies at rest again, D39: it can be picked up and thrown once more");
            assertTrue(level.currentRoomBounds().contains(stone.bounds()));
        }
        assertTrue(hits >= 5, "stones thrown from the right range reach the stump (found " + hits + " distances)");
    }

    @Test void aThrownStoneThatLightsTheStumpDropsInsteadOfFlyingOnAndALitStumpDoesNotStopIt() {
        Level level = fresh();
        throwStoneFrom(level, 215);
        ExplosiveTrap t = stump(level);
        settle(level, 45);
        if (t.isIntact()) { // find a distance that hits, then check the stone stopped at the stump
            for (float from = 140; from <= 255 && t.isIntact(); from += 1) {
                level = fresh();
                throwStoneFrom(level, from);
                t = stump(level);
                settle(level, 45);
            }
        }
        assertFalse(t.isIntact());
        assertTrue(stone(level).getX() < STUMP_RIGHT + 4, "the stone stopped at the stump instead of flying on: " + stone(level).getX());
    }

    // -- pause, retry, restart --

    @Test void pausingStopsTheWindUpAndResumingContinuesFromTheSameStateNotARestart() {
        // KimeriaGame stops calling Level.update while paused, so a pause is a run of frames with no update.
        Level paused = fresh(), straight = fresh();
        place(paused, 200); place(straight, 200);
        stump(paused).igniteFromAfar(); stump(straight).igniteFromAfar();
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        float before = stump(paused).warningRemaining();
        assertEquals(.8f - 20 * STEP, before, 1e-4f);
        assertEquals(before, stump(paused).warningRemaining(), 0f, "nothing advanced while paused");
        assertEquals(ExplosiveTrap.Phase.WINDUP, stump(paused).phase());
        for (int i = 0; i < 20; i++) { frame(paused); frame(straight); }
        assertEquals(stump(straight).warningRemaining(), stump(paused).warningRemaining(), 1e-6f, "resumed exactly where it stopped");
    }

    @Test void retryAndRestartLevelMakeTheTrapWholeAgain() {
        Level level = fresh();
        place(level, STUMP_X + 8);
        settle(level, 60 * 3);
        assertFalse(alive(level), "touched and stayed: the blast kills");
        assertTrue(stump(level).isSpent());
        retry(level);
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(level).phase(), "retry rebuilds the room: whole again");
        assertTrue(alive(level));
        assertEquals(0, stump(level).frameIndex());
        stump(level).igniteFromAfar();
        settle(level, 60 * 2);
        assertTrue(stump(level).isSpent());
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(fresh()).phase(), "Restart Level builds the room again");
    }

    @Test void aHeroWhoCannotBeHurtYetDoesNotTriggerOrGetKilled() {
        Level level = fresh();
        place(level, STUMP_X + 8);
        settle(level, 60 * 3);
        assertFalse(alive(level));
        assertFalse(level.player.canLeaveRoom(), "dying");
        place(level, ROOT_X + 8);
        settle(level, 30);
        assertEquals(ExplosiveTrap.Phase.IDLE, root(level).phase(), "a dying hero does not trigger the next trap");
    }

    // -- the bypass: a hero with no ammunition gets past without touching --

    private static boolean jumpWindow(float right, float objectX) { return right >= objectX - 22 && right < objectX - 14; }

    private void walkAndJump(Level level, float... objects) {
        int frames = 0;
        while (bodyLeft(level) < ROOT_RIGHT + 30 && frames++ < 60 * 20) {
            boolean jump = false;
            for (float x : objects) jump |= level.player.isOnGround() && jumpWindow(bodyRight(level), x);
            step(level, true, jump);
            assertTrue(alive(level), "died at " + level.player.position + " after " + frames);
        }
        held.clear();
        assertTrue(bodyLeft(level) >= ROOT_RIGHT + 30, "never got past: " + level.player.position);
    }

    @Test void aHeroWithNoAmmunitionJumpsOverBothTrapsWithOrdinaryControlsAndNeverLightsThem() {
        Level level = fresh();
        level.rangedStock.restore(0);
        assertEquals(0, level.rangedStock.count());
        walkAndJump(level, STUMP_X, ROOT_X);
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(level).phase());
        assertEquals(ExplosiveTrap.Phase.IDLE, root(level).phase());
        assertTrue(alive(level));
    }

    @Test void theSameRoutePassesWithTheAssistAndFromEveryTakeOffInTheJumpEnvelope() throws Exception {
        setLonger(true);
        int passes = 0;
        for (float edge = 40; edge <= 60; edge += 2) { // take-off windows 22+..14+ px before the object, shifted by `edge` - 40 px
            Level level = fresh();
            level.rangedStock.restore(0);
            int frames = 0;
            while (bodyLeft(level) < STUMP_RIGHT + 30 && frames++ < 60 * 20) {
                float r = bodyRight(level);
                boolean jump = level.player.isOnGround() && r >= STUMP_X - 22 + (edge - 40) / 2 && r < STUMP_X - 14 + (edge - 40) / 2;
                step(level, true, jump);
            }
            held.clear();
            if (alive(level) && stump(level).isIntact() && bodyLeft(level) >= STUMP_RIGHT + 30) passes++;
        }
        assertTrue(passes >= 4, "a range of take-off points clears the stump: " + passes);
        setLonger(false);
    }

    @Test void theOtherWayPastIsPayingAFireballFromAfarWaitingOutTheWarningAndWalkingThrough() {
        Level level = fresh();
        place(level, 200);
        int ammo = level.rangedStock.count();
        shoot(level);
        ExplosiveTrap t = stump(level);
        settle(level, 60 * 2);
        assertTrue(t.isSpent());
        assertEquals(ammo - 1, level.rangedStock.count());
        int frames = 0;
        while (bodyLeft(level) < STUMP_RIGHT + 30 && frames++ < 60 * 10) step(level, true, false);
        held.clear();
        assertTrue(alive(level));
        assertTrue(bodyLeft(level) >= STUMP_RIGHT + 30, "walked through without a single jump");
        assertTrue(t.isSpent());
    }

    // -- load-time validation --

    private String loadError(String map) {
        return assertThrows(IllegalArgumentException.class, () -> level(map)).getMessage();
    }

    @Test void aTrapInAOneTileCorridorOrUnderALowCeilingHasNoBypassAndFailsAtLoad() {
        String message = loadError("explosive-corridor");
        assertTrue(message.contains("ExplosiveStump") && message.contains("[10]") && message.contains("free height"), message);
    }

    @Test void aTrapBesideAPitHasNoRunUpOrLandingAndFailsAtLoad() {
        assertTrue(loadError("explosive-pit").contains("open floor"));
    }

    @Test void aPillarInTheJumpEnvelopeBlocksTheBypassAndFailsAtLoad() {
        assertTrue(loadError("explosive-pillar").contains("free height"));
    }

    @Test void aBlastSquareOverTheRoomEntranceFailsAtLoadBecauseAHitFromAfarCouldKillTheArrivingHero() {
        String message = loadError("explosive-entrance");
        assertTrue(message.contains("blast square") && message.contains("entrance"), message);
    }

    @Test void theGoodFixtureLoads() {
        fresh();
        assertEquals(2, fresh().objects.stream().filter(ExplosiveTrap.class::isInstance).count());
    }

    @Test void onlyTheTrapsRoomAndTheStoneRoomOfForestZeroUseAnExplosiveTrapAndTheOldBombStaysWhereItWas() throws Exception {
        // Census of the shipped maps (D47): nothing else changed unexpectedly. forest-3 keeps its one old Bomb.
        java.nio.file.Path tiles = java.nio.file.Path.of("..", "assets", "tiles");
        java.util.Map<String, Integer> expectedStumps = java.util.Map.of("forest-0", 2), expectedBombs = java.util.Map.of("forest-3", 1);
        try (var maps = java.nio.file.Files.list(tiles)) {
            for (java.nio.file.Path map : maps.filter(m -> m.toString().endsWith(".tmx")).toList()) {
                String name = map.getFileName().toString().replace(".tmx", ""), text = java.nio.file.Files.readString(map);
                assertEquals(expectedStumps.getOrDefault(name, 0), text.split("type=\"ExplosiveStump\"", -1).length - 1, name + " stumps");
                assertEquals(0, text.split("type=\"ExplosiveRoot\"", -1).length - 1, name + " roots");
                assertEquals(expectedBombs.getOrDefault(name, 0), text.split("type=\"Bomb\"", -1).length - 1, name + " bombs");
            }
        }
    }

    @Test void aDetonatedStumpStaysSpentWhenTheHeroLeavesTheRoomAndComesBack() throws Exception {
        Level level = level("explosive-rooms");
        ExplosiveTrap before = stump(level);
        place(level, 100);
        assertTrue(before.igniteFromAfar());
        settle(level, 60 * 2);
        assertTrue(before.isSpent());
        level.enterNextRoom(new Player(assets, game, 0, 0));
        assertEquals(0, level.objects.stream().filter(ExplosiveTrap.class::isInstance).count(), "room two has none");
        var room = Level.class.getDeclaredField("currentRoom");
        room.setAccessible(true);
        room.setInt(level, 0);
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        settle(level, 2);
        assertEquals(ExplosiveTrap.Phase.SPENT, stump(level).phase(), "back in room one it is still charred and harmless");
        assertTrue(alive(level));
        // A fresh Level (Restart Level) is the authored room again.
        assertEquals(ExplosiveTrap.Phase.IDLE, stump(level("explosive-rooms")).phase());
    }

    @Test void aWindingUpStumpResumesItsSavedCountdownOnReturn() throws Exception {
        Level level = level("explosive-rooms");
        place(level, 100);
        stump(level).igniteFromAfar();
        settle(level, 20);
        float remaining = stump(level).warningRemaining();
        level.enterNextRoom(new Player(assets, game, 0, 0));
        var room = Level.class.getDeclaredField("currentRoom");
        room.setAccessible(true);
        room.setInt(level, 0);
        level.resetCurrentRoom(new Player(assets, game, 0, 0));
        assertEquals(ExplosiveTrap.Phase.WINDUP, stump(level).phase());
        assertEquals(remaining, stump(level).warningRemaining(), 1e-6f);
    }
}
