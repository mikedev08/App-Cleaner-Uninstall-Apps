import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Firebase project app-cleaner-jedy; app/google-services.json holds its client config.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

// The RevenueCat public SDK key lives in local.properties (never committed):
//   revenuecat.apiKey=goog_xxxxxxxx
// Without it the app still runs; billing is simply not configured (PRD §2).
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.jedy.appcleaner.uninstaller"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jedy.appcleaner.uninstaller"
        // StorageStatsManager (per-app sizes) starts at API 26 (PRD §1).
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Google's sample AdMob app ID, which only ever serves test ads. It and the unit IDs in
        // ads/AdConfig.kt are replaced with App Cleaner's real ones before publishing (AdMob PRD §2.2).
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"

        // -Psubscriptions=false builds the app with no subscription at all: no paywall and no Pro
        // entry points, every Pro feature free, and ads for everyone. RevenueCat stays in the build.
        buildConfigField("boolean", "SUBSCRIPTIONS", (findProperty("subscriptions") != "false").toString())

        buildConfigField(
            "String",
            "REVENUECAT_API_KEY",
            "\"${localProperties.getProperty("revenuecat.apiKey", "")}\"",
        )
    }

    androidResources {
        // The nine languages the app ships (the PRD's eight plus Russian).
        localeFilters += listOf("en", "es", "fr", "de", "zh", "hi", "ar", "iw", "ru")
    }

    buildTypes {
        debug {
            // Crash reports from development builds would drown out real ones. Pass
            // -PcrashlyticsInDebug=true to send them, e.g. to check the Crashlytics setup.
            manifestPlaceholders["crashlyticsEnabled"] =
                (project.findProperty("crashlyticsInDebug") ?: "false").toString()
        }
        release {
            manifestPlaceholders["crashlyticsEnabled"] = "true"
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        // The in-app language picker offers all nine languages, so none may be split out.
        language {
            enableSplit = false
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.revenuecat.purchases)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    // Mediation adapters: added with production IDs (AdMob PRD §2.2), pinned to versions
    // certified for play-services-ads 25.5.0.

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
