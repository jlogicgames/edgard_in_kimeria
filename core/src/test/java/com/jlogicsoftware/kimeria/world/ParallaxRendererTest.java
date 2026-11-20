package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.tiled.ForegroundGroup;
import com.jlogicsoftware.kimeria.tiled.SceneryLayer;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The parallax maths (docs/ART_DIRECTION.md, "Forest look and parallax layers") and the draw order of a real forest-1 frame (#236). */
class ParallaxRendererTest {
    private static final float VIEW_W = 640, VIEW_H = 360, FLOOR_CAMERA_TOP = 8, MOB_X = 3520;
    private static final double FOREGROUND = 1.6, MID = 0.4, FAR = 0.15, GROVE = 0.25;
    private static final int DRAW_LOG = 200_000;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final NoopBatch batch = new NoopBatch();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    @Test void snapRoundsHalfUpAsJavaMathRound() {
        assertEquals(1, ParallaxRenderer.snap(0.5)); assertEquals(0, ParallaxRenderer.snap(-0.5));
        assertEquals(3, ParallaxRenderer.snap(2.5)); assertEquals(-1, ParallaxRenderer.snap(-1.5));
        assertEquals(2, ParallaxRenderer.snap(2.4999)); assertEquals(-3, ParallaxRenderer.snap(-2.5000001));
        for (double v = -50; v < 50; v += 0.125) assertEquals(Math.round(v), ParallaxRenderer.snap(v), "v = " + v);
    }

    @Test void horizontalShiftIsTheFactorTimesTheCameraFromTheOrigin() {
        assertEquals(0, ParallaxRenderer.shift(0, 12345, 0));
        assertEquals(48, ParallaxRenderer.shift(FAR, 320, 0));
        assertEquals(0, ParallaxRenderer.shift(MID, 1, 0), "0.4 px rounds to 0");
        assertEquals(1, ParallaxRenderer.shift(MID, 2, 0), "0.8 px rounds to 1");
    }

    @Test void halfPixelOffsetsRoundUp() {
        assertEquals(3, ParallaxRenderer.shift(GROVE, 10, 0), "0.25 x 10 = 2.5 rounds to 3");
        assertEquals(-2, ParallaxRenderer.shift(GROVE, -10, 0), "-2.5 rounds half up to -2");
    }

    @Test void aFactorOfOneIsThePlayLayerWhateverTheCamera() {
        for (int cam : new int[]{0, 7, 1023, 8000}) assertEquals(100f, ParallaxRenderer.worldX(100, 1, cam, 0));
        for (int top : new int[]{-100, 8, 158}) assertEquals(96f, ParallaxRenderer.worldY(96, 1, top, FLOOR_CAMERA_TOP));
    }

    @Test void screenXAndYFollowTheCardFormulas() {
        // screenX = (x - roomX) - f * (camX - roomX): the foreground of the room at x 3520, camera 3600, an object 120 px into the room
        int camX = 3600;
        float screen = ParallaxRenderer.worldX(120, FOREGROUND, camX, MOB_X) - camX;
        assertEquals(120 - 1.6 * (camX - MOB_X), screen, 1e-3);
        assertEquals(-8f, screen);
        // map-wide layers have origin 0: a 0.4 layer at camera 1000 trails by 400
        assertEquals(-400f, ParallaxRenderer.worldX(0, MID, 1000, 0) - 1000);
        // vertical: screenY = (y - originY) - f * (camTop - originY); the steady frame is the authored one, a look up 100 px moves f x 100
        assertEquals(64f - FLOOR_CAMERA_TOP, ParallaxRenderer.worldY(64, MID, 8, FLOOR_CAMERA_TOP) - 8);
        assertEquals(64f - FLOOR_CAMERA_TOP + 40, ParallaxRenderer.worldY(64, MID, -92, FLOOR_CAMERA_TOP) - (-92), "up 100 px: a 0.4 layer lags 40 px");
        assertEquals(64f - FLOOR_CAMERA_TOP - 60, ParallaxRenderer.worldY(64, MID, 158, FLOOR_CAMERA_TOP) - 158, "down 150 px: 0.4 x 150 = 60");
    }

    @Test void theFloorCameraTopOfARoomIsItsBottomMinusTheView() {
        assertEquals(8f, ParallaxRenderer.originY(new Rectangle(0, 0, 800, 368), VIEW_H));
        assertEquals(1008f, ParallaxRenderer.originY(new Rectangle(0, 1000, 640, 368), VIEW_H));
    }

    @Test void cloudDriftIsFractionalAndWrapsBelowItsLength() {
        assertEquals(0f, ParallaxRenderer.driftOffset(0f, 1024f));
        assertEquals(2.5f, ParallaxRenderer.driftOffset(2.5f, 1024f), "not snapped to a whole pixel");
        assertEquals(1023.5f, ParallaxRenderer.driftOffset(1023.5f, 1024f));
        assertEquals(0.5f, ParallaxRenderer.driftOffset(1024.5f, 1024f), "wrapped below the length");
        assertEquals(1023.5f, ParallaxRenderer.driftOffset(-0.5f, 1024f), "never negative");
        assertEquals(0f, ParallaxRenderer.driftOffset(12f, 0f), "a layer without wrap does not drift");
        float tiny = ParallaxRenderer.driftOffset(-1e-6f, 1024f);
        assertTrue(tiny >= 0f && tiny < 1024f, "a tiny negative drift stays inside [0, wrap): " + tiny);
    }

    @Test void aNegativeDriftLayerStaysWithinItsWrap() {
        TiledMapData map = forest1();
        int clouds = indexOfLayer(map, "clouds");
        map.sceneryLayers.get(clouds).driftX = -3f;
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        for (int i = 0; i < 5000; i++) {
            renderer.update(0.1f);
            float d = renderer.driftOf(clouds);
            assertTrue(d >= 0f && d < 1024f, "step " + i + ": " + d);
        }
    }

    @Test void theFractionalDriftReachesTheBatchDraw() {
        TiledMapData map = forest1();
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        int clouds = indexOfLayer(map, "clouds");
        Set<TextureRegion> cells = regionsOf(map.sceneryLayers.get(clouds));
        Rectangle room = new Rectangle(MOB_X, 0, 800, 368), view = new Rectangle(MOB_X, FLOOR_CAMERA_TOP, VIEW_W, VIEW_H);
        draw(renderer, view, room);
        float before = leftmostScreenX(cells, view.x);
        renderer.update(0.05f);
        draw(renderer, view, room);
        assertEquals(0.15f, leftmostScreenX(cells, view.x) - before, 1e-3f, "3 px/s x 0.05 s = 0.15 px, not snapped");
    }

    private TiledMapData forest1() { return TiledMapData.load("tiles/forest-1.tmx", harness.assets()); }

    private static SceneryLayer layer(TiledMapData map, String name) {
        for (SceneryLayer l : map.sceneryLayers) if (l.name.equals(name)) return l;
        throw new AssertionError(name);
    }

    private static Set<TextureRegion> regionsOf(SceneryLayer layer) {
        Set<TextureRegion> set = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TextureRegion r : layer.cells) if (r != null) set.add(r);
        return set;
    }

    /** First recorded draw of a region of {@code regions} (its x), or NaN. */
    private float firstX(Set<TextureRegion> regions) {
        for (int i = 0; i < batch.drawnCount(); i++) if (regions.contains(batch.drawnRegion(i))) return batch.drawnX(i);
        return Float.NaN;
    }

    private void draw(ParallaxRenderer renderer, Rectangle view, Rectangle room) {
        batch.record(DRAW_LOG);
        renderer.renderBack(batch, view, ParallaxRenderer.originY(room, VIEW_H));
        renderer.renderFront(batch, view, room, ParallaxRenderer.originY(room, VIEW_H));
    }

    @Test void theMoonStaysAtTheSameScreenPositionAtEveryCamera() {
        TiledMapData map = forest1();
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        Set<TextureRegion> moon = regionsOf(layer(map, "moon"));
        Rectangle room = new Rectangle(MOB_X, 0, 800, 368);
        float[] screen = new float[3];
        float[] cameras = {MOB_X, MOB_X + 77, MOB_X + 160};
        for (int i = 0; i < cameras.length; i++) {
            draw(renderer, new Rectangle(cameras[i], FLOOR_CAMERA_TOP, VIEW_W, VIEW_H), room);
            screen[i] = firstX(moon) - cameras[i];
        }
        assertEquals(screen[0], screen[1]); assertEquals(screen[0], screen[2]);
        assertEquals(27 * 16f, screen[0], "the first drawn tile of the moon on screen, the same in every world (the piece's corner tile at column 26 is transparent: no cell, no draw)");
    }

    private static int indexOfLayer(TiledMapData map, String name) { return map.sceneryLayers.indexOf(layer(map, name)); }

    /** The smallest on-screen x (from the camera) of any draw of {@code regions}, NaN when none. */
    private float leftmostScreenX(Set<TextureRegion> regions, float camera) {
        float best = Float.NaN;
        for (int i = 0; i < batch.drawnCount(); i++) {
            float x = batch.drawnX(i) - camera;
            if (regions.contains(batch.drawnRegion(i)) && x >= 0 && (Float.isNaN(best) || x < best)) best = x;
        }
        return best;
    }

    @Test void cloudsDriftWithTimeNotWithTheCamera() {
        TiledMapData map = forest1();
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        int clouds = indexOfLayer(map, "clouds");
        Set<TextureRegion> cells = regionsOf(map.sceneryLayers.get(clouds));
        Rectangle room = new Rectangle(MOB_X, 0, 800, 368);
        Rectangle view = new Rectangle(MOB_X, FLOOR_CAMERA_TOP, VIEW_W, VIEW_H);
        assertEquals(0f, renderer.driftOf(clouds));
        draw(renderer, view, room);
        float at0 = leftmostScreenX(cells, view.x);
        for (int i = 0; i < 100; i++) renderer.update(0.1f);   // 10 s at 3 px/s
        assertEquals(30f, renderer.driftOf(clouds), 0.01f);
        draw(renderer, view, room);
        float at10 = leftmostScreenX(cells, view.x);
        assertNotEquals(at0, at10, "the clouds moved");
        view.x += 50;   // the camera moves, the clouds do not follow it
        draw(renderer, view, room);
        assertEquals(at10, leftmostScreenX(cells, view.x), "the camera does not move the clouds on screen");
        for (int i = 0; i < 4000; i++) renderer.update(0.1f);   // 400 s: past one wrap (1024 / 3 = 341 s)
        assertTrue(renderer.driftOf(clouds) >= 0 && renderer.driftOf(clouds) < 1024);
        assertEquals(3 * 410f - 1024f, renderer.driftOf(clouds), 0.5f);
        for (int i = 0; i < map.sceneryLayers.size(); i++) if (i != clouds) assertEquals(0f, renderer.driftOf(i), "only the clouds drift");
    }

    @Test void aLevelFrameDrawsSceneryThenPlayThenTheRoomsForeground() {
        Rectangle view = new Rectangle(MOB_X, FLOOR_CAMERA_TOP, VIEW_W, VIEW_H);
        Level level = harness.room("forest-1", "forest-1-mob", () -> view);
        TiledMapData map = level.map;
        batch.record(DRAW_LOG);
        level.render(batch, view);
        int count = batch.drawnCount();
        assertTrue(count > 0 && count < DRAW_LOG, "the log must hold the whole frame: " + count);
        Set<TextureRegion> sky = regionsOf(layer(map, "sky")), mid = regionsOf(layer(map, "mid"));
        assertTrue(sky.contains(batch.drawnRegion(0)), "the first thing drawn is the sky");
        ForegroundGroup mob = map.foregroundGroups.get(1), traps = map.foregroundGroups.get(0);
        Set<TextureRegion> front = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < mob.count; i++) front.add(mob.regions[i]);
        Set<TextureRegion> other = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < traps.count; i++) if (!front.contains(traps.regions[i])) other.add(traps.regions[i]);
        assertTrue(front.contains(batch.drawnRegion(count - 1)), "the last thing drawn is a piece of this room's foreground");
        int lastMid = -1, firstPlay = count, firstFront = count;
        TextureRegion terrain = map.tile(map.backgroundGrid[map.heightTiles - 1][(int) (MOB_X / 16) + 1]);
        for (int i = 0; i < count; i++) {
            TextureRegion r = batch.drawnRegion(i);
            if (mid.contains(r)) lastMid = i;
            if (r == terrain && batch.drawnY(i) >= 0 && batch.drawnY(i) < map.heightPx() && firstPlay == count) firstPlay = i;
            if (front.contains(r) && firstFront == count) firstFront = i;
            assertFalse(other.contains(r), "another room's foreground piece was drawn");
        }
        assertTrue(lastMid >= 0 && lastMid < firstPlay, "the back layers come before the play layer");
        assertTrue(firstPlay < firstFront, "the foreground comes after the play layer");
    }

    @Test void everyForestRoomSharesOneFloorCameraTop() {
        // the claim "map-wide layers never jump at a room change" holds only while this does (ParallaxRenderer javadoc)
        TiledMapData map = forest1();
        float first = Float.NaN;
        int rooms = 0;
        for (com.jlogicsoftware.kimeria.tiled.TiledObject o : map.objectGroups.get("Rooms")) {
            if (!"Room".equals(o.type)) continue;
            float origin = ParallaxRenderer.originY(new Rectangle(o.x, o.y, o.width, o.height), VIEW_H);
            if (rooms++ == 0) first = origin;
            assertEquals(first, origin, "room " + o.name + " has another floor camera top: the scenery would shift on entering it");
        }
        assertTrue(rooms > 1);
    }

    @Test void theCullRectangleHasATileOfSlackSoAShakenCameraNeverShowsAGap() {
        TiledMapData map = forest1();
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        Rectangle room = new Rectangle(MOB_X, 0, 800, 368);
        float shaken = 1.5f;   // the camera moves up to 1.5 px after visibleWorldRect was taken: the view is stale by that much
        Rectangle view = new Rectangle(MOB_X + 100, FLOOR_CAMERA_TOP, VIEW_W, VIEW_H);
        draw(renderer, view, room);
        SceneryLayer grove = layer(map, "grove");
        Set<TextureRegion> cells = regionsOf(grove);
        float leftmost = Float.POSITIVE_INFINITY, rightmost = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < batch.drawnCount(); i++) {
            if (!cells.contains(batch.drawnRegion(i))) continue;
            leftmost = Math.min(leftmost, batch.drawnX(i)); rightmost = Math.max(rightmost, batch.drawnX(i) + 16);
        }
        assertTrue(leftmost <= view.x - shaken, "the left edge of the grove reaches past the stale view: " + leftmost);
        assertTrue(rightmost >= view.x + view.width + shaken, "and so does the right edge: " + rightmost);
    }

    @Test void aLayerWithOpacityRestoresTheBatchColourItFound() {
        TiledMapData map = forest1();
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        map.sceneryLayers.get(0).opacity = 0.5f;
        com.badlogic.gdx.graphics.Color found = new com.badlogic.gdx.graphics.Color(0.25f, 0.5f, 0.75f, 0.9f);
        batch.setColor(found);
        float packed = batch.getPackedColor();
        draw(renderer, new Rectangle(MOB_X, FLOOR_CAMERA_TOP, VIEW_W, VIEW_H), new Rectangle(MOB_X, 0, 800, 368));
        assertEquals(packed, batch.getPackedColor(), "the colour in force before the layers is the colour after them, not white");
    }

    @Test void aRoomWithoutAForegroundDrawsNoForegroundButKeepsTheScenery() {
        TiledMapData map = forest1();
        ParallaxRenderer renderer = new ParallaxRenderer(map);
        Rectangle room = new Rectangle(1700, 0, 640, 368), view = new Rectangle(1700, FLOOR_CAMERA_TOP, VIEW_W, VIEW_H);
        draw(renderer, view, room);
        Set<TextureRegion> front = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ForegroundGroup g : map.foregroundGroups) for (int i = 0; i < g.count; i++) front.add(g.regions[i]);
        for (int i = 0; i < batch.drawnCount(); i++) assertFalse(front.contains(batch.drawnRegion(i)));
        assertTrue(batch.drawnCount() > 0);
    }
}
