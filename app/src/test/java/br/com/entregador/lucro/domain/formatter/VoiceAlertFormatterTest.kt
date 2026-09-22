package br.com.entregador.lucro.domain.formatter

import br.com.entregador.lucro.domain.model.DeliveryCalculationResult
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAlertFormatterTest {

    @Test
    fun `formats exact values into natural portuguese text`() {
        assertEquals("12 reais", VoiceAlertFormatter.formatCurrencyVoice(12.0))
        assertEquals("3 reais", VoiceAlertFormatter.formatCurrencyVoice(3.0))
        assertEquals("1 real", VoiceAlertFormatter.formatCurrencyVoice(1.0))
        assertEquals("2 reais e 50 centavos", VoiceAlertFormatter.formatCurrencyVoice(2.50))
    }

    @Test
    fun `builds concise speech text matching requested prompt format`() {
        val resultGreen = DeliveryCalculationResult(
            fuelCostPerKm = 0.16,
            totalRouteCost = 2.0,
            netProfit = 12.0,
            earningsPerKm = 3.0,
            totalTimeHours = 0.5,
            earningsPerHour = 24.0,
            trafficLightStatus = TrafficLightStatus.GREEN
        )

        val speechGreen = VoiceAlertFormatter.buildSpeechText(resultGreen)
        assertEquals("Oferta verde. Lucro de 12 reais, 3 reais por quilômetro.", speechGreen)

        val resultYellow = resultGreen.copy(trafficLightStatus = TrafficLightStatus.YELLOW)
        val speechYellow = VoiceAlertFormatter.buildSpeechText(resultYellow)
        assertEquals("Oferta amarela. Lucro de 12 reais, 3 reais por quilômetro.", speechYellow)

        val resultRed = resultGreen.copy(trafficLightStatus = TrafficLightStatus.RED)
        val speechRed = VoiceAlertFormatter.buildSpeechText(resultRed)
        assertEquals("Oferta vermelha. Lucro de 12 reais, 3 reais por quilômetro.", speechRed)
    }
}
