# App shell design reference

The iOS and Android shells must look the same. These screenshots are the
visual reference. The values below are the ones the iOS app uses; the Android
shell currently uses a different palette and avatar colours, which still has to
be reconciled (see `chiron-memory/contradictions.md`).

| File | What it shows |
| ---- | ------------- |
| [`home-feed-reference-1.jpg`](home-feed-reference-1.jpg) | Top of Home. Header: round avatar on the left, search/QR capsule on the right, banner area above the feed |
| [`home-feed-reference-2.jpg`](home-feed-reference-2.jpg) | Scrolled feed. Cards: time line, "Name paid Name for memo" sentence with inline avatars, round grey like/comment buttons, floating bottom bar with a round blue action button |

The screenshots come from a real payments app, so their ads, merchants and
people are only examples of layout. The apps show the seeded data from
[`firestore-schema.md`](../firestore-schema.md).

## Palette (sampled from the screenshots)

| Token | Hex | Used for |
| ----- | --- | -------- |
| Brand blue | `#0087E0` | Pay/Request button (both platforms), app title, icons in empty/error states |
| Mark navy | `#021F40` | The Aven-mo mark on login and in the Home header |
| Background | `#F6F6F7` | Screen behind the cards |
| Card | `#FFFFFF` | Feed cards, profile card |
| Primary text | `#000000` | Names, memo, amounts; pill buttons |
| Secondary text | `#7F7F7F` | "paid", "for", relative time, labels |
| Action background | `#F1F1F3` | Round like/comment buttons |
| Error | `#D92D20` | Inline login error |
| New row highlight | brand blue at 14 % | Fades out about 1.5 s after a row arrives |

Cards have a 28 pt/dp corner radius, 20 padding and an 8 side gutter; pill
buttons are black, full width and 52 tall.

## Avatars

No avatar art exists yet. Each `users.avatar` key is drawn as a coloured circle
with the user's initials (first letter of up to two words of `displayName`):

| Key | Colour |
| --- | ------ |
| `avatar_maxi` | `#0087E0` |
| `avatar_annie` | `#E5487D` |
| `avatar_christophe` | `#2EAF6F` |
| anything else | `#8E8E93` (fallback) |

## Feed row content

- Top line: relative time (`Just now` under a minute, then `12 hours ago`,
  `6 days ago`, English on every phone) on the left, amount (`$31.15`) on the right.
- Sentence: `(avatar) Payer paid (avatar) Payee for memo`, using display names,
  **never "You"**, so all phones show identical rows.
- Buttons: comments count, then likes count.

## Bottom bar

A floating capsule, 68 tall, with **Home** and **Me** as regular tabs (icon and
label) and, centred between them, the **Pay/Request** button described below.
Tapping it opens the Pay/Request placeholder.

## Brand mark

The Aven-mo mark and icon live in [`brand/`](../../brand/). Everything below is
generated from the two source images there by
`swift brand/generate-brand-assets.swift` (run from the repository root); never
edit the generated files by hand.

| File | What it is |
| ---- | ---------- |
| `brand/source/aven-mo-mark-source.png` | Navy mark artwork as supplied (1254×1254) |
| `brand/source/aven-mo-icon-glossy-source.png` | Glossy app icon as supplied (500×500, transparent corners) |
| `brand/aven-mo-mark.svg` | Vector mark traced from the navy artwork; 1000×1000 viewBox, navy `#021F40` |
| `brand/aven-mo-icon-1024.png` | Icon master: glossy icon upscaled to 1024×1024, opaque |
| `ios/SplitParty/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` | iOS app icon (copy of the master) |
| `ios/SplitParty/Assets.xcassets/AvenMoMark.imageset/aven-mo-mark.svg` | In-app mark, kept as a vector, template-rendered |
| `android/app/src/main/res/drawable/ic_aven_mo_mark.xml` | In-app mark as a vector drawable, tinted in code |
| `android/app/src/main/res/drawable/ic_launcher_foreground.xml` | Launcher foreground: mark inside the 66 dp safe zone |
| `android/app/src/main/res/drawable/ic_launcher_background.xml` | Launcher background: navy radial gradient |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive icon (vectors cover every density; minSdk is 26) |

Home-screen name on both platforms: **A-venge-mo**.

Sizes are pt on iOS and dp on Android, the same number on both. A mark size is
its square box (the artwork fills the full height of the box and 87 % of its width).

| Where | Value |
| ----- | ----- |
| Login logo slot (above the title, 20 above it) | mark 72, navy `#021F40` |
| Home header | mark 24, navy, 6 left of the "Home" title (20, bold); the title stays centred and the header keeps its height (iOS: 44, set by the avatar; Android currently 52 + 4 above and below, its avatar badge, still to be aligned with iOS by the Android reshape) |
| Centre Pay/Request button | circle 60 across, brand blue `#0087E0`, white mark 30 |
| Button position | horizontally centred on the bar; top edge 16 above the bar's top edge |
| Button elevation | 8 (Android `shadowElevation = 8.dp`; iOS shadow black 24 %, radius 8, y 4) |
| Button label | none on screen; VoiceOver / TalkBack label "Pay or request" |
| Space kept free between Home and Me | button width + 8 |

The mark and button use fixed colours, so they look the same with the phone in
dark theme. The button looks the same whether Pay/Request is open or not.

## iOS | Android comparison images

`docs/design/comparison/` holds side-by-side images (iOS left, Android right) of
login, Home, Pay/Request and the bottom bar, built from the committed screenshot
references. After re-recording either platform's references, rebuild them:

```
swift docs/design/make-comparison-images.swift     # from the repository root
```

## Glossary

- **Adaptive icon**: Android launcher icon made of separate background and
  foreground layers, which each launcher crops to its own shape (circle,
  squircle, square).
- **dp**: density-independent pixel, the Android equivalent of an iOS point (pt).
- **Golden**: a committed reference screenshot that screenshot tests compare against.
- **pt**: iOS point, the layout unit (2–3 physical pixels on current iPhones).
- **Safe zone**: the middle 66 dp circle of a 108 dp adaptive icon, the only
  part every launcher mask is guaranteed to show.
