# CLAUDE.md for Monochrome Android

Monochrome is a music player/streaming Android app (package `tf.monochrome.android`) that unifies TIDAL HiFi streaming, local file playback, and encrypted collection manifests into a single library experience. Built with Jetpack Compose, Hilt, Media3, and Room.

## Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore.properties)
./gradlew assembleRelease

# Install to connected device
./gradlew installDebug

# Run lint
./gradlew lint

# Run unit tests (no tests exist yet)
./gradlew test
```

## Environment Setup

- Copy `keystore.properties.example` → `keystore.properties` and fill signing keys for release builds.
- Add to `local.properties`:
  ```
  TIDAL_CLIENT_ID=...
  TIDAL_CLIENT_SECRET=...
  ```
  Without these, TIDAL streaming is disabled (builds fine, just no credentials).
- **NDK required**: Version `28.2.13676358` — install via SDK Manager. Used for ProjectM visualizer native code.

## Tech Stack

| Component | Version / Library |
|-----------|-------------------|
| AGP | 9.0.0 |
| Kotlin | 2.1.0 |
| Compose BOM | 2024.12.01 |
| Media3 | 1.5.1 |
| Hilt | 2.57.1 |
| Room | 2.7.1 |
| Ktor | 3.0.3 (OkHttp engine) |
| Coil | 3.0.4 |
| KSP | 2.1.0-1.0.29 |
| Min SDK | 26 |
| Target/Compile SDK | 36 |
| JVM Target | 17 |
| CMake | 3.22.1, C++17 |
| ABI Filters | arm64-v8a, armeabi-v7a, x86_64 |

Version catalog: `gradle/libs.versions.toml`

## Architecture

Single-module app (`app/`). Package root: `tf.monochrome.android`.

### Data Sources

Three content sources unified into a single library:
1. **HiFi/TIDAL** — `data/api/HiFiApiClient.kt` — streaming via TIDAL OAuth
2. **Local Files** — `data/local/` — MediaStore scanner (`scanner/`) + TagReader (`tags/`) + file watcher (`watcher/`)
3. **Collections** — `data/collections/` — AES-256-GCM encrypted manifests with dedicated parser, playback, crypto, and Room DB

### Package Map

```
tf.monochrome.android/
├── audio/eq/          # AutoEQ headphone correction engine (AutoEqEngine, EqProcessor, FrequencyTargets)
├── auto/              # Android Auto — MonochromeMediaBrowserService
├── data/
│   ├── ai/            # Gemini AI integration (GeminiClient, AudioSnippetFetcher)
│   ├── api/           # TIDAL HiFi API client + models + InstanceManager
│   │   └── model/     # API response models (ApiModels.kt)
│   ├── auth/          # Google Sign-In (AuthRepository, GoogleAuthManager)
│   ├── collections/   # Encrypted collection manifests
│   │   ├── crypto/    # AES-256-GCM decryption (AesGcmDecryptor)
│   │   ├── db/        # Room DB for collections
│   │   ├── di/        # Hilt module for collections
│   │   ├── model/     # Collection data models
│   │   ├── parser/    # Manifest parsing
│   │   ├── playback/  # Collection playback resolution
│   │   └── repository/
│   ├── db/            # Main Room database (MusicDatabase)
│   │   ├── dao/       # Data access objects
│   │   └── entity/    # Database entities
│   ├── downloads/     # Offline downloads (DownloadManager, DownloadWorker)
│   ├── import_/       # Playlist import (CSV parsing)
│   ├── local/         # Local file playback
│   │   ├── db/        # Local media Room DB
│   │   ├── di/        # Hilt module for local media
│   │   ├── repository/
│   │   ├── scanner/   # MediaStore scanner
│   │   ├── tags/      # Audio tag reader
│   │   └── watcher/   # File system watcher
│   ├── preferences/   # DataStore-backed settings (PreferencesManager)
│   ├── repository/    # Top-level repositories (LibraryRepository, MusicRepository, EqRepository, HeadphoneRepository)
│   ├── scrobbling/    # Last.fm-style scrobbling (ScrobblingService)
│   └── sync/          # Cloud backup (SyncManager, BackupManager, PocketBaseClient)
├── di/                # Hilt modules (AppModule, ApiModule, DatabaseModule, NetworkModule)
├── domain/
│   ├── model/         # Domain models (Models.kt: UnifiedTrack, UnifiedAlbum, etc.; AiFilter.kt)
│   └── usecase/       # Cross-source matching, playback resolution, search, scan, import
├── player/            # Media3 playback (PlaybackService, StreamResolver, QueueManager, ReplayGainProcessor)
├── ui/
│   ├── components/    # Shared composables (MiniPlayer, TrackItem, AlbumItem, CoverImage, etc.)
│   ├── detail/        # Album/Artist detail screens (both streaming and local variants)
│   ├── eq/            # Equalizer UI + headphone selection + measurement upload
│   ├── home/          # Home screen
│   ├── library/       # Library tabs (local, collections, downloads, folders, playlists)
│   ├── main/          # MainActivity — entry point
│   ├── navigation/    # MonochromeNavHost — Compose Navigation graph
│   ├── player/        # Now Playing, lyrics, queue, visualizer
│   ├── profile/       # User profile
│   ├── search/        # Unified search
│   ├── settings/      # App settings
│   └── theme/         # Material3 theming (Color, Type, Dimensions, Theme)
├── util/              # Utilities (RomajiConverter — Japanese text romanization)
├── visualizer/        # ProjectM visualizer (renderer, native bridge, preset catalog, asset installer)
└── widget/            # Glance "Now Playing" home screen widget
```

### Native Code (C++)

```
app/src/main/cpp/
├── CMakeLists.txt
├── projectm_bridge.cpp/.h   # JNI bridge to native ProjectM library
├── projectm_jni.cpp         # JNI entry points
└── audio_ring_buffer.cpp/.h # Lock-free audio ring buffer for visualizer tap

