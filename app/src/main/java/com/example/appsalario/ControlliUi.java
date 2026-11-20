package com.example.appsalario;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.util.List;

/** Prepara i dati per {@link Controlli} e mostra il popup rosso/giallo/info. */
public final class ControlliUi {
    public static final String K_SOGLIA_RESIDUO = "soglia_residuo_pct";
    public static final String K_SOGLIA_GIORNI = "soglia_giorni_lavoro";
    public static final int GIALLO = 0xFFFFD60A;

    public interface Esito {
        /** L'utente vuole procedere (inserire/approvare). */
        void prosegui();

        /** L'utente vuole rivedere la spesa (il form resta aperto). */
        void rivedi();

        /** L'utente rinuncia alla spesa. */
        void annulla();
    }

    private ControlliUi() {}

    public static Controlli.Dati dati(final Context ctx, String nome, double importo, long data, boolean dataManuale,
                                      boolean approvazione, String idEscluso) {
        SpeseManager m = new SpeseManager(ctx);
        Saldo.aggiorna(ctx);
        final Controlli.Dati d = new Controlli.Dati();
        d.nome = nome;
        d.importo = importo;
        d.adesso = System.currentTimeMillis();
        d.approvazione = approvazione;
        d.idEscluso = idEscluso;
        d.dataPassata = dataManuale && data < d.adesso - 60_000L;
        // i soldi escono alla data della spesa: per una data futura si confronta il saldo
        // previsto a quella data (sia all'inserimento sia all'approvazione); altrimenti il saldo di adesso
        d.dataRiferimento = (dataManuale && data > d.adesso) ? data : d.adesso;
        d.saldoAttivo = Saldo.attivo(ctx);
        if (d.saldoAttivo) {
            d.saldoOra = Saldo.saldo(ctx);
            d.futuri = Saldo.prossimi(ctx, 400);
        }
        d.spese = m.getSpese();
        Stipendio st = m.stipendio();
        d.nettoMensile = st.salario > 0 ? st.nettoPerMensilita() : 0;
        d.tariffaOraria = st.tariffaOraria();
        d.oreGiorno = m.orario().oreGiorno();
        SharedPreferences p = ctx.getSharedPreferences("SalarioPrefs", 0);
        d.sogliaResiduoPct = p.getInt(K_SOGLIA_RESIDUO, 35);
        d.sogliaGiorniLavoro = p.getInt(K_SOGLIA_GIORNI, 21);
        final Context loc = Lingua.contesto(ctx);
        d.testi = new Controlli.Testi() {
            @Override
            public String s(String chiave, Object... argomenti) {
                int id = loc.getResources().getIdentifier("ctrl_" + chiave, "string", loc.getPackageName());
                return id == 0 ? chiave : loc.getString(id, argomenti);
            }
        };
        final float oreGiorno = (float) d.oreGiorno;
        d.tempo = new Controlli.Formattatore() {
            @Override
            public String ore(double ore) {
                return TempoFormatter.INSTANCE.formatta(loc, (float) ore, oreGiorno);
            }
        };
        return d;
    }

    private static int colore(Context c, int livello) {
        if (livello == Controlli.ROSSO) return ContextCompat.getColor(c, R.color.danger);
        if (livello == Controlli.GIALLO) return GIALLO;
        return ContextCompat.getColor(c, R.color.accent);
    }

    private static int icona(int livello) {
        if (livello == Controlli.ROSSO) return R.drawable.ic_error;
        if (livello == Controlli.GIALLO) return R.drawable.ic_warning;
        return R.drawable.ic_info;
    }

    /**
     * Mostra il popup. Se non ci sono avvisi chiama subito {@code prosegui}.
     * Rosso: si puo' solo rivedere o rinunciare. Giallo: rivedere o procedere comunque.
     */
    public static void mostra(final Activity a, List<Controlli.Avviso> avvisi, boolean approvazione, final Esito esito) {
        mostra(a, avvisi, approvazione, esito, null);
    }

    /** @param contesto descrizione della spesa per "Chiedi all'assistente" (null = pulsante nascosto). */
    public static void mostra(final Activity a, final List<Controlli.Avviso> avvisi, boolean approvazione, final Esito esito,
                              final String contesto) {
        if (avvisi.isEmpty()) {
            esito.prosegui();
            return;
        }
        int livello = Controlli.livelloMassimo(avvisi);
        final Dialog dialog = new Dialog(a, R.style.Theme_App_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_controllo);
        dialog.setCancelable(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.88), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius(48);
        }
        int col = colore(a, livello);
        View cerchio = dialog.findViewById(R.id.cerchioControllo);
        cerchio.setBackgroundTintList(ColorStateList.valueOf(col));
        ImageView iv = dialog.findViewById(R.id.ivControllo);
        iv.setImageResource(icona(livello));
        iv.setImageTintList(ColorStateList.valueOf(livello == Controlli.GIALLO ? 0xFF1C1C1E : 0xFFFFFFFF));

        // titolo = avviso piu' grave; poi tutti gli avvisi
        Controlli.Avviso principale = avvisi.get(0);
        for (Controlli.Avviso av : avvisi) if (av.livello > principale.livello) principale = av;
        ((TextView) dialog.findViewById(R.id.tvTitoloControllo)).setText(principale.titolo);
        LinearLayout lista = dialog.findViewById(R.id.listaControlli);
        float d = a.getResources().getDisplayMetrics().density;
        boolean piu = avvisi.size() > 1;
        // prima l'avviso principale, poi gli altri in ordine di gravita'
        for (int lv = Controlli.ROSSO; lv >= Controlli.INFO; lv--) {
            for (Controlli.Avviso av : avvisi) {
                if (av.livello != lv) continue;
                lista.addView(voce(a, av, piu && av != principale, d));
            }
        }

