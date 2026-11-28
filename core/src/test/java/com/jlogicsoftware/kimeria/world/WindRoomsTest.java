package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.FloatArray;
import com.jlogicsoftware.kimeria.effects.CrownGlintFx;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.effects.Shaders;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.items.Carryable;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.objects.Decor;
import com.jlogicsoftware.kimeria.entity.items.Trigger;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.NoopBatch;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jlogicsoftware.kimeria.effects.CrownGlintFx.CROWN;
import static com.jlogicsoftware.kimeria.effects.CrownGlintFx.DEPTHS;
import static com.jlogicsoftware.kimeria.effects.CrownGlintFx.GROVE;
import static com.jlogicsoftware.kimeria.effects.CrownGlintFx.MID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The wind's glints in the shipped forest rooms (#593, D163): where they may start, how many live, and what a still camera sees.
 */
class WindRoomsTest {
    private static final float STEP = HeadlessLevelHarness.STEP, VIEW_W = 640, VIEW_H = 360;
    private static final String[] MAPS = {"forest-0", "forest-1", "forest-2"};
    private static final int GUSTS = 4, SEEDS = 10, CAMERAS = 3;
    private static final float COLUMN_MARGIN = 24, PROTECTED_MARGIN = 16;
    private static final float[] DRIFT = {0, 32, 40};
    /**
     * The most of a room's due glints a full pool may skip, over {@value #SEEDS} seeds, two gusts at each of three cameras, with the
     * card's caps. Measured 2026-11-28 with the leaf patches and caps 64, 24 and 16: no room skips any (the first build, with caps
     * 8, 5 and 3, skipped 13.7 % and had a ceiling of 16 %). Ceiling 2 % (D163).
     */
    private static final float MOST_SKIPPED = .02f;
    /**
     * Rooms where no camera lights a crown or mid glint (2026-11-28): they have no tree (an accepted gap of D162), and the mid spots
     * their camera shows lie off screen or in trap columns. The grove still shows their gusts.
     */
    private static final Set<String> NO_NEAR_GLINT = Set.of("forest-1-escalators", "forest-1-climb");
    /**
     * The fewest crown and mid glints any one gust lights at the first, middle or last camera of every other room, and the fewest of
     * all three depths at any view of any room (the first {@value #GUSTS} gusts of each room's own seed, 2026-11-28, leaf patches).
     */
    private static final int NEAR_PER_GUST = 11, ANY_PER_GUST = 10;
    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Player player() { return new Player(harness.assets(), harness.game(), 0, 0); }

    /** Every room of the three maps in play order, windy, with the glints started by the level itself. */
    private interface RoomCheck { void check(Level level, int room); }

    private void everyRoom(RoomCheck check) {
        for (String map : MAPS) {
            Level level = harness.roomOf(map, 0);
            assertTrue(level.map.propBool("wind", false), map + " carries the map property wind");
            for (int room = 0; ; room++) {
                check.check(level, room);
                if (!level.hasNextRoom()) break;
                level.enterNextRoom(player());
            }
        }
    }

    private static float cameraX(Rectangle room, int camera) {
        float spare = room.width - VIEW_W;
        return spare <= 0 ? room.x + spare / 2 : room.x + spare * camera / (CAMERAS - 1);
    }

    private static Rectangle view(Level level, int camera) {
        Rectangle room = level.currentRoomBounds();
        return new Rectangle(cameraX(room, camera), ParallaxRenderer.originY(room, VIEW_H), VIEW_W, VIEW_H);
    }

    /** What {@code Level} does each frame for the wind, without the rest of the world. */
    private static void frame(Level level, Rectangle view) {
        Wind wind = level.wind();
        CrownGlintFx glints = level.glints();
        wind.update(STEP);
        for (int d = MID; d < DEPTHS; d++) {
            var layer = level.map.sceneryLayers.get(WindSpots.layerIndex(level.map, d));
            glints.setLayerBase(d, ParallaxRenderer.layerBaseX(layer, view.x),
                ParallaxRenderer.layerBaseY(layer, view.y, ParallaxRenderer.originY(level.currentRoomBounds(), view.height)));
        }
        glints.update(STEP, wind, view);
    }

