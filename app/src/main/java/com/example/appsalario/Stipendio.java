package com.example.appsalario;

/**
 * Parametri di stipendio e lavoro, e i conti che ne derivano.
 *
 * Modello: le ore lavorate in un anno sono i giorni lavorativi dell'anno (dall'orario:
 * giorni della settimana, festivi esclusi) x le ore della giornata (inizio-fine-pausa).
 * Tredicesima e quattordicesima (le "mensilita'") aumentano quanto incassi in un anno,
 * non quanto lavori: per questo alzano il valore della tua ora.
 */
public final class Stipendio {
    public static final String TIPO_MENSILE = "mensile";   // netto mensile in busta
    public static final String TIPO_ANNUALE = "annuale";   // netto annuo
    public static final String TIPO_RAL = "ral";           // lordo annuo

    public String tipo = TIPO_MENSILE;
    public double salario;
    public double mensilita = 13;
    public double oreGiorno = 8;
    /** Giorni lavorativi medi al mese, calcolati dall'orario (giorni della settimana e festivi). */
    public double giorniMese = 20;
    public double fondoPct = 0;
    public boolean regionalePiemonte = true;
    public double regionalePct = 0;
    public double comunalePct = 0.8;
    public double fisseMensili = 0;

    public BustaPaga.Risultato bustaPaga() {
        if (!TIPO_RAL.equals(tipo) || salario <= 0) return null;
        return BustaPaga.calcola(salario, fondoPct, regionalePiemonte, regionalePct, comunalePct);
    }

    /** Netto incassato in un anno, tutte le mensilita' comprese. */
    public double nettoAnnuo() {
        if (salario <= 0) return 0;
        if (TIPO_RAL.equals(tipo)) return bustaPaga().netto;
        if (TIPO_ANNUALE.equals(tipo)) return salario;
        return salario * Math.max(1, mensilita);
    }

    /** Netto medio di una mensilita' (come appare in busta nei mesi normali, circa). */
    public double nettoPerMensilita() {
        return nettoAnnuo() / Math.max(1, mensilita);
    }

    /** Quello che resta in un anno dopo le spese fisse (12 mesi solari). */
    public double disponibileAnnuo() {
        return nettoAnnuo() - fisseMensili * 12.0;
    }

    public double oreAnno() {
        return oreGiorno * giorniMese * 12.0;
    }

    /** Valore netto di un'ora di lavoro, al netto delle spese fisse. 0 se non calcolabile. */
    public double tariffaOraria() {
        double ore = oreAnno();
        double disp = disponibileAnnuo();
        return ore > 0 && disp > 0 ? disp / ore : 0;
    }
}
