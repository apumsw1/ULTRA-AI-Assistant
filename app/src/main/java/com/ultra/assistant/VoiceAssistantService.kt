package com.ultra.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
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

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private var ttsReady = false
    private var listening = false
    private var restarting = false
    private var speaking = false

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        isRunning = true

        createNotificationChannel()

        startForeground(
            31,
            createNotification()
        )

        tts = TextToSpeech(
            this,
            this
        )

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

            ACTION_START,
            null -> {
                if (!listening && !speaking) {
                    startListening()
                }
            }
        }

        return START_STICKY
    }

    override fun onInit(status: Int) {

        if (status != TextToSpeech.SUCCESS) {
            ttsReady = false
            return
        }

        ttsReady = true

        val locale =
            selectedLocale()

        tts?.language = locale
        tts?.setSpeechRate(0.96f)
        tts?.setPitch(1.0f)
    }

    private fun setupRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(
                this
            )

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    listening = true
                    sendVoiceState("LISTENING")
                }

                override fun onBeginningOfSpeech() {
                    listening = true
                    sendVoiceState("LISTENING")
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                    sendVoiceLevel(rmsdB)
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                    listening = false
                    sendVoiceState("PROCESSING")
                }

                override fun onError(
                    error: Int
                ) {

                    listening = false

                    sendVoiceState("IDLE")

                    when (error) {

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            stopVoiceService()
                        }

                        SpeechRecognizer.ERROR_CLIENT -> {
                            restartListening(1200)
                        }

                        else -> {
                            restartListening(600)
                        }
                    }
                }

                override fun onResults(
                    results: Bundle?
                ) {

                    listening = false

                    val list =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val text =
                        list
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (text.isEmpty()) {
                        sendVoiceState("IDLE")
                        restartListening(400)
                        return
                    }

                    sendRecognizedText(text)

                    processCommand(text)
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {

                    val list =
                        partialResults?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val text =
                        list
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (text.isNotEmpty()) {
                        sendPartialText(text)
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }

    private fun startListening() {

        if (speaking || listening) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        if (recognizer == null) {
            setupRecognizer()
        }

        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    selectedLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    selectedLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    1200L
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    900L
                )

                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    700L
                )
            }

        try {

            recognizer?.startListening(intent)

            listening = true

            sendVoiceState("LISTENING")

        } catch (_: Exception) {

            listening = false

            restartListening(1200)
        }
    }

    private fun processCommand(
        text: String
    ) {

        val command =
            text.trim()

        if (command.isEmpty()) {
            restartListening(500)
            return
        }

        when {

            contains(
                command,
                "hello",
                "হ্যালো",
                "नमस्ते"
            ) -> {

                speak(
                    english = "Hello. I am listening.",
                    bengali = "হ্যালো। আমি শুনছি।",
                    hindi = "नमस्ते। मैं सुन रहा हूँ।"
                )
            }

            contains(
                command,
                "open settings",
                "settings",
                "সেটিংস খোলো",
                "সেটিংস",
                "सेटिंग खोलो",
                "सेटिंग"
            ) -> {

                openApplication(
                    "com.android.settings",
                    "Settings"
                )
            }

            contains(
                command,
                "open chrome",
                "chrome",
                "ক্রোম খোলো",
                "ক্রোম",
                "क्रोम खोलो",
                "क्रोम"
            ) -> {

                openApplication(
                    "com.android.chrome",
                    "Chrome"
                )
            }

            contains(
                command,
                "open youtube",
                "youtube",
                "ইউটিউব খোলো",
                "ইউটিউব",
                "यूट्यूब खोलो",
                "यूट्यूब"
            ) -> {

                openUrl(
                    "https://www.youtube.com/",
                    english = "Opening YouTube.",
                    bengali = "YouTube খুলছি।",
                    hindi = "YouTube खोल रहा हूँ।"
                )
            }

            contains(
                command,
                "search",
                "সার্চ",
                "খুঁজে দেখ",
                "सर्च",
                "खोजो"
            ) -> {

                val url =
                    "https://www.google.com/search?q=" +
                        encode(command)

                openUrl(
                    url,
                    english = "Searching.",
                    bengali = "সার্চ করছি।",
                    hindi = "सर्च कर रहा हूँ।"
                )
            }

            else -> {

                val url =
                    "https://www.google.com/search?q=" +
                        encode(command)

                openUrl(
                    url,
                    english = "I am searching for that.",
                    bengali = "আমি এটা খুঁজে দেখছি।",
                    hindi = "मैं इसे खोज रहा हूँ।"
                )
            }
        }
    }

    private fun openApplication(
        packageName: String,
        name: String
    ) {

        val intent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        if (intent == null) {

            speak(
                english = "$name is not available.",
                bengali = "$name পাওয়া যাচ্ছে না।",
                hindi = "$name उपलब्ध नहीं है।"
            )

            restartListening(1400)

            return
        }

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        try {

            startActivity(intent)

            speak(
                english = "Opening $name.",
                bengali = "$name খুলছি।",
                hindi = "$name खोल रहा हूँ।"
            )

        } catch (_: Exception) {

            speak(
                english = "I could not open $name.",
                bengali = "$name খোলা যায়নি।",
                hindi = "$name नहीं खुल सका।"
            )
        }
    }

    private fun openUrl(
        url: String,
        english: String,
        bengali: String,
        hindi: String
    ) {

        try {

            val intent =
                Intent(
                    Intent.ACTION_VIEW
                ).apply {

                    data =
                        android.net.Uri.parse(url)

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            startActivity(intent)

            speak(
                english = english,
                bengali = bengali,
                hindi = hindi
            )

        } catch (_: Exception) {

            speak(
                english = "I could not open it.",
                bengali = "এটি খোলা যায়নি।",
                hindi = "इसे खोला नहीं जा सका।"
            )
        }
    }

    private fun speak(
        english: String,
        bengali: String,
        hindi: String
    ) {

        if (!ttsReady) {
            return
        }

        val language =
            selectedLanguage()

        tts?.language = language

        val text =
            when (
                selectedLanguageTag()
            ) {

                "bn-IN" ->
                    bengali

                "hi-IN" ->
                    hindi

                else ->
                    english
            }

        speaking = true

        sendVoiceState("SPEAKING")

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "ultra_voice_${System.currentTimeMillis()}"
        )

        handler.postDelayed(
            {
                speaking = false
                sendVoiceState("IDLE")
                restartListening(350)
            },
            1400L
        )
    }

    private fun selectedLanguageTag(): String {

        val value =
            readLanguageSetting()

        return when (
            value
                .lowercase(Locale.US)
                .trim()
        ) {

            "hindi",
            "hi",
            "hi-in",
            "हिन्दी",
            "हिंदी" ->
                "hi-IN"

            "bengali",
            "bangla",
            "bn",
            "bn-in",
            "বাংলা",
            "indian bengali" ->
                "bn-IN"

            else ->
                "en-IN"
        }
    }

    private fun selectedLocale(): Locale {

        return when (
            selectedLanguageTag()
        ) {

            "hi-IN" ->
                Locale("hi", "IN")

            "bn-IN" ->
                Locale("bn", "IN")

            else ->
                Locale("en", "IN")
        }
    }

    private fun readLanguageSetting(): String {

        return try {

            val clazz =
                Class.forName(
                    "com.ultra.assistant.data.LocalSettingsRepository"
                )

            val constructor =
                clazz.getConstructor(
                    android.content.Context::class.java
                )

            val repository =
                constructor.newInstance(this)

            val method =
                clazz.getMethod("language")

            method.invoke(repository) as? String
                ?: "english"

        } catch (_: Exception) {

            "english"
        }
    }

    private fun contains(
        text: String,
        vararg words: String
    ): Boolean {

        return words.any {
            text.contains(
                it,
                ignoreCase = true
            )
        }
    }

    private fun encode(
        text: String
    ): String {

        return URLEncoder.encode(
            text,
            "UTF-8"
        )
    }

    private fun restartListening(
        delay: Long
    ) {

        if (restarting || isDestroyed) {
            return
        }

        restarting = true

        handler.postDelayed(
            {

                restarting = false

                if (!isDestroyed &&
                    !speaking &&
                    !listening
                ) {
                    startListening()
                }

            },
            delay
        )
    }

    private fun sendVoiceState(
        state: String
    ) {

        sendBroadcast(
            Intent(
                "com.ultra.assistant.VOICE_STATE"
            ).apply {
                putExtra(
                    "state",
                    state
                )
            }
        )
    }

    private fun sendRecognizedText(
        text: String
    ) {

        sendBroadcast(
            Intent(
                "com.ultra.assistant.VOICE_TEXT"
            ).apply {
                putExtra(
                    "text",
                    text
                )
            }
        )
    }

    private fun sendPartialText(
        text: String
    ) {

        sendBroadcast(
            Intent(
                "com.ultra.assistant.VOICE_PARTIAL"
            ).apply {
                putExtra(
                    "text",
                    text
                )
            }
        )
    }

    private fun sendVoiceLevel(
        level: Float
    ) {

        sendBroadcast(
            Intent(
                "com.ultra.assistant.VOICE_LEVEL"
            ).apply {
                putExtra(
                    "level",
                    level
                )
            }
        )
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    "ultra_assistant",
                    "ULTRA AI Assistant",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(): Notification {

        val intent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(
            this,
            "ultra_assistant"
        )
            .setSmallIcon(
                android.R.drawable.ic_btn_speak_now
            )
            .setContentTitle(
                "ULTRA AI Assistant"
            )
            .setContentText(
                "Voice assistant is active"
            )
            .setContentIntent(
                pendingIntent
            )
            .setOngoing(true)
            .build()
    }

    private fun stopVoiceService() {

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null

        listening = false
        speaking = false
        isRunning = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null

        listening = false
        speaking = false
        isRunning = false

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}