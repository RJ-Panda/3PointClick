package com.threepointclick

import android.view.MotionEvent
import kotlin.math.roundToInt

enum class TouchState {
    IDLE,
    TRACKING,
    LEFT_HELD,
    RIGHT_HELD
}

class TouchEventParser(
    private val sender: EventSender,
    var speedMultiplier: Float = 1.5f
) {
    var state: TouchState = TouchState.IDLE
        private set

    var pointerId: Int = -1
        private set
    var clickFingerId: Int = -1
        private set

    private var lastPointerX: Float = 0f
    private var lastPointerY: Float = 0f
    private var pointerDownTime: Long = 0L

    // Public finger roles for visual feedback
    var visualPointerId: Int = -1
        private set
    var visualLeftId: Int = -1
        private set
    var visualRightId: Int = -1
        private set

    fun handleTouchEvent(event: MotionEvent) {
        val action = event.actionMasked
        val actionIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // First finger down
                val id = event.getPointerId(0)
                pointerId = id
                visualPointerId = id
                visualLeftId = -1
                visualRightId = -1
                clickFingerId = -1
                lastPointerX = event.getX(0)
                lastPointerY = event.getY(0)
                pointerDownTime = event.eventTime
                state = TouchState.TRACKING
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val newId = event.getPointerId(actionIndex)
                val newX = event.getX(actionIndex)

                val pointerIdx = event.findPointerIndex(pointerId)
                if (pointerIdx == -1) {
                    // Pointer lost, reset to this new finger
                    pointerId = newId
                    visualPointerId = newId
                    lastPointerX = newX
                    lastPointerY = event.getY(actionIndex)
                    state = TouchState.TRACKING
                    return
                }

                val currentPointerX = event.getX(pointerIdx)

                // Check for simultaneous initial touches within 25ms: center-most finger wins as pointer
                if (state == TouchState.TRACKING && clickFingerId == -1 && (event.eventTime - pointerDownTime) < 25) {
                    // If multiple touches arrived almost at once, evaluate positions
                    if (event.pointerCount >= 3) {
                        // Pick center finger among first 3
                        val touches = (0 until 3).map { idx ->
                            Triple(event.getPointerId(idx), event.getX(idx), event.getY(idx))
                        }.sortedBy { it.second }

                        val centerTouch = touches[1]
                        pointerId = centerTouch.first
                        visualPointerId = pointerId
                        lastPointerX = centerTouch.second
                        lastPointerY = centerTouch.third
                        return
                    }
                }

                when (state) {
                    TouchState.TRACKING -> {
                        clickFingerId = newId
                        if (newX < currentPointerX) {
                            // Touch is to the LEFT of pointer
                            visualLeftId = newId
                            state = TouchState.LEFT_HELD
                            sender.sendLeftClick("DOWN")
                        } else {
                            // Touch is to the RIGHT of pointer
                            visualRightId = newId
                            state = TouchState.RIGHT_HELD
                            sender.sendRightClick("DOWN")
                        }
                    }
                    TouchState.LEFT_HELD, TouchState.RIGHT_HELD -> {
                        // Third finger while in HELD state is ignored
                    }
                    TouchState.IDLE -> {}
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIdx = event.findPointerIndex(pointerId)
                if (pointerIdx != -1) {
                    val currentX = event.getX(pointerIdx)
                    val currentY = event.getY(pointerIdx)

                    val rawDx = currentX - lastPointerX
                    val rawDy = currentY - lastPointerY

                    val dx = (rawDx * speedMultiplier).roundToInt()
                    val dy = (rawDy * speedMultiplier).roundToInt()

                    if (dx != 0 || dy != 0) {
                        sender.sendMove(dx, dy)
                        lastPointerX = currentX
                        lastPointerY = currentY
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedId = event.getPointerId(actionIndex)

                if (liftedId == clickFingerId) {
                    // Click finger released
                    if (state == TouchState.LEFT_HELD) {
                        sender.sendLeftClick("UP")
                    } else if (state == TouchState.RIGHT_HELD) {
                        sender.sendRightClick("UP")
                    }
                    clickFingerId = -1
                    visualLeftId = -1
                    visualRightId = -1
                    state = TouchState.TRACKING
                } else if (liftedId == pointerId) {
                    // Pointer finger released while other finger(s) still touching
                    if (state == TouchState.LEFT_HELD) {
                        sender.sendLeftClick("UP")
                    } else if (state == TouchState.RIGHT_HELD) {
                        sender.sendRightClick("UP")
                    }
                    // Reset to idle until user restarts a clean touch gesture
                    resetState()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // All touches released
                if (state == TouchState.LEFT_HELD) {
                    sender.sendLeftClick("UP")
                } else if (state == TouchState.RIGHT_HELD) {
                    sender.sendRightClick("UP")
                }
                resetState()
            }
        }
    }

    private fun resetState() {
        state = TouchState.IDLE
        pointerId = -1
        clickFingerId = -1
        visualPointerId = -1
        visualLeftId = -1
        visualRightId = -1
    }
}
