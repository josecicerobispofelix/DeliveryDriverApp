package br.com.entregador.lucro.domain.calculator

import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.DeliverySettings
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryCalculatorTest {

    @Test
    fun `moto delivery with high profitability classifies as GREEN`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 10,
            targetMinPerKm = 2.00,
            targetMinPerHour = 25.00
        )

        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 30.00,
            totalDistanceKm = 10.0,
            estimatedTimeMinutes = 20
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        val expectedFuelPerKm = 5.80 / 35.0 // ~0.165714
        val expectedTotalCost = 10.0 * (expectedFuelPerKm + 0.12) // ~2.85714
        val expectedNetProfit = 30.00 - expectedTotalCost // ~27.1428
        val expectedEarningsPerKm = expectedNetProfit / 10.0 // ~2.7142
        val expectedTotalHours = (20 + 10) / 60.0 // 0.5 h
        val expectedEarningsPerHour = expectedNetProfit / 0.5 // ~54.285

        assertEquals(expectedFuelPerKm, result.fuelCostPerKm, 0.0001)
        assertEquals(expectedTotalCost, result.totalRouteCost, 0.001)
        assertEquals(expectedNetProfit, result.netProfit, 0.001)
        assertEquals(expectedEarningsPerKm, result.earningsPerKm, 0.001)
        assertEquals(expectedTotalHours, result.totalTimeHours, 0.0001)
        assertEquals(expectedEarningsPerHour, result.earningsPerHour, 0.001)
        assertEquals(TrafficLightStatus.GREEN, result.trafficLightStatus)
    }

    @Test
    fun `bike delivery sets fuel cost per km to zero`() {
        val settings = DeliverySettings(
            vehicleType = "BIKE",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.10,
            restaurantWaitBufferMinutes = 10,
            targetMinPerKm = 2.00,
            targetMinPerHour = 20.00
        )

        val offer = DeliveryOffer(
            platform = "UBER",
            grossValue = 16.00,
            totalDistanceKm = 4.0,
            estimatedTimeMinutes = 20
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(0.0, result.fuelCostPerKm, 0.0001)
        assertEquals(0.40, result.totalRouteCost, 0.001)
        assertEquals(15.60, result.netProfit, 0.001)
        assertEquals(3.90, result.earningsPerKm, 0.001)
        assertEquals(0.5, result.totalTimeHours, 0.0001)
        assertEquals(31.20, result.earningsPerHour, 0.001)
        assertEquals(TrafficLightStatus.GREEN, result.trafficLightStatus)
    }

    @Test
    fun `offer meeting only per km target classifies as YELLOW`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 30, // Long wait time lowers hourly rate
            targetMinPerKm = 2.00,
            targetMinPerHour = 30.00
        )

        val offer = DeliveryOffer(
            platform = "99",
            grossValue = 15.00,
            totalDistanceKm = 4.0,
            estimatedTimeMinutes = 30 // Total time = 60 min (1 hour)
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        // Route cost = 4 * ( (5.80/35) + 0.12 ) = 4 * (0.1657 + 0.12) = 1.1428
        // Net profit = 15.0 - 1.1428 = 13.857
        // Earnings/km = 13.857 / 4.0 = 3.46 (>= 2.00 -> OK)
        // Earnings/hour = 13.857 / 1.0 = 13.857 (< 30.00 -> FAILED)
        assertEquals(TrafficLightStatus.YELLOW, result.trafficLightStatus)
    }

    @Test
    fun `offer meeting only hourly target classifies as YELLOW`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 0,
            targetMinPerKm = 3.50, // High per-km target
            targetMinPerHour = 25.00
        )

        val offer = DeliveryOffer(
            platform = "UBER",
            grossValue = 18.00,
            totalDistanceKm = 8.0,
            estimatedTimeMinutes = 15 // Very fast route: 0.25 hours
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        // Route cost = 8 * 0.2857 = 2.2857
        // Net profit = 15.714
        // Earnings/km = 15.714 / 8.0 = 1.96 (< 3.50 -> FAILED)
        // Earnings/hour = 15.714 / 0.25 = 62.85 (>= 25.00 -> OK)
        assertEquals(TrafficLightStatus.YELLOW, result.trafficLightStatus)
    }

    @Test
    fun `offer below both targets classifies as RED`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 15,
            targetMinPerKm = 2.00,
            targetMinPerHour = 25.00
        )

        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 7.00,
            totalDistanceKm = 8.0,
            estimatedTimeMinutes = 35
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
    }

    @Test
    fun `division by zero edge cases are handled safely`() {
        val settings = DeliverySettings(
            fuelConsumptionKmPerLiter = 0.0,
            restaurantWaitBufferMinutes = 0
        )

        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 10.0,
            totalDistanceKm = 0.0,
            estimatedTimeMinutes = 0
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(0.0, result.fuelCostPerKm, 0.0001)
        assertEquals(0.0, result.earningsPerKm, 0.0001)
        assertEquals(0.0, result.earningsPerHour, 0.0001)
        assertEquals(0.0, result.totalTimeHours, 0.0001)
        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
    }

    @Test
    fun `offer with gross value below minGrossValueFloor classifies as RED even if rates meet targets`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 5,
            targetMinPerKm = 2.00,
            targetMinPerHour = 20.00,
            minGrossValueFloor = 8.00 // Piso de R$ 8,00
        )

        // Oferta de R$ 6,00 por 1 km em 5 minutos:
        // Custo = 1 * (0.1657 + 0.12) = 0.2857 -> Lucro = 5.714
        // Ganho/km = 5.714 (>= 2.00)
        // Tempo = (5 + 5)/60 = 0.166 h -> Ganho/h = 34.28 (>= 20.00)
        // Sem o piso, seria GREEN. Com o piso de R$ 8,00, DEVE ser RED!
        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 6.00,
            totalDistanceKm = 1.0,
            estimatedTimeMinutes = 5
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
    }

    @Test
    fun `offer exceeding maxDistanceKm classifies as RED even if pay is high`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 5,
            targetMinPerKm = 2.00,
            targetMinPerHour = 25.00,
            maxDistanceKm = 12.0 // Teto máximo de 12 km
        )

        // Oferta de R$ 60,00 por 15 km em 25 minutos:
        // Muito lucrativa, mas excede os 12 km de teto do entregador
        val offer = DeliveryOffer(
            platform = "UBER",
            grossValue = 60.00,
            totalDistanceKm = 15.0,
            estimatedTimeMinutes = 25
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
    }

    @Test
    fun `empty return percent increases route cost and reduces net profit and rates`() {
        val baseSettings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.00,
            fuelConsumptionKmPerLiter = 50.0, // Custo comb: 0.10/km
            maintenanceCostPerKm = 0.10,      // Custo manut: 0.10/km -> Total 0.20/km
            restaurantWaitBufferMinutes = 0,
            emptyReturnPercent = 0.0
        )

        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 20.00,
            totalDistanceKm = 10.0,
            estimatedTimeMinutes = 30
        )

        val baseResult = DeliveryCalculator.calculate(offer, baseSettings)
        // Custo base: 10 * 0.20 = 2.00 -> Lucro líquido: 18.00
        assertEquals(2.00, baseResult.totalRouteCost, 0.001)
        assertEquals(18.00, baseResult.netProfit, 0.001)
        assertEquals(1.80, baseResult.earningsPerKm, 0.001)

        // Agora com 50% de retorno vazio (+5 km = 15 km efetivos, +15 min = 45 min efetivos)
        val returnSettings = baseSettings.copy(emptyReturnPercent = 50.0)
        val returnResult = DeliveryCalculator.calculate(offer, returnSettings)

        // Custo com retorno: 15.0 * 0.20 = 3.00
        assertEquals(3.00, returnResult.totalRouteCost, 0.001)
        // Lucro com retorno: 20.00 - 3.00 = 17.00
        assertEquals(17.00, returnResult.netProfit, 0.001)
        // Ganho por km: 17.00 / 15.0 = 1.1333
        assertEquals(17.00 / 15.0, returnResult.earningsPerKm, 0.001)
        // Tempo: 30 min * 1.5 = 45 min = 0.75h -> Ganho por hora: 17.00 / 0.75 = 22.666
        assertEquals(0.75, returnResult.totalTimeHours, 0.001)
        assertEquals(17.00 / 0.75, returnResult.earningsPerHour, 0.001)
    }

    @Test
    fun `offer to risk area classifies as RED and marks isRiskArea when protection enabled`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 0,
            targetMinPerKm = 2.00,
            targetMinPerHour = 20.00,
            riskAreasEnabled = true,
            riskAreasList = listOf("Chapadão", "Pedreira")
        )

        // Oferta ultra lucrativa: R$ 50 para 5km em 10 min
        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 50.00,
            totalDistanceKm = 5.0,
            estimatedTimeMinutes = 10,
            destinationAddress = "Estrada do Chapadão, 1500",
            destinationNeighborhood = "Chapadão"
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        // Deve ser rebaixado para RED mesmo com lucro extraordinário
        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
        org.junit.Assert.assertTrue(result.isRiskArea)
        assertEquals("Chapadão", result.detectedRiskArea)
    }

    @Test
    fun `offer to risk area remains GREEN if risk protection is disabled`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 0,
            targetMinPerKm = 2.00,
            targetMinPerHour = 20.00,
            riskAreasEnabled = false, // Desativado
            riskAreasList = listOf("Chapadão")
        )

        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 50.00,
            totalDistanceKm = 5.0,
            estimatedTimeMinutes = 10,
            destinationNeighborhood = "Chapadão"
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(TrafficLightStatus.GREEN, result.trafficLightStatus)
        org.junit.Assert.assertFalse(result.isRiskArea)
    }

    @Test
    fun `risk area matching is accent and case insensitive`() {
        val settings = DeliverySettings(
            riskAreasEnabled = true,
            riskAreasList = listOf("Morro do Alemão") // com acento e maiúsculas
        )

        // Texto em minúsculas e sem acento
        val offer = DeliveryOffer(
            platform = "UBER",
            grossValue = 30.00,
            totalDistanceKm = 5.0,
            estimatedTimeMinutes = 15,
            destinationAddress = "Rua Central, morro do alemao"
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
        org.junit.Assert.assertTrue(result.isRiskArea)
        assertEquals("Morro do Alemão", result.detectedRiskArea)
    }

    @Test
    fun `risk area matching detects risk in rawTexts if address parser missed it`() {
        val settings = DeliverySettings(
            riskAreasEnabled = true,
            riskAreasList = listOf("Pedreira")
        )

        val offer = DeliveryOffer(
            platform = "99",
            grossValue = 25.00,
            totalDistanceKm = 4.0,
            estimatedTimeMinutes = 15,
            destinationNeighborhood = null,
            destinationAddress = null,
            rawTexts = listOf("Nova Entrega", "R$ 25,00", "Entrega: Comunidade da Pedreira", "4.0 km")
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
        org.junit.Assert.assertTrue(result.isRiskArea)
        assertEquals("Pedreira", result.detectedRiskArea)
    }

    @Test
    fun `multi order scales restaurant wait buffer and computes per order profit`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            fuelPricePerLiter = 5.80,
            fuelConsumptionKmPerLiter = 35.0,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 10,
            targetMinPerKm = 2.00,
            targetMinPerHour = 25.00
        )

        // 2 entregas: tempo no restaurante passa de 10 min para 20 min (10 * 2)
        // Tempo viagem = 20 min. Total tempo = 20 + 20 = 40 min (40/60 h = 0.6667 h)
        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 30.00,
            totalDistanceKm = 10.0,
            estimatedTimeMinutes = 20,
            orderCount = 2
        )

        val result = DeliveryCalculator.calculate(offer, settings)

        assertEquals(2, result.orderCount)
        assertEquals(15.00, result.grossValuePerOrder, 0.001) // 30 / 2
        assertEquals(40.0 / 60.0, result.totalTimeHours, 0.0001)
        assertEquals(result.netProfit / 2.0, result.netProfitPerOrder, 0.001)
    }

    @Test
    fun `rainMode applies bonus to minGrossValueFloor and rejects offer below adjusted floor`() {
        val settings = DeliverySettings(
            vehicleType = "MOTO",
            targetMinPerKm = 1.50,
            targetMinPerHour = 20.00,
            minGrossValueFloor = 10.00,
            rainModeEnabled = true,
            rainFloorBonus = 3.00
        )

        // Oferta de R$ 11.50: passaria no piso normal de 10.00, mas com chuva o piso sobe para 13.00
        val offer = DeliveryOffer(
            platform = "IFOOD",
            grossValue = 11.50,
            totalDistanceKm = 3.0,
            estimatedTimeMinutes = 10
        )

        val result = DeliveryCalculator.calculate(
            offer = offer,
            settings = settings,
            isRaining = true
        )

        org.junit.Assert.assertTrue(result.isRaining)
        assertEquals(3.00, result.appliedFloorBonus, 0.001)
        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
    }

    @Test
    fun `bike steep incline classifies route as RED and adds elevation penalty`() {
        val settings = DeliverySettings(
            vehicleType = "BIKE",
            targetMinPerKm = 6.00,
            targetMinPerHour = 38.00,
            maintenanceCostPerKm = 0.12,
            restaurantWaitBufferMinutes = 10,
            bikeElevationAlertEnabled = true
        )

        // Lucro líquido: 18.00 - (3.0 * 0.12) = 17.64
        // Sem morro: 25 min (0.4167h) -> R$ 42.33/h (passaria nos 38.00/h)
        // Com morro (+6 min): 31 min (0.5167h) -> R$ 34.14/h (reprovado < 38.00)
        // R$/km: 17.64 / 3.0 = 5.88 (reprovado < 6.00) -> Classifica como RED
        val offer = DeliveryOffer(
            platform = "UBER",
            grossValue = 18.00,
            totalDistanceKm = 3.0,
            estimatedTimeMinutes = 15
        )

        val result = DeliveryCalculator.calculate(
            offer = offer,
            settings = settings,
            isRaining = false,
            isSteepIncline = true,
            elevationGainMeters = 80
        )

        org.junit.Assert.assertTrue(result.isSteepIncline)
        assertEquals(80, result.elevationGainMeters)
        assertEquals(TrafficLightStatus.RED, result.trafficLightStatus)
    }

    @Test
    fun `analyzeElevationNominal identifies known steep Brazilian topographies`() {
        val analysisPerdizes = br.com.entregador.lucro.network.ElevationClient.analyzeElevationNominal("Rua Cardoso de Almeida, Perdizes, São Paulo")
        org.junit.Assert.assertTrue(analysisPerdizes.isSteepIncline)
        org.junit.Assert.assertTrue(analysisPerdizes.elevationGainMeters >= 45)

        val analysisSantaTeresa = br.com.entregador.lucro.network.ElevationClient.analyzeElevationNominal("Largo dos Guimarães, Santa Teresa, Rio de Janeiro")
        org.junit.Assert.assertTrue(analysisSantaTeresa.isSteepIncline)

        val analysisFlat = br.com.entregador.lucro.network.ElevationClient.analyzeElevationNominal("Avenida Brigadeiro Faria Lima, Itaim Bibi")
        org.junit.Assert.assertFalse(analysisFlat.isSteepIncline)
    }

    @Test
    fun `fuel price client returns valid benchmark for Brazilian states`() = kotlinx.coroutines.runBlocking {
        val spQuote = br.com.entregador.lucro.network.FuelPriceClient.getFuelPriceForState("SP")
        org.junit.Assert.assertTrue(spQuote.gasolineAverage in 4.5..8.5)

        val rjQuote = br.com.entregador.lucro.network.FuelPriceClient.getFuelPriceForState("RJ")
        org.junit.Assert.assertTrue(rjQuote.gasolineAverage in 4.5..8.5)
    }
}
