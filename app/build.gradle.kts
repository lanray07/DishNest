import java.net.URI
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val billingEnabled = providers.gradleProperty("dishnest.billing.enabled").orNull == "true"
val billingUrl = providers.gradleProperty("dishnest.billing.url").orNull.orEmpty()
val billingKey = providers.gradleProperty("dishnest.billing.publicKey").orNull.orEmpty()
val billingEnvironment = providers.gradleProperty("dishnest.billing.environment").orNull ?: "sandbox"
require(billingEnvironment in setOf("sandbox", "lat", "production"))
if (billingEnabled) {
    val endpoint = URI(billingUrl)
    require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null) { "Billing requires a valid HTTPS verifier URL" }
    KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(billingKey)))
    val amazonKey = file("src/main/assets/AppstoreAuthenticationKey.pem")
    require(amazonKey.isFile && amazonKey.readText().contains("PUBLIC KEY")) { "Billing requires the app-specific AppstoreAuthenticationKey.pem downloaded from Amazon; it is distinct from the receipt-service key" }
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "com.dishnest.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dishnest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "BILLING_ENABLED", billingEnabled.toString())
        buildConfigField("String", "BILLING_VERIFY_URL", quoted(billingUrl))
        buildConfigField("String", "BILLING_PUBLIC_KEY", quoted(billingKey))
        buildConfigField("String", "BILLING_ENVIRONMENT", quoted(billingEnvironment))
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("debug").manifest.srcFile(if (billingEnabled) "src/billingDebug/AndroidManifest.xml" else "src/debug/AndroidManifest.xml")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true; unitTests.all { it.maxHeapSize = "2g"; it.systemProperty("roborazzi.test.record", "true") } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.amazon.device:amazon-appstore-sdk:3.0.9")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.46.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.46.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
