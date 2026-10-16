package com.jlogicsoftware.kimeria;

import com.jlogicsoftware.kimeria.entity.items.ItemType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactInventoryTest {
    @Test void artifactsAloneNeverGrantCombatBonus() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.collect("forest", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.collect("forest", 95, ItemType.KEY);
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"), "Artifacts alone still grant no damage bonus");
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest-1"), "An empty set does not grant a reward");
    }

    @Test void aKeyAloneAndArtifactsFromDifferentLevelsDoNotCompleteTheSet() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.collect("forest", 95, ItemType.KEY);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.collect("other", 94, ItemType.CHICKEN);
        inventory.keepRoom();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
    }

    @Test void consumedElixirSurvivesRoomRetryButNeverLeaksToAnotherLevelOrRun() {
        ArtifactInventory inventory = new ArtifactInventory();
        inventory.beginLevel();
        inventory.drinkBossElixir("forest");
        assertEquals(10f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.resetRoom();
        assertEquals(10f, inventory.bossWeaponDamageMultiplier("forest"));
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("other"));
        inventory.restartLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.drinkBossElixir("forest"); inventory.beginLevel();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.drinkBossElixir("forest"); inventory.clear();
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
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
        assertEquals(1f, inventory.bossWeaponDamageMultiplier("forest"));
        inventory.clear();
        inventory.restartLevel();
        assertTrue(inventory.items().isEmpty());
    }
}
