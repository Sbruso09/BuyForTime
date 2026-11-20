package com.example.appsalario;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Scheda "Buoni pasto" nella Home e finestra per impostarli; piccoli aiuti grafici condivisi con l'importazione. */
final class BuoniUi {
    static final int ACCENTO = 0xFF5E5CE6, SUPERFICIE = 0xFF1C1C1E, ELEVATA = 0xFF2C2C2E, RIEMPIMENTO = 0x3D767680,
            TESTO = 0xFFFFFFFF, TESTO_2 = 0x99EBEBF5, VERDE = 0xFF30D158, ROSSO = 0xFFFF453A;

    private BuoniUi() {}

    static String t(String it, String en) {
        return Lingua.inglese() ? en : it;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable sfondo(Context c, int colore, float raggio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colore);
        g.setCornerRadius(dp(c, raggio));
        return g;
    }

    static TextView testo(Context c, String t, float sp, int colore, boolean grassetto) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(colore);
        Typeface f = null;
        try {
            f = c.getResources().getFont(grassetto ? R.font.inter_semibold : R.font.inter_regular);
        } catch (RuntimeException ignored) {
        }
        if (f != null) v.setTypeface(f);
        else if (grassetto) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static EditText campo(Context c, String testo, boolean numerico) {
        EditText e = new EditText(c);
        e.setText(testo);
        e.setSingleLine(true);
        e.setTextColor(TESTO);
        e.setHintTextColor(0x4DEBEBF5);
        e.setTextSize(17);
        e.setBackground(sfondo(c, 0xFF1C1C1E, 10));
        int p = dp(c, 12);
        e.setPadding(p, dp(c, 11), p, dp(c, 11));
        if (numerico) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        else e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return e;
    }

    static TextView etichetta(Context c, String t) {
        TextView v = testo(c, t.toUpperCase(Lingua.locale()), 12, TESTO_2, false);
        v.setPadding(dp(c, 4), dp(c, 14), 0, dp(c, 6));
        return v;
    }

