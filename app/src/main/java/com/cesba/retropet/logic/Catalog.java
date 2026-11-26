package com.cesba.retropet.logic;
import java.util.*;
/** Tienda: 2 alimentos, 2 cuidado, 6 decoraciones (3 espacios: WALL, CEILING, FLOOR). */
public class Catalog {
    public static class ItemDef {
        public final String id, name, type, slot, emoji;
        public final int price, minLevel, food, hap, energy, hyg, health;
        ItemDef(String id, String name, String type, String slot, String emoji, int price, int minLevel,
                int food, int hap, int energy, int hyg, int health) {
            this.id = id; this.name = name; this.type = type; this.slot = slot; this.emoji = emoji;
            this.price = price; this.minLevel = minLevel;
            this.food = food; this.hap = hap; this.energy = energy; this.hyg = hyg; this.health = health;
        }
    }
    public static final List<ItemDef> ALL = new ArrayList<>();
    private static void add(String id, String n, String t, String s, String e, int price, int lvl,
                            int f, int h, int en, int hy, int he) { ALL.add(new ItemDef(id, n, t, s, e, price, lvl, f, h, en, hy, he)); }
    static {
        // Alimentos
        add("burger",  "Cangreburger",       "FOOD", null, "🍔", 15, 1, 35,  5, 10, 0, 0);
        add("kelpshake","Batido de algas",   "FOOD", null, "🥤", 10, 1, 20,  3, 25, 0, 0);
        // Cuidado
        add("soap",    "Jabon de burbujas",  "CARE", null, "🧼", 12, 1,  0,  3,  0, 45, 0);
        add("medicine","Medicina marina",    "CARE", null, "💊", 25, 1,  0,  0, -5, 0, 40);
        // Decoracion (slot, nivel minimo)
        add("wall_gary",   "Cuadro de Gary",        "DECOR", "WALL",    "🐌", 40, 1, 0,0,0,0,0);
        add("wall_flag",   "Bandera pirata",        "DECOR", "WALL",    "🏴‍☠️", 60, 4, 0,0,0,0,0);
        add("ceil_lamp",   "Lampara de medusa",     "DECOR", "CEILING", "🪼", 50, 2, 0,0,0,0,0);
        add("ceil_bubbles","Burbujas colgantes",    "DECOR", "CEILING", "🫧", 35, 1, 0,0,0,0,0);
        add("floor_rug",   "Alfombra de flores",    "DECOR", "FLOOR",   "🌺", 30, 1, 0,0,0,0,0);
        add("floor_anchor","Ancla oxidada",         "DECOR", "FLOOR",   "⚓", 45, 3, 0,0,0,0,0);
    }
    public static ItemDef get(String id) { for (ItemDef d : ALL) if (d.id.equals(id)) return d; return null; }
}
