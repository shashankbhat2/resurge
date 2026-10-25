package com.resurgeai.core.model

import java.time.LocalDate

/** What the sky is doing, from a WMO weather code. */
enum class WeatherKind(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    SNOW("Snow"),
    STORM("Thunderstorms");

    companion object {
        /** WMO 4677 codes as Open-Meteo reports them. */
        fun fromCode(code: Int): WeatherKind = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> STORM
            else -> CLOUDY
        }
    }
}

/** One day's weather: conditions with the high and low, in °C. */
data class DayWeather(val date: LocalDate, val kind: WeatherKind, val highC: Double, val lowC: Double)

/** Now plus the last couple of weeks by day, for Today's day strip. */
data class Weather(val nowC: Double?, val nowKind: WeatherKind?, val days: Map<LocalDate, DayWeather>)
