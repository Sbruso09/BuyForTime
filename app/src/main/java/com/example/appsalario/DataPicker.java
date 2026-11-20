package com.example.appsalario;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.Calendar;

/** Selettore data/ora a ruote in stile iOS, presentato come sheet (stesso stile di ActionSheet). */
public final class DataPicker {

    public interface OnScelta {
        void scelta(long millis);
    }


    private DataPicker() {}

    public static void mostra(Context context, String titolo, long iniziale, final OnScelta onScelta) {
        final BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.ThemeOverlay_App_BottomSheet);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_data, null);
        dialog.setContentView(view);
        BottomSheetBehavior<?> behavior = dialog.getBehavior();
        behavior.setDraggable(false); // le ruote scorrono in verticale: lo sheet non deve rubare il gesto
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        if (titolo != null) ((TextView) view.findViewById(R.id.tvTitoloData)).setText(titolo);

        final NumberPicker npGiorno = view.findViewById(R.id.npGiorno);
        final NumberPicker npMese = view.findViewById(R.id.npMese);
        final NumberPicker npAnno = view.findViewById(R.id.npAnno);
        final NumberPicker npOra = view.findViewById(R.id.npOra);
        final NumberPicker npMinuto = view.findViewById(R.id.npMinuto);

        int coloreTesto = ContextCompat.getColor(context, R.color.text_primary);
        float dimensione = 21f * context.getResources().getDisplayMetrics().scaledDensity;
        for (NumberPicker np : new NumberPicker[]{npGiorno, npMese, npAnno, npOra, npMinuto}) {
            np.setTextColor(coloreTesto);
            np.setTextSize(dimensione);
            np.setSelectionDividerHeight(0);
            np.setWrapSelectorWheel(true);
        }

        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(iniziale);
        int annoCorrente = Calendar.getInstance().get(Calendar.YEAR);
        int annoMin = Math.min(annoCorrente - 30, cal.get(Calendar.YEAR));
        int annoMax = Math.max(annoCorrente + 10, cal.get(Calendar.YEAR));

        npMese.setMinValue(0);
        npMese.setMaxValue(11);
        npMese.setDisplayedValues(Lingua.mesiCorti());
        npAnno.setMinValue(annoMin);
        npAnno.setMaxValue(annoMax);
        npAnno.setWrapSelectorWheel(false);
        npOra.setMinValue(0);
        npOra.setMaxValue(23);
        npOra.setDisplayedValues(dueCifre(24));
        npMinuto.setMinValue(0);
        npMinuto.setMaxValue(59);
        npMinuto.setDisplayedValues(dueCifre(60));
        npGiorno.setMinValue(1);

        npMese.setValue(cal.get(Calendar.MONTH));
        npAnno.setValue(cal.get(Calendar.YEAR));
        aggiornaGiorni(npGiorno, npMese.getValue(), npAnno.getValue());
        npGiorno.setValue(Math.min(cal.get(Calendar.DAY_OF_MONTH), npGiorno.getMaxValue()));
        npOra.setValue(cal.get(Calendar.HOUR_OF_DAY));
        npMinuto.setValue(cal.get(Calendar.MINUTE));

        NumberPicker.OnValueChangeListener ricalcola = new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int vecchio, int nuovo) {
                aggiornaGiorni(npGiorno, npMese.getValue(), npAnno.getValue());
            }
        };
        npMese.setOnValueChangedListener(ricalcola);
        npAnno.setOnValueChangedListener(ricalcola);

        view.findViewById(R.id.btnAdessoData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar ora = Calendar.getInstance();
                npAnno.setValue(Math.max(npAnno.getMinValue(), Math.min(npAnno.getMaxValue(), ora.get(Calendar.YEAR))));
                npMese.setValue(ora.get(Calendar.MONTH));
                aggiornaGiorni(npGiorno, npMese.getValue(), npAnno.getValue());
                npGiorno.setValue(ora.get(Calendar.DAY_OF_MONTH));
                npOra.setValue(ora.get(Calendar.HOUR_OF_DAY));
                npMinuto.setValue(ora.get(Calendar.MINUTE));
            }
        });
        view.findViewById(R.id.btnAnnullaData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.btnFattoData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(npAnno.getValue(), npMese.getValue(), npGiorno.getValue(), npOra.getValue(), npMinuto.getValue(), 0);
                dialog.dismiss();
                onScelta.scelta(c.getTimeInMillis());
            }
        });
        dialog.show();
    }

    public interface OnOra {
        void scelta(int minuti);
    }

    /** Solo ore e minuti (es. orario di lavoro). */
    public static void mostraOra(Context context, String titolo, int minuti, final OnOra onOra) {
        final BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.ThemeOverlay_App_BottomSheet);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_data, null);
        dialog.setContentView(view);
        dialog.getBehavior().setDraggable(false);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        ((TextView) view.findViewById(R.id.tvTitoloData)).setText(titolo);
        view.findViewById(R.id.npGiorno).setVisibility(View.GONE);
        view.findViewById(R.id.npMese).setVisibility(View.GONE);
        view.findViewById(R.id.npAnno).setVisibility(View.GONE);
        view.findViewById(R.id.btnAdessoData).setVisibility(View.GONE);
        final NumberPicker npOra = view.findViewById(R.id.npOra);
        final NumberPicker npMinuto = view.findViewById(R.id.npMinuto);
        int colore = ContextCompat.getColor(context, R.color.text_primary);
        float dim = 22f * context.getResources().getDisplayMetrics().scaledDensity;
        for (NumberPicker np : new NumberPicker[]{npOra, npMinuto}) {
            np.setTextColor(colore);
            np.setTextSize(dim);
            np.setSelectionDividerHeight(0);
            np.setWrapSelectorWheel(true);
            np.setMinValue(0);
        }
        android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) npOra.getLayoutParams();
        lp.setMarginStart(0);
        npOra.setLayoutParams(lp);
        npOra.setMaxValue(23);
        npOra.setDisplayedValues(dueCifre(24));
        // minuti a passi di 5, come i selettori orari di iOS
        final String[] min5 = new String[12];
        for (int i = 0; i < 12; i++) min5[i] = (i < 2 ? "0" : "") + (i * 5);
        npMinuto.setMaxValue(11);
        npMinuto.setDisplayedValues(min5);
        int m = ((minuti % 1440) + 1440) % 1440;
        npOra.setValue(m / 60);
        npMinuto.setValue(Math.min(11, Math.round((m % 60) / 5f)));
        view.findViewById(R.id.btnAnnullaData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.btnFattoData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                onOra.scelta(npOra.getValue() * 60 + npMinuto.getValue() * 5);
            }
        });
        dialog.show();
    }

    public interface OnValore {
        void scelta(int indice);
    }

    /** Una sola ruota con valori a scelta (giorno del mese, mese, frequenza...). */
    public static void mostraValori(Context context, String titolo, String[] valori, int selezionato, final OnValore cb) {
        final BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.ThemeOverlay_App_BottomSheet);
        View view = LayoutInflater.from(context).inflate(R.layout.sheet_data, null);
        dialog.setContentView(view);
        dialog.getBehavior().setDraggable(false);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        ((TextView) view.findViewById(R.id.tvTitoloData)).setText(titolo);
        for (int id : new int[]{R.id.npGiorno, R.id.npAnno, R.id.npOra, R.id.npMinuto, R.id.btnAdessoData}) {
            view.findViewById(id).setVisibility(View.GONE);
        }
        final NumberPicker np = view.findViewById(R.id.npMese);
        np.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        np.setTextSize(22f * context.getResources().getDisplayMetrics().scaledDensity);
        np.setSelectionDividerHeight(0);
        np.setMinValue(0);
        np.setMaxValue(valori.length - 1);
        np.setDisplayedValues(valori);
        np.setWrapSelectorWheel(valori.length > 3);
        np.setValue(Math.max(0, Math.min(valori.length - 1, selezionato)));
        view.findViewById(R.id.btnAnnullaData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.btnFattoData).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                cb.scelta(np.getValue());
            }
        });
        dialog.show();
    }

    public static String[] mesiLunghi() {
        return Lingua.mesiLunghi();
    }

    public static String[] giorniDelMese() {
        String[] g = new String[31];
        for (int i = 0; i < 31; i++) g[i] = String.valueOf(i + 1);
        return g;
    }

    private static void aggiornaGiorni(NumberPicker npGiorno, int mese, int anno) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(anno, mese, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        int valore = npGiorno.getValue();
        npGiorno.setMaxValue(max);
        if (valore > max) npGiorno.setValue(max);
    }

    private static String[] dueCifre(int n) {
        String[] out = new String[n];
        for (int i = 0; i < n; i++) out[i] = (i < 10 ? "0" : "") + i;
        return out;
    }
}
