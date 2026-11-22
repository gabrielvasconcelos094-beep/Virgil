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
    api(project(":engine:srs"))
    implementation(libs.kotlinx.coroutines.android)
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
