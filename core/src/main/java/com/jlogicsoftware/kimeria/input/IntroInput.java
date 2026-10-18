package com.jlogicsoftware.kimeria.input;

import com.badlogic.gdx.Input;

/** Tracks each key separately so menu holds and OS key repeats cannot skip the intro. */
public final class IntroInput {
    private final boolean[] held = new boolean[Input.Keys.MAX_KEYCODE + 1];
    private boolean touched, touchEdge;
    private final boolean[] edges = new boolean[Input.Keys.MAX_KEYCODE + 1];

    /** Snapshot the menu's held inputs when the intro is opened. */
    public void reset(Input input) {
        for (int key = 0; key < held.length; key++) held[key] = input.isKeyPressed(key);
        touched = input.isTouched();
        java.util.Arrays.fill(edges, false);
        touchEdge = false;
    }

    public boolean keyPressed(int key) { return edges[key]; }
    public boolean touchPressed() { return touchEdge; }

    public boolean skipPressed(Input input) {
        boolean pressed = false;
        for (int key = 0; key < held.length; key++) {
            boolean down = input.isKeyPressed(key);
            edges[key] = !held[key] && (down || input.isKeyJustPressed(key));
            pressed |= edges[key];
            held[key] = down;
        }
        touchEdge = input.justTouched() && !touched;
        pressed |= touchEdge;
        touched = input.isTouched();
        return pressed;
    }
}
