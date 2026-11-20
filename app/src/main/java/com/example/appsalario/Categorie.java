package com.example.appsalario;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Categorie: predefinite + create dall'utente (salvate nelle preferenze). */
public final class Categorie {

    public static final class Categoria {
        public String key;
        public String nome;
        public String icona;   // nome del drawable (cat_*)
        public int colore;     // ARGB

        public Categoria(String key, String nome, String icona, int colore) {
            this.key = key;
            this.nome = nome;
            this.icona = icona;
            this.colore = colore;
        }

        public int iconaRes() {
            return Categorie.iconaRes(icona);
        }

        public boolean personalizzata() {
            return key != null && key.startsWith("u_");
        }
    }

    public static final String ALTRO = "altro";

    /** Colori di sistema iOS (tema scuro). */
    public static final int[] COLORI = {
            0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF66D4CF, 0xFF40C8E0, 0xFF64D2FF,
            0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFAC8E68, 0xFF8E8E93};

    private static final Object[][] ICONE = {
            {"cat_autorenew", R.drawable.cat_autorenew},
            {"cat_cake", R.drawable.cat_cake},
            {"cat_category", R.drawable.cat_category},
            {"cat_celebration", R.drawable.cat_celebration},
            {"cat_checkroom", R.drawable.cat_checkroom},
            {"cat_child_care", R.drawable.cat_child_care},
            {"cat_construction", R.drawable.cat_construction},
            {"cat_directions_car", R.drawable.cat_directions_car},
            {"cat_favorite", R.drawable.cat_favorite},
            {"cat_fitness_center", R.drawable.cat_fitness_center},
            {"cat_flight", R.drawable.cat_flight},
            {"cat_headphones", R.drawable.cat_headphones},
            {"cat_home", R.drawable.cat_home},
            {"cat_local_bar", R.drawable.cat_local_bar},
            {"cat_local_cafe", R.drawable.cat_local_cafe},
            {"cat_local_gas_station", R.drawable.cat_local_gas_station},
            {"cat_menu_book", R.drawable.cat_menu_book},
            {"cat_movie", R.drawable.cat_movie},
            {"cat_music_note", R.drawable.cat_music_note},
            {"cat_palette", R.drawable.cat_palette},
            {"cat_pets", R.drawable.cat_pets},
            {"cat_phishing", R.drawable.cat_phishing},
            {"cat_photo_camera", R.drawable.cat_photo_camera},
            {"cat_redeem", R.drawable.cat_redeem},
            {"cat_restaurant", R.drawable.cat_restaurant},
            {"cat_savings", R.drawable.cat_savings},
            {"cat_school", R.drawable.cat_school},
            {"cat_shopping_cart", R.drawable.cat_shopping_cart},
            {"cat_smartphone", R.drawable.cat_smartphone},
            {"cat_spa", R.drawable.cat_spa},
            {"cat_sports_esports", R.drawable.cat_sports_esports},
            {"cat_sports_motorsports", R.drawable.cat_sports_motorsports},
            {"cat_sports_soccer", R.drawable.cat_sports_soccer},
    };

    private static final List<Categoria> PREDEFINITE = Arrays.asList(
            new Categoria("alimentari", "Spesa", "cat_shopping_cart", 0xFF30D158),
            new Categoria("ristoranti", "Ristoranti", "cat_restaurant", 0xFFFF9F0A),
            new Categoria("casa", "Casa", "cat_home", 0xFF0A84FF),
            new Categoria("trasporti", "Auto e trasporti", "cat_directions_car", 0xFF40C8E0),
            new Categoria("tecnologia", "Tecnologia", "cat_smartphone", 0xFF5E5CE6),
            new Categoria("abbigliamento", "Abbigliamento", "cat_checkroom", 0xFFFF375F),
            new Categoria("svago", "Svago", "cat_sports_esports", 0xFFBF5AF2),
            new Categoria("viaggi", "Viaggi", "cat_flight", 0xFF64D2FF),
            new Categoria("salute", "Salute", "cat_favorite", 0xFFFF453A),
            new Categoria("sport", "Sport", "cat_fitness_center", 0xFF66D4CF),
            new Categoria("regali", "Regali", "cat_redeem", 0xFFFFD60A),
            new Categoria("abbonamenti", "Abbonamenti", "cat_autorenew", 0xFFAC8E68),
            new Categoria(ALTRO, "Altro", "cat_category", 0xFF8E8E93));

    private static final String PREFS = "SalarioPrefs";
    private static final String KEY = "categorie_custom";
    private static final Gson GSON = new Gson();

    private Categorie() {}

    public static String[] nomiIcone() {
        String[] out = new String[ICONE.length];
        for (int i = 0; i < ICONE.length; i++) out[i] = (String) ICONE[i][0];
        return out;
    }

    public static int iconaRes(String nome) {
        for (Object[] r : ICONE) if (r[0].equals(nome)) return (Integer) r[1];
        return R.drawable.cat_category;
    }

    public static List<Categoria> personalizzate(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        String json = p.getString(KEY, null);
        List<Categoria> out = new ArrayList<Categoria>();
        if (json == null) return out;
        try {
            Type t = new TypeToken<List<Categoria>>() {}.getType();
            List<Categoria> l = GSON.fromJson(json, t);
            if (l != null) for (Categoria c : l) if (c != null && c.key != null) out.add(c);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static void salva(Context ctx, List<Categoria> l) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, GSON.toJson(l)).apply();
    }

    /** Nome tradotto delle categorie predefinite (le personalizzate restano come le hai chiamate). */
    private static Categoria tradotta(Context ctx, Categoria c) {
        Context l = Lingua.contesto(ctx);
        int id = l.getResources().getIdentifier("catn_" + c.key, "string", l.getPackageName());
        return id == 0 ? c : new Categoria(c.key, l.getString(id), c.icona, c.colore);
    }

    /** Tutte le categorie: predefinite (Altro in fondo) + personalizzate prima di Altro. */
    public static List<Categoria> tutte(Context ctx) {
        List<Categoria> out = new ArrayList<Categoria>();
        for (Categoria c : PREDEFINITE) if (!ALTRO.equals(c.key)) out.add(tradotta(ctx, c));
        out.addAll(personalizzate(ctx));
        out.add(tradotta(ctx, PREDEFINITE.get(PREDEFINITE.size() - 1)));
        return out;
    }

    /** Categoria per chiave; spese vecchie o categorie eliminate -> Altro. */
    public static Categoria get(Context ctx, String key) {
        if (key != null) {
            for (Categoria c : PREDEFINITE) if (c.key.equals(key)) return tradotta(ctx, c);
            for (Categoria c : personalizzate(ctx)) if (c.key.equals(key)) return c;
        }
        return tradotta(ctx, PREDEFINITE.get(PREDEFINITE.size() - 1));
    }

    public static Categoria aggiungi(Context ctx, String nome, String icona, int colore) {
        List<Categoria> l = personalizzate(ctx);
        Categoria c = new Categoria("u_" + UUID.randomUUID().toString(), nome, icona, colore);
        l.add(c);
        salva(ctx, l);
        return c;
    }

    public static void elimina(Context ctx, String key) {
        List<Categoria> l = personalizzate(ctx);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).key.equals(key)) l.remove(i);
        salva(ctx, l);
    }
}
