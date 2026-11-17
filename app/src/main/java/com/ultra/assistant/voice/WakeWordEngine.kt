package com.ultra.assistant.voice
/** Phrase matcher only. This is not an acoustic/offline hotword model. */
object WakeWordEngine {
 private val phrases=listOf("hey ultra","হে আল্ট্রা","হেই আল্ট্রা","হে আল্ট্রা")
 fun detected(text:String):Boolean=phrases.any{text.contains(it,true)}
 fun strip(text:String):String=phrases.fold(text){v,p->v.replace(p,"",true)}.trim()
}
