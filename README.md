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
| `appName` | `Pulse Music Music` | ⚠️ Has double "Music" — should be fixed |
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
| `versionCode` | 1 ⚠️ (needs bumping) |
| `versionName` | 1.0 ⚠️ (web is at 2.5.1) |

---

## Known Issues

- **`appName`** is `"Pulse Music Music"` (double "Music") in both `capacitor.config.ts` files
- **`versionCode/versionName`** in `build.gradle.kts` is still `1`/`1.0`, while web frontend is `2.5.1`
- **Deep link host** in `AndroidManifest.xml` points to `web-frontend-rho-six.vercel.app` (dev URL), not the production domain
- **`google-services.json`** is missing — Firebase/push notifications won't work without it
- **Capacitor config mismatch** — `web-frontend/capacitor.config.ts` is missing the `GoogleAuth` plugin block that `mobile-app/capacitor.config.ts` has

---

## Recent Changes

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
