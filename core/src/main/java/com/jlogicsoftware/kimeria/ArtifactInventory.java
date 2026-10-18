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
    private String elixirLevel;
    private final Map<Id, Pickup> spent = new LinkedHashMap<>();

    public static Set<ItemType> requiredForLevel(String level) {
        return "forest".equals(level) ? Set.of(ItemType.CHICKEN, ItemType.KEY) : Set.of();
    }

    public void beginLevel() {
        elixirLevel = null;
        pending.clear();
        levelStart.clear();
        levelStart.putAll(kept);
        spent.clear();
    }

    public void collect(String level, int objectId, ItemType type) {
        if (type == ItemType.COIN) throw new IllegalArgumentException("Coins are not artifacts");
        Id id = new Id(level, objectId);
        if (!contains(level, objectId)) pending.putIfAbsent(id, new Pickup(level, objectId, type));
    }

    public boolean contains(String level, int objectId) {
        Id id = new Id(level, objectId);
        return kept.containsKey(id) || pending.containsKey(id) || spent.containsKey(id);
    }

    public void keepRoom() {
        kept.putAll(pending);
        pending.clear();
    }

    public void resetRoom() { pending.clear(); }

    public void restartLevel() {
        elixirLevel = null;
        spent.clear();
        pending.clear();
        kept.clear();
        kept.putAll(levelStart);
    }

    public void clear() {
        elixirLevel = null;
        spent.clear();
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
        return !required.isEmpty() && required.stream().allMatch(type -> java.util.stream.Stream.concat(kept.values().stream(), spent.values().stream())
            .anyMatch(pickup -> pickup.level().equals(level) && pickup.type() == type));
    }

    /** Payment removes inventory icons but preserves the identities and collection result. */
    public boolean consumeIngredients(String level) {
        if (!hasFullSet(level) || spent.values().stream().anyMatch(p -> p.level().equals(level))) return false;
        var ids = kept.entrySet().stream().filter(e -> e.getValue().level().equals(level)
            && requiredForLevel(level).contains(e.getValue().type())).map(Map.Entry::getKey).toList();
        for (Id id : ids) spent.put(id, kept.remove(id));
        drinkBossElixir(level);
        return true;
    }

    public boolean hasIngredient(String level, ItemType type) {
        return kept.values().stream().anyMatch(p -> p.level().equals(level) && p.type() == type);
    }

    public void endBossElixir(String level) {
        if (level.equals(elixirLevel)) elixirLevel = null;
    }

    /** Called by the healer bargain (#84) after payment and immediate consumption. Run-only. */
    public void drinkBossElixir(String level) { elixirLevel = level; }

    /** Artifacts alone never grant combat bonuses. Room retries leave consumption intact. */
    public float bossWeaponDamageMultiplier(String level) {
        return level.equals(elixirLevel) ? 10f : 1f;
    }
}
