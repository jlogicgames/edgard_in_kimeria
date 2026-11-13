package com.jlogicsoftware.kimeria.ui;

/** The debug FPS counter text. It is rebuilt only when the rate changes, so asking for it every frame concatenates nothing. */
public final class FpsLabel {
    private int shown = -1;
    private String text = "";

    public String text(int framesPerSecond) {
        if (framesPerSecond != shown) { shown = framesPerSecond; text = framesPerSecond + " FPS"; }
        return text;
    }
}
