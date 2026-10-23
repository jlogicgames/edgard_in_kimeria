package com.jlogicsoftware.kimeria;

/** Run-only stock shared by the test campaign's Forest levels. Never written to the level save. */
public final class RangedStock {
    public static final int CAPACITY = 3;
    private int count = CAPACITY, levelEntry = CAPACITY;
    public int count() { return count; }
    public boolean spend() { if (count == 0) return false; count--; return true; }
    public void restore(int value) { count = Math.max(0, Math.min(CAPACITY, value)); }
    public void beginLevel() { levelEntry = count; }
    public void restartLevel() { count = levelEntry; }
    public void reset() { count = levelEntry = CAPACITY; }
}
