package com.example.appsalario;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Scheda Saldo nella pagina Spese, dialog di rettifica e storico movimenti in stile Wallet. */
public final class SaldoUi {
    private static final NumberFormat VALUTA = NumberFormat.getCurrencyInstance(Locale.ITALY);

    private SaldoUi() {}

    static String importoConSegno(double v) {
        return (v >= 0 ? "+" : "−") + VALUTA.format(Math.abs(v));
    }

    private static String data(long ms, String formato) {
        return new SimpleDateFormat(formato, Lingua.locale()).format(new Date(ms));
    }

    /** Nome del movimento nella lingua dell'app (i nomi automatici sono salvati in italiano). */
    static String nome(Context ctx, Saldo.Movimento m) {
        Context l = Lingua.contesto(ctx);
        switch (m.tipo) {
            case Saldo.STIPENDIO: return l.getString(R.string.mov_stipendio);
            case Saldo.TREDICESIMA: return l.getString(R.string.mov_tredicesima);
            case Saldo.QUATTORDICESIMA: return l.getString(R.string.mov_quattordicesima);
            case Saldo.RETTIFICA:
                if ("Saldo iniziale".equals(m.nome)) return l.getString(R.string.mov_saldo_iniziale);
                if ("Rettifica".equals(m.nome)) return l.getString(R.string.mov_rettifica);
                return m.nome;
            case Saldo.RIMBORSO: return l.getString(R.string.mov_eliminata, m.nome);
            default: return m.nome;
        }
    }

    // ------------------------------------------------------------------ scheda

    public static void aggiornaCard(Context ctx, View card) {
        aggiornaCard(ctx, card, null);
    }

