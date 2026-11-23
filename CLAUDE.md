# CLAUDE.md

## Project

A personal, voice-first Android AI companion. There is no chat UI: the assistant talks through voice and an expressive animated face. The face should feel like a living character, not a generic avatar.

Current focus: the **Face Engine**, an independent component of the app.

## Read before working on the face

- `docs/face-engine-architecture.md` — the design reference (rendering choice, state/emotion model, parameter model, animation system, API, modules, pitfalls).
- `docs/face-engine-implementation-plan.md` — the phased plan. Work on **one phase at a time**, and only the phase you are asked for.

If a task conflicts with the architecture doc, stop and ask. Don't quietly diverge. If you think the doc is wrong, say why and propose the change before implementing it.

## Tech stack

- Kotlin, Jetpack Compose, Coroutines/Flow
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`)
- Rendering: Compose `Canvas` / `DrawScope`, driven by a single `withFrameNanos` loop
- Tests: JUnit for pure-Kotlin modules; screenshot tests (Paparazzi or Roborazzi) as a test-only dependency
- minSdk: **31** (decided in Phase 0). RenderEffect is available natively; AGSL (33+) only behind a runtime check. Every effect must have a plain-Canvas fallback.

## Module layout

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android app | Hosts the face; later, the orchestrator |
| `:face:api` | Pure Kotlin/JVM | Public interfaces and value types (`FaceController`, `Activity`, `EmotionSignal`, `FaceReaction`, `SpeechTrack`, `SpeechStyle`, `Personality`, `PowerMode`) |
| `:face:core` | Pure Kotlin/JVM | The simulation: rig, expressions, director, mood, layers, springs, behaviors, RNG, clock |
| `:face:render-compose` | Android library | `FaceRenderer`, the `FaceView` composable, frame loop, lifecycle, power handling |
| `:face:speech-android` | Android library | `AudioTrack` PCM tap, amplitude envelope, `AmplitudeSpeechTrack` |
| `:face:senses-android` | Android library | Phone events (battery, volume, tilt, touch...) into `FaceController` |
| `:face:playground` | Debug-only | Channel sliders, emotion mixer, scripted scenarios, FPS overlay, voice panel |
| `:voice:api` | Pure Kotlin/JVM | `TtsEngine`, `PcmChunk` (+ optional `Alignment`), capabilities, voices, settings, `TtsRouter`, `SentenceSplitter`, `TextNormalizer` |
| `:voice:piper` | Android library | Piper voices through sherpa-onnx (lexicon frontend), voice manifest, verified resumable downloads |
| `:voice:system` | Android library | Android `TextToSpeech` (default voice) rendered to WAV, played through our player |
| `:voice:kokoro` | Android library | Optional Kokoro-82M v1.0 int8 via sherpa-onnx (misaki lexicons, 28 English speakers), downloaded on request |
| `:voice:speaker` | Android library | `Speaker` (router → engine → `PcmPlayer` → face) and `VoiceStack`, the composition root |
| `:assistant:core` | Pure Kotlin/JVM | `Orchestrator` (listen → think → speak), `Ears`/`Brain`/`Mouth`, the reply contract and streaming `ReplyParser`, `Feeling` → `EmotionSignal`/`SpeechStyle` |
| `:assistant:claude` | Pure Kotlin/JVM | `ClaudeBrain`: Claude Messages API (Anthropic Java SDK), streaming structured replies |
| `:assistant:gemini` | Pure Kotlin/JVM | `GeminiBrain`: Gemini API `streamGenerateContent` over REST (OkHttp + Jackson, already shipped), same reply contract |
| `:assistant:android` | Android library | `RecognizerEars` (SpeechRecognizer), `SpeakerMouth`, `ApiKeyStore` (Keystore-encrypted key), `Assistant` wiring |

Dependency direction: `api` ← `core` ← `render-compose` ← `app`. `speech-android` depends only on `face:api`.
`voice:api` depends on nothing Android; engines depend on `voice:api`; only `voice:speaker` joins voice and face.
A new TTS engine (Kokoro, ElevenLabs) implements `TtsEngine` and is added in `VoiceStack`; the face, player and `Speaker` don't change.
The assistant reaches the face only through `FaceController` (`:face:api`); the brain is behind the `Brain` interface.

## Hard rules

1. **`:face:api` and `:face:core` must never import `android.*` or `androidx.*`.** They are plain JVM modules. If something needs Android, it belongs in `render-compose` or `speech-android`.
2. **The simulation is deterministic.** Every random choice uses an injected, seeded `Rng`. Time comes from an injected clock and `dt`. No `System.currentTimeMillis()`, `Random.Default` or `Math.random()` inside `:face:core`.
3. **Frame-rate independence.** Everything advances by `dt` in seconds, clamped (max 50 ms). Never assume 60 FPS.
4. **Springs, not tweens, for interruptible motion.** Channels follow targets through `SecondOrderDynamics` (f, ζ, r). Fixed-duration curves are only for authored one-shot envelopes and blink shapes.
5. **The pose is a flat `FloatArray` indexed by the `Channel` enum.** Expressions are sparse deltas from neutral. Pose describes how the face looks, never how it moves.
6. **No allocations per frame** in the frame loop, the simulation step or the renderer. Preallocate buffers, reuse `Path` objects, no `data class` copies or capturing lambdas in hot paths.
7. **Draw phase only.** Animated state is read only inside the `Canvas`/`drawBehind` lambda. Nothing should recompose per frame.
8. **No per-frame `BlurMaskFilter`.** Do glow with gradients or a cached layer.
9. **The AI never controls geometry.** Outside modules send intent (activity, emotion, intensity, tone, reactions, stage directions such as `StageMove.Left` or `Hop`). Only the Director and layers turn intent into channel values; a stage direction names a move, the engine decides the motion.
10. **The API is idempotent for state, queued for events.** `setActivity` and `setEmotion` mean "latest wins". `react(...)` is queued and resolved by priority and cooldown. Calls go into a thread-safe inbox, drained at the start of each frame.
11. **No new dependencies** without asking first, explaining what they add.

## Public repo rules

This repository is public.

- The Claude API key is entered on the phone (Settings, from the launcher icon's long-press menu) and stored encrypted with an Android Keystore key, excluded from backups. Never put it in code, `BuildConfig`, `local.properties`, Gradle files or logs.
- Never commit secrets, keystores, `google-services.json`, `local.properties`, `.env` files, model files (`*.onnx`, voice archives), recordings or any personal data. `.gitignore` covers these; keep it that way.
- Models are downloaded at runtime from their official source, listed in `voice/piper/src/main/resources/voice/piper/voices.json` with url, sha256, size and license.
- The `Secret scan` GitHub Action runs gitleaks on every push and PR; a finding fails the build.
- Every new library, model or data file gets a line in `THIRD_PARTY_NOTICES.md` with its license. No GPL code in the app.

## Code style

- Kotlin official style. Prefer small, focused files that match the package layout in the architecture doc.
- Mark public API with explicit visibility. Keep everything in `:face:core` `internal` unless something else needs it.
- Tunable numbers (spring params, blink timing, expression deltas, personality) live in data or JSON, not as magic numbers scattered through logic.
- KDoc only on the public API and on non-obvious algorithms. No comments that just restate the code.

## Testing

- Every piece of logic in `:face:core` gets JVM unit tests with a fixed `dt` and seed.
- Tests that must keep passing once they exist:
  - All channels stay within their declared ranges.
  - **No pop:** across random command sequences, the change per frame on every channel stays below its threshold.
  - The same seed gives an identical pose sequence.
  - Motion matches within tolerance at 30, 60 and 120 Hz.
  - Blink statistics over a simulated hour match the personality settings.
- The renderer gets screenshot tests for key expressions once Phase 1 is done.

## Commands

```bash
./gradlew :face:core:test          # fast JVM tests: run after every core change
./gradlew :voice:api:test          # TTS API, router, sentence splitter
./gradlew test                     # all unit tests
./gradlew :app:installDebug        # install on device
./gradlew lint                     # Android lint
```

Run `:face:core:test` before saying a task is done.

## How to work in this repo

- Before writing code for a phase, summarize your plan in a few bullets and list the files you will create or change.
- Stop at the end of the phase. Report what was built, how to try it on the device, and anything left uncertain.
- Don't start the next phase, and don't build speech, LLM integration or camera features ahead of schedule.
- When tuning values are a matter of taste, pick reasonable defaults, expose them in the playground, and tell me which ones to judge by eye.
- The visual reference is the feel of *The Mitchells vs. the Machines* (timing, squash and stretch, comic beats). The character's look must be original. Don't recreate PAL or any of the film's robots.

## Open decisions (update as they're made)

- [x] minSdk: 31
- [x] Face silhouette: **Orb** (round superellipse eyes with pupils, brows on, filled mouth)
- [x] Accent colour and background: **blue `#4CC9F0`** on near-black `#050507` with the glow backdrop (was mint until Phase 4)
- [x] Signature quirks: **double-blink when unsure**, **happy wobble**, **one-eye peek on wake** (Phase 5)
- [x] JSON format for expressions and personality: expressions `{format: 1, id, deltas}` (Phase 1); personality format 1 with grouped sections (mood, director, motion, blink, gaze, idle, sleepiness, quirks), see `face/core/src/main/resources/face/personality/` (Phase 5)
- [x] TTS source (Phase 6): **offline first**. Default: Piper voices through sherpa-onnx 2.x (Apache-2.0) with the lexicon text frontend, no eSpeak NG. Fallback: Android `TextToSpeech`, only when no Piper voice is installed. Later: Kokoro-82M via sherpa-onnx (optional, higher quality), then ElevenLabs (cloud). Never the GPL `piper1-gpl` code.
- [x] Default voice: `en_US-ljspeech-medium` int8 (LJ Speech, public-domain data), downloaded at runtime
- [x] LLM (Phase 7): **Claude API** through the Anthropic Java SDK, default `claude-opus-5-5` at effort `low` (Sonnet 5.5 / Haiku 5.5 selectable), streaming structured replies `{emotion, intensity, confidence, tone, sentences[{text, emotion?, move?}]}`
- [x] Brains (Phase 7): Claude or Gemini, chosen in Settings; each key encrypted separately. Gemini default `gemini-4-hydrogen` (free tier: Google may use free-tier data, so the app warns about it)
- [x] Voice default (Phase 7): the phone's Google TTS (system engine); Kokoro (optional 132 MB download) and Piper as offline options
- [x] Speech input (Phase 7): Android `SpeechRecognizer` (on-device when available); **double-tap** the face to talk (wake word later)
