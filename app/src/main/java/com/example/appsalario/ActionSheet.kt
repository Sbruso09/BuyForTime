package com.example.appsalario

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Menu di azioni dal basso in stile iOS (action sheet), con il tasto Annulla separato. */
object ActionSheet {

    data class Azione(val testo: String, val distruttiva: Boolean = false, val onClick: () -> Unit)

    fun mostra(context: Context, titolo: String? = null, messaggio: String? = null, azioni: List<Azione>) {
        val dialog = BottomSheetDialog(context, R.style.ThemeOverlay_App_BottomSheet)
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_azioni, null)
        dialog.setContentView(view)

        val header = view.findViewById<LinearLayout>(R.id.headerSheet)
        val tvTitolo = view.findViewById<TextView>(R.id.tvSheetTitolo)
        val tvMessaggio = view.findViewById<TextView>(R.id.tvSheetMessaggio)
        val container = view.findViewById<LinearLayout>(R.id.containerAzioni)
        val btnAnnulla = view.findViewById<TextView>(R.id.btnAnnullaSheet)

        if (titolo != null || messaggio != null) {
            header.visibility = View.VISIBLE
            titolo?.let { tvTitolo.text = it; tvTitolo.visibility = View.VISIBLE }
            messaggio?.let { tvMessaggio.text = it; tvMessaggio.visibility = View.VISIBLE }
        }

        val inflater = LayoutInflater.from(context)
        azioni.forEachIndexed { i, azione ->
            val riga = inflater.inflate(R.layout.item_azione_sheet, container, false)
            val tvAzione = riga.findViewById<TextView>(R.id.tvAzione)
            tvAzione.text = azione.testo
            tvAzione.setTextColor(ContextCompat.getColor(context, if (azione.distruttiva) R.color.danger else R.color.accent))
            // niente separatore sopra la prima azione se non c'e' l'intestazione
            riga.findViewById<View>(R.id.separatoreAzione).visibility =
                if (i == 0 && header.visibility == View.GONE) View.GONE else View.VISIBLE
            tvAzione.setOnClickListener {
                dialog.dismiss()
                azione.onClick()
            }
            container.addView(riga)
        }
        btnAnnulla.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
