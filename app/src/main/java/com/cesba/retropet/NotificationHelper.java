package com.cesba.retropet;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Crea el canal y muestra las notificaciones locales de Bob. */
public class NotificationHelper {

    public static final String CHANNEL = "retropet_alerts";

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Alertas de Bob", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Avisos cuando Bob necesita atención o termina de descansar");
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @SuppressLint("MissingPermission")
    public static void show(Context c, int id, String title, String text) {
        // Respeta el interruptor de Configuracion
        SharedPreferences prefs = c.getSharedPreferences("retropet_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("notifications", true)) return;

        // Respeta el permiso de Android 13+
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                c, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;

        ensureChannel(c);
        Intent open = new Intent(c, LoginActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManagerCompat.from(c).notify(id, b.build());
    }
}
