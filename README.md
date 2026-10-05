# AniTec Android

Native Android client (Kotlin, Jetpack Compose) of **AniTec**, a livestock management platform for ranchers and veterinarians.
It is the mobile counterpart of the web app (`anitec-frontend`) and consumes the same ASP.NET Core API (`anitec-backend`).
Course project for 1ACC0238 – Aplicaciones para Dispositivos Móviles (UPC, 202620).

## Requirements

- Android Studio (current stable) with Android SDK 37 installed.
- Run Gradle with the JDK bundled in Android Studio (**Settings → Build Tools → Gradle → Gradle JDK**). Do not use a system JDK newer than the one Android Studio ships.
- A running backend (see below), or the hosted one.



## Run

Open the `anitec-android` folder in Android Studio, let Gradle sync, then run the `app` configuration.

| Build type | Backend |
|---|---|
| `debug` | `http://10.0.2.2:5191` (your machine as seen from the emulator) |
| `release` | `https://anitec-backend.onrender.com` (free tier: the first request can take about a minute) |

To use a physical device with a backend on your computer, add this line to `local.properties` (never committed) and the same host to `app/src/debug/res/xml/network_security_config.xml`:

```
anitec.debugServerUrl=http://192.168.1.50:5191
```
```
Command line:


./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug
```

## Architecture

One Gradle module (`app`), one package per bounded context, with the same layers as the report (section 2.6):

```
com.anitec.platform
├─ app/        navigation shell and app-level state
├─ core/       designsystem · network · session · i18n · common
└─ <context>/  domain · application · infrastructure · interfaces
```

- `domain`: models and repository interfaces. `application`: use cases. `infrastructure`: Retrofit, Room, repository implementations. `interfaces`: Compose screens and ViewModels.
- Stack: Compose + Material 3, Navigation Compose, Hilt, Retrofit/OkHttp/kotlinx.serialization, Room, DataStore + Tink (encrypted session token), Coil, WorkManager, CameraX and ML Kit.
- The API does not filter data by user, so the app filters on the client like the web app does and caches only the signed-in user's records.

## Conventions

- Code, identifiers and commit messages in English.
- GitFlow (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`), Conventional Commits, Semantic Versioning.
- English is the default UI language; Latin American Spanish (`es-419`) is selectable in the app. All user-facing text lives in `strings.xml`. 
- Accessibility: every icon-only control has a content description; touch targets are at least 48 dp.