        MaterialButton primario = dialog.findViewById(R.id.btnPrimarioControllo);
        MaterialButton secondario = dialog.findViewById(R.id.btnSecondarioControllo);
        Premibile.INSTANCE.applica(primario, secondario);
        primario.setBackgroundTintList(ColorStateList.valueOf(col));
        primario.setTextColor(livello == Controlli.GIALLO ? 0xFF1C1C1E : 0xFFFFFFFF);

        if (livello == Controlli.ROSSO) {
            if (approvazione) {
                primario.setText(R.string.ctrl_ho_capito);
                secondario.setVisibility(View.GONE);
                primario.setOnClickListener(chiudi(dialog, new Runnable() {
                    @Override
                    public void run() {
                        esito.annulla();
                    }
                }));
            } else {
                primario.setText(R.string.ctrl_modifica);
                secondario.setText(R.string.ctrl_annulla_spesa);
                secondario.setTextColor(ContextCompat.getColor(a, R.color.danger));
                primario.setOnClickListener(chiudi(dialog, new Runnable() {
                    @Override
                    public void run() {
                        esito.rivedi();
                    }
                }));
                secondario.setOnClickListener(chiudi(dialog, new Runnable() {
                    @Override
                    public void run() {
                        esito.annulla();
                    }
                }));
            }
        } else if (livello == Controlli.GIALLO) {
            primario.setText(approvazione ? R.string.ctrl_non_approvare : R.string.ctrl_rivedi);
            secondario.setText(approvazione ? R.string.ctrl_approva_comunque : R.string.ctrl_inserisci_comunque);
            primario.setOnClickListener(chiudi(dialog, new Runnable() {
                @Override
                public void run() {
                    esito.rivedi();
                }
            }));
            secondario.setOnClickListener(chiudi(dialog, new Runnable() {
                @Override
                public void run() {
                    esito.prosegui();
                }
            }));
        } else {
            primario.setText(R.string.ctrl_inserisci);
            secondario.setText(android.R.string.cancel);
            primario.setOnClickListener(chiudi(dialog, new Runnable() {
                @Override
                public void run() {
                    esito.prosegui();
                }
            }));
            secondario.setOnClickListener(chiudi(dialog, new Runnable() {
                @Override
                public void run() {
                    esito.rivedi();
                }
            }));
        }
        // "Chiedi all'assistente": solo per avvisi gialli/rossi e se la chiave e' impostata
        if (contesto != null && livello >= Controlli.GIALLO && a instanceof MainActivity && ChiaveAi.presente(a)) {
            TextView chiedi = new TextView(a);
            chiedi.setTextAppearance(R.style.TextAppearance_App_Body);
            chiedi.setTextColor(ContextCompat.getColor(a, R.color.accent));
            chiedi.setText(R.string.ctrl_chiedi_assistente);
            chiedi.setGravity(Gravity.CENTER);
            chiedi.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_auto_awesome, 0, 0, 0);
            chiedi.setCompoundDrawableTintList(ColorStateList.valueOf(ContextCompat.getColor(a, R.color.accent)));
            chiedi.setCompoundDrawablePadding(Math.round(6 * d));
            chiedi.setMinHeight(Math.round(48 * d));
            chiedi.setBackgroundResource(R.drawable.pill_background);
            chiedi.setOnClickListener(chiudi(dialog, new Runnable() {
                @Override
                public void run() {
                    esito.annulla(); // la spesa non viene inserita/approvata: se ne parla con l'assistente
                    ((MainActivity) a).apriAssistente(AssistenteFragment.domandaDaAvvisi(a, contesto, avvisi));
                }
            }));
            LinearLayout.LayoutParams lpc = new LinearLayout.LayoutParams(-1, -2);
            lpc.topMargin = Math.round(6 * d);
            ((LinearLayout) primario.getParent()).addView(chiedi, lpc);
        }
        dialog.show();
        a.getWindow().getDecorView().performHapticFeedback(
                livello == Controlli.ROSSO ? android.view.HapticFeedbackConstants.LONG_PRESS
                        : android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private static View.OnClickListener chiudi(final Dialog dialog, final Runnable poi) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                poi.run();
            }
        };
    }

    private static View voce(Context c, Controlli.Avviso av, boolean conTitolo, float d) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, Math.round(8 * d), 0, Math.round(4 * d));
        if (conTitolo) {
            ImageView iv = new ImageView(c);
            iv.setImageResource(icona(av.livello));
            iv.setImageTintList(ColorStateList.valueOf(colore(c, av.livello)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(18 * d), Math.round(18 * d));
            lp.topMargin = Math.round(1 * d);
            lp.setMarginEnd(Math.round(8 * d));
            r.addView(iv, lp);
        }
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        if (conTitolo) {
            TextView t = new TextView(c);
            t.setTextAppearance(R.style.TextAppearance_App_Headline);
            t.setTextSize(15);
            t.setText(av.titolo);
            col.addView(t);
        }
        TextView testo = new TextView(c);
        testo.setTextAppearance(R.style.TextAppearance_App_Subhead);
        testo.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
        testo.setText(av.testo);
        if (!conTitolo) testo.setGravity(Gravity.CENTER);
        testo.setFontFeatureSettings("tnum");
        col.addView(testo);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }
}
