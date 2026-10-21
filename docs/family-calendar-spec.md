# Firefly: Family Calendar & Tracker, Product Spec (v0.4)

*Draft v0.3, 2026-10-20 (Android app; added Claude assistant in v0.2). Owner: Olan. Status: for review before any prototype.*

## 1. What it is

An Android app: a Skylight-style shared family calendar and tracker. One place that pulls in everyone's schedules (including Olan's work Outlook/Teams calendar), shows them in clear daily, weekly, monthly and yearly views, and makes conflicts between family members obvious before they become a problem.

## 2. Goals

1. **One source of truth** for the whole family's time: work, school, activities, appointments, chores, and big dates.
2. **Get work events in without fighting IT.** Sync when allowed; when not, make manual entry and "snap a photo / screenshot and let Claude fill it in" fast.
3. **Clear views at every zoom level**: today, this week, this month, this year.
4. **Per-person clarity**: each family member has a color and their own view, and overlaps (e.g., two people needed in two places, nobody available for a pickup) are flagged.

**Non-goals for v1:** replacing Outlook as a work tool, two-way editing of work meetings, meal planning, photo frames, a dedicated hardware display (an Android tablet on the wall in kiosk mode covers this), iPhone and web versions (the backend keeps those possible later).

## 3. Users

| Person | Role | Needs |
|---|---|---|
| Olan (parent, admin) | Sets up family, connects calendars | See work + family together, catch conflicts early |
| Partner / other adult | Editor | Add events, claim pickups, see everyone |
| Kids | Viewer (optionally editor) | See their own day, chores, activities |
| Wall display / shared Android tablet | Read-only kiosk | Today + week at a glance |

Each person has: name, color, avatar, optional linked calendars, and a "needs an adult" flag (for kids, so their events require someone to drive/attend).

## 4. Getting events in

Every event records its **source** (Graph sync, ICS feed, Google, phone calendar, manual, scanned) so it's clear what will update automatically and what won't.

### 4.1 Outlook / Teams sync: realistic options

Teams meetings live in the Outlook (Exchange) calendar, so syncing Outlook covers Teams. Options, from best to most fallback:

| Option | How it works | Pros | Limits |
|---|---|---|---|
| **A. Microsoft Graph API (OAuth)** | User signs in with their Microsoft account; app reads calendar via Graph (`Calendars.Read`), with change notifications or delta queries for near-real-time updates | Full detail, near real time, handles recurrence and cancellations | Work/school tenants often **require admin consent** for third-party apps. If IT blocks consent, this fails. Personal Outlook.com accounts work fine. |
| **B. Published ICS link** | In Outlook on the web: Settings > Calendar > Shared calendars > Publish a calendar. Choose "Can view when I'm busy" or "Can view all details", copy the ICS link, paste into the app | No app registration or admin consent; one-time setup | Many orgs **disable calendar publishing**. Read-only. Updates on a poll (every 15–60 min; Outlook's own feed refresh can lag hours). "Busy only" mode hides titles. |
| **C. Phone calendar (Android)** | If the Outlook Android app is set to sync its calendar to the phone (Outlook > Settings > account > Sync calendars), the app reads it from Android's calendar store with the `READ_CALENDAR` permission | No IT consent or publishing needed; works offline; updates as Outlook syncs | Company device policies (Intune) often block this setting for work accounts. Only covers calendars on this phone; read-only. |
| **D. Forward/share to a personal account** | Share the work calendar with a personal Outlook.com or Google account, then sync that account | Sometimes allowed when publishing isn't | External sharing is also commonly blocked; may violate policy |
| **E. Manual entry** | Quick-add form, recurring events supported | Always works | Effort; can drift from reality |
| **F. Scan & input (Claude, see section 7)** | Take a photo or pick a screenshot of Outlook/Teams week view, a photo of a school schedule, a PDF flyer; Claude extracts events and shows them for confirmation | Works even under strict IT policy; great for school/sports schedules too | Needs a review step; recurring patterns may need a nudge; no auto updates |

**Policy note:** before using B, C or D on a work calendar, check company policy. "Busy only" publishing is often an acceptable middle ground: the family sees "Olan: busy 9–11" without meeting details.

**Recommended path:** try A first; if admin consent is blocked, try C (phone calendar) or B (busy-only is enough for conflict detection); otherwise fall back to E/F. The app should walk the user through this as a short wizard and remember which path worked.

### 4.2 Other sources

- Google Calendar for a partner or kids' school accounts: read straight from the phone's calendar store if the account is on the device, or via Google sign-in for accounts on other family members' phones.
- Any ICS URL (school district, sports league, holidays).
- Built-in holiday and observance calendars by country.

### 4.3 Scan & input flow

1. User taps **Scan** and takes a photo with the camera, picks a screenshot or PDF, or uses Android's **Share** menu from any app (Teams, Outlook, Gallery, a school email) to send it straight to the app.
2. Claude extracts: title, date, start/end time, location, who it's for (guessed from context or asked), recurrence.
3. App shows a **review table**: each row editable, confidence flagged, duplicates of existing events highlighted.
4. User confirms; events are saved with source = "scanned" and a link to the original image.
5. Re-scanning a newer screenshot of the same week updates/replaces matching events instead of duplicating them.

## 5. Views

All views filter by person (toggle family members on/off) and use each person's color.

| View | Purpose | Shows |
|---|---|---|
| **Daily** | "What's happening today?" | Time-grid with one column per family member (swimlanes), so overlaps across people are visible side by side. Chores/to-dos for today. Next-up banner. |
| **Weekly** | Plan the week | 7-day grid, per-person rows or columns. Conflict markers on each day. Summary of who's driving what. |
| **Monthly** | See busy stretches | Month grid with colored dots/bars per person, day density, conflict badges. Tap a day to open Daily. |
| **Yearly (major dates)** | Big-picture planning | 12-month strip of only **major** events: birthdays, anniversaries, holidays, school breaks, trips, renewals/deadlines. Events are marked "major" manually or by type. |

Additional: a **Person view** ("Maya's week") showing only one member's events plus anything that requires an adult for them, the kiosk/wall mode on an Android tablet (Today + next 6 days, large type, auto-refresh, screen kept on), and **home-screen widgets** (today's agenda per person, next conflict).

