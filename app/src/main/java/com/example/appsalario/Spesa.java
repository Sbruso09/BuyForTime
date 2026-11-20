package com.example.appsalario;

/**
 * Spesa. I nomi dei campi originali sono mantenuti per compatibilita' con i dati
 * gia' salvati (JSON Gson). I campi nuovi valgono 0/false sulle spese vecchie.
 */
public final class Spesa {
    private String id;
    private String nome;
    private float importo;
    private float oreNecessarie;
    /** Data della spesa (automatica = momento dell'inserimento, oppure scelta a mano). */
    private long dataCreazione;
    private boolean approvata;
    private boolean notificaAttiva;
    /** true se la data e' stata impostata manualmente. */
    private boolean dataManuale;
    /** Momento dell'approvazione (0 = sconosciuto, spese approvate prima dell'aggiornamento). */
    private long dataApprovazione;
    /** Durata di calendario per ripagare la spesa, fotografata alla creazione (0 = da calcolare). */
    private long durataRipagoMs;
    /** true quando la notifica "spesa ripagata" e' stata inviata (o non serve). */
    private boolean ripagoNotificato;
    /** Costo per utilizzo: attivo solo se scelto per questa spesa. */
    private boolean tracciaUtilizzi;
    private int utilizzi;
    /** Chiave della categoria (null = Altro). */
    private String categoria;
    /** Momento del promemoria "valuta questa spesa" (0 = nessuno), per riprogrammarlo dopo un riavvio. */
    private long promemoriaTs;

    /** Pagamento: null = saldo, "buoni" = buoni pasto (dall'app Buoni Pasto). */
    private String pagamento;
    /** Buoni pasto usati (possono essere mezzi se la spesa e' divisa). */
    private double buoniUsati;
    /** Parte pagata con soldi veri (contanti/carta) oltre ai buoni: scala dal saldo. */
    private float contanti;
    /** Id del movimento dell'app Buoni Pasto da cui e' stata importata (null = inserita qui). */
    private String origine;
    /** Spesa divisa: l'altra persona ha ridato la sua meta' in soldi (accreditata sul saldo). */
    private boolean ripagata;

    public Spesa(String id, String nome, float importo, float oreNecessarie, long dataCreazione,
                 boolean dataManuale, long durataRipagoMs) {
        this.id = id;
        this.nome = nome;
        this.importo = importo;
        this.oreNecessarie = oreNecessarie;
        this.dataCreazione = dataCreazione;
        this.dataManuale = dataManuale;
        this.durataRipagoMs = durataRipagoMs;
    }

    public String getId() { return id; }
    public String getNome() { return nome == null ? "" : nome; }
    public float getImporto() { return importo; }
    public float getOreNecessarie() { return oreNecessarie; }
    public long getDataCreazione() { return dataCreazione; }
    public boolean getApprovata() { return approvata; }
    public boolean getNotificaAttiva() { return notificaAttiva; }
    public boolean getDataManuale() { return dataManuale; }
    public long getDataApprovazione() { return dataApprovazione; }
    public long getDurataRipagoMs() { return durataRipagoMs; }

    public boolean getTracciaUtilizzi() { return tracciaUtilizzi; }
    public void setTracciaUtilizzi(boolean v) { tracciaUtilizzi = v; }
    public int getUtilizzi() { return utilizzi; }
    public void setUtilizzi(int v) { utilizzi = Math.max(0, v); }
    public String getCategoria() { return categoria; }
    public void setCategoria(String v) { categoria = v; }
    public boolean getRipagoNotificato() { return ripagoNotificato; }
    public void setRipagoNotificato(boolean v) { ripagoNotificato = v; }
    public void setOreNecessarie(float v) { oreNecessarie = v; }
    public void setApprovata(boolean v) { approvata = v; }
    public void setNotificaAttiva(boolean v) { notificaAttiva = v; }
    public void setDataApprovazione(long v) { dataApprovazione = v; }
    public void setDurataRipagoMs(long v) { durataRipagoMs = v; }
    public long getPromemoriaTs() { return promemoriaTs; }
    public void setPromemoriaTs(long v) { promemoriaTs = v; }
    public boolean pagataConBuoni() { return "buoni".equals(pagamento); }
    public String getPagamento() { return pagamento; }
    public void setPagamento(String v) { pagamento = v; }
    public double getBuoniUsati() { return buoniUsati; }
    public void setBuoniUsati(double v) { buoniUsati = Math.max(0, v); }
    public float getContanti() { return contanti; }
    public void setContanti(float v) { contanti = Math.max(0, v); }
    public String getOrigine() { return origine; }
    public void setOrigine(String v) { origine = v; }
    public boolean getRipagata() { return ripagata; }
    public void setRipagata(boolean v) { ripagata = v; }
    public void setData(long data, boolean manuale) { dataCreazione = data; dataManuale = manuale; }
}
