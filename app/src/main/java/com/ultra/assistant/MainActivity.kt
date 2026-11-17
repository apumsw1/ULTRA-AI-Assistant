package com.ultra.assistant

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ultra.assistant.permissions.PermissionManager
import com.ultra.assistant.ui.UiKit
import com.ultra.assistant.ui.HomeScreen
import com.ultra.assistant.ui.VoiceScreen
import com.ultra.assistant.ui.apps.AppManagerScreen
import com.ultra.assistant.ui.automation.AutomationScreen
import com.ultra.assistant.ui.settings.SettingsScreen

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val requestCode = 77
    private var selected = "Home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF071426.toInt()
        window.navigationBarColor = 0xFF071426.toInt()
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF071426.toInt()) }
        val header = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(22,20,22,12) }
        header.addView(UiKit.text(this,"ULTRA AI",29f,0xFFFFFFFF.toInt(),true))
        header.addView(UiKit.text(this,"PERSONAL VOICE ASSISTANT",11f,0xFF83CFFF.toInt(),true))
        status = UiKit.text(this,"● Assistant status: ${if (VoiceAssistantService.isRunning) "RUNNING" else "STOPPED"}",13f,0xFF71D8B4.toInt(),true)
        header.addView(status)
        page.addView(header)
        val nav = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false }
        val tabs=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(10,4,10,8) }
        listOf("Home","Voice","Apps","Automation","Settings").forEach { name ->
            val b=Button(this).apply { text=name; textSize=12f; isAllCaps=false; setOnClickListener { selected=name; render() } }
            tabs.addView(b)
        }
        nav.addView(tabs); page.addView(nav)
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(18,8,18,24) }
        page.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(page)
        render()
    }

    fun render() {
        content.removeAllViews()
        when(selected) {
            "Home" -> HomeScreen.render(this,content)
            "Voice" -> VoiceScreen.render(this,content)
            "Apps" -> AppManagerScreen.render(this,content)
            "Automation" -> AutomationScreen.render(this,content)
            "Settings" -> SettingsScreen.render(this,content)
        }
        status.text="● Assistant status: ${if (VoiceAssistantService.isRunning) "RUNNING" else "STOPPED"}"
    }

    fun startAssistant() {
        val missing=PermissionManager.missing(this)
        if(missing.isNotEmpty()) { ActivityCompat.requestPermissions(this,missing.toTypedArray(),requestCode); return }
        ContextCompat.startForegroundService(this,Intent(this,VoiceAssistantService::class.java))
        status.text="● Assistant status: STARTING"
        Toast.makeText(this,"ULTRA AI চালু হচ্ছে",Toast.LENGTH_SHORT).show()
    }
    fun stopAssistant() { stopService(Intent(this,VoiceAssistantService::class.java)); status.text="● Assistant status: STOPPED" }
    fun enableFloating() {
        if(!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
        else { ContextCompat.startForegroundService(this,Intent(this,VoiceAssistantService::class.java).setAction("SHOW_DOT")); Toast.makeText(this,"Floating U চালু করার অনুরোধ পাঠানো হয়েছে",Toast.LENGTH_SHORT).show() }
    }
    override fun onResume(){ super.onResume(); if(::content.isInitialized) render() }
    override fun onRequestPermissionsResult(code:Int,p:Array<out String>,g:IntArray){ super.onRequestPermissionsResult(code,p,g); if(code==requestCode && g.isNotEmpty() && g.all{it==PackageManager.PERMISSION_GRANTED}) startAssistant() else Toast.makeText(this,"Microphone permission প্রয়োজন",Toast.LENGTH_LONG).show() }
}
