package br.com.entregador.lucro.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import br.com.entregador.lucro.data.repository.EncryptedDeliverySettingsRepository
import br.com.entregador.lucro.data.repository.SQLiteDeliveryHistoryRepository
import br.com.entregador.lucro.domain.calculator.DeliveryCalculator
import br.com.entregador.lucro.domain.formatter.VoiceAlertFormatter
import br.com.entregador.lucro.domain.model.DeliveryCalculationResult
import br.com.entregador.lucro.domain.model.DeliveryHistoryRecord
import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import br.com.entregador.lucro.domain.parser.DeliveryOfferParser
import br.com.entregador.lucro.network.ElevationClient
import br.com.entregador.lucro.network.WeatherClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Serviço de Acessibilidade do KmCerto para monitoramento automático de ofertas.
 * Inspeciona a árvore visual de acessibilidade das plataformas (iFood, Uber, 99),
 * extrai os valores monetários, distâncias (somando parciais) e tempo de rota,
 * calcula a rentabilidade, dispara o Overlay flutuante, recusa automaticamente ofertas vermelhas
 * e emite avisos por voz sintetizada (TTS).
 */
class DeliveryAccessibilityService : AccessibilityService() {

    private var lastOfferSignature: String? = null
    private var lastOfferProcessedTime: Long = 0L

