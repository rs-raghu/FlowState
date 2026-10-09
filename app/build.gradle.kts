plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    kotlin("kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.flowstate"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.flowstate"
        minSdk = providers.gradleProperty("flowstate.minSdk").orNull?.toInt() ?: 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    lint { abortOnError = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(project(":engine"))
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.5")
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    kapt("androidx.room:room-compiler:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
