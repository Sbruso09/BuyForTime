package com.example.appsalario;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Riceve una spesa alimentare dall'app Buoni Pasto. Si sceglie se e' stata pagata con il saldo o con i buoni pasto:
 * con i buoni scalano i buoni (e dal saldo solo l'eventuale parte in contanti).
 */
public final class ImportaSpesa extends Activity {
    public static final String AZIONE = "com.example.appsalario.IMPORTA_SPESA";

    private boolean conBuoni = true;
    private EditText etNome, etImporto, etBuoni;
    private TextView tvBuoni, tvSaldo, tvDettaglio, segBuoni, segSaldo;
    private LinearLayout boxBuoni;
    private float valoreBuono;

    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        Intent in = getIntent();
        final String id = in.getStringExtra("bp_id");
        String nota = in.getStringExtra("bp_nota");
        final long data = in.getLongExtra("bp_data", System.currentTimeMillis());
        final double buoni = in.getIntExtra("bp_buoni", 0);
        long valoreCent = in.getLongExtra("bp_valore_cent", 0);
        long extraCent = in.getLongExtra("bp_extra_cent", 0);
        final boolean meta = in.getBooleanExtra("bp_meta", false);
        valoreBuono = valoreCent / 100f;
        SaldoBuoni.valoreSeManca(this, valoreBuono);
        if (valoreBuono <= 0) valoreBuono = SaldoBuoni.valore(this);
        final double totale = (buoni * valoreCent + extraCent) / 100.0;

        // ---- sfondo scuro e scheda al centro
        FrameLayout radice = new FrameLayout(this);
        radice.setBackgroundColor(0x99000000);
        radice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = BuoniUi.dp(this, 20);
        box.setPadding(p, p, p, BuoniUi.dp(this, 12));
        box.setBackground(BuoniUi.sfondo(this, BuoniUi.ELEVATA, 16));
        box.setClickable(true);
        sv.addView(box);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9f), -2, Gravity.CENTER);
        radice.addView(sv, lp);
        setContentView(radice);

        TextView tit = BuoniUi.testo(this, BuoniUi.t("Spesa da Buoni Pasto", "Expense from Buoni Pasto"), 17, BuoniUi.TESTO, true);
        tit.setGravity(Gravity.CENTER);
        box.addView(tit);
        String quando = new SimpleDateFormat("d MMMM yyyy", Lingua.locale()).format(new Date(data));
        String det = quando + " · " + SaldoBuoni.numeroTesto(buoni) + " " + BuoniUi.t("buoni", "vouchers")
                + (extraCent > 0 ? " + " + SaldoBuoni.euro(extraCent / 100.0) : "");
        TextView sub = BuoniUi.testo(this, det, 13, BuoniUi.TESTO_2, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, BuoniUi.dp(this, 4), 0, 0);
        box.addView(sub);
        if (SaldoBuoni.giaImportata(this, id)) {
            TextView gia = BuoniUi.testo(this, BuoniUi.t("Questa spesa è già stata importata.", "This expense was already imported."),
                    13, 0xFFFF9F0A, false);
            gia.setGravity(Gravity.CENTER);
            gia.setPadding(0, BuoniUi.dp(this, 6), 0, 0);
            box.addView(gia);
        }

        // ---- nome e importo
        box.addView(BuoniUi.etichetta(this, BuoniUi.t("Nome", "Name")));
        String nome = nota == null || nota.trim().isEmpty() ? BuoniUi.t("Spesa alimentare", "Groceries") : nota.trim();
        etNome = BuoniUi.campo(this, nome, false);
        box.addView(etNome, new LinearLayout.LayoutParams(-1, -2));
        box.addView(BuoniUi.etichetta(this, BuoniUi.t("Importo (€)", "Amount (€)")));
        etImporto = BuoniUi.campo(this, BuoniUi.due(totale), true);
        box.addView(etImporto, new LinearLayout.LayoutParams(-1, -2));
        if (meta) {
            // i buoni non si dividono: l'altra persona ridà la sua metà in soldi
            TextView avviso = BuoniUi.testo(this, BuoniUi.t(
                    "Spesa divisa a metà: i buoni restano tutti tuoi. Quando l'altra persona ti ridà la sua parte, tocca la spesa "
                            + "tra le approvate e scegli \"Ripagata\": metà importo torna sul saldo.",
                    "Expense split in half: all vouchers stay yours. When the other person pays you back, tap the expense among the "
                            + "approved ones and choose \"Paid back\": half the amount goes back to your balance."), 13, BuoniUi.TESTO_2, false);
            avviso.setPadding(BuoniUi.dp(this, 4), BuoniUi.dp(this, 8), 0, 0);
            box.addView(avviso);
        }

        // ---- pagata con
        box.addView(BuoniUi.etichetta(this, BuoniUi.t("Pagata con", "Paid with")));
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(BuoniUi.sfondo(this, BuoniUi.RIEMPIMENTO, 10));
        int q = BuoniUi.dp(this, 2);
        seg.setPadding(q, q, q, q);
        segBuoni = segmento(BuoniUi.t("Buoni pasto", "Meal vouchers"));
        segSaldo = segmento(BuoniUi.t("Saldo", "Balance"));
        segBuoni.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                conBuoni = true;
                aggiorna();
            }
        });
        segSaldo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                conBuoni = false;
                aggiorna();
            }
        });
        seg.addView(segBuoni, new LinearLayout.LayoutParams(0, -2, 1f));
        seg.addView(segSaldo, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(seg);

        // ---- buoni usati
        boxBuoni = new LinearLayout(this);
        boxBuoni.setOrientation(LinearLayout.VERTICAL);
        boxBuoni.addView(BuoniUi.etichetta(this, BuoniUi.t("Buoni usati", "Vouchers used")));
        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        riga.setGravity(Gravity.CENTER_VERTICAL);
        etBuoni = BuoniUi.campo(this, SaldoBuoni.numeroTesto(buoni), true);
        etBuoni.setGravity(Gravity.CENTER);
        TextView meno = BuoniUi.tondo(this, "−"), piu = BuoniUi.tondo(this, "+");
        meno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double x = BuoniUi.numero(etBuoni);
                etBuoni.setText(SaldoBuoni.numeroTesto(Math.max(0, (x == null ? 0 : x) - 1)));
            }
        });
        piu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double x = BuoniUi.numero(etBuoni);
                etBuoni.setText(SaldoBuoni.numeroTesto((x == null ? 0 : x) + 1));
            }
        });
        int s = BuoniUi.dp(this, 44);
        riga.addView(meno, new LinearLayout.LayoutParams(s, s));
        LinearLayout.LayoutParams lq = new LinearLayout.LayoutParams(0, -2, 1f);
        lq.leftMargin = BuoniUi.dp(this, 10);
        lq.rightMargin = BuoniUi.dp(this, 10);
        riga.addView(etBuoni, lq);
        riga.addView(piu, new LinearLayout.LayoutParams(s, s));
        boxBuoni.addView(riga);
        tvBuoni = BuoniUi.testo(this, "", 13, BuoniUi.TESTO_2, false);
        tvBuoni.setPadding(BuoniUi.dp(this, 4), BuoniUi.dp(this, 8), 0, 0);
        boxBuoni.addView(tvBuoni);
        box.addView(boxBuoni);
        tvSaldo = BuoniUi.testo(this, "", 13, BuoniUi.TESTO_2, false);
        tvSaldo.setPadding(BuoniUi.dp(this, 4), BuoniUi.dp(this, 8), 0, 0);
        box.addView(tvSaldo);
        tvDettaglio = tvSaldo;

        TextWatcher w = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                aggiorna();
            }
        };
        etImporto.addTextChangedListener(w);
        etBuoni.addTextChangedListener(w);

        // ---- aggiungi / annulla
        TextView aggiungi = BuoniUi.testo(this, BuoniUi.t("Aggiungi a BuyForTime", "Add to BuyForTime"), 17, Color.WHITE, true);
        aggiungi.setGravity(Gravity.CENTER);
        aggiungi.setBackground(BuoniUi.sfondo(this, BuoniUi.ACCENTO, 14));
        aggiungi.setPadding(0, BuoniUi.dp(this, 13), 0, BuoniUi.dp(this, 13));
        aggiungi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                salva(id, data);
            }
        });
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(-1, -2);
        la.topMargin = BuoniUi.dp(this, 20);
        box.addView(aggiungi, la);
        TextView annulla = BuoniUi.testo(this, BuoniUi.t("Annulla", "Cancel"), 17, BuoniUi.ACCENTO, false);
        annulla.setGravity(Gravity.CENTER);
        annulla.setPadding(0, BuoniUi.dp(this, 12), 0, BuoniUi.dp(this, 6));
        annulla.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        box.addView(annulla);
        aggiorna();
    }

    private TextView segmento(String t) {
        TextView v = BuoniUi.testo(this, t, 14, BuoniUi.TESTO, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, BuoniUi.dp(this, 7), 0, BuoniUi.dp(this, 7));
        return v;
    }

    /** Parte pagata in contanti/carta: importo meno il valore dei buoni usati. */
    private double contanti() {
        Double imp = BuoniUi.numero(etImporto), b = BuoniUi.numero(etBuoni);
        double i = imp == null ? 0 : imp;
        if (!conBuoni) return i;
        return Math.max(0, Math.round((i - (b == null ? 0 : b) * valoreBuono) * 100.0) / 100.0);
    }

    private void aggiorna() {
        segBuoni.setBackground(conBuoni ? BuoniUi.sfondo(this, 0xFF636366, 8) : null);
        segSaldo.setBackground(!conBuoni ? BuoniUi.sfondo(this, 0xFF636366, 8) : null);
        segBuoni.setTextColor(conBuoni ? BuoniUi.TESTO : BuoniUi.TESTO_2);
        segSaldo.setTextColor(!conBuoni ? BuoniUi.TESTO : BuoniUi.TESTO_2);
        boxBuoni.setVisibility(conBuoni ? View.VISIBLE : View.GONE);
        if (conBuoni) {
            Double b = BuoniUi.numero(etBuoni);
            double usati = b == null ? 0 : b;
            String disp = SaldoBuoni.attivo(this)
                    ? BuoniUi.t("Buoni disponibili: ", "Vouchers available: ") + SaldoBuoni.numeroTesto(SaldoBuoni.numero(this))
                    + " → " + SaldoBuoni.numeroTesto(SaldoBuoni.numero(this) - usati)
                    : BuoniUi.t("Imposta i buoni pasto nella Home di BuyForTime per vedere quanti te ne restano.",
                    "Set up your meal vouchers on the BuyForTime home to see how many are left.");
            tvBuoni.setText(disp);
            double c = contanti();
            tvDettaglio.setText(c > 0
                    ? BuoniUi.t("Dal saldo scala solo la parte in contanti/carta: ", "Only the cash/card part is taken from your balance: ")
                    + SaldoBuoni.euro(c)
                    : BuoniUi.t("Il saldo monetario non cambia.", "Your money balance does not change."));
        } else {
            tvDettaglio.setText(BuoniUi.t("L'importo scala dal saldo monetario.", "The amount is taken from your money balance."));
        }
    }

    private void salva(String id, long data) {
        Double imp = BuoniUi.numero(etImporto);
        if (imp == null || imp <= 0) {
            Toast.makeText(this, BuoniUi.t("Inserisci un importo valido.", "Enter a valid amount."), Toast.LENGTH_SHORT).show();
            return;
        }
        String nome = String.valueOf(etNome.getText()).trim();
        if (nome.isEmpty()) nome = BuoniUi.t("Spesa alimentare", "Groceries");
        SpeseManager m = new SpeseManager(this);
        // la data della spesa e' quella dell'app Buoni Pasto: gia' fatta, quindi approvata subito
        if (!m.addSpesa(nome, imp.floatValue(), data, true, "alimentari", false)) {
            Toast.makeText(this, BuoniUi.t("Configura prima lo stipendio in BuyForTime.", "Set up your salary in BuyForTime first."),
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<Spesa> spese = m.getSpese();
        if (spese.isEmpty()) return;
        String nuova = spese.get(0).getId();
        Double b = BuoniUi.numero(etBuoni);
        double usati = conBuoni && b != null ? Math.max(0, b) : 0;
        m.impostaPagamento(nuova, conBuoni ? "buoni" : null, usati, (float) contanti(), id);
        if (conBuoni) SaldoBuoni.usa(this, usati);
        m.approvaSpesa(nuova);
        SaldoBuoni.segnaImportata(this, id);
        Toast.makeText(this, BuoniUi.t("Spesa aggiunta a BuyForTime", "Expense added to BuyForTime"), Toast.LENGTH_SHORT).show();
        finish();
    }
}
