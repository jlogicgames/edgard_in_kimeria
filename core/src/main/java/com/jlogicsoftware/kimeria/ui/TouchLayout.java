package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * Where the game view and the touch controls go on a given window (#226).
 *
 * <p>Landscape (or any window too short below the picture): the game fills the window as before and the
 * controls are drawn over it in 640x360 logical space. <b>Portrait</b>, with a touch screen and enough
 * room: the 640x360 picture is fitted to the window <em>width</em> and pinned to the top, and the whole
 * remaining strip below it becomes the <em>controls space</em>, {@link #SPACE_WIDTH} units wide (about
 * 1.1 px per unit on a 390 px phone, so the stick and buttons keep a thumb-sized target) and
 * {@link #spaceHeight()} units high. Nothing is drawn over the playfield. The top-centre pause button
 * (D52) stays in the game view; this class only owns the strip.
 *
 *
 * <p><b>Landscape side bars</b> (D54, reworked in D56): on a touch window that is not portrait the stick and the buttons
 * <em>always</em> sit in a bar left and right of the picture and never over it. Each bar is at least
 * {@link #REQUIRED_BAR} picture units wide (the stick needs 2 x (44 + 8) = 104, the button column 2 x (38.4 + 8) = 93), so
 * the picture is fitted to {@code min(height / 360, width / (640 + 2 x 104))} pixels per unit and centred; where the
 * window is wider than that the bars simply take the rest. The controls space is the whole window in the picture's own
 * scale ({@link #sideSpaceWidth()} x {@link #sideSpaceHeight()} units, the picture at offset {@link #sideBar()},
 * {@link #sideTop()}). Worst case (any window not wider than 640 + 208 : 360) the picture is 640 / 848 = 75.5% of the
 * full fit, which is why no smaller-picture fallback is needed; {@link #pictureScale()} is the scale the game view must
 * be capped to ({@link PlayfieldViewport#setMaxScale}).
 *
 * <p>Pure arithmetic, no GL: {@link #update} takes pixel sizes, {@link ControlsViewport#unproject} is plain
 * math, so it can be tested without a window. Screen Y here is top-down (input) except where a method says GL.
 */
public final class TouchLayout {
    /** Width of the controls space in its own units; the stick and buttons are laid out in it. */
    public static final float SPACE_WIDTH = 360f;
    /** The strip must be at least this tall (units) to fit the stick cluster and the rotate hint; else landscape layout. */
    public static final float MIN_SPACE_HEIGHT = 190f;

    /** Every side bar is at least this wide in picture units: the stick needs 2 x (radius 44 + margin 8); the button column needs less (93). */
    public static final float REQUIRED_BAR = 104f;

    private final float gameWidth, gameHeight;
    private final ControlsViewport controls = new ControlsViewport();
    private boolean portrait, sideBars;
    private float sideBar, sideTop, pictureScale;
    private int screenWidth, screenHeight, gameScreenHeight;

    public TouchLayout(float gameWidth, float gameHeight) {
        this.gameWidth = gameWidth;
        this.gameHeight = gameHeight;
    }

    /** @param touch whether the device has a touch screen; without one the layout is always the plain fit */
    public void update(int width, int height, boolean touch) {
        screenWidth = width;
        screenHeight = height;
        gameScreenHeight = Math.round(width * gameHeight / gameWidth);
        int free = height - gameScreenHeight;
        portrait = touch && gameScreenHeight <= height && free * SPACE_WIDTH / Math.max(1, width) >= MIN_SPACE_HEIGHT;
        sideBars = false;
        sideBar = 0;
        sideTop = 0;
        pictureScale = 0;
        if (portrait) { controls.layout(width, free); controls.top(gameScreenHeight); return; }
        if (touch && width > 0 && height > 0) {
            float ppu = Math.min(height / gameHeight, width / (gameWidth + 2 * REQUIRED_BAR));
            sideBars = true;
            pictureScale = ppu;
            sideBar = (width / ppu - gameWidth) / 2f;
            sideTop = (height / ppu - gameHeight) / 2f;
            controls.layoutSide(width, height, ppu, height / ppu);
        }
    }

    /** True when the stick and buttons go into the left and right bars beside the picture (landscape). */
    public boolean sideBars() { return sideBars; }

    /** Width of one side bar in picture units (side bars only). */
    public float sideBar() { return sideBar; }

    /** Pixels per picture unit the game view is drawn at in the side-bar layout (0 when not in it). */
    public float pictureScale() { return pictureScale; }

    /** Height of the whole window in picture units (side bars only). */
    public float sideSpaceHeight() { return controls.getWorldHeight(); }

    /** Space above the picture in picture units: the picture is centred vertically (side bars only). */
    public float sideTop() { return sideTop; }

    /** Width of the whole window in picture units, the controls space width (side bars only). */
    public float sideSpaceWidth() { return controls.getWorldWidth(); }

    public boolean portrait() { return portrait; }

    /** Height in pixels of the game view when fitted to the width (portrait only). */
    public int gameScreenHeight() { return gameScreenHeight; }

    /** Height in pixels of the strip below the game view (portrait only). */
    public int controlsScreenHeight() { return screenHeight - gameScreenHeight; }

    /** Height of the controls space in its units (portrait only). */
    public float spaceHeight() { return controls.getWorldHeight(); }

    public ControlsViewport controlsViewport() { return controls; }

    /** The strip below the picture, mapped to {@code SPACE_WIDTH} x {@link #spaceHeight()} Y-down units. */
    public static final class ControlsViewport extends Viewport {
        private float pixelsPerUnit = 1f;
        private int stripTop;

        ControlsViewport() {
            setCamera(new OrthographicCamera());
        }

        void layout(int width, int stripHeight) {
            pixelsPerUnit = width / SPACE_WIDTH;
            stripTop = 0;
            setWorldSize(SPACE_WIDTH, stripHeight / pixelsPerUnit);
            // GL screen Y is bottom-up: the strip is the bottom of the window.
            setScreenBounds(0, 0, width, stripHeight);
            ((OrthographicCamera) getCamera()).setToOrtho(true, SPACE_WIDTH, stripHeight / pixelsPerUnit);
        }

        /** The whole window at the picture's scale, X from the window's left edge, Y from its top (side bars). */
        void layoutSide(int width, int height, float ppu, float unitsHigh) {
            pixelsPerUnit = ppu;
            stripTop = 0;
            setWorldSize(width / ppu, unitsHigh);
            setScreenBounds(0, 0, width, height);
            ((OrthographicCamera) getCamera()).setToOrtho(true, width / ppu, unitsHigh);
        }

        /** Tells the strip where it starts, in top-down screen pixels, for {@link #unproject}. */
        void top(int topDownY) { stripTop = topDownY; }

        /** Top-down screen pixels in, controls-space units out. Does not need {@code Gdx.graphics}. */
        @Override
        public Vector2 unproject(Vector2 screenCoords) {
            screenCoords.set(screenCoords.x / pixelsPerUnit, (screenCoords.y - stripTop) / pixelsPerUnit);
            return screenCoords;
        }
    }
}
