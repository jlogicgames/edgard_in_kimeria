package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ordered, non-overlapping rooms. Coordinates include Tiled layer offsets. */
public final class RoomLayout {
    public record Room(String id, Rectangle bounds, Vector2 entrance, Rectangle exit) { }
    public final List<Room> rooms;
    public RoomLayout(TiledMapData map) {
        List<TiledObject> markers = map.layer("Rooms");
        List<Room> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (TiledObject marker : markers) {
            if (!Set.of("Room", "RoomStart", "RoomExit").contains(marker.type))
                throw new IllegalArgumentException("Unknown room marker: " + marker.type);
            if (!"Room".equals(marker.type)) continue;
            if (marker.name.isEmpty() || !ids.add(marker.name))
                throw new IllegalArgumentException("Room needs a unique name: " + marker.name);
            Rectangle bounds = rect(marker);
            if (bounds.width <= 0 || bounds.height <= 0 || bounds.x < 0 || bounds.y < 0
                || bounds.x + bounds.width > map.widthPx() || bounds.y + bounds.height > map.heightPx())
                throw new IllegalArgumentException("Invalid room bounds: " + marker.name);
            for (Room room : result) if (room.bounds.overlaps(bounds))
                throw new IllegalArgumentException("Overlapping rooms: " + marker.name);
            TiledObject start = matching(markers, "RoomStart", marker.name, true);
            TiledObject exit = matching(markers, "RoomExit", marker.name, false);
            if (!bounds.contains(start.x, start.y) || (exit != null
                && (exit.width <= 0 || exit.height <= 0 || !bounds.contains(rect(exit)))))
                throw new IllegalArgumentException("Entrance/exit outside room: " + marker.name);
            result.add(new Room(marker.name, bounds, new Vector2(start.x, start.y), exit == null ? null : rect(exit)));
        }
        if (result.isEmpty()) {
            if (!markers.isEmpty()) throw new IllegalArgumentException("Room markers without rooms");
            TiledObject player = map.layer("SpawnPoints").stream().filter(o -> "Player".equals(o.type)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Level needs a Player spawn"));
            result.add(new Room("level", new Rectangle(0, 0, map.widthPx(), map.heightPx()), new Vector2(player.x, player.y), null));
        } else {
            for (TiledObject marker : markers) if (!ids.contains(marker.name))
                throw new IllegalArgumentException("Orphan room marker: " + marker.name);
            for (int i = 0; i < result.size(); i++) {
                if ((i < result.size() - 1) != (result.get(i).exit != null))
                    throw new IllegalArgumentException("Every room except the last needs RoomExit");
            }
        }
        rooms = List.copyOf(result);
    }
    public int owner(TiledObject object) {
        float x = object.x + object.width / 2, y = object.y + object.height / 2;
        for (int i = 0; i < rooms.size(); i++) {
            Rectangle b = rooms.get(i).bounds;
            if (x >= b.x && x < b.x + b.width && y >= b.y && y < b.y + b.height) return i;
        }
        throw new IllegalArgumentException("Object outside rooms: " + object.id);
    }
    private static Rectangle rect(TiledObject o) { return new Rectangle(o.x, o.y, o.width, o.height); }
    private static TiledObject matching(List<TiledObject> markers, String type, String name, boolean required) {
        List<TiledObject> matches = markers.stream().filter(o -> type.equals(o.type) && name.equals(o.name)).toList();
        if (matches.size() > 1 || (required && matches.isEmpty()))
            throw new IllegalArgumentException("Expected one " + type + " for " + name);
        return matches.isEmpty() ? null : matches.get(0);
    }
}
