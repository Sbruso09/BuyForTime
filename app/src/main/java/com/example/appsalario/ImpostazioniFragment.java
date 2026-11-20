package com.example.appsalario;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ImpostazioniFragment extends Fragment {
    private NestedScrollView scroll;
    private TextView tvLargeTitle;
    private MaterialButtonToggleGroup segmentedTipo, segmentedMensilita, segmentedRegione;
    private TextInputLayout tilSalario, tilRegionale;
    private TextInputEditText etSalario, etRegionale, etComunale, etFondo;
    private TextView tvOrarioInizio, tvOrarioFine, tvPausaInizio, tvPausaFine, tvRiepilogoOrario;
    private androidx.appcompat.widget.SwitchCompat swPausa, swFestivi, swBlocco, swNascondi;
    private boolean ignoraSwitch;
    private final List<TextView> valoriBusta = new ArrayList<TextView>();
    private View boxPausa;
    private LinearLayout rigaGiorniLavoro;
    private Orario orario;
    private TextView tvGiornoStipendio, tvMeseTredicesima, tvMeseQuattordicesima;
    private View rigaTredicesima, rigaQuattordicesima;
    private int giornoStipendio = 1, meseTredicesima = 12, meseQuattordicesima = 7;
    private int sogliaResiduo = 35, sogliaGiorni = 21;
    private TextView tvSogliaResiduo, tvSogliaGiorni;
    private TextInputEditText etChiaveAi;
    private TextView tvStatoChiave;
    private TextInputEditText etChiaveGroq;
    private TextView tvStatoChiaveGroq, tvModello, tvUsoOggi;

    /** Stato di una riga spesa fissa (giorno, frequenza, mese di riferimento). */
    private static final class RigaFissa {
        String id;
        int giorno = 1, frequenza = 1, mese = 1;
    }
    private TextView tvNotaMensilita, tvOreAnno, tvTotaleSpeseFisse, tvNettoDisponibile, tvValoreOra;
    private View boxRal;
    private LinearLayout righeBusta, containerSpeseFisse;
    private MaterialButton btnSalva, btnAggiungiSpesaFissa;

    private final NumberFormat valuta = NumberFormat.getCurrencyInstance(Locale.ITALY);
    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { aggiornaRiepilogo(); }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_impostazioni, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        scroll = view.findViewById(R.id.scrollImpostazioni);
        tvLargeTitle = view.findViewById(R.id.tvLargeTitle);
        segmentedTipo = view.findViewById(R.id.segmentedTipo);
        segmentedMensilita = view.findViewById(R.id.segmentedMensilita);
        segmentedRegione = view.findViewById(R.id.segmentedRegione);
        tilSalario = view.findViewById(R.id.tilSalario);
        tilRegionale = view.findViewById(R.id.tilRegionale);
        etSalario = view.findViewById(R.id.etSalario);
        tvOrarioInizio = view.findViewById(R.id.tvOrarioInizio);
        tvOrarioFine = view.findViewById(R.id.tvOrarioFine);
        tvPausaInizio = view.findViewById(R.id.tvPausaInizio);
        tvPausaFine = view.findViewById(R.id.tvPausaFine);
        tvRiepilogoOrario = view.findViewById(R.id.tvRiepilogoOrario);
        swPausa = view.findViewById(R.id.swPausa);
        swFestivi = view.findViewById(R.id.swFestivi);
        swBlocco = view.findViewById(R.id.swBlocco);
        swNascondi = view.findViewById(R.id.swNascondi);
        boxPausa = view.findViewById(R.id.boxPausa);
        rigaGiorniLavoro = view.findViewById(R.id.rigaGiorniLavoro);
        tvGiornoStipendio = view.findViewById(R.id.tvGiornoStipendio);
        tvSogliaResiduo = view.findViewById(R.id.tvSogliaResiduo);
        tvSogliaGiorni = view.findViewById(R.id.tvSogliaGiorni);
        tvMeseTredicesima = view.findViewById(R.id.tvMeseTredicesima);
        tvMeseQuattordicesima = view.findViewById(R.id.tvMeseQuattordicesima);
        rigaTredicesima = view.findViewById(R.id.rigaTredicesima);
        rigaQuattordicesima = view.findViewById(R.id.rigaQuattordicesima);
        etRegionale = view.findViewById(R.id.etRegionale);
        etComunale = view.findViewById(R.id.etComunale);
        etFondo = view.findViewById(R.id.etFondo);
        tvNotaMensilita = view.findViewById(R.id.tvNotaMensilita);
        tvOreAnno = view.findViewById(R.id.tvOreAnno);
        tvTotaleSpeseFisse = view.findViewById(R.id.tvTotaleSpeseFisse);
        tvNettoDisponibile = view.findViewById(R.id.tvNettoDisponibile);
        tvValoreOra = view.findViewById(R.id.tvValoreOra);
        boxRal = view.findViewById(R.id.boxRal);
        righeBusta = view.findViewById(R.id.righeBusta);
        containerSpeseFisse = view.findViewById(R.id.containerSpeseFisse);
        btnSalva = view.findViewById(R.id.btnSalva);
        btnAggiungiSpesaFissa = view.findViewById(R.id.btnAggiungiSpesaFissa);

        scroll.setClipToPadding(false);
        Barre.INSTANCE.applica(view, scroll, scroll);
        collegaTitoloAlloScroll();
        Premibile.INSTANCE.applica(btnSalva, btnAggiungiSpesaFissa);

        carica();

        for (TextInputEditText et : new TextInputEditText[]{etSalario, etRegionale, etComunale, etFondo}) {
            et.addTextChangedListener(watcher);
        }
        MaterialButtonToggleGroup.OnButtonCheckedListener aggiorna = new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup g, int id, boolean checked) {
                if (checked) aggiornaRiepilogo();
            }
        };
        segmentedTipo.addOnButtonCheckedListener(aggiorna);
        segmentedMensilita.addOnButtonCheckedListener(aggiorna);
        segmentedRegione.addOnButtonCheckedListener(aggiorna);
        configuraOrario();
        configuraPrivacy();
        configuraAccrediti();
        configuraSoglie();
        configuraAssistente(view);
        configuraLingua(view);
        configuraNotifiche(view);
        btnAggiungiSpesaFissa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aggiungiRiga(null);
                aggiornaRiepilogo();
            }
        });
        btnSalva.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                salva();
            }
        });
        aggiornaRiepilogo();
    }

    private void collegaTitoloAlloScroll() {
        final String titolo = getString(R.string.tab_impostazioni);
        scroll.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int x, int y, int ox, int oy) {
                float corsa = Math.max(tvLargeTitle.getHeight(), 1);
                float quanto = Math.max(0f, Math.min(1f, y / corsa));
                FragmentActivity a = getActivity();
                if (a instanceof MainActivity) ((MainActivity) a).aggiornaTitoloBarra(titolo, quanto);
            }
        });
    }

    // ------------------------------------------------------------------ caricamento

    private void carica() {
        Stipendio st = speseManager().stipendio();
        if (st.salario > 0) etSalario.setText(numero(st.salario));
        segmentedTipo.check(Stipendio.TIPO_RAL.equals(st.tipo) ? R.id.btnRal
                : Stipendio.TIPO_ANNUALE.equals(st.tipo) ? R.id.btnAnnuale : R.id.btnMensile);
        segmentedMensilita.check(st.mensilita >= 14 ? R.id.btnM14 : st.mensilita >= 13 ? R.id.btnM13 : R.id.btnM12);
        segmentedRegione.check(st.regionalePiemonte ? R.id.btnPiemonte : R.id.btnRegioneFissa);
        if (st.regionalePct > 0) etRegionale.setText(numero(st.regionalePct));
        etComunale.setText(numero(st.comunalePct));
        if (st.fondoPct > 0) etFondo.setText(numero(st.fondoPct));
        orario = speseManager().orario();
        android.content.SharedPreferences pr = requireContext().getSharedPreferences("SalarioPrefs", 0);
        giornoStipendio = pr.getInt(Saldo.K_GIORNO_STIPENDIO, 1);
        meseTredicesima = pr.getInt(Saldo.K_MESE_13, 12);
        meseQuattordicesima = pr.getInt(Saldo.K_MESE_14, 7);
        sogliaResiduo = pr.getInt(ControlliUi.K_SOGLIA_RESIDUO, 35);
        sogliaGiorni = pr.getInt(ControlliUi.K_SOGLIA_GIORNI, 21);
        containerSpeseFisse.removeAllViews();
        for (SpesaFissa f : speseManager().getSpeseFisse()) aggiungiRiga(f);
    }

    private String tipo() {
        int id = segmentedTipo.getCheckedButtonId();
        if (id == R.id.btnRal) return Stipendio.TIPO_RAL;
        if (id == R.id.btnAnnuale) return Stipendio.TIPO_ANNUALE;
        return Stipendio.TIPO_MENSILE;
    }

    private int mensilita() {
        int id = segmentedMensilita.getCheckedButtonId();
        return id == R.id.btnM14 ? 14 : id == R.id.btnM13 ? 13 : 12;
    }

    private static double num(TextInputEditText et, double predefinito) {
        Float f = Numeri.toFloatOrNull(String.valueOf(et.getText()));
        return f == null ? predefinito : f;
    }

    /** Parametri come inseriti a schermo (anche non ancora salvati). */
    private Stipendio letto() {
        Stipendio st = new Stipendio();
        st.tipo = tipo();
        st.salario = num(etSalario, 0);
        st.mensilita = mensilita();
        st.oreGiorno = orario != null ? orario.oreGiorno() : 0;
        st.giorniMese = orario != null ? orario.giorniLavorativiMese(java.time.LocalDate.now().getYear()) : 0;
        st.regionalePiemonte = segmentedRegione.getCheckedButtonId() != R.id.btnRegioneFissa;
        st.regionalePct = num(etRegionale, 0);
        st.comunalePct = num(etComunale, 0);
        st.fondoPct = num(etFondo, 0);
        st.fisseMensili = totaleSpeseFisseInserite();
        return st;
    }


    // ------------------------------------------------------------------ orario

    private void configuraOrario() {
        tvOrarioInizio.setOnClickListener(sceltaOra(R.string.titolo_ora_inizio, 0));
        tvOrarioFine.setOnClickListener(sceltaOra(R.string.titolo_ora_fine, 1));
        tvPausaInizio.setOnClickListener(sceltaOra(R.string.titolo_pausa_inizio, 2));
        tvPausaFine.setOnClickListener(sceltaOra(R.string.titolo_pausa_fine, 3));
        swPausa.setChecked(orario.pausaAttiva);
        swFestivi.setChecked(orario.escludiFestivi);
        swPausa.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                orario.pausaAttiva = on;
                mostraOrario();
            }
        });
        swFestivi.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                orario.escludiFestivi = on;
                mostraOrario();
            }
        });
        mostraOrario();
    }

    private View.OnClickListener sceltaOra(final int titolo, final int quale) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(v);
                int attuale = quale == 0 ? orario.inizio : quale == 1 ? orario.fine : quale == 2 ? orario.pausaInizio : orario.pausaFine;
                DataPicker.mostraOra(requireContext(), getString(titolo), attuale, new DataPicker.OnOra() {
                    @Override
                    public void scelta(int minuti) {
                        if (quale == 0) orario.inizio = minuti;
                        else if (quale == 1) orario.fine = minuti;
                        else if (quale == 2) orario.pausaInizio = minuti;
                        else orario.pausaFine = minuti;
                        mostraOrario();
                    }
                });
            }
        };
    }

    private void mostraOrario() {
        if (orario == null || !isAdded()) return;
        Context ctx = requireContext();
        tvOrarioInizio.setText(Orario.hhmm(orario.inizio));
        tvOrarioFine.setText(Orario.hhmm(orario.fine));
        tvPausaInizio.setText(Orario.hhmm(orario.pausaInizio));
        tvPausaFine.setText(Orario.hhmm(orario.pausaFine));
        boxPausa.setVisibility(orario.pausaAttiva ? View.VISIBLE : View.GONE);
        int rosso = ContextCompat.getColor(ctx, R.color.danger), accento = ContextCompat.getColor(ctx, R.color.accent);
        boolean pausaOk = orario.pausaValida();
        tvPausaInizio.setTextColor(pausaOk ? accento : rosso);
        tvPausaFine.setTextColor(pausaOk ? accento : rosso);

        // giorni: cerchi L M M G V S D
        rigaGiorniLavoro.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            boolean on = (orario.giorni & (1 << i)) != 0;
            TextView c = new TextView(ctx);
            c.setTextAppearance(R.style.TextAppearance_App_Subhead);
            c.setText(getString(R.string.sigle_giorni).split(",")[i]);
            c.setGravity(Gravity.CENTER);
            c.setBackgroundResource(R.drawable.cerchio);
            c.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    on ? accento : ContextCompat.getColor(ctx, R.color.fill_tertiary)));
            c.setTextColor(ContextCompat.getColor(ctx, on ? R.color.white : R.color.text_secondary));
            if (on) c.setTypeface(c.getTypeface(), android.graphics.Typeface.BOLD);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    orario.giorni ^= (1 << bit);
                    mostraOrario();
                }
            });
            int lato = Math.round(36 * d);
            avvolgiCerchio(rigaGiorniLavoro, c, lato);
        }

        String tempoGiorno = TempoFormatter.INSTANCE.formatta(ctx, (float) orario.oreGiorno(), 24f);
        String tempoSett = numero(Math.round(orario.oreSettimana() * 10) / 10.0) + " h";
        tvRiepilogoOrario.setText(orario.valido() && pausaOk
                ? getString(R.string.riepilogo_orario, tempoGiorno, tempoSett)
                : getString(R.string.errore_orario));
        tvRiepilogoOrario.setTextColor(orario.valido() && pausaOk
                ? ContextCompat.getColor(ctx, R.color.text_secondary) : rosso);
        aggiornaRiepilogo();
    }

    /** Ogni cerchio occupa 1/7 della riga, centrato. */
    private static void avvolgiCerchio(LinearLayout riga, View cerchio, int lato) {
        android.widget.FrameLayout f = new android.widget.FrameLayout(riga.getContext());
        f.addView(cerchio, new android.widget.FrameLayout.LayoutParams(lato, lato, Gravity.CENTER));
        riga.addView(f, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }


    // ------------------------------------------------------------------ privacy

    private void configuraPrivacy() {
        ignoraSwitch = true;
        swBlocco.setChecked(Privacy.blocco(requireContext()));
        swNascondi.setChecked(Privacy.nascosto(requireContext()));
        ignoraSwitch = false;
        swNascondi.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                if (ignoraSwitch) return;
                Privacy.setNascosto(requireContext(), on);
                ((MainActivity) requireActivity()).aggiornaPrivacy();
            }
        });
        swBlocco.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final android.widget.CompoundButton b, final boolean on) {
                if (ignoraSwitch) return;
                final MainActivity a = (MainActivity) requireActivity();
                if (!Privacy.disponibile(a)) {
                    Toast.makeText(a, R.string.biometrico_non_disponibile, Toast.LENGTH_LONG).show();
                    rimetti(b, !on);
                    return;
                }
                // serve l'impronta sia per attivare sia per disattivare il blocco
                Privacy.autentica(a, getString(R.string.biometrico_titolo), getString(R.string.biometrico_conferma), new Privacy.Esito() {
                    @Override
                    public void riuscito() {
                        Privacy.setBlocco(a, on);
                        a.bloccoCambiato(on);
                    }

                    @Override
                    public void fallito() {
                        rimetti(b, !on);
                    }
                });
            }
        });
    }

    private void rimetti(android.widget.CompoundButton b, boolean stato) {
        ignoraSwitch = true;
        b.setChecked(stato);
        ignoraSwitch = false;
    }

    /** Chiamato quando si tocca l'occhio nella barra in alto. */
    public void aggiornaPrivacy() {
        if (swNascondi == null) return;
        ignoraSwitch = true;
        swNascondi.setChecked(Privacy.nascosto(requireContext()));
        ignoraSwitch = false;
        aggiornaRiepilogo();
    }

    private void applicaPrivacy() {
        Context ctx = requireContext();
        Privacy.sfoca(ctx, etSalario, tvNotaMensilita, tvTotaleSpeseFisse, tvNettoDisponibile, tvValoreOra);
        for (TextView tv : valoriBusta) Privacy.sfoca(ctx, tv);
        for (int i = 0; i < containerSpeseFisse.getChildCount(); i++) {
            Privacy.sfoca(ctx, (TextView) containerSpeseFisse.getChildAt(i).findViewById(R.id.etImportoSpesaFissa));
        }
    }


    // ------------------------------------------------------------------ accrediti

    private void configuraAccrediti() {
        tvGiornoStipendio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.giorno_stipendio),
                        DataPicker.giorniDelMese(), giornoStipendio - 1, new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                giornoStipendio = i + 1;
                                aggiornaRiepilogo();
                            }
                        });
            }
        });
        tvMeseTredicesima.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.mese_tredicesima),
                        DataPicker.mesiLunghi(), meseTredicesima - 1, new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                meseTredicesima = i + 1;
                                aggiornaRiepilogo();
                            }
                        });
            }
        });
        tvMeseQuattordicesima.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.mese_quattordicesima),
                        DataPicker.mesiLunghi(), meseQuattordicesima - 1, new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                meseQuattordicesima = i + 1;
                                aggiornaRiepilogo();
                            }
                        });
            }
        });
    }

    private void configuraSoglie() {
        final String[] pct = new String[18];
        for (int i = 0; i < 18; i++) pct[i] = (5 + i * 5) + "%";
        final String[] gg = new String[90];
        for (int i = 0; i < 90; i++) gg[i] = getResources().getQuantityString(R.plurals.giorni, i + 1, i + 1);
        tvSogliaResiduo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.soglia_residuo), pct, sogliaResiduo / 5 - 1,
                        new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                sogliaResiduo = 5 + i * 5;
                                salvaSoglie();
                            }
                        });
            }
        });
        tvSogliaGiorni.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.soglia_giorni), gg, sogliaGiorni - 1,
                        new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                sogliaGiorni = i + 1;
                                salvaSoglie();
                            }
                        });
            }
        });
        mostraSoglie();
    }

    /** Le soglie si applicano subito, come gli interruttori della Privacy. */
    private void salvaSoglie() {
        requireContext().getSharedPreferences("SalarioPrefs", 0).edit()
                .putInt(ControlliUi.K_SOGLIA_RESIDUO, sogliaResiduo)
                .putInt(ControlliUi.K_SOGLIA_GIORNI, sogliaGiorni)
                .apply();
        mostraSoglie();
    }

    private void mostraSoglie() {
        tvSogliaResiduo.setText(sogliaResiduo + "%");
        tvSogliaGiorni.setText(getString(R.string.soglia_giorni_valore, sogliaGiorni));
    }

    private void mostraAccrediti() {
        if (tvGiornoStipendio == null) return;
        tvGiornoStipendio.setText(getString(R.string.spesa_fissa_il) + " " + giornoStipendio);
        int n = mensilita();
        rigaTredicesima.setVisibility(n >= 13 ? View.VISIBLE : View.GONE);
        rigaQuattordicesima.setVisibility(n >= 14 ? View.VISIBLE : View.GONE);
        tvMeseTredicesima.setText(DataPicker.mesiLunghi()[meseTredicesima - 1]);
        tvMeseQuattordicesima.setText(DataPicker.mesiLunghi()[meseQuattordicesima - 1]);
    }


    // ------------------------------------------------------------------ lingua

    // ------------------------------------------------------------------ notifiche

    private TextView tvStatoNotifiche, tvStatoSveglie, tvStatoBatteria;

    private void configuraNotifiche(View view) {
        tvStatoNotifiche = view.findViewById(R.id.tvStatoNotifiche);
        tvStatoSveglie = view.findViewById(R.id.tvStatoSveglie);
        tvStatoBatteria = view.findViewById(R.id.tvStatoBatteria);
        final String pkg = requireContext().getPackageName();
        tvStatoNotifiche.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg);
                apri(i);
            }
        });
        tvStatoSveglie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT < 31) return;
                apri(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg)));
            }
        });
        tvStatoBatteria.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + pkg));
                if (!apri(i)) apri(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        });
        TextView prova = view.findViewById(R.id.tvProvaNotifica);
        prova.setText(R.string.notif_invia);
        prova.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent));
        prova.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                speseManager().notificaDiProva();
                Toast.makeText(requireContext(), R.string.notif_prova_inviata, Toast.LENGTH_LONG).show();
            }
        });
        aggiornaStatoNotifiche();
    }

    private boolean apri(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void aggiornaStatoNotifiche() {
        if (tvStatoNotifiche == null) return;
        Context c = requireContext();
        boolean notifiche = androidx.core.app.NotificationManagerCompat.from(c).areNotificationsEnabled();
        boolean sveglie = true;
        if (Build.VERSION.SDK_INT >= 31) {
            sveglie = ((android.app.AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
        }
        boolean batteria = ((android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE))
                .isIgnoringBatteryOptimizations(c.getPackageName());
        stato(tvStatoNotifiche, notifiche);
        stato(tvStatoSveglie, sveglie);
        stato(tvStatoBatteria, batteria);
    }

    private void stato(TextView tv, boolean attivo) {
        Context c = requireContext();
        tv.setText(attivo ? R.string.notif_attivo : R.string.notif_disattivo);
        tv.setTextColor(ContextCompat.getColor(c, attivo ? R.color.tempo : R.color.danger));
        tv.setClickable(!attivo);
    }

    @Override
    public void onResume() {
        super.onResume();
        aggiornaStatoNotifiche(); // di ritorno dalle impostazioni di sistema
        if (tvUsoOggi != null) mostraStatoChiave();
    }

    private void configuraLingua(View view) {
        final TextView tv = view.findViewById(R.id.tvLingua);
        tv.setText(Lingua.nome(requireContext(), Lingua.scelta()));
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String[] nomi = new String[Lingua.CODICI.length];
                int sel = 0;
                for (int i = 0; i < nomi.length; i++) {
                    nomi[i] = Lingua.nome(requireContext(), Lingua.CODICI[i]);
                    if (Lingua.CODICI[i].equals(Lingua.scelta())) sel = i;
                }
                DataPicker.mostraValori(requireContext(), getString(R.string.lingua_label), nomi, sel, new DataPicker.OnValore() {
                    @Override
                    public void scelta(int i) {
                        if (Lingua.CODICI[i].equals(Lingua.scelta())) return;
                        Lingua.imposta(Lingua.CODICI[i]); // l'app si ricarica nella nuova lingua
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ assistente AI

    private void configuraAssistente(View view) {
        etChiaveAi = view.findViewById(R.id.etChiaveAi);
        tvStatoChiave = view.findViewById(R.id.tvStatoChiave);
        etChiaveGroq = view.findViewById(R.id.etChiaveGroq);
        tvStatoChiaveGroq = view.findViewById(R.id.tvStatoChiaveGroq);
        tvModello = view.findViewById(R.id.tvModello);
        tvUsoOggi = view.findViewById(R.id.tvUsoOggi);
        mostraStatoChiave();
        view.findViewById(R.id.tvLinkGroq).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://console.groq.com/keys")));
                } catch (RuntimeException ignored) {
                }
            }
        });
        final android.content.SharedPreferences prM = requireContext().getSharedPreferences("SalarioPrefs", 0);
        final String[] codiciModello = {"veloce", "intelligente"};
        final Runnable mostraModello = new Runnable() {
            @Override
            public void run() {
                boolean intel = "intelligente".equals(prM.getString(AssistenteFragment.K_MODELLO, "veloce"));
                tvModello.setText(intel ? R.string.ai_modello_intelligente : R.string.ai_modello_veloce);
            }
        };
        mostraModello.run();
        tvModello.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] nomi = {getString(R.string.ai_modello_veloce), getString(R.string.ai_modello_intelligente)};
                int sel = "intelligente".equals(prM.getString(AssistenteFragment.K_MODELLO, "veloce")) ? 1 : 0;
                DataPicker.mostraValori(requireContext(), getString(R.string.ai_modello), nomi, sel, new DataPicker.OnValore() {
                    @Override
                    public void scelta(int i) {
                        prM.edit().putString(AssistenteFragment.K_MODELLO, codiciModello[i]).apply();
                        mostraModello.run();
                    }
                });
            }
        });
        view.findViewById(R.id.tvLinkChiave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://aistudio.google.com/apikey")));
                } catch (RuntimeException ignored) {
                }
            }
        });
        final androidx.appcompat.widget.SwitchCompat sw = view.findViewById(R.id.swRiservata);
        final android.content.SharedPreferences pr = requireContext().getSharedPreferences("SalarioPrefs", 0);
        sw.setChecked(pr.getBoolean(Assistente.K_RISERVATA, false));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                pr.edit().putBoolean(Assistente.K_RISERVATA, on).apply();
            }
        });
        view.findViewById(R.id.btnCancellaChat).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
                azioni.add(new ActionSheet.Azione(getString(R.string.cancella_chat), true, new kotlin.jvm.functions.Function0<kotlin.Unit>() {
                    @Override
                    public kotlin.Unit invoke() {
                        ChatStorico.cancella(requireContext());
                        Toast.makeText(requireContext(), R.string.chat_cancellata, Toast.LENGTH_SHORT).show();
                        return kotlin.Unit.INSTANCE;
                    }
                }));
                ActionSheet.INSTANCE.mostra(requireContext(), null, getString(R.string.nuova_chat_testo), azioni);
            }
        });
        view.findViewById(R.id.btnRimuoviChiave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChiaveAi.rimuovi(requireContext());
                ChiaveAi.rimuovi(requireContext(), ChiaveAi.GROQ);
                etChiaveAi.setText("");
                etChiaveGroq.setText("");
                mostraStatoChiave();
                Toast.makeText(requireContext(), R.string.chiavi_rimosse, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void mostraStatoChiave() {
        boolean c = ChiaveAi.presente(requireContext());
        tvStatoChiave.setText(c ? R.string.chiave_ai_salvata : R.string.chiave_ai_assente);
        tvStatoChiave.setTextColor(ContextCompat.getColor(requireContext(), c ? R.color.tempo : R.color.text_secondary));
        boolean g = ChiaveAi.presente(requireContext(), ChiaveAi.GROQ);
        tvStatoChiaveGroq.setText(g ? R.string.chiave_groq_salvata : R.string.chiave_groq_assente);
        tvStatoChiaveGroq.setTextColor(ContextCompat.getColor(requireContext(), g ? R.color.tempo : R.color.text_secondary));
        int[] uso = UsoAi.oggi(requireContext());
        tvUsoOggi.setText(getString(R.string.ai_uso_oggi, uso[0], uso[1]));
    }

    /** La chiave si salva anche se il resto delle impostazioni non e' ancora completo. */
    private void salvaChiaveSeInserita() {
        String k = String.valueOf(etChiaveAi.getText()).trim();
        if (!k.isEmpty() && ChiaveAi.salva(requireContext(), k)) etChiaveAi.setText("");
        String g = String.valueOf(etChiaveGroq.getText()).trim();
        if (!g.isEmpty() && ChiaveAi.salva(requireContext(), g, ChiaveAi.GROQ)) etChiaveGroq.setText("");
        mostraStatoChiave();
    }

    // ------------------------------------------------------------------ riepilogo live

    private void aggiornaRiepilogo() {
        if (etSalario == null || orario == null) return;
        Context ctx = requireContext();
        Stipendio st = letto();
        String tipo = st.tipo;

        tilSalario.setHint(getString(Stipendio.TIPO_RAL.equals(tipo) ? R.string.salario_hint_ral
                : Stipendio.TIPO_ANNUALE.equals(tipo) ? R.string.salario_hint_anno : R.string.salario_hint_mese));
        boxRal.setVisibility(Stipendio.TIPO_RAL.equals(tipo) ? View.VISIBLE : View.GONE);
        tilRegionale.setVisibility(st.regionalePiemonte ? View.GONE : View.VISIBLE);

        if (st.salario > 0) {
            tvNotaMensilita.setVisibility(View.VISIBLE);
            if (Stipendio.TIPO_RAL.equals(tipo)) {
                tvNotaMensilita.setText(getString(R.string.nota_mensilita_ral,
                        valuta.format(st.nettoPerMensilita()), valuta.format(st.nettoAnnuo())));
            } else if (Stipendio.TIPO_ANNUALE.equals(tipo)) {
                tvNotaMensilita.setText(getString(R.string.nota_mensilita_anno, valuta.format(st.nettoPerMensilita())));
            } else {
                tvNotaMensilita.setText(getString(R.string.nota_mensilita_mese, valuta.format(st.nettoAnnuo())));
            }
        } else {
            tvNotaMensilita.setVisibility(View.GONE);
        }

        riempiBusta(st);
        mostraAccrediti();

        double giorniMese = st.giorniMese;
        tvOreAnno.setText(getString(R.string.ore_anno, numero(Math.round(giorniMese * 12)),
                numero(Math.round(giorniMese * 10) / 10.0), numero(Math.round(st.oreAnno())) + " h"));

        tvTotaleSpeseFisse.setText(getString(R.string.totale_spese_fisse, valuta.format(st.fisseMensili)));
        if (st.salario <= 0) {
            tvNettoDisponibile.setText("");
            tvValoreOra.setText("");
        } else if (st.disponibileAnnuo() > 0) {
            tvNettoDisponibile.setText(getString(R.string.netto_disponibile, valuta.format(st.disponibileAnnuo() / 12.0)));
            tvNettoDisponibile.setTextColor(ContextCompat.getColor(ctx, R.color.tempo));
            tvValoreOra.setText(st.tariffaOraria() > 0
                    ? getString(R.string.valore_ora, valuta.format(st.tariffaOraria())) : "");
        } else {
            tvNettoDisponibile.setText(R.string.netto_non_disponibile);
            tvNettoDisponibile.setTextColor(ContextCompat.getColor(ctx, R.color.danger));
            tvValoreOra.setText("");
        }
        applicaPrivacy();
    }

    private void riempiBusta(Stipendio st) {
        righeBusta.removeAllViews();
        valoriBusta.clear();
        BustaPaga.Risultato r = st.bustaPaga();
        if (r == null) {
            riga(getString(R.string.voce_ral), "—", null, false, false);
            return;
        }
        riga(getString(R.string.voce_ral), valuta.format(r.ral), null, false, false);
        riga(getString(R.string.voce_inps), "− " + valuta.format(r.inps), null, true, false);
        if (r.fondoPensione > 0) riga(getString(R.string.voce_fondo), "− " + valuta.format(r.fondoPensione), null, true, false);
        riga(getString(R.string.voce_irpef), "− " + valuta.format(r.irpefNetta),
                getString(R.string.voce_irpef_dettaglio, valuta.format(r.irpefLorda),
                        valuta.format(r.detrazioneLavoro + r.ulterioreDetrazione)), true, false);
        double add = r.addizionaleRegionale + r.addizionaleComunale;
        riga(getString(R.string.voce_addizionali), "− " + valuta.format(add), null, true, false);
        if (r.sommaCuneo > 0) riga(getString(R.string.voce_cuneo), "+ " + valuta.format(r.sommaCuneo), null, false, false);
        if (r.trattamentoIntegrativo > 0) riga(getString(R.string.voce_integrativo), "+ " + valuta.format(r.trattamentoIntegrativo), null, false, false);
        riga(getString(R.string.voce_netto_anno), valuta.format(r.netto),
                getString(R.string.voce_aliquota_media, Math.round(r.aliquotaMedia() * 1000) / 10.0 + "%"), false, true);
        riga(getString(R.string.voce_netto_mensilita, (int) st.mensilita), valuta.format(r.netto / st.mensilita), null, false, true);
    }

    /** Riga in stile elenco iOS: etichetta a sinistra, valore a destra, separatore sottile. */
    private void riga(String etichetta, String valore, String sotto, boolean trattenuta, boolean totale) {
        Context ctx = requireContext();
        float d = getResources().getDisplayMetrics().density;
        if (righeBusta.getChildCount() > 0) {
            View sep = new View(ctx);
            sep.setBackgroundColor(ContextCompat.getColor(ctx, R.color.separator));
            righeBusta.addView(sep, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(0.5f * d))));
        }
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Math.round(11 * d), 0, Math.round(11 * d));

        LinearLayout sx = new LinearLayout(ctx);
        sx.setOrientation(LinearLayout.VERTICAL);
        TextView tvE = new TextView(ctx);
        tvE.setTextAppearance(totale ? R.style.TextAppearance_App_Headline : R.style.TextAppearance_App_Body);
        tvE.setText(etichetta);
        sx.addView(tvE);
        if (sotto != null) {
            TextView tvS = new TextView(ctx);
            tvS.setTextAppearance(R.style.TextAppearance_App_Footnote);
            tvS.setText(sotto);
            tvS.setFontFeatureSettings("tnum");
            sx.addView(tvS);
        }
        r.addView(sx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvV = new TextView(ctx);
        tvV.setTextAppearance(totale ? R.style.TextAppearance_App_Headline : R.style.TextAppearance_App_Body);
        tvV.setFontFeatureSettings("tnum");
        tvV.setText(valore);
        valoriBusta.add(tvV);
        tvV.setTextColor(ContextCompat.getColor(ctx, totale ? R.color.tempo : trattenuta ? R.color.text_secondary : R.color.text_primary));
        r.addView(tvV);
        righeBusta.addView(r);
    }

    // ------------------------------------------------------------------ spese fisse

    private void aggiungiRiga(SpesaFissa spesaFissa) {
        final View riga = getLayoutInflater().inflate(R.layout.item_spesa_fissa, containerSpeseFisse, false);
        TextInputEditText etNome = riga.findViewById(R.id.etNomeSpesaFissa);
        TextInputEditText etImporto = riga.findViewById(R.id.etImportoSpesaFissa);
        ImageButton btnRimuovi = riga.findViewById(R.id.btnRimuoviSpesaFissa);
        final RigaFissa stato = new RigaFissa();
        if (spesaFissa != null) {
            etNome.setText(spesaFissa.getNome());
            etImporto.setText(numero(spesaFissa.getImporto()));
            stato.id = spesaFissa.getId();
            stato.giorno = spesaFissa.getGiorno();
            stato.frequenza = spesaFissa.getFrequenza();
            stato.mese = spesaFissa.getMeseRiferimento();
        } else {
            stato.mese = java.time.LocalDate.now().getMonthValue();
        }
        if (stato.id == null) stato.id = UUID.randomUUID().toString();
        riga.setTag(stato);
        etImporto.addTextChangedListener(watcher);
        btnRimuovi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                containerSpeseFisse.removeView(riga);
                aggiornaRiepilogo();
            }
        });
        riga.findViewById(R.id.tvFrequenzaFissa).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int sel = 0;
                for (int i = 0; i < SpesaFissa.FREQUENZE.length; i++) if (SpesaFissa.FREQUENZE[i] == stato.frequenza) sel = i;
                DataPicker.mostraValori(requireContext(), getString(R.string.titolo_frequenza),
                        SpesaFissa.nomiFrequenze(requireContext()), sel, new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                stato.frequenza = SpesaFissa.FREQUENZE[i];
                                mostraRiga(riga);
                                aggiornaRiepilogo();
                            }
                        });
            }
        });
        riga.findViewById(R.id.tvGiornoFissa).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.titolo_giorno_addebito),
                        DataPicker.giorniDelMese(), stato.giorno - 1, new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                stato.giorno = i + 1;
                                mostraRiga(riga);
                            }
                        });
            }
        });
        riga.findViewById(R.id.tvMeseFissa).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DataPicker.mostraValori(requireContext(), getString(R.string.titolo_mese_addebito),
                        DataPicker.mesiLunghi(), stato.mese - 1, new DataPicker.OnValore() {
                            @Override
                            public void scelta(int i) {
                                stato.mese = i + 1;
                                mostraRiga(riga);
                            }
                        });
            }
        });
        mostraRiga(riga);
        containerSpeseFisse.addView(riga);
    }

    private void mostraRiga(View riga) {
        RigaFissa st = (RigaFissa) riga.getTag();
        ((TextView) riga.findViewById(R.id.tvFrequenzaFissa)).setText(SpesaFissa.nomeFrequenza(requireContext(), st.frequenza));
        ((TextView) riga.findViewById(R.id.tvGiornoFissa)).setText(String.valueOf(st.giorno));
        boolean ciclo = st.frequenza > 1;
        riga.findViewById(R.id.tvLabelMeseFissa).setVisibility(ciclo ? View.VISIBLE : View.GONE);
        TextView mese = riga.findViewById(R.id.tvMeseFissa);
        mese.setVisibility(ciclo ? View.VISIBLE : View.GONE);
        mese.setText(DataPicker.mesiLunghi()[st.mese - 1]);
        ((TextInputLayout) riga.findViewById(R.id.tilImportoSpesaFissa)).setHint(
                getString(ciclo ? R.string.spesa_fissa_importo_volta : R.string.spesa_fissa_importo_hint));
    }

    /** null se c'e' una riga incompleta. */
    private List<SpesaFissa> leggiSpeseFisse() {
        List<SpesaFissa> out = new ArrayList<SpesaFissa>();
        for (int i = 0; i < containerSpeseFisse.getChildCount(); i++) {
            View riga = containerSpeseFisse.getChildAt(i);
            String nome = String.valueOf(((TextInputEditText) riga.findViewById(R.id.etNomeSpesaFissa)).getText()).trim();
            String imp = String.valueOf(((TextInputEditText) riga.findViewById(R.id.etImportoSpesaFissa)).getText()).trim();
            if (nome.isEmpty() && imp.isEmpty()) continue;
            Float importo = Numeri.toFloatOrNull(imp);
            if (nome.isEmpty() || importo == null || importo < 0f) return null;
            RigaFissa st = (RigaFissa) riga.getTag();
            out.add(new SpesaFissa(st.id, nome, importo, st.giorno, st.frequenza, st.mese));
        }
        return out;
    }

    /** Totale al mese: le spese non mensili sono divise per la loro frequenza. */
    private double totaleSpeseFisseInserite() {
        double tot = 0;
        for (int i = 0; i < containerSpeseFisse.getChildCount(); i++) {
            View riga = containerSpeseFisse.getChildAt(i);
            Float f = Numeri.toFloatOrNull(String.valueOf(((TextInputEditText) riga.findViewById(R.id.etImportoSpesaFissa)).getText()));
            if (f != null && f > 0) tot += f / (double) ((RigaFissa) riga.getTag()).frequenza;
        }
        return tot;
    }

    // ------------------------------------------------------------------ salvataggio

    private void salva() {
        Context ctx = requireContext();
        salvaChiaveSeInserita();
        Stipendio st = letto();
        if (String.valueOf(etSalario.getText()).trim().isEmpty() || st.salario <= 0) {
            Toast.makeText(ctx, R.string.errore_salario, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ral = Stipendio.TIPO_RAL.equals(st.tipo);
        if (!orario.valido() || !orario.pausaValida()) {
            Toast.makeText(ctx, R.string.errore_orario, Toast.LENGTH_LONG).show();
            return;
        }
        if (st.giorniMese <= 0
                || (ral && (st.comunalePct < 0 || st.comunalePct > 5 || st.fondoPct < 0 || st.fondoPct > 20
                || (!st.regionalePiemonte && (st.regionalePct < 0 || st.regionalePct > 5))))) {
            Toast.makeText(ctx, R.string.errore_parametri, Toast.LENGTH_SHORT).show();
            return;
        }
        List<SpesaFissa> fisse = leggiSpeseFisse();
        if (fisse == null) {
            Toast.makeText(ctx, R.string.errore_spesa_fissa, Toast.LENGTH_SHORT).show();
            return;
        }
        SpeseManager m = speseManager();
        Saldo.aggiorna(ctx); // accrediti/addebiti gia' maturati restano con le regole di prima
        m.salvaOrario(orario);
        requireContext().getSharedPreferences("SalarioPrefs", 0).edit()
                .putInt(Saldo.K_GIORNO_STIPENDIO, giornoStipendio)
                .putInt(Saldo.K_MESE_13, meseTredicesima)
                .putInt(Saldo.K_MESE_14, meseQuattordicesima)
                .apply();
        m.salvaStipendio(st);
        m.saveSpeseFisse(fisse);
        int aggiornate = m.ricalcolaSpese(); // tutte le spese col nuovo valore dell'ora
        m.riprogrammaRipaghi(); // le fini dei countdown dipendono da ore e orario
        Toast.makeText(ctx, aggiornate > 0
                ? getResources().getQuantityString(R.plurals.spese_ricalcolate, aggiornate, aggiornate)
                : getString(R.string.impostazioni_salvate), Toast.LENGTH_SHORT).show();
        ((MainActivity) requireActivity()).vaiAlleSpese();
    }

    private SpeseManager speseManager() {
        return ((MainActivity) requireActivity()).getSpeseManager();
    }

    private static String numero(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(Locale.ITALY, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
