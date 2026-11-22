# Dexter

A personal, voice-first Android AI companion. There is no chat UI: the assistant talks through voice and an expressive animated face.

The current focus is the **Face Engine**, a self-contained set of modules that turn intent (activity, emotion, reactions) into an animated face.

## Status

| Phase | What | State |
| --- | --- | --- |
| 0 | Project skeleton: modules, version catalog, blank `FaceView` | Done |
| 1 | Design exploration and the static rig | Next |
| 2–8 | Springs, life, API, emotion, speech, integration, polish | Planned |

Right now the app opens to a black, immersive screen with a dim centre dot (the placeholder `FaceView`).

## Requirements

- JDK 17+ (the Gradle daemon uses 25 via the toolchain)
- Android SDK with platform 37
- A device or emulator running Android 12 (API 31) or newer

## Modules

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android app | Hosts the face full-screen |
| `:face:api` | Kotlin/JVM | Public interfaces and value types |
| `:face:core` | Kotlin/JVM | Deterministic face simulation (no Android) |
| `:face:render-compose` | Android library | `FaceView` composable and renderer |
| `:face:speech-android` | Android library | Audio tap for lip-sync (empty for now) |
| `:face:playground` | Android library, debug only | Tuning tools (empty for now) |

Dependencies flow `api ← core ← render-compose ← app`. `speech-android` depends only on `api`. `:face:api` and `:face:core` are plain JVM modules: an `android.*` import there fails to compile, and a guard test also rejects `androidx.*`.

## Commands

```bash
./gradlew :face:core:test     # fast JVM tests
./gradlew test                # all unit tests
./gradlew lint                # Android lint
./gradlew :app:installDebug   # install on a connected device
```

## Build settings

- minSdk 31, compile/target SDK 37, Java/Kotlin target 17
- Versions live in `gradle/libs.versions.toml`
- Tests: JUnit 5 in the JVM modules, JUnit 4 in `:app`
