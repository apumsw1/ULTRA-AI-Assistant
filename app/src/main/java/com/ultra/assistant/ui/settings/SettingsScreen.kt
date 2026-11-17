
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

        root.addView(
            UiKit.text(
                a,
                "Settings & Permissions",
                21f,
                0xFFFFFFFF.toInt(),
                true
            )
        )

        val prefs = UiKit.card(a)

        prefs.addView(
            UiKit.text(
                a,
                "Assistant Preferences",
                17f,
                0xFFFFFFFF.toInt(),
                true
            )
        )

        prefs.addView(
            UiKit.text(
                a,
                "Language: ${LocalSettingsRepository(a).language()}"
            )
        )

        prefs.addView(
            UiKit.text(a, "Wake Phrase: Hey Ultra")
        )

        root.addView(prefs)

        root.addView(
            UiKit.button(a, "Language: Bengali / English / Hindi") {

                val choices = arrayOf("বাংলা", "English", "हिन्दी")

                android.app.AlertDialog.Builder(a)
                    .setTitle("Assistant Language")
                    .setItems(choices) { _, which ->

                        LocalSettingsRepository(a)
                            .setLanguage(choices[which])

                        render(a, root)
                    }
                    .show()
            }
        )

        root.addView(
            UiKit.button(a, "Microphone / App Permissions") {

                a.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${a.packageName}")
                    )
                )
            }
        )

        root.addView(
            UiKit.button(a, "Floating Window Permission") {

                a.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${a.packageName}")
                    )
                )
            }
        )

        root.addView(
            UiKit.button(a, "Battery Optimization Settings") {

                runCatching {
                    a.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            }
        )

        val dpm = a.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager

        val admin = ComponentName(
            a,
            UltraDeviceAdminReceiver::class.java
        )

        val active = dpm.isAdminActive(admin)

        val adminCard = UiKit.card(a)

        adminCard.addView(
            UiKit.text(
                a,
                "Device Administrator",
                17f,
                0xFFFFFFFF.toInt(),
                true
            )
        )

        adminCard.addView(
            UiKit.text(
                a,
                if (active) "Status: Active"
                else "Status: Not Activated"
            )
        )

        adminCard.addView(
            UiKit.text(
                a,
                "This permission is optional. Android requires user confirmation before activation. You can deactivate it through Android settings.",
                12f,
                0xFFB4C6D8.toInt()
            )
        )

        if (active) {

            adminCard.addView(
                UiKit.button(a, "OPEN ANDROID SECURITY SETTINGS") {

                    runCatching {
                        a.startActivity(
                            Intent(Settings.ACTION_SECURITY_SETTINGS)
                        )
                    }.onFailure {
                        a.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${a.packageName}")
                            }
                        )
                    }
                }
            )

        } else {

            adminCard.addView(
                UiKit.button(a, "ADMIN ACTIVATION NOT CONFIGURED") {

                    android.app.AlertDialog.Builder(a)
                        .setTitle("Activation Code Verification")
                        .setMessage(
                            "The activation verification API endpoint and request format are not configured in this project. Device Administrator activation is disabled until proper verification is implemented."
                        )
                        .setPositiveButton("OK", null)
                        .show()
                }
            )
        }

        root.addView(adminCard)

        root.addView(
            UiKit.text(
                a,
                "Cloudflare Worker and API have not been modified. The activation endpoint is not configured in this Android project.",
                12f,
                0xFF83CFFF.toInt()
            )
        )
    }
}
