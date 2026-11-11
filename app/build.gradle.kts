import java.io.InputStream
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.bergz.zx700"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bergz.zx700"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0-test"

        ndk {
            // Pixel 9 is arm64; x86_64 lets it run in an emulator on Intel/AMD PCs.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"  // required by Oboe
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Test build: signed with the debug key so it installs straight away.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("com.google.oboe:oboe:1.9.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// ---------------------------------------------------------------------------
// Panel fonts (SIL Open Font Licence), fetched from the Google Fonts repository
// on first build so they don't need to live in git:
//   VT323 - the amber display; Barlow Condensed - the screen-printed labels.
// Their licences are copied into the app's assets/licenses folder.
val bundledFonts = mapOf(
    "src/main/res/font/vt323_regular.ttf" to "ofl/vt323/VT323-Regular.ttf",
    "src/main/res/font/barlow_condensed_semibold.ttf" to "ofl/barlowcondensed/BarlowCondensed-SemiBold.ttf",
    "src/main/res/font/barlow_condensed_bold.ttf" to "ofl/barlowcondensed/BarlowCondensed-Bold.ttf",
    "src/main/res/font/barlow_condensed_extrabold_italic.ttf" to "ofl/barlowcondensed/BarlowCondensed-ExtraBoldItalic.ttf",
    "src/main/assets/licenses/VT323-OFL.txt" to "ofl/vt323/OFL.txt",
    "src/main/assets/licenses/BarlowCondensed-OFL.txt" to "ofl/barlowcondensed/OFL.txt",
)
val fetchFonts = tasks.register("fetchFonts") {
    description = "Downloads the panel fonts (OFL) if they are missing."
    val base = layout.projectDirectory.asFile
    doLast {
        bundledFonts.forEach { (target, path) ->
            val file = File(base, target)
            if (!file.exists() || file.length() == 0L) {
                file.parentFile.mkdirs()
                URI("https://raw.githubusercontent.com/google/fonts/main/$path").toURL()
                    .openStream().use { input: InputStream -> file.outputStream().use { out -> input.copyTo(out) } }
                println("Fetched $target")
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchFonts) }
