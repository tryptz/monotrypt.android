# CLAUDE.md - MonoTrypT Android

## Commit Authorship (MANDATORY)

NEVER commit as `Claude <noreply@anthropic.com>`. NEVER append a
`https://claude.ai/code/...` footer or any `Co-Authored-By: Claude` trailer.

Before the first commit in any session, look up the repo owner's identity
from history (do NOT hardcode it here, do NOT print it back to the user):

```bash
git log -1 --pretty='%an <%ae>' -- .
```

Use that name/email via per-command identity (do not modify global git config):

```bash
git -c user.name="<name>" -c user.email="<email>" commit -m "..."
```

If a commit was already created with the wrong author, amend with
`--reset-author` and force-push the branch.

## Project Overview

MonoTrypT is a premium Android music player with TIDAL HiFi streaming, local library support, encrypted collections, a 33-processor native DSP engine, 10-band AutoEQ, and a ProjectM OpenGL visualizer. Single-module Kotlin/Compose app with C++ native audio processing via JNI.

## Build & Run

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

- **Min SDK:** 26 (Android 8.0) | **Target/Compile SDK:** 36
- **JVM Target:** 17 (OpenJDK 17 required)
- **NDK:** 28.2.13676358 | **CMake:** 3.22.1 | **C++ Standard:** C++17
- **ABIs:** arm64-v8a, armeabi-v7a, x86_64
- **Gradle:** 9.1.0 with version catalog (`gradle/libs.versions.toml`)

## Architecture

**Single module** (`app/`) with clean layer separation:

```
tf.monochrome.android/
├── audio/dsp/          # Native C++ DSP engine bridge (33 processors, 4+1 buses)
├── audio/eq/           # 10-band parametric AutoEQ (4000+ headphone profiles)
├── data/               # Repositories, Room DB (v4, 22 tables), API clients, downloads
├── di/                 # Hilt modules: App, API, Database, Network, DSP
├── domain/model/       # Track, Album, Artist, UnifiedTrack, EQ models
├── domain/usecase/     # Cross-source matching, playback resolution, media scanning
├── player/             # Media3 PlaybackService, QueueManager, StreamResolver, ReplayGain
├── ui/                 # Jetpack Compose screens (Material 3)
├── visualizer/         # ProjectM OpenGL engine + JNI audio tap
└── widget/             # Glance Now Playing widget
```

**Audio pipeline:** ExoPlayer → ReplayGainProcessor → EqProcessor → MixBusProcessor → ProjectM Tap → AudioSink

**Three playback sources** via `UnifiedTrack` / `PlaybackSource`:
- `HiFiApi` — TIDAL streaming (requires network)
- `CollectionDirect` — AES-256-GCM encrypted local collections
- `LocalFile` — Device media files via MediaStore

## Key Tech Stack

| Layer | Tech |
|-------|------|
| UI | Jetpack Compose + Material 3, Coil 3, Haze (glassmorphism) |
| DI | Hilt 2.57.1 |
| Database | Room 2.7.1 (22 tables, schema v4) |
| Network | Ktor 3.0.3 (OkHttp engine) |
| Playback | Media3 / ExoPlayer 1.5.1 |
| Async | Kotlin Coroutines 1.9.0 + Flow |
| Serialization | kotlinx.serialization 1.7.3 |
| Background | WorkManager 2.10.0 (downloads) |
| Native | C++ DSP engine via JNI, ProjectM v4.1.6 (git submodule) |
| Auth | Google Credential Manager + Appwrite OAuth2 |
| Sync | Appwrite 7.0.0 / PocketBase |

## Code Conventions

- **Reactive data flow:** Room DAOs return `Flow<T>`, ViewModels expose `StateFlow<T>`
- **Compose UI:** All screens are `@Composable` functions, not Activities/Fragments
- **ViewModels:** `@HiltViewModel` with constructor injection
- **Navigation:** Single-Activity with Compose Navigation (`MonochromeNavHost`)
- **DSP processors:** C++ headers in `app/src/main/cpp/dsp/snapins/`, compiled with `-O3 -ffast-math`
- **No tests:** Project has no test suite currently
- **Theme:** 16 color themes defined in `ui/theme/Color.kt`, dimensions in `ui/theme/Dimensions.kt`

## Important Patterns

- `PlayerViewModel` is the central playback controller — shared across screens
- `DownloadManager` uses WorkManager with unique work names `download_{trackId}`
- `DownloadWorker` reports progress via `setProgress()` for per-track UI indicators
- `TrackItem` is the reusable track row component — supports download state, like state, context menus
- `LibraryRepository` handles favorites, playlists, history (Room-backed)
- `MusicRepository` handles API calls to TIDAL HiFi backend
- Native DSP state is managed by `DspEngineManager` (Kotlin) ↔ `dsp_engine.cpp` (C++ via JNI)

## File Locations

| What | Where |
|------|-------|
| App entry | `ui/main/MainActivity.kt` |
| Navigation | `ui/navigation/MonochromeNavHost.kt` |
| Playback | `player/PlaybackService.kt`, `player/QueueManager.kt` |
| API client | `data/api/HiFiApiClient.kt` |
| Database | `data/db/MusicDatabase.kt` |
| Room entities | `data/db/entity/Entities.kt` |
| Domain models | `domain/model/Models.kt` |
| DI setup | `di/AppModule.kt`, `di/DatabaseModule.kt` |
| DSP C++ engine | `app/src/main/cpp/dsp/dsp_engine.cpp` |
| DSP processors | `app/src/main/cpp/dsp/snapins/*.h` (33 files) |
| Visualizer JNI | `app/src/main/cpp/projectm_bridge.cpp` |
| Version catalog | `gradle/libs.versions.toml` |
