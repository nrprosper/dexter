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
- minSdk: TODO (decide in Phase 0). RenderEffect needs 31+, AGSL needs 33+. Every effect must have a plain-Canvas fallback.

## Module layout

| Module | Type | Purpose |
| --- | --- | --- |
| `:app` | Android app | Hosts the face; later, the orchestrator |
| `:face:api` | Pure Kotlin/JVM | Public interfaces and value types (`FaceController`, `Activity`, `EmotionSignal`, `FaceReaction`, `SpeechTrack`, `SpeechStyle`, `Personality`, `PowerMode`) |
| `:face:core` | Pure Kotlin/JVM | The simulation: rig, expressions, director, mood, layers, springs, behaviors, RNG, clock |
| `:face:render-compose` | Android library | `FaceRenderer`, the `FaceView` composable, frame loop, lifecycle, power handling |
| `:face:speech-android` | Android library | `AudioTrack` PCM tap, amplitude envelope, `AmplitudeSpeechTrack` |
| `:face:playground` | Debug-only | Channel sliders, emotion mixer, scripted scenarios, FPS overlay |

Dependency direction: `api` ← `core` ← `render-compose` ← `app`. `speech-android` depends only on `api`.

## Hard rules

1. **`:face:api` and `:face:core` must never import `android.*` or `androidx.*`.** They are plain JVM modules. If something needs Android, it belongs in `render-compose` or `speech-android`.
2. **The simulation is deterministic.** Every random choice uses an injected, seeded `Rng`. Time comes from an injected clock and `dt`. No `System.currentTimeMillis()`, `Random.Default` or `Math.random()` inside `:face:core`.
3. **Frame-rate independence.** Everything advances by `dt` in seconds, clamped (max 50 ms). Never assume 60 FPS.
4. **Springs, not tweens, for interruptible motion.** Channels follow targets through `SecondOrderDynamics` (f, ζ, r). Fixed-duration curves are only for authored one-shot envelopes and blink shapes.
5. **The pose is a flat `FloatArray` indexed by the `Channel` enum.** Expressions are sparse deltas from neutral. Pose describes how the face looks, never how it moves.
6. **No allocations per frame** in the frame loop, the simulation step or the renderer. Preallocate buffers, reuse `Path` objects, no `data class` copies or capturing lambdas in hot paths.
7. **Draw phase only.** Animated state is read only inside the `Canvas`/`drawBehind` lambda. Nothing should recompose per frame.
8. **No per-frame `BlurMaskFilter`.** Do glow with gradients or a cached layer.
9. **The AI never controls geometry.** Outside modules send intent (activity, emotion, intensity, tone, reactions). Only the Director and layers turn intent into channel values.
10. **The API is idempotent for state, queued for events.** `setActivity` and `setEmotion` mean "latest wins". `react(...)` is queued and resolved by priority and cooldown. Calls go into a thread-safe inbox, drained at the start of each frame.
11. **No new dependencies** without asking first, explaining what they add.

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

- [ ] minSdk
- [ ] Face silhouette (eye shape, mouth style, brows or none)
- [ ] Accent colour and background
- [ ] Two or three signature quirks
- [ ] JSON format for expressions and personality
- [ ] TTS source (on-device or cloud), which affects the speech phase