    /** {@code onVerifica}: azione per correggere il saldo quando compare l'invito a verificarlo. */
    public static void aggiornaCard(Context ctx, View card, final Runnable onVerifica) {
        View vuoto = card.findViewById(R.id.boxSaldoVuoto);
        View pieno = card.findViewById(R.id.boxSaldo);
        if (!Saldo.attivo(ctx)) {
            vuoto.setVisibility(View.VISIBLE);
            pieno.setVisibility(View.GONE);
            return;
        }
        vuoto.setVisibility(View.GONE);
        pieno.setVisibility(View.VISIBLE);
        boolean nascosto = Privacy.nascosto(ctx);

        double saldo = Saldo.saldo(ctx);
        TextView tvSaldo = card.findViewById(R.id.tvSaldo);
        tvSaldo.setText(VALUTA.format(saldo));
        tvSaldo.setTextColor(ContextCompat.getColor(ctx, saldo < 0 ? R.color.danger : R.color.text_primary));
        Privacy.sfoca(tvSaldo, nascosto);

        TextView tvProssimo = card.findViewById(R.id.tvProssimoMovimento);
        List<Saldo.Movimento> prossimi = Saldo.prossimi(ctx, 62);
        if (prossimi.isEmpty()) {
            tvProssimo.setVisibility(View.GONE);
        } else {
            Saldo.Movimento m = prossimi.get(0);
            tvProssimo.setVisibility(View.VISIBLE);
            // con gli importi nascosti mostro solo nome e data
            tvProssimo.setText(nascosto
                    ? nome(ctx, m) + " · " + data(m.ts, "d MMM")
                    : ctx.getString(R.string.saldo_prossimo, nome(ctx, m), importoConSegno(m.importo), data(m.ts, "d MMM")));
        }

        TextView tvPrevisto = card.findViewById(R.id.tvPrevisto);
        double[] previsto = Saldo.previstoAlProssimoStipendio(ctx);
        if (previsto == null) {
            tvPrevisto.setVisibility(View.GONE);
        } else {
            tvPrevisto.setVisibility(View.VISIBLE);
            tvPrevisto.setText(ctx.getString(R.string.saldo_previsto, data((long) previsto[1], "d MMM"), VALUTA.format(previsto[0])));
            tvPrevisto.setTextColor(ContextCompat.getColor(ctx, previsto[0] < 0 ? R.color.danger : R.color.text_secondary));
            Privacy.sfoca(tvPrevisto, nascosto);
        }

        // avvisi permanenti sulla scheda: prima il rosso, poi l'invito a verificare
        View riga = card.findViewById(R.id.rigaAvvisoSaldo);
        ImageView ivAvv = card.findViewById(R.id.ivAvvisoSaldo);
        TextView tvAvv = card.findViewById(R.id.tvAvvisoSaldo);
        long giorniVerifica = (System.currentTimeMillis() - Saldo.ultimaVerifica(ctx)) / 86_400_000L;
        if (previsto != null && previsto[0] < 0) {
            riga.setVisibility(View.VISIBLE);
            int rosso = ContextCompat.getColor(ctx, R.color.danger);
            ivAvv.setImageResource(R.drawable.ic_error);
            ivAvv.setImageTintList(ColorStateList.valueOf(rosso));
            riga.setBackgroundTintList(ColorStateList.valueOf((rosso & 0x00FFFFFF) | 0x26000000));
            tvAvv.setText(ctx.getString(R.string.avviso_sotto_zero, data((long) previsto[1], "d MMM")));
            riga.setOnClickListener(null);
            riga.setClickable(false);
        } else if (Saldo.ultimaVerifica(ctx) > 0 && giorniVerifica > 60) {
            riga.setVisibility(View.VISIBLE);
            int acc = ContextCompat.getColor(ctx, R.color.accent);
            ivAvv.setImageResource(R.drawable.ic_info);
            ivAvv.setImageTintList(ColorStateList.valueOf(acc));
            riga.setBackgroundTintList(ColorStateList.valueOf((acc & 0x00FFFFFF) | 0x26000000));
            tvAvv.setText(ctx.getString(R.string.avviso_verifica, (int) giorniVerifica));
            if (onVerifica != null) {
                riga.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onVerifica.run();
                    }
                });
            }
        } else {
            riga.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ rettifica

    public static void mostraDialog(final Activity a, final Runnable dopo) {
        final Dialog dialog = new Dialog(a, R.style.Theme_App_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_saldo);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.88), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius(48);
        }
        final TextInputEditText et = dialog.findViewById(R.id.etSaldo);
        MaterialButton salva = dialog.findViewById(R.id.btnSalvaSaldo);
        MaterialButton annulla = dialog.findViewById(R.id.btnAnnullaSaldo);
        Premibile.INSTANCE.applica(salva, annulla);
        if (Saldo.attivo(a) && !Privacy.nascosto(a)) {
            double s = Saldo.saldo(a);
            et.setText(String.format(Locale.ITALY, "%.2f", s));
        }
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        salva.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = String.valueOf(et.getText()).trim();
                boolean negativo = t.startsWith("-");
                Float valore = Numeri.toFloatOrNull(negativo ? t.substring(1) : t);
                if (valore == null) {
                    Toast.makeText(a, R.string.errore_campi_vuoti, Toast.LENGTH_SHORT).show();
                    return;
                }
                // precisione al centesimo: parse dalla stringa, non dal float
                double v2;
                try {
                    String norm = (negativo ? t.substring(1) : t).replace(" ", "");
                    if (norm.indexOf(',') >= 0) norm = norm.replace(".", "").replace(',', '.');
                    v2 = Double.parseDouble(norm);
                } catch (NumberFormatException e) {
                    v2 = valore;
                }
                Saldo.imposta(a, negativo ? -v2 : v2);
                dialog.dismiss();
                if (dopo != null) dopo.run();
            }
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ movimenti

    public static void mostraMovimenti(final Context ctx) {
        final BottomSheetDialog dialog = new BottomSheetDialog(ctx, R.style.ThemeOverlay_App_BottomSheet);
        View view = LayoutInflater.from(ctx).inflate(R.layout.sheet_movimenti, null);
        NestedScrollView scroll = new NestedScrollView(ctx);
        scroll.addView(view);
        dialog.setContentView(scroll);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        view.findViewById(R.id.btnChiudiMovimenti).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        LinearLayout lista = view.findViewById(R.id.listaMovimenti);
        boolean nascosto = Privacy.nascosto(ctx);

        List<Saldo.Movimento> futuri = Saldo.prossimi(ctx, 31);
        if (!futuri.isEmpty()) {
            intestazione(ctx, lista, ctx.getString(R.string.movimenti_in_arrivo));
            boolean primo = true;
            for (Saldo.Movimento m : futuri) {
                riga(ctx, lista, m, primo, true, nascosto);
                primo = false;
            }
        }

        List<Saldo.Movimento> storico = new ArrayList<Saldo.Movimento>();
        long ora = System.currentTimeMillis();
        for (Saldo.Movimento m : Saldo.movimenti(ctx)) if (m.ts <= ora) storico.add(m); // i futuri sono "In arrivo"
        Collections.sort(storico, Collections.reverseOrder(Saldo.PER_DATA));
        if (storico.isEmpty()) {
            TextView tv = new TextView(ctx);
            tv.setTextAppearance(R.style.TextAppearance_App_Footnote);
            tv.setText(R.string.movimenti_vuoto);
            int p = Math.round(16 * ctx.getResources().getDisplayMetrics().density);
            tv.setPadding(0, p, 0, p);
            lista.addView(tv);
        }
        String meseCorrente = null;
        int mostrati = 0;
        boolean primo = true;
        for (Saldo.Movimento m : storico) {
            if (mostrati++ >= 400) break;
            String mese = data(m.ts, "MMMM yyyy");
            if (!mese.equals(meseCorrente)) {
                intestazione(ctx, lista, mese);
                meseCorrente = mese;
                primo = true;
            }
            riga(ctx, lista, m, primo, false, nascosto);
            primo = false;
        }
        dialog.show();
    }

    private static void intestazione(Context ctx, LinearLayout lista, String testo) {
        TextView tv = new TextView(ctx);
        tv.setTextAppearance(R.style.TextAppearance_App_GroupHeader);
        tv.setText(testo);
        float d = ctx.getResources().getDisplayMetrics().density;
        tv.setPadding(0, Math.round(18 * d), 0, Math.round(4 * d));
        lista.addView(tv);
    }

    private static void riga(Context ctx, LinearLayout lista, Saldo.Movimento m, boolean primo, boolean futuro, boolean nascosto) {
        View r = LayoutInflater.from(ctx).inflate(R.layout.item_movimento, lista, false);
        r.findViewById(R.id.sepMovimento).setVisibility(primo ? View.GONE : View.VISIBLE);
        View tile = r.findViewById(R.id.tileMovimento);
        ImageView iv = r.findViewById(R.id.ivMovimento);
        int colore;
        int icona;
        switch (m.tipo) {
            case Saldo.STIPENDIO:
                colore = ContextCompat.getColor(ctx, R.color.tempo);
                icona = R.drawable.ic_payments;
                break;
            case Saldo.TREDICESIMA:
            case Saldo.QUATTORDICESIMA:
                colore = 0xFF66D4CF;
                icona = R.drawable.cat_redeem;
                break;
            case Saldo.FISSA:
                colore = 0xFF0A84FF;
                icona = R.drawable.ic_receipt_long;
                break;
            case Saldo.SPESA: {
                Categorie.Categoria c = Categorie.get(ctx, m.rif);
                colore = c.colore;
                icona = c.iconaRes();
                break;
            }
            case Saldo.RIMBORSO:
                colore = ContextCompat.getColor(ctx, R.color.system_gray2);
                icona = R.drawable.ic_undo;
                break;
            case Saldo.RIPAGATA:
                colore = 0xFF30D158;
                icona = R.drawable.ic_payments;
                break;
            default:
                colore = ContextCompat.getColor(ctx, R.color.system_gray2);
                icona = R.drawable.ic_edit;
                break;
        }
        tile.setBackgroundTintList(ColorStateList.valueOf(colore));
        iv.setImageResource(icona);

        TextView nome = r.findViewById(R.id.tvMovimentoNome);
        nome.setText(nome(ctx, m));
        ((TextView) r.findViewById(R.id.tvMovimentoData)).setText(
                m.tipo == Saldo.SPESA || m.tipo == Saldo.RETTIFICA || m.tipo == Saldo.RIMBORSO || m.tipo == Saldo.RIPAGATA
                        ? data(m.ts, "d MMM, HH:mm") : data(m.ts, "EEEE d MMMM"));

        TextView imp = r.findViewById(R.id.tvMovimentoImporto);
        if (m.tipo == Saldo.RETTIFICA && "Saldo iniziale".equals(m.nome)) {
            imp.setText(VALUTA.format(m.importo));
            imp.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
        } else {
            imp.setText(importoConSegno(m.importo));
            imp.setTextColor(ContextCompat.getColor(ctx, m.importo >= 0 ? R.color.tempo : R.color.text_primary));
        }
        Privacy.sfoca(imp, nascosto);
        if (futuro) r.setAlpha(0.55f);
        lista.addView(r);
    }
}
