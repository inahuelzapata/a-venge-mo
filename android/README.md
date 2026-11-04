# android — Split Party Android app

Native Kotlin + Jetpack Compose app, minimum API 26 (Android 8.0). Self-contained:
the Gradle build references nothing outside this folder. It reads exactly the
collections and fields in [`docs/firestore-schema.md`](../docs/firestore-schema.md)
(linked, never copied).

What it does today (PoC-1 to PoC-6):

- **Login**: one username field, no password. `maxi`, `annie` and `christophe`
  sign in (case and surrounding spaces are ignored); anything else shows an
  inline error. The phone stays signed in across restarts until **Log out** on Me.
- **Home**: the live feed (`transactions`, newest first, all users). New rows
  appear at the top with a short fading highlight. Shows an empty state, and an
  error state when the database can't be reached (it recovers by itself). A
  banner slot above the feed is reserved for the merchant/Join banners.
- **Me**: display name, avatar, live mock balance and the user's own payments.
- **Pay/Request**: placeholder tab.

The look follows the reference screenshots in
[`docs/design/`](../docs/design) (shared with the iOS app).

## Run it on a phone, step by step

You need a recent Android Studio (it includes the SDK and a Java 25 runtime), or
any JDK 17+ plus the Android SDK (platform 37) for terminal builds.

The build runs on **Java 25**, pinned in `gradle/gradle-daemon-jvm.properties`
(Gradle Daemon JVM criteria). Android Studio and `./gradlew` both read it: Gradle
uses an installed Java 25 or downloads one into `~/.gradle/jdks` on the first
build, so there is no Gradle JDK setting to pick by hand. The JDK that *starts*
`./gradlew` only needs to be 17+ (Android Studio's bundled one works:
`export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`).
The app itself is compiled to Java 21 bytecode; that is what runs on phones.

Toolchain: Gradle 9.8.2, Android Gradle Plugin 9.5 with built-in Kotlin 2.4,
compile/target SDK 37. To move the JVM later, run
`./gradlew updateDaemonJvm --jvm-version=<N>` and commit the file it rewrites.

1. **Choose the database** (next section): `cp firestore.properties.example firestore.properties`
   and edit it.
2. **Start that database with data in it.** For the emulator, run from
   [`backend/`](../backend): `npm run emulators`, then `npm run seed` in another
   terminal. For hosted, seed it as the backend README says.
3. **Connect the phone**: enable Developer options → USB debugging, plug it in,
   accept the prompt. For the emulator target the phone must be on the **same
   Wi-Fi** as the laptop.
4. **Install**: open **this `android/` folder** in Android Studio, select the
   phone, press Run. Or from a terminal in `android/`: `./gradlew installDebug`
   (needs `ANDROID_HOME`, or a `local.properties` with `sdk.dir`; Android Studio
   writes that file for you).
5. Open **Split Party** and sign in as `maxi`, `annie` or `christophe`. The login
   screen shows which database the build points at. On **Android 17+** with the
   emulator target, the app first asks to **allow local network** (shown by the
   system as "nearby devices"): tap Allow, or the phone can't reach the laptop.

Building leaves `git status` clean: `firestore.properties`, `local.properties`,
`google-services.json` and build output are git-ignored.

## Firestore target: the one place to switch

`android/firestore.properties` (git-ignored). Same keys as iOS and
`backend/.env`; see [Firestore target](../docs/firestore-target.md).

**Emulator** (started from `backend/`):

```
FIREBASE_TARGET=emulator
FIREBASE_PROJECT_ID=demo-split-party      # must match backend/.env
FIRESTORE_EMULATOR_HOST=192.168.1.20:8080 # laptop LAN IP: ipconfig getifaddr en0
```

A physical phone can't use `127.0.0.1`. An Android Studio emulator (AVD) reaches
the laptop at `10.0.2.2:8080`. Debug builds allow cleartext HTTP so the phone can
talk to the emulator. **No `google-services.json` is needed** for this target.

**Hosted**:

```
FIREBASE_TARGET=hosted
FIREBASE_PROJECT_ID=your-firebase-project-id
FIRESTORE_EMULATOR_HOST=
```

Hosted needs `app/google-services.json` **from that same project** (below), and
the composite index from `backend/firestore.indexes.json` deployed (the Me tab
query uses it).

**Rebuild and reinstall after any change** (the values are compiled into
`BuildConfig`). No code change is needed to switch. If something is missing or
inconsistent (no target, an emulator target with no host, hosted with no
`google-services.json`, or a config file from a different project), the app
opens on a **Setup required** screen that says what to fix.

## Firebase config file (hosted only)

`google-services.json` is **never committed**. Download it from the Firebase
console (Android app `com.splitparty.android`) and put it at
`app/google-services.json`: see [`app/GoogleServices.README.md`](app/GoogleServices.README.md).
The Google Services Gradle plugin is applied only when that file exists, so
builds without it still work.

## Code map

| Path (under `app/src/main/java/com/splitparty/android/`) | What |
| ---- | ---- |
| `FirestoreTarget.kt`, `data/FirebaseSetup.kt` | Reads `BuildConfig`, connects to the emulator or hosted project |
| `data/Repository.kt`, `data/Models.kt` | Schema queries (feed, Me, users) as live Flows; money as `Long` cents |
| `data/SessionStore.kt` | Signed-in username in DataStore |
| `data/NetworkMonitor.kt`, `ui/LiveState.kt` | Offline/unreachable detection for the error states |
| `ui/MainShell.kt` | Bottom bar and tab navigation (back from a tab returns to Home) |
| `ui/home`, `ui/me`, `ui/pay`, `ui/login`, `ui/components` | Screens and the shared feed row |
| `format/` | Integer-cents money formatting, relative times |
| `app/src/main/res/drawable/ic_*.xml` | Material icons as local vectors (the Compose icon packs are discontinued) |

Unit tests: `./gradlew testDebugUnitTest`.
