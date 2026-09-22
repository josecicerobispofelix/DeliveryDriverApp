package br.com.entregador.lucro.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import br.com.entregador.lucro.R
import br.com.entregador.lucro.domain.model.DeliveryCalculationResult
import br.com.entregador.lucro.domain.model.TrafficLightStatus

/**
 * Gerenciador de efeitos sonoros imediatos e bipes de alta nitidez utilizando [SoundPool].
 * Projetado para motoristas e entregadores ouvirem alertas claros no capacete/fone de ouvido
 * com latência zero (0ms).
 */
class SoundAlertManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var soundGreenId: Int = 0
    private var soundYellowId: Int = 0
    private var soundRedId: Int = 0
    private var soundRiskId: Int = 0

    private var isLoaded: Boolean = false

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build()

            soundGreenId = soundPool?.load(context, R.raw.sound_green, 1) ?: 0
            soundYellowId = soundPool?.load(context, R.raw.sound_yellow, 1) ?: 0
            soundRedId = soundPool?.load(context, R.raw.sound_red, 1) ?: 0
            soundRiskId = soundPool?.load(context, R.raw.sound_risk, 1) ?: 0

            soundPool?.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) {
                    isLoaded = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar SoundPool: ${e.message}", e)
        }
    }

    fun playGreen() {
        playSound(soundGreenId)
    }

    fun playYellow() {
        playSound(soundYellowId)
    }

    fun playRed() {
        playSound(soundRedId)
    }

    fun playRisk() {
        playSound(soundRiskId)
    }

    /**
     * Toca o efeito sonoro correspondente ao resultado da oferta calculada.
     */
    fun playForCalculation(result: DeliveryCalculationResult) {
        if (result.isRiskArea) {
            playRisk()
            return
        }
        when (result.trafficLightStatus) {
            TrafficLightStatus.GREEN -> playGreen()
            TrafficLightStatus.YELLOW -> playYellow()
            TrafficLightStatus.RED -> playRed()
        }
    }

    private fun playSound(soundId: Int) {
        try {
            if (soundId != 0) {
                soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao reproduzir som id $soundId: ${e.message}")
        }
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "SoundAlertManager"

        @Volatile
        private var instance: SoundAlertManager? = null

        fun getInstance(context: Context): SoundAlertManager {
            return instance ?: synchronized(this) {
                instance ?: SoundAlertManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
