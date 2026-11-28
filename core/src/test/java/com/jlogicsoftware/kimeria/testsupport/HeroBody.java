package com.jlogicsoftware.kimeria.testsupport;

import com.jlogicsoftware.kimeria.entity.player.Player;

/**
 * Where the hero's body is inside his 48x48 box (D164), for tests that place him by an edge or build a body rectangle. All of
 * it is read from {@link Player}, so a test keeps no copy of the numbers. World Y grows downward.
 */
public final class HeroBody {
    private HeroBody() { }

    /** The body's size. */
    public static final float W = Player.BODY_WIDTH, H = Player.BODY_HEIGHT;
    public static final float HALF = W / 2f;
    /** From {@code position.x} to the body's left edge, centre and right edge. */
    public static final float LEFT = Player.BODY_OFFSET_X, CENTRE = LEFT + HALF, RIGHT = LEFT + W;
    /** From {@code position.y} to the body's top edge; its bottom edge, the feet, is at {@code TOP + H}. */
    public static final float TOP = Player.BODY_OFFSET_Y, FEET = TOP + H;
}
