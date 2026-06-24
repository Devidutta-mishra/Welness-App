# Yourswelnes

Yourswelnes is an Android app for **reliable field tracking of club members**. It keeps
members' location updated in the background — even when the app is closed or the device is
idle — and pairs that with check-in capture, schedules, and push notifications so teams
always know who is active and where.

The app is built to stay running. It survives reboots, network drops, battery-optimization
restrictions, and aggressive OEM background limits, syncing data offline-first and uploading
as soon as connectivity returns.

## What the App Does

- **Background location tracking** — Continuous GPS tracking that runs as a foreground
  service and keeps reporting even when the app is in the background or shut. Locations are
  stored locally first and uploaded automatically.

- **Reliable, always-on operation** — Restarts itself after device reboot, uses scheduled
  alarms and watchdog/backup workers to recover if the service is killed, and adapts to
  device standby and power-saving states so tracking doesn't silently stop overnight.

- **Offline-first sync** — Location records, notifications, and app data are saved on the
  device and synced to the server when a connection is available, so nothing is lost when
  the network is unreliable.

- **Guided permission setup** — A step-by-step setup wizard walks members through the
  permissions tracking needs (location, background access, exact alarms, battery
  optimization) with device-specific instructions for popular phone brands.

- **Secure access** — Login with token-based authentication and an optional biometric
  app-lock that protects the app after it's been idle.

- **Camera check-in** — Capture photos in-app and attach them to the relevant group, for
  on-site verification and reporting.

- **Schedules & club info** — Members can view their club details and group schedules,
  kept in sync with the server.

- **Push notifications** — Real-time alerts via Firebase Cloud Messaging, with notification
  history stored on the device and deep links into the right screen.

- **App monitoring** — Reports installed-app information to support compliance and
  administration.

- **Dashboard hand-off** — Opens the connected web dashboard with the member's session, so
  detailed views are available without a separate login.

## Built With

Kotlin · Jetpack Compose · Material 3 · Hilt · Room · Retrofit/OkHttp ·
WorkManager · Firebase Cloud Messaging · CameraX · Google Play Services Location