    private static List<TiledObject> objectsOfRoom(Level level, int room, Set<String> types) {
        List<TiledObject> found = new ArrayList<>();
        for (List<TiledObject> group : level.map.objectGroups.values())
            for (TiledObject o : group) if (types.contains(o.type) && level.roomLayout.owner(o) == room) found.add(o);
        return found;
    }

    private static void assertAllowed(Level level, int room, Rectangle view, int depth, float x, float y) {
        CrownGlintFx glints = level.glints();
        String where = level.currentRoomName() + " depth " + depth + " at " + x + "," + y;
        float left = glints.layerBaseX(depth) + x, top = glints.layerBaseY(depth) + y;
        int spot = -1;
        for (int i = 0; i < glints.spotCount(depth); i++) if (glints.spotX(depth, i) == x && glints.spotY(depth, i) == y) spot = i;
        assertTrue(spot >= 0, where + " is no listed spot");
        float width = glints.spotWidth(depth, spot), height = glints.spotHeight(depth, spot);
        assertTrue(view.overlaps(new Rectangle(left, top, width, height)), where + " off screen");
        Rectangle bounds = level.currentRoomBounds();
        assertTrue(left >= bounds.x && left <= bounds.x + bounds.width, where + " outside the room");
        for (TiledObject o : objectsOfRoom(level, room, Level.LEAF_AVOIDED))
            assertTrue(left + width < o.x - COLUMN_MARGIN - DRIFT[depth] || left > o.x + o.width + COLUMN_MARGIN + DRIFT[depth],
                where + " in the column of " + o.type + " " + o.id);
        if (depth != CROWN) return;
        for (TiledObject o : objectsOfRoom(level, room, Level.GLINT_PROTECTED)) {
            Rectangle near = new Rectangle(o.x - PROTECTED_MARGIN, o.y - PROTECTED_MARGIN,
                o.width + 2 * PROTECTED_MARGIN, o.height + 2 * PROTECTED_MARGIN);
            assertFalse(near.overlaps(new Rectangle(left, top, width, height)), where + " near " + o.type + " " + o.id);
        }
    }

    /**
     * Every glint of every room at its first, middle and last camera: on a listed spot, out of trap columns and protected boxes,
     * under the live caps, none in the calm. Prints, per room, the eligible glints per gust that the report and the card cite.
     */
    @Test void everyShippedRoomLightsOnlyAllowedSpotsUnderTheCapsAndNothingInTheCalm() {
        int[] lit = new int[DEPTHS];
        StringBuilder table = new StringBuilder("wind glints per gust (crown/mid/grove) at the first, middle and last camera\n");
        List<String> dark = new ArrayList<>();
        int[] fewest = {Integer.MAX_VALUE, Integer.MAX_VALUE}, most = new int[DEPTHS];
        String[] fewestAt = {"", ""};
        everyRoom((level, room) -> {
            StringBuilder line = new StringBuilder(level.currentRoomName()).append(" spots ")
                .append(level.glints().spotCount(CROWN)).append('/').append(level.glints().spotCount(MID)).append('/').append(level.glints().spotCount(GROVE)).append(" |");
            for (int camera = 0; camera < CAMERAS; camera++) {
                level.resetCurrentRoom(player());
                Rectangle view = view(level, camera);
                int[] before = new int[DEPTHS];
                int[] nearByGust = new int[GUSTS], anyByGust = new int[GUSTS];
                while (level.wind().gust() < GUSTS) {
                    frame(level, view);
                    int total = 0;
                    for (int d = 0; d < DEPTHS; d++) {
                        KimeriaParticleSystem p = level.glints().particles(d);
                        assertTrue(p.count() <= CrownGlintFx.capacity(d));
                        most[d] = Math.max(most[d], p.count());
                        total += p.count();
                        for (int i = 0; i < p.count(); i++) {
                            if (p.life(i) != p.maxLife(i)) continue;
                            assertAllowed(level, room, view, d, p.x(i), p.y(i));
                            before[d]++;
                            int gust = level.wind().gust();
                            if (gust >= GUSTS) continue;
                            anyByGust[gust]++;
                            if (d != GROVE) nearByGust[gust]++;
                        }
                        if (!level.wind().blowing()) assertEquals(0, p.count(), level.currentRoomName() + " glints in the calm");
                    }
                    assertTrue(total <= CrownGlintFx.TOTAL_CAPACITY);
                }
                for (int d = 0; d < DEPTHS; d++) lit[d] += before[d];
                line.append(String.format(java.util.Locale.ROOT, " %.1f/%.1f/%.1f", before[CROWN] / (float) GUSTS, before[MID] / (float) GUSTS, before[GROVE] / (float) GUSTS));
                if (before[CROWN] + before[MID] == 0) dark.add(level.currentRoomName() + " camera " + camera);
                String where = level.currentRoomName() + " camera " + camera;
                for (int gust = 0; gust < GUSTS; gust++) {
                    assertTrue(anyByGust[gust] >= ANY_PER_GUST, where + " gust " + gust + " lights " + anyByGust[gust]);
                    if (anyByGust[gust] < fewest[1]) { fewest[1] = anyByGust[gust]; fewestAt[1] = where; }
                    if (NO_NEAR_GLINT.contains(level.currentRoomName())) continue;
                    assertTrue(nearByGust[gust] >= NEAR_PER_GUST, where + " gust " + gust + " lights " + nearByGust[gust] + " near");
                    if (nearByGust[gust] < fewest[0]) { fewest[0] = nearByGust[gust]; fewestAt[0] = where; }
                }
            }
            table.append(line).append('\n');
        });
        System.out.println(table);
        System.out.println("most alive at once (crown/mid/grove): " + java.util.Arrays.toString(most));
        System.out.println("fewest per gust: near " + fewest[0] + " at " + fewestAt[0] + ", any " + fewest[1] + " at " + fewestAt[1]);
        System.out.println("views with no crown or mid glint in " + GUSTS + " gusts: " + dark);
        for (int d = 0; d < DEPTHS; d++) assertTrue(lit[d] > 0, "depth " + d + " never lit in any room");
    }

