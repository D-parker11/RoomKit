# RoomKit

RoomKit is a local-first Android rental room management application
for landlords in Phnom Penh.

This is an individual graduation project currently under development.

## Technology Stack

- Kotlin
- XML layouts and Fragments
- MVVM and Repository architecture
- Room Database with KSP
- WorkManager for local reminders
- Gradle Wrapper

## Main Features

- Property and room management
- Tenant registration and tenancy history
- Move-in and move-out workflows
- Electricity and water meter readings
- Invoice drafts, issued invoices and invoice items
- Full and partial payments with payment history
- Outstanding balances and local reminders
- Invoice PDF export and sharing
- Settings and reports

Feature verification is ongoing. This list is not a claim that all
acceptance tests have passed.

## Development Requirements

- Android Studio with support for Android Gradle Plugin 8.13.2
- Android SDK Platform 35
- Android device or emulator running API 30 or later
- Git
- Internet access for initial SDK and dependency downloads

Current locally verified build environment:

- Gradle Wrapper: 9.5.0
- Gradle runtime: JetBrains JDK 25.0.4
- App Kotlin plugin: 2.1.0
- Java/Kotlin bytecode target: 11
- Room: 2.6.1

The repository's Gradle daemon configuration may request Java 25.
Use the Gradle Wrapper included in this project.

## Run from a clean clone

1. Clone the repository:

   ```bash
   git clone https://github.com/D-parker11/RoomKit.git
   cd RoomKit
   ```

2. Open the cloned folder in Android Studio.

3. Install Android SDK Platform 35 through SDK Manager.

4. Allow Gradle synchronization and any required JDK downloads.
   Check the Gradle JDK configuration if synchronization fails.

5. Let Android Studio configure the local Android SDK location.
   `local.properties` is machine-specific and must not be committed.

6. Build and run JVM tests:
   - JVM tests: 1 test in ExampleUnitTest passed, with no failures or errors.
   - Billing-calculation tests: pending verification.
      ```bash
      chmod +x gradlew
      ./gradlew :app:assembleDebug :app:testDebugUnitTest
      ```

7. Start an API 30 or later emulator, select the `app`
   configuration, and click Run.

Debug APK location:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

Core offline workflows do not require backend credentials or API keys.

See `.env.example` for configuration notes. The application does not
automatically load this file.

Never commit signing keys, passwords, real tenant databases or backups.

## Verification Status

- Existing working-folder debug build: successful.
- JVM test task command: completed successfully; test count pending review.
- Clean-clone build and launch: pending.
- Full lifecycle and integration testing: pending.
- School README template compliance: pending template availability.

## Data Storage

Core application data is stored locally using Room.

Use fictional or anonymised records for demonstrations.
Uninstalling the application may remove locally stored data.

## Project Repository

https://github.com/D-parker11/RoomKit