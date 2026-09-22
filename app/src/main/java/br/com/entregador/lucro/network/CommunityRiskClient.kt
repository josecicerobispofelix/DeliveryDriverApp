package br.com.entregador.lucro.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente para sincronização inteligente da base comunitária de áreas de risco (bairros/favelas/pontos críticos).
 * Permite filtragem estrita por geolocalização (cidade do entregador e cidades vizinhas/próximas),
 * impedindo a inclusão de cidades distantes ou de outros estados (como Rio de Janeiro para entregadores de SP).
 */
object CommunityRiskClient {

    private const val DEFAULT_COMMUNITY_ENDPOINT =
        "https://raw.githubusercontent.com/josecicerobispofelix/DeliveryDriverApp/main/community_risk_areas.json"

    data class RiskAreaEntry(
        val name: String,
        val city: String,
        val state: String,
        val isImmediateNeighbor: Boolean = false
    )

    // Conjunto de bairros seguros, comerciais e residenciais comuns de Atibaia e região que NUNCA devem ser bloqueados
    val SAFE_COMMERCIAL_RESIDENTIAL_AREAS = setOf(
        "alvinopolis", "alvinópolis", "jardim alvinopolis", "jardim alvinópolis",
        "tanque", "portao", "portão", "usina", "boa vista", "maracana", "maracanã",
        "cerejeiras", "jardim cerejeiras", "atibaia jardim", "centro", "vila santista",
        "jardim do lago", "jardim paulista", "estancia", "estância", "lucas", "vila giglio",
        "itapetinga", "ressaca", "aguas claras", "águas claras", "vila bianchi",
        "jardim fraternidade", "jardim sao miguel", "jardim são miguel", "jardim suico", "jardim suíço"
    )

    // Base Estritamente Focada em Áreas de Risco Reais (Periferias críticas e favelas)
    val ATIBAIA_AND_NEIGHBORS_RISK_AREAS = listOf(
        // Atibaia (Apenas locais críticos conhecidos por ocorrências/atenção noturna)
        RiskAreaEntry("Caetetuba", "Atibaia", "SP", true),
        RiskAreaEntry("Jardim Imperial", "Atibaia", "SP", true),

        // Bragança Paulista (Cidades vizinhas - Apenas pontos críticos)
        RiskAreaEntry("Henedina Cortez", "Bragança Paulista", "SP", true),
        RiskAreaEntry("Parque dos Estados", "Bragança Paulista", "SP", true),

        // Cidades Vizinhas Imediatas (Apenas áreas de atenção)
        RiskAreaEntry("Cachoeirinha", "Bom Jesus dos Perdões", "SP", true),
        RiskAreaEntry("Batatuba", "Piracaia", "SP", true),

        // Mairiporã, Franco da Rocha e Francisco Morato (Apenas periferias críticas)
        RiskAreaEntry("Terra Preta", "Mairiporã", "SP", true),
        RiskAreaEntry("Parque Vitória", "Franco da Rocha", "SP", true),
        RiskAreaEntry("Pretória", "Franco da Rocha", "SP", true),
        RiskAreaEntry("Belém Capela", "Francisco Morato", "SP", true),

        // Polos Metropolitanos Vizinhos (Apenas áreas de risco conhecidas)
        RiskAreaEntry("Campo Grande", "Campinas", "SP", false),
        RiskAreaEntry("Ouro Verde", "Campinas", "SP", false),
        RiskAreaEntry("Pimentas", "Guarulhos", "SP", false),
        RiskAreaEntry("Bonsucesso", "Guarulhos", "SP", false),

        // Grande São Paulo / Capital Norte (Rota Rodovia Fernão Dias saindo de Atibaia)
        RiskAreaEntry("Brasilândia", "São Paulo", "SP", false),
        RiskAreaEntry("Jardim Peri", "São Paulo", "SP", false),
        RiskAreaEntry("Vila Nova Cachoeirinha", "São Paulo", "SP", false),
        RiskAreaEntry("Favela do Moinho", "São Paulo", "SP", false),
        RiskAreaEntry("Paraisopolis", "São Paulo", "SP", false),
        RiskAreaEntry("Heliopolis", "São Paulo", "SP", false),
        RiskAreaEntry("Capao Redondo", "São Paulo", "SP", false),
        RiskAreaEntry("Jardim Angela", "São Paulo", "SP", false),
        RiskAreaEntry("Grajaú", "São Paulo", "SP", false),
        RiskAreaEntry("Cidade Tiradentes", "São Paulo", "SP", false)
    )

    // Base Isolada do Rio de Janeiro (RJ) - Nunca deve entrar para usuários de SP
    val RIO_DE_JANEIRO_RISK_AREAS = listOf(
        RiskAreaEntry("Chapadao", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Pedreira", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Complexo do Alemao", "Rio de Janeiro", "RJ"),
        RiskAreaEntry("Mare", "Rio de Janeiro", "RJ"),
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
        RiskAreaEntry("Parada de Lucas", "Rio de Janeiro", "RJ")
    )

    // Base Isolada de Minas Gerais (MG)
    val MINAS_GERAIS_RISK_AREAS = listOf(
        RiskAreaEntry("Aglomerado da Serra", "Belo Horizonte", "MG"),
        RiskAreaEntry("Cabana do Pai Tomas", "Belo Horizonte", "MG"),
        RiskAreaEntry("Morro das Pedras", "Belo Horizonte", "MG"),
        RiskAreaEntry("Pedreira Prado Lopes", "Belo Horizonte", "MG")
    )

