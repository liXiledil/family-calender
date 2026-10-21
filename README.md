# Family Calendar

A Skylight-style family calendar for Android. Everyone in the family gets a color, and the app flags when someone is double-booked.

This is the first milestone from the product spec: family members, manual events, and day, week and month views, stored on the phone. Syncing Outlook/Teams, family sharing and the Claude assistant come next.

## Install on your phone

1. Open the latest successful run under **Actions > Android build**.
2. Download the `family-calendar-apk` artifact and unzip it.
3. Open `app-debug.apk` on your Android phone and allow installing from this source when asked.

## Build locally

Open the folder in Android Studio, or run:

```
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.
