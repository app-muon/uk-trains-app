# UK Transport App

A real-time UK transport departures app for Android. Create custom departure boards that combine National Rail stations, London buses, Tube/DLR stations, London Overground, and the Elizabeth line.

## Features

- **Custom Departure Boards** - Group multiple stations and stops into named boards for quick access
- **Board Management** - Edit boards from the home list or departure screen, and drag boards to reorder them
- **Train Departures** - Real-time scheduled/estimated times, platform numbers, and cancellation status from National Rail
- **TfL Rail Departures** - Live London Overground and Elizabeth line departures from TfL, shown as train entries
- **Tube and DLR Departures** - Live Tube and DLR arrivals from TfL, with optional line and direction filters
- **Bus Arrivals** - Live London bus arrival predictions from TfL, with route numbers and countdown times
- **Filtering** - Filter National Rail departures by destination, buses by route, and TfL rail/Tube entries by line and direction
- **Service Details** - Tap a train to see the full journey with previous/upcoming calling points; tap a bus to see its upcoming stops
- **Station Alerts** - Disruption messages from National Rail shown under each station header
- **Load More** - Paginate to see later National Rail departures beyond the initial results
- **Offline Cache** - Cached departures shown when offline, with stale data warnings

## Main Screens

1. **My Boards** - List of saved departure boards. Tap a board to open it, tap the edit icon to change it, or drag the handle to reorder boards.
2. **Create/Edit Board** - Add stations or stops, set filters, delete a board, and drag stations to reorder them within the board.
3. **Departures** - Live departures grouped by station/stop, with refresh, edit, and home actions in the top bar.
4. **Train Service Detail** - Full calling point list for a selected train.
5. **Bus Detail** - Upcoming stops for a selected bus.

## Getting Started

### Prerequisites

- Android Studio
- Android SDK 36 (compileSdk)
- Min SDK 24 (Android 7.0)
- A National Rail Darwin API key (required for National Rail departures)
- A TfL API key (optional, for higher TfL API rate limits)

### Obtaining API Keys

**Darwin (National Rail):**

1. Go to [National Rail Data Portal](https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/)
2. Register for a free account
3. You will receive an email with your API token

**TfL (buses, Tube/DLR, Overground, Elizabeth line):**

1. Go to [TfL API Portal](https://api-portal.tfl.gov.uk/)
2. Register and create an app to get an API key
3. The TfL API works without a key at reduced rate limits, so this is optional for light use

### Configuring API Keys

Add your keys to the project's `local.properties` file, in the project root:

```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
DARWIN_API_KEY=your-darwin-key-here
TFL_API_KEY=your-tfl-key-here
```

Both keys are read at build time and injected into `BuildConfig`. The `local.properties` file is excluded from version control via `.gitignore`.

- If `DARWIN_API_KEY` is missing, National Rail departures will fail.
- If `TFL_API_KEY` is missing or blank, TfL features still work at TfL's unauthenticated rate limit.

### Building and Running

1. Clone the repository.
2. Add your API keys to `local.properties` as shown above.
3. Open the project in Android Studio.
4. Sync Gradle.
5. Run on a device or emulator (API 24+).

## Architecture

The app follows MVVM with Hilt dependency injection:

```text
app/src/main/java/com/example/transport_app/
|-- data/
|   |-- db/           # Room database, DAOs, migrations
|   |-- model/        # Data classes
|   |-- network/      # DarwinSoapClient (SOAP/XML), TflApiClient (REST/JSON)
|   `-- repository/   # Data repositories
|-- di/               # Hilt AppModule
|-- ui/
|   |-- departures/   # Departures, service detail, and bus detail screens
|   |-- groups/       # Board list and create/edit board screens
|   |-- navigation/   # NavHost setup
|   `-- theme/        # Material3 theming
|-- MainActivity.kt
`-- UkTrainsApplication.kt
```

### Key Technical Details

- **Darwin API**: SOAP 1.1 XML over HTTPS with the OpenLDBWS endpoint (`ldb12.asmx`)
- **TfL API**: REST/JSON for stop search, arrivals, vehicle tracking, route discovery, Tube/DLR, Overground, and Elizabeth line data
- **National Rail Filtering**: Uses `GetDepBoardWithDetails` with client-side verification of subsequent calling points
- **TfL Stop Resolution**: TfL hub/group stop IDs are resolved to arrival-capable child stop IDs before saving
- **Database**: Room with migrations (v1-v5), caching, station types, data source metadata, line filters, and direction filters
- **Station Data**: Bundled CSV asset (`stations.csv`) for offline National Rail station name/CRS lookup
- **Rate Limiting**: Darwin requests chunked (5 concurrent max) with 1-second gaps

## Tech Stack

| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 2.1.21 |
| UI | Jetpack Compose + Material3 | BOM 2024.09.00 |
| Navigation | Navigation Compose | 2.8.0 |
| DI | Hilt | 2.59.2 |
| Database | Room | 2.8.4 |
| HTTP | OkHttp | 4.12.0 |
| Async | Kotlin Coroutines | 1.8.1 |
| Build | AGP | 9.0.1 |

## License

This project is licensed under the MIT License.

This project uses the National Rail Darwin Data Feeds and the TfL Unified API. Usage is subject to the [National Rail Data Portal Terms](https://www.nationalrail.co.uk/developers/) and [TfL Terms](https://tfl.gov.uk/corporate/terms-and-conditions/transport-data-service).
