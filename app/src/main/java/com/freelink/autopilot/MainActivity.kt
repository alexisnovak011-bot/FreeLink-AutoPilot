package com.freelink.autopilot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.graphics.Typeface
import android.graphics.Color
import android.view.ViewGroup

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "FreeLink AutoPilot"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        val info = TextView(this).apply {
            text = "Автоматическое продолжение работы в выбранном чате ChatGPT.\n\n1. Разреши службу специальных возможностей.\n2. Открой в ChatGPT чат разработки FreeLink.\n3. Вернись сюда и нажми «Запустить».\n4. Для остановки нажми «Остановить»."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, pad, 0, pad)
        }
        val settings = Button(this).apply {
            text = "Открыть специальные возможности"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val start = Button(this).apply {
            text = "Запустить автоматизацию"
            setOnClickListener {
                getSharedPreferences(ChatGptAccessibilityService.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(ChatGptAccessibilityService.KEY_ACTIVE, true).apply()
                ChatGptAccessibilityService.requestCheck = true
                info.text = "Режим включён. Открой чат FreeLink в ChatGPT. Автоматизация работает только когда ChatGPT находится на экране."
            }
        }
        val stop = Button(this).apply {
            text = "Остановить автоматизацию"
            setOnClickListener {
                getSharedPreferences(ChatGptAccessibilityService.PREFS, MODE_PRIVATE)
                    .edit().putBoolean(ChatGptAccessibilityService.KEY_ACTIVE, false).apply()
                info.text = "Автоматизация остановлена."
            }
        }
        listOf(title, info, settings, start, stop).forEach {
            layout.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = pad / 2 })
        }
        setContentView(layout)
    }
}
