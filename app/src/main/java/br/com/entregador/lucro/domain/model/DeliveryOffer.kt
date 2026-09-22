package br.com.entregador.lucro.domain.model

/**
 * Representa uma oferta de corrida/entrega recebida das plataformas parceiras.
 *
 * @property platform Plataforma de origem da oferta ("IFOOD", "UBER" ou "99").
 * @property grossValue Valor bruto pago na oferta em R$.
 * @property totalDistanceKm Distância somada em km (deslocamento até a coleta + entrega final).
 * @property estimatedTimeMinutes Tempo estimado de rota em minutos.
 */
data class DeliveryOffer(
    val platform: String,
    val grossValue: Double,
    val totalDistanceKm: Double,
    val estimatedTimeMinutes: Int,
    val destinationAddress: String? = null,
    val destinationNeighborhood: String? = null,
    val rawTexts: List<String> = emptyList(),
    val orderCount: Int = 1
) {
    val grossValuePerOrder: Double
        get() = grossValue / orderCount.coerceAtLeast(1)
    companion object {
        const val PLATFORM_IFOOD = "IFOOD"
        const val PLATFORM_UBER = "UBER"
        const val PLATFORM_99 = "99"

        val SUPPORTED_PLATFORMS = listOf(PLATFORM_IFOOD, PLATFORM_UBER, PLATFORM_99)
    }

    val isSupportedPlatform: Boolean
        get() = platform.uppercase() in SUPPORTED_PLATFORMS
}
