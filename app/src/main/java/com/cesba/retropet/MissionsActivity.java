package com.cesba.retropet;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.data.MissionEntity;
import com.cesba.retropet.data.PetEntity;
import com.cesba.retropet.logic.ActionResult;
import com.cesba.retropet.logic.EvolutionEngine;
import com.cesba.retropet.logic.GameEngine;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Misiones (diarias + logros) y progreso/evolucion. Recibe "uid" por Intent. */
public class MissionsActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private GameDao dao;
    private GameEngine game;
    private long uid;
    private PetEntity pet;
    private LinearLayout root;
    private String lastMsg = "";
    private boolean lastOk = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = getIntent().getLongExtra("uid", -1);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#FFD54F"));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        io.execute(() -> {
            dao = AppDatabase.get(this).dao();
            game = new GameEngine(dao, uid);
            reload();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        MusicPlayer.play(this, R.raw.musica_progreso);   // <-- canción de progreso
        if (game != null) io.execute(this::reload);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
    }

    private void reload() {
        pet = game.load(); // tambien reinicia las diarias si cambio el dia
        if (pet == null) { runOnUiThread(this::finish); return; }
        List<MissionEntity> missions = dao.getMissions(uid);
        runOnUiThread(() -> render(missions));
    }

    private void render(List<MissionEntity> missions) {
        root.removeAllViews();

        Button back = new Button(this);
        back.setText("← Volver");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView msg = text(lastMsg, 14, true);
        msg.setTextColor(lastOk ? Color.parseColor("#1B5E20") : Color.parseColor("#B00020"));
        root.addView(msg);

        // ---------- PROGRESO ----------
        root.addView(title("📈 Progreso"));
        root.addView(text(pet.name + " - " + EvolutionEngine.stageName(pet.stage), 18, true));
        root.addView(text("Nivel " + pet.level, 16, false));

        int next = GameEngine.xpForNextLevel(pet);
        ProgressBar xpBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        xpBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        xpBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB300")));
        if (next < 0) {
            root.addView(text("XP: " + pet.xp + " (nivel máximo)", 14, false));
            xpBar.setMax(100); xpBar.setProgress(100);
        } else {
            root.addView(text("XP: " + pet.xp + " / " + next, 14, false));
            xpBar.setMax(next); xpBar.setProgress(pet.xp);
        }
        root.addView(xpBar);

        int care = pet.feedCount + pet.careCount;
        int activity = pet.playCount + pet.trainCount + pet.minigameWins;
        root.addView(text("\nEvolución (se decide en el nivel " + EvolutionEngine.EVOLVE_LEVEL + "):", 15, true));
        if ("BASE".equals(pet.stage)) {
            root.addView(text("Cuidado (alimentar + bañar/curar): " + care
                    + "\nActividad (jugar + entrenar + minijuegos ganados): " + activity
                    + "\nTendencia actual: " + (care >= activity
                    ? "Chef Estrella 👨‍🍳 (más cuidado)" : "Aventurero 🏄 (más actividad)"), 14, false));
        } else {
            root.addView(text("¡Ya evolucionó a " + EvolutionEngine.stageName(pet.stage) + "!\n"
                    + ("EVO_A".equals(pet.stage)
                    ? "Ventaja: Cangreburgers x2 y hambre/higiene bajan 30% más lento."
                    : "Ventaja: +25% monedas en minijuegos y energía baja 30% más lento."), 14, false));
        }
        float avg = pet.wellbeingSamples == 0 ? 0 : pet.wellbeingSum / pet.wellbeingSamples;
        root.addView(text("Bienestar promedio histórico: " + Math.round(avg) + "/100", 14, false));
        root.addView(text("Récords → Memoria: " + pet.bestMemory + "   Reflejos: " + pet.bestReflex
                + "   Victorias: " + pet.minigameWins, 14, false));

        // ---------- MISIONES ----------
        root.addView(title("📅 Misiones diarias"));
        for (MissionEntity m : missions) if ("DAILY".equals(m.type)) root.addView(missionRow(m));
        root.addView(title("🏆 Logros"));
        for (MissionEntity m : missions) if ("ACHIEVEMENT".equals(m.type)) root.addView(missionRow(m));
    }

    private LinearLayout missionRow(MissionEntity m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);

        // El nivel actual cuenta como avance de la mision "Alcanza el nivel X"
        int shown = "LEVEL".equals(m.metric) ? Math.min(m.target, Math.max(m.progress, pet.level)) : m.progress;
        boolean complete = shown >= m.target;

        row.addView(text(m.description, 16, true));
        row.addView(text("Avance: " + shown + "/" + m.target
                + "   Recompensa: 💰 " + m.rewardCoins + (m.rewardXp > 0 ? " + " + m.rewardXp + " XP" : ""), 13, false));

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(m.target);
        bar.setProgress(shown);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676")));
        row.addView(bar);

        Button claim = new Button(this);
        if (m.claimed) {
            claim.setText("✔ Reclamada");
            claim.setEnabled(false);
        } else if (complete) {
            claim.setText("Reclamar recompensa 🎁");
            claim.setOnClickListener(v -> io.execute(() -> {
                pet = game.load();
                ActionResult r = game.claimMission(pet, m.id);
                lastMsg = r.message;
                lastOk = r.ok;
                reload();
            }));
        } else {
            claim.setText("En progreso...");
            claim.setEnabled(false);
        }
        row.addView(claim);
        return row;
    }

    private TextView title(String s) {
        TextView t = text(s, 20, true);
        Typeface tf = ResourcesCompat.getFont(this, R.font.esponja_fuente);
        if (tf != null) t.setTypeface(tf, Typeface.BOLD);
        t.setPadding(0, dp(20), 0, dp(4));
        return t;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
