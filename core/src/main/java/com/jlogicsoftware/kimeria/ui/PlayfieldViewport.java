package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * A {@link FitViewport} that can sit at the top of the window instead of in the middle, so a portrait
 * phone gets the picture on top and the touch controls in the strip below it ({@link TouchLayout}).
 */
public class PlayfieldViewport extends FitViewport {
    private boolean topAligned;
    private float maxScale;

    public PlayfieldViewport(float worldWidth, float worldHeight, Camera camera) {
        super(worldWidth, worldHeight, camera);
    }

    public void setTopAligned(boolean topAligned) { this.topAligned = topAligned; }

    /** Caps the picture to this many pixels per unit, centred in the window (side-bar touch layout); 0 = fill the window. */
    public void setMaxScale(float maxScale) { this.maxScale = maxScale; }

    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        super.update(screenWidth, screenHeight, centerCamera);
        if (maxScale > 0 && getScreenWidth() > Math.round(getWorldWidth() * maxScale)) {
            int w = Math.round(getWorldWidth() * maxScale), h = Math.round(getWorldHeight() * maxScale);
            setScreenBounds((screenWidth - w) / 2, (screenHeight - h) / 2, w, h);
            apply(centerCamera);
        }
        if (topAligned) {
            // GL screen Y is bottom-up, so the top of the window is height minus the picture.
            setScreenY(screenHeight - getScreenHeight());
            apply(centerCamera);
        }
    }
}
