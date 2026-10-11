package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.mob.MobDef;
import me.dimensionforge.mob.MobManager;
import me.dimensionforge.mob.MobState;
import me.dimensionforge.skill.model.Effect;
import me.dimensionforge.util.Fx;
import me.dimensionforge.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Aplicación de los 52 efectos de habilidad sobre víctimas, puntos y el propio lanzador. */
final class SkillEffects {

    private final DimensionForgePlugin plugin;
    private final SkillEngine engine;

    SkillEffects(DimensionForgePlugin plugin, SkillEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    /** Aplica el efecto principal y el secundario de la habilidad en un impacto. */
    void impact(SkillContext c, Location point, List<LivingEntity> victims) {
        if (point.getWorld() == null) {
            return;
        }
        impactFx(c, point);
        apply(c, c.d.effect, point, victims);
        if (c.d.secondary != null) {
            apply(c, c.d.secondary, point, victims);
        }
    }

    private void impactFx(SkillContext c, Location point) {
        engine.particle(point.clone().add(0, 0.8, 0), c.particle, 12, 0.5, c.color);
        if (!c.soundDone) {
            c.soundDone = true;
            Fx.sound(point, c.d.element.sound, 0.8f, 1.0f);
        }
    }

    private void apply(SkillContext c, Effect e, Location point, List<LivingEntity> victims) {
        switch (e.kind) {
            case TARGET -> {
                for (LivingEntity v : victims) {
                    if (v instanceof Player p && !engine.validPlayer(p)) {
                        continue;
                    }
                    target(c, e, v, point);
                }
            }
            case SUPPORT -> {
                for (LivingEntity v : victims) {
                    support(c, e, v);
                }
            }
            case POINT -> point(c, e, point, victims);
            case CASTER -> caster(c, e);
        }
    }

    // ---------------------------------------------------------------- utilidades

    static int amp(SkillContext c, int max) {
        return Math.max(0, Math.min(max, (int) (c.power / 3.0)));
    }

    private void potion(LivingEntity v, String key, long ticks, int amplifier) {
        PotionEffectType t = Fx.effect(key);
        if (t != null) {
            v.addPotionEffect(new PotionEffect(t, (int) Math.min(ticks, 20 * 60), amplifier, false, true, true));
        }
    }

    void hurt(SkillContext c, LivingEntity v, double amount) {
        if (amount <= 0 || v.isDead()) {
            return;
        }
        v.setNoDamageTicks(0);
        if (c.caster.isValid()) {
            v.damage(amount, c.caster);
        } else {
            v.damage(amount);
        }
    }

    private static Vector away(Location from, Location to) {
        Vector v = to.toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1.0E-4) {
            double a = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            return new Vector(Math.cos(a), 0, Math.sin(a));
        }
        return v.normalize();
    }

    private static void push(LivingEntity v, Vector dir, double strength, double y) {
        Vector vel = dir.clone().multiply(strength).setY(y);
        if (vel.length() > 2.4) {
            vel.normalize().multiply(2.4);
        }
        v.setVelocity(vel);
    }

    private void dot(SkillContext c, LivingEntity v, double perSecond, boolean blood) {
        int seconds = (int) Math.max(1, Math.min(30, Math.round(c.d.duration)));
        for (int i = 1; i <= seconds; i++) {
            engine.later(c.owner, i * 20L, () -> {
                if (!v.isValid() || v.isDead() || v instanceof Player p && !engine.validPlayer(p)) {
                    return;
                }
                hurt(c, v, perSecond);
                Location l = v.getLocation().add(0, 1, 0);
                if (blood) {
                    engine.particle(l, Fx.particle("DUST", Particle.CRIT), 6, 0.3, org.bukkit.Color.fromRGB(150, 0, 20));
                } else {
                    engine.particle(l, c.particle, 4, 0.3, c.color);
                }
            });
        }
    }

    // ---------------------------------------------------------------- efectos sobre víctimas

