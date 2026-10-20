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
    private static final float REQUIRED_LIMIT = 20f;
    private static final float COLLECTION_LIMIT = 30f;

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
        HeadlessFiles files = new HeadlessFiles();
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class}, (proxy, method, args) -> {
            // Read the exact shipped map under an alias, omitting only forest-1's decorative sky/rain.
            // All room markers, collision objects, hazards, enemies, and player physics remain intact.
            if (method.getName().equals("internal") && args[0].equals("tiles/replay-forest-1.tmx"))
                return files.internal("tiles/forest-1.tmx");
            return method.invoke(files, args);
        });
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

    @Test void singleRoomSecondLevelRequiredRouteFitsTwentySeconds() {
        measure("forest-1", false, false, 0, new float[]{125, 260, 540});
    }
    @Test void forestEntryRequiredRouteFitsTwentySeconds() {
        measure("forest-entry", false, false, 0, new float[]{95, 225, 345, 535, 860, 940});
    }
    @Test void forestCanopyRequiredRouteFitsTwentySeconds() {
        measure("forest-canopy", true, false, 0, new float[]{1400});
    }
    @Test void forestEntryAllCollectablesFitThirtySeconds() {
        measure("forest-entry", false, true, 40, new float[]{95, 140, 300, 535, 860, 940});
    }
    @Test void forestCanopyAllCollectablesFitThirtySeconds() {
        measure("forest-canopy", true, true, 0, new float[]{1400, 1460, 1555});
    }



    @Test void repeatedCollectionReturnsReachMissedArtifactsWithRealMovementAndCannotFarm() {
        Level level = new Level(assets, game, player(), "forest", Rectangle::new);
        for (int room = 0; room < 2; room++) {
            for (var o : new java.util.ArrayList<>(level.objects)) {
                if (o instanceof Enemy<?> enemy) enemy.collidedWithActor(true);
                if (o instanceof Collectable c && c.type() == ItemType.COIN) c.collideWithPlayer();
                if (o instanceof Bomb bomb) { bomb.collideWithPlayer(); bomb.update(.81f); }
            }
            if (room == 0) level.enterNextRoom(player());
        }
        level.prepareHealer();
        assertEquals(6, coins);
        assertTrue(artifacts.items().isEmpty());
        for (int trip = 0; trip < 3; trip++) {
            level.returnForCollection(player());
            held.clear(); pressed.clear(); timeScale = 1;
            float entry = replay(level, "forest-entry", false, 0, new float[]{95, 225, 345, 535, 860, 940}, true);
            assertTrue(entry <= COLLECTION_LIMIT);
            assertTrue(artifacts.items().stream().anyMatch(p -> p.type() == ItemType.CHICKEN));
            level.enterNextRoom(player()); held.clear(); pressed.clear(); timeScale = 1;
            float canopy = replay(level, "forest-canopy", false, 0, new float[]{1400, 1460, 1555, 1640}, true);
            assertTrue(canopy <= COLLECTION_LIMIT);
            assertTrue(artifacts.items().stream().anyMatch(p -> p.type() == ItemType.KEY));
            level.prepareHealer();
            assertEquals(6, coins);
            assertEquals(6, level.forestCoinsSecured());
            assertEquals(2, artifacts.items().size());
            assertTrue(level.canBrew());
            assertFalse(level.objects.stream().anyMatch(o -> o instanceof Enemy<?> || o instanceof Collectable));
            System.out.printf(java.util.Locale.ROOT, "Collection return %d: entry %.2f s, canopy %.2f s%n", trip + 1, entry, canopy);
        }
        assertTrue(level.brewElixir());
        assertEquals(0, coins);
        assertTrue(artifacts.items().isEmpty());
        assertTrue(artifacts.hasFullSet("forest"));

    }

    private Player player() { return new Player(assets, game, 0, 0); }

    @Test void gameplayContactCollectsTheChickenAndRoomRetryRestoresIt() {
        Level level = new Level(assets, game, player(), "forest", Rectangle::new);
        Collectable chicken = level.objects.stream().filter(Collectable.class::isInstance)
            .map(Collectable.class::cast).filter(item -> item.type() == ItemType.CHICKEN).findFirst().orElseThrow();
        Rectangle hitbox = level.player.hitboxBounds();
        level.player.position.add(chicken.centerX() - hitbox.x, chicken.centerY() - hitbox.y);
        level.player.update(0f);
        assertTrue(chicken.isCollected());
        assertEquals(1, artifacts.items().size());
        assertEquals(0, coins);
        level.resetCurrentRoom(player());
        assertTrue(artifacts.items().isEmpty());
        assertTrue(level.objects.stream().anyMatch(o -> o instanceof Collectable c
            && c.type() == ItemType.CHICKEN && !c.isCollected()));
    }

    /** The teaching order of #73: each mechanic first appears alone, and the room-one threats do not overlap. */
    @Test void forestIntroducesStompBombAndFallingPlatformOneAtATime() {
        Level level = new Level(assets, game, player(), "forest", Rectangle::new);
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        var mobs = level.objects.stream().filter(o -> o instanceof Enemy<?> e && !e.getClass().getSimpleName().equals("Bat"))
            .map(o -> (Enemy<?>) o).sorted(java.util.Comparator.comparingDouble(e -> e.hitboxBounds().x)).toList();
        assertFalse(mobs.isEmpty());
        assertEquals("YellowMob", mobs.get(0).getClass().getSimpleName(), "stomp is the first mechanic met");
        assertTrue(mobs.get(0).hitboxBounds().x < bomb.centerX(), "the first mob comes before the bomb");
        for (Enemy<?> mob : mobs) {
            float gap = Math.abs(mob.hitboxBounds().x + mob.hitboxBounds().width / 2f - bomb.centerX());
            assertTrue(gap >= 150f, "a walking enemy starts too close to the bomb: " + gap + " px");
        }
        assertTrue(level.fallingPlatforms.isEmpty(), "falling platforms are not introduced in the entry room");
        level.enterNextRoom(player());
        assertEquals(1, level.fallingPlatforms.size(), "one optional falling platform in the canopy");
        assertTrue(level.objects.stream().noneMatch(Bomb.class::isInstance), "no bomb shares the canopy room");
        float platformX = level.fallingPlatforms.get(0).getX();
        assertTrue(level.objects.stream().noneMatch(o -> o instanceof Enemy<?> e
            && !e.getClass().getSimpleName().equals("Bat")
            && Math.abs(e.hitboxBounds().x - platformX) < 64f), "no walking enemy right at the falling platform");
    }

    @Test void touchingABombThroughGameplayStartsAWarningInsteadOfImmediateDeath() {
        Level level = new Level(assets, game, player(), "forest", Rectangle::new);
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        Rectangle hitbox = level.player.hitboxBounds();
        level.player.position.add(bomb.centerX() - hitbox.x, bomb.centerY() - hitbox.y);
        level.player.update(0f); // Use the real gameplay overlap path to activate it.
        assertFalse(level.player.isGotHit());
        bomb.update(0.5f);
        level.player.update(0f); // A continued overlap must not restart the warning.
        assertFalse(level.player.isGotHit());
        bomb.update(0.31f);
        assertTrue(level.player.isGotHit());
    }

    @Test void playerCanEscapeAnActivatedBombAndReturnDuringItsCosmeticEffect() {
        Level level = new Level(assets, game, player(), "forest", Rectangle::new);
        Bomb bomb = level.objects.stream().filter(Bomb.class::isInstance).map(Bomb.class::cast).findFirst().orElseThrow();
        Rectangle hitbox = level.player.hitboxBounds();
        level.player.position.add(bomb.centerX() - hitbox.x, bomb.centerY() - hitbox.y);
        Vector2 contactPosition = new Vector2(level.player.position);
        level.player.update(0f);
        level.player.position.x += 80f;
        bomb.update(0.81f);
        assertTrue(bomb.isRemoved());
        assertFalse(level.player.isGotHit());
        level.player.position.set(contactPosition);
        level.update(0f); // Drain the explosion effect into the room.
        level.update(0f); // Real gameplay updates that effect without applying more damage.
        assertFalse(level.player.isGotHit());
    }

    private void measure(String route, boolean canopy, boolean collect, int delayFrames, float[] jumps) {
        String mapName = route.equals("forest-1") ? "replay-forest-1" : "forest";
        Level level = new Level(assets, game, player(), mapName, Rectangle::new);
        if (canopy) level.enterNextRoom(player());
        float firstTime = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            coins = 0; timeScale = 1; held.clear(); pressed.clear();
            if (attempt > 0) level.resetCurrentRoom(player());
            float seconds = replay(level, route, collect, delayFrames, jumps);
            assertTrue(seconds <= (collect ? COLLECTION_LIMIT : REQUIRED_LIMIT), route + " replay budget exceeded");
            if (collect) assertEquals(3, coins, "The collection route must pick up every coin in its room");
            if (attempt == 0) firstTime = seconds;
            else assertEquals(firstTime, seconds, REAL_STEP, "A reset must reproduce the same route time");
        }
        System.out.printf(java.util.Locale.ROOT, "%s %s: %.2f s (three identical attempts)%n",
            route, collect ? "all collections" : "required", firstTime);
    }

    private float replay(Level level, String route, boolean collect, int delayFrames, float[] jumps) {
        return replay(level, route, collect, delayFrames, jumps, false);
    }

    private float replay(Level level, String route, boolean collect, int delayFrames, float[] jumps, boolean artifactsRoute) {
        Player player = level.player;
        int jump = 0, lastJumpFrame = -2;
        boolean actionWasDown = false;
        // Entry collection route: get the coin behind the entrance, then return once for the ground coin.
        int phase = collect && route.equals("forest-entry") ? 0 : 1;
        for (int frame = 0; frame < 60 * COLLECTION_LIMIT; frame++) {
            held.clear(); pressed.clear();
            if (frame >= delayFrames) {
                if (collect && route.equals("forest-entry")) {
                    if (phase == 0 && player.position.x <= 8) phase = 1;
                    if (phase == 1 && player.position.x >= 400) phase = 2;
                    if (phase == 2 && player.position.x <= 330) phase = 3;
                }
                held.add(phase == 0 || phase == 2 ? Input.Keys.A : Input.Keys.D);
                if (jump < jumps.length && player.position.x >= jumps[jump] && player.isOnGround()
                    && frame - lastJumpFrame > 1) {
                    held.add(Input.Keys.J); jump++; lastJumpFrame = frame;
                }
                if (collect && route.equals("forest-entry") && phase == 3) {
                    // At normal speed the upper coin needs a ground jump beside its one-way platform.
                    var upperCoin = level.objects.stream().filter(Collectable.class::isInstance)
                        .map(Collectable.class::cast).filter(c -> c.type() == ItemType.COIN
                            && !c.isCollected() && c.getY() < 300).findFirst().orElse(null);
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
                if (artifactsRoute) {
                    ItemType target = route.equals("forest-entry") ? ItemType.CHICKEN : ItemType.KEY;
                    float stop = target == ItemType.CHICKEN ? 430 : 1710;
                    boolean hasTarget = artifacts.items().stream().anyMatch(p -> p.type() == target);
                    if (!hasTarget && player.position.x >= stop && player.position.x < stop + 20) {
                        held.remove(Input.Keys.D);
                        if (player.isOnGround() && frame - lastJumpFrame > 1) {
                            held.add(Input.Keys.J); lastJumpFrame = frame;
                        }
                    }
                }
                // Fresh wall-jump presses climb the two tall walls; release between presses.
                if (player.isClambering() && frame % 16 == 0 && frame - lastJumpFrame > 1) {
                    held.add(Input.Keys.J); lastJumpFrame = frame;
                }
                boolean action = player.canInteract();
                for (var object : level.objects) if (object instanceof Enemy<?> enemy) {
                    Rectangle e = enemy.hitboxBounds(), me = player.hitboxBounds();
                    if (player.isOnGround() && e.x >= me.x && e.x - (me.x + me.width) < 18
                        && e.y < me.y + me.height && e.y + e.height > me.y) action = true;
                }
                if (action && !actionWasDown) pressed.add(Input.Keys.K);
                actionWasDown = action;
            }
            // Match KimeriaGame.render: scale the world step, but keep keyboard buffering on real time.
            level.update(REAL_STEP * timeScale);
            assertFalse(player.isGotHit(), route + " died at frame " + frame);
            if (level.atRoomExit() || player.getState() == Player.State.DISAPPEARING)
                return (frame + 1) * REAL_STEP;
        }
        fail(route + " failed to reach its actual exit within 30 seconds; position=" + player.position);
        return 0;
    }
}
