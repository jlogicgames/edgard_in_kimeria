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
 * <p><b>Landscape side bars</b> (#226 follow-up, D54): a touch window wider than 16:9 leaves a bar left and right of the
 * height-fitted picture. When each bar is at least {@link #MIN_SIDE_BAR} units wide (in picture units: window pixels
 * divided by the picture's px-per-unit), the stick and the buttons are placed in those bars instead of over the
 * picture; the controls space is then the whole window in the picture's own scale ({@link #sideSpaceWidth()} x
 * {@link #gameHeight} units, the picture at X offset {@link #sideBar()}). A window that is not wider than that (16:9, 4:3
 * tablet) keeps the old layout, the controls over the picture.
 *
 * <p>Pure arithmetic, no GL: {@link #update} takes pixel sizes, {@link ControlsViewport#unproject} is plain
 * math, so it can be tested without a window. Screen Y here is top-down (input) except where a method says GL.
 */
public final class TouchLayout {
    /** Width of the controls space in its own units; the stick and buttons are laid out in it. */
    public static final float SPACE_WIDTH = 360f;
    /** The strip must be at least this tall (units) to fit the stick cluster and the rotate hint; else landscape layout. */
    public static final float MIN_SPACE_HEIGHT = 190f;

    /** A side bar narrower than this (picture units) is not worth a layout of its own; the controls stay over the picture. */
    public static final float MIN_SIDE_BAR = 24f;

    private final float gameWidth, gameHeight;
    private final ControlsViewport controls = new ControlsViewport();
    private boolean portrait, sideBars;
    private float sideBar;
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
        if (portrait) { controls.layout(width, free); controls.top(gameScreenHeight); return; }
        if (touch && height > 0) {
            float ppu = height / gameHeight;
            float bar = (width / ppu - gameWidth) / 2f;
            if (bar >= MIN_SIDE_BAR) {
                sideBars = true;
                sideBar = bar;
                controls.layoutSide(width, height, ppu, gameHeight);
            }
        }
    }

    /** True when the stick and buttons go into the left and right bars beside the picture (landscape). */
    public boolean sideBars() { return sideBars; }

    /** Width of one side bar in picture units (side bars only). */
    public float sideBar() { return sideBar; }

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
