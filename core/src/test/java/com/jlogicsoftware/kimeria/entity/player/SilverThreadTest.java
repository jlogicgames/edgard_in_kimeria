package com.jlogicsoftware.kimeria.entity.player;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.GridPoint2;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.HeroArt;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where the silver thread is drawn (#341, D81), measured on the batch calls of the real Player, not reasoned (AGENTS.md):
 * it starts on an opaque pixel of the drawn fist, ends at the room start's wrist height, and the slack thread after the
 * pull lies on the floor line (world Y grows down: a strand's bottom row is y + 2).
 */
class SilverThreadTest {
    private static final float MIRROR_START_X = 400;
    private static final float START_X = 32, FLOOR_TOP = 272, FLOOR_LINE = FLOOR_TOP + 48, LOWER_FLOOR = 48;
    private static final int STRAND_W = 1, STRAND_H = 2, FRAME = 64;
    /** The landing frame's left wrist facing right, (23, 52) in frame pixels, in box coordinates (minus (8, 16)). */
    private static final int LAND_WRIST_BOX_X = 15, LAND_WRIST_BOX_Y = 36;
    /** The frame is drawn at the box minus (8, 16) (D79): box coordinates plus these are frame pixels. */
    private static final int FRAME_DX = 8, FRAME_DY = 16;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final List<float[]> strands = new ArrayList<>(), frames = new ArrayList<>();
    private final Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[]{Batch.class}, (p, m, a) -> {
        if (m.getName().equals("draw") && a.length == 5 && a[0] instanceof TextureRegion) {
            float[] rect = {(float) a[1], (float) a[2], (float) a[3], (float) a[4]};
            if (rect[2] == STRAND_W && rect[3] == STRAND_H) strands.add(rect);
            else if (rect[2] == FRAME) frames.add(rect);
        }
        Class<?> r = m.getReturnType();
        if (r == boolean.class) return Boolean.FALSE;
        if (r == int.class) return Integer.valueOf(0);
        if (r == float.class) return Float.valueOf(0f);
        return null;
    });

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void restore() { harness.restoreGlobals(); }

    /** A hero spawned at forest-1's room start (the anchor), then moved to {@code x} on the same floor. */
    private Player hero(float x) { return hero(START_X, x); }

    private Player hero(float startX, float x) {
        Level level = harness.roomOf("forest-1", 0);
        Player hero = new Player(harness.assets(), harness.game(), startX, FLOOR_TOP);
        hero.setLevel(level);
        hero.spawned();
        hero.position.x = x;
        return hero;
    }

    private void render(Player hero) { strands.clear(); frames.clear(); hero.render(batch); }

    /** Whether frame pixel (x, y) of the cell the hero draws now is opaque. */
    private static boolean drawnPixelOpaque(Player hero, int x, int y) {
        SpriteSheets.Sheet sheet = hero.currentSheet();
        GridPoint2 cell = new GridPoint2();
        hero.currentCell(cell);
        return (HeroArt.sheet(sheet).getRGB(cell.x * sheet.width() + x, cell.y * sheet.height() + y) >>> 24) >= 128;
    }

    @Test void theThreadRunsFromTheRaisedFistToTheRoomStart() {
        Player hero = hero(300);
        hero.collidedWithActor(true);
        for (int i = 0; i < Math.round(Player.THREAD_DRAW_START * 60); i++) hero.update(HeadlessLevelHarness.STEP);
        render(hero);
        assertTrue(strands.isEmpty(), "nothing at 0.10 s: it starts drawing out from there");
        int toTaut = Math.round((Player.THREAD_TAUT - Player.THREAD_DRAW_START) * 60) - 1;
        for (int i = 0; i < toTaut; i++) hero.update(HeadlessLevelHarness.STEP);
        render(hero);
        assertFalse(strands.isEmpty());
        float[] first = strands.get(0), last = strands.get(strands.size() - 1);
        // The start is to the left (smaller X): the pull frames are drawn yanked backward by the left wrist, so he faces right.
        assertTrue(hero.facingRight, "the pull faces away from the start");
        assertEquals(Player.PULL_TUG, hero.pullFrame());
        int fx = Math.round(first[0] - hero.position.x) + FRAME_DX, fy = Math.round(first[1] - hero.position.y) + FRAME_DY;
        assertTrue(drawnPixelOpaque(hero, fx, fy), "the thread starts on the drawn left wrist, frame pixel " + fx + "," + fy);
        assertEquals(START_X + LAND_WRIST_BOX_X, last[0], 1f, "it ends at the start's wrist, x");
        assertEquals(FLOOR_TOP + LAND_WRIST_BOX_Y, last[1], 1f, "and y: the landing hero's left wrist height");
        float lowest = 0;
        for (float[] s : strands) lowest = Math.max(lowest, s[1]);
        assertTrue(lowest <= Math.max(first[1], last[1]) + 1, "taut by 0.45 s: no sag below the chord");
    }

    @Test void aStartOnTheRightTurnsHimLeftAndTheThreadRunsRight() {
        Player hero = hero(MIRROR_START_X, START_X + 64);
        hero.facingRight = true; // dies facing the start: the pull turns him away from it
        hero.collidedWithActor(true);
        for (int i = 0; i < Math.round(Player.THREAD_DRAW_END * 60); i++) hero.update(HeadlessLevelHarness.STEP);
        render(hero);
        assertFalse(hero.facingRight, "the pull faces away from the start");
        float[] first = strands.get(0), last = strands.get(strands.size() - 1);
        int fx = Math.round(first[0] - hero.position.x) + FRAME_DX, fy = Math.round(first[1] - hero.position.y) + FRAME_DY;
        assertTrue(drawnPixelOpaque(hero, fx, fy), "the thread starts on the drawn left wrist facing left, frame pixel " + fx + "," + fy);
        assertTrue(last[0] > first[0], "and runs right, to the start");
    }

    @Test void theSlackThreadAfterThePullLiesOnTheFloor() {
        Player hero = hero(START_X);
        hero.arriveUnderThread();
        render(hero);
        float[] first = strands.get(0);
        int fx = Math.round(first[0] - hero.position.x) + FRAME_DX, fy = Math.round(first[1] - hero.position.y) + FRAME_DY;
        assertEquals(Player.PULL_LAND, hero.pullFrame());
        assertTrue(drawnPixelOpaque(hero, fx, fy), "it hangs from the landing frame's left wrist, frame pixel " + fx + "," + fy);
        float bottom = 0;
        for (float[] s : strands) bottom = Math.max(bottom, s[1] + s[3]);
        assertEquals(FLOOR_LINE, bottom, .001f, "the coil rests on the floor line (y + 2 = the floor, Y down)");
        assertEquals(FLOOR_LINE, frames.get(0)[1] + FRAME, .001f, "and the landing frame stands on it");
        hero.advanceLanding(1f);
        render(hero);
        assertTrue(strands.isEmpty(), "dissolved by the end of the fade-in");
    }

    /**
     * Review of #341: a death below the start. Unclamped, the 16 px sag of the drawing-out thread would hang below the
     * start's floor line near the start (where the chord is still above it); the sag stops at that floor instead.
     */
    @Test void aDeathBelowTheStartNeverSagsUnderTheStartsFloor() {
        Player hero = hero(START_X + 240);
        hero.position.y = FLOOR_TOP + LOWER_FLOOR; // a floor 48 px lower than the start's
        hero.collidedWithActor(true);
        for (int i = 0; i < Math.round(Player.THREAD_DRAW_END * 60); i++) hero.update(HeadlessLevelHarness.STEP); // full sag
        render(hero);
        assertFalse(strands.isEmpty());
        float[] first = strands.get(0), last = strands.get(strands.size() - 1);
        int clamped = 0;
        for (float[] s : strands) {
            float u = (s[0] - first[0]) / (last[0] - first[0]);
            float chordBottom = first[1] + (last[1] - first[1]) * u + STRAND_H;
            assertTrue(s[1] + STRAND_H <= Math.max(chordBottom + 1, FLOOR_LINE), "x " + s[0] + ": bottom " + (s[1] + STRAND_H)
                + " under the start's floor " + FLOOR_LINE + " where the chord is above it");
            if (s[1] + STRAND_H == FLOOR_LINE) clamped++;
        }
        assertTrue(clamped > 0, "the sag reached the floor and stopped there (the case the clamp exists for)");
    }

    @Test void aDeathAtTheStartSendsTheThreadStraightUp() {
        Player hero = hero(START_X);
        hero.collidedWithActor(true);
        for (int i = 0; i < Math.round(Player.THREAD_TAUT * 60); i++) hero.update(HeadlessLevelHarness.STEP);
        render(hero);
        float[] first = strands.get(0), last = strands.get(strands.size() - 1);
        assertEquals(first[0], last[0], 1f, "vertical");
        assertTrue(last[1] < first[1] - 300, "up (smaller Y) and out of the view, to the healer's end");
    }
}
