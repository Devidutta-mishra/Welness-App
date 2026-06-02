# Fitness Tracker Android App

## Project Overview

This is a production Android application built for a fitness organization.

The application's purpose is:

1. Authenticate users.
2. Show user information and assigned fitness group.
3. Collect location data during a specified time window.
4. Upload collected location data to backend APIs.
5. Capture timestamped photos.
6. Upload photos to backend APIs.
7. Receive notifications.
8. Open a web dashboard with automatic login.

The Android application is NOT responsible for business logic such as attendance calculation, geofencing, radius checking, analytics, or reporting.

The backend server performs all business logic.

The Android application acts only as a secure data collection and display client.

---

# Architecture Requirements

The project MUST use:

- Kotlin
- Jetpack Compose
- MVVM Architecture
- Repository Pattern
- Single Source Of Truth
- Hilt Dependency Injection
- Navigation Compose
- Retrofit
- Room
- DataStore
- WorkManager
- CameraX
- Google Location Services
- Coroutines
- StateFlow
- Timber

No shortcuts.

No direct API calls from UI.

No business logic inside Composables.

No global mutable state.

---

# Folder Structure

app/

core/
network/
datastore/
location/
camera/
notification/
worker/
util/

data/
remote/
api/
dto/
mapper/

local/
room/
datastore/

repository/

domain/
model/

feature/

auth/
presentation/

home/
presentation/

camera/
presentation/

notifications/
presentation/

dashboard/
presentation/

navigation/

di/

---

# Single Source Of Truth

UI must never directly call APIs.

Flow:

UI
↓
ViewModel
↓
Repository
↓
API / Room
↓
Repository
↓
ViewModel
↓
UI

Repositories are the single source of truth.

---

# Authentication

Authentication uses JWT.

Flow:

Splash Screen
↓
Check JWT in DataStore
↓
JWT Exists?
↓
Yes
↓
Validate Profile API
↓
Home

No
↓
Login Screen

Login API:

POST /auth/login

Request:

{
"username":"",
"password":""
}

Response:

{
"accessToken":"",
"refreshToken":"",
"expiresIn":86400
}

Store token inside DataStore.

All APIs use:

Authorization: Bearer <token>

---

# Biometric Login

Support biometric login.

Biometric login is only available after first successful login.

Store credentials securely.

Use Android BiometricPrompt.

---

# Splash Screen

Responsibilities:

1. Check login state.
2. Load token.
3. Navigate to Login or Home.

No business logic.

---

# Home Screen

Display:

User Name

Fitness Group

Camera Button

Dashboard Button

Notifications Button

Sync Status

Home Screen fetches user profile from repository.

---

# User Profile

Backend provides:

{
"id":"",
"name":"",
"groupId":"",
"groupName":"",
"cameraEnabled":true,
"trackingEnabled":true
}

Store locally.

---

# Installed Apps Detection

Check:

Telegram Installed

Zoom Installed

Send status to backend.

Example:

{
"userId":"",
"telegramInstalled":true,
"zoomInstalled":false
}

Only send.

Do not enforce installation.

---

# Camera Feature

Use CameraX.

Gallery access is NOT allowed.

Flow:

Capture Photo
↓
Add Timestamp Overlay
↓
Upload To Backend

Timestamp must include:

Date
Time

Optionally GPS coordinates if backend requests.

Camera availability is controlled by backend field:

cameraEnabled

If false:

Disable camera UI.

---

# Location Tracking

Purpose:

Collect location data.

No attendance calculation.

No radius calculations.

No geofence calculations.

Backend handles everything.

Tracking Window:

06:00 AM
to
12:00 PM

Configurable by backend.

Tracking Interval:

30 seconds

Configurable by backend.

Upload Interval:

5-10 minutes

Configurable by backend.

---

# Location Storage

Store every location in Room first.

Never directly upload.

Flow:

Location Service
↓
Room Database
↓
WorkManager
↓
Bulk Upload API

Support offline operation.

---

# WorkManager

Responsibilities:

Upload pending locations.

Retry failed uploads.

Respect network availability.

---

# Notifications

Display notifications received from backend.

Support:

Local Notifications

Future FCM support.

Notification routing handled by backend.

---

# Dashboard

Home screen contains Dashboard button.

Dashboard should auto-login user.

Flow:

App
↓
Dashboard Session API
↓
Temporary Session URL
↓
Open Browser

User automatically logged in.

Never expose JWT in URL.

Backend provides implementation.

---

# Navigation Graph

Splash

↓

Login

↓

Home

├── Camera

├── Notifications

└── Dashboard

Navigation must use Navigation Compose.

Navigation logic should be centralized.

Create AppNavGraph.kt

Create Destinations.kt

---

# Dependency Injection

Use Hilt.

Create:

NetworkModule

DatabaseModule

RepositoryModule

LocationModule

DataStoreModule

NotificationModule

---

# State Management

Use:

StateFlow

UiState classes

Loading

Success

Error

patterns.

No LiveData.

---

# API Layer

Use Retrofit.

Use DTOs.

Map DTOs to domain models.

Never expose DTOs directly to UI.

---

# Database

Room Database

Tables:

UserEntity

LocationEntity

NotificationEntity

PhotoUploadEntity

---

# Logging

Use Timber.

No Log.d.

---

# Code Quality Rules

No God classes.

No business logic in Activities.

No business logic in Composables.

Repositories own data.

ViewModels own UI state.

UI only renders state.

Use immutable data classes.

Prefer constructor injection.

Use sealed classes for UI state.

All code should be production quality and maintainable.