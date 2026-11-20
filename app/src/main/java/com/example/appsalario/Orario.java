package com.example.appsalario;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.ZoneId;

/**
 * Orario di lavoro: inizio/fine giornata, pausa pranzo, giorni lavorativi, festivi nazionali.
 * Serve a due cose:
 *  - ore lavorate al giorno (fine - inizio - pausa), quindi il valore della tua ora;
 *  - far avanzare il countdown del ripago solo durante le ore di lavoro effettive.
 *
 * Gli orari sono in minuti dalla mezzanotte. Se la fine e' prima dell'inizio il turno
 * prosegue nel giorno dopo (turno notturno).
 */
public final class Orario {
    public int inizio = 9 * 60;
    public int fine = 18 * 60;
    public boolean pausaAttiva = true;
    public int pausaInizio = 13 * 60;
    public int pausaFine = 14 * 60;
    /** bit 0 = lunedi' ... bit 6 = domenica. */
    public int giorni = 0b0011111;
    public boolean escludiFestivi = true;

    private static final long MIN = 60_000L;
    private static final int MAX_GIORNI = 366 * 30; // limite di sicurezza

    private transient ZoneId zona = ZoneId.systemDefault();

    // ------------------------------------------------------------------ durata giornata

    private int fineAssoluta() {
        return fine > inizio ? fine : fine + 1440;
    }

    /** Pausa normalizzata dentro il turno, o null se non valida/disattivata. */
    private int[] pausa() {
        if (!pausaAttiva) return null;
        int a = pausaInizio, b = pausaFine;
        if (a < inizio) a += 1440;
        if (b < a) b += 1440;
        if (a <= inizio || b >= fineAssoluta() || b <= a) return null;
        return new int[]{a, b};
    }

    public boolean pausaValida() {
        return !pausaAttiva || pausa() != null;
    }

    public int minutiGiorno() {
        int tot = fineAssoluta() - inizio;
        int[] p = pausa();
        if (p != null) tot -= p[1] - p[0];
        return Math.max(0, tot);
    }

    public double oreGiorno() {
        return minutiGiorno() / 60.0;
    }

    public int numeroGiorni() {
        return Integer.bitCount(giorni & 0x7F);
    }

    public double oreSettimana() {
        return oreGiorno() * numeroGiorni();
    }

    /** Giorni lavorativi medi al mese nell'anno indicato (festivi esclusi se attivo). */
    public double giorniLavorativiMese(int anno) {
        int n = 0;
        for (LocalDate d = LocalDate.of(anno, 1, 1); d.getYear() == anno; d = d.plusDays(1)) if (lavorativo(d)) n++;
        return n / 12.0;
    }

    public boolean valido() {
        return minutiGiorno() > 0 && numeroGiorni() > 0;
    }

    // ------------------------------------------------------------------ calendario

    public boolean lavorativo(LocalDate d) {
        int bit = d.getDayOfWeek().getValue() - 1; // lunedi' = 0
        if ((giorni & (1 << bit)) == 0) return false;
        return !(escludiFestivi && festivo(d));
    }

    /** Festivi nazionali italiani (incluso Lunedi' dell'Angelo). */
    public static boolean festivo(LocalDate d) {
        MonthDay md = MonthDay.from(d);
        int m = md.getMonthValue(), g = md.getDayOfMonth();
        if ((m == 1 && (g == 1 || g == 6)) || (m == 4 && g == 25) || (m == 5 && g == 1) || (m == 6 && g == 2)
                || (m == 8 && g == 15) || (m == 11 && g == 1) || (m == 12 && (g == 8 || g == 25 || g == 26))) {
            return true;
        }
        return d.equals(pasqua(d.getYear()).plusDays(1));
    }

    /** Pasqua (algoritmo di Meeus/Jones/Butcher, calendario gregoriano). */
    static LocalDate pasqua(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mese = (h + l - 7 * m + 114) / 31, giorno = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(y, mese, giorno);
    }

    private long ms(LocalDate giorno, int minuti) {
        // ora locale (non durata dalla mezzanotte): nei giorni del cambio d'ora le 9:00 restano le 9:00
        return giorno.atStartOfDay().plusMinutes(minuti).atZone(zona).toInstant().toEpochMilli();
    }

    /** Intervalli di lavoro [da, a) del turno che inizia nel giorno indicato. */
    private long[][] intervalli(LocalDate giorno) {
        if (!lavorativo(giorno)) return new long[0][];
        int[] p = pausa();
        int f = fineAssoluta();
        if (p == null) return new long[][]{{ms(giorno, inizio), ms(giorno, f)}};
        return new long[][]{{ms(giorno, inizio), ms(giorno, p[0])}, {ms(giorno, p[1]), ms(giorno, f)}};
    }

    private LocalDate data(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zona).toLocalDate();
    }

    /**
     * Momento in cui, partendo da {@code da}, si completano {@code ore} ore di lavoro.
     */
    public long fineLavoro(long da, double ore) {
        long restante = Math.round(ore * 3_600_000.0);
        if (restante <= 0) return da;
        if (!valido()) return da + restante;
        LocalDate g = data(da).minusDays(1); // un turno notturno iniziato ieri puo' essere ancora in corso
        for (int n = 0; n < MAX_GIORNI; n++, g = g.plusDays(1)) {
            for (long[] iv : intervalli(g)) {
                long a = Math.max(iv[0], da);
                if (a >= iv[1]) continue;
                long disponibile = iv[1] - a;
                if (restante <= disponibile) return a + restante;
                restante -= disponibile;
            }
        }
        return da + restante; // non dovrebbe succedere
    }

    /** Millisecondi di lavoro effettivo fra {@code da} e {@code a}. */
    public long lavoratoTra(long da, long a) {
        if (a <= da) return 0;
        long tot = 0;
        LocalDate g = data(da).minusDays(1), ultimo = data(a);
        for (int n = 0; n < MAX_GIORNI && !g.isAfter(ultimo); n++, g = g.plusDays(1)) {
            for (long[] iv : intervalli(g)) {
                long x = Math.max(iv[0], da), y = Math.min(iv[1], a);
                if (y > x) tot += y - x;
            }
        }
        return tot;
    }

    /** true se in questo momento sei in orario di lavoro. */
    public boolean alLavoro(long adesso) {
        return lavoratoTra(adesso, adesso + MIN) > 0;
    }

    // ------------------------------------------------------------------ testo

    public static String hhmm(int minuti) {
        minuti = ((minuti % 1440) + 1440) % 1440;
        return String.format(java.util.Locale.ITALY, "%02d:%02d", minuti / 60, minuti % 60);
    }

    public static final String[] SIGLE = {"L", "M", "M", "G", "V", "S", "D"};

    static DayOfWeek giorno(int bit) {
        return DayOfWeek.of(bit + 1);
    }
}
