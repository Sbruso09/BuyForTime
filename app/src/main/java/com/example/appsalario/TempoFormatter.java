package com.example.appsalario;

import android.content.Context;
import android.content.res.Resources;

/**
 * Formatta le ore di lavoro. "giorni" = giornate lavorative da {@code oreGiorno} ore.
 *
 * Correzione: prima le ore al giorno venivano arrotondate all'intero (7,5 h -> 8 h),
 * quindi con orari non interi il conto dei giorni era sbagliato. Ora si lavora in minuti.
 */
public final class TempoFormatter {
    public static final TempoFormatter INSTANCE = new TempoFormatter();

    private TempoFormatter() {}

    public String formatta(Context context, float ore, float oreGiorno) {
        Resources res = context.getResources();
        if (!(ore > 0.0f)) return res.getQuantityString(R.plurals.ore, 0, 0);

        long secondi = Math.round(ore * 3600.0);
        if (secondi < 3600L) {
            return componi(context, (int) (secondi / 60L), R.plurals.minuti, (int) (secondi % 60L), R.plurals.secondi);
        }
        long minuti = Math.round(ore * 60.0);
        long minutiGiorno = Math.max(1L, Math.round(oreGiorno * 60.0));
        if (minuti < minutiGiorno) {
            return componi(context, (int) (minuti / 60L), R.plurals.ore, (int) (minuti % 60L), R.plurals.minuti);
        }
        long giorni = minuti / minutiGiorno;
        long oreResto = Math.round((minuti % minutiGiorno) / 60.0);
        if (oreResto * 60L >= minutiGiorno) { // l'arrotondamento ha completato la giornata
            giorni++;
            oreResto = 0;
        }
        return componi(context, (int) giorni, R.plurals.giorni, (int) oreResto, R.plurals.ore);
    }

    private String componi(Context context, int maggiore, int pluralMaggiore, int minore, int pluralMinore) {
        Resources res = context.getResources();
        String a = res.getQuantityString(pluralMaggiore, maggiore, maggiore);
        String b = res.getQuantityString(pluralMinore, minore, minore);
        if (maggiore == 0) return b;
        if (minore == 0) return a;
        return context.getString(R.string.tempo_composto, a, b);
    }
}
