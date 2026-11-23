# Play Store listing copy

Paste-ready text. Limits: name 30, short description 80, full description 4000.

## App name (9 / 30)

```
PeNNy-937
```

## Short description (66 / 80)

```
Draw animation frame by frame with a stylus. Vectors, layers, MP4.
```

## Full description (1406 / 4 000)

```
PeNNy-937 is a drawing app for people who animate by hand.

You draw a frame, flip to the next, draw it again, and the sequence starts to
move. Onion skin keeps the previous drawing faintly behind the one you are
making, so the next pose is a small correction instead of a guess.

Everything you draw is a vector, not pixels. Work at whatever zoom is
comfortable, change the canvas size later, or export bigger than your screen:
the ink is re-rendered from the strokes at whatever size you ask for, so it
never goes soft.

Work is organised the way hand-drawn animation is: frames along a timeline, each
with its own stack of layers. Duplicate a pose to start the next one, hide a
layer to see what is underneath, or bring a photo in as a locked guide you
cannot accidentally draw on. Import a video and it becomes one reference frame
per timeline step, which is the quick way to rotoscope footage you already shot.

When it moves the way you want it, export it: MP4, GIF, a PNG sequence, or the
current drawing as a still.

The app asks for no permissions at all, does not even hold the internet
permission, and has no account, no ads and no tracking. Your work lives in the
app's own folder and goes nowhere else.

It is made for a stylus: pressure, tilt and the pen's side button are read
directly, and a palm resting on the screen is ignored. It works with a finger
too.

Requires Android 8.0 or newer.
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
