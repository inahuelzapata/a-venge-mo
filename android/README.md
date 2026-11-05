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

## Appearance: the iOS captures are the source of truth

The app looks like the iOS app, screen for screen. **The iOS captures in
[`docs/design/ios-captures/`](../docs/design/ios-captures/) are the source of
truth for appearance**, not this code and not the iOS source. The numbers to
build to (colours, spacing, type roles, tab bar, status bar, radii) are in
[`docs/design/visual-conventions.md`](../docs/design/visual-conventions.md),
in `ui/theme/` (`Brand`, `Dimens`, `Type`) here. To check the match, look at the
side-by-side images and 50% overlays in
[`docs/design/comparisons/`](../docs/design/comparisons/).

Only these differences are allowed, because the platform forces them (the
conventions note explains each one):

1. System-drawn status bar content (clock, signal, battery). Its background and dark tint are matched.
2. The system navigation/gesture area.
3. System back behaviour: back from Me or Pay/Request returns to Home, and back from Home leaves the app.
4. Text rendering: Apple's SF Pro may not be bundled, so the app ships **Inter** (SIL OFL 1.1, licence in `app/src/main/assets/licenses/Inter-OFL.txt`) at the iOS sizes, weights and line heights.
5. The OS keyboard.
6. Icon glyph shapes (SF Symbols can't ship): look-alike vectors of the same size and position.
7. No backdrop blur under the tab bar.

Anything else that differs from a capture is a bug. The app is always light
(it ignores the system dark theme, as iOS does), shows no ripples or elevation
changes, and lays out against at least the iPhone's safe area (62 dp top,
34 dp bottom) so headers and the tab bar line up with an iPhone placed beside it.

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

The default is the **hosted** Firebase project: the phone talks to Firestore
over the internet, on any network (Wi-Fi or mobile data), with no laptop
involved. Full walkthrough: [Integration & Setup Guide](../docs/integration-setup-guide.md).

1. **Config files**: `cp firestore.properties.example firestore.properties` and
   set `FIREBASE_PROJECT_ID`. Put the project's `google-services.json` at
   `app/google-services.json` (next sections).
2. **Database ready**: rules and indexes deployed and the data seeded, from
   [`backend/`](../backend): `npm run deploy:firestore`, then `npm run seed`.
3. **Connect the phone**: enable Developer options → USB debugging, plug it in,
   accept the prompt.
4. **Install**: open **this `android/` folder** in Android Studio, select the
   phone, press Run. Or from a terminal in `android/`: `./gradlew installDebug`
   (needs `ANDROID_HOME`, or a `local.properties` with `sdk.dir`; Android Studio
   writes that file for you).
5. Open **Split Party** and sign in as `maxi`, `annie` or `christophe`. The login
   screen shows which database the build points at.

Building leaves `git status` clean: `firestore.properties`, `local.properties`,
`google-services.json` and build output are git-ignored.

## Firestore target: the one place to switch

`android/firestore.properties` (git-ignored). It alone decides where the app
connects: one build, no product flavours, no code change to switch. Same keys as
iOS and `backend/.env`; see [Firestore target](../docs/firestore-target.md).

**Hosted** (default):

```
FIREBASE_TARGET=hosted
FIREBASE_PROJECT_ID=your-firebase-project-id   # the same id as backend/.env
FIRESTORE_EMULATOR_HOST=
```

Hosted needs `app/google-services.json` **from that same project** (below), and
the composite indexes from `backend/firestore.indexes.json` deployed with
`npm run deploy:firestore` (the Me tab query uses one).

**Emulator** (opt-in, offline; started from `backend/` with `npm run emulators`):

```
FIREBASE_TARGET=emulator
FIREBASE_PROJECT_ID=demo-split-party      # must match backend/.env
FIRESTORE_EMULATOR_HOST=192.168.1.20:8080 # laptop LAN IP: ipconfig getifaddr en0
```

A physical phone can't use `127.0.0.1` and must be on the **same Wi-Fi** as the
laptop. An Android Studio emulator (AVD) reaches the laptop at `10.0.2.2:8080`.
Debug builds allow cleartext HTTP so the phone can talk to the emulator. On
**Android 17+** the app first asks to **allow local network** (shown by the
system as "nearby devices"): tap Allow, or the phone can't reach the laptop.
**No `google-services.json` is needed** for this target.

**Rebuild and reinstall after any change** (the values are compiled into
`BuildConfig`). If something is missing or inconsistent (no target, the template
placeholder project id, an emulator target with no host, hosted with no
`google-services.json`, or a config file from a different project), the app
opens on a **Setup required** screen that says what to fix.

## Firebase config file (hosted)

`google-services.json` is **never committed**. Download it from the Firebase
console (Project settings → Your apps → Android app `com.splitparty.android`)
and put it at `app/google-services.json`: see
[`app/GoogleServices.README.md`](app/GoogleServices.README.md). The Google
Services Gradle plugin is applied only when that file exists, so emulator
builds without it still work.

## Architecture

Single `:app` module, layered by package (under `app/src/main/java/com/splitparty/android/`):

```
ui/      Compose screens + ViewModels (one package per feature)
  └─ uses domain/ use cases
domain/  models, repository interfaces, use cases, LiveState (no Android or Firebase imports)
  └─ implemented by data/
data/    Firestore, DataStore and ConnectivityManager implementations
di/      Koin modules wiring the three layers
```

- **DI: Koin** (`di/AppModules.kt`, started in `SplitPartyApplication`). Screens get
  ViewModels with `koinViewModel()`; ViewModels that need the signed-in user take it
  as a parameter: `koinViewModel { parametersOf(userId) }`.
