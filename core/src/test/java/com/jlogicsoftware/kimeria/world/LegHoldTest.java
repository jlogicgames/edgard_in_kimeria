package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.objects.LegHold;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The leg-hold trap (#561, D128): the warning equals the shared one at both assist values, contact while open catches at
 * once, the snap catches only its arch, shut jaws are harmless, the cycle and its re-arm (held under a foot), a stone or a
 * fireball snaps it from afar, the sounds play once and only in view, and the load rules. World Y grows downward: the jaws'
 * bottom is the floor line (320). The room is {@code leghold-test.tmx} (jaws at x 224).
 */
class LegHoldTest {
    private static final float STEP = 1f / 60f, FLOOR = 320f, X = 224f, TEETH = X + LegHold.OPEN_INSET;
    private static final float HERO_W = 11f, HERO_H = 22f;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Rectangle hero = new Rectangle(), view = new Rectangle(0, 0, 640, 368);

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() throws Exception { setLonger(false); h.restoreGlobals(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    private LegHold lone(float zone, float window) {
        return new LegHold(h.assets(), h.game().trapWarnings(), X, FLOOR - LegHold.HEIGHT, zone, window, () -> hero);
    }

    private void heroAt(float centreX, float feet) { hero.set(centreX - HERO_W / 2f, feet - HERO_H, HERO_W, HERO_H); }
    /** The hero inside the zone and clear of the teeth: 16 px short of them. */
    private void heroInZone() { heroAt(TEETH - 16f - HERO_W / 2f + 4f, FLOOR); }
    private void heroFar() { heroAt(40, FLOOR); }
    private static void advance(LegHold t, float seconds) { for (int i = 0, n = Math.round(seconds / STEP); i < n; i++) t.update(STEP); }

    private static float secondsToSnap(LegHold t) {
        int frames = 0;
        while (t.phase() != LegHold.Phase.SNAP && frames < 600) { t.update(STEP); frames++; }
        return frames * STEP;
    }

    @Test void theWarningEqualsTheSharedWarningWithAndWithoutTheAssist() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
            heroInZone();
            t.update(STEP);
            assertEquals(LegHold.Phase.WARNING, t.phase());
            assertEquals(h.game().trapWarnings().duration(TrapWarnings.DEFAULT_WARNING_SECONDS), t.warningTotal(), 1e-5f);
            assertEquals(longer ? 1.2f : 0.8f, t.warningTotal(), 1e-5f);
            assertEquals(t.warningTotal(), secondsToSnap(t), STEP * 2f, "the snap comes when the warning ends");
        }
    }

    @Test void theWarningRunsToItsEndEvenIfTheHeroLeavesTheZone() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        heroInZone();
        t.update(STEP);
        heroFar();
        advance(t, 0.5f);
        assertEquals(LegHold.Phase.WARNING, t.phase());
        advance(t, 0.4f);
        assertEquals(LegHold.Phase.SNAP, t.phase(), "the 0.8 s warning is not cancelled by leaving");
    }

    @Test void nothingHappensWhileTheHeroIsOutsideTheZone() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        heroAt(TEETH - LegHold.DEFAULT_ZONE - HERO_W - 2f, FLOOR);
        advance(t, 3f);
        assertEquals(LegHold.Phase.OPEN, t.phase());
        heroAt(TEETH - LegHold.DEFAULT_ZONE - HERO_W / 2f + 1f, FLOOR); // the body's edge just inside the zone
        t.update(STEP);
        assertEquals(LegHold.Phase.WARNING, t.phase());
    }

    @Test void aHeroInTheAirAboveTheZoneIsNotSeen() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        heroAt(TEETH - 8f, FLOOR - LegHold.ZONE_HEIGHT - 2f - 0f);
        t.update(STEP);
        assertEquals(LegHold.Phase.OPEN, t.phase(), "the zone reaches only " + LegHold.ZONE_HEIGHT + " px up");
    }

    @Test void contactWhileOpenCatchesAtOnceAndAHopOverTheTeethIsSafe() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        Rectangle body = new Rectangle(TEETH + 6f, FLOOR - HERO_H, HERO_W, HERO_H);
        assertTrue(t.lethalTo(body), "standing on the open teeth");
        body.setY(FLOOR - LegHold.OPEN_HEIGHT - HERO_H - 0.5f);
        assertFalse(t.lethalTo(body), "feet 0.5 px over the teeth's top");
        body.set(TEETH + LegHold.OPEN_WIDTH + 0.5f, FLOOR - HERO_H, HERO_W, HERO_H);
        assertFalse(t.lethalTo(body), "beside the teeth");
        heroInZone();
        t.update(STEP);
        body.set(TEETH + 6f, FLOOR - HERO_H, HERO_W, HERO_H);
        assertTrue(t.lethalTo(body), "the jaws are still open during the warning");
    }

    @Test void theSnapCatchesOnlyItsArchAndShutJawsAreHarmless() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        heroInZone();
        secondsToSnap(t);
        assertEquals(LegHold.Phase.SNAP, t.phase());
        Rectangle over = new Rectangle(TEETH + 6f, FLOOR - LegHold.SNAP_HEIGHT - HERO_H + 1f, HERO_W, HERO_H); // feet 11 px over the floor
        assertTrue(t.lethalTo(over), "the arch is lethal up to its drawn height");
        over.y -= 2f;
        assertFalse(t.lethalTo(over), "a hero clear of the arch");
        advance(t, LegHold.SNAP_SECONDS + 0.01f);
        assertEquals(LegHold.Phase.SHUT, t.phase());
        assertFalse(t.lethalTo(new Rectangle(TEETH + 6f, FLOOR - HERO_H, HERO_W, HERO_H)), "walking over shut jaws");
    }

    @Test void theCycleShutsForTheWindowThenRearmsAndStaysShutUnderAFoot() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, 1.5f);
        heroInZone();
        secondsToSnap(t);
        advance(t, LegHold.SNAP_SECONDS + 0.02f);
        assertEquals(LegHold.Phase.SHUT, t.phase());
        advance(t, 1.4f);
        assertEquals(LegHold.Phase.SHUT, t.phase(), "still inside the 1.5 s window");
        heroAt(TEETH + 12f, FLOOR); // standing on the shut jaws
        advance(t, 0.3f);
        assertEquals(LegHold.Phase.SHUT, t.phase(), "past the window the jaws stay shut under a foot");
        advance(t, 2f);
        assertEquals(LegHold.Phase.SHUT, t.phase(), "never lift the jaws under a foot");
        assertFalse(t.lethalTo(hero), "and the foot is safe meanwhile");
        heroInZone();
        advance(t, 0.1f);
        advance(t, LegHold.REARM_SECONDS + 0.05f);
        assertEquals(LegHold.Phase.WARNING, t.phase(), "open again, the hero is still in the zone: a new warning begins");
    }

    @Test void aSnapFromAfarSnapsOpenJawsEmptyAndOnlyThose() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        heroFar();
        assertTrue(t.snapFromAfar());
        assertEquals(LegHold.Phase.SNAP, t.phase());
        assertFalse(t.snapFromAfar(), "already snapped: the projectile flies on");
        advance(t, 0.5f);
        assertEquals(LegHold.Phase.SHUT, t.phase());
        assertFalse(t.snapFromAfar(), "shut");
    }

    @Test void theSoundsPlayOncePerCycleAndOnlyInView() {
        Level level = h.room("leghold-test", "floor", () -> view);
        LegHold t = level.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow();
        level.player.position.x = TEETH - 16f - HERO_W / 2f + 4f - level.player.hitbox.offsetX - level.player.hitbox.width / 2f + HERO_W / 2f;
        h.sounds.clear();
        for (int i = 0; i < 60; i++) level.update(STEP);
        assertEquals(1, h.sounds.stream().filter("leghold_rattle"::equals).count(), h.sounds.toString());
        for (int i = 0; i < 40; i++) level.update(STEP);
        assertEquals(1, h.sounds.stream().filter("leghold_snap"::equals).count(), h.sounds.toString());
        assertFalse(level.player.isGotHit(), "16 px short of the teeth: never caught");
        view.set(5000, 0, 640, 368);
        h.sounds.clear();
        for (int i = 0; i < 400; i++) level.update(STEP);
        assertTrue(h.sounds.isEmpty(), "off screen it is silent: " + h.sounds);
    }

    @Test void theSnapThrowsChipsAndTheWarningHopsSpecks() {
        LegHold t = lone(LegHold.DEFAULT_ZONE, LegHold.DEFAULT_WINDOW_SECONDS);
        heroInZone();
        advance(t, 0.3f);
        assertTrue(t.specks().count() > 0, "soil specks hop from the hinges in the warning");
        secondsToSnap(t);
        assertTrue(t.specks().count() >= LegHold.CHIPS, "five chips fly up at the snap, hit or miss");
    }

    @Test void aRetryAndRestartLevelRebuildItOpen() {
        Level level = h.room("leghold-test", "floor", () -> view);
        LegHold first = level.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow();
        assertTrue(first.snapFromAfar());
        for (int i = 0; i < 30; i++) level.update(STEP);
        assertEquals(LegHold.Phase.SHUT, first.phase());
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        LegHold retried = level.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow();
        assertNotSame(first, retried);
        assertEquals(LegHold.Phase.OPEN, retried.phase(), "a retry rebuilds it open");
        Level restarted = h.room("leghold-test", "floor", () -> view);
        assertEquals(LegHold.Phase.OPEN, restarted.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow().phase(), "Restart Level builds a new level, open");
    }

    @Test void aRoomReEntryByTheHealerReturnRebuildsItOpen() {
        Level level = h.room("forest-2", "forest-entry", () -> view);
        LegHold first = level.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow();
        assertTrue(first.snapFromAfar());
        for (int i = 0; i < 30; i++) level.update(STEP);
        assertEquals(LegHold.Phase.SHUT, first.phase());
        for (int i = 0; i < 7; i++) level.enterNextRoom(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        level.prepareHealer();
        level.returnForCollection(new com.jlogicsoftware.kimeria.entity.player.Player(h.assets(), h.game(), 0, 0));
        LegHold again = level.objects.stream().filter(LegHold.class::isInstance).map(LegHold.class::cast).findFirst().orElseThrow();
        assertEquals(LegHold.Phase.OPEN, again.phase(), "re-entering the room rebuilds it open: it keeps no secured state");
    }

    // ---- the load rules ----

    private static TiledObject object(float x, float y, String... props) {
        TiledObject o = new TiledObject();
        o.type = "LegHold"; o.x = x; o.y = y; o.width = LegHold.WIDTH; o.height = LegHold.HEIGHT;
        for (int i = 0; i + 1 < props.length; i += 2) o.properties.put(props[i], props[i + 1]);
        return o;
    }

    private static TiledObject block(String type, float x, float y, float w, float h) {
        TiledObject o = new TiledObject();
        o.type = type; o.x = x; o.y = y; o.width = w; o.height = h;
        return o;
    }

    @Test void theLoadRules() {
        TiledObject o = object(224, 304);
        List<TiledObject> floor = new ArrayList<>(List.of(block(null, 0, 320, 640, 48)));
        assertNull(LegHold.problem(o, floor));
        assertNull(LegHold.problem(o, List.of(block(null, 0, 320, 240, 48), block(null, 240, 320, 400, 48))), "split blocks are merged");
        assertNull(LegHold.problem(o, List.of(block("Platform", 192, 320, 96, 16))), "a one-way platform is a floor");
        assertNotNull(LegHold.problem(o, List.of(block(null, 0, 320, 240, 48))), "the floor ends under the jaws");
        assertNotNull(LegHold.problem(o, List.of(block("QuickSand", 0, 320, 640, 48))), "quicksand is no floor");
        List<TiledObject> roofed = new ArrayList<>(floor);
        roofed.add(block(null, 200, 240, 100, 16));
        assertNotNull(LegHold.problem(o, roofed), "a solid block within 96 px over the jaws");
        roofed.set(1, block("Platform", 200, 240, 96, 16));
        assertNull(LegHold.problem(o, roofed), "a one-way platform overhead is no solid (forest-entry's ledge)");
        assertNotNull(LegHold.validate(8, 2f));
        assertNotNull(LegHold.validate(24, 0.5f));
        assertNotNull(LegHold.validate(Float.NaN, 2f));
        assertNull(LegHold.validate(16, 3f));
        assertThrows(IllegalArgumentException.class, () -> LegHold.fromMap(h.assets(), h.game().trapWarnings(), object(224, 304, "speed", "3"), () -> hero, "x"));
        TiledObject small = object(224, 304);
        small.width = 16;
        assertThrows(IllegalArgumentException.class, () -> LegHold.fromMap(h.assets(), h.game().trapWarnings(), small, () -> hero, "x"));
    }
}
