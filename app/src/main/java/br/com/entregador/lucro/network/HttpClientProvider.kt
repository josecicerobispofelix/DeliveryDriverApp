package br.com.entregador.lucro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Provedor HTTP leve e resiliente para comunicação com APIs externas.
 * Utiliza [HttpURLConnection] nativo do Android com timeouts estritos para garantir
 * que a interface do usuário nunca trave ou espere por conexões lentas.
 */
object HttpClientProvider {

    private const val CONNECT_TIMEOUT_MS = 3500
    private const val READ_TIMEOUT_MS = 4500
    private const val USER_AGENT = "KMCERTO-App/1.0.0 (Android; DeliveryDriver)"

    suspend fun get(urlString: String): Result<String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
                val response = reader.use { it.readText() }
                Result.success(response)
            } else {
                Result.failure(Exception("HTTP $responseCode: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
