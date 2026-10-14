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
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
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
            case "isSlowTime" -> timeScale < 1;
            case "setSlowTime" -> { timeScale = .5f; yield null; }
            case "setNormalTime" -> { timeScale = 1f; yield null; }
            case "setTimeScale" -> { timeScale = (float) args[0]; yield null; }
            case "addCoin" -> { coins++; yield null; }
            case "coinsCollected" -> coins;
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

    @Test void firstLevelRequiredRouteFitsTwentySeconds() {
        measure("forest-1", false, false, 0, new float[]{125, 260, 540});
    }
    @Test void forestEntryRequiredRouteFitsTwentySeconds() {
        measure("forest-entry", false, false, 0, new float[]{95, 225, 345, 535, 860});
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

    private Player player() { return new Player(assets, game, 0, 0); }

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
