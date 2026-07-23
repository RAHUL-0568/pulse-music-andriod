plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "tf.pulsemusic.music"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int
    
    defaultConfig {
        applicationId = "tf.pulsemusic.music"
        minSdk = rootProject.extra["minSdkVersion"] as Int
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        androidResources {
            ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlinOptions {
        jvmTarget = "21"
    }
}

repositories {
    flatDir {
        dirs("../capacitor-cordova-android-plugins/src/main/libs", "libs")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    
    val androidxAppCompatVersion = rootProject.extra["androidxAppCompatVersion"] as String
    val androidxCoordinatorLayoutVersion = rootProject.extra["androidxCoordinatorLayoutVersion"] as String
    val coreSplashScreenVersion = rootProject.extra["coreSplashScreenVersion"] as String
    val junitVersion = rootProject.extra["junitVersion"] as String
    val androidxJunitVersion = rootProject.extra["androidxJunitVersion"] as String
    val androidxEspressoCoreVersion = rootProject.extra["androidxEspressoCoreVersion"] as String

    implementation("androidx.appcompat:appcompat:$androidxAppCompatVersion")
    implementation("androidx.coordinatorlayout:coordinatorlayout:$androidxCoordinatorLayoutVersion")
    implementation("androidx.core:core-splashscreen:$coreSplashScreenVersion")
    
    implementation(project(":capacitor-android"))
    implementation(project(":capacitor-cordova-android-plugins"))

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    testImplementation("junit:junit:$junitVersion")
    androidTestImplementation("androidx.test.ext:junit:$androidxJunitVersion")
    androidTestImplementation("androidx.test.espresso:espresso-core:$androidxEspressoCoreVersion")
}

apply(from = "capacitor.build.gradle")

try {
    val servicesJSON = file("google-services.json")
    if (servicesJSON.exists()) {
        apply(plugin = "com.google.gms.google-services")
    }
} catch(e: Exception) {
    logger.info("google-services.json not found, google-services plugin not applied. Push Notifications won't work")
}
