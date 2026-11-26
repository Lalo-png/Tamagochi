package com.cesba.retropet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.logic.GameEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Configuracion: sonido, notificaciones, reinicio con confirmacion y cerrar sesion. */
public class SettingsActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long uid;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = getIntent().getLongExtra("uid", -1);
        prefs = getSharedPreferences("retropet_prefs", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#FFF8C4"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        Button back = new Button(this);
        back.setText("← Volver");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView title = new TextView(this);
        title.setText("⚙ Configuración");
        title.setTextSize(22);
        title.setTextColor(Color.BLACK);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);

        root.addView(toggle("🔊 Sonido", "sound"));
        root.addView(toggle("🔔 Notificaciones", "notifications"));

        Button reset = new Button(this);
        reset.setText("Reiniciar partida 🗑");
        reset.setOnClickListener(v -> confirmReset());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(24), 0, 0);
        reset.setLayoutParams(lp);
        root.addView(reset);

        Button logout = new Button(this);
        logout.setText("Cerrar sesión");
        logout.setOnClickListener(v -> {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        root.addView(logout);

        setContentView(scroll);
    }

    private SwitchCompat toggle(String label, String key) {
        SwitchCompat s = new SwitchCompat(this);
        s.setText(label);
        s.setTextSize(18);
        s.setTextColor(Color.BLACK);
        s.setPadding(0, dp(12), 0, dp(12));
        s.setChecked(prefs.getBoolean(key, true));
        s.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(key, on).apply();
            if (key.equals("sound")) {
                if (on) MusicPlayer.play(this, R.raw.musica_menu);
                else MusicPlayer.stop();
            }
        });return s;
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("¿Reiniciar partida?")
                .setMessage("Se borrarán TODOS los datos de tu mascota: nivel, monedas, "
                        + "inventario, decoración y misiones. Esto no se puede deshacer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sí, borrar todo", (d, w) -> io.execute(() -> {
                    GameDao dao = AppDatabase.get(this).dao();
                    new GameEngine(dao, uid).resetGame();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Partida reiniciada", Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(this, CharacterActivity.class);
                        i.putExtra("uid", uid);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    });
                }))
                .show();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onResume() {
        super.onResume();
        MusicPlayer.play(this, R.raw.musica_menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
    }
}

