package com.cesba.retropet.data;
import androidx.room.*;
@Entity(tableName = "missions")
public class MissionEntity {
    @PrimaryKey(autoGenerate = true) public long id;
    public long userId;
    public String type;        // DAILY o ACHIEVEMENT
    public String metric;      // FEED, PLAY, CLEAN, HEAL, TRAIN, LEVEL, WINS, EVOLVE
    public String description;
    public int target, progress, rewardCoins, rewardXp;
    public boolean claimed;
    public long lastResetDay;
    public boolean isComplete() { return progress >= target; }
}
