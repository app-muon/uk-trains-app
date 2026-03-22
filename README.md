# UK Transport App

A real-time UK transport departures app for Android. View live train departures via the National Rail Darwin API and London bus arrivals via the TfL API. Create custom departure boards combining train stations and bus stops, with optional destination/route filtering and live service details.

## Features

- **Custom Departure Boards** — Group multiple train stations and bus stops into named boards for quick access
- **Train Departures** — Real-time scheduled/estimated times, platform numbers, and cancellation status from National Rail
- **Bus Arrivals** — Live London bus arrival predictions from TfL, with route numbers and countdown times
- **Destination & Route Filtering** — Filter train departures by destination station, or bus arrivals by route number (with auto-suggested routes from TfL)
- **Service Details** — Tap a train to see the full journey with previous/upcoming calling points; tap a bus to see its upcoming stops
- **Station Alerts** — Disruption messages from National Rail shown under each station header
- **Load More** — Paginate to see later train departures beyond the initial results
- **Offline Cache** — Cached departures shown when offline, with stale data warnings
- **Colour-coded Headers** — Train sections shown in blue, bus sections in red, each with a transport icon

## Screenshots

The app has five main screens:

1. **My Boards** — List of your saved departure boards
2. **Create/Edit Board** — Add train stations or bus stops, set destination/route filters
3. **Departures** — Live departures grouped by station/stop, with status colours (green = on time, orange = delayed, red = cancelled)
4. **Train Service Detail** — Full calling point list for a selected train
5. **Bus Detail** — Upcoming stops for a selected bus

## Getting Started

### Prerequisites

- Android Studio (Arctic Fox or later)
- Android SDK 36 (compileSdk)
- Min SDK 24 (Android 7.0)
- A National Rail Darwin API key (free, required for trains)
- A TfL API key (optional, for higher bus API rate limits)

### Obtaining API Keys

**Darwin (trains):**
1. Go to [National Rail Data Portal](https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/)
2. Register for a free account
3. You will receive an email with your API token

**TfL (buses):**
1. Go to [TfL API Portal](https://api-portal.tfl.gov.uk/)
2. Register and create an app to get an API key
3. The TfL API works without a key at reduced rate limits, so this is optional for light use

### Configuring API Keys

Add your keys to the project's `local.properties` file (in the project root, same level as `build.gradle.kts`):

```properties
# local.properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
DARWIN_API_KEY=your-darwin-key-here
TFL_API_KEY=your-tfl-key-here
```

Both keys are read at build time and injected into `BuildConfig`. The `local.properties` file is excluded from version control via `.gitignore`.

- If `DARWIN_API_KEY` is missing, train departures will fail.
- If `TFL_API_KEY` is missing or blank, bus features still work at TfL's unauthenticated rate limit.

### Building and Running

1. Clone the repository
2. Add your API keys to `local.properties` as shown above
3. Open the project in Android Studio
4. Sync Gradle
5. Run on a device or emulator (API 24+)

## Architecture

The app follows **MVVM** with **Hilt** dependency injection:

```
app/src/main/java/com/example/transport_app/
├── data/
│   ├── db/           # Room database, DAOs, migrations
│   ├── model/        # Data classes (Group, StationEntry, Departure, BusStopArrival, etc.)
│   ├── network/      # DarwinSoapClient (SOAP/XML), TflApiClient (REST/JSON)
│   └── repository/   # GroupRepository, DeparturesRepository, StationRepository, BusStopRepository
├── di/               # Hilt AppModule
├── ui/
│   ├── departures/   # DeparturesScreen, ServiceDetailScreen, BusDetailScreen + ViewModels
│   ├── groups/       # GroupsListScreen, CreateEditGroupScreen + ViewModels
│   ├── navigation/   # NavHost setup
│   └── theme/        # Material3 theming
├── MainActivity.kt
└── UkTrainsApplication.kt
```

### Key Technical Details

- **Darwin API**: SOAP 1.1 XML over HTTPS with the OpenLDBWS endpoint (`ldb12.asmx`)
- **TfL API**: REST/JSON for bus stop search, arrivals, vehicle tracking, and route discovery
- **Destination Filtering**: Uses `GetDepBoardWithDetails` with client-side verification of subsequent calling points
- **Bus Stop Groups**: TfL search returns group IDs (490G prefix) which are resolved to child stops for arrivals
- **Database**: Room with migrations (v1→v4). Supports caching, station types, and route filters
- **Station Data**: Bundled JSON asset (`stations.json`) for offline train station name/CRS lookup
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

This project is licensed under the [MIT License](LICENSE).

This project uses the National Rail Darwin Data Feeds and the TfL Unified API. Usage is subject to the [National Rail Data Portal Terms](https://www.nationalrail.co.uk/developers/) and [TfL Terms](https://tfl.gov.uk/corporate/terms-and-conditions/transport-data-service).
