package app.virgil.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.virgil.core.datastore.PreferencesStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Troca a palavra do widget no intervalo escolhido pelo usuário (sem rede, sem bateria relevante). */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        VocabularyWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "virgil-widget-refresh"

        /** Agenda (ou reagenda) a atualização periódica, só se houver widget na tela inicial. */
        suspend fun schedule(context: Context, replace: Boolean = false) {
            val hasWidget = GlanceAppWidgetManager(context).getGlanceIds(VocabularyWidget::class.java).isNotEmpty()
            if (!hasWidget) return
            val minutes = PreferencesStore(context).preferences.first().widgetRefresh.minutes
            val req = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(minutes, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req)
            if (replace) VocabularyWidget().updateAll(context)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
