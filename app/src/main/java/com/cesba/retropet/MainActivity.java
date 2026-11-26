package com.cesba.retropet;

import android.content.Intent;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.data.InventoryEntity;
import com.cesba.retropet.data.PetEntity;
import com.cesba.retropet.logic.Catalog;
import com.cesba.retropet.logic.ActionResult;
import com.cesba.retropet.logic.EvolutionEngine;
import com.cesba.retropet.logic.GameEngine;
import com.cesba.retropet.logic.NeedsEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class MainActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GameEngine game;
    private PetEntity pet;
    private long uid;

    private TextView tvInfo, tvPet, tvState, tvMsg;
    private ImageView imgPet, imgRoom;
    private final ImageView[] decoImg = new ImageView[3];
    private final TextView[] decoText = new TextView[3];
    private static final String[] SLOTS = {"CEILING", "WALL", "FLOOR"};
    private final TextView[] labels = new TextView[5];
    private final ProgressBar[] bars = new ProgressBar[5];
    private final String[] NAMES = {"🍔 Alimentación", "😊 Felicidad", "⚡ Energía", "🧼 Higiene", "❤ Salud"};

    // Refresca cada 30 s mientras la app esta abierta
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 30_000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sin sesion valida: volver al login
        uid = getIntent().getLongExtra("uid", -1);
        if (uid < 0) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Fondo del menu (opcional): res/drawable/fondo_menu
        int menuBg = getResources().getIdentifier("fondo_menu", "drawable", getPackageName());
        if (menuBg != 0) {
            try {
                ((ImageView) findViewById(R.id.imgMenuBg)).setImageBitmap(decodeScaled(menuBg, 1080));
                findViewById(R.id.imgMenuBg).setVisibility(View.VISIBLE);
                findViewById(R.id.menuVeil).setVisibility(View.VISIBLE);
            } catch (Throwable ignored) {
                // Si la imagen falla, se queda el fondo amarillo y la app no se cierra
            }
        }

        tvInfo = findViewById(R.id.tvInfo);
        tvPet = findViewById(R.id.tvPet);
        imgPet = findViewById(R.id.imgPet);
        imgRoom = findViewById(R.id.imgRoom);
        decoImg[0] = findViewById(R.id.imgDecoCeiling); decoText[0] = findViewById(R.id.decoCeiling);
        decoImg[1] = findViewById(R.id.imgDecoWall);    decoText[1] = findViewById(R.id.decoWall);
        decoImg[2] = findViewById(R.id.imgDecoFloor);   decoText[2] = findViewById(R.id.decoFloor);
        tvState = findViewById(R.id.tvState);
        tvMsg = findViewById(R.id.tvMsg);
        buildBars();

        // Permiso de notificaciones (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        findViewById(R.id.btnFeed).setOnClickListener(v -> act(() -> game.feed(pet, "burger")));
        findViewById(R.id.btnPlay).setOnClickListener(v -> act(() -> game.play(pet)));
        findViewById(R.id.btnSleep).setOnClickListener(v -> act(() -> game.sleep(pet)));
        findViewById(R.id.btnClean).setOnClickListener(v -> act(() -> game.clean(pet, "soap")));
        findViewById(R.id.btnHeal).setOnClickListener(v -> act(() -> game.heal(pet, "medicine")));
        findViewById(R.id.btnTrain).setOnClickListener(v -> act(() -> game.train(pet)));
        findViewById(R.id.btnCook).setOnClickListener(v -> act(() -> game.cookBurger(pet)));
        findViewById(R.id.btnJelly).setOnClickListener(v -> act(() -> game.huntJellyfish(pet)));

        findViewById(R.id.btnShop).setOnClickListener(v -> openScreen(ShopActivity.class));
        findViewById(R.id.btnMissions).setOnClickListener(v -> openScreen(MissionsActivity.class));
        findViewById(R.id.btnMemory).setOnClickListener(v -> openScreen(MemoryGameActivity.class));
        findViewById(R.id.btnReflex).setOnClickListener(v -> openScreen(ReflexGameActivity.class));
        findViewById(R.id.btnRoom).setOnClickListener(v -> openScreen(RoomActivity.class));
        findViewById(R.id.btnSettings).setOnClickListener(v -> openScreen(SettingsActivity.class));

        io.execute(() -> {
            GameDao dao = AppDatabase.get(this).dao();
            game = new GameEngine(dao, uid);
            pet = game.load();
            if (pet == null) {
                // Cuenta sin partida: ir a elegir personaje
                runOnUiThread(() -> {
                    Intent c = new Intent(this, CharacterActivity.class);
                    c.putExtra("uid", uid);
                    startActivity(c);
                    finish();
                });
                return;
            }
            runOnUiThread(this::render);
        });
    }

    /** Abre otra pantalla pasandole el usuario actual. */
    private void openScreen(Class<?> target) {
        Intent i = new Intent(this, target);
        i.putExtra("uid", uid);
        startActivity(i);
    }

    @Override protected void onResume() {
        super.onResume();
        MusicPlayer.play(this, R.raw.musica_menu);
        AlertScheduler.cancelAll(this);          // la app esta abierta: no hacen falta alertas
        handler.post(ticker);
    }

    @Override protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
        handler.removeCallbacks(ticker);
        final android.content.Context app = getApplicationContext();
        if (game != null) {
            io.execute(() -> AlertScheduler.schedule(app, game.load()));   // programa las alertas al salir
        }
    }

    /** Carga una imagen reducida para no agotar la memoria con fotos muy grandes. */
    private android.graphics.Bitmap decodeScaled(int resId, int reqWidth) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(getResources(), resId, o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= reqWidth) sample *= 2;
        o = new android.graphics.BitmapFactory.Options();
        o.inSampleSize = sample;
        return android.graphics.BitmapFactory.decodeResource(getResources(), resId, o);
    }

    private void buildBars() {
        LinearLayout box = findViewById(R.id.barsContainer);

        // Un color brillante por cada barra: alimentación, felicidad, energía, higiene y salud
        int[] barColors = {
                Color.parseColor("#FF6D00"),   // naranja
                Color.parseColor("#FFD600"),   // amarillo
                Color.parseColor("#2979FF"),   // azul
                Color.parseColor("#18FFFF"),   // cian
                Color.parseColor("#FF1744")    // rojo
        };

        for (int i = 0; i < 5; i++) {
            labels[i] = new TextView(this);
            labels[i].setTextSize(14);
            labels[i].setTextColor(Color.parseColor("#000000"));   // <-- color del texto sobre cada barra

            bars[i] = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bars[i].setMax(100);
            bars[i].setProgressTintList(android.content.res.ColorStateList.valueOf(barColors[i]));              // relleno brillante
            bars[i].setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#BDBDBD")));  // fondo gris claro
            bars[i].setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addView(labels[i]);
            box.addView(bars[i]);
        }

    }

    /** Ejecuta una accion del juego en segundo plano, muestra el mensaje y refresca. */
    private void act(Supplier<ActionResult> action) {
        if (game == null || pet == null) return;
        io.execute(() -> {
            ActionResult r = action.get();
            pet = game.load();
            runOnUiThread(() -> {
                tvMsg.setTextColor(r.ok ? Color.parseColor("#1B5E20") : Color.parseColor("#B00020"));
                tvMsg.setText(r.message);
                render();
                animatePet(r.ok);
            });
        });
    }

    private void refresh() {
        if (game == null) return;
        io.execute(() -> { pet = game.load(); runOnUiThread(this::render); });
    }

    private void render() {
        if (pet == null) return;
        long now = System.currentTimeMillis();
        String state = NeedsEngine.stateOf(pet, now);

        // Emoji de respaldo (se usa si no hay imagen en res/drawable)
        String normalEmoji = "EVO_A".equals(pet.stage) ? "👨‍🍳" : "EVO_B".equals(pet.stage) ? "🏄" : "🧽";
        String emoji;
        switch (state) {
            case "DORMIDO": emoji = "😴"; break;
            case "ENFERMO": emoji = "🤒"; break;
            case "TRISTE":  emoji = "😢"; break;
            case "FELIZ":   emoji = "🤩"; break;
            default:        emoji = normalEmoji;
        }

        // Imagen: bob_<etapa>_<estado>, por ejemplo bob_base_feliz o bob_evo_a_normal
        String base = "bob_base_";
        int res = getResources().getIdentifier(base + state.toLowerCase(), "drawable", getPackageName());
        if (res == 0) res = getResources().getIdentifier(base + "normal", "drawable", getPackageName());
        if (res != 0) {
            imgPet.setImageResource(res);
            imgPet.setVisibility(View.VISIBLE);
            tvPet.setVisibility(View.GONE);
        } else {
            imgPet.setVisibility(View.GONE);
            tvPet.setVisibility(View.VISIBLE);
            tvPet.setText(emoji);
        }

        tvState.setText(pet.name + " (" + EvolutionEngine.stageName(pet.stage) + ") - " + state
                + "\n" + stateReason(state));
        tvInfo.setText("Nivel " + pet.level + "  |  XP " + pet.xp + "  |  💰 " + pet.coins);

        float[] v = {pet.food, pet.happiness, pet.energy, pet.hygiene, pet.health};
        for (int i = 0; i < 5; i++) {
            labels[i].setText(NAMES[i] + ": " + Math.round(v[i]) + "/100");
            bars[i].setProgress(Math.round(v[i]));
        }

        renderRoom(state);
        checkEvolutionEvent();
    }

    private String stateReason(String state) {
        switch (state) {
            case "DORMIDO": return "Está descansando para recuperar energía 💤";
            case "ENFERMO": return "Su salud está baja. Dale medicina y cuida su comida e higiene.";
            case "TRISTE":  return pet.food < 25 ? "Tiene mucha hambre. ¡Aliméntalo!" : "Se siente solo. ¡Juega con él!";
            case "FELIZ":   return "¡Está muy contento porque lo cuidas bien!";
            default:        return pet.energy < 25 ? "Está cansado. Déjalo dormir." : "Todo está bien por ahora.";
        }
    }

    // ---------- Cuarto con decoraciones ----------
    private void renderRoom(String state) {
        // Fondo: cuarto normal, o cuarto con cama si duerme (si existe esa imagen)
        int res = 0;
        if ("DORMIDO".equals(state)) res = getResources().getIdentifier("cuarto_dormido", "drawable", getPackageName());
        if (res == 0) res = getResources().getIdentifier("fondo_cuarto", "drawable", getPackageName());
        if (res != 0) {
            imgRoom.setImageResource(res);
            imgRoom.setVisibility(View.VISIBLE);
        } else {
            imgRoom.setVisibility(View.GONE);
        }

        // Decoraciones equipadas (se leen de la base de datos en segundo plano)
        io.execute(() -> {
            GameDao dao = AppDatabase.get(this).dao();
            List<InventoryEntity> inv = dao.getInventory(uid);
            Map<String, Catalog.ItemDef> equipped = new HashMap<>();
            for (InventoryEntity e : inv) {
                Catalog.ItemDef d = Catalog.get(e.itemId);
                if (d != null && "DECOR".equals(d.type) && e.equipped) equipped.put(d.slot, d);
            }
            runOnUiThread(() -> updateDecor(equipped));
        });
    }

    private void updateDecor(Map<String, Catalog.ItemDef> equipped) {
        for (int i = 0; i < 3; i++) {
            Catalog.ItemDef d = equipped.get(SLOTS[i]);
            if (d == null) {
                decoImg[i].setVisibility(View.GONE);
                decoText[i].setVisibility(View.GONE);
                continue;
            }
            int r = getResources().getIdentifier("deco_" + d.id, "drawable", getPackageName());
            if (r != 0) {                                   // imagen: res/drawable/deco_<id>
                decoImg[i].setImageResource(r);
                decoImg[i].setVisibility(View.VISIBLE);
                decoText[i].setVisibility(View.GONE);
            } else {                                        // respaldo: emoji
                decoText[i].setText(d.emoji);
                decoText[i].setVisibility(View.VISIBLE);
                decoImg[i].setVisibility(View.GONE);
            }
        }
    }

    // ---------- Animaciones ----------
    private View petView() { return imgPet.getVisibility() == View.VISIBLE ? imgPet : tvPet; }

    /** Rebote si la accion salio bien, sacudida si fallo. */
    private void animatePet(boolean ok) {
        View v = petView();
        if (ok) {
            ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, 0f, -70f, 0f).setDuration(450).start();
        } else {
            ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -25f, 25f, -25f, 25f, 0f).setDuration(400).start();
        }
    }

    /** Evento de evolucion: se muestra una sola vez por cada cambio de etapa. */
    private void checkEvolutionEvent() {
        SharedPreferences prefs = getSharedPreferences("retropet_prefs", MODE_PRIVATE);
        String key = "seen_stage_" + uid;
        String seen = prefs.getString(key, null);
        if (seen == null) {
            prefs.edit().putString(key, pet.stage).apply();
        } else if (!seen.equals(pet.stage)) {
            prefs.edit().putString(key, pet.stage).apply();
            if (!"BASE".equals(pet.stage)) playEvolution(pet.stage);
        }
    }

    private void playEvolution(String stage) {
        View v = petView();
        PropertyValuesHolder sx = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.7f, 1f);
        PropertyValuesHolder sy = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.7f, 1f);
        PropertyValuesHolder rot = PropertyValuesHolder.ofFloat(View.ROTATION_Y, 0f, 720f);
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v, sx, sy, rot);
        a.setDuration(1500);
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { showEvolutionDialog(stage); }
        });
        a.start();
    }

    private void showEvolutionDialog(String stage) {
        if (isFinishing()) return;
        String adv = "EVO_A".equals(stage)
                ? "Ventaja: cocina 2 Cangreburgers a la vez y su hambre e higiene bajan 30% más lento."
                : "Ventaja: gana 25% más monedas en los minijuegos y su energía baja 30% más lento.";
        new AlertDialog.Builder(this)
                .setTitle("✨ ¡Evolución!")
                .setMessage(pet.name + " ahora es " + EvolutionEngine.stageName(stage) + ".\n\n" + adv)
                .setPositiveButton("¡Genial!", null)
                .show();
    }
}