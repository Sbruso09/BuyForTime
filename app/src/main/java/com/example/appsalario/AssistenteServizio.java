package com.example.appsalario;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

/**
 * Servizio in primo piano attivo solo mentre l'assistente prepara una risposta:
 * impedisce ad Android di fermare il lavoro se chiudi l'app. Si chiude da solo alla fine.
 */
public final class AssistenteServizio extends Service {
    private static final String CANALE = "assistente_lavoro";
    private static final int ID = 4241;

    static void avvia(Context c) {
        try {
            c.startForegroundService(new Intent(c, AssistenteServizio.class));
        } catch (RuntimeException ignored) {
            // senza servizio la risposta arriva comunque finche' l'app resta aperta
        }
    }

    static void ferma(Context c) {
        try {
            c.stopService(new Intent(c, AssistenteServizio.class));
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Context c = Lingua.contesto(this);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CANALE, c.getString(R.string.notif_ai_lavoro_canale),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        Notification n = new NotificationCompat.Builder(this, CANALE)
                .setSmallIcon(R.drawable.ic_auto_awesome)
                .setContentTitle(c.getString(R.string.notif_ai_lavoro))
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setSilent(true)
                .build();
        try {
            startForeground(ID, n);
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!AssistenteMotore.get().inCorso()) stopSelf(); // risposta gia' arrivata nel frattempo
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
