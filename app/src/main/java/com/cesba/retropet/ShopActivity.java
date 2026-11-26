package com.cesba.retropet;

import android.graphics.Color;
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
import com.cesba.retropet.logic.GameEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Tienda + inventario. Recibe "uid" por Intent. */
public class ShopActivity extends AppCompatActivity {

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
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(BackgroundHelper.wrap(this, scroll, "fondo_tienda"));

        io.execute(() -> {
            dao = AppDatabase.get(this).dao();
            game = new GameEngine(dao, uid);
            pet = game.load();
            if (pet == null) { runOnUiThread(this::finish); return; }
            loadAndRender();
        });
    }

    /** Lee el inventario en segundo plano y dibuja la pantalla. */
    private void loadAndRender() {
        List<InventoryEntity> inv = dao.getInventory(uid);
        Map<String, InventoryEntity> map = new HashMap<>();
        for (InventoryEntity e : inv) map.put(e.itemId, e);
        runOnUiThread(() -> render(map));
    }

    private void render(Map<String, InventoryEntity> inv) {
        root.removeAllViews();

        Button back = new Button(this);
        back.setText("← Volver");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        root.addView(text("🛒 Tienda    💰 " + pet.coins + "   (Nivel " + pet.level + ")", 20, true));
        TextView msg = text(lastMsg, 14, true);
        msg.setTextColor(lastOk ? Color.parseColor("#1B5E20") : Color.parseColor("#B00020"));
        root.addView(msg);

        section("🍔 Alimentos", "FOOD", inv);
        section("🧼 Cuidado", "CARE", inv);
        section("🏠 Decoración", "DECOR", inv);
    }

    private void section(String title, String type, Map<String, InventoryEntity> inv) {
        TextView h = text(title, 18, true);
        h.setPadding(0, dp(16), 0, dp(4));
        root.addView(h);

        for (Catalog.ItemDef d : Catalog.ALL) {
            if (!d.type.equals(type)) continue;
            InventoryEntity e = inv.get(d.id);
            int qty = e == null ? 0 : e.quantity;
            boolean equipped = e != null && e.equipped;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            android.graphics.drawable.GradientDrawable card = new android.graphics.drawable.GradientDrawable();
            card.setColor(Color.parseColor("#B3FFFFFF"));   // blanco con ~70% de opacidad
            card.setCornerRadius(dp(12));
            row.setBackground(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(4), 0, dp(4));
            row.setLayoutParams(lp);

            String info = d.emoji + " " + d.name + "  -  💰 " + d.price;
            if ("DECOR".equals(type)) info += "  (" + d.slot + ")" + (qty > 0 ? "  ✔ Comprado" : "");
            else info += "  (Tienes: " + qty + ")";
            row.addView(text(info, 16, true));
            if ("DECOR".equals(type)) {
                int r = getResources().getIdentifier("deco_" + d.id, "drawable", getPackageName());
                if (r != 0) {
                    android.widget.ImageView iv = new android.widget.ImageView(this);
                    iv.setImageResource(r);
                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    row.addView(iv, new LinearLayout.LayoutParams(dp(72), dp(72)));
                }
            }

            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(Gravity.END);

            Button buy = new Button(this);
            if (pet.level < d.minLevel) {
                buy.setText("🔒 Nivel " + d.minLevel);
                buy.setEnabled(false);
            } else if ("DECOR".equals(type) && qty > 0) {
                buy.setText("Comprado");
                buy.setEnabled(false);
            } else {
                buy.setText("Comprar");
                buy.setOnClickListener(v -> run(() -> game.buy(pet, d.id)));
            }
            buttons.addView(buy);

            if ("DECOR".equals(type) && qty > 0) {
                Button eq = new Button(this);
                if (equipped) {
                    eq.setText("Quitar");
                    eq.setOnClickListener(v -> run(() -> game.unequip(d.id)));
                } else {
                    eq.setText("Equipar");
                    eq.setOnClickListener(v -> run(() -> game.equip(d.id)));
                }
                buttons.addView(eq);
            }
            row.addView(buttons);
            root.addView(row);
        }
    }

    private void run(Supplier<ActionResult> action) {
        io.execute(() -> {
            pet = game.load();
            ActionResult r = action.get();
            lastMsg = r.message;
            lastOk = r.ok;
            pet = game.load();
            if (pet == null) { runOnUiThread(this::finish); return; }
            loadAndRender();
        });
    }

    private String effects(Catalog.ItemDef d) {
        StringBuilder sb = new StringBuilder();
        if (d.food != 0) sb.append("Alimentación ").append(sign(d.food)).append("  ");
        if (d.hap != 0) sb.append("Felicidad ").append(sign(d.hap)).append("  ");
        if (d.energy != 0) sb.append("Energía ").append(sign(d.energy)).append("  ");
        if (d.hyg != 0) sb.append("Higiene ").append(sign(d.hyg)).append("  ");
        if (d.health != 0) sb.append("Salud ").append(sign(d.health)).append("  ");
        return sb.toString();
    }

    private String sign(int v) { return v > 0 ? "+" + v : String.valueOf(v); }

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

    @Override
    protected void onResume() {
        super.onResume();
        MusicPlayer.play(this, R.raw.musica_tienda);   // <-- canción de la tienda
        if (game != null) io.execute(() -> { pet = game.load(); loadAndRender(); });
    }

    @Override
    protected void onPause() {
        super.onPause();
        MusicPlayer.pause();
    }
}
