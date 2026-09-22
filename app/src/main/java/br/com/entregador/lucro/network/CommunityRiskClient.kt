package br.com.entregador.lucro.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente para sincronização inteligente da base comunitária de áreas de risco (bairros/favelas/pontos críticos).
 * Filtrado estritamente para a cidade do entregador (ex: Atibaia - SP),
 * impedindo a inclusão indevida de bairros de outras cidades (Bragança, Campinas, Guarulhos, São Paulo, RJ, etc.).
 */
object CommunityRiskClient {

    private const val DEFAULT_COMMUNITY_ENDPOINT =
        "https://raw.githubusercontent.com/josecicerobispofelix/DeliveryDriverApp/main/community_risk_areas.json"

    data class RiskAreaEntry(
        val name: String,
        val city: String,
        val state: String
    )

    // Conjunto de bairros seguros, comerciais e residenciais comuns de Atibaia que NUNCA devem ser bloqueados
    val SAFE_COMMERCIAL_RESIDENTIAL_AREAS = setOf(
        "alvinopolis", "alvinópolis", "jardim alvinopolis", "jardim alvinópolis",
        "tanque", "portao", "portão", "usina", "boa vista", "maracana", "maracanã",
        "cerejeiras", "jardim cerejeiras", "atibaia jardim", "centro", "vila santista",
        "jardim do lago", "jardim paulista", "estancia", "estância", "lucas", "vila giglio",
        "itapetinga", "ressaca", "aguas claras", "águas claras", "vila bianchi",
        "jardim fraternidade", "jardim sao miguel", "jardim são miguel", "jardim suico", "jardim suíço"
    )

