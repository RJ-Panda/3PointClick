package com.threepointclick

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TouchSurface @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var eventParser: TouchEventParser? = null

    // Visual Paints
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA2196F3.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val pointerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x332196F3.toInt()
        style = Paint.Style.FILL
    }
    private val leftClickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA00E676.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val leftClickFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3300E676.toInt()
        style = Paint.Style.FILL
    }
    private val rightClickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFF9100.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val rightClickFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FF9100.toInt()
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    // Active touch coordinates for drawing
    private class TouchIndicator(val x: Float, val y: Float, val label: String, val role: Int)
    private val currentTouches = mutableListOf<TouchIndicator>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parser = eventParser ?: return super.onTouchEvent(event)

        parser.handleTouchEvent(event)
        updateVisuals(event, parser)
        return true
    }

    private fun updateVisuals(event: MotionEvent, parser: TouchEventParser) {
        currentTouches.clear()
        if (event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until event.pointerCount) {
                val pid = event.getPointerId(i)
                val px = event.getX(i)
                val py = event.getY(i)

                val (label, role) = when (pid) {
                    parser.visualPointerId -> Pair("POINTER", 1)
                    parser.visualLeftId -> Pair("LEFT CLICK", 2)
                    parser.visualRightId -> Pair("RIGHT CLICK", 3)
                    else -> Pair("FINGER", 0)
                }
                currentTouches.add(TouchIndicator(px, py, label, role))
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (touch in currentTouches) {
            val (stroke, fill) = when (touch.role) {
                1 -> Pair(pointerPaint, pointerFill)
                2 -> Pair(leftClickPaint, leftClickFill)
                3 -> Pair(rightClickPaint, rightClickFill)
                else -> Pair(pointerPaint, pointerFill)
            }

            canvas.drawCircle(touch.x, touch.y, 65f, fill)
            canvas.drawCircle(touch.x, touch.y, 65f, stroke)
            canvas.drawText(touch.label, touch.x, touch.y - 85f, textPaint)
        }
    }
}
