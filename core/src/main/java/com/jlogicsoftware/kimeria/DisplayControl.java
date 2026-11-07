package com.jlogicsoftware.kimeria;

/**
 * Page-level fullscreen supplied by a host that has one: the web launcher on browsers with the Fullscreen API
 * (Android Chrome, desktop browsers). iPhone Safari has none, so {@link #available()} is false there and the
 * Options entry is not offered (#226).
 */
public interface DisplayControl {
    boolean available();

    boolean active();

    /** Enters or leaves fullscreen; must run from a user gesture (the menu click). */
    void toggle();

    /**
     * Backbuffer pixels per CSS pixel. The web build draws into a canvas of physical pixels (D57), so on a phone
     * every game pixel count is 2 to 3 times the CSS size; sizes that must stay finger-sized (the pause button's
     * hit circle) are given in CSS pixels and scaled by this. 1 where the backbuffer is the window.
     */
    default float pixelRatio() { return 1f; }
}
