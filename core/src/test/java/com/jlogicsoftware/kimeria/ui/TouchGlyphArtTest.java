package com.jlogicsoftware.kimeria.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D138 (#492): every pictogram of the Action and Ranged buttons exists in both states, is registered in the art tool's size table,
 * differs between idle and pressed, and reads on its face. Loops {@link TouchGlyph#values()}, so a state added without its art fails here.
 */
class TouchGlyphArtTest {
    private static final double GLYPH_CONTRAST = 7.0;
    private static final int ART = 64, GLYPH_BOX = 36, MIN_PIXELS = 60;
    private static final int NAVY = 0x14122b, SLATE = 0x3f4670, CREAM = 0xfdf3d0, AMBER = 0xffc83a;
    /** Pixels the glyph may occupy: inside the face, which is the cream or amber area 5 px in from the ring. */
    private static final int FACE_MARGIN = 14;

    @Test void everyGlyphHasIdleAndPressedArtOfTheRegisteredSize() throws Exception {
        String sizes = Files.readString(root().resolve("tools/sprite_layouts.py"));
        assertTrue(sizes.contains("TOUCH_GLYPHS = ("), "the tool lists the glyphs");
        for (TouchGlyph glyph : TouchGlyph.values()) {
            assertTrue(sizes.contains("'" + glyph.artName() + "'"), glyph + " is in TOUCH_GLYPHS of sprite_layouts.py (-> TOUCH_CONTROL_SIZES)");
            for (String name : new String[]{glyph.artName(), glyph.artName() + "-down"}) {
                BufferedImage png = load(name);
                assertEquals(ART, png.getWidth(), name);
                assertEquals(ART, png.getHeight(), name);
                assertTrue(Files.exists(root().resolve("art/hud/touch/" + name + ".grid")), name + " has its source grid");
            }
        }
        for (String line : sizes.lines().filter(l -> l.contains("TOUCH_GLYPHS = (")).toList())
            assertEquals(TouchGlyph.values().length, line.split(",").length, "TOUCH_GLYPHS lists exactly the enum: " + line);
    }

    @Test void idleDiffersFromPressedAndEveryGlyphIsDistinct() throws Exception {
        Set<String> drawings = new HashSet<>();
        for (TouchGlyph glyph : TouchGlyph.values()) {
            BufferedImage idle = load(glyph.artName()), down = load(glyph.artName() + "-down");
            assertNotEquals(idle.getRGB(ART / 2, ART / 2 - 20), down.getRGB(ART / 2, ART / 2 - 20), glyph + ": the pressed face is amber, the idle one cream");
            assertEquals(shape(idle), shape(down), glyph + ": the pressed art draws the same glyph on its amber face");
            assertTrue(drawings.add(shape(idle)), glyph + " is drawn differently from every other glyph (no state relies on colour alone)");
        }
    }

    /** The glyph (its own colour) against the face it sits on, in both states: 7:1, as the labels were (D55). */
    @Test void everyGlyphContrastsWithItsFaceAtSevenToOne() throws Exception {
        for (TouchGlyph glyph : TouchGlyph.values()) {
            for (boolean down : new boolean[]{false, true}) {
                BufferedImage png = load(glyph.artName() + (down ? "-down" : ""));
                int face = down ? AMBER : CREAM;
                int ink = dominantInk(png, face);
                double ratio = contrast(luminance(ink), luminance(face));
                assertTrue(ratio >= GLYPH_CONTRAST, glyph + (down ? " pressed" : " idle") + ": " + Integer.toHexString(ink) + " on " + Integer.toHexString(face) + " = " + ratio);
                assertTrue(inkPixels(png, face) >= MIN_PIXELS, glyph + " has enough ink to read");
            }
        }
    }

    /** The glyph stays inside a 36 px box centred on the face (the face is 54 px wide), so it clears the ring at the smallest drawn size. */
    @Test void everyGlyphFitsItsBoxInsideTheFace() throws Exception {
        for (TouchGlyph glyph : TouchGlyph.values()) {
            BufferedImage png = load(glyph.artName());
            int minX = ART, minY = ART, maxX = -1, maxY = -1;
            for (int y = FACE_MARGIN; y < ART - FACE_MARGIN; y++) for (int x = FACE_MARGIN; x < ART - FACE_MARGIN; x++) {
                if (!isInk(png.getRGB(x, y))) continue;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            assertTrue(maxX - minX + 1 <= GLYPH_BOX && maxY - minY + 1 <= GLYPH_BOX, glyph + " is " + (maxX - minX + 1) + "x" + (maxY - minY + 1));
            assertTrue(minX >= FACE_MARGIN && minY >= FACE_MARGIN && maxX < ART - FACE_MARGIN && maxY < ART - FACE_MARGIN, glyph + " stays inside the face");
        }
    }

    /** The glyph is not mirrored in left-handed mode: its source grid is a full-width frame, never {@code mirror}. */
    @Test void glyphGridsAreFullWidthFramesNeverMirrored() throws Exception {
        for (TouchGlyph glyph : TouchGlyph.values()) for (String name : new String[]{glyph.artName(), glyph.artName() + "-down"}) {
            String grid = Files.readString(root().resolve("art/hud/touch/" + name + ".grid"));
            assertTrue(grid.lines().anyMatch(l -> l.equals("frame " + name)), name + " is one full frame");
            assertFalse(grid.lines().anyMatch(l -> l.startsWith("frame ") && l.endsWith(" mirror")), name + " is asymmetric and must not be a mirrored half");
        }
    }

    /** Which pixels are glyph ink (navy or slate) inside the face, as a string, so two drawings compare. */
    private static String shape(BufferedImage png) {
        StringBuilder out = new StringBuilder();
        for (int y = FACE_MARGIN; y < ART - FACE_MARGIN; y++) for (int x = FACE_MARGIN; x < ART - FACE_MARGIN; x++) out.append(isInk(png.getRGB(x, y)) ? '#' : '.');
        return out.toString();
    }

    private static boolean isInk(int argb) { int rgb = argb & 0xffffff; return rgb == NAVY || rgb == SLATE; }

    private static int inkPixels(BufferedImage png, int face) {
        int count = 0;
        for (int y = FACE_MARGIN; y < ART - FACE_MARGIN; y++) for (int x = FACE_MARGIN; x < ART - FACE_MARGIN; x++) if (isInk(png.getRGB(x, y))) count++;
        return count;
    }

    /** The ink colour that covers most glyph pixels; the pressed hollow flame is navy on amber, its idle one slate on cream (slate on amber is 5.9:1). */
    private static int dominantInk(BufferedImage png, int face) {
        int navy = 0, slate = 0;
        for (int y = FACE_MARGIN; y < ART - FACE_MARGIN; y++) for (int x = FACE_MARGIN; x < ART - FACE_MARGIN; x++) {
            int rgb = png.getRGB(x, y) & 0xffffff;
            if (rgb == NAVY) navy++; else if (rgb == SLATE) slate++;
        }
        return navy >= slate ? NAVY : SLATE;
    }

    private static Path root() {
        Path dir = new File("").getAbsoluteFile().toPath();
        while (dir != null && !Files.exists(dir.resolve("tools/sprite_layouts.py"))) dir = dir.getParent();
        assertNotNull(dir, "the repository root");
        return dir;
    }

    private static BufferedImage load(String name) throws Exception {
        try (var input = TouchGlyphArtTest.class.getClassLoader().getResourceAsStream("images/HUD/touch/" + name + ".png")) {
            assertNotNull(input, name);
            return javax.imageio.ImageIO.read(input);
        }
    }

    private static double luminance(int rgb) {
        return 0.2126 * channel(rgb >> 16 & 0xff) + 0.7152 * channel(rgb >> 8 & 0xff) + 0.0722 * channel(rgb & 0xff);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double contrast(double a, double b) {
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }
}
