package com.ultra.assistant.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class VoiceOrbView(context: Context) : View(context) {

    enum class State {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        OFFLINE
    }

    private var state = State.IDLE
    private var frame = 0f
    private var enabled = true

    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val particleAngles = FloatArray(24) { index ->
        index * 15f
    }

    private val particleSpeeds = FloatArray(24) { index ->
        0.35f + (index % 4) * 0.08f
    }

    private val particleSizes = FloatArray(24) { index ->
        2f + (index % 3)
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    fun setState(value: State) {
        state = value
        invalidate()
    }

    fun setOrbEnabled(value: Boolean) {
        enabled = value
        invalidate()
    }

    fun getState(): State {
        return state
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!enabled) {
            drawDisabled(canvas)
            postInvalidateDelayed(100)
            return
        }

        frame += 1f

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = minOf(width, height) * 0.15f

        val pulse = when (state) {
            State.IDLE ->
                1f + 0.015f * sin(frame * 0.04f)

            State.LISTENING ->
                1f + 0.08f * sin(frame * 0.15f)

            State.PROCESSING ->
                1f + 0.05f * sin(frame * 0.25f)

            State.SPEAKING ->
                1f + 0.10f * sin(frame * 0.20f)

            State.OFFLINE ->
                1f + 0.02f * sin(frame * 0.06f)
        }

        val radius = baseRadius * pulse

        val primary = primaryColor()
        val secondary = secondaryColor()

        drawGlow(
            canvas,
            centerX,
            centerY,
            radius,
            primary
        )

        drawParticles(
            canvas,
            centerX,
            centerY,
            radius,
            secondary
        )

        drawRings(
            canvas,
            centerX,
            centerY,
            radius,
            secondary
        )

        drawOrb(
            canvas,
            centerX,
            centerY,
            radius,
            primary,
            secondary
        )

        postInvalidateDelayed(16)
    }

    private fun drawGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int
    ) {
        val glowRadius = radius * 3.4f

        glowPaint.shader = RadialGradient(
            centerX,
            centerY,
            glowRadius,
            intArrayOf(
                withAlpha(color, 105),
                withAlpha(color, 45),
                withAlpha(color, 8),
                Color.TRANSPARENT
            ),
            floatArrayOf(
                0f,
                0.4f,
                0.75f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            centerX,
            centerY,
            glowRadius,
            glowPaint
        )

        glowPaint.shader = null
    }

    private fun drawOrb(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        primary: Int,
        secondary: Int
    ) {
        orbPaint.shader = RadialGradient(
            centerX - radius * 0.30f,
            centerY - radius * 0.35f,
            radius * 1.30f,
            intArrayOf(
                Color.WHITE,
                withAlpha(primary, 245),
                secondary,
                Color.rgb(8, 14, 28)
            ),
            floatArrayOf(
                0f,
                0.25f,
                0.68f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            orbPaint
        )

        orbPaint.shader = null

        orbPaint.color = withAlpha(
            Color.WHITE,
            105
        )

        canvas.drawCircle(
            centerX - radius * 0.30f,
            centerY - radius * 0.33f,
            radius * 0.17f,
            orbPaint
        )
    }

    private fun drawRings(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int
    ) {
        val speed = when (state) {
            State.IDLE -> 0.6f
            State.LISTENING -> 1.8f
            State.PROCESSING -> 2.5f
            State.SPEAKING -> 3.0f
            State.OFFLINE -> 0.35f
        }

        val rotation = frame * speed

        for (index in 0 until 3) {
            val ringRadius =
                radius * (1.45f + index * 0.32f)

            ringPaint.style = Paint.Style.STROKE

            ringPaint.strokeWidth =
                if (index == 0) 2.2f else 1.1f

            ringPaint.color =
                withAlpha(
                    color,
                    (180 - index * 35).coerceIn(35, 200)
                )

            canvas.drawArc(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius,
                rotation + index * 120f,
                if (index == 0) 220f else 145f,
                false,
                ringPaint
            )
        }
    }

    private fun drawParticles(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int
    ) {
        val multiplier = when (state) {
            State.IDLE -> 1.00f
            State.LISTENING -> 1.35f
            State.PROCESSING -> 1.15f
            State.SPEAKING -> 1.50f
            State.OFFLINE -> 0.90f
        }

        particlePaint.style = Paint.Style.FILL

        for (index in particleAngles.indices) {
            particleAngles[index] += particleSpeeds[index]

            val radians = Math.toRadians(
                particleAngles[index].toDouble()
            )

            val distance =
                radius *
                    (1.70f + (index % 5) * 0.18f) *
                    multiplier

            val x =
                centerX +
                    cos(radians).toFloat() *
                    distance

            val y =
                centerY +
                    sin(radians).toFloat() *
                    distance

            val pulse =
                0.75f +
                    0.35f *
                    (
                        (
                            sin(
                                (frame + index * 9f) * 0.08f
                            ) + 1f
                        ) / 2f
                    )

            particlePaint.color =
                withAlpha(
                    color,
                    (70 + index % 5 * 25).coerceIn(60, 190)
                )

            canvas.drawCircle(
                x,
                y,
                particleSizes[index] * pulse,
                particlePaint
            )
        }
    }

    private fun drawDisabled(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) * 0.15f

        orbPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                Color.rgb(65, 76, 92),
                Color.rgb(30, 39, 52),
                Color.rgb(15, 20, 29)
            ),
            floatArrayOf(
                0f,
                0.65f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            orbPaint
        )

        orbPaint.shader = null
    }

    private fun primaryColor(): Int {
        return when (state) {
            State.IDLE ->
                Color.rgb(74, 184, 255)

            State.LISTENING ->
                Color.rgb(0, 229, 255)

            State.PROCESSING ->
                Color.rgb(124, 92, 255)

            State.SPEAKING ->
                Color.rgb(0, 245, 160)

            State.OFFLINE ->
                Color.rgb(255, 179, 71)
        }
    }

    private fun secondaryColor(): Int {
        return when (state) {
            State.IDLE ->
                Color.rgb(139, 92, 255)

            State.LISTENING ->
                Color.rgb(77, 124, 255)

            State.PROCESSING ->
                Color.rgb(255, 79, 216)

            State.SPEAKING ->
                Color.rgb(0, 184, 255)

            State.OFFLINE ->
                Color.rgb(255, 107, 107)
        }
    }

    private fun withAlpha(
        color: Int,
        alpha: Int
    ): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}