- **Unidirectional data flow**: each ViewModel exposes one `StateFlow<XUiState>`
  (immutable) and, where the screen has input, one `onAction(XAction)` entry point.
- **Route / Screen split**: `XRoute` gets the ViewModel and collects state;
  `XScreen(state, onAction, modifier)` is stateless, has `@Preview`s, and is what
  UI tests render.
- **Navigation**: type-safe `@Serializable` destinations (`ui/shell/ShellRoutes.kt`).
- **Live data**: repositories emit `LiveEvent`s; `toLiveState()` (`domain/live`) turns
  them into Loading / Ready / Unreachable using connectivity plus a 5 s server timeout.

| Path | What |
| ---- | ---- |
| `di/AppModules.kt` | All Koin bindings |
| `data/firebase/` | `FirestoreTarget` (BuildConfig), `FirebaseSetup` (emulator or hosted), schema mapping |
| `data/` | `FirestoreSplitPartyRepository`, `DataStoreSessionRepository`, `AndroidConnectivityObserver` |
| `domain/model`, `domain/repository`, `domain/usecase`, `domain/live` | Models (money as `Long` cents), interfaces, use cases, live state |
| `ui/root` | App root: setup / login / shell, session ViewModel |
| `ui/shell` | Bottom bar and tab navigation (back from a tab returns to Home) |
| `ui/login`, `ui/home`, `ui/me`, `ui/pay`, `ui/gate`, `ui/setup` | Feature screens |
| `ui/components`, `ui/theme`, `format/` | Shared feed row, cards and pill button; palette/spacing/type tokens (`Brand`, `Dimens`, `Type`); money and time formatting |
| `app/src/main/res/drawable/ic_*.xml`, `res/font/` | SF Symbol look-alike vector icons; the bundled Inter font |

### Adding a screen

1. Write `XUiState`/`XAction` and `XViewModel` (use cases in, `StateFlow` out).
2. Write `XRoute` (koinViewModel) and a stateless `XScreen` with previews.
3. Bind the ViewModel in `viewModelModule` (and any new use case in `domainModule`).
4. Add a `@Serializable` destination and a `composable<…>` entry if it's a tab.
5. Add a ViewModel test with the fakes in `app/src/sharedTest/.../testing/`, and a
   screenshot test per state, then run `recordPaparazziDebug`.

## Tests and code quality

```
./gradlew check            # ktlint, detekt, Android lint, all unit + UI tests, screenshot goldens
./gradlew testDebugUnitTest
./gradlew ktlintFormat     # auto-fix formatting
./gradlew recordPaparazziDebug   # re-record screenshot goldens after an intended UI change
./gradlew verifyPaparazziDebug   # compare against the goldens (also part of check)
./gradlew connectedDebugAndroidTest   # on-device (Espresso) tests: needs an emulator or phone (adb)
```

- **Unit tests (JVM)**: use cases, `LiveState`, ViewModels and the Koin graph
  (`KoinModulesTest`). They use hand-written fakes (`app/src/sharedTest/.../testing/Fakes.kt`,
  shared with the on-device tests), Turbine and
  `kotlinx-coroutines-test` with `MainDispatcherRule`.
- **Compose UI tests (Robolectric, JVM)**: Login, Home and shell navigation (with the
  real ViewModels and fake data sources). No device needed.
- **Screenshot tests (Paparazzi, JVM)**: every stateless screen in each of its states
  (`app/src/test/.../screenshots/`), on the reference iPhone's geometry (402 × 874 dp at
  3×), plus a 1.5× font-size pass. `ParityScreenshotTest` renders each iOS capture's
  state at full resolution; `python3 docs/design/capture/compare.py` turns those into
  the comparison images (rerun it after re-recording). Goldens live in `app/src/test/snapshots/images/` and are committed; on a
  mismatch the expected, actual and diff images are in `app/build/paparazzi/failures/`.
  After an intended UI change, run `recordPaparazziDebug`, look at the new PNGs, and
  commit them with the change. Snapshot names come from the test class and method,
  so renaming a test means re-recording. New screens: add a test that calls
  `paparazzi.screen { XScreen(state, …) }` for each state.
- **On-device tests (Espresso + Compose, `app/src/androidTest/.../e2e/`)**: the Split
  Party total slice on a real emulator or phone. Each test hosts the real signed-in
  shell (`MainShell`, real ViewModels) over the shared fakes, so no Firebase is needed:
  keypad entry and its limits, tip chips, system back writing nothing, a member's
  live share, the receipt scan (real runtime permission, CameraX on the device's
  camera, scripted recognition), privacy (no gallery image or app file after a scan),
  the bundled ML Kit model reading a rendered receipt's Total within the 2.5 s budget,
  and removing and leaving. They run under the Android Test Orchestrator with
  `clearPackageData`, so every test starts without the camera permission, and with
  system animations off. "Denied in settings" is set with `pm set-permission-flags …
  user-fixed` (revoking would kill the app process). Run one class with
  `-Pandroid.testInstrumentationRunnerArguments.class=com.splitparty.android.e2e.ReceiptScanE2ETest`;
  with several devices attached, pick one with `ANDROID_SERIAL`. Not covered: live
  Firestore, several phones at once, a printed receipt (these stay manual checks).
- **ktlint** (Android Studio style plus Compose rules, `.editorconfig`) and **detekt 2**
  (`config/detekt/detekt.yml`, Compose rules). detekt is a 2.0 alpha because 1.23 can't
  run on the Java 25 Gradle JVM.