## 6. Conflict detection

The core differentiator. Conflicts are computed continuously and shown as badges in every view plus a **Conflicts** list.

| Type | Rule | Example |
|---|---|---|
| **Personal double-booking** | Same person has overlapping events | Olan: Teams meeting 3–4pm and dentist 3:30pm |
| **Coverage gap** | A "needs an adult" event has no available adult (every adult is busy) | Kid's practice 5pm; both parents in meetings |
| **Transport/travel squeeze** | Back-to-back events in different locations with not enough travel time (configurable buffer, later: maps estimate) | Work ends 5:00 downtown, pickup 5:10 across town |
| **Shared resource** | Same car / same room booked twice (optional) | Both adults need the car |
| **Soft conflict** | Overlap with "tentative" or "busy-only" work blocks | Flagged amber rather than red |

Each conflict shows who's involved, why, and quick actions: assign a different adult, mark as resolved/accepted, or message the family.

Severity: **red** (hard overlap / no coverage), **amber** (tentative, tight buffer), **resolved** (acknowledged, kept visible but muted).

## 7. Claude assistant (your own API key)

Claude is built into the app through the Anthropic API, using Olan's personal API key. It does three jobs:

1. **Scan & input.** Screenshots, photos and PDFs become events (the flow in 4.3). Typed or pasted text works too: "Maya has soccer Tuesdays 5–6 at Riverside until Dec 12" becomes a recurring event.
2. **Audit.** On demand, and optionally on a weekly schedule, Claude reviews the calendar and proposes fixes to keep it concise:
   - duplicates (the same event from a sync and a scan, or entered twice)
   - stale or past items still open (unchecked chores, events with no end date)
   - events missing a person, location or "needs an adult" flag
   - inconsistent naming ("Dr appt", "Dentist", "dentist - Maya") merged to one style
   - recurring series that look wrong (a school event repeating through summer break)
   - unresolved conflicts with a suggested fix (e.g., "Partner is free; assign pickup?")
3. **Ask.** Plain-language questions: "Who's free Thursday at 5?", "What's coming up for Maya this month?"

**Claude never changes the calendar on its own.** Every scan and every audit finding is shown as a proposed change (add, edit, merge, delete) that you approve or reject, one at a time or in bulk. Every applied change is logged with its source so it can be undone.

**API key handling**
- **As built (v0.4):** you paste the key once per phone in the Claude tab; it is encrypted with the Android Keystore and stored only on that phone, and Claude calls go straight from the phone to Anthropic. It is never built into the APK, never synced to Firestore and never sent to other family members. (The earlier plan to keep it on a backend was dropped: the free Firebase plan has no server code.)
- A monthly spending cap and a usage counter in Settings; Claude features switch off cleanly if the key is missing, invalid or over the cap, and the rest of the app keeps working.
- Model choice: a capable vision model for scans and audits, a cheaper fast model for quick-add text. Configurable in Settings.
- Only the data a task needs is sent (the image being scanned, or the date range being audited). Busy-only work blocks are sent as "busy", without details.

