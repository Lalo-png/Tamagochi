package com.cesba.retropet;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.data.PetEntity;
import com.cesba.retropet.logic.ActionResult;
import com.cesba.retropet.logic.GameEngine;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minijuego 2: Reflejos (atrapa medusas), con 3 dificultades. Recibe "uid" por Intent. */
public class ReflexGameActivity extends AppCompatActivity {

    private static final int GAME_SECONDS = 20;
    private static final String JELLY = "🪼";
    private static final String PUFFER = "🐡";

    // Dificultad: 0 = Facil, 1 = Medio, 2 = Dificil
    private static final String[] DIFF_NAMES = {"Fácil", "Medio", "Difícil"};
    private static final int[] GOAL_BY = {10, 14, 18};            // puntos para ganar
    private static final long[] BASE_BY = {1000, 850, 700};       // ms entre apariciones al inicio
    private static final long[] MIN_BY = {700, 500, 380};         // ms minimos
    private static final int[] PUFFER_BY = {20, 25, 35};          // % de peces globo
    private static final String[] MULT_TEXT = {"x1", "x1.5", "x2"};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private GameDao dao;
    private GameEngine game;
    private long uid;
    private PetEntity pet;
    private LinearLayout root;

    // Estado de la partida
    private int difficulty = 0;
    private int winScore;
    private long baseInterval, minInterval;
    private int pufferPct;
    private final Button[] cells = new Button[9];
    private String[] cellContent = new String[9];
    private int score = 0, secondsLeft = GAME_SECONDS, current = -1;
    private boolean ended = false;
    private long startTime;
    private TextView tvStatus, tvDiffInfo;
    private final Button[] diffButtons = new Button[3];
    private CountDownTimer timer;

    // Zona de abajo, FUERA del panel blanco: menu de dificultad o imagen de retroalimentacion
    private FrameLayout bottomZone;
    private FrameLayout feedbackBox;
    private ImageView feedbackImg;
    private final Runnable clearFlash = () -> {
        if (feedbackImg != null) feedbackImg.clearColorFilter();
        if (feedbackBox != null) feedbackBox.setBackground(null);
    };

