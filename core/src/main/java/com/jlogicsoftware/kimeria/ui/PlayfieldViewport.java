package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.FitViewport;

/**
 * A {@link FitViewport} that can sit at the top of the window instead of in the middle, so a portrait
 * phone gets the picture on top and the touch controls in the strip below it ({@link TouchLayout}).
 */
public class PlayfieldViewport extends FitViewport {
    private boolean topAligned;

    public PlayfieldViewport(float worldWidth, float worldHeight, Camera camera) {
        super(worldWidth, worldHeight, camera);
    }

    public void setTopAligned(boolean topAligned) { this.topAligned = topAligned; }

    @Override
    public void update(int screenWidth, int screenHeight, boolean centerCamera) {
        super.update(screenWidth, screenHeight, centerCamera);
        if (topAligned) {
            // GL screen Y is bottom-up, so the top of the window is height minus the picture.
            setScreenY(screenHeight - getScreenHeight());
            apply(centerCamera);
        }
    }
}
