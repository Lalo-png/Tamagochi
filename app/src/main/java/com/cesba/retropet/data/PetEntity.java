package com.cesba.retropet.data;
import androidx.room.*;
/** Personaje + estadisticas + progreso. Una fila por usuario. */
@Entity(tableName = "pet")
public class PetEntity {
    @PrimaryKey public long userId;
    public String name;
    public String character = "Bob Esponja";
    public String stage = "BASE";            // BASE, EVO_A (Chef Estrella), EVO_B (Aventurero)
    // Necesidades 0..100
    public float food = 80, happiness = 80, energy = 80, hygiene = 80, health = 100;
    public long lastUpdate;                  // ultima actualizacion (ms)
    public long sleepUntil;                  // fin del descanso (ms)
    public int coins = 50, xp = 0, level = 1;
    // Historial para evolucion
    public float wellbeingSum; public int wellbeingSamples;
    public int feedCount, careCount, playCount, trainCount, minigameWins;
    // Records
    public int bestMemory, bestReflex;
    // Cooldowns de mecanicas
    public long lastTrain, lastCook, lastJelly;
}
