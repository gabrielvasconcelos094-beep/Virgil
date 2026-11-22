plugins {
    id("virgil.android.library")
}

android {
    namespace = "app.virgil.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    implementation(libs.kotlinx.coroutines.android)
}
