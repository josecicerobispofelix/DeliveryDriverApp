package br.com.entregador.lucro.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.entregador.lucro.domain.model.DeliverySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.deliveryDataStore: DataStore<Preferences> by preferencesDataStore(name = "delivery_settings_ds")

/**
 * Repositório reativo que gerencia as configurações do entregador utilizando
 * o Jetpack DataStore com suporte a Kotlin Coroutines e Flow.
 */
class DataStoreDeliverySettingsRepository(
    private val context: Context
) {

    val settingsFlow: Flow<DeliverySettings> = context.deliveryDataStore.data.map { preferences ->
        val defaultSettings = DeliverySettings()
        DeliverySettings(
            vehicleType = preferences[KEY_VEHICLE_TYPE] ?: defaultSettings.vehicleType,
            fuelPricePerLiter = preferences[KEY_FUEL_PRICE] ?: defaultSettings.fuelPricePerLiter,
            fuelConsumptionKmPerLiter = preferences[KEY_FUEL_CONSUMPTION] ?: defaultSettings.fuelConsumptionKmPerLiter,
            maintenanceCostPerKm = preferences[KEY_MAINTENANCE_COST] ?: defaultSettings.maintenanceCostPerKm,
            restaurantWaitBufferMinutes = preferences[KEY_WAIT_BUFFER] ?: defaultSettings.restaurantWaitBufferMinutes,
            targetMinPerKm = preferences[KEY_TARGET_KM] ?: defaultSettings.targetMinPerKm,
            targetMinPerHour = preferences[KEY_TARGET_HOUR] ?: defaultSettings.targetMinPerHour,
            voiceAlertsEnabled = preferences[KEY_VOICE_ALERTS] ?: defaultSettings.voiceAlertsEnabled,
            autoRejectRedOffers = preferences[KEY_AUTO_REJECT_RED] ?: defaultSettings.autoRejectRedOffers,
            autoRejectDelaySeconds = preferences[KEY_AUTO_REJECT_DELAY] ?: defaultSettings.autoRejectDelaySeconds,
            minGrossValueFloor = preferences[KEY_MIN_GROSS_VALUE] ?: defaultSettings.minGrossValueFloor,
            maxDistanceKm = preferences[KEY_MAX_DISTANCE] ?: defaultSettings.maxDistanceKm,
            emptyReturnPercent = preferences[KEY_EMPTY_RETURN_PERCENT] ?: defaultSettings.emptyReturnPercent,
            riskAreasEnabled = preferences[KEY_RISK_AREAS_ENABLED] ?: defaultSettings.riskAreasEnabled,
            riskAreasList = preferences[KEY_RISK_AREAS_LIST]?.let { raw ->
                if (raw.isBlank()) emptyList() else raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            } ?: defaultSettings.riskAreasList,
            autoRejectRiskAreas = preferences[KEY_AUTO_REJECT_RISK_AREAS] ?: defaultSettings.autoRejectRiskAreas,
            dailyRevenueGoal = preferences[KEY_DAILY_REVENUE_GOAL] ?: defaultSettings.dailyRevenueGoal,
            autoAcceptGreenOffers = preferences[KEY_AUTO_ACCEPT_GREEN] ?: defaultSettings.autoAcceptGreenOffers,
            autoAcceptDelaySeconds = preferences[KEY_AUTO_ACCEPT_DELAY] ?: defaultSettings.autoAcceptDelaySeconds,
            soundAlertsEnabled = preferences[KEY_SOUND_ALERTS] ?: defaultSettings.soundAlertsEnabled
        )
    }

    suspend fun getSettings(): DeliverySettings {
        return settingsFlow.first()
    }

    suspend fun saveSettings(settings: DeliverySettings) {
        context.deliveryDataStore.edit { preferences ->
            preferences[KEY_VEHICLE_TYPE] = settings.vehicleType
            preferences[KEY_FUEL_PRICE] = settings.fuelPricePerLiter
            preferences[KEY_FUEL_CONSUMPTION] = settings.fuelConsumptionKmPerLiter
            preferences[KEY_MAINTENANCE_COST] = settings.maintenanceCostPerKm
            preferences[KEY_WAIT_BUFFER] = settings.restaurantWaitBufferMinutes
            preferences[KEY_TARGET_KM] = settings.targetMinPerKm
            preferences[KEY_TARGET_HOUR] = settings.targetMinPerHour
            preferences[KEY_VOICE_ALERTS] = settings.voiceAlertsEnabled
            preferences[KEY_AUTO_REJECT_RED] = settings.autoRejectRedOffers
            preferences[KEY_AUTO_REJECT_DELAY] = settings.autoRejectDelaySeconds
            preferences[KEY_MIN_GROSS_VALUE] = settings.minGrossValueFloor
            preferences[KEY_MAX_DISTANCE] = settings.maxDistanceKm
            preferences[KEY_EMPTY_RETURN_PERCENT] = settings.emptyReturnPercent
            preferences[KEY_RISK_AREAS_ENABLED] = settings.riskAreasEnabled
            preferences[KEY_RISK_AREAS_LIST] = settings.riskAreasList.joinToString(";")
            preferences[KEY_AUTO_REJECT_RISK_AREAS] = settings.autoRejectRiskAreas
            preferences[KEY_DAILY_REVENUE_GOAL] = settings.dailyRevenueGoal
            preferences[KEY_AUTO_ACCEPT_GREEN] = settings.autoAcceptGreenOffers
            preferences[KEY_AUTO_ACCEPT_DELAY] = settings.autoAcceptDelaySeconds
            preferences[KEY_SOUND_ALERTS] = settings.soundAlertsEnabled
        }
    }

    companion object {
        val KEY_VEHICLE_TYPE = stringPreferencesKey("vehicle_type")
        val KEY_FUEL_PRICE = doublePreferencesKey("fuel_price_per_liter")
        val KEY_FUEL_CONSUMPTION = doublePreferencesKey("fuel_consumption_km_per_liter")
        val KEY_MAINTENANCE_COST = doublePreferencesKey("maintenance_cost_per_km")
        val KEY_WAIT_BUFFER = intPreferencesKey("restaurant_wait_buffer_minutes")
        val KEY_TARGET_KM = doublePreferencesKey("target_min_per_km")
        val KEY_TARGET_HOUR = doublePreferencesKey("target_min_per_hour")
        val KEY_VOICE_ALERTS = booleanPreferencesKey("voice_alerts_enabled")
        val KEY_AUTO_REJECT_RED = booleanPreferencesKey("auto_reject_red_offers")
        val KEY_AUTO_REJECT_DELAY = intPreferencesKey("auto_reject_delay_seconds")
        val KEY_MIN_GROSS_VALUE = doublePreferencesKey("min_gross_value_floor")
        val KEY_MAX_DISTANCE = doublePreferencesKey("max_distance_km")
        val KEY_EMPTY_RETURN_PERCENT = doublePreferencesKey("empty_return_percent")
        val KEY_RISK_AREAS_ENABLED = booleanPreferencesKey("risk_areas_enabled")
        val KEY_RISK_AREAS_LIST = stringPreferencesKey("risk_areas_list")
        val KEY_AUTO_REJECT_RISK_AREAS = booleanPreferencesKey("auto_reject_risk_areas")
        val KEY_DAILY_REVENUE_GOAL = doublePreferencesKey("daily_revenue_goal")
        val KEY_AUTO_ACCEPT_GREEN = booleanPreferencesKey("auto_accept_green_offers")
        val KEY_AUTO_ACCEPT_DELAY = intPreferencesKey("auto_accept_delay_seconds")
        val KEY_SOUND_ALERTS = booleanPreferencesKey("sound_alerts_enabled")
    }
}
