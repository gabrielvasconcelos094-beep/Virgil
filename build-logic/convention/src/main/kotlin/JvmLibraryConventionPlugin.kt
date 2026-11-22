import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Módulo Kotlin puro (sem Android): regras de negócio testáveis na JVM. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = VirgilBuild.JAVA
            targetCompatibility = VirgilBuild.JAVA
        }
        configureKotlin()
        dependencies {
            add("testImplementation", catalog.findLibrary("junit").get())
            add("testImplementation", catalog.findLibrary("truth").get())
            add("testImplementation", catalog.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
