package com.cesba.retropet;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * Pone una imagen de fondo detras de una pantalla.
 * - wrap(): busca res/drawable/<nombre>. Si existe, la usa de fondo; si no, deja el color amarillo.
 * - panel(): fondo blanco semitransparente con esquinas redondeadas para que el texto se lea bien.
 */
public class BackgroundHelper {

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Envuelve el contenido (normalmente un ScrollView) con una imagen de fondo. */
    public static View wrap(Activity a, ViewGroup content, String drawableName) {
        FrameLayout frame = new FrameLayout(a);
        frame.setBackgroundColor(Color.parseColor("#FFF8C4"));
        FrameLayout.LayoutParams match = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        int res = a.getResources().getIdentifier(drawableName, "drawable", a.getPackageName());
        if (res != 0) {
            ImageView bg = new ImageView(a);
            bg.setImageResource(res);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);   // llena la pantalla sin deformar
            frame.addView(bg, match);

            View tint = new View(a);                             // velo suave para mejorar la lectura
            tint.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            frame.addView(tint, match);
        }

        content.setBackgroundColor(Color.TRANSPARENT);
        int m = dp(a, 10);
        content.setPadding(m, m, m, m);                          // margen para que se vea la imagen alrededor
        content.setClipToPadding(false);
        frame.addView(content, match);
        return frame;
    }

    /** Panel redondeado semitransparente para el contenido. */
    public static GradientDrawable panel(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor("#CCFFFFFF"));
        g.setCornerRadius(dp(c, 16));
        return g;
    }
}
