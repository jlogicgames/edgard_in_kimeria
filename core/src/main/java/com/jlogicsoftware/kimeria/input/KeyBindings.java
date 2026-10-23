package com.jlogicsoftware.kimeria.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

/**
 * Keyboard bindings for the two handedness modes. The sets are disjoint: each mode answers only
 * to its own keys, and the arrow keys belong to the left-handed set alone. Keeping both live
 * would put a second, conflicting control scheme under the hand that is meant to be doing the
 * other job.
 */
public enum KeyBindings {
    /** Movement on the left hand (A/D), actions on the right (J/K/L). */
    RIGHT_HANDED(new int[]{Keys.A}, new int[]{Keys.D}, Keys.W, Keys.S, Keys.J, Keys.K, Keys.L),
    /** Movement on the arrow keys (right hand), actions on Z/X/C (left hand). */
    LEFT_HANDED(new int[]{Keys.LEFT}, new int[]{Keys.RIGHT}, Keys.UP, Keys.DOWN, Keys.Z, Keys.X, Keys.C);

    private final int[] left, right;
    private final int jump, action, shoot;
    private final int up, down;

    KeyBindings(int[] left, int[] right, int up, int down, int jump, int action, int shoot) {
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.action = action;
        this.shoot = shoot;
        this.up = up;
        this.down = down;
    }

    public static KeyBindings of(boolean leftHanded) {
        return leftHanded ? LEFT_HANDED : RIGHT_HANDED;
    }

    public boolean moveLeftHeld() {
        return anyPressed(left);
    }

    public boolean moveRightHeld() {
        return anyPressed(right);
    }

    public boolean jumpHeld() {
        return Gdx.input.isKeyPressed(jump);
    }

    public boolean upHeld() { return Gdx.input.isKeyPressed(up); }
    public boolean downHeld() { return Gdx.input.isKeyPressed(down); }
    public boolean actionHeld() { return Gdx.input.isKeyPressed(action); }
    public boolean shootHeld() { return Gdx.input.isKeyPressed(shoot); }
    public String actionName() { return Keys.toString(action); }
    public String rangedName() { return Keys.toString(shoot); }
    public String upName() { return Keys.toString(up); }
    public String downName() { return Keys.toString(down); }

    public boolean actionJustPressed() {
        return Gdx.input.isKeyJustPressed(action);
    }

    public boolean shootJustPressed() {
        return Gdx.input.isKeyJustPressed(shoot);
    }

    private static boolean anyPressed(int[] keys) {
        for (int key : keys) {
            if (Gdx.input.isKeyPressed(key)) return true;
        }
        return false;
    }
}
