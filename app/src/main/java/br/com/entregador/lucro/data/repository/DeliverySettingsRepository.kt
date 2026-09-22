package br.com.entregador.lucro.data.repository

import android.content.Context
import android.content.SharedPreferences
import br.com.entregador.lucro.domain.model.DeliverySettings

/**
 * Interface para persistência e recuperação das configurações operacionais do entregador.
 */
interface DeliverySettingsRepository {
    fun getSettings(): DeliverySettings
    fun saveSettings(settings: DeliverySettings)
}

/**
 * Implementação padrão baseada em [SharedPreferences].
 */
class SharedPreferencesDeliverySettingsRepository(
    context: Context
) : DeliverySettingsRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
            autoRejectRiskAreas = prefs.getBoolean(KEY_AUTO_REJECT_RISK_AREAS, defaultSettings.autoRejectRiskAreas)
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
            .apply()
    }

    private fun parseRiskAreasList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        private const val PREFS_NAME = "delivery_settings_prefs"
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
    }
}
