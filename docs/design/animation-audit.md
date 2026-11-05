# PoC animation audit (iOS × Android)

The nine animations the Hackathon PoC calls first-class, checked on the
**running apps** (not the code) before and after the animation-polish work
order. Values and timings live in [visual-conventions.md](visual-conventions.md);
this page records only whether each one is there.

**Legend:** present · partial (some of it is there, or it differs visibly
between the platforms) · missing · deferred (left out on purpose, see the note).

## How it was checked (2026-11-05)

- Both apps pointed at the local Firestore emulator (`demo-split-party`), seeded.
  Android: AVD "Medium Phone API 37", 1080 × 2400, signed in as Christophe,
  driven with adb. iOS: iPhone 18 Pro simulator (iOS 27.2), signed in as Maxi.
  The "other phones" (start, join, remove, bill, tip, Pay) were played by an
  admin script writing the same documents the apps write.
- iOS can't be tapped from the shell, so the host's tiles, wrong picks, scan,
  host-side Pay and the Me balance were recorded while the existing fixture UI
  tests drove the app (`PartyFlowUITests`, `TotalFlowUITests`, `PayFlowUITests`).
- Every run was screen-recorded (`simctl io recordVideo`, `adb screenrecord`),
  and frames were pulled at 50–100 ms intervals to judge order, overlap and timing.
- Reduce Motion: Android rerun with all three animator scales at 0. iOS judged
  from the gates in code before the change; re-checked live after.
- Not covered by simulators: haptics (no actuator), and 60 fps on the three
  physical demo phones. Both need the real-device run listed at the end.

## Before (start of the work order)

| # | Animation | iOS | Android | What was seen |
| - | --------- | --- | ------- | ------------- |
| 1a | Merchant card slides in + one shimmer | present | partial | iOS slides in from the trailing edge with a spring, then one sweep. Android slides **up** a third of its height with a fade (450 ms), sweep 1.0 s linear (iOS 0.9 s ease-in-out) |
| 1b | Merchant icon bobs continuously | deferred | deferred | The fork-and-knife is drawn into the banner artwork on both. Deferred by the product owner: the banner becomes dynamic in a later work order |
| 2 | Join banner drops in + pulsing dot | present | present | Both drop in from the top within ~1 s of Start. Android's dot pulses 1.1 s on the host avatar; iOS 1.2 s, dot only |
| 3 | Emoji tiles pop in one by one, then bob | present | partial | Both pop in sequence, never together. Android 120 ms apart and bobs 1.8 s; iOS 140 ms, 1.4 s |
| 4a | Joinee avatar springs in (every phone) | present | present | Both scale in with an overshoot on host and members |
| 4b | Removed/leaving avatar springs out | present | **missing** | iOS shrinks it out (reverse of the entry). Android: the avatar disappears in one frame |
| 4c | Empty seat pulses | present | partial | iOS scales to 0.94 and fades the fill. Android fades the whole seat, no scale |
| 5 | Share and total roll | present | partial | iOS rolls share **and** total (digit roll). Android rolls the share only; the total jumps |
| 6 | Laser over the receipt while OCR runs | present | present | Both sweep and stop when the confirm screen or the no-total fallback appears. Android has no 0.7 s minimum, so a fast read shows it for ~0.5 s |
| 7 | Wrong pick shakes + error haptic | partial | partial | First and second picks shake only the picked option on both. **Third pick:** iOS starts the shake but the locked screen fades in after ~0.1 s; Android goes straight to Locked with no shake. Android shakes ±14 over 360 ms, iOS ±10 three times over 0.4 s |
| 8 | Pay: ticks one by one → confetti → check | partial | partial | Host phones tick one by one on both. iOS **member** phones tick every badge in the same frame. Android: 48 confetti pieces over 1.4 s in its own palette and a green check; iOS 90 over 2.4 s, brand palette, blue check |
| 9a | Feed rows drop in one by one + fading highlight | present | partial | iOS drops the payments in 250 ms apart, highlight fading after 1.5 s. Android shows both new rows in the same frame (highlight present) |
| 9b | Me balance rolls | present | present | Both roll through intermediate values to the exact new balance |
| — | Reduce Motion → settled state | present | present | Android with scale 0: card, tiles, seats and share are all in place at once. iOS gates every animation above on Reduce Motion |

