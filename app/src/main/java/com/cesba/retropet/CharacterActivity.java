package com.cesba.retropet;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.logic.GameEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Seleccion de personaje + nombre. Se muestra solo cuando la cuenta no tiene partida. */
public class CharacterActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long uid;
    private EditText etName;
    private TextView tvMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = getIntent().getLongExtra("uid", -1);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#FFF8C4"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, dp(32), pad, pad);
        scroll.addView(root);

        root.addView(text("Elige a tu personaje", 24, true, Gravity.CENTER));

        TextView face = text("🧽", 96, false, Gravity.CENTER);
        root.addView(face);
        root.addView(text("Bob Esponja", 22, true, Gravity.CENTER));
        root.addView(text("\nUna esponja alegre y trabajadora del Crustáceo Cascarudo. "
                + "Le encanta cocinar Cangreburgers y cazar medusas en Fondo de Bikini.\n", 15, false, Gravity.CENTER));

        root.addView(text("Configuración inicial:\n"
                + "• Alimentación, felicidad, energía e higiene: 80/100\n"
                + "• Salud: 100/100\n"
                + "• Monedas: 50 💰\n"
                + "• Regalo: 2 Cangreburgers, 1 jabón y 1 medicina\n\n"
                + "Mecánicas propias:\n"
                + "• 🍔 Cocinar Cangreburgers\n"
                + "• 🪼 Cazar medusas\n", 14, false, Gravity.START));

        etName = new EditText(this);
        etName.setHint("Nombre de tu mascota");
        etName.setSingleLine(true);
        etName.setText("Bob");
        root.addView(etName);

        tvMsg = text("", 14, true, Gravity.START);
        tvMsg.setTextColor(Color.parseColor("#B00020"));
        root.addView(tvMsg);

        Button start = new Button(this);
        start.setText("¡Comenzar aventura!");
        start.setOnClickListener(v -> startGame());
        root.addView(start);

        setContentView(scroll);
    }

    private void startGame() {
        final String name = etName.getText().toString().trim();
        if (name.isEmpty()) { tvMsg.setText("Escribe un nombre para tu mascota."); return; }
        if (name.length() > 12) { tvMsg.setText("El nombre puede tener máximo 12 letras."); return; }
        io.execute(() -> {
            GameDao dao = AppDatabase.get(this).dao();
            new GameEngine(dao, uid).newGame(name);
            runOnUiThread(() -> {
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra("uid", uid);
                startActivity(i);
                finish();
            });
        });
    }

    private TextView text(String s, int sp, boolean bold, int gravity) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        t.setGravity(gravity);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}