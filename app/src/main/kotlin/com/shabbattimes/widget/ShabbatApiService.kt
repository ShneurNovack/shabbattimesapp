package com.shabbattimes.widget

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ShabbatItem(
    @SerializedName("category") val category: String,
    @SerializedName("title") val title: String,
    @SerializedName("date") val date: String
)

data class ShabbatResponse(
    @SerializedName("items") val items: List<ShabbatItem>
)

data class ShabbatTimes(
    val candleLighting: String,
    val havdalah: String,
    val shabbatDate: String
)

object ShabbatApiService {

    private val client = OkHttpClient()
    private val gson = Gson()

    fun fetchShabbatTimes(latitude: Double, longitude: Double): ShabbatTimes? {
        val url = "https://www.hebcal.com/shabbat?cfg=json&geo=pos" +
                "&latitude=$latitude&longitude=$longitude&M=on"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ShabbatTimesWidget/1.0")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseResponse(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseResponse(json: String): ShabbatTimes? {
        return try {
            val response = gson.fromJson(json, ShabbatResponse::class.java)
            val items = response.items ?: return null

            val candleItem = items.firstOrNull { it.category == "candles" }
            val havdalahItem = items.firstOrNull { it.category == "havdalah" }

            if (candleItem == null || havdalahItem == null) return null

            val candleTime = formatTime(candleItem.date)
            val havdalahTime = formatTime(havdalahItem.date)
            val shabbatDate = formatShabbatDate(candleItem.date)

            ShabbatTimes(
                candleLighting = candleTime,
                havdalah = havdalahTime,
                shabbatDate = shabbatDate
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun formatTime(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val date = inputFormat.parse(isoDate) ?: return isoDate
            val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
            outputFormat.format(date)
        } catch (e: Exception) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                inputFormat.timeZone = TimeZone.getDefault()
                val date = inputFormat.parse(isoDate) ?: return isoDate
                val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
                outputFormat.format(date)
            } catch (e2: Exception) {
                isoDate
            }
        }
    }

    private fun formatShabbatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            val date = inputFormat.parse(isoDate) ?: return ""
            "Shabbat, ${SimpleDateFormat("MMMM d", Locale.US).format(date)}"
        } catch (e: Exception) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                inputFormat.timeZone = TimeZone.getDefault()
                val date = inputFormat.parse(isoDate) ?: return ""
                "Shabbat, ${SimpleDateFormat("MMMM d", Locale.US).format(date)}"
            } catch (e2: Exception) {
                ""
            }
        }
    }
}
