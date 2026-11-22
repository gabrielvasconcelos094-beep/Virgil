plugins {
    id("virgil.android.library")
    id("virgil.android.compose")
}

android {
    namespace = "app.virgil.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.text)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
}
