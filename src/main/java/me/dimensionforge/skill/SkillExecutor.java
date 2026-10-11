package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.mob.MobState;
import me.dimensionforge.skill.model.Shape;
import me.dimensionforge.skill.model.SkillBinding;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.skill.model.Trigger;
import me.dimensionforge.util.Fx;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Lanza habilidades: valida requisitos, aplica enfriamiento y ejecuta cada una de las 18 formas. */
public final class SkillExecutor {

    private final DimensionForgePlugin plugin;
    private final SkillManager manager;
    private final SkillEngine engine;
    private final SkillEffects fx;

    SkillExecutor(DimensionForgePlugin plugin, SkillManager manager, SkillEngine engine) {
        this.plugin = plugin;
        this.manager = manager;
        this.engine = engine;
        this.fx = new SkillEffects(plugin, engine);
    }

    /**
     * Intenta lanzar una habilidad. Devuelve true si se lanzó.
     *
     * @param other jugador que provocó el disparador (puede ser null)
     */
    public boolean cast(MobState st, SkillBinding b, SkillDef d, LivingEntity other) {
        String key = b.skillId;
        if (!engine.ready(st, key)) {
            return false;
        }
        if (d.chance < 1.0 && ThreadLocalRandom.current().nextDouble() >= d.chance) {
            return false;
        }
        LivingEntity caster = st.entity;
        if (!caster.isValid() && d.trigger != Trigger.ON_DEATH) {
            return false;
        }
        Location origin = caster.getLocation();
        World w = origin.getWorld();
        if (w == null) {
            return false;
        }
        double searchRange = d.range > 0 ? d.range : 32;
        Player target = null;
        if (other instanceof Player p && engine.validPlayer(p) && p.getWorld().equals(w)
                && p.getLocation().distance(origin) <= Math.max(searchRange, 64)) {
            target = p;
        }
        Shape.Kind kind = d.shape.kind;
        boolean needsTarget = switch (d.shape) {
            case TARGET_PLAYER, NEAREST_PLAYER, PROJECTILE_STRAIGHT, PROJECTILE_HOMING, BEAM_LINE, CONE,
                 SKY_RAIN, GROUND_UNDER_TARGET, METEORS, LEAP, CHARGE, TELEPORT_BEHIND -> true;
            default -> false;
        };
        if (needsTarget) {
            Player near = engine.nearestPlayer(origin, searchRange);
            boolean triggerTargetInRange = target != null && target.getLocation().distance(origin) <= searchRange;
            if (d.shape == Shape.NEAREST_PLAYER || !triggerTargetInRange) {
                target = near;
            }
            if (target == null) {
                return false;
            }
        } else if (kind == Shape.Kind.AREA || kind == Shape.Kind.SUMMON) {
            if (engine.nearestPlayer(origin, Math.max(d.radius * 1.6, 12)) == null) {
                return false;
            }
        }

        engine.setCooldown(st, key, d.cooldown);
        if (!b.animation.isBlank()) {
            plugin.mobs().playOnce(st, b.animation);
        }
        SkillContext c = new SkillContext(st, d, target);
        boolean prev = engine.casting();
        try {
            // casting = true evita que el daño de la habilidad dispare otras habilidades en cadena
            setCasting(true);
            run(c);
        } finally {
            setCasting(prev);
        }
        return true;
    }

    private void setCasting(boolean v) {
        engine.setCasting(v);
    }

    // ---------------------------------------------------------------- formas

