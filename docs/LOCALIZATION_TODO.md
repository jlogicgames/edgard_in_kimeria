# Localization #85 implementation checklist

- [x] Extract every existing EN/UK message verbatim into UTF-8 catalogs.
- [x] Add complete Polish, German and Belarusian catalogs, including current story,
      ending, healer conversations, help/handedness/gamepad and touch labels.
- [x] Keep typed Msg keys; load common packaged resources on desktop and TeaVM.
- [x] Add explicit English runtime fallback and useful diagnostics.
- [x] Preserve saved ENGLISH/UKRAINIAN names, English default and invalid-value behavior;
      add native language names and immediate five-language switching.
- [x] Validate syntax, missing/extra/duplicate keys, empty values, placeholders;
      test invalid catalogs and runtime fallback separately.
- [x] Derive baked charset from catalogs/native names plus UI symbols; retain
      Polish, German and full Belarusian coverage and document uppercase SS policy.
- [x] Check source glyphs and actual shipped bitmap fonts/pages at 13/15/20/30;
      regenerate all sizes and reject stale/missing glyphs during builds.
- [x] Audit player-facing strings outside Msg and every rendering font.
- [x] Check actual font metrics and fix overflow in menus, hints, confirmations,
      dialogue, help and touch labels; preserve matching input hit areas.
- [x] Wire validation into desktop/web packaging and CI.
- [x] Document translation editing, key context, adding a language and font regeneration.
- [x] Run focused and full tests, desktop and TeaVM release builds.
- [x] Visually inspect all five languages in desktop and browser; verify immediate
      switching and persistence with isolated preferences/storage.
- [x] Review diff/security, remove temporary verification resources and report
      remaining human translation/device review honestly.

Completed 2026-10-21. See [editing instructions](LOCALIZATION.md) and
[validation evidence and remaining human/device review](LOCALIZATION_VALIDATION.md).
External Snyk scanning was unavailable; local source/dependency review was completed.
