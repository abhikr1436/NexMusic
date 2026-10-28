# NexMusic

An Android music streaming app with an Apple Music–style interface, playing from YouTube Music.
Works in Android Auto: browse, search and play from the car screen, or by voice ("Hey Google, play … on NexMusic").

## Run

Open the folder in Android Studio, let Gradle sync, pick the **NexMusic** run configuration and press Run.
From a terminal: `./gradlew :nexApp:installDebug`.

To see it in Android Auto with a sideloaded build, enable *Unknown sources* in Android Auto's developer settings.

## Layout

- `nexApp/` – the app: Compose UI (`ui/`), player screens (`player/`), launcher icon and manifest.
- `core/` – streaming, library database and playback service (YouTube Music client, Media3 player, Android Auto car app).
- `cast-empty/`, `lastfm-empty/` – no-op modules the core links against.

## License

GPL-3.0 (see `LICENSE`). The core is derived from [SimpMusic](https://github.com/maxrave-dev/SimpMusic).
