package com.jlogicsoftware.kimeria;

import com.jlogicsoftware.kimeria.entity.items.ItemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactInventoryTest {
    @Test void onlyTheCommittedCompleteSetWeakensTheBoss() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.beginLevel();
        assertEquals(1f, inventory.bossEnergyMultiplier("forest"));
        inventory.collect("forest", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossEnergyMultiplier("forest"));
        inventory.collect("forest", 95, ItemType.KEY);
        assertEquals(1f, inventory.bossEnergyMultiplier("forest"));
        inventory.keepRoom();
        assertEquals(0.1f, inventory.bossEnergyMultiplier("forest"));
        inventory.beginLevel();
        assertEquals(0.1f, inventory.bossEnergyMultiplier("forest"), "Retained across level transitions");
        assertEquals(1f, inventory.bossEnergyMultiplier("forest-1"), "An empty set does not grant a reward");
    }

    @Test void aKeyAloneAndArtifactsFromDifferentLevelsDoNotCompleteTheSet() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.collect("forest", 95, ItemType.KEY);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossEnergyMultiplier("forest"));
        inventory.collect("other", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossEnergyMultiplier("forest"));
    }

    @Test void identityUsesBothLevelAndMapObjectIdAndReturnedItemsAreImmutable() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.collect("forest", 94, ItemType.CHICKEN);
        inventory.collect("forest", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        inventory.collect("forest", 94, ItemType.CHICKEN);
        inventory.collect("other", 94, ItemType.CHICKEN);
        assertEquals(2, inventory.items().size());
        assertThrows(UnsupportedOperationException.class, () -> inventory.items().clear());
        assertThrows(IllegalArgumentException.class, () -> inventory.collect("forest", 37, ItemType.COIN));
    }

    @Test void restartLevelRestoresItsEntryStateAndNewRunClearsEverything() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.collect("earlier", 1, ItemType.KEY);
        inventory.keepRoom();
        inventory.beginLevel();
        inventory.collect("forest", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        inventory.collect("forest", 95, ItemType.KEY);
        inventory.restartLevel();
        assertEquals(1, inventory.items().size());
        assertEquals("earlier", inventory.items().get(0).level());
        assertFalse(inventory.contains("forest", 94));
        assertEquals(1f, inventory.bossEnergyMultiplier("forest"));
        inventory.clear();
        inventory.restartLevel();
        assertTrue(inventory.items().isEmpty());
    }
}
