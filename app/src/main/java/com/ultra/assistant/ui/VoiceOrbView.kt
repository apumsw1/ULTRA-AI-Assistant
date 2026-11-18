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
            State.LISTENING ->
                1f + 0.08f * sin(frame * 0.15f)

            State.PROCESSING ->
                1f + 0.05f * sin(frame * 0.25f)

            State.SPEAKING ->
                1f + 0.10f * sin(frame * 0.20f)

            State.OFFLINE ->
                1f + 0.02f * sin(frame * 0.06