# projectM Android integration

This app now has a `projectM`-ready visualizer shell in the player UI, but the native renderer is not linked yet.

## What upstream projectM requires

`libprojectM` is not a plain Android dependency. Upstream documents it as:

- a C++ library
- built with CMake and the Android NDK
- rendered through OpenGL / GLES
- driven by PCM audio supplied by the host app

References:

- https://github.com/projectM-visualizer/projectm
- https://raw.githubusercontent.com/projectM-visualizer/projectm/master/BUILDING.md

## What to wire next in this app

1. Add a native bridge under `app/src/main/cpp/` and build it with `externalNativeBuild`.
2. Build `libprojectM` for Android with GLES enabled and package the shared library plus headers.
3. Expose a dedicated rendering surface from Android:
   - `GLSurfaceView` or `TextureView` hosted from Compose via `AndroidView`
4. Feed PCM frames from playback into the bridge:
   - capture audio in `PlaybackService`
   - push short float/PCM windows into native code
   - call projectM audio ingestion on a steady cadence
5. Load preset assets:
   - add a curated preset pack in app assets or downloaded storage
   - expose preset selection and shuffle behavior later
6. Flip the UI host from fallback mode to native mode once the bridge reports ready.

## Current app-side seam

The player visualizer surface is isolated in:

- `app/src/main/java/tf/monochrome/android/ui/player/VisualizerComponent.kt`

That file is the intended Compose host for the future native `projectM` surface. Right now it renders a polished fallback shell and clearly labels the native engine as staged.
