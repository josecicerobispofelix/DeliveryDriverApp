package br.com.entregador.lucro.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import br.com.entregador.lucro.R
import br.com.entregador.lucro.data.repository.EncryptedDeliverySettingsRepository
import br.com.entregador.lucro.data.repository.SQLiteDeliveryHistoryRepository
import br.com.entregador.lucro.domain.calculator.DeliveryCalculator
import br.com.entregador.lucro.domain.formatter.VoiceAlertFormatter
import br.com.entregador.lucro.domain.model.DeliveryCalculationResult
import br.com.entregador.lucro.domain.model.DeliveryOffer
import br.com.entregador.lucro.domain.model.TrafficLightStatus
import br.com.entregador.lucro.sound.SoundAlertManager
import br.com.entregador.lucro.ui.HistoryActivity
import br.com.entregador.lucro.ui.MainActivity
import java.util.Locale

/**
 * Serviço em primeiro plano (Foreground Service) do KMCERTO.
 * Mantém uma bolha flutuante permanente na tela (estilo GigU e StopClub) com arraste,
 * ancoragem nas bordas (snap to edge), alvo de lixeira para dispensar (drag to trash),
 * mini-menu flutuante rápido e expansão do card do semáforo com cálculo de lucro.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null

    // 1. Bolha Flutuante Permanente (Ícone estilo GigU)
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    // 2. Card Expandido de Cálculo de Oferta (Semáforo)
    private var cardView: View? = null
    private var cardParams: WindowManager.LayoutParams? = null

    // 3. Alvo de Lixeira na Base da Tela (Drag to Trash)
    private var trashView: View? = null
    private var trashParams: WindowManager.LayoutParams? = null
    private var isHoveringTrash = false

    // 4. Mini-Menu Flutuante Rápido de Ações e Resumo
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    // Cache do último cálculo efetuado
    private var lastResult: DeliveryCalculationResult? = null
    private var lastPlatform: String = DeliveryOffer.PLATFORM_IFOOD

    // Síntese de Voz (Text-to-Speech)
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissCardRunnable = Runnable {
        removeCard()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initTextToSpeech()
        startAsForeground()
        showBubble()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localePtBr = Locale("pt", "BR")
                val langResult = textToSpeech?.setLanguage(localePtBr)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech?.setLanguage(Locale("pt"))
                }
                isTtsReady = true
                Log.d(TAG, "OverlayService TextToSpeech pronto em Português.")
            } else {
                Log.e(TAG, "Falha ao inicializar TextToSpeech (status: $status).")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BUBBLE -> {
                showBubble()
            }
            ACTION_SHOW_OFFER -> {
                showBubble() // Garante que a bolha está ativa
                removeMenu()
                val platform = intent.getStringExtra(EXTRA_PLATFORM) ?: DeliveryOffer.PLATFORM_IFOOD
                val grossValue = intent.getDoubleExtra(EXTRA_GROSS_VALUE, 0.0)
                val distanceKm = intent.getDoubleExtra(EXTRA_DISTANCE_KM, 0.0)
                val timeMinutes = intent.getIntExtra(EXTRA_TIME_MINUTES, 0)
                val destination = intent.getStringExtra(EXTRA_DESTINATION)
                val isRiskAreaExtra = intent.getBooleanExtra(EXTRA_IS_RISK_AREA, false)
                val riskAreaNameExtra = intent.getStringExtra(EXTRA_RISK_AREA_NAME)
                val orderCount = intent.getIntExtra(EXTRA_ORDER_COUNT, 1)
                val isRainingExtra = intent.getBooleanExtra(EXTRA_IS_RAINING, false)
                val isSteepExtra = intent.getBooleanExtra(EXTRA_IS_STEEP, false)
                val elevationGainExtra = intent.getIntExtra(EXTRA_ELEVATION_GAIN, 0)

                val offer = DeliveryOffer(
                    platform = platform,
                    grossValue = grossValue,
                    totalDistanceKm = distanceKm,
                    estimatedTimeMinutes = timeMinutes,
                    destinationAddress = destination,
                    destinationNeighborhood = destination,
                    orderCount = orderCount
                )
                val settings = EncryptedDeliverySettingsRepository(this).getSettings()
                var calculation = DeliveryCalculator.calculate(
                    offer = offer,
                    settings = settings,
                    isRaining = isRainingExtra,
                    isSteepIncline = isSteepExtra,
                    elevationGainMeters = elevationGainExtra
                )

                if (isRiskAreaExtra) {
                    calculation = calculation.copy(
                        isRiskArea = true,
                        detectedRiskArea = riskAreaNameExtra ?: calculation.detectedRiskArea,
                        trafficLightStatus = TrafficLightStatus.RED
                    )
                }

                lastResult = calculation
                lastPlatform = platform

                showFloatingCard(calculation, platform)
            }
            ACTION_TOGGLE_CARD -> {
                toggleCard()
            }
            ACTION_HIDE_CARD -> {
                removeCard()
                removeMenu()
            }
            ACTION_TEST_VOICE -> {
                playTestVoice()
            }
            ACTION_TEST_SOUND -> {
                SoundAlertManager.getInstance(this).playGreen()
            }
            ACTION_STOP_SERVICE -> {
                removeMenu()
                hideTrash()
                removeCard()
                removeBubble()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun playTestVoice() {
        val dummyOffer = DeliveryOffer(
            platform = DeliveryOffer.PLATFORM_IFOOD,
            grossValue = 18.50,
            totalDistanceKm = 5.2,
            estimatedTimeMinutes = 18
        )
        val settings = EncryptedDeliverySettingsRepository(this).getSettings()
        val calc = DeliveryCalculator.calculate(dummyOffer, settings)
        speakCalculation(calc)
    }

    private fun speakCalculation(result: DeliveryCalculationResult) {
        val settings = EncryptedDeliverySettingsRepository(this).getSettings()
        if (!settings.voiceAlertsEnabled) return

        val speechText = VoiceAlertFormatter.buildSpeechText(result)
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(
                speechText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "kmcerto_voice_${System.currentTimeMillis()}"
            )
        } else {
            // Se ainda está inicializando, tenta novamente após 500ms
            mainHandler.postDelayed({
                textToSpeech?.speak(
                    speechText,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "kmcerto_voice_${System.currentTimeMillis()}"
                )
            }, 500)
        }
    }

    /**
     * Inicia a notificação permanente na barra de status exigida para Foreground Service.
     */
    private fun startAsForeground() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KMCERTO Flutuante Ativo")
            .setContentText("Bolha flutuando na tela • Vigiando ofertas (iFood, Uber, 99)")
            .setSmallIcon(R.drawable.ic_kmcerto)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Exibe ou atualiza a Bolha Flutuante permanente (ícone redondo estilo GigU).
     */
    fun showBubble() {
        if (!Settings.canDrawOverlays(this)) return

        mainHandler.post {
            if (bubbleView != null) return@post // Já está visível

            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.overlay_bubble, null)
            bubbleView = view

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = resources.displayMetrics.density
            val bubbleSizePx = (54 * density).toInt()

            val params = WindowManager.LayoutParams(
                bubbleSizePx,
                bubbleSizePx,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (12 * density).toInt()
                y = (280 * density).toInt()
            }
            bubbleParams = params

            setupBubbleTouchListener(view, params)

            try {
                windowManager?.addView(view, params)
                isBubbleActive = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Configura o OnTouchListener da bolha com suporte a arrastar livremente,
     * alvo de lixeira para fechar (drag to trash), ancoragem nas bordas (snap)
     * e toque rápido para abrir o Mini-Menu do KMCERTO.
     */
    private fun setupBubbleTouchListener(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.coerceAtLeast(24)

        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false
            private var touchStartTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                Log.d("KMCERTO_BUBBLE", "onTouch: action=${event.action}, x=${event.rawX}, y=${event.rawY}")
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        isDragging = false
                        isHoveringTrash = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true
                            removeMenu()
                            removeCard()
                            showTrash()
                        }

                        if (isDragging) {
                            params.x = initialX + dx
                            params.y = initialY + dy

                            try {
                                windowManager?.updateViewLayout(bubbleView, params)
                            } catch (e: Exception) {
                                // View pode ter sido desanexada
                            }

                            // Verifica se a bolha está sobre a área da lixeira na base da tela
                            val displayMetrics = resources.displayMetrics
                            val density = displayMetrics.density
                            val screenHeight = displayMetrics.heightPixels
                            val screenWidth = displayMetrics.widthPixels
                            val trashZoneY = screenHeight - (180 * density).toInt()
                            val trashCenterX = screenWidth / 2
                            val overTrash = (event.rawY > trashZoneY) && (Math.abs(event.rawX - trashCenterX) < (90 * density).toInt())

                            if (overTrash != isHoveringTrash) {
                                isHoveringTrash = overTrash
                                updateTrashState(isHoveringTrash)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - touchStartTime
                        val totalDx = Math.abs(event.rawX - initialTouchX)
                        val totalDy = Math.abs(event.rawY - initialTouchY)
                        val density = resources.displayMetrics.density
                        val tapThreshold = Math.max(touchSlop.toFloat(), 22f * density)
                        val isTap = !isDragging && duration < 550 && totalDx < tapThreshold && totalDy < tapThreshold
                        Log.d("KMCERTO_BUBBLE", "ACTION_UP: dur=$duration, dx=$totalDx, dy=$totalDy, slop=$touchSlop, tapThreshold=$tapThreshold, isTap=$isTap")

                        if (isTap) {
                            // Toque rápido intencional: alterna o mini-menu ou fecha card aberto
                            v.performClick()
                            hideTrash()
                            if (cardView != null) {
                                removeCard()
                            } else if (menuView != null) {
                                removeMenu()
                            } else {
                                Log.d("KMCERTO_BUBBLE", "Triggering showMiniMenu() from isTap")
                                showMiniMenu()
                            }
                        } else if (isDragging) {
                            if (isHoveringTrash) {
                                // Arrastou para a lixeira: dispensa a bolha e desativa o serviço
                                hideTrash()
                                removeMenu()
                                removeCard()
                                removeBubble()
                                stopForeground(STOP_FOREGROUND_REMOVE)
                                stopSelf()
                                Toast.makeText(this@OverlayService, "✓ Bolha do KMCERTO fechada", Toast.LENGTH_SHORT).show()
                            } else {
                                hideTrash()
                                snapBubbleToEdge()
                            }
                        } else {
                            hideTrash()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    /**
     * Exibe o alvo circular da lixeira na base da tela ao começar a arrastar a bolha.
     */
    private fun showTrash() {
        if (trashView != null || !Settings.canDrawOverlays(this)) return

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_trash, null)
        trashView = view

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (45 * density).toInt()
        }
        trashParams = params

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atualiza o visual do alvo da lixeira quando a bolha está sobreposta.
     */
    private fun updateTrashState(hovering: Boolean) {
        val view = trashView ?: return
        val frameCircle = view.findViewById<View>(R.id.frameTrashCircle)
        val tvLabel = view.findViewById<TextView>(R.id.tvTrashLabel)
        if (hovering) {
            frameCircle?.setBackgroundResource(R.drawable.bg_overlay_trash_active)
            tvLabel?.text = "Solte para fechar!"
            tvLabel?.setTextColor(Color.parseColor("#FFFFFF"))
            triggerHaptic()
        } else {
            frameCircle?.setBackgroundResource(R.drawable.bg_overlay_trash)
            tvLabel?.text = "Solte aqui para fechar"
            tvLabel?.setTextColor(Color.parseColor("#EF4444"))
        }
    }

    /**
     * Remove o alvo da lixeira da tela.
     */
    private fun hideTrash() {
        if (trashView != null) {
            try {
                windowManager?.removeView(trashView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                trashView = null
                trashParams = null
                isHoveringTrash = false
            }
        }
    }

    /**
     * Emite vibração tátil rápida ao passar a bolha sobre a lixeira.
     */
    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (e: Exception) {
            // Vibração opcional
        }
    }

    /**
     * Abre o Mini-Menu de Atalhos Rápidos ao tocar na bolha flutuante.
     */
    fun showMiniMenu() {
        if (!Settings.canDrawOverlays(this)) return

        mainHandler.post {
            Log.d("KMCERTO_BUBBLE", "showMiniMenu: starting to inflate and display mini menu")
            removeCard()
            if (menuView != null) {
                removeMenu()
                return@post
            }

            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.overlay_menu, null)
            menuView = view

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = resources.displayMetrics
            val density = displayMetrics.density
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val menuWidth = Math.min((290 * density).toInt(), (screenWidth * 0.88).toInt())

            val bubbleX = bubbleParams?.x ?: (12 * density).toInt()
            val bubbleY = bubbleParams?.y ?: (280 * density).toInt()

            val isBubbleOnLeft = bubbleX < screenWidth / 2
            val targetX = if (isBubbleOnLeft) {
                bubbleX + (60 * density).toInt()
            } else {
                bubbleX - menuWidth - (10 * density).toInt()
            }.coerceIn((10 * density).toInt(), screenWidth - menuWidth - (10 * density).toInt())

            val targetY = (bubbleY - (20 * density).toInt()).coerceIn((60 * density).toInt(), screenHeight - (420 * density).toInt())

            val params = WindowManager.LayoutParams(
                menuWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = targetX
                y = targetY
            }
            menuParams = params

            // Bind das views do mini-menu
            val btnClose = view.findViewById<View>(R.id.btnMenuClose)
            val btnSummary = view.findViewById<View>(R.id.btnMenuSummary)
            val tvDailyProfit = view.findViewById<TextView>(R.id.tvMenuDailyNetProfit)
            val tvDailyMetrics = view.findViewById<TextView>(R.id.tvMenuDailyMetrics)
            val tvDailyGoalProgress = view.findViewById<TextView>(R.id.tvMenuDailyGoalProgress)

            val btnToggleVoice = view.findViewById<View>(R.id.btnMenuToggleVoice)
            val tvVoiceIcon = view.findViewById<TextView>(R.id.tvMenuVoiceIcon)
            val tvVoiceLabel = view.findViewById<TextView>(R.id.tvMenuVoiceLabel)
            val tvVoiceAction = view.findViewById<TextView>(R.id.tvMenuVoiceAction)

            val btnOpenApp = view.findViewById<View>(R.id.btnMenuOpenApp)
            val btnDismissBubble = view.findViewById<View>(R.id.btnMenuDismissBubble)

            val settingsRepo = EncryptedDeliverySettingsRepository(this)
            var currentSettings = settingsRepo.getSettings()

            // 1. Carrega dados do SQLite em tempo real
            try {
                val summary = SQLiteDeliveryHistoryRepository(this).getTodaySummary()
                tvDailyProfit.text = String.format(Locale.getDefault(), "R$ %.2f", summary.totalNetProfit)
                tvDailyMetrics.text = String.format(
                    Locale.getDefault(),
                    "%.1f km rodados • %d corridas (🟢%d 🟡%d 🔴%d)",
                    summary.totalDistanceKm, summary.totalOffersCount,
                    summary.greenCount, summary.yellowCount, summary.redCount
                )
                val pct = summary.getGoalProgressPercent(currentSettings.dailyRevenueGoal)
                val remaining = summary.getRemainingToGoal(currentSettings.dailyRevenueGoal)
                tvDailyGoalProgress?.text = String.format(
                    Locale.getDefault(),
                    "🎯 Meta: R$ %.2f / R$ %.0f (%d%%) • Falta R$ %.2f",
                    summary.totalNetProfit, currentSettings.dailyRevenueGoal, pct, remaining
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Configura Toggle de Voz

            fun updateVoiceUi(enabled: Boolean) {
                if (enabled) {
                    tvVoiceIcon.text = "🔊"
                    tvVoiceLabel.text = "Avisos de Voz: Ativados"
                    tvVoiceAction.text = "SILENCIAR"
                    tvVoiceAction.setTextColor(Color.parseColor("#EF4444"))
                } else {
                    tvVoiceIcon.text = "🔇"
                    tvVoiceLabel.text = "Avisos de Voz: Silenciados"
                    tvVoiceAction.text = "ATIVAR"
                    tvVoiceAction.setTextColor(Color.parseColor("#00E676"))
                }
            }
            updateVoiceUi(currentSettings.voiceAlertsEnabled)

            btnToggleVoice.setOnClickListener {
                val newEnabled = !currentSettings.voiceAlertsEnabled
                currentSettings = currentSettings.copy(voiceAlertsEnabled = newEnabled)
                settingsRepo.saveSettings(currentSettings)
                updateVoiceUi(newEnabled)
                val msg = if (newEnabled) "🔊 Avisos de voz ativados" else "🔇 Avisos de voz silenciados"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (newEnabled) {
                    textToSpeech?.speak("Avisos de voz ativados", TextToSpeech.QUEUE_FLUSH, null, "voice_toggle")
                }
            }

            // 3. Toque no resumo abre o Histórico Completo / Relatórios (Fase 7), ou no botão app abre a MainActivity
            btnSummary.setOnClickListener {
                removeMenu()
                openHistoryActivity()
            }

            btnOpenApp.setOnClickListener {
                removeMenu()
                openMainActivity()
            }

            // 4. Fechar botão 'X' do Mini-Menu
            btnClose.setOnClickListener {
                removeMenu()
            }

            // 5. Fechar Bolha Flutuante
            btnDismissBubble.setOnClickListener {
                removeMenu()
                removeCard()
                removeBubble()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Toast.makeText(this, "✓ Bolha do KMCERTO fechada", Toast.LENGTH_SHORT).show()
            }

            try {
                windowManager?.addView(view, params)
                Log.d("KMCERTO_BUBBLE", "showMiniMenu: SUCCESS added view to windowManager! params=($targetX, $targetY, ${menuWidth}x${params.height})")
            } catch (e: Exception) {
                Log.e("KMCERTO_BUBBLE", "showMiniMenu: FAILED to add view to windowManager", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Remove o Mini-Menu da tela se estiver aberto.
     */
    fun removeMenu() {
        if (menuView != null) {
            try {
                windowManager?.removeView(menuView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                menuView = null
                menuParams = null
            }
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun openHistoryActivity() {
        val intent = Intent(this, HistoryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    /**
     * Ancora a bolha na borda esquerda ou direita mais próxima (estilo GigU).
     */
    private fun snapBubbleToEdge() {
        val params = bubbleParams ?: return
        val view = bubbleView ?: return
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val bubbleWidth = params.width.takeIf { it > 0 } ?: (54 * density).toInt()

        val middleX = screenWidth / 2
        val margin = (10 * density).toInt()
        val targetX = if ((params.x + bubbleWidth / 2) < middleX) margin else (screenWidth - bubbleWidth - margin)

        params.x = targetX
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Alterna a visibilidade do Card do Semáforo ou Mini-Menu ao tocar na bolha flutuante.
     */
    fun toggleCard() {
        if (cardView != null) {
            removeCard()
        } else if (menuView != null) {
            removeMenu()
        } else {
            showMiniMenu()
        }
    }

    /**
     * Infla e exibe o Card do Semáforo (detalhes de lucro, taxas e semáforo).
     */
    fun showFloatingCard(result: DeliveryCalculationResult, platform: String) {
        if (!Settings.canDrawOverlays(this)) return

        mainHandler.post {
            removeMenu()
            if (cardView != null) {
                removeCard()
            }

            val inflater = LayoutInflater.from(this)
            val view = inflater.inflate(R.layout.overlay_card, null)
            cardView = view

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = resources.displayMetrics.density
            val screenWidth = resources.displayMetrics.widthPixels
            val cardWidth = Math.min((330 * density).toInt(), (screenWidth * 0.90).toInt())

            val bubbleY = bubbleParams?.y ?: (280 * density).toInt()
            val cardY = Math.max((90 * density).toInt(), bubbleY - (40 * density).toInt())

            val params = WindowManager.LayoutParams(
                cardWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = cardY
            }
            cardParams = params

            val viewIndicator = view.findViewById<View>(R.id.viewTrafficLightIndicator)
            val tvStatus = view.findViewById<TextView>(R.id.tvOverlayStatus)
            val btnClose = view.findViewById<TextView>(R.id.btnOverlayClose)
            val tvNetProfit = view.findViewById<TextView>(R.id.tvOverlayNetProfit)
            val tvRatePerKm = view.findViewById<TextView>(R.id.tvOverlayRatePerKm)
            val tvRatePerHour = view.findViewById<TextView>(R.id.tvOverlayRatePerHour)
            val rootCard = view.findViewById<View>(R.id.overlayRootLayout)
            val layoutRiskArea = view.findViewById<View>(R.id.layoutOverlayRiskArea)
            val tvRiskAreaNotice = view.findViewById<TextView>(R.id.tvOverlayRiskAreaNotice)
            val layoutDest = view.findViewById<View>(R.id.layoutOverlayDestination)
            val tvDest = view.findViewById<TextView>(R.id.tvOverlayDestination)

            // 1. Destino e Bairro (Fase 5)
            if (!result.destinationNeighborhood.isNullOrBlank()) {
                layoutDest?.visibility = View.VISIBLE
                tvDest?.text = "Entrega: ${result.destinationNeighborhood}"
            } else {
                layoutDest?.visibility = View.GONE
            }

            // 2. Alerta Visual de Área de Risco (Fase 5)
            if (result.isRiskArea) {
                layoutRiskArea?.visibility = View.VISIBLE
                val areaName = result.detectedRiskArea ?: "PERIGOSA"
                tvRiskAreaNotice?.text = "⚠️ ÁREA DE RISCO: ${areaName.uppercase()}"
            } else {
                layoutRiskArea?.visibility = View.GONE
            }

            // 3. Multi-pedidos / Rota Dupla (Fase 7)
            val layoutMultiOrder = view.findViewById<View>(R.id.layoutMultiOrder)
            val tvMultiOrderBadge = view.findViewById<TextView>(R.id.tvMultiOrderBadge)
            if (result.orderCount > 1) {
                layoutMultiOrder?.visibility = View.VISIBLE
                val label = if (result.orderCount == 2) "Rota Dupla" else "${result.orderCount} Pedidos"
                tvMultiOrderBadge?.text = String.format(
                    Locale.getDefault(),
                    "📦 %s: %d Entregas (R$ %.2f / entrega)",
                    label,
                    result.orderCount,
                    result.grossValuePerOrder
                )
            } else {
                layoutMultiOrder?.visibility = View.GONE
            }

            val currentSettings = EncryptedDeliverySettingsRepository(this).getSettings()

            // 4. Badge de Modo Chuva (Fase 8)
            val layoutRainMode = view.findViewById<View>(R.id.layoutRainModeBadge)
            val tvRainMode = view.findViewById<TextView>(R.id.tvRainModeBadge)
            if (result.isRainActive) {
                layoutRainMode?.visibility = View.VISIBLE
                val bonus = currentSettings.rainFloorBonus
                tvRainMode?.text = String.format(
                    Locale.getDefault(),
                    "🌧️ MODO CHUVA: Piso +R$ %.2f (Min R$ %.2f)",
                    bonus,
                    result.effectiveMinFloor
                )
            } else {
                layoutRainMode?.visibility = View.GONE
            }

            // 5. Badge de Subida Íngreme / Altimetria (Fase 8)
            val layoutSteep = view.findViewById<View>(R.id.layoutSteepInclineBadge)
            val tvSteep = view.findViewById<TextView>(R.id.tvSteepInclineBadge)
            if (result.isSteepIncline) {
                layoutSteep?.visibility = View.VISIBLE
                val elevText = if (result.elevationGainMeters > 0) " (+${result.elevationGainMeters}m)" else ""
                tvSteep?.text = "🚴⚠️ SUBIDA ÍNGREME DETECTADA$elevText"
            } else {
                layoutSteep?.visibility = View.GONE
            }

            tvNetProfit.text = String.format(Locale.getDefault(), "R$ %.2f", result.netProfit)
            tvRatePerKm.text = String.format(Locale.getDefault(), "R$ %.2f/km", result.earningsPerKm)
            tvRatePerHour.text = String.format(Locale.getDefault(), "R$ %.2f/h", result.earningsPerHour)

            val colorHex = when (result.trafficLightStatus) {
                TrafficLightStatus.GREEN -> {
                    tvStatus.text = "🟢 BOA OFERTA ($platform)"
                    "#00E676"
                }
                TrafficLightStatus.YELLOW -> {
                    tvStatus.text = "🟡 ATENÇÃO ($platform)"
                    "#FFB300"
                }
                TrafficLightStatus.RED -> {
                    if (result.isRiskArea) {
                        tvStatus.text = "🔴 ÁREA DE RISCO ($platform)"
                    } else {
                        tvStatus.text = "🔴 RECUSAR ($platform)"
                    }
                    "#FF1744"
                }
            }
            val statusColor = Color.parseColor(colorHex)
            tvNetProfit.setTextColor(statusColor)

            val indicatorBg = viewIndicator.background as? GradientDrawable
            indicatorBg?.setColor(statusColor)

            val cardBg = rootCard.background as? GradientDrawable
            cardBg?.setStroke(4, statusColor)

            btnClose.setOnClickListener {
                removeCard()
            }

            // Exibição e controle de Recusa Automática no Card do Semáforo
            val layoutAutoReject = view.findViewById<View>(R.id.layoutAutoRejectNotice)
            val tvAutoRejectCountdown = view.findViewById<TextView>(R.id.tvAutoRejectCountdown)
            val btnCancelAutoReject = view.findViewById<TextView>(R.id.btnCancelAutoReject)

            val shouldAutoReject = (result.trafficLightStatus == TrafficLightStatus.RED && currentSettings.autoRejectRedOffers) ||
                    (result.isRiskArea && currentSettings.autoRejectRiskAreas)

            if (shouldAutoReject) {
                layoutAutoReject?.visibility = View.VISIBLE
                val delay = currentSettings.autoRejectDelaySeconds
                val prefix = if (result.isRiskArea) "⚠️ Risco! Recusando" else "🔴 Recusando"
                tvAutoRejectCountdown?.text = if (delay > 0) "$prefix em ${delay}s..." else "$prefix oferta..."
                btnCancelAutoReject?.setOnClickListener {
                    DeliveryAccessibilityService.cancelPendingAutoReject()
                    layoutAutoReject?.visibility = View.GONE
                    Toast.makeText(this, "✓ Recusa automática cancelada para esta oferta", Toast.LENGTH_SHORT).show()
                }
            } else {
                layoutAutoReject?.visibility = View.GONE
            }

            // Exibição e controle de Aceite Automático no Card do Semáforo (Fase 6)
            val layoutAutoAccept = view.findViewById<View>(R.id.layoutAutoAcceptNotice)
            val tvAutoAcceptCountdown = view.findViewById<TextView>(R.id.tvAutoAcceptCountdown)
            val btnCancelAutoAccept = view.findViewById<TextView>(R.id.btnCancelAutoAccept)

            val shouldAutoAccept = (result.trafficLightStatus == TrafficLightStatus.GREEN && !result.isRiskArea && currentSettings.autoAcceptGreenOffers)

            if (shouldAutoAccept) {
                layoutAutoAccept?.visibility = View.VISIBLE
                val delay = currentSettings.autoAcceptDelaySeconds
                tvAutoAcceptCountdown?.text = if (delay > 0) "🟢 Aceitando em ${delay}s..." else "🟢 Aceitando oferta..."
                btnCancelAutoAccept?.setOnClickListener {
                    DeliveryAccessibilityService.cancelPendingAutoAccept()
                    layoutAutoAccept?.visibility = View.GONE
                    Toast.makeText(this, "✓ Aceite automático cancelado para esta oferta", Toast.LENGTH_SHORT).show()
                }
            } else {
                layoutAutoAccept?.visibility = View.GONE
            }

            setupCardDragListener(view, params)

            try {
                windowManager?.addView(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Emite o som de alerta (Bipes / Efeitos) se habilitado (Fase 7)
            if (currentSettings.soundAlertsEnabled) {
                SoundAlertManager.getInstance(this).playForCalculation(result)
            }

            // Emite o aviso de voz sintetizado em português
            speakCalculation(result)

            resetAutoDismissTimer()
        }
    }

    private fun setupCardDragListener(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        params.x = initialX + dx
                        params.y = initialY + dy

                        try {
                            windowManager?.updateViewLayout(cardView, params)
                        } catch (e: Exception) {
                            // View desanexada
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun resetAutoDismissTimer() {
        mainHandler.removeCallbacks(autoDismissCardRunnable)
        mainHandler.postDelayed(autoDismissCardRunnable, AUTO_DISMISS_DELAY_MS)
    }

    /**
     * Fecha apenas o Card do Semáforo, mantendo a Bolha Flutuante visível na tela.
     */
    fun removeCard() {
        mainHandler.removeCallbacks(autoDismissCardRunnable)
        if (cardView != null) {
            try {
                windowManager?.removeView(cardView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cardView = null
                cardParams = null
            }
        }
    }

    /**
     * Remove a Bolha Flutuante.
     */
    fun removeBubble() {
        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bubbleView = null
                bubbleParams = null
                isBubbleActive = false
            }
        }
    }

    override fun onDestroy() {
        removeCard()
        removeMenu()
        hideTrash()
        removeBubble()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KMCERTO Modo Flutuante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação permanente da bolha flutuante do KMCERTO"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "OverlayService"
        const val CHANNEL_ID = "kmcerto_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val AUTO_DISMISS_DELAY_MS = 12000L // 12 segundos

        var isBubbleActive = false
            private set

        const val ACTION_START_BUBBLE = "br.com.entregador.lucro.action.START_BUBBLE"
        const val ACTION_SHOW_OFFER = "br.com.entregador.lucro.action.SHOW_OFFER"
        const val ACTION_TOGGLE_CARD = "br.com.entregador.lucro.action.TOGGLE_CARD"
        const val ACTION_HIDE_CARD = "br.com.entregador.lucro.action.HIDE_CARD"
        const val ACTION_TEST_VOICE = "br.com.entregador.lucro.action.TEST_VOICE"
        const val ACTION_TEST_SOUND = "br.com.entregador.lucro.action.TEST_SOUND"
        const val ACTION_STOP_SERVICE = "br.com.entregador.lucro.action.STOP_SERVICE"

        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_GROSS_VALUE = "extra_gross_value"
        const val EXTRA_DISTANCE_KM = "extra_distance_km"
        const val EXTRA_TIME_MINUTES = "extra_time_minutes"
        const val EXTRA_DESTINATION = "extra_destination"
        const val EXTRA_IS_RISK_AREA = "extra_is_risk_area"
        const val EXTRA_RISK_AREA_NAME = "extra_risk_area_name"
        const val EXTRA_ORDER_COUNT = "extra_order_count"
        const val EXTRA_IS_RAINING = "extra_is_raining"
        const val EXTRA_IS_STEEP = "extra_is_steep"
        const val EXTRA_ELEVATION_GAIN = "extra_elevation_gain"

        /**
         * Inicializa o Foreground Service e exibe a Bolha Flutuante imediatamente.
         */
        fun start(context: Context) {
            isBubbleActive = true
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_START_BUBBLE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Dispara a exibição de uma oferta no card do semáforo por cima da tela.
         */
        fun showOffer(
            context: Context,
            platform: String,
            grossValue: Double,
            distanceKm: Double,
            timeMinutes: Int,
            destination: String? = null,
            isRiskArea: Boolean = false,
            riskAreaName: String? = null,
            orderCount: Int = 1,
            isRaining: Boolean = false,
            isSteepIncline: Boolean = false,
            elevationGainMeters: Int = 0
        ) {
            isBubbleActive = true
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_SHOW_OFFER
                putExtra(EXTRA_PLATFORM, platform)
                putExtra(EXTRA_GROSS_VALUE, grossValue)
                putExtra(EXTRA_DISTANCE_KM, distanceKm)
                putExtra(EXTRA_TIME_MINUTES, timeMinutes)
                putExtra(EXTRA_DESTINATION, destination)
                putExtra(EXTRA_IS_RISK_AREA, isRiskArea)
                putExtra(EXTRA_RISK_AREA_NAME, riskAreaName)
                putExtra(EXTRA_ORDER_COUNT, orderCount)
                putExtra(EXTRA_IS_RAINING, isRaining)
                putExtra(EXTRA_IS_STEEP, isSteepIncline)
                putExtra(EXTRA_ELEVATION_GAIN, elevationGainMeters)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Testa o aviso sonoro por voz (TTS).
         */
        fun testVoice(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_TEST_VOICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Testa o bipe sonoro de alerta imediato (SoundPool).
         */
        fun testSound(context: Context) {
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_TEST_SOUND
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Para o serviço e fecha a bolha flutuante completamente.
         */
        fun stop(context: Context) {
            isBubbleActive = false
            val intent = Intent(context, OverlayService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
