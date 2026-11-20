package com.example.appsalario;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Arrays;
import java.util.List;

public final class MainActivity extends AppCompatActivity {
    private static final int TAB_SPESE = 0;
    private static final int TAB_APPROVATE = 1;
    private static final int TAB_IMPOSTAZIONI = 2;
    private static final int TAB_ASSISTENTE = 3;
    private ImageButton btnNuovaChat;
    private ImageButton btnCercaUsato;
    public static final String EXTRA_APRI_ASSISTENTE = "APRI_ASSISTENTE";
    private static Context contestoApp;

    /** Contesto dell'applicazione (null prima del primo avvio della schermata). */
    static Context contestoApp() {
        return contestoApp;
    }
    private String domandaInSospeso;

    private ImageButton btnAggiungi;
    private View navBar;
    private View navBarSeparator;
    private SpeseManager speseManager;
    private View tabBar;
    private int tabSelezionata = -1;
    private List<View> tabs;
    private TextView tvTitoloInline;
    private ImageButton btnPrivacy;
    private View overlayBlocco;
    private boolean sbloccata;
    private boolean autenticazioneInCorso;
    private long ultimoStop;
    private static final long BLOCCO_DOPO_MS = 60_000L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        contestoApp = getApplicationContext();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);
        speseManager = new SpeseManager(this);
        navBar = findViewById(R.id.navBar);
        navBarSeparator = findViewById(R.id.navBarSeparator);
        tabBar = findViewById(R.id.tabBar);
        tvTitoloInline = findViewById(R.id.tvTitoloInline);
        btnAggiungi = findViewById(R.id.btnAggiungi);
        btnPrivacy = findViewById(R.id.btnPrivacy);
        btnPrivacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                Privacy.setNascosto(MainActivity.this, !Privacy.nascosto(MainActivity.this));
                aggiornaPrivacy();
            }
        });
        aggiornaIconaPrivacy();
        Privacy.proteggiRecenti(this, Privacy.blocco(this));
        btnAggiungi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mostraDialogAggiungiSpesa();
            }
        });
        applicaInsetsDiSistema();
        configuraTabBar();
        chiediPermessoNotifiche();
        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra(EXTRA_APRI_ASSISTENTE, false)) selezionaTab(TAB_ASSISTENTE, null);
            else selezionaTab(TAB_SPESE, getIntent().getStringExtra("SPESA_ID"));
        } else {
            // ricreata (es. cambio lingua): ripristino la scheda e lo sblocco recente
            tabSelezionata = savedInstanceState.getInt("tab", TAB_SPESE);
            evidenziaTab(tabSelezionata, false);
            btnAggiungi.setVisibility(tabSelezionata == TAB_SPESE ? View.VISIBLE : View.GONE);
            btnNuovaChat.setVisibility(tabSelezionata == TAB_ASSISTENTE ? View.VISIBLE : View.GONE);
            btnCercaUsato.setVisibility(tabSelezionata == TAB_ASSISTENTE ? View.VISIBLE : View.GONE);
            sbloccata = savedInstanceState.getBoolean("sbloccata", false);
            ultimoStop = savedInstanceState.getLong("ultimoStop", 0);
        }
    }

    /** Android 13+: senza questo permesso promemoria e "spesa ripagata" non compaiono. Chiesto una volta sola. */
    private void chiediPermessoNotifiche() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.SharedPreferences p = getSharedPreferences("SalarioPrefs", 0);
        if (p.getBoolean("permesso_notifiche_chiesto", false)) return;
        p.edit().putBoolean("permesso_notifiche_chiesto", true).apply();
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 42);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!Privacy.blocco(this)) {
            sbloccata = true;
            nascondiBlocco(false);
            return;
        }
        if (autenticazioneInCorso) return;
        if (!sbloccata || System.currentTimeMillis() - ultimoStop > BLOCCO_DOPO_MS) {
            sbloccata = false;
            mostraBlocco();
            chiediSblocco();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (sbloccata && !autenticazioneInCorso) ultimoStop = System.currentTimeMillis();
    }

    private void chiediSblocco() {
        autenticazioneInCorso = true;
        Privacy.autentica(this, getString(R.string.biometrico_titolo), null, new Privacy.Esito() {
            @Override
            public void riuscito() {
                autenticazioneInCorso = false;
                sbloccata = true;
                ultimoStop = System.currentTimeMillis();
                nascondiBlocco(true);
            }

            @Override
            public void fallito() {
                autenticazioneInCorso = false; // resta bloccata: si riprova con "Sblocca"
            }
        });
    }

    /** Schermata di blocco sopra a tutto, in stile iOS. */
    private void mostraBlocco() {
        if (overlayBlocco != null) {
            overlayBlocco.setAlpha(1f);
            return;
        }
        float d = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setGravity(android.view.Gravity.CENTER);
        box.setBackgroundColor(ContextCompat.getColor(this, R.color.background));
        box.setClickable(true);
        box.setFocusable(true);
        box.setElevation(100 * d);
        int pad = Math.round(40 * d);
        box.setPadding(pad, 0, pad, 0);

        android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
        tile.setBackgroundResource(R.drawable.tile_background);
        tile.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.surface_elevated)));
        android.widget.ImageView lucchetto = new android.widget.ImageView(this);
        lucchetto.setImageResource(R.drawable.ic_lock);
        lucchetto.setImageTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent)));
        int i = Math.round(36 * d);
        tile.addView(lucchetto, new android.widget.FrameLayout.LayoutParams(i, i, android.view.Gravity.CENTER));
        int t = Math.round(72 * d);
        box.addView(tile, new android.widget.LinearLayout.LayoutParams(t, t));

        TextView titolo = new TextView(this);
        titolo.setTextAppearance(R.style.TextAppearance_App_Title3);
        titolo.setText(R.string.blocco_titolo);
        titolo.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = Math.round(20 * d);
        box.addView(titolo, lp);

        TextView sotto = new TextView(this);
        sotto.setTextAppearance(R.style.TextAppearance_App_Footnote);
        sotto.setText(R.string.blocco_sottotitolo);
        sotto.setGravity(android.view.Gravity.CENTER);
        android.widget.LinearLayout.LayoutParams lp2 = new android.widget.LinearLayout.LayoutParams(-2, -2);
        lp2.topMargin = Math.round(4 * d);
        box.addView(sotto, lp2);

        TextView sblocca = new TextView(this);
        sblocca.setTextAppearance(R.style.TextAppearance_App_Headline);
        sblocca.setTextColor(ContextCompat.getColor(this, R.color.accent));
        sblocca.setText(R.string.sblocca);
        sblocca.setGravity(android.view.Gravity.CENTER);
        sblocca.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_fingerprint, 0, 0, 0);
        sblocca.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent)));
        sblocca.setCompoundDrawablePadding(Math.round(8 * d));
        sblocca.setBackgroundResource(R.drawable.pill_background);
        int ph = Math.round(22 * d), pv = Math.round(12 * d);
        sblocca.setPadding(ph, pv, ph, pv);
        sblocca.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!autenticazioneInCorso) chiediSblocco();
            }
        });
        android.widget.LinearLayout.LayoutParams lp3 = new android.widget.LinearLayout.LayoutParams(-2, -2);
        lp3.topMargin = Math.round(32 * d);
        box.addView(sblocca, lp3);
        Premibile.INSTANCE.applicaA(sblocca);

        ((ViewGroup) findViewById(R.id.root)).addView(box, new ViewGroup.LayoutParams(-1, -1));
        overlayBlocco = box;
    }

    private void nascondiBlocco(boolean animato) {
        final View o = overlayBlocco;
        if (o == null) return;
        overlayBlocco = null;
        if (!animato) {
            ((ViewGroup) o.getParent()).removeView(o);
            return;
        }
        o.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (o.getParent() != null) ((ViewGroup) o.getParent()).removeView(o);
            }
        }).start();
    }

    /** Chiamato dalle Impostazioni quando il blocco viene attivato/disattivato. */
    public final void bloccoCambiato(boolean attivo) {
        Privacy.proteggiRecenti(this, attivo);
        sbloccata = true;
        ultimoStop = System.currentTimeMillis();
    }

    private void aggiornaIconaPrivacy() {
        boolean on = Privacy.nascosto(this);
        btnPrivacy.setImageResource(on ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
        btnPrivacy.setContentDescription(getString(on ? R.string.mostra_importi : R.string.nascondi_importi));
    }

    /** Applica "nascondi importi" alla schermata corrente. */
    public final void aggiornaPrivacy() {
        aggiornaIconaPrivacy();
        Fragment corrente = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (corrente instanceof ImpostazioniFragment) ((ImpostazioniFragment) corrente).aggiornaPrivacy();
        else aggiornaSchermataCorrente();
    }

    @Override
    protected void onResume() {
        super.onResume();
        speseManager.riprogrammaRipaghi();
    }

    private void applicaInsetsDiSistema() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets sistema = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                navBar.setPadding(navBar.getPaddingLeft(), sistema.top, navBar.getPaddingRight(), navBar.getPaddingBottom());
                tabBar.setPadding(tabBar.getPaddingLeft(), tabBar.getPaddingTop(), tabBar.getPaddingRight(), sistema.bottom);
                return insets;
            }
        });
    }

    private void configuraTabBar() {
        tabs = Arrays.asList(findViewById(R.id.tabSpese), findViewById(R.id.tabApprovate),
                findViewById(R.id.tabImpostazioni), findViewById(R.id.tabAssistente));
        btnNuovaChat = findViewById(R.id.btnNuovaChat);
        btnCercaUsato = findViewById(R.id.btnCercaUsato);
        btnCercaUsato.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Mercatino.chiedi(MainActivity.this);
            }
        });
        btnNuovaChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
                if (!(f instanceof AssistenteFragment)) return;
                List<ActionSheet.Azione> azioni = new java.util.ArrayList<ActionSheet.Azione>();
                azioni.add(new ActionSheet.Azione(getString(R.string.nuova_chat), true, new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke() {
                        ((AssistenteFragment) f).nuovaChat();
                        return kotlin.Unit.INSTANCE;
                    }
                }));
                ActionSheet.INSTANCE.mostra(MainActivity.this, null, getString(R.string.nuova_chat_testo), azioni);
            }
        });
        for (int i = 0; i < tabs.size(); i++) {
            final int indice = i;
            tabs.get(i).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selezionaTab(indice, null);
                }
            });
        }
    }

    private void selezionaTab(int indice, String spesaId) {
        if (indice == tabSelezionata && spesaId == null) return;
        tabSelezionata = indice;
        evidenziaTab(indice, true);
        switch (indice) {
            case TAB_SPESE: {
                HomeFragment f = new HomeFragment();
                if (spesaId != null) {
                    Bundle b = new Bundle();
                    b.putString("SPESA_ID", spesaId);
                    f.setArguments(b);
                }
                mostra(f);
                break;
            }
            case TAB_APPROVATE:
                mostra(new SpeseApprovateFragment());
                break;
            case TAB_IMPOSTAZIONI:
                mostra(new ImpostazioniFragment());
                break;
            case TAB_ASSISTENTE:
                mostra(new AssistenteFragment());
                break;
            default:
                break;
        }
    }

    private void evidenziaTab(int indice, boolean animato) {
        for (int i = 0; i < tabs.size(); i++) {
            android.widget.ImageView icona = (android.widget.ImageView) ((ViewGroup) tabs.get(i)).getChildAt(0);
            icona.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(this, i == indice ? R.color.accent : R.color.system_gray)));
            if (i == indice && animato) { // piccolo rimbalzo sull'icona scelta, come la tab bar di iOS
                icona.setScaleX(0.82f);
                icona.setScaleY(0.82f);
                icona.animate().scaleX(1f).scaleY(1f).setDuration(280)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f)).start();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tabSelezionata);
        out.putBoolean("sbloccata", sbloccata);
        out.putLong("ultimoStop", System.currentTimeMillis());
    }

    private void mostra(Fragment fragment) {
        btnAggiungi.setVisibility(fragment instanceof HomeFragment ? View.VISIBLE : View.GONE);
        btnNuovaChat.setVisibility(fragment instanceof AssistenteFragment ? View.VISIBLE : View.GONE);
        btnCercaUsato.setVisibility(fragment instanceof AssistenteFragment ? View.VISIBLE : View.GONE);
        aggiornaTitoloBarra("", 0f);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    public final void aggiornaTitoloBarra(String titolo, float quantoNascosto) {
        tvTitoloInline.setText(titolo);
        tvTitoloInline.setAlpha(quantoNascosto);
        navBarSeparator.setAlpha(quantoNascosto);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String spesaId = intent.getStringExtra("SPESA_ID");
        if (spesaId != null) selezionaTab(TAB_SPESE, spesaId);
        else if (intent.getBooleanExtra(EXTRA_APRI_ASSISTENTE, false)) selezionaTab(TAB_ASSISTENTE, null);
    }

    /** Apre la chat e invia subito la domanda (es. da "Chiedi all'assistente" nei popup). */
    public final void apriAssistente(String domanda) {
        domandaInSospeso = domanda;
        tabSelezionata = -1; // forza una nuova schermata che legga la domanda
        selezionaTab(TAB_ASSISTENTE, null);
    }

    public final String prendiDomandaInSospeso() {
        String d = domandaInSospeso;
        domandaInSospeso = null;
        return d;
    }

    public final void vaiAlleImpostazioni() {
        selezionaTab(TAB_IMPOSTAZIONI, null);
    }

    public final void vaiAlleSpese() {
        selezionaTab(TAB_SPESE, null);
    }

    private void mostraDialogAggiungiSpesa() {
        if (!speseManager.isSalarioConfigurato()) {
            Toast.makeText(this, R.string.errore_salario_mancante, Toast.LENGTH_LONG).show();
            selezionaTab(TAB_IMPOSTAZIONI, null);
            return;
        }
        if (speseManager.getNettoDisponibile() <= 0f) {
            Toast.makeText(this, R.string.errore_netto, Toast.LENGTH_LONG).show();
            selezionaTab(TAB_IMPOSTAZIONI, null);
            return;
        }
        final Dialog dialog = new Dialog(this, R.style.Theme_App_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_aggiungi_spesa);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        sfocaSfondo(dialog);

        final TextInputEditText etNome = dialog.findViewById(R.id.etNomeSpesa);
        final TextInputEditText etImporto = dialog.findViewById(R.id.etImporto);
        MaterialButton btnAnnulla = dialog.findViewById(R.id.btnAnnulla);
        MaterialButton btnConferma = dialog.findViewById(R.id.btnAggiungi);
        final View rigaDataManuale = dialog.findViewById(R.id.rigaDataManuale);
        final SwitchCompat swData = dialog.findViewById(R.id.swDataManuale);
        final View boxData = dialog.findViewById(R.id.boxDataScelta);
        final TextView tvData = dialog.findViewById(R.id.tvDataScelta);
        final TextView tvOra = dialog.findViewById(R.id.tvOraScelta);
        final TextView tvNota = dialog.findViewById(R.id.tvNotaData);
        final SwitchCompat swUtilizzi = dialog.findViewById(R.id.swUtilizzi);
        dialog.findViewById(R.id.rigaUtilizzi).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swUtilizzi.toggle();
            }
        });
        Premibile.INSTANCE.applica(btnConferma, btnAnnulla);

        final View rigaCategoria = dialog.findViewById(R.id.rigaCategoria);
        final View tileCat = dialog.findViewById(R.id.tileCategoriaScelta);
        final android.widget.ImageView ivCat = dialog.findViewById(R.id.ivCategoriaScelta);
        final TextView tvCat = dialog.findViewById(R.id.tvCategoriaScelta);
        final String[] categoria = {Categorie.ALTRO};
        final Runnable mostraCategoria = new Runnable() {
            @Override
            public void run() {
                Categorie.Categoria c = Categorie.get(MainActivity.this, categoria[0]);
                CategoriaPicker.applica(tileCat, ivCat, c);
                tvCat.setText(c.nome);
            }
        };
        mostraCategoria.run();
        rigaCategoria.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                CategoriaPicker.mostra(MainActivity.this, categoria[0], new CategoriaPicker.OnScelta() {
                    @Override
                    public void scelta(Categorie.Categoria c) {
                        categoria[0] = c.key;
                        mostraCategoria.run();
                    }
                });
            }
        });

        final long[] dataScelta = {System.currentTimeMillis()};
        final Runnable mostraData = new Runnable() {
            @Override
            public void run() {
                tvData.setText(Ripago.data(dataScelta[0]));
                tvOra.setText(Ripago.ora(dataScelta[0]));
            }
        };
        mostraData.run();

        final View.OnClickListener apriPicker = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                DataPicker.mostra(MainActivity.this, getString(R.string.data_spesa_titolo), dataScelta[0], new DataPicker.OnScelta() {
                    @Override
                    public void scelta(long millis) {
                        dataScelta[0] = millis;
                        mostraData.run();
                    }
                });
            }
        };
        tvData.setOnClickListener(apriPicker);
        tvOra.setOnClickListener(apriPicker);

        swData.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean attivo) {
                boxData.setVisibility(attivo ? View.VISIBLE : View.GONE);
                tvNota.setText(attivo ? R.string.nota_data_manuale : R.string.nota_data_automatica);
                if (attivo) {
                    dataScelta[0] = System.currentTimeMillis();
                    mostraData.run();
                }
            }
        });
        rigaDataManuale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swData.toggle();
            }
        });

        btnAnnulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                dialog.dismiss();
            }
        });
        btnConferma.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                String nome = String.valueOf(etNome.getText()).trim();
                String importoStr = String.valueOf(etImporto.getText()).trim();
                if (nome.isEmpty() || importoStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, R.string.errore_campi_vuoti, Toast.LENGTH_SHORT).show();
                    return;
                }
                Float importo = Numeri.toFloatOrNull(importoStr);
                if (importo == null || importo <= 0f) {
                    Toast.makeText(MainActivity.this, R.string.errore_spesa, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (speseManager.calcolaTariffaOraria() == null) {
                    Toast.makeText(MainActivity.this, R.string.errore_salario_mancante, Toast.LENGTH_LONG).show();
                    return;
                }
                final boolean manuale = swData.isChecked();
                // data automatica = il momento esatto in cui si aggiunge la spesa
                final long data = manuale ? dataScelta[0] : System.currentTimeMillis();
                final String nomeF = nome;
                final float importoF = importo;
                Controlli.Dati dati = ControlliUi.dati(MainActivity.this, nome, importo, data, manuale, false, null);
                ControlliUi.mostra(MainActivity.this, Controlli.valuta(dati), false, new ControlliUi.Esito() {
                    @Override
                    public void prosegui() {
                        if (speseManager.addSpesa(nomeF, importoF, data, manuale, categoria[0], swUtilizzi.isChecked())) {
                            aggiornaSchermataCorrente();
                            dialog.dismiss();
                        } else {
                            Toast.makeText(MainActivity.this, R.string.errore_netto, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void rivedi() {
                        etImporto.requestFocus(); // il form resta aperto con i dati inseriti
                    }

                    @Override
                    public void annulla() {
                        dialog.dismiss(); // la spesa non viene inserita
                    }
                }, AssistenteFragment.contestoSpesa(MainActivity.this, false, nomeF, importoF, data, manuale));
            }
        });
        dialog.show();
    }

    private void aggiornaSchermataCorrente() {
        Fragment corrente = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (corrente instanceof HomeFragment) {
            ((HomeFragment) corrente).refreshSpese();
        } else if (corrente instanceof SpeseApprovateFragment) {
            ((SpeseApprovateFragment) corrente).refreshSpese();
        }
    }

    private void sfocaSfondo(Dialog dialog) {
        if (Build.VERSION.SDK_INT < 31 || dialog.getWindow() == null) return;
        dialog.getWindow().setBackgroundBlurRadius(48);
    }

    public final SpeseManager getSpeseManager() {
        return speseManager;
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View v = view != null ? view : getCurrentFocus();
        if (v == null) v = new View(this);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (tabSelezionata != TAB_SPESE) {
            selezionaTab(TAB_SPESE, null);
        } else {
            super.onBackPressed();
        }
    }
}
