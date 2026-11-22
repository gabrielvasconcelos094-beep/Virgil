import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Constantes compartilhadas por todos os módulos do Virgil. */
object VirgilBuild {
    const val COMPILE_SDK = 37
    const val COMPILE_SDK_MINOR = 2
    const val MIN_SDK = 28   // Android 9: ~95% da base ativa; mmap/NEON/fp16 confiáveis para inferência local
    const val TARGET_SDK = 36
    val JAVA = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal val Project.catalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureAndroidCommon(ext: CommonExtension) {
    ext.compileSdk = VirgilBuild.COMPILE_SDK
    ext.compileSdkMinor = VirgilBuild.COMPILE_SDK_MINOR
    ext.defaultConfig.minSdk = VirgilBuild.MIN_SDK
    ext.compileOptions.sourceCompatibility = VirgilBuild.JAVA
    ext.compileOptions.targetCompatibility = VirgilBuild.JAVA
    configureKotlin()
}

internal fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(VirgilBuild.JVM_TARGET)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}
