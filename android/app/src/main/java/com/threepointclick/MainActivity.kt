package com.threepointclick

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), EventSender.ConnectionListener {

    private lateinit var touchSurface: TouchSurface
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var hintText: TextView
    private lateinit var btnSettings: View
    private lateinit var settingsBtnText: TextView

    private lateinit var eventSender: EventSender
    private lateinit var touchParser: TouchEventParser
    private lateinit var prefs: SharedPreferences

    private var lastSettingsTapTime: Long = 0L
    private val doubleTapThresholdMs = 450L
    private var tapHintToast: Toast? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive full-screen
        hideSystemUI()

        setContentView(R.layout.activity_main)

        touchSurface = findViewById(R.id.touchSurface)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        hintText = findViewById(R.id.hintText)
        btnSettings = findViewById(R.id.btnSettings)
        settingsBtnText = findViewById(R.id.settingsBtnText)

        prefs = getSharedPreferences(SettingsDialog.PREFS_NAME, Context.MODE_PRIVATE)
        val initialSpeed = prefs.getFloat(SettingsDialog.KEY_SPEED, SettingsDialog.DEFAULT_SPEED)
        val initialHost = prefs.getString(SettingsDialog.KEY_HOST, SettingsDialog.DEFAULT_HOST) ?: SettingsDialog.DEFAULT_HOST
        val initialPort = prefs.getInt(SettingsDialog.KEY_PORT, SettingsDialog.DEFAULT_PORT)

        eventSender = EventSender(host = initialHost, port = initialPort, listener = this)
        touchParser = TouchEventParser(sender = eventSender, speedMultiplier = initialSpeed)
        touchSurface.eventParser = touchParser

        // Top-left button: requires double tap to avoid accidental triggers while gaming
        btnSettings.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSettingsTapTime < doubleTapThresholdMs) {
                tapHintToast?.cancel()
                lastSettingsTapTime = 0L
                showSettings()
            } else {
                lastSettingsTapTime = now
                tapHintToast?.cancel()
                tapHintToast = Toast.makeText(this, R.string.double_tap_hint, Toast.LENGTH_SHORT)
                tapHintToast?.show()
            }
        }

        // Auto-fade hint after 6 seconds
        hintText.postDelayed({
            hintText.animate().alpha(0f).setDuration(1000).withEndAction {
                hintText.visibility = View.GONE
            }.start()
        }, 6000)

        eventSender.start()
    }

    private fun showSettings() {
        SettingsDialog(this) { newSpeed, newHost, newPort ->
            touchParser.speedMultiplier = newSpeed
            eventSender.updateConfig(newHost, newPort)
        }.show()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        tapHintToast?.cancel()
        eventSender.stop()
    }

    // ConnectionListener callbacks
    override fun onConnecting(host: String, port: Int) {
        statusDot.setBackgroundColor(Color.parseColor("#FF9800")) // Orange
        statusText.text = getString(R.string.status_connecting, port)
    }

    override fun onConnected(host: String, port: Int) {
        statusDot.setBackgroundColor(Color.parseColor("#00E676")) // Bright green
        statusText.text = getString(R.string.status_connected, port)
    }

    override fun onDisconnected() {
        statusDot.setBackgroundColor(Color.parseColor("#F44336")) // Red
        statusText.text = getString(R.string.status_reconnecting)
    }
}
