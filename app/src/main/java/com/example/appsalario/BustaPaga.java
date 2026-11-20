package com.example.appsalario;

/**
 * Stima del netto annuo da RAL per un lavoratore dipendente, regole 2026.
 *
 *  - INPS a carico del dipendente: 9,19% (+1% oltre la prima fascia pensionabile 56.224 EUR),
 *    entro il massimale contributivo 122.295 EUR.
 *  - Fondo pensione a carico del lavoratore: deducibile (max 5.164,57 EUR).
 *  - IRPEF 2026: 23% fino a 28.000, 33% fino a 50.000, 43% oltre.
 *  - Detrazione lavoro dipendente (art. 13 TUIR) + 65 EUR tra 25.000 e 35.000.
 *  - Taglio cuneo (L. 207/2024): somma esente fino a 20.000 di reddito (7,1% / 5,3% / 4,8%),
 *    ulteriore detrazione 1.000 EUR tra 20.000 e 32.000, decrescente fino a 40.000.
 *  - Trattamento integrativo 1.200 EUR fino a 15.000 (se l'imposta e' capiente).
 *  - Addizionale regionale (Piemonte 2026 a scaglioni, o aliquota fissa) e comunale,
 *    dovute solo se l'IRPEF netta e' positiva.
 *
 * Il risultato e' annuo: tredicesima e quattordicesima cambiano come il netto e' distribuito
 * nei mesi, non il totale (le imposte vengono conguagliate sull'anno).
 */
public final class BustaPaga {

    public static final double INPS_ALIQUOTA = 0.0919;
    public static final double INPS_AGGIUNTIVA = 0.01;
    public static final double INPS_PRIMA_FASCIA = 56224.0;
    public static final double INPS_MASSIMALE = 122295.0;
    public static final double FONDO_MAX_DEDUCIBILE = 5164.57;

    private static final double[] IRPEF_SOGLIE = {28000, 50000};
    private static final double[] IRPEF_ALIQUOTE = {0.23, 0.33, 0.43};

    /** Addizionale regionale Piemonte 2026. */
    public static final double[] PIEMONTE_SOGLIE = {15000, 28000, 50000};
    public static final double[] PIEMONTE_ALIQUOTE = {0.0162, 0.0268, 0.0331, 0.0333};

    public static final class Risultato {
        public double ral;
        public double inps;
        public double fondoPensione;
        public double imponibile;
        public double irpefLorda;
        public double detrazioneLavoro;
        public double ulterioreDetrazione;
        public double irpefNetta;
        public double trattamentoIntegrativo;
        public double sommaCuneo;
        public double addizionaleRegionale;
        public double addizionaleComunale;
        public double netto;

        public double aliquotaMedia() {
            return ral > 0 ? 1.0 - netto / ral : 0;
        }
    }

    private BustaPaga() {}

    /**
     * @param ral               retribuzione annua lorda
     * @param fondoPct          contributo al fondo pensione a carico del lavoratore, % della RAL (0 = nessuno)
     * @param regionalePiemonte true = scaglioni Piemonte 2026, false = aliquota fissa {@code regionalePct}
     * @param regionalePct      aliquota regionale fissa in %, usata se non Piemonte
     * @param comunalePct       aliquota comunale in % (Torino 0,8)
     */
    public static Risultato calcola(double ral, double fondoPct, boolean regionalePiemonte,
                                    double regionalePct, double comunalePct) {
        Risultato r = new Risultato();
        r.ral = Math.max(0, ral);

        double baseInps = Math.min(r.ral, INPS_MASSIMALE);
        r.inps = baseInps * INPS_ALIQUOTA + Math.max(0, baseInps - INPS_PRIMA_FASCIA) * INPS_AGGIUNTIVA;
        r.fondoPensione = Math.min(r.ral * Math.max(0, fondoPct) / 100.0, FONDO_MAX_DEDUCIBILE);

        double reddito = Math.max(0, r.ral - r.inps - r.fondoPensione);
        r.imponibile = reddito;

        r.irpefLorda = progressiva(reddito, IRPEF_SOGLIE, IRPEF_ALIQUOTE);
        r.detrazioneLavoro = detrazioneLavoroDipendente(reddito);
        r.ulterioreDetrazione = ulterioreDetrazioneCuneo(reddito);
        r.irpefNetta = Math.max(0, r.irpefLorda - r.detrazioneLavoro - r.ulterioreDetrazione);

        if (reddito <= 15000 && r.irpefLorda > r.detrazioneLavoro - 75.0) {
            r.trattamentoIntegrativo = 1200.0;
        }
        r.sommaCuneo = sommaEsenteCuneo(reddito);

        if (r.irpefNetta > 0) {
            r.addizionaleRegionale = regionalePiemonte
                    ? progressiva(reddito, PIEMONTE_SOGLIE, PIEMONTE_ALIQUOTE)
                    : reddito * Math.max(0, regionalePct) / 100.0;
            r.addizionaleComunale = reddito * Math.max(0, comunalePct) / 100.0;
        }

        r.netto = r.ral - r.inps - r.fondoPensione - r.irpefNetta
                - r.addizionaleRegionale - r.addizionaleComunale
                + r.trattamentoIntegrativo + r.sommaCuneo;
        return r;
    }

    static double progressiva(double reddito, double[] soglie, double[] aliquote) {
        double imposta = 0, precedente = 0;
        for (int i = 0; i < aliquote.length; i++) {
            double tetto = i < soglie.length ? soglie[i] : Double.MAX_VALUE;
            if (reddito <= precedente) break;
            imposta += (Math.min(reddito, tetto) - precedente) * aliquote[i];
            precedente = tetto;
        }
        return imposta;
    }

    static double detrazioneLavoroDipendente(double r) {
        double d;
        if (r <= 15000) d = 1955;
        else if (r <= 28000) d = 1910 + 1190 * (28000 - r) / 13000.0;
        else if (r <= 50000) d = 1910 * (50000 - r) / 22000.0;
        else d = 0;
        if (r > 25000 && r <= 35000) d += 65;
        return d;
    }

    static double ulterioreDetrazioneCuneo(double r) {
        if (r <= 20000) return 0;
        if (r <= 32000) return 1000;
        if (r <= 40000) return 1000 * (40000 - r) / 8000.0;
        return 0;
    }

    static double sommaEsenteCuneo(double r) {
        if (r <= 0 || r > 20000) return 0;
        if (r <= 8500) return r * 0.071;
        if (r <= 15000) return r * 0.053;
        return r * 0.048;
    }
}