Other things seen while auditing (outside the nine):
- iOS: on a cold launch against a fresh emulator database, every seeded feed
  row flashes the new-row highlight once.
- Android reshuffles the join options with no motion; iOS springs them.

## After (end of the work order, same setup, re-recorded)

| # | Animation | iOS | Android | What changed / what was seen |
| - | --------- | --- | ------- | ---------------------------- |
| 1a | Merchant card slides in + one shimmer | present | present | Android now matches iOS: 0.35 s after Home appears, from the trailing edge with the iOS spring, one sweep 0.9 s ease-in-out after 0.25 s. Both play it once per session, after the demo switch comes back on, and again after a Demo reset (verified on both: reset → Home → slide + sweep) |
| 1b | Merchant icon bobs continuously | deferred | deferred | Unchanged by decision: the icon is part of the banner artwork, which becomes dynamic in a later work order (chiron-memory decision "merchant-banner icon bob is deferred") |
| 2 | Join banner drops in + pulsing dot | present | present | Android dot now 1.2 s ease-out, halo to 2.6× (iOS values) |
| 3 | Emoji tiles pop in one by one, then bob | present | present | Android now 140 ms apart from 20% and transparent (iOS spring), then ±3 bob over 1.4 s, alternate tiles out of step |
| 4a | Joinee avatar springs in (every phone) | present | present | Both use the iOS spring (from 30% with opacity). iOS also plays a success haptic on the joining phone at the moment its join succeeds (it had none before; the host and other members already had one) |
| 4b | Removed/leaving avatar springs out | present | present | Android keeps the departing seat in place while the same spring plays backwards, then the seat empties and pulses (recorded: Annie springs in at 1.5 s, out at 4.75 s, empty seat back at 5.25 s) |
| 4c | Empty seat pulses | present | present | Android scales to 0.94 and fades, 0.9 s each way |
| 5 | Share and total roll | present | present | Android's total now rolls with the share (0% → 20%: $100.34 … $120.00, share ends at exactly $60.00) |
| 6 | Laser over the receipt while OCR runs | present | present | Android now eases in and out and stays at least 0.7 s (recorded 0.9 s on a fast "no total" read), gone the moment the fallback shows |
| 7 | Wrong pick shakes + error haptic | present | present | Both shake ±10 three times in 0.4 s, only the picked option. The third wrong pick now completes its shake on both before "Too many wrong codes" shows (recorded on iOS: outline and shake for 0.45 s, then the locked screen) |
| 8 | Pay: ticks one by one → confetti → check | present | present | iOS member phones now tick one by one (the open → paid swap no longer crossfades over the first tick). Android uses the iOS sequence (100 ms, 280 ms apart, 150 ms), the iOS confetti (90 pieces, 2.4 s, from the top, brand palette) and tick and check springs. Same order on both (join order) |
| 9a | Feed rows drop in one by one + fading highlight | present | present | Android now drops a Pay's payments 250 ms apart, oldest first (recorded 2.0 s / 2.2 s), and fades the highlight over 1 s ease-out after 1.5 s |
| 9b | Me balance rolls | present | present | Unchanged |
| — | Reduce Motion → settled state | present | present | Re-checked live on both: iOS with Reduce Motion on (tiles together, no seat spring, paid screen final at once with no confetti, highlight goes without a fade); Android with all animator scales at 0 (card, banners, tiles, seats, share and settlement all settled at once) |

Leaving mid-animation: on Android, leaving the party screen while its tiles pop and
reopening it pops them afresh; backing out of the scan screen leaves no laser; a
quick Home → Me → Home round trip shows the card settled (fade, no replayed slide).

### Evidence and what it does not cover

- Before/after recordings of both apps (simulator and emulator) and the contact
  sheets used above are attached to the review, together with this table.
- **Not covered here, needed from the real demo phones:** the side-by-side
  recording of the three physical phones through a full demo run, haptics (the
  simulator and emulator have none), and the 60 fps check on the demo devices
  (Android GPU profile bars / iOS Instruments, including Pay ticks overlapping the
  feed drop-in). Parity is not evidenced until that recording is added.
