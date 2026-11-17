package com.ultra.assistant.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*

object UiKit {
    fun text(c:Context,s:String,size:Float=15f,color:Int=0xFFEAF4FF.toInt(),bold:Boolean=false)=TextView(c).apply{ text=s;textSize=size;setTextColor(color);if(bold)setTypeface(null, android.graphics.Typeface.BOLD);setPadding(0,7,0,7) }
    fun button(c:Context,s:String,onClick:()->Unit)=Button(c).apply{text=s;isAllCaps=false;textSize=14f;setOnClickListener{onClick()};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=5;bottomMargin=5}}
    fun card(c:Context):LinearLayout=LinearLayout(c).apply{orientation=LinearLayout.VERTICAL;setPadding(16,13,16,13);background=GradientDrawable().apply{cornerRadius=22f;setColor(0xFF10243B.toInt());setStroke(1,0xFF234766.toInt())};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=12}}
    fun gap(c:Context,h:Int=8)=View(c).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
}
