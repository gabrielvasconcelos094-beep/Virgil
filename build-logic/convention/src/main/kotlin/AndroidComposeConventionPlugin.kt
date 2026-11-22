import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Habilita Compose em um módulo Android (aplicativo ou biblioteca). */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val ext: CommonExtension = (extensions.findByType(ApplicationExtension::class.java)
            ?: extensions.getByType(LibraryExtension::class.java))
        ext.buildFeatures.compose = true
        dependencies {
            val bom = catalog.findLibrary("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))
            add("implementation", catalog.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("debugImplementation", catalog.findLibrary("androidx-compose-ui-tooling").get())
        }
    }
}
