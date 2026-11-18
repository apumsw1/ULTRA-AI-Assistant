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
    private var animationTime = 0f
    private var enabled = true

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val particles = Array(28) { Particle() }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        particles.forEachIndexed { index, particle ->
            particle.angle = index * (360f / particles.size)
            particle.radius = 70f + (index % 7) * 15f
            particle.speed = 0.25f + (index % 5) * 0.08f
            particle.size = 2f + (index % 4)
        }
    }

    fun setState(newState: State) {
        state = newState
        invalidate()
    }

    fun setOrbEnabled(value: Boolean) {
        enabled = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!enabled) {
            drawDisabled(canvas)
            return
        }

        animationTime += 1f

        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = minOf(width, height) * 0.16f

        val pulse = when (state) {
            State.LISTENING -> 1f + 0.08f * sin(animationTime * 0.15f)
            State.PROCESSING -> 1f + 0.05f * sin(animationTime * 0.25f)
            State.SPEAKING -> 1f + 0.11f * sin(animationTime * 0.20f)
            State.OFFLINE -> 1f + 0.025f * sin(animationTime * 0.08f)
            State.IDLE -> 1f + 0.018f * sin(animationTime * 0.05f)
        }

        val radius = baseRadius * pulse

        val colors = stateColors()

        drawAmbientGlow(canvas, centerX, centerY, radius, colors[0])
        drawFloatingParticles(canvas, centerX, centerY, radius, colors[1])
        drawOuterRings(canvas, centerX, centerY, radius, colors[1])
        drawOrb(canvas, centerX, centerY, radius, colors[0], colors[1])

        postInvalidateDelayed(16)
    }

    private fun drawAmbientGlow(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int
    ) {
        val glowRadius = radius * 3.5f

        glowPaint.shader = RadialGradient(
            x,
            y,
            glowRadius,
            intArrayOf(
                withAlpha(color, 100),
                withAlpha(color, 42),
                withAlpha(color, 10),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.38f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(x, y, glowRadius, glowPaint)
        glowPaint.shader = null
    }

    private fun drawOrb(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        primary: Int,
        secondary: Int
    ) {
        corePaint.shader = RadialGradient(
            x - radius * 0.28f,
            y - radius * 0.32f,
            radius * 1.2f,
            intArrayOf(
                Color.WHITE,
                withAlpha(primary, 235),
                secondary,
                withAlpha(Color.BLACK, 220)
            ),
            floatArrayOf(0f, 0.25f, 0.68f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(x, y, radius, corePaint)
        corePaint.shader = null

        centerPaint.style = Paint.Style.FILL
        centerPaint.color = withAlpha(Color.WHITE, 90)
        canvas.drawCircle(
            x - radius * 0.30f,
            y - radius * 0.32f,
            radius * 0.18f,
            centerPaint
        )

        centerPaint.color = withAlpha(primary, 130)
        canvas.drawCircle(
            x,
            y,
            radius * 0.58f,
            centerPaint
        )
    }

    private fun drawOuterRings(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int
    ) {
        val rotation = animationTime * ringSpeed()

        for (index in 0 until 3) {
            val ringRadius = radius * (1.45f + index * 0.32f)

            ringPaint.style = Paint.Style.STROKE
            ringPaint.strokeWidth = if (index == 0) 2.4f else 1.2f
            ringPaint.color = withAlpha(color, ringAlpha(index))

            val rectLeft = x - ringRadius
            val rectTop = y - ringRadius
            val rectRight = x + ringRadius
            val rectBottom = y + ringRadius

            val start = rotation + index * 120f

            canvas.drawArc(
                rectLeft,
                rectTop,
                rectRight,
                rectBottom,
                start,
                if (index == 0) 230f else 150f,
                false,
                ringPaint
            )
        }
    }

    private fun drawFloatingParticles(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int
    ) {
        particles.forEachIndexed { index, particle ->

            val direction = if (index % 2 == 0) 1f else -1f

            particle.angle += particle.speed * direction

            val radians = Math.toRadians(particle.angle.toDouble())

            val stateMultiplier = when (state) {
                State.LISTENING -> 1.35f
                State.PROCESSING -> 1.15f
                State.SPEAKING -> 1.5f
                State.OFFLINE -> 0.92f
                State.IDLE -> 1f
            }

            val distance =
                radius * (1.65f + (index % 6) * 0.20f) * stateMultiplier

            val px = x + cos(radians).toFloat() * distance
            val py = y + sin(radians).toFloat() * distance

            val alphaWave =
                95 + ((sin((animationTime + index * 12f) * 0.07f) + 1f) * 65f).toInt()

            particlePaint.style = Paint.Style.FILL
            particlePaint.color = withAlpha(
                color,
                alphaWave.coerceIn(55, 220)
            )

            canvas.drawCircle(
                px,
                py,
                particle.size * particlePulse(index),
                particlePaint
            )
        }
    }

    private fun drawDisabled(canvas: Canvas) {
        val x = width / 2f
        val y = height / 2f
        val radius = minOf(width, height) * 0.16f

        corePaint.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(
                0xFF394454.toInt(),
                0xFF202936.toInt(),
                0xFF111722.toInt()
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(x, y, radius, corePaint)
        corePaint.shader = null

        postInvalidateDelayed(120)
    }

    private fun stateColors(): IntArray {
        return when (state) {
            State.IDLE -> intArrayOf(
                0xFF4AB8FF.toInt(),
                0xFF8B5CFF.toInt()
            )

            State.LISTENING -> intArrayOf(
                0xFF00E5FF.toInt(),
                0xFF4D7CFF.toInt()
            )

            State.PROCESSING -> intArrayOf(
                0xFF7C5CFF.toInt(),
                0xFFFF4FD8.toInt()
            )

            State.SPEAKING -> intArrayOf(
                0xFF00F5A0.toInt(),
                0xFF00B8FF.toInt()
            )

            State.OFFLINE -> intArrayOf(
                0xFFFFB347.toInt(),
                0xFFFF6B6B.toInt()
            )
        }
    }

    private fun ringSpeed(): Float {
        return when (state) {
            State.LISTENING -> 1.8f
            State.PROCESSING -> 2.6f
            State.SPEAKING -> 3.1f
            State.OFFLINE -> 0.45f
            State.IDLE -> 0.7f
        }
    }

    private fun ringAlpha(index: Int): Int {
        return when (state) {
            State.LISTENING -> 190 - index * 35
            State.PROCESSING -> 175 - index * 30
            State.SPEAKING -> 215 - index * 35
            State.OFFLINE -> 105 - index * 20
            State.IDLE -> 115 - index * 25
        }.coerceIn(25, 220)
    }

    private fun particlePulse(index: Int): Float {
        return when (state) {
            State.LISTENING ->
                1.0f + 0.45f * ((sin((animationTime + index * 10f) * 0.13f) + 1f) / 2f)

            State.PROCESSING ->
                0.9f + 0.35f * ((sin((animationTime + index * 8f) * 0.18f) + 1f) / 2f)

            State.SPEAKING ->
                1.0f + 0.65f * ((sin((animationTime + index * 7f) * 0.16f) + 1f) / 2f)

            State.OFFLINE ->
                0.85f + 0.15f * ((sin((animationTime + index) * 0.04f) + 1f) / 2f)

            State.IDLE ->
                0.8f + 0.2f * ((sin((animationTime + index) * 0.05f) + 1f) / 2f)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private class Particle {
        var angle = 0f
        var radius = 100f
        var speed = 0.5f
        var size = 3f
    }
}