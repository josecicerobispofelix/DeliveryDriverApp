package br.com.entregador.lucro.domain.usecase

import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.DeliverySettings
import br.com.entregador.lucro.domain.model.ProfitCalculationResult

/**
 * Caso de uso responsável por calcular em tempo real o lucro líquido, custos operacionais
 * e viabilidade financeira de uma oferta de entrega (iFood, Uber, 99).
 */
class CalculateProfitUseCase {

    /**
     * Executa a análise financeira da oferta recebida de acordo com os parâmetros do entregador.
     *
     * @param offer Dados da corrida/oferta recebida.
     * @param settings Configurações financeiras e operacionais atuais do entregador.
     * @return [ProfitCalculationResult] com os custos, lucro e validação das metas.
     */
    operator fun invoke(offer: DeliveryOffer, settings: DeliverySettings): ProfitCalculationResult {
        val distanceKm = maxOf(0.0, offer.totalDistanceKm)
        val grossValue = maxOf(0.0, offer.grossValue)
        val tripMinutes = maxOf(0, offer.estimatedTimeMinutes)

        val fuelCost = if (settings.isBike) {
            0.0
        } else {
            if (settings.fuelConsumptionKmPerLiter > 0.0) {
                (distanceKm / settings.fuelConsumptionKmPerLiter) * settings.fuelPricePerLiter
            } else {
                0.0
            }
        }

        val maintenanceCost = distanceKm * settings.maintenanceCostPerKm
        val totalOperatingCost = fuelCost + maintenanceCost
        val netProfit = grossValue - totalOperatingCost

        val netRatePerKm = if (distanceKm > 0.0) {
            netProfit / distanceKm
        } else {
            0.0
        }

        val effectiveTotalMinutes = tripMinutes + maxOf(0, settings.restaurantWaitBufferMinutes)

        val netRatePerHour = if (effectiveTotalMinutes > 0) {
            (netProfit / effectiveTotalMinutes) * 60.0
        } else {
            0.0
        }

        val isTargetKmMet = netRatePerKm >= settings.targetMinPerKm
        val isTargetHourMet = netRatePerHour >= settings.targetMinPerHour
        val isProfitable = isTargetKmMet && isTargetHourMet && netProfit > 0.0

        return ProfitCalculationResult(
            offer = offer,
            fuelCost = fuelCost,
            maintenanceCost = maintenanceCost,
            totalOperatingCost = totalOperatingCost,
            netProfit = netProfit,
            netRatePerKm = netRatePerKm,
            effectiveTotalMinutes = effectiveTotalMinutes,
            netRatePerHour = netRatePerHour,
            isTargetKmMet = isTargetKmMet,
            isTargetHourMet = isTargetHourMet,
            isProfitable = isProfitable
        )
    }
}
