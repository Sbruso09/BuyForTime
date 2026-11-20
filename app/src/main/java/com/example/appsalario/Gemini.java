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
 * Client minimale per la Gemini API (models.generateContent) con chiamata a funzioni.
 *
 * Ciclo: invio la conversazione + le dichiarazioni degli strumenti; se il modello chiede di
 * chiamare funzioni, le eseguo in locale (sola lettura) e rimando i risultati, finche' non
 * arriva una risposta testuale.
 *
 * Importante (Gemini 3+): il turno del modello va rimandato IDENTICO a come e' arrivato,
 * perche' contiene la "thoughtSignature" richiesta nelle chiamate a funzione successive.
 * Per questo aggiungo alla storia l'oggetto "content" ricevuto, senza ricostruirlo.
 */
public final class Gemini {

    public static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** Modelli gratuiti, in ordine di preferenza (se uno non e' disponibile si passa al successivo). */
    /** "Piu' intelligente": Flash (circa 20 richieste/giorno gratis), poi i Lite quando finisce. */
    public static final String[] MODELLI_INTELLIGENTE = {"gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"};
    /** "Veloce": Flash-Lite (circa 500 richieste/giorno gratis). */
    public static final String[] MODELLI_VELOCE = {"gemini-3.5-flash-lite", "gemini-3.1-flash-lite"};
    public static final String[] MODELLI = MODELLI_VELOCE;
    private static final int MAX_PASSAGGI = 8;

    public static final class Risposta {
        public final int codice;
        public final String corpo;

        public Risposta(int codice, String corpo) {
            this.codice = codice;
            this.corpo = corpo;
        }
    }

    /** Riceve gli eventi di una risposta in streaming (uno per riga "data:" dell'SSE). */
    public interface Ricevitore {
        void evento(JsonObject pezzo);
    }

    public interface Trasporto {
        /** POST in streaming: se il codice e' 200 passa ogni evento al ricevitore, altrimenti restituisce il corpo d'errore. */
        Risposta stream(String url, String chiave, String corpo, Ricevitore r) throws IOException;

        /** Interrompe la richiesta in corso (se possibile). */
        default void annulla() {}
    }

    /** Aggiornamenti per l'interfaccia mentre arriva la risposta. */
    public interface Ascolto {
        /** Testo scritto finora nella risposta (stringa vuota = ricomincia, es. dopo una chiamata a strumento). */
        void testo(String finora);

        /** Fase in corso: INVIO (con il nome del modello), STRUMENTI (con i nomi), RIPROVO (con il modello). */
        void stato(int fase, String dettaglio);
    }

    public static final int INVIO = 1, STRUMENTI = 2, RIPROVO = 3;
    /** Tempo massimo per una domanda, tentativi compresi. */
    static long LIMITE_MS = 120_000;

    public interface Esecutore {
        /** Esegue uno strumento e restituisce un oggetto JSON con il risultato. */
        JsonObject esegui(String nome, JsonObject argomenti);
    }

    public static final class Errore extends Exception {
        public static final int CHIAVE = 1, QUOTA = 2, RETE = 3, ALTRO = 4, ANNULLATA = 5;
        public final int tipo;

        public Errore(int tipo, String messaggio) {
            super(messaggio);
            this.tipo = tipo;
        }
    }

    /** Messaggi mostrati all'utente; sostituibili con quelli tradotti. */
    public static final class Messaggi {
        public String rete = "Connessione non riuscita: controlla internet e riprova.";
        public String vuota = "Non riesco a rispondere a questa domanda. Prova a riformularla.";
        public String nessuna = "Non ho una risposta per questa domanda.";
        public String passaggi = "La domanda richiede troppi passaggi: prova a dividerla in domande più semplici.";
        public String chiave = "La chiave Gemini non è valida. Controllala nelle Impostazioni.";
        public String quota = "Hai raggiunto il limite gratuito di Gemini per ora. Riprova più tardi.";
        public String servizio = "Gemini non risponde in questo momento. Riprova tra poco.";
        public String errore = "Errore Gemini";
    }

    public Messaggi messaggi = new Messaggi();
    private final Trasporto trasporto;
    private final String chiave;
    public Ascolto ascolto;
    /** Ricerca Google al posto degli strumenti dell'app (per i prezzi dell'usato). */
    public boolean ricercaWeb;
    /** Fonti della ricerca web: {titolo, link}. */
    public final java.util.List<String[]> fonti = new java.util.ArrayList<String[]>();
    private volatile boolean annullata;

    /** Ferma la domanda in corso: chiude la connessione e fa terminare conversa() con un errore ANNULLATA. */
    public void annulla() {
        annullata = true;
        trasporto.annulla();
    }

    private void stato(int fase, String dettaglio) {
        if (ascolto != null) ascolto.stato(fase, dettaglio);
    }

    private static String nomeModello(String id) {
        // "gemini-3.6-flash" -> "Gemini 3.6 Flash"
        StringBuilder b = new StringBuilder();
        for (String p : id.split("-")) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }
    /** Ricordati tra una domanda e l'altra: evita di riprovare ogni volta modelli non disponibili. */
    private String[] modelli = MODELLI_VELOCE;
    /** Richieste HTTP fatte (per il contatore giornaliero). */
    public int richieste;

    public void usaModelli(String[] m) {
        if (m != null && m.length > 0) modelli = m;
    }
    private static volatile boolean pensieroBasso = true;

    public Gemini(Trasporto trasporto, String chiave) {
        this.trasporto = trasporto;
        this.chiave = chiave;
    }

    public String modello() {
        return modelli[0];
    }

    /**
     * @param storia contents della conversazione, gia' con l'ultimo messaggio dell'utente;
     *               viene ARRICCHITA con i turni del modello e i risultati degli strumenti.
     * @return il testo finale del modello
     */
    public String conversa(String istruzioni, JsonArray storia, JsonArray dichiarazioni, Esecutore esecutore) throws Errore {
        int modello = 0;
        int tentativi = 0;
        final long inizio = System.currentTimeMillis();
        int ultimoModello = -1;
        for (int passo = 0; passo < MAX_PASSAGGI; ) {
            if (annullata) throw new Errore(Errore.ANNULLATA, "");
            if (System.currentTimeMillis() - inizio > LIMITE_MS) throw new Errore(Errore.RETE, messaggi.servizio + " (timeout)");
            if (ultimoModello != -1 && ultimoModello != modello) stato(RIPROVO, nomeModello(modelli[modello]));
            else if (passo == 0 && tentativi == 0) stato(INVIO, nomeModello(modelli[modello]));
            ultimoModello = modello;
            JsonObject corpo = new JsonObject();
            JsonObject sys = new JsonObject();
            sys.add("parts", parti(istruzioni));
            corpo.add("systemInstruction", sys);
            corpo.add("contents", storia);
            if (ricercaWeb) {
                JsonArray tools = new JsonArray();
                JsonObject t = new JsonObject();
                t.add("google_search", new JsonObject());
                tools.add(t);
                corpo.add("tools", tools);
            } else if (dichiarazioni != null && dichiarazioni.size() > 0) {
                JsonArray tools = new JsonArray();
                JsonObject t = new JsonObject();
                t.add("functionDeclarations", dichiarazioni);
                tools.add(t);
                corpo.add("tools", tools);
            }
            JsonObject gen = new JsonObject();
            if (pensieroBasso) {
                // Gemini 3 ragiona a livello "medio" di default: per questa chat basta "basso", molto piu' veloce
                JsonObject th = new JsonObject();
                th.addProperty("thinkingLevel", "low");
                gen.add("thinkingConfig", th);
            }
            corpo.add("generationConfig", gen);

            final java.util.List<JsonObject> pezzi = new java.util.ArrayList<>();
            final StringBuilder scritto = new StringBuilder();
            final boolean[] haStrumenti = {false};
            Risposta r;
            try {
                richieste++;
                r = trasporto.stream(BASE + modelli[modello] + ":streamGenerateContent?alt=sse", chiave, corpo.toString(),
                        new Ricevitore() {
                            @Override
                            public void evento(JsonObject json) {
                                unisci(json, pezzi);
                                leggiFonti(json);
                                if (haStrumenti[0]) return;
                                for (JsonObject p : pezzi) if (p.has("functionCall")) haStrumenti[0] = true;
                                if (haStrumenti[0]) {
                                    if (ascolto != null && scritto.length() > 0) ascolto.testo("");
                                    return;
                                }
                                StringBuilder t = new StringBuilder();
                                for (JsonObject p : pezzi) if (p.has("text")) t.append(p.get("text").getAsString());
                                if (ascolto != null && t.length() > scritto.length()) {
                                    scritto.setLength(0);
                                    scritto.append(t);
                                    ascolto.testo(t.toString());
                                }
                            }
                        });
            } catch (java.net.SocketTimeoutException e) {
                if (annullata) throw new Errore(Errore.ANNULLATA, "");
                // Gemini troppo lento a rispondere: riprovo, poi passo a un modello piu' leggero
                if (pezzi.isEmpty() && ++tentativi <= 2) {
                    if (tentativi == 2 && modello < modelli.length - 1) modello++;
                    continue;
                }
                throw new Errore(Errore.RETE, messaggi.servizio + " (timeout)");
            } catch (IOException e) {
                if (annullata) throw new Errore(Errore.ANNULLATA, "");
                // connessione caduta (capita col cambio Wi-Fi/rete mobile): un nuovo tentativo di solito basta
                if (pezzi.isEmpty() && ++tentativi <= 2) {
                    pausa(800);
                    continue;
                }
                throw new Errore(Errore.RETE, messaggi.rete + " (" + e.getClass().getSimpleName() + ")");
            } catch (RuntimeException e) {
                if (annullata) throw new Errore(Errore.ANNULLATA, "");
                throw new Errore(Errore.ALTRO, messaggi.errore + ": " + e);
            }
            if (r.codice == 404 && modello < modelli.length - 1) {
                modello++; // modello non disponibile per questa chiave: provo il successivo
                continue;
            }
            if (r.codice == 400 && pensieroBasso && r.corpo != null && r.corpo.toLowerCase().contains("thinking")) {
                pensieroBasso = false; // il modello non accetta il livello di ragionamento: riprovo senza
                continue;
            }
            if (r.codice == 429 && modello < modelli.length - 1) {
                // limite di quel modello (ognuno ha il suo): passo subito al successivo
                modello++;
                continue;
            }
            if (r.codice >= 500 && ++tentativi <= 3) {
                // 503 = modello sovraccarico (frequente sul piano gratuito): riprovo, poi cambio modello
                if (tentativi >= 2 && modello < modelli.length - 1) modello++;
                else pausa(1500);
                continue;
            }
            if (r.codice != 200) throw errore(r, messaggi);
            tentativi = 0;

            if (pezzi.isEmpty()) return messaggi.vuota;
            JsonArray p = new JsonArray();
            for (JsonObject x : pezzi) p.add(x);
            JsonObject contenuto = new JsonObject();
            contenuto.addProperty("role", "model");
            contenuto.add("parts", p);
            storia.add(contenuto); // parti originali (con le thoughtSignature), solo il testo e' ricomposto

            JsonArray risposte = new JsonArray();
            StringBuilder testo = new StringBuilder();
            for (JsonElement el : p) {
                JsonObject part = el.getAsJsonObject();
                if (part.has("functionCall")) {
                    JsonObject call = part.getAsJsonObject("functionCall");
                    String nome = call.get("name").getAsString();
                    JsonObject args = call.has("args") && call.get("args").isJsonObject()
                            ? call.getAsJsonObject("args") : new JsonObject();
                    JsonObject risultato;
                    try {
                        risultato = esecutore.esegui(nome, args);
                    } catch (RuntimeException e) {
                        risultato = new JsonObject();
                        risultato.addProperty("errore", "Strumento non riuscito: " + e.getMessage());
                    }
                    JsonObject fr = new JsonObject();
                    fr.addProperty("name", nome);
                    if (call.has("id")) fr.add("id", call.get("id"));
                    fr.add("response", risultato);
                    JsonObject parte = new JsonObject();
                    parte.add("functionResponse", fr);
                    risposte.add(parte);
                } else if (part.has("text") && !(part.has("thought") && part.get("thought").getAsBoolean())) {
                    testo.append(part.get("text").getAsString());
                }
            }
            if (risposte.size() == 0) {
                if (annullata) throw new Errore(Errore.ANNULLATA, "");
                String t = testo.toString().trim();
                return t.isEmpty() ? messaggi.nessuna : t;
            }
            StringBuilder nomi = new StringBuilder();
            for (JsonElement el : risposte) {
                if (nomi.length() > 0) nomi.append(", ");
                nomi.append(el.getAsJsonObject().getAsJsonObject("functionResponse").get("name").getAsString());
            }
            stato(STRUMENTI, nomi.toString());
            JsonObject turno = new JsonObject();
            turno.addProperty("role", "user");
            turno.add("parts", risposte);
            storia.add(turno);
            passo++;
        }
        return messaggi.passaggi;
    }

    private void leggiFonti(JsonObject json) {
        try {
            if (!json.has("candidates")) return;
            JsonObject cand = json.getAsJsonArray("candidates").get(0).getAsJsonObject();
            if (!cand.has("groundingMetadata")) return;
            JsonObject gm = cand.getAsJsonObject("groundingMetadata");
            if (!gm.has("groundingChunks")) return;
            for (JsonElement el : gm.getAsJsonArray("groundingChunks")) {
                JsonObject w = el.getAsJsonObject().getAsJsonObject("web");
                if (w == null) continue;
                String titolo = w.has("title") ? w.get("title").getAsString() : "";
                String uri = w.has("uri") ? w.get("uri").getAsString() : "";
                boolean gia = false;
                for (String[] f : fonti) if (f[0].equals(titolo)) gia = true;
                if (!gia && !titolo.isEmpty()) fonti.add(new String[]{titolo, uri});
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static void pausa(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Unisce un pezzo dello streaming alle parti gia' ricevute: i testi consecutivi diventano una parte sola
     * (tenendo la thoughtSignature, che puo' arrivare in un pezzo a parte); le chiamate a funzione restano intere.
     */
    static void unisci(JsonObject json, java.util.List<JsonObject> pezzi) {
        if (!json.has("candidates")) return;
        JsonArray c = json.getAsJsonArray("candidates");
        if (c.size() == 0) return;
        JsonObject cand = c.get(0).getAsJsonObject();
        if (!cand.has("content") || !cand.getAsJsonObject("content").has("parts")) return;
        for (JsonElement el : cand.getAsJsonObject("content").getAsJsonArray("parts")) {
            JsonObject part = el.getAsJsonObject();
            if (part.has("thought") && part.get("thought").getAsBoolean()) continue;
            JsonObject coda = pezzi.isEmpty() ? null : pezzi.get(pezzi.size() - 1);
            boolean testo = part.has("text") && !part.has("functionCall");
            if (testo && coda != null && coda.has("text") && !coda.has("functionCall")) {
                coda.addProperty("text", coda.get("text").getAsString() + part.get("text").getAsString());
                if (part.has("thoughtSignature")) coda.add("thoughtSignature", part.get("thoughtSignature"));
            } else if (testo || part.has("functionCall")) {
                pezzi.add(part.deepCopy());
            } else if (part.has("thoughtSignature") && coda != null) {
                coda.add("thoughtSignature", part.get("thoughtSignature"));
            }
        }
    }

    private static JsonArray parti(String testo) {
        JsonArray a = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", testo);
        a.add(p);
        return a;
    }

    /** Messaggio dell'utente con una foto JPEG (base64) e il testo. */
    public static JsonObject messaggioConFoto(String testo, String jpegBase64) {
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        JsonArray parts = new JsonArray();
        JsonObject img = new JsonObject();
        JsonObject inline = new JsonObject();
        inline.addProperty("mimeType", "image/jpeg");
        inline.addProperty("data", jpegBase64);
        img.add("inlineData", inline);
        parts.add(img);
        JsonObject t = new JsonObject();
        t.addProperty("text", testo);
        parts.add(t);
        m.add("parts", parts);
        return m;
    }

    public static JsonObject messaggio(String ruolo, String testo) {
        JsonObject m = new JsonObject();
        m.addProperty("role", ruolo);
        m.add("parts", parti(testo));
        return m;
    }

    private static Errore errore(Risposta r, Messaggi t) {
        String msg = "";
        try {
            JsonObject e = JsonParser.parseString(r.corpo).getAsJsonObject().getAsJsonObject("error");
            msg = e.get("message").getAsString();
        } catch (RuntimeException ignored) {
        }
        String m = msg.toLowerCase();
        if (r.codice == 401 || r.codice == 403 || m.contains("api key") || m.contains("api_key")) {
            return new Errore(Errore.CHIAVE, t.chiave);
        }
        if (r.codice == 429) {
            return new Errore(Errore.QUOTA, t.quota + (msg.isEmpty() ? "" : "\n\n" + msg));
        }
        if (r.codice >= 500) {
            return new Errore(Errore.RETE, t.servizio + " (" + r.codice + ")");
        }
        return new Errore(Errore.ALTRO, t.errore + " (" + r.codice + ")" + (msg.isEmpty() ? "" : ": " + msg));
    }

    /** Trasporto reale via HttpURLConnection, con lettura dell'SSE riga per riga. */
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
        public Risposta stream(String url, String chiave, String corpo, Ricevitore ricevitore) throws IOException {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            corrente = c;
            c.setRequestMethod("POST");
            c.setConnectTimeout(15_000);
            c.setReadTimeout(45_000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "text/event-stream");
            c.setRequestProperty("x-goog-api-key", chiave);
            OutputStream os = c.getOutputStream();
            os.write(corpo.getBytes(StandardCharsets.UTF_8));
            os.close();
            int codice = c.getResponseCode();
            try {
                if (codice != 200) {
                    InputStream is = c.getErrorStream();
                    String risposta = "";
                    if (is != null) {
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
                        is.close();
                        risposta = new String(b.toByteArray(), StandardCharsets.UTF_8);
                    }
                    return new Risposta(codice, risposta);
                }
                leggiSse(c.getInputStream(), ricevitore);
                return new Risposta(200, "");
            } finally {
                corrente = null;
                c.disconnect();
            }
        }
    }

    /** Legge un flusso SSE: ogni evento "data: {...}" (anche su piu' righe) va al ricevitore. */
    static void leggiSse(InputStream is, Ricevitore ricevitore) throws IOException {
        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder dati = new StringBuilder();
        String riga;
        while (true) {
            riga = in.readLine();
            if (riga == null || riga.isEmpty()) {
                if (dati.length() > 0) {
                    String d = dati.toString().trim();
                    dati.setLength(0);
                    if (!d.isEmpty() && !"[DONE]".equals(d)) {
                        JsonElement e = JsonParser.parseString(d);
                        if (e.isJsonObject()) ricevitore.evento(e.getAsJsonObject());
                    }
                }
                if (riga == null) break;
            } else if (riga.startsWith("data:")) {
                if (dati.length() > 0) dati.append('\n');
                dati.append(riga.substring(5).trim());
            }
        }
        in.close();
    }
}
