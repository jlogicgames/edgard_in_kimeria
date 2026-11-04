package com.jlogicsoftware.kimeria;

import com.jlogicsoftware.kimeria.entity.items.ItemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactInventoryTest {
    private static ArtifactInventory inventory() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.requireForLevel("forest-3", java.util.Set.of(ItemType.CHICKEN, ItemType.KEY));
        return inventory;
    }

    @Test void artifactsAloneNeverGrantCombatBonus() {
        ArtifactInventory inventory = inventory();
        inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.collect("forest-3", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.collect("forest-3", 95, ItemType.KEY);
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"), "Artifacts alone still grant no damage bonus");
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-1"), "An empty set does not grant a reward");
    }

    @Test void aKeyAloneAndArtifactsFromDifferentLevelsDoNotCompleteTheSet() {
        ArtifactInventory inventory = inventory();
        inventory.collect("forest-3", 95, ItemType.KEY);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.collect("other", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
    }

    @Test void consumedElixirSurvivesRoomRetryButNeverLeaksToAnotherLevelOrRun() {
        ArtifactInventory inventory = inventory();
        inventory.beginLevel();
        inventory.drinkBossElixir("forest-3");
        assertEquals(10f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.resetRoom();
        assertEquals(10f, inventory.bossWeaponDamageMultiplier("forest-3"));
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("other"));
        inventory.restartLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.drinkBossElixir("forest-3"); inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.drinkBossElixir("forest-3"); inventory.clear();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
    }

    @Test void identityUsesBothLevelAndMapObjectIdAndReturnedItemsAreImmutable() {
        ArtifactInventory inventory = inventory();
        inventory.collect("forest-3", 94, ItemType.CHICKEN);
        inventory.collect("forest-3", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        inventory.collect("forest-3", 94, ItemType.CHICKEN);
        inventory.collect("other", 94, ItemType.CHICKEN);
        assertEquals(2, inventory.items().size());
        assertThrows(UnsupportedOperationException.class, () -> inventory.items().clear());
        assertThrows(IllegalArgumentException.class, () -> inventory.collect("forest-3", 37, ItemType.COIN));
    }

    @Test void restartLevelRestoresItsEntryStateAndNewRunClearsEverything() {
        ArtifactInventory inventory = inventory();
        inventory.collect("earlier", 1, ItemType.KEY);
        inventory.keepRoom();
        inventory.beginLevel();
        inventory.collect("forest-3", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        inventory.collect("forest-3", 95, ItemType.KEY);
        inventory.restartLevel();
        assertEquals(1, inventory.items().size());
        assertEquals("earlier", inventory.items().get(0).level());
        assertFalse(inventory.contains("forest-3", 94));
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-3"));
        inventory.clear();
        inventory.restartLevel();
        assertTrue(inventory.items().isEmpty());
    }
}
