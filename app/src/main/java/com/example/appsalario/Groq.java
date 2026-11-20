package com.example.appsalario;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Riserva quando Gemini finisce le richieste gratuite: Groq (API compatibile OpenAI).
 *  - domande normali: GPT-OSS con gli stessi strumenti dell'app (sola lettura);
 *  - ricerca dell'usato: GPT-OSS con la ricerca web integrata di Groq (browser_search);
 *  - foto: Qwen, che vede le immagini.
 */
public final class Groq {
    public static final String URL_CHAT = "https://api.groq.com/openai/v1/chat/completions";
    static final String[] MODELLI_TESTO = {"openai/gpt-oss-120b", "openai/gpt-oss-20b"};
    static final String[] MODELLI_FOTO = {"qwen/qwen3.8-27b", "qwen/qwen3.6-27b"};
    public static final int STRUMENTI = 0, RICERCA = 1, FOTO = 2;
    private static final int MAX_PASSAGGI = 8;

    public interface Trasporto {
        Gemini.Risposta post(String url, String chiave, String corpo) throws IOException;

        default void annulla() {}
    }

    public Gemini.Messaggi messaggi = new Gemini.Messaggi();
    public String erroreChiave = "Groq key not valid";
    public Gemini.Ascolto ascolto;
    public int richieste;
    private final Trasporto trasporto;
    private final String chiave;
    private volatile boolean annullata;
    private boolean opzioniRagionamento = true;

    public Groq(Trasporto trasporto, String chiave) {
        this.trasporto = trasporto;
        this.chiave = chiave;
    }

    public void annulla() {
        annullata = true;
        trasporto.annulla();
    }

    static String nomeModello(String id) {
        if (id.contains("gpt-oss-120b")) return "Groq · GPT-OSS 120B";
        if (id.contains("gpt-oss-20b")) return "Groq · GPT-OSS 20B";
        if (id.startsWith("qwen/")) return "Groq · Qwen";
        return "Groq";
    }

    // ------------------------------------------------------------------ conversione da formato Gemini

    /** Storia Gemini (user/model, parts) -> messages OpenAI. Le foto diventano image_url solo in modalita' FOTO. */
    static JsonArray messaggiDa(String istruzioni, JsonArray storia, boolean conFoto) {
        JsonArray out = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", istruzioni);
        out.add(sys);
        for (JsonElement el : storia) {
            JsonObject m = el.getAsJsonObject();
            String ruolo = "model".equals(m.get("role").getAsString()) ? "assistant" : "user";
            StringBuilder testo = new StringBuilder();
            String foto = null;
            if (m.has("parts")) {
                for (JsonElement p : m.getAsJsonArray("parts")) {
                    JsonObject part = p.getAsJsonObject();
                    if (part.has("text")) testo.append(part.get("text").getAsString());
                    if (part.has("inlineData")) foto = part.getAsJsonObject("inlineData").get("data").getAsString();
                }
            }
            JsonObject o = new JsonObject();
            o.addProperty("role", ruolo);
            if (foto != null && conFoto && "user".equals(ruolo)) {
                JsonArray c = new JsonArray();
                JsonObject t = new JsonObject();
                t.addProperty("type", "text");
                t.addProperty("text", testo.toString());
                c.add(t);
                JsonObject img = new JsonObject();
                img.addProperty("type", "image_url");
                JsonObject u = new JsonObject();
                u.addProperty("url", "data:image/jpeg;base64," + foto);
                img.add("image_url", u);
                c.add(img);
                o.add("content", c);
            } else {
                o.addProperty("content", (foto != null ? "[foto] " : "") + testo);
            }
            out.add(o);
        }
        return out;
    }

    /** Dichiarazioni Gemini (tipi "OBJECT", "STRING"...) -> tools OpenAI (tipi in minuscolo). */
    static JsonArray strumentiDa(JsonArray dichiarazioni) {
        JsonArray out = new JsonArray();
        if (dichiarazioni == null) return out;
        for (JsonElement el : dichiarazioni) {
            JsonObject d = el.getAsJsonObject().deepCopy();
            if (d.has("parameters")) minuscole(d.get("parameters"));
            JsonObject t = new JsonObject();
            t.addProperty("type", "function");
            t.add("function", d);
            out.add(t);
        }
        return out;
    }

