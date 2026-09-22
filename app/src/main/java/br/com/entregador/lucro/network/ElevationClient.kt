package br.com.entregador.lucro.network

import org.json.JSONObject
import java.util.Locale

/**
 * Cliente de Altimetria e Relevo para ciclistas (Bike).
 * Detecta ladeiras e subidas íngremes que causam desgaste físico excessivo ao entregador.
 */
object ElevationClient {

    private const val STEEP_INCLINE_THRESHOLD_METERS = 35

    // Termos de bairros e acidentes geográficos que caracterizam subidas íngremes no relevo urbano brasileiro
    private val HILLY_KEYWORDS = setOf(
        "morro", "alto", "serra", "ladeira", "colina", "mirante", "grota", "subida",
        "perdizes", "sumare", "pompeia", "santa teresa", "bela vista", "jaguare",
        "morumbi", "tremembe", "serra da cantareira", "santa tereza"
    )

    data class ElevationProfile(
        val isSteepIncline: Boolean,
        val elevationGainMeters: Int,
        val description: String
    )

    /**
     * Avalia o perfil de relevo do trajeto para Bicicleta:
     * 1. Consulta heurística de relevo por palavras-chave do destino/bairro.
     * 2. Se houver coordenadas e conexão, consulta a Open-Elevation API.
     */
    suspend fun analyzeElevation(
        destinationText: String,
        originLat: Double? = null,
        originLon: Double? = null,
        destLat: Double? = null,
        destLon: Double? = null
    ): ElevationProfile {
        val normalizedDest = destinationText.lowercase()
            .replace("ã", "a").replace("á", "a").replace("é", "e").replace("ó", "o")

        val containsHillyKeyword = HILLY_KEYWORDS.any { normalizedDest.contains(it) }

        // Se foram passadas coordenadas válidas, tenta consultar a Open-Elevation API
        if (originLat != null && originLon != null && destLat != null && destLon != null) {
            try {
                val locStr = String.format(Locale.US, "%.4f,%.4f|%.4f,%.4f", originLat, originLon, destLat, destLon)
                val url = "https://api.open-elevation.com/api/v1/lookup?locations=$locStr"
                val netResult = HttpClientProvider.get(url)

                if (netResult.isSuccess) {
                    val json = JSONObject(netResult.getOrThrow())
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() >= 2) {
                        val altOrigin = results.getJSONObject(0).optDouble("elevation", 0.0)
                        val altDest = results.getJSONObject(1).optDouble("elevation", 0.0)
                        val delta = (altDest - altOrigin).toInt()

                        if (delta >= STEEP_INCLINE_THRESHOLD_METERS) {
                            return ElevationProfile(
                                isSteepIncline = true,
                                elevationGainMeters = delta,
                                description = "Subida Íngreme detectada (+${delta}m de desnível) 🚴⚠️"
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback para análise de relevo nominal
            }
        }

        // Avaliação de relevo nominal baseada em topografia de bairros
        return analyzeElevationNominal(destinationText)
    }

    /**
     * Avaliação síncrona instantânea (<1ms) baseada em topografia de bairros e acidentes de relevo no Brasil.
     * Seguro para execução direta na thread principal sem bloquear leitura de acessibilidade.
     */
    fun analyzeElevationNominal(destinationText: String): ElevationProfile {
        val normalizedDest = destinationText.lowercase()
            .replace("ã", "a").replace("á", "a").replace("é", "e").replace("ó", "o")

        val containsHillyKeyword = HILLY_KEYWORDS.any { normalizedDest.contains(it) }
        return if (containsHillyKeyword) {
            ElevationProfile(
                isSteepIncline = true,
                elevationGainMeters = 45,
                description = "Relevo acidentado / Subida íngreme no bairro 🚴⚠️"
            )
        } else {
            ElevationProfile(
                isSteepIncline = false,
                elevationGainMeters = 0,
                description = "Trajeto com relevo moderado / plano 🚴"
            )
        }
    }
}
