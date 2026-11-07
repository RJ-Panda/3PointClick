package com.threepointclick

import android.view.MotionEvent
import kotlin.math.roundToInt

enum class TouchState {
    IDLE,
    TRACKING
}

class TouchEventParser(
    private val sender: EventSender,
    var speedMultiplier: Float = 1.5f
) {
    var state: TouchState = TouchState.IDLE
        private set

    var pointerId: Int = -1
        private set

    var leftClickFingerId: Int = -1
        private set
    var rightClickFingerId: Int = -1
        private set

    val isLeftHeld: Boolean get() = leftClickFingerId != -1
    val isRightHeld: Boolean get() = rightClickFingerId != -1

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
                // First finger down becomes the pointer
                val id = event.getPointerId(0)
                pointerId = id
                visualPointerId = id
                leftClickFingerId = -1
                rightClickFingerId = -1
                visualLeftId = -1
                visualRightId = -1
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
                    // Pointer lost, promote this finger to pointer
                    pointerId = newId
                    visualPointerId = newId
                    lastPointerX = newX
                    lastPointerY = event.getY(actionIndex)
                    state = TouchState.TRACKING
                    return
                }

                val currentPointerX = event.getX(pointerIdx)

                // Check for simultaneous initial touches within 25ms: center-most finger wins as pointer
                if (state == TouchState.TRACKING && leftClickFingerId == -1 && rightClickFingerId == -1 && (event.eventTime - pointerDownTime) < 25) {
                    if (event.pointerCount >= 3) {
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

                // Dual-slot independent click assignment
                if (newX < currentPointerX) {
                    // Touch is on the LEFT of the pointer
                    if (leftClickFingerId == -1) {
                        // First touch on left: trigger Left Click DOWN
                        leftClickFingerId = newId
                        visualLeftId = newId
                        sender.sendLeftClick("DOWN")
                    }
                    // Additional finger on the left side is ignored (stays inert [FINGER])
                } else {
                    // Touch is on the RIGHT of the pointer
                    if (rightClickFingerId == -1) {
                        // First touch on right: trigger Right Click DOWN
                        rightClickFingerId = newId
                        visualRightId = newId
                        sender.sendRightClick("DOWN")
                    }
                    // Additional finger on the right side is ignored (stays inert [FINGER])
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

                if (liftedId == leftClickFingerId) {
                    // Left click finger released
                    sender.sendLeftClick("UP")
                    leftClickFingerId = -1
                    visualLeftId = -1
                } else if (liftedId == rightClickFingerId) {
                    // Right click finger released
                    sender.sendRightClick("UP")
                    rightClickFingerId = -1
                    visualRightId = -1
                } else if (liftedId == pointerId) {
                    // Pointer finger released while click finger(s) still down
                    if (leftClickFingerId != -1) {
                        sender.sendLeftClick("UP")
                    }
                    if (rightClickFingerId != -1) {
                        sender.sendRightClick("UP")
                    }
                    resetState()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // All touches released
                if (leftClickFingerId != -1) {
                    sender.sendLeftClick("UP")
                }
                if (rightClickFingerId != -1) {
                    sender.sendRightClick("UP")
                }
                resetState()
            }
        }
    }

    private fun resetState() {
        state = TouchState.IDLE
        pointerId = -1
        leftClickFingerId = -1
        rightClickFingerId = -1
        visualPointerId = -1
        visualLeftId = -1
        visualRightId = -1
    }
}
