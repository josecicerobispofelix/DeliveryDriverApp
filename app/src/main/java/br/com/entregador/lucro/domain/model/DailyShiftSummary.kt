package br.com.entregador.lucro.domain.model

/**
 * Resumo consolidado dos ganhos, quilometragem e semáforos de um turno diário de trabalho.
 *
 * @property dateStr Data de referência formatada (ex: "2026-09-21").
 * @property greenCount Quantidade de ofertas verdes recomendadas no dia.
 * @property yellowCount Quantidade de ofertas amarelas de atenção no dia.
 * @property redCount Quantidade de ofertas vermelhas (recusadas manualmente ou automaticamente).
 * @property totalOffersCount Total de ofertas analisadas pelo sistema no dia.
 * @property acceptedCount Total de corridas aceitas/computadas no faturamento líquido.
 * @property totalNetProfit Faturamento líquido total acumulado nas corridas aceitas.
 * @property totalDistanceKm Quilometragem total acumulada nas corridas aceitas.
 * @property totalTimeMinutes Tempo total acumulado em minutos nas corridas aceitas.
 */
data class DailyShiftSummary(
    val dateStr: String,
    val greenCount: Int = 0,
    val yellowCount: Int = 0,
    val redCount: Int = 0,
    val totalOffersCount: Int = 0,
    val acceptedCount: Int = 0,
    val totalNetProfit: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val totalTimeMinutes: Int = 0
) {
    /**
     * Média de lucro líquido obtido por quilômetro rodado no dia (R$/km).
     */
    val averageEarningsPerKm: Double
        get() = if (totalDistanceKm > 0.0) totalNetProfit / totalDistanceKm else 0.0

    /**
     * Média de lucro líquido horário obtido no dia (R$/h).
     */
    val averageEarningsPerHour: Double
        get() = if (totalTimeMinutes > 0) totalNetProfit / (totalTimeMinutes / 60.0) else 0.0

    /**
     * Percentual de progresso em relação à meta diária (0% a 100%).
     */
    fun getGoalProgressPercent(goal: Double): Int {
        if (goal <= 0.0) return 0
        return ((totalNetProfit / goal) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Valor monetário que falta para atingir a meta diária em R$.
     */
    fun getRemainingToGoal(goal: Double): Double {
        return (goal - totalNetProfit).coerceAtLeast(0.0)
    }
}
