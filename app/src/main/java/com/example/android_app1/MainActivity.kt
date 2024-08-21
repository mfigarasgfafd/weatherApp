package com.example.android_app1

import HourlyForecastResponse
import WeatherResponse
import android.Manifest
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Color



class MainActivity : AppCompatActivity(), GestureDetector.OnGestureListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

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
        locationText = findViewById(R.id.locationText)
        chart1 = findViewById(R.id.chart1)
        forecastTextView = findViewById(R.id.forecastTextView)
        hamburgerMenu = findViewById(R.id.hamburgerMenu)

        gestureDetector = GestureDetector(this, this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)


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
                R.id.nav_about -> {
                    // Handle About click
                    Toast.makeText(this, "About clicked", Toast.LENGTH_SHORT).show()
                }
            }
            // Close the drawer after item is clicked
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Example latitude and longitude for New York ((TEMPORARY))
        val latitude = 40.7128
        val longitude = -74.0060
        fetchHourlyTemperatureData(latitude, longitude)

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
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    val nearestCity = findNearestCity(it.latitude, it.longitude)
                    fetchWeatherData(nearestCity.latitude, nearestCity.longitude)
                } ?: run {
                    forecastTextView.text = "Unable to get last known location."
                }
            }
    }


    private fun findNearestCity(latitude: Double, longitude: Double): City {
        val nearestCity = cities.minByOrNull { city ->
            calculateDistance(latitude, longitude, city.latitude, city.longitude)
        } ?: cities.first()
        currentCity = nearestCity
        return nearestCity
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


    private fun fetchWeatherData(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getHourlyTemperature(latitude, longitude)
                updateLineChart(response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchHourlyTemperatureData(latitude: Double, longitude: Double) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getHourlyTemperature(latitude, longitude)
                updateLineChart(response)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    private fun updateLineChart(response: HourlyForecastResponse) {
        val hourlyTemperature = response.hourly.temperature_2m
        val hourlyTimes = response.hourly.time.map { parseHourlyTime(it) }

        val entries = mutableListOf<Entry>()
        hourlyTimes.forEachIndexed { index, time ->
            entries.add(Entry(time.toFloat(), hourlyTemperature[index].toFloat()))
        }

        val dataSet = LineDataSet(entries, "Temperature")
        dataSet.color = Color.BLUE
        dataSet.setCircleColor(Color.BLUE)
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        dataSet.setDrawValues(false)

        val data = LineData(dataSet)
        chart1.data = data

        chart1.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return formatHourlyTime(value.toLong())
            }
        }

        chart1.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart1.xAxis.granularity = 1f
        chart1.xAxis.labelCount = 6

        chart1.axisRight.isEnabled = false
        chart1.axisLeft.granularity = 1f

        chart1.description.isEnabled = false
        chart1.legend.isEnabled = false
        chart1.invalidate()
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

}

