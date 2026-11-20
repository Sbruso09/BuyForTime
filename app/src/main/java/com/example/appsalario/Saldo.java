package com.example.appsalario;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saldo effettivo come registro di movimenti.
 *
 *  - Punto di partenza: il saldo inserito a mano (base) e il momento in cui e' stato inserito.
 *  - Ogni volta che l'app si apre vengono aggiunti i movimenti maturati dall'ultimo controllo:
 *    stipendio (giorno configurabile), tredicesima e quattordicesima nei loro mesi, spese fisse
 *    alle loro date e frequenze. Ogni movimento ha una chiave univoca (es. "fissa:id:2026-11"),
 *    quindi non puo' essere registrato due volte, anche se l'app resta chiusa per mesi.
 *  - Le spese approvate scalano dal saldo all'approvazione, o alla loro data se e' futura.
 *  - Una rettifica manuale diventa il nuovo punto di partenza: contano solo i movimenti successivi,
 *    quindi l'accredito del mese dopo arriva normalmente.
 */
public final class Saldo {

    public static final int STIPENDIO = 0, TREDICESIMA = 1, QUATTORDICESIMA = 2, FISSA = 3,
            SPESA = 4, RIMBORSO = 5, RETTIFICA = 6, RIPAGATA = 7;

    public static final class Movimento {
        public String chiave;
        public long ts;
        public double importo;
        public int tipo;
        public String nome;
        public String rif; // id spesa fissa o categoria della spesa
        /** Quando il movimento e' stato registrato nell'app (0 = dati vecchi). */
        public long registrato;

        public Movimento(String chiave, long ts, double importo, int tipo, String nome, String rif) {
            this.chiave = chiave;
            this.ts = ts;
            this.importo = Math.round(importo * 100.0) / 100.0;
            this.tipo = tipo;
            this.nome = nome;
            this.rif = rif;
        }
    }

    /** Regole che generano i movimenti automatici. */
    public static final class Regole {
        public int giornoStipendio = 1;
        public double nettoMensilita;
        public int mensilita = 12;
        public int meseTredicesima = 12;
        public int meseQuattordicesima = 7;
        public List<SpesaFissa> fisse = new ArrayList<SpesaFissa>();
        public ZoneId zona = ZoneId.systemDefault();
    }

    private Saldo() {}

    // ================================================================== logica pura

    private static long inizioGiorno(YearMonth ym, int giorno, ZoneId z) {
        LocalDate d = ym.atDay(Math.min(Math.max(1, giorno), ym.lengthOfMonth()));
        return d.atStartOfDay(z).toInstant().toEpochMilli();
    }

    /** Movimenti automatici con momento in (da, a], in ordine cronologico. */
    public static List<Movimento> genera(long da, long a, Regole r) {
        List<Movimento> out = new ArrayList<Movimento>();
        if (a <= da) return out;
        YearMonth ym = YearMonth.from(java.time.Instant.ofEpochMilli(da).atZone(r.zona));
        YearMonth fine = YearMonth.from(java.time.Instant.ofEpochMilli(a).atZone(r.zona));
        for (int guardia = 0; !ym.isAfter(fine) && guardia < 1200; guardia++, ym = ym.plusMonths(1)) {
            String mese = ym.toString(); // 2026-11
            int m = ym.getMonthValue();
            long paga = inizioGiorno(ym, r.giornoStipendio, r.zona);
            if (r.nettoMensilita > 0) {
                aggiungi(out, da, a, new Movimento("stip:" + mese, paga, r.nettoMensilita, STIPENDIO, "Stipendio", null));
                if (r.mensilita >= 13 && m == r.meseTredicesima) {
                    aggiungi(out, da, a, new Movimento("13:" + ym.getYear(), paga, r.nettoMensilita, TREDICESIMA, "Tredicesima", null));
                }
                if (r.mensilita >= 14 && m == r.meseQuattordicesima) {
                    aggiungi(out, da, a, new Movimento("14:" + ym.getYear(), paga, r.nettoMensilita, QUATTORDICESIMA, "Quattordicesima", null));
                }
            }
            for (SpesaFissa f : r.fisse) {
                if (f.getImporto() <= 0 || !f.addebitoNelMese(m)) continue;
                long ts = inizioGiorno(ym, f.getGiorno(), r.zona);
                aggiungi(out, da, a, new Movimento("fissa:" + f.getId() + ":" + mese, ts, -f.getImporto(), FISSA, f.getNome(), f.getId()));
            }
        }
        Collections.sort(out, PER_DATA);
        return out;
    }

