package com.vianshield.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val blue = Color.rgb(21, 145, 218)
    private val ink = Color.rgb(30, 45, 58)
    private val muted = Color.rgb(100, 116, 130)
    private val paleBlue = Color.rgb(231, 246, 255)
    private val prefs by lazy { getSharedPreferences("vianshield_shell", MODE_PRIVATE) }
    private var demoRequestedOn = false
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(246, 250, 253)
        window.navigationBarColor = Color.rgb(246, 250, 253)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        demoRequestedOn = prefs.getBoolean("demo_requested_on", false)
        showHome()
    }

    private fun pageBase(): LinearLayout {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
            setBackgroundColor(Color.rgb(246, 250, 253))
        }
        scroll.addView(root)
        setContentView(scroll)
        return root
    }

    private fun showHome() {
        val page = pageBase()
        page.addView(text("Vianshield", 29, ink, true))
        page.addView(text("Lightweight firewall", 14, muted, false).apply {
            setPadding(0, dp(3), 0, dp(22))
        })

        val statusCard = card()
        statusCard.addView(text(if (demoRequestedOn) "Demo toggle: ON" else "Disconnected", 20, ink, true))
        statusCard.addView(text("VPN engine: not integrated", 14, muted, false).apply {
            setPadding(0, dp(8), 0, dp(2))
        })
        statusCard.addView(text("This is a UI prototype. No traffic is protected.", 13, Color.rgb(166, 75, 35), false).apply {
            setPadding(0, dp(8), 0, 0)
        })
        page.addView(statusCard, matchWrap())

        val toggle = Button(this).apply {
            text = if (demoRequestedOn) "Stop" else "Start"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue, 16)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setOnClickListener {
                demoRequestedOn = !demoRequestedOn
                prefs.edit().putBoolean("demo_requested_on", demoRequestedOn).apply()
                addLog(if (demoRequestedOn) "Demo Start toggle pressed; engine unavailable, protection inactive" else "Demo Stop toggle pressed; engine unavailable")
                showHome()
            }
        }
        page.addView(toggle, LinearLayout.LayoutParams(-1, dp(58)).apply {
            topMargin = dp(18)
            bottomMargin = dp(14)
        })

        val logs = actionCard("Log Keeper", "View saved events, warnings and errors") { showLogs() }
        page.addView(logs, matchWrap())
        page.addView(actionCard("Settings", "DNS, filtering and speed-limit options — coming later") { showSettings() }, matchWrap().apply {
            topMargin = dp(12)
        })
        page.addView(text("Prototype shell • Engine integration comes later", 12, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun showLogs() {
        val page = pageBase()
        page.addView(text("Log Keeper", 26, ink, true))
        page.addView(text("Locally saved prototype events", 14, muted, false).apply {
            setPadding(0, dp(4), 0, dp(18))
        })
        val entries = prefs.getString("logs", "")!!.lines().filter { it.isNotBlank() }.reversed()
        if (entries.isEmpty()) {
            page.addView(card().apply { addView(text("No events yet. Use the Start/Stop toggle to create a test entry.", 14, muted, false)) }, matchWrap())
        } else {
            entries.forEach { line ->
                page.addView(card().apply { addView(text(line, 13, ink, false)) }, matchWrap().apply { bottomMargin = dp(8) })
            }
        }
        page.addView(actionCard("Clear logs", "Remove saved prototype events") {
            prefs.edit().remove("logs").apply()
            addLog("Log Keeper cleared")
            showLogs()
        }, matchWrap().apply { topMargin = dp(8) })
        page.addView(backButton { showHome() }, matchWrap().apply { topMargin = dp(16) })
    }

    private fun showSettings() {
        val page = pageBase()
        page.addView(text("Settings", 26, ink, true))
        page.addView(text("These are placeholders for later implementation.", 14, muted, false).apply {
            setPadding(0, dp(5), 0, dp(18))
        })
        listOf(
            "DNS provider" to "Not implemented",
            "Ad and tracker filtering" to "Not implemented",
            "Per-app firewall rules" to "Not implemented",
            "Global socket buffer / speed limit" to "Not implemented",
            "WireGuard configuration" to "Not implemented"
        ).forEach { (title, subtitle) ->
            page.addView(actionCard(title, subtitle) {
                addLog("Placeholder opened: $title")
            }, matchWrap().apply { bottomMargin = dp(10) })
        }
        page.addView(backButton { showHome() }, matchWrap().apply { topMargin = dp(8) })
    }

    private fun actionCard(title: String, subtitle: String, action: () -> Unit): View {
        return card().apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            addView(text(title, 16, ink, true))
            addView(text(subtitle, 13, muted, false).apply { setPadding(0, dp(5), 0, 0) })
        }
    }

    private fun backButton(action: () -> Unit) = Button(this).apply {
        text = "Back"
        isAllCaps = false
        setTextColor(blue)
        background = rounded(paleBlue, 14)
        setOnClickListener { action() }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, 18, Color.rgb(225, 234, 241))
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.START
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun addLog(message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val old = prefs.getString("logs", "") ?: ""
        val lines = (old.lines().filter { it.isNotBlank() } + "$stamp  $message").takeLast(300)
        prefs.edit().putString("logs", lines.joinToString("\n")).apply()
    }
}
