package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.FallingStone;
import com.jlogicsoftware.kimeria.entity.objects.FallingTrap;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;

/**
 * Authoring checks and geometry of the {@code FallingBranch} / {@code FallingStone} Tiled objects (D45).
 * {@link Level} runs {@link #validate} for every such object of every room when the level is constructed, so
 * a broken trap fails the load, never mid-play. Raw property strings are parsed here (NaN, infinity and
 * unparsable numbers are rejected); the shared {@code TiledObject.propFloat} silently falls back to 0.
 * tools/check_tiled_links.py mirrors these rules for maps.
 */
final class FallingTrapLayout {
    /** A validated trap: where it hangs, its trigger zone, the y its bottom lands on. */
    record Spec(Rectangle body, Rectangle zone, float landing, boolean stone, FallingStone.Skin skin) { }

    private FallingTrapLayout() { }

    static boolean isFallingTrap(String type) { return "FallingBranch".equals(type) || "FallingStone".equals(type); }

    private static String describe(TiledObject o) {
        return o.type + " '" + o.name + "' [" + o.id + "] at (" + (int) o.x + ", " + (int) o.y + ")";
    }

    private static IllegalArgumentException bad(TiledObject o, String why) {
        return new IllegalArgumentException(describe(o) + ": " + why);
    }

    private static float number(TiledObject o, String key) {
        String raw = o.properties.get(key);
        if (raw == null || raw.isBlank()) throw bad(o, "needs property " + key);
        float v;
        try {
            v = Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            throw bad(o, "property " + key + " '" + raw + "' is not a number");
        }
        if (Float.isNaN(v) || Float.isInfinite(v)) throw bad(o, "property " + key + " '" + raw + "' is not a finite number");
        return v;
    }

    private static boolean xOverlap(float ax, float aw, float bx, float bw) { return ax < bx + bw && ax + aw > bx; }

    static Spec validate(TiledObject o, RoomLayout layout, int roomIndex, TiledMapData map) {
        boolean stone = "FallingStone".equals(o.type);
        RoomLayout.Room roomData = layout.rooms.get(roomIndex);
        Rectangle room = roomData.bounds();
        if (!(o.width > 0) || !(o.height > 0)) throw bad(o, "needs a positive size");
        Rectangle body = new Rectangle(o.x, o.y, o.width, o.height);
        if (body.x < room.x || body.y < room.y || body.x + body.width > room.x + room.width
            || body.y + body.height > room.y + room.height)
            throw bad(o, "must start inside its room (never in the D38 ceiling slab)");

        float zx = number(o, "zoneX"), zy = number(o, "zoneY");
        float zw = number(o, "zoneWidth"), zh = number(o, "zoneHeight");
        if (zw <= 0 || zh <= 0) throw bad(o, "zone needs a positive width and height");
        Rectangle zone = new Rectangle(zx, zy, zw, zh);
        if (zone.x < room.x || zone.y < room.y || zone.x + zone.width > room.x + room.width
            || zone.y + zone.height > room.y + room.height) throw bad(o, "zone must lie inside its room");

        FallingStone.Skin skin = null;
        if (stone) skin = FallingStone.skin(o.prop("skin", FallingStone.DEFAULT_SKIN)); // Unknown skin fails loudly.

        float bottom = body.y + body.height;
        float landing = Float.MAX_VALUE;
        for (TiledObject c : map.layer("Collisions")) {
            if (layout.owner(c) != roomIndex || "QuickSand".equals(c.type)) continue;
            if (xOverlap(c.x, c.width, body.x, body.width) && c.y >= bottom) landing = Math.min(landing, c.y);
        }
        if (landing == Float.MAX_VALUE) throw bad(o, "has no solid floor below it in its room");
        float flight = FallingTrap.flightSeconds(landing - bottom);
        if (flight < FallingTrap.MIN_FLIGHT_SECONDS)
            throw bad(o, "drop is too short (flight " + flight + " s, minimum " + FallingTrap.MIN_FLIGHT_SECONDS + " s)");
        // Mechanisms move or toggle (a lever-raised Wall, an Escalator, a FallingPlatform): the landing could not be fixed.
        for (TiledObject m : map.layer("SpawnPoints")) {
            if (!("Actionable".equals(m.type) || "Escalator".equals(m.type) || "FallingPlatform".equals(m.type))) continue;
            if (xOverlap(m.x, m.width, body.x, body.width) && m.y < landing && m.y + m.height > bottom)
                throw bad(o, "a moving or switchable " + m.type + " [" + m.id + "] stands in its fall; its landing could not be fixed");
        }
        // A walking hero must be able to enter the zone, and it must be under the trap.
        if (!xOverlap(zone.x, zone.width, body.x, body.width))
            throw bad(o, "zone must overlap the lethal column (the trap's x-range)");
        if (zone.y + zone.height <= landing - FallingTrap.HERO_BODY_HEIGHT || zone.y >= landing)
            throw bad(o, "zone must overlap the hero's body standing on the floor (the " + FallingTrap.HERO_BODY_HEIGHT
                + " px above y " + landing + "), or a walking hero can never trigger it");
        // Entering the room must not arm the trap with the hero already in its lethal column.
        Rectangle hero = new Rectangle(roomData.entrance().x + FallingTrap.HERO_BODY_OFFSET_X,
            roomData.entrance().y + FallingTrap.HERO_BODY_OFFSET_Y, FallingTrap.HERO_BODY_WIDTH, FallingTrap.HERO_BODY_HEIGHT);
        if (hero.overlaps(zone) && xOverlap(hero.x, hero.width, body.x, body.width))
            throw bad(o, "the room entrance starts the hero inside the zone and the lethal column");
        if (stone) {
            boolean atTop = body.y == room.y;
            boolean underSolid = false;
            for (TiledObject c : map.layer("Collisions")) {
                if (layout.owner(c) != roomIndex || "QuickSand".equals(c.type) || "Platform".equals(c.type)) continue;
                if (c.y + c.height == body.y && xOverlap(c.x, c.width, body.x, body.width)) underSolid = true;
            }
            if (!atTop && !underSolid)
                throw bad(o, "a stone must start at the room top or directly under a solid block, or it pops into view in mid-air");
        }
        return new Spec(body, zone, landing, stone, skin);
    }
}
