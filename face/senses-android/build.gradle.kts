plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.vlmis.dexter.face.senses"
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
    implementation(project(":face:api"))
}
