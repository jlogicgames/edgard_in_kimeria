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
}
