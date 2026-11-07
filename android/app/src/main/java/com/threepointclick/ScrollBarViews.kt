package com.threepointclick

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Vertical Scroll Bar: Pill shape with rounded caps and a white line running down the center.
 */
class VerticalScrollBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onScrollListener: ((dy: Int) -> Unit)? = null

    private var isPressedState = false
    private var lastY = 0f
    private var accumulatedDelta = 0f
    private val scrollThresholdPx = 18f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD1E232E.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x33FFFFFF.toInt()
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = 0xCCFFFFFF.toInt()
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = w / 2f

        rect.set(1f, 1f, w - 1f, h - 1f)

        bgPaint.color = if (isPressedState) 0xEE2E384D.toInt() else 0xDD1E232E.toInt()
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        // White line running down the middle
        val lineX = w / 2f
        val lineStartY = radius + 12f
        val lineEndY = h - radius - 12f
        if (lineEndY > lineStartY) {
            canvas.drawLine(lineX, lineStartY, lineX, lineEndY, linePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
                lastY = event.y
                accumulatedDelta = 0f
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.y - lastY
                lastY = event.y
                accumulatedDelta += dy

                // Invert dy: dragging finger DOWN scrolls page DOWN (negative wheel delta)
                val steps = (-accumulatedDelta / scrollThresholdPx).toInt()
                if (steps != 0) {
                    onScrollListener?.invoke(steps)
                    accumulatedDelta -= (-steps * scrollThresholdPx)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressedState = false
                accumulatedDelta = 0f
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Horizontal Scroll Bar: Pill shape with rounded caps and a white line running along the middle.
 */
class HorizontalScrollBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onScrollListener: ((dx: Int) -> Unit)? = null

    private var isPressedState = false
    private var lastX = 0f
    private var accumulatedDelta = 0f
    private val scrollThresholdPx = 18f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD1E232E.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x33FFFFFF.toInt()
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = 0xCCFFFFFF.toInt()
    }

    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f

        rect.set(1f, 1f, w - 1f, h - 1f)

        bgPaint.color = if (isPressedState) 0xEE2E384D.toInt() else 0xDD1E232E.toInt()
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        // White line running along the middle horizontally
        val lineY = h / 2f
        val lineStartX = radius + 12f
        val lineEndX = w - radius - 12f
        if (lineEndX > lineStartX) {
            canvas.drawLine(lineStartX, lineY, lineEndX, lineY, linePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
                lastX = event.x
                accumulatedDelta = 0f
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                lastX = event.x
                accumulatedDelta += dx

                val steps = (accumulatedDelta / scrollThresholdPx).toInt()
                if (steps != 0) {
                    onScrollListener?.invoke(steps)
                    accumulatedDelta -= (steps * scrollThresholdPx)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPressedState = false
                accumulatedDelta = 0f
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * 3rd Click (Middle Click) Button: Perfectly round, no label, tactile feedback.
 */
class MiddleClickButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onMiddleClickListener: ((action: String) -> Unit)? = null

    private var isPressedState = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x44FFFFFF.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xAAFFFFFF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (Math.min(width, height) / 2f) - 2f

        bgPaint.color = if (isPressedState) 0xFF1976D2.toInt() else 0xDD1E232E.toInt()
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // Subtle center dot indicator
        dotPaint.color = if (isPressedState) Color.WHITE else 0x88FFFFFF.toInt()
        canvas.drawCircle(cx, cy, 5f, dotPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressedState = true
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onMiddleClickListener?.invoke("DOWN")
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPressedState) {
                    isPressedState = false
                    onMiddleClickListener?.invoke("UP")
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
