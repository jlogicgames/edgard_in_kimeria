package com.jlogicsoftware.kimeria.world;

import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.Remains;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The gate of D101 (monster bodies): every monster that leaves a body ({@link Remains}) is listed here and, through the real
 * {@code Level}, keeps the rule. Its body lies where it was killed quietly and counts as defeated; its pose differs from every
 * live frame (the readability guarantee); the hero passing rocks it; the sword, a stone and a stomp crumble it, a fire burns it;
 * it outlives neither a room reset nor a passed room. A burst or blast leaves nothing. A new {@code Remains} class that is not
 * listed in {@link #CASES} fails {@link #everyMonsterThatLeavesABodyIsCoveredHere()}.
 */
class RemainsTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int SETTLE_FRAMES = 60, ROCK_FRAMES = 30, BURN_FRAMES = 60;
    /**
     * The body's silhouette must differ from every live frame, drawn either way round, in at least this share of the pixels
     * either covers. The beetle on its back differs from its closest live frames (look and wiggle, mirrored) in 30.5 % of them,
     * measured 2026-11-18; the spider on its back, legs curled up, from its closest (recover) in 48.3 %, measured 2026-11-19; a
     * recoloured live frame would differ in 0 %.
     */
    private static final float POSE_DIFFERENCE = .25f;

    /**
     * One monster that leaves a body: its test map, how to find it, where its art is, and how its body reacts to the hero
     * passing (the beetle and the spider rock; a big body would twitch), read as a size that is 0 at rest.
     */
    private record Case(String map, Function<Level, Enemy<?>> find, String sheet, int frameWidth, int frameHeight,
                        int[] liveFrames, int bodyFrame, String bodySheet, ToDoubleFunction<Remains> reaction) { }

    private static final Map<String, Case> CASES = Map.of(
        "Beetle", new Case("beetle-test", RemainsTest::beetle, "assets/images/objects/monster-beetle.png", 24, 16,
            new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, 9, "assets/images/objects/monster-beetle-body.png", b -> ((Beetle) b).rockAngle()),
        "Spider", new Case("spider-test", RemainsTest::spider, "assets/images/objects/monster-spider.png", 32, 16,
            new int[]{0, 1, 2, 3, 4, 5, 6, 7}, 8, "assets/images/objects/monster-spider-body.png", b -> ((Spider) b).rockAngle()));

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private static Enemy<?> beetle(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Beetle b) return b;
        return null;
    }

    private static Enemy<?> spider(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Spider s) return s;
        return null;
    }

    private static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isRegularFile(p.resolve("docs/DECISIONS.md"))) p = p.getParent();
        assertNotNull(p, "docs/DECISIONS.md not found above the working directory");
        return p;
    }

    private void frames(Level level, int n) { for (int i = 0; i < n; i++) { level.update(STEP); harness.pressed.clear(); } }

    /** A body of the case's monster, killed quietly (as a fireball or a stone would) with the hero far away, settled. */
    private Remains body(Case c, Level level) {
        Enemy<?> monster = c.find().apply(level);
        assertNotNull(monster, c.map() + ": no monster");
        level.player.position.set(32, level.player.position.y);
        monster.collidedWithActor(true);
        frames(level, SETTLE_FRAMES);
        assertFalse(monster.isRemoved(), "a quiet kill leaves the body");
        return (Remains) monster;
    }

    @Test void everyMonsterThatLeavesABodyIsCoveredHere() throws IOException {
        Pattern implementsRemains = Pattern.compile("class\\s+(\\w+)[^{]*\\bimplements\\b[^{]*\\bRemains\\b");
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root().resolve("core/src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                Matcher m = implementsRemains.matcher(Files.readString(file));
                while (m.find()) found.add(m.group(1));
            }
        }
        assertEquals(new TreeSet<>(CASES.keySet()), found, "every Remains implementer needs a Case in RemainsTest (D101)");
    }

    @Test void theBodysPoseDiffersFromEveryLiveFrame() throws IOException {
        for (Map.Entry<String, Case> entry : CASES.entrySet()) {
            Case c = entry.getValue();
            BufferedImage sheet = javax.imageio.ImageIO.read(root().resolve(c.sheet()).toFile());
            BufferedImage body = javax.imageio.ImageIO.read(root().resolve(c.bodySheet()).toFile());
            boolean[] bodyMask = mask(sheet, c.bodyFrame(), c);
            assertArrayEquals(bodyMask, mask(body, 0, c), entry.getKey() + ": the lasting body keeps the dead frame's shape");
            for (int live : c.liveFrames()) for (boolean mirrored : new boolean[]{false, true}) {
                boolean[] liveMask = mask(sheet, live, c);
                if (mirrored) liveMask = mirror(liveMask, c);
                int differ = 0, total = 0;
                for (int i = 0; i < bodyMask.length; i++) {
                    if (bodyMask[i] || liveMask[i]) total++;
                    if (bodyMask[i] != liveMask[i]) differ++;
                }
                assertTrue(differ >= POSE_DIFFERENCE * total, entry.getKey() + ": the body's pose must differ from live frame " + live
                    + (mirrored ? " mirrored" : "") + " (" + differ + " of " + total + " pixels differ)");
            }
        }
    }

    private static boolean[] mirror(boolean[] mask, Case c) {
        boolean[] out = new boolean[mask.length];
        for (int y = 0; y < c.frameHeight(); y++)
            for (int x = 0; x < c.frameWidth(); x++) out[y * c.frameWidth() + x] = mask[y * c.frameWidth() + c.frameWidth() - 1 - x];
        return out;
    }

    private static boolean[] mask(BufferedImage image, int frame, Case c) {
        boolean[] out = new boolean[c.frameWidth() * c.frameHeight()];
        for (int y = 0; y < c.frameHeight(); y++)
            for (int x = 0; x < c.frameWidth(); x++)
                out[y * c.frameWidth() + x] = (image.getRGB(frame * c.frameWidth() + x, y) >>> 24) >= 128;
        return out;
    }

    @Test void aQuietKillLeavesABodyThatCountsAsDefeatedAndIsHarmless() {
        for (Case c : CASES.values()) {
            Level level = harness.roomOf(c.map(), 0);
            Remains body = body(c, level);
            assertTrue(body.isRemains());
            assertTrue(((Enemy<?>) body).isDefeated(), "a body counts as defeated (D6), so the combat loops skip it as an enemy");
            assertFalse(level.player.isGotHit());
        }
    }

    @Test void theHeroPassingRocksIt() {
        for (Case c : CASES.values()) {
            Level level = harness.roomOf(c.map(), 0);
            Remains body = body(c, level);
            var box = body.remainsInto(new com.badlogic.gdx.math.Rectangle());
            level.player.position.set(box.x - 29 + 2, box.y + box.height - 48);   // the hero's body just touching it
            float max = 0;
            for (int f = 0; f < ROCK_FRAMES; f++) { frames(level, 1); max = Math.max(max, Math.abs((float) c.reaction().applyAsDouble(body))); }
            assertTrue(max > 0, "it reacts to the hero passing");
        }
    }

    @Test void aWeaponCrumblesOrBurnsItAndItIsGone() {
        for (Case c : CASES.values()) {
            Level crumbled = harness.roomOf(c.map(), 0);
            Remains a = body(c, crumbled);
            a.crumble();
            assertTrue(((Enemy<?>) a).isRemoved(), "crumbled: gone at once");
            Level burnt = harness.roomOf(c.map(), 0);
            Remains b = body(c, burnt);
            b.burnUp();
            assertFalse(b.isRemains(), "a burning body is out of reach");
            frames(burnt, BURN_FRAMES);
            assertTrue(((Enemy<?>) b).isRemoved(), "burnt: gone");
        }
    }

    @Test void itOutlivesNeitherARoomResetNorAPassedRoom() {
        for (Case c : CASES.values()) {
            Level level = harness.roomOf(c.map(), 0);
            Remains body = body(c, level);
            level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
            Enemy<?> fresh = c.find().apply(level);
            assertNotNull(fresh, "a death rebuilds the room as authored: the monster is back alive");
            assertNotSame(body, fresh);
            assertFalse(((Remains) fresh).isRemains());
            Level passed = harness.roomOf(c.map(), 0);
            body(c, passed);
            passed.secureVisit();
            passed.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
            assertNull(c.find().apply(passed), "killed in a passed room: neither the monster nor its body");
        }
    }

    @Test void aBurstLeavesNoBody() {
        Level level = harness.roomOf("beetle-test", 0);
        Beetle b = (Beetle) beetle(level);
        b.burst();
        frames(level, 1);
        assertTrue(b.isRemoved());
        assertFalse(b.isRemains());
        assertNull(beetle(level), "a burst or blast leaves nothing");
    }

    @Test void theListIsNotEmpty() { assertFalse(List.copyOf(CASES.keySet()).isEmpty()); }
}
