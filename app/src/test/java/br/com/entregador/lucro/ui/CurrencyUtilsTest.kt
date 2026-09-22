package br.com.entregador.lucro.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyUtilsTest {

    @Test
    fun `formatDigitsToCurrency formats typing progression correctly`() {
        assertEquals("0,00", CurrencyUtils.formatDigitsToCurrency(""))
        assertEquals("0,07", CurrencyUtils.formatDigitsToCurrency("7"))
        assertEquals("0,70", CurrencyUtils.formatDigitsToCurrency("70"))
        assertEquals("7,00", CurrencyUtils.formatDigitsToCurrency("700"))
        assertEquals("70,00", CurrencyUtils.formatDigitsToCurrency("7000"))
        assertEquals("700,00", CurrencyUtils.formatDigitsToCurrency("70000"))
        assertEquals("1.000,00", CurrencyUtils.formatDigitsToCurrency("100000"))
        assertEquals("12.500,75", CurrencyUtils.formatDigitsToCurrency("1250075"))
    }

    @Test
    fun `parseCurrency converts Brazilian currency string to double`() {
        assertEquals(0.0, CurrencyUtils.parseCurrency(null), 0.001)
        assertEquals(0.0, CurrencyUtils.parseCurrency(""), 0.001)
        assertEquals(0.0, CurrencyUtils.parseCurrency("0,00"), 0.001)
        assertEquals(7.0, CurrencyUtils.parseCurrency("7,00"), 0.001)
        assertEquals(15.50, CurrencyUtils.parseCurrency("15,50"), 0.001)
        assertEquals(1000.0, CurrencyUtils.parseCurrency("1.000,00"), 0.001)
        assertEquals(1250.75, CurrencyUtils.parseCurrency("1.250,75"), 0.001)
    }

    @Test
    fun `formatCurrency converts double to Brazilian currency format`() {
        assertEquals("0,00", CurrencyUtils.formatCurrency(0.0))
        assertEquals("7,00", CurrencyUtils.formatCurrency(7.0))
        assertEquals("15,50", CurrencyUtils.formatCurrency(15.5))
        assertEquals("1.000,00", CurrencyUtils.formatCurrency(1000.0))
        assertEquals("1.250,75", CurrencyUtils.formatCurrency(1250.75))
    }
}
