# Yourswelnes — Android Architecture

> Auto-generated architecture reference for the **Yourswelnes** Android app.
> Application ID: `com.yourwelnes.yourswelnes` · Namespace: `com.example.yourswelnes`
> minSdk 29 · targetSdk 36 · Kotlin + Jetpack Compose · Single-module (`:app`).

---
/Users/deviduttamishra/Desktop/yourswelnes/docs/architecture.md

## 1. Project Overview

Yourswelnes is a **100% Jetpack Compose**, single-activity Android application built on a
**feature-first Clean/MVVM architecture** with **Hilt** dependency injection. Its core purpose is
**reliable, offline-capable background GPS location tracking** for club members, surrounded by
supporting features: authentication, biometric app-lock, club/group schedules, a camera capture
flow, push notifications (FCM), installed-app monitoring, and a web dashboard hand-off.

### Technology Stack

| Concern | Technology |
|---|---|
| UI | Jetpack Compose, Material 3, Compose Navigation |
| Architecture | MVVM + Repository pattern, unidirectional state (`StateFlow` UI state + one-shot events) |
| DI | Hilt (Dagger) + `hilt-work` for WorkManager injection |
| Networking | Retrofit + OkHttp + Gson, `AuthInterceptor` for bearer token |
| Local persistence | Room (v6, 5 migrations) + Jetpack DataStore (Preferences) |
| Background work | WorkManager, Foreground Service, `AlarmManager` exact alarms, `BroadcastReceiver`s |
| Push | Firebase Cloud Messaging (FCM) |
| Location | Google Play Services `FusedLocationProviderClient` |
| Camera | CameraX (core, camera2, lifecycle, view) |
| Security | AndroidX Biometric, foreground app-lock timeout |
| Misc | Coil (images), Timber (logging), Custom Tabs / Browser |

### Backend

All REST traffic targets a single base URL **`https://ywadvance.com/`** (see `NetworkModule`).
Endpoints are plain `api/...` paths; auth is a bearer token attached by `AuthInterceptor`.

---

## 2. Folder Structure

```
app/src/main/java/com/example/yourswelnes/
├── YourswelnesApplication.kt        # @HiltAndroidApp; boots workers, FGS, FCM token, alarms
├── MainActivity.kt                  # Single AppCompatActivity host; notification intents; app-lock hooks
│
├── di/                              # Hilt modules (SingletonComponent)
│   ├── NetworkModule.kt             #   Retrofit/OkHttp + all *Api providers
│   ├── DatabaseModule.kt            #   Room DB + DAOs
│   ├── RepositoryModule.kt          #   @Binds interface → impl for every repository
│   ├── LocationModule.kt            #   FusedLocationProviderClient, LocationTracker
│   ├── NotificationModule.kt        #   NotificationManagerCompat
│   └── DataStoreModule.kt           #   (marker; DataStores are @Inject constructor)
│
├── navigation/
│   ├── Destinations.kt              # Route constants + typed route builders
│   └── AppNavGraph.kt               # NavHost: all composable destinations + nav side-effects
│
├── core/                            # Cross-feature infrastructure
│   ├── database/                    #   AppDatabase, entity/, dao/ + migrations 1→6
│   ├── datastore/                   #   Auth / Location / Fcm / PermissionOnboarding preferences
│   ├── network/                     #   AuthInterceptor
│   ├── location/                    #   FusedLocationTracker, Scheduler, Uploader, Alarm scheduler...
│   ├── service/                     #   LocationForegroundService
│   ├── worker/                      #   6 WorkManager CoroutineWorkers
│   ├── receiver/                    #   BootReceiver, TrackingAlarmReceiver
│   ├── notification/                #   App + Location notif managers, FCM service, deep link
│   ├── permission/                  #   PermissionChecker, BatteryOptimizationManager
│   ├── tracking/                    #   OEM profiles & instructions, StandbyBucketMonitor
│   └── ui/                          #   Theme, shared Compose components
│
└── feature/                         # Feature-first modules (ui / data / model)
    ├── auth/                        #   Login
    ├── onboarding/                  #   Splash, Welcome, Requirements gate
    ├── biometric/                   #   Biometric lock screen + AppLockManager
    ├── home/                        #   Home dashboard, club + group schedule
    ├── camera/                      #   CameraX capture + preview + group selection
    ├── location/                    #   Location status, config, records
    ├── notifications/               #   Notification list
    ├── monitoring/                  #   Installed-app monitoring
    ├── dashboard/                   #   Web dashboard redirect URL
    └── tracking/                    #   Permission wizard + tracking setup UI
```

Each `feature/<name>/` typically contains:

```
feature/<name>/
├── ui/      → *Screen.kt (Compose), *ViewModel.kt, *UiState.kt, navigation events
├── data/    → *Repository.kt (interface), *RepositoryImpl.kt
│             ├── api/    → Retrofit interface
│             ├── dto/    → network DTOs
│             └── mapper/ → DTO ↔ domain mappers
└── model/   → domain models
```

---

## 3. Overall Architecture

