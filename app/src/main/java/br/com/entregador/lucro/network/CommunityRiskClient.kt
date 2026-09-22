package br.com.entregador.lucro.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Cliente para sincronização da base comunitária de áreas de risco (bairros/favelas/pontos críticos).
 * Possui uma base pré-catalogada nacional embutida e capacidade de sincronizar atualizações em nuvem.
 */
object CommunityRiskClient {

    private const val DEFAULT_COMMUNITY_ENDPOINT =
        "https://raw.githubusercontent.com/josecicerobispofelix/DeliveryDriverApp/main/community_risk_areas.json"

    // Base nacional de referência pré-catalogada de áreas de alto risco (RJ, SP, Grande SP e capitais)
    val DEFAULT_NATIONAL_RISK_AREAS = listOf(
        // Rio de Janeiro
        "Chapadao", "Pedreira", "Complexo do Alemao", "Mare", "Jacarezinho",
        "Cidade de Deus", "Rocinha", "Vila Cruzeiro", "Morro do Dendê", "Salgueiro",
        "Bangu", "Acari", "Vila Kennedy", "Serrinha", "Manguinhos", "Parada de Lucas",
        // São Paulo & Grande SP
        "Paraisopolis", "Heliopolis", "Capao Redondo", "Jardim Angela", "Brasilândia",
        "Jardim Pantanal", "Parque Santo Antonio", "Favela do Moinho", "Grajaú", "Cidade Tiradentes",
        "Jardim Peri", "Parque Bristol", "Vila Nova Cachoeirinha", "Vila Baquirivu", "Jardim Arpoador",
        // Minas Gerais / BH
        "Aglomerado da Serra", "Cabana do Pai Tomas", "Morro das Pedras", "Pedreira Prado Lopes"
    )

    /**
     * Sincroniza a base de áreas de risco: tenta buscar em nuvem primeiro;
     * se a rede falhar ou estiver offline, faz o merge com o catálogo nacional embutido.
     *
     * @param existingList Lista de bairros já cadastrados pelo usuário.
     * @return Novo conjunto de bairros combinando a lista do usuário com as novas áreas sincronizadas.
     */
    suspend fun syncRiskAreas(
        existingList: List<String>,
        customEndpoint: String? = null
    ): Result<SyncResult> {
        val endpoint = customEndpoint ?: DEFAULT_COMMUNITY_ENDPOINT
        val netResult = HttpClientProvider.get(endpoint)

        val fetchedAreas = mutableSetOf<String>()

        if (netResult.isSuccess) {
            try {
                val jsonStr = netResult.getOrNull().orEmpty()
                if (jsonStr.startsWith("[")) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val item = arr.optString(i)?.trim()
                        if (!item.isNullOrEmpty()) fetchedAreas.add(item)
                    }
                } else if (jsonStr.startsWith("{")) {
                    val obj = JSONObject(jsonStr)
                    val arr = obj.optJSONArray("risk_areas") ?: obj.optJSONArray("areas")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optString(i)?.trim()
                            if (!item.isNullOrEmpty()) fetchedAreas.add(item)
                        }
                    }
                }
            } catch (_: Exception) {
                // Se o JSON remoto estiver mal formatado, fallback para a base local
            }
        }

        // Se a busca remota não encontrou nada (offline ou endpoint não criado ainda), usa o catálogo nacional embutido
        if (fetchedAreas.isEmpty()) {
            fetchedAreas.addAll(DEFAULT_NATIONAL_RISK_AREAS)
        }

        // Realiza o merge sem duplicar bairros já existentes (insensível a maiúsculas/minúsculas)
        val normalizedExisting = existingList.map { normalize(it) }.toSet()
        val newItems = fetchedAreas.filter { normalize(it) !in normalizedExisting }
        val mergedList = (existingList + newItems).distinct()

        return Result.success(
            SyncResult(
                totalAreas = mergedList.size,
                newAreasAdded = newItems.size,
                updatedList = mergedList,
                source = if (netResult.isSuccess && fetchedAreas.size > DEFAULT_NATIONAL_RISK_AREAS.size) "Nuvem Comunitária" else "Catálogo Nacional Integrado"
            )
        )
    }

    private fun normalize(name: String): String =
        name.trim().lowercase().replace("ã", "a").replace("á", "a").replace("é", "e").replace("ó", "o")

    data class SyncResult(
        val totalAreas: Int,
        val newAreasAdded: Int,
        val updatedList: List<String>,
        val source: String
    )
}
