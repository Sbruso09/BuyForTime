package com.example.appsalario;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

/** Formattazione leggera delle risposte: **grassetto**, elenchi "- ", titoli "#" (resi in grassetto). */
public final class TestoAi {
    private TestoAi() {}

    public static CharSequence formatta(String testo) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] righe = testo.replace("\r", "").split("\n", -1);
        for (int r = 0; r < righe.length; r++) {
            String riga = righe[r];
            boolean titolo = false;
            String t = riga.trim();
            if (t.startsWith("#")) {
                t = t.replaceFirst("^#+\\s*", "");
                titolo = true;
            } else if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) {
                t = "•  " + t.substring(2).trim();
            }
            int inizioRiga = out.length();
            // **grassetto**
            int i = 0;
            while (i < t.length()) {
                int a = t.indexOf("**", i);
                if (a < 0) {
                    out.append(t.substring(i));
                    break;
                }
                int b = t.indexOf("**", a + 2);
                if (b < 0) {
                    out.append(t.substring(i));
                    break;
                }
                out.append(t, i, a);
                int s = out.length();
                out.append(t, a + 2, b);
                out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = b + 2;
            }
            if (titolo) out.setSpan(new StyleSpan(Typeface.BOLD), inizioRiga, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (r < righe.length - 1) out.append('\n');
        }
        // niente righe vuote in fondo
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') out.delete(out.length() - 1, out.length());
        return out;
    }
}
