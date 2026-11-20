package com.example.appsalario;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Chiave Gemini dell'utente, cifrata con una chiave AES che vive nel portachiavi del telefono (Android Keystore). */
public final class ChiaveAi {
    private static final String ALIAS = "buyfortime_ai";
    private static final String K_CHIAVE = "ai_chiave_cifrata";
    private static final String K_IV = "ai_chiave_iv";

    public static final String GEMINI = "", GROQ = "_groq";

    private ChiaveAi() {}

    public static boolean salva(Context c, String chiave, String servizio) {
        return salva(c, chiave, K_CHIAVE + servizio, K_IV + servizio);
    }

    public static String leggi(Context c, String servizio) {
        return leggi(c, K_CHIAVE + servizio, K_IV + servizio);
    }

    public static boolean presente(Context c, String servizio) {
        return prefs(c).contains(K_CHIAVE + servizio);
    }

    public static void rimuovi(Context c, String servizio) {
        prefs(c).edit().remove(K_CHIAVE + servizio).remove(K_IV + servizio).apply();
    }

    /** L'assistente parte solo con entrambe le chiavi (Gemini principale, Groq riserva). */
    public static boolean complete(Context c) {
        return presente(c, GEMINI) && presente(c, GROQ);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("SalarioPrefs", 0);
    }

    private static SecretKey chiaveAes() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    public static boolean salva(Context c, String chiave) {
        return salva(c, chiave, GEMINI);
    }

    private static boolean salva(Context c, String chiave, String kDati, String kIv) {
        try {
            Cipher cf = Cipher.getInstance("AES/GCM/NoPadding");
            cf.init(Cipher.ENCRYPT_MODE, chiaveAes());
            byte[] dati = cf.doFinal(chiave.trim().getBytes(StandardCharsets.UTF_8));
            prefs(c).edit()
                    .putString(kDati, Base64.encodeToString(dati, Base64.NO_WRAP))
                    .putString(kIv, Base64.encodeToString(cf.getIV(), Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** La chiave in chiaro, o null se non impostata/illeggibile. */
    public static String leggi(Context c) {
        return leggi(c, GEMINI);
    }

    private static String leggi(Context c, String kDati, String kIv) {
        String dati = prefs(c).getString(kDati, null), iv = prefs(c).getString(kIv, null);
        if (dati == null || iv == null) return null;
        try {
            Cipher cf = Cipher.getInstance("AES/GCM/NoPadding");
            cf.init(Cipher.DECRYPT_MODE, chiaveAes(), new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cf.doFinal(Base64.decode(dati, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean presente(Context c) {
        return prefs(c).contains(K_CHIAVE);
    }

    public static void rimuovi(Context c) {
        prefs(c).edit().remove(K_CHIAVE).remove(K_IV).apply();
    }
}
