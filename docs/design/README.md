# App shell design

**The iOS captures are the source of truth for how both apps look.** The Android
app is built to match them screen for screen.

| Path | What |
| ---- | ---- |
| [`ios-captures/`](ios-captures/) | One iOS capture per screen and state, with device, scale and build recorded in its README |
| [`visual-conventions.md`](visual-conventions.md) | Values measured from the captures (colours, spacing, type, tab bar, status bar, radii), the bundled Android font, and the **only** allowed platform differences |
| [`comparisons/`](comparisons/) | Each screen as iOS (left) and Android (right) at the same width, plus `-overlay.png` with Android at 50% over iOS |
| [`android-captures/`](android-captures/) | The Android side of each comparison |
| [`capture/`](capture/) | Scripts that recreate all of the above |
| [`../../brand/`](../../brand/) | The Aven-mo mark and app icon sources, and the script that generates every platform's brand assets (see "Brand mark" in the conventions note) |
| [`home-feed-reference-1.jpg`](home-feed-reference-1.jpg), [`-2.jpg`](home-feed-reference-2.jpg) | The original inspiration screenshots from a real payments app. Only the layout idea is used: their ads, merchants and people are examples. They are not the contract |

## Refreshing

When the iOS screens change (for example after an iOS review), capture first,
then update the note, then update Android:

```sh
docs/design/capture/capture-ios.sh                    # iOS captures (simulator, Debug fixture)
(cd android && ./gradlew recordPaparazziDebug)        # Android renders, incl. ParityScreenshotTest
python3 docs/design/capture/compare.py                # android-captures/ + comparisons/ (needs Pillow)
```

The Android images are Paparazzi renders of the same states, with the same data
as the iOS fixture, at the reference iPhone's 402 × 874 geometry
(`android/app/src/test/.../screenshots/ParityScreenshotTest.kt`). Re-running
the commands gives the same images. The status bar and the keyboard are drawn
by the OS, so the Android renders show the page colour behind the status bar
and a flat grey stand-in where the iOS keyboard is.

All images here are stored with Git LFS (see the repo `.gitattributes`).

## Glossary

- **Adaptive icon**: Android launcher icon made of separate background and foreground layers, which each launcher crops to its own shape.
- **dp**: density-independent pixel, the Android equivalent of an iOS point (pt).
- **pt**: iOS point, the layout unit (3 physical pixels on the reference iPhone).
- **Safe zone**: the middle 66 dp circle of a 108 dp adaptive icon, the only part every launcher shape is guaranteed to show.