    private final Runnable spawner = new Runnable() {
        @Override public void run() {
            if (ended) return;
            clearCell(current);                       // la anterior se escapa
            int next;
            do { next = rnd.nextInt(9); } while (next == current);
            current = next;
            String type = rnd.nextInt(100) >= pufferPct ? JELLY : PUFFER;
            cellContent[current] = type;
            cells[current].setText(type);
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            long interval = Math.max(minInterval, baseInterval - elapsed * 20);   // cada vez mas rapido
            handler.postDelayed(this, interval);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = getIntent().getLongExtra("uid", -1);

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        root.setBackground(BackgroundHelper.panel(this));
        scroll.addView(root);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        bottomZone = new FrameLayout(this);
        LinearLayout.LayoutParams zoneLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        zoneLp.bottomMargin = dp(48);          // sube la zona: aumenta el numero para subirla mas
        screen.addView(bottomZone, zoneLp);
        setContentView(BackgroundHelper.wrap(this, screen, "fondo_reflejos"));

        io.execute(() -> {
            dao = AppDatabase.get(this).dao();
            game = new GameEngine(dao, uid);
            pet = game.load();
            if (pet == null) { runOnUiThread(this::finish); return; }
            runOnUiThread(() -> showIntro(""));
        });
    }

    // ---------- Instrucciones + selector de dificultad ----------
    private void showIntro(String msg) {
        handler.removeCallbacks(clearFlash);
        root.removeAllViews();
        root.addView(backButton());
        root.addView(text("🪼 Reflejos: atrapa medusas", 22, true));
        root.addView(text("\nInstrucciones:\n"
                + "• Tienes " + GAME_SECONDS + " segundos.\n"
                + "• Toca cada medusa " + JELLY + " que aparezca: +1 punto.\n"
                + "• ¡No toques el pez globo " + PUFFER + "! Te quita 2 puntos y pone la imagen de abajo en rojo.\n"
                + "• Si no la tocas a tiempo, la medusa se escapa.\n"
                + "• Elige la dificultad en el menú de abajo.\n"
                + "• Cada partida cuesta 10 de energía ⚡.\n", 15, false));
        root.addView(text("Energía de Bob: " + Math.round(pet.energy) + "/100\n"
                + "Tu récord: " + pet.bestReflex + " puntos\n", 15, true));

        showDifficultyMenu();

        TextView m = text(msg, 14, true);
        m.setTextColor(Color.parseColor("#B00020"));
        root.addView(m);

        Button start = new Button(this);
        start.setText("¡Jugar! (-10 ⚡)");
        start.setOnClickListener(v -> startGame());
        root.addView(start);
    }

    private void refreshDifficulty() {
        for (int i = 0; i < 3; i++) {
            boolean sel = i == difficulty;
            diffButtons[i].setText((sel ? "✔ " : "") + DIFF_NAMES[i]);
            diffButtons[i].setAlpha(sel ? 1f : 0.45f);
        }
        tvDiffInfo.setText(DIFF_NAMES[difficulty] + ": meta de " + GOAL_BY[difficulty]
                + " puntos.\nRecompensa por ganar: " + MULT_TEXT[difficulty]);
    }

    /** Menu de dificultad: vive FUERA del panel blanco, en el espacio de abajo. */
    private void showDifficultyMenu() {
        handler.removeCallbacks(clearFlash);
        feedbackBox = null;
        feedbackImg = null;
        bottomZone.removeAllViews();

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackground(rounded(Color.parseColor("#99000000")));
        menu.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView label = text("Elige la dificultad:", 16, true);
        label.setTextColor(Color.WHITE);
        menu.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int d = i;
            Button b = new Button(this);
            b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setOnClickListener(v -> { difficulty = d; refreshDifficulty(); });
            diffButtons[i] = b;
            row.addView(b);
        }
        menu.addView(row);

        tvDiffInfo = text("", 14, true);
        tvDiffInfo.setTextColor(Color.WHITE);
        tvDiffInfo.setPadding(0, dp(6), 0, 0);
        menu.addView(tvDiffInfo);
        refreshDifficulty();

        bottomZone.addView(menu, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    private void startGame() {
        io.execute(() -> {
            pet = game.load();
            ActionResult r = game.startMinigame(pet);   // valida y cobra energia
            if (!r.ok) {
                runOnUiThread(() -> showIntro(r.message));
                return;
            }
            pet = game.load();
            runOnUiThread(this::buildBoard);
        });
    }

    // ---------- Tablero ----------
    private void buildBoard() {
        winScore = GOAL_BY[difficulty];
        baseInterval = BASE_BY[difficulty];
        minInterval = MIN_BY[difficulty];
        pufferPct = PUFFER_BY[difficulty];
        score = 0; secondsLeft = GAME_SECONDS; current = -1; ended = false;
        cellContent = new String[9];

        root.removeAllViews();     // aqui desaparece el menu de dificultades
        root.addView(text("🪼 Reflejos - " + DIFF_NAMES[difficulty], 22, true));
        tvStatus = text("", 17, true);
        root.addView(tvStatus);
        updateStatus();

        int idx = 0;
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 3; c++) {
                final int i = idx++;
                Button b = new Button(this);
                b.setText("");
                b.setTextSize(36);
                b.setMinWidth(0);
                b.setMinimumWidth(0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(100), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                b.setLayoutParams(lp);
                b.setOnClickListener(v -> onCell(i));
                cells[i] = b;
                row.addView(b);
            }
            root.addView(row);
        }

        buildFeedbackBox("img_minijuego_reflejos", "🪼");

        startTime = System.currentTimeMillis();
        handler.postDelayed(spawner, 800);
        timer = new CountDownTimer(GAME_SECONDS * 1000L, 250) {
            @Override public void onTick(long ms) {
                secondsLeft = (int) Math.ceil(ms / 1000.0);
                updateStatus();
            }
            @Override public void onFinish() {
                secondsLeft = 0;
                updateStatus();
                endGame();
            }
        }.start();
    }

    /** Imagen en el espacio de abajo (fuera del panel blanco). Si no existe, muestra un emoji. */
    private void buildFeedbackBox(String drawableName, String emoji) {
        bottomZone.removeAllViews();
        feedbackBox = new FrameLayout(this);
        feedbackImg = null;
        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int res = getResources().getIdentifier(drawableName, "drawable", getPackageName());
        if (res != 0) {
            feedbackImg = new ImageView(this);
            feedbackImg.setImageResource(res);
            feedbackImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            feedbackBox.addView(feedbackImg, fill);
        } else {
            TextView t = new TextView(this);
            t.setText(emoji);
            t.setTextSize(72);
            t.setGravity(Gravity.CENTER);
            feedbackBox.addView(t, fill);
        }
        bottomZone.addView(feedbackBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Pone la imagen en rojo por 2 segundos (si no hay imagen, enrojece el recuadro del emoji). */
    private void flashError() {
        if (feedbackBox == null) return;
        if (feedbackImg != null) {
            feedbackImg.setColorFilter(Color.argb(200, 255, 0, 0), PorterDuff.Mode.SRC_ATOP);
        } else {
            feedbackBox.setBackground(rounded(Color.parseColor("#99F44336")));
        }
        handler.removeCallbacks(clearFlash);
        handler.postDelayed(clearFlash, 2000);
    }

    private void updateStatus() {
        tvStatus.setText("⏱ " + secondsLeft + " s     Puntos: " + score + " (meta " + winScore + ")");
    }

    private void clearCell(int i) {
        if (i >= 0) { cellContent[i] = null; cells[i].setText(""); }
    }

    private void onCell(int i) {
        if (ended || cellContent[i] == null) return;
        if (JELLY.equals(cellContent[i])) {
            score++;
        } else {
            score = Math.max(0, score - 2);
            flashError();                              // error: la imagen se pone roja
        }
        clearCell(i);
        updateStatus();
    }

    // ---------- Fin ----------
    private void endGame() {
        if (ended) return;
        ended = true;
        handler.removeCallbacks(spawner);
        if (timer != null) timer.cancel();
        final boolean won = score >= winScore;
        final int finalScore = score;
        final int diff = difficulty;
        io.execute(() -> {
            pet = game.load();
            ActionResult r = game.finishMinigame(pet, "REFLEX", finalScore, won, diff);
            pet = game.load();
            runOnUiThread(() -> showResult(won, finalScore, r.message, diff));
        });
    }

    private void showResult(boolean won, int finalScore, String msg, int diff) {
        handler.removeCallbacks(clearFlash);
        root.removeAllViews();
        root.addView(text(won ? "🎉 ¡Ganaste!" : "😢 Se acabó el tiempo", 24, true));
        root.addView(text("\nDificultad: " + DIFF_NAMES[diff] + "\nPuntaje: " + finalScore
                + " (meta " + GOAL_BY[diff] + ")\nRécord: " + pet.bestReflex
                + "\n" + msg + "\nMonedas: 💰 " + pet.coins, 17, false));

        Button again = new Button(this);
        again.setText("Jugar de nuevo (-10 ⚡)");
        again.setOnClickListener(v -> startGame());
        root.addView(again);

        root.addView(text("\nPuedes cambiar la dificultad en el menú de abajo.", 14, false));
        showDifficultyMenu();

        root.addView(backButton());
    }

    // ---------- Utilidades ----------
    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(16));
        return g;
    }

    private Button backButton() {
        Button b = new Button(this);
        b.setText("← Volver");
        b.setOnClickListener(v -> finish());
        return b;
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

    @Override
    protected void onResume() {
        super.onResume();
        MusicPlayer.play(this, R.raw.musica_medusas);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ended = true;
        handler.removeCallbacksAndMessages(null);
        if (timer != null) timer.cancel();
    }
}