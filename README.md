# Dexter

A personal, voice-first Android AI companion. There is no chat UI: the assistant talks through voice and an expressive animated face.

The current focus is the **Face Engine**, a self-contained set of modules that turn intent (activity, emotion, reactions) into an animated face.

## Status

| Phase | What | State |
| --- | --- | --- |
| 0 | Project skeleton: modules, version catalog, blank `FaceView` | Done |
| 1 | Static rig, bold renderer, three silhouettes, 14 expressions, playground | Done: Orb silhouette, mint accent |
| 2 | Frame loop, springs, frame-rate independent motion | Done |
| 3 | Life: blink, gaze, breathing, idle | Next |
| 4–8 | API, emotion, speech, integration, polish | Planned |

The app opens to the Orb face (mint) on a black, immersive screen. Every change animates through springs; the face doesn't move on its own yet (that is Phase 3).

## Requirements

- JDK 17+ (the Gradle daemon uses 25 via the toolchain)
- Android SDK with platform 37
- A device or emulator running Android 12 (API 31) or newer

## Modules

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android app | Hosts the face full-screen |
| `:face:api` | Kotlin/JVM | Public interfaces and value types |
| `:face:core` | Kotlin/JVM | Rig (`Channel`, `FacePose`, `Constraints`) and expressions (JSON) |
| `:face:render-compose` | Android library | `FaceRenderer`, `FaceStyle` silhouettes, `FaceView` / `FaceCanvas` |
| `:face:speech-android` | Android library | Audio tap for lip-sync (empty for now) |
| `:face:playground` | Android library, debug only | "Face Playground" launcher: sliders, presets, JSON export |

Dependencies flow `api ← core ← render-compose ← app`. `speech-android` depends only on `api`. `:face:api` and `:face:core` are plain JVM modules: an `android.*` import there fails to compile, and a guard test also rejects `androidx.*`.

## The rig

The face is one flat `FloatArray` of 49 normalized channels (`face.core.rig.Channel`), grouped into eye shape, lids, eye placement, pupils, brows, mouth, head, accents and look. Vertical channels are positive-up, L/R mean screen-left and screen-right, and tilt is positive clockwise. `Constraints` clamps every channel, keeps the gaze inside the unit disc, and keeps the lower lid below the upper one.

Expressions are sparse deltas, stored as JSON (format v1) in `face/core/src/main/resources/face/expressions/`:

```json
{ "format": 1, "id": "happy", "deltas": { "mouthCurve": 0.65, "lowerLidRaiseL": 0.45, "blush": 0.55 } }
```

`neutral.json` is relative to the rig's rest values; every other expression is layered on top of neutral.

Bundled expressions: neutral, happy, joy, laugh, curious, confused, sad, concerned, playful, surprised, sleeping, determined, smug and skeptical.

What the renderer can do:
- **Eyes** morph from their silhouette into arcs (`eyeSmile`, joy) or chevrons (`eyeSquint`, laughing), and get cut by tilted and curved lids.
- **Brows** are on by default. A low brow presses the upper lid down, so angry and skeptical eyes come for free.
- **Mouth** is a filled shape by default: it opens into a "D" with an inner mouth and tongue, and turns into an "o" or a grimace. The thin stroke style is still available.
- **Head** can roam the whole screen (`headX`/`headY` ±1 reach the edges), scale 0.5×–1.8×, tilt ±45° and squash or stretch.
- **Backdrop** is either pure dark (OLED) or a soft accent glow that follows the face.

## Animation

`FaceEngine` (core) holds a **target** pose and the **displayed** pose. Each frame, `step(dt)` pulls every channel towards its target through a second-order spring (frequency f, damping ζ, response r) with one profile per channel group: pupils fast, lids and mouth medium, head slow with a little anticipation. Defaults live in `DynamicsConfig.Default`.

- Interruptions are smooth because springs keep their velocity.
- Motion is the same at 30, 60 and 120 Hz: each frame is integrated in fixed sub-steps of at most 1/240 s, and `dt` is clamped to 50 ms.
- `AnimatedFace` runs one `withFrameNanos` loop only while the screen is at least `STARTED`. The pose is read only in the draw phase, so nothing recomposes per frame.

## Face Playground (debug builds)

A debug install adds a second launcher icon, **Face Playground**.

- Switch silhouettes (Orb, Soft Block, Capsule), accent colour, brows, filled/stroke mouth and glow backdrop at the top. **Center** brings a roaming face back.
- Tap an expression chip to animate to it, then drag any slider (the face follows through the springs). Tap a channel's label to reset it.
- **Spam random targets** throws a random expression (often at a random place, size and tilt) every 0.12–0.45 s: the stress test for pops.
- **Dynamics** has live f / ζ / r sliders per channel group, plus Reset.
- The top-right overlay shows fps and the worst frame time of the last half second.
- Tap the face for a full-screen view. In full screen, tap to step through the expressions; long-press to go back.
- **Save** writes the pose as delta JSON and copies it to the clipboard. To copy saved poses into the repo:
  ```bash
  adb pull /sdcard/Android/data/com.vlmis.dexter/files/poses/ ./poses
  ```

## Commands

```bash
./gradlew :face:core:test     # fast JVM tests
./gradlew test                # all unit tests
./gradlew lint                # Android lint
./gradlew :app:installDebug   # install on a connected device
./gradlew :face:render-compose:verifyPaparazziDebug   # screenshot tests
./gradlew :face:render-compose:recordPaparazziDebug   # re-record goldens after a visual change
```

## Build settings

- minSdk 31, compile/target SDK 37, Java/Kotlin target 17
- Versions live in `gradle/libs.versions.toml`
- Tests: JUnit 5 in the JVM modules, JUnit 4 plus Paparazzi in the Android modules
- JSON: kotlinx.serialization (core only)
