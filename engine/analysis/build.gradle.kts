plugins {
    id("virgil.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.virgil.engine.analysis"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:text"))
    implementation(project(":core:database"))
    implementation(project(":engine:books"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
