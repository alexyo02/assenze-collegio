package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AbsenceRecord
import com.example.data.AppDatabase
import com.example.data.CollegeConfig
import com.example.data.ResidencyRiskLevel
import com.example.util.CivilHolidaysUtil
import com.example.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class CollegeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val minHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                if (minHeight >= 170) {
                    setWidgetExpanded(context, appWidgetId, true)
                }
                updateWidget(context, appWidgetManager, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_EXPAND -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val current = isWidgetExpanded(context, appWidgetId)
                    setWidgetExpanded(context, appWidgetId, !current)

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            updateWidget(context, appWidgetManager, appWidgetId)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_REFRESH -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_EXPAND = "com.assenzecollegio.adisu.widget.ACTION_TOGGLE_EXPAND"
        const val ACTION_REFRESH = "com.assenzecollegio.adisu.widget.ACTION_REFRESH"
        private const val PREFS_NAME = "widget_prefs"

        fun isWidgetExpanded(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean("expanded_$appWidgetId", false)
        }

        fun setWidgetExpanded(context: Context, appWidgetId: Int, expanded: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("expanded_$appWidgetId", expanded).apply()
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CollegeWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isEmpty()) return

            val intent = Intent(context, CollegeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, allWidgetIds)
            }
            context.sendBroadcast(intent)
        }

        suspend fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_college_absence)

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

            // 3. Populate Header & Metrics
            views.setTextViewText(R.id.widget_tv_subtitle, "${settings.collegeName} • ${settings.roomNumber}")
            views.setTextViewText(R.id.widget_tv_percentage, String.format(Locale.US, "%.1f%%", projectedPresence))
            views.setTextViewText(R.id.widget_tv_absences, "$totalAbsences gg")
            views.setTextViewText(R.id.widget_tv_available, "$remainingBefore56 gg")
            views.setTextViewText(R.id.widget_tv_useful, "$totalUsefulDays gg")

            when (riskLevel) {
                ResidencyRiskLevel.SAFE -> {
                    views.setViewVisibility(R.id.widget_tv_badge_safe, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_tv_badge_warn, View.GONE)
                    views.setViewVisibility(R.id.widget_tv_badge_crit, View.GONE)
                }
                ResidencyRiskLevel.WARNING -> {
                    views.setViewVisibility(R.id.widget_tv_badge_safe, View.GONE)
                    views.setViewVisibility(R.id.widget_tv_badge_warn, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_tv_badge_crit, View.GONE)
                }
                ResidencyRiskLevel.CRITICAL -> {
                    views.setViewVisibility(R.id.widget_tv_badge_safe, View.GONE)
                    views.setViewVisibility(R.id.widget_tv_badge_warn, View.GONE)
                    views.setViewVisibility(R.id.widget_tv_badge_crit, View.VISIBLE)
                }
            }

            // 4. Handle Expandable Calendar
            val isExpanded = isWidgetExpanded(context, appWidgetId)
            if (isExpanded) {
                views.setViewVisibility(R.id.widget_calendar_section, View.VISIBLE)
                views.setTextViewText(R.id.widget_btn_toggle_expand, "▴ Comprimi")

                val now = Calendar.getInstance()
                val monthName = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(Date()).replaceFirstChar { it.uppercase() }
                views.setTextViewText(R.id.widget_tv_calendar_month, monthName)

                // Render high-res crisp calendar grid bitmap
                val calendarBitmap = generateMonthCalendarBitmap(
                    context = context,
                    year = now.get(Calendar.YEAR),
                    month = now.get(Calendar.MONTH),
                    records = records
                )
                views.setImageViewBitmap(R.id.widget_calendar_image, calendarBitmap)
            } else {
                views.setViewVisibility(R.id.widget_calendar_section, View.GONE)
                views.setTextViewText(R.id.widget_btn_toggle_expand, "📅 Calendario ▾")
            }

            // 5. Setup PendingIntents
            // Open App on widget click
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_dashboard_section, openAppPendingIntent)

            // Toggle Expand Button
            val toggleIntent = Intent(context, CollegeWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_EXPAND
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_toggle_expand, togglePendingIntent)

            // Refresh Button
            val refreshIntent = Intent(context, CollegeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 9999,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun generateMonthCalendarBitmap(
            context: Context,
            year: Int,
            month: Int,
            records: List<AbsenceRecord>
        ): Bitmap {
            val width = 560
            val height = 280
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background
            val bgPaint = Paint().apply {
                color = Color.parseColor("#F8FAFC")
                isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 14f, 14f, bgPaint)

            // Days of week header
            val daysOfWeek = listOf("L", "M", "M", "G", "V", "S", "D")
            val thPaint = Paint().apply {
                color = Color.parseColor("#64748B")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            val colWidth = width / 7f
            var topY = 32f

            for (i in 0 until 7) {
                val cx = i * colWidth + colWidth / 2f
                canvas.drawText(daysOfWeek[i], cx, topY, thPaint)
            }

            // Divider under week days
            val linePaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1.5f
            }
            canvas.drawLine(10f, topY + 8f, width - 10f, topY + 8f, linePaint)

            topY += 24f

            // Determine calendar grid
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            // Monday=1, Sunday=7 in standard Italian week
            val firstDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday=0, Tuesday=1 ... Sunday=6

            val today = Calendar.getInstance()
            val isCurrentYearMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month
            val currentDayOfMonth = if (isCurrentYearMonth) today.get(Calendar.DAY_OF_MONTH) else -1

            val rowHeight = (height - topY - 10f) / 6f

            // Map each day of this month to absence type (if any)
            val absencesMap = mutableMapOf<Int, Boolean>() // day -> isJustified
            for (record in records) {
                val rCal = Calendar.getInstance().apply { timeInMillis = record.dateMillis }
                val rEnd = record.endDateMillis
                while (rCal.timeInMillis <= rEnd) {
                    if (rCal.get(Calendar.YEAR) == year && rCal.get(Calendar.MONTH) == month) {
                        val d = rCal.get(Calendar.DAY_OF_MONTH)
                        absencesMap[d] = record.isJustified
                    }
                    rCal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val textPaint = Paint().apply {
                textSize = 19f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }

            val circlePaint = Paint().apply {
                isAntiAlias = true
            }

            var currentDay = 1
            for (row in 0 until 6) {
                for (col in 0 until 7) {
                    if (row == 0 && col < firstDayOfWeek) {
                        continue
                    }
                    if (currentDay > maxDaysInMonth) {
                        break
                    }

                    val cx = col * colWidth + colWidth / 2f
                    val cy = topY + row * rowHeight + rowHeight / 2f

                    val checkCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, currentDay)
                    }
                    val isExcluded = CivilHolidaysUtil.isExcludedDay(checkCal.timeInMillis)
                    val isAbsent = absencesMap.containsKey(currentDay)
                    val isJustified = absencesMap[currentDay] == true
                    val isToday = currentDay == currentDayOfMonth

                    // Draw indicators
                    if (isAbsent) {
                        // Colored pill for absence
                        circlePaint.color = if (isJustified) Color.parseColor("#3B82F6") else Color.parseColor("#EF4444")
                        circlePaint.style = Paint.Style.FILL
                        canvas.drawCircle(cx, cy - 2f, 15f, circlePaint)

                        textPaint.color = Color.WHITE
                        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else if (isToday) {
                        // Ring for today
                        circlePaint.color = Color.parseColor("#7C3AED")
                        circlePaint.style = Paint.Style.STROKE
                        circlePaint.strokeWidth = 2.5f
                        canvas.drawCircle(cx, cy - 2f, 15f, circlePaint)

                        textPaint.color = Color.parseColor("#7C3AED")
                        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    } else if (isExcluded) {
                        textPaint.color = Color.parseColor("#94A3B8")
                        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    } else {
                        textPaint.color = Color.parseColor("#1E293B")
                        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }

                    canvas.drawText("$currentDay", cx, cy + 5f, textPaint)
                    currentDay++
                }
                if (currentDay > maxDaysInMonth) break
            }

            return bitmap
        }
    }
}
