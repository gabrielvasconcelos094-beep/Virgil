package app.virgil

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.virgil.core.database.dao.VocabularyRow
import app.virgil.core.model.WidgetTheme
import app.virgil.widget.WidgetContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetTest {
    private val row = VocabularyRow(
        7, "scarcely", null, "en-GB", "Word", "mal · quase não", "She could scarcely believe what she had witnessed.",
        null, 1, "Mrs Dalloway", 0, 0, 2, "Learning", null,
    )

    @Test fun widgetGrandeMostraTermoTraducaoIdiomaEExemplo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(300.dp, 170.dp))
        provideComposable { WidgetContent(row, WidgetTheme.System, showExample = true) }
        onNode(hasTextEqualTo("scarcely")).assertExists()
        onNode(hasTextEqualTo("mal · quase não")).assertExists()
        onNode(hasTextEqualTo("EN")).assertExists()
        onNode(hasTextEqualTo("She could scarcely believe what she had witnessed.")).assertExists()
    }

    @Test fun widgetPequenoOmiteExemplo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(150.dp, 70.dp))
        provideComposable { WidgetContent(row, WidgetTheme.Dark, showExample = true) }
        onNode(hasTextEqualTo("scarcely")).assertExists()
        onNode(hasTextEqualTo("She could scarcely believe what she had witnessed.")).assertDoesNotExist()
    }

    @Test fun widgetVazioExplicaOQueAparecera() = runGlanceAppWidgetUnitTest {
        provideComposable { WidgetContent(null, WidgetTheme.Light, showExample = true) }
        onNode(hasTextEqualTo("As palavras que você consultar nos livros aparecerão aqui.")).assertExists()
    }
}
