package com.ultra.assistant

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale
import com.ultra.assistant.data.LocalSettingsRepository
import com.ultra.assistant.voice.LanguageManager

class VoiceAssistantService : Service(), TextToSpeech.OnInitListener {
    companion object { @Volatile var isRunning: Boolean = false; private set }
    private var speech: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var commandMode = false
    private var isListening = false
    private var restartQueued = false
    private lateinit var dot: FloatingDot
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        makeChannel()
        startForeground(31, makeNotification())
        tts = TextToSpeech(this, this)
        dot = FloatingDot(this)
        if (android.provider.Settings.canDrawOverlays(this)) dot.show()
        beginListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SHOW_DOT" && ::dot.isInitialized) dot.show()
        return START_STICKY
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.language = LanguageManager.locale(LocalSettingsRepository(this@VoiceAssistantService).language())
        }
    }

    private fun beginListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition is unavailable on this device.")
            return
        }
        if (speech == null) {
            speech = SpeechRecognizer.createSpeechRecognizer(this)
            speech?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isListening = false }
                override fun onError(error: Int) { isListening = false; queueRestart(1300) }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    processPhrase(phrase)
                    queueRestart(800)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val phrase = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (!commandMode && phrase.hasWakeWord()) {
                        commandMode = true
                        say("Yes, tell me.")
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val tag = when (LocalSettingsRepository(this@VoiceAssistantService).language()) {
                "English" -> "en-IN"
                "हिन्दी" -> "hi-IN"
                else -> "bn-BD"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try { speech?.startListening(i); isListening = true } catch (_: Exception) { queueRestart(1500) }
    }

    private fun processPhrase(phrase: String) {
        if (phrase.isBlank()) return
        if (!commandMode && phrase.hasWakeWord()) {
            val inlineCommand = phrase.removeWake()
            if (inlineCommand.isBlank()) {
                commandMode = true
                say(if (LocalSettingsRepository(this).language() == "বাংলা") "জি, বলুন।" else "Yes, tell me.")
            } else {
                executeCommand(inlineCommand)
            }
            return
        }
        if (commandMode) {
            val command = phrase.removeWake()
            if (phrase.hasWakeWord() && command.isBlank()) {
                // Keep command mode armed: the next recognition session captures the user's command.
                return
            }
            commandMode = false
            if (command.isNotBlank()) executeCommand(command)
        }
    }

    private fun executeCommand(command: String) {
        val q = command.trim().ifBlank { "help" }
        when {
            q.contains("lock phone", true) || q.contains("ফোন লক") -> {
                val admin = android.content.ComponentName(this, com.ultra.assistant.admin.UltraDeviceAdminReceiver::class.java)
                val policy = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                if (policy.isAdminActive(admin)) {
                    runCatching { policy.lockNow(); say("Locking the phone now.") }
                        .onFailure { say("I could not lock the phone.") }
                } else say("Device Administrator permission is not active.")
            }
            q.contains("next", true) || q.contains("পরের গান") -> {
                sendBroadcast(Intent("com.ultra.assistant.NEXT_TRACK"))
                say("I will try to skip to the next track.")
            }
            q.contains("youtube", true) || q.contains("ইউটিউব") || q.contains("গান") || q.contains("song", true) || q.contains("music", true) ->
                search("https://www.youtube.com/results?search_query=" + ("Hindi song " + q).urlPart(), "Searching YouTube.")
            q.contains("open chrome", true) || q.contains("ক্রোম খোলো") || q.contains("ক্রোম ওপেন") -> launchPackage("com.android.chrome", "Chrome")
            q.contains("open settings", true) || q.contains("সেটিংস খোলো") || q.contains("সেটিংস ওপেন") -> launchPackage("com.android.settings", "Settings")
            q.contains("search", true) || q.contains("সার্চ") || q.contains("খুঁজে") ->
                search("https://www.google.com/search?q=" + q.urlPart(), "Searching Google.")
            else -> search("https://www.google.com/search?q=" + q.urlPart(), "I am searching for that.")
        }
    }

    private fun launchPackage(pkg: String, name: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) say("$name is not installed.")
        else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); say("Opening $name.") }
    }

    private fun search(url: String, response: String) {
        say(response)
        try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { say("I could not open the browser.") }
    }

    private fun say(message: String) {
        if (ttsReady) {
            tts?.language = LanguageManager.locale(LocalSettingsRepository(this).language())
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ultra-${System.currentTimeMillis()}")
        }
    }

    private fun queueRestart(delay: Long) {
        if (restartQueued) return
        restartQueued = true
        handler.postDelayed({
            restartQueued = false
            if (!isListening) {
                try { speech?.cancel() } catch (_: Exception) {}
                beginListening()
            }
        }, delay)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { speech?.cancel(); speech?.destroy() } catch (_: Exception) {}
        speech = null
        tts?.stop(); tts?.shutdown()
        if (::dot.isInitialized) dot.hide()
        isRunning = false
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun makeChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel("ultra_assistant", "ULTRA AI Assistant", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }
    private fun makeNotification(): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "ultra_assistant")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("ULTRA AI is listening")
            .setContentText("Wake word: Hey Ultra • Tap to open")
            .setContentIntent(pi).setOngoing(true).build()
    }
}

private fun String.hasWakeWord() = com.ultra.assistant.voice.WakeWordEngine.detected(this)
private fun String.removeWake() = com.ultra.assistant.voice.WakeWordEngine.strip(this)
private fun String.urlPart() = java.net.URLEncoder.encode(this, "UTF-8")
