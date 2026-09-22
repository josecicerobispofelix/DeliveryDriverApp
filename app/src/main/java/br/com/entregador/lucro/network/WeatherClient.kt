package br.com.entregador.lucro.network

import org.json.JSONObject
import java.util.Locale

/**
 * Cliente de Clima utilizando a Open-Meteo API (100% gratuita, sem necessidade de chave de API).
 * Detecta em tempo real se está chovendo na região do entregador para ativar o Piso Dinâmico de Mau Tempo.
 */
object WeatherClient {

    // Códigos WMO que indicam chuva, garoa ou tempestade
    private val RAIN_WEATHER_CODES = setOf(
        51, 53, 55, // Chuvisco / Garoa
        56, 57,     // Chuvisco congelante
        61, 63, 65, // Chuva fraca, moderada e forte
        66, 67,     // Chuva congelante
        80, 81, 82, // Pancadas de chuva
        95, 96, 99  // Tempestade com raios e chuva forte
    )

    data class WeatherReport(
        val isRaining: Boolean,
        val precipitationMm: Double,
        val weatherCode: Int,
        val description: String
    )

    /**
     * Consulta as condições climáticas atuais para as coordenadas dadas.
     * @param latitude Ex: -23.5505 (São Paulo) ou coordenada do GPS do entregador
     * @param longitude Ex: -46.6333
     */
    suspend fun checkWeather(
        latitude: Double = -23.5505,
        longitude: Double = -46.6333
    ): Result<WeatherReport> {
        val latStr = String.format(Locale.US, "%.4f", latitude)
        val lonStr = String.format(Locale.US, "%.4f", longitude)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latStr&longitude=$lonStr&current=weather_code,precipitation,rain,showers"

        val responseResult = HttpClientProvider.get(url)
        if (responseResult.isFailure) {
            return Result.failure(responseResult.exceptionOrNull() ?: Exception("Falha ao consultar clima"))
        }

        return try {
            val json = JSONObject(responseResult.getOrThrow())
            val current = json.optJSONObject("current") ?: return Result.failure(Exception("Resposta de clima inválida"))

            val weatherCode = current.optInt("weather_code", 0)
            val precipitation = current.optDouble("precipitation", 0.0)
            val rain = current.optDouble("rain", 0.0)
            val showers = current.optDouble("showers", 0.0)

            val totalRainMm = maxOf(precipitation, rain, showers)
            val isRaining = totalRainMm > 0.1 || weatherCode in RAIN_WEATHER_CODES

            val description = when {
                totalRainMm > 5.0 || weatherCode in setOf(65, 82, 95, 96, 99) -> "Chuva Forte / Tempestade ⛈️"
                totalRainMm > 1.5 || weatherCode in setOf(63, 81) -> "Chuva Moderada 🌧️"
                isRaining -> "Chuva Fraca / Garoa 🌦️"
                weatherCode in setOf(1, 2, 3) -> "Nublado ☁️"
                else -> "Tempo Firme / Ensolarado ☀️"
            }

            Result.success(
                WeatherReport(
                    isRaining = isRaining,
                    precipitationMm = totalRainMm,
                    weatherCode = weatherCode,
                    description = description
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
