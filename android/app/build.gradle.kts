plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.example.baseballsim"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.example.baseballsim"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2-pennant-tech"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notClass"] = "com.example.baseballsim.PennantProcessRecoveryTest"
        testInstrumentationRunnerArguments["additionalTestOutputDir"] = "/sdcard/Android/media/com.example.baseballsim/additional_test_output"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true; animationsDisabled = true }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }
ksp {
    arg("room.schemaLocation", "$projectDir/build/reports/room-schemas")
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