    @Test void aFullPoolSkipsFewGlintsInAnyRoomOverManySeeds() {
        float[] worst = {0};
        String[] worstRoom = {""};
        everyRoom((level, room) -> {
            WindSpots spots = WindSpots.load();
            FloatArray columns = new FloatArray();
            for (TiledObject o : objectsOfRoom(level, room, Level.LEAF_AVOIDED)) columns.add(o.x - COLUMN_MARGIN, o.x + o.width + COLUMN_MARGIN);
            FloatArray boxes = new FloatArray();
            for (TiledObject o : objectsOfRoom(level, room, Level.GLINT_PROTECTED)) boxes.add(o.x, o.y, o.width, o.height);
            Rectangle bounds = level.currentRoomBounds();
            int spawned = 0, skipped = 0;
            for (int seed = 0; seed < SEEDS; seed++) {
                for (int camera = 0; camera < CAMERAS; camera++) {
                    level.wind().reset(seed, bounds.x, bounds.x + bounds.width);
                    level.glints().start(seed, spots.forRoom(level.map, level.roomLayout, room, VIEW_W), columns.toArray(), boxes);
                    Rectangle view = view(level, camera);
                    while (level.wind().gust() < 2) frame(level, view);
                    spawned += level.glints().spawned();
                    skipped += level.glints().skipped();
                }
            }
            float rate = skipped / (float) Math.max(1, spawned + skipped);
            if (rate >= worst[0]) { worst[0] = rate; worstRoom[0] = level.currentRoomName() + " " + skipped + " of " + (spawned + skipped); }
        });
        System.out.println("worst skip rate " + worst[0] + " in " + worstRoom[0]);
        assertTrue(worst[0] <= MOST_SKIPPED, "skip rate " + worst[0] + " in " + worstRoom[0]);
    }

    @Test void theLevelsOwnUpdateLightsGlintsFromTheCamerasView() {
        Rectangle view = new Rectangle(0, 0, VIEW_W, VIEW_H);
        Level level = harness.room("forest-1", "forest-1-lever", () -> view);
        view.set(view(level, 0));
        int[] peak = new int[DEPTHS];
        for (int f = 0; f < 60 * 30; f++) {
            level.update(STEP);
            for (int d = 0; d < DEPTHS; d++) peak[d] = Math.max(peak[d], level.glints().particles(d).count());
        }
        assertTrue(level.glints().spawned() > 0);
        assertTrue(peak[MID] > 0 && peak[GROVE] > 0, "both layers: " + peak[MID] + " " + peak[GROVE]);
    }

