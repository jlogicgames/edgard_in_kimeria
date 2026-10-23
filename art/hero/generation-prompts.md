# Selected look poses (2026-10-23)

Generated with built-in imagegen, transparent output. The edit reference is the
first 48×48 idle frame from `assets/images/hero/Player.png` (x=0, y=432), enlarged
16× with nearest filtering. `look-master.png` packs the two selected 1280×1280
outputs: up on the left, down on the right. Source IDs and exact prompts follow.

`python3 tools/export_player_look.py` exports `assets/images/hero/Look.png`, 96×48,
with two registered 48×48 frames. It composites only the authored head rectangle
(24,23)–(31,31) onto the unchanged first idle body; no shapes are painted.
Source head colors are mapped to the original hero palette. Both runtime poses
use 16 opaque colors plus transparency, binary alpha, nearest filtering and no
dithering. Sword, palm, body and boots remain pixel-identical to the idle frame.
The occupied figure stays tiny; the transparent 48×48 animation canvas is not a
48-pixel-wide character. Selected pose artwork still needs author visual review.

## Look up

Selected source: `exec-de5145b8-5788-4aef-b0f9-6f3e31252ff8.png`.

Exact prompt:

> Use case: precise-object-edit. Edit the supplied native pixel-art hero idle frame for Edgard in Kimeria into LOOKING UP. The input is exactly one 48x48 pixel frame enlarged 16x to 768x768, with transparent background. Preserve the original full 48x48 registration: the boots touch row46, sword remains at x21 and tip row16, forearm/palm remains around (31,34). Preserve ALL pixels of the body, upright sword, hands, boots and clothing. Change ONLY the small head around native x26..32 y26..32: tilt the head and face UP toward the sky, nose/chin direction clearly above the horizontal. Keep the dark hair, warm orange/brown face, tiny red headband, dark outlines and exactly the same crude original 1994 DOS sprite density. A static standing pose, not a new hero design, not a crouch. Square transparent 48x48 frame shown enlarged with exact equal square pixel cells, full original framing and generous transparent canvas; no zooming or cropping. At most 15 opaque flat colors plus transparency, no antialiasing, gradients, soft edges, glow, shading realism, shadow, floor, text, UI or additional objects. Output exactly one matching registered pose on real transparent background.

## Look down

Selected source: `exec-ef51b30f-0ed7-42d0-85f3-613b03128d8f.png`.

Exact prompt:

> Use case: precise-object-edit. Edit the supplied native pixel-art hero idle frame for Edgard in Kimeria into LOOKING DOWN. Input is exactly one 48x48 pixel frame enlarged 16x to 768x768 on transparent background. Keep exact full 48x48 registration and all pixels of upright sword, torso, arms, hand around native (31,34), legs and boots row46. Change ONLY the tiny head at native x26..32 y26..32: lower the face/chin and point the nose/eye DOWN toward the ground in front of the hero. A clearly bowed head with dark hair on top and warm orange/brown face angled below horizontal, tiny red headband; do not crouch or bend the body. Preserve original very tiny crude DOS pixel sprite density and black outlines. Full square original canvas, do not crop or enlarge the figure; native 48x48 grid shown enlarged as exact equal square blocks. At most 15 opaque flat colors plus transparency, no antialiasing, gradients, soft edges, glow, shading realism, shadows, floor, text, UI or extra objects. Exactly one complete matching pose on genuine transparent background.
