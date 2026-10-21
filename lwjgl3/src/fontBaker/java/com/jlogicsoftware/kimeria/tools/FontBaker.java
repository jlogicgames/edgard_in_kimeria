package com.jlogicsoftware.kimeria.tools;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.tools.bmfont.BitmapFontWriter;
import com.jlogicsoftware.kimeria.localization.FontCharacters;

/**
 * Dev-only tool, not shipped with the game: bakes {@code fonts/QuestSquare.ttf}
 * to plain bitmap font files (one {@code .fnt} + {@code .png} pair per size)
 * under {@code assets/fonts/generated/}, at every size the game actually
 * uses (see {@link #SIZES} -- keep in sync with the {@code assets.font(...)}
 * call sites in {@code Overlay}/{@code Hud}).
 *
 * <p>Why bake instead of generating at runtime like a typical libGDX/FreeType
 * setup: the TeaVM/web build (see the {@code web} module) can't run
 * FreeTypeFontGenerator at all -- there's no browser port of FreeType. Baking
 * once here means every platform, including this desktop one, loads the same
 * plain bitmap font files, so there's no separate code path to maintain for
 * the web build.
 *
 * <p>Run with {@code ./gradlew lwjgl3:bakeFonts} after changing the font
 * file, catalog text, or {@link FontCharacters}.
 */
public class FontBaker extends ApplicationAdapter {
    private static final int[] SIZES = FontCharacters.SIZES;
    private static final int PAGE_SIZE = 512;
    private String characters;

    public static void main(String[] args) {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        new HeadlessApplication(new FontBaker(), config);
    }

    @Override
    public void create() {
        LocalizationCheck.catalogs();
        characters = FontCharacters.required(LocalizationCheck::source);
        LocalizationCheck.sourceGlyphs(characters);
        Gdx.files.local("fonts/generated").mkdirs();
        for (int size : SIZES) {
            bake(size);
        }
        LocalizationCheck.bitmapGlyphs(characters);
        System.out.println("Baked " + SIZES.length + " font size(s) to assets/fonts/generated/");
        Gdx.app.exit();
    }

    private void bake(int size) {
        String name = "QuestSquare-" + size;
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/QuestSquare.ttf"));
        PixmapPacker packer = new PixmapPacker(PAGE_SIZE, PAGE_SIZE, Pixmap.Format.RGBA8888, 2, false);

        FreeTypeFontGenerator.FreeTypeFontParameter param = new FreeTypeFontGenerator.FreeTypeFontParameter();
        param.size = size;
        param.characters = characters;
        param.hinting = FreeTypeFontGenerator.Hinting.Full;
        param.packer = packer;

        FreeTypeFontGenerator.FreeTypeBitmapFontData data = generator.generateData(param);

        String[] pageRefs = BitmapFontWriter.writePixmaps(packer.getPages(), Gdx.files.local("fonts/generated"), name);
        BitmapFontWriter.writeFont(data, pageRefs, Gdx.files.local("fonts/generated/" + name + ".fnt"),
            new BitmapFontWriter.FontInfo(name, size), PAGE_SIZE, PAGE_SIZE);

        generator.dispose();
        packer.dispose();
        System.out.println("  " + name + ".fnt (" + pageRefs.length + " page(s))");
    }
}
