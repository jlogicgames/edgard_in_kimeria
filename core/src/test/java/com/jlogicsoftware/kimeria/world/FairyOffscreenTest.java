package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.jlogicsoftware.kimeria.RangedStock;
import com.jlogicsoftware.kimeria.entity.Entity;
import com.jlogicsoftware.kimeria.entity.items.Lever;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.objects.Ladder;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.ui.FairyGuide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D87 (#419): the fairy guide sends her to the screen edge toward a linked mechanism that is off screen, and the pulled lever's
 * mechanisms keep a clear glow for 1.5 s. The integration cases use the real forest-1 route room (room 3 since D86) (one lever, a ladder and a lift)
 * and its real camera rule ({@code Player.roomCameraTarget}); the others fix the view by hand to put a mechanism off screen on purpose.
 * World Y grows downward: a mechanism below the view has the larger y, so its edge point is below the view's centre.
 */
class FairyOffscreenTest {
    private static final float STEP = HeadlessLevelHarness.STEP, VIEW_W = 640, VIEW_H = 360, EDGE = FairyGuide.EDGE_MARGIN;
    private static final float LEVER_X = 1440, EPS = 1e-3f;
    private static final int PULL_FRAMES = 90, SETTLE = 90;

    private final HeadlessLevelHarness h = new HeadlessLevelHarness();
    private final Set<Integer> held = h.held, pressed = h.pressed;
    private final Rectangle view = new Rectangle();
    private boolean followCamera;

    @BeforeEach void setUp() { h.setUp(); }
    @AfterEach void restoreGlobals() { h.restoreGlobals(); }

    // ---- the edge-point math: every direction, Y down ----

    private static final Rectangle VIEW = new Rectangle(100, 200, 640, 360); // centre (420, 380); inset by 16: x 116..724, y 216..544

    private Vector2 edge(float tx, float ty) { return FairyGuide.edgePoint(VIEW, EDGE, tx, ty, new Vector2()); }

    @Test void aTargetToTheRightGivesThePointOnTheRightEdgeAtTheSameHeight() {
        Vector2 p = edge(2000, 380);
        assertEquals(740 - EDGE, p.x, EPS); assertEquals(380, p.y, EPS);
    }

    @Test void aTargetToTheLeftGivesThePointOnTheLeftEdge() {
        Vector2 p = edge(-500, 380);
        assertEquals(100 + EDGE, p.x, EPS); assertEquals(380, p.y, EPS);
    }

    @Test void aTargetAboveTheViewIsASmallerYAndTheTopEdge() {
        Vector2 p = edge(420, -300);
        assertEquals(420, p.x, EPS); assertEquals(200 + EDGE, p.y, EPS);
        assertTrue(p.y < 380, "above the view centre is a smaller y");
    }

    @Test void aTargetBelowTheViewIsALargerYAndTheBottomEdge() {
        Vector2 p = edge(420, 1500);
        assertEquals(420, p.x, EPS); assertEquals(560 - EDGE, p.y, EPS);
        assertTrue(p.y > 380, "below the view centre is a larger y");
    }

    @Test void aDiagonalTargetGivesACornerWhenItLiesOnTheInsetCornerRay() {
        float halfW = 320 - EDGE, halfH = 180 - EDGE;
        Vector2 p = edge(420 + halfW * 3, 380 + halfH * 3);
        assertEquals(740 - EDGE, p.x, EPS); assertEquals(560 - EDGE, p.y, EPS);
        Vector2 up = edge(420 - halfW * 3, 380 - halfH * 3);
        assertEquals(100 + EDGE, up.x, EPS); assertEquals(200 + EDGE, up.y, EPS);
    }

    @Test void theEdgePointKeepsTheDirectionOfTheTargetAndStaysInsideTheInset() {
        float[][] targets = {{1400, 600}, {-400, 90}, {300, 900}, {900, -200}, {420, 1000}, {1000, 380}};
        for (float[] t : targets) {
            Vector2 p = edge(t[0], t[1]);
            float dx = t[0] - 420, dy = t[1] - 380, px = p.x - 420, py = p.y - 380;
            assertEquals(0, dx * py - dy * px, 1f, "same direction as the target from the centre");
            assertTrue(dx * px + dy * py > 0, "on the same side");
            assertTrue(p.x >= 100 + EDGE - EPS && p.x <= 740 - EDGE + EPS && p.y >= 200 + EDGE - EPS && p.y <= 560 - EDGE + EPS, "inside the inset view");
        }
    }

    @Test void aTargetInsideTheInsetComesBackUnchanged() {
        Vector2 p = edge(500, 400);
        assertEquals(500, p.x, EPS); assertEquals(400, p.y, EPS);
    }

    // ---- the route room: one lever, a ladder and a lift ----

    private Level route() {
        RangedStock stock = new RangedStock();
        stock.restore(0);
        Level level = new Level(h.assets(), h.game(), new Player(h.assets(), h.game(), 0, 0), "forest-1", () -> view, stock);
        level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0)); // forest-1-mob -> forest-1-deadfall (#363)
        level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0)); // -> forest-1-traps
        level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0)); // -> forest-1-lever (D86)
        level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0)); // -> forest-1-route
        followCamera = true;
        return level;
    }

    private void frame(Level level) {
        if (followCamera) { Vector2 t = level.player.roomCameraTarget(); view.set(t.x, t.y, VIEW_W, VIEW_H); }
        level.update(STEP);
        pressed.clear();
    }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private Ladder ladder(Level level) { return level.ladders.get(0); }
    private Escalator lift(Level level) { return level.escalators.get(0); }
    private float centerX(Level level) { Rectangle b = level.player.hitboxBounds(); return b.x + b.width / 2; }

    private void walkToLever(Level level) {
        int frames = 0;
        while (!level.player.canInteract()) {
            held.clear(); held.add(centerX(level) > LEVER_X + 8 ? Input.Keys.A : Input.Keys.D);
            frame(level);
            assertTrue(++frames < 60 * 30, "lever never came into reach at " + level.player.position);
        }
        held.clear();
        settle(level, 10);
    }

    /** Up + Action; with {@code keepUp} the hero keeps holding Up after the pull (the lever stays selected). */
    private void pull(Level level, boolean keepUp) {
        held.clear(); held.add(Input.Keys.W); frame(level);
        held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
        held.clear();
        if (keepUp) held.add(Input.Keys.W);
    }

    private boolean inView(Entity e) {
        return e.getX() < view.x + view.width && e.getX() + e.getWidth() > view.x && e.getY() < view.y + view.height && e.getY() + e.getHeight() > view.y;
    }

    @Test void fromTheLeverTheLiftIsOffScreenAndTheLadderIsInViewWithTheRealCamera() {
        Level level = route();
        walkToLever(level);
        assertTrue(inView(ladder(level)), "the ladder is in view from the lever");
        assertFalse(inView(lift(level)), "the lift is outside the 640x360 view: view x " + view.x + ".." + (view.x + view.width) + ", lift x " + lift(level).getX());
    }

    @Test void whileTheLeverIsSelectedTheFairyFliesToTheRightEdgeAndTwinklesThere() {
        Level level = route();
        walkToLever(level);
        held.add(Input.Keys.W);
        settle(level, SETTLE);
        var fairy = level.fairy();
        assertInstanceOf(Lever.class, fairy.selected());
        assertSame(lift(level), fairy.offscreenTarget());
        assertEquals(FairyGuide.Mode.TWINKLE, fairy.mode());
        assertEquals(view.x + view.width - EDGE, fairy.x(), 3f, "at the right edge, inset by the margin");
        assertTrue(fairy.x() > level.player.centerX() + 150, "far from Edgard, toward the lift");
        assertTrue(fairy.y() >= view.y && fairy.y() <= view.y + view.height, "inside the view vertically");
        assertEquals(1f, fairy.glowAlpha(fairy.selected()), "the lever keeps its selected glow");
        assertEquals(.3f, fairy.glowAlpha(ladder(level)), 1e-6, "the in-view ladder keeps its passive glow before the pull");
    }

    @Test void afterThePullTheCueLastsOneAndAHalfSecondsThenSheReturnsToEdgard() {
        Level level = route();
        walkToLever(level);
        pull(level, false);
        var fairy = level.fairy();
        assertTrue(fairy.changedSecondsLeft() > FairyGuide.CHANGED_SECONDS - .1f);
        assertEquals(2, fairy.changedTargets().size, "the ladder and the lift");
        assertSame(lift(level), fairy.offscreenTarget(), "the lift is off screen: she goes for it though nothing is selected");
        settle(level, 45);
        assertEquals(FairyGuide.Mode.TWINKLE, fairy.mode());
        assertEquals(view.x + view.width - EDGE, fairy.x(), 3f);
        int walked = 0;
        while (level.player.canInteract() && walked++ < 60) { held.clear(); held.add(Input.Keys.A); frame(level); }
        held.clear();
        assertNull(fairy.selected(), "out of reach of the lever nothing is selected, and the post-pull cue goes on");
        assertTrue(fairy.changedSecondsLeft() > 0 && fairy.offscreenTarget() == lift(level));
        settle(level, PULL_FRAMES);
        assertEquals(0, fairy.changedTargets().size);
        assertNull(fairy.offscreenTarget());
        settle(level, SETTLE);
        assertEquals(FairyGuide.Mode.IDLE, fairy.mode(), "back at his shoulder");
        assertTrue(fairy.distanceToGoal() < 3);
        assertEquals(level.player.facingRight, fairy.x() < level.player.centerX());
        assertEquals(0, fairy.glowAlpha(ladder(level)));
    }

    @Test void theInViewLadderGetsTheClearGlowAtThePullAndItFadesOut() {
        Level level = route();
        walkToLever(level);
        pull(level, false);
        var fairy = level.fairy();
        assertEquals(FairyGuide.CHANGED_ALPHA, fairy.glowAlpha(ladder(level)), 1e-6, "clear glow on the frame of the pull, stronger than passive");
        assertTrue(FairyGuide.CHANGED_ALPHA > .3f);
        settle(level, 30);
        assertEquals(FairyGuide.CHANGED_ALPHA, fairy.glowAlpha(ladder(level)), 1e-6);
        float fading = 0;
        for (int i = 0; i < 40; i++) {
            frame(level);
            if (fairy.changedSecondsLeft() > 0 && fairy.changedSecondsLeft() < FairyGuide.CHANGED_FADE_SECONDS) fading = fairy.glowAlpha(ladder(level));
        }
        assertTrue(fading > 0 && fading < FairyGuide.CHANGED_ALPHA, "the glow fades over the last 0.4 s: " + fading);
        settle(level, 20);
        assertEquals(0, fairy.glowAlpha(ladder(level)));
    }

    @Test void holdingUpAfterThePullKeepsTheLeverCueUntilUpIsReleased() {
        Level level = route();
        walkToLever(level);
        pull(level, true);
        settle(level, PULL_FRAMES + SETTLE);
        var fairy = level.fairy();
        assertInstanceOf(Lever.class, fairy.selected());
        assertSame(lift(level), fairy.offscreenTarget(), "still selected: still at the edge");
        held.clear();
        settle(level, SETTLE);
        assertNull(fairy.offscreenTarget());
        assertEquals(FairyGuide.Mode.IDLE, fairy.mode());
    }

    @Test void withEveryLinkedMechanismInViewNothingChangesButThePostPullGlow() {
        Level level = route();
        walkToLever(level);
        view.set(1280, 0, 1000, 600); // a view wide enough for the whole room
        followCamera = false;
        held.add(Input.Keys.W);
        settle(level, SETTLE);
        var fairy = level.fairy();
        assertNull(fairy.offscreenTarget());
        assertEquals(FairyGuide.Mode.POINT, fairy.mode(), "as before D87: she points at the lever");
        assertTrue(fairy.x() < level.player.centerX() + 40, "beside the lever, not at an edge");
        pull(level, true);
        assertNull(fairy.offscreenTarget());
        assertEquals(FairyGuide.CHANGED_ALPHA, fairy.glowAlpha(lift(level)), 1e-6, "the lift (in view) is marked at the pull");
        assertEquals(FairyGuide.CHANGED_ALPHA, fairy.glowAlpha(ladder(level)), 1e-6);
        settle(level, SETTLE);
        assertEquals(FairyGuide.Mode.POINT, fairy.mode());
        assertEquals(.3f, fairy.glowAlpha(lift(level)), 1e-6, "back to the passive glow while the lever stays selected");
    }

    @Test void withoutAViewTheFairyBehavesAsBeforeAndNoMechanismIsOffScreen() {
        Level level = route();
        followCamera = false;
        view.set(0, 0, 0, 0);
        walkToLever(level);
        held.add(Input.Keys.W);
        settle(level, SETTLE);
        assertNull(level.fairy().offscreenTarget());
        assertEquals(FairyGuide.Mode.POINT, level.fairy().mode());
    }

    @Test void aPullThatDrivesNothingMarksNothing() {
        Level level = route();
        walkToLever(level);
        level.activateTrigger(new com.jlogicsoftware.kimeria.entity.items.Trigger(LEVER_X, 272, 16, 16, "drives-nothing")); // a real pull, no linked mechanism
        assertEquals(0, level.fairy().changedTargets().size);
        settle(level, 10);
        assertEquals(0, level.fairy().changedTargets().size);
        assertNull(level.fairy().offscreenTarget());
        assertEquals(FairyGuide.Mode.IDLE, level.fairy().mode());
    }

    @Test void withTwoOffScreenTheNearestIsChosenAndItKeepsTheJobWhileItStaysOffScreen() {
        Level level = route();
        walkToLever(level);
        followCamera = false;
        view.set(0, 0, VIEW_W, VIEW_H); // far left of the room: the ladder (x 1536) and the lift (x 1936) are both to the right
        held.add(Input.Keys.W);
        settle(level, 5);
        var fairy = level.fairy();
        assertSame(ladder(level), fairy.offscreenTarget(), "the ladder is nearer to the view centre than the lift");
        view.set(1990, 0, VIEW_W, VIEW_H); // now the lift is the nearer, both still off screen (to the left)
        settle(level, 5);
        assertSame(ladder(level), fairy.offscreenTarget(), "she stays with the ladder: no flip-flop");
        view.set(1500, 0, VIEW_W, VIEW_H); // both come into view
        settle(level, 5);
        assertNull(fairy.offscreenTarget());
    }

    @Test void aMechanismBelowTheViewSendsHerDownAndOneAboveSendsHerUp() {
        Level level = route();
        walkToLever(level);
        followCamera = false;
        held.add(Input.Keys.W);
        var fairy = level.fairy();
        view.set(1300, 1000, VIEW_W, VIEW_H); // the whole room has smaller y than this view: the mechanisms are above it
        settle(level, SETTLE);
        assertNotNull(fairy.offscreenTarget());
        assertEquals(view.y + EDGE, fairy.goalY(), 3f, "above the view is its top edge");
        view.set(1300, -1000, VIEW_W, VIEW_H); // the room is below this view
        settle(level, SETTLE);
        assertEquals(view.y + view.height - EDGE, fairy.goalY(), 3f, "below the view is its bottom edge");
    }

    @Test void theFairyCueNeverMovesTheSelectionOrTheEligibility() {
        Level level = route();
        walkToLever(level);
        held.add(Input.Keys.W);
        settle(level, SETTLE);
        assertNotNull(level.fairy().offscreenTarget(), "the cue is on");
        Entity selected = level.fairy().selected();
        boolean canInteract = level.player.canInteract();
        float heroX = level.player.position.x;
        // The same walk and the same keys with no view (no cue): the hero's selection and position are identical.
        Level plain = route();
        followCamera = false;
        view.set(0, 0, 0, 0);
        walkToLever(plain);
        held.add(Input.Keys.W);
        settle(plain, SETTLE);
        assertNull(plain.fairy().offscreenTarget());
        assertEquals(selected.getClass(), plain.fairy().selected().getClass());
        assertEquals(canInteract, plain.player.canInteract());
        assertEquals(heroX, plain.player.position.x, 1e-3f);
        assertSame(plain.player.interactionFeedbackTarget(), plain.fairy().selected());
    }

    @Test void aRoomChangeDropsTheMarksAndTheCue() {
        Level level = route();
        walkToLever(level);
        pull(level, false);
        assertEquals(2, level.fairy().changedTargets().size);
        level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0));
        frame(level);
        assertEquals(0, level.fairy().changedTargets().size);
        assertNull(level.fairy().offscreenTarget());
        assertEquals(0, level.fairy().changedSecondsLeft());
        assertNotEquals(FairyGuide.Mode.TWINKLE, level.fairy().mode());
    }

    @Test void aResetOfTheCurrentRoomDropsTheMarksToo() {
        Level level = route();
        walkToLever(level);
        pull(level, false);
        level.resetCurrentRoom(new Player(h.assets(), h.game(), 0, 0));
        frame(level);
        assertEquals(0, level.fairy().changedTargets().size);
        assertNull(level.fairy().offscreenTarget());
    }

    @Test void aThrownStoneHittingALeverMarksItsMechanismsToo() {
        Level level = h.roomOf("forest-0", 4); // the stone room: a throw-activated lever across the chasm
        var lever = level.triggers.stream().filter(t -> t.throwActivated).findFirst().orElseThrow();
        var stone = level.objects.stream().filter(com.jlogicsoftware.kimeria.entity.items.Carryable.class::isInstance)
            .map(com.jlogicsoftware.kimeria.entity.items.Carryable.class::cast).findFirst().orElseThrow();
        stone.restore(new com.jlogicsoftware.kimeria.entity.items.Carryable.Snapshot(lever.getX(), lever.getY(), 0, 0, true, false));
        assertEquals(0, level.fairy().changedTargets().size);
        assertTrue(level.throwHitsLever(stone));
        assertTrue(level.fairy().changedTargets().size > 0, "the stone's hit marks what the lever drives");
        assertEquals(FairyGuide.CHANGED_SECONDS, level.fairy().changedSecondsLeft(), 1e-6);
    }

    // ---- D87 update (#434): several off-screen mechanisms are shown in turn ----

    /** The fireball refill of the route room: a static third entity for a marked set (the lift and the ladder are the other two). */
    private Entity refill(Level level) {
        return level.objects.stream().filter(o -> o.getClass().getSimpleName().equals("FireballRefill")).map(Entity.class::cast).findFirst().orElseThrow();
    }

    /** A view below and right of the three: all off screen, edge points on the top edge, left to right ladder, refill, lift. */
    private void farView() { followCamera = false; view.set(1700, 600, 640, 360); }

    private void markThree(Level level) {
        var changed = level.fairy().beginChanged();
        changed.add(lift(level)); changed.add(ladder(level)); changed.add(refill(level));
    }

    /** The fairy's off-screen targets in the order she served them, with the frames she twinkled at each. */
    private static final class Trace {
        final Entity[] targets = new Entity[64]; final int[] twinkle = new int[64]; int n;
    }

    private Trace trace(Level level, int frames) {
        Trace t = new Trace();
        Entity last = null;
        for (int i = 0; i < frames; i++) {
            frame(level);
            var fairy = level.fairy();
            Entity target = fairy.offscreenTarget();
            if (target != null && target != last) t.targets[t.n++] = target;
            if (target != null && fairy.mode() == FairyGuide.Mode.TWINKLE) t.twinkle[t.n - 1]++;
            last = target;
        }
        return t;
    }

    private void waitForTwinkleAt(Level level, Entity target) {
        int guard = 0;
        while (level.fairy().offscreenTarget() != target || level.fairy().mode() != FairyGuide.Mode.TWINKLE) { frame(level); assertTrue(guard++ < 400, "never twinkled at " + target); }
    }

    @Test void threeOffScreenTargetsAreShownNearestFirstThenClockwiseWithTheWrapAndADwellEach() {
        Level level = route();
        farView();
        markThree(level);
        Trace t = trace(level, 215); // 3.58 s: inside the 3 x (0.8 + 0.4) = 3.6 s cue
        assertTrue(t.n >= 3, "all three shown, got " + t.n);
        assertSame(lift(level), t.targets[0], "the nearest to the view centre first");
        assertSame(ladder(level), t.targets[1], "then the wrap to the smallest angle");
        assertSame(refill(level), t.targets[2], "then clockwise: the next angle");
        int dwellFrames = Math.round(FairyGuide.DWELL_SECONDS * 60);
        for (int i = 0; i < 2; i++) assertEquals(dwellFrames, t.twinkle[i], 2, "dwell at target " + i + " is 0.8 s of twinkle");
        assertTrue(t.twinkle[2] >= dwellFrames - 2, "the last one also gets its full dwell inside the window: " + t.twinkle[2]);
    }

    @Test void betweenTwoEdgePointsSheFliesAndThenTwinklesAtTheNextOne() {
        Level level = route();
        farView();
        markThree(level);
        var fairy = level.fairy();
        waitForTwinkleAt(level, lift(level));
        float firstX = fairy.x();
        int guard = 0;
        while (fairy.offscreenTarget() == lift(level)) { frame(level); assertTrue(guard++ < 400); }
        assertSame(ladder(level), fairy.offscreenTarget());
        Vector2 goal = FairyGuide.edgePoint(view, EDGE, ladder(level).centerX(), ladder(level).centerY(), new Vector2());
        assertEquals(goal.x, fairy.goalX(), 1e-3f); assertEquals(goal.y, fairy.goalY(), 1e-3f);
        assertEquals(FairyGuide.Mode.FLY, fairy.mode(), "the hop is a flight, not a jump");
        assertTrue(Math.abs(firstX - fairy.x()) < Math.abs(firstX - goal.x) / 2, "she has barely left the old edge point on the frame of the hop");
        int flying = 0;
        while (fairy.mode() != FairyGuide.Mode.TWINKLE) { frame(level); flying++; assertTrue(flying < 100); }
        assertTrue(flying > 3 && flying < 60, "a short flight: " + flying + " frames");
        assertEquals(goal.x, fairy.x(), FairyGuide.TWINKLE_RANGE);
    }

    @Test void whileTheLeverIsHeldTheCycleWrapsAroundForever() {
        Level level = route();
        walkToLever(level);
        followCamera = false;
        view.set(0, 0, VIEW_W, VIEW_H); // the ladder and the lift both right of this view
        held.add(Input.Keys.W);
        Trace t = trace(level, 60 * 12);
        assertTrue(t.n >= 8, "twelve seconds of Up is many hops: " + t.n);
        for (int i = 0; i < t.n; i++) assertSame(i % 2 == 0 ? ladder(level) : lift(level), t.targets[i], "ladder, lift, ladder... at hop " + i);
        assertInstanceOf(Lever.class, level.fairy().selected());
    }

    @Test void aTargetThatComesIntoViewMidDwellEndsItAtOnceAndTheCycleGoesOn() {
        Level level = route();
        farView();
        markThree(level);
        var fairy = level.fairy();
        waitForTwinkleAt(level, ladder(level));
        frame(level); // some way into the ladder's dwell
        view.set(ladder(level).getX() - 40, ladder(level).getY() - 100, 100, 360); // the ladder is in view now; the refill and the lift are not
        frame(level); frame(level);
        assertNotSame(ladder(level), fairy.offscreenTarget(), "the one in view dropped out of the cycle");
        assertNotNull(fairy.offscreenTarget());
        for (int i = 0; i < 100; i++) { frame(level); assertNotSame(ladder(level), fairy.offscreenTarget(), "never again while in view"); }
    }

    @Test void aTargetThatLeavesTheViewJoinsAtTheNextHopNotMidDwell() {
        Level level = route();
        followCamera = false;
        view.set(1650, 50, 200, 100); // the refill (x 1712, y 112) and the lift are in view; only the ladder is off screen
        markThree(level);
        var fairy = level.fairy();
        waitForTwinkleAt(level, ladder(level));
        view.set(1650, 500, 200, 100); // now the refill and the lift are off screen too
        for (int i = 0; i < 20; i++) { frame(level); assertSame(ladder(level), fairy.offscreenTarget(), "no switch in the middle of the dwell"); }
        Trace t = trace(level, 120);
        boolean sawRefill = false, sawLift = false;
        for (int i = 0; i < t.n; i++) { sawRefill |= t.targets[i] == refill(level); sawLift |= t.targets[i] == lift(level); }
        assertTrue(sawRefill && sawLift, "both joined the cycle after the dwell");
    }

    @Test void thePostPullWindowGrowsWithTheNumberOffScreenButNeverBelowOneAndAHalfSeconds() {
        Level level = route();
        farView();
        markThree(level);
        var fairy = level.fairy();
        settle(level, 205); // 3.42 s
        assertNotNull(fairy.offscreenTarget(), "three targets: the cue is still on at 3.4 s");
        assertEquals(0, fairy.changedTargets().size, "while the in-view marks of D87 still end at 1.5 s");
        settle(level, 30);
        assertNull(fairy.offscreenTarget(), "and over by 3.9 s");
        Level one = route(); // one off screen: the D87 window
        followCamera = false;
        view.set(1650, 50, 200, 100);
        one.fairy().beginChanged().add(ladder(one));
        settle(one, 85);
        assertNotNull(one.fairy().offscreenTarget(), "one target: 1.4 s still on");
        settle(one, 10);
        assertNull(one.fairy().offscreenTarget(), "and over by 1.6 s, as in D87");
    }

    @Test void aSingleOffScreenTargetFliesTheD87PathAndNeverLeavesItsTwinkle() {
        Level level = route();
        walkToLever(level);
        followCamera = false; // the view stays where the lever put it, so the edge point is a constant
        held.add(Input.Keys.W);
        var fairy = level.fairy();
        frame(level);
        assertTrue(fairy.offscreenTarget() == lift(level), "the lift is the one off-screen mechanism");
        float px = fairy.x(), py = fairy.y();
        float k = 1 - (float) Math.exp(-14f * STEP);
        boolean arrived = false;
        for (int i = 0; i < 60 * 8; i++) {
            frame(level);
            assertSame(lift(level), fairy.offscreenTarget(), "the same one for as long as Up is held (no cycle with one)");
            arrived |= fairy.mode() == FairyGuide.Mode.TWINKLE;
            if (arrived) assertEquals(FairyGuide.Mode.TWINKLE, fairy.mode(), "once arrived she twinkles on every frame (frame " + i + ")");
            if (i < 20) { // the D87 smoothing toward the (slowly moving) edge point, step for step
                px += (fairy.goalX() - px) * k; py += (fairy.goalY() - py) * k;
                assertEquals(px, fairy.x(), 0f, "bit-for-bit the D87 flight at frame " + i); assertEquals(py, fairy.y(), 0f);
            }
        }
        assertEquals(FairyGuide.Mode.TWINKLE, fairy.mode());
        assertTrue(fairy.distanceToGoal() <= FairyGuide.TWINKLE_RANGE);
    }

    @Test void aRoomChangeEndsTheCycleAndTheNextOneStartsAtTheNearestAgain() {
        Level level = route();
        farView();
        markThree(level);
        var fairy = level.fairy();
        waitForTwinkleAt(level, ladder(level));
        level.enterNextRoom(new Player(h.assets(), h.game(), 0, 0));
        frame(level);
        assertNull(fairy.offscreenTarget());
        assertNotEquals(FairyGuide.Mode.TWINKLE, fairy.mode());
        assertEquals(0, fairy.changedTargets().size);
    }

    /** Puts the lever's trigger (a static entity; the ladder and the lift put themselves back each update) and the refill at the given top-left corners. */
    private Entity trigger(Level level) { return level.triggers.get(0); }

    private void markStatic(Level level) {
        var changed = level.fairy().beginChanged();
        changed.add(lift(level)); changed.add(trigger(level)); changed.add(refill(level));
    }

    private void place(Level level, float triggerX, float triggerY, float refillX, float refillY) {
        trigger(level).position.set(triggerX, triggerY); refill(level).position.set(refillX, refillY);
    }

    @Test void aSetThatCrossesThePlusMinusPiSeamIsVisitedClockwiseThroughIt() {
        Level level = route();
        followCamera = false;
        view.set(980, 520, 640, 360); // centre (1300, 700); the lift stays at its place, centre (1952, 184): above right, -0.67
        place(level, 492, 724, 1292, 1492); // ladder left and a little below (+3.04), refill below (+1.57)
        markStatic(level);
        Trace t = trace(level, 215);
        assertTrue(t.n >= 3, "three shown: " + t.n);
        assertSame(refill(level), t.targets[0], "the nearest (below) first, +1.57");
        assertSame(trigger(level), t.targets[1], "then +3.04, just short of the seam");
        assertSame(lift(level), t.targets[2], "then through +-pi to -0.67");
    }

    @Test void targetsOnOneRayAreVisitedByIncreasingX() {
        Level level = route();
        followCamera = false;
        view.set(0, 4, 640, 360); // centre (320, 184): all three on the horizontal ray to the right, the lift (centre y 184) at x 1952
        place(level, 1492, 176, 992, 176);
        markStatic(level);
        Trace t = trace(level, 215);
        assertTrue(t.n >= 3, "three shown: " + t.n);
        assertSame(refill(level), t.targets[0]); assertSame(trigger(level), t.targets[1]); assertSame(lift(level), t.targets[2]);
    }

    @Test void releasingUpMidCycleEndsTheCueAndTheNextHoldStartsAtTheNearest() {
        Level level = route();
        walkToLever(level);
        followCamera = false;
        view.set(0, 0, VIEW_W, VIEW_H); // ladder (nearer) and lift off screen
        held.add(Input.Keys.W);
        var fairy = level.fairy();
        waitForTwinkleAt(level, ladder(level));
        int guard = 0;
        while (fairy.offscreenTarget() == ladder(level)) { frame(level); assertTrue(guard++ < 200); }
        assertSame(lift(level), fairy.offscreenTarget(), "the cycle moved on to the lift");
        held.clear();
        settle(level, 20);
        assertNull(fairy.offscreenTarget(), "Up released: the cue is over");
        held.add(Input.Keys.W);
        frame(level);
        assertSame(ladder(level), fairy.offscreenTarget(), "a new cycle starts at the nearest, not after the lift");
    }

    @Test void aNewPullMidCycleThatKeepsTheTargetCarriesOnAndOneThatDropsItStartsAtTheNearest() {
        Level level = route();
        farView();
        markThree(level);
        var fairy = level.fairy();
        waitForTwinkleAt(level, ladder(level));
        markThree(level); // the same lever pulled again: the cue is renewed, the current target is still in it
        frame(level);
        assertSame(ladder(level), fairy.offscreenTarget(), "still in the set: no hop in the middle of its dwell");
        waitForTwinkleAt(level, refill(level)); // the cycle went on, and the window restarted with the pull
        var only = fairy.beginChanged();
        only.add(lift(level)); // another lever: a set without the current target
        frame(level);
        assertSame(lift(level), fairy.offscreenTarget());
    }

    @Test void aLiftSwingingPastAStaticTargetsAngleDuringItsDwellDoesNotMakeTheNextHopSkipIt() {
        Level level = route();
        followCamera = false;
        view.set(0, 0, 640, 360); // centre (320, 180)
        place(level, 1536, 144, 1692, 162); // ladder a=-0.023 (nearest), refill a=-0.007, the lift (a=+0.002) where it is
        markStatic(level);
        var fairy = level.fairy();
        waitForTwinkleAt(level, refill(level));
        for (int i = 0; i <= 40; i++) { refill(level).position.y = 162 + 90 * i / 40f; frame(level); assertSame(refill(level), fairy.offscreenTarget()); } // its angle goes to +0.058, past the lift's
        int guard = 0;
        while (fairy.offscreenTarget() == refill(level)) { refill(level).position.y = 252; frame(level); assertTrue(guard++ < 200); }
        assertSame(lift(level), fairy.offscreenTarget(), "the lift (a = +0.002) is next; the wrap to the ladder would skip it");
    }
}
