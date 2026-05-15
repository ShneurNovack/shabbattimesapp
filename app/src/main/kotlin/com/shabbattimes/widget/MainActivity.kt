package com.shabbattimes.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            triggerWidgetUpdate()
            Toast.makeText(this, "Location granted! Refreshing widget...", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this,
                "Location permission is required to show Shabbat times for your area.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnAddWidget = findViewById<Button>(R.id.btn_add_widget)
        val btnGrantLocation = findViewById<Button>(R.id.btn_grant_location)
        val tvStatus = findViewById<TextView>(R.id.tv_status)

        updateStatus(tvStatus)

        btnAddWidget.setOnClickListener {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            startActivity(intent)
        }

        btnGrantLocation.setOnClickListener {
            requestLocationPermission()
        }

        val btnRefreshWidget = findViewById<Button>(R.id.btn_refresh_widget)
        btnRefreshWidget.setOnClickListener {
            triggerWidgetUpdate()
            Toast.makeText(this, "Refreshing widget...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tv_status)
        updateStatus(tvStatus)
    }

    private fun updateStatus(tvStatus: TextView) {
        val hasLocation = hasLocationPermission()
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, ShabbatWidget::class.java)
        )
        val widgetCount = widgetIds.size

        val statusText = buildString {
            if (!hasLocation) {
                appendLine("Location permission not granted.")
                appendLine("Tap \"Grant Location\" to allow the widget to show times for your area.")
            } else {
                appendLine("Location permission granted.")
            }
            appendLine()
            if (widgetCount == 0) {
                appendLine("No widget added yet.")
                appendLine("Tap \"Add Widget\" or long-press your home screen and select Widgets to add the Shabbat Times widget.")
            } else {
                appendLine("Widget active ($widgetCount instance${if (widgetCount > 1) "s" else ""} on home screen).")
            }
        }
        tvStatus.text = statusText.trim()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun triggerWidgetUpdate() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(this, ShabbatWidget::class.java)
        )
        WidgetUpdateWorker.enqueue(this, widgetIds)
    }
}
