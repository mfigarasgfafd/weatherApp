package com.example.android_app1

import ForecastResponse
import HourlyForecastResponse
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.icu.text.SimpleDateFormat
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import com.google.android.gms.tasks.OnSuccessListener
import java.util.Locale
import android.os.Build
import android.view.WindowInsetsController
import android.widget.Button
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.concurrent.TimeUnit
import android.app.NotificationChannel
import com.github.matteobattilana.weather.PrecipType
import com.github.matteobattilana.weather.WeatherView


class MainActivity : AppCompatActivity() {

    private lateinit var notificationManager: NotificationManager
    private val CHANNEL_ID = "WeatherChannel"
    private val NOTIFICATION_ID = 1
    private val WORK_NAME = "WeatherUpdateWork"


    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView


    private var isThunderstorm = false


    private lateinit var smallTemps: TextView
    private lateinit var weatherTextView: TextView
    private lateinit var bigTempView: TextView
    private lateinit var weatherStatus: TextView
    private lateinit var locationText: TextView
    private lateinit var chart1: LineChart
    private lateinit var forecastTextView: TextView
    private lateinit var hamburgerMenu: ImageButton
    private  lateinit var weatherView: WeatherView

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var currentCity: City? = null
    private val cities = mutableListOf<City>()

    private var x1 = 0f
    private var x2 = 0f

    companion object {
         val CHANNEL_ID = "WeatherChannel"
         val NOTIFICATION_ID = 1
         val WORK_NAME = "WeatherUpdateWork"
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
//        forecastTextView = findViewById(R.id.forecastTextView)
        hamburgerMenu = findViewById(R.id.hamburgerMenu)

        weatherView = findViewById<WeatherView>(R.id.weather_view)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)
        loadCitiesIntoDrawer(cities)




        val searchView = navView.getHeaderView(0).findViewById<SearchView>(R.id.search_view)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredCities = cities.filter { city ->
                    city.name.contains(newText ?: "", ignoreCase = true)
                }
                loadCitiesIntoDrawer(filteredCities)
                return true
            }
        })


        chart1.axisLeft.setDrawGridLines(false)
        chart1.xAxis.setDrawGridLines(false);
        chart1.axisRight.setDrawGridLines(false);
        chart1.xAxis.setLabelCount(5, /*force: */true)
        chart1.xAxis.axisMinimum = 0.0f
        chart1.xAxis.axisMaximum = 24.0f
        chart1.animateY(2000)


        // Load cities from CSV and create city chips
        loadCitiesFromCsv()

        // Request location permission



        val requestLocationButton: Button = findViewById(R.id.requestLocationButton)

        // Set an OnClickListener on the button to call requestLocationPermission()
        requestLocationButton.setOnClickListener {
            requestLocationPermission()
        }

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

            }
            // Close the drawer after item is clicked
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }


//        getLastKnownLocation()
        initializeDrawer()

        setupScrollListener()
        val lastCityName = getLastSelectedCity()

