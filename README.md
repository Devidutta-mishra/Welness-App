# Yourswelnes

Android application for member check-in and field tracking. Built with Kotlin and
Jetpack Compose, the app provides location tracking (foreground + scheduled background),
camera capture, push notifications, and offline-first data sync.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Min SDK:** 29 · **Target SDK:** 36 · **Compile SDK:** latest
- **Build:** Gradle (Kotlin DSL) · Android Gradle Plugin 9.2.1 · Gradle 9.4.1
- **JDK:** 11
- **Local storage:** Room
- **Networking:** Retrofit / OkHttp
- **Messaging:** Firebase Cloud Messaging
- **DI:** Hilt

Application ID: `com.yourwelnes.yourswelnes`

## Project Structure

```
app/src/main/java/com/example/yourswelnes/
├── core/          # Services, notifications, receivers, location, networking
├── di/            # Dependency-injection modules
├── feature/       # Feature screens (camera, etc.)
└── navigation/    # Navigation graph and destinations
```

## Prerequisites

- Android Studio (latest stable) or the Android command-line tools
- JDK 11
- Android SDK with the required platforms installed

## Environment Setup

1. Clone the repository.
2. Create `local.properties` in the project root (Android Studio creates this
   automatically). It must point to your Android SDK:

   ```properties
   sdk.dir=/path/to/Android/sdk
   ```

3. For **release builds**, also add the signing credentials to `local.properties`:

   ```properties
   KEYSTORE_FILE=/path/to/release.keystore
   KEYSTORE_PASSWORD=********
   KEY_ALIAS=********
   KEY_PASSWORD=********
   ```

   `local.properties` is not checked into version control — keep these values out of git.

## Firebase Setup

The app uses Firebase (Cloud Messaging). The Firebase config file is not committed.

1. In the [Firebase console](https://console.firebase.google.com/), create/open the
   project and register an Android app with package name `com.yourwelnes.yourswelnes`.
2. Download `google-services.json`.
3. Place it at `app/google-services.json`.

The Google Services Gradle plugin reads this file at build time.

## Build & Run

Run from the project root.

**Debug build / install on a connected device:**

```bash
./gradlew assembleDebug
./gradlew installDebug
```

**Run tests:**

```bash
./gradlew testDebugUnitTest
```

## Release Builds (APK / AAB)

Ensure the signing credentials (see Environment Setup) are present in `local.properties`.

**APK:**

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

**App Bundle (AAB) — for Play Store upload:**

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```