```mermaid
graph TD
    subgraph Presentation["Presentation — Jetpack Compose"]
        ACT["MainActivity<br/>(single activity)"]
        NAV["AppNavGraph / Destinations"]
        SCREENS["Feature Screens<br/>(@Composable)"]
        VMS["ViewModels<br/>(StateFlow UiState + events)"]
    end

    subgraph Domain["Domain / Data — Repositories"]
        REPOS["Repository interfaces<br/>+ Impls"]
        MAPPERS["DTO ↔ Domain Mappers"]
    end

    subgraph DataSources["Data Sources"]
        API["Retrofit APIs<br/>(ywadvance.com)"]
        ROOM["Room Database<br/>(locations, app_monitoring, notifications)"]
        DS["DataStore<br/>(Auth, Location, Fcm, Onboarding)"]
    end

    subgraph Background["Background / Platform"]
        FGS["LocationForegroundService"]
        WORK["WorkManager Workers"]
        ALARM["AlarmManager + Receivers"]
        FCM["FCM Messaging Service"]
        LOC["FusedLocationProviderClient"]
    end

    subgraph DI["Hilt — SingletonComponent"]
        MODULES["Network / Database / Repository /<br/>Location / Notification / DataStore Modules"]
    end

    ACT --> NAV --> SCREENS --> VMS --> REPOS
    REPOS --> MAPPERS
    REPOS --> API
    REPOS --> ROOM
    REPOS --> DS
    FGS --> LOC
    FGS --> ROOM
    FGS --> REPOS
    WORK --> REPOS
    ALARM --> FGS
    FCM --> DS
    FCM --> ACT
    MODULES -.provides.-> REPOS
    MODULES -.provides.-> API
    MODULES -.provides.-> ROOM

    classDef pres fill:#dbeafe,stroke:#1e40af,color:#0b1f4d;
    classDef dom fill:#dcfce7,stroke:#166534,color:#062b15;
    classDef data fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef bg fill:#fae8ff,stroke:#86198f,color:#3b0738;
    classDef di fill:#f1f5f9,stroke:#475569,color:#1e293b;
    class ACT,NAV,SCREENS,VMS pres;
    class REPOS,MAPPERS dom;
    class API,ROOM,DS data;
    class FGS,WORK,ALARM,FCM,LOC bg;
    class MODULES di;
```

**Layering rule:** UI → ViewModel → Repository (interface) → Data source. ViewModels never touch
Retrofit, Room, or DataStore directly; repositories own all mapping between DTOs and domain models.

---

## 4. Package / Module Structure

```mermaid
graph LR
    APP["com.example.yourswelnes"]

    APP --> ROOT["App root<br/>Application · MainActivity"]
    APP --> DI["di"]
    APP --> NAVP["navigation"]
    APP --> CORE["core"]
    APP --> FEAT["feature"]

    subgraph CorePkg["core/*"]
        CORE --> C1["database"]
        CORE --> C2["datastore"]
        CORE --> C3["network"]
        CORE --> C4["location"]
        CORE --> C5["service"]
        CORE --> C6["worker"]
        CORE --> C7["receiver"]
        CORE --> C8["notification"]
        CORE --> C9["permission"]
        CORE --> C10["tracking"]
        CORE --> C11["ui"]
    end

    subgraph FeatPkg["feature/*"]
        FEAT --> F1["auth"]
        FEAT --> F2["onboarding"]
        FEAT --> F3["biometric"]
        FEAT --> F4["home"]
        FEAT --> F5["camera"]
        FEAT --> F6["location"]
        FEAT --> F7["notifications"]
        FEAT --> F8["monitoring"]
        FEAT --> F9["dashboard"]
        FEAT --> F10["tracking"]
    end

    classDef root fill:#e0e7ff,stroke:#3730a3,color:#1e1b4b;
    classDef core fill:#cffafe,stroke:#0e7490,color:#083344;
    classDef feat fill:#dcfce7,stroke:#166534,color:#052e16;
    class ROOT,DI,NAVP root;
    class C1,C2,C3,C4,C5,C6,C7,C8,C9,C10,C11 core;
    class F1,F2,F3,F4,F5,F6,F7,F8,F9,F10 feat;
```

---

## 5. Screen Navigation Flow

`AppNavGraph` is a single `NavHost` with `startDestination = "splash"`. Transitions are disabled
(`EnterTransition.None`) for an instant feel. The **Requirements gate** and **Permission wizard**
are re-entrant guards that can interrupt `HOME` on every `ON_RESUME`.

