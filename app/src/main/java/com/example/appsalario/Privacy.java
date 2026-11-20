package com.example.appsalario;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.TextView;

/** Blocco biometrico e importi sfocati. */
public final class Privacy {
    private static final String PREFS = "SalarioPrefs";
    private static final String KEY_NASCOSTO = "importi_nascosti";
    private static final String KEY_BLOCCO = "blocco_biometrico";

    public interface Esito {
        void riuscito();

        void fallito();
    }

    private Privacy() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    public static boolean nascosto(Context c) {
        return prefs(c).getBoolean(KEY_NASCOSTO, false);
    }

    public static void setNascosto(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_NASCOSTO, v).apply();
    }

    public static boolean blocco(Context c) {
        return prefs(c).getBoolean(KEY_BLOCCO, false);
    }

    public static void setBlocco(Context c, boolean v) {
        prefs(c).edit().putBoolean(KEY_BLOCCO, v).apply();
    }

    // ------------------------------------------------------------------ sfocatura

    /**
     * Velatura morbida degli importi.
     *
     * TextView ritaglia il disegno al rettangolo del testo: una sfocatura applicata alle
     * lettere viene tagliata e lascia un bordo duro. Qui invece lo span si riserva un
     * margine ai lati e usa un raggio che sta dentro l'interlinea, cosi' l'alone sfuma a
     * zero prima del bordo. Al posto delle cifre vere disegna cifre fittizie: anche con
     * una sfocatura leggera non si legge nulla.
     */
    static final class Velatura extends android.text.style.ReplacementSpan {
        private static final float SCALA = 0.70f;        // segnaposto ridotto: piu' spazio per sfumare
        private static final float RAGGIO_EM = 0.30f;    // raggio della sfocatura, in em del testo
        private static final float MARGINE_EM = 0.55f;   // spazio laterale
        private static final float SFUMA_X_EM = 0.45f;   // dissolvenza ai lati
        private static final float SFUMA_Y = 0.30f;      // dissolvenza sopra/sotto (frazione d'altezza)

        private static final android.util.LruCache<String, android.graphics.Bitmap> CACHE =
                new android.util.LruCache<String, android.graphics.Bitmap>(64);

        private static String fittizio(CharSequence t, int start, int end) {
            StringBuilder b = new StringBuilder(end - start);
            for (int i = start; i < end; i++) {
                char c = t.charAt(i);
                b.append(Character.isDigit(c) ? '8' : c);
            }
            return b.toString();
        }

        private static android.graphics.Paint pennello(android.graphics.Paint base) {
            android.graphics.Paint p = new android.graphics.Paint(base);
            p.setTextSize(base.getTextSize() * SCALA);
            p.setMaskFilter(null);
            p.setShader(null);
            return p;
        }

        @Override
        public int getSize(android.graphics.Paint paint, CharSequence text, int start, int end,
                           android.graphics.Paint.FontMetricsInt fm) {
            if (fm != null) paint.getFontMetricsInt(fm);
            return Math.round(pennello(paint).measureText(fittizio(text, start, end))
                    + 2 * paint.getTextSize() * MARGINE_EM);
        }

        @Override
        public void draw(android.graphics.Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, android.graphics.Paint paint) {
            String t = fittizio(text, start, end);
            float size = paint.getTextSize();
            int w = getSize(paint, text, start, end, null);
            int h = Math.max(1, bottom - top);
            String chiave = t + "|" + size + "|" + paint.getColor() + "|" + w + "x" + h + "|" + (y - top);
            android.graphics.Bitmap bmp = CACHE.get(chiave);
            if (bmp == null) {
                bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                // 1. testo fittizio sfocato (canvas software: il BlurMaskFilter funziona sempre)
                android.graphics.Paint p = pennello(paint);
                p.setMaskFilter(new BlurMaskFilter(Math.max(2f, size * RAGGIO_EM), BlurMaskFilter.Blur.NORMAL));
                p.setAlpha(Math.round(paint.getAlpha() * 0.94f));
                float centro = h / 2f;                                   // centro della riga
                float base = centro + 0.36f * size * SCALA;              // cifre centrate sulla riga
                c.drawText(t, size * MARGINE_EM, base, p);
                // 2. dissolvenza a zero su tutti i bordi: niente puo' essere tagliato di netto
                android.graphics.Paint m = new android.graphics.Paint();
                m.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
                float fx = Math.min(w / 2f, size * SFUMA_X_EM) / w;
                m.setShader(new android.graphics.LinearGradient(0, 0, w, 0,
                        new int[]{0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                        new float[]{0f, fx, 1f - fx, 1f}, android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, m);
                m.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                        new int[]{0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                        new float[]{0f, SFUMA_Y, 1f - SFUMA_Y, 1f}, android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, m);
                CACHE.put(chiave, bmp);
            }
            canvas.drawBitmap(bmp, x, top, null);
        }
    }

    static final class Velo implements android.text.method.TransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            if (source == null || source.length() == 0) return source;
            android.text.SpannableString s = new android.text.SpannableString(source);
            s.setSpan(new Velatura(), 0, s.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return s;
        }

        @Override
        public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
                                   android.graphics.Rect previouslyFocusedRect) {
        }
    }

    private static final java.util.WeakHashMap<TextView, android.text.method.TransformationMethod> ORIGINALI =
            new java.util.WeakHashMap<TextView, android.text.method.TransformationMethod>();

    /** Vela (o ripristina) gli importi di una TextView/EditText. */
    public static void sfoca(TextView tv, boolean on) {
        if (tv == null) return;
        boolean velata = tv.getTransformationMethod() instanceof Velo;
        if (on && !velata) {
            ORIGINALI.put(tv, tv.getTransformationMethod());
            tv.setTransformationMethod(new Velo());
            if (tv instanceof android.widget.EditText) {
                // velato = sola lettura: per modificare si mostrano gli importi
                tv.setFocusable(false);
                tv.setFocusableInTouchMode(false);
            }
        } else if (!on && velata) {
            tv.setTransformationMethod(ORIGINALI.remove(tv));
            if (tv instanceof android.widget.EditText) {
                tv.setFocusable(true);
                tv.setFocusableInTouchMode(true);
            }
        }
    }

    public static void sfoca(Context c, TextView... viste) {
        boolean on = nascosto(c);
        for (TextView tv : viste) sfoca(tv, on);
    }

    // ------------------------------------------------------------------ biometria

    private static final int AUTENTICATORI = 0x000000FF /* BIOMETRIC_WEAK */ | 0x00008000 /* DEVICE_CREDENTIAL */;

    /** C'e' almeno un'impronta/volto o un PIN/sequenza impostati sul telefono. */
    public static boolean disponibile(Context c) {
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager bm = c.getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate(AUTENTICATORI) == BiometricManager.BIOMETRIC_SUCCESS;
        }
        KeyguardManager km = c.getSystemService(KeyguardManager.class);
        return km != null && km.isDeviceSecure();
    }

    @SuppressWarnings("deprecation")
    public static void autentica(Activity a, String titolo, String sottotitolo, final Esito esito) {
        BiometricPrompt.Builder b = new BiometricPrompt.Builder(a).setTitle(titolo);
        if (sottotitolo != null) b.setSubtitle(sottotitolo);
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(AUTENTICATORI);
        else b.setDeviceCredentialAllowed(true);
        try {
            b.build().authenticate(new CancellationSignal(), a.getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                            esito.riuscito();
                        }

                        @Override
                        public void onAuthenticationError(int codice, CharSequence msg) {
                            esito.fallito();
                        }
                    });
        } catch (RuntimeException e) {
            esito.fallito();
        }
    }

    /** Niente anteprima del contenuto nelle app recenti quando il blocco e' attivo. */
    public static void proteggiRecenti(Activity a, boolean on) {
        if (Build.VERSION.SDK_INT >= 33) {
            a.setRecentsScreenshotEnabled(!on);
        } else if (on) {
            a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }
}
