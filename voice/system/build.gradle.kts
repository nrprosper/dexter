plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vlmis.dexter.voice.system"
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
    testImplementation(libs.junit)
}
