plugins {
    id("virgil.android.feature")
}

android {
    namespace = "app.virgil.feature.settings"
}

dependencies {
    implementation(project(":engine:translation"))
    implementation(libs.androidx.work.runtime)
}
