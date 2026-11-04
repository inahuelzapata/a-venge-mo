# contradiction

A memory that clashes with newer reality — flagged to be resolved.

## iOS and Android shells use different palettes and avatar colours

What: The two conventions for docs/design disagree: iOS uses brand blue #0087E0, page grey #F6F6F7 and avatars #0087E0/#E5487D/#2EAF6F (sampled from the screenshots, docs/design/README.md); Android uses brand blue #0B5FD6, page grey #F2F3F5, avatars #3D7BE0/#E86A5C/#2FA37A and dates like "Oct 27" for older rows · Why: both shells were built in parallel; the checkpoint compares iOS and Android phones side by side, so one set must be chosen and the other app aligned · Where: ios/SplitParty/UI/Theme.swift, ios/SplitParty/UI/AvatarView.swift, android/app/src/main/java/com/splitparty/android/ui/theme/Brand.kt, docs/design/README.md
