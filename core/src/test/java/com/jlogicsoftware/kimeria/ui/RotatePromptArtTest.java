package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D140 (#493): the rotate prompt's two sprites exist at their registered size, read as a phone and as an amber arrow on the dark strip, and the arrow never
 * touches the phone at any angle of its turn, mirrored or not. Same pipeline and palette as the other touch art (D55, D138, D139).
 */
class RotatePromptArtTest {
    private static final int SIZE = 64, MIN_PHONE_PIXELS = 400, MIN_ARROW_PIXELS = 150;
    private static final int NAVY = 0x14122b, CREAM = 0xfdf3d0, AMBER = 0xffc83a, DARK_BAR = 0x10101c;
    private static final double EDGE_CONTRAST = 3.0;
    /** The phone turns about the middle of its art, as the code draws it (origin at the centre). */
    private static final double CENTRE = SIZE / 2.0;

    @Test void bothSpritesHaveTheirRegisteredSizeAndASourceGrid() throws Exception {
        String sizes = Files.readString(root().resolve("tools/sprite_layouts.py"));
        for (String name : new String[]{"rotate-phone", "rotate-arrow"}) {
            assertTrue(sizes.contains("'" + name + "': (" + SIZE + ", " + SIZE + ")"), name + " is registered in TOUCH_CONTROL_SIZES");
            BufferedImage png = load(name);
            assertEquals(SIZE, png.getWidth(), name);
            assertEquals(SIZE, png.getHeight(), name);
            assertTrue(Files.exists(root().resolve("art/hud/touch/" + name + ".grid")), name + " has its source grid");
            assertFalse(Files.readString(root().resolve("art/hud/touch/" + name + ".grid")).lines().anyMatch(l -> l.startsWith("frame ") && l.endsWith(" mirror")),
                name + " is a full-width frame: the code mirrors the arrow, not the grid");
        }
    }

    @Test void thePhoneIsACreamBodyWithANavyOutlineAndAScreenAndSitsInTheMiddle() throws Exception {
        BufferedImage png = load("rotate-phone");
        assertTrue(count(png, CREAM) >= MIN_PHONE_PIXELS / 4, "mostly cream: " + count(png, CREAM));
        assertTrue(count(png, NAVY) > 0 && count(png, AMBER) > 0, "navy outline and the amber sun on its screen");
        assertTrue(opaquePixels(png) >= MIN_PHONE_PIXELS);
        int minX = SIZE, minY = SIZE, maxX = -1, maxY = -1;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (opaque(png, x, y)) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        assertTrue(maxY - minY > maxX - minX, "a portrait phone: taller than wide, " + (maxX - minX + 1) + "x" + (maxY - minY + 1));
        assertEquals(CENTRE, (minX + maxX + 1) / 2.0, 0.5, "centred across, so it turns about its middle");
        assertEquals(CENTRE, (minY + maxY + 1) / 2.0, 0.5, "centred down");
        double reach = Math.hypot((maxX - minX + 1) / 2.0, (maxY - minY + 1) / 2.0);
        assertTrue(reach <= CENTRE, "its corners stay inside the art at every angle of the turn: " + reach);
    }

    @Test void theArrowIsAmberWithANavyOutlineAndBendsOverTheUpperRight() throws Exception {
        BufferedImage png = load("rotate-arrow");
        assertTrue(count(png, AMBER) >= MIN_ARROW_PIXELS, "mostly amber: " + count(png, AMBER));
        assertTrue(count(png, NAVY) > 0);
        int upperRight = 0, others = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (opaque(png, x, y)) { if (x >= SIZE / 2 && y <= SIZE / 2 + 12) upperRight++; else others++; }
        assertTrue(upperRight > 8 * others, "the arrow sits at the upper right: " + upperRight + " pixels there, " + others + " elsewhere");
        assertFalse(opaque(png, SIZE / 2, SIZE / 2), "and leaves the middle to the phone");
    }

    /** Both are light on a dark strip: the fill or the outline contrasts 3:1 with the bar they are drawn on. */
    @Test void bothReadOnTheDarkStrip() {
        for (int fill : new int[]{CREAM, AMBER})
            assertTrue(Math.max(contrast(luminance(fill), luminance(DARK_BAR)), contrast(luminance(NAVY), luminance(DARK_BAR))) >= EDGE_CONTRAST, Integer.toHexString(fill));
        assertTrue(contrast(luminance(CREAM), luminance(DARK_BAR)) >= 7.0, "the cream body reads strongly");
        assertTrue(contrast(luminance(AMBER), luminance(DARK_BAR)) >= 7.0, "so does the amber arrow");
    }

    /** At each step of the turn (0 to 90 degrees clockwise on the screen), the turned phone and the arrow, as drawn or mirrored for the way back, share no pixel. */
    @Test void theArrowNeverTouchesThePhoneAtAnyAngleOfItsTurn() throws Exception {
        BufferedImage phone = load("rotate-phone"), arrow = load("rotate-arrow");
        for (int degrees = 0; degrees <= 90; degrees += 3) for (boolean mirrored : new boolean[]{false, true}) {
            double angle = Math.toRadians(degrees), cos = Math.cos(angle), sin = Math.sin(angle);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                if (!opaque(arrow, x, y)) continue;
                double dx = (mirrored ? SIZE - 1 - x : x) + 0.5 - CENTRE, dy = y + 0.5 - CENTRE;
                // The phone turns clockwise on the screen (Y down), so a screen point maps back to the art by the opposite turn.
                int sx = (int) Math.floor(dx * cos + dy * sin + CENTRE), sy = (int) Math.floor(-dx * sin + dy * cos + CENTRE);
                if (sx >= 0 && sx < SIZE && sy >= 0 && sy < SIZE)
                    assertFalse(opaque(phone, sx, sy), "arrow pixel (" + x + "," + y + ") hits the phone at " + degrees + " degrees, mirrored " + mirrored);
            }
        }
    }

    private static int count(BufferedImage png, int rgb) {
        int count = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (opaque(png, x, y) && (png.getRGB(x, y) & 0xffffff) == rgb) count++;
        return count;
    }

    private static int opaquePixels(BufferedImage png) {
        int count = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (opaque(png, x, y)) count++;
        return count;
    }

    private static boolean opaque(BufferedImage png, int x, int y) { return (png.getRGB(x, y) >>> 24) != 0; }

    private static Path root() {
        Path dir = new File("").getAbsoluteFile().toPath();
        while (dir != null && !Files.exists(dir.resolve("tools/sprite_layouts.py"))) dir = dir.getParent();
        assertNotNull(dir, "the repository root");
        return dir;
    }

    private static BufferedImage load(String name) throws Exception {
        try (var input = RotatePromptArtTest.class.getClassLoader().getResourceAsStream("images/HUD/touch/" + name + ".png")) {
            assertNotNull(input, name);
            return javax.imageio.ImageIO.read(input);
        }
    }

    private static double luminance(int rgb) { return 0.2126 * channel(rgb >> 16 & 0xff) + 0.7152 * channel(rgb >> 8 & 0xff) + 0.0722 * channel(rgb & 0xff); }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double a, double b) { return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05); }
}
