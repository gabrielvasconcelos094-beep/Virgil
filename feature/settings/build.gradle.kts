plugins {
    id("virgil.android.feature")
}

android {
    namespace = "app.virgil.feature.settings"
}

dependencies {
    implementation(project(":engine:translation"))
    implementation(project(":engine:speech"))
    implementation(libs.androidx.work.runtime)
}
