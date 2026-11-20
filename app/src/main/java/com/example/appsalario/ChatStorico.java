package com.example.appsalario;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Cronologia della chat, salvata solo sul telefono. */
public final class ChatStorico {
    public static final String UTENTE = "utente", AI = "ai", ERRORE = "errore";
    private static final String K = "ai_chat";
    private static final int MAX = 300;
    /** Quanti messaggi recenti mandare a Gemini come contesto della conversazione. */
    private static final int CONTESTO = 20;
    private static final Gson GSON = new Gson();

    public static final class Messaggio {
        public String ruolo;
        public String testo;
        public long ts;
        /** Foto allegata (file nella memoria dell'app), o null. */
        public String immagine;
        /** Prodotto riconosciuto dall'assistente: mostra i tasti Subito/Vinted/eBay. */
        public String prodotto;

        public Messaggio(String ruolo, String testo) {
            this.ruolo = ruolo;
            this.testo = testo;
            this.ts = System.currentTimeMillis();
        }
    }

    private ChatStorico() {}

    private static String base64(String percorso) {
        try {
            java.io.File f = new java.io.File(percorso);
            if (!f.exists()) return null;
            byte[] b = new byte[(int) f.length()];
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(f));
            in.readFully(b);
            in.close();
            return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public static List<Messaggio> leggi(Context c) {
        String json = c.getSharedPreferences("SalarioPrefs", 0).getString(K, null);
        List<Messaggio> out = new ArrayList<Messaggio>();
        if (json == null) return out;
        try {
            Type t = new TypeToken<List<Messaggio>>() {}.getType();
            List<Messaggio> l = GSON.fromJson(json, t);
            if (l != null) for (Messaggio m : l) if (m != null && m.testo != null) out.add(m);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    public static synchronized void aggiungi(Context c, Messaggio m) {
        List<Messaggio> l = leggi(c);
        l.add(m);
        while (l.size() > MAX) l.remove(0);
        c.getSharedPreferences("SalarioPrefs", 0).edit().putString(K, GSON.toJson(l)).apply();
    }

    public static void cancella(Context c) {
        c.getSharedPreferences("SalarioPrefs", 0).edit().remove(K).apply();
        java.io.File[] foto = cartellaFoto(c).listFiles();
        if (foto != null) for (java.io.File f : foto) f.delete();
    }

    /** Dove vengono salvate le foto della chat (solo sul telefono). */
    public static java.io.File cartellaFoto(Context c) {
        java.io.File d = new java.io.File(c.getFilesDir(), "chat_foto");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Conversazione per Gemini: ultimi messaggi utente/AI (gli errori restano fuori). */
    public static JsonArray contesto(List<Messaggio> messaggi) {
        List<Messaggio> validi = new ArrayList<Messaggio>();
        for (Messaggio m : messaggi) if (!ERRORE.equals(m.ruolo)) validi.add(m);
        int da = Math.max(0, validi.size() - CONTESTO);
        // la conversazione deve iniziare con un messaggio dell'utente
        while (da < validi.size() && !UTENTE.equals(validi.get(da).ruolo)) da++;
        JsonArray a = new JsonArray();
        for (int i = da; i < validi.size(); i++) {
            Messaggio m = validi.get(i);
            boolean ultimo = i == validi.size() - 1;
            if (UTENTE.equals(m.ruolo) && m.immagine != null) {
                // solo la foto dell'ultimo messaggio viene inviata: le precedenti restano come segnaposto
                String b64 = ultimo ? base64(m.immagine) : null;
                if (b64 != null) {
                    a.add(Gemini.messaggioConFoto(m.testo, b64));
                    continue;
                }
                a.add(Gemini.messaggio("user", "[foto] " + m.testo));
                continue;
            }
            a.add(Gemini.messaggio(UTENTE.equals(m.ruolo) ? "user" : "model", m.testo));
        }
        return a;
    }
}
