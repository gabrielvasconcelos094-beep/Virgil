pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Virgil"

include(":app")

// Núcleo: modelos de domínio, design, persistência e repositórios
include(":core:model")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:text")

// Motores (sem UI)
include(":engine:books")

// Funcionalidades (uma por área de navegação)
include(":feature:library")
include(":feature:vocabulary")
include(":feature:review")
include(":feature:progress")
include(":feature:settings")
