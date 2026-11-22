plugins {
    id("virgil.android.library")
}

android {
    namespace = "app.virgil.core.datastore"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}
