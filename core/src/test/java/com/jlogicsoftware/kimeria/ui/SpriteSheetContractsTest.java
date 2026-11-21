package com.jlogicsoftware.kimeria.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.tiled.TiledMapData;
import com.jlogicsoftware.kimeria.tiled.TiledObject;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** Actual PNG headers, independent of mocked texture loading in gameplay tests. */
class SpriteSheetContractsTest {
    @Test void runtimeFrameDefinitionsMatchTheShippedSheetsAndTheirStateCounts() throws Exception {
        matches(SpriteSheets.STONE, 1, 1);
        matches(SpriteSheets.TORCH, 1, 1);
        matches(SpriteSheets.FIREBALL, 1, 1);
        matches(SpriteSheets.LEVER, 2, 1);
        matches(SpriteSheets.LADDER, 2, 1);
        // The hero (#343, D79): 7 columns (the 7-frame attack) by 13 clips (8 core, 5 of the second set), both facings; the ladder 4 by 3 (tools/sprite_layouts.py HERO_SHEETS).
        matches(SpriteSheets.HERO_RIGHT, 7, 13);
        matches(SpriteSheets.HERO_LEFT, 7, 13);
        matches(SpriteSheets.HERO_LADDER, 4, 3);
        // Forest trap sheets (#230): frame counts follow tools/sprite_layouts.py FOREST_TRAP_SHEETS.
        matches(SpriteSheets.TRAP_STUMP, 8, 1);
        matches(SpriteSheets.TRAP_ROOT, 8, 1);
        matches(SpriteSheets.TRAP_BLAST, 12, 1);
        matches(SpriteSheets.TRAP_THORN_ROOT, 5, 1);
        matches(SpriteSheets.TRAP_THORN_TUFT, 3, 1);
        matches(SpriteSheets.TRAP_BRANCH, 4, 1);
        matches(SpriteSheets.TRAP_STONE, 2, 1);
        matches(SpriteSheets.TRAP_SHADOW, 3, 1);
        matches(SpriteSheets.TRAP_STONE_LIP, 2, 1); // #360: loose, broken
        matches(SpriteSheets.TRAP_DEADFALL, 5, 1); // #363: tied, strain, slip, swing, caught
        matches(SpriteSheets.TRAP_DEADFALL_BOUGH, 1, 1);
        matches(SpriteSheets.TRAP_DEADFALL_VINE, 3, 1); // taut, tugged, cut
        matches(SpriteSheets.TRAP_DEADFALL_ROPE, 1, 1);
        matches(SpriteSheets.TRAP_PUFFBALL, 4, 1);
        matches(SpriteSheets.TRAP_BRAMBLE, 6, 1);
        matches(SpriteSheets.TRAP_SPORES, 3, 1);
        matches(SpriteSheets.TRAP_VINE, 3, 1);
        matches(SpriteSheets.TRAP_LOG, 3, 1);
        matches(SpriteSheets.TRAP_FALLING_PLATFORM, 4, 1); // #362, #236: fan1..fan4 (the rope sway), the split plank in each
        matches(SpriteSheets.TRAP_PERCH, 2, 1); // #349: perch, perch-left
        matches(SpriteSheets.TRAP_ROTTEN_PERCH, 6, 1); // #349: intact, crumble1, crumble2 and their -left copies
        matches(SpriteSheets.TRAP_HONEY_FUNGUS, 6, 1); // #295 phase 1: idle1..3, swell1, swell2, spent
        matches(SpriteSheets.TRAP_FALSE_HONEY_FUNGUS, 6, 1);
        // Decor (#239 step 4): idle, brush1, brush2, settle (tools/sprite_layouts.py DECOR_SHEETS).
        matches(SpriteSheets.DECOR_BUSH, 4, 1);
        matches(SpriteSheets.DECOR_VINE, 1, 1); // #239 step 5: hang
        matches(SpriteSheets.DECOR_LOOKALIKE, 1, 1); // #299: static
        matches(SpriteSheets.DECOR_TRUNK, 1, 1); // #349: the tree trunk
        matches(SpriteSheets.DECOR_CROWN, 1, 1); // #421: the leaf crown over a trunk
    }

