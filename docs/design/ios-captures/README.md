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
| Taken | 2026-11-04, by [`../capture/capture-ios.sh`](../capture/capture-ios.sh) running `ios/SplitPartyUITests/ReferenceCaptureUITests.swift`. Re-taken the same day after the brand mark landed (login mark, header mark, Pay/Request button seated in the tab bar). The Home captures (feed, new-row highlight, empty, error) were re-taken again the same day, iPhone 17 Pro / iOS 26.5, when the "Split the check" card filled the banner slot. The Split Party captures (join, confirmation, party, expired, back-to-party, demo off) and `me.png` (demo switch) were added the same day, iPhone 17 Pro / iOS 26.5, with the fixture's party code 🍕🍺🎉; their names match the Android parity renders so `compare.py` pairs them. The placeholder capture was removed with the placeholder screen. The total-slice captures (`total-*`, `removed-message`, `leave-confirm`) were added on 2026-11-05, iPhone 17 Pro / iOS 26.5, by `ReferenceCaptureUITests` tests 10–13. The Pay and Demo reset captures (`pay-*`, `home-paid-highlight`, `me-demo-section`, `demo-reset-*`) were added on 2026-11-05, iPhone 17 Pro / iOS 26.5, by tests 14–15 |

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
| [`home-feed.png`](home-feed.png) | Home with the seeded feed (signed in as Maxi), with the "Split the check" card above the first row |
| [`home-demo-off.png`](home-demo-off.png) | Home with "Demo mode: at Tony's" off: no merchant card, the feed right below the header |
| [`join-banner.png`](join-banner.png) | Home signed in as Annie while Maxi's party is open: the Join banner in the banner slot |
| [`join-confirm.png`](join-confirm.png) | Code confirmation for Maxi's party: four options, one of them 🍕🍺🎉 |
| [`join-confirm-wrong-pick.png`](join-confirm-wrong-pick.png) | Right after a wrong pick: red outline mid-shake and "2 tries left" |
| [`join-locked.png`](join-locked.png) | After the third wrong pick: "Too many wrong codes" |
| [`party-one-member.png`](party-one-member.png) | Party screen just after Maxi started it: host only, two pulsing empty seats |
| [`party-two-members.png`](party-two-members.png) | Same party after Annie joined |
| [`party-three-members.png`](party-three-members.png) | Same party after Christophe joined, in join order |
| [`party-expired.png`](party-expired.png) | Party screen once the party passed its expiry |
| [`home-back-to-party.png`](home-back-to-party.png) | Home while the user is in an open party: "Back to your party" in the banner slot |
| [`home-new-row-highlight.png`](home-new-row-highlight.png) | Home ~0.5 s after a new payment arrived: top card tinted with brand blue |
| [`home-empty.png`](home-empty.png) | Home with no payments |
| [`home-error.png`](home-error.png) | Home with the database unreachable ("Reconnecting…") |
| [`me.png`](me.png) | Me tab |
| [`pay-request.png`](pay-request.png) | Pay/Request placeholder tab |
| [`total-waiting.png`](total-waiting.png) | Party screen as Annie (member), no bill: "Waiting for the total" |
| [`total-add.png`](total-add.png) | Party screen as Maxi (host), no bill: "Add the total" with Scan receipt / Enter total |
| [`total-keypad.png`](total-keypad.png) | Enter total with 48.50 typed on the decimal pad |
| [`total-scanning.png`](total-scanning.png) | Reading a receipt: the photo with the laser line sweeping. The receipt is drawn in code by the Debug scan path (no photo is bundled) |
| [`total-confirm.png`](total-confirm.png) | Confirm total, pre-filled with the TOTAL line ($51.58), not the subtotal or tax |
| [`total-no-total-found.png`](total-no-total-found.png) | A blank page: the "couldn't read" notice above the keypad |
| [`total-camera-denied.png`](total-camera-denied.png) | Camera permission off: the explanation, Enter total and Open Settings |
| [`total-tip-chosen.png`](total-tip-chosen.png) | Host with $100.00 at 18% for three: share $39.34, chips with 18% selected |
| [`total-share-1.png`](total-share-1.png) / [`-2`](total-share-2.png) / [`-3`](total-share-3.png) | The share row with 1, 2 and 3 members ($118.00 total) |
| [`removed-message.png`](removed-message.png) | Christophe's Home right after Maxi removed him: the notice and the Join banner |
| [`leave-confirm.png`](leave-confirm.png) | Annie's "Leave Maxi's party?" confirmation |
| [`pay-add-total.png`](pay-add-total.png) | Annie (host) with two friends and no bill: Pay disabled, "Add the total" |
| [`pay-ready.png`](pay-ready.png) | Annie (host), $100.00 at 18% for three: "Pay $78.66" |
| [`pay-pending.png`](pay-pending.png) | Right after tapping Pay: "Paying…" and the pending badges on Maxi and Christophe |
| [`pay-confetti.png`](pay-confetti.png) | Mid-burst: both seats ticked paid, the success card and confetti |
| [`pay-success-host.png`](pay-success-host.png) | Annie's paid screen once the confetti is gone: "You collected $78.66" |
| [`pay-success-member.png`](pay-success-member.png) | Christophe's paid screen: "You paid $39.33" |
| [`home-paid-highlight.png`](home-paid-highlight.png) | Maxi's Home just after Annie settled with Christophe: the new payment on top, highlighted |
| [`me-demo-section.png`](me-demo-section.png) | Me scrolled to the bottom: the Demo section with "Demo reset" |
| [`demo-reset-confirm.png`](demo-reset-confirm.png) | The "Reset the demo?" confirmation |
| [`demo-reset-progress.png`](demo-reset-progress.png) | While the reset runs: "Resetting…" |
| [`demo-reset-error.png`](demo-reset-error.png) | The reset function failed: the red error under the button |

The login field focuses itself when the screen opens, so the login captures show
the system keyboard. The simulator must have its hardware keyboard disconnected
(I/O ▸ Keyboard ▸ Connect Hardware Keyboard off), or the software keyboard stays hidden. The keyboard is drawn by the OS (see the platform-difference
list in the conventions note).
