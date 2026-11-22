plugins {
    id("virgil.android.feature")
}

android {
    namespace = "app.virgil.feature.library"
}

dependencies {
    implementation(project(":engine:books"))
}