    private void run(SkillContext c) {
        SkillDef d = c.d;
        LivingEntity caster = c.caster;
        Location origin = c.origin;
        World w = origin.getWorld();
        switch (d.shape) {
            case SELF -> {
                aura(c, origin);
                fx.impact(c, origin, List.of(caster));
            }
            case ALLIES -> {
                List<LivingEntity> allies = new ArrayList<>();
                for (MobState s : plugin.mobs().active()) {
                    if (s.entity.isValid() && s.entity.getWorld().equals(w)
                            && s.entity.getLocation().distanceSquared(origin) <= d.radius * d.radius) {
                        allies.add(s.entity);
                    }
                }
                if (allies.isEmpty()) {
                    allies.add(caster);
                }
                ring(c, origin, d.radius, 20, 0.3);
                fx.impact(c, origin, allies);
            }
            case TARGET_PLAYER, NEAREST_PLAYER -> {
                Location from = eye(caster);
                line(c, from, c.target.getLocation().add(0, 1, 0));
                fx.impact(c, c.target.getLocation(), List.of(c.target));
            }
            case ALL_IN_RADIUS -> {
                ring(c, origin, d.radius, 24, 0.2);
                fx.impact(c, origin, new ArrayList<>(fx.playersNear(w, origin, d.radius)));
            }
            case PROJECTILE_STRAIGHT -> projectile(c, false);
            case PROJECTILE_HOMING -> projectile(c, true);
            case BEAM_LINE -> beam(c);
            case CONE -> cone(c);
            case CIRCLE_WAVE -> wave(c);
            case RING -> {
                ring(c, origin, d.radius, 28, 0.2);
                List<LivingEntity> hit = new ArrayList<>();
                for (Player p : fx.playersNear(w, origin, d.radius)) {
                    if (horizontal(p.getLocation(), origin) >= d.radius - 2.5) {
                        hit.add(p);
                    }
                }
                fx.impact(c, origin, hit);
            }
            case SKY_RAIN -> rain(c);
            case GROUND_UNDER_TARGET -> groundUnder(c);
            case METEORS -> meteors(c);
            case SUMMON_CIRCLE -> ritual(c);
            case LEAP -> leap(c);
            case CHARGE -> charge(c);
            case TELEPORT_BEHIND -> teleportBehind(c);
        }
    }

    // ---------------------------------------------------------------- visuales (con presupuesto)

    private static Location eye(LivingEntity e) {
        return e.getEyeLocation();
    }

