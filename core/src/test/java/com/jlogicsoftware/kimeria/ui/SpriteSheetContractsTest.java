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
        matches(SpriteSheets.PLAYER_LOOK, 2, 1);
        matches(SpriteSheets.PLAYER, 5, 10);
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
