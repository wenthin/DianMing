package com.dianming.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** 语音朗读状态 */
enum class TtsStatus {
    /** 正在连接系统语音引擎 */
    INITIALIZING,

    /** 引擎已连接，可以朗读 */
    READY,

    /** 不可用：设备没有语音引擎，或缺少中文语音数据 */
    UNAVAILABLE
}

/**
 * 文字转语音：用于朗读学生姓名。
 *
 * 注意：Android 11 起系统默认开启包可见性限制，应用若不在 Manifest 中声明
 * `<queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>`
 * 就看不到系统里的语音引擎，[TextToSpeech] 会绑定失败并静默失效。
 */
class TtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var pending: String? = null

    private val _status = MutableStateFlow(TtsStatus.INITIALIZING)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { result ->
            if (result != TextToSpeech.SUCCESS || !prepareLanguage()) {
                _status.value = TtsStatus.UNAVAILABLE
                return@TextToSpeech
            }
            _status.value = TtsStatus.READY
            pending?.let {
                pending = null
                speak(it)
            }
        }
    }

    /** 挑选一个可用的语言（优先中文），返回是否可用 */
    private fun prepareLanguage(): Boolean {
        val engine = tts ?: return false
        val chinese = listOf(Locale.CHINA, Locale.SIMPLIFIED_CHINESE, Locale.CHINESE)
            .firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
        val locale = chinese ?: Locale.getDefault().takeIf {
            it.language == "zh" && engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
        }
        if (locale == null) return false
        engine.language = locale
        engine.setSpeechRate(1.0f)
        return true
    }

    /**
     * 朗读文本。
     * @return 朗读后的状态：[TtsStatus.UNAVAILABLE] 表示语音不可用，界面可据此提示用户。
     */
    fun speak(text: String): TtsStatus {
        if (text.isBlank()) return _status.value
        if (_status.value != TtsStatus.READY) {
            // 引擎还没准备好，先记下来，等就绪后自动补读
            if (_status.value == TtsStatus.INITIALIZING) pending = text
            return _status.value
        }
        return try {
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "student_name")
            if (result == TextToSpeech.ERROR) {
                _status.value = TtsStatus.UNAVAILABLE
            }
            _status.value
        } catch (_: Exception) {
            _status.value = TtsStatus.UNAVAILABLE
            _status.value
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        pending = null
        _status.value = TtsStatus.UNAVAILABLE
    }
}
