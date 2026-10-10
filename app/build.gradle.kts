plugins {
    id("com.android.application")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.flowstate"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "dev.flowstate"
        minSdk = providers.gradleProperty("flowstate.minSdk").orNull?.toInt() ?: 29
        require(minSdk!! >= 29) { "FlowState requires Android API 29 or later" }
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The process recovery test requires two separate invocations and an external force-stop.
        testInstrumentationRunnerArguments["notClass"] = "dev.flowstate.ProcessRecoveryTest"
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    val releaseStore = providers.environmentVariable("FLOWSTATE_STORE_FILE").orNull
    signingConfigs {
        if(!releaseStore.isNullOrBlank())create("personal") {
            storeFile=file(releaseStore)
            storePassword=providers.environmentVariable("FLOWSTATE_STORE_PASSWORD").get()
            keyAlias=providers.environmentVariable("FLOWSTATE_KEY_ALIAS").get()
            keyPassword=providers.environmentVariable("FLOWSTATE_KEY_PASSWORD").get()
        }
    }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); if(!releaseStore.isNullOrBlank())signingConfig=signingConfigs.getByName("personal") } }
    if(providers.gradleProperty("flowstate.releaseSmoke").orNull=="true")buildTypes.getByName("release").signingConfig=signingConfigs.getByName("debug")
    lint { abortOnError = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas"); arg("room.incremental", "true") }
dependencies {
    implementation(project(":engine"))
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
