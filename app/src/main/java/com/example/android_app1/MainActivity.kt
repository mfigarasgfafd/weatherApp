package com.example.android_app1

import android.content.pm.PackageManager
import android.graphics.drawable.AnimationDrawable
import android.location.Location
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.android_app1.ui.theme.Android_app1Theme
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.launch
import kotlin.math.*
import android.Manifest
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity(), GestureDetector.OnGestureListener {

    lateinit var gestureDetector: GestureDetector
    var x2:Float = 0.0f
    var x1:Float = 0.0f
    var y2:Float = 0.0f
    var y1:Float = 0.0f


    companion object{
        const val MIN_DISTANCE = 150
    }


    private lateinit var weatherTextView: TextView
    private lateinit var cityChipGroup: ChipGroup
    private val cities = mutableListOf<City>()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var forecastButton: Button
    private var currentCity: City? = null


    override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)

            val rootView = findViewById<ConstraintLayout>(R.id.rootView)

            val drawable : AnimationDrawable = rootView.background as AnimationDrawable
            drawable.setEnterFadeDuration(1500)
            drawable.setExitFadeDuration(2000)
            drawable.start()


            // image rotate animation
            val imageView = findViewById<ImageView>(R.id.imageView)
            val rotateAnimation = RotateAnimation(
                0f,
                360f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            rotateAnimation.duration = 10000 // 10 seconds for a full rotation
            rotateAnimation.repeatCount = Animation.INFINITE
            imageView.startAnimation(rotateAnimation)

            //
            weatherTextView = findViewById(R.id.weatherTextView)
            cityChipGroup = findViewById(R.id.cityChipGroup)
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            // fetchWeatherData()
        requestLocationPermission()

        forecastButton = findViewById(R.id.forecastButton)
        forecastButton.setOnClickListener {
            currentCity?.let { city ->
                val intent = Intent(this, ForecastActivity::class.java)
                intent.putExtra("CITY_NAME", city.name)
                intent.putExtra("CITY_LATITUDE", city.latitude)
                intent.putExtra("CITY_LONGITUDE", city.longitude)
                startActivity(intent)
            } ?: run {
                print(currentCity)
                Toast.makeText(this, "Please select a city first", Toast.LENGTH_SHORT).show()
            }
        }

        loadCitiesFromCsv()
        createCityChips()

        this.gestureDetector = GestureDetector(this, this)
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event != null){
            gestureDetector.onTouchEvent(event)
        }
        when(event?.action){
            0 -> {
                x1 = event.x
                y1 = event.y
            }
            1 -> {
                x2 = event.x
                y2 = event.y
                val valueX:Float = x2 - x1
                if(abs(valueX) > MIN_DISTANCE){
                    if(x2 > x1){
                        // right swipe
                        print("right swipe")
                    }else{
                        val intent = Intent(this, SwipeActivity::class.java)
                    }
                }
            }
        }
        return super.onTouchEvent(event)
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
                    fetchWeatherData(nearestCity)
                } ?: run {
                    weatherTextView.text = "Unable to get last known location."
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
// testr
    private fun createCityChips() {
        cities.forEach { city ->
            val chip = Chip(this)
            chip.text = "${city.name}, ${city.country}"
            chip.isCheckable = true
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currentCity = city
                    fetchWeatherData(city)
                }
            }
            cityChipGroup.addView(chip)
        }
    }

    private fun fetchWeatherData(city: City) {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getCurrentWeather(city.latitude, city.longitude)

                val weatherDescription = when (response.current_weather.weathercode) {
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
                    else -> "Unknown weather condition " + response.current_weather.weathercode
                }

                val weatherInfo = buildString {
                    append("City: ${city.name}, ${city.country}\n\n")
                    append("Weather: $weatherDescription\n")
                    append("Temperature: ${response.current_weather.temperature}°C\n")
                    append("Wind Speed: ${response.current_weather.windspeed} km/h\n")
                    append("Wind Direction: ${response.current_weather.winddirection}°\n")
                    append("Time: ${response.current_weather.time}")
                }

                weatherTextView.text = weatherInfo
            } catch (e: Exception) {
                weatherTextView.text = "Error fetching weather data: ${e.message}"
            }
        }
    }

}