    private void target(SkillContext c, Effect e, LivingEntity v, Location point) {
        long dur = c.durationTicks();
        Location vl = v.getLocation();
        Location from = c.caster.isValid() ? c.caster.getLocation() : c.origin;
        engine.particle(vl.clone().add(0, 1, 0), c.particle, 5, 0.3, c.color);
        switch (e) {
            case DAMAGE -> hurt(c, v, c.power);
            case DAMAGE_OVER_TIME -> dot(c, v, Math.max(1.0, c.power * 0.4), false);
            case BLEED -> dot(c, v, Math.max(1.0, c.power * 0.5), true);
            case BURN -> {
                v.setFireTicks((int) Math.min(dur, 20 * 15));
                hurt(c, v, Math.max(1, c.power * 0.5));
            }
            case LIFESTEAL -> {
                double dealt = Math.max(1.0, c.power * 0.8);
                hurt(c, v, dealt);
                AttributeInstance max = c.caster.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (max != null && c.caster.isValid()) {
                    c.caster.setHealth(Math.min(max.getValue(), c.caster.getHealth() + dealt * 0.8));
                }
            }
            case KNOCKBACK -> push(v, away(from, vl), 0.7 + c.power * 0.12, 0.35);
            case PULL -> push(v, away(vl, from), 0.7 + c.power * 0.1, 0.25);
            case PULL_TO_POINT -> push(v, away(vl, point), 0.6 + c.power * 0.1, 0.2);
            case LAUNCH_UP -> push(v, new Vector(0, 0, 0), 0, 0.6 + c.power * 0.07);
            case SCARE -> {
                push(v, away(from, vl), 1.0 + c.power * 0.08, 0.3);
                potion(v, "nausea", 80, 0);
            }
            case SLOW -> potion(v, "slowness", dur, amp(c, 3));
            case FREEZE -> {
                v.setFreezeTicks(Math.min(v.getMaxFreezeTicks() + 100, (int) dur + 140));
                potion(v, "slowness", dur, 1);
            }
            case POISON -> potion(v, "poison", dur, Math.min(2, amp(c, 2)));
            case BLIND -> potion(v, "blindness", Math.min(dur, 160), 0);
            case NAUSEA -> potion(v, "nausea", dur, 0);
            case WEAKNESS -> potion(v, "weakness", dur, amp(c, 2));
            case HUNGER -> potion(v, "hunger", dur, amp(c, 3));
            case LEVITATE -> potion(v, "levitation", Math.min(dur, 60), 0);
            case MINING_FATIGUE -> potion(v, "mining_fatigue", dur, amp(c, 3));
            case WITHER -> potion(v, "wither", Math.min(dur, 120), Math.min(1, amp(c, 1)));
            case DARKNESS -> potion(v, "darkness", dur, 0);
            case GLOW -> potion(v, "glowing", dur, 0);
            case ROOT -> {
                potion(v, "slowness", dur, 6);
                potion(v, "jump_boost", dur, 128);
            }
            case STUN -> {
                long t = Math.min(dur, 80);
                potion(v, "slowness", t, 6);
                potion(v, "jump_boost", t, 128);
                potion(v, "mining_fatigue", t, 2);
                engine.stun(v.getUniqueId(), t);
            }
            case SILENCE -> {
                engine.silence(v.getUniqueId(), dur);
                if (v instanceof Player p) {
                    p.sendActionBar(Text.mm(plugin.messages().raw("skill.silenced")));
                }
            }
            case MARK -> {
                engine.mark(v.getUniqueId(), dur);
                if (v instanceof Player p) {
                    p.sendActionBar(Text.mm(plugin.messages().raw("skill.marked")));
                }
            }
            case DISARM -> {
                if (v instanceof Player p) {
                    disarm(p);
                }
            }
            case DURABILITY_DAMAGE -> {
                if (v instanceof Player p) {
                    wear(p, (int) Math.max(1, c.power * 4));
                }
            }
            case DRAIN_XP -> {
                if (v instanceof Player p) {
                    int total = p.calculateTotalExperiencePoints();
                    int take = (int) Math.min(total, Math.max(5, c.power * 8));
                    if (take > 0) {
                        p.setExperienceLevelAndProgress(total - take);
                    }
                }
            }
            case TITLE -> {
                if (v instanceof Player p) {
                    String[] parts = (c.d.extra.isBlank() ? "<dark_red>¡Cuidado!" : c.d.extra).split("\\|", 2);
                    p.showTitle(Title.title(Text.mm(parts[0]), Text.mm(parts.length > 1 ? parts[1] : "")));
                }
            }
            default -> {
                // Los demás efectos no son de tipo "víctima".
            }
        }
    }

