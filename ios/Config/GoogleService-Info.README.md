# Where GoogleService-Info.plist goes

The Firebase iOS configuration file is **not committed** (it is git-ignored).

1. Firebase console → Project settings → Your apps → iOS app whose bundle id
   matches `PRODUCT_BUNDLE_IDENTIFIER` → download `GoogleService-Info.plist`.
2. Put it at `ios/SplitParty/GoogleService-Info.plist`. That folder is
   synchronised with the Xcode project, so no project edit is needed.

Until the file is there, the placeholder screen shows
"GoogleService-Info.plist missing" instead of failing silently.
