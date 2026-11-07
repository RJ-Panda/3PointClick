package com.threepointclick

import android.content.Context
import android.content.SharedPreferences
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
        const val DEFAULT_V_SCROLL_RIGHT = true
        const val DEFAULT_ENABLE_H_SCROLL = false
        const val DEFAULT_H_SCROLL_BOTTOM = true
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
            setTextColor(0xFFFFFFFF.toInt())
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

        addDivider()

        // --- SECTION: SCROLL BARS & 3RD CLICK ---
        val scrollSectionHeader = TextView(context).apply {
            text = "SCROLL BARS & 3RD CLICK"
            setTextColor(0xFF90CAF9.toInt())
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(scrollSectionHeader)

        // 1. Vertical Scroll Bar Toggle
        var vScrollEnabled = prefs.getBoolean(KEY_ENABLE_V_SCROLL, DEFAULT_ENABLE_V_SCROLL)
        var vScrollIsRight = prefs.getBoolean(KEY_V_SCROLL_RIGHT, DEFAULT_V_SCROLL_RIGHT)
        var middleClickEnabled = prefs.getBoolean(KEY_ENABLE_MIDDLE_CLICK, DEFAULT_ENABLE_MIDDLE_CLICK)

        val cbVScroll = CheckBox(context).apply {
            text = "Vertical Scroll Bar"
            isChecked = vScrollEnabled
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(cbVScroll)

        // Vertical options sub-container
        val vOptionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 8, 0, 16)
            visibility = if (vScrollEnabled) android.view.View.VISIBLE else android.view.View.GONE
        }

        val vPosLabel = TextView(context).apply {
            text = "Position:"
            setTextColor(0xFFB0BEC5.toInt())
            textSize = 13f
        }
        vOptionsLayout.addView(vPosLabel)

        val rgVPos = RadioGroup(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val rbVRight = RadioButton(context).apply {
            text = "Right side"
            isChecked = vScrollIsRight
            setTextColor(0xFFECEFF1.toInt())
        }
        val rbVLeft = RadioButton(context).apply {
            text = "Left side"
            isChecked = !vScrollIsRight
            setTextColor(0xFFECEFF1.toInt())
        }
        rgVPos.addView(rbVRight)
        rgVPos.addView(rbVLeft)
        vOptionsLayout.addView(rgVPos)

        // Middle click button toggle (under vertical scroll)
        val cbMiddleClick = CheckBox(context).apply {
            text = "3rd Click (Middle Click) Button at bottom"
            isChecked = middleClickEnabled
            setTextColor(0xFFFFFFFF.toInt())
        }
        vOptionsLayout.addView(cbMiddleClick)

        layout.addView(vOptionsLayout)

        cbVScroll.setOnCheckedChangeListener { _, isChecked ->
            vScrollEnabled = isChecked
            vOptionsLayout.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        // 2. Horizontal Scroll Bar Toggle
        var hScrollEnabled = prefs.getBoolean(KEY_ENABLE_H_SCROLL, DEFAULT_ENABLE_H_SCROLL)
        var hScrollIsBottom = prefs.getBoolean(KEY_H_SCROLL_BOTTOM, DEFAULT_H_SCROLL_BOTTOM)

        val cbHScroll = CheckBox(context).apply {
            text = "Horizontal Scroll Bar"
            isChecked = hScrollEnabled
            setTextColor(0xFFFFFFFF.toInt())
        }
        layout.addView(cbHScroll)

        // Horizontal options sub-container
        val hOptionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 8, 0, 16)
            visibility = if (hScrollEnabled) android.view.View.VISIBLE else android.view.View.GONE
        }

        val hPosLabel = TextView(context).apply {
            text = "Position:"
            setTextColor(0xFFB0BEC5.toInt())
            textSize = 13f
        }
        hOptionsLayout.addView(hPosLabel)

        val rgHPos = RadioGroup(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val rbHBottom = RadioButton(context).apply {
            text = "Bottom"
            isChecked = hScrollIsBottom
            setTextColor(0xFFECEFF1.toInt())
        }
        val rbHTop = RadioButton(context).apply {
            text = "Top"
            isChecked = !hScrollIsBottom
            setTextColor(0xFFECEFF1.toInt())
        }
        rgHPos.addView(rbHBottom)
        rgHPos.addView(rbHTop)
        hOptionsLayout.addView(rgHPos)

        layout.addView(hOptionsLayout)

        cbHScroll.setOnCheckedChangeListener { _, isChecked ->
            hScrollEnabled = isChecked
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
            setTextColor(0xFFFFFFFF.toInt())
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
            setTextColor(0xFFFFFFFF.toInt())
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
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val portVal = editPort.text.toString().toIntOrNull() ?: DEFAULT_PORT
                val hostVal = editHost.text.toString().trim().ifEmpty { DEFAULT_HOST }

                val finalVScroll = cbVScroll.isChecked
                val finalVRight = rbVRight.isChecked
                val finalHScroll = cbHScroll.isChecked
                val finalHBottom = rbHBottom.isChecked
                val finalMiddle = cbMiddleClick.isChecked

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
