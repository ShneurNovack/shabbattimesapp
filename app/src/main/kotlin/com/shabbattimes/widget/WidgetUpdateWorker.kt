package com.shabbattimes.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_WIDGET_IDS = "widget_ids"

        fun enqueue(context: Context, appWidgetIds: IntArray) {
            val data = Data.Builder()
                .putIntArray(KEY_WIDGET_IDS, appWidgetIds)
                .build()
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val widgetIds = inputData.getIntArray(KEY_WIDGET_IDS) ?: IntArray(0)
        fetchAndUpdateWidget(widgetIds)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchAndUpdateWidget(widgetIds: IntArray) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val allWidgetIds = if (widgetIds.isEmpty()) {
            appWidgetManager.getAppWidgetIds(ComponentName(context, ShabbatWidget::class.java))
        } else {
            widgetIds
        }

        if (allWidgetIds.isEmpty()) return

        if (!hasLocationPermission()) {
            val views = buildNoPermissionView()
            allWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            return
        }

        val location = try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
        } catch (e: Exception) {
            null
        }

        if (location == null) {
            val views = buildErrorView("Location unavailable")
            allWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            return
        }

        val times = ShabbatApiService.fetchShabbatTimes(location.latitude, location.longitude)
        val views = if (times != null) buildSuccessView(times) else buildErrorView("Could not load times")
        allWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    private fun hasLocationPermission(): Boolean {
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        return coarse == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                fine == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun buildNoPermissionView(): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_shabbat).apply {
            setTextViewText(R.id.tv_header, context.getString(R.string.app_name))
            setTextViewText(R.id.tv_candle_label, "")
            setTextViewText(R.id.tv_candle_time, "")
            setTextViewText(R.id.tv_havdalah_label, "")
            setTextViewText(R.id.tv_havdalah_time, "")
            setTextViewText(R.id.tv_shabbat_date, context.getString(R.string.tap_to_grant))
            setTextViewText(R.id.tv_refresh, "")
        }

    private fun buildErrorView(message: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_shabbat).apply {
            setTextViewText(R.id.tv_header, context.getString(R.string.app_name))
            setTextViewText(R.id.tv_candle_label, "")
            setTextViewText(R.id.tv_candle_time, "")
            setTextViewText(R.id.tv_havdalah_label, "")
            setTextViewText(R.id.tv_havdalah_time, "")
            setTextViewText(R.id.tv_shabbat_date, message)
            setTextViewText(R.id.tv_refresh, context.getString(R.string.tap_to_refresh))
        }

    private fun buildSuccessView(times: ShabbatTimes): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_shabbat).apply {
            setTextViewText(R.id.tv_header, context.getString(R.string.app_name))
            setTextViewText(R.id.tv_shabbat_date, times.shabbatDate)
            setTextViewText(R.id.tv_candle_label, context.getString(R.string.candle_lighting))
            setTextViewText(R.id.tv_candle_time, times.candleLighting)
            setTextViewText(R.id.tv_havdalah_label, context.getString(R.string.havdalah))
            setTextViewText(R.id.tv_havdalah_time, times.havdalah)
            setTextViewText(R.id.tv_refresh, context.getString(R.string.tap_to_refresh))
        }
}