    // Catálogo Mapeado Estritamente por Cidade e Estado
    val ALL_KNOWN_RISK_AREAS = listOf(
        // Atibaia - SP: Apenas as áreas com histórico policial de tráfico/atenção noturna (Caetetuba e Jd Imperial)
        RiskAreaEntry("Caetetuba", "Atibaia", "SP"),
        RiskAreaEntry("Jardim Imperial", "Atibaia", "SP"),

        // Bragança Paulista - SP
        RiskAreaEntry("Henedina Cortez", "Bragança Paulista", "SP"),
        RiskAreaEntry("Parque dos Estados", "Bragança Paulista", "SP"),

        // Bom Jesus dos Perdões - SP
        RiskAreaEntry("Cachoeirinha", "Bom Jesus dos Perdões", "SP"),

        // Piracaia - SP
        RiskAreaEntry("Batatuba", "Piracaia", "SP"),

        // Mairiporã - SP
        RiskAreaEntry("Terra Preta", "Mairiporã", "SP"),

        // Franco da Rocha - SP
        RiskAreaEntry("Parque Vitória", "Franco da Rocha", "SP"),
        RiskAreaEntry("Pretória", "Franco da Rocha", "SP"),

        // Francisco Morato - SP
        RiskAreaEntry("Belém Capela", "Francisco Morato", "SP"),

        // Campinas - SP
        RiskAreaEntry("Campo Grande", "Campinas", "SP"),
        RiskAreaEntry("Ouro Verde", "Campinas", "SP"),

        // Guarulhos - SP
        RiskAreaEntry("Pimentas", "Guarulhos", "SP"),
        RiskAreaEntry("Bonsucesso", "Guarulhos", "SP"),

        // São Paulo (Capital) - SP
        RiskAreaEntry("Brasilândia", "São Paulo", "SP"),
        RiskAreaEntry("Jardim Peri", "São Paulo", "SP"),
        RiskAreaEntry("Vila Nova Cachoeirinha", "São Paulo", "SP"),
        RiskAreaEntry("Favela do Moinho", "São Paulo", "SP"),
        RiskAreaEntry("Paraisópolis", "São Paulo", "SP"),
        RiskAreaEntry("Heliópolis", "São Paulo", "SP"),
        RiskAreaEntry("Capão Redondo", "São Paulo", "SP"),
        RiskAreaEntry("Jardim Ângela", "São Paulo", "SP"),
        RiskAreaEntry("Grajaú", "São Paulo", "SP"),
        RiskAreaEntry("Cidade Tiradentes", "São Paulo", "SP"),

        // Rio de Janeiro - RJ
        RiskAreaEntry("Chapadão", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Pedreira", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Complexo do Alemão", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Maré", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Jacarezinho", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Cidade de Deus", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Rocinha", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Vila Cruzeiro", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Morro do Dendê", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Salgueiro", "São Gonçalo", "RJ"),
        RiskAreaEntry("Bangu", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Acari", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Vila Kennedy", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Serrinha", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Manguinhos", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Parada de Lucas", "Rio de Janeiro", "RJ"),

        // Minas Gerais - MG
        RiskAreaEntry("Aglomerado da Serra", "Belo Horizonte", "MG"),
        RiskAreaEntry("Cabana do Pai Tomás", "Belo Horizonte", "MG"),
        RiskAreaEntry("Morro das Pedras", "Belo Horizonte", "MG"),
        RiskAreaEntry("Pedreira Prado Lopes", "Belo Horizonte", "MG")
    )

    /**
     * Identifica e remove da lista bairros conhecidos que NÃO pertencem à cidade/UF do usuário,
     * bem como bairros residenciais/comerciais seguros.
     */
    fun pruneAreasNotInCity(
        existingList: List<String>,
        userCity: String = "Atibaia",
        userState: String = "SP"
    ): List<String> {
        val normCity = normalize(userCity)
        val normState = normalize(userState)

        // Bairros conhecidos que pertencem a OUTRAS cidades ou OUTROS estados
        val otherCitiesAreas = ALL_KNOWN_RISK_AREAS
            .filter { normalize(it.city) != normCity || normalize(it.state) != normState }
            .map { normalize(it.name) }
            .toSet()

        val safeAreas = SAFE_COMMERCIAL_RESIDENTIAL_AREAS.map { normalize(it) }.toSet()

        return existingList.filter { item ->
            val normItem = normalize(item)
            normItem !in otherCitiesAreas && normItem !in safeAreas
        }
    }

    /**
     * Remove especificamente áreas conhecidas de outros estados (ex: RJ e MG).
     */
    fun pruneUnrelatedStateAreas(existingList: List<String>): List<String> {
        val nonSpAreas = ALL_KNOWN_RISK_AREAS
            .filter { it.state != "SP" }
            .map { normalize(it.name) }
            .toSet()
        return existingList.filter { normalize(it) !in nonSpAreas }
    }

    /**
     * Remove da lista bairros comerciais e residenciais comuns de Atibaia que são seguros.
     */
    fun pruneSafeNeighborhoods(existingList: List<String>): List<String> {
        val safeNormalized = SAFE_COMMERCIAL_RESIDENTIAL_AREAS.map { normalize(it) }.toSet()
        return existingList.filter { normalize(it) !in safeNormalized }
    }

    /**
     * Sincroniza a base comunitária filtrada ESTRITAMENTE para a cidade do usuário (ex: Atibaia).
     * Se o usuário estiver em Atibaia, importa SOMENTE Caetetuba e Jardim Imperial (se houver histórico de risco),
     * e expurga qualquer bairro de outra cidade (Bragança, Campinas, Guarulhos, Franco da Rocha, RJ, etc.) ou bairro residencial seguro.
     */
    suspend fun syncCityRiskAreas(
        existingList: List<String>,
        userCity: String = "Atibaia",
        userState: String = "SP",
        autoPruneOtherCities: Boolean = true,
        customEndpoint: String? = null
    ): Result<SyncResult> {
        val targetState = userState.trim().uppercase()
        val targetCity = userCity.trim()
        val normCity = normalize(targetCity)
        val normState = normalize(targetState)

        // 1. Limpeza de bairros de fora e bairros seguros
        var cleanedExisting = existingList
        if (autoPruneOtherCities) {
            cleanedExisting = pruneAreasNotInCity(cleanedExisting, targetCity, targetState)
        }
        val removedCount = existingList.size - cleanedExisting.size

        // 2. Coleta candidatos EXCLUSIVOS da cidade do usuário
        val cityCatalog = ALL_KNOWN_RISK_AREAS
            .filter { normalize(it.city) == normCity && normalize(it.state) == normState }
            .map { it.name }

        // Tenta buscar atualizações remotas via nuvem filtrando estritamente pela cidade
        val endpoint = customEndpoint ?: DEFAULT_COMMUNITY_ENDPOINT
        val netResult = HttpClientProvider.get(endpoint)
        val fetchedRemote = mutableSetOf<String>()

        if (netResult.isSuccess) {
            try {
                val jsonStr = netResult.getOrNull().orEmpty()
                if (jsonStr.startsWith("{")) {
                    val obj = JSONObject(jsonStr)
                    val citiesObj = obj.optJSONObject("cities")
                    val cityArr = citiesObj?.optJSONArray(normCity)
                    if (cityArr != null) {
                        for (i in 0 until cityArr.length()) {
                            val item = cityArr.optString(i)?.trim()
                            if (!item.isNullOrEmpty()) {
                                fetchedRemote.add(item)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback para catálogo local
            }
        }

        val availableCandidates = (cityCatalog + fetchedRemote).distinct()

        // 3. Mescla sem duplicar
        val normalizedExisting = cleanedExisting.map { normalize(it) }.toSet()
        val newItems = availableCandidates.filter { normalize(it) !in normalizedExisting }
        val mergedList = (cleanedExisting + newItems).distinct()

        val regionDesc = "$targetCity ($targetState)"

        return Result.success(
            SyncResult(
                totalAreas = mergedList.size,
                newAreasAdded = newItems.size,
                removedOtherStateAreas = removedCount,
                removedOtherCityAreas = removedCount,
                updatedList = mergedList,
                source = if (fetchedRemote.isNotEmpty()) "Nuvem Comunitária de $targetCity" else "Catálogo Oficial de $targetCity",
                regionDescription = regionDesc
            )
        )
    }

    /**
     * Compatibilidade retroativa com syncRegionalRiskAreas
     */
    suspend fun syncRegionalRiskAreas(
        existingList: List<String>,
        userCity: String = "Atibaia",
        userState: String = "SP",
        autoPruneOtherStates: Boolean = true,
        customEndpoint: String? = null
    ): Result<SyncResult> {
        return syncCityRiskAreas(
            existingList = existingList,
            userCity = userCity,
            userState = userState,
            autoPruneOtherCities = autoPruneOtherStates,
            customEndpoint = customEndpoint
        )
    }

    /**
     * Compatibilidade retroativa com a chamada genérica anterior
     */
    suspend fun syncRiskAreas(
        existingList: List<String>,
        customEndpoint: String? = null
    ): Result<SyncResult> {
        return syncCityRiskAreas(
            existingList = existingList,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherCities = true,
            customEndpoint = customEndpoint
        )
    }

    fun normalize(name: String): String =
        name.trim().lowercase()
            .replace("ã", "a").replace("á", "a").replace("à", "a").replace("â", "a")
            .replace("é", "e").replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o").replace("ô", "o").replace("õ", "o")
            .replace("ú", "u")
            .replace("ç", "c")

    data class SyncResult(
        val totalAreas: Int,
        val newAreasAdded: Int,
        val removedOtherStateAreas: Int,
        val removedOtherCityAreas: Int = removedOtherStateAreas,
        val updatedList: List<String>,
        val source: String,
        val regionDescription: String
    )
}
