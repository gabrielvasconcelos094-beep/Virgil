plugins {
    id("virgil.android.library")
}

android {
    namespace = "app.virgil.engine.speech"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
}
