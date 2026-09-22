package br.com.entregador.lucro.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunityRiskClientTest {

    @Test
    fun syncCityRiskAreas_forAtibaia_includesExclusivelyAtibaiaDangerousPoints() = runBlocking {
        val existing = listOf("Bairro Personalizado Entregador")
        val result = CommunityRiskClient.syncCityRiskAreas(
            existingList = existing,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherCities = true
        ).getOrThrow()

        // Locais genuínos de atenção / risco EXCLUSIVOS de Atibaia
        assertTrue(result.updatedList.contains("Caetetuba"))
        assertTrue(result.updatedList.contains("Jardim Imperial"))
        assertTrue(result.updatedList.contains("Bairro Personalizado Entregador")) // Preserva o customizado

        // Bairros de OUTRAS cidades NÃO devem entrar quando a cidade é Atibaia
        assertFalse(result.updatedList.contains("Henedina Cortez")) // Bragança Paulista
        assertFalse(result.updatedList.contains("Parque dos Estados")) // Bragança Paulista
        assertFalse(result.updatedList.contains("Pimentas")) // Guarulhos
        assertFalse(result.updatedList.contains("Campo Grande")) // Campinas
        assertFalse(result.updatedList.contains("Terra Preta")) // Mairiporã
        assertFalse(result.updatedList.contains("Brasilândia")) // São Paulo

        // Bairros comuns e seguros de Atibaia NÃO devem entrar na lista de risco
        assertFalse(result.updatedList.contains("Alvinópolis"))
        assertFalse(result.updatedList.contains("Tanque"))
        assertFalse(result.updatedList.contains("Portão"))
        assertFalse(result.updatedList.contains("Usina"))
        assertFalse(result.updatedList.contains("Boa Vista"))
        assertFalse(result.updatedList.contains("Maracanã"))
        assertFalse(result.updatedList.contains("Jardim Cerejeiras"))
    }

    @Test
    fun pruneAreasNotInCity_removesOtherCitiesAndSafeAreas() {
        // Simula lista contaminada com bairros de outras cidades e bairros seguros
        val list = listOf(
            "Caetetuba",
            "Jardim Imperial",
            "Henedina Cortez", // Bragança
            "Pimentas", // Guarulhos
            "Jacarezinho", // RJ
            "Alvinópolis", // Seguro
            "Tanque", // Seguro
            "Meu Ponto Seguro Customizado" // Personalizado pelo usuário
        )

        val cleaned = CommunityRiskClient.pruneAreasNotInCity(
            existingList = list,
            userCity = "Atibaia",
            userState = "SP"
        )

        // Deve conter apenas Caetetuba, Jardim Imperial e o customizado do usuário
        assertEquals(listOf("Caetetuba", "Jardim Imperial", "Meu Ponto Seguro Customizado"), cleaned)
    }

    @Test
    fun syncCityRiskAreas_forAtibaia_neverIncludesRioDeJaneiro() = runBlocking {
        val result = CommunityRiskClient.syncCityRiskAreas(
            existingList = emptyList(),
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherCities = true
        ).getOrThrow()

        // Garante que NENHUM bairro do Rio de Janeiro é importado para Atibaia/SP
        assertFalse(result.updatedList.contains("Chapadão"))
        assertFalse(result.updatedList.contains("Pedreira"))
        assertFalse(result.updatedList.contains("Complexo do Alemão"))
        assertFalse(result.updatedList.contains("Maré"))
        assertFalse(result.updatedList.contains("Jacarezinho"))
        assertFalse(result.updatedList.contains("Rocinha"))
        assertFalse(result.updatedList.contains("Cidade de Deus"))
    }

    @Test
    fun pruneSafeNeighborhoods_removesOnlySafeNeighborhoods() {
        val list = listOf("Caetetuba", "Alvinópolis", "Tanque", "Jardim Imperial", "Outro Local")
        val cleaned = CommunityRiskClient.pruneSafeNeighborhoods(list)

        assertEquals(listOf("Caetetuba", "Jardim Imperial", "Outro Local"), cleaned)
    }
}
