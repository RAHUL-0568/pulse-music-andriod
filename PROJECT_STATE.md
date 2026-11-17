signed # Pulse Music - Project State & Changelog

## Overview
Pulse Music is an Android music player app consisting of:
- **Frontend:** Android app built with Kotlin, Jetpack Compose, and Hilt. Uses a local Room Database for offline-first capabilities (`pulse-android` folder). Audio playback is handled by ExoPlayer via `MusicPlayerManager`.
- **Backend:** A Python FastAPI server (`main.py`) running on `localhost:8080` that interfaces with an external JioSaavn API worker (`albatross0071`). It also integrates with MongoDB to handle user accounts, custom playlists, liked songs, and profile pictures.
- **Connection:** The Android device communicates with the local Python backend using `adb reverse tcp:8080 tcp:8080`.

## Full Historical Changelog (From Project Inception)

### 1. Initial Architecture & Setup
- **Stack Definition:** Set up Android project with Kotlin, Jetpack Compose for UI, Hilt for dependency injection, Room for local database caching (offline-first architecture), Retrofit for API networking, and ExoPlayer for audio playback.
- **Backend Setup:** Created a Python FastAPI server (`main.py`) serving as a middleware for the external JioSaavn worker (`albatross0071`). Connected it to a MongoDB database to handle user data (playlists, liked songs, follows).
- **Authentication:** Integrated JWT token-based authentication and user profiles. Stored tokens securely via `AuthRepository`.
- **Cloudinary Integration:** Added profile picture upload functionality to the Python backend utilizing Cloudinary.

### 2. Library & Offline-First Data Layer
- **Room Database DAOs:** Created robust local caching tables: `playlist_songs`, `liked_songs`, `followed_artists`, and `local_songs`.
- **Background Syncing:** Configured Repositories to fetch remote data silently in the background and insert it into Room, allowing the UI to instantly react via Kotlin Flows without loading spinners.
- **Library Screen Filter Bar:** Built an interactive filter bar at the top of the Library Screen to easily toggle between "Playlists", "Liked Songs", and "Artists".

### 3. Playback & UI Enhancements
- **Global Music Manager:** Built `MusicPlayerManager` using ExoPlayer to handle global song queues, playback states (`isPlaying`), and current song tracking across the app.
- **Animated Equalizer:** Implemented a custom Animated Equalizer in Compose (`AnimatedEqualizer` component) that displays next to the currently playing track in lists (Albums, Playlists, Liked Songs).
- **Dynamic Play/Pause Buttons:** Upgraded the main floating action buttons in `PlaylistScreen`, `AlbumScreen`, and `ArtistScreen`. They now dynamically show a "Pause" icon if their respective collection is playing, and clicking them toggles the playback state via `togglePlayPause()`.

### 4. Playlist Features & Spotify Import
- **Spotify Playlist Import:** Created backend API routes and frontend logic to allow users to import Spotify playlists directly into the app by extracting metadata and searching for matching JioSaavn tracks.
- **Playlist Management:** Added options to Create new custom playlists.
- **Rename & Delete Playlists:** Added UI dialogues and dropdown menus in `PlaylistScreen` to rename custom playlists or delete them entirely, updating both local Room DB and the MongoDB backend.

### 5. Critical Bug Fixes
- **Artist Page - Album "0 Songs" Bug:** Fixed a backend parsing issue where the JioSaavn API sometimes returns an empty ID string (`"id": ""`) for certain albums. `main.py` was updated to parse the inner songs list anyway instead of rejecting the album as `Unknown`.
- **Custom Playlist - "0:00" Duration Bug:** Fixed a sync issue in `PlaylistRepository.kt`. The app was incorrectly checking for `"duration"` instead of `"durationMs"` in the backend JSON payload, causing all synced playlist songs to save with `0` duration in the local database. Corrected the key and added logic to automatically delete and re-sync any corrupted 0-duration songs.
- **Profile Picture Rendering Bug:** Investigated a bug where the profile picture disappeared on app restart. Fixed by advising the user to re-login, which forces the app to correctly parse and cache the new `profilePic` field that was recently added to the backend's auth routes.

