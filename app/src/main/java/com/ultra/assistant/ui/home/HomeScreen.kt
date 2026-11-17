package com.ultra.assistant.ui
import android.widget.LinearLayout
import com.ultra.assistant.*
object HomeScreen {
 fun render(a:MainActivity,root:LinearLayout){
  val card=UiKit.card(a);card.addView(UiKit.text(a,"Your personal AI assistant",21f,0xFFFFFFFF.toInt(),true));card.addView(UiKit.text(a,"বলুন: Hey Ultra — তারপর আপনার নির্দেশ দিন।",15f));card.addView(UiKit.text(a,"Voice • Apps • Automation • Local controls",12f,0xFF83CFFF.toInt()));root.addView(card)
  root.addView(UiKit.button(a,"▶  START ASSISTANT"){a.startAssistant()});root.addView(UiKit.button(a,"■  STOP ASSISTANT"){a.stopAssistant()});root.addView(UiKit.button(a,"●  ENABLE FLOATING U"){a.enableFloating()})
  val info=UiKit.card(a);info.addView(UiKit.text(a,"Quick status",18f,0xFFFFFFFF.toInt(),true));info.addView(UiKit.text(a,"Service: ${if(VoiceAssistantService.isRunning)"Running" else "Stopped"}"));info.addView(UiKit.text(a,"Wake phrase: Hey Ultra"));info.addView(UiKit.text(a,"Cloud features: এই Android build-এ সংযুক্ত নয়"));root.addView(info)
  root.addView(UiKit.text(a,"নোট: Android microphone foreground notification চালু থাকা অবস্থায় দেখা যাবে। Wake phrase transcript-based; dedicated offline hotword model নয়। Android-এর battery ও microphone restrictions-এর কারণে সব ফোনে ২৪/৭ listening নিশ্চিত নয়।",12f,0xFFB4C6D8.toInt()))
 }
}
