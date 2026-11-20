package com.jlogicsoftware.kimeria.tiled;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * One room's foreground: tile objects drawn in front of the world at parallax factor {@link #factorX} (1.6 for the forest). The group
 * is named {@code foreground-<room name>} and its required property {@code roomx} is that room's x (both checked at load). {@code x} is
 * relative to the room's left edge; {@code y} is the object's top edge in <b>map coordinates</b>, not room-relative (Tiled stores the
 * bottom of a tile object, the parser converts): it lines up with the room's floor camera top, which is in map coordinates too, so it is
 * right for a room at any y; every forest room lies at y 0, where the two coincide. Arrays are parallel, {@link #count} long.
 */
public final class ForegroundGroup {
    public String name = "";
    /** The room's left edge in the map: the origin of this group's parallax. */
    public float roomX;
    public double factorX = 1, factorY = 1;
    public int count;
    public TextureRegion[] regions = new TextureRegion[0];
    public String[] names = new String[0];
    public float[] x = new float[0], y = new float[0], width = new float[0], height = new float[0];
    public boolean[] flipX = new boolean[0], flipY = new boolean[0];
}
