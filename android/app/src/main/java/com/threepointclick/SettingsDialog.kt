package com.threepointclick

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatDialog
import java.util.Locale

class SettingsDialog(
    context: Context,
    private val onSaved: (
        speed: Float,
        host: String,
        port: Int,
        enableVScroll: Boolean,
        vScrollRight: Boolean,
        enableHScroll: Boolean,
        hScrollBottom: Boolean,
        enableMiddleClick: Boolean
    ) -> Unit
) : AppCompatDialog(context) {

    companion object {
        const val PREFS_NAME = "3PointClickPrefs"
        const val KEY_SPEED = "speed_multiplier"
        const val KEY_HOST = "server_host"
        const val KEY_PORT = "server_port"

        const val KEY_ENABLE_V_SCROLL = "enable_v_scroll"
        const val KEY_V_SCROLL_RIGHT = "v_scroll_right"
        const val KEY_ENABLE_H_SCROLL = "enable_h_scroll"
        const val KEY_H_SCROLL_BOTTOM = "h_scroll_bottom"
        const val KEY_ENABLE_MIDDLE_CLICK = "enable_middle_click"

        const val DEFAULT_SPEED = 1.5f
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8888

        const val DEFAULT_ENABLE_V_SCROLL = false
        const val DEFAULT_V_SCROLL_RIGHT = true // Right side by default
        const val DEFAULT_ENABLE_H_SCROLL = false
        const val DEFAULT_H_SCROLL_BOTTOM = true // Bottom by default
        const val DEFAULT_ENABLE_MIDDLE_CLICK = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.settings_title)

        val scrollView = ScrollView(context).apply {
            setBackgroundColor(0xFF1E222B.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
        }

        // Title
        val titleText = TextView(context).apply {
            text = context.getString(R.string.settings_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, 0, 0, 24)
        }
        layout.addView(titleText)

        // --- SPEED MULTIPLIER ---
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

        fun addDivider() {
            val divider = android.view.View(context).apply {
                setBackgroundColor(0xFF2E3440.toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply {
                    setMargins(0, 24, 0, 24)
                }
            }
            layout.addView(divider)
        }

        fun createStyledSwitch(text: String, isChecked: Boolean): Switch {
            return Switch(context).apply {
                this.text = text
                this.isChecked = isChecked
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(0, 12, 0, 12)
            }
        }

        addDivider()

        // --- SECTION: SCROLL BARS & 3RD CLICK ---
        val scrollSectionHeader = TextView(context).apply {
            text = "SCROLL BARS & 3RD CLICK"
            setTextColor(0xFF90CAF9.toInt())
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        layout.addView(scrollSectionHeader)

        // 1. Vertical Scroll Bar Switch
        val vScrollEnabled = prefs.getBoolean(KEY_ENABLE_V_SCROLL, DEFAULT_ENABLE_V_SCROLL)
        val vScrollIsRight = prefs.getBoolean(KEY_V_SCROLL_RIGHT, DEFAULT_V_SCROLL_RIGHT)
        val middleClickEnabled = prefs.getBoolean(KEY_ENABLE_MIDDLE_CLICK, DEFAULT_ENABLE_MIDDLE_CLICK)

        val swVScroll = createStyledSwitch("Vertical Scroll Bar", vScrollEnabled)
        layout.addView(swVScroll)

        // Vertical options sub-container
        val vOptionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 4, 0, 12)
            visibility = if (vScrollEnabled) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Switch: Place on Left side (Off = Right side by default, On = Left side)
        val swVLeft = createStyledSwitch("Place on Left side (Default: Right)", !vScrollIsRight)
        vOptionsLayout.addView(swVLeft)

        // Switch: 3rd Click (Middle Click) Button
        val swMiddleClick = createStyledSwitch("3rd Click (Middle Click) Button", middleClickEnabled)
        vOptionsLayout.addView(swMiddleClick)

        layout.addView(vOptionsLayout)

        swVScroll.setOnCheckedChangeListener { _, isChecked ->
            vOptionsLayout.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        // 2. Horizontal Scroll Bar Switch
        val hScrollEnabled = prefs.getBoolean(KEY_ENABLE_H_SCROLL, DEFAULT_ENABLE_H_SCROLL)
        val hScrollIsBottom = prefs.getBoolean(KEY_H_SCROLL_BOTTOM, DEFAULT_H_SCROLL_BOTTOM)

        val swHScroll = createStyledSwitch("Horizontal Scroll Bar", hScrollEnabled)
        layout.addView(swHScroll)

        // Horizontal options sub-container
        val hOptionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 4, 0, 12)
            visibility = if (hScrollEnabled) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Switch: Place on Top (Off = Bottom by default, On = Top)
        val swHTop = createStyledSwitch("Place on Top (Default: Bottom)", !hScrollIsBottom)
        hOptionsLayout.addView(swHTop)

        layout.addView(hOptionsLayout)

        swHScroll.setOnCheckedChangeListener { _, isChecked ->
            hOptionsLayout.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        addDivider()

        // --- NETWORK CONFIG ---
        val portLabel = TextView(context).apply {
            setTextColor(0xFFD0D7DE.toInt())
            textSize = 14f
            text = context.getString(R.string.port_label)
            setPadding(0, 0, 0, 8)
        }
        layout.addView(portLabel)

        val editPort = EditText(context).apply {
            setText(prefs.getInt(KEY_PORT, DEFAULT_PORT).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF2B303C.toInt())
            setPadding(24, 16, 24, 16)
        }
        layout.addView(editPort)

        val hostLabel = TextView(context).apply {
            setTextColor(0xFFD0D7DE.toInt())
            textSize = 14f
            text = "Host IP (127.0.0.1 for ADB Reverse)"
            setPadding(0, 16, 0, 8)
        }
        layout.addView(hostLabel)

        val editHost = EditText(context).apply {
            setText(prefs.getString(KEY_HOST, DEFAULT_HOST))
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF2B303C.toInt())
            setPadding(24, 16, 24, 16)
        }
        layout.addView(editHost)

        // Buttons
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
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
            setTextColor(Color.WHITE)
            setOnClickListener {
                val portVal = editPort.text.toString().toIntOrNull() ?: DEFAULT_PORT
                val hostVal = editHost.text.toString().trim().ifEmpty { DEFAULT_HOST }

                val finalVScroll = swVScroll.isChecked
                val finalVRight = !swVLeft.isChecked // Checked = Left, Unchecked = Right
                val finalHScroll = swHScroll.isChecked
                val finalHBottom = !swHTop.isChecked // Checked = Top, Unchecked = Bottom
                val finalMiddle = swMiddleClick.isChecked

                prefs.edit()
                    .putFloat(KEY_SPEED, currentSpeed)
                    .putInt(KEY_PORT, portVal)
                    .putString(KEY_HOST, hostVal)
                    .putBoolean(KEY_ENABLE_V_SCROLL, finalVScroll)
                    .putBoolean(KEY_V_SCROLL_RIGHT, finalVRight)
                    .putBoolean(KEY_ENABLE_H_SCROLL, finalHScroll)
                    .putBoolean(KEY_H_SCROLL_BOTTOM, finalHBottom)
                    .putBoolean(KEY_ENABLE_MIDDLE_CLICK, finalMiddle)
                    .apply()

                onSaved(currentSpeed, hostVal, portVal, finalVScroll, finalVRight, finalHScroll, finalHBottom, finalMiddle)
                dismiss()
            }
        }

        btnRow.addView(cancelBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(saveBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        layout.addView(btnRow)

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
