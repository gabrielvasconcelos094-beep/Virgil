package app.virgil.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.virgil.core.designsystem.component.SectionLabel
import app.virgil.core.designsystem.component.SwitchRow
import app.virgil.core.designsystem.component.VirgilDivider
import app.virgil.core.designsystem.theme.Spacing
import app.virgil.core.designsystem.theme.Virgil
import app.virgil.core.designsystem.theme.VirgilShapes
import app.virgil.engine.translation.DownloadProgress
import app.virgil.engine.translation.EngineStatus
import app.virgil.engine.translation.ModelCatalog
import app.virgil.engine.translation.ModelDownloadWorker
import app.virgil.engine.translation.ModelSpec
import app.virgil.engine.translation.ModelStore
import app.virgil.engine.translation.Translator
import kotlinx.coroutines.launch
import java.util.Locale

private fun gb(bytes: Long) = String.format(Locale.forLanguageTag("pt-BR"), "%.2f GB", bytes / 1_000_000_000.0)

/** Instalação e remoção do modelo de tradução local (único modelo instalado por vez). */
@Composable
fun TranslationModelRoute(store: ModelStore, translator: Translator, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by store.rows.collectAsStateWithLifecycle(initialValue = emptyList())
    val progress by remember { ModelDownloadWorker.progress(context) }.collectAsStateWithLifecycle(initialValue = null)
    val status by translator.status.collectAsStateWithLifecycle()
    val ram = remember { store.totalRamBytes() }
    val recommended = remember(ram) { ModelCatalog.recommendedFor(ram) }
    var allowMetered by remember { mutableStateOf(false) }
    var confirmLicense by remember { mutableStateOf<ModelSpec?>(null) }
    var confirmRemove by remember { mutableStateOf<ModelSpec?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            importing = true
            message = store.importFromUri(uri).fold({ "${it.displayName} instalado a partir do arquivo." }, { it.message })
            importing = false
        }
    }

    fun startDownload(spec: ModelSpec) {
        val free = store.freeBytes()
        if (free < spec.sizeBytes + 200L * 1024 * 1024) {
            message = "Espaço insuficiente: ${spec.displayName} precisa de ${gb(spec.sizeBytes)} e há ${gb(free)} livres."
            return
        }
        ModelDownloadWorker.start(context, spec, allowMetered)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xxl)) {
        Row(Modifier.padding(start = Spacing.xs, top = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") }
            Text("Tradução", style = MaterialTheme.typography.headlineSmall, color = Virgil.colors.textPrimary)
        }
        Text(
            "O Virgil traduz com um modelo de linguagem executado inteiramente neste aparelho. Ele é baixado uma única vez; " +
                "depois disso a tradução funciona sem internet e nenhum trecho dos seus livros sai do aparelho.",
            style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )
        Text(
            "Memória deste aparelho: ${gb(ram)} · Espaço livre: ${gb(store.freeBytes())}",
            style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        statusLine(status)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.secondaryInk, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs))
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.accentInk, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
        }

        SectionLabel("Modelos", Modifier.padding(top = Spacing.md))
        ModelCatalog.all.forEach { spec ->
            val row = rows.firstOrNull { it.modelId == spec.id }
            ModelCard(
                spec = spec,
                recommended = spec.id == recommended.id,
                status = row?.status,
                downloadedBytes = row?.downloadedBytes ?: 0,
                progress = progress.takeIf { row?.status == ModelStore.STATUS_DOWNLOADING || row?.status == ModelStore.STATUS_VERIFYING },
                onDownload = { if (spec.licenseNoticePt != null) confirmLicense = spec else startDownload(spec) },
                onCancel = { ModelDownloadWorker.cancel(context); scope.launch { store.remove(spec) } },
                onRemove = { confirmRemove = spec },
            )
        }

        SwitchRow("Permitir download por dados móveis", "Por padrão o download só acontece em Wi-Fi.", allowMetered) { allowMetered = it }
        VirgilDivider(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg))
        SectionLabel("Sem internet")
        Text(
            "Se você já tem o arquivo .gguf de um dos modelos acima (por exemplo, copiado de um computador), pode instalá-lo daqui. " +
                "O arquivo só é aceito se for idêntico ao oficial (verificação SHA-256).",
            style = MaterialTheme.typography.bodyMedium, color = Virgil.colors.textSecondary, modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !importing, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
            Text(if (importing) "Verificando arquivo…" else "Instalar a partir de arquivo")
        }
    }

    confirmLicense?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmLicense = null },
            title = { Text("Termos do ${spec.displayName}") },
            text = { Text(spec.licenseNoticePt.orEmpty() + "\n\nTamanho do download: ${gb(spec.sizeBytes)}.") },
            confirmButton = { TextButton(onClick = { confirmLicense = null; startDownload(spec) }) { Text("Aceitar e baixar") } },
            dismissButton = { TextButton(onClick = { confirmLicense = null }) { Text("Cancelar") } },
        )
    }
    confirmRemove?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remover ${spec.displayName}?") },
            text = { Text("Libera ${gb(spec.sizeBytes)}. As traduções já feitas continuam guardadas no cache e no vocabulário.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; scope.launch { store.remove(spec) } }) { Text("Remover", color = Virgil.colors.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } },
        )
    }
}

