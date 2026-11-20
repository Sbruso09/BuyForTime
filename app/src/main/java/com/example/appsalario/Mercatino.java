package com.example.appsalario;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import java.net.URLEncoder;

/**
 * Ricerca dell'usato su Subito, Vinted ed eBay dentro l'app: una pagina web a schermo intero
 * con i risultati del sito scelto (si cambia sito con i tre pulsanti in alto, la ricerca resta).
 */
public final class Mercatino {
    private static final String[] NOMI = {"Subito", "Vinted", "eBay"};

    private Mercatino() {}

    static String url(int sito, String cosa) {
        String q;
        try {
            q = URLEncoder.encode(cosa.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            q = cosa.trim();
        }
        switch (sito) {
            case 1: return "https://www.vinted.it/catalog?search_text=" + q;
            case 2: return "https://www.ebay.it/sch/i.html?_nkw=" + q;
            default: return "https://www.subito.it/annunci-italia/vendita/usato/?q=" + q;
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Chiede cosa cercare, poi apre i risultati. */
    public static void chiedi(final Activity a) {
        final Dialog d = new Dialog(a, R.style.Theme_App_Dialog);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(a, 20);
        box.setPadding(p, p, p, dp(a, 14));
        GradientDrawable sf = new GradientDrawable();
        sf.setCornerRadius(dp(a, 18));
        sf.setColor(ContextCompat.getColor(a, R.color.surface_elevated));
        box.setBackground(sf);

        TextView titolo = new TextView(a);
        titolo.setTextAppearance(R.style.TextAppearance_App_Headline);
        titolo.setText(R.string.mercatino_titolo);
        titolo.setGravity(Gravity.CENTER);
        box.addView(titolo);

        TextView sotto = new TextView(a);
        sotto.setTextAppearance(R.style.TextAppearance_App_Footnote);
        sotto.setTextColor(ContextCompat.getColor(a, R.color.text_secondary));
        sotto.setText(R.string.mercatino_sottotitolo);
        sotto.setGravity(Gravity.CENTER);
        sotto.setPadding(0, dp(a, 4), 0, dp(a, 14));
        box.addView(sotto);

        final EditText et = new EditText(a);
        et.setTextAppearance(R.style.TextAppearance_App_Body);
        et.setTextColor(ContextCompat.getColor(a, R.color.text_primary));
        et.setHintTextColor(ContextCompat.getColor(a, R.color.text_tertiary));
        et.setHint(R.string.mercatino_hint);
        et.setBackgroundResource(R.drawable.bolla_input);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        et.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10));
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout siti = new LinearLayout(a);
        siti.setOrientation(LinearLayout.HORIZONTAL);
        siti.setPadding(0, dp(a, 14), 0, 0);
        for (int i = 0; i < NOMI.length; i++) {
            final int sito = i;
            TextView b = new TextView(a);
            b.setTextAppearance(R.style.TextAppearance_App_Subhead);
            b.setTextColor(ContextCompat.getColor(a, R.color.accent));
            b.setBackgroundResource(R.drawable.pill_background);
            b.setGravity(Gravity.CENTER);
            b.setText(NOMI[i]);
            b.setPadding(0, dp(a, 10), 0, dp(a, 10));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String cosa = String.valueOf(et.getText()).trim();
                    if (cosa.isEmpty()) {
                        et.requestFocus();
                        return;
                    }
                    nascondiTastiera(a, et);
                    d.dismiss();
                    apri(a, cosa, sito);
                }
            });
            Premibile.INSTANCE.applicaA(b);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.setMarginStart(dp(a, 8));
            siti.addView(b, lp);
        }
        box.addView(siti);
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int azione, KeyEvent e) {
                String cosa = String.valueOf(et.getText()).trim();
                if (cosa.isEmpty()) return true;
                nascondiTastiera(a, et);
                d.dismiss();
                apri(a, cosa, 0);
                return true;
            }
        });

        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.88), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        et.requestFocus();
    }

    private static void nascondiTastiera(Context c, View v) {
        ((InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    /** Pagina dei risultati a schermo intero. */
    public static void apri(final Activity a, final String cosa, int sitoIniziale) {
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int sfondo = ContextCompat.getColor(a, R.color.background);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(sfondo);

        // barra: Fine · titolo · browser
        FrameLayout barra = new FrameLayout(a);
        barra.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        TextView fine = new TextView(a);
        fine.setTextAppearance(R.style.TextAppearance_App_Body);
        fine.setTextColor(ContextCompat.getColor(a, R.color.accent));
        fine.setText(R.string.mercatino_fine);
        fine.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        barra.addView(fine, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));
        TextView titolo = new TextView(a);
        titolo.setTextAppearance(R.style.TextAppearance_App_Headline);
        titolo.setText(cosa);
        titolo.setSingleLine(true);
        titolo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titolo.setMaxWidth((int) (a.getResources().getDisplayMetrics().widthPixels * 0.5f));
        barra.addView(titolo, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        final TextView browser = new TextView(a);
        browser.setTextAppearance(R.style.TextAppearance_App_Body);
        browser.setTextColor(ContextCompat.getColor(a, R.color.accent));
        browser.setText(R.string.mercatino_browser);
        browser.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        barra.addView(browser, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        root.addView(barra, new LinearLayout.LayoutParams(-1, -2));

        // selettore sito (stile segmented control)
        LinearLayout seg = new LinearLayout(a);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable sfSeg = new GradientDrawable();
        sfSeg.setCornerRadius(dp(a, 9));
        sfSeg.setColor(ContextCompat.getColor(a, R.color.fill_tertiary));
        seg.setBackground(sfSeg);
        seg.setPadding(dp(a, 2), dp(a, 2), dp(a, 2), dp(a, 2));
        LinearLayout.LayoutParams lpSeg = new LinearLayout.LayoutParams(-1, -2);
        lpSeg.setMargins(dp(a, 16), 0, dp(a, 16), dp(a, 8));
        root.addView(seg, lpSeg);

        final ProgressBar caricamento = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        caricamento.setMax(100);
        caricamento.setProgressTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(a, R.color.accent)));
        root.addView(caricamento, new LinearLayout.LayoutParams(-1, dp(a, 3)));

        final WebView web = new WebView(a);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String s = u.getScheme();
                if ("http".equals(s) || "https".equals(s)) return false; // resta nella pagina
                // link "apri nell'app" (intent://, market://...): li apro fuori solo se c'e' chi li gestisce
                try {
                    Intent i = "intent".equals(s) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                            : new Intent(Intent.ACTION_VIEW, u);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    i.setComponent(null);
                    a.startActivity(i);
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                caricamento.setProgress(p);
                caricamento.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));

        final TextView[] pulsanti = new TextView[NOMI.length];
        final int[] attivo = {sitoIniziale};
        final Runnable evidenzia = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < pulsanti.length; i++) {
                    boolean on = i == attivo[0];
                    GradientDrawable g = new GradientDrawable();
                    g.setCornerRadius(dp(a, 7));
                    g.setColor(on ? ContextCompat.getColor(a, R.color.surface_elevated) : 0);
                    pulsanti[i].setBackground(g);
                    pulsanti[i].setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
            }
        };
        for (int i = 0; i < NOMI.length; i++) {
            final int sito = i;
            TextView b = new TextView(a);
            b.setTextAppearance(R.style.TextAppearance_App_Subhead);
            b.setTextColor(ContextCompat.getColor(a, R.color.text_primary));
            b.setGravity(Gravity.CENTER);
            b.setText(NOMI[i]);
            b.setPadding(0, dp(a, 6), 0, dp(a, 6));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (attivo[0] == sito) return;
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    attivo[0] = sito;
                    evidenzia.run();
                    web.loadUrl(url(sito, cosa));
                }
            });
            pulsanti[i] = b;
            seg.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        evidenzia.run();

        fine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        browser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String u = web.getUrl() != null ? web.getUrl() : url(attivo[0], cosa);
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (RuntimeException ignored) {
                }
            }
        });
        d.setOnKeyListener(new android.content.DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(android.content.DialogInterface di, int codice, KeyEvent e) {
                if (codice == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && web.canGoBack()) {
                    web.goBack();
                    return true;
                }
                return false;
            }
        });
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                web.stopLoading();
                web.destroy();
            }
        });

        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(sfondo));
            w.setStatusBarColor(sfondo);
            w.setNavigationBarColor(sfondo);
        }
        d.show();
        web.loadUrl(url(sitoIniziale, cosa));
    }
}
