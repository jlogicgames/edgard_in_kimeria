package com.jlogicsoftware.kimeria.world;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.objects.ThornTrap;
import com.jlogicsoftware.kimeria.entity.player.Player;
import com.jlogicsoftware.kimeria.testsupport.HeadlessLevelHarness;
import com.jlogicsoftware.kimeria.testsupport.HeroArt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hero's 64x64 frames (#343, D79): drawn at (x - 8, y - 16) from the unchanged 48x48 box, so the drawn feet stand
 * exactly on the floor line (world Y grows down: the frame's bottom is the box bottom), each facing draws its own
 * sheet, and a ladder plays a separate up, down and hold clip. Every check here reads the shipped PNGs.
 */
class HeroFrameTest {
    /** Rows of hero-right/left.png: idle, run, jump, fall, attack, look, clamber, hit, appear, disappear; columns per row. */
    private static final int[] FACING_FRAMES = {4, 6, 2, 2, 7, 2, 1, 2, 4, 4};
    private static final int JUMP_ROW = 2, FALL_ROW = 3, CLAMBER_ROW = 6;
    private static final int[] LABEL_POSE_ROWS = {0, 1, 5};      // idle, run, look: the label can show over these
    private static final int[] LADDER_FRAMES = {4, 4, 1};        // up, down, hold
    private static final int LADDER_UP_ROW = 0, LADDER_DOWN_ROW = 1, LADDER_HOLD_ROW = 2;
    private static final int LAST_ROW = 63;
    /** D79: standing and running poses are at most 31 px tall (the lowest ceiling the hero walks under is 32 px). */
    private static final int STANDING_TOP_ROW = 33;
    /** D79: nothing a label shows over rises above frame row 24 (position.y + 8), 22 px under the label's bottom. */
    private static final int LABEL_POSE_TOP_ROW = 24;
    private static final float FLOOR = 320, LADDER_X = 1216, LADDER_CENTRE = LADDER_X + 8, STEP_X = 2;

    private final HeadlessLevelHarness harness = new HeadlessLevelHarness();
    private final Set<Integer> held = harness.held, pressed = harness.pressed;
    private final HeroArt art = new HeroArt();
    private final GridPoint2 cell = new GridPoint2();

    @BeforeEach void setUp() { harness.setUp(); }
    @AfterEach void restoreGlobals() { harness.restoreGlobals(); }

    private void frame(Level level) { level.update(HeadlessLevelHarness.STEP); pressed.clear(); }

    @Test void everyGroundedAndLadderFrameStandsOnItsLastRowAndLabelPosesStayLow() {
        for (SpriteSheets.Sheet sheet : new SpriteSheets.Sheet[]{SpriteSheets.HERO_RIGHT, SpriteSheets.HERO_LEFT})
            for (int row = 0; row < FACING_FRAMES.length; row++)
                for (int column = 0; column < FACING_FRAMES[row]; column++) {
                    String where = sheet.path() + " row " + row + " column " + column;
                    assertTrue(HeroArt.top(sheet, column, row) >= 0, where + " is empty");
                    if (row != JUMP_ROW && row != FALL_ROW && row != CLAMBER_ROW)
                        assertEquals(LAST_ROW, HeroArt.bottom(sheet, column, row), where + ": the feet must be on the frame's last row");
                }
        for (SpriteSheets.Sheet sheet : new SpriteSheets.Sheet[]{SpriteSheets.HERO_RIGHT, SpriteSheets.HERO_LEFT})
            for (int row : LABEL_POSE_ROWS)
                for (int column = 0; column < FACING_FRAMES[row]; column++)
                    assertTrue(HeroArt.top(sheet, column, row) >= STANDING_TOP_ROW,
                        sheet.path() + " row " + row + " column " + column + " is taller than 31 px: top row " + HeroArt.top(sheet, column, row));
        for (int row = 0; row < LADDER_FRAMES.length; row++)
            for (int column = 0; column < LADDER_FRAMES[row]; column++) {
                assertEquals(LAST_ROW, HeroArt.bottom(SpriteSheets.HERO_LADDER, column, row), "ladder row " + row + " column " + column);
                assertTrue(HeroArt.top(SpriteSheets.HERO_LADDER, column, row) >= LABEL_POSE_TOP_ROW, "ladder row " + row + " column " + column);
            }
    }

    @Test void theFrameIsDrawnAtMinusEightMinusSixteenFromTheBoxAndItsBottomIsTheFloorLine() {
        Level level = harness.roomOf("forest-0", 1);
        Player hero = level.player;
        for (int i = 0; i < 30; i++) frame(level);
        Rectangle body = hero.hitboxBounds();
        assertEquals(hero.position.x - 8, hero.frameX(), 1e-4, "frame x");
        assertEquals(hero.position.y - 16, hero.frameY(), 1e-4, "frame y: 16 px above the box top (Y grows down)");
        assertEquals(body.y + body.height, hero.frameY() + Player.FRAME_SIZE, 1e-4, "frame bottom = hitbox bottom");
        assertEquals(body.x + body.width / 2, hero.frameX() + Player.FRAME_PIVOT_X, 1e-4, "frame axis = body centre");
    }

    /** Standing, running both ways, turning and attacking on the floor: every frame's lowest drawn pixel touches the floor line. */
    @Test void theDrawnFeetMeetTheFloorWhileStandingRunningTurningAndAttacking() {
        Level level = harness.roomOf("forest-0", 1);
        level.objects.removeIf(ThornTrap.class::isInstance);
        Player hero = level.player;
        int checked = 0;
        boolean sawRight = false, sawLeft = false;
        int[][] script = {{0, 20}, {Input.Keys.D, 60}, {Input.Keys.A, 60}, {Input.Keys.D, 8}, {0, 10}};
        for (int[] step : script)
            for (int i = 0; i < step[1]; i++) {
                held.clear();
                if (step[0] != 0) held.add(step[0]);
                frame(level);
                checked += checkFeet(hero);
                sawRight |= hero.currentSheet() == SpriteSheets.HERO_RIGHT;
                sawLeft |= hero.currentSheet() == SpriteSheets.HERO_LEFT;
            }
        for (boolean right : new boolean[]{true, false}) {
            held.clear(); held.add(right ? Input.Keys.D : Input.Keys.A); frame(level);
            held.clear(); held.add(Input.Keys.K); pressed.add(Input.Keys.K); frame(level);
            held.clear();
            for (int i = 0; i < 50; i++) { frame(level); checked += checkFeet(hero); }
            assertSame(right ? SpriteSheets.HERO_RIGHT : SpriteSheets.HERO_LEFT, hero.currentSheet(), "the facing picks the sheet");
        }
        assertTrue(sawRight && sawLeft, "both facings drawn");
        assertTrue(checked > 250, "grounded frames measured: " + checked);
    }

    private int checkFeet(Player hero) {
        if (!hero.isOnGround()) return 0;
        Rectangle body = hero.hitboxBounds();
        float feet = body.y + body.height;
        assertEquals(FLOOR, feet, 1e-3, "standing on the room floor");
        hero.currentCell(cell);
        assertEquals(feet, art.drawnBottom(hero), 1e-3, hero.getState() + " cell " + cell + " in " + hero.currentSheet().path()
            + ": the drawn feet must touch the floor line");
        return 1;
    }

    /** Up, holding still and down use three clips of the back view; the drawn feet stay on the hitbox bottom on the rungs. */
    @Test void aLadderPlaysUpHoldAndDownAsSeparateClips() {
        Level level = harness.roomOf("forest-0", 1);
        level.objects.removeIf(ThornTrap.class::isInstance);
        if (!level.ladders.get(0).isLowered()) level.ladders.get(0).performAction();
        for (int i = 0; i < 120; i++) frame(level);
        Player hero = level.player;
        hero.position.set(LADDER_CENTRE - 23.5f, FLOOR - 48);
        for (int i = 0; i < 5; i++) frame(level);
        int up = 0, hold = 0, down = 0;
        for (int i = 0; i < 40; i++) { held.clear(); held.add(Input.Keys.W); frame(level); up += ladderRow(hero, LADDER_UP_ROW); }
        for (int i = 0; i < 20; i++) { held.clear(); frame(level); hold += ladderRow(hero, LADDER_HOLD_ROW); }
        for (int i = 0; i < 20; i++) { held.clear(); held.add(Input.Keys.S); frame(level); down += ladderRow(hero, LADDER_DOWN_ROW); }
        assertTrue(up > 20 && hold == 20 && down == 20, "up " + up + " hold " + hold + " down " + down);
        assertTrue(Math.abs(hero.position.x + 23.5f - LADDER_CENTRE) < STEP_X, "on the ladder's axis");
    }

    private int ladderRow(Player hero, int expectedRow) {
        if (!hero.onLadder()) return 0;
        assertSame(SpriteSheets.HERO_LADDER, hero.currentSheet());
        hero.currentCell(cell);
        assertEquals(expectedRow, cell.y, "ladder clip");
        Rectangle body = hero.hitboxBounds();
        assertEquals(body.y + body.height, art.drawnBottom(hero), 1e-3, "feet on the rung line");
        return 1;
    }
}
