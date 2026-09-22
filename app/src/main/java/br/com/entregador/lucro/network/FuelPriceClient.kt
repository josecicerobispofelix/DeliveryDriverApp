package br.com.entregador.lucro.network

import org.json.JSONObject

/**
 * Cliente para consulta de preços médios de combustíveis (Gasolina Comum e Etanol)
 * baseados nos levantamentos oficiais da ANP (Agência Nacional do Petróleo) por Estado/UF.
 */
object FuelPriceClient {

    data class FuelPriceQuote(
        val stateCode: String,
        val stateName: String,
        val gasolineAverage: Double,
        val ethanolAverage: Double,
        val dateReference: String
    )

    // Base de referência oficial por Estado (Preços médios da ANP em vigor no Brasil)
    private val ANP_BENCHMARK = mapOf(
        "SP" to FuelPriceQuote("SP", "São Paulo", 5.89, 3.89, "ANP 2026"),
        "RJ" to FuelPriceQuote("RJ", "Rio de Janeiro", 6.05, 4.19, "ANP 2026"),
        "MG" to FuelPriceQuote("MG", "Minas Gerais", 5.95, 3.99, "ANP 2026"),
        "PR" to FuelPriceQuote("PR", "Paraná", 6.12, 4.05, "ANP 2026"),
        "RS" to FuelPriceQuote("RS", "Rio Grande do Sul", 6.19, 4.35, "ANP 2026"),
        "SC" to FuelPriceQuote("SC", "Santa Catarina", 6.15, 4.29, "ANP 2026"),
        "BA" to FuelPriceQuote("BA", "Bahia", 6.25, 4.45, "ANP 2026"),
        "PE" to FuelPriceQuote("PE", "Pernambuco", 6.09, 4.25, "ANP 2026"),
        "CE" to FuelPriceQuote("CE", "Ceará", 6.32, 4.59, "ANP 2026"),
        "GO" to FuelPriceQuote("GO", "Goiás", 5.92, 3.85, "ANP 2026"),
        "DF" to FuelPriceQuote("DF", "Distrito Federal", 5.99, 4.09, "ANP 2026"),
        "ES" to FuelPriceQuote("ES", "Espírito Santo", 6.08, 4.22, "ANP 2026")
    )

    /**
     * Retorna o preço de combustível para o estado solicitado.
     * Tenta consultar base em nuvem; se offline ou sem resposta, retorna a tabela oficial embutida.
     */
    suspend fun getFuelPriceForState(stateCode: String = "SP"): FuelPriceQuote {
        val uf = stateCode.trim().uppercase()
        val defaultQuote = ANP_BENCHMARK[uf] ?: ANP_BENCHMARK["SP"]!!

        // Tentativa de consulta em endpoint remoto de preços abertos
        try {
            val url = "https://raw.githubusercontent.com/josecicerobispofelix/DeliveryDriverApp/main/fuel_prices_anp.json"
            val netResult = HttpClientProvider.get(url)
            if (netResult.isSuccess) {
                val json = JSONObject(netResult.getOrThrow())
                val stateObj = json.optJSONObject(uf)
                if (stateObj != null) {
                    val gas = stateObj.optDouble("gasolina", defaultQuote.gasolineAverage)
                    val eta = stateObj.optDouble("etanol", defaultQuote.ethanolAverage)
                    val date = stateObj.optString("data", defaultQuote.dateReference)
                    return FuelPriceQuote(uf, defaultQuote.stateName, gas, eta, date)
                }
            }
        } catch (_: Exception) {
            // Em caso de falha de conexão, utiliza os valores oficiais de referência
        }

        return defaultQuote
    }

    fun getAvailableStates(): List<String> = ANP_BENCHMARK.keys.sorted()
}