```mermaid
graph TD
    SPLASH["splash<br/>SplashScreen"]
    REQ["requirements/{nextDest}<br/>RequirementsScreen<br/>(internet + GPS gate)"]
    WELCOME["welcome<br/>WelcomeLandingScreen"]
    LOGIN["login<br/>LoginScreen"]
    BIO["biometric_lock<br/>BiometricLockScreen"]
    HOME["home<br/>HomeScreen"]
    PERM["location_permission<br/>PermissionWizardScreen"]
    TRACK["tracking_setup<br/>TrackingSetupScreen"]
    NOTIF["notifications<br/>NotificationScreen"]
    CAM["camera/{groupId}<br/>CameraScreen"]
    PREV["camera_preview/{photoUri}<br/>CameraPreviewScreen"]

    SPLASH -->|"logged in"| REQ
    SPLASH -->|"not logged in"| REQ
    REQ -->|"nextDest = biometric_lock"| BIO
    REQ -->|"nextDest = welcome"| WELCOME
    REQ -->|"nextDest = home (re-check)"| HOME
    WELCOME -->|"Sign In"| LOGIN
    LOGIN -->|"login success"| BIO
    BIO -->|"authenticated"| HOME

    HOME -->|"requirement missing"| REQ
    HOME -->|"permission missing (on resume)"| PERM
    HOME -->|"lock timeout"| BIO
    HOME -->|"logout"| LOGIN
    HOME -->|"select group"| CAM
    HOME -->|"bell icon / FCM tap"| NOTIF
    HOME -->|"OpenDashboard (Custom Tab)"| EXT["External Browser<br/>(web dashboard)"]

    PERM -->|"onDone → pop/navigate"| HOME
    TRACK -->|"onDone"| HOME
    CAM -->|"photo captured"| PREV
    PREV -->|"retake"| CAM
    NOTIF -->|"back"| HOME

    classDef onboard fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef auth fill:#fee2e2,stroke:#991b1b,color:#450a0a;
    classDef main fill:#dbeafe,stroke:#1e40af,color:#0b1f4d;
    classDef ext fill:#f1f5f9,stroke:#475569,color:#1e293b;
    class SPLASH,REQ,WELCOME onboard;
    class LOGIN,BIO auth;
    class HOME,PERM,TRACK,NOTIF,CAM,PREV main;
    class EXT ext;
```

### Destinations reference

| Route | Screen | Arguments |
|---|---|---|
| `splash` | SplashScreen | — |
| `requirements/{nextDest}` | RequirementsScreen | `nextDest: String` |
| `welcome` | WelcomeLandingScreen | — |
| `login` | LoginScreen | — |
| `biometric_lock` | BiometricLockScreen | — |
| `home` | HomeScreen | — |
| `location_permission` | PermissionWizardScreen | — |
| `tracking_setup` | TrackingSetupScreen | — |
| `notifications` | NotificationScreen | — |
| `camera/{groupId}` | CameraScreen | `groupId: Long` |
| `camera_preview/{photoUri}` | CameraPreviewScreen | `photoUri: String (encoded)` |

---

## 6. MVVM Relationships

Every screen follows the same contract: the **ViewModel** exposes an immutable `StateFlow<UiState>`
plus a `Flow` of one-shot navigation events; the **Composable** is stateless and calls ViewModel
methods. `hiltViewModel()` supplies each ViewModel.

```mermaid
graph LR
    subgraph UI["Composable Screens"]
        S_LOGIN["LoginScreen"]
        S_HOME["HomeScreen"]
        S_NOTIF["NotificationScreen"]
        S_CAM["CameraScreen"]
        S_BIO["BiometricLockScreen"]
    end

    subgraph VM["ViewModels"]
        V_LOGIN["LoginViewModel"]
        V_HOME["HomeViewModel"]
        V_GROUP["GroupScheduleViewModel"]
        V_NOTIF["NotificationViewModel"]
        V_CAM["CameraViewModel"]
        V_GSEL["GroupSelectionViewModel"]
        V_BIO["BiometricViewModel"]
        V_LOC["LocationStatusViewModel"]
        V_SPLASH["SplashViewModel"]
        V_REQ["RequirementsViewModel"]
        V_WIZ["PermissionWizardViewModel"]
        V_SETUP["TrackingSetupViewModel"]
    end

    subgraph ST["UI State + Events"]
        U1["LoginUiState / LoginEvent"]
        U2["HomeUiState / HomeNavigationEvent"]
        U3["NotificationUiState"]
        U4["CameraUiState"]
        U5["BiometricUiState"]
    end

    subgraph R["Repositories"]
        R_AUTH["AuthRepository"]
        R_CLUB["ClubRepository"]
        R_GROUP["GroupDetailsRepository"]
        R_NOTIF["NotificationRepository"]
        R_DASH["DashboardRepository"]
        R_LCFG["LocationConfigRepository"]
        R_BIO["BiometricRepository"]
        R_REQ["RequirementsRepository"]
    end

    S_LOGIN --> V_LOGIN --> U1
    S_HOME --> V_HOME --> U2
    S_NOTIF --> V_NOTIF --> U3
    S_CAM --> V_CAM --> U4
    S_BIO --> V_BIO --> U5

    V_LOGIN --> R_AUTH
    V_HOME --> R_AUTH
    V_HOME --> R_CLUB
    V_HOME --> R_NOTIF
    V_HOME --> R_DASH
    V_HOME --> R_LCFG
    V_HOME --> R_GROUP
    V_HOME --> ALM["AppLockManager"]
    V_GROUP --> R_GROUP
    V_GSEL --> R_GROUP
    V_CAM --> R_GROUP
    V_NOTIF --> R_NOTIF
    V_BIO --> R_BIO
    V_BIO --> ALM
    V_SPLASH --> R_AUTH
    V_REQ --> R_REQ
    V_LOC --> PC["PermissionChecker / BatteryOptimizationManager"]
    V_WIZ --> PC
    V_SETUP --> PC

    classDef ui fill:#dbeafe,stroke:#1e40af,color:#0b1f4d;
    classDef vm fill:#dcfce7,stroke:#166534,color:#052e16;
    classDef st fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef repo fill:#fae8ff,stroke:#86198f,color:#3b0738;
    class S_LOGIN,S_HOME,S_NOTIF,S_CAM,S_BIO ui;
    class V_LOGIN,V_HOME,V_GROUP,V_NOTIF,V_CAM,V_GSEL,V_BIO,V_LOC,V_SPLASH,V_REQ,V_WIZ,V_SETUP vm;
    class U1,U2,U3,U4,U5 st;
    class R_AUTH,R_CLUB,R_GROUP,R_NOTIF,R_DASH,R_LCFG,R_BIO,R_REQ,ALM,PC repo;
```

