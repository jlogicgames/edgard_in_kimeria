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
 *
 * <p>D105 (#499): every room outside the exempt roles is dense. Its card lists the required route's {@code Beats}
 * ({@code id@x} in rising x, each backed by its own map object, by map id, or for a skill by the geometry that poses it;
 * two of one element within {@link #ONE_BEAT_PX} are one beat) and the {@code Beat time} (the first time
 * {@code Mastered route} states, pinned as a float literal in the named route test); it needs at least
 * {@link #MIN_BEATS} beats of {@link #MIN_DISTINCT} distinct elements and {@link #MIN_SECONDS_PER_BEAT} to
 * {@link #MAX_SECONDS_PER_BEAT} s per beat. Every element a room introduces (pickups and harmless ones aside) has a
 * {@code First meeting} entry: among the first {@link #EARLY_BEATS} beats or a death at it replays at most
 * {@link #CHEAP_RETRY_SECONDS} s, no other lethal object within {@link #CLEARANCE_PX}, and a named test that holds the
 * room's name ({@code FirstMeetingPocketTest}). The exempt roles are checked against the room. No shipped room is exempt: the
 * lists of rooms shipped below the rule before D105 are gone since #500 reworked the last of them, so a room cannot be added back.
 * (The gate keeps the mechanism for a tracked, temporary exemption as parameters of {@code densityProblems}, pinned by synthetic cases.)
 */
class RoomCardsTest {
    static final int MAX_INTRODUCTIONS = 2;
    // ---- D105 (#499): density ----
    static final int MIN_BEATS = 3, MIN_DISTINCT = 2, EARLY_BEATS = 2;
    static final double MAX_SECONDS_PER_BEAT = 4.0, MIN_SECONDS_PER_BEAT = 1.5, CHEAP_RETRY_SECONDS = 6.0;
    /** How far a beat's x may lie outside its object's span. */
    static final float BEAT_X_SLACK = 16;
    /**
     * Two instances of one element whose spans (patrol included) lie this close are one beat, answered in one go; the
     * same px as the pocket's clearance: about 1 s of walking at the hero's 100 px/s (D105).
     */
    static final float ONE_BEAT_PX = 96, CLEARANCE_PX = 96;
    static final int TILE = 16;
    /** Beat kinds that count as a beat; {@code pickup} and {@code harmless} elements never do. */
    static final Set<String> BEAT_KINDS = Set.of("hazard", "monster", "mechanism", "platform", "skill");
    static final Set<String> LETHAL_KINDS = Set.of("hazard", "monster");
    static final Set<String> KINDS = Set.of("hazard", "monster", "mechanism", "platform", "skill", "pickup", "harmless");
    /** The tutorial's strict form (D33-D40, D48) and the boss fight (D19: measured apart) are outside the band. */
    static final Set<String> DENSITY_EXEMPT = Set.of("lesson", "boss");
    /** The calm beat after danger (#352): exempt from the minimum, nothing lethal, at most one per level. */
    static final Set<String> CALM_ROLES = Set.of("rest", "reward");
    /** The tutorial level: every room in it may be a lesson. */
    static final String TUTORIAL_LEVEL = "forest-0";
    /**
     * Lessons outside the tutorial, each on its merits: {@code forest-1-lever} teaches one lever driving a ladder and a lift
     * in opposite states before the route room gates progress with it (D84, D86), with no hazard or monster in the room.
     */
    static final Set<String> LESSON_ROOMS = Set.of("forest-1-lever");
    /** {@link #LESSON_ROOMS} only shrinks: a new lesson outside the tutorial is argued in a decision, not added here quietly. */
    static final int LESSON_ROOMS_AT_D105 = 1;
    // No GRANDFATHERED sets: #500 reworked every room shipped below D105 (batches 1 to 4), so the lists were deleted rather than kept empty.
    private static final Pattern BEAT = Pattern.compile("([\\w-]+)@(\\d+(?:\\.\\d+)?)");
    /** A time in seconds as a whole token: "9.30 s", not the "9.3" inside "19.30 s". */
    private static final Pattern SECONDS = Pattern.compile("(?<![\\w.])(\\d+(?:\\.\\d+)?) s\\b");
    private static final Pattern BEAT_TIME = Pattern.compile("^(\\d+(?:\\.\\d+)?) \\(`(\\w+)`\\)$");
    private static final Pattern POCKET = Pattern.compile("pocket at x (\\d+)");
    private static final Pattern MEETING = Pattern.compile("^([\\w-]+):\\s*beat (\\d+)(?:,\\s*retry (\\d+(?:\\.\\d+)?) s)?");
    static final Set<String> ROLES = Set.of("lesson", "variation", "combination", "challenge", "approach", "reward", "rest", "boss");
    static final List<String> REQUIRED = List.of("Level", "Role", "Purpose", "Mastered route", "First learning", "Retry cost");
    static final List<String> NOVELTY = List.of("Introduces", "Develops", "Combines");
    /** Only these object groups hold gameplay objects; Rooms markers and Hints are structure, not elements. */
    static final Set<String> ELEMENT_GROUPS = Set.of("SpawnPoints", "Collisions");
    private static final Pattern BACKTICK = Pattern.compile("`([^`]+)`");
    private static final Pattern ISSUE = Pattern.compile("#\\d+\\b");

    /**
     * A map object a beat may name: its registry key and its horizontal span in the map. {@code carriesNoDeath} (D107) is set for a
     * crumble support that lies over a floor: it drops the hero alive onto it (D69), so it is no lethal neighbour.
     */
    record Placed(int id, String key, float left, float right, boolean carriesNoDeath) {
        Placed(int id, String key, float left, float right) { this(id, key, left, right, false); }
    }

    /** One room in play order with the registry keys of the objects it owns, and the objects themselves (D105). */
    record Room(String name, Set<String> keys, List<Placed> placed) {
        Room(String name, Set<String> keys) { this(name, keys, List.of()); }
    }

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
        /** D105: the registry's last column, what kind of beat an element is. */
        final Map<String, String> kindOf = new HashMap<>();
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
                if (cells.length > 4) r.kindOf.put(id, cells[4].trim());
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

    // ---- D105: density and first meetings ----

    private static boolean citesIssue(String value) { return !empty(value) && ISSUE.matcher(value).find(); }

    private static String levelOf(Map<String, String> card) {
        String level = card.getOrDefault("Level", "");
        int comma = level.indexOf(',');
        return (comma < 0 ? level : level.substring(0, comma)).trim();
    }

    /** The horizontal gap between two spans, 0 when they overlap. */
    private static float gap(Placed a, Placed b) { return Math.max(0, Math.max(a.left, b.left) - Math.min(a.right, b.right)); }

    private static String kindOf(Registry registry, String key) {
        String element = registry.elementOfKey.get(key);
        return element == null ? null : registry.kindOf.get(element);
    }

    /** The body of method {@code name} in {@code source} (from its declaration to its matching brace), or null. */
    static String methodBody(String source, String name) {
        Matcher m = Pattern.compile("\\bvoid " + Pattern.quote(name) + "\\s*\\(").matcher(source == null ? "" : source);
        if (!m.find()) return null;
        int open = source.indexOf('{', m.end());
        if (open < 0) return null;
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(m.start(), i + 1);
        }
        return null;
    }

    /**
     * Whether {@code source} asserts the float literal {@code seconds + "f"}: inside an {@code assertEquals(} as its expected
     * value, directly or through the constant it initialises.
     */
    static boolean assertsPinned(String source, String seconds) {
        if (source == null) return false;
        String literal = Pattern.quote(seconds) + "f\\b";
        if (Pattern.compile("assertEquals\\(\\s*" + literal).matcher(source).find()) return true;
        Matcher constant = Pattern.compile("\\b([A-Z][A-Z0-9_]*)\\s*=\\s*" + literal).matcher(source);
        while (constant.find())
            if (Pattern.compile("assertEquals\\(\\s*" + constant.group(1) + "\\s*,").matcher(source).find()) return true;
        return false;
    }

    /** D107: every placed object of {@code key} in the room is a crumble support over a floor (and there is at least one). */
    private static boolean allCarryNoDeath(Room room, String key) {
        boolean any = false;
        for (Placed p : room.placed) if (p.key.equals(key)) { any = true; if (!p.carriesNoDeath) return false; }
        return any;
    }

    /**
     * D107: whether {@code other} counts as a lethal neighbour of the first-met beat {@code first}. Not when it is a crumble support
     * over a floor, and not when it is part of the same skill geometry as {@code first} (one object listed under both keys: a rotten
     * branch is a {@code skin=branch} object like the sound ones the climb is made of).
     */
    private static boolean lethalNeighbour(Registry registry, Room room, Placed first, Placed other) {
        if (other.id == first.id || !isLethal(kindOf(registry, other.key)) || other.carriesNoDeath) return false;
        for (Placed p : room.placed) if (p.id == other.id && p != other && p.key.equals(first.key)) return false;
        return true;
    }

    /** Null-safe: an immutable set's {@code contains(null)} throws, and a key may map to no element or no kind. */
    private static boolean isLethal(String kind) { return kind != null && LETHAL_KINDS.contains(kind); }

    /** The first number of seconds a field states ("9.30 s"), as written, or null. */
    private static String firstSeconds(String value) {
        Matcher m = SECONDS.matcher(value == null ? "" : value);
        return m.find() ? m.group(1) : null;
    }

    /**
     * D105's rules for every room with a card. {@code sourceOf} gives a test class's source by its simple name, or null
     * when there is none (the real tree for the shipped cards, a fixed map for the synthetic ones).
     */
    static List<String> densityProblems(String text, List<Room> rooms, java.util.function.Function<String, String> sourceOf) {
        return densityProblems(text, rooms, sourceOf, Set.of());
    }

    /** As above with the rooms allowed below the band given (no shipped room is, since #500, so its rule is tested on synthetic ones). */
    static List<String> densityProblems(String text, List<Room> rooms, java.util.function.Function<String, String> sourceOf, Set<String> grandfathered) {
        return densityProblems(text, rooms, sourceOf, grandfathered, Set.of());
    }

    /** As above with the rooms excused a late or crowded first meeting given too (the shipped list shrinks with #500, so its rule is tested on a synthetic one). */
    static List<String> densityProblems(String text, List<Room> rooms, java.util.function.Function<String, String> sourceOf, Set<String> grandfathered,
                                        Set<String> firstMeetingGrandfathered) {
        Registry registry = registry(text);
        Map<String, Map<String, String>> cards = cards(text);
        List<String> problems = new ArrayList<>();
        for (String id : registry.elements)
            if (!KINDS.contains(registry.kindOf.getOrDefault(id, "")))
                problems.add("registry: element '" + id + "' has no Beat kind (one of " + new TreeSet<>(KINDS) + "), D105");
        Map<String, Integer> calmPerLevel = new HashMap<>();
        for (Room room : rooms) {
            Map<String, String> card = cards.get(room.name);
            if (card == null) continue; // reported by problems()
            String role = card.getOrDefault("Role", ""), name = room.name, level = levelOf(card);
            boolean lethal = false, boss = false;
            for (String key : room.keys) {
                lethal |= isLethal(kindOf(registry, key)) && !allCarryNoDeath(room, key);
                boss |= "boss".equals(registry.elementOfKey.get(key));
            }
            // The exempt roles, each on its merits.
            if ("lesson".equals(role)) {
                if (!TUTORIAL_LEVEL.equals(level) && !LESSON_ROOMS.contains(name))
                    problems.add(name + ": role lesson outside " + TUTORIAL_LEVEL + " and not in LESSON_ROOMS, D105");
                if (!TUTORIAL_LEVEL.equals(level) && lethal)
                    problems.add(name + ": a lesson outside " + TUTORIAL_LEVEL + " holds a lethal element, D105");
            }
            if ("boss".equals(role) && !boss) problems.add(name + ": role boss, but the room holds no boss, D105");
            if (DENSITY_EXEMPT.contains(role)) continue;
            boolean calm = CALM_ROLES.contains(role);
            if (calm && lethal) problems.add(name + ": role " + role + ", but the room holds a lethal element, D105");
            if (calm && calmPerLevel.merge(level, 1, Integer::sum) > 1)
                problems.add(name + ": a second calm room (rest or reward) in " + level + ", D105");

            // Beats: id@x in rising x, each a beat-kind element backed by its own map object (by object id).
            List<String> beats = new ArrayList<>();
            List<Placed> backing = new ArrayList<>();
            String beatsValue = card.get("Beats");
            if (empty(beatsValue)) {
                if (!calm) problems.add(name + ": field 'Beats' is empty (the required route's beats, id@x), D105");
            } else {
                Set<Integer> used = new HashSet<>();
                float lastX = -Float.MAX_VALUE;
                for (String entry : beatsValue.split(",")) {
                    Matcher m = BEAT.matcher(entry.trim());
                    if (!m.matches()) { problems.add(name + ": beat '" + entry.trim() + "' is not id@x, D105"); continue; }
                    String id = m.group(1);
                    float x = Float.parseFloat(m.group(2));
                    if (x < lastX) problems.add(name + ": beat '" + entry.trim() + "' is out of route order (Beats run in rising map x), D105");
                    lastX = x;
                    if (!registry.elements.contains(id)) { problems.add(name + ": beat names unknown element '" + id + "'"); continue; }
                    String kind = registry.kindOf.get(id);
                    if (kind == null) { problems.add(name + ": beat '" + id + "' has no Beat kind in the registry, D105"); continue; }
                    if (!BEAT_KINDS.contains(kind)) { problems.add(name + ": beat '" + id + "' is a " + kind + ", which never counts as a beat, D105"); continue; }
                    Placed found = null;
                    for (Placed p : room.placed)
                        if (!used.contains(p.id) && id.equals(registry.elementOfKey.get(p.key))
                            && x >= p.left - BEAT_X_SLACK && x <= p.right + BEAT_X_SLACK) { found = p; break; }
                    if (found == null) { problems.add(name + ": beat '" + id + "@" + m.group(2) + "' has no " + id + " object of its own at that x, D105"); continue; }
                    for (int i = 0; i < beats.size(); i++)
                        if (beats.get(i).equals(id) && gap(backing.get(i), found) <= ONE_BEAT_PX)
                            problems.add(name + ": the beats '" + id + "' at x " + (int) backing.get(i).left + " and x " + (int) found.left
                                + " lie within " + (int) ONE_BEAT_PX + " px: one beat, list it once (D105)");
                    used.add(found.id);
                    beats.add(id);
                    backing.add(found);
                }
            }

            // Beat time: the zero-charge required route, the first time 'Mastered route' states, pinned by a named test.
            double seconds = -1;
            String timeValue = card.get("Beat time");
            if (!beats.isEmpty() || !empty(timeValue)) {
                Matcher t = BEAT_TIME.matcher(timeValue == null ? "" : timeValue.trim());
                if (!t.matches()) problems.add(name + ": field 'Beat time' must read 'seconds (`TestClass`)', D105");
                else {
                    String required = firstSeconds(card.get("Mastered route"));
                    String source = sourceOf.apply(t.group(2));
                    if (!t.group(1).equals(required))
                        problems.add(name + ": 'Beat time' " + t.group(1) + " s is not the first time of 'Mastered route' (" + required
                            + " s), which states the zero-charge required route first, D105");
                    else if (!assertsPinned(source, t.group(1)))
                        problems.add(name + ": 'Beat time' " + t.group(1) + " s is not asserted as " + t.group(1) + "f (assertEquals) in " + t.group(2) + ", D105");
                    else seconds = Double.parseDouble(t.group(1));
                }
            }

            // The band.
            String below = card.get("Below density");
            if (!calm && !beats.isEmpty() && seconds > 0) {
                int n = beats.size(), distinct = new HashSet<>(beats).size();
                double pace = seconds / n;
                List<String> thin = new ArrayList<>();
                if (n < MIN_BEATS) thin.add(n + " beats (at least " + MIN_BEATS + ")");
                if (distinct < MIN_DISTINCT) thin.add(distinct + " distinct element (at least " + MIN_DISTINCT + ")");
                if (pace > MAX_SECONDS_PER_BEAT) thin.add(String.format(java.util.Locale.ROOT, "%.2f s per beat (at most %.1f)", pace, MAX_SECONDS_PER_BEAT));
                if (!thin.isEmpty()) {
                    if (!grandfathered.contains(name) || !citesIssue(below))
                        problems.add(name + ": below the density of D105: " + String.join(", ", thin)
                            + (grandfathered.contains(name) ? "; its 'Below density' must cite the rework issue (#NNN)" : ""));
                } else if (!empty(below)) {
                    problems.add(name + ": passes the density of D105 but still carries 'Below density': remove it and the room from the exemption set");
                }
                if (pace < MIN_SECONDS_PER_BEAT && !citesIssue(card.get("Overload reason")))
                    problems.add(String.format(java.util.Locale.ROOT, "%s: %.2f s per beat, denser than %.1f s, without an 'Overload reason' that cites an issue, D105",
                        name, pace, MIN_SECONDS_PER_BEAT));
            }
            if (!empty(below) && !grandfathered.contains(name))
                problems.add(name + ": 'Below density' is only for the rooms shipped below D105 (the exemption set given to the gate, empty for the shipped rooms), not for a new room");

            // First meetings: early or cheap, clear of other lethal objects, and pinned by a test that names the room.
            Map<String, String> meetings = new HashMap<>();
            String meetingValue = card.get("First meeting");
            if (!empty(meetingValue)) for (String entry : meetingValue.split(";")) {
                String e = entry.trim();
                int colon = e.indexOf(':');
                if (colon > 0) meetings.put(e.substring(0, colon).trim(), e);
            }
            boolean excusedAny = false;
            for (String id : calm ? List.<String>of() : ids("Introduces", card.get("Introduces"))) {
                String kind = registry.kindOf.get(id);
                if (kind == null || !BEAT_KINDS.contains(kind)) continue; // a pickup or a harmless element needs no pocket
                String entry = meetings.get(id);
                if (entry == null) { problems.add(name + ": introduces '" + id + "' without a 'First meeting' entry, D105"); continue; }
                Matcher m = MEETING.matcher(entry);
                if (!m.find()) { problems.add(name + ": first meeting of '" + id + "' must start 'id: beat k' (optionally ', retry R s'), D105"); continue; }
                int k = Integer.parseInt(m.group(2)), actual = beats.indexOf(id) + 1;
                if (actual == 0) { problems.add(name + ": introduces '" + id + "' but it is not among the Beats, D105"); continue; }
                if (k != actual) problems.add(name + ": first meeting of '" + id + "' says beat " + k + ", but it is beat " + actual + " of the Beats");
                List<String> faults = new ArrayList<>();
                boolean cheap = m.group(3) != null && Double.parseDouble(m.group(3)) <= CHEAP_RETRY_SECONDS;
                if (actual > EARLY_BEATS && !cheap)
                    faults.add("first met as beat " + actual + " with no retry of at most " + CHEAP_RETRY_SECONDS + " s");
                Placed first = backing.get(actual - 1);
                for (Placed other : room.placed)
                    if (lethalNeighbour(registry, room, first, other) && gap(first, other) < CLEARANCE_PX)
                        faults.add("'" + other.key + "' at x " + (int) other.left + " lies " + (int) gap(first, other) + " px from it (at least "
                            + (int) CLEARANCE_PX + ")");
                Matcher test = BACKTICK.matcher(entry), pocket = POCKET.matcher(entry);
                String[] named = test.find() ? test.group(1).split("\\.") : new String[0];
                String method = named.length == 2 ? methodBody(sourceOf.apply(named[0]), named[1]) : null;
                if (!pocket.find()) faults.add("no 'pocket at x N'");
                if (method == null) faults.add("no named test method (`Class.method`) that exists");
                else if (!method.contains("\"" + name + "\"") || !method.contains("\"" + id + "\"")
                    || !Pattern.compile("[(,]\\s*" + (pocket.find(0) ? pocket.group(1) : "-") + "\\b").matcher(method).find())
                    faults.add("its test method does not name the room, the element and the pocket x");
                if (faults.isEmpty()) continue;
                if (firstMeetingGrandfathered.contains(name) && citesIssue(entry.substring(m.end()))) { excusedAny = true; continue; }
                problems.add(name + ": first meeting of '" + id + "': " + String.join("; ", faults) + " (D105)"
                    + (firstMeetingGrandfathered.contains(name) ? "; the entry must cite the rework issue (#NNN)" : ""));
            }
            if (firstMeetingGrandfathered.contains(name) && !excusedAny)
                problems.add(name + ": its first meetings pass D105: remove the room from the first-meeting exemption set");
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

    /**
     * An object's horizontal span: its box, or a polyline's points (a spider's route, D103), widened by a horizontal patrol
     * ({@code offNeg}/{@code offPos} tiles, unless {@code isVertical}), with its map id so two alike objects stay two.
     */
    static Placed placed(String key, TiledObject o) { return placed(key, o, false); }

    static Placed placed(String key, TiledObject o, boolean carriesNoDeath) {
        float left = o.x, right = o.x + o.width;
        if (o.polyline != null) {
            left = Float.MAX_VALUE; right = -Float.MAX_VALUE;
            for (int i = 0; i < o.polyline.length; i += 2) { left = Math.min(left, o.polyline[i]); right = Math.max(right, o.polyline[i]); }
        }
        if (!o.propBool("isVertical", false)) { left -= o.propFloat("offNeg", 0) * TILE; right += o.propFloat("offPos", 0) * TILE; }
        return new Placed(o.id, key, left, right, carriesNoDeath);
    }

    /**
     * D107: solid ground lies under the object's whole span, so the support lands the hero on it. A one-way {@code Platform} does not
     * count (a falling support passes through it, D69), nor does quicksand; the floor blocks together must cover the span without a gap.
     */
    static boolean floorUnder(TiledMapData map, RoomLayout layout, TiledObject o) {
        List<TiledObject> room = new ArrayList<>();
        for (TiledObject c : map.layer("Collisions")) if (layout.owner(c) == layout.owner(o)) room.add(c);
        return floorUnder(room, o);
    }

    /** {@link #floorUnder(TiledMapData, RoomLayout, TiledObject)} over the room's collision blocks. */
    static boolean floorUnder(List<TiledObject> collisions, TiledObject o) {
        List<float[]> floors = new ArrayList<>();
        for (TiledObject c : collisions)
            if (!"QuickSand".equals(c.type) && !"Platform".equals(c.type) && c.y >= o.y + o.height) floors.add(new float[]{c.x, c.x + c.width});
        return covers(floors, o.x, o.x + o.width);
    }

    /** The spans together cover {@code left}..{@code right} without a gap. */
    static boolean covers(List<float[]> spans, float left, float right) {
        spans.sort((x, y) -> Float.compare(x[0], y[0]));
        float covered = left;
        for (float[] f : spans) if (f[0] <= covered) covered = Math.max(covered, f[1]);
        return covered >= right;
    }

    /** The geometry key a skill beat names (registry, D105), or null: a branch (the wall jump is gone, D121). */
    static String geometryKey(String group, TiledObject o) {
        if ("branch".equals(o.prop("skin", ""))) return "Geometry:branch";
        return null;
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
                List<List<Placed>> placed = new ArrayList<>();
                for (int i = 0; i < layout.rooms.size(); i++) { keys.add(new TreeSet<>()); placed.add(new ArrayList<>()); }
                for (String group : ELEMENT_GROUPS) {
                    for (TiledObject o : map.layer(group)) {
                        String geometry = geometryKey(group, o);
                        if (geometry != null) placed.get(layout.owner(o)).add(placed(geometry, o));
                        String key = key(group, o);
                        if (key == null || "Deadline".equals(key)) continue; // a Deadline is level-wide and may lie outside rooms
                        keys.get(layout.owner(o)).add(key);
                        placed.get(layout.owner(o)).add(placed(key, o, "FallingPlatform".equals(o.type) && floorUnder(map, layout, o)));
                    }
                }
                for (int i = 0; i < layout.rooms.size(); i++)
                    rooms.add(new Room(unmarked ? level : layout.rooms.get(i).id(), keys.get(i), placed.get(i)));
            }
            return rooms;
        } finally {
            Gdx.files = previous;
        }
    }

    private static String shippedCards() throws IOException { return Files.readString(root().resolve("docs/ROOM_CARDS.md")); }

    @Test void everyShippedRoomHasACardThatGivesTheEnforcedNovelty() throws IOException {
        List<Room> rooms = shippedRooms();
        assertEquals(27, rooms.size(), "forest-0 has six rooms, forest-1 twelve, forest-2 nine: update this count with a new card");
        List<String> problems = problems(shippedCards(), rooms);
        assertTrue(problems.isEmpty(), "docs/ROOM_CARDS.md is out of step with the maps (D72):\n  " + String.join("\n  ", problems));
    }

    /** D105: every shipped card is dense and meets its new elements in a pocket; no room shipped below D105 remains (#500). */
    @Test void everyShippedRoomIsDense() throws IOException {
        Map<String, Path> tests = new HashMap<>();
        try (var files = Files.walk(root().resolve("core/src/test/java"))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(f -> tests.put(f.getFileName().toString().replace(".java", ""), f));
        }
        List<String> problems = densityProblems(shippedCards(), shippedRooms(), name -> {
            try { return tests.containsKey(name) ? Files.readString(tests.get(name)) : null; } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        assertTrue(problems.isEmpty(), "docs/ROOM_CARDS.md breaks the room density (D105):\n  " + String.join("\n  ", problems));
    }

    /** The fixed lesson list names shipped rooms and only shrinks; no shipped room is exempt from the band (#500). */
    @Test void theLessonListOnlyShrinks() {
        Set<String> names = new HashSet<>();
        for (Room room : shippedRooms()) names.add(room.name());
        for (String room : LESSON_ROOMS) assertTrue(names.contains(room), room + " is not a shipped room");
        assertEquals(LESSON_ROOMS_AT_D105, LESSON_ROOMS.size(), "LESSON_ROOMS only shrinks: a new lesson outside the tutorial needs its own decision (D105)");
    }

    @Test void theRegistryCoversTheSkillsAndElementsTheCardsUse() throws IOException {
        Registry registry = registry(shippedCards());
        assertTrue(registry.elements.contains("branch-climb"), "skills without a Tiled key are registered too");
        assertFalse(registry.elements.contains("wall-jump"), "the wall jump is removed (D121)");
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

    // ---- D105: each density rule fails when it should ----

    private static final String DENSE_REGISTRY = """

        ## Element registry

        | Element id | Tiled keys | What the player does | Beat kind |
        | --- | --- | --- | --- |
        | lever | `Trigger` | levers | mechanism |
        | thorn | `ThornRoot`, `ThornTuft` | jumps | hazard |
        | mob | `RedMob` | fights | monster |
        | puff | `Puffball` | waits | hazard |
        | coin | `Collectable:Coin` | collects | pickup |
        | climb | `Geometry:branch` (skill) | climbs | skill |
        | boss | `Boss` | fights | monster |
        | crumble | `FallingPlatform` | crosses | hazard |

        ## Cards
        """;

    /** The pinning test the synthetic cards name: it pins the times they use and names the rooms it checks. */
    private static final String T_SOURCE = "static final float A = 9.00f, B = 13.00f, C = 4.00f, D = 8.00f;"
        + " void pins() { assertEquals(A, x, 0.1f); assertEquals(B, x, 0.1f); assertEquals(C, x, 0.1f); }"
        + " void dPuff() { pocket(\"d\", \"puff\", 400, \"WARNING\"); }"
        + " void entryPuff() { pocket(\"forest-entry\", \"puff\", 400, \"WARNING\"); }"
        + " void dClimb() { pocket(\"d\", \"climb\", 200, \"WARNING\"); }"
        + " void wrongRoom() { pocket(\"elsewhere\", \"puff\", 400, \"WARNING\"); }"
        + " void wrongX() { pocket(\"d\", \"puff\", 401, \"WARNING\"); }";

    private static Placed at(int id, String key, float left, float width) { return new Placed(id, key, left, left + width); }

    private static final Room KNOWN = new Room("k", Set.of("Trigger", "ThornRoot", "RedMob", "Puffball", "Collectable:Coin"));
    private static final String KNOWN_CARD = card("k", "lesson", "lever, thorn, mob, puff, coin", "-", "").replace("| Level | test |", "| Level | forest-0, room 1 |");
    private static final Room DENSE = new Room("d", Set.of("ThornTuft", "RedMob", "Puffball", "Collectable:Coin"), List.of(
        at(1, "ThornTuft", 100, 16), at(2, "RedMob", 300, 48), at(3, "Puffball", 500, 16), at(4, "Collectable:Coin", 600, 16)));

    /** A room after the lesson {@code k}; {@code rows} are its D105 rows. */
    private static String dense(String role, String introduces, String rows) {
        return card("d", role, introduces, "thorn: now beside a mob", rows).replace("| Mastered route | 1 s |", "| Mastered route | 9.00 s, 6.00 s with a charge |");
    }

    private static final String DENSE_ROWS = "| Beats | thorn@108, mob@320, puff@508 |\n| Beat time | 9.00 (`T`) |";

    private static List<String> density(String cards, Room... rooms) { return density(Set.of(), cards, rooms); }

    private static List<String> density(Set<String> grandfathered, String cards, Room... rooms) {
        return densityProblems(DENSE_REGISTRY + KNOWN_CARD + cards, List.of(rooms), name -> "T".equals(name) ? T_SOURCE : null, grandfathered);
    }

    @Test void aDenseRoomPasses() {
        assertEquals(List.of(), density(dense("variation", "-", DENSE_ROWS), KNOWN, DENSE));
    }

    @Test void tooFewBeatsOneElementOrTooSlowFails() {
        List<String> p = density(dense("variation", "-", "| Beats | thorn@108, mob@320 |\n| Beat time | 9.00 (`T`) |"), KNOWN, DENSE);
        assertTrue(p.stream().anyMatch(s -> s.contains("2 beats (at least 3)") && s.contains("4.50 s per beat")), p.toString());
        Room three = new Room("d", Set.of("ThornTuft"), List.of(at(1, "ThornTuft", 100, 16), at(2, "ThornTuft", 300, 16), at(3, "ThornTuft", 500, 16)));
        p = density(dense("variation", "-", "| Beats | thorn@108, thorn@308, thorn@508 |\n| Beat time | 9.00 (`T`) |"), KNOWN, three);
        assertTrue(p.stream().anyMatch(s -> s.contains("1 distinct element")), p.toString());
        p = density(dense("variation", "-", DENSE_ROWS).replace("9.00", "13.00"), KNOWN, DENSE);
        assertTrue(p.stream().anyMatch(s -> s.contains("4.33 s per beat")), p.toString());
    }

    @Test void tooDenseNeedsAnOverloadReasonThatCitesANumberedIssue() {
        String fast = dense("variation", "-", DENSE_ROWS).replace("9.00", "4.00");
        assertTrue(density(fast, KNOWN, DENSE).stream().anyMatch(s -> s.contains("1.33 s per beat, denser than 1.5 s")));
        assertTrue(density(fast + "| Overload reason | split under #later |\n", KNOWN, DENSE).stream().anyMatch(s -> s.contains("denser than")),
            "'#later' is no issue: an issue is # and digits");
        assertEquals(List.of(), density(fast + "| Overload reason | split tracked by #999 |\n", KNOWN, DENSE));
    }

    @Test void beatsMustBeRealObjectsOfABeatKind() {
        List<String> p = density(dense("variation", "-", "| Beats | thorn@108, mob@320, puff@508, coin@608, puff@900, ghost@950, bad |\n| Beat time | 9.00 (`T`) |"), KNOWN, DENSE);
        assertTrue(p.contains("d: beat 'coin' is a pickup, which never counts as a beat, D105"), p.toString());
        assertTrue(p.contains("d: beat 'puff@900' has no puff object of its own at that x, D105"), p.toString());
        assertTrue(p.contains("d: beat names unknown element 'ghost'"), p.toString());
        assertTrue(p.contains("d: beat 'bad' is not id@x, D105"), p.toString());
    }

    @Test void oneObjectBacksOneBeatButTwoAlikeObjectsBackTwo() {
        Room one = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(2, "RedMob", 300, 48), at(3, "Puffball", 500, 16)));
        String twice = "| Beats | thorn@108, thorn@108, mob@320, puff@508 |\n| Beat time | 9.00 (`T`) |";
        assertTrue(density(dense("variation", "-", twice), KNOWN, one).contains("d: beat 'thorn@108' has no thorn object of its own at that x, D105"));
        Room alike = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(5, "ThornTuft", 100, 16), at(2, "RedMob", 300, 48), at(3, "Puffball", 500, 16)));
        assertTrue(density(dense("variation", "-", twice), KNOWN, alike).stream().noneMatch(s -> s.contains("of its own")),
            "two alike objects (same type and box) are two objects, told apart by their map id");
    }

    @Test void beatsWithinOneBeatOfEachOtherAreOneBeat() {
        Room close = new Room("d", Set.of("ThornTuft", "RedMob"), List.of(at(1, "ThornTuft", 100, 16), at(2, "ThornTuft", 200, 16), at(3, "RedMob", 400, 48)));
        List<String> p = density(dense("variation", "-", "| Beats | thorn@108, thorn@208, mob@420 |\n| Beat time | 9.00 (`T`) |"), KNOWN, close);
        assertTrue(p.stream().anyMatch(s -> s.contains("the beats 'thorn' at x 100 and x 200 lie within 96 px")), p.toString());
        Room patrol = new Room("d", Set.of("RedMob"), List.of(at(1, "RedMob", 100, 100), at(2, "RedMob", 290, 48), at(3, "Puffball", 500, 16)));
        p = density(dense("variation", "-", "| Beats | mob@120, mob@300, puff@508 |\n| Beat time | 9.00 (`T`) |"), KNOWN, patrol);
        assertTrue(p.stream().anyMatch(s -> s.contains("lie within 96 px")), "the span includes the patrol: " + p);
    }

    @Test void aSkillBeatNeedsTheGeometryThatPosesIt() {
        String rows = "| Beats | thorn@108, climb@200, mob@320 |\n| Beat time | 9.00 (`T`) |";
        assertTrue(density(dense("variation", "-", rows), KNOWN, DENSE).contains("d: beat 'climb@200' has no climb object of its own at that x, D105"),
            "a skill is no free beat");
        Room wall = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(7, "Geometry:branch", 200, 16), at(2, "RedMob", 300, 48)));
        assertEquals(List.of(), density(dense("variation", "-", rows), KNOWN, wall));
    }

    @Test void beatsRunInRouteOrder() {
        List<String> p = density(dense("variation", "-", "| Beats | mob@320, thorn@108, puff@508 |\n| Beat time | 9.00 (`T`) |"), KNOWN, DENSE);
        assertTrue(p.contains("d: beat 'thorn@108' is out of route order (Beats run in rising map x), D105"), p.toString());
    }

    @Test void aBeatTimeIsTheRequiredRoutesWholeTokenPinnedByItsTest() {
        String other = dense("variation", "-", DENSE_ROWS).replace("| Mastered route | 9.00 s, 6.00 s with a charge |", "| Mastered route | 19.00 s, then 9.00 s |");
        assertTrue(density(other, KNOWN, DENSE).stream().anyMatch(s -> s.contains("is not the first time of 'Mastered route' (19.00 s)")),
            "9.00 inside 19.00 is no match, and a later time is not the required route");
        String unpinned = dense("variation", "-", DENSE_ROWS).replace("9.00", "8.00");
        assertTrue(density(unpinned, KNOWN, DENSE).stream().anyMatch(s -> s.contains("is not asserted as 8.00f (assertEquals) in T")),
            "8.00f stands in T but no assertEquals uses it: printed, not pinned");
        String noTest = dense("variation", "-", DENSE_ROWS.replace("9.00 (`T`)", "9.00"));
        assertTrue(density(noTest, KNOWN, DENSE).stream().anyMatch(s -> s.contains("must read 'seconds (`TestClass`)'")));
        String missing = dense("variation", "-", DENSE_ROWS.replace("(`T`)", "(`Missing`)"));
        assertTrue(density(missing, KNOWN, DENSE).stream().anyMatch(s -> s.contains("not asserted as 9.00f (assertEquals) in Missing")));
    }

    @Test void theExemptRolesAreEarnedNotDeclared() {
        String tutorial = dense("lesson", "-", "").replace("| Level | test |", "| Level | forest-0, room 2 |");
        assertEquals(List.of(), density(tutorial, KNOWN, DENSE), "a tutorial lesson is outside the band");
        String elsewhere = dense("lesson", "-", "").replace("| Level | test |", "| Level | forest-1, room 4 |");
        assertTrue(density(elsewhere, KNOWN, DENSE).contains("d: role lesson outside forest-0 and not in LESSON_ROOMS, D105"));
        String listed = card("forest-1-lever", "lesson", "-", "lever: drives two", "").replace("| Level | test |", "| Level | forest-1, room 4 |");
        assertEquals(List.of(), density(listed, KNOWN, new Room("forest-1-lever", Set.of("Trigger"))), "the listed lesson, with nothing lethal");
        assertTrue(density(listed, KNOWN, new Room("forest-1-lever", Set.of("Trigger", "RedMob"))).stream().anyMatch(s -> s.contains("holds a lethal element")));
        assertTrue(density(dense("boss", "-", ""), KNOWN, DENSE).contains("d: role boss, but the room holds no boss, D105"));
        assertEquals(List.of(), density(dense("boss", "-", ""), KNOWN, new Room("d", Set.of("Boss"))));
        assertTrue(density(dense("rest", "-", ""), KNOWN, DENSE).contains("d: role rest, but the room holds a lethal element, D105"));
        Room calm = new Room("d", Set.of("Trigger")), second = new Room("e", Set.of());
        assertEquals(List.of(), density(dense("rest", "-", ""), KNOWN, calm), "a calm room is exempt from the minimum");
        List<String> p = density(dense("rest", "-", "") + card("e", "reward", "-", "lever: again", ""), KNOWN, calm, second);
        assertTrue(p.contains("e: a second calm room (rest or reward) in test, D105"), p.toString());
    }

    @Test void belowDensityIsOnlyForTheGrandfatheredRooms() {
        String thin = card("forest-1-hive", "variation", "-", "thorn: again", "| Beats | thorn@108 |\n| Beat time | 9.00 (`T`) |\n")
            .replace("| Mastered route | 1 s |", "| Mastered route | 9.00 s |");
        Room mob = new Room("forest-1-hive", Set.of("ThornTuft"), List.of(at(1, "ThornTuft", 100, 16)));
        assertTrue(density(Set.of("forest-1-hive"), thin, KNOWN, mob).stream().anyMatch(s -> s.startsWith("forest-1-hive: below the density")));
        assertTrue(density(Set.of("forest-1-hive"), thin + "| Below density | reworked later, #soon |\n", KNOWN, mob).stream().anyMatch(s -> s.startsWith("forest-1-hive: below the density")),
            "it must cite a numbered issue");
        assertEquals(List.of(), density(Set.of("forest-1-hive"), thin + "| Below density | reworked by #500 |\n", KNOWN, mob));
        Room d = new Room("d", Set.of("ThornTuft"), List.of(at(1, "ThornTuft", 100, 16)));
        assertTrue(density(Set.of("forest-1-hive"), thin.replace("forest-1-hive", "d") + "| Below density | reworked by #500 |\n", KNOWN, d).stream()
            .anyMatch(s -> s.contains("only for the rooms shipped below D105")));
        String passing = dense("variation", "-", DENSE_ROWS).replace("### d", "### forest-1-hive") + "| Below density | reworked by #500 |\n";
        assertTrue(density(Set.of("forest-1-hive"), passing, KNOWN, new Room("forest-1-hive", DENSE.keys(), DENSE.placed())).stream().anyMatch(s -> s.contains("still carries 'Below density'")));
    }

    /** {@code d} introduces the puffball (the lesson before it does not); {@code rows} are its Beats, Beat time and First meeting. */
    private static List<String> meeting(String room, Room map, String rows) {
        String base = card("k", "lesson", "lever, thorn, mob, coin", "-", "").replace("| Level | test |", "| Level | forest-0, room 1 |");
        String card = card(room, "variation", "puff", "-", rows).replace("| Mastered route | 1 s |", "| Mastered route | 9.00 s |");
        return densityProblems(DENSE_REGISTRY + base + card, List.of(new Room("k", Set.of("Trigger", "ThornRoot", "RedMob", "Collectable:Coin")), map),
            name -> "T".equals(name) ? T_SOURCE : null, Set.<String>of(), Set.of("forest-entry"));
    }

    @Test void aFirstMeetingIsEarlyOrCheapClearAndPinnedByATestOfItsRoom() {
        String second = "| Beats | thorn@108, puff@250, mob@420 |\n| Beat time | 9.00 (`T`) |\n";
        Room room = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(3, "Puffball", 250, 16), at(2, "RedMob", 420, 48)));
        String good = "| First meeting | puff: beat 2, pocket at x 400, it swells and clouds in view (`T.dPuff`) |\n";
        assertTrue(meeting("d", room, second).stream().anyMatch(s -> s.contains("introduces 'puff' without a 'First meeting'")));
        assertEquals(List.of(), meeting("d", room, second + good));
        assertTrue(meeting("d", room, second + good.replace("T.dPuff", "T.wrongRoom")).stream().anyMatch(s -> s.contains("does not name the room, the element and the pocket x")));
        assertTrue(meeting("d", room, second + good.replace("T.dPuff", "T.wrongX")).stream().anyMatch(s -> s.contains("does not name the room, the element and the pocket x")),
            "the test must stand the hero at the card's pocket x");
        assertTrue(meeting("d", room, second + good.replace("T.dPuff", "T.missing")).stream().anyMatch(s -> s.contains("no named test method")));
        assertTrue(meeting("d", room, second + good.replace("T.dPuff", "T")).stream().anyMatch(s -> s.contains("no named test method")), "a class is not a method");
        assertTrue(meeting("d", room, second + good.replace("pocket at x 400, ", "")).stream().anyMatch(s -> s.contains("no 'pocket at x N'")));
        assertTrue(meeting("d", room, second + good.replace("beat 2", "beat 1")).stream().anyMatch(s -> s.contains("says beat 1, but it is beat 2")));
        String third = "| Beats | thorn@108, mob@320, puff@508 |\n| Beat time | 9.00 (`T`) |\n";
        assertTrue(meeting("d", DENSE, third + good.replace("beat 2", "beat 3")).stream().anyMatch(s -> s.contains("first met as beat 3")));
        assertEquals(List.of(), meeting("d", DENSE, third + good.replace("beat 2", "beat 3, retry 5.5 s")), "a cheap retry is enough");
        assertTrue(meeting("d", DENSE, third + good.replace("beat 2", "beat 3, retry 6.5 s")).stream().anyMatch(s -> s.contains("first met as beat 3")));
        Room crowded = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(3, "Puffball", 250, 16), at(2, "RedMob", 300, 48)));
        assertTrue(meeting("d", crowded, second.replace("mob@420", "mob@320") + good).stream()
            .anyMatch(s -> s.contains("'RedMob' at x 300 lies 34 px from it (at least 96)")), "another lethal object within 96 px");
    }

    // ---- D107: a crumble support counts as lethal only when it can kill ----

    private static List<String> climbMeeting(Room map) {
        String base = card("k", "lesson", "lever, thorn, mob, coin", "-", "").replace("| Level | test |", "| Level | forest-0, room 1 |");
        String card = card("d", "variation", "climb", "-", "| Beats | thorn@108, climb@200, mob@420 |\n| Beat time | 9.00 (`T`) |\n"
            + "| First meeting | climb: beat 2, pocket at x 200 (`T.dClimb`) |\n").replace("| Mastered route | 1 s |", "| Mastered route | 9.00 s |");
        return densityProblems(DENSE_REGISTRY + base + card, List.of(new Room("k", Set.of("Trigger", "ThornRoot", "RedMob", "Collectable:Coin")), map),
            name -> "T".equals(name) ? T_SOURCE : null);
    }

    @Test void aCrumbleSupportOverAFloorIsNoLethalNeighbourButOverAPitItIs() {
        Placed climb = at(7, "Geometry:branch", 200, 16);
        Room over = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 80, 16), climb, new Placed(8, "FallingPlatform", 230, 262, true), at(2, "RedMob", 420, 48)));
        assertEquals(List.of(), climbMeeting(over), "a support that drops the hero onto a floor is no neighbour that can kill (D107)");
        Room pit = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 80, 16), climb, at(8, "FallingPlatform", 230, 32), at(2, "RedMob", 420, 48)));
        assertTrue(climbMeeting(pit).stream().anyMatch(s -> s.contains("'FallingPlatform' at x 230 lies 14 px from it")), "over a pit it still counts");
    }

    /** The shipped maps: the rotten log over the pit (forest-1-traps) is lethal, the falling platforms and rotten branches over a floor are not. */
    @Test void onTheShippedMapsOnlyTheSupportsOverAFloorCarryNoDeath() {
        Map<Integer, Boolean> expected = new HashMap<>();
        expected.put(37, false);                                          // the rotten log over the pit (forest-1-traps)
        for (int id : new int[]{21, 22, 23, 24, 58, 85, 86, 87, 88, 89}) expected.put(id, true); // the escalator's platforms, the shelf's, the rotten column
        Set<Integer> seen = new HashSet<>();
        for (Room room : shippedRooms()) for (Placed p : room.placed())
            if (room.name().startsWith("forest-1") && p.key().startsWith("FallingPlatform") && expected.containsKey(p.id())) {
                assertEquals(expected.get(p.id()), p.carriesNoDeath(), room.name() + " object " + p.id() + " (" + p.key() + ")");
                seen.add(p.id());
            }
        assertEquals(expected.keySet(), seen, "every listed support is on the shipped forest-1 map");
    }

    @Test void aFloorMustCoverTheWholeSpanAndBeSolid() {
        assertTrue(covers(new ArrayList<>(List.of(new float[]{0, 100}, new float[]{100, 200})), 40, 160), "two blocks that meet cover it");
        assertFalse(covers(new ArrayList<>(List.of(new float[]{0, 100})), 40, 160), "half over a pit is lethal");
        assertFalse(covers(new ArrayList<>(List.of(new float[]{0, 90}, new float[]{110, 200})), 40, 160), "a gap under the middle is lethal");
        assertFalse(covers(new ArrayList<>(), 40, 160), "nothing under it is a pit");
    }

    private static TiledObject box(String type, float x, float y, float w, float h) {
        TiledObject o = new TiledObject();
        o.type = type; o.x = x; o.y = y; o.width = w; o.height = h;
        return o;
    }

    @Test void aOneWayPlatformOrQuicksandIsNoFloorForASupport() {
        TiledObject support = box("FallingPlatform", 16, 16, 32, 16);
        assertTrue(floorUnder(List.of(box("", 0, 48, 64, 16)), support), "a solid floor under it");
        assertFalse(floorUnder(List.of(box("Platform", 0, 48, 64, 16)), support), "a one-way platform over a pit: the support passes through it (D69), lethal");
        assertFalse(floorUnder(List.of(box("QuickSand", 0, 48, 64, 16)), support), "quicksand is no floor");
        assertFalse(floorUnder(List.of(box("", 0, 48, 32, 16)), support), "half a floor is lethal");
        assertFalse(floorUnder(List.of(box("", 0, 16, 64, 16)), support), "a block beside it, not under it, is no floor");
    }

    @Test void anObjectOfTheSkillsOwnGeometryIsNotItsOwnNeighbour() {
        Placed climb = at(7, "Geometry:branch", 200, 16);
        Room same = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 80, 16), climb, at(7, "FallingPlatform", 200, 16), at(2, "RedMob", 420, 48)));
        assertEquals(List.of(), climbMeeting(same), "one object listed under the skill's key and its own (a rotten branch)");
        Room other = new Room("d", DENSE.keys(), List.of(at(1, "ThornTuft", 80, 16), climb, at(9, "FallingPlatform", 200, 16), at(2, "RedMob", 420, 48)));
        assertTrue(climbMeeting(other).stream().anyMatch(s -> s.contains("'FallingPlatform' at x 200 lies 0 px from it")), "another object is a neighbour");
    }

    @Test void aRewardRoomMayHoldACrumbleSupportOverAFloorButNotOverAPit() {
        Room floor = new Room("d", Set.of("FallingPlatform"), List.of(new Placed(8, "FallingPlatform", 230, 262, true)));
        assertEquals(List.of(), density(dense("reward", "-", ""), KNOWN, floor), "nothing in it can kill (D107)");
        Room pit = new Room("d", Set.of("FallingPlatform"), List.of(at(8, "FallingPlatform", 230, 32)));
        assertTrue(density(dense("reward", "-", ""), KNOWN, pit).contains("d: role reward, but the room holds a lethal element, D105"));
        Room mixed = new Room("d", Set.of("FallingPlatform"), List.of(new Placed(8, "FallingPlatform", 230, 262, true), at(9, "FallingPlatform", 400, 32)));
        assertTrue(density(dense("reward", "-", ""), KNOWN, mixed).contains("d: role reward, but the room holds a lethal element, D105"), "one over a pit is enough");
    }

    @Test void aLateFirstMeetingIsExcusedOnlyOnTheShrinkingListWithANumberedIssue() {
        Room crowded = new Room("forest-entry", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(3, "Puffball", 250, 16), at(2, "RedMob", 300, 48)));
        String rows = "| Beats | thorn@108, puff@250, mob@320 |\n| Beat time | 9.00 (`T`) |\n";
        String entry = "| First meeting | puff: beat 2, pocket at x 400 (`T.entryPuff`)";
        assertTrue(meeting("forest-entry", crowded, rows + entry + " |\n").stream().anyMatch(s -> s.contains("must cite the rework issue")));
        assertEquals(List.of(), meeting("forest-entry", crowded, rows + entry + ", crowded, tracked by #500 |\n"));
        Room clear = new Room("forest-entry", DENSE.keys(), List.of(at(1, "ThornTuft", 100, 16), at(3, "Puffball", 250, 16), at(2, "RedMob", 420, 48)));
        assertTrue(meeting("forest-entry", clear, rows.replace("mob@320", "mob@420") + entry + ", tracked by #500 |\n")
            .contains("forest-entry: its first meetings pass D105: remove the room from the first-meeting exemption set"));
    }

    @Test void aRegistryRowWithoutABeatKindFailsClearly() {
        List<String> p = densityProblems(REGISTRY, List.of(), name -> null);
        assertTrue(p.contains("registry: element 'lever' has no Beat kind (one of [harmless, hazard, mechanism, monster, pickup, platform, skill]), D105"), p.toString());
        String noKinds = REGISTRY + card("d", "variation", "-", "lever: again", "| Beats | lever@10 |\n| Beat time | 1 (`T`) |\n");
        p = densityProblems(noKinds, List.of(new Room("d", Set.of("Trigger"), List.of(at(1, "Trigger", 0, 16)))), name -> null);
        assertTrue(p.contains("d: beat 'lever' has no Beat kind in the registry, D105"), "no exception, a clear message: " + p);
    }
}
