package com.jlogicsoftware.kimeria.world;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate of the trap design system (D71, #348, docs/TRAPS.md): every object type that {@code Level} spawns is either a
 * trap with a catalogue row or a listed non-trap type, and every trap row satisfies the visibility rule. A new trap
 * type added to the spawn switch without a card and a row fails here. Every trap card also states the consequence of the
 * trap's act once (D93, {@link #consequenceProblems}).
 *
 * <p>It reads the spawn switch of {@code world/Level.java} and the two marked tables of {@code docs/TRAPS.md} as
 * text. What it cannot check: that the art matches the card (a human or a frame test), contrast (#344), read distance.
 */
public class TrapCatalogTest {
    static final String LEVEL = "core/src/main/java/com/jlogicsoftware/kimeria/world/Level.java";
    static final String TRAPS = "docs/TRAPS.md";
    static final Set<String> VISIBILITY = Set.of("VISIBLE", "DISGUISED", "HIDDEN");
    static final Set<String> FAMILIES = Set.of("proximity", "position", "touch", "rhythm", "crumble");
    public static final List<String> TRAP_COLUMNS = List.of("Type", "Family", "Visibility", "At-rest cue", "Warning", "Taught in", "Decision");
    static final List<String> NON_TRAP_COLUMNS = List.of("Type", "Category", "Why it is not a trap");
    private static final Pattern PENDING = Pattern.compile("pending #\\d+");
    private static final Pattern CASE = Pattern.compile("case\\s+((?:\"[A-Za-z0-9]+\"\\s*,?\\s*)+)->");
    private static final Pattern QUOTED = Pattern.compile("\"([A-Za-z0-9]+)\"");
    /** D93: the one consequence statement of a card, and its three allowed forms. */
    static final String CONSEQUENCE = "Consequence (D93):";
    private static final Pattern CONSEQUENCE_LINE = Pattern.compile(Pattern.quote(CONSEQUENCE) + "\\s*([^|\\n]*)");
    private static final Pattern NONE_YET = Pattern.compile("^none yet \\(\\[?#\\d+");
    private static final Pattern INTENTIONALLY_NONE = Pattern.compile("^intentionally none \\(D\\d+");
    private static final Pattern SOUNDS = Pattern.compile("^`[a-z0-9_]+`(?:,\\s*`[a-z0-9_]+`)*");
    private static final Pattern SOUND = Pattern.compile("`([a-z0-9_]+)`");

    // ---- the real files ------------------------------------------------------------------------------------------

    @Test
    void everySpawnedTypeHasATrapCardOrANonTrapReasonAndEveryTrapFollowsD71() throws IOException {
        List<String> problems = problems(read(LEVEL), read(TRAPS));
        assertTrue(problems.isEmpty(), "docs/TRAPS.md and Level disagree (D71):\n  " + String.join("\n  ", problems));
    }

    @Test
    void everyTrapCardStatesTheConsequenceOfItsAct() throws IOException {
        Path root = repositoryRoot();
        Set<String> listed = Set.of(com.jlogicsoftware.kimeria.Assets.SOUND_EFFECTS);
        Predicate<String> sound = name -> listed.contains(name) && Files.exists(root.resolve("assets/audio/" + name + ".wav"));
        List<String> problems = consequenceProblems(read(TRAPS), sound);
        assertTrue(problems.isEmpty(), "docs/TRAPS.md cards and D93 disagree:\n  " + String.join("\n  ", problems));
    }

    @Test
    void theSpawnSwitchParserSeesTheKnownTraps() throws IOException {
        Set<String> types = spawnTypes(read(LEVEL));
        for (String t : List.of("ThornRoot", "ThornTuft", "FallingBranch", "FallingStone", "ExplosiveStump", "ExplosiveRoot",
            "Bomb", "Puffball", "ThornVine", "HoneyFungus", "FalseHoneyFungus", "FallingPlatform", "Player", "Decor"))
            assertTrue(types.contains(t), "spawn switch parser missed " + t + ": " + types);
        assertFalse(types.contains("Deadline"), "Deadline is handled before the switch");
        assertFalse(types.contains("QuickSand"), "QuickSand belongs to the collision switch");
    }

    // ---- the gate's rules, each shown to fire ----------------------------------------------------------------------

    private static final String LEVEL_FIXTURE = """
        void spawn(TiledObject o) {
            switch (o.type) {
                case "Player" -> { }
                case "ThornRoot", "ThornTuft" -> objects.add(x);
                default -> { }
            }
            spawnLevers();
        }
        void other() { CollisionBlock block = switch (o.type) { case "QuickSand" -> null; default -> null; }; }
        """;

    /** Card headings for the two fixture traps; the fungus-style shared heading proves a type may share one. */
    private static final String HEADINGS = "## Cards\n\n### Thorn root (`ThornRoot`)\n\n### Thorn tuft and friend (`ThornTuft`, `Other`)\n";

    private static String doc(String trapRows, String nonTrapRows) { return doc(trapRows, nonTrapRows, HEADINGS); }

    private static String doc(String trapRows, String nonTrapRows, String headings) {
        return "<!-- trap-catalogue:start -->\n| Type | Family | Visibility | At-rest cue | Warning | Taught in | Decision |\n| --- | --- | --- | --- | --- | --- | --- |\n"
            + trapRows + "<!-- trap-catalogue:end -->\n<!-- non-trap-types:start -->\n| Type | Category | Why it is not a trap |\n| --- | --- | --- |\n"
            + nonTrapRows + "<!-- non-trap-types:end -->\n\n" + headings;
    }

    @Test
    void aTrapRowWithoutACardHeadingFails() {
        // The type still appears elsewhere (its table row, prose in parentheses), which must not count as a card.
        String noTuftCard = "## Cards\n\n### Thorn root (`ThornRoot`)\n\nThe tuft (`ThornTuft`) is like the root.\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + TUFT_OK, PLAYER, noTuftCard)).stream()
            .anyMatch(p -> p.contains("ThornTuft") && p.contains("card heading")));
        String inAParagraphOnly = "## Cards\n\n### Thorn root (`ThornRoot`)\n\n#### Not a card (`ThornTuft`)\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + TUFT_OK, PLAYER, inAParagraphOnly)).stream()
            .anyMatch(p -> p.contains("ThornTuft") && p.contains("card heading")));
    }

    private static final String ROOT_OK = "| `ThornRoot` | proximity | HIDDEN | pending #1 | 0.8 s | room | D44 |\n";
    private static final String TUFT_OK = "| `ThornTuft` | proximity | VISIBLE | none needed | 0.8 s | room | D44 |\n";
    private static final String PLAYER = "| `Player` | hero | spawn |\n";

    @Test
    void aConsistentFixturePasses() {
        assertEquals(List.of(), problems(LEVEL_FIXTURE, doc(ROOT_OK + TUFT_OK, PLAYER)));
    }

    @Test
    void aSpawnedTypeWithoutARowFails() {
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK, PLAYER)).stream().anyMatch(p -> p.contains("ThornTuft") && p.contains("no row")));
    }

    @Test
    void aRowForATypeLevelDoesNotSpawnFails() {
        String extra = "| `SwingingLog` | rhythm | VISIBLE | none needed | 0.8 s | room | D99 |\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + TUFT_OK + extra, PLAYER)).stream().anyMatch(p -> p.contains("SwingingLog") && p.contains("does not spawn")));
    }

    @Test
    void aTypeInBothTablesFails() {
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + TUFT_OK, PLAYER + "| `ThornTuft` | scenery | x |\n")).stream().anyMatch(p -> p.contains("both tables")));
    }

    @Test
    void anEmptyColumnOrAnUnknownWordFails() {
        String emptyTaught = "| `ThornTuft` | proximity | VISIBLE | none needed | 0.8 s |  | D44 |\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + emptyTaught, PLAYER)).stream().anyMatch(p -> p.contains("empty column 'Taught in'")));
        String badWord = "| `ThornTuft` | proximity | OBVIOUS | none needed | 0.8 s | room | D44 |\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + badWord, PLAYER)).stream().anyMatch(p -> p.contains("visibility 'OBVIOUS'")));
        String badFamily = "| `ThornTuft` | magic | VISIBLE | none needed | 0.8 s | room | D44 |\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(ROOT_OK + badFamily, PLAYER)).stream().anyMatch(p -> p.contains("family 'magic'")));
    }

    @Test
    void aHiddenOrDisguisedTrapNeedsACueOrAnOpenIssue() {
        for (String cue : List.of("none needed", "none", "pending", "pending #ISSUE_X", "-")) {
            String row = "| `ThornRoot` | proximity | HIDDEN | " + cue + " | 0.8 s | room | D44 |\n";
            assertTrue(problems(LEVEL_FIXTURE, doc(row + TUFT_OK, PLAYER)).stream().anyMatch(p -> p.contains("ThornRoot") && p.contains("at-rest cue")),
                "cue '" + cue + "' must be refused");
        }
        String drawn = "| `ThornRoot` | proximity | DISGUISED | bone sliver in the mound | 0.8 s | room | D44 |\n";
        assertEquals(List.of(), problems(LEVEL_FIXTURE, doc(drawn + TUFT_OK, PLAYER)));
    }

    @Test
    void aHiddenOrDisguisedTrapMustBeTaught() {
        String untaught = "| `ThornRoot` | proximity | HIDDEN | pending #1 | 0.8 s | none | D44 |\n";
        assertTrue(problems(LEVEL_FIXTURE, doc(untaught + TUFT_OK, PLAYER)).stream().anyMatch(p -> p.contains("taught")));
    }

    @Test
    void missingMarkersOrChangedColumnsFail() {
        assertThrows(IllegalStateException.class, () -> problems(LEVEL_FIXTURE, "no tables here"));
        String renamed = doc(ROOT_OK + TUFT_OK, PLAYER).replace("| At-rest cue |", "| Cue |");
        assertThrows(IllegalStateException.class, () -> problems(LEVEL_FIXTURE, renamed));
        assertThrows(IllegalStateException.class, () -> problems("class Level {}", doc(ROOT_OK + TUFT_OK, PLAYER)));
    }

    private static final String ROW_ROOT = "| `ThornRoot` | proximity | VISIBLE | none needed | 0.8 s | room | D44 |\n";
    private static final Predicate<String> FIXTURE_SOUNDS = Set.of("thorn_spring", "stone_impact")::contains;

    private static String consequenceDoc(String rootCard) {
        return doc(ROW_ROOT, PLAYER, "## Cards\n\n### Thorn root (`ThornRoot`)\n\n" + rootCard + "\n\n### Other (`Other`)\n\n"
            + "| Art and sound | x. Consequence (D93): `stone_impact` |\n\n## Maintenance\n");
    }

    @Test
    void aConsequenceIsASoundAnIssueOrADecision() {
        for (String ok : List.of("`thorn_spring`", "`thorn_spring`, `stone_impact`, then dust", "none yet (#446)",
            "none yet ([#446](https://github.com/x/y/issues/446))", "intentionally none (D46: the motion)"))
            assertEquals(List.of(), consequenceProblems(consequenceDoc("| Art and sound | sheet. Consequence (D93): " + ok + " |"), FIXTURE_SOUNDS), ok);
    }

    @Test
    void aCardWithoutAValidConsequenceFails() {
        assertTrue(consequenceProblems(consequenceDoc("| Art and sound | sheet, no sound |"), FIXTURE_SOUNDS).stream()
            .anyMatch(p -> p.contains("ThornRoot") && p.contains("no 'Consequence (D93):'")), "missing statement");
        // The other card's statement must not count for this one.
        for (String bad : List.of("`thorn_snap`", "none", "none yet", "none yet (soon)", "intentionally none", "intentionally none (because)",
            "a rasp", ""))
            assertTrue(consequenceProblems(consequenceDoc("| Art and sound | sheet. Consequence (D93): " + bad + " |"), FIXTURE_SOUNDS).stream()
                .anyMatch(p -> p.contains("ThornRoot")), "'" + bad + "' must be refused");
        String twice = "| Art and sound | Consequence (D93): `thorn_spring` |\n\nConsequence (D93): none yet (#1)";
        assertTrue(consequenceProblems(consequenceDoc(twice), FIXTURE_SOUNDS).stream().anyMatch(p -> p.contains("more than one")));
    }

    // ---- the gate ----------------------------------------------------------------------------------------------------

    static List<String> problems(String levelSource, String trapsDoc) {
        Set<String> spawned = spawnTypes(levelSource);
        Map<String, List<String>> traps = table(trapsDoc, "trap-catalogue");
        Map<String, List<String>> nonTraps = table(trapsDoc, "non-trap-types");
        List<String> out = new ArrayList<>();
        for (String type : spawned)
            if (!traps.containsKey(type) && !nonTraps.containsKey(type))
                out.add(type + ": spawned by Level but has no row in docs/TRAPS.md (write its trap card, or list it as a non-trap type)");
        for (String type : traps.keySet()) {
            if (!spawned.contains(type)) out.add(type + ": catalogue row, but Level does not spawn this type (stale row)");
            if (nonTraps.containsKey(type)) out.add(type + ": listed in both tables");
        }
        for (String type : nonTraps.keySet())
            if (!spawned.contains(type)) out.add(type + ": non-trap row, but Level does not spawn this type (stale row)");
        for (Map.Entry<String, List<String>> e : traps.entrySet()) {
            String type = e.getKey();
            List<String> row = e.getValue();
            for (int i = 1; i < TRAP_COLUMNS.size(); i++)
                if (row.get(i).isEmpty()) out.add(type + ": empty column '" + TRAP_COLUMNS.get(i) + "'");
            String family = row.get(1), visibility = row.get(2), cue = row.get(3), taught = row.get(5);
            if (!family.isEmpty() && !FAMILIES.contains(family)) out.add(type + ": unknown family '" + family + "' (one of " + FAMILIES + ")");
            if (!visibility.isEmpty() && !VISIBILITY.contains(visibility)) out.add(type + ": unknown visibility '" + visibility + "' (one of " + VISIBILITY + ")");
            if (!hasCardHeading(trapsDoc, type)) out.add(type + ": no card heading '### <Name> (`" + type + "`)' in docs/TRAPS.md");
            if ("DISGUISED".equals(visibility) || "HIDDEN".equals(visibility)) {
                if (!hasCue(cue)) out.add(type + ": " + visibility + " needs an always-drawn at-rest cue or 'pending #NNN' with an issue reference (D71), got '" + cue + "'");
                if (taught.isEmpty() || taught.equalsIgnoreCase("none")) out.add(type + ": " + visibility + " must be taught in a teaching room first (D71)");
            }
        }
        return out;
    }

    /**
     * D93: the card of every catalogue trap holds exactly one {@value #CONSEQUENCE} statement: sound names in backticks
     * (each one accepted by {@code sound}: a file in {@code assets/audio} listed in {@code Assets.SOUND_EFFECTS}),
     * {@code none yet (#NNN)} or {@code intentionally none (Dxx)}. A card shared by two types serves both.
     */
    static List<String> consequenceProblems(String trapsDoc, Predicate<String> sound) {
        List<String> out = new ArrayList<>();
        for (String type : table(trapsDoc, "trap-catalogue").keySet()) {
            String card = cardText(trapsDoc, type);
            if (card == null) continue; // the catalogue gate reports the missing heading
            Matcher m = CONSEQUENCE_LINE.matcher(card);
            if (!m.find()) { out.add(type + ": card has no '" + CONSEQUENCE + "' statement (D93)"); continue; }
            String value = m.group(1).trim();
            if (m.find()) out.add(type + ": card has more than one '" + CONSEQUENCE + "' statement");
            if (value.startsWith("none yet")) {
                if (!NONE_YET.matcher(value).find()) out.add(type + ": 'none yet' must link its issue: none yet (#NNN), got '" + value + "'");
            } else if (value.startsWith("intentionally none")) {
                if (!INTENTIONALLY_NONE.matcher(value).find()) out.add(type + ": 'intentionally none' must name its decision: intentionally none (Dxx), got '" + value + "'");
            } else {
                Matcher list = SOUNDS.matcher(value);
                if (!list.find()) { out.add(type + ": consequence must be sounds in backticks, none yet (#NNN) or intentionally none (Dxx), got '" + value + "'"); continue; }
                Matcher each = SOUND.matcher(list.group());
                while (each.find())
                    if (!sound.test(each.group(1))) out.add(type + ": consequence sound '" + each.group(1) + "' is not in assets/audio and Assets.SOUND_EFFECTS");
            }
        }
        return out;
    }

    /** The card's text: from its heading to the next level-2 or level-3 heading, or null without a heading. */
    static String cardText(String doc, String type) {
        Matcher h = Pattern.compile("^### [^\\n]*\\([^)\\n]*`" + Pattern.quote(type) + "`[^)\\n]*\\)\\s*$", Pattern.MULTILINE).matcher(doc);
        if (!h.find()) return null;
        Matcher next = Pattern.compile("^#{2,3} ", Pattern.MULTILINE).matcher(doc);
        int end = next.find(h.end()) ? next.start() : doc.length();
        return doc.substring(h.end(), end);
    }

    /** A level-3 heading line whose final parenthesised list names the type in backticks: {@code ### Name (`A`, `B`)}. */
    static boolean hasCardHeading(String doc, String type) {
        Pattern heading = Pattern.compile("^### [^\\n]*\\([^)\\n]*`" + Pattern.quote(type) + "`[^)\\n]*\\)\\s*$", Pattern.MULTILINE);
        return heading.matcher(doc).find();
    }

    private static boolean hasCue(String cue) {
        String c = cue.trim().toLowerCase();
        if (c.isEmpty() || c.equals("-") || c.equals("none") || c.startsWith("none ")) return false;
        if (c.startsWith("pending")) return PENDING.matcher(c).find();
        return true;
    }

    /** Type names of the {@code case} labels of the object spawn switch: from {@code switch (o.type)} to {@code spawnLevers();}. */
    static Set<String> spawnTypes(String levelSource) {
        int start = levelSource.indexOf("switch (o.type) {");
        int end = start < 0 ? -1 : levelSource.indexOf("spawnLevers();", start);
        if (start < 0 || end < 0) throw new IllegalStateException("Level: spawn switch 'switch (o.type) { ... spawnLevers();' not found");
        Set<String> types = new LinkedHashSet<>();
        Matcher m = CASE.matcher(levelSource.substring(start, end));
        while (m.find()) {
            Matcher q = QUOTED.matcher(m.group(1));
            while (q.find()) types.add(q.group(1));
        }
        if (types.isEmpty()) throw new IllegalStateException("Level: no case labels in the spawn switch");
        return types;
    }

    /** Rows of the table between {@code <!-- name:start -->} and {@code <!-- name:end -->}, keyed by the backticked type. */
    public static Map<String, List<String>> table(String doc, String name) {
        int start = doc.indexOf("<!-- " + name + ":start -->"), end = doc.indexOf("<!-- " + name + ":end -->");
        if (start < 0 || end < start) throw new IllegalStateException("docs/TRAPS.md: markers for '" + name + "' not found");
        List<String> expected = name.equals("trap-catalogue") ? TRAP_COLUMNS : NON_TRAP_COLUMNS;
        Map<String, List<String>> rows = new LinkedHashMap<>();
        boolean header = false;
        for (String line : doc.substring(start, end).split("\n")) {
            line = line.trim();
            if (!line.startsWith("|")) continue;
            List<String> cells = cells(line);
            if (!header) {
                if (!cells.equals(expected)) throw new IllegalStateException("docs/TRAPS.md '" + name + "': columns " + cells + ", expected " + expected);
                header = true;
                continue;
            }
            if (cells.get(0).startsWith("---")) continue;
            if (cells.size() != expected.size()) throw new IllegalStateException("docs/TRAPS.md '" + name + "': row has " + cells.size() + " cells: " + line);
            String type = cells.get(0).replace("`", "").trim();
            if (rows.put(type, cells) != null) throw new IllegalStateException("docs/TRAPS.md '" + name + "': duplicate row " + type);
        }
        if (!header) throw new IllegalStateException("docs/TRAPS.md '" + name + "': no table between the markers");
        return rows;
    }

    private static List<String> cells(String line) {
        String inner = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length());
        List<String> out = new ArrayList<>();
        for (String c : inner.split("\\|", -1)) out.add(c.trim());
        return out;
    }

    /** Repository root: the working directory or the nearest parent holding {@code docs/TRAPS.md}. */
    static String read(String relative) throws IOException {
        return Files.readString(repositoryRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    static Path repositoryRoot() throws IOException {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve(TRAPS))) dir = dir.getParent();
        if (dir == null) throw new IOException("repository root with " + TRAPS + " not found above " + Paths.get("").toAbsolutePath());
        return dir;
    }
}
