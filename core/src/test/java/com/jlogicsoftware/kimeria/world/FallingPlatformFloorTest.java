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
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.environment.CollisionBlock;
import com.jlogicsoftware.kimeria.entity.objects.FallingPlatform;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A hero riding a collapsing FallingPlatform down onto the solid floor under it (#355, D68). The platform keeps
 * falling as scenery, but it must not carry the hero into the floor, and the horizontal pass must not shove him to the
 * block's far edge. The pit cases (forest-1 rotten log, forest-0 traps-room platform) still carry the hero down.
 * Real shipped maps, ordinary keys. Y grows downward: the floor line is the floor block's top.
 */
class FallingPlatformFloorTest {
    private static final float STEP = 1f / 60f;
    private static final int HOLD_FRAMES = 20, SETTLE_FRAMES = 150;
    private static final float WALK_BOUND = 60f; // 20 frames at about 100 px/s is 33 px; a shove is 900+ px

    private final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "isGameStarted" -> true;
            case "artifacts" -> artifacts;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;

    @BeforeEach void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        GdxNativesLoader.load();
        HeadlessFiles files = new HeadlessFiles();
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class}, (proxy, method, args) -> {
            // The exact shipped map under an alias, omitting only forest-1's decorative sky/rain (as Forest1TrapsTest).
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
            (proxy, method, args) -> method.getName().equals("getDeltaTime") ? STEP : null);
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    @AfterEach void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
    }

    private Player player() { return new Player(assets, game, 0, 0); }

    private RangedStock emptyStock() { RangedStock s = new RangedStock(); s.restore(0); return s; }

    private void frame(Level level) { level.update(STEP); pressed.clear(); }

    private Level forest1() { return new Level(assets, game, player(), "replay-forest-1", Rectangle::new, emptyStock()); }

    private Level forest2() {
        Level l = new Level(assets, game, player(), "forest-2", Rectangle::new, emptyStock());
        l.enterNextRoom(player());
        for (int i = 0; i < 2; i++) frame(l);
        return l;
    }

    private Level forest0Traps() {
        Level l = new Level(assets, game, player(), "forest-0", Rectangle::new);
        for (int i = 0; i < 5; i++) l.enterNextRoom(player());
        return l;
    }

    private FallingPlatform platformAt(Level l, float x, float y) {
        return l.fallingPlatforms.stream().filter(f -> f.getX() == x && f.getY() == y).findFirst()
            .orElseThrow(() -> new AssertionError("no platform at " + x + "," + y + " among " + l.fallingPlatforms.stream().map(f -> f.getX() + "," + f.getY()).toList() + " room " + l.currentRoomBounds()));
    }

    /** Top of the highest solid block under the platform's span, at or below the platform's top: the floor line. */
    private float floorLine(Level l, FallingPlatform p) {
        float top = Float.MAX_VALUE;
        for (CollisionBlock b : l.collisionBlocks) {
            if (!b.isActive() || b.isPlatform() || b.isQuickSand() || b.isWall()) continue;
            if (b.getX() < p.getX() + p.getWidth() && b.getX() + b.getWidth() > p.getX() && b.getY() >= p.getY()) top = Math.min(top, b.getY());
        }
        return top;
    }

    /** Top of the highest solid block under the hero's body (at or below {@code from}), MAX_VALUE over a gap. */
    private float floorUnderBody(Level l, float from) {
        Rectangle body = l.player.hitboxBounds();
        float top = Float.MAX_VALUE;
        for (CollisionBlock b : l.collisionBlocks) {
            if (!b.isActive() || b.isPlatform() || b.isQuickSand() || b.isWall()) continue;
            if (b.getX() < body.x + body.width && b.getX() + b.getWidth() > body.x && b.getY() >= from) top = Math.min(top, b.getY());
        }
        return top;
    }

    private float feet(Level l) { Rectangle b = l.player.hitboxBounds(); return b.y + b.height; }

    /** Drops the hero onto the platform and runs until it has been triggered. */
    private void mount(Level l, FallingPlatform p) {
        l.player.position.set(p.getX() + 8, p.getY() - 60);
        for (int i = 0; i < 90 && !p.isFalling(); i++) frame(l);
        assertTrue(p.isFalling(), "the hero landed on the platform and triggered it");
        assertEquals(p.getY(), feet(l), .01f, "standing on the platform");
    }

    /** Frames after the trigger until the platform's top reaches the floor line, measured with no input. */
    private int crossingFrame(Level l, FallingPlatform p, float floor) {
        for (int f = 0; f < 300; f++) { if (p.getY() >= floor) return f; frame(l); }
        throw new AssertionError("the platform never reached the floor line");
    }

    /** Sweeps the key-press frame across the crossing window; the hero must always end on the floor line, near where he was. */
    private void ride(String name, Supplier<Level> make, float px, float py, int key) {
        Level probe = make.get();
        FallingPlatform pp = platformAt(probe, px, py);
        float floor = floorLine(probe, pp);
        assertTrue(floor < Float.MAX_VALUE && floor > py, name + ": a solid floor under the platform");
        mount(probe, pp);
        int crossing = crossingFrame(probe, pp, floor);
        for (int offset = -HOLD_FRAMES - 2; offset <= 6; offset++) {
            Level l = make.get();
            FallingPlatform p = platformAt(l, px, py);
            mount(l, p);
            for (int i = 0, run = Math.max(0, crossing + offset); i < run; i++) frame(l);
            float before = l.player.position.x;
            String where = name + " key " + key + " pressed " + offset + " frames from the crossing";
            for (int i = 0; i < HOLD_FRAMES; i++) {
                held.clear();
                if (key != 0) held.add(key);
                frame(l);
                assertTrue(feet(l) <= floorUnderBody(l, py) + .01f, where + ": sunk into the floor, feet " + feet(l));
            }
            held.clear();
            for (int i = 0; i < SETTLE_FRAMES; i++) frame(l);
            assertTrue(p.getY() > floor, where + ": the platform kept falling");
            assertEquals(floorUnderBody(l, py), feet(l), .01f, where + ": ends at the floor line");
            assertTrue(l.player.isOnGround(), where + ": on the ground");
            assertTrue(Math.abs(l.player.position.x - before) <= WALK_BOUND, where + ": no teleport, moved " + (l.player.position.x - before));
            assertTrue(l.player.position.x > 0, where + ": inside the level");
        }
    }

    @Test void forest1PlatformOverFloorReleasesTheHeroHoldingRight() { ride("forest-1 (128,272)", this::forest1, 128, 272, Input.Keys.D); }
    @Test void forest1PlatformOverFloorReleasesTheHeroHoldingLeft() { ride("forest-1 (128,272)", this::forest1, 128, 272, Input.Keys.A); }
    @Test void forest1PlatformOverFloorReleasesTheHeroWithNoInput() { ride("forest-1 (128,272)", this::forest1, 128, 272, 0); }
    @Test void forest1HighStepOverFloorReleasesTheHero() { ride("forest-1 (192,256)", this::forest1, 192, 256, Input.Keys.D); }
    @Test void forest2PlatformOverFloorReleasesTheHeroHoldingRight() { ride("forest-2 (1568,240)", this::forest2, 1568, 240, Input.Keys.D); }
    @Test void forest2PlatformOverFloorReleasesTheHeroHoldingLeft() { ride("forest-2 (1568,240)", this::forest2, 1568, 240, Input.Keys.A); }

    /** Over a pit nothing solid is beneath: the hero is carried with the platform exactly as before, until he leaves the room. */
    private void carriedDown(Level l, FallingPlatform p) {
        mount(l, p);
        float start = p.getY();
        int carried = 0;
        for (int i = 0; i < 400 && p.getY() < start + 48; i++) {
            frame(l);
            if (p.getY() > start + 4) { assertEquals(p.getY(), feet(l), 6f, "feet ride the platform top at " + p.getY()); carried++; } // tolerance: two frames of travel
        }
        assertTrue(carried > 12, "carried down with the platform: " + carried + " frames");
    }

    @Test void theRottenLogOverThePitStillCarriesTheHeroDown() {
        Level l = forest1();
        carriedDown(l, platformAt(l, 1088, 320));
    }

    @Test void theForest0TrapsRoomPlatformOverThePitStillCarriesTheHeroDown() {
        Level l = forest0Traps();
        carriedDown(l, platformAt(l, 4096, 320));
    }
}
