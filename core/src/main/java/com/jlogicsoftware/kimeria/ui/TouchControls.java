package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.localization.Msg;

/**
 * On-screen controls for touch devices: a movement stick on the left and the three action
 * buttons on the right -- Jump, Shoot, and the context-sensitive Attack/Interact button, whose
 * label follows {@link GameContext#interactAvailable()}. In left-handed mode
 * ({@link GameContext#leftHanded()}) the whole layout is mirrored horizontally -- stick right,
 * buttons left -- for both drawing and hit areas. Drawn and hit-tested in fixed logical
 * (640x360) space. Does nothing on devices without a touch screen.
 *
 * <p>A finger only drives a control if it <em>started</em> on it while the game was being
 * played, so the tap that dismisses a menu can't leak into the first frame of play. Call
 * {@link #update(boolean)} once per frame before reading anything.
 */
public class TouchControls {
    private static final int MAX_POINTERS = 5;

    private static final float STICK_X = 80, STICK_Y = 280, STICK_RADIUS = 44, STICK_GRAB = 76;
    private static final float KNOB_SIZE = 32;
    private static final float STICK_DEADZONE = 0.3f;

    private static final float BUTTON_RADIUS = 32;
    private static final float JUMP_X = 590, JUMP_Y = 290;
    private static final float ACTION_X = 520, ACTION_Y = 318;
    private static final float SHOOT_X = 536, SHOOT_Y = 244;

    private enum Control { NONE, STICK, JUMP, SHOOT, ACTION }

    private final GameContext game;
    private final Viewport viewport;
    private final TextureRegion disc, jumpIcon, knob;
    private final BitmapFont font;
    private final Matrix4 textProjection;
    private final float logicalWidth, logicalHeight;
    private final boolean available;

    private final Control[] claimed = new Control[MAX_POINTERS];
    private final boolean[] seen = new boolean[MAX_POINTERS];
    private final Vector2 touch = new Vector2();

    private float horizontal;
    private boolean jumpHeld, shootHeld, actionHeld;
    private boolean prevShoot, prevAction;
    private boolean shootEdge, actionEdge;
    private float knobDx;

    public TouchControls(Assets assets, GameContext game, Viewport viewport, float logicalWidth, float logicalHeight) {
        this.game = game;
        this.viewport = viewport;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.disc = assets.region("images/HUD/Joystick.png", 0, 0, 64, 64);
        this.jumpIcon = assets.region("images/HUD/JumpButton.png", 0, 0, 64, 64);
        this.knob = assets.region("images/HUD/Knob.png", 0, 0, 32, 32);
        this.font = assets.font(13);

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight);
        textCamera.update();
        textProjection = textCamera.combined.cpy();

