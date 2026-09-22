package br.com.entregador.lucro.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunityRiskClientTest {

    @Test
    fun syncRegionalRiskAreas_forAtibaia_includesOnlyRealDangerousPoints() = runBlocking {
        val existing = listOf("Bairro Personalizado Entregador")
        val result = CommunityRiskClient.syncRegionalRiskAreas(
            existingList = existing,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherStates = true
        ).getOrThrow()

        // Locais genuínos de atenção / risco em Atibaia e vizinhança imediata
        assertTrue(result.updatedList.contains("Caetetuba"))
        assertTrue(result.updatedList.contains("Jardim Imperial"))
        assertTrue(result.updatedList.contains("Henedina Cortez")) // Periferia crítica Bragança
        assertTrue(result.updatedList.contains("Bairro Personalizado Entregador")) // Preserva o customizado

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
    fun syncRegionalRiskAreas_prunesExistingRioDeJaneiroAndSafeAreas() = runBlocking {
        // Simula lista que continha favelas do RJ e bairros seguros de Atibaia adicionados por engano
        val contaminatedList = listOf(
            "Caetetuba",
            "Jacarezinho",
            "Mare",
            "Alvinópolis",
            "Tanque",
            "Portão",
            "Meu Ponto Seguro Customizado"
        )

        val result = CommunityRiskClient.syncRegionalRiskAreas(
            existingList = contaminatedList,
            userCity = "Atibaia",
            userState = "SP",
            autoPruneOtherStates = true
        ).getOrThrow()

        // Deve remover RJ (Jacarezinho, Mare) e bairros seguros comuns (Alvinópolis, Tanque, Portão)
        assertTrue(result.updatedList.contains("Caetetuba"))
        assertTrue(result.updatedList.contains("Meu Ponto Seguro Customizado"))
        assertFalse(result.updatedList.contains("Jacarezinho"))
        assertFalse(result.updatedList.contains("Mare"))
        assertFalse(result.updatedList.contains("Alvinópolis"))
        assertFalse(result.updatedList.contains("Tanque"))
        assertFalse(result.updatedList.contains("Portão"))
    }

    @Test
    fun pruneSafeNeighborhoods_removesOnlySafeNeighborhoods() {
        val list = listOf("Caetetuba", "Alvinópolis", "Tanque", "Jardim Imperial", "Outro Local")
        val cleaned = CommunityRiskClient.pruneSafeNeighborhoods(list)

        assertEquals(listOf("Caetetuba", "Jardim Imperial", "Outro Local"), cleaned)
    }
}
