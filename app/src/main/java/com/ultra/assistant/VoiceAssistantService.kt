package com.ultra.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ResolveInfo
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
                if (!listening && !speaking && isRunning) {
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

        tts?.language = selectedLocale()
        tts?.setSpeechRate(0.96f)
        tts?.setPitch(1.0f)
    }

    private fun setupRecognizer() {

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        recognizer?.destroy()

        recognizer =
            SpeechRecognizer.createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: Bundle?
                ) {
                    if (!isRunning) return

                    listening = true
                    sendVoiceState("LISTENING")
                }

                override fun onBeginningOfSpeech() {
                    if (!isRunning) return

                    listening = true
                    sendVoiceState("LISTENING")
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                    if (!isRunning) return

                    sendVoiceLevel(rmsdB)
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                }

                override fun onEndOfSpeech() {
                    if (!isRunning) return

                    listening = false
                    sendVoiceState("PROCESSING")
                }

                override fun onError(
                    error: Int
                ) {

                    if (!isRunning) return

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

                    if (!isRunning) return

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

                    if (!isRunning) return

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

        if (!isRunning) {
            return
        }

        if (speaking || listening) {
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }

        if (recognizer == null) {
            setupRecognizer()
        }

        val language =
            selectedLanguageTag()

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
                    language
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    language
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

        val app =
            findApplication(command)

        if (app != null) {
            openApplication(app)
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
                    "Hello. I am listening.",
                    "হ্যালো। আমি শুনছি।",
                    "नमस्ते। मैं सुन रहा हूँ।"
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

                val query =
                    removeSearchPrefix(command)

                searchWeb(query)
            }

            else -> {

                speak(
                    "I could not find an app with that name. I will search for it.",
                    "এই নামে কোনো অ্যাপ খুঁজে পেলাম না। আমি এটি সার্চ করছি।",
                    "इस नाम का कोई ऐप नहीं मिला। मैं इसे खोज रहा हूँ।"
                )

                val url =
                    "https://www.google.com/search?q=" +
                        encode(command)

                openBrowserUrl(url)
            }
        }
    }

    private fun findApplication(
        command: String
    ): ResolveInfo? {

        val query =
            cleanAppQuery(command)

        if (query.isEmpty()) {
            return null
        }

        val launcherIntent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {
                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )
            }

        val apps =
            packageManager.queryIntentActivities(
                launcherIntent,
                0
            )

        if (apps.isEmpty()) {
            return null
        }

        val normalizedQuery =
            normalize(query)

        var best: ResolveInfo? = null
        var bestScore = 0

        for (app in apps) {

            val label =
                app.loadLabel(
                    packageManager
                )
                    ?.toString()
                    .orEmpty()

            val packageName =
                app.activityInfo?.packageName
                    .orEmpty()

            val activityName =
                app.activityInfo?.name
                    .orEmpty()

            val normalizedLabel =
                normalize(label)

            val normalizedPackage =
                normalize(packageName)

            val normalizedActivity =
                normalize(activityName)

            var score = 0

            if (
                normalizedLabel ==
                normalizedQuery
            ) {
                score = 1000
            } else if (
                normalizedLabel.startsWith(
                    normalizedQuery
                )
            ) {
                score = 850
            } else if (
                normalizedLabel.contains(
                    normalizedQuery
                )
            ) {
                score = 750
            } else if (
                normalizedQuery.contains(
                    normalizedLabel
                ) &&
                normalizedLabel.length >= 3
            ) {
                score = 700
            } else if (
                normalizedPackage.contains(
                    normalizedQuery
                )
            ) {
                score = 500
            } else if (
                normalizedActivity.contains(
                    normalizedQuery
                )
            ) {
                score = 400
            } else {

                val queryWords =
                    normalizedQuery
                        .split(" ")
                        .filter {
                            it.length >= 2
                        }

                val labelWords =
                    normalizedLabel
                        .split(" ")
                        .filter {
                            it.length >= 2
                        }

                val matches =
                    queryWords.count { word ->
                        labelWords.any { labelWord ->
                            labelWord == word ||
                                labelWord.contains(word) ||
                                word.contains(labelWord)
                        }
                    }

                if (
                    matches > 0 &&
                    queryWords.isNotEmpty()
                ) {
                    score =
                        300 +
                            (
                                matches * 100 /
                                    queryWords.size
                            )
                }
            }

            if (score > bestScore) {
                bestScore = score
                best = app
            }
        }

        return if (bestScore >= 300) {
            best
        } else {
            null
        }
    }

    private fun cleanAppQuery(
        command: String
    ): String {

        var value =
            command.trim()

        val prefixes =
            listOf(
                "open ",
                "launch ",
                "start ",
                "run ",
                "open the app ",
                "launch the app ",
                "start the app ",
                "খোলো ",
                "খুলে দাও ",
                "চালু কর ",
                "অ্যাপ খোলো ",
                "ऐप खोलो ",
                "खोलो ",
                "चालू करो ",
                "ऐप खोलें "
            )

        var changed = true

        while (
            changed &&
            value.isNotEmpty()
        ) {

            changed = false

            for (prefix in prefixes) {

                if (
                    value.startsWith(
                        prefix,
                        ignoreCase = true
                    )
                ) {

                    value =
                        value
                            .substring(
                                prefix.length
                            )
                            .trim()

                    changed = true
                    break
                }
            }
        }

        val suffixes =
            listOf(
                " app",
                " অ্যাপ",
                " ऐप"
            )

        for (suffix in suffixes) {

            if (
                value.endsWith(
                    suffix,
                    ignoreCase = true
                )
            ) {

                value =
                    value
                        .dropLast(
                            suffix.length
                        )
                        .trim()

                break
            }
        }

        return value
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^\\p{L}\\p{N}]+"),
                " "
            )
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun openApplication(
        info: ResolveInfo
    ) {

        val activity =
            info.activityInfo

        val packageName =
            activity?.packageName
                .orEmpty()

        val label =
            info.loadLabel(
                packageManager
            )
                ?.toString()
                .orEmpty()

        if (
            packageName.isEmpty() ||
            activity == null
        ) {
            restartListening(500)
            return
        }

        val intent =
            Intent(
                Intent.ACTION_MAIN
            ).apply {

                addCategory(
                    Intent.CATEGORY_LAUNCHER
                )

                setClassName(
                    packageName,
                    activity.name
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }

        try {

            startActivity(intent)

            speak(
                "Opening $label.",
                "$label খুলছি।",
                "$label खोल रहा हूँ।"
            )

        } catch (_: Exception) {

            speak(
                "I could not open $label.",
                "$label খোলা যায়নি।",
                "$label नहीं खुल सका।"
            )
        }
    }

    private fun searchWeb(
        query: String
    ) {

        val finalQuery =
            query.ifBlank {
                "search"
            }

        val url =
            "https://www.google.com/search?q=" +
                encode(finalQuery)

        openBrowserUrl(url)

        speak(
            "Searching.",
            "সার্চ করছি।",
            "सर्च कर रहा हूँ।"
        )
    }

    private fun removeSearchPrefix(
        text: String
    ): String {

        val prefixes =
            listOf(
                "search for ",
                "search ",
                "সার্চ কর ",
                "সার্চ ",
                "খুঁজে দেখ ",
                "सर्च करो ",
                "सर्च ",
                "खोजो "
            )

        for (prefix in prefixes) {

            if (
                text.startsWith(
                    prefix,
                    ignoreCase = true
                )
            ) {

                return text
                    .substring(
                        prefix.length
                    )
                    .trim()
            }
        }

        return text.trim()
    }

    private fun openBrowserUrl(
        url: String
    ) {

        try {

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

        } catch (_: Exception) {
        }
    }

    private fun speak(
        english: String,
        bengali: String,
        hindi: String
    ) {

        if (!isRunning) {
            return
        }

        if (!ttsReady) {
            restartListening(700)
            return
        }

        val tag =
            selectedLanguageTag()

        tts?.language =
            selectedLocale()

        val text =
            when (tag) {

                "bn-IN" ->
                    bengali

                "hi-IN" ->
                    hindi

                else ->
                    english
            }

        speaking = true

        sendVoiceState("SPEAKING")

        val utteranceId =
            "ultra_${System.currentTimeMillis()}"

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        handler.postDelayed(
            {
                if (!isRunning) return@postDelayed

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
                Locale(
                    "hi",
                    "IN"
                )

            "bn-IN" ->
                Locale(
                    "bn",
                    "IN"
                )

            else ->
                Locale(
                    "en",
                    "IN"
                )
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
                clazz.getMethod(
                    "language"
                )

            method.invoke(
                repository
            ) as? String ?: "english"

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

        if (
            !isRunning ||
            restarting
        ) {
            return
        }

        restarting = true

        handler.postDelayed(
            {

                restarting = false

                if (
                    isRunning &&
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

        if (!isRunning) {
            return
        }

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

        if (!isRunning) {
            return
        }

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

        if (!isRunning) {
            return
        }

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

        if (!isRunning) {
            return
        }

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

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    "ultra_assistant",
                    "ULTRA AI Assistant",
                    NotificationManager.IMPORTANCE_LOW
                )

            getSystemService(
                NotificationManager::class.java
            ).createNotificationChannel(
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

        isRunning = false

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

        ttsReady = false
        listening = false
        speaking = false
        restarting = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(
                STOP_FOREGROUND_REMOVE
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    override fun onDestroy() {

        isRunning = false

        handler.removeCallbacksAndMessages(
            null
        )

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

        ttsReady = false
        listening = false
        speaking = false
        restarting = false

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}