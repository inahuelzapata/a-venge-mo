# android — Split Party Android app

Native Kotlin + Jetpack Compose app, minimum API 26. Self-contained: the
Gradle build references nothing outside this folder. It implements the data
contract in [`docs/firestore-schema.md`](../docs/firestore-schema.md) (linked,
never copied).

Today it shows a placeholder screen with the configured Firestore target. The
app-shell Work Order builds on this project.

## Layout

| Path | What |
| ---- | ---- |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | Gradle setup and version catalog |
| `app/` | The app module (`com.splitparty.android`) |
| `firestore.properties.example` | Template for your git-ignored `firestore.properties` |

## Run on a physical Android phone (API 26+)

Requires Android Studio (or a JDK 17+ and the Android SDK with platform 35).

1. `cp firestore.properties.example firestore.properties` and set the target
   (below).
2. Enable USB debugging on the phone and connect it.
3. Open **this `android/` folder** in Android Studio, select the phone, press
   Run. Or from a terminal: `./gradlew installDebug` (needs `ANDROID_HOME` or
   a `local.properties` with `sdk.dir`; Android Studio writes that for you).

Without `firestore.properties` the app still builds; the screen says the
target is not configured.

## Firestore target: the one place to switch

`android/firestore.properties` (git-ignored):

```
FIREBASE_TARGET=emulator                  # or hosted
FIREBASE_PROJECT_ID=demo-split-party      # or your hosted project id
FIRESTORE_EMULATOR_HOST=192.168.1.20:8080 # laptop LAN IP; empty for hosted
```

Rebuild after changing it. The values end up in `BuildConfig`; code reads them
via `FirestoreTarget.fromBuildConfig()`. Debug builds allow cleartext traffic
so the phone can reach the emulator. The keys and the demo setup are the same
as iOS and the backend: see [Firestore target](../docs/firestore-target.md).
The emulator itself is started from [`backend/`](../backend); this app never
runs its own.

## Firebase config file

`google-services.json` is **not committed**. Download it from the Firebase
console (Android app `com.splitparty.android`) and put it at
`app/google-services.json`. Steps:
[`app/GoogleServices.README.md`](app/GoogleServices.README.md). Until it is
there, the placeholder screen shows a warning.
