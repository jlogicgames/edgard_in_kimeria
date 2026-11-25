package com.jlogicsoftware.kimeria.input;

import com.jlogicsoftware.kimeria.ui.TouchControls;

/** Fresh gameplay presses, independently latched per device across UI/room boundaries. */
public final class GameplayInput {
    public enum Device { KEYBOARD, GAMEPAD, TOUCH }
    private static final int KEY = Device.KEYBOARD.ordinal(), PAD = Device.GAMEPAD.ordinal(), FINGER = Device.TOUCH.ordinal();
    private static final int DEVICES = Device.values().length;
    private final boolean[] actionWasHeld = {true, true, true};
    private final boolean[] rangedWasHeld = {true, true, true};
    private final boolean[] upWasHeld = {true, true, true};
    private final boolean[] downWasHeld = {true, true, true};
    private final boolean[] actions = new boolean[DEVICES], ranges = new boolean[DEVICES]; // reused: no per-frame allocation
    private final boolean[] ups = new boolean[DEVICES], downs = new boolean[DEVICES];
    private boolean layout;
    private Device device = Device.KEYBOARD;
    private boolean up, down, actionHeld, rangedHeld, action, ranged, upPress, downPress, nonTouchMove;

    public void reset() {
        java.util.Arrays.fill(actionWasHeld, true);
        java.util.Arrays.fill(rangedWasHeld, true);
        java.util.Arrays.fill(upWasHeld, true);
        java.util.Arrays.fill(downWasHeld, true);
        action = ranged = actionHeld = rangedHeld = upPress = downPress = false;
    }

    public void update(boolean leftHanded, GamepadInput pad, TouchControls touch) {
        if (layout != leftHanded) { reset(); layout = leftHanded; }
        KeyBindings keys = KeyBindings.of(leftHanded);
        boolean keyUp = keys.upHeld(), keyDown = keys.downHeld(); // read once: every Gdx.input call counts in the allocation budget (the test input is a proxy)
        actions[KEY] = keys.actionHeld() || keys.actionJustPressed(); actions[PAD] = pad.actionHeld(); actions[FINGER] = touch.actionHeld();
        ranges[KEY] = keys.shootHeld() || keys.shootJustPressed(); ranges[PAD] = pad.shootHeld(); ranges[FINGER] = touch.shootHeld();
        action = ranged = actionHeld = rangedHeld = upPress = downPress = false;
        Device edgeDevice = null;
        for (int i = 0; i < actions.length; i++) {
            boolean a = actions[i] && !actionWasHeld[i];
            boolean r = ranges[i] && !rangedWasHeld[i];
            action |= a;
            ranged |= r;
            actionHeld |= actions[i];
            rangedHeld |= ranges[i];
            if (a || r) edgeDevice = Device.values()[i];
            actionWasHeld[i] = actions[i];
            rangedWasHeld[i] = ranges[i];
        }
        // Movement also chooses the prompt device, without idle connected pads stealing it.
        boolean keyMove = keys.moveLeftHeld() || keys.moveRightHeld() || keyUp || keyDown || keys.jumpHeld();
        boolean padMove = pad.horizontal() != 0 || pad.upHeld() || pad.downHeld() || pad.jumpHeld();
        nonTouchMove = keyMove || padMove;
        if (keyMove) device = Device.KEYBOARD;
        if (padMove) device = Device.GAMEPAD;
        if (touch.horizontal() != 0 || touch.upHeld() || touch.downHeld() || touch.jumpHeld()) device = Device.TOUCH;
        if (edgeDevice != null) device = edgeDevice;
        // Interaction (D126) is a fresh press per device; the touch stick counts only when steeply up or down.
        ups[KEY] = keyUp || keys.upJustPressed(); ups[PAD] = pad.upHeld(); ups[FINGER] = touch.interactUpHeld();
        downs[KEY] = keyDown || keys.downJustPressed(); downs[PAD] = pad.downHeld(); downs[FINGER] = touch.interactDownHeld();
        for (int i = 0; i < ups.length; i++) {
            upPress |= ups[i] && !upWasHeld[i];
            downPress |= downs[i] && !downWasHeld[i];
            upWasHeld[i] = ups[i];
            downWasHeld[i] = downs[i];
        }
        up = keyUp || pad.upHeld() || touch.upHeld();
        down = keyDown || pad.downHeld() || touch.downHeld();
    }

    public boolean verticalHeld() { return up || down; }
    public boolean up() { return up; }
    public boolean down() { return down; }
    public boolean actionHeld() { return actionHeld; }
    public boolean actionPressed() { return action; }
    public boolean rangedPressed() { return ranged; }
    public Device device() { return device; }
    /** A keyboard or a gamepad is moving the hero this frame: the touch tutorial ends for good (D139). */
    public boolean nonTouchMoved() { return nonTouchMove; }
    /** A fresh Up (or a steep touch-stick up) this frame: pick up or use a mechanism (D126). */
    public boolean upPressed() { return upPress; }
    /** A fresh Down this frame: put down (D126). */
    public boolean downPressed() { return downPress; }
    /** Climbing on hold: -1 up, +1 down, 0 when both or neither (Action no longer owns the vertical). */
    public float verticalMovement() { return (down ? 1 : 0) - (up ? 1 : 0); }
}
