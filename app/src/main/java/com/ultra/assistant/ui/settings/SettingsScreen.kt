package com.ultra.assistant.ui.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.LinearLayout
import com.ultra.assistant.MainActivity
import com.ultra.assistant.admin.UltraDeviceAdminReceiver
import com.ultra.assistant.data.LocalSettingsRepository
import com.ultra.assistant.ui.UiKit

object SettingsScreen {
    fun render(a: MainActivity, root: LinearLayout) {
        root.removeAllViews()
        root.addView(UiKit.text(a, "Settings & Permissions", 21f, 0xFFFFFFFF.toInt(), true))
        val prefs = UiKit.card(a)
        prefs.addView(UiKit.text(a, "Assistant preferences", 17f, 0xFFFFFFFF.toInt(), true))
        prefs.addView(UiKit.text(a, "Language: ${LocalSettingsRepository(a).language()}"))
        prefs.addView(UiKit.text(a, "Wake phrase: Hey Ultra"))
        root.addView(prefs)

        root.addView(UiKit.button(a, "Language: বাংলা / English / हिन्दी") {
            val choices = arrayOf("বাংলা", "English", "हिन्दी")
            android.app.AlertDialog.Builder(a).setTitle("Assistant language")
                .setItems(choices) { _, which -> LocalSettingsRepository(a).setLanguage(choices[which]); render(a, root) }.show()
        })
        root.addView(UiKit.button(a, "Microphone / App permissions") {
            a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.packageName}")))
        })
        root.addView(UiKit.button(a, "Floating window permission") {
            a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
        })
        root.addView(UiKit.button(a, "Battery optimization settings") {
            runCatching { a.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })

        val dpm = a.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(a, UltraDeviceAdminReceiver::class.java)
        val active = dpm.isAdminActive(admin)
        val adminCard = UiKit.card(a)
        adminCard.addView(UiKit.text(a, "Device Administrator", 17f, 0xFFFFFFFF.toInt(), true))
        adminCard.addView(UiKit.text(a, if (active) "Status: Active" else "Status: Not activated"))
        adminCard.addView(UiKit.text(a, "এই অনুমতি ঐচ্ছিক। Android-এর নিজস্ব confirmation screen ছাড়া এটি সক্রিয় করা যায় না; Settings থেকে যেকোনো সময় বন্ধ করা যাবে।", 12f, 0xFFB4C6D8.toInt()))
        if (active) {
            adminCard.addView(UiKit.button(a, "OPEN ANDROID ADMIN SETTINGS") {
                runCatching { a.startActivity(Intent(Settings.ACTION_DEVICE_ADMIN_SETTINGS)) }
            })
        } else {
            adminCard.addView(UiKit.button(a, "ADMIN ACTIVATION NOT CONFIGURED") {
                android.app.AlertDialog.Builder(a).setTitle("Activation code verification")
                    .setMessage("এই ZIP-এ বিদ্যমান Cloudflare activation verification endpoint বা request format নেই। ভুল/নকল verification না করে Device Administrator activation আপাতত বন্ধ রাখা হয়েছে। API details যুক্ত হলে code verification সফল হওয়ার পর Android-এর official activation screen খোলা হবে।")
                    .setPositiveButton("OK", null).show()
            })
        }
        root.addView(adminCard)
        root.addView(UiKit.text(a, "Cloudflare Worker/API ইচ্ছাকৃতভাবে অপরিবর্তিত রাখা হয়েছে। এই Android ZIP-এ activation endpoint configuration পাওয়া যায়নি।", 12f, 0xFF83CFFF.toInt()))
    }
}
