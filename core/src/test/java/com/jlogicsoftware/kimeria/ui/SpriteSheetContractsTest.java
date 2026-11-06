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
        matches(SpriteSheets.PLAYER_LOOK, 2, 1);
        matches(SpriteSheets.PLAYER, 5, 10);
        // Forest trap sheets (#230): frame counts follow tools/sprite_layouts.py FOREST_TRAP_SHEETS.
        matches(SpriteSheets.TRAP_STUMP, 5, 1);
        matches(SpriteSheets.TRAP_ROOT, 5, 1);
        matches(SpriteSheets.TRAP_BLAST, 12, 1);
        matches(SpriteSheets.TRAP_THORN_ROOT, 5, 1);
        matches(SpriteSheets.TRAP_THORN_TUFT, 3, 1);
        matches(SpriteSheets.TRAP_BRANCH, 4, 1);
        matches(SpriteSheets.TRAP_STONE, 2, 1);
        matches(SpriteSheets.TRAP_SHADOW, 3, 1);
        matches(SpriteSheets.TRAP_PUFFBALL, 4, 1);
        matches(SpriteSheets.TRAP_SPORES, 3, 1);
        matches(SpriteSheets.TRAP_VINE, 3, 1);
        matches(SpriteSheets.TRAP_LOG, 3, 1);
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
