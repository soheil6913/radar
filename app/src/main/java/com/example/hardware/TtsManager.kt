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
            setupPersianLocale()
            tts?.setSpeechRate(0.92f)

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

    private fun setupPersianLocale(): Boolean {
        val faIr = Locale("fa", "IR")
        var res = tts?.setLanguage(faIr)
        if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.setPitch(1.0f)
            return true
        }

        val fa = Locale("fa")
        res = tts?.setLanguage(fa)
        if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.setPitch(1.0f)
            return true
        }

        val fas = Locale("fas")
        res = tts?.setLanguage(fas)
        if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.setPitch(1.0f)
            return true
        }

        val per = Locale("per")
        res = tts?.setLanguage(per)
        if (res == TextToSpeech.LANG_AVAILABLE || res == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.setPitch(1.0f)
            return true
        }

        // CRITICAL: NEVER fallback to Arabic (ar). If Persian is unavailable on system TTS,
        // use default locale without switching to Arabic accent.
        Log.w(TAG, "Persian TTS language pack not installed on Android System. Playing with standard voice.")
        return false
    }

    fun speak(text: String) {
        if (!_isInitialized.value || tts == null) {
            Log.w(TAG, "TextToSpeech is not ready yet")
            return
        }

        // Clean emojis, markdown symbols, and formatting before speech synthesis
        val cleanText = text
            .replace(Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F700}-\\x{1F77F}\\x{1F780}-\\x{1F7FF}\\x{1F800}-\\x{1F8FF}\\x{1F900}-\\x{1F9FF}\\x{1FA00}-\\x{1FA6F}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]"), "")
            .replace(Regex("[#*`_~>\\[\\]()|\\\\:-]"), " ")
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanText.isEmpty()) return

        stop()

        val hasPersianChars = cleanText.contains(Regex("[\\u0600-\\u06FF]"))
        if (hasPersianChars) {
            setupPersianLocale()
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
