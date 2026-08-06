package com.example.hardware

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"

        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager {
            return instance ?: synchronized(this) {
                instance ?: TtsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var tts: TextToSpeech? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentlySpeakingText = MutableStateFlow("")
    val currentlySpeakingText: StateFlow<String> = _currentlySpeakingText.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val persianLocale = Locale("fa", "IR")
            val result = tts?.setLanguage(persianLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackFa = Locale("fa")
                val resultFa = tts?.setLanguage(fallbackFa)
                if (resultFa == TextToSpeech.LANG_MISSING_DATA || resultFa == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Persian language not fully supported on this TTS engine, using default locale")
                    tts?.setLanguage(Locale.getDefault())
                }
            }
            tts?.setSpeechRate(0.95f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingText.value = ""
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _currentlySpeakingText.value = ""
                }
            })

            _isInitialized.value = true
            Log.d(TAG, "TextToSpeech initialized successfully with Persian (fa) support")
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (!_isInitialized.value || tts == null) {
            Log.w(TAG, "TextToSpeech is not ready yet")
            return
        }

        // Clean markdown and formatting symbols from text before reading out loud
        val cleanText = text
            .replace(Regex("[#*`_~>\\[\\]()]"), "")
            .replace(Regex("[\n\r]+"), " ")
            .trim()

        if (cleanText.isEmpty()) return

        stop()

        // Detect if text contains Persian / Arabic script range (\u0600-\u06FF)
        val hasPersianChars = cleanText.contains(Regex("[\u0600-\u06FF]"))
        if (hasPersianChars) {
            val persianLocale = Locale("fa", "IR")
            val res = tts?.setLanguage(persianLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("fa"))
            }
        } else {
            tts?.setLanguage(Locale.ENGLISH)
        }

        _currentlySpeakingText.value = text
        _isSpeaking.value = true

        val utteranceId = "utterance_${System.currentTimeMillis()}"
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (tts != null && tts!!.isSpeaking) {
            tts?.stop()
        }
        _isSpeaking.value = false
        _currentlySpeakingText.value = ""
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }
}
