<p align="center">
  <h1 align="center">MONOCHROME</h1>
  <p align="center">
    <strong>High-Fidelity Music Streaming — Native Android</strong><br/>
    <em>A full native conversion of the Monochrome web app, with Industrial-Grade AutoEQ</em>
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3ddc84?logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Oreo)-blue" alt="Min SDK 26"/>
  <img src="https://img.shields.io/badge/Target%20SDK-36-blue" alt="Target SDK 36"/>
  <img src="https://img.shields.io/badge/Kotlin-2.1.0-7f52ff?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material3-4285f4?logo=jetpackcompose&logoColor=white" alt="Compose"/>
  <img src="https://img.shields.io/badge/Status-Alpha-orange" alt="Alpha"/>
</p>

---

> [!IMPORTANT]
> **ALPHA RELEASE** — Monochrome Android is in active development. This is a full native Android conversion of the Monochrome web app. Core streaming and the AutoEQ engine are functional, but expect rough edges and ongoing changes.

---

## What is Monochrome Android?

Monochrome started as a web application — a precision music streaming client built around uncompromising audio fidelity. This repository is the **full native Android conversion**: every feature rebuilt from scratch using Android-native technologies, no webview, no wrappers.

It connects to TIDAL for lossless and hi-res streaming and layers on a precision equalization engine that surgically corrects your headphones to match reference target curves — all in real time, natively on your device.

---

## Download the Alpha

> [**Download monochrome-autoeq-alpha.apk**](monochrome-autoeq-alpha.apk)
>
> Sideload via ADB or enable "Install from unknown sources" in your device settings.

---

## Features

### Streaming & Playback

| Feature | Details |
|---------|---------|
| **TIDAL Integration** | Low (96 kbps) · High (320 kbps) · Lossless FLAC · Hi-Res 24-bit FLAC |
| **Gapless Playback** | Powered by Media3/ExoPlayer |
| **ReplayGain** | Volume normalization across tracks and albums |
| **Offline Mode** | Download tracks for playback without a connection |
| **Android Auto** | Full car display integration via MediaBrowserService |
| **Chromecast** | Cast audio to any Cast-enabled device |
| **Queue Management** | Drag-to-reorder, shuffle, repeat modes |
| **Variable Speed** | Adjustable playback speed |

### Library & Discovery

- **Home Feed** — Curated content, mixes, and recommendations
- **Search** — Tracks, albums, artists, and playlists
- **Library** — Your playlists, downloads, favorites, and history
- **Artist & Album Pages** — Rich detail views with full discographies
- **Playlist Import** — Bulk import from other services
- **Scrobbling** — Full listening history tracking

### Interface

- **Jetpack Compose + Material 3** — Entirely native, no webview
- **Now Playing** — Cover Art, Lyrics, Queue, and Visualizer modes
- **120Hz Optimized** — Smooth on high-refresh-rate displays
- **Dark Theme** — Full dark mode
- **Home Screen Widget** — Glance-powered Now Playing widget
- **Deep Linking** — Opens `monochrome.tf` links directly in the app

---

## The AutoEQ Engine

The crown feature of Monochrome Android. A proprietary **Global Greedy Iterative Peaking** algorithm generates headphone correction filters with precision that doesn't exist in any other mobile EQ app.

### How It Works

```
Your Headphone's FR  ──►  AutoEQ Engine  ──►  Correction Curve  ──►  Target Response
     (measured)           (10 iterations)      (10 PEQ bands)         (what you hear)
```

**Scan** — Evaluate the entire 20 Hz – 20 kHz spectrum. Find the single largest deviation from the target curve.

**Isolate** — Calculate the exact frequency, gain (dB), and Q-factor to surgically flatten that deviation with a peaking filter.

**Refine** — Re-evaluate the full remaining error, accounting for the phase-coherent interactions of every previous filter. Repeat.

**Converge** — After 10 iterations, a tightly fitted correction that hugs the target with sub-dB accuracy.

### Why It's Different

| Standard Mobile EQ | Monochrome AutoEQ |
|-------------------|-------------------|
| 5–10 band graphic EQ, fixed frequencies | 10-band fully parametric EQ |
| 32-bit float math | **64-bit double precision** — eliminates singularities at subsonic frequencies |
| Fixed Q factors | **Adaptive Q up to 6.0** — catches narrow resonance peaks in IEMs and planars |
| Manual slider guessing | **Automatic correction** from measurement data |
| No target awareness | **10+ research-grade target curves** built in |

### Built-In Target Curves

384 frequency data points per curve for surgical correction:

| Target Curve | Description |
|-------------|-------------|
| **Harman Over-Ear 2018** | Industry standard for over-ear headphones (default) |
| **Harman In-Ear 2019** | Optimized for IEMs with enhanced bass shelf |
| **Diffuse Field** | Flat at the eardrum — the academic reference |
| **Knowles** | BA driver compensation curve |
| **Moondrop VDSF** | Virtual Diffuse Sound Field — popular in the IEM community |
| **Hi-Fi Endgame 2026** | Cutting-edge target for modern audiophile preferences |
| **PEQdB Ultra** | High-precision parametric reference |
| **SEAP / SEAP Bass** | Spectral Error Attenuation Protocol variants |
| **Flat** | Zero correction — bypass reference |

