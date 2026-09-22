package br.com.entregador.lucro.domain.model

/**
 * Classificação do semáforo de viabilidade da corrida para o entregador:
 * - [GREEN]: Ambas as metas atingidas (GanhoPorKm >= targetMinPerKm E GanhoPorHora >= targetMinPerHour).
 * - [YELLOW]: Apenas uma das metas atingida (GanhoPorKm >= targetMinPerKm OU GanhoPorHora >= targetMinPerHour).
 * - [RED]: Nenhuma das metas atingida (abaixo de ambas).
 */
enum class TrafficLightStatus {
    GREEN,
    YELLOW,
    RED
}
