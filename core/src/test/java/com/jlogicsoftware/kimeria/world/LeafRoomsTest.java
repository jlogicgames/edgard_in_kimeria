package com.jlogicsoftware.kimeria.world;

import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code leaves} property of a {@code Room} marker (#473): parsed per room, default 0, and the leaves follow the room. */
class LeafRoomsTest {
    private static final float STEP = 1f / 60f;
    private static final int LEAVES = 10, MOB_ROOM = 0, DEADFALL_ROOM = 1, TRAPS_ROOM = 2;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Player player() { return new Player(harness.assets(), harness.game(), 0, 0); }

    @Test void theLeavesPropertyIsReadPerRoomAndDefaultsToNone() {
        Level level = harness.roomOf("forest-1", MOB_ROOM);
        assertEquals(0, level.roomLayout.rooms.get(MOB_ROOM).leaves(), "a room without the property has none");
        assertEquals(LEAVES, level.roomLayout.rooms.get(DEADFALL_ROOM).leaves());
        assertEquals(0, level.roomLayout.rooms.get(TRAPS_ROOM).leaves());
    }

    @Test void aRoomWithLeavesHasThemAndOneWithoutHasNone() {
        assertEquals(0, harness.roomOf("forest-1", MOB_ROOM).leafFx().particles().count());
        assertEquals(LEAVES, harness.roomOf("forest-1", DEADFALL_ROOM).leafFx().particles().count());
    }

    @Test void leavesAreClearedWhenTheRoomChanges() {
        Level level = harness.roomOf("forest-1", DEADFALL_ROOM);
        for (int f = 0; f < 120; f++) level.update(STEP);
        assertTrue(level.leafFx().particles().count() > 0);
        level.enterNextRoom(player());
        assertEquals(0, level.leafFx().particles().count());
        for (int f = 0; f < 600; f++) level.update(STEP);
        assertEquals(0, level.leafFx().particles().count(), "none appear in a room without leaves");
    }

    @Test void deathAndRetryRestartTheLeavesInsteadOfAddingToThem() {
        Level level = harness.roomOf("forest-1", DEADFALL_ROOM);
        for (int f = 0; f < 600; f++) level.update(STEP);
        level.resetCurrentRoom(player());
        assertEquals(LEAVES, level.leafFx().particles().count(), "a fresh set, not the old one plus new");
        level.fx().clear();
        assertEquals(LEAVES, level.leafFx().particles().count(), "the screen-effect clear (death, camera) is not the room's");
    }

    /** A trap added to the catalogue (docs/TRAPS.md) without a place in {@code Level.LEAF_AVOIDED} would let leaves start over its column. */
    @Test void everyTrapOfTheCatalogueKeepsLeavesOffItsColumn() throws IOException {
        Set<String> missing = new HashSet<>(TrapCatalogTest.table(TrapCatalogTest.read(TrapCatalogTest.TRAPS), "trap-catalogue").keySet());
        missing.removeAll(Level.LEAF_AVOIDED);
        assertTrue(missing.isEmpty(), "trap types missing from Level.LEAF_AVOIDED: " + missing);
    }
}