### ViewModel → Repository matrix

| ViewModel | Repositories / Managers |
|---|---|
| `LoginViewModel` | AuthRepository |
| `SplashViewModel` | AuthRepository |
| `HomeViewModel` | AuthRepository, ClubRepository, NotificationRepository, DashboardRepository, LocationConfigRepository, GroupDetailsRepository, AppLockManager |
| `GroupScheduleViewModel` | GroupDetailsRepository |
| `NotificationViewModel` | NotificationRepository |
| `CameraViewModel` | GroupDetailsRepository (+ CameraX) |
| `GroupSelectionViewModel` | GroupDetailsRepository |
| `BiometricViewModel` | BiometricRepository, AppLockManager |
| `LocationStatusViewModel` | PermissionChecker / BatteryOptimizationManager |
| `RequirementsViewModel` | RequirementsRepository |
| `PermissionWizardViewModel` | BatteryOptimizationManager, PermissionChecker |
| `TrackingSetupViewModel` | BatteryOptimizationManager, PermissionChecker |

---

## 7. Repository & Data Source Flow

```mermaid
graph TD
    subgraph Repositories
        AUTH["AuthRepositoryImpl"]
        CLUB["ClubRepositoryImpl"]
        GROUP["GroupDetailsRepositoryImpl"]
        DASH["DashboardRepositoryImpl"]
        LCFG["LocationConfigRepositoryImpl"]
        LREC["LocationRepositoryImpl"]
        NOTIF["NotificationRepositoryImpl"]
        MON["AppMonitoringRepositoryImpl"]
        BIO["BiometricRepositoryImpl"]
        REQ["RequirementsRepositoryImpl"]
    end

    subgraph Retrofit["Retrofit APIs"]
        A_AUTH["AuthApi"]
        A_CLUB["ClubApi"]
        A_GROUP["GroupDetailsApi"]
        A_DASH["DashboardApi"]
        A_LOC["LocationApi"]
        A_NOTIF["NotificationApi"]
        A_MON["AppMonitoringApi"]
    end

    subgraph RoomDB["Room DAOs"]
        D_LOC["LocationDao"]
        D_NOTIF["NotificationDao"]
        D_MON["AppMonitoringDao"]
    end

    subgraph DataStore["DataStore"]
        P_AUTH["AuthPreferences"]
        P_LOC["LocationPreferences"]
        P_FCM["FcmPreferences"]
        P_ONB["PermissionOnboarding"]
    end

    AUTH --> A_AUTH
    AUTH --> P_AUTH
    AUTH --> P_FCM
    CLUB --> A_CLUB
    CLUB --> P_LOC
    GROUP --> A_GROUP
    GROUP --> P_AUTH
    DASH --> A_DASH
    DASH --> P_AUTH
    LCFG --> A_LOC
    LCFG --> P_LOC
    LREC --> D_LOC
    NOTIF --> A_NOTIF
    NOTIF --> D_NOTIF
    NOTIF --> P_AUTH
    MON --> A_MON
    MON --> D_MON
    MON --> P_AUTH
    BIO --> SYS["BiometricManager (system)"]
    REQ --> SYS2["Connectivity / LocationManager (system)"]

    classDef repo fill:#fae8ff,stroke:#86198f,color:#3b0738;
    classDef api fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef room fill:#cffafe,stroke:#0e7490,color:#083344;
    classDef ds fill:#dcfce7,stroke:#166534,color:#052e16;
    class AUTH,CLUB,GROUP,DASH,LCFG,LREC,NOTIF,MON,BIO,REQ repo;
    class A_AUTH,A_CLUB,A_GROUP,A_DASH,A_LOC,A_NOTIF,A_MON api;
    class D_LOC,D_NOTIF,D_MON room;
    class P_AUTH,P_LOC,P_FCM,P_ONB ds;
```

