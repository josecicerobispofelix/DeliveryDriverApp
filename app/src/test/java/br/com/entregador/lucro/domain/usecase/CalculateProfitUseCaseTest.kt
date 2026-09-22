package br.com.entregador.lucro.domain.usecase

import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.DeliverySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateProfitUseCaseTest {

    private lateinit var useCase: CalculateProfitUseCase

    @Before
    fun setUp() {
        useCase = CalculateProfitUseCase()
    }

    @Test
    fun `moto delivery calculates fuel and maintenance accurately and approves profitable offer`() {
        val settings = DeliverySettings(
            vehicleType = DeliverySettings.VEHICLE_MOTO,
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 10,
            targetMinPerKm = 2.00,
            targetMinPerHour = 25.00
        )

        val offer = DeliveryOffer(
            platform = DeliveryOffer.PLATFORM_IFOOD,
            grossValue = 20.00,
            totalDistanceKm = 5.0,
            estimatedTimeMinutes = 20
        )

        val result = useCase(offer, settings)

        val expectedFuelCost = (5.0 / 35.0) * 5.80 // ~0.82857
        val expectedMaintenance = 5.0 * 0.12 // 0.60
        val expectedTotalCost = expectedFuelCost + expectedMaintenance // ~1.42857
        val expectedNetProfit = 20.00 - expectedTotalCost // ~18.57143
        val expectedRatePerKm = expectedNetProfit / 5.0 // ~3.714
        val expectedTotalMinutes = 20 + 10 // 30 min
        val expectedRatePerHour = (expectedNetProfit / 30.0) * 60.0 // ~37.14

        assertEquals(expectedFuelCost, result.fuelCost, 0.001)
        assertEquals(expectedMaintenance, result.maintenanceCost, 0.001)
        assertEquals(expectedTotalCost, result.totalOperatingCost, 0.001)
        assertEquals(expectedNetProfit, result.netProfit, 0.001)
        assertEquals(expectedRatePerKm, result.netRatePerKm, 0.001)
        assertEquals(expectedTotalMinutes, result.effectiveTotalMinutes)
        assertEquals(expectedRatePerHour, result.netRatePerHour, 0.001)
        assertTrue(result.isTargetKmMet)
        assertTrue(result.isTargetHourMet)
        assertTrue(result.isProfitable)
    }

    @Test
    fun `bike delivery has zero fuel cost and only maintenance cost`() {
        val settings = DeliverySettings(
            vehicleType = DeliverySettings.VEHICLE_BIKE,
            maintenanceCostPerKm = 0.10,
            restaurantWaitBufferMinutes = 10,
            targetMinPerKm = 2.00,
            targetMinPerHour = 20.00
        )

        val offer = DeliveryOffer(
            platform = DeliveryOffer.PLATFORM_UBER,
            grossValue = 15.00,
            totalDistanceKm = 3.0,
            estimatedTimeMinutes = 15
        )

        val result = useCase(offer, settings)

        assertEquals(0.0, result.fuelCost, 0.0001)
        assertEquals(0.30, result.maintenanceCost, 0.001)
        assertEquals(0.30, result.totalOperatingCost, 0.001)
        assertEquals(14.70, result.netProfit, 0.001)
        assertEquals(4.90, result.netRatePerKm, 0.001)
        assertEquals(25, result.effectiveTotalMinutes)
        assertEquals(35.28, result.netRatePerHour, 0.01)
        assertTrue(result.isTargetKmMet)
        assertTrue(result.isTargetHourMet)
        assertTrue(result.isProfitable)
    }

    @Test
    fun `unprofitable offer is rejected when below per km and hourly targets`() {
        val settings = DeliverySettings(
            vehicleType = DeliverySettings.VEHICLE_MOTO,
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 10,
            targetMinPerKm = 2.00,
            targetMinPerHour = 25.00
        )

        val offer = DeliveryOffer(
            platform = DeliveryOffer.PLATFORM_99,
            grossValue = 7.00,
            totalDistanceKm = 8.0,
            estimatedTimeMinutes = 35
        )

        val result = useCase(offer, settings)

        assertFalse(result.isTargetKmMet)
        assertFalse(result.isTargetHourMet)
        assertFalse(result.isProfitable)
    }

    @Test
    fun `zero distance and zero time edge cases do not throw and handle gracefully`() {
        val settings = DeliverySettings()
        val offer = DeliveryOffer(
            platform = DeliveryOffer.PLATFORM_IFOOD,
            grossValue = 0.0,
            totalDistanceKm = 0.0,
            estimatedTimeMinutes = 0
        )

        val result = useCase(offer, settings)

        assertEquals(0.0, result.fuelCost, 0.0001)
        assertEquals(0.0, result.maintenanceCost, 0.0001)
        assertEquals(0.0, result.netProfit, 0.0001)
        assertEquals(0.0, result.netRatePerKm, 0.0001)
        assertEquals(10, result.effectiveTotalMinutes)
        assertEquals(0.0, result.netRatePerHour, 0.0001)
        assertFalse(result.isProfitable)
    }
}
