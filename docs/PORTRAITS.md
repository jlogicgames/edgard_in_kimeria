# Dialogue portraits (#90)

The portraits follow the author's [remake and remaster art direction](ART_DIRECTION.md):
richer color, finer detail, less grain and more natural illustration, preserving the
original artist's character designs and drawing language.

| Character | Runtime asset | Detailed transparent master |
| --- | --- | --- |
| Edgard | `assets/images/portraits/edgard.png` | `art/portraits/edgard-master.png` |
| Healer | `assets/images/portraits/healer.png` | `art/portraits/healer-master.png` |

Each runtime image is a **256 × 256 px indexed PNG with at most 256 palette entries,
including transparency**. Both portraits are reused before the boss, after drinking
the elixir, after victory, and in their frozen dialogue pause backgrounds. Gameplay
sprites and the text-only opening are unaffected by this portrait revision.

## Replacing the artwork

Use the [DOS originals](ORIGINAL_DOS.md#portrait-direction-for-90) for identity and
both detailed masters for consistent remaster style. Preserve the whole silhouette,
the small transparent margin, Edgard's hair/headband and the healer's beard/hooked staff.
New artwork should match the existing characters' contour finish, shading, naturalness
and warm color family. See the [art direction](ART_DIRECTION.md) for the reusable rules.

Edit or replace the corresponding runtime PNG at the same path; no Java, atlas or font
changes are required. Prefer a 256 × 256 canvas. When revising a detailed master, export
from the repository root with the JDK already used to build the game:

```sh
java -Djava.awt.headless=true tools/PortraitExport.java art/portraits/edgard-master.png assets/images/portraits/edgard.png
java -Djava.awt.headless=true tools/PortraitExport.java art/portraits/healer-master.png assets/images/portraits/healer.png
```

The exporter fits the source proportionally into a 256 × 256 transparent canvas,
resizes smoothly and quantizes RGBA to 255 representative entries plus a transparent
entry, without dithering. Fine alpha edges remain represented in the palette. It does
not add photographic texture or change the drawing. Preserve the detailed master; do
not use a runtime thumbnail as the only style reference for future images.

Restart the game after replacement, or rebuild the web distribution. Development hot
reload supports same-size texture changes; restart it after changing dimensions.

## Rendering and lifecycle

**Dialogue screen (D78, #337, 2026-11-16).** The renderer fits the full canvas proportionally
in the **160 × 160 logical-pixel** opening of each 176 px portrait window (158 × 158 until
[D142](DECISIONS.md#d142-the-dialogue-screen-stands-still-centred-names-an-even-text-box-a-darker-listener-616-amends-d78-d134-2026-11-25);
64 × 64 in 88 px frames before D78); Edgard is on the left, the healer on the right, both facing the viewer
(the healer is never mirrored: his staff stays in his right hand). At a 1280 × 720 window
a portrait occupies 320 × 320 physical pixels from the 256 px source. The window opening is
a dusk blue (`3d4a68`) so the portraits' dark contours read against it. Both stand still: the speaker is drawn
as is, the listener is multiplied by (0.46, 0.49, 0.60)
and keeps an outline of 0.21 (Edgard) and 0.22 (healer) against the opening, held by
`tools/test_dialogue_frame_sprites.py`. The frame art, the thread and the layout are in
[ART_DIRECTION.md](ART_DIRECTION.md#dialogue-screen-d78-playtest-f3f10-337). Review a new
portrait both as a master and in this screen, lit and dimmed.

`HealerDialogueView` requests full-image regions from the shared game `Assets` cache
with explicit **linear minification/magnification**. This smooths detailed illustrations
at fractional scales and preserves their finer contours. The shared Y-down flip occurs
exactly once. Other textures keep their default nearest filtering. Filters belong to
the texture, so use separate image paths when two consumers need different policies.

Normal alpha blending preserves transparent contours. Views do not own/dispose textures;
`Assets.dispose` releases them with the other cached game assets. Speaker highlighting,
white left-aligned Edgard text, green right-aligned healer text, reveal timing and
continuation prompts use the existing dialogue model; the layout is D78's.

## Provenance and style continuity

Created on 2026-10-22 with built-in ImageGen. The original DOS screenshots establish
identity; the healer remaster additionally used the selected Edgard master as the finish
and shading reference. [Exact prompts and reference roles](../art/portraits/generation-prompts.md)
are retained beside both high-resolution masters. Runtime exports use the deterministic
JDK exporter above. New illustrations should reuse these visual references together
with any original drawing for the new subject.

The artist's individual design is the continuity target; a generation prompt and
palette limit alone cannot establish artistic equivalence. Compare new work with the
originals and the other current masters, and record author judgment separately.

## Verification — remaster revision, 2026-10-22

`./gradlew :core:test validateLocalization buildDesktop buildWeb` passes: **142 tests**,
zero failures/errors/skips, five catalogs with 62 keys and 192 required glyphs, desktop
JAR and minified TeaVM release build. The portrait regression decodes the actual PNGs
and checks a maximum of 256 RGBA colors, visible art, transparent background, full-image
UVs, shared texture identity, default nearest filtering and the view's explicit linear
filtering, followed by exactly one disposal per portrait. GPU upload/deletion is stubbed
in that regression; it does not establish visual appearance.

The production exporter was run on both selected masters. Both files are 256 × 256
indexed PNGs with 256-entry RGBA palettes, including fully transparent and fine alpha-edge
entries. Re-exporting Edgard produced a byte-identical PNG. No dithering is used.

Temporary hidden-window LWJGL and TeaVM preview hooks exercised the real game renderer
at 1280 × 720 / 640 × 360 logical resolution. Each backend captured 12 scenes for each
of EN/UK/PL/DE/BE: partial reveal, revealed healer/Edgard, choices, ready, paid,
post-victory healer/Edgard, paused typing/choices/paid/post-boss. All ten contact sheets
(**120 frames**) were visually inspected. New portraits retained their complete hair,
beard and staff silhouettes, upright orientation, smooth fine shading and clean
transparency on the dark frames. Frames and text stayed in their original positions;
speaker highlights, white left-aligned Edgard, green right-aligned healer, wrapped
text/choices and reveal-dependent continuation prompts remained correct. Pause used
the same portrait/text scene under the glitch with a crisp pause menu above it.

Four additional Chromium frames checked normal/paused Belarusian presentation at
960 × 540 and 640 × 360. Fine portraits remained legible at both scales, without coarse
upscaled source blocks or visible contour halos. Chromium reported no page or console
errors. These are scripted rendering checks, not physical-device play/readability tests.

Preview hooks, temporary harness/preferences/captures, local server and browser storage
were removed; final builds use normal launchers. Only the updated author comparison
sheet and production masters/exports/prompts are retained. Earlier coarse-portrait
validation is historical and does not substitute for these remaster observations.
Local review found no new dependencies or network paths. Before PR publication the
Snyk tool became available, but automatic approval review rejected the scan because it
could send private source to a third party. The author explicitly chose local review
only. No source was sent to Snyk and no external scan result is claimed.

The standard skill validator passes for kimeria-art-direction, kimeria-dialogue and
kimeria-rendering. PyYAML was supplied in a disposable validation environment; it is
not a project dependency. Relative documentation links were checked separately.

## Author review

The author accepted the earlier coarse 64 × 64 portraits on 2026-10-22, then explicitly
revised the direction to a more natural 256-color remake/remaster preserving the original
artist's style. That earlier acceptance is superseded for the artwork.

**Remastered artwork accepted by the author, 2026-10-22:** after viewing the updated
[comparison sheet](references/dialogue-portraits-review.png), the author explicitly
accepted this version as the style reference for future illustrations
("Принимаю как образец стиля"). Both retained masters now provide that visual baseline.
This is separate from automated/renderer checks. Physical-device readability and full
native-speaker review remain separate checks in [PRE_RELEASE_TODO.md](PRE_RELEASE_TODO.md).
