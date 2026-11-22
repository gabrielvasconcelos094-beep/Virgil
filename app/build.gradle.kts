plugins {
    id("virgil.android.application")
    id("virgil.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.virgil"

    defaultConfig {
        applicationId = "app.virgil"
        versionCode = 1
        versionName = "0.1.0-fundacao"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assinatura de release: configurar keystore própria antes de distribuir (ver README).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions.unitTests.isIncludeAndroidResources = true

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":feature:library"))
    implementation(project(":feature:vocabulary"))
    implementation(project(":feature:review"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Capturas de tela das telas reais (Robolectric + gráficos nativos). Saída: app/build/outputs/roborazzi
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", "true")
    systemProperty("robolectric.pixelCopyRenderMode", "hardware")
}