**Pattern:** Repositories with both an API and a DAO use **offline-first** semantics — write to Room
first, sync to the network opportunistically (notifications, app monitoring, location records).
Config repositories cache the latest server values into DataStore so background services can read
them with zero network dependency.

---

## 8. Authentication Flow

```mermaid
sequenceDiagram
    actor User
    participant Splash as SplashViewModel
    participant Login as LoginViewModel
    participant Repo as AuthRepository
    participant FCM as FcmPreferences
    participant Api as AuthApi (api/login)
    participant Prefs as AuthPreferences
    participant Bio as BiometricViewModel
    participant Home as HomeScreen

    User->>Splash: App launch
    Splash->>Repo: isLoggedIn()
    Repo->>Prefs: read token
    alt token present
        Splash-->>User: → requirements → biometric_lock
    else no token
        Splash-->>User: → requirements → welcome → login
    end

    User->>Login: enter phone + password
    Login->>Repo: login(phone, password)
    Repo->>FCM: getFcmToken() (or fetch now)
    Repo->>Api: POST api/login { phone, password, deviceToken }
    Api-->>Repo: { token, user, profileImage, redirect }
    alt valid
        Repo->>Prefs: saveAuthData(token, user)
        Repo-->>Login: Result.success(user)
        Login-->>User: emit NavigateToBiometric
        User->>Bio: biometric prompt
        Bio-->>Home: onAuthenticated → home
    else invalid
        Repo-->>Login: AuthException("Invalid phone or password")
    end
```

**Token attachment:** After login, every subsequent request carries the bearer token automatically:

```mermaid
graph LR
    REQ["Outgoing request"] --> INT["AuthInterceptor"]
    INT -->|read| PREFS["AuthPreferences.token"]
    INT -->|"Authorization: Bearer &lt;token&gt;"| OK["OkHttp → ywadvance.com"]
    classDef n fill:#dbeafe,stroke:#1e40af,color:#0b1f4d;
    class REQ,INT,PREFS,OK n;
```

**App-lock:** `AppLockManager` (singleton) records background time on `MainActivity.onStop()`.
On `onStart()`, if more than `LOCK_TIMEOUT_MS` (60 s) elapsed, it sets `isLockRequired = true`,
which `HomeViewModel` observes and routes to `biometric_lock`. There is no server logout endpoint —
logout is client-side (clear DataStore) per `AuthApi` docs.

---

## 9. API & Firebase Interactions

### REST endpoints (base `https://ywadvance.com/`)

```mermaid
graph LR
    subgraph Client
        AUTH["AuthApi"]
        CLUB["ClubApi"]
        GROUP["GroupDetailsApi"]
        DASH["DashboardApi"]
        LOC["LocationApi"]
        NOTIF["NotificationApi"]
        MON["AppMonitoringApi"]
    end
    subgraph Backend["ywadvance.com"]
        E1["POST api/login"]
        E2["GET  api/user-club-details"]
        E3["POST api/group-details"]
        E4["POST api/generate-login-link"]
        E5["GET  api/location-tracking-time"]
        E6["POST api/store-location"]
        E7["GET  api/notifications"]
        E8["POST api/notifications/read"]
        E9["GET  api/app-download-list"]
        E10["POST api/app-list-store"]
    end
    AUTH --> E1
    CLUB --> E2
    GROUP --> E3
    DASH --> E4
    LOC --> E5
    LOC --> E6
    NOTIF --> E7
    NOTIF --> E8
    MON --> E9
    MON --> E10

    classDef c fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef e fill:#dcfce7,stroke:#166534,color:#052e16;
    class AUTH,CLUB,GROUP,DASH,LOC,NOTIF,MON c;
    class E1,E2,E3,E4,E5,E6,E7,E8,E9,E10 e;
```

### Firebase Cloud Messaging flow

```mermaid
sequenceDiagram
    participant FB as Firebase
    participant Svc as YwFirebaseMessagingService
    participant Auth as AuthPreferences
    participant FcmP as FcmPreferences
    participant Notif as AppNotificationManager
    participant Act as MainActivity
    participant Deep as NotificationDeepLink
    participant Nav as AppNavGraph

    Note over Svc: onNewToken(token)
    Svc->>FcmP: saveFcmToken(token)

    Note over Svc: onMessageReceived(message)
    Svc->>Auth: isLoggedIn()?
    alt not logged in
        Svc-->>Svc: suppress
    else logged in
        Svc->>FcmP: isNotificationShown(dedupKey)?
        alt duplicate
            Svc-->>Svc: suppress
        else new
            Svc->>FcmP: markNotificationShown(dedupKey)
            Svc->>Notif: show(trayId, title, body, notifId)
        end
    end

    Note over Act: user taps notification
    Act->>Deep: set(notifId)  (own PendingIntent OR FCM extra)
    Deep-->>Nav: pendingNotificationId emits
    Nav->>Nav: navigate to notifications + consume()
```

> **Payload note (verified on-device):** the backend frequently sends pushes **without**
> `notification_id` in the FCM data block. The service never rejects a push for missing it — it
> falls back to title/body keys and uses the FCM `messageId` as the dedup key.

