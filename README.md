# Birdayyy

A birthday and important-dates reminder for Android, built with Jetpack Compose and Material 3 Expressive.

- Pulls dates from phone contacts (including Google accounts synced to the phone) and, optionally, iCloud via CardDAV (Apple ID + app-specific password).
- A grid of shaped tiles, with everyone celebrating today as full-width cards. Pull down a little to see earlier events (grouped by month), pull further to refresh.
- A 7 AM digest notification, with per-person styles: Notify on priority, Notify silently, Never notify.
- Send a wish: pick a saved template (Friends, Family, Colleagues) and hand it to any messaging app.
- Add people and custom dates from the app; they are written back to your contacts.

## Build

Requires Android Studio's JDK and the Android SDK.

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug
```

Debug builds include a "Send a test reminder" row in Settings.

## Design

`design/` holds the design boards (`*.dc.html`) and `canvas.json` layout exported from the design canvas.

Icons: [Phosphor](https://phosphoricons.com) (Regular) via `com.adamglin:phosphor-icon`. Type: Roboto Flex.
