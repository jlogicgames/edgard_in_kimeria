package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Map-level structure of the forest-0 tutorial skeleton and of the authored hint zones. */
class TutorialLevelTest {
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
    };

    private TiledMapData map(String name) {
        Gdx.files = new HeadlessFiles();
        return TiledMapData.load("tiles/" + name + ".tmx", assets);
    }

    @Test void forestZeroReservesSixLongRoomsForTheLessonsInOrder() {
        TiledMapData map = map("forest-0");
        RoomLayout layout = new RoomLayout(map);
        List<String> lessons = List.of("lever", "ladder-bridge", "wall", "moving-platform", "thrown-stone", "existing-traps");
        assertEquals(lessons.size(), layout.rooms.size());
        var markers = map.layer("Rooms").stream().filter(o -> "Room".equals(o.type)).toList();
        for (int i = 0; i < lessons.size(); i++) {
            var room = layout.rooms.get(i);
            assertEquals(lessons.get(i), markers.get(i).prop("lesson", ""));
            // The lever room is 576 px and the ladder room 704 px (#209), as long as one lesson needs; the others are still long.
            assertEquals(i == 0 ? 576 : i == 1 ? 704 : 1280, room.bounds().width, i <= 1 ? "A short lesson room" : "Rooms are long");
            assertEquals(0, room.bounds().width % 16, "Room edges stay on the tile grid");
            assertTrue(room.bounds().contains(room.entrance().x, room.entrance().y));
            assertEquals(i < lessons.size() - 1, room.exit() != null, "Every room but the last has an exit");
            if (i > 0) assertEquals(layout.rooms.get(i - 1).bounds().x + layout.rooms.get(i - 1).bounds().width, room.bounds().x);
        }
    }

    @Test void forestZeroMapWidthMatchesTheRoomsAndEverythingStaysInsideIt() {
        TiledMapData map = map("forest-0");
        RoomLayout layout = new RoomLayout(map);
        var last = layout.rooms.get(layout.rooms.size() - 1).bounds();
        assertEquals(map.widthTiles * 16, last.x + last.width, "The tile layer ends where the last room ends");
        assertEquals(400, map.widthTiles);
        for (var o : map.layer("Hints")) assertTrue(o.x + o.width <= map.widthTiles * 16, o.name);
        for (var o : map.layer("Collisions")) assertTrue(o.x + o.width <= map.widthTiles * 16, "collision at " + o.x);
    }

    /** Hints must be readable BEFORE the hero reaches the object they explain (#209). */
    @Test void forestZeroLessonHintsStartWellBeforeTheirObject() {
        TiledMapData map = map("forest-0");
        var hints = map.layer("Hints");
        var spawn = map.layer("SpawnPoints");
        java.util.function.Function<String, Float> start = name -> hints.stream().filter(o -> name.equals(o.name)).findFirst().orElseThrow().x;
        java.util.function.Function<String, Float> object = prefix -> spawn.stream().filter(o -> o.name.startsWith(prefix)).findFirst().orElseThrow().x;
        assertTrue(object.apply("Lever [41") - start.apply("forest-0-lever-torch") >= 100, "lever message leads the lever");
        assertTrue(object.apply("Ladder") - start.apply("forest-0-ladder-climb") >= 100, "climb message leads the ladder");
        // Message zones of one room butt against each other without a gap, so a lingering text is always replaced by a true one.
        float end = 0;
        for (String name : List.of("forest-0-walk", "forest-0-lever-torch", "forest-0-lever-exit")) {
            var z = hints.stream().filter(o -> name.equals(o.name)).findFirst().orElseThrow();
            if (end > 0) assertEquals(end, z.x, name);
            end = z.x + z.width;
        }
    }

    @Test void forestZeroHasNoEnemiesHazardsOrLooseObstacles() {
        TiledMapData map = map("forest-0");
        Set<String> allowed = Set.of("Player", "Deadline", "Checkpoint");
        Set<String> lessonTypes = Set.of("Trigger", "Actionable", "Ladder");
        RoomLayout layout = new RoomLayout(map);
        for (var o : map.layer("SpawnPoints")) {
            // The lever lesson (#186) adds one Trigger and its Torch Actionable in room 0; the ladder lesson (#196)
            // one Trigger and its Ladder in room 1.
            boolean lesson = lessonTypes.contains(o.type) && layout.owner(o) <= 1;
            assertTrue(allowed.contains(o.type) || lesson, "Unexpected object: " + o.type);
            assertFalse(Set.of("RedMob", "Bomb", "FallingPlatform", "Boss").contains(o.type), "forest-0 has no enemies or hazards");
        }
        var room0 = map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) == 0).toList();
        assertEquals(1, room0.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        var actionables = room0.stream().filter(o -> "Actionable".equals(o.type)).toList();
        assertEquals(1, actionables.size(), "exactly one linked object");
        assertEquals("Torch", actionables.get(0).prop("type", ""));
        assertEquals(0, actionables.get(0).propFloat("Intensity", -1), "The torch starts dark");
        assertTrue(room0.stream().noneMatch(o -> "Ladder".equals(o.type)), "Room 0 has no ladder");
        var room1 = map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) == 1).toList();
        assertEquals(1, room1.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        assertEquals(1, room1.stream().filter(o -> "Ladder".equals(o.type)).count(), "exactly one ladder");
        assertEquals(2, room1.size(), "the ladder room holds only its lever and ladder");
        assertTrue(map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) > 1)
            .noneMatch(o -> lessonTypes.contains(o.type)), "Other rooms are still empty");
        for (var o : map.layer("Collisions")) layout.owner(o);
        // Each room: exactly one floor strip and the two side walls (lessons add their own geometry).
        for (int i = 0; i < layout.rooms.size(); i++) {
            int room = i;
            long inRoom = map.layer("Collisions").stream().filter(o -> layout.owner(o) == room).count();
            assertEquals(i == 1 ? 4 : 3, inRoom, "Room " + i + (i == 1 ? " is a corridor plus its ledge" : " is an empty corridor"));
            var exit = layout.rooms.get(i).exit();
            if (exit != null) assertEquals(i == 1 ? 208 : 320, exit.y + exit.height, i == 1 ? "Exit stands on the ladder room's ledge" : "Exit stands on the floor line");
        }
        assertEquals(1, map.layer("SpawnPoints").stream().filter(o -> "Checkpoint".equals(o.type)).count());
        assertEquals(layout.rooms.size() - 1, layout.owner(map.layer("SpawnPoints").stream()
            .filter(o -> "Checkpoint".equals(o.type)).findFirst().orElseThrow()));
    }

    @Test void onlyForestZeroAndTheForestThreeControlZoneAuthorHints() {
        assertTrue(map("forest-1").layer("Hints").isEmpty(), "forest-1 shows no tutorial hints");
        assertTrue(map("forest-1").properties.get("tutorialRoom") == null);
        TiledMapData three = map("forest-3");
        assertNull(three.properties.get("tutorialRoom"), "The hardcoded room property is gone");
        var zones = three.layer("Hints");
        assertEquals(1, zones.size());
        assertTrue(zones.get(0).propBool("controls", false));
        assertEquals("", zones.get(0).prop("message", ""));
        assertFalse(map("forest-0").layer("Hints").isEmpty());
    }

    @Test void everyAuthoredHintKeyIsALocalisedMessageInAllLanguages() {
        for (String level : List.of("forest-0", "forest-1", "forest-3"))
            for (var zone : map(level).layer("Hints")) {
                String key = zone.prop("message", "");
                if (key.isEmpty()) continue;
                Msg msg = Msg.valueOf(key); // A mistyped key fails the test as it fails the level load.
                for (Language language : Language.values())
                    assertFalse(msg.t(language).isBlank(), level + " " + key + " " + language);
            }
    }
}
