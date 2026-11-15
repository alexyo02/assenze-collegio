package com.example.formeit

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.widget.CollegeWidgetCompactProvider
import com.example.widget.CollegeWidgetProvider
import java.util.concurrent.TimeUnit

/**
 * Periodic silent refresh: keeps widgets current and records official snapshots so the
 * reconstructed history has enough resolution (FormeIt only exposes the latest values).
 * Cost per run: the same three HTTPS calls the official client makes at each start.
 */
class FormeitSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = FormeitRepository.get(applicationContext)
        if (!repo.hasStoredLogin()) return Result.success()
        return try {
            repo.refresh()
            CollegeWidgetProvider.updateAllWidgets(applicationContext)
            CollegeWidgetCompactProvider.updateAllWidgets(applicationContext)
            Result.success()
        } catch (e: FormeitException) {
            if (e.isNetwork) Result.retry() else Result.success()
        } catch (_: FormeitFirstAccessException) {
            Result.success()
        }
    }

    companion object {
        private const val NAME = "formeit_periodic_sync"
        const val INTERVAL_HOURS = 2L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FormeitSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
