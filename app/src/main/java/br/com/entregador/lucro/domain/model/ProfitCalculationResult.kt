package br.com.entregador.lucro.domain.model

/**
 * Resultado detalhado da análise de lucro líquido de uma oferta de entrega.
 *
 * @property offer Oferta avaliada.
 * @property fuelCost Custo estimado de combustível gasto na rota em R$.
 * @property maintenanceCost Custo estimado de desgaste/manutenção na rota em R$.
 * @property totalOperatingCost Custo operacional total da rota em R$.
 * @property netProfit Lucro líquido final (valor bruto - custo operacional total) em R$.
 * @property netRatePerKm Lucro líquido gerado por km rodado em R$/km.
 * @property effectiveTotalMinutes Tempo total estimado da operação (tempo de rota + espera de restaurante).
 * @property netRatePerHour Projeção de lucro líquido por hora trabalhada em R$/h.
 * @property isTargetKmMet Indica se a meta mínima de lucro por km foi atingida.
 * @property isTargetHourMet Indica se a meta mínima de lucro por hora foi atingida.
 * @property isProfitable Indica se a oferta cumpre todos os critérios mínimos do entregador.
 */
data class ProfitCalculationResult(
    val offer: DeliveryOffer,
    val fuelCost: Double,
    val maintenanceCost: Double,
    val totalOperatingCost: Double,
    val netProfit: Double,
    val netRatePerKm: Double,
    val effectiveTotalMinutes: Int,
    val netRatePerHour: Double,
    val isTargetKmMet: Boolean,
    val isTargetHourMet: Boolean,
    val isProfitable: Boolean
)
