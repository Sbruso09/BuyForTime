package com.example.appsalario;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public final class HomeFragment extends Fragment {
    private SpeseAdapter adapter;
    private AppBarLayout appBar;
    private RecyclerView rvSpese;
    private View statoVuoto;
    private View cardSaldo;

    private String[] tempoLabels;
    private final long[] tempoDelays = {60000L, 300000L, 1800000L, 3600000L, 10800000L, 21600000L, 43200000L, 86400000L, 604800000L};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) adapter.tick();
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L));
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appBar = view.findViewById(R.id.appBar);
        rvSpese = view.findViewById(R.id.rvSpese);
        statoVuoto = view.findViewById(R.id.tvListaVuota);
        cardSaldo = view.findViewById(R.id.cardSaldo);
        Premibile.INSTANCE.applicaA(cardSaldo);
        final Runnable aggiorna = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) aggiornaSaldo();
            }
        };
        cardSaldo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Saldo.attivo(requireContext())) SaldoUi.mostraMovimenti(requireContext());
                else SaldoUi.mostraDialog(requireActivity(), aggiorna);
            }
        });
        view.findViewById(R.id.btnModificaSaldo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SaldoUi.mostraDialog(requireActivity(), aggiorna);
            }
        });
        Barre.INSTANCE.applica(view, appBar, rvSpese);
        collegaTitoloAlloScroll();
        setupRecyclerView();
        loadSpese();
        Bundle args = getArguments();
        final String spesaId = args != null ? args.getString("SPESA_ID") : null;
        if (spesaId != null) {
            rvSpese.post(new Runnable() {
                @Override
                public void run() {
                    int pos = adapter.posizioneDi(spesaId);
                    if (pos != -1) {
                        rvSpese.smoothScrollToPosition(pos);
                        adapter.evidenziaSpesa(spesaId);
                    }
                }
            });
        }
    }

    private void collegaTitoloAlloScroll() {
        final String titolo = getString(R.string.tab_spese);
        appBar.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            @Override
            public void onOffsetChanged(AppBarLayout barra, int offset) {
                int corsa = barra.getTotalScrollRange();
                float quanto = corsa > 0 ? Math.abs(offset) / (float) corsa : 0f;
                FragmentActivity a = getActivity();
                if (a instanceof MainActivity) ((MainActivity) a).aggiornaTitoloBarra(titolo, quanto);
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new SpeseAdapter(speseManager().getOreGiorno(), new Function1<Spesa, Unit>() {
            @Override
            public Unit invoke(Spesa spesa) {
                mostraAzioni(spesa);
                return Unit.INSTANCE;
            }
        });
        adapter.setOnUtilizzo(new Function1<Spesa, Unit>() {
            @Override
            public Unit invoke(Spesa spesa) {
                int n = speseManager().registraUtilizzo(spesa.getId(), 1);
                adapter.aggiornaUtilizzi(spesa.getId(), n);
                return Unit.INSTANCE;
            }
        });
        adapter.setOnUtilizzoMeno(new Function1<Spesa, Unit>() {
            @Override
            public Unit invoke(Spesa spesa) {
                int n = speseManager().registraUtilizzo(spesa.getId(), -1);
                adapter.aggiornaUtilizzi(spesa.getId(), n);
                return Unit.INSTANCE;
            }
        });
        rvSpese.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSpese.setAdapter(adapter);
        new SwipeElimina(rvSpese, new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer posizione) {
                chiediEliminazione(adapter.spesaAt(posizione), posizione);
                return Unit.INSTANCE;
            }
        }).collega();
    }

    private void aggiornaSaldo() {
        Saldo.aggiorna(requireContext()); // accrediti e addebiti maturati dall'ultima apertura
        BuoniUi.scheda(requireActivity(), cardSaldo, new Runnable() {
            @Override
            public void run() {
                if (isAdded()) aggiornaSaldo();
            }
        });
        SaldoUi.aggiornaCard(requireContext(), cardSaldo, new Runnable() {
            @Override
            public void run() {
                SaldoUi.mostraDialog(requireActivity(), new Runnable() {
                    @Override
                    public void run() {
                        if (isAdded()) aggiornaSaldo();
                    }
                });
            }
        });
    }

    private void loadSpese() {
        aggiornaSaldo();
        List<Spesa> spese = speseManager().getSpese();
        if (spese.isEmpty()) {
            statoVuoto.setVisibility(View.VISIBLE);
            rvSpese.setVisibility(View.GONE);
        } else {
            statoVuoto.setVisibility(View.GONE);
            rvSpese.setVisibility(View.VISIBLE);
        }
        adapter.updateSpese(spese, Ripago.pianifica(spese, speseManager()));
    }

    private void mostraAzioni(final Spesa spesa) {
        List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
        if (!spesa.getApprovata()) {
            azioni.add(new ActionSheet.Azione(getString(R.string.approva), false, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    // controlli anche all'approvazione: e' qui che i soldi escono dal saldo
                    Controlli.Dati d = ControlliUi.dati(requireContext(), spesa.getNome(), spesa.getImporto(),
                            spesa.getDataCreazione(), spesa.getDataManuale(), true, spesa.getId());
                    ControlliUi.mostra(requireActivity(), Controlli.valuta(d), true, new ControlliUi.Esito() {
                        @Override
                        public void prosegui() {
                            if (!isAdded()) return;
                            speseManager().approvaSpesa(spesa.getId());
                            loadSpese();
                        }

                        @Override
                        public void rivedi() {
                        }

                        @Override
                        public void annulla() {
                        }
                    }, AssistenteFragment.contestoSpesa(requireContext(), true, spesa.getNome(), spesa.getImporto(),
                            spesa.getDataCreazione(), spesa.getDataManuale()));
                    return Unit.INSTANCE;
                }
            }));
        }
        azioni.add(new ActionSheet.Azione(getString(spesa.getTracciaUtilizzi() ? R.string.traccia_utilizzi_off : R.string.traccia_utilizzi_on), false, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                speseManager().impostaTracciaUtilizzi(spesa.getId(), !spesa.getTracciaUtilizzi());
                loadSpese();
                return Unit.INSTANCE;
            }
        }));
        azioni.add(new ActionSheet.Azione(getString(R.string.cambia_categoria), false, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                CategoriaPicker.mostra(requireContext(), spesa.getCategoria(), new CategoriaPicker.OnScelta() {
                    @Override
                    public void scelta(Categorie.Categoria c) {
                        if (!isAdded()) return;
                        speseManager().impostaCategoria(spesa.getId(), c.key);
                        loadSpese();
                    }
                });
                return Unit.INSTANCE;
            }
        }));
        azioni.add(new ActionSheet.Azione(getString(R.string.modifica_data), false, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                modificaData(spesa);
                return Unit.INSTANCE;
            }
        }));
        if (!spesa.getApprovata()) {
            azioni.add(new ActionSheet.Azione(getString(R.string.imposta_notifica), false, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    mostraDialogNotifica(spesa);
                    return Unit.INSTANCE;
                }
            }));
        }
        azioni.add(new ActionSheet.Azione(getString(R.string.elimina), true, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                speseManager().deleteSpesa(spesa.getId());
                loadSpese();
                return Unit.INSTANCE;
            }
        }));
        Context ctx = requireContext();
        String messaggio = getString(R.string.messaggio_sheet,
                TempoFormatter.INSTANCE.formatta(ctx, spesa.getOreNecessarie(), speseManager().getOreGiorno()),
                Ripago.dataOra(spesa.getDataCreazione()));
        ActionSheet.INSTANCE.mostra(ctx, spesa.getNome(), messaggio, azioni);
    }

    private void modificaData(final Spesa spesa) {
        DataPicker.mostra(requireContext(), spesa.getNome(), spesa.getDataCreazione(), new DataPicker.OnScelta() {
            @Override
            public void scelta(long millis) {
                if (!isAdded()) return;
                speseManager().impostaData(spesa.getId(), millis);
                loadSpese();
            }
        });
    }

    private void chiediEliminazione(final Spesa spesa, int posizione) {
        if (!spesa.getApprovata()) {
            speseManager().deleteSpesa(spesa.getId());
            loadSpese();
            return;
        }
        List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
        azioni.add(new ActionSheet.Azione(getString(R.string.elimina), true, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                speseManager().deleteSpesa(spesa.getId());
                loadSpese();
                return Unit.INSTANCE;
            }
        }));
        ActionSheet.INSTANCE.mostra(requireContext(), spesa.getNome(), getString(R.string.conferma_eliminazione_testo), azioni);
        adapter.notifyItemChanged(posizione);
    }

    private void mostraDialogNotifica(final Spesa spesa) {
        final Dialog dialog = new Dialog(requireContext(), R.style.Theme_App_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_slider_notifica);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius(48);
        }
        final TextView tvTempo = dialog.findViewById(R.id.tvTempoSelezionato);
        final Slider slider = dialog.findViewById(R.id.sliderTempo);
        MaterialButton btnAnnulla = dialog.findViewById(R.id.btnAnnulla);
        MaterialButton btnConferma = dialog.findViewById(R.id.btnConferma);
        Premibile.INSTANCE.applica(btnConferma, btnAnnulla);
        android.content.res.Resources r = getResources();
        tempoLabels = new String[]{r.getQuantityString(R.plurals.minuti, 1, 1), r.getQuantityString(R.plurals.minuti, 5, 5),
                r.getQuantityString(R.plurals.minuti, 30, 30), r.getQuantityString(R.plurals.ore, 1, 1),
                r.getQuantityString(R.plurals.ore, 3, 3), r.getQuantityString(R.plurals.ore, 6, 6),
                r.getQuantityString(R.plurals.ore, 12, 12), r.getQuantityString(R.plurals.giorni, 1, 1), getString(R.string.una_settimana)};
        tvTempo.setText(tempoLabels[3]);
        slider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(Slider s, float value, boolean fromUser) {
                tvTempo.setText(tempoLabels[(int) value]);
            }
        });
        btnAnnulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        btnConferma.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                speseManager().impostaNotifica(spesa, tempoDelays[(int) slider.getValue()]);
                loadSpese();
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSpese();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    public void refreshSpese() {
        loadSpese();
    }

    private SpeseManager speseManager() {
        return ((MainActivity) requireActivity()).getSpeseManager();
    }
}
