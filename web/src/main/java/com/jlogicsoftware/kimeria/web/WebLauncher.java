package com.jlogicsoftware.kimeria.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.jlogicsoftware.kimeria.KimeriaGame;
import org.teavm.jso.JSBody;

public class WebLauncher {
    public static void main(String[] args) {
        launch(false);
    }

    static void launch(boolean debugMode) {
        WebApplicationConfiguration config = new WebApplicationConfiguration();
        // Fill the canvas and resize with the browser window, same intent as
        // the desktop launcher's resizable window.
        config.width = 0;
        config.height = 0;
        new WebApplication(new KimeriaGame(hasTouchScreen(), debugMode), config);
    }

    @JSBody(script = "return navigator.maxTouchPoints > 0;")
    private static native boolean hasTouchScreen();
}
