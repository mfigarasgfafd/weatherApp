package com.example.android_app1

import HourlyForecastResponse
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.icu.text.SimpleDateFormat
import com.example.android_app1.MainActivity.Companion.CHANNEL_ID
import com.example.android_app1.MainActivity.Companion.NOTIFICATION_ID
import java.util.Locale

class WeatherUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {


    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val latitude = inputData.getDouble("latitude", 0.0)
        val longitude = inputData.getDouble("longitude", 0.0)

        // Fetch hourly weather data
        val hourlyForecastResponse = fetchHourlyTemperatureData(latitude, longitude)

        // Prepare and send notification
        sendHourlyNotification(hourlyForecastResponse)

        return Result.success()
    }

    private suspend fun fetchHourlyTemperatureData(latitude: Double, longitude: Double): HourlyForecastResponse {
        return RetrofitClient.instance.getHourlyTemperature(
            latitude = latitude,
            longitude = longitude,
            hourly = "temperature_2m",
            timezone = "auto"
        )
    }

    private fun sendHourlyNotification(hourlyForecastResponse: HourlyForecastResponse) {
        val hourlyData = hourlyForecastResponse.hourly

        val temperature = hourlyData.temperature_2m[0]
        val time = hourlyData.time[0]

        // Format the time to display in the notification
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val formattedTime = outputFormat.format(inputFormat.parse(time))

        // Create the notification text
        val notificationText = "Temperature at $formattedTime: ${temperature}°C"

        // Create and display the notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Hourly Weather Update")
            .setContentText(notificationText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
