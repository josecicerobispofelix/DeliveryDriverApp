package br.com.entregador.lucro.domain.calculator

import br.com.entregador.lucro.domain.model.DeliveryCalculationResult
import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.DeliverySettings
import br.com.entregador.lucro.domain.model.TrafficLightStatus

/**
 * Objeto utilitário responsável por processar o cálculo financeiro e operacional
 * de ofertas de corrida em tempo real para os entregadores de aplicativos.
 */
object DeliveryCalculator {

    /**
     * Calcula o custo, lucro líquido, ganhos por km/hora e o status no semáforo
     * de acordo com a oferta recebida e as configurações do entregador.
     *
     * @param offer Dados da oferta recebida (plataforma, valor bruto, km total, tempo estimado).
     * @param settings Configurações financeiras e operacionais do entregador.
     * @return [DeliveryCalculationResult] com os resultados do cálculo.
     */
    fun calculate(
        offer: DeliveryOffer,
        settings: DeliverySettings,
        isRaining: Boolean = false,
        isSteepIncline: Boolean = false,
        elevationGainMeters: Int = 0
    ): DeliveryCalculationResult {
        // Se vehicleType for "MOTO": CustoCombustivelPorKm = fuelPricePerLiter / fuelConsumptionKmPerLiter.
        // Se for "BIKE", CustoCombustivelPorKm = 0.0.
        val custoCombustivelPorKm = if (settings.vehicleType.equals(DeliverySettings.VEHICLE_BIKE, ignoreCase = true)) {
            0.0
        } else {
            if (settings.fuelConsumptionKmPerLiter > 0.0) {
                settings.fuelPricePerLiter / settings.fuelConsumptionKmPerLiter
            } else {
                0.0
            }
        }

        // Fator de Retorno Vazio (Filtro Anti-Prejuízo)
        val returnMultiplier = 1.0 + (settings.emptyReturnPercent.coerceAtLeast(0.0) / 100.0)
        val effectiveDistanceKm = offer.totalDistanceKm * returnMultiplier

        // CustoTotalRota = effectiveDistanceKm * (CustoCombustivelPorKm + maintenanceCostPerKm)
        val custoTotalRota = effectiveDistanceKm * (custoCombustivelPorKm + settings.maintenanceCostPerKm)

        // LucroLiquido = grossValue - CustoTotalRota
        val lucroLiquido = offer.grossValue - custoTotalRota

        // GanhoPorKm = LucroLiquido / effectiveDistanceKm (trate divisão por zero)
        val ganhoPorKm = if (effectiveDistanceKm > 0.0) {
            lucroLiquido / effectiveDistanceKm
        } else {
            0.0
        }

        // Tempo total considerando retorno proporcional, espera por pedido e relevo de bike:
        val effectiveTravelMinutes = offer.estimatedTimeMinutes * returnMultiplier
        val effectiveWaitBufferMinutes = settings.restaurantWaitBufferMinutes * offer.orderCount.coerceAtLeast(1)
        val bikeSteepPenaltyMinutes = if (isSteepIncline && settings.isBike && settings.bikeElevationAlertEnabled) 6.0 else 0.0
        val tempoTotalMinutos = effectiveTravelMinutes + effectiveWaitBufferMinutes + bikeSteepPenaltyMinutes
        val tempoTotalHoras = tempoTotalMinutos / 60.0

        // GanhoPorHora = LucroLiquido / TempoTotalHoras (trate divisão por zero)
        val ganhoPorHora = if (tempoTotalHoras > 0.0) {
            lucroLiquido / tempoTotalHoras
        } else {
            0.0
        }

        // Classificação Semáforo (TrafficLightStatus):
        // * RED (Vermelho): Se exceder teto de distância OU ficar abaixo do piso de valor bruto OU abaixo das metas.
        // * GREEN (Verde): GanhoPorKm >= targetMinPerKm E GanhoPorHora >= targetMinPerHour.
        // * YELLOW (Amarelo): GanhoPorKm >= targetMinPerKm OU GanhoPorHora >= targetMinPerHour.
        val meetsKmTarget = ganhoPorKm >= settings.targetMinPerKm
        val meetsHourTarget = ganhoPorHora >= settings.targetMinPerHour

        // Piso Dinâmico de Mau Tempo / Chuva (Fase 8)
        val isRainActive = isRaining && settings.rainModeEnabled
        val effectiveMinFloor = if (isRainActive) {
            settings.minGrossValueFloor + settings.rainFloorBonus
        } else {
            settings.minGrossValueFloor
        }

        // Filtros avançados anti-prejuízo
        val violatesMaxDistance = settings.maxDistanceKm > 0.0 && offer.totalDistanceKm > settings.maxDistanceKm
        val violatesMinGrossFloor = effectiveMinFloor > 0.0 && offer.grossValue < effectiveMinFloor

        // Detecção de Área de Risco (Fase 5)
        var isRiskArea = false
        var detectedRiskArea: String? = null

        if (settings.riskAreasEnabled && settings.riskAreasList.isNotEmpty()) {
            val normalizedSearchTargets = mutableListOf<String>()
            offer.destinationNeighborhood?.let { normalizedSearchTargets.add(normalizeText(it)) }
            offer.destinationAddress?.let { normalizedSearchTargets.add(normalizeText(it)) }
            for (text in offer.rawTexts) {
                normalizedSearchTargets.add(normalizeText(text))
            }

            for (riskArea in settings.riskAreasList) {
                val normalizedArea = normalizeText(riskArea)
                if (normalizedArea.isNotBlank()) {
                    for (target in normalizedSearchTargets) {
                        if (target.contains(normalizedArea)) {
                            isRiskArea = true
                            detectedRiskArea = riskArea
                            break
                        }
                    }
                }
                if (isRiskArea) break
            }
        }

        val trafficLightStatus = when {
            isRiskArea -> TrafficLightStatus.RED
            violatesMaxDistance -> TrafficLightStatus.RED
            violatesMinGrossFloor -> TrafficLightStatus.RED
            meetsKmTarget && meetsHourTarget -> TrafficLightStatus.GREEN
            meetsKmTarget || meetsHourTarget -> TrafficLightStatus.YELLOW
            else -> TrafficLightStatus.RED
        }

        return DeliveryCalculationResult(
            fuelCostPerKm = custoCombustivelPorKm,
            totalRouteCost = custoTotalRota,
            netProfit = lucroLiquido,
            earningsPerKm = ganhoPorKm,
            totalTimeHours = tempoTotalHoras,
            earningsPerHour = ganhoPorHora,
            trafficLightStatus = trafficLightStatus,
            isRiskArea = isRiskArea,
            detectedRiskArea = detectedRiskArea,
            destinationNeighborhood = offer.destinationNeighborhood,
            orderCount = offer.orderCount,
            netProfitPerOrder = lucroLiquido / offer.orderCount.coerceAtLeast(1),
            grossValuePerOrder = offer.grossValue / offer.orderCount.coerceAtLeast(1),
            isRainActive = isRainActive,
            effectiveMinFloor = effectiveMinFloor,
            appliedFloorBonus = if (isRainActive) settings.rainFloorBonus else 0.0,
            isSteepIncline = isSteepIncline && settings.isBike && settings.bikeElevationAlertEnabled,
            elevationGainMeters = elevationGainMeters
        )
    }

    private val DIACRITICS_REGEX = Regex("""\p{InCombiningDiacriticalMarks}+""")

    /**
     * Normaliza textos removendo acentos e convertendo para minúsculas para comparações precisas.
     */
    fun normalizeText(input: String): String {
        val decomposed = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "").lowercase().trim()
    }
}
