package com.cesba.retropet;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.cesba.retropet.data.AppDatabase;
import com.cesba.retropet.data.GameDao;
import com.cesba.retropet.logic.AuthManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pantalla de inicio: iniciar sesion o crear cuenta (local, sin internet). */
public class LoginActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private EditText etUser, etPass;
    private TextView tvMsg;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("retropet_prefs", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#FFF8C4"));
        int pad = dp(24);
        root.setPadding(pad, dp(48), pad, pad);

        TextView logo = new TextView(this);
        logo.setText("🧽");
        logo.setTextSize(80);
        logo.setGravity(android.view.Gravity.CENTER);
        root.addView(logo);

        TextView title = new TextView(this);
        title.setText("RetroPet 90");
        title.setTextSize(28);
        title.setTextColor(Color.BLACK);
        android.graphics.Typeface tf =
                androidx.core.content.res.ResourcesCompat.getFont(this, R.font.esponja_fuente);
        title.setTypeface(tf != null ? tf : title.getTypeface(), Typeface.BOLD);
        title.setGravity(android.view.Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Inicia sesión para guardar tu avance");
        sub.setGravity(android.view.Gravity.CENTER);
        sub.setTextColor(Color.DKGRAY);
        sub.setPadding(0, 0, 0, dp(24));
        root.addView(sub);

        etUser = new EditText(this);
        etUser.setHint("Usuario (mínimo 3 caracteres)");
        etUser.setSingleLine(true);
        etUser.setTextColor(Color.BLACK);
        etUser.setHintTextColor(Color.GRAY);
        etUser.setText(prefs.getString("last_user", ""));
        root.addView(etUser);

        etPass = new EditText(this);
        etPass.setHint("Contraseña (mínimo 4 caracteres)");
        etPass.setSingleLine(true);
        etPass.setTextColor(Color.BLACK);
        etPass.setHintTextColor(Color.GRAY);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(etPass);

        tvMsg = new TextView(this);
        tvMsg.setTextColor(Color.parseColor("#B00020"));
        tvMsg.setPadding(0, dp(8), 0, dp(8));
        root.addView(tvMsg);

        Button login = new Button(this);
        login.setText("Iniciar sesión");
        login.setOnClickListener(v -> submit(false));
        root.addView(login);

        Button register = new Button(this);
        register.setText("Crear cuenta nueva");
        register.setOnClickListener(v -> submit(true));
        root.addView(register);

        setContentView(root);
    }

    private void submit(boolean register) {
        final String user = etUser.getText().toString().trim();
        final String pass = etPass.getText().toString();
        if (user.length() < 3 || pass.length() < 4) {
            tvMsg.setText("Usuario: mínimo 3 caracteres. Contraseña: mínimo 4.");
            return;
        }
        io.execute(() -> {
            GameDao dao = AppDatabase.get(this).dao();
            AuthManager auth = new AuthManager(dao);
            long id = register ? auth.register(user, pass) : auth.login(user, pass);
            if (id < 0) {
                runOnUiThread(() -> tvMsg.setText(register
                        ? "Ese usuario ya existe. Elige otro nombre."
                        : "Usuario o contraseña incorrectos."));
                return;
            }
            prefs.edit().putString("last_user", user).apply();
            boolean hasPet = dao.getPet(id) != null;
            runOnUiThread(() -> {
                Intent i = new Intent(this, hasPet ? MainActivity.class : CharacterActivity.class);
                i.putExtra("uid", id);
                startActivity(i);
                finish();
            });
        });
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}