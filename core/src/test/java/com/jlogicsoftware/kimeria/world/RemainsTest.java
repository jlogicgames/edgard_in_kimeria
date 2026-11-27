package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Bat;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.FallenEnemy;
import com.jlogicsoftware.kimeria.entity.enemy.FlyingBeetle;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.enemy.Remains;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.enemy.Wolf;
import com.jlogicsoftware.kimeria.entity.enemy.Spider;
import com.jlogicsoftware.kimeria.entity.enemy.YellowMob;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The gate of D101 (monster bodies): every monster that leaves a body ({@link Remains}) is listed here and, through the real
 * {@code Level}, keeps the rule. Its body lies where it was killed quietly and counts as defeated; its pose differs from every
 * live frame (the readability guarantee); any actor passing rocks it (D119); the sword and a stomp crumble it, a fireball or a torch burns it (and flies on), a stone passes through;
 * it outlives neither a room reset nor a passed room. A burst or blast leaves nothing. A new {@code Remains} class that is not
 * listed in {@link #CASES} fails {@link #everyMonsterThatLeavesABodyIsCoveredHere()}.
 */
class RemainsTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final int SETTLE_FRAMES = 60, ROCK_FRAMES = 30, BURN_FRAMES = 60;
    /**
     * The body's silhouette must differ from every live frame, drawn either way round, in at least this share of the pixels
     * either covers. The beetle on its back differs from its closest live frames (look and wiggle, mirrored) in 30.5 % of them,
     * measured 2026-11-18; the spider on its back, legs curled up, from its closest (recover) in 48.3 %, measured 2026-11-19 (its
     * hanging frames of D103 are live frames too; its wall frames are 16x32 and upright, never lying on a floor); the snail's
     * empty shell on its back from its closest (draw) in 49.3 %, measured 2026-11-19 (#329); the wolf lying on its side from its
     * closest (bay) in 52.5 %, measured 2026-11-21 (#331); the flying beetle on its back, a wing flat, from its closest (clap) in
     * 76.8 %, measured 2026-11-26 (#330); the bat lying on its side, a wing flat (#478), from its closest (down) in 84.0 %, the
     * red mob's heap and the yellow mob face down (their death clips' last frames) from their closest (idle2, idle3) in 84.5 % each,
     * measured 2026-11-27; a recoloured live frame would differ in 0 %.
     */
    private static final float POSE_DIFFERENCE = .25f;

    /**
     * One monster that leaves a body: its test map, how to find it, where its art is, and how its body reacts to the hero
     * passing (a small body rocks; a big one twitches), read as a size that is 0 at rest. Frames are counted along the rows of
     * a sheet; {@code bodyFrame} is the dead frame in {@code sheet}, {@code lastingFrame} the same drawing as it stays, in
     * {@code bodySheet} (a strip of its own since D101's fade, or the monster's own sheet).
     */
    private record Case(String map, Function<Level, Enemy<?>> find, String sheet, int frameWidth, int frameHeight,
                        int[] liveFrames, int bodyFrame, String bodySheet, int lastingFrame, ToDoubleFunction<Remains> reaction) { }

    private static final String BAT_SHEET = "assets/images/enemy/Bat.png", RED_SHEET = "assets/images/enemy/Mobs.png",
        YELLOW_SHEET = "assets/images/enemy/yellow_mob.png";

    private static final Map<String, Case> CASES = Map.of(
        // D145: killed in the air it falls to the floor and lies on its back, one wing spread flat.
        "FlyingBeetle", new Case("flying-beetle-test", RemainsTest::flyingBeetle, "assets/images/objects/monster-flying-beetle.png", 40, 32,
            new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, 9, "assets/images/objects/monster-flying-beetle-body.png", 0, b -> ((FlyingBeetle) b).rockAngle()),
        "Beetle", new Case("beetle-test", RemainsTest::beetle, "assets/images/objects/monster-beetle.png", 24, 16,
            new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, 9, "assets/images/objects/monster-beetle-body.png", 0, b -> ((Beetle) b).rockAngle()),
        "Spider", new Case("spider-test", RemainsTest::spider, "assets/images/objects/monster-spider.png", 32, 16,
            new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 10}, 8, "assets/images/objects/monster-spider-body.png", 0, b -> ((Spider) b).rockAngle()),
        // The snail's cell 8 is its gob's two 8x8 frames, not a pose of the body: its live frames are 0 to 6.
        "Snail", new Case("snail-test", RemainsTest::snail, "assets/images/objects/monster-snail.png", 24, 16,
            new int[]{0, 1, 2, 3, 4, 5, 6}, 7, "assets/images/objects/monster-snail-body.png", 0, b -> ((Snail) b).rockAngle()),
        // The wolf's body lies on its side along the floor (frame 16, after its 16 live poses, 0 to 15): a big body twitches, 1 px, and never rocks.
        "Wolf", new Case("wolf-test", RemainsTest::wolf, "assets/images/objects/monster-wolf.png", 48, 24,
            new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, 16, "assets/images/objects/monster-wolf-body.png",
            0, b -> ((Wolf) b).twitch()),
        // #478: the bat's body is its own cell (4, 4) of its 5-frame-wide sheet; its live frames are the flaps (rows 0 to 3).
        "Bat", new Case("bat-test", level -> first(level, Bat.class), BAT_SHEET, 16, 16,
            new int[]{0, 1, 2, 3, 5, 10, 11, 12, 13, 15}, 24, BAT_SHEET, 24, b -> ((Bat) b).rockAngle()),
        // The mobs' body is the last frame of the death clip (row 4, column 3); live: run (row 1), the whip (row 2), idle (row 5).
        "RedMob", new Case("redmob-test", level -> first(level, RedMob.class), RED_SHEET, 48, 32,
            new int[]{4, 5, 6, 7, 8, 9, 10, 11, 20, 21, 22, 23}, 19, RED_SHEET, 19, b -> ((RedMob) b).twitch()),
        "YellowMob", new Case("yellowmob-test", level -> first(level, YellowMob.class), YELLOW_SHEET, 48, 32,
            new int[]{4, 5, 6, 7, 20, 21, 22, 23}, 19, YELLOW_SHEET, 19, b -> ((YellowMob) b).twitch()));

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private static <E extends Enemy<?>> E first(Level level, Class<E> type) {
        for (int i = 0; i < level.objects.size(); i++) if (type.isInstance(level.objects.get(i))) return type.cast(level.objects.get(i));
        return null;
    }

    private static Enemy<?> beetle(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Beetle b) return b;
        return null;
    }

    private static Enemy<?> flyingBeetle(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof FlyingBeetle b) return b;
        return null;
    }

    private static Enemy<?> wolf(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Wolf w) return w;
        return null;
    }

    private static Enemy<?> snail(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Snail s) return s;
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

    @Test void everyMonsterThatLeavesABodyIsCoveredHere() throws IOException, ClassNotFoundException {
        // Read from the compiled classes, so a monster that is Remains through a base class (FallenEnemy, #478) counts too.
        Path sources = root().resolve("core/src/main/java");
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String path = sources.relativize(file).toString();
                String name = path.substring(0, path.length() - ".java".length()).replace(file.getFileSystem().getSeparator(), ".");
                addRemains(Class.forName(name, false, getClass().getClassLoader()), found);
            }
        }
        assertEquals(new TreeSet<>(CASES.keySet()), found, "every Remains implementer needs a Case in RemainsTest (D101)");
    }

    /** The concrete {@link Remains} classes among {@code type} and the classes declared inside it. */
    private static void addRemains(Class<?> type, Set<String> found) {
        if (Remains.class.isAssignableFrom(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers()))
            found.add(type.getSimpleName());
        for (Class<?> inner : type.getDeclaredClasses()) addRemains(inner, found);
    }

    @Test void theBodysPoseDiffersFromEveryLiveFrame() throws IOException {
        for (Map.Entry<String, Case> entry : CASES.entrySet()) {
            Case c = entry.getValue();
            BufferedImage sheet = javax.imageio.ImageIO.read(root().resolve(c.sheet()).toFile());
            BufferedImage body = javax.imageio.ImageIO.read(root().resolve(c.bodySheet()).toFile());
            boolean[] bodyMask = mask(sheet, c.bodyFrame(), c);
            assertArrayEquals(bodyMask, mask(body, c.lastingFrame(), c), entry.getKey() + ": the lasting body keeps the dead frame's shape");
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
        int columns = image.getWidth() / c.frameWidth(), left = frame % columns * c.frameWidth(), top = frame / columns * c.frameHeight();
        for (int y = 0; y < c.frameHeight(); y++)
            for (int x = 0; x < c.frameWidth(); x++)
                out[y * c.frameWidth() + x] = (image.getRGB(left + x, top + y) >>> 24) >= 128;
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

    /** D103: a spider killed on a wall or on its thread leaves its body on the floor below, a body like any other. */
    @Test void aSpiderKilledOnAWallOrAThreadLiesOnTheFloorBelow() {
        Level climb = harness.roomOf("spider-climb", 0);
        Spider climber = (Spider) spider(climb);
        climb.player.position.set(430 - 29, 224 - 48);
        for (int f = 0; f < 60 * 4 && !climber.onWall(); f++) frames(climb, 1);
        assertTrue(climber.onWall(), "it climbs towards the hero on the ledge");
        frames(climb, 20);
        climber.collidedWithActor(true);
        frames(climb, SETTLE_FRAMES);
        assertTrue(climber.isRemains());
        assertEquals(320, climber.floorY(), .5f);
        Level drop = harness.roomOf("spider-drop", 0);
        Spider dropper = (Spider) spider(drop);
        drop.player.position.set(480 - 29, 320 - 48);
        for (int f = 0; f < 60 * 4 && dropper.state() != Spider.State.SET; f++) {
            if (f == 0) drop.player.position.set(460 - 29, 320 - 48);
            frames(drop, 1);
        }
        drop.player.position.set(480 - 29, 320 - 48);
        for (int f = 0; f < 60 * 2 && dropper.state() != Spider.State.HANG; f++) frames(drop, 1);
        assertEquals(Spider.State.HANG, dropper.state());
        dropper.collidedWithActor(true);
        frames(drop, SETTLE_FRAMES);
        assertTrue(dropper.isRemains());
        assertEquals(320, dropper.floorY(), .5f);
        dropper.crumble();
        assertTrue(dropper.isRemoved(), "a weapon finishes it as any body");
    }

    // ---- the bat and the mobs (#478, FallenEnemy) ---------------------------------------------------------------------

    @Test void theSettledBodyDrawsTheFrameListedHere() {
        for (String name : new String[]{"Bat", "RedMob", "YellowMob"}) {
            Case c = CASES.get(name);
            Level level = harness.roomOf(c.map(), 0);
            FallenEnemy<?> body = (FallenEnemy<?>) body(c, level);
            int columns = name.equals("Bat") ? 5 : 4;
            var frame = (HeadlessLevelHarness.CellRegion) body.bodyFrame();
            assertTrue(c.sheet().endsWith(frame.path), name + ": drawn from " + frame.path);
            assertEquals(c.bodyFrame() % columns * c.frameWidth(), frame.sheetX, name + ": body cell x");
            assertEquals(c.bodyFrame() / columns * c.frameHeight(), frame.sheetY, name + ": body cell y");
        }
    }

    /** The body's box is what is drawn: every opaque pixel's row and column inside the frame, mirrored with the facing. */
    @Test void theBodysBoxCoversItsDrawingFacingEitherWay() throws IOException {
        for (String name : new String[]{"Bat", "RedMob", "YellowMob"}) {
            Case c = CASES.get(name);
            boolean[] drawn = mask(javax.imageio.ImageIO.read(root().resolve(c.sheet()).toFile()), c.bodyFrame(), c);
            for (boolean right : new boolean[]{true, false}) {
                Level level = harness.roomOf(c.map(), 0);
                Enemy<?> monster = c.find().apply(level);
                monster.facingRight = right;
                Remains body = body(c, level);
                Rectangle box = body.remainsInto(new Rectangle());
                boolean[] seen = right ? drawn : mirror(drawn, c);
                int minX = c.frameWidth(), maxX = 0, minY = c.frameHeight(), maxY = 0;
                for (int y = 0; y < c.frameHeight(); y++)
                    for (int x = 0; x < c.frameWidth(); x++)
                        if (seen[y * c.frameWidth() + x]) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
                // A mob mirrors about its body hitbox's centre (Actor.pivotX), which is not the frame's.
                float shift = right ? 0 : 2 * (monster.hitbox.offsetX + monster.hitbox.width / 2) - c.frameWidth();
                assertEquals(monster.position.x + shift + minX, box.x, .01f, name + " left, facing right " + right);
                assertEquals(monster.position.x + shift + maxX + 1, box.x + box.width, .01f, name + " right, facing right " + right);
                assertEquals(monster.position.y + minY, box.y, .01f, name + " top");
                assertEquals(monster.position.y + maxY + 1, box.y + box.height, .01f, name + " floor line");
                assertEquals(320, box.y + box.height, .5f, name + ": it lies on the floor");
            }
        }
    }

    @Test void aBatKilledInTheAirFallsAndLiesOnTheFloor() {
        Level level = harness.roomOf("bat-test", 0);
        Bat bat = first(level, Bat.class);
        bat.collidedWithActor(true);
        float from = bat.position.y;
        frames(level, 6);
        assertTrue(bat.position.y > from, "it falls");
        assertEquals(0, bat.rockAngle(), "nothing rocks in the air");
        frames(level, SETTLE_FRAMES);
        assertTrue(bat.isRemains());
        assertEquals(320, bat.floorY(), .5f);
    }

    @Test void aBatKilledOverAPitLeavesNothing() {
        Level level = harness.roomOf("bat-test", 0);
        Bat bat = first(level, Bat.class);
        bat.position.x = 870;
        bat.collidedWithActor(true);
        frames(level, 2 * SETTLE_FRAMES);
        assertTrue(bat.isRemoved(), "it fell out of the room");
        assertTrue(bat.isDefeated(), "and stays a kill");
        assertFalse(bat.isRemains());
    }

    @Test void aStompOnAMobsBodyCrumblesItWithTheBounce() {
        for (String name : new String[]{"Bat", "RedMob", "YellowMob"}) {
            Case c = CASES.get(name);
            Level level = harness.roomOf(c.map(), 0);
            Remains body = body(c, level);
            Rectangle box = body.remainsInto(new Rectangle());
            // The hero's feet just above the body, falling onto it.
            level.player.position.set(box.x + box.width / 2 - 23.5f, box.y - 48 - 1);
            level.player.velocity.set(0, 120);
            for (int f = 0; f < ROCK_FRAMES && body.isRemains(); f++) frames(level, 1);
            assertTrue(((Enemy<?>) body).isRemoved(), name + ": the stomp crumbles it");
            assertTrue(level.player.velocity.y < 0, name + ": with the normal bounce");
        }
    }

    @Test void aRedMobKilledMidWhipLeavesNoLiveAttack() {
        Level level = harness.roomOf("redmob-test", 0);
        RedMob mob = first(level, RedMob.class);
        level.player.position.set(mob.attackBounds().x - 23.5f, level.player.position.y);
        for (int f = 0; f < 60 && !mob.isAttackActive(); f++) {
            frames(level, 1);
            if (mob.getState() == RedMob.State.ATTACK) level.player.position.set(32, level.player.position.y);
        }
        assertTrue(mob.isAttackActive(), "the whip is out");
        mob.collidedWithActor(true);
        assertFalse(mob.isAttackActive());
    }

    /** The swing that kills a monster does not crumble its body (D101), also when it goes on to kill a second one. */
    @Test void aSwingThatKillsTwoMonstersLeavesBothBodies() {
        Level level = harness.roomOf("redmob-test", 0);
        RedMob first = first(level, RedMob.class);
        YellowMob second = new YellowMob(harness.assets(), harness.game(), first.position.x, first.position.y, 48, 32, 0, 0);
        second.setPlayer(level.player);
        second.setLevel(level);
        level.objects.add(second);
        // The hero just left of both, facing them: his blade reaches the two standing boxes and, after, the lying ones.
        level.player.position.set(first.position.x + first.hitbox.offsetX - 38, 320 - 48);
        frames(level, 2);
        harness.pressed.add(Input.Keys.K);
        frames(level, SETTLE_FRAMES);
        assertTrue(first.isDefeated() && second.isDefeated(), "one swing kills both");
        assertFalse(first.isRemoved(), "the first body stays");
        assertFalse(second.isRemoved(), "the second body stays");
    }

    @Test void aMobKilledBesideTheHeroTwitchesOnceItLiesNotWhileItFallsOver() {
        Level level = harness.roomOf("redmob-test", 0);
        RedMob mob = first(level, RedMob.class);
        frames(level, 2);
        level.player.position.set(mob.position.x + mob.hitbox.offsetX - 22, 320 - 48);
        mob.collidedWithActor(true);
        boolean twitched = false;
        for (int f = 0; f < ROCK_FRAMES * 2; f++) {
            frames(level, 1);
            if (mob.twitch() != 0) {
                assertTrue(mob.isCurrentAnimationFinished(), "no twitch before the death clip is over");
                twitched = true;
            }
        }
        assertTrue(twitched, "the hero beside it moves the body once it lies");
    }

    @Test void aFlyingBatRocksABodyItPasses() {
        Level level = harness.roomOf("bat-test", 0);
        Bat lying = first(level, Bat.class);
        lying.collidedWithActor(true);
        level.player.position.set(32, level.player.position.y);
        frames(level, SETTLE_FRAMES);
        Bat flying = new Bat(harness.assets(), harness.game(), lying.position.x - 48, lying.position.y, 16, 16, false, 0, 6);
        flying.setPlayer(level.player);
        flying.setLevel(level);
        level.objects.add(flying);
        float max = 0;
        for (int f = 0; f < SETTLE_FRAMES; f++) { frames(level, 1); max = Math.max(max, Math.abs(lying.rockAngle())); }
        assertTrue(max > 0, "any actor passing moves a body (D119)");
    }

    @Test void theListIsNotEmpty() { assertFalse(List.copyOf(CASES.keySet()).isEmpty()); }
}