    static Double numero(EditText e) {
        String s = String.valueOf(e.getText()).trim().replace(" ", "").replace("€", "");
        if (s.isEmpty()) return null;
        if (s.indexOf(',') >= 0) s = s.replace(".", "").replace(',', '.');
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String due(double v) {
        return String.format(Lingua.locale(), "%.2f", v);
    }

    // ------------------------------------------------------------------ scheda nella Home

    /** Crea (una volta) la scheda dei buoni sotto quella del saldo e la aggiorna. */
    static void scheda(final Activity a, View cardSaldo, final Runnable dopo) {
        if (cardSaldo == null || !(cardSaldo.getParent() instanceof LinearLayout)) return;
        LinearLayout padre = (LinearLayout) cardSaldo.getParent();
        View v = padre.findViewWithTag("schedaBuoni");
        LinearLayout card;
        if (v == null) {
            card = new LinearLayout(a);
            card.setTag("schedaBuoni");
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(sfondo(a, SUPERFICIE, 16));
            card.setPadding(dp(a, 16), dp(a, 12), dp(a, 12), dp(a, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.leftMargin = dp(a, 16);
            lp.rightMargin = dp(a, 16);
            lp.topMargin = dp(a, 8);
            lp.bottomMargin = dp(a, 2);
            padre.addView(card, padre.indexOfChild(cardSaldo) + 1, lp);
            Premibile.INSTANCE.applicaA(card);
        } else {
            card = (LinearLayout) v;
            card.removeAllViews();
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                mostraDialog(a, dopo);
            }
        });

        FrameLayout tile = new FrameLayout(a);
        tile.setBackground(sfondo(a, 0xFFFF9F0A, 8));
        ImageView ic = new ImageView(a);
        int id = a.getResources().getIdentifier("ic_buono_pasto", "drawable", a.getPackageName());
        if (id == 0) id = R.drawable.cat_restaurant;
        ic.setImageResource(id);
        ic.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        tile.addView(ic, new FrameLayout.LayoutParams(dp(a, 18), dp(a, 18), Gravity.CENTER));
        card.addView(tile, new LinearLayout.LayoutParams(dp(a, 32), dp(a, 32)));

        LinearLayout testi = new LinearLayout(a);
        testi.setOrientation(LinearLayout.VERTICAL);
        testi.setPadding(dp(a, 12), 0, dp(a, 8), 0);
        testi.addView(testo(a, t("Buoni pasto", "Meal vouchers"), 15, TESTO_2, false));
        String valore;
        int colore = TESTO;
        if (!SaldoBuoni.attivo(a)) {
            valore = t("Tocca per impostarli", "Tap to set them up");
            colore = ACCENTO;
        } else {
            double n = SaldoBuoni.numero(a);
            valore = SaldoBuoni.numeroTesto(n) + " " + t(n == 1 ? "buono" : "buoni", n == 1 ? "voucher" : "vouchers");
            if (SaldoBuoni.valore(a) > 0 && !Privacy.nascosto(a)) valore += " · " + SaldoBuoni.euro(SaldoBuoni.valoreTotale(a));
            if (n < 0) colore = ROSSO;
        }
        testi.addView(testo(a, valore, 20, colore, true));
        card.addView(testi, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView chev = new ImageView(a);
        chev.setImageResource(R.drawable.ic_chevron);
        chev.setImageTintList(ColorStateList.valueOf(0x4DEBEBF5));
        card.addView(chev, new LinearLayout.LayoutParams(dp(a, 18), dp(a, 18)));
    }

    // ------------------------------------------------------------------ impostazione

    static void mostraDialog(final Activity a, final Runnable dopo) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 20);
        box.setPadding(p, p, p, dp(a, 12));
        box.setBackground(sfondo(a, ELEVATA, 16));
        TextView tit = testo(a, t("Buoni pasto", "Meal vouchers"), 17, TESTO, true);
        tit.setGravity(Gravity.CENTER);
        box.addView(tit);
        TextView sotto = testo(a, t("Quanti buoni hai adesso e quanto vale uno. Le spese pagate con i buoni scalano da qui, non dal saldo.",
                "How many vouchers you have now and the value of one. Expenses paid with vouchers are taken from here, not from your balance."),
                13, TESTO_2, false);
        sotto.setGravity(Gravity.CENTER);
        sotto.setPadding(0, dp(a, 6), 0, 0);
        box.addView(sotto);

        box.addView(etichetta(a, t("Buoni disponibili", "Vouchers available")));
        LinearLayout riga = new LinearLayout(a);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        final EditText n = campo(a, SaldoBuoni.attivo(a) ? SaldoBuoni.numeroTesto(SaldoBuoni.numero(a)) : "", true);
        n.setGravity(Gravity.CENTER);
        n.setHint("0");
        TextView meno = tondo(a, "−"), piu = tondo(a, "+");
        meno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double x = numero(n);
                n.setText(SaldoBuoni.numeroTesto(Math.max(0, (x == null ? 0 : x) - 1)));
            }
        });
        piu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double x = numero(n);
                n.setText(SaldoBuoni.numeroTesto((x == null ? 0 : x) + 1));
            }
        });
        int s = dp(a, 44);
        riga.addView(meno, new LinearLayout.LayoutParams(s, s));
        LinearLayout.LayoutParams lq = new LinearLayout.LayoutParams(0, -2, 1f);
        lq.leftMargin = dp(a, 10);
        lq.rightMargin = dp(a, 10);
        riga.addView(n, lq);
        riga.addView(piu, new LinearLayout.LayoutParams(s, s));
        box.addView(riga);

        box.addView(etichetta(a, t("Valore di un buono (€)", "Value of one voucher (€)")));
        final EditText val = campo(a, SaldoBuoni.valore(a) > 0 ? due(SaldoBuoni.valore(a)) : "", true);
        val.setHint("8,00");
        box.addView(val, new LinearLayout.LayoutParams(-1, -2));

        TextView salva = testo(a, t("Salva", "Save"), 17, Color.WHITE, true);
        salva.setGravity(Gravity.CENTER);
        salva.setBackground(sfondo(a, ACCENTO, 14));
        salva.setPadding(0, dp(a, 13), 0, dp(a, 13));
        salva.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double nn = numero(n), vv = numero(val);
                if (nn == null || vv == null || vv <= 0) {
                    Toast.makeText(a, t("Inserisci il numero di buoni e il loro valore.", "Enter the number of vouchers and their value."),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                SaldoBuoni.imposta(a, nn, vv.floatValue());
                d.dismiss();
                if (dopo != null) dopo.run();
            }
        });
        LinearLayout.LayoutParams ls = new LinearLayout.LayoutParams(-1, -2);
        ls.topMargin = dp(a, 20);
        box.addView(salva, ls);
        TextView annulla = testo(a, t("Annulla", "Cancel"), 17, ACCENTO, false);
        annulla.setGravity(Gravity.CENTER);
        annulla.setPadding(0, dp(a, 12), 0, dp(a, 6));
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        box.addView(annulla);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.88f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    static TextView tondo(Context c, String t) {
        TextView v = testo(c, t, 24, ACCENTO, true);
        v.setBackground(sfondo(c, RIEMPIMENTO, 22));
        v.setGravity(Gravity.CENTER);
        return v;
    }
}
