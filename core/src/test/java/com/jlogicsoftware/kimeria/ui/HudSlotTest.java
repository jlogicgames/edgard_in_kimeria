package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.MetricsFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Where a pickup flight lands (D132, #557): the slots come from the layout the HUD draws. */
class HudSlotTest {
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.font = new MetricsFont(FontSizes.COUNT); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    @Test void theCoinLandsOnTheCoinIconAndAnIngredientOnItsOwnSlot() {
        Hud hud = new Hud(harness.assets(), harness.game(), 640, 360);
        Vector2 out = new Vector2();
        assertTrue(hud.slotCenter(ItemType.COIN, out));
        assertEquals(26f, out.x, 0f, "icon x 10 plus half of 32");
        assertEquals(26f, out.y, 0f);
        assertFalse(hud.slotCenter(ItemType.BAT, out), "an ingredient the hero does not hold has no slot");
        harness.game().artifacts().collect("forest-2", 1, ItemType.BAT);
        harness.game().artifacts().collect("forest-2", 2, ItemType.MUSHROOM);
        assertTrue(hud.slotCenter(ItemType.BAT, out));
        float bat = out.x;
        assertTrue(hud.slotCenter(ItemType.MUSHROOM, out));
        assertEquals(bat + 32f + 8f, out.x, 0f, "the next slot is one icon and one gap along");
        assertTrue(bat > 26f + 16f, "the ingredients sit right of the coin counter");
    }
}
