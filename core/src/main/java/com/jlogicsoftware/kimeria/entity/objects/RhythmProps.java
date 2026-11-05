package com.jlogicsoftware.kimeria.entity.objects;

import com.jlogicsoftware.kimeria.tiled.TiledObject;

/** Strict reading of the rhythmic traps' map properties: a typo must fail the load, never fall back to a default. */
final class RhythmProps {
    private RhythmProps() { }

    /** The property as a finite number, {@code fallback} when absent; "1,5", "NaN" and "Infinity" throw. */
    static float number(TiledObject o, String key, float fallback) {
        String raw = o.properties.get(key);
        if (raw == null || raw.isBlank()) return fallback;
        float value;
        try {
            value = Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("property " + key + " = '" + raw + "' is not a number");
        }
        if (!Float.isFinite(value)) throw new IllegalArgumentException("property " + key + " = '" + raw + "' is not finite");
        return value;
    }
}
