plugins {
    id("virgil.android.library")
}

android {
    namespace = "app.virgil.engine.llama"
    ndkVersion = "28.2.13676358"

    defaultConfig {
        // Inferência local só faz sentido em ARM64 (praticamente todos os celulares desde 2017).
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
                cppFlags += listOf("-O3")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

// Teste com o modelo real na JVM: requer a biblioteca nativa de host e um .gguf (ver LlamaHostTest).
tasks.withType<Test>().configureEach {
    System.getenv("VIRGIL_LLAMA_HOST_LIB")?.let { systemProperty("virgil.llama.lib", it) }
    System.getenv("VIRGIL_TEST_MODEL")?.let { systemProperty("virgil.test.model", it) }
    System.getenv("VIRGIL_TEST_MODEL_FAMILY")?.let { systemProperty("virgil.test.family", it) }
}
