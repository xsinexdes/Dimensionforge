package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.skill.model.Trigger;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;

/** Eventos de mobs custom: carga/descarga, combate, muerte y apariciones naturales. */
public final class MobListener implements Listener {

    private final DimensionForgePlugin plugin;

    public MobListener(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            plugin.mobs().adopt(entity);
        }
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (plugin.mobs().state(entity) != null) {
                plugin.mobs().release(entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (plugin.mobs().state(e.getEntity()) != null) {
            return;
        }
        if (plugin.mobs().onNaturalSpawn(e.getEntity(), e.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        MobState st = plugin.mobs().state(e.getEntity());
        if (st == null) {
            return;
        }
        plugin.mobs().onHurt(st);
        LivingEntity other = null;
        if (e instanceof EntityDamageByEntityEvent by) {
            Entity damager = by.getDamager();
            if (damager instanceof Projectile p && p.getShooter() instanceof LivingEntity le) {
                other = le;
            } else if (damager instanceof LivingEntity le) {
                other = le;
            }
        }
        plugin.skills().fire(st, Trigger.ON_DAMAGED, other);
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            plugin.skills().onHitByWeapon(st, p, hand, by.isCritical());
        }
    }

    /** Multiplicador de daño de fase y de "marca" cuando el mob golpea. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        Entity damager = e.getDamager();
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        MobState st = plugin.mobs().state(damager);
        boolean summon = plugin.skills().engine() != null && plugin.skills().engine().isSummon(damager);
        if (st == null && !summon) {
            return;
        }
        if (e.getEntity() instanceof Player p && (p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }
        if (plugin.skills().engine() != null) {
            double mark = plugin.skills().engine().damageMultiplierFor(e.getEntity());
            if (mark != 1.0) {
                e.setDamage(e.getDamage() * mark);
            }
            if (plugin.skills().engine().casting()) {
                // Daño de una habilidad: ya lleva el multiplicador de fase y no encadena más habilidades.
                return;
            }
        }
        if (st == null) {
            return;
        }
        if (st.damageMultiplier != 1.0) {
            e.setDamage(e.getDamage() * st.damageMultiplier);
        }
        plugin.mobs().onAttack(st);
        if (e.getEntity() instanceof LivingEntity target) {
            plugin.skills().fire(st, Trigger.ON_ATTACK, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        MobState st = plugin.mobs().state(e.getEntity());
        if (st == null) {
            return;
        }
        plugin.mobs().onDeath(st, e.getDrops(), e::setDroppedExp);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        MobState st = plugin.mobs().state(e.getEntity());
        if (st != null && st.def.fireImmune) {
            e.setCancelled(true);
        }
    }

    /** Los mobs custom ignoran a jugadores en creativo o espectador. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (plugin.mobs().state(e.getEntity()) == null) {
            return;
        }
        if (e.getTarget() instanceof Player p
                && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            e.setCancelled(true);
        }
    }
}
