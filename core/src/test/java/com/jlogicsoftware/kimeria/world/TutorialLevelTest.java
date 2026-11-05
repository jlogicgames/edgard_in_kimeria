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

    @Test void forestZeroReservesSixRoomsForTheLessonsInOrder() {
        TiledMapData map = map("forest-0");
        RoomLayout layout = new RoomLayout(map);
        List<String> lessons = List.of("lever", "ladder-bridge", "wall", "moving-platform", "thrown-stone", "existing-traps");
        assertEquals(lessons.size(), layout.rooms.size());
        var markers = map.layer("Rooms").stream().filter(o -> "Room".equals(o.type)).toList();
        for (int i = 0; i < lessons.size(); i++) {
            var room = layout.rooms.get(i);
            assertEquals(lessons.get(i), markers.get(i).prop("lesson", ""));
            // The lever room is 576 px, the ladder room 704 px (#209), the wall and platform rooms 640 px (#197, #198) and the stone room 528 px (#199), as long as one lesson needs; the last is still long.
            assertEquals(i == 0 ? 576 : i == 1 ? 704 : i <= 3 ? 640 : i == 4 ? 528 : 1280, room.bounds().width, i <= 4 ? "A short lesson room" : "Rooms are long");
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
        assertEquals(273, map.widthTiles);
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
        assertTrue(object.apply("Lever [53") - start.apply("forest-0-wall-lever") >= 200, "wall-lever message leads the lever");
        assertTrue(object.apply("Wall [52") - start.apply("forest-0-wall-pass") >= 150, "pass message leads the wall");
        assertTrue(object.apply("Lever [58") - start.apply("forest-0-platform-lever") >= 200, "platform-lever message leads the lever");
        assertTrue(object.apply("Escalator [57") - start.apply("forest-0-platform-ride") >= 150, "ride message leads the platform");
        assertTrue(object.apply("Stone") - start.apply("forest-0-stone-throw") >= 200, "stone message leads the stone");
        assertEquals(start.apply("forest-0-stone-throw") + hints.stream().filter(o -> "forest-0-stone-throw".equals(o.name)).findFirst().orElseThrow().width,
            start.apply("forest-0-stone-exit"), "The stone room's message zones butt against each other");
        assertEquals(start.apply("forest-0-stone-exit") + hints.stream().filter(o -> "forest-0-stone-exit".equals(o.name)).findFirst().orElseThrow().width,
            start.apply("forest-0-stone-across"), "and the last one starts at the far lip, where the platform text stops being true");
        assertEquals(object.apply("Lever [65"), start.apply("forest-0-stone-across"), "at the lever");
        assertEquals(start.apply("forest-0-platform-lever") + hints.stream().filter(o -> "forest-0-platform-lever".equals(o.name)).findFirst().orElseThrow().width,
            start.apply("forest-0-platform-ride"), "The platform room's message zones butt against each other");
        assertEquals(start.apply("forest-0-wall-lever") + hints.stream().filter(o -> "forest-0-wall-lever".equals(o.name)).findFirst().orElseThrow().width,
            start.apply("forest-0-wall-pass"), "The wall room's message zones butt against each other");
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
        Set<String> lessonTypes = Set.of("Trigger", "Actionable", "Ladder", "Escalator", "Stone");
        // The wall lesson (#197) adds one Trigger and its Wall Actionable in room 2; the platform lesson (#198) one Trigger and its Escalator in room 3;
        // the stone lesson (#199) a Stone, a throw-activated Trigger and a horizontal Escalator over a chasm in room 4.
        RoomLayout layout = new RoomLayout(map);
        for (var o : map.layer("SpawnPoints")) {
            // The lever lesson (#186) adds one Trigger and its Torch Actionable in room 0; the ladder lesson (#196)
            // one Trigger and its Ladder in room 1.
            boolean lesson = lessonTypes.contains(o.type) && layout.owner(o) <= 4;
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
        var room2 = map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) == 2).toList();
        assertEquals(2, room2.size(), "the wall room holds only its lever and wall");
        assertEquals(1, room2.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        var walls = room2.stream().filter(o -> "Actionable".equals(o.type)).toList();
        assertEquals(1, walls.size(), "exactly one linked object");
        assertEquals("Wall", walls.get(0).prop("type", ""));
        assertEquals("vertical", walls.get(0).prop("movement", ""), "The wall is raised and lowered, never removed");
        assertEquals(320, walls.get(0).y + walls.get(0).height, "The wall starts lowered, standing on the floor");
        assertTrue(walls.get(0).height >= 96, "and is taller than a jump");
        var room3 = map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) == 3).toList();
        assertEquals(2, room3.size(), "the platform room holds only its lever and platform");
        assertEquals(1, room3.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        var platforms = room3.stream().filter(o -> "Escalator".equals(o.type)).toList();
        assertEquals(1, platforms.size(), "exactly one linked moving platform");
        assertTrue(platforms.get(0).propBool("isVertical", false), "The platform rises and falls");
        assertFalse(platforms.get(0).propBool("running", true), "The platform starts stopped");
        assertEquals(304, platforms.get(0).y, "and waits at the bottom, 16 px above the floor");
        assertEquals(208 - 32, platforms.get(0).y - platforms.get(0).propFloat("offNeg", 0) * 32, "and rises 32 px above the ledge top so the hero can step off");
        assertTrue(platforms.get(0).propFloat("offNeg", 0) * 32 > 58, "further than a jump");
        var room4 = map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) == 4).toList();
        assertEquals(3, room4.size(), "the stone room holds only its stone, lever and platform");
        assertEquals(1, room4.stream().filter(o -> "Stone".equals(o.type)).count(), "exactly one stone");
        assertTrue(room4.stream().filter(o -> "Stone".equals(o.type)).findFirst().orElseThrow().propBool("respawn", false), "a lost stone comes home");
        var stoneLevers = room4.stream().filter(o -> "Trigger".equals(o.type)).toList();
        assertEquals(1, stoneLevers.size(), "exactly one lever");
        assertTrue(stoneLevers.get(0).propBool("throwActivated", false), "operated by a thrown stone");
        var stonePlatforms = room4.stream().filter(o -> "Escalator".equals(o.type)).toList();
        assertEquals(1, stonePlatforms.size(), "exactly one linked moving platform, the one already taught");
        assertFalse(stonePlatforms.get(0).propBool("isVertical", true), "it crosses the chasm sideways");
        assertFalse(stonePlatforms.get(0).propBool("running", true), "and starts stopped");
        assertEquals(320, stonePlatforms.get(0).y, "flush with both lips");
        float lipNear = stonePlatforms.get(0).x, lipFar = lipNear + stonePlatforms.get(0).propFloat("offPos", 0) * 32 + stonePlatforms.get(0).width;
        assertEquals(stoneLevers.get(0).x, lipFar, "the lever stands at the far lip, where the platform ends");
        assertEquals(96, stoneLevers.get(0).x - lipNear, "a 96 px chasm");
        assertTrue(map.layer("SpawnPoints").stream().filter(o -> !"Deadline".equals(o.type) && layout.owner(o) > 4)
            .noneMatch(o -> lessonTypes.contains(o.type)), "Other rooms are still empty");
        for (var o : map.layer("Collisions")) layout.owner(o);
        // Each room: exactly one floor strip and the two side walls (lessons add their own geometry).
        for (int i = 0; i < layout.rooms.size(); i++) {
            int room = i;
            long inRoom = map.layer("Collisions").stream().filter(o -> layout.owner(o) == room).count();
            assertEquals(i == 1 || i == 3 ? 4 : i == 4 ? 5 : 3, inRoom, "Room " + i + (i == 1 || i == 3 ? " is a corridor plus its ledge" : i == 4 ? " is two floors around a chasm plus the overhang" : " is an empty corridor"));
            var exit = layout.rooms.get(i).exit();
            if (exit != null) assertEquals(i == 1 || i == 3 ? 208 : 320, exit.y + exit.height, i == 1 || i == 3 ? "Exit stands on the room's ledge" : "Exit stands on the floor line");
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
