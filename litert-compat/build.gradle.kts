plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Keep Google's pinned native backend; compile its Kotlin client with the small
// explicit-assistant role addition documented in README/UPSTREAM.json.
val nativeAar by configurations.creating
dependencies {
    nativeAar(libs.litertlm.android) { isTransitive = false }
    api(libs.gson)
    api(libs.kotlinx.coroutines.android)
    implementation(kotlin("reflect"))
    testImplementation(libs.junit)
}
val copyNativeBackend by tasks.registering(Sync::class) {
    from({ nativeAar.map { zipTree(it) } }) { include("jni/**") }
    into(layout.buildDirectory.dir("generated/native-backend"))
}
android {
    namespace = "com.localai.litertcompat"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/native-backend/jni"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}
tasks.named("preBuild").configure { dependsOn(copyNativeBackend) }
