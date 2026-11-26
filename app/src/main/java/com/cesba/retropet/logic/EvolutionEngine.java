package com.cesba.retropet.logic;
import com.cesba.retropet.data.PetEntity;
/**
 * REGLA MEDIBLE (nivel 5):
 *   cuidado  = veces alimentado + veces curado/banado
 *   actividad = veces jugado + entrenado + minijuegos ganados
 *   cuidado >= actividad  -> EVO_A "Bob Chef Estrella"  (ventaja: x2 Cangreburgers, deterioro de hambre/higiene -30%)
 *   si no                 -> EVO_B "Bob Aventurero"     (ventaja: +25% monedas en minijuegos, energia -30% mas lenta)
 */
public class EvolutionEngine {
    public static final int EVOLVE_LEVEL = 5;
    public static String decide(PetEntity p) {
        int care = p.feedCount + p.careCount;
        int activity = p.playCount + p.trainCount + p.minigameWins;
        return care >= activity ? "EVO_A" : "EVO_B";
    }
    public static String stageName(String stage) {
        switch (stage) {
            case "EVO_A": return "Bob Chef Estrella";
            case "EVO_B": return "Bob Aventurero";
            default: return "Bob Esponja";
        }
    }
}
