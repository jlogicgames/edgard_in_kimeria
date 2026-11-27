package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.Collidable;
import com.jlogicsoftware.kimeria.entity.items.Actionable;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.Decor;
import com.jlogicsoftware.kimeria.entity.objects.FalseHoneyFungus;
import com.jlogicsoftware.kimeria.entity.objects.FungusTrap;
import com.jlogicsoftware.kimeria.entity.objects.HoneyFungus;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one-kind {@code Decor} object (#239 step 4, docs/ART_DIRECTION.md section 4): the Tiled type, the brush-past
 * animation, the placement of the bushes in the shipped maps and the absence of any collision.
 *
 * <p>Y grows downward: a bush stands on a floor when {@code y + height} equals the top (y) of a solid block under it.
 */
class DecorTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    /** Bushes authored per shipped map (docs/ART_DIRECTION.md, placement table). */
    private static final Map<String, Integer> BUSHES = Map.of("forest-0", 12, "forest-1", 4, "forest-2", 11);
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Decor bush() { return new Decor(harness.assets(), "bush", 100, 296, 32, 24, false); }

    private static Rectangle hero(float x) { return new Rectangle(x, 270, 11, 22 + 26); }

    private static TiledObject object(String kind) {
        TiledObject o = new TiledObject();
        o.id = 7; o.name = "Bush"; o.type = "Decor"; o.x = 100; o.y = 296; o.width = 32; o.height = 24;
        if (kind != null) o.properties.put("kind", kind);
        return o;
    }

    @Test void theBushKindLoadsAndAnUnknownOrMissingKindFailsTheLoad() {
        assertEquals(java.util.Set.of("bush", "lookalike", "trunk", "snag", "crown"), Decor.kindNames());
        assertEquals(Decor.FRAMES, Decor.fromMap(harness.assets(), object("Bush"), "x").frameCount());
        var unknown = assertThrows(IllegalArgumentException.class, () -> Decor.fromMap(harness.assets(), object("tree"), "'Bush' [7]"));
        assertTrue(unknown.getMessage().contains("Decor 'Bush' [7]") && unknown.getMessage().contains("tree"), unknown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Decor.fromMap(harness.assets(), object(null), "x"));
        TiledObject flat = object("bush"); flat.width = 0;
        assertThrows(IllegalArgumentException.class, () -> Decor.fromMap(harness.assets(), flat, "x"));
    }

    /** D119: a bush sways for every actor moving through it, not only the hero (here a crawling beetle, hero far away). */
    @Test void aMonsterWalkingThroughABushMakesItSway() {
        harness.playSounds = false;
        Level level = harness.roomOf("beetle-test", 0);
        Decor bush = new Decor(harness.assets(), "bush", 280, 296, 32, 24, false);
        level.decor.add(bush);
        level.player.position.set(32, level.player.position.y);
        boolean swayed = false;
        for (int f = 0; f < 60 * 8 && !swayed; f++) { level.update(STEP); harness.pressed.clear(); swayed = bush.playing(); }
        assertTrue(swayed, "the beetle patrolling through the bush brushed it");
    }

    @Test void aWalkingHeroStartsTheBrushOnceAndItRestartsOnlyWhenFinished() {
        Decor d = bush();
        assertEquals(0, d.frameIndex());
        d.notice(hero(110), 100);
        assertTrue(d.playing());
        assertEquals(1, d.frameIndex());
        // Noticing again while playing must not restart: the animation reaches its end on schedule (3 frames at 10 fps).
        List<Integer> seen = new ArrayList<>(List.of(1));
        int frames = 0;
        while (d.playing() && frames < 60) {
            d.notice(hero(110), 100);
            d.update(STEP);
            frames++;
            if (d.playing() && seen.get(seen.size() - 1) != d.frameIndex()) seen.add(d.frameIndex());
        }
        assertEquals(List.of(1, 2, 3), seen, "brush1, brush2, settle");
        assertEquals(Math.round(3 / Decor.FPS / STEP), frames, 1, "0.3 s in total, not stretched by the repeated notices");
        assertEquals(0, d.frameIndex(), "back to the rest frame");
        // The hero is still walking through it, so it starts again, but only now that it has finished.
        d.notice(hero(110), 100);
        assertTrue(d.playing());
    }

    @Test void aStandingHeroOrOneBesideTheVisibleBodyStartsNothing() {
        // The bush rectangle is x 100..132, y 296..320; its drawn body is x 103..130, y 302..320 (Decor.BODY_INSET_*).
        Decor d = bush();
        d.notice(hero(110), 0);
        assertFalse(d.playing(), "standing inside the bush");
        d.notice(hero(100 + 32 - Decor.BODY_INSET_RIGHT), 100);
        assertFalse(d.playing(), "touching the right edge of the body, still beside it");
        d.notice(hero(100 + 32), 100);
        assertFalse(d.playing(), "in the empty right margin of the frame");
        d.notice(hero(100 + Decor.BODY_INSET_LEFT - 11), 100);
        assertFalse(d.playing(), "touching the left edge of the body");
        d.notice(hero(100 - 11 - 6), 100);
        assertFalse(d.playing(), "6 px to the left, where the old widened zone started the shake");
        d.notice(new Rectangle(110, 296 + 24, 11, 22), 100);
        assertFalse(d.playing(), "below the floor line");
        d.notice(new Rectangle(110, 296 + Decor.BODY_INSET_TOP - 22, 11, 22), 100);
        assertFalse(d.playing(), "above the top of the body");
    }

    @Test void aHeroOverlappingTheVisibleBodyStartsTheBrushHeadingDecidesTheLean() {
        Decor d = bush();
        d.notice(hero(100 + 32 - Decor.BODY_INSET_RIGHT - 1), 100);
        assertTrue(d.playing(), "one pixel into the body from the right");
        d = bush();
        d.notice(hero(100 + Decor.BODY_INSET_LEFT - 11 + 1), -100);
        assertTrue(d.playing(), "one pixel into the body from the left");
        d = bush();
        d.notice(new Rectangle(110, 296 + Decor.BODY_INSET_TOP - 22 + 1, 11, 22), 100);
        assertTrue(d.playing(), "the hero's feet one pixel into the body from above");
    }

    @Test void pauseFreezesTheBrush() {
        Decor d = bush();
        d.notice(hero(110), 100);
        d.update(STEP);
        int frame = d.frameIndex();
        // A paused game never calls update, so nothing may advance on its own.
        for (int i = 0; i < 100; i++) d.notice(hero(110), 100);
        assertEquals(frame, d.frameIndex());
        assertTrue(d.playing());
    }

    @Test void aHeroWalkingPastABushInTheRealLevelStartsItsBrush() {
        Level level = harness.roomOf("forest-0", 0);
        Decor first = level.decor.stream().min((a, b) -> Float.compare(a.getX(), b.getX())).orElseThrow();
        level.player.position.x = first.getX() - 60;
        level.player.position.y = first.getY() + first.getHeight() - level.player.getHeight();
        int starts = 0;
        boolean was = false;
        harness.held.add(Input.Keys.D);
        for (int i = 0; i < 360; i++) {
            level.update(STEP);
            if (first.playing() && !was) starts++;
            was = first.playing();
        }
        harness.held.clear();
        assertTrue(level.player.getX() > first.getX() + first.getWidth(), "the hero walked right through the bush");
        assertTrue(starts >= 1, "the brush must start while the hero passes");
        assertTrue(starts <= 2, "a pass of about a second restarts at most once, only after finishing");
    }

    @Test void everyShippedBushStandsOnAFloorHasNoCollisionAndIsNotAnObject() {
        assertFalse(Collidable.class.isAssignableFrom(Decor.class) || Actionable.class.isAssignableFrom(Decor.class),
            "a Decor object is neither collidable nor actionable");
        BUSHES.forEach((map, expected) -> {
            int found = 0;
            for (int room = 0; ; room++) {
                Level level = harness.roomOf(map, room);
                for (Decor d : level.decor) {
                    if (!d.brushes()) continue; // the static look-alikes (#299, test below) and the tree trunks (#349, TreeClimbTest) are not bushes
                    found++;
                    Rectangle rect = new Rectangle(d.getX(), d.getY(), d.getWidth(), d.getHeight());
                    String label = map + " bush at " + rect;
                    boolean onFloor = false;
                    for (CollisionBlock block : level.collisionBlocks) {
                        Rectangle b = new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight());
                        assertFalse(block.isActive() && !block.isQuickSand() && rect.overlaps(b),
                            label + " overlaps a solid block " + b + ": a bush has no collision and must not sink into one");
                        if (!block.isQuickSand() && rect.x < b.x + b.width && b.x < rect.x + rect.width && b.y == rect.y + rect.height) onFloor = true;
                    }
                    assertTrue(onFloor, label + ": y + height must equal the top of a solid block (floor line), world Y grows downward");
                    assertTrue(level.currentRoomBounds().contains(rect), label + " must lie inside its room");
                    assertEquals(24f, d.getHeight(), label);
                }
                for (var o : level.objects) {
                    assertFalse(o instanceof Decor, "decor is not in the object list that is drawn after the platforms");
                }
                if (!level.hasNextRoom()) break;
            }
            assertEquals(expected, found, map + ": bushes authored");
        });
    }

    @Test void aRoomRebuildRecreatesTheBushesAtTheRestFrame() {
        Level level = harness.roomOf("forest-0", 0);
        int count = level.decor.size();
        assertTrue(count > 0);
        level.decor.get(0).notice(new Rectangle(level.decor.get(0).getX(), level.decor.get(0).getY(), 11, 22), 100);
        assertTrue(level.decor.get(0).playing());
        level.resetCurrentRoom(new com.jlogicsoftware.kimeria.entity.player.Player(harness.assets(), harness.game(), 0, 0));
        assertEquals(count, level.decor.size(), "cleared and respawned, not accumulated");
        assertFalse(level.decor.get(0).playing());
        assertEquals(0, level.decor.get(0).frameIndex());
    }

    // ---- Static look-alike mushrooms (#299): the "lookalike" kind, a pale cap-and-stem tuft that never reacts ----

    /** Static look-alikes authored per shipped map: one, in forest-2 only (docs/ORIGINAL_TRAPS.md, "Static look-alikes"). */
    private static final Map<String, Integer> LOOKALIKES = Map.of("forest-0", 0, "forest-1", 0, "forest-2", 1);
    private static final int MAX_STILL_PER_LEVEL = 2;
    private static final float LOOKALIKE_WIDTH = 24f, LOOKALIKE_HEIGHT = 16f;

    private Decor lookalike() { return new Decor(harness.assets(), "lookalike", 100, 304, LOOKALIKE_WIDTH, LOOKALIKE_HEIGHT, false); }

    @Test void theLookAlikeIsOneStillFrameThatNeverMoves() {
        Decor d = lookalike();
        assertTrue(d.isStill());
        assertFalse(bush().isStill());
        assertFalse(d.brushes(), "a still kind never brushes");
        assertTrue(bush().brushes());
        assertEquals(1, d.frameCount());
        assertEquals(0, d.frameIndex());
        // The hero walks right through it, any number of times: no state change at all, and updates change nothing either.
        for (int i = 0; i < 120; i++) {
            d.notice(new Rectangle(104, 282, 11, 48), 100);
            d.notice(new Rectangle(104, 282, 11, 48), -100);
            d.update(STEP);
            assertFalse(d.playing(), "motion is the cue that an active mushroom is alive; the static one has none");
            assertEquals(0, d.frameIndex());
        }
        assertTrue(Decor.isStillKind("lookalike") && Decor.isStillKind("Lookalike"));
        assertFalse(Decor.isStillKind("bush") || Decor.isStillKind("tree"));
    }

    @Test void theShippedStaticLookAlikesComeAfterBothActiveMushroomsAndSitOnNoZoneOrFloorGap() {
        LOOKALIKES.forEach((map, expected) -> {
            int found = 0;
            for (int room = 0; ; room++) {
                Level level = harness.roomOf(map, room);
                for (Decor d : level.decor) {
                    if (!d.isStill()) continue;
                    found++;
                    Rectangle rect = new Rectangle(d.getX(), d.getY(), d.getWidth(), d.getHeight());
                    String label = map + " look-alike mushroom at " + rect;
                    assertEquals(LOOKALIKE_HEIGHT, d.getHeight(), label);
                    boolean onFloor = false;
                    for (CollisionBlock block : level.collisionBlocks) {
                        Rectangle b = new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight());
                        if (!block.isQuickSand() && rect.x < b.x + b.width && b.x < rect.x + rect.width && b.y == rect.y + rect.height) onFloor = true;
                    }
                    assertTrue(onFloor, label + ": the tuft stands on the floor line (world Y grows downward)");
                    float lastActiveRight = 0f;
                    for (var o : level.objects) {
                        if (!(o instanceof FungusTrap f)) continue;
                        assertFalse(rect.overlaps(f.triggerZone()), label + " lies on the zone of " + f.getClass().getSimpleName() + " at " + f.triggerZone());
                        assertFalse(rect.overlaps(new Rectangle(f.getX(), f.getY(), f.getWidth(), f.getHeight())), label + " covers an active mushroom");
                        lastActiveRight = Math.max(lastActiveRight, f.triggerZone().x + f.triggerZone().width);
                    }
                    // Only after the player has met an active mushroom in this map: forest-2 has the lethal one at x 1672.
                    assertTrue(map.equals("forest-2") && level.objects.stream().anyMatch(o -> o instanceof FalseHoneyFungus),
                        label + ": the first look-alike is in forest-2, in the room that holds the lethal mushroom");
                    assertTrue(d.getX() >= lastActiveRight, label + " lies after the active mushroom's zone, not before it");
                }
                if (!level.hasNextRoom()) break;
            }
            assertEquals(expected, found, map + ": static look-alikes authored");
            assertTrue(found <= MAX_STILL_PER_LEVEL, map + ": one or two per level");
        });
    }

    @Test void aStaticLookAlikeIsNeverAnActiveMushroomObject() {
        assertFalse(FungusTrap.class.isAssignableFrom(Decor.class) || HoneyFungus.class.isAssignableFrom(Decor.class),
            "the static look-alike is a picture, not an object with logic");
    }

    /**
     * #555, D130 (playtest 2 F17: "pale, see-through trees and bushes"): every frame of the tree and bush sheets is solid. Its alpha is
     * 0 or 255, nothing in between, and no transparent pixel is enclosed by the drawing (each one reaches the frame's edge through
     * transparent 4-neighbours), so the background never shows through a trunk, a crown or a bush.
     */
    @Test void theTreesAndBushesAreSolidWithNoTranslucentPixelAndNoHole() throws Exception {
        SpriteSheets.Sheet[] sheets = {SpriteSheets.DECOR_TRUNK, SpriteSheets.DECOR_SNAG, SpriteSheets.DECOR_CROWN, SpriteSheets.DECOR_BUSH};
        int[] frames = {1, 1, 1, Decor.FRAMES};
        for (int s = 0; s < sheets.length; s++) {
            java.awt.image.BufferedImage png;
            try (var input = getClass().getClassLoader().getResourceAsStream(sheets[s].path())) {
                assertNotNull(input, sheets[s].path());
                png = javax.imageio.ImageIO.read(input);
            }
            int w = sheets[s].width(), h = sheets[s].height();
            for (int f = 0; f < frames[s]; f++) {
                boolean[][] outside = new boolean[w][h];
                java.util.ArrayDeque<int[]> open = new java.util.ArrayDeque<>();
                for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
                    int alpha = png.getRGB(f * w + x, y) >>> 24;
                    assertTrue(alpha == 0 || alpha == 255, sheets[s].path() + " frame " + f + " (" + x + "," + y + ") alpha " + alpha);
                    if (alpha == 0 && (x == 0 || y == 0 || x == w - 1 || y == h - 1)) { outside[x][y] = true; open.add(new int[] {x, y}); }
                }
                while (!open.isEmpty()) {
                    int[] p = open.poll();
                    int[][] next = {{p[0] + 1, p[1]}, {p[0] - 1, p[1]}, {p[0], p[1] + 1}, {p[0], p[1] - 1}};
                    for (int[] n : next) {
                        if (n[0] < 0 || n[1] < 0 || n[0] >= w || n[1] >= h || outside[n[0]][n[1]]) continue;
                        if ((png.getRGB(f * w + n[0], n[1]) >>> 24) != 0) continue;
                        outside[n[0]][n[1]] = true;
                        open.add(n);
                    }
                }
                for (int x = 0; x < w; x++) for (int y = 0; y < h; y++)
                    assertFalse((png.getRGB(f * w + x, y) >>> 24) == 0 && !outside[x][y],
                        sheets[s].path() + " frame " + f + ": a hole at (" + x + "," + y + ") shows the background through the drawing");
            }
        }
    }
}