    /** Every enemy, pickup and lever a room spawns stands on a map object of a protected type. */
    @Test void everyLeverPickupAndMonsterOfTheForestIsProtected() {
        everyRoom((level, room) -> {
            for (var object : level.objects) {
                if (!(object instanceof Enemy<?> || object instanceof Collectable || object instanceof FireballRefill
                    || object instanceof Carryable || object instanceof Trigger)) continue;
                String name = object.getClass().getSimpleName();
                String type = object instanceof Carryable ? "Stone" : name;
                assertTrue(Level.GLINT_PROTECTED.contains(type), name + " is not in Level.GLINT_PROTECTED");
            }
        });
        for (String type : new String[]{"Bat", "Beetle", "FlyingBeetle", "MosquitoSwarm", "Spider", "Snail", "Wolf", "RedMob", "YellowMob", "Boss"})
            assertTrue(Level.GLINT_PROTECTED.contains(type), type);
        java.io.File enemies = new java.io.File("src/main/java/com/jlogicsoftware/kimeria/entity/enemy");
        assertTrue(enemies.isDirectory());
        for (java.io.File file : enemies.listFiles()) {
            String name = file.getName().replace(".java", "");
            try {
                Class<?> type = Class.forName(Enemy.class.getPackageName() + "." + name);
                if (Enemy.class.isAssignableFrom(type) && !java.lang.reflect.Modifier.isAbstract(type.getModifiers()))
                    assertTrue(Level.GLINT_PROTECTED.contains(name), name + " is not in Level.GLINT_PROTECTED");
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** The "x,y" of every pixel of a patch whose top-left is ({@code x}, {@code y}). */
    private static Set<String> pixels(float x, float y, float[] runs) {
        Set<String> result = new HashSet<>();
        for (int k = 0; k + 2 < runs.length; k += KimeriaParticleSystem.GLINT_RUN)
            for (int i = 0; i < runs[k + 2]; i++) result.add((int) (x + runs[k] + i) + "," + (int) (y + runs[k + 1]));
        return result;
    }

    /** The crown spots of a room follow {@code Decor}'s own draw, mirrored or not: each block lands on a block of the baked sheet. */
    @Test void crownSpotsFollowTheDecorsDrawAndItsMirroring() {
        Level level = harness.roomOf("forest-0", 0);
        WindSpots spots = WindSpots.load();
        int sheets = 0;
        for (WindSpots.Sheet sheet : spots.sheets()) {
            String kind = sheet.name().replace("decor-", "");
            if (sheet.depth() != CROWN || !Decor.kindNames().contains(kind)) continue;
            sheets++;
            TiledObject crown = level.map.layer("SpawnPoints").stream()
                .filter(o -> "Decor".equals(o.type) && o.prop("kind", "").startsWith("crown")).findFirst().orElseThrow();
            crown.properties.put("kind", kind);
            crown.width = sheet.width();
            crown.height = sheet.height();
            int room = level.roomLayout.owner(crown);
            Pixmap image = new Pixmap(Gdx.files.internal("images/objects/" + sheet.name() + ".png"));
            Set<String> listed = new HashSet<>();
            for (WindSpots.Patch patch : sheet.patches()) listed.addAll(pixels(patch.x(), patch.y(), patch.runs()));
            for (String mirror : new String[]{"false", "true"}) {
                crown.properties.put("mirror", mirror);
                CrownGlintFx.Spots placed = spots.forRoom(level.map, level.roomLayout, room, VIEW_W)[CROWN];
                NoopBatch batch = new NoopBatch();
                batch.record(1);
                Decor.fromMap(harness.assets(), crown, "test").render(batch);
                float drawX = batch.drawnX(0), drawY = batch.drawnY(0), drawWidth = batch.drawnWidth(0);
                assertEquals(Boolean.parseBoolean(mirror) ? -sheet.width() : sheet.width(), drawWidth);
                Set<String> hit = new HashSet<>();
                for (int i = 0; i < placed.size(); i++) {
                    for (String pixel : pixels(placed.x(i), placed.y(i), placed.runs(i))) {
                        String[] xy = pixel.split(",");
                        int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
                        // the sheet column under world column x: counted from the right edge when the sprite is mirrored
                        int column = (int) (drawWidth > 0 ? x - drawX : drawX - x - 1), row = (int) (y - drawY);
                        if (!listed.contains(column + "," + row)) continue; // another crown of the room
                        hit.add(column + "," + row);
                        assertEquals(0xff, image.getPixel(column, row) & 0xff, "opaque leaf");
                    }
                }
                assertEquals(listed, hit, kind + " mirror " + mirror);
            }
            image.dispose();
        }
        assertTrue(sheets > 0, "no crown sheet has spots");
    }

    /** crown grids' leaf keys m, l and n (tools/wind_spots.py): the only colours a crown glint may stand on. */
    private static final Set<Integer> CROWN_LEAVES = Set.of(0x233223, 0x273627, 0x2f301e);

    /**
     * On the screen, in every shipped room: each pixel of each crown patch shows a leaf of a crown, as the topmost opaque pixel
     * of the decor drawn after the play tiles, mirrored crowns included. So a glint, drawn straight after that decor, sits on a leaf.
     */
    @Test void everyShippedCrownSpotShowsALeafOfACrownMirroredOrNot() {
        java.util.Map<String, Pixmap> images = new java.util.HashMap<>();
        Set<String> checked = new HashSet<>();
        int[] spots = {0};
        everyRoom((level, room) -> {
            List<Decor> front = level.decor.stream().filter(d -> !d.behindTiles()).toList();
            float[][] drawn = new float[front.size()][];
            NoopBatch batch = new NoopBatch();
            for (int i = 0; i < front.size(); i++) {
                batch.record(1);
                front.get(i).render(batch);
                drawn[i] = new float[]{batch.drawnX(0), batch.drawnY(0), batch.drawnWidth(0)};
            }
            CrownGlintFx glints = level.glints();
            for (int spot = 0; spot < glints.spotCount(CROWN); spot++) {
                spots[0]++;
                for (String pixel : pixels(glints.spotX(CROWN, spot), glints.spotY(CROWN, spot), glints.spotRuns(CROWN, spot))) {
                    String[] xy = pixel.split(",");
                    float x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
                    String where = level.currentRoomName() + " crown spot pixel " + x + "," + y;
                    String top = null;
                    int colour = 0;
                    for (int i = front.size() - 1; i >= 0 && top == null; i--) {
                        Decor decor = front.get(i);
                        Pixmap image = images.computeIfAbsent(decor.kind(),
                            kind -> new Pixmap(Gdx.files.internal("images/objects/decor-" + kind + ".png")));
                        float left = drawn[i][0], width = drawn[i][2];
                        // the frame's column under world column x: counted from the right edge when the sprite is mirrored
                        int column = (int) Math.floor(width > 0 ? x - left : left - x - 1), row = (int) Math.floor(y - drawn[i][1]);
                        if (column < 0 || column >= Math.abs(width) || row < 0 || row >= image.getHeight()) continue;
                        int rgba = image.getPixel(column, row);
                        if ((rgba & 0xff) == 0) continue;
                        top = decor.kind();
                        colour = rgba >>> 8;
                        checked.add(decor.kind() + (decor.mirror() ? " mirrored" : ""));
                    }
                    assertTrue(top != null && top.startsWith("crown-"), where + " shows " + top);
                    assertTrue(CROWN_LEAVES.contains(colour), where + " is " + Integer.toHexString(colour) + ", not a leaf");
                }
            }
        });
        images.values().forEach(Pixmap::dispose);
        assertTrue(spots[0] > 0);
        assertTrue(checked.stream().anyMatch(k -> k.endsWith("mirrored")) && checked.stream().anyMatch(k -> !k.endsWith("mirrored")), checked.toString());
        System.out.println("crown spots checked on screen: " + spots[0] + " " + new java.util.TreeSet<>(checked));
    }

    /** The leaf keys of the layer grids (tools/wind_spots.py): the only colours a mid or grove patch may cover. */
    private static final String[] LAYER_LEAF_KEYS = {"", "456789", "789a"};

    private static Set<Integer> gridColours(String grid, String keys) {
        Set<Integer> colours = new HashSet<>();
        for (String line : Gdx.files.local("../art/parallax/forest/" + grid + ".grid").readString().split("\n")) {
            String[] words = line.trim().split("\\s+");
            if (words.length == 3 && words[0].equals("color") && keys.contains(words[1])) colours.add(Integer.parseInt(words[2], 16));
        }
        assertEquals(keys.length(), colours.size(), grid);
        return colours;
    }

    /** In every shipped room, each pixel of each mid and grove patch is a leaf pixel of its own layer's tile there: never sky, bark or undergrowth. */
    @Test void everyShippedLayerPatchLiesOnLeafPixelsOfItsOwnLayer() {
        java.util.Map<String, Pixmap> images = new java.util.HashMap<>();
        List<Set<Integer>> leaves = List.of(Set.of(), gridColours("mid", LAYER_LEAF_KEYS[MID]), gridColours("grove", LAYER_LEAF_KEYS[GROVE]));
        int[] pixels = {0};
        everyRoom((level, room) -> {
            for (int d = MID; d < DEPTHS; d++) {
                var layer = level.map.sceneryLayers.get(WindSpots.layerIndex(level.map, d));
                CrownGlintFx glints = level.glints();
                for (int spot = 0; spot < glints.spotCount(d); spot++) {
                    for (String pixel : pixels(glints.spotX(d, spot), glints.spotY(d, spot), glints.spotRuns(d, spot))) {
                        String[] xy = pixel.split(",");
                        int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]), tile = level.map.tileWidth;
                        var source = level.map.tileSource(layer.gids[y / tile * layer.columns + x / tile]);
                        String where = level.currentRoomName() + " depth " + d + " pixel " + pixel;
                        assertNotNull(source, where + " is in an empty cell");
                        Pixmap image = images.computeIfAbsent(source.image(), path -> new Pixmap(Gdx.files.internal(path)));
                        int rgba = image.getPixel(source.x() + x % tile, source.y() + y % tile);
                        assertEquals(0xff, rgba & 0xff, where + " is transparent");
                        assertTrue(leaves.get(d).contains(rgba >>> 8), where + " is " + Integer.toHexString(rgba >>> 8) + ", not a leaf");
                        pixels[0]++;
                    }
                }
            }
        });
        images.values().forEach(Pixmap::dispose);
        assertTrue(pixels[0] > 0);
        System.out.println("layer patch pixels checked: " + pixels[0]);
    }

    @Test void eachDepthIsDrawnAfterItsLayerAndUnderEverythingNearer() {
        Rectangle view = new Rectangle(0, 0, VIEW_W, VIEW_H);
        Level level = harness.room("forest-1", "forest-1-lever", () -> view);
        view.set(view(level, 0));
        int grove = WindSpots.layerIndex(level.map, GROVE), mid = WindSpots.layerIndex(level.map, MID);
        assertTrue(grove >= 0 && mid > grove);
        int frames = 0;
        while (level.glints().particles(CROWN).count() == 0 || level.glints().particles(MID).count() == 0
            || level.glints().particles(GROVE).count() == 0) {
            frame(level, view);
            assertTrue(++frames < 60 * 120, "no moment with all depths lit");
        }
        NoopBatch batch = new NoopBatch();
        batch.record(1 << 16);
        level.render(batch, view);
        TextureRegion quad = Shaders.whiteQuad();
        KimeriaParticleSystem crown = level.glints().particles(CROWN);
        int crownGlint = indexOf(batch, quad, crown.x(0) + crown.glintRuns(0)[0], crown.y(0) + crown.glintRuns(0)[1]), crowns = 0;
        for (Decor decor : level.decor) {
            if (decor.behindTiles() || !decor.kind().startsWith("crown-")) continue;
            NoopBatch alone = new NoopBatch();
            alone.record(1);
            decor.render(alone);
            int at = -1;
            for (int i = 0; i < batch.drawnCount(); i++)
                if (batch.drawnRegion(i) != quad && batch.drawnX(i) == alone.drawnX(0) && batch.drawnY(i) == alone.drawnY(0)
                    && batch.drawnWidth(i) == alone.drawnWidth(0)) at = i;
            assertTrue(at >= 0 && at < crownGlint, "the crown glints lie over every crown");
            crowns++;
        }
        assertTrue(crowns > 0);
        for (int d = MID; d < DEPTHS; d++) {
            KimeriaParticleSystem p = level.glints().particles(d);
            int at = indexOf(batch, quad, level.glints().layerBaseX(d) + p.x(0) + p.glintRuns(0)[0],
                level.glints().layerBaseY(d) + p.y(0) + p.glintRuns(0)[1]);
            assertTrue(at > lastOfLayer(batch, level, d == MID ? mid : grove), "depth " + d + " over its own layer");
            assertTrue(at < firstOfLayer(batch, level, (d == MID ? mid : grove) + 1), "depth " + d + " under the next layer");
        }
    }

    /**
     * The camera moves by fractions of a pixel and no layer offset is snapped (D154): at any height and x, a mid or grove glint the
     * level's own update lit is drawn exactly on the pixel of the layer cell its spot belongs to, as {@code renderBack} draws that cell.
     */
    @Test void aGlintStaysOnItsLayerPixelAtAnyCameraPosition() {
        Rectangle view = new Rectangle(0, 0, VIEW_W, VIEW_H);
        Level level = harness.room("forest-1", "forest-1-lever", () -> view);
        Rectangle floor = view(level, 0);
        float[] lifts = {0, .37f, 2.5f, 5.63f, 8}, sideways = {0, .4f, 31.7f};
        int checked = 0;
        for (float lift : lifts) {
            for (float shift : sideways) {
                level.resetCurrentRoom(player());
                view.set(floor.x + shift, floor.y - lift, VIEW_W, VIEW_H);
                int frames = 0;
                while (level.glints().particles(MID).count() == 0 || level.glints().particles(GROVE).count() == 0) {
                    level.update(STEP);
                    assertTrue(++frames < 60 * 120, "no moment with both layers lit at lift " + lift);
                }
                NoopBatch batch = new NoopBatch();
                batch.record(1 << 16);
                level.render(batch, view);
                for (int d = MID; d < DEPTHS; d++) {
                    var layer = level.map.sceneryLayers.get(WindSpots.layerIndex(level.map, d));
                    KimeriaParticleSystem p = level.glints().particles(d);
                    for (int i = 0; i < p.count(); i++) {
                        int column = (int) p.x(i) / level.map.tileWidth, row = (int) p.y(i) / level.map.tileHeight;
                        float[] run = p.glintRuns(i);
                        // where the cell must lie for the glint's first run to sit on its own pixel
                        TextureRegion cell = layer.cell(column, row);
                        boolean aligned = false;
                        for (int q = 0; q < batch.drawnCount() && !aligned; q++) {
                            if (batch.drawnRegion(q) != Shaders.whiteQuad()) continue;
                            float left = batch.drawnX(q) - run[0] - (p.x(i) - column * level.map.tileWidth);
                            float top = batch.drawnY(q) - run[1] - (p.y(i) - row * level.map.tileHeight);
                            for (int c = 0; c < batch.drawnCount() && !aligned; c++)
                                aligned = batch.drawnRegion(c) == cell && Math.abs(batch.drawnX(c) - left) < .001f && Math.abs(batch.drawnY(c) - top) < .001f;
                        }
                        assertTrue(aligned, "depth " + d + " glint " + i + " off its cell at lift " + lift + " shift " + shift);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 0);
    }

    private static int indexOf(NoopBatch batch, TextureRegion region, float x, float y) {
        for (int i = 0; i < batch.drawnCount(); i++)
            if (batch.drawnRegion(i) == region && batch.drawnX(i) == x && batch.drawnY(i) == y) return i;
        throw new AssertionError("glint not drawn at " + x + "," + y);
    }

    private static Set<TextureRegion> regions(Level level, int layer) {
        Set<TextureRegion> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (TextureRegion cell : level.map.sceneryLayers.get(layer).cells) if (cell != null) set.add(cell);
        return set;
    }

    private static int lastOfLayer(NoopBatch batch, Level level, int layer) {
        Set<TextureRegion> set = regions(level, layer);
        int last = -1;
        for (int i = 0; i < batch.drawnCount(); i++) if (set.contains(batch.drawnRegion(i))) last = i;
        assertTrue(last >= 0);
        return last;
    }

    private static int firstOfLayer(NoopBatch batch, Level level, int layer) {
        Set<TextureRegion> set = regions(level, layer);
        for (int i = 0; i < batch.drawnCount(); i++) if (set.contains(batch.drawnRegion(i))) return i;
        throw new AssertionError("layer " + layer + " not drawn");
    }
}
