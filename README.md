# MILK — Android Backup Gallery

MILK is a creamy milk-carton themed backup gallery packaged as an Android APK using Capacitor.

## Build the APK
1. Open the **Actions** tab.
2. Select **MILK Android APK**.
3. Select **Run workflow** if a run has not started automatically.
4. Open the completed run and download the **MILK-Android-APK** artifact.
5. Extract the artifact ZIP to get `app-debug.apk`, then install it on an Android device.

The workflow builds a debug APK for testing. A signed release APK/AAB requires a separately configured signing key.

## Features
- Animated milk carton, liquid wave and droplets
- Tap-to-play synthesized cow moo sound
- Add files to the gallery, preview, search, filter, download copies and delete backups
- Browser-style IndexedDB storage inside the app's WebView

## Important limitations
This APK wraps the current web gallery. It does not yet automatically monitor all Android downloads or copy every file in the background. That requires additional native Android download/file integration and clear user permissions. Backups are stored locally in the app's data and may be lost if app data is cleared or the app is uninstalled.
