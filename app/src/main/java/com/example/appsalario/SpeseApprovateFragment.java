package com.example.appsalario;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

public final class SpeseApprovateFragment extends Fragment {
    private SpeseAdapter adapter;
    private AppBarLayout appBar;
    private RecyclerView rvSpeseApprovate;
    private View statoVuoto;
    private TextView tvTotaleOre;
    private TextView tvTotaleSpeso;
    private View boxRiepilogo;
    private ImageView ivRiepilogo;
    private TextView tvRiepilogo;
    private View cardCategorie;
    private View tvHeaderCategorie;
    private LinearLayout barraCategorie;
    private LinearLayout listaCategorie;
    private Map<String, Ripago.Piano> piani = Collections.emptyMap();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) adapter.tick();
            aggiornaRiepilogoRipago();
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L));
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spese_approvate, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        appBar = view.findViewById(R.id.appBar);
        rvSpeseApprovate = view.findViewById(R.id.rvSpeseApprovate);
        statoVuoto = view.findViewById(R.id.tvListaVuota);
        tvTotaleSpeso = view.findViewById(R.id.tvTotaleSpeso);
        tvTotaleOre = view.findViewById(R.id.tvTotaleOre);
        boxRiepilogo = view.findViewById(R.id.boxRiepilogoRipago);
        ivRiepilogo = view.findViewById(R.id.ivRiepilogoRipago);
        tvRiepilogo = view.findViewById(R.id.tvRiepilogoRipago);
        cardCategorie = view.findViewById(R.id.cardCategorie);
        tvHeaderCategorie = view.findViewById(R.id.tvHeaderCategorie);
        barraCategorie = view.findViewById(R.id.barraCategorie);
        barraCategorie.setClipToOutline(true);
        listaCategorie = view.findViewById(R.id.listaCategorie);
        Barre.INSTANCE.applica(view, appBar, rvSpeseApprovate);
        collegaTitoloAlloScroll();
        setupRecyclerView();
        loadSpeseApprovate();
    }

    private void collegaTitoloAlloScroll() {
        final String titolo = getString(R.string.tab_approvate);
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
        rvSpeseApprovate.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSpeseApprovate.setAdapter(adapter);
        new SwipeElimina(rvSpeseApprovate, new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer posizione) {
                Spesa spesa = adapter.spesaAt(posizione);
                adapter.notifyItemChanged(posizione);
                chiediEliminazione(spesa);
                return Unit.INSTANCE;
            }
        }).collega();
    }

    private void loadSpeseApprovate() {
        SpeseManager m = speseManager();
        List<Spesa> tutte = m.getSpese();
        List<Spesa> approvate = new ArrayList<Spesa>();
        for (Spesa s : tutte) if (s.getApprovata()) approvate.add(s);
        piani = Ripago.pianifica(tutte, m);
        float oreGiorno = m.getOreGiorno();
        NumberFormat valuta = NumberFormat.getCurrencyInstance(Locale.ITALY);
        Context ctx = requireContext();

        if (approvate.isEmpty()) {
            statoVuoto.setVisibility(View.VISIBLE);
            rvSpeseApprovate.setVisibility(View.GONE);
        } else {
            statoVuoto.setVisibility(View.GONE);
            rvSpeseApprovate.setVisibility(View.VISIBLE);
        }
        adapter.updateSpese(approvate, piani);

        double speso = 0, ore = 0;
        for (Spesa s : approvate) {
            speso += s.getImporto();
            ore += s.getOreNecessarie();
        }
        tvTotaleSpeso.setText(valuta.format(speso));
        Privacy.sfoca(ctx, tvTotaleSpeso);
        tvTotaleOre.setText(TempoFormatter.INSTANCE.formatta(ctx, (float) ore, oreGiorno));
        aggiornaRiepilogoRipago();
        mostraCategorie(approvate, speso, oreGiorno, valuta);
    }

    private void aggiornaRiepilogoRipago() {
        if (boxRiepilogo == null) return;
        if (piani.isEmpty()) {
            boxRiepilogo.setVisibility(View.GONE);
            return;
        }
        Context ctx = getContext();
        if (ctx == null) return;
        boxRiepilogo.setVisibility(View.VISIBLE);
        long fine = Ripago.fineTotale(piani);
        if (System.currentTimeMillis() >= fine) {
            int verde = ContextCompat.getColor(ctx, R.color.tempo);
            ivRiepilogo.setImageResource(R.drawable.ic_checkmark_circle);
            ivRiepilogo.setImageTintList(ColorStateList.valueOf(verde));
            tvRiepilogo.setTextColor(verde);
            tvRiepilogo.setText(R.string.riepilogo_tutto_ripagato);
        } else {
            int grigio = ContextCompat.getColor(ctx, R.color.text_secondary);
            ivRiepilogo.setImageResource(R.drawable.ic_hourglass);
            ivRiepilogo.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.tempo)));
            tvRiepilogo.setTextColor(grigio);
            tvRiepilogo.setText(getString(R.string.riepilogo_libero_il, Ripago.dataOra(fine)));
        }
    }


    private static final int MAX_CATEGORIE = 6;

    /** Ripartizione per categoria: barra impilata (stile "Tempo di utilizzo") + legenda. */
    private void mostraCategorie(List<Spesa> approvate, double totale, float oreGiorno, NumberFormat valuta) {
        Context ctx = requireContext();
        if (approvate.isEmpty() || totale <= 0) {
            cardCategorie.setVisibility(View.GONE);
            tvHeaderCategorie.setVisibility(View.GONE);
            return;
        }
        cardCategorie.setVisibility(View.VISIBLE);
        tvHeaderCategorie.setVisibility(View.VISIBLE);

        final java.util.LinkedHashMap<String, double[]> perCat = new java.util.LinkedHashMap<String, double[]>();
        for (Spesa s : approvate) {
            String key = Categorie.get(ctx, s.getCategoria()).key;
            double[] v = perCat.get(key);
            if (v == null) perCat.put(key, v = new double[2]);
            v[0] += s.getImporto();
            v[1] += s.getOreNecessarie();
        }
        List<String> chiavi = new ArrayList<String>(perCat.keySet());
        java.util.Collections.sort(chiavi, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Double.compare(perCat.get(b)[0], perCat.get(a)[0]);
            }
        });

        // voci: prime MAX_CATEGORIE-1, il resto in "Altre" (se ne avanzano almeno 2)
        List<Object[]> voci = new ArrayList<Object[]>(); // {nome, colore, iconaRes, importo, ore}
        double restoImp = 0, restoOre = 0;
        int nResto = 0;
        for (int i = 0; i < chiavi.size(); i++) {
            Categorie.Categoria c = Categorie.get(ctx, chiavi.get(i));
            double[] v = perCat.get(chiavi.get(i));
            if (chiavi.size() > MAX_CATEGORIE && i >= MAX_CATEGORIE - 1) {
                restoImp += v[0];
                restoOre += v[1];
                nResto++;
            } else {
                voci.add(new Object[]{c.nome, c.colore, c.iconaRes(), v[0], v[1]});
            }
        }
        if (nResto > 0) {
            voci.add(new Object[]{getString(R.string.altre_categorie),
                    ContextCompat.getColor(ctx, R.color.system_gray2), R.drawable.cat_category, restoImp, restoOre});
        }

        barraCategorie.removeAllViews();
        listaCategorie.removeAllViews();
        int dp = (int) getResources().getDisplayMetrics().density;
        for (int i = 0; i < voci.size(); i++) {
            Object[] v = voci.get(i);
            int colore = (Integer) v[1];
            double imp = (Double) v[3];

            View segmento = new View(ctx);
            segmento.setBackgroundColor(colore);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) (imp / totale));
            if (i < voci.size() - 1) lp.setMarginEnd(Math.max(1, 2 * dp));
            barraCategorie.addView(segmento, lp);

            View riga = LayoutInflater.from(ctx).inflate(R.layout.item_riga_categoria, listaCategorie, false);
            View tile = riga.findViewById(R.id.tileRigaCategoria);
            tile.setBackgroundTintList(ColorStateList.valueOf(colore));
            ((ImageView) riga.findViewById(R.id.ivRigaCategoria)).setImageResource((Integer) v[2]);
            ((TextView) riga.findViewById(R.id.tvRigaNome)).setText((String) v[0]);
            TextView tvImp = riga.findViewById(R.id.tvRigaImporto);
            tvImp.setText(valuta.format(imp));
            Privacy.sfoca(ctx, tvImp);
            ((TextView) riga.findViewById(R.id.tvRigaDettaglio)).setText(
                    Math.round(imp / totale * 100) + "% · " + TempoFormatter.INSTANCE.formatta(ctx, ((Double) v[4]).floatValue(), oreGiorno));
            riga.findViewById(R.id.sepRigaCategoria).setVisibility(i == 0 ? View.GONE : View.VISIBLE);
            listaCategorie.addView(riga);
        }
    }

    private void mostraAzioni(final Spesa spesa) {
        List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
        if (spesa.getOrigine() != null) {
            // spesa alimentare arrivata dall'app Buoni Pasto: l'altra persona ridà la sua metà in soldi
            final double meta = Math.round(spesa.getImporto() * 50.0) / 100.0;
            String eur = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.ITALY).format(meta);
            String testo = spesa.getRipagata()
                    ? BuoniUi.t("Annulla \"ripagata\" (−" + eur + " dal saldo)", "Undo \"paid back\" (−" + eur + " from balance)")
                    : BuoniUi.t("Ripagata: accredita metà (+" + eur + ")", "Paid back: credit half (+" + eur + ")");
            azioni.add(new ActionSheet.Azione(testo, false, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    boolean ok = speseManager().impostaRipagata(spesa.getId(), !spesa.getRipagata());
                    if (!ok) {
                        android.widget.Toast.makeText(requireContext(), BuoniUi.t(
                                "Attiva prima il saldo nella Home per poter accreditare la metà.",
                                "Set up your balance on the Home first to credit the half."), android.widget.Toast.LENGTH_LONG).show();
                    }
                    loadSpeseApprovate();
                    return Unit.INSTANCE;
                }
            }));
        }
        azioni.add(new ActionSheet.Azione(getString(spesa.getTracciaUtilizzi() ? R.string.traccia_utilizzi_off : R.string.traccia_utilizzi_on), false, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                speseManager().impostaTracciaUtilizzi(spesa.getId(), !spesa.getTracciaUtilizzi());
                loadSpeseApprovate();
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
                        loadSpeseApprovate();
                    }
                });
                return Unit.INSTANCE;
            }
        }));
        azioni.add(new ActionSheet.Azione(getString(R.string.modifica_data), false, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                DataPicker.mostra(requireContext(), spesa.getNome(), spesa.getDataCreazione(), new DataPicker.OnScelta() {
                    @Override
                    public void scelta(long millis) {
                        if (!isAdded()) return;
                        speseManager().impostaData(spesa.getId(), millis);
                        loadSpeseApprovate();
                    }
                });
                return Unit.INSTANCE;
            }
        }));
        azioni.add(new ActionSheet.Azione(getString(R.string.elimina), true, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                chiediEliminazione(spesa);
                return Unit.INSTANCE;
            }
        }));
        Context ctx = requireContext();
        String messaggio = getString(R.string.messaggio_sheet,
                TempoFormatter.INSTANCE.formatta(ctx, spesa.getOreNecessarie(), speseManager().getOreGiorno()),
                Ripago.dataOra(spesa.getDataCreazione()));
        ActionSheet.INSTANCE.mostra(ctx, spesa.getNome(), messaggio, azioni);
    }

    private void chiediEliminazione(final Spesa spesa) {
        List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
        azioni.add(new ActionSheet.Azione(getString(R.string.elimina), true, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                speseManager().deleteSpesa(spesa.getId());
                loadSpeseApprovate();
                return Unit.INSTANCE;
            }
        }));
        ActionSheet.INSTANCE.mostra(requireContext(), spesa.getNome(), getString(R.string.conferma_eliminazione_testo), azioni);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSpeseApprovate();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    public void refreshSpese() {
        loadSpeseApprovate();
    }

    private SpeseManager speseManager() {
        return ((MainActivity) requireActivity()).getSpeseManager();
    }
}
