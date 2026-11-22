# Dexter

A personal, voice-first Android AI companion. There is no chat UI: the assistant talks through voice and an expressive animated face.

The current focus is the **Face Engine**, a self-contained set of modules that turn intent (activity, emotion, reactions) into an animated face.

## Status

| Phase | What | State |
| --- | --- | --- |
| 0 | Project skeleton: modules, version catalog, blank `FaceView` | Done |
| 1 | Static rig, bold renderer, three silhouettes, 14 expressions, playground | Done: Orb silhouette, blue accent |
| 2 | Frame loop, springs, frame-rate independent motion | Done |
| 3 | Life: blink, gaze, breathing, idle, boredom and sleep | Done (10-minute desk test is yours) |
| 4 | Public API (`FaceController`), activities, inbox | Done |
| 5 | Director, mood, reactions, personality, quirks | Done |
| 5.5 | Phone senses: battery, charger, volume, headphones, shake, face-down, touch | Done |
| 5.6 | More senses: tilt, heat, proximity, light, time of day, welcome back, saver, screenshot | Done |
| 6 | Speech v1 (amplitude lip-sync) | Next |
| 7–8 | Integration, polish | Planned |

The app opens to the Orb face (blue) on a black, immersive screen. Left alone it blinks, glances around, breathes and does small idle things; after a while without interaction it gets drowsy and falls asleep. Tap it to wake it.

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

## Life (Phase 3)

Each frame the engine builds the target through a fixed layer stack (neutral and emotion, activity, gaze, procedural life, speech, reactions), the springs follow it, and the **blink** is applied last to the displayed pose. Blinks are an authored curve, which springs would smear away. All timing comes from the engine's seeded `Rng` and `LifeConfig`:

- **Blinks:** log-normal intervals (about every 4 s), fast close and slower open; occasional double and slow blinks, partial blinks when drowsy; extra blinks on big gaze jumps and expression changes; none during surprise or sleep.
- **Gaze:** a target point, mostly on you, with glances away and back, tiny fixation drift, and the head following with lag.
- **Idle behaviours:** tilt, hum, squint, look-around, bounce, posture shift and yawn (drowsy only), picked from a weighted shuffle bag with cooldowns and an **attention budget** (at most 4 noticeable per minute).
- **Boredom:** drowsy after 90 s without interaction (heavier lids, slower blinks), asleep after 180 s ("Zzz", slow deep breathing). `interact()` (a tap) wakes it with a double blink, a stretch and a look at you.
- **Breathing:** a barely visible bob and scale.

## Driving the face (Phase 4)

An orchestrator only needs `:face:api`. `FaceEngine` implements `FaceController`; the app creates it (`rememberFaceEngine()`), shows it with `FaceView(engine = engine)`, and passes the same object to the orchestrator as a `FaceController`.

```kotlin
face.setActivity(Activity.Listening)                 // Sleeping | Idle | Listening | Thinking | Speaking
face.setUserVoiceLevel(level)                         // while listening: drives nods
face.setEmotion(EmotionSignal(Emotion.Happy, 0.7f))
face.react(FaceReaction.Realization)                  // queued, in order
face.attachSpeech(track, SpeechStyle.Excited)         // the mouth samples the track each frame
face.setActivity(Activity.Speaking)
face.snapshot                                         // StateFlow<FaceSnapshot>
```

- **Thread-safe:** every call lands in an inbox that the frame loop drains first. State setters are latest-wins and **idempotent** (re-sending the same state changes nothing); reactions keep their order.
- **Activities:**
  - *Listening* leans in with bigger pupils, locked gaze, fewer blinks, and nods on your voice peaks.
  - *Thinking* looks up and aside, then scans, then shows effort, then glances back apologetically if it takes long.
  - *Speaking* keeps eyes on you with brief glances away, and the mouth follows the attached `SpeechTrack`.
  - *Sleeping* sleeps until another activity is set. *Idle* keeps the boredom-to-sleep behaviour.
- **Not yet:** mouth shaping for real audio is Phase 6.

## Character (Phase 5)

The LLM chooses the feeling, the face chooses the performance.

