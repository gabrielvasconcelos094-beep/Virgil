package app.virgil.feature.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.data.ReviewRepository
import app.virgil.core.designsystem.component.EmptyState
import app.virgil.core.designsystem.component.ScreenHeader

/**
 * Fase 1: mostra a contagem real de cartões vencidos (tabela review_cards).
 * A sessão de revisão com FSRS chega na Fase 5 (ADR-0005).
 */
@Composable
fun ReviewRoute(repo: ReviewRepository) {
    val now = remember { System.currentTimeMillis() }
    val due by remember(repo) { repo.dueCount(now) }.collectAsStateWithLifecycle(initialValue = 0)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader(
            title = "Revisar",
            subtitle = when (due) {
                0 -> "Nenhuma palavra para hoje"
                1 -> "1 palavra para revisar"
                else -> "$due palavras para revisar"
            },
        )
        EmptyState(
            icon = Icons.Outlined.Spa,
            title = "Nada pendente",
            message = "As palavras que você consultar nos livros voltarão aqui em intervalos espaçados, sempre com a frase em que apareceram.",
        )
    }
}
