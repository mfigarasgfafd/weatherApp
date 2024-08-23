package com.example.android_app1

import ForecastResponse
import HourlyForecastResponse
import WeatherResponse
import android.Manifest
import android.app.ActionBar.LayoutParams
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import com.google.android.gms.tasks.OnSuccessListener



class MainActivity : AppCompatActivity(), GestureDetector.OnGestureListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    private lateinit var smallTemps: TextView
    private lateinit var weatherTextView: TextView
    private lateinit var bigTempView: TextView
    private lateinit var weatherStatus: TextView
    private lateinit var locationText: TextView
    private lateinit var chart1: LineChart
    private lateinit var forecastTextView: TextView
    private lateinit var hamburgerMenu: ImageButton

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var currentCity: City? = null
    private val cities = mutableListOf<City>()

    private lateinit var gestureDetector: GestureDetector
    private var x1 = 0f
    private var x2 = 0f

    companion object {
        const val MIN_DISTANCE = 150
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.new_main_activity)




        // Initialize views
        bigTempView = findViewById(R.id.bigTempView)
        weatherStatus = findViewById(R.id.weatherStatus)
        smallTemps = findViewById(R.id.smallTemps)
        locationText = findViewById(R.id.locationText)
        chart1 = findViewById(R.id.chart1)
        forecastTextView = findViewById(R.id.forecastTextView)
        hamburgerMenu = findViewById(R.id.hamburgerMenu)


        gestureDetector = GestureDetector(this, this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)

        loadCitiesIntoDrawer()


        chart1.axisLeft.setDrawGridLines(false)
        chart1.xAxis.setDrawGridLines(false);
        chart1.axisRight.setDrawGridLines(false);
        chart1.xAxis.setLabelCount(5, /*force: */true)
        chart1.xAxis.axisMinimum = 0.0f
        chart1.xAxis.axisMaximum = 24.0f
        chart1.animateX(1300)
        chart1.animateY(1000)

        // set fill below graph

        // Load cities from CSV and create city chips
        loadCitiesFromCsv()

        // Request location permission
        requestLocationPermission()

        // Set hamburger menu click listener
        hamburgerMenu.setOnClickListener {
            // Open the sidebar or perform related action
            openSidebar()
        }



        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Handle Home click
                    Toast.makeText(this, "Home clicked", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_settings -> {
                    // Handle Settings click
                    Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show()
                }
//                R.id.nav_about -> {
//                    // Handle About click
//                    Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show()
//                }
            }
            // Close the drawer after item is clicked
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Example latitude and longitude for New York ((TEMPORARY))
//        val latitude = 40.7128
//        val longitude = -74.0060
        getLastKnownLocation()
