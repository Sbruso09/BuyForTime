package com.example.appsalario;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/** Sheet di scelta categoria (griglia di icone in stile iOS) e creazione di categorie personalizzate. */
public final class CategoriaPicker {

    public interface OnScelta {
        void scelta(Categorie.Categoria categoria);
    }

    private static final int COLONNE = 4;

    private CategoriaPicker() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Colora una tile (sfondo = colore categoria, glifo bianco). */
    public static void applica(View tile, ImageView icona, Categorie.Categoria c) {
        tile.setBackgroundTintList(ColorStateList.valueOf(c.colore));
        icona.setImageResource(c.iconaRes());
    }

    public static void mostra(final Context ctx, String selezionata, final OnScelta onScelta) {
        final BottomSheetDialog dialog = new BottomSheetDialog(ctx, R.style.ThemeOverlay_App_BottomSheet);
        View view = LayoutInflater.from(ctx).inflate(R.layout.sheet_categorie, null);
        dialog.setContentView(view);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        view.findViewById(R.id.btnAnnullaCategorie).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        LinearLayout griglia = view.findViewById(R.id.grigliaCategorie);
        List<View> celle = new ArrayList<View>();
        for (final Categorie.Categoria c : Categorie.tutte(ctx)) {
            boolean sel = c.key.equals(selezionata) || (selezionata == null && Categorie.ALTRO.equals(c.key));
            View cella = cella(ctx, c.colore, c.iconaRes(), c.nome, sel);
            cella.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    onScelta.scelta(c);
                }
            });
            if (c.personalizzata()) {
                cella.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        dialog.dismiss();
                        chiediEliminazione(ctx, c);
                        return true;
                    }
                });
            }
            celle.add(cella);
        }
        View nuova = cella(ctx, ContextCompat.getColor(ctx, R.color.surface_input), R.drawable.cat_add,
                ctx.getString(R.string.nuova), false);
        ((ImageView) nuova.findViewWithTag("icona")).setImageTintList(
                ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.accent)));
        nuova.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                mostraNuova(ctx, onScelta);
            }
        });
        celle.add(nuova);
        riempiGriglia(ctx, griglia, celle, COLONNE);
        dialog.show();
    }

    private static void chiediEliminazione(final Context ctx, final Categorie.Categoria c) {
        List<ActionSheet.Azione> azioni = new ArrayList<ActionSheet.Azione>();
        azioni.add(new ActionSheet.Azione(ctx.getString(R.string.elimina_categoria), true, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                Categorie.elimina(ctx, c.key);
                return Unit.INSTANCE;
            }
        }));
        ActionSheet.INSTANCE.mostra(ctx, c.nome, ctx.getString(R.string.elimina_categoria_testo), azioni);
    }

    /** Cella della griglia: cerchio colorato con glifo + etichetta sotto, anello se selezionata. */
    private static View cella(Context ctx, int colore, int icona, String nome, boolean selezionata) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(0, dp(ctx, 6), 0, dp(ctx, 8));
        box.setBackgroundResource(resId(ctx, android.R.attr.selectableItemBackgroundBorderless));

        FrameLayout anello = new FrameLayout(ctx);
        int lato = dp(ctx, 58);
        anello.setLayoutParams(new LinearLayout.LayoutParams(lato, lato));
        if (selezionata) {
            anello.setBackgroundResource(R.drawable.anello_selezione);
            anello.setBackgroundTintList(ColorStateList.valueOf(colore));
        }
        FrameLayout tile = new FrameLayout(ctx);
        int t = dp(ctx, 48);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(t, t, Gravity.CENTER);
        tile.setLayoutParams(lp);
        tile.setBackgroundResource(R.drawable.cerchio);
        tile.setBackgroundTintList(ColorStateList.valueOf(colore));
        ImageView iv = new ImageView(ctx);
        iv.setTag("icona");
        int i = dp(ctx, 26);
        iv.setLayoutParams(new FrameLayout.LayoutParams(i, i, Gravity.CENTER));
        iv.setImageResource(icona);
        iv.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        tile.addView(iv);
        anello.addView(tile);
        box.addView(anello);

        TextView tv = new TextView(ctx);
        tv.setTextAppearance(R.style.TextAppearance_App_Caption);
        tv.setTextColor(ContextCompat.getColor(ctx, selezionata ? R.color.text_primary : R.color.text_secondary));
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setText(nome);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(ctx, 4);
        tv.setLayoutParams(tlp);
        tv.setPadding(dp(ctx, 2), 0, dp(ctx, 2), 0);
        box.addView(tv);
        return box;
    }

    static void riempiGriglia(Context ctx, LinearLayout griglia, List<View> celle, int colonne) {
        griglia.removeAllViews();
        LinearLayout riga = null;
        for (int i = 0; i < celle.size(); i++) {
            if (i % colonne == 0) {
                riga = new LinearLayout(ctx);
                riga.setOrientation(LinearLayout.HORIZONTAL);
                griglia.addView(riga, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            riga.addView(celle.get(i), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        int resto = celle.size() % colonne;
        if (resto != 0 && riga != null) {
            for (int i = resto; i < colonne; i++) riga.addView(new View(ctx), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private static int resId(Context ctx, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        ctx.getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId;
    }

    // ------------------------------------------------------------ nuova categoria

    public static void mostraNuova(final Context ctx, final OnScelta onScelta) {
        final BottomSheetDialog dialog = new BottomSheetDialog(ctx, R.style.ThemeOverlay_App_BottomSheet);
        View view = LayoutInflater.from(ctx).inflate(R.layout.sheet_nuova_categoria, null);
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(ctx);
        scroll.addView(view);
        dialog.setContentView(scroll);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        final View anteprima = view.findViewById(R.id.tileAnteprima);
        final ImageView ivAnteprima = view.findViewById(R.id.ivAnteprima);
        final EditText etNome = view.findViewById(R.id.etNomeCategoria);
        final LinearLayout grigliaColori = view.findViewById(R.id.grigliaColori);
        final LinearLayout grigliaIcone = view.findViewById(R.id.grigliaIcone);
        final String[] icone = Categorie.nomiIcone();
        final int[] scelta = {Categorie.COLORI[7], 0}; // colore, indice icona

        final Runnable[] aggiorna = new Runnable[1];
        aggiorna[0] = new Runnable() {
            @Override
            public void run() {
                anteprima.setBackgroundTintList(ColorStateList.valueOf(scelta[0]));
                ivAnteprima.setImageResource(Categorie.iconaRes(icone[scelta[1]]));
                // colori: pallini con anello sul selezionato
                List<View> pallini = new ArrayList<View>();
                for (final int col : Categorie.COLORI) {
                    FrameLayout f = new FrameLayout(ctx);
                    int lato = dp(ctx, 40);
                    f.setLayoutParams(new LinearLayout.LayoutParams(lato, lato));
                    if (col == scelta[0]) {
                        f.setBackgroundResource(R.drawable.anello_selezione);
                        f.setBackgroundTintList(ColorStateList.valueOf(col));
                    }
                    View p = new View(ctx);
                    int d = dp(ctx, 30);
                    p.setLayoutParams(new FrameLayout.LayoutParams(d, d, Gravity.CENTER));
                    p.setBackgroundResource(R.drawable.cerchio);
                    p.setBackgroundTintList(ColorStateList.valueOf(col));
                    f.addView(p);
                    FrameLayout wrap = new FrameLayout(ctx);
                    wrap.setPadding(0, dp(ctx, 3), 0, dp(ctx, 3));
                    wrap.addView(f, new FrameLayout.LayoutParams(lato, lato, Gravity.CENTER));
                    wrap.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            scelta[0] = col;
                            aggiorna[0].run();
                        }
                    });
                    pallini.add(wrap);
                }
                riempiGriglia(ctx, grigliaColori, pallini, 7);
                // icone: glifi su fondo grigio, selezionata col colore scelto
                List<View> celle = new ArrayList<View>();
                for (int i = 0; i < icone.length; i++) {
                    final int idx = i;
                    boolean sel = i == scelta[1];
                    FrameLayout wrap = new FrameLayout(ctx);
                    wrap.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));
                    FrameLayout c = new FrameLayout(ctx);
                    int lato = dp(ctx, 42);
                    c.setBackgroundResource(R.drawable.cerchio);
                    c.setBackgroundTintList(ColorStateList.valueOf(sel ? scelta[0]
                            : ContextCompat.getColor(ctx, R.color.surface_input)));
                    ImageView iv = new ImageView(ctx);
                    int s = dp(ctx, 22);
                    iv.setImageResource(Categorie.iconaRes(icone[i]));
                    iv.setImageTintList(ColorStateList.valueOf(sel ? 0xFFFFFFFF
                            : ContextCompat.getColor(ctx, R.color.text_secondary)));
                    c.addView(iv, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
                    wrap.addView(c, new FrameLayout.LayoutParams(lato, lato, Gravity.CENTER));
                    wrap.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            scelta[1] = idx;
                            aggiorna[0].run();
                        }
                    });
                    celle.add(wrap);
                }
                riempiGriglia(ctx, grigliaIcone, celle, 6);
            }
        };
        aggiorna[0].run();

        view.findViewById(R.id.btnAnnullaNuova).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        view.findViewById(R.id.btnFattoNuova).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nome = etNome.getText().toString().trim();
                if (nome.isEmpty()) {
                    Toast.makeText(ctx, R.string.errore_nome_categoria, Toast.LENGTH_SHORT).show();
                    return;
                }
                Categorie.Categoria c = Categorie.aggiungi(ctx, nome, icone[scelta[1]], scelta[0]);
                dialog.dismiss();
                onScelta.scelta(c);
            }
        });
        dialog.show();
    }
}
