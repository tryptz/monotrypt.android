# MonoTrypt Android

Native Android music streaming client with a built-in parametric equalizer. Connects to TIDAL for lossless and hi-res playback. Alpha.

[**Download APK**](https://github.com/tryptz/tf.monochrome.android/releases/download/Android-beta-release.apk/release_1.0.1.apk) — sideload or install via ADB.

---

## Infrastructure

### Audio Pipeline

Playback runs through Media3/ExoPlayer with audio focus, wake lock, and gapless transitions. The player handles progressive HTTP streams and DASH manifests (base64-encoded MPD). Stream URLs are resolved per-track with quality selection — LOW (96 kbps), HIGH (320 kbps), LOSSLESS (FLAC), or HI_RES (24-bit FLAC). Quality switches automatically between WiFi and cellular based on user preference.

ReplayGain is applied in the linear domain (`10^(dB/20)`) with peak protection to prevent clipping. Supports track mode, album mode, or off, with adjustable preamp.

The queue is managed in-memory with Fisher-Yates shuffle (preserving the current track), three repeat modes, and drag-to-reorder. The next two tracks are pre-resolved to cut transition latency.

### Networking

API requests go through Ktor with an OkHttp engine. There's a 200-entry LRU cache with 30-minute TTL. The client runs an instance failover system — it pulls a list of available API endpoints from uptime workers, falls back to hardcoded instances if those are unreachable, and shuffles the pool on each request. Rate-limited responses (429) trigger rotation to the next instance. Instance lists are cached in DataStore with a 15-minute TTL.

### Database

Room v2 schema (version 3) with 22 tables across three domains:

**Core library:**
- `favorite_tracks`, `favorite_albums`, `favorite_artists` — local library
- `history_tracks` — play history, indexed on timestamp
- `user_playlists` + `playlist_tracks` — playlist management with cascade delete
- `downloaded_tracks` — offline files, indexed on file path
- `cached_lyrics` — lyrics JSON with sync flag, indexed on cache time
- `eq_presets` — serialized EQ band arrays, preamp, target reference, custom flag

**Local media:**
- `local_tracks`, `local_albums`, `local_artists`, `local_genres`, `local_folders` — on-device library indexed from MediaStore
- `scan_state` — tracks incremental scan progress

**Collections:**
- `collections`, `collection_artists`, `collection_albums`, `collection_tracks` — encrypted collection metadata
- `collection_direct_links` — resolved streaming URLs
- `collection_track_artist_cross_ref`, `collection_album_artist_cross_ref` — many-to-many artist relationships

All queries are Flow-based for reactive UI updates.

### Downloads

WorkManager dispatches download jobs on `Dispatchers.IO` with network constraints. Files are written to a user-selected folder via SAF (Storage Access Framework) or fall back to external app storage. Tracks are saved as FLAC with sanitized filenames (`{Artist} - {Title}.flac`). Lyrics optionally export as LRC with `[mm:ss.ms]` timecodes. Three retry attempts with exponential backoff.

### Local Media

A MediaStore-backed scanner indexes on-device audio files. The scanner reads embedded metadata via a `TagReader`, groups tracks into albums/artists/genres/folders, and stores everything in the local media tables. A `FileObserverService` watches for filesystem changes to keep the library in sync. Configurable minimum duration filter and path exclusion list. Scan progress is emitted as a reactive Flow.

### Collections

Encrypted music collections loaded from remote manifests (version 1.3). Each manifest contains artist, album, and track metadata with AES-256-GCM encrypted streaming URLs. A `DecryptingDataSource` handles transparent decryption during playback. Collections are parsed, cached in Room, and browsable alongside the streaming catalog..

### Chromecast

Media3 Cast integration with Google Cast Framework. Playback can be routed to Chromecast-compatible devices.

### Android Auto

A `MediaBrowserService` exposes the library to Android Auto.

### Auth

Authentication goes through Appwrite with Google OAuth. The OAuth callback redirects back into the app via a registered deep link scheme. Email/password auth is also supported. The Appwrite user ID maps to a PocketBase record for cloud sync — favorites, history, and playlists sync bidirectionally with deduplication by ID and latest-timestamp wins for history.

### Scrobbling

Dual scrobbling to Last.fm and ListenBrainz. Last.fm uses MD5-signed API requests. ListenBrainz uses token-based JSON POST. Now-playing updates fire on track start, scrobbles fire on track end.

### DI & Navigation

Full Hilt injection across the app — four modules (App, Network, Database, API) providing singletons. Navigation uses Jetpack Compose Navigation with three main tabs (Home, Search, Library) in a HorizontalPager, plus detail screens for albums, artists, playlists, and mixes. A mini player overlay persists across all screens except Now Playing.

### Native Layer

C++ code compiled via CMake (C++17, targeting arm64-v8a, armeabi-v7a, x86_64). Includes a ProjectM visualizer bridge with JNI bindings and a lock-free audio ring buffer for feeding audio samples to the visualizer.

---

## User Interface

The player is designed with a modern, curved aesthetic that follows the physical screen radius of high-end Android devices.

- **Floating Player**: A detached, card-based UI that can be swiped up/down to expand or collapse.
- **Dynamic Views**: One-tap toggle between high-fidelity album art and the ProjectM-based visualizer.
- **Micro-Animations**: Smooth cross-fading transitions and gesture-based interaction models.
- **Theming**: Supports Dark, Light (White), and transparent "Clear" modes for varied aesthetics.

---

## AutoEQ Engine

A 10-band fully parametric equalizer that generates headphone correction filters from frequency response measurements.

### Algorithm

The engine runs a greedy iterative peak-finding loop. Given a headphone measurement and a target curve:

1. **Normalize** — Calculate the offset between measurement and target over a 250–2500 Hz window (the perceptually critical midrange). Compute the initial error curve across the full spectrum.

2. **Find the worst deviation** — Scan 20 Hz to 16 kHz. Apply 3-point smoothing to the error. Weight sub-50 Hz deviations by 1.2x. Find the frequency with the largest absolute error.

3. **Calculate the correction filter** — Invert the deviation as gain. Clamp to ±12 dB (±8 dB above 8 kHz to avoid harsh treble boost). Estimate bandwidth by scanning outward from the peak until error drops below half the deviation. Convert bandwidth in octaves to Q factor: `Q = √(2^bw) / (2^bw - 1)`, clamped between 0.6 and 6.0.

4. **Update the error curve** — Compute the biquad frequency response of the new filter at every measurement point and subtract it from the remaining error. This accounts for the phase-coherent interaction of all previous filters.

5. **Repeat** for up to 10 bands. Terminate early if the largest remaining deviation is under 0.05 dB.

6. **Sort** the resulting bands by frequency.

### Biquad Math

Filter responses are calculated using the direct complex form of the transfer function at 64-bit (double) precision.

For a peaking filter at frequency `f0` with gain `G` and quality `Q`:

```
w0 = 2π × f0 / fs
A  = 10^(G/40)
α  = sin(w0) / (2Q)

b0 = 1 + α×A       a0 = 1 + α/A
b1 = -2×cos(w0)    a1 = -2×cos(w0)
b2 = 1 - α×A       a2 = 1 - α/A
```

The magnitude response at frequency `f`:

```
φ = 2π × f / fs

       |b0 + b1·e^(-jφ) + b2·e^(-j2φ)|
|H| =  ─────────────────────────────────
       |a0 + a1·e^(-jφ) + a2·e^(-j2φ)|

dB = 10 × log₁₀(|H|²)
```

Low shelf and high shelf coefficients follow the standard cookbook forms with `√A` coupling terms. Stability guards catch denominator magnitudes below 1e-15, NaN, and Inf.

### Target Curves

Ten built-in targets, each defined as frequency/gain point pairs and linearly interpolated between points:

| Target | Points | Use |
|--------|--------|-----|
| Harman Over-Ear 2018 | 384 | Over-ear headphones |
| Harman In-Ear 2019 | 32 | IEMs |
| Diffuse Field | 27 | Flat at the eardrum |
| Knowles | 27 | BA driver compensation |
| Moondrop VDSF | 27 | Virtual diffuse sound field |
| Hi-Fi Endgame 2026 | 27 | Modern preference target |
| PEQdB Ultra | 27 | Parametric reference |
| SEAP | 26 | Spectral error attenuation |
| SEAP Bass | 26 | SEAP with bass shelf |
| Flat | 10 | Bypass (constant 75 dB SPL) |

Target data is embedded as raw strings, lazy-parsed on first access.

### Headphone Profiles

Over 4,000 headphone measurements load from a remote repository. The parser handles CSV and TXT with automatic delimiter detection (comma, semicolon, tab, whitespace), header row detection, and European decimal format conversion. Users can also upload their own measurement files.

### Audio Integration

The EQ processor wraps Android's system `Equalizer` AudioEffect, bound to the ExoPlayer audio session ID. Parametric bands are mapped to the device's fixed system bands using a Gaussian gain estimation model — for each system band frequency, the contribution of every parametric band is summed based on the distance in octaves from its center frequency, shaped by its Q. Gains are clamped to the device's hardware limits (reported in millibels).

EQ settings are stored as serialized JSON in DataStore and persist across sessions. Changes flow reactively — `combine(eqEnabled, eqBandsJson, eqPreamp)` triggers real-time updates without interrupting playback.

Presets are stored in Room with the full band array serialized as JSON, plus preamp, target reference, and custom/built-in flag.

---

## Tech Stack

| | |
|-|-|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose, Material 3 (BOM 2024.12.01) |
| Audio | Media3 / ExoPlayer 1.5.1 |
| Casting | Media3 Cast, Google Cast Framework 22.0.0 |
| Visualizer | ProjectM (C++17 via JNI) |
| Images | Coil 3.0.4, Palette |
| Database | Room 2.7.1 |
| Preferences | DataStore 1.1.1 |
| Network | Ktor 3.0.3 (OkHttp) |
| DI | Hilt 2.57.1 |
| Serialization | Kotlinx Serialization 1.7.3 |
| Auth | Appwrite 7.0.0, Google Credentials API |
| Encryption | AndroidX Security Crypto (AES-256-GCM) |
| AI | Gemini 2.0 Flash |
| Background | WorkManager 2.10.0 |
| Widgets | Glance 1.1.1 |
| Build | AGP 9.0.0, KSP 2.1.0, CMake 3.22.1 |

Android 8.0+ (API 26). Target SDK 36. NDK 28.2.

## Build

```bash
git clone https://github.com/tryptz/tf.monochrome.android.git
cd tf.monochrome.android
./gradlew assembleRelease
```

JDK 17+, Android SDK 36.
