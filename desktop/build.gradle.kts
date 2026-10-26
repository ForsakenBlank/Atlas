import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.forsakenblank.atlas.desktop.MainKt"
        // lets main set the window class to "atlas", so hyprland rules and launchers can match it
        jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Atlas"
            packageVersion = providers.gradleProperty("atlas.versionName").get()
            description = "Notes, trackers, money and plans that sync with your phone"
            vendor = "ForsakenBlank"
            // the whole java runtime goes in, so nothing is missing on a machine without java
            includeAllModules = true

            linux {
                iconFile.set(project.file("icons/atlas.png"))
                packageName = "atlas"
                appCategory = "Office"
            }
            windows {
                iconFile.set(project.file("icons/atlas.ico"))
                menuGroup = "Atlas"
                shortcut = true
                dirChooser = true
                upgradeUuid = "8d7a3c1e-5f1b-4a2e-9a63-2b4c7e9f1d05"
            }
        }
    }
}
