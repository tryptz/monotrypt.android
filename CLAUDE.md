# CLAUDE.md for monochrome-android

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

# Run unit tests
./gradlew test
```

## Environment Setup

Copy `keystore.properties.example` → `keystore.properties` and fill signing keys for release builds.
Add to `local.properties`:
```
TIDAL_CLIENT_ID=...
TIDAL_CLIENT_SECRET=...
```
Without these, TIDAL streaming is disabled (builds fine, just no credentials).

## Architecture

Three content sources unified into a single library:
1. **HiFi/TIDAL** — `data/api/` — streaming via TIDAL OAuth
2. **Local Files** — `data/local/` — MediaStore scanner + TagReader
3. **Collections** — `data/collections/` — AES-256-GCM encrypted manifests

Domain models in `domain/model/`: `UnifiedTrack`, `UnifiedAlbum`, `UnifiedArtist`, `PlaybackSource` sealed class.
Use cases in `domain/usecase/`: cross-source matching, playback resolution, search, import.

## Key Files

| File | Purpose |
|------|---------|
| `player/PlaybackService.kt` | Media3 background playback service |
| `player/StreamResolver.kt` | Resolves playback URL per source type |
| `player/ReplayGainProcessor.kt` | Unified ReplayGain across all sources |
| `data/preferences/PreferencesManager.kt` | DataStore-backed settings (theme, quality, ReplayGain, scrobbling) |
| `ui/main/MainActivity.kt` | Entry point — loads theme, custom font, forces max refresh rate |
| `audio/eq/AutoEqEngine.kt` | AutoEQ headphone correction engine |
| `data/collections/crypto/AesGcmDecryptor.kt` | AES-256-GCM decryption for encrypted collections |
| `visualizer/ProjectMAudioBus.kt` | ProjectM visualizer audio tap |
| `cpp/projectm_bridge.cpp` | JNI bridge to native ProjectM library |

## DI Modules

All Hilt modules are in `di/`: `AppModule`, `ApiModule`, `DatabaseModule`, `NetworkModule`.
Local media and collection modules are co-located with their feature: `data/local/di/`, `data/collections/di/`.

## Gotchas

- **NDK required**: Project uses CMake + NDK for ProjectM visualizer. NDK version `28.2.13676358` must be installed via SDK Manager.
- **`third_party/projectm`**: Native ProjectM library lives here, built as a CMake dependency.
- **Room migrations**: DB uses `fallbackToDestructiveMigration` — schema changes wipe local data on upgrade.
- **Custom fonts**: `MilkyNice.ttf` and `wwDigital.ttf` are root-level font files for the custom font feature; `assets/fonts/` holds bundled fonts.
- **FrequencyTargets.init()**: Must be called in `MainActivity.onCreate()` before EQ is usable — loads headphone measurement presets from `assets/presets/`.
- **Appwrite sync**: `data/sync/SyncManager.kt` + `PocketBaseClient.kt` handle cloud backup; Appwrite credentials needed in settings, not hardcoded.
- **Tidal API (new)**: `data/api/tidal/` is a newer, separate TIDAL client alongside the legacy `HiFiApiClient`; both coexist during migration.
