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

## Split Party card

The Home feed's entry to Split Party: a feed item directly below the header,
above the first row (and above the loading, empty and error cards). Modelled on
the Kalshi ad card in [`home-feed-reference-1.jpg`](home-feed-reference-1.jpg)
**without** its logo/name/"Sponsored" header. **Android values below are
provisional** (built from the reference before the iOS card existed). Replace
them with the iOS card's measured values when its capture lands, then reconcile
Android.

| Element | Value |
| ------- | ----- |
| Card | White, radius 28, 8 gutter (386 wide on 402), no shadow or elevation. 16 below the header, 12 above the first row |
| Hero image | `brand/banners/split-the-check-with-friends.png` (1774 × 887, 2:1), 4 from the card's top and sides, full card width minus 8, scaled to fit with no crop, radius 24 (concentric with the card) |
| Button | Pill button (52, black, "Start a party"), 12 below the image, 16 from the card's sides and bottom |
| Taps | Image and button open Split Party. Nothing else on the card is tappable. No ripple; the button dims to 80% while pressed |
| Accessibility | The image is announced by the banner's own copy ("Split the check with friends. A faster, easier way to take care of the bill."), the button as "Start a party, button" |
| Demo switch | Shown only while "Demo mode: at Tony's" (Me tab) is on, which it is by default. Off: no item at all, so the feed sits directly below the header with no gap. Arrival animation: see "Home banner slot" below (iOS values) |

The banner artwork is committed once, in `brand/banners/`. Android copies it into
generated resources at build time (`drawable-nodpi`, see `android/app/build.gradle.kts`),
so there is no copy under `android/`.

## Split Party screens: Android reconciliation

The Android slice landed first, with provisional values of its own. The iOS
values are now recorded in "Home banner slot", "Party screen" and "Code
confirmation" below, with captures in `ios-captures/` under the same names as
the Android parity renders (`join-banner`, `join-confirm`, `party-one-member`,
`party-three-members`, `party-expired`, `home-demo-off`), so `compare.py` pairs
them. Android still differs from them in these places, to reconcile in a later
Android change:

| Element | Android today | iOS (the reference) |
| ------- | ------------- | ------------------- |
| Merchant card arrival | Slides up a third of its height and fades in (450 ms), sheen 1 s after 250 ms | Waits for the parties query, slides in from the trailing edge (spring), sheen 0.9 s after 0.25 s |
| Join banner | 48 host avatar with the live dot on it, second line "Tap Join and confirm…", 36-tall Join | No avatar; 10 live dot on the left, one sentence, 38-tall Join, 1 pt brand-blue border at 25% |
| Back to your party | Dot, "You're in a Split Party at Tony's" and the emojis, then a separate pill button | The whole card is one button: emojis, "Back to your party" over "Split Party at Tony's · 4:12 left", chevron |
| Start error | "Couldn't start the party. Check your connection and try again." | Says whether the phone is offline or the database refused; dismissible |
| Code card | "Party code", emoji 40, pop 120 ms apart, bob 1.8 s, live dot beside the countdown, TalkBack "Party code: …" | "Show friends this code", emoji 44, pop 140 ms apart, bob 1.4 s, `timer` symbol, red in the last 30 s, VoiceOver "Join code: …" |
| Members | "Members", 60 avatars spread evenly | "Who's in", 56 avatars, 14 apart from the left, empty seats labelled "Waiting" |
| Expired | "Nothing was charged. Start a new party from Home." | "Nothing was charged." |
| Confirmation | Title "Join Annie's party", options are cards of three 60 tiles, shake ±14 decaying in 360 ms, "That's not the code. Try again." | Bar title "Join Split Party", host avatar and "Join Maxi's party at Tony's", 72-tall option buttons with emoji 34, shake ±10 three times in 0.4 s, message counts the tries left |
| Demo switch row (Me) | Green `#34C759` switch | System switch tinted brand blue `#0087E0`, 20 × 14 padding |
| Total area | Filled in by the total slice ("Total, tip and share" below): "Add the total" for the host, the live share for everyone | Same, since the iOS total slice; `party-one-member` and `party-three-members` were taken before it and still show the reserved area |