---

## 10. Database / Entity Relationships

Room database `yourswelnes.db`, **version 6**, three independent tables (no foreign keys — each
table is scoped per-user via a `user_id` column).

```mermaid
erDiagram
    LOCATIONS {
        long id PK "autoGenerate"
        string user_id "indexed"
        double latitude
        double longitude
        float distance "from assigned club"
        long timestamp
        boolean uploaded "indexed"
        long created_at "indexed"
    }
    NOTIFICATIONS {
        int id PK
        string user_id "per-user isolation"
        string title
        string message
        string type
        boolean is_read "server-authoritative"
        boolean is_displayed "never re-show"
        string created_at
    }
    APP_MONITORING {
        int appId PK
        string appName
        string downloadLink
        string packageName "nullable"
        boolean isInstalled
    }

    AUTH_USER ||..o{ LOCATIONS : "user_id (logical)"
    AUTH_USER ||..o{ NOTIFICATIONS : "user_id (logical)"
    AUTH_USER {
        string id "from DataStore session"
    }
```

> `AUTH_USER` is **not** a Room table — the logged-in user lives in `AuthPreferences` (DataStore).
> `user_id` columns are logical foreign keys enforced in DAO queries so User A's rows never appear
> for User B on a shared device.

### Migration history

```mermaid
graph LR
    V1["v1<br/>locations"] -->|MIGRATION_1_2| V2["v2<br/>+ app_monitoring"]
    V2 -->|MIGRATION_2_3| V3["v3<br/>locations.user_id"]
    V3 -->|MIGRATION_3_4| V4["v4<br/>+ index (user_id,uploaded,created_at)"]
    V4 -->|MIGRATION_4_5| V5["v5<br/>+ notifications"]
    V5 -->|MIGRATION_5_6| V6["v6<br/>notifications.user_id"]
    classDef v fill:#cffafe,stroke:#0e7490,color:#083344;
    class V1,V2,V3,V4,V5,V6 v;
```

### Key DAO operations

| DAO | Notable queries |
|---|---|
| `LocationDao` | `getPendingLocations(userId, limit)` (uploaded=0, batched), `markAsUploaded(ids)`, `deleteUploaded()` |
| `NotificationDao` | `getAllForUser`, `getUndisplayedForUser`, `markRead`, `markDisplayed`, IGNORE-insert (never re-show) |
| `AppMonitoringDao` | `getAll()` (Flow), `replaceAll()` (txn delete+insert) |

---

## 11. Background / Location Tracking Architecture

This is the heart of the app — a multi-layered, **offline-first, Doze-resistant** pipeline designed
so collection survives overnight, OEM battery kills, reboots, and network loss.

```mermaid
graph TD
    subgraph Triggers["Start / Recovery Triggers"]
        APPBOOT["YourswelnesApplication.onCreate"]
        BOOT["BootReceiver<br/>(BOOT_COMPLETED / MY_PACKAGE_REPLACED)"]
        ALARM["TrackingAlarmReceiver<br/>(exact AlarmClock at window start)"]
        WATCH["LocationWatchdogWorker<br/>(periodic 15 min, no network)"]
    end

    subgraph Schedulers
        TAS["TrackingAlarmScheduler<br/>scheduleNextWindowStart()"]
        HANDOFF["AlarmHandoffWakeLock"]
    end

    FGS["LocationForegroundService<br/>(FOREGROUND_SERVICE_TYPE_LOCATION)"]

    subgraph Collection
        FLP["FusedLocationProviderClient<br/>requestLocationUpdates"]
        WL["Partial WakeLock<br/>(Doze-proof CPU hold)"]
        SCHED["LocationScheduler<br/>isInTrackingWindow / distance"]
        ROOM["LocationDao.insert<br/>(offline buffer)"]
    end

    subgraph Upload["Upload Pipeline"]
        ULOOP["in-service upload loop"]
        UWORKER["LocationUploadWorker<br/>(periodic 15m + one-time, network-constrained)"]
        UPLOADER["LocationUploader"]
        LAPI["LocationApi.storeLocations<br/>POST api/store-location"]
    end

    APPBOOT --> TAS
    APPBOOT --> FGS
    BOOT --> TAS
    BOOT --> FGS
    ALARM --> HANDOFF
    ALARM --> FGS
    ALARM --> TAS
    WATCH --> FGS
    TAS --> ALARM

    FGS --> FLP
    FGS --> WL
    FGS --> SCHED
    FLP --> ROOM
    HANDOFF -.released on first persisted fix.-> FGS

    FGS --> ULOOP --> UPLOADER
    APPBOOT --> UWORKER
    UWORKER --> UPLOADER
    UPLOADER --> ROOM
    UPLOADER --> LAPI
    UPLOADER -->|markAsUploaded / deleteUploaded| ROOM

    classDef trig fill:#fee2e2,stroke:#991b1b,color:#450a0a;
    classDef sched fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef svc fill:#fae8ff,stroke:#86198f,color:#3b0738;
    classDef col fill:#dbeafe,stroke:#1e40af,color:#0b1f4d;
    classDef up fill:#dcfce7,stroke:#166534,color:#052e16;
    class APPBOOT,BOOT,ALARM,WATCH trig;
    class TAS,HANDOFF sched;
    class FGS svc;
    class FLP,WL,SCHED,ROOM col;
    class ULOOP,UWORKER,UPLOADER,LAPI up;
```