    private static void aggiungi(List<Movimento> out, long da, long a, Movimento m) {
        if (m.ts > da && m.ts <= a) out.add(m);
    }

    public static final Comparator<Movimento> PER_DATA = new Comparator<Movimento>() {
        @Override
        public int compare(Movimento x, Movimento y) {
            int c = Long.compare(x.ts, y.ts);
            if (c != 0) return c;
            return Integer.compare(x.tipo, y.tipo); // a parita' di giorno: prima gli accrediti
        }
    };

    /** Saldo = base + movimenti successivi alla base (le rettifiche sono solo storico). */
    public static double calcola(double base, long baseTs, List<Movimento> movimenti, long adesso) {
        double s = base;
        for (Movimento m : movimenti) {
            if (m.tipo != RETTIFICA && m.ts > baseTs && m.ts <= adesso) s += m.importo;
        }
        return Math.round(s * 100.0) / 100.0;
    }

    // ================================================================== stato salvato

    private static final String PREFS = "SalarioPrefs";
    private static final String K_ATTIVO = "saldo_attivo";
    private static final String K_BASE = "saldo_base";
    private static final String K_BASE_TS = "saldo_base_ts";
    private static final String K_CONTROLLO_TS = "saldo_controllo_ts";
    private static final String K_MOVIMENTI = "saldo_movimenti";
    public static final String K_GIORNO_STIPENDIO = "giorno_stipendio";
    public static final String K_MESE_13 = "mese_tredicesima";
    public static final String K_MESE_14 = "mese_quattordicesima";
    private static final int MAX_MOVIMENTI = 3000;
    private static final Gson GSON = new Gson();

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    public static boolean attivo(Context c) {
        return prefs(c).getBoolean(K_ATTIVO, false);
    }

