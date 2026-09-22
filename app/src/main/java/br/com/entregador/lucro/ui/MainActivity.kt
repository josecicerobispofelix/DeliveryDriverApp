package br.com.entregador.lucro.ui

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import br.com.entregador.lucro.R
import br.com.entregador.lucro.data.repository.DeliveryHistoryRepository
import br.com.entregador.lucro.data.repository.DeliverySettingsRepository
import br.com.entregador.lucro.data.repository.EncryptedDeliverySettingsRepository
import br.com.entregador.lucro.data.repository.SQLiteDeliveryHistoryRepository
import br.com.entregador.lucro.domain.calculator.DeliveryCalculator
import br.com.entregador.lucro.domain.model.DeliveryHistoryRecord
import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.DeliverySettings
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import br.com.entregador.lucro.network.CommunityRiskClient
import br.com.entregador.lucro.network.ElevationClient
import br.com.entregador.lucro.network.FuelPriceClient
import br.com.entregador.lucro.network.UserLocationHelper
import br.com.entregador.lucro.network.WeatherClient
import br.com.entregador.lucro.service.DeliveryAccessibilityService
import br.com.entregador.lucro.service.OverlayService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela principal (Dashboard Compacto) do aplicativo KmCerto.
 * Cada funcionalidade possui um item compacto com botão direto de ativar/desativar (Switch ON/OFF).
 * Ao tocar no item/card, abre-se um diálogo modal explicando "Do que se trata" e permitindo
 * configurar parâmetros operacionais, mantendo a tela inicial limpa e sem rolagem excessiva.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settingsRepository: DeliverySettingsRepository
    private lateinit var historyRepository: DeliveryHistoryRepository

    // Estado em memória das configurações
    private var cachedSettings: DeliverySettings = DeliverySettings()
    private val riskAreas = mutableListOf<String>()

    // Views do Resumo do Turno de Hoje (Dashboard Diário)
    private lateinit var tvDailyDate: TextView
    private lateinit var tvDailyNetProfit: TextView
    private lateinit var tvDailyMetrics: TextView
    private lateinit var tvDailyGreenCount: TextView
    private lateinit var tvDailyYellowCount: TextView
    private lateinit var tvDailyRedCount: TextView
    private lateinit var btnResetDailyShift: MaterialButton
    private lateinit var btnEditDailyGoal: MaterialButton
    private lateinit var btnOpenHistory: MaterialButton
    private lateinit var pbDailyGoal: LinearProgressIndicator
    private lateinit var tvDailyGoalProgress: TextView
    private var currentDailyGoal: Double = 200.0

    // Linha 1: Ícone Flutuante na Tela
    private lateinit var cardFloatingBubble: MaterialCardView
    private lateinit var swFloatingBubble: SwitchMaterial
    private lateinit var tvFloatingStatus: TextView

    // Linha 2: Recusa Automática
    private lateinit var cardAutoReject: MaterialCardView
    private lateinit var swAutoRejectRed: SwitchMaterial
    private lateinit var tvAutoRejectStatus: TextView

    // Linha 3: Aceite Automático
    private lateinit var cardAutoAccept: MaterialCardView
    private lateinit var swAutoAcceptEnabled: SwitchMaterial
    private lateinit var tvAutoAcceptStatus: TextView

    // Linha 4: Filtros Anti-Prejuízo
    private lateinit var cardAntiWaste: MaterialCardView
    private lateinit var swAntiWaste: SwitchMaterial
    private lateinit var tvAntiWasteStatus: TextView

    // Linha 5: Modo Chuva
    private lateinit var cardRainMode: MaterialCardView
    private lateinit var swRainMode: SwitchMaterial
    private lateinit var tvRainStatus: TextView

    // Linha 6: Áreas de Risco
    private lateinit var cardRiskAreas: MaterialCardView
    private lateinit var swRiskAreasEnabled: SwitchMaterial
    private lateinit var tvRiskAreasStatus: TextView

    // Linha 7: Veículo & Custos
    private lateinit var cardVehicleFuel: MaterialCardView
    private lateinit var tvVehicleFuelStatus: TextView

    // Linha 8: Alertas Sonoros & Simulação
    private lateinit var cardAlertsTest: MaterialCardView
    private lateinit var swVoiceAlerts: SwitchMaterial
    private lateinit var tvAlertsStatus: TextView

    // Linha 9: Acessibilidade
    private lateinit var cardProminentDisclosure: MaterialCardView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvDisclosureBadge: TextView

    private lateinit var tvStorageStatus: TextView

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            triggerGpsDetection()
        } else {
            Toast.makeText(this, "Permissão de GPS não concedida. Usando cidade padrão.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa os repositórios (EncryptedSharedPreferences AES-256 e SQLite)
        settingsRepository = EncryptedDeliverySettingsRepository(this)
        historyRepository = SQLiteDeliveryHistoryRepository(this)

        initViews()
        cachedSettings = settingsRepository.getSettings()
        loadSettingsIntoUi(cachedSettings)
        updateFloatingBubbleUi()
        updateDailySummaryUi()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatusUi()
        updateFloatingBubbleUi()
        updateDailySummaryUi()
    }

    private fun initViews() {
        // Resumo do Turno
        tvDailyDate = findViewById(R.id.tvDailyDate)
        tvDailyNetProfit = findViewById(R.id.tvDailyNetProfit)
        tvDailyMetrics = findViewById(R.id.tvDailyMetrics)
        tvDailyGreenCount = findViewById(R.id.tvDailyGreenCount)
        tvDailyYellowCount = findViewById(R.id.tvDailyYellowCount)
        tvDailyRedCount = findViewById(R.id.tvDailyRedCount)
        btnResetDailyShift = findViewById(R.id.btnResetDailyShift)
        btnEditDailyGoal = findViewById(R.id.btnEditDailyGoal)
        btnOpenHistory = findViewById(R.id.btnOpenHistory)
        pbDailyGoal = findViewById(R.id.pbDailyGoal)
        tvDailyGoalProgress = findViewById(R.id.tvDailyGoalProgress)

        // Cards e Switches Compactos
        cardFloatingBubble = findViewById(R.id.cardFloatingBubble)
        swFloatingBubble = findViewById(R.id.swFloatingBubble)
        tvFloatingStatus = findViewById(R.id.tvFloatingStatus)

        cardAutoReject = findViewById(R.id.cardAutoReject)
        swAutoRejectRed = findViewById(R.id.swAutoRejectRed)
        tvAutoRejectStatus = findViewById(R.id.tvAutoRejectStatus)

        cardAutoAccept = findViewById(R.id.cardAutoAccept)
        swAutoAcceptEnabled = findViewById(R.id.swAutoAcceptEnabled)
        tvAutoAcceptStatus = findViewById(R.id.tvAutoAcceptStatus)

        cardAntiWaste = findViewById(R.id.cardAntiWaste)
        swAntiWaste = findViewById(R.id.swAntiWaste)
        tvAntiWasteStatus = findViewById(R.id.tvAntiWasteStatus)

        cardRainMode = findViewById(R.id.cardRainMode)
        swRainMode = findViewById(R.id.swRainMode)
        tvRainStatus = findViewById(R.id.tvRainStatus)

        cardRiskAreas = findViewById(R.id.cardRiskAreas)
        swRiskAreasEnabled = findViewById(R.id.swRiskAreasEnabled)
        tvRiskAreasStatus = findViewById(R.id.tvRiskAreasStatus)

        cardVehicleFuel = findViewById(R.id.cardVehicleFuel)
        tvVehicleFuelStatus = findViewById(R.id.tvVehicleFuelStatus)

        cardAlertsTest = findViewById(R.id.cardAlertsTest)
        swVoiceAlerts = findViewById(R.id.swVoiceAlerts)
        tvAlertsStatus = findViewById(R.id.tvAlertsStatus)

        cardProminentDisclosure = findViewById(R.id.cardProminentDisclosure)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvDisclosureBadge = findViewById(R.id.tvDisclosureBadge)

        tvStorageStatus = findViewById(R.id.tvStorageStatus)
    }

    private fun setupListeners() {
        // Zerar Resumo do Turno
        btnResetDailyShift.setOnClickListener {
            showResetDailyShiftDialog()
        }

        // Editar Meta Diária
        btnEditDailyGoal.setOnClickListener {
            showEditDailyGoalDialog()
        }

        // Abrir Tela de Histórico Completo & Relatórios
        btnOpenHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // 1. Ícone Flutuante
        attachFloatingBubbleListener()
        cardFloatingBubble.setOnClickListener {
            showFloatingBubbleDialog()
        }

        // 2. Recusa Automática: switch direto e card abre popup
        swAutoRejectRed.setOnCheckedChangeListener { _, isChecked ->
            cachedSettings = cachedSettings.copy(autoRejectRedOffers = isChecked)
            settingsRepository.saveSettings(cachedSettings)
            updateAutoRejectUi(isChecked)
            val msg = if (isChecked) "🔴 Recusa automática ATIVADA (Vermelho)" else "Recusa automática DESATIVADA"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        cardAutoReject.setOnClickListener {
            showAutoRejectDialog()
        }

        // 3. Aceite Automático: switch direto e card abre popup
        swAutoAcceptEnabled.setOnCheckedChangeListener { _, isChecked ->
            cachedSettings = cachedSettings.copy(autoAcceptGreenOffers = isChecked)
            settingsRepository.saveSettings(cachedSettings)
            updateAutoAcceptUi(isChecked)
            val msg = if (isChecked) "🟢 Aceite automático ATIVADO (Verde)" else "Aceite automático DESATIVADO"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        cardAutoAccept.setOnClickListener {
            showAutoAcceptDialog()
        }

        // 4. Filtros Anti-Prejuízo: switch direto e card abre popup
        swAntiWaste.setOnCheckedChangeListener { _, isChecked ->
            cachedSettings = if (isChecked) {
                if (cachedSettings.minGrossValueFloor == 0.0 && cachedSettings.maxDistanceKm == 0.0) {
                    cachedSettings.copy(minGrossValueFloor = 7.00, maxDistanceKm = 15.0, emptyReturnPercent = 20.0)
                } else {
                    cachedSettings
                }
            } else {
                cachedSettings.copy(minGrossValueFloor = 0.0, maxDistanceKm = 0.0, emptyReturnPercent = 0.0)
            }
            settingsRepository.saveSettings(cachedSettings)
            updateAntiWasteUi()
            val msg = if (isChecked) "🛡️ Filtros Anti-Prejuízo ATIVADOS!" else "Filtros Anti-Prejuízo DESATIVADOS"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        cardAntiWaste.setOnClickListener {
            showAntiWasteDialog()
        }

        // 5. Modo Chuva: switch direto e card abre popup
        swRainMode.setOnCheckedChangeListener { _, isChecked ->
            cachedSettings = cachedSettings.copy(rainModeEnabled = isChecked)
            settingsRepository.saveSettings(cachedSettings)
            DeliveryAccessibilityService.updateWeatherStatus(
                isChecked,
                if (isChecked) "🌧️ Chuva (Manual)" else "☀️ Tempo Firme (Manual)"
            )
            updateRainModeUi(isChecked)
            val msg = if (isChecked) "🌧️ Piso dinâmico de chuva ATIVADO!" else "Piso dinâmico de chuva DESATIVADO"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        cardRainMode.setOnClickListener {
            showRainModeDialog()
        }

        // 6. Áreas de Risco: switch direto e card abre popup
        swRiskAreasEnabled.setOnCheckedChangeListener { _, isChecked ->
            cachedSettings = cachedSettings.copy(riskAreasEnabled = isChecked)
            settingsRepository.saveSettings(cachedSettings)
            updateRiskAreasUi(isChecked)
            val msg = if (isChecked) "🚨 Monitoramento de Áreas de Risco ATIVADO!" else "Monitoramento de Áreas de Risco DESATIVADO"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        cardRiskAreas.setOnClickListener {
            showRiskAreasDialog()
        }

        // 7. Veículo & Custos: card abre popup
        cardVehicleFuel.setOnClickListener {
            showVehicleFuelDialog()
        }

        // 8. Alertas Sonoros: switch direto e card abre popup
        swVoiceAlerts.setOnCheckedChangeListener { _, isChecked ->
            cachedSettings = cachedSettings.copy(voiceAlertsEnabled = isChecked)
            settingsRepository.saveSettings(cachedSettings)
            updateAlertsUi()
            val msg = if (isChecked) "Avisos por voz ATIVADOS" else "Avisos por voz DESATIVADOS"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        cardAlertsTest.setOnClickListener {
            showAlertsTestDialog()
        }

        // 9. Acessibilidade: card abre popup
        cardProminentDisclosure.setOnClickListener {
            showAccessibilityDialog()
        }
    }

    private fun loadSettingsIntoUi(settings: DeliverySettings) {
        cachedSettings = settings
        currentDailyGoal = settings.dailyRevenueGoal

        // 1. Bolha
        updateFloatingBubbleUi()

        // 2. Recusa Automática
        swAutoRejectRed.isChecked = settings.autoRejectRedOffers
        updateAutoRejectUi(settings.autoRejectRedOffers)

        // 3. Aceite Automático
        swAutoAcceptEnabled.isChecked = settings.autoAcceptGreenOffers
        updateAutoAcceptUi(settings.autoAcceptGreenOffers)

        // 4. Anti-Prejuízo
        val hasAntiWaste = settings.minGrossValueFloor > 0.0 || settings.maxDistanceKm > 0.0 || settings.emptyReturnPercent > 0.0
        swAntiWaste.isChecked = hasAntiWaste
        updateAntiWasteUi()

        // 5. Chuva
        swRainMode.isChecked = settings.rainModeEnabled
        updateRainModeUi(settings.rainModeEnabled)

        // 6. Áreas de Risco
        riskAreas.clear()
        riskAreas.addAll(settings.riskAreasList)
        swRiskAreasEnabled.isChecked = settings.riskAreasEnabled
        updateRiskAreasUi(settings.riskAreasEnabled)

        // 7. Veículo & Custos
        updateVehicleFuelUi(settings)

        // 8. Alertas
        swVoiceAlerts.isChecked = settings.voiceAlertsEnabled
        updateAlertsUi()

        // 9. Acessibilidade
        updateAccessibilityStatusUi()
    }

    private fun updateAutoRejectUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvAutoRejectStatus.text = "🔴 Ativado (Espera ${cachedSettings.autoRejectDelaySeconds}s • Vermelho)"
            tvAutoRejectStatus.setTextColor(Color.parseColor("#EF4444"))
        } else {
            tvAutoRejectStatus.text = "⚪ Desativado (Toque para detalhes)"
            tvAutoRejectStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateAutoAcceptUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvAutoAcceptStatus.text = "🟢 Ativado (Espera ${cachedSettings.autoAcceptDelaySeconds}s • Verde)"
            tvAutoAcceptStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvAutoAcceptStatus.text = "⚪ Desativado (Toque para detalhes)"
            tvAutoAcceptStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateAntiWasteUi() {
        val hasFloor = cachedSettings.minGrossValueFloor > 0.0
        val hasMax = cachedSettings.maxDistanceKm > 0.0
        if (hasFloor || hasMax || cachedSettings.emptyReturnPercent > 0.0) {
            val parts = mutableListOf<String>()
            if (hasFloor) parts.add("Piso R$ %.2f".format(Locale.US, cachedSettings.minGrossValueFloor))
            if (hasMax) parts.add("Máx %.0f km".format(cachedSettings.maxDistanceKm))
            if (cachedSettings.emptyReturnPercent > 0.0) parts.add("+%.0f%% volta".format(cachedSettings.emptyReturnPercent))
            tvAntiWasteStatus.text = "🟠 Ativo (${parts.joinToString(" • ")})"
            tvAntiWasteStatus.setTextColor(Color.parseColor("#F59E0B"))
            swAntiWaste.isChecked = true
        } else {
            tvAntiWasteStatus.text = "⚪ Desativado (Sem limites mínimos)"
            tvAntiWasteStatus.setTextColor(Color.parseColor("#9CA3AF"))
            swAntiWaste.isChecked = false
        }
    }

    private fun updateRainModeUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvRainStatus.text = "🌧️ Ativo (+R$ %.2f de bônus no piso)".format(Locale.US, cachedSettings.rainFloorBonus)
            tvRainStatus.setTextColor(Color.parseColor("#38BDF8"))
        } else {
            tvRainStatus.text = "☀️ Inativo (Tempo Firme)"
            tvRainStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateRiskAreasUi(isEnabled: Boolean) {
        val city = cachedSettings.userCity.ifEmpty { "Atibaia" }
        if (isEnabled) {
            tvRiskAreasStatus.text = "🚨 Ativo (${riskAreas.size} bairros vigiados em $city)"
            tvRiskAreasStatus.setTextColor(Color.parseColor("#EF4444"))
        } else {
            tvRiskAreasStatus.text = "⚪ Desativado (${riskAreas.size} bairros cadastrados)"
            tvRiskAreasStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateVehicleFuelUi(settings: DeliverySettings) {
        if (settings.isBike) {
            tvVehicleFuelStatus.text = "🚲 Bike • R$ %.2f/km • R$ %.0f/h".format(Locale.US, settings.targetMinPerKm, settings.targetMinPerHour)
        } else {
            tvVehicleFuelStatus.text = "🛵 Moto • Gasolina R$ %.2f • R$ %.2f/km".format(Locale.US, settings.fuelPricePerLiter, settings.targetMinPerKm)
        }
    }

    private fun updateAlertsUi() {
        val voiceStr = if (cachedSettings.voiceAlertsEnabled) "Voz TTS" else "Voz OFF"
        val soundStr = if (cachedSettings.soundAlertsEnabled) "Bipes" else "Bipes OFF"
        tvAlertsStatus.text = "🔊 $voiceStr • $soundStr (Toque p/ simular)"
    }

    private fun setFloatingBubbleStatusUi(active: Boolean) {
        swFloatingBubble.setOnCheckedChangeListener(null)
        swFloatingBubble.isChecked = active

        if (active) {
            tvFloatingStatus.text = "🟢 Ativo (Bolha flutuando na tela)"
            tvFloatingStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvFloatingStatus.text = "⚪ Desativado (Toque para ativar)"
            tvFloatingStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }

        attachFloatingBubbleListener()
    }

    private fun attachFloatingBubbleListener() {
        swFloatingBubble.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    setFloatingBubbleStatusUi(false)
                    showOverlayPermissionDialog()
                } else {
                    OverlayService.start(this)
                    setFloatingBubbleStatusUi(true)
                    Toast.makeText(
                        this,
                        "✓ Ícone flutuante ativo na tela! Pode navegar livremente.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                OverlayService.stop(this)
                setFloatingBubbleStatusUi(false)
                Toast.makeText(this, "Ícone flutuante desativado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFloatingBubbleUi() {
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        val isRunning = hasOverlayPermission && OverlayService.isBubbleActive
        setFloatingBubbleStatusUi(isRunning)
    }

    private fun updateAccessibilityStatusUi() {
        val isEnabled = isAccessibilityServiceEnabled(this, DeliveryAccessibilityService::class.java)
        if (isEnabled) {
            tvAccessibilityStatus.text = "✓ Ativo e monitorando (iFood, Uber e 99)"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#00E676"))
            tvDisclosureBadge.text = "🟢 Ativo"
            tvDisclosureBadge.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvAccessibilityStatus.text = "✕ Desativado (Toque para autorizar)"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#EF4444"))
            tvDisclosureBadge.text = "Ativar ›"
            tvDisclosureBadge.setTextColor(Color.parseColor("#38BDF8"))
        }
    }

    // ==========================================
    // POPUPS MODAIS EXPLICATIVOS ("Do que se trata")
    // ==========================================

    private fun showFloatingBubbleDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_bubble, null)
        val tvStatus = view.findViewById<TextView>(R.id.tvDialogFloatingStatus)
        val btnPerm = view.findViewById<MaterialButton>(R.id.btnDialogOverlayPermission)

        val hasOverlay = Settings.canDrawOverlays(this)
        tvStatus.text = if (hasOverlay) "🟢 Permissão de sobreposição concedida" else "🔴 Permissão necessária para flutuar"
        tvStatus.setTextColor(Color.parseColor(if (hasOverlay) "#00E676" else "#EF4444"))

        btnPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun showAutoRejectDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_auto_reject, null)
        val etDelay = view.findViewById<EditText>(R.id.etDialogAutoRejectDelay)
        etDelay.setText(cachedSettings.autoRejectDelaySeconds.toString())

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val delay = etDelay.text.toString().toIntOrNull() ?: 3
                cachedSettings = cachedSettings.copy(autoRejectDelaySeconds = delay)
                settingsRepository.saveSettings(cachedSettings)
                updateAutoRejectUi(cachedSettings.autoRejectRedOffers)
                Toast.makeText(this, "✓ Tempo de recusa automática atualizado para ${delay}s!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAutoAcceptDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_auto_accept, null)
        val etDelay = view.findViewById<EditText>(R.id.etDialogAutoAcceptDelay)
        etDelay.setText(cachedSettings.autoAcceptDelaySeconds.toString())

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val delay = etDelay.text.toString().toIntOrNull() ?: 1
                cachedSettings = cachedSettings.copy(autoAcceptDelaySeconds = delay)
                settingsRepository.saveSettings(cachedSettings)
                updateAutoAcceptUi(cachedSettings.autoAcceptGreenOffers)
                Toast.makeText(this, "✓ Tempo de aceite automático atualizado para ${delay}s!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAntiWasteDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_anti_waste, null)
        val etFloor = view.findViewById<EditText>(R.id.etDialogMinGrossValue)
        val etMaxDist = view.findViewById<EditText>(R.id.etDialogMaxDistance)
        val etReturn = view.findViewById<EditText>(R.id.etDialogEmptyReturnPercent)

        etFloor.setText(CurrencyUtils.formatCurrency(cachedSettings.minGrossValueFloor))
        etFloor.addTextChangedListener(CurrencyTextWatcher(etFloor))
        etMaxDist.setText(String.format(Locale.US, "%.1f", cachedSettings.maxDistanceKm))
        etReturn.setText(String.format(Locale.US, "%.0f", cachedSettings.emptyReturnPercent))

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val floor = CurrencyUtils.parseCurrency(etFloor.text.toString())
                val maxDist = parseDecimal(etMaxDist.text.toString(), 0.0)
                val retPct = parseDecimal(etReturn.text.toString(), 0.0)
                cachedSettings = cachedSettings.copy(
                    minGrossValueFloor = floor,
                    maxDistanceKm = maxDist,
                    emptyReturnPercent = retPct
                )
                settingsRepository.saveSettings(cachedSettings)
                updateAntiWasteUi()
                Toast.makeText(this, "✓ Filtros Anti-Prejuízo salvos com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRainModeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_rain_mode, null)
        val etBonus = view.findViewById<EditText>(R.id.etDialogRainFloorBonus)
        val tvStatus = view.findViewById<TextView>(R.id.tvDialogRainStatus)
        val btnWeather = view.findViewById<MaterialButton>(R.id.btnDialogCheckWeather)

        etBonus.setText(String.format(Locale.US, "%.2f", cachedSettings.rainFloorBonus))
        tvStatus.text = if (cachedSettings.rainModeEnabled) "🌧️ Chuva Ativa (+R$ %.2f)".format(Locale.US, cachedSettings.rainFloorBonus) else "☀️ Tempo Firme"

        btnWeather.setOnClickListener {
            btnWeather.isEnabled = false
            tvStatus.text = "Consultando satélite Open-Meteo..."
            lifecycleScope.launch {
                try {
                    val weather = WeatherClient.checkWeather().getOrThrow()
                    val raining = weather.isRaining
                    cachedSettings = cachedSettings.copy(rainModeEnabled = raining)
                    settingsRepository.saveSettings(cachedSettings)
                    swRainMode.isChecked = raining
                    updateRainModeUi(raining)
                    val statusDesc = weather.description
                    tvStatus.text = statusDesc
                    DeliveryAccessibilityService.updateWeatherStatus(raining, statusDesc)
                    val alertMsg = if (raining) "🌧️ Chuva detectada! Piso de chuva ativado." else "☀️ Sem chuva detectada ($statusDesc)."
                    Toast.makeText(this@MainActivity, alertMsg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    tvStatus.text = "Erro ao consultar satélite: ${e.message}"
                    Toast.makeText(this@MainActivity, "Falha na consulta: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    btnWeather.isEnabled = true
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val bonus = parseDecimal(etBonus.text.toString(), 3.00)
                cachedSettings = cachedSettings.copy(rainFloorBonus = bonus)
                settingsRepository.saveSettings(cachedSettings)
                updateRainModeUi(cachedSettings.rainModeEnabled)
                Toast.makeText(this, "✓ Bônus de chuva atualizado para R$ %.2f!".format(bonus), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showRiskAreasDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_risk_areas, null)
        val swAutoReject = view.findViewById<SwitchMaterial>(R.id.swDialogAutoRejectRisk)
        val etCity = view.findViewById<EditText>(R.id.etDialogUserCity)
        val etState = view.findViewById<EditText>(R.id.etDialogUserState)
        val btnGps = view.findViewById<MaterialButton>(R.id.btnDialogDetectGps)
        val btnSync = view.findViewById<MaterialButton>(R.id.btnDialogSyncCommunityRisk)
        val btnClear = view.findViewById<MaterialButton>(R.id.btnDialogClearOtherStates)
        val tvSyncStatus = view.findViewById<TextView>(R.id.tvDialogRiskSyncStatus)
        val etAdd = view.findViewById<EditText>(R.id.etDialogAddRiskArea)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnDialogAddRiskArea)
        val tvCount = view.findViewById<TextView>(R.id.tvDialogRiskAreasCount)
        val cg = view.findViewById<ChipGroup>(R.id.cgDialogRiskAreas)

        swAutoReject.isChecked = cachedSettings.autoRejectRiskAreas
        etCity.setText(cachedSettings.userCity)
        etState.setText(cachedSettings.userState)

        fun refreshChips() {
            cg.removeAllViews()
            tvCount.text = "Bairros cadastrados (${riskAreas.size}):"
            for (area in riskAreas) {
                val chip = Chip(this).apply {
                    text = area
                    isCheckable = false
                    isCloseIconVisible = true
                    chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#1E293B"))
                    setTextColor(Color.WHITE)
                    chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                    chipStrokeWidth = 2f
                    closeIconTint = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                    setOnCloseIconClickListener {
                        riskAreas.remove(area)
                        cachedSettings = cachedSettings.copy(riskAreasList = riskAreas.toList())
                        settingsRepository.saveSettings(cachedSettings)
                        refreshChips()
                        updateRiskAreasUi(cachedSettings.riskAreasEnabled)
                    }
                }
                cg.addView(chip)
            }
        }

        refreshChips()

        fun addAreaAction() {
            val name = etAdd.text.toString().trim()
            if (name.isNotBlank()) {
                val exists = riskAreas.any { it.equals(name, ignoreCase = true) }
                if (!exists) {
                    riskAreas.add(name)
                    cachedSettings = cachedSettings.copy(riskAreasList = riskAreas.toList())
                    settingsRepository.saveSettings(cachedSettings)
                    refreshChips()
                    updateRiskAreasUi(cachedSettings.riskAreasEnabled)
                    etAdd.text?.clear()
                    Toast.makeText(this, "Bairro \"$name\" adicionado!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Bairro já cadastrado!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnAdd.setOnClickListener { addAreaAction() }
        etAdd.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addAreaAction()
                true
            } else false
        }

        btnGps.setOnClickListener {
            val hasFine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasFine) {
                lifecycleScope.launch {
                    Toast.makeText(this@MainActivity, "📍 Consultando GPS...", Toast.LENGTH_SHORT).show()
                    val loc = UserLocationHelper.detectUserLocation(this@MainActivity)
                    etCity.setText(loc.city)
                    etState.setText(loc.state)
                    tvSyncStatus.text = "📍 Localização detectada: ${loc.city} - ${loc.state}"
                }
            } else {
                locationPermissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }

        btnSync.setOnClickListener {
            btnSync.isEnabled = false
            val city = etCity.text.toString().trim().ifEmpty { "Atibaia" }
            val state = etState.text.toString().trim().uppercase(Locale.ROOT).ifEmpty { "SP" }
            tvSyncStatus.text = "Sincronizando áreas de risco de $city ($state)..."
            lifecycleScope.launch {
                try {
                    val syncResult = CommunityRiskClient.syncCityRiskAreas(
                        existingList = riskAreas.toList(),
                        userCity = city,
                        userState = state,
                        autoPruneOtherCities = true
                    ).getOrThrow()

                    riskAreas.clear()
                    riskAreas.addAll(syncResult.updatedList)
                    cachedSettings = cachedSettings.copy(
                        riskAreasList = riskAreas.toList(),
                        userCity = city,
                        userState = state
                    )
                    settingsRepository.saveSettings(cachedSettings)
                    refreshChips()
                    updateRiskAreasUi(cachedSettings.riskAreasEnabled)
                    val pruneMsg = if (syncResult.removedOtherCityAreas > 0) " (${syncResult.removedOtherCityAreas} de fora removidos)" else ""
                    tvSyncStatus.text = "✓ Sincronizado para $city! +${syncResult.newAreasAdded} novos$pruneMsg"
                    Toast.makeText(this@MainActivity, "✓ Áreas de risco de $city sincronizadas!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    tvSyncStatus.text = "Falha ao sincronizar: ${e.message}"
                } finally {
                    btnSync.isEnabled = true
                }
            }
        }

        btnClear.setOnClickListener {
            val city = etCity.text.toString().trim().ifEmpty { "Atibaia" }
            val state = etState.text.toString().trim().uppercase(Locale.ROOT).ifEmpty { "SP" }
            val cleaned = CommunityRiskClient.pruneAreasNotInCity(riskAreas.toList(), city, state)
            val removedCount = riskAreas.size - cleaned.size
            if (removedCount > 0) {
                riskAreas.clear()
                riskAreas.addAll(cleaned)
                cachedSettings = cachedSettings.copy(riskAreasList = riskAreas.toList())
                settingsRepository.saveSettings(cachedSettings)
                refreshChips()
                updateRiskAreasUi(cachedSettings.riskAreasEnabled)
                tvSyncStatus.text = "✓ $removedCount bairros de fora limpos. Apenas áreas de $city ativas."
                Toast.makeText(this, "✓ $removedCount bairros de fora removidos!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Sua lista já contém apenas áreas de $city.", Toast.LENGTH_SHORT).show()
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Concluir") { _, _ ->
                cachedSettings = cachedSettings.copy(
                    autoRejectRiskAreas = swAutoReject.isChecked,
                    userCity = etCity.text.toString().trim().ifEmpty { "Atibaia" },
                    userState = etState.text.toString().trim().uppercase(Locale.ROOT).ifEmpty { "SP" },
                    riskAreasList = riskAreas.toList()
                )
                settingsRepository.saveSettings(cachedSettings)
                updateRiskAreasUi(cachedSettings.riskAreasEnabled)
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showVehicleFuelDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_vehicle_fuel, null)
        val rgType = view.findViewById<RadioGroup>(R.id.rgDialogVehicleType)
        val rbMoto = view.findViewById<RadioButton>(R.id.rbDialogMoto)
        val rbBike = view.findViewById<RadioButton>(R.id.rbDialogBike)
        val layoutElevation = view.findViewById<LinearLayout>(R.id.layoutDialogBikeElevation)
        val swElevation = view.findViewById<SwitchMaterial>(R.id.swDialogBikeElevation)
        val tilPrice = view.findViewById<TextInputLayout>(R.id.tilDialogFuelPrice)
        val etPrice = view.findViewById<EditText>(R.id.etDialogFuelPrice)
        val layoutAnp = view.findViewById<LinearLayout>(R.id.layoutDialogFuelAnpSync)
        val etUf = view.findViewById<EditText>(R.id.etDialogFuelStateCode)
        val btnAnp = view.findViewById<MaterialButton>(R.id.btnDialogSyncFuelPrice)
        val tilCons = view.findViewById<TextInputLayout>(R.id.tilDialogFuelConsumption)
        val etCons = view.findViewById<EditText>(R.id.etDialogFuelConsumption)
        val etMaint = view.findViewById<EditText>(R.id.etDialogMaintenanceCost)
        val etWait = view.findViewById<EditText>(R.id.etDialogWaitBuffer)
        val etTargetKm = view.findViewById<EditText>(R.id.etDialogTargetKm)
        val etTargetHour = view.findViewById<EditText>(R.id.etDialogTargetHour)

        if (cachedSettings.isBike) {
            rbBike.isChecked = true
            layoutElevation.visibility = View.VISIBLE
            tilPrice.visibility = View.GONE
            layoutAnp.visibility = View.GONE
            tilCons.visibility = View.GONE
        } else {
            rbMoto.isChecked = true
            layoutElevation.visibility = View.GONE
            tilPrice.visibility = View.VISIBLE
            layoutAnp.visibility = View.VISIBLE
            tilCons.visibility = View.VISIBLE
        }

        rgType.setOnCheckedChangeListener { _, checkedId ->
            val isBike = (checkedId == R.id.rbDialogBike)
            layoutElevation.visibility = if (isBike) View.VISIBLE else View.GONE
            tilPrice.visibility = if (isBike) View.GONE else View.VISIBLE
            layoutAnp.visibility = if (isBike) View.GONE else View.VISIBLE
            tilCons.visibility = if (isBike) View.GONE else View.VISIBLE
        }

        swElevation.isChecked = cachedSettings.bikeElevationAlertEnabled
        etPrice.setText(String.format(Locale.US, "%.2f", cachedSettings.fuelPricePerLiter))
        etUf.setText(cachedSettings.fuelStateCode)
        etCons.setText(String.format(Locale.US, "%.1f", cachedSettings.fuelConsumptionKmPerLiter))
        etMaint.setText(String.format(Locale.US, "%.2f", cachedSettings.maintenanceCostPerKm))
        etWait.setText(cachedSettings.restaurantWaitBufferMinutes.toString())
        etTargetKm.setText(String.format(Locale.US, "%.2f", cachedSettings.targetMinPerKm))
        etTargetHour.setText(String.format(Locale.US, "%.2f", cachedSettings.targetMinPerHour))

        btnAnp.setOnClickListener {
            val uf = etUf.text.toString().trim().uppercase(Locale.ROOT)
            if (uf.length != 2) {
                Toast.makeText(this, "Digite a UF com 2 letras (ex: SP)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnAnp.isEnabled = false
            lifecycleScope.launch {
                try {
                    val quote = FuelPriceClient.getFuelPriceForState(uf)
                    etPrice.setText(String.format(Locale.US, "%.2f", quote.gasolineAverage))
                    Toast.makeText(this@MainActivity, "✓ Preço médio ANP ($uf): R$ %.2f".format(quote.gasolineAverage), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Erro ANP: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    btnAnp.isEnabled = true
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                val isBike = rbBike.isChecked
                val price = parseDecimal(etPrice.text.toString(), 5.80)
                val cons = parseDecimal(etCons.text.toString(), 35.0)
                val maint = parseDecimal(etMaint.text.toString(), 0.12)
                val wait = etWait.text.toString().toIntOrNull() ?: 10
                val targetKm = parseDecimal(etTargetKm.text.toString(), 2.00)
                val targetHour = parseDecimal(etTargetHour.text.toString(), 25.00)
                val uf = etUf.text.toString().trim().uppercase(Locale.ROOT).ifEmpty { "SP" }
                val elev = swElevation.isChecked

                cachedSettings = cachedSettings.copy(
                    vehicleType = if (isBike) DeliverySettings.VEHICLE_BIKE else DeliverySettings.VEHICLE_MOTO,
                    fuelPricePerLiter = price,
                    fuelConsumptionKmPerLiter = cons,
                    maintenanceCostPerKm = maint,
                    restaurantWaitBufferMinutes = wait,
                    targetMinPerKm = targetKm,
                    targetMinPerHour = targetHour,
                    fuelStateCode = uf,
                    bikeElevationAlertEnabled = elev
                )
                settingsRepository.saveSettings(cachedSettings)
                updateVehicleFuelUi(cachedSettings)
                Toast.makeText(this, "✓ Custos e metas operacionais salvos!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAlertsTestDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_alerts_test, null)
        val swVoice = view.findViewById<SwitchMaterial>(R.id.swDialogVoiceAlerts)
        val btnVoice = view.findViewById<MaterialButton>(R.id.btnDialogTestVoice)
        val swSound = view.findViewById<SwitchMaterial>(R.id.swDialogSoundAlerts)
        val btnSound = view.findViewById<MaterialButton>(R.id.btnDialogTestSound)

        // Simulação
        val rgPlat = view.findViewById<RadioGroup>(R.id.rgDialogPlatform)
        val rbIfood = view.findViewById<RadioButton>(R.id.rbDialogIfood)
        val rbUber = view.findViewById<RadioButton>(R.id.rbDialogUber)
        val etGross = view.findViewById<EditText>(R.id.etDialogOfferGrossValue)
        val etDist = view.findViewById<EditText>(R.id.etDialogOfferDistance)
        val etTime = view.findViewById<EditText>(R.id.etDialogOfferTime)
        val etDest = view.findViewById<EditText>(R.id.etDialogOfferDestination)
        val etCount = view.findViewById<EditText>(R.id.etDialogOfferOrderCount)
        val btnCalc = view.findViewById<MaterialButton>(R.id.btnDialogCalculateOffer)
        val btnOverlay = view.findViewById<MaterialButton>(R.id.btnDialogTestOverlay)
        val cardRes = view.findViewById<MaterialCardView>(R.id.cardDialogResult)
        val tvVerdict = view.findViewById<TextView>(R.id.tvDialogVerdict)
        val tvNet = view.findViewById<TextView>(R.id.tvDialogNetProfit)
        val tvRates = view.findViewById<TextView>(R.id.tvDialogRates)
        val tvCosts = view.findViewById<TextView>(R.id.tvDialogCostsBreakdown)
        val tvTimeB = view.findViewById<TextView>(R.id.tvDialogTimeBreakdown)

        swVoice.isChecked = cachedSettings.voiceAlertsEnabled
        swSound.isChecked = cachedSettings.soundAlertsEnabled

        btnVoice.setOnClickListener {
            OverlayService.testVoice(this)
            Toast.makeText(this, "🔊 Reproduzindo aviso de voz...", Toast.LENGTH_SHORT).show()
        }

        btnSound.setOnClickListener {
            OverlayService.testSound(this)
            Toast.makeText(this, "🔔 Testando efeitos sonoros...", Toast.LENGTH_SHORT).show()
        }

        btnCalc.setOnClickListener {
            val platform = when {
                rbIfood.isChecked -> DeliveryOffer.PLATFORM_IFOOD
                rbUber.isChecked -> DeliveryOffer.PLATFORM_UBER
                else -> DeliveryOffer.PLATFORM_99
            }
            val gross = parseDecimal(etGross.text.toString(), 18.50)
            val dist = parseDecimal(etDist.text.toString(), 5.2)
            val min = etTime.text.toString().toIntOrNull() ?: 18
            val dest = etDest.text.toString().trim().ifBlank { null }
            val count = etCount.text.toString().toIntOrNull() ?: 1

            val offer = DeliveryOffer(
                platform = platform,
                grossValue = gross,
                totalDistanceKm = dist,
                estimatedTimeMinutes = min,
                destinationAddress = dest,
                destinationNeighborhood = dest,
                orderCount = count,
                rawTexts = if (dest != null) listOf(dest) else emptyList()
            )

            val isRaining = cachedSettings.rainModeEnabled
            val elevationInfo = if (cachedSettings.isBike && cachedSettings.bikeElevationAlertEnabled) {
                ElevationClient.analyzeElevationNominal(dest.orEmpty())
            } else {
                ElevationClient.ElevationProfile(false, 0, "")
            }

            val result = DeliveryCalculator.calculate(
                offer = offer,
                settings = cachedSettings,
                isRaining = isRaining,
                isSteepIncline = elevationInfo.isSteepIncline,
                elevationGainMeters = elevationInfo.elevationGainMeters
            )

            when (result.trafficLightStatus) {
                TrafficLightStatus.GREEN -> {
                    tvVerdict.text = "🟢 VERDE - EXCELENTE CORRIDA"
                    tvVerdict.setTextColor(Color.parseColor("#00E676"))
                    cardRes.strokeColor = Color.parseColor("#00E676")
                }
                TrafficLightStatus.YELLOW -> {
                    tvVerdict.text = "🟡 AMARELO - ATENÇÃO (Paga metade das metas)"
                    tvVerdict.setTextColor(Color.parseColor("#FFB300"))
                    cardRes.strokeColor = Color.parseColor("#FFB300")
                }
                TrafficLightStatus.RED -> {
                    val reason = when {
                        result.isRiskArea -> "🚨 ÁREA DE RISCO: ${result.detectedRiskArea ?: "Local Perigoso"}"
                        result.isSteepIncline -> "🚴 SUBIDA MUITO ÍNGREME (+%d m)".format(result.elevationGainMeters)
                        cachedSettings.maxDistanceKm > 0.0 && offer.totalDistanceKm > cachedSettings.maxDistanceKm -> "Excede distância máxima"
                        cachedSettings.minGrossValueFloor > 0.0 && offer.grossValue < cachedSettings.minGrossValueFloor -> "Abaixo do piso mínimo"
                        else -> "Prejuízo / Abaixo das metas"
                    }
                    tvVerdict.text = "🔴 VERMELHO - RECUSAR ($reason)"
                    tvVerdict.setTextColor(Color.parseColor("#EF4444"))
                    cardRes.strokeColor = Color.parseColor("#EF4444")
                }
            }

            tvNet.text = "Lucro Líquido: R$ %.2f".format(Locale.US, result.netProfit)
            tvRates.text = "Ganho/Km: R$ %.2f • Ganho/Hora: R$ %.2f".format(Locale.US, result.earningsPerKm, result.earningsPerHour)
            tvCosts.text = "Custo Rota: R$ %.2f (Combustível: R$ %.2f/km)".format(Locale.US, result.totalRouteCost, result.fuelCostPerKm)
            tvTimeB.text = "Tempo Total Estimado: %.1fh".format(Locale.US, result.totalTimeHours)

            // Grava simulação no histórico do dia
            try {
                val isRed = (result.trafficLightStatus == TrafficLightStatus.RED)
                val autoRejected = isRed && cachedSettings.autoRejectRedOffers
                val historyRecord = DeliveryHistoryRecord(
                    dateStr = SQLiteDeliveryHistoryRepository.getTodayDateString(),
                    timeStr = SQLiteDeliveryHistoryRepository.getCurrentTimeString(),
                    platform = platform,
                    grossValue = offer.grossValue,
                    netProfit = result.netProfit,
                    totalDistanceKm = offer.totalDistanceKm,
                    estimatedTimeMinutes = offer.estimatedTimeMinutes,
                    trafficLightStatus = result.trafficLightStatus,
                    wasAutoRejected = autoRejected,
                    accepted = !isRed
                )
                historyRepository.recordOffer(historyRecord)
                updateDailySummaryUi()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                showOverlayPermissionDialog()
            } else {
                val platform = when {
                    rbIfood.isChecked -> DeliveryOffer.PLATFORM_IFOOD
                    rbUber.isChecked -> DeliveryOffer.PLATFORM_UBER
                    else -> DeliveryOffer.PLATFORM_99
                }
                val gross = parseDecimal(etGross.text.toString(), 18.50)
                val dist = parseDecimal(etDist.text.toString(), 5.2)
                val min = etTime.text.toString().toIntOrNull() ?: 18
                val dest = etDest.text.toString().trim().ifBlank { null }
                val count = etCount.text.toString().toIntOrNull() ?: 1

                val isRisk = if (dest != null && cachedSettings.riskAreasEnabled) {
                    val norm = DeliveryCalculator.normalizeText(dest)
                    cachedSettings.riskAreasList.any { norm.contains(DeliveryCalculator.normalizeText(it)) }
                } else false

                OverlayService.showOffer(
                    context = this,
                    platform = platform,
                    grossValue = gross,
                    distanceKm = dist,
                    timeMinutes = min,
                    destination = dest,
                    isRiskArea = isRisk,
                    riskAreaName = if (isRisk) dest else null,
                    orderCount = count,
                    isRaining = cachedSettings.rainModeEnabled,
                    isSteepIncline = false,
                    elevationGainMeters = 0
                )
                updateFloatingBubbleUi()
                Toast.makeText(this, "✓ Semáforo exibido na tela!", Toast.LENGTH_SHORT).show()
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Salvar Preferências") { _, _ ->
                cachedSettings = cachedSettings.copy(
                    voiceAlertsEnabled = swVoice.isChecked,
                    soundAlertsEnabled = swSound.isChecked
                )
                settingsRepository.saveSettings(cachedSettings)
                swVoiceAlerts.isChecked = swVoice.isChecked
                updateAlertsUi()
                Toast.makeText(this, "✓ Preferências de áudio salvas!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showAccessibilityDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_config_accessibility, null)
        val tvStatus = view.findViewById<TextView>(R.id.tvDialogAccessibilityStatus)
        val btnManage = view.findViewById<MaterialButton>(R.id.btnDialogManageAccessibility)

        val isEnabled = isAccessibilityServiceEnabled(this, DeliveryAccessibilityService::class.java)
        if (isEnabled) {
            tvStatus.text = "✓ Ativo e funcionando perfeitamente"
            tvStatus.setTextColor(Color.parseColor("#00E676"))
            btnManage.text = "Gerenciar nas Configurações"
        } else {
            tvStatus.text = "✕ Desativado"
            tvStatus.setTextColor(Color.parseColor("#EF4444"))
            btnManage.text = "Ativar nas Configurações"
        }

        btnManage.setOnClickListener {
            if (isEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                showProminentAccessibilityDisclosure()
            }
        }

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton("Fechar", null)
            .show()
    }

    // ==========================================
    // MÉTODOS AUXILIARES E RESUMO DIÁRIO
    // ==========================================

    private fun triggerGpsDetection() {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "📍 Consultando GPS...", Toast.LENGTH_SHORT).show()
            val locationInfo = UserLocationHelper.detectUserLocation(this@MainActivity)
            cachedSettings = cachedSettings.copy(
                userCity = locationInfo.city,
                userState = locationInfo.state
            )
            settingsRepository.saveSettings(cachedSettings)
            updateRiskAreasUi(cachedSettings.riskAreasEnabled)
            Toast.makeText(this@MainActivity, "✓ Localização definida: ${locationInfo.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProminentAccessibilityDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Divulgação Destacada de Acessibilidade")
            .setMessage(
                "O KMCERTO necessita do Serviço de Acessibilidade unicamente para detetar o valor, " +
                "distância e tempo nos ecrãs dos pedidos do iFood, Uber e 99. Nenhum dado pessoal, " +
                "palavra-passe ou mensagem privada é lido, gravado ou partilhado.\n\n" +
                "Deseja aceitar os termos e abrir as configurações de acessibilidade?"
            )
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Aceitar e Ativar") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Recusar", null)
            .setCancelable(false)
            .show()
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    private fun showOverlayPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissão de Janela Flutuante Necessária")
            .setMessage(
                "Para que o KmCerto possa exibir o semáforo de lucro líquido em tempo real sobre os aplicativos " +
                "de entrega (iFood, Uber e 99) enquanto você dirige, é necessário ativar a permissão 'Sobrepor a outros apps'.\n\n" +
                "Toque em 'Ativar nas Configurações' para autorizar."
            )
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Ativar nas Configurações") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Agora Não", null)
            .show()
    }

    private fun showEditDailyGoalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.0f", currentDailyGoal))
            setSelection(text.length)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#6B7280"))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00E676"))
        }

        val container = FrameLayout(this).apply {
            setPadding(60, 20, 60, 10)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Definir Meta Diária de Lucro")
            .setMessage("Digite quanto você deseja lucrar hoje (em R$):")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                val newGoal = input.text.toString().toDoubleOrNull()
                if (newGoal != null && newGoal > 0) {
                    currentDailyGoal = newGoal
                    cachedSettings = cachedSettings.copy(dailyRevenueGoal = newGoal)
                    settingsRepository.saveSettings(cachedSettings)
                    updateDailySummaryUi()
                    Toast.makeText(this, "✓ Meta diária atualizada para R$ %.2f!".format(newGoal), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Valor inválido para a meta!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showResetDailyShiftDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Zerar Turno de Hoje")
            .setMessage("Deseja zerar as estatísticas do turno de hoje? O faturamento e os contadores voltarão a zero.")
            .setPositiveButton("Sim, Zerar") { _, _ ->
                historyRepository.clearTodayRecords()
                updateDailySummaryUi()
                Toast.makeText(this, "✓ Turno de hoje zerado com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateDailySummaryUi() {
        val summary = historyRepository.getTodaySummary()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        tvDailyDate.text = "Turno atual • ${dateFormat.format(Date())}"
        tvDailyNetProfit.text = String.format(Locale.getDefault(), "R$ %.2f", summary.totalNetProfit)
        tvDailyMetrics.text = String.format(
            Locale.getDefault(),
            "%.1f km rodados • Média R$ %.2f/km (%d viagens aceitas)",
            summary.totalDistanceKm, summary.averageEarningsPerKm, summary.acceptedCount
        )
        tvDailyGreenCount.text = summary.greenCount.toString()
        tvDailyYellowCount.text = summary.yellowCount.toString()
        tvDailyRedCount.text = summary.redCount.toString()

        val goal = currentDailyGoal
        val progressPercent = summary.getGoalProgressPercent(goal)
        val remaining = summary.getRemainingToGoal(goal)
        pbDailyGoal.progress = progressPercent
        btnEditDailyGoal.text = String.format(Locale.getDefault(), "Meta: R$ %.0f ✎", goal)
        if (progressPercent >= 100) {
            tvDailyGoalProgress.text = String.format(Locale.getDefault(), "Meta atingida! 100%% (R$ %.2f)", summary.totalNetProfit)
            tvDailyGoalProgress.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvDailyGoalProgress.text = String.format(Locale.getDefault(), "%d%% da meta • Falta R$ %.2f", progressPercent, remaining)
            tvDailyGoalProgress.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun parseDecimal(text: String?, default: Double = 0.0): Double {
        if (text.isNullOrBlank()) return default
        val normalized = text.trim().replace(",", ".")
        return normalized.toDoubleOrNull() ?: default
    }
}
