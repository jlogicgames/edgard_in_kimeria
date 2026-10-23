package com.jlogicsoftware.kimeria.input;

import com.jlogicsoftware.kimeria.ui.TouchControls;

/** Fresh gameplay presses, independently latched per device across UI/room boundaries. */
public final class GameplayInput {
    public enum Device { KEYBOARD, GAMEPAD, TOUCH }
    private final boolean[] actionWasHeld = {true, true, true};
    private final boolean[] rangedWasHeld = {true, true, true};
    private boolean layout;
    private Device device = Device.KEYBOARD;
    private boolean up, down, actionHeld, action, ranged;

    public void reset() {
        java.util.Arrays.fill(actionWasHeld, true);
        java.util.Arrays.fill(rangedWasHeld, true);
        action = ranged = actionHeld = false;
    }

    public void update(boolean leftHanded, GamepadInput pad, TouchControls touch) {
        if (layout != leftHanded) { reset(); layout = leftHanded; }
        KeyBindings keys = KeyBindings.of(leftHanded);
        boolean[] actions = {keys.actionHeld() || keys.actionJustPressed(), pad.actionHeld(), touch.actionHeld()};
        boolean[] ranges = {keys.shootHeld() || keys.shootJustPressed(), pad.shootHeld(), touch.shootHeld()};
        action = ranged = actionHeld = false;
        Device edgeDevice = null;
        for (int i = 0; i < actions.length; i++) {
            boolean a = actions[i] && !actionWasHeld[i];
            boolean r = ranges[i] && !rangedWasHeld[i];
            action |= a;
            ranged |= r;
            actionHeld |= actions[i];
            if (a || r) edgeDevice = Device.values()[i];
            actionWasHeld[i] = actions[i];
            rangedWasHeld[i] = ranges[i];
        }
        // Movement also chooses the prompt device, without idle connected pads stealing it.
        if (keys.moveLeftHeld() || keys.moveRightHeld() || keys.upHeld() || keys.downHeld() || keys.jumpHeld())
            device = Device.KEYBOARD;
        if (pad.horizontal() != 0 || pad.upHeld() || pad.downHeld() || pad.jumpHeld()) device = Device.GAMEPAD;
        if (touch.horizontal() != 0 || touch.upHeld() || touch.downHeld() || touch.jumpHeld()) device = Device.TOUCH;
        if (edgeDevice != null) device = edgeDevice;
        up = keys.upHeld() || pad.upHeld() || touch.upHeld();
        down = keys.downHeld() || pad.downHeld() || touch.downHeld();
    }

    public boolean up() { return up; }
    public boolean down() { return down; }
    public boolean actionPressed() { return action; }
    public boolean rangedPressed() { return ranged; }
    public Device device() { return device; }
    /** A directional action owns vertical input until Action is released, including on ladders (#99). */
    public float verticalMovement() { return actionHeld ? 0 : (down ? 1 : 0) - (up ? 1 : 0); }
}