### WorkManager workers

| Worker | Schedule | Purpose |
|---|---|---|
| `LocationUploadWorker` | Periodic 15 min + one-time | Drain pending Room rows → `api/store-location` (network-constrained) |
| `LocationWatchdogWorker` | Periodic 15 min (no network) | Restart FGS if OEM killed it during an active window |
| `ScheduleSyncWorker` | Periodic 30 min | Refresh tracking-time config from backend |
| `AppInstallationSyncWorker` | Periodic + one-time | Reconcile installed apps → `api/app-list-store` |
| `BackupTrackingWorker` | One-time (delayed) | Belt-and-braces tracking re-arm |
| `NotificationSyncWorker` | Cancelled (FCM replaces polling) | Legacy notification poll, now disabled |

### Platform components (AndroidManifest)

| Component | Type | Trigger |
|---|---|---|
| `LocationForegroundService` | Service (`location` type) | App boot, alarm, watchdog |
| `YwFirebaseMessagingService` | Service | `com.google.firebase.MESSAGING_EVENT` |
| `BootReceiver` | Receiver | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` |
| `TrackingAlarmReceiver` | Receiver | Exact alarm at tracking-window start |

---

## 12. Feature Breakdown

```mermaid
mindmap
  root((Yourswelnes))
    Auth
      LoginScreen and LoginViewModel
      AuthRepository to AuthApi
      AuthPreferences - token plus user
    Onboarding
      SplashScreen - routing
      WelcomeLandingScreen
      RequirementsScreen - internet plus GPS gate
    Biometric
      BiometricLockScreen
      AppLockManager - 60s timeout
      AuthenticationManager
    Home
      HomeScreen plus cards
      GroupScheduleCard
      Club plus Group plus Dashboard
    Camera
      CameraScreen - CameraX
      CameraPreviewScreen
      GroupSelectionDialog
    Location
      LocationStatusViewModel
      LocationConfig plus LocationRecord
      Foreground service pipeline
    Notifications
      NotificationScreen and VM
      FCM service plus deep link
      Offline-first - Room plus API
    Monitoring
      AppMonitoringRepository
      Installed-app reconciliation
    Dashboard
      generate-login-link to Custom Tab
    Tracking Setup
      PermissionWizardScreen
      TrackingSetupScreen
      OEM profiles plus battery opt
```

### Feature summaries

- **auth** — Phone + password login; attaches FCM device token; persists session in DataStore.
- **onboarding** — Splash decides initial route; Requirements gate enforces internet + GPS on every
  resume; Welcome is the unauthenticated landing.
- **biometric** — Foreground app-lock with a 60 s background timeout; biometric/device-credential prompt.
- **home** — Aggregates club details, group schedules, dashboard link, notifications; the central hub
  that also enforces the mandatory-permission contract on every `ON_RESUME`.
- **camera** — CameraX capture scoped to a selected group, with a preview/retake step.
- **location** — Surfaces tracking status & permission health; owns the config/record models that the
  foreground service consumes.
- **notifications** — Offline-first list (Room cache + REST), driven by FCM pushes with dedup + deep link.
- **monitoring** — Tracks which monitored apps are installed and reports status to the backend.
- **dashboard** — Requests a one-time login link and opens the web dashboard in a Custom Tab.
- **tracking** — Permission wizard + OEM-specific battery-optimization setup that guarantees the
  tracking pipeline can run reliably.

---

## 13. Dependency Injection (Hilt) Setup

All modules install into `SingletonComponent`. The app uses `@HiltAndroidApp`,
`@AndroidEntryPoint` (Activity, services), and `@HiltViewModel` + `@AssistedInject` workers via
`HiltWorkerFactory`.

```mermaid
graph TD
    APPC["@HiltAndroidApp<br/>YourswelnesApplication"]
    SC["SingletonComponent"]

    NM["NetworkModule<br/>OkHttp · Retrofit · 7 APIs"]
    DBM["DatabaseModule<br/>AppDatabase · 3 DAOs"]
    RM["RepositoryModule<br/>@Binds × 10 repos"]
    LM["LocationModule<br/>FusedLocationProviderClient · LocationTracker"]
    NTM["NotificationModule<br/>NotificationManagerCompat"]
    DSM["DataStoreModule<br/>(marker)"]

    APPC --> SC
    SC --> NM
    SC --> DBM
    SC --> RM
    SC --> LM
    SC --> NTM
    SC --> DSM

    RM -.consumes.-> NM
    RM -.consumes.-> DBM
    RM -.consumes.-> DSM

    SC --> VMFAC["@HiltViewModel ViewModels"]
    SC --> WORKFAC["HiltWorkerFactory<br/>@AssistedInject Workers"]
    SC --> ENTRY["@AndroidEntryPoint<br/>MainActivity · FGS · FCM Service"]

    classDef app fill:#e0e7ff,stroke:#3730a3,color:#1e1b4b;
    classDef mod fill:#fef9c3,stroke:#854d0e,color:#3b2503;
    classDef cons fill:#dcfce7,stroke:#166534,color:#052e16;
    class APPC,SC app;
    class NM,DBM,RM,LM,NTM,DSM mod;
    class VMFAC,WORKFAC,ENTRY cons;
