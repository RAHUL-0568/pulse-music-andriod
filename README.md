# Pulse Music — Android App

The Android app is a **Capacitor wrapper** around the web frontend. It loads the built web assets (`web-frontend/dist`) inside a native Android WebView, with native plugins for Google Auth, media session, haptics, and browser.

---

## Architecture

```
mobile-app/
├── capacitor.config.ts      # Capacitor config (webDir, plugins, appId)
├── package.json              # Shared with web-frontend (same deps)
├── android/                  # Native Android project (Gradle/Kotlin)
│   ├── app/
│   │   ├── build.gradle.kts  # App-level Gradle config
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/tf/pulsemusic/music/
│   │   │   │   ├── MainActivity.kt    # Capacitor BridgeActivity
│   │   │   │   └── MainApplication.kt # Hilt Application
│   │   │   ├── assets/
│   │   │   │   ├── public/            # ← Web assets copied here by `cap sync`
│   │   │   │   ├── capacitor.config.json
│   │   │   │   └── capacitor.plugins.json
│   │   │   └── res/                   # Icons, themes, strings
│   │   └── proguard-rules.pro
│   ├── build.gradle.kts      # Root Gradle config
│   ├── variables.gradle      # SDK versions & dependency versions
│   └── capacitor.settings.gradle
```

### How it works

1. `web-frontend/` is built with Vite → produces `web-frontend/dist/`
2. `npx cap sync android` copies `dist/` into `android/app/src/main/assets/public/`
3. Gradle builds the native APK wrapping those web assets
4. The Capacitor `BridgeActivity` loads `index.html` in a WebView

---

## Prerequisites

- **Node.js** 20+ or **Bun**
- **Android SDK** (set `ANDROID_HOME` env var)
- **ADB** (from Android SDK platform-tools)
- **Android Emulator** or physical device
- **Java 21** (required by Gradle/Kotlin config)

### Environment Setup (Windows)

```powershell
# Set Android SDK path (adjust to your install location)
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:PATH = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:PATH"
```

---

## Build & Deploy Pipeline

### Full pipeline (build → sync → compile → install → launch)

```powershell
# 1. Build web frontend
cd web-frontend
npm run build

# 2. Sync web assets into Android project
cd ../mobile-app
npx cap sync android

# 3. Build debug APK
.\android\gradlew.bat -p android assembleDebug

# 4. Install on emulator/device
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk

# 5. Force-stop & cold-launch
adb -s emulator-5554 shell am force-stop tf.pulsemusic.music
adb -s emulator-5554 shell am start -S -W -n tf.pulsemusic.music/.MainActivity
```

### Quick update (after CSS/JS changes only)

If you only changed web code (HTML/CSS/JS) and no native code:

```powershell
cd web-frontend && npm run build
cd ../mobile-app && npx cap sync android
adb -s emulator-5554 shell am force-stop tf.pulsemusic.music

# Reinstall (skip Gradle if no native changes)
adb -s emulator-5554 install -r android\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell am start -S -W -n tf.pulsemusic.music/.MainActivity
```

> **Note:** If the app shows old content after install, do a full uninstall first:
> ```powershell
> adb -s emulator-5554 uninstall tf.pulsemusic.music
> adb -s emulator-5554 install android\app\build\outputs\apk\debug\app-debug.apk
> ```

### Clean rebuild (when things are stuck)

```powershell
# Clean everything
cd mobile-app
.\android\gradlew.bat -p android clean
npx cap sync android
.\android\gradlew.bat -p android assembleDebug

# Full uninstall + reinstall
adb -s emulator-5554 uninstall tf.pulsemusic.music
adb -s emulator-5554 install android\app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 shell am start -S -W -n tf.pulsemusic.music/.MainActivity
```

---

## Capacitor Config

**File:** `capacitor.config.ts`

| Key | Value | Notes |
|-----|-------|-------|
| `appId` | `tf.pulsemusic.music` | Android package name |
| `appName` | `Pulse Music` | Fixed from "Pulse Music Music" |
| `webDir` | `../web-frontend/dist` | Relative path to built web assets |
| `GoogleAuth.serverClientId` | `217687474888-...` | Same as backend `GOOGLE_CLIENT_ID` |

> **Important:** The `web-frontend/capacitor.config.ts` does NOT have the `GoogleAuth` plugin block. If you ever run `cap sync` from `web-frontend/` instead of `mobile-app/`, Google Auth config will be lost.

---

## Capacitor Plugins

| Plugin | Package | Purpose |
|--------|---------|---------|
| App | `@capacitor/app` | App lifecycle (back button, state) |
| Browser | `@capacitor/browser` | In-app browser for OAuth flows |
| Haptics | `@capacitor/haptics` | Vibration feedback |
| Media Session | `@capgo/capacitor-media-session` | Lock screen controls, now playing |
| Google Auth | `@codetrix-studio/capacitor-google-auth` | Google Sign-In |

---

## Android Config

