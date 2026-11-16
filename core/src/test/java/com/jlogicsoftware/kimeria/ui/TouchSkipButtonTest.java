package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** The touch skip button of the story screens (#336, D77): its hit area, its edge and that it is touch only. */
class TouchSkipButtonTest {
    private final Vector2 finger = new Vector2();
    private boolean down;
    private Input previousInput;
    private Application previousApp;
    private final Assets assets = new Assets() {
        @Override public TextureRegion region(String path, float x, float y, float w, float h) { return new TextureRegion(); }
        @Override public BitmapFont font(int size) { return null; }
    };
    private final GameContext game = (GameContext) Proxy.newProxyInstance(GameContext.class.getClassLoader(),
        new Class<?>[]{GameContext.class}, (proxy, method, args) -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null);
    private final Viewport identity = new Viewport() { @Override public Vector2 unproject(Vector2 v) { return v; } };

    @BeforeEach void setUp() {
        com.badlogic.gdx.utils.GdxNativesLoader.load();
        previousInput = Gdx.input; previousApp = Gdx.app;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class}, (proxy, method, args) -> switch (method.getName()) {
            case "isTouched" -> down && (int) args[0] == 0;
            case "getX" -> (int) finger.x;
            case "getY" -> (int) finger.y;
            default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
        });
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.Android : null);
    }

    @AfterEach void restore() { Gdx.input = previousInput; Gdx.app = previousApp; }

    private boolean tapAt(TouchControls controls, float x, float y) {
        down = false; controls.updateSkip();
        finger.set(x, y); down = true; controls.updateSkip();
        boolean pressed = controls.skipPressed();
        down = false; controls.updateSkip();
        return pressed;
    }

    @Test void aTapInsideTheHitCircleSkipsAndOneOutsideDoesNot() {
        TouchControls controls = new TouchControls(assets, game, identity, 640, 360, true);
        assertTrue(tapAt(controls, TouchControls.SKIP_X, TouchControls.SKIP_Y));
        assertTrue(tapAt(controls, TouchControls.SKIP_X + TouchControls.PAUSE_HIT_RADIUS - 1, TouchControls.SKIP_Y));
        assertFalse(tapAt(controls, TouchControls.SKIP_X + TouchControls.PAUSE_HIT_RADIUS + 1, TouchControls.SKIP_Y));
        assertFalse(tapAt(controls, 320, 200), "the middle of the page is not the button");
        assertFalse(tapAt(controls, TouchControls.PAUSE_X, TouchControls.PAUSE_Y), "the pause spot is not the skip button");
    }

    @Test void theEdgeIsOneFrameAndADragOntoTheButtonDoesNotCount() {
        TouchControls controls = new TouchControls(assets, game, identity, 640, 360, true);
        down = false; controls.updateSkip();
        finger.set(TouchControls.SKIP_X, TouchControls.SKIP_Y); down = true;
        controls.updateSkip();
        assertTrue(controls.skipPressed());
        controls.updateSkip();
        assertFalse(controls.skipPressed(), "holding the finger on the button is not a second press");
        down = false; controls.updateSkip();
        finger.set(320, 200); down = true; controls.updateSkip();
        finger.set(TouchControls.SKIP_X, TouchControls.SKIP_Y); controls.updateSkip();
        assertFalse(controls.skipPressed(), "a finger that started elsewhere is not the button's");
    }

    @Test void withoutATouchScreenTheButtonNeverReactsOrDraws() {
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class<?>[]{Application.class},
            (proxy, method, args) -> method.getName().equals("getType") ? Application.ApplicationType.HeadlessDesktop : null);
        TouchControls controls = new TouchControls(assets, game, identity, 640, 360, false);
        assertFalse(controls.isAvailable());
        assertFalse(tapAt(controls, TouchControls.SKIP_X, TouchControls.SKIP_Y));
    }
}
