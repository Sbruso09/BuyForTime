package com.example.appsalario;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pianificazione del ripago delle spese approvate.
 *
 * Ogni spesa approvata ha il suo countdown indipendente, che avanza solo durante
 * l'orario di lavoro (niente notti, pause pranzo, giorni liberi e festivi).
 *
 * Inizio di una spesa:
 *  - data impostata a mano  -> quella data (passata: il countdown e' gia' avanzato;
 *                              futura: parte da quel giorno anche se gia' approvata)
 *  - data automatica        -> momento dell'approvazione
 *  - spese vecchie approvate prima dell'aggiornamento -> la loro data
 */
public final class Ripago {
    public static final int FUTURA = 0;
    public static final int IN_CODA = 1;
    public static final int IN_CORSO = 2;
    public static final int COMPLETATA = 3;

    public static final class Piano {
        public long inizioProprio;
        public long inizio;
        /** Momento in cui si completano le ore di lavoro necessarie, secondo l'orario. */
        public long fine;
        /** Ore di lavoro necessarie, in millisecondi. */
        public long durata;
        Orario orario;

        public int stato(long adesso) {
            if (adesso >= fine) return COMPLETATA;
            if (adesso >= inizio) return IN_CORSO;
            if (adesso >= inizioProprio) return IN_CODA;
            return FUTURA;
        }

        /** Avanzamento in ore di lavoro effettive (fermo fuori orario). */
        public float progresso(long adesso) {
            if (durata <= 0 || adesso >= fine) return 1f;
            if (adesso <= inizio) return 0f;
            long lavorato = orario != null ? orario.lavoratoTra(inizio, adesso) : adesso - inizio;
            return (float) Math.min(1.0, lavorato / (double) durata);
        }

        /** Tempo di calendario che manca alla fine. */
        public long restante(long adesso) {
            return Math.max(0L, fine - Math.max(adesso, inizio));
        }

        /** Ore di lavoro che mancano. */
        public long lavoroRestante(long adesso) {
            return Math.max(0L, durata - Math.round(progresso(adesso) * (double) durata));
        }

        /** true se ora sei fuori orario (countdown di lavoro in pausa). */
        public boolean inPausa(long adesso) {
            return orario != null && adesso >= inizio && adesso < fine && !orario.alLavoro(adesso);
        }
    }

    private Ripago() {}

    public static long inizioProprio(Spesa s) {
        if (s.getDataManuale()) return s.getDataCreazione();
        if (s.getDataApprovazione() > 0) return Math.max(s.getDataCreazione(), s.getDataApprovazione());
        return s.getDataCreazione();
    }

    public static Piano pianoDi(Spesa s, SpeseManager manager) {
        return pianoDi(s, manager.orario());
    }

    /** Il ripago dura le ore di lavoro della spesa, distribuite sull'orario di lavoro. */
    public static Piano pianoDi(Spesa s, Orario orario) {
        Piano p = new Piano();
        p.orario = orario;
        p.inizioProprio = inizioProprio(s);
        p.inizio = p.inizioProprio;
        double ore = Math.max(0f, s.getOreNecessarie());
        p.durata = Math.round(ore * 3_600_000.0);
        p.fine = orario.fineLavoro(p.inizio, ore);
        return p;
    }

    public static Map<String, Piano> pianifica(List<Spesa> spese, SpeseManager manager) {
        Orario orario = manager.orario();
        Map<String, Piano> piani = new HashMap<String, Piano>();
        for (Spesa s : spese) if (s.getApprovata()) piani.put(s.getId(), pianoDi(s, orario));
        return piani;
    }

    /** Ultima data di fine fra i piani (Long.MIN_VALUE se nessuno). */
    public static long fineTotale(Map<String, Piano> piani) {
        long max = Long.MIN_VALUE;
        for (Piano p : piani.values()) max = Math.max(max, p.fine);
        return max;
    }

    /** "3 g 04:12:09" oppure "04:12:09" sotto le 24 ore. */
    public static String formattaRestante(long ms) {
        long sec = (ms + 999L) / 1000L; // arrotonda per eccesso: non mostra 00:00:00 prima della fine
        long giorni = sec / 86400L;
        long ore = (sec % 86400L) / 3600L;
        long min = (sec % 3600L) / 60L;
        long s = sec % 60L;
        String hms = String.format(Locale.ITALY, "%02d:%02d:%02d", ore, min, s);
        return giorni > 0 ? giorni + (Lingua.inglese() ? " d " : " g ") + hms : hms;
    }

    public static String data(long millis) {
        return new SimpleDateFormat("d MMM yyyy", Lingua.locale()).format(new Date(millis));
    }

    public static String dataOra(long millis) {
        return new SimpleDateFormat("d MMM yyyy, HH:mm", Lingua.locale()).format(new Date(millis));
    }

    public static String ora(long millis) {
        return new SimpleDateFormat("HH:mm", Lingua.locale()).format(new Date(millis));
    }
}
