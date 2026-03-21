# UK Trains App

A real-time UK train departures app for Android, powered by the National Rail Darwin OpenLDBWS API. Create custom departure boards with multiple stations, optional destination filters, and view live service details including calling points.

## Features

- **Custom Departure Boards** — Group multiple stations into named boards for quick access
- **Optional Destination Filtering** — Filter each station's departures to only show trains heading to a specific destination
- **Live Departure Data** — Real-time scheduled/estimated times, platform numbers, and cancellation status
- **Service Details** — Tap any departure to see the full journey: previous stops, current station, and upcoming calling points with actual/estimated arrival times
- **Station Alerts** — Disruption messages from National Rail (e.g. lift closures, replacement buses) shown under each station header
- **Load More** — Paginate to see later departures beyond the initial results

## Screenshots

The app has three main screens:

1. **My Boards** — List of your saved departure boards
2. **Departures** — Live departures grouped by station, with status colours (green = on time, orange = delayed, red = cancelled)
3. **Service Detail** — Full calling point list for a selected train

## Getting Started

### Prerequisites

- Android Studio (Arctic Fox or later)
- Android SDK 36 (compileSdk)
- Min SDK 24 (Android 7.0)
- A National Rail Darwin API key (free)

### Obtaining a Darwin API Key

1. Go to [National Rail Data Portal](https://realtime.nationalrail.co.uk/OpenLDBWSRegistration/)
2. Register for an account (it's free)
3. You will receive an email containing your **API token** (a UUID like `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)

### Configuring the API Key

Add your Darwin API key to the project's `local.properties` file (in the project root directory, same level as `build.gradle.kts`):

```properties
# local.properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
DARWIN_API_KEY=your-api-key-here
```

The key is read at build time and injected into `BuildConfig.DARWIN_API_KEY`. The `local.properties` file is excluded from version control via `.gitignore`, so your key stays private.

**Important:** If you skip this step or leave the key empty, the app will build but all departure requests will fail.

### Building and Running

1. Clone the repository
2. Add your `DARWIN_API_KEY` to `local.properties` as shown above
3. Open the project in Android Studio
4. Sync Gradle
5. Run on a device or emulator (API 24+)

## Architecture

The app follows **MVVM** with **Hilt** dependency injection:

```
app/src/main/java/com/example/uk_trains_app/
├── data/
│   ├── db/           # Room database, DAOs
│   ├── model/        # Data classes (Group, StationEntry, Departure, ServiceDetail)
│   ├── network/      # DarwinSoapClient (SOAP/XML over OkHttp)
│   └── repository/   # GroupRepository, DeparturesRepository, StationRepository
├── di/               # Hilt AppModule
├── ui/
│   ├── departures/   # DeparturesScreen, ServiceDetailScreen + ViewModels
│   ├── groups/       # GroupsListScreen, CreateEditGroupScreen + ViewModels
│   ├── navigation/   # NavHost setup
│   └── theme/        # Material3 theming
├── MainActivity.kt
└── UkTrainsApplication.kt
```

### Key Technical Details

- **Darwin API**: Communicates via SOAP 1.1 XML over HTTPS with the OpenLDBWS endpoint (`ldb12.asmx`)
- **Destination Filtering**: Uses `GetDepBoardWithDetails` with client-side verification of subsequent calling points, ensuring only trains genuinely heading to the destination are shown
- **Database**: Room with migrations. Station entries support optional `filterCrs`/`filterName` columns for destination filtering
- **Station Data**: A bundled JSON asset (`stations.json`) provides offline station name/CRS code lookup for search
- **Rate Limiting**: API requests are chunked (5 concurrent max) with 1-second gaps to stay under the Darwin rate limit

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

This project uses the National Rail Darwin Data Feeds. Usage is subject to the [National Rail Data Portal Terms](https://www.nationalrail.co.uk/developers/).
