plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vlmis.dexter.voice.kokoro"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    explicitApi()
}

dependencies {
    // Reuses Piper's sherpa-onnx runtime, verified downloader and voice store.
    api(project(":voice:piper"))
    implementation("${libs.sherpa.onnx.get()}@aar")
    testImplementation(libs.junit)
}
