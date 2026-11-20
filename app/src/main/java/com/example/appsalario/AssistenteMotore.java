package com.example.appsalario;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Esegue le domande all'assistente indipendentemente dalla schermata: la risposta arriva anche
 * se cambi pagina o chiudi l'app (un servizio in primo piano tiene vivo il lavoro), viene salvata
 * nella chat e, se non stai guardando la chat, arriva la notifica "BuyForTime ha risposto".
 */
public final class AssistenteMotore {

    /** Avvisato (sul thread principale) a ogni cambiamento: testo parziale, fase, fine. */
    public interface Osservatore {
        void aggiornato();
    }

    private static final AssistenteMotore ISTANZA = new AssistenteMotore();
    private static final ExecutorService ESECUTORE = Executors.newCachedThreadPool();
    static final String CANALE_RISPOSTE = "assistente_risposte";
    static final int ID_RISPOSTA = 4242;

    public static AssistenteMotore get() {
        return ISTANZA;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Osservatore osservatore;
    private volatile boolean inCorso;
    private String parziale = "", fase = "";
    private long inizio;
    private int generazione;
    private volatile Gemini gemini;
    private volatile Groq groq;
    /** true mentre la schermata della chat e' visibile (niente notifica, la risposta si vede subito). */
    private boolean chatVisibile;

    private AssistenteMotore() {}

    public boolean inCorso() {
        return inCorso;
    }

    public String parziale() {
        return parziale;
    }

    public String fase() {
        return fase;
    }

    public long inizio() {
        return inizio;
    }

    public void setOsservatore(Osservatore o) {
        osservatore = o;
    }

    public void setChatVisibile(boolean v) {
        chatVisibile = v;
        if (v) {
            // stai guardando la chat: la notifica di una risposta gia' arrivata non serve piu'
            Context c = MainActivity.contestoApp();
            if (c != null) ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID_RISPOSTA);
        }
    }

    private void avvisa() {
        if (osservatore != null) osservatore.aggiornato();
    }

    /**
     * Avvia una domanda. Il messaggio dell'utente deve essere gia' salvato in {@link ChatStorico}.
     * @param conversazione messaggi della chat fino alla domanda compresa
     */
    public void avvia(Context contesto, final List<ChatStorico.Messaggio> conversazione, final String domanda, final String foto,
                      final String chiave, final String chiaveGroq) {
        if (inCorso) return;
        final Context ctx = contesto.getApplicationContext();
        inCorso = true;
        parziale = "";
        fase = "";
        inizio = System.currentTimeMillis();
        final int mia = ++generazione;
        AssistenteServizio.avvia(ctx); // tiene vivo il lavoro anche ad app chiusa
        avvisa();

        final List<ChatStorico.Messaggio> copia = new ArrayList<ChatStorico.Messaggio>(conversazione);
        ESECUTORE.execute(new Runnable() {
            @Override
            public void run() {
                ChatStorico.Messaggio risposta;
                try {
                    risposta = esegui(ctx, copia, domanda, foto, chiave, chiaveGroq, mia);
                } catch (Gemini.Errore e) {
                    if (e.tipo == Gemini.Errore.ANNULLATA) {
                        fine(ctx, mia, null);
                        return;
                    }
                    risposta = new ChatStorico.Messaggio(ChatStorico.ERRORE, e.getMessage());
                } catch (Throwable e) {
                    // qualsiasi errore imprevisto deve comunque arrivare in chat, mai lasciare l'attesa infinita
                    risposta = new ChatStorico.Messaggio(ChatStorico.ERRORE,
                            Lingua.contesto(ctx).getString(R.string.ai_err_storto, String.valueOf(e)));
                }
                fine(ctx, mia, risposta);
            }
        });
    }

    private void fine(final Context ctx, final int mia, final ChatStorico.Messaggio risposta) {
        if (risposta != null && mia == generazione) ChatStorico.aggiungi(ctx, risposta);
        main.post(new Runnable() {
            @Override
            public void run() {
                if (mia != generazione) return; // fermata nel frattempo: stato gia' azzerato da ferma()
                inCorso = false;
                parziale = "";
                fase = "";
                gemini = null;
                groq = null;
                AssistenteServizio.ferma(ctx);
                if (risposta != null && !chatVisibile) notifica(ctx, risposta);
                avvisa();
            }
        });
    }

    /** Ferma la domanda in corso. */
    public void ferma(Context c) {
        if (!inCorso) return;
        generazione++;
        final Gemini g = gemini;
        final Groq q = groq;
        gemini = null;
        groq = null;
        ESECUTORE.execute(new Runnable() {
            @Override
            public void run() {
                // la disconnessione non va fatta sul thread principale
                if (g != null) g.annulla();
                if (q != null) q.annulla();
            }
        });
        inCorso = false;
        parziale = "";
        fase = "";
        AssistenteServizio.ferma(c.getApplicationContext());
        avvisa();
    }

    // ------------------------------------------------------------------ lavoro

