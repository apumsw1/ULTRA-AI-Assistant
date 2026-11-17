package com.ultra.assistant.automation
import android.content.Context
class RuleManager(c:Context){private val p=c.getSharedPreferences("ultra_automation",Context.MODE_PRIVATE)
 fun all():List<Pair<String,String>> = p.all.entries.filter{it.value is String}.map{it.key to (it.value as String)}.sortedBy{it.first}
 fun add(name:String,description:String){p.edit().putString(name,description).apply()}
 fun remove(name:String){p.edit().remove(name).apply()}
}
