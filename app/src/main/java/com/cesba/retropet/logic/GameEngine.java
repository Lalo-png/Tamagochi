package com.cesba.retropet.logic;
import com.cesba.retropet.data.*;
import java.util.Random;
/**
 * Todas las acciones del juego. Cada metodo: actualiza el tiempo, valida, aplica efectos, da XP,
 * avanza misiones y GUARDA. Llamar desde un hilo secundario (Executor), no desde el hilo de UI.
 */
public class GameEngine {
    private static final int[] LEVEL_XP = {0, 5, 10, 15, 20, 30};
    private final GameDao dao; private final long uid; private final Random rnd = new Random();
    public GameEngine(GameDao dao, long uid) { this.dao = dao; this.uid = uid; }

    // ---------- Partida ----------
    public PetEntity load() {
        PetEntity p = dao.getPet(uid);
        if (p == null) return null;
        long now = System.currentTimeMillis();
        NeedsEngine.update(p, now);                 // aplica el tiempo transcurrido sin pulsar nada
        MissionManager.resetDaily(dao, uid, now);
        dao.savePet(p);
        return p;
    }
    public PetEntity newGame(String name) {
        long now = System.currentTimeMillis();
        PetEntity p = new PetEntity();
        p.userId = uid; p.name = (name == null || name.trim().isEmpty()) ? "Bob" : name.trim();
        p.lastUpdate = now;
        dao.savePet(p);
        addItem("burger", 2); addItem("soap", 1); addItem("medicine", 1);
        MissionManager.seed(dao, uid, now);
        return p;
    }
    public void resetGame() { dao.deletePet(uid); dao.deleteInventory(uid); dao.deleteMissions(uid); } // UI: pedir confirmacion

    // ---------- Inventario ----------
    private void addItem(String id, int q) {
        InventoryEntity e = dao.getInv(uid, id);
        if (e == null) { e = new InventoryEntity(); e.userId = uid; e.itemId = id; e.slot = Catalog.get(id).slot; }
        e.quantity += q; dao.saveInv(e);
    }
    private boolean consume(String id) {
        InventoryEntity e = dao.getInv(uid, id);
        if (e == null || e.quantity <= 0) return false;
        e.quantity--; dao.saveInv(e); return true;
    }
    private boolean asleep(PetEntity p, long now) { return now < p.sleepUntil; }

    // ---------- XP / nivel / evolucion ----------
    private String gainXp(PetEntity p, int xp) {
        p.xp += xp; String msg = "";
        int lvl = 1;
        for (int i = 0; i < LEVEL_XP.length; i++) if (p.xp >= LEVEL_XP[i]) lvl = i + 1;
        if (lvl > p.level) {
            p.level = lvl; msg += " ¡Nivel " + lvl + "! (revisa la tienda: hay articulos nuevos)";
            MissionManager.track(dao, uid, "LEVEL", p.level, true);
            if (p.level >= EvolutionEngine.EVOLVE_LEVEL && "BASE".equals(p.stage)) {
                p.stage = EvolutionEngine.decide(p);
                msg += " ¡EVOLUCION: " + EvolutionEngine.stageName(p.stage) + "!";
                MissionManager.track(dao, uid, "EVOLVE", 1, false);
            }
        }
        return msg;
    }
    public static int xpForNextLevel(PetEntity p) { return p.level >= LEVEL_XP.length ? -1 : LEVEL_XP[p.level]; }

    private ActionResult done(PetEntity p, String msg, int xp) {
        String extra = gainXp(p, xp);
        NeedsEngine.clampAll(p); dao.savePet(p);
        return ActionResult.ok(msg + extra);
    }

