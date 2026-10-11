package me.dimensionforge.skill;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.mob.MobState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Eventos relacionados con habilidades: invocaciones, bloques temporales, aturdimiento, silencio y reflejo. */
public final class SkillListener implements Listener {

    private final DimensionForgePlugin plugin;

    public SkillListener(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    private SkillEngine engine() {
        return plugin.skills().engine();
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) {
        SkillEngine en = engine();
        if (en == null) {
            return;
        }
        for (Entity entity : e.getEntities()) {
            en.sweepLoaded(entity);
        }
    }

    /** Las invocaciones no sueltan botín ni experiencia. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSummonDeath(EntityDeathEvent e) {
        SkillEngine en = engine();
        if (en == null || !en.isSummon(e.getEntity())) {
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        en.forgetSummon(e.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        SkillEngine en = engine();
        if (en != null && en.isTempBlock(e.getBlock())) {
            en.forgetTemp(e.getBlock());
            e.setDropItems(false);
        }
    }

    /** Daño reflejado y jugadores aturdidos que no pueden golpear. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        SkillEngine en = engine();
        if (en == null) {
            return;
        }
        Entity damager = e.getDamager();
        if (damager instanceof Projectile p && p.getShooter() instanceof Entity s) {
            damager = s;
        }
        if (damager instanceof Player p && en.isStunned(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        MobState st = plugin.mobs().state(e.getEntity());
        if (st != null && damager instanceof LivingEntity attacker && !en.casting()) {
            double f = en.reflectFraction(st.id);
            if (f > 0 && attacker != e.getEntity()) {
                double back = e.getFinalDamage() * f;
                if (back > 0.1) {
                    boolean prev = en.casting();
                    en.setCasting(true);
                    try {
                        attacker.damage(back, e.getEntity());
                    } finally {
                        en.setCasting(prev);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        SkillEngine en = engine();
        if (en != null && en.isStunned(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        SkillEngine en = engine();
        if (en != null && en.isSilenced(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        SkillEngine en = engine();
        if (en != null && en.isSilenced(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }
}
