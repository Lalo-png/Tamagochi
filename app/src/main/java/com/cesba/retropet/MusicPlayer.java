package com.cesba.retropet;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

/**
 * Musica de fondo en bucle. Una sola cancion suena a la vez.
 * - play(): inicia la cancion (si ya suena la misma, no la reinicia).
 * - pause(): pausa con 0.5 s de espera; si otra pantalla llama play() a tiempo, no se corta.
 * - stop(): libera el reproductor (se usa al apagar el sonido).
 * Respeta el interruptor "sound" de Configuracion.
 */
public class MusicPlayer {

    private static MediaPlayer mp;
    private static int currentRes = 0;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable pauser = () -> {
        if (mp != null && mp.isPlaying()) mp.pause();
    };

    public static void play(Context context, int resId) {
        handler.removeCallbacks(pauser);
        Context app = context.getApplicationContext();
        boolean soundOn = app.getSharedPreferences("retropet_prefs", Context.MODE_PRIVATE)
                .getBoolean("sound", true);
        if (!soundOn) { stop(); return; }

        if (mp != null && currentRes == resId) {          // misma cancion: continuar
            if (!mp.isPlaying()) mp.start();
            return;
        }
        stop();                                            // otra cancion: cambiar
        mp = MediaPlayer.create(app, resId);
        if (mp == null) return;
        currentRes = resId;
        mp.setLooping(true);
        mp.setVolume(0.6f, 0.6f);
        mp.start();
    }

    public static void pause() {
        handler.removeCallbacks(pauser);
        handler.postDelayed(pauser, 500);
    }

    public static void stop() {
        handler.removeCallbacks(pauser);
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) { }
            mp.release();
            mp = null;
        }
        currentRes = 0;
    }
}