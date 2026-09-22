package br.com.entregador.lucro.domain.model

/**
 * Representa as configurações operacionais e financeiras do entregador.
 *
 * @property vehicleType Tipo de veículo utilizado ("MOTO" ou "BIKE").
 * @property fuelPricePerLiter Preço do litro de combustível em R$ (ex: 5.80).
 * @property fuelConsumptionKmPerLiter Consumo médio do veículo em km/l (ex: 35.0).
 * @property maintenanceCostPerKm Custo estimado de manutenção preventiva por km (ex: 0.12).
 * @property restaurantWaitBufferMinutes Tempo extra de espera no restaurante em minutos (ex: 10 min).
 * @property targetMinPerKm Meta de lucro líquido mínimo por km rodado em R$ (ex: 2.00).
 * @property targetMinPerHour Meta de lucro líquido mínimo por hora trabalhada em R$ (ex: 25.00).
 * @property voiceAlertsEnabled Se os avisos de voz estão ativados.
 * @property autoRejectRedOffers Se a recusa automática de corridas com semáforo vermelho está ativada.
 * @property autoRejectDelaySeconds Tempo de espera em segundos antes de executar o clique de recusa automática (ex: 3s).
 * @property minGrossValueFloor Piso de valor bruto mínimo da corrida em R$ (ex: 7.00). 0.0 desativa o filtro.
 * @property maxDistanceKm Teto de distância máxima da rota em km (ex: 15.0). 0.0 desativa o filtro.
 * @property emptyReturnPercent Percentual de rota de volta vazia considerado no cálculo (ex: 20.0 para +20%).
 */
data class DeliverySettings(
    val vehicleType: String = VEHICLE_MOTO,
    val fuelPricePerLiter: Double = 5.80,
    val fuelConsumptionKmPerLiter: Double = 35.0,
    val maintenanceCostPerKm: Double = 0.12,
    val restaurantWaitBufferMinutes: Int = 10,
    val targetMinPerKm: Double = 2.00,
    val targetMinPerHour: Double = 25.00,
    val voiceAlertsEnabled: Boolean = true,
    val autoRejectRedOffers: Boolean = false,
    val autoRejectDelaySeconds: Int = 3,
    val minGrossValueFloor: Double = 0.0,
    val maxDistanceKm: Double = 0.0,
    val emptyReturnPercent: Double = 0.0,
    val riskAreasEnabled: Boolean = false,
    val riskAreasList: List<String> = emptyList(),
    val autoRejectRiskAreas: Boolean = true,
    val dailyRevenueGoal: Double = 200.0,
    val autoAcceptGreenOffers: Boolean = false,
    val autoAcceptDelaySeconds: Int = 1,
    val soundAlertsEnabled: Boolean = true
) {
    companion object {
        const val VEHICLE_MOTO = "MOTO"
        const val VEHICLE_BIKE = "BIKE"
    }

    val isBike: Boolean
        get() = vehicleType.equals(VEHICLE_BIKE, ignoreCase = true)

    val isMoto: Boolean
        get() = vehicleType.equals(VEHICLE_MOTO, ignoreCase = true)
}
