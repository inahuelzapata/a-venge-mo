# Visual conventions (iOS and Android)

**The iOS captures in [`ios-captures/`](ios-captures/) are the source of truth for
how the app looks.** Every value below was measured on 2026-11-04 from those
captures: iPhone 18 Pro simulator, 402 × 874 pt, @3x, so 1 pt = 3 px. Values come
from pixel sampling and are not copied from the iOS source or from older notes.
Android uses the same numbers in dp (1 dp ≈ 1 pt) and sp for text. Later slices on
either platform build to these numbers. When the iOS look changes, re-capture
first, then update this note, then update Android.

Side-by-side comparisons (iOS on the left, Android on the right, same width) and
50% overlays are in [`comparisons/`](comparisons/).

## Colours

| Token | Hex | Where it was sampled |
| ----- | --- | -------------------- |
| Page background | `#F6F6F7` | Behind the cards, behind the status bar, login screen |
| Card | `#FFFFFF` | Feed cards, profile card, status cards, login field |
| Brand blue | `#0087E0` | "Split Party" wordmark, Maxi avatar, Pay/Request circle, empty/error icons |
| Primary text | `#000000` | Names, memo, amounts, titles, selected tab |
| Secondary text | `#7F7F7F` | "paid"/"for", relative time, subtitles, unselected tabs, login footer |
| Chip background | `#F1F1F3` | Round comment/like buttons |
| Error | `#D92D20` | Inline login error text, icon and 1.5 outline on the field |
| Setup warning | `#FF8D28` | "not configured" explanation in the login footer |
| New-row highlight | `#D4E6F4` | Brand blue at 14% over the white card. Fades back to white after about 1.5 s |
| Disabled button | `#ACACAD` | Black at 30% over the page |
| Hairlines | black at 6–8% | Search/QR capsule outline (8%), tab bar outline (6%). There are **no separators between rows**: rows are separate cards with a 12 gap |
| Tab bar fill | `#F2F2F4` | Capsule over the page. iOS draws a translucent material over the content |
| Selected tab pill | black at 6% over the tab bar | Behind Home or Me when selected. Never behind Pay/Request |
| Mark navy | `#021F40` | The Aven-mo mark on the login screen and in the Home header (white inside the centre button) |
| Avatars | Maxi `#0087E0`, Annie `#E5487D`, Christophe `#2EAF6F`, other `#8E8E93` | Circle with white initials |

There is no dark appearance. iOS forces light mode, so Android renders this
light design whatever the system theme.

## Status bar

| Screen | Behind it | Icon/text tint |
| ------ | --------- | -------------- |
| All screens (login, Home, Me, Pay/Request) | Page background `#F6F6F7`. The bar is transparent and the page shows through, with no separate bar colour or divider | Dark |

Safe-area top on the reference device: 62 pt; bottom (home indicator): 34 pt.
Android lays out against **max(system inset, iPhone inset)**: content starts at
the larger of the status-bar/cutout inset and 62, and the tab bar sits 4 above
the larger of the gesture-area inset and 34. Headers and tab bars therefore
line up with an iPhone placed next to the phone, and nothing ever sits under a
cutout.

## Layout and spacing

Spacing values used: **4, 6, 8, 12, 14, 16, 20, 24, 28, 32**.

