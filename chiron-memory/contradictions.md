# contradiction

A memory that clashes with newer reality — flagged to be resolved.

## Android no longer has its own palette: the iOS captures rule

What: The Android-only palette (brand blue #0B5FD6, page #F2F3F5, avatars #3D7BE0/#E86A5C/#2FA37A) and "Oct 27" dates are gone. Android now uses the values measured from docs/design/ios-captures/ (blue #0087E0, page #F6F6F7, avatars #0087E0/#E5487D/#2EAF6F) and iOS wording for relative times ("2 weeks ago") · Why: resolves the earlier palette clash between the parallel shells; the captures were chosen as the single visual contract · Where: android/app/src/main/java/com/splitparty/android/ui/theme/Brand.kt, docs/design/visual-conventions.md

## The neutral '$' Pay/Request placeholder is superseded by the Aven-mo mark

What: The Pay/Request centre slot no longer holds a neutral '$' placeholder in a 52 circle inside a 92-tall bar; on both platforms it is a 60 circle in #0087E0 with the white Aven-mo mark and no label (a11y "Pay or request"), seated in and centred on the 92-tall bar with no lift or shadow, and the login holds the mark above the wordmark · Why: supersedes the reshape's decision to keep neutral placeholders until brand work landed; the user rejected an earlier version lifted 16 above the bar with a shadow because it looked like it was floating · Where: android/app/src/main/java/com/splitparty/android/ui/shell/MainShell.kt, ios/SplitParty/UI/MainTabView.swift, docs/design/visual-conventions.md

## users.avatar is no longer an avatar key

What: users.avatar no longer holds avatar_maxi / avatar_annie / avatar_christophe; since the photo-URL seed it holds each person's photo URL · Why: supersedes the decision "Avatars are initials on a fixed colour per avatar key" (rewritten in decisions.md); any app code or fixture keyed on avatar_* is stale · Where: backend/lib/seedData.js
