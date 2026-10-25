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
| `release` | `https://anitec-backend-android.onrender.com` (free tier: the first request can take about a minute) |

The first time a `debug` build opens on Android 17 or later, it asks to allow access to devices on the local network. Tap **Allow**: without it the connection to your computer hangs and sign-in ends with "Could not reach the server". Release builds do not ask, because the hosted backend is a public address.

To use a physical device with a backend on your computer, add this line to `local.properties` (never committed) and the same host to `app/src/debug/res/xml/network_security_config.xml`:

```
anitec.debugServerUrl=http://192.168.1.50:5191
```

Command line:

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew installDebug
```

## Release build and distribution (Firebase App Distribution)

These are the steps followed to generate the signed APK and hand it to testers. The `release` build talks to the hosted backend (`https://anitec-backend-android.onrender.com`), so it does not need your computer to be running anything.

### 1. Create the signing key (once)

The keystore is yours: keep it **outside** the repository and keep a backup together with its passwords. Without it, a new version cannot be installed over an older one.

```
keytool -genkeypair -v -keystore C:/Users/<you>/anitec-release.jks -alias anitec -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` asks for the passwords and the certificate data. Never write the passwords in the repository or in chat. If `keytool` is not recognized, use the one in Android Studio's JDK (`<Android Studio>\jbr\bin\keytool.exe`).

### 2. Tell the build where the key is

Copy `keystore.properties.example` to `keystore.properties` (git ignores it) and fill in `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without that file the release APK is signed with the debug key: it installs, but it is not what should be distributed (Gradle prints `keystore.properties not found: signing the release build with the debug key`).

### 3. Build the APK

From the `anitec-android` folder, with Android Studio's JDK (PowerShell):

```
.\gradlew.bat --stop
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease
```

`--stop` ends Gradle daemons that another tool (for example the VS Code Java extension) may have started with a JDK that lacks `jlink`, which makes the build fail with `jlink executable ... does not exist`. The APK is written to `app/build/outputs/apk/release/app-release.apk` (R8 is on, about 25 MB). Raise `versionCode` in `app/build.gradle.kts` before every new upload; `versionName` is the number testers see (currently 0.2.0).

Install it on a physical device and check that sign-in works before distributing it. The first request to Render can take about a minute because the free tier sleeps.

### 4. Create the Firebase project and register the app (once)

1. In the [Firebase console](https://console.firebase.google.com) create a project (here `AniTec`). Gemini in Firebase and Google Analytics are not needed and can be turned off.
2. **Add app → Android**, package name `com.anitec.platform`, alias `AniTec`, no SHA-1. Skip the `google-services.json` download: App Distribution only needs the app to be registered, and the app does not use the Firebase SDK.
3. For the Flutter app (`anitec-flutter`) repeat the step with package `com.anitec.platform.flutter` and alias `AniTec Flutter`. Choose the **Android** icon, not the Flutter one (that flow installs tools and edits the project).

### 5. Upload the APK and send it to testers

1. Open **App Distribution** (under *DevOps y participación*, or search it in *Buscar productos*), pick the app in the selector at the top and press **Comenzar**.
2. In **Versiones**, drag `app-release.apk` into the dashed box (or **Explorar**).
3. Add testers: a Google account email, or a group created in **Verificadores y grupos** (here `Equipo AniTec`). The **Distribuir** button stays disabled while the recipients have no members.
4. Write the release notes and press **Distribuir**. Every tester receives an email with the install link.

To let people join without typing their emails, open **Vínculos de invitación → Crear vínculo de invitación**, choose the group and leave the domain restriction empty. Anyone with a Google account who opens the link joins the group; add that group as a recipient of the release so that they receive the build. The link can be disabled at any time.

Public invite link of this app: <https://appdistribution.firebase.dev/i/417dc4e6d8ae3e32>

### 6. Install it on a device

The tester opens the email (or the invite link) with the invited Google account, accepts the invitation, and downloads the build; Firebase may suggest its **App Tester** app. Android asks once to allow installing from that source. If an APK signed with another key is already installed (for example a debug build), uninstall it first.

The hosted database holds demo data (`ganadero`, `maria`, `jose`, `veterinaria`; password `anitec123`).

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
- The API filters data by the signed-in user; the app still filters on the client as well and caches only the signed-in user's records.

## Conventions

- Code, identifiers and commit messages in English.
- GitFlow (`main`, `develop`, `feature/*`, `release/*`, `hotfix/*`), Conventional Commits, Semantic Versioning.
- English is the default UI language; Latin American Spanish (`es-419`) is selectable in the app. All user-facing text lives in `strings.xml`. 
- Accessibility: every icon-only control has a content description; touch targets are at least 48 dp.