Both platforms agree on the behaviour: slot priority, clock-based expiry, the
three-strike lock, haptics kept with reduced motion, and the Firestore writes.

## Total, tip and share (PoC-10)

**iOS is the reference** (captures in `ios-captures/`: `total-waiting`, `total-add`,
`total-keypad`, `total-scanning`, `total-confirm`, `total-no-total-found`,
`total-camera-denied`, `total-tip-chosen`, `total-share-1/2/3`, `removed-message`,
`leave-confirm`; same names as Android's `TotalScreenshotTest`). Measured on the
iPhone 17 Pro simulator (402 × 874 pt). Lives in the party screen's third card
(white, radius 28, 20 padding); the screen's scroll content keeps **96** clear
at the bottom so nothing ends under the floating tab bar.

| Element | iOS (pt) |
| ------- | -------- |
| Waiting (members, no bill) | Unchanged from the party slice: `hourglass` 24 secondary, "Waiting for the total" (17 semibold), "The host enters the bill and tip next." (14 secondary), min 120 tall. No entry or tip controls |
| Add the total (host, no bill) | `receipt` 24 brand blue, "Add the total" (17 semibold), "Scan the receipt or type the amount." (14 secondary), 8 apart; then 8 lower two equal capsules **48** tall, 10 apart (each 168 wide on this phone): "Scan receipt" (`camera.viewfinder`, black, white 15 semibold) and "Enter total" (`keyboard`, `#F1F1F3`, black 15 semibold). Card content 346 × 145 |
| Share row (everyone, bill set) | "Your share" (15 medium, secondary) over the amount (**40 bold**, monospaced digits), 2 apart. 14 below, a `#F1F1F3` block, radius 20, 12 vertical padding, three equal columns: label (13 medium, secondary) over value (17 semibold): "Total" $118.00, "Tip" 18% with $18.00 under it (13 secondary), "People" 3. Share, total, tip and people change in one frame; the digits roll (`numericText`, spring 0.5 / 0.85). Light impact haptic when this phone's share changes. Summary block 346 × 162 |
| Tip chips (host only) | 18 below the share row: "Tip" (15 semibold), 8 above one row of five **equal** capsules, **40** tall, 8 apart (62.7 wide each here): 0%, 15%, 18%, 20%, Custom. Unselected `#F1F1F3` with black 15 semibold, selected black with white. Custom shows its amount once chosen ("$7.00"); a stored 25% also shows on Custom. Selection haptic. Members see the tip only in the share row |
| Change total (host, bill set) | 18 below the chips: the same two 48 capsules as "Add the total" ("Scan receipt" black, "Enter total" grey) |
| Write error | Under the chips or the member row: `exclamationmark.circle.fill` + message, 15 medium, `#D92D20` ("Couldn't reach the database. Nothing was saved; try again.") |
| Keypad screen | Sheet with inline bar title "Enter total" / "Confirm total" / "Custom tip" and Cancel. 20 side margins, 12 top: prompt (17 semibold, secondary), then a white field, radius 20, **84** tall, 18 horizontal padding: "$" (26 bold, secondary) + amount (**44 bold**, monospaced) on the system decimal pad, focused on open. Invalid input: 2 pt red outline and the red message under the field, error haptic. Black 52 "Confirm total" / "Set tip" pill pinned 12 above the keyboard |
| Scan screen | Sheet, bar title "Scan receipt" with Cancel. The system camera; after capture the page turns black, the photo sits 20 in from the edges, radius 20, with "Reading the receipt…" (white 17 semibold) and a white spinner 18 below |
| Laser line | Over the photo: a 3 tall `#6CC4FF` line with a brand-blue 6 radius shadow, centred in a 36 tall glow (brand blue 0 → 35% → 0). Sweeps top to bottom and back, 1.1 s each way, ease-in-out, repeating, clipped to the photo's 20 radius. At least 0.7 s on screen. None with Reduce Motion (status and spinner stay) |
| Confirm detected amount | The keypad screen titled "Confirm total", prompt "Is this the receipt's total?", the detected amount pre-filled and editable. Nothing is written until "Confirm total" |
| No total found / unreadable | The keypad screen with a `#F1F1F3` notice (radius 16, 14 padding, `doc.text.magnifyingglass` + 15 medium): "We couldn't find a total on that receipt. Type it instead." / "We couldn't read that photo. Type the total instead." |
| Camera unavailable | In the scan sheet: `camera.slash` 52 secondary, "Camera access is off" / "No camera available" (24 bold), the reason (17 secondary), black 52 "Enter total" pill 12 lower, and for denied a brand-blue "Open Settings" (17 semibold), 32 side margins |
| Remove member (host) | Tap a non-host seat: system confirmation dialog "Remove Christophe from the party?", "Everyone's share is recalculated. They can rejoin with the code while the party is open.", destructive "Remove Christophe" |
| Leave party (members) | "Leave party", red 17 semibold text button, 44 tall, under the total card. System confirmation dialog "Leave Maxi's party?", "Your share goes back to the others. You can rejoin with the code while the party is open.", destructive "Leave party" |
| Removed message | On Home between the header and the banner slot: 85% black card, radius 18, 16 × 12 padding, 8 gutter, `person.crop.circle.badge.minus` + "You were removed from Maxi's party" (white 15 medium). Drops in from the top; gone after 5 s. The Join banner for that party shows below it |
| Expired with a bill | The expired treatment, then the last share row frozen in a white card (70% opacity), no chips or buttons |
| Accessibility | Share row: "Your share, 39 dollars and 34 cents"; "Total, 118 dollars"; "Tip, 18 percent, 18 dollars" ("No tip" at 0%); "People, 3 people". Chips: a "Tip" group, each chip a button with the Selected trait, the choice announced ("18 percent tip selected"). Scan: "Reading the receipt" announced. Host's member seats hint "Double-tap to remove from the party" |
| Reduce Motion | No laser sweep, no digit roll; changes are instant. Haptics stay |

### Android reconciliation (total slice)

Where the Android values recorded before these captures differ from iOS:

| Element | Android (before) | iOS (reference) |
| ------- | ---------------- | --------------- |
| Add the total | Body "Scan the receipt or type the amount. Everyone's share updates as you go."; 52 tall pills | Body "Scan the receipt or type the amount."; 48 tall capsules with icons; `receipt` icon above the heading |
| Share figures | Share 36 bold; Total / Tip / People as three label-value rows | Share 40 bold; the three as columns in a grey radius-20 block, tip amount under the percent |
| Tip chips | 44 tall, 6 apart, Custom 1.6 × wide | 40 tall, 8 apart, all five equal |
| Change total | Grey "Scan receipt" and "Change total", 44 tall | Black "Scan receipt" and grey "Enter total", 48 tall |
| Keypad | Custom 3 × 4 keypad, no system keyboard | The system decimal pad under an 84 tall white field (platform keypad; allowed difference) |
| Confirmation | Its own screen with "Found on the receipt", the source line and "Edit amount" | The keypad screen pre-filled ("Is this the receipt's total?"), editable in place |
| Remove / leave dialogs | Custom white card dialogs | System confirmation dialogs (platform-mandated look; wording matches) |
| Leave action | Grey capsule | Red text button |
| Removed message | White capsule, 4 s | Black card under the header, 5 s, with an icon |

Both platforms agree on the data and behaviour: integer cents, 0% stored as
`{type: custom, amountCents: 0}`, a bill always written with its tip state,
percent tips rounded half up, the host carrying the leftover cents, transactions
for every write, nothing written after expiry or before the host confirms a scan.

## Bottom (tab) bar

A floating capsule, not a full-width bar. iOS shows no top hairline across the
screen. The only hairline is the capsule's own outline.

| Property | Value |
| -------- | ----- |
| Position | 16 from the left and right, 4 above the bottom safe area (gesture area) |
| Size | Full width minus 32 × **92** tall (6 padding around 80 tall items) |
| Fill / outline / shadow | `#F2F2F4` / black 6%, 1 / black 12%, radius 16, y 4 |
| Items | Home, Pay/Request, Me. Equal widths, 4 apart. Home and Me: icon above label. The centre third holds the Pay/Request button |
| Item | 6 above, icon slot 52 tall, 3 gap, label 11, 6 below |
| Selected | Icon and label primary (black), label semibold, pill behind the item (black 6%). Home and Me only |
| Unselected | Icon and label secondary `#7F7F7F`, label regular |
| Centre button | Circle **60** across, brand blue `#0087E0`, white Aven-mo mark 30, no label. **Seated in the bar**: centred horizontally and vertically on the 92 capsule (16 clear above and below), not lifted above it, and no shadow of its own (elevation 0). Looks the same whether Pay/Request is open or not. VoiceOver / TalkBack label "Pay or request" |
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
| Centre button | 60 circle, `#0087E0`, white mark 30, centred in the 92 tab bar, no lift, no shadow |
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

## Split the check card

The entry point to Split Party: the first item of the Home feed, in the banner
slot between the header and the first payment card. It scrolls with the feed.
Its anatomy follows the Kalshi card in `home-feed-reference-1.jpg`, but with **no
header row**: no logo, no merchant name and no "Sponsored" label, because the
artwork carries the copy and the Tony's identity. It reads no Firestore data and
looks the same with seeded data, an empty feed and the error state (it sits
above the empty/error card). Measured on [`ios-captures/home-feed.png`](ios-captures/home-feed.png)
(402 pt wide, @3x).

| Value | iOS (pt) and Android (dp) |
| ----- | ------------------------- |
| Position | First feed item, 16 below the header (y 130 on the reference device), 16 above the first payment card |
| Card | White, radius 28 (continuous), 8 gutter left and right (386 wide). 259 tall on a 402 screen |
| Hero image | 4 from the card's top, left and right edges (378 wide). Radius 24 (continuous, concentric with the card). Aspect ratio **2 : 1**, fitted, never cropped: 189 tall at 378 wide |
| Image to button | 12 |
| Button | Black `#000000` capsule, **38** tall, **18** from each card edge (the 4 image inset plus 14). "Start a party", 15 semibold, white, centred. Pressed: 80% opacity |
| Button to card bottom | 16 |
| Tap targets | Hero image and button only, both open Split Party. The image shows no press feedback |
| Accessibility | The image is one element labelled with the banner copy ("Split the check with friends. A faster, easier way to take care of the bill."). The button is labelled "Start a party". No unnamed image |
| Theme | Same in light and dark: the app is light-only |

For comparison, the Kalshi reference card measures 3.3 image inset, 37.3 button
height, 14.4 button side inset, 10.9 image-to-button gap and 15.3 bottom inset,
at 1.836 px per pt. The values above round these to the spacing scale. The
button keeps 18 from the card edge, a choice made in review.

| File | What it is |
| ---- | ---------- |
| `brand/split-party-banner.png` | The banner artwork as supplied, 1774 × 887 px (2 : 1). Committed only here. The iOS project bundles this file directly ("Shared brand assets" group), with no copy in `Assets.xcassets`. 1774 px covers a 3x card on the widest iPhone (416 pt card image → 1248 px) |

The placeholder screen this card used to open is gone on both platforms:
"Start a party" now opens the party screen below.

## Home banner slot (PoC-7/8)

Home's banner slot, between the header and the first feed item, shows exactly
one of these, most important first. With nothing to show it adds no view and no
spacing: the first feed card moves up to 16 below the header.

1. **"Back to your party"** while the signed-in user is in an open, unexpired party.
2. **Join banner** for the newest open, unexpired party at Tony's hosted by a
   friend the user is not in and not locked out of.
3. **"Split the check" card** while the Me tab's "Demo mode: at Tony's" switch is on.

| Element | iOS (pt) and Android (dp) |
| ------- | ------------------------- |
| Merchant card arrival | Waits for the open-parties query (at most 1.2 s), then 0.35 s, then slides in from the trailing edge with opacity, spring (response 0.5, damping 0.85). The feed below moves down with the same spring. Then one shimmer sweep: 0.25 s delay, 0.9 s ease-in-out, a white band 35% of the card wide, peak opacity 0.55, tilted 20°, clipped to the card's 28 radius. Plays once per session, and again after the switch is turned back on |
| Join banner | White card, radius 28, 8 gutter, 1 pt brand-blue border at 25%. Padding 18 horizontal, 16 vertical, 12 between items. Left: 10 brand-blue dot whose halo grows to 2.6× and fades, 1.2 s, repeating. Middle: "{Host} started a Split Party at {Merchant}", 16 semibold, primary, wraps. Right: "Join", black capsule 38 tall, 20 horizontal padding, 15 semibold white. Drops in from the top with opacity (spring 0.45 / 0.8) |
| "Back to your party" card | Same card and padding as the Join banner, no border. The party's three emojis at 26, then "Back to your party" (16 semibold) over "Split Party at Tony's · 4:12 left" (14, secondary, monospaced digits), chevron 15 semibold secondary. The whole card is one button |
| Demo switch (Me tab) | White card, radius 28, 8 gutter, 16 below the profile card: "Demo mode: at Tony's" (17 semibold) over "Pretend this phone is at the merchant" (14 secondary), 20 × 14 padding, system switch tinted brand blue. On by default, remembered across restarts |
| Start error | Under the slot: red `#D92D20` 15 regular with an `exclamationmark.circle.fill` and a dismiss ×, 20 from the screen edge. The card stays; nothing navigates |
| Accessibility | Join banner: the sentence, then the "Join" button. "Back to your party" card: one button, "Back to your party, Split Party at Tony's" |
| Reduce Motion | No slide, drop, shimmer or pulse: banners appear and disappear instantly |

## Party screen (PoC-8/9)

Pushed over Home with the system navigation bar: inline title **"Split Party at
Tony's"** and Back. The bottom bar stays. Page background `#F6F6F7`; three white
section cards, radius 28, 8 gutter, 20 padding, 16 apart, 8 below the bar.

| Element | iOS (pt) and Android (dp) |
| ------- | ------------------------- |
| Code card | "Show friends this code" (15 medium, secondary), 14 above the tiles. Three tiles **76 × 76**, radius 20 (continuous), fill `#F1F1F3`, 12 apart, emoji 44. Then the countdown 14 below |
| Tile motion | Pop in one by one, 0.14 s apart, from 20% scale and 0 opacity (spring 0.35 / 0.55). Then bob ±3 vertically, 1.4 s ease-in-out, autoreversing, alternate tiles out of step |
| Countdown | `timer` symbol + "Expires in 4:59", 17 semibold, monospaced digits, primary; error red for the last 30 s. Ticks on the whole second and exactly at expiry |
| Member card | "Who's in" (17 semibold), 14 above the seats. Seats 14 apart, each 84 wide: avatar **56**, first name 14 medium 6 below (one line, shrinks to 80% before truncating). The host's avatar carries a black "Host" capsule (11 bold white, 7 × 2 padding, 1.5 white ring) overlapping its bottom edge by 7. At least 3 seats: empty ones are a 56 circle with a 2 pt dashed `#7F7F7F`-at-50% ring over `#E4E4E8`, "Waiting" below, pulsing (scale 0.94 and fill opacity 0.35, 0.9 s, autoreversing) |
| Member arrival | New seat scales from 30% with opacity, spring (0.45 / 0.7). Success haptic on the phones already in the party |
| Total area | See "Total, tip and share" above: "Add the total" for the host, "Waiting for the total" for members until a bill exists, then the live share for everyone. No Pay action yet |
| Reconnecting | While the party document only comes from the cache or the network is down: capsule above the code card, white, small spinner + "Reconnecting…" (14 medium, secondary), 14 × 8 padding |
| Expired | Replaces the cards: `clock.badge.xmark` 52 secondary, "This party has expired" (24 bold), "Nothing was charged." (17 secondary), 12 apart, then the black 52 "Back to Home" pill 12 lower, 32 side margins |
| Accessibility | Code: one element, "Join code: pizza, beer, party popper". Each seat: "Maxi, host", "Annie", "Empty seat" |
| Reduce Motion | Tiles appear at once and do not bob; seats appear without the spring; empty seats do not pulse. Haptics stay |

## Code confirmation (PoC-9)

Pushed from the Join banner, inline title "Join Split Party".

| Element | iOS (pt) and Android (dp) |
| ------- | ------------------------- |
| Header | Host avatar 56, "Join {Host}'s party at Tony's" (22 bold, centred), "Pick the code shown on {Host}'s phone" (16 secondary), 10 apart, 12 below the bar, 24 side margins |
| Options | Four full-width white buttons, **72** tall, radius 20, 8 gutter, 12 apart, three emojis at 34, 18 apart. Exactly one is the host's code; three decoys from the same palette, all different. New random order each visit and after every wrong pick |
| Wrong pick | The picked option gets a 2 pt red `#D92D20` outline and shakes ±10 pt three times in 0.4 s; error haptic; "That's not Maxi's code. 2 tries left." (15 medium, red, with an `exclamationmark.circle.fill`) under the options. After 0.45 s the options reshuffle (spring 0.4 / 0.8) |
| Locked (third wrong pick) | `lock.fill` 52 secondary, "Too many wrong codes" (24 bold), "You can't join Maxi's party any more. Ask them to start a new one if you're at the same table." (17 secondary), "Back to Home" pill. The Join banner for that party no longer appears on this phone; the host sees nothing |
| Accessibility | Each option is read by emoji names ("pizza, beer, party popper") |
| Reduce Motion | No shake or reshuffle animation; the outline, message and haptic stay |

## Pay and settlement (PoC-11), Demo reset (PoC-12) — provisional

Built on Android before the iOS captures exist; reconcile with iOS once its
slice records these states. Android goldens: `PayScreenshotTest`
(`pay-enabled`, `pay-disabled-total`, `pay-disabled-members`, `settled-host`,
`settled-member`, `me-demo-section`, `settled-host-dark`).

| Element | Android (dp), provisional |
| ------- | ------------------------- |
| Pay button | Host only, last item of the total card, 4 below the controls. Full-width capsule 52 tall, brand blue `#0087E0`, "Pay · $78.66" (button role, white). No ripple or elevation: a press dims to 80%. While the write runs: white spinner |
| Pay disabled | Same capsule in `#ACACAD`, label is the reason: "Add the total", "Waiting for friends to join", "This party has expired" (expired: shown under the frozen total) |
| Settled screen | Replaces the cards. Seat card: avatars 56, first name 14 below; every non-host seat gets a 22 green `#34C759` check badge with a 2 white ring, offset 4 right and down. Success card 12 below: 140-tall stage with a 76 green circle and white check (stroke 6), then "You collected" / "You paid" (body, secondary), the amount (balance role, 36 bold), "Everyone's share is in your balance." / "Paid to {Host} for Tony's." (subtitle, secondary), then the black "Back to Home" pill |
| Settlement motion | Plays once, only on a phone that saw the party open: 150 ms, then each payer's badge pops in (spring 0.45) 280 ms apart with a light tick haptic; then the check springs in (0.5) while a 48-piece confetti burst flies out and fades over 1.4 s; success haptic. Reduce Motion / animations off: final state at once, success haptic kept |
| Balance (Me) | Rolls old → new over 600 ms (ease out), integer cents, negative values as "-$12.34"; instant with animations off |
| Demo section (Me) | Last item of the Me list: "Demo" section heading, then a white card: hint (14 secondary), black "Demo reset" pill, red error line under it on failure. Confirmation is the app's own dialog card ("Reset the demo?", black "Reset", grey "Cancel") |

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
