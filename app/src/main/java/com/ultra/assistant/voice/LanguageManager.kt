package com.ultra.assistant.voice
import java.util.Locale
object LanguageManager {
 fun locale(tag:String):Locale=when(tag.lowercase()){"বাংলা","bn","bn-bd"->Locale("bn","BD");"हिन्दी","hi","hi-in"->Locale("hi","IN");else->Locale.US}
}
