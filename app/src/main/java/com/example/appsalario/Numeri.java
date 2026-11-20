package com.example.appsalario;

/** Parsing numerico tollerante: accetta sia "12.50" sia "12,50" (tastiera italiana). */
public final class Numeri {
    private Numeri() {}

    public static Float toFloatOrNull(String testo) {
        if (testo == null) return null;
        String s = testo.trim().replace(" ", "").replace("€", "");
        if (s.isEmpty()) return null;
        if (s.indexOf(',') < 0 && s.matches("\\d{1,3}(\\.\\d{3})+")) {
            // "1.500" o "12.000": in euro tre decimali non esistono, quindi e' il punto delle migliaia
            s = s.replace(".", "");
        } else if (s.indexOf(',') >= 0) {
            // "1.234,56" -> "1234.56" ; "12,5" -> "12.5"
            s = s.replace(".", "").replace(',', '.');
        }
        try {
            float v = Float.parseFloat(s);
            if (Float.isNaN(v) || Float.isInfinite(v)) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
