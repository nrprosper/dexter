plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vlmis.dexter.assistant"
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
    api(project(":assistant:core"))
    api(project(":assistant:claude"))
    api(project(":assistant:gemini"))
    api(project(":voice:speaker"))
    testImplementation(libs.junit)
}