    /** #362 (D71): the plain falling platform's at-rest cue, the split through its plank (#236), is in every frame, so a re-export without it fails. */
    @Test void everyFallingPlatformFrameCarriesTheCrack() throws Exception {
        var sheet = SpriteSheets.TRAP_FALLING_PLATFORM;
        java.awt.image.BufferedImage png;
        try (var input = getClass().getClassLoader().getResourceAsStream(sheet.path())) {
            assertNotNull(input, sheet.path());
            png = javax.imageio.ImageIO.read(input);
        }
        int ink = 0x211f30, frames = png.getWidth() / sheet.width();
        int[][] crack = {{22, 1}, {23, 3}}, gaps = {{22, 0}, {23, 4}};
        for (int frame = 0; frame < frames; frame++) {
            int left = frame * sheet.width();
            for (int[] p : crack) {
                int argb = png.getRGB(left + p[0], p[1]);
                assertEquals(0xff, argb >>> 24, "frame " + frame + ": the crack pixel " + p[0] + "," + p[1] + " is transparent");
                assertEquals(ink, argb & 0xffffff, "frame " + frame + ": the crack pixel " + p[0] + "," + p[1] + " is not ink");
            }
            for (int[] p : gaps)
                assertEquals(0, png.getRGB(left + p[0], p[1]) >>> 24, "frame " + frame + ": no gap in the slab edge at " + p[0] + "," + p[1]);
        }
    }

    /** #255: the blast is drawn standing on the stump's base, so each frame's lowest opaque row is its last row. */
    @Test void everyBlastFrameRestsOnItsBottomRow() throws Exception {
        var sheet = SpriteSheets.TRAP_BLAST;
        java.awt.image.BufferedImage png;
        try (var input = getClass().getClassLoader().getResourceAsStream(sheet.path())) {
            assertNotNull(input, sheet.path());
            png = javax.imageio.ImageIO.read(input);
        }
        for (int frame = 0; frame < 12; frame++) {
            boolean bottomRowHasArt = false;
            for (int x = 0; x < sheet.width(); x++) {
                bottomRowHasArt |= (png.getRGB(frame * sheet.width() + x, sheet.height() - 1) >>> 24) != 0;
            }
            assertTrue(bottomRowHasArt, "blast frame " + frame + " floats above the stump's base");
        }
    }

    /**
     * #413: the floor lever stands on the ground. In both frames the base fills the bottom row (the row right above the
     * floor line, world Y down: Lever draws the frame's bottom on the trigger's bottom); the two frames differ only in the
     * handle, which leans right for on (frame 0) and left for off (frame 1). Every lever in the shipped maps then has a
     * collision top at its trigger's bottom under the whole base.
     */
    @Test void theFloorLeverStandsOnTheFloorLineInBothFramesAndEveryMap() throws Exception {
        var sheet = SpriteSheets.LEVER;
        java.awt.image.BufferedImage png;
        try (var input = getClass().getClassLoader().getResourceAsStream(sheet.path())) {
            assertNotNull(input, sheet.path());
            png = javax.imageio.ImageIO.read(input);
        }
        int w = sheet.width();
        int[] base = checkFloorLeverSheet(png, w, sheet.height());
        int baseLeft = base[0], baseRight = base[1];

        Gdx.files = new HeadlessFiles();
        Assets assets = new Assets() {
            @Override public TextureRegion region(String path, float x, float y, float width, float height) { return new TextureRegion(); }
        };
        int levers = 0;
        for (String name : new String[] {"forest-0", "forest-1", "forest-2"}) {
            TiledMapData map = TiledMapData.load("tiles/" + name + ".tmx", assets);
            for (TiledObject trigger : map.layer("SpawnPoints")) {
                if (!"Trigger".equals(trigger.type)) continue;
                levers++;
                float floor = trigger.y + trigger.height, frameLeft = trigger.x + trigger.width / 2 - w / 2f;
                float left = frameLeft + baseLeft, right = frameLeft + baseRight + 1;
                boolean supported = map.layer("Collisions").stream()
                    .anyMatch(s -> s.y == floor && s.x <= left && right <= s.x + s.width);
                assertTrue(supported, name + ": the lever at x " + trigger.x + " has no floor at y " + floor + " under x " + left + ".." + right);
            }
        }
        assertTrue(levers > 0, "the shipped maps hold no lever");
    }

