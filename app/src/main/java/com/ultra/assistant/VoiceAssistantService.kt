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
import com.ultra.assistant.data.LocalSettingsRepository
import com.ultra.assistant.voice.LanguageManager
import java.net.URLEncoder
import java.util.Locale

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private var listening = false
    private var commandMode = false
    private var restarting = false
    private var processing = false

    private lateinit var floatingDot: FloatingDot

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        isRunning = true

        createNotificationChannel()

        startForeground(
            31,
            createNotification()
        )

        textToSpeech = TextToSpeech(
            this,
            this
        )

        floatingDot = FloatingDot(this)

        if (android.provider.Settings.canDrawOverlays(this)) {
            floatingDot.show()
        }

        createSpeechRecognizer()
        startListening()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action == "SHOW_DOT" &&
            ::floatingDot.isInitialized
        ) {
            floatingDot.show()
        }

        if (!listening) {
            startListening()
        }

        return START_STICKY
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            ttsReady = true

            val language =
                LocalSettingsRepository(this)
                    .language()

            textToSpeech?.language =
                LanguageManager.locale(language)

            textToSpeech?.setSpeechRate(0.96f)
            textToSpeech?.setPitch(1.0f)
        }
    }

    private fun createSpeechRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak(
                "Speech recognition is not available on this device."
            )
            return
        }

        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    listening = true
                    notifyVoiceState("LISTENING")
                }

                override fun onBeginningOfSpeech() {
                    listening = true
                    notifyVoiceState("LISTENING")
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                    listening = false
                    notifyVoiceState("PROCESSING")
                }

                override fun onError(
                    error: Int
                ) {
                    listening = false
                    notifyVoiceState("IDLE")

                    restartListening(
                        if (
                            error ==
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ) {
                            1500
                        } else {
                            700
                        }
                    )
                }

                override fun onResults(
                    results: Bundle?
                ) {
                    listening = false

                    val resultsList =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    val phrase =
                        resultsList
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (phrase.isNotBlank()) {
                        processPhrase(phrase)
                    } else {
                        notifyVoiceState("IDLE")
                        restartListening(500)
                    }
                }

                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                    val phrase =
                        partialResults
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                    if (phrase.isBlank()) {
                        return
                    }

                    notifyPartialText(phrase)

                    if (
                        !commandMode &&
                        hasWakeWord(phrase)
                    ) {
                        commandMode = true

                        stopCurrentRecognition()

                        speak(
                            wakeResponse()
                        )

                        restartListening(650)
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

        if (processing || listening) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak(
                "Speech recognition is unavailable."
            )
            return
        }

        if (speechRecognizer == null) {
            createSpeechRecognizer()
        }

        val selectedLanguage =
            LocalSettingsRepository(this)
                .language()

        val locale =
            when (
                selectedLanguage
                    .lowercase(Locale.US)
                    .trim()
            ) {

                "english",
                "en",
                "en-in",
                "english india" ->
                    "en-IN"

                "हिन्दी",
                "hindi",
                "hi",
                "hi-in",
                "hindi india" ->
                    "hi-IN"

                "বাংলা",
                "bangla",
                "bengali",
                "bn",
                "bn-in",
                "bengali india",
                "indian bengali" ->
                    "bn-IN"

                else ->
                    "en-IN"
            }

        val recognizerIntent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    locale
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    locale
                )

                putExtra(
                    RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                    false
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
                    1000L
                )
            }

        try {

            speechRecognizer?.startListening(
                recognizerIntent
            )

            listening = true
            notifyVoiceState("LISTENING")

        } catch (_: Exception) {

            listening = false
            restartListening(1200)
        }
    }

    private fun processPhrase(
        phrase: String
    ) {

        val clean =
            phrase.trim()

        if (clean.isBlank()) {
            restartListening(500)
            return
        }

        if (!commandMode) {

            if (hasWakeWord(clean)) {

                val command =
                    removeWakeWord(clean)

                if (command.isBlank()) {

                    commandMode = true

                    speak(
                        wakeResponse()
                    )

                } else {

                    commandMode = false

                    executeCommand(command)
                }

                restartListening(700)

                return
            }

            restartListening(400)

            return
        }

        commandMode = false

        val command =
            removeWakeWord(clean)
                .trim()

        if (command.isNotBlank()) {

            executeCommand(command)

        } else {

            speak(
                emptyCommandResponse()
            )

            restartListening(700)
        }
    }

    private fun executeCommand(
        command: String
    ) {

        if (processing) {
            return
        }

        processing = true
        notifyVoiceState("PROCESSING")

        val q =
            command.trim().ifBlank {
                "help"
            }

        when {

            containsAny(
                q,
                "lock phone",
                "lock my phone",
                "ফোন লক",
                "ফোনটা লক",
                "फोन लॉक"
            ) -> {

                lockPhone()
            }

            containsAny(
                q,
                "next",
                "next song",
                "next track",
                "পরের গান",
                "পরেরটা",
                "अगला"
            ) -> {

                sendBroadcast(
                    Intent(
                        "com.ultra.assistant.NEXT_TRACK"
                    )
                )

                finishCommand(
                    response(
                        "I will skip to the next track.",
                        "পরের ট্র্যাকে যাচ্ছি।",
                        "अगले ट्रैक पर जा रहा हूँ।"
                    )
                )
            }

            containsAny(
                q,
                "open chrome",
                "chrome open",
                "ক্রোম খোলো",
                "ক্রোম ওপেন",
                "क्रोम खोलो"
            ) -> {

                launchPackage(
                    "com.android.chrome",
                    "Chrome"
                )
            }

            containsAny(
                q,
                "open settings",
                "settings open",
                "সেটিংস খোলো",
                "সেটিংস ওপেন",
                "सेटिंग खोलो"
            ) -> {

                launchPackage(
                    "com.android.settings",
                    "Settings"
                )
            }

            containsAny(
                q,
                "youtube",
                "ইউটিউব",
                "यूट्यूब",
                "song",
                "music",
                "গান",
                "মিউজিক",
                "गाना",
                "म्यूजिक"
            ) -> {

                val searchUrl =
                    "https://www.youtube.com/results?search_query=" +
                        urlEncode(q)

                openUrl(
                    searchUrl,
                    response(
                        "Searching YouTube.",
                        "YouTube-এ খুঁজছি।",
                        "YouTube पर खोज रहा हूँ।"
                    )
                )
            }

            containsAny(
                q,
                "search",
                "search for",
                "সার্চ",
                "খুঁজে",
                "খোঁজ",
                "সার্চ কর",
                "सर्च",
                "खोजो"
            ) -> {

                val searchUrl =
                    "https://www.google.com/search?q=" +
                        urlEncode(q)

                openUrl(
                    searchUrl,
                    response(
                        "Searching Google.",
                        "Google-এ খুঁজছি।",
                        "Google पर खोज रहा हूँ।"
                    )
                )
            }

            else -> {

                val searchUrl =
                    "https://www.google.com/search?q=" +
                        urlEncode(q)

                openUrl(
                    searchUrl,
                    response(
                        "I am searching for that.",
                        "আমি এটা খুঁজে দেখছি।",
                        "मैं इसे खोज रहा हूँ।"
                    )
                )
            }
        }
    }

    private fun lockPhone() {

        val admin =
            android.content.ComponentName(
                this,
                com.ultra.assistant.admin.UltraDeviceAdminReceiver::class.java
            )

        val policy =
            getSystemService(
                DEVICE_POLICY_SERVICE
            ) as android.app.admin.DevicePolicyManager

        if (policy.isAdminActive(admin)) {

            runCatching {

                policy.lockNow()

                finishCommand(
                    response(
                        "Locking the phone now.",
                        "ফোন এখন লক করছি।",
                        "फोन अभी लॉक कर रहा हूँ।"
                    )
                )

            }.onFailure {

                finishCommand(
                    response(
                        "I could not lock the phone.",
                        "ফোন লক করা যায়নি।",
                        "फोन लॉक नहीं हो सका।"
                    )
                )
            }

        } else {

            finishCommand(
                response(
                    "Device Administrator permission is not active.",
                    "Device Administrator permission is not active.",
                    "Device Administrator permission is not active."
                )
            )
        }
    }

    private fun launchPackage(
        packageName: String,
        appName: String
    ) {

        val launchIntent =
            packageManager.getLaunchIntentForPackage(
                packageName
            )

        if (launchIntent == null) {

            finishCommand(
                response(
                    "$appName is not installed.",
                    "$appName ইনস্টল করা নেই।",
                    "$appName इंस्टॉल नहीं है।"
                )
            )

            return
        }

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        runCatching {

            startActivity(launchIntent)

            finishCommand(
                response(
                    "Opening $appName.",
                    "$appName খুলছি।",
                    "$appName खोल रहा हूँ।"
                )
            )

        }.onFailure {

            finishCommand(
                response(
                    "I could not open $appName.",
                    "$appName খোলা যায়নি।",
                    "$appName नहीं खुल सका।"
                )
            )
        }
    }

    private fun openUrl(
        url: String,
        message: String
    ) {

        speak(message)

        runCatching {

            val intent =
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            startActivity(intent)

            processing = false
            notifyVoiceState("SPEAKING")

            restartListening(1800)

        }.onFailure {

            finishCommand(
                response(
                    "I could not open the browser.",
                    "ব্রাউজার খোলা যায়নি।",
                    "ब्राउज़र नहीं खुल सका।"
                )
            )
        }
    }

    private fun finishCommand(
        message: String
    ) {

        speak(message)

        processing = false
        notifyVoiceState("SPEAKING")

        restartListening(1600)
    }

    private fun speak(
        message: String
    ) {

        if (!ttsReady) {
            return
        }

        val language =
            LocalSettingsRepository(this)
                .language()

        textToSpeech?.language =
            LanguageManager.locale(language)

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "ultra-${System.currentTimeMillis()}"
        )
    }

    private fun response(
        english: String,
        bengali: String,
        hindi: String
    ): String {

        return when (
            LocalSettingsRepository(this)
                .language()
                .lowercase(Locale.US)
                .trim()
        ) {

            "বাংলা",
            "bangla",
            "bengali",
            "bn",
            "bn-in",
            "bengali india",
            "indian bengali" ->
                bengali

            "हिन्दी",
            "hindi",
            "hi",
            "hi-in",
            "hindi india" ->
                hindi

            else ->
                english
        }
    }

    private fun wakeResponse(): String {

        return response(
            "Yes, I am listening.",
            "জি, আমি শুনছি।",
            "जी, मैं सुन रहा हूँ।"
        )
    }

    private fun emptyCommandResponse(): String {

        return response(
            "I did not hear a command.",
            "আমি কোনো কমান্ড শুনতে পাইনি।",
            "मैंने कोई कमांड नहीं सुनी।"
        )
    }

    private fun hasWakeWord(
        text: String
    ): Boolean {

        return com.ultra.assistant.voice
            .WakeWordEngine
            .detected(text)
    }

    private fun removeWakeWord(
        text: String
    ): String {

        return com.ultra.assistant.voice
            .WakeWordEngine
            .strip(text)
    }

    private fun containsAny(
        text: String,
        vararg values: String
    ): Boolean {

        return values.any {
            text.contains(
                it,
                ignoreCase = true
            )
        }
    }

    private fun urlEncode(
        text: String
    ): String {

        return URLEncoder.encode(
            text,
            "UTF-8"
        )
    }

    private fun stopCurrentRecognition() {

        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }

        listening = false
    }

    private fun restartListening(
        delay: Long
    ) {

        if (restarting) {
            return
        }

        restarting = true

        handler.postDelayed({

            restarting = false

            if (!isDestroyed) {

                if (speechRecognizer == null) {
                    createSpeechRecognizer()
                }

                if (!listening && !processing) {
                    startListening()
                }
            }

        }, delay)
    }

    private fun notifyVoiceState(
        state: String
    ) {
    }

    private fun notifyPartialText(
        text: String
    ) {
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    "ultra_assistant",
                    "ULTRA AI Assistant",
                    NotificationManager.IMPORTANCE_LOW
                )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(
                    this,
                    MainActivity::class.java
                ),
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

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }

        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        if (::floatingDot.isInitialized) {
            floatingDot.hide()
        }

        listening = false
        processing = false
        restarting = false
        isRunning = false

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}

private fun String.hasWakeWord(): Boolean {
    return com.ultra.assistant.voice
        .WakeWordEngine
        .detected(this)
}

private fun String.removeWake(): String {
    return com.ultra.assistant.voice
        .WakeWordEngine
        .strip(this)
}

private fun String.urlPart(): String {
    return URLEncoder.encode(
        this,
        "UTF-8"
    )
}