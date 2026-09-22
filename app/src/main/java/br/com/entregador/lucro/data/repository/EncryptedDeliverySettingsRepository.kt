package br.com.entregador.lucro.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.entregador.lucro.domain.model.DeliverySettings

/**
 * Repositório que salva e recupera as configurações do entregador utilizando
 * [EncryptedSharedPreferences] para máxima segurança de dados locais no Android.
 */
class EncryptedDeliverySettingsRepository(
    context: Context
) : DeliverySettingsRepository {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "Falha ao inicializar EncryptedSharedPreferences, utilizando fallback padrão.", e)
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun getSettings(): DeliverySettings {
        val defaultSettings = DeliverySettings()
        return DeliverySettings(
            vehicleType = prefs.getString(KEY_VEHICLE_TYPE, defaultSettings.vehicleType) ?: defaultSettings.vehicleType,
            fuelPricePerLiter = prefs.getFloat(KEY_FUEL_PRICE, defaultSettings.fuelPricePerLiter.toFloat()).toDouble(),
            fuelConsumptionKmPerLiter = prefs.getFloat(KEY_FUEL_CONSUMPTION, defaultSettings.fuelConsumptionKmPerLiter.toFloat()).toDouble(),
            maintenanceCostPerKm = prefs.getFloat(KEY_MAINTENANCE_COST, defaultSettings.maintenanceCostPerKm.toFloat()).toDouble(),
            restaurantWaitBufferMinutes = prefs.getInt(KEY_WAIT_BUFFER, defaultSettings.restaurantWaitBufferMinutes),
            targetMinPerKm = prefs.getFloat(KEY_TARGET_KM, defaultSettings.targetMinPerKm.toFloat()).toDouble(),
            targetMinPerHour = prefs.getFloat(KEY_TARGET_HOUR, defaultSettings.targetMinPerHour.toFloat()).toDouble(),
            voiceAlertsEnabled = prefs.getBoolean(KEY_VOICE_ALERTS, defaultSettings.voiceAlertsEnabled),
            autoRejectRedOffers = prefs.getBoolean(KEY_AUTO_REJECT_RED, defaultSettings.autoRejectRedOffers),
            autoRejectDelaySeconds = prefs.getInt(KEY_AUTO_REJECT_DELAY, defaultSettings.autoRejectDelaySeconds),
            minGrossValueFloor = prefs.getFloat(KEY_MIN_GROSS_VALUE, defaultSettings.minGrossValueFloor.toFloat()).toDouble(),
            maxDistanceKm = prefs.getFloat(KEY_MAX_DISTANCE, defaultSettings.maxDistanceKm.toFloat()).toDouble(),
            emptyReturnPercent = prefs.getFloat(KEY_EMPTY_RETURN_PERCENT, defaultSettings.emptyReturnPercent.toFloat()).toDouble(),
            riskAreasEnabled = prefs.getBoolean(KEY_RISK_AREAS_ENABLED, defaultSettings.riskAreasEnabled),
            riskAreasList = parseRiskAreasList(prefs.getString(KEY_RISK_AREAS_LIST, null)),
            autoRejectRiskAreas = prefs.getBoolean(KEY_AUTO_REJECT_RISK_AREAS, defaultSettings.autoRejectRiskAreas),
            dailyRevenueGoal = prefs.getFloat(KEY_DAILY_REVENUE_GOAL, defaultSettings.dailyRevenueGoal.toFloat()).toDouble(),
            autoAcceptGreenOffers = prefs.getBoolean(KEY_AUTO_ACCEPT_GREEN, defaultSettings.autoAcceptGreenOffers),
            autoAcceptDelaySeconds = prefs.getInt(KEY_AUTO_ACCEPT_DELAY, defaultSettings.autoAcceptDelaySeconds),
            soundAlertsEnabled = prefs.getBoolean(KEY_SOUND_ALERTS, defaultSettings.soundAlertsEnabled),
            rainModeEnabled = prefs.getBoolean(KEY_RAIN_MODE_ENABLED, defaultSettings.rainModeEnabled),
            rainFloorBonus = prefs.getFloat(KEY_RAIN_FLOOR_BONUS, defaultSettings.rainFloorBonus.toFloat()).toDouble(),
            fuelStateCode = prefs.getString(KEY_FUEL_STATE_CODE, defaultSettings.fuelStateCode) ?: defaultSettings.fuelStateCode,
            bikeElevationAlertEnabled = prefs.getBoolean(KEY_BIKE_ELEVATION_ENABLED, defaultSettings.bikeElevationAlertEnabled),
            userCity = prefs.getString(KEY_USER_CITY, defaultSettings.userCity) ?: defaultSettings.userCity,
            userState = prefs.getString(KEY_USER_STATE, defaultSettings.userState) ?: defaultSettings.userState
        )
    }

    override fun saveSettings(settings: DeliverySettings) {
        prefs.edit()
            .putString(KEY_VEHICLE_TYPE, settings.vehicleType)
            .putFloat(KEY_FUEL_PRICE, settings.fuelPricePerLiter.toFloat())
            .putFloat(KEY_FUEL_CONSUMPTION, settings.fuelConsumptionKmPerLiter.toFloat())
            .putFloat(KEY_MAINTENANCE_COST, settings.maintenanceCostPerKm.toFloat())
            .putInt(KEY_WAIT_BUFFER, settings.restaurantWaitBufferMinutes)
            .putFloat(KEY_TARGET_KM, settings.targetMinPerKm.toFloat())
            .putFloat(KEY_TARGET_HOUR, settings.targetMinPerHour.toFloat())
            .putBoolean(KEY_VOICE_ALERTS, settings.voiceAlertsEnabled)
            .putBoolean(KEY_AUTO_REJECT_RED, settings.autoRejectRedOffers)
            .putInt(KEY_AUTO_REJECT_DELAY, settings.autoRejectDelaySeconds)
            .putFloat(KEY_MIN_GROSS_VALUE, settings.minGrossValueFloor.toFloat())
            .putFloat(KEY_MAX_DISTANCE, settings.maxDistanceKm.toFloat())
            .putFloat(KEY_EMPTY_RETURN_PERCENT, settings.emptyReturnPercent.toFloat())
            .putBoolean(KEY_RISK_AREAS_ENABLED, settings.riskAreasEnabled)
            .putString(KEY_RISK_AREAS_LIST, settings.riskAreasList.joinToString(";"))
            .putBoolean(KEY_AUTO_REJECT_RISK_AREAS, settings.autoRejectRiskAreas)
            .putFloat(KEY_DAILY_REVENUE_GOAL, settings.dailyRevenueGoal.toFloat())
            .putBoolean(KEY_AUTO_ACCEPT_GREEN, settings.autoAcceptGreenOffers)
            .putInt(KEY_AUTO_ACCEPT_DELAY, settings.autoAcceptDelaySeconds)
            .putBoolean(KEY_SOUND_ALERTS, settings.soundAlertsEnabled)
            .putBoolean(KEY_RAIN_MODE_ENABLED, settings.rainModeEnabled)
            .putFloat(KEY_RAIN_FLOOR_BONUS, settings.rainFloorBonus.toFloat())
            .putString(KEY_FUEL_STATE_CODE, settings.fuelStateCode)
            .putBoolean(KEY_BIKE_ELEVATION_ENABLED, settings.bikeElevationAlertEnabled)
            .putString(KEY_USER_CITY, settings.userCity)
            .putString(KEY_USER_STATE, settings.userState)
            .apply()
    }

    private fun parseRiskAreasList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        private const val TAG = "EncryptedSettingsRepo"
        private const val SECURE_PREFS_NAME = "secure_delivery_settings"
        private const val FALLBACK_PREFS_NAME = "fallback_delivery_settings"

        private const val KEY_VEHICLE_TYPE = "vehicle_type"
        private const val KEY_FUEL_PRICE = "fuel_price_per_liter"
        private const val KEY_FUEL_CONSUMPTION = "fuel_consumption_km_per_liter"
        private const val KEY_MAINTENANCE_COST = "maintenance_cost_per_km"
        private const val KEY_WAIT_BUFFER = "restaurant_wait_buffer_minutes"
        private const val KEY_TARGET_KM = "target_min_per_km"
        private const val KEY_TARGET_HOUR = "target_min_per_hour"
        private const val KEY_VOICE_ALERTS = "voice_alerts_enabled"
        private const val KEY_AUTO_REJECT_RED = "auto_reject_red_offers"
        private const val KEY_AUTO_REJECT_DELAY = "auto_reject_delay_seconds"
        private const val KEY_MIN_GROSS_VALUE = "min_gross_value_floor"
        private const val KEY_MAX_DISTANCE = "max_distance_km"
        private const val KEY_EMPTY_RETURN_PERCENT = "empty_return_percent"
        private const val KEY_RISK_AREAS_ENABLED = "risk_areas_enabled"
        private const val KEY_RISK_AREAS_LIST = "risk_areas_list"
        private const val KEY_AUTO_REJECT_RISK_AREAS = "auto_reject_risk_areas"
        private const val KEY_DAILY_REVENUE_GOAL = "daily_revenue_goal"
        private const val KEY_AUTO_ACCEPT_GREEN = "auto_accept_green_offers"
        private const val KEY_AUTO_ACCEPT_DELAY = "auto_accept_delay_seconds"
        private const val KEY_SOUND_ALERTS = "sound_alerts_enabled"
        private const val KEY_RAIN_MODE_ENABLED = "rain_mode_enabled"
        private const val KEY_RAIN_FLOOR_BONUS = "rain_floor_bonus"
        private const val KEY_FUEL_STATE_CODE = "fuel_state_code"
        private const val KEY_BIKE_ELEVATION_ENABLED = "bike_elevation_enabled"
        private const val KEY_USER_CITY = "user_city"
        private const val KEY_USER_STATE = "user_state"
    }
}
