plugins {
    id("virgil.android.library")
}

android {
    namespace = "app.virgil.engine.translation"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":engine:llama"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.serialization.json)
}

tasks.withType<Test>().configureEach {
    System.getenv("VIRGIL_LLAMA_HOST_LIB")?.let {
        systemProperty("virgil.llama.lib", it)
        // A biblioteca nativa só pode ser carregada por um classloader por JVM (Robolectric usa outro).
        forkEvery = 1
    }
    System.getenv("VIRGIL_TEST_MODEL")?.let { systemProperty("virgil.test.model", it) }
}