    // ---------- Cuidados ----------
    private ActionResult useItem(PetEntity p, String itemId, String type, String metric) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob esta dormido 💤");
        Catalog.ItemDef d = Catalog.get(itemId);
        if (d == null || !d.type.equals(type)) return ActionResult.fail("Ese articulo no sirve para esto");
        if ("FEED".equals(metric) && p.food >= 95 && p.energy >= 90) return ActionResult.fail("Bob esta lleno");
        if ("CLEAN".equals(metric) && p.hygiene >= 95) return ActionResult.fail("Bob ya esta limpio");
        if ("HEAL".equals(metric) && p.health >= 95) return ActionResult.fail("Bob esta sano");
        if (!consume(itemId)) return ActionResult.fail("No tienes " + d.name + ". ¡Compra en la tienda!");
        p.food += d.food; p.happiness += d.hap; p.energy += d.energy; p.hygiene += d.hyg; p.health += d.health;
        if ("FEED".equals(metric)) p.feedCount++; else p.careCount++;
        MissionManager.track(dao, uid, metric, 1, false);
        String m = "FEED".equals(metric) ? "¡Ñam! Bob disfruto " + d.name
                 : "CLEAN".equals(metric) ? "¡Bob esta reluciente! ✨" : "Bob se siente mejor ❤";
        return done(p, m, 8);
    }
    public ActionResult feed(PetEntity p, String itemId)  { return useItem(p, itemId, "FOOD", "FEED"); }
    public ActionResult clean(PetEntity p, String itemId) { return useItem(p, itemId, "CARE", "CLEAN"); }
    public ActionResult heal(PetEntity p, String itemId)  { return useItem(p, itemId, "CARE", "HEAL"); }

    public ActionResult play(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob esta dormido 💤");
        if (p.energy < 15) return ActionResult.fail("Bob esta muy cansado para jugar");
        p.energy -= 15; p.happiness += 15; p.food -= 5; p.playCount++;
        MissionManager.track(dao, uid, "PLAY", 1, false);
        return done(p, "¡Bob juega a atrapar medusas! 🎉", 6);
    }
    public ActionResult train(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob esta dormido 💤");
        if (now - p.lastTrain < 60_000) return ActionResult.fail("Espera " + (60 - (now - p.lastTrain) / 1000) + " s para volver a entrenar");
        if (p.energy < 20 || p.food < 20) return ActionResult.fail("Bob necesita energia (20) y comida (20)");
        p.lastTrain = now; p.energy -= 20; p.food -= 8; p.hygiene -= 8; p.happiness += 4; p.health += 2; p.trainCount++;
        MissionManager.track(dao, uid, "TRAIN", 1, false);
        return done(p, "Bob practico karate con Sandy 🥋", 10);
    }
    /** Descanso de 30 min. La UI debe programar la notificacion "Bob desperto" a sleepUntil. */
    public ActionResult sleep(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob ya esta durmiendo");
        if (p.energy >= 90) return ActionResult.fail("Bob no tiene sueño");
        p.sleepUntil = now + 30L * 60 * 1000; dao.savePet(p);
        return ActionResult.ok("Bob se va a dormir 30 min 💤");
    }
    public ActionResult wake(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (!asleep(p, now)) return ActionResult.fail("Bob ya esta despierto");
        p.sleepUntil = now; p.happiness -= 5; NeedsEngine.clampAll(p); dao.savePet(p);
        return ActionResult.ok("Bob despierta de mal humor 😠");
    }

    // ---------- Mecanicas exclusivas de Bob Esponja ----------
    /** 1) Cocinar en el Crustaceo Cascarudo: cuesta 5 monedas y 10 energia, espera 10 min; produce Cangreburgers. */
    public ActionResult cookBurger(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob esta dormido 💤");
        long wait = 10L * 60 * 1000;
        if (now - p.lastCook < wait) return ActionResult.fail("La plancha se esta enfriando: " + ((wait - (now - p.lastCook)) / 60000 + 1) + " min");
        if (p.energy < 10) return ActionResult.fail("Sin energia para cocinar");
        if (p.coins < 5) return ActionResult.fail("Necesitas 5 monedas de ingredientes");
        int qty = "EVO_A".equals(p.stage) ? 2 : 1;
        p.lastCook = now; p.coins -= 5; p.energy -= 10; p.happiness += 3;
        addItem("burger", qty);
        return done(p, "¡Preparaste " + qty + " Cangreburger(s)! 🍔", 5);
    }
    /** 2) Cazar medusas: espera 15 min, 10 energia. 70% gana monedas, 30% picadura (-10 salud). */
    public ActionResult huntJellyfish(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob esta dormido 💤");
        long wait = 15L * 60 * 1000;
        if (now - p.lastJelly < wait) return ActionResult.fail("Las medusas se escondieron: " + ((wait - (now - p.lastJelly)) / 60000 + 1) + " min");
        if (p.energy < 10) return ActionResult.fail("Sin energia para cazar");
        p.lastJelly = now; p.energy -= 10;
        if (rnd.nextInt(100) < 70) {
            int gain = 10 + rnd.nextInt(16); p.coins += gain;
            return done(p, "¡Atrapaste medusas! +" + gain + " monedas 🪼", 6);
        }
        p.health -= 10; p.hygiene -= 5;
        return done(p, "¡Ay! Una medusa te pico (-10 salud) ⚡", 2);
    }

    // ---------- Minijuegos ----------
    /** Llamar antes de iniciar: cobra 10 de energia. */
    public ActionResult startMinigame(PetEntity p) {
        long now = System.currentTimeMillis(); NeedsEngine.update(p, now);
        if (asleep(p, now)) return ActionResult.fail("Bob esta dormido 💤");
        if (p.energy < 10) return ActionResult.fail("Bob necesita 10 de energia para jugar");
        p.energy -= 10; dao.savePet(p);
        return ActionResult.ok("¡A jugar!");
    }
    private static final float[] DIFF_MULT = {1f, 1.5f, 2f};
    private static final String[] DIFF_NAMES = {"Fácil", "Medio", "Difícil"};

    public ActionResult finishMinigame(PetEntity p, String game, int score, boolean won) {
        return finishMinigame(p, game, score, won, 0);
    }

    /** Premio proporcional al puntaje y multiplicado por la dificultad (solo si se gana). */
    public ActionResult finishMinigame(PetEntity p, String game, int score, boolean won, int difficulty) {
        int d = Math.max(0, Math.min(2, difficulty));
        float mult = won ? DIFF_MULT[d] : 1f;
        int coins = Math.round((won ? 10 + score * 2 : score / 2) * mult);
        if ("EVO_B".equals(p.stage)) coins = Math.round(coins * 1.25f);
        p.coins += coins; p.happiness += won ? 10 : 2; p.playCount++;
        if (won) { p.minigameWins++; MissionManager.track(dao, uid, "WINS", 1, false); }
        if ("MEMORY".equals(game)) p.bestMemory = Math.max(p.bestMemory, score);
        else p.bestReflex = Math.max(p.bestReflex, score);
        MissionManager.track(dao, uid, "PLAY", 1, false);
        int xp = won ? 12 + 4 * d : 4;
        return done(p, (won ? "¡Ganaste en " + DIFF_NAMES[d] + "! " : "Perdiste... ")
                + "+" + coins + " monedas", xp);
    }

    // ---------- Tienda / decoracion ----------
    public ActionResult buy(PetEntity p, String itemId) {
        Catalog.ItemDef d = Catalog.get(itemId);
        if (d == null) return ActionResult.fail("Articulo inexistente");
        if (p.level < d.minLevel) return ActionResult.fail("Se desbloquea en el nivel " + d.minLevel);
        if (p.coins < d.price) return ActionResult.fail("Te faltan " + (d.price - p.coins) + " monedas");
        if ("DECOR".equals(d.type)) {
            InventoryEntity e = dao.getInv(uid, itemId);
            if (e != null && e.quantity > 0) return ActionResult.fail("Ya tienes esa decoracion");
        }
        p.coins -= d.price; addItem(itemId, 1); dao.savePet(p);
        return ActionResult.ok("Compraste " + d.name + " " + d.emoji);
    }
    public ActionResult equip(String itemId) {
        Catalog.ItemDef d = Catalog.get(itemId);
        InventoryEntity e = dao.getInv(uid, itemId);
        if (d == null || !"DECOR".equals(d.type)) return ActionResult.fail("No es decoracion");
        if (e == null || e.quantity <= 0) return ActionResult.fail("No posees este objeto");
        dao.unequipSlot(uid, d.slot);               // un solo objeto por posicion
        e.equipped = true; dao.saveInv(e);
        return ActionResult.ok(d.name + " colocado en " + d.slot);
    }
    public ActionResult unequip(String itemId) {
        InventoryEntity e = dao.getInv(uid, itemId);
        if (e == null || !e.equipped) return ActionResult.fail("No esta equipado");
        e.equipped = false; dao.saveInv(e);
        return ActionResult.ok("Objeto retirado");
    }

    // ---------- Misiones ----------
    public ActionResult claimMission(PetEntity p, long missionId) {
        MissionEntity m = dao.getMission(missionId);
        if (m == null) return ActionResult.fail("Mision inexistente");
        if (m.claimed) return ActionResult.fail("Ya reclamaste esta recompensa");
        if (!m.isComplete()) return ActionResult.fail("Aun no la completas (" + m.progress + "/" + m.target + ")");
        m.claimed = true; dao.saveMission(m);
        p.coins += m.rewardCoins;
        return done(p, "Recompensa: +" + m.rewardCoins + " monedas, +" + m.rewardXp + " XP", m.rewardXp);
    }
}
