package br.com.entregador.lucro.data.repository

import br.com.entregador.lucro.domain.model.DailyShiftSummary
import br.com.entregador.lucro.domain.model.DeliveryHistoryRecord

/**
 * Interface para persistência e recuperação do histórico de corridas e faturamento diário.
 */
interface DeliveryHistoryRepository {

    /**
     * Salva uma oferta processada no banco local de histórico.
     */
    fun recordOffer(record: DeliveryHistoryRecord): Long

    /**
     * Retorna o resumo consolidado do turno de hoje (ganhos, km, contagem de semáforos).
     */
    fun getTodaySummary(): DailyShiftSummary

    /**
     * Retorna a lista de todas as ofertas registradas na data de hoje em ordem decrescente de horário.
     */
    fun getTodayRecords(): List<DeliveryHistoryRecord>

    /**
     * Limpa todos os registos do turno de hoje, permitindo ao entregador zerar o dia.
     */
    fun clearTodayRecords()

    /**
     * Retorna o resumo consolidado para um período entre [startDate] e [endDate] (formato "yyyy-MM-dd").
     */
    fun getSummaryForDateRange(startDate: String, endDate: String): DailyShiftSummary

    /**
     * Retorna a lista de ofertas registradas entre [startDate] e [endDate] (formato "yyyy-MM-dd") em ordem decrescente.
     */
    fun getRecordsForDateRange(startDate: String, endDate: String): List<DeliveryHistoryRecord>

    /**
     * Retorna todas as ofertas do histórico até o limite especificado.
     */
    fun getAllRecords(limit: Int = 200): List<DeliveryHistoryRecord>

    /**
     * Retorna o resumo consolidado de todo o histórico registrado.
     */
    fun getAllTimeSummary(): DailyShiftSummary
}
