# nexStream — Feature List

nexStream is a premium IPTV player for Android TV and Fire TV, available for £3.99 per device.

---

## Live TV & EPG

- **Full IPTV playback** — M3U and Xtream Codes playlists
- **Electronic Programme Guide (EPG)** — full canvas-rendered programme grid, scrollable horizontally across time and vertically across channels
- **Live channel switching** — D-pad navigation directly from the guide
- **Now-playing indicator** — live red "now" line shows current time across all rows
- **Programme detail** — title visible in every cell; current programme scrolls if it started before the visible area
- **Category filtering** — filter channels by group/category from the sidebar panel
- **Favourites** — mark guide categories as favourites for quick access
- **Channel logo loading** — logos loaded and cached from the playlist; initials fallback when unavailable

---

## Movies & Series (VOD)

- **Movies library** — full poster grid with TMDB artwork, age-rating badges, and category filtering
- **Series library** — season/episode browsing with poster art
- **Search** — search across Live TV, Movies, Series, and People in one place
- **My List** — save content across Live TV, Movies, and Series
- **Recently Watched** — automatically tracks watched channels, movies, and episodes

---

## CatchUp / Replay

- **CatchUp TV** — replay broadcasts up to 8 days back for supported channels
- **Date picker panel** — browse available dates per channel
- **Progress tracking** — resume from where you left off

---

## Player

- **ExoPlayer / Media3** — hardware-accelerated playback with FFmpeg decoder extension for broad format support
- **Subtitle support** — SubDL integration; search and download subtitles by language
- **Aspect ratio control** — cycle between fit, fill, zoom, and stretch
- **Volume control** — in-player D-pad volume adjustment
- **Rewind / Fast-forward** — 10-second skip buttons with on-screen feedback
- **Progress bar** — seek to any position with the D-pad
- **Auto-hide controls** — controls fade after inactivity; reappear on any input
- **Background playback** — continues audio when app is backgrounded via Media3 service
- **Remote / notification controls** — media session controls on lock screen and notification

---

## Casting

- **Google Chromecast** — cast any stream to a Chromecast device on the same network
- **DLNA / Smart TV** — cast to DLNA-compatible TVs (Samsung, LG, Sony, and others) via UPnP SSDP discovery with full DIDL-Lite metadata
- **AirPlay** — cast to Apple TV and AirPlay-compatible receivers via mDNS discovery
- **Cast overlay** — shows poster art, channel name, and programme title while casting; controls play/pause, seek, and volume on the TV
- **Auto-discovery** — scans for all device types when the cast sheet is opened; no manual setup required

---

## Profiles

- **Multiple profiles** — create up to 5 named profiles per account, each with a custom emoji avatar
- **Parental controls** — restrict a profile by age rating; blocked content is hidden from all screens
- **Per-profile watchlist & history** — My List, Recently Watched, and watch progress are isolated per profile
- **Profile sync** — profiles sync to the nexStream backend and are restored on reinstall

---

## Reminders

- **Programme reminders** — set a reminder on any future programme from the EPG
- **Notification delivery** — reminder fires via system notification at programme start time (WorkManager)
- **Reminders screen** — view and manage all upcoming reminders in one place

---

## Downloads

- **Offline downloads** — download VOD content for offline playback
- **Download manager** — track active, completed, and failed downloads

---

## Themes & Appearance

- **Dark theme** — deep navy/charcoal dark theme, default for TV use
- **Light / cream theme** — warm cream-toned light theme; backgrounds, panels, and EPG all use a cohesive cream palette
- **Custom themes** — JSON themes served from the nexstream.uk API per licence; supports custom colours, font scale, font weight, and downloadable custom fonts
- **Font scale** — adjust text size across the entire app
- **Sidebar rail order** — reorder navigation items in Settings → Navigation to match your watching habits
- **Default startup screen** — choose which section opens on launch (Guide, Movies, Series, CatchUp, etc.)

---

## Navigation (Android TV / Fire TV)

- **Three-zone focus model** — Rail → Panel → Content; fully navigable by D-pad with no touch required
- **Sidebar rail** — icon rail with labels; collapses to make room for the panel when expanded
- **Category panel** — slides in from the rail; populated with categories for the active section
- **Back navigation** — Back key returns focus from Content → Panel → Rail, exactly as expected on TV
- **D-pad key-event handling** — every interactive element handles Enter/DirectionCenter correctly for TV remotes
- **Mobile back button** — panel shows a Back row on phones and tablets for touch navigation

---

## Playlist Management

- **Add by Xtream Codes** — server URL, username, password
- **Add by M3U URL** — direct URL import with streaming JSON parser (no OOM on large playlists)
- **Auto-configure** — scan a QR code from the nexStream website; the app auto-detects and imports the playlist
- **Import progress screen** — shows live channel count, EPG, Movies, and Series loading stages
- **Multiple playlists** — manage and switch between sources in Settings → Playlists
- **Automatic EPG refresh** — WorkManager job refreshes EPG data on a schedule

---

## Licence & Trial

- **7-day free trial** — full access to Live TV, Guide, Movies, Series, CatchUp, and all features during trial
- **Post-trial mode** — after trial expiry, Live TV and Guide remain fully accessible; Movies, Series, and CatchUp are hidden until a licence is activated
- **Licence activation** — enter a licence key in Settings → Licence to unlock the full app permanently
- **Auto-activation** — if a licence is assigned to your device ID on the nexStream dashboard, it activates automatically on next launch
- **Multi-device management** — view and remove registered devices from the Licence screen
- **Reseller support** — resellers can provision accounts and pre-assign licences from the nexStream backend

---

## Push Notifications

- **Firebase Cloud Messaging (FCM)** — receive push notifications for service announcements and licence events
- **Deep-link support** — notifications can link directly to content or settings screens

---

## Settings

- **Playlists** — add, remove, and view playlist details including Xtream expiry date
- **Appearance** — choose theme, font scale, and font weight
- **Player** — configure default subtitle language, aspect ratio behaviour, and playback preferences
- **Profiles** — create, edit, and switch profiles; configure parental-control age ratings
- **Licence** — activate a licence key, view activation status, manage devices
- **Navigation** — reorder sidebar rail items and set the default startup screen
- **Account** — view account details and connected Xtream account info

---

## Technical

- **Android TV & Fire TV optimised** — Leanback-compatible, remote-first UX
- **Physical device tested** — developed and tested on Fire TV Stick 4K and Allwinner Android TV box
- **Room local database** — all channels, EPG, VOD, and series cached locally for instant browsing
- **Offline-capable** — channels, EPG, and downloaded content accessible without internet after first sync
- **TMDB integration** — high-quality poster art and age certifications pulled from The Movie Database
- **Coil image loading** — memory and disk caching with 4 concurrent fetch limit for smooth scrolling
- **Stripe payments** — secure one-time payment at nexstream.uk; £3.99 per device

---

*nexStream is developed and maintained as a solo project. For support, visit nexstream.uk.*