//
//        fetchWeatherData(latitude, longitude)
//        fetchHourlyTemperatureData(latitude, longitude)

    }

    override fun onDown(p0: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(p0: MotionEvent) {
    }

    override fun onSingleTapUp(p0: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
        return false
    }

    override fun onLongPress(p0: MotionEvent) {
    }

    override fun onFling(p0: MotionEvent, p1: MotionEvent, p2: Float, p3: Float): Boolean {
        return false
    }


    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getLastKnownLocation()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation()
            } else {
                weatherTextView.text = "Location permission denied. Unable to find nearest city."
            }
        }
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener(this, OnSuccessListener<Location> { location ->
                location?.let {
                    val nearestCity = findNearestCity(it.latitude, it.longitude, cities)
                    if (nearestCity != null) {
                        // Fetch weather data and hourly temperature data for the nearest city
                        fetchWeatherData(nearestCity.latitude, nearestCity.longitude)
                        fetchHourlyTemperatureData(nearestCity.latitude, nearestCity.longitude)
                    } else {
                        forecastTextView.text = "Unable to find nearest city."
                    }
                } ?: run {
                    forecastTextView.text = "Unable to get last known location."
                }
            })
    }




    fun findNearestCity(currentLatitude: Double, currentLongitude: Double, cities: List<City>): City? {
        var nearestCity: City? = null
        var minDistance = Double.MAX_VALUE

        for (city in cities) {
            val distance = haversineDistance(currentLatitude, currentLongitude, city.latitude, city.longitude)
            if (distance < minDistance) {
                minDistance = distance
                nearestCity = city
            }
        }
        return nearestCity
    }


    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radius of the Earth in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c // Distance in kilometers
    }


    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in kilometers
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }


    private fun loadCitiesFromCsv() {
        val inputStream = assets.open("olym.csv")
        val reader = BufferedReader(InputStreamReader(inputStream))
        reader.readLine() // Skip header
        reader.forEachLine { line ->
            val values = line.split(",")
            if (values.size >= 8) {
                cities.add(City(
                    name = values[0],
                    country = values[1],
                    latitude = values[6].toDoubleOrNull() ?: 0.0,
                    longitude = values[7].toDoubleOrNull() ?: 0.0
                ))
            }
        }
    }

    private fun updateDailyForecastUI(forecastResponse: ForecastResponse, city: City) {
        bigTempView.text = "${forecastResponse.daily.temperature_2m_min.first()}°C"
        weatherStatus.text = "${getWeatherDescription(forecastResponse.daily.weathercode.first())}"
        locationText.text = city.name
        smallTemps.text = "${forecastResponse.daily.temperature_2m_max.first() }° / ${forecastResponse.daily.temperature_2m_min.first()}°"
        updateForecastTable(forecastResponse)
    }

    // Update hourly forecast UI components (like the line chart)
    private fun updateHourlyForecastUI(hourlyForecastResponse: HourlyForecastResponse) {
        updateLineChart(hourlyForecastResponse)
    }

//    private fun updateUI(forecastResponse: ForecastResponse, city: City) {
//        bigTempView.text = "Max Temperature: ${forecastResponse.daily.temperature_2m_max.first()}°C"
//        weatherStatus.text = "Weather: ${getWeatherDescription(forecastResponse.daily.weathercode.first())}"
//        locationText.text = city.name
//        updateLineChart(forecastResponse)  // Make sure this method is defined to handle ForecastResponse if needed
//        updateForecastTable(forecastResponse)
//    }
//




    private fun updateForecastTable(forecastResponse: ForecastResponse) {
        val forecastTextView = findViewById<TextView>(R.id.forecastTextView)
        val forecastBuilder = StringBuilder()

        // Build a string to display the 7-day forecast
        forecastResponse.daily.time.forEachIndexed { index, time ->
            val maxTemp = forecastResponse.daily.temperature_2m_max[index]
            val minTemp = forecastResponse.daily.temperature_2m_min[index]
            val weatherCode = forecastResponse.daily.weathercode[index]

            // Add data to the string
            forecastBuilder.append("Date: $time\n")
                .append("Max Temp: ${maxTemp}°C\n")
                .append("Min Temp: ${minTemp}°C\n")
                .append("Weather: ${getWeatherDescription(weatherCode)}\n\n")
        }

        forecastTextView.text = forecastBuilder.toString()
    }

    // Example function to interpret weather codes
    private fun getWeatherDescription(weatherCode: Int): String {
        return when (weatherCode) {
            0 -> "Clear sky"
            1 -> "Mainly clear"
            2 -> "Partly cloudy"
            3 -> "Overcast"
            45 -> "Fog"
            48 -> "Depositing rime fog"
            51 -> "Light drizzle"
            53 -> "Moderate drizzle"
            55 -> "Dense drizzle"
            56 -> "Light freezing drizzle"
            57 -> "Dense freezing drizzle"
            61 -> "Slight rain"
            63 -> "Moderate rain"
            65 -> "Heavy rain"
            66 -> "Light freezing rain"
            67 -> "Heavy freezing rain"
            71 -> "Slight snow fall"
            73 -> "Moderate snow fall"
            75 -> "Heavy snow fall"
            77 -> "Snow grains"
            80 -> "Slight rain showers"
            81 -> "Moderate rain showers"
            82 -> "Violent rain showers"
            85 -> "Slight snow showers"
            86 -> "Heavy snow showers"
            95 -> "Slight or moderate thunderstorm"
            96 -> "Thunderstorm with slight hail"
            99 -> "Thunderstorm with heavy hail"
            // Add more weather codes as needed
            else -> "Unknown"
        }
    }



    private fun fetchWeatherData(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getForecast(
                    latitude = latitude,
                    longitude = longitude,
                    daily = "weathercode,temperature_2m_max,temperature_2m_min",
                    timezone = "auto"
                )
                findNearestCity(latitude, longitude, cities)?.let {
                    updateDailyForecastUI(response,
                        it
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                forecastTextView.text = "Error fetching weather data."
            }
        }
    }



    //    private fun fetchDailyForecast(latitude: Double, longitude: Double) {
