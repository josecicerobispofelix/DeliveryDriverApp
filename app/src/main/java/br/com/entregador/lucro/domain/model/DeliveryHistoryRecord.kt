package br.com.entregador.lucro.domain.model

/**
 * Representa o registo histórico de uma oferta de entrega capturada pelo KmCerto.
 *
 * @property id Identificador único do registo no banco local.
 * @property timestamp Carimbo de data/hora em milissegundos.
 * @property dateStr Data da oferta formatada (ex: "2026-09-21").
 * @property timeStr Hora da oferta formatada (ex: "14:35").
 * @property platform Plataforma de origem ("IFOOD", "UBER" ou "99").
 * @property grossValue Valor bruto oferecido pela plataforma em R$.
 * @property netProfit Lucro líquido apurado após descontar custos de combustível e manutenção.
 * @property totalDistanceKm Distância total da corrida em quilômetros.
 * @property estimatedTimeMinutes Tempo estimado de rota em minutos.
 * @property trafficLightStatus Status do semáforo atribuído (GREEN, YELLOW ou RED).
 * @property wasAutoRejected Se a oferta foi recusada automaticamente pela funcionalidade anti-prejuízo.
 * @property accepted Se a oferta foi aceita pelo entregador (para cômputo no faturamento do turno).
 */
data class DeliveryHistoryRecord(
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val dateStr: String,
    val timeStr: String,
    val platform: String,
    val grossValue: Double,
    val netProfit: Double,
    val totalDistanceKm: Double,
    val estimatedTimeMinutes: Int,
    val trafficLightStatus: TrafficLightStatus,
    val wasAutoRejected: Boolean = false,
    val accepted: Boolean = true
)
