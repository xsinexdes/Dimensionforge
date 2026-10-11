package me.dimensionforge.skill.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fase de un jefe: se activa cuando la vida baja de un porcentaje. */
public class BossPhase {
    /** Se activa cuando la vida (%) es igual o menor que este valor. */
    public int healthPercent = 66;
    public String name = "Fase";
    /** Mensaje (MiniMessage) mostrado a los jugadores cercanos al entrar en la fase. */
    public String message = "";
    public String sound = "entity.wither.spawn";
    /** Animación del modelo al entrar en la fase (vacío = ninguna). */
    public String animation = "";
    public double speedMultiplier = 1.0;
    public double damageMultiplier = 1.0;
    /** Si true, las habilidades base del mob se desactivan mientras dure esta fase. */
    public boolean replaceBaseSkills = false;
    /** Curación al entrar en la fase, como fracción de la vida máxima (0-1). */
    public double healFraction = 0;
    public List<SkillBinding> skills = new ArrayList<>();

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("health-percent", healthPercent);
        m.put("name", name);
        m.put("message", message);
        m.put("sound", sound);
        m.put("animation", animation);
        m.put("speed-multiplier", speedMultiplier);
        m.put("damage-multiplier", damageMultiplier);
        m.put("replace-base-skills", replaceBaseSkills);
        m.put("heal-fraction", healFraction);
        List<Map<String, Object>> l = new ArrayList<>();
        for (SkillBinding b : skills) l.add(b.toMap());
        m.put("skills", l);
        return m;
    }

    public static BossPhase fromMap(Map<?, ?> m) {
        BossPhase p = new BossPhase();
        p.healthPercent = (int) SkillDef.num(m.get("health-percent"), 66);
        p.name = SkillDef.str(m.get("name"), "Fase");
        p.message = SkillDef.str(m.get("message"), "");
        p.sound = SkillDef.str(m.get("sound"), "");
        p.animation = SkillDef.str(m.get("animation"), "");
        p.speedMultiplier = SkillDef.num(m.get("speed-multiplier"), 1);
        p.damageMultiplier = SkillDef.num(m.get("damage-multiplier"), 1);
        p.replaceBaseSkills = Boolean.parseBoolean(String.valueOf(m.get("replace-base-skills")));
        p.healFraction = SkillDef.num(m.get("heal-fraction"), 0);
        if (m.get("skills") instanceof List<?> l) {
            for (Object o : l) if (o instanceof Map<?, ?> mm) p.skills.add(SkillBinding.fromMap(mm));
        }
        return p;
    }
}
