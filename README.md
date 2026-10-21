# Family Calendar

A Skylight-style family calendar for Android. Everyone in the family gets a color, and the app flags when someone is double-booked.

This is the first milestone from the product spec: family members, manual events, and day, week and month views, stored on the phone. Syncing Outlook/Teams, family sharing and the Claude assistant come next.

## Install on your phone

1. On your Android phone, open https://github.com/liXiledil/family-calender/releases/latest/download/family-calendar.apk in Chrome.
2. Open the downloaded `family-calendar.apk` and allow installing from Chrome when asked.

Every push to `main` rebuilds the APK and replaces the **latest** release, so the same link always gets the newest version.

## Build locally

Open the folder in Android Studio, or run:

```
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.
