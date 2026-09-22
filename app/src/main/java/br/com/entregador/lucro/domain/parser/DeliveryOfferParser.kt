package br.com.entregador.lucro.domain.parser

import br.com.entregador.lucro.domain.model.DeliveryOffer

/**
 * Utilitário responsável por extrair dados financeiros e de rota de textos capturados
 * da tela através de expressões regulares (Regex).
 */
object DeliveryOfferParser {

    // Valor monetário: padrão para capturar valores como "R$ 14,50" -> Regex("""R\$\s?(\d+[\.,]\d{2})""")
    val MONEY_REGEX = Regex("""R\$\s?(\d+[\.,]\d{2})""")

    // Distâncias: padrão para capturar quilómetros como "3,2 km" ou "1.5 km" -> Regex("""(\d+[\.,]?\d*)\s?km""")
    val DISTANCE_REGEX = Regex("""(\d+[\.,]?\d*)\s?km""", RegexOption.IGNORE_CASE)

    // Tempo estimado: padrão para minutos como "18 min" -> Regex("""(\d+)\s?min""")
    val TIME_REGEX = Regex("""(\d+)\s?min""", RegexOption.IGNORE_CASE)

    // Prefixos de destino/entrega típicos de apps de entrega e transporte
    val DEST_PREFIX_REGEX = Regex(
        """^(?:Entrega(?:\s+em)?|Destino(?:\s*:)?|Entregar(?:\s+em)?|Desembarque(?:\s+em)?|Para(?:\s*:)?)\s*[:\-]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    // Multi-pedidos / Rota Dupla típica do iFood e Uber
    val MULTI_ORDER_REGEX = Regex("""(\d+)\s*(?:entregas?|coletas?|pedidos?|paradas?)""", RegexOption.IGNORE_CASE)
    val ROTA_DUPLA_REGEX = Regex("""rota\s+(?:com\s+)?2\s+(?:pedidos|entregas)|rota\s+dupla""", RegexOption.IGNORE_CASE)
    val ROTA_TRIPLA_REGEX = Regex("""rota\s+(?:com\s+)?3\s+(?:pedidos|entregas)|rota\s+tripla""", RegexOption.IGNORE_CASE)
    val PEDIDOS_AGRUPADOS_REGEX = Regex("""pedidos?\s+agrupados?""", RegexOption.IGNORE_CASE)

    /**
     * Extrai o valor monetário bruto em R$ a partir de um texto.
     */
    fun extractGrossValue(text: String): Double? {
        val match = MONEY_REGEX.find(text) ?: return null
        val numberStr = match.groupValues[1].replace(',', '.')
        return numberStr.toDoubleOrNull()
    }

    /**
     * Extrai todas as ocorrências de distâncias em km encontradas no texto.
     */
    fun extractDistancesKm(text: String): List<Double> {
        val matches = DISTANCE_REGEX.findAll(text)
        return matches.mapNotNull { match ->
            val numberStr = match.groupValues[1].replace(',', '.')
            numberStr.toDoubleOrNull()
        }.toList()
    }

    /**
     * Extrai o tempo estimado em minutos a partir de um texto.
     */
    fun extractEstimatedTimeMinutes(text: String): Int? {
        val match = TIME_REGEX.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Extrai o endereço e bairro de destino a partir da lista de nós de texto.
     * Retorna um Pair(endereçoCompleto, bairroApurado).
     */
    fun extractDestination(texts: List<String>): Pair<String?, String?> {
        var rawDest: String? = null

        for (i in texts.indices) {
            val item = texts[i].trim()
            val match = DEST_PREFIX_REGEX.find(item)
            if (match != null) {
                val captured = match.groupValues[1].trim()
                if (captured.isNotBlank() && !captured.contains("R$") && !captured.endsWith("min", ignoreCase = true)) {
                    rawDest = captured
                    break
                } else if (i + 1 < texts.size) {
                    val nextItem = texts[i + 1].trim()
                    if (nextItem.isNotBlank() && !nextItem.contains("R$") && !nextItem.endsWith("min", ignoreCase = true) && !nextItem.endsWith("km", ignoreCase = true)) {
                        rawDest = nextItem
                        break
                    }
                }
            }
        }

        if (rawDest == null) {
            for (item in texts) {
                val lower = item.lowercase()
                if (lower.startsWith("bairro:") || lower.startsWith("bairro ")) {
                    rawDest = item.substringAfter(":").trim()
                    break
                }
            }
        }

        if (rawDest == null) return Pair(null, null)

        val cleanDest = rawDest.replace(Regex("""^[•\-\s]+"""), "").trim()

        val neighborhood = if (cleanDest.contains(" - ")) {
            cleanDest.substringAfterLast(" - ").substringBefore(",").trim()
        } else if (cleanDest.contains(",")) {
            cleanDest.substringAfterLast(",").substringBefore("-").trim()
        } else {
            cleanDest
        }

        val finalNeighborhood = if (neighborhood.isNotBlank()) neighborhood else cleanDest
        return Pair(cleanDest, finalNeighborhood)
    }

    /**
     * Identifica a quantidade de entregas/pedidos na rota (ex: "2 entregas", "rota dupla").
     * Retorna 1 por padrão se for corrida simples.
     */
    fun extractOrderCount(texts: List<String>): Int {
        for (text in texts) {
            val match = MULTI_ORDER_REGEX.find(text)
            if (match != null) {
                val count = match.groupValues[1].toIntOrNull()
                if (count != null && count in 2..10) return count
            }
            if (ROTA_TRIPLA_REGEX.containsMatchIn(text)) return 3
            if (ROTA_DUPLA_REGEX.containsMatchIn(text)) return 2
            if (PEDIDOS_AGRUPADOS_REGEX.containsMatchIn(text)) return 2
        }
        return 1
    }

    /**
     * Analisa uma lista de textos coletados dos nós de tela e monta uma [DeliveryOffer].
     * Caso existam múltiplos valores de km (deslocamento para recolha e entrega final),
     * soma as distâncias para apurar a distância total.
     */
    fun parseOffer(texts: List<String>, platform: String): DeliveryOffer? {
        val combinedText = texts.joinToString(separator = " ")

        val grossValue = extractGrossValue(combinedText) ?: return null

        val distances = extractDistancesKm(combinedText)
        if (distances.isEmpty()) return null
        // Caso existam múltiplos valores de km, soma as distâncias
        val totalDistanceKm = distances.sum()

        val estimatedTimeMinutes = extractEstimatedTimeMinutes(combinedText) ?: 0

        val (destAddress, destNeighborhood) = extractDestination(texts)
        val orderCount = extractOrderCount(texts)

        return DeliveryOffer(
            platform = platform,
            grossValue = grossValue,
            totalDistanceKm = totalDistanceKm,
            estimatedTimeMinutes = estimatedTimeMinutes,
            destinationAddress = destAddress,
            destinationNeighborhood = destNeighborhood,
            rawTexts = texts,
            orderCount = orderCount
        )
    }
}
