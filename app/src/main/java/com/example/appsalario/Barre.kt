package com.example.appsalario

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Spazi per la barra di stato, la barra di navigazione in alto e la tab bar in basso (schermo a tutta altezza). */
object Barre {
    private const val ALTEZZA_NAV_BAR_DP = 44f
    private const val ALTEZZA_TAB_BAR_DP = 50.5f

    fun applica(radice: View, contenutoInAlto: View?, contenutoInBasso: View?) {
        ViewCompat.setOnApplyWindowInsetsListener(radice) { _, insets ->
            val sistema = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val densita = radice.resources.displayMetrics.density
            contenutoInAlto?.updatePadding(top = sistema.top + (ALTEZZA_NAV_BAR_DP * densita).toInt())
            contenutoInBasso?.updatePadding(bottom = sistema.bottom + (ALTEZZA_TAB_BAR_DP * densita).toInt())
            insets
        }
        ViewCompat.requestApplyInsets(radice)
    }
}
