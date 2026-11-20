package com.example.appsalario;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Saldo dei buoni pasto, separato dal saldo monetario: numero di buoni disponibili e valore di un buono.
 * Le spese pagate con i buoni scalano da qui (solo l'eventuale parte in contanti scala dal saldo).
 */
public final class SaldoBuoni {
    private static final String PREFS = "SalarioPrefs";
    private static final String K_ATTIVO = "buoni_attivo";
    private static final String K_NUMERO = "buoni_numero";
    private static final String K_VALORE = "buoni_valore";
    private static final String K_IMPORTATI = "buoni_importati";

    private SaldoBuoni() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    public static boolean attivo(Context c) {
        return prefs(c).getBoolean(K_ATTIVO, false);
    }

    public static double numero(Context c) {
        return Double.longBitsToDouble(prefs(c).getLong(K_NUMERO, Double.doubleToLongBits(0)));
    }

    /** Valore di un buono in euro (0 = non impostato). */
    public static float valore(Context c) {
        return prefs(c).getFloat(K_VALORE, 0f);
    }

    public static double valoreTotale(Context c) {
        return Math.round(numero(c) * valore(c) * 100.0) / 100.0;
    }

    public static void imposta(Context c, double numero, float valore) {
        prefs(c).edit()
                .putBoolean(K_ATTIVO, true)
                .putLong(K_NUMERO, Double.doubleToLongBits(Math.round(numero * 2.0) / 2.0))
                .putFloat(K_VALORE, valore)
                .apply();
    }

    /** Il valore del buono arriva dall'app Buoni Pasto se qui non e' ancora stato impostato. */
    public static void valoreSeManca(Context c, float valore) {
        if (valore > 0 && valore(c) <= 0) prefs(c).edit().putFloat(K_VALORE, valore).apply();
    }

    public static void usa(Context c, double buoni) {
        if (buoni <= 0) return;
        prefs(c).edit()
                .putBoolean(K_ATTIVO, true)
                .putLong(K_NUMERO, Double.doubleToLongBits(numero(c) - buoni))
                .apply();
    }

    /** Spesa pagata con i buoni eliminata: i buoni tornano disponibili. */
    public static void restituisci(Context c, double buoni) {
        if (buoni <= 0 || !attivo(c)) return;
        prefs(c).edit().putLong(K_NUMERO, Double.doubleToLongBits(numero(c) + buoni)).apply();
    }

    // ------------------------------------------------------------------ spese gia' importate

    public static boolean giaImportata(Context c, String id) {
        return id != null && prefs(c).getStringSet(K_IMPORTATI, new HashSet<String>()).contains(id);
    }

    public static void segnaImportata(Context c, String id) {
        if (id == null) return;
        Set<String> s = new HashSet<String>(prefs(c).getStringSet(K_IMPORTATI, new HashSet<String>()));
        s.add(id);
        prefs(c).edit().putStringSet(K_IMPORTATI, s).apply();
    }

    // ------------------------------------------------------------------ testi

    static String numeroTesto(double n) {
        return n == Math.rint(n) ? String.valueOf((long) n) : String.format(Locale.ITALY, "%.1f", n);
    }

    static String euro(double v) {
        return NumberFormat.getCurrencyInstance(Locale.ITALY).format(v);
    }
}
