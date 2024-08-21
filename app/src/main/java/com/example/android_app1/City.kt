package com.example.android_app1

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class City(val name: String, val country: String, val latitude: Double, val longitude: Double)

fun readCitiesFromCsv(context: Context): List<City> {
    val cities = mutableListOf<City>()
    val inputStream = context.assets.open("olym.csv")
    val reader = BufferedReader(InputStreamReader(inputStream))
    reader.readLine() // Skip header
    reader.forEachLine {
        val columns = it.split(",")
        if (columns.size >= 8) {
            val name = columns[0].trim()
            val country = columns[1].trim()
            val latitude = columns[6].trim().toDouble()
            val longitude = columns[7].trim().toDouble()
            cities.add(City(name, country, latitude, longitude))
        }
    }
    return cities
}
