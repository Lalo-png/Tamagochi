package com.cesba.retropet;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minijuego 1: Memoria de parejas, con 3 dificultades. Recibe "uid" por Intent. */
public class MemoryGameActivity extends AppCompatActivity {

    // Dificultad: 0 = Facil, 1 = Medio, 2 = Dificil
    private static final String[] DIFF_NAMES = {"Fácil", "Medio", "Difícil"};
    private static final int[] PAIRS_BY = {6, 8, 10};
    private static final int[] COLS_BY = {3, 4, 4};
    private static final int[] MOVES_BY = {16, 18, 20};
    private static final String[] MULT_TEXT = {"x1", "x1.5", "x2"};
    private static final String[] EMOJIS = {"🧽", "🍔", "🪼", "🐌", "🦀", "🦑", "🐠", "🐚", "🍍", "🐙"};
    private static final int MAX_CARDS = 20;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GameDao dao;
    private GameEngine game;
    private long uid;
    private PetEntity pet;
    private LinearLayout root;

    // Estado de la partida
    private int difficulty = 0;
    private int pairsTotal, maxMoves, cols;
    private final Button[] cards = new Button[MAX_CARDS];
    private final String[] values = new String[MAX_CARDS];
    private final boolean[] matched = new boolean[MAX_CARDS];
    private int first = -1, moves = 0, pairs = 0;
    private boolean busy = false, ended = false;
    private TextView tvStatus, tvDiffInfo;
    private final Button[] diffButtons = new Button[3];

    // Zona de abajo, FUERA del panel blanco: menu de dificultad o imagen de retroalimentacion
    private FrameLayout bottomZone;
    private FrameLayout feedbackBox;
    private ImageView feedbackImg;
    private final Runnable clearFlash = () -> {
        if (feedbackImg != null) feedbackImg.clearColorFilter();
        if (feedbackBox != null) feedbackBox.setBackground(null);
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
        setContentView(BackgroundHelper.wrap(this, screen, "fondo_memoria"));

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
        root.addView(text("🧠 Memoria de Fondo de Bikini", 22, true));
        root.addView(text("\nInstrucciones:\n"
                + "• Toca dos cartas para voltearlas.\n"
                + "• Si son iguales, se quedan descubiertas.\n"
                + "• Encuentra todas las parejas antes de quedarte sin intentos.\n"
                + "• Entre menos intentos uses, más monedas ganas.\n"
                + "• Elige la dificultad en el menú de abajo.\n"
                + "• Un error pone la imagen de abajo en rojo.\n"
                + "• Cada partida cuesta 10 de energía ⚡.\n", 15, false));
        root.addView(text("Energía de Bob: " + Math.round(pet.energy) + "/100\n"
                + "Tu récord: " + pet.bestMemory + " puntos\n", 15, true));

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
        tvDiffInfo.setText(DIFF_NAMES[difficulty] + ": " + PAIRS_BY[difficulty] + " parejas en "
                + MOVES_BY[difficulty] + " intentos.\nRecompensa por ganar: " + MULT_TEXT[difficulty]);
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
            ActionResult r = game.startMinigame(pet);   // valida y cobra la energia
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
        pairsTotal = PAIRS_BY[difficulty];
        maxMoves = MOVES_BY[difficulty];
        cols = COLS_BY[difficulty];
        int total = pairsTotal * 2;
        moves = 0; pairs = 0; first = -1; busy = false; ended = false;

        List<String> list = new ArrayList<>();
        for (int i = 0; i < pairsTotal; i++) { list.add(EMOJIS[i]); list.add(EMOJIS[i]); }
        Collections.shuffle(list);
        for (int i = 0; i < total; i++) { values[i] = list.get(i); matched[i] = false; }

        root.removeAllViews();     // aqui desaparece el menu de dificultades
        root.addView(text("🧠 Memoria - " + DIFF_NAMES[difficulty], 22, true));
        tvStatus = text("", 16, true);
        root.addView(tvStatus);
        updateStatus();

        int rows = total / cols;
        int cardHeight = total > 16 ? 76 : 88;
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                final int i = idx++;
                Button b = new Button(this);
                b.setText("❓");
                b.setTextSize(30);
                b.setMinWidth(0);
                b.setMinimumWidth(0);
                b.setMinHeight(0);
                b.setMinimumHeight(0);
                b.setPadding(0, 0, 0, 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(cardHeight), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                b.setLayoutParams(lp);
                b.setOnClickListener(v -> onCard(i));
                cards[i] = b;
                row.addView(b);
            }
            root.addView(row);
        }

        buildFeedbackBox("img_minijuego_memoria", "🧠");
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
        tvStatus.setText("Intentos: " + moves + "/" + maxMoves + "     Parejas: " + pairs + "/" + pairsTotal);
    }

    private void onCard(int i) {
        if (busy || ended || matched[i] || i == first) return;
        cards[i].setText(values[i]);
        if (first == -1) { first = i; return; }

        int a = first;
        first = -1;
        moves++;
        if (values[a].equals(values[i])) {
            matched[a] = matched[i] = true;
            cards[a].setEnabled(false);
            cards[i].setEnabled(false);
            pairs++;
            updateStatus();
            if (pairs == pairsTotal) endGame(true);
            else if (moves >= maxMoves) endGame(false);
        } else {
            busy = true;
            updateStatus();
            flashError();                                  // error: la imagen se pone roja
            handler.postDelayed(() -> {
                cards[a].setText("❓");
                cards[i].setText("❓");
                busy = false;
                if (moves >= maxMoves) endGame(false);
            }, 700);
        }
    }

    // ---------- Fin de la partida ----------
    private void endGame(boolean won) {
        if (ended) return;
        ended = true;
        final int movesLeft = Math.max(0, maxMoves - moves);
        final int score = won ? pairs + movesLeft : pairs;
        final int diff = difficulty;
        io.execute(() -> {
            pet = game.load();
            ActionResult r = game.finishMinigame(pet, "MEMORY", score, won, diff);
            pet = game.load();
            runOnUiThread(() -> showResult(won, score, r.message, diff));
        });
    }

    private void showResult(boolean won, int score, String msg, int diff) {
        handler.removeCallbacks(clearFlash);
        root.removeAllViews();
        root.addView(text(won ? "🎉 ¡Ganaste!" : "😢 Se acabaron los intentos", 24, true));
        root.addView(text("\nDificultad: " + DIFF_NAMES[diff] + "\nPuntaje: " + score
                + "\nRécord: " + pet.bestMemory + "\n" + msg
                + "\nMonedas: 💰 " + pet.coins, 17, false));

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
        MusicPlayer.play(this, R.raw.musica_memoria);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}