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
| Tool window → sliders | Brush / shape settings |
| Tool window → morph | Warp the ink on the selected layer with a soft brush (normal tool) |
| Frame window → Layers pill | Layers sheet |
| Frame window → drag a frame's grip | Reorder frames in the timeline |
| Frame window → tap / long-press a frame | Select / frame actions (empty frame, delete, hold) |
| Layers → Import reference image | Import, place, scale/rotate and lock a tracing guide |
| Layers → Move / rotate layer | Drag to move; twist two fingers to rotate the layer as one |
| Chevrons on each floating window | Collapse / expand that window |
| Trackpad/mouse (stylus-only off) | Also draws |

Palm-sized contacts are ignored. The left side holds two floating windows under the
top bar: a tool window with the inks, morph, brush and colour settings, and a frame
window with layers, the timeline, onion skin, transport and new / empty frame.
New frames copy the current one; use Empty frame for a blank canvas.
Each window collapses independently, so the canvas keeps the rest of the screen.
Fit and grid float in the opposite corner.

## Core behaviour

- **Everything is vector.** Strokes are stored as smoothed point + pressure triples;
  changing the canvas resolution or exporting at another size re-renders from vectors.
- **Merge down always works.** Any upper layer can merge into the one below it and
  the result is pixel-identical. Normal layers stay vector: both opacities are baked
  into their strokes. Pairs that cannot be represented as one stroke list (an upper
  eraser, reference images, mixed visibility) are flattened together into raster ink
  on the merged layer, which remains drawable, movable, lockable and clearable.
  Hidden layers merge by what was actually visible: a hidden upper is dropped, and a
  hidden lower lets the visible upper become the result.
- **Per-frame layers, no cap.** Frames carry their own independent layer stacks.
  Layers can be added, duplicated, hidden, opacity-blended, reordered, shifted,
  merged down, locked, or cleared. The eraser only affects the active layer.
- **Move and rotate any layer.** Open Layers and choose *Move / rotate layer* to
  drag the selected layer's strokes and reference image on the canvas as one unit.
  Put two fingers down to twist it around its own centre, with occasional 45° snaps.
  The untransformed ink stays visible as a faint ghost while you drag, so you can
  compare before and after. Done applies the whole transform as a single undoable
  edit; cancel leaves it untouched.
- **Morph ink.** Morph sits beside the ink tools. Choose it, set the brush *Area*,
  then drag: the ink under the brush follows with a smooth falloff, so strokes bend
  and objects reshape directly on the selected layer. The original ink stays
  visible as a faint ghost while you sculpt, and each drag becomes one undoable
  vector edit. Like every other tool, fingers pan and pinch-zoom while the stylus
  sculpts.
- **Reference images.** Import a photo from Layers; it arrives in a placement mode
  where you can drag, pinch-scale and rotate it, then locks as a dedicated reference
  layer on the current frame. References are guide-only unless *References in the
  export* is on (set it before importing, or per layer in Layers, or with the eye
  button while placing). Guide references are never drawn on, exported, played back
  or onion-skinned; *in export* references become footage under your drawing, for
  editing an existing clip or animating on top of a still. Unlock to reposition or
  delete.
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
  Turn on *References in the export* (or *Show video in export* while it is
  extracting) to keep the clip in the output.
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
- **Autosave.** Binary vector project files + a PNG poster thumbnail. Each project
  owns its reference folder; references are garbage-collected on project delete,
  cancelled imports and project open, and excluded from Android backup. The canvas
  camera is saved per project through a small viewport codec and restored on reopen.
- **Portable project zip.** *Export project (.zip)* bundles the `.p937` document with
  every reference it uses; *Import project (.zip)* on the home screen restores it as
  a new project. This is the backup path for work that must include reference media.
- **Save failures are visible.** A failed write leaves the previous save intact,
  shows *not saved* in the editor and offers *Retry save* in the project menu. The
  last-saved timestamp only advances after a successful document write. Each save
  keeps exactly one `.p937.bak` of the previous document; a torn or missing primary
  is recovered from it on load (and shown in the project list), then repaired.

## Export

- MP4 video (H.264 via MediaCodec, AAC-free). Frames are rasterised on worker
  threads, converted to YUV 4:2:0 and streamed into a MediaMuxer, so memory stays
  flat no matter how long the animation is. Frame holds become real durations, the
  output is even-dimensioned, and the bitrate is picked from the pixel count
  (~0.12 bits/pixel/frame). Output widths 480 / 720 / full.
- Animated GIF, written by a dependency-free encoder with per-frame median-cut
  palettes and Floyd–Steinberg dithering. Output widths 480 / 720 / full.
- PNG frame sequence (zip) with an `about.txt`.
- Current frame as PNG.

References marked *in export* are rendered in every timeline format; guide-only
references stay out.
- Files are written to the app cache and shared through the system share sheet.

## Performance

Everything below is measured, not guessed. The bench harness lives in
`app/src/test/kotlin/com/dahl404/penny937/perf/` and runs on the JVM with Robolectric's
native Skia, so it measures the real `android.graphics` paths:

