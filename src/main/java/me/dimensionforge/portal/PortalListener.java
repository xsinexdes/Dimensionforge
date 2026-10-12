package me.dimensionforge.portal;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Detección de entrada a portales, uso del hacha y protección del relleno. */
public final class PortalListener implements Listener {

    private final DimensionForgePlugin plugin;

    public PortalListener(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ() && from.getWorld() == to.getWorld())) {
            return;
        }
        Portal p = plugin.portals().findAt(to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        if (p != null && p.trigger == Portal.Trigger.WALK) {
            plugin.portals().use(e.getPlayer(), p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = e.getPlayer();
        boolean holdingWand = plugin.wand().isWand(player.getInventory().getItemInMainHand());
        if (holdingWand) {
            if (!player.hasPermission("dimensionforge.admin")) {
                return;
            }
            Action a = e.getAction();
            if (a == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);
                plugin.wand().setPos(player, true, e.getClickedBlock().getLocation());
            } else if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);
                plugin.wand().setPos(player, false, e.getClickedBlock().getLocation());
            } else if (a == Action.RIGHT_CLICK_AIR && player.isSneaking()) {
                e.setCancelled(true);
                plugin.wand().clear(player);
                plugin.messages().send(player, "wand.cleared");
            }
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            Block b = e.getClickedBlock();
            Portal p = plugin.portals().findAt(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (p != null && p.trigger == Portal.Trigger.CLICK) {
                e.setUseInteractedBlock(Event.Result.DENY);
                plugin.portals().use(player, p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.wand().isWand(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            return;
        }
        if (plugin.portals().isProtectedFill(e.getBlock()) && !e.getPlayer().hasPermission("dimensionforge.admin")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (plugin.portals().all().isEmpty()) {
            return;
        }
        if (plugin.portals().isProtectedFill(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.portals().isProtectedFill(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.portals().isProtectedFill(b));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.portals().isProtectedFill(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (plugin.portals().isProtectedFill(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.wand().forget(e.getPlayer().getUniqueId());
        plugin.portals().forgetPlayer(e.getPlayer().getUniqueId());
    }
}
