# Family Calendar

A Skylight-style family calendar for Android. Everyone in the family gets a color, and the app flags when someone is double-booked.

## Features

- **Views:** day, week, month and a yearly view of major dates (birthdays, anniversaries, trips; mark an event "major" or "repeats every year").
- **Conflicts:** per-person color coding and highlighting of double-bookings.
- **Import (Import tab):** paste an Outlook "published calendar" ICS link, or read calendars already on the phone (for example the Outlook app's synced calendar). Re-importing replaces that source's events instead of duplicating them. If work policy blocks both, add events by hand or use Claude.
- **Claude (Claude tab):** scan a photo, screenshot or PDF (or share one into Firefly from any app), audit the calendar for duplicates and cleanup, or ask questions. Claude only proposes; nothing changes until you approve, and changes can be undone. You paste your own Anthropic API key once per phone; it is stored encrypted on that phone only, and a monthly spending cap stops Claude features when reached.
- **Family sync (Sync tab):** sign in, create a family, share the invite code, and everyone's phone shows the same calendar.
- **Family locations (Family tab):** each person picks who their phone belongs to and opts in to sharing. Save places (Home, School) while standing there; arrivals and departures show up in the Recent list and as notifications on phones that opted in to alerts. Alerts depend on the phone's sharing notification staying on (set Firefly's battery use to Unrestricted); there is no always-on server push, which would need Firebase's paid Blaze plan.

## Look and feel

The **Look** tab changes the whole app on that phone: light, dark or match the phone, four soft palettes (Hearth, Seaside, Blossom, Meadow), your own main color, four font pairings and four text sizes. Each family member's color is picked from 16 swatches or mixed with a hue slider on the People tab.

Fonts bundled in `app/src/main/res/font` are Nunito, Fredoka, Lora and Atkinson Hyperlegible, all under the SIL Open Font License.

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
