# Third-party notices

## pixelfx (MIT) and Sprite Fusion (MIT)

The room-exit portal (`PortalFx`, #552), the room-change ripple (`RoomRippleFx`, #553) and the soft pickup and fairy lights (`SoftGlowFx`,
#544) take the behaviour of effects from [patch-gg/pixelfx](https://github.com/patch-gg/pixelfx) (`src/fx/arcane/portal.js`,
`src/fx/water/ripples.js`, and the light of `src/fx/arcane/arcane-orb.js` and `src/fx/holy/halo.js`). The code in this repository is written for this game on its own
particle system (`KimeriaParticleSystem`); no source of pixelfx is copied, and the numbers are this game's own. The effects are credited
here because they follow its designs. pixelfx's `NOTICE.md` adds that `arcane/portal` adapts the dithered light ramp and the streaked,
knotted disc texture of Sprite Fusion's open-source black hole
(<https://www.spritefusion.com/pixel-art-vfx/black-hole>), Copyright (c) 2026 Hugo Duprez, MIT License.

The licence of pixelfx, which covers Sprite Fusion's part, is kept here in full:

```
MIT License

Copyright (c) 2026 Patch
Copyright (c) 2026 Hugo Duprez

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Add every later effect taken from pixelfx (#396) to the list at the top of this section.
