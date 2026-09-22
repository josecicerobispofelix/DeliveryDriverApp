package br.com.entregador.lucro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryOfferTest {

    @Test
    fun `delivery offer fields are populated correctly`() {
        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 18.50,
            totalDistanceKm = 5.2,
            estimatedTimeMinutes = 18
        )

        assertEquals("IFOOD", offer.platform)
        assertEquals(18.50, offer.grossValue, 0.001)
        assertEquals(5.2, offer.totalDistanceKm, 0.001)
        assertEquals(18, offer.estimatedTimeMinutes)
        assertTrue(offer.isSupportedPlatform)
    }

    @Test
    fun `supported platforms includes IFOOD, UBER and 99`() {
        val ifoodOffer = DeliveryOffer("IFOOD", 15.0, 4.0, 15)
        val uberOffer = DeliveryOffer("UBER", 20.0, 6.0, 22)
        val ninetyNineOffer = DeliveryOffer("99", 12.0, 3.5, 12)
        val unknownOffer = DeliveryOffer("LOGGI", 10.0, 5.0, 20)

        assertTrue(ifoodOffer.isSupportedPlatform)
        assertTrue(uberOffer.isSupportedPlatform)
        assertTrue(ninetyNineOffer.isSupportedPlatform)
        assertFalse(unknownOffer.isSupportedPlatform)
    }
}
