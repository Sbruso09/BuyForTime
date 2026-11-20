package com.example.appsalario;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SpeseManager {
    private static final String PREFS_NAME = "SalarioPrefs";
    private static final String KEY_SPESE = "spese";
    private static final String KEY_SALARIO = "salario";
    private static final String KEY_TIPO = "tipo";
    private static final String KEY_ORE_GIORNO = "ore_giorno";
    private static final String KEY_GIORNI_MESE = "giorni_mese";
    private static final String KEY_MESI_ANNO = "mesi_anno";
    private static final String KEY_SPESE_FISSE = "spese_fisse";

    /** Anno e mese medi (calendario gregoriano). */
    public static final double ANNO_MS = 365.2425 * 24.0 * 3600.0 * 1000.0;
    public static final double MESE_MS = ANNO_MS / 12.0;

    private final Context context;
    private final Gson gson = new Gson();
    private final SharedPreferences prefs;

    public SpeseManager(Context context) {
        this.context = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, 0);
    }

    // ---------------------------------------------------------------- spese

    public List<Spesa> getSpese() {
        String json = prefs.getString(KEY_SPESE, null);
        List<Spesa> out = new ArrayList<Spesa>();
        if (json == null) return out;
        try {
            Type type = new TypeToken<List<Spesa>>() {}.getType();
            List<Spesa> lette = gson.fromJson(json, type);
            if (lette != null) {
                for (Spesa s : lette) if (s != null && s.getId() != null) out.add(s);
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private void saveSpese(List<Spesa> spese) {
        prefs.edit().putString(KEY_SPESE, gson.toJson(spese)).apply();
    }

    private static int indiceDi(List<Spesa> spese, String id) {
        for (int i = 0; i < spese.size(); i++) {
            if (spese.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    /**
     * Aggiunge una spesa. {@code data} e' il momento della spesa: adesso se automatica,
     * altrimenti la data scelta (passata o futura).
     */
    public boolean addSpesa(String nome, float importo, long data, boolean dataManuale, String categoria,
                            boolean tracciaUtilizzi) {
        Float tariffa = calcolaTariffaOraria();
        if (tariffa == null) return false;
        float ore = importo / tariffa;
        Spesa spesa = new Spesa(UUID.randomUUID().toString(), nome, importo, ore, data,
                dataManuale, durataDaOre(ore));
        spesa.setCategoria(categoria);
        spesa.setTracciaUtilizzi(tracciaUtilizzi);
        List<Spesa> spese = getSpese();
        spese.add(0, spesa);
        saveSpese(spese);
        return true;
    }

    /** Spesa importata dall'app Buoni Pasto: modo di pagamento, buoni usati e parte in contanti. */
    public void impostaPagamento(String id, String pagamento, double buoni, float contanti, String origine) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return;
        Spesa s = spese.get(i);
        s.setPagamento(pagamento);
        s.setBuoniUsati(buoni);
        s.setContanti(contanti);
        s.setOrigine(origine);
        saveSpese(spese);
    }

    /** Spesa divisa: l'altra persona ha ridato la sua meta' (true = accredita sul saldo, false = annulla). */
    public boolean impostaRipagata(String id, boolean ripagata) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return false;
        Spesa s = spese.get(i);
        if (ripagata == s.getRipagata()) return true;
        if (ripagata) {
            String nome = (Lingua.inglese() ? "Paid back: " : "Ripagata: ") + s.getNome();
            if (!Saldo.registraRipagata(context, s, Math.round(s.getImporto() * 50.0) / 100.0, nome)) return false;
        } else {
            Saldo.annullaRipagata(context, id, s.getNome());
        }
        s.setRipagata(ripagata);
        saveSpese(spese);
        return true;
    }

    public void deleteSpesa(String id) {
        annullaNotifica(id);
        annullaRipago(id);
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i != -1) {
            if (spese.get(i).getApprovata()) Saldo.annullaSpesa(context, id, spese.get(i).getNome());
            if (spese.get(i).pagataConBuoni()) SaldoBuoni.restituisci(context, spese.get(i).getBuoniUsati());
            if (spese.get(i).getRipagata()) Saldo.annullaRipagata(context, id, spese.get(i).getNome());
            spese.remove(i);
            saveSpese(spese);
        }
    }

    public void approvaSpesa(String id) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return;
        Spesa s = spese.get(i);
        s.setApprovata(true);
        s.setDataApprovazione(System.currentTimeMillis());
        if (s.getDurataRipagoMs() <= 0) s.setDurataRipagoMs(durataDaOre(s.getOreNecessarie()));
        // spesa con data vecchia gia' ripagata al momento dell'approvazione: nessuna notifica
        s.setRipagoNotificato(Ripago.pianoDi(s, this).fine <= System.currentTimeMillis());
        // approvata: il promemoria "valuta questa spesa" non serve piu'
        s.setNotificaAttiva(false);
        s.setPromemoriaTs(0);
        saveSpese(spese);
        annullaNotifica(id);
        programmaRipago(s);
        Saldo.registraSpesa(context, s); // soldi scalati alla data della spesa
    }

    public void impostaData(String id, long data) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return;
        Spesa s = spese.get(i);
        s.setData(data, true);
        if (s.getApprovata()) {
            // nuova data: notifica solo se il nuovo countdown non e' gia' finito
            s.setRipagoNotificato(Ripago.pianoDi(s, this).fine <= System.currentTimeMillis());
        }
        saveSpese(spese);
        programmaRipago(s);
        if (s.getApprovata()) Saldo.spostaSpesa(context, s); // l'addebito segue la nuova data
    }

    public void impostaTracciaUtilizzi(String id, boolean attiva) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return;
        spese.get(i).setTracciaUtilizzi(attiva);
        saveSpese(spese);
    }

    /** Aggiunge (o toglie, con delta negativo) utilizzi; ritorna il nuovo totale. */
    public int registraUtilizzo(String id, int delta) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return 0;
        Spesa s = spese.get(i);
        s.setUtilizzi(s.getUtilizzi() + delta);
        saveSpese(spese);
        return s.getUtilizzi();
    }

    public void impostaCategoria(String id, String categoria) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return;
        spese.get(i).setCategoria(categoria);
        saveSpese(spese);
    }

    public void segnaNotificaInviata(String id) {
        if (id == null) return;
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return;
        spese.get(i).setNotificaAttiva(false);
        spese.get(i).setPromemoriaTs(0);
        saveSpese(spese);
    }

    // ---------------------------------------------------------------- notifiche

    private PendingIntent pendingNotifica(String id, Intent intent, int flagExtra) {
        return PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE | flagExtra);
    }

    public void impostaNotifica(Spesa spesa, long delayMillis) {
        long trigger = System.currentTimeMillis() + delayMillis;
        programmaPromemoria(spesa, trigger);
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, spesa.getId());
        if (i != -1) {
            spese.get(i).setNotificaAttiva(true);
            spese.get(i).setPromemoriaTs(trigger);
            saveSpese(spese);
        }
    }

    private void programmaPromemoria(Spesa spesa, long trigger) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra("SPESA_ID", spesa.getId());
        intent.putExtra("SPESA_NOME", spesa.getNome());
        intent.putExtra("SPESA_IMPORTO", spesa.getImporto());
        intent.putExtra("SPESA_ORE", spesa.getOreNecessarie());
        PendingIntent pi = pendingNotifica(spesa.getId(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    private void annullaNotifica(String id) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pi = pendingNotifica(id, intent, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            pi.cancel();
        }
    }

    // ---------------------------------------------------------------- notifica "ripagata"

    public static final String AZIONE_RIPAGO = "com.example.appsalario.RIPAGO";
    public static final String AZIONE_PROVA = "com.example.appsalario.PROVA";

    /** Notifica di prova tra 10 secondi, con lo stesso percorso (allarme + receiver) del ripago. */
    public void notificaDiProva() {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(AZIONE_PROVA);
        PendingIntent pi = PendingIntent.getBroadcast(context, 777, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        long trigger = System.currentTimeMillis() + 10_000L;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    private PendingIntent pendingRipago(String id, int flagExtra) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(AZIONE_RIPAGO);
        intent.putExtra("SPESA_ID", id);
        return PendingIntent.getBroadcast(context, (id + ":ripago").hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE | flagExtra);
    }

    private void annullaRipago(String id) {
        PendingIntent pi = pendingRipago(id, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }

    /** Programma (o annulla) la notifica di fine countdown di una singola spesa. */
    private void programmaRipago(Spesa s) {
        if (!s.getApprovata() || s.getRipagoNotificato()) {
            annullaRipago(s.getId());
            return;
        }
        long fine = Ripago.pianoDi(s, this).fine;
        long trigger = Math.max(fine, System.currentTimeMillis() + 1000L);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pendingRipago(s.getId(), PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        }
    }

    /**
     * Ricalcola ore di lavoro e durata di ripago di TUTTE le spese con il valore attuale
     * della tua ora (dopo aver salvato stipendio, mensilita', spese fisse o orario).
     * Le spese approvate tornano "da notificare" se il nuovo countdown non e' ancora finito.
     * Ritorna il numero di spese aggiornate.
     */
    public int ricalcolaSpese() {
        Float tariffa = calcolaTariffaOraria();
        if (tariffa == null || tariffa <= 0f) return 0;
        List<Spesa> spese = getSpese();
        if (spese.isEmpty()) return 0;
        Orario orario = orario();
        long adesso = System.currentTimeMillis();
        for (Spesa s : spese) {
            float ore = s.getImporto() / tariffa;
            s.setOreNecessarie(ore);
            s.setDurataRipagoMs(durataDaOre(ore));
            if (s.getApprovata()) {
                boolean finita = Ripago.pianoDi(s, orario).fine <= adesso;
                // countdown riaperto (es. stipendio piu' basso): la notifica tornera' alla nuova fine
                if (!finita) s.setRipagoNotificato(false);
                // gia' finito con le nuove ore ma mai notificato: lo segno senza notifica a raffica
                else if (!s.getRipagoNotificato()) s.setRipagoNotificato(true);
            }
        }
        saveSpese(spese);
        return spese.size();
    }

    /**
     * Riprogramma le notifiche di tutte le spese approvate (avvio app, riavvio telefono,
     * aggiornamento). Un countdown finito mentre l'allarme era perso viene notificato subito;
     * le spese approvate prima di questa versione e gia' ripagate vengono silenziate.
     */
    public void riprogrammaRipaghi() {
        List<Spesa> spese = getSpese();
        long adesso = System.currentTimeMillis();
        boolean modificate = false;
        for (Spesa s : spese) {
            if (!s.getApprovata() || s.getRipagoNotificato()) continue;
            if (s.getDataApprovazione() <= 0 && Ripago.pianoDi(s, this).fine <= adesso) {
                s.setRipagoNotificato(true);
                modificate = true;
            }
        }
        if (modificate) saveSpese(spese);
        for (Spesa s : spese) {
            programmaRipago(s);
            // promemoria persi col riavvio: li rimetto (se scaduti nel frattempo arrivano subito)
            if (!s.getApprovata() && s.getNotificaAttiva() && s.getPromemoriaTs() > 0) {
                programmaPromemoria(s, Math.max(s.getPromemoriaTs(), adesso + 5000L));
            }
        }
    }

    public Spesa getSpesa(String id) {
        if (id == null) return null;
        for (Spesa s : getSpese()) if (s.getId().equals(id)) return s;
        return null;
    }

    /** Chiamato dal receiver: true se la notifica va mostrata ora. */
    public boolean consumaNotificaRipago(String id) {
        List<Spesa> spese = getSpese();
        int i = indiceDi(spese, id);
        if (i == -1) return false;
        Spesa s = spese.get(i);
        if (!s.getApprovata() || s.getRipagoNotificato()) return false;
        if (Ripago.pianoDi(s, this).fine > System.currentTimeMillis() + 60000L) {
            programmaRipago(s); // allarme anticipato (data cambiata): riprogramma
            return false;
        }
        s.setRipagoNotificato(true);
        saveSpese(spese);
        return true;
    }

    // ---------------------------------------------------------------- calcoli

    public static final String KEY_FONDO_PCT = "fondo_pct";
    public static final String KEY_REGIONE_PIEMONTE = "regione_piemonte";
    public static final String KEY_REGIONALE_PCT = "regionale_pct";
    public static final String KEY_COMUNALE_PCT = "comunale_pct";

    private static final String KEY_ORARIO_INIZIO = "orario_inizio";
    private static final String KEY_ORARIO_FINE = "orario_fine";
    private static final String KEY_PAUSA_ATTIVA = "pausa_attiva";
    private static final String KEY_PAUSA_INIZIO = "pausa_inizio";
    private static final String KEY_PAUSA_FINE = "pausa_fine";
    private static final String KEY_GIORNI_LAVORO = "giorni_lavoro";
    private static final String KEY_ESCLUDI_FESTIVI = "escludi_festivi";

    /** Orario salvato. Prima volta: 9:00 + ore/giorno salvate + 1 h di pausa (13-14). */
    public Orario orario() {
        Orario o = new Orario();
        if (!prefs.contains(KEY_ORARIO_INIZIO)) {
            int minuti = Math.round(prefs.getFloat(KEY_ORE_GIORNO, 8f) * 60f);
            o.fine = Math.min(o.inizio + minuti + 60, 23 * 60 + 59);
            if (o.fine <= o.pausaFine) o.pausaAttiva = false;
            if (!o.pausaAttiva) o.fine = Math.min(o.inizio + minuti, 23 * 60 + 59);
            return o;
        }
        o.inizio = prefs.getInt(KEY_ORARIO_INIZIO, o.inizio);
        o.fine = prefs.getInt(KEY_ORARIO_FINE, o.fine);
        o.pausaAttiva = prefs.getBoolean(KEY_PAUSA_ATTIVA, o.pausaAttiva);
        o.pausaInizio = prefs.getInt(KEY_PAUSA_INIZIO, o.pausaInizio);
        o.pausaFine = prefs.getInt(KEY_PAUSA_FINE, o.pausaFine);
        o.giorni = prefs.getInt(KEY_GIORNI_LAVORO, o.giorni);
        o.escludiFestivi = prefs.getBoolean(KEY_ESCLUDI_FESTIVI, o.escludiFestivi);
        return o;
    }

    public void salvaOrario(Orario o) {
        prefs.edit()
                .putInt(KEY_ORARIO_INIZIO, o.inizio)
                .putInt(KEY_ORARIO_FINE, o.fine)
                .putBoolean(KEY_PAUSA_ATTIVA, o.pausaAttiva)
                .putInt(KEY_PAUSA_INIZIO, o.pausaInizio)
                .putInt(KEY_PAUSA_FINE, o.pausaFine)
                .putInt(KEY_GIORNI_LAVORO, o.giorni)
                .putBoolean(KEY_ESCLUDI_FESTIVI, o.escludiFestivi)
                .putFloat(KEY_ORE_GIORNO, (float) o.oreGiorno())
                .apply();
    }

    /** Parametri attualmente salvati. */
    public Stipendio stipendio() {
        Stipendio st = new Stipendio();
        st.tipo = prefs.getString(KEY_TIPO, Stipendio.TIPO_MENSILE);
        st.salario = prefs.getFloat(KEY_SALARIO, 0f);
        st.mensilita = prefs.getFloat(KEY_MESI_ANNO, 13f);
        Orario o = orario();
        st.oreGiorno = o.oreGiorno();
        st.giorniMese = o.giorniLavorativiMese(java.time.LocalDate.now().getYear());
        st.fondoPct = prefs.getFloat(KEY_FONDO_PCT, 0f);
        st.regionalePiemonte = prefs.getBoolean(KEY_REGIONE_PIEMONTE, true);
        st.regionalePct = prefs.getFloat(KEY_REGIONALE_PCT, 0f);
        st.comunalePct = prefs.getFloat(KEY_COMUNALE_PCT, 0.8f);
        st.fisseMensili = getTotaleSpeseFisse();
        return st;
    }

    public void salvaStipendio(Stipendio st) {
        prefs.edit()
                .putString(KEY_TIPO, st.tipo)
                .putFloat(KEY_SALARIO, (float) st.salario)
                .putFloat(KEY_MESI_ANNO, (float) st.mensilita)
                .putFloat(KEY_FONDO_PCT, (float) st.fondoPct)
                .putBoolean(KEY_REGIONE_PIEMONTE, st.regionalePiemonte)
                .putFloat(KEY_REGIONALE_PCT, (float) st.regionalePct)
                .putFloat(KEY_COMUNALE_PCT, (float) st.comunalePct)
                .apply();
    }

    public Float calcolaTariffaOraria() {
        double t = stipendio().tariffaOraria();
        return t > 0 ? Float.valueOf((float) t) : null;
    }

    /**
     * Ore di lavoro -> tempo di calendario: la spesa vale (ore / ore lavorate in un anno)
     * del netto disponibile annuo, quindi serve la stessa frazione di anno per ripagarla.
     */
    public long durataDaOre(float ore) {
        double oreAnno = stipendio().oreAnno();
        if (ore <= 0f || oreAnno <= 0) return 0L;
        return Math.round((ore / oreAnno) * ANNO_MS);
    }

    /** Durata del ripago: quella salvata, oppure calcolata per le spese create prima dell'aggiornamento. */
    public long durataRipagoMs(Spesa s) {
        return s.getDurataRipagoMs() > 0 ? s.getDurataRipagoMs() : durataDaOre(s.getOreNecessarie());
    }

    public boolean isSalarioConfigurato() {
        return prefs.getFloat(KEY_SALARIO, 0.0f) > 0.0f;
    }

    public List<SpesaFissa> getSpeseFisse() {
        String json = prefs.getString(KEY_SPESE_FISSE, null);
        List<SpesaFissa> out = new ArrayList<SpesaFissa>();
        if (json == null) return out;
        try {
            Type type = new TypeToken<List<SpesaFissa>>() {}.getType();
            List<SpesaFissa> lette = gson.fromJson(json, type);
            if (lette != null) {
                for (SpesaFissa f : lette) if (f != null) out.add(f);
            }
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    public void saveSpeseFisse(List<SpesaFissa> speseFisse) {
        prefs.edit().putString(KEY_SPESE_FISSE, gson.toJson(speseFisse)).apply();
    }

    /** Spese fisse espresse al mese (le bimestrali/annuali divise per la frequenza). */
    public float getTotaleSpeseFisse() {
        double tot = 0.0;
        for (SpesaFissa f : getSpeseFisse()) tot += f.importoMensile();
        return (float) tot;
    }

    /** Netto disponibile in un anno dopo le spese fisse. */
    public float getNettoDisponibile() {
        return (float) stipendio().disponibileAnnuo();
    }

    public float getOreGiorno() {
        return (float) orario().oreGiorno();
    }
}
