---
name: kimeria-game-text
description: Adds, rewords, translates or removes a player-facing string in Edgard in Kimeria (localization catalogs, zone hints, healer dialogue, labels, intro or ending) under the text style rules and gate. Use whenever a key in assets/localization/*.json changes.
---

# Kimeria game text

A thin procedure. The rules live in the docs; read them, do not restate them here.

Work from the repository root; links resolve relative to this file.

- Rules, voices, limits, glossary, key naming: [text style](../../../docs/TEXT_STYLE.md) (D74)
- Catalog format, per-key context, fonts: [localization](../../../docs/LOCALIZATION.md)
- World facts and the healer's role: [story](../../../docs/STORY.md)
- Where every current string appears: [text audit](../../../docs/TEXT_AUDIT.md)
- Dialogue mechanics (timeline, input, portraits): the kimeria-dialogue skill
- How text fits the rest of the style (the tale is shown, not only told): [style system](../../../docs/STYLE_SYSTEM.md)

## Steps

1. **Locate.** Find where the string appears (map `Hint` object, `HealerDialogue`, `Player` label, menu,
   intro/ending) and pick its voice from `docs/TEXT_STYLE.md` section 1.
2. **Name the key** by `docs/TEXT_STYLE.md` section 5; add the enum value to `Msg` for a new key.
3. **Write English only.** Check voice, limits (section 3) and glossary (section 4). Show the text to
   the author and stop until they approve it. Do not translate unapproved text.
4. **Translate** into uk, pl, de and be with the glossary forms, in the same change. Add or update the
   key's row in `docs/LOCALIZATION.md` ("Message context").
5. **Validate**, all must pass:

   ```sh
   python3 tools/check_text_style.py
   ./gradlew validateLocalization
   ./gradlew :core:test
   ```

   Rebake fonts (`./gradlew :lwjgl3:bakeFonts`) only when new glyphs are reported. Never add an
   allowlist entry to `tools/text_style.json` for new text; fix the text. When a rewrite removes an
   allowlisted finding, delete its entry (the gate fails on stale entries).
6. **Report** the measured zone-message lines per language (from `LocalizationLayoutTest`) and what was
   not checked: native-speaker review and the look in the running game.
