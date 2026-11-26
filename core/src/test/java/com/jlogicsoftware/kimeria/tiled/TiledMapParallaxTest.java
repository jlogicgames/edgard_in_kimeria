package com.jlogicsoftware.kimeria.tiled;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parser keeps the parallax scenery apart from the play layer (#236): scenery layers are recognised by a parallax attribute or the
 * property {@code scenery}, carry their factors, offsets and drift, never reach {@code backgroundGrid}; the foreground object groups
 * give one group per room of tile objects (top edge, size, flips). Checked on the real forest-1 map and on small XML.
 */
class TiledMapParallaxTest {
    private static final int FOREST_TILE_COUNT = 140;
    private static final int BURIED_GIDS_FIRST = 71, BURIED_GIDS_LAST = 73;
    private static final double FOREGROUND_FACTOR = 1.6, NEAR_FACTOR = 2.2;
    private static final float MOB_ROOM_X = 3520;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private TiledMapData map;

    @BeforeEach void setUp() {
        harness.setUp();
        map = TiledMapData.load("tiles/forest-1.tmx", harness.assets());
    }

    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private SceneryLayer layer(String name) {
        for (SceneryLayer l : map.sceneryLayers) if (l.name.equals(name)) return l;
        throw new AssertionError("no scenery layer " + name);
    }

    @Test void sceneryLayersKeepTheirFileOrderWhichIsTheirDrawOrder() {
        StringBuilder names = new StringBuilder();
        for (SceneryLayer l : map.sceneryLayers) names.append(l.name).append(' ');
        assertEquals("sky stars moon clouds far grove mid play-above play-below ", names.toString());
    }

    @Test void factorsOffsetsAndCloudDriftAreRead() {
        assertEquals(0.0, layer("sky").parallaxX); assertEquals(0.0, layer("moon").parallaxY);
        assertEquals(0.15, layer("far").parallaxX, "a double, as the mock's 0.15, not the float 0.15000000596"); assertEquals(0.25, layer("grove").parallaxY);
        assertEquals(0.4, layer("mid").parallaxX); assertEquals(-32f, layer("mid").offsetY);
        assertEquals(-16f, layer("far").offsetY); assertEquals(-16f, layer("grove").offsetY);
        assertEquals(1.0, layer("play-above").parallaxX); assertEquals(-16f, layer("play-above").offsetY);
        assertEquals(map.heightPx(), layer("play-below").offsetY); assertEquals(1, layer("play-below").rows);
        assertEquals(3f, layer("clouds").driftX); assertEquals(1024f, layer("clouds").wrapX);
        assertEquals(0f, layer("far").driftX);
    }

    @Test void sceneryNeverReachesThePlayLayer() {
        for (int[] row : map.backgroundGrid) for (int gid : row) assertTrue(gid <= FOREST_TILE_COUNT, "a scenery gid in the play layer: " + gid);
        int bottom = map.heightTiles - 1;
        for (int gid : map.backgroundGrid[bottom]) assertFalse(gid >= 62 && gid <= 64, "the bottom ground row is the buried variant, not the plain tile: " + gid);
        boolean buried = false;
        for (int gid : map.backgroundGrid[bottom]) buried |= gid >= BURIED_GIDS_FIRST && gid <= BURIED_GIDS_LAST;
        assertTrue(buried);
        assertEquals(map.widthTiles, map.backgroundGrid[0].length);
    }

    @Test void everyScenerySizeMatchesItsCells() {
        for (SceneryLayer l : map.sceneryLayers) {
            assertEquals(l.columns * l.rows, l.cells.length, l.name);
            boolean any = false;
            for (TextureRegion r : l.cells) any |= r != null;
            assertTrue(any, l.name + " has no tile");
        }
    }

    @Test void oneForegroundGroupPerRoomWithTopEdgesAndSizes() {
        assertEquals(24, map.foregroundGroups.size(), "every room of forest-1 has a sharp group and a near one (D143): all twelve sharp groups first, the escalators, the traps (split from the old course, #500, D110) and the mob room first (#236)");
        ForegroundGroup escalators = map.foregroundGroups.get(0), traps = map.foregroundGroups.get(1), mob = map.foregroundGroups.get(2);
        assertEquals(0f, escalators.roomX); assertEquals(640f, traps.roomX); assertEquals(MOB_ROOM_X, mob.roomX);
        assertEquals(FOREGROUND_FACTOR, mob.factorX); assertEquals(FOREGROUND_FACTOR, mob.factorY);
        int canopy = indexOf(mob, "near-canopy");
        assertEquals(-88f, mob.x[canopy]); assertEquals(0f, mob.y[canopy], "Tiled stores the bottom edge (112), the parser keeps the top (0)");
        assertEquals(224f, mob.width[canopy]); assertEquals(112f, mob.height[canopy]);
        assertNotNull(mob.regions[canopy]);
        assertEquals(-88f, escalators.x[indexOf(escalators, "near-canopy")], "the escalators' canopy is in the start corner");
        assertEquals(540f, traps.x[indexOf(traps, "near-canopy")], "the traps room's canopy is its own, at the end corner");
        assertTrue(indexOf(mob, "vine-curtain") >= 0);
        assertEquals(-1, indexOf(escalators, "vine-curtain"), "the pieces of one room are not in another's group");
        for (int i = 0; i < mob.count; i++) assertNotNull(mob.regions[i], mob.names[i]);
    }

    @Test void everyRoomHasANearerGroupOfGiantLeavesAfterItsSharpOne() {
        int rooms = map.foregroundGroups.size() / 2;
        for (int i = 0; i < rooms; i++) {
            ForegroundGroup sharp = map.foregroundGroups.get(i), near = map.foregroundGroups.get(rooms + i);
            assertEquals(sharp.name.replace("foreground-", "foreground-near-"), near.name, "drawn after every sharp group, so over its room's");
            assertEquals(sharp.roomX, near.roomX);
            assertEquals(NEAR_FACTOR, near.factorX); assertEquals(NEAR_FACTOR, near.factorY);
            assertTrue(near.factorX > sharp.factorX, "the blurred leaves are the nearest layer");
            assertTrue(near.count > 0, near.name);
            for (int p = 0; p < near.count; p++) assertTrue(near.names[p].startsWith("edge-giant-"), near.names[p]);
            for (int p = 0; p < sharp.count; p++) assertFalse(sharp.names[p].startsWith("edge-giant-"), sharp.names[p]);
        }
    }

    private static int indexOf(ForegroundGroup group, String name) {
        for (int i = 0; i < group.count; i++) if (group.names[i].equals(name)) return i;
        return -1;
    }

    @Test void aLayerIsSceneryByAParallaxAttributeOrThePropertyOnly() {
        assertTrue(TiledMapData.isScenery(MiniXml.parse("<layer name=\"a\" parallaxx=\"0.4\"/>")));
        assertTrue(TiledMapData.isScenery(MiniXml.parse("<layer name=\"a\" parallaxy=\"0\"/>")));
        assertTrue(TiledMapData.isScenery(MiniXml.parse("<layer name=\"a\"><properties><property name=\"scenery\" value=\"true\"/></properties></layer>")));
        assertFalse(TiledMapData.isScenery(MiniXml.parse("<layer name=\"Background\" width=\"4\"/>")));
        assertFalse(TiledMapData.isScenery(MiniXml.parse("<layer name=\"b\"><properties><property name=\"scenery\" value=\"false\"/></properties></layer>")));
    }

    private String loadFailure(String map) {
        RuntimeException error = assertThrows(RuntimeException.class, () -> TiledMapData.load("tiles/" + map + ".tmx", harness.assets()));
        return error.getCause().getMessage();
    }

    @Test void aForegroundGroupNeedsItsRoomxAndItsRoom() {
        assertEquals(1, TiledMapData.load("tiles/fg-ok.tmx", harness.assets()).foregroundGroups.size());
        assertEquals(2, TiledMapData.load("tiles/fg-near-ok.tmx", harness.assets()).foregroundGroups.size(), "foreground-near-<room> belongs to <room>");
        assertTrue(loadFailure("fg-near-wrong-room").contains("no Room object is named"), "a near group needs its room too");
        assertTrue(loadFailure("fg-no-roomx").contains("roomx"));
        assertTrue(loadFailure("fg-wrong-room").contains("no Room object is named two"), "the group name is foreground-<room name>");
        assertTrue(loadFailure("fg-wrong-x").contains("is not the x of room one"), "roomx must be the room's x");
    }

    @Test void everyShippedForegroundGroupIsNamedAfterItsRoom() {
        for (ForegroundGroup g : map.foregroundGroups) {
            boolean found = false;
            for (TiledObject room : map.objectGroups.get("Rooms")) found |= "Room".equals(room.type) && (g.name.equals("foreground-" + room.name) || g.name.equals("foreground-near-" + room.name)) && room.x == g.roomX;
            assertTrue(found, g.name);
        }
    }

    @Test void aFlippedSceneryCellIsRefusedLoudly() {
        assertTrue(loadFailure("scenery-flipped").contains("flipped"));
    }

    @Test void theForegroundPiecesAreSubRectanglesOfOnePackedSheet() {
        java.util.List<String> asked = new java.util.ArrayList<>();
        com.jlogicsoftware.kimeria.Assets recording = new com.jlogicsoftware.kimeria.Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) {
                asked.add(path + " " + (int) x + " " + (int) y + " " + (int) w + " " + (int) h);
                return new TextureRegion();
            }
        };
        TiledMapData loaded = TiledMapData.load("tiles/forest-1.tmx", recording);
        ForegroundGroup traps = loaded.foregroundGroups.get(1);
        int fern = indexOf(traps, "near-fern");
        assertTrue(fern >= 0);
        long sheetRegions = asked.stream().filter(r -> r.startsWith("images/parallax/forest-fg.png ")).count();
        assertTrue(sheetRegions >= loaded.foregroundGroups.get(0).count, "every piece is a region of the one sheet: " + sheetRegions);
        assertTrue(asked.stream().noneMatch(r -> r.contains("parallax/forest-fg/")), "no per-piece image files");
        assertEquals(224f, traps.width[fern], "the drawn size is the object's, the region is the tile's sub-rectangle");
    }

    @Test void aMapWithoutSceneryHasNone() {
        TiledMapData plain = TiledMapData.load("tiles/room-test.tmx", harness.assets());   // every shipped forest map has layers since #236
        assertTrue(plain.sceneryLayers.isEmpty());
        assertTrue(plain.foregroundGroups.isEmpty());
    }
}