third_party/projectm/        # Native ProjectM library, built as CMake dependency
```

## Key Files

| File | Purpose |
|------|---------|
| `MonochromeApp.kt` | Hilt `@HiltAndroidApp` application class |
| `ui/main/MainActivity.kt` | Entry point — theme, custom font, max refresh rate, `FrequencyTargets.init()` |
| `ui/navigation/MonochromeNavHost.kt` | Compose Navigation graph — all screen routes |
| `player/PlaybackService.kt` | Media3 `MediaSessionService` for background playback |
| `player/StreamResolver.kt` | Resolves playback URL per source type (TIDAL, local, collection) |
| `player/QueueManager.kt` | Playback queue state management |
| `player/ReplayGainProcessor.kt` | Unified ReplayGain normalization across all sources |
| `data/preferences/PreferencesManager.kt` | DataStore-backed settings (theme, quality, ReplayGain, scrobbling) |
| `data/api/HiFiApiClient.kt` | TIDAL streaming API client |
| `data/api/InstanceManager.kt` | API instance lifecycle management |
| `data/collections/crypto/AesGcmDecryptor.kt` | AES-256-GCM decryption for encrypted collections |
| `data/auth/GoogleAuthManager.kt` | Google Credential Manager sign-in |
| `data/ai/GeminiClient.kt` | Gemini AI integration for audio analysis |
| `data/downloads/DownloadWorker.kt` | WorkManager-based offline download |
| `data/sync/SyncManager.kt` | Cloud sync orchestration |
| `data/sync/PocketBaseClient.kt` | PocketBase/Appwrite backend client |
| `audio/eq/AutoEqEngine.kt` | AutoEQ headphone correction engine |
| `audio/eq/FrequencyTargets.kt` | Headphone measurement presets (must init in `MainActivity.onCreate()`) |
| `visualizer/ProjectMRendererView.kt` | OpenGL surface for ProjectM visualizer |
| `visualizer/ProjectMAudioBus.kt` | Audio tap feeding PCM data to visualizer |
| `auto/MonochromeMediaBrowserService.kt` | Android Auto media browsing |
| `widget/NowPlayingWidgetReceiver.kt` | Glance app widget |

## DI Modules

All Hilt modules in `di/`: `AppModule`, `ApiModule`, `DatabaseModule`, `NetworkModule`.
Feature-specific modules co-located: `data/local/di/`, `data/collections/di/`.

WorkManager uses custom Hilt initialization (see `InitializationProvider` in manifest).

## Manifest & Features

- **Deep linking**: `https://monochrome.tf` URLs handled by `MainActivity`
- **Appwrite OAuth**: Callback via custom scheme `appwrite-callback-auth-for-monochrome`
- **Foreground service**: `mediaPlayback` type for `PlaybackService`
- **Android Auto**: `MonochromeMediaBrowserService` + `automotive_app_desc.xml`
- **Glance widget**: Now Playing widget with `BIND_APPWIDGET` permission
- **Permissions**: Internet, foreground service, wake lock, notifications, storage/media audio

## Gotchas

- **NDK required**: CMake + NDK for ProjectM visualizer. NDK `28.2.13676358` must be installed.
- **`third_party/projectm`**: Native ProjectM library lives here, built as a CMake dependency.
- **Room migrations**: DB uses `fallbackToDestructiveMigration` — schema changes wipe local data on upgrade.
- **Custom fonts**: `MilkyNice.ttf` and `wwDigital.ttf` are font files; `res/font/` holds bundled fonts.
- **`FrequencyTargets.init()`**: Must be called in `MainActivity.onCreate()` before EQ is usable — loads headphone measurement presets from `assets/presets/`.
- **Appwrite sync**: `SyncManager.kt` + `BackupManager.kt` + `PocketBaseClient.kt` handle cloud backup; Appwrite credentials needed in settings, not hardcoded.
- **No test suite**: No unit or instrumentation tests exist currently. The `test` and `androidTest` source sets are empty.
- **Release builds**: R8 minification + resource shrinking enabled. ProGuard rules in `proguard-rules.pro`.
- **Single module**: Entire app is in the `:app` module — no multi-module setup.
- **`data/import_/`**: Package uses trailing underscore to avoid Kotlin `import` keyword conflict.
- **ProjectM assets**: Visualizer presets in `assets/projectm/presets/` organized by category (Dancer, Drawing, Fractal). Installed at runtime by `ProjectMAssetInstaller`.

## Conventions

- **UI**: Jetpack Compose with Material 3. Each screen has a `*Screen.kt` composable + `*ViewModel.kt`.
- **DI**: Hilt throughout. `@HiltViewModel` for ViewModels, `@AndroidEntryPoint` for Activities/Services.
- **Networking**: Ktor with OkHttp engine + kotlinx.serialization for JSON.
- **Images**: Coil 3 for async image loading in Compose.
- **Navigation**: Compose Navigation via `MonochromeNavHost`.
- **Persistence**: Room for structured data, DataStore for preferences.
- **Background work**: WorkManager for downloads, Media3 `MediaSessionService` for playback.
- **Serialization**: kotlinx.serialization (not Gson/Moshi).
- **Coroutines**: Used throughout for async operations (`kotlinx-coroutines-android`).
