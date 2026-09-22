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
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import br.com.entregador.lucro.service.DeliveryAccessibilityService
import br.com.entregador.lucro.service.OverlayService
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela principal e de configurações do aplicativo KmCerto.
 * Permite preencher e salvar com criptografia local (EncryptedSharedPreferences)
 * os parâmetros de veículo, custos operacionais, metas de rentabilidade e avisos de voz (TTS),
 * além de gerenciar permissões de Overlay e Acessibilidade (com Prominent Disclosure).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settingsRepository: DeliverySettingsRepository
    private lateinit var historyRepository: DeliveryHistoryRepository

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
    private lateinit var pbDailyGoal: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var tvDailyGoalProgress: TextView
    private var currentDailyGoal: Double = 200.0

    // Views do Modo Flutuante Permanente (Estilo GigU)
    private lateinit var cardFloatingBubble: MaterialCardView
    private lateinit var swFloatingBubble: SwitchMaterial
    private lateinit var tvFloatingStatus: TextView

    // Views de Recusa Automática (Auto-Reject de Corridas Vermelhas)
    private lateinit var swAutoRejectRed: SwitchMaterial
    private lateinit var tvAutoRejectStatus: TextView
    private lateinit var etAutoRejectDelay: EditText

    // Views de Aceite Automático (Auto-Accept de Corridas Verdes - Fase 6)
    private lateinit var swAutoAcceptEnabled: SwitchMaterial
    private lateinit var tvAutoAcceptStatus: TextView
    private lateinit var etAutoAcceptDelay: EditText

    // Views de Filtros Anti-Prejuízo
    private lateinit var etMinGrossValue: EditText
    private lateinit var etMaxDistance: EditText
    private lateinit var etEmptyReturnPercent: EditText

    // Views de Áreas de Risco (Fase 5 - Blacklist de Bairros)
    private lateinit var swRiskAreasEnabled: SwitchMaterial
    private lateinit var tvRiskAreasStatus: TextView
    private lateinit var swAutoRejectRiskAreas: SwitchMaterial
    private lateinit var etAddRiskArea: EditText
    private lateinit var btnAddRiskArea: MaterialButton
    private lateinit var tvRiskAreasCount: TextView
    private lateinit var cgRiskAreas: ChipGroup
    private val riskAreas = mutableListOf<String>()

    // Views de Configurações
    private lateinit var rgVehicleType: RadioGroup
    private lateinit var rbMoto: RadioButton
    private lateinit var rbBike: RadioButton
    private lateinit var tilFuelPrice: TextInputLayout
    private lateinit var etFuelPrice: EditText
    private lateinit var tilFuelConsumption: TextInputLayout
    private lateinit var etFuelConsumption: EditText
    private lateinit var tilMaintenanceCost: TextInputLayout
    private lateinit var etMaintenanceCost: EditText
    private lateinit var tilWaitBuffer: TextInputLayout
    private lateinit var etWaitBuffer: EditText
    private lateinit var tilTargetKm: TextInputLayout
    private lateinit var etTargetKm: EditText
    private lateinit var tilTargetHour: TextInputLayout
    private lateinit var etTargetHour: EditText
    private lateinit var swVoiceAlerts: SwitchMaterial
    private lateinit var btnTestVoice: MaterialButton
    private lateinit var swSoundAlerts: SwitchMaterial
    private lateinit var btnTestSound: MaterialButton
    private lateinit var btnSaveSettings: Button
    private lateinit var btnResetDefaults: MaterialButton
    private lateinit var tvStorageStatus: TextView

    // Views de Acessibilidade (Prominent Disclosure)
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnEnableAccessibility: MaterialButton

    // Views de Simulação Rápida
    private lateinit var rgPlatform: RadioGroup
    private lateinit var rbIfood: RadioButton
    private lateinit var rbUber: RadioButton
    private lateinit var rb99: RadioButton
    private lateinit var etOfferGrossValue: EditText
    private lateinit var etOfferDistance: EditText
    private lateinit var etOfferTime: EditText
    private lateinit var etOfferDestination: EditText
    private lateinit var etOfferOrderCount: EditText
    private lateinit var btnCalculateOffer: Button
    private lateinit var btnTestOverlay: MaterialButton

    // Views de Resultado do Semáforo
    private lateinit var cardResult: MaterialCardView
    private lateinit var tvVerdict: TextView
    private lateinit var tvNetProfit: TextView
    private lateinit var tvRates: TextView
    private lateinit var tvCostsBreakdown: TextView
    private lateinit var tvTimeBreakdown: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa o repositório seguro utilizando EncryptedSharedPreferences (AES-256)
        settingsRepository = EncryptedDeliverySettingsRepository(this)
        historyRepository = SQLiteDeliveryHistoryRepository(this)

        initViews()
        loadSettingsIntoUi(settingsRepository.getSettings())
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

    private fun setFloatingBubbleStatusUi(active: Boolean) {
        swFloatingBubble.setOnCheckedChangeListener(null)
        swFloatingBubble.isChecked = active

        if (active) {
            tvFloatingStatus.text = "🟢 Ativo (Bolha flutuando na tela)"
            tvFloatingStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvFloatingStatus.text = "⚪ Desativado (Toque para ativar o ícone flutuante)"
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

    private fun initViews() {
        // Views do Resumo do Turno de Hoje (Dashboard)
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

        cardFloatingBubble = findViewById(R.id.cardFloatingBubble)
        swFloatingBubble = findViewById(R.id.swFloatingBubble)
        tvFloatingStatus = findViewById(R.id.tvFloatingStatus)

        swAutoRejectRed = findViewById(R.id.swAutoRejectRed)
        tvAutoRejectStatus = findViewById(R.id.tvAutoRejectStatus)
        etAutoRejectDelay = findViewById(R.id.etAutoRejectDelay)

        swAutoAcceptEnabled = findViewById(R.id.swAutoAcceptEnabled)
        tvAutoAcceptStatus = findViewById(R.id.tvAutoAcceptStatus)
        etAutoAcceptDelay = findViewById(R.id.etAutoAcceptDelay)

        etMinGrossValue = findViewById(R.id.etMinGrossValue)
        etMinGrossValue.addTextChangedListener(CurrencyTextWatcher(etMinGrossValue))
        etMinGrossValue.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etMinGrossValue.post {
                    etMinGrossValue.setSelection(etMinGrossValue.text?.length ?: 0)
                }
            }
        }
        etMinGrossValue.setOnClickListener {
            etMinGrossValue.setSelection(etMinGrossValue.text?.length ?: 0)
        }
        etMaxDistance = findViewById(R.id.etMaxDistance)
        etEmptyReturnPercent = findViewById(R.id.etEmptyReturnPercent)

        // Views de Áreas de Risco (Fase 5 - Blacklist de Bairros)
        swRiskAreasEnabled = findViewById(R.id.swRiskAreasEnabled)
        tvRiskAreasStatus = findViewById(R.id.tvRiskAreasStatus)
        swAutoRejectRiskAreas = findViewById(R.id.swAutoRejectRiskAreas)
        etAddRiskArea = findViewById(R.id.etAddRiskArea)
        btnAddRiskArea = findViewById(R.id.btnAddRiskArea)
        tvRiskAreasCount = findViewById(R.id.tvRiskAreasCount)
        cgRiskAreas = findViewById(R.id.cgRiskAreas)

        rgVehicleType = findViewById(R.id.rgVehicleType)
        rbMoto = findViewById(R.id.rbMoto)
        rbBike = findViewById(R.id.rbBike)
        tilFuelPrice = findViewById(R.id.tilFuelPrice)
        etFuelPrice = findViewById(R.id.etFuelPrice)
        tilFuelConsumption = findViewById(R.id.tilFuelConsumption)
        etFuelConsumption = findViewById(R.id.etFuelConsumption)
        tilMaintenanceCost = findViewById(R.id.tilMaintenanceCost)
        etMaintenanceCost = findViewById(R.id.etMaintenanceCost)
        tilWaitBuffer = findViewById(R.id.tilWaitBuffer)
        etWaitBuffer = findViewById(R.id.etWaitBuffer)
        tilTargetKm = findViewById(R.id.tilTargetKm)
        etTargetKm = findViewById(R.id.etTargetKm)
        tilTargetHour = findViewById(R.id.tilTargetHour)
        etTargetHour = findViewById(R.id.etTargetHour)
        swVoiceAlerts = findViewById(R.id.swVoiceAlerts)
        btnTestVoice = findViewById(R.id.btnTestVoice)
        swSoundAlerts = findViewById(R.id.swSoundAlerts)
        btnTestSound = findViewById(R.id.btnTestSound)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnResetDefaults = findViewById(R.id.btnResetDefaults)
        tvStorageStatus = findViewById(R.id.tvStorageStatus)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)

        rgPlatform = findViewById(R.id.rgPlatform)
        rbIfood = findViewById(R.id.rbIfood)
        rbUber = findViewById(R.id.rbUber)
        rb99 = findViewById(R.id.rb99)
        etOfferGrossValue = findViewById(R.id.etOfferGrossValue)
        etOfferDistance = findViewById(R.id.etOfferDistance)
        etOfferTime = findViewById(R.id.etOfferTime)
        etOfferDestination = findViewById(R.id.etOfferDestination)
        etOfferOrderCount = findViewById(R.id.etOfferOrderCount)
        btnCalculateOffer = findViewById(R.id.btnCalculateOffer)
        btnTestOverlay = findViewById(R.id.btnTestOverlay)

        cardResult = findViewById(R.id.cardResult)
        tvVerdict = findViewById(R.id.tvVerdict)
        tvNetProfit = findViewById(R.id.tvNetProfit)
        tvRates = findViewById(R.id.tvRates)
        tvCostsBreakdown = findViewById(R.id.tvCostsBreakdown)
        tvTimeBreakdown = findViewById(R.id.tvTimeBreakdown)
    }

    private fun setupListeners() {
        // Zerar Resumo do Turno de Hoje
        btnResetDailyShift.setOnClickListener {
            showResetDailyShiftDialog()
        }

        // Abrir Tela de Histórico Completo & Relatórios WhatsApp (Fase 7)
        btnOpenHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Toque em qualquer parte do card do ícone flutuante alterna o switch com 1 clique
        cardFloatingBubble.setOnClickListener {
            swFloatingBubble.isChecked = !swFloatingBubble.isChecked
        }

        // Alternância inteligente de campos entre Moto e Bike
        rgVehicleType.setOnCheckedChangeListener { _, checkedId ->
            val isBike = (checkedId == R.id.rbBike)
            updateVehicleInputState(isBike)
        }

        // Testar aviso de voz imediatamente (TTS)
        btnTestVoice.setOnClickListener {
            OverlayService.testVoice(this)
            Toast.makeText(this, "🔊 Reproduzindo aviso de voz...", Toast.LENGTH_SHORT).show()
        }

        // Testar bipe sonoro imediato (SoundPool - Fase 7)
        btnTestSound.setOnClickListener {
            OverlayService.testSound(this)
            Toast.makeText(this, "🔔 Testando efeitos sonoros...", Toast.LENGTH_SHORT).show()
        }

        // Salvar configurações
        btnSaveSettings.setOnClickListener {
            val settings = readSettingsFromUi()
            settingsRepository.saveSettings(settings)
            Toast.makeText(
                this,
                "✓ Configurações salvas localmente com criptografia AES-256!",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Switch de Recusa Automática de Ofertas Vermelhas (liga/desliga imediato)
        swAutoRejectRed.setOnCheckedChangeListener { _, isChecked ->
            updateAutoRejectUi(isChecked)
            val currentSettings = readSettingsFromUi().copy(autoRejectRedOffers = isChecked)
            settingsRepository.saveSettings(currentSettings)
            val msg = if (isChecked) {
                "✓ Recusa automática de ofertas vermelhas ATIVADA!"
            } else {
                "Recusa automática DESATIVADA. Nenhuma oferta será recusada."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Ajuste da Meta Diária (Fase 6)
        btnEditDailyGoal.setOnClickListener {
            showEditDailyGoalDialog()
        }

        // Switch de Aceite Automático de Ofertas Verdes (Fase 6)
        swAutoAcceptEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateAutoAcceptUi(isChecked)
            val currentSettings = readSettingsFromUi().copy(autoAcceptGreenOffers = isChecked)
            settingsRepository.saveSettings(currentSettings)
            val msg = if (isChecked) {
                "🟢 Aceite automático de ofertas verdes ATIVADO!"
            } else {
                "Aceite automático DESATIVADO. Nenhuma oferta será aceita automaticamente."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Switch de Vigilância de Áreas de Risco (Fase 5)
        swRiskAreasEnabled.setOnCheckedChangeListener { _, isChecked ->
            updateRiskAreasUi(isChecked)
            val currentSettings = readSettingsFromUi().copy(riskAreasEnabled = isChecked)
            settingsRepository.saveSettings(currentSettings)
            val msg = if (isChecked) {
                "🚨 Vigilância de Áreas de Risco ATIVADA!"
            } else {
                "Vigilância de Áreas de Risco DESATIVADA."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Switch de Recusa Automática em Área de Risco
        swAutoRejectRiskAreas.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = readSettingsFromUi().copy(autoRejectRiskAreas = isChecked)
            settingsRepository.saveSettings(currentSettings)
            val msg = if (isChecked) {
                "✓ Auto-recusa em áreas de risco ATIVADA!"
            } else {
                "Auto-recusa em áreas de risco DESATIVADA."
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Adicionar novo Bairro / Área de Risco com validação e persistência
        val addAreaAction = {
            val newArea = etAddRiskArea.text.toString().trim()
            if (newArea.isNotEmpty()) {
                if (riskAreas.any { it.equals(newArea, ignoreCase = true) }) {
                    Toast.makeText(this, "Bairro \"$newArea\" já cadastrado!", Toast.LENGTH_SHORT).show()
                } else {
                    riskAreas.add(newArea)
                    val updatedSettings = readSettingsFromUi().copy(riskAreasList = riskAreas.toList())
                    settingsRepository.saveSettings(updatedSettings)
                    renderRiskAreaChips()
                    updateRiskAreasUi(swRiskAreasEnabled.isChecked)
                    etAddRiskArea.text?.clear()
                    Toast.makeText(this, "✓ Bairro \"$newArea\" adicionado à lista!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnAddRiskArea.setOnClickListener { addAreaAction() }
        etAddRiskArea.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addAreaAction()
                true
            } else false
        }

        // Switch de avisos por voz: salva a preferência de áudio imediatamente
        swVoiceAlerts.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = readSettingsFromUi().copy(voiceAlertsEnabled = isChecked)
            settingsRepository.saveSettings(currentSettings)
            val msg = if (isChecked) "Avisos por voz ativados" else "Avisos por voz desativados"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Switch de sons curtos / bipes (SoundPool): salva imediatamente (Fase 7)
        swSoundAlerts.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = readSettingsFromUi().copy(soundAlertsEnabled = isChecked)
            settingsRepository.saveSettings(currentSettings)
            val msg = if (isChecked) "Sons de alerta ativados" else "Sons de alerta desativados"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Salvar configurações
        btnSaveSettings.setOnClickListener {
            val currentSettings = readSettingsFromUi()
            settingsRepository.saveSettings(currentSettings)
            Toast.makeText(this, "✓ Configurações salvas com sucesso!", Toast.LENGTH_SHORT).show()
        }

        // Restaurar configurações padrão
        btnResetDefaults.setOnClickListener {
            val defaultSettings = DeliverySettings()
            loadSettingsIntoUi(defaultSettings)
            settingsRepository.saveSettings(defaultSettings)
            Toast.makeText(this, "Valores padrão restaurados e salvos!", Toast.LENGTH_SHORT).show()
        }

        // Ativação da Acessibilidade com Prominent Disclosure
        btnEnableAccessibility.setOnClickListener {
            if (isAccessibilityServiceEnabled(this, DeliveryAccessibilityService::class.java)) {
                // Já ativo, abre as configurações de acessibilidade para gerenciamento
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                // Não ativo: exibe obrigatoriamente a declaração proeminente (Prominent Disclosure)
                showProminentAccessibilityDisclosure()
            }
        }

        // Simular corrida no painel
        btnCalculateOffer.setOnClickListener {
            calculateAndDisplayResult()
        }

        // Testar janela flutuante sobre a tela (Overlay)
        btnTestOverlay.setOnClickListener {
            checkOverlayPermissionAndTest()
        }
    }

    /**
     * Atualiza o badge e o botão de status do serviço de acessibilidade na interface.
     */
    private fun updateAccessibilityStatusUi() {
        val isEnabled = isAccessibilityServiceEnabled(this, DeliveryAccessibilityService::class.java)
        if (isEnabled) {
            tvAccessibilityStatus.text = "✓ Ativo e monitorando (iFood, Uber e 99)"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#198754"))
            btnEnableAccessibility.text = "Gerenciar Serviço de Acessibilidade"
        } else {
            tvAccessibilityStatus.text = "✕ Desativado (Toque abaixo para autorizar e ativar)"
            tvAccessibilityStatus.setTextColor(Color.parseColor("#DC3545"))
            btnEnableAccessibility.text = "Ativar Serviço de Acessibilidade"
        }
    }

    /**
     * Ecrã / Diálogo de Divulgação Destacada (Prominent Disclosure) exigido pela Google Play.
     * Direciona para Settings.ACTION_ACCESSIBILITY_SETTINGS apenas após a aceitação explícita do termo.
     */
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
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Recusar", null)
            .setCancelable(false)
            .show()
    }

    /**
     * Verifica se o serviço de acessibilidade do KmCerto está habilitado no sistema Android.
     */
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

    /**
     * Verifica se a permissão Settings.canDrawOverlays está concedida.
     * Caso não esteja, exibe um diálogo explicativo e redireciona o usuário para as configurações.
     * Caso esteja, dispara o OverlayService com dados simulados da DeliveryOffer.
     */
    private fun checkOverlayPermissionAndTest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        } else {
            triggerSimulatedOverlay()
        }
    }

    /**
     * Diálogo explicativo que esclarece a necessidade do overlay para o KmCerto funcionar
     * e redireciona para Settings.ACTION_MANAGE_OVERLAY_PERMISSION.
     */
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

    /**
     * Dispara o OverlayService passando dados simulados de uma DeliveryOffer
     * para validação visual da janela flutuante.
     */
    private fun triggerSimulatedOverlay() {
        val platform = when {
            rbIfood.isChecked -> DeliveryOffer.PLATFORM_IFOOD
            rbUber.isChecked -> DeliveryOffer.PLATFORM_UBER
            else -> DeliveryOffer.PLATFORM_99
        }
        val grossValue = parseDecimal(etOfferGrossValue.text.toString(), 18.50)
        val distance = parseDecimal(etOfferDistance.text.toString(), 5.2)
        val timeMinutes = etOfferTime.text.toString().toIntOrNull() ?: 18
        val destination = etOfferDestination.text.toString().trim().ifBlank { null }
        val orderCount = etOfferOrderCount.text.toString().toIntOrNull() ?: 1

        val settings = readSettingsFromUi()
        val isRisk = if (destination != null && settings.riskAreasEnabled) {
            val normDest = DeliveryCalculator.normalizeText(destination)
            settings.riskAreasList.any { normDest.contains(DeliveryCalculator.normalizeText(it)) }
        } else false

        OverlayService.showOffer(
            context = this,
            platform = platform,
            grossValue = grossValue,
            distanceKm = distance,
            timeMinutes = timeMinutes,
            destination = destination,
            isRiskArea = isRisk,
            riskAreaName = if (isRisk) destination else null,
            orderCount = orderCount
        )

        updateFloatingBubbleUi()

        val alertMsg = if (isRisk) "⚠️ ALERTA: Área de risco simulada ($destination)!" else "✓ Semáforo ativo na tela!"
        Toast.makeText(
            this,
            "$alertMsg O card fecha em 12s, mas a bolha continua flutuando na borda (estilo GigU).",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateVehicleInputState(isBike: Boolean) {
        if (isBike) {
            tilFuelPrice.isEnabled = false
            tilFuelConsumption.isEnabled = false
            tilFuelPrice.helperText = "Não aplicável para Bicicleta"
            tilFuelConsumption.helperText = "Não aplicável para Bicicleta"
        } else {
            tilFuelPrice.isEnabled = true
            tilFuelConsumption.isEnabled = true
            tilFuelPrice.helperText = "Ex: 5,80 por litro"
            tilFuelConsumption.helperText = "Ex: 35,0 km por litro"
        }
    }

    private fun loadSettingsIntoUi(settings: DeliverySettings) {
        if (settings.isBike) {
            rbBike.isChecked = true
            updateVehicleInputState(true)
        } else {
            rbMoto.isChecked = true
            updateVehicleInputState(false)
        }

        etFuelPrice.setText(String.format(Locale.US, "%.2f", settings.fuelPricePerLiter))
        etFuelConsumption.setText(String.format(Locale.US, "%.1f", settings.fuelConsumptionKmPerLiter))
        etMaintenanceCost.setText(String.format(Locale.US, "%.2f", settings.maintenanceCostPerKm))
        etWaitBuffer.setText(settings.restaurantWaitBufferMinutes.toString())
        etTargetKm.setText(String.format(Locale.US, "%.2f", settings.targetMinPerKm))
        etTargetHour.setText(String.format(Locale.US, "%.2f", settings.targetMinPerHour))
        swVoiceAlerts.isChecked = settings.voiceAlertsEnabled
        swSoundAlerts.isChecked = settings.soundAlertsEnabled

        // Recusa automática
        swAutoRejectRed.isChecked = settings.autoRejectRedOffers
        etAutoRejectDelay.setText(settings.autoRejectDelaySeconds.toString())
        updateAutoRejectUi(settings.autoRejectRedOffers)

        // Meta Diária (Fase 6)
        currentDailyGoal = settings.dailyRevenueGoal

        // Aceite Automático (Fase 6)
        swAutoAcceptEnabled.isChecked = settings.autoAcceptGreenOffers
        etAutoAcceptDelay.setText(settings.autoAcceptDelaySeconds.toString())
        updateAutoAcceptUi(settings.autoAcceptGreenOffers)

        // Filtros Anti-Prejuízo
        etMinGrossValue.setText(CurrencyUtils.formatCurrency(settings.minGrossValueFloor))
        etMaxDistance.setText(String.format(Locale.US, "%.1f", settings.maxDistanceKm))
        etEmptyReturnPercent.setText(String.format(Locale.US, "%.0f", settings.emptyReturnPercent))

        // Áreas de Risco (Fase 5)
        swRiskAreasEnabled.isChecked = settings.riskAreasEnabled
        swAutoRejectRiskAreas.isChecked = settings.autoRejectRiskAreas
        riskAreas.clear()
        riskAreas.addAll(settings.riskAreasList)
        renderRiskAreaChips()
        updateRiskAreasUi(settings.riskAreasEnabled)
    }

    private fun renderRiskAreaChips() {
        cgRiskAreas.removeAllViews()
        tvRiskAreasCount.text = "Bairros cadastrados (${riskAreas.size}):"
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
                    val updatedSettings = readSettingsFromUi().copy(riskAreasList = riskAreas.toList())
                    settingsRepository.saveSettings(updatedSettings)
                    renderRiskAreaChips()
                    updateRiskAreasUi(swRiskAreasEnabled.isChecked)
                    Toast.makeText(this@MainActivity, "Bairro \"$area\" removido", Toast.LENGTH_SHORT).show()
                }
            }
            cgRiskAreas.addView(chip)
        }
    }

    private fun updateRiskAreasUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvRiskAreasStatus.text = "🚨 Ativado (Protegendo contra ${riskAreas.size} bairros)"
            tvRiskAreasStatus.setTextColor(Color.parseColor("#EF4444"))
        } else {
            tvRiskAreasStatus.text = "⚪ Desativado (Toque para ativar)"
            tvRiskAreasStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateAutoRejectUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvAutoRejectStatus.text = "🔴 Ativado (Recusando ofertas vermelhas)"
            tvAutoRejectStatus.setTextColor(Color.parseColor("#EF4444"))
        } else {
            tvAutoRejectStatus.text = "⚪ Desativado (Toque para ativar)"
            tvAutoRejectStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateAutoAcceptUi(isEnabled: Boolean) {
        if (isEnabled) {
            tvAutoAcceptStatus.text = "🟢 Ativado (Aceitando ofertas verdes)"
            tvAutoAcceptStatus.setTextColor(Color.parseColor("#00E676"))
        } else {
            tvAutoAcceptStatus.text = "⚪ Desativado (Toque para ativar)"
            tvAutoAcceptStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
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
                    val updated = readSettingsFromUi().copy(dailyRevenueGoal = newGoal)
                    settingsRepository.saveSettings(updated)
                    updateDailySummaryUi()
                    Toast.makeText(this, "✓ Meta diária atualizada para R$ %.2f!".format(newGoal), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Valor inválido para a meta!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun parseDecimal(text: String?, default: Double = 0.0): Double {
        if (text.isNullOrBlank()) return default
        val normalized = text.trim().replace(",", ".")
        return normalized.toDoubleOrNull() ?: default
    }

    private fun readSettingsFromUi(): DeliverySettings {
        val vehicleType = if (rbBike.isChecked) DeliverySettings.VEHICLE_BIKE else DeliverySettings.VEHICLE_MOTO
        val fuelPrice = parseDecimal(etFuelPrice.text.toString(), 5.80)
        val fuelConsumption = parseDecimal(etFuelConsumption.text.toString(), 35.0)
        val maintenanceCost = parseDecimal(etMaintenanceCost.text.toString(), 0.12)
        val waitBuffer = etWaitBuffer.text.toString().toIntOrNull() ?: 10
        val targetKm = parseDecimal(etTargetKm.text.toString(), 2.00)
        val targetHour = parseDecimal(etTargetHour.text.toString(), 25.00)
        val voiceAlerts = swVoiceAlerts.isChecked
        val soundAlerts = swSoundAlerts.isChecked
        val autoReject = swAutoRejectRed.isChecked
        val autoRejectDelay = etAutoRejectDelay.text.toString().toIntOrNull() ?: 3
        val autoAccept = swAutoAcceptEnabled.isChecked
        val autoAcceptDelay = etAutoAcceptDelay.text.toString().toIntOrNull() ?: 1
        val minGrossValue = CurrencyUtils.parseCurrency(etMinGrossValue.text.toString())
        val maxDistance = parseDecimal(etMaxDistance.text.toString(), 0.0)
        val emptyReturn = parseDecimal(etEmptyReturnPercent.text.toString(), 0.0)
        val riskAreasEnabled = swRiskAreasEnabled.isChecked
        val autoRejectRisk = swAutoRejectRiskAreas.isChecked

        return DeliverySettings(
            vehicleType = vehicleType,
            fuelPricePerLiter = fuelPrice,
            fuelConsumptionKmPerLiter = fuelConsumption,
            maintenanceCostPerKm = maintenanceCost,
            restaurantWaitBufferMinutes = waitBuffer,
            targetMinPerKm = targetKm,
            targetMinPerHour = targetHour,
            voiceAlertsEnabled = voiceAlerts,
            soundAlertsEnabled = soundAlerts,
            autoRejectRedOffers = autoReject,
            autoRejectDelaySeconds = autoRejectDelay,
            dailyRevenueGoal = currentDailyGoal,
            autoAcceptGreenOffers = autoAccept,
            autoAcceptDelaySeconds = autoAcceptDelay,
            minGrossValueFloor = minGrossValue,
            maxDistanceKm = maxDistance,
            emptyReturnPercent = emptyReturn,
            riskAreasEnabled = riskAreasEnabled,
            riskAreasList = riskAreas.toList(),
            autoRejectRiskAreas = autoRejectRisk
        )
    }

    private fun calculateAndDisplayResult() {
        val platform = when {
            rbIfood.isChecked -> DeliveryOffer.PLATFORM_IFOOD
            rbUber.isChecked -> DeliveryOffer.PLATFORM_UBER
            else -> DeliveryOffer.PLATFORM_99
        }

        val grossValue = parseDecimal(etOfferGrossValue.text.toString(), 0.0)
        val distance = parseDecimal(etOfferDistance.text.toString(), 0.0)
        val timeMinutes = etOfferTime.text.toString().toIntOrNull() ?: 0
        val destination = etOfferDestination.text.toString().trim().ifBlank { null }
        val orderCount = etOfferOrderCount.text.toString().toIntOrNull() ?: 1

        val offer = DeliveryOffer(
            platform = platform,
            grossValue = grossValue,
            totalDistanceKm = distance,
            estimatedTimeMinutes = timeMinutes,
            destinationAddress = destination,
            destinationNeighborhood = destination,
            orderCount = orderCount,
            rawTexts = if (destination != null) listOf(destination) else emptyList()
        )

        val settings = readSettingsFromUi()
        val result = DeliveryCalculator.calculate(offer, settings)

        // Atualizar Card de Resultado com Semáforo
        when (result.trafficLightStatus) {
            TrafficLightStatus.GREEN -> {
                val multiPrefix = if (result.orderCount > 1) "📦 Rota Dupla (${result.orderCount} entregas)\n" else ""
                tvVerdict.text = "${multiPrefix}🟢 VERDE - OFERTA RECOMENDADA ($platform)\nAmbas as metas atingidas"
                tvVerdict.setTextColor(Color.parseColor("#198754"))
                cardResult.strokeColor = Color.parseColor("#198754")
            }
            TrafficLightStatus.YELLOW -> {
                val detail = if (result.earningsPerKm >= settings.targetMinPerKm) {
                    "Atingiu meta de R$/Km, mas não a de R$/Hora"
                } else {
                    "Atingiu meta de R$/Hora, mas não a de R$/Km"
                }
                val multiPrefix = if (result.orderCount > 1) "📦 Rota Dupla (${result.orderCount} entregas)\n" else ""
                tvVerdict.text = "${multiPrefix}🟡 AMARELO - ATENÇÃO ($platform)\n$detail"
                tvVerdict.setTextColor(Color.parseColor("#D97706"))
                cardResult.strokeColor = Color.parseColor("#D97706")
            }
            TrafficLightStatus.RED -> {
                val detail = when {
                    result.isRiskArea ->
                        "🚨 ÁREA DE RISCO DETECTADA: ${result.detectedRiskArea ?: "Bairro Perigoso"}"
                    settings.maxDistanceKm > 0.0 && offer.totalDistanceKm > settings.maxDistanceKm ->
                        "Distância (%.1f km) excede teto máximo de %.1f km".format(Locale.getDefault(), offer.totalDistanceKm, settings.maxDistanceKm)
                    settings.minGrossValueFloor > 0.0 && offer.grossValue < settings.minGrossValueFloor ->
                        "Valor bruto (R$ %.2f) abaixo do piso mínimo de R$ %.2f".format(Locale.getDefault(), offer.grossValue, settings.minGrossValueFloor)
                    else -> "Abaixo de ambas as metas"
                }
                val multiPrefix = if (result.orderCount > 1) "📦 Rota Dupla (${result.orderCount} entregas)\n" else ""
                tvVerdict.text = "${multiPrefix}🔴 VERMELHO - RECUSAR ($platform)\n$detail"
                tvVerdict.setTextColor(Color.parseColor("#DC3545"))
                cardResult.strokeColor = Color.parseColor("#DC3545")
            }
        }

        val profitText = if (result.orderCount > 1) {
            String.format(Locale.getDefault(), "Lucro Líquido: R$ %.2f (📦 R$ %.2f/entrega)", result.netProfit, result.netProfitPerOrder)
        } else {
            String.format(Locale.getDefault(), "Lucro Líquido: R$ %.2f", result.netProfit)
        }
        tvNetProfit.text = profitText
        tvRates.text = String.format(
            Locale.getDefault(),
            "Ganho/Km: R$ %.2f (Meta: R$ %.2f) | Ganho/Hora: R$ %.2f (Meta: R$ %.2f)",
            result.earningsPerKm, settings.targetMinPerKm,
            result.earningsPerHour, settings.targetMinPerHour
        )
        val costLabel = if (settings.emptyReturnPercent > 0.0) {
            String.format(Locale.getDefault(), "Combustível/km: R$ %.2f | Custo (c/ %.0f%% volta): R$ %.2f", result.fuelCostPerKm, settings.emptyReturnPercent, result.totalRouteCost)
        } else {
            String.format(Locale.getDefault(), "Combustível/km: R$ %.2f | Custo Total Rota: R$ %.2f", result.fuelCostPerKm, result.totalRouteCost)
        }
        tvCostsBreakdown.text = costLabel

        val timeLabel = if (settings.emptyReturnPercent > 0.0) {
            String.format(Locale.getDefault(), "Tempo Total: %.1fh (%d min + %.0f%% volta + %d min espera)", result.totalTimeHours, offer.estimatedTimeMinutes, settings.emptyReturnPercent, settings.restaurantWaitBufferMinutes)
        } else {
            String.format(Locale.getDefault(), "Tempo Total: %.1fh (%d min viagem + %d min espera restaurante)", result.totalTimeHours, offer.estimatedTimeMinutes, settings.restaurantWaitBufferMinutes)
        }
        tvTimeBreakdown.text = timeLabel

        // Registra simulação no histórico do turno diário e atualiza o dashboard
        try {
            val isRed = (result.trafficLightStatus == TrafficLightStatus.RED)
            val autoRejected = isRed && settings.autoRejectRedOffers
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

    /**
     * Atualiza as métricas e contadores de semáforo do Card Resumo do Turno de Hoje.
     */
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

        // Meta Diária (Fase 6)
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

    /**
     * Diálogo de confirmação para o entregador zerar o turno diário e reiniciar métricas.
     */
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
}
