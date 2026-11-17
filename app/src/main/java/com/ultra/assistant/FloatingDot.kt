package com.ultra.assistant

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class FloatingDot(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var dot: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun show() {
        if (dot != null) return
        val v = TextView(context).apply {
            text = "U"; textSize = 19f; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xEE087BFF.toInt())
                setStroke(3, 0xFF8AD7FF.toInt())
            }
            elevation = 16f
        }
        val p = WindowManager.LayoutParams(56, 56, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END; x = 18; y = 230
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; startX=p.x; startY=p.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=(e.rawX-downX).toInt(); val dy=(e.rawY-downY).toInt()
                    if (abs(dx)+abs(dy)>8) moved=true
                    p.x=startX-dx; p.y=startY+dy
                    try { wm.updateViewLayout(v,p) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                else -> true
            }
        }
        try { wm.addView(v,p); dot=v; params=p } catch (_: Exception) {}
    }

    fun hide() {
        dot?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        dot=null; params=null
    }
}
