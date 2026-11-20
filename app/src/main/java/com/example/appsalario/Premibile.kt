package com.example.appsalario

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** Effetto "premuto" in stile iOS: la vista si rimpicciolisce un poco al tocco e torna con una molla. */
object Premibile {
    private const val SCALA_PREMUTO = 0.96f

    fun applica(vararg viste: View) {
        viste.forEach { applicaA(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun applicaA(view: View) {
        val scalaX = molla(view, SpringAnimation.SCALE_X)
        val scalaY = molla(view, SpringAnimation.SCALE_Y)
        view.setOnTouchListener { _, evento ->
            when (evento.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    scalaX.animateToFinalPosition(SCALA_PREMUTO)
                    scalaY.animateToFinalPosition(SCALA_PREMUTO)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scalaX.animateToFinalPosition(1f)
                    scalaY.animateToFinalPosition(1f)
                }
            }
            false
        }
    }

    private fun molla(view: View, proprieta: DynamicAnimation.ViewProperty) =
        SpringAnimation(view, proprieta).apply {
            spring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = 1500f
            }
        }
}
