import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Módulo de funcionalidade: biblioteca Android + Compose + dependências de UI comuns. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("virgil.android.library")
        pluginManager.apply("virgil.android.compose")
        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))
            add("implementation", project(":core:data"))
            listOf(
                "androidx-compose-material3",
                "androidx-compose-material-icons-extended",
                "androidx-lifecycle-runtime-compose",
                "androidx-lifecycle-viewmodel-compose",
                "androidx-navigation-compose",
                "kotlinx-coroutines-android",
            ).forEach { add("implementation", catalog.findLibrary(it).get()) }
        }
    }
}
