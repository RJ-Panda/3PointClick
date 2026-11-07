package com.threepointclick

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import java.util.Locale

class SettingsDialog(
    context: Context,
    private val onSaved: (speed: Float, host: String, port: Int) -> Unit
) : AppCompatDialog(context) {

    companion object {
        const val PREFS_NAME = "3PointClickPrefs"
        const val KEY_SPEED = "speed_multiplier"
        const val KEY_HOST = "server_host"
        const val KEY_PORT = "server_port"

        const val DEFAULT_SPEED = 1.5f
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8888
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.settings_title)

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(0xFF1E222B.toInt())
        }

        // Title
        val titleText = TextView(context).apply {
            text = context.getString(R.string.settings_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(titleText)

        // Speed Multiplier
        var currentSpeed = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED)
        val speedLabel = TextView(context).apply {
            setTextColor(0xFFD0D7DE.toInt())
            textSize = 14f
            text = String.format(Locale.US, context.getString(R.string.speed_label), currentSpeed)
        }
        layout.addView(speedLabel)

        val speedSeekBar = SeekBar(context).apply {
            max = 35 // maps to 0.5f .. 4.0f (step 0.1)
            progress = ((currentSpeed - 0.5f) * 10f).toInt().coerceIn(0, 35)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentSpeed = 0.5f + (progress / 10f)
                    speedLabel.text = String.format(Locale.US, context.getString(R.string.speed_label), currentSpeed)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        layout.addView(speedSeekBar)

        // Port Input
        val portLabel = TextView(context).apply {
            setTextColor(0xFFD0D7DE.toInt())
            textSize = 14f
            text = context.getString(R.string.port_label)
            setPadding(0, 24, 0, 8)
        }
        layout.addView(portLabel)

        val editPort = EditText(context).apply {
            setText(prefs.getInt(KEY_PORT, DEFAULT_PORT).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2B303C.toInt())
            setPadding(24, 16, 24, 16)
        }
        layout.addView(editPort)

        // Host Input (Loopback 127.0.0.1 for ADB, or local IP for WiFi)
        val hostLabel = TextView(context).apply {
            setTextColor(0xFFD0D7DE.toInt())
            textSize = 14f
            text = "Host IP (127.0.0.1 for ADB Reverse)"
            setPadding(0, 24, 0, 8)
        }
        layout.addView(hostLabel)

        val editHost = EditText(context).apply {
            setText(prefs.getString(KEY_HOST, DEFAULT_HOST))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2B303C.toInt())
            setPadding(24, 16, 24, 16)
        }
        layout.addView(editHost)

        // Button row
        val btnRow = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 0)
        }

        val cancelBtn = Button(context).apply {
            text = context.getString(R.string.cancel)
            setBackgroundColor(0x00000000)
            setTextColor(0xFF9E9E9E.toInt())
            setOnClickListener { dismiss() }
        }

        val saveBtn = Button(context).apply {
            text = context.getString(R.string.save)
            setBackgroundColor(0xFF2196F3.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val portVal = editPort.text.toString().toIntOrNull() ?: DEFAULT_PORT
                val hostVal = editHost.text.toString().trim().ifEmpty { DEFAULT_HOST }

                prefs.edit()
                    .putFloat(KEY_SPEED, currentSpeed)
                    .putInt(KEY_PORT, portVal)
                    .putString(KEY_HOST, hostVal)
                    .apply()

                onSaved(currentSpeed, hostVal, portVal)
                dismiss()
            }
        }

        btnRow.addView(cancelBtn, android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(saveBtn, android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(btnRow)

        setContentView(layout)
    }
}