### 6. Premium Crossfade & Audio Engine
- **Equal-Power Crossfade:** Overhauled the crossfade system to mimic Spotify Premium. Replaced linear interpolation with equal-power curves (sine/cosine) to maintain constant perceived acoustic energy, preventing volume dips during song transitions.
- **Dual-Player Architecture:** Extracted all crossfade logic out of `MusicPlayerManager.kt` into a dedicated `CrossfadeManager.kt` managing two parallel `ExoPlayer` instances (primary and secondary) to overlap tracks flawlessly.
- **Audio Focus Overlap Fix:** Resolved a critical bug where Android's default audio focus handling instantly paused the primary player when the secondary player started. Bypassed default focus during the transition and restored it afterward.

### 7. Persistent Jam Sessions
- **MongoDB Persistence:** Overhauled `main.py` to persist Jam Sessions permanently using MongoDB (`jams`, `jam_members`, `jam_messages` collections).
### 8. Vercel API & Jam WebSocket Hybrid Routing
- **Vercel Frontend REST Routing:** Updated `local.properties` to route standard REST calls (`API_BASE_URL`) through the Vercel frontend (`https://web-frontend-rho-six.vercel.app/`).
- **Smart WebSocket Split (`JamSessionManager.kt`):** Since Vercel serverless functions do not support long-lived WebSockets (`wss://`), `JamSessionManager.kt` (`connectToJamSession`) was updated with intelligent routing. When `API_BASE_URL` points to Vercel, `wsBaseUrl` automatically routes WebSocket connections (`wss://`) straight to the persistent Render backend (`wss://pulse-music-backend-fallback-1.onrender.com/`), ensuring 100% reliable real-time Jam sync without serverless timeouts.

### 9. Admin control + data-usage pass (2026-11-17)
- **Admin panel:** `admin.html`, served by `main.py` at `/admin`. Needs the `ADMIN_KEY` env var on the server. API under `/api/v1/admin/*` (stats, list/search users, block, unblock, delete). Blocked/removed accounts get 403/401 with `X-Account-Blocked` / `X-Account-Removed`; the app (`AccountEvents` + `MainActivity`) signs out and shows the message. Live jam sockets are closed on block.
- **Security fixes in `main.py`:** email lookups use `re.escape` (`email_query`), reset codes are brute-force limited (5 tries), social-login accounts can't crash `/auth/login`, optional `GOOGLE_CLIENT_IDS` audience check.
- **Data usage:** HTTP cache is per-endpoint (private `/user`, `/auth`, `/jam` never cached; 50 MB cap, was 1 TB/30 days for everything). Startup/Clear-Cache no longer delete `audio_cache`/`image_cache`. Songs are cached under key `song_<id>` and skip the URL lookup when already on disk. 96 kbps on mobile data (best only on Wi-Fi with High quality on). Next-track pre-buffer only when crossfade is on. Recommendations only fetched when the queue is nearly empty. Cover art goes through Coil (`ArtworkLoader`) and requests smaller CDN thumbnails. Search category tiles cached 7 days.
- **Bug fixes:** `Ellipsisagy` compile error, Jam listeners follow the active player after crossfade, stale lyrics/recommendations cancelled, "Go to Album" (wrong ID) removed, real login error messages, lock screen API 24-25 crash.
- Nothing here has been compiled: no JDK on the dev machine when this was written. Build once and test before shipping.

## Known Architecture Details
- **Offline First:** Playlists, Liked Songs, and user library data are cached locally using Room. The view models typically expose `Flow` objects from Room directly to Compose, and trigger a `sync` with the backend in the background.
- **Images:** Uses Coil (`AsyncImage`) to load album arts and Cloudinary for user profile pictures.
- **Auth:** Uses JWT tokens stored in `AuthRepository` for authenticated backend routes.

*(This file is automatically generated and updated to help future AI sessions understand the project context and recent modifications.)*
