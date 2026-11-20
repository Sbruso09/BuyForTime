package com.example.appsalario;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import java.text.NumberFormat;
import java.util.Locale;

public final class NotificationReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "spese_reminder_channel";
    public static final String CHANNEL_RIPAGO = "spese_ripagate_channel";

    @Override
    public void onReceive(Context ricevuto, Intent intent) {
        Context context = Lingua.contesto(ricevuto);
        String azione = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(azione) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(azione)) {
            // gli allarmi si perdono al riavvio: li riprogrammo tutti
            new SpeseManager(context).riprogrammaRipaghi();
            return;
        }
        if (SpeseManager.AZIONE_PROVA.equals(azione)) {
            notificaProva(context);
            return;
        }
        if (SpeseManager.AZIONE_RIPAGO.equals(azione)) {
            notificaRipago(context, intent.getStringExtra("SPESA_ID"));
            return;
        }
        String spesaId = intent.getStringExtra("SPESA_ID");
        SpeseManager manager = new SpeseManager(context);
        Spesa attuale = manager.getSpesa(spesaId);
        // spesa eliminata o gia' approvata nel frattempo: niente promemoria
        if (attuale == null || attuale.getApprovata()) {
            manager.segnaNotificaInviata(spesaId);
            return;
        }
        String nome = attuale.getNome();
        float importo = attuale.getImporto();
        float ore = attuale.getOreNecessarie(); // dati aggiornati (es. dopo un cambio di stipendio)
        // il promemoria e' stato consegnato: tolgo la campanella dalla lista
        manager.segnaNotificaInviata(spesaId);

        NumberFormat valuta = NumberFormat.getCurrencyInstance(Locale.ITALY);
        String testo = context.getString(R.string.notifica_text, valuta.format(importo),
                TempoFormatter.INSTANCE.formatta(context, ore, manager.getOreGiorno()));
        creaCanale(context);

        int codice = spesaId != null ? spesaId.hashCode() : nome.hashCode();
        Intent apri = new Intent(context, MainActivity.class);
        apri.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        apri.putExtra("SPESA_ID", spesaId);
        // requestCode distinto per spesa: prima era 0 per tutte e l'ultima notifica
        // sovrascriveva l'ID delle precedenti (toccandone una si apriva la spesa sbagliata)
        PendingIntent pi = PendingIntent.getActivity(context, codice, apri,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification n = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(nome + " - " + context.getString(R.string.notifica_title))
                .setContentText(testo)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(codice, n);
    }

    private void notificaRipago(Context context, String spesaId) {
        SpeseManager manager = new SpeseManager(context);
        Spesa spesa = manager.getSpesa(spesaId);
        if (spesa == null || !manager.consumaNotificaRipago(spesaId)) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_RIPAGO,
                context.getString(R.string.notifica_ripago_canale), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(context.getString(R.string.notifica_ripago_canale_desc));
        nm.createNotificationChannel(ch);

        int codice = (spesaId + ":ripago").hashCode();
        Intent apri = new Intent(context, MainActivity.class);
        apri.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        apri.putExtra("SPESA_ID", spesaId);
        PendingIntent pi = PendingIntent.getActivity(context, codice, apri,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String importo = NumberFormat.getCurrencyInstance(Locale.ITALY).format(spesa.getImporto());
        Notification n = new NotificationCompat.Builder(context, CHANNEL_RIPAGO)
                .setSmallIcon(R.drawable.ic_checkmark_circle)
                .setContentTitle(context.getString(R.string.notifica_ripago_titolo, spesa.getNome()))
                .setContentText(context.getString(R.string.notifica_ripago_testo, importo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(codice, n);
    }

    private void notificaProva(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_RIPAGO,
                context.getString(R.string.notifica_ripago_canale), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(context.getString(R.string.notifica_ripago_canale_desc));
        nm.createNotificationChannel(ch);
        Intent apri = new Intent(context, MainActivity.class);
        apri.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 777, apri,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(context, CHANNEL_RIPAGO)
                .setSmallIcon(R.drawable.ic_checkmark_circle)
                .setContentTitle(context.getString(R.string.notif_prova_titolo))
                .setContentText(context.getString(R.string.notif_prova_testo))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(777, n);
    }

    private void creaCanale(Context context) {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notifica_channel_name), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(context.getString(R.string.notifica_channel_desc));
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(ch);
    }
}
