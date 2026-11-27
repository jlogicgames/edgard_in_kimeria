package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.RoomRippleFx;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.items.Bomb;
import com.jlogicsoftware.kimeria.entity.items.Collectable;
import com.jlogicsoftware.kimeria.entity.items.ItemType;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Executable route measurements for #70; the protocol and routes live in docs/ROOMS.md. */
class RoomReplayTest {
    private static final float REAL_STEP = 1f / 60f;
    /** Hero x (frame position) of the take-off over the false honey fungus (D63, cloud x 1672..1696) for a route that walks the floor: body right edge 43 px short of the cloud. A route that flies over it on the falling platform (first pass) needs none. */
    private static final float FUNGUS_JUMP = 1600;
    private static final float REQUIRED_LIMIT = 20f;
    private static final float COLLECTION_LIMIT = 30f;
    /**
     * #553, #606, D123: the pauses of a room change (before the fade, where the leaving ripple plays, and after it, where the entering one
     * does) are part of the replay of a room, so every route is measured to the exit plus both. They are the constants the game uses, so a
     * longer pause fails here first.
     */
    private static final float PAUSE = RoomRippleFx.LEAVE_HOLD_SECONDS + RoomRippleFx.ARRIVE_HOLD_SECONDS;

    /** Records the measured time (printed as {@code REPLAY}, copied into D123) and asserts it with the pause added against the budget. */
    private static void within(String route, float seconds, float limit) {
        System.out.printf(java.util.Locale.ROOT, "REPLAY %s: %.2f s to the exit, %.2f s with the ripple pauses, limit %.0f s%n", route, seconds, seconds + PAUSE, limit);
        assertTrue(seconds + PAUSE <= limit, route + " replay budget exceeded with the ripple pauses");
    }

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private float timeScale = 1f;
    private int coins;
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings(); // assist stays off
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) {
            return new TextureRegion();
        }
        @Override public BitmapFont font(int size) { return null; } // no rendering
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "isGameStarted" -> true;
            case "setNormalTime" -> { timeScale = 1f; yield null; }
            case "setTimeScale" -> { timeScale = (float) args[0]; yield null; }
            case "spendCoins" -> { coins -= (int) args[0]; yield null; }
            case "addCoin" -> { coins++; yield null; }
            case "coinsCollected" -> coins;
            case "artifacts" -> artifacts;
            // Invulnerability and sound are off; camera callbacks do not affect physics.
            default -> method.getReturnType() == boolean.class ? false : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics;
        previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? REAL_STEP : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics;
        Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    /** #260 (D58), split under #500 (D110): the trap course is the second half of the old one-room course, x 640-1280; 993 leaves the pit lip for the log, 1089 leaves the log. */
    @Test void singleRoomSecondLevelRequiredRouteFitsTwentySeconds() {
        assertEquals(TRAPS_S, measure("forest-1-traps", 3, false, 0, new float[]{993, 1089}), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** #500 (D110): the first half of the old course, x 0-640: 125 and 260 hop the falling platforms and the step block; the lever and the wall are worked by the shared route. */
    @Test void forestOneEscalatorsRoomRequiredRouteFitsTwentySeconds() {
        assertEquals(ESCALATORS_S, measure("forest-1-escalators", 2, false, 0, new float[]{125, 260}), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** #375, reworked under #500 (D105): the RedMob held behind a log, a falling branch, an exploding stump, then the refill; the route jumps the log (3680) and the stump (4050) and swords the mob. */
    @Test void forestOneMobRoomRequiredRouteFitsTwentySeconds() {
        assertEquals(MOB_S, measure("forest-1-mob", 0, false, 0, new float[]{3680, 4050}), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** D128: the leg-hold is beat 3 and a death at its teeth replays about 4.6 s, well inside D105's 6 s for a first meeting that is not among the first two beats. */
    @Test void theLegHoldRetryIsAboutFourSeconds() {
        legHoldArrival = -1;
        measure("forest-entry", 0, false, 0, ENTRY_JUMPS);
        assertEquals(4.63f, legHoldArrival, 0.1f, "the card's retry (first reached at " + legHoldArrival + " s)");
    }

    /**
     * D128: Bat 43 patrols x 656-688 after the pit-2 landing, 16 px short of the exit. Its whole patrol stays clear of the exit, and the
     * mastered route, started late by 0 to 4 s (0.2 s steps) so it meets the Bat at every point of its patrol, is never hit at the landing
     * or on the way to the exit and reaches it ({@code replay} fails on a hit and when the exit is not reached).
     */
    @Test void theExitIsReachedSafelyPastBat43AtEveryBatPhase() {
        Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        Rectangle exit = level.roomLayout.rooms.get(0).exit();
        var bat = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Bat).map(o -> (com.jlogicsoftware.kimeria.entity.enemy.Bat) o)
            .max(java.util.Comparator.comparingDouble(b -> b.hitboxBounds().x)).orElseThrow();
        assertEquals(672f, bat.getX(), 0.5f, "Bat 43");
        for (int frame = 0; frame < 60 * 40; frame++) {
            level.update(REAL_STEP);
            Rectangle b = bat.hitboxBounds();
            assertTrue(b.x + b.width <= exit.x - 8f, "the Bat's patrol reaches x " + (b.x + b.width) + ", the exit starts at " + exit.x);
            assertTrue(b.x >= 656f - 1f, "and it never patrols short of x 656 (its patrol is x 656-688)");
        }
        StringBuilder deaths = new StringBuilder();
        for (int delay = 0; delay <= 240; delay += 12) {
            try { measure("forest-entry", 0, false, delay, ENTRY_JUMPS); }
            catch (AssertionError e) { deaths.append(delay / 60f).append(" s: ").append(e.getMessage()).append('\n'); }
        }
        assertEquals("", deaths.toString(), "the mastered route, started late, died at the pit-2 landing or before the exit");
    }

    @Test void forestEntryRequiredRouteFitsTwentySeconds() {
        assertEquals(ENTRY_S, measure("forest-entry", 0, false, 0, ENTRY_JUMPS), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** #376, #545 (D116): the wall room split off forest-entry at x 736; the route climbs the tree and leaps on to the second wall (ForestWallsRoute), no wall jump. */
    @Test void forestWallsRequiredRouteFitsTwentySeconds() {
        assertEquals(WALLS_S, measure("forest-walls", 1, false, 0, WALLS_JUMPS), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** #366: wait short of each fissure until a jet ends; jump on to the branch through the second jet and on to the ledge. */
    @Test void forestFissureRequiredRouteFitsTwentySeconds() {
        assertEquals(FISSURE_S, measure("forest-fissure", 2, false, 0, new float[]{}), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** #331 (D115): jump the thorn root and the trunk, wait on the branch while the wolf sprints under it and bays, step off on to it (a stomp), jump the second thorn root to the exit. */
    @Test void forestGladeRequiredRouteFitsTwentySeconds() {
        assertEquals(GLADE_S, measure("forest-glade", 3, false, 0, new float[]{}), PIN_MARGIN, "the card's beat time (D105)");
    }
    /** #330: jump the thorn root, walk under the ledge the flying beetle flies over and out of its blast's reach, past the falling stone. */
    @Test void forestRoostRequiredRouteFitsTwentySeconds() {
        assertEquals(ROOST_S, measure("forest-roost", 4, false, 0, ROOST_JUMPS), PIN_MARGIN, "the card's beat time (D105)");
    }
    @Test void forestHollowRequiredRouteFitsTwentySeconds() {
        assertEquals(HOLLOW_S, measure("forest-hollow", 5, false, 0, new float[]{}), PIN_MARGIN, "the card's beat time (D105)");
    }
    @Test void forestCanopyRequiredRouteFitsTwentySeconds() {
        assertEquals(CANOPY_S, measure("forest-canopy", 6, false, 0, new float[]{1400, FUNGUS_JUMP}), PIN_MARGIN, "the card's beat time (D105)");
    }
    @Test void forestEntryAllCollectablesFitThirtySeconds() {
        measure("forest-entry", 0, true, 40, new float[]{80, 140, 300, 535});
    }
    @Test void forestCanopyAllCollectablesFitThirtySeconds() {
        measure("forest-canopy", 6, true, 0, new float[]{1400, 1460, 1555});
    }

    /** The mastered hero stops this far short of the leg-hold's teeth when they are not shut (D128). */
    private static final float LEGHOLD_WAIT_PX = 16;
    /** The wait is for a hero on the floor: one on the ledge above (feet at y 240) cannot be seen by the trap and walks on, off the ledge's end. */
    private static final float LEGHOLD_FLOOR_BELOW = 284;
    /** Seconds from the room's start at which the mastered route first stands short of the leg-hold's open teeth: the soonest a death at them can replay from (D105's retry), -1 before it. */
    private float legHoldArrival = -1;
    private static final float[] ENTRY_JUMPS = {80, 225, 345, 535}, WALLS_JUMPS = {}, ROOST_JUMPS = {4550};
    /** Required routes with zero charges, pinned within {@link #PIN_MARGIN}: the room cards' beat times (D105). */
    static final float TRAPS_S = 6.00f, ESCALATORS_S = 5.48f, MOB_S = 6.75f, ENTRY_S = 8.37f, WALLS_S = 5.55f, FISSURE_S = 9.30f, GLADE_S = 8.85f, ROOST_S = 6.43f, HOLLOW_S = 10.08f, CANOPY_S = 9.48f;
    private static final float PIN_MARGIN = 0.1f;
    /** #327: the fissure room on a collection return, the beetle gone (8.08 s before the exit moved 16 px right). */
    private static final float FISSURE_RETURN_S = 8.25f;
    /** #331: the glade on a collection return, the wolf gone with its body (D101). */
    private static final float GLADE_RETURN_S = 7.42f;
    /** #645: the hollow on a collection return, its swarms gone. */
    private static final float HOLLOW_RETURN_S = 6.43f;



    @Test void repeatedCollectionReturnsReachMissedArtifactsWithRealMovementAndCannotFarm() {
        Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        for (int room = 0; room < 7; room++) {
            for (var o : new java.util.ArrayList<>(level.objects)) {
                if (o instanceof Enemy<?> enemy) enemy.collidedWithActor(true);
                if (o instanceof Collectable c && c.type() == ItemType.COIN) c.collideWithPlayer();
                if (o instanceof Bomb bomb) { bomb.collideWithPlayer(); bomb.update(.81f); }
            }
            if (room < 6) level.enterNextRoom(player());
        }
        level.prepareHealer();
        assertEquals(6, coins);
        assertTrue(artifacts.items().isEmpty());
        for (int trip = 0; trip < 3; trip++) {
            level.returnForCollection(player());
            held.clear(); pressed.clear(); timeScale = 1;
            float entry = replay(level, "forest-entry", false, 0, ENTRY_JUMPS, true);
            within("entry", entry, COLLECTION_LIMIT);
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof Collectable c && c.type() == ItemType.BAT),
                "#417: the Bat ingredient is no longer in the entry room (the inventory may hold it from an earlier trip)");
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            float walls = replay(level, "forest-walls", false, 0, WALLS_JUMPS, true);
            within("walls", walls, COLLECTION_LIMIT);
            assertTrue(artifacts.items().stream().anyMatch(p -> p.type() == ItemType.BAT), "#417: the Bat is on the second wall's top");
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.Beetle),
                "#327: the beetle killed on the first pass stays gone on a collection return, body included (D101)");
            float fissure = replay(level, "forest-fissure", false, 0, new float[]{}, true);
            within("fissure", fissure, COLLECTION_LIMIT);
            assertEquals(FISSURE_RETURN_S, fissure, 0.1f, "#327: the collection return walks the empty ledge (pinned, D68)");
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            float glade = replay(level, "forest-glade", false, 0, new float[]{}, true);
            within("glade", glade, COLLECTION_LIMIT);
            assertEquals(GLADE_RETURN_S, glade, 0.1f, "#331: the collection return walks past the wolf's body (pinned, D68)");
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.FlyingBeetle),
                "#330: the flying beetle killed on the first pass stays gone on a collection return, body included (D101)");
            float roost = replay(level, "forest-roost", false, 0, ROOST_JUMPS, true);
            within("roost", roost, COLLECTION_LIMIT);
            assertEquals(ROOST_S, roost, 0.1f, "#330: the mastered route never meets the beetle, so a return takes as long (D68)");
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.enemy.MosquitoSwarm),
                "#645: the swarms killed on the first pass stay gone on a collection return");
            float hollow = replay(level, "forest-hollow", false, 0, new float[]{}, true);
            within("hollow", hollow, COLLECTION_LIMIT);
            assertEquals(HOLLOW_RETURN_S, hollow, 0.1f, "#645: the collection return walks past the thorn root only (pinned, D68)");
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            // The first trip crosses on the falling platform and the ledge; later trips find the platform collapsed and walk the floor, jumping the fungus.
            float[] jumps = trip == 0 ? new float[]{1400, 1460, 1555, 1640} : new float[]{1400, 1460, FUNGUS_JUMP, 1640};
            float canopy = replay(level, "forest-canopy", false, 0, jumps, true);
            within("canopy", canopy, COLLECTION_LIMIT);
            assertTrue(artifacts.items().stream().anyMatch(p -> p.type() == ItemType.MUSHROOM));
            level.prepareHealer();
            assertEquals(6, coins);
            assertEquals(6, level.forestCoinsSecured());
            assertEquals(2, artifacts.items().size());
            assertTrue(level.canBrew());
            assertFalse(level.objects.stream().anyMatch(o -> o instanceof Enemy<?> || o instanceof Collectable));
            System.out.printf(java.util.Locale.ROOT, "Collection return %d: entry %.2f s, walls %.2f s, fissure %.2f s, glade %.2f s, roost %.2f s, hollow %.2f s, canopy %.2f s%n",
                trip + 1, entry, walls, fissure, glade, roost, hollow, canopy);
        }
        assertTrue(level.brewElixir());
        assertEquals(0, coins);
        assertTrue(artifacts.items().isEmpty());
        assertTrue(artifacts.hasFullSet("forest-2"));

    }

    @Test void realCanopyRouteKeepsCollapsedPlatformAndMainExitOpenUntilFreshLevel() throws Exception {
        Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        secureCombatAndCoins(level);
        level.enterNextRoom(player());
        secureCombatAndCoins(level);
        level.enterNextRoom(player()); // forest-fissure (#366): nothing to secure
        level.enterNextRoom(player()); // forest-glade (#331): its wolf is secured with the rest
        secureCombatAndCoins(level);
        level.enterNextRoom(player()); // forest-roost (#330): so is its flying beetle
        secureCombatAndCoins(level);
        level.enterNextRoom(player()); // forest-hollow (#645): so are its swarms
        secureCombatAndCoins(level);
        level.enterNextRoom(player());
        secureCombatAndCoins(level);

        float firstPass = replay(level, "forest-canopy", false, 0, new float[]{1400, 1460, 1555});
        within("firstPass", firstPass, COLLECTION_LIMIT);
        assertFalse(artifacts.items().stream().anyMatch(item -> item.type() == ItemType.MUSHROOM),
            "The first route intentionally misses the Mushroom");
        var firstPlatform = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 115);
        assertTrue(firstPlatform.isFalling(), "Real movement and jumps must touch platform 115 before the Mushroom route");
        for (int frame = 0; frame < 300 && !firstPlatform.isRemoved(); frame++) {
            held.clear(); pressed.clear();
            level.update(REAL_STEP);
        }
        assertTrue(firstPlatform.isRemoved(), "The real-input warning must finish before the collection return");
        level.prepareHealer();
        level.returnForCollection(player());

        float entry = replay(level, "forest-entry", false, 0, ENTRY_JUMPS);
        within("entry", entry, REQUIRED_LIMIT);
        level.enterNextRoom(player());
        float walls = replay(level, "forest-walls", false, 0, WALLS_JUMPS);
        within("walls", walls, REQUIRED_LIMIT);
        level.enterNextRoom(player());
        float fissure = replay(level, "forest-fissure", false, 0, new float[]{});
        within("fissure", fissure, REQUIRED_LIMIT);
        level.enterNextRoom(player());
        float glade = replay(level, "forest-glade", false, 0, new float[]{});
        within("glade", glade, REQUIRED_LIMIT);
        level.enterNextRoom(player());
        float roost = replay(level, "forest-roost", false, 0, ROOST_JUMPS);
        within("roost", roost, REQUIRED_LIMIT);
        level.enterNextRoom(player());
        float hollow = replay(level, "forest-hollow", false, 0, new float[]{});
        within("hollow", hollow, REQUIRED_LIMIT);
        level.enterNextRoom(player());
        var platform = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(level, 115);
        assertNotSame(firstPlatform, platform);
        assertTrue(platform.isRemoved(), "Collection return preserves the secured collapsed platform state");
        assertTrue(level.fallingPlatforms.isEmpty(), "Removed platform must be pruned from live collision/update lists");

        float secondPass = replay(level, "forest-canopy", false, 0, new float[]{1400, 1460, FUNGUS_JUMP});
        within("secondPass", secondPass, COLLECTION_LIMIT);
        assertFalse(artifacts.items().stream().anyMatch(item -> item.type() == ItemType.MUSHROOM),
            "The optional Mushroom may be missed when its one-shot route support remains collapsed");
        level.enterNextRoom(player());
        assertTrue(platform.isRemoved(), "Securing the main exit retains the collapsed platform");

        Level freshLevel = new Level(assets, game, player(), "forest-2", Rectangle::new);
        secureCombatAndCoins(freshLevel);
        freshLevel.enterNextRoom(player());
        secureCombatAndCoins(freshLevel);
        freshLevel.enterNextRoom(player());
        freshLevel.enterNextRoom(player());
        freshLevel.enterNextRoom(player());
        freshLevel.enterNextRoom(player());
        freshLevel.enterNextRoom(player());
        secureCombatAndCoins(freshLevel);
        var authoredPlatform = (com.jlogicsoftware.kimeria.entity.objects.FallingPlatform) roomOwner(freshLevel, 115);
        assertFalse(authoredPlatform.isRemoved());
        assertFalse(authoredPlatform.isFalling(), "A fresh Level restores the authored one-shot support");
        float freshMushroomRoute = replay(freshLevel, "forest-canopy", false, 0,
            new float[]{1400, 1460, 1555, 1640}, true);
        within("freshMushroomRoute", freshMushroomRoute, COLLECTION_LIMIT);
        assertTrue(artifacts.items().stream().anyMatch(item -> item.type() == ItemType.MUSHROOM),
            "The fresh-level full artifact route can still collect the optional Mushroom");
    }


    private void secureCombatAndCoins(Level level) {
        for (var object : new java.util.ArrayList<>(level.objects)) {
            if (object instanceof Enemy<?> enemy) enemy.collidedWithActor(true);
            if (object instanceof Collectable item && item.type() == ItemType.COIN) item.collideWithPlayer();
            if (object instanceof Bomb bomb) { bomb.collideWithPlayer(); bomb.update(.81f); }
        }
    }

    private com.jlogicsoftware.kimeria.entity.WorldObject roomOwner(Level level, int id) throws Exception {
        var owners = Level.class.getDeclaredField("roomStateOwners");
        owners.setAccessible(true);
        return ((java.util.Map<Integer, com.jlogicsoftware.kimeria.entity.WorldObject>) owners.get(level)).get(id);
    }

    private Player player() { return new Player(assets, game, 0, 0); }

    /** #417 (D96): the Bat rests on the top of the second wall (Y grows down: its bottom edge is the wall's top edge). */
    @Test void theBatRestsOnTopOfTheSecondWall() {
        Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        level.enterNextRoom(player());
        Collectable bat = level.objects.stream().filter(Collectable.class::isInstance).map(Collectable.class::cast)
            .filter(item -> item.type() == ItemType.BAT).findFirst().orElseThrow();
        var wall = level.map.layer("Collisions").stream().filter(o -> o.id == 45).findFirst().orElseThrow();
        assertEquals(wall.y, bat.getY() + bat.getHeight(), 0.01f, "the Bat's bottom edge is the wall's top edge");
        assertTrue(bat.getX() >= wall.x && bat.getX() + bat.getWidth() <= wall.x + wall.width, "within the wall's width");
    }

    @Test void gameplayContactCollectsTheBatAndRoomRetryRestoresIt() {
        Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        level.enterNextRoom(player());  // #417: the Bat is in forest-walls, the second room
        Collectable bat = level.objects.stream().filter(Collectable.class::isInstance)
            .map(Collectable.class::cast).filter(item -> item.type() == ItemType.BAT).findFirst().orElseThrow();
        Rectangle hitbox = level.player.hitboxBounds();
        level.player.position.add(bat.centerX() - hitbox.x, bat.centerY() - hitbox.y);
        level.player.update(0f);
        assertTrue(bat.isCollected());
        assertEquals(1, artifacts.items().size());
        assertEquals(0, coins);
        level.resetCurrentRoom(player());
        assertTrue(artifacts.items().isEmpty());
        assertTrue(level.objects.stream().anyMatch(o -> o instanceof Collectable c
            && c.type() == ItemType.BAT && !c.isCollected()));
    }

    /** The teaching order of #73: each mechanic first appears alone, and the room-one threats do not overlap. */
    @Test void forestIntroducesStompTheLegHoldAndFallingPlatformOneAtATime() {
        Level level = new Level(assets, game, player(), "forest-2", Rectangle::new);
        assertTrue(level.objects.stream().noneMatch(Bomb.class::isInstance), "the Bomb left the forest for the Fortress (D128)");
        var mobs = level.objects.stream().filter(o -> o instanceof Enemy<?> e && !e.getClass().getSimpleName().equals("Bat"))
            .map(o -> (Enemy<?>) o).sorted(java.util.Comparator.comparingDouble(e -> e.hitboxBounds().x)).toList();
        assertFalse(mobs.isEmpty());
        assertEquals("YellowMob", mobs.get(0).getClass().getSimpleName(), "stomp is the first mechanic met");
        var legHold = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.objects.LegHold.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.objects.LegHold.class::cast).findFirst().orElseThrow();
        assertTrue(mobs.get(0).hitboxBounds().x < legHold.getX(), "the first mob comes before the leg-hold");
        for (Enemy<?> mob : mobs) {
            float gap = Math.abs(mob.hitboxBounds().x + mob.hitboxBounds().width / 2f - legHold.centerX());
            assertTrue(gap >= 150f, "a walking enemy starts too close to the leg-hold: " + gap + " px");
        }
        assertTrue(level.fallingPlatforms.isEmpty(), "falling platforms are not introduced in the entry room");
        level.enterNextRoom(player());
        assertTrue(level.fallingPlatforms.isEmpty(), "nor in the wall room");
        assertTrue(level.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.objects.LegHold.class::isInstance), "the leg-hold stays in the entry room");
        level.enterNextRoom(player());
        assertTrue(level.fallingPlatforms.isEmpty(), "nor in the fissure room (#366)");
        level.enterNextRoom(player());
        assertTrue(level.fallingPlatforms.isEmpty(), "nor in the glade room (#331)");
        level.enterNextRoom(player());
        assertTrue(level.fallingPlatforms.isEmpty(), "nor in the roost (#330)");
        level.enterNextRoom(player());
        assertTrue(level.fallingPlatforms.isEmpty(), "nor in the hollow (#645)");
        level.enterNextRoom(player());
        assertEquals(1, level.fallingPlatforms.size(), "one optional falling platform in the canopy");
        assertTrue(level.objects.stream().noneMatch(Bomb.class::isInstance), "no bomb shares the canopy room");
        float platformX = level.fallingPlatforms.get(0).getX();
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof Enemy<?> e
            && !e.getClass().getSimpleName().equals("Bat")
            && Math.abs(e.hitboxBounds().x - platformX) < 64f), "no walking enemy right at the falling platform");
    }

    /** The Bomb is kept for the Fortress (D128); these two tests run on the one-room fixture {@code bomb-test.tmx}. */
    @Test void touchingABombThroughGameplayStartsAWarningInsteadOfImmediateDeath() {
        Level level = new Level(assets, game, player(), "bomb-test", Rectangle::new);
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        Rectangle hitbox = level.player.hitboxBounds();
        level.player.position.add(bomb.centerX() - hitbox.x, bomb.centerY() - hitbox.y);
        level.player.update(0f); // Use the real gameplay overlap path to activate it.
        assertFalse(level.player.isGotHit());
        bomb.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 0.6f);
        level.player.update(0f); // A continued overlap must not restart the warning.
        assertFalse(level.player.isGotHit());
        bomb.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 0.41f);
        assertTrue(level.player.isGotHit());
    }

    @Test void playerCanEscapeAnActivatedBombAndReturnDuringItsCosmeticEffect() {
        Level level = new Level(assets, game, player(), "bomb-test", Rectangle::new);
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        Rectangle hitbox = level.player.hitboxBounds();
        level.player.position.add(bomb.centerX() - hitbox.x, bomb.centerY() - hitbox.y);
        Vector2 contactPosition = new Vector2(level.player.position);
        level.player.update(0f);
        level.player.position.x += 80f;
        bomb.update(TrapWarnings.DEFAULT_WARNING_SECONDS * 1.01f);
        assertTrue(bomb.isRemoved());
        assertFalse(level.player.isGotHit());
        level.player.position.set(contactPosition);
        level.update(0f); // Drain the explosion effect into the room.
        level.update(0f); // Real gameplay updates that effect without applying more damage.
        assertFalse(level.player.isGotHit());
    }

    @Test void secondLevelRefillIsCollectedAfterTheFirstRedMob() {
        var stock = new com.jlogicsoftware.kimeria.RangedStock();
        stock.restore(2);
        Level level = new Level(assets, game, player(), "forest-1", Rectangle::new, stock);
        for (int attempt = 0; attempt < 3; attempt++) {
            held.clear(); pressed.clear(); timeScale = 1;
            if (attempt > 0) level.resetCurrentRoom(player());
            float seconds = replay(level, "forest-1-mob", false, 0, new float[]{3680, 4050}, true);
            assertEquals(3, level.rangedStock.count());
            assertTrue(level.objects.stream().noneMatch(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.FireballRefill && !o.isRemoved()));
            within("second-level required route", seconds, REQUIRED_LIMIT);
            System.out.printf(java.util.Locale.ROOT, "forest-1 refill attempt %d: %.2f s%n", attempt + 1, seconds);
        }
    }

    /** Replays the route three times from a fresh room and returns its time, the same on every attempt. */
    private float measure(String route, int roomIndex, boolean collect, int delayFrames, float[] jumps) {
        String mapName = route.startsWith("forest-1") ? "forest-1" : "forest-2";
        var stock = new com.jlogicsoftware.kimeria.RangedStock();
        stock.restore(0);
        Level level = new Level(assets, game, player(), mapName, Rectangle::new, stock);
        // forest-1's escalator room is room 2 (0-based) since #375 put the first RedMob in a room of its own before it and #363 the deadfall room after it; the trap course (D110, #500) is room 3.
        for (int room = 0; room < roomIndex; room++) level.enterNextRoom(player());
        float firstTime = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            coins = 0; timeScale = 1; held.clear(); pressed.clear();
            if (attempt > 0) level.resetCurrentRoom(player());
            assertEquals(0, level.rangedStock.count(), "Required/coin replay starts with no charges, including retries");
            float seconds = replay(level, route, collect, delayFrames, jumps);
            assertTrue(level.objects.stream().noneMatch(com.jlogicsoftware.kimeria.entity.items.Fireball.class::isInstance),
                "Required and coin routes never fire a projectile");
            within(route, seconds, collect ? COLLECTION_LIMIT : REQUIRED_LIMIT);
            if (collect) assertEquals(3, coins, "The collection route must pick up every coin in its room");
            if (attempt == 0) firstTime = seconds;
            else assertEquals(firstTime, seconds, REAL_STEP, "A reset must reproduce the same route time");
        }
        System.out.printf(java.util.Locale.ROOT, "%s %s: %.2f s (three identical attempts)%n",
            route, collect ? "all collections" : "required", firstTime);
        return firstTime;
    }

    /** Whether a live Bat's hitbox is within 40 px of the hero's, both ways (the collection route's wait under the stair's Bat, #500); a killed one's body (D101) is no threat. */
    private static boolean batClose(Level level, Player player) {
        Rectangle me = player.hitboxBounds();
        for (var object : level.objects) if (object instanceof com.jlogicsoftware.kimeria.entity.enemy.Bat bat && !bat.isDefeated()) {
            Rectangle b = bat.hitboxBounds();
            if (b.x - (me.x + me.width) < 40 && me.x - (b.x + b.width) < 40 && b.y - (me.y + me.height) < 40 && me.y - (b.y + b.height) < 40) return true;
        }
        return false;
    }

    private float replay(Level level, String route, boolean collect, int delayFrames, float[] jumps) {
        return replay(level, route, collect, delayFrames, jumps, false);
    }

    private float replay(Level level, String route, boolean collect, int delayFrames, float[] jumps, boolean artifactsRoute) {
        Player player = level.player;
        int jump = 0, lastJumpFrame = -2;
        boolean actionWasDown = false, wasMechanism = false;
        // #366: the fissure room is passed by timing, not at fixed x: the shared route waits for each jet to end.
        ForestFissureRoute fissureRoute = route.equals("forest-fissure") ? new ForestFissureRoute(level) : null;
        // #331: the glade is passed by watching the wolf: wait on the branch until it bays, then step off on to its back.
        ForestGladeRoute gladeRoute = route.equals("forest-glade") ? new ForestGladeRoute(level) : null;
        // #545: the wall room is passed by the tree in front of the first wall and a leap from its top branch, never by a wall jump.
        ForestWallsRoute wallsRoute = route.equals("forest-walls") ? new ForestWallsRoute(false) : null;
        // #645: the hollow is passed by watching the swarms: jump the low one head on, walk under the high one.
        ForestHollowRoute hollowRoute = route.equals("forest-hollow") ? new ForestHollowRoute(level) : null;
        // Entry collection route: get the coin behind the entrance, then return once for the ground coin.
        int phase = collect && route.equals("forest-entry") ? 0 : 1;
        for (int frame = 0; frame < 60 * COLLECTION_LIMIT; frame++) {
            // #486: forest-entry has two pits (x 240-320 and x 560-608); every route crosses them, none falls (the floor is y 320).
            if (route.equals("forest-entry")) assertTrue(player.position.y < 284, "the route fell into a pit at x " + player.position.x);
            held.clear(); pressed.clear();
            if (frame >= delayFrames) {
                if (collect && route.equals("forest-entry")) {
                    if (phase == 0 && player.position.x <= 8) phase = 1;
                    if (phase == 1 && player.position.x >= 400) phase = 2;
                    if (phase == 2 && player.position.x <= 330) phase = 3;
                }
                if (fissureRoute != null) fissureRoute.drive(level, held, pressed);
                else if (gladeRoute != null) gladeRoute.drive(level, held, pressed);
                else if (wallsRoute != null) wallsRoute.drive(level, held, pressed);
                else if (hollowRoute != null) hollowRoute.drive(level, held, pressed);
                else held.add(phase == 0 || phase == 2 ? Input.Keys.A : Input.Keys.D);
                // D128: the leg-hold's mastered answer needs no jump: stand short of the open jaws, let them snap empty, walk over them shut.
                if (route.equals("forest-entry") && held.contains(Input.Keys.D)) {
                    com.jlogicsoftware.kimeria.entity.objects.LegHold legHold = level.objects.stream()
                        .filter(com.jlogicsoftware.kimeria.entity.objects.LegHold.class::isInstance)
                        .map(com.jlogicsoftware.kimeria.entity.objects.LegHold.class::cast).findFirst().orElse(null);
                    Rectangle me = player.hitboxBounds();
                    float teeth = legHold == null ? 0 : legHold.openBoxInto(new Rectangle()).x, right = me.x + me.width;
                    if (legHold != null && me.y + me.height > LEGHOLD_FLOOR_BELOW && right < teeth && right > teeth - LEGHOLD_WAIT_PX && legHold.phase() != com.jlogicsoftware.kimeria.entity.objects.LegHold.Phase.SHUT)
                    { held.remove(Input.Keys.D); if (legHoldArrival < 0) legHoldArrival = frame * REAL_STEP; }
                }
                // #500 (D105): Bat 41 hangs in the stair's gap at x 352; the collection route (coins under and past it) waits on the ground while the Bat is within 40 px of him.
                boolean batWait = collect && route.equals("forest-entry") && player.isOnGround() && batClose(level, player);
                if (batWait) { held.remove(Input.Keys.D); held.remove(Input.Keys.A); }
                if (!batWait && jump < jumps.length && player.position.x >= jumps[jump] && player.isOnGround()
                    && frame - lastJumpFrame > 1) {
                    held.add(Input.Keys.J); jump++; lastJumpFrame = frame;
                }
                if (collect && route.equals("forest-entry") && phase == 3) {
                    // At normal speed the upper coin needs a ground jump beside its one-way platform.
                    var upperCoin = level.objects.stream().filter(Collectable.class::isInstance)
                        .map(Collectable.class::cast).filter(c -> c.type() == ItemType.COIN
                            && !c.isCollected() && c.getY() < 284).findFirst().orElse(null);
                    if (upperCoin != null) {
                        float stop = upperCoin.getX() - player.hitbox.offsetX;
                        if (player.position.x >= stop && player.position.x < stop + 12) {
                            held.remove(Input.Keys.D);
                            if (player.isOnGround() && frame - lastJumpFrame > 1) {
                                held.add(Input.Keys.J); lastJumpFrame = frame;
                            }
                        }
                    }
                }
                if (artifactsRoute && route.startsWith("forest-1")) {
                    var refill = level.objects.stream().filter(o -> o instanceof com.jlogicsoftware.kimeria.entity.items.FireballRefill
                        && !o.isRemoved()).map(com.jlogicsoftware.kimeria.entity.items.FireballRefill.class::cast).findFirst().orElse(null);
                    float stop = refill == null ? -1 : refill.getX() - player.hitbox.offsetX;
                    if (refill != null && player.position.x >= stop && player.position.x < stop + 20) {
                        held.remove(Input.Keys.D);
                        if (player.isOnGround() && frame - lastJumpFrame > 1) {
                            held.add(Input.Keys.J); lastJumpFrame = frame;
                        }
                    }
                } else if (artifactsRoute && route.equals("forest-canopy")) {
                    ItemType target = ItemType.MUSHROOM;
                    float stop = 1710;
                    boolean hasTarget = artifacts.items().stream().anyMatch(p -> p.type() == target);
                    if (!hasTarget && player.position.x >= stop && player.position.x < stop + 20) {
                        held.remove(Input.Keys.D);
                        if (player.isOnGround() && frame - lastJumpFrame > 1) {
                            held.add(Input.Keys.J); lastJumpFrame = frame;
                        }
                    }
                }
                boolean mechanism = player.canInteract();
                boolean action = false; // Up alone operates a mechanism (D126); the sword is for monsters only
                // The sword bursts a beetle on the hero (D100): in the fissure room the shared route answers it, by the lure (#327).
                for (var object : level.objects) if (object instanceof Enemy<?> enemy
                    && !(fissureRoute != null && enemy instanceof com.jlogicsoftware.kimeria.entity.enemy.Beetle)
                    && !(gladeRoute != null && enemy instanceof com.jlogicsoftware.kimeria.entity.enemy.Wolf)
                    && !(enemy instanceof com.jlogicsoftware.kimeria.entity.enemy.MosquitoSwarm)) { // the sword cannot kill it (D151)
                    Rectangle e = enemy.hitboxBounds(), me = player.hitboxBounds();
                    if (player.isOnGround() && e.x >= me.x && e.x - (me.x + me.width) < 18
                        && e.y < me.y + me.height && e.y + e.height > me.y) { action = true; mechanism = false; }
                }
                if (mechanism) held.add(Input.Keys.W);
                if (action && (!actionWasDown || wasMechanism != mechanism)) pressed.add(Input.Keys.K);
                wasMechanism = mechanism;
                actionWasDown = action;
            }
            // Match KimeriaGame.render: scale the world step, but keep keyboard buffering on real time.
            level.update(REAL_STEP * timeScale);
            assertFalse(player.isGotHit(), route + " died at frame " + frame + " at " + player.position + " phase " + phase);
            // Stop this pickup exercise before Checkpoint secures it, so retries test rollback.
            if (artifactsRoute && route.startsWith("forest-1") && level.rangedStock.count() == 3)
                return (frame + 1) * REAL_STEP;
            if (level.atRoomExit() || player.getState() == Player.State.DISAPPEARING)
                return (frame + 1) * REAL_STEP;
        }
        fail(route + " failed to reach its actual exit within 30 seconds; position=" + player.position);
        return 0;
    }
}
