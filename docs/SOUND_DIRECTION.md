# Sound direction

Permanent record of how the game's sound effects are made and why. Read it before adding or changing a sound.

## Aesthetic decision (author, 2026-11-12)

Sounds are deliberately crude and lo-fi, in the character of 90s formant / speech synthesizers: rough, buzzy,
unpolished. The author called the chosen jump sound "awful" on audition and said that is exactly the point:
it fits the game's concept. Consequences:

- Roughness is the goal. Do not "improve" a sound toward realism: no denoising, no reverb, no polish, no
  sample libraries, no recorded voices.
- Every shipped sound is original and synthesized by `tools/audio/generate_sounds.py`. This is a requirement,
  not a preference: originality and zero licensing risk (project-original, like `intro.wav`).
- Sounds are generated, never sampled. The legacy effects inherited from the Flame/Rust versions (`hit`,
  `collect`, `bounce`, `disappear`, `boss_*`, `button_click`) are tracked in #189; the jump has already been replaced.

## The recipe (as in `tools/audio/generate_sounds.py`)

Format for every file: plain PCM, 16-bit, mono, 44.1 kHz (libGDX's WAV decoder rejects other WAV flavours).
Each file gets a 4 ms linear fade in and out against clicks, its DC offset removed, and is scaled to a named
peak. Level target: effects are matched to the existing effects, which means RMS about -16 to -17.5 dBFS for
a prominent effect like the jump, peak never above -2 dBFS for it (the test enforces at most -1 dBFS for all).
Quiet background sounds sit well below (see each recipe).

### Jump: a breathy, throaty effort "ha!" (`JUMP_PARAMS`, seed 1302)

- Source: sawtooth glottal pulse train, pitch falling 190 -> 155 Hz over the sound, 2.5 % random period
  jitter, plus 10 % irregular pulse amplitude; in the last third extra period jitter and amplitude roughness
  (vocal fry, roughness field 0.15). Softened by a 3.5 kHz low-pass.
- Formants: three parallel band-pass filters for an open /a/, centre / bandwidth / gain: F1 760 / 190 Hz / 1.0,
  F2 1250 / 260 Hz / 0.55, F3 2550 / 420 Hz / 0.2. The bandwidths are wide on purpose (buzzy, not clean).
- Aspiration onset: 30 ms of noise band-passed around 1500 Hz (bandwidth 2500 Hz, roughly 500-3000 Hz), ramping
  up over 10 ms (breath, never a click), tailing off over 12 ms, at 1.10 times the vowel peak; the vowel starts
  at 35 % of the aspiration length so the two overlap.
- Envelope: vowel attack 16 ms, exponential decay with tau 65 ms, then a 12 ms linear cutoff at the very end
  (quick, slightly abrupt, no ring). Total duration 0.15 s.
- Colour and level: 3 kHz low-pass (dark, from the throat), then tanh soft saturation (`HA_DRIVE` 3.0) which
  lowers the crest factor so the RMS reaches the target; final peak -8.0 dBFS gives RMS about -17.0 dBFS.

### The other three

- `dust_puff.wav` (seed 2201): 0.10 s of noise band-passed around 1.4 kHz (bandwidth 1.8 kHz), 4 ms attack,
  exponential decay tau 25 ms, peak -14 dBFS. A soft scuff that plays together with the jump.
- `wall_rumble.wav` (seed 3301): a **seamless 1.0 s loop**, played with `Sound.loop` for the whole move time of the
  wall and stopped with `Sound.stop(id)` (2026-11-13, D64; the one-shot with a fade at both ends was heard once while
  the wall kept moving). Noise low-passed at 180 Hz (filtered circularly) plus a 55 Hz sub sine with slow frequency
  modulation (3 Hz, +-8 Hz), 7 Hz amplitude wobble of 25 %; every component has a whole number of cycles per loop,
  so there is no fade and no click at the wrap. The start and the stop are therefore abrupt; if that is audible, add
  a volume ramp. Peak -9 dBFS. Not re-auditioned by the author yet.
- `grit_fall.wav` (seed 4401): 0.5 s, 28 ticks of 6 ms band-passed noise (about 3.2 kHz, varied +-25 %) at
  random levels, tick times drawn from an exponential (tau 180 ms) so the density decays. Peak -12 dBFS.

## Making a new sound

1. In `tools/audio/generate_sounds.py` add a function with all parameters as named constants (with a comment)
   and a fixed seed for any randomness. Use only the standard library.
2. Add it to the `GAME_SOUNDS` table of outputs.
3. Regenerate: `python3 tools/audio/generate_sounds.py`. Do not touch the constants or seeds of existing
   sounds: the other files must stay byte-identical (compare md5 before and after).
4. Add the new file to the bounds table in `tools/test_generate_sounds.py` and run
   `python3 -m unittest discover -s tools -p 'test_*.py'`. Run
   `python3 tools/audio/analyze_sounds.py [--png art/audio/previews] FILE...`: it prints duration, peak and RMS
   (dBFS), DC offset, spectral centroid and the share of energy at or below 3 kHz and in 300-900 Hz, and
   writes a waveform-envelope PNG. These numbers check format, level, clicks and rough spectral shape. They
   cannot judge how the sound sounds.
5. Keep the file plain PCM 16-bit mono 44.1 kHz.
5b. Add the name to `Assets.SOUND_EFFECTS`: every effect is created at start-up, because a sound created on its first play
   was lost on the web (see `docs/DEVELOPMENT.md`); `SoundPreloadTest` fails when a played name is missing.
6. Play it through `KimeriaGame.playSound("<name>")` (loads `assets/audio/<name>.wav`) behind the `playSounds()`
   check, like the existing callers. Add the name to `tools/check_web_release_artifact.py` only if its list
   of required files starts to cover audio effects (today it lists only `main_menu.mp3`).
7. Add the new preview PNG to `art/audio/previews/` and update `docs/DEVELOPMENT.md`, "Original synthesized
   sound effects", if the origin or wiring status needs a note.

## Audition process

A human has to listen: objective numbers cannot judge the sound. What worked: produce a default plus ONE
alternative, never more than two, put the alternate outside `assets/` for the author, and let the author
choose. Delete the loser afterwards.

Design history (author's verdicts):

- Jump, first version, a "hup": it sounded like a spoken "PA!" (plosive); rejected.
- Jump, closed-mouth nasal "hm": too dull; rejected.
- Jump, throaty "ha!" with a breath onset: the breathy variant was chosen on 2026-11-12.
- `dust_puff`, `wall_rumble` and `grit_fall` were accepted at first listen. The rumble was then changed from a
  one-shot to a loop (2026-11-13) and needs a new listen. `dust_puff` also plays on landing at 0.6 volume.

## Don't

- No plosive onset: no click and no instant attack (it reads as "P"). Ramp in over at least about 10 ms.
- No clean modern vocal or polished sample-like sound; stay crude and buzzy.
- No long tails or ringing: effects end quickly; the end gets a short cutoff, not a reverb-like decay.
- No noise or tonal content that clips: peak at most -1 dBFS everywhere, -2 dBFS for the jump.
