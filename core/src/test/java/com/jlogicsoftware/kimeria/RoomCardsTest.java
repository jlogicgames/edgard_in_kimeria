package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import com.jlogicsoftware.kimeria.world.RoomLayout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D72 (#351): every room gives the player something new. Checks {@code docs/ROOM_CARDS.md} against the shipped maps in
 * play order ({@link KimeriaGame#levelNames()}, then each map's room order from the production {@link RoomLayout}):
 * every room has a card with the required fields, every element id and Tiled type is in the registry, the room where an
 * element is first met introduces it, nothing is introduced twice, develops and combines name only elements already
 * met, and a non-lesson room introduces at most {@link #MAX_INTRODUCTIONS} elements unless an overload reason cites an
 * issue. The synthetic cases below pin each rule so the gate cannot pass vacuously.
 */
class RoomCardsTest {
    static final int MAX_INTRODUCTIONS = 2;
    static final Set<String> ROLES = Set.of("lesson", "variation", "combination", "challenge", "approach", "reward", "rest", "boss");
    static final List<String> REQUIRED = List.of("Level", "Role", "Purpose", "Mastered route", "First learning", "Retry cost");
    static final List<String> NOVELTY = List.of("Introduces", "Develops", "Combines");
    /** Only these object groups hold gameplay objects; Rooms markers and Hints are structure, not elements. */
    static final Set<String> ELEMENT_GROUPS = Set.of("SpawnPoints", "Collisions");
    private static final Pattern BACKTICK = Pattern.compile("`([^`]+)`");
    private static final Pattern ISSUE = Pattern.compile("#[\\w-]+");

    /** One room in play order with the registry keys of the objects it owns. */
    record Room(String name, Set<String> keys) { }

    /** Registry key of a map object, or null for an untyped collision (plain geometry). See ROOM_CARDS.md. */
    static String key(String group, TiledObject o) {
        if ("Collisions".equals(group)) return o.type.isEmpty() ? null : "Collisions:" + o.type;
        String skin = o.prop("skin", "");
        return switch (o.type) {
            case "Actionable" -> "Actionable:" + o.prop("type", "");
            case "Decor" -> "Decor:" + o.prop("kind", "");
            case "Escalator" -> "Escalator:" + ("false".equals(o.prop("running", "")) ? "lever" : "running");
            case "Collectable" -> "Collectable:" + o.name;
            default -> skin.isEmpty() || "forest".equals(skin) ? o.type : o.type + ":" + skin;
        };
    }

    // ---- parsing ROOM_CARDS.md ----

    static final class Registry {
        final Map<String, String> elementOfKey = new HashMap<>();
        final Set<String> elements = new LinkedHashSet<>();
        final Set<String> notElements = new HashSet<>();
    }

    static Registry registry(String text) {
        Registry r = new Registry();
        String section = section(text, "## Element registry");
        for (String line : section.split("\n")) {
            if (line.startsWith("Not elements")) collecting(r, line);
            else if (!r.notElements.isEmpty() && !line.isBlank()) collecting(r, line);
            else if (line.startsWith("| ") && !line.startsWith("| Element id") && !line.startsWith("| ---")) {
                String[] cells = line.split("\\|");
                String id = cells[1].trim();
                r.elements.add(id);
                Matcher m = BACKTICK.matcher(cells[2]);
                while (m.find()) r.elementOfKey.put(m.group(1), id);
            }
        }
        return r;
    }

    private static void collecting(Registry r, String line) {
        Matcher m = BACKTICK.matcher(line);
        while (m.find()) r.notElements.add(m.group(1));
    }

    static String section(String text, String heading) {
        int start = text.indexOf("\n" + heading + "\n");
        assertTrue(start >= 0, "ROOM_CARDS.md has no section " + heading);
        int end = text.indexOf("\n## ", start + heading.length() + 1);
        return text.substring(start + 1, end < 0 ? text.length() : end);
    }

    /** Cards by room name, in file order; each card is its Field -> Value rows. */
    static Map<String, Map<String, String>> cards(String text) {
        Map<String, Map<String, String>> cards = new LinkedHashMap<>();
        Map<String, String> current = null;
        for (String line : text.split("\n")) {
            if (line.startsWith("### ")) {
                String name = line.substring(4).trim();
                assertNull(cards.get(name), "two cards for " + name);
                current = new LinkedHashMap<>();
                cards.put(name, current);
            } else if (line.startsWith("## ")) {
                current = null;
            } else if (current != null && line.startsWith("| ") && !line.startsWith("| Field") && !line.startsWith("| ---")) {
                int split = line.indexOf('|', 2);
                current.put(line.substring(2, split).trim(), line.substring(split + 1, line.lastIndexOf('|')).trim());
            }
        }
        return cards;
    }

    private static boolean empty(String value) { return value == null || value.isBlank() || "-".equals(value); }

    private static List<String> ids(String field, String value) {
        List<String> ids = new ArrayList<>();
        if (empty(value)) return ids;
        if ("Introduces".equals(field) || "Repeats".equals(field)) {
            for (String id : value.split(",")) ids.add(id.trim());
        } else {
            for (String entry : value.split(";")) {
                String head = entry.contains(":") ? entry.substring(0, entry.indexOf(':')) : entry;
                for (String id : head.split("\\+")) ids.add(id.trim());
            }
        }
        return ids;
    }

    // ---- the rules ----

    static List<String> problems(String text, List<Room> rooms) {
        Registry registry = registry(text);
        Map<String, Map<String, String>> cards = cards(text);
        List<String> problems = new ArrayList<>();
        Set<String> roomNames = new HashSet<>();
        for (Room room : rooms) roomNames.add(room.name);
        for (String name : cards.keySet()) if (!roomNames.contains(name)) problems.add("card " + name + ": no shipped room has this name");

        Map<String, String> firstSeen = new HashMap<>();
        for (Room room : rooms) {
            Map<String, String> card = cards.get(room.name);
            if (card == null) { problems.add(room.name + ": no card in docs/ROOM_CARDS.md"); continue; }
            for (String field : REQUIRED) if (empty(card.get(field))) problems.add(room.name + ": field '" + field + "' is empty");
            String role = card.getOrDefault("Role", "");
            if (!ROLES.contains(role)) problems.add(room.name + ": role '" + role + "' is not one of " + new TreeSet<>(ROLES));
            boolean anyNovelty = false;
            for (String field : NOVELTY) anyNovelty |= !empty(card.get(field));
            if (!anyNovelty) problems.add(room.name + ": nothing new (Introduces, Develops and Combines are all empty), D72");

            // Elements the maps show in this room, mapped through the registry.
            Set<String> shown = new LinkedHashSet<>();
            for (String key : room.keys) {
                if (registry.notElements.contains(key)) continue;
                String element = registry.elementOfKey.get(key);
                if (element == null) problems.add(room.name + ": Tiled key '" + key + "' is not in the element registry");
                else shown.add(element);
            }
            List<String> introduces = ids("Introduces", card.get("Introduces"));
            for (String field : List.of("Introduces", "Develops", "Combines", "Repeats")) {
                for (String id : ids(field, card.get(field))) {
                    if (!registry.elements.contains(id)) { problems.add(room.name + ": " + field + " names unknown element '" + id + "'"); continue; }
                    String seen = firstSeen.get(id);
                    if ("Introduces".equals(field) && seen != null)
                        problems.add(room.name + ": introduces '" + id + "', but it is not new: first met in " + seen);
                    if (!"Introduces".equals(field) && seen == null && !introduces.contains(id))
                        problems.add(room.name + ": " + field.toLowerCase() + " '" + id + "', which the player has not met yet");
                }
            }
            for (String element : shown) {
                if (!firstSeen.containsKey(element) && !introduces.contains(element))
                    problems.add(room.name + ": '" + element + "' is first met here but the card does not introduce it");
            }
            if (!"lesson".equals(role) && introduces.size() > MAX_INTRODUCTIONS) {
                String reason = card.get("Overload reason");
                if (empty(reason) || !ISSUE.matcher(reason).find())
                    problems.add(room.name + ": introduces " + introduces.size() + " elements (cap " + MAX_INTRODUCTIONS
                        + " outside lessons, D72) without an 'Overload reason' that cites an issue");
            }
            for (String id : introduces) firstSeen.putIfAbsent(id, room.name);
            for (String element : shown) firstSeen.putIfAbsent(element, room.name);
        }
        return problems;
    }

    // ---- shipped maps ----

    static Path root() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null && !Files.isRegularFile(p.resolve("docs/ROOM_CARDS.md"))) p = p.getParent();
        assertNotNull(p, "docs/ROOM_CARDS.md not found above the working directory");
        return p;
    }

    static List<Room> shippedRooms() {
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        };
        com.badlogic.gdx.Files previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        try {
            List<Room> rooms = new ArrayList<>();
            for (String level : KimeriaGame.levelNames()) {
                TiledMapData map = TiledMapData.load("tiles/" + level + ".tmx", assets);
                RoomLayout layout = new RoomLayout(map);
                boolean unmarked = map.layer("Rooms").isEmpty();
                List<Set<String>> keys = new ArrayList<>();
                for (int i = 0; i < layout.rooms.size(); i++) keys.add(new TreeSet<>());
                for (String group : ELEMENT_GROUPS) {
                    for (TiledObject o : map.layer(group)) {
                        String key = key(group, o);
                        if (key == null || "Deadline".equals(key)) continue; // a Deadline is level-wide and may lie outside rooms
                        keys.get(layout.owner(o)).add(key);
                    }
                }
                for (int i = 0; i < layout.rooms.size(); i++)
                    rooms.add(new Room(unmarked ? level : layout.rooms.get(i).id(), keys.get(i)));
            }
            return rooms;
        } finally {
            Gdx.files = previous;
        }
    }

    private static String shippedCards() throws IOException { return Files.readString(root().resolve("docs/ROOM_CARDS.md")); }

    @Test void everyShippedRoomHasACardThatGivesTheEnforcedNovelty() throws IOException {
        List<Room> rooms = shippedRooms();
        assertEquals(16, rooms.size(), "forest-0 has six rooms, forest-1 six, forest-2 four: update this count with a new card");
        List<String> problems = problems(shippedCards(), rooms);
        assertTrue(problems.isEmpty(), "docs/ROOM_CARDS.md is out of step with the maps (D72):\n  " + String.join("\n  ", problems));
    }

    @Test void theRegistryCoversTheSkillsAndElementsTheCardsUse() throws IOException {
        Registry registry = registry(shippedCards());
        assertTrue(registry.elements.contains("wall-jump"), "skills without a Tiled key are registered too");
        assertEquals("touch-explosive", registry.elementOfKey.get("Bomb"), "the Bomb is a reskin of the stump");
        assertEquals("proximity-thorn", registry.elementOfKey.get("ThornTuft"), "the tuft is a reskin of the root");
        assertTrue(registry.notElements.contains("Decor:bush"));
    }

    // ---- each rule fails when it should ----

    private static final String REGISTRY = """

        ## Element registry

        | Element id | Tiled keys | What the player does |
        | --- | --- | --- |
        | lever | `Trigger` | levers |
        | thorn | `ThornRoot`, `ThornTuft` | jumps |
        | mob | `RedMob` | fights |
        | puff | `Puffball` | waits |

        Not elements: `Decor:bush`.

        ## Cards
        """;

    private static String card(String name, String role, String introduces, String develops, String extra) {
        return "### " + name + "\n\n| Field | Value |\n| --- | --- |\n| Level | test |\n| Role | " + role + " |\n| Purpose | p |\n"
            + "| Introduces | " + introduces + " |\n| Develops | " + develops + " |\n| Combines | - |\n"
            + "| Mastered route | 1 s |\n| First learning | not measured |\n| Retry cost | 1 s |\n" + extra + "\n";
    }

    private static List<String> check(String cards, Room... rooms) { return problems(REGISTRY + cards, List.of(rooms)); }

    @Test void aValidPairOfRoomsPasses() {
        assertEquals(List.of(), check(card("a", "lesson", "lever", "-", "") + card("b", "variation", "thorn", "lever: now timed", ""),
            new Room("a", Set.of("Trigger", "Decor:bush")), new Room("b", Set.of("Trigger", "ThornRoot"))));
    }

    @Test void aRoomWithNothingNewFails() {
        List<String> p = check(card("a", "lesson", "lever", "-", "") + card("b", "variation", "-", "-", ""),
            new Room("a", Set.of("Trigger")), new Room("b", Set.of("Trigger")));
        assertTrue(p.stream().anyMatch(s -> s.startsWith("b: nothing new")), p.toString());
    }

    @Test void aReskinIsNotAnIntroduction() {
        List<String> p = check(card("a", "lesson", "thorn", "-", "") + card("b", "variation", "thorn", "-", ""),
            new Room("a", Set.of("ThornRoot")), new Room("b", Set.of("ThornTuft")));
        assertTrue(p.contains("b: introduces 'thorn', but it is not new: first met in a"), p.toString());
    }

    @Test void anUnannouncedFirstSightingFails() {
        List<String> p = check(card("a", "lesson", "lever", "-", ""), new Room("a", Set.of("Trigger", "RedMob")));
        assertTrue(p.contains("a: 'mob' is first met here but the card does not introduce it"), p.toString());
    }

    @Test void developingSomethingNotYetMetFails() {
        List<String> p = check(card("a", "lesson", "lever", "mob: angrier", ""), new Room("a", Set.of("Trigger")));
        assertTrue(p.contains("a: develops 'mob', which the player has not met yet"), p.toString());
    }

    @Test void unknownKeysElementsMissingCardsAndOrphanCardsFail() {
        List<String> p = check(card("a", "lesson", "ghost", "-", "") + card("z", "lesson", "lever", "-", ""),
            new Room("a", Set.of("Mystery")), new Room("b", Set.of()));
        assertTrue(p.contains("a: Tiled key 'Mystery' is not in the element registry"), p.toString());
        assertTrue(p.contains("a: Introduces names unknown element 'ghost'"), p.toString());
        assertTrue(p.contains("b: no card in docs/ROOM_CARDS.md"), p.toString());
        assertTrue(p.contains("card z: no shipped room has this name"), p.toString());
    }

    @Test void requiredFieldsAndRolesAreChecked() {
        String broken = card("a", "corridor", "lever", "-", "").replace("| Retry cost | 1 s |", "| Retry cost | - |");
        List<String> p = check(broken, new Room("a", Set.of("Trigger")));
        assertTrue(p.contains("a: field 'Retry cost' is empty"), p.toString());
        assertTrue(p.stream().anyMatch(s -> s.startsWith("a: role 'corridor'")), p.toString());
    }

    @Test void overloadNeedsAReasonThatCitesAnIssueOutsideLessons() {
        Room a = new Room("a", Set.of("Trigger", "ThornRoot", "RedMob"));
        assertTrue(check(card("a", "combination", "lever, thorn, mob", "-", ""), a).stream()
            .anyMatch(s -> s.startsWith("a: introduces 3 elements")), "over the cap without a reason");
        assertTrue(check(card("a", "combination", "lever, thorn, mob", "-", "| Overload reason | because |"), a).stream()
            .anyMatch(s -> s.startsWith("a: introduces 3 elements")), "a reason without an issue is not argued and tracked");
        assertEquals(List.of(), check(card("a", "combination", "lever, thorn, mob", "-", "| Overload reason | split tracked by #999 |"), a));
        assertEquals(List.of(), check(card("a", "lesson", "lever, thorn, mob", "-", ""), a), "lessons follow D33-D40/D48 instead");
    }
}