//val lastCityName = getLastSelectedCity()?.let { findCityByName(it) }

        if (lastCityName != null) {
            // Use city name to find the city and load weather data
            val lastCity = findCityByName(lastCityName)
            if (lastCity != null) {
                loadWeatherForCity(lastCity)
            } else {
                getLastKnownLocation()
            }
        } else {
            getLastKnownLocation()
        }




        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        findViewById<Button>(R.id.enableNotificationsButton).setOnClickListener {
            toggleNotifications()
        }



    }

    private fun findCityByName(cityName: String): City? {
        return cities.find { it.name.equals(cityName, ignoreCase = true) }
    }

    private fun loadWeatherForCity(city: City) {
        // Fetch weather data and hourly temperature data for the selected city
        fetchWeatherData(city.latitude, city.longitude)
        fetchHourlyTemperatureData(city.latitude, city.longitude)
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
                        currentCity = nearestCity
                    } else {
                        forecastTextView.text = "Unable to find nearest city."
                    }
                } ?: run {
                    forecastTextView.text = "Unable to get last known location."
                }
            })
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun toggleNotifications() {
        val workManager = WorkManager.getInstance(applicationContext)

        if (isWorkScheduled(WORK_NAME)) {
            // Cancel the work if it's already scheduled
            workManager.cancelUniqueWork(WORK_NAME)
            findViewById<Button>(R.id.enableNotificationsButton).text = "Enable Notifications"
        } else {
            // Get current city location
            val latitude = currentCity?.latitude ?: 0.0
            val longitude = currentCity?.longitude ?: 0.0

            // Schedule the work
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val inputData = workDataOf(
                "latitude" to latitude,
                "longitude" to longitude
            )

            val weatherUpdateWork = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(15, TimeUnit.MINUTES)
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                weatherUpdateWork
            )
            findViewById<Button>(R.id.enableNotificationsButton).text = "Disable Notifications"
        }
    }


    private fun isWorkScheduled(workName: String): Boolean {
        val instance = WorkManager.getInstance(applicationContext)
        val statuses = instance.getWorkInfosForUniqueWork(workName).get()
        return statuses.any { !it.state.isFinished }
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
        updateWeatherBackgroundImage(forecastResponse.daily.temperature_2m_max.first(), forecastResponse.daily.weathercode.first())
        val constraintLayout = findViewById<ConstraintLayout>(R.id.mainConstraintLayout)

        val thunder_keywords = listOf("thunder", "thunderstorm", "lightning")
        val rain_keywords = listOf("rain", "drizzle")

        if (rain_keywords.any { it in weatherStatus.text }) {
            weatherView.setWeatherData(PrecipType.RAIN)
            isThunderstorm = false
        } else if (thunder_keywords.any { it in weatherStatus.text }) {
            val baseColor = getColor(R.color.skyblue_background)
            val darkenedColor = ColorUtils.blendARGB(baseColor, Color.BLACK, 0.5F)
            constraintLayout.setBackgroundColor(darkenedColor)
            window.statusBarColor = darkenedColor

            weatherView.setWeatherData(PrecipType.RAIN)
            isThunderstorm = true // Set the flag to true
        } else {
            weatherView.setWeatherData(PrecipType.CLEAR)
            isThunderstorm = false
        }
    }


    private fun updateWeatherBackgroundImage(temperature: Double, weatherCode: Int) {
        val weatherBackgroundImageView = findViewById<ImageView>(R.id.weatherBackgroundImageView)

        val weatherDescription = getWeatherDescription(weatherCode)
        val weatherBackgroundRes = when (weatherDescription) {
            "Clear sky" -> R.drawable.bg_sunny
            "Mainly clear" -> R.drawable.bg_sunny
            "Partly cloudy" -> R.drawable.bg_overcast
            "Overcast" -> R.drawable.bg_overcast
            "Fog", "Depositing rime fog" -> R.drawable.bg_windy
            "Light drizzle", "Moderate drizzle", "Dense drizzle" -> R.drawable.bg_rainy
            "Light freezing drizzle", "Dense freezing drizzle" -> R.drawable.bg_snowy
            "Slight rain", "Moderate rain", "Heavy rain" -> R.drawable.bg_rainy
            "Light freezing rain", "Heavy freezing rain" -> R.drawable.bg_snowy
            "Slight snow fall", "Moderate snow fall", "Heavy snow fall" -> R.drawable.bg_snowy
            "Snow grains" -> R.drawable.bg_snowy
            "Slight rain showers", "Moderate rain showers", "Violent rain showers" -> R.drawable.bg_rainy
            "Slight snow showers", "Heavy snow showers" -> R.drawable.bg_snowy
            "Slight or moderate thunderstorm", "Thunderstorm with slight hail", "Thunderstorm with heavy hail" -> R.drawable.bg_thunder
            else -> R.drawable.bg_overcast // Default case if no match found
        }

        weatherBackgroundImageView.setImageResource(weatherBackgroundRes)

        // Make the ImageView visible and apply the animation
        weatherBackgroundImageView.visibility = View.VISIBLE
        val slideInAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        weatherBackgroundImageView.startAnimation(slideInAnimation)
    }
    // Update hourly forecast UI components (like the line chart)
    private fun updateHourlyForecastUI(hourlyForecastResponse: HourlyForecastResponse) {
        updateLineChart(hourlyForecastResponse)
    }


    private fun updateForecastTable(forecastResponse: ForecastResponse) {
        val forecastLinearLayout = findViewById<LinearLayout>(R.id.forecastLinearLayout)
        forecastLinearLayout.removeAllViews() // Clear existing views

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault()) // For day of week

        forecastResponse.daily.time.forEachIndexed { index, time ->
            val date = dateFormat.parse(time)
            val dayOfWeek = dayFormat.format(date)
            val maxTemp = forecastResponse.daily.temperature_2m_max[index]
            val minTemp = forecastResponse.daily.temperature_2m_min[index]
            val weatherCode = forecastResponse.daily.weathercode[index]

            val forecastItemView = layoutInflater.inflate(R.layout.forecast_item, forecastLinearLayout, false)

            forecastItemView.findViewById<TextView>(R.id.dayOfWeekTextView).text = "${dayOfWeek}"
            forecastItemView.findViewById<TextView>(R.id.temperatureTextView).text = "\t\t${maxTemp.toInt()}°/${minTemp.toInt()}°"
            forecastItemView.findViewById<TextView>(R.id.weatherDescriptionTextView).text = "\t${
                getWeatherDescription(
                    weatherCode
                )
            }"

            forecastLinearLayout.addView(forecastItemView)
        }
    }

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
        chart1.xAxis.setDrawAxisLine(false)


    }
    private fun saveSelectedCity(cityName: String) {
        val sharedPreferences = getSharedPreferences("WeatherAppPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("LastSelectedCity", cityName)
        editor.apply()
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



    private fun loadCitiesIntoDrawer(citiesToLoad: List<City>) {
        val menu = navView.menu
        menu.clear() // Clear existing items

        citiesToLoad.forEach { city ->
            menu.add(city.name).apply {
                setOnMenuItemClickListener {
                    // Handle city selection
                    currentCity = city
                    saveSelectedCity(city.name)
                    fetchWeatherData(city.latitude, city.longitude)
                    fetchHourlyTemperatureData(city.latitude, city.longitude)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
            }
        }
    }

    private fun getLastSelectedCity(): String? {
        val sharedPreferences = getSharedPreferences("WeatherAppPreferences", MODE_PRIVATE)
        return sharedPreferences.getString("LastSelectedCity", null)
    }


    private fun initializeDrawer() {
        loadCitiesIntoDrawer(cities)  // Load all cities initially
        setupSearchView()
    }
    private fun setupSearchView() {
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        val searchView = navigationView.getHeaderView(0).findViewById<SearchView>(R.id.search_view)
        searchView.onActionViewExpanded()
        searchView.clearFocus();

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val filteredCities = if (newText.isNullOrBlank()) {
                    cities  // Show all cities if search is empty
                } else {
                    cities.filter { city ->
                        city.name.contains(newText, ignoreCase = true)
                    }
                }
                loadCitiesIntoDrawer(filteredCities)
                return true
            }
        })
    }

    private fun setupScrollListener() {
        val scrollView = findViewById<ScrollView>(R.id.mainScrollView)
        val constraintLayout = findViewById<ConstraintLayout>(R.id.mainConstraintLayout)

        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val maxScroll = scrollView.getChildAt(0).height - scrollView.height
            val scrollY = scrollView.scrollY

            if (maxScroll > 0) {
                val scrollFraction = scrollY.toFloat() / maxScroll
                val baseColor = getColor(R.color.skyblue_background)

                // Adjust the start color based on whether it's already darkened ( thunderstorm)
                val startColor = if (isThunderstorm) ColorUtils.blendARGB(baseColor, Color.BLACK, 0.5F) else baseColor

                val darkenedColor = ColorUtils.blendARGB(startColor, Color.BLACK, scrollFraction)

                // Fade the background color of the layout
                constraintLayout.setBackgroundColor(darkenedColor)

                // Fade the status bar color
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    window.statusBarColor = darkenedColor

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val insetsController = window.insetsController
                        insetsController?.setSystemBarsAppearance(
                            if (ColorUtils.calculateLuminance(darkenedColor) > 0.5)
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            else
                                0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val decorView = window.decorView
                            if (ColorUtils.calculateLuminance(darkenedColor) > 0.5) {
                                decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            } else {
                                decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                            }
                        }
                    }
                }
            }
        }
    }





}




