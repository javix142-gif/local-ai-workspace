plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

val arm64Only = providers.gradleProperty("arm64Only").orNull == "true"
val qaParallelInstall = providers.gradleProperty("qaParallelInstall").orNull == "true"
val qaKeystoreFile = providers.environmentVariable("QA_KEYSTORE_FILE").orNull
val qaKeystorePassword = providers.environmentVariable("QA_KEYSTORE_PASSWORD").orNull
val qaKeyAlias = providers.environmentVariable("QA_KEY_ALIAS").orNull
val qaKeyPassword = providers.environmentVariable("QA_KEY_PASSWORD").orNull
val qaKeystoreType = providers.environmentVariable("QA_KEYSTORE_TYPE").orNull
val qaSigningValues = listOf(qaKeystoreFile, qaKeystorePassword, qaKeyAlias, qaKeyPassword)
val qaSigningValueCount = qaSigningValues.count { !it.isNullOrBlank() }
if (qaSigningValueCount in 1..3) {
    throw GradleException("QA_PERSONAL_SIGNING_CONFIGURATION_INCOMPLETE")
}
val qaPersistentSigning = qaSigningValueCount == 4

android {
    namespace = "com.localai.workspace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localai.workspace"
        minSdk = 29
        targetSdk = 35
        versionCode = 27
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        if (arm64Only) {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    if (qaPersistentSigning) {
        signingConfigs.create("qaPersonal") {
            storeFile = file(qaKeystoreFile!!)
            storeType = qaKeystoreType
            storePassword = qaKeystorePassword
            keyAlias = qaKeyAlias
            keyPassword = qaKeyPassword
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = if (qaParallelInstall) ".parallel" else ".debug"
            versionNameSuffix = "-debug"
            if (qaPersistentSigning) {
                signingConfig = signingConfigs.getByName("qaPersonal")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    testOptions.unitTests.isIncludeAndroidResources = true

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(project(":llama-runtime"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)
    implementation(project(":litert-compat"))
    implementation(libs.gson)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

// Retain the existing host test launcher. The pinned Kotlin compatibility source
// now targets Java 17; Google's precompiled Java 21 descriptors are not packaged.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