## 8. Tracking beyond events

- **Chores & to-dos** per person, with recurrence and checkmarks (Skylight-style).
- **Countdowns** to major dates (shown on yearly view and kiosk).
- **Lists** (shopping, packing) as a later add.

## 9. Notifications

- Daily digest (morning): today per person + any conflicts.
- New conflict alert when a sync or new event creates one.
- Weekly preview (Sunday evening).
Delivery: Android push notifications (Firebase Cloud Messaging), with optional email.

## 10. Platform & architecture (proposal)

- **Native Android app in Kotlin with Jetpack Compose** (recommended). Android-only means no need for a cross-platform framework, and native gives the best access to the calendar store, camera, share menu, widgets and notifications. Flutter or React Native stay options if an iPhone version becomes a goal.
- **Phone and tablet layouts** from the same app: phone for day-to-day use, tablet for the kiosk/wall view.
- **Offline-first:** events cached on the device (Room database) so views and conflicts work without signal; changes sync when back online.
- **Small backend** shared by the family: accounts and family membership, events, sources, conflicts, the encrypted Claude key, and background jobs for polling ICS feeds and receiving Graph change notifications. A managed option (e.g., Firebase or Supabase) keeps this light.
- Phone-calendar sources (option C, Google on the device) are read on the phone and uploaded to the family's shared calendar, since only that device can see them. Android's WorkManager runs these syncs in the background.
- Recurrence handled with standard RRULE (iCalendar) so imported and manual events behave the same.
- Claude features (scan, audit, ask) run on the backend through the Anthropic API with the user's own key, always followed by human review (see section 7).
- Distribution: install directly (APK) for the family at first; Google Play (internal testing track) when it's ready to share.
- Android permissions requested only when a feature needs them: calendar (phone-calendar sync), camera (scan), notifications.

## 11. Privacy & security

- Work event details stored only if the source provides them; "busy-only" mode respected.
- Family data private to the family account; per-member permissions (admin / editor / viewer).
- OAuth tokens encrypted at rest; easy disconnect per source.
- Calendar and camera permissions are optional; the app works without them using manual entry and other sources.
- Uploaded screenshots retained only as long as the user wants (default: delete after confirmation).

## 12. Milestones

1. **M1 – Core calendar:** Android app shell, people, manual events, daily/weekly/monthly views, per-person colors, local storage.
2. **M2 – Conflicts:** double-booking + coverage gap detection, conflicts list.
3. **M3 – Import:** backend and family sharing, phone-calendar sync, ICS feeds (incl. Outlook publish link), then Graph OAuth.
4. **M4 – Claude assistant:** API key setup, camera/screenshot/share-menu/text scan with review table, then the audit.
5. **M5 – Yearly view, chores, notifications, widgets, tablet kiosk mode.**

## 13. Open questions

1. Who's in the family and which of them have their own calendars (Google, school, work)?
2. Does your work tenant allow third-party app consent or calendar publishing? (Quick test: try publishing your calendar in Outlook on the web.)
3. Is a wall-mounted Android tablet important for v1, or phone first?
4. Do the other family members have Android phones too? (If anyone uses an iPhone, they would need a different way in, such as a later web view.)
5. In the Outlook app on your phone, is "Sync calendars" available for your work account? (If yes, option C may be the easiest sync.)
6. Should kids be able to edit, or view only?
7. Which "major" date types matter most for the yearly view?

## 14. Added in v0.4 (as built)

- **Name and icon:** Firefly; rose-gold F with a small yellow firefly on soft blue.
- **Sync:** Firebase Auth (email/password) plus Firestore, invite-code family joining, offline-first with a local-only fallback when Firebase is not configured.
- **Import:** published-ICS links (with recurrence) and phone calendars, replace-by-source.
- **Yearly view** of major and yearly-repeating events.
- **Family locations:** opt-in location sharing from a foreground service to Firestore, saved places with radius, arrival and leave events, local notifications on phones that opted in to alerts. True background push (FCM) would need Cloud Functions on Firebase's Blaze plan and is deferred.
- **Not yet built:** Microsoft Graph sign-in, chores/to-dos, daily digest notifications, widgets, tablet kiosk mode, desktop app.
