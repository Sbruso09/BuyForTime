package com.example.appsalario;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;

/** Contatore delle richieste fatte oggi a Gemini e Groq (per capire quanto resta del piano gratuito). */
public final class UsoAi {
    private static final String K_GIORNO = "ai_uso_giorno", K_GEMINI = "ai_uso_gemini", K_GROQ = "ai_uso_groq";

    private UsoAi() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("SalarioPrefs", 0);
    }

    public static synchronized void aggiungi(Context c, int gemini, int groq) {
        if (gemini <= 0 && groq <= 0) return;
        SharedPreferences pr = p(c);
        String oggi = LocalDate.now().toString();
        boolean nuovo = !oggi.equals(pr.getString(K_GIORNO, ""));
        pr.edit()
                .putString(K_GIORNO, oggi)
                .putInt(K_GEMINI, (nuovo ? 0 : pr.getInt(K_GEMINI, 0)) + Math.max(0, gemini))
                .putInt(K_GROQ, (nuovo ? 0 : pr.getInt(K_GROQ, 0)) + Math.max(0, groq))
                .apply();
    }

    /** {richieste Gemini oggi, richieste Groq oggi}. */
    public static int[] oggi(Context c) {
        SharedPreferences pr = p(c);
        if (!LocalDate.now().toString().equals(pr.getString(K_GIORNO, ""))) return new int[]{0, 0};
        return new int[]{pr.getInt(K_GEMINI, 0), pr.getInt(K_GROQ, 0)};
    }
}