    private void disarm(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return;
        }
        // Se mueve a un hueco libre del inventario (nunca se tira al suelo).
        for (int i = 9; i < 36; i++) {
            ItemStack slot = p.getInventory().getItem(i);
            if (slot == null || slot.getType().isAir()) {
                p.getInventory().setItem(i, hand.clone());
                p.getInventory().setItemInMainHand(null);
                p.sendActionBar(Text.mm(plugin.messages().raw("skill.disarmed")));
                return;
            }
        }
    }

    private void wear(Player p, int amount) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        int max = hand.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (meta instanceof Damageable dm) {
            // Nunca rompe el objeto: deja como mínimo 1 punto de durabilidad.
            dm.setDamage(Math.min(max - 1, dm.getDamage() + amount));
            hand.setItemMeta(dm);
        }
    }

    // ---------------------------------------------------------------- apoyo

    private void support(SkillContext c, Effect e, LivingEntity v) {
        long dur = c.durationTicks();
        engine.particle(v.getLocation().add(0, 1, 0), Fx.particle("HAPPY_VILLAGER", Particle.CRIT), 6, 0.4, null);
        switch (e) {
            case HEAL -> {
                AttributeInstance max = v.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (max != null) {
                    v.setHealth(Math.min(max.getValue(), v.getHealth() + c.power));
                }
            }
            case ABSORPTION -> potion(v, "absorption", dur, Math.min(3, amp(c, 3)));
            case INVISIBILITY -> potion(v, "invisibility", dur, 0);
            case SPEED -> potion(v, "speed", dur, Math.min(2, amp(c, 2)));
            case STRENGTH -> potion(v, "strength", dur, Math.min(2, amp(c, 2)));
            case RESISTANCE -> potion(v, "resistance", dur, Math.min(2, amp(c, 2)));
            case REGENERATION -> potion(v, "regeneration", dur, Math.min(2, amp(c, 2)));
            case JUMP_BOOST -> potion(v, "jump_boost", dur, Math.min(2, amp(c, 2)));
            case FIRE_RESISTANCE -> potion(v, "fire_resistance", dur, 0);
            case SHIELD -> {
                potion(v, "absorption", dur, 1);
                potion(v, "resistance", dur, 0);
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- efectos sobre el lanzador

    private void caster(SkillContext c, Effect e) {
        switch (e) {
            case REFLECT_DAMAGE -> {
                engine.reflect(c.owner, Math.min(1.0, c.power / 40.0), c.durationTicks());
                engine.particle(c.caster.getLocation().add(0, 1, 0), c.particle, 20, 0.6, c.color);
            }
            case CLONES -> clones(c);
            case MODEL_ANIMATION -> plugin.mobs().playOnce(c.st, c.d.extra);
            default -> {
            }
        }
    }

    private void clones(SkillContext c) {
        MobDef def = c.st.def;
        Class<? extends LivingEntity> cls = MobManager.classOf(def.type());
        if (cls == null || !c.caster.isValid()) {
            return;
        }
        int n = Math.min(c.d.count, 6);
        if (!engine.canSummon(c.owner, n)) {
            return;
        }
        World w = c.caster.getWorld();
        for (int i = 0; i < n; i++) {
            Location l = spread(c.caster.getLocation(), 3.0);
            LivingEntity clone = w.spawn(l, cls, cl -> {
                AttributeInstance max = cl.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(8);
                    cl.setHealth(8);
                }
                AttributeInstance dmg = cl.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                if (dmg != null) {
                    dmg.setBaseValue(Math.max(1, def.damage * 0.4));
                }
                AttributeInstance sp = cl.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
                if (sp != null) {
                    sp.setBaseValue(def.speed);
                }
                AttributeInstance sc = cl.getAttribute(Attribute.GENERIC_SCALE);
                if (sc != null) {
                    sc.setBaseValue(def.scale);
                }
                cl.customName(Text.mm(def.displayName));
                cl.setCustomNameVisible(def.nameVisible);
                cl.setSilent(def.silentVanilla);
            });
            String model = "";
            if (!c.st.appliedModel.isEmpty() && plugin.models().apply(clone, c.st.appliedModel, def.modelScale)) {
                model = c.st.appliedModel;
            }
            engine.registerSummon(clone, c.owner, c.durationTicks(), model);
            if (clone instanceof Mob m && c.target != null) {
                m.setTarget(c.target);
            }
            engine.particle(l.clone().add(0, 1, 0), Fx.particle("POOF", Particle.CRIT), 8, 0.3, null);
        }
    }

    // ---------------------------------------------------------------- efectos de punto

    private static Location spread(Location center, double radius) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(r.nextDouble()) * radius;
        Location l = center.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
        // Se sube/baja hasta un hueco transitable cercano.
        for (int i = 0; i < 4; i++) {
            Block b = l.getBlock();
            if (b.isPassable() && b.getRelative(0, 1, 0).isPassable()) {
                return l;
            }
            l.add(0, 1, 0);
        }
        return center.clone();
    }

    private void point(SkillContext c, Effect e, Location point, List<LivingEntity> victims) {
        World w = point.getWorld();
        switch (e) {
            case EXPLOSION_NO_BLOCKS -> {
                w.spawnParticle(Fx.particle("EXPLOSION", Particle.CRIT), point, 1);
                Fx.sound(point, "entity.generic.explode", 0.9f, 1.1f);
                for (Player p : playersNear(w, point, c.d.radius)) {
                    double dist = p.getLocation().distance(point);
                    double f = Math.max(0.3, 1.0 - dist / (c.d.radius * 1.4));
                    hurt(c, p, c.power * f);
                    push(p, away(point, p.getLocation()), 0.5, 0.3);
                }
            }
            case LIGHTNING -> {
                w.strikeLightningEffect(point);
                for (Player p : playersNear(w, point, 2.5)) {
                    hurt(c, p, c.power);
                }
            }
            case SHOCKWAVE -> shockwave(c, point);
            case TEMP_COBWEB -> placeAround(c, point, victims, Material.COBWEB, false);
            case TEMP_ICE -> placeAround(c, point, victims, Material.PACKED_ICE, true);
            case TEMP_LAVA -> placeAround(c, point, victims, Material.CAMPFIRE, false);
            case SUMMON_MOBS -> summon(c, point, c.d.extra.isBlank() ? "ZOMBIE" : c.d.extra, Math.min(c.d.count, 8));
            case SWARM -> summon(c, point, c.d.extra.isBlank() ? "SILVERFISH" : c.d.extra, Math.min(c.d.count, 10));
            case SOUND -> Fx.sound(point, c.d.extra.isBlank() ? c.d.element.sound : c.d.extra, 1.0f, 1.0f);
            case PARTICLES -> engine.particle(point.clone().add(0, 1, 0), c.particle, 40, 0.8, c.color);
            default -> {
            }
        }
    }

    private void shockwave(SkillContext c, Location point) {
        double radius = c.d.radius;
        for (int i = 1; i <= 6; i++) {
            final int step = i;
            engine.later(c.owner, i, () -> {
                double r = radius * step / 6.0;
                for (int k = 0; k < 20; k++) {
                    double a = Math.PI * 2 * k / 20.0;
                    engine.particle(point.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), c.particle, 1, 0, c.color);
                }
            });
        }
        for (Player p : playersNear(point.getWorld(), point, radius)) {
            if (p.isOnGround()) {
                hurt(c, p, c.power * 0.8);
                push(p, away(point, p.getLocation()), 0.4, 0.5);
            }
        }
        Fx.sound(point, "entity.generic.explode", 0.6f, 0.6f);
    }

    /** Bloques temporales alrededor de cada víctima (o del punto): solo sustituyen aire y se restauran solos. */
    private void placeAround(SkillContext c, Location point, List<LivingEntity> victims, Material mat, boolean cage) {
        List<Location> centers = new ArrayList<>();
        if (victims.isEmpty()) {
            centers.add(point);
        } else {
            for (LivingEntity v : victims) {
                centers.add(v.getLocation());
            }
        }
        long dur = c.durationTicks();
        for (Location center : centers) {
            Block base = center.getBlock();
            if (mat == Material.COBWEB) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        place(base.getRelative(dx, 0, dz), mat, dur, c);
                    }
                }
                place(base.getRelative(0, 1, 0), mat, dur, c);
            } else if (cage) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx != 0 || dz != 0) {
                                place(base.getRelative(dx, dy, dz), mat, dur, c);
                            }
                        }
                    }
                }
                place(base.getRelative(0, 2, 0), mat, dur, c);
            } else {
                // Hogueras temporales sobre suelo firme alrededor del punto
                double r = Math.max(1.0, Math.min(3.0, c.d.radius * 0.5));
                int n = (int) Math.round(3 + r * 2);
                for (int i = 0; i < n; i++) {
                    Block b = spread(center, r).getBlock();
                    if (b.getRelative(0, -1, 0).getType().isSolid()) {
                        place(b, mat, dur, c);
                    }
                }
            }
        }
    }

    private void place(Block b, Material mat, long dur, SkillContext c) {
        if (engine.placeTemp(b, mat.createBlockData(), dur, c.owner)) {
            engine.particle(b.getLocation().add(0.5, 0.5, 0.5), c.particle, 2, 0.2, c.color);
        }
    }

    private void summon(SkillContext c, Location point, String type, int count) {
        if (!engine.canSummon(c.owner, count)) {
            return;
        }
        MobDef custom = plugin.mobs().get(type);
        EntityType et = null;
        if (custom == null) {
            try {
                et = EntityType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException ex) {
                et = EntityType.ZOMBIE;
            }
            if (MobManager.classOf(et) == null) {
                et = EntityType.ZOMBIE;
            }
        }
        Player nearest = c.target != null ? c.target : engine.nearestPlayer(point, 40);
        double radius = Math.max(2.0, Math.min(5.0, c.d.radius));
        for (int i = 0; i < count; i++) {
            Location l = spread(point, radius);
            LivingEntity e;
            String model = "";
            if (custom != null) {
                e = plugin.mobs().spawn(custom, l);
                if (e != null) {
                    plugin.mobs().fillHealth(e);
                }
            } else {
                e = l.getWorld().spawn(l, MobManager.classOf(et));
            }
            if (e == null) {
                continue;
            }
            engine.registerSummon(e, c.owner, c.durationTicks(), model);
            if (e instanceof Mob m && nearest != null) {
                m.setTarget(nearest);
            }
            engine.particle(l.clone().add(0, 1, 0), c.particle, 8, 0.3, c.color);
        }
        Fx.sound(point, "entity.evoker.cast_spell", 1f, 1f);
    }

    // ---------------------------------------------------------------- búsqueda

    List<Player> playersNear(World w, Location point, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (!engine.validPlayer(p)) {
                continue;
            }
            Location l = p.getLocation();
            double dx = l.getX() - point.getX();
            double dz = l.getZ() - point.getZ();
            if (dx * dx + dz * dz <= radius * radius && Math.abs(l.getY() - point.getY()) <= radius + 1.5) {
                out.add(p);
            }
        }
        return out;
    }
}
