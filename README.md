<div align="center">

<img src="https://raw.githubusercontent.com/nexStream-app/nexStream/main/assets/banner.png" alt="nexStream" width="100%" />

# nexStream

### The ultimate IPTV player for Android TV & Fire TV

[![Latest Release](https://img.shields.io/github/v/release/nexStream-app/nexStream?label=Download&color=4CAF50&style=for-the-badge&logo=android)](https://github.com/nexStream-app/nexStream/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Fire%20TV-FF6F00?style=for-the-badge&logo=android)](https://github.com/nexStream-app/nexStream/releases/latest)
[![Price](https://img.shields.io/badge/Price-£3.99%2Fdevice-blue?style=for-the-badge)](https://nexstream.uk)
[![Website](https://img.shields.io/badge/Website-nexstream.uk-9C27B0?style=for-the-badge&logo=google-chrome&logoColor=white)](https://nexstream.uk)

**[Download APK](https://github.com/nexStream-app/nexStream/releases/latest)** · **[Website](https://nexstream.uk)** · **[Get a Licence](https://nexstream.uk/buy.php)**

</div>

---

nexStream is a premium IPTV and media player built from the ground up for **Android TV** and **Fire TV**. It supports IPTV playlists (M3U & Xtream Codes), Plex, Jellyfin, and local files — all in one polished, remote-friendly interface.

> [!NOTE]
> nexStream requires a valid licence key (£3.99 per device). A free trial is available. The app does **not** provide any content — bring your own IPTV subscription, Plex/Jellyfin server, or local files.

---

## Screenshots

<table>
  <tr>
    <td><img src="https://raw.githubusercontent.com/nexStream-app/nexStream/main/assets/screenshots/home.png" alt="Home Screen" /></td>
    <td><img src="https://raw.githubusercontent.com/nexStream-app/nexStream/main/assets/screenshots/epg.png" alt="EPG Guide" /></td>
  </tr>
  <tr>
    <td align="center"><em>Live TV</em></td>
    <td align="center"><em>Electronic Programme Guide</em></td>
  </tr>
  <tr>
    <td><img src="https://raw.githubusercontent.com/nexStream-app/nexStream/main/assets/screenshots/movies.png" alt="Movies" /></td>
    <td><img src="https://raw.githubusercontent.com/nexStream-app/nexStream/main/assets/screenshots/player.png" alt="Player" /></td>
  </tr>
  <tr>
    <td align="center"><em>Movies with TMDB metadata</em></td>
    <td align="center"><em>Full-featured player</em></td>
  </tr>
</table>

---

## Features

### 📡 Playlist Sources
- **M3U playlists** — load from URL or local file; streamed with zero memory overhead on huge playlists
- **Xtream Codes** — native API support for Live, VOD, and Series with full category browsing
- **Multiple playlists** — manage and switch between playlists; custom sort order
- **Plex** — browse and play your Plex library via PIN authentication
- **Jellyfin** — full library browsing for movies, series, and music
- **Local files** — browse and play videos stored on your device or USB drive

### 📺 Live TV
- Smooth channel switching with pre-buffering
- Favourite channels and custom channel groups
- **Electronic Programme Guide (EPG)** — scrollable TV grid with current and upcoming programmes
- EPG reminder notifications — get alerted before a show starts
- **CatchUp TV** — replay broadcasts up to 8 days back (where supported by provider)
- Stream quality badge showing resolution and codec
- External player support (MX Player, VLC, etc.)

### 🎬 Movies & Series
- Full VOD and series library with TMDB metadata — posters, ratings, cast, synopsis
- **Rotten Tomatoes scores** — Critics score, Audience score, and Critics Consensus
- Season/episode browser with watch progress tracking
- Resume playback from where you left off
- Mark as watched; track progress across all content
- **My List** — save anything to watch later
- Recently Watched with quick resume

### 🔍 Search
- Search across live channels, movies, series, and EPG data simultaneously
- **People search** — browse cast and crew; see their full filmography
- Search within Plex and Jellyfin libraries

### ▶️ Player
- **ExoPlayer (Media3)** with FFmpeg decoder extension for maximum format support
- Subtitles from [SubDL](https://subdl.com/) — search and download subtitles in multiple languages
- **AI-powered subtitle generation** using Groq — automatic captions for streams without subtitles
- Subtitle delay adjustment (sync offset)
- Playback speed control (0.25× – 4×)
- Sleep timer
- Stats overlay (bitrate, codec, resolution, dropped frames)
- Aspect ratio control (Fill / Fit / Zoom)
- **Up-next** panel — see what's coming in a series

### 🎵 Music
- Jellyfin music library — browse by artist, album, or track
- Background playback

### 👥 Profiles
- Multiple viewing profiles per account
- **Parental controls** — restrict content by age rating per profile
- Profile-level settings for appearance, player, and sync
- Profile sync to the cloud

### ☁️ Cloud Sync
- **Watchlist sync** — My List synced across all your devices instantly
- **Watch progress sync** — resume from any device; synced on player close
- **Recently Watched sync** — consistent history across devices
- Real-time sync via Firebase Cloud Messaging push
- Settings sync — appearance and player preferences per profile

### 🎨 Appearance & Themes
- **Dark and Light mode**
- Custom themes loaded from nexstream.uk — unique per licence tier
- Custom font download support
- Font scale and weight controls
- **UI style** — Classic or Modern layout
- EPG mini-player while browsing the guide
- Keyboard font size and weight controls

### 🌐 Languages
Built-in translations for 10 languages — change in Settings or use System default:

🇬🇧 English · 🇫🇷 Français · 🇩🇪 Deutsch · 🇳🇱 Nederlands · 🇸🇪 Svenska · 🇮🇹 Italiano · 🇹🇷 Türkçe · 🇵🇱 Polski · 🇪🇸 Español · 🇵🇹 Português

### 🔔 Notifications
- Firebase Cloud Messaging — receive push notifications from your provider
- Programme reminders — set alerts for upcoming TV shows
- Admin broadcast notifications

### ⚙️ Settings & Utilities
- **Backup & Restore** — export your playlists and settings; restore on a new device
- EPG timezone offset — fix EPG misalignment
- Auto-update — check for and install new app versions in-app
- Custom channel groups/folders — organise channels your way
- D-pad navigation tuning — customise sidebar and panel behaviour

---

## Supported Devices

| Device | Status |
|--------|--------|
| **Amazon Fire TV Stick** (all generations) | ✅ Fully supported |
| **Amazon Fire TV Cube** | ✅ Fully supported |
| **Android TV / Google TV** boxes and sticks | ✅ Fully supported |
| **Allwinner TV boxes** | ✅ Tested and supported |
| Android phones & tablets | ⚠️ Works but optimised for 10-foot TV UI |

**Minimum:** Android 5.0 (API 21)

---

## Installation

### From GitHub Releases (Sideload)

1. On your Fire TV / Android TV, enable **Apps from Unknown Sources** in Settings → My Fire TV → Developer Options
2. Install a file manager (e.g. **Downloader** by AFTVnews)
3. Enter the APK URL from the [latest release](https://github.com/nexStream-app/nexStream/releases/latest)
4. Follow the on-screen install prompts
5. Launch nexStream and enter your licence key

> **Tip:** You can use the Downloader app code **[URL from release page]** to download directly to your Fire TV.

### Getting a Licence

Visit **[nexstream.uk/buy.php](https://nexstream.uk/buy.php)** — £3.99 per device, one-time payment. A free trial is available with no payment details required.

---

## What's New in the Latest Release

See the full changelog in [Releases](https://github.com/nexStream-app/nexStream/releases).

---

## FAQ

**Q: Does nexStream include any IPTV content?**  
A: No. nexStream is a player app. You need your own IPTV subscription, Plex server, Jellyfin server, or local video files.

**Q: How many devices can I use one licence on?**  
A: One licence covers one device. Each device needs its own £3.99 licence.

**Q: Does it work on phones/tablets?**  
A: The app installs and works on phones, but the interface is designed for TV screens and remote controls. A phone-optimised version is not planned.

**Q: Can I use it with my existing Plex or Jellyfin server?**  
A: Yes — add it as a playlist source in Settings. For Plex, you'll authenticate with a PIN; for Jellyfin, use your server URL and credentials.

**Q: Are subtitles supported?**  
A: Yes — automatic subtitle search via SubDL, with AI-generated captions for streams that don't have them.

---

## Disclaimer

nexStream is a media player application. It does not host, stream, or distribute any content. Users are responsible for ensuring they have the legal right to access any content they stream through the application. The developer makes no representations about the legality of any third-party IPTV services.

---

<div align="center">

Made with ❤️ for Android TV &nbsp;·&nbsp; [nexstream.uk](https://nexstream.uk) &nbsp;·&nbsp; [Privacy Policy](https://nexstream.uk/privacy.php) &nbsp;·&nbsp; [Terms of Service](https://nexstream.uk/terms.php)

</div>
