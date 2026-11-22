package app.virgil

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fumaça de ponta a ponta com a Activity e o grafo de dependências reais (sem simulações):
 * inicialização, as quatro áreas, Configurações e a tela do modelo de tradução.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    /** O WorkManager de teste precisa existir antes de a regra do Compose criar a Activity. */
    @get:Rule(order = 0) val workManager = object : org.junit.rules.ExternalResource() {
        override fun before() = WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext<Context>())
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun waitText(t: String) = try {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/falha-fumaca.png")
        throw e
    }

    @Test fun navegaPelasAreasPrincipais() {
        waitText("Sua estante está vazia")
        compose.onNodeWithText("Vocabulário").performClick()
        waitText("Seu vocabulário começa nos livros")
        compose.onNodeWithText("Revisar").performClick()
        waitText("Nada pendente")
        compose.onNodeWithText("Progresso").performClick()
        waitText("Tempo de leitura")
        compose.onNodeWithText("Biblioteca").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Configurações").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Configurações").performClick()
        waitText("Modelo de tradução")
        compose.onNodeWithText("Modelo de tradução").performScrollTo().performClick()
        waitText("TranslateGemma 4B")
        waitText("Instalar a partir de arquivo")
    }
}
