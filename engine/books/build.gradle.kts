plugins {
    id("virgil.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.virgil.engine.books"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:text"))
    implementation(project(":core:database"))
    implementation(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
