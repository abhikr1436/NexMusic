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
- Glass: chrome uses `Modifier.liquidGlass` (Kyant backdrop) over the NavHost's `layerBackdrop`, which must wrap the background. Dialogs and menus use `GlassDialog` / `GlassMenu` / `GlassButton` / `GlassTextField` (`ui/components/Glass.kt`), drawn in-window by `GlassOverlayHost`; never `AlertDialog` / `DropdownMenu`, whose separate windows can't be glass.
- Taste: first launch runs `taste/OnboardingScreen` (languages, artists, theme), saved in `TasteStore` (SharedPreferences). Home's Your Artists / Popular in rows, New's New From Your Artists and the app theme read it; Library › Your Taste reopens it.
- Account sync (optional NexMusic account, Library › Account): `sync/`. `AccountSync` signs in with Supabase Auth (email + password; project `apbclqpgrzxquhbkdsuc`, publishable key in `Supabase.kt`) and keeps the library as one JSON row in `public.libraries` (one row per user, RLS: own row only), synced on sign-in, app start/stop and Sync Now. `merge()` in `Snapshot.kt` is a three-way merge against the last-synced copy (`filesDir/sync_base.json`), so deletions travel; it has unit tests. Anything new the user should keep across devices goes in `Snapshot` + `SnapshotJson` + `LocalLibrary.read/apply`. Supabase "Confirm email" is on, so a new account must click the emailed link before signing in.
- Appearance (Library › Appearance): `Accent` is a getter over Compose state (`setAccentColor`), so it recolours live; page backgrounds are `Backdrop` brushes provided as `LocalBackdrop` and painted only at the app root / NavHost. Mode, accent and background live in `TasteStore` and sync with the account.
- Software Update (Library): `update/AppUpdater` reads GitHub `releases/latest` of `abhikr1436/NexMusic`, picks `NexMusic-<ver>-<abi>.apk` (else `-universal.apk`) and installs via PackageInstaller. Release assets must keep that naming and the release signing key, and the tag must be `v<versionName>`.
- Imported music (Downloaded › Import Music Files): `library/LocalImport` copies the file into the download cache under a `local-…` id and adds a song row marked downloaded; the player plays any fully cached id offline. Those ids are kept out of account sync. The core's extractor list includes MP3/WAV/OGG/ADTS for them, after the YouTube containers.
- Playlist positions: a DB trigger shifts later rows when a `pair_song_local_playlist` row is deleted, so never update a position with an insert-REPLACE; use `LocalPlaylistRepository.setTrackOrder`.
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
- Not built: YouTube sign-in, settings, Radio tab. User playlists are local only (`LocalPlaylistRepository`), not synced to YouTube.
- Many core reads are one-shot, not Room Flows. Saved albums/playlists/artists and local playlists: writes must call `LibraryChanges.bump()` (ViewModels.kt). Liked/downloaded songs: wrap the read in `onSongChange { }` so it re-runs on song-table writes. Otherwise lists go stale.
