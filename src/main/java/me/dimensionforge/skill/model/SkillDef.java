package me.dimensionforge.skill.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Definición completa de una habilidad (generada o hecha a mano). Sin dependencias de Bukkit. */
public class SkillDef {
    public String id;
    public String name;
    public String description;
    public Trigger trigger = Trigger.INTERVAL;
    public double triggerValue = 8;
    public Shape shape = Shape.NEAREST_PLAYER;
    public Element element = Element.ARCANE;
    public Effect effect = Effect.DAMAGE;
    /** Efecto secundario opcional (puede ser null). */
    public Effect secondary;
    public List<Modifier> modifiers = new ArrayList<>();
    /** Daño en puntos de vida o intensidad del efecto. */
    public double power = 6;
    public double radius = 5;
    public double range = 16;
    public double cooldown = 10;
    public double duration = 5;
    public int count = 1;
    /** Probabilidad 0-1 de lanzarla cuando se cumple el disparador. */
    public double chance = 1.0;
    public boolean signature;
    /** Valoración de potencia 1-5 (para ordenar/filtrar en el menú). */
    public int tier = 2;
    /** Texto opcional: nombre de animación del modelo, tipo de mob a invocar o título. */
    public String extra = "";
    /** Filtro de arma para el disparador HIT_BY_WEAPON (SWORD, AXE, PICKAXE, SHOVEL, HOE, TRIDENT, MACE, BOW o vacío = cualquiera). */
    public String weapon = "";

    public Effect.Category category() {
        return effect.category;
    }

    public SkillDef copy() {
        return fromMap(id, toMap());
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("description", description);
        m.put("trigger", trigger.name());
        m.put("trigger-value", triggerValue);
        m.put("shape", shape.name());
        m.put("element", element.name());
        m.put("effect", effect.name());
        if (secondary != null) m.put("secondary", secondary.name());
        List<String> mods = new ArrayList<>();
        for (Modifier mod : modifiers) mods.add(mod.name());
        m.put("modifiers", mods);
        m.put("power", round(power));
        m.put("radius", round(radius));
        m.put("range", round(range));
        m.put("cooldown", round(cooldown));
        m.put("duration", round(duration));
        m.put("count", count);
        m.put("chance", round(chance));
        m.put("signature", signature);
        m.put("tier", tier);
        if (!extra.isEmpty()) m.put("extra", extra);
        if (!weapon.isEmpty()) m.put("weapon", weapon);
        return m;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static SkillDef fromMap(String id, Map<?, ?> m) {
        SkillDef d = new SkillDef();
        d.id = id;
        d.name = str(m.get("name"), id);
        d.description = str(m.get("description"), "");
        d.trigger = en(Trigger.class, m.get("trigger"), Trigger.INTERVAL);
        d.triggerValue = num(m.get("trigger-value"), d.trigger.defaultValue);
        d.shape = en(Shape.class, m.get("shape"), Shape.NEAREST_PLAYER);
        d.element = en(Element.class, m.get("element"), Element.ARCANE);
        d.effect = en(Effect.class, m.get("effect"), Effect.DAMAGE);
        d.secondary = m.get("secondary") == null ? null : en(Effect.class, m.get("secondary"), null);
        Object mods = m.get("modifiers");
        if (mods instanceof List<?> l) {
            for (Object o : l) {
                Modifier mod = en(Modifier.class, o, null);
                if (mod != null) d.modifiers.add(mod);
            }
        }
        d.power = num(m.get("power"), 6);
        d.radius = num(m.get("radius"), 5);
        d.range = num(m.get("range"), 16);
        d.cooldown = num(m.get("cooldown"), 10);
        d.duration = num(m.get("duration"), 5);
        d.count = (int) num(m.get("count"), 1);
        d.chance = num(m.get("chance"), 1.0);
        d.signature = Boolean.parseBoolean(String.valueOf(m.get("signature")));
        d.tier = (int) num(m.get("tier"), 2);
        d.extra = str(m.get("extra"), "");
        d.weapon = str(m.get("weapon"), "");
        return d;
    }

    static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o == null) return def;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static <E extends Enum<E>> E en(Class<E> c, Object o, E def) {
        if (o == null) return def;
        try {
            return Enum.valueOf(c, String.valueOf(o).toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
