# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# NexStream — Android/Android TV IPTV Player

A commercial Kotlin/Jetpack Compose Android TV and Fire TV IPTV streaming application with a PHP/MySQL backend.
Solo project by a single developer. Priced at £3.99/device.

---

## Tech Stack

### Android App
- **Language:** Kotlin
- **UI:** Jetpack Compose (Android TV / Leanback-compatible) + hybrid RecyclerView for grids
- **Player:** ExoPlayer (Media3) with FFmpeg decoder extension
- **DI:** Hilt (KSP-based, not kapt)
- **Local DB:** Room (current schema version: **22**)
- **Networking:** Retrofit / OkHttp
- **Image loading:** Coil with custom `ImageLoader` (20% memory, 50 MB disk, 4 concurrent fetches)
- **Background work:** WorkManager (EpgRefreshWorker, ReminderWorker)
- **Push notifications:** Firebase Cloud Messaging
- **Subtitles:** SubDL integration
- **Target:** Android TV, Fire TV, and physical Allwinner TV box

### Backend (IONOS shared hosting)
- **Language:** PHP
- **Database:** MySQL
- **Domain:** nexstream.uk
- **Deployment:** IONOS file manager (no SSH access — never suggest SSH-based deploy steps)
- **Payments:** Stripe

---

## Architecture

### Android
- MVVM with ViewModels and StateFlow/SharedFlow
- Hilt for dependency injection throughout
- Room for local persistence (playlists, EPG cache, profiles, settings)
- Repository pattern — ViewModels never access data sources directly; `PlaylistRepository` is the central data hub
- Single-activity (`MainActivity`), Compose navigation

### Navigation — two layers
Top-level routing uses a sealed `Screen` class (`Loading`, `Main`, `AddPlaylist`, `player/{channelUrl}`) in `NexStreamNavGraph`. Inside `MainScreen`, sub-navigation uses the sealed interface `AppRoute` (`Guide`, `Movies`, `Series`, `CatchUp`, `Search`, `MyList`, `Downloads`, `Settings/*`). `AppRoute` extensions (`rootSection()`, `hasCategoryPanel`, `isSettings`) drive sidebar and panel behaviour.

### Three-pane layout & focus model
Navigation state lives in `MainScreen.kt` (zone, panelFocusTick, sidebarRefocusTick, etc.) and is driven by Sidebar callbacks.

**Layout — Rail and Panel are mutually exclusive:**
- **Rail visible (panel closed):** Rail = 168dp, Panel = 0dp
- **Panel visible (rail hidden):** Rail = 0dp (slides left), Panel = 180dp (slides right)

The rail animates to 0dp and is made non-focusable (`focusProperties { canFocus = false }`) when the panel is open. The panel animates to 0dp when closed. This is driven by a single `Animatable<Float>` (`panelProgress` 0→1) in `Sidebar.kt`.

All rail items have a panel — `hasPanel` does not exist anywhere in the codebase.

| Zone | Input | Result |
|------|-------|--------|
| Rail | Center/OK or DPad Right | Rail collapses to 0, panel expands, focus → panel (previously selected → "All" → first) |
| Panel | Center/OK | Selects panel item, updates content — focus stays on panel |
| Panel | DPad Right | Selects panel item, moves focus to content |
| Panel | DPad Left / Back | Panel collapses to 0, rail expands, focus → rail |
| Content | Back | Returns focus to panel (panel stays visible, rail stays hidden) |
| Content | DPad Left | Blocked — no DPad back-path from content to panel |

**Focus flow implementation:**
- `LaunchedEffect(currentRoute)` in `MainScreen.kt` — when `currentRoute.hasCategoryPanel` and NOT first load and NOT a settings sub-route switch, sets `zone = Zone.PANEL`, then waits 160ms (panel animation is 140ms) before incrementing `panelFocusTick`.
- `isSettingsSubRoute` is true only when switching between settings items where `previousRoute.isSettings == true` — this suppresses the zone reset so Center/OK on a panel item doesn't steal focus back from the content.
- `previousRoute` is tracked in `MainScreen.kt` to distinguish first-time settings entry (focus → panel) from intra-settings navigation (zone unchanged).

**Key event pattern (do not deviate):**
```kotlin
Box(
    modifier = Modifier
        .focusRequester(focusRequester)
        .onFocusChanged { isFocused = it.isFocused }
        .onKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (keyEvent.key) {
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { /* confirm */ true }
                Key.DirectionRight -> { /* zone-specific right action */ true }
                Key.DirectionLeft  -> { /* zone-specific left action  */ true }
                else -> false
            }
        }
)
```

