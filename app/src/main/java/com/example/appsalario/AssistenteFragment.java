package com.example.appsalario;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chat con l'assistente, in stile Messaggi di iOS. */
public final class AssistenteFragment extends Fragment {
    private static final ExecutorService ESECUTORE = Executors.newCachedThreadPool();

    private RecyclerView rv;
    private View intro, senzaChiave, barra, btnInvia;
    private EditText etDomanda;
    private LinearLayout rigaSuggerimenti;
    private final List<ChatStorico.Messaggio> messaggi = new ArrayList<ChatStorico.Messaggio>();
    private Adattatore adattatore;
    private boolean inAttesa;
    /** Testo della risposta mentre arriva (streaming). */
    private String parziale = "";
    /** Cosa sta facendo l'assistente (mostrato nella bolla di attesa) e da quando. */
    private String fase = "";
    private long inizioAttesa;
    /** Numero della domanda in corso: le risposte di domande interrotte vengono ignorate. */
    public static final String K_MODELLO = "ai_modello"; // "veloce" (Flash-Lite) o "intelligente" (Flash)
    private final Runnable orologio = new Runnable() {
        @Override
        public void run() {
            if (!inAttesa || !isAdded()) return;
            if (parziale.isEmpty()) adattatore.notifyItemChanged(messaggi.size());
            main.postDelayed(this, 1000);
        }
    };
    private final Handler main = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------------ foto
    /** Foto pronta da inviare (JPEG ridimensionato nella memoria dell'app), o null. */
    private String fotoInAttesa;
    private View boxAnteprima;
    private android.widget.ImageView ivAnteprima;
    private android.net.Uri uriScatto;
    private final androidx.activity.result.ActivityResultLauncher<String> galleria = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
            new androidx.activity.result.ActivityResultCallback<android.net.Uri>() {
                @Override
                public void onActivityResult(android.net.Uri uri) {
                    if (uri != null) preparaFoto(uri);
                }
            });
    private final androidx.activity.result.ActivityResultLauncher<android.net.Uri> fotocamera = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
            new androidx.activity.result.ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean ok) {
                    if (Boolean.TRUE.equals(ok) && uriScatto != null) preparaFoto(uriScatto);
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_assistente, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rv = view.findViewById(R.id.rvChat);
        intro = view.findViewById(R.id.introAssistente);
        senzaChiave = view.findViewById(R.id.boxSenzaChiave);
        barra = view.findViewById(R.id.barraAssistente);
        etDomanda = view.findViewById(R.id.etDomanda);
        btnInvia = view.findViewById(R.id.btnInvia);
        rigaSuggerimenti = view.findViewById(R.id.rigaSuggerimenti);
        boxAnteprima = view.findViewById(R.id.boxAnteprima);
        ivAnteprima = view.findViewById(R.id.ivAnteprima);
        ivAnteprima.setClipToOutline(true);
        ivAnteprima.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 12 * v.getResources().getDisplayMetrics().density);
            }
        });
        view.findViewById(R.id.btnTogliFoto).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togliFoto(true);
            }
        });
        View btnFoto = view.findViewById(R.id.btnFoto);
        Premibile.INSTANCE.applicaA(btnFoto);
        btnFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scegliFoto();
            }
        });

        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        adattatore = new Adattatore();
        rv.setAdapter(adattatore);
        rv.setItemAnimator(null);

        Premibile.INSTANCE.applicaA(btnInvia);
        btnInvia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inAttesa) ferma();
                else invia(etDomanda.getText().toString());
            }
        });
        view.findViewById(R.id.btnVaiImpostazioni).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity) requireActivity()).vaiAlleImpostazioni();
            }
        });
        applicaInsets(view);

        messaggi.addAll(ChatStorico.leggi(requireContext()));
        aggiorna(false);
        costruisciSuggerimenti();

        String domanda = ((MainActivity) requireActivity()).prendiDomandaInSospeso();
        if (domanda != null) invia(domanda);
    }

    @Override
    public void onPause() {
        super.onPause();
        AssistenteMotore.get().setOsservatore(null);
        AssistenteMotore.get().setChatVisibile(false); // da qui in poi la risposta arriva con una notifica
    }

    @Override
    public void onResume() {
        super.onResume();
        AssistenteMotore.get().setChatVisibile(true);
        AssistenteMotore.get().setOsservatore(new AssistenteMotore.Osservatore() {
            @Override
            public void aggiornato() {
                sincronizza();
            }
        });
        // la risposta puo' essere arrivata mentre eri altrove
        if (!AssistenteMotore.get().inCorso() && !inAttesa) {
            List<ChatStorico.Messaggio> salvati = ChatStorico.leggi(requireContext());
            if (salvati.size() != messaggi.size()) {
                messaggi.clear();
                messaggi.addAll(salvati);
                aggiorna(true);
            }
        }
        sincronizza();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).aggiornaTitoloBarra(getString(R.string.tab_assistente), 1f);
        }
        senzaChiave.setVisibility(ChiaveAi.complete(requireContext()) ? View.GONE : View.VISIBLE);
    }

    /** Barra di input sopra la tab bar, o sopra la tastiera quando e' aperta. */
    private void applicaInsets(View root) {
        final float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                int sotto = Math.max(ime.bottom, sys.bottom + Math.round(50.5f * d));
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) barra.getLayoutParams();
                lp.bottomMargin = sotto;
                barra.setLayoutParams(lp);
                final int alto = sys.top + Math.round(44 * d);
                rv.setPadding(rv.getPaddingLeft(), alto + Math.round(8 * d), rv.getPaddingRight(), rv.getPaddingBottom());
                barra.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded()) return;
                        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) barra.getLayoutParams();
                        rv.setPadding(rv.getPaddingLeft(), rv.getPaddingTop(), rv.getPaddingRight(),
                                barra.getHeight() + l.bottomMargin + Math.round(10 * d));
                        scorriInFondo();
                    }
                });
                return insets;
            }
        });
        ViewCompat.requestApplyInsets(root);
    }

    // ------------------------------------------------------------------ invio

    private void invia(String testo) {
        String d = testo == null ? "" : testo.trim();
        final String foto = fotoInAttesa;
        if (d.isEmpty() && foto != null) d = getString(R.string.ai_foto_domanda);
        final String domanda = d;
        if (domanda.isEmpty() || inAttesa || AssistenteMotore.get().inCorso()) return;
        final Context ctx = requireContext().getApplicationContext();
        final String chiave = ChiaveAi.leggi(ctx);
        final String chiaveGroq = ChiaveAi.leggi(ctx, ChiaveAi.GROQ);
        if (chiave == null || chiaveGroq == null) { // servono entrambe le chiavi
            senzaChiave.setVisibility(View.VISIBLE);
            return;
        }
        etDomanda.setText("");
        ChatStorico.Messaggio mu = new ChatStorico.Messaggio(ChatStorico.UTENTE, domanda);
        mu.immagine = foto;
        togliFoto(false);
        messaggi.add(mu);
        ChatStorico.aggiungi(ctx, mu);
        // la domanda prosegue anche se cambi pagina o chiudi l'app
        AssistenteMotore.get().avvia(ctx, messaggi, domanda, foto, chiave, chiaveGroq);
    }

    /** Allinea la chat allo stato del motore (domanda in corso, testo parziale, risposta arrivata). */
    private void sincronizza() {
        if (!isAdded() || adattatore == null) return;
        AssistenteMotore m = AssistenteMotore.get();
        boolean prima = inAttesa;
        boolean primoTesto = parziale.isEmpty() && !m.parziale().isEmpty();
        inAttesa = m.inCorso();
        parziale = m.parziale();
        fase = m.fase();
        inizioAttesa = m.inizio();
        if (prima && !inAttesa) {
            // risposta arrivata (o domanda fermata): la chat salvata e' la fonte di verita'
            messaggi.clear();
            messaggi.addAll(ChatStorico.leggi(requireContext()));
            main.removeCallbacks(orologio);
            aggiorna(true);
            costruisciSuggerimenti();
            return;
        }
        if (inAttesa && !prima) {
            main.removeCallbacks(orologio);
            main.postDelayed(orologio, 1000);
            aggiorna(true);
            return;
        }
        if (primoTesto) adattatore.notifyDataSetChanged();
        else adattatore.notifyItemChanged(messaggi.size());
        if (inAttesa) scorriInFondo();
    }

    // ------------------------------------------------------------------ foto

    private void scegliFoto() {
        if (inAttesa) return;
        List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
        azioni.add(new ActionSheet.Azione(getString(R.string.ai_scatta), false, new kotlin.jvm.functions.Function0<kotlin.Unit>() {
            @Override
            public kotlin.Unit invoke() {
                try {
                    java.io.File dir = new java.io.File(requireContext().getCacheDir(), "foto");
                    dir.mkdirs();
                    java.io.File f = new java.io.File(dir, "scatto.jpg");
                    uriScatto = androidx.core.content.FileProvider.getUriForFile(requireContext(),
                            requireContext().getPackageName() + ".fileprovider", f);
                    fotocamera.launch(uriScatto);
                } catch (RuntimeException e) {
                    Toast.makeText(requireContext(), R.string.ai_foto_errore, Toast.LENGTH_SHORT).show();
                }
                return kotlin.Unit.INSTANCE;
            }
        }));
        azioni.add(new ActionSheet.Azione(getString(R.string.ai_galleria), false, new kotlin.jvm.functions.Function0<kotlin.Unit>() {
            @Override
            public kotlin.Unit invoke() {
                try {
                    galleria.launch("image/*");
                } catch (RuntimeException e) {
                    Toast.makeText(requireContext(), R.string.ai_foto_errore, Toast.LENGTH_SHORT).show();
                }
                return kotlin.Unit.INSTANCE;
            }
        }));
        ActionSheet.INSTANCE.mostra(requireContext(), null, null, azioni);
    }

    /** Ridimensiona la foto (max 1280 px, JPEG) e la salva nella memoria dell'app, poi mostra l'anteprima. */
    private void preparaFoto(final android.net.Uri uri) {
        final Context ctx = requireContext().getApplicationContext();
        ESECUTORE.execute(new Runnable() {
            @Override
            public void run() {
                final String percorso = Foto.prepara(ctx, uri, ChatStorico.cartellaFoto(ctx));
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded()) return;
                        if (percorso == null) {
                            Toast.makeText(requireContext(), R.string.ai_foto_errore, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (fotoInAttesa != null) new java.io.File(fotoInAttesa).delete();
                        fotoInAttesa = percorso;
                        ivAnteprima.setImageBitmap(Foto.miniatura(percorso, 400));
                        boxAnteprima.setVisibility(View.VISIBLE);
                        etDomanda.requestFocus();
                    }
                });
            }
        });
    }

    private void togliFoto(boolean cancellaFile) {
        if (cancellaFile && fotoInAttesa != null) new java.io.File(fotoInAttesa).delete();
        fotoInAttesa = null;
        if (boxAnteprima != null) {
            boxAnteprima.setVisibility(View.GONE);
            ivAnteprima.setImageDrawable(null);
        }
    }

    /** Interrompe la domanda in corso (tasto invio trasformato in stop). */
    private void ferma() {
        AssistenteMotore.get().ferma(requireContext());
    }

    public void nuovaChat() {
        if (inAttesa) ferma();
        togliFoto(true);
        ChatStorico.cancella(requireContext());
        messaggi.clear();
        aggiorna(false);
        costruisciSuggerimenti();
    }

    private void aggiorna(boolean scorri) {
        intro.setVisibility(messaggi.isEmpty() && !inAttesa ? View.VISIBLE : View.GONE);
        btnInvia.setAlpha(1f);
        ((android.widget.ImageView) ((ViewGroup) btnInvia).getChildAt(0))
                .setImageResource(inAttesa ? R.drawable.ic_stop : R.drawable.ic_arrow_upward);
        btnInvia.setContentDescription(getString(inAttesa ? R.string.ai_ferma : R.string.assistente_invia));
        adattatore.notifyDataSetChanged();
        if (scorri) scorriInFondo();
    }

    private void scorriInFondo() {
        int n = adattatore.getItemCount();
        if (n > 0) rv.scrollToPosition(n - 1);
    }

    // ------------------------------------------------------------------ suggerimenti

    /** Pulsanti con le domande piu' utili in questo momento; quelli con "…" completano il campo. */
    private void costruisciSuggerimenti() {
        Context ctx = requireContext();
        rigaSuggerimenti.removeAllViews();
        List<String[]> s = new ArrayList<String[]>(); // {etichetta, testo, "1" se va completato}
        s.add(chip("usato", true)); // ricerca dei prezzi dell'usato, sempre per prima
        if (Saldo.attivo(ctx)) {
            double[] prev = Saldo.previstoAlProssimoStipendio(ctx);
            if (prev != null) {
                long giorni = (long) ((prev[1] - System.currentTimeMillis()) / 86_400_000L);
                if (prev[0] < 0) s.add(chip("sotto_zero", false));
                else if (giorni <= 3) s.add(chip("prima_stipendio", false));
            }
            s.add(chip("weekend", false));
        }
        s.add(chip("permettermi", true));
        s.add(chip("aspettare", true));
        s.add(chip("aob", true));
        s.add(chip("mese", false));
        s.add(chip("categorie", false));
        s.add(chip("ripago", false));
        s.add(chip("fissa", false));
        s.add(chip("obiettivo", true));
        boolean utilizzi = false;
        for (Spesa x : new SpeseManager(ctx).getSpese()) if (x.getTracciaUtilizzi()) utilizzi = true;
        if (utilizzi) s.add(chip("utilizzi", false));
        s.add(chip("aumento", true));
        s.add(chip("affitto", false));
        if (Stipendio.TIPO_RAL.equals(new SpeseManager(ctx).stipendio().tipo)) s.add(chip("busta", false));
        float d = getResources().getDisplayMetrics().density;
        for (final String[] x : s) {
            TextView chip = new TextView(ctx);
            chip.setTextAppearance(R.style.TextAppearance_App_Subhead);
            chip.setTextColor(ContextCompat.getColor(ctx, R.color.accent));
            chip.setBackgroundResource(R.drawable.pill_background);
            chip.setText(x[0]);
            chip.setSingleLine(true);
            int ph = Math.round(13 * d), pv = Math.round(7 * d);
            chip.setPadding(ph, pv, ph, pv);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("1".equals(x[2])) {
                        etDomanda.setText(x[1]);
                        int pos = x[1].indexOf(" €");
                        if (pos < 0) pos = x[1].indexOf("€ ") >= 0 ? x[1].indexOf("€ ") + 1 : -1;
                        etDomanda.setSelection(pos > 0 ? pos : x[1].length());
                        etDomanda.requestFocus();
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.showSoftInput(etDomanda, 0);
                    } else {
                        invia(x[1]);
                    }
                }
            });
            Premibile.INSTANCE.applicaA(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Math.round(6 * d));
            rigaSuggerimenti.addView(chip, lp);
        }
    }

    private String[] chip(String k, boolean daCompletare) {
        Context c = requireContext();
        int l = c.getResources().getIdentifier("chip_" + k + "_l", "string", c.getPackageName());
        int t = c.getResources().getIdentifier("chip_" + k + "_t", "string", c.getPackageName());
        return new String[]{c.getString(l), c.getString(t), daCompletare ? "1" : ""};
    }

    // ------------------------------------------------------------------ bolle

    private final class Adattatore extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemCount() {
            return messaggi.size() + (inAttesa ? 1 : 0);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            float d = c.getResources().getDisplayMetrics().density;
            FrameLayout riga = new FrameLayout(c);
            riga.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            LinearLayout colonna = new LinearLayout(c);
            colonna.setOrientation(LinearLayout.VERTICAL);
            riga.addView(colonna);
            // 0: foto
            android.widget.ImageView foto = new android.widget.ImageView(c);
            foto.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            foto.setAdjustViewBounds(true);
            foto.setClipToOutline(true);
            foto.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * v.getResources().getDisplayMetrics().density);
                }
            });
            colonna.addView(foto);
            // 1: bolla
            TextView bolla = new TextView(c);
            bolla.setTextAppearance(R.style.TextAppearance_App_Body);
            bolla.setTextIsSelectable(false);
            bolla.setLineSpacing(0, 1.08f);
            colonna.addView(bolla);
            // 2: tasti Subito / Vinted / eBay
            LinearLayout tasti = new LinearLayout(c);
            tasti.setOrientation(LinearLayout.HORIZONTAL);
            String[] nomi = {"Subito", "Vinted", "eBay"};
            for (int i = 0; i < nomi.length; i++) {
                TextView t = new TextView(c);
                t.setTextAppearance(R.style.TextAppearance_App_Subhead);
                t.setTextColor(ContextCompat.getColor(c, R.color.accent));
                t.setBackgroundResource(R.drawable.pill_background);
                t.setText(nomi[i]);
                t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                int ph = Math.round(13 * d), pv = Math.round(7 * d);
                t.setPadding(ph, pv, ph, pv);
                Premibile.INSTANCE.applicaA(t);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                if (i > 0) lp.setMarginStart(Math.round(6 * d));
                tasti.addView(t, lp);
            }
            LinearLayout.LayoutParams lpt = new LinearLayout.LayoutParams(-2, -2);
            lpt.topMargin = Math.round(6 * d);
            colonna.addView(tasti, lpt);
            return new RecyclerView.ViewHolder(riga) {};
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            Context c = h.itemView.getContext();
            float d = c.getResources().getDisplayMetrics().density;
            FrameLayout riga = (FrameLayout) h.itemView;
            LinearLayout colonna = (LinearLayout) riga.getChildAt(0);
            android.widget.ImageView foto = (android.widget.ImageView) colonna.getChildAt(0);
            final TextView bolla = (TextView) colonna.getChildAt(1);
            LinearLayout tasti = (LinearLayout) colonna.getChildAt(2);
            bolla.clearAnimation();
            boolean digita = pos >= messaggi.size();
            final ChatStorico.Messaggio m = digita ? null : messaggi.get(pos);
            boolean utente = m != null && ChatStorico.UTENTE.equals(m.ruolo);
            boolean errore = m != null && ChatStorico.ERRORE.equals(m.ruolo);

            int larghezza = c.getResources().getDisplayMetrics().widthPixels;
            int gravita = utente ? Gravity.END : Gravity.START;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravita);
            boolean stessoMittente = pos > 0 && pos - 1 < messaggi.size() && m != null
                    && TextUtils.equals(messaggi.get(pos - 1).ruolo, m.ruolo);
            lp.topMargin = Math.round((stessoMittente ? 3 : 10) * d);
            colonna.setLayoutParams(lp);
            colonna.setGravity(gravita);

            // foto dell'utente
            if (m != null && m.immagine != null) {
                android.graphics.Bitmap b = Foto.miniatura(m.immagine, 700);
                if (b != null) {
                    foto.setImageBitmap(b);
                    int w = (int) (larghezza * 0.62f);
                    LinearLayout.LayoutParams lf = new LinearLayout.LayoutParams(w, Math.round(w * Math.min(1.3f, b.getHeight() / (float) b.getWidth())));
                    lf.bottomMargin = Math.round(4 * d);
                    lf.gravity = gravita;
                    foto.setLayoutParams(lf);
                    foto.setVisibility(View.VISIBLE);
                } else {
                    foto.setVisibility(View.GONE);
                }
            } else {
                foto.setImageDrawable(null);
                foto.setVisibility(View.GONE);
            }

            GradientDrawable sfondo = new GradientDrawable();
            sfondo.setCornerRadius(19 * d);
            sfondo.setColor(utente ? ContextCompat.getColor(c, R.color.accent)
                    : ContextCompat.getColor(c, R.color.surface_elevated));
            bolla.setBackground(sfondo);
            int ph = Math.round(14 * d), pv = Math.round(9 * d);
            bolla.setPadding(ph, pv, ph, pv);
            bolla.setMaxWidth((int) (larghezza * 0.78f));
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(-2, -2);
            lb.gravity = gravita;
            bolla.setLayoutParams(lb);

            // tasti per cercare il prodotto riconosciuto
            if (m != null && ChatStorico.AI.equals(m.ruolo) && m.prodotto != null && !m.prodotto.isEmpty()) {
                tasti.setVisibility(View.VISIBLE);
                for (int i = 0; i < tasti.getChildCount(); i++) {
                    final int sito = i;
                    tasti.getChildAt(i).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Mercatino.apri(requireActivity(), m.prodotto, sito);
                        }
                    });
                }
            } else {
                tasti.setVisibility(View.GONE);
            }

            if (digita && !parziale.isEmpty()) {
                bolla.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
                bolla.setText(TestoAi.formatta(parziale));
                bolla.setOnLongClickListener(null);
                return;
            }
            if (digita) {
                long sec = (System.currentTimeMillis() - inizioAttesa) / 1000;
                String t = fase.isEmpty() ? "•••" : fase;
                if (sec >= 3) t += "  ·  " + sec + " s";
                bolla.setText(t);
                bolla.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
                AlphaAnimation an = new AlphaAnimation(0.3f, 1f);
                an.setDuration(600);
                an.setRepeatMode(Animation.REVERSE);
                an.setRepeatCount(Animation.INFINITE);
                bolla.startAnimation(an);
                bolla.setOnLongClickListener(null);
                return;
            }
            bolla.setTextColor(ContextCompat.getColor(c, utente ? R.color.white : errore ? R.color.danger : R.color.text_primary));
            bolla.setText(utente || errore ? m.testo : TestoAi.formatta(m.testo));
            final String copia = m.testo;
            bolla.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    ClipboardManager cm = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("BuyForTime", copia));
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    Toast.makeText(v.getContext(), R.string.copiato, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }

    // ------------------------------------------------------------------ domanda dai popup

    /** Testo per "Chiedi all'assistente" a partire dagli avvisi di un popup. */
    public static String domandaDaAvvisi(Context c, String contesto, List<Controlli.Avviso> avvisi) {
        Context l = Lingua.contesto(c);
        StringBuilder b = new StringBuilder(contesto).append(l.getString(R.string.ai_avvisi_intro)).append('\n');
        for (Controlli.Avviso a : avvisi) {
            b.append("- ").append(a.livello == Controlli.ROSSO ? "ROSSO" : a.livello == Controlli.GIALLO ? "GIALLO" : "INFO")
                    .append(" · ").append(a.titolo).append(": ").append(a.testo).append('\n');
        }
        b.append(l.getString(R.string.ai_avvisi_domanda));
        return b.toString();
    }

    /** "Volevo inserire/approvare la spesa X da Y €" nella lingua dell'app. */
    public static String contestoSpesa(Context c, boolean approvazione, String nome, double importo, long data, boolean manuale) {
        Context l = Lingua.contesto(c);
        String s = l.getString(approvazione ? R.string.ai_volevo_approvare : R.string.ai_volevo_inserire,
                nome, NumberFormat.getCurrencyInstance(Locale.ITALY).format(importo));
        if (manuale && data > System.currentTimeMillis()) {
            s += l.getString(R.string.ai_con_data, java.time.Instant.ofEpochMilli(data).atZone(ZoneId.systemDefault()).toLocalDate().toString());
        }
        return s;
    }

    static LocalDate oggi() {
        return LocalDate.now();
    }
}
