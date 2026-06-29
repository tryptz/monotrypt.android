# CLAUDE.md

Guidance for AI agents (Claude Code) working in this repository. For the full architecture + audit, see [`codebase.md`](./codebase.md).

## Project

Tryptify is a deep, audiophile Android music player + visualizer: it streams from self-hosted TIDAL-/Qobuz-style "HiFi" backends, indexes on-device audio, and renders a native projectM (MilkDrop) GL visualizer, with a parametric/AutoEQ equalizer, a node-based DSP mixer, native USB-Audio-Class bit-perfect output, encrypted "collections", cloud sync, and Android Auto / car mode.

- **Brand "Tryptify"; internal name "Monochrome".** Kotlin namespace `tf.monochrome.android`; **applicationId `tf.monotrypt.android`** (intentionally different — see Gotchas). Root Gradle project name is `Monochrome`.
- Version 1.6.2 (versionCode 162). minSdk 26, compile/targetSdk 36, Java/JVM 17.
- ~57k lines of Kotlin (259 files), single module, plus three native C++ engines over JNI. **No tests.**

## Build & run

```bash
git submodule update --init --recursive   # MANDATORY first — native build + visualizer break without it
./gradlew assembleDebug                    # debug APK
./gradlew installDebug                     # build + install
./gradlew assembleRelease                  # release APK (unsigned unless keystore.properties exists)
./gradlew lint                             # static analysis — primary verification (no tests exist)
```

- **Native build:** CMake 3.22.1 + NDK 29.0.14206865 builds `libmonochrome_visualizer.so`, `libmonochrome_dsp.so`, `libmonochrome_usb.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`. Source in `app/src/main/cpp/`.
- **Signing:** debug uses the **committed** `app/monotrypt-debug.keystore` (password `monotrypt`) on purpose — same signature everywhere so installs upgrade in place. Release signing is **opt-in** via a `keystore.properties` at repo root (template: `keystore.properties.example`); absent it, release builds are unsigned and every other task still works.
- **arm64 proot hosts:** the build needs a static `aapt2` (lzhiyong) override in `~/.gradle/gradle.properties`.
- Set `sdk.dir` in `local.properties`.

## Architecture

Single `:app` module, clean `data` → `domain` → `ui` layering, **MVVM** (`@HiltViewModel` + `StateFlow`), **Hilt** DI, **Compose + Material3** UI. Audio runs through Media3 ExoPlayer → a shared singleton `AudioProcessor` chain (MixBus DSP → AutoEQ → Parametric EQ → ReplayGain → spectrum tap → projectM tee) → either `DefaultAudioSink` (system mixer) or `LibusbAudioSink` (USB DAC, "bypass" path). Three native libs are reached via `ProjectMNativeBridge`, `DspNativeLoader` (`MixBusProcessor`, Oxford `*Native`), and `UsbNativeLoader` (`LibusbUacDriver`).

## Subsystem map

| Subsystem | Entry point | What it does |
|---|---|---|
| app shell / DI / nav | `MonochromeApp.kt`, `ui/main/MainActivity.kt`, `ui/navigation/MonochromeNavHost.kt` | Hilt root, perf bootstrap, splash, hybrid pager+NavHost |
| playback engine | `player/PlaybackService.kt`, `player/StreamResolver.kt`, `player/QueueManager.kt` | Media3 service, stream resolution, single-item player + singleton queue |
| player UI | `ui/player/PlayerViewModel.kt`, `ui/player/MainPlayerRoute.kt` | now-playing hero/lyrics/visualizer; car mode |
| HiFi API / data | `data/api/HiFiApiClient.kt`, `data/repository/MusicRepository.kt`, `data/api/QobuzIdRegistry.kt` | two streaming backends (TIDAL + Qobuz) via Ktor |
| local library | `data/local/scanner/MediaScanner.kt`, `data/local/tags/TagReader.kt`, `ui/library/` | MediaStore scan → Room → library UI |
| EQ | `ui/eq/EqViewModel.kt` (AutoEQ), `ui/eq/ParametricEqViewModel.kt`, `ui/oxford/` | two independent EQ surfaces + Oxford native effects |
| DSP (native) | `audio/dsp/MixBusProcessor.kt`, `audio/dsp/DspEngineManager.kt` → `libmonochrome_dsp.so` | SIMD mix-bus/plugin engine, JSON state round-trip |
| mixer | `ui/mixer/MixerViewModel.kt`, `ui/mixer/canvas/` | node/FL-style UI over the native mix bus |
| visualizer | `visualizer/ProjectMEngineRepository.kt`, `ui/player/VisualizerComponent.kt` → `libmonochrome_visualizer.so` | projectM GL render, PCM ring buffer, presets |
| USB audio | `audio/usb/LibusbAudioSink.kt`, `audio/usb/LibusbUacDriver.kt` → `libmonochrome_usb.so` | UAC isochronous bit-perfect output |
| auth / sync / AI | `data/auth/SupabaseAuthManager.kt`, `data/sync/SyncManager.kt`, `data/ai/GeminiClient.kt` | Supabase auth, **Supabase + PocketBase** sync, Gemini AI |
| downloads / collections | `data/downloads/DownloadManager.kt`, `data/collections/repository/CollectionRepository.kt` | WorkManager downloads; AES-GCM encrypted collections |
| settings / prefs / stats | `data/preferences/PreferencesManager.kt`, `ui/settings/SettingsScreen.kt`, `ui/stats/` | central DataStore; settings; listening stats |
| platform glue | `ui/theme/`, `devedit/`, `widget/`, `auto/`, `performance/`, `debug/` | theming, in-app layout editor, Glance widget, Android Auto, device tiering |

