package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.GameContext;
import com.jlogicsoftware.kimeria.localization.Language;
import com.jlogicsoftware.kimeria.localization.Msg;

/**
 * On-screen controls for touch devices: a movement stick on the left and the three action
 * buttons on the right -- Jump, Ranged, and Action. The Action and Ranged buttons carry a pictogram
 * ({@link TouchGlyph}, D138) that follows the situation and no text; vertical movement is on the stick. In left-handed mode
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
 * 360-unit-wide space (still mirrored in left-handed mode); the large "rotate the phone" prompt ({@link RotatePrompt}, #493) fills the strip above
 * them: a pictogram of a phone that turns to landscape and back, and the hint in a large font, in every screen including play.
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
    static final float KNOB_SIZE = 32;
    private static final float STICK_DEADZONE = 0.3f;
    /** Interaction needs the stick more than about 60 degrees above or below horizontal (tan 60 = 1.732). */
    private static final float INTERACT_ANGLE_TAN = 1.732f;
    /** Once past the line the stick stays an interaction until it falls below about 55 degrees, so a finger resting on the line cannot chatter. */
    private static final float INTERACT_RELEASE_TAN = 1.428f;

    private static final float BUTTON_HIT_SCALE = 1.2f, SHOOT_BUTTON_SCALE = 0.85f;
    /** Light, near-opaque art (D55) so it reads on the dark bars and over the level background and ground; the pressed art is amber. */
    static final float CONTROL_ALPHA = 0.94f;
    private static final float PRESSED_SCALE = 0.92f;
    private static final String ART = "images/HUD/touch/";
    private static final float MILLIS_PER_SECOND = 1000f;
    private static final long PROMPT_LOOP_MILLIS = (long) (RotatePrompt.CYCLE * MILLIS_PER_SECOND);

    static final int BUTTON_ART_SIZE = 64;
    private static final float BUTTON_RADIUS = 32;
    private static final float JUMP_X = 590, JUMP_Y = 290;
    private static final float ACTION_X = 520, ACTION_Y = 318;
    private static final float SHOOT_X = 536, SHOOT_Y = 244;

    /** Pause button: drawn radius 13 (touching the top edge of the boss label), hit radius 26 = a 52 px target. */
    public static final float PAUSE_X = 320, PAUSE_Y = 19, PAUSE_RADIUS = 13, PAUSE_HIT_RADIUS = 26;
    /**
     * Story skip button (#336, D77): top right of the picture on the opening and the ending, same size and hit area as the
     * pause button. Its art has a white outer outline because it sits on the black story screen (D76).
     */
    public static final float SKIP_X = 606, SKIP_Y = 22, SKIP_RADIUS = 13;
    /** In portrait the picture is drawn at about 0.6 px per unit, so 40 units is still a 24 px radius target. */
    public static final float PAUSE_HIT_RADIUS_PORTRAIT = 40;
    /** In the side-bar layout the picture can be drawn well below 1 px per unit (D56); the hit circle keeps at least this radius in CSS pixels (backbuffer pixels on the web are {@code TouchLayout.pixelRatio()} per CSS pixel, D57). */
    public static final float PAUSE_MIN_HIT_PIXELS = 24;
    /** Portrait strip layout (controls space units): cluster centre offset from the strip middle, and each control's place. */
    static final float PORTRAIT_STICK_X = 80, PORTRAIT_JUMP_X = 310, PORTRAIT_ACTION_X = 240, PORTRAIT_SHOOT_X = 256;
    static final float PORTRAIT_JUMP_DY = 10, PORTRAIT_ACTION_DY = 38, PORTRAIT_SHOOT_DY = -36, PORTRAIT_CLUSTER_DY = 8;

    /**
     * Landscape side-bar layout (D54, controls space units = picture units, X from the window's left edge): the stick
     * sits in the left bar and the three buttons in one column in the right bar (mirrored in left-handed mode), each
     * centred in its bar, or as far out as fits when the bar is narrower than the control.
     */
    static final float SIDE_EDGE_MARGIN = 8, SIDE_STICK_GRAB = 60, SIDE_STICK_Y = 270;
    static final float SIDE_JUMP_Y = 272, SIDE_SHOOT_Y = 190, SIDE_ACTION_Y = 108;

    /**
     * The touch tutorial's art (#496, D139), in controls units: the hand (32 px, its fingertip at {@link #HAND_TIP_X} and {@link #HAND_TIP_Y}
     * in the idle art, {@link #HAND_TIP_DOWN_Y} in the pressed one), the amber chevrons ahead of the drag, and the ring round the Ranged
     * button, which grows from {@link #RING_SCALE_MIN} to {@link #RING_SCALE_MAX} times the button as it pulses.
     */
    static final float HAND_SIZE = 32, HAND_TIP_X = 13, HAND_TIP_Y = 2, HAND_TIP_DOWN_Y = 5, CHEVRON_SIZE = 16;
    private static final float CHEVRON_NEAR = 52, CHEVRON_FAR = 64, GHOST_ALPHA = 0.6f, RING_SCALE_MIN = 1.2f, RING_SCALE_MAX = 1.5f;
    /** Libgdx's rotation of the right-pointing chevron that makes it point up in the Y-down controls space. */
    private static final float CHEVRON_UP_DEGREES = -90f;

    private enum Control { NONE, STICK, JUMP, SHOOT, ACTION, PAUSE }

    private final GameContext game;
    private final Viewport viewport;
    private final TextureRegion handIdle, handPressed, chevron, ring;
    /** The touch tutorial (#496, D139): lives as long as the game, so a step that ended never returns in the session. */
    private final TouchCoach coach = new TouchCoach();
    private final TouchCoach.Signals coachSignals = new TouchCoach.Signals();
    /** The rotate prompt's art (#493, D140): the portrait phone code turns, and the amber arrow bent the way it turns. */
    private final TextureRegion rotatePhone, rotateArrow;
    private final TextureRegion base, jumpIcon, jumpDown, knob, knobDown, pauseIcon, pauseDown, skipIcon, skipDown;
    /** Idle and pressed art of every {@link TouchGlyph}, by ordinal; loaded here so a missing PNG fails at load, never mid-play (#492). */
    private final TextureRegion[] glyphs = new TextureRegion[TouchGlyph.values().length], glyphsDown = new TextureRegion[TouchGlyph.values().length];
    /** Only the portrait rotate prompt is text; its layout is rebuilt when the language changes, never per frame. */
    private final BitmapFont font;
    private GlyphLayout hintLayout;
    private Language hintLanguage;
    private final RotatePrompt prompt = new RotatePrompt();
    /** Test hook: when not negative, the prompt's loop clock in seconds instead of the real one. */
    private float promptClock = -1f;
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
    /** Stick up/down past {@link #INTERACT_ANGLE_TAN}: the vertical that may also interact (D126). */
    private float steepVertical;
    private boolean jumpHeld, shootHeld, actionHeld;
    private boolean prevShoot, prevAction, prevPause, pauseHeld, pauseEdge;
    private boolean shootEdge, actionEdge;
    private final boolean[] skipSeen = new boolean[MAX_POINTERS], skipClaimed = new boolean[MAX_POINTERS];
    private boolean skipHeld, skipEdge, prevSkip;
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
        // D55: light touch-only art (art/hud/touch).
        for (TouchGlyph glyph : TouchGlyph.values()) {
            glyphs[glyph.ordinal()] = assets.region(ART + glyph.artName() + ".png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
            glyphsDown[glyph.ordinal()] = assets.region(ART + glyph.artName() + "-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        }
        this.base = assets.region(ART + "base.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.jumpIcon = assets.region(ART + "jump.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.jumpDown = assets.region(ART + "jump-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.pauseIcon = assets.region(ART + "pause.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.pauseDown = assets.region(ART + "pause-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.skipIcon = assets.region(ART + "skip.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.skipDown = assets.region(ART + "skip-down.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.knob = assets.region(ART + "knob.png", 0, 0, (int) KNOB_SIZE, (int) KNOB_SIZE);
        this.knobDown = assets.region(ART + "knob-down.png", 0, 0, (int) KNOB_SIZE, (int) KNOB_SIZE);
        this.handIdle = assets.region(ART + "hand.png", 0, 0, (int) HAND_SIZE, (int) HAND_SIZE);
        this.handPressed = assets.region(ART + "hand-down.png", 0, 0, (int) HAND_SIZE, (int) HAND_SIZE);
        this.chevron = assets.region(ART + "chevron.png", 0, 0, (int) CHEVRON_SIZE, (int) CHEVRON_SIZE);
        this.ring = assets.region(ART + "ring.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.rotatePhone = assets.region(ART + "rotate-phone.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.rotateArrow = assets.region(ART + "rotate-arrow.png", 0, 0, BUTTON_ART_SIZE, BUTTON_ART_SIZE);
        this.font = assets.font(FontSizes.ROTATE_PROMPT);

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
            float cy = portraitClusterY(spaceHeight);
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

    /** Centre Y of the portrait cluster (the stick), in strip units: a little below the strip's middle. */
    static float portraitClusterY(float spaceHeight) { return spaceHeight / 2f + PORTRAIT_CLUSTER_DY; }

    /**
     * How far above the portrait cluster's centre anything the controls can show or take reaches (#493): the stick's grab circle, the Ranged button's
     * hit circle and its tutorial ring at full pulse, the stick-up tutorial's two chevrons and its ghost knob. {@link RotatePrompt#CLUSTER_REACH} must
     * be at least this, so the prompt never overlaps a control, a touch area or the tutorial.
     */
    static float clusterReach() {
        float shoot = -PORTRAIT_SHOOT_DY;
        return Math.max(Math.max(STICK_GRAB, shoot + BUTTON_RADIUS * BUTTON_HIT_SCALE), Math.max(shoot + BUTTON_RADIUS * SHOOT_BUTTON_SCALE * RING_SCALE_MAX,
            Math.max(CHEVRON_FAR + CHEVRON_SIZE / 2, TouchCoach.SLIDE_UNITS + KNOB_SIZE / 2)));
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
        float steepTan = steepVertical == vertical ? INTERACT_RELEASE_TAN : INTERACT_ANGLE_TAN;
        steepVertical = vertical != 0 && Math.abs(dy) >= steepTan * Math.abs(dx) ? vertical : 0f;
        knobDx = stick ? dx : 0f;
        knobDy = stick ? dy : 0f;
        jumpHeld = jump;
        shootHeld = shoot;
        actionHeld = action;
        shootEdge = shoot && !prevShoot;
        actionEdge = action && !prevAction;
        if (available && active) {
            coachSignals.clear();
            game.coachSignals(coachSignals);
            // No coach zone holds the hero: nothing is timed, so the frame time is not even read.
            coach.update(coachSignals.zones == 0 ? 0f : Gdx.graphics.getDeltaTime(), true, stick, coachSignals);
        }
        pauseHeld = pause;
        pauseEdge = pause && !prevPause;
        prevPause = pause;
        prevShoot = shoot;
        prevAction = action;
    }

    /**
     * Polls the story skip button; call every frame the story is shown. A finger that goes down on the button is claimed
     * by it, so the same touch is reported only as {@link #skipPressed()} and the caller can keep it from also turning a
     * page. Works at any time, including while the story's input lock runs.
     */
    public void updateSkip() {
        boolean held = false, edge = false;
        place();
        for (int p = 0; p < MAX_POINTERS; p++) {
            if (!available || !Gdx.input.isTouched(p)) { skipSeen[p] = false; skipClaimed[p] = false; continue; }
            if (!skipSeen[p]) {
                skipSeen[p] = true;
                touch.set(Gdx.input.getX(p), Gdx.input.getY(p));
                viewport.unproject(touch);
                skipClaimed[p] = inCircle(touch.x, touch.y, SKIP_X, SKIP_Y, pauseHitRadius());
                edge |= skipClaimed[p];
            }
            held |= skipClaimed[p];
        }
        skipHeld = held;
        skipEdge = edge && !prevSkip;
        prevSkip = held;
    }

    /** Edge-triggered: true for the one frame a finger goes down on the story skip button. */
    public boolean skipPressed() { return skipEdge; }

    /** Draws the story skip button (touch devices only); before the story's fade panel, so it fades with the page. */
    public void renderSkipButton(Batch batch) {
        if (!available) return;
        drawButton(batch, skipIcon, skipDown, SKIP_X, SKIP_Y, SKIP_RADIUS, skipHeld);
        batch.setColor(Color.WHITE);
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
        return sideBars() ? Math.max(PAUSE_HIT_RADIUS, PAUSE_MIN_HIT_PIXELS * layout.pixelRatio() / layout.pictureScale()) : PAUSE_HIT_RADIUS;
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
    /** Up steeply enough to count as an interaction press (about 60 degrees); climbing uses {@link #upHeld()}. */
    public boolean interactUpHeld() { return steepVertical < 0; }
    public boolean interactDownHeld() { return steepVertical > 0; }
    public boolean isAvailable() { return available; }
    /** The touch tutorial and the touch variants of zone messages (D139): a touch device whose hero no keyboard or gamepad has moved yet. */
    public boolean touchGuided() { return available && !coach.nonTouchSeen(); }
    /** The touch tutorial (D139): which step shows now and where it is in its loop. */
    public TouchCoach coach() { return coach; }
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
    public void renderPauseButton(Batch batch) {
        if (!available) return;
        float cx = x(PAUSE_X);
        drawButton(batch, pauseIcon, pauseDown, cx, PAUSE_Y, PAUSE_RADIUS, pauseHeld);
        batch.setColor(Color.WHITE);
    }

    public void render(Batch batch) {
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
    }

    /**
     * Portrait only: draws the "rotate the phone" prompt (in every screen, so a menu-bound player sees it too) and, when
     * {@code controls} is true, the stick and buttons, into the strip below the picture. Must be called between
     * {@code batch.begin()} and {@code end()}; leaves the batch and GL viewport as the game view had them.
     */
    public void renderStrip(Batch batch, boolean controls) {
        if (!available || !portrait()) return;
        place();
        renderInControlsSpace(batch, controls, true);
    }

    /** Draws into the layout's controls viewport (strip or whole window) and restores the game view afterwards. */
    private void renderInControlsSpace(Batch batch, boolean controls, boolean hint) {
        batch.end();
        layout.controlsViewport().apply();
        batch.setProjectionMatrix(layout.controlsViewport().getCamera().combined);
        batch.begin();
        if (hint) drawRotatePrompt(batch, controls);
        if (controls) {
            drawCluster(batch);
        }
        batch.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
    }

    /**
     * The portrait "rotate the phone" prompt (#493, D140), drawn before the cluster so the tutorial's hand and ring stay on top: the pictogram,
     * a phone that turns from portrait to landscape and back with the amber arrow bent the way it turns, and the hint under it in the large
     * font, white on the dark strip. Nothing here allocates: the regions are drawn rotated, the loop is read from the clock, and the text was laid
     * out when the language changed.
     */
    private void drawRotatePrompt(Batch batch, boolean controls) {
        layOutRotateHint(game.language());
        prompt.fit(spaceHeight, portraitClusterY(spaceHeight), controls, hintLayout.height);
        if (!prompt.shown) return;
        batch.setColor(Color.WHITE);
        if (prompt.icon) {
            float size = prompt.iconSize, x = spaceWidth / 2f - size / 2f, seconds = promptSeconds();
            batch.draw(rotatePhone, x, prompt.iconY, size / 2f, size / 2f, size, size, 1f, 1f, RotatePrompt.angle(seconds));
            batch.draw(rotateArrow, x, prompt.iconY, size / 2f, size / 2f, size, size, RotatePrompt.returning(seconds) ? -1f : 1f, 1f, 0f);
        }
        if (portraitTextHeight != spaceHeight || portraitTextWidth != spaceWidth) {
            portraitTextHeight = spaceHeight;
            portraitTextWidth = spaceWidth;
            portraitTextCamera.setToOrtho(false, spaceWidth, spaceHeight);
            portraitTextCamera.update();
            portraitTextProjection.set(portraitTextCamera.combined);
        }
        batch.setProjectionMatrix(portraitTextProjection);
        font.setColor(Color.WHITE);
        font.draw(batch, hintLayout, spaceWidth / 2f - RotatePrompt.TEXT_WIDTH / 2f, spaceHeight - prompt.textTop);
        batch.setProjectionMatrix(layout.controlsViewport().getCamera().combined);
    }

    /** The loop clock of the pictogram: the real time, or the test hook's. {@code TimeUtils} reads the clock without allocating. */
    private float promptSeconds() {
        return promptClock >= 0f ? promptClock : TimeUtils.millis() % PROMPT_LOOP_MILLIS / MILLIS_PER_SECOND;
    }

    /** Fixes the prompt's loop clock (seconds into the loop) for a test; a negative value gives the real clock back. */
    public void setPromptClock(float seconds) { promptClock = seconds; }

    /** Where the last prompt drawn sits and whether it was drawn: for tests. */
    RotatePrompt prompt() { return prompt; }

    /** Lays the hint out again only when the language changed (a state change, never a frame); the width is fixed, so the layout is too. */
    private void layOutRotateHint(Language lang) {
        if (hintLayout != null && hintLanguage == lang) return;
        hintLanguage = lang;
        hintLayout = new GlyphLayout(font, Msg.ROTATE_HINT.t(lang), Color.WHITE, RotatePrompt.TEXT_WIDTH, Align.center, true);
    }

    /** The stick and the three buttons, at the positions {@link #place()} chose. */
    private void drawCluster(Batch batch) {
        batch.setColor(1f, 1f, 1f, CONTROL_ALPHA);
        batch.draw(base, stickX - STICK_RADIUS, stickY - STICK_RADIUS, STICK_RADIUS * 2, STICK_RADIUS * 2);
        boolean stickHeld = knobDx != 0f || knobDy != 0f;
        batch.draw(stickHeld ? knobDown : knob, stickX + knobDx - KNOB_SIZE / 2, stickY + knobDy - KNOB_SIZE / 2, KNOB_SIZE, KNOB_SIZE);
        drawButton(batch, jumpIcon, jumpDown, jumpX, jumpY, BUTTON_RADIUS, jumpHeld);
        int action = game.actionGlyph().ordinal(), ranged = game.rangedGlyph().ordinal();
        drawButton(batch, glyphs[action], glyphsDown[action], actionX, actionY, BUTTON_RADIUS, actionHeld);
        drawButton(batch, glyphs[ranged], glyphsDown[ranged], shootX, shootY, BUTTON_RADIUS * SHOOT_BUTTON_SCALE, shootHeld);
        drawCoach(batch);
        batch.setColor(Color.WHITE);
    }

    /**
     * The touch tutorial (#496, D139) over the cluster: the hand presses the knob and drags a ghost knob to the screen right (always right,
     * whatever the handedness: the stick's horizontal is the same in both) or up, with amber chevrons ahead of it; or a ring pulses round the Ranged
     * button. Only the hand's art is flipped for the left-handed layout, so the finger points away from the screen's edge.
     */
    private void drawCoach(Batch batch) {
        TouchCoach.Step step = coach.step();
        if (step == null) return;
        if (step == TouchCoach.Step.RANGED) {
            float half = BUTTON_RADIUS * SHOOT_BUTTON_SCALE * (RING_SCALE_MIN + (RING_SCALE_MAX - RING_SCALE_MIN) * coach.pulse());
            batch.setColor(1f, 1f, 1f, coach.ringAlpha());
            batch.draw(ring, shootX - half, shootY - half, half * 2, half * 2);
            return;
        }
        boolean up = step == TouchCoach.Step.STICK_UP;
        float dirX = up ? 0f : 1f, dirY = up ? -1f : 0f, alpha = coach.handAlpha(), slide = coach.slide() * TouchCoach.SLIDE_UNITS;
        float chevrons = coach.chevronAlpha();
        if (chevrons > 0f) {
            batch.setColor(1f, 1f, 1f, chevrons);
            float nearX = up ? stickX : chevronX(stickX, spaceWidth, CHEVRON_NEAR), farX = up ? stickX : chevronX(stickX, spaceWidth, CHEVRON_FAR);
            drawChevron(batch, nearX, stickY + dirY * CHEVRON_NEAR, up);
            drawChevron(batch, farX, stickY + dirY * CHEVRON_FAR, up);
        }
        float kx = stickX + dirX * slide, ky = stickY + dirY * slide;
        boolean pressed = coach.pressed();
        batch.setColor(1f, 1f, 1f, GHOST_ALPHA * alpha);
        batch.draw(pressed ? knobDown : knob, kx - KNOB_SIZE / 2, ky - KNOB_SIZE / 2, KNOB_SIZE, KNOB_SIZE);
        batch.setColor(1f, 1f, 1f, alpha);
        float tipY = pressed ? HAND_TIP_DOWN_Y : HAND_TIP_Y;
        if (game.leftHanded()) batch.draw(pressed ? handPressed : handIdle, kx + HAND_TIP_X, ky - tipY, -HAND_SIZE, HAND_SIZE);
        else batch.draw(pressed ? handPressed : handIdle, kx - HAND_TIP_X, ky - tipY, HAND_SIZE, HAND_SIZE);
    }

    /**
     * Where a chevron ahead of a right-drag sits: {@code distance} to the screen right of the stick, but shifted left (the pair together, so their
     * spacing holds) as far as it takes to keep the far one on screen. Left-handed in a narrow side bar the stick is within {@value #CHEVRON_FAR} units
     * of the right edge; where the pair already fits this is {@code stickX + distance}.
     */
    static float chevronX(float stickX, float spaceWidth, float distance) {
        return Math.min(stickX + distance, spaceWidth - CHEVRON_SIZE / 2 - (CHEVRON_FAR - distance));
    }

    private void drawChevron(Batch batch, float cx, float cy, boolean up) {
        float half = CHEVRON_SIZE / 2;
        batch.draw(chevron, cx - half, cy - half, half, half, CHEVRON_SIZE, CHEVRON_SIZE, 1f, 1f, up ? CHEVRON_UP_DEGREES : 0f);
    }

    private void drawButton(Batch batch, TextureRegion idle, TextureRegion pressed, float cx, float cy, float r, boolean down) {
        batch.setColor(1f, 1f, 1f, down ? 1f : CONTROL_ALPHA);
        float s = down ? r * PRESSED_SCALE : r;
        batch.draw(down ? pressed : idle, cx - s, cy - s, s * 2, s * 2);
    }
}
