# MonoTrypT Android

A music player for Android that unifies TIDAL HiFi streaming, local file playback, and encrypted collection manifests into a single library. Built for audiophiles — features a fully parametric AutoEQ engine, a 33-processor native DSP mixer, and a ProjectM visualizer.

[**Download APK**](https://github.com/tryptz/monotrypt.android/releases/tag/1.0.3) — sideload or install via ADB.

Android 8.0+ (API 26). Alpha.

---

## Features

- **TIDAL HiFi streaming** — lossless FLAC and 24-bit hi-res, with quality auto-switching between WiFi and cellular
- **Local library** — MediaStore-backed scanner with embedded tag reading, incremental sync, and filesystem watching
- **Encrypted collections** — AES-256-GCM manifest-based music collections with transparent decryption during playback
- **AutoEQ** — 10-band parametric headphone correction from 4,000+ measurements, 10 built-in target curves
- **ProjectM visualizer** — real-time OpenGL audio visualization with a preset library
- **ReplayGain** — track/album modes with adjustable preamp and peak protection
- **Scrobbling** — dual Last.fm and ListenBrainz support
- **Offline downloads** — FLAC files via WorkManager with optional LRC lyric export
- **Android Auto** — full media browsing via `MediaBrowserService`
- **Chromecast** — Media3 Cast integration
- **Cloud sync** — favorites, history, and playlists via Appwrite/PocketBase
- **Glance widget** — Now Playing home screen widget

---

## Audio Pipeline

```
ExoPlayer → ReplayGainProcessor → EqProcessor → ProjectM Tap → AudioSink
```

Playback is handled by Media3/ExoPlayer with audio focus, wake lock, and gapless transitions. Streams support progressive HTTP and DASH manifests (base64-encoded MPD). The next two tracks are pre-resolved to cut transition latency.

---

## AutoEQ Engine

A 10-band fully parametric equalizer that generates headphone correction filters from frequency response measurements.

**Algorithm** — greedy iterative peak-finding:
1. Normalize the measurement against the target over the 250–2500 Hz midrange window
2. Scan 20 Hz–16 kHz for the worst deviation (sub-50 Hz weighted 1.2×)
3. Invert the deviation as gain (clamped ±12 dB, ±8 dB above 8 kHz), estimate Q from bandwidth
4. Subtract the new filter's biquad response from the remaining error
5. Repeat up to 10 bands, or stop early if max error < 0.05 dB

**Built-in target curves:**

| Target | Use |
|--------|-----|
| Harman Over-Ear 2018 | Over-ear headphones |
| Harman In-Ear 2019 | IEMs |
| Diffuse Field | Flat at the eardrum |
| Knowles | BA driver compensation |
| Moondrop VDSF | Virtual diffuse sound field |
| Hi-Fi Endgame 2026 | Modern preference target |
| PEQdB Ultra | Parametric reference |
| SEAP / SEAP Bass | Spectral error attenuation |
| Flat | Bypass |

Over 4,000 headphone measurements load from a remote repository. Users can also upload custom measurement files. EQ settings persist in DataStore and update in real time without interrupting playback.

---

## Architecture

Single-module Android app (`app/`), package `tf.monochrome.android`.

```
tf.monochrome.android/
├── audio/eq/          # AutoEQ engine (AutoEqEngine, EqProcessor, FrequencyTargets)
├── auto/              # Android Auto — MonochromeMediaBrowserService
├── data/
│   ├── ai/            # Gemini 2.0 Flash integration
│   ├── api/           # TIDAL HiFi API client + instance failover
│   ├── auth/          # Google Sign-In + Appwrite OAuth
│   ├── collections/   # Encrypted collection manifests (AES-256-GCM)
│   ├── db/            # Main Room database (22 tables, v4 schema)
│   ├── downloads/     # WorkManager offline download
│   ├── local/         # MediaStore scanner + tag reader + filesystem watcher
│   ├── preferences/   # DataStore-backed settings
│   ├── scrobbling/    # Last.fm + ListenBrainz
│   └── sync/          # Appwrite/PocketBase cloud sync
├── di/                # Hilt modules (App, API, Database, Network)
├── domain/            # Domain models + use cases
├── player/            # Media3 playback service, stream resolution, queue, ReplayGain
├── ui/                # All screens and composables (Compose + Material 3)
├── visualizer/        # ProjectM OpenGL renderer + JNI audio tap
└── widget/            # Glance Now Playing widget
```

### Database

Room v2 schema (version 3), 22 tables across three domains:

- **Core:** favorites, play history, playlists, downloads, cached lyrics, EQ presets
- **Local media:** tracks, albums, artists, genres, folders, scan state
- **Collections:** collection metadata, streaming URLs, artist/album cross-references

All queries are Flow-based for reactive UI updates. `fallbackToDestructiveMigration` is enabled — schema upgrades wipe local data.

### Networking

Ktor with OkHttp engine. 200-entry LRU cache with 30-minute TTL. Instance failover system: pulls available API endpoints from uptime workers, falls back to hardcoded instances, shuffles the pool per request. 429 responses rotate to the next instance. Instance lists cached in DataStore with 15-minute TTL.

---

## Tech Stack

| Component | Version |
|-----------|---------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose, Material 3 (BOM 2024.12.01) |
| Audio | Media3 / ExoPlayer 1.5.1 |
| Casting | Media3 Cast, Google Cast Framework 22.0.0 |
| Visualizer | ProjectM (C++17 via JNI) |
| Images | Coil 3.0.4 |
| Database | Room 2.7.1 |
| Preferences | DataStore 1.1.1 |
| Network | Ktor 3.0.3 (OkHttp engine) |
| DI | Hilt 2.57.1 |
| Serialization | Kotlinx Serialization 1.7.3 |
| Auth | Appwrite 7.0.0, Google Credentials API |
| Encryption | AES-256-GCM (AndroidX Security Crypto) |
| AI | Gemini 2.0 Flash |
| Background | WorkManager 2.10.0 |
| Widgets | Glance 1.1.1 |
| Build | AGP 9.0.0, KSP 2.1.0-1.0.29, CMake 3.22.1 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| ABI | arm64-v8a, armeabi-v7a, x86_64 |

---

## Build

**Requirements:** JDK 17+, Android SDK 36, NDK `28.2.13676358`

```bash
git clone https://github.com/tryptz/tf.monochrome.android.git
cd tf.monochrome.android
./gradlew assembleDebug
```

**Optional — TIDAL streaming:** add credentials to `local.properties`:
```
TIDAL_CLIENT_ID=...
TIDAL_CLIENT_SECRET=...
```
Without these the app builds and runs, TIDAL content just won't load.

**Optional — release builds:** copy `keystore.properties.example` → `keystore.properties` and fill in signing keys, then:
```bash
./gradlew assembleRelease
```

**Install to device:**
```bash
./gradlew installDebug
```

The NDK is required for the ProjectM visualizer and DSP engine native code (`third_party/projectm/`, `app/src/main/cpp/`). Install NDK `28.2.13676358` via Android Studio SDK Manager.
