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

The archive the voice comes in also contains eSpeak NG data (GPL-3.0-or-later). The app never extracts or stores it: only `model.onnx`, `tokens.txt` and `MODEL_CARD` are unpacked.

## Build, test and CI only (not shipped)

| Component | License |
| --- | --- |
| [JUnit 4](https://junit.org/junit4/) and [JUnit 5](https://junit.org/junit5/) | EPL-1.0 / EPL-2.0 |
| [Paparazzi](https://github.com/cashapp/paparazzi) (screenshot tests) | Apache-2.0 |
| [gitleaks](https://github.com/gitleaks/gitleaks) 8.30.1 (secret scan in GitHub Actions) | MIT |
| [actions/checkout](https://github.com/actions/checkout) | MIT |
