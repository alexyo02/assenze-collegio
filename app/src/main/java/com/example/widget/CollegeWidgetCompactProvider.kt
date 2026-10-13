package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.CollegeConfig
import com.example.data.ResidencyRiskLevel
import com.example.util.CivilHolidaysUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

class CollegeWidgetCompactProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateCompactWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_COMPACT) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_COMPACT = "com.assenzecollegio.adisu.widget.ACTION_REFRESH_COMPACT"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CollegeWidgetCompactProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isEmpty()) return

            val intent = Intent(context, CollegeWidgetCompactProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
            }
            context.sendBroadcast(intent)
        }

        suspend fun updateCompactWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_college_4x1)

            // 1. Load Data
            val config = CollegeConfig(context)
            val settings = config.settingsFlow.value
            val database = AppDatabase.getDatabase(context)
            val records = database.absenceDao().getAllDirect()

            // 2. Compute Metrics
            val start = settings.academicYearStartDate
            val end = settings.academicYearEndDate
            val totalUsefulDays = CivilHolidaysUtil.countUsefulDays(start, end).coerceAtLeast(1)
            val minPresenceDays = ceil(totalUsefulDays * 0.56).toInt()
            val maxAllowedAbsences = (totalUsefulDays - minPresenceDays).coerceAtLeast(0)

            val distinctEffectiveAbsenceDays = mutableSetOf<Long>()
            val distinctJustifiedDays = mutableSetOf<Long>()

            for (record in records) {
                val cal = Calendar.getInstance().apply { timeInMillis = record.dateMillis }
                val recordEnd = record.endDateMillis
                while (cal.timeInMillis <= recordEnd) {
                    val day = cal.timeInMillis
                    if (day in start..end && !CivilHolidaysUtil.isExcludedDay(day)) {
                        if (record.isJustified) {
                            distinctJustifiedDays.add(day)
                        } else {
                            distinctEffectiveAbsenceDays.add(day)
                        }
                    }
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val totalAbsences = distinctEffectiveAbsenceDays.size
            val remainingBefore56 = (maxAllowedAbsences - totalAbsences).coerceAtLeast(0)
            val projectedPresence = ((totalUsefulDays - totalAbsences).toDouble() / totalUsefulDays.toDouble()) * 100.0

            val riskLevel = when {
                totalAbsences >= maxAllowedAbsences || projectedPresence <= 56.0 -> ResidencyRiskLevel.CRITICAL
                remainingBefore56 <= 15 || ((totalAbsences.toDouble() / totalUsefulDays.toDouble()) * 100.0) >= 32.0 -> ResidencyRiskLevel.WARNING
                else -> ResidencyRiskLevel.SAFE
            }

            // 3. Populate 4x1 View
            views.setTextViewText(R.id.widget_4x1_tv_percentage, String.format(Locale.US, "%.1f%%", projectedPresence))
            views.setTextViewText(R.id.widget_4x1_tv_absences, "$totalAbsences gg")
            views.setTextViewText(R.id.widget_4x1_tv_available, "$remainingBefore56 gg")

            when (riskLevel) {
                ResidencyRiskLevel.SAFE -> {
                    views.setViewVisibility(R.id.widget_4x1_badge_safe, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_4x1_badge_warn, View.GONE)
                    views.setViewVisibility(R.id.widget_4x1_badge_crit, View.GONE)
                }
                ResidencyRiskLevel.WARNING -> {
                    views.setViewVisibility(R.id.widget_4x1_badge_safe, View.GONE)
                    views.setViewVisibility(R.id.widget_4x1_badge_warn, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_4x1_badge_crit, View.GONE)
                }
                ResidencyRiskLevel.CRITICAL -> {
                    views.setViewVisibility(R.id.widget_4x1_badge_safe, View.GONE)
                    views.setViewVisibility(R.id.widget_4x1_badge_warn, View.GONE)
                    views.setViewVisibility(R.id.widget_4x1_badge_crit, View.VISIBLE)
                }
            }

            // 4. Pending Intents
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                100 + appWidgetId,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_4x1_root, openAppPendingIntent)

            val refreshIntent = Intent(context, CollegeWidgetCompactProvider::class.java).apply {
                action = ACTION_REFRESH_COMPACT
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                200 + appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_4x1_btn_refresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
