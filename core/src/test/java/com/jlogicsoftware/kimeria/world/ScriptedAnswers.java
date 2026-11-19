package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.enemy.Snail;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;

import java.util.Set;

/**
 * How the scripted (mastered) hero of the forest-1 route tests answers the snail (D106) and a thorn tuft (D44) while he walks
 * right: he jumps a gob flying at him when it is {@link #GOB_JUMP_GAP} px from his body, swings at a live snail just ahead,
 * and jumps a tuft from {@link #TUFT_TAKE_OFF} px before it. Shared by the three spider rooms of #500 (D105).
 */
final class ScriptedAnswers {
    /** Gap from the gob's core to the hero's body at which he jumps it (closing at up to 220 px/s, a jump clears the line in 0.04 s). */
    static final float GOB_JUMP_GAP = 24;
    /** Gap from the hero's body to a live snail's at which he swings (the blade reaches from closer than 17 px). */
    static final float SWORD_GAP = 8;
    /** Take-off distance before a tuft (its measured window is 2 to 61 px). */
    static final float TUFT_TAKE_OFF = 30;

    private static final Rectangle ME = new Rectangle(), IT = new Rectangle();

    private ScriptedAnswers() { }

    static Snail snail(Level l, float fromX, float toX) {
        for (int i = 0; i < l.objects.size(); i++)
            if (l.objects.get(i) instanceof Snail s && s.getX() >= fromX && s.getX() < toX) return s;
        return null;
    }

    static ThornTrap tuft(Level l) {
        for (int i = 0; i < l.objects.size(); i++) if (l.objects.get(i) instanceof ThornTrap t) return t;
        return null;
    }

    static void jump(Set<Integer> held, Set<Integer> pressed) { held.add(Input.Keys.J); pressed.add(Input.Keys.J); }

    /** True when a gob is in flight towards the grounded hero and within {@link #GOB_JUMP_GAP} of his body. */
    static boolean gobClose(Level l, Snail s) {
        if (s == null || !s.gobActive() || !l.player.isOnGround()) return false;
        Rectangle me = l.player.hitboxBoundsInto(ME);
        float gob = s.gobX(), half = Snail.GOB_CORE / 2;
        float gap = gob < me.x ? me.x - (gob + half) : gob - half - (me.x + me.width);
        return gap <= GOB_JUMP_GAP && s.gobY() > me.y && s.gobY() < me.y + me.height;
    }

    /** True when a live snail is just ahead of the grounded hero, inside the sword's reach. */
    static boolean snailInReach(Level l, Snail s) {
        if (s == null || s.isDefeated() || !l.player.isOnGround()) return false;
        Rectangle me = l.player.hitboxBoundsInto(ME), it = s.hitboxBoundsInto(IT);
        return it.x >= me.x && it.x - (me.x + me.width) < SWORD_GAP && it.y < me.y + me.height && it.y + it.height > me.y;
    }

    /** True when the tuft is {@link #TUFT_TAKE_OFF} px or closer ahead of the grounded hero. */
    static boolean tuftAhead(Level l, ThornTrap t) {
        if (t == null || !l.player.isOnGround()) return false;
        Rectangle me = l.player.hitboxBoundsInto(ME);
        float gap = t.getX() - (me.x + me.width);
        return gap >= 0 && gap <= TUFT_TAKE_OFF;
    }

    /**
     * One frame of going to finish a live snail on the hero's floor, either side of him: jump a close gob, else walk towards it and,
     * facing it inside the sword's reach, swing (once per swing: {@code swinging} holds the last frame's decision). Returns whether it swings.
     */
    static boolean finish(Level l, Snail s, boolean swinging, Set<Integer> held, Set<Integer> pressed) {
        Rectangle me = l.player.hitboxBoundsInto(ME), it = s.hitboxBoundsInto(IT);
        boolean left = it.x + it.width / 2 < me.x + me.width / 2;
        float gap = left ? me.x - (it.x + it.width) : it.x - (me.x + me.width);
        if (gobClose(l, s)) { held.add(left ? Input.Keys.A : Input.Keys.D); jump(held, pressed); return false; }
        boolean facing = l.player.facingRight != left;
        boolean swing = l.player.isOnGround() && facing && gap < SWORD_GAP;
        if (!swing) held.add(left ? Input.Keys.A : Input.Keys.D);
        else if (!swinging) pressed.add(Input.Keys.K);
        return swing;
    }

    /**
     * One frame of the walk right through a snail (and a tuft, when {@code tuft} is not null): jump a close gob, swing at a snail in
     * reach (once per swing: {@code swinging} holds the last frame's decision), else walk on. Returns whether it swings.
     */
    static boolean walkRight(Level l, Snail s, ThornTrap tuft, boolean swinging, Set<Integer> held, Set<Integer> pressed) {
        held.add(Input.Keys.D);
        if (gobClose(l, s) || tuftAhead(l, tuft)) jump(held, pressed);
        boolean swing = snailInReach(l, s);
        if (swing && !swinging) pressed.add(Input.Keys.K);
        return swing;
    }
}