    private static double horizontal(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void aura(SkillContext c, Location center) {
        ring(c, center, 1.2, 12, 0.2);
        ring(c, center, 1.2, 12, 1.2);
    }

    private void ring(SkillContext c, Location center, double r, int points, double yOff) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            engine.particle(center.clone().add(Math.cos(a) * r, yOff, Math.sin(a) * r), c.particle, 1, 0, c.color);
        }
    }

    private void line(SkillContext c, Location a, Location b) {
        Vector v = b.toVector().subtract(a.toVector());
        double len = v.length();
        if (len < 0.1) {
            return;
        }
        int steps = (int) Math.min(30, Math.ceil(len / 0.7));
        Vector step = v.clone().multiply(1.0 / steps);
        Location l = a.clone();
        for (int i = 0; i < steps; i++) {
            l.add(step);
            engine.particle(l, c.particle, 1, 0.02, c.color);
        }
    }

    private Vector dirTo(Location from, Location to) {
        Vector v = to.toVector().subtract(from.toVector());
        if (v.lengthSquared() < 1.0E-6) {
            return new Vector(0, 0, 1);
        }
        return v.normalize();
    }

    // ---------------------------------------------------------------- proyectiles

    private void projectile(SkillContext c, boolean homing) {
        double speed = homing ? 0.9 : 1.2;
        int maxSteps = (int) Math.ceil(Math.max(8, c.d.range) / speed) + 4;
        Location pos = eye(c.caster).clone();
        Vector dir = dirTo(pos, c.target.getLocation().add(0, 1, 0));
        double blast = Math.max(1.5, Math.min(3.0, c.d.radius));
        step(c, pos, dir, speed, homing, 0, maxSteps, blast);
    }

    private void step(SkillContext c, Location pos, Vector dir, double speed, boolean homing, int n, int max, double blast) {
        engine.later(c.owner, 1, () -> {
            World w = pos.getWorld();
            if (w == null || n > max) {
                return;
            }
            Vector d = dir;
            if (homing && c.target.isValid() && c.target.getWorld().equals(w)) {
                Vector want = dirTo(pos, c.target.getLocation().add(0, 1, 0));
                d = dir.clone().multiply(0.8).add(want.multiply(0.2)).normalize();
            }
            pos.add(d.clone().multiply(speed));
            engine.particle(pos, c.particle, 3, 0.05, c.color);
            boolean solid = !pos.getBlock().isPassable();
            List<Player> hit = fx.playersNear(w, pos.clone().subtract(0, 0.5, 0), 1.1);
            if (solid || !hit.isEmpty()) {
                List<LivingEntity> victims = new ArrayList<>(fx.playersNear(w, pos, blast));
                fx.impact(c, pos.clone(), victims);
                return;
            }
            step(c, pos, d, speed, homing, n + 1, max, blast);
        });
    }

    // ---------------------------------------------------------------- rayo, cono, onda

    private void beam(SkillContext c) {
        Location from = eye(c.caster);
        Vector dir = dirTo(from, c.target.getLocation().add(0, 1, 0));
        double range = Math.max(6, c.d.range);
        World w = from.getWorld();
        RayTraceResult hit = w.rayTraceBlocks(from, dir, range, FluidCollisionMode.NEVER, true);
        double len = hit == null ? range : hit.getHitPosition().distance(from.toVector());
        Location end = from.clone().add(dir.clone().multiply(len));
        line(c, from, end);
        List<LivingEntity> victims = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (!engine.validPlayer(p)) {
                continue;
            }
            Vector center = p.getLocation().add(0, 1, 0).toVector();
            Vector rel = center.clone().subtract(from.toVector());
            double t = rel.dot(dir);
            if (t < 0 || t > len) {
                continue;
            }
            double dist = rel.subtract(dir.clone().multiply(t)).length();
            if (dist <= 1.4) {
                victims.add(p);
            }
        }
        fx.impact(c, end, victims);
    }

    private void cone(SkillContext c) {
        Location from = eye(c.caster);
        Vector dir = dirTo(from, c.target.getLocation().add(0, 1, 0));
        double range = Math.max(4, c.d.range);
        for (int i = -2; i <= 2; i++) {
            Vector side = dir.clone().rotateAroundY(Math.toRadians(i * 12));
            line(c, from.clone().add(side.clone().multiply(1.0)), from.clone().add(side.multiply(range)));
        }
        List<LivingEntity> victims = new ArrayList<>();
        for (Player p : from.getWorld().getPlayers()) {
            if (!engine.validPlayer(p)) {
                continue;
            }
            Vector rel = p.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            double len = rel.length();
            if (len <= range && len > 0.01 && rel.normalize().dot(dir) >= 0.82) {
                victims.add(p);
            }
        }
        fx.impact(c, from.clone().add(dir.multiply(range / 2)), victims);
    }

    private void wave(SkillContext c) {
        Location center = c.origin.clone();
        double radius = c.d.radius;
        Set<UUID> done = new HashSet<>();
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            final int s = i;
            engine.later(c.owner, i, () -> {
                double r = radius * s / steps;
                double prev = radius * (s - 1) / steps;
                ring(c, center, r, 24, 0.2);
                for (Player p : fx.playersNear(center.getWorld(), center, r + 0.5)) {
                    double hd = horizontal(p.getLocation(), center);
                    if (hd > prev - 0.3 && hd <= r + 0.5 && Math.abs(p.getLocation().getY() - center.getY()) <= 3.0
                            && done.add(p.getUniqueId())) {
                        fx.impact(c, p.getLocation(), List.of(p));
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- cielo / suelo / meteoritos

    private void rain(SkillContext c) {
        Location center = c.target.getLocation();
        int n = Math.max(1, c.d.count);
        double radius = Math.max(3, c.d.radius);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(r.nextDouble()) * radius;
            Location pt = center.clone().add(Math.cos(a) * dist, 0, Math.sin(a) * dist);
            long delay = 6 + (long) i * Math.max(1, 40 / n);
            engine.later(c.owner, Math.max(1, delay - 4), () -> column(c, pt, 8));
            engine.later(c.owner, delay, () -> {
                List<LivingEntity> victims = new ArrayList<>(fx.playersNear(pt.getWorld(), pt, 2.2));
                fx.impact(c, pt, victims);
            });
        }
    }

    private void column(SkillContext c, Location base, int height) {
        for (int y = 0; y < height; y += 2) {
            engine.particle(base.clone().add(0, y, 0), c.particle, 1, 0.05, c.color);
        }
    }

    private void groundUnder(SkillContext c) {
        Location pt = c.target.getLocation().clone();
        double radius = Math.max(2, c.d.radius);
        ring(c, pt, radius, 20, 0.1);
        engine.later(c.owner, 8, () -> ring(c, pt, radius * 0.6, 14, 0.1));
        engine.later(c.owner, 16, () -> {
            List<LivingEntity> victims = new ArrayList<>(fx.playersNear(pt.getWorld(), pt, radius));
            fx.impact(c, pt, victims);
        });
    }

    private void meteors(SkillContext c) {
        Location center = c.target.getLocation();
        int n = Math.max(1, c.d.count);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(r.nextDouble()) * 5.0;
            Location pt = center.clone().add(Math.cos(a) * dist, 0, Math.sin(a) * dist);
            long delay = 24 + (long) i * 6;
            for (int k = 3; k >= 1; k--) {
                final int kk = k;
                engine.later(c.owner, delay - k * 3L, () -> column(c, pt.clone().add(0, kk * 3, 0), 4));
            }
            engine.later(c.owner, delay, () -> {
                pt.getWorld().spawnParticle(Fx.particle("EXPLOSION", Particle.CRIT), pt, 1);
                List<LivingEntity> victims = new ArrayList<>(fx.playersNear(pt.getWorld(), pt, c.d.radius));
                fx.impact(c, pt, victims);
            });
        }
    }

    private void ritual(SkillContext c) {
        Location center = c.origin.clone();
        for (int i = 0; i < 5; i++) {
            engine.later(c.owner, i * 4L + 1, () -> ring(c, center, Math.max(2, c.d.radius), 16, 0.1));
        }
        engine.later(c.owner, 22, () -> fx.impact(c, c.caster.isValid() ? c.caster.getLocation() : center, List.of(c.caster)));
    }

    // ---------------------------------------------------------------- movimiento

    private void leap(SkillContext c) {
        LivingEntity caster = c.caster;
        Vector flat = c.target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0);
        double dist = flat.length();
        Vector vel = (dist < 0.1 ? new Vector(0, 0, 0) : flat.normalize().multiply(Math.min(1.5, 0.35 + dist * 0.1))).setY(0.75);
        caster.setVelocity(vel);
        landing(c, 0);
    }

    private void landing(SkillContext c, int n) {
        engine.later(c.owner, 2, () -> {
            LivingEntity caster = c.caster;
            if (!caster.isValid()) {
                return;
            }
            if ((caster.isOnGround() && n >= 3) || n > 20) {
                Location l = caster.getLocation();
                engine.particle(l, c.particle, 20, c.d.radius * 0.4, c.color);
                fx.impact(c, l, new ArrayList<>(fx.playersNear(l.getWorld(), l, c.d.radius)));
                return;
            }
            landing(c, n + 1);
        });
    }

    private void charge(SkillContext c) {
        LivingEntity caster = c.caster;
        Vector dir = c.target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) {
            dir = caster.getLocation().getDirection().setY(0);
        }
        final Vector d = dir.normalize();
        Set<UUID> done = new HashSet<>();
        for (int i = 1; i <= 12; i++) {
            engine.later(c.owner, i, () -> {
                if (!caster.isValid()) {
                    return;
                }
                caster.setVelocity(d.clone().multiply(0.9).setY(caster.isOnGround() ? 0.1 : caster.getVelocity().getY()));
                Location l = caster.getLocation();
                engine.particle(l.clone().add(0, 0.5, 0), c.particle, 4, 0.3, c.color);
                for (Player p : fx.playersNear(l.getWorld(), l, 2.2)) {
                    if (done.add(p.getUniqueId())) {
                        fx.impact(c, p.getLocation(), List.of(p));
                    }
                }
            });
        }
    }

    private void teleportBehind(SkillContext c) {
        LivingEntity caster = c.caster;
        Player t = c.target;
        Location tl = t.getLocation();
        Vector back = tl.getDirection().setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(0, 0, 1);
        }
        back.normalize().multiply(-1.8);
        Location dest = tl.clone().add(back);
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            dest = tl.clone();
        }
        dest.setDirection(tl.toVector().subtract(dest.toVector()));
        engine.particle(caster.getLocation().add(0, 1, 0), c.particle, 20, 0.4, c.color);
        caster.teleport(dest);
        engine.particle(dest.clone().add(0, 1, 0), c.particle, 20, 0.4, c.color);
        if (caster instanceof Mob m) {
            m.setTarget(t);
        }
        engine.later(c.owner, 3, () -> fx.impact(c, t.getLocation(), List.of(t)));
    }

    /** Lanzamiento forzado para pruebas desde el menú: ignora enfriamiento y probabilidad. */
    public boolean test(MobState st, SkillBinding b, SkillDef d, Player viewer) {
        engine.resetCooldowns(st);
        engine.addTester(viewer.getUniqueId(), 400);
        SkillDef copy = d.copy();
        copy.chance = 1.0;
        return cast(st, b, copy, viewer);
    }
}
