package com.cesba.retropet.logic;
import com.cesba.retropet.data.PetEntity;
/**
 * Deterioro en tiempo real. Se calcula en pasos de 10 min (tambien al reabrir la app).
 * Tasas por hora (documentadas):
 *   alimentacion -6 | higiene -3 | felicidad -4 (despierto) / -1 (dormido)
 *   energia -3 (despierto) / +120 (dormido)   -> 30 min de sueño = +60 de energia
 *   salud -5 si alimentacion<20 o higiene<20 (-3 extra si alimentacion=0); +1 si todo esta bien
 * Evolucion A (Chef): alimentacion e higiene bajan 30% mas lento. Evolucion B: energia baja 30% mas lento.
 * Tope de simulacion offline: 72 h.
 * Dormir es gratis, asi que Bob nunca se queda sin forma de recuperar energia.
 */
public class NeedsEngine {
    public static final long STEP = 10L * 60 * 1000;
    public static final long MAX_OFFLINE = 72L * 60 * 60 * 1000;
    public static final float SLEEP_ENERGY_PER_HOUR = 120f;

    public static float c(float v) { return Math.max(0f, Math.min(100f, v)); }

    public static void update(PetEntity p, long now) {
        long elapsed = now - p.lastUpdate;
        if (elapsed < 0) { p.lastUpdate = now; return; }       // reloj cambiado
        boolean capped = elapsed > MAX_OFFLINE;
        long eff = Math.min(elapsed, MAX_OFFLINE);
        int steps = (int) (eff / STEP);
        if (steps == 0) return;
        long start = capped ? now - eff : p.lastUpdate;
        float h = 1f / 6f;
        float fm = "EVO_A".equals(p.stage) ? 0.7f : 1f;
        float em = "EVO_B".equals(p.stage) ? 0.7f : 1f;
        for (int i = 0; i < steps; i++) {
            long stepStart = start + i * STEP;
            // Fraccion del paso (0..1) que Bob paso dormido
            float sf = Math.max(0f, Math.min(1f, (p.sleepUntil - stepStart) / (float) STEP));

            p.food -= 6 * h * fm;
            p.hygiene -= 3 * h * fm;
            p.energy += SLEEP_ENERGY_PER_HOUR * h * sf - 3 * h * em * (1 - sf);
            p.happiness -= (1 * sf + 4 * (1 - sf)) * h;

            if (p.food < 20 || p.hygiene < 20) p.health -= 5 * h;
            else if (p.food > 60 && p.hygiene > 60 && p.happiness > 40) p.health += 1 * h;
            if (p.food <= 0) p.health -= 3 * h;
            clampAll(p);
            p.wellbeingSum += (p.food + p.happiness + p.energy + p.hygiene + p.health) / 5f;
            p.wellbeingSamples++;
        }
        p.lastUpdate = capped ? now : p.lastUpdate + steps * STEP;
    }

    public static void clampAll(PetEntity p) {
        p.food = c(p.food); p.happiness = c(p.happiness); p.energy = c(p.energy);
        p.hygiene = c(p.hygiene); p.health = c(p.health);
    }

    /** 5 estados visuales: DORMIDO, ENFERMO, TRISTE, FELIZ, NORMAL. */
    public static String stateOf(PetEntity p, long now) {
        if (now < p.sleepUntil) return "DORMIDO";
        if (p.health < 40) return "ENFERMO";
        if (p.happiness < 35 || p.food < 25) return "TRISTE";
        if (p.happiness >= 75 && p.food >= 50 && p.hygiene >= 50) return "FELIZ";
        return "NORMAL";
    }
}