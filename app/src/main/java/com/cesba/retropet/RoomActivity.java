package com.cesba.retropet;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;

import androidx.appcompat.app.AppCompatActivity;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.data.InventoryEntity;
import com.cesba.retropet.data.PetEntity;
import com.cesba.retropet.logic.ActionResult;
import com.cesba.retropet.logic.Catalog;
import com.cesba.retropet.logic.EvolutionEngine;
import com.cesba.retropet.logic.GameEngine;
import com.cesba.retropet.logic.NeedsEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Habitacion de Bob: 3 posiciones (techo, pared, piso). Recibe "uid" por Intent. */
public class RoomActivity extends AppCompatActivity {

    private static final String[] SLOTS = {"CEILING", "WALL", "FLOOR"};

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
        scroll.setBackgroundColor(Color.parseColor("#FFF8C4"));
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
        MusicPlayer.play(this, R.raw.musica_cuarto);   // <-- canción del cuarto
        if (game != null) io.execute(this::reload);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
    }

    private void reload() {
        pet = game.load();
        if (pet == null) { runOnUiThread(this::finish); return; }
        List<InventoryEntity> inv = dao.getInventory(uid);
        runOnUiThread(() -> render(inv));
    }

    private void render(List<InventoryEntity> inv) {
        root.removeAllViews();

        Button back = new Button(this);
        back.setText("← Volver");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        root.addView(text("🏠 Habitación de " + pet.name, 22, true));
        TextView msg = text(lastMsg, 14, true);
        msg.setTextColor(lastOk ? Color.parseColor("#1B5E20") : Color.parseColor("#B00020"));
        root.addView(msg);

        // Objetos equipados y poseidos por posicion
        Map<String, Catalog.ItemDef> equipped = new HashMap<>();
        for (InventoryEntity e : inv) {
            Catalog.ItemDef d = Catalog.get(e.itemId);
            if (d != null && "DECOR".equals(d.type) && e.equipped) equipped.put(d.slot, d);
        }

        // ---------- ESCENA ----------
        String state = NeedsEngine.stateOf(pet, System.currentTimeMillis());
        root.addView(band("#B3E5FC", 90, itemView(equipped.get("CEILING"), "techo")));

        LinearLayout wall = band("#FFB74D", 170, null);
        wall.setOrientation(LinearLayout.HORIZONTAL);
        wall.addView(weighted(itemView(equipped.get("WALL"), "pared")));
        wall.addView(weighted(petView(state)));
        root.addView(wall);

        root.addView(band("#8D6E63", 90, itemView(equipped.get("FLOOR"), "piso")));

        // ---------- CONTROLES ----------
        for (String slot : SLOTS) {
            root.addView(title(slotName(slot)));
            boolean any = false;
            for (InventoryEntity e : inv) {
                Catalog.ItemDef d = Catalog.get(e.itemId);
                if (d == null || !"DECOR".equals(d.type) || !slot.equals(d.slot) || e.quantity <= 0) continue;
                any = true;
                Button b = new Button(this);
                if (e.equipped) {
                    b.setText("✔ " + d.emoji + " " + d.name + " (equipado)");
                    b.setEnabled(false);
                } else {
                    b.setText("Equipar " + d.emoji + " " + d.name);
                    b.setOnClickListener(v -> run(() -> game.equip(d.id)));
                }
                root.addView(b);
                if (e.equipped) {
                    Button q = new Button(this);
                    q.setText("Quitar");
                    q.setOnClickListener(v -> run(() -> game.unequip(d.id)));
                    root.addView(q);
                }
            }
            if (!any) root.addView(text("No tienes decoraciones para esta posición. ¡Compra en la tienda 🛒!", 14, false));
        }
    }

    private void run(Supplier<ActionResult> action) {
        io.execute(() -> {
            ActionResult r = action.get();
            lastMsg = r.message;
            lastOk = r.ok;
            reload();
        });
    }

    // ---------- Piezas visuales ----------
    private LinearLayout band(String color, int heightDp, android.view.View content) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(Color.parseColor(color));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        if (content != null) b.addView(content);
        return b;
    }

    private android.view.View weighted(android.view.View v) {
        v.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        return v;
    }

    private android.view.View itemView(Catalog.ItemDef d, String slotLabel) {
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);

        // Si existe la imagen deco_<id>, se usa; si no, el emoji
        if (d != null) {
            int r = getResources().getIdentifier("deco_" + d.id, "drawable", getPackageName());
            if (r != 0) {
                int size = "WALL".equals(d.slot) ? 130 : 80;
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageResource(r);
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                box.addView(iv, new android.widget.FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER));
                return box;
            }
        }

        TextView t = new TextView(this);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.BLACK);
        if (d == null) {
            t.setText("(" + slotLabel + " vacío)");
            t.setTextSize(13);
        } else {
            t.setText(d.emoji + "\n" + d.name);
            t.setTextSize(36);
        }
        box.addView(t, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return box;
    }

    private TextView petView(String state) {
        String emoji;
        switch (state) {
            case "DORMIDO": emoji = "😴"; break;
            case "ENFERMO": emoji = "🤒"; break;
            case "TRISTE":  emoji = "😢"; break;
            case "FELIZ":   emoji = "🤩"; break;
            default:        emoji = "🧽";
        }
        TextView t = new TextView(this);
        t.setText(emoji + "\n" + EvolutionEngine.stageName(pet.stage));
        t.setTextSize(48);
        t.setTextColor(Color.BLACK);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private String slotName(String slot) {
        switch (slot) {
            case "CEILING": return "⬆ Techo";
            case "WALL":    return "▮ Pared";
            default:        return "⬇ Piso";
        }
    }

    private TextView title(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        android.graphics.Typeface tf =
                androidx.core.content.res.ResourcesCompat.getFont(this, R.font.esponja_fuente);
        if (tf != null) t.setTypeface(tf, android.graphics.Typeface.BOLD);
        t.setTextColor(android.graphics.Color.BLACK);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
