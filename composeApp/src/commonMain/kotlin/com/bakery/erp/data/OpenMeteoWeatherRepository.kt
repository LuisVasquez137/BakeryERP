package com.bakery.erp.data

import com.bakery.erp.domain.DailyForecast
import com.bakery.erp.domain.ForecastPeriod
import com.bakery.erp.domain.WeatherCondition
import com.bakery.erp.domain.WeatherRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Serializable
private data class OpenMeteoResponse(
    val daily: OpenMeteoDaily? = null,
    val hourly: OpenMeteoHourly? = null
)

@Serializable
private data class OpenMeteoDaily(
    val time: List<String> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int>? = null
)

@Serializable
private data class OpenMeteoHourly(
    val time: List<String> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    val weathercode: List<Int>? = null
)

private val openMeteoJson = Json { ignoreUnknownKeys = true }

/** Tramos fijos del día (hora local, 0-23) que arma la tarjeta de clima —
 *  cubren el horario típico de producción/reparto, no las 24h. */
private data class HourWindow(val label: String, val hours: IntRange)

private val FORECAST_WINDOWS = listOf(
    HourWindow("Mañana", 6..10),
    HourWindow("Mediodía", 11..13),
    HourWindow("Tarde", 14..17),
    HourWindow("Noche", 18..21)
)

/** WMO weather code → condición simplificada. Códigos fuera de esta lista
 *  (madrugada sin dato, etc.) caen en CLOUDY como default neutro. */
private fun conditionFromWeatherCode(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3, 45, 48 -> WeatherCondition.CLOUDY
    51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherCondition.RAIN
    95, 96, 99 -> WeatherCondition.STORM
    else -> WeatherCondition.CLOUDY
}

/**
 * Pronóstico vía Open-Meteo (https://open-meteo.com) — gratis, sin API key,
 * pensado para llamarse directo desde el cliente (navegador incluido). Un
 * solo archivo `commonMain`: no necesita variantes por plataforma, solo
 * [httpGetText] (que sí las tiene).
 */
class OpenMeteoWeatherRepository : WeatherRepository {
    override suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        dateMillis: Long
    ): DailyForecast? = runCatching {
        val date = Instant.fromEpochMilliseconds(dateMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
            .toString()
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&daily=precipitation_probability_max&hourly=precipitation_probability,weathercode" +
            "&timezone=auto&start_date=$date&end_date=$date"
        val body = httpGetText(url) ?: return@runCatching null
        val response = openMeteoJson.decodeFromString<OpenMeteoResponse>(body)
        val daily = response.daily ?: return@runCatching null
        val index = daily.time.indexOf(date)
        if (index < 0) return@runCatching null
        val probability = daily.precipitationProbabilityMax?.getOrNull(index) ?: return@runCatching null
        DailyForecast(
            rainProbabilityPercent = probability,
            periods = buildPeriods(response.hourly, date)
        )
    }.getOrNull()

    /** Arma los tramos de [FORECAST_WINDOWS] a partir del detalle por hora —
     *  máximo de probabilidad y peor condición dentro de cada tramo. Lista
     *  vacía si no hay datos por hora (independiente del máximo diario, que
     *  Open-Meteo calcula aparte). */
    private fun buildPeriods(hourly: OpenMeteoHourly?, date: String): List<ForecastPeriod> {
        hourly ?: return emptyList()
        val probabilities = hourly.precipitationProbability ?: return emptyList()
        val codes = hourly.weathercode ?: return emptyList()
        val hourToIndex = mutableMapOf<Int, Int>()
        hourly.time.forEachIndexed { i, isoTime ->
            if (!isoTime.startsWith(date)) return@forEachIndexed
            val hour = isoTime.substringAfter('T').substringBefore(':').toIntOrNull() ?: return@forEachIndexed
            hourToIndex[hour] = i
        }
        return FORECAST_WINDOWS.mapNotNull { window ->
            val indices = window.hours.mapNotNull { hourToIndex[it] }
            if (indices.isEmpty()) return@mapNotNull null
            val maxProbability = indices.maxOf { probabilities.getOrNull(it) ?: 0 }
            val worstCondition = indices
                .mapNotNull { codes.getOrNull(it) }
                .map(::conditionFromWeatherCode)
                .maxByOrNull { it.ordinal } ?: WeatherCondition.CLOUDY
            ForecastPeriod(
                label = window.label,
                rainProbabilityPercent = maxProbability,
                condition = worstCondition
            )
        }
    }
}
