# Third-party notices

Dexter uses the open-source libraries and data listed here. Each keeps its own license; the license names link to the full text.

## Libraries in the app

| Component | Version | License | Used for |
| --- | --- | --- | --- |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Android AAR) | 2.0.3 | [Apache-2.0](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) | Running Piper voices on the phone (`:voice:piper`) |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime) (bundled inside the sherpa-onnx AAR as `libonnxruntime.so`) | as shipped in sherpa-onnx 2.0.3 | [MIT](https://github.com/microsoft/onnxruntime/blob/main/LICENSE), plus its [third-party notices](https://github.com/microsoft/onnxruntime/blob/main/ThirdPartyNotices.txt) | Neural network inference |
| [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | 1.28.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Unpacking `.tar.bz2` voice archives |
| [Apache Commons IO](https://commons.apache.org/proper/commons-io/) (via Commons Compress) | 2.20.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Dependency of Commons Compress |
| [Apache Commons Codec](https://commons.apache.org/proper/commons-codec/) (via Commons Compress) | 1.19.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Dependency of Commons Compress |
| [Apache Commons Lang](https://commons.apache.org/proper/commons-lang/) (via Commons Compress) | 3.18.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Dependency of Commons Compress |
| [Anthropic Java SDK](https://github.com/anthropics/anthropic-sdk-java) (`anthropic-java`, core and OkHttp client) | 3.6.0 | [Apache-2.0](https://github.com/anthropics/anthropic-sdk-java/blob/main/LICENSE) | Talking to Claude (`:assistant:claude`) |
| [OkHttp](https://github.com/square/okhttp) and [Okio](https://github.com/square/okio) (via the Anthropic SDK) | 4.12.0 / 3.6.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | HTTP client |
| [Jackson](https://github.com/FasterXML/jackson) core, databind, annotations, jdk8, jsr310, Kotlin module, and [classmate](https://github.com/FasterXML/java-classmate) (via the Anthropic SDK) | 2.19.4 / 1.7.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | JSON |
| [victools jsonschema-generator](https://github.com/victools/jsonschema-generator) and modules (via the Anthropic SDK) | 4.38.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Schema support in the SDK |
| [Swagger annotations](https://github.com/swagger-api/swagger-core) (via the Anthropic SDK) | 2.2.50 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Annotations used by the SDK |
| [OpenTelemetry API](https://github.com/open-telemetry/opentelemetry-java) (via the Anthropic SDK) | 1.62.0 | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | Optional tracing hooks in the SDK (unused) |
| [Standard Webhooks](https://github.com/standard-webhooks/standard-webhooks) (via the Anthropic SDK) | 1.2.0 | [MIT](https://github.com/standard-webhooks/standard-webhooks/blob/main/LICENSE) | Bundled by the SDK (unused) |
| [SLF4J API](https://www.slf4j.org/) (via the Anthropic SDK) | 2.0.16 | [MIT](https://www.slf4j.org/license.html) | Logging facade (no logger bound) |
| [Kotlin reflect](https://github.com/JetBrains/kotlin) (via Jackson's Kotlin module) | 1.9.25 | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) | Reflection for Jackson |
| [Kotlin standard library](https://github.com/JetBrains/kotlin) | 2.2.10 | [Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt) | Language runtime |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.9.0 | [Apache-2.0](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) | Concurrency, flows |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | 1.9.0 | [Apache-2.0](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt) | Expression, personality, mouth and voice-manifest JSON |
| [AndroidX and Jetpack Compose](https://developer.android.com/jetpack/androidx) (core, activity, lifecycle, Compose UI, foundation, Material 3) | see `gradle/libs.versions.toml` | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | UI and Android integration |

sherpa-onnx 2.x contains no eSpeak NG or piper-phonemize code, so no GPL code is linked into the app. Dexter does not use the GPL-3.0 [OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl) code.

## Models and data downloaded at runtime

None of these files are in this repository or in the APK. The app downloads them on first use from their official source, checks the sha256 listed in [`voice/piper/src/main/resources/voice/piper/voices.json`](voice/piper/src/main/resources/voice/piper/voices.json), and stores them in app-private storage.

| File | Source | License |
| --- | --- | --- |
| Piper voice `en_US-ljspeech-medium` (int8, packaged for sherpa-onnx) | [sherpa-onnx `tts-models` release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models), converted from [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/ljspeech/medium) | Training data: [LJ Speech](https://keithito.com/LJ-Speech-Dataset/), public domain ([model card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/ljspeech/medium/MODEL_CARD)). Voice repository: MIT. Voice trained by Bryce Beattie; dataset by Keith Ito and Linda Johnson. |
| English pronunciation lexicon `lexicon-en-us.txt` | [sherpa-onnx `tts-models` release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) | **Not stated upstream.** The file header says it was built from CMUdict (BSD-style license) and `en_list`, which is most likely eSpeak NG's dictionary source (GPL-3.0-or-later). It is downloaded to the device, never redistributed by this project. Review it before shipping a commercial build. |

| Kokoro v1.0 int8 (optional, downloaded only when you tap Download) | [sherpa-onnx `tts-models` release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) (`kokoro-int8-multi-lang-v1_0`), converted from [hexgrad/Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) | Model and voices: [Apache-2.0](https://huggingface.co/hexgrad/Kokoro-82M). English lexicons generated from [misaki](https://github.com/hexgrad/misaki) (Apache-2.0). |

The archives the voices come in also contain eSpeak NG data (GPL-3.0-or-later). The app never extracts or stores it: only the files listed in each manifest (model, tokens, voices, English lexicons, model card or license) are unpacked.

## Build, test and CI only (not shipped)

| Component | License |
| --- | --- |
| [JUnit 4](https://junit.org/junit4/) and [JUnit 5](https://junit.org/junit5/) | EPL-1.0 / EPL-2.0 |
| [kotlinx-coroutines-test](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 |
| [Paparazzi](https://github.com/cashapp/paparazzi) (screenshot tests) | Apache-2.0 |
| [gitleaks](https://github.com/gitleaks/gitleaks) 8.30.1 (secret scan in GitHub Actions) | MIT |
| [actions/checkout](https://github.com/actions/checkout) | MIT |