```

| Module | Binding style | Provides |
|---|---|---|
| `NetworkModule` | `@Provides` | `HttpLoggingInterceptor`, `OkHttpClient`, `Retrofit`, and all 7 `*Api` interfaces |
| `DatabaseModule` | `@Provides` | `AppDatabase` (+5 migrations) and `LocationDao`, `NotificationDao`, `AppMonitoringDao` |
| `RepositoryModule` | `@Binds` | 10 repository interfaces → impls (Auth, Club, Dashboard, LocationConfig, Location, Notification, GroupDetails, AppMonitoring, Biometric, Requirements) |
| `LocationModule` | `@Provides` | `FusedLocationProviderClient`, `LocationTracker` ← `FusedLocationTracker` |
| `NotificationModule` | `@Provides` | `NotificationManagerCompat` |
| `DataStoreModule` | marker | DataStores are `@Singleton @Inject constructor` (auto-provided) |

---

## 14. Data Flow Explanation

### Read path (e.g. Home loading club + notifications)
1. `HomeScreen` collects `HomeViewModel.uiState` (a `StateFlow<HomeUiState>`).
2. `HomeViewModel` calls repositories (`ClubRepository`, `NotificationRepository`, …).
3. Each `*RepositoryImpl` reads its cache (Room/DataStore) and/or calls a `*Api` via Retrofit.
4. `AuthInterceptor` injects the bearer token from `AuthPreferences` into the request.
5. DTOs are mapped to domain models (`*Mapper`), returned as `Result<T>`.
6. The ViewModel folds results into a new immutable `HomeUiState`; Compose recomposes.

### Write path (location capture → upload)
1. `LocationForegroundService` registers continuous `FusedLocationProviderClient` updates inside the
   tracking window, holding a Doze-proof partial wake lock.
2. Each fix is mapped to a `LocationRecord` and **written to Room first** (`LocationDao.insert`) — the
   offline buffer. Upload never blocks collection.
3. The in-service upload loop **and** `LocationUploadWorker` independently drain pending rows:
   `getPendingLocations` → `LocationUploader` → `LocationApi.storeLocations` → `markAsUploaded` →
   `deleteUploaded`.
4. If offline, rows stay `uploaded = 0` and are retried on the next tick or when connectivity returns.

### Event path (one-shot navigation)
ViewModels emit navigation as a `Flow` of sealed events (`LoginEvent`, `HomeNavigationEvent`,
`SplashNavigationEvent`). `AppNavGraph` collects them in `LaunchedEffect` and performs `navController`
operations — keeping navigation out of UI state and immune to recomposition replays.

---

## 15. Architecture Observations

**Strengths**
- **Clean feature-first MVVM** with a strict UI → ViewModel → Repository → DataSource boundary; no
  data-source leakage into the UI.
- **Consistent UDF contract**: immutable `StateFlow` state + separate one-shot event flows everywhere.
- **Genuinely robust background story**: layered recovery (exact alarm + watchdog worker + START_STICKY
  + boot receiver), Doze-proof wake-lock handling, and an offline-first Room buffer make tracking
  resilient to OEM battery managers, reboots, and network loss.
- **Strong multi-user hygiene**: every per-user table filters by `user_id`, so accounts sharing a
  device cannot see each other's data.
- **Idempotent FCM handling**: dedup keys + `is_displayed` flag prevent duplicate/re-shown notifications.
- **Hilt** used idiomatically, including `@AssistedInject` workers via `HiltWorkerFactory`.

**Considerations / watch-items**
- The package namespace is still `com.example.yourswelnes` while the applicationId is
  `com.yourwelnes.yourswelnes` — harmless but worth aligning before long-term maintenance.
- Single Gradle module — fine at current size, but the clear `feature/*` + `core/*` split would
  modularize cleanly (`:feature:x`, `:core:y`) if build times or team scale demand it.
- `LocationForegroundService` is large and orchestrates collection, config refresh, upload, wake
  locks, and GPS-state UX. It is well-commented, but extracting the upload loop / window-management
  into collaborators would ease testing.
- Two parallel upload paths (in-service loop **and** `LocationUploadWorker`) provide redundancy by
  design; both funnel through `LocationUploader`/`LocationDao`, so they are safe but should stay in
  sync if upload logic changes.
- No server-side logout/refresh endpoints; session lifecycle is entirely client-managed via DataStore.

---

*Generated by static analysis of the `:app` module source tree. All Mermaid diagrams use standard
`graph`, `sequenceDiagram`, `erDiagram`, and `mindmap` syntax and are self-contained.*
