package com.example.appsalario;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Controlli prima di inserire o approvare una spesa.
 *
 * ROSSO (blocca)   - la spesa supera il saldo (attuale, o previsto alla data della spesa).
 * GIALLO (avvisa)  - dopo la spesa non copri le spese fisse in arrivo prima dello stipendio;
 *                  - ti resterebbe meno del N% del saldo (default 35%);
 *                  - importo sospetto (> 3 volte la spesa piu' alta, o > netto mensile);
 *                  - possibile doppione (stesso nome e importo nelle ultime 24 ore);
 *                  - costa piu' di N giorni di lavoro.
 * INFO             - saldo non impostato: nessun controllo sui soldi possibile.
 *
 * Le spese con data personalizzata nel passato non vengono controllate.
 * La parte di calcolo e' pura (nessuna dipendenza Android) per poterla testare.
 */
public final class Controlli {
    public static final int INFO = 0, GIALLO = 1, ROSSO = 2;

    public static final class Avviso {
        public final int livello;
        public final String titolo;
        public final String testo;
        /** SALDO, FISSE, RESIDUO, SOSPETTO, DOPPIONE, TEMPO, NO_SALDO */
        public final String codice;

        Avviso(String codice, int livello, String titolo, String testo) {
            this.codice = codice;
            this.livello = livello;
            this.titolo = titolo;
            this.testo = testo;
        }

        /** true per gli avvisi che dipendono dai soldi disponibili (e quindi dalla data). */
        public boolean suiSoldi() {
            return "SALDO".equals(codice) || "FISSE".equals(codice) || "RESIDUO".equals(codice);
        }
    }

    /** Tutto quello che serve per valutare una spesa. */
    public static final class Dati {
        public String nome;
        public double importo;
        public long adesso;
        /** Momento a cui confrontare il saldo: adesso, o la data futura della spesa. */
        public long dataRiferimento;
        public boolean dataPassata;
        public boolean approvazione;
        public String idEscluso;

        public boolean saldoAttivo;
        public double saldoOra;
        /** Movimenti automatici previsti da adesso in avanti (ordinati). */
        public List<Saldo.Movimento> futuri = new ArrayList<Saldo.Movimento>();
        public List<Spesa> spese = new ArrayList<Spesa>();

        public double nettoMensile;
        public double tariffaOraria;
        public double oreGiorno = 8;
        public double sogliaResiduoPct = 35;
        public double sogliaGiorniLavoro = 21;
        /** Converte ore di lavoro in testo ("3 giorni e 2 ore"). */
        public Testi testi = new Testi() {
            @Override
            public String s(String chiave, Object... argomenti) {
                StringBuilder b = new StringBuilder(chiave);
                for (Object o : argomenti) b.append(' ').append(o);
                return b.toString();
            }
        };
        public Formattatore tempo = new Formattatore() {
            @Override
            public String ore(double ore) {
                return Math.round(ore * 10) / 10.0 + " h";
            }
        };
    }

    public interface Formattatore {
        String ore(double ore);
    }

    /** Testi tradotti: chiave -> stringa formattata (in app arrivano dalle risorse "ctrl_*"). */
    public interface Testi {
        String s(String chiave, Object... argomenti);
    }

    private static final NumberFormat VALUTA = NumberFormat.getCurrencyInstance(Locale.ITALY);

    private Controlli() {}

    private static String eur(double v) {
        return VALUTA.format(v);
    }

    private static String giorno(long ms) {
        return new SimpleDateFormat("d MMM", Lingua.locale()).format(new Date(ms));
    }

    private static String lavoro(Dati d, double euro) {
        if (d.tariffaOraria <= 0) return null;
        return d.tempo.ore(euro / d.tariffaOraria);
    }

    /** Saldo previsto al momento indicato (movimenti futuri inclusi fino a quel momento). */
    public static double saldoAl(Dati d, long ts) {
        double s = d.saldoOra;
        if (ts <= d.adesso) return s;
        for (Saldo.Movimento m : d.futuri) if (m.ts <= ts) s += m.importo;
        return s;
    }

    /** Primo momento, dalla data della spesa in poi, in cui il saldo previsto raggiunge {@code obiettivo}, o -1. */
    public static long quandoArrivi(Dati d, double obiettivo) {
        long da = Math.max(d.adesso, d.dataRiferimento);
        double s = saldoAl(d, da);
        if (s >= obiettivo) return da;
        for (Saldo.Movimento m : d.futuri) {
            if (m.ts <= da) continue;
            s += m.importo;
            if (s >= obiettivo) return m.ts;
        }
        return -1;
    }

    /** {totale spese fisse, momento del prossimo stipendio (o -1)} dopo {@code da}. */
    public static double[] fissePrimaDelloStipendio(Dati d, long da, List<String> nomi) {
        double tot = 0;
        long limite = da + 31L * 86_400_000L;
        for (Saldo.Movimento m : d.futuri) {
            if (m.ts <= da) continue;
            if (m.tipo == Saldo.STIPENDIO) return new double[]{tot, m.ts};
            if (m.ts > limite) break;
            if (m.tipo == Saldo.FISSA) {
                tot += -m.importo;
                if (nomi != null && !nomi.contains(m.nome)) nomi.add(m.nome);
            }
        }
        return new double[]{tot, -1};
    }

    public static List<Avviso> valuta(Dati d) {
        List<Avviso> out = new ArrayList<Avviso>();
        if (d.dataPassata) return out; // spesa gia' fatta in passato: niente controlli
        Testi T = d.testi;

        // ---------------------------------------------------------------- soldi
        if (!d.saldoAttivo) {
            if (!d.approvazione) {
                out.add(new Avviso("NO_SALDO", INFO, T.s("t_no_saldo"), T.s("m_no_saldo")));
            }
        } else {
            boolean futura = d.dataRiferimento > d.adesso;
            double saldo = saldoAl(d, d.dataRiferimento);
            String rifSaldo = futura ? T.s("rif_futuro", giorno(d.dataRiferimento)) : T.s("rif_ora");
            double dopo = saldo - d.importo;

            if (dopo < 0) {
                double manca = -dopo;
                StringBuilder t = new StringBuilder(T.s("m_saldo", rifSaldo, eur(saldo), eur(d.importo), eur(manca)));
                String lav = lavoro(d, manca);
                if (lav != null) t.append(T.s("cioe", lav));
                t.append(".");
                long quando = quandoArrivi(d, d.importo);
                if (quando > Math.max(d.adesso, d.dataRiferimento)) t.append(T.s("ci_arrivi", giorno(quando)));
                out.add(new Avviso("SALDO", ROSSO, T.s("t_saldo"), t.toString()));
            } else {
                List<String> nomi = new ArrayList<String>();
                double[] fisse = fissePrimaDelloStipendio(d, d.dataRiferimento, nomi);
                if (fisse[0] > 0 && dopo < fisse[0]) {
                    double manca = fisse[0] - dopo;
                    String quando = fisse[1] > 0 ? T.s("prima_stipendio", giorno((long) fisse[1])) : T.s("prossimo_mese");
                    StringBuilder t = new StringBuilder(T.s("m_fisse", eur(dopo), quando, eur(fisse[0]), join(nomi), eur(manca)));
                    String lav = lavoro(d, manca);
                    if (lav != null) t.append(T.s("cioe", lav));
                    t.append(T.s("consiglio"));
                    out.add(new Avviso("FISSE", GIALLO, T.s("t_fisse"), t.toString()));
                }
                if (saldo > 0) {
                    double residuo = dopo / saldo * 100.0;
                    if (residuo < d.sogliaResiduoPct) {
                        out.add(new Avviso("RESIDUO", GIALLO, T.s("t_residuo"),
                                T.s("m_residuo", (int) Math.round(residuo), eur(dopo), eur(saldo), (int) Math.round(d.sogliaResiduoPct))));
                    }
                }
            }
        }

        // ---------------------------------------------------------------- solo all'inserimento
        if (!d.approvazione) {
            double massima = 0;
            for (Spesa s : d.spese) massima = Math.max(massima, s.getImporto());
            // il confronto con la spesa piu' alta ha senso solo con un minimo di storico
            boolean oltreMassima = d.spese.size() >= 5 && massima > 0 && d.importo > 3 * massima;
            boolean oltreNetto = d.nettoMensile > 0 && d.importo > d.nettoMensile;
            if (oltreMassima || oltreNetto) {
                String motivo = oltreNetto ? T.s("sospetto_netto", eur(d.nettoMensile)) : T.s("sospetto_max", eur(massima));
                out.add(new Avviso("SOSPETTO", GIALLO, T.s("t_sospetto"),
                        T.s("m_sospetto", eur(d.importo), motivo, eur(d.importo / 10.0))));
            }
            String n = d.nome == null ? "" : d.nome.trim();
            for (Spesa s : d.spese) {
                if (s.getId().equals(d.idEscluso)) continue;
                boolean recente = Math.abs(d.adesso - s.getDataCreazione()) <= 86_400_000L
                        || (s.getDataApprovazione() > 0 && d.adesso - s.getDataApprovazione() <= 86_400_000L);
                if (recente && s.getNome().trim().equalsIgnoreCase(n) && Math.abs(s.getImporto() - d.importo) < 0.005) {
                    out.add(new Avviso("DOPPIONE", GIALLO, T.s("t_doppione"), T.s("m_doppione", s.getNome(), eur(s.getImporto()))));
                    break;
                }
            }
        }

        // ---------------------------------------------------------------- tempo di lavoro
        if (d.tariffaOraria > 0 && d.oreGiorno > 0) {
            double ore = d.importo / d.tariffaOraria;
            double giorni = ore / d.oreGiorno;
            if (giorni > d.sogliaGiorniLavoro) {
                out.add(new Avviso("TEMPO", GIALLO, T.s("t_tempo"),
                        T.s("m_tempo", d.tempo.ore(ore), (int) Math.round(d.sogliaGiorniLavoro))));
            }
        }
        return out;
    }

    public static int livelloMassimo(List<Avviso> l) {
        int max = -1;
        for (Avviso a : l) max = Math.max(max, a.livello);
        return max;
    }

    private static String join(List<String> nomi) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < nomi.size() && i < 3; i++) {
            if (i > 0) b.append(", ");
            b.append(nomi.get(i));
        }
        if (nomi.size() > 3) b.append("…");
        return b.toString();
    }
}
