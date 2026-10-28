# CLAUDE.md – NexMusic

Respond in English.

## What this is
NexMusic: an Android-only music streaming app (package `com.nexmusic.app`) with an Apple Music–style Jetpack Compose UI, built on a YouTube Music core forked from SimpMusic (GPL-3.0).

## Modules
- `nexApp/` – the only app module. Depends on `:data`, `:domain`, `:common`, `:media3`. Started in `NexApplication` with `loadAllModules()` (Koin) from `:data`, plus `di/NexModule.kt`.
- `core/common`, `core/domain` (models, repository interfaces, `MediaPlayerHandler`), `core/data` (repository impls, Room, media handler), `core/service/*` (YouTube Music scraper, lyrics, Spotify, AI, etc.), `core/media/media3` (ExoPlayer service, MediaLibraryService browse tree, Android Auto car app `SimpMusicCarAppService`).
- `core/media/media-jvm` is only referenced by `:data`'s desktop target and is never built into the APK. `cast-empty` / `lastfm-empty` are no-op stubs; Cast, Last.fm and Sentry were removed.
- KMP core modules still declare iOS/JVM targets; only the Android target is used.

## Conventions in nexApp
- One `PlayerViewModel` lives in the activity's store (created outside the NavHost). Screens reach it through `LocalNavigator.current.player`, never their own `koinViewModel()` (that would make a second instance).
- Start playback the core's way: `handler.reset()` → `setQueueData(...)` → `loadMediaItem(track, Config.*_CLICK, index)` (see `PlayerViewModel.start`). Seeking takes a percentage (0–100).
- Colours/type: `ui/theme/Theme.kt` (`LocalNex` palette, `NexType` iOS text styles, `Accent`). Shared pieces in `ui/components/Components.kt`.
- Icons: Material Symbols Rounded as `ImageVector`, one file each in `ui/icon/`, used as `NexIcons.<Name>` with a per-icon import. Fetch with
  `curl -sfL --compressed "https://fonts.gstatic.com/render/v1/Material+Symbols+Rounded/24dp/<symbol>.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,1,0,50"`
  then rename the package and property like the existing files.
- nexApp overrides two core resources by name: `drawable/mono` (notification + car glyph) and `string/notification_channel_name`.
- Voice search ("play X on NexMusic") is handled in `core/.../SimpleMediaSessionCallback.onSetMediaItems` via `requestMetadata.searchQuery`.

## Release
- `./gradlew :nexApp:assembleRelease` gives one APK per CPU (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) plus `universal`, minified and resource-shrunk (R8 rules in `nexApp/proguard-rules.pro`). Splits are on only for release tasks.
- Signing reads `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) at the repo root; both it and the `.jks` are git-ignored. Without it the release falls back to the debug key.
- The `x86` APK has no ffmpeg (ffmpeg-kit ships no 32-bit x86 build), so merging music-video downloads fails there; everything else works.
- After a shrinking problem, check `nexApp/build/outputs/mapping/release/` and add a keep rule rather than turning R8 off.

## Known gaps
- Not built: YouTube sign-in, creating playlists, settings, Radio tab.
- Dynamic Island (`island/`): drawn by an accessibility service (`IslandService`, `TYPE_ACCESSIBILITY_OVERLAY`), the only window type above the status bar that takes touches there. It centres on the display cutout (`cutoutPath` on API 31+). The service being enabled is the Library toggle.
