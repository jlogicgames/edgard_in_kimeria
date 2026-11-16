package com.jlogicsoftware.kimeria;

import com.jlogicsoftware.kimeria.RoomCardsTest.Registry;
import com.jlogicsoftware.kimeria.RoomCardsTest.Room;
import com.jlogicsoftware.kimeria.world.TrapCatalogTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D75 (#386): the trap catalogue ({@code docs/TRAPS.md}, D71) and the room cards ({@code docs/ROOM_CARDS.md}, D72) name
 * one teaching room per trap type, and they must agree. For each trap row the first backticked word of "Taught in" is
 * the teaching room, and:
 * <ul>
 *   <li>it is a shipped room with a card;</li>
 *   <li>that room shows the trap's element (the type itself or a reskin of it, through the element registry);</li>
 *   <li>no earlier room in play order shows the type itself (the teaching room is its first meeting);</li>
 *   <li>for a DISGUISED or HIDDEN trap, the room's card introduces the trap's element (D71: taught safely, in the room
 *       whose purpose is to teach it).</li>
 * </ul>
 * What it cannot judge: whether the room really teaches (review, and the novice playtests of #135).
 */
class TrapTeachingRoomTest {
    private static final Pattern FIRST_BACKTICK = Pattern.compile("`([^`]+)`");
    private static final int TYPE = 0, VISIBILITY = 2, TAUGHT = TrapCatalogTest.TRAP_COLUMNS.indexOf("Taught in");

    static List<String> problems(String trapsDoc, String cardsDoc, List<Room> rooms) {
        Registry registry = RoomCardsTest.registry(cardsDoc);
        Map<String, Map<String, String>> cards = RoomCardsTest.cards(cardsDoc);
        List<String> out = new ArrayList<>();
        for (List<String> row : TrapCatalogTest.table(trapsDoc, "trap-catalogue").values()) {
            String type = row.get(TYPE).replace("`", "").trim(), visibility = row.get(VISIBILITY);
            Matcher m = FIRST_BACKTICK.matcher(row.get(TAUGHT));
            if (!m.find()) { out.add(type + ": 'Taught in' must start with the teaching room in backticks"); continue; }
            String taught = m.group(1);
            int index = indexOf(rooms, taught);
            if (index < 0 || !cards.containsKey(taught)) {
                out.add(type + ": teaching room '" + taught + "' is not a shipped room with a card in docs/ROOM_CARDS.md");
                continue;
            }
            String element = registry.elementOfKey.get(type);
            if (element == null) { out.add(type + ": not in the element registry of docs/ROOM_CARDS.md"); continue; }
            if (!shows(registry, rooms.get(index), element))
                out.add(type + ": teaching room '" + taught + "' shows no '" + element + "' (the type or a reskin of it)");
            for (int i = 0; i < index; i++)
                if (showsType(rooms.get(i), type))
                    out.add(type + ": first met in '" + rooms.get(i).name() + "', before its teaching room '" + taught + "'");
            if ("DISGUISED".equals(visibility) || "HIDDEN".equals(visibility)) {
                String introduces = cards.get(taught).getOrDefault("Introduces", "");
                if (!List.of(introduces.replace(" ", "").split(",")).contains(element))
                    out.add(type + ": " + visibility + ", so its teaching room '" + taught + "' must introduce '" + element
                        + "' on its card (D71), but it introduces '" + introduces + "'");
            }
        }
        return out;
    }

    private static int indexOf(List<Room> rooms, String name) {
        for (int i = 0; i < rooms.size(); i++) if (rooms.get(i).name().equals(name)) return i;
        return -1;
    }

    private static boolean shows(Registry registry, Room room, String element) {
        for (String key : room.keys()) if (element.equals(registry.elementOfKey.get(key))) return true;
        return false;
    }

    private static boolean showsType(Room room, String type) {
        for (String key : room.keys()) if (key.equals(type) || key.startsWith(type + ":")) return true;
        return false;
    }

    // ---- the real files ----

    @Test void everyTrapsTeachingRoomAgreesWithTheRoomCards() throws IOException {
        String traps = Files.readString(RoomCardsTest.root().resolve("docs/TRAPS.md"));
        String cards = Files.readString(RoomCardsTest.root().resolve("docs/ROOM_CARDS.md"));
        List<String> p = problems(traps, cards, RoomCardsTest.shippedRooms());
        assertTrue(p.isEmpty(), "docs/TRAPS.md 'Taught in' and docs/ROOM_CARDS.md disagree (D71, D72, D75):\n  " + String.join("\n  ", p));
    }

    // ---- each rule fires ----

    private static final String CARDS = """

        ## Element registry

        | Element id | Tiled keys | What the player does |
        | --- | --- | --- |
        | thorn | `ThornRoot`, `ThornTuft` | jumps |
        | lever | `Trigger` | levers |

        ## Cards

        ### a

        | Field | Value |
        | --- | --- |
        | Introduces | lever, thorn |

        ### b

        | Field | Value |
        | --- | --- |
        | Introduces | - |
        """;

    private static String traps(String rows) {
        return "<!-- trap-catalogue:start -->\n| Type | Family | Visibility | At-rest cue | Warning | Taught in | Decision |\n"
            + "| --- | --- | --- | --- | --- | --- | --- |\n" + rows + "<!-- trap-catalogue:end -->\n";
    }

    private static String row(String type, String visibility, String taught) {
        return "| `" + type + "` | proximity | " + visibility + " | pending #1 | 0.8 s | " + taught + " | D44 |\n";
    }

    private static final List<Room> ROOMS = List.of(new Room("a", java.util.Set.of("ThornRoot", "Trigger")),
        new Room("b", java.util.Set.of("ThornTuft")));

    @Test void consistentRowsPass() {
        assertEquals(List.of(), problems(traps(row("ThornRoot", "HIDDEN", "`a`, hint") + row("ThornTuft", "VISIBLE", "`b`")), CARDS, ROOMS));
    }

    @Test void aTeachingRoomWithoutACardOrBackticksFails() {
        assertTrue(problems(traps(row("ThornTuft", "VISIBLE", "`forest-9`")), CARDS, ROOMS).get(0).contains("not a shipped room"));
        assertTrue(problems(traps(row("ThornTuft", "VISIBLE", "the tutorial")), CARDS, ROOMS).get(0).contains("must start with"));
    }

    @Test void aRoomThatDoesNotShowTheElementFails() {
        List<Room> rooms = List.of(new Room("a", java.util.Set.of("Trigger")), new Room("b", java.util.Set.of("Trigger")));
        assertTrue(problems(traps(row("ThornTuft", "VISIBLE", "`b`")), CARDS, rooms).stream().anyMatch(s -> s.contains("shows no 'thorn'")));
    }

    @Test void aTypeMetBeforeItsTeachingRoomFails() {
        List<Room> rooms = List.of(new Room("a", java.util.Set.of("ThornTuft")), new Room("b", java.util.Set.of("ThornTuft")));
        assertTrue(problems(traps(row("ThornTuft", "VISIBLE", "`b`")), CARDS, rooms).stream().anyMatch(s -> s.contains("first met in 'a'")));
    }

    @Test void aHiddenTrapsTeachingRoomMustIntroduceItsElement() {
        List<String> p = problems(traps(row("ThornTuft", "HIDDEN", "`b`")), CARDS, ROOMS);
        assertTrue(p.stream().anyMatch(s -> s.contains("must introduce 'thorn'")), p.toString());
        assertEquals(List.of(), problems(traps(row("ThornTuft", "VISIBLE", "`b`")), CARDS, ROOMS), "a VISIBLE reskin may be met later");
    }
}
