package com.jlogicsoftware.kimeria.testsupport;

import com.badlogic.gdx.math.GridPoint2;
import com.jlogicsoftware.kimeria.SpriteSheets;
import com.jlogicsoftware.kimeria.entity.player.Player;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The shipped hero sheets read as pixels (#343, D79), for tests that measure where the drawn art is: the gameplay
 * harness stubs texture regions, so the cell comes from {@link Player#currentCell} and the pixels from the PNG.
 */
public final class HeroArt {
    private static final Map<String, BufferedImage> SHEETS = new HashMap<>();
    private final GridPoint2 cell = new GridPoint2();

    public static BufferedImage sheet(SpriteSheets.Sheet sheet) {
        return SHEETS.computeIfAbsent(sheet.path(), path -> {
            try (var input = HeroArt.class.getClassLoader().getResourceAsStream(path)) {
                if (input == null) throw new IllegalStateException(path + " is not on the test classpath");
                return ImageIO.read(input);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Topmost opaque row of a cell, or -1 for an empty cell. */
    public static int top(SpriteSheets.Sheet sheet, int column, int row) {
        BufferedImage image = sheet(sheet);
        for (int y = 0; y < sheet.height(); y++)
            for (int x = 0; x < sheet.width(); x++)
                if (opaque(image, column * sheet.width() + x, row * sheet.height() + y)) return y;
        return -1;
    }

    /** Lowest opaque row of a cell, or -1 for an empty cell. */
    public static int bottom(SpriteSheets.Sheet sheet, int column, int row) {
        BufferedImage image = sheet(sheet);
        for (int y = sheet.height() - 1; y >= 0; y--)
            for (int x = 0; x < sheet.width(); x++)
                if (opaque(image, column * sheet.width() + x, row * sheet.height() + y)) return y;
        return -1;
    }

    private static boolean opaque(BufferedImage image, int x, int y) { return (image.getRGB(x, y) >>> 24) != 0; }

    /** World Y (Y grows down) of the topmost opaque pixel the hero draws this frame. */
    public float drawnTop(Player hero) {
        hero.currentCell(cell);
        return hero.frameY() + top(hero.currentSheet(), cell.x, cell.y);
    }

    /** World Y just below the lowest opaque pixel the hero draws this frame: where the drawn feet meet the ground. */
    public float drawnBottom(Player hero) {
        hero.currentCell(cell);
        return hero.frameY() + bottom(hero.currentSheet(), cell.x, cell.y) + 1;
    }
}
