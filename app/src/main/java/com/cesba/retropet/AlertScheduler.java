package com.cesba.retropet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.cesba.retropet.data.PetEntity;

/**
 * Programa las alertas al salir de la pantalla principal y las cancela al volver.
 *  - WAKE: cuando termina el descanso (sleepUntil).
 *  - CRITICAL: cuando alimentacion, higiene o felicidad estan por bajar de 20
 *    (se estima con las mismas tasas de NeedsEngine). Si ya estan bajas, recuerda en 2 h.
 * Usa alarmas inexactas (no requieren permiso especial; el PDF no exige precision al segundo).
 */
public class AlertScheduler {

    private static final int REQ_WAKE = 1, REQ_CRIT = 2;
    private static final float CRITICAL = 20f;

    private static PendingIntent pi(Context c, int req, String type, int flags) {
        Intent i = new Intent(c, AlertReceiver.class);
        i.putExtra("type", type);
        return PendingIntent.getBroadcast(c, req, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancel(Context c, int req) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, req, "", PendingIntent.FLAG_NO_CREATE);
        if (p != null && am != null) { am.cancel(p); p.cancel(); }
    }

    public static void cancelAll(Context c) {
        Context app = c.getApplicationContext();
        cancel(app, REQ_WAKE);
        cancel(app, REQ_CRIT);
    }

    private static void set(Context c, int req, String type, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                pi(c, req, type, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    public static void schedule(Context c, PetEntity p) {
        Context app = c.getApplicationContext();
        cancelAll(app);
        if (p == null) return;
        SharedPreferences prefs = app.getSharedPreferences("retropet_prefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("notifications", true)) return;

        long now = System.currentTimeMillis();

        // Fin del descanso
        if (p.sleepUntil > now) set(app, REQ_WAKE, "WAKE", p.sleepUntil);

        // Necesidad critica: horas hasta que la primera estadistica baje de 20
        float fm = "EVO_A".equals(p.stage) ? 0.7f : 1f;
        double best = Double.MAX_VALUE;
        best = Math.min(best, hoursToCritical(p.food, 6f * fm));
        best = Math.min(best, hoursToCritical(p.hygiene, 3f * fm));
        best = Math.min(best, hoursToCritical(p.happiness, 4f));

        long at;
        if (best == Double.MAX_VALUE) at = now + 2L * 3600_000;   // ya estan bajas: recordatorio en 2 h
        else at = now + (long) (best * 3600_000);
        if (at < now + 60_000) at = now + 60_000;
        set(app, REQ_CRIT, "CRITICAL", at);
    }

    private static double hoursToCritical(float value, float ratePerHour) {
        if (value <= CRITICAL) return Double.MAX_VALUE;
        return (value - CRITICAL) / ratePerHour;
    }
}