private fun statusLine(s: EngineStatus): String? = when (s) {
    is EngineStatus.Unsupported -> s.reason
    is EngineStatus.Loading -> "Carregando ${s.model.displayName} na memória…"
    is EngineStatus.Ready -> "${s.model.displayName} carregado e pronto."
    is EngineStatus.Failed -> "Erro: ${s.message}"
    else -> null
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    recommended: Boolean,
    status: String?,
    downloadedBytes: Long,
    progress: DownloadProgress?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            .border(1.dp, if (recommended) Virgil.colors.secondary else Virgil.colors.divider, VirgilShapes.medium)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(spec.displayName, style = MaterialTheme.typography.titleMedium, color = Virgil.colors.textPrimary, modifier = Modifier.weight(1f))
            if (recommended) Text("RECOMENDADO", style = MaterialTheme.typography.labelSmall, color = Virgil.colors.secondaryInk)
        }
        Text(spec.summaryPt, style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        Text("${gb(spec.sizeBytes)} · ${spec.licenseName}", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textSecondary)
        Spacer(Modifier.height(Spacing.xs))
        when (status) {
            ModelStore.STATUS_READY -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Instalado", style = MaterialTheme.typography.labelLarge, color = Virgil.colors.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemove) { Text("Remover", color = Virgil.colors.error) }
            }
            ModelStore.STATUS_DOWNLOADING, ModelStore.STATUS_VERIFYING -> {
                val done = progress?.downloaded?.takeIf { it >= 0 } ?: downloadedBytes
                val verifying = status == ModelStore.STATUS_VERIFYING || progress?.verifying == true
                Text(
                    when {
                        verifying -> "Verificando integridade…"
                        progress?.waitingForNetwork == true -> "Aguardando conexão (${gb(done)} de ${gb(spec.sizeBytes)})"
                        else -> "Baixando: ${gb(done)} de ${gb(spec.sizeBytes)}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Virgil.colors.textPrimary,
                )
                if (verifying) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = Virgil.colors.primary, trackColor = Virgil.colors.divider)
                else LinearProgressIndicator(progress = { done.toFloat() / spec.sizeBytes }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Virgil.colors.primary, trackColor = Virgil.colors.divider, drawStopIndicator = {})
                TextButton(onClick = onCancel) { Text("Cancelar") }
            }
            ModelStore.STATUS_FAILED -> {
                Text(progress?.error ?: "O download falhou.", style = MaterialTheme.typography.bodySmall, color = Virgil.colors.error)
                OutlinedButton(onClick = onDownload) { Text("Tentar novamente") }
            }
            else -> OutlinedButton(onClick = onDownload) { Text("Baixar (${gb(spec.sizeBytes)})") }
        }
    }
}

