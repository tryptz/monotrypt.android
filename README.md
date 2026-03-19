# Monochrome Android

Native Android music streaming client with a built-in parametric EQ engine. Connects to TIDAL.

**Status:** Alpha — works, but rough edges.

[**Download APK**](monochrome-autoeq-alpha.apk) (sideload)

---

## Features

- TIDAL streaming (up to Hi-Res 24-bit FLAC)
- Gapless playback (Media3/ExoPlayer)
- ReplayGain normalization
- Offline downloads
- Android Auto, Chromecast
- Queue management, shuffle, repeat
- Search, playlists, artist/album pages
- Scrobbling (Last.fm, ListenBrainz)
- Home screen widget
- Dark theme, 120Hz support

## AutoEQ

10-band parametric EQ that auto-corrects headphone frequency response to a target curve.

- 4,000+ headphone profiles from [AutoEq](https://github.com/jaakkopasanen/AutoEq)
- 10+ target curves (Harman OE/IE, Diffuse Field, Moondrop VDSF, etc.)
- 64-bit double precision math
- Adaptive Q up to 6.0
- Upload custom measurements (CSV/TXT)
- Interactive drag-to-adjust EQ graph
- Save/load presets

## Tech

Kotlin 2.1.0, Jetpack Compose + Material 3, Media3 1.5.1, Room, Ktor, Hilt, Appwrite auth.

Android 8.0+ (API 26). Target SDK 36.

## Build

```bash
git clone https://github.com/tryptz/tf.monochrome.android.git
cd tf.monochrome.android
./gradlew assembleRelease
```

Requires JDK 17+ and Android SDK 36.

## Acknowledgments

- [AutoEq](https://github.com/jaakkopasanen/AutoEq) — headphone measurements
- [Media3](https://developer.android.com/media/media3) — playback engine
- [Jetpack Compose](https://developer.android.com/compose) — UI
