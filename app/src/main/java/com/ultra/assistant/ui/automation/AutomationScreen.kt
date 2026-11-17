package com.ultra.assistant.ui.automation

import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.ultra.assistant.MainActivity
import com.ultra.assistant.automation.RuleManager
import com.ultra.assistant.automation.TaskScheduler
import com.ultra.assistant.ui.UiKit

object AutomationScreen {
    private const val PREFS = "ultra_scheduled_tasks"

    fun render(a: MainActivity, root: LinearLayout) {
        root.removeAllViews()
        root.addView(UiKit.text(a, "Automation Center", 21f, 0xFFFFFFFF.toInt(), true))
        root.addView(UiKit.text(a, "লোকাল Rule এবং সময়ভিত্তিক Reminder তৈরি করুন। Reminder WorkManager দিয়ে নির্ধারিত সময়ে notification দেখাবে।", 13f, 0xFFB4C6D8.toInt()))

        val name = EditText(a).apply {
            hint = "Rule name (যেমন: Morning routine)"
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9BB0C5.toInt())
        }
        val details = EditText(a).apply {
            hint = "কাজের বিবরণ"
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9BB0C5.toInt())
        }
        root.addView(name); root.addView(details)
        root.addView(UiKit.button(a, "＋ SAVE AUTOMATION RULE") {
            val n = name.text.toString().trim(); val d = details.text.toString().trim()
            if (n.isBlank() || d.isBlank()) Toast.makeText(a, "নাম ও বিবরণ লিখুন", Toast.LENGTH_SHORT).show()
            else { RuleManager(a).add(n, d); render(a, root) }
        })

        root.addView(UiKit.text(a, "Scheduled reminder", 18f, 0xFF83CFFF.toInt(), true))
        val reminder = EditText(a).apply {
            hint = "Reminder message"
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9BB0C5.toInt())
        }
        val minutes = EditText(a).apply {
            hint = "কত মিনিট পরে? (যেমন 30)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9BB0C5.toInt())
        }
        root.addView(reminder); root.addView(minutes)
        root.addView(UiKit.button(a, "＋ SCHEDULE REMINDER") {
            val title = reminder.text.toString().trim()
            val delay = minutes.text.toString().toLongOrNull()
            if (title.isBlank() || delay == null || delay !in 1..525600) {
                Toast.makeText(a, "বার্তা লিখুন এবং 1 থেকে 525600 মিনিট দিন", Toast.LENGTH_LONG).show()
            } else {
                runCatching {
                    val id = TaskScheduler(a).scheduleReminder(title, delay)
                    a.getSharedPreferences(PREFS, 0).edit().putString(id, title).apply()
                    Toast.makeText(a, "Reminder schedule হয়েছে", Toast.LENGTH_SHORT).show()
                    render(a, root)
                }.onFailure { Toast.makeText(a, "Schedule করা যায়নি: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        })

        root.addView(UiKit.text(a, "Saved rules", 18f, 0xFF83CFFF.toInt(), true))
        val rules = RuleManager(a).all()
        if (rules.isEmpty()) root.addView(UiKit.text(a, "এখনও কোনো rule নেই।"))
        rules.forEach { rule ->
            val card = UiKit.card(a)
            card.addView(UiKit.text(a, rule.first, 16f, 0xFFFFFFFF.toInt(), true))
            card.addView(UiKit.text(a, rule.second, 13f))
            card.addView(UiKit.button(a, "Delete rule") { RuleManager(a).remove(rule.first); render(a, root) })
            root.addView(card)
        }

        root.addView(UiKit.text(a, "Pending reminders", 18f, 0xFF83CFFF.toInt(), true))
        val scheduled = a.getSharedPreferences(PREFS, 0).all
        if (scheduled.isEmpty()) root.addView(UiKit.text(a, "কোনো pending reminder নেই।"))
        scheduled.forEach { (id, value) ->
            val card = UiKit.card(a)
            card.addView(UiKit.text(a, value.toString(), 15f, 0xFFFFFFFF.toInt(), true))
            card.addView(UiKit.button(a, "Cancel reminder") {
                TaskScheduler(a).cancel(id)
                a.getSharedPreferences(PREFS, 0).edit().remove(id).apply()
                render(a, root)
            })
            root.addView(card)
        }
    }
}