    // Lista de nomes de bairros conhecidos de outros estados (RJ e MG) para limpeza automática
    val NON_SP_KNOWN_AREAS_SET: Set<String> by lazy {
        (RIO_DE_JANEIRO_RISK_AREAS + MINAS_GERAIS_RISK_AREAS).map { normalize(it.name) }.toSet()
    }

    /**
     * Remove da lista do usuário quaisquer áreas conhecidas que pertençam a outros estados (ex: Rio de Janeiro e Minas Gerais).
     */
    fun pruneUnrelatedStateAreas(existingList: List<String>): List<String> {
        return existingList.filter { normalize(it) !in NON_SP_KNOWN_AREAS_SET }
    }

    /**
     * Remove da lista bairros comerciais e residenciais comuns de Atibaia e região que são seguros e não devem ser bloqueados.
     */
    fun pruneSafeNeighborhoods(existingList: List<String>): List<String> {
        val safeNormalized = SAFE_COMMERCIAL_RESIDENTIAL_AREAS.map { normalize(it) }.toSet()
        return existingList.filter { normalize(it) !in safeNormalized }
    }

    /**
     * Sincroniza a base comunitária filtrada estritamente para a localização do usuário.
     * Se o usuário estiver em Atibaia/SP, importa SOMENTE áreas genuinamente de risco (Caetetuba, Jd Imperial, periferias críticas),
     * e remove automaticamente bairros seguros comuns (Alvinópolis, Portão, Tanque, etc.) e favelas do RJ/MG.
     */
    suspend fun syncRegionalRiskAreas(
        existingList: List<String>,
        userCity: String = "Atibaia",
        userState: String = "SP",
        autoPruneOtherStates: Boolean = true,
        customEndpoint: String? = null
    ): Result<SyncResult> {
        val targetState = userState.trim().uppercase()
        val targetCity = userCity.trim()

        // 1. Limpa bairros de fora (RJ/MG) e bairros comuns seguros adicionados anteriormente
        var intermediate = existingList
        if (autoPruneOtherStates && targetState == "SP") {
            intermediate = pruneUnrelatedStateAreas(intermediate)
        }
        if (targetState == "SP") {
            intermediate = pruneSafeNeighborhoods(intermediate)
        }
        val cleanedExisting = intermediate
        val removedCount = existingList.size - cleanedExisting.size

        // 2. Coleta áreas disponíveis
        val targetCatalog = when {
            targetState == "RJ" -> RIO_DE_JANEIRO_RISK_AREAS.map { it.name }
            targetState == "MG" -> MINAS_GERAIS_RISK_AREAS.map { it.name }
            else -> ATIBAIA_AND_NEIGHBORS_RISK_AREAS.map { it.name }
        }

        // Tenta buscar atualizações remotas via nuvem
        val endpoint = customEndpoint ?: DEFAULT_COMMUNITY_ENDPOINT
        val netResult = HttpClientProvider.get(endpoint)
        val fetchedRemote = mutableSetOf<String>()

        if (netResult.isSuccess) {
            try {
                val jsonStr = netResult.getOrNull().orEmpty()
                if (jsonStr.startsWith("{")) {
                    val obj = JSONObject(jsonStr)
                    // Se o JSON contiver divisões por estado/região:
                    val regionalObj = obj.optJSONObject("regions")
                    val stateArr = regionalObj?.optJSONArray(targetState)
                        ?: obj.optJSONArray("areas")
                    if (stateArr != null) {
                        for (i in 0 until stateArr.length()) {
                            val item = stateArr.optString(i)?.trim()
                            if (!item.isNullOrEmpty() && (targetState != "SP" || normalize(item) !in NON_SP_KNOWN_AREAS_SET)) {
                                fetchedRemote.add(item)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback para catálogo local
            }
        }

        val availableCandidates = (targetCatalog + fetchedRemote).distinct()

        // 3. Mescla sem duplicar
        val normalizedExisting = cleanedExisting.map { normalize(it) }.toSet()
        val newItems = availableCandidates.filter { normalize(it) !in normalizedExisting }
        val mergedList = (cleanedExisting + newItems).distinct()

        val regionDesc = if (targetState == "SP") {
            "Atibaia e Cidades Próximas (SP)"
        } else {
            "$targetCity ($targetState)"
        }

        return Result.success(
            SyncResult(
                totalAreas = mergedList.size,
                newAreasAdded = newItems.size,
                removedOtherStateAreas = removedCount,
                updatedList = mergedList,
                source = if (fetchedRemote.isNotEmpty()) "Nuvem Comunitária Regional" else "Catálogo Regional Integrado",
                regionDescription = regionDesc
            )
        )
    }

    /**
     * Compatibilidade retroativa com a chamada genérica anterior
     */
    suspend fun syncRiskAreas(
        existingList: List<String>,
        customEndpoint: String? = null
    ): Result<SyncResult> {
        return syncRegionalRiskAreas(
            existingList = existingList,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherStates = true,
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
        val updatedList: List<String>,
        val source: String,
        val regionDescription: String
    )
}
