package me.dimensionforge.skill.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Asignación de una habilidad a un mob, con valores que sustituyen a los de la definición (null = usar el original). */
public class SkillBinding {
    public String skillId;
    public boolean enabled = true;
    public Trigger trigger;
    public Double triggerValue;
    public Double power;
    public Double radius;
    public Double cooldown;
    public Double chance;
    /** Animación del modelo a reproducir al lanzarla (vacío = ninguna). */
    public String animation = "";

    public SkillBinding() {
    }

    public SkillBinding(String skillId) {
        this.skillId = skillId;
    }

    public SkillBinding copy() {
        return fromMap(toMap());
    }

    public void reset() {
        enabled = true;
        trigger = null;
        triggerValue = null;
        power = null;
        radius = null;
        cooldown = null;
        chance = null;
        animation = "";
    }

    public boolean hasOverrides() {
        return trigger != null || triggerValue != null || power != null || radius != null || cooldown != null
                || chance != null || !animation.isEmpty();
    }

    /** Devuelve una copia de la definición con los valores de este binding aplicados. */
    public SkillDef applyTo(SkillDef base) {
        SkillDef d = base.copy();
        if (trigger != null) d.trigger = trigger;
        if (triggerValue != null) d.triggerValue = triggerValue;
        else if (trigger != null && trigger != base.trigger) d.triggerValue = trigger.defaultValue;
        if (power != null) d.power = power;
        if (radius != null) d.radius = radius;
        if (cooldown != null) d.cooldown = cooldown;
        if (chance != null) d.chance = chance;
        return d;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skill", skillId);
        m.put("enabled", enabled);
        if (trigger != null) m.put("trigger", trigger.name());
        if (triggerValue != null) m.put("trigger-value", triggerValue);
        if (power != null) m.put("power", power);
        if (radius != null) m.put("radius", radius);
        if (cooldown != null) m.put("cooldown", cooldown);
        if (chance != null) m.put("chance", chance);
        if (!animation.isEmpty()) m.put("animation", animation);
        return m;
    }

    public static SkillBinding fromMap(Map<?, ?> m) {
        SkillBinding b = new SkillBinding(SkillDef.str(m.get("skill"), ""));
        b.enabled = m.get("enabled") == null || Boolean.parseBoolean(String.valueOf(m.get("enabled")));
        if (m.get("trigger") != null) b.trigger = SkillDef.en(Trigger.class, m.get("trigger"), null);
        if (m.get("trigger-value") != null) b.triggerValue = SkillDef.num(m.get("trigger-value"), 0);
        if (m.get("power") != null) b.power = SkillDef.num(m.get("power"), 0);
        if (m.get("radius") != null) b.radius = SkillDef.num(m.get("radius"), 0);
        if (m.get("cooldown") != null) b.cooldown = SkillDef.num(m.get("cooldown"), 0);
        if (m.get("chance") != null) b.chance = SkillDef.num(m.get("chance"), 0);
        b.animation = SkillDef.str(m.get("animation"), "");
        return b;
    }
}
