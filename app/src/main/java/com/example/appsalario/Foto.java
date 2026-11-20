package com.example.appsalario;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.LruCache;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/** Foto della chat: ridimensionate (max 1280 px, JPEG) prima di salvarle e inviarle a Gemini. */
final class Foto {
    private static final int MAX_LATO = 1280;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String k, Bitmap b) {
            return b.getByteCount();
        }
    };

    private Foto() {}

    /** Legge la foto, la raddrizza secondo l'EXIF, la riduce e la salva in {@code cartella}. Null se non riesce. */
    static String prepara(Context c, Uri uri, File cartella) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int campione = 1;
            while (Math.max(o.outWidth, o.outHeight) / (campione * 2) >= MAX_LATO) campione *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = campione;
            in = c.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            if (in != null) in.close();
            if (b == null) return null;

            int gradi = 0;
            in = c.getContentResolver().openInputStream(uri);
            if (in != null) {
                try {
                    android.media.ExifInterface exif = new android.media.ExifInterface(in);
                    int or = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
                    gradi = or == 6 ? 90 : or == 3 ? 180 : or == 8 ? 270 : 0;
                } catch (Exception ignored) {
                }
                in.close();
            }
            float scala = Math.min(1f, MAX_LATO / (float) Math.max(b.getWidth(), b.getHeight()));
            if (gradi != 0 || scala < 1f) {
                Matrix m = new Matrix();
                m.postScale(scala, scala);
                m.postRotate(gradi);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            File f = new File(cartella, UUID.randomUUID().toString() + ".jpg");
            FileOutputStream out = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, 82, out);
            out.close();
            b.recycle();
            return f.getAbsolutePath();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** Versione piccola per mostrarla in chat (in cache). */
    static Bitmap miniatura(String percorso, int maxLato) {
        String k = percorso + "@" + maxLato;
        Bitmap b = CACHE.get(k);
        if (b != null) return b;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(percorso, o);
            if (o.outWidth <= 0) return null;
            int campione = 1;
            while (Math.max(o.outWidth, o.outHeight) / (campione * 2) >= maxLato) campione *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = campione;
            b = BitmapFactory.decodeFile(percorso, o2);
            if (b != null) CACHE.put(k, b);
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }
}
