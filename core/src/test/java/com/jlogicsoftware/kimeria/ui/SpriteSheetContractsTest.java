package com.jlogicsoftware.kimeria.ui;

import com.jlogicsoftware.kimeria.SpriteSheets;
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
        // The hero (#343, D79): 7 columns (the 7-frame attack) by 15 clips (10 core, 5 of the second set), both facings; the ladder 4 by 3 (tools/sprite_layouts.py HERO_SHEETS).
        matches(SpriteSheets.HERO_RIGHT, 7, 15);
        matches(SpriteSheets.HERO_LEFT, 7, 15);
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
        matches(SpriteSheets.TRAP_FALLING_PLATFORM, 4, 1); // #362: fan1..fan4, the cracked slab in each
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

    /** #362 (D71): the plain falling platform's at-rest cue, the hairline crack, is in every fan frame, so a re-export without it fails. */
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
