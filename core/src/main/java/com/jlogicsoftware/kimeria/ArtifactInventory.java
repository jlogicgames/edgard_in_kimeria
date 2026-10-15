package com.jlogicsoftware.kimeria;

import com.jlogicsoftware.kimeria.entity.items.ItemType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Run-only artifacts. Pending pickups are visible, but only room exits secure them. */
public final class ArtifactInventory {
    public record Pickup(String level, int objectId, ItemType type) { }
    private record Id(String level, int objectId) { }

    private final Map<Id, Pickup> kept = new LinkedHashMap<>();
    private final Map<Id, Pickup> pending = new LinkedHashMap<>();
    private final Map<Id, Pickup> levelStart = new LinkedHashMap<>();

    public static Set<ItemType> requiredForLevel(String level) {
        return "forest".equals(level) ? Set.of(ItemType.CHICKEN, ItemType.KEY) : Set.of();
    }

    public void beginLevel() {
        pending.clear();
        levelStart.clear();
        levelStart.putAll(kept);
    }

    public void collect(String level, int objectId, ItemType type) {
        if (type == ItemType.COIN) throw new IllegalArgumentException("Coins are not artifacts");
        Id id = new Id(level, objectId);
        if (!kept.containsKey(id)) pending.putIfAbsent(id, new Pickup(level, objectId, type));
    }

    public boolean contains(String level, int objectId) {
        Id id = new Id(level, objectId);
        return kept.containsKey(id) || pending.containsKey(id);
    }

    public void keepRoom() {
        kept.putAll(pending);
        pending.clear();
    }

    public void resetRoom() { pending.clear(); }

    public void restartLevel() {
        pending.clear();
        kept.clear();
        kept.putAll(levelStart);
    }

    public void clear() {
        kept.clear();
        pending.clear();
        levelStart.clear();
    }

    /** Collection order, including provisional items; callers cannot mutate the inventory. */
    public List<Pickup> items() {
        List<Pickup> result = new ArrayList<>(kept.values());
        result.addAll(pending.values());
        return List.copyOf(result);
    }

    public boolean hasFullSet(String level) {
        Set<ItemType> required = requiredForLevel(level);
        return !required.isEmpty() && required.stream().allMatch(type -> kept.values().stream()
            .anyMatch(pickup -> pickup.level().equals(level) && pickup.type() == type));
    }

    /** Apply once to the boss's starting energy, using the source level of its artifacts. */
    public float bossEnergyMultiplier(String level) { return hasFullSet(level) ? 0.1f : 1f; }
}
