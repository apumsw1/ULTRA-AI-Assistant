package com.ultra.assistant.data
import android.content.Context
class LocalSettingsRepository(c:Context){private val p=c.getSharedPreferences("ultra_local_settings",Context.MODE_PRIVATE);fun language()=p.getString("language","বাংলা")?:"বাংলা";fun setLanguage(v:String){p.edit().putString("language",v).apply()}}