//        lifecycleScope.launch {
//            try {
//                val response = RetrofitClient.instance.getDailyForecast(latitude, longitude)
//                updateUI(response)
//            } catch (e: Exception) {
//                e.printStackTrace()
//                forecastTextView.text = "Error fetching daily forecast."
//            }
//        }
//    }
    private fun fetchHourlyTemperatureData(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getHourlyTemperature(
                    latitude = latitude,
                    longitude = longitude,
                    hourly = "temperature_2m",
                    timezone = "auto"
                )
                updateHourlyForecastUI(response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }



    private fun updateLineChart(hourlyForecastResponse: HourlyForecastResponse) {
        val entries = mutableListOf<Entry>()

        // Plot hourly temperatures
        hourlyForecastResponse.hourly.time.forEachIndexed { index, _ ->
            val temperature = hourlyForecastResponse.hourly.temperature_2m[index].toFloat()
            entries.add(Entry(index.toFloat(), temperature))
        }

        val dataSet = LineDataSet(entries, "Temperature")
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 12f

        // Apply a light gradient under the line
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(100, 22, 22, 235), Color.TRANSPARENT) // Light blue to transparent
        )
        dataSet.setDrawFilled(true)
        dataSet.fillDrawable = gradientDrawable

        // Customize the chart
        val lineData = LineData(dataSet)
        chart1.data = lineData
        chart1.invalidate()  // Refresh chart

        // Remove the legend
        chart1.legend.isEnabled = false

        // Disable grid lines
        chart1.axisLeft.setDrawGridLines(false)
        chart1.xAxis.setDrawGridLines(false)
        chart1.axisRight.setDrawGridLines(false)

        // Set up the X axis
        chart1.xAxis.setLabelCount(5, true)
        chart1.xAxis.axisMinimum = 0.0f
        chart1.xAxis.axisMaximum = 24.0f

        // Additional customization (optional)
        chart1.axisLeft.textColor = Color.WHITE
        chart1.xAxis.textColor = Color.WHITE
        chart1.description.isEnabled = false // Remove description label
        chart1.setTouchEnabled(true)
        chart1.setPinchZoom(true)
        chart1.isDragEnabled = true


        dataSet.setDrawHighlightIndicators(false)
        chart1.isHighlightPerDragEnabled = false
        chart1.isHighlightPerTapEnabled = false

        chart1.axisRight.isEnabled = false
//        chart1.xAxis.setDrawLabels(false)
////        chart1.xAxis.setDrawAxisLine(false)


    }



    private fun parseHourlyTime(timeString: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = sdf.parse(timeString)
        return date?.time ?: 0
    }

    private fun formatHourlyTime(timeInMillis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timeInMillis))
    }


    private fun openSidebar() {
        drawerLayout.openDrawer(GravityCompat.START)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun loadCitiesIntoDrawer() {
        val menu = navView.menu

        menu.clear()

        // Load cities from CSV
        loadCitiesFromCsv()

        // Add each city as a menu item
        cities.forEach { city ->
            val menuItem = menu.add(city.name)
            menuItem.setOnMenuItemClickListener {
                currentCity = city
                fetchWeatherData(city.latitude, city.longitude)
                fetchHourlyTemperatureData(city.latitude, city.longitude)
                drawerLayout.closeDrawer(GravityCompat.START)  // Close the drawer
                true
            }
        }
    }










}

