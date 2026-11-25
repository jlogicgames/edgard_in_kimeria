package com.jlogicsoftware.kimeria.testsupport;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.ArtifactInventory;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.world.Level;
import com.jlogicsoftware.kimeria.input.GamepadInput;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.ui.TouchControls;
import com.jlogicsoftware.kimeria.ui.TouchGlyph;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/**
 * The headless libGDX environment the room-level tests share: a stubbed GL (real PNG decoding, no GPU upload),
 * empty assets, a proxy {@link GameContext} and a scripted keyboard. Call {@link #setUp()} before and
 * {@link #restoreGlobals()} after each test; {@link #roomOf} then builds a real {@link Level}.
 */
public final class HeadlessLevelHarness {
    public static final float STEP = 1f / 60f;
    /** What the proxy {@code GameContext} answers to {@code screenShakeDisabled()}: set it before building a Level. */
    public boolean screenShakeDisabled;
    public final Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    private TouchControls touch;
    private final GamepadInput pad = new GamepadInput();
    private final TrapWarnings warnings = new TrapWarnings();
    private final ArtifactInventory artifacts = new ArtifactInventory();
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public TextureRegion region(String path, com.badlogic.gdx.graphics.Texture.TextureFilter filter) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return font; }
    };
    /** Returned by {@code GameContext.rangedGlyph}, the pictogram on the touch Ranged button (#492): the flame unless a test sets another. */
    public TouchGlyph rangedGlyph = TouchGlyph.FIREBALL;
    /** Fills {@code GameContext.coachSignals}, what the touch tutorial reads from the hero (D139); null leaves the signals cleared. */
    public java.util.function.Consumer<com.jlogicsoftware.kimeria.ui.TouchCoach.Signals> coachSignals;
    /** Returned by {@code Assets.font}; null unless a test sets it before building the object that asks for a font. */
    public BitmapFont font;
    /** Returned by {@code GameContext.invulnerable}, the developer cheat (F2): off unless a test turns it on. */
    public boolean invulnerable;
    /** Returned by {@code GameContext.playSounds}; off unless a test turns it on to read {@link #sounds}. */
    public boolean playSounds;
    /** Every name passed to {@code GameContext.playSound}, in order. */
    public final java.util.List<String> sounds = new java.util.ArrayList<>();
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "language" -> Language.ENGLISH;
            case "isGameStarted" -> true;
            case "actionGlyph" -> TouchGlyph.SWORD;
            case "rangedGlyph" -> rangedGlyph;
            case "coachSignals" -> { if (coachSignals != null) coachSignals.accept((com.jlogicsoftware.kimeria.ui.TouchCoach.Signals) args[0]); yield null; }
            case "screenShakeDisabled" -> screenShakeDisabled;
            case "artifacts" -> artifacts;
            case "playSounds" -> playSounds;
            case "invulnerable" -> invulnerable;
            case "playSound" -> { sounds.add((String) args[0]); yield null; }
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });

    private Application previousApp;
    private Graphics previousGraphics;
    private Input previousInput;
    private Files previousFiles;
    private GL20 previousGl, previousGl20;

    public void setUp() {
        previousApp = Gdx.app; previousGraphics = Gdx.graphics; previousInput = Gdx.input; previousFiles = Gdx.files;
        previousGl = Gdx.gl; previousGl20 = Gdx.gl20;
        GdxNativesLoader.load();
        // Rendering builds the shared SoftDot and Shaders.whiteQuad textures: real pixels, stub only the GPU upload.
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (proxy, method, args) -> {
                Class<?> r = method.getReturnType();
                if (r == int.class) return 1;
                if (r == boolean.class) return false;
                if (r == float.class) return 0f;
                if (r == long.class) return 0L;
                return null;
            });
        Gdx.files = new HeadlessFiles();
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isKeyPressed" -> held.contains((int) args[0]);
            case "isKeyJustPressed" -> pressed.contains((int) args[0]);
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getDeltaTime")) return STEP;
                Class<?> r = method.getReturnType();
                if (r == boolean.class) return false;
                if (r == int.class) return 0;
                if (r == float.class) return 0f;
                return null;
            });
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getType" -> Application.ApplicationType.HeadlessDesktop;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
        touch = new TouchControls(assets, game, null, 640, 360);
    }

    public void restoreGlobals() {
        Gdx.app = previousApp; Gdx.graphics = previousGraphics; Gdx.input = previousInput; Gdx.files = previousFiles;
        Gdx.gl = previousGl; Gdx.gl20 = previousGl20;
    }

    /**
     * Turns the harness's touch controls into a touch device's (D139): available, so the touch tutorial and the touch variants of zone
     * messages treat the player as guided by touch. Call after {@link #setUp()}, before building a level.
     */
    public TouchControls touchDevice() {
        touch = new TouchControls(assets, game, null, 640, 360, true);
        return touch;
    }

    public Assets assets() { return assets; }
    public GameContext game() { return game; }

    public Level roomOf(String name, int index) { return roomOf(name, index, () -> emptyView); }
    /** The view the plain {@code roomOf} gives the level: empty, one shared instance (a supplier that made a rect per call would itself allocate per frame under the fairy guide, D87). */
    private final Rectangle emptyView = new Rectangle();

    /**
     * The level {@code map} entered at the room named {@code room} (its `Room` marker's name), walking the `Rooms` list in play
     * order, so a room inserted before it does not move it (#484). Fails when the map has no such room.
     */
    public Level room(String map, String room) { return room(map, room, () -> emptyView); }

    /** As {@link #room(String, String)} with the camera's visible world rect the level reads. */
    public Level room(String map, String room, java.util.function.Supplier<Rectangle> view) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), map, view);
        while (!room.equals(level.currentRoomName())) {
            if (!level.hasNextRoom()) throw new AssertionError(map + " has no room named " + room);
            level.enterNextRoom(new Player(assets, game, 0, 0));
        }
        level.roomRipple().clear(); // the harness walks past the game's room change: the entrance ripple (#553) is not part of the room under test
        return level;
    }

    /** As {@link #roomOf(String, int)} with the camera's visible world rect the level (and the fairy guide, D87) reads. */
    public Level roomOf(String name, int index, java.util.function.Supplier<Rectangle> view) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), name, view);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        level.roomRipple().clear(); // as in room(): the entrance ripple (#553) is not part of the room under test
        return level;
    }
}
