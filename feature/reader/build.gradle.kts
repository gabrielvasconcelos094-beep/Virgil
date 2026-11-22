plugins {
    id("virgil.android.feature")
}

android {
    namespace = "app.virgil.feature.reader"
}

dependencies {
    implementation(project(":engine:books"))
    implementation(project(":core:text"))
    implementation(project(":core:database"))
    implementation(project(":engine:translation"))
}