| Element | Value |
| ------- | ----- |
| Screen gutter (cards) | 8 left and right. Cards are 386 wide on a 402 screen |
| Header margins | 16 left and right, 8 below the safe area |
| Header height | 44 (avatar, title, capsule all 44 tall). Content starts 16 below it |
| Header avatar | 44 diameter, 2 white outline, soft shadow (black 8%, radius 4, y 1) |
| Search/QR capsule | 105 × 44, white, 8% hairline, icons 20 pt with 22 between, 18 side padding |
| Home title | Centred on the screen width, "Home", with the Aven-mo mark (24) 6 to its left. The mark does not move the title |
| Feed card | Radius 28, padding 20, white. 187.33 tall with a two-line sentence |
| Gap between cards | 12 |
| Card inner spacing | 14 between time line, sentence and chips |
| Inline avatar (in sentence) | 22 diameter, on the text baseline (lowered 4). Sentence lines are 27.5 apart (the avatars make them taller than plain 20 text) |
| Comment/like chip | Height 44, capsule, 72 × 44 with a one-digit count. Icon ink about 19.7 × 18.7 (comment) and 18 × 16.7 (heart), 10.7 between icon ink and count. 8 between chips. Comments first |
| Status card (empty/error) | Radius 28, padding 28, icon line 36 (42.5 for the error's wifi symbol; ink 41.7 × 30 tray, 39.3 × 26.3 wifi), 10 between lines, centred text. Card heights 154.3 (empty), 212.7 (error). "Reconnecting…" row: spinner + 8 + text, 4 above |
| Me title | "Me", 44 tall, 8 below the safe area, centred |
| Profile card | Padding 20, avatar 80, 10 between lines. Balance block 12 above and below, 4 between label and amount |
| "Your payments" heading | 20 from the left edge, 8 above |
| Pay/Request placeholder | Blue `$` circle 64, title, subtitle, 12 apart, centred in the space above the tab bar |
| Login | Column centred vertically. Aven-mo mark (72) 20 above the wordmark. Wordmark and subtitle 8 apart, 32 above the field. Field and button 24 side margins, 12 apart, button 8 extra above |
| Login field | 56 tall white capsule, 20 inner padding, 18 text. Error adds a 1.5 `#D92D20` outline |
| Inline error | Below the field, 8 from the field's left edge: filled `!` circle icon + text, 15, `#D92D20`, wraps under the text |
| Login footer | Database icon + target text, 13, secondary, centred, 16 above the bottom safe area. Setup problem below it, 6 apart, setup-warning orange |
| Pill button | Black capsule, full width, 52 tall, 18 semibold white text. Disabled: black at 30%. Pressed: 80% opacity, nothing else |
| Corner radii | Cards 28; field, buttons, chips, capsules and tab bar are full capsules (radius = height / 2) |

## Bottom (tab) bar

A floating capsule, not a full-width bar. iOS shows no top hairline across the
screen. The only hairline is the capsule's own outline.

| Property | Value |
| -------- | ----- |
| Position | 16 from the left and right, 4 above the bottom safe area (gesture area) |
| Size | Full width minus 32 × **92** tall (6 padding around 80 tall items) |
| Fill / outline / shadow | `#F2F2F4` / black 6%, 1 / black 12%, radius 16, y 4 |
| Items | Home, Pay/Request, Me. Equal widths, 4 apart. Home and Me: icon above label. The centre third is empty, under the raised button |
| Item | 6 above, icon slot 52 tall, 3 gap, label 11, 6 below |
| Selected | Icon and label primary (black), label semibold, pill behind the item (black 6%). Home and Me only |
| Unselected | Icon and label secondary `#7F7F7F`, label regular |
| Centre button | Raised circle, **60** across, brand blue `#0087E0`, white Aven-mo mark 30, no label. Horizontally centred; top edge **16 above** the bar's top edge. Shadow black 24%, radius 8, y 4 (elevation 8). Looks the same whether Pay/Request is open or not. VoiceOver / TalkBack label "Pay or request" |
| Content | Scrolls underneath the bar. The last row stays reachable thanks to 24 bottom padding plus the bar inset |

## Brand mark

Added by the Aven-mo brand work (values above in Layout and Bottom bar). The
mark and icon are generated from [`brand/`](../../brand/) by
`swift brand/generate-brand-assets.swift` (run from the repository root); never
edit the generated files by hand. A mark size is its square box: the artwork
fills the box's full height and 87% of its width.

| Value | iOS (pt) and Android (dp) |
| ----- | ------------------------- |
| Mark colour on the page | Navy `#021F40` (login, Home header) |
| Login mark | 72, 20 above the wordmark |
| Home header mark | 24, 6 left of the title; header stays 44 tall |
| Centre button | 60 circle, `#0087E0`, white mark 30, top edge 16 above the tab bar, shadow black 24% radius 8 y 4 (elevation 8) |
| Home-screen name | "A-venge-mo" |

| File | What it is |
| ---- | ---------- |
| `brand/source/aven-mo-mark-source.png` | Navy mark artwork as supplied (1254 × 1254) |
| `brand/source/aven-mo-icon-glossy-source.png` | Glossy app icon as supplied (500 × 500, transparent corners) |
| `brand/aven-mo-mark.svg` | Vector mark traced from the navy artwork; 1000 × 1000 viewBox |
| `brand/aven-mo-icon-1024.png` | Icon master: the glossy icon upscaled to 1024 × 1024, opaque |
| `ios/SplitParty/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` | iOS app icon (copy of the master; also the dark-mode variant, so iOS does not darken it) |
| `ios/SplitParty/Assets.xcassets/AvenMoMark.imageset/aven-mo-mark.svg` | iOS in-app mark, kept as a vector, template-rendered |
| `android/app/src/main/res/drawable/ic_aven_mo_mark.xml` | Android in-app mark, tinted in code |
| `android/app/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml` | Adaptive launcher icon layers: light mark inside the 66 dp safe zone, navy radial gradient |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` | Adaptive icon (vectors cover every density; minSdk is 26). No monochrome layer, so themed icons don't restyle it |

## Type

iOS uses SF Pro, which may not be bundled on Android. Android bundles **Inter**
(v4.1, by Rasmus Andersson) instead. Inter was designed with the same goals
(neo-grotesque, tall x-height, screen use) and its metrics are close to SF Pro.
Its licence is the **SIL Open Font License 1.1**, and the licence text ships with
the app at `android/app/src/main/assets/licenses/Inter-OFL.txt`. Every Android text role uses
Inter. Sizes and weights are the iOS ones. Line height is iOS's default
for SF Pro (1.19 × size). Letter spacing is tuned per role so that line widths
match the captures (measured in `comparisons/`).

| Role | Size | Weight | Line height | Colour |
| ---- | ---- | ------ | ----------- | ------ |
| Wordmark ("Split Party") | 40 | Bold | 48 | Brand blue |
| Screen title ("Home", "Me") | 20 | Bold | 24 | Primary |
| Section heading ("Your payments"), status-card title | 20 | Semibold | 24 | Primary |
| Row primary (sentence) | 20 | Regular | 24 | Primary names/memo, secondary "paid"/"for" |
| Row secondary (time) | 15 | Regular | 18 | Secondary |
| Amount | 17 | Semibold | 20 | Primary |
| Chip count | 15 | Medium | 18 | Primary |
| Body secondary (subtitles, messages, "@maxi", "Balance") | 15 / 17 (login subtitle) | Regular | 18 / 20 | Secondary |
| Profile name | 26 | Bold | 31 | Primary |
| Balance | 36 | Bold | 43 | Primary |
| Placeholder title ("Pay or request") | 24 | Bold | 29 | Primary |
| Field text | 18 | Regular | 21 | Primary (placeholder: secondary) |
| Button | 18 | Semibold | 21 | White |
| Inline error | 15 | Regular | 18 | Error |
| Footer | 13 | Regular | 16 | Secondary |
| Tab label | 11 | Regular (selected: Semibold) | 13 | See tab bar |

## Interaction

- No ripples, no elevation change and no colour flash when a row, chip, tab or
  button is tapped. The only press feedback is the pill button dimming to 80%.
- No row is tappable in this slice.
- New rows slide in at the top and carry the highlight for 1.5 s, then fade back to white.
- A feed sentence never ends with a lone word on its last line: iOS moves the previous word down ("… for 🍦 ice / cream run"), and Android does the same.
- Relative times use the iOS wording: "Just now", "N minutes/hours/days/weeks/months/years ago".
- Tab switches are instant (no cross-fade).
- The keyboard opens with the login field focused. The login column re-centres
  in the space left above the keyboard. The footer stays above the keyboard.

## Allowed differences (platform-mandated, nothing else)

1. **System-drawn status bar content**: clock, signal, Wi-Fi and battery glyphs, and their exact positions. The background and the dark tint are matched.
2. **System navigation gesture area**: the home indicator and gesture bar, and the device's bottom inset height. The tab bar sits 4 above whatever inset the device has.
3. **System back behaviour**: Android's back gesture/button stays as the merged shell defines it (Home never goes back to login; Me and Pay/Request go back to Home). iOS has no system back.
4. **Text rendering**: Inter on Android vs SF Pro on iOS (glyph shapes, hinting and anti-aliasing). Sizes, weights, line heights and line breaks match.
5. **System keyboard**: drawn by the OS (Gboard vs the iOS keyboard), so its height differs. Only the app's layout above it is matched.
6. **Icon glyphs**: iOS uses SF Symbols, which may not ship on Android. Android uses vector look-alikes of the same size, weight and position.
7. **Translucent tab bar**: iOS blurs the content scrolling under the bar. Android draws the same colour without the backdrop blur (Compose has no backdrop blur), so they look identical over the page and differ only while a card scrolls under the bar.

## Verification (Android against the captures, 2026-11-04)

> The table below was measured before the brand mark landed. The brand work
> changed the login, Home header and centre-button areas on both platforms, so
> re-measure them once the Android renders and `comparisons/` are refreshed.

Measured on the Android parity renders (`android-captures/`, same 402 × 874 at
3x) with the same pixel sampling used for the iOS values:

| Value | iOS | Android |
| ----- | --- | ------- |
| Header: avatar / title / capsule | 44 at y 70–114, "Home" 54 wide, capsule 281–386 | same, within 0.3 (title 53.7 wide) |
| Feed card (two-line sentence) | 130.0–317.3 (187.3), gap 12 | 130.0–317.7 (187.7), gap 12 |
| Avatar diameter (header coloured disc / Me) | 42 inside a 2 ring / 80 | 42 / 80 |
| Tab bar | 16–386 × 744–836 (92), selected pill 22–138.7 | same |
| Horizontal margins | cards 8, header 16, login 24 | 8, 16, 24 |
| Me profile card | 122.0–472.3 | 122.0–472.3 |
| Status cards | 154.3 empty, 212.7 error | 154.3, 213.0 |
| Background / brand blue / primary / secondary / chip | `#F6F6F7` `#0087E0` `#000000` `#7F7F7F` `#F1F1F3` | identical values |

Line breaks match on every feed row, the Me rows and the login error. The
login footer and the not-configured explanation hold different text (database
target, config file path), so they wrap differently. That text comes from the
build, not the style.

Content that depends on the device or the data is not a style difference: the
footer's database line (iOS captures used the Debug fixture, so they say "UI
test fixture"), and relative times that move on as time passes.
