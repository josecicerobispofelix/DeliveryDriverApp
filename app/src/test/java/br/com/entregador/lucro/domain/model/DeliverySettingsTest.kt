package br.com.entregador.lucro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliverySettingsTest {

    @Test
    fun `default values match specified requirements`() {
        val settings = DeliverySettings()

        assertEquals("MOTO", settings.vehicleType)
        assertEquals(5.80, settings.fuelPricePerLiter, 0.001)
        assertEquals(35.0, settings.fuelConsumptionKmPerLiter, 0.001)
        assertEquals(0.12, settings.maintenanceCostPerKm, 0.001)
        assertEquals(10, settings.restaurantWaitBufferMinutes)
        assertEquals(2.00, settings.targetMinPerKm, 0.001)
        assertEquals(25.00, settings.targetMinPerHour, 0.001)
        assertTrue(settings.voiceAlertsEnabled)
        assertFalse(settings.autoRejectRedOffers)
        assertEquals(3, settings.autoRejectDelaySeconds)
        assertEquals(200.0, settings.dailyRevenueGoal, 0.001)
        assertFalse(settings.autoAcceptGreenOffers)
        assertEquals(1, settings.autoAcceptDelaySeconds)
        assertTrue(settings.isMoto)
        assertFalse(settings.isBike)
    }

    @Test
    fun `bike vehicle type correctly identifies as bike and not moto`() {
        val settings = DeliverySettings(vehicleType = DeliverySettings.VEHICLE_BIKE)

        assertTrue(settings.isBike)
        assertFalse(settings.isMoto)
    }

    @Test
    fun `case-insensitive vehicle type comparison works`() {
        val settings = DeliverySettings(vehicleType = "bike")

        assertTrue(settings.isBike)
    }
}
