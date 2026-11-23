plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vlmis.dexter.voice.speaker"
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
    api(project(":voice:api"))
    api(project(":face:api"))
    api(project(":face:speech-android"))
    api(project(":voice:piper"))
    api(project(":voice:kokoro"))
    api(project(":voice:system"))
    testImplementation(libs.junit)
}