**Hard rules for navigation:**
- Never send focus to content when Center/OK is pressed on a panel item
- Never use `Modifier.clickable` alone on TV — always pair with `onFocusChanged` + `onKeyEvent`
- Never duplicate navigation logic in screen-level composables — all state lives in `MainScreen.kt`
- Never add a `hasPanel` parameter — all rail items have a panel
- Back behaviour is handled by `BackHandler` in `MainScreen.kt`, not in individual screens
- Rail and panel are mutually exclusive — never show both at the same time
- Never set rail width to a fixed non-zero value when the panel is expanded

### Theming system
`ThemeManager` loads JSON themes from the nexstream.uk API (keyed by licence token) and caches them to `filesDir/themes/`. It falls back to bundled asset defaults (`assets/themes/theme-{dark,light}-default.json`). Themes can include custom font download URLs. `NexStreamThemeProvider` exposes the active theme as a `CompositionLocal`.

### Licence & trial
`LicenceManager` derives a device ID from SHA-256 of `ANDROID_ID`. `TrialManager` tracks trial start/end locally. The app checks licence state on startup via `LoadingScreen`.

### Playlist sources
`PlaylistRepository` supports two source types: **M3U** (streamed via `JsonReader`/`M3UParser`) and **Xtream Codes** (via `XtreamApiService` with JSON streaming). EPG is parsed from XMLTV via `XmltvParser`.

### Sync managers
`data/sync/` contains dedicated sync managers for profile (`ProfileSyncManager`), watchlist (`WatchlistSyncManager`), and watch progress (`ProgressSyncManager` + `ProgressSyncIntegration`). All sync to `nexstream.uk/api/` using the licence key as Bearer token.

### Hybrid grid views
`PosterGridView` (Movies, Series, CatchUp) and `EPGGridView` are custom `RecyclerView`/canvas-based views wrapped in `AndroidView`. They expose `requestItemFocus`/`requestItemFocusNow` for programmatic D-pad focus restoration. Do not replace these with pure Compose lists without understanding the focus performance implications on TV hardware.

---

## Project Structure

```
app/src/main/java/app/nexstream/player/
  data/
    local/          # Room — NexStreamDatabase, DAOs, entities
    remote/         # Retrofit API services, M3UParser, XmltvParser, XtreamModels
    repository/     # PlaylistRepository (central), WatchProgressRepository, Workers
    sync/           # ProfileSyncManager, WatchlistSyncManager, ProgressSyncManager
    profile/        # ProfileManager (active profile state, profile switching)
  di/               # Hilt modules: NetworkModule, DatabaseModule
  license/          # LicenceManager, TrialManager, LicencePreferences, API services
  subtitle/         # SubtitleManager, SubDL API service, SubtitleBottomSheet
  service/          # NexStreamPlaybackService (Media3), PlayerConnection, NexStreamFirebaseService
  downloads/        # NexStreamDownloadManager
  worker/           # EpgRefreshWorker, ReminderWorker
  ui/
    navigation/     # NexStreamNavGraph (top-level Screen routing), NavigationItem
    screens/        # One subdirectory per screen; each contains Screen + ViewModel
    components/     # Sidebar, PosterGridView, TVKeyboard, ProgressBanner, ReminderOverlay
    theme/          # NexStreamTheme, ThemeManager, ThemeParser, ThemePreference, PlayerPreferences
  MainActivity.kt
  NexStreamApp.kt
  CoilConfig.kt     # Note: duplicate exists at ui/screens/player/CoilConfig.kt
```

---

## Coding Conventions

### Kotlin / Compose
- **All coroutine work** in ViewModels via `viewModelScope`; never launch coroutines in Composables
- **D-pad / focus navigation pattern** (established, do not deviate) :
  ```kotlin
  Box(
      modifier = Modifier
          .onFocusChanged { focusState -> /* handle */ }
          .onKeyEvent { keyEvent ->
              when (keyEvent.key) {
                  Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { /* confirm */ true }
                  else -> false
              }
          }
  )
  ```
- **Large playlist parsing:** always use `JsonReader` streaming parsers — never load full JSON into memory (OOM risk with large playlists)
- **Room migrations:** always write explicit `Migration` objects; never use `fallbackToDestructiveMigration()` in production
- **Naming:** `PascalCase` for composables/classes, `camelCase` for functions/variables, `SCREAMING_SNAKE_CASE` for constants
- **No hardcoded strings** in UI — use string resources
- **Coil** for all image loading; use `AsyncImage` with placeholder and error drawables

