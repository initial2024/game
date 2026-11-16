plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.serialization"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "cn.chronicle.game"
 compileSdk = 36
 ndkVersion = "27.2.12479018"
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
 defaultConfig { applicationId = "cn.chronicle.shanhe"; minSdk = 26; targetSdk = 36; versionCode = 9; versionName = "0.5.0-dev"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"; externalNativeBuild { cmake { cppFlags += "-O2"; arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON") } } }
 buildFeatures { compose = true }
 packaging { jniLibs.useLegacyPackaging = true }
 splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 signingConfigs {
  create("stable") {
   val key = System.getenv("CHRONICLE_KEYSTORE")
   if (key != null) { storeFile = file(key); storePassword = System.getenv("CHRONICLE_STORE_PASSWORD"); keyAlias = "chronicle"; keyPassword = System.getenv("CHRONICLE_KEY_PASSWORD") }
  }
 }
 buildTypes { getByName("release") { isMinifyEnabled = false; if (System.getenv("CHRONICLE_KEYSTORE") != null) signingConfig = signingConfigs.getByName("stable") } }
}
dependencies {
 implementation(project(":core"))
 implementation("androidx.webkit:webkit:1.13.0")
 implementation(platform("androidx.compose:compose-bom:2025.05.01"))
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
