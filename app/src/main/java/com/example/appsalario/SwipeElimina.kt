package com.example.appsalario

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/** Scorri a sinistra una riga per eliminarla, con lo sfondo rosso "Elimina" come in iOS. */
class SwipeElimina(
    private val recyclerView: RecyclerView,
    private val onElimina: (posizione: Int) -> Unit,
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val contesto = recyclerView.context
    private val raggio = contesto.resources.displayMetrics.density * 16f
    private val testo = contesto.getString(R.string.elimina)
    private val sfondo = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(contesto, R.color.danger)
    }
    private val pennello = Paint().apply {
        isAntiAlias = true
        color = ContextCompat.getColor(contesto, R.color.white)
        @Suppress("DEPRECATION")
        textSize = contesto.resources.displayMetrics.scaledDensity * 15f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    fun collega() {
        ItemTouchHelper(this).attachToRecyclerView(recyclerView)
    }

    override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val posizione = viewHolder.bindingAdapterPosition
        if (posizione != RecyclerView.NO_POSITION) onElimina(posizione)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.45f

    override fun onChildDraw(
        c: Canvas, rv: RecyclerView, viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean,
    ) {
        val riga = viewHolder.itemView
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
            val larghezza = minOf(kotlin.math.abs(dX), riga.width.toFloat())
            val area = RectF(riga.right - larghezza, riga.top.toFloat(), riga.right.toFloat(), riga.bottom.toFloat())
            c.save()
            c.clipRect(area)
            c.drawRoundRect(
                RectF(riga.left.toFloat(), riga.top.toFloat(), riga.right.toFloat(), riga.bottom.toFloat()),
                raggio, raggio, sfondo,
            )
            if (larghezza > 64 * contesto.resources.displayMetrics.density) {
                val bordi = Rect()
                pennello.getTextBounds(testo, 0, testo.length, bordi)
                c.drawText(testo, riga.right - larghezza / 2f, riga.top + (riga.height + bordi.height()) / 2f, pennello)
            }
            c.restore()
        }
        super.onChildDraw(c, rv, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
