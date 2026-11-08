plugins { id("com.android.application") }

android {
    namespace = "com.villageclock.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.villageclock.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.15"
    }
    buildTypes { release { isMinifyEnabled = false } }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("org.robolectric:robolectric:4.17")
}

tasks.withType<Test>().configureEach {
    outputs.dir(rootProject.layout.projectDirectory.dir("outputs/previews"))
}