### 4,000+ Headphone Profiles

Monochrome integrates directly with the [AutoEq GitHub repository](https://github.com/jaakkopasanen/AutoEq) — the largest open database of headphone frequency response measurements. Pick your headphone model from **4,000+ profiles** and get instant, one-tap correction. No measurement rig needed.

### Custom Measurements

Own a measurement microphone? Upload your own frequency response data (CSV/TXT) through the built-in **Headphone Calibration** screen to generate custom correction curves for any transducer.

### EQ Interface

- **10-Band Interactive Graph** — Drag bands directly on the frequency response canvas
- **Spectral Color Coding** — Bands colored by frequency range (sub-bass, mids, highs)
- **Real-Time Visualization** — See the correction curve update as you adjust
- **120Hz UI** — No lag, no jitter on supported displays
- **Preset Management** — Save, load, and delete custom EQ presets
- **Preamp Control** — Global gain adjustment to prevent clipping

---

## Architecture

Built from scratch as a proper native Android app — not a port, not a wrapper.

```
┌─────────────────────────────────────────────────┐
│                   UI Layer                       │
│         Jetpack Compose + Material 3             │
│    Navigation Compose  ·  Glance Widgets         │
├─────────────────────────────────────────────────┤
│                Domain Layer                      │
│          ViewModels  ·  Use Cases                │
│        Kotlin Coroutines + Flow                  │
├─────────────────────────────────────────────────┤
│                 Data Layer                       │
│   Room DB  ·  DataStore  ·  Ktor HTTP Client     │
│   Appwrite Auth  ·  Google OAuth                 │
├─────────────────────────────────────────────────┤
│                Audio Layer                       │
│   Media3/ExoPlayer  ·  AutoEQ Engine             │
│   ReplayGain  ·  EQ Processor  ·  Chromecast     │
├─────────────────────────────────────────────────┤
│              Platform Layer                      │
│   PlaybackService  ·  Android Auto               │
│   WorkManager  ·  Notifications                  │
└─────────────────────────────────────────────────┘
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 (BOM 2024.12.01) |
| Audio | Media3 / ExoPlayer 1.5.1 |
| Database | Room 2.7.1 |
| Preferences | DataStore 1.1.1 |
| Networking | Ktor 3.0.3 (OkHttp engine) |
| DI | Hilt 2.57.1 |
| Serialization | Kotlinx Serialization 1.7.3 |
| Auth | Appwrite 7.0.0 + Google Credentials API |
| Background | WorkManager 2.10.0 |
| Widgets | Glance 1.1.1 |
| Cast | Google Cast Framework 22.0.0 |
| Build | AGP 9.0.0 + KSP 2.1.0 |

---

## Requirements

- Android 8.0+ (API 26)
- Internet connection for streaming
- A TIDAL account for music access

---

## Build from Source

```bash
git clone https://github.com/tryptz/tf.monochrome.android.git
cd tf.monochrome.android

./gradlew assembleRelease

# Output:
# app/build/outputs/apk/release/app-release.apk
```

**Requires:** JDK 17+, Android SDK API 36

---

## Project Structure

```
app/src/main/java/tf/monochrome/android/
├── audio/eq/           # AutoEQ engine, target curves, EQ processor
├── data/
│   ├── api/            # TIDAL + AutoEq GitHub API clients
│   ├── auth/           # Google OAuth, Appwrite auth
│   ├── db/             # Room database, DAOs, entities
│   ├── downloads/      # Offline download manager
│   ├── preferences/    # DataStore preferences
│   ├── repository/     # Data repositories
│   ├── scrobbling/     # Listen history tracking
│   └── sync/           # Cloud backup via PocketBase
├── domain/model/       # Domain models
├── player/             # PlaybackService, queue, ReplayGain
├── auto/               # Android Auto integration
├── di/                 # Hilt dependency injection modules
└── ui/
    ├── components/     # Shared UI components
    ├── eq/             # Equalizer screens + frequency graph
    ├── home/           # Home feed
    ├── search/         # Search
    ├── library/        # Library / playlists
    ├── player/         # Now Playing screen
    ├── detail/         # Album / Artist detail
    ├── settings/       # Settings (9 tabs)
    ├── profile/        # User profile
    ├── theme/          # Material 3 theming
    └── widget/         # Glance widget
```

---

## Acknowledgments

- [AutoEq](https://github.com/jaakkopasanen/AutoEq) by Jaakko Pasanen — open headphone measurement database
- [Media3 / ExoPlayer](https://developer.android.com/media/media3) — Android media playback
- [Jetpack Compose](https://developer.android.com/compose) — declarative Android UI
- The headphone measurement community for making reference-grade correction available to everyone

---

<p align="center">
  <strong>Monochrome Android</strong> — The web app, gone fully native.<br/>
  <sub>Alpha · Android 8.0+ · Kotlin · Jetpack Compose · Media3 · AutoEQ</sub>
</p>
