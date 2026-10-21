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

## Sync across devices (Firebase)

Sync uses a free Firebase project. Until `firebase.properties` exists in the repo root, the app keeps the calendar on the phone only.

1. Create a project at https://console.firebase.google.com.
2. **Authentication > Get started > Email/Password > Enable.**
3. **Firestore Database > Create database** (production mode), then paste `firestore.rules` into the **Rules** tab and publish.
4. **Project settings > Add app > Android**, package name `app.familycalendar`.
5. Put the values from **Project settings** into `firebase.properties`:

```
apiKey=...        # Web API key
appId=...         # App ID of the Android app (1:...:android:...)
projectId=...     # Project ID
```

These are public identifiers; `firestore.rules` is what keeps each family's data private.

The debug build is signed with `app/debug.keystore` so every CI build installs over the last one. Use a separate, private release key before publishing to the Play Store.
