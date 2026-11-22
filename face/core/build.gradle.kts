plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    api(project(":face:api"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
