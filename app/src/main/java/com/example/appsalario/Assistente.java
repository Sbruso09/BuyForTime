package com.example.appsalario;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Strumenti dell'assistente: tutti in SOLA LETTURA. L'AI li chiama per avere numeri veri;
 * i calcoli sono quelli dell'app (saldo, controlli, ore, busta paga), mai stime del modello.
 * Le simulazioni lavorano su copie e non salvano nulla.
 */
public final class Assistente implements Gemini.Esecutore {
    public static final String K_RISERVATA = "ai_modalita_riservata";

    private final Context ctx;
    private final SpeseManager manager;
    private final boolean riservata;
    private final ZoneId zona = ZoneId.systemDefault();

    public Assistente(Context ctx) {
        this.ctx = Lingua.contesto(ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx);
        this.manager = new SpeseManager(this.ctx);
        this.riservata = this.ctx.getSharedPreferences("SalarioPrefs", 0).getBoolean(K_RISERVATA, false);
    }

    // ================================================================== istruzioni

    public String istruzioni() {
        String oggi = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALY));
        return "Sei l'assistente di BuyForTime, un'app che trasforma le spese in tempo di lavoro e tiene il saldo.\n"
                + "Oggi è " + oggi + ". " + (Lingua.inglese()
                        ? "Rispondi SEMPRE in inglese (English), anche se i dati degli strumenti sono in italiano"
                        : "Rispondi sempre in italiano") + ", in modo amichevole, chiaro e breve.\n\n"
                + "REGOLE:\n"
                + "1. Per qualsiasi numero (saldo, previsioni, ore di lavoro, stipendio, spese) usa SEMPRE gli strumenti. "
                + "Non inventare cifre e non rifare a mano calcoli che uno strumento sa fare.\n"
                + "2. I controlli dell'app sono vincolanti e hanno la priorità sui tuoi consigli. Se valuta_spesa restituisce "
                + "un avviso ROSSO, la spesa non è possibile: dillo chiaramente e non suggerire modi per aggirarlo. "
                + "Se c'è un avviso GIALLO, sconsiglia la spesa così com'è e spiega il motivo. Puoi proporre alternative "
                + "(aspettare la data indicata da miglior_data, un importo entro budget_sicuro, rinunciare).\n"
                + "3. Esprimi i costi anche in tempo di lavoro, come fa l'app (giorni lavorativi e ore).\n"
                + "4. Sei in sola lettura: non puoi inserire, approvare, modificare o eliminare spese o impostazioni. "
                + "Se l'utente te lo chiede, spiega che deve farlo dall'app.\n"
                + "5. Non hai accesso a internet: per prodotti e prezzi usa le informazioni che ti dà l'utente.\n"
                + "6. Quando confronti opzioni o valuti una spesa, chiudi con una raccomandazione concreta.\n"
                + "7. Formattazione: solo **grassetto** e elenchi con '- '. Niente tabelle, titoli o emoji in eccesso.\n"
                + "8. Le date per gli strumenti sono nel formato AAAA-MM-GG.\n"
                + "9. Velocità: se ti servono più strumenti chiamali tutti insieme nello stesso turno, non uno alla volta. "
                + "Per domande generali sulla situazione usa i DATI ATTUALI qui sotto senza richiamare stato_finanziario.\n\n"
                + "DATI ATTUALI (risultato di stato_finanziario, già aggiornato):\n" + fotografia();
    }

    /** true se la domanda chiede prezzi dell'usato / annunci: si usa la ricerca Google. */
    public static boolean vuoleRicerca(String domanda) {
        String d = " " + domanda.toLowerCase(Locale.ROOT) + " ";
        String[] chiavi = {" usato", " usata", " usati", " usate", "subito", "vinted", "ebay", "second hand",
                "second-hand", " used", "secondhand", "refurbished", "ricondizionat"};
        for (String k : chiavi) if (d.contains(k)) return true;
        return false;
    }

    /** Istruzioni per la modalita' ricerca web (senza gli strumenti dell'app). */
    public String istruzioniRicerca() {
        String oggi = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALY));
        return "Sei l'assistente di BuyForTime, un'app che trasforma le spese in tempo di lavoro.\n"
                + "Oggi è " + oggi + ". " + (Lingua.inglese()
                        ? "Rispondi SEMPRE in inglese (English)"
                        : "Rispondi sempre in italiano") + ", in modo chiaro e breve.\n\n"
                + "COMPITO: l'utente cerca un prodotto usato. Usa la ricerca Google per trovare annunci e prezzi "
                + "attuali su Subito.it, Vinted ed eBay.it (in Italia). Poi:\n"
                + "- dai una forbice di prezzo realistica dell'usato e, se lo trovi, il prezzo del nuovo;\n"
                + "- indica su quale sito conviene cercare e 2-3 cose da controllare prima di comprare (condizioni, garanzia, truffe);\n"
                + "- converti i prezzi in tempo di lavoro con valore_ora_lavoro e ore_lavoro_al_giorno dei DATI qui sotto "
                + "(ore = prezzo / valore_ora_lavoro; giorni = ore / ore_lavoro_al_giorno);\n"
                + "- se il saldo è impostato, di' se il prezzo è sostenibile rispetto a budget_sicuro_oggi.\n"
                + "Non inventare prezzi: se non trovi annunci dillo. Formattazione: solo **grassetto** ed elenchi con '- '.\n"
                + RIGA_PRODOTTO + "\n\nDATI ATTUALI:\n" + fotografia();
    }

    private static final String RIGA_PRODOTTO = "ULTIMA RIGA: se hai individuato un prodotto preciso, chiudi la risposta con una riga "
            + "a parte nel formato esatto 'PRODOTTO: <marca e modello, poche parole, adatte a una ricerca su Subito/Vinted/eBay>'. "
            + "Se non c'e' un prodotto preciso non scrivere questa riga.";

    /** Istruzioni quando l'utente manda una foto. */
    public String istruzioniFoto() {
        String oggi = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALY));
        return "Sei l'assistente di BuyForTime, un'app che trasforma le spese in tempo di lavoro.\n"
                + "Oggi è " + oggi + ". " + (Lingua.inglese()
                        ? "Rispondi SEMPRE in inglese (English)"
                        : "Rispondi sempre in italiano") + ", in modo chiaro e breve.\n\n"
                + "L'utente ti manda una FOTO. Guardala con attenzione:\n"
                + "- se è un prodotto: riconosci marca, modello e condizioni visibili (se non sei sicuro dillo e indica le alternative); "
                + "se l'utente chiede del prezzo o dell'usato, usa la ricerca Google per trovare prezzi attuali dell'usato su "
                + "Subito.it, Vinted ed eBay.it e del nuovo, e dai una forbice realistica;\n"
                + "- se è uno scontrino, un cartellino o un preventivo: leggi gli importi e il totale;\n"
                + "- se ci sono due prodotti: confrontali.\n"
                + "Converti sempre i prezzi in tempo di lavoro con valore_ora_lavoro e ore_lavoro_al_giorno dei DATI qui sotto "
                + "(ore = prezzo / valore_ora_lavoro; giorni = ore / ore_lavoro_al_giorno) e, se il saldo è impostato, "
                + "di' se è sostenibile rispetto a budget_sicuro_oggi. Non inventare prezzi.\n"
                + "Formattazione: solo **grassetto** ed elenchi con '- '.\n"
                + RIGA_PRODOTTO + "\n\nDATI ATTUALI:\n" + fotografia();
    }

    /** Separa la riga finale "PRODOTTO: ..." dal testo: {testo, prodotto o null}. */
    public static String[] separaProdotto(String testo) {
        if (testo == null) return new String[]{"", null};
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?im)^[ \\t*]*(PRODOTTO|PRODUCT)[ \\t*]*:[ \\t*]*(.+?)[ \\t*]*$").matcher(testo);
        String prodotto = null;
        int da = -1, a = -1;
        while (m.find()) {
            prodotto = m.group(2).trim();
            da = m.start();
            a = m.end();
        }
        if (prodotto == null) return new String[]{testo, null};
        String resto = (testo.substring(0, da) + testo.substring(a)).trim();
        if (prodotto.isEmpty() || prodotto.length() > 80) prodotto = null;
        return new String[]{resto, prodotto};
    }


    private String fotografia() {
        try {
            return statoFinanziario().toString();
        } catch (RuntimeException e) {
            return "{}";
        }
    }

    // ================================================================== dichiarazioni

    private static JsonObject prop(String tipo, String descr) {
        JsonObject p = new JsonObject();
        p.addProperty("type", tipo);
        p.addProperty("description", descr);
        return p;
    }

    private static JsonObject funzione(String nome, String descr, JsonObject proprieta, String... obbligatori) {
        JsonObject f = new JsonObject();
        f.addProperty("name", nome);
        f.addProperty("description", descr);
        JsonObject par = new JsonObject();
        par.addProperty("type", "OBJECT");
        par.add("properties", proprieta == null ? new JsonObject() : proprieta);
        if (obbligatori.length > 0) {
            JsonArray req = new JsonArray();
            for (String o : obbligatori) req.add(o);
            par.add("required", req);
        }
        f.add("parameters", par);
        return f;
    }

    private static JsonObject props(Object... coppie) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < coppie.length; i += 2) o.add((String) coppie[i], (JsonObject) coppie[i + 1]);
        return o;
    }

    public static JsonArray dichiarazioni() {
        JsonArray a = new JsonArray();
        a.add(funzione("stato_finanziario",
                "Quadro generale: saldo attuale, prossimo stipendio e saldo previsto prima di esso, prossimi movimenti, "
                        + "netto, valore di un'ora di lavoro, spese fisse, budget sicuro e soglie dei controlli.", null));
        a.add(funzione("valuta_spesa",
                "Esegue i controlli ufficiali dell'app (rossi e gialli) su una possibile spesa e dice quanto lavoro costa.",
                props("importo", prop("NUMBER", "Importo in euro"),
                        "data", prop("STRING", "Data della spesa AAAA-MM-GG (vuota = adesso)"),
                        "nome", prop("STRING", "Nome della spesa, se noto")), "importo"));
        a.add(funzione("miglior_data",
                "Trova il primo giorno in cui la spesa non fa scattare avvisi sui soldi (rossi o gialli).",
                props("importo", prop("NUMBER", "Importo in euro"),
                        "entro_giorni", prop("INTEGER", "Quanti giorni guardare avanti (predefinito 60, massimo 180)")), "importo"));
        a.add(funzione("budget_sicuro",
                "Importo massimo spendibile a una data senza far scattare avvisi sui soldi.",
                props("data", prop("STRING", "Data AAAA-MM-GG (vuota = oggi)"))));
        JsonObject opzione = new JsonObject();
        opzione.addProperty("type", "OBJECT");
        opzione.add("properties", props("nome", prop("STRING", "Nome dell'opzione"), "prezzo", prop("NUMBER", "Prezzo in euro")));
        JsonArray reqOpz = new JsonArray();
        reqOpz.add("nome");
        reqOpz.add("prezzo");
        opzione.add("required", reqOpz);
        JsonObject arr = prop("ARRAY", "Opzioni da confrontare");
        arr.add("items", opzione);
        a.add(funzione("confronta_opzioni",
                "Confronta due o più acquisti alternativi: costo in lavoro e controlli dell'app per ciascuno.",
                props("opzioni", arr), "opzioni"));
        a.add(funzione("obiettivo_risparmio",
                "Verifica se è possibile mettere da parte un importo entro una data, considerando stipendi e spese fisse.",
                props("importo", prop("NUMBER", "Importo da accantonare"),
                        "data", prop("STRING", "Data obiettivo AAAA-MM-GG")), "importo", "data"));
        JsonObject stato = prop("STRING", "tutte, approvate o da_valutare");
        JsonArray en = new JsonArray();
        en.add("tutte");
        en.add("approvate");
        en.add("da_valutare");
        stato.add("enum", en);
        a.add(funzione("elenco_spese",
                "Elenco delle spese inserite, filtrabile per categoria, periodo e stato.",
                props("categoria", prop("STRING", "Nome della categoria (opzionale)"),
                        "da", prop("STRING", "Dal giorno AAAA-MM-GG"),
                        "a", prop("STRING", "Al giorno AAAA-MM-GG"),
                        "stato", stato,
                        "limite", prop("INTEGER", "Numero massimo di righe (predefinito 50)"))));
        a.add(funzione("riepilogo_periodo",
                "Riepilogo di un periodo: entrate, spese fisse, spese approvate, totali per categoria e ore di lavoro.",
                props("da", prop("STRING", "Dal giorno AAAA-MM-GG"), "a", prop("STRING", "Al giorno AAAA-MM-GG")), "da", "a"));
        a.add(funzione("spese_fisse", "Le spese fisse con importo, frequenza, peso mensile e ore di lavoro al mese.", null));
        a.add(funzione("ripaghi", "Spese approvate in fase di ripago: stato del countdown, data di fine, avanzamento.", null));
        a.add(funzione("costo_utilizzi", "Spese di cui si tracciano gli utilizzi: costo e tempo di lavoro per utilizzo.", null));
        JsonObject tipo = prop("STRING", "netto_mese, netto_anno o ral");
        JsonArray tipi = new JsonArray();
        tipi.add("netto_mese");
        tipi.add("netto_anno");
        tipi.add("ral");
        tipo.add("enum", tipi);
        a.add(funzione("simula_stipendio",
                "Simula uno stipendio diverso (non salva nulla) e confronta netto e valore dell'ora con quelli attuali.",
                props("tipo", tipo, "importo", prop("NUMBER", "Nuovo importo dello stipendio"),
                        "mensilita", prop("INTEGER", "12, 13 o 14"))));
        a.add(funzione("simula_spese_fisse",
                "Simula una variazione delle spese fisse mensili (non salva nulla): effetto su disponibile e valore dell'ora.",
                props("variazione_mensile", prop("NUMBER", "Euro al mese in più (positivo) o in meno (negativo)")), "variazione_mensile"));
        a.add(funzione("busta_paga",
                "Dettaglio dalla RAL al netto (INPS, IRPEF, detrazioni, addizionali). Senza ral usa quella impostata.",
                props("ral", prop("NUMBER", "RAL da calcolare (opzionale)"))));
        return a;
    }

    // ================================================================== esecuzione

    @Override
    public JsonObject esegui(String nome, JsonObject a) {
        switch (nome) {
            case "stato_finanziario": return statoFinanziario();
            case "valuta_spesa": return valutaSpesa(num(a, "importo", 0), data(a, "data"), str(a, "nome", "Spesa"));
            case "miglior_data": return migliorData(num(a, "importo", 0), (int) Math.max(1, Math.min(180, num(a, "entro_giorni", 60))));
            case "budget_sicuro": return budgetSicuro(data(a, "data"));
            case "confronta_opzioni": return confrontaOpzioni(a.has("opzioni") ? a.getAsJsonArray("opzioni") : new JsonArray());
            case "obiettivo_risparmio": return obiettivo(num(a, "importo", 0), data(a, "data"));
            case "elenco_spese": return elencoSpese(str(a, "categoria", null), data(a, "da"), data(a, "a"),
                    str(a, "stato", "tutte"), (int) num(a, "limite", 50));
            case "riepilogo_periodo": return riepilogo(data(a, "da"), data(a, "a"));
            case "spese_fisse": return speseFisse();
            case "ripaghi": return ripaghi();
            case "costo_utilizzi": return costoUtilizzi();
            case "simula_stipendio": return simulaStipendio(str(a, "tipo", null), num(a, "importo", -1), (int) num(a, "mensilita", -1));
            case "simula_spese_fisse": return simulaFisse(num(a, "variazione_mensile", 0));
            case "busta_paga": return bustaPaga(num(a, "ral", -1));
            default: {
                JsonObject o = new JsonObject();
                o.addProperty("errore", "Strumento sconosciuto: " + nome);
                return o;
            }
        }
    }

    // ------------------------------------------------------------------ utilità

    private static double num(JsonObject a, String k, double def) {
        try {
            return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsDouble() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static String str(JsonObject a, String k, String def) {
        try {
            if (!a.has(k) || a.get(k).isJsonNull()) return def;
            String s = a.get(k).getAsString().trim();
            return s.isEmpty() ? def : s;
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Data AAAA-MM-GG -> millisecondi a mezzogiorno; -1 se assente o non valida. */
    private long data(JsonObject a, String k) {
        String s = str(a, k, null);
        if (s == null) return -1;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s).atTime(12, 0).atZone(zona).toInstant().toEpochMilli();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private String giorno(long ms) {
        return Instant.ofEpochMilli(ms).atZone(zona).toLocalDate().toString();
    }

    private static double eur(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String tempo(double ore) {
        return TempoFormatter.INSTANCE.formatta(ctx, (float) ore, (float) manager.orario().oreGiorno());
    }

    private String nomeSpesa(Spesa s) {
        return riservata ? Categorie.get(ctx, s.getCategoria()).nome : s.getNome();
    }

    private Controlli.Dati dati(String nome, double importo, long data) {
        boolean futura = data > System.currentTimeMillis();
        return ControlliUi.dati(ctx, nome, importo, futura ? data : System.currentTimeMillis(), futura, false, null);
    }

    private static List<Controlli.Avviso> suiSoldi(Controlli.Dati d) {
        List<Controlli.Avviso> out = new ArrayList<Controlli.Avviso>();
        for (Controlli.Avviso av : Controlli.valuta(d)) if (av.suiSoldi()) out.add(av);
        return out;
    }

    private static JsonArray avvisiJson(List<Controlli.Avviso> l) {
        JsonArray a = new JsonArray();
        for (Controlli.Avviso av : l) {
            JsonObject o = new JsonObject();
            o.addProperty("livello", av.livello == Controlli.ROSSO ? "ROSSO" : av.livello == Controlli.GIALLO ? "GIALLO" : "INFO");
            o.addProperty("titolo", av.titolo);
            o.addProperty("dettaglio", av.testo);
            a.add(o);
        }
        return a;
    }

    private double oreDi(double importo) {
        double t = manager.stipendio().tariffaOraria();
        return t > 0 ? importo / t : 0;
    }

    // ------------------------------------------------------------------ strumenti

    private JsonObject statoFinanziario() {
        JsonObject o = new JsonObject();
        Saldo.aggiorna(ctx);
        Stipendio st = manager.stipendio();
        o.addProperty("oggi", LocalDate.now().toString());
        o.addProperty("saldo_impostato", Saldo.attivo(ctx));
        if (Saldo.attivo(ctx)) {
            o.addProperty("saldo_attuale", eur(Saldo.saldo(ctx)));
            double[] prev = Saldo.previstoAlProssimoStipendio(ctx);
            if (prev != null) {
                o.addProperty("prossimo_stipendio", giorno((long) prev[1]));
                o.addProperty("saldo_previsto_prima_dello_stipendio", eur(prev[0]));
            }
            JsonArray mov = new JsonArray();
            int n = 0;
            for (Saldo.Movimento m : Saldo.prossimi(ctx, 45)) {
                if (n++ >= 8) break;
                JsonObject x = new JsonObject();
                x.addProperty("data", giorno(m.ts));
                x.addProperty("descrizione", riservata && m.tipo == Saldo.FISSA ? "Spesa fissa" : m.nome);
                x.addProperty("importo", eur(m.importo));
                mov.add(x);
            }
            o.add("prossimi_movimenti", mov);
            o.add("budget_sicuro_oggi", budgetSicuro(-1).get("budget_sicuro"));
        }
        o.addProperty("tipo_stipendio", st.tipo);
        o.addProperty("mensilita", (int) Math.round(st.mensilita));
        o.addProperty("netto_per_mensilita", eur(st.nettoPerMensilita()));
        o.addProperty("netto_annuo", eur(st.nettoAnnuo()));
        o.addProperty("spese_fisse_al_mese", eur(st.fisseMensili));
        o.addProperty("disponibile_annuo_dopo_spese_fisse", eur(st.disponibileAnnuo()));
        o.addProperty("valore_ora_lavoro", eur(st.tariffaOraria()));
        o.addProperty("ore_lavoro_al_giorno", Math.round(st.oreGiorno * 100) / 100.0);
        o.addProperty("giorni_lavorativi_al_mese", Math.round(st.giorniMese * 10) / 10.0);
        SharedPreferences p = ctx.getSharedPreferences("SalarioPrefs", 0);
        o.addProperty("soglia_avviso_residuo_percento", p.getInt(ControlliUi.K_SOGLIA_RESIDUO, 35));
        o.addProperty("soglia_avviso_giorni_lavoro", p.getInt(ControlliUi.K_SOGLIA_GIORNI, 21));
        int daValutare = 0, approvate = 0;
        for (Spesa s : manager.getSpese()) if (s.getApprovata()) approvate++; else daValutare++;
        o.addProperty("spese_da_valutare", daValutare);
        o.addProperty("spese_approvate", approvate);
        return o;
    }

    private JsonObject valutaSpesa(double importo, long data, String nome) {
        JsonObject o = new JsonObject();
        Controlli.Dati d = dati(nome, importo, data);
        List<Controlli.Avviso> av = Controlli.valuta(d);
        int liv = Controlli.livelloMassimo(av);
        o.addProperty("esito", liv == Controlli.ROSSO ? "ROSSO: non possibile" : liv == Controlli.GIALLO ? "GIALLO: sconsigliata" : "OK");
        o.add("avvisi", avvisiJson(av));
        double ore = oreDi(importo);
        o.addProperty("ore_di_lavoro", Math.round(ore * 10) / 10.0);
        o.addProperty("tempo_di_lavoro", tempo(ore));
        if (d.saldoAttivo) {
            o.addProperty("saldo_di_riferimento", eur(Controlli.saldoAl(d, d.dataRiferimento)));
            o.addProperty("saldo_dopo_la_spesa", eur(Controlli.saldoAl(d, d.dataRiferimento) - importo));
        }
        return o;
    }

    /** Valuta i soli avvisi sui soldi a un certo giorno (0 = adesso). */
    private List<Controlli.Avviso> avvisiAlGiorno(Controlli.Dati base, int giorni) {
        long ts = giorni == 0 ? base.adesso
                : LocalDate.now().plusDays(giorni).atTime(12, 0).atZone(zona).toInstant().toEpochMilli();
        base.dataRiferimento = Math.max(base.adesso, ts);
        base.dataPassata = false;
        return suiSoldi(base);
    }

    private JsonObject migliorData(double importo, int entro) {
        JsonObject o = new JsonObject();
        if (!Saldo.attivo(ctx)) {
            o.addProperty("errore", "Saldo non impostato: impossibile calcolare la data migliore.");
            return o;
        }
        Controlli.Dati d = dati("Spesa", importo, -1);
        List<Controlli.Avviso> oggi = avvisiAlGiorno(d, 0);
        o.add("avvisi_oggi", avvisiJson(oggi));
        for (int g = 0; g <= entro; g++) {
            if (avvisiAlGiorno(d, g).isEmpty()) {
                o.addProperty("miglior_data", g == 0 ? LocalDate.now().toString() : LocalDate.now().plusDays(g).toString());
                o.addProperty("giorni_di_attesa", g);
                o.addProperty("saldo_previsto_quel_giorno", eur(Controlli.saldoAl(d, d.dataRiferimento)));
                return o;
            }
        }
        o.addProperty("miglior_data", "nessuna nei prossimi " + entro + " giorni");
        return o;
    }

    private JsonObject budgetSicuro(long data) {
        JsonObject o = new JsonObject();
        if (!Saldo.attivo(ctx)) {
            o.addProperty("errore", "Saldo non impostato.");
            return o;
        }
        Controlli.Dati d = dati("Spesa", 0, data);
        double max = Controlli.saldoAl(d, d.dataRiferimento);
        double lo = 0, hi = Math.max(0, max);
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            d.importo = mid;
            if (suiSoldi(d).isEmpty()) lo = mid; else hi = mid;
        }
        d.importo = lo;
        boolean ok = suiSoldi(d).isEmpty();
        o.addProperty("data", data > 0 ? giorno(data) : LocalDate.now().toString());
        o.addProperty("budget_sicuro", ok ? Math.floor(lo) : 0);
        o.addProperty("tempo_di_lavoro_equivalente", tempo(oreDi(ok ? Math.floor(lo) : 0)));
        return o;
    }

    private JsonObject confrontaOpzioni(JsonArray opzioni) {
        JsonObject o = new JsonObject();
        JsonArray out = new JsonArray();
        for (JsonElement el : opzioni) {
            JsonObject op = el.getAsJsonObject();
            String nome = str(op, "nome", "Opzione");
            double prezzo = num(op, "prezzo", 0);
            JsonObject r = valutaSpesa(prezzo, -1, nome);
            r.addProperty("nome", nome);
            r.addProperty("prezzo", eur(prezzo));
            out.add(r);
        }
        o.add("opzioni", out);
        return o;
    }

    private JsonObject obiettivo(double importo, long data) {
        JsonObject o = new JsonObject();
        if (!Saldo.attivo(ctx)) {
            o.addProperty("errore", "Saldo non impostato.");
            return o;
        }
        if (data <= 0) {
            o.addProperty("errore", "Data non valida.");
            return o;
        }
        Controlli.Dati d = dati("Risparmio", importo, -1);
        double previsto = Controlli.saldoAl(d, data);
        double minimo = d.saldoOra;
        double s = d.saldoOra;
        for (Saldo.Movimento m : d.futuri) {
            if (m.ts > data) break;
            s += m.importo;
            minimo = Math.min(minimo, s);
        }
        double[] fisse = Controlli.fissePrimaDelloStipendio(d, data, null);
        double resta = previsto - importo;
        o.addProperty("data", giorno(data));
        o.addProperty("saldo_previsto_alla_data", eur(previsto));
        o.addProperty("saldo_minimo_nel_periodo", eur(minimo));
        o.addProperty("dopo_aver_accantonato", eur(resta));
        o.addProperty("spese_fisse_subito_dopo", eur(fisse[0]));
        o.addProperty("fattibile", resta >= fisse[0]);
        o.addProperty("tempo_di_lavoro_per_l_importo", tempo(oreDi(importo)));
        return o;
    }

    private JsonObject elencoSpese(String categoria, long da, long a, String stato, int limite) {
        JsonObject o = new JsonObject();
        JsonArray out = new JsonArray();
        int n = 0;
        double tot = 0;
        for (Spesa s : manager.getSpese()) {
            Categorie.Categoria c = Categorie.get(ctx, s.getCategoria());
            if (categoria != null && !c.nome.equalsIgnoreCase(categoria) && !c.key.equalsIgnoreCase(categoria)) continue;
            if (da > 0 && s.getDataCreazione() < da - 43_200_000L) continue;
            if (a > 0 && s.getDataCreazione() > a + 43_200_000L) continue;
            if ("approvate".equals(stato) && !s.getApprovata()) continue;
            if ("da_valutare".equals(stato) && s.getApprovata()) continue;
            tot += s.getImporto();
            if (n++ >= Math.max(1, Math.min(200, limite))) continue;
            JsonObject x = new JsonObject();
            x.addProperty("nome", nomeSpesa(s));
            x.addProperty("importo", eur(s.getImporto()));
            x.addProperty("data", giorno(s.getDataCreazione()));
            x.addProperty("categoria", c.nome);
            x.addProperty("approvata", s.getApprovata());
            x.addProperty("tempo_di_lavoro", tempo(s.getOreNecessarie()));
            out.add(x);
        }
        o.addProperty("numero_spese", n);
        o.addProperty("totale", eur(tot));
        o.add("spese", out);
        return o;
    }

    private JsonObject riepilogo(long da, long a) {
        JsonObject o = new JsonObject();
        if (da <= 0 || a <= 0) {
            o.addProperty("errore", "Indica le date da e a.");
            return o;
        }
        long inizio = da - 43_200_000L, fine = a + 43_199_999L;
        double entrate = 0, fisse = 0, spese = 0;
        for (Saldo.Movimento m : Saldo.movimenti(ctx)) {
            if (m.ts < inizio || m.ts > fine) continue;
            if (m.tipo == Saldo.STIPENDIO || m.tipo == Saldo.TREDICESIMA || m.tipo == Saldo.QUATTORDICESIMA) entrate += m.importo;
            else if (m.tipo == Saldo.FISSA) fisse += -m.importo;
        }
        Map<String, double[]> perCat = new LinkedHashMap<String, double[]>();
        double ore = 0;
        int n = 0;
        for (Spesa s : manager.getSpese()) {
            if (!s.getApprovata()) continue;
            long quando = s.getDataApprovazione() > 0 ? Math.max(s.getDataApprovazione(), s.getDataManuale() ? s.getDataCreazione() : 0) : s.getDataCreazione();
            if (quando < inizio || quando > fine) continue;
            String c = Categorie.get(ctx, s.getCategoria()).nome;
            double[] v = perCat.get(c);
            if (v == null) perCat.put(c, v = new double[2]);
            v[0] += s.getImporto();
            v[1] += s.getOreNecessarie();
            spese += s.getImporto();
            ore += s.getOreNecessarie();
            n++;
        }
        o.addProperty("da", giorno(da));
        o.addProperty("a", giorno(a));
        o.addProperty("entrate_registrate", eur(entrate));
        o.addProperty("spese_fisse_pagate", eur(fisse));
        o.addProperty("spese_approvate", eur(spese));
        o.addProperty("numero_spese_approvate", n);
        o.addProperty("tempo_di_lavoro_speso", tempo(ore));
        o.addProperty("nota", Saldo.attivo(ctx) ? "Entrate e spese fisse contano dal momento in cui è stato impostato il saldo."
                : "Saldo non impostato: entrate e spese fisse non sono registrate.");
        JsonArray cat = new JsonArray();
        List<Map.Entry<String, double[]>> voci = new ArrayList<Map.Entry<String, double[]>>(perCat.entrySet());
        Collections.sort(voci, new Comparator<Map.Entry<String, double[]>>() {
            @Override
            public int compare(Map.Entry<String, double[]> x, Map.Entry<String, double[]> y) {
                return Double.compare(y.getValue()[0], x.getValue()[0]);
            }
        });
        for (Map.Entry<String, double[]> e : voci) {
            JsonObject x = new JsonObject();
            x.addProperty("categoria", e.getKey());
            x.addProperty("totale", eur(e.getValue()[0]));
            x.addProperty("tempo_di_lavoro", tempo(e.getValue()[1]));
            cat.add(x);
        }
        o.add("per_categoria", cat);
        return o;
    }

    private JsonObject speseFisse() {
        JsonObject o = new JsonObject();
        JsonArray out = new JsonArray();
        double t = manager.stipendio().tariffaOraria();
        int i = 1;
        double tot = 0;
        for (SpesaFissa f : manager.getSpeseFisse()) {
            JsonObject x = new JsonObject();
            x.addProperty("nome", riservata ? "Spesa fissa " + i : f.getNome());
            x.addProperty("importo_per_volta", eur(f.getImporto()));
            x.addProperty("frequenza", SpesaFissa.nomeFrequenza(f.getFrequenza()));
            x.addProperty("giorno_addebito", f.getGiorno());
            x.addProperty("peso_al_mese", eur(f.importoMensile()));
            // ore di netto necessarie a pagarla (sul netto intero, prima delle spese fisse)
            double nettoOra = manager.stipendio().oreAnno() > 0 ? manager.stipendio().nettoAnnuo() / manager.stipendio().oreAnno() : 0;
            if (nettoOra > 0) x.addProperty("tempo_di_lavoro_al_mese", tempo(f.importoMensile() / nettoOra));
            tot += f.importoMensile();
            out.add(x);
            i++;
        }
        o.addProperty("totale_al_mese", eur(tot));
        o.add("spese_fisse", out);
        return o;
    }

    private JsonObject ripaghi() {
        JsonObject o = new JsonObject();
        JsonArray out = new JsonArray();
        List<Spesa> spese = manager.getSpese();
        Map<String, Ripago.Piano> piani = Ripago.pianifica(spese, manager);
        long ora = System.currentTimeMillis();
        for (Spesa s : spese) {
            Ripago.Piano p = piani.get(s.getId());
            if (p == null) continue;
            int st = p.stato(ora);
            JsonObject x = new JsonObject();
            x.addProperty("nome", nomeSpesa(s));
            x.addProperty("importo", eur(s.getImporto()));
            x.addProperty("stato", st == Ripago.COMPLETATA ? "ripagata" : st == Ripago.IN_CORSO ? "in corso" : "non ancora iniziata");
            x.addProperty("inizio", giorno(p.inizio));
            x.addProperty("fine", giorno(p.fine));
            x.addProperty("avanzamento_percento", Math.round(p.progresso(ora) * 100));
            if (st != Ripago.COMPLETATA) x.addProperty("lavoro_mancante", tempo(p.lavoroRestante(ora) / 3_600_000.0));
            out.add(x);
        }
        o.add("spese_approvate", out);
        return o;
    }

    private JsonObject costoUtilizzi() {
        JsonObject o = new JsonObject();
        JsonArray out = new JsonArray();
        for (Spesa s : manager.getSpese()) {
            if (!s.getTracciaUtilizzi()) continue;
            JsonObject x = new JsonObject();
            x.addProperty("nome", nomeSpesa(s));
            x.addProperty("importo", eur(s.getImporto()));
            x.addProperty("utilizzi", s.getUtilizzi());
            x.addProperty("comprata_il", giorno(s.getDataCreazione()));
            if (s.getUtilizzi() > 0) {
                x.addProperty("costo_per_utilizzo", eur(s.getImporto() / s.getUtilizzi()));
                x.addProperty("lavoro_per_utilizzo", tempo(s.getOreNecessarie() / s.getUtilizzi()));
            }
            out.add(x);
        }
        o.add("spese_tracciate", out);
        if (out.size() == 0) o.addProperty("nota", "Nessuna spesa con il conteggio degli utilizzi attivo.");
        return o;
    }

    private static JsonObject riassunto(Stipendio s) {
        JsonObject o = new JsonObject();
        o.addProperty("tipo", s.tipo);
        o.addProperty("importo", eur(s.salario));
        o.addProperty("mensilita", (int) Math.round(s.mensilita));
        o.addProperty("netto_annuo", eur(s.nettoAnnuo()));
        o.addProperty("netto_per_mensilita", eur(s.nettoPerMensilita()));
        o.addProperty("disponibile_al_mese_dopo_fisse", eur(s.disponibileAnnuo() / 12.0));
        o.addProperty("valore_ora", eur(s.tariffaOraria()));
        return o;
    }

    private static Stipendio copia(Stipendio s) {
        Stipendio c = new Stipendio();
        c.tipo = s.tipo;
        c.salario = s.salario;
        c.mensilita = s.mensilita;
        c.oreGiorno = s.oreGiorno;
        c.giorniMese = s.giorniMese;
        c.fondoPct = s.fondoPct;
        c.regionalePiemonte = s.regionalePiemonte;
        c.regionalePct = s.regionalePct;
        c.comunalePct = s.comunalePct;
        c.fisseMensili = s.fisseMensili;
        return c;
    }

    private JsonObject confrontoStipendi(Stipendio attuale, Stipendio sim) {
        JsonObject o = new JsonObject();
        o.add("attuale", riassunto(attuale));
        o.add("simulato", riassunto(sim));
        o.addProperty("differenza_netto_annuo", eur(sim.nettoAnnuo() - attuale.nettoAnnuo()));
        o.addProperty("differenza_valore_ora", eur(sim.tariffaOraria() - attuale.tariffaOraria()));
        if (attuale.tariffaOraria() > 0 && sim.tariffaOraria() > 0) {
            o.addProperty("esempio_100_euro_ora", tempo(100 / attuale.tariffaOraria()));
            o.addProperty("esempio_100_euro_simulato", tempo(100 / sim.tariffaOraria()));
        }
        o.addProperty("nota", "Simulazione: nessuna impostazione è stata modificata.");
        return o;
    }

    private JsonObject simulaStipendio(String tipo, double importo, int mensilita) {
        Stipendio att = manager.stipendio();
        Stipendio sim = copia(att);
        if ("netto_mese".equals(tipo)) sim.tipo = Stipendio.TIPO_MENSILE;
        else if ("netto_anno".equals(tipo)) sim.tipo = Stipendio.TIPO_ANNUALE;
        else if ("ral".equals(tipo)) sim.tipo = Stipendio.TIPO_RAL;
        if (importo > 0) sim.salario = importo;
        if (mensilita >= 12 && mensilita <= 14) sim.mensilita = mensilita;
        return confrontoStipendi(att, sim);
    }

    private JsonObject simulaFisse(double variazione) {
        Stipendio att = manager.stipendio();
        Stipendio sim = copia(att);
        sim.fisseMensili = Math.max(0, att.fisseMensili + variazione);
        JsonObject o = confrontoStipendi(att, sim);
        o.addProperty("spese_fisse_attuali_al_mese", eur(att.fisseMensili));
        o.addProperty("spese_fisse_simulate_al_mese", eur(sim.fisseMensili));
        return o;
    }

    private JsonObject bustaPaga(double ral) {
        JsonObject o = new JsonObject();
        Stipendio st = manager.stipendio();
        BustaPaga.Risultato r;
        if (ral > 0) {
            r = BustaPaga.calcola(ral, st.fondoPct, st.regionalePiemonte, st.regionalePct, st.comunalePct);
        } else {
            r = st.bustaPaga();
            if (r == null) {
                o.addProperty("errore", "Lo stipendio non è impostato come RAL: indica una RAL da calcolare.");
                return o;
            }
        }
        o.addProperty("ral", eur(r.ral));
        o.addProperty("contributi_inps", eur(r.inps));
        o.addProperty("fondo_pensione", eur(r.fondoPensione));
        o.addProperty("reddito_imponibile", eur(r.imponibile));
        o.addProperty("irpef_lorda", eur(r.irpefLorda));
        o.addProperty("detrazione_lavoro_dipendente", eur(r.detrazioneLavoro));
        o.addProperty("ulteriore_detrazione_cuneo", eur(r.ulterioreDetrazione));
        o.addProperty("irpef_netta", eur(r.irpefNetta));
        o.addProperty("addizionale_regionale", eur(r.addizionaleRegionale));
        o.addProperty("addizionale_comunale", eur(r.addizionaleComunale));
        o.addProperty("somma_esente_cuneo", eur(r.sommaCuneo));
        o.addProperty("trattamento_integrativo", eur(r.trattamentoIntegrativo));
        o.addProperty("netto_annuo", eur(r.netto));
        o.addProperty("netto_per_mensilita", eur(r.netto / Math.max(1, st.mensilita)));
        o.addProperty("pressione_percento", Math.round(r.aliquotaMedia() * 1000) / 10.0);
        o.addProperty("regole", "Lavoro dipendente 2026: INPS 9,19%, IRPEF 23/33/43%, detrazioni art. 13, taglio del cuneo, addizionali.");
        return o;
    }
}
