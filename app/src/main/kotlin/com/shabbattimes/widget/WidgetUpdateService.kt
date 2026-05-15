package com.shabbattimes.widget

import android.annotation.SuppressLint
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.RemoteViews
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WidgetUpdateService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val EXTRA_WIDGET_IDS = "extra_widget_ids"

        fun startUpdate(context: Context, appWidgetIds: IntArray) {
            val intent = Intent(context, WidgetUpdateService::class.java).apply {
                putExtra(EXTRA_WIDGET_IDS, appWidgetIds)
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val widgetIds = intent?.getIntArrayExtra(EXTRA_WIDGET_IDS) ?: IntArray(0)
        serviceScope.launch {
            try {
                fetchAndUpdateWidget(widgetIds)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchAndUpdateWidget(widgetIds: IntArray) {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val allWidgetIds = if (widgetIds.isEmpty()) {
            AppWidgetManager.getInstance(this)
                .getAppWidgetIds(ComponentName(this, ShabbatWidget::class.java))
        } else {
            widgetIds
        }

        if (allWidgetIds.isEmpty()) return

        if (!hasLocationPermission()) {
            val views = buildNoPermissionView()
            allWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            return
        }

        val location = try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)
            val cts = CancellationTokenSource()
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
        } catch (e: Exception) {
            null
        }

        if (location == null) {
            val views = buildErrorView("Location unavailable")
            allWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
            return
        }

        val times = ShabbatApiService.fetchShabbatTimes(location.latitude, location.longitude)

        val views = if (times != null) {
            buildSuccessView(times)
        } else {
            buildErrorView("Could not load times")
        }

        allWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
    }

    private fun hasLocationPermission(): Boolean {
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        return coarse == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                fine == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun buildNoPermissionView(): RemoteViews {
        return RemoteViews(packageName, R.layout.widget_shabbat).apply {
            setTextViewText(R.id.tv_header, getString(R.string.app_name))
            setTextViewText(R.id.tv_candle_label, "")
            setTextViewText(R.id.tv_candle_time, "")
            setTextViewText(R.id.tv_havdalah_label, "")
            setTextViewText(R.id.tv_havdalah_time, "")
            setTextViewText(R.id.tv_shabbat_date, getString(R.string.tap_to_grant))
            setTextViewText(R.id.tv_refresh, "")
        }
    }

    private fun buildErrorView(message: String): RemoteViews {
        return RemoteViews(packageName, R.layout.widget_shabbat).apply {
            setTextViewText(R.id.tv_header, getString(R.string.app_name))
            setTextViewText(R.id.tv_candle_label, "")
            setTextViewText(R.id.tv_candle_time, "")
            setTextViewText(R.id.tv_havdalah_label, "")
            setTextViewText(R.id.tv_havdalah_time, "")
            setTextViewText(R.id.tv_shabbat_date, message)
            setTextViewText(R.id.tv_refresh, getString(R.string.tap_to_refresh))
        }
    }

    private fun buildSuccessView(times: ShabbatTimes): RemoteViews {
        return RemoteViews(packageName, R.layout.widget_shabbat).apply {
            setTextViewText(R.id.tv_header, getString(R.string.app_name))
            setTextViewText(R.id.tv_shabbat_date, times.shabbatDate)
            setTextViewText(R.id.tv_candle_label, getString(R.string.candle_lighting))
            setTextViewText(R.id.tv_candle_time, times.candleLighting)
            setTextViewText(R.id.tv_havdalah_label, getString(R.string.havdalah))
            setTextViewText(R.id.tv_havdalah_time, times.havdalah)
            setTextViewText(R.id.tv_refresh, getString(R.string.tap_to_refresh))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
