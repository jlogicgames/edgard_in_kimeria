package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.objects.EmberFissure;
import com.jlogicsoftware.kimeria.entity.objects.LanceRoot;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lance root (#562, D129) keeps the ember fissure's clock, column and rules ({@code EmberFissureTest}); this pins that it
 * is the same machine in another look: step for step the same phases, column and warning as an {@code EmberFissure}, the bark
 * chips and soil specks of its own, its own sounds once per cycle and only in view, and the load on {@code lance-test.tmx}.
 */
class LanceRootTest {
    private static final float STEP = 1f / 60f, FLOOR = 320f;
    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Rectangle view = new Rectangle(0, 0, 1280, 368);

    @BeforeEach void setUp() { h.setUp(); h.playSounds = true; }
    @AfterEach void restoreGlobals() throws Exception { setLonger(false); h.restoreGlobals(); }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(h.game().trapWarnings(), longer);
    }

    @Test void itRunsTheEmberFissuresClockAndColumnStepForStepWithBothAssistValues() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            for (float phase : new float[]{0f, 1.75f}) {
                LanceRoot root = new LanceRoot(h.assets(), 100, FLOOR - EmberFissure.FLOOR_OFFSET, h.game().trapWarnings(), 3.5f, phase, 1f, 96f);
                EmberFissure fissure = new EmberFissure(h.assets(), 100, FLOOR - EmberFissure.FLOOR_OFFSET, h.game().trapWarnings(), 3.5f, phase, 1f, 96f);
                Rectangle a = new Rectangle(), b = new Rectangle();
                for (int i = 0; i < 60 * 12; i++) {
                    root.update(STEP); fissure.update(STEP);
                    assertEquals(fissure.currentPhase(), root.currentPhase(), "frame " + i);
                    assertEquals(fissure.frameIndex(), root.frameIndex(), "frame " + i);
                    assertEquals(fissure.jetInto(a).height, root.jetInto(b).height, 1e-4f, "frame " + i);
                }
            }
        }
    }

    @Test void theWarningIsTheSharedWarning() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            LanceRoot root = new LanceRoot(h.assets(), 100, FLOOR - EmberFissure.FLOOR_OFFSET, h.game().trapWarnings(), 3.5f, 0f, 1f, 96f);
            assertEquals(longer ? 1.2f : 0.8f, root.warningSeconds(), 1e-5f);
        }
    }

    @Test void theWarningHopsSoilAndEveryThrustDropsBarkChips() {
        LanceRoot root = new LanceRoot(h.assets(), 100, FLOOR - EmberFissure.FLOOR_OFFSET, h.game().trapWarnings(), 3.5f, 0f, 1f, 96f);
        boolean specks = false, chips = false;
        for (int i = 0; i < 60 * 4; i++) {
            root.update(STEP);
            if (root.currentPhase() == EmberFissure.Phase.WARNING && root.sparks().count() > 0) specks = true;
            if (root.currentPhase() == EmberFissure.Phase.JET && root.jetElapsed() > 0.8f && root.sparks().count() >= LanceRoot.CHIPS) chips = true;
        }
        assertTrue(specks, "soil specks hop in the warning");
        assertTrue(chips, "bark chips fall as the root sinks, hit or miss (D93)");
    }

    @Test void theLevelLoadsItAndItsSoundsPlayOncePerCycleAndOnlyInView() {
        Level level = h.room("lance-test", "floor", () -> view);
        assertTrue(level.objects.stream().anyMatch(LanceRoot.class::isInstance), "the room holds a lance root");
        assertTrue(level.objects.stream().noneMatch(o -> o.getClass() == EmberFissure.class), "and no plain fissure");
        h.sounds.clear();
        for (int i = 0; i < 60 * 7; i++) level.update(STEP);
        assertEquals(2, h.sounds.stream().filter("root_creak"::equals).count(), h.sounds.toString());
        assertEquals(2, h.sounds.stream().filter("root_thrust"::equals).count(), h.sounds.toString());
        assertTrue(h.sounds.stream().noneMatch(s -> s.equals("fissure_rumble") || s.equals("ember_jet")), "never the fissure's sounds");
        view.set(5000, 0, 640, 368);
        h.sounds.clear();
        for (int i = 0; i < 60 * 7; i++) level.update(STEP);
        assertTrue(h.sounds.isEmpty(), "off screen it is silent: " + h.sounds);
    }

    @Test void theErrorsNameTheLanceRoot() {
        TiledObject o = new TiledObject();
        o.type = "LanceRoot"; o.x = 0; o.y = 316; o.width = 16; o.height = 12;
        o.properties.put("speed", "2");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LanceRoot.fromMap(h.assets(), o, h.game().trapWarnings(), java.util.List.of(), "x"));
        assertTrue(e.getMessage().startsWith("LanceRoot x:"), e.getMessage());
    }
}
