# PeNNy-937

A stylus-first, frame-by-frame animation studio for Android. Simple on the surface:
four inks, a timeline, and a paper you control. Powerful underneath: vector strokes,
unlimited frames, unlimited layers per frame, editable canvas size and frame rate,
onion skinning, pressure-sensitive inks, and lossless/Timeline exports.

Penny is the mascot: a pen with a face, made of ink and a little patience.

## Build

```bash
source ~/.local/opt/android-env.sh
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17+, Android SDK with platform 37, build-tools 37, AGP 9.5.0,
Gradle 9.8.2 (the wrapper handles Gradle).

## Gestures

| Action | Result |
| --- | --- |
| Stylus / mouse | Draw with the active tool |
| Pen side button + touch | Temporary eraser for that stroke |
| Finger (default "Stylus only" on) | Pan the canvas |
| Two fingers | Pinch zoom + pan |
| Double tap (finger) | Toggle fit / 2.4× zoom |
| Tap the active tool | Brush settings |
| Long-press a frame | Frame actions (duplicate, delete, hold) |
| Trackpad/mouse (stylus-only off) | Also draws |

Palm-sized contacts are ignored. On-screen buttons for fit, undo/redo, playback,
frame actions and layers are always available.

## Core behaviour

- **Everything is vector.** Strokes are stored as smoothed point + pressure triples;
  changing the canvas resolution or exporting at another size re-renders from vectors.
- **Per-frame layers, no cap.** Frames carry their own independent layer stacks.
  Layers can be added, duplicated, hidden, opacity-blended, reordered, merged down,
  or cleared. The eraser only affects the active layer.
- **No maximum animation length.** Frames are lazy-rendered in the timeline and
  streamed during export.
- **Editable canvas.** Width/height 32–4096 px, frame rate 1–60 fps, five paper
  presets. Changing size keeps strokes at their original pixel scale.
- **Onion skin.** Previous frames are tinted warm, next frames cool.
- **Inks.** Pen (pressure taper), Pencil (procedurally generated graphite grain),
  Marker (single-pass translucent compositing so joints never double up), Eraser.
- **Autosave.** Binary vector project files + a PNG poster thumbnail.

## Export

- Animated GIF, written by a dependency-free encoder with per-frame median-cut
  palettes and Floyd–Steinberg dithering. Output widths 480 / 720 / full.
- PNG frame sequence (zip) with an `about.txt`.
- Current frame as PNG.
- Files are written to the app cache and shared through the system share sheet.

## Layout

```
app/src/main/kotlin/com/masen/penny937/
  MainActivity.kt, PennyViewModel.kt
  model/          Project, Frame, Layer, Stroke, immutable edits
  data/           project store (binary format), prefs
  paint/          stroke smoothing, grain, layer bitmap renderer, rasterizer
  export/         GIF/LZW/quantizer, PNG exports
  editor/         EditorModel (tools, frames, layers, undo, autosave, export)
  ui/             Apple-flavoured Compose UI, canvas view, sheets, icons
```

The canvas is a custom `View` (`DrawingCanvasView`) for full stylus event access
(historical samples, pressure, stylus button, hover cursor, palm rejection).
Everything else is Jetpack Compose. Fonts: Inter (SIL OFL, license in
`app/src/main/res/raw/inter_license.txt`).