        Application.ApplicationType type = Gdx.app.getType();
        available = type == Application.ApplicationType.Android
            || type == Application.ApplicationType.iOS
            || Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen);
        java.util.Arrays.fill(claimed, Control.NONE);
    }

    /** @param active true while the game is being played; touches are ignored (and unclaimed) otherwise. */
    public void update(boolean active) {
        boolean stick = false, jump = false, shoot = false, action = false;
        float dx = 0;
        float stickX = x(STICK_X);

        if (available) {
            for (int p = 0; p < MAX_POINTERS; p++) {
                if (!Gdx.input.isTouched(p)) {
                    seen[p] = false;
                    claimed[p] = Control.NONE;
                    continue;
                }
                touch.set(Gdx.input.getX(p), Gdx.input.getY(p));
                viewport.unproject(touch);
                if (!seen[p]) {
                    seen[p] = true;
                    claimed[p] = active ? hitTest(touch.x, touch.y) : Control.NONE;
                }
                if (!active) continue;
                switch (claimed[p]) {
                    case STICK -> {
                        stick = true;
                        dx = Math.max(-STICK_RADIUS, Math.min(STICK_RADIUS, touch.x - stickX));
                    }
                    case JUMP -> jump = true;
                    case SHOOT -> shoot = true;
                    case ACTION -> action = true;
                    default -> { }
                }
            }
        }

        float axis = stick ? dx / STICK_RADIUS : 0f;
        horizontal = Math.abs(axis) > STICK_DEADZONE ? Math.signum(axis) : 0f;
        knobDx = stick ? dx : 0f;
        jumpHeld = jump;
        shootHeld = shoot;
        actionHeld = action;
        shootEdge = shoot && !prevShoot;
        actionEdge = action && !prevAction;
        prevShoot = shoot;
        prevAction = action;
    }

    /** Mirrors a right-handed layout X across the screen in left-handed mode. */
    private float x(float rightHandedX) {
        return game.leftHanded() ? logicalWidth - rightHandedX : rightHandedX;
    }

    private Control hitTest(float x, float y) {
        if (inCircle(x, y, x(JUMP_X), JUMP_Y, BUTTON_RADIUS * 1.2f)) return Control.JUMP;
        if (inCircle(x, y, x(ACTION_X), ACTION_Y, BUTTON_RADIUS * 1.2f)) return Control.ACTION;
        if (inCircle(x, y, x(SHOOT_X), SHOOT_Y, BUTTON_RADIUS * 1.2f)) return Control.SHOOT;
        if (inCircle(x, y, x(STICK_X), STICK_Y, STICK_GRAB)) return Control.STICK;
        return Control.NONE;
    }

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** -1, 0 or 1, like the keyboard. */
    public float horizontal() {
        return horizontal;
    }

    public boolean jumpHeld() {
        return jumpHeld;
    }

    /** Edge-triggered: true for the one frame the Shoot button goes down. */
    public boolean shootPressed() {
        return shootEdge;
    }

    /** Edge-triggered: true for the one frame the Attack/Interact button goes down. */
    public boolean actionPressed() {
        return actionEdge;
    }

    public void render(SpriteBatch batch) {
        if (!available) return;

        float stickX = x(STICK_X), jumpX = x(JUMP_X), actionX = x(ACTION_X), shootX = x(SHOOT_X);

        batch.setColor(1f, 1f, 1f, 0.55f);
        batch.draw(disc, stickX - STICK_RADIUS, STICK_Y - STICK_RADIUS, STICK_RADIUS * 2, STICK_RADIUS * 2);
        batch.setColor(1f, 1f, 1f, 0.85f);
        batch.draw(knob, stickX + knobDx - KNOB_SIZE / 2, STICK_Y - KNOB_SIZE / 2, KNOB_SIZE, KNOB_SIZE);

        drawButton(batch, jumpIcon, jumpX, JUMP_Y, BUTTON_RADIUS, jumpHeld);
        drawButton(batch, disc, actionX, ACTION_Y, BUTTON_RADIUS, actionHeld);
        drawButton(batch, disc, shootX, SHOOT_Y, BUTTON_RADIUS * 0.85f, shootHeld);
        batch.setColor(Color.WHITE);

        var lang = game.language();
        String action = (game.interactAvailable() ? Msg.INTERACT : Msg.ATTACK).t(lang);
        Matrix4 previous = batch.getProjectionMatrix();
        batch.setProjectionMatrix(textProjection);
        label(batch, action, actionX, ACTION_Y);
        label(batch, Msg.SHOOT.t(lang), shootX, SHOOT_Y);
        batch.setProjectionMatrix(previous);
    }

    private void drawButton(SpriteBatch batch, TextureRegion region, float cx, float cy, float r, boolean down) {
        batch.setColor(1f, 1f, 1f, down ? 0.9f : 0.55f);
        float s = down ? r * 0.92f : r;
        batch.draw(region, cx - s, cy - s, s * 2, s * 2);
    }

    /** Centered label; {@code yFromTop} is converted to the Y-up text projection, as in {@link Hud}. */
    private void label(SpriteBatch batch, String text, float cx, float yFromTop) {
        GlyphLayout layout = new GlyphLayout(font, text);
        font.setColor(Color.WHITE);
        font.draw(batch, layout, cx - layout.width / 2f, logicalHeight - yFromTop + layout.height / 2f);
    }
}
