# App shell design reference

The iOS and Android shells must look the same. These screenshots are the
visual reference, and the values below are what both apps use.

| File | What it shows |
| ---- | ------------- |
| [`home-feed-reference-1.jpg`](home-feed-reference-1.jpg) | Feed cards: time line, "Name paid Name for memo" sentence with inline avatars, round grey like/comment buttons, floating bottom bar with a round blue action button |
| [`home-feed-reference-2.jpg`](home-feed-reference-2.jpg) | Header: round avatar on the left, search/QR capsule on the right, banner area above the feed |

The screenshots come from a real payments app, so their ads, merchants and
people are only examples of layout. The apps show the seeded data from
[`firestore-schema.md`](../firestore-schema.md).

## Palette (sampled from the screenshots)

| Token | Hex | Used for |
| ----- | --- | -------- |
| Brand blue | `#0087E0` | Pay/Request button, app title, icons in empty/error states |
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

A floating capsule with three items in this order: **Home**, **Pay/Request**
(the round brand-blue button) and **Me**, each with an icon and a label.

## Glossary

- **dp**: density-independent pixel, the Android equivalent of an iOS point (pt).
- **pt**: iOS point, the layout unit (2–3 physical pixels on current iPhones).
