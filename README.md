<div align="center">

<img src="docs/icon.png" width="112" alt="Resurge app icon">

# Resurge

**A calm AI health coach for Android.**<br>
Habits, sleep, movement, food and weight in one daily picture, with a coach that reads it and talks back.

[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-4A41D9.svg)](LICENSE)
![Platform: Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?logo=jetpackcompose&logoColor=white)

</div>

<p align="center">
  <img src="docs/screenshots/today.png" width="19%" alt="Today: the daily tide, habits and health tiles">
  <img src="docs/screenshots/fitness.png" width="19%" alt="Fitness: the week's active minutes and workouts">
  <img src="docs/screenshots/food.png" width="19%" alt="A scanned meal with calories, macros and the coach's question">
  <img src="docs/screenshots/weight.png" width="19%" alt="Weight trend with a custom period selected">
  <img src="docs/screenshots/coach.png" width="19%" alt="Chatting with the coach">
</p>

## What it does

Resurge pulls your day together and keeps it light. Everything you do fills one daily **tide**; the coach notices patterns and tells you what to do about them.

- **Today.** The tide fills as you finish habits, hit your sleep and step goals and log meals. A habit streak, a weekly activity goal and one quiet "high tide" moment a day keep it rewarding without nagging.
- **Habits.** One-tap check-ins, streaks with freezes for off days, and a calendar of every habit's history.
- **Fitness.** Workouts from Health Connect and ones you log yourself (runs, rides, strength sets, swims and more), merged and de-duplicated, with weekly active minutes.
- **Food.** Point the in-app camera at your plate, pick a gallery photo or describe the meal. The coach identifies each food, estimates portions and nutrition, and asks one question when it would change the numbers. Fix anything in a tap before logging.
- **Weight.** Weigh-ins are smoothed into a trend, so one salty dinner doesn't erase a good week. Measure the change over any period, set a goal and pace, and import your history from Health Connect.
- **Snapshots.** Progress photos you take when you like, lined up against a faint copy of the last one. They're end-to-end encrypted on the phone before backup, and every few weeks (3 days to 8 weeks, your choice) a reel of them arrives on its own, like a photos app's memories.
- **Coach.** Chat with a coach that can see your recent data, get a short insight after each workout or night of sleep, and a weekly report on Saturday. Choose a warm *Friend* or a no-nonsense *Commander*.

Health data is read through [Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect), so it works with Fitbit, Samsung Health, Garmin, Google Fit and most other apps that write there.

## How it's built

An Android app in Kotlin and Jetpack Compose, backed by Firebase. The AI features run in Cloud Functions that call Claude, so no API key ever ships in the app.

```
app/                 Entry point, navigation, notifications
core/
  model/             Plain Kotlin models and the maths (tide, streaks, weight trend)
  data/              Firestore repositories, DataStore preferences, background sync
  healthconnect/     Health Connect reads (and optional weight writes)
  designsystem/      "Tide" design system: tokens, type, components
feature/
  dashboard/         Today, health metric details
  habits/            Habits, streaks, habit editor
  fitness/           Workouts, activity picker, workout editor
  food/              Meal scanner (CameraX), estimate review, food log
  weight/            Weight trend, weigh-ins, goal
  progress/          Progress photos (CameraX), before/after, reels
  coach/             Chat and insights
  profile/           Settings
  auth/              Google sign-in, onboarding
functions/           Cloud Functions (TypeScript): coach chat, meal analysis, insights
firestore.rules      Per-user security rules for the `resurge` database
storage.rules        Resurge's block of the shared bucket's Storage rules (progress photo ciphertext)
```

| | |
|---|---|
| UI | Jetpack Compose with [Compose Unstyled](https://composeunstyled.com), Tabler icons, Haze for blur |
| Architecture | MVVM, Hilt, Kotlin coroutines and Flow, Navigation Compose |
| Data | Cloud Firestore (offline cache), DataStore, WorkManager |
| Health | Health Connect client 1.1 |
| Camera | CameraX |
| Backend | Firebase Auth (Google), Cloud Functions (Node 22), Cloud Messaging, App Check |
| AI | Claude via the Anthropic SDK: Haiku for quick chat and insights, Sonnet for deeper chat and meal photos |
| Tests | JUnit, MockK, Robolectric, [Roborazzi](https://github.com/takahirom/roborazzi) screenshot tests, Vitest |

## Getting started

### Requirements

- Android Studio (latest stable) with JDK 17 or newer
- An Android 8.0+ device or emulator, with Health Connect for health data
- Node.js 22 and the [Firebase CLI](https://firebase.google.com/docs/cli) for the backend
- A Firebase project on the Blaze plan (Cloud Functions need it) and an [Anthropic API key](https://console.anthropic.com)

### 1. Set up Firebase

1. Create a Firebase project and add an Android app with package name `com.resurgeai.app.debug` (debug builds) and, if you'll build release, `com.resurgeai.app`.
2. Add your debug signing SHA-1 to the app (`./gradlew :app:signingReport`), then enable **Google** as a sign-in provider in Authentication.
3. Download `google-services.json` into `app/`. It's gitignored; every contributor uses their own.
4. Create a Firestore database with the database ID **`resurge`** (the app and rules use this named database).
5. Point the CLI at your project: `firebase use --add`.

### 2. Deploy the backend

```sh
cd functions
npm install
firebase functions:secrets:set ANTHROPIC_API_KEY   # paste your key
cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions
```

App Check is installed in the app but not enforced by the functions (`ENFORCE_APP_CHECK` in `functions/src/coach/chat.ts`). Debug builds use the App Check debug provider; register the debug token from Logcat in the Firebase console if you turn enforcement on.

### 3. Run the app

```sh
./gradlew :app:installDebug
```

Or open the project in Android Studio and run the `app` configuration. Sign in with Google, finish onboarding, and connect Health Connect from Today.

Without the backend you can still use habits, workouts, weight, water and Health Connect data; the coach and meal logging need the functions.

## Tests

```sh
./gradlew testDebugUnitTest            # unit and screenshot tests
./gradlew verifyRoborazziDebug         # compare screens against the recorded references
./gradlew recordRoborazziDebug         # re-record after an intentional UI change
cd functions && npm test               # backend tests
```

Screenshot references live next to each feature in `src/test/screenshots/`. Review the image diff before committing a re-recorded screen.

## Privacy

Your data is stored under your account in Firestore and protected by per-user security rules. Meal photos are sent to the meal analysis function, which passes them to Claude; only a small thumbnail is stored. From Health Connect, only daily summaries and finished workouts and nights of sleep are uploaded, for the coach and its insights. Daily limits on chat turns, insights and meal scans keep API costs bounded (`functions/src/lib/quota.ts`).

## Contributing

Issues and pull requests are welcome. Before opening a PR:

1. Run `./gradlew testDebugUnitTest` and, for backend changes, `npm test` in `functions/`.
2. Re-record screenshots for UI changes and include the updated images.
3. Follow the existing style: small composables, design tokens from `Resurge.colors` and `Resurge.type` instead of literal colors, and plain, specific copy.

## License

Resurge is free software, released under the [GNU General Public License v3.0](LICENSE). You can use, study, change and share it; if you distribute a modified version, you must release its source under the same license.