## Conventions

- Package root `tf.monochrome.android.{ui,data,domain,audio,player,visualizer,di,devedit,performance,debug,widget,auto,share,util}`.
- ViewModels are `@HiltViewModel`, expose immutable UI state as `StateFlow`; route composables collect flows and pass a flattened state object to pure layout composables (see `MainPlayerUiState`). Add a state field + callback rather than collecting flows inside layouts.
- DataStore reads at init use `Flow.first()` (the `stateIn(...).value` pattern races — avoid it).
- Serialization is `kotlinx.serialization` with a lenient global `Json` (`coerceInputValues`, `explicitNulls=false`) — keep model defaults sensible because missing/null fields silently become defaults.
- Native methods are `external fun native*`; JNI names are `Java_tf_monochrome_android_<class>_<method>`. Load via the per-engine `*NativeLoader`.
- Domain ids are source-prefixed: `local_…`/`local_album_…`/`local_artist_…`, `col_album_…`, bare numeric = catalog. Navigation routing branches on these prefixes (`CatalogNav.kt`).
- Every screen/section is wrapped in `DevEditScreen("id")` / `DevEditable("id"){}` for the in-app layout editor — **keep the string ids stable** when refactoring.

## Critical gotchas

- **Submodules are mandatory.** `third_party/projectm`, `third_party/libusb`, and `app/src/main/assets/presets` are git submodules; the native build and visualizer break without them. Never edit `third_party/**` or `assets/presets/**` as app source.
- **Do NOT "fix" the namespace/applicationId mismatch.** `tf.monochrome.android` (namespace) vs `tf.monotrypt.android` (appId) is intentional; the OAuth callback deep link `tf.monotrypt.android://login-callback` and the `${applicationId}.fileprovider` authority depend on the appId.
- **`DEBUG_POSTFIX=""` in CMake is load-bearing** — it stops debug builds emitting `lib…d.so`, so `System.loadLibrary` names match in debug and release. Don't remove it.
- **Two playback drivers must stay in sync.** In-app play goes through `PlayerViewModel` → `MediaController`; `PlaybackService.playQueue()` only runs for auto-advance/notification skips. They resolve streams independently — change both or you'll diverge behavior (already diverged on DASH).
- **Same-process singleton sharing is required.** `QueueManager`/`StreamResolver`/`UnifiedTrackRegistry`/audio processors are `@Singleton`s shared between UI and the service; there is no `android:process`. Don't add one.
- **Room uses `fallbackToDestructiveMigration`** (`di/DatabaseModule.kt`) — any schema bump wipes all local data. Add real migrations before changing entities.
- **Local album/artist ids are NOT stable across scans** (autoGenerate, groupings rebuilt every scan). Don't persist them as long-lived references.
- **Two streaming backends share one client + domain model;** a new Qobuz `Track` must be registered in `QobuzIdRegistry` or playback/lyrics/navigation mis-routes to TIDAL.
- **The debug keystore is committed on purpose;** release signing is opt-in. `usesCleartextTraffic=false` (all `http://` blocked app-wide). No tests exist — verify changes with `./gradlew assembleDebug` + `lint`.

## Where things live

- App entry / DI: `app/src/main/java/tf/monochrome/android/{MonochromeApp.kt,di/}`
- Manifest / permissions / deep links: `app/src/main/AndroidManifest.xml`
- Native C++ + CMake: `app/src/main/cpp/` (`CMakeLists.txt`, `dsp/`, `usb/`, `projectm_*.cpp`)
- Build config / versions: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`
- Audio chain: `player/`, `audio/dsp/`, `audio/eq/`, `audio/usb/`
- Cloud: `data/auth/`, `data/sync/` (Supabase + PocketBase), `data/api/`
- Docs: `docs/projectm-android-integration.md`, `docs/supabase/`

## Known issues

Top risks (full register + `file:line` in [`codebase.md`](./codebase.md) → *Risk register*):

- **Crash:** `LocalMediaDao.deleteTracksNotIn` binds every path as a SQL variable → overflows on large libraries.
- **Broken/misleading features:** Car Mode EQ not wired to audio; DASH hi-res fails on direct in-app play; ReplayGain implemented but unused; file watcher / incremental scan / measurement-upload screen / Last.fm scrobbling are dead or stubbed.
- **Security:** hardcoded Supabase anon JWT in source; `security-crypto` declared but unused (collection keys come from the server manifest); `allowBackup=true` with secrets present.
- **Architecture:** two coexisting cloud backends (Supabase + PocketBase); god files (`SettingsScreen` ~1988, `HiFiApiClient` ~1254); **zero automated tests**.
