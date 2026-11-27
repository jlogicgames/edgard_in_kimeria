package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
import com.jlogicsoftware.kimeria.effects.ScreenShake;
import com.jlogicsoftware.kimeria.effects.KimeriaParticleSystem;
import com.jlogicsoftware.kimeria.entity.enemy.Beetle;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.ExpectedWarnings;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The forest beetle (#327, D100), driven headlessly through real Level updates with ordinary keyboard controls.
 *
 * <p>Fixture beetle-test.tmx: room 640 wide, floor y 320, hero enters at x 32 (body x 50..61); the beetle spawns at x 320
 * (body x 321..343, centre 332) with offNeg 4 and offPos 4, so its {@code position.x} stays in 256..384 (body from 257 at
 * the left end, centre 268, trigger box from 249, blast dome 228..308 wide and 32 high there); a resting stone at x 96. Since D101 a beetle killed
 * quietly leaves its body, which rocks when the hero comes near and is crumbled by the sword or a stomp and burnt by a fireball or a thrown torch, which fly on; a stone passes
 * through it (D119).
 */
class BeetleTest {
    private static final float STEP = HeadlessLevelHarness.STEP;
    private static final float FLOOR = 320, LEFT_END = 256, RIGHT_END = 384, SPAWN_X = 320, LEFT_END_CENTRE = 268;
    /** Hero body right edges: just inside the trigger (249) at the left end, and just outside it but inside the sword's reach. */
    private static final float TOUCHING = 250, SWORD_RANGE = 242, HEARD = 247;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();

    @BeforeEach void setUp() { harness.setUp(); harness.playSounds = true; }
    @AfterEach void tearDown() { harness.restoreGlobals(); }

    private Level level() { return harness.roomOf("beetle-test", 0); }

    private static Beetle beetle(Level level) {
        for (int i = 0; i < level.objects.size(); i++) if (level.objects.get(i) instanceof Beetle b) return b;
        return null;
    }

    private void frame(Level level) { level.update(STEP); harness.pressed.clear(); }
    private void settle(Level level, int frames) { for (int i = 0; i < frames; i++) frame(level); }
    private static boolean alive(Level level) { return !level.player.isGotHit(); }

    /** Stands the hero (body right edge at {@code right}) on the floor, still, facing right. */
    private void place(Level level, float right) {
        level.player.position.set(right - 29, FLOOR - 48);
        level.player.velocity().setZero();
        level.player.facingRight = true;
        harness.held.clear();
    }

    private void setLonger(boolean longer) throws Exception {
        var field = TrapWarnings.class.getDeclaredField("longer");
        field.setAccessible(true);
        field.setBoolean(harness.game().trapWarnings(), longer);
    }

    /** Frames until {@code condition} holds, at most {@code limit}; fails if it never does. */
    private int until(Level level, java.util.function.BooleanSupplier condition, int limit) {
        for (int f = 0; f < limit; f++) {
            if (condition.getAsBoolean()) return f;
            frame(level);
        }
        fail("condition never held in " + limit + " frames");
        return -1;
    }

    /** Brings the beetle to its left end and makes it commit with the hero standing at {@code TOUCHING}. */
    private Beetle committed(Level level) {
        place(level, TOUCHING);
        Beetle b = beetle(level);
        until(level, b::isCommitted, 60 * 5);
        return b;
    }

    @Test void crawlsInsideItsRangeAndNeverHearsAFarHero() {
        Level level = level();
        Beetle b = beetle(level);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int f = 0; f < 60 * 20; f++) {
            frame(level);
            min = Math.min(min, b.position.x); max = Math.max(max, b.position.x);
            assertEquals(Beetle.State.CRAWL, b.getState());
        }
        assertEquals(LEFT_END, min, .01f, "reaches its left end");
        assertEquals(RIGHT_END, max, .01f, "reaches its right end");
        assertTrue(harness.sounds.isEmpty(), "a calm beetle is silent");
    }

    @Test void noticesInBodyLanguageThenChasesAndWaitsAtItsRangeEnd() {
        Level level = level();
        Beetle b = beetle(level);
        place(level, HEARD);  // hero centre 241.5: 90.5 px from the beetle's centre 332, inside the 96 px hearing
        frame(level);
        assertEquals(Beetle.State.NOTICE, b.getState());
        assertEquals(java.util.List.of("beetle_notice"), harness.sounds, "its own call, no shared alert sound");
        assertFalse(b.facingRight, "it turns to the hero");
        float x = b.position.x;
        int frames = until(level, () -> b.getState() == Beetle.State.CHASE, 60);
        assertEquals(Beetle.NOTICE_TOTAL, frames * STEP, STEP * 1.5f, "the notice lasts 0.5 s");
        assertEquals(x, b.position.x, .01f, "it does not move while it notices");
        float before = b.position.x;
        frame(level);
        assertEquals(Beetle.CHASE_SPEED * STEP, before - b.position.x, .01f, "chases at 70 px/s");
        settle(level, 60 * 3);
        assertEquals(LEFT_END, b.position.x, .01f, "waits at its range end");
        assertEquals(Beetle.State.CHASE, b.getState(), "still facing the hero, not committed: the hero is outside its trigger");
        assertTrue(alive(level));
    }

    @Test void touchIsHarmless() {
        Level level = level();
        Beetle b = beetle(level);
        Rectangle body = b.hitboxBounds();
        place(level, body.x + 6);  // the hero's body overlaps the calm beetle's body
        settle(level, 10);
        assertTrue(alive(level), "touching a beetle never kills");
    }

    @Test void commitsOnTheSpotAndBurstsAfterTheSharedWarning() throws Exception {
        for (boolean longer : new boolean[]{false, true}) {
            setLonger(longer);
            harness.sounds.clear();
            Level level = level();
            Beetle b = committed(level);
            assertEquals(ExpectedWarnings.standard(longer), b.warningTotal(), 1e-6f);
            assertTrue(harness.sounds.contains(longer ? "beetle_windup_long" : "beetle_windup"), harness.sounds.toString());
            float x = b.position.x;
            place(level, TOUCHING - 3);  // the hero steps back a little, still inside the blast: it stays where it is
            int frames = until(level, b::isRemoved, 120);
            assertEquals(ExpectedWarnings.standard(longer), frames * STEP, STEP * 1.5f, "bursts when the warning ends");
            assertEquals(x, b.position.x, .01f, "committed on the spot");
            assertFalse(alive(level), "a hero inside the dome at the instant dies");
            assertTrue(harness.sounds.contains("beetle_pop"));
            assertTrue(b.isDefeated(), "a burst beetle counts as defeated");
        }
        setLonger(false);
    }

    @Test void runningAwayDuringTheWarningSurvives() {
        Level level = level();
        Beetle b = committed(level);
        harness.held.add(Input.Keys.A);   // run left with ordinary controls
        until(level, b::isRemoved, 120);
        harness.held.clear();
        assertTrue(alive(level), "out of the blast at the instant");
        Rectangle blast = new Rectangle(LEFT_END_CENTRE - BeetleFx.BLAST_WIDTH / 2, FLOOR - BeetleFx.BLAST_HEIGHT,
            BeetleFx.BLAST_WIDTH, BeetleFx.BLAST_HEIGHT);
        assertFalse(blast.overlaps(level.player.hitboxBounds()));
    }

    @Test void jumpingOverTheBurstSurvives() {
        Level level = level();
        Beetle b = committed(level);
        until(level, () -> b.warningRemaining() <= .4f, 120);
        harness.held.add(Input.Keys.J); harness.pressed.add(Input.Keys.J);
        until(level, b::isRemoved, 120);
        harness.held.clear();
        assertTrue(alive(level), "the feet are above the 32 px dome at the instant");
    }

    /** The hero's body 11 px wide, its near edge {@code out} px to a side of the beetle's middle, its feet {@code up} px over the floor. */
    private boolean survivesABurst(float side, float out, float up) {
        Level level = level();
        Beetle b = beetle(level);
        Rectangle hero = level.player.hitboxBounds();
        float left = side > 0 ? b.centreX() + out : b.centreX() - out - hero.width;
        level.player.position.add(left - hero.x, FLOOR - up - (hero.y + hero.height));
        b.burst();
        return alive(level);
    }

    /** D149: what kills is the dome that is drawn, 40 px out along the floor and 32 px up; the corners of its bounds are safe. */
    @Test void aHeroInACornerOutsideTheDomeSurvivesAndInsideItDies() {
        float up = 20, rim = 40 * (float) Math.sqrt(1 - (up / 32) * (up / 32));  // 31.2 px out at 20 px up
        for (float side : new float[]{-1, 1}) {
            assertTrue(survivesABurst(side, rim + 1, up), "in the corner, outside the dome (side " + side + ")");
            assertTrue(survivesABurst(side, 39, up), "deep in the corner (side " + side + ")");
            assertFalse(survivesABurst(side, rim - 1, up), "inside the dome (side " + side + ")");
            assertFalse(survivesABurst(side, 39.5f, 0), "on the floor the reach is still 40 px (side " + side + ")");
            assertTrue(survivesABurst(side, 40, 0), "touching the edge is not inside (side " + side + ")");
        }
        assertFalse(survivesABurst(1, -5, 31.5f), "over its middle the dome is still 32 px high");
        assertTrue(survivesABurst(1, -5, 32), "feet at its top are clear (Y grows downward: up is a smaller y)");
    }

    /** D100 kept: the trigger is an inner part of the blast, so a hero who has just set it off is within reach. */
    @Test void theTriggerBoxLiesInsideTheDome() {
        Beetle b = beetle(level());
        Rectangle trigger = b.triggerBoxInto(new Rectangle()), corner = new Rectangle(0, trigger.y, .1f, .1f);
        for (float x : new float[]{trigger.x, trigger.x + trigger.width - corner.width})
            assertTrue(Beetle.reaches(corner.setX(x), b.centreX(), b.floorY()), "the trigger's top corner at " + x);
    }

    /**
     * D100, D149: melee stays punished with no special case. The sword reaches the beetle from at most 28 px off its middle,
     * well inside the dome: at the very tip of its reach, from either side, from a step up to the beetle's body height and from
     * as far below as the old box caught, the swinging hero dies.
     */
    @Test void theSwordAtTheTipOfItsReachStillCatchesItsHero() {
        float bodyLeft = LEFT_END_CENTRE - 11, bodyRight = LEFT_END_CENTRE + 11, reach = 17, inside = .1f;
        for (boolean fromLeft : new boolean[]{true, false}) for (float up : new float[]{0, 7, 13.9f, -10, -21.9f}) {
            Level level = level();
            Beetle b = beetle(level);
            place(level, HEARD);
            until(level, () -> b.position.x <= LEFT_END + .01f && b.getState() == Beetle.State.CHASE, 60 * 5);
            place(level, fromLeft ? bodyLeft - reach + inside : bodyRight + reach - inside + 11);
            level.player.facingRight = fromLeft;
            settle(level, 2);
            assertFalse(b.isRemoved(), "waiting at its range end");
            level.player.position.y -= up;
            harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level); harness.held.clear();
            assertTrue(b.isRemoved(), "the tip of the sword reaches it (from left " + fromLeft + ", " + up + " px up)");
            assertFalse(alive(level), "and its dome reaches the hero (from left " + fromLeft + ", " + up + " px up)");
        }
    }

    @Test void aFireballKillsItWithoutABurstEvenWhileItWindsUp() {
        for (boolean windingUp : new boolean[]{false, true}) {
            harness.sounds.clear();
            Level level = level();
            Beetle b = beetle(level);
            if (windingUp) committed(level);
            else { place(level, 200); until(level, () -> b.getState() == Beetle.State.CHASE, 60 * 5); }
            settle(level, 2);
            harness.held.clear(); harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
            until(level, b::isDefeated, 60);
            assertEquals(Beetle.State.DEAD, b.getState(), "killed, not burst");
            assertEquals(0, kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_DOME), "no burst flash (specks of the wind-up may still be fading)");
            assertTrue(harness.sounds.contains("bounce"), "the kill sound");
            assertFalse(harness.sounds.contains("beetle_pop"), "no burst sound");
            settle(level, 60 * 2);
            assertTrue(alive(level), "the hero lives (windingUp " + windingUp + ")");
            assertFalse(b.isRemoved(), "its body stays (D101)");
            assertTrue(b.isRemains());
            assertEquals(0, level.bloodParticles().count(), "no blood");
        }
    }

    /** #503: the beetle's burst is resolved in its own update, before the hero's; a cast on that frame finds a dead hero and spawns nothing. */
    @Test void aCastOnTheBurstFrameSpawnsNoBallAndSpendsNoCharge() {
        Level probe = level();
        committed(probe);
        int due = until(probe, () -> !alive(probe), 60 * 5);   // frames from the commit to the burst that kills the hero
        Level level = level();
        committed(level);
        for (int f = 0; f < due; f++) frame(level);
        assertFalse(alive(level), "the burst killed the hero on this frame");
        int stock = level.rangedStock.count();
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        assertEquals(stock, level.rangedStock.count(), "no charge spent after the burst");
        for (int i = 0; i < level.objects.size(); i++)
            assertFalse(level.objects.get(i) instanceof com.jlogicsoftware.kimeria.entity.items.Fireball, "no ball was cast");
    }

    @Test void aThrownStoneKillsItWithoutABurst() {
        Level level = level();
        Beetle b = beetle(level);
        place(level, 108);
        harness.held.clear(); frame(level); harness.held.add(Input.Keys.W); frame(level); // Up alone interacts (D126)
        harness.held.clear(); frame(level);
        assertNotNull(level.player.carriedObject(), "picked up the stone");
        place(level, 190);
        level.player.position.x = 190 - 29;
        until(level, () -> b.getState() == Beetle.State.CHASE, 60 * 3);
        settle(level, 30);
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        until(level, b::isDefeated, 120);
        assertEquals(Beetle.State.DEAD, b.getState());
        assertFalse(harness.sounds.contains("beetle_pop"));
        settle(level, 60);
        assertTrue(alive(level));
    }

    @Test void theSwordBurstsItAtOnceInEveryStateWithoutBlood() {
        for (boolean windingUp : new boolean[]{false, true}) {
            Level level = level();
            Beetle b = beetle(level);
            if (windingUp) committed(level);
            else { place(level, SWORD_RANGE); until(level, () -> b.position.x <= LEFT_END + .01f && b.getState() == Beetle.State.CHASE, 60 * 5); }
            harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level); harness.held.clear();
            settle(level, 20);
            assertTrue(b.isRemoved(), "burst at once (windingUp " + windingUp + ")");
            assertTrue(flames(level.beetleFx().hard()) > 0 || level.beetleFx().soft().count() > 0, "the burst's effects");
            assertFalse(alive(level), "the swordsman is inside the dome");
            assertEquals(0, level.bloodParticles().count(), "no blood from the beetle");
        }
    }

    /**
     * The sword on a live beetle bursts it (D100), as on main before #328: it never leaves a body, so the swing's rule of not
     * crumbling the body of the monster it has just killed (D101 update, #328) changes nothing for the beetle. Pinned over the
     * whole swing: no beetle and no body at any frame after the hit.
     */
    @Test void aSwordKillLeavesNoBeetleBodyAtAnyFrameOfTheSwing() {
        Level level = level();
        Beetle b = beetle(level);
        place(level, SWORD_RANGE);
        until(level, () -> b.position.x <= LEFT_END + .01f && b.getState() == Beetle.State.CHASE, 60 * 5);
        harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level); harness.held.clear();
        assertTrue(b.isRemoved(), "burst on the hit");
        for (int f = 0; f < 45; f++) {
            assertNull(beetle(level), "no beetle and no body, frame " + f);
            assertFalse(b.isRemains());
            frame(level);
        }
    }

    @Test void aStompOnACalmBeetleBurstsItAtOnce() {
        Level level = level();
        Beetle b = beetle(level);
        Rectangle body = b.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - 23.5f, body.y - 48 - 20);  // feet 20 px above its top, over its middle
        level.player.velocity().setZero();
        until(level, b::isRemoved, 60);
        assertFalse(alive(level), "a stomp before it commits bursts it under the hero");
    }

    @Test void aStompOnACommittedBeetleBouncesTheHeroOff() {
        Level level = level();
        Beetle b = committed(level);
        Rectangle body = b.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - 23.5f, body.y - 48 - 12);
        level.player.velocity().setZero();
        until(level, () -> level.player.velocity.y < 0, 30);
        assertTrue(b.isCommitted(), "a stomp does not kill a committed beetle");
        assertFalse(b.isDefeated());
    }

    /** A beetle shot at its left end while it waits for the hero at 200: its body lies at x 257..279, the hero clear of it. */
    private Beetle body(Level level) {
        Beetle b = beetle(level);
        place(level, 200);
        until(level, () -> b.getState() == Beetle.State.CHASE, 60 * 5);
        until(level, () -> b.position.x <= LEFT_END + .01f, 60 * 5);
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        until(level, b::isRemains, 60);
        settle(level, 60);
        return b;
    }

    private static boolean fireballFlying(Level level) {
        for (int i = 0; i < level.objects.size(); i++)
            if (level.objects.get(i) instanceof com.jlogicsoftware.kimeria.entity.items.Fireball ball && !ball.isRemoved()) return true;
        return false;
    }

    @Test void aQuietKillFlipsItThenTheBodyFadesIntoTheBodySheetAndThePoofComesAMomentLater() {
        Level level = level();
        Beetle b = beetle(level);
        place(level, 200);
        until(level, () -> b.getState() == Beetle.State.CHASE, 60 * 5);
        b.collidedWithActor(true);
        assertEquals(0, b.bodyFade(), 0, "the active dead frame at the kill");
        int smokeAtKill = kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_SMOKE);
        frame(level);
        assertEquals(smokeAtKill, kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_SMOKE), "no poof on the hit frame: the fireball's flash first");
        settle(level, (int) Math.ceil(Beetle.POOF_DELAY / STEP) + 1);
        assertTrue(kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_SMOKE) > smokeAtKill, "the poof follows");
        settle(level, (int) Math.ceil((Beetle.BODY_FADE_DELAY + Beetle.BODY_FADE_SECONDS) / STEP));
        assertEquals(1, b.bodyFade(), 0, "settled into the body sheet");
        assertTrue(b.isDefeated(), "counted as defeated for the room (D6)");
    }

    @Test void theBodyIsHarmlessAndBlocksNothing() {
        Level level = level();
        Beetle b = body(level);
        harness.held.add(Input.Keys.D);
        until(level, () -> level.player.hitboxBounds().x > b.hitboxBounds().x + b.hitboxBounds().width, 60 * 3);
        harness.held.clear();
        assertTrue(alive(level), "walking through a body is safe");
        assertTrue(b.isRemains(), "and leaves it lying");
    }

    @Test void theHeroPassingRocksTheBodyTheWayItWalksAndTheSwingSettles() {
        Level level = level();
        Beetle b = body(level);
        assertEquals(0, b.rockAngle(), 0, "still before the hero comes");
        harness.held.add(Input.Keys.D);
        float max = 0;
        for (int f = 0; f < 60 && max == 0; f++) { frame(level); max = Math.max(max, b.rockAngle()); }
        harness.held.clear();
        place(level, 200);
        for (int f = 0; f < 20; f++) { frame(level); max = Math.max(max, b.rockAngle()); }
        assertTrue(max > Beetle.KICK_DEGREES * .6f && max <= Beetle.KICK_DEGREES * 1.1f, "a push to the right, about the kick: " + max);
        settle(level, (int) (1.6f / STEP));
        assertEquals(0, b.rockAngle(), 0, "settled in about 1.4 s");
    }

    @Test void aStompOnTheBodyBouncesTheHeroOffAndCrumblesIt() {
        Level level = level();
        Beetle b = body(level);
        harness.sounds.clear();
        Rectangle body = b.hitboxBounds();
        level.player.position.set(body.x + body.width / 2 - 23.5f, body.y - 48 - 20);
        level.player.velocity().setZero();
        boolean bounced = false;
        for (int f = 0; f < 60 && !b.isRemoved(); f++) { frame(level); bounced |= level.player.velocity.y < 0; }
        assertTrue(b.isRemoved(), "a stomp crumbles the body, as the sword does");
        assertTrue(bounced, "the normal stomp bounce");
        assertTrue(kinds(level.remainsFx().hard(), KimeriaParticleSystem.KIND_GRIT) >= 10, "brown chips fall");
        assertTrue(harness.sounds.contains(Beetle.CRUMBLE_SOUND));
        assertTrue(alive(level));
    }

    @Test void theSwordCrumblesTheBody() {
        Level level = level();
        Beetle b = body(level);
        harness.sounds.clear();
        place(level, 254);
        harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level); harness.held.clear();
        until(level, b::isRemoved, 30);
        assertTrue(kinds(level.remainsFx().hard(), KimeriaParticleSystem.KIND_GRIT) >= 10, "brown chips fall");
        assertTrue(harness.sounds.contains(Beetle.CRUMBLE_SOUND));
        assertFalse(harness.sounds.contains("beetle_pop"), "a body does not burst");
        assertTrue(alive(level));
        assertEquals(0, level.bloodParticles().count(), "no blood");
    }

    /** D119: a thrown stone flies through the body; the body stays whole and the stone is not stopped. */
    @Test void aThrownStonePassesThroughTheBodyAndLeavesItIntact() {
        Level level = level();
        Beetle b = body(level);
        harness.sounds.clear();
        place(level, 108);
        harness.held.clear(); frame(level); harness.held.add(Input.Keys.W); frame(level); // Up alone interacts (D126)
        harness.held.clear(); frame(level);
        var stone = level.player.carriedObject();
        assertNotNull(stone, "picked up the stone");
        place(level, 200);
        settle(level, 5);
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        float bodyRight = b.hitboxBounds().x + b.hitboxBounds().width;
        float vx = stone.snapshot().velocityX();
        assertTrue(vx > 0, "the stone is thrown toward the body");
        boolean passed = false;
        for (int f = 0; f < 120 && !passed; f++) {
            frame(level);
            passed = stone.getX() > bodyRight;
            assertTrue(b.isRemains() && !b.isRemoved(), "the stone leaves the body whole (frame " + f + ")");
            assertEquals(vx, stone.snapshot().velocityX(), 0f, "the body neither stops nor slows the stone (frame " + f + ")");
        }
        assertTrue(passed, "the stone flew past the body, not stopped by it");
        assertFalse(harness.sounds.contains(Beetle.CRUMBLE_SOUND));
    }

    /** D119 (D101 kept): a fireball burns the body and flies on at the same speed, never stopped or spent. */
    @Test void aFireballBurnsTheBodyAndFliesOnUnslowed() {
        Level level = level();
        Beetle b = body(level);
        harness.sounds.clear();
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        float bodyRight = b.hitboxBounds().x + b.hitboxBounds().width;
        com.jlogicsoftware.kimeria.entity.items.Fireball ball = null;
        float lastX = Float.NaN, step = Float.NaN;
        boolean burnt = false;
        int maxFlames = 0;
        for (int f = 0; f < 90; f++) {
            frame(level);
            for (int i = 0; i < level.objects.size(); i++)
                if (level.objects.get(i) instanceof com.jlogicsoftware.kimeria.entity.items.Fireball fb && !fb.isRemoved()) ball = fb;
            if (b.getState() == Beetle.State.BURN) {
                burnt = true;
                assertFalse(b.isRemains(), "a burning body is out of reach (frame " + f + ")");
                maxFlames = Math.max(maxFlames, flames(level.remainsFx().hard()));
            }
            if (ball != null) {
                assertFalse(ball.isRemoved(), "the ball is not spent on the body (frame " + f + ")");
                if (!Float.isNaN(lastX)) {
                    float dx = ball.getX() - lastX;
                    if (Float.isNaN(step)) step = dx;
                    assertEquals(step, dx, 1e-3f, "the body neither stops nor slows the ball (frame " + f + ")");
                }
                lastX = ball.getX();
                if (ball.getX() > bodyRight + 8) break;
            }
        }
        assertNotNull(ball, "the ball is flying");
        assertTrue(step > 0, "the ball flies toward the body");
        assertTrue(ball.getX() > bodyRight, "the ball got past the body, unspent");
        assertTrue(burnt, "the body burns");
        assertTrue(harness.sounds.contains(Beetle.BURN_SOUND));
        for (int f = 0; f < 3 && !b.isRemoved(); f++) { frame(level); maxFlames = Math.max(maxFlames, flames(level.remainsFx().hard())); }
        assertTrue(maxFlames > 0, "flames in the fireball's ramp");
        until(level, b::isRemoved, (int) Math.ceil(Beetle.BURN_SECONDS / STEP) + 2);
        assertTrue(alive(level));
    }

    /** D119: a monster moving through the body rocks it, as the hero does. */
    @Test void aMonsterWalkingThroughTheBodyRocksIt() {
        Level level = level();
        Beetle dead = body(level);
        assertEquals(0, dead.rockAngle(), 0);
        Rectangle box = dead.hitboxBounds();
        Beetle walker = new Beetle(harness.assets(), harness.game(), box.x + 60, box.y - 2, 24, 16, 60, 0);
        walker.setPlayer(level.player);
        walker.setLevel(level);
        level.objects.add(walker);
        place(level, 32 + 29);
        float max = 0;
        for (int f = 0; f < 240 && max == 0; f++) { frame(level); max = Math.max(max, Math.abs(dead.rockAngle())); }
        assertTrue(max > 0, "the walking beetle rocked the body");
    }

    @Test void aThrownTorchBurnsTheBodyAndFliesOn() {
        Level level = level();
        Beetle b = body(level);
        harness.sounds.clear();
        Rectangle body = b.hitboxBounds();
        var torch = new com.jlogicsoftware.kimeria.entity.items.Carryable(level, 9001,
            com.jlogicsoftware.kimeria.entity.items.Carryable.Kind.TORCH, body.x - 14, body.y + 2);
        level.objects.add(torch);
        torch.release(body.x - 14, body.y + 2, true, true);
        until(level, () -> b.getState() == Beetle.State.BURN || b.isRemoved(), 30);
        assertEquals(Beetle.State.BURN, b.getState(), "fire burns a body: the torch too (D101)");
        assertTrue(harness.sounds.contains(Beetle.BURN_SOUND));
        assertTrue(torch.snapshot().velocityX() != 0, "a body stops nothing: the torch flies on");
    }

    @Test void theSwordReachesALiveBeetleBehindABody() {
        Level level = level();
        Beetle dead = body(level);
        Rectangle body = dead.hitboxBounds();
        Beetle live = new Beetle(harness.assets(), harness.game(), body.x, body.y - 2, 24, 16, 0, 0);
        live.setPlayer(level.player);
        live.setLevel(level);
        level.objects.add(live);   // after the body in the list: a body that shielded would be met first
        place(level, body.x - 3);
        harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level); harness.held.clear();
        settle(level, 5);
        assertTrue(live.isRemoved(), "the swing goes on past the body and bursts the live beetle (D100, D101)");
        assertTrue(dead.isRemoved(), "and crumbles the body on its way");
    }

    @Test void aStompOnTheBodyWorksFromBesideItToo() {
        Level level = level();
        Beetle b = body(level);
        Rectangle body = b.hitboxBounds();
        place(level, body.x + 1);    // already inside the body's near box when it lands
        frame(level);
        level.player.position.y = body.y - 48 - 1;
        level.player.velocity().set(0, 200);
        boolean bounced = false;
        for (int f = 0; f < 30 && !b.isRemoved(); f++) { frame(level); bounced |= level.player.velocity.y < 0; }
        assertTrue(b.isRemoved() && bounced, "a stomp is checked every frame, not only when the hero first comes near");
    }

    @Test void anEarlyBurnOrCrumbleKeepsThePoofAndTheFlip() {
        for (boolean burn : new boolean[]{true, false}) {
            Level level = level();
            Beetle b = beetle(level);
            place(level, 200);
            until(level, () -> b.getState() == Beetle.State.CHASE, 60 * 5);
            b.collidedWithActor(true);
            int smoke = kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_SMOKE);
            if (burn) b.burnUp(); else b.crumble();
            assertTrue(kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_SMOKE) > smoke, "the kill's poof still plays (burn " + burn + ")");
            if (burn) {
                frame(level);
                assertTrue(b.bodyFade() < 1, "an early burn keeps the hold and the fade instead of jumping to the body");
            }
        }
    }

    @Test void aBodyFallsToTheFloorAndOneBelowTheRoomIsGone() {
        Level level = level();
        Beetle b = beetle(level);
        place(level, 200);
        until(level, () -> b.getState() == Beetle.State.CHASE, 60 * 5);
        b.position.y -= 30;   // killed in the air, as on a falling platform
        b.collidedWithActor(true);
        settle(level, 60);
        assertEquals(FLOOR, b.floorY(), .5f, "the body falls and lies on the floor");
        assertFalse(b.isRemoved());
        b.position.y = level.currentRoomBounds().y + level.currentRoomBounds().height + 1;
        frame(level);
        assertTrue(b.isRemoved(), "a body below the room (over a pit) is gone");
    }

    @Test void theBodyDoesNotOutliveARoomResetNorAPassedRoom() {
        Level level = level();
        Beetle b = body(level);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        Beetle fresh = beetle(level);
        assertNotNull(fresh, "a death rebuilds the room as authored: the beetle is back alive");
        assertNotSame(b, fresh);
        assertEquals(Beetle.State.CRAWL, fresh.getState());
        Level passed = level();
        body(passed);
        passed.secureVisit();
        passed.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        assertNull(beetle(passed), "killed in a passed room: no beetle and no body (D6)");
    }

    @Test void itLosesAHeroWhoGetsFar() {
        Level level = level();
        Beetle b = beetle(level);
        place(level, HEARD);
        until(level, () -> b.getState() == Beetle.State.CHASE, 60);
        place(level, 61);   // back at the entrance: 200+ px away
        frame(level);
        assertEquals(Beetle.State.CRAWL, b.getState());
    }

    @Test void aDeathResetsTheRoomAndItsBeetle() {
        Level level = level();
        Beetle burst = committed(level);
        until(level, burst::isRemoved, 120);
        assertNull(beetle(level), "a burst leaves no body (D101)");
        assertTrue(level.beetleFx().soft().count() + level.beetleFx().hard().count() > 0);
        level.resetCurrentRoom(new Player(harness.assets(), harness.game(), 0, 0));
        Beetle fresh = beetle(level);
        assertNotNull(fresh, "rebuilt from the map");
        assertNotSame(burst, fresh);
        assertEquals(SPAWN_X, fresh.position.x, .01f);
        assertEquals(Beetle.State.CRAWL, fresh.getState());
        assertEquals(0, level.beetleFx().soft().count() + level.beetleFx().hard().count(), "effects cleared with the room");
    }

    @Test void aBeetleLeftWindingUpReturnsCalmAtItsSpawn() {
        Level level = level();
        Beetle b = committed(level);
        b.revisit(level.player);
        assertEquals(Beetle.State.CRAWL, b.getState());
        assertEquals(SPAWN_X, b.position.x, .01f);
        assertEquals(0, b.warningRemaining(), 0);
    }

    /** The burst's shake (#559, D136): only a near hero who survives, only with the option on. */
    private static final Rectangle ROOM = new Rectangle(0, 0, 4000, 4000), VIEW = new Rectangle(100, 100, 640, 360);

    @Test void aNearMissShakesTheCameraByTheBurstNumbers() {
        Level level = level();
        Beetle b = committed(level);
        harness.held.add(Input.Keys.A);
        until(level, b::isRemoved, 120);
        harness.held.clear();
        assertTrue(alive(level));
        assertTrue(level.fx().shake().running(), "a survivor within range feels the burst");
        assertEquals(ScreenShake.BURST_AMPLITUDE_PX, level.fx().shake().currentAmplitude(), .1f, "the burst's amplitude, at most a frame decayed");
        boolean moved = false;
        for (int f = 0; f < 4; f++) { frame(level); moved |= level.fx().shake().offset(ROOM, VIEW).len() > 0; }
        assertTrue(moved, "the camera is displaced while it runs");
    }

    @Test void aLethalBurstStartsNoShake() {
        Level level = level();
        Beetle b = committed(level);
        until(level, b::isRemoved, 120);
        assertFalse(alive(level));
        assertFalse(level.fx().shake().running(), "the death beat is the feedback");
    }

    @Test void aFarHeroFeelsNoShake() {
        Level level = level();
        Beetle b = beetle(level);
        level.player.position.set(b.centreX() + Beetle.SHAKE_RANGE + 200, FLOOR - 48);
        Rectangle hero = level.player.hitboxBounds();
        assertTrue(Math.abs(hero.x + hero.width / 2f - b.centreX()) > Beetle.SHAKE_RANGE, "the hero is out of range");
        b.burst();
        assertFalse(level.fx().shake().running());
    }

    @Test void theShakeIsZeroWithTheAssistOptionOff() {
        for (boolean off : new boolean[]{false, true}) {
            harness.screenShakeDisabled = off;
            Level level = level();
            Beetle b = committed(level);
            harness.held.add(Input.Keys.A);
            until(level, b::isRemoved, 120);
            harness.held.clear();
            assertTrue(alive(level));
            assertTrue(level.fx().shake().running(), "the shake starts either way; the option only zeroes its offset");
            boolean moved = false;
            for (int f = 0; f < 4; f++) { frame(level); moved |= level.fx().shake().offset(ROOM, VIEW).len() > 0; }
            assertEquals(!off, moved, off ? "option off: no offset" : "option on: an offset");
        }
        harness.screenShakeDisabled = false;
    }

    @Test void theHaloReachesTheDomesFloorEndsAndTopAndStaysInsideItsBounds() {
        BeetleFx fx = new BeetleFx(5);
        float cx = 100, floor = 200, left = 1e9f, right = -1e9f, top = 1e9f;
        fx.burst(cx, floor);
        for (int f = 0; f < 150; f++) {   // 0.15 s of 1 ms steps: the 32 halo dots (the first hard particles) reach their rim at 0.16 s
            fx.update(.001f);
            for (int i = 0; i < 32; i++) {
                left = Math.min(left, fx.hard().x(i)); right = Math.max(right, fx.hard().x(i)); top = Math.min(top, fx.hard().y(i));
                assertTrue(fx.hard().y(i) <= floor + .01f, "never below the floor line");
            }
        }
        assertEquals(cx - 40, left, 3f);
        assertEquals(cx + 40, right, 3f);
        assertEquals(floor - 32, top, 3f);
        assertTrue(left >= cx - 40 && right <= cx + 40 && top >= floor - 32, "inside the blast's bounds");
    }

    @Test void loadTimeChecksNameTheProblem() {
        Rectangle room = new Rectangle(0, 0, 640, 368);
        java.util.List<Rectangle> floor = java.util.List.of(new Rectangle(0, 320, 640, 48));
        Map<String, Rectangle> standing = Map.of("entrance", new Rectangle(50, 298, 11, 22));
        assertNull(Beetle.problem(320, 304, 24, 16, 4, 4, floor, room, standing));
        assertNotNull(Beetle.problem(96, 304, 24, 16, 1, 1, floor, room, standing), "a burst could cover the entrance");
        assertNotNull(Beetle.problem(20, 304, 24, 16, 0, 1, floor, room, Map.of()), "the blast leaves the room");
        assertNotNull(Beetle.problem(320, 304, 24, 16, 4, 4, java.util.List.of(new Rectangle(0, 320, 300, 48),
            new Rectangle(316, 320, 324, 48)), room, Map.of()), "a gap in its floor");
        assertNotNull(Beetle.problem(320, 304, 24, 16, Float.NaN, 4, floor, room, Map.of()), "a broken range");
        // D149: the sweep is the dome's, centres 268..396. At 20 px up it reaches 31.2 px past an end, not the box's 40.
        for (float[] x : new float[][]{{268 - 32 - 11, 268 - 30 - 11}, {396 + 32, 396 + 30}}) {
            assertNull(Beetle.problem(320, 304, 24, 16, 4, 4, floor, room, Map.of("checkpoint", new Rectangle(x[0], 278, 11, 22))),
                "a place in a corner of the swept bounds is clear of every dome: x " + x[0]);
            assertNotNull(Beetle.problem(320, 304, 24, 16, 4, 4, floor, room, Map.of("checkpoint", new Rectangle(x[1], 278, 11, 22))),
                "a place inside the last dome: x " + x[1]);
        }
        assertNotNull(Beetle.problem(320, 304, 24, 16, 4, 4, floor, room, Map.of("checkpoint", new Rectangle(330, 267, 11, 22))),
            "between the range ends the sweep is 32 px high");
        assertNull(Beetle.problem(320, 304, 24, 16, 4, 4, floor, room, Map.of("checkpoint", new Rectangle(330, 266, 11, 22))));
        IllegalArgumentException entrance = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("beetle-entrance", 0));
        assertTrue(entrance.getMessage().contains("entrance"), entrance.getMessage());
        IllegalArgumentException gap = assertThrows(IllegalArgumentException.class, () -> harness.roomOf("beetle-gap", 0));
        assertTrue(gap.getMessage().contains("floor"), gap.getMessage());
    }

    private static int flames(KimeriaParticleSystem system) { return kinds(system, KimeriaParticleSystem.KIND_FLAME); }

    private static int kinds(KimeriaParticleSystem system, byte kind) {
        int n = 0;
        for (int i = 0; i < system.count(); i++) if (system.kind(i) == kind) n++;
        return n;
    }
}
