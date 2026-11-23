# PeNNy-937

Animation app for Android. Requires Android 8.0 (API 26) or newer.

Free and open source under the GPL-3.0 (see `LICENSE`). No ads, no account, no
purchases, no analytics, and no `INTERNET` permission — the app is technically
unable to send anything off the device. It does one job and ships nothing else.

## Features

- Pen, pencil, marker and eraser, with pressure support for stylus input
- Strokes are stored as vectors, so canvas size and export size are independent
- Layers: add, rename, duplicate, hide, reorder, opacity, merge down, lock, clear
- Frames: add, duplicate, hold, reorder, delete, with onion skin
- Line, rectangle, square, ellipse and circle shapes
- Morph tool for warping ink on a layer, and move/rotate for a whole layer
- Reference images, and reference video split into frames on the timeline
- Eyedropper, with a choice of which layer to sample
- Grid and rule-of-thirds guides
- Export to MP4, animated GIF, PNG sequence, or the current frame as PNG
- Projects autosave, and can be exported and imported as a zip

## Gestures

| Action | Result |
| --- | --- |
| Stylus or mouse | Draw |
| Pen side button held while drawing | Erase |
| Finger | Pan the canvas |
| Two fingers | Pinch zoom and pan |
| Double tap | Toggle fit and zoom |
| Two fingers on a layer being moved | Rotate the layer |

Fingers pan and zoom by default; the pen draws.

## Build

Requires JDK 17+ and Android SDK platform 37.

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Credits

Inter font, SIL Open Font License: `app/src/main/res/raw/inter_license.txt`.

## Shelved

See [SHELVED.md](SHELVED.md) for features that were tested but not kept.
