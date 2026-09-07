package com.bakery.erp.domain

/** Condición representativa de un tramo del día — de la más despejada a la
 *  más severa, en ese orden (se usa para elegir la "peor" condición de una
 *  franja cuando varias horas caen en ella). */
enum class WeatherCondition {
    CLEAR, PARTLY_CLOUDY, CLOUDY, DRIZZLE, RAIN, STORM
}

/** Tramo del día (mañana/mediodía/tarde/noche) con su condición dominante —
 *  reemplaza la idea de "una sola hora de inicio", que no reflejaba bien
 *  franjas largas de lluvia intermitente. */
data class ForecastPeriod(
    val label: String,
    val rainProbabilityPercent: Int,
    val condition: WeatherCondition
)

/** Pronóstico simplificado para un día y una ubicación — solo lo que necesita
 *  el ajuste de cantidades sugeridas por lluvia (ver
 *  `com.bakery.erp.model.withRainBump`) y la tarjeta de clima por periodos. */
data class DailyForecast(
    val rainProbabilityPercent: Int,
    val periods: List<ForecastPeriod> = emptyList()
)

interface WeatherRepository {
    /**
     * Pronóstico para [dateMillis] en la ubicación dada.
     *
     * @return null si no se pudo obtener (sin red, error del servicio,
     *  timeout) — **nunca lanza**, para no bloquear el flujo base de
     *  sugerencias de pedido, que debe seguir funcionando aunque el clima
     *  falle.
     */
    suspend fun getForecast(latitude: Double, longitude: Double, dateMillis: Long): DailyForecast?
}
