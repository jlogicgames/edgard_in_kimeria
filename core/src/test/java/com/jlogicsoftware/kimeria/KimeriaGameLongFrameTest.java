package com.jlogicsoftware.kimeria;

import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.entity.objects.Escalator;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.world.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One long frame (a dragged window, a hidden browser tab, sleep) advances the world by no more than the hero (D160): a
 * moving lift keeps its rider and stays on its track. A real Level and Player, stepped through
 * {@code KimeriaGame.updatePlay}, the gameplay half of every rendered frame.
 */
class KimeriaGameLongFrameTest {
    private static final float FRAME = HeadlessLevelHarness.STEP, LONGEST_WORLD_STEP = .05f;
    /** The hero's box: its body centre and its feet, from the top left corner. */
    private static final float BODY_CENTRE_X = 23.5f, FEET = 48f;
    private static final float SETTLE_SECONDS = 1.5f, RIDE_SECONDS = 1f, TOLERANCE = 3f;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private KimeriaGame game;
    private Level level;
    private Method updatePlay;
    private Object playing;

    @BeforeEach void setUp() {
        harness.setUp();
        harness.invulnerable = true; // the ride is under test, not the room's dangers
    }

    @AfterEach void restore() { harness.restoreGlobals(); }

    @ParameterizedTest @CsvSource({
        "forest-0-platform, 0.5", "forest-0-platform, 1", "forest-0-platform, 30",
        "forest-0-stone, 0.5", "forest-0-stone, 1", "forest-0-stone, 30"})
    void aLongFrameLeavesTheRiderOnTheLift(String room, float seconds) throws Exception {
        enter(room);
        Escalator lift = liftInRoom();
        level.player.position.set(lift.getX() + lift.getWidth() / 2f - BODY_CENTRE_X, lift.getY() - FEET);
        level.player.velocity.setZero();
        run(SETTLE_SECONDS);
        Rectangle track = Escalator.travel(lift.getX(), lift.getY(), lift.getWidth(), lift.getHeight(), lift.isVertical,
            lift.isVertical ? 4 : 0, lift.isVertical ? 0 : 2);
        lift.performAction();
        run(RIDE_SECONDS);
        float across = level.player.position.x - lift.getX(), x = lift.getX(), y = lift.getY();

        frame(seconds);

        float moved = Math.abs(lift.getX() - x) + Math.abs(lift.getY() - y);
        assertTrue(moved <= lift.moveSpeed() * LONGEST_WORLD_STEP + .001f, "the lift jumped " + moved + " px");
        run(RIDE_SECONDS);
        assertEquals(across, level.player.position.x - lift.getX(), TOLERANCE, "he slid along the lift");
        assertEquals(lift.getY(), level.player.position.y + FEET, TOLERANCE, "he is still on the lift");
        // A turn may overshoot an end of the track by one step.
        float slack = lift.moveSpeed() * LONGEST_WORLD_STEP;
        assertTrue(lift.getX() >= track.x - slack && lift.getX() + lift.getWidth() <= track.x + track.width + slack
            && lift.getY() >= track.y - slack && lift.getY() + lift.getHeight() <= track.y + track.height + slack,
            "the lift left its track " + track + ": " + lift.getX() + ", " + lift.getY());
    }

    private void enter(String room) throws Exception {
        level = harness.room("forest-0", room);
        game = new KimeriaGame();
        Class<?> state = Class.forName(KimeriaGame.class.getName() + "$UiState");
        playing = state.getEnumConstants()[0];
        for (Object value : state.getEnumConstants()) if ("PLAYING".equals(value.toString())) playing = value;
        set("level", level);
        set("player", level.player);
        set("gameStarted", true);
        set("uiState", playing);
        updatePlay = KimeriaGame.class.getDeclaredMethod("updatePlay", float.class, state, boolean.class);
        updatePlay.setAccessible(true);
    }

    private void set(String name, Object value) throws Exception {
        Field field = KimeriaGame.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(game, value);
    }

    private Escalator liftInRoom() {
        Rectangle room = level.currentRoomBounds();
        for (Escalator lift : level.escalators) if (room.contains(lift.getX(), lift.getY())) return lift;
        throw new AssertionError("no lift in " + level.currentRoomName());
    }

    private void frame(float seconds) throws Exception { updatePlay.invoke(game, seconds, playing, true); }

    private void run(float seconds) throws Exception { for (int i = Math.round(seconds / FRAME); i > 0; i--) frame(FRAME); }
}
