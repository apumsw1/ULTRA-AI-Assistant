package com.ultra.assistant.commands
object CommandParser { fun normalize(text:String)=text.trim().lowercase();fun isSearch(text:String)=listOf("search","সার্চ","খুঁজে","খোঁজ").any{normalize(text).contains(it)};fun isNext(text:String)=normalize(text).let{it.contains("next")||it.contains("পরের গান")}}
