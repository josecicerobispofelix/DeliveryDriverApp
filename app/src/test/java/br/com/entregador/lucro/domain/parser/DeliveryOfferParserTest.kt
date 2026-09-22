package br.com.entregador.lucro.domain.parser

import br.com.entregador.lucro.domain.model.DeliveryOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeliveryOfferParserTest {

    @Test
    fun `extracts monetary value correctly with comma or dot`() {
        assertEquals(14.50, DeliveryOfferParser.extractGrossValue("Entrega por R$ 14,50") ?: 0.0, 0.001)
        assertEquals(22.80, DeliveryOfferParser.extractGrossValue("Valor: R$22.80 total") ?: 0.0, 0.001)
        assertEquals(8.00, DeliveryOfferParser.extractGrossValue("R$ 8,00") ?: 0.0, 0.001)
        assertNull(DeliveryOfferParser.extractGrossValue("Sem valor monetario"))
    }

    @Test
    fun `extracts and sums multiple distances for pickup and final delivery`() {
        val singleDistance = DeliveryOfferParser.extractDistancesKm("Rota total de 3,2 km")
        assertEquals(1, singleDistance.size)
        assertEquals(3.2, singleDistance.sum(), 0.001)

        val multipleDistancesText = "Deslocamento até a coleta: 1,5 km. Entrega final: 3.2 km."
        val distances = DeliveryOfferParser.extractDistancesKm(multipleDistancesText)
        assertEquals(2, distances.size)
        assertEquals(4.7, distances.sum(), 0.001) // 1.5 + 3.2 = 4.7
    }

    @Test
    fun `extracts estimated time in minutes correctly`() {
        assertEquals(18, DeliveryOfferParser.extractEstimatedTimeMinutes("Tempo estimado: 18 min"))
        assertEquals(25, DeliveryOfferParser.extractEstimatedTimeMinutes("25min de trajeto"))
        assertNull(DeliveryOfferParser.extractEstimatedTimeMinutes("Tempo desconhecido"))
    }

    @Test
    fun `parses complete delivery offer from list of screen texts and sums distances`() {
        val screenTexts = listOf(
            "iFood Entregador",
            "Nova oferta recebida!",
            "Restaurante Sabor da Vila",
            "R$ 14,50",
            "Coleta: 1,5 km",
            "Entrega: 3,2 km",
            "Tempo de percurso: 18 min"
        )

        val offer = DeliveryOfferParser.parseOffer(screenTexts, DeliveryOffer.PLATFORM_IFOOD)

        assertNotNull(offer)
        assertEquals(DeliveryOffer.PLATFORM_IFOOD, offer?.platform)
        assertEquals(14.50, offer?.grossValue ?: 0.0, 0.001)
        assertEquals(4.7, offer?.totalDistanceKm ?: 0.0, 0.001) // 1.5 + 3.2
        assertEquals(18, offer?.estimatedTimeMinutes)
    }

    @Test
    fun `returns null if mandatory fields are missing`() {
        val incompleteTexts = listOf("Apenas texto sem valor nem distância", "18 min")
        val offer = DeliveryOfferParser.parseOffer(incompleteTexts, "UBER")
        assertNull(offer)
    }

    @Test
    fun `extracts multi order count accurately from varied strings`() {
        assertEquals(2, DeliveryOfferParser.extractOrderCount(listOf("2 entregas", "R$ 25,00", "5 km")))
        assertEquals(2, DeliveryOfferParser.extractOrderCount(listOf("Rota dupla", "R$ 22,00")))
        assertEquals(2, DeliveryOfferParser.extractOrderCount(listOf("2 coletas e entregas")))
        assertEquals(3, DeliveryOfferParser.extractOrderCount(listOf("3 pedidos agrupados")))
        assertEquals(3, DeliveryOfferParser.extractOrderCount(listOf("Rota tripla encontrada")))
        assertEquals(1, DeliveryOfferParser.extractOrderCount(listOf("1 entrega normal", "R$ 10,00")))
        assertEquals(1, DeliveryOfferParser.extractOrderCount(listOf("Restaurante X", "R$ 15,00", "4 km")))
    }

    @Test
    fun `parses multi order offer with grossValuePerOrder calculated`() {
        val screenTexts = listOf(
            "iFood Entregador",
            "Rota dupla: 2 entregas",
            "R$ 26,00",
            "7,0 km",
            "25 min"
        )
        val offer = DeliveryOfferParser.parseOffer(screenTexts, DeliveryOffer.PLATFORM_IFOOD)
        assertNotNull(offer)
        assertEquals(2, offer?.orderCount)
        assertEquals(26.00, offer?.grossValue ?: 0.0, 0.001)
        assertEquals(13.00, offer?.grossValuePerOrder ?: 0.0, 0.001)
    }
}