```bash
source ~/.local/opt/android-env.sh
./gradlew testDebugUnitTest --tests '*RenderBenchTest*'
./gradlew testDebugUnitTest --tests '*InkFidelityTest*'   # ink must not change
./gradlew testDebugUnitTest --tests '*StrokerPipelineTest*'
./gradlew testDebugUnitTest --tests '*RasterizePathTest*'
./gradlew testDebugUnitTest --tests '*ProjectOpsTest*' --tests '*ProjectCodecTest*'
./gradlew testDebugUnitTest --tests '*ReferenceExportTest*' --tests '*ReferenceFilesTest*'
./gradlew testDebugUnitTest --tests '*GifRoundTripTest*' --tests '*VideoReferenceCapTest*'
./gradlew testDebugUnitTest --tests '*ProjectCodecFuzzTest*' --tests '*CanvasViewportTest*'
./gradlew testDebugUnitTest --tests '*CanvasViewportCodecTest*'
./gradlew assembleDebugAndroidTest        # compile the on-device export smoke test
./gradlew connectedDebugAndroidTest       # run it on a device/emulator
```

90 strokes x 120 points, timings in ms (desktop JVM, so phones are slower in
absolute terms but the ratios hold):

| Work | Before | After |
| --- | --- | --- |
| Rasterise one frame @1080 | 117 | 34 |
| Rasterise one frame @480 | 95 | 26 |
| Timeline thumbnail @104 | 46 | 17 |
| Live ink, per motion event @1080 | ~0.4 | 0.48 (one ribbon fill) |
| GIF: quantise 480x480 (flat art) | 5.0 | 5.0 |
| GIF: quantise 480x480 (noisy photo) | 52 | 23 |
| GIF: LZW 480x480 | 5.8 | 1.6 |
| GIF export, 24 frames @480 | 452 | 125 |
| Project save (5400 strokes) | 33 | 12 |
| Project load (5400 strokes) | 34 | 19 |

What changed:

- **One path per stroke.** Ink is now a filled ribbon outline (per-segment offsets
  with round joins and caps) instead of hundreds of `drawLine` stamps with a
  different width each. This was ~70% of frame rendering, thumbnail rendering and
  export time.
- **Live ink is buffered.** The stroke under the pen is drawn opaque into a scratch
  bitmap and composited once on lift, so translucent ink never darkens at joints and
  the preview is pixel-identical to the committed stroke. The eraser buffers a mask
  and cuts out through `DST_OUT` in an isolated layer.
- **Interactive layers render at display scale.** Layer caches and the live-ink
  scratch buffer are sized from the current zoom (quantised into a few steps), not
  the project resolution, so a 4096px canvas no longer reserves a 64 MiB bitmap per
  layer just to show it on a phone. The eyedropper and erase/morph previews map
  project coordinates into that cache. Exports still rasterise from vectors at their
  own target size.
- **Playback is prefetched off the main thread.** While playing, a worker rasterises
  the next eight frames into a bounded bitmap cache; the canvas only draws ready
  frames, so advancing the playhead never waits on vector rendering. Frames are
  invalidated by id, revision and preview width.
- **Single-layer fast path.** Frames with one opaque, eraser-free layer render
  straight into the output bitmap, skipping a full-size scratch bitmap and composite.
- **Exports fan out.** Frames are rasterised, quantised and LZW-compressed on all
  cores in bounded batches; only the byte stream stays ordered. Batch size is
  recalculated from the heap budget and the frame's estimated buffers (bitmap,
  pixels, encoder output), so a 4096px PNG export degrades to one frame at a time
  instead of exhausting memory. The MP4 pipeline depth follows the same budget.
- **Palette + LZW.** Median cut uses counting sort over 5-bit channels on primitive
  arrays, the 32768-entry nearest-colour table comes from a k-d tree instead of a
  256-way scan, and LZW uses an open-addressed dictionary instead of a boxed
  `HashMap`.
- **Cheap bookkeeping.** Frame/layer render revisions, total ticks and the canvas
  repaint key are cached instead of being recomputed by walking every frame, layer
  and stroke; playback frame lookup is a binary search.
- **Binary project I/O** packs its own bytes (2.8x faster saves) while staying
  byte-compatible with the existing format.

A language switch was considered and rejected: with the geometry batched into one
Skia call per stroke the remaining cost is Skia rasterisation and memory traffic,
which an NDK port would not remove.

## Layout

```
app/src/main/kotlin/com/dahl404/penny937/
  MainActivity.kt, PennyViewModel.kt
  model/          Project, Frame, Layer, Stroke, immutable edits
  data/           project store (binary format), prefs
  paint/          stroke smoothing, grain, layer bitmap renderer, rasterizer
  export/         GIF/LZW/quantizer, MP4 encoder, memory-aware export batches
  editor/         EditorModel plus Playback/Save/Export controllers
  ui/             Apple-flavoured Compose UI, canvas view, viewport camera, sheets
```

`ProjectFormat` is the persistence codec contract; `ProjectCodec` is its compact
binary implementation. `PlaybackController` owns the playhead and prefetch cache,
`SaveController` owns autosave state and write serialisation, `ExportController`
owns the export job, and `CanvasViewport` owns fit/pan/zoom/camera clamping.

The canvas is a custom `View` (`DrawingCanvasView`) for full stylus event access
(historical samples, pressure, stylus button, hover cursor, palm rejection).
Everything else is Jetpack Compose. Fonts: Inter (SIL OFL, license in
`app/src/main/res/raw/inter_license.txt`).
