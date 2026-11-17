package com.ultra.assistant.ui
import android.widget.LinearLayout
import com.ultra.assistant.*
object VoiceScreen {
 fun render(a:MainActivity,root:LinearLayout){val c=UiKit.card(a);c.addView(UiKit.text(a,"Voice Control",21f,0xFFFFFFFF.toInt(),true));c.addView(UiKit.text(a,"Wake phrase: Hey Ultra"));c.addView(UiKit.text(a,"বর্তমান voice service: ${if(VoiceAssistantService.isRunning)"Active" else "Inactive"}"));root.addView(c)
 root.addView(UiKit.button(a,"Start voice assistant"){a.startAssistant()});root.addView(UiKit.button(a,"Stop voice assistant"){a.stopAssistant()});root.addView(UiKit.text(a,"Supported command examples",17f,0xFF83CFFF.toInt(),true));listOf("Hey Ultra, search weather","Hey Ultra, open Chrome","Hey Ultra, YouTube গান খোঁজো","Hey Ultra, open settings").forEach{root.addView(UiKit.text(a,"• $it",14f))}
 root.addView(UiKit.text(a,"এই সংস্করণে SpeechRecognizer ব্যবহার করা হয়েছে। এটি dedicated offline hotword model নয়; নির্ভরযোগ্য continuous wake-word-এর জন্য আলাদা offline engine/model পরে যোগ করতে হবে।",12f,0xFFFFC878.toInt()))}
}
