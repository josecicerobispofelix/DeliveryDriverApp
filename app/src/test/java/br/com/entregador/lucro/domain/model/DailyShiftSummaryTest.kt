package br.com.entregador.lucro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DailyShiftSummaryTest {

    @Test
    fun `averages are calculated correctly when values are present`() {
        val summary = DailyShiftSummary(
            dateStr = "2026-09-21",
            greenCount = 5,
            yellowCount = 2,
            redCount = 3,
            totalOffersCount = 10,
            acceptedCount = 7,
            totalNetProfit = 140.0,
            totalDistanceKm = 35.0,
            totalTimeMinutes = 120 // 2 horas
        )

        assertEquals(4.0, summary.averageEarningsPerKm, 0.001) // 140 / 35 = 4.0 R$/km
        assertEquals(70.0, summary.averageEarningsPerHour, 0.001) // 140 / 2h = 70.0 R$/h
    }

    @Test
    fun `averages handle division by zero safely`() {
        val emptySummary = DailyShiftSummary(dateStr = "2026-09-21")

        assertEquals(0.0, emptySummary.averageEarningsPerKm, 0.001)
        assertEquals(0.0, emptySummary.averageEarningsPerHour, 0.001)
    }

    @Test
    fun `goal progress percent calculates correctly and caps at 100`() {
        val summary = DailyShiftSummary(dateStr = "2026-09-21", totalNetProfit = 100.0)

        // 100 de 200 = 50%
        assertEquals(50, summary.getGoalProgressPercent(200.0))
        assertEquals(100.0, summary.getRemainingToGoal(200.0), 0.001)

        // 100 de 100 = 100%
        assertEquals(100, summary.getGoalProgressPercent(100.0))
        assertEquals(0.0, summary.getRemainingToGoal(100.0), 0.001)

        // 100 de 80 = 100% (limitado a 100%)
        assertEquals(100, summary.getGoalProgressPercent(80.0))
        assertEquals(0.0, summary.getRemainingToGoal(80.0), 0.001)

        // Meta zero ou negativa
        assertEquals(0, summary.getGoalProgressPercent(0.0))
        assertEquals(0.0, summary.getRemainingToGoal(0.0), 0.001)
    }
}

