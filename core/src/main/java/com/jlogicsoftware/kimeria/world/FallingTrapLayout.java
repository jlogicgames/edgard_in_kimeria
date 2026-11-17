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
    /** A validated trap: where it hangs, its trigger zone, the y its bottom lands on; a stone also the top of its lip (#360). */
    record Spec(Rectangle body, Rectangle zone, float landing, boolean stone, FallingStone.Skin skin, float lipTop) { }

    /** Optional stone property (#360): the world y of the lip's top edge (absolute px, Y grows downward). */
    static final String LIP_TOP = "lipTop";
    /** Every property a falling trap reads; any other key (a misspelling such as {@code liptop}) fails the load. */
    static final java.util.Set<String> PROPERTIES = java.util.Set.of("zoneX", "zoneY", "zoneWidth", "zoneHeight", "skin", LIP_TOP);

    /**
     * The camera's top edge while the hero stands on the floor of {@code room}: {@code Player.cameraTarget} clamps it to the room's
     * bottom, so a room taller than the view hides its top rows (#360). A room no taller than the view is centred.
     */
    static float floorCameraTop(Rectangle room, float viewHeight) {
        return room.height <= viewHeight ? room.y + (room.height - viewHeight) / 2f : room.y + room.height - viewHeight;
    }

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

    /** A collision block (not a one-way platform or quicksand) of the room ends at {@code top} over the stone's column. */
    private static boolean underCollision(float top, Rectangle body, RoomLayout layout, int roomIndex, TiledMapData map) {
        for (TiledObject c : map.layer("Collisions")) {
            if (layout.owner(c) != roomIndex || "QuickSand".equals(c.type) || "Platform".equals(c.type)) continue;
            if (c.y + c.height == top && xOverlap(c.x, c.width, body.x, body.width)) return true;
        }
        return false;
    }

    /**
     * {@code top} is the bottom face of a drawn background tile row over the stone's column: every tile cell the column covers in
     * the row above is drawn and every cell in the row below is empty (the lip hangs in open air). Forest-2's block over its stone
     * is scenery tiles with no collision, so the collision rule alone could not find it.
     */
    private static boolean underTiles(float top, Rectangle body, TiledMapData map) {
        int th = map.tileHeight, tw = map.tileWidth;
        if (map.backgroundGrid == null || th <= 0 || tw <= 0 || top % th != 0) return false;
        int below = (int) (top / th), above = below - 1;
        int first = (int) (body.x / tw), last = (int) ((body.x + body.width - 1) / tw);
        if (above < 0 || below >= map.heightTiles || first < 0 || last >= map.widthTiles) return false;
        for (int c = first; c <= last; c++)
            if (map.backgroundGrid[above][c] == 0 || map.backgroundGrid[below][c] != 0) return false;
        return true;
    }

    private static boolean xOverlap(float ax, float aw, float bx, float bw) { return ax < bx + bw && ax + aw > bx; }

    /** @param viewHeight the camera's height in world px (the stone's lip must be on screen while the hero stands on the floor) */
    static Spec validate(TiledObject o, RoomLayout layout, int roomIndex, TiledMapData map, float viewHeight) {
        boolean stone = "FallingStone".equals(o.type);
        for (String key : o.properties.keySet())
            if (!PROPERTIES.contains(key)) throw bad(o, "unknown property '" + key + "' (a falling trap reads " + new java.util.TreeSet<>(PROPERTIES) + ")");
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
        float lipTop = Float.NaN;
        if (!stone && o.properties.containsKey(LIP_TOP)) throw bad(o, LIP_TOP + " applies to a FallingStone only");
        if (stone) {
            float visible = floorCameraTop(room, viewHeight);
            if (!o.properties.containsKey(LIP_TOP)) lipTop = Math.max(body.y, visible);
            else {
                lipTop = number(o, LIP_TOP);
                if (lipTop < visible)
                    throw bad(o, LIP_TOP + " " + lipTop + " is above the camera's top edge (y " + visible + ") while the hero stands on the floor: the cue would be off screen");
                if (lipTop < body.y) throw bad(o, LIP_TOP + " " + lipTop + " is above the stone's start (y " + body.y + ")");
                if (lipTop + skin.lip().height() > landing) throw bad(o, LIP_TOP + " " + lipTop + " puts the lip below the floor it lands on (y " + landing + ")");
                // D45's rule for the stone's own start, for its lip: on the default line, or hanging from something solid.
                if (lipTop != Math.max(body.y, visible) && !underCollision(lipTop, body, layout, roomIndex, map) && !underTiles(lipTop, body, map))
                    throw bad(o, LIP_TOP + " " + lipTop + " floats in the column: put it on the default line (y " + Math.max(body.y, visible)
                        + ") or directly under a solid block or a drawn tile row over the stone");
            }
        }
        return new Spec(body, zone, landing, stone, skin, lipTop);
    }
}
