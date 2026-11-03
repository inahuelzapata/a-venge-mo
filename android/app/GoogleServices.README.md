# Where google-services.json goes

The Firebase Android configuration file is **not committed** (it is git-ignored).

1. Firebase console → Project settings → Your apps → Android app with package
   `com.splitparty.android` → download `google-services.json`.
2. Put it at `android/app/google-services.json`.

Until the file is there, the placeholder screen shows
"google-services.json missing" instead of failing silently.
