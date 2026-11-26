package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #572: the forest-boss RoomStart sat 32 px inside the floor, so the hero (and the silver thread's start anchor) sank into it. Every room entrance stands on the floor. */
class RoomStartOnFloorTest {
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void restoreGlobals() { harness.restoreGlobals(); }

    /** Measured at the spawn frame, after a retry (the arrival lock holds the hero for about half a second) and after a settle. */
    @Test void bossRoomHeroAndBossStandOnTheFloorLine() {
        Level level = harness.room("forest-2", "forest-boss");
        assertNotNull(level.boss());
        assertFeetOnFloor(level, "spawn");
        for (int i = 0; i < 60; i++) level.update(HeadlessLevelHarness.STEP);
        assertFeetOnFloor(level, "after 1 s");
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        level.player.arriveAfterReset();
        assertFeetOnFloor(level, "retry spawn");
        for (int i = 0; i < 3; i++) level.update(HeadlessLevelHarness.STEP);
        assertFeetOnFloor(level, "retry, 3 frames in");
    }

    private void assertFeetOnFloor(Level level, String when) {
        Rectangle hero = level.player.hitboxBounds(), boss = level.boss().hitboxBounds();
        assertEquals(304f, hero.y + hero.height, 0.01f, when + ": hero feet " + hero);
        assertEquals(304f, boss.y + boss.height, 0.01f, when + ": boss feet " + boss);
    }

    /** The hero's box at each RoomStart (position plus the Player hitbox) must not sit inside any solid collision rectangle. */
    @Test void everyRoomEntranceStartsOnTheFloorNotInsideIt() {
        for (String map : new String[]{"forest-0", "forest-1", "forest-2"}) {
            Level level = new Level(harness.assets(), harness.game(), new Player(harness.assets(), harness.game(), 0, 0), map, () -> new Rectangle());
            for (RoomLayout.Room room : new RoomLayout(level.map).rooms) {
                Player probe = new Player(harness.assets(), harness.game(), room.entrance().x, room.entrance().y);
                Rectangle hero = probe.hitboxBounds();
                for (TiledObject c : level.map.layer("Collisions")) {
                    if ("Platform".equals(c.type) || "QuickSand".equals(c.type)) continue;
                    assertFalse(hero.overlaps(new Rectangle(c.x, c.y, c.width, c.height)),
                        map + "/" + room.id() + ": entrance body " + hero + " is inside solid collision " + c.id);
                }
            }
        }
    }
}
