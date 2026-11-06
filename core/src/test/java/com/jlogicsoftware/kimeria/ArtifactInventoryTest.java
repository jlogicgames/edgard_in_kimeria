package com.jlogicsoftware.kimeria;

import com.jlogicsoftware.kimeria.entity.items.ItemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactInventoryTest {
    private static ArtifactInventory inventory() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.requireForLevel("forest-2", java.util.Set.of(ItemType.BAT, ItemType.MUSHROOM));
        return inventory;
    }

    @Test void artifactsAloneNeverGrantCombatBonus() {
        ArtifactInventory inventory = inventory();
        inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.collect("forest-2", 94, ItemType.BAT);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.collect("forest-2", 95, ItemType.MUSHROOM);
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"), "Artifacts alone still grant no damage bonus");
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-1"), "An empty set does not grant a reward");
    }

    @Test void aMushroomAloneAndArtifactsFromDifferentLevelsDoNotCompleteTheSet() {
        ArtifactInventory inventory = inventory();
        inventory.collect("forest-2", 95, ItemType.MUSHROOM);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.collect("other", 94, ItemType.BAT);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
    }

    @Test void consumedElixirSurvivesRoomRetryButNeverLeaksToAnotherLevelOrRun() {
        ArtifactInventory inventory = inventory();
        inventory.beginLevel();
        inventory.drinkBossElixir("forest-2");
        assertEquals(10f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.resetRoom();
        assertEquals(10f, inventory.bossWeaponDamageMultiplier("forest-2"));
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("other"));
        inventory.restartLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.drinkBossElixir("forest-2"); inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.drinkBossElixir("forest-2"); inventory.clear();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
    }

    @Test void identityUsesBothLevelAndMapObjectIdAndReturnedItemsAreImmutable() {
        ArtifactInventory inventory = inventory();
        inventory.collect("forest-2", 94, ItemType.BAT);
        inventory.collect("forest-2", 94, ItemType.BAT);
        inventory.keepRoom();
        inventory.collect("forest-2", 94, ItemType.BAT);
        inventory.collect("other", 94, ItemType.BAT);
        assertEquals(2, inventory.items().size());
        assertThrows(UnsupportedOperationException.class, () -> inventory.items().clear());
        assertThrows(IllegalArgumentException.class, () -> inventory.collect("forest-2", 37, ItemType.COIN));
    }

    @Test void restartLevelRestoresItsEntryStateAndNewRunClearsEverything() {
        ArtifactInventory inventory = inventory();
        inventory.collect("earlier", 1, ItemType.MUSHROOM);
        inventory.keepRoom();
        inventory.beginLevel();
        inventory.collect("forest-2", 94, ItemType.BAT);
        inventory.keepRoom();
        inventory.collect("forest-2", 95, ItemType.MUSHROOM);
        inventory.restartLevel();
        assertEquals(1, inventory.items().size());
        assertEquals("earlier", inventory.items().get(0).level());
        assertFalse(inventory.contains("forest-2", 94));
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-2"));
        inventory.clear();
        inventory.restartLevel();
        assertTrue(inventory.items().isEmpty());
    }
}
