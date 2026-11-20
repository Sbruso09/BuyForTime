package com.example.appsalario;

/**
 * Spesa fissa ricorrente. Sostituisce la vecchia classe Kotlin mantenendo gli stessi
 * nomi di campo (id, nome, importo) per leggere i dati gia' salvati.
 *
 * importo = quanto si paga ogni volta (ogni mese, ogni 2 mesi, ...).
 * Addebito: il giorno {@code giorno} dei mesi in cui (mese - meseRiferimento) e' multiplo
 * di {@code frequenza}. Un giorno oltre la fine del mese diventa l'ultimo giorno.
 */
public final class SpesaFissa {
    public static final int[] FREQUENZE = {1, 2, 3, 6, 12};
    public static final String[] NOMI_FREQUENZE = {"Mensile", "Bimestrale", "Trimestrale", "Semestrale", "Annuale"};

    private String id;
    private String nome;
    private float importo;
    /** Giorno del mese dell'addebito (1-31). 0 = vecchi dati -> 1. */
    private int giorno;
    /** Ogni quanti mesi (1, 2, 3, 6, 12). 0 = vecchi dati -> mensile. */
    private int frequenza;
    /** Un mese (1-12) in cui l'addebito avviene: ancora il ciclo per le frequenze > 1. */
    private int meseRiferimento;

    public SpesaFissa(String id, String nome, float importo) {
        this(id, nome, importo, 1, 1, 1);
    }

    public SpesaFissa(String id, String nome, float importo, int giorno, int frequenza, int meseRiferimento) {
        this.id = id;
        this.nome = nome;
        this.importo = importo;
        this.giorno = giorno;
        this.frequenza = frequenza;
        this.meseRiferimento = meseRiferimento;
    }

    public String getId() { return id; }
    public String getNome() { return nome == null ? "" : nome; }
    public float getImporto() { return importo; }
    public int getGiorno() { return giorno <= 0 ? 1 : Math.min(31, giorno); }
    public int getFrequenza() { return frequenza <= 0 ? 1 : frequenza; }
    public int getMeseRiferimento() { return meseRiferimento <= 0 ? 1 : Math.min(12, meseRiferimento); }

    /** Costo medio al mese (per il valore della tua ora). */
    public double importoMensile() {
        return importo / (double) getFrequenza();
    }

    /** true se nel mese indicato (1-12) c'e' un addebito. */
    public boolean addebitoNelMese(int mese) {
        int f = getFrequenza();
        return f == 1 || (((mese - getMeseRiferimento()) % f) + f) % f == 0;
    }

    /** Nomi delle frequenze nella lingua dell'app. */
    public static String[] nomiFrequenze(android.content.Context c) {
        android.content.Context l = Lingua.contesto(c);
        int[] ids = {R.string.freq_1, R.string.freq_2, R.string.freq_3, R.string.freq_6, R.string.freq_12};
        String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = l.getString(ids[i]);
        return out;
    }

    public static String nomeFrequenza(android.content.Context c, int f) {
        String[] n = nomiFrequenze(c);
        for (int i = 0; i < FREQUENZE.length; i++) if (FREQUENZE[i] == f) return n[i];
        return n[0];
    }

    public static String nomeFrequenza(int f) {
        for (int i = 0; i < FREQUENZE.length; i++) if (FREQUENZE[i] == f) return NOMI_FREQUENZE[i];
        return NOMI_FREQUENZE[0];
    }
}
