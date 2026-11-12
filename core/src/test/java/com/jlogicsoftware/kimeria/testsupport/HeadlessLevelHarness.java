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
        @Override public BitmapFont font(int size) { return font; }
    };
    /** Returned by {@code Assets.font}; null unless a test sets it before building the object that asks for a font. */
    public BitmapFont font;
    private final GameContext game = (GameContext) Proxy.newProxyInstance(
        GameContext.class.getClassLoader(), new Class<?>[]{GameContext.class}, (proxy, method, args) -> switch (method.getName()) {
            case "logicalResolution" -> new Vector2(640, 360);
            case "trapWarnings" -> warnings;
            case "gamepad" -> pad;
            case "touch" -> touch;
            case "language" -> Language.ENGLISH;
            case "isGameStarted" -> true;
            case "screenShakeDisabled" -> screenShakeDisabled;
            case "artifacts" -> artifacts;
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
        // forest-1 builds a sky texture: decode the real PNG, stub only the GPU upload.
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

    public Assets assets() { return assets; }
    public GameContext game() { return game; }

    public Level roomOf(String name, int index) {
        Level level = new Level(assets, game, new Player(assets, game, 0, 0), name, Rectangle::new);
        for (int i = 0; i < index; i++) level.enterNextRoom(new Player(assets, game, 0, 0));
        return level;
    }
}