    private void impostaFase(final int mia, final String t) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (mia != generazione || !inCorso) return;
                fase = t;
                avvisa();
            }
        });
    }

    private ChatStorico.Messaggio esegui(final Context ctx, List<ChatStorico.Messaggio> copia, String domanda, String foto,
                                         String chiave, String chiaveGroq, final int mia) throws Gemini.Errore {
        final JsonArray storia = ChatStorico.contesto(copia); // legge la foto: fuori dal thread principale
        final Context lc = Lingua.contesto(ctx);
        final Gemini g = new Gemini(new Gemini.Http(), chiave);
        gemini = g;
        boolean intelligente = "intelligente".equals(ctx.getSharedPreferences("SalarioPrefs", 0)
                .getString(AssistenteFragment.K_MODELLO, "veloce"));
        g.usaModelli(intelligente ? Gemini.MODELLI_INTELLIGENTE : Gemini.MODELLI_VELOCE);
        Assistente a = new Assistente(ctx);
        g.messaggi.rete = lc.getString(R.string.ai_err_rete);
        g.messaggi.vuota = lc.getString(R.string.ai_err_vuota);
        g.messaggi.nessuna = lc.getString(R.string.ai_err_nessuna);
        g.messaggi.passaggi = lc.getString(R.string.ai_err_passaggi);
        g.messaggi.chiave = lc.getString(R.string.ai_err_chiave);
        g.messaggi.quota = lc.getString(R.string.ai_err_quota);
        g.messaggi.servizio = lc.getString(R.string.ai_err_servizio);
        g.messaggi.errore = lc.getString(R.string.ai_err_generico);
        g.ascolto = new Gemini.Ascolto() {
            @Override
            public void testo(final String finora) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mia != generazione || !inCorso) return;
                        parziale = Assistente.separaProdotto(finora)[0];
                        avvisa();
                    }
                });
            }

            @Override
            public void stato(int f, String dettaglio) {
                impostaFase(mia, f == Gemini.INVIO ? lc.getString(R.string.ai_stato_invio, dettaglio)
                        : f == Gemini.RIPROVO ? lc.getString(R.string.ai_stato_riprovo, dettaglio)
                        : lc.getString(R.string.ai_stato_dati));
            }
        };
        // con una foto o una domanda sull'usato: ricerca Google; altrimenti gli strumenti dell'app
        g.ricercaWeb = foto != null || Assistente.vuoleRicerca(domanda);
        String istr = foto != null ? a.istruzioniFoto() : g.ricercaWeb ? a.istruzioniRicerca() : a.istruzioni();
        String t;
        try {
            t = g.conversa(istr, storia, g.ricercaWeb ? null : Assistente.dichiarazioni(), a);
        } catch (Gemini.Errore eg) {
            // Gemini finito (o non disponibile): passo a Groq. Chiave Gemini errata o domanda fermata: no
            if (eg.tipo == Gemini.Errore.ANNULLATA || eg.tipo == Gemini.Errore.CHIAVE || mia != generazione) throw eg;
            g.ascolto.testo("");
            boolean quota = eg.tipo == Gemini.Errore.QUOTA;
            if (quota) impostaFase(mia, lc.getString(R.string.ai_stato_groq));
            Groq q = new Groq(new Groq.Http(), chiaveGroq);
            groq = q;
            q.messaggi = g.messaggi;
            q.erroreChiave = lc.getString(R.string.ai_err_chiave_groq);
            q.ascolto = quota ? null : g.ascolto; // lascio visibile l'avviso del cambio
            g.fonti.clear();
            try {
                JsonArray storiaGroq = ChatStorico.contesto(copia);
                t = q.conversa(istr, storiaGroq, Assistente.dichiarazioni(), a,
                        foto != null ? Groq.FOTO : g.ricercaWeb ? Groq.RICERCA : Groq.STRUMENTI);
            } finally {
                UsoAi.aggiungi(ctx, 0, q.richieste);
            }
        } finally {
            UsoAi.aggiungi(ctx, g.richieste, 0);
        }
        String[] diviso = Assistente.separaProdotto(t);
        t = diviso[0];
        if (!g.fonti.isEmpty()) {
            StringBuilder b = new StringBuilder(t).append("\n\n**").append(lc.getString(R.string.ai_fonti)).append("**");
            for (int i = 0; i < g.fonti.size() && i < 5; i++) b.append("\n- ").append(g.fonti.get(i)[0]);
            t = b.toString();
        }
        ChatStorico.Messaggio risposta = new ChatStorico.Messaggio(ChatStorico.AI, t);
        risposta.prodotto = diviso[1];
        return risposta;
    }

    // ------------------------------------------------------------------ notifica

    private static void notifica(Context ctx, ChatStorico.Messaggio m) {
        Context c = Lingua.contesto(ctx);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CANALE_RISPOSTE, c.getString(R.string.notif_ai_canale),
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(c.getString(R.string.notif_ai_canale_desc));
        nm.createNotificationChannel(ch);

        boolean errore = ChatStorico.ERRORE.equals(m.ruolo);
        String testo = m.testo == null ? "" : m.testo.replace("**", "").replaceAll("(?m)^- ", "• ").trim();
        Intent apri = new Intent(c, MainActivity.class);
        apri.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        apri.putExtra(MainActivity.EXTRA_APRI_ASSISTENTE, true);
        PendingIntent pi = PendingIntent.getActivity(c, ID_RISPOSTA, apri,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(c, CANALE_RISPOSTE)
                .setSmallIcon(R.drawable.ic_auto_awesome)
                .setContentTitle(c.getString(errore ? R.string.notif_ai_errore : R.string.notif_ai_risposta))
                .setContentText(testo)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(testo.length() > 600 ? testo.substring(0, 600) + "…" : testo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(ID_RISPOSTA, n);
    }
}
