package com.jlogicsoftware.kimeria.localization;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.jlogicsoftware.kimeria.Assets;
import com.jlogicsoftware.kimeria.ui.Menu;
import com.jlogicsoftware.kimeria.ui.MenuItem;
import com.jlogicsoftware.kimeria.ui.Overlay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real shipped font metrics, without GPU textures. Rendering itself is checked separately. */
class LocalizationLayoutTest {
    private static class MetricsFont extends BitmapFont {
        MetricsFont(int size) {
            super(new BitmapFontData(new HeadlessFiles().internal("fonts/generated/QuestSquare-" + size + ".fnt"), false),
                new TextureRegion(), true);
        }
        @Override protected void load(BitmapFontData data) { /* Metrics require no texture UVs. */ }
    }

    private static float height(BitmapFont font, String text, float width) {
        return new GlyphLayout(font, text, Color.WHITE, width, Align.center, true).height;
    }

    @Test void localizedTextFitsActualMenusDialogueHelpAndTouchBounds() {
        var previous = Gdx.files;
        Gdx.files = new HeadlessFiles();
        GdxNativesLoader.load();
        try {
            BitmapFont body = new MetricsFont(15), button = new MetricsFont(20), hint = new MetricsFont(13), title = new MetricsFont(30);
            Overlay overlay = new Overlay(new Assets() {
                @Override public BitmapFont font(int size) { return new MetricsFont(size); }
            }, 640, 360);
            for (Language language : Language.values()) {
                String prefix = language.code() + " ";
                for (Msg message : new Msg[]{Msg.TITLE, Msg.NEW_GAME, Msg.OPTIONS, Msg.ASSIST, Msg.ABOUT, Msg.PAUSE_MENU, Msg.LOADING})
                    assertTrue(new GlyphLayout(title, message.t(language)).width <= 608, prefix + message);
                for (Msg message : new Msg[]{Msg.PLAY, Msg.CONTINUE, Msg.NEW_GAME, Msg.CONFIRM, Msg.CANCEL,
                    Msg.ABOUT, Msg.OPTIONS, Msg.EXIT, Msg.BACK, Msg.RESUME, Msg.RESTART_LEVEL, Msg.EXIT_TO_MENU, Msg.RULES, Msg.CONTROLS})
                    assertTrue(new GlyphLayout(button, message.t(language)).width <= 544, prefix + message);
                assertTrue(height(body, Msg.NEW_GAME_CONFIRMATION.t(language), 290) <= 80, prefix + "confirmation");
                assertTrue(height(body, Msg.ABOUT_RULES.t(language), 580) <= 140, prefix + "rules");
                assertTrue(height(body, Msg.HEALER.t(language), 100) <= 20, prefix + "speaker name");
                assertTrue(new GlyphLayout(body, language.nativeName()).width <= 260, prefix + "language name");
                assertTrue(height(hint, Msg.MENU_HINT.t(language), 608) <= 26, prefix + "menu hint");
                for (Msg message : new Msg[]{Msg.HEALER_GREETING, Msg.EDGARD_REPLY, Msg.HEALER_INGREDIENTS,
                    Msg.HEALER_PAYMENT, Msg.HEALER_BOTH, Msg.HEALER_READY, Msg.HEALER_OFFER, Msg.HEALER_PAID,
                    Msg.HEALER_LOCK, Msg.HEALER_VICTORY, Msg.EDGARD_DARKNESS, Msg.HEALER_GUIDE})
                    assertTrue(height(body, message.t(language), 556) <= 80, prefix + message);
                for (Msg message : new Msg[]{Msg.HEALER_RETURN, Msg.HEALER_BREW, Msg.HEALER_FIGHT})
                    assertTrue(new GlyphLayout(body, message.t(language)).width <= 512, prefix + message);
                for (Msg message : new Msg[]{Msg.ATTACK, Msg.INTERACT, Msg.TURN_ON, Msg.TURN_OFF, Msg.SHOOT, Msg.PICK_UP, Msg.PUT_DOWN, Msg.THROW, Msg.FIREBALL, Msg.NO_AMMO, Msg.PLACE_BLOCKED, Msg.DIRECTION_CONFLICT}) {
                    float width = message == Msg.SHOOT ? 52.4f : 62f;
                    assertTrue(new GlyphLayout(hint, message.t(language)).width <= width, prefix + message);
                    assertTrue(height(hint, message.t(language), width) <= 32, prefix + message);
                }
                for (boolean left : new boolean[]{false, true}) {
                    String help = (left ? Msg.ABOUT_BODY_LEFT_HANDED : Msg.ABOUT_BODY).t(language) + "\n\n"
                        + (left ? Msg.ABOUT_GAMEPAD_LEFT_HANDED : Msg.ABOUT_GAMEPAD).t(language);
                    // Existing About layout: title, body margin, two buttons, surrounding margins.
                    assertTrue(44 + height(body, help, 580) + 20 + 88 + 40 <= 332, prefix + "controller help left=" + left);
                }
                for (String label : List.of(Msg.LONGER_TRAP_WARNINGS.t(language) + ": " + Msg.OFF.t(language),
                    Msg.LEFT_HANDED_LABEL.t(language) + ": " + Msg.ON.t(language),
                    Msg.LANGUAGE_LABEL.t(language) + ": " + language.nativeName(), Msg.RESTART_LEVEL.t(language))) {
                    Menu menu = new Menu(null, null, List.of(new MenuItem(label, () -> {})), null);
                    assertTrue(new GlyphLayout(button, label).width <= overlay.buttonsWidth(menu) - 48, prefix + label);
                    assertTrue(overlay.buttonsWidth(menu) <= 592, prefix + label);
                }
            }
        } finally { Gdx.files = previous; }
    }
}
