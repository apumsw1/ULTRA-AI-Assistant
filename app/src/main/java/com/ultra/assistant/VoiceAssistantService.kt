
package com.ultra.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.ultra.assistant.data.LocalSettingsRepository
import java.net.URLEncoder
import java.util.Locale

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "com.ultra.assistant.START_VOICE"
        const val ACTION_STOP = "com.ultra.assistant.STOP_VOICE"

        @Volatile
        var isRunning = false
            private set
    }

    private enum class FollowUp { NONE, MUSIC_LANGUAGE }

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var speaking = false
    private var restarting = false
    private var destroyed = false
    private var followUp = FollowUp.NONE

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        isRunning = true
        createNotificationChannel()
        startForeground(31, createNotification())
        tts = TextToSpeech(this, this)
        setupRecognizer()
        startListening()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVoiceService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (!listening && !speaking && isRunning) {
                    startListening()
                }
            }
        }
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return

        ttsReady = true
        applyVoiceSettings()

        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    handler.post {
                        finishSpeaking()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    handler.post {
                        finishSpeaking()
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    handler.post {
                        finishSpeaking()
                    }
                }
            }
        )
    }

    private fun applyVoiceSettings() {
        val engine = tts ?: return
        engine.language = selectedLocale()

        val prefs = getSharedPreferences(
            "ultra_local_settings",
            MODE_PRIVATE
        )

        engine.setSpeechRate(
            prefs.getFloat("voice_speed", 0.96f)
                .coerceIn(0.75f, 1.25f)
        )

        engine.setPitch(
            prefs.getFloat("voice_pitch", 1.0f)
                .coerceIn(0.75f, 1.35f)
        )

        val voiceName = prefs.getString("voice_name", null)
        if (!voiceName.isNullOrBlank()) {
            engine.voices
                ?.firstOrNull { it.name == voiceName }
                ?.let { engine.voice = it }
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        runCatching {
            recognizer?.destroy()
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    if (!isRunning) return
                    listening = true
                    sendVoiceState("LISTENING")
                }

                override fun onBeginningOfSpeech() {
                    if (!isRunning) return
                    listening = true
                    sendVoiceState("LISTENING")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    if (isRunning) sendVoiceLevel(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (!isRunning) return
                    listening = false
                    sendVoiceState("PROCESSING")
                }

                override fun onError(error: Int) {
                    if (!isRunning) return
                    listening = false
                    sendVoiceState("IDLE")

                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stopVoiceService()
                    } else {
                        restartListening(
                            if (error == SpeechRecognizer.ERROR_CLIENT) 1200L
                            else 700L
                        )
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!isRunning) return
                    listening = false

                    val spoken = results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (spoken.isBlank()) {
                        sendVoiceState("IDLE")
                        restartListening(450L)
                    } else {
                        sendRecognizedText(spoken)
                        processCommand(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isRunning) return

                    val partial = partialResults
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (partial.isNotBlank()) {
                        sendPartialText(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
        )
    }

    private fun startListening() {
        if (!isRunning || destroyed || speaking || listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        if (recognizer == null) setupRecognizer()

        val language = selectedLanguageTag()

        val speechIntent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1200L
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                900L
            )
        }

        try {
            recognizer?.startListening(speechIntent)
            listening = true
            sendVoiceState("LISTENING")
        } catch (_: Exception) {
            listening = false
            restartListening(1200L)
        }
    }

    private fun processCommand(rawText: String) {
        val command = rawText.trim()
        if (command.isBlank()) {
            restartListening(400L)
            return
        }

        if (followUp == FollowUp.MUSIC_LANGUAGE) {
            followUp = FollowUp.NONE

            val language = when {
                contains(command, "বাংলা", "bangla", "bengali") -> "Bengali"
                contains(command, "হিন্দি", "hindi", "हिंदी", "हिन्दी") -> "Hindi"
                contains(command, "ইংলিশ", "ইংরেজি", "english") -> "English"
                else -> ""
            }

            if (language.isNotBlank()) {
                openMusicSearch("$language songs")
            } else {
                followUp = FollowUp.MUSIC_LANGUAGE
                speak(
                    "I didn't catch that. Would you like Bengali, Hindi, or English songs?"
                )
            }
            return
        }

        when {
            isWifiCommand(command) -> {
                openInternetSettings()
                speak("Opening internet settings. You can change Wi-Fi there.")
            }

            isBluetoothCommand(command) -> {
                openSettingsSafely(Settings.ACTION_BLUETOOTH_SETTINGS)
                speak("Opening Bluetooth settings.")
            }

            isGeneralSettingsCommand(command) -> {
                openSettingsSafely(Settings.ACTION_SETTINGS)
                speak("Opening settings.")
            }

            isExplicitSearch(command) -> {
                val query = removeSearchPrefix(command)
                if (query.isBlank()) {
                    speak("What would you like me to search for?")
                } else {
                    openExplicitWebSearch(query)
                }
            }

            isMusicRequest(command) -> {
                when {
                    contains(command, "হিন্দি", "hindi", "हिंदी") ->
                        openMusicSearch("Hindi songs")

                    contains(command, "বাংলা", "bangla", "bengali") ->
                        openMusicSearch("Bengali songs")

                    contains(command, "english", "ইংলিশ", "ইংরেজি") ->
                        openMusicSearch("English songs")

                    else -> {
                        followUp = FollowUp.MUSIC_LANGUAGE
                        speak(
                            "Of course. What kind of songs would you like: Bengali, Hindi, or English?"
                        )
                    }
                }
            }

            isAppLaunchRequest(command) -> {
                val query = cleanAppQuery(command)
                val app = findApplication(query)

                if (app != null) {
                    openApplication(app)
                } else {
                    speak(
                        "I can't find an installed app called $query. Did you mean a different app?"
                    )
                }
            }

            contains(command, "hello", "হ্যালো", "नमस्ते", "নমস্কার") -> {
                speak("Hello. I'm here. What would you like to do?")
            }

            contains(command, "what do you mean", "মানে কি", "তুমি কি বলতে চাচ্ছ") -> {
                speak(
                    "I mean I couldn't identify that clearly. Tell me what you want to do, and I'll try again."
                )
            }

            else -> {
                speak(
                    "I'm not sure I understood that. Could you say it another way?"
                )
            }
        }
    }

    private fun isWifiCommand(text: String): Boolean {
        val wifi = contains(
            text,
            "wifi", "wi-fi", "ওয়াইফাই", "ওয়াইফাই",
            "ওয়াই ফাই", "ওয়াই ফাই"
        )

        val action = contains(
            text,
            "বন্ধ", "off", "চালু", "অন", "on",
            "খোলো", "খুলে"
        )

        return wifi && action
    }

    private fun openInternetSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val panel = Intent(
                Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (panel.resolveActivity(packageManager) != null) {
                startActivity(panel)
                return
            }
        }

        openSettingsSafely(Settings.ACTION_WIFI_SETTINGS)
    }

    private fun isBluetoothCommand(text: String): Boolean =
        contains(text, "bluetooth", "ব্লুটুথ") &&
            contains(
                text,
                "settings", "সেটিং", "বন্ধ",
                "চালু", "off", "on", "খোলো"
            )

    private fun isGeneralSettingsCommand(text: String): Boolean =
        contains(
            text,
            "settings", "সেটিংস", "সেটিংসে", "सेटिंग", "सेटिंग्स"
        ) &&
            !contains(
                text,
                "wifi", "wi-fi", "ওয়াইফাই",
                "ওয়াইফাই", "bluetooth", "ব্লুটুথ"
            )

    private fun openSettingsSafely(action: String) {
        try {
            startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            speak("I couldn't open that settings page on this device.")
        }
    }

    private fun isExplicitSearch(text: String): Boolean =
        contains(
            text,
            "search for ", "search ", "google ",
            "সার্চ কর", "সার্চ ", "খুঁজে দেখ",
            "খোঁজ কর", "सर्च करो", "खोजो "
        )

    private fun removeSearchPrefix(text: String): String {
        val prefixes = listOf(
            "search for ", "search ", "google ",
            "সার্চ করে ", "সার্চ করো ", "সার্চ কর ",
            "সার্চ ", "খুঁজে দেখ ", "খোঁজ কর ",
            "सर्च करो ", "सर्च ", "खोजो "
        )

        val value = text.trim()
        val prefix = prefixes.firstOrNull {
            value.startsWith(it, ignoreCase = true)
        }

        return prefix?.let {
            value.drop(it.length).trim()
        } ?: value
    }

    private fun isMusicRequest(text: String): Boolean =
        contains(
            text,
            "গান", "music", "song", "songs",
            "গান শোনাও", "গান বাজাও"
        )

    private fun openMusicSearch(query: String) {
        try {
            val url = "https://music.youtube.com/search?q=" + encode(query)
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            speak("Okay. Here are the $query.")
        } catch (_: Exception) {
            speak("I couldn't open a music app on this phone.")
        }
    }

    private fun isAppLaunchRequest(text: String): Boolean =
        contains(
            text,
            "open ", "launch ", "start ",
            "খোলো", "খুলে দাও", "চালু কর",
            "অ্যাপ খোলো", "खोलो", "ऐप खोलो", "चालू करो"
        )

    private fun cleanAppQuery(command: String): String {
        var value = command.trim()

        val prefixes = listOf(
            "open the app ", "launch the app ", "start the app ",
            "open ", "launch ", "start ", "run ",
            "অ্যাপ খোলো ", "খুলে দাও ", "খোলো ",
            "চালু কর ", "ऐप खोलो ", "खोलो ", "चालू करो "
        )

        var changed = true
        while (changed) {
            changed = false
            val prefix = prefixes.firstOrNull {
                value.startsWith(it, ignoreCase = true)
            }

            if (prefix != null) {
                value = value.drop(prefix.length).trim()
                changed = true
            }
        }

        val suffixes = listOf(
            " অ্যাপ খোলো", " অ্যাপ চালু করো",
            " অ্যাপ", " app", " খুলে দাও",
            " খোলো", " চালু করো", " চালাও"
        )

        val suffix = suffixes.firstOrNull {
            value.endsWith(it, ignoreCase = true)
        }

        if (suffix != null) {
            value = value.dropLast(suffix.length).trim()
        }

        return value
    }

    private fun findApplication(queryText: String): ResolveInfo? {
        val query = normalize(queryText)
        if (query.length < 2) return null

        val launcher = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = packageManager.queryIntentActivities(launcher, 0)

        var best: ResolveInfo? = null
        var bestScore = 0

        for (app in apps) {
            val label = app.loadLabel(packageManager)?.toString().orEmpty()
            val normalized = normalize(label)

            val score = when {
                normalized == query -> 1000
                normalized.startsWith(query) -> 850
                normalized.contains(query) -> 750
                query.contains(normalized) && normalized.length >= 4 -> 650
                else -> 0
            }

            if (score > bestScore) {
                bestScore = score
                best = app
            }
        }

        return if (bestScore >= 650) best else null
    }

    private fun openApplication(info: ResolveInfo) {
        val activity = info.activityInfo ?: run {
            speak("I couldn't open that app.")
            return
        }

        val label = info.loadLabel(packageManager)?.toString().orEmpty()

        try {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(activity.packageName, activity.name)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )

            startActivity(intent)
            speak("Opening $label. What would you like to do?")
        } catch (_: Exception) {
            speak("I couldn't open $label.")
        }
    }

    private fun openExplicitWebSearch(query: String) {
        try {
            val url = "https://www.google.com/search?q=" + encode(query)
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            speak("Searching for $query.")
        } catch (_: Exception) {
            speak("I couldn't open a browser to search for that.")
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun selectedLanguageTag(): String {
        val value = LocalSettingsRepository(this)
            .language()
            .lowercase(Locale.ROOT)
            .trim()

        return when (value) {
            "hindi", "hi", "hi-in", "हिन्दी", "हिंदी" -> "hi-IN"
            "bengali", "bangla", "bn", "bn-in", "বাংলা",
            "indian bengali" -> "bn-IN"
            else -> "en-IN"
        }
    }

    private fun selectedLocale(): Locale =
        when (selectedLanguageTag()) {
            "hi-IN" -> Locale("hi", "IN")
            "bn-IN" -> Locale("bn", "IN")
            else -> Locale("en", "IN")
        }

    private fun speak(message: String) {
        if (!isRunning) return

        if (!ttsReady || tts == null) {
            sendVoiceState("IDLE")
            restartListening(700L)
            return
        }

        applyVoiceSettings()
        speaking = true
        sendVoiceState("SPEAKING")

        val id = "ultra_" + System.currentTimeMillis()
        tts?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            id
        )
    }

    private fun finishSpeaking() {
        if (!isRunning || destroyed) return
        speaking = false
        sendVoiceState("IDLE")
        restartListening(350L)
    }

    private fun contains(text: String, vararg words: String): Boolean =
        words.any { text.contains(it, ignoreCase = true) }

    private fun encode(text: String): String =
        URLEncoder.encode(text, "UTF-8")

    private fun restartListening(delay: Long) {
        if (!isRunning || destroyed || restarting) return

        restarting = true

        handler.postDelayed({
            restarting = false
            if (isRunning && !destroyed && !speaking && !listening) {
                startListening()
            }
        }, delay)
    }

    private fun sendVoiceState(state: String) {
        if (!isRunning) return
        sendBroadcast(
            Intent("com.ultra.assistant.VOICE_STATE")
                .putExtra("state", state)
        )
    }

    private fun sendRecognizedText(text: String) {
        if (!isRunning) return
        sendBroadcast(
            Intent("com.ultra.assistant.VOICE_TEXT")
                .putExtra("text", text)
        )
    }

    private fun sendPartialText(text: String) {
        if (!isRunning) return
        sendBroadcast(
            Intent("com.ultra.assistant.VOICE_PARTIAL")
                .putExtra("text", text)
        )
    }

    private fun sendVoiceLevel(level: Float) {
        if (!isRunning) return
        sendBroadcast(
            Intent("com.ultra.assistant.VOICE_LEVEL")
                .putExtra("level", level)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ultra_assistant",
                "ULTRA AI Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                vibrationPattern = longArrayOf(0L)
                setShowBadge(false)
            }

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "ultra_assistant")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ULTRA AI Assistant")
            .setContentText("Voice assistant is active")
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun stopVoiceService() {
        isRunning = false
        destroyed = true
        handler.removeCallbacksAndMessages(null)

        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }

        recognizer = null

        runCatching {
            tts?.stop()
            tts?.shutdown()
        }

        tts = null
        ttsReady = false
        listening = false
        speaking = false
        restarting = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        destroyed = true
        handler.removeCallbacksAndMessages(null)

        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
        }

        recognizer = null

        runCatching {
            tts?.stop()
            tts?.shutdown()
        }

        tts = null
        ttsReady = false
        listening = false
        speaking = false
        restarting = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
