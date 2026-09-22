package br.com.entregador.lucro.domain.formatter

import br.com.entregador.lucro.domain.model.DeliveryCalculationResult
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import kotlin.math.roundToInt

/**
 * Utilitário responsável por gerar a frase de voz sintetizada curta e natural
 * para o entregador ouvir enquanto pilota ou dirige.
 */
object VoiceAlertFormatter {

    /**
     * Formata um valor monetário em texto fonético em português.
     * Ex: 12.0 -> "12 reais", 3.0 -> "3 reais", 2.50 -> "2 reais e 50 centavos".
     */
    fun formatCurrencyVoice(value: Double): String {
        val totalCents = (value * 100).roundToInt()
        val reais = totalCents / 100
        val centavos = totalCents % 100

        return when {
            reais == 0 && centavos > 0 -> "$centavos centavos"
            centavos == 0 -> {
                if (reais == 1) "1 real" else "$reais reais"
            }
            reais == 1 -> "1 real e $centavos centavos"
            else -> "$reais reais e $centavos centavos"
        }
    }

    /**
     * Monta a frase concisa para síntese de voz (TTS).
     * Exemplo normal: "Oferta verde para Pinheiros. Lucro de 12 reais, 3 reais por quilômetro."
     * Exemplo área de risco: "Atenção! Área de risco detectada em Chapadão. Corrida vermelha!"
     */
    fun buildSpeechText(result: DeliveryCalculationResult): String {
        if (result.isRiskArea) {
            val area = result.detectedRiskArea ?: "perigosa"
            return "Atenção! Área de risco detectada em $area. Corrida vermelha!"
        }

        val colorName = when (result.trafficLightStatus) {
            TrafficLightStatus.GREEN -> "verde"
            TrafficLightStatus.YELLOW -> "amarela"
            TrafficLightStatus.RED -> "vermelha"
        }

        val profitText = formatCurrencyVoice(result.netProfit)
        val rateKmText = formatCurrencyVoice(result.earningsPerKm)

        val destPart = if (!result.destinationNeighborhood.isNullOrBlank()) {
            " para ${result.destinationNeighborhood}"
        } else {
            ""
        }

        val orderPart = if (result.orderCount > 1) {
            "Rota com ${result.orderCount} entregas. "
        } else {
            ""
        }

        return "${orderPart}Oferta $colorName$destPart. Lucro de $profitText, $rateKmText por quilômetro."
    }
}
