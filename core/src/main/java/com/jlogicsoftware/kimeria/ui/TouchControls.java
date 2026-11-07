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
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.localization.Msg;

/**
 * On-screen controls for touch devices: a movement stick on the left and the three action
 * buttons on the right -- Jump, Ranged, and directional Action. Labels follow the selected
 * command, with vertical movement on the stick. In left-handed mode
 * ({@link GameContext#leftHanded()}) the whole layout is mirrored horizontally -- stick right,
 * buttons left -- for both drawing and hit areas. Drawn and hit-tested in fixed logical
 * (640x360) space. Does nothing on devices without a touch screen.
 *
 * <p>A small pause button sits at the top centre, in the gap between the coin/artifact HUD on the
 * left, the fireball stock on the right and the boss bar below it (#259). It is the touch
 * equivalent of Esc / gamepad Start. The centre of the screen is its own mirror image, so
 * left-handed mode keeps it in place and it never collides with the mirrored stick and buttons.
 * Unlike the stick and buttons it also works during healer dialogue ({@link #update(boolean, boolean)}).
 *
 * <p><b>Portrait</b> ({@link TouchLayout}, #226): with a {@link TouchLayout} set and the window tall enough, the
 * stick and the three buttons move out of the picture into the strip below it, laid out in that strip's own
 * 360-unit-wide space (still mirrored in left-handed mode); a one-line "rotate the phone" hint is drawn above them.
 * The pause button stays in the game view at logical (320, 19), but its hit circle grows so it stays a thumb-sized
 * target at the small portrait scale.
 *
 * <p><b>Landscape side bars</b> (D54): on a touch window wider than 16:9 ({@link TouchLayout#sideBars()}) the stick goes in
 * the left bar and the buttons in a column in the right bar (mirrored when left-handed), drawn in the window-wide
 * controls space at the picture's scale; when a bar is narrower than its control the control sits as far out as fits.
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

    private static final float BUTTON_HIT_SCALE = 1.2f, SHOOT_BUTTON_SCALE = 0.85f;
    /** Light, near-opaque art (D55) so it reads on the dark bars and over sky/ground; the pressed art is amber. */
    static final float CONTROL_ALPHA = 0.94f;
    private static final float HINT_SIDE_MARGIN = 24f;
    private static final float PRESSED_SCALE = 0.92f;
    /** Button labels sit on the cream button art, so they are dark navy (matches the art outline); the hint stays white. */
    static final Color LABEL_COLOR = new Color(0x14122bff);

    private static final String ART = "images/HUD/touch/";

    private static final int BUTTON_ART_SIZE = 64;
    private static final float BUTTON_RADIUS = 32;
    /**
     * Labels wrap inside the cream area of the light button art (D55): the dark outline and white ring take about 5 px
     * on every side, so the wrap width is the drawn diameter minus 14. Long words (be "Няма дзеяння" is 59 px on one line)
     * wrap to two lines instead of running onto the ring.
     */
    private static final float BUTTON_LABEL_PADDING = 14f;
    public static final float ACTION_LABEL_WIDTH = BUTTON_RADIUS * 2f - BUTTON_LABEL_PADDING;
    public static final float RANGED_LABEL_WIDTH = BUTTON_RADIUS * 2f * SHOOT_BUTTON_SCALE - BUTTON_LABEL_PADDING;
    private static final float JUMP_X = 590, JUMP_Y = 290;
    private static final float ACTION_X = 520, ACTION_Y = 318;
    private static final float SHOOT_X = 536, SHOOT_Y = 244;

    /** Pause button: drawn radius 13 (touching the top edge of the boss label), hit radius 26 = a 52 px target. */
    public static final float PAUSE_X = 320, PAUSE_Y = 19, PAUSE_RADIUS = 13, PAUSE_HIT_RADIUS = 26;
    /** In portrait the picture is drawn at about 0.6 px per unit, so 40 units is still a 24 px radius target. */
    public static final float PAUSE_HIT_RADIUS_PORTRAIT = 40;
    /** In the side-bar layout the picture can be drawn well below 1 px per unit (D56); the hit circle keeps at least this radius in pixels. */
    public static final float PAUSE_MIN_HIT_PIXELS = 24;
    /** Portrait strip layout (controls space units): cluster centre offset from the strip middle, and each control's place. */
    static final float PORTRAIT_STICK_X = 80, PORTRAIT_JUMP_X = 310, PORTRAIT_ACTION_X = 240, PORTRAIT_SHOOT_X = 256;
    static final float PORTRAIT_JUMP_DY = 10, PORTRAIT_ACTION_DY = 38, PORTRAIT_SHOOT_DY = -36, PORTRAIT_CLUSTER_DY = 8;
    static final float PORTRAIT_HINT_Y = 14;

    /**
     * Landscape side-bar layout (D54, controls space units = picture units, X from the window's left edge): the stick
     * sits in the left bar and the three buttons in one column in the right bar (mirrored in left-handed mode), each
     * centred in its bar, or as far out as fits when the bar is narrower than the control.
     */
    static final float SIDE_EDGE_MARGIN = 8, SIDE_STICK_GRAB = 60, SIDE_STICK_Y = 270;
    static final float SIDE_JUMP_Y = 272, SIDE_SHOOT_Y = 190, SIDE_ACTION_Y = 108;

    private enum Control { NONE, STICK, JUMP, SHOOT, ACTION, PAUSE }

    private final GameContext game;
    private final Viewport viewport;
    private final TextureRegion disc, discDown, base, jumpIcon, jumpDown, knob, knobDown, pauseIcon, pauseDown;
    private final BitmapFont font;
    private final Matrix4 textProjection;
    private final float logicalWidth, logicalHeight;
    private final boolean available;
    private boolean previousLayout;
    private TouchLayout layout;
    private float stickGrab = STICK_GRAB;
    private float stickX, stickY, jumpX, jumpY, actionX, actionY, shootX, shootY, spaceWidth, spaceHeight;
    private final Matrix4 portraitTextProjection = new Matrix4();
    private final OrthographicCamera portraitTextCamera = new OrthographicCamera();
    private float portraitTextHeight = -1, portraitTextWidth = -1;
    private final Vector2 controlsTouch = new Vector2();

    private final Control[] claimed = new Control[MAX_POINTERS];
    private final boolean[] seen = new boolean[MAX_POINTERS];
    private final Vector2 touch = new Vector2();

    private float horizontal, vertical;
    private boolean jumpHeld, shootHeld, actionHeld;
    private boolean prevShoot, prevAction, prevPause, pauseHeld, pauseEdge;
    private boolean shootEdge, actionEdge;
    private float knobDx, knobDy;

    public TouchControls(Assets assets, GameContext game, Viewport viewport, float logicalWidth, float logicalHeight) {
        this(assets, game, viewport, logicalWidth, logicalHeight, false);
    }

    /** The TeaVM backend cannot report MultitouchScreen; the web launcher supplies browser capability. */
    public TouchControls(Assets assets, GameContext game, Viewport viewport, float logicalWidth, float logicalHeight, boolean platformTouch) {
        this.game = game;
        previousLayout = game.leftHanded();
        this.viewport = viewport;
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        // D55: light touch-only art (art/hud/touch); Joystick/JumpButton/Knob/PauseButton.png stay for other users.
        this.disc = assets.region(ART + "disc.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.discDown = assets.region(ART + "disc-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.base = assets.region(ART + "base.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.jumpIcon = assets.region(ART + "jump.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.jumpDown = assets.region(ART + "jump-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.pauseIcon = assets.region(ART + "pause.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.pauseDown = assets.region(ART + "pause-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.knob = assets.region(ART + "knob.png", 0, 0, (int) KNOB_SIZE, (int) KNOB_SIZE);
        this.knobDown = assets.region(ART + "knob-down.png", 0, 0, (int) KNOB_SIZE, (int) KNOB_SIZE);
        this.font = assets.font(13);

        OrthographicCamera textCamera = new OrthographicCamera();
        textCamera.setToOrtho(false, logicalWidth, logicalHeight);
        textCamera.update();
        textProjection = textCamera.combined.cpy();

        Application.ApplicationType type = Gdx.app.getType();
        available = platformTouch || type == Application.ApplicationType.Android
            || type == Application.ApplicationType.iOS
            || Gdx.input.isPeripheralAvailable(Input.Peripheral.MultitouchScreen);
        java.util.Arrays.fill(claimed, Control.NONE);
    }

    /** Supplies the portrait layout; null (the default) keeps the stick and buttons over the picture. */
    public void setLayout(TouchLayout layout) { this.layout = layout; }

    private boolean portrait() { return layout != null && layout.portrait(); }

    private boolean sideBars() { return layout != null && layout.sideBars(); }

    /** Stick and buttons are drawn and hit-tested in the layout's own controls space (portrait strip or side bars). */
    private boolean ownSpace() { return portrait() || sideBars(); }

    /** Centre X of the stick in the left bar and of the button column in the right bar, right-handed. */
    static float sideStickX(float barUnits) { return Math.max(barUnits / 2f, STICK_RADIUS + SIDE_EDGE_MARGIN); }
    static float sideButtonInset(float barUnits) { return Math.max(barUnits / 2f, BUTTON_RADIUS * BUTTON_HIT_SCALE + SIDE_EDGE_MARGIN); }

    /** Fills the control positions for the current layout and handedness, in the space they are drawn and hit-tested in. */
    private void place() {
        stickGrab = STICK_GRAB;
        if (portrait()) {
            spaceWidth = TouchLayout.SPACE_WIDTH;
            spaceHeight = layout.spaceHeight();
            float cy = spaceHeight / 2f + PORTRAIT_CLUSTER_DY;
            stickX = mirror(PORTRAIT_STICK_X); stickY = cy;
            jumpX = mirror(PORTRAIT_JUMP_X); jumpY = cy + PORTRAIT_JUMP_DY;
            actionX = mirror(PORTRAIT_ACTION_X); actionY = cy + PORTRAIT_ACTION_DY;
            shootX = mirror(PORTRAIT_SHOOT_X); shootY = cy + PORTRAIT_SHOOT_DY;
        } else if (sideBars()) {
            spaceWidth = layout.sideSpaceWidth();
            spaceHeight = layout.sideSpaceHeight();
            float bar = layout.sideBar(), buttonsX = spaceWidth - sideButtonInset(bar), top = layout.sideTop();
            stickX = mirror(sideStickX(bar)); stickY = top + SIDE_STICK_Y;
            jumpX = mirror(buttonsX); jumpY = top + SIDE_JUMP_Y;
            shootX = mirror(buttonsX); shootY = top + SIDE_SHOOT_Y;
            actionX = mirror(buttonsX); actionY = top + SIDE_ACTION_Y;
            // The grab circle stops at the picture's edge, so a finger on the picture never starts the stick.
            stickGrab = Math.min(SIDE_STICK_GRAB, bar - sideStickX(bar));
        } else {
            spaceWidth = logicalWidth;
            spaceHeight = logicalHeight;
            stickX = x(STICK_X); stickY = STICK_Y;
            jumpX = x(JUMP_X); jumpY = JUMP_Y;
            actionX = x(ACTION_X); actionY = ACTION_Y;
            shootX = x(SHOOT_X); shootY = SHOOT_Y;
        }
    }

    private float mirror(float rightHandedX) {
        return game.leftHanded() ? spaceWidth - rightHandedX : rightHandedX;
    }

    /** @param active true while the game is being played; touches are ignored (and unclaimed) otherwise. */
    public void update(boolean active) {
        update(active, active);
    }

    /**
     * @param active   true while the game is being played; stick and action touches are ignored otherwise
     * @param pausable true while the pause button may be pressed (playing, or healer dialogue)
     */
    public void update(boolean active, boolean pausable) {
        if (previousLayout != game.leftHanded()) {
            java.util.Arrays.fill(claimed, Control.NONE);
            previousLayout = game.leftHanded();
        }
        boolean stick = false, jump = false, shoot = false, action = false, pause = false;
        float dx = 0, dy = 0;
        place();

        if (available) {
            for (int p = 0; p < MAX_POINTERS; p++) {
                if (!Gdx.input.isTouched(p)) {
                    seen[p] = false;
                    claimed[p] = Control.NONE;
                    continue;
                }
                touch.set(Gdx.input.getX(p), Gdx.input.getY(p));
                viewport.unproject(touch);
                // Portrait: the stick and buttons live in the strip below the picture, in their own space.
                controlsTouch.set(Gdx.input.getX(p), Gdx.input.getY(p));
                if (ownSpace()) layout.controlsViewport().unproject(controlsTouch); else controlsTouch.set(touch);
                if (!seen[p]) {
                    seen[p] = true;
                    Control hit = hitTest(touch.x, touch.y, controlsTouch.x, controlsTouch.y);
                    claimed[p] = active || (pausable && hit == Control.PAUSE) ? hit : Control.NONE;
                }
                if (!pausable && claimed[p] == Control.PAUSE) claimed[p] = Control.NONE;
                if (!active) {
                    if (claimed[p] == Control.PAUSE) pause = true; else claimed[p] = Control.NONE;
                    continue;
                }
                switch (claimed[p]) {
                    case STICK -> {
                        stick = true;
                        dx = Math.max(-STICK_RADIUS, Math.min(STICK_RADIUS, controlsTouch.x - stickX));
                        dy = Math.max(-STICK_RADIUS, Math.min(STICK_RADIUS, controlsTouch.y - stickY));
                    }
                    case JUMP -> jump = true;
                    case SHOOT -> shoot = true;
                    case ACTION -> action = true;
                    case PAUSE -> pause = true;
                    default -> { }
                }
            }
        }

        float axis = stick ? dx / STICK_RADIUS : 0f;
        horizontal = Math.abs(axis) > STICK_DEADZONE ? Math.signum(axis) : 0f;
        vertical = stick && Math.abs(dy / STICK_RADIUS) > STICK_DEADZONE ? Math.signum(dy) : 0;
        knobDx = stick ? dx : 0f;
        knobDy = stick ? dy : 0f;
        jumpHeld = jump;
        shootHeld = shoot;
        actionHeld = action;
        shootEdge = shoot && !prevShoot;
        actionEdge = action && !prevAction;
        pauseHeld = pause;
        pauseEdge = pause && !prevPause;
        prevPause = pause;
        prevShoot = shoot;
        prevAction = action;
    }

    /** Mirrors a right-handed layout X across the screen in left-handed mode. */
    private float x(float rightHandedX) {
        return game.leftHanded() ? logicalWidth - rightHandedX : rightHandedX;
    }

    /** (x, y) is in the game view's logical space (the pause button); (cx, cy) in the space of the stick and buttons. */
    private Control hitTest(float x, float y, float cx, float cy) {
        if (inCircle(x, y, x(PAUSE_X), PAUSE_Y, pauseHitRadius())) return Control.PAUSE;
        if (inCircle(cx, cy, jumpX, jumpY, BUTTON_RADIUS * BUTTON_HIT_SCALE)) return Control.JUMP;
        if (inCircle(cx, cy, actionX, actionY, BUTTON_RADIUS * BUTTON_HIT_SCALE)) return Control.ACTION;
        if (inCircle(cx, cy, shootX, shootY, BUTTON_RADIUS * BUTTON_HIT_SCALE)) return Control.SHOOT;
        if (inCircle(cx, cy, stickX, stickY, stickGrab)) return Control.STICK;
        return Control.NONE;
    }

    private float pauseHitRadius() {
        if (portrait()) return PAUSE_HIT_RADIUS_PORTRAIT;
        return sideBars() ? Math.max(PAUSE_HIT_RADIUS, PAUSE_MIN_HIT_PIXELS / layout.pictureScale()) : PAUSE_HIT_RADIUS;
    }

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    /** -1, 0 or 1, like the keyboard. */
    public float horizontal() {
        return horizontal;
    }

    public boolean upHeld() { return vertical < 0; }
    public boolean isAvailable() { return available; }
    public boolean downHeld() { return vertical > 0; }
    public boolean actionHeld() { return actionHeld; }
    public boolean shootHeld() { return shootHeld; }

    public boolean jumpHeld() {
        return jumpHeld;
    }

    /** Edge-triggered: true for the one frame the Shoot button goes down. */
    public boolean shootPressed() {
        return shootEdge;
    }

    /** Edge-triggered: true for the one frame the Action button goes down. */
    public boolean actionPressed() {
        return actionEdge;
    }

    /** Edge-triggered: true for the one frame a finger goes down on the pause button. */
    public boolean pausePressed() {
        return pauseEdge;
    }

    /** Draws only the pause button, for screens that show no stick (healer dialogue). */
    public void renderPauseButton(SpriteBatch batch) {
        if (!available) return;
        float cx = x(PAUSE_X);
        drawButton(batch, pauseIcon, pauseDown, cx, PAUSE_Y, PAUSE_RADIUS, pauseHeld);
        batch.setColor(Color.WHITE);
    }

    public void render(SpriteBatch batch) {
        if (!available) return;
        place();
        if (portrait()) {
            renderPauseButton(batch);
            renderStrip(batch, true);
            return;
        }

        if (sideBars()) {
            renderPauseButton(batch);
            renderInControlsSpace(batch, true, false);
            return;
        }

        drawCluster(batch);
        renderPauseButton(batch);

        var lang = game.language();
        // A copy: setProjectionMatrix() overwrites the batch's own matrix in place, so a bare reference
        // would already hold the text projection when it is "restored" below.
        Matrix4 previous = batch.getProjectionMatrix().cpy();
        batch.setProjectionMatrix(textProjection);
        label(batch, game.actionLabel().t(lang), actionX, actionY, ACTION_LABEL_WIDTH, logicalHeight, LABEL_COLOR);
        label(batch, game.rangedLabel().t(lang), shootX, shootY, RANGED_LABEL_WIDTH, logicalHeight, LABEL_COLOR);
        batch.setProjectionMatrix(previous);
    }

    /**
     * Portrait only: draws the "rotate the phone" hint (in every screen, so a menu-bound player sees it too) and, when
     * {@code controls} is true, the stick and buttons, into the strip below the picture. Must be called between
     * {@code batch.begin()} and {@code end()}; leaves the batch and GL viewport as the game view had them.
     */
    public void renderStrip(SpriteBatch batch, boolean controls) {
        if (!available || !portrait()) return;
        place();
        renderInControlsSpace(batch, controls, true);
    }

    /** Draws into the layout's controls viewport (strip or whole window) and restores the game view afterwards. */
    private void renderInControlsSpace(SpriteBatch batch, boolean controls, boolean hint) {
        batch.end();
        layout.controlsViewport().apply();
        batch.setProjectionMatrix(layout.controlsViewport().getCamera().combined);
        batch.begin();
        if (controls) {
            drawCluster(batch);
        }
        if (portraitTextHeight != spaceHeight || portraitTextWidth != spaceWidth) {
            portraitTextHeight = spaceHeight;
            portraitTextWidth = spaceWidth;
            portraitTextCamera.setToOrtho(false, spaceWidth, spaceHeight);
            portraitTextCamera.update();
            portraitTextProjection.set(portraitTextCamera.combined);
        }
        batch.setProjectionMatrix(portraitTextProjection);
        var lang = game.language();
        if (hint) label(batch, Msg.ROTATE_HINT.t(lang), spaceWidth / 2f, PORTRAIT_HINT_Y, spaceWidth - HINT_SIDE_MARGIN * 2f, spaceHeight, Color.WHITE);
        if (controls) {
            label(batch, game.actionLabel().t(lang), actionX, actionY, ACTION_LABEL_WIDTH, spaceHeight, LABEL_COLOR);
            label(batch, game.rangedLabel().t(lang), shootX, shootY, RANGED_LABEL_WIDTH, spaceHeight, LABEL_COLOR);
        }
        batch.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
    }

    /** The stick and the three buttons, at the positions {@link #place()} chose. */
    private void drawCluster(SpriteBatch batch) {
        batch.setColor(1f, 1f, 1f, CONTROL_ALPHA);
        batch.draw(base, stickX - STICK_RADIUS, stickY - STICK_RADIUS, STICK_RADIUS * 2, STICK_RADIUS * 2);
        boolean stickHeld = knobDx != 0f || knobDy != 0f;
        batch.draw(stickHeld ? knobDown : knob, stickX + knobDx - KNOB_SIZE / 2, stickY + knobDy - KNOB_SIZE / 2, KNOB_SIZE, KNOB_SIZE);
        drawButton(batch, jumpIcon, jumpDown, jumpX, jumpY, BUTTON_RADIUS, jumpHeld);
        drawButton(batch, disc, discDown, actionX, actionY, BUTTON_RADIUS, actionHeld);
        drawButton(batch, disc, discDown, shootX, shootY, BUTTON_RADIUS * SHOOT_BUTTON_SCALE, shootHeld);
        batch.setColor(Color.WHITE);
    }

    private void drawButton(SpriteBatch batch, TextureRegion idle, TextureRegion pressed, float cx, float cy, float r, boolean down) {
        batch.setColor(1f, 1f, 1f, down ? 1f : CONTROL_ALPHA);
        float s = down ? r * PRESSED_SCALE : r;
        batch.draw(down ? pressed : idle, cx - s, cy - s, s * 2, s * 2);
    }

    /** Centered label; {@code yFromTop} is converted to the Y-up text projection, as in {@link Hud}. */
    private void label(SpriteBatch batch, String text, float cx, float yFromTop, float width, float height, Color color) {
        GlyphLayout glyphs = new GlyphLayout(font, text, color, width, Align.center, true);
        font.setColor(color);
        font.draw(batch, glyphs, cx - width / 2f, height - yFromTop + glyphs.height / 2f);
    }
}
