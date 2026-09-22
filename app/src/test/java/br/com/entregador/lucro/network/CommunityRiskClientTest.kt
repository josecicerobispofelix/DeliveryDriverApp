package br.com.entregador.lucro.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunityRiskClientTest {

    @Test
    fun syncRegionalRiskAreas_forAtibaia_includesAtibaiaAndNeighbors() = runBlocking {
        val existing = listOf("Bairro Personalizado Entregador")
        val result = CommunityRiskClient.syncRegionalRiskAreas(
            existingList = existing,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherStates = true
        ).getOrThrow()

        // Verifica se bairros de Atibaia e cidades vizinhas estão presentes
        assertTrue(result.updatedList.contains("Caetetuba"))
        assertTrue(result.updatedList.contains("Tanque"))
        assertTrue(result.updatedList.contains("Portão"))
        assertTrue(result.updatedList.contains("Henedina Cortez")) // Bragança Paulista
        assertTrue(result.updatedList.contains("Cachoeirinha")) // Bom Jesus dos Perdões
        assertTrue(result.updatedList.contains("Terra Preta")) // Mairiporã
        assertTrue(result.updatedList.contains("Bairro Personalizado Entregador")) // Não apaga o customizado
    }

    @Test
    fun syncRegionalRiskAreas_forAtibaia_neverIncludesRioDeJaneiro() = runBlocking {
        val result = CommunityRiskClient.syncRegionalRiskAreas(
            existingList = emptyList(),
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherStates = true
        ).getOrThrow()

        // Garante que NENHUM bairro do Rio de Janeiro é importado para Atibaia/SP
        assertFalse(result.updatedList.contains("Chapadao"))
        assertFalse(result.updatedList.contains("Pedreira"))
        assertFalse(result.updatedList.contains("Complexo do Alemao"))
        assertFalse(result.updatedList.contains("Mare"))
        assertFalse(result.updatedList.contains("Jacarezinho"))
        assertFalse(result.updatedList.contains("Rocinha"))
        assertFalse(result.updatedList.contains("Cidade de Deus"))
    }

    @Test
    fun syncRegionalRiskAreas_prunesExistingRioDeJaneiroAreas() = runBlocking {
        // Simula a lista anterior do usuário que continha bairros do RJ importados por engano
        val contaminatedList = listOf(
            "Caetetuba",
            "Jacarezinho",
            "Mare",
            "Complexo do Alemao",
            "Rocinha",
            "Meu Ponto Seguro"
        )

        val result = CommunityRiskClient.syncRegionalRiskAreas(
            existingList = contaminatedList,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherStates = true
        ).getOrThrow()

        // Deve remover os 4 bairros do RJ e preservar Caetetuba e Meu Ponto Seguro
        assertTrue(result.removedOtherStateAreas >= 4)
        assertTrue(result.updatedList.contains("Caetetuba"))
        assertTrue(result.updatedList.contains("Meu Ponto Seguro"))
        assertFalse(result.updatedList.contains("Jacarezinho"))
        assertFalse(result.updatedList.contains("Mare"))
        assertFalse(result.updatedList.contains("Complexo do Alemao"))
        assertFalse(result.updatedList.contains("Rocinha"))
    }

    @Test
    fun pruneUnrelatedStateAreas_removesOnlyNonSPAreas() {
        val list = listOf("Caetetuba", "Portão", "Jacarezinho", "Pedreira Prado Lopes", "Outro Bairro")
        val cleaned = CommunityRiskClient.pruneUnrelatedStateAreas(list)

        assertEquals(listOf("Caetetuba", "Portão", "Outro Bairro"), cleaned)
    }
}
