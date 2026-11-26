package com.cesba.retropet.logic;
import com.cesba.retropet.data.*;
import java.util.List;
import java.util.TimeZone;
public class MissionManager {
    public static long today(long now) { return (now + TimeZone.getDefault().getOffset(now)) / 86400000L; }

    private static void mk(GameDao d, long u, String type, String metric, String desc, int target, int coins, int xp, long day) {
        MissionEntity m = new MissionEntity();
        m.userId = u; m.type = type; m.metric = metric; m.description = desc; m.target = target;
        m.rewardCoins = coins; m.rewardXp = xp; m.lastResetDay = day;
        d.saveMission(m);
    }
    public static void seed(GameDao d, long u, long now) {
        long day = today(now);
        mk(d, u, "DAILY", "FEED",  "Alimenta a Bob 3 veces", 3, 15, 10, day);
        mk(d, u, "DAILY", "PLAY",  "Juega 2 veces", 2, 15, 10, day);
        mk(d, u, "DAILY", "CLEAN", "Banalo 1 vez", 1, 10, 8, day);
        mk(d, u, "ACHIEVEMENT", "LEVEL",  "Alcanza el nivel 4", 4, 50, 0, 0);
        mk(d, u, "ACHIEVEMENT", "WINS",   "Gana 5 minijuegos", 5, 40, 20, 0);
        mk(d, u, "ACHIEVEMENT", "EVOLVE", "Haz evolucionar a Bob", 1, 80, 0, 0);
    }
    public static void resetDaily(GameDao d, long u, long now) {
        long day = today(now);
        for (MissionEntity m : d.getMissions(u))
            if ("DAILY".equals(m.type) && m.lastResetDay != day) {
                m.progress = 0; m.claimed = false; m.lastResetDay = day; d.saveMission(m);
            }
    }
    /** Suma progreso (o fija valor absoluto, p. ej. nivel). Nunca excede la meta. */
    public static void track(GameDao d, long u, String metric, int value, boolean absolute) {
        List<MissionEntity> list = d.getMissions(u);
        for (MissionEntity m : list) {
            if (!m.metric.equals(metric) || m.claimed) continue;
            m.progress = Math.min(m.target, absolute ? Math.max(m.progress, value) : m.progress + value);
            d.saveMission(m);
        }
    }
}