| Setting | Value |
|---------|-------|
| `minSdkVersion` | 24 (Android 7.0) |
| `compileSdkVersion` | 36 |
| `targetSdkVersion` | 36 |
| Java/Kotlin target | 21 |
| Gradle plugin | 8.13.0 |
| DI framework | Dagger Hilt |
| `versionCode` | 251 |
| `versionName` | 2.5.1 |

---

## Known Issues

- ~~**`appName`** is `"Pulse Music Music"` (double "Music") in both `capacitor.config.ts` files~~ (Fixed)
- ~~**`versionCode/versionName`** in `build.gradle.kts` is still `1`/`1.0`, while web frontend is `2.5.1`~~ (Fixed to 2.5.1)
- **Deep link host** in `AndroidManifest.xml` points to `web-frontend-rho-six.vercel.app` (This is intentional for the Jam feature, DO NOT CHANGE).
- **`google-services.json`** is missing — Firebase/push notifications won't work without it.
- ~~**Capacitor config mismatch** — `web-frontend/capacitor.config.ts` is missing the `GoogleAuth` plugin block that `mobile-app/capacitor.config.ts` has~~ (Fixed)

---

## Recent Changes

### 2026-07-30: Android Swipe Gestures & Playback Performance Engine
- **Mini Player Swipe Gestures:** Implemented native-feeling, high-performance horizontal swipe gestures (Swipe Left for Next Track, Swipe Right for Previous Track) on the Mini Player.
- **Main Player Swipe Gestures:** Added Spotify-style swipe gestures to the Main Player. 
- **Stationary Player Shell Animations:** Perfected transition animations so that *only* the track content (album artwork, title, and artist) moves during a swipe, while the rest of the player UI (controls, progress bar, background) remains completely stationary.
- **Playback Caching & Preloading Engine:** Built a production-grade sliding window cache (previous 2, next 2 tracks) that preloads audio streams, artwork, and lyrics using an LRU cache system to ensure zero-latency transitions and eliminate buffering spinners.
- **Search UI Refinements:** The bottom navigation bar and mobile keyboard now hide automatically when typing in the search page for a less cluttered experience.
- **APK Built:** Generated the latest iteration of `pulse-music.apk` and tested on the Android emulator.

### 2026-07-29: Mobile UI Overhaul & Navigation Polish (PulseMusic-v17)
- **Mobile Mini Player Redesign:** Completely overhauled the mobile mini-player UI. Removed the progress bar and extra controls to create a sleek, single-row layout featuring only the track info, Play/Pause, and Like button. Reduced height to 70px to maximize screen real estate.
- **Search History:** Refactored search history to be "interaction-based" (Spotify style), storing clean objects instead of raw text queries.
- **Playback & Navigation:** Automatically launch the fullscreen player when any track or queue item is explicitly clicked.
- **Player Overlap Bug:** Fixed an issue where clicking anywhere inside the active fullscreen player would incorrectly trigger the mini player to appear over it.
- **Theming & Flashes:** Eliminated the "flash of old app design" on fresh starts by injecting a synchronous script to parse theme settings before DOM paint.

### 2026-07-28: Layout Redesign & Seamless Playback (PulseMusic-v8)
- **UI Redesign Synced:** Completely removed the left sidebar navigation and hamburger menu button, delivering a unified single-column layout for the mobile app. The "Jam" session button was relocated to the fullscreen player footer.
- **Bug Fixes:** Fixed mobile CSS grid layout breakages and Javascript event listener crashes that previously resulted in blank screens on app load.
- **Playback UI Enhancements:** Configured the app to automatically open the main fullscreen player natively via Javascript whenever a user taps any track item, "Play All", or "Play" button.
- **APK Built:** `PulseMusic-v8.apk` generated and placed on desktop.

### 2026-07-28: Android Native Configs & Player Fixes (PulseMusic-v4)
- **Android Native Configs Fixed:** Corrected `appName` to "Pulse Music", updated `versionCode` to 251 and `versionName` to "2.5.1". Copied GoogleAuth plugin to web config. Left Jam URL deep link intact.
- **Web Player Fixes Synced:** Player now opens by clicking anywhere on the track info. Fixed playback delays by removing artificial `waitForCanPlayOrTimeout` wait and adding `preload="auto"`, resolving the issue where songs get stuck on end.
- **APK Built:** `PulseMusic-v4.apk` generated and placed on desktop.

### 2026-07-27: Bottom Navigation Glassmorphism
- Updated `web-frontend/styles.css` with Spotify-inspired glassmorphism bottom nav
- Semi-transparent background with `backdrop-filter: blur(20px)`
- Rounded top corners (`border-radius: 18px`)
- Soft floating shadow
- Smooth transitions on active state
- Press/tap feedback animation (`scale(0.92)`)
- Full safe-area support for gesture navigation
- Fallback for browsers without `color-mix()` support
- **Files changed:** `web-frontend/styles.css` only (CSS-only change, no HTML/JS)

### 2026-07-30: Artist Page Caching & Bugfixes
- Fixed HTTP 422 errors when navigating to the Artist Page from the Main Player.
- Implemented robust ArtistRepository with ArtistCache, ArtistImageCache, and deduplicator for instant loading and offline robustness.
- Implemented intelligent background prefetching on track playback and search results rendering.
