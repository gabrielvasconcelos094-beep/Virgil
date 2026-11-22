package app.virgil.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.virgil.AppContainer
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.feature.library.LibraryRoute
import app.virgil.feature.progress.ProgressRoute
import app.virgil.feature.review.ReviewRoute
import app.virgil.feature.settings.LicensesScreen
import app.virgil.feature.settings.SettingsRoute
import app.virgil.feature.vocabulary.VocabularyRoute
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object LibraryDest
@Serializable object VocabularyDest
@Serializable object ReviewDest
@Serializable object ProgressDest
@Serializable object SettingsDest
@Serializable object LicensesDest

private data class TopLevel(val route: Any, val routeClass: KClass<*>, val label: String, val icon: ImageVector)

private val TopLevels = listOf(
    TopLevel(LibraryDest, LibraryDest::class, "Biblioteca", Icons.AutoMirrored.Outlined.LibraryBooks),
    TopLevel(VocabularyDest, VocabularyDest::class, "Vocabulário", Icons.Outlined.Translate),
    TopLevel(ReviewDest, ReviewDest::class, "Revisar", Icons.Outlined.Spa),
    TopLevel(ProgressDest, ProgressDest::class, "Progresso", Icons.Outlined.Insights),
)

@Composable
fun VirgilApp(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = TopLevels.any { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                Column {
                    VirgilDivider()
                    NavigationBar(containerColor = Virgil.colors.background, tonalElevation = 0.dp) {
                        TopLevels.forEach { t ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Virgil.colors.primary,
                                    selectedTextColor = Virgil.colors.primary,
                                    indicatorColor = Virgil.colors.surfaceSunken,
                                    unselectedIconColor = Virgil.colors.textSecondary,
                                    unselectedTextColor = Virgil.colors.textSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = LibraryDest,
            modifier = Modifier.fillMaxSize().padding(padding),
            // Transições discretas: um esmaecimento curto, sem deslizamentos chamativos.
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable<LibraryDest> {
                SafeArea {
                    LibraryRoute(
                        repo = container.library,
                        onOpenBook = { /* Leitor: Fase 2 */ },
                        onOpenSettings = { nav.navigate(SettingsDest) },
                    )
                }
            }
            composable<VocabularyDest> { SafeArea { VocabularyRoute(container.vocabulary) } }
            composable<ReviewDest> { SafeArea { ReviewRoute(container.reviews) } }
            composable<ProgressDest> { SafeArea { ProgressRoute(container.progress) } }
            composable<SettingsDest> {
                SafeArea(includeBottom = true) {
                    SettingsRoute(
                        prefsRepo = container.preferences,
                        profileRepo = container.profiles,
                        onBack = { nav.popBackStack() },
                        onOpenLicenses = { nav.navigate(LicensesDest) },
                    )
                }
            }
            composable<LicensesDest> { SafeArea(includeBottom = true) { LicensesScreen(onBack = { nav.popBackStack() }) } }
        }
    }
}

