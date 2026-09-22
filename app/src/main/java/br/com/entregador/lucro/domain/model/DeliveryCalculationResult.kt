package br.com.entregador.lucro.domain.model

/**
 * Resultado do cálculo operacional e financeiro executado pelo [br.com.entregador.lucro.domain.calculator.DeliveryCalculator].
 *
 * @property fuelCostPerKm Custo de combustível por km em R$/km (0.0 para BIKE).
 * @property totalRouteCost Custo operacional total da rota em R$.
 * @property netProfit Lucro líquido final (grossValue - CustoTotalRota) em R$.
 * @property earningsPerKm Ganho líquido real por km rodado em R$/km.
 * @property totalTimeHours Tempo total estimado da rota em horas (incluindo espera no restaurante).
 * @property earningsPerHour Ganho líquido real projetado por hora em R$/h.
 * @property trafficLightStatus Classificação no semáforo (GREEN, YELLOW, RED).
 */
data class DeliveryCalculationResult(
    val fuelCostPerKm: Double,
    val totalRouteCost: Double,
    val netProfit: Double,
    val earningsPerKm: Double,
    val totalTimeHours: Double,
    val earningsPerHour: Double,
    val trafficLightStatus: TrafficLightStatus,
    val isRiskArea: Boolean = false,
    val detectedRiskArea: String? = null,
    val destinationNeighborhood: String? = null,
    val orderCount: Int = 1,
    val netProfitPerOrder: Double = netProfit / orderCount.coerceAtLeast(1),
    val grossValuePerOrder: Double = 0.0
)
