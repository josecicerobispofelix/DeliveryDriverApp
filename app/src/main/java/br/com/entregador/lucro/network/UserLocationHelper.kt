package br.com.entregador.lucro.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Utilitário para detecção inteligente da localização do entregador (cidade, estado e coordenadas).
 * Utilizado para filtrar áreas de risco locais/vizinhas e consultar clima e altimetria precisos.
 */
object UserLocationHelper {

    const val DEFAULT_CITY = "Atibaia"
    const val DEFAULT_STATE = "SP"
    const val DEFAULT_LATITUDE = -23.1189
    const val DEFAULT_LONGITUDE = -46.5533

    data class LocationInfo(
        val city: String,
        val state: String,
        val latitude: Double,
        val longitude: Double,
        val isGpsAccurate: Boolean
    ) {
        val displayName: String
            get() = "$city - $state"
    }

    /**
     * Tenta detectar a localização do usuário via GPS/Rede do Android.
     * Caso não possua permissão ou não encontre sinal, retorna a cidade padrão (Atibaia - SP).
     */
    @SuppressLint("MissingPermission")
    suspend fun detectUserLocation(context: Context): LocationInfo = withContext(Dispatchers.IO) {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse && !hasFine) {
            return@withContext LocationInfo(
                city = DEFAULT_CITY,
                state = DEFAULT_STATE,
                latitude = DEFAULT_LATITUDE,
                longitude = DEFAULT_LONGITUDE,
                isGpsAccurate = false
            )
        }

        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            var bestLocation: Location? = null

            if (locationManager != null) {
                val providers = locationManager.getProviders(true)
                for (provider in providers) {
                    val l = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                        bestLocation = l
                    }
                }
            }

            if (bestLocation != null) {
                val lat = bestLocation.latitude
                val lon = bestLocation.longitude

                // Tenta Geocodificação reversa para extrair cidade e estado
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                val addresses = try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)
                } catch (_: Exception) {
                    null
                }

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val detectedCity = address.subAdminArea ?: address.locality ?: address.subLocality ?: DEFAULT_CITY
                    val detectedState = address.adminArea ?: DEFAULT_STATE
                    val ufCode = mapStateToUf(detectedState)

                    return@withContext LocationInfo(
                        city = detectedCity,
                        state = ufCode,
                        latitude = lat,
                        longitude = lon,
                        isGpsAccurate = true
                    )
                }

                return@withContext LocationInfo(
                    city = DEFAULT_CITY,
                    state = DEFAULT_STATE,
                    latitude = lat,
                    longitude = lon,
                    isGpsAccurate = true
                )
            }
        } catch (_: Exception) {
            // Em caso de erro, usa o fallback de Atibaia - SP
        }

        LocationInfo(
            city = DEFAULT_CITY,
            state = DEFAULT_STATE,
            latitude = DEFAULT_LATITUDE,
            longitude = DEFAULT_LONGITUDE,
            isGpsAccurate = false
        )
    }

    private fun mapStateToUf(stateName: String): String {
        val lower = stateName.trim().lowercase()
        return when {
            lower.contains("são paulo") || lower.contains("sao paulo") || lower == "sp" -> "SP"
            lower.contains("rio de janeiro") || lower == "rj" -> "RJ"
            lower.contains("minas gerais") || lower == "mg" -> "MG"
            lower.contains("paraná") || lower.contains("parana") || lower == "pr" -> "PR"
            lower.length == 2 -> stateName.uppercase()
            else -> DEFAULT_STATE
        }
    }
}
