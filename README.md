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
| Sliders button in the tool rail | Brush / shape settings |
| Drag the handle under a frame | Reorder frames in the timeline |
| Layers → Import reference image | Import, place, scale/rotate and lock a tracing guide |
| Long-press a frame | Frame actions (duplicate, delete, hold) |
| Plus in the transport | Add a blank frame |
| Trackpad/mouse (stylus-only off) | Also draws |

Palm-sized contacts are ignored. On-screen buttons for fit, undo/redo, playback,
frame actions and layers are always available.

## Core behaviour

- **Everything is vector.** Strokes are stored as smoothed point + pressure triples;
  changing the canvas resolution or exporting at another size re-renders from vectors.
- **Per-frame layers, no cap.** Frames carry their own independent layer stacks.
  Layers can be added, duplicated, hidden, opacity-blended, reordered, merged down,
  locked, or cleared. The eraser only affects the active layer.
- **Reference images.** Import a photo from Layers; it arrives in a placement mode
  where you can drag, pinch-scale and rotate it, then locks as a dedicated reference
  layer on the current frame. Reference layers are guides: they are never drawn on,
  exported, played back or onion-skinned. Unlock to reposition or delete.
- **Layer eyedropper.** Open Color and choose *Pick from Layer 1* to sample ink
  directly from the selected layer — including a reference image. A floating loupe
  previews the live color while you drag; lift to keep, or cancel to revert. Other
  layers are ignored, so the picked color is the ink you actually tapped, not the
  blended composite.
- **Reference video.** Import a clip from Layers; it is sampled at the project frame
  rate (for example 10s at 12fps = 120 references) and each sample becomes a locked
  reference layer on its own timeline frame, extending the animation as needed.
  The first reference is fit to the canvas, the first frame of the clip is selected,
  and the next video import automatically continues after the previous clip ends.
  Long clips are capped at 3000 frames per import; lower the project fps or trim.
- **No maximum animation length.** Frames are lazy-rendered in the timeline and
  streamed during export.
- **Editable canvas.** Width/height 32–4096 px, frame rate 1–60 fps, five paper
  presets. Changing size keeps strokes at their original pixel scale.
- **Onion skin.** A single centre-detented slider: slide left for a blue ghost of the
  previous frame, right for a red ghost of the next. Centre is off, and only one
  neighbour is shown at a time.
- **Inks.** Pen (pressure taper), Pencil (procedurally generated graphite grain),
  Marker (single-pass translucent compositing so joints never double up), Eraser.
- **Basic shapes.** Line, rectangle, square, ellipse and circle are drag-to-place
  vector outlines. Square/circle hold their aspect; shapes undo, erase and export
  like any other stroke.
- **Grid view.** A toggleable squares grid (with a centre cross) or rule-of-thirds
  guide drawn over the paper, never baked into exports.
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
