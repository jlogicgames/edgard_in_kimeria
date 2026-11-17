package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.enemy.Enemy;
import com.jlogicsoftware.kimeria.entity.enemy.RedMob;
import com.jlogicsoftware.kimeria.entity.items.FireballRefill;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * forest-1's first room (#375, D72): the first RedMob, alone on flat ground, then the refill. The room lies after the tree
 * room on the map (x 3520-4320) and is first in the Rooms list, which sets the play order. Y grows downward: floor top y 320.
 */
class Forest1MobTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float ROOM_X = 3520, ROOM_END = 4320, START_X = 3552, MOB_X = 3776, REFILL_X = 4208, EXIT_X = 4256, FLOOR = 320;
    /** The hero's centre wakes the mob in x 3728-3824 (offNeg/offPos 3 tiles around 3776). */
    private static final float WAKE_LEFT = 3728, WAKE_RIGHT = 3824, VIEW_W = 640;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    private Player player() { return new Player(h.assets(), h.game(), 0, 0); }
    private Level room(int charges) {
        RangedStock stock = new RangedStock();
        stock.restore(charges);
        return new Level(h.assets(), h.game(), player(), "forest-1", Rectangle::new, stock);
    }
    private RedMob mob(Level l) { return l.objects.stream().filter(RedMob.class::isInstance).map(RedMob.class::cast).findFirst().orElse(null); }
    private FireballRefill refill(Level l) { return l.objects.stream().filter(FireballRefill.class::isInstance).map(FireballRefill.class::cast).findFirst().orElse(null); }
    private float centerX(Level l) { Rectangle b = l.player.hitboxBounds(); return b.x + b.width / 2; }

    @Test void theLevelStartsHereAndTheRoomHoldsOneRedMobOneRefillAndTheExitAndNothingElse() {
        Level l = room(0);
        assertEquals("forest-1-mob", l.currentRoomName());
        assertEquals(new Rectangle(ROOM_X, 0, ROOM_END - ROOM_X, 368), l.currentRoomBounds());
        assertEquals(START_X, l.player.position.x, 0.01f);
        RedMob m = mob(l);
        assertNotNull(m);
        assertEquals(MOB_X, m.getX(), 0.01f);
        assertEquals(1, l.objects.stream().filter(Enemy.class::isInstance).count(), "one enemy");
        FireballRefill r = refill(l);
        assertNotNull(r);
        assertEquals(REFILL_X, r.getX(), 0.01f);
        assertEquals(FLOOR - 16, r.getY() + r.getHeight(), 0.01f, "the refill floats one tile over the floor, in reach of a walking hero");
        assertTrue(l.escalators.isEmpty(), "no machinery");
        assertTrue(l.fallingPlatforms.isEmpty(), "no supports");
        assertEquals("", l.player.zoneHint(), "no hints (forest-1 has none, D33)");
        assertEquals("forest-1-traps", nextRoomName(l));
    }

    private String nextRoomName(Level l) { l.enterNextRoom(player()); return l.currentRoomName(); }

    /** The mob is in view from the entrance; the refill is out of view for as long as the mob is alive and the hero is in its range. */
    @Test void theMobIsMetAloneAndTheRefillComesIntoViewOnlyAfterIt() {
        Level l = room(0);
        Vector2 cam = l.player.roomCameraTarget();
        assertTrue(cam.x <= MOB_X && MOB_X + 48 <= cam.x + VIEW_W, "the mob is on screen from the entrance");
        assertFalse(REFILL_X < cam.x + VIEW_W, "the refill is not");
        boolean actionWasDown = false, sawRefill = false, wokeMob = false;
        for (int f = 0; f < 60 * 20 && !l.atRoomExit(); f++) {
            held.clear(); pressed.clear();
            held.add(Input.Keys.D);
            boolean action = false;
            RedMob m = mob(l);
            if (m != null && !m.isDefeated()) {
                Rectangle e = m.hitboxBounds(), me = l.player.hitboxBounds();
                action = l.player.isOnGround() && e.x >= me.x && e.x - (me.x + me.width) < 18;
            }
            if (action && !actionWasDown) pressed.add(Input.Keys.K);
            actionWasDown = action;
            l.update(STEP);
            assertFalse(l.player.isGotHit(), "the sword takes the mob without a hit");
            float c = centerX(l);
            Vector2 view = l.player.roomCameraTarget();
            boolean refillInView = REFILL_X < view.x + VIEW_W;
            boolean mobAlive = mob(l) != null && !mob(l).isDefeated();
            if (c >= WAKE_LEFT && c <= WAKE_RIGHT) wokeMob = true;
            if (mobAlive && c <= WAKE_RIGHT) assertFalse(refillInView, "refill in view while the mob is met, hero centre " + c);
            if (refillInView) sawRefill = true;
        }
        assertTrue(wokeMob && sawRefill, "the walk woke the mob and then saw the refill");
        assertTrue(l.atRoomExit(), "reached the exit");
        assertTrue(mob(l) == null || mob(l).isDefeated());
        assertTrue(l.player.position.x + 48 >= EXIT_X, "at the exit");
    }

    /** A hero who spent a charge (a run starts with three) gets it back from the refill on the walk to the exit; the mob is taken as a secured kill would be. */
    @Test void theRefillGivesASpentChargeBackOnTheWalkToTheExit() {
        Level l = room(2);
        mob(l).collidedWithActor(true);
        for (int f = 0; f < 60 * 20 && !l.atRoomExit(); f++) {
            held.clear(); pressed.clear();
            held.add(Input.Keys.D);
            l.update(STEP);
            assertFalse(l.player.isGotHit());
        }
        assertTrue(l.atRoomExit());
        assertEquals(3, l.rangedStock.count(), "the refill returned the charge");
        assertTrue(refill(l) == null || refill(l).isRemoved(), "the refill is taken");
    }
}
