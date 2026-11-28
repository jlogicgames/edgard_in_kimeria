package com.jlogicsoftware.kimeria.entity.objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The lift's drawn plank lies on the line a rider stands on (the footprint's top); the world's Y grows downward. */
class EscalatorSurfaceTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int FRAME_WIDTH = 32, ON_FRAMES = 8;
    /** The walk cue straw the plank's top face is drawn in (art/objects/escalator.grid), RGBA8888. */
    private static final int STRAW = 0xe1cfa5ff;
    /** Straw px of the top face in one frame at least: the bar's 30 inner columns less the hub in the middle. */
    private static final int PLANK_STRAW_MIN = 24;
    /** The most px a row above the plank may hold: the hub's 4 px cap. */
    private static final int CAP_WIDTH = 4;
    /** Frames that carry each lift through both of its turns: the longest authored travel is 128 px at 50 px/s. */
    private static final int RIDE_FRAMES = 420;
    /** The hero's frame x on a lift, px right of its left edge: the body stands in the middle of the 32 px plank. */
    private static final float ON_PLANK_X = -7.5f;
    private static final float HERO_FRAME = 48;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    @Test void theSurfaceRowIsThePlanksTopFaceInEveryFrame() {
        assertPlankAt("images/objects/GreyOff.png", 1);
        assertPlankAt("images/objects/GreyOn.png", ON_FRAMES);
    }

    private static void assertPlankAt(String path, int frames) {
        Pixmap sheet = new Pixmap(Gdx.files.internal(path));
        try {
            assertEquals(frames * FRAME_WIDTH, sheet.getWidth(), path);
            for (int frame = 0; frame < frames; frame++) {
                for (int row = 0; row < Escalator.SURFACE_ROW; row++) {
                    assertTrue(opaque(sheet, frame, row) <= CAP_WIDTH, path + " frame " + frame + " row " + row + " is above the plank");
                    assertEquals(0, straw(sheet, frame, row), path + " frame " + frame + " row " + row);
                }
                assertTrue(straw(sheet, frame, Escalator.SURFACE_ROW) >= PLANK_STRAW_MIN, path + " frame " + frame + " has no plank top");
            }
        } finally {
            sheet.dispose();
        }
    }

    private static int opaque(Pixmap sheet, int frame, int row) {
        int count = 0;
        for (int x = frame * FRAME_WIDTH; x < (frame + 1) * FRAME_WIDTH; x++) if ((sheet.getPixel(x, row) & 0xff) != 0) count++;
        return count;
    }

    private static int straw(Pixmap sheet, int frame, int row) {
        int count = 0;
        for (int x = frame * FRAME_WIDTH; x < (frame + 1) * FRAME_WIDTH; x++) if (sheet.getPixel(x, row) == STRAW) count++;
        return count;
    }

    /** Stopped and running, along both axes and through the turns, where the horizontal bar is mirrored. */
    @Test void thePlankIsDrawnOnTheFootprintsTopInEveryState() {
        for (boolean vertical : new boolean[]{false, true}) {
            for (boolean running : new boolean[]{false, true}) {
                Escalator lift = new Escalator(harness.assets(), 320, 224, 32, 16, vertical, 1, 1, "", running);
                for (int i = 0; i < RIDE_FRAMES; i++) {
                    lift.update(STEP);
                    assertEquals(lift.getY(), drawnPlankY(lift), 0f, "vertical " + vertical + " running " + running + " frame " + i);
                }
            }
        }
    }

    @Test void theRidersFeetStandOnTheDrawnPlankOfTheHorizontalLift() {
        ride(false);
    }

    @Test void theRidersFeetStandOnTheDrawnPlankOfTheVerticalLift() {
        ride(true);
    }

    private void ride(boolean vertical) {
        Level level = harness.room("forest-1", "forest-1-escalators");
        Escalator lift = level.escalators.stream().filter(e -> e.isVertical == vertical && e.isRunning()).findFirst().orElseThrow();
        level.player.position.set(lift.getX() + ON_PLANK_X, lift.getY() - HERO_FRAME);
        float startX = lift.getX(), startY = lift.getY(), travelled = 0;
        for (int i = 0; i < RIDE_FRAMES; i++) {
            level.update(STEP);
            assertSame(lift, level.player.currentEscalator(), "rides at frame " + i);
            Rectangle body = level.player.hitboxBounds();
            assertEquals(drawnPlankY(lift), body.y + body.height, 0.01f, "feet on the plank at frame " + i);
            travelled = Math.max(travelled, Math.abs(lift.getX() - startX) + Math.abs(lift.getY() - startY));
        }
        assertTrue(travelled >= 32, "the lift moved: " + travelled);
    }

    /** The world y of the plank's top face as {@code render} draws it. */
    private float drawnPlankY(Escalator lift) {
        batch.record(1);
        lift.render(batch);
        assertEquals(1, batch.drawnCount());
        return batch.drawnY(0) + Escalator.SURFACE_ROW;
    }
}
