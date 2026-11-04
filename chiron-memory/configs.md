# config

Setup and configuration — env vars, flags, how to run the project.

## Firestore target is chosen by FIREBASE_TARGET

What: The seed/reset scripts need FIREBASE_TARGET=emulator|hosted plus FIREBASE_PROJECT_ID; emulator also needs FIRESTORE_EMULATOR_HOST, hosted needs GOOGLE_APPLICATION_CREDENTIALS (a key outside the repo) and refuses to run if FIRESTORE_EMULATOR_HOST is set; reset on hosted needs --yes · Why: the work order forbids writing anywhere unless the target is explicit, and the Admin SDK silently honours FIRESTORE_EMULATOR_HOST · Where: backend/lib/env.js, backend/.env.example

## Emulator tests need Java and run serially

What: npm run verify (backend/) runs the Firestore emulator through emulators:exec, which needs a JDK 21 on PATH (e.g. /opt/homebrew/opt/openjdk@21/bin, or a Temurin 21 JAVA_HOME); emulator test files run with --test-concurrency=1, and the rules test uses its own project id (demo-split-party-rules, hence singleProjectMode false) · Why: the acceptance test asserts exact collection contents and would race with the rules test on shared data · Where: backend/package.json, backend/firebase.json

## Android build runs on Java 25 via Gradle Daemon JVM criteria

What: android/ uses Gradle 9.8.2 + AGP 9.5 (built-in Kotlin, no org.jetbrains.kotlin.android plugin) + Kotlin 2.4.21; gradle/gradle-daemon-jvm.properties pins the daemon to Java 25 and the foojay resolver in settings.gradle.kts downloads it if missing, so Android Studio and ./gradlew need no Gradle JDK setting; app bytecode is Java 21 (compileOptions), compile/target SDK 37 · Why: Android Studio bundles Java 25, which Gradle 8.11 could not run on ("incompatible with the Gradle JVM version 25") · Where: android/gradle/gradle-daemon-jvm.properties, android/settings.gradle.kts, android/app/build.gradle.kts · Learned: with built-in Kotlin the Kotlin version follows the Compose compiler plugin version on the classpath (AGP alone pins 2.2.10); change the JVM with ./gradlew updateDaemonJvm --jvm-version=N
