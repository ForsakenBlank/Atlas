import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// the version lives in gradle.properties, this hands it to the kotlin code
val versionName = providers.gradleProperty("atlas.versionName").get()
val generateVersion by tasks.registering {
    val out = layout.buildDirectory.dir("generated/version")
    inputs.property("version", versionName)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/forsakenblank/atlas/AtlasVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.forsakenblank.atlas\n\nconst val ATLAS_VERSION = \"$versionName\"\n")
    }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // both targets run on the jvm, so the shared code is compiled once for each of them
    // and keeps using java.time and the rest of the jdk instead of being rewritten
    val sharedDir = "src/shared/kotlin"

    sourceSets {
        commonMain.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            api(libs.lifecycle.runtime.compose)
            api(libs.lifecycle.viewmodel.compose)
            api(libs.navigation.compose)
            api(libs.androidx.room.runtime)
            api(libs.androidx.datastore.preferences.core)
            api(libs.kotlinx.serialization.json)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain {
            kotlin.srcDir(sharedDir)
            kotlin.srcDir(generateVersion)
            dependencies {
                api(libs.androidx.room.ktx)
                api(libs.androidx.datastore.preferences)
                api(libs.androidx.activity.compose)
            }
        }
        val desktopMain by getting {
            kotlin.srcDir(sharedDir)
            kotlin.srcDir(generateVersion)
            dependencies {
                api(compose.desktop.common)
                api(libs.androidx.sqlite.bundled)
                api(libs.kotlinx.coroutines.swing)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "com.forsakenblank.atlas.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // room writes its database code separately for each target
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
