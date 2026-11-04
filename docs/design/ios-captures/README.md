# iOS reference captures

These captures define how the app looks on **both** platforms. The Android app is
styled to match them. Measured values are in
[`../visual-conventions.md`](../visual-conventions.md).

| | |
| --- | --- |
| Build | iOS shell with the Aven-mo brand mark (branch `wo/aven-mo-brand-mark-app-icon-and-venmo-style-pay-df6eb2fb`, merged with `main` at `cb1a6bc`), Debug configuration |
| Device | **iPhone 17 Pro simulator**, iOS 26.5 (`IOS_CAPTURE_DEVICE="iPhone 17 Pro"`): no iPhone 18 Pro / iOS 27.2 simulator was installed on the machine that re-took them. Same screen, 402 × 874 pt at **@3x**, so each PNG is 1206 × 2622 px. The first set (before the brand mark) was taken on an iPhone 18 Pro, iOS 27.2 |
| Appearance | Light. The app forces light mode (`preferredColorScheme(.light)`) |
| Status bar | Pinned to 9:41, full signal and Wi-Fi, charged battery (`simctl status_bar override`) |
| Data | The app's Debug fixture (`-UITestFixture`), which serves the same users and payments as `backend/lib/seedData.js`, with ages relative to now ("3 hours ago" and so on) |
| Taken | 2026-11-04, by [`../capture/capture-ios.sh`](../capture/capture-ios.sh) running `ios/SplitPartyUITests/ReferenceCaptureUITests.swift`. Re-taken the same day after the brand mark landed (login mark, header mark, Pay/Request button seated in the tab bar) |

The work order asked for a physical iPhone. These come from the simulator at the
same pt size and scale as an iPhone 17/18 Pro, because the project owner chose
that: no iPhone was connected. The simulator renders the app's pixels the same
way a phone does. To refresh the captures, for example after an iOS UI change,
run `docs/design/capture/capture-ios.sh`. Android then follows the new images.

| File | Screen and state |
| ---- | ---------------- |
| [`login-empty.png`](login-empty.png) | Login, empty field (focused, keyboard up), Sign in disabled |
| [`login-filled.png`](login-filled.png) | Login, "maxi" typed, Sign in enabled |
| [`login-error.png`](login-error.png) | Login after "thor": red field outline and inline error under the field |
| [`login-not-configured.png`](login-not-configured.png) | Launch with no Firestore target: footer says what to fix, Sign in disabled |
| [`home-feed.png`](home-feed.png) | Home with the seeded feed (signed in as Maxi) |
| [`home-new-row-highlight.png`](home-new-row-highlight.png) | Home ~0.5 s after a new payment arrived: top card tinted with brand blue |
| [`home-empty.png`](home-empty.png) | Home with no payments |
| [`home-error.png`](home-error.png) | Home with the database unreachable ("Reconnecting…") |
| [`me.png`](me.png) | Me tab |
| [`pay-request.png`](pay-request.png) | Pay/Request placeholder tab |

The login field focuses itself when the screen opens, so the login captures show
the system keyboard. The simulator must have its hardware keyboard disconnected
(I/O ▸ Keyboard ▸ Connect Hardware Keyboard off), or the software keyboard stays hidden. The keyboard is drawn by the OS (see the platform-difference
list in the conventions note).
