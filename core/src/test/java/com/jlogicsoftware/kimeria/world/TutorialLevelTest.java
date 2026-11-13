package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
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
            // The lever room is 576 px; since D48 each of the next four rooms also holds one trap and its hint lead: ladder 896 px, wall 784, platform 800, stone 736; the traps room stays 720 px (#200).
            assertEquals(i == 0 ? 576 : i == 1 ? 896 : i == 2 ? 784 : i == 3 ? 800 : i == 4 ? 736 : 720, room.bounds().width, "A short lesson room");
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
        assertEquals(282, map.widthTiles);
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
        assertTrue(object.apply("Lever [53") - start.apply("forest-0-wall-lever") >= 160, "wall-lever message leads the lever (160 px = 0.9 s of full text, HintLeadTest; D48 puts the branch before the lever)");
        assertTrue(object.apply("Wall [52") - start.apply("forest-0-wall-pass") >= 150, "pass message leads the wall");
        assertTrue(object.apply("Lever [58") - start.apply("forest-0-platform-lever") >= 160, "platform-lever message leads the lever (160 px = 0.9 s of full text, HintLeadTest; D48 puts the stone before the lever)");
        assertTrue(object.apply("Escalator [57") - start.apply("forest-0-platform-ride") >= 150, "ride message leads the platform");
        assertTrue(object.apply("Stone") - start.apply("forest-0-stone-throw") >= 200, "stone message leads the stone");
        java.util.function.BiConsumer<String, String> butt = (first, second) ->
            assertEquals(start.apply(first) + hints.stream().filter(o -> first.equals(o.name)).findFirst().orElseThrow().width,
                start.apply(second), first + " and " + second + " butt against each other");
        butt.accept("forest-0-stone-throw", "forest-0-stone-stump");
        butt.accept("forest-0-stone-stump", "forest-0-stone-exit");
        butt.accept("forest-0-ladder-root", "forest-0-ladder-lever");
        butt.accept("forest-0-ladder-lever", "forest-0-ladder-climb");
        butt.accept("forest-0-ladder-climb", "forest-0-ladder-exit");
        butt.accept("forest-0-wall-branch", "forest-0-wall-lever");
        butt.accept("forest-0-wall-lever", "forest-0-wall-pass");
        butt.accept("forest-0-platform-stone", "forest-0-platform-lever");
        butt.accept("forest-0-platform-lever", "forest-0-platform-ride");
        // D48 trap hints: the message zone starts at least 160 px (about 0.9 s of full text for a walker, HintLeadTest) before the trap's trigger.
        java.util.function.Function<String, TiledObject> trap = type -> spawn.stream().filter(o -> type.equals(o.type)).findFirst().orElseThrow();
        // D48: each trap comes first in its room; its hint covers the whole crossing and the next text starts beyond the trap's column.
        java.util.function.Function<String, Float> endOf = name -> start.apply(name) + hints.stream().filter(o -> name.equals(o.name)).findFirst().orElseThrow().width;
        assertTrue(trap.apply("ThornRoot").x - 64 - start.apply("forest-0-ladder-root") >= 160, "root message leads the root's zone");
        assertTrue(endOf.apply("forest-0-ladder-root") >= trap.apply("ThornRoot").x + 16, "root message lasts past the root");
        assertTrue(endOf.apply("forest-0-wall-branch") >= trap.apply("FallingBranch").x + 32, "branch message lasts past the branch column");
        assertTrue(endOf.apply("forest-0-platform-stone") >= trap.apply("FallingStone").x + 16, "stone message lasts past the stone column");
        assertTrue(endOf.apply("forest-0-stone-stump") >= object.apply("Stone Room Stump") + 16, "stump message lasts past the stump");
        assertTrue(trap.apply("FallingBranch").propFloat("zoneX", 0) - start.apply("forest-0-wall-branch") >= 160, "branch message leads the branch's zone");
        assertTrue(trap.apply("FallingStone").propFloat("zoneX", 0) - start.apply("forest-0-platform-stone") >= 160, "stone-fall message leads the stone's zone");
        assertTrue(object.apply("Stone Room Stump") - start.apply("forest-0-stone-stump") >= 160, "stump message leads the stone-room stump");
        butt.accept("forest-0-stone-exit", "forest-0-stone-across"); // the last one starts at the far lip, where the platform text stops being true
        assertEquals(object.apply("Lever [65"), start.apply("forest-0-stone-across"), "at the lever");
        assertTrue(object.apply("FallingPlatform") - start.apply("forest-0-traps-platform") >= 200, "platform-trap message leads the pit");
        assertTrue(object.apply("Exploding Stump") - start.apply("forest-0-traps-stump") >= 190, "stump message leads the stump");
        assertEquals(start.apply("forest-0-traps-platform") + hints.stream().filter(o -> "forest-0-traps-platform".equals(o.name)).findFirst().orElseThrow().width,
            start.apply("forest-0-traps-stump"), "The traps room's message zones butt against each other");
        // Message zones of one room butt against each other without a gap, so a lingering text is always replaced by a true one.
        float end = 0;
        for (String name : List.of("forest-0-walk", "forest-0-lever-torch", "forest-0-lever-exit")) {
            var z = hints.stream().filter(o -> name.equals(o.name)).findFirst().orElseThrow();
            if (end > 0) assertEquals(end, z.x, name);
            end = z.x + z.width;
        }
    }

    /**
     * Not gameplay objects, left out of the per-room object counts: the Deadline, the Decor scenery and vines (#239: no collision, no
     * hazard, no enemy) and the harmless HoneyFungus (D63: a trigger zone and a golden particle burst, no hitbox, nothing reads it;
     * it is counted separately below). The lethal FalseHoneyFungus is a hazard and is not listed.
     */
    private static final Set<String> COSMETIC = Set.of("Deadline", "Decor", "HangingVine", "HoneyFungus");

    @Test void forestZeroHasNoEnemiesHazardsOrLooseObstacles() {
        TiledMapData map = map("forest-0");
        // D63 (#295): the harmless honey fungus is scenery that bursts into golden spores; the lethal FalseHoneyFungus must not appear here.
        Set<String> allowed = Set.of("Player", "Deadline", "Checkpoint", "Decor", "HangingVine", "HoneyFungus");
        // D48: one trap in each of rooms 1-4: thorn root, falling branch, falling stone, exploding stump.
        java.util.Map<Integer, String> newTraps = java.util.Map.of(1, "ThornRoot", 2, "FallingBranch", 3, "FallingStone", 4, "ExplosiveStump");
        Set<String> lessonTypes = Set.of("Trigger", "Actionable", "Ladder", "Escalator", "Stone");
        // The wall lesson (#197) adds one Trigger and its Wall Actionable in room 2; the platform lesson (#198) one Trigger and its Escalator in room 3;
        // the stone lesson (#199) a Stone, a throw-activated Trigger and a horizontal Escalator over a chasm in room 4.
        RoomLayout layout = new RoomLayout(map);
        for (var o : map.layer("SpawnPoints")) {
            // The lever lesson (#186) adds one Trigger and its Torch Actionable in room 0; the ladder lesson (#196)
            // one Trigger and its Ladder in room 1.
            boolean lesson = lessonTypes.contains(o.type) && layout.owner(o) <= 4;
            // The traps lesson (#200) adds exactly one falling platform and one exploding stump (D47, replacing the bomb) to the last room, no monster anywhere.
            boolean trap = (Set.of("ExplosiveStump", "FallingPlatform").contains(o.type) && layout.owner(o) == 5)
                || (newTraps.containsValue(o.type) && o.type.equals(newTraps.get(layout.owner(o))));
            assertTrue(allowed.contains(o.type) || lesson || trap, "Unexpected object: " + o.type);
            assertFalse(Set.of("RedMob", "Boss").contains(o.type), "forest-0 has no monsters");
        }
        var fungi = map.layer("SpawnPoints").stream().filter(o -> "HoneyFungus".equals(o.type)).map(layout::owner).toList();
        assertEquals(java.util.List.of(0, 2), fungi, "one harmless honey fungus in the lever room and one in the wall room, none elsewhere (D63)");
        var room0 = map.layer("SpawnPoints").stream().filter(o -> !COSMETIC.contains(o.type) && layout.owner(o) == 0).toList();
        assertEquals(1, room0.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        var actionables = room0.stream().filter(o -> "Actionable".equals(o.type)).toList();
        assertEquals(1, actionables.size(), "exactly one linked object");
        assertEquals("Torch", actionables.get(0).prop("type", ""));
        assertEquals(0, actionables.get(0).propFloat("Intensity", -1), "The torch starts dark");
        assertTrue(room0.stream().noneMatch(o -> "Ladder".equals(o.type)), "Room 0 has no ladder");
        var room1 = map.layer("SpawnPoints").stream().filter(o -> !COSMETIC.contains(o.type) && layout.owner(o) == 1).toList();
        assertEquals(1, room1.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        assertEquals(1, room1.stream().filter(o -> "Ladder".equals(o.type)).count(), "exactly one ladder");
        assertEquals(3, room1.size(), "the ladder room holds only its lever, ladder and thorn root");
        assertEquals(1, room1.stream().filter(o -> "ThornRoot".equals(o.type)).count(), "exactly one thorn root");
        var room2 = map.layer("SpawnPoints").stream().filter(o -> !COSMETIC.contains(o.type) && layout.owner(o) == 2).toList();
        assertEquals(3, room2.size(), "the wall room holds only its lever, wall and falling branch");
        assertEquals(1, room2.stream().filter(o -> "FallingBranch".equals(o.type)).count(), "exactly one falling branch");
        assertEquals(1, room2.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        var walls = room2.stream().filter(o -> "Actionable".equals(o.type)).toList();
        assertEquals(1, walls.size(), "exactly one linked object");
        assertEquals("Wall", walls.get(0).prop("type", ""));
        assertEquals("vertical", walls.get(0).prop("movement", ""), "The wall is raised and lowered, never removed");
        assertEquals(320, walls.get(0).y + walls.get(0).height, "The wall starts lowered, standing on the floor");
        assertTrue(walls.get(0).height >= 96, "and is taller than a jump");
        var room3 = map.layer("SpawnPoints").stream().filter(o -> !COSMETIC.contains(o.type) && layout.owner(o) == 3).toList();
        assertEquals(3, room3.size(), "the platform room holds only its lever, platform and falling stone");
        assertEquals(1, room3.stream().filter(o -> "FallingStone".equals(o.type)).count(), "exactly one falling stone");
        assertEquals(1, room3.stream().filter(o -> "Trigger".equals(o.type)).count(), "exactly one lever");
        var platforms = room3.stream().filter(o -> "Escalator".equals(o.type)).toList();
        assertEquals(1, platforms.size(), "exactly one linked moving platform");
        assertTrue(platforms.get(0).propBool("isVertical", false), "The platform rises and falls");
        assertFalse(platforms.get(0).propBool("running", true), "The platform starts stopped");
        assertEquals(304, platforms.get(0).y, "and waits at the bottom, 16 px above the floor");
        assertEquals(208 - 32, platforms.get(0).y - platforms.get(0).propFloat("offNeg", 0) * 32, "and rises 32 px above the ledge top so the hero can step off");
        assertTrue(platforms.get(0).propFloat("offNeg", 0) * 32 > 58, "further than a jump");
        var room4 = map.layer("SpawnPoints").stream().filter(o -> !COSMETIC.contains(o.type) && layout.owner(o) == 4).toList();
        assertEquals(4, room4.size(), "the stone room holds only its stone, lever, platform and exploding stump");
        assertEquals(1, room4.stream().filter(o -> "ExplosiveStump".equals(o.type)).count(), "exactly one exploding stump");
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
        var room5 = map.layer("SpawnPoints").stream().filter(o -> !COSMETIC.contains(o.type) && layout.owner(o) == 5).toList();
        assertEquals(3, room5.size(), "the traps room holds only its falling platform, exploding stump and the level-end checkpoint");
        assertEquals(1, room5.stream().filter(o -> "FallingPlatform".equals(o.type)).count(), "exactly one falling platform");
        assertEquals(1, room5.stream().filter(o -> "ExplosiveStump".equals(o.type)).count(), "exactly one exploding stump");
        assertTrue(room5.stream().noneMatch(o -> lessonTypes.contains(o.type)), "no lever, no mechanism in the traps room");
        for (var o : map.layer("Collisions")) layout.owner(o);
        // Each room: exactly one floor strip and the two side walls (lessons add their own geometry).
        for (int i = 0; i < layout.rooms.size(); i++) {
            int room = i;
            long inRoom = map.layer("Collisions").stream().filter(o -> layout.owner(o) == room).count();
            assertEquals(i == 1 || i == 3 ? 4 : i == 4 ? 5 : i == 5 ? 4 : 3, inRoom, "Room " + i + (i == 1 || i == 3 ? " is a corridor plus its ledge" : i == 4 ? " is two floors around a chasm plus the overhang" : i == 5 ? " is two floors around a pit" : " is an empty corridor"));
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
        TiledMapData three = map("forest-2");
        assertNull(three.properties.get("tutorialRoom"), "The hardcoded room property is gone");
        var zones = three.layer("Hints");
        assertEquals(1, zones.size());
        assertTrue(zones.get(0).propBool("controls", false));
        assertEquals("", zones.get(0).prop("message", ""));
        assertFalse(map("forest-0").layer("Hints").isEmpty());
    }

    @Test void everyAuthoredHintKeyIsALocalisedMessageInAllLanguages() {
        for (String level : List.of("forest-0", "forest-1", "forest-2"))
            for (var zone : map(level).layer("Hints")) {
                String key = zone.prop("message", "");
                if (key.isEmpty()) continue;
                Msg msg = Msg.valueOf(key); // A mistyped key fails the test as it fails the level load.
                for (Language language : Language.values())
                    assertFalse(msg.t(language).isBlank(), level + " " + key + " " + language);
            }
    }
}
