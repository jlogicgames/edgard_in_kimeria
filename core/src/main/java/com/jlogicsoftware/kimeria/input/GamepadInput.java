package com.jlogicsoftware.kimeria.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;

/**
 * Generic gamepad polling, matching the Rust version's README: left
 * stick/D-pad move, South (A) jump, West/East (X/B) attack, North (Y)
 * interact, Start pause; Up/Down/Left/Right or the stick navigate menus,
 * A confirms, B goes back. Uses {@link ControllerMapping}'s generic
 * Xbox-style button indices, which gdx-controllers resolves per-OS/per-pad,
 * so this isn't tied to one specific controller brand.
 *
 * <p>One instance polls edges (just-pressed) across frames; call
 * {@link #update()} once per frame before reading edge getters.
 */
public class GamepadInput {
    private static final float AXIS_DEADZONE = 0.4f;
    private static final float AXIS_MENU_DEADZONE = 0.6f;

    private boolean prevConfirm, prevBack, prevPause, prevAttack, prevInteract;
    private boolean prevUp, prevDown, prevLeft, prevRight;

    private boolean confirmEdge, backEdge, pauseEdge, attackEdge, interactEdge;
    private boolean upEdge, downEdge, leftEdge, rightEdge;

    private float horizontal;
    private boolean jumpHeld;
    // A also confirms menu buttons; a press that a menu consumed must not
    // count as a jump once play starts, so jump stays off until A is released.
    private boolean jumpSuppressed;

    private Controller controller() {
        var list = Controllers.getControllers();
        return list.size == 0 ? null : list.first();
    }

    private static boolean button(Controller c, ControllerMapping m, int index) {
        return c != null && index != ControllerMapping.UNDEFINED && c.getButton(index);
    }

    public void update() {
        Controller c = controller();
        ControllerMapping m = c == null ? null : c.getMapping();

        boolean confirm = button(c, m, m == null ? -1 : m.buttonA);
        boolean back = button(c, m, m == null ? -1 : m.buttonB);
        boolean pause = button(c, m, m == null ? -1 : m.buttonStart);
        boolean attack = button(c, m, m == null ? -1 : m.buttonX) || button(c, m, m == null ? -1 : m.buttonB);
        boolean interact = button(c, m, m == null ? -1 : m.buttonY);

        float axisX = c == null ? 0f : safeAxis(c, m.axisLeftX);
        float axisY = c == null ? 0f : safeAxis(c, m.axisLeftY);
        boolean dpadUp = button(c, m, m == null ? -1 : m.buttonDpadUp);
        boolean dpadDown = button(c, m, m == null ? -1 : m.buttonDpadDown);
        boolean dpadLeft = button(c, m, m == null ? -1 : m.buttonDpadLeft);
        boolean dpadRight = button(c, m, m == null ? -1 : m.buttonDpadRight);

        boolean up = dpadUp || axisY < -AXIS_MENU_DEADZONE;
        boolean down = dpadDown || axisY > AXIS_MENU_DEADZONE;
        boolean left = dpadLeft || axisX < -AXIS_MENU_DEADZONE;
        boolean right = dpadRight || axisX > AXIS_MENU_DEADZONE;

        confirmEdge = confirm && !prevConfirm;
        backEdge = back && !prevBack;
        pauseEdge = pause && !prevPause;
        attackEdge = attack && !prevAttack;
        interactEdge = interact && !prevInteract;
        upEdge = up && !prevUp;
        downEdge = down && !prevDown;
        leftEdge = left && !prevLeft;
        rightEdge = right && !prevRight;

        prevConfirm = confirm;
        prevBack = back;
        prevPause = pause;
        prevAttack = attack;
        prevInteract = interact;
        prevUp = up;
        prevDown = down;
        prevLeft = left;
        prevRight = right;

        horizontal = (dpadLeft ? -1f : 0f) + (dpadRight ? 1f : 0f);
        if (horizontal == 0f && Math.abs(axisX) > AXIS_DEADZONE) {
            horizontal = Math.signum(axisX);
        }
        if (!confirm) jumpSuppressed = false;
        jumpHeld = confirm && !jumpSuppressed;
    }

    /** Ignores the A button for jumping until it is released (call when a menu used the press). */
    public void suppressJumpUntilRelease() {
        jumpSuppressed = true;
        jumpHeld = false;
    }

    private static float safeAxis(Controller c, int index) {
        if (index == ControllerMapping.UNDEFINED) return 0f;
        try {
            return c.getAxis(index);
        } catch (Exception e) {
            return 0f;
        }
    }

    public boolean menuUp() {
        return upEdge;
    }

    public boolean menuDown() {
        return downEdge;
    }

    public boolean menuLeft() {
        return leftEdge;
    }

    public boolean menuRight() {
        return rightEdge;
    }

    public boolean confirm() {
        return confirmEdge;
    }

    public boolean back() {
        return backEdge;
    }

    public boolean pausePressed() {
        return pauseEdge;
    }

    public float horizontal() {
        return horizontal;
    }

    public boolean jumpHeld() {
        return jumpHeld;
    }

    public boolean attackPressed() {
        return attackEdge;
    }

    public boolean interactPressed() {
        return interactEdge;
    }
}
