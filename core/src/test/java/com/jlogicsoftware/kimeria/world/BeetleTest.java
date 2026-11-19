package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.TrapWarnings;
import com.jlogicsoftware.kimeria.effects.BeetleFx;
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
 * the left end, centre 268, trigger box from 249, blast box 228..308 there); a resting stone at x 96. Since D101 a beetle killed
 * quietly leaves its body, which rocks when the hero comes near and is crumbled by the sword or a stone, burnt by a fireball.
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
            place(level, TOUCHING - 3);  // the hero steps back a little, still inside the blast box: it stays where it is
            int frames = until(level, b::isRemoved, 120);
            assertEquals(ExpectedWarnings.standard(longer), frames * STEP, STEP * 1.5f, "bursts when the warning ends");
            assertEquals(x, b.position.x, .01f, "committed on the spot");
            assertFalse(alive(level), "a hero inside the box at the instant dies");
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
        assertTrue(alive(level), "out of the 80x32 box at the instant");
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
        assertTrue(alive(level), "the feet are above the 32 px box at the instant");
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
            assertEquals(0, kinds(level.beetleFx().soft(), KimeriaParticleSystem.KIND_GLOW), "no burst flash (specks of the wind-up may still be fading)");
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
        harness.held.add(Input.Keys.W); frame(level);
        harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level);
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
            assertFalse(alive(level), "the swordsman is inside the box");
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

    @Test void aQuietKillFlipsItThenTheBodyFadesIntoTheDecorPaletteAndThePoofComesAMomentLater() {
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
        assertEquals(1, b.bodyFade(), 0, "settled into the decor body");
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
        assertTrue(b.isRemoved(), "a stomp crumbles the body, as the sword and a stone do");
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

    @Test void aThrownStoneCrumblesTheBodyAndFliesOn() {
        Level level = level();
        Beetle b = body(level);
        place(level, 108);
        harness.held.add(Input.Keys.W); frame(level);
        harness.held.add(Input.Keys.K); harness.pressed.add(Input.Keys.K); frame(level);
        harness.held.clear(); frame(level);
        var stone = level.player.carriedObject();
        assertNotNull(stone, "picked up the stone");
        place(level, 200);
        level.player.position.x = 200 - 29;
        settle(level, 5);
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        until(level, b::isRemoved, 120);
        assertTrue(stone.snapshot().velocityX() != 0, "a body stops nothing: the stone flies on");
        assertTrue(harness.sounds.contains(Beetle.CRUMBLE_SOUND));
    }

    @Test void aFireballBurnsTheBodyAndFliesOn() {
        Level level = level();
        Beetle b = body(level);
        harness.sounds.clear();
        harness.held.add(Input.Keys.L); harness.pressed.add(Input.Keys.L); frame(level); harness.held.clear();
        until(level, () -> b.getState() == Beetle.State.BURN, 60);
        assertTrue(fireballFlying(level), "the ball is not spent on a body");
        assertTrue(harness.sounds.contains(Beetle.BURN_SOUND));
        settle(level, 3);
        assertTrue(flames(level.remainsFx().hard()) > 0, "flames in the fireball's ramp");
        assertFalse(b.isRemains(), "a burning body is out of reach");
        until(level, b::isRemoved, (int) Math.ceil(Beetle.BURN_SECONDS / STEP) + 2);
        assertTrue(alive(level));
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

    @Test void theSprayStaysInsideTheBlastBox() {
        BeetleFx fx = new BeetleFx(7);
        float cx = 100, floor = 200;
        fx.burst(cx, floor);
        KimeriaParticleSystem hard = fx.hard();
        for (int f = 0; f < 30; f++) {
            for (int i = 0; i < hard.count(); i++) {
                float half = hard.drawSize(i) / 2;
                assertTrue(hard.x(i) - half >= cx - BeetleFx.BLAST_WIDTH / 2 && hard.x(i) + half <= cx + BeetleFx.BLAST_WIDTH / 2, "x " + hard.x(i));
                assertTrue(hard.y(i) - half >= floor - BeetleFx.BLAST_HEIGHT && hard.y(i) + half <= floor, "y " + hard.y(i));
            }
            fx.update(STEP);
        }
    }

    @Test void theSpecksAreBornOnTheBlastBoxEdgeAndGoToTheBody() {
        BeetleFx fx = new BeetleFx(11);
        float cx = 100, floor = 200;
        for (int i = 0; i < 200; i++) fx.speck(cx, floor);
        KimeriaParticleSystem hard = fx.hard();
        for (int i = 0; i < hard.count(); i++) {
            float x = hard.x(i), y = hard.y(i);
            boolean side = Math.abs(Math.abs(x - cx) - BeetleFx.BLAST_WIDTH / 2) < .01f && y >= floor - BeetleFx.BLAST_HEIGHT - .01f && y <= floor + .01f;
            boolean top = Math.abs(y - (floor - BeetleFx.BLAST_HEIGHT)) < .01f && Math.abs(x - cx) <= BeetleFx.BLAST_WIDTH / 2 + .01f;
            assertTrue(side || top, "on the edge: " + x + ", " + y);
        }
        fx.update(1f);
        assertEquals(0, hard.count(), "they die out within their life");
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
