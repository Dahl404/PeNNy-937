# Play Store listing copy

Paste-ready text. Limits: name 30, short description 80, full description 4000.

## App name (9 / 30)

```
PeNNy-937
```

## Short description (69 / 80)

```
Frame-by-frame animation with vector inks, layers and onion skin.
```

## Full description (2 118 / 4 000)

```
PeNNy-937 is a frame-by-frame animation studio for Android, built for a stylus.

Draw on paper you control, flip through frames, and export a movie. Every stroke
is kept as a vector, so nothing you draw is welded to the size you drew it at:
change the canvas resolution afterwards, or export larger than your screen, and
the ink is re-rendered from the vectors at full quality.

INK
Pen with pressure taper, pencil with procedural graphite grain, and a marker that
lays down one even translucent pass so overlapping strokes never double up at the
joints. The eraser only touches the layer you are on. Hold the pen's side button
to erase for a single stroke without changing tools. Pressure, hover position and
the stylus button come from the raw stylus events, and palm-sized contacts are
ignored so your hand can rest on the screen.

FRAMES AND LAYERS
Add, duplicate, hold, reorder and delete frames. Every frame carries its own stack
of layers: rename, duplicate, hide, reorder, set opacity, lock, clear, or merge
down. Merge down works for every kind of layer, including reference images and
eraser marks. Onion skin shows one neighbouring drawing at a time: a blue ghost
of the previous frame behind your ink, or a red ghost of the next one.

TOOLS
Line, rectangle, square, ellipse and circle are drag-to-place vector shapes. Morph
bends the ink under a soft brush, so a drawing can be reshaped directly instead of
erased and redrawn. Move and rotate drags a whole layer across the canvas, and two
fingers twist it around its own centre. The eyedropper samples the layer you pick,
not the flattened composite, so the colour you get is the ink you tapped.

REFERENCES
Import a photo and place it as a locked guide. Import a video and get one
reference layer per timeline frame, sampled at your project frame rate: a fast way
to rotoscope, or to draw over footage you already have. References stay guides by
default (never drawn on, never exported), or can be included in the export as
footage underneath your drawing.

EXPORT
MP4 (H.264), animated GIF, a PNG sequence, or the current frame as a single PNG.
Exports render from the vectors at the size you choose, not at the size of your
screen. Projects autosave as compact vector documents with a poster thumbnail, and
can be exported and imported as a zip that carries every reference with it.

NO ACCOUNT, NO NETWORK
The app requests no permissions and holds no internet permission at all, so it
cannot send anything anywhere. There is no telemetry, no advertising and no sign
in. Your work stays in the app's own folder on your device.

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
