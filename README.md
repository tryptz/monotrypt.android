<p align="center">
  <h1 align="center">MONOCHROME</h1>
  <p align="center">
    <strong>High-Fidelity Music Streaming for Android</strong><br/>
    <em>With Industrial-Grade AutoEQ Headphone Correction</em>
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

> **ALPHA RELEASE** — Monochrome is under active development. Core streaming and the AutoEQ engine are functional, but expect rough edges, missing features, and breaking changes. We ship fast and iterate faster.

---

## What is Monochrome?

Monochrome is a music streaming client for Android built from the ground up with one obsession: **audio fidelity**. It connects to TIDAL for lossless and hi-res streaming, then layers on a precision equalization engine that can surgically correct your headphones to match reference target curves — all in real time, on your phone.

This isn't a toy EQ with 5 sliders. This is a **10-band parametric equalizer** powered by a custom **AutoEQ algorithm** that analyzes frequency response data and generates correction filters with 64-bit mathematical precision.

---

## Key Features

### Streaming & Playback

| Feature | Details |
|---------|---------|
| **TIDAL Integration** | Stream at Low (96 kbps), High (320 kbps), Lossless (FLAC), or Hi-Res (24-bit FLAC) |
| **Gapless Playback** | Powered by Media3/ExoPlayer for seamless transitions |
| **ReplayGain** | Automatic volume normalization across tracks and albums |
| **Offline Mode** | Download tracks for offline listening |
| **Android Auto** | Full integration with car displays via MediaBrowserService |
| **Chromecast** | Cast audio to any Cast-enabled device |
| **Queue Management** | Drag-to-reorder, shuffle, repeat modes |
| **Playback Speed** | Variable speed control |

### Library & Discovery

- **Home Feed** — Curated content, mixes, and recommendations
- **Search** — Find tracks, albums, artists, and playlists
- **Library** — Your playlists, downloads, favorites, and history
- **Artist & Album Views** — Rich detail pages with discographies
- **Playlist Import** — Bulk import from other services
- **Scrobbling** — Track your listening history

### The Interface

- **Jetpack Compose + Material 3** — Modern, fluid UI built entirely in Compose
- **Now Playing** — Multiple view modes: Cover Art, Lyrics, Queue, and Visualizer
- **120Hz Optimized** — Butter-smooth on high-refresh displays
- **Dark Theme** — Full dark mode support
- **Home Screen Widget** — Glance-powered Now Playing widget
- **Deep Linking** — Open `monochrome.tf` links directly in the app

---

## The AutoEQ Engine

This is what sets Monochrome apart. The built-in AutoEQ engine uses a proprietary **Global Greedy Iterative Peaking** algorithm to generate headphone correction filters with clinical precision.

### How It Works

```
Your Headphone's FR  ──►  AutoEQ Engine  ──►  Correction Curve  ──►  Flat/Target Response
     (measured)           (10 iterations)      (10 PEQ bands)         (what you hear)
```

**Step 1 — Scan.** The engine evaluates the entire 20 Hz – 20 kHz spectrum and identifies the single largest deviation from the target curve.

**Step 2 — Isolate.** It calculates the optimal frequency, gain, and Q-factor to surgically flatten that deviation using a peaking filter.

**Step 3 — Refine.** After applying the filter, the engine re-evaluates the *entire* remaining error — accounting for the phase-coherent effects of every previous filter. Then it repeats.

**Step 4 — Converge.** After 10 iterations, you get a tightly fitted correction curve that hugs your target with sub-dB accuracy.

### Why It's Different

| Traditional Mobile EQ | Monochrome AutoEQ |
|-----------------------|-------------------|
| 5-band graphic EQ with fixed frequencies | 10-band fully parametric EQ |
| 32-bit float math | **64-bit double precision** — eliminates singularities at subsonic frequencies |
| Fixed Q factors | **Adaptive Q up to 6.0** — catches narrow resonance peaks in IEMs and planars |
| Manual slider guessing | **Automatic correction** from measurement data |
| No target curve awareness | **10+ research-grade target curves** built in |

### Built-In Target Curves

Choose your reference. Every curve ships with 384 frequency data points for surgical precision:

| Target Curve | Description |
|-------------|-------------|
| **Harman Over-Ear 2018** | The industry standard for over-ear headphones (default) |
| **Harman In-Ear 2019** | Optimized for IEMs with enhanced bass shelf |
| **Diffuse Field** | Flat at the eardrum — the academic reference |
| **Knowles** | BA driver compensation curve |
| **Moondrop VDSF** | Virtual Diffuse Sound Field — popular in the IEM community |
| **Hi-Fi Endgame 2026** | Cutting-edge target for modern audiophile preferences |
| **PEQdB Ultra** | High-precision parametric reference |
| **SEAP / SEAP Bass** | Spectral Error Attenuation Protocol variants |
| **Flat** | Zero correction — bypass reference |

### 4,000+ Headphone Profiles

Monochrome integrates directly with the [AutoEq GitHub repository](https://github.com/jaakkopasanen/AutoEq) — the largest open database of headphone frequency response measurements. Select your headphone model from **4,000+ profiles** and get instant correction. No measurement rig required.

### Custom Measurements

Own a measurement mic? Upload your own frequency response data (CSV/TXT) through the built-in **Headphone Calibration** screen and generate custom correction curves.

---

## Architecture

Monochrome is built with modern Android architecture principles:

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

## Getting Started

### Requirements

- Android 8.0+ (API 26)
- Internet connection for streaming
- A TIDAL account for music access

### Download the Alpha

> [**Download monochrome-autoeq-alpha.apk**](monochrome-autoeq-alpha.apk)
>
> This is an alpha build. Install at your own risk. We recommend sideloading via ADB or enabling "Install from unknown sources" in your device settings.

### Build from Source

```bash
# Clone the repository
git clone https://github.com/user/tf.monochrome.android.git
cd tf.monochrome.android

# Build the release APK
./gradlew assembleRelease

# The APK will be at:
# app/build/outputs/apk/release/app-release.apk
```

**Build requirements:**
- JDK 17+
- Android SDK with API 36
- Gradle (wrapper included)

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

## Alpha Roadmap

Monochrome is in **active alpha development**. Here's what's coming:

- [ ] Public beta release on Google Play
- [ ] Additional streaming service backends
- [ ] Bluetooth codec detection and per-codec EQ profiles
- [ ] Measurement microphone integration for in-situ correction
- [ ] Crossfeed for headphone soundstage widening
- [ ] Parametric EQ sharing and community profiles
- [ ] Multi-device sync
- [ ] Tablet and foldable layouts

---

## Contributing

Monochrome is in early alpha. If you're interested in contributing — especially in audio DSP, Android media internals, or Compose UI — open an issue or reach out.

---

## Acknowledgments

- [AutoEq](https://github.com/jaakkopasanen/AutoEq) by Jaakko Pasanen — headphone measurement database
- [Media3](https://developer.android.com/media/media3) — Android's modern media playback library
- [Jetpack Compose](https://developer.android.com/compose) — declarative UI toolkit
- The headphone measurement community for making audiophile-grade correction accessible to everyone

---

<p align="center">
  <strong>Monochrome</strong> — Built with obsession for audio fidelity and mathematical precision.<br/>
  <sub>Alpha Release · Android 8.0+ · Kotlin · Jetpack Compose · Media3 · AutoEQ</sub>
</p>
