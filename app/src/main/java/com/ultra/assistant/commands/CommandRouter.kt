package com.ultra.assistant.commands
/** Local command classification. Actual phone actions remain in the Android service. */
object CommandRouter { fun category(command:String):String=when{CommandParser.isNext(command)->"MEDIA_NEXT";command.contains("youtube",true)||command.contains("গান")->"YOUTUBE_SEARCH";command.contains("open",true)||command.contains("খোলো")->"APP_OPEN";CommandParser.isSearch(command)->"WEB_SEARCH";else->"WEB_SEARCH"} }