    private static double base(Context c) {
        try {
            return Double.parseDouble(prefs(c).getString(K_BASE, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static List<Movimento> movimenti(Context c) {
        String json = prefs(c).getString(K_MOVIMENTI, null);
        List<Movimento> out = new ArrayList<Movimento>();
        if (json == null) return out;
        try {
            Type t = new TypeToken<List<Movimento>>() {}.getType();
            List<Movimento> l = GSON.fromJson(json, t);
            if (l != null) for (Movimento m : l) if (m != null && m.chiave != null) out.add(m);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static void salvaMovimenti(Context c, List<Movimento> l) {
        Collections.sort(l, PER_DATA);
        while (l.size() > MAX_MOVIMENTI) l.remove(0);
        prefs(c).edit().putString(K_MOVIMENTI, GSON.toJson(l)).apply();
    }

    public static Regole regole(Context c) {
        SpeseManager m = new SpeseManager(c);
        Stipendio st = m.stipendio();
        Regole r = new Regole();
        SharedPreferences p = prefs(c);
        r.giornoStipendio = p.getInt(K_GIORNO_STIPENDIO, 1);
        r.meseTredicesima = p.getInt(K_MESE_13, 12);
        r.meseQuattordicesima = p.getInt(K_MESE_14, 7);
        r.mensilita = (int) Math.round(st.mensilita);
        r.nettoMensilita = st.salario > 0 ? st.nettoPerMensilita() : 0;
        r.fisse = m.getSpeseFisse();
        return r;
    }

    /** Aggiunge i movimenti maturati dall'ultimo controllo. Da chiamare all'apertura delle schermate. */
    public static void aggiorna(Context c) {
        if (!attivo(c)) return;
        long adesso = System.currentTimeMillis();
        long ultimo = prefs(c).getLong(K_CONTROLLO_TS, adesso);
        if (adesso <= ultimo) return;
        List<Movimento> esistenti = movimenti(c);
        Set<String> chiavi = new HashSet<String>();
        for (Movimento m : esistenti) chiavi.add(m.chiave);
        boolean nuovi = false;
        for (Movimento m : genera(ultimo, adesso, regole(c))) {
            if (chiavi.add(m.chiave)) {
                esistenti.add(m);
                nuovi = true;
            }
        }
        if (nuovi) salvaMovimenti(c, esistenti);
        prefs(c).edit().putLong(K_CONTROLLO_TS, adesso).apply();
    }

    /** Momento dell'ultimo saldo inserito a mano (0 se mai). */
    public static long ultimaVerifica(Context c) {
        return prefs(c).getLong(K_BASE_TS, 0);
    }

    public static double saldo(Context c) {
        return calcola(base(c), prefs(c).getLong(K_BASE_TS, 0), movimenti(c), System.currentTimeMillis());
    }

    /** Imposta il saldo attuale (prima volta o rettifica). Non tocca gli accrediti futuri. */
    public static void imposta(Context c, double valore) {
        long adesso = System.currentTimeMillis();
        List<Movimento> l;
        if (!attivo(c)) {
            l = new ArrayList<Movimento>();
            l.add(new Movimento("ret:" + adesso, adesso, valore, RETTIFICA, "Saldo iniziale", null));
            prefs(c).edit().putLong(K_CONTROLLO_TS, adesso).apply();
        } else {
            aggiorna(c);
            double prima = saldo(c);
            l = movimenti(c);
            l.add(new Movimento("ret:" + adesso, adesso, valore - prima, RETTIFICA, "Rettifica", null));
        }
        salvaMovimenti(c, l);
        prefs(c).edit()
                .putBoolean(K_ATTIVO, true)
                .putString(K_BASE, String.valueOf(Math.round(valore * 100.0) / 100.0))
                .putLong(K_BASE_TS, adesso)
                .apply();
    }

    /**
     * Momento in cui i soldi di una spesa escono dal conto:
     *  - data automatica: adesso (all'approvazione);
     *  - data scelta a mano: quella data, passata o futura. Se e' precedente all'ultimo saldo
     *    inserito, quei soldi sono gia' dentro il saldo della banca e non vengono scalati di nuovo.
     */
    static long momentoAddebito(Spesa s, long adesso) {
        return s.getDataManuale() ? s.getDataCreazione() : adesso;
    }

    /** Spesa approvata: scala dal saldo alla sua data. */
    public static void registraSpesa(Context c, Spesa s) {
        if (!attivo(c)) return;
        aggiorna(c);
        List<Movimento> l = movimenti(c);
        String chiave = "spesa:" + s.getId();
        for (Movimento m : l) if (m.chiave.equals(chiave)) return;
        long adesso = System.currentTimeMillis();
        // pagata con i buoni pasto: dal saldo esce solo l'eventuale parte in contanti/carta
        double importo = s.pagataConBuoni() ? s.getContanti() : s.getImporto();
        if (importo <= 0) return;
        Movimento m = new Movimento(chiave, momentoAddebito(s, adesso), -importo, SPESA, s.getNome(), s.getCategoria());
        m.registrato = adesso;
        l.add(m);
        salvaMovimenti(c, l);
    }

    /** true se il movimento era gia' stato contato nel saldo prima dell'ultima rettifica. */
    private static boolean assorbitoNellaBase(Movimento m, long baseTs) {
        boolean registratoPrima = m.registrato == 0 || m.registrato <= baseTs;
        return m.ts <= baseTs && registratoPrima;
    }

    /** Data di una spesa approvata cambiata: sposto il suo addebito (se non e' gia' dentro il saldo inserito). */
    public static void spostaSpesa(Context c, Spesa s) {
        if (!attivo(c)) return;
        List<Movimento> l = movimenti(c);
        long baseTs = prefs(c).getLong(K_BASE_TS, 0);
        for (Movimento m : l) {
            if (!m.chiave.equals("spesa:" + s.getId())) continue;
            if (assorbitoNellaBase(m, baseTs)) return; // gia' compreso nel saldo che hai inserito
            long adesso = System.currentTimeMillis();
            m.ts = s.getDataManuale() ? s.getDataCreazione() : Math.max(m.ts, adesso);
            if (m.registrato == 0) m.registrato = adesso;
            salvaMovimenti(c, l);
            return;
        }
    }

    /**
     * Spesa approvata eliminata: se il suo addebito non era ancora dentro un saldo inserito a mano lo tolgo,
     * altrimenti (gia' compreso nel saldo inserito) registro un rimborso.
     */
    public static void annullaSpesa(Context c, String spesaId, String nome) {
        if (!attivo(c)) return;
        List<Movimento> l = movimenti(c);
        Movimento trovato = null;
        for (Movimento m : l) if (m.chiave.equals("spesa:" + spesaId)) trovato = m;
        if (trovato == null) return; // approvata prima di attivare il saldo: non era stata scalata
        long baseTs = prefs(c).getLong(K_BASE_TS, 0);
        if (!assorbitoNellaBase(trovato, baseTs)) {
            l.remove(trovato);
        } else {
            long adesso = System.currentTimeMillis();
            // l'addebito resta nello storico (e' gia' dentro il saldo inserito), il rimborso lo compensa
            Movimento r = new Movimento("rimborso:" + spesaId, adesso, -trovato.importo, RIMBORSO, nome, trovato.rif);
            r.registrato = adesso;
            l.add(r);
        }
        salvaMovimenti(c, l);
    }

    /** Spesa divisa ripagata dall'altra persona: accredita {@code importo} sul saldo adesso. */
    public static boolean registraRipagata(Context c, Spesa s, double importo, String nome) {
        if (!attivo(c) || importo <= 0) return false;
        aggiorna(c);
        List<Movimento> l = movimenti(c);
        String chiave = "ripagata:" + s.getId();
        for (Movimento m : l) if (m.chiave.equals(chiave)) return true;
        long adesso = System.currentTimeMillis();
        Movimento m = new Movimento(chiave, adesso, importo, RIPAGATA, nome, s.getCategoria());
        m.registrato = adesso;
        l.add(m);
        salvaMovimenti(c, l);
        return true;
    }

    /** Annulla l'accredito di una spesa ripagata (tolto, o compensato se gia' dentro un saldo inserito a mano). */
    public static void annullaRipagata(Context c, String spesaId, String nome) {
        if (!attivo(c)) return;
        List<Movimento> l = movimenti(c);
        Movimento trovato = null;
        for (Movimento m : l) if (m.chiave.equals("ripagata:" + spesaId)) trovato = m;
        if (trovato == null) return;
        long baseTs = prefs(c).getLong(K_BASE_TS, 0);
        if (!assorbitoNellaBase(trovato, baseTs)) {
            l.remove(trovato);
        } else {
            long adesso = System.currentTimeMillis();
            Movimento r = new Movimento("annulla_ripagata:" + spesaId + ":" + adesso, adesso, -trovato.importo, RIMBORSO, nome, trovato.rif);
            r.registrato = adesso;
            l.add(r);
        }
        salvaMovimenti(c, l);
    }

    /** Prossimi movimenti previsti (non ancora avvenuti), entro {@code giorni}. */
    public static List<Movimento> prossimi(Context c, int giorni) {
        long adesso = System.currentTimeMillis();
        long fine = adesso + giorni * 86_400_000L;
        List<Movimento> out = genera(adesso, fine, regole(c));
        // spese approvate con data futura: gia' registrate, scaleranno alla loro data
        for (Movimento m : movimenti(c)) if (m.ts > adesso && m.ts <= fine) out.add(m);
        Collections.sort(out, PER_DATA);
        return out;
    }

    /** {saldo previsto subito prima del prossimo stipendio, momento del prossimo stipendio} o null. */
    public static double[] previstoAlProssimoStipendio(Context c) {
        List<Movimento> futuri = prossimi(c, 62);
        double s = saldo(c);
        for (Movimento m : futuri) {
            if (m.tipo == STIPENDIO) return new double[]{Math.round(s * 100.0) / 100.0, m.ts};
            if (m.tipo != TREDICESIMA && m.tipo != QUATTORDICESIMA) s += m.importo;
        }
        return null;
    }
}
