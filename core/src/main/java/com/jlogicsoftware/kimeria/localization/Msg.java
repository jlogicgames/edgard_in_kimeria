package com.jlogicsoftware.kimeria.localization;

/** Stable, typed message keys. Translation text lives in assets/localization/*.json. */
public enum Msg {
    TITLE, PLAY, CONTINUE, NEW_GAME, NEW_GAME_CONFIRMATION, CONFIRM, CANCEL, ABOUT, OPTIONS, EXIT, BACK, RESUME, RESTART_LEVEL, EXIT_TO_MENU,
    PAUSE_MENU, LANGUAGE_LABEL, DISPLAY_LABEL, FULLSCREEN, WINDOWED, MENU_HINT, ABOUT_BODY, ABOUT_BODY_LEFT_HANDED, ABOUT_GAMEPAD, ABOUT_GAMEPAD_LEFT_HANDED, LOADING, INTRO_STORY, ENDING_STORY,
    ATTACK, INTERACT, SHOOT, LEFT_HANDED_LABEL, ASSIST, LONGER_TRAP_WARNINGS, ON, OFF, BOSS,
    HEALER, EDGARD, HEALER_GREETING, EDGARD_REPLY, HEALER_INGREDIENTS, HEALER_PAYMENT,
    HEALER_BOTH, HEALER_READY, HEALER_OFFER, HEALER_PAID, HEALER_LOCK, HEALER_RETURN, HEALER_BREW,
    HEALER_VICTORY, EDGARD_DARKNESS, HEALER_GUIDE, HEALER_FIGHT, HEALER_HINT, CHICKEN, KEY, COINS, PAID, ABOUT_RULES, RULES, CONTROLS, FOREST_COINS_COUNT;

    public String t(Language language) {
        return Localization.current().text(language, name());
    }

    /** Named placeholder/value pairs, e.g. format(language, "count", "3", "total", "6"). */
    public String format(Language language, String... arguments) {
        return Catalog.format(t(language), arguments);
    }
}
