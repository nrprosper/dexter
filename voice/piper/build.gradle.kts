import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vlmis.dexter.voice.piper"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    explicitApi()
}

val sherpaAar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    api(project(":voice:api"))
    implementation("${libs.sherpa.onnx.get()}@aar")
    sherpaAar("${libs.sherpa.onnx.get()}@aar")
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

// The AAR comes straight from GitHub releases, so pin its checksum (sherpa-onnx 2.0.3, Apache-2.0).
val verifySherpaOnnx by tasks.registering {
    val expected = "4c766e2e74c9d33d595338c21fe3b305c73ba45d47e96a235675c33142a2ec50"
    inputs.files(sherpaAar.incoming.files)
    doLast {
        val aar = inputs.files.singleFile
        val digest = MessageDigest.getInstance("SHA-256")
        aar.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == expected) { "sherpa-onnx AAR checksum mismatch: $actual (expected $expected)" }
    }
}
tasks.named("preBuild") { dependsOn(verifySherpaOnnx) }