- **Director:** every `EmotionSignal` adds evidence for its emotion (scaled by confidence), and evidence fades over a few seconds. The face only switches when another emotion clearly out-evidences the current one (30% plus the hysteresis margin) and the minimum dwell has passed. A stray label from a noisy LLM loses; a repeated or confident one wins. Changes blend with inertia, big swings (sad → happy) get a transition beat (a surprise or a neutral pause), and expressions decay back to the **mood** with a half-life.
- **Mood:** a slow temperament from the personality (Dexter rests neutral), nudged by what happens and drifting back.
- **Arousal:** energy from the emotion and speech style; it speeds up springs and idle behaviour ("excited" is just happy with high arousal).
- **Reactions:** Surprise, Startle, Delight, Acknowledge, Oops, Laugh and Realization play as attack/hold/release envelopes. A higher priority interrupts a lower one, each has a cooldown (five surprises in a row give one pop), and several hand over to a follow-up emotion.
- **Quirks:** a double blink when unsure, a happy head wobble, and a one-eye peek when woken.
- **Personality:** JSON (format 1) in `face/core/src/main/resources/face/personality/`: **Dexter** (default: bouncy, curious, expressive) and **Sage** (calm, composed, slow). Apply one at runtime with `setPersonality`.

## Phone senses (Phase 5.5)

`:face:senses-android` (`PhoneSenses`, depends only on `:face:api`) turns phone events into face intent while the face is on screen, and always reacts playfully:
- **Low battery** (≤15%): the face turns red and worried and gets sleepy sooner; charging brings it back.
- **Charger in/out, battery full, headphones**: a zap, an "oh?", a contented stretch, a little head bob.
- **Shake**: dizzy circling eyes with swirls orbiting above the head. **Face-down**: sleeps; picking it up wakes it with a peek.
- **Volume keys**: the face scoots up-right, shrinks and watches the system slider level, then bounces back.
- **Tilt**: the face lives inside the glass, sliding and looking a little downhill as you tilt the phone (relative to how you usually hold it).
- **Heat**: warm gives a flush; hot, sweating, panting and a squint; critical, a melting droop.
- **Hand over the screen**: eyes squeeze shut, then one peeks. **In a pocket**: curls up and sleeps.
- **Room light**: dark makes it dimmer and sleepier; a sudden bright light makes it squint.
- **Time of day**: a big good-morning stretch once a day, a "should you be sleeping?" look after midnight, sleepier at night.
- **Welcome back** when you return to it (bigger after a long absence). **Battery saver**: heavy-lidded and slower.
- **Screenshot** (Android 14+): it winks and poses for the photo.
- **Touch** (hit-tested on the drawn face): poke an eye, tap a cheek (giggle), the mouth (boop) or forehead (bonk), long-press to pet, drag and the eyes follow, rapid taps escalate to a pout and a grumpy turn-away.

## Face Playground (debug builds)

A debug install adds a second launcher icon, **Face Playground**.

- Switch silhouettes (Orb, Soft Block, Capsule), accent colour, brows, filled/stroke mouth and glow backdrop at the top. **Center** brings a roaming face back.
- Tap an expression chip to animate to it, then drag any slider (the face follows through the springs). Tap a channel's label to reset it.
- **Spam random targets** throws a random expression (often at a random place, size and tilt) every 0.12–0.45 s: the stress test for pops.
- **Dynamics** has live f / ζ / r sliders per channel group, plus Reset.
- The top-right overlay shows fps and the worst frame time of the last half second; bottom-left shows awake / drowsy / asleep.
- **Orchestrator** drives the face only through `FaceController`: activity, emotion and intensity, reactions, a "User talking" voice simulator, and **Run scenario** (listen 3 s → think 4 s → speak 5 s → idle, run from a background thread).
- **Personality** switches Dexter and Sage. The active preset is an editable file that hot-reloads within a second:
  ```bash
  adb pull /sdcard/Android/data/com.vlmis.dexter/files/personality/dexter.json
  # edit it, then
  adb push dexter.json /sdcard/Android/data/com.vlmis.dexter/files/personality/dexter.json
  ```
- **Noisy LLM** (in the orchestrator panel) sends a random emotion every 0.8–3 s, mostly "happy", to show the Director keeping the face calm. Reaction buttons now animate.
- **Emotion mixer** sets emotion amounts and arousal directly (**Use mix**); **Director** hands control back.
- **Life** turns the behaviours on or off, **×10** speeds up time, **Doze** sends it to sleep now, and the **Behaviour log** lists blinks, behaviours and state changes as they happen.
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
