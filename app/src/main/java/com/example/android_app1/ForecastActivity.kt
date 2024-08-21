package com.example.android_app1

import ForecastResponse
import WeatherResponse
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.http.GET
import retrofit2.http.Query

class ForecastActivity : AppCompatActivity() {

    private lateinit var forecastTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forecast)

        forecastTextView = findViewById(R.id.forecastTextView)

        val cityName = intent.getStringExtra("CITY_NAME") ?: "Unknown City"
        val latitude = intent.getDoubleExtra("CITY_LATITUDE", 0.0)
        val longitude = intent.getDoubleExtra("CITY_LONGITUDE", 0.0)

        title = "Forecast for $cityName"

        fetchForecast(latitude, longitude)
    }

    private fun fetchForecast(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getForecast(latitude, longitude)
                displayForecast(response)  // Explicitly cast the response
            } catch (e: Exception) {
                forecastTextView.text = "Error fetching forecast: ${e.message}"
            }
        }
    }

    private fun displayForecast(forecast: ForecastResponse) {
        val forecastText = buildString {
            append("7-Day Forecast:\n\n")
            for (i in forecast.daily.time.indices) {
                val date = forecast.daily.time[i]
                val tempMax = forecast.daily.temperature_2m_max[i]
                val tempMin = forecast.daily.temperature_2m_min[i]
                val weatherCode = forecast.daily.weathercode[i]

                append("Date: $date\n")
                append("Max Temp: $tempMax°C\n")
                append("Min Temp: $tempMin°C\n")
                append("Weather: ${getWeatherDescription(weatherCode)}\n\n")
            }
        }
        forecastTextView.text = forecastText
    }

    private fun getWeatherDescription(code: Int): String {
        // Use the same weather code mapping as in MainActivity
        return when (code) {
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            // ... add all other weather codes ...
            else -> "Unknown weather condition"
        }
    }
}