    /**
     * The sprite half of the #413 contract, for any floor-lever drawing: returns the base's {left, right} columns.
     * The base is found from the drawing: its bottom row spans the base, and it goes up while a row is at least half that
     * wide in both frames (the handle above it is narrow). Every base row must be identical in the two frames (one base,
     * one pivot), the base must fill its bottom row, and the handle (the pixels where the frames differ, all above the
     * base) must lean further right in frame 0 (on) than in frame 1 (off).
     */
    static int[] checkFloorLeverSheet(java.awt.image.BufferedImage png, int w, int h) {
        int bottom = h - 1, baseLeft = -1, baseRight = -1;
        for (int x = 0; x < w; x++)
            if (opaque(png, x, bottom)) { if (baseLeft < 0) baseLeft = x; baseRight = x; }
        assertTrue(baseLeft >= 0, "the lever has no base on its bottom row");
        for (int x = baseLeft; x <= baseRight; x++)
            for (int frame = 0; frame < 2; frame++)
                assertTrue(opaque(png, frame * w + x, bottom), "frame " + frame + ": a gap under the base at " + x);
        int baseWidth = baseRight - baseLeft + 1, baseTop = bottom;
        while (baseTop > 0 && 2 * span(png, 0, baseTop - 1, w) >= baseWidth && 2 * span(png, w, baseTop - 1, w) >= baseWidth) baseTop--;
        assertTrue(baseTop < bottom, "the base is a single row: no top face or front to compare");
        for (int y = baseTop; y <= bottom; y++)
            for (int x = 0; x < w; x++)
                assertEquals(png.getRGB(x, y), png.getRGB(w + x, y), "the base differs between the frames at " + x + "," + y);
        float onSum = 0, offSum = 0; int onCount = 0, offCount = 0;
        for (int y = 0; y < baseTop; y++)
            for (int x = 0; x < w; x++) {
                if (png.getRGB(x, y) == png.getRGB(w + x, y)) continue;
                if (opaque(png, x, y)) { onSum += x; onCount++; }
                if (opaque(png, w + x, y)) { offSum += x; offCount++; }
            }
        assertTrue(onCount > 0 && offCount > 0, "the on and off frames do not differ above the base");
        assertTrue(onSum / onCount > offSum / offCount, "on (frame 0) leans right of off (frame 1)");
        return new int[] {baseLeft, baseRight};
    }

    /** A base pixel changed in the off frame only (one row above the bottom) must fail the floor-lever check. */
    @Test void aBaseThatDiffersBetweenTheFramesFailsTheFloorLeverCheck() throws Exception {
        var sheet = SpriteSheets.LEVER;
        java.awt.image.BufferedImage png;
        try (var input = getClass().getClassLoader().getResourceAsStream(sheet.path())) {
            png = javax.imageio.ImageIO.read(input);
        }
        var copy = new java.awt.image.BufferedImage(png.getWidth(), png.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(png, 0, 0, null);
        int w = sheet.width(), y = sheet.height() - 2, x = checkFloorLeverSheet(copy, w, sheet.height())[0] + 1;
        copy.setRGB(w + x, y, copy.getRGB(w + x, y) ^ 0x00ffffff);
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> checkFloorLeverSheet(copy, w, sheet.height()));
    }

    /** The opaque width (leftmost to rightmost opaque pixel) of one row of the frame starting at column {@code left}. */
    private static int span(java.awt.image.BufferedImage png, int left, int y, int w) {
        int first = -1, last = -1;
        for (int x = 0; x < w; x++)
            if (opaque(png, left + x, y)) { if (first < 0) first = x; last = x; }
        return first < 0 ? 0 : last - first + 1;
    }

    private static boolean opaque(java.awt.image.BufferedImage png, int x, int y) { return (png.getRGB(x, y) >>> 24) != 0; }

    private void matches(SpriteSheets.Sheet sheet, int columns, int rows) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream(sheet.path())) {
            assertNotNull(input, sheet.path());
            byte[] header = input.readNBytes(24);
            assertEquals(24, header.length, sheet.path());
            ByteBuffer png = ByteBuffer.wrap(header);
            assertEquals(sheet.width() * columns, png.getInt(16), sheet.path() + " width");
            assertEquals(sheet.height() * rows, png.getInt(20), sheet.path() + " height");
        }
    }
}
