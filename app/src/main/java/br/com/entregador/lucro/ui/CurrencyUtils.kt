package br.com.entregador.lucro.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utilitários para formatação e manipulação de valores monetários no formato brasileiro (R$ #.##0,00).
 * Permite inserção automática de vírgula e ponto conforme o entregador digita os números.
 */
object CurrencyUtils {
    private val symbols = DecimalFormatSymbols(Locale("pt", "BR"))
    private val decimalFormat = DecimalFormat("#,##0.00", symbols)

    /**
     * Converte uma string formatada (ex: "7,00", "15,50", "1.250,00") para Double.
     * Retorna 0.0 se for nula ou vazia.
     */
    fun parseCurrency(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        val clean = text.replace("[^0-9]".toRegex(), "")
        return if (clean.isEmpty()) 0.0 else clean.toDouble() / 100.0
    }

    /**
     * Converte um valor numérico Double para string formatada em Real (ex: 7.0 -> "7,00", 1250.5 -> "1.250,50").
     */
    fun formatCurrency(value: Double): String {
        return decimalFormat.format(value)
    }

    /**
     * Recebe uma sequência de dígitos digitados e formata automaticamente com vírgula e ponto.
     * Exemplo:
     * "7" -> "0,07"
     * "70" -> "0,70"
     * "700" -> "7,00"
     * "7000" -> "70,00"
     * "100000" -> "1.000,00"
     */
    fun formatDigitsToCurrency(digitsOnly: String): String {
        val clean = digitsOnly.replace("[^0-9]".toRegex(), "")
        // Limita a 10 dígitos para evitar estouro (até R$ 99.999.999,99)
        val safe = if (clean.length > 10) clean.substring(0, 10) else clean
        val value = if (safe.isEmpty()) 0.0 else safe.toDouble() / 100.0
        return decimalFormat.format(value)
    }
}

/**
 * TextWatcher que aplica máscara monetária em tempo real ao EditText.
 * Conforme o usuário digita os dígitos, a vírgula dos centavos e o ponto de milhar entram automaticamente.
 */
class CurrencyTextWatcher(
    private val editText: EditText,
    private val onValueChanged: ((Double) -> Unit)? = null
) : TextWatcher {

    private var isUpdating = false

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isUpdating) return

        isUpdating = true
        try {
            val originalString = s?.toString() ?: ""
            val formatted = CurrencyUtils.formatDigitsToCurrency(originalString)

            if (formatted != originalString) {
                editText.setText(formatted)
                editText.setSelection(formatted.length)
            }
            val value = CurrencyUtils.parseCurrency(formatted)
            onValueChanged?.invoke(value)
        } finally {
            isUpdating = false
        }
    }
}
