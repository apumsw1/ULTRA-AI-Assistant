package com.ultra.assistant.ui.apps

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.LinearLayout
import com.ultra.assistant.MainActivity
import com.ultra.assistant.ui.UiKit

object AppManagerScreen {
    fun render(a: MainActivity, root: LinearLayout) {
        root.removeAllViews()
        root.addView(UiKit.text(a, "Installed apps", 21f, 0xFFFFFFFF.toInt(), true))
        root.addView(UiKit.text(a, "Android launcher-এ দৃশ্যমান অ্যাপ খোলা বা App info দেখা যাবে। Android security policy অনুযায়ী অন্য অ্যাপ জোর করে বন্ধ/নিয়ন্ত্রণ করা যাবে না।", 13f, 0xFFB4C6D8.toInt()))
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = runCatching {
            a.packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(a.packageManager).toString().lowercase() }
        }.getOrDefault(emptyList())
        apps.forEach { info ->
            val label = info.loadLabel(a.packageManager).toString()
            val pkg = info.activityInfo.packageName
            val card = UiKit.card(a)
            card.addView(UiKit.text(a, label, 16f, 0xFFFFFFFF.toInt(), true))
            card.addView(UiKit.text(a, pkg, 11f, 0xFF9BB0C5.toInt()))
            card.addView(UiKit.button(a, "OPEN APP") {
                val launch = a.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) a.startActivity(launch)
            })
            card.addView(UiKit.button(a, "APP INFO") {
                a.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            })
            root.addView(card)
        }
        if (apps.isEmpty()) root.addView(UiKit.text(a, "এই ডিভাইসে launcher app তালিকা পাওয়া যায়নি।"))
    }
}
