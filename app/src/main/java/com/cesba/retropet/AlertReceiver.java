package com.cesba.retropet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recibe la alarma programada y muestra la notificacion correspondiente. */
public class AlertReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String type = intent.getStringExtra("type");
        if ("WAKE".equals(type)) {
            NotificationHelper.show(context, 1, "Bob despertó 😄",
                    "Terminó de descansar y está listo para jugar.");
        } else if ("CRITICAL".equals(type)) {
            NotificationHelper.show(context, 2, "¡Bob te necesita! ⚠",
                    "Alguna de sus necesidades está muy baja. Entra a cuidarlo.");
        }
    }
}