    private static void minuscole(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (String k : o.keySet()) {
                JsonElement v = o.get(k);
                if ("type".equals(k) && v.isJsonPrimitive()) o.addProperty(k, v.getAsString().toLowerCase(java.util.Locale.ROOT));
                else minuscole(v);
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) minuscole(x);
        }
    }

    static String senzaPensieri(String s) {
        if (s == null) return "";
        return s.replaceAll("(?s)<think>.*?</think>", "").replaceAll("(?s)^.*?</think>", "").trim();
    }

    // ------------------------------------------------------------------ conversazione

    public String conversa(String istruzioni, JsonArray storia, JsonArray dichiarazioni, Gemini.Esecutore esecutore, int modo)
            throws Gemini.Errore {
        String[] modelli = modo == FOTO ? MODELLI_FOTO : MODELLI_TESTO;
        JsonArray messaggiChat = messaggiDa(istruzioni, storia, modo == FOTO);
        JsonArray tools;
        if (modo == RICERCA) {
            tools = new JsonArray();
            JsonObject b = new JsonObject();
            b.addProperty("type", "browser_search");
            tools.add(b);
        } else if (modo == STRUMENTI) {
            tools = strumentiDa(dichiarazioni);
        } else {
            tools = new JsonArray();
        }
        int modello = 0, tentativi = 0;
        final long inizio = System.currentTimeMillis();
        if (ascolto != null) ascolto.stato(Gemini.RIPROVO, nomeModello(modelli[0]));
        for (int passo = 0; passo < MAX_PASSAGGI; ) {
            if (annullata) throw new Gemini.Errore(Gemini.Errore.ANNULLATA, "");
            if (System.currentTimeMillis() - inizio > Gemini.LIMITE_MS) {
                throw new Gemini.Errore(Gemini.Errore.RETE, messaggi.servizio + " (timeout)");
            }
            JsonObject corpo = new JsonObject();
            corpo.addProperty("model", modelli[modello]);
            corpo.add("messages", messaggiChat);
            if (tools.size() > 0) corpo.add("tools", tools);
            if (opzioniRagionamento) {
                if (modelli[modello].startsWith("openai/gpt-oss")) corpo.addProperty("reasoning_effort", "low");
                else corpo.addProperty("reasoning_format", "hidden");
            }
            Gemini.Risposta r;
            try {
                richieste++;
                r = trasporto.post(URL_CHAT, chiave, corpo.toString());
            } catch (IOException e) {
                if (annullata) throw new Gemini.Errore(Gemini.Errore.ANNULLATA, "");
                if (++tentativi <= 2) continue;
                throw new Gemini.Errore(Gemini.Errore.RETE, messaggi.rete + " (Groq, " + e.getClass().getSimpleName() + ")");
            }
            String msg = messaggioErrore(r.corpo);
            if (r.codice == 400 && opzioniRagionamento && msg.toLowerCase().contains("reasoning")) {
                opzioniRagionamento = false; // il modello non accetta l'opzione: riprovo senza
                continue;
            }
            if ((r.codice == 404 || (r.codice == 400 && msg.toLowerCase().contains("model"))) && modello < modelli.length - 1) {
                modello++;
                continue;
            }
            if (r.codice == 429 && modello < modelli.length - 1) {
                modello++;
                continue;
            }
            if (r.codice >= 500 && ++tentativi <= 3) {
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            if (r.codice == 401 || r.codice == 403) throw new Gemini.Errore(Gemini.Errore.CHIAVE, erroreChiave);
            if (r.codice == 429) throw new Gemini.Errore(Gemini.Errore.QUOTA, messaggi.quota + " (Groq)" + (msg.isEmpty() ? "" : "\n\n" + msg));
            if (r.codice != 200) {
                throw new Gemini.Errore(Gemini.Errore.ALTRO, "Groq (" + r.codice + ")" + (msg.isEmpty() ? "" : ": " + msg));
            }
            tentativi = 0;

            JsonObject json = JsonParser.parseString(r.corpo).getAsJsonObject();
            JsonArray scelte = json.has("choices") ? json.getAsJsonArray("choices") : null;
            if (scelte == null || scelte.size() == 0) return messaggi.vuota;
            JsonObject m = scelte.get(0).getAsJsonObject().getAsJsonObject("message");
            JsonArray chiamate = m.has("tool_calls") && m.get("tool_calls").isJsonArray() ? m.getAsJsonArray("tool_calls") : null;
            if (chiamate == null || chiamate.size() == 0) {
                String t = m.has("content") && !m.get("content").isJsonNull() ? senzaPensieri(m.get("content").getAsString()) : "";
                return t.isEmpty() ? messaggi.nessuna : t;
            }
            // il turno dell'assistente torna nella conversazione (senza i campi solo di Groq)
            JsonObject turno = new JsonObject();
            turno.addProperty("role", "assistant");
            if (m.has("content") && !m.get("content").isJsonNull()) turno.add("content", m.get("content"));
            turno.add("tool_calls", chiamate);
            messaggiChat.add(turno);
            StringBuilder nomi = new StringBuilder();
            for (JsonElement el : chiamate) {
                JsonObject c = el.getAsJsonObject();
                JsonObject f = c.getAsJsonObject("function");
                String nome = f.get("name").getAsString();
                JsonObject args;
                try {
                    String a = f.has("arguments") && !f.get("arguments").isJsonNull() ? f.get("arguments").getAsString() : "{}";
                    JsonElement p = JsonParser.parseString(a.isEmpty() ? "{}" : a);
                    args = p.isJsonObject() ? p.getAsJsonObject() : new JsonObject();
                } catch (RuntimeException e) {
                    args = new JsonObject();
                }
                JsonObject risultato;
                try {
                    risultato = esecutore != null ? esecutore.esegui(nome, args) : new JsonObject();
                } catch (RuntimeException e) {
                    risultato = new JsonObject();
                    risultato.addProperty("errore", "Strumento non riuscito: " + e.getMessage());
                }
                JsonObject tm = new JsonObject();
                tm.addProperty("role", "tool");
                tm.addProperty("tool_call_id", c.get("id").getAsString());
                tm.addProperty("content", risultato.toString());
                messaggiChat.add(tm);
                if (nomi.length() > 0) nomi.append(", ");
                nomi.append(nome);
            }
            if (ascolto != null) ascolto.stato(Gemini.STRUMENTI, nomi.toString());
            passo++;
        }
        return messaggi.passaggi;
    }

    private static String messaggioErrore(String corpo) {
        try {
            JsonObject e = JsonParser.parseString(corpo).getAsJsonObject().getAsJsonObject("error");
            return e.get("message").getAsString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    /** Trasporto reale. */
    public static final class Http implements Trasporto {
        private volatile HttpURLConnection corrente;

        @Override
        public void annulla() {
            HttpURLConnection c = corrente;
            if (c != null) {
                try {
                    c.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
        }

        @Override
        public Gemini.Risposta post(String url, String chiave, String corpo) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            corrente = c;
            try {
                c.setRequestMethod("POST");
                c.setConnectTimeout(15_000);
                c.setReadTimeout(60_000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setRequestProperty("Authorization", "Bearer " + chiave);
                OutputStream os = c.getOutputStream();
                os.write(corpo.getBytes(StandardCharsets.UTF_8));
                os.close();
                int codice = c.getResponseCode();
                InputStream is = codice >= 400 ? c.getErrorStream() : c.getInputStream();
                String risposta = "";
                if (is != null) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
                    is.close();
                    risposta = new String(b.toByteArray(), StandardCharsets.UTF_8);
                }
                return new Gemini.Risposta(codice, risposta);
            } finally {
                corrente = null;
                c.disconnect();
            }
        }
    }
}
