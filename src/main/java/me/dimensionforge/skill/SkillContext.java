package me.dimensionforge.skill;

import me.dimensionforge.mob.MobState;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.util.Fx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Datos de un lanzamiento concreto de una habilidad. */
final class SkillContext {
    final MobState st;
    final SkillDef d;
    final LivingEntity caster;
    final Player target;
    /** Potencia efectiva (ya con el multiplicador de fase del mob). */
    final double power;
    final UUID owner;
    final Particle particle;
    final Color color;
    /** Ubicación del lanzador en el momento de lanzar (sigue siendo válida si el lanzador muere después). */
    final Location origin;
    boolean soundDone;

    SkillContext(MobState st, SkillDef d, Player target) {
        this.st = st;
        this.d = d;
        this.caster = st.entity;
        this.target = target;
        this.power = d.power * st.damageMultiplier;
        this.owner = st.id;
        this.particle = Fx.particle(d.element.particle, Particle.CRIT);
        this.color = Fx.color(d.element.color, Color.WHITE);
        this.origin = st.entity.getLocation().clone();
    }

    long durationTicks() {
        return Math.max(20L, (long) (d.duration * 20.0));
    }
}
