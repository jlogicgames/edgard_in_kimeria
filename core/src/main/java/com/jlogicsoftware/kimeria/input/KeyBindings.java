package com.jlogicsoftware.kimeria.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

/**
 * Keyboard bindings for the two handedness modes. The left-handed set <em>replaces</em> the
 * default one while the mode is on (rather than adding to it): WASD and J/K/L sit under the
 * left and right hand respectively, so keeping them live would put a second, conflicting
 * control scheme under the hand that is now meant to be doing the other job.
 */
public enum KeyBindings {
    /** Movement on the left hand (WASD or arrows), actions on the right (J/K/L). */
    RIGHT_HANDED(new int[]{Keys.A, Keys.LEFT}, new int[]{Keys.D, Keys.RIGHT}, Keys.J, Keys.K, Keys.L),
    /** Movement on the arrow keys (right hand), actions on Z/X/C (left hand). */
    LEFT_HANDED(new int[]{Keys.LEFT}, new int[]{Keys.RIGHT}, Keys.Z, Keys.X, Keys.C);

    private final int[] left, right;
    private final int jump, action, shoot;

    KeyBindings(int[] left, int[] right, int jump, int action, int shoot) {
        this.left = left;
        this.right = right;
        this.jump = jump;
        this.action = action;
        this.shoot = shoot;
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