### Retrofit base URLs
- `https://nexstream.uk/api/` — licence, watchlist, watch progress, themes, trial
- `https://api.subdl.com/` — subtitles
- Xtream Codes API URLs are dynamic per-playlist (set at service-creation time, not in `NetworkModule`)

### PHP Backend
- All API responses return JSON
- Auth uses token-based auth (not session cookies)
- Stripe webhook handlers must validate signatures before processing
- Reseller invite system: invites generaion

---

## Build & Test Commands

On Windows use `.\gradlew`; on Mac/Linux use `./gradlew`.

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run lint
./gradlew lint

# Clean build
./gradlew clean assembleDebug
```

> Testing is done manually on physical Fire TV and Allwinner TV box. There is no CI pipeline currently.

---

## Backend Deployment

Backend files are deployed to IONOS via **`deploy.ps1`** (WinSCP SFTP — credentials in `sftp.env`).

```powershell
# Upload specific files (most common — after a build)
.\deploy.ps1 nexStream.apk
.\deploy.ps1 build_number.txt
.\deploy.ps1 download.php
.\deploy.ps1 api/mylist.php        # any single file relative to backend/

# Full sync of entire backend/ directory
.\deploy.ps1
```

- Credentials are read from `sftp.env` in the project root (not committed)
- The "mkdir" error on every upload is harmless — the remote directory already exists; WinSCP skips and continues
- Database migrations are run manually via phpMyAdmin or inline PHP scripts

## Backend Architecture (PHP)

**Location:** `backend/` — read and reason about this alongside the Android code.  
**Deployed to:** nexstream.uk (IONOS shared hosting). This folder is NOT built or compiled  
locally — files are uploaded via IONOS file manager. Never suggest running PHP locally  
or any SSH/CLI deploy steps.

### API contract (Android ↔ PHP)
- All responses are JSON
- Auth: Bearer token = licence key (SHA-256 of ANDROID_ID)
- Base URL from Android: `https://nexstream.uk/api/`
- AES-256-CBC encryption used for [specific fields e.g. passwords, playlist credentials]

---

## Room Database

Current schema version: **22** (`NexStreamDatabase.kt`)

Entities: `PlaylistEntity`, `ChannelEntity`, `ProgramEntity`, `MovieEntity`, `SeriesEntity`, `EpisodeEntity`, `WatchlistEntity`, `RecentlyWatchedEntity`, `ReminderEntity`, `ProfileEntity`, `ProfileCategoryFilter`, `WatchProgressEntity`, `TmdbPosterEntity`

- Always create an explicit `Migration(from, to)` object for any schema change
- Add new migrations to the `addMigrations(...)` call in `NexStreamDatabase.create()`
- Never rename columns in-place — add new column, migrate data, drop old column across separate migrations if needed

---

## EPG & CatchUp

- EPG grid is canvas-rendered (`EPGGridView`) — changes here are performance-sensitive
- Parallel EPG fetching is used in the CatchUp screen; maintain coroutine parallelism patterns
- TMDB poster caching uses both Coil disk cache and a Room `TmdbPosterEntity` table

---

## Profile Sync

- Profiles sync to backend including `is_restricted` boolean (parental controls)
- Profile sync must handle conflict resolution (last-write-wins by `updated_at` timestamp)
- Restricted profiles block content above a configured age rating
- Active profile is held in `ProfileManager` and flows down to watchlist, recently-watched, and watch-progress queries

---

## Hard Rules

- **Never suggest SSH for backend deployment** — IONOS file manager only
- **Never use `fallbackToDestructiveMigration()`** in the Room database builder
- **Never load full large JSON into memory** — use `JsonReader` streaming
- **Never commit API keys, Stripe secrets, or `.env` equivalents** — check before touching config files
- **TMDB API key** and **Stripe secret key** are environment-sensitive — reference via BuildConfig or backend config, never hardcode

---

## Key External Services

| Service | Purpose |
|---------|---------|
| TMDB API | Channel/programme metadata and posters |
| SubDL | Subtitle search and download |
| Stripe | Payment processing (£3.99/device) |
| IONOS | Shared hosting for nexstream.uk backend |
| Firebase | Push notifications (FCM) |

---

## Terminology

- **CatchUp** — replay of previously broadcast content, up to 8 days back
- **EPG** — Electronic Programme Guide; the TV schedule grid
- **Reseller** — a white-label partner who can provision nexStream accounts under their own branding
- **Profile** — a per-user viewing profile within a single account, with optional parental restrictions
- **Playlist** — an M3U or Xtream Codes source defining channels available to a user
- **Allwinner box** — a physical Android TV device used for testing (in addition to Fire TV)
