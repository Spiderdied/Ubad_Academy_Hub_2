import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is read from android/keystore.properties or environment
// variables. Neither is committed; without them the release build is unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)

// ── Public cloud client configuration ────────────────────────────────────────
// These values are PUBLIC BY DESIGN and are the same ones the web app ships in
// firebase-auth.js / index.html:
//   * the Firebase *web* API key only identifies the project; all access control
//     lives in firestore.rules / storage.rules, which are owner-only.
//   * the Worker URL is a public endpoint that authenticates every caller itself.
// They are therefore defaults here, and deliberately not secrets.
//
// The Google OAuth *web* client id has NO default: Credential Manager needs it,
// and it is specific to the Firebase project's OAuth clients. Supply it without
// committing anything via a Gradle property or an environment variable:
//   -Pubad.googleWebClientId=…   or   UBAD_GOOGLE_WEB_CLIENT_ID=…
// `android/keystore.properties` is also honoured. When it is absent, Google
// sign-in reports itself as unconfigured instead of failing at runtime.
//
// Nothing here is a credential. B2_KEY_ID, B2_APPLICATION_KEY and the Worker's
// FIREBASE_API_KEY are Cloudflare Worker secrets and never ship in the app.
val keystorePropsForConfig = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cloudValue(gradleKey: String, env: String, default: String = ""): String =
    providers.gradleProperty(gradleKey).orNull
        ?: System.getenv(env)
        ?: keystorePropsForConfig.getProperty(gradleKey)
        ?: default

val firebaseProjectId = cloudValue("ubad.firebaseProjectId", "UBAD_FIREBASE_PROJECT_ID", "ubad-academy-hub")
val firebaseWebApiKey = cloudValue("ubad.firebaseWebApiKey", "UBAD_FIREBASE_WEB_API_KEY", "AIzaSyB8mYXZ31BUDoPN5HeB1lpSy7_Tdhvnlyk")
val firebaseAppId = cloudValue("ubad.firebaseAppId", "UBAD_FIREBASE_APP_ID", "1:595289164594:web:6c34e660307af0a6a3652b")
val firebaseSenderId = cloudValue("ubad.firebaseSenderId", "UBAD_FIREBASE_SENDER_ID", "595289164594")
val firebaseStorageBucket = cloudValue("ubad.firebaseStorageBucket", "UBAD_FIREBASE_STORAGE_BUCKET", "ubad-academy-hub.firebasestorage.app")
val firebaseAuthDomain = cloudValue("ubad.firebaseAuthDomain", "UBAD_FIREBASE_AUTH_DOMAIN", "ubad-academy-hub.firebaseapp.com")
val cloudWorkerUrl = cloudValue("ubad.workerUrl", "UBAD_WORKER_URL", "https://ubad-academy-sync.abdalla-toaila34.workers.dev")
val googleWebClientId = cloudValue("ubad.googleWebClientId", "UBAD_GOOGLE_WEB_CLIENT_ID", "")

android {
    namespace = "com.ubad.academy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ubad.academy"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.15.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_WEB_API_KEY", "\"$firebaseWebApiKey\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"$firebaseStorageBucket\"")
        buildConfigField("String", "FIREBASE_AUTH_DOMAIN", "\"$firebaseAuthDomain\"")
        buildConfigField("String", "CLOUD_WORKER_URL", "\"$cloudWorkerUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "UBAD_KEYSTORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = signingValue("storePassword", "UBAD_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "UBAD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "UBAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
        buildConfig = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Cloud sync: Firebase Auth (Google sign-in) + Firestore, and Credential
    // Manager for the native Google ID-token flow.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.navigation.testing)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
