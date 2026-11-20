package com.example.appsalario;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.text.DateFormatSymbols;
import java.util.Locale;

/**
 * Lingua dell'app: Automatica (lingua del telefono), Italiano, English.
 * Usa le lingue per-app di AppCompat (salvate da sole; su Android 13+ e' la funzione di sistema).
 * Il testo di base delle risorse e' italiano; values-en contiene l'inglese.
 */
public final class Lingua {
    /** "" = automatica. */
    public static final String[] CODICI = {"", "it", "en"};

    private Lingua() {}

    public static String scelta() {
        LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
        return l.isEmpty() ? "" : l.get(0).getLanguage();
    }

    public static void imposta(String codice) {
        AppCompatDelegate.setApplicationLocales(codice == null || codice.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList() : LocaleListCompat.forLanguageTags(codice));
    }

    /** Lingua effettiva dei testi: inglese se scelto (o se il telefono e' in inglese in automatico), altrimenti italiano. */
    public static boolean inglese() {
        LocaleListCompat l = AppCompatDelegate.getApplicationLocales();
        Locale loc = l.isEmpty() ? Locale.getDefault() : l.get(0);
        return loc != null && "en".equals(loc.getLanguage());
    }

    /** Locale per date e mesi (la valuta resta nel formato italiano in euro). */
    public static Locale locale() {
        return inglese() ? Locale.UK : Locale.ITALY;
    }

    /**
     * Contesto con la lingua scelta: serve dove si usa il contesto dell'applicazione
     * (notifiche, assistente in background), che su Android < 13 non segue la lingua per-app.
     */
    public static Context contesto(Context c) {
        Configuration conf = new Configuration(c.getResources().getConfiguration());
        conf.setLocale(inglese() ? Locale.ENGLISH : Locale.ITALIAN);
        return c.createConfigurationContext(conf);
    }

    public static String[] mesiLunghi() {
        String[] m = new DateFormatSymbols(locale()).getMonths();
        String[] out = new String[12];
        System.arraycopy(m, 0, out, 0, 12);
        return out;
    }

    public static String[] mesiCorti() {
        String[] m = new DateFormatSymbols(locale()).getShortMonths();
        String[] out = new String[12];
        for (int i = 0; i < 12; i++) out[i] = m[i].replace(".", "");
        return out;
    }

    /** Nome della lingua per il selettore, nella lingua stessa. */
    public static String nome(Context c, String codice) {
        if ("it".equals(codice)) return "Italiano";
        if ("en".equals(codice)) return "English";
        return c.getString(R.string.lingua_automatica);
    }
}
