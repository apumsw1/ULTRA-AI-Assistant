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
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val particles = Array(30) { Particle() }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        particles.forEachIndexed { index, particle ->
            particle.angle = index * (360f / particles.size)
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

    fun getState(): State {
        return state
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
        val baseRadius = minOf(width, height) * 0.15f

        val pulse = when (state) {
            State.LISTENING ->
                1f + 0.08f * sin(animationTime * 0.15f)

            State.PROCESSING ->
                1f + 0.05f * sin(animationTime * 0.25f)

            State.SPEAKING ->
                1f + 0.11f * sin(animationTime * 0.20f)

            State.OFFLINE ->
                1f + 0.025f * sin(animationTime * 0.08f)

            State.IDLE ->
                1f + 0.018f * sin(animationTime * 0.05f)
        }

        val radius = baseRadius * pulse
        val colors = stateColors()

        drawAmbientGlow(
            canvas,
            centerX,
            centerY,
            radius,
            colors[0]
        )

        drawFloatingParticles(
            canvas,
            centerX,
            centerY,
            radius,
            colors[1]
        )

        drawOuterRings(
            canvas,
            centerX,
            centerY,
            radius,
            colors[1]
        )

        drawOrb(
            canvas,
            centerX,
            centerY,
            radius,
            colors[0],
            colors[1]
        )

        postInvalidateDelayed(16)
    }

    private fun drawAmbientGlow(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int
    ) {
        val glowRadius = radius * 3.6f

        glowPaint.shader = RadialGradient(
            x,
            y,
            glowRadius,
            intArrayOf(
                withAlpha(color, 105),
                withAlpha(color, 48),
                withAlpha(color, 12),
                Color.TRANSPARENT
            ),
            floatArrayOf(
                0f,
                0.38f,
                0.72f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            x,
            y,
            glowRadius,
            glowPaint
        )

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
            radius * 1.25f,
            intArrayOf(
                Color.WHITE,
                withAlpha(primary, 245),
                secondary,
                withAlpha(Color.BLACK, 230)
            ),
            floatArrayOf(
                0f,
                0.24f,
                0.68f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(
            x,
            y,
            radius,
            corePaint
        )

        corePaint.shader = null

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = withAlpha(
            Color.WHITE,
            105
        )

        canvas.drawCircle(
            x - radius * 0.30f,
            y - radius * 0.33f,
            radius * 0.17f,
            highlightPaint
        )

        highlightPaint.color = withAlpha(
            primary,
            125
        )

        canvas.drawCircle(
            x,
            y,
            radius * 0.56f,
            highlightPaint
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

            val ringRadius =
                radius * (1.45f + index * 0.32f)

            ringPaint.style = Paint.Style.STROKE

            ringPaint.strokeWidth =
                if (index == 0) 2.3f else 1.15f

            ringPaint.color =
                withAlpha(
                    color,
                    ringAlpha(index)
                )

            val left = x - ringRadius
            val top = y - ringRadius
            val right = x + ringRadius
            val bottom = y + ringRadius

            val startAngle =
                rotation + index * 120f

            canvas.drawArc(
                left,
                top,
                right,
                bottom,
                startAngle,
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

            val