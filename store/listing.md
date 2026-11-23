# Play Store listing copy

Paste-ready text. Limits: name 30, short description 80, full description 4000.

## App name (9 / 30)

```
PeNNy-937
```

## Short description (72 / 80)

```
Free, open source animation for Android. No ads, no account, no network.
```

## Full description (1706 / 4 000)

```
Most animation apps on Android want something from you: an account, a
subscription, an ad before every undo, or access to your contacts.

PeNNy-937 wants nothing. Free, open source, no account, no ads, no purchases,
no tracking, no permissions, and not even the internet permission, so it could
not send your work anywhere if it tried. The source is on GitHub.

Three things it does that most of them do not.

Strokes stay vectors: draw at whatever zoom suits your hand, change the canvas
size afterwards, or render out larger than your screen. The ink is re-rendered
from the strokes at the size you ask for, so a line stays a line at 4K. Most
drawing apps fix the project size once and rasterise it forever.

Video becomes a reference on every timeline step: import footage you already
shot and you get one locked reference layer per frame, at your project frame
rate. Photos work the same way as a single guide. References are never drawn on
by accident, and never exported unless you ask for them in the render.

The stylus is read raw: pressure and tilt taper the line, the pen's side button
erases for exactly one stroke without changing tools, and a palm on the glass is
ignored instead of leaving a smear.

The rest is what you would want anyway. Layers inside each drawing, with merge
down. Onion skin, one neighbour at a time. Line, rectangle, ellipse and circle
shapes, move and rotate, and an eyedropper that samples the layer you picked
rather than the flattened image. Export to MP4, GIF, a PNG sequence or a single
still. Projects autosave as compact documents and can be zipped with every
reference inside.

8.7 MB. Android 8.0 or newer. No account to create, no network to be offline
from.
```

## Category and tags

- Category: **Art & Design** (alternative: Entertainment)
- Tags: animation, drawing, sketch, stylus, frame by frame
- Contact email: *(fill in - required)*
- Website: https://github.com/Dahl404/PeNNy-937
- Privacy policy: https://dahl404.github.io/PeNNy-937/privacy.html

## Graphic assets (already generated in `store/`)

| Asset | File | Spec |
| --- | --- | --- |
| App icon | `icon_512.png` | 512x512, 32-bit PNG, no rounding baked in |
| Feature graphic | `feature_1024x500.png` | 1024x500, no transparency |
| Phone screenshots | `phone-*.png` | min 2, 1080x1920 (9:16 — Play rejects anything taller than 16:9 or wider than 9:16) |

Screenshot set, in upload order (captured on the 1440x2560 phone surface, which is
exactly 9:16, then downscaled to 1080x1920):

1. `phone-1-library.png` — project library with real thumbnails
2. `phone-2-editor.png` — editor with the floating tool and frame windows
3. `phone-3-canvas.png` — the same frame with the windows slid away
4. `phone-4-layers.png` — layer list: stroke counts, references, opacity, lock
5. `phone-5-color.png` — colour sheet: wheel, swatches, sample-from-line-work
6. `phone-6-export.png` — export sheet: MP4 / GIF / frames / still
7. `phone-7-settings.png` — project menu: stylus only, pressure, grid, windows

To recapture: `adb shell wm size 1440x2560` (exact 9:16, no cropping needed),
`adb shell settings put global policy_control immersive.full=*` to hide the bars,
then `adb exec-out screencap -p`, downscale with
`magick in.png -resize 1080x1920! -strip PNG24:out.png`, and afterwards
`adb shell wm size reset` plus `settings put global policy_control null`.

Regenerate the graphics with `python3 store/make_assets.py` (needs `rsvg-convert`
and the Inter TTFs installed under `~/.local/share/fonts`).