    // Síntese de Voz (Text-to-Speech)
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    // Handler e controle de Recusa e Aceite Automático
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRejectRunnable: Runnable? = null
    private var pendingAcceptRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        initTextToSpeech()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "KmCerto DeliveryAccessibilityService conectado e ativo!")
        refreshWeather()
    }

    private fun refreshWeather() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = WeatherClient.checkWeather()
                if (result.isSuccess) {
                    val report = result.getOrThrow()
                    updateWeatherStatus(report.isRaining, report.description)
                    Log.d(TAG, "Clima consultado com sucesso: ${report.description} (Chovendo: ${report.isRaining})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível atualizar clima em segundo plano: ${e.message}")
            }
        }
    }

    /**
     * Inicializa a biblioteca TextToSpeech configurada para português ("pt", "BR").
     */
    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localePtBr = Locale("pt", "BR")
                val langResult = textToSpeech?.setLanguage(localePtBr)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Idioma pt-BR não suportado diretamente, utilizando português padrão.")
                    textToSpeech?.setLanguage(Locale("pt"))
                }
                isTtsReady = true
                Log.d(TAG, "TextToSpeech inicializado com sucesso em Português.")
            } else {
                Log.e(TAG, "Falha na inicialização do TextToSpeech (status: $status).")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 1. Identifica o packageName da aplicação ativa
        val packageName = event.packageName?.toString() ?: return
        val platform = resolvePlatformFromPackage(packageName) ?: return

        // 2. Obtém o nó raiz através de rootInActiveWindow
        val rootNode = rootInActiveWindow ?: return

        try {
            // 3. Recolhe todo o texto visível da árvore AccessibilityNodeInfo numa lista de strings
            val visibleTexts = mutableListOf<String>()
            collectVisibleTexts(rootNode, visibleTexts)

            if (visibleTexts.isEmpty()) return

            // 4. Implementa métodos de extração com expressões regulares (Regex) e monta a DeliveryOffer
            val offer = DeliveryOfferParser.parseOffer(visibleTexts, platform) ?: return

            // Valida se os dados essenciais da oferta foram apurados
            if (offer.grossValue <= 0.0 || offer.totalDistanceKm <= 0.0) return

            // Previne disparos repetidos consecutivos para a mesma oferta na tela
            val signature = "${offer.platform}_${offer.grossValue}_${offer.totalDistanceKm}_${offer.estimatedTimeMinutes}"
            val currentTime = SystemClock.uptimeMillis()
            if (signature == lastOfferSignature && (currentTime - lastOfferProcessedTime) < DEBOUNCE_TIME_MS) {
                return
            }

            lastOfferSignature = signature
            lastOfferProcessedTime = currentTime

            Log.i(TAG, "Oferta detectada: $platform | R$ ${offer.grossValue} | ${offer.totalDistanceKm} km | ${offer.estimatedTimeMinutes} min")

            // 5. Recupera as preferências guardadas em DeliverySettings
            val settings = EncryptedDeliverySettingsRepository(this).getSettings()

            val isRaining = isCurrentWeatherRain || settings.rainModeEnabled
            val elevationProfile = if (settings.isBike && settings.bikeElevationAlertEnabled) {
                ElevationClient.analyzeElevationNominal(
                    offer.destinationNeighborhood ?: offer.destinationAddress ?: ""
                )
            } else {
                ElevationClient.ElevationProfile(false, 0, "")
            }

            // 6. Executa o cálculo em DeliveryCalculator.calculate(offer, settings)
            val result = DeliveryCalculator.calculate(
                offer = offer,
                settings = settings,
                isRaining = isRaining,
                isSteepIncline = elevationProfile.isSteepIncline,
                elevationGainMeters = elevationProfile.elevationGainMeters
            )

            // Registra a oferta no histórico do turno diário (Fase 2)
            try {
                val isRed = (result.trafficLightStatus == TrafficLightStatus.RED)
                val autoRejected = isRed && settings.autoRejectRedOffers
                val historyRepo = SQLiteDeliveryHistoryRepository(this)
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
                historyRepo.recordOffer(historyRecord)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao gravar oferta no histórico: ${e.message}")
            }

            // 7. Envia um Intent para o OverlayService com os dados calculados para exibir o card flutuante
            val overlayIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OFFER
                putExtra(OverlayService.EXTRA_PLATFORM, platform)
                putExtra(OverlayService.EXTRA_GROSS_VALUE, offer.grossValue)
                putExtra(OverlayService.EXTRA_DISTANCE_KM, offer.totalDistanceKm)
                putExtra(OverlayService.EXTRA_TIME_MINUTES, offer.estimatedTimeMinutes)
                putExtra(OverlayService.EXTRA_DESTINATION, offer.destinationNeighborhood ?: offer.destinationAddress)
                putExtra(OverlayService.EXTRA_IS_RISK_AREA, result.isRiskArea)
                putExtra(OverlayService.EXTRA_RISK_AREA_NAME, result.detectedRiskArea)
                putExtra(OverlayService.EXTRA_ORDER_COUNT, offer.orderCount)
                putExtra(OverlayService.EXTRA_IS_RAINING, isRaining)
                putExtra(OverlayService.EXTRA_IS_STEEP, elevationProfile.isSteepIncline)
                putExtra(OverlayService.EXTRA_ELEVATION_GAIN, elevationProfile.elevationGainMeters)
                putExtra("extra_net_profit", result.netProfit)
                putExtra("extra_rate_per_km", result.earningsPerKm)
                putExtra("extra_rate_per_hour", result.earningsPerHour)
                putExtra("extra_traffic_light", result.trafficLightStatus.name)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }

            // 8. Se os avisos por voz estiverem habilitados nas preferências, emite o alerta falado
            val isGreenOffer = (result.trafficLightStatus == TrafficLightStatus.GREEN)
            val isRedOffer = (result.trafficLightStatus == TrafficLightStatus.RED)
            val shouldAutoReject = (isRedOffer && settings.autoRejectRedOffers) ||
                    (result.isRiskArea && settings.autoRejectRiskAreas)
            val shouldAutoAccept = (isGreenOffer && !result.isRiskArea && settings.autoAcceptGreenOffers)

            if (settings.voiceAlertsEnabled && isTtsReady) {
                if (result.isRiskArea) {
                    val area = result.detectedRiskArea ?: "perigosa"
                    val msg = if (shouldAutoReject) {
                        "Atenção! Área de risco detectada em $area. Recusando por segurança!"
                    } else {
                        "Atenção! Área de risco detectada em $area. Corrida vermelha!"
                    }
                    textToSpeech?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "kmcerto_voice_${System.currentTimeMillis()}")
                } else if (shouldAutoReject) {
                    if (settings.autoRejectDelaySeconds > 0) {
                        textToSpeech?.speak(
                            "Oferta vermelha. Recusando em ${settings.autoRejectDelaySeconds} segundos.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "kmcerto_voice_${System.currentTimeMillis()}"
                        )
                    } else {
                        speakRejectConfirmation()
                    }
                } else if (shouldAutoAccept) {
                    if (settings.autoAcceptDelaySeconds > 0) {
                        textToSpeech?.speak(
                            "Oferta verde excelente! Aceitando em ${settings.autoAcceptDelaySeconds} segundos.",
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "kmcerto_voice_${System.currentTimeMillis()}"
                        )
                    } else {
                        speakAcceptConfirmation()
                    }
                } else {
                    speakVoiceAlert(result)
                }
            }

            // 9. Agendamento ou execução da Recusa ou Aceite Automático
            if (shouldAutoReject) {
                scheduleAutoReject(settings.autoRejectDelaySeconds)
            } else if (shouldAutoAccept) {
                scheduleAutoAccept(settings.autoAcceptDelaySeconds)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar evento de acessibilidade: ${e.message}", e)
        } finally {
            // Recicla o nó raiz para evitar vazamentos de memória no Android
            rootNode.recycle()
        }
    }

    /**
     * Agenda ou executa imediatamente a recusa da corrida vermelha na tela.
     */
    private fun scheduleAutoReject(delaySeconds: Int) {
        cancelAutoReject()

        if (delaySeconds <= 0) {
            val currentRoot = rootInActiveWindow ?: return
            try {
                val rejected = findAndClickRejectButton(currentRoot)
                if (rejected) {
                    Log.i(TAG, "Oferta vermelha recusada instantaneamente.")
                    speakRejectConfirmation()
                }
            } finally {
                currentRoot.recycle()
            }
        } else {
            val delayMs = delaySeconds * 1000L
            Log.i(TAG, "Agendando recusa automática de oferta vermelha para daqui a $delaySeconds segundos...")
            val runnable = Runnable {
                val activeRoot = rootInActiveWindow
                if (activeRoot != null) {
                    try {
                        val rejected = findAndClickRejectButton(activeRoot)
                        if (rejected) {
                            Log.i(TAG, "Oferta vermelha recusada com sucesso após contagem regressiva.")
                            speakRejectConfirmation()
                        }
                    } finally {
                        activeRoot.recycle()
                    }
                }
                pendingRejectRunnable = null
            }
            pendingRejectRunnable = runnable
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    /**
     * Cancela uma recusa automática pendente caso o entregador decida manter a oferta.
     */
    fun cancelAutoReject() {
        pendingRejectRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingRejectRunnable = null
            Log.i(TAG, "Recusa automática pendente cancelada pelo usuário.")
        }
    }

    /**
     * Localiza e aciona o botão de recusa/rejeição nas telas do iFood, Uber e 99.
     */
    fun findAndClickRejectButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        val rejectKeywords = listOf(
            "Não aceitar",
            "Nao aceitar",
            "Recusar",
            "Rejeitar",
            "Dispensar",
            "Não quero",
            "Nao quero",
            "Recusar corrida",
            "Recusar entrega",
            "Rejeitar pedido"
        )

        // 1. Busca prioritária pelo método rápido nativo de texto
        for (keyword in rejectKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (clickNodeOrParent(node)) {
                        Log.i(TAG, "Recusa acionada via keyword: '$keyword'")
                        return true
                    }
                }
            }
        }

        // 2. Busca recursiva para apanhar contentDescription ou botões sem texto direto
        return findAndClickRejectRecursive(rootNode)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    return true
                }
            }
            val parent = current.parent
            if (current != node) {
                current.recycle()
            }
            current = parent
            depth++
        }
        current?.recycle()
        return false
    }

    private fun findAndClickRejectRecursive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        if (isRejectAction(text) || isRejectAction(desc)) {
            if (clickNodeOrParent(node)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val clicked = findAndClickRejectRecursive(child)
                child.recycle()
                if (clicked) return true
            }
        }
        return false
    }

    private fun isRejectAction(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = raw.lowercase()
        // Segurança estrita: NUNCA clicar no botão de aceitar pedido
        if (lower == "aceitar" || lower.startsWith("aceitar") || lower == "aceitar pedido") {
            return false
        }
        return lower.contains("recusar") ||
                lower.contains("rejeitar") ||
                lower.contains("não aceitar") ||
                lower.contains("nao aceitar") ||
                lower.contains("dispensar") ||
                lower.contains("não quero") ||
                lower.contains("nao quero")
    }

    private fun speakRejectConfirmation() {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(
                "Oferta vermelha recusada automaticamente.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "kmcerto_reject_${System.currentTimeMillis()}"
            )
        }
    }

    /**
     * Agenda ou executa imediatamente o aceite da corrida verde excelente na tela.
     */
    private fun scheduleAutoAccept(delaySeconds: Int) {
        cancelAutoAccept()

        if (delaySeconds <= 0) {
            val currentRoot = rootInActiveWindow ?: return
            try {
                val accepted = findAndClickAcceptButton(currentRoot)
                if (accepted) {
                    Log.i(TAG, "Oferta verde aceita instantaneamente.")
                    speakAcceptConfirmation()
                }
            } finally {
                currentRoot.recycle()
            }
        } else {
            val delayMs = delaySeconds * 1000L
            Log.i(TAG, "Agendando aceite automático de oferta verde para daqui a $delaySeconds segundos...")
            val runnable = Runnable {
                val activeRoot = rootInActiveWindow
                if (activeRoot != null) {
                    try {
                        val accepted = findAndClickAcceptButton(activeRoot)
                        if (accepted) {
                            Log.i(TAG, "Oferta verde aceita com sucesso após contagem regressiva.")
                            speakAcceptConfirmation()
                        }
                    } finally {
                        activeRoot.recycle()
                    }
                }
                pendingAcceptRunnable = null
            }
            pendingAcceptRunnable = runnable
            mainHandler.postDelayed(runnable, delayMs)
        }
    }

    /**
     * Cancela um aceite automático pendente caso o entregador decida cancelar.
     */
    fun cancelAutoAccept() {
        pendingAcceptRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingAcceptRunnable = null
            Log.i(TAG, "Aceite automático pendente cancelado pelo usuário.")
        }
    }

    private fun speakAcceptConfirmation() {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(
                "Oferta verde aceita com sucesso!",
                TextToSpeech.QUEUE_FLUSH,
                null,
                "kmcerto_accept_${System.currentTimeMillis()}"
            )
        }
    }

    /**
     * Localiza e aciona o botão de aceite nas telas do iFood, Uber e 99.
     */
    fun findAndClickAcceptButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false

        val acceptKeywords = listOf(
            "Aceitar corrida",
            "Aceitar entrega",
            "Aceitar pedido",
            "Aceitar",
            "Confirmar",
            "Pegar corrida",
            "Pegar pedido"
        )

        // 1. Busca prioritária pelo método rápido nativo de texto
        for (keyword in acceptKeywords) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (!isRejectAction(text) && !isRejectAction(desc)) {
                        if (clickNodeOrParent(node)) {
                            Log.i(TAG, "Aceite acionado via keyword: '$keyword'")
                            return true
                        }
                    }
                }
            }
        }

        // 2. Busca recursiva para apanhar contentDescription ou botões aninhados
        return findAndClickAcceptRecursive(rootNode)
    }

    private fun findAndClickAcceptRecursive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        if (isAcceptAction(text) || isAcceptAction(desc)) {
            if (!isRejectAction(text) && !isRejectAction(desc)) {
                if (clickNodeOrParent(node)) {
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val clicked = findAndClickAcceptRecursive(child)
                child.recycle()
                if (clicked) return true
            }
        }
        return false
    }

    private fun isAcceptAction(raw: String): Boolean {
        if (raw.isBlank()) return false
        val lower = raw.lowercase()
        // Trava estrita de segurança: NUNCA clicar em botões que tenham negação ou recusa
        if (lower.contains("não") || lower.contains("nao") || lower.contains("recusar") || lower.contains("rejeitar")) {
            return false
        }
        return lower == "aceitar" ||
                lower.startsWith("aceitar ") ||
                lower == "aceitar pedido" ||
                lower == "aceitar entrega" ||
                lower == "aceitar corrida" ||
                lower == "confirmar" ||
                lower == "pegar"
    }

    /**
     * Emite um aviso sonoro por voz sintetizada curto e claro.
     * Exemplo: "Oferta verde. Lucro de 12 reais, 3 reais por quilômetro."
     */
    private fun speakVoiceAlert(result: DeliveryCalculationResult) {
        val speechText = VoiceAlertFormatter.buildSpeechText(result)
        Log.d(TAG, "Reproduzindo aviso por voz: $speechText")
        textToSpeech?.speak(
            speechText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "kmcerto_voice_${System.currentTimeMillis()}"
        )
    }

    /**
     * Método auxiliar recursivo para recolher todo o texto visível da árvore
     * [AccessibilityNodeInfo] numa lista de strings.
     */
    fun collectVisibleTexts(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null || !node.isVisibleToUser) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            texts.add(text)
        }

        val contentDesc = node.contentDescription?.toString()?.trim()
        if (!contentDesc.isNullOrEmpty() && contentDesc != text) {
            texts.add(contentDesc)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectVisibleTexts(child, texts)
                child.recycle()
            }
        }
    }

    /**
     * Mapeia o pacote da aplicação para a plataforma correspondente ("IFOOD", "UBER" ou "99").
     */
    private fun resolvePlatformFromPackage(packageName: String): String? {
        val lower = packageName.lowercase()
        return when {
            lower.contains("ifood") || lower.contains("brainweb") -> DeliveryOffer.PLATFORM_IFOOD
            lower.contains("uber") -> DeliveryOffer.PLATFORM_UBER
            lower.contains("99") || lower.contains("taxis99") || lower.contains("didi") -> DeliveryOffer.PLATFORM_99
            // Permite teste interno no próprio app KmCerto
            lower == packageName.lowercase() && lower.contains("entregador") -> DeliveryOffer.PLATFORM_IFOOD
            else -> null
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "KmCerto DeliveryAccessibilityService interrompido.")
    }

    override fun onDestroy() {
        cancelAutoReject()
        cancelAutoAccept()
        if (instance == this) {
            instance = null
        }
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DeliveryAccessibility"
        private const val DEBOUNCE_TIME_MS = 8000L // 8 segundos para evitar re-disparos da mesma oferta

        var instance: DeliveryAccessibilityService? = null
            private set

        @Volatile
        var isCurrentWeatherRain: Boolean = false
            private set

        @Volatile
        var lastWeatherDescription: String = "Tempo Firme ☀️"
            private set

        fun updateWeatherStatus(isRaining: Boolean, description: String) {
            isCurrentWeatherRain = isRaining
            lastWeatherDescription = description
        }

        fun cancelPendingAutoReject() {
            instance?.cancelAutoReject()
        }

        fun cancelPendingAutoAccept() {
            instance?.cancelAutoAccept()
        }
    }
}
